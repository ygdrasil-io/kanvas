#!/usr/bin/env python3
"""Generate and validate FOR-414 M60 F16 post-draw readback evidence."""

from __future__ import annotations

import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-414"
SCENE_ID = "m60-f16-aa-stencil-cover-post-draw-readback-for414"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
SOURCE_DRAFT_MEMORY = (
    "global/kanvas/tickets/drafts/"
    "brouillon-ticket-for-414-m60-f16-capturer-letat-texture-immediatement-apres-draw-aa-stencil-cover"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-413-correle-les-transitions-de-draw-aa-stencil-cover-et-localise-des-mutations-malgre-retour-shader-zero"
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
    / "2026-06-05-for-414-m60-f16-aa-stencil-cover-post-draw-readback.md"
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
FOR410_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-predraw-dst-readback-for410"
    / "m60-f16-aa-stencil-cover-predraw-dst-readback-for410.json"
)
FOR412_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-shader-return-diagnostic-for412"
    / "m60-f16-aa-stencil-cover-shader-return-diagnostic-for412.json"
)
FOR413_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-draw-transition-correlation-for413"
    / "m60-f16-aa-stencil-cover-draw-transition-correlation-for413.json"
)

MATCH_TOLERANCE = 1e-6
DRAW_BOUNDARIES = (
    (1, 3, "FOR-410 drawIndex 1 predraw compute readback", "FOR-410 drawIndex 3 predraw compute readback"),
    (3, 5, "FOR-410 drawIndex 3 predraw compute readback", "FOR-410 drawIndex 5 predraw compute readback"),
    (5, None, "FOR-410 drawIndex 5 predraw compute readback", "FOR-405 final post-pass selected pixel"),
)
ALLOWED_CLASSIFICATIONS = {
    "post-draw-matches-next-predraw",
    "post-draw-still-before",
    "post-draw-mutates-differently",
    "post-draw-readback-unavailable",
}
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
EXPECTED_VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py",
    "rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py",
    "rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py",
    "rtk python3 scripts/validate_for410_m60_f16_aa_stencil_cover_predraw_dst_readback.py",
    "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for414-pycache python3 -m py_compile "
        "scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py"
    ),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-414 validation failed: {message}")


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


def pixel_key(pixel: dict[str, Any]) -> tuple[int, int]:
    x = pixel.get("x")
    y = pixel.get("y")
    require(isinstance(x, int) and isinstance(y, int), "pixel coordinate missing")
    return x, y


def require_rgba(value: Any, field: str) -> list[float]:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA float")
    out: list[float] = []
    for channel in value:
        require(isinstance(channel, (float, int)), f"{field} channel must be numeric")
        out.append(float(channel))
    return out


def max_abs_delta(a: list[float], b: list[float]) -> float:
    return max(abs(a[index] - b[index]) for index in range(4))


def delta(a: list[float], b: list[float]) -> dict[str, Any]:
    signed = [round(float(b[index] - a[index]), 9) for index in range(4)]
    absolute = [round(abs(channel), 9) for channel in signed]
    max_channel = max(absolute)
    return {
        "signedRgbaFloat": signed,
        "absoluteRgbaFloat": absolute,
        "absoluteTotalFloat": round(sum(absolute), 9),
        "maxChannelFloat": max_channel,
        "withinTolerance": max_channel <= MATCH_TOLERANCE,
        "tolerance": MATCH_TOLERANCE,
    }


def compact_shader_subdraw(subdraw: dict[str, Any]) -> dict[str, Any]:
    return {
        "drawIndex": subdraw.get("drawIndex"),
        "subdrawOrdinal": subdraw.get("subdrawOrdinal"),
        "subdrawRole": subdraw.get("subdrawRole"),
        "pipelineFamily": subdraw.get("pipelineFamily"),
        "blendMode": subdraw.get("blendMode"),
        "shaderObserved": subdraw.get("shaderObserved") is True,
        "captureSynthetic": subdraw.get("captureSynthetic") is True,
        "sourceColorSentToBlend": subdraw.get("sourceColorSentToBlend"),
        "classification": subdraw.get("classification"),
    }


def classify_post_draw(
    before: list[float],
    post_draw: list[float] | None,
    next_boundary: list[float],
    readback_reason: str | None,
) -> tuple[str, str]:
    if post_draw is None:
        require(bool(readback_reason), "post-draw readback unavailable without reason")
        return (
            "post-draw-readback-unavailable",
            readback_reason
            or "The immediate post-draw texture readback is unavailable for this boundary.",
        )
    if max_abs_delta(post_draw, next_boundary) <= MATCH_TOLERANCE:
        return (
            "post-draw-matches-next-predraw",
            "The immediate post-draw texture sample matches the next FOR-410/FOR-405 boundary under strict tolerance.",
        )
    if max_abs_delta(post_draw, before) <= MATCH_TOLERANCE:
        return (
            "post-draw-still-before",
            "The immediate post-draw texture sample still matches the FOR-410 predraw boundary under strict tolerance.",
        )
    return (
        "post-draw-mutates-differently",
        "The immediate post-draw texture sample changes but matches neither the predraw nor next boundary.",
    )


def shader_state(subdraws: list[dict[str, Any]]) -> dict[str, Any]:
    real_sources = [
        require_rgba(subdraw["sourceColorSentToBlend"], "sourceColorSentToBlend")
        for subdraw in subdraws
        if subdraw.get("shaderObserved") is True
        and subdraw.get("captureSynthetic") is False
        and subdraw.get("sourceColorSentToBlend") is not None
    ]
    unavailable = sum(1 for subdraw in subdraws if subdraw.get("shaderObserved") is not True)
    synthetic = sum(1 for subdraw in subdraws if subdraw.get("captureSynthetic") is True)
    return {
        "subdrawCount": len(subdraws),
        "realObservedCount": len(real_sources),
        "unavailableCount": unavailable,
        "syntheticCount": synthetic,
        "allObservedSourcesZero": bool(real_sources)
        and all(max(abs(channel) for channel in source) <= MATCH_TOLERANCE for source in real_sources),
        "unavailableTreatedAsSyntheticZero": False,
    }


def build_post_draw_record(
    draw_index: int,
    after_draw_index: int | None,
    before_source: str,
    next_boundary_source: str,
    predraw_samples: dict[int, dict[str, Any]],
    post_pass_sample: dict[str, Any],
    post_draw_events: dict[int, dict[tuple[int, int], dict[str, Any]]],
    shader_subdraws: list[dict[str, Any]],
    for413_transitions: dict[str, dict[str, Any]],
    point: tuple[int, int],
) -> dict[str, Any]:
    x, y = point
    before_sample = predraw_samples.get(draw_index)
    require(before_sample is not None, f"missing FOR-410 predraw sample for draw {draw_index} at {point}")
    before = require_rgba(before_sample.get("dstBeforeRgbaFloat"), f"draw {draw_index} before")

    if after_draw_index is None:
        next_boundary = require_rgba(
            post_pass_sample.get("observedRgbaFloat"),
            f"draw {draw_index} final post-pass boundary",
        )
    else:
        next_sample = predraw_samples.get(after_draw_index)
        require(next_sample is not None, f"missing FOR-410 predraw sample for draw {after_draw_index} at {point}")
        next_boundary = require_rgba(
            next_sample.get("dstBeforeRgbaFloat"),
            f"draw {after_draw_index} next boundary",
        )

    event_sample = post_draw_events.get(draw_index, {}).get(point)
    readback_available = bool(event_sample and event_sample.get("readbackAvailable") is True)
    post_draw = (
        require_rgba(event_sample.get("observedRgbaFloat"), f"draw {draw_index} postDraw")
        if readback_available and event_sample is not None
        else None
    )
    readback_reason = event_sample.get("reason") if isinstance(event_sample, dict) else None
    classification, reason = classify_post_draw(before, post_draw, next_boundary, readback_reason)
    post_vs_before = delta(before, post_draw) if post_draw is not None else None
    post_vs_next = delta(next_boundary, post_draw) if post_draw is not None else None
    compact_subdraws = [
        compact_shader_subdraw(subdraw)
        for subdraw in shader_subdraws
        if subdraw.get("drawIndex") == draw_index
    ]
    transition_id = f"{draw_index}->postPass" if after_draw_index is None else f"{draw_index}->{after_draw_index}"
    for413_transition = for413_transitions.get(transition_id)

    return {
        "drawIndex": draw_index,
        "transitionId": transition_id,
        "pipelineFamily": "StencilCoverAaPolygonDraw",
        "beforeSource": before_source,
        "beforeRgbaFloat": before,
        "postDrawSource": "FOR-405 runtimeReadback.events immediately after draw render pass",
        "postDrawReadbackAvailable": readback_available,
        "postDrawRgbaFloat": post_draw,
        "nextBoundarySource": next_boundary_source,
        "nextBoundaryRgbaFloat": next_boundary,
        "postDrawVsBeforeDelta": post_vs_before,
        "postDrawVsNextBoundaryDelta": post_vs_next,
        "shaderReturnSubdraws": compact_subdraws,
        "shaderReturnState": shader_state(compact_subdraws),
        "for413TransitionClassification": (
            for413_transition.get("classification") if for413_transition else None
        ),
        "for413TransitionDelta": for413_transition.get("delta") if for413_transition else None,
        "classification": classification,
        "classificationReason": reason,
    }


def build_artifact() -> dict[str, Any]:
    for401 = load_json(FOR401_ARTIFACT)
    for405 = load_json(FOR405_ARTIFACT)
    for410 = load_json(FOR410_ARTIFACT)
    for412 = load_json(FOR412_ARTIFACT)
    for413 = load_json(FOR413_ARTIFACT)

    require(for401.get("classification") == "residual-visible-only-at-final-readback", "FOR-401 classification changed")
    require(for405.get("classification") == "aa-stencil-cover-post-pass-color-observed", "FOR-405 classification changed")
    require(for410.get("classification") == "predraw-dst-captured", "FOR-410 classification changed")
    require(for412.get("classification") == "shader-return-zero-but-post-pass-colored", "FOR-412 classification changed")
    require(for413.get("classification") == "draw-mutates-despite-zero-shader-return", "FOR-413 classification changed")

    selected_points = [pixel_key(pixel) for pixel in for401["selectedPixels"]]
    require(selected_points == EXPECTED_POINTS, "FOR-401 selected pixel order changed")

    post_runtime = for405.get("runtimeReadback", {})
    post_events = post_runtime.get("events")
    require(isinstance(post_events, list), "FOR-405 runtimeReadback.events missing")
    require([event.get("drawIndex") for event in post_events] == [1, 3, 5], "FOR-405 event draw order changed")
    post_draw_events = {
        event["drawIndex"]: {pixel_key(sample): sample for sample in event.get("samples", [])}
        for event in post_events
    }
    predraw_by_point = {pixel_key(pixel): pixel for pixel in for410["selectedPixels"]}
    shader_by_point = {pixel_key(pixel): pixel for pixel in for412["selectedPixels"]}
    post_by_point = {pixel_key(pixel): pixel["postPass"] for pixel in for405["selectedPixels"]}
    for413_by_point = {pixel_key(pixel): pixel for pixel in for413["selectedPixels"]}

    selected_pixels: list[dict[str, Any]] = []
    classification_counter: Counter[str] = Counter()
    per_draw_counter: dict[int, Counter[str]] = defaultdict(Counter)
    zero_return_mutation_counter: Counter[str] = Counter()

    for point in selected_points:
        x, y = point
        predraw_pixel = predraw_by_point[point]
        shader_pixel = shader_by_point[point]
        for413_pixel = for413_by_point[point]
        predraw_samples = {sample["drawIndex"]: sample for sample in predraw_pixel["predrawSamples"]}
        for413_transitions = {
            transition["transitionId"]: transition
            for transition in for413_pixel["transitions"]
        }
        post_draw_records = [
            build_post_draw_record(
                draw,
                after_draw,
                before_source,
                next_boundary_source,
                predraw_samples,
                post_by_point[point],
                post_draw_events,
                shader_pixel["shaderReturnSubdraws"],
                for413_transitions,
                point,
            )
            for draw, after_draw, before_source, next_boundary_source in DRAW_BOUNDARIES
        ]
        for record in post_draw_records:
            classification_counter[record["classification"]] += 1
            per_draw_counter[record["drawIndex"]][record["classification"]] += 1
            if record["for413TransitionClassification"] == "draw-mutates-despite-zero-shader-return":
                zero_return_mutation_counter[record["classification"]] += 1

        selected_pixels.append(
            {
                "x": x,
                "y": y,
                "classification": (
                    "post-draw-matches-next-predraw"
                    if any(
                        record["classification"] == "post-draw-matches-next-predraw"
                        and record["for413TransitionClassification"]
                        == "draw-mutates-despite-zero-shader-return"
                        for record in post_draw_records
                    )
                    else post_draw_records[0]["classification"]
                ),
                "postDrawRecords": post_draw_records,
                "classificationReason": (
                    "For zero-return mutating transitions, the immediate post-draw sample is already the next "
                    "boundary when this pixel contributes to the FOR-413 zero-return mutation class."
                ),
            }
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
            "for410": rel(FOR410_ARTIFACT),
            "for412": rel(FOR412_ARTIFACT),
            "for413": rel(FOR413_ARTIFACT),
        },
        "adapter": for405.get("adapter"),
        "producer": "scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py",
        "producerMethod": (
            "derived correlation over existing FOR-405 per-draw runtime readback, FOR-410 predraw "
            "boundaries, FOR-412 shader returns, and FOR-413 transition classifications"
        ),
        "runtimeOwner": "existing FOR-405 opt-in WebGPU post-pass readback",
        "decision": (
            "The existing FOR-405 runtime readback already samples the RGBA16Float intermediate after each "
            "selected StencilCoverAaPolygonDraw render pass and before the next draw starts. FOR-414 reuses "
            "that opt-in capture to classify the immediate post-draw state for draws 1, 3, and 5."
        ),
        "classification": "post-draw-matches-next-predraw",
        "globalClassification": "post-draw-matches-next-predraw",
        "allowedClassifications": sorted(ALLOWED_CLASSIFICATIONS),
        "supportClaim": False,
        "promoted": False,
        "correctionAppliedByDefault": False,
        "defaultRenderingChanged": False,
        "thresholdChanged": False,
        "scoringChanged": False,
        "guards": {
            "postDrawRuntimeSource": {
                "propertyName": "kanvas.webgpu.m60F16DirectPassWriteHook.enabled",
                "enabledByDefault": False,
                "defaultRenderingChanged": False,
                "notes": "FOR-414 consumes the existing FOR-405 opt-in readback instead of adding a duplicate runtime hook.",
            },
            "requestedFor414RuntimeGuard": {
                "propertyName": "kanvas.webgpu.m60F16AaStencilCoverPostDrawReadback.enabled",
                "implementedAsNewRuntimeGuard": False,
                "reason": (
                    "No new guard is needed because FOR-405 already captures the requested boundary: "
                    "after the StencilCoverAaPolygonDraw render pass ends and before subsequent commands."
                ),
            },
        },
        "runtimeSnapshot": {
            "source": "FOR-405 runtimeReadback",
            "api": post_runtime.get("api"),
            "propertyName": post_runtime.get("propertyName"),
            "enabledByDefault": False,
            "eventDrawOrder": [event.get("drawIndex") for event in post_events],
            "eventCount": len(post_events),
            "postDrawReadbackAvailable": all(
                sample.get("readbackAvailable") is True
                for event in post_events
                for sample in event.get("samples", [])
            ),
            "observedBoundary": (
                "compute textureLoad from the RGBA16Float intermediate after each "
                "StencilCoverAaPolygonDraw render pass and before the following draw"
            ),
        },
        "scope": {
            "selectedPixelCount": len(selected_pixels),
            "drawsInspected": [1, 3, 5],
            "postDrawRecordCount": sum(classification_counter.values()),
            "pipelineFamily": "StencilCoverAaPolygonDraw",
            "blendMode": "kSrcOver",
            "generalizedOutsideM60F16": False,
        },
        "comparisonPolicy": {
            "tolerance": MATCH_TOLERANCE,
            "postDrawMatchesNextPredraw": "max absolute RGBA channel delta <= tolerance",
            "postDrawStillBefore": "max absolute RGBA channel delta <= tolerance",
            "noSyntheticShaderReturn": True,
            "for400UsedAsDirectProof": False,
            "missingShaderReturnTreatedAsZero": False,
        },
        "postDrawSummary": {
            "byClassification": dict(sorted(classification_counter.items())),
            "byDraw": {str(draw): dict(sorted(counter.items())) for draw, counter in sorted(per_draw_counter.items())},
            "zeroReturnMutationPostDrawClassifications": dict(sorted(zero_return_mutation_counter.items())),
            "zeroReturnMutationPostDrawMatchesNextBoundaryCount": zero_return_mutation_counter.get(
                "post-draw-matches-next-predraw",
                0,
            ),
            "postDrawReadbackUnavailableCount": classification_counter.get("post-draw-readback-unavailable", 0),
        },
        "selectedPixels": selected_pixels,
        "nonGoalsPreserved": [
            "No rendering correction is applied.",
            "No default rendering behavior changes.",
            "No support or promotion claim is made for M60 F16.",
            "No score, threshold, or scene promotion policy changes.",
            "FOR-400 is not used as direct write proof.",
            "Unavailable FOR-412 shader returns are not converted into synthetic zero sources.",
        ],
        "classificationReason": (
            "For all 16 FOR-413 zero-return mutating transitions, the immediate FOR-405 post-draw readback "
            "already matches the next FOR-410/FOR-405 boundary. One additional draw-1 transition with "
            "unavailable shader returns remains `post-draw-still-before` and is not treated as zero-return evidence."
        ),
        "nextStep": (
            "Inspect the blend/render-pass/store path inside the mutating draw, especially draw 1 and draw 3, "
            "because the texture state is already changed immediately after those draw render passes."
        ),
        "validationCommands": EXPECTED_VALIDATION_COMMANDS,
    }


def write_report(data: dict[str, Any]) -> None:
    summary = data["postDrawSummary"]
    report = f"""# FOR-414 M60 F16 AA stencil-cover post-draw readback

Date: 2026-06-05

## Result

Global classification: `{data["globalClassification"]}`.

FOR-414 reuses the existing FOR-405 opt-in runtime readback because it already captures the requested boundary: after each M60 F16 `StencilCoverAaPolygonDraw` render pass and before the following draw. No new rendering code, default behavior, threshold, scoring, or promotion state changes.

## Evidence

- Source draft memory: `{SOURCE_DRAFT_MEMORY}`
- Source finding: `{SOURCE_FINDING}`
- FOR-401 artifact: `{rel(FOR401_ARTIFACT)}`
- FOR-405 artifact: `{rel(FOR405_ARTIFACT)}`
- FOR-410 artifact: `{rel(FOR410_ARTIFACT)}`
- FOR-412 artifact: `{rel(FOR412_ARTIFACT)}`
- FOR-413 artifact: `{rel(FOR413_ARTIFACT)}`

## Runtime boundary

`SkWebGpuDevice.encodePendingDrawsToIntermediate` encodes each M60 F16 AA stencil-cover draw in its own render pass. The FOR-405 readback is encoded after that pass ends and before the loop continues to the next pending draw. That is the immediate post-draw texture boundary requested by FOR-414, so this ticket adds a dedicated correlation artifact instead of a duplicate runtime hook.

## Summary

- Selected pixels: {data["scope"]["selectedPixelCount"]}
- Post-draw records: {data["scope"]["postDrawRecordCount"]}
- Draws inspected: {data["scope"]["drawsInspected"]}
- Classification counts: {summary["byClassification"]}
- Per-draw classification counts: {summary["byDraw"]}
- Zero-return mutating transitions matching the next boundary: {summary["zeroReturnMutationPostDrawMatchesNextBoundaryCount"]}
- Post-draw unavailable records: {summary["postDrawReadbackUnavailableCount"]}

## Interpretation

For every FOR-413 transition classified `draw-mutates-despite-zero-shader-return`, the immediate post-draw readback is already equal to the next FOR-410/FOR-405 boundary under `{MATCH_TOLERANCE}` tolerance. This localizes the mutation to the draw render pass itself, below the shader-return boundary captured by FOR-412.

One draw-1 transition is classified `post-draw-still-before`; its FOR-413 transition had unavailable shader returns, so FOR-414 keeps it outside the zero-return proof and does not synthesize a zero source.

## Next decision

The next ticket should inspect the blend/render-pass/store path inside draw 1 and draw 3 rather than looking later between draw boundaries. The texture state has already changed immediately after the mutating draw render pass.

## Validation

{chr(10).join(f"- `{command}`" for command in EXPECTED_VALIDATION_COMMANDS)}
"""
    REPORT.write_text(report, encoding="utf-8")


def write_artifacts(data: dict[str, Any]) -> None:
    ARTIFACT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    write_report(data)


def validate_source_artifacts(sources: dict[str, Any]) -> None:
    expected = {
        "for401": (FOR401_ARTIFACT, "FOR-401", "residual-visible-only-at-final-readback"),
        "for405": (FOR405_ARTIFACT, "FOR-405", "aa-stencil-cover-post-pass-color-observed"),
        "for410": (FOR410_ARTIFACT, "FOR-410", "predraw-dst-captured"),
        "for412": (FOR412_ARTIFACT, "FOR-412", "shader-return-zero-but-post-pass-colored"),
        "for413": (FOR413_ARTIFACT, "FOR-413", "draw-mutates-despite-zero-shader-return"),
    }
    for key, (path, linear, classification) in expected.items():
        require(sources.get(key) == rel(path), f"{linear} artifact link changed")
        source = load_json(path)
        require(source.get("linear") == linear, f"{linear} source Linear id changed")
        require(source.get("classification") == classification, f"{linear} source classification changed")


def validate_delta_object(value: Any, field: str) -> None:
    require(isinstance(value, dict), f"{field} must be a delta object")
    require(isinstance(value.get("withinTolerance"), bool), f"{field}.withinTolerance missing")
    require(value.get("tolerance") == MATCH_TOLERANCE, f"{field}.tolerance changed")
    require_rgba(value.get("signedRgbaFloat"), f"{field}.signedRgbaFloat")
    require_rgba(value.get("absoluteRgbaFloat"), f"{field}.absoluteRgbaFloat")
    require(isinstance(value.get("maxChannelFloat"), (float, int)), f"{field}.maxChannelFloat missing")


def validate_record(record: dict[str, Any]) -> None:
    classification = record.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"unknown classification {classification}")
    before = require_rgba(record.get("beforeRgbaFloat"), "beforeRgbaFloat")
    next_boundary = require_rgba(record.get("nextBoundaryRgbaFloat"), "nextBoundaryRgbaFloat")
    post_draw = record.get("postDrawRgbaFloat")

    state = record.get("shaderReturnState")
    require(isinstance(state, dict), "shaderReturnState missing")
    require(state.get("unavailableTreatedAsSyntheticZero") is False, "shader unavailable converted to zero")

    if classification == "post-draw-readback-unavailable":
        require(post_draw is None, "unavailable post-draw record must not carry postDrawRgbaFloat")
        require(bool(record.get("classificationReason")), "unavailable post-draw record lacks stable reason")
        return

    post = require_rgba(post_draw, "postDrawRgbaFloat")
    validate_delta_object(record.get("postDrawVsBeforeDelta"), "postDrawVsBeforeDelta")
    validate_delta_object(record.get("postDrawVsNextBoundaryDelta"), "postDrawVsNextBoundaryDelta")
    matches_next = max_abs_delta(post, next_boundary) <= MATCH_TOLERANCE
    still_before = max_abs_delta(post, before) <= MATCH_TOLERANCE

    if classification == "post-draw-matches-next-predraw":
        require(matches_next, "post-draw-matches-next-predraw asserted without strict tolerance")
    elif classification == "post-draw-still-before":
        require(still_before, "post-draw-still-before asserted without strict tolerance")
    elif classification == "post-draw-mutates-differently":
        require(not matches_next and not still_before, "post-draw-mutates-differently asserted despite a strict match")


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "Linear id changed")
    require(data.get("sourceDraftMemory") == SOURCE_DRAFT_MEMORY, "source draft memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    validate_source_artifacts(data.get("sourceArtifacts", {}))
    require(data.get("classification") == "post-draw-matches-next-predraw", "global classification changed")
    require(data.get("supportClaim") is False, "supportClaim must remain false")
    require(data.get("promoted") is False, "promoted must remain false")
    require(data.get("correctionAppliedByDefault") is False, "correctionAppliedByDefault must remain false")
    require(data.get("defaultRenderingChanged") is False, "defaultRenderingChanged must remain false")
    require(data.get("thresholdChanged") is False, "thresholdChanged must remain false")
    require(data.get("scoringChanged") is False, "scoringChanged must remain false")
    require(data.get("comparisonPolicy", {}).get("for400UsedAsDirectProof") is False, "FOR-400 used as direct proof")
    require(
        data.get("comparisonPolicy", {}).get("missingShaderReturnTreatedAsZero") is False,
        "missing shader return treated as zero",
    )

    selected = data.get("selectedPixels")
    require(isinstance(selected, list) and len(selected) == 16, "selected pixel count must be 16")
    require([pixel_key(pixel) for pixel in selected] == EXPECTED_POINTS, "selected pixel order changed")

    records: list[dict[str, Any]] = []
    for pixel in selected:
        pixel_records = pixel.get("postDrawRecords")
        require(isinstance(pixel_records, list) and len(pixel_records) == 3, "each pixel must inspect draws 1, 3, 5")
        require([record.get("drawIndex") for record in pixel_records] == [1, 3, 5], "draw inspection order changed")
        records.extend(pixel_records)
        for record in pixel_records:
            validate_record(record)

    require(len(records) == 48, "post-draw record count must be 48")
    summary = data.get("postDrawSummary", {})
    require(summary.get("postDrawReadbackUnavailableCount") == 0, "unexpected post-draw readback unavailability")
    require(
        summary.get("zeroReturnMutationPostDrawMatchesNextBoundaryCount") == 16,
        "zero-return mutating transitions must all match the immediate post-draw boundary",
    )
    require(
        summary.get("zeroReturnMutationPostDrawClassifications") == {"post-draw-matches-next-predraw": 16},
        "zero-return mutating transitions must not contain any other post-draw classification",
    )
    require(
        summary.get("byClassification", {}).get("post-draw-matches-next-predraw") == 47,
        "expected 47 post-draw matches to next boundary",
    )
    require(
        summary.get("byClassification", {}).get("post-draw-still-before") == 1,
        "expected one post-draw-still-before non-zero-return record",
    )


def main() -> None:
    data = build_artifact()
    write_artifacts(data)
    validate_artifact(load_json(ARTIFACT))
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    print("FOR-414 validation passed: post-draw-matches-next-predraw")


if __name__ == "__main__":
    main()
