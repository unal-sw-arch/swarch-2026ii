import { apiRequest } from './client';
import {
  BorrowingItem,
  CreateImplementPayload,
  ImplementFilterParams,
  ImplementItem,
  ReserveItem,
  UpdateImplementPayload,
} from '../types/equipment';

export async function getImplements(
  params?: ImplementFilterParams,
  token?: string | null
): Promise<ImplementItem[]> {
  return apiRequest<ImplementItem[]>('/inventory/implements/', {
    params: params as Record<string, string | number | boolean | null | undefined>,
    token,
  });
}

export async function getImplementDetail(
  id: string | number,
  token?: string | null
): Promise<ImplementItem> {
  return apiRequest<ImplementItem>(`/inventory/implements/${id}/`, { token });
}

function implementPayloadToFormData(
  payload: CreateImplementPayload | UpdateImplementPayload
): FormData {
  const formData = new FormData();
  for (const [key, value] of Object.entries(payload)) {
    if (value !== undefined && value !== null) {
      if (key === 'image_file' && value instanceof File) {
        formData.append('image_file', value);
      } else if (key !== 'image_file') {
        formData.append(key, String(value));
      }
    }
  }
  return formData;
}

export async function createImplement(
  payload: CreateImplementPayload,
  token: string
): Promise<ImplementItem> {
  let body: any;
  if (payload.image_file instanceof File) {
    body = implementPayloadToFormData(payload);
  } else {
    const { image_file, ...rest } = payload;
    body = rest;
  }
  return apiRequest<ImplementItem>('/inventory/implements/', {
    method: 'POST',
    body,
    token,
  });
}

export async function updateImplement(
  id: string | number,
  payload: UpdateImplementPayload,
  token: string
): Promise<ImplementItem> {
  let body: any;
  if (payload.image_file instanceof File) {
    body = implementPayloadToFormData(payload);
  } else {
    const { image_file, ...rest } = payload;
    body = rest;
  }
  return apiRequest<ImplementItem>(`/inventory/implements/${id}/`, {
    method: 'PATCH',
    body,
    token,
  });
}

export async function deleteImplement(id: string | number, token: string): Promise<void> {
  await apiRequest<void>(`/inventory/implements/${id}/`, {
    method: 'DELETE',
    token,
  });
}

export async function createReserve(
  implementId: number,
  token: string
): Promise<ReserveItem> {
  return apiRequest<ReserveItem>('/inventory/reserves/', {
    method: 'POST',
    body: { implement: implementId },
    token,
  });
}

export async function getReserves(token: string): Promise<ReserveItem[]> {
  return apiRequest<ReserveItem[]>('/inventory/reserves/', { token });
}

export async function confirmReserve(
  reserveId: number,
  token: string
): Promise<BorrowingItem> {
  return apiRequest<BorrowingItem>(`/inventory/reserves/${reserveId}/confirm/`, {
    method: 'POST',
    token,
  });
}

export async function cancelReserve(
  reserveId: number,
  token: string
): Promise<{ detail: string }> {
  return apiRequest<{ detail: string }>(`/inventory/reserves/${reserveId}/cancel/`, {
    method: 'POST',
    token,
  });
}

export async function getBorrowings(token: string): Promise<BorrowingItem[]> {
  return apiRequest<BorrowingItem[]>('/inventory/borrowings/', { token });
}

export async function returnBorrowing(
  borrowingId: number,
  token: string
): Promise<{ detail: string }> {
  return apiRequest<{ detail: string }>(`/inventory/borrowings/${borrowingId}/return/`, {
    method: 'POST',
    token,
  });
}
