import { StatCard } from '../../../../shared';

function pct(num, denom) {
    if (!denom || denom === 0) return '0%';
    return ((num / denom) * 100).toFixed(0) + '%';
}

export function MetricsCards({ offererMetrics, clientMetrics }) {
    const cards = [];

    if (clientMetrics) {
        const { totalRequestsSent, totalAcceptedRequests, totalCompletedRequests,
                totalCancelledRequests, totalRescheduledRequests, totalNotProvidedRequests,
                averageRating, totalPositiveTags, totalNegativeTags, totalComments } = clientMetrics;

        cards.push(
            { icon: 'send', value: String(totalRequestsSent ?? 0), label: 'Solicitudes enviadas' },
            { icon: 'checkCircle', value: String(totalAcceptedRequests ?? 0), label: 'Solicitudes aceptadas', variant: 'success' },
            { icon: 'check', value: String(totalCompletedRequests ?? 0), label: 'Completados', variant: 'success' },
            { icon: 'xCircle', value: pct(totalCancelledRequests, totalRequestsSent), label: 'Cancelaciones', variant: 'danger' },
            { icon: 'reschedule', value: pct(totalRescheduledRequests, totalRequestsSent), label: 'Reprogramaciones', variant: 'warn' },
            { icon: 'close', value: pct(totalNotProvidedRequests, totalRequestsSent), label: 'No proporcionados', variant: 'danger' },
            { icon: 'star', value: (averageRating ?? 0).toFixed(1) + '★', label: 'Calificación', variant: 'warn', fill: 'currentColor' },
            { icon: 'messageSquare', value: String(totalComments ?? 0), label: 'Reseñas recibidas' },
        );

        if ((totalPositiveTags ?? 0) > 0 || (totalNegativeTags ?? 0) > 0) {
            cards.push(
                { icon: 'thumbUp', value: String(totalPositiveTags), label: 'Tags positivos', variant: 'success' },
                { icon: 'thumbDown', value: String(totalNegativeTags), label: 'Tags negativos', variant: 'danger' },
            );
        }
    }

    if (offererMetrics) {
        const { totalRequestsReceived, totalAcceptedRequests, totalCompletedServices,
                totalCancelledServices, totalNotProvidedServices, totalRescheduleProposalsSent,
                averageRating, totalPositiveTags, totalNegativeTags, totalComments } = offererMetrics;

        cards.push(
            { icon: 'inbox', value: String(totalRequestsReceived ?? 0), label: 'Solicitudes recibidas' },
            { icon: 'checkCircle', value: String(totalAcceptedRequests ?? 0), label: 'Aceptadas', variant: 'success' },
            { icon: 'check', value: String(totalCompletedServices ?? 0), label: 'Completados', variant: 'success' },
            { icon: 'xCircle', value: pct(totalCancelledServices, totalRequestsReceived), label: 'Cancelaciones', variant: 'danger' },
            { icon: 'close', value: pct(totalNotProvidedServices, totalRequestsReceived), label: 'No proporcionados', variant: 'danger' },
            { icon: 'reschedule', value: String(totalRescheduleProposalsSent ?? 0), label: 'Propuestas enviadas' },
            { icon: 'star', value: (averageRating ?? 0).toFixed(1) + '★', label: 'Calificación', variant: 'warn', fill: 'currentColor' },
            { icon: 'messageSquare', value: String(totalComments ?? 0), label: 'Reseñas recibidas' },
        );

        if ((totalPositiveTags ?? 0) > 0 || (totalNegativeTags ?? 0) > 0) {
            cards.push(
                { icon: 'thumbUp', value: String(totalPositiveTags), label: 'Tags positivos', variant: 'success' },
                { icon: 'thumbDown', value: String(totalNegativeTags), label: 'Tags negativos', variant: 'danger' },
            );
        }
    }

    if (cards.length === 0) return null;

    return (
        <div className="metrics-grid g4" style={{ marginBottom: '22px' }}>
            {cards.map(s => <StatCard key={s.label} {...s} />)}
        </div>
    );
}
