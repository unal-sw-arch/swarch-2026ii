import { useState } from 'react';
import './DirectRemoveModal.css';

const CATEGORIES = [
    'Contenido inapropiado',
    'Lenguaje ofensivo',
    'Spam',
    'Información falsa',
    'Otro',
];

export function DirectRemoveModal({ feedback, onClose, onConfirm }) {
    const [category, setCategory] = useState(CATEGORIES[0]);
    const [reason, setReason] = useState('');
    const [loading, setLoading] = useState(false);

    if (!feedback) return null;

    const handleSubmit = async () => {
        setLoading(true);
        try {
            await onConfirm({
                targetType: feedback.feedbackType,
                targetId: feedback.feedbackId,
                reportedUserId: feedback.authorId,
                category,
                reason: reason || `Eliminación directa: ${category}`,
            });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal" style={{ maxWidth: '480px' }} onClick={(e) => e.stopPropagation()}>
                <div className="modal-title">Eliminar reseña (RF-049)</div>
                <div className="modal-sub">
                    Se creará un reporte de auditoría con esta eliminación.
                </div>

                <div style={{ background: 'var(--c-bg-s)', borderRadius: 'var(--r-lg)', padding: '10px 12px', fontSize: '12px', color: 'var(--c-mid)', marginBottom: '14px' }}>
                    <strong>Tipo:</strong> {feedback.feedbackType === 'SERVICE' ? 'Reseña de servicio' : 'Reseña de cliente'}<br />
                    <strong>Autor:</strong> Usuario #{feedback.authorId}<br />
                    <strong>Rating:</strong> {feedback.rating != null ? `${feedback.rating}/5` : 'Sin rating'}<br />
                    {feedback.comment && <><strong>Comentario:</strong> "{feedback.comment}"</>}
                </div>

                <div className="input-group">
                    <label className="label">Categoría</label>
                    <select
                        className="input"
                        value={category}
                        onChange={(e) => setCategory(e.target.value)}
                    >
                        {CATEGORIES.map((cat) => (
                            <option key={cat} value={cat}>{cat}</option>
                        ))}
                    </select>
                </div>

                <div className="input-group">
                    <label className="label">Motivo (opcional)</label>
                    <textarea
                        className="input"
                        rows="3"
                        placeholder="Describe el motivo de la eliminación..."
                        value={reason}
                        onChange={(e) => setReason(e.target.value)}
                    />
                </div>

                <div style={{ display: 'flex', gap: '7px' }}>
                    <button
                        className="btn btn-ghost btn-full"
                        style={{ border: '1px solid var(--c-border)' }}
                        onClick={onClose}
                        disabled={loading}
                    >
                        Cancelar
                    </button>
                    <button
                        className="btn btn-danger btn-full"
                        onClick={handleSubmit}
                        disabled={loading}
                    >
                        {loading ? 'Eliminando...' : 'Eliminar y crear reporte'}
                    </button>
                </div>
            </div>
        </div>
    );
}
