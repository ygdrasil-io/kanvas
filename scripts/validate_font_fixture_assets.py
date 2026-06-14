#!/usr/bin/env python3
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


INDEX_PATH = "reports/font/fixtures/provenance/index.json"
TOP_LEVEL_KEYS = [
    "schemaVersion",
    "indexId",
    "fixtureRoot",
    "licensePolicy",
    "sizeBudgetBytes",
    "offlinePolicy",
    "fixtures",
]
FIXTURE_KEYS = [
    "fixtureId",
    "familyId",
    "ownerTickets",
    "source",
    "license",
    "assets",
    "expectedDumps",
    "nonClaims",
]
SOURCE_KEYS = ["kind", "project", "url", "version"]
LICENSE_KEYS = ["id", "path"]
ASSET_KEYS = ["path", "sha256", "sizeBytes", "role"]
ACCEPTED_LICENSES = ["Apache-2.0", "SIL-OFL-1.1"]
SOURCE_KINDS = ["in-repo-existing", "vendored-external", "synthetic-kanvas"]
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
LICENSE_ROOT = "reports/font/fixtures/licenses"
FONT_ROOT = "reports/font/fixtures/fonts"
EXPECTED_ROOT = "reports/font/fixtures/expected"


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"font fixture asset validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def require_keys(payload: dict[str, Any], expected: list[str], label: str) -> None:
    require(
        list(payload.keys()) == expected,
        f"{label} keys must be stable and ordered: {expected}",
    )


def require_string(value: Any, label: str) -> str:
    require(
        isinstance(value, str) and value.strip() == value and value,
        f"{label} must be a non-empty trimmed string",
    )
    return value


def require_string_list(value: Any, label: str, *, allow_empty: bool = False) -> list[str]:
    require(isinstance(value, list), f"{label} must be a list")
    if not allow_empty:
        require(value, f"{label} must be non-empty")
    for index, item in enumerate(value):
        require_string(item, f"{label}[{index}]")
    return value


def require_non_negative_int(value: Any, label: str) -> int:
    require(
        isinstance(value, int) and not isinstance(value, bool) and value >= 0,
        f"{label} must be non-negative integer",
    )
    return value


def repo_path(root: Path, relative_path: str, label: str) -> Path:
    require_string(relative_path, label)
    require(not Path(relative_path).is_absolute(), f"{label} must be relative: {relative_path}")
    resolved_root = root.resolve()
    resolved_path = (resolved_root / relative_path).resolve()
    require(
        resolved_path == resolved_root or resolved_root in resolved_path.parents,
        f"{label} escapes project root: {relative_path}",
    )
    return resolved_path


def require_under(relative_path: str, required_root: str, label: str) -> None:
    require_string(relative_path, label)
    relative = Path(relative_path)
    required = Path(required_root)
    require(
        relative == required or required in relative.parents,
        f"{label} must be under {required_root}: {relative_path}",
    )


def require_file(root: Path, relative_path: str, label: str, *, asset: bool = False) -> Path:
    path = repo_path(root, relative_path, label)
    if asset:
        require(path.is_file(), f"missing asset file for {label}: {relative_path}")
    else:
        require(path.is_file(), f"missing file for {label}: {relative_path}")
    return path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(root: Path, relative_path: str) -> Any:
    path = require_file(root, relative_path, relative_path)
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def load_index(root: Path) -> dict[str, Any]:
    index = load_json(root, INDEX_PATH)
    require(isinstance(index, dict), "index root must be an object")
    return index


def validate_fixture(root: Path, fixture: Any, index: int, accepted_licenses: list[str]) -> int:
    require(isinstance(fixture, dict), f"fixtures[{index}] must be an object")
    require_keys(fixture, FIXTURE_KEYS, f"fixtures[{index}]")
    fixture_id = require_string(fixture["fixtureId"], f"fixtures[{index}].fixtureId")
    require_string(fixture["familyId"], f"{fixture_id}.familyId")
    require_string_list(fixture["ownerTickets"], f"{fixture_id}.ownerTickets")

    source = fixture["source"]
    require(isinstance(source, dict), f"{fixture_id}.source must be an object")
    require_keys(source, SOURCE_KEYS, f"{fixture_id}.source")
    require(
        source["kind"] in SOURCE_KINDS,
        f"{fixture_id}.source.kind is not accepted: {source['kind']}",
    )
    require_string(source["project"], f"{fixture_id}.source.project")
    require_string(source["url"], f"{fixture_id}.source.url")
    require_string(source["version"], f"{fixture_id}.source.version")

    license_payload = fixture["license"]
    require(isinstance(license_payload, dict), f"{fixture_id}.license must be an object")
    require_keys(license_payload, LICENSE_KEYS, f"{fixture_id}.license")
    license_id = require_string(license_payload["id"], f"{fixture_id}.license.id")
    require(license_id in accepted_licenses, f"{fixture_id}.license.id is not accepted: {license_id}")
    require_under(license_payload["path"], LICENSE_ROOT, f"{fixture_id}.license.path")
    require_file(root, license_payload["path"], f"{fixture_id}.license.path")

    total_size = 0
    assets = fixture["assets"]
    require(isinstance(assets, list) and assets, f"{fixture_id}.assets must be a non-empty list")
    for asset_index, asset in enumerate(assets):
        require(isinstance(asset, dict), f"{fixture_id}.assets[{asset_index}] must be an object")
        require_keys(asset, ASSET_KEYS, f"{fixture_id}.assets[{asset_index}]")
        require_under(asset["path"], FONT_ROOT, f"{fixture_id}.assets[{asset_index}].path")
        asset_path = require_file(
            root,
            asset["path"],
            f"{fixture_id}.assets[{asset_index}].path",
            asset=True,
        )
        expected_hash = require_string(asset["sha256"], f"{fixture_id}.assets[{asset_index}].sha256")
        require(
            SHA256_RE.match(expected_hash) is not None,
            f"{fixture_id}.assets[{asset_index}].sha256 must be lowercase SHA-256",
        )
        actual_hash = sha256(asset_path)
        require(
            actual_hash == expected_hash,
            f"{fixture_id}.assets[{asset_index}] SHA-256 mismatch: {asset['path']}",
        )
        expected_size = require_non_negative_int(
            asset["sizeBytes"],
            f"{fixture_id}.assets[{asset_index}].sizeBytes",
        )
        actual_size = asset_path.stat().st_size
        require(
            actual_size == expected_size,
            f"{fixture_id}.assets[{asset_index}] size mismatch: {asset['path']}",
        )
        require_string(asset["role"], f"{fixture_id}.assets[{asset_index}].role")
        total_size += actual_size

    for dump_path in require_string_list(fixture["expectedDumps"], f"{fixture_id}.expectedDumps", allow_empty=True):
        require_under(dump_path, EXPECTED_ROOT, f"{fixture_id}.expectedDumps")
        require_file(root, dump_path, f"{fixture_id}.expectedDumps")

    non_claims = require_string_list(fixture["nonClaims"], f"{fixture_id}.nonClaims")
    require(
        "no-complete-target-support-claim" in non_claims,
        f"{fixture_id}.nonClaims must include no-complete-target-support-claim",
    )
    return total_size


def validate_index(root: Path, index: dict[str, Any]) -> None:
    require_keys(index, TOP_LEVEL_KEYS, "index")
    require(index["schemaVersion"] == 1, "schemaVersion must be 1")
    require(index["indexId"] == "font-fixture-assets", "indexId changed")
    require(index["fixtureRoot"] == "reports/font/fixtures", "fixtureRoot changed")
    require(index["licensePolicy"] == ACCEPTED_LICENSES, "licensePolicy changed")
    require(index["offlinePolicy"] == "tests-must-not-download", "offlinePolicy changed")
    size_budget = require_non_negative_int(index["sizeBudgetBytes"], "sizeBudgetBytes")
    require(size_budget == 20 * 1024 * 1024, "sizeBudgetBytes must be 20 MiB")
    fixtures = index["fixtures"]
    require(isinstance(fixtures, list), "fixtures must be a list")
    fixture_ids = [
        require_string(row.get("fixtureId"), f"fixtures[{i}].fixtureId")
        for i, row in enumerate(fixtures)
        if isinstance(row, dict)
    ]
    require(len(fixture_ids) == len(fixtures), "fixtures must contain only objects")
    require(fixture_ids == sorted(fixture_ids), "fixtures must be sorted by fixtureId")
    require(len(fixture_ids) == len(set(fixture_ids)), "fixtureId values must be unique")
    total_size = sum(
        validate_fixture(root, fixture, index, ACCEPTED_LICENSES)
        for index, fixture in enumerate(fixtures)
    )
    require(total_size <= size_budget, f"fixture assets exceed size budget: {total_size} > {size_budget}")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    try:
        index = load_index(root)
        validate_index(root, index)
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(f"Font fixture asset validation passed: {len(index['fixtures'])} fixtures.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
