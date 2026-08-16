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
    def _testcase(name, body="", classname="SkiaGmRunner"):
        return '<testcase name="%s" classname="%s">%s</testcase>' % (
            name,
            classname,
            body,
        )

    def write_cli_fixtures(
        self,
        skia_runner=None,
        dashboard_output=None,
        dashboard_data=None,
        score_before=(
            "modecolorfilters=98.75\n"
            "pass=98.75\n"
            "route-only=99.0\n"
        ),
        score_after=(
            "modecolorfilters=98.75\n"
            "pass=98.75\n"
            "route-only=99.0\n"
        ),
        test_oracle=None,
        cpu_oracle=None,
    ):
        if skia_runner is None:
            skia_runner = self.write_runner(
                "skia-runner.xml",
                [self._testcase("pass"), self._testcase("route-only")],
            )
        if dashboard_output is None:
            dashboard_output = {
                "gms": [
                    {
                        "name": "pass",
                        "referenceKind": "skia-upstream",
                        "isPassing": True,
                        "similarity": 98.0,
                        "routeOnly": False,
                    },
                    {
                        "name": "below-threshold",
                        "referenceKind": "skia-upstream",
                        "isPassing": False,
                        "similarity": 90.0,
                        "routeOnly": False,
                    },
                    {
                        "name": "missing-reference",
                        "referenceKind": "skia-upstream",
                        "isPassing": None,
                        "noReference": True,
                        "noScoreCause": "reference-missing",
                        "routeOnly": False,
                    },
                    {
                        "name": "size-mismatch",
                        "referenceKind": "skia-upstream",
                        "isPassing": None,
                        "sizeMismatch": True,
                        "noScoreCause": "size-mismatch",
                        "routeOnly": False,
                    },
                ]
            }
        if dashboard_data is None:
            dashboard_data = {
                "rows": [
                    {
                        "name": "pass",
                        "referenceKind": "skia-upstream",
                        "score": 98.0,
                    },
                    {
                        "name": "below-threshold",
                        "referenceKind": "skia-upstream",
                        "score": 90.0,
                    },
                    {
                        "name": "missing-reference",
                        "referenceKind": "skia-upstream",
                        "score": None,
                    },
                    {
                        "name": "size-mismatch",
                        "referenceKind": "skia-upstream",
                        "score": None,
                    },
                ]
            }
        if test_oracle is None:
            test_oracle = {"rows": [{"name": "route-only", "referenceKind": "test-oracle"}]}
        if cpu_oracle is None:
            cpu_oracle = {"rows": [{"name": "route-only", "referenceKind": "cpu-oracle"}]}

        svg_xml = self.write_runner(
            "svg-results.xml",
            [self._testcase("pass", classname="SvgIntegrationTest")],
        )
        cpu_results = self.write_json("cpu-results.json", cpu_oracle)
        gpu_results = self.write_json("gpu-results.json", test_oracle)
        fp13_runner = self.write_runner("fp13-runner.xml", [], tests=615)
        dashboard_json_path = self.write_json("dashboard.json", dashboard_output)
        dashboard_dir = self.root / "dashboard-output"
        dashboard_dir.mkdir(parents=True, exist_ok=True)
        dashboard_output_path = self.write_json(
            "dashboard-output/dashboard.json", dashboard_data
        )
        generated_renders = self.root / "generated-renders"
        generated_renders.mkdir(parents=True, exist_ok=True)
        self.write_text("generated-renders/pass.png", "fixture render\n")
        score_before_path = self.write_text("scores/before.properties", score_before)
        score_after_path = self.write_text("scores/after.properties", score_after)
        commands_json = self.write_json(
            "provenance/commands.json",
            {"runner": "./gradlew :integration-tests:skia:test"},
        )
        environment_json = self.write_json(
            "provenance/environment.json",
            {"os": "test", "display": ":99"},
        )
        evidence_index = self.write_json(
            "provenance/evidence-index.json",
            {"entries": [{"name": "pass", "referenceKind": "skia-upstream"}]},
        )
        return {
            "skiaRunner": skia_runner,
            "dashboardJson": dashboard_json_path,
            "dashboardDir": dashboard_dir,
            "dashboardOutput": dashboard_output_path,
            "generatedRenders": generated_renders,
            "svgXml": svg_xml,
            "cpuResults": cpu_results,
            "gpuResults": gpu_results,
            "scoreBefore": score_before_path,
            "scoreAfter": score_after_path,
            "fp13Runner": fp13_runner,
            "commandsJson": commands_json,
            "environmentJson": environment_json,
            "evidenceIndex": evidence_index,
        }

    def cli_args(
        self,
        fixtures,
        output_json,
        output_markdown,
        status="classification",
        check=False,
    ):
        args = [
            "--skia-runner",
            str(fixtures["skiaRunner"]),
            "--dashboard-json",
            str(fixtures["dashboardJson"]),
            "--dashboard-dir",
            str(fixtures["dashboardDir"]),
            "--generated-renders",
            str(fixtures["generatedRenders"]),
            "--svg-xml",
            str(fixtures["svgXml"]),
            "--cpu-results",
            str(fixtures["cpuResults"]),
            "--gpu-results",
            str(fixtures["gpuResults"]),
            "--scores-before",
            str(fixtures["scoreBefore"]),
            "--scores-after",
            str(fixtures["scoreAfter"]),
            "--fp13-runner",
            str(fixtures["fp13Runner"]),
            "--commands-json",
            str(fixtures["commandsJson"]),
            "--environment-json",
            str(fixtures["environmentJson"]),
            "--evidence-index",
            str(fixtures["evidenceIndex"]),
            "--source-commit",
            "abc123",
            "--status",
            status,
            "--output-json",
            str(output_json),
            "--output-markdown",
            str(output_markdown),
        ]
        if check:
            args.append("--check")
        return args

    def run_main(self, fixtures, check=False, status="classification"):
        output_json = self.root / "reports" / "wave1.json"
        output_markdown = self.root / "reports" / "wave1.md"
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            exit_status = reconcile.main(
                self.cli_args(
                    fixtures,
                    output_json,
                    output_markdown,
                    status=status,
                    check=check,
                )
            )
        manifest = (
            json.loads(output_json.read_text(encoding="utf-8"))
            if output_json.is_file()
            else None
        )
        return exit_status, output.getvalue(), manifest, output_json, output_markdown

    @staticmethod
    def sha256(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    @staticmethod
    def rows_by_name(rows):
        return {row["name"]: row for row in rows}

    @staticmethod
    def snapshot_path(path):
        if path.is_dir():
            return {
                str(child.relative_to(path)): child.read_bytes()
                for child in sorted(path.rglob("*"))
                if child.is_file()
            }
        return path.read_bytes()

    def snapshot_fixtures(self, fixtures):
        return {name: self.snapshot_path(path) for name, path in fixtures.items()}

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
        dashboard_fixture = json.loads(
            fixtures["dashboardJson"].read_text(encoding="utf-8")
        )
        self.assertEqual(
            [row["name"] for row in dashboard_fixture["gms"]],
            ["pass", "below-threshold", "missing-reference", "size-mismatch"],
        )
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        dashboard = manifest["dashboard"]
        self.assertEqual(dashboard["outputDir"], str(fixtures["dashboardDir"]))
        self.assertEqual(dashboard["dataPath"], str(fixtures["dashboardJson"]))
        self.assertEqual(dashboard["outputSha256"], self.sha256(fixtures["dashboardOutput"]))
        self.assertEqual(dashboard["dataSha256"], self.sha256(fixtures["dashboardJson"]))
        self.assertEqual(manifest["current"]["dashboard"]["rows"], 4)
        provenance = manifest["provenance"]
        for field, fixture_key in (
            ("commands", "commandsJson"),
            ("environment", "environmentJson"),
            ("evidenceIndex", "evidenceIndex"),
        ):
            self.assertEqual(provenance[field]["path"], str(fixtures[fixture_key]))
            self.assertEqual(
                provenance[field]["sha256"],
                self.sha256(fixtures[fixture_key]),
            )

    def test_score_before_after_integrity_and_runner_side_effect_restore_are_reported(self):
        fixtures = self.write_cli_fixtures()
        before = self.snapshot_fixtures(fixtures)
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        score_file = manifest["scoreFile"]
        self.assertEqual(score_file["beforePath"], str(fixtures["scoreBefore"]))
        self.assertEqual(score_file["afterPath"], str(fixtures["scoreAfter"]))
        self.assertEqual(score_file["beforeSha256"], self.sha256(fixtures["scoreBefore"]))
        self.assertEqual(score_file["afterSha256"], self.sha256(fixtures["scoreAfter"]))
        self.assertEqual(score_file["beforeSha256"], score_file["afterSha256"])
        self.assertTrue(score_file["integrityPreserved"])
        self.assertFalse(score_file["directEditDetected"])
        self.assertTrue(score_file["restored"])
        self.assertFalse(manifest["policy"]["scoresDirectlyEdited"])
        self.assertEqual(
            reconcile.load_scores(fixtures["scoreBefore"])["modecolorfilters"],
            98.75,
        )

        runner = manifest["current"]["runner"]
        self.assertTrue(runner["sideEffect"])
        self.assertTrue(runner["restored"])
        self.assertEqual(before, self.snapshot_fixtures(fixtures))

    def test_reference_lanes_keep_skia_upstream_test_oracle_and_cpu_oracle_distinct(self):
        fixtures = self.write_cli_fixtures()
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        rows = manifest["rows"]
        self.assertEqual(
            {row["referenceKind"] for row in rows["skia"]},
            {"skia-upstream"},
        )
        self.assertEqual(
            {row["referenceKind"] for row in rows["testOracle"]},
            {"test-oracle"},
        )
        self.assertEqual(
            {row["referenceKind"] for row in rows["cpuOracle"]},
            {"cpu-oracle"},
        )

    def test_runner_classifies_missing_size_similarity_terminal_and_aborted_rows(self):
        runner = self.write_runner(
            "classification-runner.xml",
            [
                self._testcase(
                    "missing-reference",
                    '<failure message="Reference PNG not found at /reference/missing.png"/>',
                ),
                self._testcase(
                    "size-mismatch",
                    '<failure message="Buffer sizes differ: expected 64x64, got 32x32"/>',
                ),
                self._testcase(
                    "similarity-failure",
                    '<failure message="similarity 25.0 below threshold 95.0"/>',
                ),
                self._testcase(
                    "terminal-refusal",
                    '<error type="GPUPreparedSurfaceTerminalException" message="terminal refusal"/>',
                ),
                self._testcase(
                    "unclassified-error",
                    '<error message="unexpected renderer error"/>',
                ),
                self._testcase(
                    "aborted-blocking",
                    '<skipped message="GM is BLOCKING" type="org.opentest4j.TestAbortedException"/>',
                ),
            ],
        )
        fixtures = self.write_cli_fixtures(
            skia_runner=runner,
            dashboard_output={"gms": []},
            dashboard_data={"rows": []},
            test_oracle={"rows": []},
            cpu_oracle={"rows": []},
        )
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        rows = self.rows_by_name(manifest["rows"]["skia"])
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
        self.assertEqual(current["observedComparableRows"], 1)
        self.assertEqual(current["candidateUnlockedRows"], 1)
        self.assertEqual(manifest["supportedRowsAfter"], 1)
        self.assertEqual(manifest["routeOnlyRows"], 1)
        self.assertFalse(manifest["routeOnlyRowsPromoted"])

    def test_escalation_limits_failed_hypotheses_to_three(self):
        runner = self.write_runner(
            "escalation-runner.xml",
            [
                self._testcase(
                    "failure-%s" % index,
                    '<error message="unsupported.route.%s"/>' % index,
                )
                for index in range(5)
            ],
        )
        fixtures = self.write_cli_fixtures(
            skia_runner=runner,
            dashboard_output={"gms": []},
            dashboard_data={"rows": []},
            test_oracle={"rows": []},
            cpu_oracle={"rows": []},
        )
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        escalation = manifest["escalation"]
        self.assertEqual(escalation["maxFailedHypotheses"], 3)
        self.assertGreaterEqual(len(escalation["failedHypotheses"]), 1)
        self.assertLessEqual(len(escalation["failedHypotheses"]), 3)

    def test_check_rejects_missing_dashboard_without_writing_report(self):
        fixtures = self.write_cli_fixtures()
        fixtures["dashboardJson"].unlink()
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
        before = self.snapshot_fixtures(fixtures)
        fixtures["dashboardJson"].write_text("{ malformed json\n", encoding="utf-8")
        malformed_before = fixtures["dashboardJson"].read_bytes()
        status, stdout, manifest, output_json, output_markdown = self.run_main(
            fixtures, check=True
        )

        self.assertNotEqual(status, 0)
        self.assertIn("dashboard", stdout.lower())
        self.assertIsNone(manifest)
        self.assertFalse(output_json.exists())
        self.assertFalse(output_markdown.exists())
        self.assertEqual(fixtures["dashboardJson"].read_bytes(), malformed_before)
        for name, path in fixtures.items():
            if name != "dashboardJson":
                self.assertEqual(self.snapshot_path(path), before[name])

    def test_check_rejects_divergent_score_after_as_score_integrity_violation(self):
        fixtures = self.write_cli_fixtures(
            score_after=(
                "modecolorfilters=98.75\n"
                "pass=97.0\n"
                "route-only=99.0\n"
            )
        )
        before = self.snapshot_fixtures(fixtures)
        self.assertNotEqual(
            self.sha256(fixtures["scoreBefore"]),
            self.sha256(fixtures["scoreAfter"]),
        )
        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("score", stdout.lower())
        self.assertIsNotNone(manifest)
        self.assertTrue(manifest["scoreFile"]["directEditDetected"])
        self.assertEqual(before, self.snapshot_fixtures(fixtures))

    def test_check_rejects_unclassified_runner_error(self):
        runner = self.write_runner(
            "unclassified-runner.xml",
            [self._testcase("unknown", '<error message="unclassified renderer error"/>')],
        )
        fixtures = self.write_cli_fixtures(
            skia_runner=runner,
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
        rows = self.rows_by_name(manifest["rows"]["skia"])
        self.assertEqual(rows["unknown"]["classification"], "unclassified")

    def test_successful_report_does_not_mutate_any_fixture_input(self):
        fixtures = self.write_cli_fixtures()
        before = self.snapshot_fixtures(fixtures)
        status, stdout, _, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(before, self.snapshot_fixtures(fixtures))


if __name__ == "__main__":
    unittest.main()
