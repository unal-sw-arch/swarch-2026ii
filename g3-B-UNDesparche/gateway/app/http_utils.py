import httpx
from fastapi import Request, Response
from starlette.datastructures import UploadFile


def relay(upstream: httpx.Response) -> Response:
    """
    Devuelve al cliente la respuesta de un microservicio tal cual: mismo código,
    mismo cuerpo (bytes) y mismo content-type. No parsea el JSON, así que funciona
    también con respuestas vacías (204 en un DELETE) o que no son JSON.
    """
    return Response(
        content=upstream.content,
        status_code=upstream.status_code,
        media_type=upstream.headers.get("content-type"),
    )


def forward_query_params(request: Request) -> list[tuple[str, str]]:
    """
    Devuelve los query params de la petición (?category=...&page=2) para reenviarlos
    al microservicio. multi_items conserva las claves repetidas (?a=1&a=2).
    """
    return list(request.query_params.multi_items())


async def extract_payload(request: Request) -> dict:
    """
    Extrae el cuerpo de la petición en la forma que espera ServiceClient.request:
    - application/json     -> {"json": ...}
    - multipart/urlencoded -> {"data": campos, "files": archivos}
    Se usa con `**await extract_payload(request)`.
    """
    content_type = request.headers.get("content-type", "")
    if content_type.startswith("application/json"):
        return {"json": await request.json()}

    form = await request.form()
    data = {}
    files = {}
    for key, value in form.items():
        if isinstance(value, UploadFile):
            files[key] = (
                value.filename,
                value.file,
                value.content_type or "application/octet-stream",
            )
        else:
            data[key] = value
    return {"data": data, "files": files}
