import { useState } from 'react';
import { Icon } from '../Icon/Icon';

/**
 * <img> de foto de SERVICIO con fallback ante error de carga (404 / archivo ausente).
 *
 * A diferencia de <Avatar> (personas → iniciales), aquí el fallback es un placeholder de imagen:
 * el nodo `fallback` provisto (p. ej. el icono de la categoría que la tarjeta ya mostraba cuando
 * no hay foto) o, si no se pasa, un icono de cámara sobre fondo neutro.
 */
export function ServiceImage({ src, alt = '', className, style, onClick, fallback }) {
    // Recuerda qué src falló; si el src cambia se reintenta (sin useEffect).
    const [failedSrc, setFailedSrc] = useState(null);

    if (src && failedSrc !== src) {
        return (
            <img
                src={src}
                alt={alt}
                className={className}
                style={style}
                onClick={onClick}
                onError={() => setFailedSrc(src)}
            />
        );
    }

    if (fallback !== undefined) return fallback;

    return (
        <div
            className={className}
            onClick={onClick}
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--c-bg-s)', color: 'var(--c-soft)', ...style }}
        >
            <Icon name="camera" size={24} />
        </div>
    );
}
