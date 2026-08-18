import {Link} from 'react-router-dom';
import './Landing.css';

function Landing() {
    return (
        <div className="page landing">
            <div className="card">
                <h1>Welcome to FairShare</h1>
                <p className="subtitle">Easily split bills and manage group expenses.</p>

                <div className="actions">
                    <Link to="/register" className="button landingLink">Create Profile</Link>
                    <Link to="/login" className="button">Log-in</Link>
                </div>
            </div>
        </div>
    );
}

export default Landing;
