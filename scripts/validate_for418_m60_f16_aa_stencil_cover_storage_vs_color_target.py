#!/usr/bin/env python3
"""Generate and validate FOR-418 M60 F16 storage vs color-target evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-418"
SCENE_ID = "m60-f16-aa-stencil-cover-storage-vs-color-target-for418"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
RAW_RUNTIME = ARTIFACT_DIR / "raw-runtime-snapshot-for418.json"
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-418-m60-f16-aa-stencil-cover-storage-vs-color-target.md"
)
FOR412_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-shader-return-diagnostic-for412"
    / "m60-f16-aa-stencil-cover-shader-return-diagnostic-for412.json"
)
FOR417_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-isolated-color-target-runtime-for417"
    / "m60-f16-aa-stencil-cover-isolated-color-target-runtime-for417.json"
)
SKWEBGPUDEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
WEBGPUSINK = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
CAPTURE_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"

GUARD = "kanvas.webgpu.m60F16AaStencilCoverStorageColorTargetComparison.enabled"
EXPECTED_POINTS = [
    (92, 75),
    (91, 76),
    (90, 77),
    (89, 78),
    (88, 79),
    (87, 80),
    (101, 37),
    (102, 37),
    (99, 38),
    (100, 38),
    (101, 38),
    (102, 38),
    (103, 38),
    (104, 38),
    (98, 39),
    (99, 39),
]
MATCH_TOLERANCE = 1e-6
EXPECTED_VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for418_m60_f16_aa_stencil_cover_storage_vs_color_target.py",
    "rtk python3 scripts/validate_for417_m60_f16_aa_stencil_cover_isolated_color_target_runtime.py",
    "rtk python3 scripts/validate_for416_m60_f16_aa_stencil_cover_isolated_color_target.py",
    "rtk python3 scripts/validate_for415_m60_f16_aa_stencil_cover_blend_render_pass_state.py",
    "rtk python3 scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py",
    "rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py",
    "rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for418-pycache python3 -m py_compile "
        "scripts/validate_for418_m60_f16_aa_stencil_cover_storage_vs_color_target.py"
    ),
    "rtk git diff --check",
    "rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-418 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain an object")
    return data


def rgba(value: Any, field: str) -> list[float]:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA")
    return [float(channel) for channel in value]


def is_zero(value: list[float]) -> bool:
    return max(abs(channel) for channel in value) <= MATCH_TOLERANCE


def delta(a: list[float], b: list[float]) -> dict[str, Any]:
    signed = [round(b[index] - a[index], 9) for index in range(4)]
    absolute = [round(abs(channel), 9) for channel in signed]
    max_channel = max(absolute)
    return {
        "signedRgbaFloat": signed,
        "absoluteRgbaFloat": absolute,
        "absoluteTotalFloat": round(sum(absolute), 9),
        "maxChannelFloat": max_channel,
        "withinTolerance": max_channel <= MATCH_TOLERANCE,
        "tolerance": MATCH_TOLERANCE,
    }


def by_draw(events: list[dict[str, Any]]) -> dict[int, dict[str, Any]]:
    out: dict[int, dict[str, Any]] = {}
    for event in events:
        draw = event.get("drawIndex")
        require(isinstance(draw, int), "event drawIndex missing")
        out[draw] = event
    return out


def color_sample(event: dict[str, Any], x: int, y: int) -> dict[str, Any]:
    for sample in event.get("samples", []):
        if sample.get("x") == x and sample.get("y") == y:
            return sample
    fail(f"missing color sample for draw {event.get('drawIndex')} pixel {(x, y)}")


def storage_samples(event: dict[str, Any], x: int, y: int) -> list[dict[str, Any]]:
    samples = [
        sample
        for sample in event.get("samples", [])
        if sample.get("x") == x and sample.get("y") == y
    ]
    require(len(samples) == 2, f"expected inside/outside storage samples for {(x, y)}")
    return samples


def source_audit() -> dict[str, Any]:
    source = SKWEBGPUDEVICE.read_text(encoding="utf-8")
    sink = WEBGPUSINK.read_text(encoding="utf-8")
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    checks = {
        "runtimeGuardPresent": GUARD in source,
        "runtimeGuardDisabledByDefault": (
            "WEBGPU_M60_F16_AA_STENCIL_COVER_STORAGE_COLOR_TARGET_COMPARISON_FLAG" in source
            and 'WEBGPU_M60_F16_AA_STENCIL_COVER_STORAGE_COLOR_TARGET_COMPARISON_FLAG,\n            "false"' in source
        ),
        "separateScratchTarget": (
            "m60F16AaStencilCoverStorageColorTargetComparisonScratchTexture" in source
            and "m60F16AaStencilCoverStorageColorTargetComparisonScratchView" in source
        ),
        "samePassShaderStorageAndColorTarget": (
            "m60F16AaStencilCoverStorageColorTargetComparisonPipelineFor" in source
            and "m60F16AaStencilCoverShaderReturnDiagnosticShader" in source
            and "blend = null" in source
        ),
        "resourcesClosed": all(
            needle in source
            for needle in (
                "m60F16AaStencilCoverStorageColorTargetComparisonColorStaging?.close()",
                "m60F16AaStencilCoverStorageColorTargetComparisonColorStorage?.close()",
                "m60F16AaStencilCoverStorageColorTargetComparisonShaderStaging?.close()",
                "m60F16AaStencilCoverStorageColorTargetComparisonShaderStorage?.close()",
                "m60F16AaStencilCoverStorageColorTargetComparisonScratchView?.close()",
                "m60F16AaStencilCoverStorageColorTargetComparisonScratchTexture?.close()",
                "m60F16AaStencilCoverStorageColorTargetComparisonDepthStencilView?.close()",
                "m60F16AaStencilCoverStorageColorTargetComparisonDepthStencilTexture?.close()",
            )
        ),
        "pipelineCacheClosed": (
            "m60F16AaStencilCoverStorageColorTargetComparisonPipelineCache.values.forEach { it.close() }"
            in source
            and "m60F16AaStencilCoverStorageColorTargetComparisonPipelineCache.clear()" in source
        ),
        "snapshotExported": "m60F16AaStencilCoverStorageColorTargetComparisonSnapshot()" in sink,
        "captureWritesRawSnapshot": "raw-runtime-snapshot-for418.json" in capture,
    }
    missing = [name for name, present in checks.items() if not present]
    require(not missing, f"source audit missing checks: {missing}")
    return {
        "source": rel(SKWEBGPUDEVICE),
        "testSink": rel(WEBGPUSINK),
        "captureTest": rel(CAPTURE_TEST),
        "checks": checks,
    }


def build_artifact() -> dict[str, Any]:
    raw = load_json(RAW_RUNTIME)
    for412 = load_json(FOR412_ARTIFACT)
    for417 = load_json(FOR417_ARTIFACT)
    require(raw.get("linear") == LINEAR_ID, "raw runtime Linear id changed")
    require(raw.get("propertyName") == GUARD, "raw runtime guard changed")
    require(raw.get("enabled") is True, "raw runtime guard was not enabled")
    require(raw.get("scratchFormat") == "RGBA16Float", "scratch format changed")
    require(for412.get("classification") == "shader-return-zero-but-post-pass-colored", "FOR-412 baseline changed")
    require(
        for417.get("classification") == "isolated-color-target-output-nonzero-matches-mutation",
        "FOR-417 baseline changed",
    )

    storage_by_draw = by_draw(raw.get("storageEvents", []))
    color_by_draw = by_draw(raw.get("colorTargetEvents", []))
    mutation_records = for417.get("mutationRecords")
    require(isinstance(mutation_records, list), "FOR-417 mutationRecords missing")
    require(len(mutation_records) == len(EXPECTED_POINTS), "FOR-417 mutation record count changed")

    records: list[dict[str, Any]] = []
    for mutation in mutation_records:
        draw = mutation.get("drawIndex")
        x = mutation.get("x")
        y = mutation.get("y")
        require(isinstance(draw, int) and isinstance(x, int) and isinstance(y, int), "mutation key missing")
        require((x, y) in EXPECTED_POINTS, f"unexpected mutation point {(x, y)}")
        storage_event = storage_by_draw.get(draw)
        color_event = color_by_draw.get(draw)
        require(storage_event is not None and color_event is not None, f"FOR-418 draw {draw} missing")
        color = color_sample(color_event, x, y)
        color_rgba = rgba(color.get("scratchOutputRgbaFloat"), "FOR-418 color target")
        require(color.get("readbackAvailable") is True, f"FOR-418 color unavailable for {(draw, x, y)}")
        storage_pair = storage_samples(storage_event, x, y)
        storage_details = []
        for storage in storage_pair:
            sent = rgba(storage.get("sourceColorSentToBlend"), "FOR-418 storage source")
            storage_details.append(
                {
                    "subdrawRole": storage.get("subdrawRole"),
                    "shaderObserved": storage.get("shaderObserved"),
                    "candidateBranchReached": storage.get("candidateBranchReached"),
                    "sourceColorSentToBlend": sent,
                    "storageVsColorTargetDelta": delta(sent, color_rgba),
                    "classification": storage.get("classification"),
                    "reason": storage.get("reason"),
                }
            )
            require(storage.get("shaderObserved") is True, f"storage not observed for {(draw, x, y)}")
            require(is_zero(sent), f"FOR-418 storage was not zero for {(draw, x, y)}")
        require(not is_zero(color_rgba), f"FOR-418 color target stayed zero for {(draw, x, y)}")
        records.append(
            {
                "transitionId": mutation.get("transitionId"),
                "drawIndex": draw,
                "x": x,
                "y": y,
                "pipelineFamily": mutation.get("pipelineFamily"),
                "blendMode": mutation.get("blendMode"),
                "for413Classification": mutation.get("for413Classification"),
                "for414Classification": mutation.get("for414Classification"),
                "for412Baseline": "shader-return-zero-but-post-pass-colored",
                "for417Classification": mutation.get("classification"),
                "storageSamples": storage_details,
                "colorTarget": {
                    "readbackAvailable": color.get("readbackAvailable"),
                    "scratchOutputRgbaFloat": color_rgba,
                    "classification": color.get("classification"),
                    "reason": color.get("reason"),
                },
                "divergenceReason": (
                    "FOR-418 wrote zero to shader-return storage while the same diagnostic "
                    "scratch render pass produced a non-zero RGBA16Float color target sample."
                ),
                "classification": "storage-zero-while-same-pass-color-target-nonzero",
            }
        )

    draws = sorted({record["drawIndex"] for record in records})
    require(draws == [1, 3], f"expected mutating draws 1/3, got {draws}")
    require(len(records) == 16, f"expected 16 comparison records, got {len(records)}")

    artifact = {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceSceneId": "m60-bounded-stroke-cap-join",
        "classification": "storage-zero-while-same-pass-color-target-nonzero",
        "globalClassification": "FOR-418 narrows divergence to storage/shader-return side-channel",
        "classificationReason": (
            "The direct FOR-418 scratch+storage pass covers the same 16 mutating FOR-401 pixels. "
            "For every record, shader-return storage is observed but remains zero, while the "
            "same pass's no-blend RGBA16Float color target is non-zero."
        ),
        "decision": "do-not-change-rendering-route",
        "supportClaim": "diagnostic evidence only",
        "promoted": False,
        "correctionAppliedByDefault": False,
        "defaultRenderingChanged": False,
        "thresholdChanged": False,
        "scoringChanged": False,
        "guards": {
            "for418StorageColorTargetComparison": GUARD,
            "enabledByDefault": False,
            "for417IsolatedColorTarget": "kanvas.webgpu.m60F16AaStencilCoverIsolatedColorTarget.enabled",
            "for412ShaderReturnDiagnostic": "kanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled",
        },
        "scope": {
            "draws": draws,
            "pixelCount": len(records),
            "points": [{"x": x, "y": y} for x, y in EXPECTED_POINTS],
            "pipelineFamily": "StencilCoverAaPolygonDraw",
            "blendMode": "kSrcOver",
            "intermediateFormat": "RGBA16Float",
        },
        "runtimeSummary": {
            "rawRuntime": rel(RAW_RUNTIME),
            "storageEvents": len(raw.get("storageEvents", [])),
            "colorTargetEvents": len(raw.get("colorTargetEvents", [])),
            "coveredMutationRecords": len(records),
            "storageZeroRecords": len(records),
            "colorTargetNonZeroRecords": len(records),
        },
        "records": records,
        "sourceArtifacts": {
            "rawFor418Runtime": rel(RAW_RUNTIME),
            "for412": rel(FOR412_ARTIFACT),
            "for417": rel(FOR417_ARTIFACT),
        },
        "sourceCodeAudit": source_audit(),
        "nonGoalsPreserved": [
            "No default rendering route changed.",
            "No score, threshold, fallback, or promotion changed.",
            "No value is synthesized when a runtime hook is unavailable.",
        ],
        "nextStep": (
            "Inspect why the diagnostic storage branch writes zero while @location(0) still "
            "produces the non-zero color-target output in the same pass."
        ),
        "validationCommands": EXPECTED_VALIDATION_COMMANDS,
    }
    return artifact


def write_report(artifact: dict[str, Any]) -> None:
    summary = artifact["runtimeSummary"]
    REPORT.write_text(
        "\n".join(
            [
                "# FOR-418 - M60 F16 storage vs color-target",
                "",
                f"Classification: `{artifact['classification']}`.",
                "",
                "FOR-418 adds an opt-in diagnostic pass guarded by "
                f"`{GUARD}` and disabled by default. The pass replays the bounded "
                "M60 F16 AA stencil-cover draw into an `RGBA16Float` scratch target "
                "with blending disabled while the shader-return diagnostic writes to "
                "storage in the same render pass.",
                "",
                "Result:",
                "",
                f"- Covered mutation records: {summary['coveredMutationRecords']}.",
                f"- Storage-zero records: {summary['storageZeroRecords']}.",
                f"- Non-zero color-target records: {summary['colorTargetNonZeroRecords']}.",
                "- Mutating draws covered: 1 and 3.",
                "",
                "Interpretation: the direct comparison no longer points at fixed-function "
                "blend, load/store, or the no-blend scratch output. The remaining suspect "
                "is the shader-return/storage side-channel path: it observes the fragments "
                "but records zero while the same pass produces the non-zero color output.",
                "",
                "No default route, score, threshold, fallback, promotion, or rendering output "
                "is changed by this ticket.",
            ]
        )
        + "\n",
        encoding="utf-8",
    )


def main() -> None:
    artifact = build_artifact()
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(artifact, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    write_report(artifact)
    print(
        f"FOR-418 validation passed: {artifact['classification']} "
        f"records={artifact['runtimeSummary']['coveredMutationRecords']}"
    )
    print(f"wrote {rel(ARTIFACT)}")
    print(f"wrote {rel(REPORT)}")


if __name__ == "__main__":
    main()
