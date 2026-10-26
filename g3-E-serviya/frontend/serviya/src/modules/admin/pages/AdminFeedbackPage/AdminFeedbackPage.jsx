import { useState, useCallback, useEffect } from "react";
import { AdminNavbar } from '../../components/AdminNavbar/AdminNavbar';
import { AdminSidebar } from '../../components/AdminSidebar/AdminSidebar';
import { ToastContainer } from '../../../../shared/components/Toast/Toast';
import { useToast } from '../../../../shared/hooks/useToast';
import { adminFeedbackApi } from '../../../../shared/api';
import { userApi } from '../../../../shared/api';
import { DirectRemoveModal } from '../../components/DirectRemoveModal/DirectRemoveModal';

import './AdminFeedbackPage.css';

const FEEDBACK_TYPE_LABELS = {
    SERVICE: 'Reseña de servicio',
    CLIENT: 'Reseña de cliente',
};

export function AdminFeedbackPage() {
    const { toasts, showToast } = useToast();
    const [filters, setFilters] = useState({
        clientId: '',
        offererId: '',
        serviceId: '',
        keyword: '',
        ratingMin: '',
        ratingMax: '',
    });
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searched, setSearched] = useState(false);
    const [selectedFeedback, setSelectedFeedback] = useState(null);
    const [nameCache, setNameCache] = useState({});

    // Resolve display names for user IDs to avoid N+1 calls
    const resolveName = useCallback(async (userId) => {
        if (!userId) return `Usuario ${userId}`;
        if (nameCache[userId]) return nameCache[userId];

        try {
            const name = await userApi.getDisplayName(userId);
            setNameCache((prev) => ({ ...prev, [userId]: name }));
            return name;
        } catch {
            const fallback = `Usuario ${userId}`;
            setNameCache((prev) => ({ ...prev, [userId]: fallback }));
            return fallback;
        }
    }, [nameCache]);

    const handleChange = (field, value) => {
        setFilters((prev) => ({ ...prev, [field]: value }));
    };

    const handleSearch = useCallback(async () => {
        setLoading(true);
        setSearched(true);
        try {
            const params = {};
            if (filters.clientId) params.clientId = filters.clientId;
            if (filters.offererId) params.offererId = filters.offererId;
            if (filters.serviceId) params.serviceId = filters.serviceId;
            if (filters.keyword) params.keyword = filters.keyword;
            if (filters.ratingMin) params.ratingMin = filters.ratingMin;
            if (filters.ratingMax) params.ratingMax = filters.ratingMax;
            params.page = 0;
            params.size = 100;

            const data = await adminFeedbackApi.search(params);
            setResults(data.content ?? []);
        } catch {
            showToast('No fue posible buscar el feedback', 'danger');
        } finally {
            setLoading(false);
        }
    }, [filters, showToast]);

    // Resolve names for author and target when results load
    useEffect(() => {
        if (results.length > 0) {
            const uniqueIds = new Set();
            results.forEach((item) => {
                if (item.authorId) uniqueIds.add(item.authorId);
                if (item.targetId) uniqueIds.add(item.targetId);
            });
            uniqueIds.forEach((id) => {
                if (!nameCache[id]) {
                    resolveName(id);
                }
            });
        }
    }, [results, nameCache, resolveName]);

    const handleRemove = async (item) => {
        setSelectedFeedback(item);
    };

    const handleRemoveConfirm = async (payload) => {
        try {
            await adminFeedbackApi.removeDirect(payload);
            showToast('Reseña eliminada correctamente', 'success');
            setResults((prev) => prev.filter((r) => r.feedbackId !== payload.targetId));
            setSelectedFeedback(null);
        } catch (err) {
            showToast(err.message || 'No fue posible eliminar la reseña', 'danger');
        }
    };

    const handleRevert = async (item) => {
        if (!window.confirm('¿Revertir esta reseña? Esta acción no se puede deshacer.')) return;
        try {
            await adminFeedbackApi.removeDirect({
                targetType: item.feedbackType,
                targetId: item.feedbackId,
                reportedUserId: item.authorId,
                category: 'ADMIN_DIRECT',
                reason: 'Eliminación directa desde panel de administración',
            });
            showToast('Reseña revertida correctamente', 'success');
            setResults((prev) => prev.filter((r) => r.feedbackId !== item.feedbackId));
        } catch (err) {
            showToast(err.message || 'No fue posible revertir la reseña', 'danger');
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
                        <h1>Búsqueda de reseñas (RF-048)</h1>
                        <p>Busca y gestiona reseñas de servicio y de cliente</p>
                    </div>

                    {/* Filtros de búsqueda */}
                    <div className="feedback-filters card" style={{ marginBottom: '18px' }}>
                        <div className="feedback-filters-grid">
                            <div className="input-group">
                                <label className="label">ID Cliente</label>
                                <input
                                    className="input"
                                    type="number"
                                    placeholder="Ej: 1"
                                    value={filters.clientId}
                                    onChange={(e) => handleChange('clientId', e.target.value)}
                                />
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
                                <label className="label">ID Servicio</label>
                                <input
                                    className="input"
                                    type="number"
                                    placeholder="Ej: 5"
                                    value={filters.serviceId}
                                    onChange={(e) => handleChange('serviceId', e.target.value)}
                                />
                            </div>
                            <div className="input-group">
                                <label className="label">Palabra clave</label>
                                <input
                                    className="input"
                                    type="text"
                                    placeholder="Buscar en comentario..."
                                    value={filters.keyword}
                                    onChange={(e) => handleChange('keyword', e.target.value)}
                                />
                            </div>
                            <div className="input-group">
                                <label className="label">Rating mínimo</label>
                                <input
                                    className="input"
                                    type="number"
                                    min="1"
                                    max="5"
                                    placeholder="1"
                                    value={filters.ratingMin}
                                    onChange={(e) => handleChange('ratingMin', e.target.value)}
                                />
                            </div>
                            <div className="input-group">
                                <label className="label">Rating máximo</label>
                                <input
                                    className="input"
                                    type="number"
                                    min="1"
                                    max="5"
                                    placeholder="5"
                                    value={filters.ratingMax}
                                    onChange={(e) => handleChange('ratingMax', e.target.value)}
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
                                        setFilters({ clientId: '', offererId: '', serviceId: '', keyword: '', ratingMin: '', ratingMax: '' });
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
                            No se encontraron reseñas con los filtros indicados.
                        </div>
                    )}

                    {results.length > 0 && (
                        <div>
                            <div style={{ fontSize: '13px', color: 'var(--c-mid)', marginBottom: '10px' }}>
                                {results.length} resultado{results.length !== 1 ? 's' : ''}
                            </div>
                            {results.map((item) => (
                                <div key={`${item.feedbackType}-${item.feedbackId}`} className="card feedback-result-card">
                                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px', marginBottom: '10px' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                            <span className={`badge ${item.feedbackType === 'SERVICE' ? 'badge-warn' : 'badge-primary'}`}>
                                                {FEEDBACK_TYPE_LABELS[item.feedbackType] ?? item.feedbackType}
                                            </span>
                                            {item.rating != null && (
                                                <span className="badge badge-gray">
                                                    {'★'.repeat(item.rating)}{'☆'.repeat(5 - item.rating)}
                                                </span>
                                            )}
                                        </div>
                                        <span style={{ fontSize: '12px', color: 'var(--c-soft)' }}>
                                            ID: {item.feedbackId} · Solicitud: {item.requestId}
                                        </span>
                                    </div>

                                    <div style={{ display: 'flex', gap: '14px', marginBottom: '10px', flexWrap: 'wrap', fontSize: '13px' }}>
                                        <div>
                                            <span style={{ color: 'var(--c-soft)', fontSize: '11px', textTransform: 'uppercase' }}>Autor </span>
                                            <span style={{ fontWeight: 600 }}>
                                                {nameCache[item.authorId] ? nameCache[item.authorId] : `Usuario #${item.authorId}`}
                                            </span>
                                        </div>
                                        <div>
                                            <span style={{ color: 'var(--c-soft)', fontSize: '11px', textTransform: 'uppercase' }}>Destino </span>
                                            <span style={{ fontWeight: 600 }}>
                                                {item.feedbackType === 'SERVICE'
                                                    ? nameCache[item.targetId]
                                                        ? nameCache[item.targetId]
                                                        : `Servicio #${item.targetId}`
                                                    : nameCache[item.targetId]
                                                        ? nameCache[item.targetId]
                                                        : `Cliente #${item.targetId}`}
                                            </span>
                                        </div>
                                        {item.createdAt && (
                                            <div>
                                                <span style={{ color: 'var(--c-soft)', fontSize: '11px', textTransform: 'uppercase' }}>Fecha </span>
                                                <span>{new Date(item.createdAt).toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })}</span>
                                            </div>
                                        )}
                                    </div>

                                    {item.comment && (
                                        <div style={{ background: 'var(--c-bg-s)', borderRadius: 'var(--r-lg)', padding: '10px 12px', fontSize: '12px', color: 'var(--c-mid)', marginBottom: '10px' }}>
                                            "{item.comment}"
                                        </div>
                                    )}

                                    <div style={{ display: 'flex', gap: '7px' }}>
                                        <button className="btn btn-danger btn-sm" onClick={() => handleRemove(item)}>
                                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: '14px', marginRight: '2px' }}><polyline points="3 6 5 6 21 6" /><path d="M19 6l-1 14H6L5 6" /></svg>
                                            Eliminar reseña
                                        </button>
                                        <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={() => handleRevert(item)}>
                                            Revertir
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </main>
            </div>

            {selectedFeedback && (
                <DirectRemoveModal
                    feedback={selectedFeedback}
                    onClose={() => setSelectedFeedback(null)}
                    onConfirm={handleRemoveConfirm}
                />
            )}

            <ToastContainer toasts={toasts} />
        </>
    );
}

export default AdminFeedbackPage;
