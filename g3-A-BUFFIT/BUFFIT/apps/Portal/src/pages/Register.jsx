import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { registerUser } from '../services/api';

export const Register = () => {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    role: 'Usuario',              // Roles: Usuario, Comedor, Administrador
    user_type: 'ESTUDIANTE'       // Tipos: APOYO_ALIMENTARIO, ESTUDIANTE, GENERAL
  });

  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      await registerUser(formData);
      setSuccess('¡Usuario registrado con éxito! Redirigiendo al inicio de sesión...');
      setTimeout(() => {
        navigate('/login');
      }, 2000);
    } catch (err) {
      const detail = err.response?.data?.detail || err.response?.data?.error || 'Error al registrar el usuario';
      setError(detail);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="register-container" style={{ maxWidth: '400px', margin: '50px auto', padding: '20px', border: '1px solid #ccc', borderRadius: '8px' }}>
      <h2>Registro BUFF/T</h2>
      
      {error && <div style={{ color: 'red', marginBottom: '10px' }}>{error}</div>}
      {success && <div style={{ color: 'green', marginBottom: '10px' }}>{success}</div>}

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '10px' }}>
          <label>Nombre Completo:</label>
          <input
            type="text"
            name="name"
            value={formData.name}
            onChange={handleChange}
            required
            style={{ width: '100%', padding: '8px', marginTop: '4px' }}
          />
        </div>

        <div style={{ marginBottom: '10px' }}>
          <label>Correo Electrónico:</label>
          <input
            type="email"
            name="email"
            value={formData.email}
            onChange={handleChange}
            required
            style={{ width: '100%', padding: '8px', marginTop: '4px' }}
          />
        </div>

        <div style={{ marginBottom: '10px' }}>
          <label>Contraseña:</label>
          <input
            type="password"
            name="password"
            value={formData.password}
            onChange={handleChange}
            required
            style={{ width: '100%', padding: '8px', marginTop: '4px' }}
          />
        </div>

        <div style={{ marginBottom: '10px' }}>
          <label>Rol en el Sistema:</label>
          <select
            name="role"
            value={formData.role}
            onChange={handleChange}
            style={{ width: '100%', padding: '8px', marginTop: '4px' }}
          >
            <option value="Usuario">Usuario (Estudiante / Cliente)</option>
            <option value="Comedor">Comedor (Personal de entrega)</option>
            <option value="Administrador">Administrador</option>
          </select>
        </div>

        {formData.role === 'Usuario' && (
          <div style={{ marginBottom: '10px' }}>
            <label>Tipo de Beneficio / Usuario:</label>
            <select
              name="user_type"
              value={formData.user_type}
              onChange={handleChange}
              style={{ width: '100%', padding: '8px', marginTop: '4px' }}
            >
              <option value="ESTUDIANTE">Estudiante ($3.500 COP)</option>
              <option value="APOYO_ALIMENTARIO">Apoyo Alimentario ($0 COP)</option>
              <option value="GENERAL">General ($12.000 COP)</option>
            </select>
          </div>
        )}

        <button
          type="submit"
          disabled={loading}
          style={{ width: '100%', padding: '10px', backgroundColor: '#007bff', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
        >
          {loading ? 'Registrando...' : 'Crear Cuenta'}
        </button>
      </form>

      <p style={{ marginTop: '15px', textAlign: 'center' }}>
        ¿Ya tienes cuenta? <Link to="/login">Inicia Sesión aquí</Link>
      </p>
    </div>
  );
};

export default Register;