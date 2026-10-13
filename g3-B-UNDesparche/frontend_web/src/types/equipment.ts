export type ImplementState = 'DIS' | 'NDS' | 'RES' | 'PRE';

export const IMPLEMENT_STATE_LABELS: Record<ImplementState, string> = {
  DIS: 'Disponible',
  NDS: 'No Disponible',
  RES: 'Reservado',
  PRE: 'Prestado',
};

export type ImplementCategory = 'BAL' | 'RAQ' | 'MES' | 'JUR' | 'JUM' | 'OTS' | '';

export const IMPLEMENT_CATEGORY_LABELS: Record<string, string> = {
  BAL: 'Balones',
  RAQ: 'Raquetas',
  MES: 'Mesas',
  JUR: 'Juegos recreativos',
  JUM: 'Juegos de mesa',
  OTS: 'Otros',
  '': 'Sin categoría',
};

export type FacultyCode =
  | 'ART'
  | 'CCS'
  | 'CIA'
  | 'CIE'
  | 'CHS'
  | 'DER'
  | 'ENF'
  | 'ING'
  | 'MED'
  | 'MVZ'
  | 'ODO';

export const FACULTY_LABELS: Record<FacultyCode, string> = {
  ART: 'Facultad de Artes',
  CCS: 'Facultad de Ciencias',
  CIA: 'Facultad de Ciencias Agrarias',
  CIE: 'Facultad de Ciencias Económicas',
  CHS: 'Facultad de Ciencias Humanas',
  DER: 'Facultad de Derecho, Ciencias Políticas y Sociales',
  ENF: 'Facultad de Enfermería',
  ING: 'Facultad de Ingeniería',
  MED: 'Facultad de Medicina',
  MVZ: 'Facultad de Medicina Veterinaria y de Zootecnia',
  ODO: 'Facultad de Odontología',
};

export interface ImplementItem {
  id: number;
  name: string;
  description: string;
  category: ImplementCategory;
  state: ImplementState;
  faculty: FacultyCode;
  image?: string | null;
}

export interface ImplementFilterParams {
  search?: string;
  category?: string;
  faculty?: string;
  state?: string;
}

export interface CreateImplementPayload {
  name: string;
  description: string;
  category?: ImplementCategory;
  state?: 'DIS' | 'NDS'; // Al crear solo DIS o NDS permitido
  faculty?: FacultyCode; // Obligatorio para Sistema, asignado para Implementos
  image_file?: File | null;
}

export interface UpdateImplementPayload {
  name?: string;
  description?: string;
  category?: ImplementCategory;
  state?: 'DIS' | 'NDS';
  faculty?: FacultyCode;
  image_file?: File | null;
}

export interface UserSummary {
  id: number;
  name: string;
  email: string;
}

export interface ReserveItem {
  id: number;
  implement: ImplementItem | number;
  user: UserSummary;
  datetime_reserved: string;
  datetime_expiration: string;
  active: boolean;
}

export interface BorrowingItem {
  id: number;
  implement: ImplementItem | number;
  user: UserSummary;
  datetime_borrowed: string;
  datetime_return?: string | null;
  active: boolean;
}
