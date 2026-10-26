import { Link } from 'react-router-dom';

import "./Navbar.css";

export const Navbar = () => {
    const scrollToHow = () => {
        document
        .getElementById("como")
        ?.scrollIntoView({
            behavior: "smooth",
        });
    };
    return (
        <nav className="nav">
            <Link
                to="/"
                className="nav-logo">
                <img
                    src="/logo.svg"
                    alt="ServiYa"
                    style={{ height: '24px' }} />
            </Link>
            <div className="nav-spacer"></div>
            <div className="nav-links">
                <Link to="/services">
                    Servicios
                </Link>
                <button
                    className="nav-link-btn"
                    onClick={scrollToHow}>
                    Cómo funciona
                </button>
                <Link to="/register">
                    Sé oferente
                </Link>
            </div>
            <div className="nav-actions">
                <Link
                    to="/login"
                    className="btn btn-outline btn-sm">
                    Iniciar sesión
                </Link>
                <Link
                    to="/register"
                    className="btn btn-primary btn-sm">
                    Registrarse
                </Link>
            </div>
        </nav>
    );
};