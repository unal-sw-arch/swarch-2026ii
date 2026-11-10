"""Explicit download command. The running API never downloads model weights."""

import argparse
import json
from pathlib import Path

from app.inference import MANIFEST_PATH, read_spec


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", default="selected", help="selected, all, minilm or e5-small")
    parser.add_argument("--model-root", type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    keys = list(manifest["candidates"]) if args.model == "all" else [
        manifest["selected"] if args.model == "selected" else args.model
    ]

    from huggingface_hub import constants, snapshot_download

    if constants.HF_HUB_OFFLINE:
        parser.error("Unset HF_HUB_OFFLINE for preparation; the API itself loads models offline")

    for key in keys:
        spec = read_spec(key)
        target = args.model_root / key
        print(f"Downloading {spec.version}", flush=True)
        snapshot_download(
            repo_id=spec.repository,
            revision=spec.revision,
            local_dir=target,
            token=False,
            allow_patterns=["*.json", "*.safetensors", "*.model", "1_Pooling/*.json"],
            ignore_patterns=["onnx/*", "openvino/*"],
            max_workers=2,
        )
        (target / "nexoplay-revision.json").write_text(
            json.dumps({"repository": spec.repository, "revision": spec.revision}, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"Ready: {target}", flush=True)


if __name__ == "__main__":
    main()
