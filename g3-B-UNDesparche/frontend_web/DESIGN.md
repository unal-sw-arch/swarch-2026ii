# Guía de Diseño y Tokens UI · UNDesparche

Este documento define las reglas de diseño visual, tipografía, movimiento y principios de artesanía para mantener la interfaz elegante, coherente con el estilo de HeroUI Mint + Varela Round y **libre de patrones genéricos generados por IA**.

---

## 1. Filosofía e Identidad del Campus

- **Arraigo Estudiantil UNAL**: Los textos, ejemplos y microcopias reflejan la realidad de la Ciudad Universitaria (Sede Bogotá):
  - Lugares icónicos: Canchas de Minas (Ed. 411), Edificio de Ciencia y Tecnología CyT (Ed. 453), Plaza Santander / Ché (Ed. 101/102), El Frepe / Jardín Freud (Ed. 224), La Perla y Concha Acústica.
  - Dinámicas reales: La "Regla de los 10 minutos" para reclamar implementos en taquillas de bienestar, recesos entre clases de 2 horas, torneos relámpago interfacultades.
- **Tono de Voz**: Cercano, sobrio, estudiantil e institucional. Sin lenguaje publicitario inflado o falso entusiasmo de plantilla SaaS.

---

## 2. Tipografía y Jerarquía

Para evitar el aspecto infantil o caricaturesco de aplicar fuentes redondeadas a párrafos y tablas, la tipografía está desacoplada:

| Uso | Tipografía | Clase / Variable | Justificación |
|---|---|---|---|
| **Titulares & Display** | **Varela Round** | `font-display` (`--font-display`) | Identidad amigable, orgánica y distintiva de la marca. |
| **Cuerpo, Tablas, Formularios y Datos** | **Plus Jakarta Sans** | `font-sans` (`--font-sans`) | Máxima legibilidad, contraste técnico y números tabulares claros (`tabular-nums`). |

### Reglas Tipográficas:
- **Titulares Sólidos**: Usar colores de alto contraste (`text-neutral-900` en modo claro, `text-white` en modo oscuro).
- **Prohibido el gradiente de texto**: Nunca usar `bg-clip-text text-transparent bg-gradient-to-r` en encabezados H1/H2 principales; degrada la legibilidad y delata diseño de plantilla de IA.
- **Sin Eyebrows / Kickers redundantes**: Si el título es fuerte, no necesita un mini-badge en mayúsculas arriba anunciándolo.

---

## 3. Paleta de Colores y Tokens HeroUI Mint

El color verde menta es un **acento quirúrgico**, no el relleno de toda la interfaz.

- **Mint Primario**: `#00D592` (HeroUI Mint base)
- **Mint Enfoque / Texto Claro**: `#00875A` (contraste AA para texto en fondos claros)
- **Mint Modo Oscuro**: `#00F5A0` (contraste alto sobre negros profundos `#080E14`)
- **Bordes de Tarjetas**:
  - Claro: `border: 1px solid rgba(0, 0, 0, 0.08)` (Hairline neutro y limpio)
  - Oscuro: `border: 1px solid rgba(255, 255, 255, 0.08)`
  - *Evitar*: Bordes permanentemente verdes en todos los contenedores; el acento verde solo aparece en estados activos o `hover:border-[#00D592]/30`.
- **Radio de Esquinas (`border-radius`)**:
  - Tarjetas y Contenedores: `0.875rem` (14px) con `.heroui-card`
  - Botones y Chips: `rounded-xl` (12px) o `rounded-full` para píldoras de estado
  - No usar `rounded-3xl` indiscriminadamente en tarjetas de datos densas.

---

## 4. Principios de Movimiento (Framer Motion)

Basados en las guías de Emil Kowalski y Jakub Krehel:

- **Ecuación de Easing**: Spring de alta precisión sin rebote exagerado:
  ```ts
  export const SPRING_PRODUCTION = {
    type: 'spring',
    duration: 0.4,
    bounce: 0,
  };
  ```
- **Receta de Entrada**: Opacidad + Y-Translate (12px a 0) + Filtro Blur (4px a 0px):
  ```ts
  export const fadeUpBlur = {
    initial: { opacity: 0, y: 12, filter: 'blur(4px)' },
    animate: { opacity: 1, y: 0, filter: 'blur(0px)', transition: SPRING_PRODUCTION },
    exit: { opacity: 0, y: -8, filter: 'blur(3px)' }
  };
  ```
- **Respuesta Táctil en Botones**:
  - `whileTap={{ scale: 0.97 }}`
  - `whileHover={{ y: -1 }}` (o `scale: 1.01`)

---

## 5. Checklist Anti-IA (Craft Floor)

1. **[X] Sin badges con puntos pulsantes (`animate-pulse`)**: Sustituidos por indicadores funcionales de estado en vivo con propósito.
2. **[X] Sin el trío de 3 tarjetas idénticas de SaaS**: Usar diagramaciones asimétricas editoriales donde una característica clave recibe jerarquía visual dominante.
3. **[X] Sin ribbons de emojis flotantes**: Enlaces de descubrimiento con nombres reales de facultades y edificios.
4. **[X] Sin métricas infladas sin contexto**: Sustituidas por widgets interactivos en vivo (*Pulso del Campus* con pestañas de Eventos, Implementos y Espacios).
