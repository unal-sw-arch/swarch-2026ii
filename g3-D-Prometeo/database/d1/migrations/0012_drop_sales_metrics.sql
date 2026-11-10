-- Las métricas de ventas acumuladas ahora viven en MongoDB (dueño), servidas
-- al Worker a través del metrics-gateway. D1 deja de almacenarlas: el Worker ya
-- no lee ni escribe estas tablas. Sus índices se eliminan junto con las tablas.
--
-- Se conservan intactos resource_versions y el trigger realtime_dashboard_billing
-- (0004): la señal de "dashboard cambió" sigue en D1 y mantiene vivo el tiempo
-- real; solo cambió de dónde se leen los números cuando el cliente re-consulta.

DROP TABLE IF EXISTS daily_category_sales;
DROP TABLE IF EXISTS daily_product_sales;
DROP TABLE IF EXISTS sales_metric_buckets;
DROP TABLE IF EXISTS hall_order_status_totals;
DROP TABLE IF EXISTS hall_sales_totals;
