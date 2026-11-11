"""Offline inference, versioned video persistence and collection management."""

import asyncio
from contextlib import AsyncExitStack, asynccontextmanager, suppress
import logging
from uuid import UUID

import httpx
from fastapi import FastAPI, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.exception_handlers import request_validation_exception_handler
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool

from app.config import Settings
from app.inference import InferenceError, ModelUnavailableError, TextEmbeddingEngine, read_spec
from app.metadata import TEXT_TEMPLATE_VERSION, VideoMetadata
from app.qdrant_collections import CollectionManager, CollectionSetupError, definitions_for
from app.video_errors import VideoError
from app.video_repository import VideoEmbeddingRepository
from app.video_schemas import VideoEmbeddingRequest, VideoEmbeddingResponse
from app.video_service import VideoEmbeddingService
from app.video_state import UserStateStore, VideoStateStore
from app.user_repository import UserEmbeddingRepository
from app.user_schemas import UserRecalculateRequest, UserRecalculateResponse
from app.user_service import UserEmbeddingService


logger = logging.getLogger(__name__)


def create_app(
    settings: Settings | None = None,
    engine: TextEmbeddingEngine | None = None,
    qdrant_transport: httpx.AsyncBaseTransport | None = None,
    qdrant_sync_transport: httpx.BaseTransport | None = None,
    video_store: VideoStateStore | None = None,
    user_store: UserStateStore | None = None,
) -> FastAPI:
    settings = settings or Settings.from_environment()
    engine = engine or TextEmbeddingEngine(
        read_spec(settings.model_key), settings.model_root, settings.cpu_threads
    )
    collection_definitions = definitions_for(settings, engine.spec)
    video_store = video_store or VideoStateStore(settings.state_root, settings.video_lock_timeout_seconds)
    user_store = user_store or UserStateStore(settings.state_root, settings.user_lock_timeout_seconds)

    @asynccontextmanager
    async def lifespan(application: FastAPI):
        application.state.embedding_engine = engine
        try:
            await run_in_threadpool(video_store.initialize)
        except VideoError:
            logger.exception("Persistent video state unavailable")
        try:
            await run_in_threadpool(user_store.initialize)
        except VideoError:
            logger.exception("Persistent user state unavailable")
        try:
            await run_in_threadpool(engine.load)
        except ModelUnavailableError as error:
            # Keep liveness available and make the missing model visible in readiness.
            logger.error("Embedding model unavailable: %s", error)
        async with AsyncExitStack() as stack:
            client = await stack.enter_async_context(httpx.AsyncClient(
                base_url=settings.qdrant_url,
                timeout=settings.qdrant_timeout_seconds,
                trust_env=False,
                transport=qdrant_transport,
            ))
            sync_client = stack.enter_context(httpx.Client(
                base_url=settings.qdrant_url, timeout=settings.qdrant_timeout_seconds,
                trust_env=False, transport=qdrant_sync_transport,
            ))
            application.state.qdrant_http = client
            manager = CollectionManager(client, collection_definitions)
            application.state.collection_manager = manager
            application.state.video_service = VideoEmbeddingService(
                engine, video_store, VideoEmbeddingRepository(sync_client, collection_definitions[0])
            )
            application.state.user_service = UserEmbeddingService(
                engine.spec, user_store, UserEmbeddingRepository(sync_client, collection_definitions[0], collection_definitions[1])
            )

            async def initialize_with_retry():
                while True:
                    try:
                        results = await manager.initialize()
                        logger.info("Qdrant collections initialized: %s", results)
                        return
                    except CollectionSetupError as error:
                        logger.error("Qdrant initialization: %s", error.to_dict())
                        if not error.retryable:
                            return
                    await asyncio.sleep(settings.qdrant_init_retry_seconds)

            # Initialization can retry independently while liveness stays available.
            task = asyncio.create_task(initialize_with_retry())
            try:
                yield
            finally:
                task.cancel()
                with suppress(asyncio.CancelledError):
                    await task

    application = FastAPI(
        title="NexoPlay Embeddings API",
        description=(
            "CPU text inference in memory. Preview does not persist vectors. "
            "Videos are persisted with UUIDs and metadata versions. "
            "Weighted user profiles support partial reconciliation and versioned invalidation."
        ),
        version="0.7.0",
        lifespan=lifespan,
    )

    @application.get("/", tags=["Infrastructure"])
    async def service_info() -> dict:
        return {
            "service": "embeddings-service",
            "stage": "integration",
            "capabilities": {
                "text_inference": engine.loaded,
                "video_embeddings": True,
                "user_profiles": True,
                "video_invalidation": True,
                "qdrant_collection_initialization": True,
                "qdrant_point_writes": True,
            },
        }

    @application.get("/health", tags=["Infrastructure"])
    async def health() -> dict:
        return {"service": "embeddings-service", "status": "ok"}

    @application.get(
        "/ready",
        tags=["Infrastructure"],
        responses={503: {"description": "The model, Qdrant or compatible collections are unavailable"}},
    )
    async def ready(request: Request) -> JSONResponse:
        dependencies = {"model": "ready" if engine.loaded else "unavailable"}
        try:
            await run_in_threadpool(video_store.check)
            dependencies["video_state"] = "ready"
        except VideoError:
            dependencies["video_state"] = "unavailable"
        try:
            await run_in_threadpool(user_store.check)
            dependencies["profile_state"] = "ready"
        except VideoError:
            dependencies["profile_state"] = "unavailable"
        collections = []
        error = None
        try:
            response = await request.app.state.qdrant_http.get("/readyz")
            response.raise_for_status()
        except httpx.HTTPError:
            dependencies["qdrant"] = "unavailable"
            dependencies["collections"] = "unavailable"
        else:
            dependencies["qdrant"] = "ready"
            try:
                collections = await request.app.state.collection_manager.inspect()
                dependencies["collections"] = "ready"
            except CollectionSetupError as failure:
                dependencies["collections"] = "unavailable"
                error = failure.to_dict()

        available = all(value == "ready" for value in dependencies.values())
        return JSONResponse(
            status_code=200 if available else 503,
            content={
                "service": "embeddings-service",
                "status": "ready" if available else "not_ready",
                "stage": "integration",
                "dependencies": dependencies,
                "collections": collections,
                "error": error,
                "vector_persistence_implemented": True,
            }
        )

    @application.get("/collections", tags=["Infrastructure"])
    async def collections_info(request: Request) -> JSONResponse:
        """Read-only: this route never creates, updates or deletes collections."""
        try:
            collections = await request.app.state.collection_manager.inspect()
        except CollectionSetupError as error:
            return JSONResponse(status_code=503, content={"error": error.to_dict()})
        return JSONResponse(content={"status": "ready", "read_only": True, "collections": collections})

    @application.get("/model", tags=["Inference"])
    async def model_info() -> dict:
        return {
            "loaded": engine.loaded,
            "model_key": engine.spec.key,
            "embedding_model_version": engine.spec.version,
            "dimension": engine.spec.dimension,
            "max_tokens": engine.spec.max_tokens,
            "prefix": engine.spec.prefix,
            "text_template_version": TEXT_TEMPLATE_VERSION,
            "device": "cpu",
            "cpu_threads": engine.cpu_threads,
            "normalization": "L2",
            "metric": "Cosine",
        }

    @application.exception_handler(VideoError)
    async def video_error_handler(request: Request, error: VideoError):
        headers = {"Retry-After": "1"} if error.status == 429 else None
        return JSONResponse(status_code=error.status, content={"error": error.to_dict()}, headers=headers)

    @application.post("/embeddings/videos", response_model=VideoEmbeddingResponse, tags=["Videos"])
    async def generate_video(body: VideoEmbeddingRequest, request: Request):
        return await run_in_threadpool(request.app.state.video_service.generate, body)

    @application.get("/embeddings/videos/{video_id}/status", tags=["Videos"])
    async def video_status(video_id: UUID, request: Request):
        return await run_in_threadpool(request.app.state.video_service.status, str(video_id))

    @application.delete("/embeddings/videos/{video_id}", tags=["Videos"])
    async def invalidate_video(video_id: UUID, request: Request,
                               metadata_version: int = Query(gt=0, le=9_007_199_254_740_991)):
        return await run_in_threadpool(request.app.state.video_service.invalidate, str(video_id), metadata_version)

    @application.exception_handler(RequestValidationError)
    async def request_validation_handler(request: Request, error: RequestValidationError):
        if request.url.path != "/embeddings/users/recalculate":
            return await request_validation_exception_handler(request, error)
        details = [{"loc": list(item["loc"]), "message": item["msg"], "type": item["type"]} for item in error.errors()]
        fields = {item["loc"][-1] for item in error.errors() if item["loc"] and item["type"] == "literal_error"}
        code = "UNSUPPORTED_CONTRACT_VERSION" if "contract_version" in fields else "UNSUPPORTED_WEIGHTS_VERSION" if "weights_version" in fields else "INVALID_REQUEST"
        return JSONResponse(status_code=422, content={"error": {
            "code": code, "message": "Invalid full interaction summary", "retryable": False, "details": details,
        }})

    @application.post("/embeddings/users/recalculate", response_model=UserRecalculateResponse, tags=["Users"])
    async def recalculate_user(body: UserRecalculateRequest, request: Request):
        return await run_in_threadpool(request.app.state.user_service.recalculate, body)

    @application.get("/embeddings/users/{user_id}/status", tags=["Users"])
    async def user_status(user_id: UUID, request: Request):
        return await run_in_threadpool(request.app.state.user_service.status, str(user_id))

    @application.post("/embeddings/preview", tags=["Inference"])
    async def preview(metadata: VideoMetadata):
        """Generate a vector in memory; never writes to Qdrant or registers a video."""
        try:
            result = await run_in_threadpool(engine.embed, metadata)
        except ModelUnavailableError:
            return JSONResponse(status_code=503, content={"error": {
                "code": "MODEL_UNAVAILABLE",
                "message": "Prepare the pinned model and restart the service.",
                "retryable": False,
            }})
        except InferenceError:
            logger.exception("Text inference failed")
            return JSONResponse(status_code=503, content={"error": {
                "code": "INFERENCE_FAILED",
                "message": "Could not generate a valid text vector.",
                "retryable": True,
            }})
        return {"persisted": False, **result.to_dict()}

    return application


app = create_app()
