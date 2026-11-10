"""CPU inference in memory. This module has no database or HTTP write client."""

from dataclasses import asdict, dataclass
import json
import math
from pathlib import Path
from threading import Lock

from app.metadata import TEXT_TEMPLATE_VERSION, VideoMetadata


MANIFEST_PATH = Path(__file__).resolve().parents[1] / "models.json"


class ModelUnavailableError(RuntimeError):
    pass


class InferenceError(RuntimeError):
    pass


@dataclass(frozen=True)
class ModelSpec:
    key: str
    repository: str
    revision: str
    dimension: int
    max_tokens: int
    prefix: str

    @property
    def version(self) -> str:
        return f"{self.repository}@{self.revision}"


def read_spec(key: str | None = None) -> ModelSpec:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    if manifest["text_template_version"] != TEXT_TEMPLATE_VERSION:
        raise ValueError("Model manifest and text template versions differ")
    key = key or manifest["selected"]
    if key not in manifest["candidates"]:
        raise ValueError(f"Unknown model key: {key}")
    return ModelSpec(key=key, **manifest["candidates"][key])


def normalize_vector(values, dimension: int) -> list[float]:
    try:
        vector = [float(value) for value in values]
    except (TypeError, ValueError, OverflowError) as error:
        raise InferenceError("Model returned non-numeric vector values") from error
    if len(vector) != dimension or not all(math.isfinite(value) for value in vector):
        raise InferenceError("Model returned an invalid vector or dimension")
    norm = math.hypot(*vector)
    if not math.isfinite(norm) or norm <= 1e-12:
        raise InferenceError("Cannot normalize a zero or invalid vector")
    return [value / norm for value in vector]


@dataclass(frozen=True)
class EmbeddingResult:
    vector: list[float]
    dimension: int
    embedding_model_version: str
    text_template_version: str
    metadata_hash: str
    input_tokens: int
    used_tokens: int
    truncated: bool

    def to_dict(self) -> dict:
        return asdict(self)


class TextEmbeddingEngine:
    def __init__(self, spec: ModelSpec, model_root: Path, cpu_threads: int = 4):
        if cpu_threads < 1:
            raise ValueError("cpu_threads must be positive")
        self.spec = spec
        self.model_root = Path(model_root)
        self.cpu_threads = cpu_threads
        self._model = None
        self._lock = Lock()

    @property
    def loaded(self) -> bool:
        return self._model is not None

    def load(self) -> None:
        with self._lock:
            if self.loaded:
                return
            model_path = self.model_root / self.spec.key
            marker = model_path / "nexoplay-revision.json"
            if not marker.is_file():
                raise ModelUnavailableError(
                    f"Missing pinned model {self.spec.key}; run app.prepare_models first"
                )
            try:
                metadata = json.loads(marker.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError) as error:
                raise ModelUnavailableError("Cannot read the model revision marker") from error
            if metadata != {"repository": self.spec.repository, "revision": self.spec.revision}:
                raise ModelUnavailableError("Downloaded model revision does not match models.json")
            try:
                import torch
                from sentence_transformers import SentenceTransformer

                torch.set_num_threads(self.cpu_threads)
                model = SentenceTransformer(
                    str(model_path),
                    device="cpu",
                    local_files_only=True,
                    trust_remote_code=False,
                    model_kwargs={"use_safetensors": True},
                )
                if model.get_sentence_embedding_dimension() != self.spec.dimension:
                    raise ModelUnavailableError("Model dimension differs from models.json")
                if model.max_seq_length < self.spec.max_tokens:
                    raise ModelUnavailableError("Model token limit is below the configured limit")
                model.max_seq_length = self.spec.max_tokens
                model.tokenizer.truncation_side = "right"
                model.eval()
                self._model = model
            except ModelUnavailableError:
                raise
            except Exception as error:
                raise ModelUnavailableError(f"Failed to load pinned model {self.spec.key}") from error

    def embed(self, metadata: VideoMetadata) -> EmbeddingResult:
        return self.embed_many([metadata])[0]

    def embed_many(self, items: list[VideoMetadata]) -> list[EmbeddingResult]:
        if not self.loaded:
            raise ModelUnavailableError("The embedding model has not been loaded")
        if not items:
            return []
        texts = [self.spec.prefix + item.to_text() for item in items]
        with self._lock:
            try:
                # Count before truncation so callers can detect information loss.
                tokenized = self._model.tokenizer(
                    texts, add_special_tokens=True, truncation=False, verbose=False
                )["input_ids"]
                vectors = self._model.encode(
                    texts,
                    batch_size=8,
                    normalize_embeddings=True,
                    convert_to_numpy=True,
                    show_progress_bar=False,
                    prompt="",
                )
                if len(vectors) != len(items):
                    raise InferenceError("Model returned an unexpected batch size")
                results = []
                for metadata, token_ids, vector in zip(items, tokenized, vectors, strict=True):
                    count = len(token_ids)
                    results.append(EmbeddingResult(
                        vector=normalize_vector(vector, self.spec.dimension),
                        dimension=self.spec.dimension,
                        embedding_model_version=self.spec.version,
                        text_template_version=TEXT_TEMPLATE_VERSION,
                        metadata_hash=metadata.content_hash(),
                        input_tokens=count,
                        used_tokens=min(count, self.spec.max_tokens),
                        truncated=count > self.spec.max_tokens,
                    ))
                return results
            except InferenceError:
                raise
            except Exception as error:
                raise InferenceError("Text inference failed") from error
