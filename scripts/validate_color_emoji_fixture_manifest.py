#!/usr/bin/env python3
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


MANIFEST_PATH = "reports/font/fixtures/expected/color/color-emoji-fixture-manifest.json"
PROVENANCE_INDEX_PATH = "reports/font/fixtures/provenance/index.json"

TOP_LEVEL_KEYS = [
    "schemaVersion",
    "dumpId",
    "ownerTickets",
    "fixtureFamilies",
    "legacyGates",
    "componentDumps",
    "legacyGateCoverage",
    "families",
    "rebaselinePolicy",
    "nonClaims",
]
COMPONENT_DUMP_KEYS = ["dumpId", "path", "bodySha256"]
LEGACY_GATE_COVERAGE_KEYS = ["gate", "familyIds", "gpuEvidenceRequired", "remainingEvidence"]
FAMILY_KEYS = [
    "familyId",
    "coveredTickets",
    "claimClassification",
    "fixtureIds",
    "expectedDumpFiles",
    "expectedDiagnostics",
    "legacyGates",
    "gpuEvidenceRequired",
    "remainingGate",
    "provenance",
]
PROVENANCE_KEYS = ["kind", "source", "licenseNotes", "sourceHashes", "generatedRecipe"]
REBASELINE_KEYS = ["ordinaryTestRuns", "reviewRequirement", "reasonRequired"]

EXPECTED_FAMILIES = ["color-glyphs", "emoji", "png-bitmap-glyphs", "svg-glyphs"]
EXPECTED_GATES = ["coloremoji_blendmodes", "scaledemoji", "scaledemoji_rendering"]
EXPECTED_COMPONENT_DUMPS = [
    ("bitmap-glyph-plan", "reports/font/fixtures/expected/color/bitmap-glyph-plan.json"),
    ("color-glyph-composite-plan", "reports/font/fixtures/expected/color/color-glyph-composite-plan.json"),
    ("color-glyph-plan", "reports/font/fixtures/expected/color/color-glyph-plan.json"),
    ("color-svg-emoji-goldens", "reports/font/fixtures/expected/color/color-svg-emoji-goldens.json"),
    ("colrv1-fixture-manifest", "reports/font/fixtures/expected/color/colrv1-fixture-manifest.json"),
    ("colrv1-paint-graph", "reports/font/fixtures/expected/color/colrv1-paint-graph.json"),
    ("emoji-route-trace", "reports/font/fixtures/expected/color/emoji-route-trace.json"),
    ("svg-glyph-fixture-manifest", "reports/font/fixtures/expected/color/svg-glyph-fixture-manifest.json"),
    ("svg-glyph-plan", "reports/font/fixtures/expected/color/svg-glyph-plan.json"),
]
EXPECTED_FAMILY_TICKETS = {
    "color-glyphs": ["KFONT-M10-001", "KFONT-M10-002", "KFONT-M10-003", "KFONT-M10-004", "KFONT-M10-005"],
    "emoji": ["KFONT-M10-009"],
    "png-bitmap-glyphs": ["KFONT-M10-006"],
    "svg-glyphs": ["KFONT-M10-007", "KFONT-M10-008"],
}
EXPECTED_FAMILY_COUNTS = {
    "color-glyphs": 9,
    "emoji": 8,
    "png-bitmap-glyphs": 5,
    "svg-glyphs": 16,
}
EXPECTED_FAMILY_GATES = {
    "color-glyphs": ["coloremoji_blendmodes"],
    "emoji": ["scaledemoji"],
    "png-bitmap-glyphs": ["scaledemoji_rendering"],
    "svg-glyphs": ["scaledemoji_rendering"],
}
EXPECTED_GATE_FAMILIES = {
    "coloremoji_blendmodes": ["color-glyphs", "emoji"],
    "scaledemoji": ["emoji"],
    "scaledemoji_rendering": ["emoji", "png-bitmap-glyphs", "svg-glyphs"],
}
EXPECTED_NON_CLAIMS = [
    "no-complete-target-support-claim",
    "no-complete-colrv1-rendering-claim",
    "no-complete-png-bitmap-glyph-routing-claim",
    "no-complete-svg-in-opentype-rendering-claim",
    "no-complete-emoji-sequence-shaping-claim",
    "no-gpu-color-glyph-support-claim",
    "no-platform-color-font-fallback-claim",
]
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"color emoji fixture manifest validation failed: {message}")


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


def require_bool(value: Any, label: str) -> bool:
    require(isinstance(value, bool), f"{label} must be a boolean")
    return value


def require_existing_file(root: Path, relative_path: str, label: str) -> Path:
    path = (root / relative_path).resolve()
    require(path.is_file(), f"{label} missing file: {relative_path}")
    return path


def load_json(root: Path, relative_path: str) -> Any:
    path = require_existing_file(root, relative_path, relative_path)
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def canonical_dump_body_sha256(path: Path) -> str:
    body = re.sub(
        r',\n  "dumpSha256": "[0-9a-f]{64}"\n}\n$',
        "\n}\n",
        path.read_text(encoding="utf-8"),
    )
    return hashlib.sha256(body.encode("utf-8")).hexdigest()


def provenance_hashes(root: Path) -> set[str]:
    index = load_json(root, PROVENANCE_INDEX_PATH)
    require(isinstance(index, dict), "provenance index root must be an object")
    hashes: set[str] = set()
    for fixture in index.get("fixtures", []):
        if not isinstance(fixture, dict):
            continue
        for asset in fixture.get("assets", []):
            if isinstance(asset, dict) and isinstance(asset.get("sha256"), str):
                hashes.add(asset["sha256"])
    return hashes


def validate_component_dumps(root: Path, component_dumps: Any) -> None:
    require(isinstance(component_dumps, list), "componentDumps must be a list")
    observed: list[tuple[str, str]] = []
    for index, component in enumerate(component_dumps):
        require(isinstance(component, dict), f"componentDumps[{index}] must be an object")
        require_keys(component, COMPONENT_DUMP_KEYS, f"componentDumps[{index}]")
        dump_id = require_string(component["dumpId"], f"componentDumps[{index}].dumpId")
        path = require_string(component["path"], f"componentDumps[{index}].path")
        body_hash = require_string(component["bodySha256"], f"componentDumps[{index}].bodySha256")
        require(SHA256_RE.match(body_hash) is not None, f"{dump_id}.bodySha256 must be lowercase SHA-256")
        dump_path = require_existing_file(root, path, dump_id)
        require(body_hash == canonical_dump_body_sha256(dump_path), f"{dump_id}.bodySha256 does not match {path}")
        observed.append((dump_id, path))
    require(observed == EXPECTED_COMPONENT_DUMPS, "componentDumps changed")


def validate_legacy_gate_coverage(coverage: Any) -> None:
    require(isinstance(coverage, list), "legacyGateCoverage must be a list")
    observed_gates: list[str] = []
    for gate in coverage:
        require(isinstance(gate, dict), "legacyGateCoverage rows must be objects")
        require_keys(gate, LEGACY_GATE_COVERAGE_KEYS, "legacyGateCoverage")
        gate_id = require_string(gate["gate"], "legacyGateCoverage.gate")
        require(gate_id in EXPECTED_GATES, f"legacy gate id unknown: {gate_id}")
        observed_gates.append(gate_id)
        require(gate["familyIds"] == EXPECTED_GATE_FAMILIES[gate_id], f"{gate_id}.familyIds changed")
        require_bool(gate["gpuEvidenceRequired"], f"{gate_id}.gpuEvidenceRequired")
        require_string(gate["remainingEvidence"], f"{gate_id}.remainingEvidence")
    require(observed_gates == EXPECTED_GATES, "legacyGateCoverage order changed")


def validate_family(root: Path, family: Any, known_source_hashes: set[str]) -> None:
    require(isinstance(family, dict), "families rows must be objects")
    require_keys(family, FAMILY_KEYS, "family")
    family_id = require_string(family["familyId"], "family.familyId")
    require(family_id in EXPECTED_FAMILIES, f"familyId is unknown: {family_id}")
    require(family["coveredTickets"] == EXPECTED_FAMILY_TICKETS[family_id], f"{family_id}.coveredTickets changed")
    require_string(family["claimClassification"], f"{family_id}.claimClassification")
    fixture_ids = require_string_list(family["fixtureIds"], f"{family_id}.fixtureIds")
    require(len(fixture_ids) == EXPECTED_FAMILY_COUNTS[family_id], f"{family_id}.fixtureIds count changed")
    require(len(fixture_ids) == len(set(fixture_ids)), f"{family_id}.fixtureIds contains duplicates")
    for expected_dump_file in require_string_list(family["expectedDumpFiles"], f"{family_id}.expectedDumpFiles"):
        require_existing_file(root, expected_dump_file, f"{family_id}.expectedDumpFiles")
    require_string_list(family["expectedDiagnostics"], f"{family_id}.expectedDiagnostics", allow_empty=True)
    require(family["legacyGates"] == EXPECTED_FAMILY_GATES[family_id], f"{family_id}.legacyGates changed")
    require_bool(family["gpuEvidenceRequired"], f"{family_id}.gpuEvidenceRequired")
    require_string(family["remainingGate"], f"{family_id}.remainingGate")

    provenance = family["provenance"]
    require(isinstance(provenance, dict), f"{family_id}.provenance must be an object")
    require_keys(provenance, PROVENANCE_KEYS, f"{family_id}.provenance")
    require_string(provenance["kind"], f"{family_id}.provenance.kind")
    require_string(provenance["source"], f"{family_id}.provenance.source")
    require_string_list(provenance["licenseNotes"], f"{family_id}.provenance.licenseNotes")
    source_hashes = require_string_list(provenance["sourceHashes"], f"{family_id}.provenance.sourceHashes", allow_empty=True)
    for source_hash in source_hashes:
        require(SHA256_RE.match(source_hash) is not None, f"{family_id}.provenance.sourceHashes must be lowercase SHA-256")
        require(source_hash in known_source_hashes, f"{family_id}.provenance.sourceHashes references unknown provenance asset hash")
    require_string(provenance["generatedRecipe"], f"{family_id}.provenance.generatedRecipe")


def validate_families(root: Path, families: Any) -> None:
    require(isinstance(families, list), "families must be a list")
    observed = [family.get("familyId") if isinstance(family, dict) else None for family in families]
    require(observed == EXPECTED_FAMILIES, "families order changed")
    known_source_hashes = provenance_hashes(root)
    for family in families:
        validate_family(root, family, known_source_hashes)


def validate_manifest(root: Path, manifest: dict[str, Any]) -> None:
    require_keys(manifest, TOP_LEVEL_KEYS, "manifest")
    require(manifest["schemaVersion"] == 1, "schemaVersion must be 1")
    require(manifest["dumpId"] == "color-emoji-fixture-manifest", "dumpId changed")
    require(manifest["ownerTickets"] == ["KFONT-M10-010"], "ownerTickets changed")
    require(manifest["fixtureFamilies"] == EXPECTED_FAMILIES, "fixtureFamilies must be stable")
    require(manifest["legacyGates"] == EXPECTED_GATES, "legacyGates must be stable")
    validate_component_dumps(root, manifest["componentDumps"])
    validate_legacy_gate_coverage(manifest["legacyGateCoverage"])
    validate_families(root, manifest["families"])

    rebaseline = manifest["rebaselinePolicy"]
    require(isinstance(rebaseline, dict), "rebaselinePolicy must be an object")
    require_keys(rebaseline, REBASELINE_KEYS, "rebaselinePolicy")
    require(rebaseline["ordinaryTestRuns"] == "must-not-overwrite-goldens", "rebaseline ordinaryTestRuns changed")
    require_string(rebaseline["reviewRequirement"], "rebaselinePolicy.reviewRequirement")
    require_string(rebaseline["reasonRequired"], "rebaselinePolicy.reasonRequired")

    non_claims = require_string_list(manifest["nonClaims"], "nonClaims")
    require(non_claims == EXPECTED_NON_CLAIMS, "nonClaims changed")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    try:
        manifest = load_json(root, MANIFEST_PATH)
        require(isinstance(manifest, dict), "manifest root must be an object")
        validate_manifest(root, manifest)
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print("Color emoji fixture manifest validation passed: 4 fixture families, 9 component dumps.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
