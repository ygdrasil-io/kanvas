#!/usr/bin/env python3
"""Validate FOR-402 M60 F16 pass-write probe evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-402"
SCENE_ID = "m60-f16-pass-write-probe-for402"
DECISION = "M60_F16_PASS_WRITE_PROBE_RECORDED"
SOURCE_MEMORY = (
    "global/kanvas/findings/"
    "for-401-localise-le-residu-m60-f16-au-readback-final-sans-writer-identifie"
)
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"

FOR402_GUARD = "kanvas.webgpu.m60F16PassWriteProbe.enabled"
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
    / "2026-06-05-for-402-m60-f16-pass-write-probe.md"
)
FOR401_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-final-residual-origin-map-for401"
    / "m60-f16-final-residual-origin-map-for401.json"
)
FOR400_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-coverage-stencil-contribution-map-for400"
    / "m60-f16-coverage-stencil-contribution-map-for400.json"
)
CAPTURE_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
GPU_RASTER_BUILD = PROJECT_ROOT / "gpu-raster/build.gradle.kts"

EXPECTED_SELECTED = [
    (92, 75, [181, 191, 230, 255], [133, 150, 214, 255], {"r": 48, "g": 41, "b": 16, "a": 0}, 105, True, True),
    (91, 76, [181, 191, 230, 255], [133, 150, 214, 255], {"r": 48, "g": 41, "b": 16, "a": 0}, 105, True, True),
    (90, 77, [181, 191, 230, 255], [133, 150, 214, 255], {"r": 48, "g": 41, "b": 16, "a": 0}, 105, True, True),
    (89, 78, [181, 191, 230, 255], [133, 150, 214, 255], {"r": 48, "g": 41, "b": 16, "a": 0}, 105, True, True),
    (88, 79, [181, 191, 230, 255], [133, 150, 214, 255], {"r": 48, "g": 41, "b": 16, "a": 0}, 105, True, True),
    (87, 80, [181, 191, 230, 255], [133, 150, 214, 255], {"r": 48, "g": 41, "b": 16, "a": 0}, 105, True, True),
    (101, 37, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93, False, False),
    (102, 37, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93, False, False),
    (99, 38, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93, False, False),
    (100, 38, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93, False, False),
    (101, 38, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93, False, False),
    (102, 38, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93, False, False),
    (103, 38, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93, False, False),
    (104, 38, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93, False, False),
    (98, 39, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93, False, False),
    (99, 39, [0, 138, 76, 255], [68, 121, 68, 255], {"r": 68, "g": 17, "b": 8, "a": 0}, 93, False, False),
]

ALLOWED_CLASSIFICATIONS = {
    "final-residual-written-by-m60-aa-stencil-cover",
    "final-residual-written-by-other-draw",
    "final-residual-not-observed-before-readback",
    "pass-write-probe-inconclusive",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-402 validation failed: {message}")


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


def validate_source_artifacts() -> None:
    for401 = load_json(FOR401_ARTIFACT)
    require(for401.get("linear") == "FOR-401", "FOR-401 source artifact has wrong Linear id")
    require(for401.get("classification") == "residual-visible-only-at-final-readback", "FOR-401 classification changed")
    require(for401.get("supportClaim") is False, "FOR-401 supportClaim changed")
    require(for401.get("promoted") is False, "FOR-401 promoted changed")
    summary = for401.get("residualOriginSummary")
    require(isinstance(summary, dict), "FOR-401 residualOriginSummary missing")
    require(summary.get("currentTotalResidual") == 62748, "FOR-401 current residual changed")
    require(summary.get("currentMismatchPixels") == 1615, "FOR-401 mismatch pixels changed")
    require(summary.get("selectedResidualTotal") == 1560, "FOR-401 selected residual changed")
    require(summary.get("writtenByM60AaStencilCoverCount") == 0, "FOR-401 M60 writer count changed")
    require(summary.get("writtenByOtherPathCount") == 0, "FOR-401 other writer count changed")
    require(summary.get("readbackOnlyUnknownCount") == 16, "FOR-401 unknown writer count changed")

    source_selected = for401.get("selectedPixels")
    require(isinstance(source_selected, list), "FOR-401 selectedPixels missing")
    require(len(source_selected) == len(EXPECTED_SELECTED), "FOR-401 selected count changed")
    for index, (pixel, expected) in enumerate(zip(source_selected, EXPECTED_SELECTED, strict=True)):
        x, y, current, reference, channels, residual, in_for397, in_for400 = expected
        require((pixel.get("x"), pixel.get("y")) == (x, y), f"FOR-401 coordinate changed at {index}")
        require(pixel.get("currentGpuRgba") == current, f"FOR-401 GPU color changed at {index}")
        require(pixel.get("referenceRgba") == reference, f"FOR-401 reference color changed at {index}")
        require(pixel.get("residualByChannel") == channels, f"FOR-401 channels changed at {index}")
        require(pixel.get("residualTotal") == residual, f"FOR-401 residual changed at {index}")
        require(pixel.get("belongsToFor397Predicate") is in_for397, f"FOR-401 FOR-397 membership changed at {index}")
        require(pixel.get("belongsToFor400Window") is in_for400, f"FOR-401 FOR-400 membership changed at {index}")

    for400 = load_json(FOR400_ARTIFACT)
    require(for400.get("linear") == "FOR-400", "FOR-400 source artifact has wrong Linear id")
    require(for400.get("classification") == "predicate-window-zero-contribution", "FOR-400 classification changed")
    require(for400.get("supportClaim") is False, "FOR-400 supportClaim changed")
    require(for400.get("promoted") is False, "FOR-400 promoted changed")
    contribution = for400.get("contributionSummary")
    require(isinstance(contribution, dict), "FOR-400 contributionSummary missing")
    require(contribution.get("effectiveContributionCount") == 0, "FOR-400 effective contribution changed")


def validate_sources() -> None:
    capture = read_text(CAPTURE_TEST)
    build = read_text(GPU_RASTER_BUILD)
    for needle in (
        "writeM60F16PassWriteProbe",
        "m60F16PassWriteProbeJson",
        "passWriteProbePixelJson",
        "pass-write-probe-inconclusive",
        "final-residual-not-observed-before-readback",
        '"supportClaim": false',
        '"promoted": false',
        f'System.getProperty(FOR402_PASS_WRITE_PROBE_PROPERTY, "false").toBoolean()',
        f'"$FOR402_PASS_WRITE_PROBE_PROPERTY"',
        "directPassWriteInstrumentationAvailable",
        "SkWebGpuDevice render-pass draw submission or post-draw/pre-readback texture boundary",
        "proof of a pass write",
    ):
        require(needle in capture, f"capture test missing FOR-402 proof: {needle}")
    require(
        'for400EffectiveSelected > 0 -> "final-residual-written-by-m60-aa-stencil-cover"' not in capture,
        "FOR-402 must not convert FOR-400 contribution evidence into a pass-write claim",
    )
    for needle in (
        f'System.getProperty("{FOR402_GUARD}")?.let',
        f'systemProperty("{FOR402_GUARD}", it)',
    ):
        require(needle in build, f"Gradle test property propagation missing: {needle}")


def validate_artifact(data: dict[str, Any]) -> str:
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("sourceSceneId") == ROW_ID, "wrong source row id")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "wrong Basic Memory source")
    require(data.get("decision") == DECISION, "wrong decision")
    classification = data.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"unexpected classification: {classification!r}")
    require(classification == "pass-write-probe-inconclusive", "classification changed")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications changed")
    require(data.get("supportClaim") is False, "supportClaim must remain false")
    require(data.get("promoted") is False, "promoted must remain false")
    require(data.get("correctionAppliedByDefault") is False, "correction must not be default")

    guards = data.get("guards")
    require(isinstance(guards, dict), "guards block missing")
    require(guards.get("passWriteProbe", {}).get("guardId") == FOR402_GUARD, "FOR-402 guard missing")
    require(guards.get("passWriteProbe", {}).get("enabledForEvidenceRun") is True, "FOR-402 evidence guard not enabled")
    require(guards.get("passWriteProbe", {}).get("enabledByDefault") is False, "FOR-402 default changed")
    require(guards.get("finalResidualOriginMap", {}).get("guardId") == FOR401_GUARD, "FOR-401 guard missing")
    require(guards.get("coverageStencilContributionMap", {}).get("guardId") == FOR400_GUARD, "FOR-400 guard missing")
    require(guards.get("boundedRuntimeCorrection", {}).get("guardId") == FOR398_GUARD, "FOR-398 guard missing")
    require(guards.get("fragmentLaneDiagnostic", {}).get("guardId") == FRAGMENT_GUARD, "FOR-396 guard missing")
    require(guards.get("bandMetadataTransport", {}).get("guardId") == TRANSPORT_GUARD, "FOR-394 guard missing")
    for name, guard in guards.items():
        require(isinstance(guard, dict), f"guard entry invalid: {name}")
        require(guard.get("enabledByDefault") is False, f"guard must be disabled by default: {name}")

    finding = data.get("sourceFinding")
    require(isinstance(finding, dict), "sourceFinding missing")
    require(finding.get("classification") == "residual-visible-only-at-final-readback", "source finding classification changed")
    require(finding.get("selectedResidualTotal") == 1560, "source finding selected residual changed")
    require(finding.get("writtenByM60AaStencilCover") == 0, "source finding M60 writer changed")
    require(finding.get("writtenByOtherPath") == 0, "source finding other writer changed")
    require(finding.get("readbackOnlyUnknown") == 16, "source finding unknown writer changed")

    scope = data.get("probeScope")
    require(isinstance(scope, dict), "probeScope missing")
    require(scope.get("sampleSource") == "exact FOR-401 final residual selection", "wrong sample source")
    require(scope.get("sampleLimit") == 16, "sample limit changed")
    require(scope.get("deterministicTieBreak") == ["residualTotal desc", "y asc", "x asc"], "tie-break changed")
    require(scope.get("directPassWriteInstrumentationAvailable") is False, "direct writer instrumentation should be absent")
    require("m60F16CoverageStencilContributionMapSnapshot" in scope.get("availableWriterEvidence", ""), "available evidence API missing")
    require("post-draw/pre-readback" in scope.get("missingInstrumentationPoint", ""), "missing instrumentation point is not precise")

    summary = data.get("passWriteSummary")
    require(isinstance(summary, dict), "passWriteSummary missing")
    require(summary.get("currentTotalResidual") == 62748, "current total residual changed")
    require(summary.get("currentMismatchPixels") == 1615, "current mismatch pixels changed")
    require(summary.get("selectedPixelCount") == 16, "selected count changed")
    require(summary.get("selectedResidualTotal") == 1560, "selected residual total changed")
    require(summary.get("selectedInFor397PredicateCount") == 6, "FOR-397 selected overlap changed")
    require(summary.get("selectedInFor400WindowCount") == 6, "FOR-400 selected overlap changed")
    require(summary.get("selectedOutsideFor400WindowCount") == 10, "outside FOR-400 selected count changed")
    require(summary.get("m60AaStencilCoverWriteCount") == 0, "M60 writer count changed")
    require(summary.get("otherDrawWriteCount") == 0, "other writer count changed")
    require(summary.get("notObservedBeforeReadbackCount") == 0, "not-observed count should not be claimed without direct hook")
    require(summary.get("passWriteProbeInconclusiveCount") == 16, "inconclusive count changed")
    require(summary.get("for400EffectiveSelectedContributionCount") == 0, "FOR-400 selected contribution changed")

    selected = data.get("selectedPixels")
    require(isinstance(selected, list), "selectedPixels missing")
    require(len(selected) == len(EXPECTED_SELECTED), "selected pixel count changed")
    for index, (pixel, expected) in enumerate(zip(selected, EXPECTED_SELECTED, strict=True)):
        require(isinstance(pixel, dict), f"selected pixel {index} must be an object")
        x, y, current, reference, channels, residual, in_for397, in_for400 = expected
        require((pixel.get("x"), pixel.get("y")) == (x, y), f"selected coordinate changed at {index}")
        require(pixel.get("currentGpuRgba") == current, f"currentGpuRgba changed at {index}")
        require(pixel.get("referenceRgba") == reference, f"referenceRgba changed at {index}")
        require(pixel.get("residualByChannel") == channels, f"residualByChannel changed at {index}")
        require(pixel.get("residualTotal") == residual, f"selected residual changed at {index}")
        require(pixel.get("belongsToFor397Predicate") is in_for397, f"FOR-397 membership changed at {index}")
        require(pixel.get("belongsToFor400Window") is in_for400, f"FOR-400 membership changed at {index}")
        require(pixel.get("for401AttributionCandidate") == "readbackOnlyUnknown", f"FOR-401 attribution changed at {index}")
        require(pixel.get("classification") == "pass-write-probe-inconclusive", f"pixel classification changed at {index}")
        observed = pixel.get("observedWrite")
        require(isinstance(observed, dict), f"observedWrite missing at {index}")
        require(observed.get("observed") is False, f"observed write should be false at {index}")
        require(observed.get("available") is False, f"direct write evidence should be unavailable at {index}")
        require(observed.get("m60AaStencilCoverWriteObserved") is False, f"M60 writer should not be claimed at {index}")
        require(observed.get("otherDrawWriteObserved") is False, f"other writer should not be claimed at {index}")
        require(observed.get("drawId") is None, f"drawId should be unavailable at {index}")
        require(observed.get("pipelineFamily") is None, f"pipeline family should be unavailable at {index}")
        require(observed.get("for400EffectiveContribution") is False, f"FOR-400 effective contribution should be false at {index}")
        require("No direct per-draw framebuffer write hook" in observed.get("reason", ""), f"missing refusal reason at {index}")

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
    require("draw submission" in next_step and "pre-readback" in next_step, "next step must name the next hook")
    commands = data.get("validationCommands")
    require(isinstance(commands, list), "validationCommands missing")
    require(
        "rtk python3 scripts/validate_for402_m60_f16_pass_write_probe.py" in commands,
        "FOR-402 validator command missing",
    )
    return str(classification)


def validate_report(classification: str) -> None:
    text = read_text(REPORT)
    for needle in (
        "FOR-402 M60 F16 pass-write probe",
        SOURCE_MEMORY,
        classification,
        "Selected residual total: `1560`",
        "M60 AA stencil-cover writes observed: `0`",
        "Other draw writes observed: `0`",
        "Pass-write probe inconclusive: `16`",
        "supportClaim=false",
        "promoted=false",
        "No correction was applied.",
        "SkWebGpuDevice render-pass draw submission",
        "rtk python3 scripts/validate_for402_m60_f16_pass_write_probe.py",
    ):
        require(needle in text, f"report missing: {needle}")


def main() -> None:
    validate_source_artifacts()
    validate_sources()
    data = load_json(ARTIFACT)
    classification = validate_artifact(data)
    validate_report(classification)
    print(f"FOR-402 validation passed: {classification}")


if __name__ == "__main__":
    main()
