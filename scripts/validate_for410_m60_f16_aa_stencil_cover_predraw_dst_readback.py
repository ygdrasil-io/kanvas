#!/usr/bin/env python3
"""Validate FOR-410 M60 F16 AA stencil-cover predraw dst readback evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-410"
SCENE_ID = "m60-f16-aa-stencil-cover-predraw-dst-readback-for410"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
GUARD = "kanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled"
FOR408_GUARD = "kanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled"
FOR405_GUARD = "kanvas.webgpu.m60F16DirectPassWriteHook.enabled"
BAND_METADATA_GUARD = "kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled"
SOURCE_DRAFT_MEMORY = (
    "global/kanvas/tickets/drafts/"
    "brouillon-ticket-for-410-m60-f16-capturer-letat-destination-avant-aa-stencil-cover-pour-le-replay-source-over"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-409-confirme-que-le-replay-source-over-m60-f16-manque-encore-letat-initial-destination"
)

ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / SCENE_ID
    / f"{SCENE_ID}.json"
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
FOR409_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-source-over-replay-for409"
    / "m60-f16-aa-stencil-cover-source-over-replay-for409.json"
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-410-m60-f16-aa-stencil-cover-predraw-dst-readback.md"
)
CAPTURE_TEST = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)
RUNTIME_OWNER = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"

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
ALLOWED_GLOBAL_CLASSIFICATIONS = {
    "predraw-dst-captured",
    "predraw-dst-partial",
    "predraw-dst-unavailable",
}
ALLOWED_PIXEL_CLASSIFICATIONS = {
    "predraw-dst-captured",
    "predraw-dst-unavailable",
}
EXPECTED_VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for410_m60_f16_aa_stencil_cover_predraw_dst_readback.py",
    "rtk python3 scripts/validate_for409_m60_f16_aa_stencil_cover_source_over_replay.py",
    "rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py",
    "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true "
        "-Dkanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled=true "
        "-Dkanvas.webgpu.m60F16AaStencilCoverSourceOverReplay.enabled=true "
        "-Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true "
        "-Dkanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled=true "
        "-Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true "
        ":gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-410 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def require_rgba_float_or_null(value: Any, field: str) -> bool:
    if value is None:
        return False
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA float or null")
    for channel in value:
        require(isinstance(channel, (float, int)), f"{field} channel must be numeric")
    return True


def require_rgba8_or_null(value: Any, field: str) -> None:
    if value is None:
        return
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA8 or null")
    for channel in value:
        require(isinstance(channel, int) and 0 <= channel <= 255, f"{field} channel must be byte")


def validate_sources() -> None:
    test_text = CAPTURE_TEST.read_text(encoding="utf-8")
    runtime_text = RUNTIME_OWNER.read_text(encoding="utf-8")
    for needle in (
        "FOR410_PREDRAW_DST_READBACK_PROPERTY",
        GUARD,
        "writeM60F16AaStencilCoverPredrawDstReadback",
        "m60F16AaStencilCoverPredrawDstReadbackJson",
    ):
        require(needle in test_text, f"capture test missing source evidence: {needle}")
    for needle in (
        "WEBGPU_M60_F16_AA_STENCIL_COVER_PREDRAW_DST_READBACK_FLAG",
        GUARD,
        "m60F16AaStencilCoverPredrawDstReadbackSnapshot",
        "recordM60F16AaStencilCoverPredrawDstReadbacks",
        "StencilCoverAaPolygonDraw",
        "M60_F16_DIRECT_PASS_WRITE_HOOK_POINTS",
    ):
        require(needle in runtime_text, f"runtime owner missing source evidence: {needle}")


def validate_source_artifacts(sources: dict[str, Any]) -> None:
    expected = {
        "for401": (FOR401_ARTIFACT, "FOR-401", "residual-visible-only-at-final-readback"),
        "for405": (FOR405_ARTIFACT, "FOR-405", "aa-stencil-cover-post-pass-color-observed"),
        "for408": (FOR408_ARTIFACT, "FOR-408", "per-subdraw-framebuffer-state-unavailable"),
        "for409": (FOR409_ARTIFACT, "FOR-409", "source-over-replay-insufficient-inputs"),
    }
    for key, (path, linear, classification) in expected.items():
        require(sources.get(key) == rel(path), f"{linear} artifact link changed")
        source = load_json(path)
        require(source.get("linear") == linear, f"{linear} source Linear id changed")
        require(source.get("classification") == classification, f"{linear} source classification changed")


def pixel_key(pixel: dict[str, Any]) -> tuple[int, int]:
    x = pixel.get("x")
    y = pixel.get("y")
    require(isinstance(x, int) and isinstance(y, int), "pixel coordinate missing")
    return x, y


def validate_artifact(data: dict[str, Any]) -> str:
    validate_sources()
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("sourceSceneId") == ROW_ID, "wrong source scene")
    require(data.get("sourceDraftMemory") == SOURCE_DRAFT_MEMORY, "wrong draft memory")
    require(data.get("sourceFinding") == SOURCE_FINDING, "wrong source finding")

    sources = data.get("sourceArtifacts")
    require(isinstance(sources, dict), "sourceArtifacts missing")
    validate_source_artifacts(sources)

    classification = data.get("globalClassification")
    require(classification == data.get("classification"), "classification/globalClassification mismatch")
    require(classification in ALLOWED_GLOBAL_CLASSIFICATIONS, "unexpected global classification")
    require(
        set(data.get("allowedClassifications", [])) == ALLOWED_GLOBAL_CLASSIFICATIONS,
        "classification taxonomy changed",
    )
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
    predraw_guard = guards.get("predrawDstReadback")
    require(isinstance(predraw_guard, dict), "predraw guard missing")
    require(predraw_guard.get("guardId") == GUARD, "wrong predraw guard")
    require(predraw_guard.get("enabledForEvidenceRun") is True, "predraw guard must be enabled for evidence")
    require(predraw_guard.get("enabledByDefault") is False, "predraw guard must be disabled by default")
    band_guard = guards.get("bandMetadataTransport")
    require(isinstance(band_guard, dict), "band metadata guard missing")
    require(band_guard.get("guardId") == BAND_METADATA_GUARD, "wrong band metadata guard")
    require(band_guard.get("enabledForEvidenceRun") is True, "band metadata guard must be enabled for evidence")
    require(band_guard.get("enabledByDefault") is False, "band metadata guard must be disabled by default")
    require(guards.get("contributionIsolation", {}).get("guardId") == FOR408_GUARD, "FOR-408 guard missing")
    require(guards.get("postPassReadback", {}).get("guardId") == FOR405_GUARD, "FOR-405 guard missing")

    runtime = data.get("runtimeSnapshot")
    require(isinstance(runtime, dict), "runtimeSnapshot missing")
    require(runtime.get("api") == "SkWebGpuDevice.m60F16AaStencilCoverPredrawDstReadbackSnapshot()", "wrong API")
    require(runtime.get("propertyName") == GUARD, "wrong runtime property")
    require(runtime.get("sampleLimit") == 16, "runtime sample limit must be 16")
    require("before StencilCoverAaPolygonDraw" in str(runtime.get("observedBoundary")), "wrong observed boundary")

    scope = data.get("scope")
    require(isinstance(scope, dict), "scope missing")
    require(scope.get("selectedPixelCount") == 16, "selected pixel count must be 16")
    require(scope.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "wrong pipeline family")
    require(scope.get("blendMode") == "kSrcOver", "wrong blend mode")
    require(scope.get("generalizedOutsideM60F16") is False, "scope generalized outside M60 F16")

    selected = data.get("selectedPixels")
    require(isinstance(selected, list) and len(selected) == 16, "selectedPixels must contain 16 pixels")
    seen: list[tuple[int, int]] = []
    captured_count = 0
    replay_possible_count = 0
    inspected_count = 0
    for pixel in selected:
        require(isinstance(pixel, dict), "selected pixel must be object")
        key = pixel_key(pixel)
        require(key in EXPECTED_POINTS, f"unexpected coordinate: {key}")
        seen.append(key)
        pixel_class = pixel.get("classification")
        require(pixel_class in ALLOWED_PIXEL_CLASSIFICATIONS, f"unexpected pixel classification at {key}")
        draw_indices = pixel.get("drawIndexInspected")
        require(isinstance(draw_indices, list), f"drawIndexInspected missing at {key}")
        inspected_count += len(draw_indices)
        for draw_index in draw_indices:
            require(isinstance(draw_index, int), f"drawIndexInspected must contain ints at {key}")
        has_dst = require_rgba_float_or_null(pixel.get("dstBeforeRgbaFloat"), f"dstBeforeRgbaFloat at {key}")
        require_rgba8_or_null(pixel.get("dstBeforeRgba8"), f"dstBeforeRgba8 at {key}")
        if pixel_class == "predraw-dst-captured":
            require(has_dst, f"captured pixel missing dstBeforeRgbaFloat at {key}")
            captured_count += 1
        else:
            require(not has_dst, f"unavailable pixel must not synthesize dstBeforeRgbaFloat at {key}")

        relation = pixel.get("for408Relation")
        require(isinstance(relation, dict), f"for408Relation missing at {key}")
        require(isinstance(relation.get("observedReplayInputSubdrawCount"), int), f"FOR-408 count missing at {key}")
        require(isinstance(relation.get("subdraws"), list), f"FOR-408 subdraw relation missing at {key}")
        replay = pixel.get("for409Replay")
        require(isinstance(replay, dict), f"for409Replay missing at {key}")
        becomes_possible = replay.get("becomesPossible")
        require(isinstance(becomes_possible, bool), f"for409Replay.becomesPossible missing at {key}")
        if becomes_possible:
            replay_possible_count += 1
            require(has_dst, f"FOR-409 replay cannot become possible without dstBefore at {key}")
        predraw_samples = pixel.get("predrawSamples")
        require(isinstance(predraw_samples, list), f"predrawSamples missing at {key}")
        for sample in predraw_samples:
            require(isinstance(sample, dict), f"predraw sample must be object at {key}")
            require(sample.get("pipelineFamily") == "StencilCoverAaPolygonDraw", f"wrong sample pipeline at {key}")
            require_rgba_float_or_null(sample.get("dstBeforeRgbaFloat"), f"sample dstBefore at {key}")
            require_rgba8_or_null(sample.get("dstBeforeRgba8"), f"sample dstBeforeRgba8 at {key}")

    require(set(seen) == set(EXPECTED_POINTS), "FOR-401 coordinate set changed")
    if classification == "predraw-dst-captured":
        require(captured_count == 16, "predraw-dst-captured requires 16 non-null dstBeforeRgbaFloat pixels")
    elif classification == "predraw-dst-partial":
        require(0 < captured_count < 16, "predraw-dst-partial requires partial non-null dstBeforeRgbaFloat pixels")
    else:
        require(captured_count == 0, "predraw-dst-unavailable requires zero non-null dstBeforeRgbaFloat pixels")

    summary = data.get("predrawSummary")
    require(isinstance(summary, dict), "predrawSummary missing")
    require(summary.get("selectedPixelCount") == 16, "summary selected count changed")
    require(summary.get("capturedPixelCount") == captured_count, "captured count mismatch")
    require(summary.get("for409ReplayPossiblePixelCount") == replay_possible_count, "FOR-409 possible count mismatch")
    require(inspected_count > 0, "no predraw drawIndex inspected")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "defaultRenderingChanged",
        "supportClaimRaised",
        "promoted",
        "thresholdChanged",
        "scoringChanged",
        "correctionApplied",
        "generalizedOutsideM60F16",
        "syntheticDstBeforeUsed",
    ):
        require(non_goals.get(key) is False, f"non-goal changed: {key}")
    require(data.get("validationCommands") == EXPECTED_VALIDATION_COMMANDS, "validation command list changed")
    return str(classification)


def validate_report(classification: str) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "FOR-410 M60 F16 AA stencil-cover predraw dst readback",
        f"Classification: `{classification}`",
        GUARD,
        "supportClaim=false",
        "promoted=false",
        "correctionAppliedByDefault=false",
        "defaultRenderingChanged=false",
        "thresholdChanged=false",
        "scoringChanged=false",
        "rtk python3 scripts/validate_for410_m60_f16_aa_stencil_cover_predraw_dst_readback.py",
    ):
        require(needle in text, f"report missing: {needle}")


def main() -> None:
    data = load_json(ARTIFACT)
    classification = validate_artifact(data)
    validate_report(classification)
    print(f"FOR-410 validation passed: {classification}")


if __name__ == "__main__":
    main()
