# Requisitos no funcionales

Los requisitos no funcionales describen **cómo** debe ser el sistema (su forma, su calidad y sus restricciones), no lo que hace.

## Requisitos de arquitectura

Son obligatorios para el prototipo 2. **Todos se cumplen.**

| ID | Requisito | Cómo se cumple en Kanasta | Estado |
|---|---|---|---|
| RNF-01 | El sistema debe seguir una **arquitectura distribuida**. | Kanasta tiene 13 componentes que corren en 13 contenedores separados y se comunican por la red (HTTP, AMQP y conexiones a bases de datos). | ✅ |
| RNF-02 | Debe tener al menos **dos componentes de presentación distintos**, uno de ellos un **front-end web**. | **Kanasta Web** (página web para los clientes, Node.js) y **Kanasta Admin** (aplicación de escritorio para el administrador, Python con PyQt5). | ✅ |
| RNF-03 | El front-end web debe seguir una subarquitectura **SSR (Server-Side Rendering)**. | Kanasta Web arma cada página **en el servidor**: pide los datos al API Gateway, llena una plantilla EJS y envía al navegador el HTML terminado. El navegador no ejecuta JavaScript de la aplicación; los formularios se envían con POST y el servidor responde con la página siguiente. | ✅ |
| RNF-04 | Debe tener al menos **cuatro componentes de lógica**. | **Catalog Service** (Python), **Orders Service** (JavaScript), **Accounts Service** (Java), **Notifications Service** (Go) e **Image Provider** (JavaScript). Son cinco. | ✅ |
| RNF-05 | Debe tener al menos **un componente de comunicación u orquestación** entre los componentes lógicos. | **API Gateway** (Go): es la única puerta de entrada; para cada petición pregunta al Accounts Service quién es el usuario, revisa los permisos y la envía al servicio que corresponde. Además, el **Message Broker** (RabbitMQ) comunica a los servicios entre sí de forma asíncrona. | ✅ |
| RNF-06 | Debe tener al menos **cuatro componentes de datos**, incluyendo bases **relacionales y NoSQL**. | **Catalog DB** y **Accounts DB** (PostgreSQL, relacionales); **Orders DB** y **Notifications DB** (MongoDB, NoSQL de documentos). Cada una pertenece a un solo servicio. | ✅ |
| RNF-07 | Debe tener al menos **un componente encargado de los procesos asíncronos**. | **Notifications Service** junto con el **Message Broker**: Accounts y Orders dejan un mensaje en la cola `notifications` y siguen trabajando; el Notifications Service lee la cola y envía los correos con Resend sin hacer esperar al cliente. | ✅ |
| RNF-08 | Debe usar un **conjunto de conectores basados en HTTP**. | **REST** (front-ends → API Gateway → Catalog, Accounts y Notifications; Orders → Catalog; Notifications → Catalog), **GraphQL** (front-ends → API Gateway → Orders), **HTTP** para páginas e imágenes (navegador → Kanasta Web; front-ends → API Gateway → Image Provider) y **REST sobre HTTPS** hacia Resend. | ✅ |
| RNF-09 | Debe construirse con al menos **cuatro lenguajes de programación** de propósito general. | **Python** (Catalog Service, Kanasta Admin), **JavaScript** (Kanasta Web, Orders Service, Image Provider), **Java** (Accounts Service) y **Go** (API Gateway, Notifications Service). | ✅ |
| RNF-10 | El despliegue debe estar **orientado a contenedores**. | Cada componente tiene su imagen de Docker y todo se enciende con `docker compose up --build`. Incluso la aplicación de escritorio corre en un contenedor y se ve desde el navegador (noVNC). | ✅ |

## Requisitos de calidad

| ID | Atributo | Requisito | Cómo se cumple | Estado |
|---|---|---|---|---|
| RNF-11 | Seguridad | Solo el administrador puede manejar pedidos, productos, cuentas, datos de la tienda y correo. | El API Gateway revisa el rol de cada sesión antes de dejar pasar la petición; el Orders Service revisa el rol en cada operación de GraphQL. La web no deja ingresar cuentas de administrador. | ✅ |
| RNF-12 | Seguridad | Las contraseñas no se deben guardar en texto plano. | El Accounts Service guarda solo el resumen **BCrypt** de cada contraseña. | ✅ |
| RNF-13 | Seguridad | La clave de la cuenta de correo no se debe mostrar una vez guardada. | El Notifications Service solo devuelve una pista (`re_...abcd`). | ✅ |
| RNF-14 | Seguridad | Las bases de datos y los servicios no deben estar expuestos fuera del servidor. | Hay dos redes de Docker: `frontend` y `backend`. Solo el API Gateway está en las dos; los servicios y las bases solo están en `backend` y no publican puertos. | ✅ |
| RNF-15 | Seguridad | Los textos escritos por los usuarios no deben poder ejecutar código en la página. | Las plantillas EJS escapan todo el texto (`<%= %>`) y las consultas SQL usan parámetros. | ✅ |
| RNF-16 | Integridad de datos | No se puede vender más cantidad de la que hay en inventario. | El Catalog Service descuenta solo si alcanza (`stock >= cantidad`) y la base no acepta inventario negativo. Si una parte del pedido falla, se devuelve lo que ya se había reservado. | ✅ |
| RNF-17 | Disponibilidad | Si el envío de correos falla o el broker se cae, los pedidos y las cuentas deben seguir funcionando. | El envío es asíncrono: el pedido se guarda primero y el correo se procesa después. Si Resend falla, el error queda en el historial de correos. | ✅ |
| RNF-18 | Disponibilidad parcial | Si falta una imagen, la tienda debe seguir funcionando. | El Image Provider responde con una imagen de reemplazo ("Imagen no disponible"). | ✅ |
| RNF-19 | Facilidad de uso | Un cliente nuevo debe poder crear su cuenta y hacer un pedido sin ayuda en menos de 5 minutos. | Menú simple, mensajes claros en español, datos de entrega ya escritos con los de la cuenta. | ✅ |
| RNF-20 | Adaptabilidad | La página debe verse bien en celular y en computador. | Diseño adaptable (CSS responsive). | ✅ |
| RNF-21 | Compatibilidad | La página debe funcionar aun en celulares sencillos o con JavaScript desactivado. | Al ser SSR, el navegador solo recibe HTML y CSS; todo funciona con enlaces y formularios normales. | ✅ |
| RNF-22 | Mantenibilidad | Cada componente debe poder cambiarse sin modificar los demás, mientras respete su interfaz. | Cada servicio es dueño de su base de datos y solo se comunica por su API o por mensajes. | ✅ |
| RNF-23 | Portabilidad e instalación | La tienda debe poder instalar Kanasta en su propio computador (Windows, macOS o Linux) con un solo comando. | Solo se necesita Docker; no hay que instalar Python, Node.js, Java, Go ni bases de datos. | ✅ |
| RNF-24 | Transparencia de precios | El cliente debe saber cuándo el valor final puede cambiar. | Los productos por peso o volumen muestran un aviso en el catálogo, el detalle, el carrito, la confirmación y el recibo. | ✅ |
| RNF-25 | Idioma | Toda la interfaz y los correos deben estar en español, con precios en pesos colombianos. | Textos en español y precios con formato `$ 12.500`. | ✅ |
