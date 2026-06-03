#!/usr/bin/env python3
"""Generate and validate FOR-282 CPU color-filter/SrcIn/SrcOver evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

import numpy as np

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for271_nested_rrect_blurred_envelope_audit as for271
import validate_for278_m60_boundary_layer_composite_fixture as for278
import validate_for280_cpu_aa_difference_clip_coverage_edge as for280
import validate_for281_cpu_mask_filter_clip_coverage_trace as for281


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-282"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "m60-cpu-color-filter-srcin-blend-parity-for282.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-282-cpu-color-filter-srcin-blend-parity.md"
SOURCE_FOR278 = SCENE_DIR / "m60-boundary-layer-composite-fixture-for278.json"
SOURCE_FOR279 = SCENE_DIR / "m60-cpu-layer-boundary-composite-for279.json"
SOURCE_FOR280 = SCENE_DIR / "m60-cpu-aa-difference-clip-coverage-edge-for280.json"
SOURCE_FOR281 = SCENE_DIR / "m60-cpu-mask-filter-clip-coverage-trace-for281.json"
STATS = SCENE_DIR / "stats.json"
GM_SOURCE = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
COLOR_FILTER_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorFilters.kt"

DECISION = "REFUSE_CORRECTION_PENDING_RUNTIME_DISPATCH_BLEND_STORE_TRACE"
NEXT_ACTION = "TARGET_CPU_MASK_FILTER_DISPATCH_SRC_AND_DST_TRACE"
PRIMARY_WINDOWS = (
    "draw_oval_outer_boundary",
    "difference_oval_inner_boundary",
    "halo_interior",
)
WHITE_RGBA = [255, 255, 255, 255]
RED_RGBA = [255, 0, 0, 255]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-282 validation failed: {message}")


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


def source_needles() -> None:
    gm_text = require_needles(
        GM_SOURCE,
        (
            "val whitePaint = SkPaint().apply",
            "color = SK_ColorWHITE",
            "blendMode = SkBlendMode.kSrc",
            "SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.366025f)",
            "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)",
            "c.drawRRect(rr, paint)",
        ),
    )
    if "saveLayer(" in gm_text:
        fail("BlurredClippedCircleGM unexpectedly uses saveLayer")
    require_needles(
        COLOR_FILTER_SOURCE,
        (
            "public fun Blend(colour: SkColor, mode: SkBlendMode): SkColorFilter",
            "internal class SkBlendColorFilter",
            "return blendColor4f(src4f, src, mode)",
            "SkBlendMode.kSrcIn -> {",
            "out[0] = sr * da; out[1] = sg * da; out[2] = sb * da; out[3] = sa * da",
            "`Blend(c, kSrcIn)` keeps the pixel's alpha but uses `c.rgb`",
        ),
    )
    require_needles(
        CPU_SOURCE,
        (
            "private fun drawPathWithMaskFilter",
            "val effectiveColor = if (shader == null) {",
            "transformPaintColor(applyColorFilter(paint.colorFilter, paint.color))",
            "val rgb = effectiveColor and 0x00FFFFFF",
            "val effA = (paintA * maskA + 127) / 255",
            "dispatchBlend(devX, devY, (effA shl 24) or rgb, mode, blender)",
            "private fun blend(x: Int, y: Int, srcIn: SkColor, mode: SkBlendMode)",
            "val newA = (SkColorGetA(srcIn) * cov + 127) / 255",
            "private fun blendSrcOver(src: SkColor, dst: SkColor): SkColor",
            "bitmap.setPixel(x, y, src)",
        ),
    )


def rgba_to_argb(rgba: list[int]) -> list[int]:
    return [int(rgba[3]), int(rgba[0]), int(rgba[1]), int(rgba[2])]


def argb_to_rgba(argb: list[int]) -> list[int]:
    return [int(argb[1]), int(argb[2]), int(argb[3]), int(argb[0])]


def src_over_argb(src: list[int], dst: list[int]) -> list[int]:
    sa, sr, sg, sb = [int(v) for v in src]
    da, dr, dg, db = [int(v) for v in dst]
    if sa == 255:
        return [255, sr, sg, sb]
    if sa == 0:
        return [da, dr, dg, db]
    inv_sa = 255 - sa
    out_a = sa + (da * inv_sa + 127) // 255
    if out_a == 0:
        return [0, 0, 0, 0]
    out_r = (sr * sa + dr * da * inv_sa // 255 + out_a // 2) // out_a
    out_g = (sg * sa + dg * da * inv_sa // 255 + out_a // 2) // out_a
    out_b = (sb * sa + db * da * inv_sa // 255 + out_a // 2) // out_a
    return [out_a, out_r, out_g, out_b]


def src_after_mask_and_clip(mask_a: int, clip_coverage: int) -> dict[str, Any]:
    src_after_blend = list(RED_RGBA)
    alpha_after_mask = (src_after_blend[3] * int(mask_a) + 127) // 255
    alpha_after_clip = (alpha_after_mask * int(clip_coverage) + 127) // 255
    src_after_mask = [src_after_blend[0], src_after_blend[1], src_after_blend[2], alpha_after_mask]
    src_after_clip = [src_after_blend[0], src_after_blend[1], src_after_blend[2], alpha_after_clip]
    expected_src_over_white = argb_to_rgba(
        src_over_argb(rgba_to_argb(src_after_clip), rgba_to_argb(WHITE_RGBA))
    )
    return {
        "srcAfterBlendRedSrcInRgba": src_after_blend,
        "srcAfterMaskAlphaRgba": src_after_mask,
        "srcAfterClipAlphaRgba": src_after_clip,
        "expectedSrcOverWhiteRgba": expected_src_over_white,
    }


def red_payload_mask(image: np.ndarray, mask: np.ndarray) -> np.ndarray:
    r = image[:, :, 0]
    g = image[:, :, 1]
    b = image[:, :, 2]
    a = image[:, :, 3]
    return mask & (a > 8) & (r > g + 24) & (r > b + 24)


def rgba_array(values: list[list[int]], height: int, width: int, target: np.ndarray) -> np.ndarray:
    out = np.zeros((height, width, 4), dtype=np.int16)
    ys, xs = np.nonzero(target)
    for value, y, x in zip(values, ys.tolist(), xs.tolist()):
        out[y, x] = np.array(value, dtype=np.int16)
    return out


def signed_summary(base: np.ndarray, actual: np.ndarray, mask: np.ndarray) -> dict[str, Any]:
    signed = actual.astype(np.int16) - base.astype(np.int16)
    if not mask.any():
        return {
            "meanSignedRgba": [0.0, 0.0, 0.0, 0.0],
            "minSignedRgba": [0, 0, 0, 0],
            "maxSignedRgba": [0, 0, 0, 0],
            "greaterThanThirtyTwoPixels": 0,
            "maxChannelDelta": 0,
        }
    abs_delta = np.abs(signed).max(axis=2)
    return {
        "meanSignedRgba": [rounded(signed[:, :, channel][mask].mean(), 3) for channel in range(4)],
        "minSignedRgba": [int(signed[:, :, channel][mask].min()) for channel in range(4)],
        "maxSignedRgba": [int(signed[:, :, channel][mask].max()) for channel in range(4)],
        "greaterThanThirtyTwoPixels": int((mask & (abs_delta > 32)).sum()),
        "maxChannelDelta": int(abs_delta[mask].max()),
    }


def target_metrics(
    *,
    target: np.ndarray,
    expected: np.ndarray,
    reference: np.ndarray,
    cpu: np.ndarray,
    gpu: np.ndarray,
) -> dict[str, Any]:
    cpu_ref_diff = np.abs(cpu - reference).max(axis=2)
    gpu_ref_diff = np.abs(gpu - reference).max(axis=2)
    expected_cpu_diff = np.abs(expected - cpu.astype(np.int16)).max(axis=2)
    expected_ref_diff = np.abs(expected - reference.astype(np.int16)).max(axis=2)
    expected_gpu_diff = np.abs(expected - gpu.astype(np.int16)).max(axis=2)
    expected_red_payload = red_payload_mask(expected.astype(np.uint8), target)
    cpu_buckets = for278.for272.color_buckets(cpu, target)
    ref_buckets = for278.for272.color_buckets(reference, target)
    gpu_buckets = for278.for272.color_buckets(gpu, target)
    expected_buckets = for278.for272.color_buckets(expected.astype(np.uint8), target)
    return {
        "targetPixels": int(target.sum()),
        "expectedRedPayloadPixels": int(expected_red_payload.sum()),
        "cpuRedDominantPixelsOnTarget": int(red_payload_mask(cpu, target).sum()),
        "referenceRedPayloadPixelsOnTarget": int(red_payload_mask(reference, target).sum()),
        "gpuRedPayloadPixelsOnTarget": int(red_payload_mask(gpu, target).sum()),
        "cpuRedPayloadPixelsOnTarget": int(cpu_buckets.get("red_tinted_reference_like", 0)),
        "cpuWhiteLayerPixelsOnTarget": int(cpu_buckets.get("white_or_layer_background", 0)),
        "referenceRedTintPixelsOnTarget": int(ref_buckets.get("red_tinted_reference_like", 0)),
        "gpuCloseToReferencePixelsOnTarget": int((target & (gpu_ref_diff <= 32)).sum()),
        "cpuReferenceGreaterThanThirtyTwoPixels": int((target & (cpu_ref_diff > 32)).sum()),
        "gpuReferenceGreaterThanThirtyTwoPixels": int((target & (gpu_ref_diff > 32)).sum()),
        "expectedVsCpuGreaterThanThirtyTwoPixels": int((target & (expected_cpu_diff > 32)).sum()),
        "expectedVsReferenceGreaterThanThirtyTwoPixels": int((target & (expected_ref_diff > 32)).sum()),
        "expectedVsGpuGreaterThanThirtyTwoPixels": int((target & (expected_gpu_diff > 32)).sum()),
        "expectedVsCpuMaxChannelDelta": int(expected_cpu_diff[target].max()),
        "expectedVsReferenceMaxChannelDelta": int(expected_ref_diff[target].max()),
        "expectedVsGpuMaxChannelDelta": int(expected_gpu_diff[target].max()),
        "expectedMinusCpuOnTarget": signed_summary(cpu.astype(np.int16), expected, target),
        "expectedMinusReferenceOnTarget": signed_summary(reference.astype(np.int16), expected, target),
        "expectedMinusGpuOnTarget": signed_summary(gpu.astype(np.int16), expected, target),
        "expectedColorBucketsOnTarget": expected_buckets,
        "cpuColorBucketsOnTarget": cpu_buckets,
        "referenceColorBucketsOnTarget": ref_buckets,
        "gpuColorBucketsOnTarget": gpu_buckets,
    }


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
    base = for281.trace_pixel(
        x=x,
        y=y,
        blurred_mask=blurred_mask,
        mask_bounds=mask_bounds,
        geometry=geometry,
        reference=reference,
        cpu=cpu,
        gpu=gpu,
    )
    parity = src_after_mask_and_clip(
        base["maskAAfterBlur"],
        base["clipCoverage"],
    )
    cpu_rgba = np.array(base["cpuRgba"], dtype=np.int16)
    expected_rgba = np.array(parity["expectedSrcOverWhiteRgba"], dtype=np.int16)
    base.update(parity)
    base["expectedMinusCpuSignedRgba"] = (expected_rgba - cpu_rgba).tolist()
    base["expectedVsCpuMaxChannelDelta"] = int(np.abs(expected_rgba - cpu_rgba).max())
    base["redPayloadExpectedBeforeSrcOver"] = parity["srcAfterClipAlphaRgba"][3] > 0
    base["cpuKeepsRedPayload"] = bool(
        base["cpuRgba"][3] > 8
        and base["cpuRgba"][0] > base["cpuRgba"][1] + 24
        and base["cpuRgba"][0] > base["cpuRgba"][2] + 24
    )
    return base


def generate_audit() -> dict[str, Any]:
    source_needles()
    source_for278 = load_json(SOURCE_FOR278)
    source_for279 = load_json(SOURCE_FOR279)
    source_for280 = load_json(SOURCE_FOR280)
    source_for281 = load_json(SOURCE_FOR281)
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
    blurred_mask, mask_bounds = for281.generate_blurred_mask(width, height, geometry)

    combined_mask = np.zeros((height, width), dtype=bool)
    windows: list[dict[str, Any]] = []
    expected_values: list[list[int]] = []
    target_coords: list[tuple[int, int]] = []
    cpu_ref_diff = np.abs(cpu - reference).max(axis=2)
    for window in source_for278["windows"]:
        subzone = window["subzone"]
        if subzone not in PRIMARY_WINDOWS:
            fail(f"unexpected FOR-278 window `{subzone}`")
        mask = for281.mask_from_window(window, masks)
        window_target = mask & (cpu_ref_diff > 32)
        combined_mask |= mask
        window_expected_values: list[list[int]] = []
        for y, x in zip(*np.nonzero(window_target)):
            trace = for281.trace_pixel(
                x=int(x),
                y=int(y),
                blurred_mask=blurred_mask,
                mask_bounds=mask_bounds,
                geometry=geometry,
                reference=reference,
                cpu=cpu,
                gpu=gpu,
            )
            expected = src_after_mask_and_clip(
                trace["maskAAfterBlur"],
                trace["clipCoverage"],
            )["expectedSrcOverWhiteRgba"]
            expected_values.append(expected)
            window_expected_values.append(expected)
            target_coords.append((int(y), int(x)))

        window_expected = np.zeros((height, width, 4), dtype=np.int16)
        for value, (y, x) in zip(window_expected_values, zip(*np.nonzero(window_target))):
            window_expected[int(y), int(x)] = np.array(value, dtype=np.int16)

        anchor = window["anchorSample"]
        windows.append(
            {
                "name": window["name"],
                "subzone": subzone,
                "bounds": window["bounds"],
                "maskPixels": int(mask.sum()),
                "cpuReferenceGreaterThanThirtyTwoPixels": int(window_target.sum()),
                "metrics": target_metrics(
                    target=window_target,
                    expected=window_expected,
                    reference=reference,
                    cpu=cpu,
                    gpu=gpu,
                ),
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
            }
        )

    target = combined_mask & (cpu_ref_diff > 32)
    expected_image = np.zeros((height, width, 4), dtype=np.int16)
    for value, (y, x) in zip(expected_values, target_coords):
        expected_image[y, x] = np.array(value, dtype=np.int16)
    combined_metrics = target_metrics(
        target=target,
        expected=expected_image,
        reference=reference,
        cpu=cpu,
        gpu=gpu,
    )

    high_delta_traces: list[dict[str, Any]] = []
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
        "probe": "cpu-color-filter-srcin-blend-parity",
        "sceneId": SCENE_ID,
        "backend": "CPU/Reference/WebGPU",
        "sourceAudits": {
            "for278": str(SOURCE_FOR278.relative_to(PROJECT_ROOT)),
            "for279": str(SOURCE_FOR279.relative_to(PROJECT_ROOT)),
            "for280": str(SOURCE_FOR280.relative_to(PROJECT_ROOT)),
            "for281": str(SOURCE_FOR281.relative_to(PROJECT_ROOT)),
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
            "FOR-282 reconstructs the bounded SrcIn payload expected from "
            "SkColorFilters.Blend(RED, kSrcIn), then applies mask alpha, final "
            "difference-clip alpha, and the CPU SrcOver integer formula over the "
            "white layer. The 89 FOR-278 target pixels all have non-zero red "
            "payload before SrcOver, while the current CPU output keeps zero "
            "red-dominant payload and 78 white/layer-background pixels. That "
            "locates the mismatch in the RGB payload path after the alpha stages, "
            "but without a runtime dispatch/store trace it cannot prove whether "
            "the production source entering dispatchBlend is already wrong or "
            "whether the loss happens inside blend/store."
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
        "reconstructionModel": {
            "inputPaintColor": "SkPaint default opaque black; Blend(RED,kSrcIn) replaces RGB and keeps input alpha",
            "colorFilterFormula": "Blend(RED,kSrcIn): src=RED, dst=paint.color => output RGB red, alpha RED.a * paint.a",
            "maskAlphaFormula": "filteredSourceAlpha = colorFilterAlpha * maskA / 255 rounded",
            "clipAlphaFormula": "finalSourceAlpha = filteredSourceAlpha * clipCoverage / 255 rounded",
            "blendFormula": "SkBitmapDevice 8-bit SrcOver fast path over the already drawn white layer",
            "instrumentationBoundary": (
                "This reconstructs the scalar payload from committed artifacts and source formulas. "
                "It does not patch the renderer and does not observe the runtime src/dst arguments "
                "inside dispatchBlend."
            ),
        },
        "sourcePreservation": {
            "for278RegeneratedMatchesSource": regenerated_for278["classification"]
            == source_for278["classification"],
            "for279DecisionPreserved": source_for279.get("decision")
            == "REFUSE_CORRECTION_PENDING_DEEPER_LAYER_COMPOSITE_MODEL",
            "for280DecisionPreserved": source_for280.get("decision")
            == "REFUSE_CORRECTION_PENDING_EXPLICIT_SKAA_CLIP_AND_MASK_FILTER_TRACE",
            "for281DecisionPreserved": source_for281.get("decision")
            == "REFUSE_CORRECTION_PENDING_MASK_SOURCE_COLOR_FILTER_BLEND_PARITY",
        },
        "windows": windows,
        "combinedBoundaryFixture": {
            "maskPixels": int(combined_mask.sum()),
            "targetPixels": int(target.sum()),
            "metrics": combined_metrics,
        },
        "highDeltaTraceSamples": high_delta_traces,
        "fullSceneM60": {
            "cpuSimilarity": stats["cpuSimilarity"],
            "cpuMatchingPixels": stats["cpuMatchingPixels"],
            "cpuMaxChannelDelta": stats["cpuMaxChannelDelta"],
            "cpuReferenceGreaterThanThirtyTwoPixels": source_for281["fullSceneM60"][
                "cpuReferenceGreaterThanThirtyTwoPixels"
            ],
            "gpuSimilarity": stats["gpuSimilarity"],
            "gpuMatchingPixels": stats["gpuMatchingPixels"],
            "gpuMaxChannelDelta": stats["gpuMaxChannelDelta"],
            "gpuReferenceGreaterThanThirtyTwoPixels": source_for281["fullSceneM60"][
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
    anchor = window["anchorTrace"]
    return (
        f"| `{window['subzone']}` | {window['maskPixels']} | "
        f"{metrics['targetPixels']} | {metrics['expectedRedPayloadPixels']} | "
        f"{metrics['cpuRedPayloadPixelsOnTarget']} | "
        f"{metrics['cpuWhiteLayerPixelsOnTarget']} | "
        f"{metrics['referenceRedTintPixelsOnTarget']} | "
        f"{metrics['expectedVsCpuGreaterThanThirtyTwoPixels']} | "
        f"`{anchor['srcAfterClipAlphaRgba']}` | "
        f"`{anchor['expectedSrcOverWhiteRgba']}` | `{anchor['cpuRgba']}` |"
    )


def sample_row(sample: dict[str, Any]) -> str:
    return (
        f"| `{sample['subzone']}` | {sample['x']},{sample['y']} | "
        f"{sample['cpuReferenceMaxChannelDelta']} | "
        f"{sample['maskAAfterBlur']} | {sample['clipCoverage']} | "
        f"`{sample['srcAfterClipAlphaRgba']}` | "
        f"`{sample['expectedSrcOverWhiteRgba']}` | "
        f"`{sample['referenceRgba']}` | `{sample['cpuRgba']}` | `{sample['gpuRgba']}` |"
    )


def write_report(audit: dict[str, Any]) -> None:
    combined = audit["combinedBoundaryFixture"]["metrics"]
    full = audit["fullSceneM60"]
    route = audit["route"]
    rows = "\n".join(window_row(window) for window in audit["windows"])
    samples = "\n".join(sample_row(sample) for sample in audit["highDeltaTraceSamples"][:8])
    report = f"""# FOR-282 CPU Color Filter SrcIn Blend Parity

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Support scene: `{audit["supportDecision"]}`

FOR-282 reconstruit la charge couleur attendue pour
`SkColorFilters.Blend(RED, kSrcIn)`, puis applique `maskA`,
`clipCoverage`, et le `SrcOver` CPU sur le layer blanc deja dessine.
Aucun renderer n'est modifie.

## Resultat Court

| Mesure | Valeur |
|---|---:|
| Pixels fixture FOR-278 | {audit["combinedBoundaryFixture"]["maskPixels"]} |
| Pixels cibles CPU/reference >32 | {combined["targetPixels"]} |
| Charge rouge attendue avant `SrcOver` | {combined["expectedRedPayloadPixels"]} |
| Charge rouge observee CPU | {combined["cpuRedPayloadPixelsOnTarget"]} |
| Pixels CPU blanc/layer | {combined["cpuWhiteLayerPixelsOnTarget"]} |
| Pixels reference rouge teintee | {combined["referenceRedTintPixelsOnTarget"]} |
| Pixels GPU proches reference | {combined["gpuCloseToReferencePixelsOnTarget"]} |
| Reconstruction vs CPU >32 | {combined["expectedVsCpuGreaterThanThirtyTwoPixels"]} |
| Reconstruction vs reference >32 | {combined["expectedVsReferenceGreaterThanThirtyTwoPixels"]} |
| Reconstruction vs GPU >32 | {combined["expectedVsGpuGreaterThanThirtyTwoPixels"]} |

Les 89 pixels critiques ont une charge rouge non nulle apres
`Blend(RED,kSrcIn)` + masque + clip. Le CPU final n'en garde aucune et
reste majoritairement blanc/layer. La perte est donc dans le chemin RGB
apres les preuves d'alpha FOR-280/FOR-281, mais le rejeu deterministe ne
prouve pas encore si la valeur runtime envoyee a `dispatchBlend` est deja
fausse ou si la perte arrive dans `SrcOver`/store.

## Fenetres FOR-278

| Zone | Pixels | Cibles | Rouge attendu | Rouge CPU | Blanc CPU | Rouge ref | Reco vs CPU >32 | Source ancre | Reco ancre | CPU ancre |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|
{rows}

## Echantillons Haute Difference

| Zone | Pixel | Delta CPU/ref | `maskA` | clip | Source apres clip | Reco SrcOver blanc | Reference RGBA | CPU RGBA | GPU RGBA |
|---|---|---:|---:|---:|---|---|---|---|---|
{samples}

## Interpretation

FOR-282 refuse une correction de production immediate:
`{audit["decision"]}`.

Ce refus est plus precis que FOR-281: la charge rouge attendue existe pour
tous les pixels cibles, et le CPU final ne la transporte pas. Le prochain
ticket doit instrumenter de maniere bornee les arguments runtime
`srcIn`, `cov`, `dst` et la sortie de `blend`/`setPixel` autour de ces memes
coordonnees. Sans cette trace, changer le renderer risquerait de corriger un
modele reconstruit plutot que le chemin causal effectif.

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
rtk python3 scripts/validate_for282_cpu_color_filter_srcin_blend_parity.py
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
    if audit.get("probe") != "cpu-color-filter-srcin-blend-parity":
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
    for key in (
        "for278RegeneratedMatchesSource",
        "for279DecisionPreserved",
        "for280DecisionPreserved",
        "for281DecisionPreserved",
    ):
        if source.get(key) is not True:
            fail(f"source preservation `{key}` failed")

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

    combined = audit["combinedBoundaryFixture"]["metrics"]
    if audit["combinedBoundaryFixture"].get("maskPixels") != 148:
        fail("combined mask pixel count changed")
    if combined.get("targetPixels") != 89:
        fail("combined target pixel count changed")
    if combined.get("expectedRedPayloadPixels") != 89:
        fail("red payload must be expected on all target pixels")
    if combined.get("cpuRedPayloadPixelsOnTarget") != 0:
        fail("CPU unexpectedly keeps red payload on target pixels")
    if combined.get("cpuWhiteLayerPixelsOnTarget") != 78:
        fail("CPU white/layer target count changed")
    if combined.get("referenceRedTintPixelsOnTarget") != 56:
        fail("reference red-tint target count changed")
    if combined.get("cpuReferenceGreaterThanThirtyTwoPixels") != 89:
        fail("CPU/reference target >32 changed")
    if combined.get("gpuReferenceGreaterThanThirtyTwoPixels") != 11:
        fail("GPU/reference target >32 changed")
    if combined.get("expectedVsCpuGreaterThanThirtyTwoPixels") < 80:
        fail("reconstruction no longer diverges from CPU strongly enough")
    if combined.get("expectedVsReferenceGreaterThanThirtyTwoPixels") == 0:
        fail("reconstruction unexpectedly matches reference exactly")
    if combined["cpuColorBucketsOnTarget"].get("red_tinted_reference_like", -1) != 0:
        fail("CPU red-tint bucket changed")
    if combined["cpuColorBucketsOnTarget"].get("white_or_layer_background", -1) != 78:
        fail("CPU white bucket changed")

    expected_windows = {
        "draw_oval_outer_boundary": {"maskPixels": 59, "target": 59, "cpuWhite": 59},
        "difference_oval_inner_boundary": {"maskPixels": 67, "target": 18, "cpuWhite": 14},
        "halo_interior": {"maskPixels": 22, "target": 12, "cpuWhite": 5},
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
        if metrics.get("targetPixels") != expected["target"]:
            fail(f"{subzone} target pixels changed")
        if metrics.get("expectedRedPayloadPixels") != expected["target"]:
            fail(f"{subzone} expected red payload changed")
        if metrics.get("cpuRedPayloadPixelsOnTarget") != 0:
            fail(f"{subzone} CPU unexpectedly keeps red payload")
        if metrics.get("cpuWhiteLayerPixelsOnTarget") != expected["cpuWhite"]:
            fail(f"{subzone} CPU white/layer count changed")
        anchor = window["anchorTrace"]
        if anchor.get("redPayloadExpectedBeforeSrcOver") is not True:
            fail(f"{subzone} anchor lost expected red payload")
        if anchor.get("cpuKeepsRedPayload") is not False:
            fail(f"{subzone} anchor CPU unexpectedly keeps red payload")

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
        "FOR-282 CPU Color Filter SrcIn Blend Parity",
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
        "FOR-282 validation passed: "
        f"{audit['decision']}; "
        f"{combined['expectedRedPayloadPixels']} expected red-payload target pixels, "
        f"{combined['cpuRedPayloadPixelsOnTarget']} CPU red-payload pixels, "
        f"{combined['cpuWhiteLayerPixelsOnTarget']} CPU white/layer pixels; "
        f"M60 remains {audit['supportDecision']}."
    )


if __name__ == "__main__":
    main()
