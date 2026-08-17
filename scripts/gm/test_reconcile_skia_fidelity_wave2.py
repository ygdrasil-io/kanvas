import contextlib
import copy
import hashlib
import html
import importlib.util
import io
import json
import pathlib
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = pathlib.Path(__file__).with_name("reconcile_skia_fidelity_wave2.py")
SPEC = importlib.util.spec_from_file_location("reconcile_skia_fidelity_wave2", SCRIPT)
reconcile = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(reconcile)

WAVE1_MANIFEST = (
    ROOT
    / "reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/wave1-classification.json"
)
WAVE1_DASHBOARD = WAVE1_MANIFEST.with_name("skia-dashboard-gms.json")
FAILURE_CODE = "unsupported.image.alpha_interpretation"
FOLLOW_UP_FAILURE_CODE = "unsupported.image.native_binding"
FAMILY_COUNTS = {
    "IMAGE": 38,
    "COMPOSITE": 8,
    "CLIP": 6,
    "BLUR": 3,
    "GRADIENT": 2,
    "RUNTIME_EFFECT": 1,
}


class ReconcileSkiaFidelityWave2Test(unittest.TestCase):
    def setUp(self):
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tempdir.name)
        self.selected_rows = self.real_selected_rows()

    def tearDown(self):
        self.tempdir.cleanup()

    @staticmethod
    def real_selected_rows():
        value = json.loads(WAVE1_MANIFEST.read_text(encoding="utf-8"))
        return [
            copy.deepcopy(row)
            for row in value["rows"]["skia"]
            if row.get("failureCode") == FAILURE_CODE
        ]

    def write_text(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def write_json(self, name, value):
        return self.write_text(name, json.dumps(value, indent=2, sort_keys=True) + "\n")

    @staticmethod
    def sha256(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def write_runner(self, rows, extra_rows=(), passed_names=()):
        passed_names = set(passed_names)
        testcases = []
        for index, row in enumerate([*rows, *extra_rows]):
            name = row["name"]
            identity = row.get("gmIdentity", {})
            display_name = identity.get("displayName", name) if isinstance(identity, dict) else name
            source_registration = row.get("sourceRegistration", name)
            source_class = row.get("sourceClass")
            failure = "" if name in passed_names else (
                '<failure type="GPUPreparedSurfaceTerminalException" '
                'message="GPUPreparedSurfaceTerminalException: %s: terminal refusal"/>'
                % html.escape(row.get("failureCode", FAILURE_CODE), quote=True)
            )
            testcases.append(
                '<testcase name="%s" classname="org.graphiks.kanvas.skia.SkiaGmRunner" '
                'sourceRegistration="%s"%s>%s</testcase>'
                % (
                    html.escape(display_name, quote=True),
                    html.escape(source_registration, quote=True),
                    (
                        ' sourceClass="%s"' % html.escape(source_class, quote=True)
                        if source_class
                        else ""
                    ),
                    failure,
                )
            )
        return self.write_text(
            "skia-runner.xml",
            '<testsuite tests="%s" failures="%s" errors="0" skipped="0">%s</testsuite>'
            % (
                len(testcases),
                sum(row["name"] not in passed_names for row in [*rows, *extra_rows]),
                "".join(testcases),
            ),
        )

    def write_residual_evidence(self, rows):
        entries = []
        for row in rows:
            entries.append(
                {
                    "name": row["name"],
                    "family": row["family"],
                    "referenceKind": "skia-upstream",
                    "failureCode": FOLLOW_UP_FAILURE_CODE,
                    "fallbackReason": "prepared-surface-alpha-route-refused",
                    "expectedRoute": "prepared-image-unpremul",
                    "rootCause": "image-alpha-interpretation",
                    "followUpFamily": row["family"],
                    "classification": "terminal-refusal",
                }
            )
        return self.write_json(
            "provenance/evidence-index.json",
            {
                "entries": entries,
                "policy": {
                    "globalThresholdWeakened": False,
                    "assertionsWeakened": False,
                    "referencesModified": False,
                    "memoryBudgetChanged": False,
                    "readinessDelta": 0.0,
                },
            },
        )

    def supported_evidence(
        self,
        row,
        index=0,
        route_only=False,
        after_score=98.0,
        route_signature="prepared-image-unpremul",
        expected_route="prepared-image-unpremul",
    ):
        dimensions = {
            "render": {"width": 32, "height": 32},
            "reference": {"width": 32, "height": 32},
        }
        entry = {
            "name": row["name"],
            "family": row["family"],
            "referenceKind": "skia-upstream",
            "failureCode": FAILURE_CODE,
            "supportedAfter": True,
            "pixelImproved": True,
            "similarityBefore": 90.0,
            "similarityAfter": after_score,
            "minSimilarity": 95.0,
            "candidateUnlocked": True,
            "causalBucket": "image-alpha",
            "routeSignature": route_signature,
            "expectedRoute": expected_route,
            "minimalOperationTrace": "draw-image",
            "ownershipBoundary": "kanvas-image",
            "routeOnly": route_only,
            "dimensions": copy.deepcopy(dimensions),
        }
        artifacts = {}
        for label in ("render", "reference", "cpu", "gpu", "diff", "stat", "route"):
            artifact = self.write_text(
                "artifacts/supported-%s-%s.dat" % (index, label),
                "%s-%s\n" % (row["name"], label),
            )
            record = {"path": str(artifact), "sha256": self.sha256(artifact)}
            if label in {"render", "reference"}:
                record["dimensions"] = {"width": 32, "height": 32}
            artifacts[label] = record
        entry["artifacts"] = artifacts
        return entry

    def configure_supported_fixture(
        self,
        fixtures,
        *,
        route_only=False,
        after_score=98.0,
        comparable=True,
        junit_pass=True,
        is_passing=True,
        remove_is_passing=False,
        route_signature="prepared-image-unpremul",
        expected_route="prepared-image-unpremul",
    ):
        dashboard = json.loads(fixtures["dashboardJson"].read_text(encoding="utf-8"))
        row = dashboard["gms"][0]
        for field in ("classification", "terminal", "terminalRefusal"):
            row.pop(field, None)
        row.update(
            {
                "renderFailed": False,
                "isPassing": is_passing,
                "score": after_score,
                "minSimilarity": 95.0,
                "routeOnly": route_only,
                "failureCode": FAILURE_CODE,
            }
        )
        row.update(
            {
                "classification": (
                    "route-only"
                    if route_only
                    else "similarity-failure"
                    if after_score < 95.0 or is_passing is False
                    else "pass"
                ),
                "terminal": False,
                "terminalRefusal": False,
            }
        )
        if remove_is_passing:
            del row["isPassing"]
        if comparable:
            row["dimensions"] = {
                "render": {"width": 32, "height": 32},
                "reference": {"width": 32, "height": 32},
            }
        else:
            row["dimensions"] = {
                "render": {"width": 32, "height": 32},
                "reference": {"width": 31, "height": 32},
            }
        if not comparable:
            row["classification"] = "size-mismatch"
        dashboard_text = json.dumps(dashboard, indent=2, sort_keys=True) + "\n"
        fixtures["dashboardJson"].write_text(dashboard_text, encoding="utf-8")
        dashboard_output = fixtures["dashboardDir"] / "data/gms.json"
        dashboard_output.write_text(dashboard_text, encoding="utf-8")

        runner_rows = copy.deepcopy(self.selected_rows)
        for runner_row in runner_rows:
            runner_row["failureCode"] = FOLLOW_UP_FAILURE_CODE
        runner_rows[0]["failureCode"] = FAILURE_CODE
        fixtures["skiaRunner"] = self.write_runner(
            runner_rows,
            passed_names={self.selected_rows[0]["name"]} if junit_pass else (),
        )
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0] = self.supported_evidence(
            self.selected_rows[0],
            route_only=route_only,
            after_score=after_score,
            route_signature=route_signature,
            expected_route=expected_route,
        )
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return row

    def configure_residual_fixture(
        self, fixtures, failure_code=FOLLOW_UP_FAILURE_CODE
    ):
        dashboard = json.loads(fixtures["dashboardJson"].read_text(encoding="utf-8"))
        dashboard["gms"][0].update(
            {
                "classification": "failure",
                "terminal": True,
                "terminalRefusal": True,
                "failureCode": failure_code,
            }
        )
        dashboard_text = json.dumps(dashboard, indent=2, sort_keys=True) + "\n"
        fixtures["dashboardJson"].write_text(dashboard_text, encoding="utf-8")
        (fixtures["dashboardDir"] / "data/gms.json").write_text(
            dashboard_text, encoding="utf-8"
        )

        runner_rows = copy.deepcopy(self.selected_rows)
        for runner_row in runner_rows:
            runner_row["failureCode"] = FOLLOW_UP_FAILURE_CODE
        runner_rows[0]["failureCode"] = failure_code
        fixtures["skiaRunner"] = self.write_runner(runner_rows)

        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"][0].update(
            {
                "classification": "terminal-refusal",
                "failureCode": failure_code,
            }
        )
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    def write_cli_fixtures(self):
        dashboard_rows = []
        for row in self.selected_rows:
            dashboard_row = {
                "name": row["name"],
                "family": row["family"],
                "referenceKind": "skia-upstream",
                "failureCode": FOLLOW_UP_FAILURE_CODE,
                "classification": "failure",
                "terminal": True,
                "terminalRefusal": True,
                "isPassing": None,
                "renderFailed": True,
                "dimensions": copy.deepcopy(row.get("dimensions")),
            }
            for field in (
                "class",
                "className",
                "sourceClass",
                "sourceRegistration",
                "gmIdentity",
                "evidenceLane",
            ):
                if field in row:
                    dashboard_row[field] = copy.deepcopy(row[field])
            dashboard_rows.append(dashboard_row)
        dashboard = {"gms": dashboard_rows}
        dashboard_json = self.write_json("skia-dashboard-gms.json", dashboard)
        dashboard_dir = self.root / "dashboard"
        dashboard_output = self.write_json("dashboard/data/gms.json", dashboard)
        dashboard_dir.mkdir(parents=True, exist_ok=True)
        svg_xml = self.write_text(
            "svg-integration.xml",
            '<testsuite tests="1" failures="0" errors="0" skipped="0">'
            '<testcase name="route-only"/></testsuite>',
        )
        cpu_results = self.write_json("cpu-results.json", {"rows": []})
        gpu_results = self.write_json("gpu-results.json", {"rows": []})
        fp13_runner = self.write_text(
            "fp13-runner.xml",
            '<testsuite tests="0" failures="0" errors="0" skipped="0"/>',
        )
        commands = {
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
        }
        commands_json = self.write_json("provenance/commands.json", commands)
        environment_json = self.write_json(
            "provenance/environment.json",
            {
                "DISPLAY": ":99",
                "os": "test",
                "repository": "https://github.com/ygdrasil-io/kanvas.git",
                "runnerSideEffectObserved": True,
                "worktree": str(self.root),
            },
        )
        score_before = self.write_text("scores/before.properties", "modecolorfilters=98.75\n")
        score_after = self.write_text("scores/after.properties", "modecolorfilters=98.75\n")
        runner_rows = copy.deepcopy(self.selected_rows)
        for runner_row in runner_rows:
            runner_row["failureCode"] = FOLLOW_UP_FAILURE_CODE
        skia_runner = self.write_runner(runner_rows)
        evidence_index = self.write_residual_evidence(self.selected_rows)
        cohort_manifest = self.root / "cohort.json"
        cohort_manifest.write_bytes(WAVE1_MANIFEST.read_bytes())
        generated_renders = self.root / "generated-renders"
        generated_renders.mkdir(exist_ok=True)
        return {
            "skiaRunner": skia_runner,
            "dashboardJson": dashboard_json,
            "dashboardDir": dashboard_dir,
            "dashboardOutput": dashboard_output,
            "generatedRenders": generated_renders,
            "svgXml": svg_xml,
            "cpuResults": cpu_results,
            "gpuResults": gpu_results,
            "scoreBefore": score_before,
            "scoreAfter": score_after,
            "fp13Runner": fp13_runner,
            "commandsJson": commands_json,
            "environmentJson": environment_json,
            "evidenceIndex": evidence_index,
            "cohortManifest": cohort_manifest,
        }

    def write_raw_dashboard_fixtures(self):
        fixtures = self.write_cli_fixtures()
        dashboard = json.loads(WAVE1_DASHBOARD.read_text(encoding="utf-8"))
        raw_rows = [
            {
                "name": row["name"],
                "family": row["family"],
                "isPassing": row.get("isPassing"),
                "similarity": row.get("similarity"),
                "routeOnly": False,
            }
            for row in dashboard["gms"]
        ]
        dashboard_text = json.dumps({"gms": raw_rows}, indent=2, sort_keys=True) + "\n"
        fixtures["dashboardJson"].write_text(dashboard_text, encoding="utf-8")
        (fixtures["dashboardDir"] / "data/gms.json").write_text(
            dashboard_text, encoding="utf-8"
        )

        fixtures["skiaRunner"] = self.write_runner(
            self.selected_rows,
            passed_names={row["name"] for row in self.selected_rows},
        )
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"] = [
            self.supported_evidence(row, index=index)
            for index, row in enumerate(self.selected_rows)
        ]
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return fixtures

    def write_full_population_raw_fixtures(self):
        fixtures = self.write_raw_dashboard_fixtures()
        raw_rows = json.loads(
            fixtures["dashboardJson"].read_text(encoding="utf-8")
        )["gms"]
        fixtures["skiaRunner"] = self.write_runner(
            raw_rows,
            passed_names={row["name"] for row in raw_rows},
        )
        return fixtures

    @staticmethod
    def small_selection():
        row = {
            "name": "small-gm",
            "family": "IMAGE",
            "referenceKind": "skia-upstream",
            "evidenceLane": "skia-dashboard",
            "className": "SmallRunner",
            "sourceClass": "SmallGm",
            "sourceRegistration": "small-gm",
            "gmIdentity": {
                "displayName": "small-gm",
                "sourceClass": "SmallGm",
                "sourceClassPath": "small-gm",
                "sourceRegistration": "small-gm",
            },
            "classification": "failure",
            "terminal": True,
            "terminalRefusal": True,
            "failureCode": FAILURE_CODE,
        }
        return reconcile.CohortSelection(
            rows=(row,),
            identities=frozenset({("small-gm", "skia-upstream")}),
            family_counts={"IMAGE": 1},
            failure_code=FAILURE_CODE,
        )

    def cli_args(self, fixtures, output_json=None, output_markdown=None, status="classification", check=False, source_commit=None):
        output_json = output_json or self.root / "reports/wave2.json"
        output_markdown = output_markdown or self.root / "reports/wave2.md"
        source_commit = source_commit or subprocess.check_output(
            ["git", "rev-parse", "HEAD"], text=True
        ).strip()
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
            "--cohort-manifest",
            str(fixtures["cohortManifest"]),
            "--cohort-failure-code",
            FAILURE_CODE,
            "--source-commit",
            source_commit,
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

    def run_main(self, fixtures, check=False, status="classification", source_commit=None):
        output_json = self.root / "reports/wave2.json"
        output_markdown = self.root / "reports/wave2.md"
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            exit_status = reconcile.main(
                self.cli_args(
                    fixtures,
                    output_json=output_json,
                    output_markdown=output_markdown,
                    status=status,
                    check=check,
                    source_commit=source_commit,
                )
            )
        manifest = (
            json.loads(output_json.read_text(encoding="utf-8"))
            if output_json.is_file()
            else None
        )
        return exit_status, output.getvalue(), manifest, output_json, output_markdown

    @staticmethod
    def snapshot(path):
        if path.is_dir():
            return {
                str(child.relative_to(path)): child.read_bytes()
                for child in sorted(path.rglob("*"))
                if child.is_file()
            }
        return path.read_bytes()

    def snapshot_fixtures(self, fixtures):
        return {name: self.snapshot(path) for name, path in fixtures.items()}

    def test_real_wave1_manifest_selects_exact_frozen_58_row_cohort(self):
        self.assertTrue(WAVE1_MANIFEST.is_file())
        original = WAVE1_MANIFEST.read_bytes()
        selection = reconcile.load_cohort_manifest(WAVE1_MANIFEST, FAILURE_CODE)

        self.assertEqual(len(selection.rows), 58)
        self.assertIsInstance(selection.identities, frozenset)
        self.assertEqual(selection.identities, reconcile.COHORT_IDENTITIES)
        self.assertEqual(selection.family_counts, FAMILY_COUNTS)
        self.assertEqual(
            {reference_kind for _, reference_kind in selection.identities},
            {"skia-upstream"},
        )
        self.assertEqual(WAVE1_MANIFEST.read_bytes(), original)

    def test_cohort_selection_rejects_missing_duplicate_wrong_code_and_wrong_lane(self):
        value = json.loads(WAVE1_MANIFEST.read_text(encoding="utf-8"))
        rows = value["rows"]["skia"]
        selected_indexes = [
            index for index, row in enumerate(rows) if row.get("failureCode") == FAILURE_CODE
        ]

        missing = copy.deepcopy(value)
        del missing["rows"]["skia"][selected_indexes[0]]
        with self.assertRaises(ValueError):
            reconcile.select_cohort_rows(missing, FAILURE_CODE)

        duplicate = copy.deepcopy(value)
        duplicate["rows"]["skia"].append(copy.deepcopy(rows[selected_indexes[0]]))
        with self.assertRaises(ValueError):
            reconcile.select_cohort_rows(duplicate, FAILURE_CODE)

        wrong_code = copy.deepcopy(value)
        wrong_code["rows"]["skia"][selected_indexes[0]]["failureCode"] = (
            "unsupported.image.native_binding"
        )
        with self.assertRaises(ValueError):
            reconcile.select_cohort_rows(wrong_code, FAILURE_CODE)

        wrong_lane = copy.deepcopy(value)
        wrong_lane["rows"]["skia"][selected_indexes[0]]["referenceKind"] = "cpu-oracle"
        with self.assertRaises(ValueError):
            reconcile.select_cohort_rows(wrong_lane, FAILURE_CODE)

    def test_source_and_input_bytes_are_unchanged_after_classification(self):
        fixtures = self.write_cli_fixtures()
        before = self.snapshot_fixtures(fixtures)

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertIsNotNone(manifest)
        self.assertEqual(before, self.snapshot_fixtures(fixtures))

    def test_fresh_rows_reject_unknown_cohort_identity(self):
        fixtures = self.write_cli_fixtures()
        dashboard = json.loads(fixtures["dashboardJson"].read_text(encoding="utf-8"))
        unrelated = {
            "name": "not-in-wave2",
            "family": "IMAGE",
            "referenceKind": "skia-upstream",
            "failureCode": FAILURE_CODE,
            "renderFailed": True,
        }
        dashboard["gms"].append(unrelated)
        fixtures["dashboardJson"].write_text(
            json.dumps(dashboard, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        dashboard_output = fixtures["dashboardDir"] / "data/gms.json"
        dashboard_output.write_text(
            json.dumps(dashboard, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        runner_rows = copy.deepcopy(self.selected_rows)
        for runner_row in runner_rows:
            runner_row["failureCode"] = FOLLOW_UP_FAILURE_CODE
        fixtures["skiaRunner"] = self.write_runner(runner_rows, [unrelated])
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["entries"].append(
            {
                "name": "not-in-wave2",
                "referenceKind": "skia-upstream",
                "failureCode": FAILURE_CODE,
                "fallbackReason": "unrelated",
                "expectedRoute": "unrelated",
                "rootCause": "unrelated",
                "followUpFamily": "IMAGE",
            }
        )
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 2, stdout)
        self.assertIsNone(manifest)
        self.assertIn("unknown identity", stdout)

    def test_fresh_selected_dashboard_requires_exact_failure_code(self):
        variants = (
            ("wrong", "unsupported.image.native_binding"),
            ("missing", None),
        )
        for variant, failure_code in variants:
            for check in (False, True):
                with self.subTest(variant=variant, check=check):
                    fixtures = self.write_cli_fixtures()
                    self.configure_supported_fixture(fixtures)
                    dashboard = json.loads(
                        fixtures["dashboardJson"].read_text(encoding="utf-8")
                    )
                    first = dashboard["gms"][0]
                    if failure_code is None:
                        del first["failureCode"]
                    else:
                        first["failureCode"] = failure_code
                    dashboard_text = json.dumps(dashboard, indent=2, sort_keys=True) + "\n"
                    fixtures["dashboardJson"].write_text(
                        dashboard_text, encoding="utf-8"
                    )
                    (fixtures["dashboardDir"] / "data/gms.json").write_text(
                        dashboard_text, encoding="utf-8"
                    )

                    status, stdout, _, _, _ = self.run_main(fixtures, check=check)

                    expected_status = 2
                    self.assertEqual(status, expected_status, stdout)
                    self.assertIn("failurecode", stdout.lower())

    def test_check_validates_current_head_source_commit_and_policy_non_weakening(self):
        fixtures = self.write_cli_fixtures()
        head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()

        status, stdout, manifest, _, markdown_path = self.run_main(
            fixtures, check=True, source_commit=head
        )

        self.assertEqual(status, 0, stdout)
        policy = manifest["policy"]
        self.assertFalse(policy["assertionsWeakened"])
        self.assertFalse(policy["globalThresholdWeakened"])
        self.assertFalse(policy["memoryBudgetChanged"])
        self.assertFalse(policy["referencesModified"])
        self.assertFalse(policy["scoresDirectlyEdited"])
        self.assertEqual(policy["readinessDelta"], 0.0)
        self.assertIn("# Skia Fidelity Wave 2 UNPREMUL Reconciliation", markdown_path.read_text())

        stale = "0" * 40 if head != "0" * 40 else "1" * 40
        status, stdout, _, _, _ = self.run_main(
            fixtures, check=True, source_commit=stale
        )
        self.assertEqual(status, 1)
        self.assertIn("sourceCommit", stdout)

    def test_terminal_refusal_accepts_residual_evidence_without_render(self):
        for check, status_name in ((False, "classification"), (True, "blocked")):
            with self.subTest(check=check):
                fixtures = self.write_cli_fixtures()
                self.configure_residual_fixture(fixtures)

                status, stdout, manifest, _, _ = self.run_main(
                    fixtures, check=check, status=status_name
                )

                self.assertEqual(status, 0, stdout)
                self.assertEqual(manifest["supportedRowsAfter"], 0)
                self.assertEqual(
                    manifest["residualCodes"],
                    [FOLLOW_UP_FAILURE_CODE],
                )
                self.assertEqual(manifest["rows"]["skia"][0]["failureCode"], FOLLOW_UP_FAILURE_CODE)
                self.assertEqual(
                    manifest["rows"]["evidence"][0]["failureCode"],
                    FOLLOW_UP_FAILURE_CODE,
                )
                self.assertEqual(
                    manifest["rows"]["skiaJunit"][0]["failureCode"],
                    FOLLOW_UP_FAILURE_CODE,
                )
                self.assertEqual(manifest["current"]["dashboard"]["rows"], 58)
                self.assertEqual(manifest["current"]["runner"]["rows"], 58)

    def test_every_selected_identity_requires_an_evidence_entry(self):
        for supported in (False, True):
            for check in (False, True):
                with self.subTest(supported=supported, check=check):
                    fixtures = self.write_cli_fixtures()
                    if supported:
                        self.configure_supported_fixture(fixtures)
                    evidence = json.loads(
                        fixtures["evidenceIndex"].read_text(encoding="utf-8")
                    )
                    del evidence["entries"][0]
                    fixtures["evidenceIndex"].write_text(
                        json.dumps(evidence, indent=2, sort_keys=True) + "\n",
                        encoding="utf-8",
                    )

                    status, stdout, _, _, _ = self.run_main(
                        fixtures,
                        check=check,
                        status="blocked" if check else "classification",
                    )

                    self.assertEqual(status, 1 if check else 2, stdout)
                    self.assertIn("evidence", stdout.lower())

    def test_check_preserves_suite_lifecycle_rows_and_junit_counts(self):
        variants = ("suite-error", "malformed-counts", "unclassified-error")
        for variant in variants:
            with self.subTest(variant=variant):
                fixtures = self.write_cli_fixtures()
                xml = fixtures["skiaRunner"].read_text(encoding="utf-8")
                if variant == "suite-error":
                    xml = xml.replace(
                        "</testsuite>",
                        '<error type="suite-error" message="suite lifecycle error"/>'
                        "</testsuite>",
                    )
                elif variant == "malformed-counts":
                    xml = xml.replace('tests="58"', 'tests="not-an-integer"', 1)
                else:
                    xml = xml.replace(
                        '<testsuite tests="58" failures="58" errors="0" skipped="0">',
                        '<testsuite tests="59" failures="58" errors="1" skipped="0">',
                        1,
                    ).replace(
                        "</testsuite>",
                        '<testcase name="unclassified-orphan">'
                        '<error message="unexpected renderer error"/>'
                        "</testcase></testsuite>",
                    )
                fixtures["skiaRunner"].write_text(xml, encoding="utf-8")

                status, stdout, manifest, _, _ = self.run_main(
                    fixtures, check=True, status="blocked"
                )

                self.assertEqual(status, 1, stdout)
                self.assertIsNotNone(manifest)
                if variant == "suite-error":
                    self.assertIn("suite-level", stdout.lower())
                    self.assertEqual(
                        manifest["current"]["runner"]["parsedCounts"]["errors"], 1
                    )
                elif variant == "malformed-counts":
                    self.assertIn("count mismatch", stdout.lower())
                    self.assertTrue(manifest["current"]["runner"]["countMismatches"])
                else:
                    self.assertIn("unclassified", stdout.lower())
                self.assertEqual(manifest["current"]["runner"]["rows"], 58)
                expected_full_rows = {
                    "suite-error": 59,
                    "malformed-counts": 58,
                    "unclassified-error": 59,
                }
                self.assertEqual(
                    manifest["current"]["runnerFullPopulation"]["rows"],
                    expected_full_rows[variant],
                )

    def test_enriched_dashboard_requires_complete_normalized_metadata(self):
        selection = self.small_selection()
        fields = (
            "className",
            "sourceClass",
            "sourceRegistration",
            "gmIdentity",
            "classification",
            "terminal",
            "terminalRefusal",
            "referenceKind",
            "family",
            "evidenceLane",
            "failureCode",
        )
        for field in fields:
            with self.subTest(field=field):
                dashboard = copy.deepcopy(selection.rows[0])
                dashboard["renderFailed"] = True
                dashboard.pop(field, None)
                with self.assertRaises(ValueError):
                    rows = reconcile._select_dashboard_rows(
                        {"gms": [dashboard]}, selection.identities
                    )
                    reconcile._validate_fresh_dashboard_metadata(
                        rows,
                        selection,
                        reconcile._raw_dashboard_identity_map({"gms": [dashboard]}),
                    )

        dashboard = copy.deepcopy(selection.rows[0])
        dashboard["renderFailed"] = True
        dashboard["sourceRegistration"] = "wrong-registration"
        with self.assertRaisesRegex(ValueError, "sourceRegistration"):
            rows = reconcile._select_dashboard_rows(
                {"gms": [dashboard]}, selection.identities
            )
            reconcile._validate_fresh_dashboard_metadata(
                rows,
                selection,
                reconcile._raw_dashboard_identity_map({"gms": [dashboard]}),
            )

    def test_oracle_inputs_are_filtered_and_reported(self):
        fixtures = self.write_cli_fixtures()
        name = self.selected_rows[0]["name"]
        fixtures["cpuResults"].write_text(
            json.dumps(
                {
                    "rows": [
                        {
                            "name": name,
                            "referenceKind": "cpu-oracle",
                            "evidenceLane": "cpu-oracle",
                            "score": 87.0,
                        }
                    ]
                }
            ),
            encoding="utf-8",
        )
        fixtures["gpuResults"].write_text(
            json.dumps(
                {
                    "rows": [
                        {
                            "name": name,
                            "referenceKind": "test-oracle",
                            "evidenceLane": "test-oracle",
                            "score": 88.0,
                        }
                    ]
                }
            ),
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["current"]["cpuOracle"]["rows"], 1)
        self.assertEqual(manifest["current"]["testOracle"]["rows"], 1)
        self.assertEqual(manifest["rows"]["cpuOracle"][0]["name"], name)
        self.assertEqual(
            manifest["rows"]["cpuOracle"][0]["evidenceLane"], "cpu-oracle"
        )
        self.assertEqual(
            manifest["rows"]["testOracle"][0]["referenceKind"], "test-oracle"
        )

    def test_oracle_inputs_reject_malformed_shape_or_lane(self):
        variants = (
            ("cpuResults", {"rows": [{"name": "small", "referenceKind": "test-oracle"}]}),
            ("gpuResults", {"rows": ["not-an-object"]}),
            ("cpuResults", {"rows": "not-an-array"}),
        )
        for path_key, value in variants:
            with self.subTest(path_key=path_key, value=value):
                fixtures = self.write_cli_fixtures()
                fixtures[path_key].write_text(
                    json.dumps(value), encoding="utf-8"
                )

                status, stdout, _, _, _ = self.run_main(fixtures)

                self.assertEqual(status, 2, stdout)
                self.assertIn("oracle", stdout.lower())

    def test_check_validates_and_reports_fp13_runner(self):
        fixtures = self.write_cli_fixtures()
        fixtures["fp13Runner"].write_text(
            '<testsuite tests="0" failures="0" errors="1" skipped="0">'
            '<error type="fp13-error" message="fp13 lifecycle error"/>'
            "</testsuite>",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="blocked"
        )

        self.assertEqual(status, 1, stdout)
        self.assertIsNotNone(manifest)
        self.assertIn("fp13", stdout.lower())
        self.assertEqual(manifest["current"]["fp13"]["parsedCounts"]["errors"], 1)

    def test_candidate_unlocked_count_uses_causal_candidates_not_supported_rows(self):
        fixtures = self.write_cli_fixtures()
        self.configure_supported_fixture(fixtures)
        evidence = json.loads(
            fixtures["evidenceIndex"].read_text(encoding="utf-8")
        )
        for field in ("supportedAfter", "pixelImproved"):
            del evidence["entries"][0][field]
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["supportedRowsAfter"], 0)
        self.assertEqual(manifest["candidateUnlockedRows"], 1)

    def test_candidate_count_uses_combined_dashboard_and_evidence_causality(self):
        fixtures = self.write_cli_fixtures()
        self.configure_supported_fixture(fixtures)
        dashboard = json.loads(
            fixtures["dashboardJson"].read_text(encoding="utf-8")
        )
        dashboard["gms"][0].update(
            {
                "candidateUnlocked": True,
                "causalBucket": "dashboard-cause",
                "routeSignature": "prepared-image-unpremul",
                "expectedRoute": "prepared-image-unpremul",
                "minimalOperationTrace": "dashboard-trace",
                "ownershipBoundary": "dashboard-owner",
            }
        )
        dashboard_text = json.dumps(dashboard, indent=2, sort_keys=True) + "\n"
        fixtures["dashboardJson"].write_text(dashboard_text, encoding="utf-8")
        (fixtures["dashboardDir"] / "data/gms.json").write_text(
            dashboard_text, encoding="utf-8"
        )
        evidence = json.loads(
            fixtures["evidenceIndex"].read_text(encoding="utf-8")
        )
        for field in (
            "supportedAfter",
            "pixelImproved",
            "candidateUnlocked",
            "causalBucket",
            "routeSignature",
            "expectedRoute",
            "minimalOperationTrace",
            "ownershipBoundary",
        ):
            evidence["entries"][0].pop(field, None)
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["supportedRowsAfter"], 0)
        self.assertEqual(manifest["candidateUnlockedRows"], 1)

    def test_supported_after_rejects_unsafe_or_weakened_thresholds(self):
        values = (-1.0, 101.0, float("nan"), 90.0)
        for value in values:
            with self.subTest(value=value):
                fixtures = self.write_cli_fixtures()
                self.configure_supported_fixture(fixtures)
                evidence = json.loads(
                    fixtures["evidenceIndex"].read_text(encoding="utf-8")
                )
                evidence["entries"][0]["minSimilarity"] = value
                fixtures["evidenceIndex"].write_text(
                    json.dumps(evidence, indent=2, sort_keys=True) + "\n",
                    encoding="utf-8",
                )

                status, stdout, _, _, _ = self.run_main(
                    fixtures, check=True, status="approved"
                )

                self.assertEqual(status, 1, stdout)
                self.assertIn("threshold", stdout.lower())

    def test_oracle_inputs_reject_duplicates_and_out_of_range_scores(self):
        variants = (
            [
                {"name": "blurrect_compare", "score": 10.0},
                {"name": "blurrect_compare", "score": 11.0},
            ],
            [{"name": "blurrect_compare", "score": -0.1}],
            [{"name": "blurrect_compare", "score": 100.1}],
        )
        for rows in variants:
            with self.subTest(rows=rows):
                fixtures = self.write_cli_fixtures()
                fixtures["cpuResults"].write_text(
                    json.dumps({"rows": rows}), encoding="utf-8"
                )

                status, stdout, _, _, _ = self.run_main(fixtures)

                self.assertEqual(status, 2, stdout)
                self.assertIn("oracle", stdout.lower())

    def test_raw_dashboard_and_full_junit_population_filter_to_cohort(self):
        fixtures = self.write_full_population_raw_fixtures()
        raw_rows = json.loads(
            fixtures["dashboardJson"].read_text(encoding="utf-8")
        )["gms"]
        self.assertEqual(len(raw_rows), 610)

        status, stdout, manifest, _, markdown_path = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["current"]["dashboard"]["rows"], 58)
        self.assertEqual(manifest["current"]["runner"]["rows"], 58)
        self.assertEqual(manifest["current"]["runner"]["selectedRows"], 58)
        self.assertEqual(
            manifest["current"]["runnerFullPopulation"]["rows"], 610
        )
        self.assertEqual(
            manifest["provenance"]["skiaRunnerFullPopulation"]["value"]["rows"],
            610,
        )
        self.assertEqual(len(manifest["rows"]["skiaJunit"]), 58)
        markdown = markdown_path.read_text(encoding="utf-8")
        self.assertIn("| `runner` | 58 |", markdown)
        self.assertIn("runnerFullPopulationRows: `610`", markdown)

    def test_residual_followup_failure_code_must_be_present_and_consistent(self):
        variants = (
            "missing-evidence",
            "empty-dashboard",
            "mismatched-evidence",
            "mismatched-junit",
            "original-cohort-code",
        )
        for variant in variants:
            with self.subTest(variant=variant):
                fixtures = self.write_cli_fixtures()
                self.configure_residual_fixture(
                    fixtures,
                    failure_code=(
                        FAILURE_CODE
                        if variant == "original-cohort-code"
                        else FOLLOW_UP_FAILURE_CODE
                    ),
                )
                if variant == "missing-evidence":
                    evidence = json.loads(
                        fixtures["evidenceIndex"].read_text(encoding="utf-8")
                    )
                    del evidence["entries"][0]["failureCode"]
                    fixtures["evidenceIndex"].write_text(
                        json.dumps(evidence, indent=2, sort_keys=True) + "\n",
                        encoding="utf-8",
                    )
                elif variant == "empty-dashboard":
                    dashboard = json.loads(
                        fixtures["dashboardJson"].read_text(encoding="utf-8")
                    )
                    dashboard["gms"][0]["failureCode"] = ""
                    dashboard_text = json.dumps(dashboard, indent=2, sort_keys=True) + "\n"
                    fixtures["dashboardJson"].write_text(
                        dashboard_text, encoding="utf-8"
                    )
                    (fixtures["dashboardDir"] / "data/gms.json").write_text(
                        dashboard_text, encoding="utf-8"
                    )
                elif variant == "mismatched-evidence":
                    evidence = json.loads(
                        fixtures["evidenceIndex"].read_text(encoding="utf-8")
                    )
                    evidence["entries"][0]["failureCode"] = (
                        "unsupported.image.other"
                    )
                    fixtures["evidenceIndex"].write_text(
                        json.dumps(evidence, indent=2, sort_keys=True) + "\n",
                        encoding="utf-8",
                    )
                elif variant == "mismatched-junit":
                    runner_rows = copy.deepcopy(self.selected_rows)
                    runner_rows[0]["failureCode"] = "unsupported.image.other"
                    fixtures["skiaRunner"] = self.write_runner(runner_rows)

                status, stdout, _, _, _ = self.run_main(
                    fixtures, check=True, status="blocked"
                )

                self.assertEqual(status, 2 if variant == "empty-dashboard" else 1, stdout)
                self.assertIn(
                    "distinct" if variant == "original-cohort-code" else "failurecode",
                    stdout.lower(),
                )

    def test_incomplete_supported_after_evidence_is_rejected(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        entry = evidence["entries"][0]
        entry.update(
            {
                "supportedAfter": True,
                "pixelImproved": True,
                "similarityBefore": 90.0,
                "similarityAfter": 98.0,
                "minSimilarity": 95.0,
                "candidateUnlocked": True,
                "causalBucket": "image-alpha",
                "routeSignature": "prepared-image-unpremul",
                "minimalOperationTrace": "draw-image",
                "ownershipBoundary": "kanvas-image",
            }
        )
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertEqual(status, 1)
        self.assertIn("complete pixel evidence", stdout.lower())
        self.assertIsNotNone(manifest)

    def test_check_rejects_missing_hash_duplicate_path_orphan_and_input_alias(self):
        fixtures = self.write_cli_fixtures()
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        artifact = self.write_text("provenance/shared.dat", "shared\n")
        record = {"path": artifact.name, "sha256": self.sha256(artifact)}
        evidence["entries"][0]["artifacts"] = {"route": record}
        evidence["entries"][1]["artifacts"] = {"stat": record}
        evidence["entries"][2]["artifacts"] = {"cpu": {"path": artifact.name}}
        evidence["entries"].append(
            {
                "name": "orphan",
                "referenceKind": "skia-upstream",
                "failureCode": FAILURE_CODE,
                "fallbackReason": "refusal",
                "expectedRoute": "route",
                "rootCause": "cause",
                "followUpFamily": "IMAGE",
            }
        )
        evidence["entries"][3]["artifacts"] = {
            "reference": {
                "path": str(fixtures["scoreBefore"]),
                "sha256": self.sha256(fixtures["scoreBefore"]),
            }
        }
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertEqual(status, 1)
        self.assertIsNotNone(manifest)
        self.assertIn("duplicate", stdout.lower())
        self.assertIn("hash", stdout.lower())
        self.assertIn("orphan", stdout.lower())
        self.assertIn("aliases input", stdout.lower())

    def test_selected_evidence_requires_exact_failure_code(self):
        variants = (
            ("wrong", "unsupported.image.native_binding"),
            ("missing", None),
        )
        for variant, failure_code in variants:
            for check in (False, True):
                with self.subTest(variant=variant, check=check):
                    fixtures = self.write_cli_fixtures()
                    self.configure_supported_fixture(fixtures)
                    evidence = json.loads(
                        fixtures["evidenceIndex"].read_text(encoding="utf-8")
                    )
                    if failure_code is None:
                        del evidence["entries"][0]["failureCode"]
                    else:
                        evidence["entries"][0]["failureCode"] = failure_code
                    fixtures["evidenceIndex"].write_text(
                        json.dumps(evidence, indent=2, sort_keys=True) + "\n",
                        encoding="utf-8",
                    )

                    status, stdout, _, _, _ = self.run_main(
                        fixtures,
                        check=check,
                        status="approved" if check else "classification",
                    )

                    self.assertEqual(status, 1 if check else 2, stdout)
                    self.assertIn("failurecode", stdout.lower())

    def test_check_rejects_duplicate_provenance_artifact_paths(self):
        fixtures = self.write_cli_fixtures()
        artifact = self.write_text("provenance/shared-provenance.dat", "shared\n")
        record = {"path": artifact.name, "sha256": self.sha256(artifact)}
        evidence = json.loads(fixtures["evidenceIndex"].read_text(encoding="utf-8"))
        evidence["provenanceArtifacts"] = {
            "commands": copy.deepcopy(record),
            "environment": copy.deepcopy(record),
        }
        fixtures["evidenceIndex"].write_text(
            json.dumps(evidence, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures, check=True)

        self.assertEqual(status, 1, stdout)
        self.assertIsNotNone(manifest)
        self.assertIn("duplicate", stdout.lower())

    def test_approved_control_is_valid_and_bad_variants_report_policy_reasons(self):
        fixtures = self.write_cli_fixtures()
        self.configure_supported_fixture(fixtures)
        status, stdout, manifest, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )
        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["supportedRowsAfter"], 1)
        self.assertEqual(manifest["routeOnlyRows"], 0)

        variants = (
            ({"after_score": 94.0}, "below threshold/minSimilarity"),
            ({"is_passing": False}, "dashboard row is not passing"),
            ({"is_passing": None}, "dashboard row is not passing"),
            ({"remove_is_passing": True}, "dashboard row is not passing"),
            ({"route_only": True}, "route-only row cannot be supported after"),
            ({"comparable": False}, "not comparable"),
            ({"junit_pass": False}, "passing JUnit"),
            ({"route_signature": "cpu-fallback"}, "cpu fallback"),
        )
        for variant, reason in variants:
            with self.subTest(variant=variant):
                fixtures = self.write_cli_fixtures()
                self.configure_supported_fixture(fixtures, **variant)

                status, stdout, _, _, _ = self.run_main(
                    fixtures, check=True, status="approved"
                )

                self.assertEqual(status, 1, stdout)
                self.assertIn(reason.lower(), stdout.lower())

    def test_raw_dashboard_rows_normalize_and_filter_before_metadata_validation(self):
        fixtures = self.write_raw_dashboard_fixtures()
        raw_rows = json.loads(fixtures["dashboardJson"].read_text(encoding="utf-8"))["gms"]
        self.assertEqual(len(raw_rows), 610)
        self.assertTrue(
            all(
                set(row) == {"name", "family", "isPassing", "similarity", "routeOnly"}
                for row in raw_rows
            )
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        self.assertEqual(manifest["current"]["dashboard"]["rows"], 58)
        self.assertEqual(len(manifest["rows"]["skia"]), 58)
        selected = next(
            row
            for row in manifest["rows"]["skia"]
            if row["name"] == self.selected_rows[0]["name"]
        )
        self.assertEqual(selected["family"], self.selected_rows[0]["family"])
        self.assertEqual(selected["referenceKind"], "skia-upstream")
        self.assertEqual(selected["evidenceLane"], "skia-dashboard")

    def test_enriched_dashboard_none_metadata_does_not_use_raw_defaults(self):
        for field in ("referenceKind", "evidenceLane", "failureCode"):
            with self.subTest(field=field):
                fixtures = self.write_cli_fixtures()
                dashboard = json.loads(
                    fixtures["dashboardJson"].read_text(encoding="utf-8")
                )
                dashboard["gms"][0][field] = None
                dashboard_text = json.dumps(dashboard, indent=2, sort_keys=True) + "\n"
                fixtures["dashboardJson"].write_text(
                    dashboard_text, encoding="utf-8"
                )
                (fixtures["dashboardDir"] / "data/gms.json").write_text(
                    dashboard_text, encoding="utf-8"
                )

                status, stdout, _, _, _ = self.run_main(fixtures)

                self.assertEqual(status, 2, stdout)

    def test_same_name_unselected_identity_cannot_contaminate_selected_lane(self):
        fixtures = self.write_cli_fixtures()
        dashboard = json.loads(
            fixtures["dashboardJson"].read_text(encoding="utf-8")
        )
        selected = dashboard["gms"][0]
        dashboard["gms"].append(
            {
                "name": selected["name"],
                "family": selected["family"],
                "referenceKind": "cpu-oracle",
                "evidenceLane": "cpu-oracle",
                "isPassing": selected["isPassing"],
                "similarity": selected.get("similarity"),
                "routeOnly": selected.get("routeOnly", False),
            }
        )
        dashboard_text = json.dumps(dashboard, indent=2, sort_keys=True) + "\n"
        fixtures["dashboardJson"].write_text(dashboard_text, encoding="utf-8")
        (fixtures["dashboardDir"] / "data/gms.json").write_text(
            dashboard_text, encoding="utf-8"
        )

        status, stdout, manifest, _, _ = self.run_main(fixtures)

        self.assertEqual(status, 0, stdout)
        selected_output = next(
            row
            for row in manifest["rows"]["skia"]
            if row["name"] == selected["name"]
        )
        self.assertEqual(selected_output["evidenceLane"], "skia-dashboard")

    def test_supported_after_requires_fixed_route_signature_and_expected_route(self):
        fixtures = self.write_cli_fixtures()
        self.configure_supported_fixture(
            fixtures,
            route_signature="caller-controlled-route",
            expected_route="caller-controlled-route",
        )

        status, stdout, _, _, _ = self.run_main(
            fixtures, check=True, status="approved"
        )

        self.assertEqual(status, 1, stdout)
        self.assertIn("expected gpu-prepared route", stdout.lower())

    def test_malformed_cohort_population_policy_returns_cli_error(self):
        for value in (None, [], "not-a-policy"):
            with self.subTest(value=value):
                fixtures = self.write_cli_fixtures()
                manifest = json.loads(
                    fixtures["cohortManifest"].read_text(encoding="utf-8")
                )
                manifest["populationPolicy"] = value
                fixtures["cohortManifest"].write_text(
                    json.dumps(manifest, indent=2, sort_keys=True) + "\n",
                    encoding="utf-8",
                )

                status, stdout, result, _, _ = self.run_main(fixtures)

                self.assertEqual(status, 2, stdout)
                self.assertIsNone(result)
                self.assertIn("population policy", stdout.lower())
                self.assertIn("object", stdout.lower())

    def test_classification_and_check_reject_evidence_only_unknown_identity(self):
        orphan = {
            "name": "evidence-only-orphan",
            "family": "IMAGE",
            "referenceKind": "skia-upstream",
            "failureCode": FAILURE_CODE,
            "fallbackReason": "unrelated",
            "expectedRoute": "unrelated",
            "rootCause": "unrelated",
            "followUpFamily": "IMAGE",
        }
        for check, expected_status in ((False, 2), (True, 1)):
            with self.subTest(check=check):
                fixtures = self.write_cli_fixtures()
                evidence = json.loads(
                    fixtures["evidenceIndex"].read_text(encoding="utf-8")
                )
                evidence["entries"].append(copy.deepcopy(orphan))
                fixtures["evidenceIndex"].write_text(
                    json.dumps(evidence, indent=2, sort_keys=True) + "\n",
                    encoding="utf-8",
                )

                status, stdout, manifest, _, _ = self.run_main(fixtures, check=check)

                self.assertEqual(status, expected_status, stdout)
                self.assertIn("unknown identity", stdout.lower())
                if not check:
                    self.assertIsNone(manifest)

    def test_fresh_dashboard_metadata_must_match_frozen_cohort(self):
        variants = (
            "missing-family",
            "wrong-family",
            "missing-evidence-lane",
            "wrong-evidence-lane",
            "duplicate",
            "unknown",
        )
        for variant in variants:
            with self.subTest(variant=variant):
                fixtures = self.write_cli_fixtures()
                dashboard = json.loads(
                    fixtures["dashboardJson"].read_text(encoding="utf-8")
                )
                first = dashboard["gms"][0]
                if variant == "missing-family":
                    del first["family"]
                elif variant == "wrong-family":
                    first["family"] = "WRONG_FAMILY"
                elif variant == "missing-evidence-lane":
                    del first["evidenceLane"]
                elif variant == "wrong-evidence-lane":
                    first["evidenceLane"] = "cpu-oracle"
                elif variant == "duplicate":
                    dashboard["gms"].append(copy.deepcopy(first))
                elif variant == "unknown":
                    first["name"] = "not-in-wave2"
                dashboard_text = json.dumps(dashboard, indent=2, sort_keys=True) + "\n"
                fixtures["dashboardJson"].write_text(dashboard_text, encoding="utf-8")
                (
                    fixtures["dashboardDir"] / "data/gms.json"
                ).write_text(dashboard_text, encoding="utf-8")

                status, stdout, _, _, _ = self.run_main(fixtures)

                expected_status = 2
                self.assertEqual(status, expected_status, stdout)

        fixtures = self.write_cli_fixtures()
        dashboard = json.loads(fixtures["dashboardJson"].read_text(encoding="utf-8"))
        dashboard["gms"][0]["freshMetadataMarker"] = "preserve-me"
        dashboard_text = json.dumps(dashboard, indent=2, sort_keys=True) + "\n"
        fixtures["dashboardJson"].write_text(dashboard_text, encoding="utf-8")
        (fixtures["dashboardDir"] / "data/gms.json").write_text(
            dashboard_text, encoding="utf-8"
        )
        status, stdout, manifest, _, _ = self.run_main(fixtures)
        self.assertEqual(status, 0, stdout)
        self.assertEqual(
            manifest["rows"]["skia"][0]["freshMetadataMarker"], "preserve-me"
        )

    def test_fresh_dashboard_reference_kind_must_match_frozen_cohort(self):
        for missing in (True, False):
            with self.subTest(missing=missing):
                fixtures = self.write_cli_fixtures()
                dashboard = json.loads(
                    fixtures["dashboardJson"].read_text(encoding="utf-8")
                )
                first = dashboard["gms"][0]
                if missing:
                    del first["referenceKind"]
                else:
                    first["referenceKind"] = "cpu-oracle"
                dashboard_text = json.dumps(dashboard, indent=2, sort_keys=True) + "\n"
                fixtures["dashboardJson"].write_text(dashboard_text, encoding="utf-8")
                (fixtures["dashboardDir"] / "data/gms.json").write_text(
                    dashboard_text, encoding="utf-8"
                )

                status, stdout, _, _, _ = self.run_main(fixtures)

                expected_status = 2
                self.assertEqual(status, expected_status, stdout)
                self.assertIn(
                    "referencekind" if missing else "unknown identity",
                    stdout.lower(),
                )

    def test_check_rejects_weak_population_context(self):
        variants = (
            ("cohort", "includeBlocking", False),
            ("cohort", "dashboardProperty", "-Pgm.includeBlocking=false"),
            ("cohort", "runnerProperty", "-Dkanvas.gm.includeBlocking=false"),
            ("cohort", "wave0DirectlyComparable", True),
            ("cohort", "wave0Population", 614),
            ("cohort", "comparisonNote", "not-population-shifted"),
            ("fresh", "includeBlocking", False),
        )
        for source, key, value in variants:
            with self.subTest(source=source, key=key):
                fixtures = self.write_cli_fixtures()
                path = (
                    fixtures["cohortManifest"]
                    if source == "cohort"
                    else fixtures["evidenceIndex"]
                )
                payload = json.loads(path.read_text(encoding="utf-8"))
                container = payload.setdefault("populationPolicy", {})
                container[key] = value
                path.write_text(
                    json.dumps(payload, indent=2, sort_keys=True) + "\n",
                    encoding="utf-8",
                )

                status, stdout, _, _, _ = self.run_main(fixtures, check=True, status="blocked")

                self.assertEqual(status, 1, stdout)

    def test_raw_malformed_evidence_is_rejected_before_wave1_parsing(self):
        variants = (
            ("not-an-evidence-object", "non-dict"),
            ({"referenceKind": "skia-upstream", "failureCode": FAILURE_CODE}, "identity"),
        )
        for entry, reason in variants:
            with self.subTest(reason=reason):
                fixtures = self.write_cli_fixtures()
                evidence = json.loads(
                    fixtures["evidenceIndex"].read_text(encoding="utf-8")
                )
                evidence["entries"].append(entry)
                fixtures["evidenceIndex"].write_text(
                    json.dumps(evidence, indent=2, sort_keys=True) + "\n",
                    encoding="utf-8",
                )

                status, stdout, _, _, _ = self.run_main(fixtures)

                self.assertEqual(status, 2, stdout)
                self.assertIn(reason, stdout.lower())


if __name__ == "__main__":
    unittest.main()
