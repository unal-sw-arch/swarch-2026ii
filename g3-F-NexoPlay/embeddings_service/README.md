# Embeddings API - videos y perfiles en CPU

Responsable: Camilo. Servicio interno independiente de `recommendation_service`, que corresponde a Nicolás.

Estado de integración: los pasos 1 a 6 se verificaron con Qdrant real. El código del paso 7 incluye integración con Processing, invalidación de videos y consumo desde Recommendation mediante Gateway. Interaction Worker ya está incorporado y adaptado al contrato de perfiles; su despliegue y la verificación conjunta con los servicios reales siguen pendientes para el punto 5.

## Estado de esta entrega

Implementado:

- Validación de metadatos, plantilla determinista y hash del contenido normalizado.
- Modelo MiniLM multilingüe con revisión fija, 384 dimensiones y normalización L2.
- Carga local una vez por proceso, sin descargas durante las solicitudes y con cuatro hilos CPU por defecto.
- Inferencia serializada en CPU; la vista previa mantiene el vector únicamente en memoria.
- Creación idempotente de `video_embeddings` y `user_embeddings` con 384 dimensiones y métrica `Cosine`.
- Validación de formato de vector, revisión del modelo, plantilla y rol mediante metadatos de colección.
- Guardado de embeddings de videos por UUID, con versiones crecientes, regeneración y confirmación posterior a la escritura.
- Diario persistente de versiones y errores, bloqueos por video y recuperación de candidatos tras respuestas perdidas.
- Perfiles de usuario ponderados y normalizados, con recuperación de fuentes por lotes, reconciliación parcial y control persistente de versión y revisión.
- Invalidación de perfiles vacíos mediante un marcador sin vector, compatible con el lector de Recommendation.
- Evaluación reproducible de dos modelos y pruebas automatizadas.
- Servicio FastAPI con configuración de conexión a Qdrant.
- `GET /`: identifica la fase y las capacidades disponibles.
- `GET /health`: comprueba que la API responde.
- `GET /ready`: verifica el modelo cargado, Qdrant y la compatibilidad de ambas colecciones.
- `GET /collections`: consulta de solo lectura del esquema y cantidad de puntos.
- `GET /model`: informa modelo, revisión, dimensión, límite de tokens y configuración.
- `POST /embeddings/preview`: genera un vector en memoria y devuelve `persisted: false`.
- `POST /embeddings/videos`: recibe el contrato de metadatos y guarda o actualiza el punto del video.
- `GET /embeddings/videos/{video_id}/status`: consulta de solo lectura del estado y de las versiones.
- `POST /embeddings/users/recalculate`: recibe el resumen completo de interacciones y confirma el perfil o su invalidación.
- `GET /embeddings/users/{user_id}/status`: consulta de solo lectura del estado del perfil y del último intento.
- `DELETE /embeddings/videos/{video_id}?metadata_version=N`: invalidación mediante un marcador `DELETED` sin vector, con protección frente a escrituras atrasadas; añadido en el paso 7.
- Dockerfile e incorporación a Compose en el puerto interno `8090`.

Pendiente:

- Desplegar y verificar la integración de Processing, su outbox y la invalidación con PostgreSQL y Qdrant reales.
- Verificar en ejecución Interaction Worker: deduplicación, agregado persistente, umbral de 10 eventos, reintentos y reconciliación ya implementados.
- Verificar Gateway y Recommendation en ejecución y completar la demostración con eventos reales.

Los contratos de persistencia de videos y recálculo de usuarios están implementados. La invalidación de videos está incluida en el código del paso 7, pendiente de despliegue. El contenedor anterior del paso 6 todavía no incorpora esa ruta. La vista previa continúa sin registrar un video ni guardar su vector.

`GET /ready` con `200` significa que el modelo, ambos diarios de estado, Qdrant y las dos colecciones compatibles están disponibles; incluye `vector_persistence_implemented: true`. Sus dependencias son `model`, `video_state`, `profile_state`, `qdrant` y `collections`. Ante una dependencia ausente o incompatible devuelve `503`. La consulta no crea colecciones ni escribe puntos. El health check de Docker usa `/health` y permite 60 segundos de arranque para cargar las dependencias y el modelo.

Si faltan los pesos o su marcador de revisión no coincide, `/health` permanece disponible, pero `/ready` y la vista previa indican que el modelo no está disponible. Después de preparar los pesos es necesario reiniciar el servicio.

Ejemplo del cuerpo de la vista previa:

```json
{
  "title": "Introducción a Python",
  "description": "Variables, funciones y ejemplos básicos.",
  "category": "Programación",
  "tags": ["python", "principiantes"],
  "language": "es"
}
```

La respuesta incluye `vector`, `dimension`, `embedding_model_version`, `text_template_version`, `metadata_hash`, `input_tokens`, `used_tokens`, `truncated` y `persisted`. No se persiste el resultado.

## Guardado y estado de videos

`POST /embeddings/videos` recibe `contract_version`, el UUID original, `metadata_version` y el objeto `metadata`. El cuerpo completo y los códigos de respuesta están en el [contrato de metadatos](../contrato%20metadatos%20de%20video.md). El paso 7 prepara la copia después del commit mediante una notificación PostgreSQL y un intento HTTP inmediato; `embeddings-dispatcher` recupera pendientes de forma independiente de RabbitMQ. Falta desplegar y verificar esa conexión.

La respuesta `200` confirma el punto en Qdrant y el registro local. Devuelve `GENERATED`, `UPDATED` o `UNCHANGED`, sin el vector completo. Los metadatos normalizados equivalentes evitan repetir inferencia; una solicitud idéntica ya confirmada tampoco repite el upsert. El ID del punto es el UUID original y el payload contiene categoría, idioma declarado, hash, versiones, fecha y datos de recorte.

Se rechazan versiones inferiores a la mayor recibida y contenido distinto para la misma versión. Qdrant también aplica una condición de versión al actualizar el punto; el orden de llegada de paquetes HTTP no puede hacer retroceder un punto que ya tenga una versión superior. Se usa `wait=true`, `ordering=strong` y lectura posterior de confirmación.

`GET /embeddings/videos/{video_id}/status` muestra la versión solicitada, la observada en Qdrant, el último resultado confirmado, el último error y si existe un vector anterior utilizable. Un fallo de conexión devuelve `503`. `404` significa que el servicio no recibió solicitudes para ese UUID, sin determinar su existencia en PostgreSQL.

## Recálculo y estado de usuarios

`POST /embeddings/users/recalculate` recibe UUID, `interaction_version`, `weights_version` y un resumen completo con una entrada por video: reproducciones registradas al comenzar, like activo y cantidad de comentarios. `completed_views` conserva su nombre por compatibilidad, pero cuenta esos inicios acumulados sin límite por video. La ponderación `interaction-weights-v1` usa pesos 1, 3 y 2 respectivamente; el perfil es la media ponderada de vectores compatibles, normalizada L2. El [contrato para Cristian](../contrato%20vector%20de%20usuario.md) contiene ejemplos, validación y política de reintentos.

El Worker controla el umbral de 10 eventos nuevos, la deduplicación y el historial; Embeddings no consulta MongoDB ni consume eventos. La API no exige versiones múltiplos de 10. Cada solicitud reconstruye desde su resumen completo y no acumula sobre el perfil anterior.

`200 READY` usa todos los videos; `200 PARTIAL` utiliza los disponibles e informa los pendientes. Repetir un parcial consulta solo las fuentes pendientes y conserva las ya utilizadas, para no degradarlo por ausencias transitorias. Cada mejora incrementa `profile_revision`; repetir sin mejoras conserva fecha y revisión. Un resumen nuevo vuelve a consultar sus fuentes desde cero. Si no hay ningún vector utilizable, o falla Qdrant, se devuelve `503` y se conserva el punto anterior.

`interactions: []` devuelve `200 NO_PROFILE`: reemplaza el punto con un marcador de versión cuyo `vector` es `{}`, sin vector cero ni sintético. Así se invalida el perfil sin permitir que escrituras antiguas lo restauren. El lector de Recommendation reconoce ese estado como ausencia de perfil. Una versión posterior con contribuciones puede reactivarlo.

Versiones inferiores a la mayor recibida devuelven `409 STALE_INTERACTION_VERSION`; contenido distinto para la misma versión devuelve `409 SNAPSHOT_CONFLICT`. Los reintentos usan una huella canónica ordenada por UUID. El upsert condicional en Qdrant compara versión y revisión, confirma mediante lectura y registra el resultado local antes del `200`. Repetir un `READY` con la misma configuración conserva el resultado sin reconsultar fuentes; la incorporación posterior de metadatos de videos requiere otro resumen o una regeneración coordinada.

`GET /embeddings/users/{user_id}/status` informa versiones solicitada, observada y confirmada, último intento, error y `has_profile`. No modifica el perfil. Un UUID desconocido devuelve `404 PROFILE_NOT_FOUND`; un fallo de conexión devuelve `503`.

## Diario persistente y concurrencia

El volumen `mvp1_embeddings_state` conserva SQLite y los archivos de bloqueo por UUID. Antes de inferir se registra la mayor versión recibida; antes del HTTP de escritura se registra el candidato exacto. Un reintento puede confirmar un punto ya escrito o reenviar el candidato sin volver a inferir. Al confirmar el resultado se retira el candidato temporal del diario.

Los diarios `video_state.sqlite3` y `user_state.sqlite3` están separados, al igual que sus bloqueos, incluso si un usuario y un video comparten UUID. El diario de usuarios conserva el último punto confirmado y, mientras sea parcial, las fuentes ya utilizadas. No guarda eventos originales ni textos de comentarios. Un fallo del diario de perfiles devuelve `503 PROFILE_STATE_UNAVAILABLE`; un bloqueo ocupado devuelve `429 BUSY` con `Retry-After: 1`.

Un fallo de inferencia deja intacto el punto anterior. Un timeout de escritura puede haber ocurrido después de que Qdrant aplicó el cambio: se informa `PENDING_CONFIRMATION` y se recupera mediante reintento. Qdrant y SQLite no comparten una transacción; no se devuelve éxito hasta confirmar ambos pasos. Fallos del diario devuelven `503 STATE_UNAVAILABLE`.

Procesos que comparten el volumen local se excluyen por UUID mediante `filelock`; un bloqueo ocupado devuelve `429 VIDEO_BUSY` con `Retry-After: 1`. El alcance de esta entrega es un host con volumen compartido. Los reinicios y recreaciones de contenedores conservan el volumen y las versiones; el procedimiento de respaldo debe incluir tanto `mvp1_embeddings_state` como `qdrant_data`.

Fuera de Docker, el directorio de estado predeterminado es `artifacts/embeddings/state`; puede configurarse mediante `EMBEDDING_STATE_ROOT`. El diario es propiedad de Embeddings y no modifica tablas de PostgreSQL de Processing. No guarda una copia completa de los textos recibidos.

## Preparación del modelo

Los pesos se guardan en `artifacts/embeddings/models`, ignorado por Git. No se incluyen en la imagen Docker: se montan en `/models` como solo lectura.

Desde la raíz, usando un entorno Python propio:

```powershell
python -m pip install torch==2.14.0 --index-url https://download.pytorch.org/whl/cpu
python -m pip install -r embeddings_service/requirements-evaluation.txt
Set-Location embeddings_service
python -m app.prepare_models --model selected --model-root ../artifacts/embeddings/models
```

La preparación requiere conectividad con Hugging Face y no debe ejecutarse con `HF_HUB_OFFLINE=1`. Descarga revisiones fijas con pesos safetensors y deja un marcador de identidad al completarse. `--model all` prepara ambos candidatos para repetir la comparación.

El servicio en ejecución utiliza `local_files_only=True`, deshabilita código remoto y se configura con `HF_HUB_OFFLINE=1` en Docker. No descarga pesos al recibir solicitudes.

## Integración local para Juan José

Compose proporciona:

| Configuración | Valor |
| --- | --- |
| Servicio de Embeddings | `embeddings-service` |
| URL interna de Embeddings | `http://embeddings-service:8090` |
| URL interna de Qdrant | `http://qdrant:6333` |
| API de Qdrant desde el host | `http://127.0.0.1:6333` por defecto |
| Dashboard de Qdrant | `http://127.0.0.1:6333/dashboard` por defecto |
| Persistencia de Qdrant | Volumen existente `qdrant_data` |
| Timeout del sondeo a Qdrant | `QDRANT_TIMEOUT_SECONDS`, 5 segundos por defecto |
| Modelo seleccionado | `models.json`, clave `minilm` |
| Directorio de modelos en Docker | `EMBEDDING_MODEL_ROOT=/models` |
| Hilos CPU | `EMBEDDING_CPU_THREADS=4` por defecto |
| Colecciones | `VIDEO_COLLECTION=video_embeddings`, `USER_COLLECTION=user_embeddings` |
| Reintento de inicialización | `QDRANT_INIT_RETRY_SECONDS=5` por defecto |
| Diario persistente | Volumen `mvp1_embeddings_state`, montado en `/var/lib/embeddings` |
| Espera para el bloqueo de un video | `VIDEO_LOCK_TIMEOUT_SECONDS=5` por defecto |
| Espera para el bloqueo de un usuario | `USER_LOCK_TIMEOUT_SECONDS=5` por defecto |

Embeddings no publica un puerto al host ni tiene ruta en API Gateway. Solo utiliza la red interna de Compose. Qdrant utiliza además la red `host-access` para que Docker pueda publicar su puerto, restringido a `127.0.0.1`.

Al arrancar, el servicio crea únicamente las colecciones ausentes y valida las existentes. Si Qdrant está temporalmente caído, conserva `/health` y reintenta la inicialización en segundo plano; `/ready` responde `503` hasta que se confirme la configuración. Una incompatibilidad permanente se reporta sin borrar, recrear ni adoptar la colección. No se insertan puntos durante la inicialización.

`EMBEDDING_MODEL_KEY` permite elegir un candidato del manifiesto para evaluación; no debe cambiarse contra colecciones de otro modelo sin una migración coordinada. `EMBEDDING_MODEL_VERSION`, cuando está configurada, debe coincidir con la revisión seleccionada. Recommendation debe usar la misma revisión exacta.

Después de preparar el modelo, para levantar únicamente esta infraestructura desde la raíz del repositorio:

```powershell
docker compose up -d --build --no-deps qdrant embeddings-service
```

Para consultar el estado de Embeddings sin publicar un puerto adicional:

```powershell
docker compose exec -T embeddings-service python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8090/ready').read().decode())"
```

## Inicialización y verificación de Qdrant

El arranque ya inicializa las colecciones. También puede ejecutarse explícitamente dentro del contenedor:

```powershell
docker compose exec -T embeddings-service python -m app.initialize_qdrant
```

Repetirlo conserva las colecciones compatibles y devuelve `action: validated`. El comando no carga el modelo ni escribe puntos. Ante una incompatibilidad termina con código distinto de cero; no la corrige destruyendo datos.

Para comprobar la configuración sin crear nada:

```powershell
docker compose exec -T embeddings-service python -m app.initialize_qdrant --check-only
```

Cada colección almacena en `config.metadata.nexoplay` la revisión del modelo, la plantilla, la normalización, la versión de esquema y su rol. Una colección sin esa identificación tampoco se adopta automáticamente aunque mida 384 dimensiones.

El inicializador revisa primero todas las colecciones existentes. Si detecta una incompatibilidad, no crea las restantes. Si una caída interrumpe la creación a mitad de camino, un reintento conserva lo creado y completa lo pendiente. Dos procesos que intentan crear la misma colección validan el resultado de Qdrant después del conflicto.

Solo el arranque y el comando explícito crean colecciones. `/ready` y `/collections` son de solo lectura. Si se elimina una colección mientras el servicio está funcionando, esas consultas informan el problema; se debe ejecutar el inicializador o reiniciar Embeddings para crearla nuevamente.

En esta máquina se completó el `.env` local con las variables de integración, sin cambiar sus credenciales. El archivo permanece ignorado por Git. En otra instalación se debe copiar y completar `.env.example`; el servicio no reescribe ese archivo automáticamente.

Para ejecutar fuera de Docker, usar las dependencias de inferencia, situarse en esta carpeta y establecer `QDRANT_URL=http://127.0.0.1:6333` antes de iniciar `python -m uvicorn app.main:app --port 8090`. La ruta local de modelos se resuelve desde el repositorio; no depende del directorio de trabajo. La aplicación no carga ni modifica automáticamente el `.env` de la raíz.

## Evaluación y pruebas

Desde esta carpeta, después de preparar ambos modelos:

```powershell
python -m app.benchmark --model-root ../artifacts/embeddings/models --output evaluation/results-cpu.json --threads 4
python -m pytest -p no:cacheprovider -q checks
```

El benchmark ejecuta cada candidato en un proceso separado, mide 60 inferencias después del calentamiento y prueba perfiles contra videos que no fueron utilizados para construirlos. No accede a Qdrant. El conjunto es sintético y pequeño; sus resultados no sustituyen la evaluación con el catálogo real.

Las pruebas de persistencia cubren versiones fuera de orden, conflictos, recuperación de respuestas perdidas, fallos al confirmar el diario, dos instancias sobre el mismo estado y conservación del último vector válido. También se verificó el endpoint contra Qdrant 1.20.0 y la persistencia al recrear el contenedor; los datos sintéticos de esa comprobación se retiraron al finalizar.

Las pruebas de usuarios cubren ponderación con resultado conocido, normalización, retiro de likes, vectores inválidos, reconciliación parcial sin degradación, lotes de 128 videos, resumen vacío, escrituras atrasadas y recuperación tras fallos. La verificación real comprobó esos flujos con vectores generados por el modelo y persistencia al recrear el contenedor. También se verificó el lector real de Recommendation con perfil activo, ausente y `NO_PROFILE`.

- [Decisión del modelo y resultados](../seleccion%20modelo%20embeddings.md).
- [Datos sintéticos](evaluation/catalog.json).
- [Reporte medido en CPU](evaluation/results-cpu.json).

## Contratos para compartir

- Processing: [contrato de metadatos de video](../contrato%20metadatos%20de%20video.md).
- Cristian: [contrato de vector de usuario](../contrato%20vector%20de%20usuario.md).
- Nicolás: [contrato de Qdrant](../contrato%20qdrant.md).

El paso 7 incorpora cambios en Processing, Shared, Gateway y Recommendation que deben revisarse con sus responsables antes del despliegue. Interaction Worker fue incorporado y adaptado al contrato; Frontend ya registra eventos al comenzar la reproducción. La última ejecución de las pruebas Python aprobó 112 casos; las pruebas de outbox usan SQLite aislado, las del Worker usan `mongomock` y HTTP se simula. No equivalen a una verificación completa de MongoDB, RabbitMQ, PostgreSQL, Gateway y Recommendation en ejecución.
