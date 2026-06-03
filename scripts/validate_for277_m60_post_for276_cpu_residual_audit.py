#!/usr/bin/env python3
"""Generate and validate FOR-277 post-FOR-276 M60 CPU residual evidence."""

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
LINEAR_ID = "FOR-277"
PARENT_ID = "FOR-241"
SCENE_ID = for269.SCENE_ID
SCENE_DIR = for269.SCENE_DIR
AUDIT_NAME = "m60-post-for276-cpu-residual-audit-for277.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-277-m60-post-for276-cpu-residual-audit.md"
SOURCE_FOR274 = SCENE_DIR / "nested-rrect-cpu-reference-layer-mask-audit-for274.json"
SOURCE_FOR276 = SCENE_DIR / "cpu-mask-filter-clip-order-for276.json"

PRIMARY_SUBZONES = (
    "draw_oval_outer_boundary",
    "difference_oval_inner_boundary",
    "halo_interior",
)
DIAGNOSTIC_SUBZONES = PRIMARY_SUBZONES + (
    "removed_difference_oval_interior",
    "outside_draw_oval",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-277 validation failed: {message}")


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


def signed_rgb_summary(base: np.ndarray, actual: np.ndarray, mask: np.ndarray) -> dict[str, Any]:
    signed = actual - base
    if not mask.any():
        return {
            "meanSignedRgb": [0.0, 0.0, 0.0],
            "minSignedRgb": [0, 0, 0],
            "maxSignedRgb": [0, 0, 0],
            "alphaGreaterThanThirtyTwoPixels": 0,
        }
    alpha_delta = np.abs(signed[:, :, 3])
    return {
        "meanSignedRgb": [rounded(signed[:, :, channel][mask].mean(), 3) for channel in range(3)],
        "minSignedRgb": [int(signed[:, :, channel][mask].min()) for channel in range(3)],
        "maxSignedRgb": [int(signed[:, :, channel][mask].max()) for channel in range(3)],
        "alphaGreaterThanThirtyTwoPixels": int((alpha_delta[mask] > 32).sum()),
    }


def signed_samples(
    *,
    subzone: str,
    reference: np.ndarray,
    cpu: np.ndarray,
    gpu: np.ndarray,
    masks: dict[str, np.ndarray],
    limit: int = 3,
) -> list[dict[str, Any]]:
    cpu_ref_diff = np.abs(cpu - reference).max(axis=2)
    gpu_ref_diff = np.abs(gpu - reference).max(axis=2)
    sample_mask = masks[subzone] & (cpu_ref_diff > 32)
    ys, xs = np.nonzero(sample_mask)
    records = sorted((-int(cpu_ref_diff[y, x]), int(y), int(x)) for y, x in zip(ys.tolist(), xs.tolist()))
    samples: list[dict[str, Any]] = []
    for neg_delta, y, x in records[:limit]:
        ref_px = reference[y, x].astype(int)
        cpu_px = cpu[y, x].astype(int)
        gpu_px = gpu[y, x].astype(int)
        samples.append(
            {
                "x": x,
                "y": y,
                "subzone": subzone,
                "referenceRgba": ref_px.tolist(),
                "cpuRgba": cpu_px.tolist(),
                "gpuRgba": gpu_px.tolist(),
                "cpuMinusReferenceSignedRgba": (cpu_px - ref_px).tolist(),
                "gpuMinusReferenceSignedRgba": (gpu_px - ref_px).tolist(),
                "cpuReferenceMaxChannelDelta": int(cpu_ref_diff[y, x]),
                "gpuReferenceMaxChannelDelta": int(gpu_ref_diff[y, x]),
            }
        )
    return samples


def comparison_metrics(
    *,
    name: str,
    base_label: str,
    actual_label: str,
    images: dict[str, np.ndarray],
    masks: dict[str, np.ndarray],
) -> dict[str, Any]:
    base = images[base_label]
    actual = images[actual_label]
    diff = np.abs(actual - base).max(axis=2)
    total_gt32 = int((diff > 32).sum())
    subzones: dict[str, Any] = {}
    for subzone in DIAGNOSTIC_SUBZONES:
        zone_mask = masks[subzone]
        gt32 = zone_mask & (diff > 32)
        actual_buckets = for272.color_buckets(actual, gt32)
        base_buckets = for272.color_buckets(base, gt32)
        gt32_count = int(gt32.sum())
        white_count = actual_buckets.get("white_or_layer_background", 0)
        red_ref_count = base_buckets.get("red_tinted_reference_like", 0)
        subzones[subzone] = {
            "pixels": int(zone_mask.sum()),
            "greaterThanThirtyTwoPixels": gt32_count,
            "shareOfComparisonGreaterThanThirtyTwo": rounded(gt32_count / max(1, total_gt32) * 100.0),
            "maxChannelDelta": int(diff[zone_mask].max()) if zone_mask.any() else 0,
            "meanBaseRgbaOnGt32": for272.mean_rgba(base, gt32),
            "meanActualRgbaOnGt32": for272.mean_rgba(actual, gt32),
            "signedRgbOnGt32": signed_rgb_summary(base, actual, gt32),
            "baseColorBucketsOnGt32": base_buckets,
            "actualColorBucketsOnGt32": actual_buckets,
            "actualWhiteLayerShareOfSubzoneGt32": rounded(white_count / max(1, gt32_count) * 100.0),
            "baseRedTintShareOfSubzoneGt32": rounded(red_ref_count / max(1, gt32_count) * 100.0),
        }
    return {
        "comparison": name,
        "base": base_label,
        "actual": actual_label,
        "greaterThanThirtyTwoPixels": total_gt32,
        "maxChannelDelta": int(diff.max()),
        "blurredEnvelopeGreaterThanThirtyTwoPixels": int(((diff > 32) & masks["blurred_content_envelope"]).sum()),
        "exactMatchingPixels": int((diff == 0).sum()),
        "exactSimilarity": rounded(int((diff == 0).sum()) / diff.size * 100.0),
        "subzones": subzones,
    }


def classify_cpu_residual(cpu: dict[str, Any], source_for276: dict[str, Any]) -> dict[str, Any]:
    primary_gt32 = sum(cpu["subzones"][name]["greaterThanThirtyTwoPixels"] for name in PRIMARY_SUBZONES)
    primary_white = sum(
        cpu["subzones"][name]["actualColorBucketsOnGt32"]["white_or_layer_background"]
        for name in PRIMARY_SUBZONES
    )
    primary_red_ref = sum(
        cpu["subzones"][name]["baseColorBucketsOnGt32"]["red_tinted_reference_like"]
        for name in PRIMARY_SUBZONES
    )
    alpha_gt32 = sum(
        cpu["subzones"][name]["signedRgbOnGt32"]["alphaGreaterThanThirtyTwoPixels"]
        for name in PRIMARY_SUBZONES
    )
    recovered = source_for276["maskFilterClipOrder"]["recoveredShareOfUnclippedSupport"]
    if primary_gt32 == cpu["greaterThanThirtyTwoPixels"] and primary_white / max(1, primary_gt32) >= 0.80 and alpha_gt32 == 0:
        dominant = "LAYER_BACKGROUND_BOUNDARY_RESIDUAL_REMAINS_DOMINANT_AFTER_FOR276"
    else:
        dominant = "MIXED_BOUNDARY_RESIDUAL_REQUIRES_NEW_MINIMIZED_FIXTURE"
    return {
        "dominantClassification": dominant,
        "primarySubzoneGreaterThanThirtyTwoPixels": primary_gt32,
        "primarySubzoneShareOfCpuReferenceGreaterThanThirtyTwo": rounded(
            primary_gt32 / max(1, cpu["greaterThanThirtyTwoPixels"]) * 100.0
        ),
        "primaryWhiteLayerPixels": primary_white,
        "primaryWhiteLayerShare": rounded(primary_white / max(1, primary_gt32) * 100.0),
        "primaryReferenceRedTintPixels": primary_red_ref,
        "primaryReferenceRedTintShare": rounded(primary_red_ref / max(1, primary_gt32) * 100.0),
        "primaryAlphaGreaterThanThirtyTwoPixels": alpha_gt32,
        "for276FixtureRecoveredShare": recovered,
        "for276Relation": (
            "FOR-276 proves that active-AA source-mask pre-clipping can lose a bounded halo, "
            "but the regenerated full scene keeps the same CPU/reference residual. "
            "Therefore that source-clip ordering issue is not sufficient to explain M60."
        ),
        "hypothesisRanking": [
            {
                "hypothesis": "layer_background_or_final_boundary_composite",
                "classification": "DOMINANT",
                "evidence": "CPU/reference >32 pixels are all in primary boundary/halo subzones; 85%+ are white/layer actual pixels with unchanged alpha.",
            },
            {
                "hypothesis": "mask_extent_or_source_clip_order",
                "classification": "REDUCED_AFTER_FOR276",
                "evidence": "FOR-276 recovers 80% in the bounded AA fixture, but current full-scene CPU/reference counters are unchanged from FOR-274.",
            },
            {
                "hypothesis": "color_payload",
                "classification": "NOT_PRIMARY_FOR_CPU",
                "evidence": "CPU pixels are white/layer where reference is red-tinted; alpha is unchanged, and FOR-273 already removed the dominant GPU color payload issue.",
            },
            {
                "hypothesis": "reference_divergence",
                "classification": "UNPROVEN",
                "evidence": "The Skia reference remains internally consistent with red-tinted blur boundary pixels; no minimized refusal evidence exists.",
            },
        ],
    }


def delta_from_for274(current: dict[str, Any], source_for274: dict[str, Any], comparison_name: str) -> dict[str, Any]:
    current_cmp = comparison_by_name(current, comparison_name)
    source_cmp = comparison_by_name(source_for274, comparison_name)
    subzones = {}
    for name in DIAGNOSTIC_SUBZONES:
        now = current_cmp["subzones"][name]
        before = source_cmp["subzones"][name]
        subzones[name] = {
            "greaterThanThirtyTwoPixelsDelta": now["greaterThanThirtyTwoPixels"] - before["greaterThanThirtyTwoPixels"],
            "whiteLayerPixelsDelta": now["actualColorBucketsOnGt32"]["white_or_layer_background"]
            - before["actualColorBucketsOnGt32"]["white_or_layer_background"],
            "alphaGreaterThanThirtyTwoPixelsDelta": now["signedRgbOnGt32"]["alphaGreaterThanThirtyTwoPixels"]
            - before["signedRgbOnGt32"]["alphaGreaterThanThirtyTwoPixels"],
        }
    return {
        "comparison": comparison_name,
        "greaterThanThirtyTwoPixelsDelta": current_cmp["greaterThanThirtyTwoPixels"]
        - source_cmp["greaterThanThirtyTwoPixels"],
        "maxChannelDeltaDelta": current_cmp["maxChannelDelta"] - source_cmp["maxChannelDelta"],
        "subzones": subzones,
    }


def generate_audit() -> dict[str, Any]:
    source_for274 = load_json(SOURCE_FOR274)
    source_for276 = load_json(SOURCE_FOR276)
    stats = load_json(SCENE_DIR / "stats.json")
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
    comparisons = [
        comparison_metrics(
            name="cpu_vs_reference",
            base_label="reference",
            actual_label="cpu",
            images=images,
            masks=masks,
        ),
        comparison_metrics(
            name="gpu_vs_reference",
            base_label="reference",
            actual_label="gpu",
            images=images,
            masks=masks,
        ),
        comparison_metrics(
            name="gpu_vs_cpu",
            base_label="cpu",
            actual_label="gpu",
            images=images,
            masks=masks,
        ),
    ]
    current = {"comparisons": comparisons}
    cpu = comparison_by_name(current, "cpu_vs_reference")
    gpu = comparison_by_name(current, "gpu_vs_reference")
    classification = classify_cpu_residual(cpu, source_for276)
    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-post-for276-cpu-residual-audit",
        "sceneId": SCENE_ID,
        "backend": "Reference/CPU/WebGPU",
        "sourceAudits": {
            "for274": str(SOURCE_FOR274.relative_to(PROJECT_ROOT)),
            "for276": str(SOURCE_FOR276.relative_to(PROJECT_ROOT)),
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
        "dominantFinding": classification["dominantClassification"],
        "decisionRationale": (
            "The post-FOR-276 full-scene CPU/reference residual is numerically unchanged from FOR-274. "
            "The bounded FOR-276 AA fixture recovered local blur halo, but M60 still has 15,726 CPU/reference "
            ">32 pixels, all in primary boundary/halo zones, with unchanged alpha and mostly white/layer CPU pixels."
        ),
        "nextAction": "CREATE_MINIMIZED_FULL_SCENE_BOUNDARY_LAYER_COMPOSITE_AUDIT_BEFORE_ANY_M60_PROMOTION",
        "route": {
            "cpu": route_cpu.get("selectedRoute"),
            "gpu": route_gpu.get("selectedRoute"),
            "gpuStatus": route_gpu.get("status"),
            "fallbackReason": route_gpu.get("fallbackReason"),
            "coverageStrategy": route_gpu.get("coverageStrategy"),
            "pipelineKey": route_gpu.get("pipelineKey"),
            "cropFallbackPreserved": for269.CROP_FALLBACK_REASON,
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
        "for276FixtureRelation": {
            "preFixClippedRedSupportInClip": source_for276["maskFilterClipOrder"]["preFixBaseline"][
                "clippedRedSupportInClip"
            ],
            "postFixClippedRedSupportInClip": source_for276["maskFilterClipOrder"]["clippedRedSupportInClip"],
            "preFixLostRedSupportInClip": source_for276["maskFilterClipOrder"]["preFixBaseline"]["lostRedSupportInClip"],
            "postFixLostRedSupportInClip": source_for276["maskFilterClipOrder"]["lostRedSupportInClip"],
            "postFixRecoveredShareOfUnclippedSupport": source_for276["maskFilterClipOrder"][
                "recoveredShareOfUnclippedSupport"
            ],
            "fullSceneCpuReferenceGt32DeltaVsFor274": 0,
            "interpretation": classification["for276Relation"],
        },
        "geometry": geometry,
        "classification": classification,
        "comparisons": comparisons,
        "deltaFromFor274": {
            "cpu_vs_reference": delta_from_for274(current, source_for274, "cpu_vs_reference"),
            "gpu_vs_reference": delta_from_for274(current, source_for274, "gpu_vs_reference"),
        },
        "signedCpuReferenceSamplesBySubzone": {
            subzone: signed_samples(
                subzone=subzone,
                reference=images["reference"],
                cpu=images["cpu"],
                gpu=images["gpu"],
                masks=masks,
            )
            for subzone in PRIMARY_SUBZONES
        },
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
    cpu = comparison_by_name(audit, "cpu_vs_reference")
    gpu = comparison_by_name(audit, "gpu_vs_reference")
    fixture = audit["for276FixtureRelation"]
    classification = audit["classification"]
    delta = audit["deltaFromFor274"]["cpu_vs_reference"]
    rows = []
    for subzone in PRIMARY_SUBZONES:
        cpu_zone = cpu["subzones"][subzone]
        gpu_zone = gpu["subzones"][subzone]
        rows.append(
            "| `{}` | {} | {} | {}% | {} | {} | `{}` |".format(
                subzone,
                cpu_zone["greaterThanThirtyTwoPixels"],
                gpu_zone["greaterThanThirtyTwoPixels"],
                cpu_zone["actualWhiteLayerShareOfSubzoneGt32"],
                cpu_zone["actualColorBucketsOnGt32"]["white_or_layer_background"],
                cpu_zone["signedRgbOnGt32"]["alphaGreaterThanThirtyTwoPixels"],
                cpu_zone["signedRgbOnGt32"]["meanSignedRgb"],
            )
        )
    sample_rows = []
    for subzone in PRIMARY_SUBZONES:
        for sample in audit["signedCpuReferenceSamplesBySubzone"][subzone][:2]:
            sample_rows.append(
                "| `{subzone}` | {x},{y} | `{referenceRgba}` | `{cpuRgba}` | `{gpuRgba}` | `{cpuMinusReferenceSignedRgba}` | `{gpuMinusReferenceSignedRgba}` |".format(
                    **sample
                )
            )

    report = f"""# FOR-277 M60 Post-FOR-276 CPU Residual Audit

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["supportDecision"]}`

Dominant finding: `{audit["dominantFinding"]}`.

FOR-277 relit la scene complete apres FOR-276 avec les masques de FOR-269,
FOR-271 et FOR-274. Aucun chemin renderer n'est modifie. Le but est de savoir
si la correction locale FOR-276 explique le residu CPU/reference de M60.

## Resultat Court

| Mesure | Valeur |
|---|---:|
| CPU/reference similarity | {audit["reportedStats"]["cpuSimilarity"]}% |
| CPU matching pixels | {audit["reportedStats"]["cpuMatchingPixels"]} |
| CPU max channel delta | {audit["reportedStats"]["cpuMaxChannelDelta"]} |
| CPU/reference >32 pixels | {cpu["greaterThanThirtyTwoPixels"]} |
| GPU/reference similarity | {audit["reportedStats"]["gpuSimilarity"]}% |
| GPU matching pixels | {audit["reportedStats"]["gpuMatchingPixels"]} |
| GPU max channel delta | {audit["reportedStats"]["gpuMaxChannelDelta"]} |
| GPU/reference >32 pixels | {gpu["greaterThanThirtyTwoPixels"]} |
| Seuil promotion | {audit["supportThreshold"]}% |

M60 reste `expected-unsupported`: CPU/reference et GPU/reference restent sous
le seuil strict de `{audit["supportThreshold"]}%`.

## Relation avec FOR-276

| Compteur fixture FOR-276 | Avant | Apres |
|---|---:|---:|
| Pixels rouges conserves dans le clip de halo | {fixture["preFixClippedRedSupportInClip"]} | {fixture["postFixClippedRedSupportInClip"]} |
| Pixels rouges perdus dans le clip de halo | {fixture["preFixLostRedSupportInClip"]} | {fixture["postFixLostRedSupportInClip"]} |
| Part recuperee | 0.0% | {fixture["postFixRecoveredShareOfUnclippedSupport"]}% |

La fixture bornee est valide, mais le residu pleine scene est numeriquement
inchange contre FOR-274: CPU/reference >32 delta
`{delta["greaterThanThirtyTwoPixelsDelta"]}`, CPU max-delta delta
`{delta["maxChannelDeltaDelta"]}`. FOR-276 ne suffit donc pas a expliquer M60.

## Zones Dominantes

| Zone | CPU/ref >32 | GPU/ref >32 | CPU blanc/layer | CPU pixels blancs | CPU alpha >32 | CPU signed RGB moyen |
|---|---:|---:|---:|---:|---:|---|
{chr(10).join(rows)}

Synthese CPU: les zones primaires portent
`{classification["primarySubzoneGreaterThanThirtyTwoPixels"]}` pixels >32,
soit `{classification["primarySubzoneShareOfCpuReferenceGreaterThanThirtyTwo"]}%`
du residu CPU/reference. Les pixels CPU blancs/layer representent
`{classification["primaryWhiteLayerShare"]}%` de ces zones, avec
`{classification["primaryAlphaGreaterThanThirtyTwoPixels"]}` pixel a alpha
>32. Le probleme reste donc RGB/fond, pas une perte d'alpha globale.

## Echantillons Signes

| Zone | Pixel | Reference RGBA | CPU RGBA | GPU RGBA | CPU-reference signe | GPU-reference signe |
|---|---|---|---|---|---|---|
{chr(10).join(sample_rows)}

## Classement des hypotheses

| Hypothese | Classement | Preuve simple |
|---|---|---|
| `layer_background_or_final_boundary_composite` | `DOMINANT` | CPU garde majoritairement du blanc/fond ou layer la ou la reference est rouge teintee, sans delta alpha >32. |
| `mask_extent_or_source_clip_order` | `REDUCED_AFTER_FOR276` | FOR-276 recupere 80% dans la fixture AA, mais les compteurs pleine scene restent identiques a FOR-274. |
| `color_payload` | `NOT_PRIMARY_FOR_CPU` | La polarite CPU est blanche/fond, pas une mauvaise charge rouge; le gros probleme GPU de couleur a deja ete reduit par FOR-273. |
| `reference_divergence` | `UNPROVEN` | Aucun cas minimal ne justifie encore un refus de la reference Skia. |

## Decision

Conserver `expected-unsupported` avec fallback
`{audit["route"]["fallbackReason"]}`. Preserver
`{audit["route"]["cropFallbackPreserved"]}`. Aucun seuil n'est affaibli et
aucun support large clip-stack, readback/fallback, Ganesh, Graphite ou
compilateur SkSL n'est ajoute.

## Prochaine Action

`{audit["nextAction"]}`.

La suite doit isoler le composite/fond de bord pleine scene, par exemple avec
une fixture minimale qui reproduit l'empilement saveLayer + difference oval +
blur SrcIn aux dimensions/bords M60, avant toute promotion.

## Validation

```text
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
    if audit.get("supportThreshold") != for269.SUPPORT_THRESHOLD:
        fail("support threshold changed")
    if audit.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED":
        fail("support decision must remain expected-unsupported")
    if audit.get("dominantFinding") != "LAYER_BACKGROUND_BOUNDARY_RESIDUAL_REMAINS_DOMINANT_AFTER_FOR276":
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

    reported = audit.get("reportedStats", {})
    expected_stats = {
        "cpuSimilarity": 97.31,
        "gpuSimilarity": 98.48,
        "threshold": for269.SUPPORT_THRESHOLD,
        "cpuMatchingPixels": 908439,
        "gpuMatchingPixels": 919363,
        "cpuMaxChannelDelta": 237,
        "gpuMaxChannelDelta": 57,
    }
    for key, expected in expected_stats.items():
        if reported.get(key) != expected:
            fail(f"reported stats `{key}` changed")
    if reported["cpuSimilarity"] >= for269.SUPPORT_THRESHOLD or reported["gpuSimilarity"] >= for269.SUPPORT_THRESHOLD:
        fail("M60 unexpectedly reaches support threshold")

    cpu = comparison_by_name(audit, "cpu_vs_reference")
    gpu = comparison_by_name(audit, "gpu_vs_reference")
    if cpu["greaterThanThirtyTwoPixels"] != 15726:
        fail("CPU/reference >32 residual changed")
    if gpu["greaterThanThirtyTwoPixels"] != 2869:
        fail("GPU/reference >32 residual changed")
    if cpu["maxChannelDelta"] != 237:
        fail("CPU/reference max channel delta changed")
    if gpu["maxChannelDelta"] != 57:
        fail("GPU/reference max channel delta changed")

    required_cpu = {
        "draw_oval_outer_boundary": (8077, 6157),
        "difference_oval_inner_boundary": (5201, 5001),
        "halo_interior": (2448, 2360),
    }
    required_gpu = {
        "draw_oval_outer_boundary": 2796,
        "difference_oval_inner_boundary": 73,
        "halo_interior": 0,
    }
    for subzone, (gt32, white) in required_cpu.items():
        zone = cpu["subzones"][subzone]
        if zone["greaterThanThirtyTwoPixels"] != gt32:
            fail(f"CPU/reference {subzone} >32 changed")
        if zone["actualColorBucketsOnGt32"]["white_or_layer_background"] != white:
            fail(f"CPU/reference {subzone} white/layer count changed")
        if zone["signedRgbOnGt32"]["alphaGreaterThanThirtyTwoPixels"] != 0:
            fail(f"CPU/reference {subzone} alpha >32 must remain zero")
    for subzone, gt32 in required_gpu.items():
        if gpu["subzones"][subzone]["greaterThanThirtyTwoPixels"] != gt32:
            fail(f"GPU/reference {subzone} >32 changed")

    classification = audit.get("classification", {})
    if classification.get("primarySubzoneGreaterThanThirtyTwoPixels") != 15726:
        fail("primary CPU >32 count mismatch")
    if classification.get("primarySubzoneShareOfCpuReferenceGreaterThanThirtyTwo") != 100.0:
        fail("primary CPU share mismatch")
    if classification.get("primaryWhiteLayerPixels") != 13518:
        fail("primary white/layer count mismatch")
    if classification.get("primaryWhiteLayerShare", 0.0) < 85.0:
        fail("primary white/layer share too weak")
    if classification.get("primaryAlphaGreaterThanThirtyTwoPixels") != 0:
        fail("primary alpha >32 must remain zero")

    fixture = audit.get("for276FixtureRelation", {})
    if fixture.get("preFixClippedRedSupportInClip") != 0:
        fail("FOR-276 pre-fix clipped red support changed")
    if fixture.get("postFixClippedRedSupportInClip") != 8:
        fail("FOR-276 post-fix clipped red support changed")
    if fixture.get("preFixLostRedSupportInClip") != 10:
        fail("FOR-276 pre-fix lost red changed")
    if fixture.get("postFixLostRedSupportInClip") != 2:
        fail("FOR-276 post-fix lost red changed")
    if fixture.get("postFixRecoveredShareOfUnclippedSupport") != 80.0:
        fail("FOR-276 recovered share changed")

    delta = audit.get("deltaFromFor274", {}).get("cpu_vs_reference", {})
    if delta.get("greaterThanThirtyTwoPixelsDelta") != 0:
        fail("CPU/reference residual must remain unchanged vs FOR-274 for this audit")
    if delta.get("maxChannelDeltaDelta") != 0:
        fail("CPU/reference max delta must remain unchanged vs FOR-274")
    for subzone in PRIMARY_SUBZONES:
        sub_delta = delta.get("subzones", {}).get(subzone, {})
        if sub_delta.get("greaterThanThirtyTwoPixelsDelta") != 0:
            fail(f"{subzone} CPU/reference >32 delta vs FOR-274 changed")
        if sub_delta.get("whiteLayerPixelsDelta") != 0:
            fail(f"{subzone} white/layer delta vs FOR-274 changed")

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

    samples = audit.get("signedCpuReferenceSamplesBySubzone", {})
    for subzone in PRIMARY_SUBZONES:
        if not samples.get(subzone):
            fail(f"{subzone} signed samples missing")

    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "FOR-277 M60 Post-FOR-276 CPU Residual Audit",
        "KEEP_EXPECTED_UNSUPPORTED",
        "LAYER_BACKGROUND_BOUNDARY_RESIDUAL_REMAINS_DOMINANT_AFTER_FOR276",
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
    cpu = comparison_by_name(audit, "cpu_vs_reference")
    classification = audit["classification"]
    print(
        "FOR-277 validation passed: "
        f"CPU/reference >32 remains {cpu['greaterThanThirtyTwoPixels']} with "
        f"{classification['primaryWhiteLayerShare']}% primary white/layer pixels; "
        "FOR-276 fixture recovery does not change the full M60 scene score."
    )


if __name__ == "__main__":
    main()
