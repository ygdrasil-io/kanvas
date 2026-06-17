#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


INVENTORY_PATH = "reports/pure-kotlin-text/font-fixture-inventory.json"

TOP_LEVEL_KEYS = [
    "schemaVersion",
    "inventoryId",
    "scope",
    "sourceManifest",
    "oraclePolicy",
    "families",
]

SOURCE_MANIFEST_KEYS = ["path", "section"]
ORACLE_POLICY_KEYS = ["normative", "nonNormative"]
FAMILY_KEYS = ["familyId", "targetSpec", "fixtures"]
TARGET_SPEC_KEYS = ["path", "section"]
FIXTURE_KEYS = [
    "fixtureId",
    "targetGate",
    "status",
    "fixtureKind",
    "generationPolicy",
    "evidencePaths",
    "expectedArtifacts",
    "nonClaims",
]

KNOWN_STATUSES = {
    "current-positive-evidence",
    "current-refusal-evidence",
    "fixture-specified",
    "implementation-gated",
}

KNOWN_FIXTURE_KINDS = {
    "synthetic-font-bytes",
    "synthetic-table-bytes",
    "structured-dump",
    "route-diagnostic",
    "generated-test-data",
}

REQUIRED_FONT_FAMILY_GATES = {
    "a8-sdf-artifacts": [
        "a8-atlas-pack",
        "atlas-capacity-refusal",
        "cache-budget-refusal",
        "cache-inventory",
        "cache-telemetry",
        "sdf-normalization",
        "sdf-transform-refusal",
        "stale-generation-refusal",
    ],
    "cff-cff2-scaler": [
        "cff-type2-line-curve-flex",
        "cff-local-global-subroutines",
        "cff-subroutine-safety",
        "cff-malformed-stack-refusal",
        "cff-path-output",
        "cff-unsupported-operator-refusal",
        "cff2-blend-vsindex",
        "cff2-variation-store-region",
        "cff2-variation-path-output",
        "cff-cff2-malformed-index-dict-refusal",
        "selected-face-cff-cff2-provenance-dump",
    ],
    "color-glyphs": [
        "colrv0-layer",
        "colrv1-budget-refusal",
        "colrv1-composite-clip",
        "colrv1-cycle-refusal",
        "colrv1-gradient-operation-group",
        "colrv1-solid-glyph-colr-glyph",
        "colrv1-solid-gradient-transform",
    ],
    "emoji": [
        "emoji-vs15-vs16",
        "emoji-skin-tone",
        "emoji-zwj-family",
        "emoji-fallback-unavailable-refusal",
        "emoji-color-glyph-unavailable-refusal",
    ],
    "font-source-sfnt": [
        "single-ttf-provenance",
        "ttc-face-index-provenance",
        "malformed-required-table-diagnostic",
        "malformed-optional-table-diagnostic",
        "system-scan-skipped-file-diagnostic",
    ],
    "png-bitmap-glyphs": [
        "cbdt-cblc-png",
        "sbix-png",
        "unavailable-strike-refusal",
        "malformed-png-refusal",
        "non-png-payload-refusal",
    ],
    "svg-glyphs": [
        "svg-static-path",
        "svg-gradient-transform-clip",
        "svg-use-recursion-refusal",
        "svg-external-resource-refusal",
        "svg-unsupported-feature-refusal",
    ],
    "truetype-scaler": [
        "truetype-simple-glyph",
        "truetype-composite-glyph-transform",
        "truetype-gvar-simple-delta",
        "truetype-gvar-composite-delta",
        "truetype-gvar-iup",
        "truetype-vertical-metrics",
        "truetype-malformed-glyf-isolation",
        "truetype-avar-coordinate-mapping",
    ],
}

FORBIDDEN_FAMILIES = {"gpu-handoff", "paragraph", "shaping-scripts"}
FORBIDDEN_EXTERNAL_ENGINE_TERMS = [
    "harfbuzz",
    "freetype",
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
    raise ValidationError(f"pure Kotlin text font fixture inventory validation failed: {message}")


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


def load_inventory(root: Path) -> dict[str, Any]:
    inventory = load_json(root, INVENTORY_PATH)
    require(isinstance(inventory, dict), "inventory root must be an object")
    return inventory


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


def walk_strings(value: Any) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, list):
        result: list[str] = []
        for item in value:
            result.extend(walk_strings(item))
        return result
    if isinstance(value, dict):
        result = []
        for item in value.values():
            result.extend(walk_strings(item))
        return result
    return []


def validate_no_external_oracle_terms(payload: dict[str, Any]) -> None:
    text = "\n".join(walk_strings(payload)).lower()
    for term in FORBIDDEN_EXTERNAL_ENGINE_TERMS:
        require(term not in text, f"external engine term is not allowed in font fixture inventory: {term}")


def validate_fixture(root: Path, fixture: Any, family_id: str, index: int) -> dict[str, Any]:
    require(isinstance(fixture, dict), f"{family_id}.fixtures[{index}] must be an object")
    require_keys(fixture, FIXTURE_KEYS, f"{family_id}.fixtures[{index}]")

    fixture_id = require_string(fixture["fixtureId"], f"{family_id}.fixtures[{index}].fixtureId")
    target_gate = require_string(fixture["targetGate"], f"{fixture_id}.targetGate")
    require(target_gate in REQUIRED_FONT_FAMILY_GATES[family_id], f"{fixture_id} has unknown targetGate: {target_gate}")

    status = require_string(fixture["status"], f"{fixture_id}.status")
    require(status in KNOWN_STATUSES, f"{fixture_id} has unknown status: {status}")
    require(status != "target-supported", f"{fixture_id} must not use target-supported as a fixture status")

    fixture_kind = require_string(fixture["fixtureKind"], f"{fixture_id}.fixtureKind")
    require(fixture_kind in KNOWN_FIXTURE_KINDS, f"{fixture_id} has unknown fixtureKind: {fixture_kind}")
    require_string(fixture["generationPolicy"], f"{fixture_id}.generationPolicy")

    evidence_paths = require_string_list(fixture["evidencePaths"], f"{fixture_id}.evidencePaths")
    for evidence_path in evidence_paths:
        require_existing_path(root, evidence_path, f"{fixture_id}.evidencePaths")

    require_string_list(fixture["expectedArtifacts"], f"{fixture_id}.expectedArtifacts")
    non_claims = require_string_list(fixture["nonClaims"], f"{fixture_id}.nonClaims")
    require("no-complete-target-support-claim" in non_claims, f"{fixture_id}.nonClaims must include no-complete-target-support-claim")
    return fixture


def validate_family(root: Path, family: Any, index: int) -> dict[str, Any]:
    require(isinstance(family, dict), f"families[{index}] must be an object")
    require_keys(family, FAMILY_KEYS, f"families[{index}]")

    family_id = require_string(family["familyId"], f"families[{index}].familyId")
    require(family_id in REQUIRED_FONT_FAMILY_GATES, f"unknown font fixture family: {family_id}")
    require(family_id not in FORBIDDEN_FAMILIES, f"forbidden non-font fixture family: {family_id}")

    target_spec = family["targetSpec"]
    require(isinstance(target_spec, dict), f"{family_id}.targetSpec must be an object")
    require_keys(target_spec, TARGET_SPEC_KEYS, f"{family_id}.targetSpec")
    require_existing_path(root, target_spec["path"], f"{family_id}.targetSpec.path")
    require_string(target_spec["section"], f"{family_id}.targetSpec.section")

    fixtures = family["fixtures"]
    require(isinstance(fixtures, list) and fixtures, f"{family_id}.fixtures must be a non-empty list")
    validated = [validate_fixture(root, fixture, family_id, fixture_index) for fixture_index, fixture in enumerate(fixtures)]
    fixture_ids = [fixture["fixtureId"] for fixture in validated]
    require(fixture_ids == sorted(fixture_ids), f"{family_id}.fixtures must be sorted by fixtureId")
    require(len(fixture_ids) == len(set(fixture_ids)), f"{family_id}.fixtureId values must be unique")

    gates = [fixture["targetGate"] for fixture in validated]
    missing = sorted(set(REQUIRED_FONT_FAMILY_GATES[family_id]) - set(gates))
    extra = sorted(set(gates) - set(REQUIRED_FONT_FAMILY_GATES[family_id]))
    require(not missing, f"{family_id} missing fixture gates: {missing}")
    require(not extra, f"{family_id} has extra fixture gates: {extra}")
    return family


def validate_inventory(root: Path, inventory: dict[str, Any]) -> None:
    root = root.resolve()
    require_keys(inventory, TOP_LEVEL_KEYS, "inventory")
    require(inventory["schemaVersion"] == 1, "schemaVersion must be 1")
    require(inventory["inventoryId"] == "pure-kotlin-text-font-fixture-inventory", "inventoryId changed")
    require(inventory["scope"] == "font-only-no-gpu-handoff", "scope must exclude GPU handoff")

    source_manifest = inventory["sourceManifest"]
    require(isinstance(source_manifest, dict), "sourceManifest must be an object")
    require_keys(source_manifest, SOURCE_MANIFEST_KEYS, "sourceManifest")
    require(source_manifest["path"] == "reports/pure-kotlin-text/fixture-evidence-manifest.json", "sourceManifest.path changed")
    require_existing_path(root, source_manifest["path"], "sourceManifest.path")
    require_string(source_manifest["section"], "sourceManifest.section")

    oracle_policy = inventory["oraclePolicy"]
    require(isinstance(oracle_policy, dict), "oraclePolicy must be an object")
    require_keys(oracle_policy, ORACLE_POLICY_KEYS, "oraclePolicy")
    require_string(oracle_policy["normative"], "oraclePolicy.normative")
    require_string(oracle_policy["nonNormative"], "oraclePolicy.nonNormative")

    families = inventory["families"]
    require(isinstance(families, list) and families, "families must be a non-empty list")
    validated = [validate_family(root, family, index) for index, family in enumerate(families)]
    family_ids = [family["familyId"] for family in validated]
    require(family_ids == sorted(family_ids), "families must be sorted by familyId")
    require(family_ids == sorted(REQUIRED_FONT_FAMILY_GATES), "families must cover exactly the font fixture families")
    require(not (set(family_ids) & FORBIDDEN_FAMILIES), "inventory includes non-font fixture families")

    validate_no_external_oracle_terms(inventory)


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    try:
        inventory = load_inventory(root)
        validate_inventory(root, inventory)
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    fixture_count = sum(len(family["fixtures"]) for family in inventory["families"])
    print(
        "Pure Kotlin text font fixture inventory validation passed: "
        f"{len(inventory['families'])} font families, {fixture_count} fixtures, 0 GPU handoff rows."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
