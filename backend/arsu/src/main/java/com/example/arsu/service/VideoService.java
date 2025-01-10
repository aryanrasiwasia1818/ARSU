package com.example.arsu.service;

import com.example.arsu.model.Video;
import com.example.arsu.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachePut;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class VideoService {
    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    @Value("${video.storage.path:/home/aryan/ARSU/Storage}")
    private String STORAGE_DIR;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TOPIC = "video-processing";

    /**
     * Upload video with MultipartFile support.
     */
    public Video uploadVideo(Video video, MultipartFile multipartFile) throws IOException {
        validateVideoFile(multipartFile);

        // Save metadata to MongoDB
        video.setCreatedAt(new Date());
        video.setUpdatedAt(new Date());
        Video savedVideo = videoRepository.save(video);

        // Create unique file name and paths
        String uniqueFileName = UUID.randomUUID().toString() + "_" + multipartFile.getOriginalFilename();
        Path videoDir = Paths.get(STORAGE_DIR, savedVideo.getId());
        Path originalFilePath = videoDir.resolve(uniqueFileName);

        try {
            // Ensure directory exists and save original file
            Files.createDirectories(videoDir);
            Files.copy(multipartFile.getInputStream(), originalFilePath);

            // Convert video to HLS
            convertVideoToHLS(savedVideo.getId(), originalFilePath.toFile());

            // Set the URL to the local storage path
            video.setUrl(savedVideo.getId() + "/" + uniqueFileName);
            savedVideo = videoRepository.save(video);

            // Send Kafka notification
            kafkaTemplate.send(TOPIC, "Video processed successfully: " + video.getTitle());

            // Update cache
            updateRedisCache(savedVideo);

            return savedVideo;

        } catch (Exception e) {
            log.error("Error processing video: {}", e.getMessage(), e);
            // Cleanup on failure
            deleteDirectory(videoDir);
            videoRepository.deleteById(savedVideo.getId());
            throw new IOException("Failed to process video: " + e.getMessage(), e);
        }
    }

    /**
     * Upload video with File support.
     */
    public Video uploadVideo(Video video, File file) throws IOException {
        // Step 1: Save metadata to MongoDB
        video.setCreatedAt(new Date());
        video.setUpdatedAt(new Date());
        Video savedVideo = videoRepository.save(video);

        // Step 2: Store file in storage
        String uniqueFileName = UUID.randomUUID().toString() + "_" + file.getName();
        Path storagePath = Paths.get(STORAGE_DIR, uniqueFileName);

        ensureStorageDirectoryExists();
        if (!Files.exists(storagePath)) {
            Files.copy(file.toPath(), storagePath);
        }

        // Step 3: Convert video to HLS with multiple qualities
        convertVideoToHLS(savedVideo.getId(), file);

        // Step 4: Set the URL to the unique file name
        video.setUrl(uniqueFileName);
        videoRepository.save(video);

        // Step 5: Publish event to Kafka
        kafkaTemplate.send(TOPIC, "Video uploaded: " + video.getTitle());

        // Step 6: Update Redis cache
        updateRedisCache(savedVideo);

        return savedVideo;
    }

    /**
     * Converts the video to HLS with multiple qualities.
     */
    private void convertVideoToHLS(String videoId, File inputFile) throws IOException {
        validateVideoId(videoId);
        Path outputDir = Paths.get(STORAGE_DIR, videoId);

        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-i");
        command.add(inputFile.getAbsolutePath());
        command.add("-filter_complex");
        command.add("[0:v]split=4[v1][v2][v3][v4];" +
                "[v1]scale=w=1280:h=720:force_original_aspect_ratio=decrease[v1out];" +
                "[v2]scale=w=1920:h=1080:force_original_aspect_ratio=decrease[v2out];" +
                "[v3]scale=w=854:h=480:force_original_aspect_ratio=decrease[v3out];" +
                "[v4]scale=w=426:h=240:force_original_aspect_ratio=decrease[v4out]");

        // Add outputs for all qualities
        addQualityOutput(command, outputDir, "720p", "[v1out]", "3000k");
        addQualityOutput(command, outputDir, "1080p", "[v2out]", "5000k");
        addQualityOutput(command, outputDir, "480p", "[v3out]", "1500k");
        addQualityOutput(command, outputDir, "240p", "[v4out]", "800k");

        log.info("Executing FFmpeg command: {}", String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Read process output in a separate thread
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg output: {}", line);
                }
            } catch (IOException e) {
                log.error("Error reading FFmpeg output", e);
            }
        });
        outputReader.start();

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("FFmpeg command failed with exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg process interrupted", e);
        }
    }

    private void addQualityOutput(List<String> command, Path outputDir, String quality, String mapInput, String bitrate) {
        command.addAll(Arrays.asList(
                "-map", mapInput,
                "-c:v", "libx264",
                "-b:v", bitrate,
                "-maxrate", bitrate,
                "-bufsize", String.valueOf(Integer.parseInt(bitrate.replace("k", "")) * 2) + "k",
                "-hls_time", "10",
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", outputDir.resolve(quality + "_%03d.ts").toString(),
                outputDir.resolve(quality + ".m3u8").toString()
        ));
    }

    /**
     * Validates if the video file is correct.
     */
    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Video file cannot be null or empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new IllegalArgumentException("Invalid file type. Only video files are allowed");
        }

        // Check file size (optional)
        if (file.getSize() > 1000 * 1024 * 1024) { // 1000 MB
            throw new IllegalArgumentException("File size exceeds maximum limit");
        }
    }

    /**
     * Validates if the video ID is non-null and non-empty.
     */
    private void validateVideoId(String videoId) {
        if (videoId == null || videoId.isEmpty()) {
            throw new IllegalArgumentException("Invalid video ID. It cannot be null or empty.");
        }
    }

    /**
     * Ensures the storage directory exists.
     */
    private void ensureStorageDirectoryExists() {
        File storageDir = new File(STORAGE_DIR);
        if (!storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                throw new IllegalStateException("Failed to create storage directory: " + STORAGE_DIR);
            }
        }
    }

    /**
     * Deletes the video directory.
     */
    private void deleteDirectory(Path path) {
        try {
            if (Files.exists(path)) {
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                log.error("Error deleting path: {}", p, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.error("Error deleting directory: {}", path, e);
        }
    }

    /**
     * Updates Redis cache for a user's videos.
     */
    @CachePut(value = "videos", key = "#video.userId")
    public List<Video> updateRedisCache(Video video) {
        return videoRepository.findByUserId(video.getUserId());
    }

    /**
     * Get all videos by a specific user.
     */
    public List<Video> getVideosByUserId(String userId) {
        return videoRepository.findByUserId(userId);
    }

    /**
     * Get all videos.
     */
    public List<Video> getAllVideos() {
        return videoRepository.findAll();
    }
}
