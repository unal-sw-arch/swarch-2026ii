# Gestor de Inventario (BUFF/T)

FastAPI (HTTP, puerto **8003**) + servidor gRPC (puerto **50051**) sobre **MongoDB** (`bd_inventario`, vía pymongo).

## Cómo se acopla al resto
| Conector del diagrama | Quién → quién | Implementado en |
|---|---|---|
| `<<http>>` | API Gateway → Gestor de Inventario | `app/routes.py` (el gateway reenvía `/inventory/{path}` → `:8003/{path}`) |
| `<<gRPC>>` | Gestor de Pedidos → Gestor de Inventario | `app/grpc_server.py` (`ValidateAndDeductStock`, mismo `.proto` de Pedidos) |
| BD inventario | Gestor de Inventario → MongoDB | `app/database.py` (pymongo) |

## Correr local
```bash
# 1) Mongo (desde BUFFIT/)
docker compose up -d db-inventario

# 2) Servicio (desde BUFFIT/apps/gestor-inventario)
python -m venv .venv && .venv\Scripts\activate      # Windows
pip install -r requirements.txt
python run.py
```
Al arrancar, si la BD está vacía se cargan datos de ejemplo para 3 comedores (Central, Hemeroteca, Agronomía) y se levanta el servidor gRPC.

Probar: `http://localhost:8003/opciones` · `http://localhost:8003/health` · documentación interactiva en `http://localhost:8003/docs`
Prueba automática sin Mongo: `pip install mongomock httpx` y `python tests/smoke_test.py`

## Como contenedor (agregar a `docker-compose.yml`)
```yaml
  gestor-inventario:
    build: ./apps/gestor-inventario
    container_name: gestor_inventario
    environment:
      MONGO_URI: mongodb://mongo_buffit:password_buffit@db-inventario:27017/?authSource=admin
    ports:
      - "8003:8003"
      - "50051:50051"
    depends_on:
      - db-inventario
```

## Endpoints (HTTP)
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/opciones?comedor=` | cualquiera | Opciones del menú para el Portal (solo con stock > 0) |
| GET | `/menu?comedor=` | cualquiera | Menú por categoría con cantidades |
| GET | `/items?comedor=&categoria=` | cualquiera | Inventario completo |
| POST | `/items` | Comedor / Admin | Crea ítem o fija cantidad: `{dining_hall, product_name, category, quantity}` |
| PUT | `/items/{id}` | Comedor / Admin | Modifica `quantity`, `product_name` o `category` |
| DELETE | `/items/{id}` | Comedor / Admin | Elimina ítem |
| POST | `/restock` | cualquiera | Devuelve porciones si se cancela un pedido |
| POST | `/reportes/sobrantes` | Comedor / Admin | Informe de sobrantes al cierre: `{dining_hall}` |
| GET | `/reportes/sobrantes?comedor=` | cualquiera | Historial de informes |
| POST | `/seed?reset=true` | Comedor / Admin | Recarga datos de ejemplo |

Los roles se leen del JWT (`role`: `Comedor` o `Administrador`). Para desactivarlo en desarrollo: `ENFORCE_ROLES=false`.

## gRPC
`ValidateAndDeductStock(dining_hall, items[])` valida existencias de **todos** los ítems y descuenta; si falta alguno no descuenta nada. Los ítems repetidos se suman (mismo carbohidrato dos veces = 2 porciones). Se busca por comedor + nombre del producto.

## Qué hace cada archivo
| Archivo | Para qué sirve |
|---|---|
| `run.py` | Punto de entrada local: arranca uvicorn con la app (`python run.py`). |
| `Dockerfile` | Construye la imagen del servicio (Python 3.11 + dependencias) y lo arranca con uvicorn en el 8003. |
| `requirements.txt` | Dependencias: FastAPI/uvicorn (HTTP), pymongo (Mongo), grpcio/protobuf (gRPC), pyjwt (roles). |
| `.env.example` | Variables configurables (puertos, URI de Mongo, secreto JWT). Cópialo a `.env` si necesitas cambiar algo. |
| `.gitignore` | Evita subir `__pycache__`, entornos virtuales y `.env`. |
| `app/main.py` | Crea la app FastAPI. Al arrancar prepara índices, carga datos de ejemplo y levanta el servidor gRPC; al cerrar lo detiene. Registra rutas, CORS y el manejo de errores. |
| `app/config.py` | Lee la configuración (puertos, Mongo, JWT, roles permitidos) desde variables de entorno. |
| `app/database.py` | Conexión a MongoDB (`bd_inventario`) y creación del índice único comedor + producto. |
| `app/routes.py` | Endpoints HTTP que consume el API Gateway (menú, CRUD de inventario, restock, reportes, seed). |
| `app/schemas.py` | Modelos Pydantic que validan los cuerpos de las peticiones (y alimentan `/docs`). |
| `app/security.py` | Dependencia que verifica el JWT y exige rol Comedor/Administrador en las rutas que modifican inventario. |
| `app/inventory_service.py` | Lógica de negocio: consulta de menú, descuento atómico con reversión, restock, informe de sobrantes y datos de ejemplo. No depende de FastAPI ni de gRPC. |
| `app/grpc_server.py` | Servidor gRPC que implementa `ValidateAndDeductStock`; es el conector con Gestor de Pedidos. |
| `app/inventory.proto` | Contrato gRPC (copia exacto del de Gestor de Pedidos). |
| `app/inventory_pb2.py` / `app/inventory_pb2_grpc.py` | Código generado a partir del `.proto` (no se edita a mano). Ya está generado, no hay que correr protoc. |
| `app/__init__.py` | Vacío; marca `app` como paquete Python. |
| `tests/smoke_test.py` | Prueba automática de HTTP, roles y gRPC usando un Mongo simulado. |
