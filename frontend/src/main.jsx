import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import UserProfile from './UserProfile.jsx';
import GroupsOverview from './pages/GroupsOverview.jsx';
import CreateGroup from './pages/CreateGroup.jsx';
import GroupPage from './pages/GroupPage.jsx';
import Header from './components/Header.jsx';
import Landing from './pages/Landing.jsx';
import './index.css';

createRoot(document.getElementById('root')).render(
    <StrictMode>
        <BrowserRouter>
            <Header />
            <Routes>
                <Route path="/" element={<Landing />} />
                <Route path="/register" element={<UserProfile />} />
                <Route path="/groups" element={<GroupsOverview />} />
                <Route path="/groups/new" element={<CreateGroup />} />
                <Route path="/groups/:id" element={<GroupPage />} />
            </Routes>
        </BrowserRouter>
    </StrictMode>,
);
