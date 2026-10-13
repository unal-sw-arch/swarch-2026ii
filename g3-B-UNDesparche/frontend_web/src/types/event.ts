export type EventStatus = 'PRO' | 'ECU' | 'CAN' | 'FIN';

export const EVENT_STATUS_LABELS: Record<EventStatus, string> = {
  PRO: 'Programado',
  ECU: 'En Curso',
  CAN: 'Cancelado',
  FIN: 'Finalizado',
};

export type EventCategory = 'ACA' | 'CUL' | 'DEP' | 'ASA' | 'PAR' | 'OTR';

export const EVENT_CATEGORY_LABELS: Record<EventCategory, string> = {
  ACA: 'Académico',
  CUL: 'Cultural',
  DEP: 'Deportes',
  ASA: 'Asamblea',
  PAR: 'Parche',
  OTR: 'Otro',
};

export interface EventOrganizer {
  id: number;
  name: string;
  email: string;
}

export interface CampusEvent {
  id: number;
  name: string;
  description: string;
  place: string;
  latitude: string | number;
  longitude: string | number;
  datetime_start: string;
  datetime_end: string;
  status: EventStatus;
  category: EventCategory | null;
  published: boolean;
  image?: string | null;
  organizer?: EventOrganizer | null;
}

export interface EventFilterParams {
  search?: string;
  category?: string;
  category__in?: string;
  status?: string;
  status__in?: string;
  published?: boolean;
  ordering?: string;
}

export interface CreateEventPayload {
  name: string;
  description: string;
  place: string;
  latitude: number | string;
  longitude: number | string;
  datetime_start: string;
  datetime_end: string;
  status?: EventStatus;
  category?: EventCategory | null;
  image_file?: File | null;
}

export interface UpdateEventPayload {
  name?: string;
  description?: string;
  place?: string;
  latitude?: number | string;
  longitude?: number | string;
  datetime_start?: string;
  datetime_end?: string;
  status?: EventStatus;
  category?: EventCategory | null;
  image_file?: File | null;
}
