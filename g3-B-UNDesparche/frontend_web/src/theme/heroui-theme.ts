/**
 * Tokens oficiales del tema Mint de HeroUI con tipografía Varela Round
 * Basado en las variables extraídas de heroui.com/themes:
 * - hue: 155, lightness: 0.82, chroma: 0.12, base: 0.0015
 * - fontFamily: "Varela Round", sans-serif
 * - radius: "medium" (0.75rem / 12px)
 * - formRadius: "large" (1rem / 16px / rounded-full)
 */

export const themeTokens = {
  colors: {
    // Mint Palette (HeroUI official preset)
    mint: {
      50: '#F0FDF8',
      100: '#DCFCEE',
      200: '#B6F7DA',
      300: '#7EEDBF',
      400: '#3DDC9F',
      500: '#00D592', // Principal HeroUI Mint
      600: '#00BA7C',
      700: '#009262',
      800: '#04724E',
      900: '#065E42',
      950: '#023524',
    },
    // Semánticos HeroUI
    primary: '#00D592',
    primaryHover: '#00BA7C',
    primaryGlow: '#00F5A0',
    primaryForeground: '#032B1D', // Verde bosque profundo de alto contraste

    secondary: '#1A2E3B',
    secondaryForeground: '#E6FAF3',

    // Superficies y Canvas (Dark y Light HeroUI Mint)
    canvasLight: '#F8FAF9',
    surfaceLight: '#FFFFFF',
    surfaceSubtleLight: '#F0F5F2',
    borderLight: 'rgba(0, 213, 146, 0.20)',

    canvasDark: '#080E14',
    surfaceDark: '#0E1720',
    surfaceSubtleDark: '#14202C',
    borderDark: 'rgba(0, 213, 146, 0.18)',

    // Estados funcionales
    danger: '#FF4D4F',
    dangerLight: '#FFEBEB',
    warning: '#F5A623',
    warningLight: '#FFF8E6',
    success: '#00D592',
    successLight: '#DCFCEE',

    // Textos
    textPrimaryLight: '#0E1720',
    textSecondaryLight: '#4B5563',
    textPrimaryDark: '#F3FAF6',
    textSecondaryDark: '#94A3B8',
  },
  radii: {
    sm: '0.375rem',
    md: '0.75rem',    // radius: medium HeroUI
    lg: '1rem',       // formRadius: large
    xl: '1.25rem',
    '2xl': '1.5rem',
    '3xl': '2rem',
    full: '9999px',
  },
  fonts: {
    display: '"Varela Round", sans-serif',
    sans: '"Varela Round", "Plus Jakarta Sans", sans-serif',
    mono: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
  },
} as const;

export type ThemeTokens = typeof themeTokens;
