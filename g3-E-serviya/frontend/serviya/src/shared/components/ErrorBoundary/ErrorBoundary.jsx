import React from 'react';
import { Link } from 'react-router-dom';
import { Icon } from '../shared';

export class ErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = { hasError: false, error: null, errorInfo: null };
    }

    static getDerivedStateFromError(error) {
        return { hasError: true, error };
    }

    componentDidCatch(error, errorInfo) {
        console.error('ErrorBoundary caught an error:', error, errorInfo);
        this.setState({ errorInfo });
    }

    render() {
        if (this.state.hasError) {
            return this.props.fallback || (
                <div style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    minHeight: '60vh',
                    padding: '24px',
                    textAlign: 'center'
                }}>
                    <Icon name="alertTriangle" size={48} style={{ color: 'var(--c-danger)', marginBottom: '16px' }} />
                    <h2 style={{ marginBottom: '8px', color: 'var(--c-text)' }}>Algo salió mal</h2>
                    <p style={{ color: 'var(--c-mid)', marginBottom: '24px', maxWidth: '400px' }}>
                        Ocurrió un error inesperado. Nuestro equipo ha sido notificado.
                    </p>
                    <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', justifyContent: 'center' }}>
                        <Link to="/" className="btn btn-primary">
                            <Icon name="home" size={15} /> Ir al inicio
                        </Link>
                        <Link to="/login" className="btn btn-ghost" style={{ border: '1px solid var(--c-border)' }}>
                            <Icon name="login" size={15} /> Iniciar sesión
                        </Link>
                        <button
                            className="btn btn-secondary"
                            onClick={() => window.location.reload()}
                        >
                            <Icon name="refreshCw" size={15} /> Recargar página
                        </button>
                    </div>
                    {process.env.NODE_ENV === 'development' && this.state.error && (
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
                                {this.state.error?.toString()}
                                {this.state.errorInfo?.componentStack && '\n\n' + this.state.errorInfo.componentStack}
                            </pre>
                        </details>
                    )}
                </div>
            );
        }

        return this.props.children;
    }
}

export default ErrorBoundary;