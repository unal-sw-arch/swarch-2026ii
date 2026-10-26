import { useState } from "react";
import { useToast } from '../../../../shared/hooks/useToast';
import { useNavigate } from 'react-router-dom';

import "./Hero.css";


export function Hero({ categories = [] }) {
    const { showToast } = useToast();
    const [activeChip, setActiveChip] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedCategoryId, setSelectedCategoryId] = useState('');
    const navigate = useNavigate();

    const handleSearch = () => {
        const queryParams = new URLSearchParams();
        if (searchQuery.trim()) {
            queryParams.append('q', searchQuery.trim());
        }
        if (selectedCategoryId) {
            queryParams.append('categoryId', selectedCategoryId);
        }
        navigate(`/services?${queryParams.toString()}`);
    };

    return (
        <section className="hero">
            <h1>Tu hogar merece el<br /><span>mejor servicio</span></h1>
            <p>Conectamos clientes con profesionales verificados para todos los servicios de tu hogar, cerca de ti y cuando lo necesitas.</p>
            <div className="search-box">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--c-soft)" strokeWidth="2" style={{ flexShrink: 0 }}>
                    <circle cx="11" cy="11" r="8" /><path d="m21 21-4.35-4.35" />
                </svg>
                <input
                    placeholder="¿Qué servicio necesitas? ej: plomero, electricista..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                />
                <div className="s-div"></div>
                <select value={selectedCategoryId} onChange={(e) => setSelectedCategoryId(e.target.value)}>
                    <option value="">Todas las categorías</option>
                    {categories.map((cat) => (
                        <option key={cat.id} value={cat.id}>{cat.name}</option>
                    ))}
                </select>
                <button className="btn btn-primary" style={{ borderRadius: '100px', padding: '9px 20px', flexShrink: 0 }} onClick={handleSearch}>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ width: '14px', marginRight: '4px' }}><circle cx="11" cy="11" r="8" /><path d="m21 21-4.35-4.35" /></svg>
                    Buscar
                </button>
            </div>
            <div className="hero-chips">
                {categories.slice(0, 5).map((cat) => (
                    <span
                        key={cat.id}
                        className={`chip ${activeChip === cat.id ? 'active' : ''}`}
                        onClick={() => {
                            setActiveChip(cat.id);
                            navigate(`/services?categoryId=${cat.id}`);
                        }}
                    >
                        {cat.name}
                    </span>
                ))}
            </div>
        </section>
    );
}