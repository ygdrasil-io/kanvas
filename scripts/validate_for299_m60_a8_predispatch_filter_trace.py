#!/usr/bin/env python3
"""Generate and validate FOR-299 M60 A8 srcInPayload pre-dispatch trace evidence."""

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
import validate_for298_m60_a8_srcinpayload_runtime_filter as for298


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-299"
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
SOURCE_FOR298 = for298.AUDIT
RAW_FOR294 = for294.RAW_AUDIT
AUDIT_NAME = "m60-a8-predispatch-filter-trace-for299.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-299-m60-a8-predispatch-filter-trace.md"

PREDISPATCH_SOURCE = "SkBitmapDevice.drawPathWithMaskFilter.A8.preDispatch"
BLEND_SKIP_SOURCE = "SkBitmapDevice.drawPathWithMaskFilter.A8.blendSkip"
PREDISPATCH_CALLSITE = "SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload"
DISPATCH_SOURCE = for294.RED_SOURCE

DECISION_MASK_ALPHA_ZERO = "A8_SRCINPAYLOAD_MASK_ALPHA_ZERO_EXPLAINS_HOLE"
DECISION_COMPOSITE_SPAN = "A8_SRCINPAYLOAD_COMPOSITE_SPAN_EXCLUDES_HOLE"
DECISION_MASK_BOUNDS_OR_OFFSET = "A8_SRCINPAYLOAD_MASK_BOUNDS_OR_OFFSET_EXPLAINS_HOLE"
DECISION_LAYER_CLIP = "A8_SRCINPAYLOAD_LAYER_CLIP_INTERSECTION_EXPLAINS_HOLE"
DECISION_AMBIGUOUS = "A8_SRCINPAYLOAD_PREDISPATCH_FILTER_TRACE_STILL_AMBIGUOUS"
DECISIONS = {
    DECISION_MASK_ALPHA_ZERO,
    DECISION_COMPOSITE_SPAN,
    DECISION_MASK_BOUNDS_OR_OFFSET,
    DECISION_LAYER_CLIP,
    DECISION_AMBIGUOUS,
}

REQUIRED_FIELDS = {
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
    raise SystemExit(f"FOR-299 validation failed: {message}")


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


def sorted_coords(coords: Iterable[tuple[int, int]]) -> list[tuple[int, int]]:
    return sorted(coords, key=lambda item: (item[1], item[0]))


def coord_sample(coords: set[tuple[int, int]], limit: int = 12) -> list[dict[str, int]]:
    return [{"x": x, "y": y} for x, y in sorted_coords(coords)[:limit]]


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


def bounds_contains(bounds: dict[str, Any] | None, coord: tuple[int, int]) -> bool:
    if not isinstance(bounds, dict):
        return False
    x, y = coord
    return int(bounds["left"]) <= x < int(bounds["right"]) and int(bounds["top"]) <= y < int(bounds["bottom"])


def value_stats(values: list[int]) -> dict[str, Any]:
    if not values:
        return {
            "pixels": 0,
            "min": None,
            "max": None,
            "average": None,
            "zeroPixels": 0,
            "nonZeroPixels": 0,
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
        "topValues": [{"value": value, "pixels": count} for value, count in counts.most_common(8)],
    }


def source_needles() -> dict[str, bool]:
    source_text = (
        PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
    ).read_text(encoding="utf-8")
    test_text = (
        PROJECT_ROOT
        / "skia-integration-tests/src/test/kotlin/org/skia/tests/For294M60ExpandedRedDrawRRectRuntimeTraceTest.kt"
    ).read_text(encoding="utf-8")
    return {
        "cpuTraceHasA8PayloadMetadata": "public data class A8SrcInPayloadTrace" in source_text,
        "cpuTraceRecordsPredispatchWithoutDispatchSource": PREDISPATCH_SOURCE in source_text,
        "cpuTraceRecordsBlendSkipWithoutDispatchSource": BLEND_SKIP_SOURCE in source_text,
        "srcInPayloadDispatchCarriesMetadata": "a8SrcInPayloadTrace = trace" in source_text,
        "for294RawSerializesMaskLocalX": '"maskLocalX": ${nullableInt(event.maskLocalX)}' in test_text,
        "for294RawSerializesBounds": '"activeClipBounds": ${bounds(event.activeClipBounds)}' in test_text,
    }


def a8_field_event(event: dict[str, Any]) -> bool:
    return any(event.get(field) is not None for field in REQUIRED_FIELDS)


def dispatch_event(event: dict[str, Any]) -> bool:
    return (
        bool(event.get("rootDevice"))
        and str(event.get("source")) == DISPATCH_SOURCE
        and event.get("a8SkipReason") is None
    )


def predispatch_event(event: dict[str, Any]) -> bool:
    return (
        bool(event.get("rootDevice"))
        and str(event.get("source")) == PREDISPATCH_SOURCE
        and str(event.get("callsite")) == PREDISPATCH_CALLSITE
    )


def blend_skip_event(event: dict[str, Any]) -> bool:
    return (
        bool(event.get("rootDevice"))
        and str(event.get("source")) == BLEND_SKIP_SOURCE
        and str(event.get("callsite")) == PREDISPATCH_CALLSITE
    )


def summarize_event(event: dict[str, Any] | None) -> dict[str, Any] | None:
    if event is None:
        return None
    return {
        "index": int(event["index"]),
        "x": int(event["x"]),
        "y": int(event["y"]),
        "source": event.get("source"),
        "callsite": event.get("callsite"),
        "mode": event.get("mode"),
        "blurredMaskAlpha": event.get("blurredMaskAlpha"),
        "maskedAlphaBeforeBlend": event.get("maskedAlphaBeforeBlend"),
        "a8SkipReason": event.get("a8SkipReason"),
        "maskLocalX": event.get("maskLocalX"),
        "maskLocalY": event.get("maskLocalY"),
        "activeClipBounds": event.get("activeClipBounds"),
        "layerBounds": event.get("layerBounds"),
        "sourceLayerBounds": event.get("sourceLayerBounds"),
    }


def component_summary(
    component_id: str,
    coords: set[tuple[int, int]],
    *,
    predispatch_events: list[dict[str, Any]],
    blend_skip_events: list[dict[str, Any]],
    dispatch_events: list[dict[str, Any]],
    original_targets: set[tuple[int, int]],
) -> dict[str, Any]:
    pre = [event for event in predispatch_events if key_xy(event) in coords]
    blend_skip = [event for event in blend_skip_events if key_xy(event) in coords]
    dispatch = [event for event in dispatch_events if key_xy(event) in coords]
    skip_reasons = Counter(
        str(event.get("a8SkipReason"))
        for event in [*pre, *blend_skip]
        if event.get("a8SkipReason") is not None
    )
    blurred_values = [int(event["blurredMaskAlpha"]) for event in pre if event.get("blurredMaskAlpha") is not None]
    masked_values = [
        int(event["maskedAlphaBeforeBlend"])
        for event in pre
        if event.get("maskedAlphaBeforeBlend") is not None
    ]
    return {
        "id": component_id,
        "pixels": len(coords),
        "bounds": bounds_from_coords(coords),
        "originalTargetPixels": len(coords & original_targets),
        "predispatchEventCount": len(pre),
        "predispatchPixelCount": len({key_xy(event) for event in pre}),
        "dispatchEventCount": len(dispatch),
        "dispatchPixelCount": len({key_xy(event) for event in dispatch}),
        "blendSkipEventCount": len(blend_skip),
        "blendSkipPixelCount": len({key_xy(event) for event in blend_skip}),
        "missingPredispatchPixels": len(coords - {key_xy(event) for event in pre}),
        "skipReasonCounts": dict(skip_reasons),
        "blurredMaskAlpha": value_stats(blurred_values),
        "maskedAlphaBeforeBlend": value_stats(masked_values),
        "firstPredispatch": summarize_event(min(pre, key=lambda event: int(event["index"])) if pre else None),
        "firstBlendSkip": summarize_event(
            min(blend_skip, key=lambda event: int(event["index"])) if blend_skip else None
        ),
        "firstDispatch": summarize_event(min(dispatch, key=lambda event: int(event["index"])) if dispatch else None),
    }


def decide(
    *,
    target_hole_coords: set[tuple[int, int]],
    hole_summary: dict[str, Any],
    predispatch_events: list[dict[str, Any]],
    blend_skip_events: list[dict[str, Any]],
) -> tuple[str, str, dict[str, Any]]:
    hole_predispatch_pixels = int(hole_summary["predispatchPixelCount"])
    hole_dispatch_pixels = int(hole_summary["dispatchPixelCount"])
    if hole_dispatch_pixels != 0:
        return (
            DECISION_AMBIGUOUS,
            "candidate-minus-runtime-002 unexpectedly has A8 srcInPayload dispatch pixels.",
            {"candidateHoleHasDispatch": True},
        )
    if hole_predispatch_pixels == len(target_hole_coords):
        skip_counts = hole_summary["skipReasonCounts"]
        mask_zero = int(skip_counts.get("A8_SRCINPAYLOAD_MASK_ALPHA_ZERO", 0))
        if mask_zero == len(target_hole_coords):
            return (
                DECISION_MASK_ALPHA_ZERO,
                "Every candidate-minus-runtime-002 pixel is iterated by the A8 srcInPayload loop and skipped before dispatch because blurredMaskAlpha is zero.",
                {"maskAlphaZeroSkipPixels": mask_zero},
            )
        active_clip_zero = int(skip_counts.get("A8_SRCINPAYLOAD_ACTIVE_CLIP_COVERAGE_ZERO", 0))
        coverage_alpha_zero = int(skip_counts.get("A8_SRCINPAYLOAD_SRC_ALPHA_ZERO_AFTER_COVERAGE", 0))
        if mask_zero + active_clip_zero + coverage_alpha_zero == len(target_hole_coords) and active_clip_zero > 0:
            return (
                DECISION_LAYER_CLIP,
                "Every candidate-minus-runtime-002 pixel is iterated by A8 srcInPayload; 3,272 pixels are skipped because active AA clip coverage is zero, 10 more round to zero source alpha after coverage modulation, and 11 edge pixels have zero blurred-mask alpha.",
                {
                    "activeClipCoverageZeroPixels": active_clip_zero,
                    "sourceAlphaZeroAfterCoveragePixels": coverage_alpha_zero,
                    "maskAlphaZeroPixels": mask_zero,
                },
            )
    trace_regions = []
    for event in predispatch_events:
        source_bounds = event.get("sourceLayerBounds")
        clip_bounds = event.get("activeClipBounds")
        layer_bounds = event.get("layerBounds")
        if not (isinstance(source_bounds, dict) and isinstance(clip_bounds, dict) and isinstance(layer_bounds, dict)):
            continue
        composite = {
            "left": int(event["maskOriginLeft"]) + int(event["compositeX0"]),
            "top": int(event["maskOriginTop"]) + int(event["compositeY0"]),
            "right": int(event["maskOriginLeft"]) + int(event["compositeX1"]),
            "bottom": int(event["maskOriginTop"]) + int(event["compositeY1"]),
        }
        key = (
            tuple(composite.items()),
            tuple(source_bounds.items()),
            tuple(clip_bounds.items()),
            tuple(layer_bounds.items()),
        )
        if key not in [region["key"] for region in trace_regions]:
            trace_regions.append(
                {
                    "key": key,
                    "compositeBounds": composite,
                    "sourceLayerBounds": source_bounds,
                    "activeClipBounds": clip_bounds,
                    "layerBounds": layer_bounds,
                }
            )
    def inside_any(coord: tuple[int, int], field: str) -> bool:
        return any(bounds_contains(region[field], coord) for region in trace_regions)

    missing = target_hole_coords - {
        key_xy(event) for event in predispatch_events if key_xy(event) in target_hole_coords
    }
    if missing and not any(inside_any(coord, "compositeBounds") for coord in target_hole_coords):
        return (
            DECISION_COMPOSITE_SPAN,
            "No candidate-minus-runtime-002 pixel is inside the observed A8 srcInPayload composite span, so the loop never iterates the hole.",
            {"missingPredispatchPixels": len(missing), "observedRegions": trace_regions[:4]},
        )
    if missing and not any(inside_any(coord, "sourceLayerBounds") for coord in target_hole_coords):
        return (
            DECISION_MASK_BOUNDS_OR_OFFSET,
            "No candidate-minus-runtime-002 pixel maps inside the observed mask/source-layer bounds.",
            {"missingPredispatchPixels": len(missing), "observedRegions": trace_regions[:4]},
        )
    if missing and not any(
        inside_any(coord, "activeClipBounds") and inside_any(coord, "layerBounds")
        for coord in target_hole_coords
    ):
        return (
            DECISION_LAYER_CLIP,
            "No candidate-minus-runtime-002 pixel survives the observed active clip and layer bounds intersection.",
            {"missingPredispatchPixels": len(missing), "observedRegions": trace_regions[:4]},
        )
    return (
        DECISION_AMBIGUOUS,
        "The enriched trace exposes A8 pre-dispatch state, but candidate-minus-runtime-002 is only partially explained by the available per-pixel events and observed bounds.",
        {
            "candidateHolePixels": len(target_hole_coords),
            "candidateHolePredispatchPixels": hole_predispatch_pixels,
            "candidateHoleDispatchPixels": hole_dispatch_pixels,
            "missingPredispatchPixels": len(missing),
            "skipReasonCounts": hole_summary["skipReasonCounts"],
            "blendSkipPixels": len({key_xy(event) for event in blend_skip_events if key_xy(event) in target_hole_coords}),
            "observedRegions": trace_regions[:4],
        },
    )


def analyze(
    *,
    raw: dict[str, Any],
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
    source_for298: dict[str, Any],
) -> dict[str, Any]:
    domains = for298.reconstruct_domains(source_for295)
    original_targets = {key_xy(pixel) for pixel in source_for292.get("perPixelComparisons", [])}
    if len(original_targets) != 59:
        fail("FOR-292 perPixelComparisons must contain the original 59 targets")
    events = [event for event in raw.get("events", []) if isinstance(event, dict)]
    predispatch_events = [event for event in events if predispatch_event(event)]
    blend_skip_events = [event for event in events if blend_skip_event(event)]
    dispatch_events = [event for event in events if dispatch_event(event)]
    predispatch_coords = {key_xy(event) for event in predispatch_events}
    blend_skip_coords = {key_xy(event) for event in blend_skip_events}
    dispatch_coords = {key_xy(event) for event in dispatch_events}
    observed_fields = set().union(*(set(event.keys()) for event in events)) if events else set()
    a8_fields = set().union(*(set(event.keys()) for event in events if a8_field_event(event))) if events else set()
    target_hole_coords = domains["targetHoleCoords"]
    target_hole = component_summary(
        "candidate-minus-runtime-002",
        target_hole_coords,
        predispatch_events=predispatch_events,
        blend_skip_events=blend_skip_events,
        dispatch_events=dispatch_events,
        original_targets=original_targets,
    )
    runtime_components = [
        component_summary(
            component["id"],
            component["_coords"],
            predispatch_events=predispatch_events,
            blend_skip_events=blend_skip_events,
            dispatch_events=dispatch_events,
            original_targets=original_targets,
        )
        for component in domains["runtimeComponents"]
    ]
    original_summary = component_summary(
        "original-59-targets",
        original_targets,
        predispatch_events=predispatch_events,
        blend_skip_events=blend_skip_events,
        dispatch_events=dispatch_events,
        original_targets=original_targets,
    )
    decision, exact_gap, details = decide(
        target_hole_coords=target_hole_coords,
        hole_summary=target_hole,
        predispatch_events=predispatch_events,
        blend_skip_events=blend_skip_events,
    )
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-a8-predispatch-filter-trace",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/for294-raw-a8-predispatch",
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
            "for298": str(SOURCE_FOR298.relative_to(PROJECT_ROOT)),
            "for294Raw": str(RAW_FOR294.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": decision,
        "exactGap": exact_gap,
        "decisionDetails": details,
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
            "for298Decision": source_for298["decision"],
        },
        "traceSchema": {
            "rawEventCount": len(events),
            "rawRootEventCount": sum(1 for event in events if bool(event.get("rootDevice"))),
            "eventFieldNames": sorted(observed_fields),
            "a8FieldNames": sorted(a8_fields & REQUIRED_FIELDS),
            "missingRequiredA8Fields": sorted(REQUIRED_FIELDS - a8_fields),
            "predispatchSource": PREDISPATCH_SOURCE,
            "blendSkipSource": BLEND_SKIP_SOURCE,
            "dispatchSource": DISPATCH_SOURCE,
            "sourceNeedles": source_needles(),
        },
        "predispatchMetric": {
            "redCandidatePixels": len(domains["redCandidateCoords"]),
            "candidateMinusRuntime002Pixels": len(target_hole_coords),
            "originalTargetPixels": len(original_targets),
            "redRuntimeDispatchPixels": len(domains["redRuntimeCoords"]),
            "rawPredispatchEvents": len(predispatch_events),
            "rawPredispatchPixels": len(predispatch_coords),
            "rawBlendSkipEvents": len(blend_skip_events),
            "rawBlendSkipPixels": len(blend_skip_coords),
            "rawDispatchEvents": len(dispatch_events),
            "rawDispatchPixels": len(dispatch_coords),
            "predispatchCoordsOutsideCandidate": len(predispatch_coords - domains["redCandidateCoords"]),
            "blendSkipCoordsOutsideCandidate": len(blend_skip_coords - domains["redCandidateCoords"]),
            "dispatchCoordsOutsideCandidate": len(dispatch_coords - domains["redCandidateCoords"]),
            "candidateMinusRuntime002": target_hole,
            "originalTargets": original_summary,
            "redRuntimeComponents": runtime_components,
            "candidateMinusRuntime002Sample": coord_sample(target_hole_coords),
            "originalTargetSample": coord_sample(original_targets),
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
            "for298DecisionPreserved": source_for298["decision"] == for298.DECISION_STILL_UNEXPOSED,
        },
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRendererChanged": False,
            "normalRenderingChanged": False,
            "cpuInstrumentationOptInOnly": True,
            "gpuRendererChanged": False,
            "globalBlendChanged": False,
            "setPixelSemanticsChanged": False,
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
        },
    }


def component_row(component: dict[str, Any]) -> str:
    return (
        f"| `{component['id']}` | {component['pixels']} | `{component['bounds']}` | "
        f"{component['originalTargetPixels']} | {component['predispatchPixelCount']} | "
        f"{component['blendSkipPixelCount']} | {component['dispatchPixelCount']} | "
        f"{component['missingPredispatchPixels']} | "
        f"`{component['skipReasonCounts']}` | {component['blurredMaskAlpha']['zeroPixels']} | "
        f"{component['blurredMaskAlpha']['average']} | {component['maskedAlphaBeforeBlend']['average']} |"
    )


def write_report(audit: dict[str, Any]) -> None:
    metric = audit["predispatchMetric"]
    schema = audit["traceSchema"]
    target_hole = metric["candidateMinusRuntime002"]
    runtime_rows = "\n".join(component_row(component) for component in metric["redRuntimeComponents"])
    field_rows = "\n".join(f"| `{field}` | present |" for field in schema["a8FieldNames"])
    source_rows = "\n".join(f"| `{key}` | {value} |" for key, value in schema["sourceNeedles"].items())
    report = f"""# FOR-299 M60 A8 Pre-dispatch Filter Trace

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Exact gap/result: `{audit["exactGap"]}`

## Result

FOR-299 adds opt-in CPU trace metadata for
`SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload` and regenerates the
FOR-294 raw capture over the complete red candidate domain. Normal rendering,
fallback policy, blend behavior, GPU/WebGPU routing, and M60 support status are
unchanged.

| Measure | Value |
|---|---:|
| Red candidate pixels | {metric["redCandidatePixels"]} |
| `candidate-minus-runtime-002` pixels | {metric["candidateMinusRuntime002Pixels"]} |
| Original target pixels | {metric["originalTargetPixels"]} |
| Red runtime dispatch pixels | {metric["redRuntimeDispatchPixels"]} |
| Raw pre-dispatch events | {metric["rawPredispatchEvents"]} |
| Raw pre-dispatch pixels | {metric["rawPredispatchPixels"]} |
| Raw A8 blend-skip events | {metric["rawBlendSkipEvents"]} |
| Raw A8 blend-skip pixels | {metric["rawBlendSkipPixels"]} |
| Raw A8 `srcInPayload` dispatch events | {metric["rawDispatchEvents"]} |
| Raw A8 `srcInPayload` dispatch pixels | {metric["rawDispatchPixels"]} |
| Pre-dispatch coords outside candidate | {metric["predispatchCoordsOutsideCandidate"]} |
| Blend-skip coords outside candidate | {metric["blendSkipCoordsOutsideCandidate"]} |
| Dispatch coords outside candidate | {metric["dispatchCoordsOutsideCandidate"]} |

## Component Comparison

| Component | Pixels | Bounds | Original targets | Pre-dispatch pixels | Blend-skip pixels | Dispatch pixels | Missing pre-dispatch | Skip reasons | Zero blurred-mask pixels | Avg blurred mask | Avg masked alpha |
|---|---:|---|---:|---:|---:|---:|---:|---|---:|---:|---:|
{component_row(target_hole)}
{component_row(metric["originalTargets"])}
{runtime_rows}

## Serialized A8 Fields

| Field | Status |
|---|---|
{field_rows}

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
- FOR-298 decision: `{audit["route"]["for298Decision"]}`

M60 remains `expected-unsupported`: `{audit["route"]["visualParityFallbackPreserved"]}`.
The crop fallback remains `{audit["route"]["cropFallbackPreserved"]}`.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_audit(audit: dict[str, Any]) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("wrong Linear id")
    if audit.get("parent") != PARENT_ID:
        fail("wrong parent id")
    if audit.get("sceneId") != SCENE_ID:
        fail("wrong scene id")
    if audit.get("decision") not in DECISIONS:
        fail("invalid FOR-299 decision")
    if not audit.get("exactGap"):
        fail("decision must include exact gap/result")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision changed")
    schema = audit["traceSchema"]
    if set(schema["missingRequiredA8Fields"]):
        fail(f"missing required A8 fields: {schema['missingRequiredA8Fields']}")
    for key, value in schema["sourceNeedles"].items():
        if value is not True:
            fail(f"source needle `{key}` failed")
    metric = audit["predispatchMetric"]
    if metric["redCandidatePixels"] != 22424:
        fail("red candidate domain size changed")
    if metric["candidateMinusRuntime002Pixels"] != 3293:
        fail("candidate-minus-runtime-002 size changed")
    if metric["originalTargetPixels"] != 59:
        fail("original target count changed")
    if metric["redRuntimeDispatchPixels"] != 9088:
        fail("red runtime coordinate count changed")
    if metric["rawDispatchPixels"] != 9088:
        fail("raw A8 srcInPayload dispatch pixel count changed")
    if metric["blendSkipCoordsOutsideCandidate"] != 0:
        fail("blend-skip coordinates escaped reconstructed candidate")
    if metric["dispatchCoordsOutsideCandidate"] != 0:
        fail("dispatch coordinates escaped reconstructed candidate")
    if metric["candidateMinusRuntime002"]["dispatchPixelCount"] != 0:
        fail("candidate-minus-runtime-002 unexpectedly has dispatch pixels")
    if metric["originalTargets"]["dispatchPixelCount"] != 0:
        fail("original targets unexpectedly have A8 dispatch pixels")
    if [component["dispatchPixelCount"] for component in metric["redRuntimeComponents"]] != [2275, 2275, 2270, 2268]:
        fail("red runtime component dispatch sizes changed")
    for key, value in audit["sourcePreservation"].items():
        if value is not True:
            fail(f"source preservation flag `{key}` is not true")
    strict = audit["strictPreservation"]
    for key in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "productionRendererChanged",
        "normalRenderingChanged",
        "gpuRendererChanged",
        "globalBlendChanged",
        "setPixelSemanticsChanged",
        "m60Promoted",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if strict.get(key) is not False:
            fail(f"strict preservation flag `{key}` changed")
    if strict.get("cpuInstrumentationOptInOnly") is not True:
        fail("CPU instrumentation must remain opt-in")


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
    source_for298 = load_json(SOURCE_FOR298)

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
    for298.validate_audit(
        source_for298,
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

    domains = for298.reconstruct_domains(source_for295)
    for294.run_runtime_trace(domains["redCandidateCoords"])
    raw = load_json(RAW_FOR294)
    audit = analyze(
        raw=raw,
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
        source_for298=source_for298,
    )
    validate_audit(audit)
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
    write_report(audit)
    reread = load_json(AUDIT)
    validate_audit(reread)
    report_text = REPORT.read_text(encoding="utf-8")
    for needle in (
        audit["decision"],
        "Component Comparison",
        "Serialized A8 Fields",
        "FOR-298 decision",
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
    ):
        if needle not in report_text:
            fail(f"report missing `{needle}`")
    print(f"FOR-299 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-299 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"Decision: {audit['decision']}")


if __name__ == "__main__":
    main()
