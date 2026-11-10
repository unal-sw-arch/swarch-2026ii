INSERT INTO auth.permissions (id, code, description) VALUES
    ('20000000-0000-0000-0000-000000000018', 'orders:cancel_own', 'Cancelar pedidos propios que siguen pendientes')
ON CONFLICT (code) DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM auth.roles r
JOIN auth.permissions p ON p.code = 'orders:cancel_own'
WHERE r.name = 'cliente'
ON CONFLICT DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM auth.roles r
JOIN auth.permissions p ON p.code = 'orders:cancel_own'
WHERE r.name = 'admin'
ON CONFLICT DO NOTHING;
