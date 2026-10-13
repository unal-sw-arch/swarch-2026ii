-- =====================================================
-- DIRECCIONES de clientes (requiere users)
-- =====================================================
-- `address_line` es VARBINARY (AES-256-GCM en runtime via PiiAttributeConverter).
-- El converter tiene fallback: si el valor almacenado no es ciphertext válido, lo
-- devuelve tal cual como texto UTF-8 (datos semilla/legacy). Por eso aquí se siembra
-- en claro y la aplicación lo lee sin romperse. NO requiere FOREIGN_KEY_CHECKS=0:
-- los user_id existen (02_populate_users) y las direcciones se crean ANTES que las
-- service_requests que las referencian (08).
--
-- ids de dirección resultantes (AUTO_INCREMENT):
--   1 -> user 2    2 -> user 11   3 -> user 12
--   4 -> user 13   5 -> user 5    6 -> user 2 (segunda dirección)
--   7 -> user 1    8 -> user 4    9 -> user 10
SET NAMES utf8mb4;

INSERT INTO addresses (user_id, address_line, city, latitude, longitude) VALUES
-- Clientes existentes
(2,  'Calle 10 # 5-23, Apto 301', 'Bogotá',   4.6097100, -74.0817500),
(11, 'Carrera 45 # 12-34',        'Medellín', 6.2476400, -75.5658100),
(12, 'Avenida 6N # 23-61',        'Cali',     3.4516500, -76.5319900),
(13, 'Calle 72 # 10-07, Of. 502', 'Bogotá',   4.6584700, -74.0548200),
(5,  'Carrera 7 # 80-44',         'Bogotá',   4.6664300, -74.0531500),
(2,  'Diagonal 25 # 40-15',       'Bogotá',   4.6280000, -74.0660000),
-- Oferentes en distintas zonas de Bogotá (para filtro de cercanía)
(1,  'Carrera 11 # 82-71, Chapinero',  'Bogotá', 4.6628800, -74.0523000),
(4,  'Carrera 7 # 138-45, Usaquén',     'Bogotá', 4.7215300, -74.0418600),
(10, 'Avenida Boyacá # 80-22, Suba',     'Bogotá', 4.7573700, -74.0873000);

-- Vincular direcciones de oferentes a sus perfiles
UPDATE user_profiles SET primary_address_id = 7 WHERE user_id = 1;
UPDATE user_profiles SET primary_address_id = 1 WHERE user_id = 2;
UPDATE user_profiles SET primary_address_id = 8 WHERE user_id = 4;
UPDATE user_profiles SET primary_address_id = 9 WHERE user_id = 10;
