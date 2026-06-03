#!/usr/bin/env python3
"""Generate and validate FOR-293 M60 red drawRRect runtime-visibility audit."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any

import numpy as np

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for271_nested_rrect_blurred_envelope_audit as for271
import validate_for280_cpu_aa_difference_clip_coverage_edge as for280
import validate_for281_cpu_mask_filter_clip_coverage_trace as for281
import validate_for286_cpu_active_aa_difference_store_trace as for286
import validate_for288_m60_outer_boundary_store_order_audit as for288
import validate_for289_m60_outer_boundary_runtime_write_chronology as for289
import validate_for290_m60_expanded_runtime_write_trace as for290
import validate_for291_m60_reconstructed_store_model_audit as for291
import validate_for292_m60_source_payload_derivation_audit as for292


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-293"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
SOURCE_FOR286 = for288.SOURCE_FOR286
SOURCE_FOR288 = for290.SOURCE_FOR288
SOURCE_FOR289 = for290.SOURCE_FOR289
SOURCE_FOR290 = for290.AUDIT
SOURCE_FOR291 = for291.AUDIT
SOURCE_FOR292 = for292.AUDIT
AUDIT_NAME = "m60-red-drawrrect-runtime-visibility-audit-for293.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-293-m60-red-drawrrect-runtime-visibility-audit.md"
GM_SOURCE = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
TRACE_TEST_SOURCE = (
    PROJECT_ROOT
    / "skia-integration-tests/src/test/kotlin/org/skia/tests/For290M60ExpandedRuntimeWriteTraceTest.kt"
)

DECISION_RUNTIME_OUTSIDE = "RED_DRAWRRECT_RUNTIME_DISPATCH_OUTSIDE_TARGET_PIXELS"
DECISION_CLIPPED = "RED_DRAWRRECT_CLIPPED_BEFORE_ROOT_DISPATCH"
DECISION_MASK_ZERO = "RED_DRAWRRECT_MASK_COVERAGE_ZERO_AT_TARGET_PIXELS"
DECISION_TARGET_MISMATCH = "RED_DRAWRRECT_RECONSTRUCTION_TARGET_PIXEL_SET_MISMATCH"
DECISION_AMBIGUOUS = "RED_DRAWRRECT_RUNTIME_VISIBILITY_STILL_AMBIGUOUS"
DECISIONS = {
    DECISION_RUNTIME_OUTSIDE,
    DECISION_CLIPPED,
    DECISION_MASK_ZERO,
    DECISION_TARGET_MISMATCH,
    DECISION_AMBIGUOUS,
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-293 validation failed: {message}")


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


def bounds_from_mask(mask: np.ndarray) -> dict[str, int] | None:
    ys, xs = np.nonzero(mask)
    if len(xs) == 0:
        return None
    return {
        "left": int(xs.min()),
        "top": int(ys.min()),
        "right": int(xs.max()) + 1,
        "bottom": int(ys.max()) + 1,
        "rightInclusive": int(xs.max()),
        "bottomInclusive": int(ys.max()),
    }


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


def key_xy(item: dict[str, Any]) -> tuple[int, int]:
    return (int(item["x"]), int(item["y"]))


def source_needles() -> dict[str, bool]:
    gm_text = GM_SOURCE.read_text(encoding="utf-8")
    cpu_text = CPU_SOURCE.read_text(encoding="utf-8")
    trace_test_text = TRACE_TEST_SOURCE.read_text(encoding="utf-8")
    return {
        "gmRedDrawRRectAfterWhiteDrawRect": (
            "c.drawRect(clipRect1, whitePaint)" in gm_text
            and "c.drawRRect(rr, paint)" in gm_text
            and gm_text.index("c.drawRect(clipRect1, whitePaint)") < gm_text.index("c.drawRRect(rr, paint)")
        ),
        "gmRedDrawRRectUsesBlurMaskFilter": "SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.366025f)" in gm_text,
        "gmRedDrawRRectUsesSrcInColorFilter": "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)" in gm_text,
        "gmHasNoSaveLayer": "saveLayer(" not in gm_text,
        "cpuA8SrcInPayloadTraceSourcePresent": (
            '"SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload"' in cpu_text
        ),
        "cpuBlendTraceFiltersByTargetPixels": "SkCpuWriteChronologyTrace.shouldTrace(x, y, width, height)" in cpu_text,
        "cpuTraceRecordFiltersByTargetPixels": "if (!enabled || Target(x, y) !in targetPixels) return" in cpu_text,
        "for290TraceTestRequiresExactly59Targets": "assertEquals(59, targetPixels.size" in trace_test_text,
        "for290TraceTestUsesRootDimensions": "width = size.width" in trace_test_text and "height = size.height" in trace_test_text,
        "for290TraceTestIncludesBitmapDirectWrites": "includeBitmapDirectWrites = true" in trace_test_text,
    }


def red_draw_runtime_domain(
    width: int,
    height: int,
    geometry: dict[str, Any],
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, dict[str, int]]:
    blurred_mask, mask_bounds = for281.generate_blurred_mask(width, height, geometry)
    mask_alpha = np.zeros((height, width), dtype=np.uint8)
    mask_alpha[mask_bounds["top"] : mask_bounds["bottom"], mask_bounds["left"] : mask_bounds["right"]] = blurred_mask
    clip_coverage = np.zeros((height, width), dtype=np.uint8)
    for y in range(mask_bounds["top"], mask_bounds["bottom"]):
        for x in range(mask_bounds["left"], mask_bounds["right"]):
            clip_coverage[y, x] = for280.difference_clip_coverage(x, y, geometry["differenceOvalDeviceBounds"])
    alpha_after_clip = ((mask_alpha.astype(np.int32) * clip_coverage.astype(np.int32) + 127) // 255)
    mask_nonzero = mask_alpha > 0
    visible_store_candidate = mask_nonzero & (clip_coverage > 0) & (alpha_after_clip > 0)
    return mask_alpha, clip_coverage, alpha_after_clip, visible_store_candidate, mask_bounds


def summarize_runtime_events(source_for290: dict[str, Any], target_keys: set[tuple[int, int]]) -> dict[str, Any]:
    events = source_for290.get("events", [])
    if not isinstance(events, list):
        fail("FOR-290 source events[] changed")
    event_coords = {key_xy(event) for event in events if isinstance(event, dict)}
    root_events = [event for event in events if isinstance(event, dict) and bool(event.get("rootDevice"))]
    root_coords = {key_xy(event) for event in root_events}
    root_dispatch_events = [
        event for event in root_events if str(event.get("source")) == "SkBitmapDevice.dispatchBlend"
    ]
    red_root_events = [
        event for event in root_events
        if red_tint(rgba(event.get("valueWrittenRgba")))
        or red_tint(rgba(event.get("srcInputRgba")))
        or "A8.srcInPayload" in str(event.get("source"))
        or str(event.get("mode")) == "kSrcOver"
    ]
    return {
        "eventCount": len(events),
        "rootEventCount": len(root_events),
        "rootDispatchEventCount": len(root_dispatch_events),
        "eventCoordinateCount": len(event_coords),
        "rootCoordinateCount": len(root_coords),
        "targetCoordinateCount": len(target_keys),
        "eventCoordinatesEqualTargets": event_coords == target_keys,
        "rootCoordinatesEqualTargets": root_coords == target_keys,
        "rootEventsOutsideTargets": sum(1 for event in root_events if key_xy(event) not in target_keys),
        "redOrKSrcOverRootEventsInCapturedTrace": len(red_root_events),
        "sourceCounts": dict(Counter(str(event.get("source")) for event in root_events)),
        "modeCounts": dict(Counter(str(event.get("mode")) for event in root_events)),
        "branchCounts": dict(Counter(str(event.get("branch")) for event in root_events)),
        "bounds": bounds_from_coords(root_coords),
    }


def analyze(
    *,
    source_for286: dict[str, Any],
    source_for288: dict[str, Any],
    source_for289: dict[str, Any],
    source_for290: dict[str, Any],
    source_for291: dict[str, Any],
    source_for292: dict[str, Any],
) -> dict[str, Any]:
    reference = for269.load_image("skia")
    cpu = for269.load_image("cpu")
    gpu = for269.load_image("gpu")
    if reference.shape != cpu.shape or reference.shape != gpu.shape:
        fail("reference, CPU, and GPU PNG dimensions differ")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA images")

    geometry, masks = for271.make_envelope_masks(width, height)
    mask_alpha, clip_coverage, alpha_after_clip, visible_candidate, mask_bounds = red_draw_runtime_domain(
        width,
        height,
        geometry,
    )

    pixels = source_for292.get("perPixelComparisons", [])
    if len(pixels) != 59:
        fail(f"FOR-292 per-pixel count changed: {len(pixels)}")
    target_keys = {key_xy(pixel) for pixel in pixels}
    if len(target_keys) != 59:
        fail("FOR-292 target coordinate set is not 59 unique pixels")

    target_mask = np.zeros((height, width), dtype=bool)
    for x, y in target_keys:
        target_mask[y, x] = True
    runtime = summarize_runtime_events(source_for290, target_keys)
    red_candidate_coords = {
        (int(x), int(y))
        for y, x in zip(*np.nonzero(visible_candidate))
    }
    target_candidates = target_keys & red_candidate_coords
    visible_outside_captured_runtime = red_candidate_coords - {
        key_xy(event)
        for event in source_for290.get("events", [])
        if isinstance(event, dict)
    }

    target_zero_mask = sum(1 for x, y in target_keys if int(mask_alpha[y, x]) == 0)
    target_zero_clip = sum(1 for x, y in target_keys if int(clip_coverage[y, x]) == 0)
    target_zero_after_clip = sum(1 for x, y in target_keys if int(alpha_after_clip[y, x]) == 0)
    target_full_clip = sum(1 for x, y in target_keys if int(clip_coverage[y, x]) == 255)
    target_red_reconstructed = sum(
        1
        for pixel in pixels
        if pixel["reconstructedDerivation"]["mappedDraw"] == "BlurredClippedCircleGM.c.drawRRect(rr, paint)"
        and pixel["reconstructedDerivation"]["maskFilterBranch"]
        == "SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload"
        and str(pixel["reconstructedDerivation"]["dispatchInputMode"]) == "SkBlendMode.kSrcOver"
    )

    decision = DECISION_AMBIGUOUS
    exact_gap = (
        "FOR-290's runtime trace is target-filtered to exactly the 59 outer-boundary pixels; "
        f"{len(visible_outside_captured_runtime)} reconstructed red drawRRect store-candidate pixels outside "
        "those captured coordinates have no root runtime visibility yet."
    )
    if target_keys != {key_xy(pixel) for pixel in source_for291.get("perPixelComparisons", [])}:
        decision = DECISION_TARGET_MISMATCH
        exact_gap = None
    elif target_zero_mask == 59 or target_zero_after_clip == 59:
        decision = DECISION_MASK_ZERO
        exact_gap = None
    elif target_zero_clip == 59:
        decision = DECISION_CLIPPED
        exact_gap = None
    elif runtime["redOrKSrcOverRootEventsInCapturedTrace"] > 0 and runtime["rootEventsOutsideTargets"] > 0:
        decision = DECISION_RUNTIME_OUTSIDE
        exact_gap = None

    source_needles_result = source_needles()
    red_domain = {
        "drawOvalDeviceBounds": geometry["drawOvalDeviceBounds"],
        "differenceOvalDeviceBounds": geometry["differenceOvalDeviceBounds"],
        "maskFilterBounds": mask_bounds,
        "maskNonZeroPixels": int((mask_alpha > 0).sum()),
        "maskNonZeroBounds": bounds_from_mask(mask_alpha > 0),
        "storeCandidatePixelsAfterClip": int(visible_candidate.sum()),
        "storeCandidateBoundsAfterClip": bounds_from_mask(visible_candidate),
        "storeCandidatePixelsOutsideCapturedRuntimeCoordinates": len(visible_outside_captured_runtime),
        "storeCandidateSubzones": {
            "difference_oval_inner_boundary": int((visible_candidate & masks["difference_oval_inner_boundary"]).sum()),
            "draw_oval_outer_boundary": int((visible_candidate & masks["draw_oval_outer_boundary"]).sum()),
            "halo_interior": int((visible_candidate & masks["halo_interior"]).sum()),
            "removed_difference_oval_interior": int((visible_candidate & masks["removed_difference_oval_interior"]).sum()),
            "outside_draw_oval": int((visible_candidate & masks["outside_draw_oval"]).sum()),
            "blurred_content_envelope": int((visible_candidate & masks["blurred_content_envelope"]).sum()),
        },
    }
    target_summary = {
        "targetPixels": len(target_keys),
        "targetBounds": bounds_from_coords(target_keys),
        "targetsInsideRedStoreCandidateDomain": len(target_candidates),
        "targetsWithNonZeroMask": 59 - target_zero_mask,
        "targetsWithZeroMask": target_zero_mask,
        "targetsWithFullClipCoverage": target_full_clip,
        "targetsWithZeroClipCoverage": target_zero_clip,
        "targetsWithNonZeroAlphaAfterClip": 59 - target_zero_after_clip,
        "targetsWithZeroAlphaAfterClip": target_zero_after_clip,
        "targetsMappedToRedDrawRRectA8SrcInKSrcOver": target_red_reconstructed,
        "runtimeComparableRootDispatchPixels": source_for292["comparisonSummary"]["runtimeRootDispatchPixels"],
        "runtimeComparableWhiteKSrcPayloadPixels": source_for292["comparisonSummary"]["runtimeWhiteKSrcPayloadPixels"],
    }
    anchor = next((pixel for pixel in pixels if (pixel["x"], pixel["y"]) == (99, 89)), pixels[0])
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-red-drawrrect-runtime-visibility-audit",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/reconstructed-red-domain-vs-target-filtered-runtime-root",
        "sourceAudits": {
            "for286": str(SOURCE_FOR286.relative_to(PROJECT_ROOT)),
            "for288": str(SOURCE_FOR288.relative_to(PROJECT_ROOT)),
            "for289": str(SOURCE_FOR289.relative_to(PROJECT_ROOT)),
            "for290": str(SOURCE_FOR290.relative_to(PROJECT_ROOT)),
            "for291": str(SOURCE_FOR291.relative_to(PROJECT_ROOT)),
            "for292": str(SOURCE_FOR292.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": decision,
        "exactGap": exact_gap,
        "firstDemonstratedDivergence": (
            "The red drawRRect reconstructed domain is much wider than the 59 captured runtime coordinates, "
            "but the only runtime-root trace currently available is hard-filtered to exactly those 59 pixels."
        ),
        "route": {
            "gpuStatus": "expected-unsupported",
            "fallbackReason": for269.FALLBACK_REASON,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
            "for286Decision": source_for286["decision"],
            "for288PrimaryClassification": source_for288["primaryClassification"],
            "for289Decision": source_for289["decision"],
            "for290Decision": source_for290["decision"],
            "for291Decision": source_for291["decision"],
            "for292Decision": source_for292["decision"],
            "sourceNeedles": source_needles_result,
        },
        "redDrawRRectDomain": red_domain,
        "targetPixelComparison": target_summary,
        "runtimeRootVisibility": runtime,
        "comparison": {
            "redCandidateDomainContainsAllTargets": len(target_candidates) == 59,
            "runtimeObservedOnlyTargetCoordinates": bool(runtime["eventCoordinatesEqualTargets"]),
            "runtimeRootObservedOnlyTargetCoordinates": bool(runtime["rootCoordinatesEqualTargets"]),
            "runtimeRedDrawRRectEventsOnCapturedTargets": runtime["redOrKSrcOverRootEventsInCapturedTrace"],
            "runtimeTraceCanAnswerElsewhereQuestion": False,
            "reasonRuntimeElsewhereCannotBeAnswered": (
                "For290M60ExpandedRuntimeWriteTraceTest asserts exactly 59 targets and "
                "SkCpuWriteChronologyTrace records only configured target pixels."
            ),
        },
        "anchorComparison": {
            "x": int(anchor["x"]),
            "y": int(anchor["y"]),
            "redReconstructed": anchor["reconstructedDerivation"],
            "runtimeRoot": anchor["runtimeRootDerivation"],
            "maskAAfterBlur": int(mask_alpha[int(anchor["y"]), int(anchor["x"])]),
            "clipCoverage": int(clip_coverage[int(anchor["y"]), int(anchor["x"])]),
            "alphaAfterClip": int(alpha_after_clip[int(anchor["y"]), int(anchor["x"])]),
        },
        "sourcePreservation": {
            "for286DecisionPreserved": source_for286["decision"] == for286.DECISION,
            "for288ClassificationPreserved": (
                source_for288["primaryClassification"] == "OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE"
            ),
            "for289DecisionPreserved": source_for289["decision"] == "PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP",
            "for290DecisionPreserved": source_for290["decision"] == "NO_RUNTIME_RED_ROOT_STORE_FOUND",
            "for291DecisionPreserved": source_for291["decision"] == "RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH",
            "for292DecisionPreserved": (
                source_for292["decision"] == "RECONSTRUCTED_PAYLOAD_DRAW_MAPPING_MISMATCH"
            ),
        },
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRendererChanged": False,
            "cpuRendererChanged": False,
            "cpuInstrumentationAdded": False,
            "gpuRendererChanged": False,
            "globalBlendChanged": False,
            "setPixelSemanticsChanged": False,
            "for288ClassificationChanged": False,
            "for289DecisionChanged": False,
            "for290DecisionChanged": False,
            "for291DecisionChanged": False,
            "for292DecisionChanged": False,
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def write_report(audit: dict[str, Any]) -> None:
    red = audit["redDrawRRectDomain"]
    target = audit["targetPixelComparison"]
    runtime = audit["runtimeRootVisibility"]
    anchor = audit["anchorComparison"]
    source_rows = "\n".join(
        f"| `{source}` | {count} |" for source, count in sorted(runtime["sourceCounts"].items())
    )
    subzone_rows = "\n".join(
        f"| `{name}` | {count} |" for name, count in red["storeCandidateSubzones"].items()
    )
    report = f"""# FOR-293 M60 Red drawRRect Runtime Visibility Audit

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Exact gap: `{audit["exactGap"] or "none"}`

## Result

FOR-293 audits the visibility question left by FOR-292: the 59
`draw_oval_outer_boundary` pixels are reconstructed from the red blurred
`drawRRect` path, but the comparable runtime root dispatch is still the
white `drawRect` path. The red reconstructed domain is not zero-masked or
fully clipped at the target pixels: all 59 targets have non-zero blur mask,
full clip coverage, and non-zero alpha after clip.

The current runtime evidence cannot prove whether the red `drawRRect`
emits root dispatches elsewhere, because the FOR-290 trace harness is
hard-filtered to exactly the 59 target pixels. The reconstructed red
store-candidate domain contains {red["storeCandidatePixelsAfterClip"]}
pixels after mask and clip, leaving
{red["storeCandidatePixelsOutsideCapturedRuntimeCoordinates"]} candidate
pixels outside the captured runtime coordinate set.

| Measure | Value |
|---|---:|
| Target pixels | {target["targetPixels"]} |
| Red store candidates after mask/clip | {red["storeCandidatePixelsAfterClip"]} |
| Red candidates outside captured runtime coordinates | {red["storeCandidatePixelsOutsideCapturedRuntimeCoordinates"]} |
| Targets inside red candidate domain | {target["targetsInsideRedStoreCandidateDomain"]} |
| Targets with non-zero mask | {target["targetsWithNonZeroMask"]} |
| Targets with full clip coverage | {target["targetsWithFullClipCoverage"]} |
| Targets with non-zero alpha after clip | {target["targetsWithNonZeroAlphaAfterClip"]} |
| Targets mapped to red drawRRect A8 SrcIn kSrcOver | {target["targetsMappedToRedDrawRRectA8SrcInKSrcOver"]} |
| Runtime root events captured | {runtime["rootEventCount"]} |
| Runtime root dispatch events captured | {runtime["rootDispatchEventCount"]} |
| Runtime red/kSrcOver events in captured trace | {runtime["redOrKSrcOverRootEventsInCapturedTrace"]} |
| Runtime root events outside targets in captured trace | {runtime["rootEventsOutsideTargets"]} |

## Bounds And Coverage

| Field | Bounds |
|---|---|
| Draw oval device bounds | `{red["drawOvalDeviceBounds"]}` |
| Difference oval device bounds | `{red["differenceOvalDeviceBounds"]}` |
| Mask-filter bounds | `{red["maskFilterBounds"]}` |
| Red mask non-zero bounds | `{red["maskNonZeroBounds"]}` |
| Red store-candidate bounds after clip | `{red["storeCandidateBoundsAfterClip"]}` |
| Target bounds | `{target["targetBounds"]}` |
| Captured runtime root bounds | `{runtime["bounds"]}` |

## Red Candidate Subzones

| Subzone | Pixels |
|---|---:|
{subzone_rows}

## Captured Runtime Root Counts

| Source | Events |
|---|---:|
{source_rows}

The captured runtime coordinates equal the 59 target coordinates:
`{runtime["eventCoordinatesEqualTargets"]}`. This proves the captured trace
does not include an outside-target search area.

## Anchor Pixel

| Field | Red reconstructed path | Runtime root path |
|---|---|---|
| Pixel | `{anchor["x"]},{anchor["y"]}` | `{anchor["x"]},{anchor["y"]}` |
| Draw | `{anchor["redReconstructed"]["mappedDraw"]}` | `{anchor["runtimeRoot"]["mappedDraw"]}` |
| Source | `{anchor["redReconstructed"]["dispatchInputSrcRgba"]}` | `{anchor["runtimeRoot"]["srcInputRgba"]}` |
| Mode | `{anchor["redReconstructed"]["dispatchInputMode"]}` | `{anchor["runtimeRoot"]["mode"]}` |
| Mask alpha | {anchor["maskAAfterBlur"]} | n/a |
| Clip coverage | {anchor["clipCoverage"]} | {anchor["runtimeRoot"]["coverage"]} |
| Alpha after clip | {anchor["alphaAfterClip"]} | n/a |
| Branch | `{anchor["redReconstructed"]["writeBranch"]}` | `{anchor["runtimeRoot"]["branch"]}` |

## Preserved Decisions

- FOR-288 classification: `{audit["route"]["for288PrimaryClassification"]}`
- FOR-289 decision: `{audit["route"]["for289Decision"]}`
- FOR-290 decision: `{audit["route"]["for290Decision"]}`
- FOR-291 decision: `{audit["route"]["for291Decision"]}`
- FOR-292 decision: `{audit["route"]["for292Decision"]}`

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
    source_for286: dict[str, Any],
    source_for288: dict[str, Any],
    source_for289: dict[str, Any],
    source_for290: dict[str, Any],
    source_for291: dict[str, Any],
    source_for292: dict[str, Any],
) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("wrong Linear id")
    if audit.get("parent") != PARENT_ID:
        fail("wrong parent id")
    if audit.get("sceneId") != SCENE_ID:
        fail("wrong scene id")
    if audit.get("decision") not in DECISIONS:
        fail("invalid FOR-293 decision")
    if audit.get("decision") == DECISION_AMBIGUOUS and not audit.get("exactGap"):
        fail("ambiguous decision must name the exact gap")
    if audit.get("decision") != DECISION_AMBIGUOUS and audit.get("exactGap") is not None:
        fail("resolved decision should not carry an unresolved exact gap")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision changed")

    if source_for286["decision"] != for286.DECISION:
        fail("FOR-286 source decision changed")
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

    route = audit["route"]
    for key, value in route["sourceNeedles"].items():
        if value is not True:
            fail(f"source needle `{key}` failed")
    if route["visualParityFallbackPreserved"] != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route["cropFallbackPreserved"] != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")

    red = audit["redDrawRRectDomain"]
    expected_red = {
        "maskNonZeroPixels": 267896,
        "storeCandidatePixelsAfterClip": 22424,
        "storeCandidatePixelsOutsideCapturedRuntimeCoordinates": 22365,
    }
    for key, expected in expected_red.items():
        if red.get(key) != expected:
            fail(f"red domain `{key}` changed: {red.get(key)} != {expected}")
    if red["drawOvalDeviceBounds"] != [8, 8, 584, 584]:
        fail("draw oval device bounds changed")
    if red["differenceOvalDeviceBounds"] != [16, 16, 576, 576]:
        fail("difference oval device bounds changed")
    if red["maskFilterBounds"] != {"left": 2, "top": 2, "right": 590, "bottom": 590, "margin": 5}:
        fail("mask-filter bounds changed")

    target = audit["targetPixelComparison"]
    expected_target = {
        "targetPixels": 59,
        "targetsInsideRedStoreCandidateDomain": 59,
        "targetsWithNonZeroMask": 59,
        "targetsWithZeroMask": 0,
        "targetsWithFullClipCoverage": 59,
        "targetsWithZeroClipCoverage": 0,
        "targetsWithNonZeroAlphaAfterClip": 59,
        "targetsWithZeroAlphaAfterClip": 0,
        "targetsMappedToRedDrawRRectA8SrcInKSrcOver": 59,
        "runtimeComparableRootDispatchPixels": 59,
        "runtimeComparableWhiteKSrcPayloadPixels": 59,
    }
    for key, expected in expected_target.items():
        if target.get(key) != expected:
            fail(f"target summary `{key}` changed: {target.get(key)} != {expected}")

    runtime = audit["runtimeRootVisibility"]
    expected_runtime = {
        "eventCount": 118,
        "rootEventCount": 118,
        "rootDispatchEventCount": 59,
        "eventCoordinateCount": 59,
        "rootCoordinateCount": 59,
        "targetCoordinateCount": 59,
        "rootEventsOutsideTargets": 0,
        "redOrKSrcOverRootEventsInCapturedTrace": 0,
    }
    for key, expected in expected_runtime.items():
        if runtime.get(key) != expected:
            fail(f"runtime summary `{key}` changed: {runtime.get(key)} != {expected}")
    if runtime["sourceCounts"] != {"SkBitmap.eraseColor": 59, "SkBitmapDevice.dispatchBlend": 59}:
        fail("runtime source counts changed")
    if runtime["modeCounts"] != {"direct": 59, "kSrc": 59}:
        fail("runtime mode counts changed")
    if runtime["eventCoordinatesEqualTargets"] is not True:
        fail("FOR-290 runtime events no longer equal target coordinate set")
    if runtime["rootCoordinatesEqualTargets"] is not True:
        fail("FOR-290 runtime root events no longer equal target coordinate set")

    comparison = audit["comparison"]
    if comparison["redCandidateDomainContainsAllTargets"] is not True:
        fail("red candidate domain no longer contains all targets")
    if comparison["runtimeTraceCanAnswerElsewhereQuestion"] is not False:
        fail("FOR-293 should remain explicit about the trace visibility gap")
    if audit["decision"] != DECISION_AMBIGUOUS:
        fail(f"FOR-293 expected {DECISION_AMBIGUOUS}, got {audit['decision']}")

    preservation = audit["strictPreservation"]
    for key in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "productionRendererChanged",
        "cpuRendererChanged",
        "cpuInstrumentationAdded",
        "gpuRendererChanged",
        "globalBlendChanged",
        "setPixelSemanticsChanged",
        "for288ClassificationChanged",
        "for289DecisionChanged",
        "for290DecisionChanged",
        "for291DecisionChanged",
        "for292DecisionChanged",
        "m60Promoted",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if preservation.get(key) is not False:
            fail(f"strict preservation `{key}` changed")


def main() -> None:
    source_for286 = load_json(SOURCE_FOR286)
    source_for288 = load_json(SOURCE_FOR288)
    source_for289 = load_json(SOURCE_FOR289)
    source_for290 = load_json(SOURCE_FOR290)
    source_for291 = load_json(SOURCE_FOR291)
    source_for292 = load_json(SOURCE_FOR292)
    for286.validate(source_for286)
    for288.validate_audit(source_for288)
    for289.validate_audit(source_for289, source_for288)
    for290.validate_audit(source_for290, source_for288, source_for289)
    for291.validate_audit(source_for291, source_for286, source_for288, source_for289, source_for290)
    for292.validate_audit(source_for292, source_for286, source_for288, source_for289, source_for290, source_for291)

    audit = analyze(
        source_for286=source_for286,
        source_for288=source_for288,
        source_for289=source_for289,
        source_for290=source_for290,
        source_for291=source_for291,
        source_for292=source_for292,
    )
    validate_audit(audit, source_for286, source_for288, source_for289, source_for290, source_for291, source_for292)
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
    write_report(audit)
    reread = load_json(AUDIT)
    validate_audit(reread, source_for286, source_for288, source_for289, source_for290, source_for291, source_for292)
    report_text = REPORT.read_text(encoding="utf-8")
    for needle in (
        audit["decision"],
        "draw_oval_outer_boundary",
        "FOR-292 decision",
        "Red store candidates after mask/clip",
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
        "No\nproduction renderer",
    ):
        if needle not in report_text:
            fail(f"report missing `{needle}`")
    print(f"FOR-293 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-293 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"Decision: {audit['decision']}")


if __name__ == "__main__":
    main()
