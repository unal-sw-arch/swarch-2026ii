import { useNavigate } from 'react-router-dom';
import { getApiImageUrl, ServiceImage } from '../../../../shared';

import "./ServiceCard.css";

export const ServiceCard = ({
    id,
    name,
    provider,
    category,
    price,
    rating,
    icon,
    availability,
    photos = [],
}) => {
    const navigate = useNavigate();
    return (
        <div
            className="s-card"
            onClick={() => navigate(`/services/${id}`)}>
            <div className="s-card-img">
                <ServiceImage
                    src={getApiImageUrl(photos[0])}
                    alt={name}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    fallback={
                        <svg
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="1.5">
                            {icon}
                        </svg>
                    }
                />
                <div className="s-card-avail">
                    <span
                        className={`badge ${availability === 'Hoy'
                            ? 'badge-success'
                            : 'badge-warn'
                            }`}>
                        {availability}
                    </span>
                </div>
            </div>
            <div className="s-card-body">
                <div className="s-card-name">
                    {name}
                </div>
                <div className="s-oferer">
                    <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2">
                        <circle
                            cx="12"
                            cy="8"
                            r="4" />
                        <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" />
                    </svg>
                    {provider} · {category}
                </div>
                <div className="s-row">
                    <span className="stars">
                        {'★'.repeat(Math.min(Math.round(rating), 5))}
                        <span
                            style={{
                                color: 'var(--c-soft)',
                                fontSize: '11px',
                                marginLeft: '4px',
                            }}>
                            {rating.toFixed(1)}
                        </span>
                    </span>
                    <span className="s-price">
                        desde {price}
                    </span>
                </div>
            </div>
        </div>
    );
};