# Cliente administrativo

Aplicación Flutter para administrar catálogo, ingredientes, salones, empleados, historial y métricas. Reutiliza la vista operativa del salón mediante una dependencia local explícita a `operational_client`.

## Código

- `lib/features`: pantallas agrupadas por capacidad administrativa.
- `lib/services`: API Cloudflare, sesión y canal en tiempo real.
- `lib/utils`: formato y utilidades de presentación.
- `test`: pruebas unitarias y de widgets.

Los directorios nativos son integración de Flutter y no deben recibir lógica de negocio. El identificador nativo anterior se conserva para no romper actualizaciones instaladas; el paquete Dart se llama `restaurante_admin`.
