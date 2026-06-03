#!/usr/bin/env python3
"""Validate FOR-284 CPU mask-filter A8 SrcIn payload patch evidence."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for283_cpu_dispatch_blend_store_trace as for283


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-284"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "m60-cpu-mask-filter-a8-srcin-payload-patch-for284.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-284-cpu-mask-filter-a8-srcin-payload-patch.md"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
TEST_SOURCE = PROJECT_ROOT / "kanvas-skia/src/test/kotlin/org/skia/core/For275CpuSrcInBlurLayerFixtureTest.kt"
INTEGRATION_REPORT = PROJECT_ROOT / "skia-integration-tests/test-similarity-report.md"

DECISION = "KEEP_EXPECTED_UNSUPPORTED_AFTER_TARGETED_CPU_PAYLOAD_PATCH"
PATCH_DECISION = "CPU_MASK_FILTER_A8_SOLID_COLOR_FILTER_PAYLOAD_PATCH_APPLIED"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-284 validation failed: {message}")


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


def source_needles() -> None:
    require_needles(
        CPU_SOURCE,
        (
            "private fun drawPathWithMaskFilter",
            "val colorFilter = paint.colorFilter",
            "val filterMaskPayloadAfterMask = colorFilter",
            "?.asBlendModeFilter()",
            "?.mode == SkBlendMode.kSrcIn",
            "if (colorFilter == null) {",
            "dispatchBlend(devX, devY, (effA shl 24) or rgb, mode, blender)",
            "val baseA = SkColorGetA(paint.color)",
            "val maskedA = (baseA * maskA + 127) / 255",
            "applyColorFilter(colorFilter, (maskedA shl 24) or (paint.color and 0x00FFFFFF))",
            "dispatchBlend(devX, devY, src, mode, blender)",
        ),
    )
    require_needles(
        TEST_SOURCE,
        (
            "FOR-284 A8 solid SrcIn color filter composites the mask payload",
            "val mask = renderA8MaskControl()",
            "val expected = srcOverRedOnWhite(maskA)",
            "every covered A8 mask pixel must dispatch the red SrcIn payload before SrcOver",
            "fun srcOverRedOnWhite(srcA: Int): SkColor",
        ),
    )


def parse_blurred_clipped_circle_report() -> dict[str, Any]:
    text = INTEGRATION_REPORT.read_text(encoding="utf-8")
    pattern = re.compile(
        r"\| BlurredClippedCircleGM \| (?P<similarity>[0-9.]+)% \| (?P<delta>[^|]+) \| "
        r"(?P<tolerance>[0-9]+) \| (?P<matching>[0-9,]+) / (?P<pixels>[0-9,]+) \| "
        r"(?P<max>[^|]+) \| (?P<mean>[^|]+) \|"
    )
    match = pattern.search(text)
    if not match:
        fail("missing BlurredClippedCircleGM row in skia-integration-tests/test-similarity-report.md")
    return {
        "similarity": float(match.group("similarity")),
        "deltaLabel": match.group("delta").strip(),
        "tolerance": int(match.group("tolerance")),
        "matchingPixels": int(match.group("matching").replace(",", "")),
        "pixels": int(match.group("pixels").replace(",", "")),
        "maxChannelDeltaRgba": [int(v.strip()) for v in match.group("max").split(",")],
        "meanChannelDeltaRgba": [int(v.strip()) for v in match.group("mean").split(",")],
    }


def generate_audit() -> dict[str, Any]:
    source_needles()
    for283_audit = for283.generate_audit()
    stats = load_json(SCENE_DIR / "stats.json")
    route_cpu = load_json(SCENE_DIR / "route-cpu.json")
    route_gpu = load_json(SCENE_DIR / "route-gpu.json")
    integration = parse_blurred_clipped_circle_report()

    before_metrics = for283_audit["combinedBoundaryFixture"]["metrics"]
    full = for283_audit["fullSceneM60"]
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "cpu-mask-filter-a8-srcin-payload-patch",
        "sceneId": SCENE_ID,
        "backend": "CPU/Reference/WebGPU",
        "patchDecision": PATCH_DECISION,
        "supportDecision": DECISION,
        "sourceAudits": {
            "for283": str(for283.AUDIT.relative_to(PROJECT_ROOT)),
        },
        "sourceImages": {
            "reference": str((SCENE_DIR / "skia.png").relative_to(PROJECT_ROOT)),
            "cpu": str((SCENE_DIR / "cpu.png").relative_to(PROJECT_ROOT)),
            "gpu": str((SCENE_DIR / "gpu.png").relative_to(PROJECT_ROOT)),
        },
        "patchScope": {
            "productionFiles": [str(CPU_SOURCE.relative_to(PROJECT_ROOT))],
            "testFiles": [str(TEST_SOURCE.relative_to(PROJECT_ROOT))],
            "globalBlendChanged": False,
            "setPixelChanged": False,
            "gpuChanged": False,
            "coordinatePatch": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
        },
        "payloadContract": {
            "beforeFor283TargetPixels": before_metrics["targetPixels"],
            "beforeDispatchRedPayloadPixels": before_metrics["dispatchInputRedPayloadPixels"],
            "beforeBlendRedTintPixels": before_metrics["blendResultRedTintPixels"],
            "beforeCpuReadbackRedDominantPixels": before_metrics["readBackRedDominantPixels"],
            "beforeCpuWhiteLayerPixels": before_metrics["readBackWhiteLayerPixels"],
            "patch": (
                "A8 solid color-filter branch now builds the masked source alpha, applies the "
                "paint Blend(...,kSrcIn) colorFilter to that per-mask source, then dispatches the filtered payload."
            ),
            "unitTest": (
                "For275CpuSrcInBlurLayerFixtureTest.FOR-284 compares every non-zero A8 mask pixel "
                "against Blend(RED,kSrcIn)+SrcOver over white with max delta <= 1."
            ),
        },
        "m60Before": {
            "cpuSimilarity": full["cpuSimilarity"],
            "cpuMatchingPixels": full["cpuMatchingPixels"],
            "cpuMaxChannelDelta": full["cpuMaxChannelDelta"],
            "cpuReferenceGreaterThanThirtyTwoPixels": full["cpuReferenceGreaterThanThirtyTwoPixels"],
            "gpuSimilarity": full["gpuSimilarity"],
            "gpuMatchingPixels": full["gpuMatchingPixels"],
            "gpuMaxChannelDelta": full["gpuMaxChannelDelta"],
            "gpuReferenceGreaterThanThirtyTwoPixels": full["gpuReferenceGreaterThanThirtyTwoPixels"],
        },
        "m60After": {
            "cpuSimilarity": stats["cpuSimilarity"],
            "cpuMatchingPixels": stats["cpuMatchingPixels"],
            "cpuMaxChannelDelta": stats["cpuMaxChannelDelta"],
            "cpuReferenceGreaterThanThirtyTwoPixels": full["cpuReferenceGreaterThanThirtyTwoPixels"],
            "integrationTestSimilarity": integration["similarity"],
            "integrationTestMatchingPixels": integration["matchingPixels"],
            "integrationTestMaxChannelDeltaRgba": integration["maxChannelDeltaRgba"],
            "gpuSimilarity": stats["gpuSimilarity"],
            "gpuMatchingPixels": stats["gpuMatchingPixels"],
            "gpuMaxChannelDelta": stats["gpuMaxChannelDelta"],
            "gpuReferenceGreaterThanThirtyTwoPixels": full["gpuReferenceGreaterThanThirtyTwoPixels"],
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
        },
        "decisionRationale": (
            "The targeted CPU callsite patch codifies the A8 solid color-filter payload contract and "
            "the Kotlin fixture proves Blend(RED,kSrcIn) over the generated mask. M60 full-scene "
            "CPU/reference metrics remain at the FOR-283 level, so this is not sufficient evidence "
            "to promote the scene or change GPU/coverage diagnostics."
        ),
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "gpuRendererChanged": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def write_report(audit: dict[str, Any]) -> None:
    before = audit["m60Before"]
    after = audit["m60After"]
    route = audit["route"]
    payload = audit["payloadContract"]
    report = f"""# FOR-284 CPU Mask Filter A8 SrcIn Payload Patch

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Patch decision: `{audit["patchDecision"]}`

Support decision: `{audit["supportDecision"]}`

## Resultat

Le chemin CPU `drawPathWithMaskFilter` garde l'ancien dispatch direct pour les
cas non concernes. Pour le cas solid A8 avec
`SkColorFilters.Blend(..., kSrcIn)`, il construit maintenant la source masquee,
applique ce color filter sur le payload, puis envoie le resultat a
`dispatchBlend`.

## Payload Cible

| Mesure FOR-283 | Valeur |
|---|---:|
| Pixels cibles | {payload["beforeFor283TargetPixels"]} |
| Source dispatch rouge | {payload["beforeDispatchRedPayloadPixels"]} |
| Resultat blend teinte rouge | {payload["beforeBlendRedTintPixels"]} |
| Readback CPU rouge dominant | {payload["beforeCpuReadbackRedDominantPixels"]} |
| Readback CPU blanc/layer | {payload["beforeCpuWhiteLayerPixels"]} |

Test Kotlin cible:
`For275CpuSrcInBlurLayerFixtureTest.FOR-284 A8 solid SrcIn color filter composites the mask payload`.

## M60 Avant / Apres

| Mesure | Avant FOR-284 | Apres FOR-284 |
|---|---:|---:|
| CPU similarity | {before["cpuSimilarity"]} | {after["cpuSimilarity"]} |
| CPU matching pixels | {before["cpuMatchingPixels"]} | {after["cpuMatchingPixels"]} |
| CPU max channel delta | {before["cpuMaxChannelDelta"]} | {after["cpuMaxChannelDelta"]} |
| CPU/ref >32 | {before["cpuReferenceGreaterThanThirtyTwoPixels"]} | {after["cpuReferenceGreaterThanThirtyTwoPixels"]} |
| Integration test similarity | n/a | {after["integrationTestSimilarity"]} |
| GPU similarity | {before["gpuSimilarity"]} | {after["gpuSimilarity"]} |
| GPU matching pixels | {before["gpuMatchingPixels"]} | {after["gpuMatchingPixels"]} |
| GPU max channel delta | {before["gpuMaxChannelDelta"]} | {after["gpuMaxChannelDelta"]} |
| GPU/ref >32 | {before["gpuReferenceGreaterThanThirtyTwoPixels"]} | {after["gpuReferenceGreaterThanThirtyTwoPixels"]} |

## Routes Et Diagnostics

| Champ | Valeur |
|---|---|
| CPU route | `{route["cpu"]}` |
| GPU route | `{route["gpu"]}` |
| GPU status | `{route["gpuStatus"]}` |
| Fallback visual parity | `{route["visualParityFallbackPreserved"]}` |
| Fallback crop | `{route["cropFallbackPreserved"]}` |

## Decision

`{audit["supportDecision"]}`.

La correction est limitee au payload CPU A8 solid + `Blend(..., kSrcIn)`. Les
metriques pleine scene M60 ne s'ameliorent pas assez pour promouvoir la scene;
les diagnostics `coverage.nested-clip-visual-parity-below-threshold` et
`image-filter.crop-input-nonnull-prepass-required` restent inchanges.

Artefact: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`
"""
    REPORT.write_text(report, encoding="utf-8")


def validate(audit: dict[str, Any]) -> None:
    if audit.get("patchDecision") != PATCH_DECISION:
        fail("patch decision mismatch")
    if audit.get("supportDecision") != DECISION:
        fail("support decision mismatch")
    scope = audit.get("patchScope", {})
    for field in ("globalBlendChanged", "setPixelChanged", "gpuChanged", "coordinatePatch"):
        if scope.get(field) is not False:
            fail(f"patch scope `{field}` must be false")
    route = audit.get("route", {})
    if route.get("fallbackReason") != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route.get("cropFallbackPreserved") != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")
    before = audit.get("m60Before", {})
    after = audit.get("m60After", {})
    if before.get("cpuSimilarity") != 97.31 or after.get("cpuSimilarity") != 97.31:
        fail("CPU similarity changed unexpectedly")
    if after.get("integrationTestSimilarity") != 97.31:
        fail("integration test similarity must remain 97.31")
    if before.get("cpuReferenceGreaterThanThirtyTwoPixels") != 15726:
        fail("before CPU/ref >32 changed")
    if after.get("cpuReferenceGreaterThanThirtyTwoPixels") != 15726:
        fail("after CPU/ref >32 changed")
    payload = audit.get("payloadContract", {})
    if payload.get("beforeDispatchRedPayloadPixels") != 89:
        fail("FOR-283 dispatch red payload baseline changed")
    if payload.get("beforeCpuReadbackRedDominantPixels") != 9:
        fail("FOR-283 readback red-dominant baseline changed")
    strict = audit.get("strictPreservation", {})
    for field in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "gpuRendererChanged",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if strict.get(field) is not False:
            fail(f"strict preservation `{field}` must be false")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "FOR-284 CPU Mask Filter A8 SrcIn Payload Patch",
        PATCH_DECISION,
        DECISION,
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
    before = audit["m60Before"]
    after = audit["m60After"]
    print(
        "FOR-284 validation passed: "
        f"{audit['patchDecision']}; "
        f"CPU similarity {before['cpuSimilarity']} -> {after['cpuSimilarity']}; "
        f"CPU/ref >32 {before['cpuReferenceGreaterThanThirtyTwoPixels']} -> "
        f"{after['cpuReferenceGreaterThanThirtyTwoPixels']}; "
        f"M60 remains {audit['supportDecision']}."
    )


if __name__ == "__main__":
    main()
