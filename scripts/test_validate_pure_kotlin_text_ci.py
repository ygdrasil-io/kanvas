#!/usr/bin/env python3
import importlib.util
import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = PROJECT_ROOT / "scripts" / "validate_pure_kotlin_text_ci.py"

sys.dont_write_bytecode = True


def load_validator():
    if not VALIDATOR_PATH.is_file():
        raise AssertionError("missing validator script: scripts/validate_pure_kotlin_text_ci.py")
    spec = importlib.util.spec_from_file_location("validate_pure_kotlin_text_ci", VALIDATOR_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError("unable to load validator module spec")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class PureKotlinTextCiValidationTest(unittest.TestCase):
    def test_font_ci_lane_evidence_and_workflow_are_auditable_non_claims(self) -> None:
        validator = load_validator()
        evidence = validator.load_evidence(PROJECT_ROOT)
        workflow_text = validator.load_workflow_text(PROJECT_ROOT)

        validator.validate_evidence(PROJECT_ROOT, evidence)
        validator.validate_workflow_text(workflow_text)

        self.assertEqual(
            [
                "schemaVersion",
                "laneId",
                "laneName",
                "tickets",
                "dashboardClassification",
                "claimPromotionAllowed",
                "supportClaim",
                "headlessPolicy",
                "gradleTasks",
                "workflow",
                "missingModulePolicy",
                "triggerSamples",
                "nonClaims",
                "validationCommands",
            ],
            list(evidence.keys()),
        )
        self.assertEqual("pure-kotlin-font-foundation", evidence["laneName"])
        self.assertEqual("tracked-gap", evidence["dashboardClassification"])
        self.assertIs(evidence["claimPromotionAllowed"], False)
        self.assertEqual(
            [
                ":font:core:test",
                ":font:sfnt:test",
                ":font:scaler:test",
                ":font:text:test",
                ":font:glyph:test",
                ":font:gpu-api:test",
            ],
            [row["task"] for row in evidence["gradleTasks"]],
        )
        self.assertEqual("font.ci.module-missing", evidence["missingModulePolicy"]["diagnosticCode"])
        self.assertEqual(
            "python3 scripts/validate_pure_kotlin_text_boundary_contracts.py",
            evidence["workflow"]["invokesBoundaryValidator"],
        )
        self.assertEqual(
            "python3 scripts/validate_pure_kotlin_text_claim_dashboard.py",
            evidence["workflow"]["invokesClaimDashboardValidator"],
        )
        self.assertEqual("git diff --check", evidence["workflow"]["invokesDiffCheck"])
        self.assertEqual(
            [".upstream/specs/pure-kotlin-text", "reports/pure-kotlin-text"],
            evidence["workflow"]["diffCheckPaths"],
        )
        self.assertIn(
            "python3 scripts/validate_pure_kotlin_text_boundary_contracts.py",
            workflow_text,
        )
        self.assertIn(
            "python3 scripts/validate_pure_kotlin_text_claim_dashboard.py",
            workflow_text,
        )

        samples = {row["path"]: row for row in evidence["triggerSamples"]}
        self.assertTrue(samples[".upstream/specs/pure-kotlin-text/README.md"]["triggersLane"])
        self.assertTrue(
            samples[
                ".upstream/specs/pure-kotlin-text/tickets/M0-claims-ci-diagnostics/KFONT-M0-002-add-pure-kotlin-text-specs-to-ci-trigger-paths.md"
            ]["triggersLane"]
        )
        self.assertFalse(
            samples["archives/target-closeout-2026-05-31/pure-kotlin-text/archived-migration.md"]["triggersLane"]
        )

    def test_validator_rejects_missing_required_workflow_path_filter(self) -> None:
        validator = load_validator()
        workflow_text = validator.load_workflow_text(PROJECT_ROOT)
        modified = workflow_text.replace("      - '.upstream/specs/pure-kotlin-text/**'\n", "")

        with self.assertRaises(validator.ValidationError) as missing:
            validator.validate_workflow_text(modified)
        self.assertIn("missing pull_request path filter", str(missing.exception))

    def test_font_ci_job_executes_diff_check_for_spec_paths(self) -> None:
        validator = load_validator()
        workflow_text = validator.load_workflow_text(PROJECT_ROOT)
        job_block = validator.workflow_job_block(workflow_text, validator.JOB_ID)

        validator.validate_workflow_text(workflow_text)
        self.assertIn("fetch-depth: 0", job_block)
        self.assertIn("git diff --check", job_block)
        self.assertIn("git merge-base", job_block)
        self.assertIn(".upstream/specs/pure-kotlin-text", job_block)
        self.assertIn("reports/pure-kotlin-text", job_block)

    def test_validator_rejects_missing_executed_diff_check_step(self) -> None:
        validator = load_validator()
        workflow_text = validator.load_workflow_text(PROJECT_ROOT)
        job_block = validator.workflow_job_block(workflow_text, validator.JOB_ID)
        step_block = validator.workflow_step_block(job_block, validator.DIFF_CHECK_STEP_NAME)
        modified = workflow_text.replace(step_block, "")

        with self.assertRaises(validator.ValidationError) as missing:
            validator.validate_workflow_text(modified)
        self.assertIn("workflow missing step", str(missing.exception))

    def test_validator_rejects_disabled_diff_check_step(self) -> None:
        validator = load_validator()
        workflow_text = validator.load_workflow_text(PROJECT_ROOT)
        modified = workflow_text.replace(
            "      - name: Check pure Kotlin text diff hygiene\n",
            "      - name: Check pure Kotlin text diff hygiene\n"
            "        if: false\n",
        )

        with self.assertRaises(validator.ValidationError) as disabled:
            validator.validate_workflow_text(modified)
        self.assertIn("must run unconditionally", str(disabled.exception))

    def test_validator_rejects_comment_only_diff_check_command(self) -> None:
        validator = load_validator()
        workflow_text = validator.load_workflow_text(PROJECT_ROOT)
        modified = workflow_text.replace(
            '          git diff --check "$BASE_SHA...HEAD" -- \\\n',
            '          # git diff --check "$BASE_SHA...HEAD" -- \\\n',
        ).replace(
            "          git diff --check -- \\\n",
            "          # git diff --check -- \\\n",
        )

        with self.assertRaises(validator.ValidationError) as commented:
            validator.validate_workflow_text(modified)
        self.assertIn("diff hygiene step must execute git diff --check", str(commented.exception))

    def test_validator_rejects_diff_check_without_default_branch_base_fallback(self) -> None:
        validator = load_validator()
        workflow_text = validator.load_workflow_text(PROJECT_ROOT)
        modified = workflow_text.replace(
            "        env:\n"
            "          DEFAULT_BRANCH: ${{ github.event.repository.default_branch }}\n",
            "",
        ).replace(
            "          if [ -z \"$BASE_SHA\" ] || ! git cat-file -e \"$BASE_SHA^{commit}\"; then\n"
            "            git fetch --no-tags --prune origin \"+refs/heads/${DEFAULT_BRANCH}:refs/remotes/origin/${DEFAULT_BRANCH}\"\n"
            "            BASE_SHA=\"$(git merge-base \"origin/${DEFAULT_BRANCH}\" HEAD)\"\n"
            "          fi\n"
            "          if [ -z \"$BASE_SHA\" ]; then\n"
            "            echo \"Unable to resolve a base commit for pure Kotlin text diff hygiene.\" >&2\n"
            "            exit 1\n"
            "          fi\n",
            "",
        )

        with self.assertRaises(validator.ValidationError) as missing_fallback:
            validator.validate_workflow_text(modified)
        self.assertIn("default-branch merge base fallback", str(missing_fallback.exception))

    def test_validator_rejects_disabled_validator_steps(self) -> None:
        validator = load_validator()
        workflow_text = validator.load_workflow_text(PROJECT_ROOT)

        for step_name in (
            "Validate pure Kotlin font CI lane",
            "Validate pure Kotlin text boundaries",
            "Validate pure Kotlin text claim dashboard",
        ):
            with self.subTest(step_name=step_name):
                modified = workflow_text.replace(
                    f"      - name: {step_name}\n",
                    f"      - name: {step_name}\n"
                    "        if: false\n",
                )

                with self.assertRaises(validator.ValidationError) as disabled:
                    validator.validate_workflow_text(modified)
                self.assertIn("must run unconditionally", str(disabled.exception))

    def test_validator_rejects_fail_open_validator_steps(self) -> None:
        validator = load_validator()
        workflow_text = validator.load_workflow_text(PROJECT_ROOT)

        for step_name in (
            "Validate pure Kotlin font CI lane",
            "Validate pure Kotlin text boundaries",
            "Validate pure Kotlin text claim dashboard",
        ):
            with self.subTest(step_name=step_name):
                modified = workflow_text.replace(
                    f"      - name: {step_name}\n",
                    f"      - name: {step_name}\n"
                    "        continue-on-error: true\n",
                )

                with self.assertRaises(validator.ValidationError) as fail_open:
                    validator.validate_workflow_text(modified)
                self.assertIn("must not continue on error", str(fail_open.exception))

    def test_validator_rejects_comment_only_validator_steps(self) -> None:
        validator = load_validator()
        workflow_text = validator.load_workflow_text(PROJECT_ROOT)

        for command in (
            "python3 scripts/validate_pure_kotlin_text_ci.py",
            "python3 scripts/validate_pure_kotlin_text_boundary_contracts.py",
            "python3 scripts/validate_pure_kotlin_text_claim_dashboard.py",
        ):
            with self.subTest(command=command):
                modified = workflow_text.replace(
                    f"        run: {command}\n",
                    f"        run: |\n"
                    f"          # {command}\n"
                    "          true\n",
                )

                with self.assertRaises(validator.ValidationError) as commented:
                    validator.validate_workflow_text(modified)
                self.assertIn("must execute command", str(commented.exception))

    def test_validator_rejects_claim_promotion_or_support_classification(self) -> None:
        validator = load_validator()
        evidence = validator.load_evidence(PROJECT_ROOT)
        modified = dict(evidence)
        modified["dashboardClassification"] = "target-supported"
        modified["claimPromotionAllowed"] = True

        with self.assertRaises(validator.ValidationError) as support_claim:
            validator.validate_evidence(PROJECT_ROOT, modified)
        self.assertIn("must remain tracked-gap", str(support_claim.exception))

    def test_missing_module_diagnostic_is_stable_and_tracked_gap(self) -> None:
        validator = load_validator()
        evidence = validator.load_evidence(PROJECT_ROOT)
        settings_text = "\n".join(
            [
                'include(":font")',
                'include(":font:core")',
                'include(":font:scaler")',
                'include(":font:text")',
                'include(":font:glyph")',
                'include(":font:gpu-api")',
                "",
            ]
        )

        diagnostics = validator.module_diagnostics_from_settings_text(evidence, settings_text)

        self.assertEqual(
            [
                {
                    "code": "font.ci.module-missing",
                    "classification": "tracked-gap",
                    "modulePath": ":font:sfnt",
                    "task": ":font:sfnt:test",
                    "claimPromotionAllowed": False,
                }
            ],
            diagnostics,
        )

    def test_trigger_glob_evaluator_keeps_archives_inactive(self) -> None:
        validator = load_validator()

        self.assertTrue(
            validator.path_matches_glob(
                ".upstream/specs/pure-kotlin-text/tickets/M0-claims-ci-diagnostics/KFONT-M0-001-wire-pure-kotlin-font-modules-into-ci.md",
                ".upstream/specs/pure-kotlin-text/**",
            )
        )
        self.assertFalse(
            validator.path_matches_glob(
                "archives/target-closeout-2026-05-31/pure-kotlin-text/archived-migration.md",
                ".upstream/specs/pure-kotlin-text/**",
            )
        )


if __name__ == "__main__":
    unittest.main()
