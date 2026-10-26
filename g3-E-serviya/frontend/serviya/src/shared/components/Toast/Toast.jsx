import { Link } from 'react-router-dom';

const TOAST_TYPES = {
  info: { bg: '#0F172A', ico: <><path d="M12 8v4m0 4h.01"/><circle cx="12" cy="12" r="10"/></> },
  success: { bg: '#065F46', ico: <path d="M20 6 9 17l-5-5"/> },
  danger: { bg: '#991B1B', ico: <><circle cx="12" cy="12" r="10"/><path d="m15 9-6 6m0-6 6 6"/></> },
  warn: { bg: '#92400E', ico: <><path d="m10.29 3.86-8.5 14.72A1 1 0 0 0 2.68 20h16.64a1 1 0 0 0 .89-1.42l-8.5-14.72a1 1 0 0 0-1.76 0z"/><path d="M12 9v4m0 4h.01"/></> }
};

export const ToastContainer = ({ toasts }) => {
  return (
    <div style={{ position: 'fixed', bottom: '20px', right: '20px', zIndex: 9999, display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {toasts.map((t) => {
        const config = TOAST_TYPES[t.type] || TOAST_TYPES.info;
        const content = (
          <div
            key={t.id}
            style={{
              background: config.bg,
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              padding: '12px 16px',
              borderRadius: '8px',
              color: 'white',
              boxShadow: '0 4px 6px rgba(0,0,0,0.15)',
              animation: 'fadeIn 0.3s ease',
              fontFamily: 'sans-serif',
              fontSize: '14px'
            }}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round">
              {config.ico}
            </svg>
            <span>{t.msg}</span>
          </div>
        );
        return t.to
          ? <Link key={t.id} to={t.to} style={{ textDecoration: 'none' }}>{content}</Link>
          : content;
      })}
    </div>
  );
};
