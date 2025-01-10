import React, { useEffect, useRef } from 'react';
import Hls from 'hls.js'; // Import Hls.js

const VideoPlayer = ({ src }) => {
    const videoRef = useRef(null);

    useEffect(() => {
        if (videoRef.current) {
            const video = videoRef.current;

            if (Hls.isSupported()) {
                const hls = new Hls();
                hls.loadSource(src);
                hls.attachMedia(video);
                hls.on(Hls.Events.MANIFEST_PARSED, () => {
                    video.play().catch(e => console.log('Play prevented:', e));
                });
            }
            // For browsers that natively support HLS
            else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                video.src = src;
                video.addEventListener('loadedmetadata', () => {
                    video.play().catch(e => console.log('Play prevented:', e));
                });
            }
        }
    }, [src]);

    return (
        <video
            ref={videoRef}
            controls
            className="video-player"
        />
    );
};

export default VideoPlayer;
