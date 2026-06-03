#!/usr/bin/env python3
"""Generate and validate FOR-273 WebGPU solid-blur Blend(kSrcIn) evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269
import validate_for272_nested_rrect_blur_color_layer_audit as for272


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-273"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "nested-rrect-solid-blur-srcin-fold-for273.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-273-webgpu-solid-blur-srcin-fold.md"
SOURCE_FOR272 = SCENE_DIR / "nested-rrect-blur-color-layer-audit-for272.json"
GPU_SOURCE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
FOR272_VALIDATOR = PROJECT_ROOT / "scripts/validate_for272_nested_rrect_blur_color_layer_audit.py"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-273 validation failed: {message}")


def json_dump(data: dict[str, Any]) -> str:
    return json.dumps(data, indent=2, sort_keys=False) + "\n"


def rounded(value: float, digits: int = 6) -> float:
    return round(float(value), digits)


def comparison(audit: dict[str, Any], name: str) -> dict[str, Any]:
    for item in audit.get("comparisons", []):
        if item.get("comparison") == name:
            return item
    fail(f"missing comparison `{name}`")


def compact_comparison(before: dict[str, Any], after: dict[str, Any]) -> dict[str, Any]:
    subzones = {}
    for subzone in for272.SUBZONES:
        before_zone = before["subzones"][subzone]
        after_zone = after["subzones"][subzone]
        subzones[subzone] = {
            "beforeGreaterThanThirtyTwoPixels": before_zone["greaterThanThirtyTwoPixels"],
            "afterGreaterThanThirtyTwoPixels": after_zone["greaterThanThirtyTwoPixels"],
            "deltaGreaterThanThirtyTwoPixels": (
                after_zone["greaterThanThirtyTwoPixels"] -
                before_zone["greaterThanThirtyTwoPixels"]
            ),
            "beforeActualColorBucketsOnGt32": before_zone["actualColorBucketsOnGt32"],
            "afterActualColorBucketsOnGt32": after_zone["actualColorBucketsOnGt32"],
        }
    return {
        "comparison": before["comparison"],
        "beforeGreaterThanThirtyTwoPixels": before["greaterThanThirtyTwoPixels"],
        "afterGreaterThanThirtyTwoPixels": after["greaterThanThirtyTwoPixels"],
        "deltaGreaterThanThirtyTwoPixels": (
            after["greaterThanThirtyTwoPixels"] -
            before["greaterThanThirtyTwoPixels"]
        ),
        "beforeMaxChannelDelta": before["maxChannelDelta"],
        "afterMaxChannelDelta": after["maxChannelDelta"],
        "deltaMaxChannelDelta": after["maxChannelDelta"] - before["maxChannelDelta"],
        "beforeSignedMeanRgbaOnBlurredEnvelopeGt32": (
            before["signedDeltaSummaryOnBlurredEnvelopeGt32"]["meanSignedRgba"]
        ),
        "afterSignedMeanRgbaOnBlurredEnvelopeGt32": (
            after["signedDeltaSummaryOnBlurredEnvelopeGt32"]["meanSignedRgba"]
        ),
        "subzones": subzones,
    }


def generate_audit() -> dict[str, Any]:
    before = for269.load_json(SOURCE_FOR272)
    current = for272.generate_audit()
    before_stats = before["reportedStats"]
    after_stats = current["reportedStats"]
    before_gpu = comparison(before, "gpu_vs_reference")
    after_gpu = comparison(current, "gpu_vs_reference")
    before_cpu = comparison(before, "cpu_vs_reference")
    after_cpu = comparison(current, "cpu_vs_reference")
    before_gpu_cpu = comparison(before, "gpu_vs_cpu")
    after_gpu_cpu = comparison(current, "gpu_vs_cpu")

    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "sceneId": SCENE_ID,
        "probe": "webgpu-solid-blur-srcin-fold",
        "sourceBeforeAudit": str(SOURCE_FOR272.relative_to(PROJECT_ROOT)),
        "sourceTest": "org.skia.gpu.webgpu.NestedClipSceneCaptureTest",
        "artifactRoot": str(SCENE_DIR.relative_to(PROJECT_ROOT)),
        "implementation": {
            "renderer": str(GPU_SOURCE.relative_to(PROJECT_ROOT)),
            "foldedCase": "solid paint + SkBlurMaskFilter + SkColorFilters.Blend(colour, kSrcIn)",
            "nonGoals": [
                "No CPU/layer correction.",
                "No broad color-filter DAG support.",
                "No support promotion from GPU-only evidence.",
            ],
        },
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "route": current["route"],
        "beforeStats": before_stats,
        "afterStats": after_stats,
        "effect": {
            "gpuSimilarityDelta": rounded(after_stats["gpuSimilarity"] - before_stats["gpuSimilarity"], 2),
            "gpuMatchingPixelsDelta": after_stats["gpuMatchingPixels"] - before_stats["gpuMatchingPixels"],
            "gpuMaxChannelDeltaDelta": after_stats["gpuMaxChannelDelta"] - before_stats["gpuMaxChannelDelta"],
            "cpuSimilarityDelta": rounded(after_stats["cpuSimilarity"] - before_stats["cpuSimilarity"], 2),
            "remainingPromotionBlocker": (
                "CPU/reference remains below the strict threshold; GPU/reference also remains below threshold."
            ),
        },
        "comparisons": [
            compact_comparison(before_gpu, after_gpu),
            compact_comparison(before_cpu, after_cpu),
            compact_comparison(before_gpu_cpu, after_gpu_cpu),
        ],
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "cpuLayerCorrectionAdded": False,
            "generalColorFilterDagSupportAdded": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
            "visualParityFallbackPreserved": for269.FALLBACK_REASON,
        },
    }


def write_report(audit: dict[str, Any]) -> None:
    gpu = comparison(audit, "gpu_vs_reference")
    cpu = comparison(audit, "cpu_vs_reference")
    route = audit["route"]
    before = audit["beforeStats"]
    after = audit["afterStats"]
    report = f"""# FOR-273 WebGPU Solid Blur SrcIn Fold

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["supportDecision"]}`

FOR-273 folds only `SkColorFilters.Blend(colour, kSrcIn)` into the WebGPU
solid-blur paint colour. The scene remains on route `{route["gpu"]}` with
fallback `{route["fallbackReason"]}` because GPU/reference `{after["gpuSimilarity"]}`
and CPU/reference `{after["cpuSimilarity"]}` remain below the strict
`{audit["supportThreshold"]}` threshold.

## Before / After

| Metric | FOR-272 before | FOR-273 after | Delta |
|---|---:|---:|---:|
| GPU/reference similarity | {before["gpuSimilarity"]} | {after["gpuSimilarity"]} | {audit["effect"]["gpuSimilarityDelta"]} |
| GPU matching pixels | {before["gpuMatchingPixels"]} | {after["gpuMatchingPixels"]} | {audit["effect"]["gpuMatchingPixelsDelta"]} |
| GPU max channel delta | {before["gpuMaxChannelDelta"]} | {after["gpuMaxChannelDelta"]} | {audit["effect"]["gpuMaxChannelDeltaDelta"]} |
| CPU/reference similarity | {before["cpuSimilarity"]} | {after["cpuSimilarity"]} | {audit["effect"]["cpuSimilarityDelta"]} |

| Comparison | >32 before | >32 after | Max delta before | Max delta after |
|---|---:|---:|---:|---:|
| GPU/reference | {gpu["beforeGreaterThanThirtyTwoPixels"]} | {gpu["afterGreaterThanThirtyTwoPixels"]} | {gpu["beforeMaxChannelDelta"]} | {gpu["afterMaxChannelDelta"]} |
| CPU/reference | {cpu["beforeGreaterThanThirtyTwoPixels"]} | {cpu["afterGreaterThanThirtyTwoPixels"]} | {cpu["beforeMaxChannelDelta"]} | {cpu["afterMaxChannelDelta"]} |

## Key Subzones

| Subzone | GPU/ref >32 before | GPU/ref >32 after |
|---|---:|---:|
| `halo_interior` | {gpu["subzones"]["halo_interior"]["beforeGreaterThanThirtyTwoPixels"]} | {gpu["subzones"]["halo_interior"]["afterGreaterThanThirtyTwoPixels"]} |
| `draw_oval_outer_boundary` | {gpu["subzones"]["draw_oval_outer_boundary"]["beforeGreaterThanThirtyTwoPixels"]} | {gpu["subzones"]["draw_oval_outer_boundary"]["afterGreaterThanThirtyTwoPixels"]} |
| `difference_oval_inner_boundary` | {gpu["subzones"]["difference_oval_inner_boundary"]["beforeGreaterThanThirtyTwoPixels"]} | {gpu["subzones"]["difference_oval_inner_boundary"]["afterGreaterThanThirtyTwoPixels"]} |

## Residual Risk

The WebGPU black/clear RGB halo is removed, but this does not promote the
scene. CPU/reference remains at `{after["cpuSimilarity"]}` and the route stays
`expected-unsupported`. The remaining work is a separate CPU/reference
layer-or-mask-extent decision, not a GPU-only support claim.

## Validation

```text
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.NestedClipSceneCaptureTest
rtk python3 scripts/validate_for273_webgpu_solid_blur_srcin_fold.py
rtk python3 scripts/validate_for272_nested_rrect_blur_color_layer_audit.py
rtk python3 scripts/validate_for271_nested_rrect_blurred_envelope_audit.py
rtk python3 scripts/validate_for270_nested_rrect_difference_oval_mask.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`
"""
    REPORT.write_text(report)


def validate(audit: dict[str, Any]) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("linear id mismatch")
    if audit.get("parent") != PARENT_ID:
        fail("parent id mismatch")
    if audit.get("sceneId") != SCENE_ID:
        fail("scene id mismatch")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision must remain expected-unsupported")

    route = audit.get("route", {})
    if route.get("gpu") != for269.GPU_ROUTE:
        fail("GPU route changed")
    if route.get("gpuStatus") != "expected-unsupported":
        fail("GPU status must remain expected-unsupported")
    if route.get("fallbackReason") != for269.FALLBACK_REASON:
        fail("fallback reason changed")

    before = audit.get("beforeStats", {})
    after = audit.get("afterStats", {})
    if before.get("gpuSimilarity") != 97.5:
        fail("FOR-272 baseline GPU similarity must remain 97.5")
    if after.get("gpuSimilarity", 0.0) <= before.get("gpuSimilarity", 0.0):
        fail("GPU/reference similarity must improve after FOR-273")
    if after.get("gpuSimilarity", 0.0) >= for269.SUPPORT_THRESHOLD:
        fail("GPU/reference must stay below strict support threshold")
    if after.get("cpuSimilarity") != before.get("cpuSimilarity"):
        fail("FOR-273 must not change CPU/reference similarity")
    if after.get("cpuSimilarity", 0.0) >= for269.SUPPORT_THRESHOLD:
        fail("CPU/reference must remain below strict support threshold")
    if after.get("gpuMaxChannelDelta", 9999) >= before.get("gpuMaxChannelDelta", 0):
        fail("GPU max channel delta must decrease")

    comparisons = {item["comparison"]: item for item in audit.get("comparisons", [])}
    gpu = comparisons.get("gpu_vs_reference")
    cpu = comparisons.get("cpu_vs_reference")
    if not isinstance(gpu, dict) or not isinstance(cpu, dict):
        fail("missing compact comparison evidence")
    if gpu["afterGreaterThanThirtyTwoPixels"] >= gpu["beforeGreaterThanThirtyTwoPixels"]:
        fail("GPU/reference >32 residual must decrease")
    halo = gpu["subzones"]["halo_interior"]
    if halo["beforeGreaterThanThirtyTwoPixels"] != 3568:
        fail("FOR-272 baseline must retain WebGPU halo-interior residual")
    if halo["afterGreaterThanThirtyTwoPixels"] != 0:
        fail("FOR-273 must clear WebGPU halo-interior >32 residual")
    if halo["beforeActualColorBucketsOnGt32"]["black_or_clear_rgb"] != 3568:
        fail("FOR-272 baseline must retain black/clear RGB WebGPU halo")
    if halo["afterActualColorBucketsOnGt32"]["black_or_clear_rgb"] != 0:
        fail("FOR-273 must remove black/clear RGB WebGPU halo")
    if cpu["afterGreaterThanThirtyTwoPixels"] != cpu["beforeGreaterThanThirtyTwoPixels"]:
        fail("CPU/reference residual must remain unchanged by WebGPU fold")

    source = GPU_SOURCE.read_text()
    for needle in (
        "solidBlurPaintColor(paint)",
        "paint.colorFilter?.asBlendModeFilter()",
        "SkBlendMode.kSrcIn",
        "Blend(colour, kSrcIn)",
    ):
        if needle not in source:
            fail(f"renderer source missing `{needle}`")
    validator = FOR272_VALIDATOR.read_text()
    if "is_for273_superseded_state" not in validator:
        fail("FOR-272 validator must remain executable after FOR-273")

    strict = audit.get("strictPreservation", {})
    for field in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "cpuLayerCorrectionAdded",
        "generalColorFilterDagSupportAdded",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if strict.get(field) is not False:
            fail(f"strict preservation field `{field}` must be false")
    if strict.get("cropFallbackPreserved") != for269.CROP_FALLBACK_REASON:
        fail("Crop fallback preservation mismatch")
    if strict.get("visualParityFallbackPreserved") != for269.FALLBACK_REASON:
        fail("visual parity fallback preservation mismatch")

    text = REPORT.read_text()
    for needle in (
        "FOR-273 WebGPU Solid Blur SrcIn Fold",
        "KEEP_EXPECTED_UNSUPPORTED",
        "GPU/reference similarity",
        "CPU/reference remains",
        for269.FALLBACK_REASON,
        AUDIT_NAME,
    ):
        if needle not in text:
            fail(f"report missing `{needle}`")


def main() -> None:
    audit = generate_audit()
    SCENE_DIR.mkdir(parents=True, exist_ok=True)
    AUDIT.write_text(json_dump(audit))
    write_report(audit)
    validate(for269.load_json(AUDIT))
    print(
        "FOR-273 validation passed: WebGPU solid blur folds Blend(kSrcIn), "
        "GPU/reference improves without support promotion, CPU/reference remains blocking, "
        "and expected-unsupported diagnostics are preserved."
    )


if __name__ == "__main__":
    main()
