// Content of the tour: views, elements and steps

const VIEWS = {
  cc: "Componentes y conectores",
  despliegue: "Despliegue",
  capas: "Capas",
  descomposicion: "Descomposición",
};

const ALL_COMPONENTS = ["browser", "web", "admin", "gateway", "catalog", "orders", "accounts", "notifications",
  "images", "broker", "resend", "catalogdb", "accountsdb", "ordersdb", "notificationsdb"];
const LOGIC = ["catalog", "orders", "accounts", "notifications", "images"];
const DATA = ["catalogdb", "accountsdb", "ordersdb", "notificationsdb"];

// Short description shown when an element of a diagram is clicked
const ELEMENTS = {
  browser: ["Navegador del cliente", "Celular o computador del cliente. Solo recibe HTML y CSS ya armados por Kanasta Web."],
  adminbrowser: ["Navegador del administrador", "Muestra Kanasta Admin a través de noVNC (puerto 6080)."],
  adminnative: ["Python + PyQt5", "Opción para instalar Kanasta Admin directamente en el computador del administrador."],
  web: ["Kanasta Web", "Front-end web SSR (Node.js, Express y EJS). Pide los datos al API Gateway, arma el HTML en el servidor y lo envía al navegador."],
  admin: ["Kanasta Admin", "Aplicación de escritorio en Python con PyQt5. Pedidos, productos, categorías, clientes, datos de la tienda, correo y cuenta propia."],
  gateway: ["API Gateway", "Escrito en Go. Única puerta de entrada: valida la sesión con el Accounts Service, revisa permisos y envía cada petición a su servicio."],
  catalog: ["Catalog Service", "Python (FastAPI). Productos por unidad, kg o litro; inventario, categorías, opiniones y datos de la tienda."],
  orders: ["Orders Service", "JavaScript (Node.js) con GraphQL. Carritos y pedidos; reserva inventario y publica el evento del recibo."],
  accounts: ["Accounts Service", "Java (Spring Boot). Cuentas, contraseñas con BCrypt, sesiones y roles. Publica eventos de cuenta creada y cambio de contraseña."],
  notifications: ["Notifications Service", "Go. Lee la cola del broker y envía los correos con Resend. Guarda la configuración del correo y el historial."],
  images: ["Image Provider", "JavaScript (Node.js). Entrega y recibe las imágenes de los productos; si falta una, responde con una de reemplazo."],
  broker: ["Message Broker", "RabbitMQ. Guarda los eventos en la cola «notifications» hasta que el Notifications Service los procesa."],
  resend: ["Resend", "Servicio externo de correo en Internet (API REST sobre HTTPS)."],
  catalogdb: ["Catalog DB", "PostgreSQL (relacional): tienda, categorías, productos y opiniones."],
  accountsdb: ["Accounts DB", "PostgreSQL (relacional): usuarios y sesiones."],
  ordersdb: ["Orders DB", "MongoDB (NoSQL de documentos): carritos y pedidos."],
  notificationsdb: ["Notifications DB", "MongoDB (NoSQL de documentos): configuración del correo e historial de envíos."],
  imagefiles: ["Archivos de imágenes", "Volumen images-data del Image Provider."],
  Kanasta: ["Kanasta", "El sistema completo."],
};

const STEPS = [
  {
    group: "Inicio",
    num: "",
    title: "Kanasta en un minuto",
    view: "cc",
    nodes: [],
    html: `
      <p class="kicker">Inicio</p>
      <h1>Una tienda de barrio, vendiendo a domicilio</h1>
      <p>Kanasta es un software que una tienda de barrio (frutas, verduras, carnes y mercado) instala en su propio computador.
         El administrador configura la tienda y el catálogo desde una <b>aplicación de escritorio</b>, y los vecinos compran
         desde una <b>página web</b>. La tienda lleva el pedido a la casa y el cliente paga al recibirlo.</p>
      <ul>
        <li>Frutas, verduras, carnes y líquidos se venden <b>por kg o por litro</b>, con un aviso de que el valor final puede variar un poco.</li>
        <li>Los clientes reciben correos al crear su cuenta, al cambiar su contraseña y al comprar (con el recibo).</li>
        <li>Las 22 historias de usuario están implementadas.</li>
      </ul>
      <h2>Cómo usar este recorrido</h2>
      <p>Cada paso explica un requisito no funcional del prototipo 2 y cómo lo cumple Kanasta. A la derecha, el diagrama
         correspondiente <b>resalta</b> los elementos que intervienen. Puedes cambiar de vista con las pestañas, hacer clic
         en cualquier elemento o moverte con las flechas del teclado.</p>
      <p class="verdict">✔ 10 de 10 requisitos de arquitectura cumplidos</p>`,
  },
  {
    group: "Requisitos",
    num: "RNF-01",
    title: "Arquitectura distribuida",
    view: "despliegue",
    nodes: ["web", "admin", "gateway", "catalog", "orders", "accounts", "notifications", "images", "broker", ...DATA],
    html: `
      <p class="kicker">RNF-01</p>
      <h1>Arquitectura distribuida</h1>
      <p class="requirement">El sistema debe seguir una arquitectura distribuida.</p>
      <p>Kanasta está formado por <b>13 componentes que corren en 13 procesos separados</b> (13 contenedores) y que solo
         se hablan <b>por la red</b>: HTTP entre front-ends, gateway y servicios; AMQP con el broker; y el protocolo de cada
         base de datos.</p>
      <table>
        <tr><th>Red de Docker</th><th>Quién está</th></tr>
        <tr><td><code>frontend</code></td><td>Kanasta Web, Kanasta Admin, API Gateway</td></tr>
        <tr><td><code>backend</code></td><td>API Gateway, 5 servicios, Message Broker, 4 bases de datos</td></tr>
      </table>
      <p>Ningún componente comparte memoria ni archivos con otro: si uno se detiene, los demás siguen corriendo.
         Por ejemplo, si el Notifications Service se apaga, los pedidos se siguen recibiendo y los correos esperan en la cola.</p>
      <p class="verdict">✔ Cumple</p>`,
  },
  {
    group: "Requisitos",
    num: "RNF-02",
    title: "Dos componentes de presentación",
    view: "cc",
    nodes: ["browser", "web", "admin", "gateway"],
    html: `
      <p class="kicker">RNF-02</p>
      <h1>Dos componentes de presentación, uno de ellos web</h1>
      <p class="requirement">El sistema debe incluir al menos dos componentes de presentación distintos (uno de ellos: front-end web).</p>
      <table>
        <tr><th>Componente</th><th>Usuario</th><th>Tecnología</th></tr>
        <tr><td><b>Kanasta Web</b></td><td>Clientes</td><td>JavaScript: Node.js, Express y plantillas EJS</td></tr>
        <tr><td><b>Kanasta Admin</b></td><td>Administrador</td><td>Python: aplicación de escritorio con PyQt5</td></tr>
      </table>
      <p>Las dos interfaces usan <b>el mismo API Gateway</b> con los mismos conectores (REST, GraphQL y HTTP). Por eso las
         reglas del negocio son iguales para ambas: no están en las pantallas, están en los servicios.</p>
      <img class="screenshot" src="img/admin-pedidos.png" alt="Kanasta Admin, pestaña Pedidos">
      <p class="muted">Kanasta Admin. En la instalación con Docker se abre desde el navegador (noVNC); también se puede instalar con Python.</p>
      <p class="verdict">✔ Cumple</p>`,
  },
  {
    group: "Requisitos",
    num: "RNF-03",
    title: "Front-end web con SSR",
    view: "cc",
    nodes: ["browser", "web", "gateway", "catalog"],
    html: `
      <p class="kicker">RNF-03</p>
      <h1>El front-end web usa Server-Side Rendering</h1>
      <p class="requirement">El front-end web debe seguir una subarquitectura SSR (Server-Side Rendering).</p>
      <p>Así se arma la página de un producto, por ejemplo <code>/producto/1</code>:</p>
      <ol class="flow">
        <li>El navegador pide <code>GET /producto/1</code> a Kanasta Web.</li>
        <li>Kanasta Web, <b>en el servidor</b>, pide el producto y sus opiniones al API Gateway (REST).</li>
        <li>El gateway reenvía las dos peticiones al Catalog Service y devuelve los datos.</li>
        <li>Kanasta Web llena la plantilla <code>producto.ejs</code> con esos datos.</li>
        <li>El navegador recibe <b>HTML terminado</b>. No ejecuta JavaScript de la aplicación.</li>
      </ol>
      <p class="file">codigo/web/src/index.js</p>
      <pre><code>app.get("/producto/:id", page(async (req, res) =&gt; {
  const [product, reviews] = await Promise.all([
    gateway.getProduct(req.params.id),
    gateway.getReviews(req.params.id),
  ]);
  res.render("producto", { title: product.name, product, reviews });
}));</code></pre>
      <p class="file">codigo/web/views/producto.ejs</p>
      <pre><code>&lt;h1&gt;&lt;%= product.name %&gt;&lt;/h1&gt;
&lt;p class="price big"&gt;&lt;%= priceWithUnit(product) %&gt;&lt;/p&gt;</code></pre>
      <p>Agregar al carrito, ingresar o confirmar el pedido son <b>formularios normales</b> (POST): el servidor hace la
         acción y responde con la página siguiente. Por eso la tienda funciona incluso con JavaScript desactivado.</p>
      <img class="screenshot" src="img/web-catalogo.png" alt="Catálogo de Kanasta Web">
      <p class="verdict">✔ Cumple</p>`,
  },
  {
    group: "Requisitos",
    num: "RNF-04",
    title: "Cuatro componentes de lógica",
    view: "capas",
    nodes: LOGIC,
    html: `
      <p class="kicker">RNF-04</p>
      <h1>Cinco componentes de lógica</h1>
      <p class="requirement">El sistema debe incluir al menos cuatro componentes de tipo lógico.</p>
      <table>
        <tr><th>Componente</th><th>Lenguaje</th><th>Responsabilidad</th></tr>
        <tr><td>Catalog Service</td><td>Python</td><td>Productos por unidad, kg o litro; inventario, categorías, opiniones, datos de la tienda.</td></tr>
        <tr><td>Orders Service</td><td>JavaScript</td><td>Carritos y pedidos; reserva y devuelve inventario.</td></tr>
        <tr><td>Accounts Service</td><td>Java</td><td>Cuentas, contraseñas, sesiones y roles.</td></tr>
        <tr><td>Notifications Service</td><td>Go</td><td>Correos de bienvenida, cambio de contraseña y recibo.</td></tr>
        <tr><td>Image Provider</td><td>JavaScript</td><td>Imágenes de los productos.</td></tr>
      </table>
      <p>Cada uno tiene una responsabilidad de negocio distinta y es <b>dueño de sus datos</b>: si necesita algo de otro, lo pide
         por su API. Por ejemplo, el Orders Service le pide al Catalog Service que reserve 1,5 kg de banano; nunca toca la tabla de productos.</p>
      <p class="verdict">✔ Cumple (5 de 4)</p>`,
  },
  {
    group: "Requisitos",
    num: "RNF-05",
    title: "Comunicación y orquestación",
    view: "cc",
    nodes: ["web", "admin", "gateway", "accounts", "catalog", "orders", "notifications", "images", "broker"],
    html: `
      <p class="kicker">RNF-05</p>
      <h1>Un componente que comunica y orquesta</h1>
      <p class="requirement">El sistema debe incluir al menos un componente que permita la comunicación/orquestación entre los componentes lógicos.</p>
      <p>El <b>API Gateway</b> (Go) es la única puerta hacia la lógica. En cada petición:</p>
      <ol class="flow">
        <li>Lee el token de sesión y le pregunta al <b>Accounts Service</b> quién es el usuario.</li>
        <li>Revisa las reglas de acceso (público, cliente o administrador).</li>
        <li>Agrega los encabezados <code>X-User-Id</code>, <code>X-User-Role</code>… para que los servicios sepan quién llama.</li>
        <li>Envía la petición al servicio dueño de esa ruta.</li>
      </ol>
      <p class="file">codigo/api-gateway/main.go</p>
      <pre><code>routes = []route{
    {"/api/catalog/", CATALOG_URL},
    {"/api/accounts/", ACCOUNTS_URL},
    {"/api/notifications/", NOTIFICATIONS_URL},
    {"/graphql", ORDERS_URL},
    {"/images", IMAGES_URL},
}</code></pre>
      <p>Entre los servicios, el <b>Message Broker</b> (RabbitMQ) comunica de forma asíncrona: Accounts y Orders publican
         eventos que el Notifications Service consume, sin conocerse entre sí.</p>
      <p class="verdict">✔ Cumple</p>`,
  },
  {
    group: "Requisitos",
    num: "RNF-06",
    title: "Cuatro componentes de datos",
    view: "cc",
    nodes: [...DATA, "catalog", "accounts", "orders", "notifications"],
    html: `
      <p class="kicker">RNF-06</p>
      <h1>Cuatro bases de datos: relacionales y NoSQL</h1>
      <p class="requirement">El sistema debe incluir al menos cuatro componentes de datos (incluyendo bases relacionales y NoSQL).</p>
      <table>
        <tr><th>Base</th><th>Tipo</th><th>Dueño</th><th>Por qué ese tipo</th></tr>
        <tr><td>Catalog DB</td><td>PostgreSQL · relacional</td><td>Catalog Service</td><td>Tablas con reglas: <code>CHECK (stock &gt;= 0)</code> impide vender lo que no hay.</td></tr>
        <tr><td>Accounts DB</td><td>PostgreSQL · relacional</td><td>Accounts Service</td><td>Correos únicos (<code>UNIQUE</code>) y sesiones ligadas a su usuario.</td></tr>
        <tr><td>Orders DB</td><td>MongoDB · documentos</td><td>Orders Service</td><td>Un pedido es un documento completo, como una factura.</td></tr>
        <tr><td>Notifications DB</td><td>MongoDB · documentos</td><td>Notifications Service</td><td>Configuración e historial de correos con forma libre.</td></tr>
      </table>
      <p>Cada base tiene <b>un solo dueño</b>: ningún otro servicio conoce su dirección ni su contraseña.</p>
      <p class="verdict">✔ Cumple</p>`,
  },
  {
    group: "Requisitos",
    num: "RNF-07",
    title: "Procesos asíncronos",
    view: "cc",
    nodes: ["orders", "accounts", "broker", "notifications", "resend", "notificationsdb"],
    html: `
      <p class="kicker">RNF-07</p>
      <h1>Correos sin hacer esperar al cliente</h1>
      <p class="requirement">El sistema debe incluir al menos un componente responsable de manejar los procesos asíncronos.</p>
      <p>Enviar un correo puede tardar o fallar. Por eso nadie lo envía "en el momento":</p>
      <ol class="flow">
        <li>El cliente confirma su pedido. El Orders Service lo guarda y <b>responde de inmediato</b>.</li>
        <li>Además deja el evento <code>ORDER_PLACED</code> en la cola <code>notifications</code> del broker.</li>
        <li>El <b>Notifications Service</b> (Go) lee la cola cuando puede, arma el recibo y lo envía con <b>Resend</b>.</li>
        <li>El resultado (Enviado, Falló o No enviado) queda en el historial que ve el administrador.</li>
      </ol>
      <p class="file">codigo/orders-service/src/publisher.js</p>
      <pre><code>// The order is already saved: if the broker is down we only lose the email, not the order
function publish(event) {
  channel.sendToQueue(QUEUE, Buffer.from(JSON.stringify(event)), { persistent: true });
}</code></pre>
      <p>Lo mismo pasa con <code>ACCOUNT_CREATED</code> y <code>PASSWORD_CHANGED</code>, publicados por el Accounts Service (Java).</p>
      <p class="verdict">✔ Cumple</p>`,
  },
  {
    group: "Requisitos",
    num: "RNF-08",
    title: "Conectores basados en HTTP",
    view: "cc",
    nodes: ALL_COMPONENTS,
    connectorFilter: true,
    html: `
      <p class="kicker">RNF-08</p>
      <h1>Un conjunto de conectores HTTP</h1>
      <p class="requirement">El sistema debe incluir un conjunto de conectores basados en HTTP.</p>
      <p>Elige un tipo de conector para verlo en el diagrama:</p>
      <div class="chips" id="connector-chips"></div>
      <table>
        <tr><th>Conector</th><th>Sobre</th><th>Dónde</th></tr>
        <tr><td>HTTP (páginas)</td><td>HTTP</td><td>Navegador → Kanasta Web</td></tr>
        <tr><td>REST</td><td>HTTP</td><td>Front-ends → Gateway → Catalog, Accounts, Notifications; Orders y Notifications → Catalog</td></tr>
        <tr><td>GraphQL</td><td>HTTP</td><td>Front-ends → Gateway → Orders</td></tr>
        <tr><td>HTTP (imágenes)</td><td>HTTP</td><td>Front-ends → Gateway → Image Provider</td></tr>
        <tr><td>REST (HTTPS)</td><td>HTTPS</td><td>Notifications → Resend</td></tr>
        <tr><td>AMQP</td><td>TCP</td><td>Servicios ↔ Message Broker (complementa a HTTP)</td></tr>
      </table>
      <p>REST se usa donde hay recursos simples (<code>/products</code>, <code>/me</code>); GraphQL donde cada pantalla necesita
         datos distintos del mismo servicio (carrito, mis pedidos, todos los pedidos).</p>
      <p class="verdict">✔ Cumple</p>`,
  },
  {
    group: "Requisitos",
    num: "RNF-09",
    title: "Cuatro lenguajes",
    view: "cc",
    nodes: ALL_COMPONENTS,
    languageFilter: true,
    html: `
      <p class="kicker">RNF-09</p>
      <h1>Cuatro lenguajes de propósito general</h1>
      <p class="requirement">El sistema debe construirse usando al menos cuatro lenguajes de programación de propósito general distintos.</p>
      <p>Elige un lenguaje para ver sus componentes:</p>
      <div class="chips" id="language-chips"></div>
      <table>
        <tr><th>Lenguaje</th><th>Componentes</th><th>Se nota en…</th></tr>
        <tr><td>Python</td><td>Catalog Service, Kanasta Admin</td><td><code>requirements.txt</code>, archivos <code>.py</code></td></tr>
        <tr><td>JavaScript</td><td>Kanasta Web, Orders Service, Image Provider</td><td><code>package.json</code>, archivos <code>.js</code></td></tr>
        <tr><td>Java</td><td>Accounts Service</td><td><code>pom.xml</code>, archivos <code>.java</code></td></tr>
        <tr><td>Go</td><td>API Gateway, Notifications Service</td><td><code>go.mod</code>, archivos <code>.go</code></td></tr>
      </table>
      <p>Los componentes no necesitan saber en qué lenguaje está el otro: se entienden por HTTP, JSON y AMQP.</p>
      <p class="verdict">✔ Cumple</p>`,
  },
  {
    group: "Requisitos",
    num: "RNF-10",
    title: "Despliegue en contenedores",
    view: "despliegue",
    nodes: ["web", "admin", "gateway", "catalog", "orders", "accounts", "notifications", "images", "broker", ...DATA],
    html: `
      <p class="kicker">RNF-10</p>
      <h1>Todo corre en contenedores</h1>
      <p class="requirement">El despliegue del sistema debe estar orientado a contenedores.</p>
      <p>Cada componente tiene su <code>Dockerfile</code> (o usa una imagen oficial) y un solo archivo,
         <code>codigo/docker-compose.yml</code>, declara los 13 contenedores, las 2 redes y los 5 volúmenes.</p>
      <pre><code>cd codigo
docker compose up --build</code></pre>
      <table>
        <tr><th>Dirección</th><th>Qué abre</th></tr>
        <tr><td><code>http://localhost:8080</code></td><td>Kanasta Web (clientes)</td></tr>
        <tr><td><code>http://localhost:6080/vnc.html</code></td><td>Kanasta Admin dentro del navegador</td></tr>
        <tr><td><code>http://localhost:8000</code></td><td>API Gateway</td></tr>
      </table>
      <p>Hasta la aplicación de escritorio está en un contenedor (con un escritorio virtual y noVNC), así la tienda no
         tiene que instalar Python, Node.js, Java, Go ni bases de datos.</p>
      <p class="verdict">✔ Cumple</p>`,
  },
  {
    group: "Vistas",
    num: "",
    title: "Las cuatro vistas",
    view: "cc",
    nodes: [],
    explorer: true,
    html: `
      <p class="kicker">Explorar</p>
      <h1>Las cuatro vistas de la arquitectura</h1>
      <p>Cambia de vista con las pestañas del visor y haz clic en cualquier elemento.</p>
      <table>
        <tr><th>Vista</th><th>Qué muestra</th></tr>
        <tr><td><a href="#" data-view="cc">Componentes y conectores</a></td><td>Qué piezas hay en ejecución, sus puertos y cómo se conectan (con notación de piruleta: bola = proveedor, cuenca = consumidor).</td></tr>
        <tr><td><a href="#" data-view="despliegue">Despliegue</a></td><td>En qué máquinas, contenedores, redes y volúmenes corre cada pieza.</td></tr>
        <tr><td><a href="#" data-view="capas">Capas</a></td><td>Quién puede usar a quién: presentación → comunicación → lógica → mensajería → datos.</td></tr>
        <tr><td><a href="#" data-view="descomposicion">Descomposición</a></td><td>Cómo se divide el código: subsistemas, módulos (carpetas) y submódulos (archivos).</td></tr>
      </table>`,
  },
  {
    group: "Vistas",
    num: "",
    title: "Resumen",
    view: "cc",
    nodes: ALL_COMPONENTS,
    html: `
      <p class="kicker">Resumen</p>
      <h1>Todos los requisitos se cumplen</h1>
      <table class="checklist">
        <tr><th>Requisito</th><th>En Kanasta</th><th></th></tr>
        <tr><td>Arquitectura distribuida</td><td>13 contenedores comunicados por red</td><td>✔</td></tr>
        <tr><td>2 presentaciones (una web)</td><td>Kanasta Web + Kanasta Admin</td><td>✔</td></tr>
        <tr><td>Web con SSR</td><td>Node.js + EJS arma el HTML en el servidor</td><td>✔</td></tr>
        <tr><td>4 componentes de lógica</td><td>Catalog, Orders, Accounts, Notifications, Image Provider</td><td>✔</td></tr>
        <tr><td>Comunicación / orquestación</td><td>API Gateway (+ Message Broker)</td><td>✔</td></tr>
        <tr><td>4 de datos, relacional y NoSQL</td><td>2 PostgreSQL + 2 MongoDB</td><td>✔</td></tr>
        <tr><td>Procesos asíncronos</td><td>Notifications Service + RabbitMQ</td><td>✔</td></tr>
        <tr><td>Conectores HTTP</td><td>HTTP, REST, GraphQL, REST sobre HTTPS</td><td>✔</td></tr>
        <tr><td>4 lenguajes</td><td>Python, JavaScript, Java, Go</td><td>✔</td></tr>
        <tr><td>Contenedores</td><td>Docker Compose, un comando</td><td>✔</td></tr>
      </table>
      <p>Y además: 22 de 22 historias de usuario, productos por peso o volumen con su aviso, correos con Resend
         configurables desde Kanasta Admin y una instalación local para cada tienda.</p>
      <p class="verdict">Kanasta: todo lo que buscas, en una canasta.</p>`,
  },
];

const CONNECTOR_TYPES = ["HTTP", "REST", "GraphQL", "AMQP", "REST (HTTPS)", "Database Connection"];

const LANGUAGES = {
  Python: ["catalog", "admin"],
  JavaScript: ["web", "orders", "images"],
  Java: ["accounts"],
  Go: ["gateway", "notifications"],
};
