#!/usr/bin/env python3
"""Validate FOR-405 M60 F16 AA stencil-cover post-pass readback evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-405"
SCENE_ID = "m60-f16-aa-stencil-cover-post-pass-readback-for405"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
SOURCE_MEMORY = (
    "global/kanvas/findings/"
    "for-404-ajoute-un-hook-runtime-borne-aa-stencil-cover-mais-bloque-sur-le-readback-post-pass"
)
HOOK_GUARD = "kanvas.webgpu.m60F16DirectPassWriteHook.enabled"
SHADER = "shaders/m60_f16_aa_stencil_cover_post_pass_readback_for405_diagnostic_only.wgsl"

ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / SCENE_ID
    / f"{SCENE_ID}.json"
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-405-m60-f16-aa-stencil-cover-post-pass-readback.md"
)
FOR401_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-final-residual-origin-map-for401"
    / "m60-f16-final-residual-origin-map-for401.json"
)
FOR404_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-runtime-hook-for404"
    / "m60-f16-aa-stencil-cover-runtime-hook-for404.json"
)
WEBGPU_DEVICE = (
    PROJECT_ROOT
    / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
)
WEBGPU_SINK = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
CAPTURE_TEST = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)
SHADER_FILE = PROJECT_ROOT / "gpu-raster/src/main/resources" / SHADER

ALLOWED_CLASSIFICATIONS = {
    "aa-stencil-cover-post-pass-color-observed",
    "aa-stencil-cover-post-pass-format-unsupported",
    "aa-stencil-cover-post-pass-copy-blocked",
    "aa-stencil-cover-post-pass-readback-inconclusive",
}

EXPECTED_SELECTED = [
    (92, 75, [181, 191, 230, 255], [133, 150, 214, 255], {"r": 48, "g": 41, "b": 16, "a": 0}, 105),
    (91, 76, [181, 191, 230, 255], [133, 150, 214, 255], {"r": 48, "g": 41, "b": 16, "a": 0}, 105),
    (90, 77, [181, 191, 230, 255], [133, 150, 214, 255], {"r": 48, "g": 41, "b": 16, "a": 0}, 105),
    (89, 78, [181, 191, 230, 255], [133, 150, 214, 255], {"r": 48, "g": 41, "b": 16, "a": 0}, 105),
    (88, 79, [181, 191, 230, 255], [133, 150, 214, 255], {"r": 48, "g": 41, "b": 16, "a": 0}, 105),
    (87, 80, [181, 191, 230, 255], [133, 150, 214, 255], {"r": 48, "g": 41, "b": 16, "a": 0}, 105),
    (101, 37, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93),
    (102, 37, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93),
    (99, 38, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93),
    (100, 38, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93),
    (101, 38, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93),
    (102, 38, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93),
    (103, 38, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93),
    (104, 38, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93),
    (98, 39, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93),
    (99, 39, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-405 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def read_text(path: Path) -> str:
    require(path.is_file(), f"missing file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def validate_sources() -> None:
    device = read_text(WEBGPU_DEVICE)
    for needle in (
        "M60F16AaStencilCoverPostPassReadbackSnapshot",
        "m60F16AaStencilCoverPostPassReadbackSnapshot()",
        "recordM60F16AaStencilCoverPostPassReadbacks",
        "m60F16AaStencilCoverPostPassReadbackPipeline",
        SHADER,
        "M60_F16_AA_STENCIL_COVER_POST_PASS_READBACK_SHADER",
        "M60_F16_AA_STENCIL_COVER_POST_PASS_READBACK_SAMPLE_COUNT",
        "aa-stencil-cover-post-pass-color-observed",
        "aa-stencil-cover-post-pass-format-unsupported",
        "aa-stencil-cover-post-pass-copy-blocked",
        "aa-stencil-cover-post-pass-readback-inconclusive",
        "M60_F16_DIRECT_PASS_WRITE_HOOK_POINTS",
        HOOK_GUARD,
    ):
        require(needle in device, f"SkWebGpuDevice missing FOR-405 source: {needle}")

    sink = read_text(WEBGPU_SINK)
    require(
        "aaStencilCoverPostPassReadbackSnapshot" in sink,
        "WebGpuSink result must expose the FOR-405 snapshot",
    )

    capture = read_text(CAPTURE_TEST)
    for needle in (
        "writeM60F16AaStencilCoverPostPassReadback",
        "m60F16AaStencilCoverPostPassReadbackJson",
        "for400UsedAsDirectProof",
        "context-only-not-direct-write-proof",
        SOURCE_MEMORY,
        "62748",
    ):
        require(needle in capture, f"capture test missing FOR-405 source: {needle}")

    shader = read_text(SHADER_FILE)
    for x, y, *_ in EXPECTED_SELECTED:
        require(f"vec2i({x}, {y})" in shader, f"shader missing FOR-401 coordinate {(x, y)}")
    require("index >= 16u" in shader, "shader must be bounded to 16 samples")
    require("textureLoad(intermediate_texture, point, 0)" in shader, "shader must textureLoad intermediate")


def validate_source_artifacts() -> None:
    for401 = load_json(FOR401_ARTIFACT)
    require(for401.get("linear") == "FOR-401", "FOR-401 source Linear id changed")
    require(for401.get("classification") == "residual-visible-only-at-final-readback", "FOR-401 classification changed")
    summary = for401.get("residualOriginSummary")
    require(isinstance(summary, dict), "FOR-401 residualOriginSummary missing")
    require(summary.get("currentTotalResidual") == 62748, "FOR-401 total residual changed")
    require(summary.get("selectedResidualTotal") == 1560, "FOR-401 selected residual changed")
    require(summary.get("readbackOnlyUnknownCount") == 16, "FOR-401 unknown count changed")

    for404 = load_json(FOR404_ARTIFACT)
    require(for404.get("linear") == "FOR-404", "FOR-404 source Linear id changed")
    require(for404.get("classification") == "aa-stencil-cover-post-pass-readback-blocked", "FOR-404 classification changed")
    runtime = for404.get("runtimeHook")
    require(isinstance(runtime, dict), "FOR-404 runtimeHook missing")
    require(runtime.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "FOR-404 pipeline changed")
    require(runtime.get("postPassReadbackAvailable") is False, "FOR-404 must remain blocked")
    summary404 = for404.get("postPassSummary")
    require(isinstance(summary404, dict), "FOR-404 postPassSummary missing")
    require(summary404.get("postPassReadbackBlockedCount") == 16, "FOR-404 blocked count changed")


def validate_artifact(data: dict[str, Any]) -> str:
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("sourceSceneId") == ROW_ID, "wrong source scene id")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "wrong FOR-404 source memory")
    require(data.get("sourceArtifacts", {}).get("for401") == rel(FOR401_ARTIFACT), "FOR-401 artifact not linked")
    require(data.get("sourceArtifacts", {}).get("for404") == rel(FOR404_ARTIFACT), "FOR-404 artifact not linked")
    classification = data.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"unexpected classification: {classification!r}")
    require(classification == "aa-stencil-cover-post-pass-color-observed", "FOR-405 did not observe post-pass color")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "taxonomy changed")
    require(data.get("supportClaim") is False, "supportClaim must remain false")
    require(data.get("promoted") is False, "promoted must remain false")
    require(data.get("correctionAppliedByDefault") is False, "correction must not be default")
    require(data.get("defaultRenderingChanged") is False, "default rendering must not change")

    guards = data.get("guards")
    require(isinstance(guards, dict), "guards missing")
    for key in ("aaStencilCoverPostPassRuntimeHook", "aaStencilCoverPostPassReadback"):
        guard = guards.get(key)
        require(isinstance(guard, dict), f"{key} guard missing")
        require(guard.get("guardId") == HOOK_GUARD, f"wrong {key} guard id")
        require(guard.get("enabledForEvidenceRun") is True, f"{key} must be enabled for artifact")
        require(guard.get("enabledByDefault") is False, f"{key} must be disabled by default")
    require(
        guards.get("coverageStencilContributionMap", {}).get("evidencePolicy")
        == "context-only-not-direct-write-proof",
        "FOR-400 policy changed",
    )

    context = data.get("sourceContext")
    require(isinstance(context, dict), "sourceContext missing")
    require(context.get("for404Classification") == "aa-stencil-cover-post-pass-readback-blocked", "FOR-404 context changed")
    require(context.get("for400EvidencePolicy") == "context-only-not-direct-write-proof", "FOR-400 context policy changed")
    require(context.get("for400UsedAsDirectProof") is False, "FOR-400 used as direct proof")
    require(context.get("for401CurrentTotalResidual") == 62748, "FOR-401 residual context changed")
    require(context.get("for401CurrentMismatchPixels") == 1615, "FOR-401 mismatch context changed")
    require(context.get("for401SelectedResidualTotal") == 1560, "FOR-401 selected residual context changed")
    require(context.get("for401SelectedPixelCount") == 16, "FOR-401 selected count context changed")

    runtime = data.get("runtimeReadback")
    require(isinstance(runtime, dict), "runtimeReadback missing")
    require(runtime.get("api") == "SkWebGpuDevice.m60F16AaStencilCoverPostPassReadbackSnapshot()", "wrong runtime API")
    require(runtime.get("propertyName") == HOOK_GUARD, "wrong runtime property")
    require(runtime.get("enabled") is True, "runtime readback must be enabled")
    require(runtime.get("diagnosticShader") == SHADER, "wrong diagnostic shader")
    require(runtime.get("intermediateFormat") == "RGBA16Float", "wrong intermediate format")
    require(runtime.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "wrong pipeline family")
    require(runtime.get("sampleLimit") == 16, "sample limit changed")
    require(runtime.get("eventCount") == 3, "event count changed")
    require(runtime.get("postPassReadbackAvailable") is True, "post-pass readback should be available")
    events = runtime.get("events")
    require(isinstance(events, list) and len(events) == 3, "runtime events missing")
    for event in events:
        require(event.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "event pipeline changed")
        require(isinstance(event.get("drawIndex"), int), "event drawIndex missing")
        require(event.get("copyAttempted") is True, "event copy was not attempted")
        require(event.get("copySucceeded") is True, "event copy did not succeed")
        samples = event.get("samples")
        require(isinstance(samples, list) and len(samples) == 16, "event sample count changed")
        for sample in samples:
            require(sample.get("classification") in ALLOWED_CLASSIFICATIONS, "event sample classification invalid")
            require(sample.get("readbackAttempted") is True, "sample readback not attempted")

    summary = data.get("postPassSummary")
    require(isinstance(summary, dict), "postPassSummary missing")
    require(summary.get("currentTotalResidual") == 62748, "current total residual changed")
    require(summary.get("currentMismatchPixels") == 1615, "current mismatch pixels changed")
    require(summary.get("selectedPixelCount") == 16, "selected count changed")
    require(summary.get("selectedResidualTotal") == 1560, "selected residual changed")
    require(summary.get("postPassObservedCount") == 16, "observed count changed")
    require(summary.get("postPassFormatUnsupportedCount") == 0, "format unsupported count changed")
    require(summary.get("postPassCopyBlockedCount") == 0, "copy blocked count changed")
    require(summary.get("postPassReadbackInconclusiveCount") == 0, "inconclusive count changed")

    selected = data.get("selectedPixels")
    require(isinstance(selected, list), "selectedPixels missing")
    require(len(selected) == len(EXPECTED_SELECTED), "selected pixel count changed")
    for index, (pixel, expected) in enumerate(zip(selected, EXPECTED_SELECTED, strict=True)):
        x, y, current, reference, channels, residual = expected
        require((pixel.get("x"), pixel.get("y")) == (x, y), f"coordinate changed at {index}")
        require(pixel.get("currentGpuRgba") == current, f"current color changed at {index}")
        require(pixel.get("referenceRgba") == reference, f"reference color changed at {index}")
        require(pixel.get("residualByChannel") == channels, f"channels changed at {index}")
        require(pixel.get("residualTotal") == residual, f"residual changed at {index}")
        require(pixel.get("for401AttributionCandidate") == "readbackOnlyUnknown", f"FOR-401 attribution changed at {index}")
        require(pixel.get("classification") == "aa-stencil-cover-post-pass-color-observed", f"pixel classification changed at {index}")
        post = pixel.get("postPass")
        require(isinstance(post, dict), f"postPass missing at {index}")
        require(post.get("observed") is True, f"post-pass not observed at {index}")
        require(post.get("readbackAvailable") is True, f"readback unavailable at {index}")
        require(post.get("observedRgba8") == current, f"observed RGBA8 does not match current GPU color at {index}")
        rgba_float = post.get("observedRgbaFloat")
        require(isinstance(rgba_float, list) and len(rgba_float) == 4, f"observed RGBA float missing at {index}")
        require(post.get("pipelineFamily") == "StencilCoverAaPolygonDraw", f"pixel pipeline changed at {index}")
        require(post.get("targetingStencilCoverAaDrawCount", 0) > 0, f"coordinate was not targeted at {index}")
        require(post.get("for400ContributionEvidenceUsedAsProof") is False, f"FOR-400 used as proof at {index}")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "defaultRenderingChanged",
        "supportClaimRaised",
        "promoted",
        "thresholdChanged",
        "scoringChanged",
        "correctionApplied",
        "for380BroadCorrectionReintroduced",
        "for400UsedAsDirectProof",
        "generalizedOutsideM60F16",
    ):
        require(non_goals.get(key) is False, f"non-goal changed: {key}")
    return str(classification)


def validate_report(classification: str) -> None:
    text = read_text(REPORT)
    for needle in (
        "FOR-405 M60 F16 AA stencil-cover post-pass readback",
        "FOR-404 classification: `aa-stencil-cover-post-pass-readback-blocked`",
        "FOR-401 current total residual: `62748`",
        "FOR-401 selected residual total: `1560`",
        "FOR-400 remains context only and is not used as direct writer proof.",
        "SkWebGpuDevice.m60F16AaStencilCoverPostPassReadbackSnapshot()",
        SHADER,
        classification,
        "Post-pass observed pixels: `16`",
        "supportClaim=false",
        "promoted=false",
        "FOR-400 remains `context-only-not-direct-write-proof`",
        "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
    ):
        require(needle in text, f"report missing: {needle}")


def main() -> None:
    validate_sources()
    validate_source_artifacts()
    data = load_json(ARTIFACT)
    classification = validate_artifact(data)
    validate_report(classification)
    print(f"FOR-405 validation passed: {classification}")


if __name__ == "__main__":
    main()
