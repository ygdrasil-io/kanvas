#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable


MANIFEST_PATH = "reports/pure-kotlin-text/fixture-evidence-manifest.json"
SOURCE_SPEC_PATH = ".upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md"

KNOWN_CLASSIFICATIONS = [
    "target-supported",
    "current-supported",
    "tracked-gap",
    "expected-unsupported",
    "fixture-gated",
    "GPU-gated",
    "drift-only",
]

REQUIRED_FAMILIES = {
    "a8-sdf-artifacts",
    "cff-cff2-scaler",
    "color-glyphs",
    "complex-script-fixture-matrix",
    "emoji",
    "font-source-sfnt",
    "font-source-system-scan",
    "glyph-strike-key",
    "gpu-handoff",
    "latin-gsub-gpos-fixtures",
    "paragraph",
    "paragraph-fixture-goldens",
    "png-bitmap-glyphs",
    "sfnt-malformed-tables",
    "shaping-scripts",
    "svg-glyphs",
    "truetype-scaler",
    "unicode-data-generation",
}

TOP_LEVEL_KEYS = [
    "schemaVersion",
    "manifestId",
    "sourceSpec",
    "supportClaim",
    "oraclePolicy",
    "nonClaims",
    "dashboardClassifications",
    "validationCommand",
    "fixtureFamilies",
]

ROW_KEYS = [
    "familyId",
    "familyName",
    "classification",
    "targetSpec",
    "currentEvidencePaths",
    "requiredEvidenceGates",
    "validationCommands",
    "fixtureCommands",
    "nonClaims",
]

TARGET_SPEC_KEYS = ["path", "section"]
ORACLE_POLICY_KEYS = ["normative", "externalComparisons"]
SOURCE_SPEC_KEYS = ["path", "section"]

GATED_CLASSIFICATIONS = {"fixture-gated", "GPU-gated", "tracked-gap"}
FORBIDDEN_EXTERNAL_ENGINE_TERMS = [
    "harfbuzz",
    "freeType".lower(),
    "fontations",
    "awt",
    "jni",
    "coretext",
    "directwrite",
    "fontconfig",
    "platform shaper",
    "platform shapers",
    "native engine",
    "native engines",
]


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"pure Kotlin text fixture manifest validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def load_manifest(root: Path) -> dict[str, Any]:
    manifest = load_json(root, MANIFEST_PATH)
    require(isinstance(manifest, dict), "manifest root must be an object")
    return manifest


def require_string(value: Any, label: str) -> str:
    require(isinstance(value, str) and value.strip() == value and value, f"{label} must be a non-empty trimmed string")
    return value


def require_string_list(value: Any, label: str, *, allow_empty: bool = False) -> list[str]:
    require(isinstance(value, list), f"{label} must be a list")
    if not allow_empty:
        require(len(value) > 0, f"{label} must be non-empty")
    for index, item in enumerate(value):
        require_string(item, f"{label}[{index}]")
    return value


def require_keys(payload: dict[str, Any], expected: list[str], label: str) -> None:
    require(list(payload.keys()) == expected, f"{label} keys must be stable and ordered: {expected}")


def require_existing_path(root: Path, relative_path: str, label: str) -> None:
    require_string(relative_path, label)
    require(not Path(relative_path).is_absolute(), f"{label} must be relative: {relative_path}")
    resolved_root = root.resolve()
    resolved_path = (resolved_root / relative_path).resolve()
    require(
        resolved_path == resolved_root or resolved_root in resolved_path.parents,
        f"{label} must stay under project root: {relative_path}",
    )
    require(resolved_path.is_file(), f"{label} does not exist: {relative_path}")


def row_allows_external_terms(path: tuple[Any, ...], manifest: dict[str, Any]) -> bool:
    if "nonClaims" in path:
        return True
    if path and path[0] == "fixtureFamilies" and len(path) >= 2 and isinstance(path[1], int):
        rows = manifest.get("fixtureFamilies")
        if isinstance(rows, list) and 0 <= path[1] < len(rows):
            row = rows[path[1]]
            return isinstance(row, dict) and row.get("classification") == "drift-only"
    return False


def walk_strings(value: Any, path: tuple[Any, ...] = ()) -> Iterable[tuple[tuple[Any, ...], str]]:
    if isinstance(value, str):
        yield path, value
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from walk_strings(item, (*path, index))
    elif isinstance(value, dict):
        for key, item in value.items():
            yield from walk_strings(item, (*path, key))


def validate_external_engine_terms(manifest: dict[str, Any]) -> None:
    for path, text in walk_strings(manifest):
        lowered = text.lower()
        for term in FORBIDDEN_EXTERNAL_ENGINE_TERMS:
            pattern = rf"(?<![a-z0-9]){re.escape(term)}(?![a-z0-9])"
            if re.search(pattern, lowered) and not row_allows_external_terms(path, manifest):
                fail(f"external engine term outside drift-only/non-claims at {'.'.join(map(str, path))}: {term}")


def validate_row(root: Path, row: Any, index: int) -> dict[str, Any]:
    require(isinstance(row, dict), f"fixtureFamilies[{index}] must be an object")
    require_keys(row, ROW_KEYS, f"fixtureFamilies[{index}]")

    family_id = require_string(row["familyId"], f"fixtureFamilies[{index}].familyId")
    require_string(row["familyName"], f"{family_id}.familyName")

    classification = require_string(row["classification"], f"{family_id}.classification")
    require(classification in KNOWN_CLASSIFICATIONS, f"{family_id} has unknown classification: {classification}")
    require(classification != "target-supported", f"{family_id} must not be target-supported")

    target_spec = row["targetSpec"]
    require(isinstance(target_spec, dict), f"{family_id}.targetSpec must be an object")
    require_keys(target_spec, TARGET_SPEC_KEYS, f"{family_id}.targetSpec")
    require_existing_path(root, target_spec["path"], f"{family_id}.targetSpec.path")
    require_string(target_spec["section"], f"{family_id}.targetSpec.section")

    current_evidence = require_string_list(row["currentEvidencePaths"], f"{family_id}.currentEvidencePaths")
    for evidence_path in current_evidence:
        require_existing_path(root, evidence_path, f"{family_id}.currentEvidencePaths")

    gates = require_string_list(row["requiredEvidenceGates"], f"{family_id}.requiredEvidenceGates", allow_empty=True)
    if classification in GATED_CLASSIFICATIONS:
        require(gates, f"{family_id} must have non-empty requiredEvidenceGates for gated classification {classification}")

    validation_commands = require_string_list(row["validationCommands"], f"{family_id}.validationCommands", allow_empty=True)
    fixture_commands = require_string_list(row["fixtureCommands"], f"{family_id}.fixtureCommands", allow_empty=True)
    require(validation_commands or fixture_commands, f"{family_id} must include validationCommands or fixtureCommands")

    non_claims = require_string_list(row["nonClaims"], f"{family_id}.nonClaims")
    require(
        "no-complete-target-support-claim" in non_claims,
        f"{family_id}.nonClaims must include no-complete-target-support-claim",
    )
    return row


def validate_manifest(root: Path, manifest: dict[str, Any]) -> None:
    root = root.resolve()
    require_keys(manifest, TOP_LEVEL_KEYS, "manifest")
    require(manifest["schemaVersion"] == 1, "schemaVersion must be 1")
    require(manifest["manifestId"] == "pure-kotlin-text-fixture-evidence-manifest", "manifestId changed")

    source_spec = manifest["sourceSpec"]
    require(isinstance(source_spec, dict), "sourceSpec must be an object")
    require_keys(source_spec, SOURCE_SPEC_KEYS, "sourceSpec")
    require(source_spec["path"] == SOURCE_SPEC_PATH, "sourceSpec.path must point to spec 07")
    require_existing_path(root, source_spec["path"], "sourceSpec.path")
    require(source_spec["section"] == "Target Fixture Manifest", "sourceSpec.section changed")

    require(
        manifest["supportClaim"] == "coordination-evidence-infrastructure-no-support-claim",
        "manifest must be explicit that it is not a support claim",
    )

    oracle_policy = manifest["oraclePolicy"]
    require(isinstance(oracle_policy, dict), "oraclePolicy must be an object")
    require_keys(oracle_policy, ORACLE_POLICY_KEYS, "oraclePolicy")
    require_string(oracle_policy["normative"], "oraclePolicy.normative")
    require_string(oracle_policy["externalComparisons"], "oraclePolicy.externalComparisons")

    require_string_list(manifest["nonClaims"], "nonClaims")
    require(manifest["dashboardClassifications"] == KNOWN_CLASSIFICATIONS, "dashboard classifications must match spec 07 exactly")
    require(manifest["validationCommand"] == "rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py", "validationCommand changed")

    rows = manifest["fixtureFamilies"]
    require(isinstance(rows, list) and rows, "fixtureFamilies must be a non-empty list")
    validated_rows = [validate_row(root, row, index) for index, row in enumerate(rows)]
    family_ids = [row["familyId"] for row in validated_rows]
    require(len(family_ids) == len(set(family_ids)), "familyId values must be unique")
    require(family_ids == sorted(family_ids), "familyId values must be sorted")
    require(REQUIRED_FAMILIES.issubset(set(family_ids)), f"missing required fixture families: {sorted(REQUIRED_FAMILIES - set(family_ids))}")

    validate_external_engine_terms(manifest)


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    try:
        manifest = load_manifest(root)
        validate_manifest(root, manifest)
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    rows = manifest["fixtureFamilies"]
    print(
        "Pure Kotlin text fixture manifest validation passed: "
        f"{len(rows)} fixture families, "
        f"{sum(1 for row in rows if row['classification'] == 'target-supported')} target-supported rows."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
