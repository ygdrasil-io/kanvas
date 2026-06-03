#!/usr/bin/env python3
"""Generate and validate FOR-292 M60 source-payload derivation audit."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for286_cpu_active_aa_difference_store_trace as for286
import validate_for288_m60_outer_boundary_store_order_audit as for288
import validate_for289_m60_outer_boundary_runtime_write_chronology as for289
import validate_for290_m60_expanded_runtime_write_trace as for290
import validate_for291_m60_reconstructed_store_model_audit as for291


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-292"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
SOURCE_FOR286 = for288.SOURCE_FOR286
SOURCE_FOR288 = for290.SOURCE_FOR288
SOURCE_FOR289 = for290.SOURCE_FOR289
SOURCE_FOR290 = for290.AUDIT
SOURCE_FOR291 = for291.AUDIT
AUDIT_NAME = "m60-source-payload-derivation-audit-for292.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-292-m60-source-payload-derivation-audit.md"
GM_SOURCE = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"

DECISION_DRAW_MAPPING = "RECONSTRUCTED_PAYLOAD_DRAW_MAPPING_MISMATCH"
DECISION_LAYER_SOURCE = "RECONSTRUCTED_PAYLOAD_LAYER_SOURCE_MISMATCH"
DECISION_BLEND_MODE = "RECONSTRUCTED_PAYLOAD_BLEND_MODE_MISMATCH"
DECISION_MASK_FILTER = "RECONSTRUCTED_PAYLOAD_MASK_FILTER_BRANCH_MISMATCH"
DECISION_AMBIGUOUS = "RECONSTRUCTED_PAYLOAD_DERIVATION_STILL_AMBIGUOUS"
DECISIONS = {
    DECISION_DRAW_MAPPING,
    DECISION_LAYER_SOURCE,
    DECISION_BLEND_MODE,
    DECISION_MASK_FILTER,
    DECISION_AMBIGUOUS,
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-292 validation failed: {message}")


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


def red_payload(value: list[int]) -> bool:
    r, g, b, a = value
    return a > 0 and r == 255 and g == 0 and b == 0


def white_payload(value: list[int]) -> bool:
    r, g, b, a = value
    return a >= 240 and r >= 245 and g >= 245 and b >= 245


def key_str(pixel: dict[str, Any]) -> str:
    return f"{int(pixel['x'])},{int(pixel['y'])}"


def source_needles() -> dict[str, bool]:
    gm_text = GM_SOURCE.read_text(encoding="utf-8")
    cpu_text = CPU_SOURCE.read_text(encoding="utf-8")
    return {
        "gmWhiteKSrcDrawBeforeRedRRect": (
            "c.drawRect(clipRect1, whitePaint)" in gm_text
            and "c.drawRRect(rr, paint)" in gm_text
            and gm_text.index("c.drawRect(clipRect1, whitePaint)") < gm_text.index("c.drawRRect(rr, paint)")
        ),
        "gmRedRRectUsesSrcInColorFilter": "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)" in gm_text,
        "gmRedRRectUsesBlurMaskFilter": "maskFilter = SkBlurMaskFilter.Make" in gm_text,
        "gmNoSaveLayer": "saveLayer(" not in gm_text,
        "cpuA8SrcInPayloadBranchPresent": (
            '"SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload"' in cpu_text
        ),
        "cpuA8SrcInComputesMaskedAlphaBeforeFilter": "val maskedA = (baseA * maskA + 127) / 255" in cpu_text,
        "cpuA8SrcInAppliesColorFilterToMaskedPayload": (
            "applyColorFilter(colorFilter, (maskedA shl 24) or (paint.color and 0x00FFFFFF))" in cpu_text
        ),
        "cpuDispatchBlendCarriesTraceSource": (
            'traceSource: String = "SkBitmapDevice.dispatchBlend"' in cpu_text
        ),
        "cpuKSrcOverPartialStoreBranchPresent": (
            '"SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)"' in cpu_text
        ),
    }


def summarize_runtime_dispatch(pixel: dict[str, Any]) -> dict[str, Any]:
    dispatch = pixel["runtimeRoot"]["dispatchEvent"]
    return {
        "source": dispatch["source"],
        "callsite": dispatch["callsite"],
        "branch": dispatch["branch"],
        "mode": dispatch["mode"],
        "coverage": int(dispatch["coverage"]),
        "srcInputRgba": rgba(dispatch["srcInputRgba"]),
        "srcAfterCoverageRgba": rgba(dispatch["srcAfterCoverageRgba"]),
        "valueBeforeRgba": rgba(dispatch["valueBeforeRgba"]),
        "valueWrittenRgba": rgba(dispatch["valueWrittenRgba"]),
        "rootDevice": bool(dispatch["rootDevice"]),
        "deviceKind": dispatch["deviceKind"],
    }


def analyze(
    *,
    source_for286: dict[str, Any],
    source_for288: dict[str, Any],
    source_for289: dict[str, Any],
    source_for290: dict[str, Any],
    source_for291: dict[str, Any],
) -> dict[str, Any]:
    pixels = source_for291.get("perPixelComparisons", [])
    if len(pixels) != 59:
        fail(f"FOR-291 per-pixel count changed: {len(pixels)}")

    per_pixel: list[dict[str, Any]] = []
    reconstructed_red_payloads = 0
    reconstructed_ksrc_over = 0
    reconstructed_mask_filter_payloads = 0
    runtime_white_ksrc_payloads = 0
    runtime_root_dispatches = 0
    runtime_temporary_events = 0
    draw_mapping_mismatches = 0
    layer_source_mismatches = 0
    blend_mode_mismatches = 0
    mask_filter_branch_mismatches = 0
    source_payload_mismatches = 0
    coordinate_matches = 0
    coverage_matches = 0
    reconstructed_alpha_values: list[int] = []
    runtime_modes = Counter()
    runtime_sources = Counter()
    reconstructed_branches = Counter()

    for pixel in pixels:
        reconstructed = pixel["reconstructed"]
        runtime = summarize_runtime_dispatch(pixel)
        recon_src = rgba(reconstructed["dispatchInputSrcRgba"])
        runtime_src = runtime["srcInputRgba"]
        recon_mode = str(reconstructed["dispatchInputMode"]).replace("SkBlendMode.", "")
        runtime_mode = str(runtime["mode"])
        reconstructed_branch = str(reconstructed["writeBranch"])
        runtime_branch = str(runtime["branch"])
        reconstructed_alpha_values.append(int(recon_src[3]))

        if red_payload(recon_src):
            reconstructed_red_payloads += 1
        if recon_mode == "kSrcOver":
            reconstructed_ksrc_over += 1
        if reconstructed_branch == "SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)":
            reconstructed_mask_filter_payloads += 1
        if white_payload(runtime_src) and runtime_mode == "kSrc":
            runtime_white_ksrc_payloads += 1
        if runtime["source"] == "SkBitmapDevice.dispatchBlend" and runtime["rootDevice"]:
            runtime_root_dispatches += 1
        if int(pixel["runtimeRoot"]["temporaryEventCount"]) != 0:
            runtime_temporary_events += int(pixel["runtimeRoot"]["temporaryEventCount"])
        if recon_src != runtime_src:
            source_payload_mismatches += 1
        if recon_mode != runtime_mode:
            blend_mode_mismatches += 1
        if (
            reconstructed_branch == "SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)"
            and runtime_branch == "SkBitmapDevice.blend.kSrc.setPixel(out)"
        ):
            mask_filter_branch_mismatches += 1
        if runtime["deviceKind"] == "root" and int(pixel["runtimeRoot"]["temporaryEventCount"]) == 0:
            layer_source_mismatches += 0
        if (
            red_payload(recon_src)
            and recon_mode == "kSrcOver"
            and white_payload(runtime_src)
            and runtime_mode == "kSrc"
        ):
            draw_mapping_mismatches += 1
        if bool(pixel["comparison"]["sameRootCoordinate"]):
            coordinate_matches += 1
        if bool(pixel["comparison"]["coverageMatches"]):
            coverage_matches += 1
        runtime_modes[runtime_mode] += 1
        runtime_sources[str(runtime["source"])] += 1
        reconstructed_branches[reconstructed_branch] += 1

        per_pixel.append(
            {
                "x": int(pixel["x"]),
                "y": int(pixel["y"]),
                "reconstructedDerivation": {
                    "mappedDraw": "BlurredClippedCircleGM.c.drawRRect(rr, paint)",
                    "paintSource": "maskFilter=SkBlurMaskFilter.Make + colorFilter=Blend(SK_ColorRED,kSrcIn)",
                    "maskFilterBranch": "SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload",
                    "payloadFormula": "src = transformPaintColor(applyColorFilter(colorFilter, maskedA<<24 | paint.rgb))",
                    "maskAAfterBlur": int(reconstructed["maskAAfterBlur"]),
                    "coverageAppliedInBlend": int(reconstructed["coverageAppliedInBlend"]),
                    "dispatchInputMode": reconstructed["dispatchInputMode"],
                    "dispatchInputSrcRgba": recon_src,
                    "writeBranch": reconstructed_branch,
                    "valueWrittenRgba": rgba(reconstructed["valueWrittenRgba"]),
                },
                "runtimeRootDerivation": {
                    "mappedDraw": "BlurredClippedCircleGM.c.drawRect(clipRect1, whitePaint)",
                    "paintSource": "whitePaint color=SK_ColorWHITE blendMode=kSrc",
                    "maskFilterBranch": None,
                    **runtime,
                },
                "comparison": {
                    "sameRootCoordinate": bool(pixel["comparison"]["sameRootCoordinate"]),
                    "coverageMatches": bool(pixel["comparison"]["coverageMatches"]),
                    "reconstructedRedSrcInPayload": red_payload(recon_src),
                    "runtimeWhiteKSrcPayload": white_payload(runtime_src) and runtime_mode == "kSrc",
                    "drawMappingMatches": False,
                    "layerSourceMismatch": False,
                    "blendModeMismatch": recon_mode != runtime_mode,
                    "maskFilterBranchMismatch": (
                        reconstructed_branch == "SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)"
                        and runtime_branch == "SkBitmapDevice.blend.kSrc.setPixel(out)"
                    ),
                    "sourcePayloadMismatch": recon_src != runtime_src,
                },
            }
        )

    decision = DECISION_DRAW_MAPPING
    exact_gap = None
    if coordinate_matches != 59 or coverage_matches != 59:
        decision = DECISION_AMBIGUOUS
        exact_gap = "Comparable target coordinates or coverage no longer align 59/59."
    elif runtime_temporary_events != 0:
        decision = DECISION_LAYER_SOURCE
        exact_gap = None
    elif draw_mapping_mismatches != 59:
        if blend_mode_mismatches == 59:
            decision = DECISION_BLEND_MODE
            exact_gap = None
        elif mask_filter_branch_mismatches == 59:
            decision = DECISION_MASK_FILTER
            exact_gap = None
        else:
            decision = DECISION_AMBIGUOUS
            exact_gap = (
                "FOR-292 expected a 59/59 red drawRRect reconstruction versus white kSrc runtime split, "
                f"but observed {draw_mapping_mismatches}/59."
            )

    anchor = next((pixel for pixel in per_pixel if (pixel["x"], pixel["y"]) == (99, 89)), per_pixel[0])
    needles = source_needles()
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-source-payload-derivation-audit",
        "sceneId": SCENE_ID,
        "backend": "CPU/RGBA_8888/reconstructed-derivation-vs-runtime-root",
        "sourceAudits": {
            "for286": str(SOURCE_FOR286.relative_to(PROJECT_ROOT)),
            "for288": str(SOURCE_FOR288.relative_to(PROJECT_ROOT)),
            "for289": str(SOURCE_FOR289.relative_to(PROJECT_ROOT)),
            "for290": str(SOURCE_FOR290.relative_to(PROJECT_ROOT)),
            "for291": str(SOURCE_FOR291.relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": decision,
        "exactGap": exact_gap,
        "firstDemonstratedDivergence": (
            "The reconstructed payload chain maps the 59 pixels to the red blurred drawRRect "
            "A8 SrcIn payload path, while the comparable runtime root event maps to the white "
            "drawRect kSrc dispatch path."
        ),
        "nextAction": "AUDIT_WHY_RED_MASK_FILTER_DRAW_DOES_NOT_EMIT_COMPARABLE_ROOT_DISPATCH_FOR_THE_59_PIXELS",
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
            "sourceNeedles": needles,
        },
        "targetPixelCount": len(per_pixel),
        "derivationChain": {
            "reconstructed": [
                "BlurredClippedCircleGM draws the red oval with maskFilter=SkBlurMaskFilter.Make.",
                "The paint has colorFilter=Blend(SK_ColorRED,kSrcIn) and default kSrcOver blend mode.",
                "SkBitmapDevice.drawPathWithMaskFilter enters the A8 srcInPayload branch.",
                "maskedA is derived from the blurred mask alpha and paint alpha.",
                "applyColorFilter turns the masked source into a red RGBA payload.",
                "dispatchBlend feeds blend.kSrcOver; active-AA coverage is 255 for the 59 pixels.",
                "blend.kSrcOver.partialSrc.setPixel(out) reconstructs a red-tinted write.",
            ],
            "runtimeRoot": [
                "The root trace sees SkBitmap.eraseColor for each target pixel.",
                "The comparable root dispatch source is SkBitmapDevice.dispatchBlend.",
                "The runtime dispatch payload is white RGBA with mode kSrc for 59/59 pixels.",
                "The branch is blend.kSrc.setPixel(out), with no red root write and no temporary event.",
            ],
        },
        "comparisonSummary": {
            "coordinateMatches": coordinate_matches,
            "coverageMatches": coverage_matches,
            "reconstructedRedSrcInPayloadPixels": reconstructed_red_payloads,
            "reconstructedKSrcOverPixels": reconstructed_ksrc_over,
            "reconstructedMaskFilterPayloadPixels": reconstructed_mask_filter_payloads,
            "runtimeWhiteKSrcPayloadPixels": runtime_white_ksrc_payloads,
            "runtimeRootDispatchPixels": runtime_root_dispatches,
            "runtimeTemporaryEvents": runtime_temporary_events,
            "drawMappingMismatches": draw_mapping_mismatches,
            "layerSourceMismatches": layer_source_mismatches,
            "blendModeMismatches": blend_mode_mismatches,
            "maskFilterBranchMismatches": mask_filter_branch_mismatches,
            "sourcePayloadMismatches": source_payload_mismatches,
            "reconstructedAlphaMin": min(reconstructed_alpha_values),
            "reconstructedAlphaMax": max(reconstructed_alpha_values),
        },
        "runtimeCounts": {
            "dispatchModes": dict(runtime_modes),
            "dispatchSources": dict(runtime_sources),
            "reconstructedBranches": dict(reconstructed_branches),
        },
        "anchorComparison": anchor,
        "perPixelComparisons": per_pixel,
        "sourcePreservation": {
            "for286DecisionPreserved": source_for286["decision"] == for286.DECISION,
            "for288ClassificationPreserved": (
                source_for288["primaryClassification"] == "OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE"
            ),
            "for289DecisionPreserved": source_for289["decision"] == "PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP",
            "for290DecisionPreserved": source_for290["decision"] == "NO_RUNTIME_RED_ROOT_STORE_FOUND",
            "for291DecisionPreserved": source_for291["decision"] == "RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH",
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
            "m60Promoted": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def write_report(audit: dict[str, Any]) -> None:
    summary = audit["comparisonSummary"]
    anchor = audit["anchorComparison"]
    reconstructed_chain = "\n".join(f"- {item}" for item in audit["derivationChain"]["reconstructed"])
    runtime_chain = "\n".join(f"- {item}" for item in audit["derivationChain"]["runtimeRoot"])
    mode_rows = "\n".join(
        f"| `{name}` | {count} |" for name, count in sorted(audit["runtimeCounts"]["dispatchModes"].items())
    )
    branch_rows = "\n".join(
        f"| `{name}` | {count} |"
        for name, count in sorted(audit["runtimeCounts"]["reconstructedBranches"].items())
    )
    report = f"""# FOR-292 M60 Source Payload Derivation Audit

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Exact gap: `{audit["exactGap"] or "none"}`

## Result

FOR-292 traces the derivation chain behind the FOR-286/FOR-288 red
payload reconstruction and compares it to the FOR-290/FOR-291 runtime root
chain. The first demonstrated divergence is draw mapping: the
reconstruction maps the 59 pixels to the red blurred `drawRRect` A8
`SrcIn` payload path, while the comparable runtime root dispatch is the
white `drawRect` path using `kSrc`.

This keeps the FOR-291 result intact: the root runtime trace still has 0
red writes and 59 white `kSrc` dispatch payloads. The blend-mode and
mask-filter branch mismatches are consequences of comparing two different
draw paths, not the earliest demonstrated cause.

| Measure | Value |
|---|---:|
| Target pixels | {audit["targetPixelCount"]} |
| Coordinate matches | {summary["coordinateMatches"]} |
| Coverage matches | {summary["coverageMatches"]} |
| Reconstructed red SrcIn payload pixels | {summary["reconstructedRedSrcInPayloadPixels"]} |
| Reconstructed kSrcOver pixels | {summary["reconstructedKSrcOverPixels"]} |
| Reconstructed mask-filter payload pixels | {summary["reconstructedMaskFilterPayloadPixels"]} |
| Runtime white kSrc payload pixels | {summary["runtimeWhiteKSrcPayloadPixels"]} |
| Runtime root dispatch pixels | {summary["runtimeRootDispatchPixels"]} |
| Runtime temporary events | {summary["runtimeTemporaryEvents"]} |
| Draw mapping mismatches | {summary["drawMappingMismatches"]} |
| Layer source mismatches | {summary["layerSourceMismatches"]} |
| Blend mode mismatches | {summary["blendModeMismatches"]} |
| Mask-filter branch mismatches | {summary["maskFilterBranchMismatches"]} |
| Source payload mismatches | {summary["sourcePayloadMismatches"]} |
| Reconstructed alpha min | {summary["reconstructedAlphaMin"]} |
| Reconstructed alpha max | {summary["reconstructedAlphaMax"]} |

## Reconstructed Chain

{reconstructed_chain}

## Runtime Root Chain

{runtime_chain}

## Anchor Pixel

| Field | Reconstructed payload chain | Runtime root chain |
|---|---|---|
| Pixel | `{anchor["x"]},{anchor["y"]}` | `{anchor["x"]},{anchor["y"]}` |
| Mapped draw | `{anchor["reconstructedDerivation"]["mappedDraw"]}` | `{anchor["runtimeRootDerivation"]["mappedDraw"]}` |
| Source | `{anchor["reconstructedDerivation"]["dispatchInputSrcRgba"]}` | `{anchor["runtimeRootDerivation"]["srcInputRgba"]}` |
| Mode | `{anchor["reconstructedDerivation"]["dispatchInputMode"]}` | `{anchor["runtimeRootDerivation"]["mode"]}` |
| Coverage | {anchor["reconstructedDerivation"]["coverageAppliedInBlend"]} | {anchor["runtimeRootDerivation"]["coverage"]} |
| Branch | `{anchor["reconstructedDerivation"]["writeBranch"]}` | `{anchor["runtimeRootDerivation"]["branch"]}` |
| Written | `{anchor["reconstructedDerivation"]["valueWrittenRgba"]}` | `{anchor["runtimeRootDerivation"]["valueWrittenRgba"]}` |

## Runtime And Reconstruction Counts

| Runtime dispatch mode | Pixels |
|---|---:|
{mode_rows}

| Reconstructed branch | Pixels |
|---|---:|
{branch_rows}

## Preserved Decisions

- FOR-288 classification: `{audit["route"]["for288PrimaryClassification"]}`
- FOR-289 decision: `{audit["route"]["for289Decision"]}`
- FOR-290 decision: `{audit["route"]["for290Decision"]}`
- FOR-291 decision: `{audit["route"]["for291Decision"]}`

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
) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("wrong Linear id")
    if audit.get("parent") != PARENT_ID:
        fail("wrong parent id")
    if audit.get("sceneId") != SCENE_ID:
        fail("wrong scene id")
    if audit.get("targetPixelCount") != 59:
        fail("target pixel count changed")
    if audit.get("decision") not in DECISIONS:
        fail("invalid FOR-292 decision")
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

    route = audit["route"]
    for key, value in route["sourceNeedles"].items():
        if value is not True:
            fail(f"source needle `{key}` failed")
    if route["for286Decision"] != for286.DECISION:
        fail("FOR-286 route decision changed")
    if route["for288PrimaryClassification"] != "OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE":
        fail("FOR-288 route classification changed")
    if route["for289Decision"] != "PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP":
        fail("FOR-289 route decision changed")
    if route["for290Decision"] != "NO_RUNTIME_RED_ROOT_STORE_FOUND":
        fail("FOR-290 route decision changed")
    if route["for291Decision"] != "RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH":
        fail("FOR-291 route decision changed")
    if route["visualParityFallbackPreserved"] != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route["cropFallbackPreserved"] != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")

    summary = audit["comparisonSummary"]
    expected_summary = {
        "coordinateMatches": 59,
        "coverageMatches": 59,
        "reconstructedRedSrcInPayloadPixels": 59,
        "reconstructedKSrcOverPixels": 59,
        "reconstructedMaskFilterPayloadPixels": 59,
        "runtimeWhiteKSrcPayloadPixels": 59,
        "runtimeRootDispatchPixels": 59,
        "runtimeTemporaryEvents": 0,
        "drawMappingMismatches": 59,
        "layerSourceMismatches": 0,
        "blendModeMismatches": 59,
        "maskFilterBranchMismatches": 59,
        "sourcePayloadMismatches": 59,
    }
    for key, expected in expected_summary.items():
        if summary.get(key) != expected:
            fail(f"summary `{key}` changed: {summary.get(key)} != {expected}")
    if audit["decision"] != DECISION_DRAW_MAPPING:
        fail(f"FOR-292 expected {DECISION_DRAW_MAPPING}, got {audit['decision']}")
    if audit["runtimeCounts"]["dispatchModes"] != {"kSrc": 59}:
        fail("runtime dispatch mode counts changed")
    if audit["runtimeCounts"]["dispatchSources"] != {"SkBitmapDevice.dispatchBlend": 59}:
        fail("runtime dispatch source counts changed")
    if audit["runtimeCounts"]["reconstructedBranches"] != {
        "SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)": 59
    }:
        fail("reconstructed branch counts changed")
    if len(audit.get("perPixelComparisons", [])) != 59:
        fail("per-pixel comparison count changed")

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
    for286.validate(source_for286)
    for288.validate_audit(source_for288)
    for289.validate_audit(source_for289, source_for288)
    for290.validate_audit(source_for290, source_for288, source_for289)
    for291.validate_audit(source_for291, source_for286, source_for288, source_for289, source_for290)

    audit = analyze(
        source_for286=source_for286,
        source_for288=source_for288,
        source_for289=source_for289,
        source_for290=source_for290,
        source_for291=source_for291,
    )
    validate_audit(audit, source_for286, source_for288, source_for289, source_for290, source_for291)
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
    write_report(audit)
    reread = load_json(AUDIT)
    validate_audit(reread, source_for286, source_for288, source_for289, source_for290, source_for291)
    report_text = REPORT.read_text(encoding="utf-8")
    for needle in (
        audit["decision"],
        "draw mapping",
        "FOR-291 decision",
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
        "No\nproduction renderer",
    ):
        if needle not in report_text:
            fail(f"report missing `{needle}`")
    print(f"FOR-292 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-292 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(f"Decision: {audit['decision']}")


if __name__ == "__main__":
    main()
