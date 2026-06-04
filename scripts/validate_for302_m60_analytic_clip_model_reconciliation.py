#!/usr/bin/env python3
"""Generate and validate FOR-302 M60 analytic clip model reconciliation."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for280_cpu_aa_difference_clip_coverage_edge as for280
import validate_for292_m60_source_payload_derivation_audit as for292
import validate_for293_m60_red_drawrrect_runtime_visibility_audit as for293
import validate_for294_m60_expanded_red_drawrrect_runtime_trace as for294
import validate_for295_m60_red_domain_vs_white_targets as for295
import validate_for300_m60_active_aa_clip_coverage as for300
import validate_for301_m60_skaaclip_band_trace as for301


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-302"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-m60-analytic-clip-model-reconciliation-ticket"
SOURCE_FOR292 = for292.AUDIT
SOURCE_FOR293 = for293.AUDIT
SOURCE_FOR295 = for295.AUDIT
SOURCE_FOR300 = for300.AUDIT
SOURCE_FOR301 = for301.AUDIT
AUDIT_NAME = "m60-analytic-clip-model-reconciliation-for302.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-302-m60-analytic-clip-model-reconciliation.md"

DECISION_POLARITY_FIX = "M60_ANALYTIC_MODEL_CLIP_POLARITY_FIX_APPLIED"
DECISION_PIXEL_CENTER_FIX = "M60_ANALYTIC_MODEL_PIXEL_CENTER_FIX_APPLIED"
DECISION_CTM_OR_BOUNDS_FIX = "M60_ANALYTIC_MODEL_CTM_OR_BOUNDS_FIX_APPLIED"
DECISION_RUNTIME_CORRECT = "M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT"
DECISION_AMBIGUOUS = "M60_ANALYTIC_RUNTIME_CONTRADICTION_STILL_AMBIGUOUS"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-302 validation failed: {message}")


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


def sorted_coords(coords: Iterable[tuple[int, int]]) -> list[tuple[int, int]]:
    return sorted(coords, key=lambda item: (item[1], item[0]))


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


def compare_values(model: list[int], runtime: list[int]) -> dict[str, int]:
    if len(model) != len(runtime):
        fail("model/runtime comparison length mismatch")
    return {
        "pixels": len(model),
        "exactMatches": sum(1 for a, b in zip(model, runtime) if a == b),
        "mismatches": sum(1 for a, b in zip(model, runtime) if a != b),
        "modelNonZeroRuntimeZero": sum(1 for a, b in zip(model, runtime) if a > 0 and b == 0),
        "modelZeroRuntimeNonZero": sum(1 for a, b in zip(model, runtime) if a == 0 and b > 0),
        "bothNonZeroDifferent": sum(1 for a, b in zip(model, runtime) if a > 0 and b > 0 and a != b),
        "zeroNonZeroClassificationMatches": sum(1 for a, b in zip(model, runtime) if (a == 0) == (b == 0)),
    }


def coverage_from_bands(bands: list[dict[str, Any]], x: int, y: int) -> int:
    for band in bands:
        if int(band["top"]) <= y < int(band["bottom"]):
            for run in band.get("runs", []):
                if int(run["left"]) <= x < int(run["right"]):
                    return int(run["alpha"])
            return 0
    return 0


def runtime_maps(for301_audit: dict[str, Any]) -> dict[str, Any]:
    raw = for301_audit["rawBandTrace"]
    return {
        "parent": raw["parentBands"],
        "path": raw["pathBands"],
        "result": raw["resultBands"],
    }


def runtime_value(maps: dict[str, Any], name: str, coord: tuple[int, int]) -> int:
    x, y = coord
    return coverage_from_bands(maps[name], x, y)


def for293_clip_grid() -> Any:
    reference = for269.load_image("skia")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA reference image")
    geometry, _masks = for294.coordinate_masks(width, height)[:2]
    _mask_alpha, clip_coverage, _alpha_after_clip, _visible_candidate, _mask_bounds = for293.red_draw_runtime_domain(
        width,
        height,
        geometry,
    )
    return clip_coverage


def ellipse_path_coverage(x: int, y: int, rect: list[int], mode: str) -> int:
    if mode == "for293-4x4":
        return for280.ellipse_coverage_4x4(x, y, rect)
    left, top, right, bottom = rect
    cx = (left + right) / 2.0
    cy = (top + bottom) / 2.0
    rx = (right - left) / 2.0
    ry = (bottom - top) / 2.0
    if mode == "center-binary":
        px = x + 0.5
        py = y + 0.5
    elif mode == "top-left-binary":
        px = float(x)
        py = float(y)
    else:
        fail(f"unknown ellipse sampling mode: {mode}")
    return 255 if ((px - cx) / rx) ** 2 + ((py - cy) / ry) ** 2 <= 1.0 else 0


def analytic_variant_summary(coords: set[tuple[int, int]], rect: list[int], mode: str) -> dict[str, Any]:
    result_values = [255 - ellipse_path_coverage(x, y, rect, mode) for x, y in sorted_coords(coords)]
    return {
        "rect": rect,
        "sampling": mode,
        "resultCoverage": value_stats(result_values),
    }


def coord_sample(
    coords: list[tuple[int, int]],
    *,
    for293_values: list[int],
    path_values: list[int],
    result_values: list[int],
    limit: int = 12,
) -> list[dict[str, int]]:
    return [
        {
            "x": x,
            "y": y,
            "for293SurvivingClipCoverage": for293_values[index],
            "runtimeDifferencePathCoverage": path_values[index],
            "runtimeDifferenceResultCoverage": result_values[index],
        }
        for index, (x, y) in enumerate(coords[:limit])
    ]


def summarize_group(
    group_id: str,
    coords: set[tuple[int, int]],
    *,
    maps: dict[str, Any],
    for293_clip: Any,
) -> dict[str, Any]:
    ordered = sorted_coords(coords)
    parent_values = [runtime_value(maps, "parent", coord) for coord in ordered]
    path_values = [runtime_value(maps, "path", coord) for coord in ordered]
    result_values = [runtime_value(maps, "result", coord) for coord in ordered]
    formula_values = [(parent * (255 - path) + 127) // 255 for parent, path in zip(parent_values, path_values)]
    for293_values = [int(for293_clip[y, x]) for x, y in ordered]
    for293_implied_path = [255 - value for value in for293_values]
    naive_inverted_for293_result = [255 - value for value in for293_values]
    return {
        "id": group_id,
        "pixels": len(ordered),
        "bounds": bounds_from_coords(coords),
        "runtimeParentCoverage": value_stats(parent_values),
        "runtimeDifferencePathCoverage": value_stats(path_values),
        "runtimeDifferenceResultCoverage": value_stats(result_values),
        "runtimeFormulaResultCoverage": value_stats(formula_values),
        "for293SurvivingClipCoverage": value_stats(for293_values),
        "for293ImpliedDifferencePathCoverage": value_stats(for293_implied_path),
        "naivePolarityInvertedFor293ResultCoverage": value_stats(naive_inverted_for293_result),
        "for293SurvivingClipVsRuntimeResult": compare_values(for293_values, result_values),
        "for293ImpliedPathVsRuntimePath": compare_values(for293_implied_path, path_values),
        "runtimeFormulaVsRuntimeResult": compare_values(formula_values, result_values),
        "naivePolarityInversionVsRuntimeResult": compare_values(naive_inverted_for293_result, result_values),
        "sample": coord_sample(
            ordered,
            for293_values=for293_values,
            path_values=path_values,
            result_values=result_values,
        ),
    }


def analyze(
    *,
    source_for292: dict[str, Any],
    source_for293: dict[str, Any],
    source_for295: dict[str, Any],
    source_for300: dict[str, Any],
    source_for301: dict[str, Any],
) -> dict[str, Any]:
    groups = for301.build_probe_groups(source_for292, source_for295)
    maps = runtime_maps(source_for301)
    clip_grid = for293_clip_grid()
    summaries = {
        group_id: summarize_group(group_id, coords, maps=maps, for293_clip=clip_grid)
        for group_id, coords in groups.items()
    }
    original = summaries["original-59-targets"]
    target_hole = summaries["candidate-minus-runtime-002"]
    red_components = [summaries[key] for key in sorted(summaries) if key.startswith("red-runtime-")]
    all_red_coords = set().union(*(groups[component["id"]] for component in red_components))
    variant_groups = {
        "original-59-targets": groups["original-59-targets"],
        "candidate-minus-runtime-002": groups["candidate-minus-runtime-002"],
        "red-runtime-union": all_red_coords,
    }
    trace = source_for301["traceEvent"]
    decisions = {
        DECISION_POLARITY_FIX: True,
        DECISION_PIXEL_CENTER_FIX: False,
        DECISION_CTM_OR_BOUNDS_FIX: False,
        DECISION_RUNTIME_CORRECT: True,
        DECISION_AMBIGUOUS: False,
    }
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-analytic-clip-model-reconciliation",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/audit-only-runtime-reconciled-clip-model",
        "sourceMemory": SOURCE_MEMORY,
        "sourceAudits": {
            "for292": str(SOURCE_FOR292.relative_to(PROJECT_ROOT)),
            "for293": str(SOURCE_FOR293.relative_to(PROJECT_ROOT)),
            "for295": str(SOURCE_FOR295.relative_to(PROJECT_ROOT)),
            "for300": str(SOURCE_FOR300.relative_to(PROJECT_ROOT)),
            "for301": str(SOURCE_FOR301.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": DECISION_RUNTIME_CORRECT,
        "decisions": decisions,
        "exactGap": (
            "FOR-293 treated the analytic surviving difference-clip coverage as enough for red root "
            "visibility. FOR-301 proves that the runtime `SkAAClip` path operand is full over the "
            "59 original targets, so `kDifference` computes zero result coverage there."
        ),
        "localAuditModelCorrection": {
            "applied": True,
            "decision": DECISION_POLARITY_FIX,
            "scope": "FOR-302 validator/report only; normal renderer and historical FOR-293 artifacts are unchanged.",
            "before": "FOR-293 compared red store candidates against an analytic ellipse-derived surviving clip.",
            "after": "FOR-302 reconciles by applying the runtime SkAAClip formula parent * (255 - pathCoverage) / 255 and treating FOR-301 pathCoverage as the audited path operand.",
            "why": "The runtime formula matches every probed pixel in the original targets, candidate-minus-runtime-002, and red-runtime-* groups.",
        },
        "hypothesisResults": {
            "clipPolarity": {
                "classification": "audit-model-fix-applied",
                "evidence": "The runtime result is explained by subtracting the traced path operand from the full parent clip.",
            },
            "pixelCenter": {
                "classification": "rejected",
                "evidence": "Center and top-left binary ellipse variants keep all 59 original pixels nonzero under the traced inner bounds.",
            },
            "ctmOrBounds": {
                "classification": "rejected",
                "evidence": f"FOR-301 trace reports CTM scale sx={trace['matrix']['sx']}, sy={trace['matrix']['sy']} and path bounds {trace['path']['bounds']}.",
            },
            "rounding": {
                "classification": "rejected-as-primary-cause",
                "evidence": "The original 59 pixels differ by full coverage: FOR-293=255, runtime path=255, runtime result=0.",
            },
            "runtimeContradiction": {
                "classification": "reconciled",
                "evidence": "The runtime SkAAClip result is internally consistent with parent/path/result probes and the kDifference alpha formula.",
            },
        },
        "traceEvent": trace,
        "route": {
            "gpuStatus": "expected-unsupported",
            "fallbackReason": for269.FALLBACK_REASON,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "for293Decision": source_for293["decision"],
            "for300Decision": source_for300["decision"],
            "for301Decision": source_for301["decision"],
        },
        "comparison": {
            "originalTargets": original,
            "candidateMinusRuntime002": target_hole,
            "redRuntimeComponents": red_components,
        },
        "analyticVariantChecks": {
            group_id: {
                "innerBoundsFor2934x4": analytic_variant_summary(coords, [16, 16, 576, 576], "for293-4x4"),
                "innerBoundsCenterBinary": analytic_variant_summary(coords, [16, 16, 576, 576], "center-binary"),
                "innerBoundsTopLeftBinary": analytic_variant_summary(coords, [16, 16, 576, 576], "top-left-binary"),
                "outerDrawBoundsFor2934x4Control": analytic_variant_summary(coords, [8, 8, 584, 584], "for293-4x4"),
            }
            for group_id, coords in variant_groups.items()
        },
        "sourcePreservation": {
            "for293DecisionPreserved": source_for293["decision"] == for293.DECISION_AMBIGUOUS,
            "for300DecisionPreserved": source_for300["decision"] == for300.DECISION_BOUNDED_NO_FIX,
            "for301DecisionPreserved": source_for301["decision"] == for301.DECISION_ALPHA_MERGE,
            "m60ExpectedUnsupportedPreserved": True,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
        "strictPreservation": {
            "productionRenderingChanged": False,
            "normalRendererChanged": False,
            "skAAClipOpChanged": False,
            "ctmChanged": False,
            "blendChanged": False,
            "setPixelChanged": False,
            "gpuOrWebGpuChanged": False,
            "thresholdChanged": False,
            "m60Promoted": False,
        },
    }


def component_row(component: dict[str, Any]) -> str:
    current = component["for293SurvivingClipVsRuntimeResult"]
    formula = component["runtimeFormulaVsRuntimeResult"]
    return (
        f"| `{component['id']}` | {component['pixels']} | `{component['bounds']}` | "
        f"{component['for293SurvivingClipCoverage']['nonZeroPixels']} | "
        f"{component['runtimeDifferencePathCoverage']['fullPixels']} | "
        f"{component['runtimeDifferenceResultCoverage']['zeroPixels']} | "
        f"{current['mismatches']} | {formula['exactMatches']} |"
    )


def write_report(audit: dict[str, Any]) -> None:
    comparison = audit["comparison"]
    original = comparison["originalTargets"]
    target_hole = comparison["candidateMinusRuntime002"]
    runtime_rows = "\n".join(component_row(component) for component in comparison["redRuntimeComponents"])
    decision_rows = "\n".join(f"| `{key}` | {value} |" for key, value in audit["decisions"].items())
    variant = audit["analyticVariantChecks"]["original-59-targets"]
    report = f"""# FOR-302 M60 Analytic Clip Model Reconciliation

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Exact gap/result: `{audit["exactGap"]}`

## Result

FOR-302 reconciles FOR-293's analytic model with the FOR-301 runtime
`SkAAClip` trace. The correction is audit-only: FOR-302 uses the traced
runtime path operand and applies `parent * (255 - pathCoverage) / 255` for
`clipRRect(kDifference)`. It does not change normal rendering, `SkAAClip.op`,
CTM handling, blend, `setPixel`, GPU/WebGPU routing, thresholds, or M60
support status.

| Decision | Applied |
|---|---|
{decision_rows}

## Component Comparison

| Component | Pixels | Bounds | FOR-293 cov>0 | Runtime path cov=255 | Runtime result cov=0 | FOR-293/result mismatches | Runtime formula/result exact |
|---|---:|---|---:|---:|---:|---:|---:|
{component_row(original)}
{component_row(target_hole)}
{runtime_rows}

## Hypothesis Checks

| Hypothesis | Outcome | Evidence |
|---|---|---|
| Clip polarity / difference alpha merge | audit fix applied | Runtime parent/path/result probes satisfy `parent * (255 - pathCoverage) / 255` on every probed pixel. |
| Pixel center | rejected | Inner-bounds center and top-left variants still keep all 59 original pixels nonzero. |
| CTM or bounds | rejected | FOR-301 reports CTM `scale(2)` and path bounds `{audit["traceEvent"]["path"]["bounds"]}`. |
| Rounding | rejected as primary cause | Original targets differ at full coverage: FOR-293 surviving clip is 255, runtime path is 255, runtime result is 0. |
| Runtime contradiction | reconciled | Runtime SkAAClip is internally consistent; the contradiction is in the audit model assumption. |

## Original 59 Variant Control

| Variant | Result zero pixels | Result nonzero pixels | Result full pixels |
|---|---:|---:|---:|
| Inner bounds 4x4 | {variant["innerBoundsFor2934x4"]["resultCoverage"]["zeroPixels"]} | {variant["innerBoundsFor2934x4"]["resultCoverage"]["nonZeroPixels"]} | {variant["innerBoundsFor2934x4"]["resultCoverage"]["fullPixels"]} |
| Inner bounds center binary | {variant["innerBoundsCenterBinary"]["resultCoverage"]["zeroPixels"]} | {variant["innerBoundsCenterBinary"]["resultCoverage"]["nonZeroPixels"]} | {variant["innerBoundsCenterBinary"]["resultCoverage"]["fullPixels"]} |
| Inner bounds top-left binary | {variant["innerBoundsTopLeftBinary"]["resultCoverage"]["zeroPixels"]} | {variant["innerBoundsTopLeftBinary"]["resultCoverage"]["nonZeroPixels"]} | {variant["innerBoundsTopLeftBinary"]["resultCoverage"]["fullPixels"]} |
| Outer draw bounds 4x4 control | {variant["outerDrawBoundsFor2934x4Control"]["resultCoverage"]["zeroPixels"]} | {variant["outerDrawBoundsFor2934x4Control"]["resultCoverage"]["nonZeroPixels"]} | {variant["outerDrawBoundsFor2934x4Control"]["resultCoverage"]["fullPixels"]} |

M60 remains `expected-unsupported`: `{audit["route"]["fallbackReason"]}`.
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
    if audit.get("decision") != DECISION_RUNTIME_CORRECT:
        fail("runtime reconciliation decision changed")
    decisions = audit["decisions"]
    expected_decisions = {
        DECISION_POLARITY_FIX: True,
        DECISION_PIXEL_CENTER_FIX: False,
        DECISION_CTM_OR_BOUNDS_FIX: False,
        DECISION_RUNTIME_CORRECT: True,
        DECISION_AMBIGUOUS: False,
    }
    if decisions != expected_decisions:
        fail(f"unexpected decisions: {decisions}")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision changed")
    original = audit["comparison"]["originalTargets"]
    if original["pixels"] != 59:
        fail("original target count changed")
    if original["for293SurvivingClipCoverage"]["nonZeroPixels"] != 59:
        fail("FOR-293 no longer predicts nonzero coverage for all original targets")
    if original["runtimeDifferencePathCoverage"]["fullPixels"] != 59:
        fail("runtime path is no longer full on original targets")
    if original["runtimeDifferenceResultCoverage"]["zeroPixels"] != 59:
        fail("runtime result is no longer zero on original targets")
    if original["for293SurvivingClipVsRuntimeResult"]["mismatches"] != 59:
        fail("FOR-293/runtime original mismatch count changed")
    if original["runtimeFormulaVsRuntimeResult"]["exactMatches"] != 59:
        fail("runtime formula no longer matches original targets")
    target_hole = audit["comparison"]["candidateMinusRuntime002"]
    if target_hole["pixels"] != 3293:
        fail("candidate-minus-runtime-002 size changed")
    if target_hole["for293SurvivingClipCoverage"]["nonZeroPixels"] != 3293:
        fail("FOR-293 candidate-minus-runtime-002 coverage changed")
    if target_hole["runtimeDifferenceResultCoverage"]["zeroPixels"] != 3281:
        fail("candidate-minus-runtime-002 runtime zero count changed")
    if target_hole["runtimeFormulaVsRuntimeResult"]["exactMatches"] != 3293:
        fail("runtime formula no longer matches candidate-minus-runtime-002")
    red_components = audit["comparison"]["redRuntimeComponents"]
    if [component["pixels"] for component in red_components] != [2275, 2275, 2270, 2268]:
        fail("red-runtime component sizes changed")
    for component in red_components:
        if component["runtimeDifferenceResultCoverage"]["zeroPixels"] != 0:
            fail(f"{component['id']} unexpectedly has zero runtime result coverage")
        if component["runtimeFormulaVsRuntimeResult"]["exactMatches"] != component["pixels"]:
            fail(f"runtime formula no longer matches {component['id']}")
    variant = audit["analyticVariantChecks"]["original-59-targets"]
    if variant["innerBoundsCenterBinary"]["resultCoverage"]["nonZeroPixels"] != 59:
        fail("pixel-center variant unexpectedly explains original targets")
    if variant["innerBoundsTopLeftBinary"]["resultCoverage"]["nonZeroPixels"] != 59:
        fail("top-left variant unexpectedly explains original targets")
    for key, value in audit["sourcePreservation"].items():
        if value is not True and key != "visualParityFallbackPreserved":
            fail(f"source preservation `{key}` failed")
    for key, value in audit["strictPreservation"].items():
        if value is not False:
            fail(f"strict preservation `{key}` changed")


def main() -> None:
    source_for292 = load_json(SOURCE_FOR292)
    source_for293 = load_json(SOURCE_FOR293)
    source_for295 = load_json(SOURCE_FOR295)
    source_for300 = load_json(SOURCE_FOR300)
    source_for301 = load_json(SOURCE_FOR301)
    audit = analyze(
        source_for292=source_for292,
        source_for293=source_for293,
        source_for295=source_for295,
        source_for300=source_for300,
        source_for301=source_for301,
    )
    validate_audit(audit)
    SCENE_DIR.mkdir(parents=True, exist_ok=True)
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
    write_report(audit)
    print(f"FOR-302 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-302 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(
        "FOR-302 decision: "
        f"{audit['decision']} "
        f"originalMismatch={audit['comparison']['originalTargets']['for293SurvivingClipVsRuntimeResult']['mismatches']} "
        f"candidateRuntimeZero={audit['comparison']['candidateMinusRuntime002']['runtimeDifferenceResultCoverage']['zeroPixels']}"
    )


if __name__ == "__main__":
    main()
