import { useState } from 'react';

/**
 * Contenido de un avatar circular: muestra la foto si carga, o las iniciales como fallback.
 * Se coloca DENTRO del contenedor circular existente (.av, .nav-av, etc.).
 *
 * Clave: si la imagen falla al cargar (404, ruta registrada pero archivo ausente, etc.) cae a
 * las iniciales en vez de dejar el icono de imagen rota + texto alt del navegador.
 */
export function Avatar({ src, initials = '', alt = 'Foto de perfil' }) {
    // Recuerda qué src falló; si el src cambia (p. ej. tras subir una foto nueva) se reintenta.
    const [failedSrc, setFailedSrc] = useState(null);

    if (src && failedSrc !== src) {
        return (
            <img
                src={src}
                alt={alt}
                onError={() => setFailedSrc(src)}
                style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: '50%' }}
            />
        );
    }
    return <>{initials}</>;
}
