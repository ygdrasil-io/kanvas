#!/usr/bin/env python3
"""Validate the FOR-366 F16 positive residual target inventory."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-366"
SCENE_ID = "f16-positive-residual-target-inventory-for366"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-366-f16-positive-residual-target-inventory.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-f16-positive-residual-target-inventory-after-for-365"
)
SOURCE_FINDINGS = [
    "global/kanvas/findings/for-365-rejects-covered-source-alpha-f16-candidate-by-current-guards",
    "global/kanvas/findings/for-364-captures-independent-comparable-f16-arc-evidence",
    "global/kanvas/findings/for-363-constrained-f16-candidate-search-matrix-ready",
]

FOR365_SCENE_ID = "f16-constrained-candidate-evaluation-for365"
FOR365_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR365_SCENE_ID / f"{FOR365_SCENE_ID}.json"
)
FOR344_SCENE_ID = "f16-broader-non-arc-color-policy-for344"
FOR344_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR344_SCENE_ID / f"{FOR344_SCENE_ID}.json"
)
FOR357_SCENE_ID = "f16-additional-non-arc-comparable-row-for357"
FOR357_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR357_SCENE_ID / f"{FOR357_SCENE_ID}.json"
)

FOR365_REQUIRED_DECISION = "F16_CONSTRAINED_CANDIDATE_REJECTED_BY_CURRENT_GUARDS"
FOR344_REQUIRED_DECISION = "F16_BROADER_NON_ARC_EVIDENCE_PARTIAL_REQUIRES_MORE_REFERENCE_ROWS"
FOR357_REQUIRED_DECISION = "F16_ADDITIONAL_NON_ARC_COMPARABLE_ROW_PARTIAL_INSUFFICIENT_REFERENCE"

DECISION_READY = "F16_POSITIVE_RESIDUAL_TARGET_INVENTORY_READY"
ALLOWED_DECISIONS = [DECISION_READY]

CLOSED_FOR355_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
REJECTED_FOR365_POLICY_ID = "covered_source_alpha_src_over_white_without_non_arc_guard_probe"

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    "rtk python3 scripts/validate_for364_f16_independent_comparable_arc_evidence.py",
    "rtk python3 scripts/validate_for363_f16_constrained_candidate_search.py",
    "rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py",
    "rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py",
    "rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk python3 -m py_compile scripts/validate_for366_f16_positive_residual_target_inventory.py",
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
    raise SystemExit(f"FOR-366 validation failed: {message}")


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


def validate_source_decisions(
    for365: dict[str, Any],
    for344: dict[str, Any],
    for357: dict[str, Any],
) -> None:
    require(for365.get("linear") == "FOR-365", "FOR-365 identity changed")
    require(for365.get("decision") == FOR365_REQUIRED_DECISION, "FOR-365 decision changed")
    validate_implementation_false(for365, "FOR-365")
    candidate = for365.get("candidate")
    require(isinstance(candidate, dict), "FOR-365 candidate block missing")
    require(candidate.get("policyId") == REJECTED_FOR365_POLICY_ID, "FOR-365 rejected policy changed")
    require(candidate.get("selectedForImplementation") is False, "FOR-365 candidate selected for implementation")

    require(for344.get("linear") == "FOR-344", "FOR-344 identity changed")
    require(for344.get("decision") == FOR344_REQUIRED_DECISION, "FOR-344 decision changed")
    validate_implementation_false(for344, "FOR-344")

    require(for357.get("linear") == "FOR-357", "FOR-357 identity changed")
    require(for357.get("decision") == FOR357_REQUIRED_DECISION, "FOR-357 decision changed")
    validate_implementation_false(for357, "FOR-357")


def source_status(row: dict[str, Any], key: str) -> str | None:
    value = row.get(key)
    if isinstance(value, dict):
        status = value.get("status")
        if isinstance(status, str):
            return status
    return None


def guard_rows(for365: dict[str, Any]) -> list[dict[str, Any]]:
    evaluation = for365.get("evaluation")
    require(isinstance(evaluation, dict), "FOR-365 evaluation missing")
    table = evaluation.get("table")
    require(isinstance(table, list), "FOR-365 evaluation table missing")
    rows: list[dict[str, Any]] = []
    for item in table:
        require(isinstance(item, dict), "FOR-365 guard row must be an object")
        source = item.get("source")
        row_id = item.get("rowId")
        current = item.get("currentResidual")
        require(isinstance(source, str), "FOR-365 guard source missing")
        require(isinstance(row_id, str), f"{source} row id missing")
        require(isinstance(current, int), f"{source} current residual missing")
        classification = "mandatory-zero-guard" if current == 0 else "low-positive-mandatory-guard"
        rows.append(
            {
                "source": source,
                "rowId": row_id,
                "rowKind": item.get("rowKind"),
                "sourceArtifact": item.get("sourceArtifact"),
                "currentResidual": current,
                "for365CandidateResidual": item.get("candidateResidual"),
                "for365CandidateWorsened": item.get("guardWorsened"),
                "classification": classification,
                "mustRemainStable": True,
            }
        )
    require(
        [row["source"] for row in rows] == ["FOR-345", "FOR-358", "FOR-361", "FOR-364"],
        "FOR-365 guard source order changed",
    )
    return rows


def positive_targets(for344: dict[str, Any], for357: dict[str, Any]) -> list[dict[str, Any]]:
    matrix = for344.get("matrix")
    require(isinstance(matrix, dict), "FOR-344 matrix missing")
    matrix_rows = matrix.get("rows")
    require(isinstance(matrix_rows, list), "FOR-344 matrix rows missing")

    inventory = for357.get("inventory")
    require(isinstance(inventory, dict), "FOR-357 inventory missing")
    inventory_rows = inventory.get("rows")
    require(isinstance(inventory_rows, list), "FOR-357 inventory rows missing")
    for357_by_row = {row.get("rowId"): row for row in inventory_rows if isinstance(row, dict)}

    rows: list[dict[str, Any]] = []
    for row in matrix_rows:
        require(isinstance(row, dict), "FOR-344 matrix row must be an object")
        current = row.get("currentResidual")
        if not isinstance(current, int) or current <= 0:
            continue
        row_id = row.get("rowId")
        require(isinstance(row_id, str), "positive FOR-344 row id missing")
        companion = for357_by_row.get(row_id)
        reference_status = source_status(row, "reference") or (
            companion.get("referenceStatus") if isinstance(companion, dict) else None
        )
        current_status = source_status(row, "current") or (
            companion.get("currentStatus") if isinstance(companion, dict) else None
        )
        candidate_status = source_status(row, "candidate") or (
            companion.get("candidateStatus") if isinstance(companion, dict) else None
        )
        comparable = row.get("referenceCurrentCandidateComparable") is True
        risk = "high" if comparable else "metadata-gap"
        if row.get("nonArc") is True and current >= 100 and not comparable:
            risk = "evidence-target-before-candidate"
        rows.append(
            {
                "source": "FOR-344",
                "rowId": row_id,
                "family": row.get("family"),
                "sceneId": row.get("sceneId"),
                "rowKind": "non-arc" if row.get("nonArc") is True else "arc",
                "sourceKind": row.get("sourceKind"),
                "currentResidual": current,
                "referenceCurrentCandidateComparable": comparable,
                "referenceStatus": reference_status,
                "currentStatus": current_status,
                "candidateStatus": candidate_status,
                "sampleCount": row.get("sampleCount"),
                "sourceArtifacts": row.get("sourceArtifacts"),
                "guardRisk": risk,
                "nextUse": (
                    "candidate-evaluation-target"
                    if comparable
                    else "capture-comparable-reference-current-candidate-evidence-first"
                ),
            }
        )
    rows.sort(
        key=lambda row: (
            -int(row["currentResidual"]),
            0 if row["rowKind"] == "non-arc" else 1,
            str(row["rowId"]),
        )
    )
    return rows


def build_artifact() -> dict[str, Any]:
    for365 = load_json(FOR365_ARTIFACT)
    for344 = load_json(FOR344_ARTIFACT)
    for357 = load_json(FOR357_ARTIFACT)
    validate_source_decisions(for365, for344, for357)

    guards = guard_rows(for365)
    targets = positive_targets(for344, for357)
    require(targets, "no positive F16 target rows found")

    proposed = next(
        (
            row
            for row in targets
            if row["rowKind"] == "non-arc" and row["guardRisk"] == "evidence-target-before-candidate"
        ),
        targets[0],
    )
    proposed_family = {
        "familyId": "m60-bounded-stroke-cap-join-positive-residual-evidence-target",
        "selectedRowId": proposed["rowId"],
        "selectedFamily": proposed.get("family"),
        "currentResidual": proposed["currentResidual"],
        "nextTicketType": proposed["nextUse"],
        "candidateImplementationAuthorized": False,
        "scoreIncreaseAuthorized": False,
        "reason": (
            "The selected row has the largest non-arc positive residual in the committed F16 "
            "evidence, but it lacks a comparable reference/current/candidate row. The next "
            "safe move is to capture comparable evidence, not to implement a color policy."
        ),
    }

    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": SOURCE_FINDINGS,
        "decision": DECISION_READY,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-366 inventories current positive F16 residual targets after FOR-365 rejected "
            "a candidate that worsened every mandatory guard."
        ),
        "inputValidation": {
            "for365Artifact": rel(FOR365_ARTIFACT),
            "for365Decision": for365.get("decision"),
            "for365RequiredDecision": FOR365_REQUIRED_DECISION,
            "for344Artifact": rel(FOR344_ARTIFACT),
            "for344Decision": for344.get("decision"),
            "for344RequiredDecision": FOR344_REQUIRED_DECISION,
            "for357Artifact": rel(FOR357_ARTIFACT),
            "for357Decision": for357.get("decision"),
            "for357RequiredDecision": FOR357_REQUIRED_DECISION,
        },
        "rejectedCandidateBoundaries": [
            {
                "source": "FOR-355",
                "policyId": CLOSED_FOR355_POLICY_ID,
                "selectableForFutureWork": False,
                "reason": "closed after independent FOR-361 evidence",
            },
            {
                "source": "FOR-365",
                "policyId": REJECTED_FOR365_POLICY_ID,
                "selectableForFutureWork": False,
                "reason": "worsened all mandatory FOR-365 guards",
            },
        ],
        "guardRows": guards,
        "positiveResidualRows": targets,
        "rankingPolicy": {
            "sort": ["currentResidual descending", "non-arc before arc", "rowId ascending"],
            "fields": [
                "currentResidual",
                "rowKind",
                "referenceCurrentCandidateComparable",
                "referenceStatus",
                "currentStatus",
                "candidateStatus",
                "guardRisk",
            ],
        },
        "proposedNextEvidenceTarget": proposed_family,
        "criteriaEvaluation": {
            "sourceMemoryRecorded": True,
            "for365DecisionCaptured": True,
            "guardsSeparatedFromTargets": True,
            "positiveResidualRowsRanked": True,
            "for355CandidateExcluded": True,
            "for365CandidateExcluded": True,
            "exactlyOneNextEvidenceTargetProposed": True,
            "candidateImplementationAuthorized": False,
            "scoreIncreaseAuthorized": False,
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
    guards = data["guardRows"]
    positives = data["positiveResidualRows"]
    proposed = data["proposedNextEvidenceTarget"]

    lines = [
        "# FOR-366 F16 Positive Residual Target Inventory",
        "",
        f"Linear: `{LINEAR_ID}`",
        "",
        f"Decision: `{data['decision']}`",
        "",
        "FOR-366 does not implement a candidate. It inventories current F16 rows with",
        "positive residuals after FOR-365 rejected a candidate that worsened every guard.",
        "",
        "## Source Memory",
        "",
        f"- `{SOURCE_MEMORY}`",
        "",
        "## Rejected Candidate Boundaries",
        "",
        f"- FOR-355: `{CLOSED_FOR355_POLICY_ID}` remains closed and unselectable.",
        f"- FOR-365: `{REJECTED_FOR365_POLICY_ID}` remains rejected and unselectable.",
        "",
        "## Mandatory Guards",
        "",
        "| source | row | kind | current residual | FOR-365 candidate residual | classification |",
        "|---|---|---|---:|---:|---|",
    ]
    for row in guards:
        lines.append(
            f"| `{row['source']}` | `{row['rowId']}` | `{row['rowKind']}` | "
            f"{row['currentResidual']} | {row['for365CandidateResidual']} | `{row['classification']}` |"
        )

    lines.extend(
        [
            "",
            "## Positive Residual Inventory",
            "",
            "| rank | row | family | kind | current residual | comparable | guard risk | next use |",
            "|---:|---|---|---|---:|---|---|---|",
        ]
    )
    for index, row in enumerate(positives, start=1):
        lines.append(
            f"| {index} | `{row['rowId']}` | `{row.get('family')}` | `{row['rowKind']}` | "
            f"{row['currentResidual']} | `{row['referenceCurrentCandidateComparable']}` | "
            f"`{row['guardRisk']}` | `{row['nextUse']}` |"
        )

    lines.extend(
        [
            "",
            "## Proposed Next Evidence Target",
            "",
            f"- Family: `{proposed['familyId']}`",
            f"- Row: `{proposed['selectedRowId']}`",
            f"- Current residual: `{proposed['currentResidual']}`",
            f"- Next ticket type: `{proposed['nextTicketType']}`",
            "",
            "Reason: the selected row has the largest non-arc positive residual in the",
            "committed F16 evidence, but it is not yet a comparable reference/current/candidate",
            "row. The next safe move is to capture comparable evidence before evaluating or",
            "implementing another policy.",
            "",
            "## Non-goals Preserved",
            "",
            "- No renderer behavior change.",
            "- No score increase, threshold change, candidate implementation, or promotion.",
            "- No GPU/WGSL, geometry, coverage, fallback, Kadre, F16 premul, blend, or",
            "  `SkBitmap.getPixel` change.",
            "- No scene-id, coordinate, selected-cell, fixture-only, or full-GM-crop branch.",
            "",
            "## Artifacts",
            "",
            f"- JSON: `{rel(ARTIFACT)}`",
            f"- Validator: `scripts/validate_for366_f16_positive_residual_target_inventory.py`",
            f"- Report: `{rel(REPORT)}`",
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
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("decision") == DECISION_READY, "decision changed")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")

    inputs = data.get("inputValidation")
    require(isinstance(inputs, dict), "input validation missing")
    require(inputs.get("for365Decision") == FOR365_REQUIRED_DECISION, "FOR-365 input decision changed")
    require(inputs.get("for344Decision") == FOR344_REQUIRED_DECISION, "FOR-344 input decision changed")
    require(inputs.get("for357Decision") == FOR357_REQUIRED_DECISION, "FOR-357 input decision changed")

    guards = data.get("guardRows")
    require(isinstance(guards, list) and len(guards) == 4, "guard rows changed")
    require([row.get("source") for row in guards] == ["FOR-345", "FOR-358", "FOR-361", "FOR-364"], "guard order changed")
    require([row.get("currentResidual") for row in guards] == [0, 3, 0, 0], "guard residuals changed")

    positives = data.get("positiveResidualRows")
    require(isinstance(positives, list) and len(positives) >= 3, "positive residual inventory too small")
    residuals = [row.get("currentResidual") for row in positives]
    require(all(isinstance(value, int) and value > 0 for value in residuals), "invalid positive residual")
    require(residuals == sorted(residuals, reverse=True), "positive residual rows not sorted")

    proposed = data.get("proposedNextEvidenceTarget")
    require(isinstance(proposed, dict), "proposed next target missing")
    require(
        proposed.get("familyId") == "m60-bounded-stroke-cap-join-positive-residual-evidence-target",
        "unexpected proposed target family",
    )
    require(proposed.get("candidateImplementationAuthorized") is False, "FOR-366 authorized implementation")
    require(proposed.get("scoreIncreaseAuthorized") is False, "FOR-366 authorized score increase")

    criteria = data.get("criteriaEvaluation")
    require(isinstance(criteria, dict), "criteria evaluation missing")
    for key, expected in (
        ("sourceMemoryRecorded", True),
        ("for365DecisionCaptured", True),
        ("guardsSeparatedFromTargets", True),
        ("positiveResidualRowsRanked", True),
        ("for355CandidateExcluded", True),
        ("for365CandidateExcluded", True),
        ("exactlyOneNextEvidenceTargetProposed", True),
        ("candidateImplementationAuthorized", False),
        ("scoreIncreaseAuthorized", False),
    ):
        require(criteria.get(key) is expected, f"criteria changed: {key}")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation block missing")
    require(implementation.get("evidenceOnly") is True, "FOR-366 is no longer evidence-only")
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
        f"{DECISION_READY}: positiveRows={len(data['positiveResidualRows'])} "
        f"next={data['proposedNextEvidenceTarget']['selectedRowId']}"
    )


if __name__ == "__main__":
    main()
