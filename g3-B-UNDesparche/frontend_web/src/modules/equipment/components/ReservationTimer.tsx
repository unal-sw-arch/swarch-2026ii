import React, { useState, useEffect } from 'react';

export interface ReservationTimerProps {
  expirationTime: string | Date;
  onExpire?: () => void;
}

export function ReservationTimer({
  expirationTime,
  onExpire,
}: ReservationTimerProps): React.JSX.Element {
  const [timeLeftMs, setTimeLeftMs] = useState<number>(() => {
    const exp = new Date(expirationTime).getTime();
    return Math.max(0, exp - Date.now());
  });

  useEffect(() => {
    const interval = setInterval(() => {
      const exp = new Date(expirationTime).getTime();
      const diff = exp - Date.now();
      if (diff <= 0) {
        setTimeLeftMs(0);
        clearInterval(interval);
        onExpire?.();
      } else {
        setTimeLeftMs(diff);
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [expirationTime, onExpire]);

  const totalSeconds = Math.floor(timeLeftMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  const formattedTime = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;

  const isExpired = timeLeftMs <= 0;
  const isUrgent = timeLeftMs > 0 && timeLeftMs < 2 * 60 * 1000; // Menos de 2 minutos

  if (isExpired) {
    return (
      <div
        role="alert"
        className="heroui-card p-6 text-center border-amber-400/40 bg-amber-500/10 text-amber-900 dark:text-amber-200"
      >
        <span className="material-symbols-outlined text-4xl text-[#F5A623]" aria-hidden="true">
          timer_off
        </span>
        <h4 className="mt-2 font-display font-bold text-lg">
          Tiempo de recogida expirado
        </h4>
        <p className="mt-1.5 text-xs text-amber-800/90 dark:text-amber-300">
          La ventana de 10 minutos ha finalizado. El implemento puede haber sido liberado. Acércate a la oficina de deportes o contacta al administrador de la facultad.
        </p>
      </div>
    );
  }

  return (
    <div
      role="timer"
      aria-live="polite"
      className={`heroui-card p-6 text-center transition-all duration-300 ${
        isUrgent
          ? 'border-red-500/40 bg-red-500/10 text-red-900 dark:text-red-200 shadow-lg shadow-red-500/10'
          : 'border-[#00D592]/40 bg-gradient-to-b from-[#00D592]/15 via-white/80 to-transparent dark:via-[#0E1720]/80 text-gray-900 dark:text-white shadow-xl shadow-[#00D592]/10'
      }`}
    >
      <div className="flex items-center justify-center gap-2">
        <span
          className={`material-symbols-outlined animate-pulse text-2xl ${
            isUrgent ? 'text-[#FF4D4F]' : 'text-[#00D592]'
          }`}
          aria-hidden="true"
        >
          hourglass_top
        </span>
        <span className="font-bold text-xs uppercase tracking-wider text-[#00875A] dark:text-[#00F5A0]">
          Tiempo restante para reclamar en taquilla con carné
        </span>
      </div>

      <div className="mt-3 font-display font-extrabold text-5xl sm:text-6xl tabular-nums tracking-tight text-gray-900 dark:text-white">
        {formattedTime}
      </div>

      <div className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-[#00D592]/15 px-3.5 py-1 text-xs font-semibold text-[#00875A] dark:text-[#00F5A0] border border-[#00D592]/30">
        <span className="h-2 w-2 rounded-full bg-[#00D592] animate-ping" />
        Lleva tu carné físico o digital UNAL al centro deportivo
      </div>
    </div>
  );
}
