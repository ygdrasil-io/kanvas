#!/usr/bin/env python3
"""Generate and validate FOR-286 CPU active-AA difference store evidence."""

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
import validate_for283_cpu_dispatch_blend_store_trace as for283
import validate_for285_m60_post_payload_residual as for285


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-286"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "m60-cpu-active-aa-difference-store-trace-for286.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-286-cpu-active-aa-difference-store-trace.md"
SOURCE_FOR278 = SCENE_DIR / "m60-boundary-layer-composite-fixture-for278.json"
SOURCE_FOR283 = SCENE_DIR / "m60-cpu-dispatch-blend-store-trace-for283.json"
SOURCE_FOR285 = SCENE_DIR / "m60-post-payload-residual-for285.json"
GM_SOURCE = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
BITMAP_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"

DECISION = "NEXT_FIX_CPU_ACTIVE_AA_DIFFERENCE_CLIP_STORE_ORDER_NOT_SRCOVER_OR_PAYLOAD"
NEXT_ACTION = "TARGET_CPU_ACTIVE_AA_DIFFERENCE_CLIP_STORE_ORDER"
RESIDUAL_CLASSIFICATION = "CPU_ACTIVE_AA_DIFFERENCE_LAYER_STORE_ORDER_RESIDUAL"
PRIMARY_WINDOWS = (
    "draw_oval_outer_boundary",
    "difference_oval_inner_boundary",
    "halo_interior",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-286 validation failed: {message}")


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


def require_needles(path: Path, needles: tuple[str, ...]) -> str:
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")
    return text


def rounded(value: float, digits: int = 6) -> float:
    return round(float(value), digits)


def source_needles() -> dict[str, bool]:
    gm_text = require_needles(
        GM_SOURCE,
        (
            "public class BlurredClippedCircleGM",
            "c.drawRect(clipRect1, whitePaint)",
            "c.clipRRect(clipRRect, SkClipOp.kDifference, doAntiAlias = true)",
            "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)",
            "c.drawRRect(rr, paint)",
        ),
    )
    cpu_text = require_needles(
        CPU_SOURCE,
        (
            "private fun drawPathWithMaskFilter",
            "val filterMaskPayloadAfterMask = colorFilter",
            "?.mode == SkBlendMode.kSrcIn",
            "applyColorFilter(colorFilter, (maskedA shl 24) or (paint.color and 0x00FFFFFF))",
            "dispatchBlend(devX, devY, src, mode, blender)",
            "private fun dispatchBlend(",
            "null -> blend(x, y, src, mode)",
            "private fun blend(x: Int, y: Int, srcIn: SkColor, mode: SkBlendMode)",
            "if (activeAaClip != null) cov = clipCoverage(x, y)",
            "val src: SkColor = if (cov == 0)",
            "bitmap.setPixel(x, y, src)",
            "val dst = bitmap.getPixel(x, y)",
            "bitmap.setPixel(x, y, SkColorSetARGB(outA, outR, outG, outB))",
        ),
    )
    bitmap_text = require_needles(
        BITMAP_SOURCE,
        (
            "public fun setPixel(x: Int, y: Int, c: SkColor)",
            "SkColorType.kRGBA_8888 -> pixels8888[y * width + x] = c",
            "public fun getPixel(x: Int, y: Int): SkColor",
        ),
    )
    return {
        "gmUsesWhiteSrcLayerBeforeDifferenceBlur": "blendMode = SkBlendMode.kSrc" in gm_text,
        "gmHasNoSaveLayer": "saveLayer(" not in gm_text,
        "a8SrcInPayloadBranchPresent": "filterMaskPayloadAfterMask" in cpu_text,
        "dispatchBlendRoutesNullBlenderToBlend": "null -> blend(x, y, src, mode)" in cpu_text,
        "blendAppliesActiveAaClipCoverage": "if (activeAaClip != null) cov = clipCoverage(x, y)" in cpu_text,
        "srcOverFastPathStoresOpaqueSrc": "bitmap.setPixel(x, y, src)" in cpu_text,
        "srcOverFastPathStoresComputedOut": "bitmap.setPixel(x, y, SkColorSetARGB(outA, outR, outG, outB))" in cpu_text,
        "bitmapSetPixelDirectRgba8888Store": "pixels8888[y * width + x] = c" in bitmap_text,
    }


def source_needles_valid(needles: dict[str, bool]) -> None:
    for key, value in needles.items():
        if value is not True:
            fail(f"source needle `{key}` failed")


def max_delta_image(actual: np.ndarray, reference: np.ndarray) -> np.ndarray:
    return np.abs(actual.astype(np.int16) - reference.astype(np.int16)).max(axis=2)


def max_delta(a: list[int], b: list[int]) -> int:
    return for283.max_delta(a, b)


def signed_rgba(actual: list[int], base: list[int]) -> list[int]:
    return for283.signed_rgba(actual, base)


def store_trace_pixel(
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
    base = for283.dispatch_trace_pixel(
        x=x,
        y=y,
        subzone=subzone,
        blurred_mask=blurred_mask,
        mask_bounds=mask_bounds,
        geometry=geometry,
        reference=reference,
        cpu=cpu,
        gpu=gpu,
    )
    src_after_coverage = [int(v) for v in base["srcAfterCoverageRgba"]]
    dst_before_blend = [int(v) for v in base["dstBeforeBlendRgba"]]
    readback = [int(v) for v in base["readBackAfterCpuOutputRgba"]]
    src_alpha = int(src_after_coverage[3])
    if src_alpha == 255:
        branch = "SkBitmapDevice.blend.kSrcOver.opaqueSrc.setPixel(src)"
        dst_read_by_branch = False
        blend_result = list(src_after_coverage)
        write_value = list(src_after_coverage)
        write_would_run = True
    elif src_alpha == 0:
        branch = "SkBitmapDevice.blend.kSrcOver.zeroSrc.return"
        dst_read_by_branch = False
        blend_result = list(dst_before_blend)
        write_value = None
        write_would_run = False
    else:
        branch = "SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)"
        dst_read_by_branch = True
        blend_result = [int(v) for v in base["blendResultBeforeStoreRgba"]]
        write_value = list(blend_result)
        write_would_run = True

    written_vs_readback_delta = max_delta(write_value, readback) if write_value is not None else 0
    blend_vs_write_delta = max_delta(blend_result, write_value) if write_value is not None else 0
    reference_rgba = [int(v) for v in base["referenceRgba"]]
    return {
        **base,
        "coverageAppliedInBlend": int(base["cov"]),
        "srcAfterActiveAaClipCoverageRgba": src_after_coverage,
        "dstReadBySrcOverBranch": dst_read_by_branch,
        "dstBeforeBlendRgba": dst_before_blend,
        "blendResultBeforeWriteRgba": blend_result,
        "writeBranch": branch,
        "writeWouldRun": write_would_run,
        "valueWrittenRgba": write_value,
        "readBackAfterDrawRgba": readback,
        "writeMatchesBlendResult": blend_vs_write_delta == 0,
        "blendVsWriteMaxChannelDelta": blend_vs_write_delta,
        "readBackMatchesWritten": write_value is not None and written_vs_readback_delta <= 1,
        "writtenVsReadBackMaxChannelDelta": written_vs_readback_delta,
        "writtenMinusReadBackSignedRgba": signed_rgba(write_value, readback) if write_value is not None else [0, 0, 0, 0],
        "writtenVsReferenceMaxChannelDelta": max_delta(write_value, reference_rgba) if write_value is not None else 0,
        "writeCarriesRedTint": write_value is not None and for283.red_tint_over_white(write_value),
        "writeRedDominant": write_value is not None and for283.red_dominant(write_value),
        "readBackRedDominant": for283.red_dominant(readback),
        "readBackIsWhiteOrLayer": for283.white_or_layer(readback),
    }


def trace_metrics(samples: list[dict[str, Any]]) -> dict[str, Any]:
    pixels = len(samples)
    cov_values = [int(s["coverageAppliedInBlend"]) for s in samples]
    write_deltas = [int(s["writtenVsReadBackMaxChannelDelta"]) for s in samples if s["valueWrittenRgba"] is not None]
    write_alphas = [int(s["valueWrittenRgba"][3]) for s in samples if s["valueWrittenRgba"] is not None]
    return {
        "targetPixels": pixels,
        "dispatchWouldRunPixels": sum(1 for s in samples if s["dispatchWouldRun"]),
        "dispatchInputRedPayloadPixels": sum(1 for s in samples if s["dispatchInputCarriesRedPayload"]),
        "coverageAppliedNonZeroPixels": sum(1 for s in samples if int(s["coverageAppliedInBlend"]) > 0),
        "coverageAppliedFullPixels": sum(1 for s in samples if int(s["coverageAppliedInBlend"]) == 255),
        "dstBeforeBlendWhitePixels": sum(1 for s in samples if s["dstBeforeBlendRgba"] == for283.WHITE_RGBA),
        "blendResultRedTintPixels": sum(1 for s in samples if s["blendResultCarriesRedTint"]),
        "blendResultRedDominantPixels": sum(1 for s in samples if s["blendResultRedDominant"]),
        "writeWouldRunPixels": sum(1 for s in samples if s["writeWouldRun"]),
        "writeMatchesBlendResultPixels": sum(1 for s in samples if s["writeMatchesBlendResult"]),
        "writeRedTintPixels": sum(1 for s in samples if s["writeCarriesRedTint"]),
        "writeRedDominantPixels": sum(1 for s in samples if s["writeRedDominant"]),
        "writeOpaqueSrcFastPathPixels": sum(1 for s in samples if s["writeBranch"].endswith("opaqueSrc.setPixel(src)")),
        "writePartialSrcFastPathPixels": sum(1 for s in samples if s["writeBranch"].endswith("partialSrc.setPixel(out)")),
        "readBackMatchesWrittenPixels": sum(1 for s in samples if s["readBackMatchesWritten"]),
        "readBackRedDominantPixels": sum(1 for s in samples if s["readBackRedDominant"]),
        "readBackWhiteLayerPixels": sum(1 for s in samples if s["readBackIsWhiteOrLayer"]),
        "writtenVsReadBackGreaterThanThirtyTwoPixels": sum(1 for d in write_deltas if d > 32),
        "writtenVsReadBackMaxChannelDelta": max(write_deltas) if write_deltas else 0,
        "coverageMin": min(cov_values) if cov_values else 0,
        "coverageMax": max(cov_values) if cov_values else 0,
        "coverageMean": rounded(sum(cov_values) / pixels) if pixels else 0.0,
        "writeAlphaMin": min(write_alphas) if write_alphas else 0,
        "writeAlphaMax": max(write_alphas) if write_alphas else 0,
        "writeAlphaMean": rounded(sum(write_alphas) / len(write_alphas)) if write_alphas else 0.0,
    }


def generate_audit() -> dict[str, Any]:
    needles = source_needles()
    source_needles_valid(needles)
    source_for278 = load_json(SOURCE_FOR278)
    source_for283 = load_json(SOURCE_FOR283)
    source_for285 = load_json(SOURCE_FOR285)
    stats = load_json(SCENE_DIR / "stats.json")
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
    cpu_ref_diff = max_delta_image(cpu, reference)
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
            store_trace_pixel(
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
                "anchorTrace": store_trace_pixel(
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
        sample = store_trace_pixel(
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
        "probe": "cpu-active-aa-difference-store-trace",
        "sceneId": SCENE_ID,
        "backend": "CPU/Reference/WebGPU",
        "sourceAudits": {
            "for278": str(SOURCE_FOR278.relative_to(PROJECT_ROOT)),
            "for283": str(SOURCE_FOR283.relative_to(PROJECT_ROOT)),
            "for285": str(SOURCE_FOR285.relative_to(PROJECT_ROOT)),
        },
        "sourceImages": {
            "reference": str((SCENE_DIR / "skia.png").relative_to(PROJECT_ROOT)),
            "cpu": str((SCENE_DIR / "cpu.png").relative_to(PROJECT_ROOT)),
            "gpu": str((SCENE_DIR / "gpu.png").relative_to(PROJECT_ROOT)),
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": DECISION,
        "nextAction": NEXT_ACTION,
        "residualClassification": RESIDUAL_CLASSIFICATION,
        "route": {
            "cpu": route_cpu.get("selectedRoute"),
            "gpu": route_gpu.get("selectedRoute"),
            "gpuStatus": route_gpu.get("status"),
            "fallbackReason": route_gpu.get("fallbackReason"),
            "coverageStrategy": route_gpu.get("coverageStrategy"),
            "pipelineKey": route_gpu.get("pipelineKey"),
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
            "sourceNeedles": needles,
        },
        "traceModel": {
            "observationBoundary": (
                "Bounded reconstruction over the committed M60 CPU/reference/GPU images, the "
                "FOR-278 windows, and the current SkBitmapDevice A8 SrcIn + kSrcOver store branches."
            ),
            "dispatchSource": "FOR-284 branch applies Blend(RED,kSrcIn) after mask alpha, then calls dispatchBlend.",
            "coverageAppliedInBlend": "blend() folds activeAaClip clipCoverage(x,y) into src.alpha before SrcOver.",
            "dstBeforeBlend": "BlurredClippedCircleGM draws a white kSrc background before the difference clipped blur.",
            "valueWritten": (
                "For kSrcOver, sa==255 writes src directly; 0<sa<255 reads dst and writes "
                "SkColorSetARGB(outA,outR,outG,outB)."
            ),
            "readBackAfterDraw": "Committed CPU PNG pixel after the GM draw completes.",
            "maskBounds": mask_bounds,
        },
        "sourcePreservation": {
            "for283TargetPixelsPreserved": source_for283["combinedBoundaryFixture"]["metrics"]["targetPixels"] == 89,
            "for283BlendRedTintPreserved": source_for283["combinedBoundaryFixture"]["metrics"][
                "blendResultRedTintPixels"
            ]
            == 89,
            "for285DecisionPreserved": source_for285.get("decision") == for285.DECISION,
            "for285ResidualPreserved": source_for285.get("residualClassification")
            == for285.RESIDUAL_CLASSIFICATION,
        },
        "windows": windows,
        "combinedBoundaryFixture": {
            "maskPixels": int(combined_mask.sum()),
            "targetPixels": int(target.sum()),
            "metrics": combined_metrics,
        },
        "highDeltaTraceSamples": high_delta_traces,
        "causeMatrix": {
            "srcOverMath": {
                "selected": False,
                "evidence": (
                    "The reconstructed kSrcOver branch writes the exact pre-write blend result for "
                    "89/89 FOR-278 target pixels; 89/89 written values carry red tint."
                ),
            },
            "activeAaClipApplication": {
                "selected": False,
                "evidence": (
                    "activeAaClip coverage is applied in blend() and remains non-zero for 89/89 target pixels; "
                    "coverage modulation still leaves 89/89 red-tinted write values."
                ),
            },
            "layerOrStoreOrder": {
                "selected": True,
                "evidence": (
                    "The reconstructed store writes red-tinted values for 89/89 target pixels, but the "
                    "committed CPU output reads back only 9 red-dominant pixels and 78 white/layer pixels."
                ),
            },
            "proofInsufficient": {
                "selected": False,
                "evidence": (
                    "FOR-286 has enough bounded reconstruction evidence to choose the next correction target, "
                    "while still avoiding any production renderer change in this ticket."
                ),
            },
        },
        "decisionRationale": (
            "The FOR-286 store reconstruction keeps the FOR-285 route and M60 counters unchanged, then "
            "adds the missing write-stage value. Source entering dispatchBlend is red, activeAaClip coverage "
            "is non-zero, SrcOver produces the value that setPixel would write, and that value is red-tinted "
            "for all 89 FOR-278 target pixels. The final CPU readback is still dominated by white/layer pixels, "
            "so the next production fix should target CPU active-AA difference clip store/order behavior, not "
            "global SrcOver math, payload generation, setPixel storage, GPU/WebGPU, or thresholds."
        ),
        "fullSceneM60": {
            "cpuSimilarity": stats["cpuSimilarity"],
            "cpuMatchingPixels": stats["cpuMatchingPixels"],
            "cpuMaxChannelDelta": stats["cpuMaxChannelDelta"],
            "cpuReferenceGreaterThanThirtyTwoPixels": source_for285["m60PostFor284"][
                "cpuReferenceGreaterThanThirtyTwoPixels"
            ],
            "gpuSimilarity": stats["gpuSimilarity"],
            "gpuMatchingPixels": stats["gpuMatchingPixels"],
            "gpuMaxChannelDelta": stats["gpuMaxChannelDelta"],
            "gpuReferenceGreaterThanThirtyTwoPixels": source_for285["m60PostFor284"][
                "gpuReferenceGreaterThanThirtyTwoPixels"
            ],
        },
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRendererChanged": False,
            "cpuRendererChanged": False,
            "gpuRendererChanged": False,
            "globalBlendChanged": False,
            "setPixelChanged": False,
            "m60Promoted": False,
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
        f"| `{window['subzone']}` | {metrics['targetPixels']} | "
        f"{metrics['dispatchInputRedPayloadPixels']} | {metrics['coverageAppliedNonZeroPixels']} | "
        f"{metrics['dstBeforeBlendWhitePixels']} | {metrics['blendResultRedTintPixels']} | "
        f"{metrics['writeRedTintPixels']} | {metrics['readBackWhiteLayerPixels']} | "
        f"{metrics['writtenVsReadBackGreaterThanThirtyTwoPixels']} | "
        f"`{anchor['dispatchInputSrcRgba']}` | `{anchor['srcAfterActiveAaClipCoverageRgba']}` | "
        f"`{anchor['blendResultBeforeWriteRgba']}` | `{anchor['valueWrittenRgba']}` | "
        f"`{anchor['readBackAfterDrawRgba']}` |"
    )


def sample_row(sample: dict[str, Any]) -> str:
    return (
        f"| `{sample['subzone']}` | {sample['x']},{sample['y']} | "
        f"{sample['cpuReferenceMaxChannelDelta']} | `{sample['dispatchInputSrcRgba']}` | "
        f"{sample['coverageAppliedInBlend']} | `{sample['dstBeforeBlendRgba']}` | "
        f"`{sample['blendResultBeforeWriteRgba']}` | `{sample['valueWrittenRgba']}` | "
        f"`{sample['readBackAfterDrawRgba']}` | {sample['writtenVsReadBackMaxChannelDelta']} |"
    )


def cause_row(name: str, cause: dict[str, Any]) -> str:
    return f"| `{name}` | `{cause['selected']}` | {cause['evidence']} |"


def write_report(audit: dict[str, Any]) -> None:
    combined = audit["combinedBoundaryFixture"]["metrics"]
    full = audit["fullSceneM60"]
    route = audit["route"]
    rows = "\n".join(window_row(window) for window in audit["windows"])
    samples = "\n".join(sample_row(sample) for sample in audit["highDeltaTraceSamples"][:8])
    causes = "\n".join(cause_row(name, cause) for name, cause in audit["causeMatrix"].items())
    report = f"""# FOR-286 CPU Active AA Difference Store Trace

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Residual classification: `{audit["residualClassification"]}`

Next action: `{audit["nextAction"]}`

## Resultat Court

FOR-286 ajoute le stade manquant a FOR-283/FOR-285: la valeur que le chemin
CPU `kSrcOver` ecrit via `setPixel` sous `activeAaClip` difference. Aucun
renderer n'est modifie; le validateur reconstruit le store a partir des
branches source exactes et des PNG M60 versionnes.

| Mesure | Valeur |
|---|---:|
| Pixels fixture FOR-278 | {audit["combinedBoundaryFixture"]["maskPixels"]} |
| Pixels cibles CPU/reference >32 | {combined["targetPixels"]} |
| Source dispatch rouge | {combined["dispatchInputRedPayloadPixels"]} |
| Coverage `activeAaClip` non nulle dans `blend` | {combined["coverageAppliedNonZeroPixels"]} |
| Destination blanche avant blend | {combined["dstBeforeBlendWhitePixels"]} |
| Resultat avant ecriture red-tint | {combined["blendResultRedTintPixels"]} |
| Valeur ecrite red-tint | {combined["writeRedTintPixels"]} |
| Valeur ecrite rouge dominante | {combined["writeRedDominantPixels"]} |
| Readback rouge dominant apres draw | {combined["readBackRedDominantPixels"]} |
| Readback blanc/layer apres draw | {combined["readBackWhiteLayerPixels"]} |
| Ecrit vs readback >32 | {combined["writtenVsReadBackGreaterThanThirtyTwoPixels"]} |
| Ecrit vs readback delta max | {combined["writtenVsReadBackMaxChannelDelta"]} |

## Fenetres FOR-278

| Zone | Cibles | Src rouge | Cov non nulle | Dst blanc | Blend red-tint | Write red-tint | Readback blanc | Write/readback >32 | Src ancre | Src+cov ancre | Blend ancre | Write ancre | Readback ancre |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|---|
{rows}

## Echantillons Haute Difference

| Zone | Pixel | Delta CPU/ref | Src dispatch | cov | Dst avant | Blend avant write | Valeur ecrite | Readback apres draw | Write/readback delta |
|---|---|---:|---|---:|---|---|---|---|---:|
{samples}

## Separation Des Causes

| Cause | Retenue | Preuve |
|---|---|---|
{causes}

Decision: `{audit["decision"]}`. Le bug cible n'est pas la math `SrcOver`
ni l'application scalaire de `activeAaClip`: les deux produisent une valeur
ecrite rouge pour les 89 pixels. Le residu est classe
`{audit["residualClassification"]}` parce que la sortie CPU finale relit
majoritairement le blanc/layer.

## Pleine Scene Et Preservation

| Mesure | Valeur |
|---|---:|
| CPU/reference similarity | {full["cpuSimilarity"]}% |
| CPU matching pixels | {full["cpuMatchingPixels"]} |
| CPU max channel delta | {full["cpuMaxChannelDelta"]} |
| CPU/reference >32 | {full["cpuReferenceGreaterThanThirtyTwoPixels"]} |
| GPU/reference similarity | {full["gpuSimilarity"]}% |
| GPU/reference >32 | {full["gpuReferenceGreaterThanThirtyTwoPixels"]} |

M60 reste `expected-unsupported`: `{route["visualParityFallbackPreserved"]}`.
Le diagnostic crop reste `{route["cropFallbackPreserved"]}`. Aucun seuil,
chemin GPU/WebGPU, `blend`, `setPixel`, support global, Ganesh/Graphite ou
SkSL n'est modifie.

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
    if audit.get("probe") != "cpu-active-aa-difference-store-trace":
        fail("probe mismatch")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision must remain expected-unsupported")
    if audit.get("decision") != DECISION:
        fail("decision mismatch")
    if audit.get("nextAction") != NEXT_ACTION:
        fail("next action mismatch")
    if audit.get("residualClassification") != RESIDUAL_CLASSIFICATION:
        fail("residual classification mismatch")

    route = audit["route"]
    if route.get("cpu") != for269.CPU_ROUTE:
        fail("CPU route changed")
    if route.get("gpu") != for269.GPU_ROUTE:
        fail("GPU route changed")
    if route.get("gpuStatus") != "expected-unsupported":
        fail("GPU status changed")
    if route.get("fallbackReason") != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route.get("cropFallbackPreserved") != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")
    for key, value in route.get("sourceNeedles", {}).items():
        if value is not True:
            fail(f"source needle `{key}` failed")

    source = audit.get("sourcePreservation", {})
    for field in (
        "for283TargetPixelsPreserved",
        "for283BlendRedTintPreserved",
        "for285DecisionPreserved",
        "for285ResidualPreserved",
    ):
        if source.get(field) is not True:
            fail(f"source preservation `{field}` failed")

    combined_fixture = audit["combinedBoundaryFixture"]
    combined = combined_fixture["metrics"]
    if combined_fixture.get("maskPixels") != 148:
        fail("combined mask pixel count changed")
    if combined_fixture.get("targetPixels") != 89:
        fail("combined target pixel count changed")
    expected_combined = {
        "targetPixels": 89,
        "dispatchWouldRunPixels": 89,
        "dispatchInputRedPayloadPixels": 89,
        "coverageAppliedNonZeroPixels": 89,
        "dstBeforeBlendWhitePixels": 89,
        "blendResultRedTintPixels": 89,
        "writeWouldRunPixels": 89,
        "writeMatchesBlendResultPixels": 89,
        "writeRedTintPixels": 89,
        "writeRedDominantPixels": 82,
        "readBackRedDominantPixels": 9,
        "readBackWhiteLayerPixels": 78,
    }
    for key, expected in expected_combined.items():
        if combined.get(key) != expected:
            fail(f"combined metric `{key}` changed: {combined.get(key)} != {expected}")
    if combined.get("writtenVsReadBackGreaterThanThirtyTwoPixels") < 80:
        fail("write/readback divergence is no longer strong enough")

    expected_windows = {
        "draw_oval_outer_boundary": {
            "maskPixels": 59,
            "target": 59,
            "writeTint": 59,
            "writeDominant": 52,
            "readBackDominant": 0,
            "readBackWhite": 59,
        },
        "difference_oval_inner_boundary": {
            "maskPixels": 67,
            "target": 18,
            "writeTint": 18,
            "writeDominant": 18,
            "readBackDominant": 3,
            "readBackWhite": 14,
        },
        "halo_interior": {
            "maskPixels": 22,
            "target": 12,
            "writeTint": 12,
            "writeDominant": 12,
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
        if metrics.get("writeRedTintPixels") != expected["writeTint"]:
            fail(f"{subzone} write red-tint count changed")
        if metrics.get("writeRedDominantPixels") != expected["writeDominant"]:
            fail(f"{subzone} write red-dominant count changed")
        if metrics.get("readBackRedDominantPixels") != expected["readBackDominant"]:
            fail(f"{subzone} readback red-dominant count changed")
        if metrics.get("readBackWhiteLayerPixels") != expected["readBackWhite"]:
            fail(f"{subzone} readback white/layer count changed")
        anchor = window["anchorTrace"]
        if anchor.get("writeMatchesBlendResult") is not True:
            fail(f"{subzone} anchor write no longer matches blend result")
        if anchor.get("writeCarriesRedTint") is not True:
            fail(f"{subzone} anchor lost red-tint write")

    cause = audit.get("causeMatrix", {})
    if cause["srcOverMath"].get("selected") is not False:
        fail("SrcOver math must not be selected")
    if cause["activeAaClipApplication"].get("selected") is not False:
        fail("activeAaClip application must not be selected")
    if cause["layerOrStoreOrder"].get("selected") is not True:
        fail("layer/store order must be selected")
    if cause["proofInsufficient"].get("selected") is not False:
        fail("proof insufficient must not be selected")

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
        "globalBlendChanged",
        "setPixelChanged",
        "m60Promoted",
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
        "FOR-286 CPU Active AA Difference Store Trace",
        DECISION,
        NEXT_ACTION,
        RESIDUAL_CLASSIFICATION,
        "SrcOver",
        "activeAaClip",
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
        "FOR-286 validation passed: "
        f"{audit['decision']}; "
        f"targets={combined['targetPixels']}; "
        f"writeRedTint={combined['writeRedTintPixels']}; "
        f"readBackWhiteLayer={combined['readBackWhiteLayerPixels']}; "
        f"writeVsReadback>32={combined['writtenVsReadBackGreaterThanThirtyTwoPixels']}."
    )


if __name__ == "__main__":
    main()
