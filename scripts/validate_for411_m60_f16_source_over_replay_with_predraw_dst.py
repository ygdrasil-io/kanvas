#!/usr/bin/env python3
"""Validate FOR-411 M60 F16 source-over replay with predraw dst evidence."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-411"
SCENE_ID = "m60-f16-source-over-replay-with-predraw-dst-for411"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
SOURCE_DRAFT_MEMORY = (
    "global/kanvas/tickets/drafts/"
    "brouillon-ticket-for-411-m60-f16-rejouer-source-over-avec-dst-before-predraw-capture"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-410-capture-letat-destination-predraw-m60-f16-avant-aa-stencil-cover"
)

ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-411-m60-f16-source-over-replay-with-predraw-dst.md"
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
FOR410_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-predraw-dst-readback-for410"
    / "m60-f16-aa-stencil-cover-predraw-dst-readback-for410.json"
)

FOR408_GUARD = "kanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled"
FOR409_GUARD = "kanvas.webgpu.m60F16AaStencilCoverSourceOverReplay.enabled"
FOR410_GUARD = "kanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled"
FOR405_GUARD = "kanvas.webgpu.m60F16DirectPassWriteHook.enabled"

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
    "source-over-replay-still-insufficient-inputs",
    "source-over-replay-not-applicable",
}
EXPECTED_VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for411_m60_f16_source_over_replay_with_predraw_dst.py",
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
MATCH_TOLERANCE = 1e-6


def fail(message: str) -> None:
    raise SystemExit(f"FOR-411 validation failed: {message}")


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


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


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


def require_rgba_float(value: Any, field: str) -> list[float]:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA float")
    out: list[float] = []
    for channel in value:
        require(isinstance(channel, (float, int)), f"{field} channel must be numeric")
        out.append(float(channel))
    return out


def require_rgba8(value: Any, field: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA8")
    out: list[int] = []
    for channel in value:
        require(isinstance(channel, int) and 0 <= channel <= 255, f"{field} channel must be byte")
        out.append(channel)
    return out


def rgba8_from_float(rgba: list[float]) -> list[int]:
    return [max(0, min(255, round(channel * 255))) for channel in rgba]


def delta_rgba(a: list[float], b: list[float]) -> dict[str, Any]:
    signed = [a[index] - b[index] for index in range(4)]
    absolute = [abs(value) for value in signed]
    return {
        "signedRgbaFloat": signed,
        "absoluteRgbaFloat": absolute,
        "absoluteTotalFloat": sum(absolute),
        "maxChannelFloat": max(absolute),
        "withinTolerance": max(absolute) <= MATCH_TOLERANCE,
        "tolerance": MATCH_TOLERANCE,
    }


def replay_source_over(dst: list[float], source: list[float], coverage: float) -> tuple[list[float], list[float]]:
    effective_source = [channel * coverage for channel in source]
    alpha = effective_source[3]
    out = [effective_source[index] + dst[index] * (1.0 - alpha) for index in range(4)]
    return out, effective_source


def replayable_subdraw(source: dict[str, Any]) -> bool:
    return (
        source.get("shaderObserved") is True
        and source.get("sourceColorPremulRgbaFloat") is not None
        and source.get("coverageOrAaAlpha") is not None
        and source.get("blendMode") == "kSrcOver"
    )


def source_over_subdraw(source: dict[str, Any], used: bool, replay_before: list[float] | None = None) -> dict[str, Any]:
    source_rgba = source.get("sourceColorPremulRgbaFloat")
    coverage = source.get("coverageOrAaAlpha")
    item = {
        "drawIndex": source.get("drawIndex"),
        "subdrawOrdinal": source.get("subdrawOrdinal"),
        "subdrawRole": source.get("subdrawRole"),
        "pipelineFamily": source.get("pipelineFamily"),
        "blendMode": source.get("blendMode"),
        "shaderObserved": source.get("shaderObserved") is True,
        "sourceColorPremulRgbaFloat": source_rgba,
        "coverageOrAaAlpha": coverage,
        "usedForReplay": used,
    }
    if used:
        src = require_rgba_float(source_rgba, "sourceColorPremulRgbaFloat")
        require(isinstance(coverage, (float, int)), "coverageOrAaAlpha must be numeric")
        require(replay_before is not None, "used subdraw requires replay_before")
        replay_after, effective_source = replay_source_over(replay_before, src, float(coverage))
        item.update(
            {
                "effectiveSourcePremulRgbaFloat": effective_source,
                "replayBeforeRgbaFloat": replay_before,
                "replayAfterRgbaFloat": replay_after,
                "formula": "out = (sourceColorPremulRgbaFloat * coverageOrAaAlpha) + dst * (1 - effectiveSource.a)",
            },
        )
    else:
        reason = "subdraw-not-observed-by-for408-hook"
        if source.get("shaderObserved") is True and source_rgba is None:
            reason = "source-color-premul-missing"
        elif source.get("shaderObserved") is True and coverage is None:
            reason = "coverage-or-aa-alpha-missing"
        elif source.get("blendMode") != "kSrcOver":
            reason = "non-source-over-blend-mode"
        item["excludedReason"] = reason
    return item


def validate_source_inputs(
    for401: dict[str, Any],
    for405: dict[str, Any],
    for408: dict[str, Any],
    for409: dict[str, Any],
    for410: dict[str, Any],
) -> None:
    expected = (
        (for401, "FOR-401", "residual-visible-only-at-final-readback"),
        (for405, "FOR-405", "aa-stencil-cover-post-pass-color-observed"),
        (for408, "FOR-408", "per-subdraw-framebuffer-state-unavailable"),
        (for409, "FOR-409", "source-over-replay-insufficient-inputs"),
        (for410, "FOR-410", "predraw-dst-captured"),
    )
    for source, linear, classification in expected:
        require(source.get("linear") == linear, f"{linear} source Linear id changed")
        require(source.get("classification") == classification, f"{linear} source classification changed")


def build_artifact() -> dict[str, Any]:
    for401 = load_json(FOR401_ARTIFACT)
    for405 = load_json(FOR405_ARTIFACT)
    for408 = load_json(FOR408_ARTIFACT)
    for409 = load_json(FOR409_ARTIFACT)
    for410 = load_json(FOR410_ARTIFACT)
    validate_source_inputs(for401, for405, for408, for409, for410)

    selected408 = for408.get("selectedPixels")
    selected405 = for405.get("selectedPixels")
    selected409 = for409.get("selectedPixels")
    selected410 = for410.get("selectedPixels")
    require(isinstance(selected408, list) and len(selected408) == 16, "FOR-408 selected count changed")
    require(isinstance(selected405, list) and len(selected405) == 16, "FOR-405 selected count changed")
    require(isinstance(selected409, list) and len(selected409) == 16, "FOR-409 selected count changed")
    require(isinstance(selected410, list) and len(selected410) == 16, "FOR-410 selected count changed")

    for405_by_coord = by_coordinate(selected405, "FOR-405")
    for409_by_coord = by_coordinate(selected409, "FOR-409")
    for410_by_coord = by_coordinate(selected410, "FOR-410")

    pixels: list[dict[str, Any]] = []
    for source_pixel in selected408:
        require(isinstance(source_pixel, dict), "FOR-408 selected pixel must be an object")
        x, y = pixel_key(source_pixel)
        require((x, y) in EXPECTED_POINTS, f"unexpected coordinate: {(x, y)}")
        subdraws = source_pixel.get("perSubdraw")
        require(isinstance(subdraws, list), f"perSubdraw missing at {(x, y)}")
        ordered = sorted(subdraws, key=lambda item: (item["drawIndex"], item["subdrawOrdinal"]))

        predraw_pixel = for410_by_coord[(x, y)]
        dst_before = predraw_pixel.get("dstBeforeRgbaFloat")
        replay_inputs_sufficient = dst_before is not None
        used_subdraws: list[dict[str, Any]] = []
        excluded_subdraws: list[dict[str, Any]] = []
        replay = require_rgba_float(dst_before, f"FOR-410 dstBeforeRgbaFloat at {(x, y)}")

        for subdraw in ordered:
            if replayable_subdraw(subdraw):
                used = source_over_subdraw(subdraw, True, replay)
                replay = require_rgba_float(used["replayAfterRgbaFloat"], "replayAfterRgbaFloat")
                used_subdraws.append(used)
            else:
                excluded_subdraws.append(source_over_subdraw(subdraw, False))

        post_pass = for405_by_coord[(x, y)].get("postPass")
        require(isinstance(post_pass, dict), f"FOR-405 postPass missing at {(x, y)}")
        post_rgba = require_rgba_float(post_pass.get("observedRgbaFloat"), f"FOR-405 postPass at {(x, y)}")
        post_rgba8 = require_rgba8(post_pass.get("observedRgba8"), f"FOR-405 postPass RGBA8 at {(x, y)}")
        delta = delta_rgba(replay, post_rgba)

        if not replay_inputs_sufficient:
            classification = "source-over-replay-still-insufficient-inputs"
            reason = "FOR-410 did not provide a destination state for this pixel."
        elif not used_subdraws:
            classification = "source-over-replay-not-applicable"
            reason = "No FOR-408 source-over subdraw was replayable for this pixel."
        elif delta["withinTolerance"]:
            classification = "source-over-replay-matches-post-pass"
            reason = "FOR-411 source-over replay with FOR-410 dstBefore reproduces the FOR-405 post-pass color."
        else:
            classification = "source-over-replay-differs-post-pass"
            reason = (
                "FOR-411 source-over replay with FOR-410 dstBefore does not reproduce the FOR-405 "
                "post-pass color; the observed FOR-408 source/coverage inputs leave a measurable delta."
            )

        pixels.append(
            {
                "x": x,
                "y": y,
                "classification": classification,
                "dstBeforeRgbaFloat": dst_before,
                "dstBeforeRgba8": predraw_pixel.get("dstBeforeRgba8"),
                "dstBeforeSource": predraw_pixel.get("dstBeforeSource"),
                "firstCapturedDrawIndex": predraw_pixel.get("firstCapturedDrawIndex"),
                "drawIndexInspected": predraw_pixel.get("drawIndexInspected"),
                "replayOrder": "drawIndex-then-subdrawOrdinal",
                "replayInputsSufficient": replay_inputs_sufficient,
                "observedSubdrawCount": sum(1 for item in ordered if replayable_subdraw(item)),
                "usedSubdrawCount": len(used_subdraws),
                "excludedSubdrawCount": len(excluded_subdraws),
                "subdrawsUsed": used_subdraws,
                "subdrawsExcluded": excluded_subdraws,
                "replayedRgbaFloat": replay,
                "replayedRgba8": rgba8_from_float(replay),
                "postPassObservedRgbaFloat": post_rgba,
                "postPassObservedRgba8": post_rgba8,
                "replayVsPostPassDelta": delta,
                "for409PreviousClassification": for409_by_coord[(x, y)].get("classification"),
                "for410PredrawStatus": predraw_pixel.get("predrawStatus"),
                "classificationReason": reason,
            },
        )

    matches = sum(1 for pixel in pixels if pixel["classification"] == "source-over-replay-matches-post-pass")
    differs = sum(1 for pixel in pixels if pixel["classification"] == "source-over-replay-differs-post-pass")
    insufficient = sum(1 for pixel in pixels if pixel["classification"] == "source-over-replay-still-insufficient-inputs")
    not_applicable = sum(1 for pixel in pixels if pixel["classification"] == "source-over-replay-not-applicable")
    if differs:
        global_classification = "source-over-replay-differs-post-pass"
    elif insufficient:
        global_classification = "source-over-replay-still-insufficient-inputs"
    elif not_applicable:
        global_classification = "source-over-replay-not-applicable"
    else:
        global_classification = "source-over-replay-matches-post-pass"

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
            "for408": rel(FOR408_ARTIFACT),
            "for409": rel(FOR409_ARTIFACT),
            "for410": rel(FOR410_ARTIFACT),
        },
        "adapter": for410.get("adapter"),
        "producer": "scripts/validate_for411_m60_f16_source_over_replay_with_predraw_dst.py",
        "runtimeOwner": "diagnostic-offline-replay-from-FOR-408-FOR-410-artifacts",
        "decision": "M60_F16_SOURCE_OVER_REPLAY_WITH_PREDRAW_DST_RECORDED",
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
            "sourceOverReplay": {
                "guardId": FOR409_GUARD,
                "enabledForEvidenceRun": True,
                "enabledByDefault": False,
            },
            "predrawDstReadback": {
                "guardId": FOR410_GUARD,
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
            "math": "premultiplied float SrcOver with coverage: out = (srcPremul * coverage) + dst * (1 - (srcPremul.a * coverage))",
            "subdrawOrder": "drawIndex ascending, then subdrawOrdinal ascending",
            "initialStateSource": rel(FOR410_ARTIFACT),
            "initialStateAvailable": True,
            "comparisonTarget": rel(FOR405_ARTIFACT),
            "matchTolerance": MATCH_TOLERANCE,
            "noSyntheticInitialState": True,
        },
        "sourceContext": {
            "for408Classification": for408.get("classification"),
            "for409PreviousClassification": for409.get("classification"),
            "for410Classification": for410.get("classification"),
            "for410CapturedPixelCount": for410.get("predrawSummary", {}).get("capturedPixelCount"),
            "for405Classification": for405.get("classification"),
            "for400EvidencePolicy": "context-only-not-direct-write-proof",
            "for400UsedAsDirectProof": False,
        },
        "replaySummary": {
            "selectedPixelCount": len(pixels),
            "sourceOverReplayMatchesPostPassCount": matches,
            "sourceOverReplayDiffersPostPassCount": differs,
            "sourceOverReplayStillInsufficientInputsCount": insufficient,
            "sourceOverReplayNotApplicableCount": not_applicable,
            "observedReplayInputSubdrawCount": sum(pixel["observedSubdrawCount"] for pixel in pixels),
            "usedSubdrawCount": sum(pixel["usedSubdrawCount"] for pixel in pixels),
            "excludedSubdrawCount": sum(pixel["excludedSubdrawCount"] for pixel in pixels),
            "postPassObservedPixelCount": sum(1 for pixel in pixels if pixel["postPassObservedRgbaFloat"] is not None),
            "predrawDstConsumedPixelCount": sum(1 for pixel in pixels if pixel["dstBeforeRgbaFloat"] is not None),
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
            "FOR-411 consumes the FOR-410 predraw destination state and replays the FOR-408 observed "
            "source-over subdraws. The replay remains white for the 16 selected pixels while the "
            "FOR-405 post-pass samples are colored, so the diagnostic classifies the full replay as "
            "source-over-replay-differs-post-pass rather than insufficient inputs."
        ),
        "validationCommands": EXPECTED_VALIDATION_COMMANDS,
    }


def build_report(data: dict[str, Any]) -> str:
    summary = data["replaySummary"]
    rows = []
    for pixel in data["selectedPixels"]:
        delta = pixel["replayVsPostPassDelta"]
        rows.append(
            "| ({x}, {y}) | `{dst}` | `{used}` | `{replayed}` | `{post}` | `{max_delta}` | `{classification}` |".format(
                x=pixel["x"],
                y=pixel["y"],
                dst=pixel["dstBeforeRgbaFloat"],
                used=pixel["usedSubdrawCount"],
                replayed=pixel["replayedRgbaFloat"],
                post=pixel["postPassObservedRgbaFloat"],
                max_delta=delta["maxChannelFloat"],
                classification=pixel["classification"],
            ),
        )
    commands = "\n".join(f"- `{command}`" for command in data["validationCommands"])
    return f"""# FOR-411 M60 F16 source-over replay with predraw dst

Date: 2026-06-05

## Result

Classification: `{data["globalClassification"]}`.

FOR-411 consumes the `dstBeforeRgbaFloat` captured by FOR-410 and replays the
FOR-408 source-over subdraws in `drawIndex` then `subdrawOrdinal` order. The
replay is no longer blocked by the initial destination state, but it does not
reproduce the FOR-405 post-pass colors for the 16 FOR-401 pixels.

This is diagnostic evidence only. Rendering, scoring, thresholds, and scene
promotion are unchanged.

## Evidence

Artifact:
`{rel(ARTIFACT)}`

| Source | Path |
|---|---|
| FOR-401 selected residual coordinates | `{data["sourceArtifacts"]["for401"]}` |
| FOR-405 post-pass readback | `{data["sourceArtifacts"]["for405"]}` |
| FOR-408 per-subdraw hook | `{data["sourceArtifacts"]["for408"]}` |
| FOR-409 previous source-over replay | `{data["sourceArtifacts"]["for409"]}` |
| FOR-410 predraw dst readback | `{data["sourceArtifacts"]["for410"]}` |
| FOR-410 memory finding | `{data["sourceFinding"]}` |
| FOR-411 draft memory | `{data["sourceDraftMemory"]}` |

## Summary

| Metric | Value |
|---|---:|
| Selected FOR-401 pixels | `{summary["selectedPixelCount"]}` |
| Predraw dst consumed pixels | `{summary["predrawDstConsumedPixelCount"]}` |
| Observed replay-input subdraws | `{summary["observedReplayInputSubdrawCount"]}` |
| Used subdraws | `{summary["usedSubdrawCount"]}` |
| Excluded subdraws | `{summary["excludedSubdrawCount"]}` |
| Post-pass observed pixels | `{summary["postPassObservedPixelCount"]}` |
| Matches post-pass | `{summary["sourceOverReplayMatchesPostPassCount"]}` |
| Differs from post-pass | `{summary["sourceOverReplayDiffersPostPassCount"]}` |
| Still insufficient inputs | `{summary["sourceOverReplayStillInsufficientInputsCount"]}` |
| Not applicable | `{summary["sourceOverReplayNotApplicableCount"]}` |

## Pixel Replay

| Coordinate | dstBefore | Used subdraws | Replayed RGBA float | Post-pass RGBA float | Max delta | Classification |
|---|---|---:|---|---|---:|---|
{chr(10).join(rows)}

## Interpretation

The replay starts from the real FOR-410 destination state. The replayed
FOR-408 source/coverage inputs still leave the selected pixels at white, while
the FOR-405 post-pass samples are colored. The next diagnostic step should
inspect why the observed AA stencil-cover source/coverage inputs do not explain
the post-pass write.

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


def validate_report(classification: str) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "FOR-411 M60 F16 source-over replay with predraw dst",
        f"Classification: `{classification}`",
        "source-over-replay-differs-post-pass",
        "FOR-410 predraw dst readback",
        "supportClaim=false",
        "promoted=false",
        "correctionAppliedByDefault=false",
        "defaultRenderingChanged=false",
        "thresholdChanged=false",
        "scoringChanged=false",
        "rtk python3 scripts/validate_for411_m60_f16_source_over_replay_with_predraw_dst.py",
    ):
        require(needle in text, f"report missing: {needle}")


def validate_source_artifact_links(sources: dict[str, Any]) -> None:
    expected = {
        "for401": (FOR401_ARTIFACT, "FOR-401", "residual-visible-only-at-final-readback"),
        "for405": (FOR405_ARTIFACT, "FOR-405", "aa-stencil-cover-post-pass-color-observed"),
        "for408": (FOR408_ARTIFACT, "FOR-408", "per-subdraw-framebuffer-state-unavailable"),
        "for409": (FOR409_ARTIFACT, "FOR-409", "source-over-replay-insufficient-inputs"),
        "for410": (FOR410_ARTIFACT, "FOR-410", "predraw-dst-captured"),
    }
    for key, (path, linear, classification) in expected.items():
        require(sources.get(key) == rel(path), f"{linear} artifact link changed")
        source = load_json(path)
        require(source.get("linear") == linear, f"{linear} source Linear id changed")
        require(source.get("classification") == classification, f"{linear} source classification changed")


def validate_artifact(data: dict[str, Any]) -> str:
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("sourceSceneId") == ROW_ID, "wrong source scene")
    require(data.get("sourceDraftMemory") == SOURCE_DRAFT_MEMORY, "wrong draft memory")
    require(data.get("sourceFinding") == SOURCE_FINDING, "wrong FOR-410 finding memory")

    sources = data.get("sourceArtifacts")
    require(isinstance(sources, dict), "sourceArtifacts missing")
    validate_source_artifact_links(sources)

    classification = data.get("globalClassification")
    require(classification == data.get("classification"), "classification/globalClassification mismatch")
    require(classification in ALLOWED_CLASSIFICATIONS, "unexpected global classification")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "classification taxonomy changed")
    require(classification == "source-over-replay-differs-post-pass", "global classification changed")

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
    require(guards.get("sourceOverReplay", {}).get("guardId") == FOR409_GUARD, "FOR-409 guard missing")
    require(guards.get("predrawDstReadback", {}).get("guardId") == FOR410_GUARD, "FOR-410 guard missing")
    require(guards.get("contributionIsolation", {}).get("guardId") == FOR408_GUARD, "FOR-408 guard missing")
    require(guards.get("postPassReadback", {}).get("guardId") == FOR405_GUARD, "FOR-405 guard missing")
    for name, guard in guards.items():
        require(guard.get("enabledByDefault") is False, f"{name} guard must be disabled by default")

    scope = data.get("scope")
    require(isinstance(scope, dict), "scope missing")
    require(scope.get("selectedPixelCount") == 16, "selected pixel count must be 16")
    require(scope.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "wrong pipeline family")
    require(scope.get("blendMode") == "kSrcOver", "wrong blend mode")
    require(scope.get("generalizedOutsideM60F16") is False, "scope generalized outside M60 F16")

    policy = data.get("replayPolicy")
    require(isinstance(policy, dict), "replayPolicy missing")
    require("SrcOver" in str(policy.get("math")), "SrcOver replay math missing")
    require(policy.get("initialStateAvailable") is True, "FOR-410 initial state should be available")
    require(policy.get("noSyntheticInitialState") is True, "synthetic initial state must be forbidden")
    require(policy.get("matchTolerance") == MATCH_TOLERANCE, "match tolerance changed")

    summary = data.get("replaySummary")
    require(isinstance(summary, dict), "replaySummary missing")
    require(summary.get("selectedPixelCount") == 16, "summary selected count changed")
    require(summary.get("predrawDstConsumedPixelCount") == 16, "predraw dst consumed count changed")
    require(summary.get("sourceOverReplayMatchesPostPassCount") == 0, "matches count changed")
    require(summary.get("sourceOverReplayDiffersPostPassCount") == 16, "differs count changed")
    require(summary.get("sourceOverReplayStillInsufficientInputsCount") == 0, "still-insufficient count changed")
    require(summary.get("sourceOverReplayNotApplicableCount") == 0, "not-applicable count changed")
    require(summary.get("observedReplayInputSubdrawCount") == 44, "observed subdraw count changed")
    require(summary.get("usedSubdrawCount") == 44, "used subdraw count changed")
    require(summary.get("excludedSubdrawCount") == 52, "excluded subdraw count changed")
    require(summary.get("postPassObservedPixelCount") == 16, "post-pass observed count changed")

    selected = data.get("selectedPixels")
    require(isinstance(selected, list) and len(selected) == 16, "selectedPixels must contain 16 pixels")
    seen: list[tuple[int, int]] = []
    for pixel in selected:
        require(isinstance(pixel, dict), "selected pixel must be an object")
        key = pixel_key(pixel)
        require(key in EXPECTED_POINTS, f"unexpected coordinate: {key}")
        seen.append(key)
        require(pixel.get("classification") == "source-over-replay-differs-post-pass", f"pixel classification changed at {key}")
        require(pixel.get("replayInputsSufficient") is True, f"replay inputs should be sufficient at {key}")
        require(pixel.get("usedSubdrawCount", 0) > 0, f"used subdraw count missing at {key}")
        require(pixel.get("excludedSubdrawCount", 0) >= 0, f"excluded subdraw count missing at {key}")
        require_rgba_float(pixel.get("dstBeforeRgbaFloat"), f"dstBeforeRgbaFloat at {key}")
        require_rgba8(pixel.get("dstBeforeRgba8"), f"dstBeforeRgba8 at {key}")
        require_rgba_float(pixel.get("replayedRgbaFloat"), f"replayedRgbaFloat at {key}")
        require_rgba8(pixel.get("replayedRgba8"), f"replayedRgba8 at {key}")
        require_rgba_float(pixel.get("postPassObservedRgbaFloat"), f"postPassObservedRgbaFloat at {key}")
        require_rgba8(pixel.get("postPassObservedRgba8"), f"postPassObservedRgba8 at {key}")
        delta = pixel.get("replayVsPostPassDelta")
        require(isinstance(delta, dict), f"delta missing at {key}")
        require(delta.get("withinTolerance") is False, f"differs classification requires out-of-tolerance delta at {key}")
        require(delta.get("maxChannelFloat", 0.0) > MATCH_TOLERANCE, f"delta too small at {key}")
        used = pixel.get("subdrawsUsed")
        excluded = pixel.get("subdrawsExcluded")
        require(isinstance(used, list), f"subdrawsUsed missing at {key}")
        require(isinstance(excluded, list), f"subdrawsExcluded missing at {key}")
        require(len(used) == pixel.get("usedSubdrawCount"), f"used subdraw count mismatch at {key}")
        require(len(excluded) == pixel.get("excludedSubdrawCount"), f"excluded subdraw count mismatch at {key}")
        for subdraw in used:
            require(subdraw.get("usedForReplay") is True, f"used subdraw not marked used at {key}")
            require(subdraw.get("blendMode") == "kSrcOver", f"used subdraw blend changed at {key}")
            require_rgba_float(subdraw.get("effectiveSourcePremulRgbaFloat"), f"effective source at {key}")
            require_rgba_float(subdraw.get("replayBeforeRgbaFloat"), f"replay before at {key}")
            require_rgba_float(subdraw.get("replayAfterRgbaFloat"), f"replay after at {key}")
        for subdraw in excluded:
            require(subdraw.get("usedForReplay") is False, f"excluded subdraw marked used at {key}")
            require(isinstance(subdraw.get("excludedReason"), str), f"excluded reason missing at {key}")

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
    ):
        require(non_goals.get(key) is False, f"non-goal changed: {key}")

    require(data.get("validationCommands") == EXPECTED_VALIDATION_COMMANDS, "validation command list changed")
    return str(classification)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="rewrite the FOR-411 artifact and report")
    args = parser.parse_args()

    if args.write:
        expected = build_artifact()
        write_if_changed(ARTIFACT, json.dumps(expected, indent=2, sort_keys=False) + "\n")
        write_if_changed(REPORT, build_report(expected))

    data = load_json(ARTIFACT)
    classification = validate_artifact(data)
    validate_report(classification)
    print(f"FOR-411 validation passed: {classification}")


if __name__ == "__main__":
    main()
