import React, { useState, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const { login } = useContext(AuthContext);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    const res = await login(username, password);
    if (res && res.user) {
      navigate('/mis-pedidos');
    } else {
      setError(res?.error || 'Error de autenticación');
    }
  };

  return (
    <div className="container" style={{ maxWidth: '400px', marginTop: '80px' }}>
      <div className="card">
        <h2 style={{ textAlign: 'center', color: '#14253f' }}>Iniciar Sesión</h2>
        {error && <p style={{ color: 'red' }}>{error}</p>}
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Usuario / Correo UNAL</label>
            <input
              type="text"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="usuario@unal.edu.co"
            />
          </div>
          <div className="form-group">
            <label>Contraseña</label>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <button type="submit" className="btn" style={{ width: '100%' }}>
            Ingresar
          </button>
        </form>

        <p style={{ textAlign: 'center', marginTop: '15px', fontSize: '0.9rem' }}>
          ¿No tienes una cuenta?{' '}
          <Link to="/register" style={{ color: '#f16e58', fontWeight: 'bold' }}>
            Regístrate aquí
          </Link>
        </p>
      </div>
    </div>
  );
}