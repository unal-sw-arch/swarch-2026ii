"""Explicit idempotent collection initialization, without model loading or point writes."""

import argparse
import asyncio
import json

import httpx

from app.config import Settings
from app.inference import read_spec
from app.qdrant_collections import CollectionManager, CollectionSetupError, definitions_for


async def initialize(check_only: bool = False) -> dict:
    settings = Settings.from_environment()
    definitions = definitions_for(settings, read_spec(settings.model_key))
    async with httpx.AsyncClient(
        base_url=settings.qdrant_url, timeout=settings.qdrant_timeout_seconds, trust_env=False
    ) as client:
        manager = CollectionManager(client, definitions)
        results = await manager.inspect() if check_only else await manager.initialize()
        return {"status": "ready", "collections": results, "points_written": 0}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check-only", action="store_true", help="Validate existing collections without creating anything")
    args = parser.parse_args()
    try:
        print(json.dumps(asyncio.run(initialize(args.check_only)), indent=2))
    except CollectionSetupError as error:
        print(json.dumps({"status": "not_ready", "error": error.to_dict()}, indent=2))
        raise SystemExit(1)


if __name__ == "__main__":
    main()
