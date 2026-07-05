#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan033_runtime_effect_uniform_preview as kan033


class RuntimeEffectUniformPreviewTest(unittest.TestCase):
    def test_build_evidence_covers_two_registered_effects(self) -> None:
        evidence = kan033.build_evidence(PROJECT_ROOT)

        self.assertEqual("kan-033-runtime-effect-uniform-preview-v1", evidence["packId"])
        self.assertEqual("pass", evidence["status"])
        self.assertEqual(
            {
                "effectCount": 2,
                "editedStateCount": 4,
                "gpuParityStateCount": 4,
                "pipelineKeyChanges": 0,
                "invalidEditCount": 2,
            },
            evidence["counts"],
        )
        self.assertEqual(
            ["runtime.simple_rt", "runtime.spiral_rt"],
            [effect["stableId"] for effect in evidence["effects"]],
        )
        for effect in evidence["effects"]:
            self.assertTrue((PROJECT_ROOT / effect["wgslSourcePath"]).is_file())
            self.assertIn("fragment:", effect["wgslSourceHash"])
            self.assertTrue(effect["wgslEntryPoint"].endswith("_source"))
            telemetry = effect["telemetry"]
            self.assertFalse(telemetry["uniformValuesInPipelineKey"])
            self.assertTrue(telemetry["pipelineKeyStableAcrossUniformEdits"])
            self.assertEqual(2, telemetry["uniformUpdateCount"])
            self.assertEqual(0, telemetry["compileCountDelta"])
            self.assertEqual("none", telemetry["fallbackReason"])
        for row in evidence["editedStates"]:
            self.assertIn("routeGpu", row["artifacts"])
            self.assertIn("stats", row["artifacts"])
            self.assertTrue(row["artifacts"]["routeGpu"].endswith("/route-gpu.json"))
            self.assertTrue(row["artifacts"]["stats"].endswith("/stats.json"))

    def test_invalid_inputs_and_non_registered_effects_keep_stable_refusals(self) -> None:
        evidence = kan033.build_evidence(PROJECT_ROOT)

        refusals = {row["id"]: row["fallbackReason"] for row in evidence["stableRefusals"]}
        self.assertEqual(
            "runtime-effect.preview-uniform-out-of-range",
            refusals["uniform-out-of-range"],
        )
        self.assertEqual(
            "runtime-effect.preview-effect-not-registered",
            refusals["effect-not-registered"],
        )
        self.assertEqual(
            "runtime-effect.arbitrary-sksl-unsupported",
            refusals["arbitrary-sksl"],
        )
        invalid_edits = [effect["invalidEdit"] for effect in evidence["effects"]]
        self.assertEqual(["clamp", "refuse"], [edit["policy"] for edit in invalid_edits])

    def test_write_outputs_materializes_pm_json_routes_and_pngs(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan033.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "runtime-effect-uniform-preview.json").read_text())
            telemetry = json.loads((output_dir / "runtime-effect-uniform-preview-telemetry.json").read_text())
            edited_states = json.loads((output_dir / "runtime-effect-uniform-preview-edited-states.json").read_text())
            markdown = (output_dir / "runtime-effect-uniform-preview.md").read_text()

            for row in evidence["editedStates"]:
                state_dir = output_dir / "states" / row["id"]
                self.assertTrue((state_dir / "cpu.png").is_file())
                self.assertTrue((state_dir / "gpu.png").is_file())
                self.assertTrue((state_dir / "diff.png").is_file())
                self.assertTrue((state_dir / "route-gpu.json").is_file())
                self.assertTrue((state_dir / "stats.json").is_file())
                stats = json.loads((state_dir / "stats.json").read_text())
                self.assertEqual(row["id"], stats["sceneId"])
                self.assertEqual("none", stats["fallbackReason"])

        self.assertEqual(evidence["counts"], payload["counts"])
        self.assertEqual(4, len(telemetry["rows"]))
        self.assertTrue(all("effectStableId" in row for row in telemetry["rows"]))
        self.assertTrue(all(row["compileCountBefore"] == row["compileCountAfter"] for row in telemetry["rows"]))
        self.assertEqual(4, len(edited_states["states"]))
        self.assertIn("# KAN-033 Runtime Effect Uniform Preview", markdown)
        self.assertIn("No live SkSL editor", markdown)
        self.assertIn("Kadre native: `opt-in`", markdown)


if __name__ == "__main__":
    unittest.main()
