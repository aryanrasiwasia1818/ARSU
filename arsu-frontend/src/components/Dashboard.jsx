// src/components/Dashboard.jsx
import React from 'react';
import { Link } from 'react-router-dom';

const Dashboard = () => (
    <div>
        <h1>Dashboard</h1>
        <Link to="/upload">Upload Video</Link> | <Link to="/videos">Stream Videos</Link>
    </div>
);

export default Dashboard;