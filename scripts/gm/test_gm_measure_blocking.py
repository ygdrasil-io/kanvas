import importlib.util
import json
import pathlib
import unittest


SCRIPT = pathlib.Path(__file__).with_name("gm_measure_blocking.py")
SPEC = importlib.util.spec_from_file_location("gm_measure_blocking", SCRIPT)
gm_measure_blocking = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gm_measure_blocking)


class ClassifyAttemptsTest(unittest.TestCase):
    def test_three_completed_samples_below_one_second_are_fast(self):
        result = gm_measure_blocking.classify_attempts([
            {"record": "PASS|0|alpha|220"},
            {"record": "PASS|1|alpha|780"},
            {"record": "PASS|2|alpha|920"},
        ])

        self.assertEqual("FAST", result["tag"])
        self.assertEqual(780, result["medianMs"])
        self.assertEqual(0, result["timeoutCount"])
        self.assertEqual(0, result["errorCount"])
        self.assertEqual("median below 1000 ms", result["classificationReason"])

    def test_two_timeouts_make_the_row_blocking_even_with_a_fast_sample(self):
        result = gm_measure_blocking.classify_attempts([
            {"record": "TIMEOUT|0|alpha"},
            {"record": "PASS|1|alpha|80"},
            {"record": "TIMEOUT|2|alpha"},
        ])

        self.assertEqual("BLOCKING", result["tag"])
        self.assertIsNone(result["medianMs"])
        self.assertEqual(2, result["timeoutCount"])
        self.assertEqual(0, result["errorCount"])
        self.assertEqual("two or more attempts timed out", result["classificationReason"])

    def test_device_lost_error_is_explicitly_incomplete_or_error(self):
        result = gm_measure_blocking.classify_attempts([
            {"record": "PASS|0|alpha|80"},
            {"record": "FAIL|1|alpha|device-lost"},
            {"record": "PASS|2|alpha|90"},
        ])

        self.assertEqual("BLOCKING", result["tag"])
        self.assertEqual(1, result["errorCount"])
        self.assertIn("device-lost", result["classificationReason"])

    def test_attempt_count_must_be_three(self):
        with self.assertRaisesRegex(ValueError, "exactly three"):
            gm_measure_blocking.classify_attempts([
                {"record": "PASS|0|alpha|80"},
                {"record": "PASS|1|alpha|90"},
            ])


class DeterministicReportTest(unittest.TestCase):
    def test_report_is_sorted_and_preserves_raw_samples_and_provenance(self):
        report = gm_measure_blocking.build_report(
            {
                "zeta": [{"record": "PASS|0|zeta|10"}] * 3,
                "alpha": [{"record": "PASS|0|alpha|10"}] * 3,
            },
            timed_out_batches=[["zeta", "alpha"]],
            provenance={"backend": "webgpu", "gitHead": "abc", "jdk": "21", "os": "test-os"},
        )

        self.assertEqual(["alpha", "zeta"], [row["name"] for row in report["rows"]])
        self.assertEqual(1, report["schemaVersion"])
        self.assertEqual([["alpha", "zeta"]], report["timedOutBatches"])
        self.assertEqual("PASS|0|alpha|10", report["rows"][0]["rawSamples"][0])
        encoded = gm_measure_blocking.to_sorted_json(report)
        self.assertEqual(encoded, json.dumps(json.loads(encoded), indent=2, sort_keys=True) + "\n")
        markdown = gm_measure_blocking.to_markdown(report)
        self.assertIn("- schemaVersion: `1`", markdown)
        self.assertIn("## Provenance", markdown)
        self.assertIn(r"`PASS\|0\|alpha\|10`", markdown)

    def test_markdown_escapes_pipes_in_raw_attempt_samples(self):
        report = gm_measure_blocking.build_report(
            {
                "alpha": [
                    {"record": "FAIL|0|alpha|device|lost"},
                    {"record": "FAIL|1|alpha|device|lost"},
                    {"record": "FAIL|2|alpha|device|lost"},
                ],
            },
            timed_out_batches=[],
            provenance={"backend": "webgpu", "gitHead": "abc", "jdk": "21", "os": "test-os"},
        )

        markdown = gm_measure_blocking.to_markdown(report)
        self.assertIn(r"`FAIL\|0\|alpha\|device\|lost`", markdown)
        self.assertNotIn("`FAIL|0|alpha|device|lost`", markdown)


if __name__ == "__main__":
    unittest.main()
