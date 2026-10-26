import { useState, useEffect, useCallback, useRef } from 'react';
import {
  ImplementFilterParams,
  ImplementItem,
  ReserveItem,
} from '../../types/equipment';
import {
  getImplements,
  getImplementDetail,
  createReserve as apiCreateReserve,
} from '../equipment';

export function useImplements(initialParams?: ImplementFilterParams, token?: string | null) {
  const [implementsList, setImplementsList] = useState<ImplementItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<Error | null>(null);

  const [search, setSearch] = useState<string>(initialParams?.search || '');
  const [category, setCategory] = useState<string>(initialParams?.category || '');
  const [faculty, setFaculty] = useState<string>(initialParams?.faculty || '');
  const [state, setState] = useState<string>(initialParams?.state || '');

  const [debouncedSearch, setDebouncedSearch] = useState<string>(search);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  const fetchImplements = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: ImplementFilterParams = {};
      if (debouncedSearch.trim()) params.search = debouncedSearch.trim();
      if (category) params.category = category;
      if (faculty) params.faculty = faculty;
      if (state) params.state = state;

      const data = await getImplements(params, token);
      setImplementsList(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [debouncedSearch, category, faculty, state, token]);

  useEffect(() => {
    fetchImplements();
  }, [fetchImplements]);

  return {
    implementsList,
    loading,
    error,
    search,
    setSearch,
    category,
    setCategory,
    faculty,
    setFaculty,
    state,
    setState,
    refresh: fetchImplements,
  };
}

export function useImplement(id: string | number | null, token?: string | null) {
  const [implement, setImplement] = useState<ImplementItem | null>(null);
  const [loading, setLoading] = useState<boolean>(Boolean(id));
  const [error, setError] = useState<Error | null>(null);

  const fetchDetail = useCallback(async () => {
    if (!id) {
      setImplement(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await getImplementDetail(id, token);
      setImplement(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [id, token]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  return { implement, loading, error, refetch: fetchDetail };
}

export function useCreateReserve(token: string | null) {
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<Error | null>(null);
  const [reserve, setReserve] = useState<ReserveItem | null>(null);
  const isMounted = useRef(true);

  const mutate = async (implementId: number): Promise<ReserveItem> => {
    if (!token) {
      throw new Error('Debes iniciar sesión para realizar una reserva.');
    }
    setLoading(true);
    setError(null);
    try {
      const res = await apiCreateReserve(implementId, token);
      if (isMounted.current) {
        setReserve(res);
      }
      return res;
    } catch (err) {
      const parsedErr = err instanceof Error ? err : new Error(String(err));
      if (isMounted.current) {
        setError(parsedErr);
      }
      throw parsedErr;
    } finally {
      if (isMounted.current) {
        setLoading(false);
      }
    }
  };

  const reset = () => {
    setError(null);
    setReserve(null);
    setLoading(false);
  };

  return { mutate, loading, error, reserve, reset };
}
