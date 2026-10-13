# Team 3-B

## Members

- Deibyd Santiago Barragan Gaitan
- Jacobo Alzate Corredor
- Víctor Camilo Cañón Castellanos
- David Santiago Velasquez Gomez

Proyecto de la materia Ingeniería de Software II, Universidad Nacional de Colombia (Facultad de Ingeniería, Departamento de Ingeniería de Sistemas e Industrial). Docente: Santiago Suarez Suarez. 2026.

## Project

**UNDesparche** es una plataforma web para la gestión de eventos e implementos recreativos de la Universidad Nacional de Colombia (Sede Bogotá).

### Problema

- Los estudiantes no tienen un medio centralizado para conocer las actividades y los implementos recreativos disponibles en el campus.
- Consultar la disponibilidad de un implemento exige desplazarse físicamente a cada facultad.
- Los eventos se difunden sobre todo por correo institucional, cuyo alcance se limita por la saturación de mensajes.

### Objetivo y alcance

Centralizar y facilitar el acceso a actividades recreativas, culturales y deportivas, con administración de inventarios, visualización de disponibilidad de implementos, y gestión y consulta de eventos. Incluye registro, actualización, consulta, seguimiento y reserva de implementos y eventos, con control de acceso basado en roles. El préstamo de implementos se limita a la comunidad universitaria mediante autenticación institucional, mientras que usuarios externos pueden suscribirse a notificaciones de eventos sin autenticarse.

### Roles

| Rol | Funciones principales |
|---|---|
| Administrador del sistema | Gestiona usuarios y roles, supervisa eventos e implementos, y puede bloquear cuentas por actividades indebidas. |
| Administrador de eventos | Crea, modifica, publica y cancela los eventos que haya creado. |
| Administrador de implementos | Registra y gestiona los implementos de su facultad, actualiza su disponibilidad y gestiona las reservas. |
| Miembro de la comunidad | Usuario con correo `@unal.edu.co`. Consulta implementos y eventos, reserva implementos y se suscribe a notificaciones. |

### Funcionalidades destacadas

- Autenticación con cuentas institucionales de Google (`@unal.edu.co`).
- Visualización de eventos publicados en un mapa del campus.
- Filtrado de eventos por categoría y estado, y ordenamiento por fecha.
- Catálogo de implementos filtrable por categoría y consultable por facultad.
- Reserva temporal de implementos y solicitud formal de préstamo, con aviso de descargo de responsabilidad.
- Suscripción a eventos, incluso sin registro (mediante correo electrónico), y notificaciones por correo ante cambios.
- Eventos en borrador antes de su publicación; los eventos publicados no se eliminan, solo se cancelan.
- Registro de auditoría de las acciones del módulo de implementos.

### Entidades principales

- **Usuario (Miembro de la comunidad):** nombre completo, correo institucional, facultad, rol, estado (Activo / Sancionado) y fecha de registro.
- **Evento:** nombre, descripción, lugar, fecha y hora de inicio y fin, organizador, categoría, imagen, si está publicado, y estado (Programado / En Curso / Finalizado / Cancelado).
- **Implemento:** nombre, categoría, facultad propietaria, descripción, imagen, tiempo de uso y estado (Disponible / No Disponible / Reservado / Prestado).

### Reglas de negocio

- **RN-01:** solo se aceptan registros con correo `@unal.edu.co`.
- **RN-02:** una reserva dura 10 minutos; pasado ese tiempo el implemento vuelve automáticamente a Disponible.
- **RN-03:** si un implemento se entrega dañado, el usuario pasa a Sancionado (no puede reservar hasta pagar la multa y el reemplazo o arreglo) y el implemento pasa a No Disponible.
- **RN-04:** solo un Administrador de Implementos puede manipular implementos de la facultad registrada en su perfil.
- **RN-05:** cada miembro puede tener como máximo un implemento Reservado u Ocupado a la vez.

### Requerimientos no funcionales relevantes

- **Seguridad:** Firebase Authentication con cuentas de Google; cierre de sesión tras 10 minutos de inactividad.
- **Rendimiento:** consultas de disponibilidad y carga del mapa en menos de 2 segundos; caché para consultas de alta frecuencia.
- **Escalabilidad:** contenedores Docker separados para frontend, backend y base de datos; arquitectura modular que permita agregar módulos sin afectar los existentes.
- **Disponibilidad:** manejo de errores controlados sin pérdida de información y backups automáticos semanales de la base de datos.
- **Mantenibilidad y calidad:** PEP8, Clean Code, patrones de diseño, documentación con docstrings, pruebas unitarias y linters.
- **Interoperabilidad:** Google Maps Platform para el mapa y un servicio externo para el envío de correos.
- **Concurrencia:** evitar condiciones de carrera (dos personas no pueden reservar el mismo implemento) y soportar cerca de 400 usuarios concurrentes, 24 horas al día durante el periodo académico.
- **Usabilidad y portabilidad:** interfaz responsive, acceso a cualquier funcionalidad principal en máximo 5 clics y soporte para las dos últimas versiones de Google Chrome.

## Architecture

## How to run