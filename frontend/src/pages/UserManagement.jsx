import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { API_BASE } from '../api/config';

const countries = [
  { name: 'New Zealand', value: 'NEW_ZEALAND', currency: 'NZD' },
  { name: 'Australia', value: 'AUSTRALIA', currency: 'AUD' }
];

const currencies = ['NZD', 'AUD'];

function UserManagement() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [country, setCountry] = useState('');
  const [currency, setCurrency] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    const storedUser = localStorage.getItem('fairshareUser');
    if (!storedUser) {
      navigate('/login');
      return;
    }

    const parsedUser = JSON.parse(storedUser);
    if (!parsedUser.id) {
      navigate('/login');
      return;
    }

    async function loadProfile() {
      try {
        const response = await fetch(`${API_BASE}/users/${parsedUser.id}`);
        if (!response.ok) {
          throw new Error('Profile could not be loaded');
        }

        const result = await response.json();
        const user = result.user;
        setUsername(user.username || '');
        setEmail(user.email || '');
        setCountry(user.country || '');
        setCurrency(user.currency || '');
      } catch (loadError) {
        setError('Could not load your profile. Please try again.');
      } finally {
        setLoading(false);
      }
    }

    loadProfile();
  }, [navigate]);

  function handleCountryChange(event) {
    const selectedCountry = event.target.value;
    setCountry(selectedCountry);

    const countryData = countries.find((item) => item.value === selectedCountry);
    if (countryData) {
      setCurrency(countryData.currency);
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setMessage('');

    const storedUser = localStorage.getItem('fairshareUser');
    if (!storedUser) {
      navigate('/login');
      return;
    }

    const parsedUser = JSON.parse(storedUser);
    const payload = {
      username,
      email,
      country,
      currency,
      password: password || undefined
    };

    try {
      const response = await fetch(`${API_BASE}/users/${parsedUser.id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const text = await response.text();
        throw new Error(text || 'Profile update failed');
      }

      const result = await response.json();
      const nextUser = result.user;
      localStorage.setItem(
        'fairshareUser',
        JSON.stringify({
          id: nextUser.id,
          username: nextUser.username,
          email: nextUser.email
        })
      );
      setPassword('');
      setMessage('Your profile has been updated.');
    } catch (submitError) {
      setError(submitError.message || 'Unable to update profile.');
    }
  }

  function handleLogout() {
    localStorage.removeItem('fairshareUser');
    navigate('/');
  }

  if (loading) {
    return (
      <div className="page">
        <div className="card">
          <p>Loading your profile…</p>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="card">
        <h1>Manage Profile</h1>
        <p className="subtitle">Update your account information</p>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </div>

          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">New Password</label>
            <input
              id="password"
              type="password"
              value={password}
              placeholder="Leave blank to keep current password"
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>

          <div className="form-group">
            <label htmlFor="country">Country</label>
            <select id="country" value={country} onChange={handleCountryChange}>
              <option value="">Select your country</option>
              {countries.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.name}
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="currency">Currency</label>
            <select id="currency" value={currency} onChange={(event) => setCurrency(event.target.value)}>
              <option value="">Select your currency</option>
              {currencies.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
          </div>

          {error && <div className="error">{error}</div>}
          {message && <div className="success">{message}</div>}

          <button type="submit">Save Changes</button>
          <button type="button" onClick={handleLogout} className="secondary-button">
            Log Out
          </button>
        </form>

        <div style={{ marginTop: '16px' }}>
          <Link to="/groups">Back to groups</Link>
        </div>
      </div>
    </div>
  );
}

export default UserManagement;
