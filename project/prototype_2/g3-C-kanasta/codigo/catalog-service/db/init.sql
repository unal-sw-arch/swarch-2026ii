-- Catalog database: store settings, categories, products and reviews

CREATE TABLE store (
    id            INTEGER PRIMARY KEY CHECK (id = 1),
    name          VARCHAR(80) NOT NULL,
    slogan        VARCHAR(120) NOT NULL DEFAULT '',
    address       VARCHAR(160) NOT NULL DEFAULT '',
    phone         VARCHAR(40) NOT NULL DEFAULT '',
    email         VARCHAR(120) NOT NULL DEFAULT '',
    delivery_area VARCHAR(200) NOT NULL DEFAULT '',
    opening_hours VARCHAR(120) NOT NULL DEFAULT ''
);

CREATE TABLE categories (
    id       SERIAL PRIMARY KEY,
    name     VARCHAR(60) NOT NULL,
    slug     VARCHAR(60) NOT NULL UNIQUE,
    position INTEGER NOT NULL DEFAULT 50
);

-- unit: how the product is sold. price is per unit, per kilogram or per liter.
CREATE TABLE products (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    price       INTEGER NOT NULL CHECK (price >= 0),
    unit        VARCHAR(10) NOT NULL DEFAULT 'unidad' CHECK (unit IN ('unidad', 'kg', 'litro')),
    stock       NUMERIC(10, 2) NOT NULL CHECK (stock >= 0),
    image       VARCHAR(120) NOT NULL DEFAULT '',
    category_id INTEGER NOT NULL REFERENCES categories(id)
);

CREATE TABLE reviews (
    id         SERIAL PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id    INTEGER NOT NULL,
    user_name  VARCHAR(120) NOT NULL,
    rating     INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, user_id)
);

INSERT INTO store (id, name, slogan, address, phone, email, delivery_area, opening_hours) VALUES
    (1, 'Mercado El Vecino', 'Frutas, verduras y mercado a la puerta de tu casa',
     'Calle 45 # 13-20, Bogotá', '601 555 0123', 'contacto@mercadoelvecino.com',
     'Barrios Chapinero, Teusaquillo y Palermo (máximo 3 km de la tienda)',
     'Lunes a sábado de 7:00 a. m. a 8:00 p. m.');

INSERT INTO categories (id, name, slug, position) VALUES
    (1, 'Frutas', 'frutas', 1),
    (2, 'Verduras', 'verduras', 2),
    (3, 'Carnes y pescado', 'carnes', 3),
    (4, 'Lácteos y huevos', 'lacteos', 4),
    (5, 'Despensa', 'despensa', 5),
    (6, 'Bebidas', 'bebidas', 6),
    (7, 'Aseo', 'aseo', 7),
    (8, 'Hogar', 'hogar', 8),
    (9, 'Papelería', 'papeleria', 9),
    (10, 'Tecnología', 'tecnologia', 10),
    (11, 'Ropa', 'ropa', 11),
    (12, 'Deportes', 'deportes', 12);
SELECT setval('categories_id_seq', 12);

INSERT INTO products (name, description, price, unit, stock, image, category_id) VALUES
    -- Frutas
    ('Banano', 'Banano maduro, ideal para el desayuno y los batidos.', 3200, 'kg', 40, 'banana.jpg', 1),
    ('Manzana roja', 'Manzana roja crocante y dulce.', 8900, 'kg', 25, 'apple.jpg', 1),
    ('Naranja', 'Naranja jugosa, perfecta para jugo.', 3500, 'kg', 50, 'orange.jpg', 1),
    ('Mango', 'Mango maduro de temporada.', 6500, 'kg', 20, 'mango.jpg', 1),
    ('Fresas', 'Fresas frescas y rojas.', 12000, 'kg', 10, 'strawberries.jpg', 1),
    ('Limón', 'Limón jugoso para bebidas y comidas.', 4200, 'kg', 20, 'lemon.jpg', 1),
    ('Uvas', 'Uvas dulces sin semilla.', 11000, 'kg', 12, 'grapes.jpg', 1),
    ('Aguacate', 'Aguacate grande, listo para comer en 2 o 3 días.', 3500, 'unidad', 30, 'avocado.jpg', 1),
    ('Piña', 'Piña dulce y aromática.', 6000, 'unidad', 15, 'pineapple.jpg', 1),
    -- Verduras
    ('Tomate', 'Tomate rojo para ensaladas y guisos.', 4800, 'kg', 30, 'tomato.jpg', 2),
    ('Papa pastusa', 'Papa pastusa lavada, buena para sopas y puré.', 2600, 'kg', 80, 'potato.jpg', 2),
    ('Cebolla cabezona', 'Cebolla cabezona blanca.', 3400, 'kg', 40, 'onion.jpg', 2),
    ('Zanahoria', 'Zanahoria fresca y crocante.', 2900, 'kg', 35, 'carrot.jpg', 2),
    ('Pimentón', 'Pimentón rojo y verde.', 7600, 'kg', 15, 'bell-pepper.jpg', 2),
    ('Plátano verde', 'Plátano verde para patacones.', 3000, 'kg', 40, 'plantain.jpg', 2),
    ('Lechuga', 'Lechuga crespa fresca.', 2500, 'unidad', 20, 'lettuce.jpg', 2),
    ('Brócoli', 'Brócoli fresco, una cabeza por unidad.', 4500, 'unidad', 12, 'broccoli.jpg', 2),
    ('Ajo', 'Cabeza de ajo.', 1200, 'unidad', 50, 'garlic.jpg', 2),
    -- Carnes y pescado
    ('Carne de res molida', 'Carne de res molida, fresca del día.', 26000, 'kg', 15, 'ground-beef.jpg', 3),
    ('Pechuga de pollo', 'Pechuga de pollo sin piel.', 18500, 'kg', 20, 'chicken-breast.jpg', 3),
    ('Costilla de cerdo', 'Costilla de cerdo para asar o sudar.', 21000, 'kg', 10, 'pork-ribs.jpg', 3),
    ('Filete de pescado', 'Filete de pescado blanco, sin espinas.', 24000, 'kg', 8, 'fish-fillet.jpg', 3),
    -- Lácteos y huevos
    ('Leche fresca a granel', 'Leche fresca de finca, se entrega en botella.', 3800, 'litro', 40, 'milk.jpg', 4),
    ('Huevos x 30', 'Bandeja de 30 huevos rojos AA.', 18000, 'unidad', 25, 'eggs.jpg', 4),
    ('Queso campesino', 'Queso campesino fresco.', 22000, 'kg', 10, 'cheese.jpg', 4),
    ('Mantequilla 250 g', 'Mantequilla con sal.', 7800, 'unidad', 20, 'butter.jpg', 4),
    -- Despensa
    ('Arroz a granel', 'Arroz blanco de grano largo.', 4200, 'kg', 100, 'rice.jpg', 5),
    ('Fríjol rojo', 'Fríjol rojo seleccionado.', 9800, 'kg', 30, 'beans.jpg', 5),
    ('Lenteja', 'Lenteja limpia y seleccionada.', 6500, 'kg', 30, 'lentils.jpg', 5),
    ('Azúcar', 'Azúcar blanca refinada.', 4300, 'kg', 50, 'sugar.jpg', 5),
    ('Aceite de girasol a granel', 'Aceite vegetal de girasol para cocinar.', 9500, 'litro', 30, 'cooking-oil.jpg', 5),
    ('Panela', 'Panela redonda de 500 g.', 3000, 'unidad', 40, 'panela.jpg', 5),
    ('Café molido 500 g', 'Café colombiano tostado y molido.', 16500, 'unidad', 25, 'ground-coffee.jpg', 5),
    ('Pasta espagueti 500 g', 'Pasta de trigo tipo espagueti.', 3800, 'unidad', 40, 'spaghetti.jpg', 5),
    ('Pan tajado', 'Pan blanco tajado, horneado hoy.', 6500, 'unidad', 15, 'bread.jpg', 5),
    -- Bebidas
    ('Jugo de naranja natural', 'Jugo de naranja recién exprimido, sin azúcar.', 9000, 'litro', 12, 'orange-juice.jpg', 6),
    ('Agua en botella 600 ml', 'Agua potable sin gas.', 1800, 'unidad', 60, 'water.jpg', 6),
    -- Aseo
    ('Jabón de baño', 'Barra de jabón de 125 g.', 3200, 'unidad', 40, 'soap.jpg', 7),
    ('Detergente en polvo 1 kg', 'Detergente para ropa blanca y de color.', 12500, 'unidad', 20, 'detergent.jpg', 7),
    ('Papel higiénico x 4', 'Paquete de 4 rollos doble hoja.', 9800, 'unidad', 30, 'toilet-paper.jpg', 7),
    -- Hogar
    ('Cafetera de goteo', 'Prepara hasta 10 tazas de café. Jarra de vidrio incluida.', 159000, 'unidad', 10, 'coffee-maker.jpg', 8),
    ('Olla de acero inoxidable', 'Olla de 5 litros con tapa, apta para todo tipo de estufa.', 129000, 'unidad', 14, 'cooking-pot.jpg', 8),
    ('Taza de cerámica', 'Taza roja de cerámica de 350 ml, perfecta para el café de la mañana.', 25000, 'unidad', 40, 'mug.jpg', 8),
    ('Lámpara de escritorio', 'Lámpara LED con brazo flexible y tres niveles de luz.', 99000, 'unidad', 9, 'desk-lamp.jpg', 8),
    -- Papelería
    ('Cuaderno argollado', 'Cuaderno de 100 hojas cuadriculadas con argolla metálica.', 12000, 'unidad', 100, 'notebook.jpg', 9),
    ('Colores x 12', 'Caja de 12 lápices de colores con portalápices.', 18000, 'unidad', 60, 'colored-pencils.jpg', 9),
    ('Morral de viaje 50 L', 'Morral resistente con varios bolsillos y espaldar acolchado.', 249000, 'unidad', 6, 'backpack.jpg', 9),
    -- Tecnología
    ('Portátil 15 pulgadas', 'Portátil con 8 GB de RAM y 512 GB de almacenamiento.', 2199000, 'unidad', 8, 'laptop.jpg', 10),
    ('Celular Android 128 GB', 'Pantalla de 6,5 pulgadas, cámara de 50 MP y batería para todo el día.', 899000, 'unidad', 15, 'smartphone.jpg', 10),
    ('Audífonos con cancelación de ruido', 'Audífonos de diadema cómodos, con estuche y cable incluido.', 549000, 'unidad', 10, 'headphones.jpg', 10),
    ('Teclado mecánico RGB', 'Teclado mecánico con luces de colores y reposamuñecas.', 289000, 'unidad', 12, 'keyboard.jpg', 10),
    ('Mouse inalámbrico', 'Mouse ergonómico con receptor USB y batería de larga duración.', 69000, 'unidad', 30, 'mouse.jpg', 10),
    ('Disco duro 1 TB', 'Disco duro de 2,5 pulgadas para guardar fotos, videos y documentos.', 219000, 'unidad', 20, 'external-drive.jpg', 10),
    -- Ropa
    ('Camiseta de algodón', 'Camiseta blanca 100% algodón con estampado.', 45000, 'unidad', 50, 't-shirt.jpg', 11),
    ('Chaqueta de jean', 'Chaqueta clásica de jean azul claro.', 179000, 'unidad', 9, 'jacket.jpg', 11),
    ('Tenis para correr', 'Tenis livianos con buena amortiguación para entrenar todos los días.', 329000, 'unidad', 11, 'running-shoes.jpg', 11),
    ('Gorra deportiva', 'Gorra ajustable de algodón.', 59000, 'unidad', 25, 'cap.jpg', 11),
    -- Deportes
    ('Balón de fútbol', 'Balón número 5 cosido a máquina, para cancha sintética o natural.', 89000, 'unidad', 18, 'football.jpg', 12),
    ('Bicicleta de montaña', 'Marco de aluminio, rin 29 y frenos de disco hidráulicos.', 2890000, 'unidad', 3, 'bicycle.jpg', 12),
    ('Juego de mancuernas', 'Mancuernas de colores recubiertas en vinilo, de 1 a 5 kg.', 199000, 'unidad', 7, 'dumbbells.jpg', 12),
    ('Tapete de yoga', 'Tapete antideslizante de algodón, fácil de enrollar.', 79000, 'unidad', 0, 'yoga-mat.jpg', 12);

-- Sample reviews (user ids 2 and 3 are the demo customers created by the Accounts Service)
INSERT INTO reviews (product_id, user_id, user_name, rating, comment) VALUES
    (1, 2, 'Ana Gómez', 5, 'Llegaron en su punto, ni muy verdes ni muy maduros.'),
    (1, 3, 'Carlos Ruiz', 4, 'Buen precio y buen tamaño.'),
    (5, 2, 'Ana Gómez', 5, 'Muy frescas y dulces.'),
    (19, 3, 'Carlos Ruiz', 4, 'Carne fresca, el peso llegó muy cerca de lo que pedí.'),
    (24, 2, 'Ana Gómez', 5, 'Huevos grandes y ninguno llegó roto.');
