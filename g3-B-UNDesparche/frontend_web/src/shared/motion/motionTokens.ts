import { Variants, Transition } from 'framer-motion';

/**
 * Design Motion Tokens based on Jakub Krehel & Emil Kowalski principles:
 * - Enter recipe: Opacity (0 -> 1) + TranslateY (12px -> 0) + Blur (4px -> 0px)
 * - Subtle exit: Smaller translate (-6px), blur (2px)
 * - Spring easing: bounce: 0 (professional, refined, no overshoot)
 * - Speed: sub-300ms for UI actions, 350-450ms for larger entrance reveals
 * - Full reduced-motion awareness
 */

export const SPRING_PRODUCTION: Transition = {
  type: 'spring',
  duration: 0.4,
  bounce: 0,
};

export const SPRING_FAST: Transition = {
  type: 'spring',
  duration: 0.25,
  bounce: 0,
};

export const SPRING_MODAL: Transition = {
  type: 'spring',
  duration: 0.35,
  bounce: 0,
};

// Fade up with blur (Jakub signature enter recipe)
export const fadeUpBlur: Variants = {
  initial: {
    opacity: 0,
    y: 12,
    filter: 'blur(4px)',
  },
  animate: {
    opacity: 1,
    y: 0,
    filter: 'blur(0px)',
    transition: SPRING_PRODUCTION,
  },
  exit: {
    opacity: 0,
    y: -8,
    filter: 'blur(3px)',
    transition: { duration: 0.2, ease: [0.32, 0.72, 0, 1] },
  },
};

// Simple fade
export const fadeIn: Variants = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: 0.25, ease: 'easeOut' } },
  exit: { opacity: 0, transition: { duration: 0.18, ease: 'easeIn' } },
};

// Staggered list / grid container
export const staggerContainer: Variants = {
  initial: {},
  animate: {
    transition: {
      staggerChildren: 0.05,
      delayChildren: 0.04,
    },
  },
};

// Staggered item inside container
export const staggerItem: Variants = {
  initial: {
    opacity: 0,
    y: 14,
    filter: 'blur(4px)',
  },
  animate: {
    opacity: 1,
    y: 0,
    filter: 'blur(0px)',
    transition: {
      type: 'spring',
      duration: 0.45,
      bounce: 0,
    },
  },
  exit: {
    opacity: 0,
    y: -6,
    filter: 'blur(2px)',
    transition: { duration: 0.18 },
  },
};

// Modal backdrop & dialog
export const modalBackdropVariants: Variants = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: 0.22, ease: [0.16, 1, 0.3, 1] } },
  exit: { opacity: 0, transition: { duration: 0.18, ease: 'easeOut' } },
};

export const modalDialogVariants: Variants = {
  initial: {
    opacity: 0,
    scale: 0.95,
    y: 10,
    filter: 'blur(4px)',
  },
  animate: {
    opacity: 1,
    scale: 1,
    y: 0,
    filter: 'blur(0px)',
    transition: SPRING_MODAL,
  },
  exit: {
    opacity: 0,
    scale: 0.98,
    y: 6,
    filter: 'blur(2px)',
    transition: { duration: 0.18, ease: [0.32, 0.72, 0, 1] },
  },
};

// Drawer backdrop & slide
export const drawerBackdropVariants: Variants = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: 0.22 } },
  exit: { opacity: 0, transition: { duration: 0.18 } },
};

export const drawerPanelVariants: Variants = {
  initial: { x: '-100%' },
  animate: {
    x: 0,
    transition: { type: 'spring', duration: 0.38, bounce: 0 },
  },
  exit: {
    x: '-100%',
    transition: { type: 'spring', duration: 0.28, bounce: 0 },
  },
};

// Tab view content switch
export const tabContentVariants: Variants = {
  initial: { opacity: 0, y: 8, filter: 'blur(3px)' },
  animate: {
    opacity: 1,
    y: 0,
    filter: 'blur(0px)',
    transition: SPRING_PRODUCTION,
  },
  exit: {
    opacity: 0,
    y: -6,
    filter: 'blur(2px)',
    transition: { duration: 0.15 },
  },
};

// Button tactile press micro-interaction (Emil Kowalski)
export const buttonTapMotion = {
  whileTap: { scale: 0.97 },
  whileHover: { y: -1 },
  transition: { duration: 0.12 },
};

export const cardHoverMotion = {
  whileHover: { y: -3 },
  transition: { type: 'spring', duration: 0.25, bounce: 0 },
};
