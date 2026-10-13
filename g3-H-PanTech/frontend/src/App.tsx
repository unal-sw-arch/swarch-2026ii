import { useState } from 'react'
import type { Role } from './types'
import MeseroView from './views/MeseroView'
import CocineroView from './views/CocineroView'
import DespachadorView from './views/DespachadorView'
import ProductosView from './views/ProductosView'
import HistorialView from './views/HistorialView'

const ROLES: { id: Role; label: string; hint: string }[] = [
  { id: 'mesero', label: 'Mesero', hint: 'Pedidos, menú y reservas' },
  { id: 'cocinero', label: 'Cocinero', hint: 'Cola de cocina priorizada' },
  { id: 'despachador', label: 'Despachador', hint: 'Confirmar entregas' },
  { id: 'productos', label: 'Productos', hint: 'CRUD del catálogo' },
  { id: 'historial', label: 'Historial', hint: 'Comandas y tiempos de preparación' },
]

export default function App() {
  const [role, setRole] = useState<Role>('mesero')

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-logo">🥐</span>
          <div>
            <h1>Panadería Express</h1>
            <p>Sistema distribuido de pedidos, cocina y despacho</p>
          </div>
        </div>
        <nav className="roles">
          {ROLES.map((item) => (
            <button
              key={item.id}
              className={`role-button ${role === item.id ? 'active' : ''}`}
              onClick={() => setRole(item.id)}
              title={item.hint}
            >
              {item.label}
            </button>
          ))}
        </nav>
      </header>

      <main className="content">
        {role === 'mesero' && <MeseroView />}
        {role === 'cocinero' && <CocineroView />}
        {role === 'despachador' && <DespachadorView />}
        {role === 'productos' && <ProductosView />}
        {role === 'historial' && <HistorialView />}
      </main>
    </div>
  )
}
