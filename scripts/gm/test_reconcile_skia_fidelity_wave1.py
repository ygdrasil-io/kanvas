import contextlib
import hashlib
import importlib.util
import io
import json
import pathlib
import subprocess
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
            {
                "skiaRunner": (
                    "DISPLAY=:99 ./gradlew -F off :integration-tests:skia:test "
                    "--tests org.graphiks.kanvas.skia.SkiaGmRunner "
                    "-Dkanvas.gm.includeBlocking=true --no-daemon --no-parallel "
                    "--console=plain"
                ),
                "skiaRunnerChunks": [
                    (
                        "DISPLAY=:99 ./gradlew -F off :integration-tests:skia:test "
                        "--tests org.graphiks.kanvas.skia.SkiaGmRunner "
                        "-Dkanvas.gm.includeBlocking=true --no-daemon --no-parallel "
                        "--console=plain"
                    )
                ],
                "svg": (
                    "DISPLAY=:99 ./gradlew -F off :integration-tests:svg:test "
                    "--no-daemon --no-parallel --console=plain"
                ),
                "cpu": (
                    "DISPLAY=:99 ./gradlew -F off :kanvas:test "
                    "--no-daemon --no-parallel --console=plain"
                ),
                "gpu": (
                    "DISPLAY=:99 ./gradlew -F off :gpu-renderer:test "
                    "--no-daemon --no-parallel --console=plain"
                ),
                "dashboard": (
                    "DISPLAY=:99 ./gradlew -F off "
                    ":integration-tests:skia:generateSkiaDashboard "
                    "-Pgm.includeBlocking=true --no-daemon --no-parallel "
                    "--console=plain"
                ),
                "scan": (
                    "DISPLAY=:99 ./gradlew -F off "
                    ":integration-tests:skia:generateSkiaScan "
                    "--no-daemon --no-parallel --console=plain"
                ),
                "junitMerge": "python3 scripts/gm/merge_skia_junit.py",
                "timeoutRows": "python3 scripts/gm/scan_results_to_junit.py",
            },
        )
        environment_json = self.write_json(
            "provenance/environment.json",
            {
                "DISPLAY": ":99",
                "os": "test",
                "repository": "https://github.com/ygdrasil-io/kanvas.git",
                "runnerSideEffectObserved": True,
                "worktree": "/workspace/quick-sailor",
            },
        )
        evidence_artifacts = {}
        for label in ("render", "reference", "cpu", "gpu", "diff", "stat", "route"):
            artifact_path = self.write_text(
                "provenance/evidence/pass-%s.dat" % label,
                "%s evidence\n" % label,
            )
            evidence_artifacts[label] = {
                "path": str(artifact_path.relative_to(self.root / "provenance")),
                "sha256": hashlib.sha256(artifact_path.read_bytes()).hexdigest(),
            }
        evidence_index = self.write_json(
            "provenance/evidence-index.json",
            {
                "entries": [
                    {
                        "name": "pass",
                        "referenceKind": "skia-upstream",
                        "comparable": True,
                        "dimensions": {"width": 64, "height": 64},
                        "candidateUnlocked": True,
                        "causalBucket": "geometry",
                        "routeSignature": "coverage-route",
                        "minimalOperationTrace": "draw-path",
                        "ownershipBoundary": "kanvas-geometry",
                        "similarityBefore": 90.0,
                        "similarityAfter": 98.0,
                        "pixelImproved": True,
                        "artifacts": evidence_artifacts,
                    }
                ],
                "policy": {
                    "globalThresholdWeakened": False,
                    "assertionsWeakened": False,
                    "referencesModified": False,
                    "memoryBudgetChanged": False,
                    "readinessDelta": 0.0,
                },
            },
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
            subprocess.check_output(
                ["git", "rev-parse", "HEAD"], text=True
            ).strip(),
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
        self.assertEqual(
            manifest["inputs"]["commands"],
            json.loads(fixtures["commandsJson"].read_text(encoding="utf-8")),
        )
        self.assertEqual(
            manifest["inputs"]["environment"],
            json.loads(fixtures["environmentJson"].read_text(encoding="utf-8")),
        )
        self.assertNotEqual(manifest["generatedAt"], "not-recorded")
        self.assertEqual(
            [row["name"] for row in manifest["rows"]["skiaJunit"]],
            ["pass", "route-only"],
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

    def test_comparable_row_counters_require_causal_and_pixel_evidence(self):
        fixtures = self.write_cli_fixtures()
        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        current = manifest["current"]
        self.assertEqual(current["observedComparableRows"], 1)
        self.assertEqual(current["candidateUnlockedRows"], 1)
        self.assertEqual(manifest["supportedRowsAfter"], 0)
        self.assertEqual(manifest["routeOnlyRows"], 1)
        self.assertFalse(manifest["routeOnlyRowsPromoted"])
        rows = self.rows_by_name(manifest["rows"]["skia"])
        self.assertFalse(rows["below-threshold"].get("candidateUnlocked", False))

    def test_check_accepts_complete_evidence_and_preserves_inputs(self):
        fixtures = self.write_cli_fixtures()
        before = self.snapshot_fixtures(fixtures)
        status, stdout, manifest, output_json, output_markdown = self.run_main(
            fixtures, check=True
        )

        self.assertEqual(status, 0, stdout)
        self.assertIsNotNone(manifest)
        self.assertTrue(output_json.exists())
        self.assertTrue(output_markdown.exists())
        self.assertEqual(before, self.snapshot_fixtures(fixtures))

    def test_dashboard_rows_default_to_skia_reference_lane(self):
        dashboard_output = {
            "gms": [
                {"name": "pass", "isPassing": True, "similarity": 98.0}
            ]
        }
        dashboard_data = {"rows": [{"name": "pass", "score": 98.0}]}
        fixtures = self.write_cli_fixtures(
            dashboard_output=dashboard_output,
            dashboard_data=dashboard_data,
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["observedComparableRows"], 1)
        self.assertEqual(
            manifest["rows"]["skia"][0]["referenceKind"], "skia-upstream"
        )

    def test_check_accepts_html_dashboard_output(self):
        fixtures = self.write_cli_fixtures()
        fixtures["dashboardDir"].joinpath("dashboard.json").unlink()
        fixtures["dashboardDir"].joinpath("index.html").write_text(
            "<!doctype html>\n", encoding="utf-8"
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertIsNotNone(manifest["dashboard"]["outputSha256"])

    def test_check_requires_evidence_for_current_similarity_failure(self):
        dashboard_output = {
            "gms": [
                {
                    "name": "below-threshold",
                    "referenceKind": "skia-upstream",
                    "isPassing": False,
                    "similarity": 90.0,
                }
            ]
        }
        dashboard_data = {
            "rows": [
                {
                    "name": "below-threshold",
                    "referenceKind": "skia-upstream",
                    "score": 90.0,
                }
            ]
        }
        fixtures = self.write_cli_fixtures(
            dashboard_output=dashboard_output,
            dashboard_data=dashboard_data,
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("missing evidence", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_check_requires_exact_skia_runner_selector(self):
        fixtures = self.write_cli_fixtures()
        commands = json.loads(fixtures["commandsJson"].read_text(encoding="utf-8"))
        commands["skiaRunner"] = commands["skiaRunner"].replace(
            "org.graphiks.kanvas.skia.SkiaGmRunner", "OtherTest"
        )
        fixtures["commandsJson"].write_text(
            json.dumps(commands, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("SkiaGmRunner", stdout)
        self.assertIsNotNone(manifest)

    def test_check_requires_all_reproducibility_commands(self):
        fixtures = self.write_cli_fixtures()
        commands = json.loads(fixtures["commandsJson"].read_text(encoding="utf-8"))
        del commands["timeoutRows"]
        fixtures["commandsJson"].write_text(
            json.dumps(commands, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("timeoutRows", stdout)
        self.assertIsNotNone(manifest)

    def test_blocked_manifest_retains_observed_support_counter(self):
        fixtures = self.write_cli_fixtures()

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, status="blocked"
        )

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["supportedRowsAfter"], 1)

    def test_evidence_identity_allows_enriched_source_metadata(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["className"] = "SkiaGmRunner"
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, status="blocked")

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["observedComparableRows"], 1)

    def test_check_rejects_empty_evidence_index(self):
        fixtures = self.write_cli_fixtures()
        fixtures["evidenceIndex"].write_text('{"entries": []}\n', encoding="utf-8")

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("zero entries", stdout)
        self.assertIsNotNone(manifest)

    def test_check_rejects_missing_evidence_artifact(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["artifacts"]["gpu"]["path"] = "evidence/missing-gpu.dat"
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("artifact path is absent", stdout)
        self.assertIsNotNone(manifest)

    def test_check_allows_classified_terminal_refusal(self):
        runner = self.write_runner(
            "terminal-runner.xml",
            [
                self._testcase(
                    "terminal-refusal",
                    '<error type="GPUPreparedSurfaceTerminalException" message="terminal refusal"/>',
                )
            ],
        )
        fixtures = self.write_cli_fixtures(skia_runner=runner)

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertEqual(status, 0, stdout)
        refusal = self.rows_by_name(manifest["rows"]["skiaJunit"])["terminal-refusal"]
        self.assertEqual(refusal["classification"], "terminal-refusal")

    def test_skia_failure_codes_are_not_wave0_expected_unsupported(self):
        runner = self.write_runner(
            "skia-unsupported.xml",
            [
                self._testcase(
                    "unsupported-skia",
                    '<failure message="unsupported.core_primitive.geometry.invalid"/>',
                )
            ],
        )
        fixtures = self.write_cli_fixtures(
            skia_runner=runner,
            dashboard_output={"gms": []},
            dashboard_data={"rows": []},
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        row = self.rows_by_name(manifest["rows"]["skiaJunit"])["unsupported-skia"]
        self.assertFalse(row["expectedUnsupported"])
        self.assertEqual(row["classification"], "unclassified")

    def test_explicit_skia_stubs_are_classified_as_implementation_failures(self):
        runner = self.write_runner(
            "skia-stubs.xml",
            [
                self._testcase(
                    "mesh-zero-init",
                    '<failure message="unsupported.unrelated STUB.MESH.GPU_ZERO_INIT"/>',
                ),
                self._testcase(
                    "pathops-blend",
                    '<failure message="STUB.PATHOPS_BLEND"/>',
                ),
            ],
        )
        fixtures = self.write_cli_fixtures(
            skia_runner=runner,
            dashboard_output={"gms": []},
            dashboard_data={"rows": []},
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        rows = self.rows_by_name(manifest["rows"]["skiaJunit"])
        self.assertEqual(rows["mesh-zero-init"]["failureCode"], "STUB.MESH.GPU_ZERO_INIT")
        self.assertTrue(rows["mesh-zero-init"]["implementationFailure"])
        self.assertEqual(rows["mesh-zero-init"]["classification"], "implementation-failure")
        self.assertEqual(rows["pathops-blend"]["classification"], "implementation-failure")

    def test_skipped_terminal_refusal_remains_explicit(self):
        runner = self.write_runner(
            "skipped-terminal.xml",
            [
                self._testcase(
                    "skipped-terminal",
                    '<skipped type="GPUPreparedSurfaceTerminalException" message="terminal refusal"/>',
                )
            ],
        )
        fixtures = self.write_cli_fixtures(
            skia_runner=runner,
            dashboard_output={"gms": []},
            dashboard_data={"rows": []},
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        row = self.rows_by_name(manifest["rows"]["skiaJunit"])["skipped-terminal"]
        self.assertEqual(row["classification"], "terminal-refusal")
        self.assertTrue(row["terminalRefusal"])

    def test_evidence_matching_does_not_cross_reference_lanes(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["referenceKind"] = "test-oracle"
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["observedComparableRows"], 0)

    def test_check_rejects_duplicate_evidence_keys(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"].append(dict(evidence["entries"][0]))
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("duplicate", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_result_directories_are_loaded_as_oracle_lanes(self):
        fixtures = self.write_cli_fixtures()
        for key, filename in (("cpuResults", "cpu.json"), ("gpuResults", "gpu.json")):
            source = fixtures[key]
            directory = self.root / key
            directory.mkdir()
            directory.joinpath(filename).write_bytes(source.read_bytes())
            source.unlink()
            fixtures[key] = directory

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["current"]["cpuOracle"]["rows"], 1)
        self.assertEqual(manifest["current"]["testOracle"]["rows"], 1)

    def test_check_rejects_empty_score_properties(self):
        fixtures = self.write_cli_fixtures(score_before="", score_after="")

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("modecolorfilters", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_approved_check_requires_actual_similarity_improvement(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["similarityBefore"] = 98.0
        evidence["entries"][0]["similarityAfter"] = 98.0
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertNotEqual(status, 0)
        self.assertIn("approved", stdout.lower())
        self.assertIsNotNone(manifest)
        self.assertEqual(manifest["status"], "blocked")

    def test_terminal_junit_failure_blocks_dashboard_approval(self):
        runner = self.write_runner(
            "terminal-approval.xml",
            [
                self._testcase(
                    "pass",
                    '<error type="GPUPreparedSurfaceTerminalException" message="terminal refusal"/>',
                )
            ],
        )
        fixtures = self.write_cli_fixtures(skia_runner=runner)

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertNotEqual(status, 0)
        self.assertIn("terminal", stdout.lower())
        self.assertEqual(manifest["supportedRowsAfter"], 0)

    def test_check_rejects_mismatched_render_and_reference_dimensions(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["dimensions"] = {
            "render": {"width": 64, "height": 64},
            "reference": {"width": 32, "height": 32},
        }
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertNotEqual(status, 0)
        self.assertIn("dimension", stdout.lower())
        self.assertEqual(manifest["supportedRowsAfter"], 0)

    def test_check_requires_evidence_for_each_valid_current_row(self):
        dashboard_output = {
            "gms": [
                {
                    "name": "pass",
                    "referenceKind": "skia-upstream",
                    "isPassing": True,
                    "similarity": 98.0,
                },
                {
                    "name": "second-pass",
                    "referenceKind": "skia-upstream",
                    "isPassing": True,
                    "similarity": 98.0,
                },
            ]
        }
        dashboard_data = {
            "rows": [
                {"name": "pass", "referenceKind": "skia-upstream", "score": 98.0},
                {
                    "name": "second-pass",
                    "referenceKind": "skia-upstream",
                    "score": 98.0,
                },
            ]
        }
        fixtures = self.write_cli_fixtures(
            dashboard_output=dashboard_output,
            dashboard_data=dashboard_data,
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertNotEqual(status, 0)
        self.assertIn("evidence", stdout.lower())
        self.assertEqual(manifest["observedComparableRows"], 1)

    def test_candidate_counter_uses_one_shared_causal_hypothesis(self):
        dashboard_output = {
            "gms": [
                {
                    "name": "pass",
                    "referenceKind": "skia-upstream",
                    "isPassing": True,
                    "similarity": 98.0,
                },
                {
                    "name": "second-pass",
                    "referenceKind": "skia-upstream",
                    "isPassing": True,
                    "similarity": 98.0,
                },
            ]
        }
        dashboard_data = {"rows": dashboard_output["gms"]}
        fixtures = self.write_cli_fixtures(
            dashboard_output=dashboard_output,
            dashboard_data=dashboard_data,
        )
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        second = dict(evidence["entries"][0])
        second["name"] = "second-pass"
        second["causalBucket"] = "paint"
        second["routeSignature"] = "paint-route"
        second["minimalOperationTrace"] = "draw-paint"
        second["ownershipBoundary"] = "kanvas-paint"
        evidence["entries"].append(second)
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["candidateUnlockedRows"], 1)

    def test_approved_status_requires_check_mode(self):
        fixtures = self.write_cli_fixtures()

        status, stdout, manifest, _, _ = self.run_main(fixtures, status="approved")

        self.assertNotEqual(status, 0)
        self.assertIn("check", stdout.lower())
        self.assertIsNone(manifest)

    def test_approved_check_accepts_complete_improving_evidence(self):
        fixtures = self.write_cli_fixtures()

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["supportedRowsAfter"], 1)

    def test_route_only_name_cannot_be_promoted(self):
        dashboard_output = {
            "gms": [
                {
                    "name": "route-only",
                    "referenceKind": "skia-upstream",
                    "isPassing": True,
                    "similarity": 98.0,
                }
            ]
        }
        dashboard_data = {"rows": [{"name": "route-only", "score": 98.0}]}
        fixtures = self.write_cli_fixtures(
            dashboard_output=dashboard_output,
            dashboard_data=dashboard_data,
        )
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["name"] = "route-only"
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertNotEqual(status, 0)
        self.assertEqual(manifest["supportedRowsAfter"], 0)

    def test_junit_pass_does_not_overwrite_dashboard_similarity_failure(self):
        dashboard_output = {
            "gms": [
                {
                    "name": "pass",
                    "referenceKind": "skia-upstream",
                    "isPassing": False,
                    "similarity": 90.0,
                }
            ]
        }
        dashboard_data = {"rows": [{"name": "pass", "score": 90.0}]}
        fixtures = self.write_cli_fixtures(
            dashboard_output=dashboard_output,
            dashboard_data=dashboard_data,
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        row = self.rows_by_name(manifest["rows"]["skia"])["pass"]
        self.assertEqual(row["classification"], "similarity-failure")

    def test_check_rejects_policy_violation_from_evidence(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["policy"]["globalThresholdWeakened"] = True
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("threshold", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_check_rejects_invalid_policy_values(self):
        for invalid_value in (None, [], {}, "maybe"):
            with self.subTest(invalid_value=invalid_value):
                fixtures = self.write_cli_fixtures()
                evidence = json.loads(
                    fixtures["evidenceIndex"].read_text(encoding="utf-8")
                )
                evidence["policy"]["globalThresholdWeakened"] = invalid_value
                fixtures["evidenceIndex"].write_text(
                    json.dumps(evidence, indent=2, sort_keys=True) + "\n",
                    encoding="utf-8",
                )

                status, stdout, manifest, _, _ = self.run_main(
                    fixtures, check=True
                )

                self.assertNotEqual(status, 0)
                self.assertIn("policy", stdout.lower())
                self.assertIsNotNone(manifest)

    def test_check_rejects_nested_scores_after_mismatch(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(
            fixtures["evidenceIndex"].read_text(encoding="utf-8")
        )
        evidence["entries"][0]["scoresAfter"] = {"modecolorfilters": 99.0}
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertNotEqual(status, 0)
        self.assertIn("nested after", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_check_accepts_nested_before_and_after_score_maps(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(
            fixtures["evidenceIndex"].read_text(encoding="utf-8")
        )
        evidence["entries"][0]["scoresBefore"] = {"similarity": 90.0}
        evidence["entries"][0]["scoresAfter"] = {"similarity": 98.0}
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["supportedRowsAfter"], 1)

    def test_check_accepts_nested_comparison_before_and_after_scores(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(
            fixtures["evidenceIndex"].read_text(encoding="utf-8")
        )
        evidence["entries"][0].pop("similarityBefore")
        evidence["entries"][0].pop("similarityAfter")
        evidence["entries"][0]["comparison"] = {
            "before": {"score": 90.0},
            "after": {"score": 98.0},
        }
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["supportedRowsAfter"], 1)

    def test_check_rejects_nested_comparison_after_score_mismatch(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(
            fixtures["evidenceIndex"].read_text(encoding="utf-8")
        )
        evidence["entries"][0].pop("similarityBefore")
        evidence["entries"][0].pop("similarityAfter")
        evidence["entries"][0]["comparison"] = {
            "before": {"score": 90.0},
            "after": {"score": 99.0},
        }
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertNotEqual(status, 0)
        self.assertIn("nested after", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_check_rejects_similarity_outside_pixel_score_range(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["similarityBefore"] = 1000.0
        evidence["entries"][0]["similarityAfter"] = 1001.0
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertNotEqual(status, 0)
        self.assertIn("similarity", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_lifecycle_type_on_skipped_row_remains_explicit(self):
        runner = self.write_runner(
            "skipped-lifecycle-type.xml",
            [
                self._testcase(
                    "skipped-lifecycle-type",
                    '<skipped type="LifecycleException" message="renderer stopped"/>',
                )
            ],
        )
        fixtures = self.write_cli_fixtures(
            skia_runner=runner,
            dashboard_output={"gms": []},
            dashboard_data={"rows": []},
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        row = self.rows_by_name(manifest["rows"]["skiaJunit"])["skipped-lifecycle-type"]
        self.assertEqual(row["classification"], "lifecycle-failure")

    def test_directory_singleton_oracle_object_is_retained(self):
        fixtures = self.write_cli_fixtures()
        source = fixtures["gpuResults"]
        directory = self.root / "gpu-results"
        directory.mkdir()
        directory.joinpath("single.json").write_text(
            json.dumps({"name": "singleton", "referenceKind": "test-oracle"}) + "\n",
            encoding="utf-8",
        )
        source.unlink()
        fixtures["gpuResults"] = directory

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["current"]["testOracle"]["rows"], 1)

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
        self.assertTrue(manifest["policy"]["scoresDirectlyEdited"])
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

    def test_parameterized_junit_name_merges_to_dashboard_gm(self):
        runner = self.write_runner(
            "parameterized-runner.xml",
            [
                self._testcase(
                    "[1] org.graphiks.kanvas.skia.gm.composite.ModeColorFiltersGm@d5e575c",
                )
            ],
        )
        dashboard = {
            "gms": [
                {
                    "name": "modecolorfilters",
                    "isPassing": True,
                    "similarity": 98.0,
                    "width": 64,
                    "height": 64,
                }
            ]
        }
        fixtures = self.write_cli_fixtures(
            skia_runner=runner,
            dashboard_output=dashboard,
            dashboard_data={"rows": dashboard["gms"]},
        )
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["name"] = "modecolorfilters"
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertEqual(status, 0, stdout)
        row = self.rows_by_name(manifest["rows"]["skia"])["modecolorfilters"]
        self.assertEqual(row["junit"]["outcome"], "passed")

    def test_exact_junit_name_wins_over_normalized_dashboard_collision(self):
        runner = self.write_runner(
            "exact-name-runner.xml",
            [self._testcase("[71] lineargradientrt")],
        )
        dashboard = {
            "gms": [
                {"name": "linear_gradient_rt", "isPassing": True, "similarity": 98.0},
                {"name": "lineargradientrt", "isPassing": True, "similarity": 98.0},
            ]
        }
        fixtures = self.write_cli_fixtures(
            skia_runner=runner,
            dashboard_output=dashboard,
            dashboard_data={"rows": dashboard["gms"]},
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        rows = self.rows_by_name(manifest["rows"]["skia"])
        self.assertFalse(rows["lineargradientrt"]["junitMissing"])
        self.assertFalse(rows["lineargradientrt"].get("junitAmbiguous", False))
        self.assertTrue(rows["linear_gradient_rt"]["junitMissing"])

    def test_duplicate_junit_rows_are_rejected_by_population_check(self):
        runner = self.write_runner(
            "duplicate-junit.xml",
            [self._testcase("pass"), self._testcase("pass")],
        )
        fixtures = self.write_cli_fixtures(skia_runner=runner)

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("multiple JUnit testcases", stdout)
        self.assertIsNotNone(manifest)

    def test_junit_matching_does_not_cross_reference_dashboard_lanes(self):
        runner = self.write_runner("lane-junit.xml", [self._testcase("pass")])
        dashboard = {
            "gms": [
                {
                    "name": "pass",
                    "referenceKind": "test-oracle",
                    "isPassing": True,
                    "similarity": 98.0,
                }
            ]
        }
        fixtures = self.write_cli_fixtures(
            skia_runner=runner,
            dashboard_output=dashboard,
            dashboard_data={"rows": dashboard["gms"]},
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        row = self.rows_by_name(manifest["rows"]["skia"])["pass"]
        self.assertTrue(row["junitMissing"])

    def test_check_rejects_missing_junit_row(self):
        runner = self.write_runner("empty-runner.xml", [], tests=0)
        fixtures = self.write_cli_fixtures(skia_runner=runner)

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertNotEqual(status, 0)
        self.assertIn("JUnit", stdout)
        self.assertEqual(manifest["status"], "blocked")

    def test_suite_level_junit_error_is_retained(self):
        runner = self.write_text(
            "suite-error.xml",
            '<testsuite name="SkiaGmRunner" tests="0" failures="0" errors="1">'
            '<error type="LifecycleException" message="suite teardown failed"/>'
            "</testsuite>",
        )
        fixtures = self.write_cli_fixtures(
            skia_runner=runner,
            dashboard_output={"gms": []},
            dashboard_data={"rows": []},
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        rows = manifest["rows"]["skiaJunit"]
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["classification"], "lifecycle-failure")

    def test_check_rejects_junit_declared_count_mismatch(self):
        runner = self.write_runner(
            "count-mismatch.xml",
            [self._testcase("pass")],
            tests=0,
        )
        fixtures = self.write_cli_fixtures(skia_runner=runner)

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("count", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_evidence_class_metadata_must_not_contradict_junit(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["className"] = "WrongRunnerClass"
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertNotEqual(status, 0)
        self.assertIn("evidence", stdout.lower())
        self.assertEqual(manifest["status"], "blocked")

    def test_check_rejects_evidence_artifact_aliasing_input(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        score_path = fixtures["scoreBefore"]
        evidence["entries"][0]["artifacts"]["render"] = {
            "path": str(score_path),
            "sha256": self.sha256(score_path),
        }
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("alias", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_check_rejects_invalid_top_level_dimensions_even_with_nested_valid_dimensions(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["dimensions"] = {
            "render": {"width": 0, "height": 0},
            "reference": {"width": 64, "height": 64},
        }
        for label in ("render", "reference"):
            path = fixtures["evidenceIndex"].parent / "evidence" / (
                "nested-%s.dat" % label
            )
            path.write_text("nested %s\n" % label, encoding="utf-8")
            evidence["entries"][0]["artifacts"][label] = {
                "path": str(path.relative_to(fixtures["evidenceIndex"].parent)),
                "sha256": self.sha256(path),
                "dimensions": {"width": 64, "height": 64},
            }
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("dimension", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_check_rejects_contradictory_dashboard_containers(self):
        dashboard = {
            "gms": [
                {
                    "name": "pass",
                    "referenceKind": "skia-upstream",
                    "isPassing": False,
                    "similarity": 90.0,
                }
            ],
            "rows": [
                {
                    "name": "pass",
                    "referenceKind": "skia-upstream",
                    "isPassing": True,
                    "similarity": 98.0,
                }
            ],
        }
        fixtures = self.write_cli_fixtures(
            dashboard_output=dashboard,
            dashboard_data={"rows": dashboard["gms"]},
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("dashboard", stdout.lower())
        self.assertIsNone(manifest)

    def test_string_route_only_evidence_cannot_be_promoted(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["routeOnly"] = "true"
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertNotEqual(status, 0)
        self.assertEqual(manifest["supportedRowsAfter"], 0)

    def test_check_rejects_malformed_exact_execution_flag(self):
        fixtures = self.write_cli_fixtures()
        commands = json.loads(fixtures["commandsJson"].read_text(encoding="utf-8"))
        commands["skiaRunner"] = commands["skiaRunner"].replace(
            "--no-daemon", "--no-daemonish"
        )
        fixtures["commandsJson"].write_text(
            json.dumps(commands, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("no-daemon", stdout)
        self.assertIsNotNone(manifest)

    def test_check_rejects_conflicting_include_blocking_flags(self):
        fixtures = self.write_cli_fixtures()
        commands = json.loads(fixtures["commandsJson"].read_text(encoding="utf-8"))
        commands["skiaRunner"] += " -Dkanvas.gm.includeBlocking=false"
        commands["dashboard"] += " -Pgm.includeBlocking=false"
        fixtures["commandsJson"].write_text(
            json.dumps(commands, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("exactly to true", stdout)
        self.assertIsNotNone(manifest)

    def test_check_accepts_macos_without_display(self):
        fixtures = self.write_cli_fixtures()
        commands = json.loads(fixtures["commandsJson"].read_text(encoding="utf-8"))
        for name, command in commands.items():
            if isinstance(command, list):
                commands[name] = [
                    item.replace("DISPLAY=:99 ", "", 1) for item in command
                ]
            else:
                commands[name] = command.replace("DISPLAY=:99 ", "", 1)
        fixtures["commandsJson"].write_text(
            json.dumps(commands, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        environment = json.loads(
            fixtures["environmentJson"].read_text(encoding="utf-8")
        )
        environment.pop("DISPLAY")
        environment["os"] = "macOS"
        fixtures["environmentJson"].write_text(
            json.dumps(environment, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertEqual(status, 0, stdout)
        self.assertIsNotNone(manifest)

    def test_check_rejects_unmatched_skipped_junit_row(self):
        runner = self.write_runner(
            "orphan-skipped.xml",
            [
                self._testcase(
                    "orphan-blocking",
                    '<skipped message="GM is BLOCKING" type="org.opentest4j.TestAbortedException"/>',
                )
            ],
        )
        fixtures = self.write_cli_fixtures(skia_runner=runner)

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("no dashboard row", stdout)
        self.assertIsNotNone(manifest)

    def test_dashboard_score_overrides_inconsistent_passing_flag(self):
        dashboard = {
            "gms": [
                {
                    "name": "pass",
                    "referenceKind": "skia-upstream",
                    "isPassing": True,
                    "similarity": 90.0,
                    "minSimilarity": 95.0,
                }
            ]
        }
        fixtures = self.write_cli_fixtures(
            dashboard_output=dashboard,
            dashboard_data={"rows": dashboard["gms"]},
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        row = self.rows_by_name(manifest["rows"]["skia"])["pass"]
        self.assertEqual(row["classification"], "similarity-failure")

    def test_dashboard_string_false_is_similarity_failure(self):
        dashboard = {
            "gms": [
                {
                    "name": "pass",
                    "referenceKind": "skia-upstream",
                    "isPassing": "false",
                    "similarity": 98.0,
                }
            ]
        }
        fixtures = self.write_cli_fixtures(
            dashboard_output=dashboard,
            dashboard_data={"rows": dashboard["gms"]},
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        row = self.rows_by_name(manifest["rows"]["skia"])["pass"]
        self.assertEqual(row["classification"], "similarity-failure")

    def test_valid_similarity_failure_is_observed_but_not_supported(self):
        runner = self.write_runner(
            "similarity-failure-runner.xml",
            [
                self._testcase(
                    "below-threshold",
                    '<failure message="similarity 90.0 below threshold 95.0"/>',
                )
            ],
        )
        dashboard = {
            "gms": [
                {
                    "name": "below-threshold",
                    "referenceKind": "skia-upstream",
                    "isPassing": False,
                    "similarity": 90.0,
                    "width": 64,
                    "height": 64,
                }
            ]
        }
        fixtures = self.write_cli_fixtures(
            skia_runner=runner,
            dashboard_output=dashboard,
            dashboard_data={"rows": dashboard["gms"]},
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["observedComparableRows"], 1)
        self.assertEqual(manifest["supportedRowsAfter"], 0)

    def test_below_threshold_row_with_true_passing_flag_is_not_supported(self):
        dashboard = {
            "gms": [
                {
                    "name": "pass",
                    "referenceKind": "skia-upstream",
                    "isPassing": True,
                    "similarity": 90.0,
                    "minSimilarity": 95.0,
                }
            ]
        }
        fixtures = self.write_cli_fixtures(
            dashboard_output=dashboard,
            dashboard_data={"rows": dashboard["gms"]},
        )
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["similarityBefore"] = 80.0
        evidence["entries"][0]["similarityAfter"] = 90.0
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, status="blocked")

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["supportedRowsAfter"], 0)

    def test_empty_policy_evidence_is_rejected(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["policy"] = {}
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("policy evidence", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_unmatched_non_route_junit_failure_is_rejected(self):
        runner = self.write_runner(
            "unmatched-junit-runner.xml",
            [
                self._testcase("pass"),
                self._testcase(
                    "unreported-failure",
                    '<failure message="similarity 90.0 below threshold 95.0"/>',
                ),
            ],
        )
        fixtures = self.write_cli_fixtures(skia_runner=runner)

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("dashboard", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_nested_after_score_must_match_dashboard_score(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0]["similarityAfter"] = 99.99
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertNotEqual(status, 0)
        self.assertIn("score", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_stale_html_does_not_win_over_dashboard_data_output(self):
        fixtures = self.write_cli_fixtures()
        stale_html = fixtures["dashboardDir"] / "index.html"
        stale_html.write_text("stale dashboard\n", encoding="utf-8")

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(
            manifest["dashboard"]["outputSha256"],
            self.sha256(fixtures["dashboardOutput"]),
        )


if __name__ == "__main__":
    unittest.main()
