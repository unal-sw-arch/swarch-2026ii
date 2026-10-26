# Reglas de organización

## Límites del sistema

- `apps/cloud_api` es el único backend. No se debe crear un servidor Node o una base SQLite paralela.
- D1 se modifica únicamente mediante migraciones nuevas en `database/d1/migrations`; una migración aplicada no se reescribe.
- Los clientes consumen HTTP para lecturas y comandos, y WebSocket para parches en tiempo real.
- El código de negocio que solo necesita el Worker vive dentro de `apps/cloud_api/domain` o de la feature correspondiente.
- Un cliente Flutter no debe importar archivos del otro salvo la dependencia explícita del cliente administrativo al operativo para reutilizar la vista en vivo.

## Carpetas y archivos

- Una funcionalidad nueva se crea dentro de `features/<nombre>`.
- Cada carpeta de feature debe tener un `README.md` breve que explique su alcance y sus puntos de entrada.
- Los archivos de UI coordinadores deben limitarse a montar estado y composición. Diálogos, lienzos, flujos y widgets grandes van en archivos separados.
- Como guía, al superar unas 500–700 líneas se debe evaluar una separación por responsabilidad. No se divide un archivo solo para cumplir una cifra.
- Los nombres de archivo usan `snake_case`; los nombres de carpetas describen capacidades, no dispositivos antiguos.
- No se conservan aliases, parsers o dependencias de arquitecturas retiradas “por si acaso”. Git es el historial.

## Cambios obligatorios

1. Actualizar el `README.md` de la carpeta afectada si cambia su responsabilidad.
2. Añadir una migración cuando cambie D1.
3. Añadir o actualizar pruebas cerca del comportamiento modificado.
4. Ejecutar typecheck/análisis y todas las pruebas relevantes antes de integrar.
