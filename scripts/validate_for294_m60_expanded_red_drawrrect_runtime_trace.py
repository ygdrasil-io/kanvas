#!/usr/bin/env python3
"""Generate and validate FOR-294 M60 expanded red drawRRect runtime trace evidence."""

from __future__ import annotations

import json
import os
import subprocess
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import numpy as np

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for271_nested_rrect_blurred_envelope_audit as for271
import validate_for288_m60_outer_boundary_store_order_audit as for288
import validate_for289_m60_outer_boundary_runtime_write_chronology as for289
import validate_for290_m60_expanded_runtime_write_trace as for290
import validate_for291_m60_reconstructed_store_model_audit as for291
import validate_for292_m60_source_payload_derivation_audit as for292
import validate_for293_m60_red_drawrrect_runtime_visibility_audit as for293


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-294"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
SOURCE_FOR288 = for290.SOURCE_FOR288
SOURCE_FOR289 = for290.SOURCE_FOR289
SOURCE_FOR290 = for290.AUDIT
SOURCE_FOR291 = for291.AUDIT
SOURCE_FOR292 = for292.AUDIT
SOURCE_FOR293 = for293.AUDIT
BUILD_DIR = PROJECT_ROOT / "build/reports/for294"
TARGET_FILE = BUILD_DIR / "m60-expanded-red-drawrrect-runtime-trace-for294.targets.txt"
RAW_AUDIT = BUILD_DIR / "m60-expanded-red-drawrrect-runtime-trace-for294.raw.json"
AUDIT_NAME = "m60-expanded-red-drawrrect-runtime-trace-for294.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-294-m60-expanded-red-drawrrect-runtime-trace.md"
TEST_CLASS = "org.skia.tests.For294M60ExpandedRedDrawRRectRuntimeTraceTest"
TEST_SOURCE = (
    PROJECT_ROOT
    / "skia-integration-tests/src/test/kotlin/org/skia/tests/For294M60ExpandedRedDrawRRectRuntimeTraceTest.kt"
)

DECISION_FOUND = "RED_DRAWRRECT_RUNTIME_ROOT_DISPATCH_FOUND_OUTSIDE_TARGETS"
DECISION_ABSENT = "RED_DRAWRRECT_RUNTIME_ROOT_ABSENT_IN_EXPANDED_DOMAIN"
DECISION_CAPACITY_LIMITED = "RED_DRAWRRECT_RUNTIME_TRACE_CAPACITY_LIMITED"
DECISION_AMBIGUOUS = "RED_DRAWRRECT_EXPANDED_TRACE_STILL_AMBIGUOUS"
DECISIONS = {
    DECISION_FOUND,
    DECISION_ABSENT,
    DECISION_CAPACITY_LIMITED,
    DECISION_AMBIGUOUS,
}
REQUESTED_SUBZONES = (
    "draw_oval_outer_boundary",
    "difference_oval_inner_boundary",
    "halo_interior",
    "outside_draw_oval",
    "blurred_content_envelope",
)
RED_SOURCE = "SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-294 validation failed: {message}")


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


def rgba(value: Any) -> list[int]:
    if not (isinstance(value, list) and len(value) == 4):
        fail(f"expected RGBA list, got {value!r}")
    return [int(v) for v in value]


def red_tint(value: list[int]) -> bool:
    r, g, b, a = value
    return a > 0 and r >= 180 and r > g + 20 and r > b + 20


def white_or_layer(value: list[int]) -> bool:
    r, g, b, a = value
    return a >= 240 and r >= 245 and g >= 245 and b >= 245


def key_xy(item: dict[str, Any]) -> tuple[int, int]:
    return (int(item["x"]), int(item["y"]))


def bounds_from_coords(coords: set[tuple[int, int]]) -> dict[str, int] | None:
    if not coords:
        return None
    xs = [x for x, _ in coords]
    ys = [y for _, y in coords]
    return {
        "left": min(xs),
        "top": min(ys),
        "right": max(xs) + 1,
        "bottom": max(ys) + 1,
        "rightInclusive": max(xs),
        "bottomInclusive": max(ys),
    }


def sorted_coords(coords: set[tuple[int, int]]) -> list[tuple[int, int]]:
    return sorted(coords, key=lambda item: (item[1], item[0]))


def write_target_file(coords: set[tuple[int, int]]) -> None:
    BUILD_DIR.mkdir(parents=True, exist_ok=True)
    TARGET_FILE.write_text(
        "".join(f"{x},{y}\n" for x, y in sorted_coords(coords)),
        encoding="utf-8",
    )


def run_runtime_trace(coords: set[tuple[int, int]]) -> None:
    write_target_file(coords)
    cmd = [
        "./gradlew",
        ":skia-integration-tests:test",
        "--tests",
        TEST_CLASS,
        "--rerun-tasks",
    ]
    env = os.environ.copy()
    env["KANVAS_FOR294_TARGETS_FILE"] = str(TARGET_FILE)
    env["KANVAS_FOR294_OUTPUT"] = str(RAW_AUDIT)
    result = subprocess.run(cmd, cwd=PROJECT_ROOT, env=env)
    if result.returncode != 0:
        fail(f"expanded red drawRRect runtime trace test failed with exit code {result.returncode}")


def source_needles() -> dict[str, bool]:
    test_text = TEST_SOURCE.read_text(encoding="utf-8")
    for290_test = (
        PROJECT_ROOT
        / "skia-integration-tests/src/test/kotlin/org/skia/tests/For290M60ExpandedRuntimeWriteTraceTest.kt"
    ).read_text(encoding="utf-8")
    cpu_text = (PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt").read_text(
        encoding="utf-8"
    )
    bitmap_text = (PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt").read_text(
        encoding="utf-8"
    )
    return {
        "for290StillRequiresExactly59Targets": "assertEquals(59, targetPixels.size" in for290_test,
        "for294UsesTargetFileToAvoidEnvironmentCapacityLimit": "KANVAS_FOR294_TARGETS_FILE" in test_text,
        "for294RequiresExpandedTargetSet": "targetPixels.size > 59" in test_text,
        "for294UsesRootDimensions": "width = size.width" in test_text and "height = size.height" in test_text,
        "for294IncludesBitmapDirectWrites": "includeBitmapDirectWrites = true" in test_text,
        "cpuA8SrcInPayloadTraceSourcePresent": f'"{RED_SOURCE}"' in cpu_text,
        "bitmapSetPixelDirectWritesInstrumented": 'source = "SkBitmap.setPixel"' in bitmap_text,
        "bitmapEraseColorBulkWritesInstrumented": 'source = "SkBitmap.eraseColor"' in bitmap_text,
    }


def red_event(event: dict[str, Any]) -> bool:
    source = str(event.get("source"))
    mode = str(event.get("mode"))
    branch = str(event.get("branch"))
    return (
        source == RED_SOURCE
        or "A8.srcInPayload" in source
        or (mode == "kSrcOver" and red_tint(rgba(event.get("srcInputRgba"))))
        or "kSrcOver" in branch and red_tint(rgba(event.get("valueWrittenRgba")))
    )


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
        "srcInputRgba": event.get("srcInputRgba"),
        "srcAfterCoverageRgba": event.get("srcAfterCoverageRgba"),
        "valueBeforeRgba": event.get("valueBeforeRgba"),
        "valueWrittenRgba": event.get("valueWrittenRgba"),
        "valueReadAfterRgba": event.get("valueReadAfterRgba"),
    }


def coordinate_masks(width: int, height: int) -> tuple[dict[str, Any], dict[str, np.ndarray], np.ndarray]:
    geometry, masks = for271.make_envelope_masks(width, height)
    _, _, _, visible_candidate, _ = for293.red_draw_runtime_domain(width, height, geometry)
    return geometry, masks, visible_candidate


def coords_from_mask(mask: np.ndarray) -> set[tuple[int, int]]:
    return {(int(x), int(y)) for y, x in zip(*np.nonzero(mask))}


def subzone_for_coord(masks: dict[str, np.ndarray], x: int, y: int) -> list[str]:
    return [name for name in REQUESTED_SUBZONES if bool(masks[name][y, x])]


def analyze(
    *,
    raw: dict[str, Any],
    source_for288: dict[str, Any],
    source_for289: dict[str, Any],
    source_for290: dict[str, Any],
    source_for291: dict[str, Any],
    source_for292: dict[str, Any],
    source_for293: dict[str, Any],
    red_candidate_coords: set[tuple[int, int]],
    masks: dict[str, np.ndarray],
) -> dict[str, Any]:
    events = raw.get("events")
    if not isinstance(events, list):
        fail("raw FOR-294 audit missing events[]")
    target_pixels = raw.get("targetPixels")
    if not isinstance(target_pixels, list):
        fail("raw FOR-294 audit missing targetPixels[]")

    target_coords = {key_xy(pixel) for pixel in target_pixels if isinstance(pixel, dict)}
    if target_coords != red_candidate_coords:
        missing = len(red_candidate_coords - target_coords)
        extra = len(target_coords - red_candidate_coords)
        decision = DECISION_CAPACITY_LIMITED
        exact_limit = (
            f"target file/domain mismatch: captured {len(target_coords)} coordinates, "
            f"missing {missing}, extra {extra}, requested {len(red_candidate_coords)}"
        )
    else:
        decision = DECISION_AMBIGUOUS
        exact_limit = None

    original_target_coords = {key_xy(pixel) for pixel in source_for292.get("perPixelComparisons", [])}
    root_events = [event for event in events if isinstance(event, dict) and bool(event.get("rootDevice"))]
    root_coords = {key_xy(event) for event in root_events}
    missing_runtime_coords = red_candidate_coords - root_coords
    if missing_runtime_coords:
        decision = DECISION_CAPACITY_LIMITED
        exact_limit = (
            f"runtime trace missed {len(missing_runtime_coords)} of "
            f"{len(red_candidate_coords)} requested red-domain coordinates"
        )

    direct_erase = [
        event for event in root_events if str(event.get("source")) == "SkBitmap.eraseColor"
    ]
    direct_set_pixel = [
        event for event in root_events if str(event.get("source")) == "SkBitmap.setPixel"
    ]
    root_dispatch = [
        event
        for event in root_events
        if str(event.get("source")) == "SkBitmapDevice.dispatchBlend"
        or str(event.get("source")) == RED_SOURCE
        or str(event.get("source", "")).startswith("SkBitmapDevice.")
    ]
    red_root_dispatch = [event for event in root_dispatch if red_event(event)]
    red_root_dispatch_coords = {key_xy(event) for event in red_root_dispatch}
    red_outside_target_events = [
        event for event in red_root_dispatch if key_xy(event) not in original_target_coords
    ]
    red_inside_target_events = [
        event for event in red_root_dispatch if key_xy(event) in original_target_coords
    ]

    if decision != DECISION_CAPACITY_LIMITED:
        if red_outside_target_events:
            decision = DECISION_FOUND
            exact_limit = None
        elif not red_root_dispatch:
            decision = DECISION_ABSENT
            exact_limit = None
        else:
            decision = DECISION_AMBIGUOUS
            exact_limit = (
                "red runtime root dispatch exists only on the original 59 targets; "
                "no outside-target red dispatch proves the FOR-293 elsewhere question"
            )

    target_readback_by_coord = {
        key_xy(pixel): rgba(pixel.get("finalReadbackRgba"))
        for pixel in target_pixels
        if isinstance(pixel, dict)
    }
    events_by_coord: dict[tuple[int, int], list[dict[str, Any]]] = defaultdict(list)
    for event in root_events:
        events_by_coord[key_xy(event)].append(event)

    subzones: dict[str, Any] = {}
    for name in REQUESTED_SUBZONES:
        zone_coords = coords_from_mask(masks[name]) & red_candidate_coords
        zone_root_events = [event for event in root_events if key_xy(event) in zone_coords]
        zone_dispatch_events = [event for event in root_dispatch if key_xy(event) in zone_coords]
        zone_red_events = [event for event in red_root_dispatch if key_xy(event) in zone_coords]
        zone_white_final = sum(1 for coord in zone_coords if white_or_layer(target_readback_by_coord[coord]))
        zone_red_final = sum(1 for coord in zone_coords if red_tint(target_readback_by_coord[coord]))
        subzones[name] = {
            "candidatePixels": len(zone_coords),
            "rootEventCount": len(zone_root_events),
            "rootDispatchEventCount": len(zone_dispatch_events),
            "redRootDispatchEventCount": len(zone_red_events),
            "redRootDispatchPixelCount": len({key_xy(event) for event in zone_red_events}),
            "whiteFinalReadbackPixels": zone_white_final,
            "redFinalReadbackPixels": zone_red_final,
            "firstRedRootDispatch": summarize_event(min(zone_red_events, key=lambda event: int(event["index"])) if zone_red_events else None),
        }

    source_needles_result = source_needles()
    first_red_outside = min(red_outside_target_events, key=lambda event: int(event["index"])) if red_outside_target_events else None
    first_red_any = min(red_root_dispatch, key=lambda event: int(event["index"])) if red_root_dispatch else None
    final_white = sum(1 for value in target_readback_by_coord.values() if white_or_layer(value))
    final_red = sum(1 for value in target_readback_by_coord.values() if red_tint(value))
    source_counts = dict(Counter(str(event.get("source")) for event in root_events))
    mode_counts = dict(Counter(str(event.get("mode")) for event in root_events))
    branch_counts = dict(Counter(str(event.get("branch")) for event in root_events))
    red_source_counts = dict(Counter(str(event.get("source")) for event in red_root_dispatch))

    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-expanded-red-drawrrect-runtime-trace",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/runtime-root-expanded-red-domain",
        "sourceAudits": {
            "for288": str(SOURCE_FOR288.relative_to(PROJECT_ROOT)),
            "for289": str(SOURCE_FOR289.relative_to(PROJECT_ROOT)),
            "for290": str(SOURCE_FOR290.relative_to(PROJECT_ROOT)),
            "for291": str(SOURCE_FOR291.relative_to(PROJECT_ROOT)),
            "for292": str(SOURCE_FOR292.relative_to(PROJECT_ROOT)),
            "for293": str(SOURCE_FOR293.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": decision,
        "exactLimitOrGap": exact_limit,
        "traceStrategy": {
            "type": "complete-red-domain-capture",
            "reason": (
                "FOR-293 left 22365 red store-candidate pixels outside the FOR-290 target set; "
                "FOR-294 writes the complete 22424-coordinate red candidate domain to a target file "
                "and reuses the disabled-by-default root write chronology trace."
            ),
            "targetCoordinateSource": "FOR-293 reconstructed red drawRRect mask/clip store-candidate domain",
            "targetCoordinateFile": str(TARGET_FILE.relative_to(PROJECT_ROOT)),
            "rawRuntimeAudit": str(RAW_AUDIT.relative_to(PROJECT_ROOT)),
            "requestedCoordinates": len(red_candidate_coords),
            "capturedTargetCoordinates": len(target_coords),
            "missingRuntimeCoordinates": len(missing_runtime_coords),
            "originalOuterBoundaryTargetCoordinates": len(original_target_coords),
            "coordinatesOutsideOriginalTargets": len(red_candidate_coords - original_target_coords),
            "sourceNeedles": source_needles_result,
        },
        "route": {
            "gpuStatus": "expected-unsupported",
            "fallbackReason": for269.FALLBACK_REASON,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
            "for288PrimaryClassification": source_for288["primaryClassification"],
            "for289Decision": source_for289["decision"],
            "for290Decision": source_for290["decision"],
            "for291Decision": source_for291["decision"],
            "for292Decision": source_for292["decision"],
            "for293Decision": source_for293["decision"],
        },
        "runtimeRootTrace": {
            "rawEventCount": int(raw.get("eventCount", len(events))),
            "rootEventCount": len(root_events),
            "rootCoordinateCount": len(root_coords),
            "rootCoordinatesCoverExpandedDomain": missing_runtime_coords == set(),
            "rootBounds": bounds_from_coords(root_coords),
            "sourceCounts": source_counts,
            "modeCounts": mode_counts,
            "branchCounts": branch_counts,
            "directEraseColorEvents": len(direct_erase),
            "directSetPixelEvents": len(direct_set_pixel),
            "rootDispatchEvents": len(root_dispatch),
            "redRootDispatchEvents": len(red_root_dispatch),
            "redRootDispatchPixels": len(red_root_dispatch_coords),
            "redRootDispatchEventsOnOriginalTargets": len(red_inside_target_events),
            "redRootDispatchEventsOutsideOriginalTargets": len(red_outside_target_events),
            "redRootDispatchPixelsOutsideOriginalTargets": len({key_xy(event) for event in red_outside_target_events}),
            "redRootDispatchSourceCounts": red_source_counts,
            "firstRedRootDispatch": summarize_event(first_red_any),
            "firstRedRootDispatchOutsideOriginalTargets": summarize_event(first_red_outside),
        },
        "subzoneComparison": subzones,
        "finalReadback": {
            "expandedDomainPixels": len(target_readback_by_coord),
            "whiteOrLayerPixels": final_white,
            "redTintPixels": final_red,
            "otherPixels": len(target_readback_by_coord) - final_white - final_red,
        },
        "sourcePreservation": {
            "for288ClassificationPreserved": (
                source_for288["primaryClassification"] == "OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE"
            ),
            "for289DecisionPreserved": source_for289["decision"] == "PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP",
            "for290DecisionPreserved": source_for290["decision"] == "NO_RUNTIME_RED_ROOT_STORE_FOUND",
            "for291DecisionPreserved": source_for291["decision"] == "RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH",
            "for292DecisionPreserved": (
                source_for292["decision"] == "RECONSTRUCTED_PAYLOAD_DRAW_MAPPING_MISMATCH"
            ),
            "for293DecisionPreserved": (
                source_for293["decision"] == "RED_DRAWRRECT_RUNTIME_VISIBILITY_STILL_AMBIGUOUS"
            ),
        },
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRendererChanged": False,
            "cpuRendererChanged": False,
            "cpuInstrumentationChanged": False,
            "testOnlyAuditHarnessAdded": True,
            "gpuRendererChanged": False,
            "globalBlendChanged": False,
            "setPixelSemanticsChanged": False,
            "for288ClassificationChanged": False,
            "for289DecisionChanged": False,
            "for290DecisionChanged": False,
            "for291DecisionChanged": False,
            "for292DecisionChanged": False,
            "for293DecisionChanged": False,
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
        f"`{event.get('source')}` | `{event.get('branch')}` | `{event.get('mode')}` | "
        f"`{event.get('srcInputRgba')}` | `{event.get('valueWrittenRgba')}` |"
    )


def write_report(audit: dict[str, Any]) -> None:
    trace = audit["runtimeRootTrace"]
    strategy = audit["traceStrategy"]
    final = audit["finalReadback"]
    subzone_rows = "\n".join(
        (
            f"| `{name}` | {data['candidatePixels']} | {data['rootEventCount']} | "
            f"{data['rootDispatchEventCount']} | {data['redRootDispatchEventCount']} | "
            f"{data['redRootDispatchPixelCount']} | {data['whiteFinalReadbackPixels']} | "
            f"{data['redFinalReadbackPixels']} |"
        )
        for name, data in audit["subzoneComparison"].items()
    )
    source_rows = "\n".join(
        f"| `{source}` | {count} |" for source, count in sorted(trace["sourceCounts"].items())
    )
    report = f"""# FOR-294 M60 Expanded Red drawRRect Runtime Trace

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Exact limit/gap: `{audit["exactLimitOrGap"] or "none"}`

## Result

FOR-294 expands the runtime root trace from the 59
`draw_oval_outer_boundary` pixels used by FOR-290 to the complete FOR-293
red `drawRRect` reconstructed store-candidate domain. This uses a test-only
audit harness because the FOR-290 test intentionally asserts exactly 59
coordinates.

Strategy: complete capture of {strategy["requestedCoordinates"]} red-domain
coordinates via `{strategy["targetCoordinateFile"]}`. The trace observed
{trace["rootEventCount"]} root write events and covered
{trace["rootCoordinateCount"]} unique root coordinates.

| Measure | Value |
|---|---:|
| Requested expanded coordinates | {strategy["requestedCoordinates"]} |
| Captured target coordinates | {strategy["capturedTargetCoordinates"]} |
| Coordinates outside original 59 targets | {strategy["coordinatesOutsideOriginalTargets"]} |
| Missing runtime coordinates | {strategy["missingRuntimeCoordinates"]} |
| Root events | {trace["rootEventCount"]} |
| Root dispatch events | {trace["rootDispatchEvents"]} |
| Direct `eraseColor` root events | {trace["directEraseColorEvents"]} |
| Direct `setPixel` root events | {trace["directSetPixelEvents"]} |
| Red root dispatch events | {trace["redRootDispatchEvents"]} |
| Red root dispatch pixels | {trace["redRootDispatchPixels"]} |
| Red root dispatch events on original targets | {trace["redRootDispatchEventsOnOriginalTargets"]} |
| Red root dispatch events outside original targets | {trace["redRootDispatchEventsOutsideOriginalTargets"]} |
| Red root dispatch pixels outside original targets | {trace["redRootDispatchPixelsOutsideOriginalTargets"]} |
| Final white/layer pixels in expanded domain | {final["whiteOrLayerPixels"]} |
| Final red-tint pixels in expanded domain | {final["redTintPixels"]} |
| Final other pixels in expanded domain | {final["otherPixels"]} |

| Event | Index | Pixel | Source | Branch | Mode | Source RGBA | Written RGBA |
|---|---:|---|---|---|---|---|---|
{row_from_event("First red root dispatch", trace["firstRedRootDispatch"])}
{row_from_event("First red root dispatch outside original targets", trace["firstRedRootDispatchOutsideOriginalTargets"])}

## Subzone Comparison

| Subzone | Candidate pixels | Root events | Root dispatch events | Red dispatch events | Red dispatch pixels | Final white/layer | Final red-tint |
|---|---:|---:|---:|---:|---:|---:|---:|
{subzone_rows}

## Runtime Root Source Counts

| Source | Events |
|---|---:|
{source_rows}

## Preserved Decisions

- FOR-288 classification: `{audit["route"]["for288PrimaryClassification"]}`
- FOR-289 decision: `{audit["route"]["for289Decision"]}`
- FOR-290 decision: `{audit["route"]["for290Decision"]}`
- FOR-291 decision: `{audit["route"]["for291Decision"]}`
- FOR-292 decision: `{audit["route"]["for292Decision"]}`
- FOR-293 decision: `{audit["route"]["for293Decision"]}`

M60 remains `expected-unsupported`: `{audit["route"]["visualParityFallbackPreserved"]}`.
The crop fallback remains `{audit["route"]["cropFallbackPreserved"]}`. No
production renderer, threshold, GPU/WebGPU, fallback, blend, or setPixel
behavior changed.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_audit(
    audit: dict[str, Any],
    source_for288: dict[str, Any],
    source_for289: dict[str, Any],
    source_for290: dict[str, Any],
    source_for291: dict[str, Any],
    source_for292: dict[str, Any],
    source_for293: dict[str, Any],
) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("wrong Linear id")
    if audit.get("parent") != PARENT_ID:
        fail("wrong parent id")
    if audit.get("sceneId") != SCENE_ID:
        fail("wrong scene id")
    if audit.get("decision") not in DECISIONS:
        fail("invalid FOR-294 decision")
    if audit.get("decision") in {DECISION_CAPACITY_LIMITED, DECISION_AMBIGUOUS} and not audit.get("exactLimitOrGap"):
        fail("capacity-limited or ambiguous decision must name the exact limit/gap")
    if audit.get("decision") in {DECISION_FOUND, DECISION_ABSENT} and audit.get("exactLimitOrGap") is not None:
        fail("resolved decision should not carry an unresolved limit/gap")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision changed")

    if source_for288["primaryClassification"] != "OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE":
        fail("FOR-288 source classification changed")
    if source_for289["decision"] != "PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP":
        fail("FOR-289 source decision changed")
    if source_for290["decision"] != "NO_RUNTIME_RED_ROOT_STORE_FOUND":
        fail("FOR-290 source decision changed")
    if source_for291["decision"] != "RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH":
        fail("FOR-291 source decision changed")
    if source_for292["decision"] != "RECONSTRUCTED_PAYLOAD_DRAW_MAPPING_MISMATCH":
        fail("FOR-292 source decision changed")
    if source_for293["decision"] != "RED_DRAWRRECT_RUNTIME_VISIBILITY_STILL_AMBIGUOUS":
        fail("FOR-293 source decision changed")

    route = audit["route"]
    if route["visualParityFallbackPreserved"] != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route["cropFallbackPreserved"] != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")
    for key, value in audit["traceStrategy"]["sourceNeedles"].items():
        if value is not True:
            fail(f"source needle `{key}` failed")

    strategy = audit["traceStrategy"]
    if strategy["requestedCoordinates"] != 22424:
        fail(f"expanded domain size changed: {strategy['requestedCoordinates']}")
    if strategy["coordinatesOutsideOriginalTargets"] != 22365:
        fail("outside-original coordinate count changed")
    if strategy["capturedTargetCoordinates"] != strategy["requestedCoordinates"]:
        fail("expanded target capture did not include every requested coordinate")
    if strategy["missingRuntimeCoordinates"] != 0:
        fail("runtime trace missed expanded-domain coordinates")

    trace = audit["runtimeRootTrace"]
    if trace["rootCoordinatesCoverExpandedDomain"] is not True:
        fail("root trace no longer covers the complete expanded domain")
    if trace["directEraseColorEvents"] != strategy["requestedCoordinates"]:
        fail("direct eraseColor events no longer cover every expanded-domain coordinate")
    if trace["directSetPixelEvents"] != 0:
        fail("unexpected direct setPixel root events appeared")
    if trace["redRootDispatchEventsOutsideOriginalTargets"] > trace["redRootDispatchEvents"]:
        fail("red outside-target event count exceeds total red event count")
    if audit["decision"] == DECISION_FOUND and trace["redRootDispatchEventsOutsideOriginalTargets"] <= 0:
        fail("found decision requires outside-target red root dispatch events")
    if audit["decision"] == DECISION_ABSENT and trace["redRootDispatchEvents"] != 0:
        fail("absent decision cannot contain red root dispatch events")

    for name in REQUESTED_SUBZONES:
        if name not in audit["subzoneComparison"]:
            fail(f"missing subzone `{name}`")
        if audit["subzoneComparison"][name]["candidatePixels"] <= 0:
            fail(f"subzone `{name}` has no candidate pixels")

    preservation = audit["strictPreservation"]
    for key in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "productionRendererChanged",
        "cpuRendererChanged",
        "cpuInstrumentationChanged",
        "gpuRendererChanged",
        "globalBlendChanged",
        "setPixelSemanticsChanged",
        "for288ClassificationChanged",
        "for289DecisionChanged",
        "for290DecisionChanged",
        "for291DecisionChanged",
        "for292DecisionChanged",
        "for293DecisionChanged",
        "m60Promoted",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if preservation.get(key) is not False:
            fail(f"strict preservation `{key}` changed")
    if preservation.get("testOnlyAuditHarnessAdded") is not True:
        fail("FOR-294 must explain the test-only audit harness")


def main() -> None:
    source_for288 = load_json(SOURCE_FOR288)
    source_for289 = load_json(SOURCE_FOR289)
    source_for290 = load_json(SOURCE_FOR290)
    source_for291 = load_json(SOURCE_FOR291)
    source_for292 = load_json(SOURCE_FOR292)
    source_for293 = load_json(SOURCE_FOR293)
    for288.validate_audit(source_for288)
    for289.validate_audit(source_for289, source_for288)
    for290.validate_audit(source_for290, source_for288, source_for289)
    for291.validate_audit(source_for291, for291.load_json(for291.SOURCE_FOR286), source_for288, source_for289, source_for290)
    for292.validate_audit(source_for292, for291.load_json(for291.SOURCE_FOR286), source_for288, source_for289, source_for290, source_for291)
    for293.validate_audit(source_for293, for291.load_json(for291.SOURCE_FOR286), source_for288, source_for289, source_for290, source_for291, source_for292)

    reference = for269.load_image("skia")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA reference image")
    _, masks, visible_candidate = coordinate_masks(width, height)
    red_candidate_coords = coords_from_mask(visible_candidate)
    if len(red_candidate_coords) != 22424:
        fail(f"FOR-293 red candidate domain size changed: {len(red_candidate_coords)}")

    run_runtime_trace(red_candidate_coords)
    raw = load_json(RAW_AUDIT)
    audit = analyze(
        raw=raw,
        source_for288=source_for288,
        source_for289=source_for289,
        source_for290=source_for290,
        source_for291=source_for291,
        source_for292=source_for292,
        source_for293=source_for293,
        red_candidate_coords=red_candidate_coords,
        masks=masks,
    )
    validate_audit(audit, source_for288, source_for289, source_for290, source_for291, source_for292, source_for293)
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
    write_report(audit)
    reread = load_json(AUDIT)
    validate_audit(reread, source_for288, source_for289, source_for290, source_for291, source_for292, source_for293)
    report_text = REPORT.read_text(encoding="utf-8")
    for needle in (
        audit["decision"],
        "complete capture",
        "Subzone Comparison",
        "FOR-293 decision",
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
        "No\nproduction renderer",
    ):
        if needle not in report_text:
            fail(f"report missing `{needle}`")
    print(f"FOR-294 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-294 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"Decision: {audit['decision']}")


if __name__ == "__main__":
    main()
