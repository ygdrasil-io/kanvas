#!/usr/bin/env python3
"""Validate the FOR-368 M60 F16 candidate metadata capture evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-368"
SCENE_ID = "m60-f16-candidate-metadata-capture-for368"
SOURCE_SCENE_ID = "m60-bounded-stroke-cap-join"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
FAMILY_ID = "m60-bounded-stroke-cap-join-positive-residual-evidence-target"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-05-for-368-m60-f16-candidate-metadata-capture.md"
)

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-m60-f16-candidate-metadata-capture-after-for-367"
)

FOR365_SCENE_ID = "f16-constrained-candidate-evaluation-for365"
FOR365_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR365_SCENE_ID / f"{FOR365_SCENE_ID}.json"
)
FOR366_SCENE_ID = "f16-positive-residual-target-inventory-for366"
FOR366_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR366_SCENE_ID / f"{FOR366_SCENE_ID}.json"
)
FOR367_SCENE_ID = "m60-bounded-stroke-cap-join-comparable-f16-evidence-for367"
FOR367_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR367_SCENE_ID / f"{FOR367_SCENE_ID}.json"
)

FOR365_REQUIRED_DECISION = "F16_CONSTRAINED_CANDIDATE_REJECTED_BY_CURRENT_GUARDS"
FOR366_REQUIRED_DECISION = "F16_POSITIVE_RESIDUAL_TARGET_INVENTORY_READY"
FOR367_REQUIRED_DECISION = "M60_BOUNDED_STROKE_CAP_JOIN_COMPARABLE_F16_EVIDENCE_RECORDED"
FOR367_REQUIRED_CLASSIFICATION = "still-missing-comparable-metadata"

DECISION = "M60_F16_CANDIDATE_METADATA_STILL_MISSING"
CLASSIFICATION = "candidate-metadata-still-missing"
ALLOWED_CLASSIFICATIONS = ["ready-for-candidate-evaluation", CLASSIFICATION]
REQUIRED_RESIDUAL = 856
REQUIRED_SAMPLE_COUNT = 10
F16_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
REJECTED_FOR365_POLICY_ID = "covered_source_alpha_src_over_white_without_non_arc_guard_probe"
EXPECTED_COORDINATES = [
    (92, 75),
    (91, 76),
    (90, 77),
    (89, 78),
    (88, 79),
    (87, 80),
    (21, 81),
    (93, 74),
    (17, 77),
    (69, 81),
]

CANDIDATE_STATUS = "missing-from-committed-m60-artifacts"
SOURCE_STATUS = "missing-from-committed-m60-artifacts"
PREMUL_BLEND_STATUS = "not-capturable-from-committed-m60-artifacts"
CANDIDATE_REFUSAL_REASON = (
    "No committed M60 artifact records candidatePolicyRgba samples for policy "
    "straight_srgb_quantized_alpha_src_over_white at this coordinate; FOR-368 is "
    "evidence-only and cannot render or invent a candidate sample."
)
SOURCE_REFUSAL_REASON = (
    "The committed M60 artifacts expose reference/current RGBA samples only; they do "
    "not expose source/input/raw RGBA or raw F16 premul/unpremul components for this coordinate."
)
PREMUL_BLEND_REASON = (
    "FOR-367 records targetColorSpaceBlend diagnostics, not explicit source F16 "
    "premul/blend inputs for a selected candidate row."
)

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
    "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
    "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    "rtk python3 scripts/validate_for364_f16_independent_comparable_arc_evidence.py",
    "rtk python3 scripts/validate_for363_f16_constrained_candidate_search.py",
    "rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py",
    "rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py",
    "rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for368-pycache python3 -m py_compile "
        "scripts/validate_for368_m60_f16_candidate_metadata_capture.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]

IMPLEMENTATION_FALSE_KEYS = (
    "rendererBehaviorChanged",
    "newColorPolicyImplemented",
    "candidateSelectedForImplementation",
    "selectableCandidateDefined",
    "implementationPlanAuthorized",
    "colorToF16PremulChanged",
    "blendF16PremulModeChanged",
    "skBitmapGetPixelChanged",
    "skBitmapGetPixelAsSrgbChanged",
    "gpuOrWgslChanged",
    "geometryChanged",
    "coverageChanged",
    "fallbackChanged",
    "thresholdsChanged",
    "promotionChanged",
    "scoreChanged",
    "kadreChanged",
    "rendererFixtureOrCoordinateBranchAdded",
    "rendererSceneBranchAdded",
    "rendererSelectedCellOrFullGmCropBranchAdded",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-368 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


def rgba(value: Any, label: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{label} must be RGBA")
    result: list[int] = []
    for channel in value:
        require(isinstance(channel, int) and 0 <= channel <= 255, f"{label} channel out of range")
        result.append(channel)
    return result


def validate_implementation_false(data: dict[str, Any], linear: str) -> None:
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), f"{linear} implementation block missing")
    for key in (
        "rendererBehaviorChanged",
        "gpuOrWgslChanged",
        "geometryChanged",
        "coverageChanged",
        "fallbackChanged",
        "thresholdsChanged",
        "promotionChanged",
        "scoreChanged",
        "kadreChanged",
    ):
        require(implementation.get(key) is False, f"{linear} implementation guard changed: {key}")


def validate_inputs(
    for365: dict[str, Any],
    for366: dict[str, Any],
    for367: dict[str, Any],
) -> dict[str, Any]:
    require(for365.get("linear") == "FOR-365", "FOR-365 identity changed")
    require(for365.get("decision") == FOR365_REQUIRED_DECISION, "FOR-365 decision changed")
    validate_implementation_false(for365, "FOR-365")
    candidate = for365.get("candidate")
    require(isinstance(candidate, dict), "FOR-365 candidate block missing")
    require(candidate.get("policyId") == REJECTED_FOR365_POLICY_ID, "FOR-365 rejected policy changed")
    require(candidate.get("selectedForImplementation") is False, "FOR-365 candidate selected")

    require(for366.get("linear") == "FOR-366", "FOR-366 identity changed")
    require(for366.get("decision") == FOR366_REQUIRED_DECISION, "FOR-366 decision changed")
    validate_implementation_false(for366, "FOR-366")
    target = for366.get("proposedNextEvidenceTarget")
    require(isinstance(target, dict), "FOR-366 selected target missing")
    require(target.get("familyId") == FAMILY_ID, "FOR-366 selected family changed")
    require(target.get("selectedRowId") == ROW_ID, "FOR-366 selected row changed")
    require(target.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-366 residual changed")

    require(for367.get("linear") == "FOR-367", "FOR-367 identity changed")
    require(for367.get("decision") == FOR367_REQUIRED_DECISION, "FOR-367 decision changed")
    require(for367.get("classification") == FOR367_REQUIRED_CLASSIFICATION, "FOR-367 classification changed")
    validate_implementation_false(for367, "FOR-367")
    line = for367.get("evidenceLine")
    require(isinstance(line, dict), "FOR-367 evidence line missing")
    require(line.get("rowId") == ROW_ID, "FOR-367 row changed")
    require(line.get("familyId") == FAMILY_ID, "FOR-367 family changed")
    require(line.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-367 residual changed")
    require(line.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-367 sample count changed")
    require(line.get("referenceCurrentCandidateComparable") is False, "FOR-367 candidate comparability changed")
    return line


def sample_residual(reference: list[int], current: list[int]) -> int:
    return sum(abs(reference[index] - current[index]) for index in range(4))


def build_sample(raw: dict[str, Any], index: int) -> dict[str, Any]:
    require(raw.get("index") == index, "FOR-367 sample index changed")
    require((raw.get("x"), raw.get("y")) == EXPECTED_COORDINATES[index - 1], "FOR-367 sample coordinate changed")
    reference = rgba(raw.get("referenceRgba"), "referenceRgba")
    current = rgba(raw.get("currentRgba"), "currentRgba")
    residual = sample_residual(reference, current)
    require(raw.get("sampleResidual") == residual, "FOR-367 sample residual changed")
    require(raw.get("candidatePolicyRgba") is None, "FOR-367 unexpectedly captured candidatePolicyRgba")
    require(raw.get("sourceInputRgba") is None, "FOR-367 unexpectedly captured sourceInputRgba")
    return {
        "index": index,
        "x": raw["x"],
        "y": raw["y"],
        "region": raw.get("region"),
        "referenceRgba": reference,
        "currentRgba": current,
        "sampleResidual": residual,
        "candidatePolicyRgba": None,
        "candidatePolicyRgbaStatus": CANDIDATE_STATUS,
        "candidatePolicyRgbaRefusalReason": CANDIDATE_REFUSAL_REASON,
        "sourceRawRgba": None,
        "sourceRawRgbaStatus": SOURCE_STATUS,
        "sourceRawRgbaRefusalReason": SOURCE_REFUSAL_REASON,
        "premulBlendAssumptionStatus": PREMUL_BLEND_STATUS,
        "premulBlendAssumptionReason": PREMUL_BLEND_REASON,
        "readyForCandidateEvaluation": False,
    }


def build_artifact() -> dict[str, Any]:
    for365 = load_json(FOR365_ARTIFACT)
    for366 = load_json(FOR366_ARTIFACT)
    for367 = load_json(FOR367_ARTIFACT)
    line = validate_inputs(for365, for366, for367)

    raw_samples = line.get("samples")
    require(isinstance(raw_samples, list), "FOR-367 samples missing")
    require(len(raw_samples) == REQUIRED_SAMPLE_COUNT, "FOR-367 sample count changed")
    samples = [build_sample(raw, index) for index, raw in enumerate(raw_samples, start=1)]
    computed_residual = sum(sample["sampleResidual"] for sample in samples)
    require(computed_residual == REQUIRED_RESIDUAL, "computed residual changed")

    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceSceneId": SOURCE_SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION,
        "classification": CLASSIFICATION,
        "allowedClassifications": ALLOWED_CLASSIFICATIONS,
        "decisionReason": (
            "FOR-368 preserves the FOR-367 M60 coordinates and residual, but the committed "
            "artifacts do not contain candidatePolicyRgba or source/raw RGBA samples; the "
            "line therefore remains candidate-metadata-still-missing."
        ),
        "inputValidation": {
            "for365Artifact": rel(FOR365_ARTIFACT),
            "for365Decision": for365.get("decision"),
            "for365RequiredDecision": FOR365_REQUIRED_DECISION,
            "for366Artifact": rel(FOR366_ARTIFACT),
            "for366Decision": for366.get("decision"),
            "for366RequiredDecision": FOR366_REQUIRED_DECISION,
            "for367Artifact": rel(FOR367_ARTIFACT),
            "for367Decision": for367.get("decision"),
            "for367RequiredDecision": FOR367_REQUIRED_DECISION,
            "for367Classification": for367.get("classification"),
            "for367RequiredClassification": FOR367_REQUIRED_CLASSIFICATION,
            "for367RowId": line.get("rowId"),
            "for367CurrentResidual": line.get("currentResidual"),
            "for367SampleCount": line.get("sampleCount"),
        },
        "evidenceLine": {
            "rowId": ROW_ID,
            "familyId": FAMILY_ID,
            "sourceKind": line.get("sourceKind"),
            "rowKind": line.get("rowKind"),
            "candidatePolicyId": F16_POLICY_ID,
            "currentResidual": REQUIRED_RESIDUAL,
            "computedResidual": computed_residual,
            "sampleCount": REQUIRED_SAMPLE_COUNT,
            "sampleCoordinatesPreservedFromFor367": True,
            "referenceCurrentComparable": line.get("referenceCurrentComparable"),
            "referenceCurrentCandidateComparable": False,
            "candidatePolicyRgba": {
                "available": False,
                "status": CANDIDATE_STATUS,
                "refusalReason": CANDIDATE_REFUSAL_REASON,
            },
            "sourceRawRgba": {
                "available": False,
                "status": SOURCE_STATUS,
                "refusalReason": SOURCE_REFUSAL_REASON,
            },
            "premulBlendAssumptions": {
                "available": False,
                "status": PREMUL_BLEND_STATUS,
                "reason": PREMUL_BLEND_REASON,
            },
            "samples": samples,
        },
        "candidateMetadataReadiness": {
            "classification": CLASSIFICATION,
            "readyForCandidateEvaluation": False,
            "missingMetadata": [
                "candidatePolicyRgba samples for the same 10 FOR-367 coordinates",
                "source/input/raw RGBA for the same 10 FOR-367 coordinates",
                "explicit F16 premul/blend inputs for a selected candidate row",
            ],
            "refusalAcceptedAsEvidence": True,
            "refusalIsImplementationAuthorization": False,
        },
        "nonGoalsPreserved": {
            "rendererBehaviorChanged": False,
            "scoreIncreased": False,
            "thresholdChanged": False,
            "candidateImplementationAuthorized": False,
            "gpuOrWgslChanged": False,
            "geometryChanged": False,
            "coverageChanged": False,
            "fallbackChanged": False,
            "kadreChanged": False,
            "f16PremulBlendChanged": False,
            "skBitmapGetPixelChanged": False,
        },
        "criteriaEvaluation": {
            "sourceMemoryRecorded": True,
            "for367InputRecorded": True,
            "for367DecisionRequired": True,
            "for367ClassificationRequired": True,
            "sameTenCoordinatesPreserved": True,
            "currentResidualKeptAt856": True,
            "referenceRgbaRecordedForEachSample": True,
            "currentRgbaRecordedForEachSample": True,
            "candidatePolicyRgbaStatusRecordedForEachSample": True,
            "sourceRawRgbaStatusRecordedForEachSample": True,
            "premulBlendAssumptionStatusRecordedForEachSample": True,
            "stableRefusalReasonsRecorded": True,
            "classificationRecorded": True,
            "rendererBehaviorChanged": False,
            "scoreIncreased": False,
            "thresholdChanged": False,
            "candidateImplementationAuthorized": False,
        },
        "implementation": {
            "evidenceOnly": True,
            "rendererBehaviorChanged": False,
            "newColorPolicyImplemented": False,
            "candidateSelectedForImplementation": False,
            "selectableCandidateDefined": False,
            "implementationPlanAuthorized": False,
            "colorToF16PremulChanged": False,
            "blendF16PremulModeChanged": False,
            "skBitmapGetPixelChanged": False,
            "skBitmapGetPixelAsSrgbChanged": False,
            "gpuOrWgslChanged": False,
            "geometryChanged": False,
            "coverageChanged": False,
            "fallbackChanged": False,
            "thresholdsChanged": False,
            "promotionChanged": False,
            "scoreChanged": False,
            "kadreChanged": False,
            "rendererFixtureOrCoordinateBranchAdded": False,
            "rendererSceneBranchAdded": False,
            "rendererSelectedCellOrFullGmCropBranchAdded": False,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def render_report(data: dict[str, Any]) -> str:
    line = data["evidenceLine"]
    samples = line["samples"]
    readiness = data["candidateMetadataReadiness"]
    lines = [
        "# FOR-368 Capture des métadonnées candidate F16 M60",
        "",
        f"Linear: `{LINEAR_ID}`",
        "",
        f"Décision: `{data['decision']}`",
        "",
        f"Classification: `{data['classification']}`",
        "",
        "FOR-368 est un ticket de preuve uniquement. Il reprend les 10 coordonnées",
        "FOR-367 pour `m60-bounded-stroke-cap-join`, conserve le résiduel courant",
        "`856` et enregistre pourquoi les métadonnées candidate F16 restent",
        "indisponibles dans les artefacts commités.",
        "",
        "## Source mémoire",
        "",
        f"- `{SOURCE_MEMORY}`",
        "",
        "## Entrées obligatoires",
        "",
        f"- FOR-365 décision requise: `{FOR365_REQUIRED_DECISION}`",
        f"- FOR-366 décision requise: `{FOR366_REQUIRED_DECISION}`",
        f"- FOR-367 décision requise: `{FOR367_REQUIRED_DECISION}`",
        f"- FOR-367 classification requise: `{FOR367_REQUIRED_CLASSIFICATION}`",
        f"- Ligne: `{ROW_ID}`",
        f"- Résiduel courant: `{REQUIRED_RESIDUAL}`",
        "",
        "## Résultat",
        "",
        f"- Classification: `{readiness['classification']}`",
        f"- Prêt pour évaluation candidate: `{readiness['readyForCandidateEvaluation']}`",
        f"- Échantillons préservés: `{line['sampleCount']}`",
        f"- Résiduel recomputé: `{line['computedResidual']}`",
        "",
        "La ligne reste `candidate-metadata-still-missing`: aucun artefact commité",
        "ne contient les samples `candidatePolicyRgba`, les valeurs source/input/raw",
        "RGBA, ni les entrées F16 premul/blend nécessaires pour évaluer une future",
        "candidate. Ces valeurs ne sont pas inventées.",
        "",
        "## Refus stables",
        "",
        f"- candidatePolicyRgba: `{line['candidatePolicyRgba']['status']}` - {line['candidatePolicyRgba']['refusalReason']}",
        f"- source/raw RGBA: `{line['sourceRawRgba']['status']}` - {line['sourceRawRgba']['refusalReason']}",
        f"- hypothèses premul/blend: `{line['premulBlendAssumptions']['status']}` - {line['premulBlendAssumptions']['reason']}",
        "",
        "## Table des échantillons",
        "",
        "| # | x | y | reference RGBA | current RGBA | residual | candidatePolicyRgba | source/raw RGBA | premul/blend |",
        "|---:|---:|---:|---|---|---:|---|---|---|",
    ]
    for sample in samples:
        lines.append(
            f"| {sample['index']} | {sample['x']} | {sample['y']} | "
            f"`{sample['referenceRgba']}` | `{sample['currentRgba']}` | "
            f"{sample['sampleResidual']} | `{sample['candidatePolicyRgbaStatus']}` | "
            f"`{sample['sourceRawRgbaStatus']}` | `{sample['premulBlendAssumptionStatus']}` |"
        )

    lines.extend(
        [
            "",
            "## Non-objectifs respectés",
            "",
            "- Aucun changement de rendu.",
            "- Aucun score augmenté.",
            "- Aucun seuil modifié.",
            "- Aucune implémentation candidate autorisée.",
            "- Aucun changement GPU/WGSL, géométrie, couverture, fallback, Kadre,",
            "  F16 premul, blend ou `SkBitmap.getPixel`.",
            "",
            "## Artefacts",
            "",
            f"- JSON: `{rel(ARTIFACT)}`",
            f"- Validateur: `scripts/validate_for368_m60_f16_candidate_metadata_capture.py`",
            f"- Rapport: `{rel(REPORT)}`",
            "",
            "## Validation",
            "",
        ]
    )
    lines.extend(f"- `{command}`" for command in VALIDATION_COMMANDS)
    return "\n".join(lines) + "\n"


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceSceneId") == SOURCE_SCENE_ID, "source scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("allowedClassifications") == ALLOWED_CLASSIFICATIONS, "allowed classifications changed")

    inputs = data.get("inputValidation")
    require(isinstance(inputs, dict), "input validation missing")
    require(inputs.get("for365Decision") == FOR365_REQUIRED_DECISION, "FOR-365 decision changed")
    require(inputs.get("for366Decision") == FOR366_REQUIRED_DECISION, "FOR-366 decision changed")
    require(inputs.get("for367Decision") == FOR367_REQUIRED_DECISION, "FOR-367 decision changed")
    require(inputs.get("for367Classification") == FOR367_REQUIRED_CLASSIFICATION, "FOR-367 classification changed")
    require(inputs.get("for367RowId") == ROW_ID, "FOR-367 row changed")
    require(inputs.get("for367CurrentResidual") == REQUIRED_RESIDUAL, "FOR-367 residual changed")

    line = data.get("evidenceLine")
    require(isinstance(line, dict), "evidence line missing")
    require(line.get("rowId") == ROW_ID, "row id changed")
    require(line.get("familyId") == FAMILY_ID, "family id changed")
    require(line.get("candidatePolicyId") == F16_POLICY_ID, "candidate policy changed")
    require(line.get("currentResidual") == REQUIRED_RESIDUAL, "residual changed")
    require(line.get("computedResidual") == REQUIRED_RESIDUAL, "computed residual changed")
    require(line.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count changed")
    require(line.get("sampleCoordinatesPreservedFromFor367") is True, "coordinates not preserved")

    for key, status in (
        ("candidatePolicyRgba", CANDIDATE_STATUS),
        ("sourceRawRgba", SOURCE_STATUS),
    ):
        block = line.get(key)
        require(isinstance(block, dict), f"{key} block missing")
        require(block.get("available") is False, f"{key} unexpectedly available")
        require(block.get("status") == status, f"{key} status changed")
        require(isinstance(block.get("refusalReason"), str) and block["refusalReason"], f"{key} refusal missing")
    premul = line.get("premulBlendAssumptions")
    require(isinstance(premul, dict), "premul/blend block missing")
    require(premul.get("available") is False, "premul/blend unexpectedly available")
    require(premul.get("status") == PREMUL_BLEND_STATUS, "premul/blend status changed")
    require(isinstance(premul.get("reason"), str) and premul["reason"], "premul/blend reason missing")

    samples = line.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "samples changed")
    residual = 0
    for index, sample in enumerate(samples, start=1):
        require(isinstance(sample, dict), "sample must be object")
        require(sample.get("index") == index, "sample index changed")
        require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "sample coordinate changed")
        reference = rgba(sample.get("referenceRgba"), "sample referenceRgba")
        current = rgba(sample.get("currentRgba"), "sample currentRgba")
        expected_residual = sample_residual(reference, current)
        require(sample.get("sampleResidual") == expected_residual, "sample residual changed")
        residual += expected_residual
        require(sample.get("candidatePolicyRgba") is None, "candidatePolicyRgba unexpectedly present")
        require(sample.get("candidatePolicyRgbaStatus") == CANDIDATE_STATUS, "candidate status changed")
        require(
            sample.get("candidatePolicyRgbaRefusalReason") == CANDIDATE_REFUSAL_REASON,
            "candidate refusal reason changed",
        )
        require(sample.get("sourceRawRgba") is None, "source raw RGBA unexpectedly present")
        require(sample.get("sourceRawRgbaStatus") == SOURCE_STATUS, "source status changed")
        require(sample.get("sourceRawRgbaRefusalReason") == SOURCE_REFUSAL_REASON, "source reason changed")
        require(sample.get("premulBlendAssumptionStatus") == PREMUL_BLEND_STATUS, "premul/blend status changed")
        require(sample.get("premulBlendAssumptionReason") == PREMUL_BLEND_REASON, "premul/blend reason changed")
        require(sample.get("readyForCandidateEvaluation") is False, "sample unexpectedly ready")
    require(residual == REQUIRED_RESIDUAL, "sample residual total changed")

    readiness = data.get("candidateMetadataReadiness")
    require(isinstance(readiness, dict), "candidate metadata readiness missing")
    require(readiness.get("classification") == CLASSIFICATION, "readiness classification changed")
    require(readiness.get("readyForCandidateEvaluation") is False, "readiness changed")
    require(readiness.get("refusalAcceptedAsEvidence") is True, "refusal evidence flag changed")
    require(readiness.get("refusalIsImplementationAuthorization") is False, "refusal authorized implementation")

    criteria = data.get("criteriaEvaluation")
    require(isinstance(criteria, dict), "criteria evaluation missing")
    for key, expected in (
        ("sourceMemoryRecorded", True),
        ("for367InputRecorded", True),
        ("for367DecisionRequired", True),
        ("for367ClassificationRequired", True),
        ("sameTenCoordinatesPreserved", True),
        ("currentResidualKeptAt856", True),
        ("referenceRgbaRecordedForEachSample", True),
        ("currentRgbaRecordedForEachSample", True),
        ("candidatePolicyRgbaStatusRecordedForEachSample", True),
        ("sourceRawRgbaStatusRecordedForEachSample", True),
        ("premulBlendAssumptionStatusRecordedForEachSample", True),
        ("stableRefusalReasonsRecorded", True),
        ("classificationRecorded", True),
        ("rendererBehaviorChanged", False),
        ("scoreIncreased", False),
        ("thresholdChanged", False),
        ("candidateImplementationAuthorized", False),
    ):
        require(criteria.get(key) is expected, f"criteria changed: {key}")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "non-goals block missing")
    require(all(value is False for value in non_goals.values()), "a non-goal was not preserved")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation block missing")
    require(implementation.get("evidenceOnly") is True, "FOR-368 is no longer evidence-only")
    for key in IMPLEMENTATION_FALSE_KEYS:
        require(implementation.get(key) is False, f"implementation guard changed: {key}")


def main() -> None:
    data = build_artifact()
    json_text = json.dumps(data, indent=2, sort_keys=False) + "\n"
    report_text = render_report(data)
    write_if_changed(ARTIFACT, json_text)
    write_if_changed(REPORT, report_text)
    validate_artifact(load_json(ARTIFACT))
    require(REPORT.read_text(encoding="utf-8") == report_text, "report is stale")
    print(
        f"{DECISION}: row={ROW_ID} residual={REQUIRED_RESIDUAL} "
        f"classification={CLASSIFICATION}"
    )


if __name__ == "__main__":
    main()
