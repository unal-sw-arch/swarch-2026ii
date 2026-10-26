import { useState, useCallback, useRef } from 'react';

export interface UseApiMutationResult<TData, TVariables> {
  data: TData | null;
  loading: boolean;
  error: Error | null;
  mutate: (variables: TVariables) => Promise<TData>;
  reset: () => void;
}

export function useApiMutation<TData, TVariables>(
  mutationFn: (variables: TVariables) => Promise<TData>
): UseApiMutationResult<TData, TVariables> {
  const [data, setData] = useState<TData | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<Error | null>(null);
  const isMountedRef = useRef(true);

  const mutate = useCallback(
    async (variables: TVariables): Promise<TData> => {
      setLoading(true);
      setError(null);
      try {
        const result = await mutationFn(variables);
        if (isMountedRef.current) {
          setData(result);
        }
        return result;
      } catch (err) {
        const parsedError = err instanceof Error ? err : new Error(String(err));
        if (isMountedRef.current) {
          setError(parsedError);
        }
        throw parsedError;
      } finally {
        if (isMountedRef.current) {
          setLoading(false);
        }
      }
    },
    [mutationFn]
  );

  const reset = useCallback(() => {
    setData(null);
    setError(null);
    setLoading(false);
  }, []);

  return { data, loading, error, mutate, reset };
}
