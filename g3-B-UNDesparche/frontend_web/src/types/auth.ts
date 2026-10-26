export type RoleName =
  | 'Miembro de la Comunidad'
  | 'Administrador de Eventos'
  | 'Administrador de Implementos'
  | 'Administrador del Sistema';

export type UserStatus = 'ACT' | 'SAN';

export interface BackendProfile {
  id: number;
  email: string;
  name: string;
  faculty: string | null;
  status: UserStatus;
  roles: RoleName[];
  is_active?: boolean;
  date_joined?: string;
}

export interface UserUpdatePayload {
  status?: UserStatus;
  roles?: RoleName[];
  faculty?: string | null;
}
