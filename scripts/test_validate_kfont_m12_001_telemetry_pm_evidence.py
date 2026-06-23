#!/usr/bin/env python3
import importlib.util
import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = PROJECT_ROOT / "scripts" / "validate_kfont_m12_001_telemetry_pm_evidence.py"

sys.dont_write_bytecode = True


def load_validator():
    if not VALIDATOR_PATH.is_file():
        raise AssertionError("missing validator script: scripts/validate_kfont_m12_001_telemetry_pm_evidence.py")
    spec = importlib.util.spec_from_file_location("validate_kfont_m12_001_telemetry_pm_evidence", VALIDATOR_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError("unable to load validator module spec")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class KfontM12001TelemetryPmEvidenceTest(unittest.TestCase):
    def test_pipeline_pm_bundle_block_keeps_real_task_block(self) -> None:
        validator = load_validator()
        build_gradle = validator.load_text(PROJECT_ROOT, validator.BUILD_GRADLE_PATH)
        block = validator.pipeline_pm_bundle_block(build_gradle)

        self.assertIn('tasks.register("pipelinePmBundle")', block)
        self.assertIn(validator.GPU_REPORT_PATH, block)
        self.assertIn(validator.ADVISORY_MD_PATH, block)
        self.assertNotIn('tasks.named("pipelinePmBundle")', block)

    def test_pipeline_pm_bundle_block_rejects_missing_task(self) -> None:
        validator = load_validator()
        with self.assertRaises(validator.ValidationError) as missing:
            validator.pipeline_pm_bundle_block("tasks.register(\"otherTask\") {}")
        self.assertIn("missing Gradle marker", str(missing.exception))

    def test_pipeline_pm_bundle_block_excludes_later_named_hooks(self) -> None:
        validator = load_validator()
        synthetic = """
tasks.register("pipelinePmBundle") {
    inputs.file(layout.projectDirectory.file("reports/pure-kotlin-text/parser-metrics.json"))
}
tasks.named("pipelinePmBundle") {
    inputs.file(layout.projectDirectory.file("reports/pure-kotlin-text/2026-06-19-kfont-m12-005-gpu-handoff-metrics.md"))
}
"""
        block = validator.pipeline_pm_bundle_block(synthetic)
        self.assertIn("parser-metrics.json", block)
        self.assertNotIn(validator.GPU_REPORT_PATH, block)


if __name__ == "__main__":
    unittest.main()
