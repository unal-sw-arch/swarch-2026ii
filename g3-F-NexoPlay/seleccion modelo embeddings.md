# Selección del modelo de embeddings - MVP2

## Decisión inicial

Se selecciona `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` como referencia inicial del proyecto, tras compararlo con `intfloat/multilingual-e5-small` usando metadatos sintéticos en esta máquina.

| Parámetro | Configuración |
| --- | --- |
| Revisión inmutable | `e8f8c211226b894fcb81acc59f3b34ba3efd5f42` |
| `embedding_model_version` | `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2@e8f8c211226b894fcb81acc59f3b34ba3efd5f42` |
| Dimensión | 384 |
| Máximo de tokens | 128, incluyendo tokens especiales |
| Prefijo adicional | Ninguno |
| Plantilla | `video-text-v1` |
| Normalización | L2, con rechazo de vectores nulos o no finitos |
| Métrica prevista en Qdrant | `Cosine` |
| Ejecución | CPU, 4 hilos por defecto |

El manifiesto operativo está en [models.json](embeddings_service/models.json). El modelo publicado convierte oraciones y párrafos en vectores de 384 dimensiones; su configuración declara una longitud máxima de 128 tokens. [Ficha oficial de MiniLM](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2).

La selección permitió cerrar la configuración que necesita Nicolás. La inicialización de colecciones ya está disponible y el paso 5 incorpora la persistencia de vectores de videos.

## Construcción del texto

La plantilla incluye, en este orden, los campos presentes:

```text
Título: Introducción a Python
Categoría: Programación
Etiquetas: principiantes, python
Descripción: Variables y funciones mediante ejemplos básicos.
```

- Se normaliza Unicode a NFC, se eliminan espacios sobrantes y se colapsan saltos internos y espacios repetidos.
- El título es obligatorio; campos opcionales vacíos se omiten.
- Las etiquetas se convierten a minúsculas, se deduplican y se ordenan para producir una entrada estable.
- Título, categoría y descripción conservan sus mayúsculas y acentos.
- El idioma declarado se conserva en los metadatos normalizados y su hash; no se añade como una frase al texto ni se detecta automáticamente.
- Se calcula SHA-256 sobre los metadatos canónicos y la versión de plantilla. Cambiar el orden de etiquetas o espacios equivalentes no cambia el hash.
- Cambiar esta construcción o un prefijo del modelo requiere versionar la plantilla y coordinar una regeneración.

## Textos extensos

El tokenizador recorta por la derecha a 128 tokens. El orden prioriza los campos cortos antes de la descripción, aunque una cabecera muy extensa también puede agotar el presupuesto.

Cada resultado informa `input_tokens`, `used_tokens` y `truncated`. Esta versión no resume textos, no divide descripciones en fragmentos y no promete conservar la información situada al final de una descripción larga.

En la prueba extensa, MiniLM recibió 1627 tokens y utilizó 128; E5 recibió 1630 y utilizó 512. Las diferencias de conteo incluyen el prefijo de E5. Ninguno de los 20 videos del catálogo de prueba necesitó recorte.

## Evaluación realizada

Máquina: Windows, aproximadamente 31,43 GiB de RAM y 24 procesadores lógicos. Cada modelo se ejecutó en un proceso independiente, en CPU y con 4 hilos. La instalación local de PyTorch incluye CUDA, pero esta evaluación fuerza `device=cpu`; sus cifras de memoria incluyen esas librerías cargadas.

Datos: 20 videos sintéticos distribuidos entre programación, ciclismo, cocina y piano; 8 consultas, 8 comparaciones de pares y 4 perfiles. Las etiquetas de grupo se usan para evaluar y no se pasan al modelo. Los perfiles se construyen con dos videos, con pesos 1 y 3, y se evalúan contra los otros videos, excluyendo los dos utilizados.

| Medida | MiniLM | E5 Small |
| --- | ---: | ---: |
| Precisión media de perfiles en los primeros 3 resultados | 1,0000 | 0,9167 |
| MRR de consultas en los primeros 5 resultados | 1,0000 | 1,0000 |
| Aciertos en comparaciones relacionado/no relacionado | 8/8 | 8/8 |
| Inferencia individual media, 60 muestras | 25,781 ms | 31,739 ms |
| Inferencia individual p95 | 37,851 ms | 40,964 ms |
| Pico aproximado de RSS del proceso | 1809,84 MiB | 1627,15 MiB |
| Carga inicial, incluyendo importaciones | 40,756 s | 13,409 s |

La carga inicial depende también de importaciones y cachés del sistema: no se interpreta como una comparación rigurosa de arranque en frío. El RSS se muestrea cada 20 ms e incluye dependencias y pesos; no equivale al consumo exclusivo del modelo ni al consumo final de Docker.

Se elige MiniLM por sus resultados de perfiles y su menor latencia de inferencia en esta prueba. E5 consume menos RSS en esta ejecución y permite entradas más largas; se conserva como candidato para evaluar con metadatos reales si el recorte afecta la relevancia.

La prueba es pequeña, sintética y con temas fáciles de separar. No demuestra superioridad general de MiniLM ni permite estimar la calidad del catálogo real. No se ajustaron los pesos de los modelos ni se realizó entrenamiento.

El candidato E5 se evaluó con `query: ` en todos los textos, siguiendo su recomendación para similitud semántica simétrica. No se mezclaron vectores de candidatos distintos. [Ficha y reglas oficiales de E5](https://huggingface.co/intfloat/multilingual-e5-small).

Archivos reproducibles:

- [Datos sintéticos](embeddings_service/evaluation/catalog.json).
- [Resultados medidos y entorno](embeddings_service/evaluation/results-cpu.json).
- [Comparador de modelos](embeddings_service/app/benchmark.py).
- [Preparación de pesos con revisión fija](embeddings_service/app/prepare_models.py).

## Carga del modelo y siguiente etapa

El modelo se prepara explícitamente antes de iniciar el servicio y se carga una vez por proceso desde un montaje de solo lectura. El servicio no descarga pesos al recibir solicitudes. Se fija la revisión y se deshabilita código remoto. Sentence Transformers admite CPU, revisión fija y carga desde archivos locales. [Documentación oficial](https://sbert.net/docs/package_reference/sentence_transformer/model.html).

`POST /embeddings/preview` permite generar el vector en memoria y devuelve `persisted: false`. No registra un UUID ni escribe en Qdrant. `POST /embeddings/videos` ya guarda el punto por UUID con control persistente de versiones y `GET /embeddings/videos/{video_id}/status` consulta su estado. `POST /embeddings/users/recalculate` construye perfiles a partir del resumen completo de interacciones y `GET /embeddings/users/{user_id}/status` consulta su estado.

Las colecciones `video_embeddings` y `user_embeddings` utilizan dimensión 384, métrica `Cosine` y el esquema del [contrato Qdrant](contrato%20qdrant.md). El perfil de usuario ya se calcula como una combinación ponderada normalizada de los vectores de videos; no requiere un segundo modelo de texto. La integración automática con los productores queda para la siguiente etapa.
