import { Link } from 'react-router-dom';

import "./Footer.css";

export const Footer = () => {
    const scrollToHow = () => {
        document
            .getElementById("como")
            ?.scrollIntoView({
                behavior: "smooth",
            });
    };
    return (
        <footer>
            <div className="f-grid">
                <div>
                    <img
                        src="/logo.svg"
                        style={{
                            height: '28px',
                            filter:
                                'brightness(0) invert(1) opacity(.85)',
                        }}
                        alt="ServiYa" />
                    <div className="f-desc">
                        Conectamos hogares con los mejores
                        profesionales de tu ciudad.
                        Rápido, seguro y confiable.
                    </div>
                </div>
                <div className="f-col">
                    <h4>
                        Plataforma
                    </h4>
                    <Link to="/services">
                        Buscar servicios
                    </Link>
                    <button
                        className="footer-link-btn"
                        onClick={scrollToHow}>
                        Cómo funciona
                    </button>
                    <Link to="/register">
                        Sé oferente
                    </Link>
                </div>
                <div className="f-col">
                    <h4>
                        Soporte
                    </h4>
                    <Link to="/help">
                        Centro de ayuda
                    </Link>

                    <Link to="/terms">
                        Términos de uso
                    </Link>

                    <Link to="/privacy">
                        Privacidad y datos
                    </Link>
                </div>
                <div className="f-col">
                    <h4>
                        Contacto
                    </h4>
                    <Link to="mailto:hola@serviya.co">
                        hola@serviya.co
                    </Link>
                    <Link to="https://wa.me/57123456789" target="_blank">
                        WhatsApp
                    </Link>
                    <Link to="https://instagram.com/serviya" target="_blank">
                        Instagram
                    </Link>
                </div>
            </div>
            <div className="f-bottom">
                <span>
                    © 2025 ServiYa.
                    Todos los derechos reservados.
                </span>
                <span>
                    Hecho con cariño en Colombia
                </span>
            </div>
        </footer>
    );
};