import importlib.util
import json
import pathlib
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name("reconcile_skia_fidelity_wave0.py")
SPEC = importlib.util.spec_from_file_location("reconcile_skia_fidelity_wave0", SCRIPT)
reconcile = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(reconcile)


class ReconcileSkiaFidelityWave0Test(unittest.TestCase):
    def setUp(self):
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tempdir.name)

    def tearDown(self):
        self.tempdir.cleanup()

    def write_text(self, name, content):
        path = self.root / name
        path.write_text(content, encoding="utf-8")
        return path

    def write_json(self, name, value):
        return self.write_text(name, json.dumps(value))

    def write_xml(self, content):
        return self.write_text("results.xml", content)

    def test_classifies_terminal_memory_failure_as_unexpected(self):
        xml = """<testsuite tests="1" failures="1" skipped="0">
          <testcase name="render GM" classname="SkiaGmRunner">
            <failure message="unsupported.frame_memory.aggregate_budget_exceeded"/>
          </testcase>
        </testsuite>"""
        result = reconcile.parse_skia_runner(self.write_xml(xml))
        self.assertEqual(result["unexpectedFailures"], 1)
        self.assertEqual(
            result["rows"][0]["failureCode"],
            "unsupported.frame_memory.aggregate_budget_exceeded",
        )
        self.assertTrue(result["rows"][0]["terminal"])

    def test_keeps_cpu_oracle_and_skia_rows_distinct(self):
        dashboard_path = self.write_json(
            "dashboard.json", {"gms": [{"name": "draw", "similarity": 99.0}]}
        )
        dashboard = reconcile.parse_dashboard(dashboard_path)
        result = reconcile.build_delta(
            {"dashboard": dashboard, "cpuOracleRows": ["draw"]}, "abc123"
        )
        self.assertEqual(result["rows"]["skia"][0]["referenceKind"], "unknown")
        self.assertEqual(
            result["rows"]["cpuOracle"][0]["referenceKind"], "cpu-oracle"
        )

    def test_svg_terminal_error_is_not_a_skip(self):
        xml = """<testsuite tests="1" failures="1" skipped="0">
          <testcase name="texture-3" classname="SvgIntegrationTest">
            <failure message="failed.surface.prepared.session-close"/>
          </testcase>
        </testsuite>"""
        result = reconcile.parse_svg_results(self.write_xml(xml))
        self.assertEqual(result["failures"], 1)
        self.assertEqual(result["skips"], 0)
        self.assertEqual(result["rows"][0]["classification"], "lifecycle-failure")
        self.assertTrue(result["rows"][0]["terminal"])

    def test_historical_fp13_is_context_only(self):
        fp13_path = self.write_xml(
            '<testsuite tests="615" failures="498" skipped="40" errors="0"/>'
        )
        fp13 = reconcile.parse_skia_runner(fp13_path)
        result = reconcile.build_delta({"fp13": fp13}, "abc123")
        self.assertFalse(result["inputs"]["fp13"]["acceptanceBaseline"])
        self.assertEqual(result["policy"]["readinessDelta"], 0.0)

    def test_runner_keeps_reference_and_similarity_failures_separate(self):
        xml = """<testsuite tests="5" failures="3" skipped="1" errors="1">
          <testcase name="missing-reference" classname="SkiaGmRunner">
            <failure message="Reference PNG not found at /reference/missing-reference.png"/>
          </testcase>
          <testcase name="scale-pixels" classname="SkiaGmRunner">
            <failure message="Buffer sizes differ: expected 64x64, got 32x32"/>
          </testcase>
          <testcase name="modecolorfilters" classname="SkiaGmRunner">
            <failure message="similarity 25.079917907714844 below threshold 95.0"/>
          </testcase>
          <testcase name="blocked" classname="SkiaGmRunner">
            <skipped message="GM is blocking" type="org.opentest4j.TestAbortedException"/>
          </testcase>
          <testcase name="unclassified" classname="SkiaGmRunner">
            <error message="unexpected renderer error"/>
          </testcase>
        </testsuite>"""
        result = reconcile.parse_skia_runner(self.write_xml(xml))
        rows = {row["name"]: row for row in result["rows"]}
        self.assertEqual(rows["missing-reference"]["classification"], "missing-reference")
        self.assertTrue(rows["missing-reference"]["missingReference"])
        self.assertEqual(rows["scale-pixels"]["classification"], "size-mismatch")
        self.assertTrue(rows["scale-pixels"]["sizeMismatch"])
        self.assertEqual(rows["modecolorfilters"]["classification"], "similarity-failure")
        self.assertTrue(rows["modecolorfilters"]["similarityFailure"])
        self.assertEqual(rows["blocked"]["outcome"], "skipped")
        self.assertEqual(rows["blocked"]["failureCode"], "TestAbortedException")
        self.assertEqual(result["unclassifiedFailures"], 1)

    def test_scores_preserve_modecolorfilters_and_parse_float_values(self):
        scores = reconcile.load_scores(
            self.write_text(
                "scores.properties",
                "# generated scores\nmodecolorfilters=25.079917907714844\nblank=\n",
            )
        )
        self.assertEqual(scores["modecolorfilters"], 25.079917907714844)
        self.assertNotIn("blank", scores)

    def test_svg_expected_unsupported_codes_are_classified(self):
        codes = [
            "unsupported.core_primitive.geometry.invalid",
            "unsupported.material.linear_gradient_capability_missing",
            "unsupported.geometry.path_key_nondeterministic",
            "unsupported.core_primitive.stencil_edge_fan_budget",
        ]
        testcases = "\n".join(
            '<testcase name="svg-%s" classname="SvgIntegrationTest">'
            '<failure message="%s"/></testcase>' % (code, code)
            for code in codes
        )
        result = reconcile.parse_svg_results(
            self.write_xml(
                '<testsuite tests="4" failures="4" skipped="0">%s</testsuite>'
                % testcases
            )
        )
        self.assertEqual(result["failures"], 4)
        self.assertEqual(
            [row["failureCode"] for row in result["rows"]],
            codes,
        )
        self.assertTrue(all(row["classification"] == "expected-unsupported" for row in result["rows"]))
        self.assertTrue(all(row["expectedUnsupported"] for row in result["rows"]))


if __name__ == "__main__":
    unittest.main()
