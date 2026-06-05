#!/usr/bin/env python3
"""Validate FOR-403 M60 F16 direct pass-write hook refusal evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-403"
SCENE_ID = "m60-f16-direct-pass-write-hook-for403"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
SOURCE_MEMORY = (
    "global/kanvas/findings/"
    "for-402-refuse-le-pass-write-probe-m60-f16-tant-que-le-hook-direct-manque"
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
    / "2026-06-05-for-403-m60-f16-direct-pass-write-hook.md"
)
FOR400_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-coverage-stencil-contribution-map-for400"
    / "m60-f16-coverage-stencil-contribution-map-for400.json"
)
FOR401_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-final-residual-origin-map-for401"
    / "m60-f16-final-residual-origin-map-for401.json"
)
FOR402_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-pass-write-probe-for402"
    / "m60-f16-pass-write-probe-for402.json"
)
WEBGPU_DEVICE = (
    PROJECT_ROOT
    / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
)

ALLOWED_CLASSIFICATIONS = {
    "m60-aa-stencil-cover-write-observed",
    "other-draw-write-observed",
    "post-draw-pre-readback-boundary-required",
    "direct-pass-write-hook-inconclusive",
}
DIRECT_WRITE_CLASSIFICATIONS = {
    "m60-aa-stencil-cover-write-observed",
    "other-draw-write-observed",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-403 validation failed: {message}")


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


def comparable_pixel(pixel: dict[str, Any]) -> dict[str, Any]:
    return {
        "x": pixel.get("x"),
        "y": pixel.get("y"),
        "currentGpuRgba": pixel.get("currentGpuRgba"),
        "referenceRgba": pixel.get("referenceRgba"),
        "residualByChannel": pixel.get("residualByChannel"),
        "residualTotal": pixel.get("residualTotal"),
        "belongsToFor397Predicate": pixel.get("belongsToFor397Predicate"),
        "belongsToFor400Window": pixel.get("belongsToFor400Window"),
    }


def validate_source_artifacts() -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    for400 = load_json(FOR400_ARTIFACT)
    require(for400.get("linear") == "FOR-400", "FOR-400 source Linear id changed")
    require(for400.get("classification") == "predicate-window-zero-contribution", "FOR-400 classification changed")
    require(for400.get("supportClaim") is False, "FOR-400 supportClaim changed")
    require(for400.get("promoted") is False, "FOR-400 promoted changed")
    contribution = for400.get("contributionSummary")
    require(isinstance(contribution, dict), "FOR-400 contributionSummary missing")
    require(contribution.get("effectiveContributionCount") == 0, "FOR-400 effective contribution changed")

    for401 = load_json(FOR401_ARTIFACT)
    require(for401.get("linear") == "FOR-401", "FOR-401 source Linear id changed")
    require(for401.get("classification") == "residual-visible-only-at-final-readback", "FOR-401 classification changed")
    require(for401.get("supportClaim") is False, "FOR-401 supportClaim changed")
    require(for401.get("promoted") is False, "FOR-401 promoted changed")
    summary = for401.get("residualOriginSummary")
    require(isinstance(summary, dict), "FOR-401 residualOriginSummary missing")
    require(summary.get("currentTotalResidual") == 62748, "FOR-401 total residual changed")
    require(summary.get("currentMismatchPixels") == 1615, "FOR-401 mismatch count changed")
    require(summary.get("selectedResidualTotal") == 1560, "FOR-401 selected residual changed")
    require(summary.get("writtenByM60AaStencilCoverCount") == 0, "FOR-401 M60 writer count changed")
    require(summary.get("writtenByOtherPathCount") == 0, "FOR-401 other writer count changed")
    require(summary.get("readbackOnlyUnknownCount") == 16, "FOR-401 unknown writer count changed")
    selected = for401.get("selectedPixels")
    require(isinstance(selected, list), "FOR-401 selectedPixels missing")
    require(len(selected) == 16, "FOR-401 selected pixel count changed")

    for402 = load_json(FOR402_ARTIFACT)
    require(for402.get("linear") == "FOR-402", "FOR-402 source Linear id changed")
    require(for402.get("classification") == "pass-write-probe-inconclusive", "FOR-402 classification changed")
    require(for402.get("supportClaim") is False, "FOR-402 supportClaim changed")
    require(for402.get("promoted") is False, "FOR-402 promoted changed")
    scope = for402.get("probeScope")
    require(isinstance(scope, dict), "FOR-402 probeScope missing")
    require(scope.get("directPassWriteInstrumentationAvailable") is False, "FOR-402 direct hook availability changed")
    summary402 = for402.get("passWriteSummary")
    require(isinstance(summary402, dict), "FOR-402 passWriteSummary missing")
    require(summary402.get("m60AaStencilCoverWriteCount") == 0, "FOR-402 M60 write claim changed")
    require(summary402.get("otherDrawWriteCount") == 0, "FOR-402 other write claim changed")
    return for400, for401, for402


def validate_runtime_boundary_refusal() -> None:
    text = read_text(WEBGPU_DEVICE)
    for needle in (
        "private fun encodePendingDrawsToIntermediate",
        "public fun flush(): ByteArray",
        "internal fun flushDrawsOnly()",
        "context.queue.submit(listOf(encoder.finish()))",
        "target.encodeCopyToStaging(encoder)",
        "recordM60F16FragmentLaneDiagnostics(perDrawResources)",
        "m60F16CoverageStencilContributionMapSnapshot",
    ):
        require(needle in text, f"SkWebGpuDevice evidence missing: {needle}")
    require(
        "m60F16DirectPassWriteHookSnapshot" not in text,
        "runtime direct pass-write hook exists; FOR-403 refusal artifact must be regenerated",
    )


def validate_artifact(data: dict[str, Any], for401: dict[str, Any]) -> str:
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("sourceSceneId") == ROW_ID, "wrong source scene id")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "wrong Basic Memory source")
    require(data.get("decision") == "M60_F16_DIRECT_PASS_WRITE_HOOK_REFUSED", "wrong decision")
    classification = data.get("classification")
    require(classification == "direct-pass-write-hook-inconclusive", "FOR-403 classification changed")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "taxonomy changed")
    require(set(data.get("directWriteClassifications", [])) == DIRECT_WRITE_CLASSIFICATIONS, "direct-write taxonomy changed")
    require(data.get("supportClaim") is False, "supportClaim must remain false")
    require(data.get("promoted") is False, "promoted must remain false")
    require(data.get("correctionAppliedByDefault") is False, "correction must not be default")
    require(data.get("defaultRenderingChanged") is False, "default rendering must not change")

    guards = data.get("guards")
    require(isinstance(guards, dict), "guards missing")
    direct_guard = guards.get("directPassWriteHook")
    require(isinstance(direct_guard, dict), "directPassWriteHook guard missing")
    require(direct_guard.get("guardId") == "kanvas.webgpu.m60F16DirectPassWriteHook.enabled", "wrong direct guard id")
    require(direct_guard.get("enabledByDefault") is False, "direct hook guard default changed")
    require(direct_guard.get("registeredInRuntime") is False, "runtime hook should not be claimed")
    require(guards.get("coverageStencilContributionMap", {}).get("evidencePolicy") == "context-only-not-direct-write-proof", "FOR-400 policy changed")
    for name, guard in guards.items():
        require(isinstance(guard, dict), f"invalid guard entry: {name}")
        require(guard.get("enabledByDefault") is False, f"guard enabled by default: {name}")

    hook = data.get("directPassWriteHook")
    require(isinstance(hook, dict), "directPassWriteHook block missing")
    require(hook.get("directPassWriteInstrumentationAvailable") is False, "direct hook availability must be false")
    require("post-draw/pre-readback" in hook.get("requestedObservationBoundary", ""), "missing requested boundary")
    require("draw id and pipeline family" in hook.get("missingBoundary", ""), "missing precise blocker")
    require("contextual only" in hook.get("for400ContributionEvidencePolicy", ""), "FOR-400 policy must be explicit")

    context = data.get("sourceContext")
    require(isinstance(context, dict), "sourceContext missing")
    require(context.get("for401CurrentTotalResidual") == 62748, "FOR-401 residual context changed")
    require(context.get("for401CurrentMismatchPixels") == 1615, "FOR-401 mismatch context changed")
    require(context.get("for401SelectedResidualTotal") == 1560, "FOR-401 selected residual context changed")
    require(context.get("for402DirectPassWriteInstrumentationAvailable") is False, "FOR-402 availability context changed")
    require(context.get("for400EffectiveContributionCount") == 0, "FOR-400 contribution context changed")

    summary = data.get("passWriteSummary")
    require(isinstance(summary, dict), "passWriteSummary missing")
    require(summary.get("currentTotalResidual") == 62748, "current total residual changed")
    require(summary.get("currentMismatchPixels") == 1615, "current mismatch count changed")
    require(summary.get("selectedPixelCount") == 16, "selected count changed")
    require(summary.get("selectedResidualTotal") == 1560, "selected residual changed")
    require(summary.get("m60AaStencilCoverWriteObservedCount") == 0, "M60 write observation changed")
    require(summary.get("otherDrawWriteObservedCount") == 0, "other write observation changed")
    require(summary.get("postDrawPreReadbackBoundaryRequiredCount") == 16, "boundary-required count changed")
    require(summary.get("directPassWriteHookInconclusiveCount") == 16, "inconclusive count changed")
    require(summary.get("for400EffectiveSelectedContributionCount") == 0, "FOR-400 selected contribution changed")

    selected = data.get("selectedPixels")
    source_selected = for401.get("selectedPixels")
    require(isinstance(selected, list), "selectedPixels missing")
    require(isinstance(source_selected, list), "FOR-401 selectedPixels missing")
    require(len(selected) == len(source_selected) == 16, "selected pixel count mismatch")
    for index, (pixel, source_pixel) in enumerate(zip(selected, source_selected, strict=True)):
        require(comparable_pixel(pixel) == comparable_pixel(source_pixel), f"selected FOR-401 pixel changed at {index}")
        require(pixel.get("for401AttributionCandidate") == "readbackOnlyUnknown", f"FOR-401 attribution changed at {index}")
        require(pixel.get("classification") == "post-draw-pre-readback-boundary-required", f"pixel taxonomy changed at {index}")
        direct = pixel.get("directWrite")
        require(isinstance(direct, dict), f"directWrite missing at {index}")
        require(direct.get("observed") is False, f"direct write must not be observed at {index}")
        require(direct.get("available") is False, f"direct write must not be available at {index}")
        require(direct.get("drawId") is None, f"drawId must not be invented at {index}")
        require(direct.get("pipelineFamily") is None, f"pipelineFamily must not be invented at {index}")
        require(direct.get("beforeRgba") is None and direct.get("afterRgba") is None, f"before/after colors must not be invented at {index}")
        require(direct.get("for400ContributionEvidenceUsedAsProof") is False, f"FOR-400 was used as proof at {index}")
        require("FOR-400 context cannot identify a writer" in direct.get("reason", ""), f"missing FOR-400 refusal reason at {index}")

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
    return str(classification)


def validate_report(classification: str) -> None:
    text = read_text(REPORT)
    for needle in (
        "FOR-403 M60 F16 direct pass-write hook",
        SOURCE_MEMORY,
        "FOR-401 current total residual: `62748`",
        "FOR-401 selected residual total: `1560`",
        "FOR-402 classification: `pass-write-probe-inconclusive`",
        classification,
        "Direct pass-write hook available: `false`",
        "Pixels requiring post-draw/pre-readback boundary: `16`",
        "therefore retained only as context",
        "not converted into proof",
        "supportClaim=false",
        "promoted=false",
        "rtk python3 scripts/validate_for403_m60_f16_direct_pass_write_hook.py",
    ):
        require(needle in text, f"report missing: {needle}")


def main() -> None:
    _, for401, _ = validate_source_artifacts()
    validate_runtime_boundary_refusal()
    data = load_json(ARTIFACT)
    classification = validate_artifact(data, for401)
    validate_report(classification)
    print(f"FOR-403 validation passed: {classification}")


if __name__ == "__main__":
    main()
