#!/usr/bin/env python3
"""Validate FOR-408 M60 F16 AA stencil-cover per-subdraw hook evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-408"
SCENE_ID = "m60-f16-aa-stencil-cover-per-subdraw-hook-for408"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
GUARD = "kanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled"
BOUNDED_RUNTIME_CORRECTION_GUARD = "kanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled"
SOURCE_DRAFT_MEMORY = (
    "global/kanvas/tickets/drafts/"
    "brouillon-ticket-for-408-m60-f16-hook-per-subdraw-aa-stencil-cover-contribution-isolation"
)
SOURCE_FOR407_MEMORY = (
    "global/kanvas/findings/"
    "for-407-formalise-le-manque-de-donnees-per-subdraw-pour-isoler-la-cause-m60-f16"
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
    / "2026-06-05-for-408-m60-f16-aa-stencil-cover-per-subdraw-hook.md"
)
FOR401_ARTIFACT = (
    "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json"
)
FOR405_ARTIFACT = (
    "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-aa-stencil-cover-post-pass-readback-for405/"
    "m60-f16-aa-stencil-cover-post-pass-readback-for405.json"
)
FOR406_ARTIFACT = (
    "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-post-pass-reference-comparison-for406/"
    "m60-f16-post-pass-reference-comparison-for406.json"
)
FOR407_ARTIFACT = (
    "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-aa-stencil-cover-contribution-isolation-for407/"
    "m60-f16-aa-stencil-cover-contribution-isolation-for407.json"
)

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
    "coverage-aa-wrong",
    "source-color-wrong",
    "blend-source-over-wrong",
    "draw-order-or-accumulation-wrong",
    "per-subdraw-framebuffer-state-unavailable",
    "per-subdraw-inputs-captured",
    "per-subdraw-hook-no-samples",
    "per-subdraw-hook-disabled",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-408 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{path.relative_to(PROJECT_ROOT)} must contain an object")
    return data


def require_rgba_float_or_null(value: Any, field: str) -> None:
    if value is None:
        return
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA float or null")
    for channel in value:
        require(isinstance(channel, (float, int)), f"{field} channel must be numeric")


def validate_report(classification: str) -> None:
    require(REPORT.is_file(), f"missing report: {REPORT.relative_to(PROJECT_ROOT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "FOR-408 M60 F16 AA stencil-cover per-subdraw hook",
        f"Classification: `{classification}`",
        GUARD,
        "supportClaim=false",
        "promoted=false",
        "correctionAppliedByDefault=false",
        "defaultRenderingChanged=false",
        "FOR-400 remains context-only",
        "rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py",
    ):
        require(needle in text, f"report missing: {needle}")


def main() -> None:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("sourceSceneId") == ROW_ID, "wrong source scene")
    require(data.get("sourceDraftMemory") == SOURCE_DRAFT_MEMORY, "wrong draft memory")
    require(data.get("sourceMemory", {}).get("for407") == SOURCE_FOR407_MEMORY, "wrong FOR-407 memory")

    sources = data.get("sourceArtifacts")
    require(isinstance(sources, dict), "sourceArtifacts missing")
    require(sources.get("for401") == FOR401_ARTIFACT, "FOR-401 artifact link changed")
    require(sources.get("for405") == FOR405_ARTIFACT, "FOR-405 artifact link changed")
    require(sources.get("for406") == FOR406_ARTIFACT, "FOR-406 artifact link changed")
    require(sources.get("for407") == FOR407_ARTIFACT, "FOR-407 artifact link changed")

    classification = data.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, "unexpected classification")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "classification taxonomy changed")
    for key in ("supportClaim", "promoted", "correctionAppliedByDefault", "defaultRenderingChanged"):
        require(data.get(key) is False, f"{key} must remain false")

    guards = data.get("guards")
    require(isinstance(guards, dict), "guards missing")
    contribution = guards.get("contributionIsolation")
    require(isinstance(contribution, dict), "contribution guard missing")
    require(contribution.get("guardId") == GUARD, "wrong contribution guard")
    require(contribution.get("enabledForEvidenceRun") is True, "contribution guard must be enabled for evidence run")
    require(contribution.get("enabledByDefault") is False, "contribution guard must be disabled by default")
    bounded = guards.get("boundedRuntimeCorrection")
    require(isinstance(bounded, dict), "bounded runtime correction transport guard missing")
    require(bounded.get("guardId") == BOUNDED_RUNTIME_CORRECTION_GUARD, "wrong bounded runtime correction guard")
    require(bounded.get("enabledForEvidenceRun") is True, "bounded runtime correction transport must be declared")
    require(bounded.get("enabledByDefault") is False, "bounded runtime correction guard must be disabled by default")
    require(
        "diagnostic transport" in str(bounded.get("usage", "")),
        "bounded runtime correction usage must be framed as diagnostic transport",
    )

    runtime = data.get("runtimeSnapshot")
    require(isinstance(runtime, dict), "runtimeSnapshot missing")
    require(runtime.get("api") == "SkWebGpuDevice.m60F16AaStencilCoverContributionIsolationSnapshot()", "wrong API")
    require(runtime.get("propertyName") == GUARD, "wrong runtime property")
    require(runtime.get("sampleLimit") == 16, "runtime sample limit must be 16")

    summary = data.get("isolationSummary")
    require(isinstance(summary, dict), "isolationSummary missing")
    require(summary.get("selectedPixelCount") == 16, "selected pixel count must be 16")
    require(summary.get("for400EvidencePolicy") == "context-only-not-direct-write-proof", "FOR-400 policy changed")
    require(summary.get("for400UsedAsDirectProof") is False, "FOR-400 used as direct proof")

    selected = data.get("selectedPixels")
    require(isinstance(selected, list) and len(selected) == 16, "selectedPixels must contain 16 pixels")
    seen: list[tuple[int, int]] = []
    shader_observed = 0
    missing_framebuffer_state = 0
    for pixel in selected:
        require(isinstance(pixel, dict), "selected pixel must be object")
        key = (pixel.get("x"), pixel.get("y"))
        require(key in EXPECTED_POINTS, f"unexpected coordinate: {key}")
        seen.append(key)  # type: ignore[arg-type]
        subdraws = pixel.get("perSubdraw")
        require(isinstance(subdraws, list), f"perSubdraw missing at {key}")
        for subdraw in subdraws:
            require(isinstance(subdraw, dict), f"subdraw must be object at {key}")
            require(subdraw.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "wrong pipeline family")
            require(subdraw.get("subdrawRole") in {"inside", "outside"}, "wrong subdraw role")
            require(subdraw.get("blendMode") == "kSrcOver", "wrong blend mode")
            require_rgba_float_or_null(subdraw.get("dstBeforeRgbaFloat"), "dstBeforeRgbaFloat")
            require_rgba_float_or_null(subdraw.get("sourceColorPremulRgbaFloat"), "sourceColorPremulRgbaFloat")
            require_rgba_float_or_null(subdraw.get("expectedSourceOverRgbaFloat"), "expectedSourceOverRgbaFloat")
            require_rgba_float_or_null(subdraw.get("dstAfterRgbaFloat"), "dstAfterRgbaFloat")
            if subdraw.get("shaderObserved") is True:
                shader_observed += 1
                missing = subdraw.get("missingFields")
                require(isinstance(missing, list), "missingFields must be list")
                if "dstBeforeRgbaFloat" in missing or "dstAfterRgbaFloat" in missing:
                    missing_framebuffer_state += 1

    require(set(seen) == set(EXPECTED_POINTS), "FOR-401 coordinate set changed")
    if classification == "per-subdraw-framebuffer-state-unavailable":
        require(shader_observed > 0, "framebuffer-state classification requires shader-observed samples")
        require(missing_framebuffer_state > 0, "framebuffer-state classification requires missing framebuffer fields")

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
    ):
        require(non_goals.get(key) is False, f"non-goal changed: {key}")

    validate_report(str(classification))
    print(f"FOR-408 validation passed: {classification}")


if __name__ == "__main__":
    main()
