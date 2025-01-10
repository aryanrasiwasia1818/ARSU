import React from 'react';
import { BrowserRouter as Router, Route, Routes, Link } from 'react-router-dom';
import Register from './components/Register.jsx';
import Login from './components/Login.jsx';
import UploadVideo from './components/UploadVideo.jsx';
import VideoList from './components/VideoList.jsx';
import Dashboard from './components/Dashboard.jsx';

const Home = () => (
    <div style={styles.container}>
        <h1>Welcome to ARSU</h1>
        <div>
            <Link to="/login" style={styles.link}>Login</Link> |
            <Link to="/register" style={styles.link}>Register</Link>
        </div>
    </div>
);

const App = () => {
    return (
        <Router>
            <Routes>
                <Route path="/" element={<Home />} />
                <Route path="/register" element={<Register />} />
                <Route path="/login" element={<Login />} />
                <Route path="/upload" element={<UploadVideo />} />
                <Route path="/videos" element={<VideoList />} />
                <Route path="/dashboard" element={<Dashboard />} />
            </Routes>
        </Router>
    );
};

const styles = {
    container: {
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',  // Vertically center the content
        alignItems: 'center',      // Horizontally center the content
        height: '100vh',           // Full screen height
        textAlign: 'center',       // Ensure text is centered
    },
    link: {
        margin: '0 10px',  // Add some space between links
    }
};

export default App;
