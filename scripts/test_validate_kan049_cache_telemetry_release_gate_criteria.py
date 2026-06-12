#!/usr/bin/env python3
import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR = PROJECT_ROOT / "scripts" / "validate_kan049_cache_telemetry_release_gate_criteria.py"
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))


class CacheTelemetryReleaseGateCriteriaTest(unittest.TestCase):
    def setUp(self) -> None:
        self.assertTrue(VALIDATOR.is_file(), f"missing validator: {VALIDATOR}")
        global kan049
        import validate_kan049_cache_telemetry_release_gate_criteria as kan049

    def test_build_evidence_classifies_counters_and_keeps_m85_derived(self) -> None:
        evidence = kan049.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-049", evidence["ticket"])
        self.assertEqual("kan-049-cache-telemetry-release-gate-criteria", evidence["packId"])
        self.assertEqual("pass", evidence["status"])
        self.assertFalse(evidence["releaseBlockingChange"])
        self.assertFalse(evidence["readinessDelta"])

        rows = {row["id"]: row for row in evidence["counterRows"]}
        self.assertEqual(
            {"observed", "observed-partial", "derived", "unavailable"},
            {row["sourceClass"] for row in rows.values()},
        )

        observed = rows["headless-webgpu.pipelineCacheHits"]
        self.assertEqual("observed", observed["sourceClass"])
        self.assertEqual("SkWebGpuDevice.cacheTelemetrySnapshot()", observed["provenance"]["sourceApi"])
        self.assertTrue(observed["countsAsObservedWebGpuCacheTelemetry"])
        self.assertFalse(observed["releaseBlocking"])

        derived = rows["m85-derived.pipelineCacheHits"]
        self.assertEqual("derived", derived["sourceClass"])
        self.assertEqual(
            "reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json",
            derived["provenance"]["sourceArtifact"],
        )
        self.assertFalse(derived["countsAsObservedWebGpuCacheTelemetry"])
        self.assertFalse(derived["countedAsCacheReadinessGate"])

        partial = rows["native-route.pipelineCreates"]
        self.assertEqual("observed-partial", partial["sourceClass"])
        self.assertIn("pipeline", partial["missingCounterFamilies"])
        self.assertFalse(partial["releaseBlocking"])

        unavailable = rows["native-callbacks.broadWebGpuCacheHitCallbacks"]
        self.assertEqual("unavailable", unavailable["sourceClass"])
        self.assertEqual("kadre-runtime.native-cache-counter-unavailable", unavailable["stableMissingCounterReason"])
        self.assertFalse(unavailable["releaseBlocking"])

        gate = evidence["gateCriteria"]
        self.assertEqual("cacheTelemetry.observed-counter.candidate", gate["candidate"]["name"])
        self.assertEqual("cacheTelemetry.observed-counter.release-blocking", gate["releaseBlocking"]["name"])
        self.assertEqual("observed", gate["candidate"]["allowedSourceClass"])
        self.assertIn("negative fixture", " ".join(gate["candidate"]["requires"]))

        fixture = evidence["negativeFixture"]
        self.assertEqual("expected-fail", fixture["status"])
        self.assertFalse(fixture["mutatesBaseline"])
        self.assertGreaterEqual(len(fixture["cases"]), 4)

        freeze = evidence["gateFreezeDelta"]
        self.assertEqual([], freeze["changedReleaseBlockingGates"])
        self.assertEqual([], freeze["promotedCacheTelemetryGates"])
        self.assertIn("m85 resource/cache ledger", freeze["baselinePerformanceGates"])

    def test_validation_rejects_m85_derived_row_reclassified_as_observed(self) -> None:
        evidence = kan049.build_evidence(PROJECT_ROOT)
        evidence["counterRows"] = copy.deepcopy(evidence["counterRows"])
        row = next(row for row in evidence["counterRows"] if row["id"] == "m85-derived.pipelineCacheHits")
        row["sourceClass"] = "observed"
        row["countsAsObservedWebGpuCacheTelemetry"] = True

        with self.assertRaisesRegex(kan049.ValidationError, "M85 derived ledger counted as observed"):
            kan049.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_unavailable_counter_without_reason(self) -> None:
        evidence = kan049.build_evidence(PROJECT_ROOT)
        evidence["counterRows"] = copy.deepcopy(evidence["counterRows"])
        row = next(row for row in evidence["counterRows"] if row["id"] == "native-callbacks.bindGroupCacheCallbacks")
        row["stableMissingCounterReason"] = ""

        with self.assertRaisesRegex(kan049.ValidationError, "unavailable counter missing stable reason"):
            kan049.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_release_blocking_counter_promotion(self) -> None:
        evidence = kan049.build_evidence(PROJECT_ROOT)
        evidence["counterRows"] = copy.deepcopy(evidence["counterRows"])
        row = next(row for row in evidence["counterRows"] if row["id"] == "headless-webgpu.pipelineCacheHits")
        row["releaseBlocking"] = True

        with self.assertRaisesRegex(kan049.ValidationError, "release-blocking counter"):
            kan049.validate_evidence(evidence, PROJECT_ROOT)

    def test_write_outputs_materializes_expected_files(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan049.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / kan049.OUTPUT_JSON).read_text(encoding="utf-8"))
            fixture = json.loads((output_dir / kan049.OUTPUT_NEGATIVE_FIXTURE).read_text(encoding="utf-8"))
            freeze = json.loads((output_dir / kan049.OUTPUT_GATE_FREEZE_DELTA).read_text(encoding="utf-8"))
            markdown = (output_dir / kan049.OUTPUT_MARKDOWN).read_text(encoding="utf-8")

        self.assertEqual(evidence["summary"], payload["summary"])
        self.assertEqual("expected-fail", fixture["status"])
        self.assertEqual([], freeze["changedReleaseBlockingGates"])
        self.assertIn("# KAN-049 Cache Telemetry Release-Gate Criteria", markdown)
        self.assertIn("observed-partial", markdown)
        self.assertIn("derived", markdown)
        self.assertIn("unavailable", markdown)


if __name__ == "__main__":
    unittest.main()
