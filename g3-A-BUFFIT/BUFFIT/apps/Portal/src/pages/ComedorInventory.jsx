import React, { useCallback, useEffect, useState } from 'react';
import DiningHallGate from '../components/DiningHallGate';
import {
  getInventoryItems, createInventoryItem, updateInventoryItem,
  createLeftoverReport, getLeftoverReports,
} from '../services/api';

const CATEGORIES = ['proteinas', 'carbohidratos', 'ensaladas', 'bebidas', 'sopas', 'postres'];

const errorText = (err, fallback) => {
  const d = err.response?.data;
  if (typeof d?.detail === 'string') return d.detail;
  return d?.error || d?.message || fallback;
};

function InventoryPanel({ hall }) {
  const [items, setItems] = useState([]);
  const [edits, setEdits] = useState({});
  const [reports, setReports] = useState([]);
  const [message, setMessage] = useState({ text: '', type: '' });
  const [newItem, setNewItem] = useState({ product_name: '', category: CATEGORIES[0], quantity: 0 });

  const load = useCallback(async () => {
    try {
      setItems(await getInventoryItems(hall));
      setReports(await getLeftoverReports(hall));
      setEdits({});
    } catch (err) {
      setMessage({ text: errorText(err, 'No se pudo cargar el inventario.'), type: 'error' });
    }
  }, [hall]);

  useEffect(() => { load(); }, [load]);

  const notify = (text, type = 'success') => setMessage({ text, type });

  const saveQuantity = async (item) => {
    const quantity = Number(edits[item.id]);
    if (!Number.isInteger(quantity) || quantity < 0) {
      notify('La cantidad debe ser un entero mayor o igual a 0.', 'error');
      return;
    }
    try {
      await updateInventoryItem(item.id, { quantity });
      notify(`"${item.product_name}" actualizado a ${quantity}.`);
      await load();
    } catch (err) {
      notify(errorText(err, 'No se pudo actualizar el ítem.'), 'error');
    }
  };

  const addItem = async (e) => {
    e.preventDefault();
    try {
      await createInventoryItem({ ...newItem, quantity: Number(newItem.quantity), dining_hall: hall });
      notify(`"${newItem.product_name}" guardado en ${hall}.`);
      setNewItem({ product_name: '', category: CATEGORIES[0], quantity: 0 });
      await load();
    } catch (err) {
      notify(errorText(err, 'No se pudo guardar el ítem.'), 'error');
    }
  };

  const closeDay = async () => {
    if (!window.confirm(`¿Generar el informe de sobrantes de ${hall} con el inventario actual?`)) return;
    try {
      const report = await createLeftoverReport(hall);
      notify(`Informe generado: ${report.total_portions} porciones sobrantes.`);
      await load();
    } catch (err) {
      notify(errorText(err, 'No se pudo generar el informe.'), 'error');
    }
  };

  return (
    <>
      {message.text && (
        <div style={{
          padding: '10px 14px', marginBottom: '16px', borderRadius: '6px', fontWeight: 'bold',
          backgroundColor: message.type === 'error' ? '#fde8e8' : '#e8f5e9',
          color: message.type === 'error' ? '#9b1c1c' : '#1b5e20',
        }}>
          {message.text}
        </div>
      )}

      <div className="card">
        <h2>Inventario del día</h2>
        {items.length === 0 ? (
          <p>No hay productos registrados para este comedor.</p>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ textAlign: 'left', borderBottom: '2px solid #ddd' }}>
                <th>Categoría</th><th>Producto</th><th>Disponible</th><th>Nueva cantidad</th><th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} style={{ borderBottom: '1px solid #eee' }}>
                  <td>{item.category}</td>
                  <td>{item.product_name}</td>
                  <td>{item.quantity}</td>
                  <td>
                    <input
                      type="number" min="0" style={{ width: '90px', padding: '6px' }}
                      value={edits[item.id] ?? ''}
                      placeholder={String(item.quantity)}
                      onChange={(e) => setEdits({ ...edits, [item.id]: e.target.value })}
                    />
                  </td>
                  <td>
                    <button
                      className="btn" disabled={edits[item.id] === undefined || edits[item.id] === ''}
                      onClick={() => saveQuantity(item)}
                    >
                      Guardar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <form className="card" onSubmit={addItem}>
        <h3>Agregar o fijar un producto</h3>
        <div className="grid">
          <div className="form-group">
            <label>Producto</label>
            <input required value={newItem.product_name}
              onChange={(e) => setNewItem({ ...newItem, product_name: e.target.value })} />
          </div>
          <div className="form-group">
            <label>Categoría</label>
            <select value={newItem.category} onChange={(e) => setNewItem({ ...newItem, category: e.target.value })}>
              {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label>Cantidad</label>
            <input type="number" min="0" required value={newItem.quantity}
              onChange={(e) => setNewItem({ ...newItem, quantity: e.target.value })} />
          </div>
        </div>
        <button type="submit" className="btn">Guardar producto</button>
      </form>

      <div className="card">
        <h3>Informe de sobrantes</h3>
        <p>Al terminar la jornada, genera el informe con las porciones que quedaron.</p>
        <button type="button" className="btn" onClick={closeDay}>Generar informe de cierre</button>

        {reports.length > 0 && (
          <div style={{ marginTop: '16px' }}>
            {reports.map((r) => (
              <details key={r.id} style={{ marginBottom: '8px' }}>
                <summary>
                  {new Date(r.created_at).toLocaleString('es-CO')} — {r.total_portions} porciones
                </summary>
                <ul>
                  {r.leftovers.map((l, i) => (
                    <li key={i}>{l.product_name} ({l.category}): {l.quantity}</li>
                  ))}
                </ul>
              </details>
            ))}
          </div>
        )}
      </div>
    </>
  );
}

export default function ComedorInventory() {
  return <DiningHallGate>{(hall) => <InventoryPanel hall={hall} />}</DiningHallGate>;
}
