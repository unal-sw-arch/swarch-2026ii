/**
 * Read-only star rating rendered with text glyphs (matches the mockup `.stars`).
 * Shows up to 5 stars and, optionally, the numeric value and review count.
 */
export function Stars({ rating = 0, count, showValue = false, size = 13 }) {
    const full = Math.min(Math.round(rating), 5);
    const empty = 5 - full;

    return (
        <span className="stars" style={{ fontSize: size }}>
            {'★'.repeat(full)}
            {'☆'.repeat(Math.max(0, empty))}
            {(showValue || count != null) && (
                <span style={{ color: 'var(--c-soft)', fontSize: '11px', marginLeft: '4px' }}>
                    {showValue ? rating : ''}
                    {count != null ? ` (${count})` : ''}
                </span>
            )}
        </span>
    );
}
