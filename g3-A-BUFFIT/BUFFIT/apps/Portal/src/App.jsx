import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import Login from './pages/Login';
import Register from './pages/Register';
import Home from './pages/Home';
import HomeAdmin from './pages/HomeAdmin';
import OrderSummary from './pages/OrderSummary';
import MyOrdersList from './components/MyOrdersList';
import DiningHallDashboard from './components/DiningHallDashboard';
import ComedorInventory from './pages/ComedorInventory';
import Estadisticas from './pages/Estadisticas';
import './App.css';

// El personal del comedor tiene su propio flujo (/comedor); no arma pedidos.
const CONSUMER_ONLY = ['Comedor'];
const COMEDOR_ROLES = ['Comedor'];
const STATS_ROLES = ['Comedor', 'Administrador'];
const ADMIN_ROLES = ['Administrador'];

export default function App() {
  return (
    <AuthProvider>
      <Router>
        <Navbar />
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route
            path="/"
            element={
              <ProtectedRoute excludeRoles={CONSUMER_ONLY}>
                <Home />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin"
            element={
              <ProtectedRoute roles={ADMIN_ROLES}>
                <HomeAdmin />
              </ProtectedRoute>
            }
          />
          <Route
            path="/resumen"
            element={
              <ProtectedRoute excludeRoles={CONSUMER_ONLY}>
                <OrderSummary />
              </ProtectedRoute>
            }
          />
          <Route
            path="/mis-pedidos"
            element={
              <ProtectedRoute excludeRoles={CONSUMER_ONLY}>
                <MyOrdersList />
              </ProtectedRoute>
            }
          />
          <Route
            path="/comedor"
            element={
              <ProtectedRoute roles={COMEDOR_ROLES}>
                <DiningHallDashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/comedor/inventario"
            element={
              <ProtectedRoute roles={COMEDOR_ROLES}>
                <ComedorInventory />
              </ProtectedRoute>
            }
          />
          <Route
            path="/estadisticas"
            element={
              <ProtectedRoute roles={STATS_ROLES}>
                <Estadisticas />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Router>
    </AuthProvider>
  );
}
