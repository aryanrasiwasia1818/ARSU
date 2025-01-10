import React, { useState, useEffect } from 'react';
import axios from 'axios';
import './VideoList.css';
import VideoPlayer from './VideoPlayer.jsx';

const VideoList = () => {
    const [videos, setVideos] = useState([]);
    const [selectedQuality, setSelectedQuality] = useState('720p');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchVideos = async () => {
            try {
                const response = await axios.get('/api/videos/all');
                console.log('API Response:', response.data); // Debug log

                if (Array.isArray(response.data)) {
                    // Validate that each video has an ID
                    const validVideos = response.data.filter(video => {
                        if (!video.id && !video._id) {
                            console.warn('Found video without ID:', video);
                            return false;
                        }
                        return true;
                    });

                    // Normalize the data to ensure each video has an id property
                    const normalizedVideos = validVideos.map(video => ({
                        ...video,
                        id: video.id || video._id // MongoDB might use _id
                    }));

                    setVideos(normalizedVideos);
                } else if (typeof response.data === 'object') {
                    // Handle potential Spring HATEOAS response
                    const videoArray = Array.isArray(response.data._embedded?.videos)
                        ? response.data._embedded.videos
                        : [response.data];

                    const normalizedVideos = videoArray
                        .filter(video => video.id || video._id)
                        .map(video => ({
                            ...video,
                            id: video.id || video._id
                        }));

                    setVideos(normalizedVideos);
                }
            } catch (error) {
                console.error('Error fetching videos:', error);
                setError(error.response?.data?.message || 'Failed to load videos');
            } finally {
                setLoading(false);
            }
        };

        fetchVideos();
    }, []);

    const handleQualityChange = (event) => {
        setSelectedQuality(event.target.value);
    };

    const getVideoUrl = (videoId) => {
        if (!videoId) {
            console.error('Attempted to get URL for video with no ID');
            return null;
        }
        return `/api/videos/stream/${videoId}?quality=${selectedQuality}`;
    };

    if (loading) return <div>Loading videos...</div>;
    if (error) return <div className="error-message">{error}</div>;
    if (!videos.length) return <div>No videos available</div>;

    return (
        <div className="video-list-container">
            <h2>Uploaded Videos</h2>

            <div className="quality-selector">
                <label htmlFor="quality">Select Quality: </label>
                <select
                    id="quality"
                    value={selectedQuality}
                    onChange={handleQualityChange}
                    className="quality-select"
                >
                    <option value="240p">240p</option>
                    <option value="480p">480p</option>
                    <option value="720p">720p</option>
                    <option value="1080p">1080p</option>
                </select>
            </div>

            <div className="videos-grid">
                {videos.map((video) => {
                    const videoUrl = getVideoUrl(video.id);
                    if (!videoUrl) return null;

                    return (
                        <div key={video.id} className="video-card">
                            <h3 className="video-title">{video.title}</h3>
                            <p className="video-description">{video.description}</p>
                            <div className="video-player-container">
                                <VideoPlayer src={videoUrl} />
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

export default VideoList;