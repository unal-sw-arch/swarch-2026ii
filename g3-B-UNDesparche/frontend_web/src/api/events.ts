import { apiRequest } from './client';
import {
  CampusEvent,
  CreateEventPayload,
  EventFilterParams,
  UpdateEventPayload,
} from '../types/event';

export function normalizeEventCoordinates(event: CampusEvent): CampusEvent {
  return {
    ...event,
    latitude: typeof event.latitude === 'string' ? parseFloat(event.latitude) || 4.6383 : event.latitude,
    longitude: typeof event.longitude === 'string' ? parseFloat(event.longitude) || -74.084 : event.longitude,
  };
}

export async function getEvents(
  params?: EventFilterParams,
  token?: string | null
): Promise<CampusEvent[]> {
  const events = await apiRequest<CampusEvent[]>('/events/', {
    params: params as Record<string, string | number | boolean | null | undefined>,
    token,
  });
  return events.map(normalizeEventCoordinates);
}

export async function getEventDetail(
  id: string | number,
  token?: string | null
): Promise<CampusEvent> {
  const event = await apiRequest<CampusEvent>(`/events/${id}/`, { token });
  return normalizeEventCoordinates(event);
}

function eventPayloadToFormData(payload: CreateEventPayload | UpdateEventPayload): FormData {
  const formData = new FormData();
  for (const [key, value] of Object.entries(payload)) {
    if (value !== undefined && value !== null) {
      if (key === 'image_file' && value instanceof File) {
        formData.append('image_file', value);
      } else if (key === 'latitude' || key === 'longitude') {
        formData.append(key, Number(value).toFixed(6));
      } else if (key !== 'image_file') {
        formData.append(key, String(value));
      }
    }
  }
  return formData;
}

function sanitizeEventPayload<T extends CreateEventPayload | UpdateEventPayload>(payload: T): any {
  const { image_file, ...rest } = payload as any;
  if (rest.latitude !== undefined && rest.latitude !== null) {
    rest.latitude = parseFloat(Number(rest.latitude).toFixed(6));
  }
  if (rest.longitude !== undefined && rest.longitude !== null) {
    rest.longitude = parseFloat(Number(rest.longitude).toFixed(6));
  }
  return rest;
}

export async function createEvent(
  payload: CreateEventPayload,
  token: string
): Promise<CampusEvent> {
  let body: any;
  if (payload.image_file instanceof File) {
    body = eventPayloadToFormData(payload);
  } else {
    body = sanitizeEventPayload(payload);
  }
  const event = await apiRequest<CampusEvent>('/events/', {
    method: 'POST',
    body,
    token,
  });
  return normalizeEventCoordinates(event);
}

export async function updateEvent(
  id: string | number,
  payload: UpdateEventPayload,
  token: string
): Promise<CampusEvent> {
  let body: any;
  if (payload.image_file instanceof File) {
    body = eventPayloadToFormData(payload);
  } else {
    body = sanitizeEventPayload(payload);
  }
  const event = await apiRequest<CampusEvent>(`/events/${id}/`, {
    method: 'PATCH',
    body,
    token,
  });
  return normalizeEventCoordinates(event);
}

export async function deleteEvent(id: string | number, token: string): Promise<void> {
  await apiRequest<void>(`/events/${id}/`, {
    method: 'DELETE',
    token,
  });
}

export async function publishEvent(id: string | number, token: string): Promise<CampusEvent> {
  const event = await apiRequest<CampusEvent>(`/events/${id}/publish/`, {
    method: 'POST',
    token,
  });
  return normalizeEventCoordinates(event);
}

export async function subscribeToEvent(
  id: string | number,
  email?: string,
  token?: string | null
): Promise<{ detail: string }> {
  return apiRequest<{ detail: string }>(`/events/${id}/subscribe/`, {
    method: 'POST',
    body: token ? undefined : email ? { email } : undefined,
    token,
  });
}

export async function unsubscribeFromEvent(
  id: string | number,
  token?: string | null
): Promise<{ detail: string }> {
  return apiRequest<{ detail: string }>(`/events/${id}/unsubscribe/`, {
    method: 'POST',
    token,
  });
}

export interface EmailUnsubscribeResponse {
  detail: string;
  event_name?: string;
  already?: boolean;
}

export async function unsubscribeWithToken(tokenStr: string): Promise<EmailUnsubscribeResponse> {
  return apiRequest<EmailUnsubscribeResponse>('/events/unsubscribe/', {
    method: 'POST',
    body: { token: tokenStr },
  });
}
