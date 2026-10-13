# Reglas para asistentes de código

- Respeta los límites descritos en `CONTRIBUTING.md`.
- El backend vigente es exclusivamente `apps/cloud_api`; no recrees `source/backend`, SQLite local, mDNS, mTLS ni emparejamiento por QR.
- Mantén los nombres públicos de API y el comportamiento observable durante refactors.
- Para D1, crea migraciones acumulativas y nunca edites una migración ya desplegada.
- Ubica código nuevo en la feature dueña del comportamiento y actualiza su `README.md`.
- Antes de entregar, ejecuta `npm run cloud:typecheck`, `npm test`, `flutter analyze` y `flutter test` en ambos clientes.
