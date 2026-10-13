# Requisitos no funcionales

Los requisitos no funcionales describen **cómo** debe ser el sistema (su forma, su calidad y sus restricciones), no lo que hace.

## Requisitos de arquitectura

Estos requisitos son obligatorios para el prototipo. Todos se cumplen.

| ID | Requisito | Cómo se cumple en Kanasta | Estado |
|---|---|---|---|
| RNF-01 | El sistema debe seguir una **arquitectura distribuida**. | Kanasta está formado por 6 componentes que corren en contenedores separados y se comunican por la red. | ✅ |
| RNF-02 | Debe tener al menos **un componente de presentación** (front-end web). | **Kanasta Web**: páginas HTML, CSS y JavaScript servidas por Nginx. | ✅ |
| RNF-03 | Debe tener al menos **dos componentes de lógica**. | **Catalog Service** (productos e inventario), **Orders Service** (carritos y pedidos) e **Image Provider** (imágenes). Son tres. | ✅ |
| RNF-04 | Debe tener al menos **dos componentes de datos**: una base relacional y una NoSQL. | **Catalog DB** en PostgreSQL (relacional) y **Orders DB** en MongoDB (NoSQL, documentos). | ✅ |
| RNF-05 | Debe usar al menos **dos tipos distintos de conectores basados en HTTP**. | **REST** (Web → Catalog Service y Orders Service → Catalog Service), **GraphQL** (Web → Orders Service) y **HTTP** simple para imágenes (Web → Image Provider). | ✅ |
| RNF-06 | Debe estar construido con al menos **dos lenguajes de programación** de propósito general. | **Python** (Catalog Service) y **JavaScript / Node.js** (Orders Service, Image Provider y la Web). | ✅ |
| RNF-07 | El despliegue debe estar **orientado a contenedores**. | Cada componente tiene su `Dockerfile` y todo se levanta con un solo comando: `docker compose up --build`. | ✅ |

## Requisitos de calidad

| ID | Atributo | Requisito | Cómo se cumple | Estado |
|---|---|---|---|---|
| RNF-08 | Facilidad de uso | Un cliente nuevo debe poder hacer una compra completa sin ayuda y en menos de 3 minutos. | Menú simple de 4 opciones, mensajes en español claro y botones visibles. | ✅ |
| RNF-09 | Adaptabilidad | Las páginas deben verse bien en computador y en celular. | El diseño se reorganiza en pantallas pequeñas (CSS responsive). | ✅ |
| RNF-10 | Desempeño | El catálogo debe cargar en menos de 2 segundos en una red local. | Consultas SQL simples y las imágenes se guardan en caché del navegador por un día. | ✅ |
| RNF-11 | Integridad de datos | No se puede vender más unidades de las que hay en inventario. | El Catalog Service descuenta unidades solo si alcanzan (`stock >= cantidad`) y la base de datos no permite inventario negativo. | ✅ |
| RNF-12 | Disponibilidad parcial | Si falta una imagen, la tienda debe seguir funcionando. | El Image Provider responde con una imagen de reemplazo ("Imagen no disponible"). | ✅ |
| RNF-13 | Mantenibilidad | Cada componente debe poder cambiarse sin modificar los demás, mientras respete su interfaz. | Cada servicio tiene su propia base de datos y solo se comunica por su API (REST, GraphQL o HTTP). | ✅ |
| RNF-14 | Portabilidad | El sistema debe correr en Windows, macOS o Linux. | Solo se necesita Docker; no hay que instalar Python, Node.js ni bases de datos. | ✅ |
| RNF-15 | Seguridad | El panel de administración debe estar protegido con usuario y contraseña. | Depende de las historias HU-10 y HU-11 (cuentas de usuario). | ⏳ Versión 2 |
| RNF-16 | Seguridad | Los textos escritos por los usuarios no deben poder ejecutar código en la página. | La web escapa todos los textos antes de mostrarlos y las consultas SQL usan parámetros. | ✅ |
| RNF-17 | Idioma | Toda la interfaz debe estar en español y los precios en pesos colombianos. | Textos en español y precios con formato `$ 1.234.000`. | ✅ |
