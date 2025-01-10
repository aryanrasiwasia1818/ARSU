package com.example.arsu.controller;

import com.example.arsu.model.Video;
import com.example.arsu.service.VideoService;
import com.example.arsu.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private static final Logger logger = LoggerFactory.getLogger(VideoController.class);

    @Autowired
    private VideoService videoService;

    @Autowired
    private VideoRepository videoRepository;

    private final Path videoLocation = Paths.get("/home/aryan/ARSU/Storage");

    /**
     * Upload video API supporting both MultipartFile and File input types.
     * This method uses MultipartFile for video upload and passes it to VideoService for processing.
     */
    @PostMapping("/upload")
    public ResponseEntity<Video> uploadVideo(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("file") MultipartFile multipartFile) {
        try {
            Video video = new Video();
            video.setTitle(title);
            video.setDescription(description);
            video.setUserId("default"); // Set appropriate user ID based on your authentication

            Video savedVideo = videoService.uploadVideo(video, multipartFile);
            return ResponseEntity.ok(savedVideo);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            logger.error("Error processing video upload: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get videos by user ID.
     */
    @GetMapping("/user/{userId}")
    public List<Video> getVideosByUserId(@PathVariable String userId) {
        return videoService.getVideosByUserId(userId);
    }

    /**
     * Get all videos in the database.
     */
    @GetMapping("/all")
    public ResponseEntity<List<Video>> getAllVideos() {
        try {
            List<Video> videos = videoService.getAllVideos();
            logger.info("Found {} videos", videos.size());

            // Debug log each video
            videos.forEach(video -> logger.debug("Video: {}", video));

            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(videos);
        } catch (Exception e) {
            logger.error("Error retrieving videos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Stream video in different qualities using HLS.
     * Allows clients to stream a video in a specific quality (e.g., 720p, 1080p).
     */
    @GetMapping("/stream/{videoId}")
    public ResponseEntity<Resource> streamVideo(@PathVariable String videoId, @RequestParam String quality) {
        try {
            // Check if the video exists in the repository
            Optional<Video> videoOptional = videoRepository.findById(videoId);
            if (videoOptional.isEmpty()) {
                logger.warn("Video with ID {} not found", videoId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Video video = videoOptional.get();

            // Validate the quality parameter
            List<String> supportedQualities = Arrays.asList("720p", "1080p", "480p", "240p");
            if (!supportedQualities.contains(quality)) {
                logger.warn("Invalid quality requested: {}", quality);
                return ResponseEntity.badRequest().build();
            }

            // Validate the video URL
            if (video.getUrl() == null || video.getUrl().isEmpty()) {
                logger.warn("Video URL is missing for video ID {}", videoId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            // Path to the HLS playlist
            Path file = videoLocation.resolve(video.getUrl() + "/" + quality + ".m3u8");
            if (!Files.exists(file)) {
                logger.warn("HLS file not found at path: {}", file.toString());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Resource resource = new UrlResource(file.toUri());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error streaming video", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Stream raw video file (MP4).
     * Allows clients to stream the raw video file in its original format.
     */
    @GetMapping("/stream-raw/{videoId}")
    public ResponseEntity<Resource> streamRawVideo(@PathVariable String videoId) {
        try {
            // Check if the video exists in the repository
            Optional<Video> videoOptional = videoRepository.findById(videoId);
            if (videoOptional.isEmpty()) {
                logger.warn("Video with ID {} not found", videoId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Video video = videoOptional.get();

            // Validate the video URL
            if (video.getUrl() == null || video.getUrl().isEmpty()) {
                logger.warn("Video URL is missing for video ID {}", videoId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            // Path to the raw video file
            Path file = videoLocation.resolve(video.getUrl());
            if (!Files.exists(file)) {
                logger.warn("Raw video file not found at path: {}", file.toString());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Resource resource = new UrlResource(file.toUri());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error streaming raw video", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
