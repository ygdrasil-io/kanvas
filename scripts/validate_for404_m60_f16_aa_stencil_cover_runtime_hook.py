#!/usr/bin/env python3
"""Validate FOR-404 M60 F16 AA stencil-cover runtime-hook evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-404"
SCENE_ID = "m60-f16-aa-stencil-cover-runtime-hook-for404"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
SOURCE_MEMORY = (
    "global/kanvas/findings/"
    "for-403-refuse-le-hook-direct-pass-write-m60-f16-sans-frontiere-post-draw-pre-readback"
)
HOOK_GUARD = "kanvas.webgpu.m60F16DirectPassWriteHook.enabled"

ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / SCENE_ID
    / f"{SCENE_ID}.json"
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-404-m60-f16-aa-stencil-cover-runtime-hook.md"
)
FOR401_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-final-residual-origin-map-for401"
    / "m60-f16-final-residual-origin-map-for401.json"
)
FOR403_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-direct-pass-write-hook-for403"
    / "m60-f16-direct-pass-write-hook-for403.json"
)
WEBGPU_DEVICE = (
    PROJECT_ROOT
    / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
)
CAPTURE_TEST = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)
WEBGPU_SINK = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
GPU_RASTER_BUILD = PROJECT_ROOT / "gpu-raster/build.gradle.kts"

ALLOWED_CLASSIFICATIONS = {
    "aa-stencil-cover-post-pass-observed",
    "aa-stencil-cover-post-pass-readback-blocked",
    "aa-stencil-cover-pass-not-targeting-coordinate",
    "aa-stencil-cover-runtime-hook-inconclusive",
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
    raise SystemExit(f"FOR-404 validation failed: {message}")


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
        "WEBGPU_M60_F16_DIRECT_PASS_WRITE_HOOK_FLAG",
        "kanvas.webgpu.m60F16DirectPassWriteHook.enabled",
        "M60F16AaStencilCoverPostPassRuntimeHookSnapshot",
        "m60F16AaStencilCoverPostPassRuntimeHookSnapshot()",
        "recordM60F16AaStencilCoverPostPassRuntimeHook",
        "StencilCoverAaPolygonDraw",
        "aa-stencil-cover-post-pass-readback-blocked",
        "M60_F16_DIRECT_PASS_WRITE_HOOK_POINTS",
    ):
        require(needle in device, f"SkWebGpuDevice missing hook source: {needle}")
    require(
        "m60F16DirectPassWriteHookSnapshot" not in device,
        "FOR-404 must not replace FOR-403 with a broad direct pass-write snapshot",
    )

    capture = read_text(CAPTURE_TEST)
    for needle in (
        "writeM60F16AaStencilCoverRuntimeHook",
        "m60F16AaStencilCoverRuntimeHookJson",
        "for401SelectedResidualOriginPixels",
        "for400ContributionEvidenceUsedAsProof",
        "aa-stencil-cover-post-pass-readback-blocked",
        "62748",
        SOURCE_MEMORY,
    ):
        require(needle in capture, f"capture test missing FOR-404 source: {needle}")

    sink = read_text(WEBGPU_SINK)
    require(
        "aaStencilCoverPostPassRuntimeHookSnapshot" in sink,
        "WebGpuSink result must expose the FOR-404 snapshot",
    )
    build = read_text(GPU_RASTER_BUILD)
    require(
        f'System.getProperty("{HOOK_GUARD}")?.let' in build
        and f'systemProperty("{HOOK_GUARD}", it)' in build,
        "Gradle test property propagation missing for FOR-404 hook",
    )


def validate_source_artifacts() -> None:
    for401 = load_json(FOR401_ARTIFACT)
    require(for401.get("linear") == "FOR-401", "FOR-401 source Linear id changed")
    require(for401.get("classification") == "residual-visible-only-at-final-readback", "FOR-401 classification changed")
    summary = for401.get("residualOriginSummary")
    require(isinstance(summary, dict), "FOR-401 residualOriginSummary missing")
    require(summary.get("currentTotalResidual") == 62748, "FOR-401 total residual changed")
    require(summary.get("selectedResidualTotal") == 1560, "FOR-401 selected residual changed")
    require(summary.get("readbackOnlyUnknownCount") == 16, "FOR-401 unknown count changed")

    for403 = load_json(FOR403_ARTIFACT)
    require(for403.get("linear") == "FOR-403", "FOR-403 source Linear id changed")
    require(for403.get("classification") == "direct-pass-write-hook-inconclusive", "FOR-403 classification changed")
    require(for403.get("sourceMemory") == (
        "global/kanvas/findings/"
        "for-402-refuse-le-pass-write-probe-m60-f16-tant-que-le-hook-direct-manque"
    ), "FOR-403 source memory changed")
    hook = for403.get("directPassWriteHook")
    require(isinstance(hook, dict), "FOR-403 directPassWriteHook missing")
    require(hook.get("directPassWriteInstrumentationAvailable") is False, "FOR-403 availability changed")


def validate_artifact(data: dict[str, Any]) -> str:
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("sourceSceneId") == ROW_ID, "wrong source scene id")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "wrong FOR-403 finding")
    require(data.get("sourceArtifacts", {}).get("for403") == rel(FOR403_ARTIFACT), "FOR-403 artifact not linked")
    classification = data.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"unexpected classification: {classification!r}")
    require(classification == "aa-stencil-cover-post-pass-readback-blocked", "FOR-404 classification changed")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "taxonomy changed")
    require(data.get("supportClaim") is False, "supportClaim must remain false")
    require(data.get("promoted") is False, "promoted must remain false")
    require(data.get("correctionAppliedByDefault") is False, "correction must not be default")
    require(data.get("defaultRenderingChanged") is False, "default rendering must not change")

    guards = data.get("guards")
    require(isinstance(guards, dict), "guards missing")
    hook_guard = guards.get("aaStencilCoverPostPassRuntimeHook")
    require(isinstance(hook_guard, dict), "FOR-404 guard missing")
    require(hook_guard.get("guardId") == HOOK_GUARD, "wrong FOR-404 guard id")
    require(hook_guard.get("enabledForEvidenceRun") is True, "FOR-404 guard must be enabled for artifact")
    for name, guard in guards.items():
        require(isinstance(guard, dict), f"invalid guard entry: {name}")
        require(guard.get("enabledByDefault") is False, f"guard enabled by default: {name}")
    require(
        guards.get("coverageStencilContributionMap", {}).get("evidencePolicy")
        == "context-only-not-direct-write-proof",
        "FOR-400 policy changed",
    )

    context = data.get("sourceContext")
    require(isinstance(context, dict), "sourceContext missing")
    require(context.get("for403Classification") == "direct-pass-write-hook-inconclusive", "FOR-403 context changed")
    require(context.get("for403Finding") == SOURCE_MEMORY, "FOR-403 finding context changed")
    require(context.get("for400EvidencePolicy") == "context-only-not-direct-write-proof", "FOR-400 direct proof policy changed")
    require(context.get("for401CurrentTotalResidual") == 62748, "FOR-401 residual context changed")
    require(context.get("for401SelectedResidualTotal") == 1560, "FOR-401 selected residual context changed")
    require(context.get("for401SelectedPixelCount") == 16, "FOR-401 selected count context changed")

    runtime = data.get("runtimeHook")
    require(isinstance(runtime, dict), "runtimeHook missing")
    require(runtime.get("api") == "SkWebGpuDevice.m60F16AaStencilCoverPostPassRuntimeHookSnapshot()", "wrong runtime API")
    require(runtime.get("propertyName") == HOOK_GUARD, "wrong runtime property")
    require(runtime.get("enabled") is True, "runtime hook must be enabled")
    require(runtime.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "wrong pipeline family")
    require(runtime.get("sampleLimit") == 16, "sample limit changed")
    require(runtime.get("postPassReadbackAvailable") is False, "post-pass readback should remain blocked")
    events = runtime.get("events")
    require(isinstance(events, list) and events, "runtime events missing")
    for event in events:
        require(event.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "event pipeline changed")
        require(isinstance(event.get("drawIndex"), int), "event drawIndex missing")
        samples = event.get("samples")
        require(isinstance(samples, list) and len(samples) == 16, "event sample count changed")

    summary = data.get("postPassSummary")
    require(isinstance(summary, dict), "postPassSummary missing")
    require(summary.get("currentTotalResidual") == 62748, "current total residual changed")
    require(summary.get("currentMismatchPixels") == 1615, "current mismatch pixels changed")
    require(summary.get("selectedPixelCount") == 16, "selected count changed")
    require(summary.get("selectedResidualTotal") == 1560, "selected residual changed")
    require(summary.get("postPassObservedCount") == 0, "post-pass observed claim changed")
    require(summary.get("postPassReadbackBlockedCount") == 16, "blocked count changed")
    require(summary.get("passNotTargetingCoordinateCount") == 0, "not-targeting count changed")
    require(summary.get("runtimeHookInconclusiveCount") == 0, "inconclusive count changed")

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
        require(pixel.get("classification") == "aa-stencil-cover-post-pass-readback-blocked", f"pixel classification changed at {index}")
        post = pixel.get("postPass")
        require(isinstance(post, dict), f"postPass missing at {index}")
        require(post.get("observed") is False, f"post-pass observed must be false at {index}")
        require(post.get("readbackAvailable") is False, f"readback must be unavailable at {index}")
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
        "FOR-404 M60 F16 AA stencil-cover runtime hook",
        SOURCE_MEMORY,
        "FOR-401 current total residual: `62748`",
        "FOR-401 selected residual total: `1560`",
        classification,
        "Runtime API:",
        "SkWebGpuDevice.m60F16AaStencilCoverPostPassRuntimeHookSnapshot()",
        "Runtime events: `3`",
        "Post-pass readback blocked pixels: `16`",
        "FOR-400 contribution samples remain contextual only",
        "supportClaim=false",
        "promoted=false",
        "rtk python3 scripts/validate_for404_m60_f16_aa_stencil_cover_runtime_hook.py",
    ):
        require(needle in text, f"report missing: {needle}")


def main() -> None:
    validate_sources()
    validate_source_artifacts()
    data = load_json(ARTIFACT)
    classification = validate_artifact(data)
    validate_report(classification)
    print(f"FOR-404 validation passed: {classification}")


if __name__ == "__main__":
    main()
