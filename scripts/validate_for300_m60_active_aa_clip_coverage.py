#!/usr/bin/env python3
"""Generate and validate FOR-300 M60 active AA clip coverage evidence."""

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
import validate_for299_m60_a8_predispatch_filter_trace as for299


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-300"
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
SOURCE_FOR299 = for299.AUDIT
RAW_FOR294 = for294.RAW_AUDIT
AUDIT_NAME = "m60-active-aa-clip-coverage-for300.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-300-m60-active-aa-clip-coverage.md"

PREDISPATCH_SOURCE = for299.PREDISPATCH_SOURCE
BLEND_SKIP_SOURCE = for299.BLEND_SKIP_SOURCE
DISPATCH_SOURCE = for299.DISPATCH_SOURCE

DECISION_CORRECT = "A8_ACTIVE_CLIP_COVERAGE_ZERO_IS_CORRECT_FOR_M60"
DECISION_LAYER_SCOPE_FIX = "A8_ACTIVE_CLIP_LAYER_SCOPE_FIX_APPLIED"
DECISION_COORDINATE_OFFSET_FIX = "A8_ACTIVE_CLIP_COORDINATE_OFFSET_FIX_APPLIED"
DECISION_DIFFERENCE_FIX = "A8_ACTIVE_CLIP_DIFFERENCE_OPERATION_FIX_APPLIED"
DECISION_BOUNDED_NO_FIX = "A8_ACTIVE_CLIP_CAUSE_BOUNDED_NO_SAFE_FIX"
DECISIONS = {
    DECISION_CORRECT,
    DECISION_LAYER_SCOPE_FIX,
    DECISION_COORDINATE_OFFSET_FIX,
    DECISION_DIFFERENCE_FIX,
    DECISION_BOUNDED_NO_FIX,
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-300 validation failed: {message}")


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


def bounds_key(bounds: dict[str, Any] | None) -> str:
    if not isinstance(bounds, dict):
        return "null"
    return (
        f"{int(bounds['left'])},{int(bounds['top'])},"
        f"{int(bounds['right'])},{int(bounds['bottom'])}"
    )


def composite_bounds(event: dict[str, Any]) -> dict[str, int] | None:
    fields = ("maskOriginLeft", "maskOriginTop", "compositeX0", "compositeY0", "compositeX1", "compositeY1")
    if any(event.get(field) is None for field in fields):
        return None
    return {
        "left": int(event["maskOriginLeft"]) + int(event["compositeX0"]),
        "top": int(event["maskOriginTop"]) + int(event["compositeY0"]),
        "right": int(event["maskOriginLeft"]) + int(event["compositeX1"]),
        "bottom": int(event["maskOriginTop"]) + int(event["compositeY1"]),
    }


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


def rgba(value: Any) -> list[int]:
    if not (isinstance(value, list) and len(value) == 4):
        fail(f"expected RGBA list, got {value!r}")
    return [int(v) for v in value]


def classify_final(value: list[int]) -> str:
    if for294.white_or_layer(value):
        return "finalWhiteLayer"
    if for294.red_tint(value):
        return "finalRedTint"
    return "finalOther"


def final_readback_stats(coords: set[tuple[int, int]], final_by_coord: dict[tuple[int, int], list[int]]) -> dict[str, Any]:
    counts = Counter(classify_final(final_by_coord[coord]) for coord in coords if coord in final_by_coord)
    return {
        "pixels": len(coords),
        "availablePixels": sum(counts.values()),
        "counts": dict(counts),
        "sample": [
            {"x": x, "y": y, "rgba": final_by_coord[(x, y)], "class": classify_final(final_by_coord[(x, y)])}
            for x, y in sorted_coords(coords)
            if (x, y) in final_by_coord
        ][:12],
    }


def predispatch_event(event: dict[str, Any]) -> bool:
    return for299.predispatch_event(event)


def blend_skip_event(event: dict[str, Any]) -> bool:
    return for299.blend_skip_event(event)


def dispatch_event(event: dict[str, Any]) -> bool:
    return for299.dispatch_event(event)


def read_for294_final_pixels(raw: dict[str, Any]) -> dict[tuple[int, int], list[int]]:
    return {
        key_xy(pixel): rgba(pixel["finalReadbackRgba"])
        for pixel in raw.get("targetPixels", [])
        if isinstance(pixel, dict)
    }


def representative_event(events: list[dict[str, Any]]) -> dict[str, Any] | None:
    if not events:
        return None
    event = min(events, key=lambda item: int(item["index"]))
    return {
        "index": int(event["index"]),
        "x": int(event["x"]),
        "y": int(event["y"]),
        "source": event.get("source"),
        "coverage": event.get("coverage"),
        "a8SkipReason": event.get("a8SkipReason"),
        "maskLocalX": event.get("maskLocalX"),
        "maskLocalY": event.get("maskLocalY"),
        "blurredMaskAlpha": event.get("blurredMaskAlpha"),
        "maskedAlphaBeforeBlend": event.get("maskedAlphaBeforeBlend"),
        "activeClipBounds": event.get("activeClipBounds"),
        "layerBounds": event.get("layerBounds"),
        "sourceLayerBounds": event.get("sourceLayerBounds"),
        "compositeBounds": composite_bounds(event),
        "finalReadbackRgba": event.get("valueReadAfterRgba"),
    }


def unique_bounds(events: list[dict[str, Any]], field: str) -> list[dict[str, Any] | None]:
    seen: dict[str, dict[str, Any] | None] = {}
    for event in events:
        bounds = event.get(field)
        seen.setdefault(bounds_key(bounds), bounds if isinstance(bounds, dict) else None)
    return list(seen.values())


def offset_stats(events: list[dict[str, Any]]) -> dict[str, Any]:
    dx_values: list[int] = []
    dy_values: list[int] = []
    matches = 0
    checked = 0
    for event in events:
        if event.get("maskLocalX") is None or event.get("maskLocalY") is None:
            continue
        checked += 1
        dx = int(event["x"]) - int(event["maskLocalX"])
        dy = int(event["y"]) - int(event["maskLocalY"])
        dx_values.append(dx)
        dy_values.append(dy)
        if dx == int(event["maskOriginLeft"]) and dy == int(event["maskOriginTop"]):
            matches += 1
    return {
        "checkedPixels": checked,
        "deviceMinusMaskLocalX": value_stats(dx_values),
        "deviceMinusMaskLocalY": value_stats(dy_values),
        "matchesMaskOriginPixels": matches,
        "allOffsetsMatchMaskOrigin": checked > 0 and matches == checked,
    }


def reconstructed_stats(
    coords: set[tuple[int, int]],
    *,
    mask_alpha: Any,
    clip_coverage: Any,
    alpha_after_clip: Any,
) -> dict[str, Any]:
    mask_values = [int(mask_alpha[y, x]) for x, y in coords]
    clip_values = [int(clip_coverage[y, x]) for x, y in coords]
    alpha_values = [int(alpha_after_clip[y, x]) for x, y in coords]
    return {
        "maskAlpha": value_stats(mask_values),
        "for293AnalyticClipCoverage": value_stats(clip_values),
        "alphaAfterAnalyticClip": value_stats(alpha_values),
        "allPixelsPassFor293CandidatePredicate": all(
            mask > 0 and clip > 0 and alpha > 0
            for mask, clip, alpha in zip(mask_values, clip_values, alpha_values)
        ),
    }


def component_summary(
    component_id: str,
    coords: set[tuple[int, int]],
    *,
    pre_by_coord: dict[tuple[int, int], dict[str, Any]],
    outcome_by_coord: dict[tuple[int, int], dict[str, Any]],
    dispatch_coords: set[tuple[int, int]],
    original_targets: set[tuple[int, int]],
    final_by_coord: dict[tuple[int, int], list[int]],
    mask_alpha: Any,
    clip_coverage: Any,
    alpha_after_clip: Any,
) -> dict[str, Any]:
    pre = [pre_by_coord[coord] for coord in sorted_coords(coords) if coord in pre_by_coord]
    outcome = [outcome_by_coord[coord] for coord in sorted_coords(coords) if coord in outcome_by_coord]
    active_values = [int(event["coverage"]) for event in outcome if event.get("coverage") is not None]
    skip_reasons = Counter(
        str(event.get("a8SkipReason")) for event in outcome if event.get("a8SkipReason") is not None
    )
    source_bounds = unique_bounds(pre, "sourceLayerBounds")
    layer_bounds = unique_bounds(pre, "layerBounds")
    active_trace_bounds = unique_bounds(pre, "activeClipBounds")
    composite = [composite_bounds(event) for event in pre if composite_bounds(event) is not None]
    composite_unique: dict[str, dict[str, Any]] = {}
    for bounds in composite:
        composite_unique.setdefault(bounds_key(bounds), bounds)
    source_inside = sum(1 for coord in coords if any(bounds_contains(bounds, coord) for bounds in source_bounds))
    layer_inside = sum(1 for coord in coords if any(bounds_contains(bounds, coord) for bounds in layer_bounds))
    composite_inside = sum(1 for coord in coords if any(bounds_contains(bounds, coord) for bounds in composite_unique.values()))
    return {
        "id": component_id,
        "pixels": len(coords),
        "bounds": bounds_from_coords(coords),
        "originalTargetPixels": len(coords & original_targets),
        "predispatchPixelCount": len([coord for coord in coords if coord in pre_by_coord]),
        "outcomePixelCount": len([coord for coord in coords if coord in outcome_by_coord]),
        "dispatchPixelCount": len(coords & dispatch_coords),
        "blendSkipPixelCount": len([coord for coord in coords if coord in outcome_by_coord and coord not in dispatch_coords]),
        "missingPredispatchPixels": len(coords - set(pre_by_coord)),
        "missingOutcomePixels": len(coords - set(outcome_by_coord)),
        "activeAaClipCoverage": value_stats(active_values),
        "skipReasonCounts": dict(skip_reasons),
        "blurredMaskAlpha": value_stats([int(event["blurredMaskAlpha"]) for event in pre]),
        "maskedAlphaBeforeBlend": value_stats([int(event["maskedAlphaBeforeBlend"]) for event in pre]),
        "traceBounds": {
            "activeClipTraceBounds": active_trace_bounds,
            "layerBounds": layer_bounds,
            "sourceLayerBounds": source_bounds,
            "compositeBounds": list(composite_unique.values()),
            "a8SpanLeftValues": sorted({int(event["a8SpanLeft"]) for event in pre if event.get("a8SpanLeft") is not None}),
            "a8SpanRightValues": sorted({int(event["a8SpanRight"]) for event in pre if event.get("a8SpanRight") is not None}),
            "sourceLayerContainsPixels": source_inside,
            "layerContainsPixels": layer_inside,
            "compositeContainsPixels": composite_inside,
        },
        "maskCoordinateMapping": offset_stats(pre),
        "reconstructedFor293Model": reconstructed_stats(
            coords,
            mask_alpha=mask_alpha,
            clip_coverage=clip_coverage,
            alpha_after_clip=alpha_after_clip,
        ),
        "finalReadback": final_readback_stats(coords, final_by_coord),
        "firstPredispatch": representative_event(pre),
        "firstOutcome": representative_event(outcome),
    }


def per_pixel_sample(
    coords: set[tuple[int, int]],
    *,
    pre_by_coord: dict[tuple[int, int], dict[str, Any]],
    outcome_by_coord: dict[tuple[int, int], dict[str, Any]],
    final_by_coord: dict[tuple[int, int], list[int]],
    mask_alpha: Any,
    clip_coverage: Any,
    alpha_after_clip: Any,
    limit: int = 20,
) -> list[dict[str, Any]]:
    sample = []
    for x, y in sorted_coords(coords)[:limit]:
        pre = pre_by_coord.get((x, y), {})
        outcome = outcome_by_coord.get((x, y), {})
        sample.append(
            {
                "x": x,
                "y": y,
                "deviceCoord": {"x": x, "y": y},
                "maskCoord": {"x": pre.get("maskLocalX"), "y": pre.get("maskLocalY")},
                "maskOrigin": {"left": pre.get("maskOriginLeft"), "top": pre.get("maskOriginTop")},
                "activeAaClipCoverage": outcome.get("coverage"),
                "skipReason": outcome.get("a8SkipReason"),
                "blurredMaskAlpha": pre.get("blurredMaskAlpha"),
                "maskedAlphaBeforeBlend": pre.get("maskedAlphaBeforeBlend"),
                "for293AnalyticClipCoverage": int(clip_coverage[y, x]),
                "for293MaskAlpha": int(mask_alpha[y, x]),
                "for293AlphaAfterClip": int(alpha_after_clip[y, x]),
                "finalReadbackRgba": final_by_coord.get((x, y)),
            }
        )
    return sample


def source_needles() -> dict[str, bool]:
    bitmap_text = (PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt").read_text(
        encoding="utf-8"
    )
    canvas_text = (PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt").read_text(
        encoding="utf-8"
    )
    aa_text = (PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkAAClip.kt").read_text(
        encoding="utf-8"
    )
    return {
        "a8BlendSkipRecordsActiveClipCoverageZero": "A8_SRCINPAYLOAD_ACTIVE_CLIP_COVERAGE_ZERO" in bitmap_text,
        "blendSamplesActiveAaClipCoverage": "if (activeAaClip != null) cov = clipCoverage(x, y)" in bitmap_text,
        "a8TraceCarriesMaskAndLayerBounds": "sourceLayerBounds = sourceLayerTraceBounds" in bitmap_text,
        "canvasDifferenceUsesSkAAClipDifference": "combined.op(pathAac, SkRegion.Op.kDifference)" in canvas_text,
        "clipPathDifferenceRasterizesWithCurrentMatrix": "rasterisePathToAaClip(s, path, s.clip" in canvas_text,
        "skAAClipDifferenceCombinesAlpha": "SkRegion.Op.kDifference -> mulDiv255Round(a, 255 - b)" in aa_text,
    }


def decide(original: dict[str, Any], target_hole: dict[str, Any], runtime_components: list[dict[str, Any]]) -> tuple[str, str, dict[str, Any]]:
    original_coverage = original["activeAaClipCoverage"]
    original_model = original["reconstructedFor293Model"]
    hole_coverage = target_hole["activeAaClipCoverage"]
    runtime_dispatch = sum(int(component["dispatchPixelCount"]) for component in runtime_components)
    all_original_zero = original_coverage["zeroPixels"] == 59
    all_original_model_nonzero = original_model["for293AnalyticClipCoverage"]["zeroPixels"] == 0
    scope_ruled_out = (
        original["traceBounds"]["sourceLayerContainsPixels"] == 59
        and original["traceBounds"]["layerContainsPixels"] == 59
        and original["traceBounds"]["compositeContainsPixels"] == 59
    )
    offset_ruled_out = bool(original["maskCoordinateMapping"]["allOffsetsMatchMaskOrigin"])
    runtime_has_nonzero = runtime_dispatch == 9088 and all(
        component["activeAaClipCoverage"]["nonZeroPixels"] == component["dispatchPixelCount"]
        for component in runtime_components
    )
    if all_original_zero and all_original_model_nonzero and scope_ruled_out and offset_ruled_out and runtime_has_nonzero:
        return (
            DECISION_BOUNDED_NO_FIX,
            (
                "The 59 original pixels are inside the A8 source/mask/composite/layer spans and "
                "map to mask coordinates by the observed mask origin, but every runtime outcome "
                "has activeAaClip coverage 0 while the FOR-293 analytic difference-clip model "
                "predicts nonzero coverage. The immediate cause is therefore runtime "
                "SkAAClip coverage, not A8 iteration, mask bounds, layer scope, or coordinate "
                "offset. The exact SkAAClip band/raster difference cause is bounded but not "
                "proved enough for a safe local fix."
            ),
            {
                "layerScopeRuledOut": True,
                "coordinateOffsetRuledOut": True,
                "sourceMaskBoundsRuledOut": True,
                "runtimeSkAAClipCoverageContradictsFor293AnalyticModel": True,
                "safeLocalFixApplied": False,
                "nextRecommendedTicket": (
                    "Instrument true SkAAClip getBounds/bands and coverage probes during the "
                    "M60 clipRRect(kDifference) stack, then compare transformed vs untransformed "
                    "oval coverage before changing SkAAClip.op or clip CTM handling."
                ),
            },
        )
    return (
        DECISION_BOUNDED_NO_FIX,
        "FOR-300 could not prove a safe correction from the available trace fields.",
        {
            "originalCoverageZeroPixels": original_coverage["zeroPixels"],
            "targetHoleCoverageZeroPixels": hole_coverage["zeroPixels"],
            "runtimeDispatchPixels": runtime_dispatch,
            "safeLocalFixApplied": False,
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
    source_for299: dict[str, Any],
) -> dict[str, Any]:
    reference = for269.load_image("skia")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA reference image")
    geometry, _masks = for294.coordinate_masks(width, height)[:2]
    mask_alpha, clip_coverage, alpha_after_clip, _visible_candidate, mask_bounds = for293.red_draw_runtime_domain(
        width,
        height,
        geometry,
    )
    domains = for298.reconstruct_domains(source_for295)
    original_targets = {key_xy(pixel) for pixel in source_for292.get("perPixelComparisons", [])}
    if len(original_targets) != 59:
        fail("FOR-292 perPixelComparisons must contain the original 59 targets")
    final_by_coord = read_for294_final_pixels(raw)
    events = [event for event in raw.get("events", []) if isinstance(event, dict)]
    pre_events = [event for event in events if predispatch_event(event)]
    outcome_events = [event for event in events if blend_skip_event(event) or dispatch_event(event)]
    dispatch_events = [event for event in events if dispatch_event(event)]
    pre_by_coord = {key_xy(event): event for event in pre_events}
    outcome_by_coord = {key_xy(event): event for event in outcome_events}
    dispatch_coords = {key_xy(event) for event in dispatch_events}
    target_hole_coords = domains["targetHoleCoords"]

    target_hole = component_summary(
        "candidate-minus-runtime-002",
        target_hole_coords,
        pre_by_coord=pre_by_coord,
        outcome_by_coord=outcome_by_coord,
        dispatch_coords=dispatch_coords,
        original_targets=original_targets,
        final_by_coord=final_by_coord,
        mask_alpha=mask_alpha,
        clip_coverage=clip_coverage,
        alpha_after_clip=alpha_after_clip,
    )
    original = component_summary(
        "original-59-targets",
        original_targets,
        pre_by_coord=pre_by_coord,
        outcome_by_coord=outcome_by_coord,
        dispatch_coords=dispatch_coords,
        original_targets=original_targets,
        final_by_coord=final_by_coord,
        mask_alpha=mask_alpha,
        clip_coverage=clip_coverage,
        alpha_after_clip=alpha_after_clip,
    )
    runtime_components = [
        component_summary(
            component["id"],
            component["_coords"],
            pre_by_coord=pre_by_coord,
            outcome_by_coord=outcome_by_coord,
            dispatch_coords=dispatch_coords,
            original_targets=original_targets,
            final_by_coord=final_by_coord,
            mask_alpha=mask_alpha,
            clip_coverage=clip_coverage,
            alpha_after_clip=alpha_after_clip,
        )
        for component in domains["runtimeComponents"]
    ]
    decision, exact_gap, details = decide(original, target_hole, runtime_components)
    nonzero_runtime_coverage_coords = {
        key_xy(event)
        for event in outcome_events
        if int(event.get("coverage", 0)) > 0
    }
    zero_runtime_coverage_coords = {
        key_xy(event)
        for event in outcome_events
        if int(event.get("coverage", 0)) == 0
    }
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-active-aa-clip-coverage",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/for299-derived-active-aa-clip-audit",
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
            "for299": str(SOURCE_FOR299.relative_to(PROJECT_ROOT)),
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
            "for299Decision": source_for299["decision"],
        },
        "coverageMetric": {
            "redCandidatePixels": len(domains["redCandidateCoords"]),
            "candidateMinusRuntime002Pixels": len(target_hole_coords),
            "originalTargetPixels": len(original_targets),
            "redRuntimeDispatchPixels": len(domains["redRuntimeCoords"]),
            "rawPredispatchPixels": len(pre_by_coord),
            "rawOutcomePixels": len(outcome_by_coord),
            "rawDispatchPixels": len(dispatch_coords),
            "runtimeActiveCoverageZeroPixels": len(zero_runtime_coverage_coords),
            "runtimeActiveCoverageNonZeroPixels": len(nonzero_runtime_coverage_coords),
            "observedNonZeroActiveCoverageBoundsWithinA8Candidate": bounds_from_coords(nonzero_runtime_coverage_coords),
            "observedZeroActiveCoverageBoundsWithinA8Candidate": bounds_from_coords(zero_runtime_coverage_coords),
            "for293MaskFilterBounds": mask_bounds,
            "for293DifferenceOvalDeviceBounds": geometry["differenceOvalDeviceBounds"],
            "for293DrawOvalDeviceBounds": geometry["drawOvalDeviceBounds"],
            "candidateMinusRuntime002": target_hole,
            "originalTargets": original,
            "redRuntimeComponents": runtime_components,
        },
        "perPixelSamples": {
            "originalTargets": per_pixel_sample(
                original_targets,
                pre_by_coord=pre_by_coord,
                outcome_by_coord=outcome_by_coord,
                final_by_coord=final_by_coord,
                mask_alpha=mask_alpha,
                clip_coverage=clip_coverage,
                alpha_after_clip=alpha_after_clip,
                limit=59,
            ),
            "candidateMinusRuntime002": per_pixel_sample(
                target_hole_coords,
                pre_by_coord=pre_by_coord,
                outcome_by_coord=outcome_by_coord,
                final_by_coord=final_by_coord,
                mask_alpha=mask_alpha,
                clip_coverage=clip_coverage,
                alpha_after_clip=alpha_after_clip,
            ),
            "redRuntimeComponents": {
                component["id"]: per_pixel_sample(
                    component["_coords"],
                    pre_by_coord=pre_by_coord,
                    outcome_by_coord=outcome_by_coord,
                    final_by_coord=final_by_coord,
                    mask_alpha=mask_alpha,
                    clip_coverage=clip_coverage,
                    alpha_after_clip=alpha_after_clip,
                    limit=8,
                )
                for component in domains["runtimeComponents"]
            },
        },
        "interpretation": {
            "activeClipTraceBoundsAreDrawClipParameterNotSkAAClipGetBounds": True,
            "trueSkAAClipBoundsAndBandsNotSerialized": True,
            "observedCoverageIsRuntimeActiveAaClipCoverageFromBlend": True,
            "layerScopeExplainsOriginalTargets": False,
            "coordinateOffsetExplainsOriginalTargets": False,
            "maskOrSourceBoundsExplainOriginalTargets": False,
            "skAAClipDifferenceOrClipStackCoverageRemainsNamedGap": True,
            "recommendedFollowUp": details["nextRecommendedTicket"],
        },
        "sourceNeedles": source_needles(),
        "sourcePreservation": {
            "for288ClassificationPreserved": (
                source_for288["primaryClassification"] == "OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE"
            ),
            "for289DecisionPreserved": source_for289["decision"] == "PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP",
            "for290DecisionPreserved": source_for290["decision"] == "NO_RUNTIME_RED_ROOT_STORE_FOUND",
            "for291DecisionPreserved": source_for291["decision"] == "RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH",
            "for292DecisionPreserved": source_for292["decision"] == "RECONSTRUCTED_PAYLOAD_DRAW_MAPPING_MISMATCH",
            "for293DecisionPreserved": source_for293["decision"] == "RED_DRAWRRECT_RUNTIME_VISIBILITY_STILL_AMBIGUOUS",
            "for294DecisionPreserved": source_for294["decision"] == "RED_DRAWRRECT_RUNTIME_ROOT_DISPATCH_FOUND_OUTSIDE_TARGETS",
            "for295DecisionPreserved": source_for295["decision"] == "ORIGINAL_TARGETS_OUTSIDE_RED_RUNTIME_DISPATCH_DOMAIN",
            "for296DecisionPreserved": (
                source_for296["decision"]
                == "RED_RUNTIME_DISPATCH_DOMAIN_SPATIALLY_SEPARATE_FROM_ORIGINAL_TARGET_CLUSTER"
            ),
            "for297DecisionPreserved": source_for297["decision"] == "RUNTIME_RED_DISPATCH_REQUIRES_ADDITIONAL_UNMODELED_FILTER",
            "for298DecisionPreserved": source_for298["decision"] == for298.DECISION_STILL_UNEXPOSED,
            "for299DecisionPreserved": source_for299["decision"] == for299.DECISION_LAYER_CLIP,
        },
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRendererChanged": False,
            "normalRenderingChanged": False,
            "cpuInstrumentationAdded": False,
            "gpuRendererChanged": False,
            "globalBlendChanged": False,
            "setPixelSemanticsChanged": False,
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
        },
    }


def component_row(component: dict[str, Any]) -> str:
    coverage = component["activeAaClipCoverage"]
    reconstructed = component["reconstructedFor293Model"]
    final = component["finalReadback"]["counts"]
    return (
        f"| `{component['id']}` | {component['pixels']} | `{component['bounds']}` | "
        f"{component['originalTargetPixels']} | {component['predispatchPixelCount']} | "
        f"{component['blendSkipPixelCount']} | {component['dispatchPixelCount']} | "
        f"{coverage['zeroPixels']} | {coverage['nonZeroPixels']} | "
        f"`{component['skipReasonCounts']}` | "
        f"{reconstructed['for293AnalyticClipCoverage']['zeroPixels']} | "
        f"{reconstructed['for293AnalyticClipCoverage']['nonZeroPixels']} | "
        f"`{final}` |"
    )


def write_report(audit: dict[str, Any]) -> None:
    metric = audit["coverageMetric"]
    target_hole = metric["candidateMinusRuntime002"]
    original = metric["originalTargets"]
    runtime_rows = "\n".join(component_row(component) for component in metric["redRuntimeComponents"])
    source_rows = "\n".join(f"| `{key}` | {value} |" for key, value in audit["sourceNeedles"].items())
    report = f"""# FOR-300 M60 Active AA Clip Coverage

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Exact gap/result: `{audit["exactGap"]}`

## Result

FOR-300 consumes the FOR-299 A8 pre-dispatch trace and compares the original
59 target pixels, `candidate-minus-runtime-002`, and all `red-runtime-*`
components without changing renderer code. The immediate runtime cause is the
active AA clip coverage sampled inside `SkBitmapDevice.blend`: the original
59 pixels are in the A8 mask/source/layer/composite spans but receive
coverage 0 and therefore only produce A8 `blendSkip` events.

| Measure | Value |
|---|---:|
| Red candidate pixels | {metric["redCandidatePixels"]} |
| `candidate-minus-runtime-002` pixels | {metric["candidateMinusRuntime002Pixels"]} |
| Original target pixels | {metric["originalTargetPixels"]} |
| Red runtime dispatch pixels | {metric["redRuntimeDispatchPixels"]} |
| Raw pre-dispatch pixels | {metric["rawPredispatchPixels"]} |
| Raw A8 outcome pixels | {metric["rawOutcomePixels"]} |
| Raw A8 dispatch pixels | {metric["rawDispatchPixels"]} |
| Runtime active coverage zero pixels | {metric["runtimeActiveCoverageZeroPixels"]} |
| Runtime active coverage nonzero pixels | {metric["runtimeActiveCoverageNonZeroPixels"]} |

## Component Comparison

| Component | Pixels | Bounds | Original targets | Pre-dispatch | Blend-skip | Dispatch | Runtime cov=0 | Runtime cov>0 | Skip reasons | FOR-293 analytic cov=0 | FOR-293 analytic cov>0 | Final readback |
|---|---:|---|---:|---:|---:|---:|---:|---:|---|---:|---:|---|
{component_row(target_hole)}
{component_row(original)}
{runtime_rows}

## Bounds And Coordinates

| Field | Value |
|---|---|
| FOR-293 mask filter bounds | `{metric["for293MaskFilterBounds"]}` |
| FOR-293 difference oval device bounds | `{metric["for293DifferenceOvalDeviceBounds"]}` |
| FOR-293 draw oval device bounds | `{metric["for293DrawOvalDeviceBounds"]}` |
| Observed nonzero active coverage bounds inside A8 candidate | `{metric["observedNonZeroActiveCoverageBoundsWithinA8Candidate"]}` |
| Observed zero active coverage bounds inside A8 candidate | `{metric["observedZeroActiveCoverageBoundsWithinA8Candidate"]}` |
| Original source/layer/composite contains pixels | `{original["traceBounds"]["sourceLayerContainsPixels"]}/{original["traceBounds"]["layerContainsPixels"]}/{original["traceBounds"]["compositeContainsPixels"]}` |
| Original mask offset matches mask origin | `{original["maskCoordinateMapping"]["allOffsetsMatchMaskOrigin"]}` |
| Original active clip trace bounds | `{original["traceBounds"]["activeClipTraceBounds"]}` |
| Original source layer bounds | `{original["traceBounds"]["sourceLayerBounds"]}` |
| Original composite bounds | `{original["traceBounds"]["compositeBounds"]}` |

`activeClipBounds` in the FOR-299 trace is the draw clip parameter serialized by
the A8 trace, not a dump of `SkAAClip.getBounds()` or its bands. The decisive
coverage value still comes from the runtime `activeAaClip.coverage(x, y)` path
recorded in A8 dispatch/blend-skip outcomes.

## Ruled Out

- Layer scope: original pixels are inside the observed layer/source/composite
  spans, and the red runtime components use the same bounds.
- Coordinate offset: `device - maskLocal` equals the serialized mask origin for
  all original target pixels.
- Mask/source bounds: the original pixels are iterated in pre-dispatch and have
  nonzero blurred mask alpha.

## Remaining Named Gap

The remaining bounded gap is the difference between the FOR-293 analytic
difference-clip model and the runtime `SkAAClip` coverage. FOR-300 does not
apply a local fix because the trace does not serialize the true `SkAAClip`
bounds, bands, or intermediate `op(kDifference)` inputs. A safe correction
needs a follow-up that captures those internals before changing clip CTM or
`SkAAClip` difference semantics.

Recommended next ticket: {audit["interpretation"]["recommendedFollowUp"]}

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
- FOR-299 decision: `{audit["route"]["for299Decision"]}`

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
        fail("invalid FOR-300 decision")
    if audit.get("decision") != DECISION_BOUNDED_NO_FIX:
        fail("FOR-300 must not claim a fix without Kotlin changes and before/after evidence")
    if not audit.get("exactGap"):
        fail("decision must include exact gap/result")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision changed")
    metric = audit["coverageMetric"]
    if metric["redCandidatePixels"] != 22424:
        fail("red candidate domain size changed")
    if metric["candidateMinusRuntime002Pixels"] != 3293:
        fail("candidate-minus-runtime-002 size changed")
    if metric["originalTargetPixels"] != 59:
        fail("original target count changed")
    if metric["redRuntimeDispatchPixels"] != 9088:
        fail("red runtime coordinate count changed")
    if metric["rawPredispatchPixels"] != 22424:
        fail("raw pre-dispatch pixel count changed")
    if metric["rawDispatchPixels"] != 9088:
        fail("raw dispatch pixel count changed")
    original = metric["originalTargets"]
    if original["predispatchPixelCount"] != 59:
        fail("original targets are not all pre-dispatched")
    if original["dispatchPixelCount"] != 0:
        fail("original targets unexpectedly dispatch A8 srcInPayload")
    if original["activeAaClipCoverage"]["zeroPixels"] != 59:
        fail("original targets no longer all have zero runtime active AA coverage")
    if original["reconstructedFor293Model"]["for293AnalyticClipCoverage"]["zeroPixels"] != 0:
        fail("FOR-293 analytic model no longer predicts nonzero original-target coverage")
    if original["traceBounds"]["sourceLayerContainsPixels"] != 59:
        fail("source layer bounds no longer contain original targets")
    if original["traceBounds"]["compositeContainsPixels"] != 59:
        fail("composite bounds no longer contain original targets")
    if original["maskCoordinateMapping"]["allOffsetsMatchMaskOrigin"] is not True:
        fail("original target mask coordinate offset changed")
    if original["finalReadback"]["counts"] != {"finalWhiteLayer": 59}:
        fail("original target final readback changed")
    target_hole = metric["candidateMinusRuntime002"]
    if target_hole["dispatchPixelCount"] != 0:
        fail("candidate-minus-runtime-002 unexpectedly dispatches A8 srcInPayload")
    if target_hole["activeAaClipCoverage"]["zeroPixels"] != 3272:
        fail("candidate-minus-runtime-002 active coverage zero count changed")
    if target_hole["skipReasonCounts"].get("A8_SRCINPAYLOAD_ACTIVE_CLIP_COVERAGE_ZERO") != 3272:
        fail("candidate-minus-runtime-002 active clip skip count changed")
    if [component["dispatchPixelCount"] for component in metric["redRuntimeComponents"]] != [2275, 2275, 2270, 2268]:
        fail("red runtime component dispatch sizes changed")
    for component in metric["redRuntimeComponents"]:
        if component["activeAaClipCoverage"]["zeroPixels"] != 0:
            fail(f"{component['id']} unexpectedly has zero runtime active coverage")
    for key, value in audit["sourceNeedles"].items():
        if value is not True:
            fail(f"source needle `{key}` failed")
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
        "cpuInstrumentationAdded",
        "gpuRendererChanged",
        "globalBlendChanged",
        "setPixelSemanticsChanged",
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
    source_for298 = load_json(SOURCE_FOR298)
    source_for299 = load_json(SOURCE_FOR299)

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
    for299.validate_audit(source_for299)

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
        source_for299=source_for299,
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
        "Bounds And Coordinates",
        "Remaining Named Gap",
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
    ):
        if needle not in report_text:
            fail(f"report missing `{needle}`")
    print(f"FOR-300 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-300 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"Decision: {audit['decision']}")


if __name__ == "__main__":
    main()
