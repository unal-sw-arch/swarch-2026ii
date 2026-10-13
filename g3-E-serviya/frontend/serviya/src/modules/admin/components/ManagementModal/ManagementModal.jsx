import { useState } from 'react';
import './ManagementModal.css';

const ACTIONS = [
    { value: 'warn', label: 'Advertencia (RF-060/071)', description: 'Envía una advertencia formal al usuario reportado.' },
    { value: 'ban', label: 'Baneo (RF-063/069)', description: 'Prohíbe permanentemente al usuario reportado.' },
    { value: 'close', label: 'Sin consecuencias (RF-059)', description: 'Cierra el reporte sin penalizar al usuario.' },
];

const REQUEST_ACTIONS = [
    ...ACTIONS,
    { value: 'mark_not_provided', label: 'No prestado (RF-074)', description: 'Marca la solicitud como no prestada.' },
];

const FEEDBACK_ACTIONS = [
    { value: 'revert', label: 'Revertir reseña (RF-049)', description: 'Elimina la reseña reportada y cierra el reporte.' },
    ...ACTIONS,
];

export function ManagementModal({ report, onClose, onExecute }) {
    const [action, setAction] = useState('');
    const [loading, setLoading] = useState(false);

    if (!report) return null;

    const isRequest = report.type === 'Solicitud';
    const availableActions = isRequest ? REQUEST_ACTIONS : FEEDBACK_ACTIONS;

    const handleExecute = async () => {
        if (!action) return;
        setLoading(true);
        try {
            await onExecute(action);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal" style={{ maxWidth: '520px' }} onClick={(e) => e.stopPropagation()}>
                <div className="modal-title">Gestionar reporte {report.id}</div>
                <div className="modal-sub">Reporte contra {report.reportedName} — {report.reason}</div>

                <div className="input-group">
                    <label className="label">Acción a tomar</label>
                    <div className="action-options">
                        {availableActions.map((opt) => (
                            <label
                                key={opt.value}
                                className={`action-option ${action === opt.value ? 'selected' : ''}`}
                            >
                                <input
                                    type="radio"
                                    name="moderation-action"
                                    value={opt.value}
                                    checked={action === opt.value}
                                    onChange={(e) => setAction(e.target.value)}
                                    style={{ accentColor: 'var(--c-primary)' }}
                                />
                                <div>
                                    <div style={{ fontWeight: 600, fontSize: '13px' }}>{opt.label}</div>
                                    <div style={{ fontSize: '11px', color: 'var(--c-mid)' }}>{opt.description}</div>
                                </div>
                            </label>
                        ))}
                    </div>
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
                        className="btn btn-primary btn-full"
                        onClick={handleExecute}
                        disabled={loading || !action}
                    >
                        {loading ? 'Ejecutando...' : 'Ejecutar y cerrar'}
                    </button>
                </div>
            </div>
        </div>
    );
}