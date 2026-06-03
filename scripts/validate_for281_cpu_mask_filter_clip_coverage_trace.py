#!/usr/bin/env python3
"""Generate and validate FOR-281 CPU mask-filter/clip coverage trace evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any

import numpy as np

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for271_nested_rrect_blurred_envelope_audit as for271
import validate_for278_m60_boundary_layer_composite_fixture as for278
import validate_for280_cpu_aa_difference_clip_coverage_edge as for280


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-281"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "m60-cpu-mask-filter-clip-coverage-trace-for281.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-281-cpu-mask-filter-clip-coverage-trace.md"
SOURCE_FOR278 = SCENE_DIR / "m60-boundary-layer-composite-fixture-for278.json"
SOURCE_FOR279 = SCENE_DIR / "m60-cpu-layer-boundary-composite-for279.json"
SOURCE_FOR280 = SCENE_DIR / "m60-cpu-aa-difference-clip-coverage-edge-for280.json"
STATS = SCENE_DIR / "stats.json"
GM_SOURCE = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
BLUR_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBlurMaskFilter.kt"

SIGMA = 1.366025
PAINT_ALPHA = 255
DECISION = "REFUSE_CORRECTION_PENDING_MASK_SOURCE_COLOR_FILTER_BLEND_PARITY"
NEXT_ACTION = "TARGET_CPU_MASK_FILTER_COLOR_FILTER_SRCIN_BLEND_PARITY"
PRIMARY_WINDOWS = (
    "draw_oval_outer_boundary",
    "difference_oval_inner_boundary",
    "halo_interior",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-281 validation failed: {message}")


def json_dump(data: dict[str, Any]) -> str:
    return json.dumps(data, indent=2, sort_keys=False) + "\n"


def rounded(value: float, digits: int = 6) -> float:
    return round(float(value), digits)


def load_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    if not isinstance(data, dict):
        fail(f"{path.relative_to(PROJECT_ROOT)} must contain a JSON object")
    return data


def require_needles(path: Path, needles: tuple[str, ...]) -> str:
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")
    return text


def coverage_bucket(value: int) -> str:
    if value == 0:
        return "zero"
    if value == 255:
        return "full"
    return "partial"


def bucket_summary(values: list[int]) -> dict[str, Any]:
    buckets = {"zero": 0, "partial": 0, "full": 0}
    for value in values:
        buckets[coverage_bucket(value)] += 1
    return {
        "buckets": buckets,
        "min": int(min(values)) if values else 0,
        "max": int(max(values)) if values else 0,
        "mean": rounded(sum(values) / len(values)) if values else 0.0,
    }


def mask_from_window(window: dict[str, Any], masks: dict[str, np.ndarray]) -> np.ndarray:
    bounds = window["bounds"]
    height, width = next(iter(masks.values())).shape
    mask = np.zeros((height, width), dtype=bool)
    mask[bounds["top"] : bounds["bottom"], bounds["left"] : bounds["right"]] = True
    return mask & masks[window["subzone"]]


def gaussian_kernel_1d(sigma: float, radius: int) -> np.ndarray:
    two_sigma_sq = 2.0 * sigma * sigma
    kernel = np.array(
        [math.exp(-float((i - radius) * (i - radius)) / two_sigma_sq) for i in range(2 * radius + 1)],
        dtype=np.float64,
    )
    return kernel / kernel.sum()


def blur_horizontal(src: np.ndarray, kernel: np.ndarray, radius: int) -> np.ndarray:
    height, width = src.shape
    dst = np.zeros_like(src, dtype=np.uint8)
    for y in range(height):
        for x in range(width):
            acc = 0.0
            for k in range(-radius, radius + 1):
                xi = x + k
                value = int(src[y, xi]) if 0 <= xi < width else 0
                acc += value * float(kernel[k + radius])
            dst[y, x] = max(0, min(255, int(acc + 0.5)))
    return dst


def blur_vertical(src: np.ndarray, kernel: np.ndarray, radius: int) -> np.ndarray:
    height, width = src.shape
    dst = np.zeros_like(src, dtype=np.uint8)
    for y in range(height):
        for x in range(width):
            acc = 0.0
            for k in range(-radius, radius + 1):
                yi = y + k
                value = int(src[yi, x]) if 0 <= yi < height else 0
                acc += value * float(kernel[k + radius])
            dst[y, x] = max(0, min(255, int(acc + 0.5)))
    return dst


def mask_bounds_for_draw_oval(width: int, height: int, draw_oval: list[int]) -> dict[str, int]:
    radius = math.ceil(3.0 * SIGMA)
    left, top, right, bottom = draw_oval
    return {
        "left": max(0, math.floor(left - 1.0) - radius),
        "top": max(0, math.floor(top - 1.0) - radius),
        "right": min(width, math.ceil(right + 1.0) + radius),
        "bottom": min(height, math.ceil(bottom + 1.0) + radius),
        "margin": radius,
    }


def generate_blurred_mask(width: int, height: int, geometry: dict[str, Any]) -> tuple[np.ndarray, dict[str, int]]:
    draw_oval = geometry["drawOvalDeviceBounds"]
    bounds = mask_bounds_for_draw_oval(width, height, draw_oval)
    mask_width = bounds["right"] - bounds["left"]
    mask_height = bounds["bottom"] - bounds["top"]
    if mask_width <= 0 or mask_height <= 0:
        fail("empty mask-filter trace bounds")
    source = np.zeros((mask_height, mask_width), dtype=np.uint8)
    for y in range(mask_height):
        for x in range(mask_width):
            source[y, x] = for280.ellipse_coverage_4x4(
                bounds["left"] + x,
                bounds["top"] + y,
                draw_oval,
            )
    kernel = gaussian_kernel_1d(SIGMA, bounds["margin"])
    horizontal = blur_horizontal(source, kernel, bounds["margin"])
    blurred = blur_vertical(horizontal, kernel, bounds["margin"])
    return blurred, bounds


def alpha_after_clip(src_alpha_before_clip: int, clip_coverage: int) -> int:
    return (src_alpha_before_clip * clip_coverage + 127) // 255


def trace_pixel(
    *,
    x: int,
    y: int,
    blurred_mask: np.ndarray,
    mask_bounds: dict[str, int],
    geometry: dict[str, Any],
    reference: np.ndarray,
    cpu: np.ndarray,
    gpu: np.ndarray,
) -> dict[str, Any]:
    if not (
        mask_bounds["left"] <= x < mask_bounds["right"]
        and mask_bounds["top"] <= y < mask_bounds["bottom"]
    ):
        mask_a = 0
    else:
        mask_a = int(blurred_mask[y - mask_bounds["top"], x - mask_bounds["left"]])
    clip_coverage = for280.difference_clip_coverage(x, y, geometry["differenceOvalDeviceBounds"])
    src_alpha_before_clip = (PAINT_ALPHA * mask_a + 127) // 255
    src_alpha_after_clip = alpha_after_clip(src_alpha_before_clip, clip_coverage)
    ref_rgba = reference[y, x].astype(int)
    cpu_rgba = cpu[y, x].astype(int)
    gpu_rgba = gpu[y, x].astype(int)
    return {
        "x": int(x),
        "y": int(y),
        "maskAAfterBlur": mask_a,
        "clipCoverage": int(clip_coverage),
        "srcAlphaBeforeClipModulation": int(src_alpha_before_clip),
        "srcAlphaAfterClipModulation": int(src_alpha_after_clip),
        "referenceRgba": ref_rgba.tolist(),
        "cpuRgba": cpu_rgba.tolist(),
        "gpuRgba": gpu_rgba.tolist(),
        "cpuMinusReferenceSignedRgba": (cpu_rgba - ref_rgba).tolist(),
        "gpuMinusReferenceSignedRgba": (gpu_rgba - ref_rgba).tolist(),
    }


def trace_metrics(
    *,
    mask: np.ndarray,
    blurred_mask: np.ndarray,
    mask_bounds: dict[str, int],
    geometry: dict[str, Any],
    reference: np.ndarray,
    cpu: np.ndarray,
    gpu: np.ndarray,
) -> dict[str, Any]:
    cpu_ref_diff = np.abs(cpu - reference).max(axis=2)
    gpu_ref_diff = np.abs(gpu - reference).max(axis=2)
    target = mask & (cpu_ref_diff > 32)
    mask_values: list[int] = []
    clip_values: list[int] = []
    before_values: list[int] = []
    after_values: list[int] = []
    for y, x in zip(*np.nonzero(target)):
        sample = trace_pixel(
            x=int(x),
            y=int(y),
            blurred_mask=blurred_mask,
            mask_bounds=mask_bounds,
            geometry=geometry,
            reference=reference,
            cpu=cpu,
            gpu=gpu,
        )
        mask_values.append(sample["maskAAfterBlur"])
        clip_values.append(sample["clipCoverage"])
        before_values.append(sample["srcAlphaBeforeClipModulation"])
        after_values.append(sample["srcAlphaAfterClipModulation"])
    return {
        "pixels": int(mask.sum()),
        "cpuReferenceGreaterThanThirtyTwoPixels": int(target.sum()),
        "gpuReferenceGreaterThanThirtyTwoPixels": int((mask & (gpu_ref_diff > 32)).sum()),
        "cpuReferenceMaxChannelDelta": int(cpu_ref_diff[mask].max()) if mask.any() else 0,
        "gpuReferenceMaxChannelDelta": int(gpu_ref_diff[mask].max()) if mask.any() else 0,
        "maskAAfterBlurOnCpuReferenceGt32": bucket_summary(mask_values),
        "clipCoverageOnCpuReferenceGt32": bucket_summary(clip_values),
        "srcAlphaBeforeClipModulationOnCpuReferenceGt32": bucket_summary(before_values),
        "srcAlphaAfterClipModulationOnCpuReferenceGt32": bucket_summary(after_values),
        "zeroMaskAAndCpuReferenceGt32Pixels": int(sum(1 for value in mask_values if value == 0)),
        "zeroSrcAlphaAfterClipAndCpuReferenceGt32Pixels": int(sum(1 for value in after_values if value == 0)),
    }


def source_trace_needles() -> None:
    gm_text = require_needles(
        GM_SOURCE,
        (
            "c.scale(2f, 2f)",
            "c.clipRRect(clipRRect, SkClipOp.kDifference, doAntiAlias = true)",
            "SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.366025f)",
            "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)",
            "c.drawRRect(rr, paint)",
        ),
    )
    if "saveLayer(" in gm_text:
        fail("BlurredClippedCircleGM unexpectedly uses saveLayer")
    require_needles(
        BLUR_SOURCE,
        (
            "private val radius: Int = ceil(3.0 * sigma).toInt().coerceAtLeast(1)",
            "private val kernel: FloatArray = gaussianKernel1D(sigma, radius)",
            "blurHorizontal(src, tmp, w, h, kernel, radius)",
            "blurVertical(tmp, blur, w, h, kernel, radius)",
            "SkBlurStyle.kNormal -> blur",
        ),
    )
    require_needles(
        CPU_SOURCE,
        (
            "private fun drawPathWithMaskFilter",
            "val preserveOffClipMaskSource = activeAaClip != null",
            "val maskA = blurred[y * maskW + x].toInt() and 0xFF",
            "val effA = (paintA * maskA + 127) / 255",
            "dispatchBlend(devX, devY, (effA shl 24) or rgb, mode, blender)",
            "private fun clipCoverage(x: Int, y: Int): Int",
            "val newA = (SkColorGetA(srcIn) * cov + 127) / 255",
        ),
    )


def generate_audit() -> dict[str, Any]:
    source_trace_needles()
    source_for278 = load_json(SOURCE_FOR278)
    source_for279 = load_json(SOURCE_FOR279)
    source_for280 = load_json(SOURCE_FOR280)
    stats = load_json(STATS)
    route_cpu = load_json(SCENE_DIR / "route-cpu.json")
    route_gpu = load_json(SCENE_DIR / "route-gpu.json")
    regenerated_for278 = for278.generate_audit()
    reference = for269.load_image("skia")
    cpu = for269.load_image("cpu")
    gpu = for269.load_image("gpu")
    if reference.shape != cpu.shape or reference.shape != gpu.shape:
        fail("reference, CPU, and GPU PNG dimensions differ")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA images")
    geometry, masks = for271.make_envelope_masks(width, height)
    blurred_mask, mask_bounds = generate_blurred_mask(width, height, geometry)

    combined_mask = np.zeros((height, width), dtype=bool)
    windows: list[dict[str, Any]] = []
    for window in source_for278["windows"]:
        subzone = window["subzone"]
        if subzone not in PRIMARY_WINDOWS:
            fail(f"unexpected FOR-278 window `{subzone}`")
        mask = mask_from_window(window, masks)
        combined_mask |= mask
        anchor = window["anchorSample"]
        windows.append(
            {
                "name": window["name"],
                "subzone": subzone,
                "bounds": window["bounds"],
                "maskPixels": int(mask.sum()),
                "anchorTrace": trace_pixel(
                    x=int(anchor["x"]),
                    y=int(anchor["y"]),
                    blurred_mask=blurred_mask,
                    mask_bounds=mask_bounds,
                    geometry=geometry,
                    reference=reference,
                    cpu=cpu,
                    gpu=gpu,
                ),
                "metrics": trace_metrics(
                    mask=mask,
                    blurred_mask=blurred_mask,
                    mask_bounds=mask_bounds,
                    geometry=geometry,
                    reference=reference,
                    cpu=cpu,
                    gpu=gpu,
                ),
            }
        )

    combined_metrics = trace_metrics(
        mask=combined_mask,
        blurred_mask=blurred_mask,
        mask_bounds=mask_bounds,
        geometry=geometry,
        reference=reference,
        cpu=cpu,
        gpu=gpu,
    )
    high_delta_traces = []
    cpu_ref_diff = np.abs(cpu - reference).max(axis=2)
    target = combined_mask & (cpu_ref_diff > 32)
    records = sorted((-int(cpu_ref_diff[y, x]), int(y), int(x)) for y, x in zip(*np.nonzero(target)))
    for neg_delta, y, x in records[:12]:
        sample = trace_pixel(
            x=x,
            y=y,
            blurred_mask=blurred_mask,
            mask_bounds=mask_bounds,
            geometry=geometry,
            reference=reference,
            cpu=cpu,
            gpu=gpu,
        )
        sample["cpuReferenceMaxChannelDelta"] = -neg_delta
        sample["subzone"] = for271.subzone_for_sample(x, y, masks)
        high_delta_traces.append(sample)

    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "cpu-mask-filter-clip-coverage-trace",
        "sceneId": SCENE_ID,
        "backend": "CPU/Reference/WebGPU",
        "sourceAudits": {
            "for278": str(SOURCE_FOR278.relative_to(PROJECT_ROOT)),
            "for279": str(SOURCE_FOR279.relative_to(PROJECT_ROOT)),
            "for280": str(SOURCE_FOR280.relative_to(PROJECT_ROOT)),
        },
        "sourceImages": {
            "reference": str((SCENE_DIR / "skia.png").relative_to(PROJECT_ROOT)),
            "cpu": str((SCENE_DIR / "cpu.png").relative_to(PROJECT_ROOT)),
            "gpu": str((SCENE_DIR / "gpu.png").relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": DECISION,
        "decisionRationale": (
            "FOR-281 traces the four requested scalar values over the same FOR-278 windows. "
            "For all 89 CPU/reference >32 pixels, the reconstructed mask-filter alpha after blur "
            "is non-zero and the source alpha after final difference-clip modulation is also "
            "non-zero. The remaining mismatch is therefore not explained by a zero maskA, zero "
            "clipCoverage, or zero post-modulation alpha. A production correction now needs a "
            "bounded color-filter/SrcIn/blend parity fixture before changing renderer behavior."
        ),
        "route": {
            "cpu": route_cpu.get("selectedRoute"),
            "gpu": route_gpu.get("selectedRoute"),
            "gpuStatus": route_gpu.get("status"),
            "fallbackReason": route_gpu.get("fallbackReason"),
            "coverageStrategy": route_gpu.get("coverageStrategy"),
            "pipelineKey": route_gpu.get("pipelineKey"),
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
        },
        "traceModel": {
            "maskBounds": mask_bounds,
            "sigma": SIGMA,
            "blurMargin": mask_bounds["margin"],
            "paintAlpha": PAINT_ALPHA,
            "maskAFormula": "source draw oval 4x4 coverage -> SkBlurMaskFilter(kNormal) separable Gaussian",
            "srcAlphaBeforeClipFormula": "paintAlpha * maskA / 255 rounded",
            "srcAlphaAfterClipFormula": "srcAlphaBeforeClip * clipCoverage / 255 rounded",
            "clipCoverageFormula": "SkAAClip difference estimate: parentCoverage=255, pathCoverage=b => 255-b",
            "instrumentationBoundary": (
                "This is a deterministic test-visible replay of the same scalar formulas and scene "
                "geometry, not a production renderer patch."
            ),
        },
        "sourcePreservation": {
            "for278RegeneratedMatchesSource": regenerated_for278["classification"]
            == source_for278["classification"],
            "for279DecisionPreserved": source_for279.get("decision")
            == "REFUSE_CORRECTION_PENDING_DEEPER_LAYER_COMPOSITE_MODEL",
            "for280DecisionPreserved": source_for280.get("decision")
            == "REFUSE_CORRECTION_PENDING_EXPLICIT_SKAA_CLIP_AND_MASK_FILTER_TRACE",
        },
        "windows": windows,
        "combinedBoundaryFixture": {
            "maskPixels": int(combined_mask.sum()),
            "metrics": combined_metrics,
        },
        "highDeltaTraceSamples": high_delta_traces,
        "fullSceneM60": {
            "cpuSimilarity": stats["cpuSimilarity"],
            "cpuMatchingPixels": stats["cpuMatchingPixels"],
            "cpuMaxChannelDelta": stats["cpuMaxChannelDelta"],
            "cpuReferenceGreaterThanThirtyTwoPixels": source_for280["fullSceneM60"][
                "cpuReferenceGreaterThanThirtyTwoPixels"
            ],
            "gpuSimilarity": stats["gpuSimilarity"],
            "gpuMatchingPixels": stats["gpuMatchingPixels"],
            "gpuMaxChannelDelta": stats["gpuMaxChannelDelta"],
            "gpuReferenceGreaterThanThirtyTwoPixels": source_for280["fullSceneM60"][
                "gpuReferenceGreaterThanThirtyTwoPixels"
            ],
        },
        "nextAction": NEXT_ACTION,
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


def window_row(window: dict[str, Any]) -> str:
    metrics = window["metrics"]
    mask_a = metrics["maskAAfterBlurOnCpuReferenceGt32"]
    clip = metrics["clipCoverageOnCpuReferenceGt32"]
    after = metrics["srcAlphaAfterClipModulationOnCpuReferenceGt32"]
    anchor = window["anchorTrace"]
    return (
        f"| `{window['subzone']}` | {window['maskPixels']} | "
        f"{metrics['cpuReferenceGreaterThanThirtyTwoPixels']} | "
        f"`{mask_a['buckets']}` | `{clip['buckets']}` | `{after['buckets']}` | "
        f"{anchor['maskAAfterBlur']} | {anchor['clipCoverage']} | "
        f"{anchor['srcAlphaAfterClipModulation']} |"
    )


def sample_row(sample: dict[str, Any]) -> str:
    return (
        f"| `{sample['subzone']}` | {sample['x']},{sample['y']} | "
        f"{sample['cpuReferenceMaxChannelDelta']} | "
        f"{sample['maskAAfterBlur']} | {sample['clipCoverage']} | "
        f"{sample['srcAlphaBeforeClipModulation']} | {sample['srcAlphaAfterClipModulation']} | "
        f"`{sample['referenceRgba']}` | `{sample['cpuRgba']}` | `{sample['gpuRgba']}` |"
    )


def write_report(audit: dict[str, Any]) -> None:
    combined = audit["combinedBoundaryFixture"]["metrics"]
    mask_a = combined["maskAAfterBlurOnCpuReferenceGt32"]
    clip = combined["clipCoverageOnCpuReferenceGt32"]
    before = combined["srcAlphaBeforeClipModulationOnCpuReferenceGt32"]
    after = combined["srcAlphaAfterClipModulationOnCpuReferenceGt32"]
    full = audit["fullSceneM60"]
    route = audit["route"]
    rows = "\n".join(window_row(window) for window in audit["windows"])
    samples = "\n".join(sample_row(sample) for sample in audit["highDeltaTraceSamples"][:8])
    report = f"""# FOR-281 CPU Mask Filter Clip Coverage Trace

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Support scene: `{audit["supportDecision"]}`

FOR-281 trace les valeurs demandees pour les fenetres FOR-278:
`maskA` apres blur, `clipCoverage`, `src.alpha` avant modulation, puis
`src.alpha` apres modulation par le clip final. Aucun renderer n'est modifie.

## Resultat Court

| Mesure | Valeur |
|---|---:|
| Pixels fixture FOR-278 | {audit["combinedBoundaryFixture"]["maskPixels"]} |
| CPU/reference >32 dans la fixture | {combined["cpuReferenceGreaterThanThirtyTwoPixels"]} |
| GPU/reference >32 dans la fixture | {combined["gpuReferenceGreaterThanThirtyTwoPixels"]} |
| `maskA` zero sur CPU/ref >32 | {combined["zeroMaskAAndCpuReferenceGt32Pixels"]} |
| `src.alpha` apres modulation zero sur CPU/ref >32 | {combined["zeroSrcAlphaAfterClipAndCpuReferenceGt32Pixels"]} |
| `maskA` partiel / plein | {mask_a["buckets"]["partial"]} / {mask_a["buckets"]["full"]} |
| `clipCoverage` partiel / plein | {clip["buckets"]["partial"]} / {clip["buckets"]["full"]} |
| `src.alpha` avant min / max / moyenne | {before["min"]} / {before["max"]} / {before["mean"]} |
| `src.alpha` apres min / max / moyenne | {after["min"]} / {after["max"]} / {after["mean"]} |

Les 89 pixels critiques ont tous `maskA > 0` et `src.alpha > 0` apres
modulation par le clip. Une correction qui force seulement `maskA`,
`clipCoverage`, ou le composite fond/layer serait donc non causale.

## Fenetres FOR-278

| Zone | Pixels | CPU/ref >32 | `maskA` | `clipCoverage` | `src.alpha` apres | `maskA` ancre | clip ancre | alpha apres ancre |
|---|---:|---:|---|---|---|---:|---:|---:|
{rows}

## Echantillons Haute Difference

| Zone | Pixel | Delta max | `maskA` | clip | alpha avant | alpha apres | Reference RGBA | CPU RGBA | GPU RGBA |
|---|---|---:|---:|---:|---:|---:|---|---|---|
{samples}

## Interpretation

FOR-281 refuse une correction de production immediate:
`{audit["decision"]}`.

Le residu visible reste RGB/payload sur alpha opaque. Les valeurs tracees
montrent que le masque floute et la modulation de clip ne tombent pas a zero
sur les pixels cibles. La suite admissible doit isoler la parite
`SkColorFilters.Blend(RED, kSrcIn)` + alpha de masque + `SrcOver` sur une
fixture bornee, puis seulement ensuite changer le renderer.

## Pleine Scene

| Mesure | Valeur |
|---|---:|
| CPU/reference similarity | {full["cpuSimilarity"]}% |
| CPU matching pixels | {full["cpuMatchingPixels"]} |
| CPU max channel delta | {full["cpuMaxChannelDelta"]} |
| CPU/reference >32 | {full["cpuReferenceGreaterThanThirtyTwoPixels"]} |
| GPU/reference similarity | {full["gpuSimilarity"]}% |
| GPU/reference >32 | {full["gpuReferenceGreaterThanThirtyTwoPixels"]} |

M60 reste `expected-unsupported` avec fallback `{route["fallbackReason"]}`.
`{route["cropFallbackPreserved"]}` est preserve. Aucun seuil n'est affaibli,
aucun support large clip-stack/readback n'est ajoute, et aucun chemin
Ganesh/Graphite/SkSL n'est introduit.

## Prochaine Action

`{audit["nextAction"]}`.

## Validation

```text
rtk python3 scripts/validate_for281_cpu_mask_filter_clip_coverage_trace.py
rtk python3 scripts/validate_for280_cpu_aa_difference_clip_coverage_edge.py
rtk python3 scripts/validate_for279_cpu_layer_boundary_composite_refusal.py
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`
"""
    REPORT.write_text(report, encoding="utf-8")


def validate(audit: dict[str, Any]) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("linear id mismatch")
    if audit.get("parent") != PARENT_ID:
        fail("parent id mismatch")
    if audit.get("sceneId") != SCENE_ID:
        fail("scene id mismatch")
    if audit.get("probe") != "cpu-mask-filter-clip-coverage-trace":
        fail("probe mismatch")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision must remain expected-unsupported")
    if audit.get("decision") != DECISION:
        fail("decision mismatch")
    if audit.get("nextAction") != NEXT_ACTION:
        fail("next action mismatch")
    source = audit.get("sourcePreservation", {})
    if source.get("for278RegeneratedMatchesSource") is not True:
        fail("FOR-278 source was not preserved")
    if source.get("for279DecisionPreserved") is not True:
        fail("FOR-279 decision was not preserved")
    if source.get("for280DecisionPreserved") is not True:
        fail("FOR-280 decision was not preserved")

    route = audit.get("route", {})
    if route.get("cpu") != for269.CPU_ROUTE:
        fail("CPU route mismatch")
    if route.get("gpu") != for269.GPU_ROUTE:
        fail("GPU route mismatch")
    if route.get("gpuStatus") != "expected-unsupported":
        fail("GPU status changed")
    if route.get("fallbackReason") != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route.get("cropFallbackPreserved") != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")

    trace = audit.get("traceModel", {})
    if trace.get("sigma") != SIGMA:
        fail("sigma changed")
    if trace.get("blurMargin") != 5:
        fail("blur margin changed")
    if trace.get("maskBounds") != {"left": 2, "top": 2, "right": 590, "bottom": 590, "margin": 5}:
        fail("mask bounds changed")

    expected_windows = {
        "draw_oval_outer_boundary": {
            "maskPixels": 59,
            "cpuGt32": 59,
            "maskA": {"zero": 0, "partial": 59, "full": 0},
            "clip": {"zero": 0, "partial": 0, "full": 59},
            "after": {"zero": 0, "partial": 59, "full": 0},
            "anchor": {"maskA": 250, "clip": 255, "after": 250},
        },
        "difference_oval_inner_boundary": {
            "maskPixels": 67,
            "cpuGt32": 18,
            "maskA": {"zero": 0, "partial": 0, "full": 18},
            "clip": {"zero": 0, "partial": 10, "full": 8},
            "after": {"zero": 0, "partial": 10, "full": 8},
            "anchor": {"maskA": 255, "clip": 255, "after": 255},
        },
        "halo_interior": {
            "maskPixels": 22,
            "cpuGt32": 12,
            "maskA": {"zero": 0, "partial": 2, "full": 10},
            "clip": {"zero": 0, "partial": 0, "full": 12},
            "after": {"zero": 0, "partial": 2, "full": 10},
            "anchor": {"maskA": 255, "clip": 255, "after": 255},
        },
    }
    windows = audit.get("windows", [])
    if len(windows) != 3:
        fail("expected three windows")
    for window in windows:
        subzone = window.get("subzone")
        expected = expected_windows.get(subzone)
        if expected is None:
            fail(f"unexpected window `{subzone}`")
        metrics = window["metrics"]
        if window.get("maskPixels") != expected["maskPixels"]:
            fail(f"{subzone} mask pixels changed")
        if metrics.get("cpuReferenceGreaterThanThirtyTwoPixels") != expected["cpuGt32"]:
            fail(f"{subzone} CPU/reference >32 changed")
        if metrics["maskAAfterBlurOnCpuReferenceGt32"]["buckets"] != expected["maskA"]:
            fail(f"{subzone} maskA buckets changed")
        if metrics["clipCoverageOnCpuReferenceGt32"]["buckets"] != expected["clip"]:
            fail(f"{subzone} clip buckets changed")
        if metrics["srcAlphaAfterClipModulationOnCpuReferenceGt32"]["buckets"] != expected["after"]:
            fail(f"{subzone} post-modulation alpha buckets changed")
        anchor = window["anchorTrace"]
        if anchor.get("maskAAfterBlur") != expected["anchor"]["maskA"]:
            fail(f"{subzone} anchor maskA changed")
        if anchor.get("clipCoverage") != expected["anchor"]["clip"]:
            fail(f"{subzone} anchor clip changed")
        if anchor.get("srcAlphaAfterClipModulation") != expected["anchor"]["after"]:
            fail(f"{subzone} anchor post alpha changed")

    combined = audit["combinedBoundaryFixture"]["metrics"]
    if audit["combinedBoundaryFixture"].get("maskPixels") != 148:
        fail("combined mask pixel count changed")
    if combined.get("cpuReferenceGreaterThanThirtyTwoPixels") != 89:
        fail("combined CPU/reference >32 changed")
    if combined.get("gpuReferenceGreaterThanThirtyTwoPixels") != 11:
        fail("combined GPU/reference >32 changed")
    if combined.get("zeroMaskAAndCpuReferenceGt32Pixels") != 0:
        fail("maskA unexpectedly zero on target pixels")
    if combined.get("zeroSrcAlphaAfterClipAndCpuReferenceGt32Pixels") != 0:
        fail("post-modulation alpha unexpectedly zero on target pixels")
    if combined["maskAAfterBlurOnCpuReferenceGt32"]["buckets"] != {
        "zero": 0,
        "partial": 61,
        "full": 28,
    }:
        fail("combined maskA buckets changed")
    if combined["clipCoverageOnCpuReferenceGt32"]["buckets"] != {
        "zero": 0,
        "partial": 10,
        "full": 79,
    }:
        fail("combined clip buckets changed")
    if combined["srcAlphaAfterClipModulationOnCpuReferenceGt32"]["buckets"] != {
        "zero": 0,
        "partial": 71,
        "full": 18,
    }:
        fail("combined post-modulation alpha buckets changed")
    if combined["srcAlphaAfterClipModulationOnCpuReferenceGt32"]["min"] != 6:
        fail("combined post-modulation alpha min changed")
    if combined["srcAlphaAfterClipModulationOnCpuReferenceGt32"]["max"] != 255:
        fail("combined post-modulation alpha max changed")

    full = audit.get("fullSceneM60", {})
    if full.get("cpuSimilarity") != 97.31:
        fail("M60 CPU similarity changed")
    if full.get("gpuSimilarity") != 98.48:
        fail("M60 GPU similarity changed")
    if full.get("cpuReferenceGreaterThanThirtyTwoPixels") != 15726:
        fail("M60 CPU/reference >32 changed")
    if full.get("gpuReferenceGreaterThanThirtyTwoPixels") != 2869:
        fail("M60 GPU/reference >32 changed")

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
            fail(f"strict preservation `{field}` must be false")
    if strict.get("cropFallbackPreserved") != for269.CROP_FALLBACK_REASON:
        fail("crop fallback preservation mismatch")
    if strict.get("visualParityFallbackPreserved") != for269.FALLBACK_REASON:
        fail("visual parity fallback preservation mismatch")

    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "FOR-281 CPU Mask Filter Clip Coverage Trace",
        DECISION,
        NEXT_ACTION,
        for269.FALLBACK_REASON,
        for269.CROP_FALLBACK_REASON,
        AUDIT_NAME,
    ):
        if needle not in text:
            fail(f"report missing `{needle}`")


def main() -> None:
    audit = generate_audit()
    SCENE_DIR.mkdir(parents=True, exist_ok=True)
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
    write_report(audit)
    validate(load_json(AUDIT))
    combined = audit["combinedBoundaryFixture"]["metrics"]
    print(
        "FOR-281 validation passed: "
        f"{audit['decision']}; "
        f"{combined['cpuReferenceGreaterThanThirtyTwoPixels']} CPU/reference >32 target pixels, "
        f"maskA zero={combined['zeroMaskAAndCpuReferenceGt32Pixels']}, "
        f"post-alpha zero={combined['zeroSrcAlphaAfterClipAndCpuReferenceGt32Pixels']}, "
        f"M60 remains {audit['supportDecision']}."
    )


if __name__ == "__main__":
    main()
