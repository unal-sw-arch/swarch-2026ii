# Interaction Worker: integración con Embeddings

Adaptación de los puntos 1 a 4. El despliegue y las pruebas con MongoDB, RabbitMQ y Qdrant reales quedan para el punto 5; estos cambios no levantan contenedores.

## Solicitud y reglas

- Cada 10 eventos nuevos persistidos desde `profiled_version`, enviar el resumen completo a `POST /embeddings/users/recalculate`.
- Incluir `contract_version: 1.0`, UUID del usuario, `interaction_version`, `weights_version: interaction-weights-v1` e `interactions` ordenadas por UUID.
- Cada entrada contiene `video_id`, `completed_views`, `like_active` y `comments_count`. No se envía `weight`.
- `completed_views` mantiene el nombre de la API, pero cuenta reproducciones registradas al comenzar. Cada nuevo inicio acumula; no se espera al final ni se aplica límite o ventana temporal. El reproductor existente emite una vez por inicio, sin contar segmentos o pausas.
- Cada comentario contribuye. El like vigente aporta una sola contribución; el retiro lo desactiva. Los pesos 1/3/2 pertenecen a Embeddings.
- `event_id` deduplica redeliveries. Eventos nuevos aceptados incrementan la versión; repetir un estado de like no agrega otro peso al vector.
- Se conservan los registros históricos que tenían `counted: false`; no se reescriben automáticamente con las reglas nuevas.

## Persistencia y recuperación

1. `EventApplier` persiste el evento y las proyecciones de lectura de likes/comentarios. Preserva microsegundos y un desempate por UUID para ordenar likes rápidos; las fechas BSON por sí solas solo conservan milisegundos.
2. `note_activity` registra `latest_version` en MongoDB antes del ACK a RabbitMQ. Si falla, la entrega queda sin confirmar y se repite sin duplicar el evento.
3. Un hilo separado ejecuta el barrido de perfiles. La inferencia HTTP no bloquea el consumidor ni sus heartbeats.
4. Un lease con token serializa solicitudes del mismo usuario entre Workers. Todas las confirmaciones requieren el token vigente; una respuesta de un propietario vencido no modifica el estado de otro.
5. Una agregación `$facet` obtiene versión, vistas, comentarios y likes de la misma entrada del log. No combina contadores de consultas independientes ni depende de proyecciones que podrían estar actualizándose.
6. Antes de HTTP se persiste `pending_request`. Tras un fallo, reinicio o respuesta perdida, se reutiliza exactamente, aunque hayan llegado más eventos. `profiled_version` avanza solo después de validar el resultado y confirmar la escritura MongoDB.

El estado vive en `user_interaction_state`, dentro del volumen existente de MongoDB. Las escrituras solicitan journal. No se necesita habilitar replica set para una transacción entre colecciones.

## Respuestas

- `READY`: confirmar versión y retirar la tarea.
- `PARTIAL`: confirmar versión y guardar `reconciliation_request`, respuesta e IDs pendientes. Repetir el mismo resumen sin esperar diez eventos nuevos. Si hay otros diez eventos, preparar una versión nueva con todo el historial después de resolver cualquier escritura incierta.
- `NO_PROFILE`: confirmar el resumen vacío y retirar la tarea. Recommendation usará su respaldo.
- Red, `408`, `429` y `5xx`: conservar candidato y checkpoint; reintentar con esperas 2, 5, 15, 30 y 60 segundos, respetando un `Retry-After` mayor.
- `422` y `409`: conservar `blocked_request`, `blocked_version` y `last_error` para revisión, sin repetir indefinidamente. Una versión superior al bloqueo puede iniciar otro intento. Nunca usar `current_interaction_version` de un error como confirmación exitosa.
- Un `200` con identidad, versión, contadores, estado o revisión inválidos se trata como confirmación incierta y no mueve el checkpoint.

## Configuración

Compose pasa la URL interna, el umbral 10 y los timeouts definidos en `.env.example`: conexión 5 s, respuesta 60 s, lease 120 s y barrido 1 s. El lease debe superar conexión + respuesta por al menos 10 s. El cierre dispone de 75 s para terminar una solicitud en curso.

`VIEW_DEDUP_MINUTES`, `MAX_VIEWS_PER_VIDEO`, `WEIGHT_*` y `EMBEDDINGS_TIMEOUT_SECONDS` dejan de utilizarse. El Worker usa el cliente HTTP compartido y los timeouts `EMBEDDINGS_CONNECT_TIMEOUT_SECONDS` / `EMBEDDINGS_RESPONSE_TIMEOUT_SECONDS`.

## Pruebas locales

Instalar `requirements-dev.txt` en un entorno propio y ejecutar desde la raíz, incluyendo raíz y `embeddings_service` en `PYTHONPATH`:

```powershell
$env:PYTHONPATH="$PWD;$PWD\embeddings_service"
python -m pytest -p no:cacheprovider -q interaction_worker/checks embeddings_service/checks processing_service/checks
```

Las pruebas usan MongoDB simulado con `mongomock`, HTTP simulado y el DTO real de la API. Cubren umbral, acumulación, comentarios, orden de likes, contrato, snapshots persistentes, reinicios, errores, perfiles parciales, ACK y leases. Las garantías reales de MongoDB, RabbitMQ y el recorrido completo deben verificarse en el punto 5.
