#!/usr/bin/env python3
"""Merge Under The Bedrock sound events into BetterModel's generated resource-pack ZIP."""
from __future__ import annotations
import argparse
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOUNDS_JSON = ROOT / "resource-pack" / "assets" / "underthebedrock" / "sounds.json"

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bettermodel_pack", type=Path, help="BetterModel-generated ZIP resource pack")
    parser.add_argument("output", type=Path, help="Merged resource pack ZIP to create")
    args = parser.parse_args()
    if not args.bettermodel_pack.is_file():
        parser.error(f"BetterModel pack not found: {args.bettermodel_pack}")
    events = json.loads(SOUNDS_JSON.read_text(encoding="utf-8"))
    if len(events) != 27 * 4:
        raise SystemExit(f"Expected 108 entity sound events, found {len(events)}")
    with zipfile.ZipFile(args.bettermodel_pack, "r") as source:
        bad = source.testzip()
        if bad:
            raise SystemExit(f"Input resource pack is corrupt at {bad}")
        entries = {name: source.read(name) for name in source.namelist() if name != "assets/underthebedrock/sounds.json"}
    entries["assets/underthebedrock/sounds.json"] = SOUNDS_JSON.read_bytes()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for name, data in entries.items():
            output.writestr(name, data)
    with zipfile.ZipFile(args.output, "r") as result:
        bad = result.testzip()
        if bad:
            raise SystemExit(f"Merged resource pack is corrupt at {bad}")
    print(f"Merged {len(events)} Under The Bedrock sound events into {args.output}")
    print("The custom sound event IDs currently use carefully selected Minecraft sound samples.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
