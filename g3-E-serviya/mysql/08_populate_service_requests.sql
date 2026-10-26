-- =====================================================
-- SOLICITUDES DE SERVICIO (requiere services + users + addresses)
-- =====================================================
-- Consolida los dos seeds antiguos (03_populate_requests + 07_populate_service_requests)
-- en uno solo, COHERENTE y con TODAS las FKs reales (ya NO hace falta
-- FOREIGN_KEY_CHECKS=0):
--   * offerer_id siempre 1 (los 3 servicios sembrados pertenecen al oferente 1).
--   * service_id en {1,2,3} (los únicos servicios que existen).
--   * client_id distinto del oferente, con una address_id que pertenece a ese cliente
--     (ver 05_populate_addresses): cli 2->addr 1/6, cli 11->addr 2, cli 12->addr 3,
--     cli 13->addr 4, cli 5->addr 5.
--   * status en mayúscula (ENUM real de service_requests).
--
-- ids de solicitud resultantes (AUTO_INCREMENT, 1..11); la fila 6 referencia
-- previous_request_id=5 (reprogramación), que ya existe al insertarse en orden.
-- Las filas 9-11 son COMPLETED adicionales para poblar feedback y métricas.

INSERT INTO service_requests (
    service_id,
    previous_request_id,
    client_id,
    offerer_id,
    address_id,
    scheduled_date,
    status,
    updated_by,
    requested_price,
    created_at,
    completed_at,
    updated_status_at
) VALUES
-- 1. Cliente 2 crea una solicitud futura pendiente
(1, NULL, 2,  1, 1, '2026-07-15 10:00:00', 'PENDING',      NULL, 150.00, '2026-06-12 09:00:00', NULL, '2026-06-12 09:00:00'),

-- 2. Cliente 11 solicita y el oferente la acepta
(2, NULL, 11, 1, 2, '2026-07-20 14:30:00', 'ACCEPTED',     1,    85.50,  '2026-06-12 08:15:00', NULL, '2026-06-13 11:00:00'),

-- 3. Cliente 12 solicita; el oferente la rechaza
(3, NULL, 12, 1, 3, '2026-08-01 16:00:00', 'REJECTED',     1,    200.00, '2026-06-12 17:45:00', NULL, '2026-06-13 09:30:00'),

-- 4. Cliente 13 solicita y luego cancela
(1, NULL, 13, 1, 4, '2026-08-15 11:00:00', 'CANCELLED',    13,   175.00, '2026-06-12 10:00:00', NULL, '2026-06-14 14:20:00'),

-- 5. Cliente 5 crea una solicitud que se reprograma
(2, NULL, 5,  1, 5, '2026-07-05 13:00:00', 'RESCHEDULED',  1,    120.00, '2026-06-12 08:00:00', NULL, '2026-06-15 10:00:00'),

-- 6. Solicitud de seguimiento de la anterior (previous_request_id = 5)
(2, 5,    5,  1, 5, '2026-07-19 13:00:00', 'ACCEPTED',     1,    120.00, '2026-06-15 10:00:00', NULL, '2026-06-15 10:30:00'),

-- 7. Cliente 2: servicio completado (habilita feedback en 09)
(3, NULL, 2,  1, 6, '2026-06-10 09:00:00', 'COMPLETED',    1,    130.00, '2026-06-05 13:00:00', '2026-06-10 11:00:00', '2026-06-10 11:00:00'),

-- 8. Cliente 11: servicio completado (habilita feedback en 09)
(1, NULL, 11, 1, 2, '2026-06-12 08:00:00', 'COMPLETED',    1,    160.00, '2026-06-06 12:00:00', '2026-06-12 10:30:00', '2026-06-12 10:30:00'),

-- 9. Cliente 2: jardinería completada
(2, NULL, 2,  1, 1, '2026-06-15 14:00:00', 'COMPLETED',    1,    90.00,  '2026-06-10 10:00:00', '2026-06-15 16:30:00', '2026-06-15 16:30:00'),

-- 10. Cliente 12: reparación celulares completada
(4, NULL, 12, 1, 3, '2026-06-18 11:00:00', 'COMPLETED',    1,    140.00, '2026-06-14 09:00:00', '2026-06-18 12:15:00', '2026-06-18 12:15:00'),

-- 11. Cliente 5: plomería completada
(1, NULL, 5,  1, 5, '2026-06-20 09:00:00', 'COMPLETED',    1,    110.00, '2026-06-16 15:00:00', '2026-06-20 10:30:00', '2026-06-20 10:30:00');
