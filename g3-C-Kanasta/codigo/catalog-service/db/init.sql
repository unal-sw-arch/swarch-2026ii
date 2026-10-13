-- Catalog database: categories and products

CREATE TABLE categories (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    slug VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE products (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    price       INTEGER NOT NULL CHECK (price >= 0),
    stock       INTEGER NOT NULL CHECK (stock >= 0),
    image       VARCHAR(120) NOT NULL,
    category_id INTEGER NOT NULL REFERENCES categories(id)
);

INSERT INTO categories (name, slug) VALUES
    ('Tecnología', 'tecnologia'),
    ('Hogar', 'hogar'),
    ('Ropa', 'ropa'),
    ('Deportes', 'deportes'),
    ('Papelería', 'papeleria');

INSERT INTO products (name, description, price, stock, image, category_id) VALUES
    ('Portátil 15 pulgadas', 'Portátil con 8 GB de RAM y 512 GB de almacenamiento. Ideal para estudiar y trabajar.', 2199000, 8, 'laptop.jpg', 1),
    ('Celular Android 128 GB', 'Pantalla de 6,5 pulgadas, cámara de 50 MP y batería para todo el día.', 899000, 15, 'smartphone.jpg', 1),
    ('Audífonos con cancelación de ruido', 'Audífonos de diadema cómodos, con estuche y cable incluido.', 549000, 10, 'headphones.jpg', 1),
    ('Teclado mecánico RGB', 'Teclado mecánico con luces de colores y reposamuñecas.', 289000, 12, 'keyboard.jpg', 1),
    ('Mouse inalámbrico', 'Mouse ergonómico con receptor USB y batería de larga duración.', 69000, 30, 'mouse.jpg', 1),
    ('Disco duro 1 TB', 'Disco duro de 2,5 pulgadas para guardar fotos, videos y documentos.', 219000, 20, 'external-drive.jpg', 1),
    ('Cafetera de goteo', 'Prepara hasta 10 tazas de café. Jarra de vidrio incluida.', 159000, 10, 'coffee-maker.jpg', 2),
    ('Olla de acero inoxidable', 'Olla de 5 litros con tapa, apta para todo tipo de estufa.', 129000, 14, 'cooking-pot.jpg', 2),
    ('Taza de cerámica', 'Taza roja de cerámica de 350 ml, perfecta para el café de la mañana.', 25000, 40, 'mug.jpg', 2),
    ('Lámpara de escritorio', 'Lámpara LED con brazo flexible y tres niveles de luz.', 99000, 9, 'desk-lamp.jpg', 2),
    ('Camiseta de algodón', 'Camiseta blanca 100% algodón con estampado.', 45000, 50, 't-shirt.jpg', 3),
    ('Chaqueta de jean', 'Chaqueta clásica de jean azul claro.', 179000, 9, 'jacket.jpg', 3),
    ('Tenis para correr', 'Tenis livianos con buena amortiguación para entrenar todos los días.', 329000, 11, 'running-shoes.jpg', 3),
    ('Gorra deportiva', 'Gorra ajustable de algodón.', 59000, 25, 'cap.jpg', 3),
    ('Balón de fútbol', 'Balón número 5 cosido a máquina, para cancha sintética o natural.', 89000, 18, 'football.jpg', 4),
    ('Bicicleta de montaña', 'Marco de aluminio, rin 29 y frenos de disco hidráulicos.', 2890000, 3, 'bicycle.jpg', 4),
    ('Juego de mancuernas', 'Mancuernas de colores recubiertas en vinilo, de 1 a 5 kg.', 199000, 7, 'dumbbells.jpg', 4),
    ('Tapete de yoga', 'Tapete antideslizante de algodón, fácil de enrollar.', 79000, 0, 'yoga-mat.jpg', 4),
    ('Cuaderno argollado', 'Cuaderno de 100 hojas cuadriculadas con argolla metálica.', 12000, 100, 'notebook.jpg', 5),
    ('Colores x 12', 'Caja de 12 lápices de colores con portalápices.', 18000, 60, 'colored-pencils.jpg', 5),
    ('Morral de viaje 50 L', 'Morral resistente con varios bolsillos y espaldar acolchado.', 249000, 6, 'backpack.jpg', 5);
