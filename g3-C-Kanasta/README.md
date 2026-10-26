<p align="center">
  <img src="recursos/logo.svg" alt="Kanasta" width="360">
</p>

# Kanasta

**Kanasta** es una tienda en línea de productos variados: tecnología, hogar, ropa, deportes y papelería. Permite a cualquier persona explorar el catálogo, buscar productos, armar un carrito de compras y hacer pedidos con pago contra entrega, sin necesidad de crear una cuenta. El personal de la tienda cuenta con un panel para seguir los pedidos y actualizar su estado.

Kanasta está pensada para pequeños y medianos comercios que quieren vender por internet sin depender de grandes plataformas. Su arquitectura distribuida separa el catálogo, los pedidos y las imágenes en servicios independientes, lo que permite que cada parte crezca y cambie por separado.

## Equipo

**Equipo:** C 

| Integrantes |
|---|
| Rene Alejandro Barrero Gonzalez |
| Cristian Alejandro Sanabria Pinzon |
| Juan Mateo Rozo Torres |
| Daniel Esteban López Guaca|

## Sistema de software

| | |
|---|---|
| **Nombre** | Kanasta |
| **Logo** | <img src="recursos/icono.svg" width="48"> |
| **Lema** | *Todo lo que buscas, en una canasta* |
| **Descripción** | Tienda en línea con catálogo, búsqueda, carrito de compras, pedidos y panel de administración de pedidos. |

## Estructura arquitectónica

### Vista de Componentes y Conectores (C&C)

![Vista C&C](proyecto/documentacion/arquitectura/vista-cc.png)

### Estilos arquitectónicos

- **Cliente-servidor:** toda la comunicación es de tipo petición-respuesta. La web es cliente de los servicios, el servicio de pedidos es cliente del servicio de catálogo y cada servicio es cliente de su base de datos.
- **Por niveles (tiers):** presentación (Kanasta Web), lógica (Catalog Service, Orders Service, Image Provider) y datos (PostgreSQL y MongoDB). Cada nivel solo se comunica con el nivel de abajo.

### Elementos y relaciones

| Componente | Tipo | Tecnología | Responsabilidad |
|---|---|---|---|
| Kanasta Web | Presentación | HTML, CSS, JavaScript, Nginx | Interfaz de la tienda. |
| Catalog Service | Lógica | Python (FastAPI) | Productos, categorías, búsqueda e inventario. |
| Orders Service | Lógica | JavaScript (Node.js, GraphQL) | Carritos y pedidos. |
| Image Provider | Lógica | JavaScript (Node.js) | Imágenes de los productos. |
| Catalog DB | Datos (relacional) | PostgreSQL | Categorías y productos. |
| Orders DB | Datos (NoSQL) | MongoDB | Carritos y pedidos. |

| Conector | Tipo | Relación |
|---|---|---|
| C1 | REST | Kanasta Web → Catalog Service |
| C2 | GraphQL | Kanasta Web → Orders Service |
| C3 | HTTP | Kanasta Web → Image Provider |
| C4 | REST | Orders Service → Catalog Service |
| C5 | Database Connection | Catalog Service → Catalog DB |
| C6 | Database Connection | Orders Service → Orders DB |

La descripción completa está en [descripcion-arquitectura.md](proyecto/documentacion/arquitectura/descripcion-arquitectura.md).

## Cómo ejecutar

### Requisitos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows o macOS) o Docker Engine con Docker Compose (Linux).

### Pasos

1. Abrir una terminal en la carpeta `proyecto/codigo`:

   ```bash
   cd proyecto/codigo
   ```

2. Construir y encender todos los componentes:

   ```bash
   docker compose up --build
   ```

   La primera vez tarda unos minutos porque descarga las imágenes base. Las bases de datos se llenan solas con productos y pedidos de ejemplo.

3. Abrir la tienda en el navegador: **http://localhost:8080**

4. Para apagar todo, pulsar `Ctrl + C` en la terminal o ejecutar:

   ```bash
   docker compose down
   ```

   Para borrar también los datos y empezar desde cero:

   ```bash
   docker compose down -v
   ```

### Direcciones útiles

| Dirección | Qué muestra |
|---|---|
| http://localhost:8080 | La tienda (Kanasta Web) |
| http://localhost:8001/docs | Documentación interactiva de la API REST del catálogo |
| http://localhost:8002/graphql | API GraphQL de pedidos (recibe peticiones POST) |
| http://localhost:8003/images | Lista de imágenes del Image Provider |

## Estructura del proyecto

```
.
├── README.md                     ← este archivo
├── recursos/                     ← logo e ícono
├── proyecto/
│   ├── codigo/
│   │   ├── docker-compose.yml    ← despliegue de todos los componentes
│   │   ├── web/                  ← Kanasta Web (HTML, CSS, JS + Nginx)
│   │   ├── catalog-service/      ← Catalog Service (Python) + datos de Catalog DB
│   │   ├── orders-service/       ← Orders Service (Node.js) + datos de Orders DB
│   │   └── image-provider/       ← Image Provider (Node.js) + imágenes
│   └── documentacion/
│       ├── requisitos-funcionales.md
│       ├── requisitos-no-funcionales.md
│       ├── mapa-historias-usuario.puml / .png
│       ├── arquitectura/         ← vista C&C (.dot / .png) y su descripción
│       ├── manual-de-usuario.md
│       └── capturas/             ← imágenes usadas en el manual
└── extras/                       ← material de apoyo interno
```

## Documentación

- [Requisitos funcionales e historias de usuario](proyecto/documentacion/requisitos-funcionales.md)
- [Requisitos no funcionales](proyecto/documentacion/requisitos-no-funcionales.md)
- [Descripción de la arquitectura](proyecto/documentacion/arquitectura/descripcion-arquitectura.md)
- [Manual de usuario](proyecto/documentacion/manual-de-usuario.md)

Las imágenes de los productos provienen de Wikimedia Commons con licencias libres; los créditos están en [CREDITOS.md](proyecto/codigo/image-provider/images/CREDITOS.md).
