#!/usr/bin/env python3
"""Validate the FOR-390 M60 F16 full-scene regression discriminator audit."""

from __future__ import annotations

import json
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-390"
DECISION = "M60_F16_FULL_SCENE_REGRESSION_DISCRIMINATOR_RECORDED"
CLASSIFICATION = "narrower-metadata-defendable"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-caracteriser-les-8-regressions-full-scene-apres-for-389"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-389-montre-que-le-candidat-source-couverture-m60-f16-regresse-en-full-scene"
)
FOR389_DECISION = "M60_F16_SOURCE_COVERAGE_FULL_SCENE_CANDIDATE_EVALUATED"
FOR389_CLASSIFICATION = "full-scene-regresses"
FOR388_DECISION = "M60_F16_COMPOSITION_METADATA_AUDIT_RECORDED"
FOR388_CLASSIFICATION = "usable-correction-candidate"
SOURCE_CANDIDATE_ID = "source-color-and-oriented-coverage-lane"
BEST_CANDIDATE_ID = "source-facing-local-band-lane"
FOR389_GUARD_PROPERTY = "kanvas.webgpu.m60F16SourceCoverageFullSceneCandidate.enabled"

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-full-scene-regression-discriminator-for390"
)
ARTIFACT = ARTIFACT_DIR / "m60-f16-full-scene-regression-discriminator-for390.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-390-m60-f16-full-scene-regression-discriminator.md"
FOR389_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-coverage-full-scene-candidate-for389/"
    "m60-f16-source-coverage-full-scene-candidate-for389.json"
)
FOR388_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-composition-metadata-audit-for388/"
    "m60-f16-composition-metadata-audit-for388.json"
)
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

FORBIDDEN_SELECTION_FIELDS = {
    "referenceRgba",
    "currentRgba",
    "probeRgba",
    "currentResidual",
    "probeResidual",
    "deltaVsCurrent",
    "gainVsCurrent",
    "currentErrorByChannel",
    "probeErrorByChannel",
    "probeMinusCurrentErrorByChannel",
    "probeMinusCurrentRgba",
    "membershipCategory",
    "membershipReason",
    "for389EvaluationResult",
}
FORBIDDEN_SELECTION_NEEDLES = {
    "reference",
    "probeResidual",
    "currentResidual",
    "deltaVsCurrent",
    "for389EvaluationResult",
    "membershipCategory",
    "membershipReason",
    "truth",
}
VALIDATION_COMMANDS = [
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true "
        ":gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
    "rtk python3 scripts/validate_for390_m60_f16_full_scene_regression_discriminator.py",
    "rtk python3 scripts/validate_for389_m60_f16_source_coverage_full_scene_candidate.py",
    "rtk python3 scripts/validate_for388_m60_f16_composition_metadata_audit.py",
    "rtk python3 scripts/validate_for387_m60_f16_residual_fringe_discriminator_audit.py",
    "rtk python3 scripts/validate_for386_m60_f16_coverage_regression_discriminator_audit.py",
    "rtk python3 scripts/validate_for385_m60_f16_generalized_coverage_metadata_predicate_audit.py",
    "rtk python3 scripts/validate_for384_m60_f16_pre_correction_geometry_coverage_metadata_audit.py",
    "rtk python3 scripts/validate_for383_m60_f16_pre_probe_predicate_audit.py",
    "rtk python3 scripts/validate_for382_m60_f16_coverage_composition_membership_audit.py",
    "rtk python3 scripts/validate_for381_m60_f16_source_color_subzone_audit.py",
    "rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py",
    "rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py",
    "rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py",
    "rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py",
    "rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py",
    "rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py",
    "rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py",
    "rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py",
    "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
    "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
    "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
    "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
    "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
    "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
    "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]

Pixel = dict[str, Any]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-390 validation failed: {message}")


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


def rgba_key(pixel: Pixel) -> str:
    return "rgba(" + ",".join(str(value) for value in pixel["transparentSourceRgba"]) + ")"


def cap_join(pixel: Pixel) -> str:
    return f"{pixel['cap']}-{pixel['join']}"


def neighborhood_signature(pixel: Pixel) -> str:
    neighborhood = pixel["coverageOrthogonalNeighborhood"]
    ordered = ("north", "south", "west", "east")
    return ",".join(f"{key}={neighborhood[key]}" for key in ordered)


def band_local_x_bucket(pixel: Pixel) -> str:
    local_x = int(pixel["bandLocalX"])
    if local_x <= 8:
        return "left-terminal-0-8"
    if local_x <= 20:
        return "source-adjacent-9-20"
    if local_x < 39:
        return "middle-21-38"
    return "right-terminal-39-45"


def band_edge_distance_bucket(pixel: Pixel) -> str:
    distance = int(pixel["bandEdgeDistance"])
    if distance <= 4:
        return "edge-0-4"
    if distance <= 8:
        return "edge-5-8"
    if distance <= 17:
        return "edge-9-17"
    return "edge-18-20"


def regression_topology(pixel: Pixel) -> str:
    neighborhood = pixel["coverageOrthogonalNeighborhood"]
    if neighborhood["orthogonalPartialCount"] != 0:
        return "partial-neighbor"
    nonzero = neighborhood["orthogonalNonZeroCount"]
    return f"{nonzero}-solid-orthogonal-neighbors"


def selected_kind(pixel: Pixel) -> str:
    result = pixel.get("for389EvaluationResult")
    if result == "source-local-useful":
        return "improved"
    if result == "regressed-if-corrected":
        return "regressed"
    return "unchanged"


def residual_stats(pixels: list[Pixel]) -> dict[str, int]:
    before = sum(int(pixel["currentResidual"]) for pixel in pixels)
    after = sum(int(pixel["probeResidual"]) for pixel in pixels)
    return {
        "count": len(pixels),
        "beforeResidual": before,
        "afterResidual": after,
        "deltaVsCurrent": after - before,
        "gainVsCurrent": before - after,
    }


def candidate_class(improved: int, regressions: int, improved_truth: int) -> str:
    if improved == improved_truth and regressions == 0:
        return "narrower-metadata-defendable"
    if improved == improved_truth:
        return "narrower-metadata-still-regresses"
    return "metadata-insufficient"


@dataclass(frozen=True)
class Candidate:
    candidate_id: str
    description: str
    signal_family: str
    selection_method: str
    selection_fields: tuple[str, ...]
    select: Callable[[Pixel], bool]


def discriminator_candidates() -> list[Candidate]:
    return [
        Candidate(
            candidate_id="for389-source-color-and-oriented-coverage-lane",
            description="Baseline FOR-389 source-color plus oriented coverage lane.",
            signal_family="source-color-plus-oriented-coverage",
            selection_method=(
                "(round-round && blue source && orientedCoverageSide in "
                "{west-terminal,north-west-solid}) || "
                "(butt-bevel && blue source && orientedCoverageSide == north-east-solid "
                "&& coverageAlphaByte >= 160)"
            ),
            selection_fields=(
                "strokeBand",
                "transparentSourceColorFamily",
                "orientedCoverageSide",
                "coverageAlphaByte",
            ),
            select=lambda pixel: True,
        ),
        Candidate(
            candidate_id="alpha-96-terminal-only",
            description="Keeps the lone 96-alpha west terminal selected by FOR-389.",
            signal_family="coverage-alpha-plus-orientation",
            selection_method="coverageAlphaByte == 96 && orientedCoverageSide == west-terminal",
            selection_fields=("coverageAlphaByte", "orientedCoverageSide"),
            select=lambda pixel: pixel["coverageAlphaByte"] == 96
            and pixel["orientedCoverageSide"] == "west-terminal",
        ),
        Candidate(
            candidate_id="band-local-x-ge-17",
            description="Tests whether the useful pixels sit away from the zero-local-x terminal.",
            signal_family="band-local-position",
            selection_method="bandLocalX >= 17",
            selection_fields=("bandLocalX",),
            select=lambda pixel: int(pixel["bandLocalX"]) >= 17,
        ),
        Candidate(
            candidate_id="round-right-terminal-only",
            description="Keeps only the round/round right-terminal lane.",
            signal_family="stroke-band-plus-band-local-position",
            selection_method="strokeBand == round-round && bandLocalX >= 39",
            selection_fields=("strokeBand", "bandLocalX"),
            select=lambda pixel: pixel["strokeBand"] == "round-round" and int(pixel["bandLocalX"]) >= 39,
        ),
        Candidate(
            candidate_id="exclude-round-left-terminal",
            description="Drops the round/round left-terminal band positions that dominate regressions.",
            signal_family="stroke-band-plus-band-local-position",
            selection_method="!(strokeBand == round-round && bandLocalX <= 8)",
            selection_fields=("strokeBand", "bandLocalX"),
            select=lambda pixel: not (
                pixel["strokeBand"] == "round-round" and int(pixel["bandLocalX"]) <= 8
            ),
        ),
        Candidate(
            candidate_id=BEST_CANDIDATE_ID,
            description=(
                "Keeps the source-facing local lane: round/round right terminal "
                "plus the butt/bevel source-side corner."
            ),
            signal_family="stroke-band-plus-source-facing-band-local-position",
            selection_method=(
                "(strokeBand == round-round && bandLocalX >= 39) || "
                "(strokeBand == butt-bevel && bandLocalX <= 17)"
            ),
            selection_fields=("strokeBand", "bandLocalX"),
            select=lambda pixel: (
                pixel["strokeBand"] == "round-round" and int(pixel["bandLocalX"]) >= 39
            )
            or (pixel["strokeBand"] == "butt-bevel" and int(pixel["bandLocalX"]) <= 17),
        ),
        Candidate(
            candidate_id="source-facing-local-band-lane-with-orientation",
            description=(
                "Same local-lane split as the best candidate, with explicit oriented coverage "
                "side retained from FOR-389."
            ),
            signal_family="stroke-band-plus-position-plus-oriented-coverage",
            selection_method=(
                "(round-round && bandLocalX >= 39 && orientedCoverageSide in "
                "{west-terminal,north-west-solid}) || "
                "(butt-bevel && bandLocalX <= 17 && orientedCoverageSide == north-east-solid)"
            ),
            selection_fields=("strokeBand", "bandLocalX", "orientedCoverageSide"),
            select=lambda pixel: (
                pixel["strokeBand"] == "round-round"
                and int(pixel["bandLocalX"]) >= 39
                and pixel["orientedCoverageSide"] in {"west-terminal", "north-west-solid"}
            )
            or (
                pixel["strokeBand"] == "butt-bevel"
                and int(pixel["bandLocalX"]) <= 17
                and pixel["orientedCoverageSide"] == "north-east-solid"
            ),
        ),
    ]


def group_counts(pixels: list[Pixel], value: Callable[[Pixel], str]) -> list[dict[str, Any]]:
    counter = Counter(value(pixel) for pixel in pixels)
    return [{"value": key, "count": counter[key]} for key in sorted(counter)]


def build_regression_groups(regressions: list[Pixel]) -> list[dict[str, Any]]:
    groupers: list[tuple[str, str, Callable[[Pixel], str]]] = [
        ("transparentSourceRgba", "transparent source RGBA", rgba_key),
        ("transparentSourceColorFamily", "source color family", lambda pixel: pixel["transparentSourceColorFamily"]),
        ("orientedCoverageSide", "oriented coverage side", lambda pixel: pixel["orientedCoverageSide"]),
        ("coverageOrthogonalNeighborhood", "orthogonal coverage neighborhood", neighborhood_signature),
        ("coverageAlphaByte", "coverage alpha", lambda pixel: str(pixel["coverageAlphaByte"])),
        ("transparentSourceAlphaByte", "transparent source alpha", lambda pixel: str(pixel["transparentSourceAlphaByte"])),
        ("strokeBand", "stroke band", lambda pixel: pixel["strokeBand"]),
        ("capJoin", "cap/join", cap_join),
        ("bandLocalXBucket", "band local X bucket", band_local_x_bucket),
        ("bandEdgeDistanceBucket", "distance to local band edge", band_edge_distance_bucket),
        ("fringeTopology", "fringe topology", regression_topology),
        ("sourceCoverageRelation", "source/coverage relation", lambda pixel: pixel["sourceCoverageRelation"]),
    ]
    return [
        {
            "field": field,
            "description": description,
            "groups": group_counts(regressions, value),
        }
        for field, description, value in groupers
    ]


def build_selected_pixel_comparison(samples: list[Pixel]) -> list[dict[str, Any]]:
    return [
        {
            "x": pixel["x"],
            "y": pixel["y"],
            "kind": selected_kind(pixel),
            "strokeBand": pixel["strokeBand"],
            "cap": pixel["cap"],
            "join": pixel["join"],
            "transparentSourceRgba": pixel["transparentSourceRgba"],
            "transparentSourceColorFamily": pixel["transparentSourceColorFamily"],
            "orientedCoverageSide": pixel["orientedCoverageSide"],
            "coverageOrthogonalNeighborhood": pixel["coverageOrthogonalNeighborhood"],
            "coverageAlphaByte": pixel["coverageAlphaByte"],
            "transparentSourceAlphaByte": pixel["transparentSourceAlphaByte"],
            "bandLocalX": pixel["bandLocalX"],
            "bandLocalXBucket": band_local_x_bucket(pixel),
            "bandEdgeDistance": pixel["bandEdgeDistance"],
            "bandEdgeDistanceBucket": band_edge_distance_bucket(pixel),
            "fringeTopology": regression_topology(pixel),
            "sourceCoverageRelation": pixel["sourceCoverageRelation"],
            "evaluationOnly": {
                "currentResidual": pixel["currentResidual"],
                "probeResidual": pixel["probeResidual"],
                "deltaVsCurrent": pixel["deltaVsCurrent"],
                "for389EvaluationResult": pixel["for389EvaluationResult"],
                "membershipCategory": pixel["membershipCategory"],
            },
        }
        for pixel in samples
    ]


def evaluate_candidate(candidate: Candidate, samples: list[Pixel], full_scene_before: int) -> dict[str, Any]:
    selected = [pixel for pixel in samples if candidate.select(pixel)]
    improved = [pixel for pixel in selected if selected_kind(pixel) == "improved"]
    regressions = [pixel for pixel in selected if selected_kind(pixel) == "regressed"]
    unchanged = [pixel for pixel in selected if selected_kind(pixel) == "unchanged"]
    selected_residual = residual_stats(selected)
    estimated_after = (
        full_scene_before
        - selected_residual["beforeResidual"]
        + selected_residual["afterResidual"]
    )
    precision = 0.0 if not selected else len(improved) / len(selected)
    recall = len(improved) / 8
    return {
        "id": candidate.candidate_id,
        "description": candidate.description,
        "signalFamily": candidate.signal_family,
        "selectionMethod": candidate.selection_method,
        "scope": "FOR-389-selected-pixels",
        "refinesCandidateId": SOURCE_CANDIDATE_ID,
        "inheritedSelectionFields": [
            "strokeBand",
            "transparentSourceColorFamily",
            "orientedCoverageSide",
            "coverageAlphaByte",
        ],
        "selectionFields": list(candidate.selection_fields),
        "forbiddenSelectionFields": sorted(FORBIDDEN_SELECTION_FIELDS.intersection(candidate.selection_fields)),
        "usesSkiaReferenceForSelection": False,
        "usesProbeOutcomeForSelection": False,
        "usesProbeResidualForSelection": False,
        "usesDeltaVsCurrentForSelection": False,
        "usesCurrentResidualForSelection": False,
        "usesFor388TruthAsSelection": False,
        "usesFor389TruthAsSelection": False,
        "selectedPixels": len(selected),
        "improvedPixelsRecovered": len(improved),
        "improvedTruth": 8,
        "regressionsIncluded": len(regressions),
        "unchangedIncluded": len(unchanged),
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "candidateClass": candidate_class(len(improved), len(regressions), 8),
        "selectedResidualIfApplied": selected_residual,
        "estimatedFullSceneResidualIfAppliedToThisSubset": {
            "baseUncorrectedFullSceneResidual": full_scene_before,
            "simulatedAfterResidual": estimated_after,
            "deltaVsCurrent": estimated_after - full_scene_before,
            "gainVsCurrent": full_scene_before - estimated_after,
        },
        "selectedPixelCoordinates": [{"x": pixel["x"], "y": pixel["y"]} for pixel in selected],
    }


def validate_sources() -> tuple[dict[str, Any], dict[str, Any]]:
    for389 = load_json(FOR389_ARTIFACT)
    require(for389.get("decision") == FOR389_DECISION, "FOR-389 decision changed")
    require(for389.get("classification") == FOR389_CLASSIFICATION, "FOR-389 classification changed")
    require(for389.get("auditDoesNotApplyRendererChange") is True, "FOR-389 must remain diagnostic-only")
    require(for389.get("correctionAppliedByDefault") is False, "FOR-389 correction must remain disabled")
    candidate = for389.get("candidate")
    require(isinstance(candidate, dict), "FOR-389 candidate missing")
    require(candidate.get("id") == SOURCE_CANDIDATE_ID, "FOR-389 candidate id changed")
    require(candidate.get("selectedPixels") == 16, "FOR-389 selected count changed")
    require(candidate.get("improvedPixels") == 8, "FOR-389 improved count changed")
    require(candidate.get("regressedPixels") == 8, "FOR-389 regressed count changed")
    require(candidate.get("sourceLocalRecovered") == 8, "FOR-389 source-local recovery changed")
    require(candidate.get("precision") == 0.5, "FOR-389 precision changed")
    require(candidate.get("recall") == 1.0, "FOR-389 recall changed")

    for388 = load_json(FOR388_ARTIFACT)
    require(for388.get("decision") == FOR388_DECISION, "FOR-388 decision changed")
    require(for388.get("classification") == FOR388_CLASSIFICATION, "FOR-388 classification changed")
    best = for388.get("bestCompositionMetadataCandidate")
    require(isinstance(best, dict), "FOR-388 best candidate missing")
    require(best.get("id") == SOURCE_CANDIDATE_ID, "FOR-388 best candidate id changed")
    require(best.get("sourceLocalRecovered") == 8, "FOR-388 source-local recovery changed")
    require(best.get("regressedPixelsIncluded") == 0, "FOR-388 local regression count changed")

    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    for needle in (
        FOR389_GUARD_PROPERTY,
        '"runtimeHookInstalled": false',
        '"correctionAppliedByDefault": false',
        '"correctionPredicateEnabled": false',
    ):
        require(needle in source, f"capture source missing non-activation proof: {needle}")
    return for389, for388


def build_artifact() -> dict[str, Any]:
    for389, for388 = validate_sources()
    samples = for389.get("selectedPixelSamples")
    require(isinstance(samples, list), "FOR-389 selected samples missing")
    require(len(samples) == 16, "FOR-389 selected sample count changed")
    improved = [pixel for pixel in samples if selected_kind(pixel) == "improved"]
    regressions = [pixel for pixel in samples if selected_kind(pixel) == "regressed"]
    require(len(improved) == 8, "improved sample count changed")
    require(len(regressions) == 8, "regressed sample count changed")

    full_scene_before = for389["fullSceneImpactIfAppliedToSelectedPixelsOnly"][
        "baseUncorrectedFullSceneResidual"
    ]
    candidates = [
        evaluate_candidate(candidate, samples, full_scene_before)
        for candidate in discriminator_candidates()
    ]
    best = next(candidate for candidate in candidates if candidate["id"] == BEST_CANDIDATE_ID)
    require(best["candidateClass"] == CLASSIFICATION, "best candidate no longer defendable")
    require(best["selectedPixels"] == 8, "best selected count changed")
    require(best["improvedPixelsRecovered"] == 8, "best improved count changed")
    require(best["regressionsIncluded"] == 0, "best regression count changed")

    artifact = {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": "m60-f16-full-scene-regression-discriminator-for390",
        "sourceSceneId": for389["sceneId"],
        "sourceArtifact": rel(FOR389_ARTIFACT),
        "secondarySourceArtifact": rel(FOR388_ARTIFACT),
        "adapter": for389["adapter"],
        "producer": "scripts/validate_for390_m60_f16_full_scene_regression_discriminator.py",
        "producerInput": "FOR-389 selected full-scene samples; FOR-388 best-candidate context",
        "sourceMemory": SOURCE_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "requiredFor389Decision": FOR389_DECISION,
        "requiredFor389Classification": FOR389_CLASSIFICATION,
        "requiredFor388Decision": FOR388_DECISION,
        "requiredFor388Classification": FOR388_CLASSIFICATION,
        "decision": DECISION,
        "classification": CLASSIFICATION,
        "allowedClassifications": [
            "narrower-metadata-defendable",
            "narrower-metadata-still-regresses",
            "metadata-insufficient",
        ],
        "auditDoesNotProduceCorrection": True,
        "auditDoesNotApplyRendererChange": True,
        "correctionAppliedByDefault": False,
        "correctionPredicateEnabled": False,
        "runtimeHookInstalled": False,
        "sourceCandidate": {
            "id": SOURCE_CANDIDATE_ID,
            "for389Classification": for389["classification"],
            "selectedPixels": for389["candidate"]["selectedPixels"],
            "improvedPixels": for389["candidate"]["improvedPixels"],
            "regressedPixels": for389["candidate"]["regressedPixels"],
            "precision": for389["candidate"]["precision"],
            "recall": for389["candidate"]["recall"],
        },
        "metadataOnlySelectionPolicy": {
            "forbiddenSelectionFields": sorted(FORBIDDEN_SELECTION_FIELDS),
            "selectionMayUseSkiaReference": False,
            "selectionMayUseProbeOutcome": False,
            "selectionMayUseProbeResidual": False,
            "selectionMayUseCurrentResidual": False,
            "selectionMayUseDeltaVsCurrent": False,
            "selectionMayUseFor388Truth": False,
            "selectionMayUseFor389Truth": False,
            "evaluationMayUseTruthAndResiduals": True,
        },
        "selectedPixelSummary": {
            "totalSelectedByFor389": len(samples),
            "improved": len(improved),
            "regressed": len(regressions),
            "selectedResidualBefore": for389["candidate"]["selectedResidualIfApplied"]["beforeResidual"],
            "selectedResidualAfterFor389": for389["candidate"]["selectedResidualIfApplied"]["afterResidual"],
        },
        "selectedPixelComparison": build_selected_pixel_comparison(samples),
        "regressionMetadataGroups": build_regression_groups(regressions),
        "discriminatorCandidates": candidates,
        "bestDiscriminator": best,
        "classificationReason": (
            "Inside the FOR-389 selected population, existing metadata includes a narrower "
            "source-facing local band lane that recovers all 8 FOR-389 improved pixels "
            "while selecting 0 of the 8 FOR-389 regressions. It is diagnostic-only and "
            "must be captured/named as renderer-owned metadata before any activation."
        ),
        "nextMove": (
            "Do not activate the correction in FOR-390. A follow-up ticket may capture a "
            "stable renderer-owned source-facing local lane signal and re-evaluate it on "
            "the full M60 F16 scene before wiring a runtime predicate."
        ),
        "nonGoalsPreserved": {
            "rendererBehaviorChanged": False,
            "runtimeBehaviorChanged": False,
            "gpuOrWgslChanged": False,
            "geometryProductionChanged": False,
            "coverageProductionChanged": False,
            "fallbackChanged": False,
            "scoreChanged": False,
            "thresholdChanged": False,
            "promotionChanged": False,
            "correctionEnabled": False,
            "probeEnabledByDefault": False,
        },
        "validationCommands": VALIDATION_COMMANDS,
    }
    return artifact


def write_artifacts(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    write_report(data)


def write_report(data: dict[str, Any]) -> None:
    best = data["bestDiscriminator"]
    candidates = data["discriminatorCandidates"]
    groups_by_field = {group["field"]: group["groups"] for group in data["regressionMetadataGroups"]}
    candidate_rows = "\n".join(
        "| `{id}` | {selected} | {improved} | {regressions} | {precision:.4f} | {recall:.4f} | {after} | `{klass}` |".format(
            id=candidate["id"],
            selected=candidate["selectedPixels"],
            improved=candidate["improvedPixelsRecovered"],
            regressions=candidate["regressionsIncluded"],
            precision=candidate["precision"],
            recall=candidate["recall"],
            after=candidate["estimatedFullSceneResidualIfAppliedToThisSubset"]["simulatedAfterResidual"],
            klass=candidate["candidateClass"],
        )
        for candidate in candidates
    )
    regression_rows = "\n".join(
        "| `{field}` | {values} |".format(
            field=field,
            values=", ".join(f"`{entry['value']}`={entry['count']}" for entry in groups),
        )
        for field, groups in groups_by_field.items()
    )
    selected_rows = "\n".join(
        "| ({x},{y}) | `{kind}` | `{band}` | `{side}` | {local_x} | {edge} | {coverage} | `{relation}` |".format(
            x=pixel["x"],
            y=pixel["y"],
            kind=pixel["kind"],
            band=pixel["strokeBand"],
            side=pixel["orientedCoverageSide"],
            local_x=pixel["bandLocalX"],
            edge=pixel["bandEdgeDistance"],
            coverage=pixel["coverageAlphaByte"],
            relation=pixel["sourceCoverageRelation"],
        )
        for pixel in data["selectedPixelComparison"]
    )
    report = f"""# FOR-390 M60 F16 full-scene regression discriminator

Decision: `{data["decision"]}`

Classification: `{data["classification"]}`

Artifact: `{rel(ARTIFACT)}`

Sources:

- `{data["sourceArtifact"]}`
- `{data["secondarySourceArtifact"]}`

FOR-390 reste diagnostic-only. Il ne modifie pas le renderer, n'active aucune
correction, ne change pas WGSL, les fallbacks, les seuils, le scoring, ni la
politique de promotion.

## Resultat court

FOR-389 selectionne 16 pixels avec `{SOURCE_CANDIDATE_ID}` : 8 ameliores et 8
regresses. FOR-390 teste des discriminateurs plus fins a l'interieur de cette
population deja selectionnee. Les 8 regressions ont la meme famille de source bleue, la meme
relation source/couverture et des alpha source/couverture egaux a 160. Elles se
separent par position locale de bande :

- round/round regresse sur `bandLocalX` 0..4, alors que les pixels utiles
  round/round sont sur 39..45 ;
- butt/bevel regresse sur `bandLocalX` 18..20, alors que le seul pixel utile
  butt/bevel est sur 17.

Le meilleur discriminateur documente est `{best["id"]}` :

- pixels selectionnes : {best["selectedPixels"]} ;
- ameliores recuperes : {best["improvedPixelsRecovered"]}/8 ;
- regressions incluses : {best["regressionsIncluded"]}/8 ;
- precision : {best["precision"]:.4f} ;
- rappel : {best["recall"]:.4f} ;
- residuel full-scene estime : {best["estimatedFullSceneResidualIfAppliedToThisSubset"]["baseUncorrectedFullSceneResidual"]} -> {best["estimatedFullSceneResidualIfAppliedToThisSubset"]["simulatedAfterResidual"]}.

La classification est `{data["classification"]}` parce qu'une metadata deja
presente dans l'audit (`strokeBand` + `bandLocalX`), appliquee comme raffinement
du prefiltre FOR-389, suffit a separer les 8 pixels utiles des 8 regressions sur
le perimetre FOR-389. Cette conclusion reste une preuve d'analyse : la metadata
doit etre capturee et nommee comme signal renderer stable avant toute activation.

## Pixels selectionnes

| Coord | Type evaluation | Stroke band | Orientation | bandLocalX | distance bord | coverage alpha | relation source/couverture |
|---|---:|---|---|---:|---:|---:|---|
{selected_rows}

## Groupes des 8 regressions

| Metadata | Groupes |
|---|---|
{regression_rows}

## Discriminateurs testes

| Candidat | Selectionnes | Ameliores | Regressions | Precision | Rappel | Residuel estime | Classe |
|---|---:|---:|---:|---:|---:|---:|---|
{candidate_rows}

## Garde anti-oracle

Les discriminateurs ci-dessus utilisent seulement des metadata renderer
disponibles dans l'audit : `transparentSourceRgba`, famille couleur source,
orientation coverage, voisinage orthogonal, alpha coverage/source, `strokeBand`,
cap/join, `bandLocalX`, distance au bord, topologie et relation
source/couverture.

Ils n'utilisent pas la reference Skia, le probe, le residuel courant,
`deltaVsCurrent`, ni les verites FOR-388/FOR-389 comme selection primaire. Ces
donnees restent dans l'artefact uniquement pour evaluer precision, rappel et
residuel estime.

## Suite

Ne pas activer `{SOURCE_CANDIDATE_ID}` dans FOR-390. Le prochain ticket peut
capturer un signal renderer explicite de type `source-facing local band lane`
et le re-tester en scene complete avant toute garde runtime.
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor389Decision") == FOR389_DECISION, "FOR-389 decision requirement changed")
    require(data.get("requiredFor389Classification") == FOR389_CLASSIFICATION, "FOR-389 classification requirement changed")
    require(data.get("requiredFor388Decision") == FOR388_DECISION, "FOR-388 decision requirement changed")
    require(data.get("requiredFor388Classification") == FOR388_CLASSIFICATION, "FOR-388 classification requirement changed")
    require(set(data.get("allowedClassifications", [])) == {
        "narrower-metadata-defendable",
        "narrower-metadata-still-regresses",
        "metadata-insufficient",
    }, "allowed classifications changed")
    for key in (
        "auditDoesNotProduceCorrection",
        "auditDoesNotApplyRendererChange",
    ):
        require(data.get(key) is True, f"{key} must be true")
    for key in (
        "correctionAppliedByDefault",
        "correctionPredicateEnabled",
        "runtimeHookInstalled",
    ):
        require(data.get(key) is False, f"{key} must be false")

    summary = data.get("selectedPixelSummary")
    require(isinstance(summary, dict), "selected summary missing")
    require(summary.get("totalSelectedByFor389") == 16, "selected count changed")
    require(summary.get("improved") == 8, "improved count changed")
    require(summary.get("regressed") == 8, "regressed count changed")
    require(summary.get("selectedResidualBefore") == 734, "selected residual before changed")
    require(summary.get("selectedResidualAfterFor389") == 1109, "FOR-389 selected residual after changed")

    comparison = data.get("selectedPixelComparison")
    require(isinstance(comparison, list), "selected pixel comparison missing")
    require(len(comparison) == 16, "selected pixel comparison count changed")
    require(sum(1 for pixel in comparison if pixel.get("kind") == "improved") == 8, "improved comparison count changed")
    require(sum(1 for pixel in comparison if pixel.get("kind") == "regressed") == 8, "regressed comparison count changed")

    groups = data.get("regressionMetadataGroups")
    require(isinstance(groups, list), "regression groups missing")
    required_group_fields = {
        "transparentSourceRgba",
        "transparentSourceColorFamily",
        "orientedCoverageSide",
        "coverageOrthogonalNeighborhood",
        "coverageAlphaByte",
        "transparentSourceAlphaByte",
        "strokeBand",
        "capJoin",
        "bandLocalXBucket",
        "bandEdgeDistanceBucket",
        "fringeTopology",
        "sourceCoverageRelation",
    }
    require({group.get("field") for group in groups} == required_group_fields, "regression group fields changed")

    policy = data.get("metadataOnlySelectionPolicy")
    require(isinstance(policy, dict), "selection policy missing")
    for key, value in policy.items():
        if key == "forbiddenSelectionFields" or key == "evaluationMayUseTruthAndResiduals":
            continue
        require(value is False, f"selection policy {key} must be false")
    require(policy.get("evaluationMayUseTruthAndResiduals") is True, "evaluation truth flag changed")

    candidates = data.get("discriminatorCandidates")
    require(isinstance(candidates, list), "candidates missing")
    require(len(candidates) >= 6, "not enough discriminator candidates")
    ids = {candidate.get("id") for candidate in candidates}
    require(BEST_CANDIDATE_ID in ids, "best candidate missing")
    require("for389-source-color-and-oriented-coverage-lane" in ids, "FOR-389 baseline missing")
    for candidate in candidates:
        require(candidate.get("scope") == "FOR-389-selected-pixels", f"{candidate.get('id')} scope changed")
        require(candidate.get("refinesCandidateId") == SOURCE_CANDIDATE_ID, f"{candidate.get('id')} refines candidate changed")
        inherited = candidate.get("inheritedSelectionFields")
        require(isinstance(inherited, list), f"{candidate.get('id')} inherited fields missing")
        require(not FORBIDDEN_SELECTION_FIELDS.intersection(inherited), f"{candidate.get('id')} inherited forbidden fields")
        for key in (
            "usesSkiaReferenceForSelection",
            "usesProbeOutcomeForSelection",
            "usesProbeResidualForSelection",
            "usesDeltaVsCurrentForSelection",
            "usesCurrentResidualForSelection",
            "usesFor388TruthAsSelection",
            "usesFor389TruthAsSelection",
        ):
            require(candidate.get(key) is False, f"{candidate.get('id')} {key} must be false")
        require(candidate.get("forbiddenSelectionFields") == [], f"{candidate.get('id')} uses forbidden fields")
        method = str(candidate.get("selectionMethod", ""))
        for needle in FORBIDDEN_SELECTION_NEEDLES:
            require(needle not in method, f"{candidate.get('id')} selection method mentions forbidden {needle}")

    baseline = next(candidate for candidate in candidates if candidate["id"] == "for389-source-color-and-oriented-coverage-lane")
    require(baseline["selectedPixels"] == 16, "baseline selected count changed")
    require(baseline["improvedPixelsRecovered"] == 8, "baseline improved count changed")
    require(baseline["regressionsIncluded"] == 8, "baseline regression count changed")
    require(baseline["candidateClass"] == "narrower-metadata-still-regresses", "baseline class changed")

    best = data.get("bestDiscriminator")
    require(isinstance(best, dict), "best discriminator missing")
    require(best.get("id") == BEST_CANDIDATE_ID, "best discriminator id changed")
    require(best.get("selectedPixels") == 8, "best selected count changed")
    require(best.get("improvedPixelsRecovered") == 8, "best improved count changed")
    require(best.get("regressionsIncluded") == 0, "best regressions changed")
    require(best.get("precision") == 1.0, "best precision changed")
    require(best.get("recall") == 1.0, "best recall changed")
    residual = best.get("estimatedFullSceneResidualIfAppliedToThisSubset")
    require(isinstance(residual, dict), "best residual estimate missing")
    require(residual.get("simulatedAfterResidual") == 1949, "best full-scene residual estimate changed")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "non-goals missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal {key} must remain false")


def validate_report() -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        DECISION,
        CLASSIFICATION,
        SOURCE_CANDIDATE_ID,
        BEST_CANDIDATE_ID,
        "8 ameliores",
        "8 regressions",
        "2014 -> 1949",
        "diagnostic-only",
        "Ne pas activer",
    ):
        require(needle in text, f"report missing {needle}")


def main() -> None:
    data = build_artifact()
    write_artifacts(data)
    persisted = load_json(ARTIFACT)
    require(persisted == data, "persisted artifact differs from generated artifact")
    validate_artifact(persisted)
    validate_report()
    best = persisted["bestDiscriminator"]
    print(
        "FOR-390 validation passed: "
        f"{persisted['classification']} / selected={best['selectedPixels']} "
        f"improved={best['improvedPixelsRecovered']} regressions={best['regressionsIncluded']} "
        f"estimatedResidual={best['estimatedFullSceneResidualIfAppliedToThisSubset']['simulatedAfterResidual']}"
    )


if __name__ == "__main__":
    main()
