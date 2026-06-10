#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan030_runtime_child_shader_effect_lane as kan030


class RuntimeChildShaderEffectLaneTest(unittest.TestCase):
    def test_build_evidence_records_unsharp_child_shader_refusal_lane(self) -> None:
        evidence = kan030.build_evidence(PROJECT_ROOT)

        self.assertEqual("kan-030-runtime-child-shader-effect-lane", evidence["packId"])
        self.assertEqual("expected-unsupported", evidence["status"])
        self.assertEqual(
            {
                "totalCandidates": 1,
                "cpuSupported": 1,
                "gpuSupported": 0,
                "gpuExpectedUnsupported": 1,
                "childBindings": 1,
                "uniformValuesInPipelineKey": 0,
            },
            evidence["counts"],
        )
        candidate = evidence["candidates"][0]
        self.assertEqual("runtime.unsharp_rt", candidate["stableId"])
        self.assertEqual("cpu-only", candidate["supportState"])
        self.assertEqual("runtime-effect.child-binding-unsupported", candidate["gpuFallbackReason"])
        self.assertEqual("SkBuiltinShaderEffectsChildrenTest", candidate["cpuOracle"]["testClass"])
        self.assertEqual(
            [
                {
                    "name": "child",
                    "index": 0,
                    "type": "kShader",
                    "binding": "child[0]",
                    "bindingState": "cpu-supported-gpu-unsupported",
                    "resourceAxis": "childShader",
                    "resourceAxisState": "classified-not-in-pipeline-key",
                    "fallbackReason": "runtime-effect.child-binding-unsupported",
                }
            ],
            candidate["children"],
        )
        self.assertFalse(candidate["pipelineKeyPolicy"]["uniformValuesIncluded"])
        self.assertNotIn("uniformBytes", candidate["pipelineKeyPolicy"]["acceptedAxes"])

    def test_validate_evidence_rejects_gpu_support_claim_without_artifacts(self) -> None:
        evidence = kan030.build_evidence(PROJECT_ROOT)
        evidence["counts"]["gpuSupported"] = 1
        evidence["candidates"][0]["gpuSupportState"] = "supported"

        with self.assertRaisesRegex(kan030.ValidationError, "GPU support must remain refused"):
            kan030.validate_evidence(PROJECT_ROOT, evidence)

    def test_write_outputs_materializes_json_markdown_and_route(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan030.write_outputs(PROJECT_ROOT, output_dir)

            payload = json.loads((output_dir / "runtime-child-shader-effect-lane.json").read_text())
            route = json.loads((output_dir / "runtime-child-shader-effect-lane-route.json").read_text())
            markdown = (output_dir / "runtime-child-shader-effect-lane.md").read_text()

        self.assertEqual(evidence["counts"], payload["counts"])
        self.assertEqual("expected-unsupported", route["status"])
        self.assertEqual("runtime-effect.child-binding-unsupported", route["fallbackReason"])
        self.assertIn("# Runtime Child Shader Effect Lane", markdown)
        self.assertIn("runtime.unsharp_rt", markdown)
        self.assertIn("No GPU child shader support claim", markdown)


if __name__ == "__main__":
    unittest.main()
