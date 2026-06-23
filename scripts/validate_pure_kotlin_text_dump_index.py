#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable


INDEX_PATH = "reports/pure-kotlin-text/dump-evidence-index.json"
SOURCE_SPEC_PATH = ".upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md"

KNOWN_CLASSIFICATIONS = [
    "producer-only",
    "golden-gated",
    "current-golden",
]

REQUIRED_DUMPS = {
    "a8-atlas-build-result",
    "a8-sdf-atlas-lifecycle",
    "arabic-seed-readiness",
    "bitmap-glyph-plan",
    "cff-cff2-readiness",
    "cff-charstring-trace",
    "cff-index-dict",
    "cff-scaler-path-output",
    "cff-subroutine-trace",
    "cmap-contract",
    "color-emoji-fixture-manifest",
    "color-svg-emoji-goldens",
    "emoji-route-trace",
    "facade-adapter-inventory",
    "fallback-catalog-build",
    "font-source-liberation-scan-root",
    "glyph-atlas-lifecycle",
    "glyph-strike-key",
    "latin-gsub-gpos-goldens",
    "line-breaks",
    "malformed-sfnt-fixtures",
    "paragraph-input-goldens",
    "paragraph-layout-result",
    "paragraph-shaping-requests",
    "paragraph-shaping-requests-goldens",
    "png-glyph-image",
    "sdf-atlas-build-result",
    "script-runs",
    "sfnt-cmap-format14-readiness",
    "sfnt-table-facts",
    "svg-glyph-document",
    "svg-glyph-fixture-manifest",
    "svg-glyph-plan",
    "truetype-variation-readiness",
    "unicode-data-seed",
}

TOP_LEVEL_KEYS = [
    "schemaVersion",
    "indexId",
    "sourceSpec",
    "supportClaim",
    "goldenUpdatePolicy",
    "nonClaims",
    "validationCommand",
    "dumpRows",
]

SOURCE_SPEC_KEYS = ["path", "section"]
GOLDEN_POLICY_KEYS = ["ordinaryTestRuns", "reviewRequirement", "oraclePolicy"]
ROW_KEYS = [
    "dumpId",
    "ownerTicket",
    "classification",
    "targetSpec",
    "producerPaths",
    "expectedFields",
    "validationCommands",
    "updatePolicy",
    "nonClaims",
]
TARGET_SPEC_KEYS = ["path", "section"]

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
    raise ValidationError(f"pure Kotlin text dump index validation failed: {message}")


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


def load_index(root: Path) -> dict[str, Any]:
    index = load_json(root, INDEX_PATH)
    require(isinstance(index, dict), "index root must be an object")
    return index


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


def walk_strings(value: Any, path: tuple[Any, ...] = ()) -> Iterable[tuple[tuple[Any, ...], str]]:
    if isinstance(value, str):
        yield path, value
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from walk_strings(item, (*path, index))
    elif isinstance(value, dict):
        for key, item in value.items():
            yield from walk_strings(item, (*path, key))


def external_terms_allowed(path: tuple[Any, ...]) -> bool:
    return bool(path and path[0] in {"nonClaims", "goldenUpdatePolicy"})


def validate_external_engine_terms(index: dict[str, Any]) -> None:
    for path, text in walk_strings(index):
        lowered = text.lower()
        for term in FORBIDDEN_EXTERNAL_ENGINE_TERMS:
            pattern = rf"(?<![a-z0-9]){re.escape(term)}(?![a-z0-9])"
            if re.search(pattern, lowered) and not external_terms_allowed(path):
                fail(f"external engine term outside policy/non-claims at {'.'.join(map(str, path))}: {term}")


def validate_row(root: Path, row: Any, index: int) -> dict[str, Any]:
    require(isinstance(row, dict), f"dumpRows[{index}] must be an object")
    require_keys(row, ROW_KEYS, f"dumpRows[{index}]")

    dump_id = require_string(row["dumpId"], f"dumpRows[{index}].dumpId")
    require_string(row["ownerTicket"], f"{dump_id}.ownerTicket")
    classification = require_string(row["classification"], f"{dump_id}.classification")
    require(classification in KNOWN_CLASSIFICATIONS, f"{dump_id} has unknown classification: {classification}")
    require(classification != "current-golden", f"{dump_id} must not claim current-golden without checked golden artifacts")

    target_spec = row["targetSpec"]
    require(isinstance(target_spec, dict), f"{dump_id}.targetSpec must be an object")
    require_keys(target_spec, TARGET_SPEC_KEYS, f"{dump_id}.targetSpec")
    require_existing_path(root, target_spec["path"], f"{dump_id}.targetSpec.path")
    require_string(target_spec["section"], f"{dump_id}.targetSpec.section")

    producer_paths = require_string_list(row["producerPaths"], f"{dump_id}.producerPaths")
    for path_index, producer_path in enumerate(producer_paths):
        require_existing_path(root, producer_path, f"{dump_id}.producerPaths[{path_index}]")

    expected_fields = require_string_list(row["expectedFields"], f"{dump_id}.expectedFields")
    require(expected_fields == sorted(expected_fields), f"{dump_id}.expectedFields must be sorted")
    require(len(expected_fields) == len(set(expected_fields)), f"{dump_id}.expectedFields must be unique")

    validation_commands = require_string_list(row["validationCommands"], f"{dump_id}.validationCommands")
    require_string(row["updatePolicy"], f"{dump_id}.updatePolicy")
    non_claims = require_string_list(row["nonClaims"], f"{dump_id}.nonClaims")
    require("producer-only" in non_claims, f"{dump_id}.nonClaims must include producer-only")
    return row


def validate_index(root: Path, index: dict[str, Any]) -> None:
    root = root.resolve()
    require_keys(index, TOP_LEVEL_KEYS, "index")
    require(index["schemaVersion"] == 1, "schemaVersion must be 1")
    require(index["indexId"] == "pure-kotlin-text-dump-evidence-index", "indexId changed")

    source_spec = index["sourceSpec"]
    require(isinstance(source_spec, dict), "sourceSpec must be an object")
    require_keys(source_spec, SOURCE_SPEC_KEYS, "sourceSpec")
    require(source_spec["path"] == SOURCE_SPEC_PATH, "sourceSpec.path must point to spec 07")
    require_existing_path(root, source_spec["path"], "sourceSpec.path")
    require(source_spec["section"] == "Evidence Artifacts", "sourceSpec.section changed")

    require(
        index["supportClaim"] == "dump-index-coordination-artifact-no-support-claim",
        "index must be explicit that it is not a support claim",
    )

    policy = index["goldenUpdatePolicy"]
    require(isinstance(policy, dict), "goldenUpdatePolicy must be an object")
    require_keys(policy, GOLDEN_POLICY_KEYS, "goldenUpdatePolicy")
    require(policy["ordinaryTestRuns"] == "must-not-overwrite-goldens", "ordinary tests must not overwrite goldens")
    require_string(policy["reviewRequirement"], "goldenUpdatePolicy.reviewRequirement")
    require_string(policy["oraclePolicy"], "goldenUpdatePolicy.oraclePolicy")

    non_claims = require_string_list(index["nonClaims"], "nonClaims")
    require("not-a-support-claim" in non_claims, "nonClaims must include not-a-support-claim")
    require(
        index["validationCommand"] == "rtk python3 scripts/validate_pure_kotlin_text_dump_index.py",
        "validationCommand changed",
    )

    rows = index["dumpRows"]
    require(isinstance(rows, list) and rows, "dumpRows must be a non-empty list")
    validated_rows = [validate_row(root, row, row_index) for row_index, row in enumerate(rows)]
    dump_ids = [row["dumpId"] for row in validated_rows]
    require(dump_ids == sorted(dump_ids), "dumpRows must be sorted by dumpId")
    require(len(dump_ids) == len(set(dump_ids)), "dumpId values must be unique")
    require(REQUIRED_DUMPS.issubset(set(dump_ids)), f"missing required dumps: {sorted(REQUIRED_DUMPS - set(dump_ids))}")

    validate_external_engine_terms(index)


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    try:
        index = load_index(root)
        validate_index(root, index)
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print(
        "Pure Kotlin text dump index validation passed: "
        f"{len(index['dumpRows'])} dump rows."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
