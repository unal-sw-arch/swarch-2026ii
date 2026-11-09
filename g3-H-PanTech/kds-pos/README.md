# KDS/POS

Aplicación de escritorio (Flutter/Dart) para cocina y despacho de Panadería Express. **Siempre llama al API Gateway** (`/auth/*` y `/queue/*`), nunca a Kitchen directo, y no forma parte del `docker-compose.p2.yml`.

## Qué hace

| Pantalla | Quién | Qué permite |
|---|---|---|
| Inicio de sesión | Todos | Correo y contraseña. Solo entran `cocinero`, `despachador`, `supervisor` o `admin` (según los roles del JWT); con otro rol no se guarda la sesión |
| Cocina | `cocinero`, `supervisor`, `admin` | Cola en orden de prioridad con posición, espera, ETA y capacidad (`En preparación 2/4`). Botones *En preparación* y *Listo* (requieren `kitchen:update`) |
| Despacho | `despachador`, `supervisor`, `admin` | Pedidos listos separados en *Para llevar* y *En mesa*, con el botón *Entregar* (requiere `kitchen:deliver`) |

El supervisor y el admin ven las dos pestañas; el cocinero y el despachador, solo la suya. El KDS oculta lo que la cuenta no puede hacer, pero la decisión real la toman el Gateway y Kitchen.

La cola se actualiza por **polling cada 4 s** (`GET /queue`), sin solapar consultas. Si cae la red se conservan los últimos datos y aparece un aviso con *Reintentar*.

## Requisitos

- Flutter 3.47 / Dart 3.13 (`flutter --version`).
- Windows 10 u 11 con el **Modo de desarrollador** activado (`start ms-settings:developers`): Flutter lo exige para resolver los plugins (`flutter pub get` falla sin él).
- Para compilar el ejecutable de Windows: **Visual Studio con la carga "Desktop development with C++"** (`flutter doctor` debe mostrar Visual Studio en verde).
- **Después de activar el Modo de desarrollador, ejecute una vez `flutter pub get` y haga commit de lo que regenere** (`windows/flutter/generated_plugins.cmake`, `generated_plugin_registrant.cc` y `.h`). Los que están en el repositorio se generaron sin poder resolver plugins y **no registran `flutter_secure_storage_windows`**: un `.exe` compilado con ellos fallaría al guardar la sesión.
- Un API Gateway accesible (por defecto `http://localhost:8080`).

## Configuración (sin URLs en el código)

La URL del Gateway se resuelve en este orden:

1. `--dart-define=GATEWAY_URL=http://host:8080`
2. Archivo `kds_config.json` junto al `.exe`:
   ```json
   {"gateway_url": "http://host:8080", "poll_seconds": 4}
   ```
3. `http://localhost:8080`, el puerto publicado del Gateway según `docs/equipo/contratos.md` (solo para desarrollo).

`--dart-define=POLL_SECONDS=4` (o `poll_seconds`) cambia el intervalo; se acota entre 2 y 30 s. Una URL que no sea `http(s)` muestra un error de configuración.

## Ejecutar

```powershell
cd kds-pos
flutter pub get
flutter run -d windows --dart-define=GATEWAY_URL=http://localhost:8080
```

**Respaldo para la demo (sin Visual Studio): Chrome.**

```powershell
flutter run -d chrome --web-port=5000 --dart-define=GATEWAY_URL=http://localhost:8080
```

El navegador sí aplica CORS: agregue `http://localhost:5000` a `CORS_ORIGINS` del Gateway (en `.env.p2`, separado por comas) y reinicie el Gateway. En web, `flutter_secure_storage` guarda la sesión cifrada por el navegador, no en el almacenamiento seguro del sistema: úselo solo para demostraciones.

## Compilar

```powershell
flutter build windows --release --dart-define=GATEWAY_URL=http://gateway.panaderia.local:8080
```

El ejecutable queda en `build\windows\x64\runner\Release\`. Para no recompilar al cambiar de servidor, copie `kds_config.json` junto al `.exe`.

## Pruebas

```powershell
flutter analyze
flutter test
```

53 pruebas (unitarias y de widgets) con un Gateway falso (`test/support.dart`): configuración, modelos, sesión y roles, cliente HTTP, y las pantallas de login, cocina y despacho. No necesitan el stack de Docker.

Para probar contra el sistema real, el Gateway necesita un Identity que emita los JWT; mientras no exista, `kitchen-service/tests/smoke` firma tokens de prueba (ver el README de Kitchen).

## Errores y mensajes

| Respuesta | Qué ve la persona |
|---|---|
| `401` | Intenta renovar con el `refresh_token`; si no puede, vuelve al login con "Su sesión expiró" |
| `403` | "Su cuenta no tiene permiso para esta operación." (en la cola, en un aviso con *Reintentar*) |
| `404` | "El pedido ya no existe en la cola." y se actualiza la cola |
| `409 INVALID_STATE` | "El pedido cambió de estado (ahora está "listo"). Se actualizó la cola." |
| `409 KITCHEN_AT_CAPACITY` | "La cocina está llena (4 de 4 en preparación). El pedido sigue pendiente; termine uno e inténtelo de nuevo." |
| `429` | "Demasiadas consultas seguidas. Espere unos segundos." |
| `502`/`503`/`504` | "El servicio no está disponible. Reintente en unos segundos." |
| Sin red o sin respuesta en 8 s | Aviso "Sin conexión con el Gateway" con los últimos datos y *Reintentar* |

## Supuestos sobre Identity (por verificar)

El cuerpo de `POST /auth/login` no está definido en ningún documento. El KDS asume:

- Petición: `{"email", "password"}`.
- Respuesta: `{"access_token", "refresh_token", "token_type", "expires_in"}`.
- `POST /auth/refresh`: petición `{"refresh_token"}`, misma forma de respuesta.
- Los roles y permisos se leen de los claims `roles` y `permissions` del JWT (sin verificar la firma: la verifica el Gateway).

Todo está aislado en dos lugares: `LoginResponse.fromJson` y las dos peticiones de `login`/`_doRefresh` en `lib/api/gateway_client.dart`, y la lectura de claims en `Session.fromTokens` de `lib/auth/session_store.dart`. Si Identity responde otra forma, solo se cambia ahí.

## Límites conocidos

- Sin WebSocket: el polling de 4 s es lo acordado para el prototipo.
- *Entregar* no pide confirmación.
- No imprime comandas ni emite sonido al llegar un pedido.
- No se pudo compilar para Windows en el equipo de desarrollo (falta Visual Studio con C++); sí se compiló para web y se ejecutaron `flutter analyze` y `flutter test`.

## Estructura

```text
lib/main.dart                       arranque y configuración
lib/app.dart                        tema, navegación y pantalla principal
lib/config.dart                     URL del Gateway e intervalo de polling
lib/api/gateway_client.dart         cliente HTTP, errores traducidos y renovación de sesión
lib/auth/session_store.dart         sesión, roles y almacenamiento seguro del token
lib/auth/session_controller.dart    login, restauración y cierre de sesión
lib/auth/login_page.dart            pantalla de inicio de sesión
lib/kitchen/queue_controller.dart   polling y acciones
lib/kitchen/queue_page.dart         cola de cocina
lib/kitchen/order_card.dart         tarjeta de comanda
lib/kitchen/status_buttons.dart     botones En preparación y Listo
lib/dispatch/dispatch_page.dart     pedidos listos y Entregar
lib/models/queue_item.dart          modelos de la cola
lib/widgets/status_banner.dart      aviso de error con reintento
test/                               pruebas y Gateway falso
```
