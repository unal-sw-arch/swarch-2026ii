# UNDesparche: Prototipo 2

**Presentado por:**

-   Deibyd Santiago Barragan Gaitan - dbarragang@unal.edu.co
-   Jacobo Alzate Corredor - jalzateco@unal.edu.co
-   Víctor Camilo Cañón Castellanos - vcanonc@unal.edu.co
-   David Santiago Velasquez Gomez - dvelasquezg@unal.edu.co

**Docente:**

Santiago Suarez Suarez - ssuarez@unal.edu.co

Universidad Nacional de Colombia\
Facultad de Ingeniería\
Departamento de Ingeniería de Sistemas e Industrial\
2026

## Índice

-   [Contexto](#contexto)
-   [Alcance del proyecto](#alcance-del-proyecto)
-   [Funcionalidades principales del
    sistema](#funcionalidades-principales-del-sistema)
    -   [Características principales para el administrador del
        sistema](#características-principales-para-el-administrador-del-sistema)
    -   [Características principales para el administrador de
        eventos](#características-principales-para-el-administrador-de-eventos)
    -   [Características principales para el administrador de
        implementos](#características-principales-para-el-administrador-de-implementos)
    -   [Características principales para el
        miembro](#características-principales-para-el-miembro)
-   [Caracterización de actores](#caracterización-de-actores)
-   [Caracterización de entidades](#caracterización-de-entidades)
-   [Reglas de negocio](#reglas-de-negocio)
-   [Requerimientos funcionales](#requerimientos-funcionales)
-   [Requerimientos no funcionales](#requerimientos-no-funcionales)
    -   [Seguridad](#seguridad)
    -   [Rendimiento](#rendimiento)
    -   [Escalabilidad](#escalabilidad)
    -   [Disponibilidad](#disponibilidad)
    -   [Mantenibilidad](#mantenibilidad)
    -   [Portabilidad](#portabilidad)
    -   [Usabilidad](#usabilidad)
    -   [Interoperabilidad](#interoperabilidad)
    -   [Calidad](#calidad)
    -   [Concurrencia](#concurrencia)
-   [Arquitectura del sistema](#arquitectura-del-sistema)
    -   [Estructura de Componentes y Conectores (C&C)](#estructura-de-componentes-y-conectores-c-c)
    -   [Diagrama C&C](#diagrama-c-c)
    -   [Descripción de componentes](#descripción-de-componentes)
    -   [Interfaces y mecanismos de comunicación](#interfaces-y-mecanismos-de-comunicación)
    -   [Escenarios de interacción](#escenarios-de-interacción)

### Descripción del proyecto

Este documento tiene como objetivo la recopilación, análisis y
especificación de los requerimientos de un sistema para la gestión de
eventos e implementos recreativos de la UNAL como proyecto de la materia
de Arquitectura de Software.

## Contexto

En la Universidad Nacional de Colombia, los estudiantes no cuentan con
un medio centralizado que les permita conocer las actividades y los
implementos recreativos disponibles dentro del campus. Como
consecuencia, muchos desconocen estos recursos y pierden oportunidades
de participación en espacios deportivos, culturales y recreativos.

Actualmente, la consulta de disponibilidad de implementos requiere
desplazarse físicamente a las distintas facultades, ya que no existe un
sistema que permita verificar esta información de manera remota. De
forma similar, la difusión de eventos se realiza principalmente a través
del correo institucional, lo que limita su alcance debido a la
saturación de mensajes que reciben los estudiantes.

A partir de esta situación, surge la necesidad de una solución que
centralice la información relacionada con implementos recreativos y
eventos, permitiendo su consulta, seguimiento y gestión de manera
eficiente. La propuesta plantea un sistema que facilite la visualización
de disponibilidad de implementos por facultad, así como la publicación y
consulta de eventos, integrando además mecanismos de notificación y
visualización geográfica dentro del campus.

UNDesparche está enfocado en el sector de la educación superior y la
gestión de bienestar institucional universitario. Su naturaleza consiste
en el desarrollo de una solución de software plataforma web orientada a
centralizar, promover y optimizar el acceso a actividades recreativas,
culturales y deportivas dentro de la Universidad Nacional de Colombia
(Sede Bogotá).

El sistema actúa como un puente directo entre las dependencias
administrativas de la universidad (facultades, áreas de deporte, cultura
y bienestar) y los estudiantes, resolviendo problemas de ineficiencia
logística en el préstamo presencial de implementos recreativos y fallas
en los canales masivos de comunicación institucional (correos no leídos,
carteleras físicas desactualizadas).

## Alcance del proyecto

Diseñar y desarrollar una aplicación web para la gestión de eventos y de
implementos recreativos en la Universidad Nacional de Colombia, que
permita la administración de inventarios y la visualización de
disponibilidad de implementos, como también la gestión y consulta de
eventos.

El sistema incluirá funcionalidades de registro, actualización,
consulta, seguimiento y reserva de implementos y eventos, con control de
acceso basado en roles (administrador general, administrador de eventos,
administrador de implementos y estudiantes), limitado al uso dentro de
la comunidad universitaria mediante autenticación institucional para el
préstamo de implementos, mientras que usuarios externos podrán
suscribirse a notificaciones de eventos sin autenticarse.

## Funcionalidades principales del sistema

A continuación, se enlistan las características y funcionalidades
proyectadas para el software, segmentadas por los roles que tendrá el
sistema:

### Características principales para el administrador del sistema

1.  **Gestión de usuarios:** El sistema permitirá crear, consultar y
    eliminar los datos de los usuarios registrados, asignarles un rol
    (Administrador del sistema, Administrador de eventos, Administrador
    de implementos, Miembros de la comunidad) y darle los permisos
    referentes a su rol.
2.  **Gestión de eventos:** Permite supervisar y gestionar todos los
    eventos registrados en el sistema, incluyendo su creación,
    modificación y eliminación en caso necesario.
3.  **Gestión de implementos:** Permite supervisar y administrar el
    inventario general de implementos recreativos registrados por las
    distintas facultades.
4.  **Gestión de cuentas:** El administrador del sistema puede bloquear
    cuentas por actividades indebidas.

### Características principales para el administrador de eventos

1.  **Gestión de eventos:** El administrador de eventos podrá crear,
    consultar, modificar y eliminar eventos (que haya creado antes de
    ser publicados), definiendo atributos como nombre, estado (por
    hacerse, en ejecución, cancelado, realizado), descripción, lugar,
    fecha y hora, categoría, entre otros.
2.  **Publicación de eventos:** El administrador de eventos podrá
    publicar los eventos para que sean visibles a los demás usuarios del
    sistema.

### Características principales para el administrador de implementos

1.  **Gestión de implementos:** El administrador de implementos podrá
    registrar, actualizar, eliminar y consultar implementos recreativos
    asociados a su respectiva facultad.
2.  **Gestión de disponibilidad:** El administrador de implementos podrá
    actualizar el estado de disponibilidad de los implementos.
3.  **Gestión de reservas:** El administrador de implementos podrá
    validar y gestionar las solicitudes de reserva realizadas por los
    estudiantes.

### Características principales para el miembro

Nota: llamaremos "miembro" al usuario registrado en la plataforma con
correo UNAL (@unal.edu.co).

1.  **Consulta de implementos:** El miembro podrá consultar el catálogo
    de implementos por facultades y verificar su disponibilidad.
2.  **Suscripción y notificaciones:** El miembro podrá realizar
    suscripciones a eventos o implementos y recibir notificaciones sobre
    cambios relevantes.
3.  **Reserva de implementos:** El miembro podrá realizar reservas
    temporales de implementos disponibles por un tiempo determinado.

## Caracterización de actores

| Actor | Descripción | Rango de Edad | Género | Gustos / Intereses | Discapacidad |
|---|---|---|---|---|---|
| Miembro de la Comunidad | Estudiantes, docentes o administrativos con correo @unal.edu.co que usan el sistema para consultar y reservar eventos e implementos | 15 – 100 años | Todos los géneros | Deportes, videojuegos, música, integración social, cultura y bienestar. | Puede presentar discapacidades motrices o visuales (el sistema NO implementará accesibilidad). |
| Administrador de Eventos | Estudiante, docente o funcionario administrativo encargado de proponer, publicar y gestionar la logística de eventos culturales o deportivos. | 18 – 65 años | Todos los géneros | Liderazgo estudiantil, difusión cultural, coordinación logística y animación de espacios públicos. | Puede presentar discapacidades motrices o visuales (el sistema NO implementará accesibilidad). |
| Administrador de Implementos | Funcionario de Bienestar Universitario o estudiante auxiliar del inventario y control físico de los implementos deportivos en las facultades. | 18 – 65 años | Todos los géneros | Control de inventarios, servicio al cliente, deportes y gestión administrativa. | Puede presentar discapacidades motrices o visuales (el sistema NO implementará accesibilidad). |
| Administrador del Sistema | Ingeniero de soporte encargado de supervisar los logs de auditoría, gestionar permisos globales de acceso y garantizar la operación técnica. | 22 – 60 años | Todos los géneros | Ciberseguridad, administración de bases de datos y optimización de software. | Puede presentar discapacidades motrices o visuales (el sistema NO implementará accesibilidad). |

## Caracterización de entidades

A continuación, se estructuran las características técnicas de los
elementos principales del dominio de software.

### 1. Miembros de la Comunidad (Usuarios)

-   **Descripción:** Entidad que tiene los datos de todas las personas
    registradas con credenciales institucionales (correo @unal.edu.co =
    Miembro de la Comunidad , Administrador de Eventos, Administrador de
    Implementos, Administrador del Sistema).
-   **Propiedades:** id_usuario, nombre completo, correo institucional
    (@unal.edu.co), facultad, rol (Valores permitidos: Miembro de la
    Comunidad , Administrador de Eventos, Administrador de Implementos,
    Administrador del Sistema), estado del usuario (Valores permitidos:
    Activo, Sancionado)., fecha_registro.

### 2. Evento

-   **Descripción:** Actividad con un objetivo concreto programada
    dentro de los límites geográficos del campus universitario. Pueden
    asistir tanto Miembros de la Comunidad universitaria como externos
    (personas sin correo @unal.edu.co), creada por un Administrador de
    Eventos.
-   **Propiedades:** id_evento, nombre del evento, descripción,
    publicado, lugar, fecha y hora de inicio, fecha y hora de fin,
    organizador, estado del evento (Valores permitidos: Programado, En
    Curso, Finalizado, Cancelado), categoría temática (Valores
    permitidos: Académico, Cultural, Deportivo, Asamblea, Parche, Otro),
    imagen.

### 3. Implemento

-   **Descripción:** Objeto, material recreativo o artículo deportivo de
    propiedad de la universidad, disponible en las facultades para
    préstamo a los Miembros de la Comunidad.
-   **Propiedades:** id_implemento, nombre del artículo, categoría
    (Valores permitidos: Balones, Raquetas, Mesas, Juegos recreativos,
    Juegos de mesa, otros), facultad propietaria, estado del implemento
    (valores permitidos: Disponible, No Disponible, Reservado,
    Prestado), descripción, imagen.

## Reglas de negocio

**RN-01 (Autenticación Exclusiva):** El sistema restringirá el registro
e inicio de sesión exclusivamente a usuarios que utilicen una cuenta con
dominio institucional (@unal.edu.co). Cualquier solicitud proveniente de
un dominio distinto será rechazada de manera automática.

**RN-02 (Vigencia Temporal de Reservas):** Al solicitar la reserva de un
implemento en estado "Disponible", su estado cambiará a "Reservado" y el
Miembro de la Comunidad dispondrá de un plazo improrrogable de diez (10)
minutos para realizar su retiro presencial en la facultad
correspondiente. Transcurrido dicho lapso sin la confirmación del
préstamo, el sistema cancelará la reserva y reajustará el estado del
implemento a "Disponible".

**RN-03 (Sanción por Daño o Deterioro de Implementos):** Si un
implemento es devuelto con daños o averías, el Administrador de
Implementos registrará la novedad, modificando el estado del Miembro de
la Comunidad de "Activo" a "Sancionado" y el del implemento a "No
Disponible". Esta sanción inhabilitará al usuario para realizar nuevas
reservas hasta que se efectúe el pago correspondiente o la reparación de
los daños causados.

**RN-04 (Exclusividad de Gestión por Facultad):** Un usuario únicamente
podrá gestionar o modificar la información de un implemento si cuenta
con el rol de Administrador de Implementos y el artículo pertenece
formalmente a la misma facultad registrada en su perfil.

**RN-05 (Límite de Reservas Simultáneas):** Un Miembro de la Comunidad
podrá tener registrado un máximo de un (1) implemento simultáneamente
bajo los estados "Reservado" o "Prestado" en todo el sistema.

## Requerimientos funcionales

Todo lo referente al que debe hacer la aplicación para cumplir sus
funcionalidades principales de manera óptima, satisfaciendo las
necesidades del cliente.

**RF-1.** El sistema debe permitir el registro e inicio de sesión de
usuarios mediante autenticación exclusiva con cuentas de correo
institucionales (@unal.edu.co) a través de Google.

**RF-2.** El sistema debe manejar los siguientes roles de usuario:
Administrador del sistema, Administrador de eventos, Administrador de
implementos y Miembro de la comunidad.

**RF-3.** El sistema debe permitir a todos los usuarios visualizar los
eventos públicos en un mapa interactivo del campus universitario.

**RF-4.** El sistema debe permitir filtrar todos los eventos por
categoría temática (Académico, Cultural, Deportivo, Asamblea, Parche,
Otro) y estado (Programado, En Curso, Finalizado, Cancelado).

**RF-5.** El sistema debe permitir ordenar el listado de eventos por
fecha y hora de inicio.

**RF-6.** El sistema debe permitir filtrar el catálogo de implementos
por categoría (Balones, Raquetas, Mesas, Juegos recreativos, Juegos de
mesa, otros).

**RF-7.** El sistema debe permitir a usuarios no registrados suscribirse
a las notificaciones de un evento específico ingresando una dirección de
correo electrónico válida.

**RF-8.** El sistema debe enviar notificaciones automáticas por correo
electrónico a los usuarios suscritos a un evento cuando ocurran cambios
relevantes en el mismo (modificación de lugar, fecha, hora o
cancelación).

**RF-9.** El sistema debe permitir a los miembros de la comunidad
consultar el catálogo completo e inventario de implementos recreativos
filtrados por facultad propietaria y verificar su estado de
disponibilidad (Disponible, No Disponible, Reservado, Prestado).

**RF-10.** El sistema debe mostrar un aviso de descargo de
responsabilidad antes de solicitar la reserva de cualquier implemento.

**RF-11.** El sistema debe permitir la reserva de un implemento
únicamente si el usuario está con estado Activo y acepta expresamente el
aviso de descargo de responsabilidad.

**RF-12.** El sistema debe permitir a los miembros de la comunidad
autenticados realizar la reserva temporal de un implemento en estado
"Disponible", cambiando su estado a "Reservado" por un plazo
improrrogable de diez (10) minutos para su retiro físico.

**RF-13.** El sistema debe cancelar automáticamente la reserva y cambiar
el estado del implemento de vuelta a "Disponible" si el plazo de diez
(10) minutos expira sin que el préstamo haya sido confirmado por el
Administrador de Implementos.

**RF-14.** El sistema debe validar y restringir que un miembro de la
comunidad sólo tenga máximo un (1) implemento activo simultáneamente
bajo el estado "Reservado" o "Prestado".

**RF-15.** El sistema debe permitir a los miembros de la comunidad
autenticados suscribirse a eventos para recibir notificaciones.

**RF-16.** El sistema debe permitir a los administradores de eventos
registrar eventos en el sistema especificando nombre, descripción,
lugar, fecha/hora de inicio, fecha/hora de fin, categoría temática y
opcionalmente una imagen asociada.

**RF-17.** El sistema debe guardar los eventos creados inicialmente en
estado de borrador (no publicado), impidiendo su visualización pública
hasta que su creador decida publicarlo.

**RF-18.** El sistema debe permitir a los administradores de eventos
publicar los eventos de su autoría para hacerlos visibles a la
comunidad.

**RF-19.** El sistema debe permitir a los administradores de eventos
modificar la información de los eventos creados por ellos.

**RF-20.** El sistema debe permitir a los administradores de eventos
cancelar los eventos creados por ellos cambiando su estado a
"Cancelado".

**RF-21.** El sistema debe impedir la eliminación física de eventos que
ya hayan sido publicados para preservar el historial del sistema.

**RF-22.** El sistema debe permitir a los administradores de implementos
registrar, consultar, modificar y eliminar físicamente implementos
recreativos asociados de manera exclusiva a su facultad registrada.

**RF-23.** El sistema debe permitir al administrador de implementos
gestionar el estado de los implementos de su facultad (Disponible, No
Disponible, Reservado, Prestado).

**RF-24.** El sistema debe permitir a los administradores de implementos
validar y procesar las solicitudes de reserva de implementos
pertenecientes a su facultad.

**RF-25.** El sistema debe permitir al administrador de implementos
reportar la entrega de un implemento dañado, cambiando automáticamente
el estado del usuario de "Activo" a "Sancionado" y el del implemento a
"No Disponible", restringiendo futuras reservas del usuario sancionado.

**RF-26.** El sistema debe permitir al administrador del sistema
consultar la lista completa de usuarios registrados mostrando su
id_usuario, nombre completo, correo institucional, facultad, rol, estado
(Activo, Sancionado) y fecha de registro.

**RF-27.** El sistema debe permitir al administrador del sistema buscar
usuarios por nombre completo o correo institucional y filtrarlos por rol
y estado actual.

**RF-28.** El sistema debe permitir al administrador del sistema asignar
o revocar roles de usuario, exceptuando la modificación sobre otros
administradores del sistema.

**RF-29.** El sistema debe permitir al administrador del sistema
bloquear o sancionar cuentas de usuario ante faltas cometidas y eliminar
usuarios registrados (excepto a otros administradores del sistema).

**RF-30.** El sistema debe permitir al administrador del sistema
supervisar, consultar, modificar o eliminar cualquier evento o
implemento registrado en la plataforma independientemente de la facultad
u organizador.

## Requerimientos no funcionales

El cómo la aplicación debe cumplir sus funcionalidades principales de
manera que satisfaga las necesidades del cliente y a su vez no presente
errores.

### Seguridad

**RNF-1.** El sistema debe integrar la plataforma Firebase
Authentication para autenticar a los usuarios de manera segura mediante
sus cuentas institucionales de Google.

**RNF-2.** El sistema debe finalizar automáticamente la sesión activa
del usuario tras detectar diez (10) minutos continuos de inactividad.

### Rendimiento

**RNF-3.** El tiempo de respuesta para la consulta de disponibilidad de
inventario y la carga del mapa interactivo no debe superar los dos (2)
segundos bajo condiciones normales de red.

**RNF-4.** El backend debe implementar un mecanismo de almacenamiento en
caché para las peticiones de alta frecuencia, optimizando la velocidad
de respuesta y reduciendo la carga sobre la base de datos.

### Escalabilidad

**RNF-5.** El sistema debe ser desplegado en contenedores Docker
independientes para la interfaz de usuario (frontend) y sus respectivos
microservicios o servicios de soporte.

**RNF-6.** La arquitectura del sistema debe permitir la incorporación
modular de nuevas funcionalidades o servicios sin degradar ni alterar la
operación de los módulos existentes.

### Disponibilidad

**RNF-7.** Ante la ocurrencia de fallas controladas, el sistema debe
registrar el evento de excepción y garantizar la continuidad operativa
sin comprometer ni perder la información del usuario.

**RNF-8.** El sistema debe contar con un proceso automatizado de
respaldo (backup) de la base de datos con una periodicidad semanal.

### Mantenibilidad

**RNF-9.** El código fuente debe apegarse al estándar de estilo PEP 8 e
implementar principios de Código Limpio (Clean Code) y patrones de
diseño reconocidos para favorecer su mantenibilidad.

**RNF-10.** El código fuente debe incluir documentación técnica clara
mediante comentarios e intencionados docstrings para clases, métodos y
módulos principales.

### Portabilidad

**RNF-11.** La aplicación web debe ser plenamente compatible y funcional
en las últimas dos versiones principales del navegador Google Chrome.

### Usabilidad

**RNF-12.** La interfaz gráfica de usuario debe contar con un diseño
adaptable (responsive) para su correcta visualización en diversos
dispositivos.

**RNF-13.** La navegación debe estructurarse de modo que el usuario
pueda acceder a cualquier funcionalidad principal en un máximo de cinco
(5) clics.

### Interoperabilidad

**RNF-14.** El sistema debe integrarse con Google Maps Platform para
visualizar de forma precisa la ubicación geográfica de los eventos en el
campus.

**RNF-15.** El sistema debe utilizar un servicio externo especializado
para el envío seguro y confiable de notificaciones por correo
electrónico.

### Calidad

**RNF-16.** El sistema debe incluir contar con pruebas unitarias que
cubra la lógica de los módulos principales.

**RNF-17.** El desarrollo debe emplear herramientas de análisis estático
de código (linters) para identificar y corregir posibles defectos
durante la fase de construcción.

### Concurrencia

**RNF-18.** El sistema debe prevenir condiciones de carrera para
garantizar la integridad de los datos, asegurando que un mismo
implemento no pueda ser reservado por más de un usuario simultáneamente.

**RNF-19.** La aplicación debe soportar de manera concurrente cerca de
400 usuarios a cualquier hora del día, funcionando de lunes a domingo
las 24 horas del día durante el periodo académico establecido por el
Consejo Universitario.

---

# Arquitectura del sistema

## Estructura de Componentes y Conectores (C&C)

### Diagrama C&C

El diagrama muestra dos clientes, un gateway, tres servicios de dominio, sus bases de datos, un sistema de mensajería y los servicios externos de autenticación, mapas, almacenamiento y correo.

![C&C diagram](images/diagrama_c&c.png)

### Descripción de componentes

| Componente | Responsabilidad |
|---|---|
| **ReactWebPage** | Interfaz web desarrollada en React. Se comunica con el Gateway, Firebase Authentication y Google Maps. |
| **FlutterMobileApp** | Aplicación móvil desarrollada en Flutter. Utiliza las mismas integraciones que el cliente web. |
| **APIGateway** | API desarrollada con FastAPI. Valida los ID Tokens de Firebase y encamina las solicitudes a los servicios de dominio. Añade a las solicitudes internas el contexto del usuario mediante encabezados. No contiene lógica de negocio ni sustituye la autorización de cada servicio. Mantiene en memoria una caché de datos de usuario con TTL de 60 segundos; la validación del token se realiza en cada solicitud protegida. |
| **Users** | Servicio desarrollado con Django REST Framework. Gestiona usuarios, roles y estados. Comprueba la identidad recibida del Gateway consultando su propia base de datos. |
| **Events** | Servicio desarrollado con Django REST Framework. Gestiona eventos y suscripciones. Persiste los datos en `EventsDB` y utiliza Firebase Storage para las imágenes. Aplica los permisos de las operaciones sobre eventos. |
| **Inventory** | Servicio desarrollado con Django REST Framework. Gestiona implementos, disponibilidad, reservas y préstamos. Persiste los datos en `Inventory DB` y utiliza Firebase Storage para las imágenes. Aplica las reglas de autorización del inventario. |
| **UsersDB**, **EventsDB**, **Inventory DB** | Bases de datos PostgreSQL independientes, una para cada servicio de dominio. Los servicios acceden a ellas mediante Django ORM. |
| **NotificationsQueue** | Cola de mensajes implementada con RabbitMQ. Recibe mensajes publicados por `Events` e `Inventory` y los entrega al consumidor. |
| **Notifications** | Servicio independiente desarrollado con FastAPI. Consume mensajes de RabbitMQ, construye los correos y solicita su envío a Brevo. |
| **FirebaseAuth** | Proveedor externo de autenticación. Los clientes obtienen ID Tokens que el Gateway valida en las solicitudes protegidas. |
| **GoogleMapsAPI** | Servicio externo de mapas, utilizado directamente por los clientes para mostrar ubicaciones. |
| **Firebase Storage** | Almacenamiento externo utilizado por `Events` e `Inventory` para las imágenes de eventos e implementos. |
| **Brevo** | Servicio externo utilizado por `Notifications` para el envío de correos electrónicos. |

### Interfaces y mecanismos de comunicación

Cada conexión del diagrama enlaza los puertos indicados en la tabla. Los mecanismos corresponden al protocolo o tecnología utilizada en esa comunicación.

| Origen | Puerto de salida | Destino | Puerto de entrada | Mecanismo |
|---|---|---|---|---|
| `ReactWebPage` | `fbAuthPt` | `FirebaseAuth` | `authPt` | SDK de Firebase sobre HTTPS |
| `FlutterMobileApp` | `fbAuthPt` | `FirebaseAuth` | `authPt` | SDK de Firebase sobre HTTPS |
| `ReactWebPage` | `apiPt` | `APIGateway` | `webApiPt` | HTTP |
| `FlutterMobileApp` | `apiPt` | `APIGateway` | `mobileApiPt` | HTTP |
| `ReactWebPage` | `mapsPt` | `GoogleMapsAPI` | `apiPt` | API de Google Maps sobre HTTPS |
| `FlutterMobileApp` | `mapsPt` | `GoogleMapsAPI` | `apiPt` | API de Google Maps sobre HTTPS |
| `APIGateway` | `usersPt` | `Users` | `apiPt` | HTTP |
| `APIGateway` | `eventsPt` | `Events` | `apiPt` | HTTP |
| `APIGateway` | `invPt` | `Inventory` | `apiPt` | HTTP |
| `Users` | `usersDbPt` | `UsersDB` | `queryPt` | Django ORM / conexión PostgreSQL |
| `Events` | `eventsDbPt` | `EventsDB` | `queryPt` | Django ORM / conexión PostgreSQL |
| `Inventory` | `invDbPt` | `Inventory DB` | `queryPt` | Django ORM / conexión PostgreSQL |
| `Events` | `fbStgPt` | `Firebase Storage` | `objStgPt` | API de Firebase Storage |
| `Inventory` | `fbStgPt` | `Firebase Storage` | `objStgPt` | API de Firebase Storage |
| `Events` | `ntfsPt` | `NotificationsQueue` | `prodPt` | AMQP, publicación de mensajes |
| `Inventory` | `ntfsPt` | `NotificationsQueue` | `prodPt` | AMQP, publicación de mensajes |
| `NotificationsQueue` | `conPt` | `Notifications` | `queuePt` | AMQP, consumo de mensajes |
| `Notifications` | `brevoPt` | `Brevo` | `emailApiPt` | API HTTP de Brevo |

En las solicitudes protegidas, los clientes envían el ID Token en `Authorization: Bearer <token>`. El Gateway lo valida y transmite la identidad del usuario a los servicios mediante los encabezados `X-From-Gateway`, `X-User-Id`, `X-User-Email`, `X-User-Role`, `X-User-Faculty` y `X-User-Status`. El token de Firebase no se reenvía. Las solicitudes públicas pueden llegar sin contexto de usuario.

El Gateway encamina las solicitudes HTTP, pero cada servicio aplica sus propios permisos. `Users` reconstruye el usuario consultando `UsersDB`; `Events` e `Inventory` utilizan el contexto recibido para evaluar sus reglas de acceso.

Las tareas periódicas previstas en `Events` e `Inventory` revisarán, aproximadamente cada 30 segundos, condiciones temporales o cambios de estado que puedan generar notificaciones. Cuando corresponda, publicarán un mensaje en RabbitMQ. `Notifications` consume los mensajes de forma continua y llama a la API de Brevo para solicitar el envío del correo.

**Consideración de seguridad:** el encabezado `X-From-Gateway: true` no autentica criptográficamente al emisor. La comunicación interna debe restringirse a los componentes autorizados y requiere un mecanismo de autenticación entre servicios para evitar la falsificación de encabezados.

### Escenarios de interacción

#### 1. Autenticación y solicitud protegida

1. El cliente se autentica directamente con `FirebaseAuth` y obtiene un ID Token.
2. Envía la solicitud al Gateway con el token en el encabezado `Authorization`.
3. El Gateway valida el token y obtiene el identificador del usuario (`firebase_uid`).
4. Si no tiene datos vigentes en caché, consulta a `Users`; si el usuario no existe, solicita su creación.
5. El Gateway añade los encabezados de contexto y reenvía la solicitud al servicio correspondiente.
6. El servicio verifica los permisos, ejecuta la operación y devuelve la respuesta a través del Gateway.

#### 2. Consulta y visualización de eventos

1. El cliente solicita los eventos al Gateway.
2. El Gateway reenvía la solicitud a `Events`.
3. `Events` consulta `EventsDB` y devuelve los eventos correspondientes.
4. El cliente utiliza los datos de ubicación para mostrarlos mediante `GoogleMapsAPI`, sin pasar por el Gateway.

#### 3. Cambio de evento y envío de correo

1. El cliente envía al Gateway una solicitud para crear o modificar un evento.
2. El Gateway valida la identidad y remite la solicitud a `Events`.
3. `Events` verifica los permisos y guarda los cambios en `EventsDB`.
4. Si el cambio requiere notificaciones, `Events` publica un mensaje en `NotificationsQueue` mediante AMQP.
5. `Notifications` consume el mensaje y solicita a Brevo el envío del correo mediante su API.

#### 4. Reserva y vencimiento de un implemento

1. El cliente solicita a `Inventory` la disponibilidad de un implemento.
2. `Inventory` consulta `Inventory DB` y, al recibir una solicitud de reserva, verifica las reglas correspondientes, incluido el límite de un implemento reservado o prestado por miembro.
3. Si se cumplen las condiciones, registra la reserva, que debe confirmarse en un plazo de diez minutos.
4. La tarea periódica prevista detecta las reservas vencidas, las cancela y restablece la disponibilidad del implemento.
5. Si corresponde enviar una notificación, `Inventory` publica el mensaje en RabbitMQ para que `Notifications` lo procese y solicite el envío a Brevo.