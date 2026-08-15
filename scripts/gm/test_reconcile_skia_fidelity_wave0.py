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

    def write_cli_fixtures(self, runner_xml=None, svg_xml=None):
        runner_xml = runner_xml or (
            '<testsuite tests="1" failures="0" errors="0" skipped="0">'
            '<testcase name="draw" classname="SkiaGmRunner"/></testsuite>'
        )
        svg_xml = svg_xml or (
            '<testsuite tests="1" failures="0" errors="0" skipped="0">'
            '<testcase name="draw" classname="SvgIntegrationTest"/></testsuite>'
        )
        return {
            "skia": self.write_text("SkiaGmRunner.xml", runner_xml),
            "dashboard": self.write_json(
                "gms.json", {"gms": [{"name": "draw", "similarity": 99.0}]}
            ),
            "svg": self.write_text("svg.xml", svg_xml),
            "scores": self.write_text("scores.properties", "draw=99.0\n"),
            "fp13": self.write_text(
                "fp13.xml",
                '<testsuite tests="1" failures="0" errors="0" skipped="0">'
                '<testcase name="draw" classname="SkiaGmRunner"/></testsuite>',
            ),
        }

    def cli_args(self, fixtures, output_json, output_markdown, check=False):
        args = [
            "--skia-runner",
            str(fixtures["skia"]),
            "--dashboard-json",
            str(fixtures["dashboard"]),
            "--svg-xml",
            str(fixtures["svg"]),
            "--scores",
            str(fixtures["scores"]),
            "--fp13-runner",
            str(fixtures["fp13"]),
            "--source-commit",
            "abc123",
            "--output-json",
            str(output_json),
            "--output-markdown",
            str(output_markdown),
        ]
        if check:
            args.append("--check")
        return args

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

    def test_skia_known_svg_code_remains_unexpected(self):
        xml = """<testsuite tests="1" failures="1" skipped="0">
          <testcase name="geometry-invalid" classname="SkiaGmRunner">
            <failure message="unsupported.core_primitive.geometry.invalid"/>
          </testcase>
        </testsuite>"""
        result = reconcile.parse_skia_runner(self.write_xml(xml))
        row = result["rows"][0]
        self.assertEqual(row["failureCode"], "unsupported.core_primitive.geometry.invalid")
        self.assertFalse(row["expectedUnsupported"])
        self.assertEqual(row["classification"], "unclassified")
        self.assertEqual(result["unexpectedFailures"], 1)

    def test_extracts_failure_codes_from_type_and_element_text(self):
        xml = """<testsuite tests="2" failures="1" errors="1" skipped="0">
          <testcase name="typed-code" classname="SkiaGmRunner">
            <failure message="render failed" type="unsupported.material.linear_gradient_capability_missing"/>
          </testcase>
          <testcase name="text-code" classname="SkiaGmRunner">
            <error message="render failed" type="java.lang.IllegalStateException">
              failureCode: failed.surface.prepared.session-close
            </error>
          </testcase>
        </testsuite>"""
        result = reconcile.parse_skia_runner(self.write_xml(xml))
        rows = {row["name"]: row for row in result["rows"]}
        self.assertEqual(
            rows["typed-code"]["failureCode"],
            "unsupported.material.linear_gradient_capability_missing",
        )
        self.assertEqual(
            rows["text-code"]["failureCode"],
            "failed.surface.prepared.session-close",
        )

    def test_properties_support_whitespace_escaped_and_continued_entries(self):
        scores = reconcile.load_scores(
            self.write_text(
                "scores.properties",
                "mode\\ colorfilters 25.079917907714844\n"
                "continued=25.0799179077148\\\n"
                " 44\n"
                "escaped=25\\.079917907714844\n",
            )
        )
        self.assertEqual(scores["mode colorfilters"], 25.079917907714844)
        self.assertEqual(scores["continued"], 25.079917907714844)
        self.assertEqual(scores["escaped"], 25.079917907714844)

    def test_main_writes_schema_and_preserves_inputs(self):
        fixtures = self.write_cli_fixtures()
        before = {name: path.read_bytes() for name, path in fixtures.items()}
        output_json = self.root / "out" / "delta.json"
        output_markdown = self.root / "out" / "delta.md"
        status = reconcile.main(
            self.cli_args(fixtures, output_json, output_markdown)
        )
        self.assertEqual(status, 0)
        self.assertTrue(output_json.is_file())
        self.assertTrue(output_markdown.is_file())
        delta = json.loads(output_json.read_text(encoding="utf-8"))
        self.assertEqual(
            set(delta),
            {
                "schemaVersion",
                "kind",
                "generatedBy",
                "sourceCommit",
                "policy",
                "inputs",
                "current",
                "crossLaneDelta",
                "historicalFp13Delta",
                "rows",
                "nonClaims",
            },
        )
        self.assertEqual(delta["kind"], "skia-fidelity-wave-0-delta")
        self.assertEqual(delta["policy"]["readinessDelta"], 0.0)
        self.assertIn("# Skia Fidelity Wave 0 Reconciliation", output_markdown.read_text(encoding="utf-8"))
        self.assertEqual(before, {name: path.read_bytes() for name, path in fixtures.items()})

    def test_main_check_rejects_unclassified_current_error(self):
        fixtures = self.write_cli_fixtures(
            runner_xml=(
                '<testsuite tests="1" failures="0" errors="1" skipped="0">'
                '<testcase name="unknown" classname="SkiaGmRunner">'
                '<error message="unclassified renderer error"/>'
                '</testcase></testsuite>'
            )
        )
        before = fixtures["skia"].read_bytes()
        output_json = self.root / "check" / "delta.json"
        output_markdown = self.root / "check" / "delta.md"
        status = reconcile.main(
            self.cli_args(fixtures, output_json, output_markdown, check=True)
        )
        self.assertEqual(status, 1)
        self.assertTrue(output_json.is_file())
        self.assertTrue(output_markdown.is_file())
        self.assertEqual(fixtures["skia"].read_bytes(), before)

    def test_main_check_rejects_missing_input(self):
        fixtures = self.write_cli_fixtures()
        fixtures["scores"].unlink()
        output_json = self.root / "missing" / "delta.json"
        output_markdown = self.root / "missing" / "delta.md"
        status = reconcile.main(
            self.cli_args(fixtures, output_json, output_markdown, check=True)
        )
        self.assertNotEqual(status, 0)
        self.assertFalse(output_json.exists())
        self.assertFalse(output_markdown.exists())

    def test_main_rejects_hard_link_output_without_mutating_input(self):
        fixtures = self.write_cli_fixtures()
        before = fixtures["skia"].read_bytes()
        output_json = self.root / "hard-linked-output.json"
        output_json.hardlink_to(fixtures["skia"])
        output_markdown = self.root / "hard-linked-output.md"
        status = reconcile.main(
            self.cli_args(fixtures, output_json, output_markdown)
        )
        self.assertNotEqual(status, 0)
        self.assertEqual(fixtures["skia"].read_bytes(), before)
        self.assertFalse(output_markdown.exists())


if __name__ == "__main__":
    unittest.main()
