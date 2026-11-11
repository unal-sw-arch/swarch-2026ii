"""Create absent collections and validate existing ones without replacing data."""

from dataclasses import dataclass
from urllib.parse import quote

import httpx

from app.config import Settings
from app.inference import ModelSpec
from app.metadata import TEXT_TEMPLATE_VERSION


class CollectionSetupError(RuntimeError):
    def __init__(self, code: str, message: str, retryable: bool, collection: str | None = None):
        super().__init__(message)
        self.code = code
        self.retryable = retryable
        self.collection = collection

    def to_dict(self) -> dict:
        return {
            "code": self.code,
            "message": str(self),
            "retryable": self.retryable,
            "collection": self.collection,
        }


@dataclass(frozen=True)
class CollectionDefinition:
    name: str
    role: str
    dimension: int
    model_version: str

    @property
    def metadata(self) -> dict:
        return {"nexoplay": {
            "schema_version": "1.0",
            "role": self.role,
            "embedding_model_version": self.model_version,
            "text_template_version": TEXT_TEMPLATE_VERSION,
            "normalization": "L2",
        }}

    @property
    def path(self) -> str:
        return f"/collections/{quote(self.name, safe='')}"


def definitions_for(settings: Settings, model: ModelSpec) -> tuple[CollectionDefinition, ...]:
    if settings.expected_model_version and settings.expected_model_version != model.version:
        raise ValueError("EMBEDDING_MODEL_VERSION does not match the selected model revision")
    return (
        CollectionDefinition(settings.video_collection, "videos", model.dimension, model.version),
        CollectionDefinition(settings.user_collection, "users", model.dimension, model.version),
    )


class CollectionManager:
    def __init__(self, client: httpx.AsyncClient, definitions: tuple[CollectionDefinition, ...]):
        self.client = client
        self.definitions = definitions

    async def _get(self, definition: CollectionDefinition) -> dict | None:
        try:
            response = await self.client.get(definition.path)
            if response.status_code == 404:
                return None
            response.raise_for_status()
            body = response.json()
            if not isinstance(body, dict) or body.get("status") != "ok" or not isinstance(body.get("result"), dict):
                raise ValueError("Unexpected collection response")
            return body["result"]
        except (httpx.HTTPError, ValueError) as error:
            raise self._request_error(error, definition.name) from error

    @staticmethod
    def _request_error(error: Exception, collection: str) -> CollectionSetupError:
        retryable = True
        if isinstance(error, httpx.HTTPStatusError):
            status = error.response.status_code
            retryable = status in {408, 429} or status >= 500
        return CollectionSetupError(
            "QDRANT_COLLECTION_REQUEST_FAILED",
            f"Could not confirm collection {collection}; no existing data was replaced",
            retryable,
            collection,
        )

    @staticmethod
    def validate_schema(definition: CollectionDefinition, info: dict) -> dict:
        config = info.get("config")
        params = config.get("params", {}) if isinstance(config, dict) else {}
        vectors = params.get("vectors") if isinstance(params, dict) else None
        compatible = (
            isinstance(vectors, dict)
            and vectors.get("size") == definition.dimension
            and vectors.get("distance") == "Cosine"
            and not vectors.get("multivector_config")
        )
        if not compatible:
            raise CollectionSetupError(
                "COLLECTION_SCHEMA_MISMATCH",
                f"Collection {definition.name} must have one unnamed {definition.dimension}-dimensional Cosine vector",
                False,
                definition.name,
            )
        metadata = config.get("metadata")
        binding = metadata.get("nexoplay") if isinstance(metadata, dict) else None
        expected = definition.metadata["nexoplay"]
        if not isinstance(binding, dict) or any(binding.get(key) != value for key, value in expected.items()):
            raise CollectionSetupError(
                "COLLECTION_MODEL_MISMATCH",
                f"Collection {definition.name} has missing or incompatible model, template or role metadata",
                False,
                definition.name,
            )
        return {
            "name": definition.name,
            "role": definition.role,
            "dimension": definition.dimension,
            "distance": "Cosine",
            "embedding_model_version": definition.model_version,
            "text_template_version": TEXT_TEMPLATE_VERSION,
            "points_count": info.get("points_count"),
        }

    async def inspect(self) -> list[dict]:
        """Read-only validation, safe for readiness probes."""
        results = []
        for definition in self.definitions:
            info = await self._get(definition)
            if info is None:
                raise CollectionSetupError(
                    "COLLECTION_MISSING", f"Collection {definition.name} has not been initialized", True, definition.name
                )
            results.append(self.validate_schema(definition, info))
        return results

    async def initialize(self) -> list[dict]:
        """Preflight both schemas, then create only missing collections."""
        missing = []
        for definition in self.definitions:
            info = await self._get(definition)
            if info is None:
                missing.append(definition)
            else:
                self.validate_schema(definition, info)

        created = set()
        for definition in missing:
            try:
                response = await self.client.put(definition.path, json={
                    "vectors": {"size": definition.dimension, "distance": "Cosine"},
                    "metadata": definition.metadata,
                })
                # Another replica may have created it after the preflight.
                if response.status_code not in {400, 409}:
                    response.raise_for_status()
                    body = response.json()
                    if not isinstance(body, dict) or body.get("status") != "ok" or body.get("result") is not True:
                        raise ValueError("Collection creation was not confirmed")
                    created.add(definition.name)
                info = await self._get(definition)
                if info is None:
                    response.raise_for_status()
                    raise CollectionSetupError(
                        "COLLECTION_NOT_CONFIRMED", f"Collection {definition.name} was not confirmed after creation", True, definition.name
                    )
                self.validate_schema(definition, info)
            except CollectionSetupError:
                raise
            except (httpx.HTTPError, ValueError) as error:
                raise self._request_error(error, definition.name) from error

        results = await self.inspect()
        for result in results:
            result["action"] = "created" if result["name"] in created else "validated"
        return results
