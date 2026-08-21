import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import {createBrowserRouter, RouterProvider} from 'react-router-dom';
import UserProfile from './UserProfile.jsx';
import GroupsOverview from './pages/GroupsOverview.jsx';
import CreateGroup from './pages/CreateGroup.jsx';
import GroupPage from './pages/GroupPage.jsx';
import Landing from './pages/Landing.jsx';
import GroupMembers from './pages/GroupMembers.jsx';
import AddExpense from './pages/AddExpense.jsx';
import Login from './pages/Login.jsx';
import UserManagement from './pages/UserManagement.jsx';
import './index.css';
import App from './App.jsx';

const router = createBrowserRouter([
    {
        path: '/',
        element: <App/>,
        children: [
            {
                path: '/',
                element: <Landing/>,
            },
            {
                path: '/login',
                element: <Login/>,
            },
            {
                path: '/register',
                element: <UserProfile/>,
            },
            {
                path: '/profile',
                element: <UserManagement/>,
            },
            {
                path: 'groups',
                element: <GroupsOverview/>,
            },
            {
                path: '/groups/new',
                element: <CreateGroup/>,
            },
            {
                path: '/groups/:id',
                element: <GroupPage/>,
            },
            {
                path: '/groups/:id/members',
                element: <GroupMembers/>
            },
            {
                path: '/groups/:id/expenses/new',
                element: <AddExpense/>,
            }
        ]
    }
]);

createRoot(document.getElementById('root')).render(
    <StrictMode>
        <RouterProvider router={router}/>
    </StrictMode>,
);
