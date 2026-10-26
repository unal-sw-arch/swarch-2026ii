import { apiRequest } from './client';
import { BackendProfile, RoleName, UserUpdatePayload } from '../types/auth';

const ROLE_CODE_TO_LABEL: Record<string, RoleName> = {
  SYS_ADMIN: 'Administrador del Sistema',
  EVENT_ADMIN: 'Administrador de Eventos',
  IMPLEMENT_ADMIN: 'Administrador de Implementos',
  COMMUNITY: 'Miembro de la Comunidad',
};

const ROLE_LABEL_TO_CODE: Record<string, string> = {
  'Administrador del Sistema': 'SYS_ADMIN',
  'Administrador de Eventos': 'EVENT_ADMIN',
  'Administrador de Implementos': 'IMPLEMENT_ADMIN',
  'Miembro de la Comunidad': 'COMMUNITY',
};

/**
 * Normaliza la respuesta del backend de usuarios al contrato de la interfaz de usuario.
 */
export function normalizeBackendProfile(raw: any): BackendProfile {
  let roleCode = raw.role;
  if (!roleCode && raw.roles) {
    roleCode = Array.isArray(raw.roles) ? raw.roles[0] : raw.roles;
  }
  const label =
    ROLE_CODE_TO_LABEL[roleCode] ||
    (typeof roleCode === 'string' && roleCode.includes(' ') ? roleCode : 'Miembro de la Comunidad');

  return {
    id: raw.id,
    email: raw.email,
    name: raw.name || '',
    faculty: raw.faculty || null,
    status: raw.status || 'ACT',
    roles: [label as RoleName],
    is_active: raw.is_active ?? true,
    date_joined: raw.date_joined,
  };
}

export async function getCurrentUserProfile(token: string): Promise<BackendProfile> {
  const raw = await apiRequest<any>('/users/me/', { token });
  return normalizeBackendProfile(raw);
}

export interface UserFilterParams {
  search?: string;
  groups__name?: string;
}

export async function getUsers(
  params?: UserFilterParams,
  token?: string
): Promise<BackendProfile[]> {
  const backendParams: Record<string, any> = {};
  if (params?.search) backendParams.search = params.search;
  if (params?.groups__name) {
    backendParams.role = ROLE_LABEL_TO_CODE[params.groups__name] || params.groups__name;
  }

  const rawList = await apiRequest<any[]>('/users/', {
    params: backendParams,
    token,
  });
  return (Array.isArray(rawList) ? rawList : []).map(normalizeBackendProfile);
}

export async function updateUser(
  id: number,
  payload: UserUpdatePayload,
  token: string
): Promise<BackendProfile> {
  const backendBody: Record<string, any> = {};
  if (payload.status !== undefined) backendBody.status = payload.status;
  if (payload.faculty !== undefined) backendBody.faculty = payload.faculty;
  if (payload.roles !== undefined) {
    const roleLabel = payload.roles[0] || 'Miembro de la Comunidad';
    backendBody.role = ROLE_LABEL_TO_CODE[roleLabel] || roleLabel;
  }

  const raw = await apiRequest<any>(`/users/${id}/`, {
    method: 'PATCH',
    body: backendBody,
    token,
  });
  return normalizeBackendProfile(raw);
}

export async function deleteUser(id: number, token: string): Promise<void> {
  await apiRequest<void>(`/users/${id}/`, {
    method: 'DELETE',
    token,
  });
}
