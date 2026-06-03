#!/usr/bin/env python3
"""Generate and validate FOR-296 M60 red runtime spatial separation evidence."""

from __future__ import annotations

import json
import math
import sys
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


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-296"
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
RAW_FOR294 = for294.RAW_AUDIT
AUDIT_NAME = "m60-red-runtime-spatial-separation-for296.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-296-m60-red-runtime-spatial-separation.md"

DECISION_SPATIALLY_SEPARATE = "RED_RUNTIME_DISPATCH_DOMAIN_SPATIALLY_SEPARATE_FROM_ORIGINAL_TARGET_CLUSTER"
DECISION_CANDIDATE_OVERBROAD = "RECONSTRUCTED_RED_CANDIDATE_DOMAIN_OVERBROAD_AT_ORIGINAL_TARGETS"
DECISION_COMPOSITE_MISMATCH = "RED_RUNTIME_DOMAIN_FINAL_COMPOSITE_MISMATCH_DOMINATES_NEXT_FIX"
DECISION_AMBIGUOUS = "SPATIAL_DOMAIN_AUDIT_STILL_AMBIGUOUS"
DECISIONS = {
    DECISION_SPATIALLY_SEPARATE,
    DECISION_CANDIDATE_OVERBROAD,
    DECISION_COMPOSITE_MISMATCH,
    DECISION_AMBIGUOUS,
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-296 validation failed: {message}")


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


def sorted_coords(coords: Iterable[tuple[int, int]]) -> list[tuple[int, int]]:
    return sorted(coords, key=lambda item: (item[1], item[0]))


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
            key=lambda item: (
                item["nearest"]["euclidean"],
                item["y"],
                item["x"],
            ),
        ),
    }


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
    summaries = []
    for index, component in enumerate(ordered):
        summaries.append(
            {
                "id": f"{label}-{index:03d}",
                "pixels": len(component),
                "bounds": bounds_from_coords(component),
                "sampleCoordinates": [{"x": x, "y": y} for x, y in sorted_coords(component)[:8]],
                "_coords": component,
            }
        )
    return summaries


def component_containing(
    components: list[dict[str, Any]],
    coords: set[tuple[int, int]],
    targets: set[tuple[int, int]],
) -> dict[str, Any] | None:
    for component in components:
        candidates = component.get("_coords")
        if not isinstance(candidates, set):
            continue
        if candidates & targets:
            result = dict(component)
            result["originalTargetPixels"] = len(candidates & targets)
            return result
    return None


def component_preview(
    name: str,
    coords: set[tuple[int, int]],
    *,
    targets: set[tuple[int, int]],
    white: set[tuple[int, int]],
    red_tint: set[tuple[int, int]],
    other: set[tuple[int, int]],
    limit: int = 8,
) -> dict[str, Any]:
    components = connected_components(coords, name)
    enriched = []
    for component in components[:limit]:
        component_coords = component["_coords"]
        component_public = {key: value for key, value in component.items() if key != "_coords"}
        enriched.append(
            {
                **component_public,
                "originalTargetPixels": len(component_coords & targets),
                "finalWhiteLayerPixels": len(component_coords & white),
                "finalRedTintPixels": len(component_coords & red_tint),
                "finalOtherPixels": len(component_coords & other),
            }
        )
    return {
        "componentCount": len(components),
        "largestComponents": enriched,
        "allComponentsForTargetLookup": components,
    }


def public_component_preview(preview: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in preview.items() if key != "allComponentsForTargetLookup"}


def read_for294_final_pixels(raw_for294: dict[str, Any]) -> dict[tuple[int, int], list[int]]:
    target_pixels = raw_for294.get("targetPixels")
    if not isinstance(target_pixels, list):
        fail("raw FOR-294 audit missing targetPixels[]")
    result = {key_xy(pixel): rgba(pixel.get("finalReadbackRgba")) for pixel in target_pixels if isinstance(pixel, dict)}
    if len(result) != 22424:
        fail(f"FOR-294 raw final target pixel count changed: {len(result)}")
    return result


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
) -> dict[str, Any]:
    reference = for269.load_image("skia")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA reference image")
    _, masks, visible_candidate = for294.coordinate_masks(width, height)
    red_candidate_coords = for294.coords_from_mask(visible_candidate)
    if len(red_candidate_coords) != source_for294["traceStrategy"]["requestedCoordinates"]:
        fail("FOR-293 red candidate domain no longer matches FOR-294 requested coordinates")

    original_targets = {key_xy(pixel) for pixel in source_for295.get("perPixelComparisons", [])}
    if len(original_targets) != 59:
        fail("FOR-295 perPixelComparisons must contain the original 59 targets")
    red_runtime_coords = {
        key_xy(coord)
        for coord in source_for295.get("redRuntimeDispatchCoordinates", [])
        if isinstance(coord, dict)
    }
    if len(red_runtime_coords) != 9088:
        fail("FOR-295 red runtime dispatch coordinates changed")
    final_by_coord = read_for294_final_pixels(raw_for294)
    if set(final_by_coord) != red_candidate_coords:
        fail("FOR-294 raw final readback coordinates no longer match the red candidate domain")

    final_white = {coord for coord, value in final_by_coord.items() if for294.white_or_layer(value)}
    final_red_tint = {coord for coord, value in final_by_coord.items() if for294.red_tint(value)}
    final_other = set(final_by_coord) - final_white - final_red_tint
    candidate_minus_runtime = red_candidate_coords - red_runtime_coords
    runtime_outside_candidate = red_runtime_coords - red_candidate_coords
    original_runtime_intersection = original_targets & red_runtime_coords
    original_candidate_intersection = original_targets & red_candidate_coords

    candidate_hole_components = component_preview(
        "candidate-minus-runtime",
        candidate_minus_runtime,
        targets=original_targets,
        white=final_white,
        red_tint=final_red_tint,
        other=final_other,
        limit=10,
    )
    target_hole = component_containing(
        candidate_hole_components["allComponentsForTargetLookup"],
        candidate_minus_runtime,
        original_targets,
    )
    if target_hole is None:
        decision = DECISION_AMBIGUOUS
        exact_gap = "No candidate-minus-runtime connected component contains the original 59 targets."
    elif original_runtime_intersection:
        decision = DECISION_AMBIGUOUS
        exact_gap = (
            f"{len(original_runtime_intersection)} original targets unexpectedly intersect the "
            "FOR-294 red runtime dispatch domain."
        )
    else:
        decision = DECISION_SPATIALLY_SEPARATE
        exact_gap = None

    target_distances = distance_stats(original_targets, red_runtime_coords)
    zone_comparison: dict[str, Any] = {}
    for name in for294.REQUESTED_SUBZONES:
        zone_coords = for294.coords_from_mask(masks[name]) & red_candidate_coords
        zone_targets = zone_coords & original_targets
        zone_comparison[name] = {
            "candidatePixels": len(zone_coords),
            "redRuntimeDispatchPixels": len(zone_coords & red_runtime_coords),
            "candidateMinusRuntimePixels": len(zone_coords - red_runtime_coords),
            "originalTargetPixels": len(zone_targets),
            "originalTargetsInsideRuntime": len(zone_targets & red_runtime_coords),
            "finalWhiteLayerPixels": len(zone_coords & final_white),
            "finalRedTintPixels": len(zone_coords & final_red_tint),
            "finalOtherPixels": len(zone_coords & final_other),
            "redRuntimeFinalWhiteLayerPixels": len(zone_coords & red_runtime_coords & final_white),
            "redRuntimeFinalRedTintPixels": len(zone_coords & red_runtime_coords & final_red_tint),
            "redRuntimeFinalOtherPixels": len(zone_coords & red_runtime_coords & final_other),
            "targetDistanceToRedRuntimeDispatch": distance_stats(zone_targets, red_runtime_coords),
        }

    runtime_components = component_preview(
        "red-runtime",
        red_runtime_coords,
        targets=original_targets,
        white=final_white,
        red_tint=final_red_tint,
        other=final_other,
        limit=8,
    )
    final_white_components = component_preview(
        "final-white-layer",
        final_white,
        targets=original_targets,
        white=final_white,
        red_tint=final_red_tint,
        other=final_other,
        limit=8,
    )
    final_red_tint_components = component_preview(
        "final-red-tint",
        final_red_tint,
        targets=original_targets,
        white=final_white,
        red_tint=final_red_tint,
        other=final_other,
        limit=8,
    )
    original_target_components = component_preview(
        "original-target",
        original_targets,
        targets=original_targets,
        white=final_white,
        red_tint=final_red_tint,
        other=final_other,
        limit=8,
    )

    target_hole_details: dict[str, Any] | None = None
    if target_hole is not None and target_hole["bounds"] is not None:
        target_hole_coords = target_hole["_coords"]
        target_hole_public = {key: value for key, value in target_hole.items() if key != "_coords"}
        target_hole_details = {
            **target_hole_public,
            "finalWhiteLayerPixels": len(target_hole_coords & final_white),
            "finalRedTintPixels": len(target_hole_coords & final_red_tint),
            "finalOtherPixels": len(target_hole_coords & final_other),
            "minDistanceFromOriginalTargetsToRedRuntimeDispatch": target_distances["minEuclidean"]
            if target_distances
            else None,
            "nearestOriginalTargetToRedRuntimeDispatch": target_distances["nearestPixel"]
            if target_distances
            else None,
        }

    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-red-runtime-spatial-separation",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/for294-for295-derived-spatial-audit",
        "sourceAudits": {
            "for288": str(SOURCE_FOR288.relative_to(PROJECT_ROOT)),
            "for289": str(SOURCE_FOR289.relative_to(PROJECT_ROOT)),
            "for290": str(SOURCE_FOR290.relative_to(PROJECT_ROOT)),
            "for291": str(SOURCE_FOR291.relative_to(PROJECT_ROOT)),
            "for292": str(SOURCE_FOR292.relative_to(PROJECT_ROOT)),
            "for293": str(SOURCE_FOR293.relative_to(PROJECT_ROOT)),
            "for294": str(SOURCE_FOR294.relative_to(PROJECT_ROOT)),
            "for295": str(SOURCE_FOR295.relative_to(PROJECT_ROOT)),
            "for294RawFinalReadback": str(RAW_FOR294.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": decision,
        "exactGap": exact_gap,
        "decisionRationale": (
            "The original 59 M60 targets form one white final-readback cluster inside the "
            "single reconstructed red candidate component, but they sit in a candidate-minus-runtime "
            "hole component and are at least 70.936591 px away from the nearest observed red runtime "
            "dispatch coordinate. The FOR-294 final-composite mismatch remains real inside the red "
            "runtime domain, but it does not dominate this target-cluster membership question."
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
        },
        "domainSpatialComparison": {
            "originalTargetPixels": len(original_targets),
            "redCandidateDomainPixels": len(red_candidate_coords),
            "redRuntimeDispatchPixels": len(red_runtime_coords),
            "candidateMinusRuntimePixels": len(candidate_minus_runtime),
            "runtimeOutsideCandidatePixels": len(runtime_outside_candidate),
            "finalWhiteLayerPixels": len(final_white),
            "finalRedTintPixels": len(final_red_tint),
            "finalOtherPixels": len(final_other),
            "originalTargetsInsideRedCandidateDomain": len(original_candidate_intersection),
            "originalTargetsInsideRedRuntimeDispatchDomain": len(original_runtime_intersection),
            "originalTargetsInsideCandidateMinusRuntimeHole": len(original_targets & candidate_minus_runtime),
            "originalTargetsWithFinalWhiteLayerReadback": len(original_targets & final_white),
            "originalTargetsWithFinalRedTintReadback": len(original_targets & final_red_tint),
            "redRuntimeDispatchWithFinalRedTint": len(red_runtime_coords & final_red_tint),
            "redRuntimeDispatchWithFinalWhiteLayer": len(red_runtime_coords & final_white),
            "redRuntimeDispatchWithFinalOther": len(red_runtime_coords & final_other),
            "finalRedTintOutsideRedRuntimeDispatch": len(final_red_tint - red_runtime_coords),
            "finalWhiteLayerInsideRedRuntimeDispatch": len(final_white & red_runtime_coords),
            "candidateBounds": bounds_from_coords(red_candidate_coords),
            "redRuntimeDispatchBounds": bounds_from_coords(red_runtime_coords),
            "originalTargetBounds": bounds_from_coords(original_targets),
            "candidateMinusRuntimeBounds": bounds_from_coords(candidate_minus_runtime),
            "finalWhiteLayerBounds": bounds_from_coords(final_white),
            "finalRedTintBounds": bounds_from_coords(final_red_tint),
            "originalTargetDistanceToRedRuntimeDispatch": target_distances,
        },
        "connectedComponents": {
            "redCandidate": public_component_preview(
                component_preview(
                    "red-candidate",
                    red_candidate_coords,
                    targets=original_targets,
                    white=final_white,
                    red_tint=final_red_tint,
                    other=final_other,
                    limit=4,
                )
            ),
            "redRuntimeDispatch": public_component_preview(runtime_components),
            "candidateMinusRuntimeHoles": public_component_preview(candidate_hole_components),
            "finalWhiteLayer": public_component_preview(final_white_components),
            "finalRedTint": public_component_preview(final_red_tint_components),
            "originalTargetCluster": public_component_preview(original_target_components),
        },
        "zoneSpatialComparison": zone_comparison,
        "originalTargetClusterSummary": {
            "bounds": bounds_from_coords(original_targets),
            "pixels": len(original_targets),
            "componentCount": original_target_components["componentCount"],
            "insideRedCandidateDomain": len(original_targets & red_candidate_coords),
            "insideRedRuntimeDispatchDomain": len(original_targets & red_runtime_coords),
            "insideCandidateMinusRuntimeHole": len(original_targets & candidate_minus_runtime),
            "finalWhiteLayerPixels": len(original_targets & final_white),
            "finalRedTintPixels": len(original_targets & final_red_tint),
            "targetHoleComponent": target_hole_details,
            "nearestRedRuntimeDispatchStats": target_distances,
            "sourceSubzones": {
                name: len(original_targets & (for294.coords_from_mask(masks[name]) & red_candidate_coords))
                for name in for294.REQUESTED_SUBZONES
            },
        },
        "interpretation": {
            "nonMembershipInRuntimeRedDomain": len(original_runtime_intersection) == 0,
            "reconstructedCandidateIncludesOriginalTargets": len(original_candidate_intersection) == 59,
            "reconstructedCandidateOverbroadAtOriginalTargets": (
                len(original_targets & candidate_minus_runtime) == 59
                and len(original_targets & final_white) == 59
            ),
            "runtimeDomainFinalCompositeMismatchStillPresent": (
                len(red_runtime_coords & final_red_tint) != len(red_runtime_coords)
            ),
            "runtimeDomainFinalCompositeMismatchDominatesNextFix": False,
            "spatialSeparationDominatesOriginalTargetClusterQuestion": decision == DECISION_SPATIALLY_SEPARATE,
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
        },
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRendererChanged": False,
            "cpuRendererChanged": False,
            "cpuInstrumentationChanged": False,
            "newRuntimeTraceGeneratedByFOR296": False,
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
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def markdown_bounds(bounds: dict[str, int] | None) -> str:
    return "`none`" if bounds is None else f"`{bounds}`"


def component_rows(components: list[dict[str, Any]]) -> str:
    return "\n".join(
        (
            f"| `{component['id']}` | {component['pixels']} | {markdown_bounds(component['bounds'])} | "
            f"{component['originalTargetPixels']} | {component['finalWhiteLayerPixels']} | "
            f"{component['finalRedTintPixels']} | {component['finalOtherPixels']} |"
        )
        for component in components
    )


def write_report(audit: dict[str, Any]) -> None:
    domain = audit["domainSpatialComparison"]
    cluster = audit["originalTargetClusterSummary"]
    nearest = cluster["nearestRedRuntimeDispatchStats"]
    target_hole = cluster["targetHoleComponent"]
    zone_rows = "\n".join(
        (
            f"| `{name}` | {data['candidatePixels']} | {data['redRuntimeDispatchPixels']} | "
            f"{data['candidateMinusRuntimePixels']} | {data['originalTargetPixels']} | "
            f"{data['originalTargetsInsideRuntime']} | {data['finalWhiteLayerPixels']} | "
            f"{data['finalRedTintPixels']} | {data['finalOtherPixels']} | "
            f"{data['targetDistanceToRedRuntimeDispatch']['minEuclidean'] if data['targetDistanceToRedRuntimeDispatch'] else 'n/a'} | "
            f"{data['targetDistanceToRedRuntimeDispatch']['averageEuclidean'] if data['targetDistanceToRedRuntimeDispatch'] else 'n/a'} | "
            f"{data['targetDistanceToRedRuntimeDispatch']['maxEuclidean'] if data['targetDistanceToRedRuntimeDispatch'] else 'n/a'} |"
        )
        for name, data in audit["zoneSpatialComparison"].items()
    )
    runtime_rows = component_rows(audit["connectedComponents"]["redRuntimeDispatch"]["largestComponents"])
    hole_rows = component_rows(audit["connectedComponents"]["candidateMinusRuntimeHoles"]["largestComponents"])
    white_rows = component_rows(audit["connectedComponents"]["finalWhiteLayer"]["largestComponents"])
    red_rows = component_rows(audit["connectedComponents"]["finalRedTint"]["largestComponents"])
    report = f"""# FOR-296 M60 Red Runtime Spatial Separation

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Exact gap: `{audit["exactGap"] or "none"}`

## Result

FOR-296 audits the spatial split left by FOR-295 without changing rendering.
The original 59 pixels are still inside the reconstructed red candidate domain,
but they are in a candidate-minus-runtime hole component and have no overlap
with the observed FOR-294 red runtime dispatch coordinates.

| Measure | Value |
|---|---:|
| Original target pixels | {domain["originalTargetPixels"]} |
| Red candidate domain pixels | {domain["redCandidateDomainPixels"]} |
| Red runtime dispatch pixels | {domain["redRuntimeDispatchPixels"]} |
| Candidate-minus-runtime hole pixels | {domain["candidateMinusRuntimePixels"]} |
| Runtime pixels outside candidate | {domain["runtimeOutsideCandidatePixels"]} |
| Final white/layer pixels | {domain["finalWhiteLayerPixels"]} |
| Final red-tint pixels | {domain["finalRedTintPixels"]} |
| Final other pixels | {domain["finalOtherPixels"]} |
| Targets inside red candidate | {domain["originalTargetsInsideRedCandidateDomain"]} |
| Targets inside red runtime dispatch | {domain["originalTargetsInsideRedRuntimeDispatchDomain"]} |
| Targets inside candidate-minus-runtime hole | {domain["originalTargetsInsideCandidateMinusRuntimeHole"]} |
| Targets final white/layer | {domain["originalTargetsWithFinalWhiteLayerReadback"]} |
| Targets final red-tint | {domain["originalTargetsWithFinalRedTintReadback"]} |
| Red runtime dispatch final red-tint | {domain["redRuntimeDispatchWithFinalRedTint"]} |
| Red runtime dispatch final white/layer | {domain["redRuntimeDispatchWithFinalWhiteLayer"]} |
| Red runtime dispatch final other | {domain["redRuntimeDispatchWithFinalOther"]} |

## Bounds

| Domain | Bounds |
|---|---|
| Red candidate | {markdown_bounds(domain["candidateBounds"])} |
| Red runtime dispatch | {markdown_bounds(domain["redRuntimeDispatchBounds"])} |
| Candidate-minus-runtime holes | {markdown_bounds(domain["candidateMinusRuntimeBounds"])} |
| Original target cluster | {markdown_bounds(domain["originalTargetBounds"])} |
| Final white/layer | {markdown_bounds(domain["finalWhiteLayerBounds"])} |
| Final red-tint | {markdown_bounds(domain["finalRedTintBounds"])} |

The candidate and runtime domains share broad bounds, so bounds alone are not
enough. The red runtime dispatch set is sparse: it has four connected
components, while the target cluster remains final white/layer inside a
candidate-minus-runtime hole.

## Target Cluster

| Measure | Value |
|---|---:|
| Cluster pixels | {cluster["pixels"]} |
| Cluster connected components | {cluster["componentCount"]} |
| Inside red candidate | {cluster["insideRedCandidateDomain"]} |
| Inside red runtime dispatch | {cluster["insideRedRuntimeDispatchDomain"]} |
| Inside candidate-minus-runtime hole | {cluster["insideCandidateMinusRuntimeHole"]} |
| Final white/layer pixels | {cluster["finalWhiteLayerPixels"]} |
| Final red-tint pixels | {cluster["finalRedTintPixels"]} |
| Min Euclidean distance to red runtime dispatch | {nearest["minEuclidean"]} |
| Avg Euclidean distance to red runtime dispatch | {nearest["averageEuclidean"]} |
| Max Euclidean distance to red runtime dispatch | {nearest["maxEuclidean"]} |
| Min Manhattan distance to red runtime dispatch | {nearest["minManhattan"]} |
| Max Manhattan distance to red runtime dispatch | {nearest["maxManhattan"]} |
| Min Chebyshev distance to red runtime dispatch | {nearest["minChebyshev"]} |
| Max Chebyshev distance to red runtime dispatch | {nearest["maxChebyshev"]} |

Nearest target: `{nearest["nearestPixel"]["x"]},{nearest["nearestPixel"]["y"]}` to
red runtime dispatch `{nearest["nearestPixel"]["nearest"]["x"]},{nearest["nearestPixel"]["nearest"]["y"]}`.

Target hole component: `{target_hole["id"] if target_hole else "none"}`,
pixels `{target_hole["pixels"] if target_hole else "n/a"}`, bounds
{markdown_bounds(target_hole["bounds"] if target_hole else None)}, final white/layer
`{target_hole["finalWhiteLayerPixels"] if target_hole else "n/a"}`, final red-tint
`{target_hole["finalRedTintPixels"] if target_hole else "n/a"}`.

## Zone Spatial Comparison

| Subzone | Candidate | Runtime red | Candidate-runtime holes | Original targets | Targets in runtime | Final white/layer | Final red-tint | Final other | Target min dist | Target avg dist | Target max dist |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
{zone_rows}

## Connected Component Groups

### Red Runtime Dispatch

| Component | Pixels | Bounds | Original targets | Final white/layer | Final red-tint | Final other |
|---|---:|---|---:|---:|---:|---:|
{runtime_rows}

### Candidate-Minus-Runtime Holes

| Component | Pixels | Bounds | Original targets | Final white/layer | Final red-tint | Final other |
|---|---:|---|---:|---:|---:|---:|
{hole_rows}

### Final White/Layer

| Component | Pixels | Bounds | Original targets | Final white/layer | Final red-tint | Final other |
|---|---:|---|---:|---:|---:|---:|
{white_rows}

### Final Red-Tint

| Component | Pixels | Bounds | Original targets | Final white/layer | Final red-tint | Final other |
|---|---:|---|---:|---:|---:|---:|
{red_rows}

## Interpretation

- Non-membership in the runtime red domain: `{audit["interpretation"]["nonMembershipInRuntimeRedDomain"]}`.
- Reconstructed candidate includes original targets: `{audit["interpretation"]["reconstructedCandidateIncludesOriginalTargets"]}`.
- Reconstructed candidate is overbroad at the original target cluster: `{audit["interpretation"]["reconstructedCandidateOverbroadAtOriginalTargets"]}`.
- Runtime-domain final-composite mismatch still present: `{audit["interpretation"]["runtimeDomainFinalCompositeMismatchStillPresent"]}`.
- Runtime-domain final-composite mismatch dominates next fix: `{audit["interpretation"]["runtimeDomainFinalCompositeMismatchDominatesNextFix"]}`.

The next diagnostic focus is the spatial candidate/runtime split around the
original target cluster, not the final-composite mismatch inside the red
runtime dispatch domain.

## Preserved Decisions

- FOR-288 classification: `{audit["route"]["for288PrimaryClassification"]}`
- FOR-289 decision: `{audit["route"]["for289Decision"]}`
- FOR-290 decision: `{audit["route"]["for290Decision"]}`
- FOR-291 decision: `{audit["route"]["for291Decision"]}`
- FOR-292 decision: `{audit["route"]["for292Decision"]}`
- FOR-293 decision: `{audit["route"]["for293Decision"]}`
- FOR-294 decision: `{audit["route"]["for294Decision"]}`
- FOR-295 decision: `{audit["route"]["for295Decision"]}`

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
) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("wrong Linear id")
    if audit.get("parent") != PARENT_ID:
        fail("wrong parent id")
    if audit.get("sceneId") != SCENE_ID:
        fail("wrong scene id")
    if audit.get("decision") not in DECISIONS:
        fail("invalid FOR-296 decision")
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
    if source_for295["decision"] != "ORIGINAL_TARGETS_OUTSIDE_RED_RUNTIME_DISPATCH_DOMAIN":
        fail("FOR-295 source decision changed")

    route = audit["route"]
    if route["visualParityFallbackPreserved"] != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route["cropFallbackPreserved"] != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")

    domain = audit["domainSpatialComparison"]
    expected_domain = {
        "originalTargetPixels": 59,
        "redCandidateDomainPixels": 22424,
        "redRuntimeDispatchPixels": 9088,
        "candidateMinusRuntimePixels": 13336,
        "runtimeOutsideCandidatePixels": 0,
        "finalWhiteLayerPixels": 15304,
        "finalRedTintPixels": 6108,
        "finalOtherPixels": 1012,
        "originalTargetsInsideRedCandidateDomain": 59,
        "originalTargetsInsideRedRuntimeDispatchDomain": 0,
        "originalTargetsInsideCandidateMinusRuntimeHole": 59,
        "originalTargetsWithFinalWhiteLayerReadback": 59,
        "originalTargetsWithFinalRedTintReadback": 0,
        "redRuntimeDispatchWithFinalRedTint": 6108,
        "redRuntimeDispatchWithFinalWhiteLayer": 1968,
        "redRuntimeDispatchWithFinalOther": 1012,
        "finalRedTintOutsideRedRuntimeDispatch": 0,
        "finalWhiteLayerInsideRedRuntimeDispatch": 1968,
    }
    for key, expected in expected_domain.items():
        if domain.get(key) != expected:
            fail(f"domain `{key}` changed: {domain.get(key)} != {expected}")
    distances = domain["originalTargetDistanceToRedRuntimeDispatch"]
    if distances["minEuclidean"] != 70.936591:
        fail("minimum Euclidean target/runtime distance changed")
    if distances["averageEuclidean"] != 77.870976:
        fail("average Euclidean target/runtime distance changed")
    if distances["maxEuclidean"] != 84.386018:
        fail("maximum Euclidean target/runtime distance changed")

    components = audit["connectedComponents"]
    if components["redCandidate"]["componentCount"] != 1:
        fail("red candidate component count changed")
    if components["redRuntimeDispatch"]["componentCount"] != 4:
        fail("red runtime dispatch component count changed")
    if components["candidateMinusRuntimeHoles"]["componentCount"] != 80:
        fail("candidate-minus-runtime hole component count changed")
    if components["finalWhiteLayer"]["componentCount"] != 17:
        fail("final white/layer component count changed")
    if components["finalRedTint"]["componentCount"] != 4:
        fail("final red-tint component count changed")
    if components["originalTargetCluster"]["componentCount"] != 1:
        fail("original target cluster component count changed")

    cluster = audit["originalTargetClusterSummary"]
    target_hole = cluster["targetHoleComponent"]
    if target_hole["pixels"] != 3293:
        fail("target candidate-hole component size changed")
    if target_hole["originalTargetPixels"] != 59:
        fail("target candidate-hole component no longer contains all original targets")
    if target_hole["finalWhiteLayerPixels"] != 3293:
        fail("target candidate-hole component is no longer entirely final white/layer")
    if target_hole["finalRedTintPixels"] != 0:
        fail("target candidate-hole component unexpectedly contains final red-tint")
    if cluster["sourceSubzones"]["draw_oval_outer_boundary"] != 59:
        fail("original targets are no longer all in draw_oval_outer_boundary")
    if cluster["sourceSubzones"]["blurred_content_envelope"] != 59:
        fail("original targets are no longer all in blurred_content_envelope")

    zones = audit["zoneSpatialComparison"]
    expected_zones = {
        "draw_oval_outer_boundary": (10856, 5548, 5308, 59, 6176, 3700, 980),
        "difference_oval_inner_boundary": (6168, 1192, 4976, 0, 4976, 1176, 16),
        "halo_interior": (3568, 1248, 2320, 0, 2320, 1232, 16),
        "outside_draw_oval": (1832, 1100, 732, 0, 1832, 0, 0),
        "blurred_content_envelope": (20592, 7988, 12604, 59, 13472, 6108, 1012),
    }
    for name, expected in expected_zones.items():
        data = zones[name]
        actual = (
            data["candidatePixels"],
            data["redRuntimeDispatchPixels"],
            data["candidateMinusRuntimePixels"],
            data["originalTargetPixels"],
            data["finalWhiteLayerPixels"],
            data["finalRedTintPixels"],
            data["finalOtherPixels"],
        )
        if actual != expected:
            fail(f"zone `{name}` changed: {actual} != {expected}")

    interpretation = audit["interpretation"]
    if interpretation["nonMembershipInRuntimeRedDomain"] is not True:
        fail("FOR-296 must keep original-target non-membership explicit")
    if interpretation["reconstructedCandidateOverbroadAtOriginalTargets"] is not True:
        fail("FOR-296 must record the overbroad candidate fact at original targets")
    if interpretation["runtimeDomainFinalCompositeMismatchStillPresent"] is not True:
        fail("FOR-294 final-composite mismatch fact changed")
    if interpretation["runtimeDomainFinalCompositeMismatchDominatesNextFix"] is not False:
        fail("FOR-296 must not let final-composite mismatch dominate this spatial audit")
    if audit["decision"] != DECISION_SPATIALLY_SEPARATE:
        fail(f"FOR-296 expected {DECISION_SPATIALLY_SEPARATE}, got {audit['decision']}")

    preservation = audit["strictPreservation"]
    for key in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "productionRendererChanged",
        "cpuRendererChanged",
        "cpuInstrumentationChanged",
        "newRuntimeTraceGeneratedByFOR296",
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
    source_for295 = load_json(SOURCE_FOR295)
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
    for295.validate_audit(
        source_for295,
        source_for288,
        source_for289,
        source_for290,
        source_for291,
        source_for292,
        source_for293,
        source_for294,
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
            source_for295=source_for295,
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
        source_for295,
    )
    report_text = REPORT.read_text(encoding="utf-8")
    for needle in (
        reread["decision"],
        "Zone Spatial Comparison",
        "Connected Component Groups",
        "Target hole component",
        "FOR-295 decision",
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
        "No\nproduction renderer",
    ):
        if needle not in report_text:
            fail(f"report missing `{needle}`")
    print(f"FOR-296 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-296 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"Decision: {reread['decision']}")


if __name__ == "__main__":
    main()
