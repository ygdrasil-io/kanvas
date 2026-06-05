#!/usr/bin/env python3
"""Validate FOR-407 M60 F16 AA stencil-cover contribution isolation evidence."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-407"
SCENE_ID = "m60-f16-aa-stencil-cover-contribution-isolation-for407"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
SOURCE_DRAFT_MEMORY = (
    "global/kanvas/tickets/drafts/"
    "brouillon-ticket-for-407-m60-f16-isoler-couverture-couleur-blend-dans-la-passe-aa-stencil-cover"
)
SOURCE_MEMORY_FOR405 = (
    "global/kanvas/findings/"
    "for-405-observe-les-couleurs-post-passe-aa-stencil-cover-m60-f16"
)
SOURCE_MEMORY_FOR406 = (
    "global/kanvas/findings/"
    "for-406-montre-que-la-divergence-m60-f16-est-deja-visible-en-post-passe-aa-stencil-cover"
)

ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-407-m60-f16-aa-stencil-cover-contribution-isolation.md"
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

ALLOWED_PIXEL_CLASSIFICATIONS = {
    "coverage-aa-wrong",
    "source-color-wrong",
    "blend-source-over-wrong",
    "draw-order-or-accumulation-wrong",
    "insufficient-per-subdraw-data",
}
EXPECTED_VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for407_m60_f16_aa_stencil_cover_contribution_isolation.py",
    "rtk python3 scripts/validate_for406_m60_f16_post_pass_reference_comparison.py",
    "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
    "rtk python3 scripts/validate_for404_m60_f16_aa_stencil_cover_runtime_hook.py",
    "rtk python3 scripts/validate_for403_m60_f16_direct_pass_write_hook.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]

MISSING_PER_SUBDRAW_INPUTS = [
    {
        "field": "dstBeforeRgbaFloat",
        "whyRequired": "needed to replay source-over and separate bad blend math from bad inputs",
    },
    {
        "field": "sourceColorPremulRgbaFloat",
        "whyRequired": "needed to decide whether the source color supplied to the pass is wrong",
    },
    {
        "field": "coverageOrAaAlpha",
        "whyRequired": "needed to decide whether AA coverage, not color or blend, is wrong",
    },
    {
        "field": "expectedSourceOverRgbaFloat",
        "whyRequired": "needed to compare the pass output with independently replayed source-over",
    },
    {
        "field": "dstAfterEachInsideOutsideSubdrawRgbaFloat",
        "whyRequired": "needed to identify inside/outside draw order or accumulation mistakes",
    },
    {
        "field": "subdrawRole",
        "whyRequired": "needed to tie a sample to inside cover, outside cover, or another sub-draw role",
    },
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-407 validation failed: {message}")


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


def rgba8_delta(a: list[int], b: list[int]) -> dict[str, Any]:
    signed = [a[i] - b[i] for i in range(4)]
    absolute = [abs(v) for v in signed]
    return {
        "signedByChannel": {"r": signed[0], "g": signed[1], "b": signed[2], "a": signed[3]},
        "absoluteByChannel": {"r": absolute[0], "g": absolute[1], "b": absolute[2], "a": absolute[3]},
        "absoluteTotal": sum(absolute),
        "maxChannelDelta": max(absolute),
    }


def require_rgba8(value: Any, field: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA8")
    for channel in value:
        require(isinstance(channel, int) and 0 <= channel <= 255, f"{field} channel out of range")
    return value


def require_rgba_float(value: Any, field: str) -> list[float]:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA float")
    for channel in value:
        require(isinstance(channel, (float, int)), f"{field} channel must be numeric")
    return [float(channel) for channel in value]


def observed_event_sequence(for405: dict[str, Any], x: int, y: int) -> list[dict[str, Any]]:
    runtime = for405.get("runtimeReadback")
    require(isinstance(runtime, dict), "FOR-405 runtimeReadback missing")
    events = runtime.get("events")
    require(isinstance(events, list), "FOR-405 runtimeReadback events missing")
    sequence: list[dict[str, Any]] = []
    for event in events:
        require(isinstance(event, dict), "FOR-405 runtime event must be an object")
        samples = event.get("samples")
        require(isinstance(samples, list), "FOR-405 runtime event samples missing")
        match = None
        for sample in samples:
            require(isinstance(sample, dict), "FOR-405 runtime sample must be an object")
            if (sample.get("x"), sample.get("y")) == (x, y):
                match = sample
                break
        require(match is not None, f"FOR-405 runtime event missing sample at {(x, y)}")
        sequence.append(
            {
                "drawIndex": event.get("drawIndex"),
                "pipelineFamily": event.get("pipelineFamily"),
                "fillType": event.get("fillType"),
                "blendMode": event.get("blendMode"),
                "targetWithinScissor": match.get("targetWithinScissor"),
                "readbackAvailable": match.get("readbackAvailable"),
                "observedRgbaFloat": require_rgba_float(
                    match.get("observedRgbaFloat"),
                    f"FOR-405 event observedRgbaFloat at {(x, y)}",
                ),
                "observedRgba8": require_rgba8(
                    match.get("observedRgba8"),
                    f"FOR-405 event observedRgba8 at {(x, y)}",
                ),
            },
        )
    return sequence


def validate_source_artifacts(
    for401: dict[str, Any],
    for404: dict[str, Any],
    for405: dict[str, Any],
    for406: dict[str, Any],
) -> None:
    require(for401.get("linear") == "FOR-401", "FOR-401 source Linear id changed")
    require(for401.get("classification") == "residual-visible-only-at-final-readback", "FOR-401 classification changed")
    summary401 = for401.get("residualOriginSummary")
    require(isinstance(summary401, dict), "FOR-401 residualOriginSummary missing")
    require(summary401.get("currentTotalResidual") == 62748, "FOR-401 total residual changed")
    require(summary401.get("currentMismatchPixels") == 1615, "FOR-401 mismatch count changed")
    require(summary401.get("selectedResidualTotal") == 1560, "FOR-401 selected residual changed")
    require(summary401.get("readbackOnlyUnknownCount") == 16, "FOR-401 unknown count changed")

    require(for404.get("linear") == "FOR-404", "FOR-404 source Linear id changed")
    require(for404.get("classification") == "aa-stencil-cover-post-pass-readback-blocked", "FOR-404 classification changed")
    runtime404 = for404.get("runtimeHook")
    require(isinstance(runtime404, dict), "FOR-404 runtimeHook missing")
    require(runtime404.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "FOR-404 pipeline changed")
    require(runtime404.get("eventCount") == 3, "FOR-404 event count changed")
    require(runtime404.get("postPassReadbackAvailable") is False, "FOR-404 must remain readback-blocked")

    require(for405.get("linear") == "FOR-405", "FOR-405 source Linear id changed")
    require(for405.get("classification") == "aa-stencil-cover-post-pass-color-observed", "FOR-405 classification changed")
    runtime405 = for405.get("runtimeReadback")
    require(isinstance(runtime405, dict), "FOR-405 runtimeReadback missing")
    require(runtime405.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "FOR-405 pipeline changed")
    require(runtime405.get("intermediateFormat") == "RGBA16Float", "FOR-405 intermediate format changed")
    require(runtime405.get("eventCount") == 3, "FOR-405 event count changed")
    require(runtime405.get("postPassReadbackAvailable") is True, "FOR-405 post-pass readback must be available")
    summary405 = for405.get("postPassSummary")
    require(isinstance(summary405, dict), "FOR-405 postPassSummary missing")
    require(summary405.get("postPassObservedCount") == 16, "FOR-405 observed count changed")

    require(for406.get("linear") == "FOR-406", "FOR-406 source Linear id changed")
    require(for406.get("classification") == "post-pass-already-diverged", "FOR-406 classification changed")
    require(
        for406.get("globalConclusion") == "divergence-already-visible-in-post-pass",
        "FOR-406 conclusion changed",
    )
    summary406 = for406.get("comparisonSummary")
    require(isinstance(summary406, dict), "FOR-406 comparisonSummary missing")
    require(summary406.get("selectedPixelCount") == 16, "FOR-406 selected count changed")
    require(summary406.get("postPassAlreadyDivergedCount") == 16, "FOR-406 post-pass count changed")
    require(summary406.get("selectedPostPassToSkiaDeltaTotal") == 1560, "FOR-406 post-pass delta changed")
    require(summary406.get("selectedCurrentGpuFinalToSkiaDeltaTotal") == 1560, "FOR-406 final delta changed")


def build_artifact() -> dict[str, Any]:
    for401 = load_json(FOR401_ARTIFACT)
    for404 = load_json(FOR404_ARTIFACT)
    for405 = load_json(FOR405_ARTIFACT)
    for406 = load_json(FOR406_ARTIFACT)
    validate_source_artifacts(for401, for404, for405, for406)

    selected401 = for401.get("selectedPixels")
    selected405 = for405.get("selectedPixels")
    selected406 = for406.get("selectedPixels")
    require(isinstance(selected401, list) and len(selected401) == 16, "FOR-401 selected pixel count changed")
    require(isinstance(selected405, list) and len(selected405) == 16, "FOR-405 selected pixel count changed")
    require(isinstance(selected406, list) and len(selected406) == 16, "FOR-406 selected pixel count changed")
    by405 = by_coordinate(selected405, "FOR-405")
    by406 = by_coordinate(selected406, "FOR-406")

    pixels: list[dict[str, Any]] = []
    for index, pixel401 in enumerate(selected401):
        require(isinstance(pixel401, dict), f"FOR-401 pixel {index} must be an object")
        x, y = pixel_key(pixel401)
        pixel405 = by405.get((x, y))
        pixel406 = by406.get((x, y))
        require(pixel405 is not None, f"FOR-405 missing coordinate {(x, y)}")
        require(pixel406 is not None, f"FOR-406 missing coordinate {(x, y)}")

        post = pixel405.get("postPass")
        require(isinstance(post, dict), f"FOR-405 postPass missing at {(x, y)}")
        post_rgba8 = require_rgba8(post.get("observedRgba8"), f"FOR-405 postPass observedRgba8 at {(x, y)}")
        post_rgba_float = require_rgba_float(
            post.get("observedRgbaFloat"),
            f"FOR-405 postPass observedRgbaFloat at {(x, y)}",
        )
        current_gpu = require_rgba8(pixel406.get("currentGpuFinalRgba"), f"FOR-406 currentGpuFinalRgba at {(x, y)}")
        skia = require_rgba8(pixel406.get("skiaRgba"), f"FOR-406 skiaRgba at {(x, y)}")
        cpu = require_rgba8(pixel406.get("cpuRgba"), f"FOR-406 cpuRgba at {(x, y)}")
        require(post_rgba8 == pixel406.get("postPassObservedRgba8"), f"FOR-405/FOR-406 post-pass mismatch at {(x, y)}")
        require(current_gpu == pixel401.get("currentGpuRgba"), f"FOR-401/FOR-406 current GPU mismatch at {(x, y)}")
        require(skia == pixel401.get("referenceRgba"), f"FOR-401/FOR-406 reference mismatch at {(x, y)}")
        require(post_rgba8 == current_gpu, f"post-pass/final mismatch at {(x, y)}")
        require(cpu == skia, f"CPU/Skia mismatch at {(x, y)}")
        require(pixel406.get("classification") == "post-pass-already-diverged", f"FOR-406 pixel changed at {(x, y)}")

        pixels.append(
            {
                "index": index,
                "x": x,
                "y": y,
                "classification": "insufficient-per-subdraw-data",
                "classificationReason": (
                    "FOR-401/FOR-405/FOR-406 prove the post-pass color already diverges, "
                    "but the sources do not expose per-subdraw source color, AA coverage, "
                    "destination-before, source-over replay, or inside/outside accumulation."
                ),
                "availableEvidence": {
                    "for401CurrentGpuRgba": require_rgba8(
                        pixel401.get("currentGpuRgba"),
                        f"FOR-401 currentGpuRgba at {(x, y)}",
                    ),
                    "for401ReferenceRgba": require_rgba8(
                        pixel401.get("referenceRgba"),
                        f"FOR-401 referenceRgba at {(x, y)}",
                    ),
                    "for401ResidualByChannel": pixel401.get("residualByChannel"),
                    "for401ResidualTotal": pixel401.get("residualTotal"),
                    "postPassObservedRgba8": post_rgba8,
                    "postPassObservedRgbaFloat": post_rgba_float,
                    "currentGpuFinalRgba": current_gpu,
                    "cpuRgba": cpu,
                    "skiaRgba": skia,
                    "postPassEqualsCurrentGpuFinal": True,
                    "cpuEqualsSkia": True,
                    "postPassToSkiaDelta": rgba8_delta(post_rgba8, skia),
                    "currentGpuFinalToSkiaDelta": rgba8_delta(current_gpu, skia),
                    "observedAfterStencilCoverDraws": observed_event_sequence(for405, x, y),
                    "for400UsedAsDirectProof": False,
                },
                "missingEvidence": MISSING_PER_SUBDRAW_INPUTS,
                "excludedClassifications": {
                    "coverage-aa-wrong": "coverageOrAaAlpha is not present per pixel/subdraw",
                    "source-color-wrong": "sourceColorPremulRgbaFloat is not present per pixel/subdraw",
                    "blend-source-over-wrong": "dstBefore/source/coverage inputs and independent source-over output are not present",
                    "draw-order-or-accumulation-wrong": (
                        "only post-draw boundary colors are available; inside/outside subdraw order "
                        "and after-each-subdraw accumulation are not present"
                    ),
                },
            },
        )

    summary401 = for401["residualOriginSummary"]
    summary405 = for405["postPassSummary"]
    summary406 = for406["comparisonSummary"]
    runtime405 = for405["runtimeReadback"]
    artifact = {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceSceneId": ROW_ID,
        "sourceDraftMemory": SOURCE_DRAFT_MEMORY,
        "sourceMemory": {
            "for405": SOURCE_MEMORY_FOR405,
            "for406": SOURCE_MEMORY_FOR406,
        },
        "sourceArtifacts": {
            "for401": rel(FOR401_ARTIFACT),
            "for404": rel(FOR404_ARTIFACT),
            "for405": rel(FOR405_ARTIFACT),
            "for406": rel(FOR406_ARTIFACT),
        },
        "producer": "scripts/validate_for407_m60_f16_aa_stencil_cover_contribution_isolation.py",
        "decision": "M60_F16_AA_STENCIL_COVER_CONTRIBUTION_ISOLATION_RECORDED",
        "classification": "insufficient-per-subdraw-data",
        "globalClassification": "insufficient-per-subdraw-data",
        "globalConclusion": "per-subdraw-inputs-required-before-root-cause-classification",
        "allowedPixelClassifications": sorted(ALLOWED_PIXEL_CLASSIFICATIONS),
        "allowedGlobalClassifications": sorted(ALLOWED_PIXEL_CLASSIFICATIONS),
        "supportClaim": False,
        "promoted": False,
        "correctionAppliedByDefault": False,
        "defaultRenderingChanged": False,
        "sourceContext": {
            "for401Classification": for401.get("classification"),
            "for401CurrentTotalResidual": summary401.get("currentTotalResidual"),
            "for401CurrentMismatchPixels": summary401.get("currentMismatchPixels"),
            "for401SelectedResidualTotal": summary401.get("selectedResidualTotal"),
            "for401SelectedPixelCount": len(selected401),
            "for404Classification": for404.get("classification"),
            "for405Classification": for405.get("classification"),
            "for405PostPassObservedCount": summary405.get("postPassObservedCount"),
            "for406Classification": for406.get("classification"),
            "for406GlobalConclusion": for406.get("globalConclusion"),
            "for406PostPassAlreadyDivergedCount": summary406.get("postPassAlreadyDivergedCount"),
            "for406SelectedPostPassToSkiaDeltaTotal": summary406.get("selectedPostPassToSkiaDeltaTotal"),
            "for400EvidencePolicy": "context-only-not-direct-write-proof",
            "for400UsedAsDirectProof": False,
        },
        "availableData": {
            "selectedFinalResidualPixels": True,
            "postPassBoundaryRgba16FloatReadback": True,
            "postPassBoundaryEventCount": runtime405.get("eventCount"),
            "postPassBoundaryDrawIndexes": [event.get("drawIndex") for event in runtime405.get("events", [])],
            "postPassMatchesCurrentGpuFinalCount": summary406.get("postPassEqualsCurrentGpuFinalCount"),
            "cpuMatchesSkiaCount": summary406.get("cpuEqualsSkiaCount"),
            "postPassToSkiaDeltaTotal": summary406.get("selectedPostPassToSkiaDeltaTotal"),
            "finalGpuToSkiaDeltaTotal": summary406.get("selectedCurrentGpuFinalToSkiaDeltaTotal"),
        },
        "missingData": MISSING_PER_SUBDRAW_INPUTS,
        "classificationRules": {
            "coverage-aa-wrong": "requires per-pixel/subdraw coverage or AA alpha compared with expected coverage",
            "source-color-wrong": "requires per-pixel/subdraw source color actually consumed by the pass",
            "blend-source-over-wrong": "requires source, coverage, destination-before, and independently replayed source-over output",
            "draw-order-or-accumulation-wrong": "requires after-each inside/outside subdraw state or equivalent accumulation trace",
            "insufficient-per-subdraw-data": (
                "selected post-pass colors are known, but at least one required per-subdraw input "
                "for all four concrete root-cause classes is absent"
            ),
        },
        "isolationSummary": {
            "selectedPixelCount": len(pixels),
            "insufficientPerSubdrawDataCount": len(pixels),
            "coverageAaWrongCount": 0,
            "sourceColorWrongCount": 0,
            "blendSourceOverWrongCount": 0,
            "drawOrderOrAccumulationWrongCount": 0,
            "postPassAlreadyDivergedCount": summary406.get("postPassAlreadyDivergedCount"),
            "postPassObservedCount": summary405.get("postPassObservedCount"),
            "postPassEqualsCurrentGpuFinalCount": summary406.get("postPassEqualsCurrentGpuFinalCount"),
            "cpuEqualsSkiaCount": summary406.get("cpuEqualsSkiaCount"),
            "selectedPostPassToSkiaDeltaTotal": summary406.get("selectedPostPassToSkiaDeltaTotal"),
            "selectedCurrentGpuFinalToSkiaDeltaTotal": summary406.get("selectedCurrentGpuFinalToSkiaDeltaTotal"),
            "for401SelectedResidualTotal": summary401.get("selectedResidualTotal"),
        },
        "minimalNextHook": {
            "required": True,
            "guardId": "kanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled",
            "enabledByDefault": False,
            "scope": "M60 F16 only, StencilCoverAaPolygonDraw only, exactly the 16 FOR-401 coordinates",
            "implementationPoint": (
                "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt around the "
                "StencilCoverAaPolygonDraw cover sub-draw execution"
            ),
            "recordLimit": {
                "pixelCount": 16,
                "pipelineFamily": "StencilCoverAaPolygonDraw",
                "drawIndexes": [1, 3, 5],
            },
            "recordFields": [
                "pixel coordinate",
                "drawIndex",
                "subdrawOrdinal",
                "subdrawRole inside/outside/other",
                "dstBeforeRgbaFloat from the intermediate texture",
                "sourceColorPremulRgbaFloat used by the draw",
                "coverageOrAaAlpha used by the fragment",
                "blendMode, expected kSrcOver",
                "expectedSourceOverRgbaFloat recomputed from the recorded inputs",
                "dstAfterRgbaFloat after that subdraw",
            ],
            "successCriterion": (
                "each FOR-401 pixel can be assigned to coverage-aa-wrong, source-color-wrong, "
                "blend-source-over-wrong, or draw-order-or-accumulation-wrong without using FOR-400 "
                "as direct write proof"
            ),
        },
        "selectedPixels": pixels,
        "nonGoalsPreserved": {
            "defaultRenderingChanged": False,
            "supportClaimRaised": False,
            "promoted": False,
            "thresholdChanged": False,
            "scoringChanged": False,
            "correctionApplied": False,
            "for380BroadCorrectionReintroduced": False,
            "for400UsedAsDirectProof": False,
            "generalizedOutsideM60F16": False,
            "kotlinRenderingChanged": False,
        },
        "classificationReason": (
            "Existing FOR-401/FOR-405/FOR-406 evidence proves all 16 selected pixels diverge by "
            "the post-pass boundary, but it does not contain per-subdraw source color, AA coverage, "
            "destination-before, independently replayed source-over, or inside/outside accumulation."
        ),
        "validationCommands": EXPECTED_VALIDATION_COMMANDS,
    }
    return artifact


def build_report(data: dict[str, Any]) -> str:
    summary = data["isolationSummary"]
    rows = []
    for pixel in data["selectedPixels"]:
        available = pixel["availableEvidence"]
        rows.append(
            "| ({x}, {y}) | `{post}` | `{skia}` | `{delta}` | `{events}` | `{classification}` |".format(
                x=pixel["x"],
                y=pixel["y"],
                post=available["postPassObservedRgba8"],
                skia=available["skiaRgba"],
                delta=available["postPassToSkiaDelta"]["absoluteTotal"],
                events=len(available["observedAfterStencilCoverDraws"]),
                classification=pixel["classification"],
            ),
        )
    rows_text = "\n".join(rows)
    missing_rows = "\n".join(
        f"| `{item['field']}` | {item['whyRequired']} |" for item in data["missingData"]
    )
    commands = "\n".join(f"- `{command}`" for command in data["validationCommands"])
    hook = data["minimalNextHook"]
    hook_fields = "\n".join(f"- `{field}`" for field in hook["recordFields"])
    return f"""# FOR-407 M60 F16 AA stencil-cover contribution isolation

Date: 2026-06-05

This analytical report reuses FOR-401, FOR-404, FOR-405, and FOR-406 evidence
for the 16 M60 F16 residual coordinates. It does not change Kotlin rendering,
default diagnostics, score thresholds, support policy, or promotion state.

## Result

Global classification: `{data["globalClassification"]}`.
Conclusion: `{data["globalConclusion"]}`.

FOR-406 already proves that all 16 pixels diverge at the post-pass
`StencilCoverAaPolygonDraw` boundary. FOR-405 also provides RGBA16Float
post-pass readback for the same pixels and three draw events. That is enough
to place the divergence before final present/readback, but not enough to
separate AA coverage, source color, source-over blend math, or inside/outside
subdraw accumulation.

## Sources

| Source | Path |
|---|---|
| FOR-401 selected residual pixels | `{data["sourceArtifacts"]["for401"]}` |
| FOR-404 runtime-hook context | `{data["sourceArtifacts"]["for404"]}` |
| FOR-405 post-pass readback | `{data["sourceArtifacts"]["for405"]}` |
| FOR-406 post-pass/reference comparison | `{data["sourceArtifacts"]["for406"]}` |
| FOR-405 finding memory | `{data["sourceMemory"]["for405"]}` |
| FOR-406 finding memory | `{data["sourceMemory"]["for406"]}` |
| Ticket draft memory | `{data["sourceDraftMemory"]}` |

FOR-400 remains `context-only-not-direct-write-proof`.

## Existing Data

| Metric | Value |
|---|---:|
| Selected FOR-401 pixels | `{summary["selectedPixelCount"]}` |
| Classified insufficient per-subdraw data | `{summary["insufficientPerSubdrawDataCount"]}` |
| Post-pass already diverged | `{summary["postPassAlreadyDivergedCount"]}` |
| Post-pass observed pixels | `{summary["postPassObservedCount"]}` |
| Post-pass equals current GPU final | `{summary["postPassEqualsCurrentGpuFinalCount"]}` |
| CPU equals Skia | `{summary["cpuEqualsSkiaCount"]}` |
| Sum delta post-pass -> Skia | `{summary["selectedPostPassToSkiaDeltaTotal"]}` |
| Sum delta final GPU -> Skia | `{summary["selectedCurrentGpuFinalToSkiaDeltaTotal"]}` |
| FOR-401 selected residual total | `{summary["for401SelectedResidualTotal"]}` |

## Missing Data

| Missing field | Why it is required |
|---|---|
{missing_rows}

## Pixel Classification

| Coordinate | Post-pass observed | Skia | Delta post-pass->Skia | Observed draw boundaries | Classification |
|---|---|---|---:|---:|---|
{rows_text}

## Minimal Next Hook

Required guard: `{hook["guardId"]}`.
Enabled by default: `{str(hook["enabledByDefault"]).lower()}`.
Scope: `{hook["scope"]}`.
Implementation point: `{hook["implementationPoint"]}`.

The hook should record only:

{hook_fields}

Success criterion: {hook["successCriterion"]}.

## Non-Goals Preserved

| Contract | Value |
|---|---|
| supportClaim=false | `{str(data["supportClaim"]).lower()}` |
| promoted=false | `{str(data["promoted"]).lower()}` |
| correctionAppliedByDefault=false | `{str(data["correctionAppliedByDefault"]).lower()}` |
| defaultRenderingChanged=false | `{str(data["defaultRenderingChanged"]).lower()}` |
| FOR-400 direct proof | `{data["nonGoalsPreserved"]["for400UsedAsDirectProof"]}` |

## Validations

{commands}
"""


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


def validate_artifact(actual: dict[str, Any], expected: dict[str, Any]) -> str:
    require(actual == expected, f"{rel(ARTIFACT)} is stale or does not match recomputed FOR-407 analysis")
    require(actual.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(actual.get("linear") == LINEAR_ID, "wrong Linear id")
    require(actual.get("sceneId") == SCENE_ID, "wrong scene id")
    require(actual.get("sourceSceneId") == ROW_ID, "wrong source scene id")
    require(actual.get("sourceDraftMemory") == SOURCE_DRAFT_MEMORY, "wrong ticket draft memory")
    source_memory = actual.get("sourceMemory")
    require(isinstance(source_memory, dict), "sourceMemory missing")
    require(source_memory.get("for405") == SOURCE_MEMORY_FOR405, "wrong FOR-405 source memory")
    require(source_memory.get("for406") == SOURCE_MEMORY_FOR406, "wrong FOR-406 source memory")
    require(actual.get("sourceArtifacts", {}).get("for401") == rel(FOR401_ARTIFACT), "FOR-401 artifact not linked")
    require(actual.get("sourceArtifacts", {}).get("for404") == rel(FOR404_ARTIFACT), "FOR-404 artifact not linked")
    require(actual.get("sourceArtifacts", {}).get("for405") == rel(FOR405_ARTIFACT), "FOR-405 artifact not linked")
    require(actual.get("sourceArtifacts", {}).get("for406") == rel(FOR406_ARTIFACT), "FOR-406 artifact not linked")
    require(actual.get("classification") == "insufficient-per-subdraw-data", "global classification changed")
    require(actual.get("globalClassification") == "insufficient-per-subdraw-data", "global classification missing")
    require(
        actual.get("globalConclusion") == "per-subdraw-inputs-required-before-root-cause-classification",
        "global conclusion changed",
    )
    require(set(actual.get("allowedPixelClassifications", [])) == ALLOWED_PIXEL_CLASSIFICATIONS, "pixel taxonomy changed")
    require(set(actual.get("allowedGlobalClassifications", [])) == ALLOWED_PIXEL_CLASSIFICATIONS, "global taxonomy changed")
    require(actual.get("supportClaim") is False, "supportClaim must remain false")
    require(actual.get("promoted") is False, "promoted must remain false")
    require(actual.get("correctionAppliedByDefault") is False, "correction must not be default")
    require(actual.get("defaultRenderingChanged") is False, "default rendering must not change")

    context = actual.get("sourceContext")
    require(isinstance(context, dict), "sourceContext missing")
    require(context.get("for400EvidencePolicy") == "context-only-not-direct-write-proof", "FOR-400 policy changed")
    require(context.get("for400UsedAsDirectProof") is False, "FOR-400 used as direct proof")
    require(context.get("for401SelectedPixelCount") == 16, "FOR-401 selected count changed")
    require(context.get("for405PostPassObservedCount") == 16, "FOR-405 observed count changed")
    require(context.get("for406PostPassAlreadyDivergedCount") == 16, "FOR-406 post-pass count changed")

    summary = actual.get("isolationSummary")
    require(isinstance(summary, dict), "isolationSummary missing")
    require(summary.get("selectedPixelCount") == 16, "selected count changed")
    require(summary.get("insufficientPerSubdrawDataCount") == 16, "insufficient count changed")
    require(summary.get("coverageAaWrongCount") == 0, "coverage classification count changed")
    require(summary.get("sourceColorWrongCount") == 0, "source classification count changed")
    require(summary.get("blendSourceOverWrongCount") == 0, "blend classification count changed")
    require(summary.get("drawOrderOrAccumulationWrongCount") == 0, "order classification count changed")
    require(summary.get("selectedPostPassToSkiaDeltaTotal") == 1560, "post-pass delta changed")
    require(summary.get("selectedCurrentGpuFinalToSkiaDeltaTotal") == 1560, "final delta changed")
    require(summary.get("for401SelectedResidualTotal") == 1560, "FOR-401 selected residual changed")

    hook = actual.get("minimalNextHook")
    require(isinstance(hook, dict), "minimalNextHook missing")
    require(hook.get("required") is True, "next hook must be required")
    require(
        hook.get("guardId") == "kanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled",
        "next hook guard changed",
    )
    require(hook.get("enabledByDefault") is False, "next hook must be disabled by default")
    require(hook.get("recordLimit", {}).get("pixelCount") == 16, "next hook pixel bound changed")
    require(hook.get("recordLimit", {}).get("pipelineFamily") == "StencilCoverAaPolygonDraw", "next hook pipeline changed")

    selected = actual.get("selectedPixels")
    require(isinstance(selected, list) and len(selected) == 16, "selectedPixels missing")
    seen: set[tuple[int, int]] = set()
    for index, pixel in enumerate(selected):
        require(pixel.get("index") == index, f"pixel index changed at {index}")
        key = pixel_key(pixel)
        require(key not in seen, f"duplicate selected coordinate: {key}")
        seen.add(key)
        require(pixel.get("classification") == "insufficient-per-subdraw-data", f"pixel classification changed at {index}")
        available = pixel.get("availableEvidence")
        require(isinstance(available, dict), f"availableEvidence missing at {index}")
        require(available.get("postPassObservedRgba8") == available.get("currentGpuFinalRgba"), f"post/final mismatch at {index}")
        require(available.get("cpuRgba") == available.get("skiaRgba"), f"CPU/Skia mismatch at {index}")
        require(available.get("for400UsedAsDirectProof") is False, f"FOR-400 direct proof at {index}")
        require(
            available.get("postPassToSkiaDelta", {}).get("absoluteTotal")
            == available.get("currentGpuFinalToSkiaDelta", {}).get("absoluteTotal"),
            f"post/final delta mismatch at {index}",
        )
        events = available.get("observedAfterStencilCoverDraws")
        require(isinstance(events, list) and len(events) == 3, f"event sequence missing at {index}")
        for event in events:
            require(event.get("pipelineFamily") == "StencilCoverAaPolygonDraw", f"event pipeline changed at {index}")
            require(event.get("blendMode") == "kSrcOver", f"event blend mode changed at {index}")
        require(pixel.get("missingEvidence") == MISSING_PER_SUBDRAW_INPUTS, f"missing evidence changed at {index}")
        excluded = pixel.get("excludedClassifications")
        require(isinstance(excluded, dict), f"excluded classifications missing at {index}")
        for classification in ALLOWED_PIXEL_CLASSIFICATIONS - {"insufficient-per-subdraw-data"}:
            require(classification in excluded, f"missing exclusion reason for {classification} at {index}")

    non_goals = actual.get("nonGoalsPreserved")
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
        "kotlinRenderingChanged",
    ):
        require(non_goals.get(key) is False, f"non-goal changed: {key}")
    require(actual.get("validationCommands") == EXPECTED_VALIDATION_COMMANDS, "validation command list changed")
    return str(actual.get("classification"))


def validate_report(classification: str) -> None:
    text = read_text(REPORT)
    for needle in (
        "FOR-407 M60 F16 AA stencil-cover contribution isolation",
        f"Global classification: `{classification}`",
        "Conclusion: `per-subdraw-inputs-required-before-root-cause-classification`",
        "FOR-400 remains `context-only-not-direct-write-proof`.",
        "Classified insufficient per-subdraw data | `16`",
        "Post-pass already diverged | `16`",
        "Sum delta post-pass -> Skia | `1560`",
        "`dstBeforeRgbaFloat`",
        "`sourceColorPremulRgbaFloat`",
        "`coverageOrAaAlpha`",
        "Required guard: `kanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled`",
        "supportClaim=false",
        "promoted=false",
        "correctionAppliedByDefault=false",
        "defaultRenderingChanged=false",
        "rtk python3 scripts/validate_for407_m60_f16_aa_stencil_cover_contribution_isolation.py",
    ):
        require(needle in text, f"report missing: {needle}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="rewrite the FOR-407 artifact and report")
    args = parser.parse_args()

    expected = build_artifact()
    report = build_report(expected)
    if args.write:
        ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
        write_if_changed(ARTIFACT, json.dumps(expected, indent=2, sort_keys=False) + "\n")
        write_if_changed(REPORT, report)

    actual = load_json(ARTIFACT)
    classification = validate_artifact(actual, expected)
    validate_report(classification)
    print(f"FOR-407 validation passed: {classification}")


if __name__ == "__main__":
    main()
