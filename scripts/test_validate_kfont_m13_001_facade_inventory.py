#!/usr/bin/env python3
import copy
import importlib.util
import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = PROJECT_ROOT / "scripts" / "validate_kfont_m13_001_facade_inventory.py"

sys.dont_write_bytecode = True


def load_validator():
    if not VALIDATOR_PATH.is_file():
        raise AssertionError("missing validator script: scripts/validate_kfont_m13_001_facade_inventory.py")
    spec = importlib.util.spec_from_file_location("validate_kfont_m13_001_facade_inventory", VALIDATOR_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError("unable to load validator module spec")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class KfontM13001FacadeInventoryTest(unittest.TestCase):
    def test_facade_inventory_covers_required_routes_gates_and_dashboard_bundle_links(self) -> None:
        validator = load_validator()
        artifact = validator.load_json(PROJECT_ROOT, validator.ARTIFACT_PATH)
        dashboard = validator.load_json(PROJECT_ROOT, validator.DASHBOARD_PATH)
        taxonomy = validator.load_json(PROJECT_ROOT, validator.TAXONOMY_PATH)
        report = validator.load_text(PROJECT_ROOT, validator.REPORT_PATH)
        build_gradle = validator.load_text(PROJECT_ROOT, validator.BUILD_GRADLE_PATH)

        self.assertEqual("facade-adapter-inventory", artifact["artifactId"])
        self.assertEqual("KFONT-M13-001", artifact["ticketId"])
        self.assertEqual(validator.REQUIRED_ROUTE_IDS, [row["routeId"] for row in artifact["routes"]])
        self.assertEqual(validator.REQUIRED_LEGACY_GATES, [row["gate"] for row in artifact["legacyGateCoverage"]])
        self.assertEqual(
            {
                "current-supported": 3,
                "tracked-gap": 2,
                "DependencyGated": 2,
            },
            artifact["summary"]["classificationCounts"],
        )
        self.assertEqual(
            {
                "promote-with-contract": 4,
                "replace": 2,
                "reuse-as-is": 1,
            },
            artifact["summary"]["migrationCategoryCounts"],
        )
        validator.validate_inventory(PROJECT_ROOT, artifact, dashboard, taxonomy, report, build_gradle)

    def test_validator_rejects_missing_required_route(self) -> None:
        validator = load_validator()
        artifact = validator.load_json(PROJECT_ROOT, validator.ARTIFACT_PATH)
        dashboard = validator.load_json(PROJECT_ROOT, validator.DASHBOARD_PATH)
        taxonomy = validator.load_json(PROJECT_ROOT, validator.TAXONOMY_PATH)
        report = validator.load_text(PROJECT_ROOT, validator.REPORT_PATH)
        build_gradle = validator.load_text(PROJECT_ROOT, validator.BUILD_GRADLE_PATH)
        modified = copy.deepcopy(artifact)
        modified["routes"] = [row for row in modified["routes"] if row["routeId"] != "sktextblob-glyph-runs"]

        with self.assertRaises(validator.ValidationError) as missing:
            validator.validate_inventory(PROJECT_ROOT, modified, dashboard, taxonomy, report, build_gradle)
        self.assertIn("route coverage/order changed", str(missing.exception))

    def test_validator_rejects_paragraph_route_without_missing_public_facade_note(self) -> None:
        validator = load_validator()
        artifact = validator.load_json(PROJECT_ROOT, validator.ARTIFACT_PATH)
        modified = copy.deepcopy(artifact)
        paragraph = next(row for row in modified["routes"] if row["routeId"] == "paragraph-compatible-apis")
        paragraph["facadeSurface"] = "Pure Kotlin paragraph owner exists."

        with self.assertRaises(validator.ValidationError) as missing:
            validator.validate_route(paragraph, set())
        self.assertIn("org.skia.paragraph", str(missing.exception))

    def test_validator_rejects_adjacent_gate_that_claims_route_owner(self) -> None:
        validator = load_validator()
        artifact = validator.load_json(PROJECT_ROOT, validator.ARTIFACT_PATH)
        dashboard = validator.load_json(PROJECT_ROOT, validator.DASHBOARD_PATH)
        taxonomy = validator.load_json(PROJECT_ROOT, validator.TAXONOMY_PATH)
        report = validator.load_text(PROJECT_ROOT, validator.REPORT_PATH)
        build_gradle = validator.load_text(PROJECT_ROOT, validator.BUILD_GRADLE_PATH)
        modified = copy.deepcopy(artifact)
        pdf_gate = next(row for row in modified["legacyGateCoverage"] if row["gate"] == "pdf_never_embed")
        pdf_gate["routeIds"] = ["skfontmgr-catalog"]

        with self.assertRaises(validator.ValidationError) as adjacent:
            validator.validate_inventory(PROJECT_ROOT, modified, dashboard, taxonomy, report, build_gradle)
        self.assertIn("adjacent-out-of-scope", str(adjacent.exception))

    def test_validator_rejects_pm_bundle_without_taxonomy_artifact(self) -> None:
        validator = load_validator()
        artifact = validator.load_json(PROJECT_ROOT, validator.ARTIFACT_PATH)
        dashboard = validator.load_json(PROJECT_ROOT, validator.DASHBOARD_PATH)
        taxonomy = validator.load_json(PROJECT_ROOT, validator.TAXONOMY_PATH)
        report = validator.load_text(PROJECT_ROOT, validator.REPORT_PATH)
        build_gradle = validator.load_text(PROJECT_ROOT, validator.BUILD_GRADLE_PATH)
        modified_build = build_gradle.replace(
            '    inputs.file(layout.projectDirectory.file("reports/pure-kotlin-text/font-diagnostic-taxonomy.json"))\n',
            "",
        ).replace(
            '            "reports/pure-kotlin-text/font-diagnostic-taxonomy.json",\n',
            "",
        )

        with self.assertRaises(validator.ValidationError) as missing:
            validator.validate_inventory(PROJECT_ROOT, artifact, dashboard, taxonomy, report, modified_build)
        self.assertIn("font-diagnostic-taxonomy.json", str(missing.exception))


if __name__ == "__main__":
    unittest.main()
