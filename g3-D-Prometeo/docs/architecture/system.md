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

D1 es la única fuente de verdad. `database/d1/migrations` define su evolución. Los precios se almacenan como pesos enteros, no como centavos.

## Clientes

Los clientes conocen una URL estable mediante `RESTAURANT_API_URL`. No hay descubrimiento local, certificados propios, mDNS ni QR de emparejamiento.
