#!/usr/bin/env python3
"""Generate and validate FOR-413 M60 F16 draw-transition correlation evidence."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-413"
SCENE_ID = "m60-f16-aa-stencil-cover-draw-transition-correlation-for413"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
SOURCE_DRAFT_MEMORY = (
    "global/kanvas/tickets/drafts/"
    "brouillon-ticket-for-413-m60-f16-correler-les-transitions-de-draw-aa-stencil-cover-avec-le-retour-shader"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-412-capture-la-source-shader-retourne-au-blend-et-classe-les-16-pixels-comme-zero-shader-return-post-pass-colore-1"
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
    / "2026-06-05-for-413-m60-f16-aa-stencil-cover-draw-transition-correlation.md"
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

MATCH_TOLERANCE = 1e-6
DRAW_TRANSITIONS = (
    (1, 3, "FOR-410 drawIndex 1 predraw compute readback", "FOR-410 drawIndex 3 predraw compute readback"),
    (3, 5, "FOR-410 drawIndex 3 predraw compute readback", "FOR-410 drawIndex 5 predraw compute readback"),
    (5, None, "FOR-410 drawIndex 5 predraw compute readback", "FOR-405 post-pass collapsed sample"),
)
ALLOWED_CLASSIFICATIONS = {
    "draw-mutates-despite-zero-shader-return",
    "draw-unchanged-after-zero-shader-return",
    "draw-change-explained-by-shader-return",
    "draw-change-unattributed-shader-unavailable",
    "draw-boundary-unavailable",
}
PRIORITY = [
    "draw-mutates-despite-zero-shader-return",
    "draw-change-unattributed-shader-unavailable",
    "draw-change-explained-by-shader-return",
    "draw-unchanged-after-zero-shader-return",
    "draw-boundary-unavailable",
]
STRONG_CLASSIFICATIONS = {
    "draw-mutates-despite-zero-shader-return",
    "draw-unchanged-after-zero-shader-return",
    "draw-change-explained-by-shader-return",
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
    "rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py",
    "rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py",
    "rtk python3 scripts/validate_for410_m60_f16_aa_stencil_cover_predraw_dst_readback.py",
    "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for413-pycache python3 -m py_compile "
        "scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py"
    ),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-413 validation failed: {message}")


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


def source_over_premul(src: list[float], dst: list[float]) -> list[float]:
    inv_alpha = 1.0 - src[3]
    return [
        src[0] + dst[0] * inv_alpha,
        src[1] + dst[1] * inv_alpha,
        src[2] + dst[2] * inv_alpha,
        src[3] + dst[3] * inv_alpha,
    ]


def all_zero(values: list[list[float]]) -> bool:
    return all(max(abs(channel) for channel in value) <= MATCH_TOLERANCE for value in values)


def compact_shader_subdraw(subdraw: dict[str, Any]) -> dict[str, Any]:
    source = subdraw.get("sourceColorSentToBlend")
    return {
        "drawIndex": subdraw.get("drawIndex"),
        "subdrawOrdinal": subdraw.get("subdrawOrdinal"),
        "subdrawRole": subdraw.get("subdrawRole"),
        "pipelineFamily": subdraw.get("pipelineFamily"),
        "blendMode": subdraw.get("blendMode"),
        "shaderObserved": subdraw.get("shaderObserved") is True,
        "captureSynthetic": subdraw.get("captureSynthetic") is True,
        "sourceColorSentToBlend": source,
        "classification": subdraw.get("classification"),
    }


def classify_transition(
    before: list[float] | None,
    after: list[float] | None,
    observed_sources: list[list[float]],
    unavailable_count: int,
    replay_delta: dict[str, Any] | None,
) -> tuple[str, str]:
    if before is None or after is None:
        return "draw-boundary-unavailable", "The before or after draw boundary is missing."

    mutated = max_abs_delta(before, after) > MATCH_TOLERANCE
    observed_zero = bool(observed_sources) and all_zero(observed_sources)
    all_shader_returns_available = bool(observed_sources) and unavailable_count == 0

    if all_shader_returns_available and observed_zero and mutated:
        return (
            "draw-mutates-despite-zero-shader-return",
            "The draw changes the intermediate texture while every real FOR-412 shader return for this draw is zero.",
        )
    if all_shader_returns_available and observed_zero and not mutated:
        return (
            "draw-unchanged-after-zero-shader-return",
            "The draw leaves the intermediate texture unchanged and every real FOR-412 shader return for this draw is zero.",
        )
    if observed_sources and replay_delta is not None and replay_delta["withinTolerance"]:
        return (
            "draw-change-explained-by-shader-return",
            "Premultiplied source-over replay with the available real shader returns reproduces the after boundary.",
        )
    if mutated and unavailable_count > 0:
        return (
            "draw-change-unattributed-shader-unavailable",
            "The draw changes the intermediate texture but at least one shader return for this draw is unavailable.",
        )
    if unavailable_count > 0:
        return (
            "draw-boundary-unavailable",
            "The before/after state boundaries are present, but the shader-return boundary for this draw is unavailable; no synthetic zero is used.",
        )
    return (
        "draw-change-unattributed-shader-unavailable",
        "The draw transition is not explained by the available shader returns.",
    )


def highest_priority(classifications: list[str]) -> str:
    for candidate in PRIORITY:
        if candidate in classifications:
            return candidate
    fail("no transition classifications available")


def build_transition(
    draw_index: int,
    after_draw_index: int | None,
    before_source: str,
    after_source: str,
    predraw_by_index: dict[int, dict[str, Any]],
    post_pass: dict[str, Any],
    shader_subdraws: list[dict[str, Any]],
) -> dict[str, Any]:
    before_sample = predraw_by_index.get(draw_index)
    after_sample = post_pass if after_draw_index is None else predraw_by_index.get(after_draw_index)
    before = before_sample.get("dstBeforeRgbaFloat") if before_sample is not None else None
    after = after_sample.get("observedRgbaFloat") if after_draw_index is None and after_sample is not None else (
        after_sample.get("dstBeforeRgbaFloat") if after_sample is not None else None
    )
    before_rgba = require_rgba(before, f"draw {draw_index} before") if before is not None else None
    after_rgba = require_rgba(after, f"draw {draw_index} after") if after is not None else None

    subdraws = [compact_shader_subdraw(subdraw) for subdraw in shader_subdraws if subdraw.get("drawIndex") == draw_index]
    real_sources = [
        require_rgba(subdraw["sourceColorSentToBlend"], f"draw {draw_index} sourceColorSentToBlend")
        for subdraw in subdraws
        if subdraw.get("shaderObserved") is True
        and subdraw.get("captureSynthetic") is False
        and subdraw.get("sourceColorSentToBlend") is not None
    ]
    unavailable_count = sum(1 for subdraw in subdraws if subdraw.get("shaderObserved") is not True)
    synthetic_count = sum(1 for subdraw in subdraws if subdraw.get("captureSynthetic") is True)

    replayed = before_rgba[:] if before_rgba is not None else None
    if replayed is not None:
        for source in real_sources:
            replayed = source_over_premul(source, replayed)
    replay_delta = delta(replayed, after_rgba) if replayed is not None and after_rgba is not None else None
    transition_delta = delta(before_rgba, after_rgba) if before_rgba is not None and after_rgba is not None else None
    classification, reason = classify_transition(before_rgba, after_rgba, real_sources, unavailable_count, replay_delta)

    return {
        "drawIndex": draw_index,
        "transitionId": f"{draw_index}->postPass" if after_draw_index is None else f"{draw_index}->{after_draw_index}",
        "beforeSource": before_source if before_rgba is not None else None,
        "afterSource": after_source if after_rgba is not None else None,
        "beforeRgbaFloat": before_rgba,
        "afterRgbaFloat": after_rgba,
        "delta": transition_delta,
        "withinTolerance": transition_delta["withinTolerance"] if transition_delta else False,
        "shaderReturnSubdraws": subdraws,
        "shaderReturnState": {
            "subdrawCount": len(subdraws),
            "realObservedCount": len(real_sources),
            "unavailableCount": unavailable_count,
            "syntheticCount": synthetic_count,
            "allAvailableNonSynthetic": bool(real_sources) and unavailable_count == 0 and synthetic_count == 0,
            "allObservedSourcesZero": bool(real_sources) and all_zero(real_sources),
            "unavailableTreatedAsSyntheticZero": False,
        },
        "sourceOverReplay": {
            "inputSourceField": "sourceColorSentToBlend",
            "inputsUsedCount": len(real_sources),
            "sourceUnavailableCount": unavailable_count,
            "skippedUnavailableAsSyntheticZero": False,
            "startRgbaFloat": before_rgba,
            "replayedRgbaFloat": replayed,
            "replayVsAfterDelta": replay_delta,
            "withinTolerance": replay_delta["withinTolerance"] if replay_delta else False,
        },
        "classification": classification,
        "classificationReason": reason,
    }


def build_artifact() -> dict[str, Any]:
    for401 = load_json(FOR401_ARTIFACT)
    for405 = load_json(FOR405_ARTIFACT)
    for410 = load_json(FOR410_ARTIFACT)
    for412 = load_json(FOR412_ARTIFACT)

    require(for401.get("classification") == "residual-visible-only-at-final-readback", "FOR-401 classification changed")
    require(for405.get("classification") == "aa-stencil-cover-post-pass-color-observed", "FOR-405 classification changed")
    require(for410.get("classification") == "predraw-dst-captured", "FOR-410 classification changed")
    require(for412.get("classification") == "shader-return-zero-but-post-pass-colored", "FOR-412 classification changed")

    selected_points = [pixel_key(pixel) for pixel in for401["selectedPixels"]]
    require(selected_points == EXPECTED_POINTS, "FOR-401 selected pixel order changed")
    post_by_point = {pixel_key(pixel): pixel["postPass"] for pixel in for405["selectedPixels"]}
    predraw_by_point = {pixel_key(pixel): pixel for pixel in for410["selectedPixels"]}
    shader_by_point = {pixel_key(pixel): pixel for pixel in for412["selectedPixels"]}

    selected_pixels: list[dict[str, Any]] = []
    transition_counter: Counter[str] = Counter()
    mutates_despite_zero_draws: Counter[int] = Counter()
    state_change_draws: Counter[int] = Counter()
    first_state_change_draws: Counter[int] = Counter()
    shader_unavailable_draws: Counter[int] = Counter()

    for x, y in selected_points:
        predraw_pixel = predraw_by_point[(x, y)]
        shader_pixel = shader_by_point[(x, y)]
        predraw_samples = {sample["drawIndex"]: sample for sample in predraw_pixel["predrawSamples"]}
        shader_subdraws = shader_pixel["shaderReturnSubdraws"]
        transitions = [
            build_transition(draw, after_draw, before_source, after_source, predraw_samples, post_by_point[(x, y)], shader_subdraws)
            for draw, after_draw, before_source, after_source in DRAW_TRANSITIONS
        ]
        classifications = [transition["classification"] for transition in transitions]
        pixel_classification = highest_priority(classifications)
        transition_counter.update(classifications)
        first_state_change: int | None = None
        for transition in transitions:
            if transition["classification"] == "draw-mutates-despite-zero-shader-return":
                mutates_despite_zero_draws[transition["drawIndex"]] += 1
            transition_delta = transition.get("delta")
            if isinstance(transition_delta, dict) and not transition_delta.get("withinTolerance", True):
                state_change_draws[transition["drawIndex"]] += 1
                if first_state_change is None:
                    first_state_change = transition["drawIndex"]
            if transition["shaderReturnState"]["unavailableCount"] > 0:
                shader_unavailable_draws[transition["drawIndex"]] += 1
        if first_state_change is not None:
            first_state_change_draws[first_state_change] += 1

        strongest_transition = next(
            transition for transition in transitions if transition["classification"] == pixel_classification
        )

        selected_pixels.append(
            {
                "x": x,
                "y": y,
                "classification": pixel_classification,
                "transitions": transitions,
                "classificationReason": (
                    f"Draw {strongest_transition['drawIndex']} has the highest-priority transition class "
                    f"`{pixel_classification}` for this pixel."
                    if pixel_classification == "draw-mutates-despite-zero-shader-return"
                    else f"The highest-priority transition class for this pixel is `{pixel_classification}`."
                ),
            }
        )

    global_classification = highest_priority([pixel["classification"] for pixel in selected_pixels])
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
        },
        "adapter": for412.get("adapter"),
        "producer": "scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py",
        "producerMethod": "derived Python correlation over existing FOR-401/FOR-405/FOR-410/FOR-412 artifacts",
        "runtimeOwner": "none; derived evidence only",
        "decision": (
            "FOR-413 localizes AA stencil-cover intermediate-texture mutations to draw 1 and draw 3. "
            "Across the 16 FOR-401 pixels, every pixel has a draw transition that mutates despite real zero "
            "shader returns captured by FOR-412."
        ),
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
            "derivedOnly": {
                "enabledByDefault": False,
                "defaultRenderingChanged": False,
                "notes": "FOR-413 consumes checked-in artifacts and adds no runtime guard or rendering code.",
            }
        },
        "scope": {
            "selectedPixelCount": len(selected_pixels),
            "transitionCount": sum(transition_counter.values()),
            "pipelineFamily": "StencilCoverAaPolygonDraw",
            "blendMode": "kSrcOver",
            "drawTransitions": [
                {"drawIndex": draw, "afterDrawIndex": after_draw, "afterSource": after_source}
                for draw, after_draw, _before_source, after_source in DRAW_TRANSITIONS
            ],
            "generalizedOutsideM60F16": False,
        },
        "comparisonPolicy": {
            "tolerance": MATCH_TOLERANCE,
            "sourceOverReplay": "premultiplied kSrcOver applied in subdraw order",
            "replaySourceField": "FOR-412 sourceColorSentToBlend",
            "noSyntheticShaderReturn": True,
            "for400UsedAsDirectProof": False,
            "missingShaderReturnTreatedAsZero": False,
        },
        "transitionSummary": {
            "byClassification": dict(sorted(transition_counter.items())),
            "mutatesDespiteZeroShaderReturnDrawCounts": dict(sorted(mutates_despite_zero_draws.items())),
            "stateChangeDrawCounts": dict(sorted(state_change_draws.items())),
            "firstStateChangeDrawCounts": dict(sorted(first_state_change_draws.items())),
            "shaderUnavailableDrawCounts": dict(sorted(shader_unavailable_draws.items())),
            "draw1MutatesDespiteZeroShaderReturnPixelCount": mutates_despite_zero_draws.get(1, 0),
            "draw3MutatesDespiteZeroShaderReturnPixelCount": mutates_despite_zero_draws.get(3, 0),
            "mutatesDespiteZeroShaderReturnPixelCount": sum(mutates_despite_zero_draws.values()),
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
            "All 16 FOR-401 pixels have at least one draw transition where the FOR-410/FOR-405 boundary mutates "
            "while the real FOR-412 sourceColorSentToBlend values observed for that draw are zero."
        ),
        "nextStep": (
            "Instrument the WebGPU blend/render-pass/store boundary around the mutating draw transitions, with "
            "draw 1 and draw 3 separated, because FOR-413 shows intermediate texture mutations despite zero "
            "observed shader returns."
        ),
        "validationCommands": EXPECTED_VALIDATION_COMMANDS,
    }


def write_report(data: dict[str, Any]) -> None:
    summary = data["transitionSummary"]
    report = f"""# FOR-413 M60 F16 AA stencil-cover draw-transition correlation

Date: 2026-06-05

## Result

Global classification: `{data["globalClassification"]}`.

FOR-413 correlates existing FOR-401, FOR-405, FOR-410, and FOR-412 artifacts only. It does not change rendering code, default behavior, thresholds, scoring, or promotion state.

## Evidence

- Source draft memory: `{SOURCE_DRAFT_MEMORY}`
- Source finding: `{SOURCE_FINDING}`
- FOR-401 artifact: `{rel(FOR401_ARTIFACT)}`
- FOR-405 artifact: `{rel(FOR405_ARTIFACT)}`
- FOR-410 artifact: `{rel(FOR410_ARTIFACT)}`
- FOR-412 artifact: `{rel(FOR412_ARTIFACT)}`

For each of the 16 FOR-401 pixels, the derived transitions are:

- draw 1 before = FOR-410 predraw draw 1; after = FOR-410 predraw draw 3
- draw 3 before = FOR-410 predraw draw 3; after = FOR-410 predraw draw 5
- draw 5 before = FOR-410 predraw draw 5; after = FOR-405 post-pass sample

## Summary

- Total transitions: {data["scope"]["transitionCount"]}
- `draw-mutates-despite-zero-shader-return`: {summary["byClassification"].get("draw-mutates-despite-zero-shader-return", 0)}
- `draw-unchanged-after-zero-shader-return`: {summary["byClassification"].get("draw-unchanged-after-zero-shader-return", 0)}
- `draw-change-unattributed-shader-unavailable`: {summary["byClassification"].get("draw-change-unattributed-shader-unavailable", 0)}
- `draw-boundary-unavailable`: {summary["byClassification"].get("draw-boundary-unavailable", 0)}
- Draw 1 mutates despite zero shader return for {summary["draw1MutatesDespiteZeroShaderReturnPixelCount"]} pixels.
- Draw 3 mutates despite zero shader return for {summary["draw3MutatesDespiteZeroShaderReturnPixelCount"]} pixels.
- First state-change draw counts: {summary["firstStateChangeDrawCounts"]}.

## Interpretation

The strongest classification is distributed across draw 1 and draw 3. FOR-410 provides the before/after boundaries between draw 1, draw 3, draw 5, and the FOR-405 post-pass sample. FOR-412 shows the real non-synthetic `sourceColorSentToBlend` values observed for the mutating zero-return transitions are zero, and the premultiplied source-over replay over the before state does not reproduce the after state.

Six pixels mutate on draw 1 despite zero shader return. Ten pixels mutate on draw 3 despite zero shader return. One additional draw 1 transition mutates while shader returns are unavailable, so it is kept as `draw-change-unattributed-shader-unavailable` and not converted into a zero-source claim. Draw 5 has state boundaries available and remains stable, but FOR-412 does not observe shader returns for draw 5, so the artifact does not fabricate zero sources for it.

## Next decision

Open the next diagnostic below the shader-return boundary for the mutating draw transitions, separating draw 1 and draw 3. Inspect WebGPU blend, render-pass, attachment store/load, or the immediate post-draw texture state around those draws. The current artifacts are sufficient to say the color can appear between predraw boundaries despite zero observed shader returns.

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
    }
    for key, (path, linear, classification) in expected.items():
        require(sources.get(key) == rel(path), f"{linear} artifact link changed")
        source = load_json(path)
        require(source.get("linear") == linear, f"{linear} source Linear id changed")
        require(source.get("classification") == classification, f"{linear} source classification changed")


def validate_report(classification: str) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "FOR-413",
        classification,
        SOURCE_DRAFT_MEMORY,
        SOURCE_FINDING,
        rel(FOR401_ARTIFACT),
        rel(FOR405_ARTIFACT),
        rel(FOR410_ARTIFACT),
        rel(FOR412_ARTIFACT),
        "draw 1",
        "zero observed shader return",
    ):
        require(needle in text, f"report missing: {needle}")


def validate_transition(transition: dict[str, Any]) -> None:
    classification = transition.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"unexpected transition classification: {classification}")

    before = transition.get("beforeRgbaFloat")
    after = transition.get("afterRgbaFloat")
    if before is not None:
        before = require_rgba(before, "beforeRgbaFloat")
    if after is not None:
        after = require_rgba(after, "afterRgbaFloat")
    if classification in STRONG_CLASSIFICATIONS:
        require(before is not None and after is not None, f"{classification} requires real before/after boundaries")
        require(transition.get("beforeSource") is not None, f"{classification} missing beforeSource")
        require(transition.get("afterSource") is not None, f"{classification} missing afterSource")

    if before is not None and after is not None:
        expected_delta = delta(before, after)
        require(transition.get("delta") == expected_delta, "transition delta is not derived from before/after")
        require(transition.get("withinTolerance") == expected_delta["withinTolerance"], "withinTolerance mismatch")

    shader_state = transition.get("shaderReturnState")
    require(isinstance(shader_state, dict), "shaderReturnState missing")
    require(shader_state.get("unavailableTreatedAsSyntheticZero") is False, "unavailable shader returns treated as zero")
    require(shader_state.get("syntheticCount") == 0, "synthetic shader return leaked into FOR-413")

    subdraws = transition.get("shaderReturnSubdraws")
    require(isinstance(subdraws, list), "shaderReturnSubdraws missing")
    real_sources = [
        require_rgba(subdraw.get("sourceColorSentToBlend"), "sourceColorSentToBlend")
        for subdraw in subdraws
        if subdraw.get("shaderObserved") is True and subdraw.get("captureSynthetic") is False
    ]
    unavailable = [subdraw for subdraw in subdraws if subdraw.get("shaderObserved") is not True]
    for subdraw in unavailable:
        require(subdraw.get("sourceColorSentToBlend") is None, "unavailable shader return must not carry a source")

    replay = transition.get("sourceOverReplay")
    require(isinstance(replay, dict), "sourceOverReplay missing")
    require(replay.get("inputSourceField") == "sourceColorSentToBlend", "wrong replay source field")
    require(replay.get("inputsUsedCount") == len(real_sources), "replay input count mismatch")
    require(replay.get("sourceUnavailableCount") == len(unavailable), "replay unavailable count mismatch")
    require(replay.get("skippedUnavailableAsSyntheticZero") is False, "unavailable shader returns replayed as synthetic zero")
    if before is not None:
        replayed = before[:]
        for source in real_sources:
            replayed = source_over_premul(source, replayed)
        require(max_abs_delta(replayed, require_rgba(replay.get("replayedRgbaFloat"), "replayedRgbaFloat")) <= MATCH_TOLERANCE, "replay output mismatch")
    if before is not None and after is not None:
        expected_replay_delta = delta(require_rgba(replay.get("replayedRgbaFloat"), "replayedRgbaFloat"), after)
        require(replay.get("replayVsAfterDelta") == expected_replay_delta, "replay delta mismatch")
        require(replay.get("withinTolerance") == expected_replay_delta["withinTolerance"], "replay withinTolerance mismatch")

    if classification == "draw-mutates-despite-zero-shader-return":
        require(before is not None and after is not None and max_abs_delta(before, after) > MATCH_TOLERANCE, "mutation classification without numeric mutation")
        require(bool(real_sources) and all_zero(real_sources), "mutation classification without zero real shader returns")
        require(len(unavailable) == 0, "mutation despite zero cannot include unavailable shader returns")
    elif classification == "draw-unchanged-after-zero-shader-return":
        require(before is not None and after is not None and max_abs_delta(before, after) <= MATCH_TOLERANCE, "unchanged classification with mutation")
        require(bool(real_sources) and all_zero(real_sources), "unchanged zero classification without zero real shader returns")
        require(len(unavailable) == 0, "unchanged zero cannot include unavailable shader returns")
    elif classification == "draw-change-explained-by-shader-return":
        require(replay.get("withinTolerance") is True, "explained classification without replay match")
    elif classification == "draw-change-unattributed-shader-unavailable":
        require(before is not None and after is not None and max_abs_delta(before, after) > MATCH_TOLERANCE, "unattributed unavailable classification without mutation")
        require(len(unavailable) > 0, "unattributed unavailable classification without unavailable shader return")


def validate_artifact(data: dict[str, Any]) -> str:
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("sourceSceneId") == ROW_ID, "wrong source scene")
    require(data.get("sourceDraftMemory") == SOURCE_DRAFT_MEMORY, "wrong source draft memory")
    require(data.get("sourceFinding") == SOURCE_FINDING, "wrong source finding")

    sources = data.get("sourceArtifacts")
    require(isinstance(sources, dict), "sourceArtifacts missing")
    validate_source_artifacts(sources)

    classification = data.get("globalClassification")
    require(classification == data.get("classification"), "classification/globalClassification mismatch")
    require(classification in ALLOWED_CLASSIFICATIONS, "unexpected global classification")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "classification taxonomy changed")
    require(classification == "draw-mutates-despite-zero-shader-return", "global classification changed")

    for key in (
        "supportClaim",
        "promoted",
        "correctionAppliedByDefault",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
    ):
        require(data.get(key) is False, f"{key} must remain false")

    policy = data.get("comparisonPolicy")
    require(isinstance(policy, dict), "comparisonPolicy missing")
    require(policy.get("tolerance") == MATCH_TOLERANCE, "tolerance changed")
    require(policy.get("noSyntheticShaderReturn") is True, "synthetic shader return must be forbidden")
    require(policy.get("missingShaderReturnTreatedAsZero") is False, "missing shader returns treated as zero")
    require(policy.get("for400UsedAsDirectProof") is False, "FOR-400 must not be direct proof")

    scope = data.get("scope")
    require(isinstance(scope, dict), "scope missing")
    require(scope.get("selectedPixelCount") == 16, "selected pixel count must be 16")
    require(scope.get("transitionCount") == 48, "transition count must be 48")
    require(scope.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "wrong pipeline family")
    require(scope.get("blendMode") == "kSrcOver", "wrong blend mode")
    require(scope.get("generalizedOutsideM60F16") is False, "scope generalized outside M60 F16")

    pixels = data.get("selectedPixels")
    require(isinstance(pixels, list) and len(pixels) == 16, "selectedPixels must contain 16 pixels")
    require([pixel_key(pixel) for pixel in pixels] == EXPECTED_POINTS, "selected pixel order changed")

    all_transition_classes: list[str] = []
    mutates_despite_zero_by_draw: Counter[int] = Counter()
    for pixel in pixels:
        transitions = pixel.get("transitions")
        require(isinstance(transitions, list) and len(transitions) == 3, "each pixel must contain three transitions")
        require([transition.get("transitionId") for transition in transitions] == ["1->3", "3->5", "5->postPass"], "transition ids changed")
        for transition in transitions:
            validate_transition(transition)
            all_transition_classes.append(transition["classification"])
            if transition["drawIndex"] == 5:
                require(transition["classification"] == "draw-boundary-unavailable", "draw 5 classification changed")
            if transition["classification"] == "draw-mutates-despite-zero-shader-return":
                mutates_despite_zero_by_draw[transition["drawIndex"]] += 1
        require(pixel.get("classification") == highest_priority([transition["classification"] for transition in transitions]), "pixel classification priority mismatch")

    require(
        dict(sorted(mutates_despite_zero_by_draw.items())) == {1: 6, 3: 10},
        "mutates-despite-zero draw distribution changed",
    )
    require(highest_priority(all_transition_classes) == classification, "global classification priority mismatch")

    summary = data.get("transitionSummary")
    require(isinstance(summary, dict), "transitionSummary missing")
    expected_counts = dict(Counter(all_transition_classes))
    require(summary.get("byClassification") == dict(sorted(expected_counts.items())), "transition summary counts mismatch")
    require(summary.get("draw1MutatesDespiteZeroShaderReturnPixelCount") == 6, "draw 1 mutation summary changed")
    require(summary.get("draw3MutatesDespiteZeroShaderReturnPixelCount") == 10, "draw 3 mutation summary changed")
    require(summary.get("mutatesDespiteZeroShaderReturnPixelCount") == 16, "zero shader return mutation summary changed")

    require(data.get("validationCommands") == EXPECTED_VALIDATION_COMMANDS, "validation commands changed")
    validate_report(classification)
    return classification


def main() -> None:
    artifact = build_artifact()
    write_artifacts(artifact)
    persisted = load_json(ARTIFACT)
    classification = validate_artifact(persisted)
    print(f"FOR-413 validation passed: {classification}")


if __name__ == "__main__":
    main()
