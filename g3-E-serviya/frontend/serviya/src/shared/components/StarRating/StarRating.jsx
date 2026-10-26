import { useState } from "react";
/**
 * Interactive 5-star picker used in confirm/review modals.
 * Controlled via `value` / `onChange`.
 */
export function StarRating({ value = 0, onChange, size = 28 }) {
    const [hover, setHover] = useState(0);
    const active = hover || value;

    return (
        <div style={{ display: 'flex', gap: '6px', cursor: 'pointer' }} onMouseLeave={() => setHover(0)}>
            {[1, 2, 3, 4, 5].map((n) => (
                <svg
                    key={n}
                    viewBox="0 0 24 24"
                    width={size}
                    height={size}
                    fill={n <= active ? '#F59E0B' : '#CBD5E1'}
                    onMouseEnter={() => setHover(n)}
                    onClick={() => onChange?.(n)}
                    style={{ transition: 'fill .12s' }}
                >
                    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
                </svg>
            ))}
        </div>
    );
}
