-- =====================================================
-- SERVICIOS (requiere users + categories + offerer_profiles)
-- Categorías: 1=Carpintería 2=Electricidad 3=Fontanería
--   4=Jardinería 5=Limpieza 6=Pintura
--   7=Electrodomésticos 8=Vehículos 9=Tecnología
-- Ofertantes: 1-10 (requiere offerer_profiles)
-- =====================================================
SET NAMES utf8mb4;

INSERT INTO services (
    offerer_id,
    title,
    description,
    photos,
    price_hourly,
    category_id,
    average_duration_minutes,
    is_active,
    operation_radius_km
)
VALUES
-- ── CARPINTERÍA (cat 1) ──────────────────────────────────
(1,
 'Carpintería a medida - Muebles y restauración',
 'Fabricación de muebles a medida: cocinas, armarios, estanterías y repisas en madera maciza. Restauración de muebles antiguos, injerto de piezas y acabados en barniz o laca. Trabajo con materiales como roble, pino, nogal y MDF. Presupuesto sin compromiso.',
 '["https://images.serviya.com/carpinteria/mueble-cocina-01.jpg", "https://images.serviya.com/carpinteria/restauracion-mesa-02.jpg"]',
 35.00,
 1,
 180,
 TRUE,
 8.50
),

(10,
 'Puertas y ventanas - Instalación y ajuste',
 'Instalación, reparación y ajuste de puertas de interior y exterior, ventanas de madera y aluminio. Cambio de cerraduras, bisagras y guarniciones. Sellado y aislamiento para mejorar eficiencia energética del hogar.',
 '["https://images.serviya.com/carpinteria/puerta-madera-01.jpg", "https://images.serviya.com/carpinteria/ventana-aluminio-02.jpg"]',
 28.00,
 1,
 120,
 TRUE,
 5.25
),

(10,
 'Deck y terraza de madera',
 'Diseño e instalación de decks para terrazas, patios y jardines. Tratamiento anti-hongos e impermeabilización. Reparación de tablones sueltos y reacabado de superficies deterioradas por intemperie.',
 '["https://images.serviya.com/carpinteria/deck-terraza-01.jpg"]',
 42.00,
 1,
 240,
 TRUE,
 15.00
),

-- ── ELECTRICIDAD (cat 2) ─────────────────────────────────
(4,
 'Instalaciones eléctricas residenciales',
 'Instalación y renovación de cableado eléctrico en viviendas y apartamentos. Tableros generales y divisiones, puesta a tierra, protección contra sobrecorriente. Cumplimiento con normas RETIE. Atención de cortos circuitos y fallas de iluminación.',
 '["https://images.serviya.com/electricidad/tablero-residencial-01.jpg", "https://images.serviya.com/electricidad/cableado-02.jpg"]',
 40.00,
 2,
 150,
 TRUE,
 12.00
),

(4,
 'Iluminación LED - Proyectos y ejecución',
 'Diseño y ejecución de esquemas de iluminación LED para hogares, oficinas y locales comerciales. Instalación de perfiles LED, luces empotradas, tiras led y reguladores inteligentes. Ahorro energético garantizado.',
 '["https://images.serviya.com/electricidad/led-oficina-01.jpg", "https://images.serviya.com/electricidad/led-sala-02.jpg"]',
 38.00,
 2,
 120,
 TRUE,
 10.00
),

-- ── FONTANERÍA (cat 3) ──────────────────────────────────
(1,
 'Reparación de fugas y tuberías',
 'Detección y reparación de fugas de agua en paredes, techos y pisos. Soldadura de tuberías de PVC y cobre, reemplazo de grifería dañada, desatasco de obstrucciones. Servicio de emergencia disponible.',
 '["https://images.serviya.com/fontaneria/reparacion-fuga-01.jpg", "https://images.serviya.com/fontaneria/tuberia-cobre-02.jpg"]',
 32.00,
 3,
 90,
 TRUE,
 6.75
),

(1,
 'Instalación de baños completos',
 'Instalación sanitaria completa: inodoros, lavamanos, duchas, bañeras y accesorios. Conexión de agua fría y caliente, desagüe y ventilación. Remodelación de baños con acabados modernos.',
 '["https://images.serviya.com/fontaneria/bano-completo-01.jpg"]',
 55.00,
 3,
 240,
 TRUE,
 10.00
),

(1,
 'Calentador de agua - Instalación y mantenimiento',
 'Instalación y reparación de calentadores de gas y eléctricos. Des calcificación, cambio de resistencias, revisión de válvulas de seguridad. Asesoría para la选购 del equipo adecuado según consumo familiar.',
 '["https://images.serviya.com/fontaneria/calentador-gas-01.jpg"]',
 45.00,
 3,
 120,
 TRUE,
 8.00
),

-- ── JARDINERÍA (cat 4) ──────────────────────────────────
(1,
 'Mantenimiento de jardines y áreas verdes',
 'Podade de árboles y arbustos, desmalezado, siega de césped, abono y fertilización. Diseño de jardines con plantas nativas y exóticas. Instalación de sistemas de riego por aspersión y goteo.',
 '["https://images.serviya.com/jardineria/mantenimiento-jardin-01.jpg", "https://images.serviya.com/jardineria/riego-goteo-02.jpg"]',
 25.00,
 4,
 180,
 TRUE,
 15.00
),

(1,
 'Paisajismo y diseño de exteriores',
 'Proyecto integral de paisajismo: selección de especies, distribución espacial, senderos, fuentes ornamentales y iluminación de jardín. Transformación completa de patios, quintas y zonas comunes de conjuntos residenciales.',
 '["https://images.serviya.com/jardineria/paisajismo-01.jpg", "https://images.serviya.com/jardineria/fuente-ornamental-02.jpg"]',
 50.00,
 4,
 300,
 TRUE,
 20.00
),

-- ── LIMPIEZA (cat 5) ────────────────────────────────────
(2,
 'Limpieza profunda de hogares',
 'Servicio de aseo completo para viviendas: pisos, baños, cocinas, vidrieras, muebles tapizados y electrodomésticos. Productos biodegradables y equipo profesional. Ideal para mudanzas o limpieza de temporada.',
 '["https://images.serviya.com/limpieza/hogar-profundo-01.jpg", "https://images.serviya.com/limpieza/cocina-profunda-02.jpg"]',
 22.00,
 5,
 180,
 TRUE,
 4.50
),

(2,
 'Limpieza de oficinas y locales comerciales',
 'Aseo diario, semanal o quincenal de espacios comerciales. Limpieza de pisos, mobiliario, zonas de recepción, baños y áreas comunes. Servicios programados con personal fijo y productos de calidad.',
 '["https://images.serviya.com/limpieza/oficina-limpieza-01.jpg"]',
 20.00,
 5,
 120,
 TRUE,
 6.00
),

(2,
 'Limpieza de tapicerías y alfombras',
 'Limpieza profunda de muebles tapizados, alfombras, cortinas y colchones con extracción de suciedad y ácaros. Tratamiento antimanchas y desodorización. Servicio a domicilio con equipo especializado.',
 '["https://images.serviya.com/limpieza/tapiceria-01.jpg", "https://images.serviya.com/limpieza/alfombra-limpieza-02.jpg"]',
 28.00,
 5,
 90,
 TRUE,
 3.00
),

-- ── PINTURA (cat 6) ─────────────────────────────────────
(1,
 'Pintura interior y exterior de viviendas',
 'Preparación de superficies, tapizado de grietas, imprimación y pintura con acabados mates, satinados o brillantes. Pintura de techos, paredes, molduras y puertas. Asesoría en combinación de colores y tendencias.',
 '["https://images.serviya.com/pintura/interior-sala-01.jpg", "https://images.serviya.com/pintura/exterior-fachada-02.jpg"]',
 18.00,
 6,
 240,
 TRUE,
 12.50
),

(1,
 'Pintura decorativa - Falsos techos y paredes',
 'Ejecución de acabados decorativos: efecto empapelado, estuco veneciano, micaceo, marmolino y efecto concreto. Falsos techos de drywall con pintura texturizada. Renovación integral de interiores.',
 '["https://images.serviya.com/pintura/decorativa-estuco-01.jpg"]',
 30.00,
 6,
 180,
 TRUE,
 8.00
),

-- ── ELECTRODOMÉSTICOS (cat 7) ───────────────────────────
(1,
 'Reparación de neveras y congeladores',
 'Diagnóstico y reparación de refrigeradores domésticos y comerciales. Recarga de gas refrigerante, cambio de compresor, termostato y ventiladores. Solución de filtraciones y problemas de enfriamiento.',
 '["https://images.serviya.com/electrodomesticos/reparacion-nevera-01.jpg"]',
 45.00,
 7,
 120,
 TRUE,
 7.00
),

(1,
 'Lavadoras y secadoras - Servicio técnico',
 'Reparación de lavadoras automáticas, semiautomáticas y secadoras de ropa. Cambio de rodamientos, correas, válvulas y bombas de drenaje. Servicio a domicilio con garantía en repuestos.',
 '["https://images.serviya.com/electrodomesticos/lavadora-servicio-01.jpg"]',
 40.00,
 7,
 90,
 TRUE,
 5.50
),

(1,
 'Aire acondicionado - Instalación y mantenimiento',
 'Instalación, limpieza y recarga de gases para aires acondicionados split y centrales. Mantenimiento preventivo y correctivo. Desinfección de filtros y evaporadores. Atención de fugas de refrigerante.',
 '["https://images.serviya.com/electrodomesticos/aire-acondicionado-01.jpg"]',
 50.00,
 7,
 150,
 TRUE,
 18.00
),

-- ── VEHÍCULOS (cat 8) ───────────────────────────────────
(1,
 'Mecánica general - Carros y motos',
 'Diagnóstico computarizado, afinamiento mayor y menor, cambio de aceite y filtros, revisión de frenos, suspensión y dirección. Reparación de motor, caja de cambios y sistema de refrigeración. Atención a domicilio.',
 '["https://images.serviya.com/vehiculos/mecanica-general-01.jpg", "https://images.serviya.com/vehiculos/frenos-02.jpg"]',
 35.00,
 8,
 150,
 TRUE,
 10.00
),

(1,
 'Latonería y pintura automotriz',
 'Enderezamiento de laminas, reparación de abolladuras, pulido de pintura, desmanchado y encerado profesional. Pintura completa o parcial de carrocería. Tratamiento de óxido y protección anticorrosiva.',
 '["https://images.serviya.com/vehiculos/latoneria-pulido-01.jpg"]',
 40.00,
 8,
 300,
 TRUE,
 15.00
),

(1,
 'Electroautomotriz - Electricidad de vehículos',
 'Reparación del sistema eléctrico automotriz: alternadores, marchas, luces, cerraduras centrales, alarma y arranque remoto. Instalación de accesorios electrónicos: cámaras de reversa, pantallas y sonido.',
 '["https://images.serviya.com/vehiculos/electroautomotriz-01.jpg"]',
 42.00,
 8,
 120,
 TRUE,
 8.00
),

-- ── TECNOLOGÍA (cat 9) ──────────────────────────────────
(1,
 'Reparación de celulares y tablets',
 'Cambio de pantalla, batería, conector de carga y altavoces. Desbloqueo de equipos, recuperación de datos y formateo. Reparación de placas, soldadura de componentes y solución de fallas de software.',
 '["https://images.serviya.com/tecnologia/celular-reparacion-01.jpg", "https://images.serviya.com/tecnologia/tablet-servicio-02.jpg"]',
 25.00,
 9,
 60,
 TRUE,
 3.50
),

(1,
 'Soporte técnico de computadores',
 'Reparación de computadores de escritorio y portátiles: cambio de disco duro a SSD, ampliación de memoria RAM, limpieza interna y cambio de pasta térmica. Formateo con instalación de sistema operativo y programas.',
 '["https://images.serviya.com/tecnologia/pc-reparacion-01.jpg"]',
 30.00,
 9,
 90,
 TRUE,
 5.00
),

(1,
 'Redes y cableado estructurado',
 'Instalación y configuración de redes domésticas y empresariales. Cableado de par trenzado CAT6/CAT6A, patch panels, switches y routers. Configuración de WiFi, cámaras IP y sistemas de vigilancia.',
 '["https://images.serviya.com/tecnologia/redes-cableado-01.jpg"]',
 38.00,
 9,
 180,
 TRUE,
 20.00
),

(1,
 'Climatización de data centers',
 'Diseño e implementación de sistemas de climatización para salas de servidores. Instalación de aire acondicionado de precisión, monitoreo de temperatura y humedad. Optimización de eficiencia energética del rack.',
 '["https://images.serviya.com/tecnologia/datacenter-clima-01.jpg"]',
 65.00,
 9,
 360,
 TRUE,
 25.00
);
