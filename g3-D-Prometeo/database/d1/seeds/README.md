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

Los usuarios no administradores quedan asignados a los cuatro salones (`9001` Salón Principal, `9002` Terraza, `9003` Salón Familiar, `9004` Barra). También se conservan `cloud.admin` y `cloud.waiter` del fixture original.

## Catálogo de ejemplo

`development.sql` carga un restaurante de ejemplo para desarrollo:

- **4 salones** con entre 5 y 10 mesas cada uno (`P-*`, `T-*`, `F-*`, `B-*`).
- **Menú Principal** (primario en los cuatro salones) con categorías: Hamburguesas, Salchipapas, Papas, Bebidas y Adiciones (especial).
- **16 productos** (15 activos + 1 inactivo para pruebas de visibilidad), con sus ingredientes. Precios en pesos enteros.

> Solo para desarrollo local. No usar estas credenciales en entornos reales.
