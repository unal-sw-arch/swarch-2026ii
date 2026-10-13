import { useState, useEffect } from 'react';
import { Navbar } from '../../../../shared/components/Navbar/Navbar';
import { Footer } from '../../../../shared/components/Footer/Footer';
import { ToastContainer } from '../../../../shared/components/Toast/Toast';
import { useToast } from '../../../../shared/hooks/useToast';
import { CategoryCard } from '../../components/CategoryCard/CategoryCard';
import { ServiceCard } from '../../components/ServiceCard/ServiceCard';
import { Hero } from '../../components/Hero/Hero';
import { Link } from 'react-router-dom';
import { categoryApi, serviceApi, platformApi, AppNavbar, isAuthenticated } from '../../../../shared';

import "./HomePage.css";

const CATEGORY_ICONS = {
    'Electricidad': <path d="M13 2 3 14h9l-1 8 10-12h-9l1-8z" />,
    'Fontanería': <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />,
    'Limpieza': <><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /><polyline points="9 22 9 12 15 12 15 22" /></>,
    'Jardinería': <path d="M12 22V12m0 0C12 7 7 4 7 4s0 5 5 8zm0 0c0-5 5-8 5-8s0 5-5 8z" />,
    'Pintura': <path d="m2 12 10-8 10 8v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2z" />,
    'Carpintería': <><rect x="3" y="3" width="18" height="18" rx="2" /><path d="M3 9h18M9 21V9" /></>,
    'Cerrajería': <><rect x="3" y="11" width="18" height="11" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" /></>,
    'Reparaciones de electrodomésticos': <path d="M8 7H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3m-1-4-1-4-4 4-4-4-1 4" />,
    'Reparaciones de vehículos': <path d="M5 8h14M5 12h14M5 16h14" />,
    'Tecnología y electrónica': <><rect x="2" y="3" width="20" height="14" rx="2" /><path d="M8 21h8M12 17v4" /></>
};

const DEFAULT_ICON = <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />;



function HomePage() {
    const { toasts, showToast } = useToast();
    const [categories, setCategories] = useState([]);
    const [featServices, setFeatServices] = useState([]);
    const [loadingServices, setLoadingServices] = useState(false);
    const [stats, setStats] = useState({
        activeOfferers: 0,
        completedServices: 0,
        averageRating: 0,
        totalCategories: 0
    });

    useEffect(() => {
        const loadCategories = async () => {
            try {
                const data = await categoryApi.getCategories();
                setCategories(data || []);
            } catch (err) {
                console.error('Error loading categories:', err);
                showToast("Error al cargar categorías: " + err.message, "error");
            }
        };
        loadCategories();
    }, [showToast]);

    useEffect(() => {
        const loadFeaturedServices = async () => {
            setLoadingServices(true);
            try {
                // Busca servicios activos, ordenados por fecha de creación descendente
                const data = await serviceApi.searchServices({
                    page: 0,
                    size: 4,
                    available: true,
                    sort: 'createdAt,desc'
                });
                setFeatServices(data?.content || []);
            } catch (err) {
                console.error('Error loading featured services:', err);
                // Silenciosamente, la sección queda vacía si falla
            } finally {
                setLoadingServices(false);
            }
        };
        loadFeaturedServices();
    }, []);

    useEffect(() => {
        const loadStats = async () => {
            try {
                const data = await platformApi.getStats();
                setStats(data || {
                    activeOfferers: 0,
                    completedServices: 0,
                    averageRating: 0,
                    totalCategories: 0
                });
            } catch (err) {
                console.error('Error loading platform stats:', err);
                // Mantiene los valores por defecto si falla
            }
        };
        loadStats();
    }, []);

    return (
        <>
            {/* Barra según sesión: autenticada (avatar/notificaciones) o pública (login/registro). */}
            {isAuthenticated()
                ? <AppNavbar links={[{ to: '/services', label: 'Servicios' }]} />
                : <Navbar />}
            <Hero categories={categories} />
            {/* Stats Bar */}
            <div className="stats-bar">
                <div className="stat-it"><div className="stat-big">+{stats.activeOfferers}</div><div className="stat-lbl">Oferentes activos</div></div>
                <div className="stat-it"><div className="stat-big">+{stats.completedServices}</div><div className="stat-lbl">Servicios realizados</div></div>
                <div className="stat-it"><div className="stat-big">{stats.averageRating.toFixed(1)} ★</div><div className="stat-lbl">Calificación promedio</div></div>
                <div className="stat-it"><div className="stat-big">{stats.totalCategories}</div><div className="stat-lbl">Categorías</div></div>
            </div>
            {/* Categorías */}
            <section className="sec">
                <div className="sec-title">Categorías</div>
                <div className="sec-sub">Encuentra el especialista que necesitas</div>
                <div className="cats-grid">
                    {categories.map((cat) => (
                        <CategoryCard 
                            key={cat.id} 
                            id={cat.id}
                            name={cat.name} 
                            icon={CATEGORY_ICONS[cat.name] || DEFAULT_ICON} 
                        />
                    ))}
                </div>
            </section>
            {/* Servicios Destacados */}
            <section className="sec sec-gray">
                <div className="sec-title">Servicios destacados</div>
                <div className="sec-sub">Los más recientes</div>
                {loadingServices ? (
                    <p style={{ fontSize: '13px', color: 'var(--c-mid)', textAlign: 'center', padding: '20px' }}>Cargando servicios...</p>
                ) : featServices.length === 0 ? (
                    <p style={{ fontSize: '13px', color: 'var(--c-soft)', textAlign: 'center', padding: '20px' }}>No hay servicios disponibles</p>
                ) : (
                    <div className="s-cards">
                        {featServices.map((s) => {
                            const catName = categories.find(c => c.id === s.categoryId)?.name || 'Servicio';
                            return (
                                <ServiceCard
                                    key={s.id}
                                    id={s.id}
                                    name={s.title}
                                    provider={s.offererName || 'Oferente'}
                                    category={catName}
                                    price={s.priceHourly ? `$${s.priceHourly.toLocaleString()}` : 'Consultar'}
                                    rating={s.averageRating ?? 0}
                                    availability={s.active ? 'Disponible' : 'No disponible'}
                                    icon={CATEGORY_ICONS[catName] || DEFAULT_ICON}
                                    photos={s.photos || []}
                                />
                            );
                        })}
                    </div>
                )}
            </section>
            {/* Cómo funciona */}
            <section className="sec" id="como">
                <div className="sec-title" style={{ textAlign: 'center' }}>¿Cómo funciona?</div>
                <div className="sec-sub" style={{ textAlign: 'center' }}>En 3 simples pasos</div>
                <div className="how-grid">
                    <div className="how-step"><div className="how-num">1</div><div className="how-title">Busca el servicio</div><div className="how-desc">Filtra por categoría, cercanía, precio y disponibilidad. Encuentra al profesional ideal.</div></div>
                    <div className="how-step"><div className="how-num">2</div><div className="how-title">Solicita y agenda</div><div className="how-desc">Elige fecha, hora y dirección. El oferente recibe tu solicitud y la confirma.</div></div>
                    <div className="how-step"><div className="how-num">3</div><div className="how-title">Confirma y califica</div><div className="how-desc">Confirma que el servicio fue realizado y deja tu reseña para ayudar a la comunidad.</div></div>
                </div>
            </section>
            {/* CTA Band */}
            <div className="cta-band">
                <h2>¿Ofreces servicios para el hogar?</h2>
                <p>Únete a ServiYa y llega a cientos de clientes cerca de ti. Gratis para empezar.</p>
                <div className="cta-btns">
                    <Link
                        to="/register"
                        className="btn btn-primary btn-lg">
                        Registrarme como oferente
                    </Link>
                    <Link to="/register" className="btn btn-lg" style={{ background: 'rgba(255,255,255,.1)', color: 'white', border: '2px solid rgba(255,255,255,.25)' }}>
                        Registrarme como cliente
                    </Link>
                </div>
            </div>
            <Footer />
            <ToastContainer toasts={toasts} />
        </>
    );
}

export default HomePage;