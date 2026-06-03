#!/usr/bin/env python3
"""Generate and validate FOR-278 M60 boundary layer composite evidence."""

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


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-278"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "m60-boundary-layer-composite-fixture-for278.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-278-m60-boundary-layer-composite-fixture.md"
SOURCE_FOR277 = SCENE_DIR / "m60-post-for276-cpu-residual-audit-for277.json"

PRIMARY_SUBZONES = (
    "draw_oval_outer_boundary",
    "difference_oval_inner_boundary",
    "halo_interior",
)
COMPARISONS = (
    ("cpu_vs_reference", "reference", "cpu"),
    ("gpu_vs_reference", "reference", "gpu"),
    ("gpu_vs_cpu", "cpu", "gpu"),
)
WINDOW_RADIUS = 5


def fail(message: str) -> None:
    raise SystemExit(f"FOR-278 validation failed: {message}")


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


def comparison_by_name(audit: dict[str, Any], name: str) -> dict[str, Any]:
    for item in audit.get("comparisons", []):
        if item.get("comparison") == name:
            return item
    fail(f"missing comparison `{name}`")


def mean_rgba(image: np.ndarray, mask: np.ndarray) -> list[float]:
    if not mask.any():
        return [0.0, 0.0, 0.0, 0.0]
    return [rounded(image[:, :, channel][mask].mean(), 3) for channel in range(4)]


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


def bounds_for_center(x: int, y: int, width: int, height: int) -> dict[str, int]:
    return {
        "left": max(0, x - WINDOW_RADIUS),
        "top": max(0, y - WINDOW_RADIUS),
        "right": min(width, x + WINDOW_RADIUS + 1),
        "bottom": min(height, y + WINDOW_RADIUS + 1),
    }


def mask_from_bounds(bounds: dict[str, int], height: int, width: int) -> np.ndarray:
    mask = np.zeros((height, width), dtype=bool)
    mask[bounds["top"] : bounds["bottom"], bounds["left"] : bounds["right"]] = True
    return mask


def select_anchor(subzone: str, reference: np.ndarray, cpu: np.ndarray, masks: dict[str, np.ndarray]) -> dict[str, Any]:
    diff = np.abs(cpu - reference).max(axis=2)
    candidate_mask = masks[subzone] & (diff > 32)
    ys, xs = np.nonzero(candidate_mask)
    if xs.size == 0:
        fail(f"no CPU/reference >32 anchor in `{subzone}`")
    records = sorted((-int(diff[y, x]), int(y), int(x)) for y, x in zip(ys.tolist(), xs.tolist()))
    neg_delta, y, x = records[0]
    return {"x": x, "y": y, "cpuReferenceMaxChannelDelta": -neg_delta}


def comparison_metrics(
    *,
    name: str,
    base_label: str,
    actual_label: str,
    images: dict[str, np.ndarray],
    mask: np.ndarray,
) -> dict[str, Any]:
    base = images[base_label]
    actual = images[actual_label]
    diff = np.abs(actual - base).max(axis=2)
    gt32 = mask & (diff > 32)
    exact = mask & (diff == 0)
    gt32_count = int(gt32.sum())
    actual_buckets = for272.color_buckets(actual, gt32)
    base_buckets = for272.color_buckets(base, gt32)
    return {
        "comparison": name,
        "base": base_label,
        "actual": actual_label,
        "pixels": int(mask.sum()),
        "exactMatchingPixels": int(exact.sum()),
        "greaterThanThirtyTwoPixels": gt32_count,
        "maxChannelDelta": int(diff[mask].max()) if mask.any() else 0,
        "meanBaseRgbaOnGt32": mean_rgba(base, gt32),
        "meanActualRgbaOnGt32": mean_rgba(actual, gt32),
        "signedOnGt32": signed_summary(base, actual, gt32),
        "baseColorBucketsOnGt32": base_buckets,
        "actualColorBucketsOnGt32": actual_buckets,
        "actualWhiteLayerShareOfGt32": rounded(
            actual_buckets.get("white_or_layer_background", 0) / max(1, gt32_count) * 100.0
        ),
        "baseRedTintShareOfGt32": rounded(
            base_buckets.get("red_tinted_reference_like", 0) / max(1, gt32_count) * 100.0
        ),
    }


def pixel_sample(
    *,
    subzone: str,
    anchor: dict[str, Any],
    images: dict[str, np.ndarray],
) -> dict[str, Any]:
    x = anchor["x"]
    y = anchor["y"]
    reference = images["reference"][y, x].astype(int)
    cpu = images["cpu"][y, x].astype(int)
    gpu = images["gpu"][y, x].astype(int)
    return {
        "x": x,
        "y": y,
        "subzone": subzone,
        "referenceRgba": reference.tolist(),
        "cpuRgba": cpu.tolist(),
        "gpuRgba": gpu.tolist(),
        "cpuMinusReferenceSignedRgba": (cpu - reference).tolist(),
        "gpuMinusReferenceSignedRgba": (gpu - reference).tolist(),
        "gpuMinusCpuSignedRgba": (gpu - cpu).tolist(),
        "cpuReferenceMaxChannelDelta": anchor["cpuReferenceMaxChannelDelta"],
    }


def build_windows(images: dict[str, np.ndarray], masks: dict[str, np.ndarray]) -> tuple[list[dict[str, Any]], np.ndarray]:
    height, width, _ = images["reference"].shape
    combined_mask = np.zeros((height, width), dtype=bool)
    windows = []
    for subzone in PRIMARY_SUBZONES:
        anchor = select_anchor(subzone, images["reference"], images["cpu"], masks)
        bounds = bounds_for_center(anchor["x"], anchor["y"], width, height)
        window_mask = mask_from_bounds(bounds, height, width) & masks[subzone]
        if not window_mask.any():
            fail(f"empty FOR-278 window for `{subzone}`")
        combined_mask |= window_mask
        windows.append(
            {
                "name": f"{subzone}_max_cpu_reference_delta_window",
                "subzone": subzone,
                "bounds": bounds,
                "windowRadius": WINDOW_RADIUS,
                "anchor": anchor,
                "maskPixels": int(window_mask.sum()),
                "comparisons": [
                    comparison_metrics(
                        name=name,
                        base_label=base_label,
                        actual_label=actual_label,
                        images=images,
                        mask=window_mask,
                    )
                    for name, base_label, actual_label in COMPARISONS
                ],
                "anchorSample": pixel_sample(subzone=subzone, anchor=anchor, images=images),
            }
        )
    return windows, combined_mask


def classify(combined: dict[str, Any]) -> dict[str, Any]:
    cpu = comparison_by_name(combined, "cpu_vs_reference")
    gpu = comparison_by_name(combined, "gpu_vs_reference")
    cpu_white = cpu["actualColorBucketsOnGt32"].get("white_or_layer_background", 0)
    ref_red = cpu["baseColorBucketsOnGt32"].get("red_tinted_reference_like", 0)
    alpha_gt32 = cpu["signedOnGt32"]["alphaGreaterThanThirtyTwoPixels"]
    if (
        cpu["greaterThanThirtyTwoPixels"] == 89
        and cpu_white >= 78
        and ref_red >= 56
        and alpha_gt32 == 0
        and gpu["greaterThanThirtyTwoPixels"] < cpu["greaterThanThirtyTwoPixels"] // 4
    ):
        dominant = "MINIMIZED_BOUNDARY_LAYER_COMPOSITE_RESIDUAL_ISOLATED"
    else:
        dominant = "BOUNDARY_LAYER_COMPOSITE_RESIDUAL_NOT_YET_MINIMIZED"
    return {
        "dominantClassification": dominant,
        "fixtureReproducesFor277Finding": dominant == "MINIMIZED_BOUNDARY_LAYER_COMPOSITE_RESIDUAL_ISOLATED",
        "combinedCpuReferenceGreaterThanThirtyTwoPixels": cpu["greaterThanThirtyTwoPixels"],
        "combinedGpuReferenceGreaterThanThirtyTwoPixels": gpu["greaterThanThirtyTwoPixels"],
        "combinedCpuActualWhiteLayerPixels": cpu_white,
        "combinedCpuActualWhiteLayerShare": rounded(cpu_white / max(1, cpu["greaterThanThirtyTwoPixels"]) * 100.0),
        "combinedReferenceRedTintPixels": ref_red,
        "combinedReferenceRedTintShare": rounded(ref_red / max(1, cpu["greaterThanThirtyTwoPixels"]) * 100.0),
        "combinedCpuReferenceAlphaGreaterThanThirtyTwoPixels": alpha_gt32,
        "interpretation": (
            "A three-window crop around the strongest FOR-277 boundary pixels keeps the same polarity: "
            "CPU stores white/layer-background RGB while the Skia reference keeps red-tinted blur payload, "
            "with no alpha >32 delta. GPU is much closer to reference in these windows."
        ),
        "nextAction": "TARGET_CPU_LAYER_BACKGROUND_COMPOSITE_AROUND_DIFFERENCE_CLIP_BOUNDARY",
    }


def generate_audit() -> dict[str, Any]:
    source_for277 = load_json(SOURCE_FOR277)
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
    windows, combined_mask = build_windows(images, masks)
    combined = {
        "maskPixels": int(combined_mask.sum()),
        "comparisons": [
            comparison_metrics(
                name=name,
                base_label=base_label,
                actual_label=actual_label,
                images=images,
                mask=combined_mask,
            )
            for name, base_label, actual_label in COMPARISONS
        ],
    }
    classification = classify(combined)
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-boundary-layer-composite-fixture",
        "sceneId": SCENE_ID,
        "fixtureKind": "cropped-full-scene-boundary-window-audit",
        "backend": "Reference/CPU/WebGPU",
        "sourceAudit": str(SOURCE_FOR277.relative_to(PROJECT_ROOT)),
        "sourceImages": {
            "reference": str((SCENE_DIR / "skia.png").relative_to(PROJECT_ROOT)),
            "cpu": str((SCENE_DIR / "cpu.png").relative_to(PROJECT_ROOT)),
            "gpu": str((SCENE_DIR / "gpu.png").relative_to(PROJECT_ROOT)),
        },
        "sourceTest": "org.skia.gpu.webgpu.NestedClipSceneCaptureTest",
        "artifactRoot": str(SCENE_DIR.relative_to(PROJECT_ROOT)),
        "supportThreshold": for269.SUPPORT_THRESHOLD,
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED",
        "dominantFinding": classification["dominantClassification"],
        "decisionRationale": (
            "FOR-278 isolates the FOR-277 residual into three 11x11 boundary windows clipped to their "
            "diagnostic subzones. The minimized audit retains CPU white/layer RGB over red-tinted reference "
            "payload with unchanged alpha, so it is sufficient to drive a later CPU composite correction."
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
        "sourceFor277Classification": source_for277.get("classification", {}),
        "geometry": geometry,
        "windows": windows,
        "combinedBoundaryFixture": combined,
        "classification": classification,
        "nextAction": classification["nextAction"],
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
    cpu = comparison_by_name(window, "cpu_vs_reference")
    gpu = comparison_by_name(window, "gpu_vs_reference")
    sample = window["anchorSample"]
    return (
        f"| `{window['subzone']}` | `{window['bounds']}` | {window['maskPixels']} | "
        f"{cpu['greaterThanThirtyTwoPixels']} | {gpu['greaterThanThirtyTwoPixels']} | "
        f"{cpu['actualWhiteLayerShareOfGt32']}% | {cpu['baseRedTintShareOfGt32']}% | "
        f"`{sample['cpuMinusReferenceSignedRgba']}` |"
    )


def write_report(audit: dict[str, Any]) -> None:
    combined = audit["combinedBoundaryFixture"]
    cpu = comparison_by_name(combined, "cpu_vs_reference")
    gpu = comparison_by_name(combined, "gpu_vs_reference")
    classification = audit["classification"]
    route = audit["route"]
    rows = "\n".join(window_row(window) for window in audit["windows"])
    samples = "\n".join(
        "| `{subzone}` | {x},{y} | `{referenceRgba}` | `{cpuRgba}` | `{gpuRgba}` | `{cpuMinusReferenceSignedRgba}` | `{gpuMinusReferenceSignedRgba}` |".format(
            **window["anchorSample"]
        )
        for window in audit["windows"]
    )
    report = f"""# FOR-278 M60 Boundary Layer Composite Fixture

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["supportDecision"]}`

Dominant finding: `{audit["dominantFinding"]}`.

FOR-278 extrait une fixture d'audit minimale depuis les PNG reference/CPU/GPU
M60 existants. Elle ne modifie aucun renderer: trois fenetres 11x11 sont
centrees sur les pixels CPU/reference les plus divergents des zones FOR-277
`draw_oval_outer_boundary`, `difference_oval_inner_boundary` et
`halo_interior`, puis intersectees avec leurs masques de zone.

## Resultat Court

| Mesure | Valeur |
|---|---:|
| Pixels de fixture combines | {combined["maskPixels"]} |
| CPU/reference >32 pixels | {cpu["greaterThanThirtyTwoPixels"]} |
| CPU/reference max channel delta | {cpu["maxChannelDelta"]} |
| CPU blanc/layer sur >32 | {classification["combinedCpuActualWhiteLayerPixels"]} |
| CPU blanc/layer share | {classification["combinedCpuActualWhiteLayerShare"]}% |
| Reference rouge teintee sur >32 | {classification["combinedReferenceRedTintPixels"]} |
| Reference rouge teintee share | {classification["combinedReferenceRedTintShare"]}% |
| CPU alpha >32 | {classification["combinedCpuReferenceAlphaGreaterThanThirtyTwoPixels"]} |
| GPU/reference >32 pixels | {gpu["greaterThanThirtyTwoPixels"]} |
| GPU/reference max channel delta | {gpu["maxChannelDelta"]} |

La fixture reproduit le signal FOR-277: le CPU conserve du blanc/fond ou layer
la ou la reference garde une charge rouge teintee, sans delta alpha >32. Le GPU
reste beaucoup plus proche de la reference sur ces fenetres.

## Fenetres De Bord

| Zone | Bounds | Pixels | CPU/ref >32 | GPU/ref >32 | CPU blanc/layer | Ref rouge | CPU-ref sample signe |
|---|---|---:|---:|---:|---:|---:|---|
{rows}

## Echantillons Signes

| Zone | Pixel | Reference RGBA | CPU RGBA | GPU RGBA | CPU-reference signe | GPU-reference signe |
|---|---|---|---|---|---|---|
{samples}

## Classification

`{classification["dominantClassification"]}`.

{classification["interpretation"]}

## Decision

Conserver `expected-unsupported` avec fallback
`{route["fallbackReason"]}`. Preserver
`{route["cropFallbackPreserved"]}`. Aucun seuil n'est affaibli et aucun
support large clip-stack, readback/fallback, Ganesh, Graphite ou compilateur
SkSL n'est ajoute.

## Prochaine Action

`{audit["nextAction"]}`.

La prochaine correction doit cibler le composite CPU fond/layer autour du bord
`clipRRect(kDifference)` + blur SrcIn, puis verifier que ces fenetres et la
scene complete M60 bougent avant toute promotion.

## Validation

```text
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
    if audit.get("fixtureKind") != "cropped-full-scene-boundary-window-audit":
        fail("fixture kind mismatch")
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision must remain expected-unsupported")
    if audit.get("dominantFinding") != "MINIMIZED_BOUNDARY_LAYER_COMPOSITE_RESIDUAL_ISOLATED":
        fail("dominant finding mismatch")

    route = audit.get("route", {})
    if route.get("cpu") != for269.CPU_ROUTE:
        fail("CPU route mismatch")
    if route.get("gpu") != for269.GPU_ROUTE:
        fail("GPU route mismatch")
    if route.get("gpuStatus") != "expected-unsupported":
        fail("GPU status must remain expected-unsupported")
    if route.get("fallbackReason") != for269.FALLBACK_REASON:
        fail("visual parity fallback changed")
    if route.get("cropFallbackPreserved") != for269.CROP_FALLBACK_REASON:
        fail("crop fallback changed")

    source_classification = audit.get("sourceFor277Classification", {})
    if source_classification.get("primarySubzoneGreaterThanThirtyTwoPixels") != 15726:
        fail("source FOR-277 CPU residual changed")
    if source_classification.get("primaryWhiteLayerPixels") != 13518:
        fail("source FOR-277 white/layer count changed")

    windows = audit.get("windows", [])
    if len(windows) != 3:
        fail("expected three FOR-278 boundary windows")
    expected_windows = {
        "draw_oval_outer_boundary": {
            "anchor": {"x": 99, "y": 89, "cpuReferenceMaxChannelDelta": 224},
            "bounds": {"left": 94, "top": 84, "right": 105, "bottom": 95},
            "maskPixels": 59,
            "cpuGt32": 59,
            "gpuGt32": 11,
            "cpuWhite": 59,
            "refRed": 32,
        },
        "difference_oval_inner_boundary": {
            "anchor": {"x": 262, "y": 17, "cpuReferenceMaxChannelDelta": 237},
            "bounds": {"left": 257, "top": 12, "right": 268, "bottom": 23},
            "maskPixels": 67,
            "cpuGt32": 18,
            "gpuGt32": 0,
            "cpuWhite": 14,
            "refRed": 12,
        },
        "halo_interior": {
            "anchor": {"x": 226, "y": 21, "cpuReferenceMaxChannelDelta": 235},
            "bounds": {"left": 221, "top": 16, "right": 232, "bottom": 27},
            "maskPixels": 22,
            "cpuGt32": 12,
            "gpuGt32": 0,
            "cpuWhite": 5,
            "refRed": 12,
        },
    }
    for window in windows:
        subzone = window.get("subzone")
        expected = expected_windows.get(subzone)
        if expected is None:
            fail(f"unexpected FOR-278 window `{subzone}`")
        if window.get("anchor") != expected["anchor"]:
            fail(f"{subzone} anchor changed")
        if window.get("bounds") != expected["bounds"]:
            fail(f"{subzone} bounds changed")
        if window.get("maskPixels") != expected["maskPixels"]:
            fail(f"{subzone} mask pixel count changed")
        cpu = comparison_by_name(window, "cpu_vs_reference")
        gpu = comparison_by_name(window, "gpu_vs_reference")
        if cpu["greaterThanThirtyTwoPixels"] != expected["cpuGt32"]:
            fail(f"{subzone} CPU/reference >32 count changed")
        if gpu["greaterThanThirtyTwoPixels"] != expected["gpuGt32"]:
            fail(f"{subzone} GPU/reference >32 count changed")
        if cpu["actualColorBucketsOnGt32"]["white_or_layer_background"] != expected["cpuWhite"]:
            fail(f"{subzone} CPU white/layer count changed")
        if cpu["baseColorBucketsOnGt32"]["red_tinted_reference_like"] != expected["refRed"]:
            fail(f"{subzone} reference red tint count changed")
        if cpu["signedOnGt32"]["alphaGreaterThanThirtyTwoPixels"] != 0:
            fail(f"{subzone} CPU/reference alpha >32 must remain zero")

    combined = audit.get("combinedBoundaryFixture", {})
    if combined.get("maskPixels") != 148:
        fail("combined fixture mask pixel count changed")
    cpu = comparison_by_name(combined, "cpu_vs_reference")
    gpu = comparison_by_name(combined, "gpu_vs_reference")
    gpu_cpu = comparison_by_name(combined, "gpu_vs_cpu")
    if cpu["greaterThanThirtyTwoPixels"] != 89:
        fail("combined CPU/reference >32 count changed")
    if cpu["maxChannelDelta"] != 237:
        fail("combined CPU/reference max channel delta changed")
    if cpu["actualColorBucketsOnGt32"]["white_or_layer_background"] != 78:
        fail("combined CPU white/layer count changed")
    if cpu["baseColorBucketsOnGt32"]["red_tinted_reference_like"] != 56:
        fail("combined reference red tint count changed")
    if cpu["signedOnGt32"]["alphaGreaterThanThirtyTwoPixels"] != 0:
        fail("combined CPU/reference alpha >32 must remain zero")
    if gpu["greaterThanThirtyTwoPixels"] != 11:
        fail("combined GPU/reference >32 count changed")
    if gpu["maxChannelDelta"] != 41:
        fail("combined GPU/reference max channel delta changed")
    if gpu_cpu["greaterThanThirtyTwoPixels"] != 82:
        fail("combined GPU/CPU >32 count changed")

    classification = audit.get("classification", {})
    if classification.get("fixtureReproducesFor277Finding") is not True:
        fail("fixture must reproduce FOR-277 finding")
    if classification.get("combinedCpuActualWhiteLayerShare") != 87.640449:
        fail("combined white/layer share changed")
    if classification.get("combinedReferenceRedTintShare") != 62.921348:
        fail("combined red tint share changed")
    if classification.get("combinedCpuReferenceAlphaGreaterThanThirtyTwoPixels") != 0:
        fail("classification alpha >32 changed")
    if audit.get("nextAction") != "TARGET_CPU_LAYER_BACKGROUND_COMPOSITE_AROUND_DIFFERENCE_CLIP_BOUNDARY":
        fail("next action changed")

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
        "FOR-278 M60 Boundary Layer Composite Fixture",
        "MINIMIZED_BOUNDARY_LAYER_COMPOSITE_RESIDUAL_ISOLATED",
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
    classification = audit["classification"]
    print(
        "FOR-278 validation passed: "
        f"{classification['combinedCpuReferenceGreaterThanThirtyTwoPixels']} CPU/reference >32 pixels "
        f"in {audit['combinedBoundaryFixture']['maskPixels']} boundary-fixture pixels; "
        f"{classification['combinedCpuActualWhiteLayerShare']}% CPU white/layer, "
        f"{classification['combinedCpuReferenceAlphaGreaterThanThirtyTwoPixels']} alpha >32."
    )


if __name__ == "__main__":
    main()
