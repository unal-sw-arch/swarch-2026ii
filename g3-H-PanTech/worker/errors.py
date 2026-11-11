class InvalidEvent(Exception):
    """El evento es inválido o irrecuperable: no se reintenta, va directo a la DLQ."""


class TransientError(Exception):
    """Fallo temporal (red, servicio caído, carrera de orden): se reintenta con backoff."""
