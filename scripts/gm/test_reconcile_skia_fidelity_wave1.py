import contextlib
import hashlib
import importlib.util
import io
import json
import pathlib
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name("reconcile_skia_fidelity_wave1.py")
SPEC = importlib.util.spec_from_file_location("reconcile_skia_fidelity_wave1", SCRIPT)
reconcile = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(reconcile)


REQUIRED_MANIFEST_FIELDS = {
    "status",
    "populationPolicy",
    "policy",
    "dashboard",
    "scoreFile",
    "rows",
    "current",
    "supportedRowsAfter",
    "routeOnlyRows",
    "routeOnlyRowsPromoted",
    "escalation",
}


class ReconcileSkiaFidelityWave1Test(unittest.TestCase):
    def setUp(self):
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tempdir.name)

    def tearDown(self):
        self.tempdir.cleanup()

    def write_text(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def write_json(self, name, value):
        return self.write_text(name, json.dumps(value, indent=2, sort_keys=True) + "\n")

    def write_runner(self, name, testcases, tests=None, failures=None, errors=None, skipped=None):
        tests = len(testcases) if tests is None else tests
        failures = sum("<failure" in testcase for testcase in testcases) if failures is None else failures
        errors = sum("<error" in testcase for testcase in testcases) if errors is None else errors
        skipped = sum("<skipped" in testcase for testcase in testcases) if skipped is None else skipped
        xml = (
            '<testsuite tests="%s" failures="%s" errors="%s" skipped="%s">%s</testsuite>'
            % (tests, failures, errors, skipped, "".join(testcases))
        )
        return self.write_text(name, xml)

    @staticmethod
    def testcase(name, body="", classname="SkiaGmRunner"):
        return '<testcase name="%s" classname="%s">%s</testcase>' % (
            name,
            classname,
            body,
        )

    def write_cli_fixtures(
        self,
        runner_before=None,
        runner_after=None,
        dashboard_output=None,
        dashboard_data=None,
        score_before="supported=85.0\nroute-only=99.0\n",
        score_after="supported=98.0\nroute-only=99.0\n",
        test_oracle=None,
        cpu_oracle=None,
    ):
        if runner_before is None:
            runner_before = self.write_runner(
                "runner-before.xml",
                [
                    self.testcase(
                        "supported",
                        '<failure message="similarity 85.0 below threshold 95.0"/>',
                    ),
                    self.testcase("route-only"),
                ],
            )
        if runner_after is None:
            runner_after = self.write_runner(
                "runner-after.xml",
                [self.testcase("supported"), self.testcase("route-only")],
            )
        if dashboard_output is None:
            dashboard_output = {
                "gms": [
                    {
                        "name": "supported",
                        "referenceKind": "skia-upstream",
                        "isPassing": True,
                        "similarity": 98.0,
                        "routeOnly": False,
                    },
                    {
                        "name": "route-only",
                        "referenceKind": "test-oracle",
                        "isPassing": True,
                        "similarity": 99.0,
                        "routeOnly": True,
                    },
                ]
            }
        if dashboard_data is None:
            dashboard_data = {
                "rows": [
                    {
                        "name": "supported",
                        "referenceKind": "skia-upstream",
                        "score": 98.0,
                    },
                    {
                        "name": "route-only",
                        "referenceKind": "test-oracle",
                        "score": 99.0,
                    },
                ]
            }
        if test_oracle is None:
            test_oracle = {"rows": [{"name": "supported", "referenceKind": "test-oracle"}]}
        if cpu_oracle is None:
            cpu_oracle = {"rows": [{"name": "supported", "referenceKind": "cpu-oracle"}]}

        wave0 = self.write_runner("wave0-runner.xml", [], tests=615)
        dashboard_output_path = self.write_json("dashboard/output.json", dashboard_output)
        dashboard_data_path = self.write_json("dashboard/data.json", dashboard_data)
        score_before_path = self.write_text("scores/before.properties", score_before)
        score_after_path = self.write_text("scores/after.properties", score_after)
        test_oracle_path = self.write_json("oracles/test.json", test_oracle)
        cpu_oracle_path = self.write_json("oracles/cpu.json", cpu_oracle)
        return {
            "wave0": wave0,
            "runnerBefore": runner_before,
            "runnerAfter": runner_after,
            "dashboardOutput": dashboard_output_path,
            "dashboardData": dashboard_data_path,
            "scoreBefore": score_before_path,
            "scoreAfter": score_after_path,
            "testOracle": test_oracle_path,
            "cpuOracle": cpu_oracle_path,
        }

    def cli_args(self, fixtures, output_json, output_markdown, check=False):
        args = [
            "--wave0-runner",
            str(fixtures["wave0"]),
            "--runner-before",
            str(fixtures["runnerBefore"]),
            "--runner-after",
            str(fixtures["runnerAfter"]),
            "--dashboard-output",
            str(fixtures["dashboardOutput"]),
            "--dashboard-data",
            str(fixtures["dashboardData"]),
            "--score-before",
            str(fixtures["scoreBefore"]),
            "--score-after",
            str(fixtures["scoreAfter"]),
            "--test-oracle",
            str(fixtures["testOracle"]),
            "--cpu-oracle",
            str(fixtures["cpuOracle"]),
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

    def run_main(self, fixtures, check=False):
        output_json = self.root / "reports" / "wave1.json"
        output_markdown = self.root / "reports" / "wave1.md"
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            status = reconcile.main(
                self.cli_args(fixtures, output_json, output_markdown, check=check)
            )
        manifest = (
            json.loads(output_json.read_text(encoding="utf-8"))
            if output_json.is_file()
            else None
        )
        return status, output.getvalue(), manifest, output_json, output_markdown

    @staticmethod
    def sha256(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    @staticmethod
    def rows_by_name(rows):
        return {row["name"]: row for row in rows}

    def test_manifest_declares_classification_and_include_blocking_population_policy(self):
        fixtures = self.write_cli_fixtures()
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertIsNotNone(manifest)
        self.assertTrue(REQUIRED_MANIFEST_FIELDS.issubset(manifest))
        self.assertNotIn("population_policy", manifest)
        self.assertEqual(manifest["status"], "classification")

        population = manifest["populationPolicy"]
        self.assertTrue(population["includeBlocking"])
        self.assertEqual(
            population["runnerProperty"],
            "-Dkanvas.gm.includeBlocking=true",
        )
        self.assertEqual(population["dashboardProperty"], "-Pgm.includeBlocking=true")
        self.assertEqual(population["wave0Population"], 615)
        self.assertFalse(population["wave0DirectlyComparable"])

        policy = manifest["policy"]
        self.assertFalse(policy["scoresDirectlyEdited"])
        self.assertFalse(policy["globalThresholdWeakened"])
        self.assertEqual(policy["readinessDelta"], 0.0)

    def test_dashboard_provenance_records_output_data_paths_and_sha256_hashes(self):
        fixtures = self.write_cli_fixtures()
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        dashboard = manifest["dashboard"]
        self.assertEqual(dashboard["outputPath"], str(fixtures["dashboardOutput"]))
        self.assertEqual(dashboard["dataPath"], str(fixtures["dashboardData"]))
        self.assertEqual(dashboard["outputSha256"], self.sha256(fixtures["dashboardOutput"]))
        self.assertEqual(dashboard["dataSha256"], self.sha256(fixtures["dashboardData"]))

    def test_score_before_after_integrity_and_runner_side_effect_restore_are_reported(self):
        fixtures = self.write_cli_fixtures()
        before = {name: path.read_bytes() for name, path in fixtures.items()}
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        score_file = manifest["scoreFile"]
        self.assertEqual(score_file["beforePath"], str(fixtures["scoreBefore"]))
        self.assertEqual(score_file["afterPath"], str(fixtures["scoreAfter"]))
        self.assertEqual(score_file["beforeSha256"], self.sha256(fixtures["scoreBefore"]))
        self.assertEqual(score_file["afterSha256"], self.sha256(fixtures["scoreAfter"]))
        self.assertTrue(score_file["integrityPreserved"])
        self.assertFalse(score_file["directEdit"])
        self.assertTrue(score_file["restored"])
        self.assertFalse(manifest["policy"]["scoresDirectlyEdited"])

        runner = manifest["current"]["runner"]
        self.assertTrue(runner["sideEffect"])
        self.assertTrue(runner["restored"])
        self.assertEqual(before, {name: path.read_bytes() for name, path in fixtures.items()})

    def test_reference_lanes_keep_skia_upstream_test_oracle_and_cpu_oracle_distinct(self):
        fixtures = self.write_cli_fixtures()
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        rows = manifest["rows"]
        self.assertIsInstance(rows, list)
        reference_kinds = {row["referenceKind"] for row in rows}
        self.assertIn("skia-upstream", reference_kinds)
        self.assertIn("test-oracle", reference_kinds)
        self.assertIn("cpu-oracle", reference_kinds)
        self.assertNotEqual(
            self.rows_by_name(rows)["supported"]["referenceKind"],
            "test-oracle",
        )

    def test_runner_classifies_missing_size_similarity_terminal_and_aborted_rows(self):
        runner = self.write_runner(
            "classification-runner.xml",
            [
                self.testcase(
                    "missing-reference",
                    '<failure message="Reference PNG not found at /reference/missing.png"/>',
                ),
                self.testcase(
                    "size-mismatch",
                    '<failure message="Buffer sizes differ: expected 64x64, got 32x32"/>',
                ),
                self.testcase(
                    "similarity-failure",
                    '<failure message="similarity 25.0 below threshold 95.0"/>',
                ),
                self.testcase(
                    "terminal-refusal",
                    '<error type="GPUPreparedSurfaceTerminalException" message="terminal refusal"/>',
                ),
                self.testcase(
                    "unclassified-error",
                    '<error message="unexpected renderer error"/>',
                ),
                self.testcase(
                    "aborted-blocking",
                    '<skipped message="GM is BLOCKING" type="org.opentest4j.TestAbortedException"/>',
                ),
            ],
        )
        fixtures = self.write_cli_fixtures(
            runner_after=runner,
            dashboard_output={"gms": []},
            dashboard_data={"rows": []},
            test_oracle={"rows": []},
            cpu_oracle={"rows": []},
        )
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        rows = self.rows_by_name(manifest["rows"])
        self.assertEqual(rows["missing-reference"]["classification"], "missing-reference")
        self.assertEqual(rows["size-mismatch"]["classification"], "size-mismatch")
        self.assertEqual(rows["similarity-failure"]["classification"], "similarity-failure")
        self.assertEqual(rows["terminal-refusal"]["classification"], "terminal-refusal")
        self.assertTrue(rows["terminal-refusal"]["terminalRefusal"])
        self.assertEqual(rows["unclassified-error"]["classification"], "unclassified")
        self.assertEqual(rows["aborted-blocking"]["failureCode"], "TestAbortedException")
        self.assertEqual(rows["aborted-blocking"]["classification"], "skip")

    def test_comparable_row_counters_promote_supported_row_but_not_route_only_row(self):
        fixtures = self.write_cli_fixtures()
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        current = manifest["current"]
        self.assertEqual(current["comparableRowsBefore"], 1)
        self.assertEqual(current["comparableRowsAfter"], 1)
        self.assertEqual(current["comparableRowsImproved"], 1)
        self.assertEqual(current["routeOnlyRows"], 1)
        self.assertEqual(manifest["supportedRowsAfter"], ["supported"])
        self.assertEqual(manifest["routeOnlyRows"], ["route-only"])
        self.assertEqual(manifest["routeOnlyRowsPromoted"], [])

    def test_escalation_limits_failed_hypotheses_to_three(self):
        runner = self.write_runner(
            "escalation-runner.xml",
            [
                self.testcase(
                    "failure-%s" % index,
                    '<error message="unsupported.route.%s"/>' % index,
                )
                for index in range(5)
            ],
        )
        fixtures = self.write_cli_fixtures(
            runner_after=runner,
            dashboard_output={"gms": []},
            dashboard_data={"rows": []},
            test_oracle={"rows": []},
            cpu_oracle={"rows": []},
        )
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        escalation = manifest["escalation"]
        self.assertEqual(escalation["maxFailedHypotheses"], 3)
        self.assertLessEqual(len(escalation["failedHypotheses"]), 3)

    def test_check_rejects_missing_dashboard_without_writing_report(self):
        fixtures = self.write_cli_fixtures()
        fixtures["dashboardOutput"].unlink()
        status, stdout, manifest, output_json, output_markdown = self.run_main(
            fixtures, check=True
        )

        self.assertNotEqual(status, 0)
        self.assertIn("dashboard", stdout.lower())
        self.assertIsNone(manifest)
        self.assertFalse(output_json.exists())
        self.assertFalse(output_markdown.exists())

    def test_check_rejects_malformed_dashboard_without_mutating_fixture_inputs(self):
        fixtures = self.write_cli_fixtures()
        before = {name: path.read_bytes() for name, path in fixtures.items()}
        fixtures["dashboardOutput"].write_text("{ malformed json\n", encoding="utf-8")
        malformed_before = fixtures["dashboardOutput"].read_bytes()
        status, stdout, manifest, output_json, output_markdown = self.run_main(
            fixtures, check=True
        )

        self.assertNotEqual(status, 0)
        self.assertIn("dashboard", stdout.lower())
        self.assertIsNone(manifest)
        self.assertFalse(output_json.exists())
        self.assertFalse(output_markdown.exists())
        self.assertEqual(fixtures["dashboardOutput"].read_bytes(), malformed_before)
        for name, path in fixtures.items():
            if name != "dashboardOutput":
                self.assertEqual(path.read_bytes(), before[name])

    def test_check_rejects_unclassified_runner_error(self):
        runner = self.write_runner(
            "unclassified-runner.xml",
            [self.testcase("unknown", '<error message="unclassified renderer error"/>')],
        )
        fixtures = self.write_cli_fixtures(
            runner_after=runner,
            dashboard_output={"gms": []},
            dashboard_data={"rows": []},
            test_oracle={"rows": []},
            cpu_oracle={"rows": []},
        )
        status, stdout, manifest, output_json, output_markdown = self.run_main(
            fixtures, check=True
        )

        self.assertNotEqual(status, 0)
        self.assertIn("unclassified", stdout.lower())
        self.assertTrue(output_json.exists())
        self.assertTrue(output_markdown.exists())
        self.assertIsNotNone(manifest)
        rows = self.rows_by_name(manifest["rows"])
        self.assertEqual(rows["unknown"]["classification"], "unclassified")

    def test_successful_report_does_not_mutate_any_fixture_input(self):
        fixtures = self.write_cli_fixtures()
        before = {name: path.read_bytes() for name, path in fixtures.items()}
        status, stdout, _, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(before, {name: path.read_bytes() for name, path in fixtures.items()})


if __name__ == "__main__":
    unittest.main()
