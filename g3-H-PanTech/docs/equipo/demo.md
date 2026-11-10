# Guion de demostración — Panadería Express P2

Este documento es la ruta corta para levantar, sembrar y demostrar el Prototipo 2 sin
improvisar. Todos los comandos se ejecutan desde `g3-H-PanTech/`.

## 1. Requisitos

- Docker Desktop con Docker Compose v2.
- Python 3.11 o superior (solo para `scripts/seed_demo.py` y
  `scripts/smoke_test.py`; no requiere paquetes externos).
- Flutter SDK solo si se quiere abrir el KDS/POS de escritorio.
- Puertos libres: `3000` (Web SSR), `8080` (Gateway), `15672` (RabbitMQ),
  `9000` (RustFS S3), `9001` (consola RustFS), `5432`/`27017`/`6379` si se
  publican para inspección local.

## 2. Levantar el stack

```bash
cp .env.p2.example .env.p2
docker compose -f docker-compose.p2.yml --env-file .env.p2 up --build -d
```

El compose levanta los servicios y dos jobs efímeros:

- `rabbitmq-init`: importa el exchange, las colas y los bindings.
- `rustfs-perms`: prepara el volumen de objetos.
- `rustfs-init`: crea los buckets; se ejecuta una vez con el perfil `init`:

```bash
docker compose -f docker-compose.p2.yml --env-file .env.p2 \
  --profile init run --rm rustfs-init
```

Esperar a que Gateway y Web SSR respondan:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/wait_for_services.ps1
```

En Linux/macOS se puede usar el equivalente:

```bash
until curl -fsS http://localhost:8080/health >/dev/null; do sleep 2; done
```

## 3. Sembrar datos demo

El seed de Flyway y el arranque de Order Service ya crean usuarios y productos.
Este script es idempotente y completa lo que falte usando el API Gateway:

```bash
python scripts/seed_demo.py
```

## 4. Smoke test automático

Con el stack arriba y el Worker consumiendo eventos:

```bash
python scripts/smoke_test.py
```

El script recorre: login → menú → pedido → pago simulado → cola de cocina
(`en_preparacion` y `listo`) → entrega → historial → reserva cancelada → reportes.
Si algo falla, imprime el endpoint y el cuerpo de error. Se puede aumentar el
tiempo de espera de los eventos asíncronos:

```bash
python scripts/smoke_test.py --timeout 180
```

## 5. Cuentas demo

Todas usan la contraseña `Demo1234!`.

| Cuenta | Rol | Para demostrar |
|---|---|---|
| `cliente@demo.com` | `cliente` | Menú, carrito, pedido para llevar, pago y seguimiento |
| `mesero@demo.com` | `mesero` | Pedido de mostrador, cobro y reservas |
| `cocinero@demo.com` | `cocinero` | Cola de cocina y cambio a `en_preparacion`/`listo` |
| `despachador@demo.com` | `despachador` | Entrega de pedidos listos |
| `supervisor@demo.com` | `supervisor` | Historial general, cancelaciones, reportes y cuentas operativas |
| `admin@demo.com` | `admin` | Productos, roles, permisos y auditoría |
| `multi@demo.com` | `mesero` + `despachador` | Cuenta multi-rol: permisos efectivos unidos |

## 6. Guion sugerido (10–12 minutos)

1. **Cliente**
   - Entrar a http://localhost:3000 con `cliente@demo.com`.
   - Abrir **Menú**, agregar dos productos al carrito y confirmar el pedido.
   - En la pantalla de pago, pagar el total simulado.
   - Mostrar el detalle del pedido y el estado que se actualiza por polling.

2. **Cocina**
   - Entrar con `cocinero@demo.com` y abrir **Backoffice → Cocina (KDS)**.
   - Mostrar la cola priorizada, el ETA y el pedido recién pagado.
   - Cambiar el pedido a **En preparación** y luego a **Listo** (la vista web es de
     solo lectura; el cambio se hace desde el KDS/POS o con el smoke test).
   - Si se usa el KDS Flutter: `flutter run -d windows`.

3. **Despacho**
   - Entrar con `despachador@demo.com`; en **Backoffice → Cocina (KDS)** solo aparecen los pedidos listos.
   - Confirmar la entrega desde Flutter KDS/POS; la vista web de despacho es de solo lectura.

4. **Backoffice**
   - Entrar con `supervisor@demo.com`: historial general, cancelar un pedido
     pendiente y abrir reportes.
   - Entrar con `admin@demo.com`: crear un producto, desactivar una cuenta y
     mostrar la matriz de roles/permisos y la auditoría.

5. **Denegaciones**
   - Intentar abrir `/staff/roles` con `mesero@demo.com`: el middleware redirige a
     `/forbidden`.
   - Intentar cobrar un pedido ajeno con `cliente@demo.com`: el Gateway/Order
     Service responde `403`.
   - Intentar cambiar la cola con `mesero@demo.com`: `403`.

## 7. Rutas principales

| Ruta | Audiencia | Descripción |
|---|---|---|
| `/` | Público | Portada y estado del prototipo |
| `/menu` | Autenticado | Menú renderizado en servidor |
| `/cart` | Autenticado | Carrito en cookie httpOnly |
| `/checkout` | Autenticado | Confirmación del pedido |
| `/checkout/payment?orderId=…` | Autenticado | Pago simulado |
| `/orders` | Autenticado | Pedidos propios |
| `/orders/[id]` | Autenticado | Seguimiento con polling |
| `/reservations` | Autenticado | Reservas propias |
| `/staff` | Personal | Panel filtrado por permisos |
| `/staff/orders` | Mesero/supervisor/admin | Pedidos de mostrador y cobro |
| `/staff/reservations` | Mesero/supervisor/admin | Confirmar/cancelar reservas |
| `/staff/history` | Supervisor/admin | Historial general |
| `/staff/cancel` | Supervisor/admin | Cancelar pedidos pendientes |
| `/staff/products` | Admin | CRUD del catálogo |
| `/staff/users` | Supervisor/admin | Cuentas y roles |
| `/staff/roles` | Admin | Matriz de permisos y auditoría |
| `/staff/reports` | Supervisor/admin | Ventas, tiempos y exportación |
| `/staff/kitchen` | Cocinero/despachador/admin | Cola de cocina de respaldo |

## 8. Reinitializar la demo

```bash
docker compose -f docker-compose.p2.yml --env-file .env.p2 down -v
docker compose -f docker-compose.p2.yml --env-file .env.p2 up --build -d
python scripts/seed_demo.py
```

`down -v` borra PostgreSQL, MongoDB, Redis y RustFS. Es la forma más rápida de
volver a un estado limpio antes de una presentación.

## 9. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `wait_for_services.ps1` agota el tiempo | Puerto ocupado o build fallido | `docker compose ... ps` y `docker compose ... logs --tail=100 api-gateway web-ssr` |
| El pedido queda `pagado` y no aparece en cocina | Worker no consume o RabbitMQ no tiene bindings | Revisar `docker compose ... logs worker`, `rabbitmq-init` y la consola de RabbitMQ |
| El pago responde `409` | Se envió dos veces el pago con la misma llave de idempotencia | Es el comportamiento esperado; usar una llave nueva por intento |
| El historial tarda | Consistencia eventual de los read models | Esperar unos segundos; el smoke test reintenta |
| `429` en ráfaga | Rate limit del Gateway | Bajar el ritmo o ajustar `RATE_LIMIT_*` en `.env.p2` |
| KDS no compila en Windows | Requiere Visual Studio C++ y Flutter Windows habilitado | Usar `web-ssr` → `/staff/kitchen` como respaldo documentado |

## 10. Estado de verificación

En el entorno de integración usado para este cierre no había daemon de Docker ni
SDK de Go/Flutter, por lo que se validaron de forma estática y con las herramientas
disponibles:

- `docker compose ... config --quiet` para P1 y P2.
- `npm run typecheck` y build de Web SSR.
- `python -m compileall` de scripts, Order Service y Worker.
- Pruebas unitarias de Order Service/Worker cuando sus dependencias estaban
  instaladas.

El smoke test completo, las pruebas Go, Flutter y .NET deben ejecutarse en una
máquina con Docker y los SDK correspondientes. El workflow `.github/workflows/ci-p2.yml`
queda preparado para esa verificación.
