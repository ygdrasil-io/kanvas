#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan034_runtime_effects_v2_evidence_bundle as kan034


class RuntimeEffectsV2EvidenceBundleTest(unittest.TestCase):
    def test_build_evidence_lists_every_runtime_effect_v2_row(self) -> None:
        evidence = kan034.build_evidence(PROJECT_ROOT)

        self.assertEqual("kan-034-runtime-effects-v2-evidence-bundle-v1", evidence["packId"])
        self.assertEqual("pass", evidence["status"])
        self.assertEqual(
            {
                "totalRows": 8,
                "descriptorBacked": 6,
                "gpuBacked": 4,
                "cpuOnly": 2,
                "expectedUnsupported": 2,
                "dependencyGated": 0,
                "layoutMatched": 4,
                "supportClaimsWithArtifacts": 4,
                "unsupportedRowsVisible": 4,
                "previewEditedStates": 4,
                "missingArtifacts": 0,
                "hiddenBroadClaims": 0,
            },
            evidence["counts"],
        )
        self.assertEqual(
            [
                "policy.arbitrary_sksl_input",
                "policy.unregistered_wgsl_descriptor",
                "runtime.color_filter_luma_to_alpha",
                "runtime.invert_blender",
                "runtime.linear_gradient_rt",
                "runtime.simple_rt",
                "runtime.spiral_rt",
                "runtime.unsharp_rt",
            ],
            [row["stableId"] for row in evidence["rows"]],
        )

    def test_support_claims_have_artifacts_and_refusals_stay_visible(self) -> None:
        evidence = kan034.build_evidence(PROJECT_ROOT)
        by_id = {row["stableId"]: row for row in evidence["rows"]}

        for stable_id in (
            "runtime.color_filter_luma_to_alpha",
            "runtime.linear_gradient_rt",
            "runtime.simple_rt",
            "runtime.spiral_rt",
        ):
            row = by_id[stable_id]
            self.assertTrue(row["supportClaim"])
            self.assertEqual("gpu-backed", row["supportState"])
            artifacts = row["artifactGroups"]["primarySupport"]
            self.assertIn("stats", artifacts)
            self.assertTrue(any(key.lower().startswith("route") for key in artifacts))

        self.assertFalse(by_id["runtime.unsharp_rt"]["supportClaim"])
        self.assertEqual("cpu-only", by_id["runtime.unsharp_rt"]["supportState"])
        self.assertIn(
            "runtime-effect.child-binding-unsupported",
            by_id["runtime.unsharp_rt"]["specializedEvidence"]["stableRefusals"],
        )
        self.assertEqual(
            "runtime-effect.blender-dst-read-unsupported",
            by_id["runtime.invert_blender"]["fallbackReason"],
        )
        self.assertIn(
            "runtime-effect.preview-effect-not-registered",
            evidence["stableRefusals"],
        )
        self.assertIn(
            "No dynamic SkSL compilation.",
            evidence["nonClaims"],
        )
        self.assertIn(
            "No SkSL IR or VM.",
            evidence["nonClaims"],
        )

    def test_write_outputs_materializes_pm_bundle_and_artifact_audit(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan034.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "evidence.json").read_text())
            markdown = (output_dir / "evidence.md").read_text()

        self.assertEqual(evidence["counts"], payload["counts"])
        self.assertEqual(8, len(payload["rows"]))
        self.assertEqual([], payload["artifactAudit"]["missing"])
        self.assertEqual([], payload["claimAudit"]["hiddenBroadClaims"])
        self.assertIn("# KAN-034 Runtime Effects V2 Evidence Bundle", markdown)
        self.assertIn("dynamic SkSL", markdown)
        self.assertIn("runtime-effect.arbitrary-sksl-unsupported", markdown)


if __name__ == "__main__":
    unittest.main()
