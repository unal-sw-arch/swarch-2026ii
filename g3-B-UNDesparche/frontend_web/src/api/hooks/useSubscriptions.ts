import { useState, useEffect, useCallback } from 'react';
import { subscribeToEvent, unsubscribeFromEvent } from '../events';

export function useSubscriptions(
  eventId: number | string | undefined,
  token: string | null = null,
  initialSubscribed = false
) {
  const [isSubscribed, setIsSubscribed] = useState<boolean>(initialSubscribed);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const storageKey = eventId ? `guest_subscription_${eventId}` : null;

  useEffect(() => {
    if (!token && storageKey) {
      const stored = localStorage.getItem(storageKey);
      if (stored === 'true') {
        setIsSubscribed(true);
      }
    }
  }, [storageKey, token]);

  const subscribe = useCallback(
    async (guestEmail?: string) => {
      if (!eventId) return;
      setLoading(true);
      setError(null);
      const previousState = isSubscribed;
      setIsSubscribed(true);

      try {
        await subscribeToEvent(eventId, guestEmail, token);
        if (!token && storageKey) {
          localStorage.setItem(storageKey, 'true');
        }
      } catch (err) {
        setIsSubscribed(previousState);
        const msg = err instanceof Error ? err.message : 'Error al suscribirse al evento';
        setError(msg);
        throw err;
      } finally {
        setLoading(false);
      }
    },
    [eventId, token, isSubscribed, storageKey]
  );

  const unsubscribe = useCallback(async () => {
    if (!eventId) return;
    setLoading(true);
    setError(null);
    const previousState = isSubscribed;
    setIsSubscribed(false);

    try {
      await unsubscribeFromEvent(eventId, token);
      if (!token && storageKey) {
        localStorage.removeItem(storageKey);
      }
    } catch (err) {
      setIsSubscribed(previousState);
      const msg = err instanceof Error ? err.message : 'Error al cancelar la suscripción';
      setError(msg);
      throw err;
    } finally {
      setLoading(false);
    }
  }, [eventId, token, isSubscribed, storageKey]);

  const toggleSubscription = useCallback(
    async (guestEmail?: string) => {
      if (isSubscribed) {
        await unsubscribe();
      } else {
        await subscribe(guestEmail);
      }
    },
    [isSubscribed, subscribe, unsubscribe]
  );

  return {
    isSubscribed,
    loading,
    error,
    subscribe,
    unsubscribe,
    toggleSubscription,
  };
}
