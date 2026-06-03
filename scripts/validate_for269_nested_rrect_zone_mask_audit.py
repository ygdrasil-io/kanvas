#!/usr/bin/env python3
"""Generate and validate FOR-269 nested RRect clip zone/mask audit evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import numpy as np
from PIL import Image


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-269"
PARENT_ID = "FOR-241"
SCENE_ID = "m60-bounded-nested-rrect-clip"
SCENE_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
AUDIT_NAME = "nested-rrect-zone-mask-audit-for269.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-269-nested-rrect-zone-mask-audit.md"
SOURCE_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-01-m60-nested-clip-path-aa-promotion.md"
TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/NestedClipSceneCaptureTest.kt"

GPU_ROUTE = "webgpu.coverage.nested-rrect-clip.expected-unsupported"
CPU_ROUTE = "cpu.coverage.nested-rrect-clip-oracle"
FALLBACK_REASON = "coverage.nested-clip-visual-parity-below-threshold"
CROP_FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
SUPPORT_THRESHOLD = 99.95


def fail(message: str) -> None:
    raise SystemExit(f"FOR-269 validation failed: {message}")


def load_json(path: Path) -> Any:
    if not path.is_file():
        fail(f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    return json.loads(path.read_text())


def load_image(name: str) -> np.ndarray:
    path = SCENE_DIR / f"{name}.png"
    if not path.is_file():
        fail(f"missing PNG file: {path.relative_to(PROJECT_ROOT)}")
    return np.asarray(Image.open(path).convert("RGBA"), dtype=np.int16)


def json_dump(data: dict[str, Any]) -> str:
    return json.dumps(data, indent=2, sort_keys=False) + "\n"


def rounded(value: float, digits: int = 6) -> float:
    return round(float(value), digits)


def mask_bounds(mask: np.ndarray) -> dict[str, int] | None:
    ys, xs = np.nonzero(mask)
    if xs.size == 0:
        return None
    return {
        "left": int(xs.min()),
        "top": int(ys.min()),
        "right": int(xs.max() + 1),
        "bottom": int(ys.max() + 1),
    }


def diff_stats(diff: np.ndarray, mask: np.ndarray) -> dict[str, Any]:
    masked = diff[mask]
    pixels = int(mask.sum())
    exact = diff > 0
    gt1 = diff > 1
    gt8 = diff > 8
    gt32 = diff > 32
    mismatch = int((exact & mask).sum())
    return {
        "pixels": pixels,
        "bounds": mask_bounds(mask),
        "exactMismatchPixels": mismatch,
        "exactMatchPixels": pixels - mismatch,
        "toleranceOneMismatchPixels": int((gt1 & mask).sum()),
        "greaterThanEightPixels": int((gt8 & mask).sum()),
        "greaterThanThirtyTwoPixels": int((gt32 & mask).sum()),
        "maxChannelDelta": int(masked.max()) if pixels else 0,
        "meanMaxChannelDelta": rounded(masked.mean()) if pixels else 0.0,
    }


def make_masks(width: int, height: int) -> tuple[dict[str, Any], dict[str, np.ndarray]]:
    y, x = np.mgrid[0:height, 0:width]
    xc = x + 0.5
    yc = y + 0.5

    # BlurredClippedCircleGM applies scale(2) before the nested clips/draw.
    device = (xc >= 0) & (xc < width) & (yc >= 0) & (yc < height)
    clip_rect_1 = (0, 0, 1164 * 2, 802 * 2)
    clip_rect_2 = clip_rect_1
    inner_oval = (8 * 2, 8 * 2, 288 * 2, 288 * 2)
    outer_oval = (4 * 2, 4 * 2, 292 * 2, 292 * 2)

    def ellipse_value(rect: tuple[int, int, int, int]) -> np.ndarray:
        left, top, right, bottom = rect
        cx = (left + right) / 2.0
        cy = (top + bottom) / 2.0
        rx = (right - left) / 2.0
        ry = (bottom - top) / 2.0
        return ((xc - cx) / rx) ** 2 + ((yc - cy) / ry) ** 2

    inner_value = ellipse_value(inner_oval)
    outer_value = ellipse_value(outer_oval)
    inner = inner_value <= 1.0
    outer = outer_value <= 1.0
    inner_boundary = np.abs(np.sqrt(inner_value) - 1.0) <= (3.0 / 280.0)
    outer_boundary = np.abs(np.sqrt(outer_value) - 1.0) <= (3.0 / 288.0)
    rect_intersection = device
    surviving_clip = rect_intersection & ~inner
    blurred_envelope = (outer & ~inner) | inner_boundary | outer_boundary

    geometry = {
        "sourceScene": "BlurredClippedCircleGM",
        "deviceSize": {"width": width, "height": height},
        "canvasScale": 2,
        "sourceClipStack": [
            {"kind": "clipRect", "op": "intersect", "localBounds": [0, 0, 1164, 802]},
            {"kind": "clipRect", "op": "intersect", "localBounds": [0, 0, 1164, 802]},
            {"kind": "clipRRect", "shape": "oval", "op": "difference", "localBounds": [8, 8, 288, 288]},
        ],
        "deviceClipRects": [
            {"kind": "clipRect", "op": "intersect", "deviceBounds": list(clip_rect_1)},
            {"kind": "clipRect", "op": "intersect", "deviceBounds": list(clip_rect_2)},
        ],
        "differenceOvalDeviceBounds": list(inner_oval),
        "drawOvalDeviceBounds": list(outer_oval),
        "boundaryBandDevicePixels": 3,
        "note": "The two intersected clip rects cover the whole 1164x802 surface after scale(2); the strict final-clip exterior is the oval removed by clipRRect(kDifference).",
    }
    masks = {
        "intersected_rectangles": rect_intersection,
        "outside_clip_removed_difference_oval": inner,
        "intersected_rectangles_surviving_difference": surviving_clip,
        "difference_rrect_oval_boundary": inner_boundary,
        "blurred_content_envelope": blurred_envelope,
        "outside_draw_oval": ~outer,
    }
    return geometry, masks


def sample_region(x: int, y: int, masks: dict[str, np.ndarray]) -> str:
    if masks["difference_rrect_oval_boundary"][y, x]:
        return "difference_rrect_oval_boundary"
    if masks["outside_clip_removed_difference_oval"][y, x]:
        return "outside_clip_removed_difference_oval"
    if masks["blurred_content_envelope"][y, x]:
        return "blurred_content_envelope"
    if masks["intersected_rectangles_surviving_difference"][y, x]:
        return "intersected_rectangles_surviving_difference"
    return "outside_intersected_rectangles"


def high_delta_samples(
    *,
    comparison: str,
    reference: np.ndarray,
    actual: np.ndarray,
    diff: np.ndarray,
    masks: dict[str, np.ndarray],
    limit: int = 16,
) -> list[dict[str, Any]]:
    ys, xs = np.nonzero(diff > 32)
    records: list[tuple[int, int, int]] = []
    for y, x in zip(ys.tolist(), xs.tolist()):
        records.append((-int(diff[y, x]), int(y), int(x)))
    records.sort()

    samples: list[dict[str, Any]] = []
    for neg_delta, y, x in records[:limit]:
        ref_rgba = reference[y, x].astype(int)
        actual_rgba = actual[y, x].astype(int)
        signed = actual_rgba - ref_rgba
        samples.append(
            {
                "comparison": comparison,
                "x": x,
                "y": y,
                "region": sample_region(x, y, masks),
                "referenceRgba": ref_rgba.tolist(),
                "actualRgba": actual_rgba.tolist(),
                "signedDeltaRgba": signed.tolist(),
                "maxChannelDelta": -neg_delta,
            }
        )
    return samples


def comparison_audit(name: str, reference: np.ndarray, actual: np.ndarray, masks: dict[str, np.ndarray]) -> dict[str, Any]:
    channel_delta = np.abs(actual - reference)
    diff = channel_delta.max(axis=2)
    total = int(diff.size)
    exact_match = int((diff == 0).sum())
    gt32 = diff > 32
    zone_stats = {zone: diff_stats(diff, mask) for zone, mask in masks.items()}
    dominant_candidates = {
        zone: zone_stats[zone]
        for zone in (
            "outside_clip_removed_difference_oval",
            "difference_rrect_oval_boundary",
            "blurred_content_envelope",
            "outside_draw_oval",
        )
    }
    dominant_gt32_zone = max(
        dominant_candidates.items(),
        key=lambda item: (item[1]["greaterThanThirtyTwoPixels"], item[0]),
    )
    return {
        "comparison": name,
        "totalPixels": total,
        "exactMatchingPixels": exact_match,
        "exactSimilarity": rounded((exact_match / total) * 100.0),
        "exactMismatchPixels": total - exact_match,
        "toleranceOneMismatchPixels": int((diff > 1).sum()),
        "greaterThanEightPixels": int((diff > 8).sum()),
        "greaterThanThirtyTwoPixels": int(gt32.sum()),
        "maxChannelDelta": int(diff.max()),
        "dominantGreaterThanThirtyTwoZone": dominant_gt32_zone[0],
        "dominantGreaterThanThirtyTwoZoneShare": rounded(
            dominant_gt32_zone[1]["greaterThanThirtyTwoPixels"] / max(1, int(gt32.sum())) * 100.0,
        ),
        "zones": zone_stats,
        "highDeltaSamples": high_delta_samples(
            comparison=name,
            reference=reference,
            actual=actual,
            diff=diff,
            masks=masks,
        ),
    }


def generate_audit() -> dict[str, Any]:
    stats = load_json(SCENE_DIR / "stats.json")
    route_gpu = load_json(SCENE_DIR / "route-gpu.json")
    route_cpu = load_json(SCENE_DIR / "route-cpu.json")
    reference = load_image("skia")
    cpu = load_image("cpu")
    gpu = load_image("gpu")

    if reference.shape != cpu.shape or reference.shape != gpu.shape:
        fail("reference, CPU, and GPU PNG dimensions differ")
    height, width, channels = reference.shape
    if channels != 4:
        fail("expected RGBA images")

    geometry, masks = make_masks(width, height)
    cpu_audit = comparison_audit("cpu_vs_reference", reference, cpu, masks)
    gpu_audit = comparison_audit("gpu_vs_reference", reference, gpu, masks)

    audit = {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "nested-rrect-zone-mask-audit",
        "sceneId": SCENE_ID,
        "backend": "CPU/WebGPU",
        "sourceReport": str(SOURCE_REPORT.relative_to(PROJECT_ROOT)),
        "sourceTest": "org.skia.gpu.webgpu.NestedClipSceneCaptureTest",
        "artifactRoot": str(SCENE_DIR.relative_to(PROJECT_ROOT)),
        "supportThreshold": SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "nextAction": "BOUNDED_CLIP_MASK_CORRECTION_FIRST",
        "secondaryBlocker": "CPU_REFERENCE_RESIDUAL_REMAINS_BELOW_STRICT_THRESHOLD",
        "decisionRationale": (
            "GPU/reference high-delta pixels are dominated by the oval removed by "
            "clipRRect(kDifference), while CPU/reference high-delta pixels are localized "
            "to the blurred content envelope. The scene therefore remains refused until "
            "a bounded clip-mask correction and follow-up CPU/reference proof exist."
        ),
        "route": {
            "cpu": route_cpu.get("selectedRoute"),
            "gpu": route_gpu.get("selectedRoute"),
            "gpuStatus": route_gpu.get("status"),
            "fallbackReason": route_gpu.get("fallbackReason"),
            "coverageStrategy": route_gpu.get("coverageStrategy"),
            "pipelineKey": route_gpu.get("pipelineKey"),
        },
        "reportedStats": {
            "cpuSimilarity": stats.get("cpuSimilarity"),
            "gpuSimilarity": stats.get("gpuSimilarity"),
            "threshold": stats.get("threshold"),
            "cpuMatchingPixels": stats.get("cpuMatchingPixels"),
            "gpuMatchingPixels": stats.get("gpuMatchingPixels"),
            "cpuMaxChannelDelta": stats.get("cpuMaxChannelDelta"),
            "gpuMaxChannelDelta": stats.get("gpuMaxChannelDelta"),
        },
        "geometry": geometry,
        "zoneDefinitions": {
            "accounting": "Masks are analytic and intentionally non-exclusive; zone counts isolate hypotheses and must not be summed as a partition.",
            "intersected_rectangles": "Pixels inside both scaled clipRect intersections; this covers the full captured surface for this GM.",
            "outside_clip_removed_difference_oval": "Pixels inside the device-space oval removed by clipRRect(kDifference), i.e. outside the final surviving clip for the draw.",
            "intersected_rectangles_surviving_difference": "Pixels inside the intersected rect clips and outside the removed oval.",
            "difference_rrect_oval_boundary": "Three-device-pixel band around the removed oval boundary.",
            "blurred_content_envelope": "Outer draw oval annulus plus three-device-pixel bands around the inner difference oval and outer draw oval.",
            "outside_draw_oval": "Pixels outside the device-space draw oval.",
        },
        "comparisons": [gpu_audit, cpu_audit],
        "strictPreservation": {
            "supportPromotionChanged": False,
            "supportThresholdChanged": False,
            "wideClipStackSupportAdded": False,
            "fallbackOrReadbackAdded": False,
            "productionRendererChanged": False,
            "ganeshOrGraphiteAdded": False,
            "skSLCompilerAdded": False,
            "cropFallbackPreserved": CROP_FALLBACK_REASON,
        },
    }
    return audit


def write_report(audit: dict[str, Any]) -> None:
    comparisons = {item["comparison"]: item for item in audit["comparisons"]}
    gpu = comparisons["gpu_vs_reference"]
    cpu = comparisons["cpu_vs_reference"]
    gpu_removed = gpu["zones"]["outside_clip_removed_difference_oval"]
    cpu_blur = cpu["zones"]["blurred_content_envelope"]
    route = audit["route"]
    report = f"""# FOR-269 Nested RRect Zone/Mask Audit

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["supportDecision"]}`

Next action: `{audit["nextAction"]}`. The stable refusal remains
`{route["fallbackReason"]}` on route `{route["gpu"]}`. No support promotion,
threshold change, broad clip-stack support, fallback/readback path, Ganesh,
Graphite, or SkSL compiler was added. The existing Crop refusal
`{CROP_FALLBACK_REASON}` remains preserved.

## Route And Threshold

| Field | Value |
|---|---|
| CPU route | `{route["cpu"]}` |
| GPU route | `{route["gpu"]}` |
| GPU status | `{route["gpuStatus"]}` |
| Fallback | `{route["fallbackReason"]}` |
| Strict support threshold | `{audit["supportThreshold"]}` |
| Reported CPU/reference similarity | `{audit["reportedStats"]["cpuSimilarity"]}` |
| Reported GPU/reference similarity | `{audit["reportedStats"]["gpuSimilarity"]}` |

## Zone Findings

| Comparison | Dominant >32 delta zone | Share | Max delta |
|---|---|---:|---:|
| GPU/reference | `{gpu["dominantGreaterThanThirtyTwoZone"]}` | {gpu["dominantGreaterThanThirtyTwoZoneShare"]}% | {gpu["maxChannelDelta"]} |
| CPU/reference | `{cpu["dominantGreaterThanThirtyTwoZone"]}` | {cpu["dominantGreaterThanThirtyTwoZoneShare"]}% | {cpu["maxChannelDelta"]} |

GPU/reference has {gpu_removed["greaterThanThirtyTwoPixels"]} pixels above
delta 32 inside `outside_clip_removed_difference_oval`, which is the oval
removed by `clipRRect(kDifference)`. CPU/reference has
{cpu_blur["greaterThanThirtyTwoPixels"]} pixels above delta 32 in
`blurred_content_envelope`, so CPU/reference is still below the strict support
bar even after the GPU clip-mask issue is isolated.

The zone masks are analytic and non-exclusive. Their counters isolate likely
failure hypotheses and must not be summed as a partition of the image.

## Machine Artifact

`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`

## Validation

```text
rtk python3 scripts/validate_for269_nested_rrect_zone_mask_audit.py
```
"""
    REPORT.write_text(report)


def require_text(path: Path, needles: list[str]) -> None:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")


def validate_audit(audit: dict[str, Any]) -> None:
    if audit.get("linear") != LINEAR_ID:
        fail("linear id mismatch")
    if audit.get("parent") != PARENT_ID:
        fail("parent id mismatch")
    if audit.get("sceneId") != SCENE_ID:
        fail("scene id mismatch")
    if audit.get("supportThreshold") != SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision must keep expected-unsupported")
    if audit.get("nextAction") != "BOUNDED_CLIP_MASK_CORRECTION_FIRST":
        fail("next action must remain bounded clip-mask correction first")

    route = audit.get("route", {})
    if route.get("cpu") != CPU_ROUTE:
        fail("CPU route mismatch")
    if route.get("gpu") != GPU_ROUTE:
        fail("GPU route mismatch")
    if route.get("gpuStatus") != "expected-unsupported":
        fail("GPU status must remain expected-unsupported")
    if route.get("fallbackReason") != FALLBACK_REASON:
        fail("fallback reason mismatch")
    definitions = audit.get("zoneDefinitions")
    if not isinstance(definitions, dict) or "non-exclusive" not in definitions.get("accounting", ""):
        fail("zone definitions must document non-exclusive mask accounting")

    reported = audit.get("reportedStats", {})
    if reported.get("threshold") != SUPPORT_THRESHOLD:
        fail("reported stats threshold changed")
    if reported.get("gpuSimilarity") >= SUPPORT_THRESHOLD:
        fail("GPU similarity unexpectedly reaches support threshold")
    if reported.get("cpuSimilarity") >= SUPPORT_THRESHOLD:
        fail("CPU similarity unexpectedly reaches strict support threshold")

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
    if strict.get("cropFallbackPreserved") != CROP_FALLBACK_REASON:
        fail("Crop fallback preservation mismatch")

    comparisons = {item.get("comparison"): item for item in audit.get("comparisons", [])}
    gpu = comparisons.get("gpu_vs_reference")
    cpu = comparisons.get("cpu_vs_reference")
    if not isinstance(gpu, dict) or not isinstance(cpu, dict):
        fail("missing GPU/CPU comparisons")
    if gpu.get("dominantGreaterThanThirtyTwoZone") != "outside_clip_removed_difference_oval":
        fail("GPU high deltas must be dominated by removed difference oval")
    if gpu.get("dominantGreaterThanThirtyTwoZoneShare", 0) < 90.0:
        fail("GPU removed-oval high-delta share must stay above 90%")
    if cpu.get("dominantGreaterThanThirtyTwoZone") != "blurred_content_envelope":
        fail("CPU high deltas must be dominated by blurred content envelope")

    for comparison in (gpu, cpu):
        samples = comparison.get("highDeltaSamples")
        if not isinstance(samples, list) or not samples:
            fail(f"{comparison.get('comparison')} must include highDeltaSamples")
        if len(samples) > 16:
            fail(f"{comparison.get('comparison')} highDeltaSamples must be bounded")
        zones = comparison.get("zones")
        if not isinstance(zones, dict):
            fail(f"{comparison.get('comparison')} missing zones")
        for zone in (
            "intersected_rectangles",
            "outside_clip_removed_difference_oval",
            "intersected_rectangles_surviving_difference",
            "difference_rrect_oval_boundary",
            "blurred_content_envelope",
            "outside_draw_oval",
        ):
            if zone not in zones:
                fail(f"{comparison.get('comparison')} missing zone `{zone}`")

    require_text(
        SOURCE_REPORT,
        [
            SCENE_ID,
            GPU_ROUTE,
            FALLBACK_REASON,
            "expected-unsupported",
        ],
    )
    require_text(
        TEST,
        [
            "GPU_SUPPORT_THRESHOLD = 99.95",
            "gpuCmp.similarity < GPU_SUPPORT_THRESHOLD",
            "coverage.nested-clip-visual-parity-below-threshold",
        ],
    )
    require_text(
        REPORT,
        [
            "FOR-269 Nested RRect Zone/Mask Audit",
            "Decision: `KEEP_EXPECTED_UNSUPPORTED`",
            "Next action: `BOUNDED_CLIP_MASK_CORRECTION_FIRST`",
            FALLBACK_REASON,
            CROP_FALLBACK_REASON,
            "non-exclusive",
            AUDIT_NAME,
        ],
    )


def main() -> None:
    audit = generate_audit()
    SCENE_DIR.mkdir(parents=True, exist_ok=True)
    AUDIT.write_text(json_dump(audit))
    write_report(audit)
    validate_audit(load_json(AUDIT))
    print(
        "FOR-269 validation passed: nested RRect clip residual is isolated by "
        "zone/mask, GPU high deltas are dominated by the removed difference oval, "
        "CPU residual remains in the blurred envelope, and support stays expected-unsupported."
    )


if __name__ == "__main__":
    main()
