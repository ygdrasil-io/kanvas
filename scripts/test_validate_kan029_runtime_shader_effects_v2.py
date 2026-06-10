#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan029_runtime_shader_effects_v2 as kan029


class RuntimeShaderEffectsV2PromotionTest(unittest.TestCase):
    def test_build_evidence_promotes_three_registered_shader_effects(self) -> None:
        evidence = kan029.build_evidence(PROJECT_ROOT)

        self.assertEqual("kan-029-runtime-shader-effects-v2-promotion", evidence["packId"])
        self.assertEqual("pass", evidence["status"])
        self.assertEqual(
            {
                "total": 3,
                "supported": 3,
                "fallbackNone": 3,
                "layoutMatched": 3,
                "belowThreshold": 0,
                "missingArtifacts": 0,
            },
            evidence["counts"],
        )
        self.assertEqual(
            ["runtime.linear_gradient_rt", "runtime.simple_rt", "runtime.spiral_rt"],
            [row["stableId"] for row in evidence["effects"]],
        )
        for row in evidence["effects"]:
            self.assertEqual("pass", row["status"])
            self.assertEqual("none", row["fallbackReason"])
            self.assertEqual("gpu-backed", row["supportState"])
            self.assertEqual("layout-matched", row["layoutStatus"])
            self.assertGreaterEqual(row["cpuSimilarity"], row["threshold"])
            self.assertGreaterEqual(row["gpuSimilarity"], row["threshold"])
            self.assertTrue(row["parserEvidence"])
            self.assertTrue(row["renderEvidence"])
            self.assertFalse(row["pipelineKeyPolicy"]["uniformValuesIncluded"])
            for artifact in row["artifacts"].values():
                self.assertTrue((PROJECT_ROOT / artifact).is_file(), artifact)

    def test_validate_evidence_rejects_missing_visual_artifact(self) -> None:
        evidence = kan029.build_evidence(PROJECT_ROOT)
        evidence["effects"][0]["artifacts"]["gpu"] = "reports/wgsl-pipeline/missing-kan029-gpu.png"

        with self.assertRaisesRegex(kan029.ValidationError, "missing referenced artifact"):
            kan029.validate_evidence(PROJECT_ROOT, evidence)

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan029.write_outputs(PROJECT_ROOT, output_dir)

            payload = json.loads((output_dir / "runtime-shader-effects-v2-promotion.json").read_text())
            markdown = (output_dir / "runtime-shader-effects-v2-promotion.md").read_text()

        self.assertEqual(evidence["counts"], payload["counts"])
        self.assertIn("# Runtime Shader Effects V2 Promotion", markdown)
        self.assertIn("Status counts: total=3; supported=3; fallback-none=3; layout-matched=3; below-threshold=0; missing-artifacts=0.", markdown)
        self.assertIn("runtime.simple_rt", markdown)
        self.assertIn("No dynamic SkSL compilation", markdown)


if __name__ == "__main__":
    unittest.main()
