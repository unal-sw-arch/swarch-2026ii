# Modelo de lectura

Construye snapshots de catálogo, salones y layouts para los endpoints de lectura y bootstrap. No ejecuta mutaciones.

## Totales de ventas por salón

`readHalls(database, employeeId?, hallTotals?)` ya no une una tabla de métricas
de D1. Para administradores, los totales de ventas por salón viven en MongoDB:
el llamador (`routes.ts`, en `/halls` y `/bootstrap`) los obtiene con
`metricsClient(c.env).hallTotals()` y los pasa como `hallTotals`; aquí se leen
los salones de D1 y se combinan en código. Los empleados no reciben totales
(se pasa una lista vacía).
