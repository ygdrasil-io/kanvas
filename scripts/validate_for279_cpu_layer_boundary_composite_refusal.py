#!/usr/bin/env python3
"""Generate and validate FOR-279 CPU boundary composite refusal evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for278_m60_boundary_layer_composite_fixture as for278


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-279"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "m60-cpu-layer-boundary-composite-for279.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-279-cpu-layer-boundary-composite.md"
SOURCE_FOR277 = SCENE_DIR / "m60-post-for276-cpu-residual-audit-for277.json"
SOURCE_FOR278 = SCENE_DIR / "m60-boundary-layer-composite-fixture-for278.json"
STATS = SCENE_DIR / "stats.json"
GM_SOURCE = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/BlurredClippedCircleGM.kt"
CANVAS_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
GPU_SOURCE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"

REFUSAL = "REFUSE_CORRECTION_PENDING_DEEPER_LAYER_COMPOSITE_MODEL"
NEXT_ACTION = "TARGET_CPU_AA_DIFFERENCE_CLIP_COVERAGE_EDGE_MODEL"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-279 validation failed: {message}")


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


def comparison_by_name(audit: dict[str, Any], name: str) -> dict[str, Any]:
    for item in audit.get("comparisons", []):
        if item.get("comparison") == name:
            return item
    fail(f"missing comparison `{name}`")


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
            "public open fun clipRRect(rrect: SkRRect, op: SkClipOp, doAntiAlias: Boolean = false)",
            "SkClipOp.kDifference -> clipPath(SkPath.Rect(rect), SkClipOp.kDifference, doAntiAlias)",
            "private fun clipPathDifference",
            "combined.op(pathAac, SkRegion.Op.kDifference)",
        ),
    )
    require_needles(
        CPU_SOURCE,
        (
            "private fun drawPathWithMaskFilter",
            "val preserveOffClipMaskSource = activeAaClip != null",
            "val compositeX0 = maxOf(0, clip.left - ml)",
            "if (activeAaClip != null) cov = clipCoverage(x, y)",
            "private fun dispatchBlend(",
            "dispatchBlend(devX, devY",
            "override fun compositeFrom",
        ),
    )
    require_needles(
        GPU_SOURCE,
        (
            "CLIP_KIND_RRECT_DIFFERENCE",
            "drawPathWithBlurMaskFilterIfApplicable",
            "solidBlurPaintColor(paint)",
            "Blend(colour, kSrcIn)",
        ),
    )


def before_after_metrics(source_for277: dict[str, Any], source_for278: dict[str, Any], stats: dict[str, Any]) -> dict[str, Any]:
    full_cpu = comparison_by_name(source_for277, "cpu_vs_reference")
    full_gpu = comparison_by_name(source_for277, "gpu_vs_reference")
    window_cpu = comparison_by_name(source_for278["combinedBoundaryFixture"], "cpu_vs_reference")
    window_gpu = comparison_by_name(source_for278["combinedBoundaryFixture"], "gpu_vs_reference")
    classification = source_for278["classification"]
    return {
        "rendererPatchApplied": False,
        "reason": (
            "The named layer-composite correction is refused because the source GM uses save/restore, "
            "not saveLayer. The observed white/background pixels therefore flow through the CPU AA "
            "difference-clip mask-filter draw path, and a targeted layer composite patch would not be causal."
        ),
        "for278BoundaryWindows": {
            "before": {
                "maskPixels": source_for278["combinedBoundaryFixture"]["maskPixels"],
                "cpuReferenceGreaterThanThirtyTwoPixels": window_cpu["greaterThanThirtyTwoPixels"],
                "cpuWhiteLayerPixels": classification["combinedCpuActualWhiteLayerPixels"],
                "cpuWhiteLayerShare": classification["combinedCpuActualWhiteLayerShare"],
                "cpuAlphaGreaterThanThirtyTwoPixels": classification[
                    "combinedCpuReferenceAlphaGreaterThanThirtyTwoPixels"
                ],
                "gpuReferenceGreaterThanThirtyTwoPixels": window_gpu["greaterThanThirtyTwoPixels"],
                "maxChannelDelta": window_cpu["maxChannelDelta"],
            },
            "afterNoRendererPatch": {
                "maskPixels": source_for278["combinedBoundaryFixture"]["maskPixels"],
                "cpuReferenceGreaterThanThirtyTwoPixels": window_cpu["greaterThanThirtyTwoPixels"],
                "cpuWhiteLayerPixels": classification["combinedCpuActualWhiteLayerPixels"],
                "cpuWhiteLayerShare": classification["combinedCpuActualWhiteLayerShare"],
                "cpuAlphaGreaterThanThirtyTwoPixels": classification[
                    "combinedCpuReferenceAlphaGreaterThanThirtyTwoPixels"
                ],
                "gpuReferenceGreaterThanThirtyTwoPixels": window_gpu["greaterThanThirtyTwoPixels"],
                "maxChannelDelta": window_cpu["maxChannelDelta"],
            },
            "delta": {
                "cpuReferenceGreaterThanThirtyTwoPixels": 0,
                "cpuWhiteLayerPixels": 0,
                "maxChannelDelta": 0,
            },
        },
        "fullSceneM60": {
            "before": {
                "cpuSimilarity": stats["cpuSimilarity"],
                "cpuMatchingPixels": stats["cpuMatchingPixels"],
                "cpuMaxChannelDelta": stats["cpuMaxChannelDelta"],
                "cpuReferenceGreaterThanThirtyTwoPixels": full_cpu["greaterThanThirtyTwoPixels"],
                "gpuSimilarity": stats["gpuSimilarity"],
                "gpuMatchingPixels": stats["gpuMatchingPixels"],
                "gpuMaxChannelDelta": stats["gpuMaxChannelDelta"],
                "gpuReferenceGreaterThanThirtyTwoPixels": full_gpu["greaterThanThirtyTwoPixels"],
            },
            "afterNoRendererPatch": {
                "cpuSimilarity": stats["cpuSimilarity"],
                "cpuMatchingPixels": stats["cpuMatchingPixels"],
                "cpuMaxChannelDelta": stats["cpuMaxChannelDelta"],
                "cpuReferenceGreaterThanThirtyTwoPixels": full_cpu["greaterThanThirtyTwoPixels"],
                "gpuSimilarity": stats["gpuSimilarity"],
                "gpuMatchingPixels": stats["gpuMatchingPixels"],
                "gpuMaxChannelDelta": stats["gpuMaxChannelDelta"],
                "gpuReferenceGreaterThanThirtyTwoPixels": full_gpu["greaterThanThirtyTwoPixels"],
            },
            "delta": {
                "cpuReferenceGreaterThanThirtyTwoPixels": 0,
                "cpuMatchingPixels": 0,
                "cpuMaxChannelDelta": 0,
            },
        },
    }


def generate_audit() -> dict[str, Any]:
    validate_sources()
    source_for277 = load_json(SOURCE_FOR277)
    source_for278 = load_json(SOURCE_FOR278)
    regenerated_for278 = for278.generate_audit()
    stats = load_json(STATS)
    route_cpu = load_json(SCENE_DIR / "route-cpu.json")
    route_gpu = load_json(SCENE_DIR / "route-gpu.json")
    metrics = before_after_metrics(source_for277, source_for278, stats)
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "cpu-layer-boundary-composite-refusal",
        "sceneId": SCENE_ID,
        "backend": "CPU/Reference/WebGPU",
        "sourceAudits": {
            "for277": str(SOURCE_FOR277.relative_to(PROJECT_ROOT)),
            "for278": str(SOURCE_FOR278.relative_to(PROJECT_ROOT)),
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
        "decision": REFUSAL,
        "decisionRationale": (
            "FOR-279 refuses a production CPU correction in this ticket. The isolated FOR-278 pixels "
            "are real, but the GM does not exercise saveLayer composite. FOR-275 already showed that "
            "a minimized saveLayer-vs-direct fixture is identical, and FOR-276 moved the bounded "
            "mask-source ordering fixture without moving full-scene M60 counters. The next safe "
            "implementation needs a deeper AA difference-clip coverage model, not a special "
            "background/layer patch."
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
        "responsibleCpuPath": [
            {
                "file": str(GM_SOURCE.relative_to(PROJECT_ROOT)),
                "function": "BlurredClippedCircleGM.onDraw",
                "role": "scene issues save/clipRRect(kDifference)/drawRRect; no saveLayer composite is in the draw stack",
            },
            {
                "file": str(CANVAS_SOURCE.relative_to(PROJECT_ROOT)),
                "function": "SkCanvas.clipPathDifference",
                "role": "builds the SkAAClip difference mask used by the following draw",
            },
            {
                "file": str(CPU_SOURCE.relative_to(PROJECT_ROOT)),
                "function": "SkBitmapDevice.drawPathWithMaskFilter",
                "role": "generates blur source, then composites through final clipCoverage and blend dispatch",
            },
        ],
        "excludedPath": {
            "file": str(CPU_SOURCE.relative_to(PROJECT_ROOT)),
            "function": "SkBitmapDevice.compositeFrom",
            "reason": "not exercised by BlurredClippedCircleGM because the GM uses save/restore, not saveLayer/restore",
        },
        "for278RegeneratedMatchesSource": regenerated_for278["classification"] == source_for278["classification"],
        "metrics": metrics,
        "refusalEvidence": {
            "for275SaveLayerVsDirectGreaterThanZeroPixels": 0,
            "for276FullSceneCpuReferenceGt32Delta": 0,
            "for278BoundaryCpuReferenceGt32AfterNoPatch": metrics["for278BoundaryWindows"]["afterNoRendererPatch"][
                "cpuReferenceGreaterThanThirtyTwoPixels"
            ],
            "for278BoundaryCpuWhiteLayerAfterNoPatch": metrics["for278BoundaryWindows"]["afterNoRendererPatch"][
                "cpuWhiteLayerPixels"
            ],
            "fullSceneCpuReferenceGt32AfterNoPatch": metrics["fullSceneM60"]["afterNoRendererPatch"][
                "cpuReferenceGreaterThanThirtyTwoPixels"
            ],
            "stableDiagnostic": REFUSAL,
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


def write_report(audit: dict[str, Any]) -> None:
    metrics = audit["metrics"]
    window_before = metrics["for278BoundaryWindows"]["before"]
    window_after = metrics["for278BoundaryWindows"]["afterNoRendererPatch"]
    full_before = metrics["fullSceneM60"]["before"]
    full_after = metrics["fullSceneM60"]["afterNoRendererPatch"]
    route = audit["route"]
    report = f"""# FOR-279 CPU Layer Boundary Composite

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

Support scene: `{audit["supportDecision"]}`

FOR-279 audite le levier `TARGET_CPU_LAYER_BACKGROUND_COMPOSITE_AROUND_DIFFERENCE_CLIP_BOUNDARY`
apres FOR-278. Aucun renderer n'est modifie: la correction est refusee parce
que la scene source n'utilise pas `saveLayer`. Le chemin causal observe est le
draw CPU `clipRRect(kDifference)` + `drawRRect` avec `SkBlurMaskFilter` et
`Blend(RED, kSrcIn)`, pas `SkBitmapDevice.compositeFrom`.

## Compteurs FOR-278

| Mesure | Avant | Apres sans patch renderer | Delta |
|---|---:|---:|---:|
| Pixels fixture | {window_before["maskPixels"]} | {window_after["maskPixels"]} | 0 |
| CPU/reference >32 | {window_before["cpuReferenceGreaterThanThirtyTwoPixels"]} | {window_after["cpuReferenceGreaterThanThirtyTwoPixels"]} | 0 |
| CPU blanc/fond sur >32 | {window_before["cpuWhiteLayerPixels"]} | {window_after["cpuWhiteLayerPixels"]} | 0 |
| CPU blanc/fond share | {window_before["cpuWhiteLayerShare"]}% | {window_after["cpuWhiteLayerShare"]}% | 0 |
| CPU alpha >32 | {window_before["cpuAlphaGreaterThanThirtyTwoPixels"]} | {window_after["cpuAlphaGreaterThanThirtyTwoPixels"]} | 0 |
| GPU/reference >32 | {window_before["gpuReferenceGreaterThanThirtyTwoPixels"]} | {window_after["gpuReferenceGreaterThanThirtyTwoPixels"]} | 0 |

## Compteurs M60 Pleine Scene

| Mesure | Avant | Apres sans patch renderer | Delta |
|---|---:|---:|---:|
| CPU/reference similarity | {full_before["cpuSimilarity"]}% | {full_after["cpuSimilarity"]}% | 0 |
| CPU matching pixels | {full_before["cpuMatchingPixels"]} | {full_after["cpuMatchingPixels"]} | 0 |
| CPU max channel delta | {full_before["cpuMaxChannelDelta"]} | {full_after["cpuMaxChannelDelta"]} | 0 |
| CPU/reference >32 | {full_before["cpuReferenceGreaterThanThirtyTwoPixels"]} | {full_after["cpuReferenceGreaterThanThirtyTwoPixels"]} | 0 |
| GPU/reference similarity | {full_before["gpuSimilarity"]}% | {full_after["gpuSimilarity"]}% | 0 |
| GPU/reference >32 | {full_before["gpuReferenceGreaterThanThirtyTwoPixels"]} | {full_after["gpuReferenceGreaterThanThirtyTwoPixels"]} | 0 |

## Chemin CPU Identifie

1. `BlurredClippedCircleGM.onDraw`: `save()` puis `clipRRect(kDifference)` puis `drawRRect`.
2. `SkCanvas.clipPathDifference`: construit le `SkAAClip` difference.
3. `SkBitmapDevice.drawPathWithMaskFilter`: genere le masque de flou puis compose via `clipCoverage`.

Le chemin exclu est `SkBitmapDevice.compositeFrom`: il correspond au retour de
`saveLayer`, absent de cette GM. Corriger ce composite serait donc non causal
pour les pixels FOR-278.

## Refus

`{audit["decision"]}`.

Le refus est stable: FOR-275 montre `0` pixel different entre direct et
`saveLayer` dans la fixture minimale; FOR-276 recupere le halo dans une fixture
AA bornee mais laisse M60 inchange; FOR-278 reste a
`{window_after["cpuReferenceGreaterThanThirtyTwoPixels"]}` pixels CPU/reference
>32 et `{window_after["cpuWhiteLayerPixels"]}` pixels blanc/fond dans les
fenetres ciblees.

## Decision De Support

M60 reste `expected-unsupported` avec fallback `{route["fallbackReason"]}`.
`{route["cropFallbackPreserved"]}` est preserve. Aucun seuil n'est affaibli,
aucun support large clip-stack/readback n'est ajoute, et aucun chemin
Ganesh/Graphite/SkSL n'est introduit.

## Prochaine Action

`{audit["nextAction"]}`.

Le prochain ticket doit auditer le modele `SkAAClip` difference autour du bord
AA: coverage par pixel, convention de sample, et composition avec le masque
floute. C'est le point causal restant avant toute correction CPU pleine scene.

## Validation

```text
rtk python3 scripts/validate_for279_cpu_layer_boundary_composite_refusal.py
rtk python3 scripts/validate_for278_m60_boundary_layer_composite_fixture.py
rtk python3 scripts/validate_for277_m60_post_for276_cpu_residual_audit.py
rtk python3 scripts/validate_for276_cpu_mask_filter_clip_order.py
rtk python3 scripts/validate_for275_cpu_srcin_blur_layer_fixture.py
rtk python3 scripts/validate_for274_nested_rrect_cpu_reference_layer_audit.py
rtk python3 scripts/validate_for273_webgpu_solid_blur_srcin_fold.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
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
    if audit.get("probe") != "cpu-layer-boundary-composite-refusal":
        fail("probe mismatch")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision must remain expected-unsupported")
    if audit.get("decision") != REFUSAL:
        fail("refusal decision mismatch")
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
    if audit.get("for278RegeneratedMatchesSource") is not True:
        fail("regenerated FOR-278 classification does not match source")

    excluded = audit.get("excludedPath", {})
    if excluded.get("function") != "SkBitmapDevice.compositeFrom":
        fail("excluded path must name SkBitmapDevice.compositeFrom")
    if "not exercised" not in excluded.get("reason", ""):
        fail("excluded path reason must document non-execution")

    metrics = audit.get("metrics", {})
    if metrics.get("rendererPatchApplied") is not False:
        fail("FOR-279 must not claim a renderer patch")
    windows = metrics.get("for278BoundaryWindows", {})
    before = windows.get("before", {})
    after = windows.get("afterNoRendererPatch", {})
    delta = windows.get("delta", {})
    if before.get("cpuReferenceGreaterThanThirtyTwoPixels") != 89:
        fail("FOR-278 before CPU/reference >32 changed")
    if before.get("cpuWhiteLayerPixels") != 78:
        fail("FOR-278 before white/layer changed")
    if after != before:
        fail("after-no-patch FOR-278 metrics must match before metrics")
    if delta.get("cpuReferenceGreaterThanThirtyTwoPixels") != 0:
        fail("FOR-278 CPU/reference delta must be zero")
    if delta.get("cpuWhiteLayerPixels") != 0:
        fail("FOR-278 white/layer delta must be zero")

    full = metrics.get("fullSceneM60", {})
    full_before = full.get("before", {})
    full_after = full.get("afterNoRendererPatch", {})
    full_delta = full.get("delta", {})
    if full_before.get("cpuSimilarity") != 97.31:
        fail("M60 CPU similarity changed")
    if full_before.get("gpuSimilarity") != 98.48:
        fail("M60 GPU similarity changed")
    if full_before.get("cpuReferenceGreaterThanThirtyTwoPixels") != 15726:
        fail("M60 CPU/reference >32 changed")
    if full_after != full_before:
        fail("after-no-patch full-scene metrics must match before metrics")
    if full_delta.get("cpuReferenceGreaterThanThirtyTwoPixels") != 0:
        fail("full-scene CPU/reference delta must be zero")

    refusal = audit.get("refusalEvidence", {})
    if refusal.get("stableDiagnostic") != REFUSAL:
        fail("stable diagnostic mismatch")
    if refusal.get("for275SaveLayerVsDirectGreaterThanZeroPixels") != 0:
        fail("FOR-275 saveLayer/direct evidence changed")
    if refusal.get("for276FullSceneCpuReferenceGt32Delta") != 0:
        fail("FOR-276 full-scene delta evidence changed")

    if audit.get("nextAction") != NEXT_ACTION:
        fail("next action mismatch")
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
        "FOR-279 CPU Layer Boundary Composite",
        REFUSAL,
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
    windows = audit["metrics"]["for278BoundaryWindows"]["afterNoRendererPatch"]
    full = audit["metrics"]["fullSceneM60"]["afterNoRendererPatch"]
    print(
        "FOR-279 validation passed: "
        f"{audit['decision']}; "
        f"FOR-278 CPU/reference >32 stays {windows['cpuReferenceGreaterThanThirtyTwoPixels']} "
        f"with {windows['cpuWhiteLayerPixels']} white/layer pixels; "
        f"M60 CPU/reference stays {full['cpuReferenceGreaterThanThirtyTwoPixels']} >32 "
        f"at {full['cpuSimilarity']}%."
    )


if __name__ == "__main__":
    main()
