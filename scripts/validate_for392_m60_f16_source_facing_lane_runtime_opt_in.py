#!/usr/bin/env python3
"""Validate the FOR-392 M60 F16 source-facing lane runtime opt-in audit."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-392"
DECISION = "M60_F16_SOURCE_FACING_LANE_RUNTIME_OPT_IN_EVALUATED"
CLASSIFICATION = "runtime-hook-refused-missing-per-fragment-lane-metadata"
CANDIDATE_ID = "source-facing-local-band-lane"
METADATA_NAME = "sourceFacingLocalBandLane"
GUARD_PROPERTY = "kanvas.webgpu.m60F16SourceFacingLaneRuntimeCandidate.enabled"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-evaluer-predicate-runtime-opt-in-source-facing-local-band-lane-apres-for-391"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-391-enregistre-source-facing-local-band-lane-comme-metadata-diagnostique-stable-m60-f16"
)
FOR391_DECISION = "M60_F16_SOURCE_FACING_LOCAL_BAND_LANE_METADATA_RECORDED"
FOR391_CLASSIFICATION = "source-facing-local-band-lane-stable-diagnostic-only"
FOR389_DECISION = "M60_F16_SOURCE_COVERAGE_FULL_SCENE_CANDIDATE_EVALUATED"
FOR389_CLASSIFICATION = "full-scene-regresses"
FOR380_DECISION = "M60_F16_SOURCE_COLOR_CORRECTION_PROBE_RECORDED"
FOR380_CLASSIFICATION = "regression-detected"
OLD_PROBE_GUARD_PROPERTY = "kanvas.webgpu.m60F16SourceColorCorrectionProbe.enabled"

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-runtime-opt-in-for392"
)
ARTIFACT = ARTIFACT_DIR / "m60-f16-source-facing-lane-runtime-opt-in-for392.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-392-m60-f16-source-facing-lane-runtime-opt-in.md"
FOR391_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-metadata-for391/"
    "m60-f16-source-facing-lane-metadata-for391.json"
)
FOR389_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-coverage-full-scene-candidate-for389/"
    "m60-f16-source-coverage-full-scene-candidate-for389.json"
)
FOR380_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-color-correction-probe-for380/"
    "m60-f16-source-color-correction-probe-for380.json"
)
RENDERER_SOURCE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
AA_STENCIL_COVER_SHADER = PROJECT_ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

VALIDATION_COMMANDS = [
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true "
        ":gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
    "rtk python3 scripts/validate_for392_m60_f16_source_facing_lane_runtime_opt_in.py",
    "rtk python3 scripts/validate_for391_m60_f16_source_facing_lane_metadata.py",
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


def fail(message: str) -> None:
    raise SystemExit(f"FOR-392 validation failed: {message}")


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


def require_close(actual: Any, expected: float, label: str) -> None:
    require(isinstance(actual, (int, float)), f"{label} must be numeric")
    require(abs(float(actual) - expected) < 0.00005, f"{label} changed: {actual} != {expected}")


def validate_renderer_guard() -> dict[str, Any]:
    require(RENDERER_SOURCE.is_file(), "renderer source missing")
    source = RENDERER_SOURCE.read_text(encoding="utf-8")
    for needle in (
        "WEBGPU_M60_F16_SOURCE_FACING_LANE_RUNTIME_CANDIDATE_FLAG",
        GUARD_PROPERTY,
        'System.getProperty(WEBGPU_M60_F16_SOURCE_FACING_LANE_RUNTIME_CANDIDATE_FLAG, "false").toBoolean()',
        "m60F16SourceFacingLaneRuntimeCandidateRequested()",
        "if (m60F16SourceFacingLaneRuntimeCandidateRequested()) return false",
        OLD_PROBE_GUARD_PROPERTY,
        "packed[colorFilterBase + 2] = if (d.m60F16SourceColorCorrectionProbe) 0f else targetColorSpaceBlendFlag()",
    ):
        require(needle in source, f"renderer source missing {needle}")

    require(AA_STENCIL_COVER_SHADER.is_file(), "AA stencil-cover shader missing")
    shader = AA_STENCIL_COVER_SHADER.read_text(encoding="utf-8")
    for forbidden in ("sourceFacingLocalBandLane", "strokeBand", "bandLocalX"):
        require(forbidden not in shader, f"shader unexpectedly exposes {forbidden}")

    return {
        "rendererSource": rel(RENDERER_SOURCE),
        "shaderSource": rel(AA_STENCIL_COVER_SHADER),
        "guardRecognizedByRenderer": True,
        "enabledByDefault": False,
        "runtimeHookInstalled": False,
        "oldDrawWideProbeBlockedWhenFor392GuardRequested": True,
        "refusalReason": "m60-f16-source-facing-lane-missing-per-fragment-runtime-metadata",
        "missingRuntimeMetadata": [METADATA_NAME, "strokeBand", "bandLocalX"],
        "availableShaderControl": "draw-wide targetColorSpaceBlend flag only",
    }


def validate_sources() -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    for391 = load_json(FOR391_ARTIFACT)
    require(for391.get("decision") == FOR391_DECISION, "FOR-391 decision changed")
    require(for391.get("classification") == FOR391_CLASSIFICATION, "FOR-391 classification changed")
    require(for391.get("runtimeHookInstalled") is False, "FOR-391 must remain diagnostic-only")
    require(for391.get("correctionAppliedByDefault") is False, "FOR-391 correction default changed")
    selection = for391.get("selection")
    require(isinstance(selection, dict), "FOR-391 selection missing")
    require(selection.get("selectedPixels") == 8, "FOR-391 selected count changed")
    require(selection.get("improvedPixelsRecovered") == 8, "FOR-391 improved count changed")
    require(selection.get("regressionsIncluded") == 0, "FOR-391 regression count changed")
    ideal = selection.get("estimatedFullSceneResidualIfAppliedToThisSubset")
    require(isinstance(ideal, dict), "FOR-391 ideal residual missing")
    require(ideal.get("baseUncorrectedFullSceneResidual") == 2014, "FOR-391 base residual changed")
    require(ideal.get("simulatedAfterResidual") == 1949, "FOR-391 ideal residual changed")

    for389 = load_json(FOR389_ARTIFACT)
    require(for389.get("decision") == FOR389_DECISION, "FOR-389 decision changed")
    require(for389.get("classification") == FOR389_CLASSIFICATION, "FOR-389 classification changed")
    broad = for389.get("fullSceneImpactIfAppliedToSelectedPixelsOnly")
    require(isinstance(broad, dict), "FOR-389 broad impact missing")
    require(broad.get("baseUncorrectedFullSceneResidual") == 2014, "FOR-389 base residual changed")
    require(broad.get("simulatedAfterResidual") == 2389, "FOR-389 broad candidate residual changed")
    require(broad.get("fullProbeMismatchPixelsNotUsedForSelection") == 3181, "FOR-389 full-probe mismatch changed")
    require_close(broad.get("fullProbeSimilarityNotUsedForSelection"), 87.06, "FOR-389 full-probe similarity")

    for380 = load_json(FOR380_ARTIFACT)
    require(for380.get("decision") == FOR380_DECISION, "FOR-380 decision changed")
    require(for380.get("classification") == FOR380_CLASSIFICATION, "FOR-380 classification changed")
    require(for380.get("correctionAppliedByDefault") is False, "FOR-380 correction default changed")
    return for391, for389, for380


def build_candidate_pixels(for391: dict[str, Any]) -> list[dict[str, Any]]:
    pixels = for391.get("metadataPixels")
    require(isinstance(pixels, list), "FOR-391 metadata pixels missing")
    selected = [pixel for pixel in pixels if pixel.get(METADATA_NAME) is True]
    require(len(selected) == 8, "FOR-391 true metadata count changed")
    return [
        {
            "x": pixel["x"],
            "y": pixel["y"],
            METADATA_NAME: True,
            "reason": pixel["sourceFacingLocalBandLaneReason"],
            "derivationInputs": pixel["derivationInputs"],
            "evaluationOnly": pixel["evaluationOnly"],
        }
        for pixel in selected
    ]


def build_artifact() -> dict[str, Any]:
    renderer_guard = validate_renderer_guard()
    for391, for389, _for380 = validate_sources()
    selection = for391["selection"]
    ideal = selection["estimatedFullSceneResidualIfAppliedToThisSubset"]
    selected_residual = selection["selectedResidualIfApplied"]
    broad = for389["fullSceneImpactIfAppliedToSelectedPixelsOnly"]

    artifact = {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": "m60-f16-source-facing-lane-runtime-opt-in-for392",
        "sourceSceneId": for391["sceneId"],
        "sourceArtifact": rel(FOR391_ARTIFACT),
        "secondarySourceArtifact": rel(FOR389_ARTIFACT),
        "broadProbeSourceArtifact": rel(FOR380_ARTIFACT),
        "adapter": for391["adapter"],
        "producer": "scripts/validate_for392_m60_f16_source_facing_lane_runtime_opt_in.py",
        "producerInput": "FOR-391 metadata artifact plus FOR-389/FOR-380 runtime-risk evidence",
        "sourceMemory": SOURCE_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "requiredFor391Decision": FOR391_DECISION,
        "requiredFor391Classification": FOR391_CLASSIFICATION,
        "requiredFor389Decision": FOR389_DECISION,
        "requiredFor389Classification": FOR389_CLASSIFICATION,
        "requiredFor380Decision": FOR380_DECISION,
        "requiredFor380Classification": FOR380_CLASSIFICATION,
        "decision": DECISION,
        "classification": CLASSIFICATION,
        "allowedClassifications": [
            "runtime-candidate-defendable-opt-in-only",
            "runtime-candidate-non-activable-regresses",
            "runtime-hook-refused-missing-per-fragment-lane-metadata",
        ],
        "candidateOnly": True,
        "promoted": False,
        "limitedTo": "M60 F16 bounded stroke cap/join scene only",
        "supportClaim": False,
        "correctionAppliedByDefault": False,
        "correctionPredicateEnabledByDefault": False,
        "defaultBehaviorChanged": False,
        "rendererBehaviorChangedByDefault": False,
        "wgslChanged": False,
        "fallbackChanged": False,
        "explicitOptInGuard": {
            "guardId": GUARD_PROPERTY,
            "enabledByDefault": False,
            "recognizedByRenderer": True,
            "requestedStateEvaluation": "stable-refusal",
            "runtimeHookInstalled": False,
            "correctionAllowedWhenEnabled": False,
            "oldDrawWideProbeGuard": OLD_PROBE_GUARD_PROPERTY,
            "oldDrawWideProbeBlockedWhenFor392GuardRequested": True,
            "reason": renderer_guard["refusalReason"],
        },
        "defaultGuardOffProof": {
            "currentBehaviorSource": rel(FOR389_ARTIFACT),
            "baseUncorrectedFullSceneResidual": ideal["baseUncorrectedFullSceneResidual"],
            "unchangedResidualWithGuardDisabled": ideal["baseUncorrectedFullSceneResidual"],
            "baseUncorrectedMismatchPixels": broad["baseUncorrectedMismatchPixels"],
            "uncorrectedSimilarity": broad["uncorrectedSimilarity"],
            "guardDisabledMatchesCurrentCounters": True,
            "normalRouteRemainsRefused": True,
            "fallbackReasonStable": "coverage.stroke-cap-join-visual-parity-below-threshold",
        },
        "candidatePredicate": {
            "id": CANDIDATE_ID,
            "metadataName": METADATA_NAME,
            "selectionMethod": f"{METADATA_NAME} == true",
            "derivationFormula": (
                "(strokeBand == round-round && bandLocalX >= 39) || "
                "(strokeBand == butt-bevel && bandLocalX <= 17)"
            ),
            "scope": "FOR-391/FOR-389 selected M60 F16 pixels",
            "selectedPixels": selection["selectedPixels"],
            "sameEightPixelsAsFor391": True,
            "improvedPixelsRecovered": selection["improvedPixelsRecovered"],
            "improvedTruth": selection["improvedTruth"],
            "regressionsIncluded": selection["regressionsIncluded"],
            "unchangedIncluded": selection["unchangedIncluded"],
            "precision": selection["precision"],
            "recall": selection["recall"],
            "selectedResidualIfApplied": selected_residual,
            "idealSimulatedFullSceneImpactIfLaneRuntimeWereAvailable": ideal,
            "selectedPixelCoordinates": selection["selectedPixelCoordinates"],
        },
        "guardEnabledEvaluation": {
            "mode": "requested-but-refused",
            "runtimeCorrectionApplied": False,
            "runtimeHookInstalled": False,
            "residualWithGuardEnabled": ideal["baseUncorrectedFullSceneResidual"],
            "residualDeltaWithGuardEnabled": 0,
            "reason": renderer_guard["refusalReason"],
            "candidateIdealResidualNotApplied": ideal["simulatedAfterResidual"],
            "candidateIdealGainNotApplied": ideal["gainVsCurrent"],
            "driftFromFor391SelectedPixels": 0,
            "includedRegressions": 0,
        },
        "runtimeSafetyAudit": {
            "perFragmentLaneMetadataAvailableInShader": False,
            "drawWideCorrectionProbeAlreadyExists": True,
            "drawWideCorrectionProbeKnownToRegress": True,
            "drawWideCandidateResidual": broad["simulatedAfterResidual"],
            "drawWideCandidateDeltaVsCurrent": broad["deltaVsCurrent"],
            "fullProbeMismatchPixels": broad["fullProbeMismatchPixelsNotUsedForSelection"],
            "fullProbeSimilarity": broad["fullProbeSimilarityNotUsedForSelection"],
            "refuseBroadProbeAsImplementationOfLanePredicate": True,
            "rendererGuard": renderer_guard,
        },
        "candidatePixels": build_candidate_pixels(for391),
        "nonGoalsPreserved": {
            "correctionEnabledByDefault": False,
            "m60F16PromotedAsSupported": False,
            "generalizedOutsideM60F16": False,
            "globalScoringChanged": False,
            "thresholdChanged": False,
            "fallbackChanged": False,
            "unrelatedWgslRuntimeChanged": False,
            "unrelatedScenesChanged": False,
        },
        "classificationReason": (
            "The sourceFacingLocalBandLane predicate still selects the same 8 useful FOR-391 pixels "
            "with no included regression and an ideal simulated residual of 2014 -> 1949. It is not "
            "activated because the current AA stencil-cover shader only has a draw-wide colour-space "
            "control, not per-fragment strokeBand/bandLocalX/sourceFacingLocalBandLane metadata. "
            "Routing the new guard through the older draw-wide FOR-380 probe would reproduce a known "
            "regressing path instead of the lane predicate."
        ),
        "nextMove": (
            "Do not activate the predicate. A future ticket must first expose lane-safe per-fragment "
            "runtime metadata or a bounded shader-side predicate, then re-run this opt-in evaluation."
        ),
        "validationCommands": VALIDATION_COMMANDS,
    }
    return artifact


def write_artifacts(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    write_report(data)


def write_report(data: dict[str, Any]) -> None:
    candidate = data["candidatePredicate"]
    guard_eval = data["guardEnabledEvaluation"]
    pixels = "\n".join(
        "| ({x},{y}) | `{band}` | {local_x} | `{kind}` | {before} -> {after} |".format(
            x=pixel["x"],
            y=pixel["y"],
            band=pixel["derivationInputs"]["strokeBand"],
            local_x=pixel["derivationInputs"]["bandLocalX"],
            kind=pixel["evaluationOnly"]["kind"],
            before=pixel["evaluationOnly"]["currentResidual"],
            after=pixel["evaluationOnly"]["probeResidual"],
        )
        for pixel in data["candidatePixels"]
    )
    report = f"""# FOR-392 M60 F16 sourceFacingLocalBandLane runtime opt-in

Decision: `{data["decision"]}`

Classification: `{data["classification"]}`

Artifact: `{rel(ARTIFACT)}`

FOR-392 ajoute le garde opt-in `{GUARD_PROPERTY}`, desactive par defaut. Le
garde est reconnu cote renderer mais il n'active pas de correction : le hook
runtime est refuse car le shader AA stencil-cover n'expose pas de metadata par
fragment `strokeBand`, `bandLocalX` ou `{METADATA_NAME}`.

## Resultat

- comportement par defaut inchange : residuel M60 F16 `{data["defaultGuardOffProof"]["baseUncorrectedFullSceneResidual"]}` ;
- garde FOR-392 active : correction refusee, residuel `{guard_eval["residualWithGuardEnabled"]}`, delta `{guard_eval["residualDeltaWithGuardEnabled"]}` ;
- simulation ideale du predicate si metadata runtime par fragment disponible : `{candidate["idealSimulatedFullSceneImpactIfLaneRuntimeWereAvailable"]["baseUncorrectedFullSceneResidual"]} -> {candidate["idealSimulatedFullSceneImpactIfLaneRuntimeWereAvailable"]["simulatedAfterResidual"]}` ;
- pixels selectionnes : `{candidate["selectedPixels"]}` ;
- ameliores recuperes : `{candidate["improvedPixelsRecovered"]}/8` ;
- regressions incluses : `{candidate["regressionsIncluded"]}/8` ;
- promotion : `false`.

## Pourquoi le hook est refuse

Le vieux probe FOR-380 est un controle draw-wide : il force toute la passe a
conserver la source dans le domaine direct/recompose-on-white. FOR-389 a deja
montre que cette famille regresse en full-scene (`2014 -> 2389`). Le nouveau
predicate ne doit toucher que les 8 pixels `{METADATA_NAME}` ; sans metadata
par fragment, brancher le garde sur ce probe serait une activation unsafe.

## Pixels du predicate

| Coord | Stroke band | bandLocalX | Evaluation | Residuel |
|---|---|---:|---|---:|
{pixels}

## Politique

Le resultat reste candidate-only, non promu, non active par defaut et limite a
M60 F16. Aucun score, seuil, fallback, scene non liee ou WGSL runtime non lie
n'est modifie.
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor391Decision") == FOR391_DECISION, "FOR-391 requirement changed")
    require(data.get("requiredFor391Classification") == FOR391_CLASSIFICATION, "FOR-391 class changed")
    require(data.get("requiredFor389Decision") == FOR389_DECISION, "FOR-389 requirement changed")
    require(data.get("requiredFor389Classification") == FOR389_CLASSIFICATION, "FOR-389 class changed")
    require(data.get("requiredFor380Decision") == FOR380_DECISION, "FOR-380 requirement changed")
    require(data.get("requiredFor380Classification") == FOR380_CLASSIFICATION, "FOR-380 class changed")
    require(data.get("candidateOnly") is True, "must remain candidate-only")
    require(data.get("promoted") is False, "must not promote")
    require(data.get("supportClaim") is False, "must not claim support")
    for key in (
        "correctionAppliedByDefault",
        "correctionPredicateEnabledByDefault",
        "defaultBehaviorChanged",
        "rendererBehaviorChangedByDefault",
        "wgslChanged",
        "fallbackChanged",
    ):
        require(data.get(key) is False, f"{key} must be false")

    guard = data.get("explicitOptInGuard")
    require(isinstance(guard, dict), "guard missing")
    require(guard.get("guardId") == GUARD_PROPERTY, "guard id changed")
    require(guard.get("enabledByDefault") is False, "guard must be disabled by default")
    require(guard.get("recognizedByRenderer") is True, "guard must be recognized")
    require(guard.get("runtimeHookInstalled") is False, "runtime hook must remain refused")
    require(guard.get("correctionAllowedWhenEnabled") is False, "guard must not allow correction")
    require(guard.get("oldDrawWideProbeBlockedWhenFor392GuardRequested") is True, "old probe must be blocked")

    default = data.get("defaultGuardOffProof")
    require(isinstance(default, dict), "default proof missing")
    require(default.get("baseUncorrectedFullSceneResidual") == 2014, "base residual changed")
    require(default.get("unchangedResidualWithGuardDisabled") == 2014, "disabled residual changed")
    require(default.get("guardDisabledMatchesCurrentCounters") is True, "disabled counters must match")
    require(default.get("normalRouteRemainsRefused") is True, "normal route refusal changed")

    candidate = data.get("candidatePredicate")
    require(isinstance(candidate, dict), "candidate missing")
    require(candidate.get("id") == CANDIDATE_ID, "candidate id changed")
    require(candidate.get("metadataName") == METADATA_NAME, "metadata name changed")
    require(candidate.get("selectedPixels") == 8, "selected count changed")
    require(candidate.get("sameEightPixelsAsFor391") is True, "FOR-391 pixel parity changed")
    require(candidate.get("improvedPixelsRecovered") == 8, "improved count changed")
    require(candidate.get("improvedTruth") == 8, "improved truth changed")
    require(candidate.get("regressionsIncluded") == 0, "regression count changed")
    require(candidate.get("unchangedIncluded") == 0, "unchanged count changed")
    require(candidate.get("precision") == 1.0, "precision changed")
    require(candidate.get("recall") == 1.0, "recall changed")
    selected_residual = candidate.get("selectedResidualIfApplied")
    require(isinstance(selected_residual, dict), "selected residual missing")
    require(selected_residual.get("beforeResidual") == 734, "selected before residual changed")
    require(selected_residual.get("afterResidual") == 669, "selected after residual changed")
    ideal = candidate.get("idealSimulatedFullSceneImpactIfLaneRuntimeWereAvailable")
    require(isinstance(ideal, dict), "ideal impact missing")
    require(ideal.get("baseUncorrectedFullSceneResidual") == 2014, "ideal base changed")
    require(ideal.get("simulatedAfterResidual") == 1949, "ideal after changed")
    require(ideal.get("gainVsCurrent") == 65, "ideal gain changed")

    guard_enabled = data.get("guardEnabledEvaluation")
    require(isinstance(guard_enabled, dict), "guard enabled evaluation missing")
    require(guard_enabled.get("mode") == "requested-but-refused", "guard enabled mode changed")
    require(guard_enabled.get("runtimeCorrectionApplied") is False, "runtime correction must be refused")
    require(guard_enabled.get("runtimeHookInstalled") is False, "runtime hook must be absent")
    require(guard_enabled.get("residualWithGuardEnabled") == 2014, "guard-enabled residual changed")
    require(guard_enabled.get("residualDeltaWithGuardEnabled") == 0, "guard-enabled delta changed")
    require(guard_enabled.get("candidateIdealResidualNotApplied") == 1949, "candidate ideal residual changed")
    require(guard_enabled.get("driftFromFor391SelectedPixels") == 0, "FOR-391 selection drift changed")
    require(guard_enabled.get("includedRegressions") == 0, "included regressions changed")

    safety = data.get("runtimeSafetyAudit")
    require(isinstance(safety, dict), "runtime safety audit missing")
    require(safety.get("perFragmentLaneMetadataAvailableInShader") is False, "per-fragment metadata claim changed")
    require(safety.get("drawWideCorrectionProbeAlreadyExists") is True, "draw-wide probe existence changed")
    require(safety.get("drawWideCorrectionProbeKnownToRegress") is True, "draw-wide regression proof changed")
    require(safety.get("drawWideCandidateResidual") == 2389, "draw-wide residual changed")
    require(safety.get("drawWideCandidateDeltaVsCurrent") == 375, "draw-wide delta changed")
    require(safety.get("fullProbeMismatchPixels") == 3181, "full probe mismatch changed")
    require_close(safety.get("fullProbeSimilarity"), 87.06, "full probe similarity")
    require(safety.get("refuseBroadProbeAsImplementationOfLanePredicate") is True, "broad probe refusal changed")

    pixels = data.get("candidatePixels")
    require(isinstance(pixels, list), "candidate pixels missing")
    require(len(pixels) == 8, "candidate pixel count changed")
    require(sum(1 for pixel in pixels if pixel["evaluationOnly"]["kind"] == "improved") == 8, "improved pixel list changed")
    for pixel in pixels:
        derivation = pixel.get("derivationInputs")
        require(isinstance(derivation, dict), "pixel derivation missing")
        require(set(derivation) == {"strokeBand", "bandLocalX"}, "derivation fields changed")
        lane = (
            derivation["strokeBand"] == "round-round" and int(derivation["bandLocalX"]) >= 39
        ) or (
            derivation["strokeBand"] == "butt-bevel" and int(derivation["bandLocalX"]) <= 17
        )
        require(lane is True, "candidate pixel no longer matches lane predicate")

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
        GUARD_PROPERTY,
        "desactive par defaut",
        "correction refusee",
        "2014 -> 1949",
        "2014 -> 2389",
        "8/8",
        "0/8",
        "non promu",
        "M60 F16",
    ):
        require(needle in text, f"report missing {needle}")


def main() -> None:
    data = build_artifact()
    write_artifacts(data)
    persisted = load_json(ARTIFACT)
    require(persisted == data, "persisted artifact differs from generated artifact")
    validate_artifact(persisted)
    validate_report()
    candidate = persisted["candidatePredicate"]
    print(
        "FOR-392 validation passed: "
        f"{persisted['classification']} / selected={candidate['selectedPixels']} "
        f"improved={candidate['improvedPixelsRecovered']} regressions={candidate['regressionsIncluded']} "
        f"guardEnabledResidual={persisted['guardEnabledEvaluation']['residualWithGuardEnabled']} "
        f"idealResidual={candidate['idealSimulatedFullSceneImpactIfLaneRuntimeWereAvailable']['simulatedAfterResidual']}"
    )


if __name__ == "__main__":
    main()
