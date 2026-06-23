#!/usr/bin/env python3
"""Tests for validate_pure_kotlin_text_golden_update_policy.py."""

import copy
import importlib.util
import json
import unittest
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = (
    PROJECT_ROOT / "scripts" / "validate_pure_kotlin_text_golden_update_policy.py"
)
POLICY_PATH = (
    PROJECT_ROOT / "reports" / "pure-kotlin-text" / "golden-update-policy.json"
)
DUMP_INDEX_PATH = (
    PROJECT_ROOT / "reports" / "pure-kotlin-text" / "dump-evidence-index.json"
)
FIXTURE_MANIFEST_PATH = (
    PROJECT_ROOT / "reports" / "pure-kotlin-text" / "fixture-evidence-manifest.json"
)


def load_validator():
    spec = importlib.util.spec_from_file_location(
        "validate_pure_kotlin_text_golden_update_policy", VALIDATOR_PATH
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class TestGoldenUpdatePolicyValidation(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.v = load_validator()
        with open(POLICY_PATH, encoding="utf-8") as f:
            cls.policy = json.load(f)
        with open(DUMP_INDEX_PATH, encoding="utf-8") as f:
            cls.dump_index = json.load(f)
        with open(FIXTURE_MANIFEST_PATH, encoding="utf-8") as f:
            cls.manifest = json.load(f)

    def test_policy_validates_successfully(self):
        self.v.validate_policy_structure(self.policy)
        self.v.validate_oracle_policy(self.policy)
        self.v.validate_drift_labels(self.policy)
        self.v.validate_golden_types(self.policy)
        self.v.validate_consistency_with_dump_index(self.policy, self.dump_index)
        self.v.validate_consistency_with_fixture_manifest(
            self.policy, self.manifest
        )
        self.v.validate_dump_rows_comply(self.policy, self.dump_index)

    def test_missing_required_key_raises(self):
        modified = copy.deepcopy(self.policy)
        del modified["ordinaryTestRuns"]
        with self.assertRaises(self.v.ValidationError):
            self.v.validate_policy_structure(modified)

    def test_missing_oracle_key_raises(self):
        modified = copy.deepcopy(self.policy)
        del modified["oraclePolicy"]["normative"]
        with self.assertRaises(self.v.ValidationError):
            self.v.validate_oracle_policy(modified)

    def test_missing_drift_key_raises(self):
        modified = copy.deepcopy(self.policy)
        del modified["driftLabels"]["allowedDriftLabels"]
        with self.assertRaises(self.v.ValidationError):
            self.v.validate_drift_labels(modified)

    def test_unknown_dump_classification_raises(self):
        modified_dump = copy.deepcopy(self.dump_index)
        if modified_dump["dumpRows"]:
            modified_dump["dumpRows"][0]["classification"] = "unknown-type"
            with self.assertRaises(self.v.ValidationError):
                self.v.validate_dump_rows_comply(
                    self.policy, modified_dump
                )

    def test_golden_gated_dump_missing_expected_fields_raises(self):
        modified_dump = copy.deepcopy(self.dump_index)
        for row in modified_dump["dumpRows"]:
            if row.get("classification") == "golden-gated":
                del row["expectedFields"]
                with self.assertRaises(self.v.ValidationError):
                    self.v.validate_dump_rows_comply(
                        self.policy, modified_dump
                    )
                return
        self.skipTest("No golden-gated dump row found to test")

    def test_ordinary_test_runs_consistency(self):
        dump_policy = self.dump_index.get("goldenUpdatePolicy", {})
        if dump_policy:
            self.assertEqual(
                self.policy["ordinaryTestRuns"],
                dump_policy["ordinaryTestRuns"],
            )

    def test_oracle_normative_consistency(self):
        manifest_oracle = self.manifest.get("oraclePolicy", {})
        policy_oracle = self.policy.get("oraclePolicy", {})
        if manifest_oracle and policy_oracle:
            self.assertEqual(
                policy_oracle.get("normative"),
                manifest_oracle.get("normative"),
            )

    def test_forbidden_engines_match_spec(self):
        engines = self.policy["oraclePolicy"]["forbiddenEngines"]
        self.assertIn("HarfBuzz", engines)
        self.assertIn("FreeType", engines)
        self.assertIn("CoreText", engines)
        self.assertIn("DirectWrite", engines)

    def test_allowed_drift_labels_are_non_empty(self):
        labels = self.policy["driftLabels"]["allowedDriftLabels"]
        self.assertTrue(labels)
        for label in labels:
            self.assertTrue(label)


if __name__ == "__main__":
    unittest.main()
