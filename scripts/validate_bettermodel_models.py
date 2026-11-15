#!/usr/bin/env python3
"""Validate Under The Bedrock's BetterModel Blockbench assets without third-party packages."""
from __future__ import annotations

import json
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / "bettermodel" / "models"
EXPECTED_ANIMATIONS = {
    "idle", "walk", "run", "stalk", "crawl", "attack", "lunge", "twitch",
    "reveal", "disappear", "emerge", "hurt", "death", "roar", "inspect",
    "listen", "recoil", "turn",
}
UUID_RE = re.compile(r"^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$")


def fail(path: Path, message: str) -> None:
    raise ValueError(f"{path.name}: {message}")


def validate(path: Path) -> tuple[int, int, int]:
    model = json.loads(path.read_text(encoding="utf-8"))
    elements = model.get("elements") or []
    textures = model.get("textures") or []
    roots = model.get("outliner") or []
    animations = model.get("animations") or []

    if not elements:
        fail(path, "no geometry elements")
    if len(roots) != 1:
        fail(path, f"expected one skeleton root, found {len(roots)}")
    if not textures or not all(
        isinstance(t.get("source"), str) and t["source"].startswith("data:image/png;base64,")
        for t in textures
    ):
        fail(path, "missing embedded PNG texture")

    element_ids = [e.get("uuid") for e in elements]
    if None in element_ids or len(set(element_ids)) != len(element_ids):
        fail(path, "missing or duplicate geometry UUID")

    group_ids: set[str] = set()
    group_names: dict[str, str] = {}
    assignments: list[str] = []
    all_uuids: list[str] = element_ids[:]

    def walk(node: dict, is_root: bool = False) -> None:
        group_id = node.get("uuid")
        if not isinstance(group_id, str) or group_id in all_uuids:
            fail(path, "missing or duplicate bone UUID")
        if not is_root:
            group_ids.add(group_id)
            group_names[group_id] = node.get("name", "")
        all_uuids.append(group_id)
        for child in node.get("children") or []:
            if isinstance(child, str):
                assignments.append(child)
            elif isinstance(child, dict):
                walk(child)
            else:
                fail(path, "invalid outliner child")

    for root in roots:
        walk(root, is_root=True)

    if sorted(assignments) != sorted(element_ids):
        fail(path, "geometry must be assigned to exactly one bone (missing, duplicate, or orphaned element)")

    animation_names = [a.get("name") for a in animations]
    if set(animation_names) != EXPECTED_ANIMATIONS or len(animation_names) != len(EXPECTED_ANIMATIONS):
        fail(path, f"expected exactly 18 clips; found {animation_names}")

    for animation in animations:
        length = animation.get("length")
        if not isinstance(length, (int, float)) or not math.isfinite(length) or length <= 0:
            fail(path, f"invalid duration for {animation.get('name')}")
        animators = animation.get("animators") or {}
        if set(animators) != group_ids:
            fail(path, f"{animation.get('name')}: animator/bone UUID coverage mismatch")
        for bone_id, animator in animators.items():
            if animator.get("name") != group_names[bone_id] or animator.get("type") != "bone":
                fail(path, f"{animation.get('name')}: invalid animator binding")
            keyframes = animator.get("keyframes") or []
            if len(keyframes) < 2:
                fail(path, f"{animation.get('name')}: insufficient keyframes")
            times = []
            for keyframe in keyframes:
                frame_uuid = keyframe.get("uuid")
                if not isinstance(frame_uuid, str) or not UUID_RE.match(frame_uuid):
                    fail(path, f"{animation.get('name')}: invalid keyframe UUID")
                all_uuids.append(frame_uuid)
                time = keyframe.get("time")
                if not isinstance(time, (int, float)) or not math.isfinite(time):
                    fail(path, f"{animation.get('name')}: invalid keyframe time")
                times.append(time)
                if keyframe.get("channel") != "rotation":
                    fail(path, f"{animation.get('name')}: unsupported animation channel")
                points = keyframe.get("data_points") or []
                if len(points) != 1:
                    fail(path, f"{animation.get('name')}: expected one rotation data point")
                point = points[0]
                if not all(isinstance(point.get(axis), (int, float)) and math.isfinite(point[axis]) for axis in ("x", "y", "z")):
                    fail(path, f"{animation.get('name')}: non-finite rotation value")
            if times != sorted(times) or times[0] < 0 or times[-1] > length:
                fail(path, f"{animation.get('name')}: keyframes are unordered or outside clip duration")

    for texture in textures:
        texture_uuid = texture.get("uuid")
        if texture_uuid:
            all_uuids.append(texture_uuid)
    for animation in animations:
        animation_uuid = animation.get("uuid")
        if animation_uuid:
            all_uuids.append(animation_uuid)

    if any(not isinstance(value, str) or not UUID_RE.match(value) for value in all_uuids):
        fail(path, "invalid UUID format")
    if len(all_uuids) != len(set(all_uuids)):
        fail(path, "UUID collision across geometry, bones, textures, animations, or keyframes")

    return len(elements), len(group_ids), sum(
        len(animator.get("keyframes") or [])
        for animation in animations
        for animator in (animation.get("animators") or {}).values()
    )


def main() -> int:
    files = sorted(MODEL_DIR.glob("*.bbmodel"))
    if len(files) != 28:
        print(f"Expected 28 model assets (27 archetypes + revealed Mimic), found {len(files)}", file=sys.stderr)
        return 1
    totals = [0, 0, 0]
    for path in files:
        try:
            result = validate(path)
        except Exception as error:
            print(f"FAIL {path.name}: {error}", file=sys.stderr)
            return 1
        totals = [a + b for a, b in zip(totals, result)]
    print(
        f"PASS: {len(files)} models; {totals[0]} geometry elements; "
        f"{totals[1]} animated bones; {len(files) * len(EXPECTED_ANIMATIONS)} clips; "
        f"{totals[2]} keyframes."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
