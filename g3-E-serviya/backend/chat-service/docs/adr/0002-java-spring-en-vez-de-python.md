# ADR-0002: Java 21 + Spring Boot en vez de Python + FastAPI

- **Estado:** Aceptado — reemplaza el ADR-002 de la guía
- **Fecha:** 2026-11-09

## Contexto
La guía proponía Python/FastAPI porque el chat es intensivo en E/S y porque lo iba a construir otro equipo. Al
implementarlo dentro de este repositorio, todo lo demás (monolito, `notifications-service`, gateway) es Java 21 +
Spring Boot 3.5, con Maven, Flyway, Testcontainers y el mismo estilo hexagonal.

## Decisión
Implementar el chat en Java 21 + Spring Boot 3.5 (Spring MVC, WebSocket nativo de Spring, Spring Data JPA/JDBC,
Spring AMQP, Spring Data Redis/Lettuce, resource server OAuth2 para ES256/JWKS). La arquitectura de la guía se
mantiene igual: hexagonal, puertos en `application`, dominio sin frameworks.

## Alternativas consideradas
- **Python + FastAPI (guía):** sin reutilización; un segundo stack que operar y mantener.
- **Spring WebFlux:** más conexiones por hilo, pero más curva y el resto del proyecto es MVC. Las conexiones
  WebSocket abiertas e inactivas no ocupan hilos en Tomcat (NIO), así que 1 000 por instancia (RNF-CHAT-03) no
  deberían requerir programación reactiva. **Pendiente medirlo** con una prueba de carga (k6/Locust).

## Consecuencias
- Se reutilizan plantilla, convenciones y herramientas (`notifications-service` como referencia).
- La capa `application` no tiene anotaciones de Spring: los casos de uso son clases planas que se instancian en
  `shared/infrastructure/config/UseCaseConfig`, y las transacciones pasan por el puerto `UnitOfWork`.
- Equivalencias con la guía: Alembic → Flyway, pydantic → records, import-linter → revisión (pendiente: ArchUnit).
