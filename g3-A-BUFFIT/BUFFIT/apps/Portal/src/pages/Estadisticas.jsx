import React, { useCallback, useEffect, useState } from 'react';
import {
  getMenuOpciones, getStatsSummary, getStatsByDiningHall, getStatsBySlot, getStatsByUserType, getStatsDaily,
} from '../services/api';

const cop = (n) => new Intl.NumberFormat('es-CO', {
  style: 'currency', currency: 'COP', maximumFractionDigits: 0,
}).format(n || 0);
const num = (n) => new Intl.NumberFormat('es-CO').format(n || 0);

const USER_TYPE_LABELS = {
  APOYO_ALIMENTARIO: 'Apoyo alimentario (paga $0)',
  ESTUDIANTE: 'Estudiante con carnet',
  GENERAL: 'General sin carnet',
};

function Kpi({ label, value, hint }) {
  return (
    <div className="card" style={{ marginBottom: 0 }}>
      <div style={{ fontSize: '0.85rem', color: '#555' }}>{label}</div>
      <div style={{ fontSize: '1.7rem', fontWeight: 'bold', color: '#14253f' }}>{value}</div>
      {hint && <div style={{ fontSize: '0.8rem', color: '#777' }}>{hint}</div>}
    </div>
  );
}

// Barras horizontales: una fila por elemento, ancho proporcional al mayor valor
function BarList({ rows, valueKey, format = num, color = '#14253f', emptyText = 'Sin datos para este filtro.' }) {
  if (!rows.length) return <p style={{ color: '#777' }}>{emptyText}</p>;
  const max = Math.max(...rows.map((r) => r[valueKey]), 1);
  return (
    <div>
      {rows.map((r) => (
        <div key={r.label} style={{ marginBottom: '10px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.9rem' }}>
            <span>{r.label}</span>
            <strong>{format(r[valueKey])}</strong>
          </div>
          <div style={{ background: '#eee', borderRadius: '4px', height: '12px' }}>
            <div style={{
              width: `${(r[valueKey] / max) * 100}%`, height: '100%', background: color, borderRadius: '4px',
            }} />
          </div>
        </div>
      ))}
    </div>
  );
}

// Barras verticales por día
function DailyChart({ rows }) {
  if (!rows.length) return <p style={{ color: '#777' }}>Sin datos para este filtro.</p>;
  const max = Math.max(...rows.map((r) => r.subsidio_ejercido + r.subsidio_comprometido), 1);
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: '8px', height: '180px', overflowX: 'auto' }}>
      {rows.map((r) => {
        const total = r.subsidio_ejercido + r.subsidio_comprometido;
        return (
          <div key={r.fecha} style={{ flex: '0 0 56px', textAlign: 'center', fontSize: '0.75rem' }}
            title={`${r.fecha}: ${r.pedidos} pedidos, ${cop(r.subsidio_ejercido)} ejercido, ${cop(r.subsidio_comprometido)} pendiente`}>
            <div style={{ height: '130px', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end' }}>
              <div style={{ height: `${(r.subsidio_comprometido / max) * 130}px`, background: '#f9b9ac' }} />
              <div style={{ height: `${(r.subsidio_ejercido / max) * 130}px`, background: '#f67055' }} />
            </div>
            <div>{r.fecha.slice(5)}</div>
            <div style={{ color: '#777' }}>{total ? cop(total) : '—'}</div>
          </div>
        );
      })}
    </div>
  );
}

export default function Estadisticas() {
  const [filters, setFilters] = useState({ desde: '', hasta: '', comedor: '' });
  const [officialHalls, setOfficialHalls] = useState([]);
  const [halls, setHalls] = useState([]);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [summary, byHall, bySlot, byType, daily] = await Promise.all([
        getStatsSummary(filters),
        getStatsByDiningHall(filters),
        getStatsBySlot(filters),
        getStatsByUserType(filters),
        getStatsDaily(filters),
      ]);
      setData({ summary, byHall, bySlot, byType, daily });
      // Las opciones del filtro de comedor salen de los comedores que ya tienen datos
      if (!filters.comedor) setHalls(byHall.map((h) => h.comedor));
    } catch (err) {
      const d = err.response?.data;
      setError(d?.error || d?.detail || 'No se pudieron cargar las estadísticas.');
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => { load(); }, [load]);

  // Opciones del filtro: los comedores oficiales (los mismos que ve el estudiante al pedir),
  // aunque todavia no tengan pedidos. Si el menu no responde, quedan los que ya tienen datos.
  useEffect(() => {
    getMenuOpciones()
      .then((menu) => setOfficialHalls(menu.puntosRecogida || []))
      .catch(() => {});
  }, []);

  const set = (key) => (e) => setFilters({ ...filters, [key]: e.target.value });
  const s = data?.summary;

  return (
    <div className="container">
      <h1 style={{ color: '#14253f' }}>Estadísticas de subsidio de alimentación</h1>
      <p style={{ color: '#555' }}>
        Subsidio por pedido = precio sin carnet ({cop(s?.precio_referencia ?? 16500)}) − valor pagado.
        “Ejercido” cuenta pedidos entregados; “pendiente” los confirmados aún sin entregar.
        Los cancelados no cuentan.
      </p>

      <div className="card">
        <div className="grid">
          <div className="form-group">
            <label>Desde</label>
            <input type="date" value={filters.desde} onChange={set('desde')} />
          </div>
          <div className="form-group">
            <label>Hasta</label>
            <input type="date" value={filters.hasta} onChange={set('hasta')} />
          </div>
          <div className="form-group">
            <label>Comedor</label>
            <select value={filters.comedor} onChange={set('comedor')}>
              <option value="">Todos</option>
              {[...new Set([...officialHalls, ...halls])].sort().map((h) => <option key={h} value={h}>{h}</option>)}
            </select>
          </div>
        </div>
        <button className="btn" onClick={load} disabled={loading}>{loading ? 'Cargando…' : 'Actualizar'}</button>
      </div>

      {error && (
        <div style={{ padding: '10px 14px', marginBottom: '16px', borderRadius: '6px', background: '#fde8e8', color: '#9b1c1c' }}>
          {error}
        </div>
      )}

      {s && (
        <>
          <div className="grid" style={{ marginBottom: '20px' }}>
            <Kpi label="Pedidos (sin cancelados)" value={num(s.pedidos)}
              hint={`${num(s.entregados)} entregados · ${num(s.pendientes)} pendientes`} />
            <Kpi label="Estudiantes distintos" value={num(s.estudiantes_distintos)}
              hint={`${num(s.usuarios_distintos)} usuarios en total`} />
            <Kpi label="Subsidio ejercido" value={cop(s.subsidio_ejercido)}
              hint={`Promedio por entrega: ${cop(s.promedio_subsidio_por_entrega)}`} />
            <Kpi label="Subsidio pendiente" value={cop(s.subsidio_comprometido)} hint="Pedidos sin entregar" />
            <Kpi label="Recaudado" value={cop(s.recaudado)} hint="Pagado en pedidos entregados" />
            <Kpi label="Cancelados" value={num(s.cancelados)} />
          </div>

          <div className="grid">
            <div className="card">
              <h3>Pedidos por comedor</h3>
              <BarList rows={data.byHall.map((h) => ({ label: h.comedor, pedidos: h.pedidos }))} valueKey="pedidos" />
            </div>
            <div className="card">
              <h3>Estudiantes distintos por comedor</h3>
              <BarList color="#f67055"
                rows={data.byHall.map((h) => ({ label: h.comedor, v: h.estudiantes_distintos }))} valueKey="v" />
            </div>
            <div className="card">
              <h3>Subsidio ejercido por comedor</h3>
              <BarList format={cop}
                rows={data.byHall.map((h) => ({ label: h.comedor, v: h.subsidio_ejercido }))} valueKey="v" />
            </div>
            <div className="card">
              <h3>Subsidio ejercido por tipo de usuario</h3>
              <BarList format={cop} color="#f67055"
                rows={data.byType.map((t) => ({
                  label: USER_TYPE_LABELS[t.tipo_usuario] || t.tipo_usuario, v: t.subsidio_ejercido,
                }))} valueKey="v" />
            </div>
            <div className="card">
              <h3>Pedidos por franja horaria</h3>
              <BarList rows={data.bySlot.map((f) => ({ label: f.franja || 'Sin franja', v: f.pedidos }))} valueKey="v" />
            </div>
          </div>

          <div className="card">
            <h3>Subsidio por día</h3>
            <div style={{ fontSize: '0.8rem', marginBottom: '8px' }}>
              <span style={{ color: '#f67055' }}>■</span> Ejercido &nbsp;
              <span style={{ color: '#f9b9ac' }}>■</span> Pendiente
            </div>
            <DailyChart rows={data.daily} />
          </div>

          <div className="card">
            <h3>Detalle por comedor</h3>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ textAlign: 'left', borderBottom: '2px solid #ddd' }}>
                    <th>Comedor</th><th>Pedidos</th><th>Estudiantes</th><th>Entregados</th>
                    <th>Cancelados</th><th>Subsidio ejercido</th><th>Subsidio pendiente</th>
                  </tr>
                </thead>
                <tbody>
                  {data.byHall.map((h) => (
                    <tr key={h.comedor} style={{ borderBottom: '1px solid #eee' }}>
                      <td>{h.comedor}</td><td>{num(h.pedidos)}</td><td>{num(h.estudiantes_distintos)}</td>
                      <td>{num(h.entregados)}</td><td>{num(h.cancelados)}</td>
                      <td>{cop(h.subsidio_ejercido)}</td><td>{cop(h.subsidio_comprometido)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
