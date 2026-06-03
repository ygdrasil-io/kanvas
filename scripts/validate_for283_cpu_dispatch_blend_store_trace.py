#!/usr/bin/env python3
"""Generate and validate FOR-283 CPU dispatch/blend/store trace evidence."""

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
import validate_for281_cpu_mask_filter_clip_coverage_trace as for281
import validate_for282_cpu_color_filter_srcin_blend_parity as for282


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-283"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "m60-cpu-dispatch-blend-store-trace-for283.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-283-cpu-dispatch-blend-store-trace.md"
SOURCE_FOR278 = SCENE_DIR / "m60-boundary-layer-composite-fixture-for278.json"
SOURCE_FOR281 = SCENE_DIR / "m60-cpu-mask-filter-clip-coverage-trace-for281.json"
SOURCE_FOR282 = SCENE_DIR / "m60-cpu-color-filter-srcin-blend-parity-for282.json"
STATS = SCENE_DIR / "stats.json"
GM_SOURCE = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
BITMAP_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
COLOR_FILTER_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorFilters.kt"

DECISION = "TARGETED_CORRECTION_POSSIBLE_AT_CPU_MASK_FILTER_DISPATCH_CALLSITE"
NEXT_ACTION = "PATCH_CPU_MASK_FILTER_A8_SOLID_COLOR_FILTER_DISPATCH_PAYLOAD_AND_REGENERATE_M60"
PRIMARY_WINDOWS = (
    "draw_oval_outer_boundary",
    "difference_oval_inner_boundary",
    "halo_interior",
)
WHITE_RGBA = [255, 255, 255, 255]
RED_RGBA = [255, 0, 0, 255]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-283 validation failed: {message}")


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
            "val maskA = blurred[y * maskW + x].toInt() and 0xFF",
            "val effA = (paintA * maskA + 127) / 255",
            "dispatchBlend(devX, devY, (effA shl 24) or rgb, mode, blender)",
            "private fun dispatchBlend(",
            "null -> blend(x, y, src, mode)",
            "private fun blend(x: Int, y: Int, srcIn: SkColor, mode: SkBlendMode)",
            "if (activeAaClip != null) cov = clipCoverage(x, y)",
            "val newA = (SkColorGetA(srcIn) * cov + 127) / 255",
            "val dst = bitmap.getPixel(x, y)",
            "bitmap.setPixel(x, y, SkColorSetARGB(outA, outR, outG, outB))",
            "bitmap.setPixel(x, y, src)",
        ),
    )
    require_needles(
        BITMAP_SOURCE,
        (
            "public fun setPixel(x: Int, y: Int, c: SkColor)",
            "SkColorType.kRGBA_8888 -> pixels8888[y * width + x] = c",
            "public fun getPixel(x: Int, y: Int): SkColor",
        ),
    )


def rgba_to_argb(rgba: list[int]) -> list[int]:
    return [int(rgba[3]), int(rgba[0]), int(rgba[1]), int(rgba[2])]


def argb_to_rgba(argb: list[int]) -> list[int]:
    return [int(argb[1]), int(argb[2]), int(argb[3]), int(argb[0])]


def red_carrier(rgba: list[int]) -> bool:
    return int(rgba[3]) > 0 and int(rgba[0]) == 255 and int(rgba[1]) == 0 and int(rgba[2]) == 0


def red_tint_over_white(rgba: list[int]) -> bool:
    return int(rgba[3]) > 8 and int(rgba[0]) >= 250 and int(rgba[1]) < 255 and int(rgba[2]) < 255


def red_dominant(rgba: list[int]) -> bool:
    return int(rgba[3]) > 8 and int(rgba[0]) > int(rgba[1]) + 24 and int(rgba[0]) > int(rgba[2]) + 24


def white_or_layer(rgba: list[int]) -> bool:
    return int(rgba[3]) > 240 and min(int(rgba[0]), int(rgba[1]), int(rgba[2])) >= 240


def signed_rgba(actual: list[int], base: list[int]) -> list[int]:
    return [int(a) - int(b) for a, b in zip(actual, base)]


def max_delta(a: list[int], b: list[int]) -> int:
    return max(abs(int(av) - int(bv)) for av, bv in zip(a, b))


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


def dispatch_trace_pixel(
    *,
    x: int,
    y: int,
    subzone: str,
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
    mask_a = int(base["maskAAfterBlur"])
    cov = int(base["clipCoverage"])
    dispatch_src = [RED_RGBA[0], RED_RGBA[1], RED_RGBA[2], mask_a]
    src_after_cov = [RED_RGBA[0], RED_RGBA[1], RED_RGBA[2], (mask_a * cov + 127) // 255]
    dst_before_blend = list(WHITE_RGBA)
    blend_result = argb_to_rgba(src_over_argb(rgba_to_argb(src_after_cov), rgba_to_argb(dst_before_blend)))
    readback = [int(v) for v in base["cpuRgba"]]
    reference_rgba = [int(v) for v in base["referenceRgba"]]
    gpu_rgba = [int(v) for v in base["gpuRgba"]]
    return {
        "x": int(x),
        "y": int(y),
        "subzone": subzone,
        "dispatchInputSrcRgba": dispatch_src,
        "dispatchInputMode": "SkBlendMode.kSrcOver",
        "dispatchInputBlender": "null",
        "dispatchWouldRun": mask_a > 0,
        "cov": cov,
        "srcAfterCoverageRgba": src_after_cov,
        "dstBeforeBlendRgba": dst_before_blend,
        "blendResultBeforeStoreRgba": blend_result,
        "readBackAfterCpuOutputRgba": readback,
        "referenceRgba": reference_rgba,
        "gpuRgba": gpu_rgba,
        "dispatchInputCarriesRedPayload": red_carrier(dispatch_src),
        "srcAfterCoverageCarriesRedPayload": red_carrier(src_after_cov),
        "blendResultCarriesRedTint": red_tint_over_white(blend_result),
        "blendResultRedDominant": red_dominant(blend_result),
        "readBackRedDominant": red_dominant(readback),
        "readBackIsWhiteOrLayer": white_or_layer(readback),
        "blendVsReadBackMaxChannelDelta": max_delta(blend_result, readback),
        "blendMinusReadBackSignedRgba": signed_rgba(blend_result, readback),
        "blendVsReferenceMaxChannelDelta": max_delta(blend_result, reference_rgba),
        "readBackVsReferenceMaxChannelDelta": max_delta(readback, reference_rgba),
        "referenceVsReadBackSignedRgba": signed_rgba(reference_rgba, readback),
        "maskAAfterBlur": mask_a,
        "srcAlphaBeforeClipModulation": int(base["srcAlphaBeforeClipModulation"]),
        "srcAlphaAfterClipModulation": int(base["srcAlphaAfterClipModulation"]),
    }


def trace_metrics(samples: list[dict[str, Any]]) -> dict[str, Any]:
    pixels = len(samples)
    blend_deltas = [int(s["blendVsReadBackMaxChannelDelta"]) for s in samples]
    ref_deltas = [int(s["readBackVsReferenceMaxChannelDelta"]) for s in samples]
    cov_values = [int(s["cov"]) for s in samples]
    alphas_after_cov = [int(s["srcAfterCoverageRgba"][3]) for s in samples]
    return {
        "targetPixels": pixels,
        "dispatchWouldRunPixels": sum(1 for s in samples if s["dispatchWouldRun"]),
        "dispatchInputRedPayloadPixels": sum(1 for s in samples if s["dispatchInputCarriesRedPayload"]),
        "nonZeroCovPixels": sum(1 for s in samples if int(s["cov"]) > 0),
        "nonZeroSrcAfterCoveragePixels": sum(1 for s in samples if int(s["srcAfterCoverageRgba"][3]) > 0),
        "dstBeforeBlendWhitePixels": sum(1 for s in samples if s["dstBeforeBlendRgba"] == WHITE_RGBA),
        "srcAfterCoverageRedPayloadPixels": sum(1 for s in samples if s["srcAfterCoverageCarriesRedPayload"]),
        "blendResultRedTintPixels": sum(1 for s in samples if s["blendResultCarriesRedTint"]),
        "blendResultRedDominantPixels": sum(1 for s in samples if s["blendResultRedDominant"]),
        "readBackRedDominantPixels": sum(1 for s in samples if s["readBackRedDominant"]),
        "readBackWhiteLayerPixels": sum(1 for s in samples if s["readBackIsWhiteOrLayer"]),
        "blendVsReadBackGreaterThanThirtyTwoPixels": sum(1 for d in blend_deltas if d > 32),
        "blendVsReadBackMaxChannelDelta": max(blend_deltas) if blend_deltas else 0,
        "readBackVsReferenceGreaterThanThirtyTwoPixels": sum(1 for d in ref_deltas if d > 32),
        "readBackVsReferenceMaxChannelDelta": max(ref_deltas) if ref_deltas else 0,
        "covMin": min(cov_values) if cov_values else 0,
        "covMax": max(cov_values) if cov_values else 0,
        "covMean": rounded(sum(cov_values) / pixels) if pixels else 0.0,
        "srcAlphaAfterCoverageMin": min(alphas_after_cov) if alphas_after_cov else 0,
        "srcAlphaAfterCoverageMax": max(alphas_after_cov) if alphas_after_cov else 0,
        "srcAlphaAfterCoverageMean": rounded(sum(alphas_after_cov) / pixels) if pixels else 0.0,
    }


def generate_audit() -> dict[str, Any]:
    source_needles()
    source_for278 = load_json(SOURCE_FOR278)
    source_for281 = load_json(SOURCE_FOR281)
    source_for282 = load_json(SOURCE_FOR282)
    stats = load_json(STATS)
    route_cpu = load_json(SCENE_DIR / "route-cpu.json")
    route_gpu = load_json(SCENE_DIR / "route-gpu.json")
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
    cpu_ref_diff = np.abs(cpu - reference).max(axis=2)

    combined_mask = np.zeros((height, width), dtype=bool)
    all_samples: list[dict[str, Any]] = []
    windows: list[dict[str, Any]] = []
    for window in source_for278["windows"]:
        subzone = window["subzone"]
        if subzone not in PRIMARY_WINDOWS:
            fail(f"unexpected FOR-278 window `{subzone}`")
        mask = for281.mask_from_window(window, masks)
        target = mask & (cpu_ref_diff > 32)
        combined_mask |= mask
        samples = [
            dispatch_trace_pixel(
                x=int(x),
                y=int(y),
                subzone=subzone,
                blurred_mask=blurred_mask,
                mask_bounds=mask_bounds,
                geometry=geometry,
                reference=reference,
                cpu=cpu,
                gpu=gpu,
            )
            for y, x in zip(*np.nonzero(target))
        ]
        all_samples.extend(samples)
        anchor = window["anchorSample"]
        windows.append(
            {
                "name": window["name"],
                "subzone": subzone,
                "bounds": window["bounds"],
                "maskPixels": int(mask.sum()),
                "targetPixels": int(target.sum()),
                "metrics": trace_metrics(samples),
                "anchorTrace": dispatch_trace_pixel(
                    x=int(anchor["x"]),
                    y=int(anchor["y"]),
                    subzone=subzone,
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
    high_delta_traces: list[dict[str, Any]] = []
    records = sorted((-int(cpu_ref_diff[y, x]), int(y), int(x)) for y, x in zip(*np.nonzero(target)))
    for neg_delta, y, x in records[:12]:
        sample = dispatch_trace_pixel(
            x=x,
            y=y,
            subzone=for271.subzone_for_sample(x, y, masks),
            blurred_mask=blurred_mask,
            mask_bounds=mask_bounds,
            geometry=geometry,
            reference=reference,
            cpu=cpu,
            gpu=gpu,
        )
        sample["cpuReferenceMaxChannelDelta"] = -neg_delta
        high_delta_traces.append(sample)

    combined_metrics = trace_metrics(all_samples)
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "cpu-dispatch-blend-store-trace",
        "sceneId": SCENE_ID,
        "backend": "CPU/Reference/WebGPU",
        "sourceAudits": {
            "for278": str(SOURCE_FOR278.relative_to(PROJECT_ROOT)),
            "for281": str(SOURCE_FOR281.relative_to(PROJECT_ROOT)),
            "for282": str(SOURCE_FOR282.relative_to(PROJECT_ROOT)),
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
            "FOR-283 extends the FOR-282 bounded reconstruction to the CPU dispatch/blend/store contract. "
            "For the 89 FOR-278 target pixels, the local observable solid-mask path has non-zero source "
            "entering dispatchBlend, non-zero clip coverage, and a white destination from the prior kSrc "
            "layer draw. 86 pixels are red-dominant at dispatch input, 82 remain red-dominant after the "
            "SrcOver pre-store model, but all 89 still carry a red tint before store. The committed CPU "
            "output reads back only 9 red-dominant pixels, 0 reference-like red pixels in the FOR-282 "
            "buckets, and 78 white/layer pixels. This makes a broad blend math or setPixel storage fix unlikely; "
            "the next production change should target the CPU mask-filter A8 solid color-filter dispatch "
            "payload/callsite and regenerate M60 evidence before any promotion."
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
            "observationBoundary": (
                "Bounded local-observable trace over committed M60 CPU/reference/GPU images and the "
                "SkBitmapDevice solid A8 mask-filter dispatch contract. It records the dispatch source "
                "equivalent, cov, dst, pre-store SrcOver result, and CPU readback for only FOR-278 pixels."
            ),
            "dispatchInputFormula": "src = (maskA-derived alpha << 24) | effectiveColor.rgb; effectiveColor is Blend(RED,kSrcIn) applied to default opaque paint color",
            "covFormula": "blend() modulates src.alpha by activeAaClip clipCoverage(x,y)",
            "dstFormula": "white kSrc layer draw precedes the blurred red draw in BlurredClippedCircleGM",
            "blendFormula": "SkBitmapDevice 8-bit SrcOver fast path",
            "readBackFormula": "committed CPU output PNG pixel after the GM exits",
            "maskBounds": mask_bounds,
        },
        "sourcePreservation": {
            "for281DecisionPreserved": source_for281.get("decision")
            == "REFUSE_CORRECTION_PENDING_MASK_SOURCE_COLOR_FILTER_BLEND_PARITY",
            "for282DecisionPreserved": source_for282.get("decision")
            == "REFUSE_CORRECTION_PENDING_RUNTIME_DISPATCH_BLEND_STORE_TRACE",
            "for282TargetPixelsPreserved": source_for282["combinedBoundaryFixture"]["metrics"].get("targetPixels") == 89,
            "for282CpuRedPayloadPreserved": source_for282["combinedBoundaryFixture"]["metrics"].get(
                "cpuRedPayloadPixelsOnTarget"
            )
            == 0,
            "for282CpuReferenceLikeRedPayloadPixels": source_for282["combinedBoundaryFixture"]["metrics"].get(
                "cpuRedPayloadPixelsOnTarget"
            ),
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
        f"| `{window['subzone']}` | {window['maskPixels']} | {metrics['targetPixels']} | "
        f"{metrics['dispatchInputRedPayloadPixels']} | {metrics['nonZeroCovPixels']} | "
        f"{metrics['blendResultRedTintPixels']} | {metrics['readBackRedDominantPixels']} | "
        f"{metrics['readBackWhiteLayerPixels']} | "
        f"`{anchor['dispatchInputSrcRgba']}` | `{anchor['srcAfterCoverageRgba']}` | "
        f"`{anchor['blendResultBeforeStoreRgba']}` | `{anchor['readBackAfterCpuOutputRgba']}` |"
    )


def sample_row(sample: dict[str, Any]) -> str:
    return (
        f"| `{sample['subzone']}` | {sample['x']},{sample['y']} | "
        f"{sample['cpuReferenceMaxChannelDelta']} | "
        f"`{sample['dispatchInputSrcRgba']}` | {sample['cov']} | "
        f"`{sample['dstBeforeBlendRgba']}` | `{sample['blendResultBeforeStoreRgba']}` | "
        f"`{sample['readBackAfterCpuOutputRgba']}` | `{sample['referenceRgba']}` | "
        f"{sample['blendVsReadBackMaxChannelDelta']} |"
    )


def write_report(audit: dict[str, Any]) -> None:
    combined = audit["combinedBoundaryFixture"]["metrics"]
    full = audit["fullSceneM60"]
    route = audit["route"]
    rows = "\n".join(window_row(window) for window in audit["windows"])
    samples = "\n".join(sample_row(sample) for sample in audit["highDeltaTraceSamples"][:8])
    report = f"""# FOR-283 CPU Dispatch Blend Store Trace

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Support scene: `{audit["supportDecision"]}`

FOR-283 trace de maniere bornee les coordonnees FOR-278 deja isolees:
source equivalente entrant dans `dispatchBlend`, `cov`, destination avant
blend, resultat `SrcOver` avant store, puis valeur relue dans la sortie CPU.
Aucun renderer n'est modifie.

## Resultat Court

| Mesure | Valeur |
|---|---:|
| Pixels fixture FOR-278 | {audit["combinedBoundaryFixture"]["maskPixels"]} |
| Pixels cibles CPU/reference >32 | {combined["targetPixels"]} |
| Source dispatch rouge | {combined["dispatchInputRedPayloadPixels"]} |
| `cov` non nul | {combined["nonZeroCovPixels"]} |
| Destination blanche avant blend | {combined["dstBeforeBlendWhitePixels"]} |
| Source apres coverage rouge | {combined["srcAfterCoverageRedPayloadPixels"]} |
| Resultat blend teinte rouge avant store | {combined["blendResultRedTintPixels"]} |
| Resultat blend rouge dominant avant store | {combined["blendResultRedDominantPixels"]} |
| Rouge dominant relu dans sortie CPU | {combined["readBackRedDominantPixels"]} |
| Rouge reference-like relu FOR-282 | {audit["sourcePreservation"]["for282CpuReferenceLikeRedPayloadPixels"]} |
| Blanc/layer relu dans sortie CPU | {combined["readBackWhiteLayerPixels"]} |
| Blend vs readback >32 | {combined["blendVsReadBackGreaterThanThirtyTwoPixels"]} |
| Blend vs readback delta max | {combined["blendVsReadBackMaxChannelDelta"]} |

Les 89 pixels critiques ont une source rouge locale, une couverture non
nulle, un `dst` blanc, et une teinte rouge avant store. La sortie CPU relue
reste dominee par 78 pixels blanc/layer et 0 pixel rouge reference-like dans
la classification FOR-282. La correction large du blend ou de
`SkBitmap.setPixel` n'est donc pas justifiee par cette trace.

## Fenetres FOR-278

| Zone | Pixels | Cibles | Src rouge | Cov non nul | Blend teinte | Readback rouge | Readback blanc | Src ancre | Src+cov ancre | Blend ancre | Readback ancre |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|
{rows}

## Echantillons Haute Difference

| Zone | Pixel | Delta CPU/ref | Src dispatch | cov | Dst avant | Blend avant store | Readback CPU | Reference | Blend/readback delta |
|---|---|---:|---|---:|---|---|---|---|---:|
{samples}

## Interpretation

FOR-283 produit la decision `{audit["decision"]}`.

Le chemin causal restant est le callsite CPU `drawPathWithMaskFilter` pour le
cas A8 solid + `SkColorFilters.Blend(RED,kSrcIn)`: soit le payload qui atteint
effectivement `dispatchBlend` diverge de ce contrat local, soit l'appel/store
est evite pour ces pixels malgre `maskA > 0` et `cov > 0`. La prochaine
action nommee est `{audit["nextAction"]}`.

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

## Validation

```text
rtk python3 scripts/validate_for283_cpu_dispatch_blend_store_trace.py
rtk python3 scripts/validate_for282_cpu_color_filter_srcin_blend_parity.py
rtk ./gradlew pipelineSceneDashboardGate
rtk git diff --check origin/master...HEAD
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
    if audit.get("probe") != "cpu-dispatch-blend-store-trace":
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
        "for281DecisionPreserved",
        "for282DecisionPreserved",
        "for282TargetPixelsPreserved",
        "for282CpuRedPayloadPreserved",
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

    combined_fixture = audit["combinedBoundaryFixture"]
    combined = combined_fixture["metrics"]
    if combined_fixture.get("maskPixels") != 148:
        fail("combined mask pixel count changed")
    if combined_fixture.get("targetPixels") != 89:
        fail("combined target pixel count changed")
    if combined.get("targetPixels") != 89:
        fail("combined metrics target pixel count changed")
    if combined.get("dispatchWouldRunPixels") != 89:
        fail("all target pixels must have dispatch input")
    if combined.get("dispatchInputRedPayloadPixels") != 89:
        fail("dispatch input red payload changed")
    if combined.get("nonZeroCovPixels") != 89:
        fail("coverage must be non-zero for all target pixels")
    if combined.get("nonZeroSrcAfterCoveragePixels") != 89:
        fail("source after coverage must be non-zero for all target pixels")
    if combined.get("dstBeforeBlendWhitePixels") != 89:
        fail("dst before blend must remain white for all target pixels")
    if combined.get("srcAfterCoverageRedPayloadPixels") != 89:
        fail("source after coverage red payload changed")
    if combined.get("blendResultRedTintPixels") != 89:
        fail("blend result red tint changed")
    if combined.get("blendResultRedDominantPixels") != 82:
        fail("blend result red-dominant count changed")
    if combined.get("readBackRedDominantPixels") != 9:
        fail("CPU readback red-dominant count changed")
    if combined.get("readBackWhiteLayerPixels") != 78:
        fail("CPU readback white/layer count changed")
    if combined.get("blendVsReadBackGreaterThanThirtyTwoPixels") < 80:
        fail("blend/readback divergence is no longer strong enough")

    expected_windows = {
        "draw_oval_outer_boundary": {
            "maskPixels": 59,
            "target": 59,
            "dispatchRed": 59,
            "blendTint": 59,
            "blendDominant": 52,
            "readBackDominant": 0,
            "readBackWhite": 59,
        },
        "difference_oval_inner_boundary": {
            "maskPixels": 67,
            "target": 18,
            "dispatchRed": 18,
            "blendTint": 18,
            "blendDominant": 18,
            "readBackDominant": 3,
            "readBackWhite": 14,
        },
        "halo_interior": {
            "maskPixels": 22,
            "target": 12,
            "dispatchRed": 12,
            "blendTint": 12,
            "blendDominant": 12,
            "readBackDominant": 6,
            "readBackWhite": 5,
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
        if metrics.get("targetPixels") != expected["target"]:
            fail(f"{subzone} target pixels changed")
        if metrics.get("dispatchInputRedPayloadPixels") != expected["dispatchRed"]:
            fail(f"{subzone} dispatch input red payload changed")
        if metrics.get("blendResultRedTintPixels") != expected["blendTint"]:
            fail(f"{subzone} blend red tint changed")
        if metrics.get("blendResultRedDominantPixels") != expected["blendDominant"]:
            fail(f"{subzone} blend red-dominant count changed")
        if metrics.get("readBackRedDominantPixels") != expected["readBackDominant"]:
            fail(f"{subzone} readback red-dominant count changed")
        if metrics.get("readBackWhiteLayerPixels") != expected["readBackWhite"]:
            fail(f"{subzone} readback white/layer count changed")
        anchor = window["anchorTrace"]
        if anchor.get("dispatchInputCarriesRedPayload") is not True:
            fail(f"{subzone} anchor lost dispatch red payload")
        if anchor.get("blendResultCarriesRedTint") is not True:
            fail(f"{subzone} anchor lost blend red tint")

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
        "FOR-283 CPU Dispatch Blend Store Trace",
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
        "FOR-283 validation passed: "
        f"{audit['decision']}; "
        f"{combined['dispatchInputRedPayloadPixels']} dispatch red-payload pixels, "
        f"{combined['blendResultRedTintPixels']} blend red-tint pixels, "
        f"{combined['readBackRedDominantPixels']} CPU readback red-dominant pixels, "
        f"{combined['readBackWhiteLayerPixels']} CPU white/layer pixels; "
        f"M60 remains {audit['supportDecision']}."
    )


if __name__ == "__main__":
    main()
