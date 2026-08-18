import { Link } from 'react-router-dom';

function Landing() {
  return (
    <div className="page landing">
      <div className="card">
        <h1>Welcome to FairShare</h1>
        <p className="subtitle">Easily split bills and manage group expenses.</p>

        <div className="actions">
          <Link to="/groups" className="button">Group Overview</Link>
          <Link to="/groups/new" className="button secondary">Create Group</Link>
          <Link to="/register" className="button">User Profile</Link>
        </div>
      </div>
    </div>
  );
}

export default Landing;
