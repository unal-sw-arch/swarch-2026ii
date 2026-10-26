# Seeds

Datos mínimos para entornos de desarrollo. Los catálogos reales se cargan con los scripts de `scripts/catalog`.

## Usuarios de prueba

`development.sql` crea usuarios con contraseñas conocidas para probar la aplicación en local. Los hashes se generan con el mismo scrypt del backend (`apps/cloud_api/domain/security/password.ts`).

| Rol      | Usuario        | Contraseña        |
| -------- | -------------- | ----------------- |
| admin    | `admin.test`   | `AdminTest1234`   |
| waiter   | `mesero.test`  | `MeseroTest1234`  |
| kitchen  | `cocina.test`  | `CocinaTest1234`  |
| cashier  | `caja.test`    | `CajaTest123456`  |
| manager  | `gerente.test` | `GerenteTest1234` |

Los usuarios no administradores quedan asignados al salón de prueba `9001`. También se conservan `cloud.admin` y `cloud.waiter` del fixture original.

> Solo para desarrollo local. No usar estas credenciales en entornos reales.
