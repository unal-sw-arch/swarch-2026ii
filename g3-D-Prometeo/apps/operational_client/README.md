# Cliente operativo

Aplicación Flutter para seleccionar salones, operar mesas, tomar y entregar pedidos, facturar, dividir cuentas y consultar ventas del turno.

## Código

- `lib/features`: pantallas y flujos agrupados por capacidad.
- `lib/models`: contratos que representan usuarios, salones y pedidos.
- `lib/services`: HTTP, WebSocket, sesión segura y adaptación de respuestas.
- `lib/utils`: utilidades transversales de presentación.
- `test`: pruebas unitarias y de widgets.

Los directorios de plataforma son administrados por Flutter. El identificador nativo de la aplicación se conserva para mantener compatibilidad con instalaciones existentes, aunque el paquete Dart se llama `restaurante_operational`.
