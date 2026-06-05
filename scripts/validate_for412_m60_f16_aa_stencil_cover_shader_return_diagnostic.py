#!/usr/bin/env python3
"""Validate FOR-412 M60 F16 AA stencil-cover shader-return diagnostics."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-412"
SCENE_ID = "m60-f16-aa-stencil-cover-shader-return-diagnostic-for412"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
SOURCE_DRAFT_MEMORY = (
    "global/kanvas/tickets/drafts/"
    "brouillon-ticket-for-412-m60-f16-capturer-la-source-effective-envoyee-au-blend-aa-stencil-cover"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-411-rejoue-source-over-avec-dst-before-et-classe-les-16-pixels-en-divergence-post-pass"
)

ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / SCENE_ID
    / f"{SCENE_ID}.json"
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-412-m60-f16-aa-stencil-cover-shader-return-diagnostic.md"
)
FOR401_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-final-residual-origin-map-for401"
    / "m60-f16-final-residual-origin-map-for401.json"
)
FOR405_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-post-pass-readback-for405"
    / "m60-f16-aa-stencil-cover-post-pass-readback-for405.json"
)
FOR408_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-per-subdraw-hook-for408"
    / "m60-f16-aa-stencil-cover-per-subdraw-hook-for408.json"
)
FOR410_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-predraw-dst-readback-for410"
    / "m60-f16-aa-stencil-cover-predraw-dst-readback-for410.json"
)
FOR411_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-source-over-replay-with-predraw-dst-for411"
    / "m60-f16-source-over-replay-with-predraw-dst-for411.json"
)

FOR412_GUARD = "kanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled"
FOR410_GUARD = "kanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled"
FOR409_GUARD = "kanvas.webgpu.m60F16AaStencilCoverSourceOverReplay.enabled"
FOR408_GUARD = "kanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled"
FOR405_GUARD = "kanvas.webgpu.m60F16DirectPassWriteHook.enabled"
MATCH_TOLERANCE = 1e-6

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
    "shader-return-explains-post-pass",
    "for408-source-field-mismatch",
    "shader-return-zero-but-post-pass-colored",
    "shader-return-unavailable",
}
STRONG_CLASSIFICATIONS = {
    "shader-return-explains-post-pass",
    "for408-source-field-mismatch",
    "shader-return-zero-but-post-pass-colored",
}
EXPECTED_VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py",
    "rtk python3 scripts/validate_for411_m60_f16_source_over_replay_with_predraw_dst.py",
    "rtk python3 scripts/validate_for410_m60_f16_aa_stencil_cover_predraw_dst_readback.py",
    "rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py",
    "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true "
        "-Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled=true "
        "-Dkanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled=true "
        "-Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true "
        "-Dkanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled=true "
        "-Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true "
        ":gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-412 validation failed: {message}")


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


def require_rgba_float(value: Any, field: str) -> list[float]:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA float")
    out: list[float] = []
    for channel in value:
        require(isinstance(channel, (float, int)), f"{field} channel must be numeric")
        out.append(float(channel))
    return out


def require_rgba8(value: Any, field: str) -> None:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA8")
    for channel in value:
        require(isinstance(channel, int) and 0 <= channel <= 255, f"{field} channel must be byte")


def pixel_key(pixel: dict[str, Any]) -> tuple[int, int]:
    x = pixel.get("x")
    y = pixel.get("y")
    require(isinstance(x, int) and isinstance(y, int), "pixel coordinate missing")
    return x, y


def max_abs_delta(a: list[float], b: list[float]) -> float:
    return max(abs(a[index] - b[index]) for index in range(4))


def validate_source_artifact_links(sources: dict[str, Any]) -> None:
    expected = {
        "for401": (FOR401_ARTIFACT, "FOR-401", "residual-visible-only-at-final-readback"),
        "for405": (FOR405_ARTIFACT, "FOR-405", "aa-stencil-cover-post-pass-color-observed"),
        "for408": (FOR408_ARTIFACT, "FOR-408", "per-subdraw-framebuffer-state-unavailable"),
        "for410": (FOR410_ARTIFACT, "FOR-410", "predraw-dst-captured"),
        "for411": (FOR411_ARTIFACT, "FOR-411", "source-over-replay-differs-post-pass"),
    }
    for key, (path, linear, classification) in expected.items():
        require(sources.get(key) == rel(path), f"{linear} artifact link changed")
        source = load_json(path)
        require(source.get("linear") == linear, f"{linear} source Linear id changed")
        require(source.get("classification") == classification, f"{linear} source classification changed")


def validate_report(classification: str) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "FOR-412",
        classification,
        FOR412_GUARD,
        "shader-return-zero-but-post-pass-colored",
        "rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py",
    ):
        require(needle in text, f"report missing: {needle}")


def validate_artifact(data: dict[str, Any]) -> str:
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("sourceSceneId") == ROW_ID, "wrong source scene")
    require(data.get("sourceDraftMemory") == SOURCE_DRAFT_MEMORY, "wrong draft memory")
    require(data.get("sourceFinding") == SOURCE_FINDING, "wrong source finding")

    sources = data.get("sourceArtifacts")
    require(isinstance(sources, dict), "sourceArtifacts missing")
    validate_source_artifact_links(sources)

    classification = data.get("globalClassification")
    require(classification == data.get("classification"), "classification/globalClassification mismatch")
    require(classification in ALLOWED_CLASSIFICATIONS, "unexpected global classification")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "classification taxonomy changed")
    require(classification == "shader-return-zero-but-post-pass-colored", "global classification changed")

    for key in (
        "supportClaim",
        "promoted",
        "correctionAppliedByDefault",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
    ):
        require(data.get(key) is False, f"{key} must remain false")

    guards = data.get("guards")
    require(isinstance(guards, dict), "guards missing")
    require(guards.get("shaderReturnDiagnostic", {}).get("guardId") == FOR412_GUARD, "FOR-412 guard missing")
    require(guards.get("predrawDstReadback", {}).get("guardId") == FOR410_GUARD, "FOR-410 guard missing")
    require(guards.get("sourceOverReplay", {}).get("guardId") == FOR409_GUARD, "FOR-409 guard missing")
    require(guards.get("contributionIsolation", {}).get("guardId") == FOR408_GUARD, "FOR-408 guard missing")
    require(guards.get("postPassReadback", {}).get("guardId") == FOR405_GUARD, "FOR-405 guard missing")
    for name, guard in guards.items():
        require(guard.get("enabledByDefault") is False, f"{name} guard must be disabled by default")
    require(
        guards.get("sourceOverReplay", {}).get("enabledForEvidenceRun") is False,
        "FOR-412 must reuse the FOR-411 source artifact instead of rerunning source-over replay",
    )

    runtime = data.get("runtimeSnapshot")
    require(isinstance(runtime, dict), "runtimeSnapshot missing")
    require(runtime.get("api") == "SkWebGpuDevice.m60F16AaStencilCoverShaderReturnDiagnosticSnapshot()", "wrong API")
    require(runtime.get("propertyName") == FOR412_GUARD, "wrong runtime property")
    require(runtime.get("enabled") is True, "FOR-412 runtime snapshot must be enabled")
    require("before returning @location(0)" in str(runtime.get("observedBoundary")), "observed boundary too weak")
    require(runtime.get("sampleLimit") == 16, "runtime sample limit changed")

    scope = data.get("scope")
    require(isinstance(scope, dict), "scope missing")
    require(scope.get("selectedPixelCount") == 16, "selected pixel count must be 16")
    require(scope.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "wrong pipeline family")
    require(scope.get("blendMode") == "kSrcOver", "wrong blend mode")
    require(scope.get("generalizedOutsideM60F16") is False, "scope generalized outside M60 F16")

    policy = data.get("comparisonPolicy")
    require(isinstance(policy, dict), "comparisonPolicy missing")
    require(policy.get("noSyntheticShaderReturn") is True, "synthetic shader return must be forbidden")
    require(policy.get("for400UsedAsDirectProof") is False, "FOR-400 must not be direct proof")
    require(
        "derived from captured colorAfterColorFilter" in str(policy.get("correctedColorBeforeCoverageMeaning")),
        "correctedColorBeforeCoverage derivation must stay explicit",
    )
    require(abs(float(policy.get("tolerance")) - MATCH_TOLERANCE) < 1e-12, "tolerance changed")

    summary = data.get("shaderReturnSummary")
    require(isinstance(summary, dict), "shaderReturnSummary missing")
    require(summary.get("selectedPixelCount") == 16, "summary selected count changed")
    require(summary.get("observedShaderReturnPixelCount") == 16, "observed pixel count changed")
    require(summary.get("observedShaderReturnSubdrawCount") == 44, "observed subdraw count changed")
    require(summary.get("shaderReturnExplainsPostPassCount") == 0, "explains count changed")
    require(summary.get("for408SourceFieldMismatchCount") == 0, "mismatch count changed")
    require(summary.get("shaderReturnZeroButPostPassColoredCount") == 16, "zero/post-pass count changed")
    require(summary.get("shaderReturnUnavailableCount") == 0, "unavailable count changed")
    require(summary.get("predrawDstConsumedPixelCount") == 16, "predraw consumed count changed")
    require(summary.get("postPassObservedPixelCount") == 16, "post-pass observed count changed")

    selected = data.get("selectedPixels")
    require(isinstance(selected, list) and len(selected) == 16, "selectedPixels must contain 16 pixels")
    seen: list[tuple[int, int]] = []
    for pixel in selected:
        require(isinstance(pixel, dict), "selected pixel must be an object")
        key = pixel_key(pixel)
        require(key in EXPECTED_POINTS, f"unexpected coordinate: {key}")
        seen.append(key)
        pixel_class = pixel.get("classification")
        require(pixel_class in ALLOWED_CLASSIFICATIONS, f"unexpected pixel classification at {key}")
        require(pixel_class == "shader-return-zero-but-post-pass-colored", f"pixel classification changed at {key}")
        require(pixel.get("observedShaderReturnCount", 0) > 0, f"no shader return observed at {key}")
        require(pixel.get("sourceReturnedToBlendAvailable") is True, f"source return unavailable at {key}")
        require(pixel.get("captureSynthetic") is False, f"synthetic capture at {key}")
        require_rgba_float(pixel.get("dstBeforeRgbaFloat"), f"dstBefore at {key}")
        post = require_rgba_float(pixel.get("postPassObservedRgbaFloat"), f"postPass at {key}")
        require_rgba8(pixel.get("postPassObservedRgba8"), f"postPass RGBA8 at {key}")
        replay = require_rgba_float(pixel.get("replayedWithShaderReturnRgbaFloat"), f"shader replay at {key}")
        delta = pixel.get("shaderReturnReplayVsPostPassDelta")
        require(isinstance(delta, dict), f"shader replay delta missing at {key}")
        require(delta.get("withinTolerance") is False, f"zero/post-pass class needs out-of-tolerance delta at {key}")
        require(float(delta.get("maxChannelFloat", 0.0)) > MATCH_TOLERANCE, f"delta too small at {key}")
        require(max_abs_delta(replay, post) > MATCH_TOLERANCE, f"replay unexpectedly matches post-pass at {key}")

        shader_subdraws = pixel.get("shaderReturnSubdraws")
        require(isinstance(shader_subdraws, list), f"shaderReturnSubdraws missing at {key}")
        captured = [
            subdraw for subdraw in shader_subdraws
            if subdraw.get("shaderObserved") is True and subdraw.get("captureSynthetic") is False
        ]
        require(len(captured) == pixel.get("observedShaderReturnCount"), f"observed count mismatch at {key}")
        require(captured, f"no captured shader subdraw at {key}")
        for subdraw in captured:
            source = require_rgba_float(subdraw.get("sourceColorSentToBlend"), f"sourceColorSentToBlend at {key}")
            require(max(abs(channel) for channel in source) <= MATCH_TOLERANCE, f"shader return no longer zero at {key}")
            require_rgba_float(subdraw.get("sourceColorBeforeQuantization"), f"sourceColorBeforeQuantization at {key}")
            require_rgba_float(subdraw.get("sourceFieldUsedByFOR408Replay"), f"FOR-408 source at {key}")
            require_rgba_float(subdraw.get("sourceFieldUsedByFOR411AfterCoverage"), f"FOR-411 source at {key}")
            require(subdraw.get("classification") == "shader-return-captured", f"subdraw class changed at {key}")

        if pixel_class in STRONG_CLASSIFICATIONS:
            require(captured, f"strong classification without non-synthetic capture at {key}")

    require(set(seen) == set(EXPECTED_POINTS), "FOR-401 coordinate set changed")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "defaultRenderingChanged",
        "supportClaimRaised",
        "promoted",
        "thresholdChanged",
        "scoringChanged",
        "correctionApplied",
        "for400UsedAsDirectProof",
        "generalizedOutsideM60F16",
        "syntheticShaderReturnUsed",
    ):
        require(non_goals.get(key) is False, f"non-goal changed: {key}")

    require(data.get("validationCommands") == EXPECTED_VALIDATION_COMMANDS, "validation command list changed")
    return str(classification)


def main() -> None:
    data = load_json(ARTIFACT)
    classification = validate_artifact(data)
    validate_report(classification)
    print(f"FOR-412 validation passed: {classification}")


if __name__ == "__main__":
    main()
