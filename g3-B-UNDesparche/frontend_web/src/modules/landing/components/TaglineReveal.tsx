import React, { useRef } from 'react';
import { useScroll, useTransform, motion, MotionValue } from 'framer-motion';

interface WordProps {
  children: string;
  progress: MotionValue<number>;
  range: [number, number];
}

function Word({ children, progress, range }: WordProps) {
  const opacity = useTransform(progress, range, [0.28, 1]);
  return (
    <span className="relative inline-block mr-[0.28em]">
      <span className="absolute inset-0 text-neutral-300/80 dark:text-neutral-700/80 select-none" aria-hidden="true">
        {children}
      </span>
      <motion.span style={{ opacity }} className="relative text-neutral-900 dark:text-white">
        {children}
      </motion.span>
    </span>
  );
}

export function TaglineReveal(): React.JSX.Element {
  const containerRef = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({
    target: containerRef,
    offset: ['start 0.85', 'end 0.35'],
  });

  const phrase = 'El campus no se queda quieto. Conéctate con todo lo que pasa en la Nacho.';
  const words = phrase.split(' ');

  return (
    <section
      ref={containerRef}
      aria-label="Manifiesto del campus"
      className="relative bg-white dark:bg-[#080E14] py-24 sm:py-32 px-4 sm:px-6 lg:px-8 border-b border-neutral-100 dark:border-neutral-800"
    >
      <div className="mx-auto max-w-4xl text-center">
        <div className="mb-6 inline-flex items-center gap-2 rounded-full bg-neutral-100 dark:bg-neutral-800/80 px-3.5 py-1 text-xs font-semibold text-neutral-600 dark:text-neutral-300 border border-neutral-200 dark:border-neutral-700">
          <span className="h-1.5 w-1.5 rounded-full bg-[#00875A] dark:bg-[#00D592]" aria-hidden="true" />
          <span>Dinámica real en la Ciudad Universitaria</span>
        </div>

        <h2 className="font-display font-bold text-3xl sm:text-5xl lg:text-6xl tracking-tight leading-tight sm:leading-[1.16] text-balance">
          {words.map((word, i) => {
            const start = i / words.length;
            const end = start + 1 / words.length;
            return (
              <Word key={`${word}-${i}`} progress={scrollYProgress} range={[start, end]}>
                {word}
              </Word>
            );
          })}
        </h2>

        <p className="mt-8 mx-auto max-w-xl text-sm sm:text-base text-neutral-500 dark:text-neutral-400 text-pretty">
          Diseñado para el ritmo entre clases: reservas con la regla de diez minutos en los Puntos de Bienestar y la cartelera completa de parches de la Sede Bogotá.
        </p>
      </div>
    </section>
  );
}
