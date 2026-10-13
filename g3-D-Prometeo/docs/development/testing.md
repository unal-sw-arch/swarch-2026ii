# Verificación

Desde la raíz:

```bash
npm run typecheck
npm run cloud:typecheck
npm test
```

En `apps/operational_client` y `apps/admin_client`:

```bash
flutter pub get
flutter analyze
flutter test
```

Un refactor de carpetas se considera completo únicamente cuando pasan estas comprobaciones y no quedan referencias a rutas antiguas.
