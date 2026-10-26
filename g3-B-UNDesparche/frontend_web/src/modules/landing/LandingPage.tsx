import React, { useEffect, useState, useRef } from 'react';
import { Link } from 'react-router';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import { getEvents } from '../../api/events';
import { CampusEvent, EVENT_CATEGORY_LABELS } from '../../types/event';
import { CampusMap } from '../map/components/CampusMap';
import { TaglineReveal } from './components/TaglineReveal';
import { FaqSection } from './components/FaqSection';
import { fadeUpBlur, SPRING_PRODUCTION } from '../../shared/motion/motionTokens';

type PulseTab = 'events' | 'spots';
type CampusSpotId = 'minas' | 'cyt' | 'freud' | 'plaza_che';

interface SpotPulseData {
  id: CampusSpotId;
  label: string;
  shortName: string;
  building: string;
  events: Array<{
    category: string;
    time: string;
    title: string;
    detail: string;
  }>;
  equipment?: Array<{
    isCountdown?: boolean;
    badge: string;
    countOrTime: string;
    title: string;
    place: string;
  }>;
  spots: Array<{
    name: string;
    status: string;
    statusColor: string;
    description: string;
  }>;
}

const SPOT_PULSE_REGISTRY: Record<CampusSpotId, SpotPulseData> = {
  minas: {
    id: 'minas',
    label: '⚽ Canchas de Minas (Ed. 411)',
    shortName: 'Canchas de Minas',
    building: 'Ed. 411 · Minas',
    events: [
      {
        category: 'Mundialito UNAL',
        time: 'Hoy · 5:30 PM',
        title: 'Fecha 3 de Microfútbol Interfacultades',
        detail: 'Canchas de Minas (Ed. 411) · Entrada libre',
      },
      {
        category: 'Torneo Relámpago',
        time: 'Mañana · 2:00 PM',
        title: 'Encuentro de Voleibol y Fútbol Sala',
        detail: 'Polideportivo de Minas · 8 equipos en cancha',
      },
    ],
    equipment: [
      {
        isCountdown: true,
        badge: 'Turno activo · Regla de 10 min',
        countOrTime: '09:34',
        title: 'Balón de Fútbol Golty Origen #5',
        place: 'Punto de Bienestar Ingeniería (Ed. 401)',
      },
      {
        badge: 'Disponibilidad inmediata',
        countOrTime: '3 disponibles',
        title: 'Balón de Microfútbol Golty Futsal',
        place: 'Punto de Bienestar Canchas de Minas (Ed. 411)',
      },
    ],
    spots: [
      {
        name: 'Canchas de Minas (Ed. 411)',
        status: 'Alta actividad deportiva',
        statusColor: 'text-[#00875A] dark:text-[#00F5A0]',
        description: 'Canchas de microfútbol, baloncesto y polideportivo. Punto neurálgico de torneos estudiantiles.',
      },
    ],
  },
  cyt: {
    id: 'cyt',
    label: '🏓 Ping Pong CyT (Ed. 453)',
    shortName: 'Edificio CyT',
    building: 'Ed. 453 · CyT',
    events: [
      {
        category: 'Torneo CyT',
        time: 'Hoy · 4:00 PM',
        title: 'Torneo Relámpago de Ping Pong CyT',
        detail: 'Ed. Ciencia y Tecnología (453) · 32 inscritos',
      },
      {
        category: 'Comunidad Académica',
        time: 'Jueves · 3:00 PM',
        title: 'Círculo de Estudio en Algoritmia y Lógica',
        detail: 'Sala 204 CyT · Entrada libre estudiantes UNAL',
      },
    ],
    equipment: [
      {
        badge: 'Disponibilidad inmediata',
        countOrTime: '6 disponibles',
        title: 'Kit de Raquetas Ping Pong Stiga Pro + Pelotas',
        place: 'Mesas CyT Primer Piso (Ed. 453)',
      },
      {
        badge: 'Disponibilidad inmediata',
        countOrTime: '4 disponibles',
        title: 'Juego de Ajedrez con Reloj Digital DGT',
        place: 'Sala de Estudio CyT (Ed. 453)',
      },
    ],
    spots: [
      {
        name: 'CyT - Ciencia y Tecnología (Ed. 453)',
        status: 'Mesas activas',
        statusColor: 'text-[#00875A] dark:text-[#00F5A0]',
        description: 'Mesas de ping pong en el hall central y salas de trabajo individual con tomas eléctricas.',
      },
    ],
  },
  freud: {
    id: 'freud',
    label: '🌱 Jardín Freud',
    shortName: 'Jardín Freud',
    building: 'Ed. 224 · Humanas',
    events: [
      {
        category: 'Desparche Acústico',
        time: 'Hoy · 4:30 PM',
        title: 'Encuentro de Guitarra y Música Libre',
        detail: 'Jardín Freud (Ed. 224) · Trae tu instrumento',
      },
      {
        category: 'Círculo Cultural',
        time: 'Viernes · 1:00 PM',
        title: 'Picnic Literario y Tertulia de Filosofía',
        detail: 'Zona verde frente a Ciencias Humanas',
      },
    ],
    equipment: [
      {
        badge: 'Disponibilidad inmediata',
        countOrTime: '3 disponibles',
        title: 'Disco Volador Frisbee Ultimate UNAL',
        place: 'Punto de Bienestar Ciencias Humanas (Ed. 224)',
      },
      {
        badge: 'Disponibilidad inmediata',
        countOrTime: '2 disponibles',
        title: 'Juego de Mesa Catan Clásico',
        place: 'Punto de Bienestar Ciencias Humanas',
      },
    ],
    spots: [
      {
        name: 'Jardín Freud (Ed. 224)',
        status: 'Ambiente tranquilo',
        statusColor: 'text-neutral-500',
        description: 'Zona verde arbolada frente a Ciencias Humanas. Espacio preferido para descanso, lectura y charlas.',
      },
    ],
  },
  plaza_che: {
    id: 'plaza_che',
    label: '📍 Plaza Ché',
    shortName: 'Plaza Ché (Santander)',
    building: 'Ed. 102 · Biblioteca',
    events: [
      {
        category: 'Cine Foro',
        time: 'Hoy · 6:00 PM',
        title: 'Proyección Audiovisual al Aire Libre',
        detail: 'Plaza Ché frente a Auditorio León de Greiff',
      },
      {
        category: 'Feria Comunitaria',
        time: 'Mañana · 11:00 AM',
        title: 'Muestra Autogestionada de Arte Estudiantil',
        detail: 'Alrededores de la Plaza Ché (Ed. 102)',
      },
    ],
    equipment: [
      {
        badge: 'Disponibilidad inmediata',
        countOrTime: '1 disponible',
        title: 'Megáfono y Kit de Audio para Actividades',
        place: 'Punto de Bienestar Central',
      },
      {
        badge: 'En préstamo activo',
        countOrTime: 'Ocupado',
        title: 'Ajedrez Gigante para Plaza Abierta',
        place: 'Custodia Bienestar Sede',
      },
    ],
    spots: [
      {
        name: 'Plaza Ché (Santander - Ed. 102)',
        status: 'Alta concurrencia',
        statusColor: 'text-[#00875A] dark:text-[#00F5A0]',
        description: 'Frente a la Biblioteca Central (Ed. 102). Epicentro histórico de la vida universitaria y encuentros masivos.',
      },
    ],
  },
};

export function LandingPage(): React.JSX.Element {
  const { isAuthenticated, signInWithGoogle } = useAuth();
  const [upcomingEvents, setUpcomingEvents] = useState<CampusEvent[]>([]);
  const [mapEvents, setMapEvents] = useState<CampusEvent[]>([]);
  const [selectedMapEvent, setSelectedMapEvent] = useState<CampusEvent | null>(null);
  const [loadingEvents, setLoadingEvents] = useState<boolean>(true);
  const [activePulseTab, setActivePulseTab] = useState<PulseTab>('events');
  const [selectedSpot, setSelectedSpot] = useState<CampusSpotId | null>(null);
  const carouselRef = useRef<HTMLDivElement>(null);



  const getCampusTag = (place: string) => {
    const lower = place.toLowerCase();
    if (lower.includes('453') || lower.includes('cyt') || lower.includes('tecnolog')) return 'ED. 453 · CYT';
    if (lower.includes('411') || lower.includes('minas')) return 'ED. 411 · MINAS';
    if (lower.includes('401') || lower.includes('ingenier')) return 'ED. 401 · ING';
    if (lower.includes('102') || lower.includes('ché') || lower.includes('che') || lower.includes('biblioteca')) return 'ED. 102 · PLAZA CHÉ';
    if (lower.includes('224') || lower.includes('freud')) return 'ED. 224 · FREUD';
    if (lower.includes('101') || lower.includes('león') || lower.includes('leon')) return 'ED. 101 · EL LEÓN';
    const match = place.match(/Ed(?:ificio|\.)?\s*(\d+)/i);
    if (match) return `ED. ${match[1]} · CAMPUS`;
    return place.length > 18 ? `${place.slice(0, 15).toUpperCase()}…` : place.toUpperCase();
  };

  const isEventToday = (datetimeStr: string) => {
    try {
      const evDate = new Date(datetimeStr);
      const today = new Date();
      return (
        evDate.getDate() === today.getDate() &&
        evDate.getMonth() === today.getMonth() &&
        evDate.getFullYear() === today.getFullYear()
      );
    } catch {
      return false;
    }
  };

  useEffect(() => {
    const fetchUpcoming = async () => {
      try {
        const allEvents = await getEvents({ published: true });
        const now = new Date().toISOString();
        let fullList = allEvents.filter((ev) => ev.published !== false && ev.datetime_end >= now);
        if (fullList.length === 0 && allEvents.length > 0) {
          fullList = allEvents.filter((ev) => ev.published !== false);
        }
        setMapEvents(fullList.length > 0 ? fullList : allEvents);
        setUpcomingEvents(fullList.slice(0, 8));
      } catch (err) {
        console.warn('Error al cargar próximos eventos para el landing:', err);
      } finally {
        setLoadingEvents(false);
      }
    };
    fetchUpcoming();
  }, []);

  const scrollCarousel = (direction: 'left' | 'right') => {
    if (carouselRef.current) {
      const scrollAmount = direction === 'left' ? -340 : 340;
      carouselRef.current.scrollBy({ left: scrollAmount, behavior: 'smooth' });
    }
  };

  return (
    <div className="flex flex-col font-sans">
      {/* 1. HERO SECTION: SOLID DISPLAY TYPOGRAPHY + CAMPUS PULSE BOARD */}
      <section className="relative overflow-hidden bg-white dark:bg-[#080E14] px-4 pt-24 pb-20 sm:px-6 sm:pt-32 sm:pb-28 lg:px-8 lg:pt-36">
        {/* Campus Hero Photographic Background: Crisp and natural in light mode, atmospheric in dark mode */}
        <div className="absolute inset-0 z-0 pointer-events-none select-none overflow-hidden">
          <img
            src="/hero-unal.jpg"
            alt="Campus Universidad Nacional de Colombia - Plaza Central y Auditorio León de Greiff"
            className="h-full w-full object-cover object-center opacity-100 dark:opacity-85 scale-[1.02] filter contrast-[1.05] dark:brightness-[0.85] dark:contrast-[1.12] transition-opacity duration-500"
            loading="eager"
          />
          {/* Atmospheric gradient strictly for dark mode (no difuminado in light mode) */}
          <div className="hidden dark:block absolute inset-0 bg-gradient-to-r from-[#080E14]/90 via-[#080E14]/65 via-45% to-transparent" />
          <div className="hidden dark:block absolute bottom-0 inset-x-0 h-32 bg-gradient-to-t from-[#080E14] to-transparent" />
        </div>

        <div className="relative z-10 mx-auto max-w-7xl">
          <div className="grid grid-cols-1 items-start gap-12 lg:grid-cols-12 lg:gap-16">
            
            {/* Left Column: Direct, human-crafted value proposition in a frosted glass panel */}
            <motion.div
              variants={fadeUpBlur}
              initial="initial"
              animate="animate"
              className="lg:col-span-7 rounded-3xl bg-white/85 dark:bg-transparent backdrop-blur-xl dark:backdrop-blur-none p-6 sm:p-8 lg:p-10 border border-black/[0.08] dark:border-transparent shadow-xl dark:shadow-none"
            >
              {/* Context Pill (authentic campus location, not a marketing ticker) */}
              <div className="inline-flex items-center gap-2 rounded-full bg-neutral-100 dark:bg-white/10 text-neutral-800 dark:text-white border border-neutral-200 dark:border-white/20 px-3.5 py-1 text-xs font-semibold backdrop-blur-md shadow-xs">
                <span className="h-2 w-2 rounded-full bg-[#00D592]" aria-hidden="true" />
                <span>Universidad Nacional de Colombia · Sede Bogotá</span>
              </div>

              {/* Display Headline: Cap at 680px, text-balance, bold contrast */}
              <h1 className="mt-6 max-w-[680px] font-display font-bold text-4xl tracking-tight sm:text-5xl lg:text-6xl sm:leading-[1.12] text-balance text-neutral-950 dark:text-white">
                Todo lo que pasa en la Nacho,<br className="hidden sm:inline" /> coordinado en un solo lugar.
              </h1>

              {/* Subheading: max-w-[680px], text-pretty */}
              <p className="mt-5 max-w-[680px] text-base text-neutral-700 dark:text-neutral-200 sm:text-lg font-normal leading-relaxed text-pretty">
                Eventos culturales y deportivos autogestionados, reserva de implementos con la regla de los 10 minutos y el mapa en vivo de la Ciudad Universitaria.
              </p>

              {/* Single Primary Call to Action above the fold (Rule A4 & B1, B2) */}
              <div className="mt-8 flex flex-wrap items-center gap-4">
                <motion.div whileTap={{ scale: 0.98 }} whileHover={{ y: -1 }}>
                  <Link
                    to="/events"
                    className="inline-flex items-center gap-2 rounded-lg bg-[#00D592] px-4 py-2 text-base font-semibold text-neutral-950 shadow-md transition-colors hover:bg-[#00C082] focus-visible:outline-2 focus-visible:outline-[#00D592]"
                  >
                    <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <polygon points="3 11 22 2 13 21 11 13 3 11" />
                    </svg>
                    Explorar parches del campus
                  </Link>
                </motion.div>

                <span className="text-xs text-neutral-500 dark:text-neutral-300">
                  Acceso libre con cuenta institucional @unal.edu.co
                </span>
              </div>

              {/* Quick direct discovery chips for popular campus spots with interactive pulse filter */}
              <div className="mt-8 pt-2">
                <div className="flex items-center justify-between">
                  <p className="text-xs font-semibold uppercase tracking-wider text-neutral-500 dark:text-neutral-300">
                    Parches frecuentes en el campus
                  </p>
                  {selectedSpot && (
                    <button
                      type="button"
                      onClick={() => setSelectedSpot(null)}
                      className="text-[11px] font-semibold text-[#00875A] dark:text-[#00F5A0] hover:underline"
                    >
                      Restablecer filtro ✕
                    </button>
                  )}
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {(Object.keys(SPOT_PULSE_REGISTRY) as CampusSpotId[]).map((key) => {
                    const spot = SPOT_PULSE_REGISTRY[key];
                    const isSelected = selectedSpot === key;
                    return (
                      <button
                        key={key}
                        type="button"
                        onClick={() => setSelectedSpot(isSelected ? null : key)}
                        className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition-all cursor-pointer ${
                          isSelected
                            ? 'bg-[#00D592] text-neutral-950 border border-[#00D592] shadow-sm font-bold scale-[1.02]'
                            : 'bg-neutral-100 hover:bg-neutral-200/80 dark:bg-white/10 dark:hover:bg-white/20 text-neutral-800 dark:text-white border border-neutral-200/80 dark:border-white/15 backdrop-blur-md'
                        }`}
                      >
                        {spot.label}
                        {isSelected && (
                          <span className="text-[10px] ml-0.5 opacity-75">✕</span>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>
            </motion.div>

            {/* Right Column: "Pulso del Campus" Interactive Live Widget */}
            <motion.div
              initial={{ opacity: 0, scale: 0.98, y: 12 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              transition={{ ...SPRING_PRODUCTION, delay: 0.1 }}
              className="lg:col-span-5"
            >
              <div className="rounded-2xl border border-black/[0.08] dark:border-white/[0.08] bg-white/85 dark:bg-[#0E1720]/80 backdrop-blur-xl p-5 sm:p-6 shadow-xl dark:shadow-[0_8px_30px_rgb(0,0,0,0.25)]">
                {/* Header widget with live activity indicator */}
                <div className="flex items-center justify-between pb-4 border-b border-black/[0.05] dark:border-white/[0.06]">
                  <div className="flex items-center gap-2.5">
                    <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-[#00D592]/15 text-[#00875A] dark:text-[#00F5A0]">
                      <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d="M12 2a10 10 0 1 0 10 10" />
                        <path d="M12 12 19 5" />
                        <circle cx="12" cy="12" r="2" />
                      </svg>
                    </span>
                    <div>
                      <h2 className="font-display font-bold text-sm text-neutral-900 dark:text-white">
                        Pulso del Campus
                      </h2>
                      <p className="text-[11px] text-neutral-500 dark:text-neutral-400">
                        Sincronización en vivo · Hoy
                      </p>
                    </div>
                  </div>

                  {/* Interactive category switcher */}
                  <div className="flex rounded-lg bg-black/[0.04] dark:bg-white/[0.06] backdrop-blur-sm p-0.5 text-[11px] font-semibold border border-black/[0.03] dark:border-white/[0.04]">
                    <button
                      type="button"
                      onClick={() => setActivePulseTab('events')}
                      className={`px-2.5 py-1 rounded-md transition-all cursor-pointer ${
                        activePulseTab === 'events'
                          ? 'bg-white dark:bg-[#14202C] text-neutral-900 dark:text-white shadow-xs'
                          : 'text-neutral-500 hover:text-neutral-900 dark:hover:text-white'
                      }`}
                    >
                      Eventos
                    </button>
                    <button
                      type="button"
                      onClick={() => setActivePulseTab('spots')}
                      className={`px-2.5 py-1 rounded-md transition-all cursor-pointer ${
                        activePulseTab === 'spots'
                          ? 'bg-white dark:bg-[#14202C] text-neutral-900 dark:text-white shadow-xs'
                          : 'text-neutral-500 hover:text-neutral-900 dark:hover:text-white'
                      }`}
                    >
                      Espacios
                    </button>
                  </div>
                </div>

                {/* Active spot filter indicator pill */}
                {selectedSpot && (
                  <motion.div
                    initial={{ opacity: 0, height: 0, marginTop: 0 }}
                    animate={{ opacity: 1, height: 'auto', marginTop: 12 }}
                    exit={{ opacity: 0, height: 0, marginTop: 0 }}
                    className="flex items-center justify-between rounded-lg bg-[#00D592]/10 dark:bg-[#00D592]/15 border border-[#00D592]/25 px-3 py-1.5 text-xs text-[#00875A] dark:text-[#00F5A0]"
                  >
                    <div className="flex items-center gap-1.5 font-semibold">
                      <span className="h-1.5 w-1.5 rounded-full bg-[#00D592] animate-pulse" />
                      <span>Filtrado en: <strong>{SPOT_PULSE_REGISTRY[selectedSpot].shortName}</strong></span>
                    </div>
                    <button
                      type="button"
                      onClick={() => setSelectedSpot(null)}
                      className="text-[11px] font-semibold hover:underline opacity-80 hover:opacity-100 cursor-pointer"
                    >
                      Ver todo ✕
                    </button>
                  </motion.div>
                )}

                {/* Tab content area */}
                <div className="mt-4 min-h-[220px]">
                  <AnimatePresence mode="wait">
                    {activePulseTab === 'events' && (
                      <motion.div
                        key={`tab-events-${selectedSpot || 'all'}`}
                        initial={{ opacity: 0, y: 6 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -6 }}
                        transition={{ duration: 0.2 }}
                        className="space-y-3"
                      >
                        {selectedSpot ? (
                          SPOT_PULSE_REGISTRY[selectedSpot].events.map((evItem, idx) => (
                            <div key={idx} className="rounded-xl border border-black/[0.05] dark:border-white/[0.06] bg-white/60 dark:bg-[#14202C]/60 backdrop-blur-md p-3.5 transition-colors hover:border-[#00D592]/30">
                              <div className="flex items-center justify-between text-xs">
                                <span className="font-semibold text-[#00875A] dark:text-[#00F5A0]">
                                  {evItem.category}
                                </span>
                                <span className="text-neutral-500 font-medium">{evItem.time}</span>
                              </div>
                              <p className="mt-1 font-semibold text-sm text-neutral-900 dark:text-white">
                                {evItem.title}
                              </p>
                              <p className="mt-0.5 text-xs text-neutral-500 dark:text-neutral-400 flex items-center gap-1.5">
                                <svg className="h-3.5 w-3.5 shrink-0 text-[#00D592]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                  <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" />
                                  <circle cx="12" cy="10" r="3" />
                                </svg>
                                {evItem.detail}
                              </p>
                            </div>
                          ))
                        ) : (
                          <>
                            <div className="rounded-xl border border-black/[0.05] dark:border-white/[0.06] bg-white/60 dark:bg-[#14202C]/60 backdrop-blur-md p-3.5 transition-colors hover:border-[#00D592]/30">
                              <div className="flex items-center justify-between text-xs">
                                <span className="font-semibold text-[#00875A] dark:text-[#00F5A0]">
                                  Torneo CyT
                                </span>
                                <span className="text-neutral-500 font-medium">Hoy · 4:00 PM</span>
                              </div>
                              <p className="mt-1 font-semibold text-sm text-neutral-900 dark:text-white">
                                Torneo Relámpago de Ping Pong CyT
                              </p>
                              <p className="mt-0.5 text-xs text-neutral-500 dark:text-neutral-400 flex items-center gap-1.5">
                                <svg className="h-3.5 w-3.5 shrink-0 text-[#00D592]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                  <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" />
                                  <circle cx="12" cy="10" r="3" />
                                </svg>
                                Ed. Ciencia y Tecnología (453) · 32 inscritos
                              </p>
                            </div>

                            <div className="rounded-xl border border-black/[0.05] dark:border-white/[0.06] bg-white/60 dark:bg-[#14202C]/60 backdrop-blur-md p-3.5 transition-colors hover:border-[#00D592]/30">
                              <div className="flex items-center justify-between text-xs">
                                <span className="font-semibold text-neutral-600 dark:text-neutral-300">
                                  Mundialito UNAL
                                </span>
                                <span className="text-neutral-500 font-medium">Hoy · 5:30 PM</span>
                              </div>
                              <p className="mt-1 font-semibold text-sm text-neutral-900 dark:text-white">
                                Fecha 3 de Microfútbol Interfacultades
                              </p>
                              <p className="mt-0.5 text-xs text-neutral-500 dark:text-neutral-400 flex items-center gap-1.5">
                                <svg className="h-3.5 w-3.5 shrink-0 text-[#00D592]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                  <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" />
                                  <circle cx="12" cy="10" r="3" />
                                </svg>
                                Canchas de Minas (Ed. 411) · Entrada libre
                              </p>
                            </div>
                          </>
                        )}
                      </motion.div>
                    )}



                    {activePulseTab === 'spots' && (
                      <motion.div
                        key={`tab-spots-${selectedSpot || 'all'}`}
                        initial={{ opacity: 0, y: 6 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -6 }}
                        transition={{ duration: 0.2 }}
                        className="space-y-3"
                      >
                        {selectedSpot ? (
                          SPOT_PULSE_REGISTRY[selectedSpot].spots.map((spItem, idx) => (
                            <div key={idx} className="rounded-xl border border-black/[0.05] dark:border-white/[0.06] bg-white/60 dark:bg-[#14202C]/60 backdrop-blur-md p-3.5">
                              <div className="flex items-center justify-between text-xs">
                                <span className="font-semibold text-neutral-800 dark:text-neutral-200">
                                  {spItem.name}
                                </span>
                                <span className={`inline-flex items-center gap-1 text-[11px] font-semibold ${spItem.statusColor}`}>
                                  <span className="h-1.5 w-1.5 rounded-full bg-[#00D592]" />
                                  {spItem.status}
                                </span>
                              </div>
                              <p className="mt-1 text-xs text-neutral-500 dark:text-neutral-400">
                                {spItem.description}
                              </p>
                            </div>
                          ))
                        ) : (
                          <>
                            <div className="rounded-xl border border-black/[0.05] dark:border-white/[0.06] bg-white/60 dark:bg-[#14202C]/60 backdrop-blur-md p-3.5">
                              <div className="flex items-center justify-between text-xs">
                                <span className="font-semibold text-neutral-800 dark:text-neutral-200">
                                  Plaza Ché (Santander)
                                </span>
                                <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-[#00875A] dark:text-[#00F5A0]">
                                  <span className="h-1.5 w-1.5 rounded-full bg-[#00D592]" />
                                  Alta concurrencia
                                </span>
                              </div>
                              <p className="mt-1 text-xs text-neutral-500 dark:text-neutral-400">
                                Frente a la Biblioteca Central (Ed. 102). Parche cultural, música y actividades abiertas.
                              </p>
                            </div>

                            <div className="rounded-xl border border-black/[0.05] dark:border-white/[0.06] bg-white/60 dark:bg-[#14202C]/60 backdrop-blur-md p-3.5">
                              <div className="flex items-center justify-between text-xs">
                                <span className="font-semibold text-neutral-800 dark:text-neutral-200">
                                  Jardín Freud
                                </span>
                                <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-neutral-500">
                                  Tranquilo
                                </span>
                              </div>
                              <p className="mt-1 text-xs text-neutral-500 dark:text-neutral-400">
                                Zona verde frente a Ciencias Humanas (Ed. 224). Espacio de lectura y desparche acústico.
                              </p>
                            </div>
                          </>
                        )}
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>

                {/* Footer status notice */}
                <div className="mt-4 pt-3.5 border-t border-black/[0.05] dark:border-white/[0.06] flex items-center justify-between text-xs">
                  <span className="text-neutral-500 dark:text-neutral-400">
                    Autenticado con @unal.edu.co
                  </span>
                  <Link
                    to="/events"
                    className="font-semibold text-[#00875A] dark:text-[#00F5A0] hover:underline flex items-center gap-1"
                  >
                    Ver todo
                    <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <polyline points="9 18 15 12 9 6" />
                    </svg>
                  </Link>
                </div>
              </div>
            </motion.div>
          </div>
        </div>
      </section>

      {/* 2. TAGLINE REVEAL (Rule B11: Dedicated word-by-word scroll interpolation) */}
      <TaglineReveal />

      {/* 3. ASYMMETRIC EDITORIAL FEATURE GRID & HOW IT WORKS (Rule A2, A5, B3, B6) */}
      <section className="relative overflow-hidden bg-neutral-50/70 py-16 dark:bg-[#0A1016] sm:py-24 border-b border-black/[0.06] dark:border-white/[0.06]">
        {/* Ambient diffuse glows */}
        <div
          className="pointer-events-none absolute top-1/4 -right-24 h-[440px] w-[520px] rounded-full bg-[#00D592]/8 blur-[130px] dark:bg-[#00D592]/8"
          aria-hidden="true"
        />
        <div
          className="pointer-events-none absolute -bottom-24 -left-20 h-[380px] w-[480px] rounded-full bg-[#00D592]/6 blur-[120px] dark:bg-[#00D592]/6"
          aria-hidden="true"
        />

        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          
          <div className="max-w-[680px]">
            <div className="inline-flex items-center gap-2 rounded-full bg-neutral-200/60 dark:bg-neutral-800/80 px-3.5 py-1 text-xs font-semibold text-neutral-700 dark:text-neutral-300 border border-neutral-300/60 dark:border-neutral-700">
              <span className="h-1.5 w-1.5 rounded-full bg-[#00D592]" aria-hidden="true" />
              <span>Dinámica y Convivencia en el Campus</span>
            </div>
            <h2 className="mt-4 font-display font-bold text-3xl tracking-tight text-neutral-900 dark:text-white sm:text-4xl text-balance">
              Diseñado para la realidad entre clases de la Ciudad Universitaria
            </h2>
            <p className="mt-3 text-base text-neutral-600 dark:text-neutral-400 text-pretty">
              Sin intermediarios ni tiempo perdido: sincroniza tus descansos con los Puntos de Bienestar, las canchas y los espacios autogestionados de la Sede Bogotá.
            </p>
          </div>

          <div className="mt-12 grid grid-cols-1 gap-6 lg:grid-cols-12">
            
            {/* Hero Feature & 3-Step Process: La Regla de los 10 Minutos (7 cols) */}
            <motion.div
              whileInView={{ opacity: 1, y: 0 }}
              initial={{ opacity: 0, y: 16 }}
              viewport={{ once: true, margin: '-40px' }}
              transition={SPRING_PRODUCTION}
              className="rounded-2xl border border-black/[0.07] dark:border-white/[0.08] bg-white/75 dark:bg-[#0E1720]/80 backdrop-blur-xl p-6 sm:p-8 lg:col-span-7 flex flex-col justify-between shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-[0_8px_30px_rgb(0,0,0,0.25)]"
            >
              <div>
                <div className="flex items-center gap-2.5">
                  <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#00D592]/15 text-[#00875A] dark:text-[#00F5A0]">
                    <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <circle cx="12" cy="12" r="10" />
                      <polyline points="12 6 12 12 16 14" />
                    </svg>
                  </span>
                  <span className="text-xs font-bold uppercase tracking-wider text-[#00875A] dark:text-[#00F5A0]">
                    Regla de Convivencia
                  </span>
                </div>

                <h3 className="mt-5 font-display font-bold text-2xl text-neutral-900 dark:text-white text-balance">
                  Cero implementos acaparados: La Regla de los 10 Minutos
                </h3>
                <p className="mt-2.5 text-sm text-neutral-600 dark:text-neutral-300 leading-relaxed text-pretty">
                  Apartas el balón, raqueta o juego de mesa desde tu celular al salir del salón. Tienes exactamente 10 minutos de gracia para presentarte con tu carné en el Punto de Bienestar de tu facultad. Si no llegas a tiempo, el sistema libera automáticamente el implemento para que otro estudiante pueda aprovecharlo.
                </p>
              </div>

              {/* 3 Steps: How It Works with Nested Radius formula (Rule B3: outer rounded-2xl, gap 16px -> inner rounded-xl) */}
              <div className="mt-8 pt-6 border-t border-black/[0.05] dark:border-white/[0.06] grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="rounded-xl bg-white/60 dark:bg-[#14202C]/60 backdrop-blur-md p-4 border border-black/[0.05] dark:border-white/[0.06]">
                  <span className="font-display font-bold text-xs text-[#00875A] dark:text-[#00F5A0] tracking-wide">
                    01. Aparta en 1 toque
                  </span>
                  <p className="mt-1.5 text-xs text-neutral-600 dark:text-neutral-400 leading-relaxed">
                    Eliges implemento disponible en el Punto de Bienestar de tu facultad.
                  </p>
                </div>
                <div className="rounded-xl bg-white/60 dark:bg-[#14202C]/60 backdrop-blur-md p-4 border border-black/[0.05] dark:border-white/[0.06]">
                  <span className="font-display font-bold text-xs text-[#00875A] dark:text-[#00F5A0] tracking-wide">
                    02. 10 min de gracia
                  </span>
                  <p className="mt-1.5 text-xs text-neutral-600 dark:text-neutral-400 leading-relaxed">
                    Caminas al Punto de Bienestar y reclamas con tu carné de estudiante.
                  </p>
                </div>
                <div className="rounded-xl bg-white/60 dark:bg-[#14202C]/60 backdrop-blur-md p-4 border border-black/[0.05] dark:border-white/[0.06]">
                  <span className="font-display font-bold text-xs text-[#00875A] dark:text-[#00F5A0] tracking-wide">
                    03. Juega y rota
                  </span>
                  <p className="mt-1.5 text-xs text-neutral-600 dark:text-neutral-400 leading-relaxed">
                    Disfrutas tu hora libre y devuelves a tiempo para la comunidad.
                  </p>
                </div>
              </div>
            </motion.div>

            {/* Right Column Features (5 cols) */}
            <div className="lg:col-span-5 flex flex-col gap-6">
              
              {/* Feature 2: Georreferenciación Precisa */}
              <motion.div
                whileInView={{ opacity: 1, y: 0 }}
                initial={{ opacity: 0, y: 16 }}
                viewport={{ once: true, margin: '-40px' }}
                transition={{ ...SPRING_PRODUCTION, delay: 0.08 }}
                className="rounded-2xl border border-black/[0.07] dark:border-white/[0.08] bg-white/75 dark:bg-[#0E1720]/80 backdrop-blur-xl p-6 flex-1 shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-[0_8px_30px_rgb(0,0,0,0.25)]"
              >
                <div className="flex items-center gap-2.5">
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-black/[0.04] dark:bg-white/[0.06] text-neutral-700 dark:text-neutral-300">
                    <svg className="h-4.5 w-4.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <polygon points="3 6 9 3 15 6 21 3 21 18 15 21 9 18 3 21" />
                      <line x1="9" y1="3" x2="9" y2="18" />
                      <line x1="15" y1="6" x2="15" y2="21" />
                    </svg>
                  </span>
                  <span className="text-xs font-bold uppercase tracking-wider text-neutral-500 dark:text-neutral-400">
                    Georreferenciación UNAL
                  </span>
                </div>

                <h3 className="mt-4 font-display font-bold text-lg text-neutral-900 dark:text-white text-balance">
                  Ubicación exacta con números oficiales de edificio
                </h3>
                <p className="mt-2 text-xs text-neutral-600 dark:text-neutral-400 leading-relaxed text-pretty">
                  Ubica actividades no solo en auditorios sino en puntos de encuentro clásicos: El León (Ed. 101), Plaza Ché (Ed. 102), Jardín Freud (Ed. 224), Canchas de Minas (Ed. 411) o CyT (Ed. 453).
                </p>
                <Link
                  to="/map"
                  className="mt-4 inline-flex items-center gap-1.5 text-xs font-semibold text-[#00875A] dark:text-[#00F5A0] hover:underline"
                >
                  Explorar mapa del campus
                  <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <line x1="5" y1="12" x2="19" y2="12" />
                    <polyline points="12 5 19 12 12 19" />
                  </svg>
                </Link>
              </motion.div>

              {/* Feature 3: Comunidad 100% UNAL Autoverificada */}
              <motion.div
                whileInView={{ opacity: 1, y: 0 }}
                initial={{ opacity: 0, y: 16 }}
                viewport={{ once: true, margin: '-40px' }}
                transition={{ ...SPRING_PRODUCTION, delay: 0.16 }}
                className="rounded-2xl border border-black/[0.07] dark:border-white/[0.08] bg-white/75 dark:bg-[#0E1720]/80 backdrop-blur-xl p-6 flex-1 shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-[0_8px_30px_rgb(0,0,0,0.25)]"
              >
                <div className="flex items-center gap-2.5">
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-black/[0.04] dark:bg-white/[0.06] text-neutral-700 dark:text-neutral-300">
                    <svg className="h-4.5 w-4.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                      <path d="m9 12 2 2 4-4" />
                    </svg>
                  </span>
                  <span className="text-xs font-bold uppercase tracking-wider text-neutral-500 dark:text-neutral-400">
                    Comunidad Segura
                  </span>
                </div>

                <h3 className="mt-4 font-display font-bold text-lg text-neutral-900 dark:text-white text-balance">
                  Validación exclusiva con cuenta @unal.edu.co
                </h3>
                <p className="mt-2 text-xs text-neutral-600 dark:text-neutral-400 leading-relaxed text-pretty">
                  Sin perfiles falsos ni publicidad externa invasiva. Cada publicación, reserva de implemento o suscripción a eventos está respaldada por miembros activos de la comunidad universitaria.
                </p>
              </motion.div>

            </div>

          </div>
        </div>
      </section>

      {/* 4. AGENDA EN VIVO: CARRUSEL HORIZONTAL DE PRÓXIMOS EVENTOS (Rule A2, B3, B5, B6) */}
      <section className="relative overflow-hidden bg-white py-16 dark:bg-[#080E14] sm:py-24 border-b border-black/[0.06] dark:border-white/[0.06]">
        {/* Ambient diffuse glow */}
        <div
          className="pointer-events-none absolute -top-24 left-1/3 h-[420px] w-[620px] -translate-x-1/2 rounded-full bg-[#00D592]/8 blur-[140px] dark:bg-[#00D592]/7"
          aria-hidden="true"
        />

        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          
          <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4">
            <div className="max-w-[680px]">
              <div className="inline-flex items-center gap-2 rounded-full bg-neutral-100 dark:bg-neutral-800/80 px-3.5 py-1 text-xs font-semibold text-neutral-700 dark:text-neutral-300 border border-neutral-200 dark:border-neutral-700">
                <span className="h-1.5 w-1.5 rounded-full bg-[#00D592]" aria-hidden="true" />
                <span>Cartelera Comunitaria</span>
              </div>
              <h2 className="mt-3 font-display font-bold text-2xl text-neutral-900 sm:text-3xl dark:text-white text-balance">
                Próximas actividades en la Sede Bogotá
              </h2>
              <p className="mt-1.5 text-sm text-neutral-600 dark:text-neutral-400 text-pretty">
                Consulta la cartelera en tiempo real, entérate de las actividades oficiales y conéctate con los eventos del campus.
              </p>
            </div>

            {/* Controles del carrusel */}
            <div className="flex items-center gap-2 self-end sm:self-auto">
              <button
                type="button"
                onClick={() => scrollCarousel('left')}
                aria-label="Ver eventos anteriores"
                className="inline-flex h-9 w-9 items-center justify-center rounded-xl border border-black/[0.07] bg-white/80 text-neutral-700 hover:border-[#00D592] hover:text-[#00875A] dark:border-white/[0.08] dark:bg-[#0E1720]/80 backdrop-blur-md dark:text-neutral-200 dark:hover:text-[#00F5A0] transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592] shadow-xs"
              >
                <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <line x1="19" y1="12" x2="5" y2="12" />
                  <polyline points="12 19 5 12 12 5" />
                </svg>
              </button>
              <button
                type="button"
                onClick={() => scrollCarousel('right')}
                aria-label="Ver eventos siguientes"
                className="inline-flex h-9 w-9 items-center justify-center rounded-xl border border-black/[0.07] bg-white/80 text-neutral-700 hover:border-[#00D592] hover:text-[#00875A] dark:border-white/[0.08] dark:bg-[#0E1720]/80 backdrop-blur-md dark:text-neutral-200 dark:hover:text-[#00F5A0] transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592] shadow-xs"
              >
                <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <line x1="5" y1="12" x2="19" y2="12" />
                  <polyline points="12 5 19 12 12 19" />
                </svg>
              </button>
            </div>
          </div>

          {/* Horizontal snap container */}
          <div
            ref={carouselRef}
            className="mt-6 flex gap-5 overflow-x-auto pt-3.5 pb-4 snap-x snap-mandatory scroll-smooth no-scrollbar"
          >
            {loadingEvents ? (
              <div className="py-12 text-center text-sm text-neutral-500 w-full">
                Cargando eventos próximos…
              </div>
            ) : upcomingEvents.length === 0 ? (
              <div className="py-12 text-center text-sm text-neutral-500 w-full">
                No hay actividades programadas por el momento. ¡Sé el primero en crear una!
              </div>
            ) : (
              upcomingEvents.map((ev) => (
                <motion.div
                  key={ev.id}
                  whileHover={{ y: -5 }}
                  transition={{ type: 'spring', duration: 0.35, bounce: 0 }}
                  className="relative group w-80 shrink-0 snap-start flex flex-col justify-between overflow-hidden rounded-2xl border border-black/[0.08] dark:border-white/[0.08] bg-white/80 dark:bg-[#0E1720]/80 backdrop-blur-xl p-4 shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-[0_8px_30px_rgb(0,0,0,0.25)] hover:border-[#00D592]/50 hover:shadow-[0_12px_36px_rgba(0,213,146,0.12)] transition-all duration-300"
                >
                  <div>
                    {/* Event image with nested radius (outer rounded-2xl -> inner rounded-xl) */}
                    <div className="relative h-40 w-full overflow-hidden rounded-xl mb-3.5 bg-neutral-100 dark:bg-neutral-800">
                      {ev.image ? (
                        <img
                          src={ev.image}
                          alt={ev.name}
                          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                          loading="lazy"
                        />
                      ) : (
                        <div className="flex h-full w-full items-center justify-center bg-gradient-to-tr from-[#00D592]/10 via-[#00BA7C]/5 to-transparent text-[#00BA7C]">
                          <svg className="h-10 w-10 opacity-70" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                            <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                            <line x1="16" y1="2" x2="16" y2="6" />
                            <line x1="8" y1="2" x2="8" y2="6" />
                            <line x1="3" y1="10" x2="21" y2="10" />
                          </svg>
                        </div>
                      )}

                      {/* Category chip on top-left */}
                      <span className="absolute top-2.5 left-2.5 inline-flex items-center rounded-full bg-black/60 dark:bg-black/75 px-2.5 py-0.5 text-[11px] font-semibold text-white shadow-xs backdrop-blur-md border border-white/10">
                        {ev.category ? EVENT_CATEGORY_LABELS[ev.category] : 'Campus'}
                      </span>

                      {/* Live Activity pulsing beacon on top-right */}
                      <span className="absolute top-2.5 right-2.5 inline-flex items-center gap-1.5 rounded-full bg-black/60 dark:bg-black/75 px-2.5 py-0.5 text-[10px] font-semibold text-white shadow-xs backdrop-blur-md border border-white/10">
                        <span className="relative flex h-2 w-2">
                          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#00D592] opacity-80" />
                          <span className="relative inline-flex rounded-full h-2 w-2 bg-[#00D592]" />
                        </span>
                        <span>{isEventToday(ev.datetime_start) ? 'Hoy en vivo' : 'Confirmado'}</span>
                      </span>
                    </div>

                    {/* Architectural Campus Tag + Date */}
                    <div className="flex items-center justify-between text-xs pt-0.5">
                      <span className="font-mono text-[10px] font-bold tracking-wider text-[#00875A] dark:text-[#00F5A0] bg-[#00D592]/10 dark:bg-[#00D592]/15 px-2 py-0.5 rounded-md border border-[#00D592]/20">
                        {getCampusTag(ev.place)}
                      </span>
                      <time dateTime={ev.datetime_start} className="tabular-nums font-semibold text-neutral-600 dark:text-neutral-300">
                        {new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short' }).format(
                          new Date(ev.datetime_start)
                        )}
                      </time>
                    </div>

                    {/* Event Name */}
                    <h3 className="mt-2.5 font-display font-bold text-base text-neutral-900 line-clamp-2 dark:text-white group-hover:text-[#00875A] dark:group-hover:text-[#00F5A0] transition-colors">
                      <Link
                        to={`/events/${ev.id}`}
                        className="focus-visible:outline-2 focus-visible:outline-[#00D592]"
                      >
                        {ev.name}
                      </Link>
                    </h3>

                    {/* Campus Location */}
                    <p className="mt-1.5 text-xs text-neutral-500 dark:text-neutral-400 line-clamp-1 flex items-center gap-1.5">
                      <svg className="h-3.5 w-3.5 shrink-0 text-[#00D592]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" />
                        <circle cx="12" cy="10" r="3" />
                      </svg>
                      {ev.place}
                    </p>
                  </div>

                  {/* Clean Hairline Footer */}
                  <div className="mt-4 pt-3 border-t border-black/[0.05] dark:border-white/[0.06] flex justify-end items-center text-xs">
                    <Link
                      to={`/events/${ev.id}`}
                      className="inline-flex items-center gap-1 font-semibold text-xs text-[#00875A] hover:text-[#00BA7C] dark:text-[#00F5A0] transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
                    >
                      <span>Ver detalles</span>
                      <svg className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <line x1="5" y1="12" x2="19" y2="12" />
                        <polyline points="12 5 19 12 12 19" />
                      </svg>
                    </Link>
                  </div>
                </motion.div>
              ))
            )}
          </div>
        </div>
      </section>

      {/* 5. MAPA VIVO DEL CAMPUS: GEORREFERENCIACIÓN EN TIEMPO REAL (Rule A2, B3, B4, B6) */}
      <section className="relative overflow-hidden bg-neutral-50/70 py-16 dark:bg-[#0A1016] sm:py-24 border-b border-black/[0.06] dark:border-white/[0.06]">
        {/* Ambient diffuse glow behind the map */}
        <div
          className="pointer-events-none absolute top-1/2 left-1/2 h-[480px] w-[800px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#00D592]/10 blur-[150px] dark:bg-[#00D592]/10"
          aria-hidden="true"
        />

        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4">
            <div className="max-w-[680px]">
              <div className="inline-flex items-center gap-2 rounded-full bg-neutral-200/60 dark:bg-neutral-800/80 px-3.5 py-1 text-xs font-semibold text-neutral-700 dark:text-neutral-300 border border-neutral-300/60 dark:border-neutral-700">
                <span className="h-1.5 w-1.5 rounded-full bg-[#00D592]" aria-hidden="true" />
                <span>Georreferenciación Sede Bogotá</span>
              </div>
              <h2 className="mt-3 font-display font-bold text-2xl text-neutral-900 sm:text-3xl dark:text-white text-balance">
                Mapa Vivo de la Ciudad Universitaria
              </h2>
              <p className="mt-1.5 text-sm text-neutral-600 dark:text-neutral-400 text-pretty">
                Ubica en tiempo real dónde ocurren las actividades culturales, deportivas y académicas. Selecciona cualquier punto para ver detalles o trazar tu ruta.
              </p>
            </div>

            <div className="flex items-center gap-3">
              <Link
                to="/map"
                className="inline-flex items-center gap-2 rounded-lg bg-[#00D592] px-4 py-2 text-sm font-semibold text-neutral-950 shadow-xs transition-colors hover:bg-[#00C082] focus-visible:outline-2 focus-visible:outline-[#00D592]"
              >
                <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <polygon points="3 6 9 3 15 6 21 3 21 18 15 21 9 18 3 21" />
                  <line x1="9" y1="3" x2="9" y2="18" />
                  <line x1="15" y1="6" x2="15" y2="21" />
                </svg>
                Explorar mapa completo
              </Link>
            </div>
          </div>

          {/* Contenedor del mapa embebido con borde perimetral y fondo plano */}
          <div className="mt-8 overflow-hidden rounded-2xl border border-black/[0.07] dark:border-white/[0.08] bg-white/75 shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:bg-[#0E1720]/80 backdrop-blur-xl dark:shadow-[0_8px_30px_rgb(0,0,0,0.25)]">
            <div className="h-[480px] w-full">
              <CampusMap
                events={mapEvents}
                selectedEvent={selectedMapEvent}
                onSelectEvent={setSelectedMapEvent}
              />
            </div>
          </div>

          {/* Accesos rápidos a zonas emblemáticas */}
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-xs text-neutral-500 dark:text-neutral-400 px-1">
            <span className="font-semibold text-neutral-700 dark:text-neutral-300">
              Puntos neurálgicos frecuentes:
            </span>
            <div className="flex flex-wrap gap-2">
              <Link
                to="/map"
                className="inline-flex items-center gap-1.5 rounded-lg bg-white/70 dark:bg-[#0E1720]/80 backdrop-blur-md px-3 py-1.5 font-medium text-neutral-700 dark:text-neutral-300 border border-black/[0.06] dark:border-white/[0.08] hover:border-[#00D592]/50 hover:text-neutral-900 dark:hover:text-white transition-all shadow-xs"
              >
                📍 Plaza Ché (Ed. 102)
              </Link>
              <Link
                to="/map"
                className="inline-flex items-center gap-1.5 rounded-lg bg-white/70 dark:bg-[#0E1720]/80 backdrop-blur-md px-3 py-1.5 font-medium text-neutral-700 dark:text-neutral-300 border border-black/[0.06] dark:border-white/[0.08] hover:border-[#00D592]/50 hover:text-neutral-900 dark:hover:text-white transition-all shadow-xs"
              >
                🌱 Jardín Freud (Ed. 224)
              </Link>
              <Link
                to="/map"
                className="inline-flex items-center gap-1.5 rounded-lg bg-white/70 dark:bg-[#0E1720]/80 backdrop-blur-md px-3 py-1.5 font-medium text-neutral-700 dark:text-neutral-300 border border-black/[0.06] dark:border-white/[0.08] hover:border-[#00D592]/50 hover:text-neutral-900 dark:hover:text-white transition-all shadow-xs"
              >
                ⚽ Canchas de Minas (Ed. 411)
              </Link>
              <Link
                to="/map"
                className="inline-flex items-center gap-1.5 rounded-lg bg-white/70 dark:bg-[#0E1720]/80 backdrop-blur-md px-3 py-1.5 font-medium text-neutral-700 dark:text-neutral-300 border border-black/[0.06] dark:border-white/[0.08] hover:border-[#00D592]/50 hover:text-neutral-900 dark:hover:text-white transition-all shadow-xs"
              >
                🏓 CyT (Ed. 453)
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* 6. FAQ ACCORDION: OBJECTION HANDLING (Rule A2 item 10, A5) */}
      <FaqSection />

      {/* 7. INSTITUTIONAL CALL TO ACTION & RISK REVERSAL (Rule A2 items 11-12, A4, B4, B6) */}
      <section className="relative overflow-hidden bg-neutral-900 dark:bg-[#070B0E] px-4 py-20 text-center text-white sm:px-6 lg:px-8 border-t border-neutral-800">
        {/* Ambient diffuse glow */}
        <div
          className="pointer-events-none absolute top-1/2 left-1/2 h-[420px] w-[700px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#00D592]/12 blur-[140px]"
          aria-hidden="true"
        />

        <div className="relative mx-auto max-w-[680px]">
          <div className="inline-flex items-center gap-2 rounded-full bg-neutral-800/80 px-3.5 py-1 text-xs font-semibold text-neutral-300 border border-neutral-700">
            <span className="h-1.5 w-1.5 rounded-full bg-[#00D592]" aria-hidden="true" />
            <span>Únete a la Sede Bogotá</span>
          </div>

          <h2 className="mt-4 font-display font-bold text-3xl tracking-tight sm:text-4xl text-white text-balance">
            Conéctate con todo lo que pasa en el campus
          </h2>
          <p className="mt-3 text-base font-normal text-neutral-300 text-pretty">
            Sin costo, sin intermediarios y respaldado al 100% por tu cuenta institucional @unal.edu.co. Todo lo que pasa en la Nacho, en un solo lugar.
          </p>

          <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-3.5">
            {isAuthenticated ? (
              <motion.div whileHover={{ y: -1 }} whileTap={{ scale: 0.98 }}>
                <Link
                  to="/events"
                  className="inline-flex items-center gap-2 rounded-lg bg-[#00D592] px-6 py-3 text-base font-semibold text-neutral-950 shadow-xs transition-colors hover:bg-[#00C082] focus-visible:outline-2 focus-visible:outline-[#00D592]"
                >
                  <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <polygon points="3 11 22 2 13 21 11 13 3 11" />
                  </svg>
                  Explorar parches del campus
                </Link>
              </motion.div>
            ) : (
              <>
                <motion.button
                  type="button"
                  whileHover={{ y: -1 }}
                  whileTap={{ scale: 0.98 }}
                  onClick={signInWithGoogle}
                  className="inline-flex items-center gap-3 rounded-lg bg-white px-6 py-3 text-sm font-semibold text-neutral-900 shadow-xs hover:bg-neutral-100 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-[#00D592]"
                >
                  <svg className="h-4.5 w-4.5" viewBox="0 0 24 24" aria-hidden="true">
                    <path
                      fill="#4285F4"
                      d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                    />
                    <path
                      fill="#34A853"
                      d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                    />
                    <path
                      fill="#FBBC05"
                      d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                    />
                    <path
                      fill="#EA4335"
                      d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                    />
                  </svg>
                  <span>Ingresar con correo @unal.edu.co</span>
                </motion.button>

                <Link
                  to="/events"
                  className="inline-flex items-center gap-2 rounded-lg border border-neutral-700 bg-transparent px-5 py-3 text-sm font-semibold text-neutral-200 hover:bg-neutral-800/80 transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
                >
                  Explorar sin iniciar sesión
                </Link>
              </>
            )}
          </div>

          {/* Risk Reversal & Trust Signals (Rule A2 item 11) */}
          <div className="mt-10 flex flex-wrap items-center justify-center gap-6 text-xs text-neutral-400 border-t border-neutral-800/80 pt-6">
            <span className="inline-flex items-center gap-1.5">
              <svg className="h-4 w-4 text-[#00D592]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <polyline points="20 6 9 17 4 12" />
              </svg>
              100% gratuito para comunidad UNAL
            </span>
            <span className="inline-flex items-center gap-1.5">
              <svg className="h-4 w-4 text-[#00D592]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <polyline points="20 6 9 17 4 12" />
              </svg>
              Sin contraseñas extras: acceso directo Google
            </span>
            <span className="inline-flex items-center gap-1.5">
              <svg className="h-4 w-4 text-[#00D592]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <polyline points="20 6 9 17 4 12" />
              </svg>
              Cero publicidad comercial
            </span>
          </div>
        </div>
      </section>
    </div>
  );
}
