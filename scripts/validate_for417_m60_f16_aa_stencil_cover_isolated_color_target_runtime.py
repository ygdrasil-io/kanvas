#!/usr/bin/env python3
"""Generate and validate FOR-417 M60 F16 no-blend scratch runtime evidence."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-417"
SCENE_ID = "m60-f16-aa-stencil-cover-isolated-color-target-runtime-for417"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
SOURCE_DRAFT_MEMORY = (
    "global/kanvas/tickets/drafts/"
    "brouillon-ticket-for-417-m60-f16-ajouter-scratch-no-blend-pour-sortie-color-target-aa-stencil-cover"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-416-refuse-de-synthetiser-la-sortie-color-target-isolee-et-confirme-le-besoin-dun-scratch-no-blend"
)

ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
RAW_RUNTIME = ARTIFACT_DIR / "raw-runtime-snapshot-for417.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-417-m60-f16-aa-stencil-cover-isolated-color-target-runtime.md"
)
SKWEBGPUDEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
WEBGPUSINK = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
CAPTURE_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
FOR401_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-final-residual-origin-map-for401"
    / "m60-f16-final-residual-origin-map-for401.json"
)
FOR412_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-shader-return-diagnostic-for412"
    / "m60-f16-aa-stencil-cover-shader-return-diagnostic-for412.json"
)
FOR413_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-draw-transition-correlation-for413"
    / "m60-f16-aa-stencil-cover-draw-transition-correlation-for413.json"
)
FOR414_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-post-draw-readback-for414"
    / "m60-f16-aa-stencil-cover-post-draw-readback-for414.json"
)
FOR416_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-isolated-color-target-for416"
    / "m60-f16-aa-stencil-cover-isolated-color-target-for416.json"
)

GUARD = "kanvas.webgpu.m60F16AaStencilCoverIsolatedColorTarget.enabled"
MATCH_TOLERANCE = 1e-6
F16_RECONSTRUCTION_TOLERANCE = 6e-4
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
ALLOWED_CLASSIFICATIONS = {
    "isolated-color-target-output-zero-fixed-function-suspect",
    "isolated-color-target-output-diverges-from-shader-return",
    "isolated-color-target-output-nonzero-matches-mutation",
    "isolated-color-target-runtime-hook-unavailable",
}
EXPECTED_VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for417_m60_f16_aa_stencil_cover_isolated_color_target_runtime.py",
    "rtk python3 scripts/validate_for416_m60_f16_aa_stencil_cover_isolated_color_target.py",
    "rtk python3 scripts/validate_for415_m60_f16_aa_stencil_cover_blend_render_pass_state.py",
    "rtk python3 scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py",
    "rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py",
    "rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for417-pycache python3 -m py_compile "
        "scripts/validate_for417_m60_f16_aa_stencil_cover_isolated_color_target_runtime.py"
    ),
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true "
        ":gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-417 validation failed: {message}")


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


def pixel_key(pixel: dict[str, Any]) -> tuple[int, int]:
    x = pixel.get("x")
    y = pixel.get("y")
    require(isinstance(x, int) and isinstance(y, int), "pixel coordinate missing")
    return x, y


def require_rgba(value: Any, field: str) -> list[float]:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA float")
    out: list[float] = []
    for channel in value:
        require(isinstance(channel, (float, int)), f"{field} channel must be numeric")
        out.append(float(channel))
    return out


def max_abs_delta(a: list[float], b: list[float]) -> float:
    return max(abs(a[index] - b[index]) for index in range(4))


def delta(a: list[float], b: list[float], tolerance: float = MATCH_TOLERANCE) -> dict[str, Any]:
    signed = [round(float(b[index] - a[index]), 9) for index in range(4)]
    absolute = [round(abs(channel), 9) for channel in signed]
    max_channel = max(absolute)
    return {
        "signedRgbaFloat": signed,
        "absoluteRgbaFloat": absolute,
        "absoluteTotalFloat": round(sum(absolute), 9),
        "maxChannelFloat": max_channel,
        "withinTolerance": max_channel <= tolerance,
        "tolerance": tolerance,
    }


def source_over(src: list[float], dst: list[float]) -> list[float]:
    inv_alpha = 1.0 - src[3]
    return [src[index] + dst[index] * inv_alpha for index in range(4)]


def is_zero(rgba: list[float]) -> bool:
    return max(abs(channel) for channel in rgba) <= MATCH_TOLERANCE


def extract_between(source: str, start: str, end: str) -> str:
    start_index = source.find(start)
    require(start_index >= 0, f"source audit start marker missing: {start}")
    end_index = source.find(end, start_index)
    require(end_index > start_index, f"source audit end marker missing: {end}")
    return source[start_index:end_index]


def source_audit() -> dict[str, Any]:
    source = SKWEBGPUDEVICE.read_text(encoding="utf-8")
    sink = WEBGPUSINK.read_text(encoding="utf-8")
    capture_test = CAPTURE_TEST.read_text(encoding="utf-8")
    aa_branch = extract_between(
        source,
        "if (d is StencilCoverAaPolygonDraw) {",
        "if (d is StencilCoverAaGradientDraw) {",
    )
    checks = {
        "runtimeGuardPresent": GUARD in source,
        "runtimeGuardDisabledByDefault": (
            "WEBGPU_M60_F16_AA_STENCIL_COVER_ISOLATED_COLOR_TARGET_FLAG" in source
            and '"false"' in extract_between(
                source,
                "m60F16AaStencilCoverIsolatedColorTargetDiagnosticsEnabled",
                "private val rawRectUniformColorWrites",
            )
        ),
        "scratchTargetSeparateFromIntermediateView": (
            "m60F16AaStencilCoverIsolatedColorTargetScratchTexture" in source
            and "BindGroupEntry(binding = 0u, resource = isolatedColorTargetScratchView!!)" in source
            and "m60F16AaStencilCoverIsolatedColorTargetBindGroup" in source
        ),
        "scratchFormatRgba16Float": "format = GPUTextureFormat.RGBA16Float" in source,
        "noBlendColorTarget": (
            "m60F16AaStencilCoverIsolatedColorTargetPipelineFor" in source
            and "blend = null" in extract_between(
                source,
                "private fun m60F16AaStencilCoverIsolatedColorTargetPipelineFor(",
                "// \u2500\u2500\u2500 Present pass",
            )
        ),
        "scratchPassDoesNotTargetIntermediate": (
            "view = scratchView" in aa_branch and "view = colorView" in aa_branch
        ),
        "scratchDepthStencilSeparate": "m60F16AaStencilCoverIsolatedColorTargetDepthStencilTexture" in source,
        "scratchResourcesClosed": all(
            needle in source
            for needle in (
                "m60F16AaStencilCoverIsolatedColorTargetStaging?.close()",
                "m60F16AaStencilCoverIsolatedColorTargetStorage?.close()",
                "m60F16AaStencilCoverIsolatedColorTargetScratchView?.close()",
                "m60F16AaStencilCoverIsolatedColorTargetScratchTexture?.close()",
                "m60F16AaStencilCoverIsolatedColorTargetDepthStencilView?.close()",
                "m60F16AaStencilCoverIsolatedColorTargetDepthStencilTexture?.close()",
            )
        ),
        "diagnosticPipelineCacheClosed": (
            "m60F16AaStencilCoverIsolatedColorTargetPipelineCache.values.forEach { it.close() }" in source
            and "m60F16AaStencilCoverIsolatedColorTargetPipelineCache.clear()" in source
        ),
        "snapshotExported": "m60F16AaStencilCoverIsolatedColorTargetSnapshot()" in sink,
        "captureWritesRawSnapshot": "raw-runtime-snapshot-for417.json" in capture_test,
    }
    missing = [name for name, present in checks.items() if not present]
    require(not missing, f"source audit missing checks: {missing}")
    return {
        "source": rel(SKWEBGPUDEVICE),
        "testSink": rel(WEBGPUSINK),
        "captureTest": rel(CAPTURE_TEST),
        "method": "bounded source audit for FOR-417 runtime guard, scratch pass, no-blend pipeline, readback, and resource cleanup",
        "checks": checks,
    }


def runtime_event_by_draw(raw: dict[str, Any]) -> dict[int, dict[str, Any]]:
    require(raw.get("linear") == LINEAR_ID, "raw runtime Linear id changed")
    require(raw.get("propertyName") == GUARD, "raw runtime guard changed")
    require(raw.get("enabled") is True, "raw runtime guard was not enabled for evidence")
    require(raw.get("scratchFormat") == "RGBA16Float", "scratch format changed")
    events = raw.get("events")
    require(isinstance(events, list) and len(events) >= 3, "expected runtime events for draws 1/3/5")
    by_draw: dict[int, dict[str, Any]] = {}
    for event in events:
        draw = event.get("drawIndex")
        require(draw in {1, 3, 5}, f"unexpected runtime draw {draw}")
        require(event.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "wrong pipeline family")
        require(event.get("blendMode") == "kSrcOver", "wrong blend mode")
        require(event.get("scratchTargetEncoded") is True, f"scratch target not encoded for draw {draw}")
        require(event.get("copyAttempted") is True and event.get("copySucceeded") is True, "copy failed")
        samples = event.get("samples")
        require(isinstance(samples, list) and len(samples) == 16, f"expected 16 samples for draw {draw}")
        points = [(sample["x"], sample["y"]) for sample in samples]
        require(points == EXPECTED_POINTS, f"runtime point order changed for draw {draw}")
        by_draw[draw] = event
    require({1, 3}.issubset(by_draw), "mutating draw runtime events missing")
    return by_draw


def sample_for(event: dict[str, Any], point: tuple[int, int]) -> dict[str, Any]:
    for sample in event["samples"]:
        if (sample["x"], sample["y"]) == point:
            return sample
    fail(f"runtime sample missing at {point} draw {event['drawIndex']}")


def build_record(record: dict[str, Any], runtime_events: dict[int, dict[str, Any]]) -> dict[str, Any]:
    x, y = pixel_key(record)
    draw_index = record.get("drawIndex")
    require(draw_index in {1, 3}, f"unexpected mutating draw {draw_index}")
    require(record.get("for413Classification") == "draw-mutates-despite-zero-shader-return", "FOR-413 drift")
    require(record.get("for414Classification") == "post-draw-matches-next-predraw", "FOR-414 drift")
    before = require_rgba(record.get("beforeRgbaFloat"), "beforeRgbaFloat")
    after = require_rgba(record.get("afterRgbaFloat"), "afterRgbaFloat")
    post = require_rgba(record.get("postDrawRgbaFloat"), "postDrawRgbaFloat")
    require(max_abs_delta(after, post) <= MATCH_TOLERANCE, "FOR-414 post-draw drift")
    sources = []
    for subdraw in record.get("sourceColorSentToBlendSubdraws", []):
        require(subdraw.get("shaderObserved") is True, "FOR-412 source must be observed")
        require(subdraw.get("captureSynthetic") is False, "synthetic FOR-412 source forbidden")
        source = require_rgba(subdraw.get("sourceColorSentToBlend"), "sourceColorSentToBlend")
        require(is_zero(source), f"FOR-412 source must be zero at {(x, y)} draw {draw_index}")
        sources.append({
            "subdrawRole": subdraw.get("subdrawRole"),
            "sourceColorSentToBlend": source,
            "captureSynthetic": False,
            "for412Classification": subdraw.get("for412Classification"),
        })
    require(len(sources) == 2, f"expected inside/outside sources at {(x, y)}")

    runtime_event = runtime_events[draw_index]
    runtime_sample = sample_for(runtime_event, (x, y))
    require(runtime_sample.get("readbackAvailable") is True, f"scratch sample unavailable at {(x, y)}")
    scratch = require_rgba(runtime_sample.get("scratchOutputRgbaFloat"), "scratchOutputRgbaFloat")
    scratch_over_before = source_over(scratch, before)
    scratch_matches_mutation = max_abs_delta(scratch_over_before, after) <= F16_RECONSTRUCTION_TOLERANCE
    require(scratch_matches_mutation, f"scratch SrcOver did not reconstruct mutation at {(x, y)}")
    require(not is_zero(scratch), f"scratch output unexpectedly zero at {(x, y)}")

    return {
        "x": x,
        "y": y,
        "drawIndex": draw_index,
        "transitionId": record.get("transitionId"),
        "pipelineFamily": "StencilCoverAaPolygonDraw",
        "blendMode": "kSrcOver",
        "for413Classification": record.get("for413Classification"),
        "for414Classification": record.get("for414Classification"),
        "for416Classification": record.get("classification"),
        "beforeRgbaFloat": before,
        "afterRgbaFloat": after,
        "postDrawRgbaFloat": post,
        "mutationDelta": record.get("mutationDelta"),
        "sourceColorSentToBlendSubdraws": sources,
        "isolatedColorTarget": {
            "available": True,
            "scratchTargetEncoded": True,
            "scratchFormat": "RGBA16Float",
            "colorTargetBlend": None,
            "combinedOutputRgbaFloat": scratch,
            "combinedOutputRgba8": runtime_sample.get("scratchOutputRgba8"),
            "sampleSource": "FOR-417 no-blend scratch runtime readback",
            "classification": "isolated-color-target-output-observed",
        },
        "sourceVsScratchDelta": delta([0.0, 0.0, 0.0, 0.0], scratch),
        "scratchVsPostDrawDirectDelta": delta(scratch, after),
        "scratchSourceOverBeforeRgbaFloat": [round(channel, 9) for channel in scratch_over_before],
        "scratchSourceOverBeforeVsMutationDelta": delta(
            scratch_over_before,
            after,
            F16_RECONSTRUCTION_TOLERANCE,
        ),
        "classification": "isolated-color-target-output-nonzero-matches-mutation",
        "classificationReason": (
            "FOR-417 observes a real non-zero no-blend scratch color-target output. "
            "The observed scratch output diverges from the zero FOR-412 storage side-channel, "
            "and SrcOver(scratch, dstBefore) reconstructs the FOR-414 immediate mutation."
        ),
    }


def build_artifact() -> dict[str, Any]:
    for401 = load_json(FOR401_ARTIFACT)
    for412 = load_json(FOR412_ARTIFACT)
    for413 = load_json(FOR413_ARTIFACT)
    for414 = load_json(FOR414_ARTIFACT)
    for416 = load_json(FOR416_ARTIFACT)
    raw = load_json(RAW_RUNTIME)

    require(for401.get("classification") == "residual-visible-only-at-final-readback", "FOR-401 changed")
    require(for412.get("classification") == "shader-return-zero-but-post-pass-colored", "FOR-412 changed")
    require(for413.get("classification") == "draw-mutates-despite-zero-shader-return", "FOR-413 changed")
    require(for414.get("classification") == "post-draw-matches-next-predraw", "FOR-414 changed")
    require(for416.get("classification") == "isolated-color-target-diagnostic-unavailable", "FOR-416 changed")
    require([pixel_key(pixel) for pixel in for401["selectedPixels"]] == EXPECTED_POINTS, "FOR-401 points changed")

    runtime_events = runtime_event_by_draw(raw)
    records = [build_record(record, runtime_events) for record in for416["mutationRecords"]]
    require(len(records) == 16, "expected 16 mutation records")
    draw_counts = Counter(record["drawIndex"] for record in records)
    require(draw_counts == Counter({1: 6, 3: 10}), f"unexpected draw split: {draw_counts}")

    classification = "isolated-color-target-output-nonzero-matches-mutation"
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceSceneId": ROW_ID,
        "sourceDraftMemory": SOURCE_DRAFT_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "sourceArtifacts": {
            "for401": rel(FOR401_ARTIFACT),
            "for412": rel(FOR412_ARTIFACT),
            "for413": rel(FOR413_ARTIFACT),
            "for414": rel(FOR414_ARTIFACT),
            "for416": rel(FOR416_ARTIFACT),
            "rawRuntime": rel(RAW_RUNTIME),
        },
        "adapter": raw.get("adapter"),
        "producer": "scripts/validate_for417_m60_f16_aa_stencil_cover_isolated_color_target_runtime.py",
        "runtimeOwner": rel(SKWEBGPUDEVICE),
        "decision": (
            "FOR-417 adds an opt-in RGBA16Float scratch render pass with color-target blend disabled. "
            "It observes the selected M60 F16 AA stencil-cover output directly and correlates it with "
            "FOR-412, FOR-414, and FOR-416."
        ),
        "classification": classification,
        "globalClassification": classification,
        "allowedClassifications": sorted(ALLOWED_CLASSIFICATIONS),
        "supportClaim": False,
        "promoted": False,
        "correctionAppliedByDefault": False,
        "defaultRenderingChanged": False,
        "thresholdChanged": False,
        "scoringChanged": False,
        "guards": {
            "isolatedColorTargetRuntime": {
                "guardId": GUARD,
                "enabledForEvidenceRun": True,
                "enabledByDefault": False,
                "defaultRenderingChanged": False,
            },
            "sourceRuntimeInputs": {
                "for412ShaderReturnDiagnostic": (
                    "kanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled"
                ),
                "for414PostDrawReadback": "kanvas.webgpu.m60F16DirectPassWriteHook.enabled",
                "for416DerivedAbsence": rel(FOR416_ARTIFACT),
            },
        },
        "scope": {
            "selectedPixelCount": 16,
            "mutationRecordCount": len(records),
            "drawsInspected": [1, 3],
            "drawsContext": sorted(runtime_events),
            "pipelineFamily": "StencilCoverAaPolygonDraw",
            "blendMode": "kSrcOver",
            "scratchFormat": "RGBA16Float",
            "generalizedOutsideM60F16": False,
        },
        "comparisonPolicy": {
            "tolerance": MATCH_TOLERANCE,
            "sourceField": "FOR-412 sourceColorSentToBlend",
            "isolatedTargetField": "FOR-417 isolatedColorTarget.combinedOutputRgbaFloat",
            "mutationCheck": "SrcOver(scratchOutput, beforeRgbaFloat) equals afterRgbaFloat within tolerance",
            "f16ReconstructionTolerance": F16_RECONSTRUCTION_TOLERANCE,
            "noSyntheticShaderReturn": True,
            "missingShaderReturnTreatedAsZero": False,
            "missingIsolatedTargetTreatedAsZero": False,
            "for400UsedAsDirectProof": False,
        },
        "sourceCodeAudit": source_audit(),
        "runtimeSummary": {
            "rawRuntimeArtifact": rel(RAW_RUNTIME),
            "eventsObserved": len(runtime_events),
            "mutatingDrawCounts": {str(draw): draw_counts[draw] for draw in sorted(draw_counts)},
            "scratchSamplesAvailableForMutationRecords": len(records),
            "nonZeroScratchOutputCount": sum(
                not is_zero(record["isolatedColorTarget"]["combinedOutputRgbaFloat"]) for record in records
            ),
            "scratchSourceOverMatchesMutationCount": sum(
                record["scratchSourceOverBeforeVsMutationDelta"]["withinTolerance"] for record in records
            ),
            "sourceVsScratchDivergenceCount": sum(
                not record["sourceVsScratchDelta"]["withinTolerance"] for record in records
            ),
            "byClassification": dict(Counter(record["classification"] for record in records)),
        },
        "mutationRecords": records,
        "nonGoalsPreserved": [
            "No rendering correction is applied.",
            "The diagnostic guard is disabled by default.",
            "No default rendering behavior changes.",
            "No support or promotion claim is made for M60 F16.",
            "No score, threshold, route, fallback, or scene promotion policy changes.",
            "No extension outside M60 F16 / StencilCoverAaPolygonDraw / kSrcOver.",
            "No synthetic zero source is introduced.",
            "No Ganesh, Graphite, or SkSL compiler work is introduced.",
        ],
        "classificationReason": (
            "The no-blend scratch target produces non-zero color-target outputs for all 16 "
            "zero-return mutating transitions. Those values diverge from the FOR-412 storage "
            "side-channel zeros, and SrcOver(scratch, dstBefore) matches the immediate FOR-414 "
            "post-draw mutations."
        ),
        "nextStep": (
            "Investigate why the FOR-412 storage side-channel reports zero while the actual "
            "color-target output from the same bounded cover draw is non-zero: the suspect moves "
            "inside the diagnostic shader-return capture path, not fixed-function blend/load/store."
        ),
        "validationCommands": EXPECTED_VALIDATION_COMMANDS,
    }


def validate_artifact(data: dict[str, Any]) -> str:
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    classification = data.get("classification")
    require(classification == data.get("globalClassification"), "classification mismatch")
    require(classification in ALLOWED_CLASSIFICATIONS, "unexpected classification")
    require(classification == "isolated-color-target-output-nonzero-matches-mutation", "classification changed")
    for key in (
        "supportClaim",
        "promoted",
        "correctionAppliedByDefault",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
    ):
        require(data.get(key) is False, f"{key} must remain false")
    guard = data["guards"]["isolatedColorTargetRuntime"]
    require(guard["guardId"] == GUARD, "guard id changed")
    require(guard["enabledByDefault"] is False, "guard must be disabled by default")
    require(data["scope"]["drawsInspected"] == [1, 3], "draw scope changed")
    require(data["scope"]["mutationRecordCount"] == 16, "mutation count changed")
    require(data["runtimeSummary"]["mutatingDrawCounts"] == {"1": 6, "3": 10}, "draw split changed")
    require(data["runtimeSummary"]["scratchSamplesAvailableForMutationRecords"] == 16, "missing scratch samples")
    require(data["runtimeSummary"]["nonZeroScratchOutputCount"] == 16, "scratch output zero detected")
    require(data["runtimeSummary"]["scratchSourceOverMatchesMutationCount"] == 16, "scratch did not match mutation")
    require(data["runtimeSummary"]["sourceVsScratchDivergenceCount"] == 16, "scratch did not diverge from source")
    records = data.get("mutationRecords")
    require(isinstance(records, list) and len(records) == 16, "mutationRecords must contain 16 entries")
    require(sorted(pixel_key(record) for record in records) == sorted(EXPECTED_POINTS), "point set changed")
    for record in records:
        require(record["classification"] == classification, "record classification drift")
        require(record["drawIndex"] in {1, 3}, "unexpected record draw")
        require(record["isolatedColorTarget"]["available"] is True, "scratch output unavailable")
        require(record["isolatedColorTarget"]["scratchTargetEncoded"] is True, "scratch target absent")
        require(record["sourceVsScratchDelta"]["withinTolerance"] is False, "source/scratch should diverge")
        require(record["scratchSourceOverBeforeVsMutationDelta"]["withinTolerance"] is True, "scratch mismatch")
        for subdraw in record["sourceColorSentToBlendSubdraws"]:
            require(subdraw["captureSynthetic"] is False, "synthetic source forbidden")
            require(is_zero(require_rgba(subdraw["sourceColorSentToBlend"], "source")), "source not zero")
    for command in EXPECTED_VALIDATION_COMMANDS:
        require(command in data["validationCommands"], f"missing validation command {command}")
    require(all(data["sourceCodeAudit"]["checks"].values()), "source audit failed")
    return str(classification)


def write_report(data: dict[str, Any]) -> None:
    summary = data["runtimeSummary"]
    report = f"""# FOR-417 M60 F16 AA stencil-cover isolated color-target runtime

Date: 2026-06-05

## Result

Global classification: `{data["globalClassification"]}`.

FOR-417 adds an opt-in runtime diagnostic guarded by `{GUARD}`. The guard is disabled by default. The default render path, route, score, thresholds, fallback policy, and promotion state are unchanged.

## Evidence

- Source draft memory: `{SOURCE_DRAFT_MEMORY}`
- Source finding: `{SOURCE_FINDING}`
- Raw runtime snapshot: `{rel(RAW_RUNTIME)}`
- Final artifact: `{rel(ARTIFACT)}`
- Runtime owner: `{rel(SKWEBGPUDEVICE)}`
- Test sink: `{rel(WEBGPUSINK)}`
- Capture test: `{rel(CAPTURE_TEST)}`

## Scope

- Selected pixels: {data["scope"]["selectedPixelCount"]}
- Zero-return mutating transitions covered: {data["scope"]["mutationRecordCount"]}
- Mutating draw counts: {summary["mutatingDrawCounts"]}
- Pipeline family: `{data["scope"]["pipelineFamily"]}`
- Blend mode: `{data["scope"]["blendMode"]}`
- Scratch format: `{data["scope"]["scratchFormat"]}`

## Observation

The scratch pass replays the bounded M60 F16 `StencilCoverAaPolygonDraw` cover draw into a separate `RGBA16Float` color target. The color target disables blend with `blend = null`, uses a separate depth/stencil texture, and is read through the existing 16-point compute readback shader.

All 16 FOR-413 mutating transitions have available scratch samples. The scratch output is non-zero for all 16 records, diverges from the zero FOR-412 storage side-channel, and `SrcOver(scratchOutput, dstBefore)` matches the FOR-414 immediate post-draw mutation for all 16.

## Summary

- Runtime events observed: {summary["eventsObserved"]}
- Scratch samples available for mutation records: {summary["scratchSamplesAvailableForMutationRecords"]}
- Non-zero scratch outputs: {summary["nonZeroScratchOutputCount"]}
- Scratch SrcOver matches mutation: {summary["scratchSourceOverMatchesMutationCount"]}
- Source-vs-scratch divergences: {summary["sourceVsScratchDivergenceCount"]}

## Interpretation

FOR-415 already excluded an obvious blend/render-pass descriptor mismatch. FOR-417 now shows the actual no-blend color-target output is non-zero and reconstructs the mutation. The remaining suspect is therefore the FOR-412 shader-return storage side-channel or its diagnostic capture path, not fixed-function blend/load/store.

## Non-goals preserved

- No rendering correction.
- No default behavior, threshold, score, route, fallback, or promotion change.
- No extension outside M60 F16 / `StencilCoverAaPolygonDraw` / `kSrcOver`.
- No synthetic zero source.
- No Ganesh, Graphite, or SkSL compiler work.

## Validation

Expected local commands:

```text
{chr(10).join(data["validationCommands"])}
```
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_report(classification: str) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "FOR-417",
        classification,
        SOURCE_DRAFT_MEMORY,
        GUARD,
        "blend = null",
        "SrcOver(scratchOutput, dstBefore)",
    ):
        require(needle in text, f"report missing: {needle}")


def main() -> None:
    data = build_artifact()
    ARTIFACT.parent.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    write_report(data)
    written = load_json(ARTIFACT)
    classification = validate_artifact(written)
    validate_report(classification)
    print(
        f"FOR-417 artifact validated: {classification}; "
        f"records={len(written['mutationRecords'])}; artifact={rel(ARTIFACT)}"
    )


if __name__ == "__main__":
    main()
