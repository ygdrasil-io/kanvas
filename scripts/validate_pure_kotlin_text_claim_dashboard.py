#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from typing import Any


DASHBOARD_PATH = "reports/pure-kotlin-text/font-claim-dashboard.json"
BUILD_GRADLE_PATH = "build.gradle.kts"

EXPECTED_CLASSIFICATIONS = [
    "target-supported",
    "current-supported",
    "tracked-gap",
    "DependencyGated",
    "fixture-gated",
    "GPU-gated",
    "expected-unsupported",
    "drift-only",
]

EXPECTED_EVIDENCE_KINDS = [
    "fixture-provenance",
    "deterministic-dump",
    "cpu-oracle",
    "gpu-artifact",
    "route-diagnostics",
    "refusal-diagnostics",
]

EVIDENCE_FIELDS = [
    "fixtureProvenance",
    "deterministicDumps",
    "cpuOracle",
    "gpuArtifacts",
    "routeDiagnostics",
    "refusalDiagnostics",
]

COHERENT_NON_CLAIM_SURFACES = {
    "glyph-artifact-metrics",
    "glyph-atlas-occupancy",
    "glyph-cache-metrics",
}

REQUIRED_SURFACES = {
    "complex-shaping",
    "emoji-color",
    "fallback",
    "lcd",
    "outline-path",
    "sdf",
    "simple-latin-atlas",
}

REQUIRED_GENERIC_LABELS = {
    "emoji supported",
    "font missing",
    "text works",
}

REQUIRED_LEGACY_GATES = {
    "coloremoji_blendmodes",
    "dftext",
    "fontations",
    "fontations_ft_compare",
    "pdf_never_embed",
    "scaledemoji",
    "scaledemoji_rendering",
}

TOP_LEVEL_KEYS = [
    "schemaVersion",
    "dashboardId",
    "ticketId",
    "sourceSpecs",
    "supportClaim",
    "claimPromotionAllowed",
    "classifications",
    "classificationRules",
    "requiredEvidenceKinds",
    "surfaceRows",
    "negativeGenericLabels",
    "legacyGates",
    "gradleWiring",
    "validationCommands",
    "nonClaims",
]

SOURCE_SPEC_KEYS = ["path", "section"]
CLASSIFICATION_RULE_KEYS = ["classification", "meaning", "minimumEvidenceKinds", "promotionPolicy"]
EVIDENCE_KIND_KEYS = ["kind", "dashboardField", "requiredWhen"]
TARGET_SPEC_KEYS = ["path", "section"]
SURFACE_ROW_KEYS = [
    "surfaceId",
    "label",
    "classification",
    "gpuClaimed",
    "claimPromotionAllowed",
    "targetSpec",
    "evidence",
    "requiredEvidenceGates",
    "legacyGates",
    "nonClaims",
]
NEGATIVE_LABEL_KEYS = ["label", "diagnosticCode", "classification", "replacementRequirement", "claimPromotionAllowed"]
LEGACY_GATE_KEYS = [
    "gate",
    "status",
    "classification",
    "targetOwners",
    "requiredRetirementEvidence",
    "replacementClaim",
    "claimPromotionAllowed",
]
GRADLE_WIRING_KEYS = ["taskName", "validatorCommand", "sceneGateTask", "pmBundleTask"]

TASK_NAME = "validatePureKotlinTextClaimDashboard"
VALIDATOR_COMMAND = "rtk python3 scripts/validate_pure_kotlin_text_claim_dashboard.py"


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"pure Kotlin text claim dashboard validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def require_keys(payload: dict[str, Any], expected: list[str], label: str) -> None:
    require(list(payload.keys()) == expected, f"{label} keys must be stable and ordered: {expected}")


def require_string(value: Any, label: str) -> str:
    require(isinstance(value, str) and value.strip() == value and value, f"{label} must be a non-empty trimmed string")
    return value


def require_string_list(value: Any, label: str, *, allow_empty: bool = False) -> list[str]:
    require(isinstance(value, list), f"{label} must be a list")
    if not allow_empty:
        require(value, f"{label} must be non-empty")
    for index, item in enumerate(value):
        require_string(item, f"{label}[{index}]")
    return value


def require_false(value: Any, label: str) -> None:
    require(value is False, f"{label} must be false")


def require_existing_path(root: Path, relative_path: str, label: str) -> Path:
    require_string(relative_path, label)
    require(not Path(relative_path).is_absolute(), f"{label} must be relative: {relative_path}")
    resolved_root = root.resolve()
    resolved_path = (resolved_root / relative_path).resolve()
    require(
        resolved_path == resolved_root or resolved_root in resolved_path.parents,
        f"{label} must stay under project root: {relative_path}",
    )
    require(resolved_path.is_file(), f"{label} does not exist: {relative_path}")
    return resolved_path


def load_json(root: Path, relative_path: str) -> Any:
    path = require_existing_path(root, relative_path, relative_path)
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def load_dashboard(root: Path) -> dict[str, Any]:
    dashboard = load_json(root, DASHBOARD_PATH)
    require(isinstance(dashboard, dict), f"{DASHBOARD_PATH} root must be an object")
    return dashboard


def load_dump_artifact(root: Path, relative_path: str) -> dict[str, Any] | None:
    payload = load_json(root, relative_path)
    if not isinstance(payload, dict):
        return None
    if "dumpId" not in payload or "nonClaims" not in payload:
        return None
    return payload


def load_build_gradle_text(root: Path) -> str:
    return require_existing_path(root, BUILD_GRADLE_PATH, BUILD_GRADLE_PATH).read_text(encoding="utf-8")


def normalize_label(label: str) -> str:
    return re.sub(r"\s+", " ", label.strip().lower())


def validate_source_specs(root: Path, source_specs: Any) -> None:
    require(isinstance(source_specs, list) and source_specs, "sourceSpecs must be a non-empty list")
    for index, row in enumerate(source_specs):
        require(isinstance(row, dict), f"sourceSpecs[{index}] must be an object")
        require_keys(row, SOURCE_SPEC_KEYS, f"sourceSpecs[{index}]")
        require_existing_path(root, row["path"], f"sourceSpecs[{index}].path")
        require_string(row["section"], f"sourceSpecs[{index}].section")


def validate_classifications(classifications: Any) -> None:
    require(isinstance(classifications, list), "classifications must be a list")
    for index, value in enumerate(classifications):
        require_string(value, f"classifications[{index}]")
    missing = [value for value in EXPECTED_CLASSIFICATIONS if value not in classifications]
    require(not missing, f"missing classifications: {missing}")
    require(classifications == EXPECTED_CLASSIFICATIONS, "classifications must be stable and ordered")


def validate_classification_rules(rules: Any) -> None:
    require(isinstance(rules, list), "classificationRules must be a list")
    require([row.get("classification") for row in rules if isinstance(row, dict)] == EXPECTED_CLASSIFICATIONS, "classificationRules must cover every taxonomy value in order")
    for index, row in enumerate(rules):
        require(isinstance(row, dict), f"classificationRules[{index}] must be an object")
        require_keys(row, CLASSIFICATION_RULE_KEYS, f"classificationRules[{index}]")
        classification = require_string(row["classification"], f"classificationRules[{index}].classification")
        require(classification in EXPECTED_CLASSIFICATIONS, f"unknown classification rule: {classification}")
        require_string(row["meaning"], f"{classification}.meaning")
        evidence_kinds = require_string_list(row["minimumEvidenceKinds"], f"{classification}.minimumEvidenceKinds", allow_empty=True)
        unknown = [kind for kind in evidence_kinds if kind not in EXPECTED_EVIDENCE_KINDS]
        require(not unknown, f"{classification}.minimumEvidenceKinds has unknown evidence kinds: {unknown}")
        require_string(row["promotionPolicy"], f"{classification}.promotionPolicy")


def validate_required_evidence_kinds(kinds: Any) -> None:
    require(isinstance(kinds, list), "requiredEvidenceKinds must be a list")
    require([row.get("kind") for row in kinds if isinstance(row, dict)] == EXPECTED_EVIDENCE_KINDS, "requiredEvidenceKinds changed")
    for index, row in enumerate(kinds):
        require(isinstance(row, dict), f"requiredEvidenceKinds[{index}] must be an object")
        require_keys(row, EVIDENCE_KIND_KEYS, f"requiredEvidenceKinds[{index}]")
        require_string(row["kind"], f"requiredEvidenceKinds[{index}].kind")
        field = require_string(row["dashboardField"], f"requiredEvidenceKinds[{index}].dashboardField")
        require(field in EVIDENCE_FIELDS, f"unknown evidence dashboard field: {field}")
        require_string(row["requiredWhen"], f"requiredEvidenceKinds[{index}].requiredWhen")


def validate_target_spec(root: Path, target_spec: Any, label: str) -> None:
    require(isinstance(target_spec, dict), f"{label}.targetSpec must be an object")
    require_keys(target_spec, TARGET_SPEC_KEYS, f"{label}.targetSpec")
    require_existing_path(root, target_spec["path"], f"{label}.targetSpec.path")
    require_string(target_spec["section"], f"{label}.targetSpec.section")


def validate_evidence(root: Path, evidence: Any, label: str) -> dict[str, list[str]]:
    require(isinstance(evidence, dict), f"{label}.evidence must be an object")
    require_keys(evidence, EVIDENCE_FIELDS, f"{label}.evidence")
    validated: dict[str, list[str]] = {}
    for field in EVIDENCE_FIELDS:
        values = require_string_list(evidence[field], f"{label}.evidence.{field}", allow_empty=True)
        for path_index, path in enumerate(values):
            require_existing_path(root, path, f"{label}.evidence.{field}[{path_index}]")
        validated[field] = values
    return validated


def validate_non_claims(non_claims: Any, label: str) -> list[str]:
    values = require_string_list(non_claims, label)
    require("no-complete-target-support-claim" in values, f"{label} must include no-complete-target-support-claim")
    return values


def validate_surface_row(root: Path, row: Any, index: int) -> dict[str, Any]:
    require(isinstance(row, dict), f"surfaceRows[{index}] must be an object")
    require_keys(row, SURFACE_ROW_KEYS, f"surfaceRows[{index}]")
    surface_id = require_string(row["surfaceId"], f"surfaceRows[{index}].surfaceId")
    label = require_string(row["label"], f"{surface_id}.label")
    require(normalize_label(label) not in REQUIRED_GENERIC_LABELS, f"{surface_id} uses a generic label: {label}")

    classification = require_string(row["classification"], f"{surface_id}.classification")
    require(classification in EXPECTED_CLASSIFICATIONS, f"{surface_id} has unknown classification: {classification}")
    require(isinstance(row["gpuClaimed"], bool), f"{surface_id}.gpuClaimed must be boolean")
    require_false(row["claimPromotionAllowed"], f"{surface_id}.claimPromotionAllowed")

    validate_target_spec(root, row["targetSpec"], surface_id)
    evidence = validate_evidence(root, row["evidence"], surface_id)
    gates = require_string_list(row["requiredEvidenceGates"], f"{surface_id}.requiredEvidenceGates", allow_empty=True)
    legacy_gates = require_string_list(row["legacyGates"], f"{surface_id}.legacyGates", allow_empty=True)
    unknown_legacy = [gate for gate in legacy_gates if gate not in REQUIRED_LEGACY_GATES]
    require(not unknown_legacy, f"{surface_id}.legacyGates contains unknown gates: {unknown_legacy}")
    non_claims = validate_non_claims(row["nonClaims"], f"{surface_id}.nonClaims")
    dump_artifacts = [
        (path, payload)
        for path in evidence["deterministicDumps"]
        if path.endswith(".json")
        for payload in [load_dump_artifact(root, path)]
        if payload is not None
    ]
    if surface_id in COHERENT_NON_CLAIM_SURFACES and len(dump_artifacts) == 1:
        dump_path, payload = dump_artifacts[0]
        dump_non_claims = require_string_list(payload["nonClaims"], f"{surface_id} dump nonClaims")
        require(
            dump_non_claims == non_claims,
            f"{surface_id}.nonClaims must match dump nonClaims in {dump_path}",
        )

    if row["gpuClaimed"] and not evidence["gpuArtifacts"]:
        require(classification == "GPU-gated", f"{surface_id}: GPU claimed without GPU artifact must stay GPU-gated")

    if classification == "target-supported":
        require(evidence["fixtureProvenance"], f"{surface_id} target-supported requires fixture provenance")
        require(evidence["deterministicDumps"], f"{surface_id} target-supported requires deterministic dumps")
        require(evidence["cpuOracle"], f"{surface_id} target-supported requires CPU oracle evidence")
        require(evidence["routeDiagnostics"], f"{surface_id} target-supported requires route diagnostics")

    if classification == "current-supported":
        require(evidence["deterministicDumps"], f"{surface_id} current-supported requires deterministic dumps")
        require(evidence["routeDiagnostics"], f"{surface_id} current-supported requires route diagnostics")
        require(gates, f"{surface_id} current-supported requires bounded evidence gates")

    if classification in {"tracked-gap", "DependencyGated", "fixture-gated", "GPU-gated"}:
        require(gates, f"{surface_id} must list requiredEvidenceGates for {classification}")

    if classification == "fixture-gated":
        require(not evidence["fixtureProvenance"], f"{surface_id} fixture-gated row must not hide completed fixture provenance")

    if classification == "drift-only":
        require(not row["gpuClaimed"], f"{surface_id} drift-only row must not claim GPU support")

    return row


def validate_surface_rows(root: Path, rows: Any) -> None:
    require(isinstance(rows, list) and rows, "surfaceRows must be a non-empty list")
    validated = [validate_surface_row(root, row, index) for index, row in enumerate(rows)]
    surface_ids = [row["surfaceId"] for row in validated]
    require(surface_ids == sorted(surface_ids), "surfaceRows must be sorted by surfaceId")
    require(len(surface_ids) == len(set(surface_ids)), "surfaceId values must be unique")
    missing = sorted(REQUIRED_SURFACES - set(surface_ids))
    require(not missing, f"missing surface rows: {missing}")


def validate_negative_generic_labels(rows: Any) -> None:
    require(isinstance(rows, list), "negativeGenericLabels must be a list")
    labels = []
    for index, row in enumerate(rows):
        require(isinstance(row, dict), f"negativeGenericLabels[{index}] must be an object")
        require_keys(row, NEGATIVE_LABEL_KEYS, f"negativeGenericLabels[{index}]")
        label = normalize_label(require_string(row["label"], f"negativeGenericLabels[{index}].label"))
        labels.append(label)
        require(label in REQUIRED_GENERIC_LABELS, f"unexpected generic negative label: {label}")
        diagnostic = require_string(row["diagnosticCode"], f"negativeGenericLabels[{index}].diagnosticCode")
        require(
            diagnostic.startswith("font.claim.") or diagnostic.startswith("text.claim."),
            f"negativeGenericLabels[{index}].diagnosticCode must use claim namespace",
        )
        require(row["classification"] == "tracked-gap", f"{label} negative label must remain tracked-gap")
        require_string(row["replacementRequirement"], f"negativeGenericLabels[{index}].replacementRequirement")
        require_false(row["claimPromotionAllowed"], f"negativeGenericLabels[{index}].claimPromotionAllowed")
    missing = sorted(REQUIRED_GENERIC_LABELS - set(labels))
    require(not missing, f"missing negative generic labels: {missing}")
    require(labels == sorted(labels), "negativeGenericLabels must be sorted by label")


def validate_legacy_gates(rows: Any) -> None:
    require(isinstance(rows, list), "legacyGates must be a list")
    gates = []
    for index, row in enumerate(rows):
        require(isinstance(row, dict), f"legacyGates[{index}] must be an object")
        require_keys(row, LEGACY_GATE_KEYS, f"legacyGates[{index}]")
        gate = require_string(row["gate"], f"legacyGates[{index}].gate")
        gates.append(gate)
        require(row["status"] == "open", f"{gate} must remain open")
        classification = require_string(row["classification"], f"{gate}.classification")
        require(classification in EXPECTED_CLASSIFICATIONS, f"{gate} has unknown classification: {classification}")
        require_string_list(row["targetOwners"], f"{gate}.targetOwners")
        require_string_list(row["requiredRetirementEvidence"], f"{gate}.requiredRetirementEvidence")
        require(row["replacementClaim"] is None, f"{gate}.replacementClaim must remain null")
        require_false(row["claimPromotionAllowed"], f"{gate}.claimPromotionAllowed")
    missing = sorted(REQUIRED_LEGACY_GATES - set(gates))
    require(not missing, f"missing legacy gates: {missing}")
    require(gates == sorted(gates), "legacyGates must be sorted by gate")


def validate_gradle_wiring_evidence(wiring: Any) -> None:
    require(isinstance(wiring, dict), "gradleWiring must be an object")
    require_keys(wiring, GRADLE_WIRING_KEYS, "gradleWiring")
    require(wiring["taskName"] == TASK_NAME, "gradleWiring.taskName changed")
    require(wiring["validatorCommand"] == VALIDATOR_COMMAND, "gradleWiring.validatorCommand changed")
    require(wiring["sceneGateTask"] == "pipelineSceneDashboardGate", "gradleWiring.sceneGateTask changed")
    require(wiring["pmBundleTask"] == "pipelinePmBundle", "gradleWiring.pmBundleTask changed")


def validate_validation_commands(commands: Any) -> None:
    require(
        commands
        == [
            "rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_claim_dashboard.py",
            "rtk python3 scripts/validate_pure_kotlin_text_claim_dashboard.py",
            "rtk ./gradlew --no-daemon validatePureKotlinTextClaimDashboard",
            "rtk git diff --check",
        ],
        "validationCommands changed",
    )


def validate_dashboard_non_claims(non_claims: Any) -> None:
    values = require_string_list(non_claims, "nonClaims")
    joined = "\n".join(values).lower()
    for token in ("rendering", "shaping", "fallback", "sdf", "emoji", "gpu"):
        require(token in joined, f"nonClaims must mention no {token} support claim")
    require("no-complete-target-support-claim" in values, "nonClaims must include no-complete-target-support-claim")


def validate_dashboard(root: Path, dashboard: dict[str, Any]) -> None:
    root = root.resolve()
    require_keys(dashboard, TOP_LEVEL_KEYS, DASHBOARD_PATH)
    require(dashboard["schemaVersion"] == 1, "schemaVersion must be 1")
    require(dashboard["dashboardId"] == "pure-kotlin-text-font-claim-dashboard", "dashboardId changed")
    require(dashboard["ticketId"] == "KFONT-M0-005", "ticketId changed")
    validate_source_specs(root, dashboard["sourceSpecs"])
    require(
        dashboard["supportClaim"] == "validation-dashboard-infrastructure-only",
        "supportClaim must remain validation-dashboard-infrastructure-only",
    )
    require_false(dashboard["claimPromotionAllowed"], "claimPromotionAllowed")
    validate_classifications(dashboard["classifications"])
    validate_classification_rules(dashboard["classificationRules"])
    validate_required_evidence_kinds(dashboard["requiredEvidenceKinds"])
    validate_surface_rows(root, dashboard["surfaceRows"])
    validate_negative_generic_labels(dashboard["negativeGenericLabels"])
    validate_legacy_gates(dashboard["legacyGates"])
    validate_gradle_wiring_evidence(dashboard["gradleWiring"])
    validate_validation_commands(dashboard["validationCommands"])
    validate_dashboard_non_claims(dashboard["nonClaims"])


def is_comment_line(line: str) -> bool:
    return line.strip().startswith("//")


def non_comment_lines(text: str) -> list[str]:
    return [line for line in text.splitlines() if not is_comment_line(line)]


def line_contains(text: str, needle: str) -> bool:
    return any(needle in line for line in non_comment_lines(text))


def exact_line(text: str, expected: str) -> bool:
    return any(line.strip() == expected for line in non_comment_lines(text))


def gradle_block(build_text: str, marker: str) -> str:
    lines = build_text.splitlines()
    start = None
    for index, line in enumerate(lines):
        if marker in line and not is_comment_line(line):
            start = index
            break
    require(start is not None, f"missing Gradle marker: {marker}")

    block: list[str] = []
    depth = 0
    seen_open = False
    for line in lines[start:]:
        block.append(line)
        for char in line:
            if char == "{":
                depth += 1
                seen_open = True
            elif char == "}":
                depth -= 1
                if seen_open and depth == 0:
                    return "\n".join(block)
    fail(f"unterminated Gradle block: {marker}")


def validate_gradle_wiring(build_text: str) -> None:
    require(
        line_contains(build_text, f'tasks.register<Exec>("{TASK_NAME}")'),
        f"missing Gradle task {TASK_NAME}",
    )
    task_block = gradle_block(build_text, f'tasks.register<Exec>("{TASK_NAME}")')
    require(
        exact_line(
            task_block,
            'commandLine("python3", "scripts/validate_pure_kotlin_text_claim_dashboard.py", rootDir.absolutePath)',
        ),
        f"{TASK_NAME} must execute the claim dashboard validator",
    )
    require(line_contains(task_block, f'reports/pure-kotlin-text/font-claim-dashboard.json'), f"{TASK_NAME} must declare dashboard input")

    scene_block = gradle_block(build_text, 'tasks.register("pipelineSceneDashboardGate")')
    require(line_contains(scene_block, f'dependsOn("{TASK_NAME}")'), f"pipelineSceneDashboardGate must depend on {TASK_NAME}")

    pm_block = gradle_block(build_text, 'tasks.register("pipelinePmBundle")')
    require(line_contains(pm_block, f'"{TASK_NAME}"'), f"pipelinePmBundle must depend on {TASK_NAME}")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    try:
        dashboard = load_dashboard(root)
        validate_dashboard(root, dashboard)
        validate_gradle_wiring(load_build_gradle_text(root))
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print(
        "Pure Kotlin text claim dashboard validation passed: "
        f"{len(dashboard['surfaceRows'])} surface rows, "
        f"{len(dashboard['legacyGates'])} legacy gates, "
        f"{len(dashboard['negativeGenericLabels'])} generic-label refusals."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
