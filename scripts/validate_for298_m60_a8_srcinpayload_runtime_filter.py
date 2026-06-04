#!/usr/bin/env python3
"""Generate and validate FOR-298 M60 A8 srcInPayload runtime-filter evidence."""

from __future__ import annotations

import json
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
import validate_for297_m60_candidate_hole_overbreadth as for297


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-298"
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
SOURCE_FOR297 = for297.AUDIT
RAW_FOR294 = for294.RAW_AUDIT
AUDIT_NAME = "m60-a8-srcinpayload-runtime-filter-for298.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-298-m60-a8-srcinpayload-runtime-filter.md"
TRACE_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
TRACE_TEST_SOURCE = (
    PROJECT_ROOT
    / "skia-integration-tests/src/test/kotlin/org/skia/tests/For294M60ExpandedRedDrawRRectRuntimeTraceTest.kt"
)

DECISION_RUNTIME_SPAN = "A8_SRCINPAYLOAD_RUNTIME_SPAN_FILTER_EXPLAINS_HOLE"
DECISION_MASK_BOUNDS_OR_OFFSET = "A8_SRCINPAYLOAD_MASK_BOUNDS_OR_OFFSET_EXPLAINS_HOLE"
DECISION_LAYER_CLIP_INTERSECTION = "A8_SRCINPAYLOAD_LAYER_CLIP_INTERSECTION_EXPLAINS_HOLE"
DECISION_STILL_UNEXPOSED = "A8_SRCINPAYLOAD_FILTER_STILL_UNEXPOSED"
DECISIONS = {
    DECISION_RUNTIME_SPAN,
    DECISION_MASK_BOUNDS_OR_OFFSET,
    DECISION_LAYER_CLIP_INTERSECTION,
    DECISION_STILL_UNEXPOSED,
}

OBSERVED_EVENT_FIELDS = {
    "index",
    "x",
    "y",
    "bitmapWidth",
    "bitmapHeight",
    "deviceKind",
    "rootDevice",
    "source",
    "callsite",
    "branch",
    "mode",
    "blender",
    "coverage",
    "srcInputRgba",
    "srcAfterCoverageRgba",
    "valueBeforeRgba",
    "valueWrittenRgba",
    "valueReadAfterRgba",
}
REQUIRED_A8_FILTER_FIELDS = {
    "maskLocalX",
    "maskLocalY",
    "maskOriginLeft",
    "maskOriginTop",
    "maskWidth",
    "maskHeight",
    "compositeX0",
    "compositeY0",
    "compositeX1",
    "compositeY1",
    "blurredMaskAlpha",
    "maskedAlphaBeforeBlend",
    "a8SkipReason",
    "a8SpanLeft",
    "a8SpanRight",
    "activeClipBounds",
    "layerBounds",
    "sourceLayerBounds",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-298 validation failed: {message}")


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


def public_component(component: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in component.items() if key != "_coords"}


def coord_sample(coords: set[tuple[int, int]], limit: int = 12) -> list[dict[str, int]]:
    return [{"x": x, "y": y} for x, y in sorted_coords(coords)[:limit]]


def component_source_counts(component: dict[str, Any]) -> dict[str, int]:
    runtime_events = component.get("runtimeEvents", {})
    source_counts = runtime_events.get("rootSourceCounts", {})
    if not isinstance(source_counts, dict):
        return {}
    return {str(key): int(value) for key, value in source_counts.items()}


def source_needles() -> dict[str, bool]:
    source_text = TRACE_SOURCE.read_text(encoding="utf-8")
    test_text = TRACE_TEST_SOURCE.read_text(encoding="utf-8")
    return {
        "a8SrcInPayloadDispatchSourcePresent": (
            '"SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload"' in source_text
        ),
        "a8LoopUsesMaskLocalCoordinates": (
            "for (y in compositeY0 until compositeY1)" in source_text
            and "for (x in compositeX0 until compositeX1)" in source_text
            and "val devX = ml + x" in source_text
            and "val devY = mt + y" in source_text
        ),
        "a8LoopHasMaskedAlphaSkipBeforeDispatch": (
            "val maskedA = (baseA * maskA + 127) / 255" in source_text
            and "if (maskedA == 0 && !mustBlendZero) continue" in source_text
        ),
        "traceRecordSchemaIsPostBlendOnly": (
            "public data class Event(" in source_text
            and "public val coverage: Int" in source_text
            and "public val srcInput: SkColor" in source_text
            and "public val srcAfterCoverage: SkColor" in source_text
            and "public val valueReadAfter: SkColor" in source_text
            and "maskLocalX" not in source_text
            and "blurredMaskAlpha" not in source_text
            and "a8SkipReason" not in source_text
        ),
        "for294RawJsonSerializesOnlyEventFields": (
            '"coverage": ${event.coverage}' in test_text
            and '"srcInputRgba": ${rgba(event.srcInput)}' in test_text
            and "maskLocalX" not in test_text
            and "blurredMaskAlpha" not in test_text
            and "a8SkipReason" not in test_text
        ),
    }


def reconstruct_domains(source_for295: dict[str, Any]) -> dict[str, Any]:
    reference = for269.load_image("skia")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA reference image")
    geometry, _masks = for294.coordinate_masks(width, height)[:2]
    _mask_alpha, _clip_coverage, _alpha_after_clip, visible_candidate, _mask_bounds = (
        for293.red_draw_runtime_domain(width, height, geometry)
    )
    red_candidate_coords = for294.coords_from_mask(visible_candidate)
    red_runtime_coords = {
        key_xy(coord)
        for coord in source_for295.get("redRuntimeDispatchCoordinates", [])
        if isinstance(coord, dict)
    }
    if len(red_candidate_coords) != 22424:
        fail(f"red candidate domain size changed: {len(red_candidate_coords)}")
    if len(red_runtime_coords) != 9088:
        fail(f"red runtime domain size changed: {len(red_runtime_coords)}")
    candidate_minus_runtime = red_candidate_coords - red_runtime_coords
    target_components = for297.connected_components(candidate_minus_runtime, "candidate-minus-runtime")
    runtime_components = for297.connected_components(red_runtime_coords, "red-runtime")
    return {
        "redCandidateCoords": red_candidate_coords,
        "redRuntimeCoords": red_runtime_coords,
        "candidateMinusRuntimeCoords": candidate_minus_runtime,
        "targetHoleCoords": next(
            component["_coords"]
            for component in target_components
            if component["id"] == "candidate-minus-runtime-002"
        ),
        "runtimeComponents": runtime_components,
    }


def optional_raw_trace_summary(
    raw_path: Path,
    *,
    target_hole_coords: set[tuple[int, int]],
    original_targets: set[tuple[int, int]],
    red_runtime_coords: set[tuple[int, int]],
) -> dict[str, Any]:
    if not raw_path.exists():
        return {
            "present": False,
            "path": str(raw_path.relative_to(PROJECT_ROOT)),
            "usedForDecision": False,
            "reason": "raw FOR-294 build artifact absent; versioned FOR-294..FOR-297 artifacts were used",
        }
    raw = load_json(raw_path)
    events = [event for event in raw.get("events", []) if isinstance(event, dict)]
    red_events = [event for event in events if str(event.get("source")) == for294.RED_SOURCE]
    hole_events = [event for event in events if key_xy(event) in target_hole_coords]
    target_events = [event for event in events if key_xy(event) in original_targets]
    observed_fields = set().union(*(set(event.keys()) for event in events)) if events else set()
    red_fields = set().union(*(set(event.keys()) for event in red_events)) if red_events else set()
    return {
        "present": True,
        "path": str(raw_path.relative_to(PROJECT_ROOT)),
        "usedForDecision": False,
        "eventCount": len(events),
        "eventFieldNames": sorted(observed_fields),
        "redSrcInPayloadEventCount": len(red_events),
        "redSrcInPayloadPixelCount": len({key_xy(event) for event in red_events}),
        "redSrcInPayloadFieldNames": sorted(red_fields),
        "candidateHoleRootEventCount": len(hole_events),
        "candidateHoleSourceCounts": dict(Counter(str(event.get("source")) for event in hole_events)),
        "candidateHoleRedSrcInPayloadEventCount": sum(
            1 for event in hole_events if str(event.get("source")) == for294.RED_SOURCE
        ),
        "originalTargetRootEventCount": len(target_events),
        "originalTargetSourceCounts": dict(Counter(str(event.get("source")) for event in target_events)),
        "originalTargetRedSrcInPayloadEventCount": sum(
            1 for event in target_events if str(event.get("source")) == for294.RED_SOURCE
        ),
        "redRuntimeCoordsCoveredByRawRedEvents": len({key_xy(event) for event in red_events} & red_runtime_coords),
        "missingRequiredA8FilterFields": sorted(REQUIRED_A8_FILTER_FIELDS - red_fields),
    }


def summarize_component_for_filter(component: dict[str, Any]) -> dict[str, Any]:
    mask_stats = component["maskClipAlphaStats"]
    runtime_events = component["runtimeEvents"]
    return {
        "id": component["id"],
        "pixels": int(component["pixels"]),
        "bounds": component["bounds"],
        "originalTargetPixels": int(component["originalTargetPixels"]),
        "rootEventCount": int(runtime_events["rootEventCount"]),
        "rootSourceCounts": component_source_counts(component),
        "redSrcInPayloadEventCount": int(runtime_events["redRuntimeDispatchEventCount"]),
        "redSrcInPayloadPixelCount": int(runtime_events["redRuntimeDispatchPixelCount"]),
        "finalWhiteLayerPixels": int(component["finalReadback"]["finalWhiteLayerPixels"]),
        "finalRedTintPixels": int(component["finalReadback"]["finalRedTintPixels"]),
        "maskAlpha": {
            "min": mask_stats["maskAlpha"]["min"],
            "max": mask_stats["maskAlpha"]["max"],
            "average": mask_stats["maskAlpha"]["average"],
            "zeroPixels": mask_stats["maskAlpha"]["zeroPixels"],
        },
        "clipCoverage": {
            "min": mask_stats["clipCoverage"]["min"],
            "max": mask_stats["clipCoverage"]["max"],
            "average": mask_stats["clipCoverage"]["average"],
            "zeroPixels": mask_stats["clipCoverage"]["zeroPixels"],
        },
        "alphaAfterClip": {
            "min": mask_stats["alphaAfterClip"]["min"],
            "max": mask_stats["alphaAfterClip"]["max"],
            "average": mask_stats["alphaAfterClip"]["average"],
            "zeroPixels": mask_stats["alphaAfterClip"]["zeroPixels"],
        },
        "allPixelsPassFor293CandidatePredicate": bool(
            mask_stats["allPixelsPassFor293CandidatePredicate"]
        ),
    }


def analyze(
    *,
    source_for288: dict[str, Any],
    source_for289: dict[str, Any],
    source_for290: dict[str, Any],
    source_for291: dict[str, Any],
    source_for292: dict[str, Any],
    source_for293: dict[str, Any],
    source_for294: dict[str, Any],
    source_for295: dict[str, Any],
    source_for296: dict[str, Any],
    source_for297: dict[str, Any],
) -> dict[str, Any]:
    domains = reconstruct_domains(source_for295)
    original_targets = {key_xy(pixel) for pixel in source_for292.get("perPixelComparisons", [])}
    if len(original_targets) != 59:
        fail("FOR-292 perPixelComparisons must contain the original 59 targets")
    target_hole_coords = domains["targetHoleCoords"]
    target_hole_summary = source_for297["componentComparison"]["targetHoleComponent"]
    if target_hole_summary["id"] != "candidate-minus-runtime-002":
        fail("FOR-297 target hole id changed")
    runtime_component_summaries = source_for297["componentComparison"]["redRuntimeComponents"]
    runtime_components = [
        summarize_component_for_filter(component)
        for component in runtime_component_summaries
    ]
    runtime_srcin_pixels = sum(component["redSrcInPayloadPixelCount"] for component in runtime_components)
    target_hole_filter = summarize_component_for_filter(target_hole_summary)
    raw_summary = optional_raw_trace_summary(
        RAW_FOR294,
        target_hole_coords=target_hole_coords,
        original_targets=original_targets,
        red_runtime_coords=domains["redRuntimeCoords"],
    )
    missing_fields = sorted(REQUIRED_A8_FILTER_FIELDS - OBSERVED_EVENT_FIELDS)
    decision = DECISION_STILL_UNEXPOSED
    exact_gap = (
        "FOR-294/FOR-297 expose post-dispatch root blend events for A8 srcInPayload, "
        "but not the skipped pre-dispatch A8 loop state needed to separate span iteration, "
        "mask bounds/offset, or layer/source/clip intersection: missing "
        + ", ".join(missing_fields)
        + "."
    )
    source_needles_result = source_needles()
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-a8-srcinpayload-runtime-filter",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/for294-for297-derived-runtime-filter-audit",
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
            "for297": str(SOURCE_FOR297.relative_to(PROJECT_ROOT)),
            "for294RawOptional": str(RAW_FOR294.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": decision,
        "exactGap": exact_gap,
        "decisionRationale": (
            "`candidate-minus-runtime-002` contains the original 59 pixels and all 3,293 "
            "of its pixels pass the reconstructed FOR-293 `mask_alpha > 0 && "
            "clip_coverage > 0 && alpha_after_clip > 0` predicate, yet it has 0 "
            "observed root A8 `srcInPayload` events. The four `red-runtime-*` "
            "components have 9,088 observed A8 events. Existing events are emitted "
            "after `dispatchBlend`; skipped A8 loop pixels do not record span, mask "
            "offset/bounds, masked alpha, or layer/clip intersection state."
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
            "for297Decision": source_for297["decision"],
        },
        "runtimeFilterMetric": {
            "observedRuntimeA8DispatchMembership": {
                "candidateMinusRuntime002Pixels": int(target_hole_summary["pixels"]),
                "candidateMinusRuntime002RedSrcInPayloadPixels": target_hole_filter[
                    "redSrcInPayloadPixelCount"
                ],
                "candidateMinusRuntime002RedSrcInPayloadEvents": target_hole_filter[
                    "redSrcInPayloadEventCount"
                ],
                "originalTargetPixels": len(original_targets),
                "originalTargetsInsideCandidateMinusRuntime002": len(original_targets & target_hole_coords),
                "originalTargetsInsideObservedA8SrcInPayloadRuntime": len(
                    original_targets & domains["redRuntimeCoords"]
                ),
                "observedRedRuntimeComponentPixels": runtime_srcin_pixels,
                "observedRedRuntimeComponents": len(runtime_components),
                "redRuntimeCoordsOutsideCandidateDomain": len(
                    domains["redRuntimeCoords"] - domains["redCandidateCoords"]
                ),
                "candidateMinusRuntime002Bounds": bounds_from_coords(target_hole_coords),
                "originalTargetBounds": bounds_from_coords(original_targets),
                "observedRedRuntimeBounds": bounds_from_coords(domains["redRuntimeCoords"]),
                "candidateMinusRuntime002Sample": coord_sample(target_hole_coords),
                "originalTargetSample": coord_sample(original_targets),
            },
            "candidateMinusRuntime002": target_hole_filter,
            "redRuntimeComponents": runtime_components,
            "for294RuntimeTrace": {
                "rootEventCount": source_for294["runtimeRootTrace"]["rootEventCount"],
                "rootCoordinateCount": source_for294["runtimeRootTrace"]["rootCoordinateCount"],
                "redSrcInPayloadEvents": source_for294["runtimeRootTrace"]["redRootDispatchEvents"],
                "redSrcInPayloadPixels": source_for294["runtimeRootTrace"]["redRootDispatchPixels"],
                "sourceCounts": source_for294["runtimeRootTrace"]["sourceCounts"],
                "branchCounts": source_for294["runtimeRootTrace"]["branchCounts"],
                "rootCoordinatesCoverExpandedDomain": source_for294["runtimeRootTrace"][
                    "rootCoordinatesCoverExpandedDomain"
                ],
            },
        },
        "runtimeFilterObservability": {
            "eventSchemaFields": sorted(OBSERVED_EVENT_FIELDS),
            "requiredA8FilterFieldsMissingFromEventSchema": missing_fields,
            "optionalRawFOR294": raw_summary,
            "sourceNeedles": source_needles_result,
            "postDispatchOnlyTrace": True,
            "canDecideRuntimeSpanFilter": False,
            "canDecideMaskBoundsOrOffsetFilter": False,
            "canDecideLayerClipIntersectionFilter": False,
            "structuredMissingInformation": {
                "runtimeSpanIteration": [
                    "a8SpanLeft",
                    "a8SpanRight",
                    "maskLocalX",
                    "maskLocalY",
                    "a8SkipReason",
                ],
                "maskBoundsOrOffset": [
                    "maskOriginLeft",
                    "maskOriginTop",
                    "maskWidth",
                    "maskHeight",
                    "compositeX0",
                    "compositeY0",
                    "compositeX1",
                    "compositeY1",
                ],
                "layerSourceClipIntersection": [
                    "activeClipBounds",
                    "layerBounds",
                    "sourceLayerBounds",
                ],
                "preDispatchAlphaFilter": [
                    "blurredMaskAlpha",
                    "maskedAlphaBeforeBlend",
                ],
            },
        },
        "interpretation": {
            "candidateHolePassesReconstructedPredicate": bool(
                target_hole_summary["maskClipAlphaStats"]["allPixelsPassFor293CandidatePredicate"]
            ),
            "candidateHoleHasObservedA8SrcInPayload": (
                target_hole_filter["redSrcInPayloadPixelCount"] > 0
            ),
            "originalTargetsHaveObservedA8SrcInPayload": bool(original_targets & domains["redRuntimeCoords"]),
            "runtimeComponentsHaveObservedA8SrcInPayload": runtime_srcin_pixels == 9088,
            "spanFilterCouldExplainButIsNotExposed": True,
            "maskBoundsOrOffsetCouldExplainButIsNotExposed": True,
            "layerClipIntersectionCouldExplainButIsNotExposed": True,
            "exactRuntimeFilterStillUnexposed": decision == DECISION_STILL_UNEXPOSED,
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
            "for297DecisionPreserved": (
                source_for297["decision"] == "RUNTIME_RED_DISPATCH_REQUIRES_ADDITIONAL_UNMODELED_FILTER"
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
            "newRuntimeTraceGeneratedByFOR298": False,
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
            "for297DecisionChanged": False,
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def metric_row(label: str, component: dict[str, Any]) -> str:
    return (
        f"| {label} | {component['pixels']} | `{component['bounds']}` | "
        f"{component['originalTargetPixels']} | {component['redSrcInPayloadPixelCount']} | "
        f"{component['finalWhiteLayerPixels']} | {component['finalRedTintPixels']} | "
        f"{component['maskAlpha']['average']} | {component['clipCoverage']['average']} | "
        f"{component['alphaAfterClip']['average']} |"
    )


def write_report(audit: dict[str, Any]) -> None:
    metric = audit["runtimeFilterMetric"]["observedRuntimeA8DispatchMembership"]
    observability = audit["runtimeFilterObservability"]
    hole = audit["runtimeFilterMetric"]["candidateMinusRuntime002"]
    runtime_rows = "\n".join(
        metric_row(component["id"], component)
        for component in audit["runtimeFilterMetric"]["redRuntimeComponents"]
    )
    missing_rows = "\n".join(
        f"| `{field}` | missing |" for field in observability["requiredA8FilterFieldsMissingFromEventSchema"]
    )
    source_rows = "\n".join(
        f"| `{key}` | {value} |" for key, value in observability["sourceNeedles"].items()
    )
    raw = observability["optionalRawFOR294"]
    raw_status = "present" if raw["present"] else "absent"
    raw_red_events = raw.get("redSrcInPayloadEventCount", "n/a")
    report = f"""# FOR-298 M60 A8 srcInPayload Runtime Filter

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Exact gap: `{audit["exactGap"]}`

## Result

FOR-298 compares `candidate-minus-runtime-002`, the four `red-runtime-*`
components, and the original 59 target pixels without changing rendering.
The runtime A8/source-in filter is still not exposed by the current
artifacts: FOR-294 records post-dispatch root blend writes, but skipped A8
loop pixels do not carry span, mask offset/bounds, masked-alpha, or
layer/source/clip intersection metadata.

| Measure | Value |
|---|---:|
| `candidate-minus-runtime-002` pixels | {metric["candidateMinusRuntime002Pixels"]} |
| `candidate-minus-runtime-002` A8 `srcInPayload` pixels | {metric["candidateMinusRuntime002RedSrcInPayloadPixels"]} |
| Original target pixels | {metric["originalTargetPixels"]} |
| Original targets inside `candidate-minus-runtime-002` | {metric["originalTargetsInsideCandidateMinusRuntime002"]} |
| Original targets inside observed A8 `srcInPayload` runtime | {metric["originalTargetsInsideObservedA8SrcInPayloadRuntime"]} |
| Observed red runtime component pixels | {metric["observedRedRuntimeComponentPixels"]} |
| Observed red runtime components | {metric["observedRedRuntimeComponents"]} |
| Runtime coords outside reconstructed candidate | {metric["redRuntimeCoordsOutsideCandidateDomain"]} |

## Component Comparison

| Component | Pixels | Bounds | Original targets | A8 `srcInPayload` pixels | Final white/layer | Final red-tint | Avg mask | Avg clip | Avg alpha-after-clip |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|
{metric_row(hole["id"], hole)}
{runtime_rows}

## Missing Runtime Filter Fields

| Field | Status |
|---|---|
{missing_rows}

The local raw FOR-294 build artifact is `{raw_status}` at
`{raw["path"]}`. Raw A8 `srcInPayload` events inspected: `{raw_red_events}`.
The raw artifact is optional and not required for the decision because the
versioned FOR-294..FOR-297 artifacts and trace schema already show that the
pre-dispatch A8 filter state is not serialized.

## Source Needles

| Needle | Present |
|---|---|
{source_rows}

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
- FOR-297 decision: `{audit["route"]["for297Decision"]}`

M60 remains `expected-unsupported`: `{audit["route"]["visualParityFallbackPreserved"]}`.
The crop fallback remains `{audit["route"]["cropFallbackPreserved"]}`. No
production renderer, threshold, GPU/WebGPU, fallback, blend, runtime trace,
or setPixel behavior changed.

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
    source_for297: dict[str, Any],
) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("wrong Linear id")
    if audit.get("parent") != PARENT_ID:
        fail("wrong parent id")
    if audit.get("sceneId") != SCENE_ID:
        fail("wrong scene id")
    if audit.get("decision") not in DECISIONS:
        fail("invalid FOR-298 decision")
    if audit.get("decision") == DECISION_STILL_UNEXPOSED and not audit.get("exactGap"):
        fail("still-unexposed decision must name the exact gap")
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
        "for297": SOURCE_FOR297,
        "for294RawOptional": RAW_FOR294,
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
    if source_for297["decision"] != "RUNTIME_RED_DISPATCH_REQUIRES_ADDITIONAL_UNMODELED_FILTER":
        fail("FOR-297 source decision changed")

    route = audit["route"]
    if route["visualParityFallbackPreserved"] != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route["cropFallbackPreserved"] != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")

    metric = audit["runtimeFilterMetric"]["observedRuntimeA8DispatchMembership"]
    if metric["candidateMinusRuntime002Pixels"] != 3293:
        fail("candidate-minus-runtime-002 size changed")
    if metric["candidateMinusRuntime002RedSrcInPayloadPixels"] != 0:
        fail("candidate-minus-runtime-002 unexpectedly has A8 runtime pixels")
    if metric["originalTargetPixels"] != 59:
        fail("original target count changed")
    if metric["originalTargetsInsideCandidateMinusRuntime002"] != 59:
        fail("original targets no longer all sit in candidate-minus-runtime-002")
    if metric["originalTargetsInsideObservedA8SrcInPayloadRuntime"] != 0:
        fail("original targets unexpectedly intersect observed A8 runtime")
    if metric["observedRedRuntimeComponentPixels"] != 9088:
        fail("observed red runtime component pixel count changed")
    if metric["observedRedRuntimeComponents"] != 4:
        fail("observed red runtime component count changed")
    if metric["redRuntimeCoordsOutsideCandidateDomain"] != 0:
        fail("red runtime coords escaped reconstructed candidate domain")

    hole = audit["runtimeFilterMetric"]["candidateMinusRuntime002"]
    if hole["id"] != "candidate-minus-runtime-002":
        fail("target hole id changed")
    if hole["redSrcInPayloadPixelCount"] != 0:
        fail("target hole has A8 srcInPayload pixels")
    if hole["maskAlpha"]["zeroPixels"] != 0:
        fail("target hole mask alpha now contains zero pixels")
    if hole["clipCoverage"]["zeroPixels"] != 0:
        fail("target hole clip coverage now contains zero pixels")
    if hole["alphaAfterClip"]["zeroPixels"] != 0:
        fail("target hole alpha-after-clip now contains zero pixels")
    if not hole["allPixelsPassFor293CandidatePredicate"]:
        fail("target hole no longer passes reconstructed candidate predicate")

    runtime_components = audit["runtimeFilterMetric"]["redRuntimeComponents"]
    if [component["redSrcInPayloadPixelCount"] for component in runtime_components] != [2275, 2275, 2270, 2268]:
        fail("red runtime component sizes changed")
    if not all(component["allPixelsPassFor293CandidatePredicate"] for component in runtime_components):
        fail("a red runtime component no longer passes reconstructed candidate predicate")

    observability = audit["runtimeFilterObservability"]
    missing = set(observability["requiredA8FilterFieldsMissingFromEventSchema"])
    if missing != REQUIRED_A8_FILTER_FIELDS:
        fail("missing A8 filter field set changed")
    for key, value in observability["sourceNeedles"].items():
        if value is not True:
            fail(f"source needle `{key}` failed")
    if observability["postDispatchOnlyTrace"] is not True:
        fail("trace must remain classified as post-dispatch only")
    if observability["canDecideRuntimeSpanFilter"] is not False:
        fail("runtime span filter was marked decidable without exposed span data")
    if observability["canDecideMaskBoundsOrOffsetFilter"] is not False:
        fail("mask bounds/offset filter was marked decidable without exposed mask data")
    if observability["canDecideLayerClipIntersectionFilter"] is not False:
        fail("layer/clip intersection was marked decidable without exposed intersection data")

    interpretation = audit["interpretation"]
    if interpretation["candidateHolePassesReconstructedPredicate"] is not True:
        fail("candidate hole reconstructed predicate interpretation changed")
    if interpretation["candidateHoleHasObservedA8SrcInPayload"] is not False:
        fail("candidate hole A8 interpretation changed")
    if interpretation["originalTargetsHaveObservedA8SrcInPayload"] is not False:
        fail("original target A8 interpretation changed")
    if interpretation["runtimeComponentsHaveObservedA8SrcInPayload"] is not True:
        fail("runtime components A8 interpretation changed")
    if interpretation["exactRuntimeFilterStillUnexposed"] is not True:
        fail("exact runtime filter interpretation changed")
    if audit["decision"] != DECISION_STILL_UNEXPOSED:
        fail("FOR-298 decision changed")

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
        "newRuntimeTraceGeneratedByFOR298",
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
        "for297DecisionChanged",
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
    source_for297 = load_json(SOURCE_FOR297)

    for288.validate_audit(source_for288)
    for289.validate_audit(source_for289, source_for288)
    for290.validate_audit(source_for290, source_for288, source_for289)
    source_for286 = for291.load_json(for291.SOURCE_FOR286)
    for291.validate_audit(source_for291, source_for286, source_for288, source_for289, source_for290)
    for292.validate_audit(
        source_for292,
        source_for286,
        source_for288,
        source_for289,
        source_for290,
        source_for291,
    )
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
    for296.validate_audit(
        source_for296,
        source_for288,
        source_for289,
        source_for290,
        source_for291,
        source_for292,
        source_for293,
        source_for294,
        source_for295,
    )
    for297.validate_audit(
        source_for297,
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

    audit = analyze(
        source_for288=source_for288,
        source_for289=source_for289,
        source_for290=source_for290,
        source_for291=source_for291,
        source_for292=source_for292,
        source_for293=source_for293,
        source_for294=source_for294,
        source_for295=source_for295,
        source_for296=source_for296,
        source_for297=source_for297,
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
        source_for297,
    )
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
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
        source_for296,
        source_for297,
    )
    report_text = REPORT.read_text(encoding="utf-8")
    for needle in (
        audit["decision"],
        "candidate-minus-runtime-002",
        "Missing Runtime Filter Fields",
        "FOR-297 decision",
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
        "No\nproduction renderer",
    ):
        if needle not in report_text:
            fail(f"report missing `{needle}`")
    print(f"wrote {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"wrote {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"decision {audit['decision']}")


if __name__ == "__main__":
    main()
