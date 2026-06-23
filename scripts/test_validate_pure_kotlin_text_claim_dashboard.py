#!/usr/bin/env python3
import copy
import importlib.util
import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = PROJECT_ROOT / "scripts" / "validate_pure_kotlin_text_claim_dashboard.py"

sys.dont_write_bytecode = True


def load_validator():
    if not VALIDATOR_PATH.is_file():
        raise AssertionError("missing validator script: scripts/validate_pure_kotlin_text_claim_dashboard.py")
    spec = importlib.util.spec_from_file_location("validate_pure_kotlin_text_claim_dashboard", VALIDATOR_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError("unable to load validator module spec")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class PureKotlinTextClaimDashboardTest(unittest.TestCase):
    def test_dashboard_covers_classifications_surfaces_legacy_gates_and_wiring(self) -> None:
        validator = load_validator()
        dashboard = validator.load_dashboard(PROJECT_ROOT)
        build_text = validator.load_build_gradle_text(PROJECT_ROOT)

        validator.validate_dashboard(PROJECT_ROOT, dashboard)
        validator.validate_gradle_wiring(build_text)

        self.assertEqual(
            [
                "target-supported",
                "current-supported",
                "tracked-gap",
                "DependencyGated",
                "fixture-gated",
                "GPU-gated",
                "expected-unsupported",
                "drift-only",
            ],
            dashboard["classifications"],
        )

        surface_rows = {row["surfaceId"]: row for row in dashboard["surfaceRows"]}
        self.assertEqual(
            {
                "a8-outline-rasterization",
                "cluster-safety",
                "complex-shaping",
                "emoji-color",
                "fallback",
                "font-parser-metrics",
                "font-scaler-metrics",
                "font-telemetry-schema",
                "glyph-artifact-metrics",
                "glyph-artifact-plan-route-taxonomy",
                "glyph-atlas-eviction-invalidation",
                "glyph-atlas-occupancy",
                "glyph-cache-metrics",
                "glyph-cache-telemetry",
                "glyph-strike-key-completeness",
                "gpu-text-handoff-metrics",
                "gpu-text-route-refusals",
                "gpu-text-upload-metrics",
                "lcd",
                "outline-path",
                "paragraph-layout-metrics",
                "sdf",
                "skia-facade-adapter-inventory",
                "simple-latin-atlas",
                "text-shaping-metrics",
            },
            set(surface_rows),
        )
        self.assertEqual("current-supported", surface_rows["outline-path"]["classification"])
        self.assertEqual("GPU-gated", surface_rows["sdf"]["classification"])
        self.assertFalse(any(row["claimPromotionAllowed"] for row in dashboard["surfaceRows"]))

        negative_labels = {row["label"] for row in dashboard["negativeGenericLabels"]}
        self.assertEqual({"emoji supported", "font missing", "text works"}, negative_labels)
        self.assertEqual(
            {
                "coloremoji_blendmodes",
                "dftext",
                "fontations",
                "fontations_ft_compare",
                "pdf_never_embed",
                "scaledemoji",
                "scaledemoji_rendering",
            },
            {row["gate"] for row in dashboard["legacyGates"]},
        )

    def test_validator_rejects_missing_classification_value(self) -> None:
        validator = load_validator()
        dashboard = validator.load_dashboard(PROJECT_ROOT)
        modified = copy.deepcopy(dashboard)
        modified["classifications"] = [
            value for value in modified["classifications"] if value != "DependencyGated"
        ]

        with self.assertRaises(validator.ValidationError) as missing:
            validator.validate_dashboard(PROJECT_ROOT, modified)
        self.assertIn("missing classifications", str(missing.exception))

    def test_validator_rejects_generic_font_missing_if_not_a_negative_fixture(self) -> None:
        validator = load_validator()
        dashboard = validator.load_dashboard(PROJECT_ROOT)
        modified = copy.deepcopy(dashboard)
        modified["negativeGenericLabels"] = [
            row for row in modified["negativeGenericLabels"] if row["label"] != "font missing"
        ]

        with self.assertRaises(validator.ValidationError) as missing:
            validator.validate_dashboard(PROJECT_ROOT, modified)
        self.assertIn("missing negative generic labels", str(missing.exception))

    def test_validator_rejects_generic_label_in_claim_rows(self) -> None:
        validator = load_validator()
        dashboard = validator.load_dashboard(PROJECT_ROOT)
        modified = copy.deepcopy(dashboard)
        modified["surfaceRows"][0]["label"] = "font missing"

        with self.assertRaises(validator.ValidationError) as generic:
            validator.validate_dashboard(PROJECT_ROOT, modified)
        self.assertIn("generic label", str(generic.exception))

    def test_validator_rejects_gpu_claim_promoted_without_gpu_artifact(self) -> None:
        validator = load_validator()
        dashboard = validator.load_dashboard(PROJECT_ROOT)
        modified = copy.deepcopy(dashboard)
        rows = {row["surfaceId"]: row for row in modified["surfaceRows"]}
        rows["sdf"]["classification"] = "target-supported"
        rows["sdf"]["gpuClaimed"] = True
        rows["sdf"]["evidence"]["gpuArtifacts"] = []

        with self.assertRaises(validator.ValidationError) as gpu:
            validator.validate_dashboard(PROJECT_ROOT, modified)
        self.assertIn("GPU claimed without GPU artifact", str(gpu.exception))

    def test_validator_rejects_current_supported_without_current_evidence(self) -> None:
        validator = load_validator()
        dashboard = validator.load_dashboard(PROJECT_ROOT)
        modified = copy.deepcopy(dashboard)
        rows = {row["surfaceId"]: row for row in modified["surfaceRows"]}
        rows["outline-path"]["evidence"] = {field: [] for field in validator.EVIDENCE_FIELDS}

        with self.assertRaises(validator.ValidationError) as current:
            validator.validate_dashboard(PROJECT_ROOT, modified)
        self.assertIn("current-supported requires", str(current.exception))

    def test_validator_rejects_missing_legacy_gate(self) -> None:
        validator = load_validator()
        dashboard = validator.load_dashboard(PROJECT_ROOT)
        modified = copy.deepcopy(dashboard)
        modified["legacyGates"] = [
            row for row in modified["legacyGates"] if row["gate"] != "dftext"
        ]

        with self.assertRaises(validator.ValidationError) as legacy:
            validator.validate_dashboard(PROJECT_ROOT, modified)
        self.assertIn("missing legacy gates", str(legacy.exception))

    def test_validator_rejects_missing_gradle_wiring(self) -> None:
        validator = load_validator()
        build_text = validator.load_build_gradle_text(PROJECT_ROOT)
        modified = build_text.replace("validatePureKotlinTextClaimDashboard", "validatePureKotlinTextClaimDashboardDisabled")

        with self.assertRaises(validator.ValidationError) as gradle:
            validator.validate_gradle_wiring(modified)
        self.assertIn("validatePureKotlinTextClaimDashboard", str(gradle.exception))

    def test_validator_rejects_commented_gradle_task_registration(self) -> None:
        validator = load_validator()
        build_text = validator.load_build_gradle_text(PROJECT_ROOT)
        modified = build_text.replace(
            'tasks.register<Exec>("validatePureKotlinTextClaimDashboard") {',
            '// tasks.register<Exec>("validatePureKotlinTextClaimDashboard") {',
        )

        with self.assertRaises(validator.ValidationError) as gradle:
            validator.validate_gradle_wiring(modified)
        self.assertIn("missing Gradle task", str(gradle.exception))

    def test_validator_rejects_gradle_task_without_validator_commandline(self) -> None:
        validator = load_validator()
        build_text = validator.load_build_gradle_text(PROJECT_ROOT)
        modified = build_text.replace(
            '    commandLine("python3", "scripts/validate_pure_kotlin_text_claim_dashboard.py", rootDir.absolutePath)',
            '    commandLine("python3", "-c", "pass")',
        )

        with self.assertRaises(validator.ValidationError) as gradle:
            validator.validate_gradle_wiring(modified)
        self.assertIn("must execute the claim dashboard validator", str(gradle.exception))

    def test_validator_rejects_gradle_task_that_echoes_validator_path(self) -> None:
        validator = load_validator()
        build_text = validator.load_build_gradle_text(PROJECT_ROOT)
        modified = build_text.replace(
            '    commandLine("python3", "scripts/validate_pure_kotlin_text_claim_dashboard.py", rootDir.absolutePath)',
            '    commandLine("echo", "scripts/validate_pure_kotlin_text_claim_dashboard.py")',
        )

        with self.assertRaises(validator.ValidationError) as gradle:
            validator.validate_gradle_wiring(modified)
        self.assertIn("must execute the claim dashboard validator", str(gradle.exception))

    def test_validator_rejects_gradle_task_that_mentions_validator_in_python_expression(self) -> None:
        validator = load_validator()
        build_text = validator.load_build_gradle_text(PROJECT_ROOT)
        modified = build_text.replace(
            '    commandLine("python3", "scripts/validate_pure_kotlin_text_claim_dashboard.py", rootDir.absolutePath)',
            '    commandLine("python3", "-c", "scripts/validate_pure_kotlin_text_claim_dashboard.py")',
        )

        with self.assertRaises(validator.ValidationError) as gradle:
            validator.validate_gradle_wiring(modified)
        self.assertIn("must execute the claim dashboard validator", str(gradle.exception))

    def test_validator_rejects_commented_scene_gate_dependency(self) -> None:
        validator = load_validator()
        build_text = validator.load_build_gradle_text(PROJECT_ROOT)
        modified = build_text.replace(
            '    dependsOn("validatePureKotlinTextClaimDashboard")',
            '    // dependsOn("validatePureKotlinTextClaimDashboard")',
        )

        with self.assertRaises(validator.ValidationError) as gradle:
            validator.validate_gradle_wiring(modified)
        self.assertIn("pipelineSceneDashboardGate must depend", str(gradle.exception))

    def test_validator_rejects_commented_pm_bundle_dependency(self) -> None:
        validator = load_validator()
        build_text = validator.load_build_gradle_text(PROJECT_ROOT)
        modified = build_text.replace(
            '        "validatePureKotlinTextClaimDashboard",',
            '        // "validatePureKotlinTextClaimDashboard",',
        )

        with self.assertRaises(validator.ValidationError) as gradle:
            validator.validate_gradle_wiring(modified)
        self.assertIn("pipelinePmBundle must depend", str(gradle.exception))


if __name__ == "__main__":
    unittest.main()
