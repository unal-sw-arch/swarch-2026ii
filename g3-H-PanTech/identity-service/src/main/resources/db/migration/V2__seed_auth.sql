-- =============================================================================
-- Identity Service — datos semilla
-- Roles, permisos, matriz de autorización y usuarios de demostración.
--
-- Todos los usuarios demo usan la contraseña: Demo1234!
-- =============================================================================

INSERT INTO auth.permissions (id, code, description) VALUES
    ('20000000-0000-0000-0000-000000000001', 'orders:create_own',         'Crear un pedido propio para llevar'),
    ('20000000-0000-0000-0000-000000000002', 'orders:read_own',           'Ver los pedidos propios'),
    ('20000000-0000-0000-0000-000000000003', 'orders:create',             'Crear pedidos de mostrador o en mesa'),
    ('20000000-0000-0000-0000-000000000004', 'orders:charge',             'Cobrar o confirmar el pago de un pedido'),
    ('20000000-0000-0000-0000-000000000005', 'orders:read_all',           'Ver todos los pedidos'),
    ('20000000-0000-0000-0000-000000000006', 'reservations:manage_own',   'Crear, ver y cancelar reservas propias'),
    ('20000000-0000-0000-0000-000000000007', 'reservations:manage_all',   'Gestionar todas las reservas'),
    ('20000000-0000-0000-0000-000000000008', 'kitchen:read',              'Ver la cola de cocina'),
    ('20000000-0000-0000-0000-000000000009', 'kitchen:update',            'Cambiar estados de preparación'),
    ('20000000-0000-0000-0000-000000000010', 'kitchen:deliver',           'Confirmar la entrega de un pedido'),
    ('20000000-0000-0000-0000-000000000011', 'products:manage',           'Crear, editar y retirar productos'),
    ('20000000-0000-0000-0000-000000000012', 'history:read_own',          'Ver el historial propio'),
    ('20000000-0000-0000-0000-000000000013', 'history:read_all',          'Ver el historial general'),
    ('20000000-0000-0000-0000-000000000014', 'reports:read',              'Ver y exportar reportes'),
    ('20000000-0000-0000-0000-000000000015', 'users:manage',              'Gestionar cualquier cuenta'),
    ('20000000-0000-0000-0000-000000000016', 'users:manage_operational',  'Gestionar cuentas operativas'),
    ('20000000-0000-0000-0000-000000000017', 'roles:manage',              'Gestionar roles y permisos')
ON CONFLICT (code) DO NOTHING;

INSERT INTO auth.roles (id, name, description, level, is_staff) VALUES
    ('10000000-0000-0000-0000-000000000001', 'cliente',     'Cliente externo; solo accede a lo propio', 10,  false),
    ('10000000-0000-0000-0000-000000000002', 'mesero',      'Crea pedidos, cobra y gestiona reservas',  50,  true),
    ('10000000-0000-0000-0000-000000000003', 'cocinero',    'Consulta la cola y cambia estados',        50,  true),
    ('10000000-0000-0000-0000-000000000004', 'despachador', 'Confirma entregas de pedidos listos',      50,  true),
    ('10000000-0000-0000-0000-000000000005', 'supervisor',  'Supervisa la operación del turno',         80,  true),
    ('10000000-0000-0000-0000-000000000006', 'admin',       'Administra usuarios, roles y catálogo',    100, true)
ON CONFLICT (name) DO NOTHING;

-- cliente: solo lo propio
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM auth.roles r
JOIN auth.permissions p ON p.code IN (
    'orders:create_own', 'orders:read_own', 'reservations:manage_own', 'history:read_own'
)
WHERE r.name = 'cliente'
ON CONFLICT DO NOTHING;

-- mesero: pedidos de mostrador, cobro y reservas
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM auth.roles r
JOIN auth.permissions p ON p.code IN ('orders:create', 'orders:charge', 'reservations:manage_all')
WHERE r.name = 'mesero'
ON CONFLICT DO NOTHING;

-- cocinero: cola de cocina
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM auth.roles r
JOIN auth.permissions p ON p.code IN ('kitchen:read', 'kitchen:update')
WHERE r.name = 'cocinero'
ON CONFLICT DO NOTHING;

-- despachador: pedidos listos y entrega
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM auth.roles r
JOIN auth.permissions p ON p.code IN ('kitchen:read', 'kitchen:deliver')
WHERE r.name = 'despachador'
ON CONFLICT DO NOTHING;

-- supervisor: toda la operación, historial y reportes; cuentas operativas
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM auth.roles r
JOIN auth.permissions p ON p.code IN (
    'orders:create', 'orders:charge', 'orders:read_all', 'reservations:manage_all',
    'kitchen:read', 'kitchen:update', 'kitchen:deliver',
    'history:read_all', 'reports:read', 'users:manage_operational'
)
WHERE r.name = 'supervisor'
ON CONFLICT DO NOTHING;

-- admin: todos los permisos
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM auth.roles r
CROSS JOIN auth.permissions p
WHERE r.name = 'admin'
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- Usuarios demo (contraseña: Demo1234!)
-- -----------------------------------------------------------------------------
INSERT INTO auth.users (id, email, password_hash, full_name, active) VALUES
    ('30000000-0000-0000-0000-000000000001', 'cliente@demo.com',      '$2b$10$dWIDZaZ/HBGZ1ajF2SrIvewBSlNK6JiGc1GzE1pamNZTxeU2uxOjm', 'Cliente Demo',              true),
    ('30000000-0000-0000-0000-000000000002', 'mesero@demo.com',       '$2b$10$dWIDZaZ/HBGZ1ajF2SrIvewBSlNK6JiGc1GzE1pamNZTxeU2uxOjm', 'Mesero Demo',               true),
    ('30000000-0000-0000-0000-000000000003', 'cocinero@demo.com',     '$2b$10$dWIDZaZ/HBGZ1ajF2SrIvewBSlNK6JiGc1GzE1pamNZTxeU2uxOjm', 'Cocinero Demo',             true),
    ('30000000-0000-0000-0000-000000000004', 'despachador@demo.com',  '$2b$10$dWIDZaZ/HBGZ1ajF2SrIvewBSlNK6JiGc1GzE1pamNZTxeU2uxOjm', 'Despachador Demo',          true),
    ('30000000-0000-0000-0000-000000000005', 'supervisor@demo.com',   '$2b$10$dWIDZaZ/HBGZ1ajF2SrIvewBSlNK6JiGc1GzE1pamNZTxeU2uxOjm', 'Supervisor Demo',           true),
    ('30000000-0000-0000-0000-000000000006', 'admin@demo.com',        '$2b$10$dWIDZaZ/HBGZ1ajF2SrIvewBSlNK6JiGc1GzE1pamNZTxeU2uxOjm', 'Administrador Demo',        true),
    ('30000000-0000-0000-0000-000000000007', 'multi@demo.com',        '$2b$10$dWIDZaZ/HBGZ1ajF2SrIvewBSlNK6JiGc1GzE1pamNZTxeU2uxOjm', 'Mesero y Despachador Demo', true)
ON CONFLICT (email) DO NOTHING;

INSERT INTO auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM auth.users u
JOIN auth.roles r ON r.name = 'cliente'
WHERE u.email = 'cliente@demo.com'
ON CONFLICT DO NOTHING;

INSERT INTO auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM auth.users u
JOIN auth.roles r ON r.name = 'mesero'
WHERE u.email = 'mesero@demo.com'
ON CONFLICT DO NOTHING;

INSERT INTO auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM auth.users u
JOIN auth.roles r ON r.name = 'cocinero'
WHERE u.email = 'cocinero@demo.com'
ON CONFLICT DO NOTHING;

INSERT INTO auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM auth.users u
JOIN auth.roles r ON r.name = 'despachador'
WHERE u.email = 'despachador@demo.com'
ON CONFLICT DO NOTHING;

INSERT INTO auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM auth.users u
JOIN auth.roles r ON r.name = 'supervisor'
WHERE u.email = 'supervisor@demo.com'
ON CONFLICT DO NOTHING;

INSERT INTO auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM auth.users u
JOIN auth.roles r ON r.name = 'admin'
WHERE u.email = 'admin@demo.com'
ON CONFLICT DO NOTHING;

-- Usuario multi-rol: mesero + despachador
INSERT INTO auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM auth.users u
JOIN auth.roles r ON r.name IN ('mesero', 'despachador')
WHERE u.email = 'multi@demo.com'
ON CONFLICT DO NOTHING;
