#!/usr/bin/env python3
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


MANIFEST_PATH = "reports/font/fixtures/expected/color/color-emoji-fixture-manifest.json"
PROVENANCE_INDEX_PATH = "reports/font/fixtures/provenance/index.json"
EXPECTED_DUMP_PATH = "reports/font/fixtures/expected/color/color-emoji-fixture-manifest.json"

TOP_LEVEL_KEYS = [
    "schemaVersion",
    "dumpId",
    "ownerTickets",
    "fixtureFamilies",
    "legacyGates",
    "rebaselinePolicy",
    "cases",
    "nonClaims",
]
LEGACY_GATE_KEYS = ["gateId", "fixtureIds", "gpuEvidenceRequired", "remainingEvidence"]
REBASELINE_KEYS = ["ordinaryTestRuns", "reviewRequirement", "linkedDumpDiffs", "autoOverwritePolicy"]
CASE_KEYS = [
    "fixtureId",
    "family",
    "fontSourceId",
    "target",
    "provenance",
    "expectedRoute",
    "expectedDiagnostics",
    "expectedDumpFiles",
    "legacyGates",
    "gpuEvidenceRequired",
    "remainingEvidence",
]
TARGET_KEYS = ["kind", "glyphIds", "textSequence"]
PROVENANCE_KEYS = ["kind", "source", "licenseNote", "sourceSha256", "generatedSourceRecipe"]

EXPECTED_FAMILIES = ["color-glyphs", "emoji", "png-bitmap-glyphs", "svg-glyphs"]
KNOWN_GATES = {"coloremoji_blendmodes", "scaledemoji", "scaledemoji_rendering"}
KNOWN_FONT_SOURCE_IDS = {
    "color-colrv1-test-glyphs",
    "emoji-sequence-generated-test-data",
    "fallback-emoji-noto-color",
    "synthetic-color-glyph-fixtures",
    "synthetic-svg-glyph-documents",
}
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")

EXPECTED_FIXTURE_IDS = {
    "color-glyphs-colrv0-layered-palette-override",
    "color-glyphs-colrv1-composite-clip-bounds",
    "color-glyphs-colrv1-cycle-refusal",
    "color-glyphs-colrv1-expanded-paint-count-refusal",
    "color-glyphs-colrv1-gradient-operation-group",
    "color-glyphs-colrv1-malformed-offset-refusal",
    "color-glyphs-colrv1-recursion-depth-refusal",
    "color-glyphs-colrv1-solid-glyph-colr-glyph-bounds",
    "color-glyphs-colrv1-transform-bounds",
    "emoji-color-glyph-unavailable",
    "emoji-fallback-unavailable",
    "emoji-flag-svg",
    "emoji-keycap-png",
    "emoji-role-skin-tone-colr",
    "emoji-skin-tone-bitmap",
    "emoji-unsupported-sequence",
    "emoji-variation-selector-colr",
    "emoji-zwj-outline-fallback",
    "png-bitmap-glyphs-cbdt-cblc-png",
    "png-bitmap-glyphs-malformed-png-refusal",
    "png-bitmap-glyphs-non-png-payload-refusal",
    "png-bitmap-glyphs-sbix-png",
    "png-bitmap-glyphs-unavailable-strike-refusal",
    "svg-glyphs-svg-animation-refusal",
    "svg-glyphs-svg-defs-symbol-use-radial-gradient",
    "svg-glyphs-svg-embedded-text-refusal",
    "svg-glyphs-svg-external-resource-refusal",
    "svg-glyphs-svg-filter-refusal",
    "svg-glyphs-svg-foreign-object-refusal",
    "svg-glyphs-svg-gradient-stop-budget-refusal",
    "svg-glyphs-svg-gradient-transform-clip",
    "svg-glyphs-svg-malformed-document-refusal",
    "svg-glyphs-svg-malformed-path-data-refusal",
    "svg-glyphs-svg-network-reference-refusal",
    "svg-glyphs-svg-path-command-budget-refusal",
    "svg-glyphs-svg-script-refusal",
    "svg-glyphs-svg-static-path",
    "svg-glyphs-svg-unsupported-css-selector-refusal",
    "svg-glyphs-svg-use-recursion-refusal",
}
EXPECTED_GATE_FIXTURES = {
    "coloremoji_blendmodes": {"color-glyphs-colrv1-composite-clip-bounds"},
    "scaledemoji": {
        "emoji-color-glyph-unavailable",
        "emoji-fallback-unavailable",
        "emoji-flag-svg",
        "emoji-keycap-png",
        "emoji-role-skin-tone-colr",
        "emoji-skin-tone-bitmap",
        "emoji-unsupported-sequence",
        "emoji-variation-selector-colr",
        "emoji-zwj-outline-fallback",
    },
    "scaledemoji_rendering": {
        "emoji-flag-svg",
        "emoji-keycap-png",
        "emoji-skin-tone-bitmap",
        "png-bitmap-glyphs-cbdt-cblc-png",
        "png-bitmap-glyphs-malformed-png-refusal",
        "png-bitmap-glyphs-non-png-payload-refusal",
        "png-bitmap-glyphs-sbix-png",
        "png-bitmap-glyphs-unavailable-strike-refusal",
        "svg-glyphs-svg-defs-symbol-use-radial-gradient",
        "svg-glyphs-svg-gradient-transform-clip",
        "svg-glyphs-svg-static-path",
    },
}
EXPECTED_FAMILY_COUNTS = {
    "color-glyphs": 9,
    "emoji": 9,
    "png-bitmap-glyphs": 5,
    "svg-glyphs": 16,
}
GPU_REQUIRED_ROUTES = {"bitmap", "colr", "png", "svg", "svg-plan"}
KNOWN_EXPECTED_ROUTES = GPU_REQUIRED_ROUTES | {"missing", "outline", "refusal"}


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


def require_existing_file(root: Path, relative_path: str, label: str) -> None:
    path = (root / relative_path).resolve()
    require(path.is_file(), f"{label} missing file: {relative_path}")


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def load_json(root: Path, relative_path: str) -> Any:
    require_existing_file(root, relative_path, relative_path)
    try:
        return json.loads((root / relative_path).read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def validate_target(target: Any, fixture_id: str) -> None:
    require(isinstance(target, dict), f"{fixture_id}.target must be an object")
    require_keys(target, TARGET_KEYS, f"{fixture_id}.target")
    kind = require_string(target["kind"], f"{fixture_id}.target.kind")
    require(kind in {"glyph-id", "text-sequence"}, f"{fixture_id}.target.kind is unknown: {kind}")
    glyph_ids = target["glyphIds"]
    require(isinstance(glyph_ids, list), f"{fixture_id}.target.glyphIds must be a list")
    for index, glyph_id in enumerate(glyph_ids):
        require(isinstance(glyph_id, int) and glyph_id >= 0, f"{fixture_id}.target.glyphIds[{index}] must be non-negative integer")
    if kind == "glyph-id":
        require(glyph_ids, f"{fixture_id}.target.glyphIds must be non-empty for glyph-id targets")
        require(target["textSequence"] is None, f"{fixture_id}.target.textSequence must be null for glyph-id targets")
    else:
        require_string(target["textSequence"], f"{fixture_id}.target.textSequence")


def validate_provenance(root: Path, provenance: Any, fixture_id: str, font_source_id: str, provenance_lookup: dict[str, Any]) -> None:
    require(isinstance(provenance, dict), f"{fixture_id}.provenance must be an object")
    require_keys(provenance, PROVENANCE_KEYS, f"{fixture_id}.provenance")
    require_string(provenance["kind"], f"{fixture_id}.provenance.kind")
    require_string(provenance["source"], f"{fixture_id}.provenance.source")
    require_string(provenance["licenseNote"], f"{fixture_id}.provenance.licenseNote")
    source_sha = require_string(provenance["sourceSha256"], f"{fixture_id}.provenance.sourceSha256")
    require(SHA256_RE.match(source_sha) is not None, f"{fixture_id}.provenance.sourceSha256 must be lowercase SHA-256")
    recipe = require_string_list(provenance["generatedSourceRecipe"], f"{fixture_id}.provenance.generatedSourceRecipe")
    require(recipe, f"{fixture_id}.provenance.generatedSourceRecipe must be non-empty")

    if font_source_id in {"color-colrv1-test-glyphs", "fallback-emoji-noto-color"}:
        source_fixture = provenance_lookup.get(font_source_id)
        require(source_fixture is not None, f"{fixture_id} references missing provenance fixture {font_source_id}")
        require(
            EXPECTED_DUMP_PATH in source_fixture["expectedDumps"],
            f"{font_source_id}.expectedDumps must include {EXPECTED_DUMP_PATH}",
        )
        asset_hash = source_fixture["assets"][0]["sha256"]
        require(source_sha == asset_hash, f"{fixture_id}.provenance.sourceSha256 must match {font_source_id} asset hash")
        require(
            provenance["source"] == f"{PROVENANCE_INDEX_PATH}#{font_source_id}",
            f"{fixture_id}.provenance.source must point to provenance index entry {font_source_id}",
        )
    else:
        require(
            source_sha == sha256_text("|".join(recipe)),
            f"{fixture_id}.provenance.sourceSha256 must match generatedSourceRecipe hash",
        )


def validate_case(root: Path, case: Any, provenance_lookup: dict[str, Any]) -> dict[str, Any]:
    require(isinstance(case, dict), "case rows must be objects")
    require_keys(case, CASE_KEYS, "case")
    fixture_id = require_string(case["fixtureId"], "case.fixtureId")
    family = require_string(case["family"], f"{fixture_id}.family")
    require(family in EXPECTED_FAMILIES, f"{fixture_id}.family is unknown: {family}")
    font_source_id = require_string(case["fontSourceId"], f"{fixture_id}.fontSourceId")
    require(font_source_id in KNOWN_FONT_SOURCE_IDS, f"{fixture_id}.fontSourceId is unknown: {font_source_id}")
    validate_target(case["target"], fixture_id)
    validate_provenance(root, case["provenance"], fixture_id, font_source_id, provenance_lookup)

    expected_route = case["expectedRoute"]
    require(expected_route is None or isinstance(expected_route, str), f"{fixture_id}.expectedRoute must be string or null")
    if expected_route is not None:
        require(expected_route in KNOWN_EXPECTED_ROUTES, f"{fixture_id}.expectedRoute is unknown: {expected_route}")
    require_string_list(case["expectedDiagnostics"], f"{fixture_id}.expectedDiagnostics", allow_empty=True)
    expected_dump_files = require_string_list(case["expectedDumpFiles"], f"{fixture_id}.expectedDumpFiles")
    for dump_file in expected_dump_files:
        require_existing_file(root, f"reports/font/fixtures/expected/color/{dump_file}", f"{fixture_id}.expectedDumpFiles")
    linked_gates = require_string_list(case["legacyGates"], f"{fixture_id}.legacyGates", allow_empty=True)
    require(set(linked_gates).issubset(KNOWN_GATES), f"{fixture_id}.legacyGates has unknown gate ids")
    gpu_required = require_bool(case["gpuEvidenceRequired"], f"{fixture_id}.gpuEvidenceRequired")
    remaining = require_string_list(case["remainingEvidence"], f"{fixture_id}.remainingEvidence")
    require(remaining, f"{fixture_id}.remainingEvidence must be non-empty")
    require((expected_route in GPU_REQUIRED_ROUTES) == gpu_required, f"{fixture_id}.gpuEvidenceRequired must track expectedRoute semantics")
    return case


def validate_manifest(root: Path, manifest: dict[str, Any]) -> None:
    require_keys(manifest, TOP_LEVEL_KEYS, "manifest")
    require(manifest["schemaVersion"] == 1, "schemaVersion must be 1")
    require(manifest["dumpId"] == "color-emoji-fixture-manifest", "dumpId changed")
    require(manifest["ownerTickets"] == ["KFONT-M10-010"], "ownerTickets changed")
    require(manifest["fixtureFamilies"] == EXPECTED_FAMILIES, "fixtureFamilies must be stable")

    provenance_index = load_json(root, PROVENANCE_INDEX_PATH)
    require(isinstance(provenance_index, dict), "provenance index root must be an object")
    provenance_lookup = {
        fixture["fixtureId"]: fixture
        for fixture in provenance_index["fixtures"]
        if isinstance(fixture, dict) and isinstance(fixture.get("fixtureId"), str)
    }

    legacy_gates = manifest["legacyGates"]
    require(isinstance(legacy_gates, list) and legacy_gates, "legacyGates must be a non-empty list")
    seen_gate_ids: list[str] = []
    for gate in legacy_gates:
        require(isinstance(gate, dict), "legacy gate rows must be objects")
        require_keys(gate, LEGACY_GATE_KEYS, "legacyGate")
        gate_id = require_string(gate["gateId"], "legacyGate.gateId")
        require(gate_id in KNOWN_GATES, f"legacy gate id unknown: {gate_id}")
        seen_gate_ids.append(gate_id)
        fixture_ids = set(require_string_list(gate["fixtureIds"], f"{gate_id}.fixtureIds"))
        require(fixture_ids == EXPECTED_GATE_FIXTURES[gate_id], f"{gate_id}.fixtureIds changed")
        require_bool(gate["gpuEvidenceRequired"], f"{gate_id}.gpuEvidenceRequired")
        require_string_list(gate["remainingEvidence"], f"{gate_id}.remainingEvidence")
    require(seen_gate_ids == sorted(KNOWN_GATES), "legacyGates must be sorted by gateId")

    rebaseline = manifest["rebaselinePolicy"]
    require(isinstance(rebaseline, dict), "rebaselinePolicy must be an object")
    require_keys(rebaseline, REBASELINE_KEYS, "rebaselinePolicy")
    require(rebaseline["ordinaryTestRuns"] == "must-not-overwrite-goldens", "rebaseline ordinaryTestRuns changed")
    require_string(rebaseline["reviewRequirement"], "rebaselinePolicy.reviewRequirement")
    linked_dump_diffs = require_string_list(rebaseline["linkedDumpDiffs"], "rebaselinePolicy.linkedDumpDiffs")
    require(
        linked_dump_diffs == [
            "bitmap-glyph-plan.json",
            "color-glyph-composite-plan.json",
            "color-glyph-plan.json",
            "color-svg-emoji-goldens.json",
            "colrv1-fixture-manifest.json",
            "colrv1-paint-graph.json",
            "emoji-route-trace.json",
            "svg-glyph-fixture-manifest.json",
            "svg-glyph-plan.json",
        ],
        "rebaselinePolicy.linkedDumpDiffs changed",
    )
    require(rebaseline["autoOverwritePolicy"] == "forbidden", "rebaseline autoOverwritePolicy changed")

    cases = manifest["cases"]
    require(isinstance(cases, list) and cases, "cases must be a non-empty list")
    validated_cases = [validate_case(root, case, provenance_lookup) for case in cases]
    fixture_ids = [case["fixtureId"] for case in validated_cases]
    require(fixture_ids == sorted(fixture_ids), "cases must be sorted by fixtureId")
    require(set(fixture_ids) == EXPECTED_FIXTURE_IDS, "cases must cover the exact required fixture ids")
    family_counts: dict[str, int] = {}
    case_gate_map: dict[str, set[str]] = {gate_id: set() for gate_id in KNOWN_GATES}
    for case in validated_cases:
        family_counts[case["family"]] = family_counts.get(case["family"], 0) + 1
        for gate_id in case["legacyGates"]:
            case_gate_map[gate_id].add(case["fixtureId"])
    require(family_counts == EXPECTED_FAMILY_COUNTS, f"family counts changed: {family_counts}")
    require(case_gate_map == EXPECTED_GATE_FIXTURES, f"case legacyGates changed: {case_gate_map}")

    non_claims = require_string_list(manifest["nonClaims"], "nonClaims")
    for required_non_claim in [
        "no-complete-target-support-claim",
        "no-complete-colrv1-rendering-claim",
        "no-complete-png-bitmap-glyph-routing-claim",
        "no-complete-svg-in-opentype-rendering-claim",
        "no-complete-emoji-sequence-shaping-claim",
        "no-complete-color-glyph-fallback-support-claim",
        "no-gpu-color-glyph-support-claim",
        "no-gpu-bitmap-glyph-route-claim",
        "no-gpu-svg-glyph-route-claim",
        "no-platform-color-font-fallback-claim",
        "no-platform-bitmap-codec-claim",
        "no-platform-emoji-engine-claim",
        "no-native-svg-renderer-claim",
        "no-scaledemoji-retirement",
        "no-scaledemoji-rendering-retirement",
        "no-coloremoji-blendmodes-retirement",
    ]:
        require(required_non_claim in non_claims, f"missing non-claim: {required_non_claim}")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    try:
        manifest = load_json(root, MANIFEST_PATH)
        require(isinstance(manifest, dict), "manifest root must be an object")
        validate_manifest(root, manifest)
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print("Color emoji fixture manifest validation passed: 39 fixture rows.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
