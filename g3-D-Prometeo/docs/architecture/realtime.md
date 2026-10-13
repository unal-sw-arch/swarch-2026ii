# Tiempo real

`RestaurantCoordinator` mantiene las conexiones WebSocket del restaurante y conserva versiones por recurso. Las mutaciones exitosas generan parches pequeños para salones, pedidos, actividad, métricas o catálogo.

Una reconexión compara versiones antes de solicitar datos completos. Un evento ajeno al catálogo no debe invalidar ni volver a descargar el menú. Si el cliente detecta una brecha de versión, solicita únicamente el recurso indicado por el evento de resincronización.
