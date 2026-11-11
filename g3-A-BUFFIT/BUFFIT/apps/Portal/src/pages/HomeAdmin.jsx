import React, { useEffect, useState, useContext } from 'react';
import { getAllUsers, toggleUser, deleteUser } from '../services/api'; 
import { AuthContext } from '../context/AuthContext';

export default function AdminHome() {
  // acciones en los usuarios
  const [userToToggleStatus, setUserToToggleStatus] = useState(null);
  const [blockReason, setBlockReason] = useState('');
  const [userToDelete, setUserToDelete] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorModal, setErrorModal] = useState('');
  const [succesModal, setSuccesModal] = useState('');
  const { user, logout } = useContext(AuthContext);

  const [isFiltersOpen, setIsFiltersOpen] = useState(false);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Estados para el buscador
  const [searchTerm, setSearchTerm] = useState('')
  const [filters, setFilters] = useState({
    role:'',
    user_type:'',
    is_active:''
  });
  const searchCriteria = ['email', 'name'];

  const handleToggleSubmit = async () => {
    setIsSubmitting(true);
    console.log("Enviando al backend:", {
      id: userToToggleStatus.id,
      accion: userToToggleStatus.is_active ? 'desactivar' : 'activar',
      razon: userToToggleStatus.is_active ? blockReason : null
    });
    const action=userToToggleStatus.is_active ? 'desactivar' : 'activar';

    try {
      const data = await toggleUser(
        userToToggleStatus.id, 
        action, 
        userToToggleStatus.is_active ? String(blockReason) : null
      );
      setSuccesModal(`Usuario ${action}do con exito`);
      setUsers(prevUsers => prevUsers.map(u => 
          u.id === userToToggleStatus.id ? { ...u, is_active: !u.is_active } : u
      ));
    } catch (err) {
      setErrorModal(err.response?.data?.error || 'Error al modificar al usuario');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeleteSubmit = async () => {
    setIsSubmitting(true);
    console.log("Eliminando usuario:", userToDelete.id);
    try {
      const data = await deleteUser(userToDelete.id);
      setUsers(prevUsers => prevUsers.filter(u => 
        u.id !== userToDelete.id
      ));
    setSuccesModal('Usuario eliminado');
    } catch (err) {
      setErrorModal(err.response?.data?.error || 'Error al eliminar al usuario');
    } finally {
      setIsSubmitting(false);
    }
  };
  
  const handleFilterChange = (e) => {
    const { name, value } = e.target;
    setFilters(prevFilters => ({
      ...prevFilters,
      [name]: value
    }));
  };

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        const data = await getAllUsers();
        setUsers(data);
      } catch (err) {
        setError(err.response?.data?.error || 'Error al obtener la lista de usuarios');
      } finally {
        setLoading(false);
      }
    };

    fetchUsers();
  }, []);

  const filteredUsers = users.filter(u => {
    if (u.id==user?.id) return false;
    const fisrtFilter = Object.entries(filters).every(([key, term]) => {
      if (!term) return true; 
      const valueToSearch = String(u[key] || '').toLowerCase();
      return valueToSearch.includes(term.toLowerCase());
    });

    if(!fisrtFilter) return false;

    if (searchTerm){
      const term = searchTerm.toLowerCase();
      const valueToSearch = searchCriteria.some(criteria =>{
        const value=String(user[criteria] || '').toLowerCase();
        return value.includes(term);
      }); 
      if (!valueToSearch) return false;
    } return true;
    

  });

  return (
    <div style={{ padding: '20px', maxWidth: '800px', margin: '40px auto' }}>
      <h1>Panel de Administrador</h1>
      <p>Bienvenido. Aquí puedes gestionar todos los usuarios del sistema.</p>

      <div style={{
        display: 'flex', gap: '10px', marginBottom: '20px', 
        padding: '15px', backgroundColor: '#f5f5f5', borderRadius: '8px'
      }}>

        <input 
          type="text" 
          placeholder={`Buscar...`}
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{ flex: 1, padding: '8px', borderRadius: '4px', border: '1px solid #ccc' }}
        />
      
      </div>
      <div style={{ position: 'relative', display: 'inline-block', marginBottom:'25px' }}>
      {/* Botón para abrir/cerrar */}
      <button 
        onClick={() => setIsFiltersOpen(!isFiltersOpen)}
        className="btn" style={{ width: '70%'} }
      >
        {isFiltersOpen ? 'Ocultar Filtros' : 'Mostrar Filtros'}
      </button>

      {/* Menú desplegable */}
      {isFiltersOpen && (
        <div style={{ 
          position: 'absolute', 
          top: '100%', 
          left: 0, 
          marginTop: '8px',
          padding: '16px', 
          border: '1px solid #ccc', 
          backgroundColor: '#fff',
          borderRadius: '8px',
          boxShadow: '0 4px 6px rgba(0,0,0,0.1)',
          display: 'flex',
          flexDirection: 'column',
          gap: '12px',
          minWidth: '200px',
          zIndex: 10
        }}>
          
          {/* Filtro: Role */}
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <label style={{ fontWeight: 'bold', marginBottom: '4px' }}>Role:</label>
            <select name="role" value={filters.role} onChange={handleFilterChange}>
              <option value="">Ninguno</option>
              <option value="Usuario">Usuario</option>
              <option value="Comedor">Comedor</option>
              <option value="Administrador">Administrador</option>
            </select>
          </div>

          {/* Filtro: Tipo de Usuario */}
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <label style={{ fontWeight: 'bold', marginBottom: '4px' }}>Tipo de usuario:</label>
            <select name="user_type" value={filters.user_type} onChange={handleFilterChange}>
              <option value="">Ninguno</option>
              <option value="ESTUDIANTE">Estudiante con carnet</option>
              <option value="APOYO_ALIMENTARIO">Apoyo Alimentario</option>
              <option value="GENERAL">General sin carnet</option>
            </select>
          </div>

          {/* Filtro: ¿Está activo? */}
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <label style={{ fontWeight: 'bold', marginBottom: '4px' }}>¿Está activo?:</label>
            <select name="is_active" value={filters.is_active} onChange={handleFilterChange}>
              <option value="">Ninguno</option>
              <option value="true">Activo</option>
              <option value="false">Inactivo</option>
            </select>
          </div>

        </div>
      )}
    </div>

      {loading && <p>Cargando usuarios...</p>}
      {error && <p style={{ color: 'red', fontWeight: 'bold' }}>{error}</p>}
      
      {!loading && !error && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {filteredUsers.length > 0 ? (
            filteredUsers.map((user) => (
              <details 
                key={user.id} 
                style={{ 
                  backgroundColor: '#fff', 
                  border: '1px solid #ddd', 
                  borderRadius: '6px', 
                  padding: '10px' 
                }}
              >
                <summary style={{ cursor: 'pointer', fontWeight: 'bold', color: '#14253f' }}>
                  {user.name} ({user.email})
                </summary>
                
                <div style={{ marginTop: '10px', paddingLeft: '15px', borderLeft: '3px solid #f16e58' }}>
                  <p><strong>ID:</strong> {user.id}</p>
                  <p><strong>Rol:</strong> {user.role}</p>
                  <p><strong>Tipo de Usuario:</strong> {user.user_type}</p>
                  <p><strong>Estado:</strong> {user.is_active ? '✅ Activo' : '❌ Inactivo'}</p>
                  {user.is_active? '': <p><strong>Razon del bloqueo:</strong> {String(user.notes_state)}</p>}
                  <p><strong>Fecha de creación:</strong> {new Date(user.created_at).toLocaleDateString()}</p>
                </div>

                {/*Botones de Acción */}
                  <div style={{ marginTop: '15px', display: 'flex', gap: '10px' }}>
                    <button 
                      onClick={() => setUserToToggleStatus(user)}
                      style={{ 
                        padding: '6px 12px', 
                        backgroundColor: user.is_active ? '#f0ad4e' : '#5cb85c', 
                        color: 'white', 
                        border: 'none', 
                        borderRadius: '4px', 
                        cursor: 'pointer' 
                      }}
                    >
                      {user.is_active ? 'Inhabilitar' : 'Habilitar'}
                    </button>
                    
                    <button 
                      onClick={() => setUserToDelete(user)}
                      style={{ 
                        padding: '6px 12px', 
                        backgroundColor: '#d9534f', 
                        color: 'white', 
                        border: 'none', 
                        borderRadius: '4px', 
                        cursor: 'pointer' 
                      }}
                    >
                      Eliminar
                    </button>
                  </div>
              </details>
            ))
          ) : (
            <p style={{ color: '#666' }}>No se encontraron usuarios con esos criterios.</p>
          )}
        </div>
      )}

    {/*INHABILITAR / HABILITAR USUARIO */}
      {userToToggleStatus && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.5)', zIndex: 1000,
          display: 'flex', justifyContent: 'center', alignItems: 'center'
        }}>
          <div style={{
            backgroundColor: '#fff', padding: '25px', borderRadius: '8px', 
            width: '90%', maxWidth: '400px', boxShadow: '0 4px 6px rgba(0,0,0,0.1)'
          }}>

            {(!succesModal && !errorModal) ?(
            <>
              <h2 style={{ marginTop: 0, color: '#14253f' }}>
                {userToToggleStatus.is_active ? 'Inhabilitar Usuario' : 'Habilitar Usuario'}
              </h2>
              
              <p>¿Estás seguro de que deseas {userToToggleStatus.is_active ? 'bloquear' : 'reactivar'} a <strong>{userToToggleStatus.name}</strong>?</p>
              
              {/* Campo de texto que si se va a inhabilitar */}
              {userToToggleStatus.is_active && (
                <div style={{ marginTop: '15px' }}>
                  <label style={{ display: 'block', marginBottom: '8px', fontWeight: 'bold', fontSize: '0.9rem' }}>
                    Razón del bloqueo:
                  </label>
                  <textarea 
                    value={blockReason}
                    onChange={(e) => setBlockReason(e.target.value)}
                    placeholder="Ej: Violación de términos, cuenta duplicada..."
                    style={{ 
                      width: '100%', height: '80px', padding: '10px', 
                      borderRadius: '4px', border: '1px solid #ccc',
                      resize: 'none', boxSizing: 'border-box'
                    }}
                  />
                </div>
              )}
            
            </>
            ):('')}

            {/* ZONA DE MENSAJES DE RESPUESTA */}
            {errorModal && (
              <div style={{ padding: '10px', backgroundColor: '#f8d7da', color: '#721c24', borderRadius: '4px', marginTop: '10px' }}>
                {errorModal}
              </div>
            )}
            {succesModal && (
              <div style={{ padding: '10px', backgroundColor: '#d4edda', color: '#155724', borderRadius: '4px', marginTop: '10px' }}>
                {succesModal}
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
              <button 
                onClick={() => { setUserToToggleStatus(null); setBlockReason(''); setErrorModal(''); setSuccesModal('');}}
                disabled={isSubmitting}
                style={{ padding: '8px 15px', border: '1px solid #ccc', 
                  backgroundColor: (succesModal || errorModal)? '#14253f':'#f9f9f9', 
                  borderRadius: '4px', cursor: isSubmitting? 'not-allowed':'pointer' }}
              >
                {(succesModal || errorModal)? 'Cerrar' : 'Cancelar'}
              </button>
              <button 
                onClick={handleToggleSubmit}
                disabled={isSubmitting || (userToToggleStatus.is_active && !blockReason.trim())}
                style={{ 
                  padding: '8px 15px', border: 'none', borderRadius: '4px', cursor: 'pointer',
                  backgroundColor: isSubmitting || (userToToggleStatus.is_active && !blockReason.trim()) ? '#ccc' : '#14253f', 
                  color: 'white',
                  display: (succesModal || errorModal)? 'none':'block' 
                }}
              >
                {isSubmitting? 'Procesando...':'Confirmar'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ELIMINAR USUARIO*/}
      {userToDelete && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.5)', zIndex: 1000,
          display: 'flex', justifyContent: 'center', alignItems: 'center'
        }}>
          <div style={{
            backgroundColor: '#fff', padding: '25px', borderRadius: '8px', 
            width: '90%', maxWidth: '400px', borderTop: '5px solid #d9534f'
          }}>
          
          {(!succesModal && !errorModal)?(
            <>
              <h2 style={{ marginTop: 0, color: '#d9534f' }}>Eliminar Usuario</h2>
              <p>Estás a punto de eliminar permanentemente a <strong>{userToDelete.name}</strong>.</p>
              <p style={{ fontSize: '0.85rem', color: '#666' }}>Esta acción es irreversible y podría afectar los registros de pedidos asociados a este usuario.</p>
            </>
          ):('')}

          {/* ZONA DE MENSAJES DE RESPUESTA */}
            {errorModal && (
              <div style={{ padding: '10px', backgroundColor: '#f8d7da', color: '#721c24', borderRadius: '4px', marginTop: '10px' }}>
                {errorModal}
              </div>
            )}
            {succesModal && (
              <div style={{ padding: '10px', backgroundColor: '#d4edda', color: '#155724', borderRadius: '4px', marginTop: '10px' }}>
                {succesModal}
              </div>
            )}
            
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
              <button 
                onClick={() => {setUserToDelete(null); setBlockReason(''); setErrorModal(''); setSuccesModal('');}}
                style={{ padding: '8px 15px', border: '1px solid #ccc', 
                  backgroundColor: (succesModal || errorModal)? '#14253f':'#f9f9f9', 
                  borderRadius: '4px', cursor: isSubmitting? 'not-allowed':'pointer' }}
              >
                {(succesModal || errorModal)? 'Cerrar' : 'Cancelar'}
              </button>
              <button 
                onClick={handleDeleteSubmit}
                style={{ padding: '8px 15px', border: 'none', borderRadius: '4px', 
                  backgroundColor: '#d9534f', 
                  color: 'white', 
                  display: (succesModal || errorModal)? 'none':'block' }}
              >
                {isSubmitting? 'Procesando...':'Si, eliminar'}
              </button>
            </div>
          </div>
        </div>
      )}
    
    </div>
  );
}