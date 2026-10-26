import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

interface FaqItem {
  question: string;
  answer: string;
}

const FAQ_ITEMS: FaqItem[] = [
  {
    question: '¿Qué pasa exactamente si no llego en los 10 minutos a reclamar mi implemento?',
    answer:
      'El sistema cancela automáticamente la reserva y libera el implemento para que cualquier otro compañero presente en el Punto de Bienestar pueda aprovecharlo. No hay sanciones económicas ni bloqueos en tu primera ocasión: la regla busca simplemente que los balones y raquetas no se queden apartados mientras alguien espera en la cancha.',
  },
  {
    question: '¿Quién puede publicar eventos o torneos en la plataforma?',
    answer:
      'La publicación y gestión oficial de eventos está a cargo exclusivamente de los Administradores de Eventos autorizados. Toda la comunidad UNAL puede explorar la cartelera completa, consultar detalles y ubicaciones georreferenciadas, y suscribirse libremente para recibir avisos y recordatorios por correo.',
  },
  {
    question: '¿El préstamo de implementos o la asistencia a los eventos tiene algún costo?',
    answer:
      'Cero pesos. Tanto el uso de los implementos deportivos en los Puntos de Bienestar universitario como la asistencia a las actividades listadas son 100% gratuitos para la comunidad UNAL.',
  },
  {
    question: '¿Qué documento necesito presentar en el Punto de Bienestar para reclamar un implemento?',
    answer:
      'Es indispensable presentar tu carné estudiantil vigente en el Punto de Bienestar de la facultad para verificar tu identidad y formalizar el préstamo.',
  },
  {
    question: '¿Cómo puedo cancelar las notificaciones de un evento al que me inscribí?',
    answer:
      'Cada correo de recordatorio incluye un enlace directo para cancelar tu suscripción con un solo clic, sin formularios extensos ni necesidad de recordar contraseñas.',
  },
];

export function FaqSection(): React.JSX.Element {
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  const toggleQuestion = (index: number) => {
    setOpenIndex((prev) => (prev === index ? null : index));
  };

  // Structured schema for search engines (FAQPage)
  const faqSchema = {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: FAQ_ITEMS.map((item) => ({
      '@type': 'Question',
      name: item.question,
      acceptedAnswer: {
        '@type': 'Answer',
        text: item.answer,
      },
    })),
  };

  return (
    <section
      aria-label="Preguntas frecuentes"
      className="relative overflow-hidden bg-white py-16 dark:bg-[#080E14] sm:py-24 border-b border-black/[0.06] dark:border-white/[0.06]"
    >
      {/* Ambient diffuse glow */}
      <div
        className="pointer-events-none absolute -top-24 right-1/4 h-[420px] w-[520px] rounded-full bg-[#00D592]/8 blur-[140px] dark:bg-[#00D592]/7"
        aria-hidden="true"
      />

      {/* Schema Injection */}
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(faqSchema) }}
      />

      <div className="relative mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-[680px] mx-auto">
          <div className="inline-flex items-center gap-2 rounded-full bg-neutral-100 dark:bg-neutral-800/80 px-3.5 py-1 text-xs font-semibold text-neutral-700 dark:text-neutral-300 border border-neutral-200 dark:border-neutral-700">
            <span className="h-1.5 w-1.5 rounded-full bg-[#00D592]" aria-hidden="true" />
            <span>Resolución de Dudas</span>
          </div>
          <h2 className="mt-4 font-display font-bold text-3xl tracking-tight text-neutral-900 dark:text-white sm:text-4xl text-balance">
            Todo lo que necesitas saber antes de tu primer parche
          </h2>
          <p className="mt-3 text-base text-neutral-600 dark:text-neutral-400 text-pretty">
            Respuestas claras y sin rodeos sobre los Puntos de Bienestar, la regla de los 10 minutos y el acceso al campus.
          </p>
        </div>

        <div className="mt-12 space-y-3.5">
          {FAQ_ITEMS.map((item, index) => {
            const isOpen = openIndex === index;
            const questionId = `faq-question-${index}`;
            const answerId = `faq-answer-${index}`;

            return (
              <div
                key={item.question}
                className="overflow-hidden rounded-2xl border border-black/[0.07] dark:border-white/[0.08] bg-white/75 dark:bg-[#0E1720]/80 backdrop-blur-xl shadow-[0_4px_20px_rgb(0,0,0,0.03)] dark:shadow-[0_4px_20px_rgb(0,0,0,0.2)] transition-all duration-200 hover:border-[#00D592]/30"
              >
                <button
                  type="button"
                  id={questionId}
                  aria-expanded={isOpen}
                  aria-controls={answerId}
                  onClick={() => toggleQuestion(index)}
                  className="flex w-full items-center justify-between gap-4 p-5 text-left transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
                >
                  <span className="font-display font-semibold text-base text-neutral-900 dark:text-white leading-snug">
                    {item.question}
                  </span>
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-black/[0.04] dark:bg-white/[0.06] text-neutral-700 dark:text-neutral-300">
                    <svg
                      className={`h-4 w-4 transition-transform duration-200 ease-out ${
                        isOpen ? 'rotate-180' : ''
                      }`}
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      aria-hidden="true"
                    >
                      <polyline points="6 9 12 15 18 9" />
                    </svg>
                  </span>
                </button>

                <AnimatePresence initial={false}>
                  {isOpen && (
                    <motion.div
                      id={answerId}
                      role="region"
                      aria-labelledby={questionId}
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      transition={{ duration: 0.25, ease: [0.32, 0.72, 0, 1] }}
                      className="overflow-hidden"
                    >
                      <div className="px-5 pb-5 pt-1 text-sm text-neutral-600 dark:text-neutral-300 leading-relaxed text-pretty border-t border-black/[0.05] dark:border-white/[0.06]">
                        {item.answer}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
