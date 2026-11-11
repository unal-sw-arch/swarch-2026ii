# Cómo correr el programa y confirmar que funciona

## Camino rápido: un solo comando (necesitas Docker Desktop y Python)

Desde esta carpeta (`docs/equipo/propuestas-yeison/verificacion/`):

```bash
pip install "PyJWT[crypto]"
python gen_token.py cliente          # crea ./keys la primera vez e imprime un TOKEN: cópialo
docker compose -f docker-compose.demo.yml up --build
```

Usa la **infraestructura real** de `g3-H-PanTech/infrastructure` (Postgres, Mongo, Redis, RabbitMQ y RustFS) y los mismos nombres de
variables que `docker-compose.p2.yml`. Solo sustituye lo que aún no existe: Identity (el token lo firma `gen_token.py`) y Kitchen (`fake-kitchen`).

La primera vez tarda varios minutos. Está listo cuando `docker compose -f docker-compose.demo.yml ps` muestra `order-service` y
`reporting-service` como `healthy`.

Luego, en el navegador:

1. **http://localhost:8000/docs** → **Authorize** → pega el token (sin la palabra `Bearer`).
2. `GET /menu`: 8 productos.
3. `POST /orders` con `{"order_type": "para_llevar", "items": [{"product_id": "p-cafe-americano", "quantity": 2}]}`: 201, total `7.00`. Copia el `id`.
4. `POST /orders/{id}/payment` con `{"amount": "7.00"}` y el header `Idempotency-Key` = `k1`: 201, estado `pagado`.
   Repítelo igual: 200 con `idempotent_replay: true` (no se cobra dos veces).
5. A los ~10 segundos, `GET /orders/{id}/status`: **`listo`**. Flujo completo: pago → `order.paid` → Worker → cocina → `order.ready` → estado.
6. Efectos secundarios:
   - Logs: `docker compose -f docker-compose.demo.yml logs worker fake-kitchen`.
   - Comprobante PDF: http://localhost:9001 (usuario `panaderia`, clave `panaderia123`) → bucket `receipts`.
   - Colas: http://localhost:15672 (usuario `panaderia`, clave `panaderia`): `worker.order_paid`, `worker.order_ready`... y `panaderia.dlq`.
7. Reporting: `python gen_token.py supervisor`, abre **http://localhost:8003/docs**, autoriza y prueba `GET /history` y `GET /reports/sales`.
   (`/reports/times` da 503 hasta que exista la tabla de Kitchen; es lo esperado.)

Apagar y borrar todo: `docker compose -f docker-compose.demo.yml down -v`.
Si algo falla: `docker compose -f docker-compose.demo.yml logs <servicio>`.

## Casos de fallo (los más valiosos)
- **Reintentos y DLQ:** detén la cocina simulada (`docker compose -f docker-compose.demo.yml stop fake-kitchen`), paga un pedido y mira la consola de RabbitMQ:
  el Worker reintenta y, al agotar los intentos, el mensaje aparece en `panaderia.dlq`.
- **Outbox:** `docker compose -f docker-compose.demo.yml stop rabbitmq`, paga un pedido (debe responder 201) y luego `start rabbitmq`: el evento se publica solo.

## Pruebas automáticas (sin infraestructura)

```bash
cd g3-H-PanTech/order-service      # python -m venv .venv && activar
pip install -r requirements-dev.txt && pytest -q

cd ../worker
pip install -r requirements.txt && python -m unittest discover -s tests -t . -v

cd ../reporting-service            # .NET 8 SDK
dotnet build && dotnet test Tests/ReportingService.Tests.csproj
```

## Construir las imágenes
```bash
cd g3-H-PanTech
docker build -t order-service ./order-service
docker build -t worker ./worker
docker build -t reporting-service ./reporting-service
```
