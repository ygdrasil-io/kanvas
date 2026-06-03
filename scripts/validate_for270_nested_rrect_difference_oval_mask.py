#!/usr/bin/env python3
"""Generate and validate FOR-270 nested RRect difference-oval evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

import validate_for269_nested_rrect_zone_mask_audit as for269


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-270"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "nested-rrect-zone-mask-audit-for270.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-270-nested-rrect-difference-oval-mask.md"

FOR269_GPU_REMOVED_OVAL_GT32 = 246_288
FOR269_GPU_REMOVED_OVAL_SHARE = 92.912221
FOR269_GPU_SIMILARITY = 71.22


def fail(message: str) -> None:
    raise SystemExit(f"FOR-270 validation failed: {message}")


def json_dump(data: dict[str, Any]) -> str:
    return json.dumps(data, indent=2, sort_keys=False) + "\n"


def comparison(audit: dict[str, Any], name: str) -> dict[str, Any]:
    for item in audit.get("comparisons", []):
        if item.get("comparison") == name:
            return item
    fail(f"missing comparison `{name}`")


def enrich_audit(audit: dict[str, Any]) -> dict[str, Any]:
    gpu = comparison(audit, "gpu_vs_reference")
    cpu = comparison(audit, "cpu_vs_reference")
    gpu_removed = gpu["zones"]["outside_clip_removed_difference_oval"]
    after_removed = gpu_removed["greaterThanThirtyTwoPixels"]
    after_total = max(1, gpu["greaterThanThirtyTwoPixels"])
    after_share = round((after_removed / after_total) * 100.0, 6)

    audit["linear"] = LINEAR_ID
    audit["parent"] = PARENT_ID
    audit["sourceReport"] = str(REPORT.relative_to(PROJECT_ROOT))
    audit["sourceAudit"] = (
        "reports/wgsl-pipeline/scenes/artifacts/"
        f"{SCENE_ID}/nested-rrect-zone-mask-audit-for269.json"
    )
    audit["supportDecision"] = "KEEP_EXPECTED_UNSUPPORTED"
    audit["nextAction"] = "BLURRED_CONTENT_ENVELOPE_RESIDUAL_REMAINS"
    audit["secondaryBlocker"] = "GPU_AND_CPU_REFERENCE_REMAIN_BELOW_STRICT_THRESHOLD"
    audit["decisionRationale"] = (
        "FOR-270 applies the bounded WebGPU blur-composite analytic clip mask to "
        "the nested clip difference oval. The removed-oval GPU residual drops "
        "below the FOR-269 dominant zone, but both GPU/reference and CPU/reference "
        "remain below the strict support threshold, dominated by the blurred "
        "content envelope."
    )
    audit["for270Effect"] = {
        "boundedFix": "webgpu.blur-composite-analytic-clip-shape",
        "for269GpuSimilarity": FOR269_GPU_SIMILARITY,
        "for270GpuSimilarity": audit["reportedStats"]["gpuSimilarity"],
        "for269GpuRemovedOvalGreaterThanThirtyTwoPixels": FOR269_GPU_REMOVED_OVAL_GT32,
        "for270GpuRemovedOvalGreaterThanThirtyTwoPixels": after_removed,
        "for269GpuRemovedOvalDominantShare": FOR269_GPU_REMOVED_OVAL_SHARE,
        "for270GpuRemovedOvalShare": after_share,
        "for270GpuDominantGreaterThanThirtyTwoZone": gpu["dominantGreaterThanThirtyTwoZone"],
        "for270CpuDominantGreaterThanThirtyTwoZone": cpu["dominantGreaterThanThirtyTwoZone"],
    }
    return audit


def write_report(audit: dict[str, Any]) -> None:
    gpu = comparison(audit, "gpu_vs_reference")
    cpu = comparison(audit, "cpu_vs_reference")
    route = audit["route"]
    effect = audit["for270Effect"]
    report = f"""# FOR-270 Nested RRect Difference-Oval Mask

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["supportDecision"]}`

The bounded correction applies the active analytic clip shape during the
WebGPU blur final composite. The route remains `{route["gpu"]}` with fallback
`{route["fallbackReason"]}` because the strict support threshold is not met.

## Before/After

| Measure | FOR-269 baseline | FOR-270 |
|---|---:|---:|
| GPU/reference similarity | {effect["for269GpuSimilarity"]} | {effect["for270GpuSimilarity"]} |
| GPU >32 deltas in `outside_clip_removed_difference_oval` | {effect["for269GpuRemovedOvalGreaterThanThirtyTwoPixels"]} | {effect["for270GpuRemovedOvalGreaterThanThirtyTwoPixels"]} |
| GPU removed-oval share of >32 deltas | {effect["for269GpuRemovedOvalDominantShare"]}% | {effect["for270GpuRemovedOvalShare"]}% |

## Residual

| Comparison | Dominant >32 delta zone | Share | Max delta |
|---|---|---:|---:|
| GPU/reference | `{gpu["dominantGreaterThanThirtyTwoZone"]}` | {gpu["dominantGreaterThanThirtyTwoZoneShare"]}% | {gpu["maxChannelDelta"]} |
| CPU/reference | `{cpu["dominantGreaterThanThirtyTwoZone"]}` | {cpu["dominantGreaterThanThirtyTwoZoneShare"]}% | {cpu["maxChannelDelta"]} |

## Validation

```text
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.NestedClipSceneCaptureTest
rtk python3 scripts/validate_for270_nested_rrect_difference_oval_mask.py
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`
"""
    REPORT.write_text(report)


def validate(audit: dict[str, Any]) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("linear id mismatch")
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

    reported = audit.get("reportedStats", {})
    if reported.get("gpuSimilarity") >= for269.SUPPORT_THRESHOLD:
        fail("GPU similarity unexpectedly reaches support threshold")
    if reported.get("cpuSimilarity") >= for269.SUPPORT_THRESHOLD:
        fail("CPU similarity unexpectedly reaches support threshold")

    gpu = comparison(audit, "gpu_vs_reference")
    cpu = comparison(audit, "cpu_vs_reference")
    gpu_removed = gpu["zones"]["outside_clip_removed_difference_oval"]
    removed_gt32 = gpu_removed["greaterThanThirtyTwoPixels"]
    if removed_gt32 >= FOR269_GPU_REMOVED_OVAL_GT32:
        fail("removed-oval GPU >32 residual did not decrease")
    if removed_gt32 > 1_000:
        fail("removed-oval GPU >32 residual is not bounded after correction")
    if gpu.get("dominantGreaterThanThirtyTwoZone") != "blurred_content_envelope":
        fail("GPU residual should now be dominated by blurred content envelope")
    if cpu.get("dominantGreaterThanThirtyTwoZone") != "blurred_content_envelope":
        fail("CPU residual should remain dominated by blurred content envelope")

    strict = audit.get("strictPreservation", {})
    for field in (
        "supportPromotionChanged",
        "supportThresholdChanged",
        "wideClipStackSupportAdded",
        "fallbackOrReadbackAdded",
        "productionRendererChanged",
        "ganeshOrGraphiteAdded",
        "skSLCompilerAdded",
    ):
        if strict.get(field) is not False:
            fail(f"strict preservation field `{field}` must be false")
    if strict.get("cropFallbackPreserved") != for269.CROP_FALLBACK_REASON:
        fail("Crop fallback preservation mismatch")

    text = REPORT.read_text()
    for needle in (
        "FOR-270 Nested RRect Difference-Oval Mask",
        "KEEP_EXPECTED_UNSUPPORTED",
        for269.FALLBACK_REASON,
        AUDIT_NAME,
    ):
        if needle not in text:
            fail(f"report missing `{needle}`")


def main() -> None:
    audit = enrich_audit(for269.generate_audit())
    SCENE_DIR.mkdir(parents=True, exist_ok=True)
    AUDIT.write_text(json_dump(audit))
    write_report(audit)
    validate(audit)
    print(
        "FOR-270 validation passed: removed difference-oval GPU residual decreased, "
        "blurred content envelope is now dominant, and support remains expected-unsupported."
    )


if __name__ == "__main__":
    main()
