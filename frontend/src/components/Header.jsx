import { Link } from 'react-router-dom';

function Header() {
  return (
    <header className="header">
      <div className="container header-inner">
        <Link to="/" className="logo">FairShare</Link>
        <nav className="main-nav">
          <Link to="/">Home</Link>
          <Link to="/groups">Groups</Link>
          <Link to="/register">User Profile</Link>
          <Link to="/groups/new">Create Group</Link>
        </nav>
      </div>
    </header>
  );
}

export default Header;
