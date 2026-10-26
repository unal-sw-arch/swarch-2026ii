-- =====================================================
-- FEEDBACK DE SERVICIO + MÉTRICAS PRECALCULADAS
-- =====================================================
-- Un feedback por solicitud (request_id es UNIQUE) y SOLO sobre solicitudes COMPLETED.
-- service_id y client_id coinciden con los de la solicitud referenciada.
-- Las métricas (service_metrics, offerer_metrics, client_metrics) se insertan
-- manualmente aquí para que el seed inicial tenga datos visibles sin depender de
-- los event listeners de Java (que solo se ejecutan en runtime).
--
-- Cálculo de service_metrics.average_rating (ponderado):
--   service 1 (plomería): ratings 4 + 3 → avg 3.50 | 2 ratings | 2 comments
--   service 2 (jardinería): rating 4        → avg 4.00 | 1 rating  | 1 comment
--   service 3 (limpieza): rating 5          → avg 5.00 | 1 rating  | 1 comment
--   service 4 (celulares): rating 5         → avg 5.00 | 1 rating  | 1 comment
--
-- Cálculo de offerer_metrics (offerer_id=1):
--   average_rating = (5+4+4+5+3)/5 = 4.20
--   total_completed_services = 5 (requests 7,8,9,10,11)
--   total_cancelled_services = 1 (request 4)
--   total_requests_received = 11
SET NAMES utf8mb4;

-- =====================================================
-- SERVICE FEEDBACK
-- =====================================================
insert into service_feedback (request_id, service_id, client_id, rating, comment) values
    (7,  3, 2,  5, 'Excelente servicio, muy recomendado.'),
    (8,  1, 11, 4, 'Buen trabajo, quedé satisfecho.'),
    (9,  2, 2,  4, 'Muy buen jardín, quedó hermoso.'),
    (10, 4, 12, 5, 'Rápido y eficiente, el celular quedó como nuevo.'),
    (11, 1, 5,  3, 'El trabajo estuvo bien pero demoró más de lo esperado.');

-- =====================================================
-- SERVICE METRICS (una fila por servicio)
-- =====================================================
insert into service_metrics (service_id, average_rating, total_ratings, total_comments, total_requests_received) values
    (1, 3.50, 2, 2, 3),   -- plomería
    (2, 4.00, 1, 1, 3),   -- jardinería
    (3, 5.00, 1, 1, 2),   -- limpieza
    (4, 5.00, 1, 1, 1);   -- celulares

-- =====================================================
-- OFFERER METRICS (oferente 1)
-- =====================================================
insert into offerer_metrics (
    offerer_id, average_rating, total_ratings, total_comments,
    total_positive_tags, total_negative_tags,
    total_requests_received, total_accepted_requests,
    total_completed_services, total_cancelled_services,
    total_reschedule_proposals_sent, total_not_provided_services
) values (
    1, 4.20, 5, 5,
    0, 0,
    11, 7,
    5, 1,
    0, 0
);

-- =====================================================
-- CLIENT METRICS (clientes con solicitudes)
-- =====================================================
insert into client_metrics (
    client_id, average_rating, total_ratings, total_comments,
    total_positive_tags, total_negative_tags,
    total_requests_sent, total_accepted_requests,
    total_completed_requests, total_cancelled_requests,
    total_rescheduled_requests, total_not_provided_requests
) values
    (2,  0.00, 0, 0, 0, 0, 3, 2, 2, 0, 0, 0),  -- Bob Smith
    (5,  0.00, 0, 0, 0, 0, 3, 2, 1, 0, 1, 0),  -- Emma Davis
    (11, 0.00, 0, 0, 0, 0, 2, 2, 1, 0, 0, 0),  -- Karen White
    (12, 0.00, 0, 0, 0, 0, 2, 1, 1, 0, 0, 0),  -- Leo Harris
    (13, 0.00, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0);  -- Mia Martin
