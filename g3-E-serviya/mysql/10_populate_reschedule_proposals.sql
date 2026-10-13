-- =====================================================
-- PROPUESTAS DE REPROGRAMACION (requiere service_requests + users)
-- =====================================================
-- Datos de prueba para ejercitar los reads de propuestas (recibidas/enviadas/detalle/
-- por-solicitud). client_id y offerer_id estan DENORMALIZADOS: deben coincidir con los de
-- la solicitud referenciada (ver 08_populate_service_requests). offerer_id siempre 1.
--
-- Solicitudes disponibles (id: client_id, status):
--   1: cli 2  PENDING   | 2: cli 11 ACCEPTED | 3: cli 12 REJECTED | 4: cli 13 CANCELLED
--   5: cli 5  RESCHEDULED| 6: cli 5  ACCEPTED | 7: cli 2 COMPLETED | 8: cli 11 COMPLETED
--
-- Se cubren los 5 estados de ProposalStatus. Las PENDING van sobre solicitudes ACCEPTED
-- (invariante del dominio); las resueltas se apoyan en solicitudes coherentes con su historia
-- (p.ej. SUPERSEDED sobre la solicitud 5 que quedo RESCHEDULED por reprogramacion libre).
-- La coherencia estricta de la maquina de estados NO es el objetivo de este seed; basta con
-- FKs validas y variedad de estados/fechas/partes para probar los listados y filtros.

INSERT INTO reschedule_proposals (
    request_id,
    client_id,
    offerer_id,
    reason,
    proposed_date,
    status,
    created_at,
    responded_at
) VALUES
-- 1. PENDING sobre la solicitud 2 (cli 11, ACCEPTED): el oferente propone mover la cita.
(2, 11, 1, 'El oferente propone mover la cita por cruce de agenda', '2026-07-22 10:00:00', 'PENDING',    '2026-06-14 09:00:00', NULL),

-- 2. PENDING sobre la solicitud 6 (cli 5, ACCEPTED): propuesta de nueva hora.
(6, 5,  1, 'Propuesta de nueva hora para el servicio agendado',    '2026-07-21 09:00:00', 'PENDING',    '2026-06-16 12:00:00', NULL),

-- 3. SUPERSEDED sobre la solicitud 5 (cli 5, RESCHEDULED): el cliente reprogramo libremente
--    y la propuesta del oferente quedo superada.
(5, 5,  1, 'El cliente reprogramo libremente; la propuesta quedo superada', '2026-07-12 13:00:00', 'SUPERSEDED', '2026-06-14 08:30:00', '2026-06-15 10:00:00'),

-- 4. REJECTED sobre la solicitud 2 (cli 11): una primera propuesta que el cliente rechazo
--    (la solicitud siguio ACCEPTED con su fecha original).
(2, 11, 1, 'Primera propuesta rechazada por el cliente',           '2026-07-25 15:00:00', 'REJECTED',   '2026-06-13 12:00:00', '2026-06-13 18:00:00'),

-- 5. CANCELLED sobre la solicitud 3 (cli 12): el oferente retiro su propuesta.
(3, 12, 1, 'Propuesta cancelada por el propio oferente',           '2026-08-05 16:00:00', 'CANCELLED',  '2026-06-13 08:00:00', '2026-06-13 09:00:00'),

-- 6. ACCEPTED sobre la solicitud 8 (cli 11): el cliente acepto una propuesta previa.
(8, 11, 1, 'Propuesta aceptada por el cliente',                     '2026-06-11 08:00:00', 'ACCEPTED',   '2026-06-07 10:00:00', '2026-06-08 09:00:00');
