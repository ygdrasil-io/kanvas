#!/usr/bin/env python3
import importlib.util
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = PROJECT_ROOT / "scripts" / "validate_font_fixture_assets.py"

sys.dont_write_bytecode = True


def load_validator():
    spec = importlib.util.spec_from_file_location("validate_font_fixture_assets", VALIDATOR_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError("unable to load scripts/validate_font_fixture_assets.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def valid_payload(asset_path: Path, *, license_path: str, asset_row_path: str, expected_dumps: list[str]) -> dict:
    return {
        "schemaVersion": 1,
        "indexId": "font-fixture-assets",
        "fixtureRoot": "reports/font/fixtures",
        "licensePolicy": ["Apache-2.0", "SIL-OFL-1.1"],
        "sizeBudgetBytes": 20 * 1024 * 1024,
        "offlinePolicy": "tests-must-not-download",
        "fixtures": [
            {
                "fixtureId": "scoped-paths",
                "familyId": "font-source-sfnt",
                "ownerTickets": ["PKT-02D"],
                "source": {
                    "kind": "synthetic-kanvas",
                    "project": "Kanvas",
                    "url": asset_row_path,
                    "version": "test",
                },
                "license": {
                    "id": "SIL-OFL-1.1",
                    "path": license_path,
                },
                "assets": [
                    {
                        "path": asset_row_path,
                        "sha256": hashlib.sha256(asset_path.read_bytes()).hexdigest(),
                        "sizeBytes": asset_path.stat().st_size,
                        "role": "font",
                    }
                ],
                "expectedDumps": expected_dumps,
                "nonClaims": ["no-complete-target-support-claim"],
            }
        ],
    }


class FontFixtureAssetsValidatorTest(unittest.TestCase):
    def test_repo_fixture_root_validates(self) -> None:
        validator = load_validator()
        index = validator.load_index(PROJECT_ROOT)
        validator.validate_index(PROJECT_ROOT, index)
        self.assertEqual("font-fixture-assets", index["indexId"])
        self.assertEqual("tests-must-not-download", index["offlinePolicy"])
        self.assertEqual(20 * 1024 * 1024, index["sizeBudgetBytes"])

    def test_rejects_missing_asset(self) -> None:
        validator = load_validator()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "reports/font/fixtures/provenance").mkdir(parents=True)
            (root / "reports/font/fixtures/licenses").mkdir(parents=True)
            (root / "reports/font/fixtures/licenses/OFL-1.1.txt").write_text("license\n", encoding="utf-8")
            payload = {
                "schemaVersion": 1,
                "indexId": "font-fixture-assets",
                "fixtureRoot": "reports/font/fixtures",
                "licensePolicy": ["Apache-2.0", "SIL-OFL-1.1"],
                "sizeBudgetBytes": 20 * 1024 * 1024,
                "offlinePolicy": "tests-must-not-download",
                "fixtures": [
                    {
                        "fixtureId": "missing-font",
                        "familyId": "font-source-sfnt",
                        "ownerTickets": ["PKT-02D"],
                        "source": {
                            "kind": "vendored-external",
                            "project": "Missing",
                            "url": "https://example.invalid/font.ttf",
                            "version": "none",
                        },
                        "license": {
                            "id": "SIL-OFL-1.1",
                            "path": "reports/font/fixtures/licenses/OFL-1.1.txt",
                        },
                        "assets": [
                            {
                                "path": "reports/font/fixtures/fonts/missing.ttf",
                                "sha256": "0" * 64,
                                "sizeBytes": 1,
                                "role": "font",
                            }
                        ],
                        "expectedDumps": [],
                        "nonClaims": ["no-complete-target-support-claim"],
                    }
                ],
            }
            (root / "reports/font/fixtures/provenance/index.json").write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaises(validator.ValidationError) as failure:
                validator.validate_index(root, payload)
            self.assertIn("missing asset", str(failure.exception))

    def test_rejects_bool_size_bytes(self) -> None:
        validator = load_validator()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "reports/font/fixtures/fonts").mkdir(parents=True)
            (root / "reports/font/fixtures/licenses").mkdir(parents=True)
            asset_path = root / "reports/font/fixtures/fonts/one-byte.ttf"
            asset_path.write_bytes(b"x")
            (root / "reports/font/fixtures/licenses/OFL-1.1.txt").write_text("license\n", encoding="utf-8")
            payload = {
                "schemaVersion": 1,
                "indexId": "font-fixture-assets",
                "fixtureRoot": "reports/font/fixtures",
                "licensePolicy": ["Apache-2.0", "SIL-OFL-1.1"],
                "sizeBudgetBytes": 20 * 1024 * 1024,
                "offlinePolicy": "tests-must-not-download",
                "fixtures": [
                    {
                        "fixtureId": "bool-size",
                        "familyId": "font-source-sfnt",
                        "ownerTickets": ["PKT-02D"],
                        "source": {
                            "kind": "synthetic-kanvas",
                            "project": "Kanvas",
                            "url": "reports/font/fixtures/fonts/one-byte.ttf",
                            "version": "test",
                        },
                        "license": {
                            "id": "SIL-OFL-1.1",
                            "path": "reports/font/fixtures/licenses/OFL-1.1.txt",
                        },
                        "assets": [
                            {
                                "path": "reports/font/fixtures/fonts/one-byte.ttf",
                                "sha256": "2d711642b726b04401627ca9fbac32f5c8530fb1903cc4db02258717921a4881",
                                "sizeBytes": True,
                                "role": "font",
                            }
                        ],
                        "expectedDumps": [],
                        "nonClaims": ["no-complete-target-support-claim"],
                    }
                ],
            }
            with self.assertRaises(validator.ValidationError) as failure:
                validator.validate_index(root, payload)
            self.assertIn("sizeBytes", str(failure.exception))

    def test_rejects_asset_path_outside_fixture_fonts_root(self) -> None:
        validator = load_validator()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "reports/font/fixtures/licenses").mkdir(parents=True)
            (root / "external").mkdir()
            (root / "reports/font/fixtures/licenses/OFL-1.1.txt").write_text("license\n", encoding="utf-8")
            asset_path = root / "external/existing.ttf"
            asset_path.write_bytes(b"x")
            payload = valid_payload(
                asset_path,
                license_path="reports/font/fixtures/licenses/OFL-1.1.txt",
                asset_row_path="external/existing.ttf",
                expected_dumps=[],
            )
            with self.assertRaises(validator.ValidationError) as failure:
                validator.validate_index(root, payload)
            self.assertIn("reports/font/fixtures/fonts", str(failure.exception))

    def test_rejects_license_path_outside_fixture_licenses_root(self) -> None:
        validator = load_validator()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "reports/font/fixtures/fonts").mkdir(parents=True)
            (root / "licenses").mkdir()
            asset_path = root / "reports/font/fixtures/fonts/one-byte.ttf"
            asset_path.write_bytes(b"x")
            (root / "licenses/OFL-1.1.txt").write_text("license\n", encoding="utf-8")
            payload = valid_payload(
                asset_path,
                license_path="licenses/OFL-1.1.txt",
                asset_row_path="reports/font/fixtures/fonts/one-byte.ttf",
                expected_dumps=[],
            )
            with self.assertRaises(validator.ValidationError) as failure:
                validator.validate_index(root, payload)
            self.assertIn("reports/font/fixtures/licenses", str(failure.exception))

    def test_rejects_expected_dump_path_outside_fixture_expected_root(self) -> None:
        validator = load_validator()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "reports/font/fixtures/fonts").mkdir(parents=True)
            (root / "reports/font/fixtures/licenses").mkdir(parents=True)
            (root / "expected").mkdir()
            asset_path = root / "reports/font/fixtures/fonts/one-byte.ttf"
            asset_path.write_bytes(b"x")
            (root / "reports/font/fixtures/licenses/OFL-1.1.txt").write_text("license\n", encoding="utf-8")
            (root / "expected/dump.json").write_text("{}\n", encoding="utf-8")
            payload = valid_payload(
                asset_path,
                license_path="reports/font/fixtures/licenses/OFL-1.1.txt",
                asset_row_path="reports/font/fixtures/fonts/one-byte.ttf",
                expected_dumps=["expected/dump.json"],
            )
            with self.assertRaises(validator.ValidationError) as failure:
                validator.validate_index(root, payload)
            self.assertIn("reports/font/fixtures/expected", str(failure.exception))


if __name__ == "__main__":
    unittest.main()
