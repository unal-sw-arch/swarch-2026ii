# Datos y migraciones

- D1 es la base de datos de producción y desarrollo remoto.
- Las migraciones se aplican en orden numérico con Wrangler.
- Los acumulados de ventas (métricas) evitan recalcular todo el historial para las métricas frecuentes. Su dueño es MongoDB (no D1), accedido por el Worker a través del `metrics-gateway`; las órdenes que los originan siguen en D1. Solo aplica en desarrollo local.
- Los snapshots de precio y los productos inactivos preservan el historial de facturación.
- Los scripts de catálogo son idempotentes a nivel de menú y se ejecutan directamente contra D1.

No deben añadirse archivos de base de datos binarios al repositorio.
