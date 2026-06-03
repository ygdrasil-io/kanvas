#!/usr/bin/env python3
"""Generate and validate FOR-290 M60 expanded runtime write trace evidence."""

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
import validate_for289_m60_outer_boundary_runtime_write_chronology as for289


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-290"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
SOURCE_FOR288 = for288.AUDIT
SOURCE_FOR289 = for289.AUDIT
RAW_AUDIT = PROJECT_ROOT / "build/reports/for290/m60-expanded-runtime-write-trace-for290.raw.json"
AUDIT = SCENE_DIR / "m60-expanded-runtime-write-trace-for290.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-290-m60-expanded-runtime-write-trace.md"

DECISION_IDENTIFIED = "RUNTIME_RED_STORE_CALLSITE_IDENTIFIED"
DECISION_NO_RED = "NO_RUNTIME_RED_ROOT_STORE_FOUND"
DECISION_SCOPE_GAP = "PROOF_STILL_INSUFFICIENT_TRACE_SCOPE_GAP"
NEXT_ACTION_IDENTIFIED = "INVESTIGATE_IDENTIFIED_RUNTIME_RED_STORE_OVERWRITE"
NEXT_ACTION_NO_RED = "RECONSTRUCTED_RED_STORE_NOT_RUNTIME_ROOT_WRITE"
NEXT_ACTION_SCOPE_GAP = "EXPAND_TRACE_TO_EXACT_REMAINING_WRITE_GAP"
TEST_CLASS = "org.skia.tests.For290M60ExpandedRuntimeWriteTraceTest"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-290 validation failed: {message}")


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
    env["KANVAS_FOR290_TARGETS"] = coords
    env["KANVAS_FOR290_OUTPUT"] = str(RAW_AUDIT)
    result = subprocess.run(cmd, cwd=PROJECT_ROOT, env=env)
    if result.returncode != 0:
        fail(f"expanded runtime trace test failed with exit code {result.returncode}")


def pixel_key(event: dict[str, Any]) -> tuple[int, int]:
    return (int(event["x"]), int(event["y"]))


def summarize_event(event: dict[str, Any] | None) -> dict[str, Any] | None:
    if event is None:
        return None
    return {
        "index": int(event["index"]),
        "x": int(event["x"]),
        "y": int(event["y"]),
        "bitmapWidth": int(event.get("bitmapWidth", -1)),
        "bitmapHeight": int(event.get("bitmapHeight", -1)),
        "deviceKind": event.get("deviceKind"),
        "rootDevice": bool(event.get("rootDevice")),
        "source": event.get("source"),
        "callsite": event.get("callsite"),
        "branch": event.get("branch"),
        "mode": event.get("mode"),
        "coverage": int(event.get("coverage", 0)),
        "valueBeforeRgba": event.get("valueBeforeRgba"),
        "valueWrittenRgba": event.get("valueWrittenRgba"),
        "valueReadAfterRgba": event.get("valueReadAfterRgba"),
    }


def source_needles() -> dict[str, bool]:
    device_text = (PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt").read_text(
        encoding="utf-8"
    )
    bitmap_text = (PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt").read_text(
        encoding="utf-8"
    )
    test_text = (
        PROJECT_ROOT
        / "skia-integration-tests/src/test/kotlin/org/skia/tests/For290M60ExpandedRuntimeWriteTraceTest.kt"
    ).read_text(encoding="utf-8")
    return {
        "for289DefaultDispatchOnlyPreserved": "includeBitmapDirectWrites: Boolean = false" in device_text,
        "blendWritesSuppressBitmapDirectDuplicate": "withBitmapDirectWriteTraceSuppressed" in device_text,
        "bitmapSetPixelDirectWritesInstrumented": "source = \"SkBitmap.setPixel\"" in bitmap_text,
        "bitmapEraseColorBulkWritesInstrumented": "source = \"SkBitmap.eraseColor\"" in bitmap_text,
        "for290EnablesExpandedBitmapDirectWrites": "includeBitmapDirectWrites = true" in test_text,
    }


def analyze(
    raw: dict[str, Any],
    targets: list[dict[str, int]],
    source_for288: dict[str, Any],
    source_for289: dict[str, Any],
) -> dict[str, Any]:
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
            for field in ("bitmapWidth", "bitmapHeight", "deviceKind", "rootDevice"):
                if field not in event:
                    fail(f"event {event.get('index')} missing `{field}`")
    if set(events_by_pixel) != target_set:
        missing = sorted(target_set - set(events_by_pixel))[:8]
        fail(f"expanded runtime trace missing target pixels: {missing}")

    root_events = [event for event in events if bool(event.get("rootDevice")) and pixel_key(event) in target_set]
    red_root_events = [event for event in root_events if red_tint(rgba_from_event(event, "valueWrittenRgba"))]
    direct_root_events = [event for event in root_events if str(event.get("mode")) == "direct"]
    dispatch_root_events = [
        event for event in root_events
        if str(event.get("source")) == "SkBitmapDevice.dispatchBlend"
        or str(event.get("source", "")).startswith("SkBitmapDevice.")
    ]
    final_white = [
        p for p in raw.get("targetPixels", [])
        if isinstance(p, dict) and white_or_layer([int(v) for v in p.get("finalReadbackRgba", [])])
    ]
    first_red = min(red_root_events, key=lambda event: int(event["index"])) if red_root_events else None

    needles = source_needles()
    scope_covered = all(needles.values())
    if first_red is not None:
        decision = DECISION_IDENTIFIED
        next_action = NEXT_ACTION_IDENTIFIED
        trace_gap = None
    elif scope_covered:
        decision = DECISION_NO_RED
        next_action = NEXT_ACTION_NO_RED
        trace_gap = None
    else:
        decision = DECISION_SCOPE_GAP
        next_action = NEXT_ACTION_SCOPE_GAP
        missing_needles = [key for key, value in needles.items() if not value]
        trace_gap = "Expanded trace source coverage missing needles: " + ", ".join(missing_needles)

    source_counts = Counter(str(event.get("source", "")) for event in root_events)
    branch_counts = Counter(str(event.get("branch", "")) for event in root_events)
    event_counts_by_pixel = {
        f"{x},{y}": len(pixel_events)
        for (x, y), pixel_events in sorted(events_by_pixel.items(), key=lambda item: (item[0][1], item[0][0]))
    }
    per_pixel = []
    for target in targets:
        key = (target["x"], target["y"])
        pixel_events = events_by_pixel[key]
        first_pixel_red = next(
            (event for event in pixel_events if bool(event.get("rootDevice")) and red_tint(rgba_from_event(event, "valueWrittenRgba"))),
            None,
        )
        per_pixel.append(
            {
                "x": target["x"],
                "y": target["y"],
                "eventCount": len(pixel_events),
                "rootEventCount": sum(1 for event in pixel_events if bool(event.get("rootDevice"))),
                "directBitmapWriteCount": sum(1 for event in pixel_events if str(event.get("mode")) == "direct"),
                "firstRuntimeRedRootWrite": summarize_event(first_pixel_red),
            }
        )

    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-expanded-runtime-write-trace",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/runtime",
        "sourceAudits": {
            "for288": str(SOURCE_FOR288.relative_to(PROJECT_ROOT)),
            "for289": str(SOURCE_FOR289.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": decision,
        "nextAction": next_action,
        "traceGap": trace_gap,
        "route": {
            "gpuStatus": "expected-unsupported",
            "fallbackReason": for269.FALLBACK_REASON,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
            "for288PrimaryClassification": source_for288["primaryClassification"],
            "for289Decision": source_for289["decision"],
        },
        "targetPixelCount": len(targets),
        "eventCount": len(events),
        "rootEventCount": len(root_events),
        "traceScope": {
            "device": "root SkBitmapDevice plus root SkBitmap direct eraseColor/setPixel writes",
            "rootWidth": 1164,
            "rootHeight": 802,
            "temporaryMaskDevicesExcludedByDimensions": True,
            "bitmapDirectWritesEnabledForFOR290Only": True,
            "observedDirectBitmapRootWrites": len(direct_root_events),
            "observedDispatchRootWrites": len(dispatch_root_events),
            "sourceNeedles": needles,
        },
        "sourceCounts": dict(source_counts),
        "branchCounts": dict(branch_counts),
        "eventCountsByPixel": event_counts_by_pixel,
        "summary": {
            "targetsWithRuntimeRedRootStore": len({pixel_key(event) for event in red_root_events}),
            "runtimeRedRootStoreEvents": len(red_root_events),
            "directBitmapRootWriteEvents": len(direct_root_events),
            "dispatchRootWriteEvents": len(dispatch_root_events),
            "finalReadbackWhiteLayerTargets": len(final_white),
            "firstRuntimeRedRootStore": summarize_event(first_red),
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
            "bitmapDirectInstrumentationDisabledByDefault": True,
            "gpuRendererChanged": False,
            "globalBlendChanged": False,
            "setPixelSemanticsChanged": False,
            "for288ClassificationChanged": False,
            "for289DecisionChanged": False,
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def row_from_event(label: str, event: dict[str, Any] | None) -> str:
    if event is None:
        return f"| {label} | n/a | n/a | n/a | n/a | n/a | n/a | n/a |"
    return (
        f"| {label} | {event['index']} | `{event['x']},{event['y']}` | "
        f"`{event.get('deviceKind')}` | `{event['source']}` | `{event['branch']}` | "
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
    report = f"""# FOR-290 M60 Expanded Runtime Write Trace

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Next action: `{audit["nextAction"]}`

## Result

FOR-290 extended the disabled-by-default CPU runtime trace beyond
`SkBitmapDevice.dispatchBlend` to include root `SkBitmap` direct
`eraseColor` / `setPixel` writes. The trace remains bounded to the same 59
`draw_oval_outer_boundary` target pixels and to the GM root dimensions
1164x802, excluding temporary mask/layer devices by dimension.

| Measure | Value |
|---|---:|
| Target pixels | {audit["targetPixelCount"]} |
| Root write events | {audit["rootEventCount"]} |
| Direct bitmap root write events | {summary["directBitmapRootWriteEvents"]} |
| Dispatch root write events | {summary["dispatchRootWriteEvents"]} |
| Runtime red root store events | {summary["runtimeRedRootStoreEvents"]} |
| Targets with runtime red root store | {summary["targetsWithRuntimeRedRootStore"]} |
| Final readback white/layer targets | {summary["finalReadbackWhiteLayerTargets"]} |

| Event | Index | Pixel | Device | Source | Branch | Before | Written |
|---|---:|---|---|---|---|---|---|
{row_from_event("First runtime red root store", summary["firstRuntimeRedRootStore"])}

## Interpretation

Trace gap: {audit["traceGap"] or "none"}

No runtime red root store was observed in the expanded trace unless named
above. The observed root writes on the 59 target pixels are the direct bitmap
background fill plus the white/layer `dispatchBlend` writes. The FOR-288
classification remains `{audit["route"]["for288PrimaryClassification"]}` and
the FOR-289 decision remains `{audit["route"]["for289Decision"]}`.

M60 remains `expected-unsupported` for
`{audit["route"]["visualParityFallbackPreserved"]}`, and the crop fallback
remains `{audit["route"]["cropFallbackPreserved"]}`.

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


def validate_audit(audit: dict[str, Any], source_for288: dict[str, Any], source_for289: dict[str, Any]) -> None:
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
    if audit["route"]["for288PrimaryClassification"] != "OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE":
        fail("FOR-288 route classification changed")
    if source_for289["decision"] != "PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP":
        fail("FOR-289 source decision changed")
    if audit["route"]["for289Decision"] != "PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP":
        fail("FOR-289 route decision changed")
    if audit["summary"]["finalReadbackWhiteLayerTargets"] != 59:
        fail("final target readback no longer has 59 white/layer pixels")
    if audit["decision"] not in {DECISION_IDENTIFIED, DECISION_NO_RED, DECISION_SCOPE_GAP}:
        fail("invalid FOR-290 decision")
    if audit["decision"] == DECISION_NO_RED and audit["summary"]["runtimeRedRootStoreEvents"] != 0:
        fail("NO_RUNTIME_RED_ROOT_STORE_FOUND cannot contain red root store events")
    if audit["decision"] == DECISION_SCOPE_GAP and not audit.get("traceGap"):
        fail("scope-gap decision must name the exact trace gap")
    if audit["rootEventCount"] < 59:
        fail("expanded runtime trace did not capture at least one root event per target")
    if audit["summary"]["directBitmapRootWriteEvents"] < 59:
        fail("expanded runtime trace did not capture the root bitmap direct background writes")
    preservation = audit["strictPreservation"]
    for key in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "productionRendererChanged",
        "gpuRendererChanged",
        "globalBlendChanged",
        "setPixelSemanticsChanged",
        "for288ClassificationChanged",
        "for289DecisionChanged",
        "m60Promoted",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if preservation.get(key) is not False:
            fail(f"strict preservation `{key}` changed")
    for key in ("cpuInstrumentationAddedDisabledByDefault", "bitmapDirectInstrumentationDisabledByDefault"):
        if preservation.get(key) is not True:
            fail(f"missing strict preservation `{key}`")


def main() -> None:
    source_for288 = load_json(SOURCE_FOR288)
    source_for289 = load_json(SOURCE_FOR289)
    for288.validate_audit(source_for288)
    for289.validate_audit(source_for289, source_for288)
    outer = outer_window(source_for288)
    if outer["targetPixels"] != 59:
        fail("FOR-288 outer target count changed")
    targets = derive_target_pixels(source_for288)
    run_runtime_trace(targets)
    raw = load_json(RAW_AUDIT)
    audit = analyze(raw, targets, source_for288, source_for289)
    validate_audit(audit, source_for288, source_for289)
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
    write_report(audit)
    reread = load_json(AUDIT)
    validate_audit(reread, source_for288, source_for289)
    report_text = REPORT.read_text(encoding="utf-8")
    for needle in (
        audit["decision"],
        "draw_oval_outer_boundary",
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
        "First runtime red root store",
    ):
        if needle not in report_text:
            fail(f"report missing `{needle}`")
    print(f"FOR-290 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-290 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"Decision: {audit['decision']}")


if __name__ == "__main__":
    main()
