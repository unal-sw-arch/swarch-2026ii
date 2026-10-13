import { useEffect } from "react";
/**
 * Generic modal. Renders nothing when `open` is false.
 * Closes on overlay click and on Escape.
 */
export function Modal({ open, onClose, children, maxWidth = 460 }) {
    useEffect(() => {
        if (!open) return;
        const onKey = (e) => e.key === 'Escape' && onClose?.();
        document.addEventListener('keydown', onKey);
        return () => document.removeEventListener('keydown', onKey);
    }, [open, onClose]);

    if (!open) return null;

    return (
        <div className="modal-overlay" onClick={(e) => e.target === e.currentTarget && onClose?.()}>
            <div className="modal" style={{ maxWidth }}>
                {children}
            </div>
        </div>
    );
}
