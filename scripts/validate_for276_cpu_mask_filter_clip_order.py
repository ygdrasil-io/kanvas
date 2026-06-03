#!/usr/bin/env python3
"""Validate FOR-276 CPU mask-filter/clip order evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-276"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "cpu-mask-filter-clip-order-for276.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-276-cpu-mask-filter-clip-order.md"
SOURCE_FOR275 = SCENE_DIR / "cpu-srcin-blur-layer-fixture-for275.json"
STATS = SCENE_DIR / "stats.json"
TEST_SOURCE = PROJECT_ROOT / "kanvas-skia/src/test/kotlin/org/skia/core/For275CpuSrcInBlurLayerFixtureTest.kt"
CPU_SOURCE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-276 validation failed: {message}")


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
        CPU_SOURCE,
        (
            "Keep source mask generation independent from the final clip only",
            "for active AA clips",
            "Rect-only clips keep the",
            "established quick-reject behavior covered by BlurQuickRejectGM",
            "val preserveOffClipMaskSource = activeAaClip != null",
            "val compositeX0 = maxOf(0, clip.left - ml)",
            "for (y in compositeY0 until compositeY1)",
            "for (x in compositeX0 until compositeX1)",
        ),
    )
    require_needles(
        TEST_SOURCE,
        (
            "FOR-276 CPU mask filter keeps source outside AA clip before blur",
            "canvas.clipRRect(SkRRect.MakeOval(HALO_CLIP), SkClipOp.kIntersect, doAntiAlias = true)",
            "cpu-mask-filter-clip-order-for276.json",
            "CPU_MASK_FILTER_SOURCE_CLIP_ORDER_WAS_TRUNCATING_BLUR_SOURCE",
            "REGENERATE_M60_SCENE_EVIDENCE_AND_RECHECK_CPU_GPU_REFERENCE_THRESHOLD_BEFORE_PROMOTION",
        ),
    )


def validate_for275_source() -> dict[str, Any]:
    source = load_json(SOURCE_FOR275)
    if source.get("linear") != "FOR-275":
        fail("source fixture must be FOR-275")
    if source.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("FOR-275 source must keep expected-unsupported")
    route = source.get("routePreservation", {})
    if route.get("gpuStatus") != "expected-unsupported":
        fail("FOR-275 GPU status changed")
    if route.get("fallbackReason") != for269.FALLBACK_REASON:
        fail("FOR-275 fallback reason changed")
    layer = source.get("layerComparison", {})
    if layer.get("greaterThanZeroPixels") != 0:
        fail("FOR-275 saveLayer/direct separation changed")
    if source.get("layerIntroducedWhitePixels") != 0:
        fail("FOR-275 layer introduced white pixels")
    return source


def validate_scene_stats() -> dict[str, Any]:
    stats = load_json(STATS)
    if stats.get("sceneId") != SCENE_ID:
        fail("scene stats id mismatch")
    if stats.get("threshold") != for269.SUPPORT_THRESHOLD:
        fail("scene stats threshold changed")
    if stats.get("cpuSimilarity") != 97.31:
        fail("M60 CPU similarity changed; update FOR-276 evidence intentionally")
    if stats.get("gpuSimilarity") != 98.48:
        fail("M60 GPU similarity changed; update FOR-276 evidence intentionally")
    if stats.get("gpuStatus") != "expected-unsupported":
        fail("M60 GPU status must remain expected-unsupported")
    if stats.get("fallbackReason") != for269.FALLBACK_REASON:
        fail("M60 fallback reason changed")
    if stats.get("cpuMatchingPixels") != 908439:
        fail("M60 CPU matching pixels changed; update FOR-276 evidence intentionally")
    if stats.get("gpuMatchingPixels") != 919363:
        fail("M60 GPU matching pixels changed; update FOR-276 evidence intentionally")
    return stats


def validate(audit: dict[str, Any]) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("linear id mismatch")
    if audit.get("parent") != PARENT_ID:
        fail("parent id mismatch")
    if audit.get("sceneId") != SCENE_ID:
        fail("scene id mismatch")
    if audit.get("probe") != "cpu-mask-filter-clip-order":
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
    if fixture.get("variants") != ["intersectHaloClip", "unclippedBlurControl"]:
        fail("fixture variants changed")
    if fixture.get("intersectHaloClip") != {"left": 14, "top": 30, "right": 18, "bottom": 42}:
        fail("intersect halo clip changed")

    metrics = audit.get("maskFilterClipOrder", {})
    baseline = metrics.get("preFixBaseline", {})
    if baseline.get("clippedRedSupportInClip") != 0:
        fail("pre-fix baseline must expose zero clipped red support")
    if baseline.get("lostRedSupportInClip") != 10:
        fail("pre-fix baseline lost-red count changed")
    if baseline.get("recoveredShareOfUnclippedSupport") != 0.0:
        fail("pre-fix baseline recovered share changed")

    if metrics.get("clipPixels") != 48:
        fail("clip pixel count changed")
    if metrics.get("unclippedRedSupportInClip") != 10:
        fail("unclipped red support changed")
    if metrics.get("clippedRedSupportInClip") != 8:
        fail("bounded AA correction did not recover the expected clipped red support")
    if metrics.get("lostRedSupportInClip") != 2:
        fail("bounded AA correction lost-red count changed")
    if metrics.get("recoveredShareOfUnclippedSupport") != 80.0:
        fail("recovered share changed")
    samples = metrics.get("samples", [])
    if not isinstance(samples, list) or len(samples) < 4:
        fail("signed RGB samples missing")
    first = samples[0]
    if first.get("clipMinusUnclippedSignedRgba") != [0, 30, 30, 0]:
        fail("first AA sample changed")

    correction = audit.get("boundedCorrection", {})
    if correction.get("sourceMaskBounds") != "device bounds expanded by mask-filter margin for active AA clips, not truncated to final clip":
        fail("source mask bounds decision changed")
    if correction.get("finalCompositionBounds") != "current clip rect plus active AA clip coverage":
        fail("final composition bounds decision changed")
    if correction.get("wideClipStackSupportAdded") is not False:
        fail("wide clip stack support must not be claimed")

    if audit.get("dominantFixtureHypothesis") != "CPU_MASK_FILTER_SOURCE_CLIP_ORDER_WAS_TRUNCATING_BLUR_SOURCE":
        fail("dominant fixture hypothesis changed")
    if audit.get("nextAction") != "REGENERATE_M60_SCENE_EVIDENCE_AND_RECHECK_CPU_GPU_REFERENCE_THRESHOLD_BEFORE_PROMOTION":
        fail("next action changed")

    strict = audit.get("strictPreservation", {})
    expected = {
        "productionRendererChanged": True,
        "cpuRendererChanged": True,
        "gpuRendererChanged": False,
        "supportPromotionChanged": False,
        "supportThresholdChanged": False,
        "fallbackOrReadbackAdded": False,
        "wideClipStackSupportAdded": False,
        "ganeshOrGraphiteAdded": False,
        "skSLCompilerAdded": False,
    }
    for key, value in expected.items():
        if strict.get(key) is not value:
            fail(f"strict preservation `{key}` expected {value}")


def write_report(audit: dict[str, Any], source_for275: dict[str, Any], stats: dict[str, Any]) -> None:
    metrics = audit["maskFilterClipOrder"]
    baseline = metrics["preFixBaseline"]
    route = audit["routePreservation"]
    first = metrics["samples"][0]
    for275_mask = source_for275["maskExtent"]
    report = f"""# FOR-276 CPU Mask Filter Clip Order

Linear: `{LINEAR_ID}`

Scene cible: `{SCENE_ID}`

Decision: `{audit["supportDecision"]}`

FOR-276 corrige de facon bornee l'ordre CPU du `mask filter` (filtre de
masque) et du `clip` (decoupe) pour les AA clips actifs. Le masque source du
blur est maintenant genere dans les bornes device + marge du filtre pour ce
chemin, sans tronquer au clip final avant filtrage. La composition reste
limitee au clip courant et a l'AA clip actif. Les clips rectangulaires simples
gardent le quick reject existant.

## Mesure Avant/Apres

| Mesure | Avant correction | Apres correction |
|---|---:|---:|
| Support rouge non clippe dans le clip de halo | {metrics["unclippedRedSupportInClip"]} | {metrics["unclippedRedSupportInClip"]} |
| Pixels rouges conserves dans le clip de halo | {baseline["clippedRedSupportInClip"]} | {metrics["clippedRedSupportInClip"]} |
| Pixels rouges perdus dans le clip de halo | {baseline["lostRedSupportInClip"]} | {metrics["lostRedSupportInClip"]} |
| Part recuperee | {baseline["recoveredShareOfUnclippedSupport"]}% | {metrics["recoveredShareOfUnclippedSupport"]}% |

## Fixture

| Element | Valeur |
|---|---:|
| Dimensions | {audit["fixture"]["width"]} x {audit["fixture"]["height"]} |
| Sigma | {audit["fixture"]["sigma"]} |
| Pixels du clip de halo | {metrics["clipPixels"]} |
| Clip de halo | `{audit["fixture"]["intersectHaloClip"]}` |

## Sample Signe

| Pixel | Unclipped RGBA | Clip RGBA apres correction | Clip-unclipped |
|---|---|---|---|
| {first["x"]},{first["y"]} | `{first["unclippedBlurControlRgba"]}` | `{first["intersectHaloClipRgba"]}` | `{first["clipMinusUnclippedSignedRgba"]}` |

## Preservation

- M60 reste `expected-unsupported`.
- Fallback `{route["fallbackReason"]}` conserve.
- Fallback `{route["cropFallbackPreserved"]}` conserve.
- Aucun readback/fallback, support large clip-stack, Ganesh, Graphite ou
  compilateur SkSL ajoute.
- FOR-275 reste stable: saveLayer/direct `{source_for275["layerComparison"]["greaterThanZeroPixels"]}`
  pixel different, pixels rouges perdus sous difference clip
  `{for275_mask["lostRedBlurPixels"]}`.

## Impact Scene M60

| Mesure scene complete | Valeur |
|---|---:|
| CPU/reference similarity | {stats["cpuSimilarity"]}% |
| CPU matching pixels | {stats["cpuMatchingPixels"]} |
| CPU max channel delta | {stats["cpuMaxChannelDelta"]} |
| GPU/reference similarity | {stats["gpuSimilarity"]}% |
| GPU matching pixels | {stats["gpuMatchingPixels"]} |
| GPU max channel delta | {stats["gpuMaxChannelDelta"]} |
| Seuil promotion | {stats["threshold"]}% |

La fixture bornee recupere le halo local, mais l'evidence scene regeneree
reste sous seuil: CPU/reference `{stats["cpuSimilarity"]}%` et GPU/reference
`{stats["gpuSimilarity"]}%`. La scene n'est donc pas promue.

Le bornage preserve aussi `BlurQuickRejectGM`: le comportement source-mask
non tronque n'est active que lorsqu'un AA clip non rectangulaire est lie au
device.

## Conclusion

Hypothese dominante:
`{audit["dominantFixtureHypothesis"]}`.

La correction est locale au chemin CPU `drawPathWithMaskFilter`. Elle ne suffit
pas a promouvoir M60: il faut regenerer les evidences scene et verifier
simultanement CPU/reference et GPU/reference au seuil `{audit["supportThreshold"]}`.

## Validation

```text
rtk ./gradlew --no-daemon :kanvas-skia:test --tests org.skia.core.For275CpuSrcInBlurLayerFixtureTest
rtk ./gradlew --no-daemon :skia-integration-tests:test --tests org.skia.tests.Round12Test
rtk python3 scripts/validate_for276_cpu_mask_filter_clip_order.py
rtk python3 scripts/validate_for275_cpu_srcin_blur_layer_fixture.py
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.NestedClipSceneCaptureTest
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`
"""
    REPORT.write_text(report, encoding="utf-8")


def main() -> None:
    validate_sources()
    source_for275 = validate_for275_source()
    stats = validate_scene_stats()
    audit = load_json(AUDIT)
    validate(audit)
    write_report(audit, source_for275, stats)
    metrics = audit["maskFilterClipOrder"]
    baseline = metrics["preFixBaseline"]
    print(
        "FOR-276 validation passed: "
        f"clipped red {baseline['clippedRedSupportInClip']} -> {metrics['clippedRedSupportInClip']}, "
        f"lost red {baseline['lostRedSupportInClip']} -> {metrics['lostRedSupportInClip']}, "
        f"recovered {baseline['recoveredShareOfUnclippedSupport']}% -> "
        f"{metrics['recoveredShareOfUnclippedSupport']}%."
    )


if __name__ == "__main__":
    main()
