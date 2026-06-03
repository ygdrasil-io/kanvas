#!/usr/bin/env python3
"""Generate and validate FOR-280 CPU AA difference-clip coverage evidence."""

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


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-280"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "m60-cpu-aa-difference-clip-coverage-edge-for280.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-280-cpu-aa-difference-clip-coverage-edge.md"
SOURCE_FOR277 = SCENE_DIR / "m60-post-for276-cpu-residual-audit-for277.json"
SOURCE_FOR278 = SCENE_DIR / "m60-boundary-layer-composite-fixture-for278.json"
SOURCE_FOR279 = SCENE_DIR / "m60-cpu-layer-boundary-composite-for279.json"
STATS = SCENE_DIR / "stats.json"
GM_SOURCE = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"
CANVAS_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt"
AA_CLIP_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkAAClip.kt"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"

DECISION = "REFUSE_CORRECTION_PENDING_EXPLICIT_SKAA_CLIP_AND_MASK_FILTER_TRACE"
NEXT_ACTION = "INSTRUMENT_CPU_MASK_FILTER_AND_SKAA_CLIP_COVERAGE_EDGE_TRACE"
PRIMARY_WINDOWS = (
    "draw_oval_outer_boundary",
    "difference_oval_inner_boundary",
    "halo_interior",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-280 validation failed: {message}")


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


def comparison_by_name(audit: dict[str, Any], name: str) -> dict[str, Any]:
    for item in audit.get("comparisons", []):
        if item.get("comparison") == name:
            return item
    fail(f"missing comparison `{name}`")


def coverage_bucket(value: int) -> str:
    if value == 0:
        return "zero"
    if value == 255:
        return "full"
    return "partial"


def ellipse_coverage_4x4(x: int, y: int, rect: list[int]) -> int:
    left, top, right, bottom = rect
    cx = (left + right) / 2.0
    cy = (top + bottom) / 2.0
    rx = (right - left) / 2.0
    ry = (bottom - top) / 2.0
    covered = 0
    for sy in range(4):
        for sx in range(4):
            px = x + (sx + 0.5) / 4.0
            py = y + (sy + 0.5) / 4.0
            if ((px - cx) / rx) ** 2 + ((py - cy) / ry) ** 2 <= 1.0:
                covered += 1
    return int(covered * 255 / 16)


def difference_clip_coverage(x: int, y: int, difference_oval: list[int]) -> int:
    # Parent clip coverage is 255 in M60; SkAAClip kDifference therefore
    # reduces to 255 - pathCoverage after the same 4x4 alpha quantization.
    return 255 - ellipse_coverage_4x4(x, y, difference_oval)


def counter() -> dict[str, int]:
    return {"zero": 0, "partial": 0, "full": 0}


def update_counter(target: dict[str, int], value: int) -> None:
    target[coverage_bucket(value)] += 1


def mask_from_window(window: dict[str, Any], masks: dict[str, np.ndarray]) -> np.ndarray:
    bounds = window["bounds"]
    height, width = next(iter(masks.values())).shape
    mask = np.zeros((height, width), dtype=bool)
    mask[bounds["top"] : bounds["bottom"], bounds["left"] : bounds["right"]] = True
    return mask & masks[window["subzone"]]


def signed_summary(base: np.ndarray, actual: np.ndarray, mask: np.ndarray) -> dict[str, Any]:
    signed = actual - base
    if not mask.any():
        return {
            "meanSignedRgba": [0.0, 0.0, 0.0, 0.0],
            "minSignedRgba": [0, 0, 0, 0],
            "maxSignedRgba": [0, 0, 0, 0],
            "alphaGreaterThanThirtyTwoPixels": 0,
        }
    alpha_delta = np.abs(signed[:, :, 3])
    return {
        "meanSignedRgba": [rounded(signed[:, :, channel][mask].mean(), 3) for channel in range(4)],
        "minSignedRgba": [int(signed[:, :, channel][mask].min()) for channel in range(4)],
        "maxSignedRgba": [int(signed[:, :, channel][mask].max()) for channel in range(4)],
        "alphaGreaterThanThirtyTwoPixels": int((alpha_delta[mask] > 32).sum()),
    }


def coverage_metrics_for_mask(
    *,
    mask: np.ndarray,
    reference: np.ndarray,
    cpu: np.ndarray,
    gpu: np.ndarray,
    geometry: dict[str, Any],
) -> dict[str, Any]:
    cpu_ref_diff = np.abs(cpu - reference).max(axis=2)
    gpu_ref_diff = np.abs(gpu - reference).max(axis=2)
    cpu_gt32 = mask & (cpu_ref_diff > 32)
    gpu_gt32 = mask & (gpu_ref_diff > 32)
    clip_counts = counter()
    draw_counts = counter()
    clip_values: list[int] = []
    draw_values: list[int] = []
    for y, x in zip(*np.nonzero(cpu_gt32)):
        clip_value = difference_clip_coverage(int(x), int(y), geometry["differenceOvalDeviceBounds"])
        draw_value = ellipse_coverage_4x4(int(x), int(y), geometry["drawOvalDeviceBounds"])
        clip_values.append(clip_value)
        draw_values.append(draw_value)
        update_counter(clip_counts, clip_value)
        update_counter(draw_counts, draw_value)
    cpu_buckets = for278.for272.color_buckets(cpu, cpu_gt32)
    reference_buckets = for278.for272.color_buckets(reference, cpu_gt32)
    gpu_buckets = for278.for272.color_buckets(gpu, gpu_gt32)
    return {
        "pixels": int(mask.sum()),
        "cpuReferenceGreaterThanThirtyTwoPixels": int(cpu_gt32.sum()),
        "gpuReferenceGreaterThanThirtyTwoPixels": int(gpu_gt32.sum()),
        "cpuReferenceMaxChannelDelta": int(cpu_ref_diff[mask].max()) if mask.any() else 0,
        "gpuReferenceMaxChannelDelta": int(gpu_ref_diff[mask].max()) if mask.any() else 0,
        "estimatedDifferenceClipCoverageOnCpuReferenceGt32": {
            "buckets": clip_counts,
            "min": int(min(clip_values)) if clip_values else 0,
            "max": int(max(clip_values)) if clip_values else 0,
            "mean": rounded(sum(clip_values) / len(clip_values)) if clip_values else 0.0,
        },
        "estimatedDrawOvalCoverageOnCpuReferenceGt32": {
            "buckets": draw_counts,
            "min": int(min(draw_values)) if draw_values else 0,
            "max": int(max(draw_values)) if draw_values else 0,
            "mean": rounded(sum(draw_values) / len(draw_values)) if draw_values else 0.0,
        },
        "cpuActualColorBucketsOnGt32": cpu_buckets,
        "referenceColorBucketsOnGt32": reference_buckets,
        "gpuActualColorBucketsOnGpuGt32": gpu_buckets,
        "cpuMinusReferenceOnGt32": signed_summary(reference, cpu, cpu_gt32),
        "gpuMinusReferenceOnGt32": signed_summary(reference, gpu, gpu_gt32),
    }


def sample_with_coverage(sample: dict[str, Any], geometry: dict[str, Any]) -> dict[str, Any]:
    x = int(sample["x"])
    y = int(sample["y"])
    clip_cov = difference_clip_coverage(x, y, geometry["differenceOvalDeviceBounds"])
    draw_cov = ellipse_coverage_4x4(x, y, geometry["drawOvalDeviceBounds"])
    enriched = dict(sample)
    enriched["estimatedDifferenceClipCoverage"] = clip_cov
    enriched["estimatedDrawOvalCoverage"] = draw_cov
    enriched["estimatedDifferenceClipCoverageBucket"] = coverage_bucket(clip_cov)
    enriched["estimatedDrawOvalCoverageBucket"] = coverage_bucket(draw_cov)
    return enriched


def validate_sources() -> None:
    gm_text = require_needles(
        GM_SOURCE,
        (
            "public class BlurredClippedCircleGM",
            "c.save()",
            "c.clipRRect(clipRRect, SkClipOp.kDifference, doAntiAlias = true)",
            "c.drawRRect(rr, paint)",
            "SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.366025f)",
            "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)",
        ),
    )
    if "saveLayer(" in gm_text:
        fail("BlurredClippedCircleGM unexpectedly uses saveLayer")
    require_needles(
        CANVAS_SOURCE,
        (
            "private fun clipPathDifference",
            "val pathAac = rasterisePathToAaClip(s, path, s.clip, doAntiAlias)",
            "val combined = SkAAClip(s.aaClip ?: SkAAClip(s.clip))",
            "combined.op(pathAac, SkRegion.Op.kDifference)",
            "raster.setActiveClip(s.aaClip)",
        ),
    )
    require_needles(
        AA_CLIP_SOURCE,
        (
            "yielding alphas in `{0, 16, 32, ..., 240, 255}`",
            "SkRegion.Op.kDifference -> mulDiv255Round(a, 255 - b)",
            "coverage(x: Int, y: Int): Int",
            "private fun setPathAA(path: SkPath, clip: SkRegion): Boolean",
        ),
    )
    require_needles(
        CPU_SOURCE,
        (
            "private fun clipCoverage(x: Int, y: Int): Int",
            "private fun drawPathWithMaskFilter",
            "val preserveOffClipMaskSource = activeAaClip != null",
            "val maskA = blurred[y * maskW + x].toInt() and 0xFF",
            "dispatchBlend(devX, devY",
            "if (activeAaClip != null) cov = clipCoverage(x, y)",
        ),
    )


def generate_audit() -> dict[str, Any]:
    validate_sources()
    source_for277 = load_json(SOURCE_FOR277)
    source_for278 = load_json(SOURCE_FOR278)
    source_for279 = load_json(SOURCE_FOR279)
    regenerated_for278 = for278.generate_audit()
    stats = load_json(STATS)
    route_cpu = load_json(SCENE_DIR / "route-cpu.json")
    route_gpu = load_json(SCENE_DIR / "route-gpu.json")
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

    combined_mask = np.zeros((height, width), dtype=bool)
    windows: list[dict[str, Any]] = []
    for window in source_for278["windows"]:
        subzone = window["subzone"]
        if subzone not in PRIMARY_WINDOWS:
            fail(f"unexpected FOR-278 window `{subzone}`")
        mask = mask_from_window(window, masks)
        combined_mask |= mask
        windows.append(
            {
                "name": window["name"],
                "subzone": subzone,
                "bounds": window["bounds"],
                "maskPixels": int(mask.sum()),
                "anchorSample": sample_with_coverage(window["anchorSample"], geometry),
                "metrics": coverage_metrics_for_mask(
                    mask=mask,
                    reference=images["reference"],
                    cpu=images["cpu"],
                    gpu=images["gpu"],
                    geometry=geometry,
                ),
            }
        )

    full_cpu = comparison_by_name(source_for277, "cpu_vs_reference")
    full_gpu = comparison_by_name(source_for277, "gpu_vs_reference")
    combined_metrics = coverage_metrics_for_mask(
        mask=combined_mask,
        reference=images["reference"],
        cpu=images["cpu"],
        gpu=images["gpu"],
        geometry=geometry,
    )
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "cpu-aa-difference-clip-coverage-edge",
        "sceneId": SCENE_ID,
        "backend": "CPU/Reference/WebGPU",
        "sourceAudits": {
            "for277": str(SOURCE_FOR277.relative_to(PROJECT_ROOT)),
            "for278": str(SOURCE_FOR278.relative_to(PROJECT_ROOT)),
            "for279": str(SOURCE_FOR279.relative_to(PROJECT_ROOT)),
        },
        "sourceImages": {
            "reference": str((SCENE_DIR / "skia.png").relative_to(PROJECT_ROOT)),
            "cpu": str((SCENE_DIR / "cpu.png").relative_to(PROJECT_ROOT)),
            "gpu": str((SCENE_DIR / "gpu.png").relative_to(PROJECT_ROOT)),
        },
        "sourceTest": "org.skia.gpu.webgpu.NestedClipSceneCaptureTest",
        "artifactRoot": str(SCENE_DIR.relative_to(PROJECT_ROOT)),
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "decision": DECISION,
        "decisionRationale": (
            "FOR-280 confirms that the FOR-278 high-delta pixels are on the remaining "
            "SkAAClip difference -> drawPathWithMaskFilter -> clipCoverage path, but a "
            "production correction is still not causal enough. A 4x4 estimate of the final "
            "difference clip keeps all 89 CPU/reference >32 target pixels non-zero and 79 of "
            "them fully covered, including all three anchors. The visible CPU loss therefore "
            "cannot be explained by final clipCoverage alone; the missing observable is the "
            "mask-filter source/blur alpha before clipCoverage and blend."
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
        "coverageModel": {
            "skAaClipPathCoverage": "4x4 supersampled path coverage quantized with c * 255 / 16",
            "differenceFormula": "parentCoverage=255, pathCoverage=b => clipCoverage=255-b",
            "dispatchFormula": "drawPathWithMaskFilter computes effA=paintA*maskA/255 then dispatchBlend applies src.alpha*=clipCoverage/255",
            "observableBoundary": (
                "maskA is local to drawPathWithMaskFilter and clipCoverage is private; this audit "
                "uses final PNG pixels plus a deterministic 4x4 edge estimate, not renderer instrumentation."
            ),
        },
        "causalPath": [
            {
                "file": str(GM_SOURCE.relative_to(PROJECT_ROOT)),
                "function": "BlurredClippedCircleGM.onDraw",
                "role": "issues save/clipRRect(kDifference)/drawRRect with blur and Blend(RED, kSrcIn)",
            },
            {
                "file": str(CANVAS_SOURCE.relative_to(PROJECT_ROOT)),
                "function": "SkCanvas.clipPathDifference",
                "role": "rasterizes the oval into SkAAClip and applies kDifference",
            },
            {
                "file": str(AA_CLIP_SOURCE.relative_to(PROJECT_ROOT)),
                "function": "SkAAClip.op + coverage",
                "role": "combines parent coverage with path coverage and serves clipCoverage(x,y)",
            },
            {
                "file": str(CPU_SOURCE.relative_to(PROJECT_ROOT)),
                "function": "SkBitmapDevice.drawPathWithMaskFilter",
                "role": "builds blurred mask alpha, then dispatches blended pixels through final clipCoverage",
            },
        ],
        "for278RegeneratedMatchesSource": regenerated_for278["classification"] == source_for278["classification"],
        "for279DecisionPreserved": source_for279.get("decision") == (
            "REFUSE_CORRECTION_PENDING_DEEPER_LAYER_COMPOSITE_MODEL"
        ),
        "windows": windows,
        "combinedBoundaryFixture": {
            "maskPixels": int(combined_mask.sum()),
            "metrics": combined_metrics,
        },
        "alphaRgbRelation": {
            "cpuReferenceAlphaGreaterThanThirtyTwoPixels": combined_metrics[
                "cpuMinusReferenceOnGt32"
            ]["alphaGreaterThanThirtyTwoPixels"],
            "cpuActualWhiteLayerPixels": combined_metrics["cpuActualColorBucketsOnGt32"][
                "white_or_layer_background"
            ],
            "referenceRedTintPixels": combined_metrics["referenceColorBucketsOnGt32"][
                "red_tinted_reference_like"
            ],
            "gpuReferenceGreaterThanThirtyTwoPixels": combined_metrics[
                "gpuReferenceGreaterThanThirtyTwoPixels"
            ],
            "interpretation": (
                "Le residu porte sur la charge RGB visible avec alpha opaque: les pixels CPU cibles "
                "sont majoritairement blancs/fond-layer alors que la reference reste rouge teintee; "
                "l'alpha ne differe jamais de plus de 32. Comme la couverture finale `difference` "
                "estimee est non nulle pour chaque pixel cible, une correction limitee a `clipCoverage` "
                "ou au composite fond/layer serait non causale."
            ),
        },
        "fullSceneM60": {
            "cpuSimilarity": stats["cpuSimilarity"],
            "cpuMatchingPixels": stats["cpuMatchingPixels"],
            "cpuMaxChannelDelta": stats["cpuMaxChannelDelta"],
            "cpuReferenceGreaterThanThirtyTwoPixels": full_cpu["greaterThanThirtyTwoPixels"],
            "gpuSimilarity": stats["gpuSimilarity"],
            "gpuMatchingPixels": stats["gpuMatchingPixels"],
            "gpuMaxChannelDelta": stats["gpuMaxChannelDelta"],
            "gpuReferenceGreaterThanThirtyTwoPixels": full_gpu["greaterThanThirtyTwoPixels"],
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
    clip = metrics["estimatedDifferenceClipCoverageOnCpuReferenceGt32"]
    draw = metrics["estimatedDrawOvalCoverageOnCpuReferenceGt32"]
    sample = window["anchorSample"]
    return (
        f"| `{window['subzone']}` | {window['maskPixels']} | "
        f"{metrics['cpuReferenceGreaterThanThirtyTwoPixels']} | "
        f"{metrics['gpuReferenceGreaterThanThirtyTwoPixels']} | "
        f"`{clip['buckets']}` | `{draw['buckets']}` | "
        f"{sample['estimatedDifferenceClipCoverage']} | "
        f"{sample['estimatedDrawOvalCoverage']} |"
    )


def sample_row(window: dict[str, Any]) -> str:
    sample = window["anchorSample"]
    return (
        f"| `{sample['subzone']}` | {sample['x']},{sample['y']} | "
        f"`{sample['referenceRgba']}` | `{sample['cpuRgba']}` | `{sample['gpuRgba']}` | "
        f"`{sample['cpuMinusReferenceSignedRgba']}` | "
        f"{sample['estimatedDifferenceClipCoverage']} | {sample['estimatedDrawOvalCoverage']} |"
    )


def write_report(audit: dict[str, Any]) -> None:
    combined = audit["combinedBoundaryFixture"]["metrics"]
    clip = combined["estimatedDifferenceClipCoverageOnCpuReferenceGt32"]
    draw = combined["estimatedDrawOvalCoverageOnCpuReferenceGt32"]
    alpha_rgb = audit["alphaRgbRelation"]
    full = audit["fullSceneM60"]
    route = audit["route"]
    rows = "\n".join(window_row(window) for window in audit["windows"])
    samples = "\n".join(sample_row(window) for window in audit["windows"])
    report = f"""# FOR-280 CPU AA Difference Clip Coverage Edge

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Support scene: `{audit["supportDecision"]}`

FOR-280 audite le chemin causal restant apres FOR-279:
`SkAAClip` difference -> `SkBitmapDevice.drawPathWithMaskFilter` ->
`clipCoverage`. Aucun renderer n'est modifie.

## Resultat Court

| Mesure | Valeur |
|---|---:|
| Pixels fixture FOR-278 | {audit["combinedBoundaryFixture"]["maskPixels"]} |
| CPU/reference >32 dans la fixture | {combined["cpuReferenceGreaterThanThirtyTwoPixels"]} |
| GPU/reference >32 dans la fixture | {combined["gpuReferenceGreaterThanThirtyTwoPixels"]} |
| Clip difference estime pleine couverture sur CPU/ref >32 | {clip["buckets"]["full"]} |
| Clip difference estime couverture partielle sur CPU/ref >32 | {clip["buckets"]["partial"]} |
| Clip difference estime couverture zero sur CPU/ref >32 | {clip["buckets"]["zero"]} |
| Draw oval direct estime pleine couverture sur CPU/ref >32 | {draw["buckets"]["full"]} |
| Draw oval direct estime couverture partielle sur CPU/ref >32 | {draw["buckets"]["partial"]} |
| Draw oval direct estime couverture zero sur CPU/ref >32 | {draw["buckets"]["zero"]} |
| CPU blanc/fond sur >32 | {alpha_rgb["cpuActualWhiteLayerPixels"]} |
| Reference rouge teintee sur >32 | {alpha_rgb["referenceRedTintPixels"]} |
| CPU alpha >32 | {alpha_rgb["cpuReferenceAlphaGreaterThanThirtyTwoPixels"]} |

Le clip final estime ne coupe pas les pixels cibles: les 89 pixels
CPU/reference >32 ont tous une couverture `difference` non nulle, et 79 sont
estimes en pleine couverture. Les trois ancres FOR-278 sont en pleine couverture. Cela
refuse une correction limitee a `clipCoverage` ou au composite fond/layer.

## Fenetres FOR-278 Et Couverture Estimee

| Zone | Pixels | CPU/ref >32 | GPU/ref >32 | Repartition clip difference | Repartition draw oval | Clip ancre | Draw ancre |
|---|---:|---:|---:|---|---|---:|---:|
{rows}

## Echantillons Ancres

| Zone | Pixel | Reference RGBA | CPU RGBA | GPU RGBA | CPU-reference signe | Clip estime | Draw estime |
|---|---|---|---|---|---|---:|---:|
{samples}

## Relation Alpha/RGB

{alpha_rgb["interpretation"]}

Le signal reste RGB/payload sur alpha opaque: `alpha >32 == 0`, CPU garde
majoritairement blanc/fond, et la reference garde une charge rouge teintee.
Le GPU reste plus proche de la reference sur ces fenetres.

## Chemin CPU Observe

1. `BlurredClippedCircleGM.onDraw`: `save()`, `clipRRect(kDifference)`, puis `drawRRect`.
2. `SkCanvas.clipPathDifference`: construit le `SkAAClip` difference.
3. `SkAAClip.op`: applique `kDifference` via `parent * (255 - path) / 255`.
4. `SkBitmapDevice.drawPathWithMaskFilter`: calcule le masque floute puis appelle `dispatchBlend`.
5. `dispatchBlend`: applique `clipCoverage(x, y)` au `src.alpha`.

## Refus

`{audit["decision"]}`.

La preuve est insuffisante pour un patch de production: `maskA` est local au
chemin `drawPathWithMaskFilter`, `clipCoverage` est prive, et les pixels finaux
ne separent pas directement source-mask, flou, clip final et blend. Le prochain
patch admissible doit donc ajouter une fixture/trace ciblee de ces deux valeurs
avant de modifier la regle generale de couverture.

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

La suite doit tracer, pour ces memes fenetres, `maskA` apres blur,
`clipCoverage`, `src.alpha` avant et apres modulation, puis comparer CPU,
reference et GPU avant toute correction.

## Validation

```text
rtk python3 scripts/validate_for280_cpu_aa_difference_clip_coverage_edge.py
rtk python3 scripts/validate_for279_cpu_layer_boundary_composite_refusal.py
rtk python3 scripts/validate_for278_m60_boundary_layer_composite_fixture.py
rtk python3 scripts/validate_for277_m60_post_for276_cpu_residual_audit.py
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
    if audit.get("probe") != "cpu-aa-difference-clip-coverage-edge":
        fail("probe mismatch")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision must remain expected-unsupported")
    if audit.get("decision") != DECISION:
        fail("decision mismatch")
    if audit.get("nextAction") != NEXT_ACTION:
        fail("next action mismatch")
    if audit.get("for278RegeneratedMatchesSource") is not True:
        fail("regenerated FOR-278 classification does not match source")
    if audit.get("for279DecisionPreserved") is not True:
        fail("FOR-279 refusal decision was not preserved")

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

    windows = audit.get("windows", [])
    if len(windows) != 3:
        fail("expected three FOR-278 windows")
    expected = {
        "draw_oval_outer_boundary": {
            "maskPixels": 59,
            "cpuGt32": 59,
            "gpuGt32": 11,
            "clip": {"zero": 0, "partial": 0, "full": 59},
            "draw": {"zero": 17, "partial": 8, "full": 34},
            "anchorClip": 255,
            "anchorDraw": 255,
        },
        "difference_oval_inner_boundary": {
            "maskPixels": 67,
            "cpuGt32": 18,
            "gpuGt32": 0,
            "clip": {"zero": 0, "partial": 10, "full": 8},
            "draw": {"zero": 0, "partial": 0, "full": 18},
            "anchorClip": 255,
            "anchorDraw": 255,
        },
        "halo_interior": {
            "maskPixels": 22,
            "cpuGt32": 12,
            "gpuGt32": 0,
            "clip": {"zero": 0, "partial": 0, "full": 12},
            "draw": {"zero": 0, "partial": 0, "full": 12},
            "anchorClip": 255,
            "anchorDraw": 255,
        },
    }
    for window in windows:
        subzone = window.get("subzone")
        item = expected.get(subzone)
        if item is None:
            fail(f"unexpected window `{subzone}`")
        metrics = window["metrics"]
        if window.get("maskPixels") != item["maskPixels"]:
            fail(f"{subzone} mask pixels changed")
        if metrics.get("cpuReferenceGreaterThanThirtyTwoPixels") != item["cpuGt32"]:
            fail(f"{subzone} CPU/reference >32 changed")
        if metrics.get("gpuReferenceGreaterThanThirtyTwoPixels") != item["gpuGt32"]:
            fail(f"{subzone} GPU/reference >32 changed")
        if metrics["estimatedDifferenceClipCoverageOnCpuReferenceGt32"]["buckets"] != item["clip"]:
            fail(f"{subzone} estimated difference clip buckets changed")
        if metrics["estimatedDrawOvalCoverageOnCpuReferenceGt32"]["buckets"] != item["draw"]:
            fail(f"{subzone} estimated draw oval buckets changed")
        sample = window["anchorSample"]
        if sample.get("estimatedDifferenceClipCoverage") != item["anchorClip"]:
            fail(f"{subzone} anchor difference clip coverage changed")
        if sample.get("estimatedDrawOvalCoverage") != item["anchorDraw"]:
            fail(f"{subzone} anchor draw oval coverage changed")

    combined = audit.get("combinedBoundaryFixture", {})
    if combined.get("maskPixels") != 148:
        fail("combined mask pixel count changed")
    metrics = combined.get("metrics", {})
    if metrics.get("cpuReferenceGreaterThanThirtyTwoPixels") != 89:
        fail("combined CPU/reference >32 changed")
    if metrics.get("gpuReferenceGreaterThanThirtyTwoPixels") != 11:
        fail("combined GPU/reference >32 changed")
    if metrics.get("cpuReferenceMaxChannelDelta") != 237:
        fail("combined CPU/reference max delta changed")
    if metrics["estimatedDifferenceClipCoverageOnCpuReferenceGt32"]["buckets"] != {
        "zero": 0,
        "partial": 10,
        "full": 79,
    }:
        fail("combined estimated difference clip buckets changed")
    if metrics["estimatedDrawOvalCoverageOnCpuReferenceGt32"]["buckets"] != {
        "zero": 17,
        "partial": 8,
        "full": 64,
    }:
        fail("combined estimated draw oval buckets changed")

    alpha_rgb = audit.get("alphaRgbRelation", {})
    if alpha_rgb.get("cpuReferenceAlphaGreaterThanThirtyTwoPixels") != 0:
        fail("CPU/reference alpha >32 changed")
    if alpha_rgb.get("cpuActualWhiteLayerPixels") != 78:
        fail("CPU white/layer count changed")
    if alpha_rgb.get("referenceRedTintPixels") != 56:
        fail("reference red tint count changed")
    if alpha_rgb.get("gpuReferenceGreaterThanThirtyTwoPixels") != 11:
        fail("GPU/reference >32 changed")

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
        "FOR-280 CPU AA Difference Clip Coverage Edge",
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
    clip = combined["estimatedDifferenceClipCoverageOnCpuReferenceGt32"]["buckets"]
    print(
        "FOR-280 validation passed: "
        f"{audit['decision']}; "
        f"{combined['cpuReferenceGreaterThanThirtyTwoPixels']} CPU/reference >32 target pixels, "
        f"difference clip coverage buckets={clip}, "
        f"M60 remains {audit['supportDecision']}."
    )


if __name__ == "__main__":
    main()
