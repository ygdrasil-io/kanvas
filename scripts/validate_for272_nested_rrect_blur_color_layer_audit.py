#!/usr/bin/env python3
"""Generate and validate FOR-272 nested RRect blur color/layer evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

import numpy as np

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for271_nested_rrect_blurred_envelope_audit as for271


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-272"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "nested-rrect-blur-color-layer-audit-for272.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-272-nested-rrect-blur-color-layer-audit.md"
SOURCE_AUDIT = SCENE_DIR / "nested-rrect-blurred-envelope-audit-for271.json"
GM_SOURCE = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
GPU_SOURCE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
GPU_SHADER = PROJECT_ROOT / "gpu-raster/src/main/resources/shaders/blur_gaussian.wgsl"

SUBZONES = (
    "difference_oval_inner_boundary",
    "draw_oval_outer_boundary",
    "halo_interior",
    "removed_difference_oval_interior",
    "outside_draw_oval",
)
COMPARISONS = (
    ("gpu_vs_reference", "reference", "gpu"),
    ("cpu_vs_reference", "reference", "cpu"),
    ("gpu_vs_cpu", "cpu", "gpu"),
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-272 validation failed: {message}")


def json_dump(data: dict[str, Any]) -> str:
    return json.dumps(data, indent=2, sort_keys=False) + "\n"


def rounded(value: float, digits: int = 6) -> float:
    return round(float(value), digits)


def read_required(path: Path, needles: tuple[str, ...]) -> str:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")
    return text


def color_category_masks(image: np.ndarray) -> dict[str, np.ndarray]:
    r = image[:, :, 0]
    g = image[:, :, 1]
    b = image[:, :, 2]
    a = image[:, :, 3]
    max_rgb = np.maximum.reduce((r, g, b))
    min_rgb = np.minimum.reduce((r, g, b))
    red_reference = (a > 8) & (r >= 160) & (g >= 40) & (g <= 110) & (b <= 90)
    white_layer = (a > 8) & (min_rgb >= 240)
    black_payload = (a > 8) & (max_rgb <= 16)
    gray_background = (a > 8) & (np.abs(r - 204) <= 8) & (np.abs(g - 204) <= 8) & (np.abs(b - 204) <= 8)
    transparent = a <= 8
    classified = red_reference | white_layer | black_payload | gray_background | transparent
    return {
        "transparent": transparent,
        "black_or_clear_rgb": black_payload,
        "white_or_layer_background": white_layer,
        "red_tinted_reference_like": red_reference,
        "gray_background_like": gray_background,
        "other_opaque": (a > 8) & ~classified,
    }


def color_buckets(image: np.ndarray, mask: np.ndarray) -> dict[str, int]:
    return {
        name: int((bucket & mask).sum())
        for name, bucket in color_category_masks(image).items()
    }


def mean_rgba(image: np.ndarray, mask: np.ndarray) -> list[float]:
    if not mask.any():
        return [0.0, 0.0, 0.0, 0.0]
    return [rounded(image[:, :, channel][mask].mean(), 3) for channel in range(4)]


def signed_summary(base: np.ndarray, actual: np.ndarray, mask: np.ndarray) -> dict[str, Any]:
    signed = actual - base
    if not mask.any():
        return {
            "meanSignedRgba": [0.0, 0.0, 0.0, 0.0],
            "minSignedRgba": [0, 0, 0, 0],
            "maxSignedRgba": [0, 0, 0, 0],
        }
    return {
        "meanSignedRgba": [rounded(signed[:, :, channel][mask].mean(), 3) for channel in range(4)],
        "minSignedRgba": [int(signed[:, :, channel][mask].min()) for channel in range(4)],
        "maxSignedRgba": [int(signed[:, :, channel][mask].max()) for channel in range(4)],
    }


def subzone_samples(
    *,
    subzone: str,
    reference: np.ndarray,
    cpu: np.ndarray,
    gpu: np.ndarray,
    masks: dict[str, np.ndarray],
    limit: int = 4,
) -> list[dict[str, Any]]:
    gpu_ref_diff = np.abs(gpu - reference).max(axis=2)
    cpu_ref_diff = np.abs(cpu - reference).max(axis=2)
    gpu_cpu_diff = np.abs(gpu - cpu).max(axis=2)
    sample_mask = masks[subzone] & ((gpu_ref_diff > 32) | (cpu_ref_diff > 32) | (gpu_cpu_diff > 32))
    ys, xs = np.nonzero(sample_mask)
    records = sorted(
        (
            -int(max(gpu_ref_diff[y, x], cpu_ref_diff[y, x], gpu_cpu_diff[y, x])),
            int(y),
            int(x),
        )
        for y, x in zip(ys.tolist(), xs.tolist())
    )
    samples: list[dict[str, Any]] = []
    for neg_delta, y, x in records[:limit]:
        ref = reference[y, x].astype(int)
        cpu_px = cpu[y, x].astype(int)
        gpu_px = gpu[y, x].astype(int)
        samples.append(
            {
                "x": x,
                "y": y,
                "subzone": subzone,
                "referenceRgba": ref.tolist(),
                "cpuRgba": cpu_px.tolist(),
                "gpuRgba": gpu_px.tolist(),
                "cpuMinusReferenceSignedRgba": (cpu_px - ref).tolist(),
                "gpuMinusReferenceSignedRgba": (gpu_px - ref).tolist(),
                "gpuMinusCpuSignedRgba": (gpu_px - cpu_px).tolist(),
                "maxObservedChannelDelta": -neg_delta,
            }
        )
    return samples


def hypothesis_for_subzone(
    *,
    subzone: str,
    gpu_ref_gt32: int,
    cpu_ref_gt32: int,
    gpu_actual_buckets: dict[str, int],
    cpu_actual_buckets: dict[str, int],
) -> dict[str, Any]:
    if gpu_ref_gt32 == 0 and cpu_ref_gt32 == 0:
        return {
            "dominantHypothesis": "NOT_PRIMARY_FOR_FOR272",
            "rationale": "No >32 residual in this FOR-271 subzone; final composite/background outside the blur envelope is not the current blocker.",
        }
    gpu_black = gpu_actual_buckets.get("black_or_clear_rgb", 0)
    cpu_white = cpu_actual_buckets.get("white_or_layer_background", 0)
    if subzone == "halo_interior":
        return {
            "dominantHypothesis": "GPU_SOLID_BLUR_COLOR_FILTER_NOT_FOLDED_WITH_CPU_BACKGROUND_LAYER_RETENTION",
            "rationale": (
                "Reference samples are red-tinted, WebGPU high-delta samples are black/clear RGB, "
                "and CPU high-delta samples are mostly white layer/background. This points to a WebGPU "
                "solid-blur paint-color payload issue plus an independent CPU/reference layer or mask-extent residual."
            ),
            "gpuBlackOrClearRgbHighDeltaPixels": gpu_black,
            "cpuWhiteLayerHighDeltaPixels": cpu_white,
        }
    return {
        "dominantHypothesis": "MIXED_BOUNDARY_COLOR_PAYLOAD_AND_LAYER_EDGE",
        "rationale": (
            "The oval boundary band combines the same WebGPU black/clear RGB polarity with CPU white layer/background "
            "pixels, so a boundary-only composite fix would not explain both backends."
        ),
        "gpuBlackOrClearRgbHighDeltaPixels": gpu_black,
        "cpuWhiteLayerHighDeltaPixels": cpu_white,
    }


def comparison_by_name(for271_audit: dict[str, Any], name: str) -> dict[str, Any]:
    for item in for271_audit.get("comparisons", []):
        if item.get("comparison") == name:
            return item
    fail(f"FOR-271 source audit missing comparison `{name}`")


def comparison_metrics(
    *,
    name: str,
    base_label: str,
    actual_label: str,
    images: dict[str, np.ndarray],
    masks: dict[str, np.ndarray],
) -> dict[str, Any]:
    base = images[base_label]
    actual = images[actual_label]
    diff = np.abs(actual - base).max(axis=2)
    signed = actual - base
    subzone_metrics: dict[str, Any] = {}
    for subzone in SUBZONES:
        zone_mask = masks[subzone]
        gt32 = zone_mask & (diff > 32)
        subzone_metrics[subzone] = {
            "pixels": int(zone_mask.sum()),
            "greaterThanThirtyTwoPixels": int(gt32.sum()),
            "meanBaseRgba": mean_rgba(base, zone_mask),
            "meanActualRgba": mean_rgba(actual, zone_mask),
            "signedDeltaOnGt32": signed_summary(base, actual, gt32),
            "baseColorBucketsOnGt32": color_buckets(base, gt32),
            "actualColorBucketsOnGt32": color_buckets(actual, gt32),
            "maxChannelDelta": int(diff[zone_mask].max()) if zone_mask.any() else 0,
        }
        if gt32.any():
            subzone_metrics[subzone]["meanMaxChannelDeltaOnGt32"] = rounded(diff[gt32].mean(), 3)
        else:
            subzone_metrics[subzone]["meanMaxChannelDeltaOnGt32"] = 0.0
    return {
        "comparison": name,
        "base": base_label,
        "actual": actual_label,
        "greaterThanThirtyTwoPixels": int((diff > 32).sum()),
        "maxChannelDelta": int(diff.max()),
        "signedDeltaSummaryOnBlurredEnvelopeGt32": signed_summary(
            base,
            actual,
            (diff > 32) & masks["blurred_content_envelope"],
        ),
        "subzones": subzone_metrics,
    }


def generate_audit() -> dict[str, Any]:
    for271_audit = for269.load_json(SOURCE_AUDIT)
    stats = for269.load_json(SCENE_DIR / "stats.json")
    route_gpu = for269.load_json(SCENE_DIR / "route-gpu.json")
    route_cpu = for269.load_json(SCENE_DIR / "route-cpu.json")
    images = {
        "reference": for269.load_image("skia"),
        "cpu": for269.load_image("cpu"),
        "gpu": for269.load_image("gpu"),
    }
    reference = images["reference"]
    if reference.shape != images["cpu"].shape or reference.shape != images["gpu"].shape:
        fail("reference, CPU, and GPU PNG dimensions differ")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA images")

    geometry, masks = for271.make_envelope_masks(width, height)
    source_model = {
        "gmPaintChain": {
            "source": str(GM_SOURCE.relative_to(PROJECT_ROOT)),
            "draw": "drawRRect(drawOval, paint)",
            "maskFilter": "SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.366025f)",
            "colorFilter": "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)",
            "shader": "none",
            "paintColorDefault": "SkPaint default solid color is the unfiltered destination for the Blend color filter.",
        },
        "cpuModelEvidence": {
            "source": str(CPU_SOURCE.relative_to(PROJECT_ROOT)),
            "solidMaskFilterColor": "drawPathWithMaskFilter computes transformPaintColor(applyColorFilter(paint.colorFilter, paint.color)) before mask alpha modulation.",
            "observedPolarity": "CPU/reference high-delta pixels are mostly white layer/background in the same red-tinted reference envelope.",
        },
        "webGpuModelEvidence": {
            "source": str(GPU_SOURCE.relative_to(PROJECT_ROOT)),
            "shader": str(GPU_SHADER.relative_to(PROJECT_ROOT)),
            "solidMaskFilterColor": "drawPathWithBlurMaskFilterIfApplicable extracts paintColor from paint.color for the blur V/composite pass.",
            "shaderComposite": "blur_gaussian.wgsl multiplies blurred coverage by uniforms.paintColor in fs_vertical_composite/fs_composite_upsampled.",
            "observedPolarity": "WebGPU/reference high-delta pixels are black/clear RGB where the reference is red-tinted.",
        },
    }

    comparisons = [
        comparison_metrics(
            name=name,
            base_label=base_label,
            actual_label=actual_label,
            images=images,
            masks=masks,
        )
        for name, base_label, actual_label in COMPARISONS
    ]
    by_name = {item["comparison"]: item for item in comparisons}

    hypotheses_by_subzone = {}
    for subzone in SUBZONES:
        gpu_zone = by_name["gpu_vs_reference"]["subzones"][subzone]
        cpu_zone = by_name["cpu_vs_reference"]["subzones"][subzone]
        hypotheses_by_subzone[subzone] = hypothesis_for_subzone(
            subzone=subzone,
            gpu_ref_gt32=gpu_zone["greaterThanThirtyTwoPixels"],
            cpu_ref_gt32=cpu_zone["greaterThanThirtyTwoPixels"],
            gpu_actual_buckets=gpu_zone["actualColorBucketsOnGt32"],
            cpu_actual_buckets=cpu_zone["actualColorBucketsOnGt32"],
        )

    sample_sets = {
        subzone: subzone_samples(
            subzone=subzone,
            reference=images["reference"],
            cpu=images["cpu"],
            gpu=images["gpu"],
            masks=masks,
        )
        for subzone in SUBZONES
    }

    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "nested-rrect-blur-color-layer-audit",
        "sceneId": SCENE_ID,
        "backend": "Reference/CPU/WebGPU",
        "sourceAudit": str(SOURCE_AUDIT.relative_to(PROJECT_ROOT)),
        "sourceTest": "org.skia.gpu.webgpu.NestedClipSceneCaptureTest",
        "artifactRoot": str(SCENE_DIR.relative_to(PROJECT_ROOT)),
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "residualClassification": "COLOR_LAYER_PAYLOAD_POLARITY_WITH_SHARED_CPU_REFERENCE_RESIDUAL",
        "dominantHypothesis": "GPU_SOLID_BLUR_COLOR_FILTER_NOT_FOLDED_PLUS_CPU_REFERENCE_LAYER_RESIDUAL",
        "nextAction": "BOUNDED_GPU_SOLID_BLUR_COLOR_FILTER_FOLD_CANDIDATE_AFTER_CPU_REFERENCE_DECISION",
        "decisionRationale": (
            "FOR-272 does not promote the scene. Alpha is unchanged in FOR-271 high-delta pixels. "
            "Reference pixels in the blur envelope are red-tinted, WebGPU pixels skew black/clear RGB, "
            "and CPU pixels skew white layer/background. The GPU path has a bounded color-filter fold "
            "candidate, but CPU/reference remains below the strict threshold in the same envelope."
        ),
        "route": {
            "cpu": route_cpu.get("selectedRoute"),
            "gpu": route_gpu.get("selectedRoute"),
            "gpuStatus": route_gpu.get("status"),
            "fallbackReason": route_gpu.get("fallbackReason"),
            "coverageStrategy": route_gpu.get("coverageStrategy"),
            "pipelineKey": route_gpu.get("pipelineKey"),
        },
        "reportedStats": {
            "cpuSimilarity": stats.get("cpuSimilarity"),
            "gpuSimilarity": stats.get("gpuSimilarity"),
            "threshold": stats.get("threshold"),
            "cpuMatchingPixels": stats.get("cpuMatchingPixels"),
            "gpuMatchingPixels": stats.get("gpuMatchingPixels"),
            "cpuMaxChannelDelta": stats.get("cpuMaxChannelDelta"),
            "gpuMaxChannelDelta": stats.get("gpuMaxChannelDelta"),
        },
        "geometry": geometry,
        "sourceModel": source_model,
        "comparisons": comparisons,
        "hypothesesBySubzone": hypotheses_by_subzone,
        "signedRgbSamplesBySubzone": sample_sets,
        "globalHypothesisRanking": [
            {
                "hypothesis": "webgpu_solid_blur_uses_unfiltered_paint_color",
                "classification": "DOMINANT_FOR_WEBGPU_COLOR_POLARITY",
                "evidence": "WebGPU high-delta samples are black/clear RGB while reference samples are red-tinted; source evidence shows paint.color feeds blur paintColor.",
            },
            {
                "hypothesis": "cpu_reference_layer_or_mask_extent_residual",
                "classification": "DOMINANT_FOR_CPU_REFERENCE_RESIDUAL",
                "evidence": "CPU high-delta samples in the same envelope are mostly white layer/background, not red-tinted payload.",
            },
            {
                "hypothesis": "color_filter_blur_order",
                "classification": "NOT_PRIMARY_AS_A_PURE_ORDER_SWAP",
                "evidence": "For solid paint with Blend(RED,kSrcIn), applying the filter before mask-alpha modulation should still produce a red payload; observed backend polarities are black and white.",
            },
            {
                "hypothesis": "final_composite_clip_or_alpha",
                "classification": "REJECTED_AS_PRIMARY_AFTER_FOR270",
                "evidence": "FOR-271 reports zero alpha >32 pixels, no >32 residual in removed_difference_oval_interior/outside_draw_oval, and retained FOR-270 clip-mask improvement.",
            },
            {
                "hypothesis": "source_mask_color",
                "classification": "SECONDARY_TO_PAYLOAD_FOLDING",
                "evidence": "The blur mask source is white alpha coverage; WebGPU black output follows from multiplying coverage by unfiltered black paintColor, not from the white mask itself.",
            },
        ],
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRendererChanged": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
        },
    }


def comparison(audit: dict[str, Any], name: str) -> dict[str, Any]:
    for item in audit.get("comparisons", []):
        if item.get("comparison") == name:
            return item
    fail(f"missing comparison `{name}`")


def write_report(audit: dict[str, Any]) -> None:
    gpu = comparison(audit, "gpu_vs_reference")
    cpu = comparison(audit, "cpu_vs_reference")
    gpu_cpu = comparison(audit, "gpu_vs_cpu")
    route = audit["route"]
    hyp = audit["hypothesesBySubzone"]
    halo_samples = audit["signedRgbSamplesBySubzone"]["halo_interior"][:3]
    sample_lines = "\n".join(
        "| {x},{y} | `{referenceRgba}` | `{cpuRgba}` | `{gpuRgba}` | `{cpuMinusReferenceSignedRgba}` | `{gpuMinusReferenceSignedRgba}` |".format(**sample)
        for sample in halo_samples
    )
    report = f"""# FOR-272 Nested RRect Blur Color/Layer Audit

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["supportDecision"]}`

Dominant hypothesis: `{audit["dominantHypothesis"]}`.

The audited chain is `SkBlurMaskFilter(kNormal, sigma=1.366025)` plus
`SkColorFilters.Blend(RED, kSrcIn)` on the `BlurredClippedCircleGM`
`drawRRect`. The scene remains on route `{route["gpu"]}` with fallback
`{route["fallbackReason"]}` because GPU/reference `{audit["reportedStats"]["gpuSimilarity"]}`
and CPU/reference `{audit["reportedStats"]["cpuSimilarity"]}` remain below the
strict `{audit["supportThreshold"]}` threshold.

## Color/Layer Findings

| Comparison | >32 pixels | Signed mean RGBA in blurred envelope >32 | Max delta |
|---|---:|---|---:|
| GPU/reference | {gpu["greaterThanThirtyTwoPixels"]} | `{gpu["signedDeltaSummaryOnBlurredEnvelopeGt32"]["meanSignedRgba"]}` | {gpu["maxChannelDelta"]} |
| CPU/reference | {cpu["greaterThanThirtyTwoPixels"]} | `{cpu["signedDeltaSummaryOnBlurredEnvelopeGt32"]["meanSignedRgba"]}` | {cpu["maxChannelDelta"]} |
| GPU/CPU | {gpu_cpu["greaterThanThirtyTwoPixels"]} | `{gpu_cpu["signedDeltaSummaryOnBlurredEnvelopeGt32"]["meanSignedRgba"]}` | {gpu_cpu["maxChannelDelta"]} |

| Subzone | GPU/ref >32 | CPU/ref >32 | Dominant hypothesis |
|---|---:|---:|---|
| `draw_oval_outer_boundary` | {gpu["subzones"]["draw_oval_outer_boundary"]["greaterThanThirtyTwoPixels"]} | {cpu["subzones"]["draw_oval_outer_boundary"]["greaterThanThirtyTwoPixels"]} | `{hyp["draw_oval_outer_boundary"]["dominantHypothesis"]}` |
| `difference_oval_inner_boundary` | {gpu["subzones"]["difference_oval_inner_boundary"]["greaterThanThirtyTwoPixels"]} | {cpu["subzones"]["difference_oval_inner_boundary"]["greaterThanThirtyTwoPixels"]} | `{hyp["difference_oval_inner_boundary"]["dominantHypothesis"]}` |
| `halo_interior` | {gpu["subzones"]["halo_interior"]["greaterThanThirtyTwoPixels"]} | {cpu["subzones"]["halo_interior"]["greaterThanThirtyTwoPixels"]} | `{hyp["halo_interior"]["dominantHypothesis"]}` |
| `removed_difference_oval_interior` | {gpu["subzones"]["removed_difference_oval_interior"]["greaterThanThirtyTwoPixels"]} | {cpu["subzones"]["removed_difference_oval_interior"]["greaterThanThirtyTwoPixels"]} | `{hyp["removed_difference_oval_interior"]["dominantHypothesis"]}` |
| `outside_draw_oval` | {gpu["subzones"]["outside_draw_oval"]["greaterThanThirtyTwoPixels"]} | {cpu["subzones"]["outside_draw_oval"]["greaterThanThirtyTwoPixels"]} | `{hyp["outside_draw_oval"]["dominantHypothesis"]}` |

## Signed RGB Samples

Halo-interior samples show the polarity directly:

| Pixel | Reference RGBA | CPU RGBA | GPU RGBA | CPU-reference signed | GPU-reference signed |
|---|---|---|---|---|---|
{sample_lines}

## Interpretation

FOR-271 already proved `COLOR_PAYLOAD_SHIFT_ALPHA_UNCHANGED`: alpha is not the
primary failure. FOR-272 narrows the color/layer polarity:

- Reference envelope pixels are red-tinted.
- CPU/reference high-delta pixels skew white, consistent with layer/background
  retention or mask extent ordering in the CPU oracle.
- WebGPU/reference high-delta pixels skew black/clear RGB. The bounded source
  candidate is the solid blur path using `paint.color` for `paintColor` while
  the GM supplies the red tint through `SkColorFilters.Blend(RED, kSrcIn)`.

This is not enough for support promotion because CPU/reference is still below
the strict threshold in the same envelope. A bounded GPU color-filter fold may
be a next implementation candidate, but it must be validated against FOR-270
and FOR-271 and cannot clear support alone.

## Support Verdict

Keep `expected-unsupported` with fallback
`coverage.nested-clip-visual-parity-below-threshold`. No threshold weakening,
broad clip-stack support, fallback/readback, Ganesh, Graphite, or SkSL compiler
was added. Preserve `{for269.CROP_FALLBACK_REASON}`.

## Validation

```text
rtk python3 scripts/validate_for272_nested_rrect_blur_color_layer_audit.py
rtk python3 scripts/validate_for271_nested_rrect_blurred_envelope_audit.py
rtk python3 scripts/validate_for270_nested_rrect_difference_oval_mask.py
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`
"""
    REPORT.write_text(report)


def validate(audit: dict[str, Any]) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("linear id mismatch")
    if audit.get("parent") != PARENT_ID:
        fail("parent id mismatch")
    if audit.get("sceneId") != SCENE_ID:
        fail("scene id mismatch")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision must remain expected-unsupported")
    if audit.get("dominantHypothesis") != "GPU_SOLID_BLUR_COLOR_FILTER_NOT_FOLDED_PLUS_CPU_REFERENCE_LAYER_RESIDUAL":
        fail("dominant hypothesis mismatch")

    source = for269.load_json(SOURCE_AUDIT)
    if source.get("linear") != "FOR-271":
        fail("source audit must be FOR-271")
    if source.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("FOR-271 source decision changed")
    for name in ("gpu_vs_reference", "cpu_vs_reference", "gpu_vs_cpu"):
        source_comp = comparison_by_name(source, name)
        color = source_comp.get("colorPremulClassification", {})
        if color.get("verdict") != "COLOR_PAYLOAD_SHIFT_ALPHA_UNCHANGED":
            fail(f"{name} FOR-271 color classification changed")
        if color.get("alphaDeltaGreaterThanThirtyTwoPixels") != 0:
            fail(f"{name} must retain unchanged-alpha evidence")

    route = audit.get("route", {})
    if route.get("cpu") != for269.CPU_ROUTE:
        fail("CPU route mismatch")
    if route.get("gpu") != for269.GPU_ROUTE:
        fail("GPU route changed")
    if route.get("gpuStatus") != "expected-unsupported":
        fail("GPU status must remain expected-unsupported")
    if route.get("fallbackReason") != for269.FALLBACK_REASON:
        fail("fallback reason changed")

    reported = audit.get("reportedStats", {})
    if reported.get("threshold") != for269.SUPPORT_THRESHOLD:
        fail("reported stats threshold changed")
    if reported.get("gpuSimilarity") >= for269.SUPPORT_THRESHOLD:
        fail("GPU similarity unexpectedly reaches support threshold")
    if reported.get("cpuSimilarity") >= for269.SUPPORT_THRESHOLD:
        fail("CPU similarity unexpectedly reaches support threshold")

    gm_text = read_required(
        GM_SOURCE,
        (
            "SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.366025f)",
            "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)",
            "c.drawRRect(rr, paint)",
        ),
    )
    if "shader" in gm_text[gm_text.find("val paint = SkPaint().apply"):gm_text.find("c.drawRRect(rr, paint)")]:
        fail("GM paint unexpectedly gained a shader")
    read_required(
        CPU_SOURCE,
        (
            "private fun drawPathWithMaskFilter",
            "transformPaintColor(applyColorFilter(paint.colorFilter, paint.color))",
            "val rgb = effectiveColor and 0x00FFFFFF",
        ),
    )
    read_required(
        GPU_SOURCE,
        (
            "private fun drawPathWithBlurMaskFilterIfApplicable",
            "val color = paint.color",
            "paintR = cr, paintG = cg, paintB = cb, paintA = ca",
        ),
    )
    read_required(
        GPU_SHADER,
        (
            "uniforms.paintColor.r * coverage",
            "fn fs_vertical_composite",
            "fn fs_composite_upsampled",
        ),
    )

    gpu = comparison(audit, "gpu_vs_reference")
    cpu = comparison(audit, "cpu_vs_reference")
    gpu_cpu = comparison(audit, "gpu_vs_cpu")
    if gpu["subzones"]["halo_interior"]["actualColorBucketsOnGt32"]["black_or_clear_rgb"] != 3568:
        fail("GPU halo interior should be black/clear RGB in current artifact")
    if cpu["subzones"]["halo_interior"]["actualColorBucketsOnGt32"]["white_or_layer_background"] < 2000:
        fail("CPU halo interior must expose white layer/background polarity")
    if gpu_cpu["subzones"]["halo_interior"]["greaterThanThirtyTwoPixels"] != 3568:
        fail("GPU/CPU halo divergence must cover the halo interior")

    hypotheses = audit.get("hypothesesBySubzone", {})
    if hypotheses.get("halo_interior", {}).get("dominantHypothesis") != (
        "GPU_SOLID_BLUR_COLOR_FILTER_NOT_FOLDED_WITH_CPU_BACKGROUND_LAYER_RETENTION"
    ):
        fail("halo interior hypothesis mismatch")
    for subzone in ("removed_difference_oval_interior", "outside_draw_oval"):
        if hypotheses.get(subzone, {}).get("dominantHypothesis") != "NOT_PRIMARY_FOR_FOR272":
            fail(f"{subzone} must remain not primary")
    samples = audit.get("signedRgbSamplesBySubzone", {}).get("halo_interior")
    if not isinstance(samples, list) or not samples:
        fail("halo interior signed samples missing")
    first = samples[0]
    if first.get("gpuMinusReferenceSignedRgba", [0])[0] >= 0:
        fail("first halo sample must show negative WebGPU-reference red delta")
    if first.get("cpuMinusReferenceSignedRgba", [0, 0])[1] <= 0:
        fail("first halo sample must show positive CPU-reference green delta")

    strict = audit.get("strictPreservation", {})
    for field in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "productionRendererChanged",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if strict.get(field) is not False:
            fail(f"strict preservation field `{field}` must be false")
    if strict.get("cropFallbackPreserved") != for269.CROP_FALLBACK_REASON:
        fail("Crop fallback preservation mismatch")

    text = REPORT.read_text()
    for needle in (
        "FOR-272 Nested RRect Blur Color/Layer Audit",
        "KEEP_EXPECTED_UNSUPPORTED",
        "GPU_SOLID_BLUR_COLOR_FILTER_NOT_FOLDED_PLUS_CPU_REFERENCE_LAYER_RESIDUAL",
        "COLOR_PAYLOAD_SHIFT_ALPHA_UNCHANGED",
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
        AUDIT_NAME,
    ):
        if needle not in text:
            fail(f"report missing `{needle}`")


def main() -> None:
    audit = generate_audit()
    SCENE_DIR.mkdir(parents=True, exist_ok=True)
    AUDIT.write_text(json_dump(audit))
    write_report(audit)
    validate(for269.load_json(AUDIT))
    print(
        "FOR-272 validation passed: blur color/layer polarity is audited, "
        "WebGPU solid-blur color-filter folding is the bounded GPU candidate, "
        "CPU/reference layer residual remains blocking, and support stays expected-unsupported."
    )


if __name__ == "__main__":
    main()
