# RestauranteApp

Sistema de operación y administración de restaurante. La aplicación usa una API desplegada en Cloudflare Workers, D1 como base de datos y un Durable Object para distribuir cambios en tiempo real.

## Estructura

```text
apps/
  cloud_api/             Worker, reglas del servidor y tiempo real
  operational_client/   Flutter para meseros, caja y operación del salón
  admin_client/         Flutter para configuración, métricas e historial
database/
  d1/migrations/        Esquema versionado de D1
  d1/seeds/             Datos de desarrollo
scripts/
  catalog/              Importadores de catálogos hacia D1
docs/
  architecture/         Diseño vigente del sistema
  development/          Reglas y flujos para contribuir
```

Los directorios nativos de Flutter (`android`, `ios`, `linux`, `macos`, `web` y `windows`) contienen integración de plataforma y, en gran parte, archivos generados. El código de producto vive en `lib`.

## Comandos principales

```bash
npm install
npm run cloud:typecheck
npm test
npm run cloud:dev
```

Para cada cliente Flutter:

```bash
flutter pub get
flutter analyze
flutter test
```

Consulta [la arquitectura](docs/architecture/system.md) y [las reglas de contribución](CONTRIBUTING.md) antes de añadir una funcionalidad.
