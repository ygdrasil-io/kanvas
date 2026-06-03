#!/usr/bin/env python3
"""Generate and validate FOR-289 M60 outer-boundary runtime write chronology."""

from __future__ import annotations

import json
import os
import subprocess
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import numpy as np
from PIL import Image

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for271_nested_rrect_blurred_envelope_audit as for271
import validate_for288_m60_outer_boundary_store_order_audit as for288


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-289"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
SOURCE_FOR288 = for288.AUDIT
RAW_AUDIT = PROJECT_ROOT / "build/reports/for289/m60-outer-boundary-runtime-write-chronology-for289.raw.json"
AUDIT = SCENE_DIR / "m60-outer-boundary-runtime-write-chronology-for289.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-289-m60-outer-boundary-runtime-write-chronology.md"

DECISION_IDENTIFIED = "FIRST_LATER_WHITE_LAYER_WRITE_AFTER_RUNTIME_RED_STORE_IDENTIFIED"
DECISION_INSUFFICIENT = "PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP"
NEXT_ACTION_IDENTIFIED = "TARGET_IDENTIFIED_LATER_CPU_WRITE_CALLSITE"
NEXT_ACTION_INSUFFICIENT = "EXPAND_RUNTIME_TRACE_BEYOND_SKBITMAPDEVICE_DISPATCHBLEND"
TEST_CLASS = "org.skia.tests.For289M60OuterBoundaryRuntimeWriteChronologyTest"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-289 validation failed: {message}")


def json_dump(data: dict[str, Any]) -> str:
    return json.dumps(data, indent=2, sort_keys=False) + "\n"


def load_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    if not isinstance(data, dict):
        fail(f"{path.relative_to(PROJECT_ROOT)} must contain a JSON object")
    return data


def load_rgba(path: Path) -> np.ndarray:
    try:
        return np.asarray(Image.open(path).convert("RGBA"), dtype=np.int16)
    except FileNotFoundError:
        fail(f"missing image: {path.relative_to(PROJECT_ROOT)}")


def rgba_from_event(event: dict[str, Any], field: str) -> list[int]:
    value = event.get(field)
    if not (isinstance(value, list) and len(value) == 4):
        fail(f"event {event.get('index')} missing `{field}` RGBA")
    return [int(v) for v in value]


def red_tint(rgba: list[int]) -> bool:
    r, g, b, a = rgba
    return a >= 200 and r >= 180 and r > g + 20 and r > b + 20


def white_or_layer(rgba: list[int]) -> bool:
    r, g, b, a = rgba
    return a >= 240 and r >= 245 and g >= 245 and b >= 245


def max_delta(a: np.ndarray, b: np.ndarray) -> np.ndarray:
    return np.abs(a - b).max(axis=2)


def outer_window(source_for288: dict[str, Any]) -> dict[str, Any]:
    for zone in source_for288.get("zoneComparisons", []):
        if zone.get("subzone") == "draw_oval_outer_boundary":
            return zone
    fail("FOR-288 source audit missing draw_oval_outer_boundary")


def source_window_bounds(source_for288: dict[str, Any]) -> dict[str, int]:
    source_for286 = load_json(for288.SOURCE_FOR286)
    for window in source_for286.get("windows", []):
        if window.get("subzone") == "draw_oval_outer_boundary":
            bounds = window.get("bounds")
            if isinstance(bounds, dict):
                return {key: int(bounds[key]) for key in ("left", "top", "right", "bottom")}
    fail("FOR-286 source audit missing outer-boundary bounds")


def derive_target_pixels(source_for288: dict[str, Any]) -> list[dict[str, int]]:
    bounds = source_window_bounds(source_for288)
    reference = load_rgba(SCENE_DIR / "skia.png")
    cpu = load_rgba(SCENE_DIR / "cpu.png")
    height, width, _ = reference.shape
    _, masks = for271.make_envelope_masks(width, height)
    window_mask = np.zeros((height, width), dtype=bool)
    window_mask[bounds["top"] : bounds["bottom"], bounds["left"] : bounds["right"]] = True
    target_mask = window_mask & masks["draw_oval_outer_boundary"]
    diff = max_delta(cpu, reference)
    targets: list[dict[str, int]] = []
    for y in range(bounds["top"], bounds["bottom"]):
        for x in range(bounds["left"], bounds["right"]):
            if (
                target_mask[y, x]
                and int(diff[y, x]) > 32
                and white_or_layer(cpu[y, x].astype(int).tolist())
            ):
                targets.append({"x": x, "y": y})
    if len(targets) != 59:
        fail(f"derived outer target count changed: {len(targets)}")
    return targets


def run_runtime_trace(targets: list[dict[str, int]]) -> None:
    coords = ";".join(f"{p['x']},{p['y']}" for p in targets)
    cmd = [
        "./gradlew",
        ":skia-integration-tests:test",
        "--tests",
        TEST_CLASS,
        "--rerun-tasks",
    ]
    env = os.environ.copy()
    env["KANVAS_FOR289_TARGETS"] = coords
    env["KANVAS_FOR289_OUTPUT"] = str(RAW_AUDIT)
    result = subprocess.run(cmd, cwd=PROJECT_ROOT, env=env)
    if result.returncode != 0:
        fail(f"runtime trace test failed with exit code {result.returncode}")


def pixel_key(event: dict[str, Any]) -> tuple[int, int]:
    return (int(event["x"]), int(event["y"]))


def analyze(raw: dict[str, Any], targets: list[dict[str, int]], source_for288: dict[str, Any]) -> dict[str, Any]:
    events = raw.get("events")
    if not isinstance(events, list):
        fail("raw runtime audit missing events[]")
    target_set = {(p["x"], p["y"]) for p in targets}
    events_by_pixel: dict[tuple[int, int], list[dict[str, Any]]] = defaultdict(list)
    for event in events:
        if not isinstance(event, dict):
            fail("events[] must contain objects")
        key = pixel_key(event)
        if key in target_set:
            events_by_pixel[key].append(event)
    if set(events_by_pixel) != target_set:
        missing = sorted(target_set - set(events_by_pixel))[:8]
        fail(f"runtime trace missing target pixels: {missing}")

    red_events = [
        event for event in events
        if pixel_key(event) in target_set
        and "drawPathWithMaskFilter.A8.srcInPayload" in str(event.get("source", ""))
        and red_tint(rgba_from_event(event, "valueWrittenRgba"))
    ]
    red_by_pixel: dict[tuple[int, int], dict[str, Any]] = {}
    later_white_by_pixel: dict[tuple[int, int], dict[str, Any]] = {}
    for key, pixel_events in events_by_pixel.items():
        first_red = next(
            (
                event for event in pixel_events
                if "drawPathWithMaskFilter.A8.srcInPayload" in str(event.get("source", ""))
                and red_tint(rgba_from_event(event, "valueWrittenRgba"))
            ),
            None,
        )
        if first_red is None:
            continue
        red_by_pixel[key] = first_red
        later_white = next(
            (
                event for event in pixel_events
                if int(event["index"]) > int(first_red["index"])
                and white_or_layer(rgba_from_event(event, "valueWrittenRgba"))
            ),
            None,
        )
        if later_white is not None:
            later_white_by_pixel[key] = later_white

    final_white = [
        p for p in raw.get("targetPixels", [])
        if isinstance(p, dict) and white_or_layer([int(v) for v in p.get("finalReadbackRgba", [])])
    ]
    first_red_global = min(red_events, key=lambda event: int(event["index"])) if red_events else None
    later_after_global = [
        event for event in events
        if first_red_global is not None
        and pixel_key(event) in target_set
        and int(event["index"]) > int(first_red_global["index"])
        and white_or_layer(rgba_from_event(event, "valueWrittenRgba"))
    ]
    first_later_white_global = (
        min(later_after_global, key=lambda event: int(event["index"])) if later_after_global else None
    )
    if len(red_by_pixel) == 59 and first_later_white_global is not None:
        decision = DECISION_IDENTIFIED
        next_action = NEXT_ACTION_IDENTIFIED
        proof_gap = None
    elif not red_by_pixel:
        decision = DECISION_INSUFFICIENT
        next_action = NEXT_ACTION_INSUFFICIENT
        proof_gap = (
            "The root-device-bounded trace observed only the initial white kSrc output writes on "
            "the 59 target pixels. No runtime write with source "
            "SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload wrote a red-tinted value to "
            "those root pixels, so the FOR-286 reconstructed red store is not yet proven to be an "
            "actual output-device write."
        )
    elif len(later_white_by_pixel) == 0 and len(final_white) == 59:
        decision = DECISION_INSUFFICIENT
        next_action = NEXT_ACTION_INSUFFICIENT
        proof_gap = (
            "The bounded dispatchBlend trace observed runtime red stores but no later white/layer "
            "dispatchBlend write, while final target readback is still white/layer. The missing writer "
            "is outside the instrumented SkBitmapDevice.dispatchBlend/setPixel callsites or the "
            "checked-in FOR-288 reconstructed-store model is not the actual runtime write path."
        )
    else:
        decision = DECISION_INSUFFICIENT
        next_action = NEXT_ACTION_INSUFFICIENT
        proof_gap = (
            "Runtime trace did not produce a complete red-store plus later-white chronology for all "
            "59 target pixels; inspect per-pixel firstRedRuntimeWrite/laterWhiteLayerWrite gaps."
        )

    source_counts = Counter(str(event.get("source", "")) for event in events)
    branch_counts = Counter(str(event.get("branch", "")) for event in events)
    event_counts_by_pixel = {
        f"{x},{y}": len(pixel_events)
        for (x, y), pixel_events in sorted(events_by_pixel.items(), key=lambda item: (item[0][1], item[0][0]))
    }
    per_pixel = []
    for target in targets:
        key = (target["x"], target["y"])
        first_red = red_by_pixel.get(key)
        later_white = later_white_by_pixel.get(key)
        per_pixel.append(
            {
                "x": target["x"],
                "y": target["y"],
                "eventCount": len(events_by_pixel[key]),
                "firstRedRuntimeWrite": summarize_event(first_red) if first_red else None,
                "firstLaterWhiteLayerWrite": summarize_event(later_white) if later_white else None,
            }
        )

    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-outer-boundary-runtime-write-chronology",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/runtime",
        "sourceAudits": {
            "for288": str(SOURCE_FOR288.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": decision,
        "nextAction": next_action,
        "proofGap": proof_gap,
        "route": {
            "gpuStatus": "expected-unsupported",
            "fallbackReason": for269.FALLBACK_REASON,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
            "for288PrimaryClassification": source_for288["primaryClassification"],
        },
        "targetPixelCount": len(targets),
        "eventCount": len(events),
        "traceScope": {
            "device": "root SkBitmapDevice only",
            "rootWidth": 1164,
            "rootHeight": 802,
            "temporaryMaskDevicesExcluded": True,
        },
        "sourceCounts": dict(source_counts),
        "branchCounts": dict(branch_counts),
        "eventCountsByPixel": event_counts_by_pixel,
        "summary": {
            "targetsWithRuntimeRedStore": len(red_by_pixel),
            "targetsWithLaterWhiteLayerWrite": len(later_white_by_pixel),
            "finalReadbackWhiteLayerTargets": len(final_white),
            "firstRuntimeRedStore": summarize_event(first_red_global),
            "firstLaterWhiteLayerWriteAfterFirstRed": summarize_event(first_later_white_global),
        },
        "targetPixels": raw.get("targetPixels", []),
        "perPixelChronology": per_pixel,
        "events": events,
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRendererChanged": False,
            "cpuInstrumentationAddedDisabledByDefault": True,
            "gpuRendererChanged": False,
            "globalBlendChanged": False,
            "setPixelChanged": False,
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def summarize_event(event: dict[str, Any] | None) -> dict[str, Any] | None:
    if event is None:
        return None
    return {
        "index": int(event["index"]),
        "x": int(event["x"]),
        "y": int(event["y"]),
        "source": event.get("source"),
        "callsite": event.get("callsite"),
        "branch": event.get("branch"),
        "mode": event.get("mode"),
        "coverage": int(event.get("coverage", 0)),
        "valueBeforeRgba": event.get("valueBeforeRgba"),
        "valueWrittenRgba": event.get("valueWrittenRgba"),
        "valueReadAfterRgba": event.get("valueReadAfterRgba"),
    }


def row_from_event(label: str, event: dict[str, Any] | None) -> str:
    if event is None:
        return f"| {label} | n/a | n/a | n/a | n/a | n/a | n/a |"
    return (
        f"| {label} | {event['index']} | `{event['x']},{event['y']}` | "
        f"`{event['source']}` | `{event['branch']}` | "
        f"`{event['valueBeforeRgba']}` | `{event['valueWrittenRgba']}` |"
    )


def write_report(audit: dict[str, Any]) -> None:
    summary = audit["summary"]
    source_rows = "\n".join(
        f"| `{source}` | {count} |" for source, count in sorted(audit["sourceCounts"].items())
    )
    branch_rows = "\n".join(
        f"| `{branch}` | {count} |" for branch, count in sorted(audit["branchCounts"].items())
    )
    report = f"""# FOR-289 M60 Outer Boundary Runtime Write Chronology

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Next action: `{audit["nextAction"]}`

## Result

FOR-289 traced actual CPU writes on the root `SkBitmapDevice` for the 59
`draw_oval_outer_boundary` target pixels derived from the FOR-288 outer window.
Temporary mask devices are excluded by matching the GM output dimensions
1164x802. The trace captures write order, coordinate, logical source, callsite,
branch, value before write, value written, and value read immediately after
write.

| Measure | Value |
|---|---:|
| Target pixels | {audit["targetPixelCount"]} |
| Runtime write events | {audit["eventCount"]} |
| Targets with runtime red store | {summary["targetsWithRuntimeRedStore"]} |
| Targets with later white/layer write | {summary["targetsWithLaterWhiteLayerWrite"]} |
| Final readback white/layer targets | {summary["finalReadbackWhiteLayerTargets"]} |

| Event | Index | Pixel | Source | Branch | Before | Written |
|---|---:|---|---|---|---|---|
{row_from_event("First runtime red store", summary["firstRuntimeRedStore"])}
{row_from_event("First later white/layer write", summary["firstLaterWhiteLayerWriteAfterFirstRed"])}

## Interpretation

Proof gap: {audit["proofGap"] or "none"}

The FOR-288 classification is preserved as
`{audit["route"]["for288PrimaryClassification"]}`. M60 remains
`expected-unsupported` for `{audit["route"]["visualParityFallbackPreserved"]}`,
and the crop fallback remains `{audit["route"]["cropFallbackPreserved"]}`.

## Source Counts

| Source | Events |
|---|---:|
{source_rows}

## Branch Counts

| Branch | Events |
|---|---:|
{branch_rows}

Machine artifacts:

- `{AUDIT.relative_to(PROJECT_ROOT)}`
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_audit(audit: dict[str, Any], source_for288: dict[str, Any]) -> None:
    if audit["linear"] != LINEAR_ID:
        fail("wrong Linear id")
    if audit["sceneId"] != SCENE_ID:
        fail("wrong scene id")
    if audit["targetPixelCount"] != 59:
        fail("target pixel count changed")
    if audit["supportDecision"] != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision changed")
    if audit["route"]["visualParityFallbackPreserved"] != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if audit["route"]["cropFallbackPreserved"] != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")
    if source_for288["primaryClassification"] != "OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE":
        fail("FOR-288 source classification changed")
    if audit["summary"]["finalReadbackWhiteLayerTargets"] != 59:
        fail("final target readback no longer has 59 white/layer pixels")
    if audit["summary"]["targetsWithRuntimeRedStore"] <= 0 and audit["decision"] != DECISION_INSUFFICIENT:
        fail("missing runtime red store requires proof-insufficient decision")
    preservation = audit["strictPreservation"]
    for key in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "productionRendererChanged",
        "gpuRendererChanged",
        "globalBlendChanged",
        "setPixelChanged",
        "m60Promoted",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if preservation.get(key) is not False:
            fail(f"strict preservation `{key}` changed")
    if preservation.get("cpuInstrumentationAddedDisabledByDefault") is not True:
        fail("missing disabled-by-default CPU instrumentation declaration")


def main() -> None:
    source_for288 = load_json(SOURCE_FOR288)
    for288.validate_audit(source_for288)
    outer = outer_window(source_for288)
    if outer["targetPixels"] != 59:
        fail("FOR-288 outer target count changed")
    targets = derive_target_pixels(source_for288)
    run_runtime_trace(targets)
    raw = load_json(RAW_AUDIT)
    audit = analyze(raw, targets, source_for288)
    validate_audit(audit, source_for288)
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
    write_report(audit)
    reread = load_json(AUDIT)
    validate_audit(reread, source_for288)
    report_text = REPORT.read_text(encoding="utf-8")
    for needle in (
        audit["decision"],
        "draw_oval_outer_boundary",
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
        "First runtime red store",
    ):
        if needle not in report_text:
            fail(f"report missing `{needle}`")
    print(f"FOR-289 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-289 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"Decision: {audit['decision']}")


if __name__ == "__main__":
    main()
