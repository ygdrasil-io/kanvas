#!/usr/bin/env python3
"""Generate and validate FOR-297 M60 candidate hole overbreadth evidence."""

from __future__ import annotations

import json
import math
import sys
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for288_m60_outer_boundary_store_order_audit as for288
import validate_for289_m60_outer_boundary_runtime_write_chronology as for289
import validate_for290_m60_expanded_runtime_write_trace as for290
import validate_for291_m60_reconstructed_store_model_audit as for291
import validate_for292_m60_source_payload_derivation_audit as for292
import validate_for293_m60_red_drawrrect_runtime_visibility_audit as for293
import validate_for294_m60_expanded_red_drawrrect_runtime_trace as for294
import validate_for295_m60_red_domain_vs_white_targets as for295
import validate_for296_m60_red_runtime_spatial_separation as for296


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-297"
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
SOURCE_FOR295 = for295.AUDIT
SOURCE_FOR296 = for296.AUDIT
RAW_FOR294 = for294.RAW_AUDIT
AUDIT_NAME = "m60-candidate-hole-overbreadth-for297.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-297-m60-candidate-hole-overbreadth.md"

DECISION_MASK_CLIP_MODEL = "RECONSTRUCTED_RED_CANDIDATE_OVERBROAD_FROM_MASK_CLIP_MODEL"
DECISION_SUBZONE_SELECTION = "RECONSTRUCTED_RED_CANDIDATE_OVERBROAD_FROM_SUBZONE_SELECTION"
DECISION_UNMODELED_FILTER = "RUNTIME_RED_DISPATCH_REQUIRES_ADDITIONAL_UNMODELED_FILTER"
DECISION_AMBIGUOUS = "CANDIDATE_OVERBREADTH_CAUSE_STILL_AMBIGUOUS"
DECISIONS = {
    DECISION_MASK_CLIP_MODEL,
    DECISION_SUBZONE_SELECTION,
    DECISION_UNMODELED_FILTER,
    DECISION_AMBIGUOUS,
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-297 validation failed: {message}")


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


def sorted_coords(coords: Iterable[tuple[int, int]]) -> list[tuple[int, int]]:
    return sorted(coords, key=lambda item: (item[1], item[0]))


def connected_components(coords: set[tuple[int, int]], label: str) -> list[dict[str, Any]]:
    remaining = set(coords)
    components: list[set[tuple[int, int]]] = []
    while remaining:
        start = min(remaining, key=lambda item: (item[1], item[0]))
        stack = [start]
        remaining.remove(start)
        component: set[tuple[int, int]] = set()
        while stack:
            x, y = stack.pop()
            component.add((x, y))
            for neighbor in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if neighbor in remaining:
                    remaining.remove(neighbor)
                    stack.append(neighbor)
        components.append(component)
    ordered = sorted(
        components,
        key=lambda component: (
            -len(component),
            bounds_from_coords(component)["top"],  # type: ignore[index]
            bounds_from_coords(component)["left"],  # type: ignore[index]
        ),
    )
    return [
        {
            "id": f"{label}-{index:03d}",
            "pixels": len(component),
            "bounds": bounds_from_coords(component),
            "sampleCoordinates": [{"x": x, "y": y} for x, y in sorted_coords(component)[:8]],
            "_coords": component,
        }
        for index, component in enumerate(ordered)
    ]


def public_component(component: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in component.items() if key != "_coords"}


def nearest_coord(coord: tuple[int, int], candidates: set[tuple[int, int]]) -> dict[str, Any]:
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


def distance_stats(coords: set[tuple[int, int]], candidates: set[tuple[int, int]]) -> dict[str, Any] | None:
    if not coords:
        return None
    nearest = [nearest_coord(coord, candidates) for coord in sorted_coords(coords)]
    return {
        "pixels": len(coords),
        "minEuclidean": min(item["euclidean"] for item in nearest),
        "maxEuclidean": max(item["euclidean"] for item in nearest),
        "averageEuclidean": round(sum(item["euclidean"] for item in nearest) / len(nearest), 6),
        "minManhattan": min(item["manhattan"] for item in nearest),
        "maxManhattan": max(item["manhattan"] for item in nearest),
        "minChebyshev": min(item["chebyshev"] for item in nearest),
        "maxChebyshev": max(item["chebyshev"] for item in nearest),
        "nearestPixel": min(
            (
                {
                    "x": coord[0],
                    "y": coord[1],
                    "nearest": nearest_coord(coord, candidates),
                }
                for coord in coords
            ),
            key=lambda item: (item["nearest"]["euclidean"], item["y"], item["x"]),
        ),
    }


def classify_final(value: list[int]) -> str:
    if for294.white_or_layer(value):
        return "finalWhiteLayer"
    if for294.red_tint(value):
        return "finalRedTint"
    return "finalOther"


def value_stats(values: list[int]) -> dict[str, Any]:
    if not values:
        return {
            "pixels": 0,
            "min": None,
            "max": None,
            "average": None,
            "zeroPixels": 0,
            "nonZeroPixels": 0,
            "fullPixels": 0,
            "topValues": [],
        }
    counts = Counter(values)
    return {
        "pixels": len(values),
        "min": min(values),
        "max": max(values),
        "average": round(sum(values) / len(values), 6),
        "zeroPixels": counts.get(0, 0),
        "nonZeroPixels": len(values) - counts.get(0, 0),
        "fullPixels": counts.get(255, 0),
        "topValues": [{"value": value, "pixels": count} for value, count in counts.most_common(8)],
    }


def mask_clip_alpha_stats(
    coords: set[tuple[int, int]],
    mask_alpha: Any,
    clip_coverage: Any,
    alpha_after_clip: Any,
) -> dict[str, Any]:
    mask_values = [int(mask_alpha[y, x]) for x, y in coords]
    clip_values = [int(clip_coverage[y, x]) for x, y in coords]
    alpha_values = [int(alpha_after_clip[y, x]) for x, y in coords]
    return {
        "maskAlpha": value_stats(mask_values),
        "clipCoverage": value_stats(clip_values),
        "alphaAfterClip": value_stats(alpha_values),
        "allPixelsPassFor293CandidatePredicate": all(
            mask > 0 and clip > 0 and alpha > 0
            for mask, clip, alpha in zip(mask_values, clip_values, alpha_values)
        ),
    }


def subzone_counts(coords: set[tuple[int, int]], masks: dict[str, Any]) -> dict[str, int]:
    return {
        name: sum(1 for x, y in coords if bool(masks[name][y, x]))
        for name in for294.REQUESTED_SUBZONES
    }


def read_for294_final_pixels(raw_for294: dict[str, Any]) -> dict[tuple[int, int], list[int]]:
    target_pixels = raw_for294.get("targetPixels")
    if not isinstance(target_pixels, list):
        fail("raw FOR-294 audit missing targetPixels[]")
    result = {key_xy(pixel): rgba(pixel.get("finalReadbackRgba")) for pixel in target_pixels if isinstance(pixel, dict)}
    if len(result) != 22424:
        fail(f"FOR-294 raw final target pixel count changed: {len(result)}")
    return result


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


def event_stats(coords: set[tuple[int, int]], events: list[dict[str, Any]]) -> dict[str, Any]:
    selected = [event for event in events if key_xy(event) in coords and bool(event.get("rootDevice"))]
    red_events = [event for event in selected if str(event.get("source")) == for294.RED_SOURCE]
    return {
        "rootEventCount": len(selected),
        "rootSourceCounts": dict(Counter(str(event.get("source")) for event in selected)),
        "rootModeCounts": dict(Counter(str(event.get("mode")) for event in selected)),
        "redRuntimeDispatchEventCount": len(red_events),
        "redRuntimeDispatchPixelCount": len({key_xy(event) for event in red_events}),
        "firstRootEvent": summarize_event(min(selected, key=lambda event: int(event["index"])) if selected else None),
        "lastRootEvent": summarize_event(max(selected, key=lambda event: int(event["index"])) if selected else None),
        "firstRedRuntimeDispatch": summarize_event(
            min(red_events, key=lambda event: int(event["index"])) if red_events else None
        ),
    }


def final_readback_stats(coords: set[tuple[int, int]], final_by_coord: dict[tuple[int, int], list[int]]) -> dict[str, Any]:
    counts = Counter(classify_final(final_by_coord[coord]) for coord in coords)
    colors = Counter(json.dumps(final_by_coord[coord]) for coord in coords)
    return {
        "finalWhiteLayerPixels": counts.get("finalWhiteLayer", 0),
        "finalRedTintPixels": counts.get("finalRedTint", 0),
        "finalOtherPixels": counts.get("finalOther", 0),
        "topFinalReadbackColors": [{"rgba": json.loads(color), "pixels": count} for color, count in colors.most_common(8)],
    }


def component_deep_summary(
    component: dict[str, Any],
    *,
    original_targets: set[tuple[int, int]],
    final_by_coord: dict[tuple[int, int], list[int]],
    mask_alpha: Any,
    clip_coverage: Any,
    alpha_after_clip: Any,
    masks: dict[str, Any],
    events: list[dict[str, Any]],
) -> dict[str, Any]:
    coords = component["_coords"]
    return {
        **public_component(component),
        "originalTargetPixels": len(coords & original_targets),
        "maskClipAlphaStats": mask_clip_alpha_stats(coords, mask_alpha, clip_coverage, alpha_after_clip),
        "subzonePixels": subzone_counts(coords, masks),
        "finalReadback": final_readback_stats(coords, final_by_coord),
        "runtimeEvents": event_stats(coords, events),
    }


def nearest_runtime_components(
    coords: set[tuple[int, int]],
    runtime_components: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    rows = []
    for component in runtime_components:
        component_coords = component["_coords"]
        stats = distance_stats(coords, component_coords)
        rows.append(
            {
                "componentId": component["id"],
                "pixels": component["pixels"],
                "bounds": component["bounds"],
                "distanceFromOriginalTargetCluster": stats,
            }
        )
    return sorted(rows, key=lambda item: item["distanceFromOriginalTargetCluster"]["minEuclidean"])


def cluster_neighborhood(
    targets: set[tuple[int, int]],
    *,
    red_candidate_coords: set[tuple[int, int]],
    red_runtime_coords: set[tuple[int, int]],
    target_hole_coords: set[tuple[int, int]],
    final_white: set[tuple[int, int]],
    final_red: set[tuple[int, int]],
) -> list[dict[str, Any]]:
    rows = []
    for radius in (0, 4, 8, 16, 32, 64, 72):
        coords = {
            (x, y)
            for target_x, target_y in targets
            for x in range(target_x - radius, target_x + radius + 1)
            for y in range(target_y - radius, target_y + radius + 1)
        }
        rows.append(
            {
                "chebyshevRadius": radius,
                "sampledPixels": len(coords),
                "redCandidatePixels": len(coords & red_candidate_coords),
                "redRuntimeDispatchPixels": len(coords & red_runtime_coords),
                "candidateMinusRuntimeTargetHolePixels": len(coords & target_hole_coords),
                "finalWhiteLayerPixels": len(coords & final_white),
                "finalRedTintPixels": len(coords & final_red),
            }
        )
    return rows


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
    source_for295: dict[str, Any],
    source_for296: dict[str, Any],
) -> dict[str, Any]:
    reference = for269.load_image("skia")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA reference image")
    geometry, masks = for294.coordinate_masks(width, height)[:2]
    mask_alpha, clip_coverage, alpha_after_clip, visible_candidate, mask_bounds = for293.red_draw_runtime_domain(
        width,
        height,
        geometry,
    )
    red_candidate_coords = for294.coords_from_mask(visible_candidate)
    if len(red_candidate_coords) != 22424:
        fail(f"red candidate domain size changed: {len(red_candidate_coords)}")

    original_targets = {key_xy(pixel) for pixel in source_for292.get("perPixelComparisons", [])}
    if len(original_targets) != 59:
        fail("FOR-292 perPixelComparisons must contain the original 59 targets")
    red_runtime_coords = {
        key_xy(coord)
        for coord in source_for295.get("redRuntimeDispatchCoordinates", [])
        if isinstance(coord, dict)
    }
    if len(red_runtime_coords) != 9088:
        fail(f"red runtime dispatch coordinate count changed: {len(red_runtime_coords)}")
    final_by_coord = read_for294_final_pixels(raw_for294)
    events = [event for event in raw_for294.get("events", []) if isinstance(event, dict)]
    if len(events) != int(raw_for294.get("eventCount", len(events))):
        fail("raw FOR-294 event count changed")

    candidate_minus_runtime = red_candidate_coords - red_runtime_coords
    runtime_outside_candidate = red_runtime_coords - red_candidate_coords
    candidate_components = connected_components(candidate_minus_runtime, "candidate-minus-runtime")
    runtime_components = connected_components(red_runtime_coords, "red-runtime")
    target_hole = next((component for component in candidate_components if component["_coords"] & original_targets), None)
    if target_hole is None:
        decision = DECISION_AMBIGUOUS
        exact_gap = "No candidate-minus-runtime component contains the original 59 targets."
        target_hole_coords: set[tuple[int, int]] = set()
    else:
        target_hole_coords = target_hole["_coords"]
        target_hole_mask = mask_clip_alpha_stats(target_hole_coords, mask_alpha, clip_coverage, alpha_after_clip)
        target_hole_subzones = subzone_counts(target_hole_coords, masks)
        runtime_subzones = subzone_counts(red_runtime_coords, masks)
        target_subzones = subzone_counts(original_targets, masks)
        if len(original_targets & red_runtime_coords) != 0:
            decision = DECISION_AMBIGUOUS
            exact_gap = "Original targets unexpectedly intersect the red runtime dispatch domain."
        elif not target_hole_mask["allPixelsPassFor293CandidatePredicate"]:
            decision = DECISION_MASK_CLIP_MODEL
            exact_gap = None
        elif all(runtime_subzones.get(name, 0) == 0 for name, pixels in target_subzones.items() if pixels > 0):
            decision = DECISION_SUBZONE_SELECTION
            exact_gap = None
        else:
            decision = DECISION_UNMODELED_FILTER
            exact_gap = None

    final_white = {coord for coord, value in final_by_coord.items() if for294.white_or_layer(value)}
    final_red = {coord for coord, value in final_by_coord.items() if for294.red_tint(value)}
    final_other = set(final_by_coord) - final_white - final_red

    target_hole_summary = (
        component_deep_summary(
            target_hole,
            original_targets=original_targets,
            final_by_coord=final_by_coord,
            mask_alpha=mask_alpha,
            clip_coverage=clip_coverage,
            alpha_after_clip=alpha_after_clip,
            masks=masks,
            events=events,
        )
        if target_hole is not None
        else None
    )
    runtime_summaries = [
        component_deep_summary(
            component,
            original_targets=original_targets,
            final_by_coord=final_by_coord,
            mask_alpha=mask_alpha,
            clip_coverage=clip_coverage,
            alpha_after_clip=alpha_after_clip,
            masks=masks,
            events=events,
        )
        for component in runtime_components
    ]

    original_target_payloads = [
        {
            "x": int(pixel["x"]),
            "y": int(pixel["y"]),
            "reconstructed": pixel["reconstructedDerivation"],
            "runtimeRoot": pixel["runtimeRootDerivation"],
            "finalReadbackRgba": final_by_coord[key_xy(pixel)],
            "nearestRedRuntimeDispatch": nearest_coord(key_xy(pixel), red_runtime_coords),
        }
        for pixel in sorted(source_for292.get("perPixelComparisons", []), key=lambda item: (int(item["y"]), int(item["x"])))
    ]

    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-candidate-hole-overbreadth",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/for294-for296-derived-diagnostic",
        "sourceAudits": {
            "for288": str(SOURCE_FOR288.relative_to(PROJECT_ROOT)),
            "for289": str(SOURCE_FOR289.relative_to(PROJECT_ROOT)),
            "for290": str(SOURCE_FOR290.relative_to(PROJECT_ROOT)),
            "for291": str(SOURCE_FOR291.relative_to(PROJECT_ROOT)),
            "for292": str(SOURCE_FOR292.relative_to(PROJECT_ROOT)),
            "for293": str(SOURCE_FOR293.relative_to(PROJECT_ROOT)),
            "for294": str(SOURCE_FOR294.relative_to(PROJECT_ROOT)),
            "for295": str(SOURCE_FOR295.relative_to(PROJECT_ROOT)),
            "for296": str(SOURCE_FOR296.relative_to(PROJECT_ROOT)),
            "for294RawFinalReadback": str(RAW_FOR294.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": decision,
        "exactGap": exact_gap,
        "decisionRationale": (
            "candidate-minus-runtime-002 passes the same reconstructed FOR-293 mask/clip predicate "
            "as the red candidate domain and has nonzero mask alpha, clip coverage, and alpha-after-clip "
            "for every pixel. Its subzones are not unique: the original 59 targets are in "
            "draw_oval_outer_boundary, and FOR-294 observed 5548 red runtime dispatch pixels in that "
            "same subzone. The observed runtime red dispatch therefore requires an additional runtime "
            "write-eligibility filter that is not represented by the current mask/clip/subzone "
            "reconstruction artifacts."
        ),
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
            "for295Decision": source_for295["decision"],
            "for296Decision": source_for296["decision"],
        },
        "reconstructedModel": {
            "drawOvalDeviceBounds": geometry["drawOvalDeviceBounds"],
            "differenceOvalDeviceBounds": geometry["differenceOvalDeviceBounds"],
            "maskFilterBounds": mask_bounds,
            "candidatePredicate": "mask_alpha > 0 && clip_coverage > 0 && alpha_after_clip > 0",
            "candidatePixels": len(red_candidate_coords),
            "runtimeRedDispatchPixels": len(red_runtime_coords),
            "candidateMinusRuntimePixels": len(candidate_minus_runtime),
            "runtimeOutsideCandidatePixels": len(runtime_outside_candidate),
            "candidateBounds": bounds_from_coords(red_candidate_coords),
            "runtimeRedDispatchBounds": bounds_from_coords(red_runtime_coords),
            "candidateMinusRuntimeBounds": bounds_from_coords(candidate_minus_runtime),
        },
        "targetCluster": {
            "pixels": len(original_targets),
            "bounds": bounds_from_coords(original_targets),
            "insideRedCandidateDomain": len(original_targets & red_candidate_coords),
            "insideRedRuntimeDispatchDomain": len(original_targets & red_runtime_coords),
            "insideTargetHoleComponent": len(original_targets & target_hole_coords),
            "finalReadback": final_readback_stats(original_targets, final_by_coord),
            "maskClipAlphaStats": mask_clip_alpha_stats(original_targets, mask_alpha, clip_coverage, alpha_after_clip),
            "subzonePixels": subzone_counts(original_targets, masks),
            "distanceToAllRedRuntimeDispatch": distance_stats(original_targets, red_runtime_coords),
            "distanceToRedRuntimeComponents": nearest_runtime_components(original_targets, runtime_components),
            "neighborhoodByChebyshevRadius": cluster_neighborhood(
                original_targets,
                red_candidate_coords=red_candidate_coords,
                red_runtime_coords=red_runtime_coords,
                target_hole_coords=target_hole_coords,
                final_white=final_white,
                final_red=final_red,
            ),
            "payloadSourceComparisonSample": original_target_payloads[:12],
        },
        "componentComparison": {
            "candidateMinusRuntimeComponentCount": len(candidate_components),
            "targetHoleComponent": target_hole_summary,
            "redRuntimeComponentCount": len(runtime_components),
            "redRuntimeComponents": runtime_summaries,
            "largestCandidateMinusRuntimeComponents": [
                component_deep_summary(
                    component,
                    original_targets=original_targets,
                    final_by_coord=final_by_coord,
                    mask_alpha=mask_alpha,
                    clip_coverage=clip_coverage,
                    alpha_after_clip=alpha_after_clip,
                    masks=masks,
                    events=events,
                )
                for component in candidate_components[:4]
            ],
        },
        "readbackSummary": {
            "candidateDomain": final_readback_stats(red_candidate_coords, final_by_coord),
            "candidateMinusRuntime": final_readback_stats(candidate_minus_runtime, final_by_coord),
            "redRuntimeDispatch": final_readback_stats(red_runtime_coords, final_by_coord),
            "finalWhiteLayerPixels": len(final_white),
            "finalRedTintPixels": len(final_red),
            "finalOtherPixels": len(final_other),
        },
        "interpretation": {
            "candidateHolePassesMaskClipModel": (
                target_hole_summary is not None
                and target_hole_summary["maskClipAlphaStats"]["allPixelsPassFor293CandidatePredicate"]
            ),
            "candidateHoleIsExplainedBySubzoneSelection": False,
            "candidateHoleHasRedRuntimeDispatch": (
                target_hole_summary is not None
                and target_hole_summary["runtimeEvents"]["redRuntimeDispatchPixelCount"] > 0
            ),
            "sameOriginalTargetSubzoneHasRuntimeDispatchElsewhere": (
                subzone_counts(original_targets, masks)["draw_oval_outer_boundary"] == 59
                and subzone_counts(red_runtime_coords, masks)["draw_oval_outer_boundary"] > 0
            ),
            "runtimeDispatchRequiresUnmodeledFilter": decision == DECISION_UNMODELED_FILTER,
            "unmodeledFilterGap": (
                "The current artifacts do not expose the per-pixel runtime criterion between "
                "FOR-293 alpha_after_clip > 0 and FOR-294 A8 srcInPayload root dispatch membership."
            ),
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
            "for295DecisionPreserved": (
                source_for295["decision"] == "ORIGINAL_TARGETS_OUTSIDE_RED_RUNTIME_DISPATCH_DOMAIN"
            ),
            "for296DecisionPreserved": (
                source_for296["decision"]
                == "RED_RUNTIME_DISPATCH_DOMAIN_SPATIALLY_SEPARATE_FROM_ORIGINAL_TARGET_CLUSTER"
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
            "newRuntimeTraceGeneratedByFOR297": False,
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
            "for295DecisionChanged": False,
            "for296DecisionChanged": False,
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def metric_row(label: str, stats: dict[str, Any]) -> str:
    return (
        f"| {label} | {stats['pixels']} | {stats['min']} | {stats['max']} | "
        f"{stats['average']} | {stats['zeroPixels']} | {stats['nonZeroPixels']} | {stats['fullPixels']} |"
    )


def component_rows(components: list[dict[str, Any]]) -> str:
    return "\n".join(
        (
            f"| `{component['id']}` | {component['pixels']} | `{component['bounds']}` | "
            f"{component['originalTargetPixels']} | "
            f"{component['maskClipAlphaStats']['maskAlpha']['average']} | "
            f"{component['maskClipAlphaStats']['clipCoverage']['average']} | "
            f"{component['maskClipAlphaStats']['alphaAfterClip']['average']} | "
            f"{component['finalReadback']['finalWhiteLayerPixels']} | "
            f"{component['finalReadback']['finalRedTintPixels']} | "
            f"{component['runtimeEvents']['redRuntimeDispatchPixelCount']} |"
        )
        for component in components
    )


def write_report(audit: dict[str, Any]) -> None:
    model = audit["reconstructedModel"]
    cluster = audit["targetCluster"]
    target_hole = audit["componentComparison"]["targetHoleComponent"]
    if target_hole is None:
        target_hole = {}
    hole_stats = target_hole["maskClipAlphaStats"]
    target_stats = cluster["maskClipAlphaStats"]
    nearest = cluster["distanceToAllRedRuntimeDispatch"]
    neighborhood_rows = "\n".join(
        (
            f"| {row['chebyshevRadius']} | {row['sampledPixels']} | {row['redCandidatePixels']} | "
            f"{row['redRuntimeDispatchPixels']} | {row['candidateMinusRuntimeTargetHolePixels']} | "
            f"{row['finalWhiteLayerPixels']} | {row['finalRedTintPixels']} |"
        )
        for row in cluster["neighborhoodByChebyshevRadius"]
    )
    runtime_distance_rows = "\n".join(
        (
            f"| `{row['componentId']}` | `{row['bounds']}` | "
            f"{row['distanceFromOriginalTargetCluster']['minEuclidean']} | "
            f"{row['distanceFromOriginalTargetCluster']['averageEuclidean']} | "
            f"{row['distanceFromOriginalTargetCluster']['maxEuclidean']} | "
            f"`{row['distanceFromOriginalTargetCluster']['nearestPixel']['x']},"
            f"{row['distanceFromOriginalTargetCluster']['nearestPixel']['y']}` -> "
            f"`{row['distanceFromOriginalTargetCluster']['nearestPixel']['nearest']['x']},"
            f"{row['distanceFromOriginalTargetCluster']['nearestPixel']['nearest']['y']}` |"
        )
        for row in cluster["distanceToRedRuntimeComponents"]
    )
    subzone_rows = "\n".join(
        (
            f"| `{name}` | {cluster['subzonePixels'][name]} | "
            f"{target_hole['subzonePixels'][name]} | "
            f"{sum(component['subzonePixels'][name] for component in audit['componentComparison']['redRuntimeComponents'])} |"
        )
        for name in for294.REQUESTED_SUBZONES
    )
    report = f"""# FOR-297 M60 Candidate Hole Overbreadth

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Exact gap: `{audit["exactGap"] or "none"}`

## Result

FOR-297 compares `candidate-minus-runtime-002` against the FOR-294 red
runtime components without changing rendering. The candidate hole is not an
alpha-zero or clip-zero region: every pixel in the component passes the
FOR-293 reconstructed predicate
`mask_alpha > 0 && clip_coverage > 0 && alpha_after_clip > 0`.

The current evidence rejects subzone selection as the cause. The original 59
targets are in `draw_oval_outer_boundary`; FOR-294 also observed 5,548 red
runtime dispatch pixels in that same subzone. The remaining explanation is an
additional runtime write-eligibility filter between the reconstructed
mask/clip model and the observed A8 `srcInPayload` root dispatch membership.

| Measure | Value |
|---|---:|
| Red candidate domain pixels | {model["candidatePixels"]} |
| Red runtime dispatch pixels | {model["runtimeRedDispatchPixels"]} |
| Candidate-minus-runtime pixels | {model["candidateMinusRuntimePixels"]} |
| Runtime pixels outside candidate | {model["runtimeOutsideCandidatePixels"]} |
| Original target pixels | {cluster["pixels"]} |
| Targets inside red candidate | {cluster["insideRedCandidateDomain"]} |
| Targets inside red runtime dispatch | {cluster["insideRedRuntimeDispatchDomain"]} |
| Targets inside `candidate-minus-runtime-002` | {cluster["insideTargetHoleComponent"]} |
| Target min distance to red runtime dispatch | {nearest["minEuclidean"]} |
| Target avg distance to red runtime dispatch | {nearest["averageEuclidean"]} |
| Target max distance to red runtime dispatch | {nearest["maxEuclidean"]} |

## Component Comparison

| Component | Pixels | Bounds | Original targets | Avg mask | Avg clip | Avg alpha-after-clip | Final white/layer | Final red-tint | Red runtime pixels |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|
{component_rows([target_hole])}
{component_rows(audit["componentComparison"]["redRuntimeComponents"])}

## Mask, Clip, Alpha

`candidate-minus-runtime-002`:

| Metric | Pixels | Min | Max | Average | Zero | Non-zero | Full |
|---|---:|---:|---:|---:|---:|---:|---:|
{metric_row("Mask alpha", hole_stats["maskAlpha"])}
{metric_row("Clip coverage", hole_stats["clipCoverage"])}
{metric_row("Alpha after clip", hole_stats["alphaAfterClip"])}

Original 59 target cluster:

| Metric | Pixels | Min | Max | Average | Zero | Non-zero | Full |
|---|---:|---:|---:|---:|---:|---:|---:|
{metric_row("Mask alpha", target_stats["maskAlpha"])}
{metric_row("Clip coverage", target_stats["clipCoverage"])}
{metric_row("Alpha after clip", target_stats["alphaAfterClip"])}

## Subzones

| Subzone | Original targets | `candidate-minus-runtime-002` | Red runtime dispatch |
|---|---:|---:|---:|
{subzone_rows}

## Cluster Neighborhood

| Chebyshev radius | Sampled pixels | Red candidate | Red runtime dispatch | Target-hole pixels | Final white/layer | Final red-tint |
|---:|---:|---:|---:|---:|---:|---:|
{neighborhood_rows}

## Runtime Component Distances

| Runtime component | Bounds | Min distance | Avg distance | Max distance | Nearest pair |
|---|---|---:|---:|---:|---|
{runtime_distance_rows}

## Payload And Source

For the original 59 pixels, FOR-292 still maps the reconstructed source to
`BlurredClippedCircleGM.c.drawRRect(rr, paint)` through
`SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload`, while the comparable
runtime root source is the white `drawRect` `kSrc` dispatch. FOR-294 proves
that red A8 runtime dispatch does exist elsewhere, but not in
`candidate-minus-runtime-002`.

## Preserved Decisions

- FOR-288 classification: `{audit["route"]["for288PrimaryClassification"]}`
- FOR-289 decision: `{audit["route"]["for289Decision"]}`
- FOR-290 decision: `{audit["route"]["for290Decision"]}`
- FOR-291 decision: `{audit["route"]["for291Decision"]}`
- FOR-292 decision: `{audit["route"]["for292Decision"]}`
- FOR-293 decision: `{audit["route"]["for293Decision"]}`
- FOR-294 decision: `{audit["route"]["for294Decision"]}`
- FOR-295 decision: `{audit["route"]["for295Decision"]}`
- FOR-296 decision: `{audit["route"]["for296Decision"]}`

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
    source_for295: dict[str, Any],
    source_for296: dict[str, Any],
) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("wrong Linear id")
    if audit.get("parent") != PARENT_ID:
        fail("wrong parent id")
    if audit.get("sceneId") != SCENE_ID:
        fail("wrong scene id")
    if audit.get("decision") not in DECISIONS:
        fail("invalid FOR-297 decision")
    if audit.get("decision") == DECISION_AMBIGUOUS and not audit.get("exactGap"):
        fail("ambiguous decision must name the exact gap")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision changed")

    expected_sources = {
        "for288": SOURCE_FOR288,
        "for289": SOURCE_FOR289,
        "for290": SOURCE_FOR290,
        "for291": SOURCE_FOR291,
        "for292": SOURCE_FOR292,
        "for293": SOURCE_FOR293,
        "for294": SOURCE_FOR294,
        "for295": SOURCE_FOR295,
        "for296": SOURCE_FOR296,
        "for294RawFinalReadback": RAW_FOR294,
    }
    for key, path in expected_sources.items():
        if audit["sourceAudits"].get(key) != str(path.relative_to(PROJECT_ROOT)):
            fail(f"source audit path changed for {key}")

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
    if source_for295["decision"] != "ORIGINAL_TARGETS_OUTSIDE_RED_RUNTIME_DISPATCH_DOMAIN":
        fail("FOR-295 source decision changed")
    if (
        source_for296["decision"]
        != "RED_RUNTIME_DISPATCH_DOMAIN_SPATIALLY_SEPARATE_FROM_ORIGINAL_TARGET_CLUSTER"
    ):
        fail("FOR-296 source decision changed")

    route = audit["route"]
    if route["visualParityFallbackPreserved"] != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route["cropFallbackPreserved"] != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")
    if audit["reconstructedModel"]["candidatePixels"] != 22424:
        fail("candidate domain size changed")
    if audit["reconstructedModel"]["runtimeRedDispatchPixels"] != 9088:
        fail("runtime red dispatch size changed")
    if audit["reconstructedModel"]["candidateMinusRuntimePixels"] != 13336:
        fail("candidate-minus-runtime size changed")
    if audit["reconstructedModel"]["runtimeOutsideCandidatePixels"] != 0:
        fail("runtime outside candidate changed")

    cluster = audit["targetCluster"]
    if cluster["pixels"] != 59:
        fail("original target count changed")
    if cluster["insideRedCandidateDomain"] != 59:
        fail("original targets no longer all inside candidate domain")
    if cluster["insideRedRuntimeDispatchDomain"] != 0:
        fail("original targets unexpectedly intersect runtime red dispatch")
    if cluster["insideTargetHoleComponent"] != 59:
        fail("original targets no longer all inside target hole")
    if cluster["finalReadback"]["finalWhiteLayerPixels"] != 59:
        fail("original targets no longer all final white/layer")
    if cluster["subzonePixels"]["draw_oval_outer_boundary"] != 59:
        fail("original target subzone changed")
    if cluster["distanceToAllRedRuntimeDispatch"]["minEuclidean"] != 70.936591:
        fail("nearest runtime distance changed")

    target_hole = audit["componentComparison"]["targetHoleComponent"]
    if target_hole["id"] != "candidate-minus-runtime-002":
        fail("target hole component id changed")
    if target_hole["pixels"] != 3293:
        fail("target hole size changed")
    if target_hole["originalTargetPixels"] != 59:
        fail("target hole original target count changed")
    if target_hole["finalReadback"]["finalWhiteLayerPixels"] != 3293:
        fail("target hole final white/layer count changed")
    if target_hole["finalReadback"]["finalRedTintPixels"] != 0:
        fail("target hole unexpectedly has final red-tint pixels")
    if target_hole["runtimeEvents"]["redRuntimeDispatchPixelCount"] != 0:
        fail("target hole unexpectedly has red runtime dispatch")
    if not target_hole["maskClipAlphaStats"]["allPixelsPassFor293CandidatePredicate"]:
        fail("target hole no longer passes the FOR-293 candidate predicate")
    if target_hole["maskClipAlphaStats"]["maskAlpha"]["zeroPixels"] != 0:
        fail("target hole contains zero mask-alpha pixels")
    if target_hole["maskClipAlphaStats"]["clipCoverage"]["zeroPixels"] != 0:
        fail("target hole contains zero clip-coverage pixels")
    if target_hole["maskClipAlphaStats"]["alphaAfterClip"]["zeroPixels"] != 0:
        fail("target hole contains zero alpha-after-clip pixels")

    if audit["componentComparison"]["redRuntimeComponentCount"] != 4:
        fail("red runtime component count changed")
    if audit["componentComparison"]["candidateMinusRuntimeComponentCount"] != 80:
        fail("candidate-minus-runtime component count changed")
    if audit["componentComparison"]["redRuntimeComponents"][0]["runtimeEvents"]["redRuntimeDispatchPixelCount"] != 2275:
        fail("first runtime component dispatch count changed")
    if audit["interpretation"]["candidateHoleIsExplainedBySubzoneSelection"] is not False:
        fail("subzone-selection interpretation changed")
    if audit["interpretation"]["runtimeDispatchRequiresUnmodeledFilter"] is not True:
        fail("runtime unmodeled-filter interpretation changed")
    if audit["decision"] != DECISION_UNMODELED_FILTER:
        fail("FOR-297 decision changed")

    preservation = audit["sourcePreservation"]
    for key, value in preservation.items():
        if value is not True:
            fail(f"source preservation flag `{key}` is not true")
    strict = audit["strictPreservation"]
    for key in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "productionRendererChanged",
        "cpuRendererChanged",
        "cpuInstrumentationChanged",
        "newRuntimeTraceGeneratedByFOR297",
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
        "for295DecisionChanged",
        "for296DecisionChanged",
        "m60Promoted",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if strict.get(key) is not False:
            fail(f"strict preservation flag `{key}` changed")


def main() -> None:
    source_for288 = load_json(SOURCE_FOR288)
    source_for289 = load_json(SOURCE_FOR289)
    source_for290 = load_json(SOURCE_FOR290)
    source_for291 = load_json(SOURCE_FOR291)
    source_for292 = load_json(SOURCE_FOR292)
    source_for293 = load_json(SOURCE_FOR293)
    source_for294 = load_json(SOURCE_FOR294)
    source_for295 = load_json(SOURCE_FOR295)
    source_for296 = load_json(SOURCE_FOR296)
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
            source_for295=source_for295,
            source_for296=source_for296,
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
            source_for295,
            source_for296,
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
            source_for295,
            source_for296,
        )
        if not REPORT.exists():
            write_report(audit)
    validate_audit(
        load_json(AUDIT),
        source_for288,
        source_for289,
        source_for290,
        source_for291,
        source_for292,
        source_for293,
        source_for294,
        source_for295,
        source_for296,
    )
    print(f"wrote {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"wrote {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"decision {audit['decision']}")


if __name__ == "__main__":
    main()
