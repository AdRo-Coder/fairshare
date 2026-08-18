import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

function Header() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  useEffect(() => {
    setIsLoggedIn(Boolean(localStorage.getItem('fairshareUser')));
  }, []);

  return (
    <header className="header">
      <div className="container header-inner">
        <Link to="/" className="logo">FairShare</Link>
        <nav className="main-nav">
          <Link to="/">Home</Link>
          <Link to="/groups">Groups</Link>
          {isLoggedIn ? (
            <Link to="/profile">Profile</Link>
          ) : (
            <Link to="/login">Log In</Link>
          )}
          <Link to="/groups/new">Create Group</Link>
        </nav>
      </div>
    </header>
  );
}

export default Header;
