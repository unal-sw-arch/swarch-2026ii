import React, { useEffect, useState } from 'react';
import { getMenuOpciones } from '../services/api';

const STORAGE_KEY = 'comedor_asignado';
const FALLBACK_HALLS = ['Comedor Central', 'Comedor Hemeroteca', 'Comedor Agronomía'];

const today = () => new Date().toLocaleDateString('en-CA'); // YYYY-MM-DD en hora local

// El personal elige a qué comedor está asignado cada día: la elección caduca al cambiar de día.
const readStored = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
    return saved && saved.date === today() ? saved.hall : null;
  } catch {
    return null;
  }
};

/**
 * Pide el comedor del día y, una vez elegido, renderiza children(hall).
 */
export default function DiningHallGate({ children }) {
  const [hall, setHall] = useState(readStored);
  const [halls, setHalls] = useState(FALLBACK_HALLS);
  const [choice, setChoice] = useState('');

  useEffect(() => {
    getMenuOpciones()
      .then((data) => {
        if (data.puntosRecogida?.length) setHalls(data.puntosRecogida);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!choice) setChoice(halls[0]);
  }, [halls, choice]);

  const confirm = (e) => {
    e.preventDefault();
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ hall: choice, date: today() }));
    setHall(choice);
  };

  const change = () => {
    localStorage.removeItem(STORAGE_KEY);
    setHall(null);
  };

  if (!hall) {
    return (
      <div className="container" style={{ maxWidth: '480px', marginTop: '60px' }}>
        <form className="card" onSubmit={confirm}>
          <h2>¿En qué comedor trabajas hoy?</h2>
          <p>Esta selección vale para el día de hoy.</p>
          <div className="form-group">
            <label>Comedor asignado</label>
            <select value={choice} onChange={(e) => setChoice(e.target.value)}>
              {halls.map((h) => <option key={h} value={h}>{h}</option>)}
            </select>
          </div>
          <button type="submit" className="btn" style={{ width: '100%' }}>Continuar</button>
        </form>
      </div>
    );
  }

  return (
    <div className="container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
        <h3 style={{ margin: 0 }}>📍 {hall}</h3>
        <button type="button" className="btn" onClick={change}>Cambiar comedor</button>
      </div>
      {children(hall)}
    </div>
  );
}
