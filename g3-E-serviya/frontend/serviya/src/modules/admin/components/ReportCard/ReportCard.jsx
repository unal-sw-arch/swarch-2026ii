import './ReportCard.css';

export function ReportCard({ report, onManage, onDetail, onDelete }) {
    if (report.status === 'Resuelto') {
        return (
            <div className="card" style={{ opacity: .8, marginBottom: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                        <span className="badge badge-primary">{report.type}</span>
                        <span className="badge badge-gray">{report.priority}</span>
                        <span style={{ fontSize: '12px', color: 'var(--c-soft)' }}>{report.id} · {report.date}</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span className="badge badge-success">Resuelto</span>
                        <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={onDetail}>Ver detalle</button>
                    </div>
                </div>
                {/* Mantiene reportante → reportado y categoría para poder ubicarlo sin buscador. */}
                <div style={{ marginTop: '10px', display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center', fontSize: '12px', color: 'var(--c-mid)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <div className="av av-xs">{report.reporterInitials}</div>
                        <span style={{ fontWeight: 600 }}>{report.reporterName}</span>
                        <span style={{ color: 'var(--c-soft)' }}>→</span>
                        <div className="av av-xs">{report.reportedInitials}</div>
                        <span style={{ fontWeight: 600 }}>{report.reportedName}</span>
                    </div>
                    <span className="badge badge-gray">{report.category}</span>
                </div>
            </div>
        );
    }

    const borderLeftColor = report.priority === 'Alta' ? 'var(--c-danger)' : 'var(--c-warn)';
    const badgePriorityClass = report.priority === 'Alta' ? 'badge-danger' : 'badge-warn';
    const badgeTypeClass = report.type === 'Solicitud' ? 'badge-danger' : 'badge-warn';

    return (
        <div className="card" style={{ marginBottom: '12px', borderLeft: `3px solid ${borderLeftColor}` }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px', marginBottom: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span className={`badge ${badgeTypeClass}`}>{report.type}</span>
                    <span className={`badge ${badgePriorityClass}`}>{report.priority}</span>
                    <span style={{ fontSize: '12px', color: 'var(--c-soft)' }}>{report.id} · {report.date}</span>
                </div>
                <span className="badge badge-warn">Pendiente</span>
            </div>

            <div style={{ display: 'flex', gap: '14px', marginBottom: '12px', flexWrap: 'wrap' }}>
                <div>
                    <div style={{ fontSize: '11px', color: 'var(--c-soft)', textTransform: 'uppercase', marginBottom: '4px' }}>Reportante</div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '7px' }}>
                        <div className="av av-xs">{report.reporterInitials}</div>
                        <span style={{ fontSize: '13px', fontWeight: 600 }}>{report.reporterName}</span>
                    </div>
                </div>
                <div>
                    <div style={{ fontSize: '11px', color: 'var(--c-soft)', textTransform: 'uppercase', marginBottom: '4px' }}>
                        {report.type === 'Solicitud' ? 'Reportado' : 'Reseña de'}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '7px' }}>
                        <div className="av av-xs">{report.reportedInitials}</div>
                        <span style={{ fontSize: '13px', fontWeight: 600 }}>{report.reportedName}</span>
                    </div>
                </div>
            </div>

            <div style={{ background: 'var(--c-bg-s)', borderRadius: 'var(--r-lg)', padding: '10px 12px', fontSize: '12px', color: 'var(--c-mid)', marginBottom: '12px' }}>
                <strong>Motivo:</strong> {report.reason}<br />
                <strong>{report.type === 'Solicitud' ? 'Descripción:' : 'Reseña reportada:'}</strong> {report.description}
            </div>

            <div style={{ display: 'flex', gap: '7px', flexWrap: 'wrap' }}>
                <button className="btn btn-primary btn-sm" onClick={() => onManage(report)}>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: '14px', marginRight: '2px' }}><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /></svg>
                    {report.type === 'Solicitud' ? 'Gestionar y cerrar' : 'Gestionar'}
                </button>
                <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={onDetail}>
                    Ver detalle
                </button>
                {report.type !== 'Solicitud' && (
                    <button className="btn btn-danger btn-sm" onClick={onDelete}>
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ width: '14px', marginRight: '2px' }}><polyline points="3 6 5 6 21 6" /><path d="M19 6l-1 14H6L5 6" /></svg>
                        Eliminar reseña
                    </button>
                )}
            </div>
        </div>
    );
}
