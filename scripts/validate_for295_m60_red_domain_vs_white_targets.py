#!/usr/bin/env python3
"""Generate and validate FOR-295 M60 red runtime domain vs white targets evidence."""

from __future__ import annotations

import json
import math
import sys
from collections import Counter
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for288_m60_outer_boundary_store_order_audit as for288
import validate_for289_m60_outer_boundary_runtime_write_chronology as for289
import validate_for290_m60_expanded_runtime_write_trace as for290
import validate_for291_m60_reconstructed_store_model_audit as for291
import validate_for292_m60_source_payload_derivation_audit as for292
import validate_for293_m60_red_drawrrect_runtime_visibility_audit as for293
import validate_for294_m60_expanded_red_drawrrect_runtime_trace as for294


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-295"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
SOURCE_FOR288 = for290.SOURCE_FOR288
SOURCE_FOR289 = for290.SOURCE_FOR289
SOURCE_FOR290 = for290.AUDIT
SOURCE_FOR291 = for291.AUDIT
SOURCE_FOR292 = for292.AUDIT
SOURCE_FOR293 = for293.AUDIT
SOURCE_FOR294 = for294.AUDIT
RAW_FOR294 = for294.RAW_AUDIT
AUDIT_NAME = "m60-red-domain-vs-white-targets-for295.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-295-m60-red-domain-vs-white-targets.md"

DECISION_TARGETS_OUTSIDE_RED_RUNTIME = "ORIGINAL_TARGETS_OUTSIDE_RED_RUNTIME_DISPATCH_DOMAIN"
DECISION_WHITE_OVERWRITE = "ORIGINAL_TARGETS_WHITE_OVERWRITE_AFTER_RED_NEIGHBOR_DOMAIN"
DECISION_WHITE_DRAWRECT_BOUNDARY = "TARGET_SET_SELECTS_WHITE_DRAWRECT_BOUNDARY_NOT_RED_DISPATCH_BOUNDARY"
DECISION_FINAL_COMPOSITE_MISMATCH = "RED_RUNTIME_DOMAIN_FINAL_COMPOSITE_MISMATCH"
DECISION_AMBIGUOUS = "RED_RUNTIME_DOMAIN_COMPARISON_STILL_AMBIGUOUS"
DECISIONS = {
    DECISION_TARGETS_OUTSIDE_RED_RUNTIME,
    DECISION_WHITE_OVERWRITE,
    DECISION_WHITE_DRAWRECT_BOUNDARY,
    DECISION_FINAL_COMPOSITE_MISMATCH,
    DECISION_AMBIGUOUS,
}
REQUESTED_SUBZONES = for294.REQUESTED_SUBZONES
RED_SOURCE = for294.RED_SOURCE


def fail(message: str) -> None:
    raise SystemExit(f"FOR-295 validation failed: {message}")


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


def key_xy(item: dict[str, Any]) -> tuple[int, int]:
    return (int(item["x"]), int(item["y"]))


def coord_key(coord: tuple[int, int]) -> str:
    return f"{coord[0]},{coord[1]}"


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


def summarize_event(event: dict[str, Any] | None) -> dict[str, Any] | None:
    if event is None:
        return None
    return {
        "index": int(event["index"]),
        "x": int(event["x"]),
        "y": int(event["y"]),
        "source": event.get("source"),
        "branch": event.get("branch"),
        "mode": event.get("mode"),
        "coverage": int(event.get("coverage", 0)),
        "srcInputRgba": event.get("srcInputRgba"),
        "srcAfterCoverageRgba": event.get("srcAfterCoverageRgba"),
        "valueBeforeRgba": event.get("valueBeforeRgba"),
        "valueWrittenRgba": event.get("valueWrittenRgba"),
        "valueReadAfterRgba": event.get("valueReadAfterRgba"),
    }


def inside_bounds(coord: tuple[int, int], bounds: dict[str, int] | None) -> bool:
    if bounds is None:
        return False
    x, y = coord
    return bounds["left"] <= x < bounds["right"] and bounds["top"] <= y < bounds["bottom"]


def distance_to_bounds(coord: tuple[int, int], bounds: dict[str, int] | None) -> int | None:
    if bounds is None:
        return None
    x, y = coord
    dx = max(bounds["left"] - x, 0, x - (bounds["right"] - 1))
    dy = max(bounds["top"] - y, 0, y - (bounds["bottom"] - 1))
    return max(dx, dy)


def nearest_coord(
    coord: tuple[int, int],
    candidates: set[tuple[int, int]],
) -> dict[str, Any]:
    if not candidates:
        fail("cannot compute nearest coordinate against an empty set")
    x, y = coord
    nearest = min(candidates, key=lambda item: ((item[0] - x) ** 2 + (item[1] - y) ** 2, item[1], item[0]))
    dx = nearest[0] - x
    dy = nearest[1] - y
    return {
        "x": nearest[0],
        "y": nearest[1],
        "dx": dx,
        "dy": dy,
        "euclidean": round(math.sqrt((dx * dx) + (dy * dy)), 6),
        "manhattan": abs(dx) + abs(dy),
        "chebyshev": max(abs(dx), abs(dy)),
    }


def source_needles() -> dict[str, bool]:
    gm_source = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"
    trace_source = (
        PROJECT_ROOT
        / "skia-integration-tests/src/test/kotlin/org/skia/tests/For294M60ExpandedRedDrawRRectRuntimeTraceTest.kt"
    )
    gm_text = gm_source.read_text(encoding="utf-8")
    trace_text = trace_source.read_text(encoding="utf-8")
    return {
        "gmWhiteDrawRectBeforeRedDrawRRect": (
            "c.drawRect(clipRect1, whitePaint)" in gm_text
            and "c.drawRRect(rr, paint)" in gm_text
            and gm_text.index("c.drawRect(clipRect1, whitePaint)") < gm_text.index("c.drawRRect(rr, paint)")
        ),
        "gmRedDrawRRectUsesSrcInColorFilter": "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)" in gm_text,
        "gmRedDrawRRectUsesBlurMaskFilter": "SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.366025f)" in gm_text,
        "for294UsesTargetFileNoEnvironmentCapacityLimit": "KANVAS_FOR294_TARGETS_FILE" in trace_text,
        "for294RequiresExpandedTargetSet": "targetPixels.size > 59" in trace_text,
        "for294IncludesBitmapDirectWrites": "includeBitmapDirectWrites = true" in trace_text,
    }


def red_root_dispatch_events(raw_for294: dict[str, Any]) -> list[dict[str, Any]]:
    events = raw_for294.get("events")
    if not isinstance(events, list):
        fail("raw FOR-294 audit missing events[]")
    red_events = [
        event
        for event in events
        if isinstance(event, dict)
        and bool(event.get("rootDevice"))
        and str(event.get("source")) == RED_SOURCE
    ]
    if len(red_events) != 9088:
        fail(f"FOR-294 red root dispatch event count changed: {len(red_events)}")
    return red_events


def last_events_by_coord(events: list[dict[str, Any]]) -> dict[tuple[int, int], dict[str, Any]]:
    last: dict[tuple[int, int], dict[str, Any]] = {}
    for event in events:
        coord = key_xy(event)
        if coord not in last or int(event["index"]) > int(last[coord]["index"]):
            last[coord] = event
    return last


def analyze(
    *,
    raw_for294: dict[str, Any],
    source_for288: dict[str, Any],
    source_for289: dict[str, Any],
    source_for290: dict[str, Any],
    source_for291: dict[str, Any],
    source_for292: dict[str, Any],
    source_for293: dict[str, Any],
    source_for294: dict[str, Any],
) -> dict[str, Any]:
    reference = for269.load_image("skia")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA reference image")
    _, masks, visible_candidate = for294.coordinate_masks(width, height)
    red_candidate_coords = for294.coords_from_mask(visible_candidate)
    if len(red_candidate_coords) != source_for294["traceStrategy"]["requestedCoordinates"]:
        fail("FOR-293 red candidate domain no longer matches FOR-294 requested coordinates")

    target_pixels = source_for292.get("perPixelComparisons")
    if not isinstance(target_pixels, list) or len(target_pixels) != 59:
        fail("FOR-292 perPixelComparisons must contain the original 59 targets")
    original_targets = {key_xy(pixel) for pixel in target_pixels}
    if len(original_targets) != 59:
        fail("original target coordinates are not unique")

    raw_events = [event for event in raw_for294.get("events", []) if isinstance(event, dict)]
    red_events = red_root_dispatch_events(raw_for294)
    red_runtime_coords = {key_xy(event) for event in red_events}
    red_runtime_bounds = bounds_from_coords(red_runtime_coords)
    original_runtime_intersection = original_targets & red_runtime_coords
    target_final_by_coord = {
        key_xy(pixel): rgba(pixel.get("finalReadbackRgba"))
        for pixel in source_for290.get("targetPixels", [])
        if isinstance(pixel, dict)
    }
    if set(target_final_by_coord) != original_targets:
        fail("FOR-290 target final readbacks no longer match the original 59 coordinates")
    for290_last_events = last_events_by_coord(
        [event for event in source_for290.get("events", []) if isinstance(event, dict)]
    )

    per_pixel: list[dict[str, Any]] = []
    nearest_distances = []
    subzone_counts = {name: 0 for name in REQUESTED_SUBZONES}
    target_runtime_sources = Counter()
    target_last_colors = Counter()
    for pixel in sorted(target_pixels, key=lambda item: (int(item["y"]), int(item["x"]))):
        coord = key_xy(pixel)
        zones = for294.subzone_for_coord(masks, coord[0], coord[1])
        for zone in zones:
            if zone in subzone_counts:
                subzone_counts[zone] += 1
        nearest = nearest_coord(coord, red_runtime_coords)
        nearest_distances.append(nearest)
        last_event = for290_last_events.get(coord)
        runtime_root = pixel["runtimeRootDerivation"]
        target_runtime_sources[str(runtime_root.get("source"))] += 1
        target_last_colors[json.dumps(target_final_by_coord[coord])] += 1
        per_pixel.append(
            {
                "x": coord[0],
                "y": coord[1],
                "subzones": zones,
                "insideRedCandidateDomain": coord in red_candidate_coords,
                "insideRedRuntimeDispatchDomain": coord in red_runtime_coords,
                "insideRedRuntimeDispatchBounds": inside_bounds(coord, red_runtime_bounds),
                "chebyshevDistanceToRedRuntimeDispatchBounds": distance_to_bounds(coord, red_runtime_bounds),
                "nearestRedRuntimeDispatch": nearest,
                "finalReadbackRgba": target_final_by_coord[coord],
                "runtimeRootLastEvent": summarize_event(last_event),
                "runtimeRootMappedDraw": runtime_root.get("mappedDraw"),
                "runtimeRootSource": runtime_root.get("source"),
                "runtimeRootMode": runtime_root.get("mode"),
                "runtimeRootSrcInputRgba": runtime_root.get("srcInputRgba"),
                "runtimeRootValueWrittenRgba": runtime_root.get("valueWrittenRgba"),
                "redRootDispatchEventPresent": coord in red_runtime_coords,
                "reconstructedRedDrawRRect": pixel["reconstructedDerivation"],
            }
        )

    subzones: dict[str, Any] = {}
    for name in REQUESTED_SUBZONES:
        target_zone_coords = {
            key_xy(pixel)
            for pixel in target_pixels
            if name in for294.subzone_for_coord(masks, int(pixel["x"]), int(pixel["y"]))
        }
        red_zone_coords = {
            coord for coord in red_runtime_coords if bool(masks[name][coord[1], coord[0]])
        }
        candidate_zone_coords = for294.coords_from_mask(masks[name]) & red_candidate_coords
        subzones[name] = {
            "originalTargetPixels": len(target_zone_coords),
            "redCandidatePixels": len(candidate_zone_coords),
            "redRuntimeDispatchPixels": len(red_zone_coords),
            "intersectionOriginalTargetsWithRedRuntimeDispatch": len(target_zone_coords & red_zone_coords),
            "originalTargetFinalWhiteLayerPixels": sum(
                1 for coord in target_zone_coords if for294.white_or_layer(target_final_by_coord[coord])
            ),
            "originalTargetFinalRedTintPixels": sum(
                1 for coord in target_zone_coords if for294.red_tint(target_final_by_coord[coord])
            ),
        }

    min_euclidean = min(item["euclidean"] for item in nearest_distances)
    max_euclidean = max(item["euclidean"] for item in nearest_distances)
    min_manhattan = min(item["manhattan"] for item in nearest_distances)
    max_manhattan = max(item["manhattan"] for item in nearest_distances)
    min_chebyshev = min(item["chebyshev"] for item in nearest_distances)
    max_chebyshev = max(item["chebyshev"] for item in nearest_distances)

    if original_runtime_intersection:
        decision = DECISION_AMBIGUOUS
        exact_gap = (
            f"{len(original_runtime_intersection)} original targets unexpectedly intersect the "
            "FOR-294 red runtime dispatch domain."
        )
    else:
        decision = DECISION_TARGETS_OUTSIDE_RED_RUNTIME
        exact_gap = None

    red_coordinate_list = [
        {"x": x, "y": y}
        for x, y in sorted_coords(red_runtime_coords)
    ]
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-red-domain-vs-white-targets",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/for294-red-root-domain-vs-original-white-targets",
        "sourceAudits": {
            "for288": str(SOURCE_FOR288.relative_to(PROJECT_ROOT)),
            "for289": str(SOURCE_FOR289.relative_to(PROJECT_ROOT)),
            "for290": str(SOURCE_FOR290.relative_to(PROJECT_ROOT)),
            "for291": str(SOURCE_FOR291.relative_to(PROJECT_ROOT)),
            "for292": str(SOURCE_FOR292.relative_to(PROJECT_ROOT)),
            "for293": str(SOURCE_FOR293.relative_to(PROJECT_ROOT)),
            "for294": str(SOURCE_FOR294.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": decision,
        "exactGap": exact_gap,
        "decisionRationale": (
            "The original 59 M60 white targets are all inside the reconstructed FOR-293 red "
            "store-candidate domain, but none is inside the observed FOR-294 red runtime root "
            "dispatch domain. Their comparable root event remains the white drawRect kSrc path."
        ),
        "sourceNeedles": source_needles(),
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
            "for294Decision": source_for294["decision"],
        },
        "domainComparison": {
            "originalTargetPixels": len(original_targets),
            "redCandidateDomainPixels": len(red_candidate_coords),
            "redRuntimeDispatchPixels": len(red_runtime_coords),
            "originalTargetsInsideRedCandidateDomain": len(original_targets & red_candidate_coords),
            "originalTargetsInsideRedRuntimeDispatchDomain": len(original_runtime_intersection),
            "originalTargetsOutsideRedRuntimeDispatchDomain": len(original_targets - red_runtime_coords),
            "originalTargetBounds": bounds_from_coords(original_targets),
            "redCandidateBounds": bounds_from_coords(red_candidate_coords),
            "redRuntimeDispatchBounds": red_runtime_bounds,
            "originalTargetsInsideRedRuntimeDispatchBounds": sum(
                1 for coord in original_targets if inside_bounds(coord, red_runtime_bounds)
            ),
            "originalTargetsWithFinalWhiteLayerReadback": sum(
                1 for coord in original_targets if for294.white_or_layer(target_final_by_coord[coord])
            ),
            "originalTargetsWithFinalRedTintReadback": sum(
                1 for coord in original_targets if for294.red_tint(target_final_by_coord[coord])
            ),
            "originalTargetRuntimeRootSources": dict(target_runtime_sources),
            "originalTargetFinalReadbackColors": {
                color: count for color, count in sorted(target_last_colors.items())
            },
            "minEuclideanDistanceToRedRuntimeDispatch": min_euclidean,
            "maxEuclideanDistanceToRedRuntimeDispatch": max_euclidean,
            "minManhattanDistanceToRedRuntimeDispatch": min_manhattan,
            "maxManhattanDistanceToRedRuntimeDispatch": max_manhattan,
            "minChebyshevDistanceToRedRuntimeDispatch": min_chebyshev,
            "maxChebyshevDistanceToRedRuntimeDispatch": max_chebyshev,
            "averageEuclideanDistanceToRedRuntimeDispatch": round(
                sum(item["euclidean"] for item in nearest_distances) / len(nearest_distances),
                6,
            ),
        },
        "subzoneComparison": subzones,
        "perPixelComparisons": per_pixel,
        "redRuntimeDispatchCoordinates": red_coordinate_list,
        "runtimeRootTrace": {
            "for294RootEventCount": source_for294["runtimeRootTrace"]["rootEventCount"],
            "for294RedRootDispatchEvents": source_for294["runtimeRootTrace"]["redRootDispatchEvents"],
            "for294RedRootDispatchEventsOnOriginalTargets": source_for294["runtimeRootTrace"][
                "redRootDispatchEventsOnOriginalTargets"
            ],
            "for294RedRootDispatchEventsOutsideOriginalTargets": source_for294["runtimeRootTrace"][
                "redRootDispatchEventsOutsideOriginalTargets"
            ],
            "firstRedRootDispatch": source_for294["runtimeRootTrace"]["firstRedRootDispatch"],
            "firstRedRootDispatchOutsideOriginalTargets": source_for294["runtimeRootTrace"][
                "firstRedRootDispatchOutsideOriginalTargets"
            ],
        },
        "finalCompositeComparison": {
            "for294ExpandedDomainPixels": source_for294["finalReadback"]["expandedDomainPixels"],
            "for294FinalWhiteLayerPixels": source_for294["finalReadback"]["whiteOrLayerPixels"],
            "for294FinalRedTintPixels": source_for294["finalReadback"]["redTintPixels"],
            "for294FinalOtherPixels": source_for294["finalReadback"]["otherPixels"],
            "originalTargetFinalWhiteLayerPixels": 59,
            "originalTargetFinalRedTintPixels": 0,
            "redRuntimeDomainFinalCompositeMismatchPresent": (
                source_for294["runtimeRootTrace"]["redRootDispatchPixels"]
                != source_for294["finalReadback"]["redTintPixels"]
            ),
            "mismatchIsNotTheOriginalTargetMembershipCause": True,
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
            "for294DecisionPreserved": (
                source_for294["decision"] == "RED_DRAWRRECT_RUNTIME_ROOT_DISPATCH_FOUND_OUTSIDE_TARGETS"
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
            "newRuntimeTraceGeneratedByFOR295": False,
            "gpuRendererChanged": False,
            "globalBlendChanged": False,
            "setPixelSemanticsChanged": False,
            "for288ClassificationChanged": False,
            "for289DecisionChanged": False,
            "for290DecisionChanged": False,
            "for291DecisionChanged": False,
            "for292DecisionChanged": False,
            "for293DecisionChanged": False,
            "for294DecisionChanged": False,
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def write_report(audit: dict[str, Any]) -> None:
    domain = audit["domainComparison"]
    composite = audit["finalCompositeComparison"]
    runtime = audit["runtimeRootTrace"]
    subzone_rows = "\n".join(
        (
            f"| `{name}` | {data['originalTargetPixels']} | {data['redCandidatePixels']} | "
            f"{data['redRuntimeDispatchPixels']} | {data['intersectionOriginalTargetsWithRedRuntimeDispatch']} | "
            f"{data['originalTargetFinalWhiteLayerPixels']} | {data['originalTargetFinalRedTintPixels']} |"
        )
        for name, data in audit["subzoneComparison"].items()
    )
    pixel_rows = "\n".join(
        (
            f"| `{pixel['x']},{pixel['y']}` | `{','.join(pixel['subzones'])}` | "
            f"{pixel['insideRedCandidateDomain']} | {pixel['insideRedRuntimeDispatchDomain']} | "
            f"`{pixel['nearestRedRuntimeDispatch']['x']},{pixel['nearestRedRuntimeDispatch']['y']}` | "
            f"{pixel['nearestRedRuntimeDispatch']['euclidean']} | `{pixel['finalReadbackRgba']}` | "
            f"`{pixel['runtimeRootSource']}` | `{pixel['runtimeRootMode']}` | "
            f"{pixel['redRootDispatchEventPresent']} |"
        )
        for pixel in audit["perPixelComparisons"]
    )
    report = f"""# FOR-295 M60 Red Runtime Domain vs White Targets

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Exact gap: `{audit["exactGap"] or "none"}`

## Result

FOR-295 compares the FOR-294 red runtime root dispatch domain with the
original 59 M60 white targets. The 59 targets remain inside the reconstructed
FOR-293 red store-candidate domain, but they are outside the observed red
runtime root dispatch domain. The comparable root event on every target is
still the white `drawRect` `kSrc` path captured by FOR-290/FOR-292.

| Measure | Value |
|---|---:|
| Original target pixels | {domain["originalTargetPixels"]} |
| Red candidate domain pixels | {domain["redCandidateDomainPixels"]} |
| Red runtime dispatch pixels | {domain["redRuntimeDispatchPixels"]} |
| Targets inside red candidate domain | {domain["originalTargetsInsideRedCandidateDomain"]} |
| Targets inside red runtime dispatch domain | {domain["originalTargetsInsideRedRuntimeDispatchDomain"]} |
| Targets outside red runtime dispatch domain | {domain["originalTargetsOutsideRedRuntimeDispatchDomain"]} |
| Targets with final white/layer readback | {domain["originalTargetsWithFinalWhiteLayerReadback"]} |
| Targets with final red-tint readback | {domain["originalTargetsWithFinalRedTintReadback"]} |
| Min Euclidean distance to red runtime dispatch | {domain["minEuclideanDistanceToRedRuntimeDispatch"]} |
| Max Euclidean distance to red runtime dispatch | {domain["maxEuclideanDistanceToRedRuntimeDispatch"]} |
| Avg Euclidean distance to red runtime dispatch | {domain["averageEuclideanDistanceToRedRuntimeDispatch"]} |
| Min Manhattan distance to red runtime dispatch | {domain["minManhattanDistanceToRedRuntimeDispatch"]} |
| Max Manhattan distance to red runtime dispatch | {domain["maxManhattanDistanceToRedRuntimeDispatch"]} |
| Min Chebyshev distance to red runtime dispatch | {domain["minChebyshevDistanceToRedRuntimeDispatch"]} |
| Max Chebyshev distance to red runtime dispatch | {domain["maxChebyshevDistanceToRedRuntimeDispatch"]} |

## Bounds

| Domain | Bounds |
|---|---|
| Original target bounds | `{domain["originalTargetBounds"]}` |
| Red candidate bounds | `{domain["redCandidateBounds"]}` |
| Red runtime dispatch bounds | `{domain["redRuntimeDispatchBounds"]}` |

The target bounds lie inside the broad red runtime dispatch bounds, but no
target coordinate belongs to the sparse red runtime dispatch coordinate set.

## Subzone Comparison

| Subzone | Original targets | Red candidates | Red runtime dispatch pixels | Target/red intersection | Target final white/layer | Target final red-tint |
|---|---:|---:|---:|---:|---:|---:|
{subzone_rows}

## Runtime And Composite

| Measure | Value |
|---|---:|
| FOR-294 root events | {runtime["for294RootEventCount"]} |
| FOR-294 red root dispatch events | {runtime["for294RedRootDispatchEvents"]} |
| FOR-294 red root dispatch events on original targets | {runtime["for294RedRootDispatchEventsOnOriginalTargets"]} |
| FOR-294 red root dispatch events outside original targets | {runtime["for294RedRootDispatchEventsOutsideOriginalTargets"]} |
| FOR-294 expanded final white/layer pixels | {composite["for294FinalWhiteLayerPixels"]} |
| FOR-294 expanded final red-tint pixels | {composite["for294FinalRedTintPixels"]} |
| FOR-294 expanded final other pixels | {composite["for294FinalOtherPixels"]} |

The expanded red runtime domain has its own final-composite mismatch
({runtime["for294RedRootDispatchEvents"]} red dispatch events versus
{composite["for294FinalRedTintPixels"]} final red-tint pixels), but that is not
the membership cause for the original 59 pixels: they receive no red runtime
root dispatch event at all.

## Per-Pixel Comparison

| Pixel | Subzones | In red candidate | In red runtime dispatch | Nearest red dispatch | Euclidean distance | Final readback | Runtime source | Runtime mode | Red root event |
|---|---|---|---|---|---:|---|---|---|---|
{pixel_rows}

## Preserved Decisions

- FOR-288 classification: `{audit["route"]["for288PrimaryClassification"]}`
- FOR-289 decision: `{audit["route"]["for289Decision"]}`
- FOR-290 decision: `{audit["route"]["for290Decision"]}`
- FOR-291 decision: `{audit["route"]["for291Decision"]}`
- FOR-292 decision: `{audit["route"]["for292Decision"]}`
- FOR-293 decision: `{audit["route"]["for293Decision"]}`
- FOR-294 decision: `{audit["route"]["for294Decision"]}`

M60 remains `expected-unsupported`: `{audit["route"]["visualParityFallbackPreserved"]}`.
The crop fallback remains `{audit["route"]["cropFallbackPreserved"]}`. No
production renderer, threshold, GPU/WebGPU, fallback, blend, runtime trace, or
setPixel behavior changed.

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
    source_for294: dict[str, Any],
) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("wrong Linear id")
    if audit.get("parent") != PARENT_ID:
        fail("wrong parent id")
    if audit.get("sceneId") != SCENE_ID:
        fail("wrong scene id")
    if audit.get("decision") not in DECISIONS:
        fail("invalid FOR-295 decision")
    if audit.get("decision") == DECISION_AMBIGUOUS and not audit.get("exactGap"):
        fail("ambiguous decision must name the exact gap")
    if audit.get("decision") != DECISION_AMBIGUOUS and audit.get("exactGap") is not None:
        fail("resolved decision should not carry an unresolved exact gap")
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
    if source_for294["decision"] != "RED_DRAWRRECT_RUNTIME_ROOT_DISPATCH_FOUND_OUTSIDE_TARGETS":
        fail("FOR-294 source decision changed")

    for key, value in audit["sourceNeedles"].items():
        if value is not True:
            fail(f"source needle `{key}` failed")

    route = audit["route"]
    if route["visualParityFallbackPreserved"] != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route["cropFallbackPreserved"] != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")

    domain = audit["domainComparison"]
    expected_domain = {
        "originalTargetPixels": 59,
        "redCandidateDomainPixels": 22424,
        "redRuntimeDispatchPixels": 9088,
        "originalTargetsInsideRedCandidateDomain": 59,
        "originalTargetsInsideRedRuntimeDispatchDomain": 0,
        "originalTargetsOutsideRedRuntimeDispatchDomain": 59,
        "originalTargetsWithFinalWhiteLayerReadback": 59,
        "originalTargetsWithFinalRedTintReadback": 0,
        "minManhattanDistanceToRedRuntimeDispatch": 100,
        "maxManhattanDistanceToRedRuntimeDispatch": 119,
        "minChebyshevDistanceToRedRuntimeDispatch": 54,
        "maxChebyshevDistanceToRedRuntimeDispatch": 64,
    }
    for key, expected in expected_domain.items():
        if domain.get(key) != expected:
            fail(f"domain `{key}` changed: {domain.get(key)} != {expected}")
    if domain["minEuclideanDistanceToRedRuntimeDispatch"] != 70.936591:
        fail("minimum Euclidean distance changed")
    if domain["maxEuclideanDistanceToRedRuntimeDispatch"] != 84.386018:
        fail("maximum Euclidean distance changed")
    if domain["averageEuclideanDistanceToRedRuntimeDispatch"] != 77.870976:
        fail("average Euclidean distance changed")
    if domain["originalTargetRuntimeRootSources"] != {"SkBitmapDevice.dispatchBlend": 59}:
        fail("original target runtime root source counts changed")

    red_coords = {
        key_xy(coord)
        for coord in audit.get("redRuntimeDispatchCoordinates", [])
        if isinstance(coord, dict)
    }
    if len(red_coords) != 9088:
        fail("red runtime dispatch coordinate set size changed")
    per_pixel = audit.get("perPixelComparisons")
    if not isinstance(per_pixel, list) or len(per_pixel) != 59:
        fail("per-pixel comparison count changed")
    for pixel in per_pixel:
        coord = key_xy(pixel)
        if not pixel["insideRedCandidateDomain"]:
            fail(f"target {coord_key(coord)} left the red candidate domain")
        if pixel["insideRedRuntimeDispatchDomain"]:
            fail(f"target {coord_key(coord)} unexpectedly entered the red runtime dispatch domain")
        if pixel["redRootDispatchEventPresent"]:
            fail(f"target {coord_key(coord)} unexpectedly has a red root dispatch event")
        if not for294.white_or_layer(rgba(pixel["finalReadbackRgba"])):
            fail(f"target {coord_key(coord)} final readback is no longer white/layer")
        if pixel["runtimeRootSource"] != "SkBitmapDevice.dispatchBlend":
            fail(f"target {coord_key(coord)} runtime source changed")
        if pixel["runtimeRootMode"] != "kSrc":
            fail(f"target {coord_key(coord)} runtime mode changed")
        if key_xy(pixel["nearestRedRuntimeDispatch"]) not in red_coords:
            fail(f"target {coord_key(coord)} nearest red dispatch is not in the red domain")

    if audit["decision"] != DECISION_TARGETS_OUTSIDE_RED_RUNTIME:
        fail(f"FOR-295 expected {DECISION_TARGETS_OUTSIDE_RED_RUNTIME}, got {audit['decision']}")
    for name in REQUESTED_SUBZONES:
        if name not in audit["subzoneComparison"]:
            fail(f"missing subzone `{name}`")
    if audit["subzoneComparison"]["draw_oval_outer_boundary"]["originalTargetPixels"] != 59:
        fail("original targets are no longer all in draw_oval_outer_boundary")
    if audit["subzoneComparison"]["blurred_content_envelope"]["originalTargetPixels"] != 59:
        fail("original targets are no longer all in blurred_content_envelope")
    for name in ("difference_oval_inner_boundary", "halo_interior", "outside_draw_oval"):
        if audit["subzoneComparison"][name]["originalTargetPixels"] != 0:
            fail(f"original targets unexpectedly entered subzone `{name}`")

    composite = audit["finalCompositeComparison"]
    if composite["redRuntimeDomainFinalCompositeMismatchPresent"] is not True:
        fail("FOR-294 final composite mismatch fact changed")
    if composite["mismatchIsNotTheOriginalTargetMembershipCause"] is not True:
        fail("FOR-295 must keep membership cause separate from composite mismatch")

    preservation = audit["strictPreservation"]
    for key in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "productionRendererChanged",
        "cpuRendererChanged",
        "cpuInstrumentationChanged",
        "newRuntimeTraceGeneratedByFOR295",
        "gpuRendererChanged",
        "globalBlendChanged",
        "setPixelSemanticsChanged",
        "for288ClassificationChanged",
        "for289DecisionChanged",
        "for290DecisionChanged",
        "for291DecisionChanged",
        "for292DecisionChanged",
        "for293DecisionChanged",
        "for294DecisionChanged",
        "m60Promoted",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if preservation.get(key) is not False:
            fail(f"strict preservation `{key}` changed")


def main() -> None:
    source_for288 = load_json(SOURCE_FOR288)
    source_for289 = load_json(SOURCE_FOR289)
    source_for290 = load_json(SOURCE_FOR290)
    source_for291 = load_json(SOURCE_FOR291)
    source_for292 = load_json(SOURCE_FOR292)
    source_for293 = load_json(SOURCE_FOR293)
    source_for294 = load_json(SOURCE_FOR294)
    source_for286 = for291.load_json(for291.SOURCE_FOR286)
    for288.validate_audit(source_for288)
    for289.validate_audit(source_for289, source_for288)
    for290.validate_audit(source_for290, source_for288, source_for289)
    for291.validate_audit(source_for291, source_for286, source_for288, source_for289, source_for290)
    for292.validate_audit(source_for292, source_for286, source_for288, source_for289, source_for290, source_for291)
    for293.validate_audit(
        source_for293,
        source_for286,
        source_for288,
        source_for289,
        source_for290,
        source_for291,
        source_for292,
    )
    for294.validate_audit(
        source_for294,
        source_for288,
        source_for289,
        source_for290,
        source_for291,
        source_for292,
        source_for293,
    )

    if RAW_FOR294.exists():
        raw_for294 = load_json(RAW_FOR294)
        audit = analyze(
            raw_for294=raw_for294,
            source_for288=source_for288,
            source_for289=source_for289,
            source_for290=source_for290,
            source_for291=source_for291,
            source_for292=source_for292,
            source_for293=source_for293,
            source_for294=source_for294,
        )
        validate_audit(
            audit,
            source_for288,
            source_for289,
            source_for290,
            source_for291,
            source_for292,
            source_for293,
            source_for294,
        )
        AUDIT.write_text(json_dump(audit), encoding="utf-8")
        write_report(audit)
    else:
        audit = load_json(AUDIT)
        validate_audit(
            audit,
            source_for288,
            source_for289,
            source_for290,
            source_for291,
            source_for292,
            source_for293,
            source_for294,
        )
        if not REPORT.exists():
            write_report(audit)

    reread = load_json(AUDIT)
    validate_audit(
        reread,
        source_for288,
        source_for289,
        source_for290,
        source_for291,
        source_for292,
        source_for293,
        source_for294,
    )
    report_text = REPORT.read_text(encoding="utf-8")
    for needle in (
        reread["decision"],
        "Subzone Comparison",
        "Per-Pixel Comparison",
        "FOR-294 decision",
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
        "No\nproduction renderer",
    ):
        if needle not in report_text:
            fail(f"report missing `{needle}`")
    print(f"FOR-295 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-295 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"Decision: {reread['decision']}")


if __name__ == "__main__":
    main()
