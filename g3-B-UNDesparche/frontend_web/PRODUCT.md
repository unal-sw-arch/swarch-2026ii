# PRODUCT.md — UNDesparche

## 1. Visión y Propósito
**UNDesparche** es la plataforma comunitaria oficial de la Universidad Nacional de Colombia (Sede Bogotá) diseñada para transformar la vida universitaria en el campus a través de tres pilares fundamentales:
1. **Eventos**: Publicación, descubrimiento geolocalizado y suscripción a actividades académicas, culturales, deportivas y asambleas.
2. **Implementos e Inventario**: Catálogo y préstamo descentralizado de material deportivo y recreativo por facultades (reserva express de 10 minutos, recogida con carné institucional y devolución).
3. **Mapa Interactivo del Campus**: Visualización espacial del campus con georreferenciación de eventos y puntos de encuentro.

## 2. Audiencia y Roles
Exclusivo para la comunidad universitaria de la UNAL (autenticación obligatoria con dominio `@unal.edu.co`).

- **Miembro de la Comunidad** (Rol por defecto): Consulta eventos, se suscribe con su correo institucional, explora el catálogo de implementos de todas las facultades, reserva implementos (máx. 1 activo a la vez) y navega el mapa.
- **Administrador de Eventos**: Crea, edita, publica y gestiona eventos institucionales.
- **Administrador de Implementos**: Administra el inventario de su facultad específica, confirma entregas presenciales (convirtiendo reservas en préstamos activos) y registra devoluciones.
- **Administrador del Sistema**: Acceso global y supervisión general. Gestiona roles y estados de usuarios, administra eventos globales y supervisa el inventario de las 11 facultades.

## 3. Principios del Producto
- **Cero Fricción para Estudiantes**: El campus es dinámico. Las reservas se hacen en segundos desde el celular o portátil entre clases.
- **Transparencia Institucional**: Estados claros de eventos (`Programado`, `En Curso`, `Cancelado`, `Finalizado`) y de inventario (`Disponible`, `Reservado`, `Prestado`, `No Disponible`).
- **Seguridad y Disciplina Comunitaria**: Temporizador de recogida de 10 minutos para evitar acaparamiento. Estado sancionado (`SAN`) informativo que restringe reservas hasta regularizar situación.
- **Inclusión y Accesibilidad**: Cumplimiento WCAG AA, soporte para teclado, lectores de pantalla (`aria-live`, `aria-label`) y respeto absoluto por `prefers-reduced-motion`.
