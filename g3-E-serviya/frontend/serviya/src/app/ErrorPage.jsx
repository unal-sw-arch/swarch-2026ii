import { useRouteError } from 'react-router-dom';
import { Link } from 'react-router-dom';
import { Icon } from '../shared';

export function ErrorPage() {
    const error = useRouteError();

    console.error('Route error:', error);

    const is404 = error?.status === 404 || error?.statusText === 'Not Found';
    const is401 = error?.status === 401;
    const is500 = error?.status === 500 || error?.statusText === 'Internal Server Error';

    return (
        <div style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: '60vh',
            padding: '24px',
            textAlign: 'center'
        }}>
            <Icon name={is404 ? 'search' : is401 ? 'lock' : 'alertTriangle'} size={48} style={{ color: 'var(--c-danger)', marginBottom: '16px' }} />
            <h2 style={{ marginBottom: '8px', color: 'var(--c-text)' }}>
                {is404 ? 'Página no encontrada' : is401 ? 'No autorizado' : is500 ? 'Error del servidor' : 'Error'}
            </h2>
            <p style={{ color: 'var(--c-mid)', marginBottom: '24px', maxWidth: '400px' }}>
                {is404 ? 'La página que buscas no existe o ha sido movida.'
                    : is401 ? 'Tu sesión ha expirado. Por favor, inicia sesión de nuevo.'
                    : is500 ? 'Ocurrió un error en el servidor. Intenta más tarde.'
                    : 'Ocurrió un error inesperado.'}
            </p>
            <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', justifyContent: 'center' }}>
                <Link to="/" className="btn btn-primary">
                    <Icon name="home" size={15} /> Ir al inicio
                </Link>
                {is401 && (
                    <Link to="/login" className="btn btn-secondary">
                        <Icon name="login" size={15} /> Iniciar sesión
                    </Link>
                )}
                <button
                    className="btn btn-ghost"
                    style={{ border: '1px solid var(--c-border)' }}
                    onClick={() => window.history.back()}
                >
                    <Icon name="chevronLeft" size={15} /> Volver
                </button>
            </div>
            {process.env.NODE_ENV === 'development' && (
                <details style={{ marginTop: '24px', textAlign: 'left', maxWidth: '600px' }}>
                    <summary style={{ cursor: 'pointer', color: 'var(--c-soft)' }}>Detalles técnicos (desarrollo)</summary>
                    <pre style={{
                        marginTop: '12px',
                        padding: '12px',
                        background: 'var(--c-bg-s)',
                        borderRadius: 'var(--r-md)',
                        fontSize: '11px',
                        overflow: 'auto',
                        color: 'var(--c-text)'
                    }}>
                        {JSON.stringify(error, null, 2)}
                    </pre>
                </details>
            )}
        </div>
    );
}

export default ErrorPage;