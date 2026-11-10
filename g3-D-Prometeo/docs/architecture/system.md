# Arquitectura vigente

```text
Operational client ─┐
                    ├─ HTTPS ─> Cloudflare Worker ─> D1
Admin client ───────┘                 │
          └──────── WebSocket ─> Durable Object
                                      └─ multicast de parches
```

## Lecturas

Los clientes hacen lecturas HTTP al Worker. Las lecturas de catálogo pueden servirse desde caché y se invalidan únicamente por mutaciones de catálogo.

## Escrituras

Las peticiones `POST`, `PUT`, `PATCH` y `DELETE` autenticadas atraviesan el `RestaurantCoordinator`. El Durable Object delega el comando a las rutas del Worker y, cuando tiene éxito, publica el parche correspondiente a los clientes conectados.

## Persistencia

D1 es la fuente de verdad transaccional (usuarios, sesiones, catálogo, salones, órdenes, idempotencia y versiones de recursos). `database/d1/migrations` define su evolución. Los precios se almacenan como pesos enteros, no como centavos.

Las **métricas de ventas acumuladas** son la excepción: su dueño es MongoDB, al que el Worker accede por HTTP mediante el `metrics-gateway` (`services/metrics_gateway/`), ya que los Workers no abren TCP crudo. Es un store de solo lectura/escritura de proyecciones; las órdenes que las originan siguen en D1, y el tiempo real del dashboard se mantiene con el bump de `resource_versions('dashboard')` en D1. Solo aplica en desarrollo local.

## Clientes

Los clientes conocen una URL estable mediante `RESTAURANT_API_URL`. No hay descubrimiento local, certificados propios, mDNS ni QR de emparejamiento.
