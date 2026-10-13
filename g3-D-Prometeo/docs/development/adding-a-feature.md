# Añadir una funcionalidad

1. Identifica qué aplicación es dueña del comportamiento.
2. Crea o amplía una carpeta en `features` y documenta su alcance.
3. Mantén transporte, persistencia y UI separados.
4. Si cambia el contrato, actualiza Worker y clientes en el mismo cambio.
5. Si cambia D1, añade una migración nueva.
6. Cubre el flujo con pruebas y ejecuta la verificación completa.

Cuando una feature necesite tiempo real, define primero el recurso y el parche mínimo que el Durable Object debe publicar; no uses una recarga global como mecanismo por defecto.
