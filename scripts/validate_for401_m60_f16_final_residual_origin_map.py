#!/usr/bin/env python3
"""Validate FOR-401 M60 F16 final residual origin-map evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-401"
SCENE_ID = "m60-f16-final-residual-origin-map-for401"
DECISION = "M60_F16_FINAL_RESIDUAL_ORIGIN_MAP_RECORDED"
SOURCE_MEMORY = (
    "global/kanvas/findings/"
    "for-400-prouve-que-la-fenetre-coverage-stencil-m60-f16-autour-des-pixels-for-397-ne-contribue-pas"
)
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"

FOR401_GUARD = "kanvas.webgpu.m60F16FinalResidualOriginMap.enabled"
FOR400_GUARD = "kanvas.webgpu.m60F16CoverageStencilContributionMap.enabled"
FOR398_GUARD = "kanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled"
FRAGMENT_GUARD = "kanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled"
TRANSPORT_GUARD = "kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled"

ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / SCENE_ID
    / f"{SCENE_ID}.json"
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-401-m60-f16-final-residual-origin-map.md"
)
FOR400_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-coverage-stencil-contribution-map-for400"
    / "m60-f16-coverage-stencil-contribution-map-for400.json"
)
CAPTURE_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
GPU_RASTER_BUILD = PROJECT_ROOT / "gpu-raster/build.gradle.kts"

EXPECTED_FOR397_PIXELS = [
    (93, 74),
    (92, 75),
    (91, 76),
    (17, 77),
    (90, 77),
    (89, 78),
    (88, 79),
    (87, 80),
]
EXPECTED_FOR400_WINDOW = {
    (xx, yy)
    for x, y in EXPECTED_FOR397_PIXELS
    for yy in range(y - 1, y + 2)
    for xx in range(x - 1, x + 2)
}
EXPECTED_SELECTED = [
    (92, 75, 105, True, True),
    (91, 76, 105, True, True),
    (90, 77, 105, True, True),
    (89, 78, 105, True, True),
    (88, 79, 105, True, True),
    (87, 80, 105, True, True),
    (101, 37, 93, False, False),
    (102, 37, 93, False, False),
    (99, 38, 93, False, False),
    (100, 38, 93, False, False),
    (101, 38, 93, False, False),
    (102, 38, 93, False, False),
    (103, 38, 93, False, False),
    (104, 38, 93, False, False),
    (98, 39, 93, False, False),
    (99, 39, 93, False, False),
]
ALLOWED_CLASSIFICATIONS = {
    "residual-carried-outside-for400-window",
    "residual-carried-by-other-draw-path",
    "residual-visible-only-at-final-readback",
    "residual-origin-inconclusive",
}
ALLOWED_ATTRIBUTIONS = {
    "writtenByM60AaStencilCover",
    "writtenByOtherPath",
    "readbackOnlyUnknown",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-401 validation failed: {message}")


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


def pixel_set(pixels: Any) -> set[tuple[int, int]]:
    require(isinstance(pixels, list), "pixel list expected")
    out: set[tuple[int, int]] = set()
    for pixel in pixels:
        require(isinstance(pixel, dict), f"pixel entry must be object: {pixel!r}")
        x = pixel.get("x")
        y = pixel.get("y")
        require(isinstance(x, int) and isinstance(y, int), f"invalid pixel coordinate: {pixel!r}")
        out.add((x, y))
    return out


def validate_sources() -> None:
    for400 = load_json(FOR400_ARTIFACT)
    require(for400.get("linear") == "FOR-400", "FOR-400 source artifact has wrong Linear id")
    require(for400.get("classification") == "predicate-window-zero-contribution", "FOR-400 classification changed")
    require(for400.get("supportClaim") is False, "FOR-400 supportClaim changed")
    require(for400.get("promoted") is False, "FOR-400 promoted changed")
    summary = for400.get("contributionSummary")
    require(isinstance(summary, dict), "FOR-400 contributionSummary missing")
    require(summary.get("effectiveContributionCount") == 0, "FOR-400 effective contribution changed")
    require(summary.get("neighborEffectiveContributionCount") == 0, "FOR-400 neighbor contribution changed")
    render = for400.get("renderComparison")
    require(isinstance(render, dict), "FOR-400 renderComparison missing")
    require(render.get("currentTotalResidual") == 62748, "FOR-400 current residual changed")
    require(render.get("correctedTotalResidual") == 62748, "FOR-400 corrected residual changed")

    capture = read_text(CAPTURE_TEST)
    build = read_text(GPU_RASTER_BUILD)
    for needle in (
        "writeM60F16FinalResidualOriginMap",
        "m60F16FinalResidualOriginMapJson",
        "finalResidualOriginPixels",
        "residual-visible-only-at-final-readback",
        '"supportClaim": false',
        '"promoted": false',
        f'System.getProperty(FOR401_FINAL_RESIDUAL_ORIGIN_MAP_PROPERTY, "false").toBoolean()',
        f'"{FOR401_GUARD}"',
    ):
        require(needle in capture, f"capture test missing FOR-401 proof: {needle}")
    for needle in (
        f'System.getProperty("{FOR401_GUARD}")?.let',
        f'systemProperty("{FOR401_GUARD}", it)',
    ):
        require(needle in build, f"Gradle test property propagation missing: {needle}")


def validate_artifact(data: dict[str, Any]) -> str:
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("sourceSceneId") == ROW_ID, "wrong source row id")
    require(data.get("decision") == DECISION, "wrong decision")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "wrong Basic Memory source")
    classification = data.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"unexpected classification: {classification!r}")
    require(classification == "residual-visible-only-at-final-readback", "classification changed")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications changed")
    require(data.get("supportClaim") is False, "supportClaim must remain false")
    require(data.get("promoted") is False, "promoted must remain false")
    require(data.get("correctionAppliedByDefault") is False, "correction must not be default")

    guards = data.get("guards")
    require(isinstance(guards, dict), "guards block missing")
    require(guards.get("finalResidualOriginMap", {}).get("guardId") == FOR401_GUARD, "FOR-401 guard missing")
    require(guards.get("finalResidualOriginMap", {}).get("enabledForEvidenceRun") is True, "FOR-401 evidence guard not enabled")
    require(guards.get("finalResidualOriginMap", {}).get("enabledByDefault") is False, "FOR-401 default changed")
    require(guards.get("coverageStencilContributionMap", {}).get("guardId") == FOR400_GUARD, "FOR-400 guard missing")
    require(guards.get("coverageStencilContributionMap", {}).get("enabledByDefault") is False, "FOR-400 default changed")
    require(guards.get("boundedRuntimeCorrection", {}).get("guardId") == FOR398_GUARD, "FOR-398 guard missing")
    require(guards.get("fragmentLaneDiagnostic", {}).get("guardId") == FRAGMENT_GUARD, "FOR-396 guard missing")
    require(guards.get("bandMetadataTransport", {}).get("guardId") == TRANSPORT_GUARD, "FOR-394 guard missing")

    historical = data.get("historicalEvidence")
    require(isinstance(historical, dict), "historicalEvidence missing")
    require(historical.get("for397PixelCount") == 8, "FOR-397 pixel count changed")
    require(historical.get("for400WindowRadius") == 1, "FOR-400 radius changed")
    require(historical.get("for400WindowPixelCount") == 48, "FOR-400 window size changed")
    require(historical.get("for400RawReadbackSampleCount") == 144, "FOR-400 raw readback count changed")
    require(historical.get("for400EffectiveContributionCount") == 0, "FOR-400 effective contribution changed")
    require(historical.get("for400Classification") == "predicate-window-zero-contribution", "FOR-400 proof changed")
    require(historical.get("for400ResidualBefore") == 62748, "FOR-400 residual before changed")
    require(historical.get("for400ResidualAfter") == 62748, "FOR-400 residual after changed")

    policy = data.get("selectionPolicy")
    require(isinstance(policy, dict), "selectionPolicy missing")
    require(policy.get("sampleLimit") == 16, "sample limit changed")
    require(policy.get("selectedPixelCount") == 16, "selected count changed")
    require(policy.get("deterministicTieBreak") == ["residualTotal desc", "y asc", "x asc"], "tie-break changed")

    require(pixel_set(data.get("for397PredicatePixels")) == set(EXPECTED_FOR397_PIXELS), "FOR-397 pixels changed")
    require(pixel_set(data.get("for400WindowPixels")) == EXPECTED_FOR400_WINDOW, "FOR-400 window changed")

    summary = data.get("residualOriginSummary")
    require(isinstance(summary, dict), "residualOriginSummary missing")
    require(summary.get("currentTotalResidual") == 62748, "current total residual changed")
    require(summary.get("currentMismatchPixels") == 1615, "current mismatch pixels changed")
    require(summary.get("selectedResidualTotal") == 1560, "selected residual total changed")
    require(summary.get("selectedInFor397PredicateCount") == 6, "FOR-397 selected overlap changed")
    require(summary.get("selectedInFor400WindowCount") == 6, "FOR-400 selected overlap changed")
    require(summary.get("selectedOutsideFor400WindowCount") == 10, "outside FOR-400 selected count changed")
    require(summary.get("writtenByM60AaStencilCoverCount") == 0, "M60 writer count changed")
    require(summary.get("writtenByOtherPathCount") == 0, "other writer count changed")
    require(summary.get("readbackOnlyUnknownCount") == 16, "unknown writer count changed")

    selected = data.get("selectedPixels")
    require(isinstance(selected, list), "selectedPixels missing")
    require(len(selected) == len(EXPECTED_SELECTED), "selected pixel count changed")
    last_residual = None
    for index, (pixel, expected) in enumerate(zip(selected, EXPECTED_SELECTED, strict=True)):
        require(isinstance(pixel, dict), f"selected pixel {index} must be an object")
        x, y, residual, in_for397, in_for400 = expected
        require((pixel.get("x"), pixel.get("y")) == (x, y), f"selected coordinate changed at {index}")
        require(pixel.get("residualTotal") == residual, f"selected residual changed at {index}")
        require(pixel.get("belongsToFor397Predicate") is in_for397, f"FOR-397 membership changed at {index}")
        require(pixel.get("belongsToFor400Window") is in_for400, f"FOR-400 membership changed at {index}")
        require(pixel.get("attributionCandidate") in ALLOWED_ATTRIBUTIONS, f"bad attribution at {index}")
        require(pixel.get("attributionCandidate") == "readbackOnlyUnknown", f"unexpected attribution at {index}")
        rgba = pixel.get("currentGpuRgba")
        ref = pixel.get("referenceRgba")
        require(isinstance(rgba, list) and len(rgba) == 4, f"currentGpuRgba invalid at {index}")
        require(isinstance(ref, list) and len(ref) == 4, f"referenceRgba invalid at {index}")
        channels = pixel.get("residualByChannel")
        require(isinstance(channels, dict), f"residualByChannel invalid at {index}")
        require(set(channels) == {"r", "g", "b", "a"}, f"channel keys invalid at {index}")
        require(sum(channels.values()) == residual, f"channel sum mismatch at {index}")
        if last_residual is not None:
            require(residual <= last_residual, "selected pixels are not sorted by descending residual")
        last_residual = residual

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
        "generalizedOutsideM60F16",
    ):
        require(non_goals.get(key) is False, f"non-goal changed: {key}")

    next_step = data.get("nextStep", "")
    require(
        "pass-write probe" in next_step or "draw/pass write trace" in next_step,
        "next step must name writer tracing",
    )
    commands = data.get("validationCommands")
    require(isinstance(commands, list), "validationCommands missing")
    require(
        "rtk python3 scripts/validate_for401_m60_f16_final_residual_origin_map.py" in commands,
        "FOR-401 validator command missing",
    )
    return str(classification)


def validate_report(classification: str) -> None:
    text = read_text(REPORT)
    for needle in (
        "FOR-401 M60 F16 final residual origin map",
        SOURCE_MEMORY,
        classification,
        "Current total residual: `62748`",
        "Selected pixels in FOR-400 window: `6`",
        "`readbackOnlyUnknown`: `16`",
        "No correction was applied.",
        "M60 F16 remains unpromoted: `promoted=false`.",
        "draw/pass write trace",
        "rtk python3 scripts/validate_for401_m60_f16_final_residual_origin_map.py",
    ):
        require(needle in text, f"report missing: {needle}")


def main() -> None:
    validate_sources()
    data = load_json(ARTIFACT)
    classification = validate_artifact(data)
    validate_report(classification)
    print(f"FOR-401 validation passed: {classification}")


if __name__ == "__main__":
    main()
