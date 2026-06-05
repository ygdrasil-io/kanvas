#!/usr/bin/env python3
"""Validate FOR-409 M60 F16 AA stencil-cover source-over replay evidence."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-409"
SCENE_ID = "m60-f16-aa-stencil-cover-source-over-replay-for409"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
GUARD = "kanvas.webgpu.m60F16AaStencilCoverSourceOverReplay.enabled"
FOR408_GUARD = "kanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled"
FOR405_GUARD = "kanvas.webgpu.m60F16DirectPassWriteHook.enabled"
SOURCE_DRAFT_MEMORY = (
    "global/kanvas/tickets/drafts/"
    "brouillon-ticket-for-409-m60-f16-replay-diagnostique-source-over-hors-fixed-function-blend"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-408-ajoute-le-hook-per-subdraw-aa-stencil-cover-mais-confirme-le-blocage-framebuffer-m60-f16"
)

ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-409-m60-f16-aa-stencil-cover-source-over-replay.md"
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
FOR406_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-post-pass-reference-comparison-for406"
    / "m60-f16-post-pass-reference-comparison-for406.json"
)
FOR408_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-per-subdraw-hook-for408"
    / "m60-f16-aa-stencil-cover-per-subdraw-hook-for408.json"
)
CAPTURE_TEST = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
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
    "source-over-replay-matches-post-pass",
    "source-over-replay-differs-post-pass",
    "source-over-replay-insufficient-inputs",
    "source-over-replay-no-observed-subdraw",
}
EXPECTED_VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for409_m60_f16_aa_stencil_cover_source_over_replay.py",
    "rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py",
    "rtk python3 scripts/validate_for407_m60_f16_aa_stencil_cover_contribution_isolation.py",
    "rtk python3 scripts/validate_for406_m60_f16_post_pass_reference_comparison.py",
    "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true "
        "-Dkanvas.webgpu.m60F16AaStencilCoverSourceOverReplay.enabled=true "
        "-Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true "
        "-Dkanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled=true "
        ":gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-409 validation failed: {message}")


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


def pixel_key(pixel: dict[str, Any]) -> tuple[int, int]:
    x = pixel.get("x")
    y = pixel.get("y")
    require(isinstance(x, int) and isinstance(y, int), "pixel coordinate missing")
    return x, y


def by_coordinate(pixels: list[Any], source_name: str) -> dict[tuple[int, int], dict[str, Any]]:
    out: dict[tuple[int, int], dict[str, Any]] = {}
    for pixel in pixels:
        require(isinstance(pixel, dict), f"{source_name} pixel must be an object")
        key = pixel_key(pixel)
        require(key not in out, f"duplicate coordinate in {source_name}: {key}")
        out[key] = pixel
    return out


def require_rgba_float_or_null(value: Any, field: str) -> None:
    if value is None:
        return
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA float or null")
    for channel in value:
        require(isinstance(channel, (float, int)), f"{field} channel must be numeric")


def require_rgba8_or_null(value: Any, field: str) -> None:
    if value is None:
        return
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA8 or null")
    for channel in value:
        require(isinstance(channel, int) and 0 <= channel <= 255, f"{field} channel must be byte")


def validate_sources() -> None:
    text = CAPTURE_TEST.read_text(encoding="utf-8")
    for needle in (
        "FOR409_SOURCE_OVER_REPLAY_PROPERTY",
        GUARD,
        "writeM60F16AaStencilCoverSourceOverReplay",
        "m60F16AaStencilCoverSourceOverReplayJson",
        "initial-state-before-first-observed-subdraw-unavailable",
        "drawIndex-then-subdrawOrdinal",
    ):
        require(needle in text, f"capture test missing source evidence: {needle}")


def source_over_subdraw(source: dict[str, Any]) -> dict[str, Any]:
    observed = source.get("shaderObserved") is True
    source_rgba = source.get("sourceColorPremulRgbaFloat")
    coverage = source.get("coverageOrAaAlpha")
    blend_mode = source.get("blendMode")
    if not observed:
        reason = "subdraw-not-observed-by-for408-hook"
    elif source_rgba is None:
        reason = "source-color-premul-missing"
    elif coverage is None:
        reason = "coverage-or-aa-alpha-missing"
    elif blend_mode != "kSrcOver":
        reason = "non-source-over-blend-mode"
    else:
        reason = "initial-state-before-first-observed-subdraw-unavailable"
    return {
        "drawIndex": source["drawIndex"],
        "subdrawOrdinal": source["subdrawOrdinal"],
        "subdrawRole": source["subdrawRole"],
        "pipelineFamily": source["pipelineFamily"],
        "blendMode": blend_mode,
        "shaderObserved": observed,
        "sourceColorPremulRgbaFloat": source_rgba,
        "coverageOrAaAlpha": coverage,
        "usedForReplay": False,
        "excludedReason": reason,
    }


def build_artifact() -> dict[str, Any]:
    for401 = load_json(FOR401_ARTIFACT)
    for405 = load_json(FOR405_ARTIFACT)
    for406 = load_json(FOR406_ARTIFACT)
    for408 = load_json(FOR408_ARTIFACT)
    validate_sources()

    require(for401.get("linear") == "FOR-401", "FOR-401 source changed")
    require(for405.get("linear") == "FOR-405", "FOR-405 source changed")
    require(for406.get("linear") == "FOR-406", "FOR-406 source changed")
    require(for408.get("linear") == "FOR-408", "FOR-408 source changed")
    require(for408.get("classification") == "per-subdraw-framebuffer-state-unavailable", "FOR-408 classification changed")
    require(for405.get("classification") == "aa-stencil-cover-post-pass-color-observed", "FOR-405 classification changed")
    require(for406.get("classification") == "post-pass-already-diverged", "FOR-406 classification changed")

    selected408 = for408.get("selectedPixels")
    selected405 = for405.get("selectedPixels")
    selected406 = for406.get("selectedPixels")
    require(isinstance(selected408, list) and len(selected408) == 16, "FOR-408 selected pixel count changed")
    require(isinstance(selected405, list) and len(selected405) == 16, "FOR-405 selected pixel count changed")
    require(isinstance(selected406, list) and len(selected406) == 16, "FOR-406 selected pixel count changed")
    for405_by_coord = by_coordinate(selected405, "FOR-405")
    for406_by_coord = by_coordinate(selected406, "FOR-406")

    pixels: list[dict[str, Any]] = []
    for source_pixel in selected408:
        require(isinstance(source_pixel, dict), "FOR-408 selected pixel must be an object")
        x, y = pixel_key(source_pixel)
        require((x, y) in EXPECTED_POINTS, f"unexpected coordinate: {(x, y)}")
        subdraws = source_pixel.get("perSubdraw")
        require(isinstance(subdraws, list), f"perSubdraw missing at {(x, y)}")
        ordered = sorted(subdraws, key=lambda item: (item["drawIndex"], item["subdrawOrdinal"]))
        observed = [
            subdraw
            for subdraw in ordered
            if subdraw.get("shaderObserved") is True
            and subdraw.get("sourceColorPremulRgbaFloat") is not None
            and subdraw.get("coverageOrAaAlpha") is not None
            and subdraw.get("blendMode") == "kSrcOver"
        ]
        post405 = for405_by_coord[(x, y)].get("postPass")
        require(isinstance(post405, dict), f"FOR-405 postPass missing at {(x, y)}")
        comparison406 = for406_by_coord[(x, y)]
        classification = (
            "source-over-replay-insufficient-inputs"
            if observed
            else "source-over-replay-no-observed-subdraw"
        )
        pixels.append(
            {
                "x": x,
                "y": y,
                "classification": classification,
                "initialState": {
                    "available": False,
                    "rgbaFloat": None,
                    "source": None,
                    "missingReason": (
                        "source-over-replay-initial-state-unavailable"
                        if observed
                        else "source-over-replay-no-observed-subdraw"
                    ),
                    "verification": (
                        "FOR-408 does not expose dstBeforeRgbaFloat for the first observed subdraw; "
                        "FOR-405 only exposes post-pass destination after the AA stencil-cover pass."
                    ),
                },
                "observedSubdrawCount": len(observed),
                "usedSubdrawCount": 0,
                "excludedSubdrawCount": len(ordered),
                "replayOrder": "drawIndex-then-subdrawOrdinal",
                "replayInputsSufficient": False,
                "replayedRgbaFloat": None,
                "postPassObservedRgbaFloat": post405.get("observedRgbaFloat"),
                "postPassObservedRgba8": post405.get("observedRgba8"),
                "replayVsPostPassDelta": None,
                "for406PostPassClassification": comparison406.get("classification"),
                "subdrawsUsed": [],
                "subdrawsExcluded": [source_over_subdraw(subdraw) for subdraw in ordered],
                "classificationReason": (
                    "FOR-408 provides observed source color and coverage for this pixel, but FOR-409 "
                    "has no explicit initial destination state before the first observed subdraw."
                    if observed
                    else "FOR-408 did not observe a replayable subdraw for this pixel."
                ),
            },
        )

    global_classification = (
        "source-over-replay-insufficient-inputs"
        if any(pixel["classification"] == "source-over-replay-insufficient-inputs" for pixel in pixels)
        else "source-over-replay-no-observed-subdraw"
    )
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceSceneId": ROW_ID,
        "sourceDraftMemory": SOURCE_DRAFT_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "sourceArtifacts": {
            "for401": rel(FOR401_ARTIFACT),
            "for405": rel(FOR405_ARTIFACT),
            "for406": rel(FOR406_ARTIFACT),
            "for408": rel(FOR408_ARTIFACT),
        },
        "adapter": for408.get("adapter"),
        "producer": rel(CAPTURE_TEST),
        "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
        "decision": "M60_F16_AA_STENCIL_COVER_SOURCE_OVER_REPLAY_RECORDED",
        "classification": global_classification,
        "globalClassification": global_classification,
        "allowedClassifications": sorted(ALLOWED_CLASSIFICATIONS),
        "supportClaim": False,
        "promoted": False,
        "correctionAppliedByDefault": False,
        "defaultRenderingChanged": False,
        "thresholdChanged": False,
        "scoringChanged": False,
        "guards": {
            "experimentalStrokeRenderer": {
                "guardId": "kanvas.webgpu.strokeCapJoin.experimentalRender",
                "enabledForEvidenceRun": True,
                "enabledByDefault": False,
            },
            "sourceOverReplay": {
                "guardId": GUARD,
                "enabledForEvidenceRun": True,
                "enabledByDefault": False,
            },
            "contributionIsolation": {
                "guardId": FOR408_GUARD,
                "enabledForEvidenceRun": True,
                "enabledByDefault": False,
            },
            "postPassReadback": {
                "guardId": FOR405_GUARD,
                "enabledForEvidenceRun": True,
                "enabledByDefault": False,
            },
        },
        "scope": {
            "scene": "M60 F16 bounded stroke cap/join target-colorspace blend",
            "pixelSet": "FOR-401 selected residual coordinates",
            "selectedPixelCount": len(pixels),
            "pipelineFamily": "StencilCoverAaPolygonDraw",
            "blendMode": "kSrcOver",
            "generalizedOutsideM60F16": False,
        },
        "replayPolicy": {
            "math": "premultiplied float SrcOver: out = src + dst * (1 - src.a)",
            "subdrawOrder": "drawIndex ascending, then subdrawOrdinal ascending",
            "initialStateRequirement": (
                "exact destination premultiplied RGBA float before the first observed replay subdraw"
            ),
            "initialStateAvailable": False,
            "insufficientInputClassification": "source-over-replay-insufficient-inputs",
            "noSyntheticInitialState": True,
        },
        "sourceContext": {
            "for408Classification": for408.get("classification"),
            "for408RuntimeApi": "SkWebGpuDevice.m60F16AaStencilCoverContributionIsolationSnapshot()",
            "for408ObservedSubdrawCount": sum(pixel["observedSubdrawCount"] for pixel in pixels),
            "for405RuntimeApi": "SkWebGpuDevice.m60F16AaStencilCoverPostPassReadbackSnapshot()",
            "for405PostPassObservedPixelCount": sum(1 for pixel in pixels if pixel["postPassObservedRgbaFloat"] is not None),
            "for406Classification": for406.get("classification"),
            "for400EvidencePolicy": "context-only-not-direct-write-proof",
            "for400UsedAsDirectProof": False,
        },
        "replaySummary": {
            "selectedPixelCount": len(pixels),
            "sourceOverReplayMatchesPostPassCount": 0,
            "sourceOverReplayDiffersPostPassCount": 0,
            "sourceOverReplayInsufficientInputsCount": sum(
                1 for pixel in pixels if pixel["classification"] == "source-over-replay-insufficient-inputs"
            ),
            "sourceOverReplayNoObservedSubdrawCount": sum(
                1 for pixel in pixels if pixel["classification"] == "source-over-replay-no-observed-subdraw"
            ),
            "observedReplayInputSubdrawCount": sum(pixel["observedSubdrawCount"] for pixel in pixels),
            "usedSubdrawCount": 0,
            "excludedSubdrawCount": sum(pixel["excludedSubdrawCount"] for pixel in pixels),
            "postPassObservedPixelCount": sum(1 for pixel in pixels if pixel["postPassObservedRgbaFloat"] is not None),
            "initialStateMissingPixelCount": sum(1 for pixel in pixels if pixel["observedSubdrawCount"] > 0),
        },
        "selectedPixels": pixels,
        "nonGoalsPreserved": {
            "defaultRenderingChanged": False,
            "supportClaimRaised": False,
            "promoted": False,
            "thresholdChanged": False,
            "scoringChanged": False,
            "correctionApplied": False,
            "for400UsedAsDirectProof": False,
            "generalizedOutsideM60F16": False,
        },
        "classificationReason": (
            "FOR-409 can order FOR-408 observed source/coverage subdraw inputs and compare against "
            "FOR-405 post-pass colors, but the exact destination premultiplied RGBA float before the "
            "first observed subdraw is still unavailable. The diagnostic therefore refuses to synthesize "
            "an initial state and classifies the replay as insufficient inputs."
        ),
        "validationCommands": EXPECTED_VALIDATION_COMMANDS,
    }


def build_report(data: dict[str, Any]) -> str:
    summary = data["replaySummary"]
    rows = []
    for pixel in data["selectedPixels"]:
        rows.append(
            "| ({x}, {y}) | `{observed}` | `{used}` | `{excluded}` | `{initial}` | `{replayed}` | `{post}` | `{delta}` | `{classification}` |".format(
                x=pixel["x"],
                y=pixel["y"],
                observed=pixel["observedSubdrawCount"],
                used=pixel["usedSubdrawCount"],
                excluded=pixel["excludedSubdrawCount"],
                initial=pixel["initialState"]["missingReason"],
                replayed=pixel["replayedRgbaFloat"],
                post=pixel["postPassObservedRgbaFloat"],
                delta=pixel["replayVsPostPassDelta"],
                classification=pixel["classification"],
            ),
        )
    commands = "\n".join(f"- `{command}`" for command in data["validationCommands"])
    return f"""# FOR-409 M60 F16 AA stencil-cover source-over replay

Date: 2026-06-05

## Result

Classification: `{data["globalClassification"]}`.

The diagnostic is opt-in behind `{GUARD}`, disabled by default. It is bounded
to M60 F16, `StencilCoverAaPolygonDraw`, `kSrcOver`, and the 16 FOR-401
coordinates. It reuses FOR-408 per-subdraw source/coverage data and FOR-405
post-pass colors, but it does not synthesize an initial destination color.

The replay cannot classify `matches` or `differs` because the exact
premultiplied destination RGBA float before the first observed subdraw is not
available. The correct conservative result is therefore
`source-over-replay-insufficient-inputs`.

## Evidence

Artifact:
`{rel(ARTIFACT)}`

| Source | Path |
|---|---|
| FOR-401 selected residual coordinates | `{data["sourceArtifacts"]["for401"]}` |
| FOR-405 post-pass readback | `{data["sourceArtifacts"]["for405"]}` |
| FOR-406 post-pass/reference comparison | `{data["sourceArtifacts"]["for406"]}` |
| FOR-408 per-subdraw hook | `{data["sourceArtifacts"]["for408"]}` |
| FOR-408 memory finding | `{data["sourceFinding"]}` |
| FOR-409 draft memory | `{data["sourceDraftMemory"]}` |

## Summary

| Metric | Value |
|---|---:|
| Selected FOR-401 pixels | `{summary["selectedPixelCount"]}` |
| Observed replay-input subdraws | `{summary["observedReplayInputSubdrawCount"]}` |
| Used subdraws | `{summary["usedSubdrawCount"]}` |
| Excluded subdraws | `{summary["excludedSubdrawCount"]}` |
| Post-pass observed pixels | `{summary["postPassObservedPixelCount"]}` |
| Initial-state missing pixels | `{summary["initialStateMissingPixelCount"]}` |
| Insufficient-input pixels | `{summary["sourceOverReplayInsufficientInputsCount"]}` |
| No-observed-subdraw pixels | `{summary["sourceOverReplayNoObservedSubdrawCount"]}` |

## Pixel Replay

| Coordinate | Observed subdraws | Used | Excluded | Initial state | Replayed RGBA float | Post-pass RGBA float | Delta | Classification |
|---|---:|---:|---:|---|---|---|---|---|
{chr(10).join(rows)}

## Non-Goals Preserved

| Contract | Value |
|---|---|
| supportClaim=false | `{str(data["supportClaim"]).lower()}` |
| promoted=false | `{str(data["promoted"]).lower()}` |
| correctionAppliedByDefault=false | `{str(data["correctionAppliedByDefault"]).lower()}` |
| defaultRenderingChanged=false | `{str(data["defaultRenderingChanged"]).lower()}` |
| thresholdChanged=false | `{str(data["thresholdChanged"]).lower()}` |
| scoringChanged=false | `{str(data["scoringChanged"]).lower()}` |

## Validations

{commands}
"""


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


def validate_report(classification: str) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "FOR-409 M60 F16 AA stencil-cover source-over replay",
        f"Classification: `{classification}`",
        GUARD,
        "source-over-replay-insufficient-inputs",
        "supportClaim=false",
        "promoted=false",
        "correctionAppliedByDefault=false",
        "defaultRenderingChanged=false",
        "thresholdChanged=false",
        "scoringChanged=false",
        "rtk python3 scripts/validate_for409_m60_f16_aa_stencil_cover_source_over_replay.py",
    ):
        require(needle in text, f"report missing: {needle}")


def validate_artifact(data: dict[str, Any]) -> str:
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("sourceSceneId") == ROW_ID, "wrong source scene")
    require(data.get("sourceDraftMemory") == SOURCE_DRAFT_MEMORY, "wrong draft memory")
    require(data.get("sourceFinding") == SOURCE_FINDING, "wrong FOR-408 finding memory")
    sources = data.get("sourceArtifacts")
    require(isinstance(sources, dict), "sourceArtifacts missing")
    require(sources.get("for401") == rel(FOR401_ARTIFACT), "FOR-401 artifact link changed")
    require(sources.get("for405") == rel(FOR405_ARTIFACT), "FOR-405 artifact link changed")
    require(sources.get("for406") == rel(FOR406_ARTIFACT), "FOR-406 artifact link changed")
    require(sources.get("for408") == rel(FOR408_ARTIFACT), "FOR-408 artifact link changed")

    classification = data.get("globalClassification")
    require(classification == data.get("classification"), "classification/globalClassification mismatch")
    require(classification in ALLOWED_CLASSIFICATIONS, "unexpected global classification")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "classification taxonomy changed")
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
    replay_guard = guards.get("sourceOverReplay")
    require(isinstance(replay_guard, dict), "sourceOverReplay guard missing")
    require(replay_guard.get("guardId") == GUARD, "wrong source-over replay guard")
    require(replay_guard.get("enabledByDefault") is False, "source-over replay must be disabled by default")
    require(replay_guard.get("enabledForEvidenceRun") is True, "source-over replay must be enabled for evidence")
    require(guards.get("contributionIsolation", {}).get("guardId") == FOR408_GUARD, "FOR-408 guard missing")
    require(guards.get("postPassReadback", {}).get("guardId") == FOR405_GUARD, "FOR-405 guard missing")

    scope = data.get("scope")
    require(isinstance(scope, dict), "scope missing")
    require(scope.get("selectedPixelCount") == 16, "selected pixel count must be 16")
    require(scope.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "wrong pipeline family")
    require(scope.get("blendMode") == "kSrcOver", "wrong blend mode")
    require(scope.get("generalizedOutsideM60F16") is False, "scope generalized outside M60 F16")

    policy = data.get("replayPolicy")
    require(isinstance(policy, dict), "replayPolicy missing")
    require("SrcOver" in str(policy.get("math")), "SrcOver replay math missing")
    require(policy.get("initialStateAvailable") is False, "initial state should not be marked available")
    require(policy.get("noSyntheticInitialState") is True, "synthetic initial state must be forbidden")

    summary = data.get("replaySummary")
    require(isinstance(summary, dict), "replaySummary missing")
    require(summary.get("selectedPixelCount") == 16, "summary selected count changed")
    require(summary.get("sourceOverReplayMatchesPostPassCount") == 0, "matches count must be zero")
    require(summary.get("sourceOverReplayDiffersPostPassCount") == 0, "differs count must be zero")
    require(summary.get("sourceOverReplayInsufficientInputsCount") == 16, "insufficient count changed")
    require(summary.get("sourceOverReplayNoObservedSubdrawCount") == 0, "no-observed count changed")
    require(summary.get("observedReplayInputSubdrawCount") == 44, "observed subdraw count changed")
    require(summary.get("usedSubdrawCount") == 0, "no subdraw can be used without initial state")
    require(summary.get("excludedSubdrawCount") == 96, "excluded subdraw count changed")
    require(summary.get("postPassObservedPixelCount") == 16, "post-pass observed count changed")
    require(summary.get("initialStateMissingPixelCount") == 16, "initial-state missing count changed")

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
        initial = pixel.get("initialState")
        require(isinstance(initial, dict), f"initialState missing at {key}")
        available = initial.get("available")
        rgba = initial.get("rgbaFloat")
        if pixel_class in {"source-over-replay-matches-post-pass", "source-over-replay-differs-post-pass"}:
            require(available is True, f"{pixel_class} requires initial state at {key}")
            require_rgba_float_or_null(rgba, f"initialState.rgbaFloat at {key}")
            require(rgba is not None, f"{pixel_class} requires non-null initial rgba at {key}")
            require(pixel.get("replayedRgbaFloat") is not None, f"{pixel_class} requires replayed color at {key}")
            require(pixel.get("replayVsPostPassDelta") is not None, f"{pixel_class} requires delta at {key}")
        else:
            require(available is False, f"{pixel_class} must not claim initial state at {key}")
            require(rgba is None, f"{pixel_class} must not synthesize initial rgba at {key}")
            require(pixel.get("replayedRgbaFloat") is None, f"{pixel_class} must not synthesize replay color at {key}")
            require(pixel.get("replayVsPostPassDelta") is None, f"{pixel_class} must not synthesize delta at {key}")
        require(pixel.get("replayInputsSufficient") is False, f"replay inputs must be insufficient at {key}")
        require(pixel.get("usedSubdrawCount") == 0, f"used subdraw count must be zero at {key}")
        require_rgba_float_or_null(pixel.get("postPassObservedRgbaFloat"), f"postPassObservedRgbaFloat at {key}")
        require_rgba8_or_null(pixel.get("postPassObservedRgba8"), f"postPassObservedRgba8 at {key}")
        used = pixel.get("subdrawsUsed")
        excluded = pixel.get("subdrawsExcluded")
        require(isinstance(used, list) and used == [], f"subdrawsUsed must be empty at {key}")
        require(isinstance(excluded, list) and len(excluded) == 6, f"subdrawsExcluded changed at {key}")
        order = [(item.get("drawIndex"), item.get("subdrawOrdinal")) for item in excluded]
        require(order == sorted(order), f"subdraw order changed at {key}")
        for subdraw in excluded:
            require(subdraw.get("pipelineFamily") == "StencilCoverAaPolygonDraw", f"wrong pipeline at {key}")
            require(subdraw.get("blendMode") == "kSrcOver", f"wrong blend mode at {key}")
            require(subdraw.get("usedForReplay") is False, f"subdraw incorrectly used at {key}")
            require(isinstance(subdraw.get("excludedReason"), str), f"excludedReason missing at {key}")

    require(set(seen) == set(EXPECTED_POINTS), "FOR-401 coordinate set changed")
    require(classification == "source-over-replay-insufficient-inputs", "global classification changed")

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
    require(data.get("validationCommands") == EXPECTED_VALIDATION_COMMANDS, "validation command list changed")
    return str(classification)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="rewrite the FOR-409 artifact and report")
    args = parser.parse_args()

    if args.write:
        expected = build_artifact()
        write_if_changed(ARTIFACT, json.dumps(expected, indent=2, sort_keys=False) + "\n")
        write_if_changed(REPORT, build_report(expected))

    data = load_json(ARTIFACT)
    classification = validate_artifact(data)
    validate_report(classification)
    print(f"FOR-409 validation passed: {classification}")


if __name__ == "__main__":
    main()
