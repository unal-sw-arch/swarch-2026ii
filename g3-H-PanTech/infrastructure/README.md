# Infraestructura — Prototipo 2

**Responsable:** Jaime Darley Angulo Tenorio.

Esta carpeta contiene la inicialización de los almacenes y del broker. No contiene lógica de negocio.

## Archivos

| Archivo | Qué hace | Cuándo se ejecuta |
|---|---|---|
| `postgres/init.sql` | Crea la base, los esquemas `auth`, `orders`, `kitchen`, `reporting` y las extensiones `pgcrypto` y `citext`. | Solo al crear el volumen `postgres_data`. |
| `mongodb/init.js` | Crea las colecciones `menu`, `order_history`, `reservation_read`, `report_snapshots` y sus índices. | Solo al crear el volumen `mongo_data`. |
| `redis/redis.conf` | Activa AOF, fija `maxmemory-policy noeviction` y configura persistencia. | En cada arranque del contenedor. |
| `minio/init.sh` | Crea los buckets `receipts`, `products` y `reports` y sus políticas. | Manual, con el perfil `init`. |
| `rabbitmq/rabbitmq.conf` | Carga las definiciones al arrancar. | En cada arranque. |
| `rabbitmq/definitions.json` | Declara el exchange `panaderia.events`, la DLX, las colas y los bindings. | Al arrancar RabbitMQ. |

## Orden de inicialización

1. `postgres` y `mongodb` ejecutan sus scripts cuando Docker crea el volumen por primera vez.
2. `redis` y `rabbitmq` leen su configuración en cada arranque.
3. `minio` arranca vacío; los buckets se crean con el job `minio-init`.

## Crear los buckets de MinIO

El job no se ejecuta con `docker compose up`; hay que invocarlo una vez:

```bash
docker compose -f docker-compose.p2.yml --env-file .env.p2 \
  --profile init run --rm minio-init
```

Verificación:

```bash
docker compose -f docker-compose.p2.yml --env-file .env.p2 \
  exec minio ls /data
```

## Reiniciar desde cero

```bash
docker compose -f docker-compose.p2.yml --env-file .env.p2 down -v
docker compose -f docker-compose.p2.yml --env-file .env.p2 up --build -d
```

`down -v` borra los volúmenes y vuelve a ejecutar `init.sql` e `init.js`.

## Reglas para los dueños de servicios

- No editen `postgres/init.sql`: creen sus tablas con migraciones dentro de su servicio.
- No editen `mongodb/init.js`: creen sus índices en `order-service/migrations/mongo/`.
- Si necesitan una cola o un binding nuevo, propongan el cambio en `rabbitmq/definitions.json` y Jaime lo aplica.
- Si necesitan una variable nueva, propongan el cambio en `.env.p2.example` y Jaime lo consolida.

## Verificación rápida

```bash
docker compose -f docker-compose.p2.yml --env-file .env.p2 config --quiet
```

Debe terminar sin errores.
