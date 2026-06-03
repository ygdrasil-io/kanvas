#!/usr/bin/env python3
"""Generate and validate FOR-274 CPU/reference layer or mask-extent evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

import numpy as np

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for271_nested_rrect_blurred_envelope_audit as for271
import validate_for272_nested_rrect_blur_color_layer_audit as for272


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-274"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "nested-rrect-cpu-reference-layer-mask-audit-for274.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-274-nested-rrect-cpu-reference-layer-mask-audit.md"
SOURCE_FOR273 = SCENE_DIR / "nested-rrect-solid-blur-srcin-fold-for273.json"
GM_SOURCE = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
GPU_SOURCE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"

PRIMARY_SUBZONES = (
    "draw_oval_outer_boundary",
    "difference_oval_inner_boundary",
    "halo_interior",
)
DIAGNOSTIC_SUBZONES = PRIMARY_SUBZONES + (
    "removed_difference_oval_interior",
    "outside_draw_oval",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-274 validation failed: {message}")


def json_dump(data: dict[str, Any]) -> str:
    return json.dumps(data, indent=2, sort_keys=False) + "\n"


def rounded(value: float, digits: int = 6) -> float:
    return round(float(value), digits)


def comparison(audit: dict[str, Any], name: str) -> dict[str, Any]:
    for item in audit.get("comparisons", []):
        if item.get("comparison") == name:
            return item
    fail(f"missing comparison `{name}`")


def signed_rgb_summary(base: np.ndarray, actual: np.ndarray, mask: np.ndarray) -> dict[str, Any]:
    signed = actual - base
    if not mask.any():
        return {
            "meanSignedRgb": [0.0, 0.0, 0.0],
            "minSignedRgb": [0, 0, 0],
            "maxSignedRgb": [0, 0, 0],
            "alphaGreaterThanThirtyTwoPixels": 0,
        }
    alpha_delta = np.abs(signed[:, :, 3])
    return {
        "meanSignedRgb": [rounded(signed[:, :, channel][mask].mean(), 3) for channel in range(3)],
        "minSignedRgb": [int(signed[:, :, channel][mask].min()) for channel in range(3)],
        "maxSignedRgb": [int(signed[:, :, channel][mask].max()) for channel in range(3)],
        "alphaGreaterThanThirtyTwoPixels": int((alpha_delta[mask] > 32).sum()),
    }


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
    channel_delta = np.abs(actual - base)
    diff = channel_delta.max(axis=2)
    subzones: dict[str, Any] = {}
    for subzone in DIAGNOSTIC_SUBZONES:
        zone_mask = masks[subzone]
        gt32 = zone_mask & (diff > 32)
        subzones[subzone] = {
            "pixels": int(zone_mask.sum()),
            "greaterThanThirtyTwoPixels": int(gt32.sum()),
            "shareOfComparisonGreaterThanThirtyTwo": rounded(int(gt32.sum()) / max(1, int((diff > 32).sum())) * 100.0),
            "maxChannelDelta": int(diff[zone_mask].max()) if zone_mask.any() else 0,
            "meanBaseRgbaOnGt32": for272.mean_rgba(base, gt32),
            "meanActualRgbaOnGt32": for272.mean_rgba(actual, gt32),
            "signedRgbOnGt32": signed_rgb_summary(base, actual, gt32),
            "baseColorBucketsOnGt32": for272.color_buckets(base, gt32),
            "actualColorBucketsOnGt32": for272.color_buckets(actual, gt32),
        }
    gt32 = diff > 32
    envelope_gt32 = gt32 & masks["blurred_content_envelope"]
    return {
        "comparison": name,
        "base": base_label,
        "actual": actual_label,
        "greaterThanThirtyTwoPixels": int(gt32.sum()),
        "blurredEnvelopeGreaterThanThirtyTwoPixels": int(envelope_gt32.sum()),
        "maxChannelDelta": int(diff.max()),
        "signedRgbOnBlurredEnvelopeGt32": signed_rgb_summary(base, actual, envelope_gt32),
        "subzones": subzones,
    }


def signed_samples_for_subzone(
    *,
    subzone: str,
    reference: np.ndarray,
    cpu: np.ndarray,
    gpu: np.ndarray,
    masks: dict[str, np.ndarray],
    limit: int = 4,
) -> list[dict[str, Any]]:
    cpu_ref_diff = np.abs(cpu - reference).max(axis=2)
    gpu_ref_diff = np.abs(gpu - reference).max(axis=2)
    sample_mask = masks[subzone] & ((cpu_ref_diff > 32) | (gpu_ref_diff > 32))
    ys, xs = np.nonzero(sample_mask)
    records = sorted(
        (
            -int(max(cpu_ref_diff[y, x], gpu_ref_diff[y, x])),
            int(y),
            int(x),
        )
        for y, x in zip(ys.tolist(), xs.tolist())
    )
    samples: list[dict[str, Any]] = []
    for neg_delta, y, x in records[:limit]:
        ref_px = reference[y, x].astype(int)
        cpu_px = cpu[y, x].astype(int)
        gpu_px = gpu[y, x].astype(int)
        samples.append(
            {
                "x": x,
                "y": y,
                "subzone": subzone,
                "referenceRgba": ref_px.tolist(),
                "cpuRgba": cpu_px.tolist(),
                "gpuRgba": gpu_px.tolist(),
                "cpuMinusReferenceSignedRgba": (cpu_px - ref_px).tolist(),
                "gpuMinusReferenceSignedRgba": (gpu_px - ref_px).tolist(),
                "cpuReferenceMaxChannelDelta": int(cpu_ref_diff[y, x]),
                "gpuReferenceMaxChannelDelta": int(gpu_ref_diff[y, x]),
                "maxObservedChannelDelta": -neg_delta,
            }
        )
    return samples


def classify_cpu_subzone(zone: dict[str, Any]) -> dict[str, Any]:
    gt32 = zone["greaterThanThirtyTwoPixels"]
    if gt32 == 0:
        return {
            "hypothesis": "NOT_PRIMARY",
            "rationale": "No CPU/reference >32 residual in this diagnostic subzone.",
        }
    actual_buckets = zone["actualColorBucketsOnGt32"]
    base_buckets = zone["baseColorBucketsOnGt32"]
    white = actual_buckets.get("white_or_layer_background", 0)
    red_ref = base_buckets.get("red_tinted_reference_like", 0)
    alpha_gt32 = zone["signedRgbOnGt32"]["alphaGreaterThanThirtyTwoPixels"]
    white_share = rounded(white / gt32 * 100.0)
    red_ref_share = rounded(red_ref / gt32 * 100.0)
    if alpha_gt32 == 0 and white_share >= 70.0:
        return {
            "hypothesis": "LAYER_BACKGROUND_RETENTION_DOMINANT",
            "rationale": (
                "CPU/reference high-delta pixels keep opaque white/layer RGB where the reference is red-tinted; "
                "alpha is unchanged, so this is not a coverage-alpha miss."
            ),
            "cpuWhiteLayerShareOfGt32": white_share,
            "referenceRedTintShareOfGt32": red_ref_share,
            "maskExtentOrBlurExtent": "SECONDARY_UNPROVEN_BOUNDARY_QUESTION",
            "colorFilterBlurOrder": "NOT_PRIMARY_FOR_SOLID_BLEND_SRCIN",
        }
    return {
        "hypothesis": "MIXED_LAYER_AND_MASK_EXTENT_BOUNDARY_RESIDUAL",
        "rationale": (
            "The residual remains RGB-only and boundary-local, but the white/layer bucket is not dominant enough "
            "to rule out mask or blur extent effects for this subzone."
        ),
        "cpuWhiteLayerShareOfGt32": white_share,
        "referenceRedTintShareOfGt32": red_ref_share,
        "maskExtentOrBlurExtent": "SECONDARY_POSSIBLE",
        "colorFilterBlurOrder": "NOT_PRIMARY_FOR_SOLID_BLEND_SRCIN",
    }


def source_model() -> dict[str, Any]:
    return {
        "gmPaintChain": {
            "source": str(GM_SOURCE.relative_to(PROJECT_ROOT)),
            "maskFilter": "SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.366025f)",
            "colorFilter": "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)",
            "draw": "c.drawRRect(rr, paint)",
        },
        "cpuEvidence": {
            "source": str(CPU_SOURCE.relative_to(PROJECT_ROOT)),
            "maskFilterPath": "drawPathWithMaskFilter",
            "colorFilterApplication": "transformPaintColor(applyColorFilter(paint.colorFilter, paint.color))",
            "observedPostFor273Polarity": "CPU/reference high-delta pixels are mostly opaque white/layer RGB with unchanged alpha.",
        },
        "webGpuEvidence": {
            "source": str(GPU_SOURCE.relative_to(PROJECT_ROOT)),
            "postFor273Fold": "solidBlurPaintColor(paint) folds only Blend(colour, kSrcIn) for solid blur.",
            "observedPostFor273Polarity": "GPU/reference halo_interior >32 is zero; residual is now mostly boundary-local.",
        },
    }


def generate_audit() -> dict[str, Any]:
    source_for273 = for269.load_json(SOURCE_FOR273)
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
    comparisons = [
        comparison_metrics(
            name="cpu_vs_reference",
            base_label="reference",
            actual_label="cpu",
            images=images,
            masks=masks,
        ),
        comparison_metrics(
            name="gpu_vs_reference",
            base_label="reference",
            actual_label="gpu",
            images=images,
            masks=masks,
        ),
        comparison_metrics(
            name="gpu_vs_cpu",
            base_label="cpu",
            actual_label="gpu",
            images=images,
            masks=masks,
        ),
    ]
    by_name = {item["comparison"]: item for item in comparisons}
    cpu = by_name["cpu_vs_reference"]
    gpu = by_name["gpu_vs_reference"]
    cpu_hypotheses = {
        subzone: classify_cpu_subzone(cpu["subzones"][subzone])
        for subzone in DIAGNOSTIC_SUBZONES
    }
    white_cpu_pixels = sum(
        cpu["subzones"][subzone]["actualColorBucketsOnGt32"]["white_or_layer_background"]
        for subzone in PRIMARY_SUBZONES
    )
    primary_cpu_gt32 = sum(
        cpu["subzones"][subzone]["greaterThanThirtyTwoPixels"]
        for subzone in PRIMARY_SUBZONES
    )
    gpu_primary_gt32 = sum(
        gpu["subzones"][subzone]["greaterThanThirtyTwoPixels"]
        for subzone in PRIMARY_SUBZONES
    )
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "nested-rrect-cpu-reference-layer-mask-audit",
        "sceneId": SCENE_ID,
        "backend": "Reference/CPU/WebGPU",
        "sourceAudit": str(SOURCE_FOR273.relative_to(PROJECT_ROOT)),
        "sourceTest": "org.skia.gpu.webgpu.NestedClipSceneCaptureTest",
        "artifactRoot": str(SCENE_DIR.relative_to(PROJECT_ROOT)),
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "dominantHypothesis": "CPU_LAYER_BACKGROUND_RETENTION_DOMINANT_MASK_EXTENT_SECONDARY",
        "dominantHypothesisRationale": (
            "Post-FOR-273 CPU/reference high-delta pixels remain RGB-only with unchanged alpha. "
            "The primary subzones are mostly opaque white/layer pixels where the reference is red-tinted, "
            "while WebGPU halo_interior is cleared and GPU/reference residual is much smaller. "
            "Mask or blur extent remains a secondary boundary question, but color-filter/blur order is not the dominant explanation."
        ),
        "nextAction": "BOUNDED_CPU_MASK_FILTER_LAYER_BACKGROUND_MODEL_AUDIT_OR_FIXTURE_BEFORE_ANY_SUPPORT_PROMOTION",
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
        "sourceFor273Effect": source_for273.get("effect"),
        "postFor273ComparisonSummary": {
            "cpuReferenceGreaterThanThirtyTwoPixels": cpu["greaterThanThirtyTwoPixels"],
            "gpuReferenceGreaterThanThirtyTwoPixels": gpu["greaterThanThirtyTwoPixels"],
            "cpuReferencePrimarySubzoneGreaterThanThirtyTwoPixels": primary_cpu_gt32,
            "gpuReferencePrimarySubzoneGreaterThanThirtyTwoPixels": gpu_primary_gt32,
            "cpuPrimaryWhiteLayerPixels": white_cpu_pixels,
            "cpuPrimaryWhiteLayerShare": rounded(white_cpu_pixels / max(1, primary_cpu_gt32) * 100.0),
            "gpuHaloInteriorGreaterThanThirtyTwoPixels": gpu["subzones"]["halo_interior"]["greaterThanThirtyTwoPixels"],
            "promotionBlocker": "CPU/reference remains below 99.95 and has more >32 pixels than GPU/reference; GPU-only promotion is invalid.",
        },
        "geometry": geometry,
        "sourceModel": source_model(),
        "comparisons": comparisons,
        "cpuHypothesesBySubzone": cpu_hypotheses,
        "signedRgbSamplesBySubzone": {
            subzone: signed_samples_for_subzone(
                subzone=subzone,
                reference=images["reference"],
                cpu=images["cpu"],
                gpu=images["gpu"],
                masks=masks,
            )
            for subzone in PRIMARY_SUBZONES
        },
        "hypothesisRanking": [
            {
                "hypothesis": "layer_background_retention",
                "classification": "DOMINANT",
                "evidence": "CPU actual pixels in primary >32 subzones are mostly white/layer RGB, reference is red-tinted, and alpha >32 is zero.",
            },
            {
                "hypothesis": "mask_extent_or_blur_extent",
                "classification": "SECONDARY_BOUNDARY_QUESTION",
                "evidence": "Residual is concentrated on oval boundary bands plus halo, so a CPU fixture should isolate blur extent, but current RGB polarity first points to retained layer/fond.",
            },
            {
                "hypothesis": "color_filter_blur_order_cpu",
                "classification": "NOT_PRIMARY",
                "evidence": "For solid Blend(RED,kSrcIn), either side of mask alpha modulation should still carry a red payload; observed CPU output is white/layer.",
            },
            {
                "hypothesis": "explicit_reference_divergence_refusal",
                "classification": "NOT_YET_JUSTIFIED",
                "evidence": "The reference is internally consistent with red-tinted blur pixels. Refusal should wait for a minimized CPU mask-filter fixture.",
            },
        ],
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRendererChanged": False,
            "cpuRendererChanged": False,
            "gpuRendererChanged": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def write_report(audit: dict[str, Any]) -> None:
    cpu = comparison(audit, "cpu_vs_reference")
    gpu = comparison(audit, "gpu_vs_reference")
    gpu_cpu = comparison(audit, "gpu_vs_cpu")
    route = audit["route"]
    summary = audit["postFor273ComparisonSummary"]
    hypotheses = audit["cpuHypothesesBySubzone"]
    samples = audit["signedRgbSamplesBySubzone"]
    sample_rows = []
    for subzone in PRIMARY_SUBZONES:
        for sample in samples[subzone][:2]:
            sample_rows.append(
                "| `{subzone}` | {x},{y} | `{referenceRgba}` | `{cpuRgba}` | `{gpuRgba}` | `{cpuMinusReferenceSignedRgba}` | `{gpuMinusReferenceSignedRgba}` |".format(**sample)
            )
    sample_lines = "\n".join(sample_rows)

    report = f"""# FOR-274 Nested RRect CPU/Reference Layer Mask Audit

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["supportDecision"]}`

Dominant hypothesis: `{audit["dominantHypothesis"]}`.

FOR-274 audits the residual left after FOR-273. The scene stays on route
`{route["gpu"]}` with fallback `{route["fallbackReason"]}`. No renderer path
is changed and no support promotion is claimed: CPU/reference is
`{audit["reportedStats"]["cpuSimilarity"]}` and GPU/reference is
`{audit["reportedStats"]["gpuSimilarity"]}`, both below the strict
`{audit["supportThreshold"]}` threshold.

## Post-FOR-273 Summary

| Metric | Value |
|---|---:|
| CPU/reference >32 pixels | {cpu["greaterThanThirtyTwoPixels"]} |
| GPU/reference >32 pixels | {gpu["greaterThanThirtyTwoPixels"]} |
| GPU/CPU >32 pixels | {gpu_cpu["greaterThanThirtyTwoPixels"]} |
| CPU primary-subzone >32 pixels | {summary["cpuReferencePrimarySubzoneGreaterThanThirtyTwoPixels"]} |
| CPU primary white/layer pixels | {summary["cpuPrimaryWhiteLayerPixels"]} |
| CPU primary white/layer share | {summary["cpuPrimaryWhiteLayerShare"]}% |
| GPU `halo_interior` >32 pixels | {summary["gpuHaloInteriorGreaterThanThirtyTwoPixels"]} |

## Required Subzones

| Subzone | CPU/ref >32 | GPU/ref >32 | CPU signed RGB mean on >32 | CPU actual white/layer | Classification |
|---|---:|---:|---|---:|---|
| `draw_oval_outer_boundary` | {cpu["subzones"]["draw_oval_outer_boundary"]["greaterThanThirtyTwoPixels"]} | {gpu["subzones"]["draw_oval_outer_boundary"]["greaterThanThirtyTwoPixels"]} | `{cpu["subzones"]["draw_oval_outer_boundary"]["signedRgbOnGt32"]["meanSignedRgb"]}` | {cpu["subzones"]["draw_oval_outer_boundary"]["actualColorBucketsOnGt32"]["white_or_layer_background"]} | `{hypotheses["draw_oval_outer_boundary"]["hypothesis"]}` |
| `difference_oval_inner_boundary` | {cpu["subzones"]["difference_oval_inner_boundary"]["greaterThanThirtyTwoPixels"]} | {gpu["subzones"]["difference_oval_inner_boundary"]["greaterThanThirtyTwoPixels"]} | `{cpu["subzones"]["difference_oval_inner_boundary"]["signedRgbOnGt32"]["meanSignedRgb"]}` | {cpu["subzones"]["difference_oval_inner_boundary"]["actualColorBucketsOnGt32"]["white_or_layer_background"]} | `{hypotheses["difference_oval_inner_boundary"]["hypothesis"]}` |
| `halo_interior` | {cpu["subzones"]["halo_interior"]["greaterThanThirtyTwoPixels"]} | {gpu["subzones"]["halo_interior"]["greaterThanThirtyTwoPixels"]} | `{cpu["subzones"]["halo_interior"]["signedRgbOnGt32"]["meanSignedRgb"]}` | {cpu["subzones"]["halo_interior"]["actualColorBucketsOnGt32"]["white_or_layer_background"]} | `{hypotheses["halo_interior"]["hypothesis"]}` |

## Signed RGB Samples

| Subzone | Pixel | Reference RGBA | CPU RGBA | GPU RGBA | CPU-reference signed | GPU-reference signed |
|---|---|---|---|---|---|---|
{sample_lines}

## Interpretation

The CPU/reference residual is RGB-only: every primary subzone reports zero
alpha >32 pixels. CPU pixels are mostly opaque white/layer RGB where the
reference is red-tinted. WebGPU is no longer the dominant blocker after
FOR-273 because `halo_interior` is cleared and GPU/reference >32 is now much
smaller than CPU/reference.

Classification:

- Layer/fond retention: dominant.
- Mask extent or blur extent: secondary boundary question, still worth a
  minimized CPU fixture.
- CPU color-filter/blur order: not primary for this solid
  `Blend(RED, kSrcIn)` chain, because the expected payload should remain red.
- Explicit reference divergence refusal: not justified yet without a smaller
  CPU/reference fixture.

## Next Action

Add a bounded CPU mask-filter/layer fixture for `SkBlurMaskFilter(kNormal)` +
`SkColorFilters.Blend(RED, kSrcIn)` to prove whether the white layer comes from
the CPU layer/fond composite or from blur/mask extent. Do not promote M60 until
CPU/reference and GPU/reference both reach `{audit["supportThreshold"]}` with
route/stat evidence.

## Support Verdict

Keep `expected-unsupported` with fallback
`coverage.nested-clip-visual-parity-below-threshold`. Preserve
`image-filter.crop-input-nonnull-prepass-required`. No threshold weakening,
broad clip-stack support, fallback/readback, Ganesh, Graphite, or SkSL compiler
was added.

## Validation

```text
rtk python3 scripts/validate_for274_nested_rrect_cpu_reference_layer_audit.py
rtk python3 scripts/validate_for273_webgpu_solid_blur_srcin_fold.py
rtk python3 scripts/validate_for272_nested_rrect_blur_color_layer_audit.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`
"""
    REPORT.write_text(report)


def validate_source_needles() -> None:
    checks = (
        (
            GM_SOURCE,
            (
                "SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.366025f)",
                "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)",
                "c.drawRRect(rr, paint)",
            ),
        ),
        (
            CPU_SOURCE,
            (
                "private fun drawPathWithMaskFilter",
                "transformPaintColor(applyColorFilter(paint.colorFilter, paint.color))",
                "val rgb = effectiveColor and 0x00FFFFFF",
            ),
        ),
        (
            GPU_SOURCE,
            (
                "private fun drawPathWithBlurMaskFilterIfApplicable",
                "solidBlurPaintColor(paint)",
                "Blend(colour, kSrcIn)",
            ),
        ),
    )
    for path, needles in checks:
        text = path.read_text()
        for needle in needles:
            if needle not in text:
                fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")


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
    if audit.get("dominantHypothesis") != "CPU_LAYER_BACKGROUND_RETENTION_DOMINANT_MASK_EXTENT_SECONDARY":
        fail("dominant hypothesis mismatch")

    source = for269.load_json(SOURCE_FOR273)
    if source.get("linear") != "FOR-273":
        fail("source audit must be FOR-273")
    effect = source.get("effect", {})
    if effect.get("cpuSimilarityDelta") != 0.0:
        fail("FOR-273 source must not change CPU/reference")

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
    if reported.get("cpuSimilarity") != 97.31:
        fail("post-FOR-273 CPU/reference similarity changed")
    if reported.get("gpuSimilarity") != 98.48:
        fail("post-FOR-273 GPU/reference similarity changed")
    if reported.get("cpuSimilarity", 0.0) >= for269.SUPPORT_THRESHOLD:
        fail("CPU/reference unexpectedly reaches support threshold")
    if reported.get("gpuSimilarity", 0.0) >= for269.SUPPORT_THRESHOLD:
        fail("GPU/reference unexpectedly reaches support threshold")

    cpu = comparison(audit, "cpu_vs_reference")
    gpu = comparison(audit, "gpu_vs_reference")
    if cpu["greaterThanThirtyTwoPixels"] != 15726:
        fail("CPU/reference >32 residual changed")
    if gpu["greaterThanThirtyTwoPixels"] != 2869:
        fail("GPU/reference >32 residual changed")
    required_cpu_gt32 = {
        "draw_oval_outer_boundary": 8077,
        "difference_oval_inner_boundary": 5201,
        "halo_interior": 2448,
    }
    required_gpu_gt32 = {
        "draw_oval_outer_boundary": 2796,
        "difference_oval_inner_boundary": 73,
        "halo_interior": 0,
    }
    for subzone, expected in required_cpu_gt32.items():
        zone = cpu["subzones"][subzone]
        if zone["greaterThanThirtyTwoPixels"] != expected:
            fail(f"CPU/reference {subzone} >32 changed")
        if zone["signedRgbOnGt32"]["alphaGreaterThanThirtyTwoPixels"] != 0:
            fail(f"CPU/reference {subzone} alpha >32 must remain zero")
        if zone["actualColorBucketsOnGt32"]["white_or_layer_background"] / max(1, expected) < 0.70:
            fail(f"CPU/reference {subzone} must remain mostly white/layer")
    for subzone, expected in required_gpu_gt32.items():
        if gpu["subzones"][subzone]["greaterThanThirtyTwoPixels"] != expected:
            fail(f"GPU/reference {subzone} >32 changed")

    summary = audit.get("postFor273ComparisonSummary", {})
    if summary.get("cpuPrimaryWhiteLayerShare", 0.0) < 80.0:
        fail("CPU primary white/layer share too weak for dominant hypothesis")
    if summary.get("gpuHaloInteriorGreaterThanThirtyTwoPixels") != 0:
        fail("FOR-273 halo-interior WebGPU fix must remain visible")

    hypotheses = audit.get("cpuHypothesesBySubzone", {})
    for subzone in PRIMARY_SUBZONES:
        if hypotheses.get(subzone, {}).get("hypothesis") != "LAYER_BACKGROUND_RETENTION_DOMINANT":
            fail(f"{subzone} hypothesis mismatch")
    samples = audit.get("signedRgbSamplesBySubzone", {})
    for subzone in PRIMARY_SUBZONES:
        if not samples.get(subzone):
            fail(f"{subzone} signed samples missing")
    halo_first = samples["halo_interior"][0]
    if halo_first["cpuMinusReferenceSignedRgba"][3] != 0:
        fail("halo sample must retain alpha equality")
    if halo_first["cpuMinusReferenceSignedRgba"][1] <= 0 or halo_first["cpuMinusReferenceSignedRgba"][2] <= 0:
        fail("halo sample must show positive CPU-reference green/blue deltas")

    strict = audit.get("strictPreservation", {})
    for field in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "productionRendererChanged",
        "cpuRendererChanged",
        "gpuRendererChanged",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if strict.get(field) is not False:
            fail(f"strict preservation field `{field}` must be false")
    if strict.get("cropFallbackPreserved") != for269.CROP_FALLBACK_REASON:
        fail("Crop fallback preservation mismatch")
    if strict.get("visualParityFallbackPreserved") != for269.FALLBACK_REASON:
        fail("visual parity fallback preservation mismatch")

    validate_source_needles()
    text = REPORT.read_text()
    for needle in (
        "FOR-274 Nested RRect CPU/Reference Layer Mask Audit",
        "KEEP_EXPECTED_UNSUPPORTED",
        "CPU_LAYER_BACKGROUND_RETENTION_DOMINANT_MASK_EXTENT_SECONDARY",
        "LAYER_BACKGROUND_RETENTION_DOMINANT",
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
        "FOR-274 validation passed: CPU/reference residual is audited post-FOR-273, "
        "layer/background retention is the dominant hypothesis, mask extent remains secondary, "
        "GPU-only promotion is rejected, and expected-unsupported diagnostics are preserved."
    )


if __name__ == "__main__":
    main()
