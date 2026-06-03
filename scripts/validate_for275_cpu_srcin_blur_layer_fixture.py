#!/usr/bin/env python3
"""Validate FOR-275 minimized CPU SrcIn blur layer/mask fixture evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-275"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "cpu-srcin-blur-layer-fixture-for275.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-275-cpu-srcin-blur-layer-fixture.md"
SOURCE_FOR274 = SCENE_DIR / "nested-rrect-cpu-reference-layer-mask-audit-for274.json"
TEST_SOURCE = PROJECT_ROOT / "kanvas-skia/src/test/kotlin/org/skia/core/For275CpuSrcInBlurLayerFixtureTest.kt"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
GPU_SOURCE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-275 validation failed: {message}")


def load_json(path: Path) -> dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
    except FileNotFoundError:
        fail(f"missing {path.relative_to(PROJECT_ROOT)}")
    if not isinstance(data, dict):
        fail(f"{path.relative_to(PROJECT_ROOT)} must contain a JSON object")
    return data


def require_needles(path: Path, needles: tuple[str, ...]) -> None:
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")


def validate_sources() -> None:
    require_needles(
        TEST_SOURCE,
        (
            "SkBlurMaskFilter.Make(SkBlurStyle.kNormal, SIGMA)",
            "SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)",
            "canvas.clipRRect(INNER_OVAL, SkClipOp.kDifference, doAntiAlias = true)",
            "canvas.saveLayer(null, null)",
            "unclippedBlurControl",
            "MASK_EXTENT_OR_ACTIVE_CLIP_TRUNCATION_REPRODUCED_LAYER_BACKGROUND_NOT_REPRODUCED",
        ),
    )
    require_needles(
        CPU_SOURCE,
        (
            "private fun drawPathWithMaskFilter",
            "transformPaintColor(applyColorFilter(paint.colorFilter, paint.color))",
            "val rgb = effectiveColor and 0x00FFFFFF",
        ),
    )
    require_needles(
        GPU_SOURCE,
        (
            "private fun drawPathWithBlurMaskFilterIfApplicable",
            "solidBlurPaintColor(paint)",
            "Blend(colour, kSrcIn)",
        ),
    )


def validate_for274_source() -> dict[str, Any]:
    source = load_json(SOURCE_FOR274)
    if source.get("linear") != "FOR-274":
        fail("source audit must be FOR-274")
    if source.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("FOR-274 source must keep expected-unsupported")
    route = source.get("route", {})
    if route.get("gpuStatus") != "expected-unsupported":
        fail("FOR-274 GPU status changed")
    if route.get("fallbackReason") != for269.FALLBACK_REASON:
        fail("FOR-274 fallback reason changed")
    reported = source.get("reportedStats", {})
    if reported.get("cpuSimilarity") != 97.31:
        fail("FOR-274 CPU/reference similarity changed")
    if reported.get("gpuSimilarity") != 98.48:
        fail("FOR-274 GPU/reference similarity changed")
    if source.get("dominantHypothesis") != "CPU_LAYER_BACKGROUND_RETENTION_DOMINANT_MASK_EXTENT_SECONDARY":
        fail("FOR-274 dominant hypothesis changed")
    return source


def validate(audit: dict[str, Any]) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("linear id mismatch")
    if audit.get("parent") != PARENT_ID:
        fail("parent id mismatch")
    if audit.get("sceneId") != SCENE_ID:
        fail("scene id mismatch")
    if audit.get("probe") != "cpu-srcin-blur-layer-fixture":
        fail("probe mismatch")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision must remain expected-unsupported")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")

    route = audit.get("routePreservation", {})
    if route.get("gpuStatus") != "expected-unsupported":
        fail("expected-unsupported status not preserved")
    if route.get("fallbackReason") != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route.get("cropFallbackPreserved") != for269.CROP_FALLBACK_REASON:
        fail("crop fallback not preserved")

    fixture = audit.get("fixture", {})
    if fixture.get("width") != 96 or fixture.get("height") != 72:
        fail("fixture dimensions changed")
    if fixture.get("sigma") != 1.366025:
        fail("fixture sigma changed")
    if fixture.get("paintChain") != "SkBlurMaskFilter(kNormal) + SkColorFilters.Blend(RED, kSrcIn)":
        fail("fixture paint chain changed")
    if fixture.get("variants") != [
        "directDifferenceClip",
        "saveLayerDifferenceClip",
        "unclippedBlurControl",
    ]:
        fail("fixture variants changed")

    layer = audit.get("layerComparison", {})
    if layer.get("greaterThanZeroPixels") != 0:
        fail("saveLayer fixture differs from direct CPU fixture")
    if layer.get("greaterThanThirtyTwoPixels") != 0:
        fail("saveLayer fixture has >32 deltas")
    if layer.get("maxChannelDelta") != 0:
        fail("saveLayer fixture max delta changed")
    if audit.get("layerIntroducedWhitePixels") != 0:
        fail("saveLayer introduced white/fond pixels")

    mask = audit.get("maskExtent", {})
    if mask.get("unclippedRedBlurSupportPixels", 0) <= 0:
        fail("unclipped blur support missing")
    if mask.get("lostRedBlurPixels", 0) <= 0:
        fail("active difference clip did not reproduce lost red blur pixels")
    if mask.get("whiteLayerLostPixels") != mask.get("lostRedBlurPixels"):
        fail("lost blur pixels must all classify as white/fond in this fixture")
    if mask.get("whiteLayerShareOfLostPixels") != 100.0:
        fail("white/fond lost share changed")
    if mask.get("directRedPixelsInsideUnclippedSupport", 0) <= 0:
        fail("direct fixture lost all red support, fixture is too broad")
    samples = mask.get("samples", [])
    if not isinstance(samples, list) or len(samples) < 4:
        fail("signed RGB samples missing")
    first = samples[0]
    if first.get("directMinusUnclippedSignedRgba") != [0, 255, 255, 0]:
        fail("first sample must show white/fond RGB retention with unchanged alpha")
    if first.get("layeredMinusDirectSignedRgba") != [0, 0, 0, 0]:
        fail("first sample must show no layer-vs-direct delta")

    if audit.get("dominantFixtureHypothesis") != (
        "MASK_EXTENT_OR_ACTIVE_CLIP_TRUNCATION_REPRODUCED_LAYER_BACKGROUND_NOT_REPRODUCED"
    ):
        fail("dominant fixture hypothesis changed")
    if audit.get("nextAction") != (
        "CPU_LOCAL_MASK_FILTER_CLIP_ORDER_AUDIT_OR_BOUNDED_FIXTURE_CORRECTION_BEFORE_M60_PROMOTION"
    ):
        fail("next action changed")

    strict = audit.get("strictPreservation", {})
    for key in (
        "productionRendererChanged",
        "cpuRendererChanged",
        "gpuRendererChanged",
        "supportPromotionChanged",
        "supportThresholdChanged",
        "fallbackOrReadbackAdded",
        "wideClipStackSupportAdded",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if strict.get(key) is not False:
            fail(f"strict preservation `{key}` must be false")


def write_report(audit: dict[str, Any], source_for274: dict[str, Any]) -> None:
    layer = audit["layerComparison"]
    mask = audit["maskExtent"]
    first = mask["samples"][0]
    route = audit["routePreservation"]
    source_stats = source_for274["reportedStats"]
    report = f"""# FOR-275 CPU SrcIn Blur Layer Fixture

Linear: `{LINEAR_ID}`

Scene cible: `{SCENE_ID}`

Decision: `{audit["supportDecision"]}`

FOR-275 ajoute une fixture CPU minimale pour `SkBlurMaskFilter(kNormal)` +
`SkColorFilters.Blend(RED, kSrcIn)`. Le renderer n'est pas modifie. La scene
M60 reste `expected-unsupported` avec fallback `{route["fallbackReason"]}`:
CPU/reference `{source_stats["cpuSimilarity"]}` et GPU/reference
`{source_stats["gpuSimilarity"]}` restent sous le seuil strict
`{audit["supportThreshold"]}`.

## Fixture

| Element | Valeur |
|---|---:|
| Dimensions | {audit["fixture"]["width"]} x {audit["fixture"]["height"]} |
| Sigma | {audit["fixture"]["sigma"]} |
| Support rouge blur non clippe | {mask["unclippedRedBlurSupportPixels"]} |
| Support rouge conserve sous clip difference | {mask["directRedPixelsInsideUnclippedSupport"]} |
| Pixels rouges perdus vers blanc/fond | {mask["lostRedBlurPixels"]} |
| Part blanc/fond des pixels perdus | {mask["whiteLayerShareOfLostPixels"]}% |

## Separation Des Hypotheses

| Hypothese | Mesure | Verdict |
|---|---:|---|
| `saveLayer` / retention de fond | {layer["greaterThanZeroPixels"]} pixel different du rendu direct | Non reproduit dans la fixture minimale |
| Etendue masque/flou sous clip actif | {mask["lostRedBlurPixels"]} pixels rouges perdus vers blanc/fond | Reproduit |
| Blanc introduit seulement par layer | {audit["layerIntroducedWhitePixels"]} pixel | Refuse |

## Sample Signe

| Pixel | Unclipped RGBA | Direct RGBA | Layered RGBA | Direct-unclipped | Layered-direct |
|---|---|---|---|---|---|
| {first["x"]},{first["y"]} | `{first["unclippedBlurControlRgba"]}` | `{first["directDifferenceClipRgba"]}` | `{first["saveLayerDifferenceClipRgba"]}` | `{first["directMinusUnclippedSignedRgba"]}` | `{first["layeredMinusDirectSignedRgba"]}` |

## Conclusion

Hypothese dominante fixture:
`{audit["dominantFixtureHypothesis"]}`.

Prochaine action: auditer ou corriger de facon bornee l'ordre CPU
mask-filter/clip pour `SkBlurMaskFilter(kNormal)` avant toute promotion M60.
Ne pas promouvoir la scene tant que CPU/reference et GPU/reference ne prouvent
pas simultanement `{audit["supportThreshold"]}` avec routes et stats.

## Preservation

- `expected-unsupported` conserve.
- Fallback `{route["fallbackReason"]}` conserve.
- Fallback `{route["cropFallbackPreserved"]}` conserve.
- Aucun readback/fallback, support large clip-stack, Ganesh, Graphite ou SkSL
  compiler ajoute.

## Validation

```text
rtk ./gradlew --no-daemon :kanvas-skia:test --tests org.skia.core.For275CpuSrcInBlurLayerFixtureTest
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


def main() -> None:
    validate_sources()
    source_for274 = validate_for274_source()
    audit = load_json(AUDIT)
    validate(audit)
    write_report(audit, source_for274)
    print(
        "FOR-275 validation passed: "
        f"layer delta={audit['layerComparison']['greaterThanZeroPixels']} px, "
        f"lost red blur={audit['maskExtent']['lostRedBlurPixels']} px, "
        f"white share={audit['maskExtent']['whiteLayerShareOfLostPixels']}%."
    )


if __name__ == "__main__":
    main()
