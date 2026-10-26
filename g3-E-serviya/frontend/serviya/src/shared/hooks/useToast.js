import { useState, useCallback } from 'react';

export const useToast = () => {
  const [toasts, setToasts] = useState([]);

  const showToast = useCallback((msg, type = 'info', to = null) => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, msg, type, to }]);

    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4000);
  }, []);

  return { toasts, showToast };
};
