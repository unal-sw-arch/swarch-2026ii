import { useState, useCallback } from "react";
import { Icon, Modal, ToastContainer, useToast } from '../../../../shared';
import { AdminNavbar } from '../../components/AdminNavbar/AdminNavbar';
import { AdminSidebar } from '../../components/AdminSidebar/AdminSidebar';
import { adminServiceApi, categoryApi } from '../../../../shared/api';

import './AdminServicesPage.css';

export function AdminServicesPage() {
    const { toasts, showToast } = useToast();
    const [filters, setFilters] = useState({
        name: '',
        categoryId: '',
        offererId: '',
        minPrice: '',
        maxPrice: '',
        available: '',
        minRating: '',
        maxRating: '',
    });
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searched, setSearched] = useState(false);
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [categories, setCategories] = useState([]);

    const loadCategories = useCallback(async () => {
        try {
            const data = await categoryApi.getCategories();
            setCategories(Array.isArray(data) ? data : data.content ?? []);
        } catch {
            // Silently ignore — category filter simply won't be populated
        }
    }, []);

    const handleOpenFilters = () => {
        if (categories.length === 0) loadCategories();
    };

    const handleChange = (field, value) => {
        setFilters((prev) => ({ ...prev, [field]: value }));
    };

    const handleSearch = useCallback(async () => {
        setLoading(true);
        setSearched(true);
        try {
            const params = {};
            if (filters.name) params.name = filters.name;
            if (filters.categoryId) params.categoryId = filters.categoryId;
            if (filters.offererId) params.offererId = filters.offererId;
            if (filters.minPrice) params.minPrice = filters.minPrice;
            if (filters.maxPrice) params.maxPrice = filters.maxPrice;
            if (filters.available) params.available = filters.available;
            if (filters.minRating) params.minRating = filters.minRating;
            if (filters.maxRating) params.maxRating = filters.maxRating;
            params.page = 0;
            params.size = 100;

            const data = await adminServiceApi.search(params);
            setResults(data.content ?? []);
        } catch {
            showToast('No fue posible buscar servicios', 'danger');
        } finally {
            setLoading(false);
        }
    }, [filters, showToast]);

    const handleDeleteClick = (service) => {
        setDeleteTarget(service);
    };

    const handleDeleteConfirm = async () => {
        if (!deleteTarget) return;
        try {
            await adminServiceApi.deleteService(deleteTarget.id);
            showToast('Servicio eliminado correctamente', 'success');
            setResults((prev) => prev.filter((s) => s.id !== deleteTarget.id));
            setDeleteTarget(null);
        } catch (err) {
            showToast(err.message || 'No fue posible eliminar el servicio', 'danger');
        }
    };

    const hasActiveFilter = Object.values(filters).some((v) => v !== '');

    return (
        <>
            <AdminNavbar />

            <div className="admin-layout-container">
                <AdminSidebar />

                <main className="main-content">
                    <div className="ph">
                        <h1>Gestión de servicios (RF-064)</h1>
                        <p>Busca y elimina servicios del marketplace</p>
                    </div>

                    {/* Filtros de búsqueda */}
                    <div className="services-filters card" style={{ marginBottom: '18px' }} onClick={handleOpenFilters}>
                        <div className="services-filters-grid">
                            <div className="input-group">
                                <label className="label">Nombre</label>
                                <input
                                    className="input"
                                    type="text"
                                    placeholder="Buscar por nombre..."
                                    value={filters.name}
                                    onChange={(e) => handleChange('name', e.target.value)}
                                />
                            </div>
                            <div className="input-group">
                                <label className="label">Categoría</label>
                                <select
                                    className="input"
                                    value={filters.categoryId}
                                    onChange={(e) => handleChange('categoryId', e.target.value)}
                                >
                                    <option value="">Todas las categorías</option>
                                    {categories.map((cat) => (
                                        <option key={cat.id} value={cat.id}>
                                            {cat.name}
                                        </option>
                                    ))}
                                </select>
                            </div>
                            <div className="input-group">
                                <label className="label">ID Oferente</label>
                                <input
                                    className="input"
                                    type="number"
                                    placeholder="Ej: 2"
                                    value={filters.offererId}
                                    onChange={(e) => handleChange('offererId', e.target.value)}
                                />
                            </div>
                            <div className="input-group">
                                <label className="label">Precio mínimo</label>
                                <input
                                    className="input"
                                    type="number"
                                    min="0"
                                    placeholder="0"
                                    value={filters.minPrice}
                                    onChange={(e) => handleChange('minPrice', e.target.value)}
                                />
                            </div>
                            <div className="input-group">
                                <label className="label">Precio máximo</label>
                                <input
                                    className="input"
                                    type="number"
                                    min="0"
                                    placeholder="999999"
                                    value={filters.maxPrice}
                                    onChange={(e) => handleChange('maxPrice', e.target.value)}
                                />
                            </div>
                            <div className="input-group">
                                <label className="label">Disponibilidad</label>
                                <select
                                    className="input"
                                    value={filters.available}
                                    onChange={(e) => handleChange('available', e.target.value)}
                                >
                                    <option value="">Todos</option>
                                    <option value="true">Activos</option>
                                    <option value="false">Inactivos</option>
                                </select>
                            </div>
                            <div className="input-group">
                                <label className="label">Rating mínimo</label>
                                <input
                                    className="input"
                                    type="number"
                                    min="1"
                                    max="5"
                                    step="0.1"
                                    placeholder="1"
                                    value={filters.minRating}
                                    onChange={(e) => handleChange('minRating', e.target.value)}
                                />
                            </div>
                            <div className="input-group">
                                <label className="label">Rating máximo</label>
                                <input
                                    className="input"
                                    type="number"
                                    min="1"
                                    max="5"
                                    step="0.1"
                                    placeholder="5"
                                    value={filters.maxRating}
                                    onChange={(e) => handleChange('maxRating', e.target.value)}
                                />
                            </div>
                        </div>
                        <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
                            <button className="btn btn-primary btn-sm" onClick={handleSearch} disabled={loading}>
                                {loading ? 'Buscando...' : 'Buscar'}
                            </button>
                            {hasActiveFilter && (
                                <button
                                    className="btn btn-ghost btn-sm"
                                    style={{ border: '1px solid var(--c-border)' }}
                                    onClick={() => {
                                        setFilters({ name: '', categoryId: '', offererId: '', minPrice: '', maxPrice: '', available: '', minRating: '', maxRating: '' });
                                        setResults([]);
                                        setSearched(false);
                                    }}
                                >
                                    Limpiar
                                </button>
                            )}
                        </div>
                    </div>

                    {/* Resultados */}
                    {searched && !loading && results.length === 0 && (
                        <div className="card" style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--c-mid)' }}>
                            No se encontraron servicios con los filtros indicados.
                        </div>
                    )}

                    {results.length > 0 && (
                        <div>
                            <div style={{ fontSize: '13px', color: 'var(--c-mid)', marginBottom: '10px' }}>
                                {results.length} resultado{results.length !== 1 ? 's' : ''}
                            </div>
                            {results.map((svc) => (
                                <div key={svc.id} className="card service-result-card">
                                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px', marginBottom: '10px' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                            <span className={`badge ${svc.active ? 'badge-success' : 'badge-danger'}`}>
                                                {svc.active ? 'Activo' : 'Inactivo'}
                                            </span>
                                            {svc.averageRating != null && (
                                                <span className="badge badge-gray">
                                                    {'★'.repeat(Math.round(svc.averageRating))}{'☆'.repeat(5 - Math.round(svc.averageRating))}
                                                    {' '}({svc.totalRatings ?? 0})
                                                </span>
                                            )}
                                        </div>
                                        <span style={{ fontSize: '12px', color: 'var(--c-soft)' }}>
                                            ID: {svc.id} · Oferente: {svc.offererName ?? `#${svc.offererId}`}
                                        </span>
                                    </div>

                                    <div style={{ fontWeight: 700, fontSize: '14px', marginBottom: '6px' }}>
                                        {svc.title}
                                    </div>

                                    {svc.description && (
                                        <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginBottom: '10px', lineHeight: 1.4 }}>
                                            {svc.description.length > 120 ? svc.description.slice(0, 120) + '...' : svc.description}
                                        </div>
                                    )}

                                    <div style={{ display: 'flex', gap: '14px', marginBottom: '10px', flexWrap: 'wrap', fontSize: '13px' }}>
                                        {svc.priceHourly != null && (
                                            <div>
                                                <span style={{ color: 'var(--c-soft)', fontSize: '11px', textTransform: 'uppercase' }}>Precio/hora </span>
                                                <span style={{ fontWeight: 600 }}>${svc.priceHourly.toLocaleString('es-CO')}</span>
                                            </div>
                                        )}
                                        {svc.averageDurationMinutes != null && (
                                            <div>
                                                <span style={{ color: 'var(--c-soft)', fontSize: '11px', textTransform: 'uppercase' }}>Duración </span>
                                                <span>{svc.averageDurationMinutes} min</span>
                                            </div>
                                        )}
                                        {svc.operationRadiusKm != null && (
                                            <div>
                                                <span style={{ color: 'var(--c-soft)', fontSize: '11px', textTransform: 'uppercase' }}>Radio </span>
                                                <span>{svc.operationRadiusKm} km</span>
                                            </div>
                                        )}
                                        {svc.createdAt && (
                                            <div>
                                                <span style={{ color: 'var(--c-soft)', fontSize: '11px', textTransform: 'uppercase' }}>Creado </span>
                                                <span>{new Date(svc.createdAt).toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })}</span>
                                            </div>
                                        )}
                                    </div>

                                    <div style={{ display: 'flex', gap: '7px' }}>
                                        <button className="btn btn-danger btn-sm" onClick={() => handleDeleteClick(svc)}>
                                            <Icon name="trash" size={14} />
                                            Eliminar servicio
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </main>
            </div>

            {/* Modal de confirmación de eliminación */}
            <Modal open={!!deleteTarget} onClose={() => setDeleteTarget(null)}>
                <div className="modal-title">Eliminar servicio (RF-064)</div>
                <div className="modal-sub">
                    Se eliminará permanentemente el servicio del marketplace. Esta acción no se puede deshacer.
                </div>
                {deleteTarget && (
                    <div className="delete-service-preview">
                        <div style={{ fontWeight: 700, fontSize: '14px', marginBottom: '4px' }}>{deleteTarget.title}</div>
                        <div style={{ fontSize: '12px', color: 'var(--c-mid)' }}>
                            ID: {deleteTarget.id} · Oferente: {deleteTarget.offererName ?? `#${deleteTarget.offererId}`}
                        </div>
                    </div>
                )}
                <div style={{ display: 'flex', gap: '8px', marginTop: '16px' }}>
                    <button className="btn btn-ghost btn-full" onClick={() => setDeleteTarget(null)}>Cancelar</button>
                    <button className="btn btn-danger btn-full" onClick={handleDeleteConfirm}>
                        <Icon name="trash" size={15} />
                        Confirmar eliminación
                    </button>
                </div>
            </Modal>

            <ToastContainer toasts={toasts} />
        </>
    );
}

export default AdminServicesPage;
