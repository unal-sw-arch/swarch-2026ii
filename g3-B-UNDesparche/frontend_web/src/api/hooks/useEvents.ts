import { useState, useEffect, useCallback, useRef } from 'react';
import {
  CampusEvent,
  CreateEventPayload,
  EventCategory,
  EventFilterParams,
  EventStatus,
  UpdateEventPayload,
} from '../../types/event';
import {
  getEvents,
  getEventDetail,
  createEvent as apiCreateEvent,
  updateEvent as apiUpdateEvent,
  deleteEvent as apiDeleteEvent,
  publishEvent as apiPublishEvent,
} from '../events';

export function useEvents(token?: string | null) {
  const [events, setEvents] = useState<CampusEvent[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<Error | null>(null);

  const [search, setSearch] = useState<string>('');
  const [categoryFilter, setCategoryFilter] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('');

  const [debouncedSearch, setDebouncedSearch] = useState<string>('');

  // Debounce 300ms para búsqueda
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  const fetchEvents = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: EventFilterParams = {};
      if (debouncedSearch.trim()) params.search = debouncedSearch.trim();
      if (categoryFilter) params.category = categoryFilter;
      if (statusFilter) params.status = statusFilter;

      const data = await getEvents(params, token);
      setEvents(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [debouncedSearch, categoryFilter, statusFilter, token]);

  useEffect(() => {
    fetchEvents();
  }, [fetchEvents]);

  return {
    events,
    loading,
    error,
    search,
    setSearch,
    categoryFilter,
    setCategoryFilter: (cat: string | EventCategory) => setCategoryFilter(cat),
    statusFilter,
    setStatusFilter: (st: string | EventStatus) => setStatusFilter(st),
    refresh: fetchEvents,
  };
}

export function useEventDetail(id: string | number | undefined, token?: string | null) {
  const [event, setEvent] = useState<CampusEvent | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchDetail = useCallback(async () => {
    if (!id) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);

    try {
      const data = await getEventDetail(id, token);
      if (!cancelled) {
        setEvent(data);
      }
    } catch (err) {
      if (!cancelled) {
        setError(err instanceof Error ? err : new Error(String(err)));
      }
    } finally {
      if (!cancelled) {
        setLoading(false);
      }
    }

    return () => {
      cancelled = true;
    };
  }, [id, token]);

  useEffect(() => {
    const cleanup = fetchDetail();
    return () => {
      cleanup.then((fn) => fn && fn());
    };
  }, [fetchDetail]);

  return { event, loading, error, refetch: fetchDetail };
}

export function useAdminEvents(token: string | null) {
  const [events, setEvents] = useState<CampusEvent[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [error, setError] = useState<Error | null>(null);
  const isMounted = useRef(true);

  const fetchEvents = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      const data = await getEvents(undefined, token);
      if (isMounted.current) {
        setEvents(data);
      }
    } catch (err) {
      if (isMounted.current) {
        setError(err instanceof Error ? err : new Error(String(err)));
      }
    } finally {
      if (isMounted.current) {
        setLoading(false);
      }
    }
  }, [token]);

  useEffect(() => {
    isMounted.current = true;
    fetchEvents();
    return () => {
      isMounted.current = false;
    };
  }, [fetchEvents]);

  const handleCreate = async (payload: CreateEventPayload) => {
    if (!token) throw new Error('Token requerido para crear evento');
    setSaving(true);
    setError(null);
    try {
      const created = await apiCreateEvent(payload, token);
      await fetchEvents();
      return created;
    } catch (err) {
      const parsedErr = err instanceof Error ? err : new Error(String(err));
      setError(parsedErr);
      throw parsedErr;
    } finally {
      setSaving(false);
    }
  };

  const handleUpdate = async (id: number | string, payload: UpdateEventPayload) => {
    if (!token) throw new Error('Token requerido para actualizar evento');
    setSaving(true);
    setError(null);
    try {
      const updated = await apiUpdateEvent(id, payload, token);
      await fetchEvents();
      return updated;
    } catch (err) {
      const parsedErr = err instanceof Error ? err : new Error(String(err));
      setError(parsedErr);
      throw parsedErr;
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number | string) => {
    if (!token) throw new Error('Token requerido para eliminar evento');
    setSaving(true);
    setError(null);
    try {
      await apiDeleteEvent(id, token);
      await fetchEvents();
    } catch (err) {
      const parsedErr = err instanceof Error ? err : new Error(String(err));
      setError(parsedErr);
      throw parsedErr;
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async (id: number | string) => {
    if (!token) throw new Error('Token requerido para publicar evento');
    setSaving(true);
    setError(null);
    try {
      const published = await apiPublishEvent(id, token);
      await fetchEvents();
      return published;
    } catch (err) {
      const parsedErr = err instanceof Error ? err : new Error(String(err));
      setError(parsedErr);
      throw parsedErr;
    } finally {
      setSaving(false);
    }
  };

  return {
    events,
    loading,
    saving,
    error,
    createEvent: handleCreate,
    updateEvent: handleUpdate,
    deleteEvent: handleDelete,
    publishEvent: handlePublish,
    refresh: fetchEvents,
    clearError: () => setError(null),
  };
}
