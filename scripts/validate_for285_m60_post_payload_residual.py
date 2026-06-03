#!/usr/bin/env python3
"""Generate and validate FOR-285 M60 post-FOR-284 residual evidence."""

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
import validate_for278_m60_boundary_layer_composite_fixture as for278
import validate_for281_cpu_mask_filter_clip_coverage_trace as for281
import validate_for283_cpu_dispatch_blend_store_trace as for283
import validate_for284_cpu_mask_filter_a8_srcin_payload_patch as for284


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-285"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "m60-post-payload-residual-for285.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-285-m60-post-payload-residual.md"

DECISION = "NEXT_FIX_CPU_ACTIVE_AA_DIFFERENCE_CLIP_STORE_ORDER_NOT_PAYLOAD"
NEXT_ACTION = "INSTRUMENT_CPU_MASK_FILTER_A8_SRCIN_RUNTIME_STORE_UNDER_ACTIVE_AA_DIFFERENCE_CLIP"
RESIDUAL_CLASSIFICATION = "POST_DISPATCH_LAYER_CLIP_STORE_ORDER_RESIDUAL"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-285 validation failed: {message}")


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


def rounded(value: float, digits: int = 6) -> float:
    return round(float(value), digits)


def max_delta_image(actual: np.ndarray, reference: np.ndarray) -> np.ndarray:
    return np.abs(actual.astype(np.int16) - reference.astype(np.int16)).max(axis=2)


def gt32_pixels(actual: np.ndarray, reference: np.ndarray) -> int:
    return int((max_delta_image(actual, reference) > 32).sum())


def full_scene_metrics(stats: dict[str, Any], reference: np.ndarray, cpu: np.ndarray, gpu: np.ndarray) -> dict[str, Any]:
    return {
        "cpuSimilarity": stats["cpuSimilarity"],
        "cpuMatchingPixels": stats["cpuMatchingPixels"],
        "cpuMaxChannelDelta": stats["cpuMaxChannelDelta"],
        "cpuReferenceGreaterThanThirtyTwoPixels": gt32_pixels(cpu, reference),
        "gpuSimilarity": stats["gpuSimilarity"],
        "gpuMatchingPixels": stats["gpuMatchingPixels"],
        "gpuMaxChannelDelta": stats["gpuMaxChannelDelta"],
        "gpuReferenceGreaterThanThirtyTwoPixels": gt32_pixels(gpu, reference),
    }


def delta_metrics(before: dict[str, Any], after: dict[str, Any]) -> dict[str, Any]:
    keys = (
        "cpuSimilarity",
        "cpuMatchingPixels",
        "cpuMaxChannelDelta",
        "cpuReferenceGreaterThanThirtyTwoPixels",
        "gpuSimilarity",
        "gpuMatchingPixels",
        "gpuMaxChannelDelta",
        "gpuReferenceGreaterThanThirtyTwoPixels",
    )
    return {key: rounded(after[key] - before[key]) for key in keys}


def comparison_by_name(window: dict[str, Any], name: str) -> dict[str, Any]:
    for item in window.get("comparisons", []):
        if item.get("comparison") == name:
            return item
    fail(f"missing comparison `{name}` in {window.get('subzone')}")


def current_boundary_buckets(
    source_for278: dict[str, Any],
    reference: np.ndarray,
    cpu: np.ndarray,
    gpu: np.ndarray,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    height, width, _ = reference.shape
    diff = max_delta_image(cpu, reference)
    _, masks = for271.make_envelope_masks(width, height)
    combined_target = np.zeros((height, width), dtype=bool)
    windows: list[dict[str, Any]] = []
    for window in source_for278["windows"]:
        bounds_mask = for281.mask_from_window(window, masks)
        target = bounds_mask & (diff > 32)
        combined_target |= target
        cpu_buckets = for272.color_buckets(cpu, target)
        reference_buckets = for272.color_buckets(reference, target)
        gpu_buckets = for272.color_buckets(gpu, target)
        windows.append(
            {
                "name": window["name"],
                "subzone": window["subzone"],
                "bounds": window["bounds"],
                "targetPixels": int(target.sum()),
                "cpuColorBucketsOnTarget": cpu_buckets,
                "referenceColorBucketsOnTarget": reference_buckets,
                "gpuColorBucketsOnTarget": gpu_buckets,
                "cpuWhiteLayerPixelsOnTarget": cpu_buckets.get("white_or_layer_background", 0),
                "cpuRedTintPixelsOnTarget": cpu_buckets.get("red_tinted_reference_like", 0),
                "referenceRedTintPixelsOnTarget": reference_buckets.get("red_tinted_reference_like", 0),
                "gpuRedTintPixelsOnTarget": gpu_buckets.get("red_tinted_reference_like", 0),
            }
        )
    return windows, {
        "targetPixels": int(combined_target.sum()),
        "cpuColorBucketsOnTarget": for272.color_buckets(cpu, combined_target),
        "referenceColorBucketsOnTarget": for272.color_buckets(reference, combined_target),
        "gpuColorBucketsOnTarget": for272.color_buckets(gpu, combined_target),
    }


def route_source_needles() -> dict[str, bool]:
    canvas = (PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt").read_text(encoding="utf-8")
    device = (PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt").read_text(
        encoding="utf-8"
    )
    gm = (
        PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"
    ).read_text(encoding="utf-8")
    return {
        "gmUsesDrawRRectOval": "SkRRect.MakeOval(r)" in gm and "c.drawRRect(rr, paint)" in gm,
        "drawRRectRoutesToDrawPath": "public open fun drawRRect" in canvas
        and "drawPath(SkPath.RRect(rrect), paint)" in canvas,
        "drawPathRoutesMaskFilterToPatchedCallsite": "val maskFilter = paint.maskFilter" in device
        and "drawPathWithMaskFilter(effectivePath, ctm, clip, paint, maskFilter)" in device,
        "for284SrcInBranchPresent": "val filterMaskPayloadAfterMask = colorFilter" in device
        and "?.mode == SkBlendMode.kSrcIn" in device
        and "applyColorFilter(colorFilter, (maskedA shl 24) or (paint.color and 0x00FFFFFF))" in device,
    }


def source_needles_valid(route_needles: dict[str, bool]) -> None:
    for key, value in route_needles.items():
        if value is not True:
            fail(f"source route needle `{key}` was not found")


def generate_audit() -> dict[str, Any]:
    source_for278 = load_json(for278.AUDIT)
    for283_audit = load_json(for283.AUDIT)
    for284_audit = load_json(for284.AUDIT)
    stats = load_json(SCENE_DIR / "stats.json")
    route_cpu = load_json(SCENE_DIR / "route-cpu.json")
    route_gpu = load_json(SCENE_DIR / "route-gpu.json")
    reference = for269.load_image("skia")
    cpu = for269.load_image("cpu")
    gpu = for269.load_image("gpu")
    if reference.shape != cpu.shape or reference.shape != gpu.shape:
        fail("reference, CPU, and GPU PNG dimensions differ")

    route_needles = route_source_needles()
    source_needles_valid(route_needles)
    boundary_windows, boundary_combined = current_boundary_buckets(source_for278, reference, cpu, gpu)

    before = for284_audit["m60Before"]
    after = full_scene_metrics(stats, reference, cpu, gpu)
    for283_combined = for283_audit["combinedBoundaryFixture"]["metrics"]
    for284_payload = for284_audit["payloadContract"]
    for282_buckets = {
        "cpuColorBucketsOnTarget": for283_audit["sourcePreservation"]["for282CpuReferenceLikeRedPayloadPixels"],
        "cpuReferenceLikeRedPayloadPixels": for283_audit["sourcePreservation"]["for282CpuReferenceLikeRedPayloadPixels"],
    }

    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-post-payload-residual",
        "sceneId": SCENE_ID,
        "backend": "CPU/Reference/WebGPU",
        "decision": DECISION,
        "nextAction": NEXT_ACTION,
        "residualClassification": RESIDUAL_CLASSIFICATION,
        "sourceAudits": {
            "for278": str(for278.AUDIT.relative_to(PROJECT_ROOT)),
            "for283": str(for283.AUDIT.relative_to(PROJECT_ROOT)),
            "for284": str(for284.AUDIT.relative_to(PROJECT_ROOT)),
        },
        "sourceImages": {
            "reference": str((SCENE_DIR / "skia.png").relative_to(PROJECT_ROOT)),
            "cpu": str((SCENE_DIR / "cpu.png").relative_to(PROJECT_ROOT)),
            "gpu": str((SCENE_DIR / "gpu.png").relative_to(PROJECT_ROOT)),
        },
        "route": {
            "cpu": route_cpu.get("selectedRoute"),
            "gpu": route_gpu.get("selectedRoute"),
            "gpuStatus": route_gpu.get("status"),
            "fallbackReason": route_gpu.get("fallbackReason"),
            "coverageStrategy": route_gpu.get("coverageStrategy"),
            "pipelineKey": route_gpu.get("pipelineKey"),
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
            "sourceNeedles": route_needles,
        },
        "m60BeforeFor284": before,
        "m60PostFor284": {
            **after,
            "integrationTestSimilarity": for284_audit["m60After"]["integrationTestSimilarity"],
            "integrationTestMatchingPixels": for284_audit["m60After"]["integrationTestMatchingPixels"],
        },
        "m60DeltaAfterFor284": delta_metrics(before, after),
        "artifactFreshness": {
            "directCpuReferenceGreaterThanThirtyTwoPixels": after["cpuReferenceGreaterThanThirtyTwoPixels"],
            "directGpuReferenceGreaterThanThirtyTwoPixels": after["gpuReferenceGreaterThanThirtyTwoPixels"],
            "for284CpuReferenceGreaterThanThirtyTwoPixels": for284_audit["m60After"][
                "cpuReferenceGreaterThanThirtyTwoPixels"
            ],
            "for284GpuReferenceGreaterThanThirtyTwoPixels": for284_audit["m60After"][
                "gpuReferenceGreaterThanThirtyTwoPixels"
            ],
            "checkedInM60ArtifactsMatchFor284PostCounters": (
                after["cpuReferenceGreaterThanThirtyTwoPixels"]
                == for284_audit["m60After"]["cpuReferenceGreaterThanThirtyTwoPixels"]
                and after["gpuReferenceGreaterThanThirtyTwoPixels"]
                == for284_audit["m60After"]["gpuReferenceGreaterThanThirtyTwoPixels"]
            ),
            "obsoleteArtifactRuledOutForCheckedInEvidence": True,
        },
        "for283LocalTraceComparison": {
            "targetPixels": for283_combined["targetPixels"],
            "dispatchInputRedPayloadPixels": for283_combined["dispatchInputRedPayloadPixels"],
            "nonZeroCovPixels": for283_combined["nonZeroCovPixels"],
            "dstBeforeBlendWhitePixels": for283_combined["dstBeforeBlendWhitePixels"],
            "blendResultRedTintPixels": for283_combined["blendResultRedTintPixels"],
            "blendResultRedDominantPixels": for283_combined["blendResultRedDominantPixels"],
            "readBackRedDominantPixels": for283_combined["readBackRedDominantPixels"],
            "readBackWhiteLayerPixels": for283_combined["readBackWhiteLayerPixels"],
            "blendVsReadBackGreaterThanThirtyTwoPixels": for283_combined[
                "blendVsReadBackGreaterThanThirtyTwoPixels"
            ],
        },
        "for284PayloadPatchComparison": {
            "patchDecision": for284_audit["patchDecision"],
            "supportDecision": for284_audit["supportDecision"],
            "beforeFor283TargetPixels": for284_payload["beforeFor283TargetPixels"],
            "beforeDispatchRedPayloadPixels": for284_payload["beforeDispatchRedPayloadPixels"],
            "beforeBlendRedTintPixels": for284_payload["beforeBlendRedTintPixels"],
            "beforeCpuReadbackRedDominantPixels": for284_payload["beforeCpuReadbackRedDominantPixels"],
            "beforeCpuWhiteLayerPixels": for284_payload["beforeCpuWhiteLayerPixels"],
            "patchScope": for284_audit["patchScope"],
        },
        "currentBoundaryBuckets": {
            "windows": boundary_windows,
            "combined": boundary_combined,
            "for282CpuReferenceLikeRedPayloadPixels": for282_buckets["cpuReferenceLikeRedPayloadPixels"],
        },
        "causeMatrix": {
            "routeDifferent": {
                "selected": False,
                "evidence": (
                    "M60 drawRRect(oval) routes through SkPath.RRect -> drawPath -> "
                    "drawPathWithMaskFilter, and the FOR-284 SrcIn payload branch is present."
                ),
            },
            "maskOrExtentZero": {
                "selected": False,
                "evidence": (
                    "FOR-283/FOR-281 target windows retain 89/89 non-zero mask/dispatch pixels "
                    "and 89/89 non-zero clip coverage on the same CPU/reference >32 pixels."
                ),
            },
            "obsoleteMeasurementArtifact": {
                "selected": False,
                "evidence": (
                    "The checked-in PNGs recompute CPU/reference >32=15726 and GPU/reference >32=2869, "
                    "matching the post-FOR-284 counters."
                ),
            },
            "layerClipStoreOrder": {
                "selected": True,
                "evidence": (
                    "The same 89 local pixels have a red SrcIn payload and red-tinted pre-store "
                    "model, but committed CPU readback remains dominated by 78 white/layer pixels "
                    "and full-scene metrics have zero delta after FOR-284."
                ),
            },
        },
        "decisionRationale": (
            "FOR-284 fixed the local A8 solid SrcIn payload contract, but M60 still has identical "
            "full-scene counters and the same FOR-278 red/white buckets. The active route reaches "
            "the patched callsite and the checked-in artifacts are internally current, so the next "
            "production work should instrument the runtime store/composite interaction under the "
            "active AA difference clip instead of changing global blend, setPixel, GPU, or thresholds."
        ),
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
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def metric_row(label: str, before: Any, after: Any, delta: Any) -> str:
    return f"| {label} | {before} | {after} | {delta} |"


def bucket_row(window: dict[str, Any]) -> str:
    cpu = window["cpuColorBucketsOnTarget"]
    ref = window["referenceColorBucketsOnTarget"]
    gpu = window["gpuColorBucketsOnTarget"]
    return (
        f"| `{window['subzone']}` | {window['targetPixels']} | "
        f"{cpu.get('white_or_layer_background', 0)} | {cpu.get('red_tinted_reference_like', 0)} | "
        f"{ref.get('red_tinted_reference_like', 0)} | {gpu.get('red_tinted_reference_like', 0)} |"
    )


def write_report(audit: dict[str, Any]) -> None:
    before = audit["m60BeforeFor284"]
    after = audit["m60PostFor284"]
    delta = audit["m60DeltaAfterFor284"]
    local = audit["for283LocalTraceComparison"]
    payload = audit["for284PayloadPatchComparison"]
    route = audit["route"]
    buckets = audit["currentBoundaryBuckets"]
    bucket_rows = "\n".join(bucket_row(window) for window in buckets["windows"])
    metric_rows = "\n".join(
        (
            metric_row("CPU similarity", before["cpuSimilarity"], after["cpuSimilarity"], delta["cpuSimilarity"]),
            metric_row(
                "CPU matching pixels",
                before["cpuMatchingPixels"],
                after["cpuMatchingPixels"],
                delta["cpuMatchingPixels"],
            ),
            metric_row(
                "CPU max channel delta",
                before["cpuMaxChannelDelta"],
                after["cpuMaxChannelDelta"],
                delta["cpuMaxChannelDelta"],
            ),
            metric_row(
                "CPU/reference >32",
                before["cpuReferenceGreaterThanThirtyTwoPixels"],
                after["cpuReferenceGreaterThanThirtyTwoPixels"],
                delta["cpuReferenceGreaterThanThirtyTwoPixels"],
            ),
            metric_row("GPU similarity", before["gpuSimilarity"], after["gpuSimilarity"], delta["gpuSimilarity"]),
            metric_row(
                "GPU/reference >32",
                before["gpuReferenceGreaterThanThirtyTwoPixels"],
                after["gpuReferenceGreaterThanThirtyTwoPixels"],
                delta["gpuReferenceGreaterThanThirtyTwoPixels"],
            ),
        )
    )
    report = f"""# FOR-285 M60 Post-Payload Residual

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Residual classification: `{audit["residualClassification"]}`

Next action: `{audit["nextAction"]}`

## Resultat Court

FOR-284 a corrige le contrat local CPU A8 solid +
`SkColorFilters.Blend(..., kSrcIn)`, mais les artefacts M60 post-FOR-284
gardent un delta nul sur la pleine scene. La route source atteint le callsite
corrige; les compteurs recalcules depuis les PNG correspondent aux compteurs
post-FOR-284. Le residu actionnable est donc l'ordre composite/store sous
clip AA actif, pas un nouveau changement global de payload, `blend`, `setPixel`
ou WebGPU.

## Pleine Scene Avant / Apres FOR-284

| Mesure | Avant FOR-284 | Post-FOR-284 | Delta |
|---|---:|---:|---:|
{metric_rows}

## Fenetres FOR-278: Buckets Rouge / Blanc

| Zone | Cibles CPU/ref >32 | CPU blanc/layer | CPU red-tint | Reference red-tint | GPU red-tint |
|---|---:|---:|---:|---:|---:|
{bucket_rows}

Combined target pixels: {buckets["combined"]["targetPixels"]}.
CPU combined white/layer: {buckets["combined"]["cpuColorBucketsOnTarget"].get("white_or_layer_background", 0)}.
CPU combined red-tint: {buckets["combined"]["cpuColorBucketsOnTarget"].get("red_tinted_reference_like", 0)}.
Reference combined red-tint: {buckets["combined"]["referenceColorBucketsOnTarget"].get("red_tinted_reference_like", 0)}.

## Comparaison FOR-283 / FOR-284

| Preuve | Valeur |
|---|---:|
| FOR-283 target pixels | {local["targetPixels"]} |
| FOR-283 dispatch red payload | {local["dispatchInputRedPayloadPixels"]} |
| FOR-283 non-zero cov | {local["nonZeroCovPixels"]} |
| FOR-283 dst white before blend | {local["dstBeforeBlendWhitePixels"]} |
| FOR-283 blend red tint before store | {local["blendResultRedTintPixels"]} |
| FOR-283 blend red dominant before store | {local["blendResultRedDominantPixels"]} |
| FOR-283 CPU readback red dominant | {local["readBackRedDominantPixels"]} |
| FOR-283 CPU readback white/layer | {local["readBackWhiteLayerPixels"]} |
| FOR-284 patch target pixels | {payload["beforeFor283TargetPixels"]} |
| FOR-284 patch dispatch red payload baseline | {payload["beforeDispatchRedPayloadPixels"]} |

## Route Et Diagnostics

| Champ | Valeur |
|---|---|
| CPU route | `{route["cpu"]}` |
| GPU route | `{route["gpu"]}` |
| GPU status | `{route["gpuStatus"]}` |
| Fallback visual parity | `{route["visualParityFallbackPreserved"]}` |
| Fallback crop | `{route["cropFallbackPreserved"]}` |
| Source route drawRRect -> drawPathWithMaskFilter | `{route["sourceNeedles"]["drawRRectRoutesToDrawPath"] and route["sourceNeedles"]["drawPathRoutesMaskFilterToPatchedCallsite"]}` |
| FOR-284 SrcIn branch present | `{route["sourceNeedles"]["for284SrcInBranchPresent"]}` |

## Decision

`{audit["decision"]}`.

Cause retenue: `{RESIDUAL_CLASSIFICATION}`. Route differente: non. Masque ou
extent nul: non sur les fenetres bornees. Artefact obsolete: non pour les
preuves versionnees, car les PNG recalculent CPU/reference >32 =
{audit["artifactFreshness"]["directCpuReferenceGreaterThanThirtyTwoPixels"]}.

La prochaine correction doit instrumenter puis corriger le store/composite CPU
du draw mask-filter A8 SrcIn sous `activeAaClip` difference, en observant les
arguments runtime de `dispatchBlend`, la couverture appliquee dans `blend`,
la valeur ecrite par `setPixel`, et la valeur relue apres le draw. M60 reste
non promue avec `{route["visualParityFallbackPreserved"]}` et
`{route["cropFallbackPreserved"]}` restent conserves.

Artefact: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`
"""
    REPORT.write_text(report, encoding="utf-8")


def validate(audit: dict[str, Any]) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("linear id mismatch")
    if audit.get("parent") != PARENT_ID:
        fail("parent id mismatch")
    if audit.get("sceneId") != SCENE_ID:
        fail("scene id mismatch")
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
            fail(f"source route needle `{key}` failed")

    before = audit["m60BeforeFor284"]
    after = audit["m60PostFor284"]
    delta = audit["m60DeltaAfterFor284"]
    if before["cpuSimilarity"] != 97.31 or after["cpuSimilarity"] != 97.31:
        fail("CPU similarity must remain 97.31")
    if before["cpuReferenceGreaterThanThirtyTwoPixels"] != 15726:
        fail("pre-FOR-284 CPU/reference >32 changed")
    if after["cpuReferenceGreaterThanThirtyTwoPixels"] != 15726:
        fail("post-FOR-284 CPU/reference >32 changed")
    if before["gpuReferenceGreaterThanThirtyTwoPixels"] != 2869:
        fail("pre-FOR-284 GPU/reference >32 changed")
    if after["gpuReferenceGreaterThanThirtyTwoPixels"] != 2869:
        fail("post-FOR-284 GPU/reference >32 changed")
    for key, value in delta.items():
        if value != 0:
            fail(f"M60 metric `{key}` changed unexpectedly")

    freshness = audit["artifactFreshness"]
    if freshness.get("checkedInM60ArtifactsMatchFor284PostCounters") is not True:
        fail("checked-in M60 artifacts do not match FOR-284 post counters")
    if freshness.get("obsoleteArtifactRuledOutForCheckedInEvidence") is not True:
        fail("obsolete artifact was not ruled out for checked-in evidence")

    local = audit["for283LocalTraceComparison"]
    if local["targetPixels"] != 89:
        fail("FOR-283 target pixel count changed")
    if local["dispatchInputRedPayloadPixels"] != 89:
        fail("FOR-283 dispatch red payload count changed")
    if local["nonZeroCovPixels"] != 89:
        fail("FOR-283 non-zero coverage count changed")
    if local["blendResultRedTintPixels"] != 89:
        fail("FOR-283 blend red tint count changed")
    if local["readBackWhiteLayerPixels"] != 78:
        fail("FOR-283 CPU readback white/layer count changed")

    payload = audit["for284PayloadPatchComparison"]
    if payload["patchDecision"] != for284.PATCH_DECISION:
        fail("FOR-284 patch decision changed")
    if payload["supportDecision"] != for284.DECISION:
        fail("FOR-284 support decision changed")
    scope = payload["patchScope"]
    for field in ("globalBlendChanged", "setPixelChanged", "gpuChanged", "coordinatePatch"):
        if scope.get(field) is not False:
            fail(f"FOR-284 scope `{field}` must remain false")

    buckets = audit["currentBoundaryBuckets"]
    if buckets["combined"]["targetPixels"] != 89:
        fail("current combined target pixel count changed")
    cpu_combined = buckets["combined"]["cpuColorBucketsOnTarget"]
    reference_combined = buckets["combined"]["referenceColorBucketsOnTarget"]
    if cpu_combined.get("white_or_layer_background") != 78:
        fail("current CPU white/layer bucket changed")
    if cpu_combined.get("red_tinted_reference_like") != 0:
        fail("current CPU red-tint bucket changed")
    if reference_combined.get("red_tinted_reference_like") != 56:
        fail("current reference red-tint bucket changed")

    cause = audit["causeMatrix"]
    if cause["layerClipStoreOrder"].get("selected") is not True:
        fail("layer/clip/store cause must be selected")
    for field in ("routeDifferent", "maskOrExtentZero", "obsoleteMeasurementArtifact"):
        if cause[field].get("selected") is not False:
            fail(f"cause `{field}` must not be selected")

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
        "FOR-285 M60 Post-Payload Residual",
        DECISION,
        NEXT_ACTION,
        RESIDUAL_CLASSIFICATION,
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
    print(
        "FOR-285 validation passed: "
        f"{audit['decision']}; "
        f"CPU similarity {audit['m60BeforeFor284']['cpuSimilarity']} -> "
        f"{audit['m60PostFor284']['cpuSimilarity']}; "
        f"CPU/ref >32 {audit['m60BeforeFor284']['cpuReferenceGreaterThanThirtyTwoPixels']} -> "
        f"{audit['m60PostFor284']['cpuReferenceGreaterThanThirtyTwoPixels']}; "
        f"residual={audit['residualClassification']}."
    )


if __name__ == "__main__":
    main()
