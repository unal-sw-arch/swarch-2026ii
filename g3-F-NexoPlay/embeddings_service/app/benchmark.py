"""Compare pinned CPU models in separate processes; never connects to Qdrant."""

import argparse
import hashlib
from datetime import datetime, timezone
from importlib.metadata import version
import json
import math
import os
from pathlib import Path
import platform
import statistics
import subprocess
import sys
from threading import Event, Thread
from time import perf_counter

from app.inference import MANIFEST_PATH, TextEmbeddingEngine, normalize_vector, read_spec
from app.metadata import TEXT_TEMPLATE_VERSION, VideoMetadata


FIXTURES = Path(__file__).resolve().parents[1] / "evaluation" / "catalog.json"


def evaluate(key: str, model_root: Path, threads: int) -> dict:
    import psutil

    process = psutil.Process()
    peak = [process.memory_info().rss]
    stop = Event()

    def sample_memory():
        while not stop.wait(0.02):
            peak[0] = max(peak[0], process.memory_info().rss)

    sampler = Thread(target=sample_memory, daemon=True)
    sampler.start()
    try:
        spec = read_spec(key)
        engine = TextEmbeddingEngine(spec, model_root, cpu_threads=threads)
        started = perf_counter()
        engine.load()
        load_seconds = perf_counter() - started
        fixture = json.loads(FIXTURES.read_text(encoding="utf-8"))
        videos = fixture["videos"]
        items = [VideoMetadata.model_validate(v["metadata"]) for v in videos]
        engine.embed(items[0])  # Warm up; excluded from latency statistics.
        latencies = []
        for _ in range(3):
            for item in items:
                started = perf_counter()
                engine.embed(item)
                latencies.append((perf_counter() - started) * 1000)
        results = engine.embed_many(items)
        vectors = {v["id"]: r.vector for v, r in zip(videos, results, strict=True)}

        def similarity(a, b):
            return sum(x * y for x, y in zip(a, b, strict=True))

        correct = sum(
            similarity(vectors[a], vectors[positive]) > similarity(vectors[a], vectors[negative])
            for a, positive, negative in fixture["triplets"]
        )
        profile_results = []
        for group in sorted({v["group"] for v in videos}):
            group_videos = [v for v in videos if v["group"] == group]
            seeds = [v["id"] for v in group_videos[:2]]
            a, b = (vectors[video_id] for video_id in seeds)
            profile = normalize_vector([x + 3 * y for x, y in zip(a, b, strict=True)], spec.dimension)
            candidates = sorted(
                (v for v in videos if v["id"] not in seeds),
                key=lambda v: similarity(profile, vectors[v["id"]]),
                reverse=True,
            )[:3]
            profile_results.append({
                "group": group,
                "seed_ids": seeds,
                "top3_ids": [v["id"] for v in candidates],
                "precision_at_3": sum(v["group"] == group for v in candidates) / 3,
            })

        query_results = []
        for query in fixture["queries"]:
            vector = engine.embed(VideoMetadata.model_validate(query["metadata"])).vector
            ranked = sorted(videos, key=lambda v: similarity(vector, vectors[v["id"]]), reverse=True)
            rank = next(i + 1 for i, v in enumerate(ranked) if v["group"] == query["group"])
            query_results.append({
                "title": query["metadata"]["title"],
                "expected_group": query["group"],
                "first_relevant_rank": rank,
                "top3_ids": [v["id"] for v in ranked[:3]],
                "reciprocal_rank_at_5": 1 / rank if rank <= 5 else 0,
            })

        long_item = VideoMetadata(
            title="Preparación de una ruta en bicicleta",
            category="Deportes",
            tags=["ciclismo", "resistencia"],
            description="Planifica el esfuerzo, la hidratación y los descansos durante el recorrido. " * 100,
        )
        long_result = engine.embed(long_item)
        if not long_result.truncated:
            raise AssertionError("Long-input fixture was not truncated")
        norms = [sum(x * x for x in result.vector) ** 0.5 for result in results]
        if not all(abs(norm - 1) < 1e-5 for norm in norms):
            raise AssertionError("Vectors are not normalized")
        latencies.sort()
        return {
            "model_key": key,
            "model_version": spec.version,
            "dimension": spec.dimension,
            "max_tokens": spec.max_tokens,
            "prefix": spec.prefix,
            "cpu_threads": threads,
            "load_seconds_including_imports": round(load_seconds, 3),
            "single_video_latency_ms": {
                "samples": len(latencies),
                "mean": round(statistics.mean(latencies), 3),
                "p95": round(latencies[math.ceil(0.95 * len(latencies)) - 1], 3),
            },
            "peak_process_rss_mib": round(max(peak[0], process.memory_info().rss) / 2**20, 2),
            "triplet_accuracy": correct / len(fixture["triplets"]),
            "profile_precision_at_3": statistics.mean(p["precision_at_3"] for p in profile_results),
            "query_mrr_at_5": statistics.mean(q["reciprocal_rank_at_5"] for q in query_results),
            "profiles": profile_results,
            "queries": query_results,
            "truncated_catalog_items": sum(r.truncated for r in results),
            "long_input": {
                "input_tokens": long_result.input_tokens,
                "used_tokens": long_result.used_tokens,
                "truncated": long_result.truncated,
            },
        }
    finally:
        stop.set()
        sampler.join()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-root", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--worker", choices=["minilm", "e5-small"])
    args = parser.parse_args()
    if args.threads < 1:
        parser.error("--threads must be positive")
    if args.worker:
        print("NEXOPLAY_REPORT=" + json.dumps(evaluate(args.worker, args.model_root, args.threads)))
        return
    if args.output is None:
        parser.error("--output is required for the comparison")

    import psutil

    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    fixture = json.loads(FIXTURES.read_text(encoding="utf-8"))
    report = {
        "evaluated_at": datetime.now(timezone.utc).isoformat(),
        "runtime": {
            "platform": platform.platform(),
            "python": platform.python_version(),
            "cpu_logical": os.cpu_count(),
            "cpu_threads_per_model": args.threads,
            "system_ram_gib": round(psutil.virtual_memory().total / 2**30, 2),
            "packages": {p: version(p) for p in ["torch", "transformers", "sentence-transformers", "numpy", "psutil"]},
        },
        "dataset": {
            "source": "synthetic",
            "sha256": hashlib.sha256(FIXTURES.read_bytes()).hexdigest(),
            "text_template_version": TEXT_TEMPLATE_VERSION,
            "videos": len(fixture["videos"]),
            "queries": len(fixture["queries"]),
            "triplets": len(fixture["triplets"]),
            "profiles": len({v["group"] for v in fixture["videos"]}),
        },
        "limitations": [
            "Small hand-labeled synthetic corpus; not a real-catalog relevance benchmark.",
            "Windows CPU measurements; Docker CPU-only performance must be checked separately.",
            "Peak RSS sampled every 20 ms; includes imports and model weights, not GPU allocations.",
            "Synthetic group labels are evaluation-only and never included as input fields.",
        ],
        "models": [],
    }
    for key in manifest["candidates"]:
        print(f"Evaluating {key} in an isolated CPU process...", flush=True)
        completed = subprocess.run(
            [sys.executable, "-B", "-m", "app.benchmark", "--worker", key,
             "--model-root", str(args.model_root.resolve()), "--threads", str(args.threads)],
            capture_output=True,
            text=True,
            encoding="utf-8",
            timeout=600,
        )
        if completed.returncode:
            raise RuntimeError(f"Evaluation failed for {key}:\n{completed.stderr}")
        lines = [line for line in completed.stdout.splitlines() if line.startswith("NEXOPLAY_REPORT=")]
        if len(lines) != 1:
            raise RuntimeError(f"Missing evaluation result for {key}: {completed.stdout}")
        result = json.loads(lines[0].split("=", 1)[1])
        report["models"].append(result)
        print(json.dumps({k: result[k] for k in ["model_key", "profile_precision_at_3", "query_mrr_at_5", "triplet_accuracy", "single_video_latency_ms", "peak_process_rss_mib"]}), flush=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Report: {args.output}", flush=True)


if __name__ == "__main__":
    main()
