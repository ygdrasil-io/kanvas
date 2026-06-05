#!/usr/bin/env python3
"""Validate FOR-419 M60 F16 shader-return storage zero-cause evidence."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FOR418_RAW = ROOT / (
    "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-aa-stencil-cover-storage-vs-color-target-for418/"
    "raw-runtime-snapshot-for418.json"
)
FOR419_DIR = ROOT / (
    "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-aa-stencil-cover-shader-return-storage-zero-cause-for419"
)
FOR419_RAW = FOR419_DIR / "raw-runtime-snapshot-for419.json"
FOR419_ARTIFACT = FOR419_DIR / (
    "m60-f16-aa-stencil-cover-shader-return-storage-zero-cause-for419.json"
)
REPORT = ROOT / (
    "reports/wgsl-pipeline/"
    "2026-06-05-for-419-m60-f16-aa-stencil-cover-shader-return-storage-zero-cause.md"
)
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
BUILD_GRADLE = ROOT / "gpu-raster/build.gradle.kts"

TARGET_DRAWS = {1, 3}
EXPECTED_DRAW_COUNTS = {1: 6, 3: 10}
SAMPLE_STRIDE_BYTES = 96
VEC4_BYTES = 16
SUBDRAW_COUNT = 2


def load_json(path: Path) -> dict:
    if not path.exists():
        raise AssertionError(f"missing required artifact: {path}")
    return json.loads(path.read_text())


def nonzero(values: list[float] | None) -> bool:
    return bool(values) and any(abs(float(v)) > 1.0e-9 for v in values)


def event_by_draw(snapshot: dict, key: str) -> dict[int, dict]:
    return {int(event["drawIndex"]): event for event in snapshot[key]}


def sample_by_xy(samples: list[dict]) -> dict[tuple[int, int], dict]:
    return {(int(sample["x"]), int(sample["y"])): sample for sample in samples}


def storage_by_xy_role(samples: list[dict]) -> dict[tuple[int, int, str], dict]:
    return {
        (int(sample["x"]), int(sample["y"]), str(sample["subdrawRole"])): sample
        for sample in samples
    }


def storage_offsets(slot: int, subdraw_ordinal: int) -> dict:
    base_vec4 = (slot * SUBDRAW_COUNT + subdraw_ordinal) * 6
    base_byte = base_vec4 * VEC4_BYTES
    return {
        "sampleSlot": slot,
        "subdrawOrdinal": subdraw_ordinal,
        "subdrawSideOffset": subdraw_ordinal,
        "sampleStrideBytes": SAMPLE_STRIDE_BYTES,
        "baseVec4Index": base_vec4,
        "baseByteOffset": base_byte,
        "fieldVec4Offsets": {
            "header": base_vec4,
            "colorAfterColorFilter": base_vec4 + 1,
            "colorAfterTargetColorspaceIfNeeded": base_vec4 + 2,
            "sourceColorBeforeQuantization": base_vec4 + 3,
            "coverageSourceAlphaQuantizedAlpha": base_vec4 + 4,
            "sourceColorSentToBlend": base_vec4 + 5,
        },
        "fieldByteOffsets": {
            "header": base_byte,
            "colorAfterColorFilter": base_byte + 16,
            "colorAfterTargetColorspaceIfNeeded": base_byte + 32,
            "sourceColorBeforeQuantization": base_byte + 48,
            "coverageSourceAlphaQuantizedAlpha": base_byte + 64,
            "sourceColorSentToBlend": base_byte + 80,
        },
    }


def source_checks() -> dict:
    device = DEVICE.read_text()
    build = BUILD_GRADLE.read_text()
    required = {
        "guardDefaultFalse": (
            "WEBGPU_M60_F16_AA_STENCIL_COVER_SHADER_RETURN_STORAGE_ZERO_CAUSE_FLAG" in device
            and 'WEBGPU_M60_F16_AA_STENCIL_COVER_SHADER_RETURN_STORAGE_ZERO_CAUSE_FLAG,\n            "false"' in device
        ),
        "shaderVariantUsesStorageZeroCause": "storageZeroCauseDiagnostic = true" in device,
        "entryLaneRecordDisabledForFor419": "if (diagnostic && !storageZeroCauseDiagnostic)" in device,
        "nonzeroPreservingWrite": "if (!output_nonzero)" in device,
        "pipelineCacheClosed": (
            "m60F16AaStencilCoverShaderReturnStorageZeroCausePipelineCache.values.forEach { it.close() }"
            in device
        ),
        "shaderModuleClosed": (
            "m60F16AaStencilCoverShaderReturnStorageZeroCauseShader.close()" in device
        ),
        "testPropertyForwarded": (
            "kanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled" in build
        ),
    }
    missing = [name for name, ok in required.items() if not ok]
    if missing:
        raise AssertionError(f"FOR-419 source checks failed: {', '.join(missing)}")
    return required


def main() -> None:
    for418 = load_json(FOR418_RAW)
    for419 = load_json(FOR419_RAW)

    if not for419.get("enabled"):
        raise AssertionError("FOR-419 raw snapshot is not enabled")
    if for419.get("propertyName") != (
        "kanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled"
    ):
        raise AssertionError("FOR-419 property name mismatch")
    if for419.get("scratchFormat") != "RGBA16Float":
        raise AssertionError("FOR-419 scratch format must be RGBA16Float")

    checks = source_checks()
    for418_storage = event_by_draw(for418, "storageEvents")
    for418_color = event_by_draw(for418, "colorTargetEvents")
    for419_storage = event_by_draw(for419, "storageEvents")
    for419_color = event_by_draw(for419, "colorTargetEvents")

    records: list[dict] = []
    for draw_index in sorted(TARGET_DRAWS):
        if draw_index not in for418_storage or draw_index not in for419_storage:
            raise AssertionError(f"missing draw {draw_index} in FOR-418/FOR-419 storage events")
        color_418_by_xy = sample_by_xy(for418_color[draw_index]["samples"])
        color_419_by_xy = sample_by_xy(for419_color[draw_index]["samples"])
        storage_418_by_key = storage_by_xy_role(for418_storage[draw_index]["samples"])
        storage_419_by_key = storage_by_xy_role(for419_storage[draw_index]["samples"])

        draw_records = 0
        for slot, color_sample in enumerate(for418_color[draw_index]["samples"]):
            xy = (int(color_sample["x"]), int(color_sample["y"]))
            color_418 = color_sample.get("scratchOutputRgbaFloat")
            color_419 = color_419_by_xy.get(xy, {}).get("scratchOutputRgbaFloat")
            if not nonzero(color_418):
                continue
            if not nonzero(color_419):
                raise AssertionError(f"FOR-419 lost nonzero color target for draw {draw_index} pixel {xy}")

            draw_records += 1
            roles = []
            for role in ("inside", "outside"):
                legacy = storage_418_by_key.get((xy[0], xy[1], role))
                probe = storage_419_by_key.get((xy[0], xy[1], role))
                if legacy is None or probe is None:
                    raise AssertionError(f"missing storage role {role} for draw {draw_index} pixel {xy}")
                subdraw_ordinal = int(legacy["subdrawOrdinal"])
                roles.append(
                    {
                        "subdrawRole": role,
                        "for418": {
                            "shaderObserved": bool(legacy["shaderObserved"]),
                            "candidateBranchReached": bool(legacy["candidateBranchReached"]),
                            "colorAfterColorFilter": legacy.get("colorAfterColorFilter"),
                            "colorAfterTargetColorspaceIfNeeded": legacy.get(
                                "colorAfterTargetColorspaceIfNeeded"
                            ),
                            "correctedColorBeforeCoverage": legacy.get("correctedColorBeforeCoverage"),
                            "coverageOrAaAlpha": legacy.get("coverageOrAaAlpha"),
                            "sourceAlphaAfterCoverage": legacy.get("sourceAlphaAfterCoverage"),
                            "sourceColorBeforeQuantization": legacy.get("sourceColorBeforeQuantization"),
                            "sourceColorSentToBlend": legacy.get("sourceColorSentToBlend"),
                            "quantizedAlphaSentToBlend": legacy.get("quantizedAlphaSentToBlend"),
                        },
                        "for419": {
                            "applicationPointStorageObserved": bool(probe["shaderObserved"]),
                            "candidateBranchReached": bool(probe["candidateBranchReached"]),
                            "sourceColorSentToBlend": probe.get("sourceColorSentToBlend"),
                        },
                        "storageLayout": storage_offsets(slot, subdraw_ordinal),
                    }
                )
                if not legacy["shaderObserved"]:
                    raise AssertionError(f"FOR-418 did not observe storage for draw {draw_index} pixel {xy}")
                if nonzero(legacy.get("sourceColorSentToBlend")):
                    raise AssertionError(f"FOR-418 unexpectedly has nonzero source for draw {draw_index} pixel {xy}")
                if probe["shaderObserved"] or nonzero(probe.get("sourceColorSentToBlend")):
                    raise AssertionError(
                        f"FOR-419 application-point-only storage unexpectedly observed output for "
                        f"draw {draw_index} pixel {xy}"
                    )

            records.append(
                {
                    "drawIndex": draw_index,
                    "pixel": {"x": xy[0], "y": xy[1]},
                    "colorTargetFor418RgbaFloat": color_418,
                    "colorTargetFor419RgbaFloat": color_419,
                    "classification": "application-point-storage-hook-not-reached",
                    "hypothesisSupport": {
                        "valueCalculatedAfterStorageWrite": False,
                        "wrongFieldOrBranchWritten": False,
                        "storageOffsetOrStrideIncorrect": False,
                        "wrongSideOrSubdraw": False,
                        "diagnosticShaderDivergesFromRenderedReturn": True,
                        "storageWriteClearedAfterDraw": False,
                    },
                    "subdraws": roles,
                    "reason": (
                        "FOR-418 entry storage observed this pixel with zero fields while the color target "
                        "was nonzero. FOR-419 removed the entry write and allowed only the application-point "
                        "return hook to write nonzero outputs; storage then observed no application-point "
                        "record while the same scratch color target stayed nonzero."
                    ),
                }
            )

        expected = EXPECTED_DRAW_COUNTS[draw_index]
        if draw_records != expected:
            raise AssertionError(f"draw {draw_index} records {draw_records}, expected {expected}")

    if len(records) != 16:
        raise AssertionError(f"FOR-419 records {len(records)}, expected 16")

    classification = "application-point-storage-hook-not-on-rendered-return-path"
    artifact = {
        "schemaVersion": 1,
        "linear": "FOR-419",
        "sceneId": "m60-f16-aa-stencil-cover-shader-return-storage-zero-cause-for419",
        "classification": classification,
        "summary": {
            "recordCount": len(records),
            "drawCounts": {str(k): v for k, v in EXPECTED_DRAW_COUNTS.items()},
            "for418StorageObservedZeroFieldRecords": len(records),
            "for419ApplicationPointStorageObservedRecords": 0,
            "for419ColorTargetNonzeroRecords": len(records),
        },
        "guard": {
            "propertyName": for419["propertyName"],
            "enabledForEvidenceRun": True,
            "enabledByDefault": False,
        },
        "sourceChecks": checks,
        "sourceInspection": {
            "diagnosticShader": for419["diagnosticShader"],
            "pipelineLayout": for419["pipelineLayout"],
            "scratchFormat": for419["scratchFormat"],
            "storageLayout": {
                "sampleStrideBytes": SAMPLE_STRIDE_BYTES,
                "vec4Bytes": VEC4_BYTES,
                "vec4sPerSubdrawSample": 6,
                "subdrawsPerPixel": SUBDRAW_COUNT,
            },
            "interpretation": (
                "The FOR-419 shader variant disables the entry lane storage write and preserves only "
                "nonzero application-point writes. The continued nonzero color target with zero "
                "application-point storage isolates the divergence to the shader-return diagnostic "
                "instrumentation path, not storage offsets, side mapping, post-draw clears, fixed blend, "
                "or render-pass load/store."
            ),
        },
        "records": records,
        "rawArtifacts": {
            "for418": str(FOR418_RAW.relative_to(ROOT)),
            "for419": str(FOR419_RAW.relative_to(ROOT)),
        },
    }
    FOR419_DIR.mkdir(parents=True, exist_ok=True)
    FOR419_ARTIFACT.write_text(json.dumps(artifact, indent=2) + "\n")

    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(
        "\n".join(
            [
                "# FOR-419 - M60 F16 shader-return storage zero-cause",
                "",
                f"Classification: `{classification}`.",
                "",
                "## Resultat",
                "",
                "- Les 16 transitions mutatrices FOR-401/FOR-413 restent couvertes.",
                "- FOR-418 observe le storage d'entree mais les champs couleur/couverture restent a zero.",
                "- FOR-419 desactive cette ecriture d'entree et ne preserve que les sorties non nulles au point d'application.",
                "- Dans FOR-419, la cible couleur scratch reste non nulle pour les 16 records, mais le storage au point d'application n'observe aucun record.",
                "",
                "## Cause probable",
                "",
                "Le hook storage `m60_f16_application_point_output` n'est pas sur le chemin `@location(0)` reel qui produit la couleur dans cette variante diagnostique. La divergence ne vient pas d'un offset/stride storage, d'un mauvais side/subdraw, d'un clear apres draw, du blend fixe, ni du load/store render-pass.",
                "",
                "## Artefacts",
                "",
                f"- `{FOR419_ARTIFACT.relative_to(ROOT)}`",
                f"- `{FOR419_RAW.relative_to(ROOT)}`",
                f"- `{FOR418_RAW.relative_to(ROOT)}`",
                "",
                "## Garde-fous",
                "",
                f"- Guard opt-in: `{for419['propertyName']}`.",
                "- Desactive par defaut.",
                "- Aucun changement de rendu par defaut, route, score, seuil, fallback ou promotion.",
                "- Resources et caches FOR-419 fermes dans `SkWebGpuDevice.close()`.",
                "",
                "## Validation",
                "",
                "- `rtk python3 scripts/validate_for419_m60_f16_aa_stencil_cover_shader_return_storage_zero_cause.py`",
            ]
        )
        + "\n"
    )
    print(f"FOR-419 validation passed: {classification}, records={len(records)}")


if __name__ == "__main__":
    main()
