import React, { useState, useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { getMenuOpciones } from '../services/api';

export default function Home() {
  const { user } = useContext(AuthContext);
  const currentUser = user?.user || user;

  const [menuData, setMenuData] = useState({
    proteinas: [],
    carbohidratos: [],
    ensaladas: [],
    bebidas: [],
    sopas: [],
    postres: [],
    puntosRecogida: ['Comedor Central', 'Comedor Hemeroteca', 'Comedor Agronomía'],
    rangosHorarios: [
      '11:30 AM - 11:45 AM',
      '11:45 AM - 12:00 PM',
      '12:00 PM - 12:15 PM',
      '12:15 PM - 12:30 PM',
      '12:45 PM - 01:00 PM',
      '1:15 PM - 1:30 PM'
    ]
  });

  const [loading, setLoading] = useState(true);
  const [errorMenu, setErrorMenu] = useState('');

  const [proteina, setProteina] = useState('');
  const [carb1, setCarb1] = useState('');
  const [carb2, setCarb2] = useState('');
  const [ensalada, setEnsalada] = useState('');
  const [bebida, setBebida] = useState('');

  const [quiereSopa, setQuiereSopa] = useState(false);
  const [sopa, setSopa] = useState('Sin sopa');

  const [quierePostre, setQuierePostre] = useState(false);
  const [postre, setPostre] = useState('Sin postre');

  const [punto, setPunto] = useState('Comedor Central');
  const [horario, setHorario] = useState('11:30 AM - 11:45 AM');

  const navigate = useNavigate();

  useEffect(() => {
    const fetchMenu = async () => {
      try {
        setLoading(true);
        const data = await getMenuOpciones();

        setMenuData({
          proteinas: data.proteinas || [],
          carbohidratos: data.carbohidratos || [],
          ensaladas: data.ensaladas || [],
          bebidas: data.bebidas || [],
          sopas: data.sopas || ['Sopa del Día (Verduras)', 'Crema de Pollo'],
          postres: data.postres || ['Flan de Leche', 'Fruta de Temporada'],
          puntosRecogida: data.puntosRecogida || ['Comedor Central', 'Comedor Hemeroteca', 'Comedor Agronomía'],
          rangosHorarios: data.rangosHorarios || [
            '11:30 AM - 11:45 AM',
            '11:45 AM - 12:00 PM',
            '12:00 PM - 12:15 PM',
            '12:15 PM - 12:30 PM',
            '12:45 PM - 01:00 PM',
            '1:15 PM - 1:30 PM'
          ]
        });

        if (data.puntosRecogida?.length > 0) setPunto(data.puntosRecogida[0]);
        if (data.rangosHorarios?.length > 0) setHorario(data.rangosHorarios[0]);

      } catch (err) {
        console.error("Error al cargar menú:", err);
        setErrorMenu('No se pudieron obtener las opciones de menú del día.');
      } finally {
        setLoading(false);
      }
    };

    fetchMenu();
  }, []);

  const handleIrAResumen = (e) => {
    e.preventDefault();

    if (!proteina || !carb1 || !carb2 || !ensalada || !bebida) {
      alert('Por favor selecciona todos los componentes del plato.');
      return;
    }

    // Asegurar user_id válido (string no vacío)
    const activeUserId = String(currentUser?.id || currentUser?.user_id || currentUser?.email || "1");

    const itemsData = [
      { 
        product_name: proteina, 
        name: proteina, 
        category: "PROTEINA", 
        item_type: "PROTEINA", 
        quantity: 1 
      },
      { 
        product_name: carb1, 
        name: carb1, 
        category: "CARBOHIDRATO_1", 
        item_type: "CARBOHIDRATO_1", 
        quantity: 1 
      },
      { 
        product_name: carb2, 
        name: carb2, 
        category: "CARBOHIDRATO_2", 
        item_type: "CARBOHIDRATO_2", 
        quantity: 1 
      },
      { 
        product_name: ensalada, 
        name: ensalada, 
        category: "ENSALADA", 
        item_type: "ENSALADA", 
        quantity: 1 
      },
      { 
        product_name: bebida, 
        name: bebida, 
        category: "BEBIDA", 
        item_type: "BEBIDA", 
        quantity: 1 
      },
    ];

    if (quiereSopa && sopa !== 'Sin sopa') {
      itemsData.push({ 
        product_name: sopa, 
        name: sopa, 
        category: "SOPA", 
        item_type: "SOPA", 
        quantity: 1 
      });
    }

    if (quierePostre && postre !== 'Sin postre') {
      itemsData.push({ 
        product_name: postre, 
        name: postre, 
        category: "POSTRE", 
        item_type: "POSTRE", 
        quantity: 1 
      });
    }
    const draftOrder = {
      user_id: activeUserId,
      user_type: currentUser?.user_type || 'ESTUDIANTE',
      dining_hall: punto || 'Comedor Central',
      pickup_time_slot: horario || '11:30 AM - 11:45 AM',
      items: itemsData,
      platoVisual: {
        proteina,
        carb1,
        carb2,
        ensalada,
        bebida,
        sopa: quiereSopa ? sopa : 'Sin sopa',
        postre: quierePostre ? postre : 'Sin postre'
      },
      precio: currentUser?.user_type === 'APOYO_ALIMENTARIO' ? 0 : 8500
    };

    navigate('/resumen', { state: { draftOrder } });
  };

  if (loading) {
    return (
      <div className="container" style={{ textAlign: 'center', marginTop: '50px' }}>
        <h3>Cargando menú del día desde el inventario...</h3>
      </div>
    );
  }

  return (
    <div className="container">
      <h2>Arma tu Almuerzo BUFF/T</h2>
      <p>
        Bienvenido, <strong>{currentUser?.name || currentUser?.email || 'Estudiante'}</strong>. Selecciona las opciones ofertadas para hoy.
      </p>

      {errorMenu && <p style={{ color: 'red', fontWeight: 'bold' }}>{errorMenu}</p>}

      <form onSubmit={handleIrAResumen}>
        <div className="card">
          <h3>1. Selección de Plato Principal</h3>
          <div className="grid">
            <div className="form-group">
              <label>Proteína (1 porción)</label>
              <select value={proteina} onChange={(e) => setProteina(e.target.value)} required>
                <option value="">-- Seleccionar --</option>
                {menuData.proteinas.map((p, i) => <option key={i} value={p}>{p}</option>)}
              </select>
            </div>

            <div className="form-group">
              <label>Carbohidrato 1</label>
              <select value={carb1} onChange={(e) => setCarb1(e.target.value)} required>
                <option value="">-- Seleccionar --</option>
                {menuData.carbohidratos.map((c, i) => <option key={i} value={c}>{c}</option>)}
              </select>
            </div>

            <div className="form-group">
              <label>Carbohidrato 2</label>
              <select value={carb2} onChange={(e) => setCarb2(e.target.value)} required>
                <option value="">-- Seleccionar --</option>
                {menuData.carbohidratos.map((c, i) => <option key={i} value={c}>{c}</option>)}
              </select>
            </div>

            <div className="form-group">
              <label>Ensalada</label>
              <select value={ensalada} onChange={(e) => setEnsalada(e.target.value)} required>
                <option value="">-- Seleccionar --</option>
                {menuData.ensaladas.map((e, i) => <option key={i} value={e}>{e}</option>)}
              </select>
            </div>

            <div className="form-group">
              <label>Bebida</label>
              <select value={bebida} onChange={(e) => setBebida(e.target.value)} required>
                <option value="">-- Seleccionar --</option>
                {menuData.bebidas.map((b, i) => <option key={i} value={b}>{b}</option>)}
              </select>
            </div>
          </div>
        </div>

        <div className="card">
          <h3>2. Entradas y Acompañamientos Opcionales</h3>
          <div className="grid">
            <div className="form-group">
              <label style={{ display: 'block', marginBottom: '8px' }}>
                <input
                  type="checkbox"
                  checked={quiereSopa}
                  onChange={(e) => {
                    setQuiereSopa(e.target.checked);
                    if (!e.target.checked) setSopa('Sin sopa');
                  }}
                  style={{ marginRight: '8px' }}
                />
                Deseo incluir Sopa
              </label>

              {quiereSopa && (
                <select value={sopa} onChange={(e) => setSopa(e.target.value)} required>
                  <option value="">-- Seleccionar Sopa --</option>
                  {menuData.sopas.map((s, i) => <option key={i} value={s}>{s}</option>)}
                </select>
              )}
            </div>

            <div className="form-group">
              <label style={{ display: 'block', marginBottom: '8px' }}>
                <input
                  type="checkbox"
                  checked={quierePostre}
                  onChange={(e) => {
                    setQuierePostre(e.target.checked);
                    if (!e.target.checked) setPostre('Sin postre');
                  }}
                  style={{ marginRight: '8px' }}
                />
                Deseo incluir Postre
              </label>

              {quierePostre && (
                <select value={postre} onChange={(e) => setPostre(e.target.value)} required>
                  <option value="">-- Seleccionar Postre --</option>
                  {menuData.postres.map((p, i) => <option key={i} value={p}>{p}</option>)}
                </select>
              )}
            </div>
          </div>
        </div>

        <div className="card">
          <h3>3. Logística de Entrega</h3>
          <div className="grid">
            <div className="form-group">
              <label>Punto de Recogida</label>
              <select value={punto} onChange={(e) => setPunto(e.target.value)}>
                {menuData.puntosRecogida.map((pr, i) => <option key={i} value={pr}>{pr}</option>)}
              </select>
            </div>

            <div className="form-group">
              <label>Rango de Hora</label>
              <select value={horario} onChange={(e) => setHorario(e.target.value)}>
                {menuData.rangosHorarios.map((rh, i) => <option key={i} value={rh}>{rh}</option>)}
              </select>
            </div>
          </div>
        </div>

        <button type="submit" className="btn" style={{ fontSize: '1.2rem', width: '100%' }}>
          Revisar Resumen y Confirmar Orden
        </button>
      </form>
    </div>
  );
}