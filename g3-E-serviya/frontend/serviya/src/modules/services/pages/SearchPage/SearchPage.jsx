import { useState, useEffect } from "react";
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AppNavbar, Icon, Stars, ToastContainer, useToast, serviceApi, categoryApi, getApiImageUrl, ServiceImage } from '../../../../shared';

import './SearchPage.css';

const availBadge = (active) => (active ? 'badge-success' : 'badge-warn');

export function SearchPage() {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const { toasts, showToast } = useToast();

    // Categorías reales de la API
    const [categories, setCategories] = useState([]);

    // Filtros aplicados reales, inicializados desde query params
    const [nameQuery, setNameQuery] = useState(() => searchParams.get('q') || "");
    const [activeCatId, setActiveCatId] = useState(() => {
        const catId = searchParams.get('categoryId');
        return catId ? Number(catId) : null;
    });
    const [offererIdFilter, setOffererIdFilter] = useState(() => {
        const oid = searchParams.get('offererId');
        return oid ? Number(oid) : null;
    });

    // Estado de la búsqueda y paginación
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [totalElements, setTotalElements] = useState(0);

    const [minPrice, setMinPrice] = useState("");
    const [maxPrice, setMaxPrice] = useState("");
    const [minRating, setMinRating] = useState(null);
    const [maxDistanceKm, setMaxDistanceKm] = useState(null);
    const [sort, setSort] = useState("createdAt,desc");

    // Inputs locales (sidebar / buscador superior), inicializados desde query params
    const [topSearch, setTopSearch] = useState(() => searchParams.get('q') || "");
    const [sidebarName, setSidebarName] = useState(() => searchParams.get('q') || "");
    const [sidebarMinPrice, setSidebarMinPrice] = useState("");
    const [sidebarMaxPrice, setSidebarMaxPrice] = useState("");
    const [sidebarMinRating, setSidebarMinRating] = useState(null);
    const [sidebarDistance, setSidebarDistance] = useState(null);

    // Ubicación real del usuario (geolocalización del navegador)
    const [userLocation, setUserLocation] = useState(null);

    // Cargar categorías al montar
    useEffect(() => {
        const loadCategories = async () => {
            try {
                const data = await categoryApi.getCategories();
                setCategories(data || []);
            } catch (err) {
                showToast("Error al cargar categorías: " + err.message, "error");
            }
        };
        loadCategories();
    }, []);

    // Obtener ubicación real del usuario
    useEffect(() => {
        if (!navigator.geolocation) return;
        navigator.geolocation.getCurrentPosition(
            (pos) => setUserLocation({
                latitude: pos.coords.latitude,
                longitude: pos.coords.longitude
            }),
            () => setUserLocation(null)
        );
    }, []);

    // Sincronizar URL query params con estados locales
    useEffect(() => {
        const q = searchParams.get('q') || "";
        const catId = searchParams.get('categoryId') ? Number(searchParams.get('categoryId')) : null;
        const oid = searchParams.get('offererId') ? Number(searchParams.get('offererId')) : null;

        setNameQuery(q);
        setTopSearch(q);
        setSidebarName(q);
        setActiveCatId(catId);
        setOffererIdFilter(oid);
    }, [searchParams]);

    // Función de búsqueda
    const fetchServices = async (page = 0, currentCatId = activeCatId, currentSort = sort) => {
        setLoading(true);
        try {
            const params = {
                page: page,
                size: 20,
            };
            if (nameQuery.trim()) params.name = nameQuery.trim();
            if (currentCatId) params.categoryId = currentCatId;
            if (offererIdFilter) params.offererId = offererIdFilter;
            if (minPrice) params.minPrice = minPrice;
            if (maxPrice) params.maxPrice = maxPrice;
            if (minRating) params.minRating = minRating;
            if (maxDistanceKm && userLocation) {
                params.maxDistanceKm = maxDistanceKm;
                params.latitude = userLocation.latitude;
                params.longitude = userLocation.longitude;
            }
            if (currentSort) {
                params.sort = currentSort;
            }

            const data = await serviceApi.searchServices(params);
            setResults(data.content || []);
            setTotalPages(data.totalPages || 1);
            setTotalElements(data.totalElements || 0);
            setCurrentPage(data.number || 0);
        } catch (err) {
            showToast("Error al buscar servicios: " + err.message, "error");
        } finally {
            setLoading(false);
        }
    };

    // Recargar cuando cambian los filtros principales o ordenación
    useEffect(() => {
        fetchServices(0, activeCatId, sort);
    }, [nameQuery, activeCatId, offererIdFilter, minPrice, maxPrice, minRating, maxDistanceKm, sort, userLocation]);

    // Manejar selección de categoría y actualizar URL params
    const handleCategorySelect = (catId) => {
        const params = new URLSearchParams(searchParams);
        if (catId) {
            params.set('categoryId', catId);
        } else {
            params.delete('categoryId');
        }
        params.delete('page');
        setSearchParams(params);
    };

    // Manejar submit del buscador superior
    const handleTopSearchSubmit = (e) => {
        if (e) e.preventDefault();
        const params = new URLSearchParams(searchParams);
        if (topSearch.trim()) {
            params.set('q', topSearch.trim());
        } else {
            params.delete('q');
        }
        params.delete('page');
        setSearchParams(params);
    };

    // Aplicar filtros de la barra lateral
    const handleApplyFilters = () => {
        setNameQuery(sidebarName);
        setTopSearch(sidebarName);
        setMinPrice(sidebarMinPrice);
        setMaxPrice(sidebarMaxPrice);
        setMinRating(sidebarMinRating);
        setMaxDistanceKm(sidebarDistance);

        const params = new URLSearchParams(searchParams);
        if (sidebarName.trim()) {
            params.set('q', sidebarName.trim());
        } else {
            params.delete('q');
        }
        params.delete('page');
        setSearchParams(params);

        showToast("Filtros aplicados", "success");
    };

    // Limpiar todos los filtros
    const handleClearAll = () => {
        setTopSearch("");
        setSidebarName("");
        setSidebarMinPrice("");
        setSidebarMaxPrice("");
        setSidebarMinRating(null);
        setSidebarDistance(null);

        setNameQuery("");
        setMinPrice("");
        setMaxPrice("");
        setMinRating(null);
        setMaxDistanceKm(null);
        setActiveCatId(null);
        setOffererIdFilter(null);

        setSearchParams({});
        showToast("Filtros limpiados", "info");
    };

    const getActiveFilterLabels = () => {
        const list = [];
        if (nameQuery) list.push({ key: 'name', val: `Búsqueda: "${nameQuery}"` });
        if (activeCatId) {
            const cat = categories.find(c => c.id === activeCatId);
            if (cat) list.push({ key: 'cat', val: cat.name });
        }
        if (offererIdFilter) list.push({ key: 'offerer', val: `Oferente #${offererIdFilter}` });
        if (minPrice || maxPrice) {
            list.push({ key: 'price', val: `Precio: ${minPrice ? `$${minPrice}` : '$0'} - ${maxPrice ? `$${maxPrice}` : '∞'}` });
        }
        if (minRating) list.push({ key: 'rating', val: `${minRating}★+` });
        if (maxDistanceKm) list.push({ key: 'distance', val: `Cerca de ${maxDistanceKm}km` });
        return list;
    };

    const handleRemoveActiveFilter = (item) => {
        const params = new URLSearchParams(searchParams);
        if (item.key === 'name') {
            params.delete('q');
            setSearchParams(params);
            setNameQuery("");
            setTopSearch("");
            setSidebarName("");
        } else if (item.key === 'cat') {
            params.delete('categoryId');
            setSearchParams(params);
            setActiveCatId(null);
        } else if (item.key === 'offerer') {
            params.delete('offererId');
            setSearchParams(params);
            setOffererIdFilter(null);
        } else if (item.key === 'price') {
            setMinPrice("");
            setMaxPrice("");
            setSidebarMinPrice("");
            setSidebarMaxPrice("");
        } else if (item.key === 'rating') {
            setMinRating(null);
            setSidebarMinRating(null);
        } else if (item.key === 'distance') {
            setMaxDistanceKm(null);
            setSidebarDistance(null);
        }
    };

    return (
        <>
            <AppNavbar avatar="JP" links={[{ to: '/services', label: 'Servicios' }]} />

            <div className="search-hd">
                <form className="search-bar" onSubmit={handleTopSearchSubmit}>
                    <Icon name="search" size={16} style={{ color: 'var(--c-soft)' }} />
                    <input 
                        placeholder="Buscar servicios..." 
                        value={topSearch}
                        onChange={(e) => setTopSearch(e.target.value)}
                    />
                    <button type="submit" className="btn btn-primary btn-sm">
                        <Icon name="search" size={13} />Buscar
                    </button>
                </form>
                <div className="search-chips">
                    <span 
                        className={`chip ${activeCatId === null ? 'active' : ''}`} 
                        onClick={() => handleCategorySelect(null)}
                    >
                        Todas
                    </span>
                    {categories.map((c) => (
                        <span 
                            key={c.id} 
                            className={`chip ${activeCatId === c.id ? 'active' : ''}`} 
                            onClick={() => handleCategorySelect(c.id)}
                        >
                            {c.name}
                        </span>
                    ))}
                </div>
            </div>

            <div className="search-layout">
                <aside className="filters">
                    <div className="filter-hd">
                        <span>Filtros</span>
                        <span className="filter-clear" onClick={handleClearAll}>Limpiar todo</span>
                    </div>

                    <div className="filter-sec">
                        <div className="filter-sec-title">Categoría</div>
                        <label className="filter-opt">
                            <input 
                                type="radio" 
                                name="sidebar-category" 
                                checked={activeCatId === null} 
                                onChange={() => handleCategorySelect(null)} 
                            /> Todas
                        </label>
                        {categories.map((c) => (
                            <label className="filter-opt" key={c.id}>
                                <input 
                                    type="radio" 
                                    name="sidebar-category" 
                                    checked={activeCatId === c.id} 
                                    onChange={() => handleCategorySelect(c.id)} 
                                /> {c.name}
                            </label>
                        ))}
                    </div>
                    <div className="divider" />

                    <div className="filter-sec">
                        <div className="filter-sec-title">Nombre / Oferente</div>
                        <div className="input-wrap" style={{ marginTop: '6px' }}>
                            <div className="input-ico"><Icon name="search" size={15} /></div>
                            <input 
                                className="input" 
                                placeholder="Buscar por nombre..." 
                                style={{ fontSize: '12px', padding: '8px 10px 8px 34px' }} 
                                value={sidebarName}
                                onChange={(e) => setSidebarName(e.target.value)}
                            />
                        </div>
                    </div>
                    <div className="divider" />

                    <div className="filter-sec">
                        <div className="filter-sec-title">Puntuación mínima</div>
                        <div className="star-filter">
                            {[
                                { lbl: 'Todos', val: null },
                                { lbl: '3★+', val: 3 },
                                { lbl: '4★+', val: 4 },
                                { lbl: '5★', val: 5 }
                            ].map((s) => (
                                <div 
                                    key={s.lbl} 
                                    className={`star-opt ${sidebarMinRating === s.val ? 'active' : ''}`} 
                                    onClick={() => setSidebarMinRating(s.val)}
                                >
                                    {s.lbl}
                                </div>
                            ))}
                        </div>
                    </div>
                    <div className="divider" />

                    <div className="filter-sec">
                        <div className="filter-sec-title">Precio (por servicio)</div>
                        <div className="price-wrap">
                            <input 
                                className="price-in" 
                                placeholder="$mín" 
                                value={sidebarMinPrice}
                                onChange={(e) => setSidebarMinPrice(e.target.value)}
                            />
                            <span style={{ color: 'var(--c-soft)', fontSize: '12px' }}>—</span>
                            <input 
                                className="price-in" 
                                placeholder="$máx" 
                                value={sidebarMaxPrice}
                                onChange={(e) => setSidebarMaxPrice(e.target.value)}
                            />
                        </div>
                    </div>
                    <div className="divider" />

                    <div className="filter-sec">
                        <div className="filter-sec-title">Cercanía</div>
                        <select 
                            className="input" 
                            style={{ fontSize: '12px', padding: '8px 10px' }}
                            value={sidebarDistance || ''}
                            onChange={(e) => setSidebarDistance(e.target.value ? Number(e.target.value) : null)}
                        >
                            <option value="">Cualquier distancia</option>
                            <option value="5">Menos de 5 km</option>
                            <option value="10">Menos de 10 km</option>
                            <option value="20">Menos de 20 km</option>
                        </select>
                        {sidebarDistance && !userLocation && (
                            <div style={{ fontSize: '11px', color: '#e74c3c', marginTop: '6px' }}>
                                Activa la ubicación en tu navegador para usar este filtro.
                            </div>
                        )}
                    </div>
                    <div className="divider" />

                    <button className="btn btn-primary btn-full" style={{ marginTop: '14px' }} onClick={handleApplyFilters}>
                        <Icon name="filter" size={15} />Aplicar filtros
                    </button>
                </aside>

                <div>
                    <div className="results-hd">
                        <div className="results-count">
                            <strong>{totalElements} resultados</strong> encontrados
                        </div>
                        <select 
                            className="sort-sel"
                            value={sort}
                            onChange={(e) => setSort(e.target.value)}
                        >
                            <option value="createdAt,desc">Más recientes</option>
                            <option value="priceHourly,asc">Menor precio</option>
                            <option value="priceHourly,desc">Mayor precio</option>
                            <option value="title,asc">Título (A-Z)</option>
                        </select>
                    </div>

                    <div className="active-filters">
                        {getActiveFilterLabels().map((item) => (
                            <div className="af" key={item.key}>
                                {item.val} 
                                <button onClick={() => handleRemoveActiveFilter(item)}>×</button>
                            </div>
                        ))}
                    </div>

                    <div className="r-cards">
                        {loading ? (
                            <div style={{ padding: '40px', gridColumn: '1 / -1', textAlign: 'center', color: 'var(--c-soft)' }}>
                                <div className="loading-spinner" style={{ margin: '0 auto 10px', width: '30px', height: '30px', border: '3px solid var(--c-border)', borderTop: '3px solid var(--c-primary)', borderRadius: '50%', animation: 'spin 1s linear infinite' }} />
                                Cargando servicios...
                            </div>
                        ) : results.length === 0 ? (
                            <div style={{ padding: '40px', gridColumn: '1 / -1', textAlign: 'center', color: 'var(--c-soft)' }}>
                                No se encontraron servicios que coincidan con la búsqueda.
                            </div>
                        ) : (
                            results.map((s) => {
                                const serviceCatName = categories.find(c => c.id === s.categoryId)?.name || 'Servicio';
                                return (
                                    <div className="r-card" key={s.id} onClick={() => navigate(`/services/${s.id}`)}>
                                        <div className="r-card-img">
                                            <ServiceImage
                                                src={getApiImageUrl(s.photos?.[0])}
                                                alt={s.title}
                                                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                                fallback={<Icon name="wrench" size={38} strokeWidth={1.5} />}
                                            />
                                            <div className="r-card-av">
                                                <span className={`badge ${availBadge(s.active)}`}>
                                                    {s.active ? 'Disponible' : 'Inactivo'}
                                                </span>
                                            </div>
                                        </div>
                                        <div className="r-card-body">
                                            <div className="r-card-name">{s.title}</div>
                                            <div className="r-oferer">
                                                <div className="av av-xs">OF</div>
                                                <span>{s.offererName || 'Oferente'}</span>
                                            </div>
                                            <div style={{ fontSize: '11px', marginBottom: '8px' }}>
                                                <span style={{ color: 'var(--c-primary)', fontWeight: 600 }}>{serviceCatName}</span>
                                            </div>
                                            <div style={{ fontSize: '11px', marginBottom: '8px' }}>
                                                <Stars rating={s.averageRating ?? 0} showValue count={s.totalRatings ?? 0} size={11} />
                                            </div>
                                            <div className="r-card-ft">
                                                <span className="r-price">desde ${s.priceHourly ? s.priceHourly.toLocaleString() : '0'}</span>
                                                {s.operationRadiusKm && (
                                                    <span className="r-loc">
                                                        <Icon name="mapPin" size={11} />
                                                        {s.operationRadiusKm} km
                                                    </span>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })
                        )}
                    </div>

                    {totalPages > 1 && (
                        <div className="pager">
                            {Array.from({ length: totalPages }, (_, i) => (
                                <button 
                                    key={i} 
                                    className={`btn ${currentPage === i ? 'btn-primary' : 'btn-ghost'} btn-sm`}
                                    onClick={() => fetchServices(i)}
                                >
                                    {i + 1}
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            </div>
            <ToastContainer toasts={toasts} />
            
            {/* Agregar una animación simple de spin en un tag style */}
            <style>{`
                @keyframes spin {
                    0% { transform: rotate(0deg); }
                    100% { transform: rotate(360deg); }
                }
            `}</style>
        </>
    );
}

export default SearchPage;
