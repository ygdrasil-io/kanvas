#!/usr/bin/env python3
"""Validates reports/pure-kotlin-text/golden-update-policy.json."""

import json
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
POLICY_PATH = PROJECT_ROOT / "reports" / "pure-kotlin-text" / "golden-update-policy.json"
DUMP_INDEX_PATH = PROJECT_ROOT / "reports" / "pure-kotlin-text" / "dump-evidence-index.json"
FIXTURE_MANIFEST_PATH = (
    PROJECT_ROOT / "reports" / "pure-kotlin-text" / "fixture-evidence-manifest.json"
)

REQUIRED_POLICY_KEYS = [
    "schemaVersion",
    "policyId",
    "sourceSpec",
    "supportClaim",
    "ordinaryTestRuns",
    "reviewRequirement",
    "rebaselineApproval",
    "oraclePolicy",
    "driftLabels",
    "goldenTypes",
    "nonClaims",
    "validationCommand",
]

REQUIRED_ORACLE_KEYS = ["normative", "externalComparisons", "forbiddenEngines"]

REQUIRED_DRIFT_KEYS = ["purpose", "allowedDriftLabels", "driftLabelPolicy"]

REQUIRED_GOLDEN_TYPE_KEYS = ["description", "updatePolicy", "reviewRequired"]


class ValidationError(Exception):
    pass


def require(condition: object, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def require_type(value: object, expected_type: type, path: str) -> None:
    require(
        isinstance(value, expected_type),
        f"{path} must be {expected_type.__name__}, got {type(value).__name__}",
    )


def require_string(value: object, path: str) -> str:
    require_type(value, str, path)
    require(value, f"{path} must not be empty")
    return value


def require_list_of_strings(values: object, path: str) -> None:
    require_type(values, list, path)
    for index, value in enumerate(values):
        require_string(value, f"{path}[{index}]")


def load_policy() -> dict:
    require(POLICY_PATH.is_file(), f"Policy file not found: {POLICY_PATH}")
    return json.loads(POLICY_PATH.read_text(encoding="utf-8"))


def load_dump_index() -> dict:
    require(DUMP_INDEX_PATH.is_file(), f"Dump index not found: {DUMP_INDEX_PATH}")
    return json.loads(DUMP_INDEX_PATH.read_text(encoding="utf-8"))


def load_fixture_manifest() -> dict:
    require(
        FIXTURE_MANIFEST_PATH.is_file(),
        f"Fixture manifest not found: {FIXTURE_MANIFEST_PATH}",
    )
    return json.loads(FIXTURE_MANIFEST_PATH.read_text(encoding="utf-8"))


def validate_policy_structure(policy: dict) -> None:
    for key in REQUIRED_POLICY_KEYS:
        require(key in policy, f"Missing required policy key: {key}")

    require_type(policy["schemaVersion"], int, "schemaVersion")
    require_string(policy["policyId"], "policyId")
    require_string(policy["supportClaim"], "supportClaim")
    require_string(policy["ordinaryTestRuns"], "ordinaryTestRuns")
    require_string(policy["reviewRequirement"], "reviewRequirement")
    require_string(policy["rebaselineApproval"], "rebaselineApproval")
    require_string(policy["validationCommand"], "validationCommand")

    require_type(policy["nonClaims"], list, "nonClaims")
    require(policy["nonClaims"], "nonClaims must not be empty")


def validate_oracle_policy(policy: dict) -> None:
    oracle = policy.get("oraclePolicy", {})
    for key in REQUIRED_ORACLE_KEYS:
        require(key in oracle, f"Missing required oraclePolicy key: {key}")

    require_string(oracle["normative"], "oraclePolicy.normative")
    require_string(
        oracle["externalComparisons"], "oraclePolicy.externalComparisons"
    )
    require_list_of_strings(
        oracle["forbiddenEngines"], "oraclePolicy.forbiddenEngines"
    )


def validate_drift_labels(policy: dict) -> None:
    drift = policy.get("driftLabels", {})
    for key in REQUIRED_DRIFT_KEYS:
        require(key in drift, f"Missing required driftLabels key: {key}")

    require_string(drift["purpose"], "driftLabels.purpose")
    require_list_of_strings(
        drift["allowedDriftLabels"], "driftLabels.allowedDriftLabels"
    )
    require_string(
        drift["driftLabelPolicy"], "driftLabels.driftLabelPolicy"
    )


def validate_golden_types(policy: dict) -> None:
    types = policy.get("goldenTypes", {})
    require(types, "goldenTypes must not be empty")

    for type_name, type_def in types.items():
        type_path = f"goldenTypes.{type_name}"
        for key in REQUIRED_GOLDEN_TYPE_KEYS:
            require(
                key in type_def, f"Missing required key {key} in {type_path}"
            )
        require_string(type_def["description"], f"{type_path}.description")
        require_string(type_def["updatePolicy"], f"{type_path}.updatePolicy")
        require_type(type_def["reviewRequired"], bool, f"{type_path}.reviewRequired")


def validate_consistency_with_dump_index(policy: dict, dump_index: dict) -> None:
    dump_policy = dump_index.get("goldenUpdatePolicy", {})
    if dump_policy:
        require(
            policy["ordinaryTestRuns"] == dump_policy.get("ordinaryTestRuns"),
            "ordinaryTestRuns mismatch between policy and dump-index",
        )
        require(
            policy["reviewRequirement"] == dump_policy.get("reviewRequirement"),
            "reviewRequirement mismatch between policy and dump-index",
        )


def validate_consistency_with_fixture_manifest(policy: dict, manifest: dict) -> None:
    manifest_oracle = manifest.get("oraclePolicy", {})
    policy_oracle = policy.get("oraclePolicy", {})

    if manifest_oracle and policy_oracle:
        require(
            policy_oracle.get("normative") == manifest_oracle.get("normative"),
            "oraclePolicy.normative mismatch between policy and fixture-manifest",
        )


def validate_dump_rows_comply(policy: dict, dump_index: dict) -> None:
    known_types = set(policy.get("goldenTypes", {}).keys())
    rows = dump_index.get("dumpRows", [])

    for row in rows:
        classification = row.get("classification", "")
        require(
            classification in known_types,
            f"Dump row '{row.get('dumpId')}' has unknown classification '{classification}'. "
            f"Known types: {known_types}",
        )

        if classification == "golden-gated":
            require(
                "expectedFields" in row,
                f"Golden-gated dump '{row.get('dumpId')}' must have expectedFields",
            )
            require(
                "updatePolicy" in row,
                f"Golden-gated dump '{row.get('dumpId')}' must have updatePolicy",
            )


def validate() -> None:
    policy = load_policy()
    dump_index = load_dump_index()
    manifest = load_fixture_manifest()

    validate_policy_structure(policy)
    validate_oracle_policy(policy)
    validate_drift_labels(policy)
    validate_golden_types(policy)
    validate_consistency_with_dump_index(policy, dump_index)
    validate_consistency_with_fixture_manifest(policy, manifest)
    validate_dump_rows_comply(policy, dump_index)

    print(f"Golden update policy validation passed: {policy['policyId']}")


if __name__ == "__main__":
    try:
        validate()
    except ValidationError as e:
        print(f"Golden update policy validation failed: {e}")
        exit(1)
