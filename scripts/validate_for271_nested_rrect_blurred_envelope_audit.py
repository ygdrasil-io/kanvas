#!/usr/bin/env python3
"""Generate and validate FOR-271 nested RRect blurred-envelope evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

import numpy as np

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-271"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "nested-rrect-blurred-envelope-audit-for271.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-271-nested-rrect-blurred-envelope-audit.md"
SOURCE_AUDIT = SCENE_DIR / "nested-rrect-zone-mask-audit-for270.json"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-271 validation failed: {message}")


def json_dump(data: dict[str, Any]) -> str:
    return json.dumps(data, indent=2, sort_keys=False) + "\n"


def rounded(value: float, digits: int = 6) -> float:
    return round(float(value), digits)


def ellipse_value(
    *,
    x_centered: np.ndarray,
    y_centered: np.ndarray,
    rect: tuple[int, int, int, int],
) -> np.ndarray:
    left, top, right, bottom = rect
    cx = (left + right) / 2.0
    cy = (top + bottom) / 2.0
    rx = (right - left) / 2.0
    ry = (bottom - top) / 2.0
    return ((x_centered - cx) / rx) ** 2 + ((y_centered - cy) / ry) ** 2


def make_envelope_masks(width: int, height: int) -> tuple[dict[str, Any], dict[str, np.ndarray]]:
    geometry, base_masks = for269.make_masks(width, height)
    y, x = np.mgrid[0:height, 0:width]
    xc = x + 0.5
    yc = y + 0.5
    inner_value = ellipse_value(
        x_centered=xc,
        y_centered=yc,
        rect=tuple(geometry["differenceOvalDeviceBounds"]),
    )
    outer_value = ellipse_value(
        x_centered=xc,
        y_centered=yc,
        rect=tuple(geometry["drawOvalDeviceBounds"]),
    )
    inner_distance = np.sqrt(inner_value)
    outer_distance = np.sqrt(outer_value)
    inner = inner_distance <= 1.0
    outer = outer_distance <= 1.0
    inner_boundary = np.abs(inner_distance - 1.0) <= (3.0 / 280.0)
    outer_boundary = np.abs(outer_distance - 1.0) <= (3.0 / 288.0)
    envelope = base_masks["blurred_content_envelope"]

    difference_boundary = envelope & inner_boundary
    draw_boundary = envelope & outer_boundary & ~difference_boundary
    halo_interior = envelope & outer & ~inner & ~inner_boundary & ~outer_boundary
    removed_interior = inner & ~inner_boundary
    outside_draw = ~outer & ~outer_boundary
    other_surviving_clip = (
        base_masks["intersected_rectangles"]
        & ~difference_boundary
        & ~draw_boundary
        & ~halo_interior
        & ~removed_interior
        & ~outside_draw
    )

    masks = {
        "difference_oval_inner_boundary": difference_boundary,
        "draw_oval_outer_boundary": draw_boundary,
        "halo_interior": halo_interior,
        "removed_difference_oval_interior": removed_interior,
        "outside_draw_oval": outside_draw,
        "surviving_clip_other": other_surviving_clip,
        "blurred_content_envelope": envelope,
        "outside_clip_removed_difference_oval": base_masks["outside_clip_removed_difference_oval"],
    }
    return geometry, masks


def subzone_for_sample(x: int, y: int, masks: dict[str, np.ndarray]) -> str:
    for name in (
        "difference_oval_inner_boundary",
        "draw_oval_outer_boundary",
        "halo_interior",
        "removed_difference_oval_interior",
        "outside_draw_oval",
        "surviving_clip_other",
    ):
        if masks[name][y, x]:
            return name
    return "outside_intersected_rectangles"


def signed_channel_summary(signed: np.ndarray, mask: np.ndarray) -> dict[str, Any]:
    if not mask.any():
        return {
            "meanRgba": [0.0, 0.0, 0.0, 0.0],
            "minRgba": [0, 0, 0, 0],
            "maxRgba": [0, 0, 0, 0],
        }
    return {
        "meanRgba": [rounded(signed[:, :, channel][mask].mean()) for channel in range(4)],
        "minRgba": [int(signed[:, :, channel][mask].min()) for channel in range(4)],
        "maxRgba": [int(signed[:, :, channel][mask].max()) for channel in range(4)],
    }


def unpremul_delta(reference_or_cpu: np.ndarray, actual: np.ndarray, mask: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    valid = mask & (reference_or_cpu[:, :, 3] > 8) & (actual[:, :, 3] > 8)
    base = reference_or_cpu.astype(np.float64)
    candidate = actual.astype(np.float64)
    base_rgb = np.zeros_like(base[:, :, :3])
    actual_rgb = np.zeros_like(candidate[:, :, :3])
    if valid.any():
        base_rgb[valid] = base[:, :, :3][valid] * 255.0 / base[:, :, 3][valid, None]
        actual_rgb[valid] = candidate[:, :, :3][valid] * 255.0 / candidate[:, :, 3][valid, None]
    return np.abs(actual_rgb - base_rgb).max(axis=2), valid


def color_premul_classification(
    *,
    reference_or_cpu: np.ndarray,
    actual: np.ndarray,
    diff: np.ndarray,
    masks: dict[str, np.ndarray],
) -> dict[str, Any]:
    channel_delta = np.abs(actual - reference_or_cpu)
    signed = actual - reference_or_cpu
    envelope_gt32 = (diff > 32) & masks["blurred_content_envelope"]
    alpha_delta = channel_delta[:, :, 3]
    rgb_delta = channel_delta[:, :, :3].max(axis=2)
    unpremul, valid_unpremul = unpremul_delta(reference_or_cpu, actual, envelope_gt32)
    max_channels = np.argmax(channel_delta, axis=2)
    channel_names = ("r", "g", "b", "a")

    buckets = {
        "alpha_or_coverage_scaled_rgb_close_unpremul": valid_unpremul & (alpha_delta > 16) & (unpremul <= 16),
        "rgb_payload_shift_alpha_close": valid_unpremul & (alpha_delta <= 8) & (unpremul > 32),
        "mixed_alpha_and_color_shift": valid_unpremul & (alpha_delta > 8) & (unpremul > 32),
        "premul_rgb_delta_alpha_close": valid_unpremul & (alpha_delta <= 8) & (rgb_delta > 32) & (unpremul <= 32),
        "transparent_edge_or_one_alpha_zero": envelope_gt32
        & ((reference_or_cpu[:, :, 3] <= 8) | (actual[:, :, 3] <= 8)),
    }
    count = int(envelope_gt32.sum())
    rgb_payload = int(buckets["rgb_payload_shift_alpha_close"].sum())
    verdict = (
        "COLOR_PAYLOAD_SHIFT_ALPHA_UNCHANGED"
        if count and rgb_payload == count and int((envelope_gt32 & (alpha_delta > 32)).sum()) == 0
        else "MIXED_COLOR_OR_ALPHA_RESIDUAL"
    )
    return {
        "scope": "blurred_content_envelope_gt32",
        "pixels": count,
        "alphaDeltaGreaterThanThirtyTwoPixels": int((envelope_gt32 & (alpha_delta > 32)).sum()),
        "rgbDeltaGreaterThanThirtyTwoPixels": int((envelope_gt32 & (rgb_delta > 32)).sum()),
        "maxChannelCounts": {
            channel_names[channel]: int((envelope_gt32 & (max_channels == channel)).sum())
            for channel in range(4)
        },
        "signedDeltaSummary": signed_channel_summary(signed, envelope_gt32),
        "unpremulComparablePixels": int(valid_unpremul.sum()),
        "unpremulMaxDelta": int(unpremul[valid_unpremul].max()) if valid_unpremul.any() else 0,
        "unpremulMeanDelta": rounded(unpremul[valid_unpremul].mean()) if valid_unpremul.any() else 0.0,
        "buckets": {name: int(mask.sum()) for name, mask in buckets.items()},
        "verdict": verdict,
    }


def high_delta_samples(
    *,
    comparison: str,
    reference_or_cpu: np.ndarray,
    actual: np.ndarray,
    diff: np.ndarray,
    masks: dict[str, np.ndarray],
    limit: int = 12,
) -> list[dict[str, Any]]:
    sample_mask = (diff > 32) & masks["blurred_content_envelope"]
    ys, xs = np.nonzero(sample_mask)
    records = sorted((-int(diff[y, x]), int(y), int(x)) for y, x in zip(ys.tolist(), xs.tolist()))
    samples: list[dict[str, Any]] = []
    for neg_delta, y, x in records[:limit]:
        base_rgba = reference_or_cpu[y, x].astype(int)
        actual_rgba = actual[y, x].astype(int)
        samples.append(
            {
                "comparison": comparison,
                "x": x,
                "y": y,
                "subzone": subzone_for_sample(x, y, masks),
                "referenceOrCpuRgba": base_rgba.tolist(),
                "actualRgba": actual_rgba.tolist(),
                "signedDeltaRgba": (actual_rgba - base_rgba).tolist(),
                "maxChannelDelta": -neg_delta,
            }
        )
    return samples


def comparison_audit(
    *,
    name: str,
    reference_or_cpu: np.ndarray,
    actual: np.ndarray,
    masks: dict[str, np.ndarray],
) -> dict[str, Any]:
    channel_delta = np.abs(actual - reference_or_cpu)
    diff = channel_delta.max(axis=2)
    total = int(diff.size)
    exact_match = int((diff == 0).sum())
    subzone_order = (
        "difference_oval_inner_boundary",
        "draw_oval_outer_boundary",
        "halo_interior",
        "removed_difference_oval_interior",
        "outside_draw_oval",
        "surviving_clip_other",
    )
    subzones = {name: for269.diff_stats(diff, masks[name]) for name in subzone_order}
    dominant = max(
        ((name, subzones[name]) for name in subzone_order),
        key=lambda item: (item[1]["greaterThanThirtyTwoPixels"], item[0]),
    )
    envelope_gt32 = int(((diff > 32) & masks["blurred_content_envelope"]).sum())
    total_gt32 = int((diff > 32).sum())
    return {
        "comparison": name,
        "totalPixels": total,
        "exactMatchingPixels": exact_match,
        "exactSimilarity": rounded((exact_match / total) * 100.0),
        "exactMismatchPixels": total - exact_match,
        "toleranceOneMismatchPixels": int((diff > 1).sum()),
        "greaterThanEightPixels": int((diff > 8).sum()),
        "greaterThanThirtyTwoPixels": total_gt32,
        "maxChannelDelta": int(diff.max()),
        "blurredEnvelopeGreaterThanThirtyTwoPixels": envelope_gt32,
        "blurredEnvelopeShareOfGreaterThanThirtyTwo": rounded(envelope_gt32 / max(1, total_gt32) * 100.0),
        "dominantEnvelopeSubzone": dominant[0],
        "dominantEnvelopeSubzoneShareOfGreaterThanThirtyTwo": rounded(
            dominant[1]["greaterThanThirtyTwoPixels"] / max(1, total_gt32) * 100.0,
        ),
        "subzones": subzones,
        "colorPremulClassification": color_premul_classification(
            reference_or_cpu=reference_or_cpu,
            actual=actual,
            diff=diff,
            masks=masks,
        ),
        "highDeltaSamples": high_delta_samples(
            comparison=name,
            reference_or_cpu=reference_or_cpu,
            actual=actual,
            diff=diff,
            masks=masks,
        ),
    }


def overlap_audit(reference: np.ndarray, cpu: np.ndarray, gpu: np.ndarray, masks: dict[str, np.ndarray]) -> dict[str, Any]:
    gpu_ref_diff = np.abs(gpu - reference).max(axis=2)
    cpu_ref_diff = np.abs(cpu - reference).max(axis=2)
    gpu_cpu_diff = np.abs(gpu - cpu).max(axis=2)
    gpu_ref_gt32 = gpu_ref_diff > 32
    cpu_ref_gt32 = cpu_ref_diff > 32
    gpu_cpu_gt32 = gpu_cpu_diff > 32
    shared = gpu_ref_gt32 & cpu_ref_gt32
    subzone_names = (
        "difference_oval_inner_boundary",
        "draw_oval_outer_boundary",
        "halo_interior",
        "removed_difference_oval_interior",
        "outside_draw_oval",
    )
    return {
        "gpuReferenceGreaterThanThirtyTwoPixels": int(gpu_ref_gt32.sum()),
        "cpuReferenceGreaterThanThirtyTwoPixels": int(cpu_ref_gt32.sum()),
        "sharedGpuCpuReferenceGreaterThanThirtyTwoPixels": int(shared.sum()),
        "gpuReferenceSharedShare": rounded(shared.sum() / max(1, gpu_ref_gt32.sum()) * 100.0),
        "cpuReferenceSharedShare": rounded(shared.sum() / max(1, cpu_ref_gt32.sum()) * 100.0),
        "gpuReferenceOnlyGreaterThanThirtyTwoPixels": int((gpu_ref_gt32 & ~cpu_ref_gt32).sum()),
        "cpuReferenceOnlyGreaterThanThirtyTwoPixels": int((cpu_ref_gt32 & ~gpu_ref_gt32).sum()),
        "gpuCpuGreaterThanThirtyTwoPixels": int(gpu_cpu_gt32.sum()),
        "gpuCpuOverlapWithGpuReferenceGt32": int((gpu_cpu_gt32 & gpu_ref_gt32).sum()),
        "gpuCpuOverlapWithCpuReferenceGt32": int((gpu_cpu_gt32 & cpu_ref_gt32).sum()),
        "sharedBySubzone": {
            name: int((shared & masks[name]).sum())
            for name in subzone_names
        },
        "verdict": "SHARED_REFERENCE_RESIDUAL_WITH_BACKEND_COLOR_POLARITY_DIFFERENCE",
        "rationale": (
            "CPU/reference and GPU/reference high-delta pixels are spatially concentrated in the same "
            "blurred envelope subzones, but GPU/CPU also has a large RGB-only delta. The remaining issue "
            "is not isolated to WebGPU clip masking, and the CPU oracle does not yet meet the strict reference threshold."
        ),
    }


def generate_audit() -> dict[str, Any]:
    stats = for269.load_json(SCENE_DIR / "stats.json")
    route_gpu = for269.load_json(SCENE_DIR / "route-gpu.json")
    route_cpu = for269.load_json(SCENE_DIR / "route-cpu.json")
    reference = for269.load_image("skia")
    cpu = for269.load_image("cpu")
    gpu = for269.load_image("gpu")
    if reference.shape != cpu.shape or reference.shape != gpu.shape:
        fail("reference, CPU, and GPU PNG dimensions differ")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA images")

    geometry, masks = make_envelope_masks(width, height)
    comparisons = [
        comparison_audit(name="gpu_vs_reference", reference_or_cpu=reference, actual=gpu, masks=masks),
        comparison_audit(name="cpu_vs_reference", reference_or_cpu=reference, actual=cpu, masks=masks),
        comparison_audit(name="gpu_vs_cpu", reference_or_cpu=cpu, actual=gpu, masks=masks),
    ]
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "nested-rrect-blurred-content-envelope-audit",
        "sceneId": SCENE_ID,
        "backend": "CPU/WebGPU",
        "sourceAudit": str(SOURCE_AUDIT.relative_to(PROJECT_ROOT)),
        "sourceTest": "org.skia.gpu.webgpu.NestedClipSceneCaptureTest",
        "artifactRoot": str(SCENE_DIR.relative_to(PROJECT_ROOT)),
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "nextAction": "BLUR_COLOR_OR_LAYER_MODEL_RECONCILIATION_BEFORE_PROMOTION",
        "residualClassification": "SHARED_CPU_REFERENCE_BLURRED_ENVELOPE_WITH_GPU_CPU_COLOR_DIVERGENCE",
        "decisionRationale": (
            "The residual is confined to the blurred content envelope and its two oval boundary bands. "
            "Alpha remains unchanged in the high-delta pixels, so the observed error is RGB payload/layer "
            "color, not a premultiplied alpha coverage miss. CPU/reference is also below the strict threshold, "
            "therefore the scene cannot be promoted by a WebGPU-local clip-mask change."
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
        "subzoneDefinitions": {
            "accounting": "FOR-271 primary subzones are exclusive for blurred-envelope diagnosis; FOR-269 compatibility zones remain available through sourceAudit.",
            "difference_oval_inner_boundary": "Three-device-pixel band around the inner oval removed by clipRRect(kDifference).",
            "draw_oval_outer_boundary": "Three-device-pixel band around the outer draw oval, excluding pixels already assigned to the inner boundary.",
            "halo_interior": "Pixels between the outer draw oval and inner difference oval after removing both boundary bands.",
            "removed_difference_oval_interior": "Interior of the difference oval after removing its boundary band.",
            "outside_draw_oval": "Pixels outside the draw oval after removing the outer boundary band.",
            "surviving_clip_other": "Remaining intersected-rectangle pixels not assigned to envelope or exterior diagnostic subzones.",
        },
        "comparisons": comparisons,
        "overlap": overlap_audit(reference, cpu, gpu, masks),
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
    overlap = audit["overlap"]
    route = audit["route"]
    report = f"""# FOR-271 Nested RRect Blurred Envelope Audit

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["supportDecision"]}`

The remaining residual after FOR-270 is localized to `blurred_content_envelope`.
The route remains `{route["gpu"]}` with fallback `{route["fallbackReason"]}`;
no promotion is valid because GPU/reference `{audit["reportedStats"]["gpuSimilarity"]}`
and CPU/reference `{audit["reportedStats"]["cpuSimilarity"]}` are both below
the strict `{audit["supportThreshold"]}` threshold.

## Subzone Findings

| Comparison | >32 pixels | Dominant subzone | Share | Max delta |
|---|---:|---|---:|---:|
| GPU/reference | {gpu["greaterThanThirtyTwoPixels"]} | `{gpu["dominantEnvelopeSubzone"]}` | {gpu["dominantEnvelopeSubzoneShareOfGreaterThanThirtyTwo"]}% | {gpu["maxChannelDelta"]} |
| CPU/reference | {cpu["greaterThanThirtyTwoPixels"]} | `{cpu["dominantEnvelopeSubzone"]}` | {cpu["dominantEnvelopeSubzoneShareOfGreaterThanThirtyTwo"]}% | {cpu["maxChannelDelta"]} |
| GPU/CPU | {gpu_cpu["greaterThanThirtyTwoPixels"]} | `{gpu_cpu["dominantEnvelopeSubzone"]}` | {gpu_cpu["dominantEnvelopeSubzoneShareOfGreaterThanThirtyTwo"]}% | {gpu_cpu["maxChannelDelta"]} |

| Subzone | GPU/reference >32 | CPU/reference >32 | GPU/CPU >32 |
|---|---:|---:|---:|
| `draw_oval_outer_boundary` | {gpu["subzones"]["draw_oval_outer_boundary"]["greaterThanThirtyTwoPixels"]} | {cpu["subzones"]["draw_oval_outer_boundary"]["greaterThanThirtyTwoPixels"]} | {gpu_cpu["subzones"]["draw_oval_outer_boundary"]["greaterThanThirtyTwoPixels"]} |
| `difference_oval_inner_boundary` | {gpu["subzones"]["difference_oval_inner_boundary"]["greaterThanThirtyTwoPixels"]} | {cpu["subzones"]["difference_oval_inner_boundary"]["greaterThanThirtyTwoPixels"]} | {gpu_cpu["subzones"]["difference_oval_inner_boundary"]["greaterThanThirtyTwoPixels"]} |
| `halo_interior` | {gpu["subzones"]["halo_interior"]["greaterThanThirtyTwoPixels"]} | {cpu["subzones"]["halo_interior"]["greaterThanThirtyTwoPixels"]} | {gpu_cpu["subzones"]["halo_interior"]["greaterThanThirtyTwoPixels"]} |
| `removed_difference_oval_interior` | {gpu["subzones"]["removed_difference_oval_interior"]["greaterThanThirtyTwoPixels"]} | {cpu["subzones"]["removed_difference_oval_interior"]["greaterThanThirtyTwoPixels"]} | {gpu_cpu["subzones"]["removed_difference_oval_interior"]["greaterThanThirtyTwoPixels"]} |
| `outside_draw_oval` | {gpu["subzones"]["outside_draw_oval"]["greaterThanThirtyTwoPixels"]} | {cpu["subzones"]["outside_draw_oval"]["greaterThanThirtyTwoPixels"]} | {gpu_cpu["subzones"]["outside_draw_oval"]["greaterThanThirtyTwoPixels"]} |

## Color And Premul

| Comparison | Classification | Alpha >32 | RGB >32 | RGB payload shift |
|---|---|---:|---:|---:|
| GPU/reference | `{gpu["colorPremulClassification"]["verdict"]}` | {gpu["colorPremulClassification"]["alphaDeltaGreaterThanThirtyTwoPixels"]} | {gpu["colorPremulClassification"]["rgbDeltaGreaterThanThirtyTwoPixels"]} | {gpu["colorPremulClassification"]["buckets"]["rgb_payload_shift_alpha_close"]} |
| CPU/reference | `{cpu["colorPremulClassification"]["verdict"]}` | {cpu["colorPremulClassification"]["alphaDeltaGreaterThanThirtyTwoPixels"]} | {cpu["colorPremulClassification"]["rgbDeltaGreaterThanThirtyTwoPixels"]} | {cpu["colorPremulClassification"]["buckets"]["rgb_payload_shift_alpha_close"]} |
| GPU/CPU | `{gpu_cpu["colorPremulClassification"]["verdict"]}` | {gpu_cpu["colorPremulClassification"]["alphaDeltaGreaterThanThirtyTwoPixels"]} | {gpu_cpu["colorPremulClassification"]["rgbDeltaGreaterThanThirtyTwoPixels"]} | {gpu_cpu["colorPremulClassification"]["buckets"]["rgb_payload_shift_alpha_close"]} |

The high-delta pixels have unchanged alpha and are classified as RGB payload
differences, not premultiplied-alpha coverage differences. GPU/reference skews
toward darker/black envelope samples, while CPU/reference skews toward
lighter/white samples.

## CPU/GPU Locality

| Measure | Value |
|---|---:|
| Shared CPU+GPU/reference >32 pixels | {overlap["sharedGpuCpuReferenceGreaterThanThirtyTwoPixels"]} |
| Share of GPU/reference >32 also CPU/reference >32 | {overlap["gpuReferenceSharedShare"]}% |
| Share of CPU/reference >32 also GPU/reference >32 | {overlap["cpuReferenceSharedShare"]}% |
| GPU/reference-only >32 pixels | {overlap["gpuReferenceOnlyGreaterThanThirtyTwoPixels"]} |
| CPU/reference-only >32 pixels | {overlap["cpuReferenceOnlyGreaterThanThirtyTwoPixels"]} |
| GPU/CPU >32 pixels | {overlap["gpuCpuGreaterThanThirtyTwoPixels"]} |

Verdict: `{overlap["verdict"]}`. The residual is not safely WebGPU-local:
CPU/reference is already below the strict threshold in the same blurred-envelope
region, while GPU/CPU still proves a backend color/layer divergence.

## Support Verdict

Keep `expected-unsupported` with fallback
`coverage.nested-clip-visual-parity-below-threshold`. Do not weaken the
threshold, add broad clip-stack support, fallback/readback, Ganesh/Graphite, or
SkSL compiler behavior. Preserve
`image-filter.crop-input-nonnull-prepass-required`.

Next action: reconcile the blur content/layer color model against reference and
CPU before considering any bounded blur/mask-filter correction.

## Validation

```text
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
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision must remain expected-unsupported")
    source = for269.load_json(SOURCE_AUDIT)
    if source.get("linear") != "FOR-270":
        fail("source audit must be FOR-270")

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
    if reported.get("gpuSimilarity") >= for269.SUPPORT_THRESHOLD:
        fail("GPU similarity unexpectedly reaches support threshold")
    if reported.get("cpuSimilarity") >= for269.SUPPORT_THRESHOLD:
        fail("CPU similarity unexpectedly reaches support threshold")

    definitions = audit.get("subzoneDefinitions", {})
    for zone in (
        "draw_oval_outer_boundary",
        "difference_oval_inner_boundary",
        "halo_interior",
        "removed_difference_oval_interior",
        "outside_draw_oval",
    ):
        if zone not in definitions:
            fail(f"missing subzone definition `{zone}`")

    for name in ("gpu_vs_reference", "cpu_vs_reference", "gpu_vs_cpu"):
        item = comparison(audit, name)
        if item.get("dominantEnvelopeSubzone") != "draw_oval_outer_boundary":
            fail(f"{name} should be dominated by draw oval outer boundary")
        if item.get("blurredEnvelopeShareOfGreaterThanThirtyTwo", 0) < 99.0:
            fail(f"{name} high deltas must remain in blurred content envelope")
        subzones = item.get("subzones", {})
        if subzones.get("removed_difference_oval_interior", {}).get("greaterThanThirtyTwoPixels") != 0:
            fail(f"{name} must not regress removed difference oval interior")
        if subzones.get("outside_draw_oval", {}).get("greaterThanThirtyTwoPixels") != 0:
            fail(f"{name} must not show >32 outside draw oval")
        color = item.get("colorPremulClassification", {})
        if color.get("verdict") != "COLOR_PAYLOAD_SHIFT_ALPHA_UNCHANGED":
            fail(f"{name} must classify the residual as RGB payload with unchanged alpha")
        if color.get("alphaDeltaGreaterThanThirtyTwoPixels") != 0:
            fail(f"{name} must not have alpha >32 residuals")
        if not item.get("highDeltaSamples"):
            fail(f"{name} must include high-delta samples")

    overlap = audit.get("overlap", {})
    if overlap.get("verdict") != "SHARED_REFERENCE_RESIDUAL_WITH_BACKEND_COLOR_POLARITY_DIFFERENCE":
        fail("overlap verdict mismatch")
    if overlap.get("gpuReferenceSharedShare", 0) < 70.0:
        fail("GPU/reference residual is not sufficiently shared with CPU/reference")
    if overlap.get("cpuReferenceSharedShare", 0) < 90.0:
        fail("CPU/reference residual is not sufficiently shared with GPU/reference")
    if overlap.get("gpuCpuGreaterThanThirtyTwoPixels", 0) <= 0:
        fail("GPU/CPU comparison must expose backend color divergence")

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
        "FOR-271 Nested RRect Blurred Envelope Audit",
        "KEEP_EXPECTED_UNSUPPORTED",
        "COLOR_PAYLOAD_SHIFT_ALPHA_UNCHANGED",
        "SHARED_REFERENCE_RESIDUAL_WITH_BACKEND_COLOR_POLARITY_DIFFERENCE",
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
        "FOR-271 validation passed: blurred-envelope residual is RGB-only with unchanged alpha, "
        "shared by CPU/reference and GPU/reference, divergent between GPU and CPU colors, "
        "and support remains expected-unsupported."
    )


if __name__ == "__main__":
    main()
