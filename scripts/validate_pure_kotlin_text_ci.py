#!/usr/bin/env python3
import fnmatch
import json
import re
import sys
from pathlib import Path
from typing import Any


EVIDENCE_PATH = "reports/pure-kotlin-text/font-ci-lane.json"
WORKFLOW_PATH = ".github/workflows/test.yml"
SETTINGS_PATH = "settings.gradle.kts"

LANE_NAME = "pure-kotlin-font-foundation"
JOB_ID = "pure_kotlin_font_foundation"
EXPECTED_TASKS = [
    ":font:core:test",
    ":font:sfnt:test",
    ":font:scaler:test",
    ":font:text:test",
    ":font:glyph:test",
    ":font:gpu-api:test",
]
REQUIRED_PATH_FILTERS = [
    ".upstream/specs/pure-kotlin-text/**",
    "font/**",
    "reports/pure-kotlin-text/**",
    "scripts/validate_pure_kotlin_text_boundary_contracts.py",
    "scripts/test_validate_pure_kotlin_text_boundary_contracts.py",
    "scripts/validate_pure_kotlin_text_claim_dashboard.py",
    "scripts/test_validate_pure_kotlin_text_claim_dashboard.py",
]
DIFF_CHECK_COMMAND = "git diff --check"
DIFF_CHECK_STEP_NAME = "Check pure Kotlin text diff hygiene"
DIFF_CHECK_BASE_EXPR = "BASE_SHA=\"${{ github.event.pull_request.base.sha || github.event.before || '' }}\""
DIFF_CHECK_BASE_TEST = 'if [ -z "$BASE_SHA" ] || ! git cat-file -e "$BASE_SHA^{commit}"; then'
DIFF_CHECK_DEFAULT_BRANCH_ENV = "DEFAULT_BRANCH: ${{ github.event.repository.default_branch }}"
DIFF_CHECK_DEFAULT_BRANCH_FETCH = 'git fetch --no-tags --prune origin "+refs/heads/${DEFAULT_BRANCH}:refs/remotes/origin/${DEFAULT_BRANCH}"'
DIFF_CHECK_DEFAULT_BRANCH_MERGE_BASE = 'BASE_SHA="$(git merge-base "origin/${DEFAULT_BRANCH}" HEAD)"'
DIFF_CHECK_RANGE_PREFIX = 'git diff --check "$BASE_SHA...HEAD" --'
CI_VALIDATOR_STEP_NAME = "Validate pure Kotlin font CI lane"
CI_VALIDATOR_COMMAND = "python3 scripts/validate_pure_kotlin_text_ci.py"
BOUNDARY_VALIDATOR_STEP_NAME = "Validate pure Kotlin text boundaries"
BOUNDARY_VALIDATOR_COMMAND = "python3 scripts/validate_pure_kotlin_text_boundary_contracts.py"
CLAIM_DASHBOARD_VALIDATOR_STEP_NAME = "Validate pure Kotlin text claim dashboard"
CLAIM_DASHBOARD_VALIDATOR_COMMAND = "python3 scripts/validate_pure_kotlin_text_claim_dashboard.py"
DIFF_CHECK_PATHS = [
    ".upstream/specs/pure-kotlin-text",
    "reports/pure-kotlin-text",
]
TOP_LEVEL_KEYS = [
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
]

FORBIDDEN_NATIVE_FONT_ENGINES = [
    "HarfBuzz",
    "FreeType",
    "Fontations",
    "CoreText",
    "DirectWrite",
    "fontconfig",
    "java.awt",
    "javax.swing",
    "JNI",
    "AWT",
]


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"pure Kotlin text CI validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def require_keys(payload: dict[str, Any], expected: list[str], label: str) -> None:
    require(list(payload.keys()) == expected, f"{label} keys must be stable and ordered: {expected}")


def require_string(value: Any, label: str) -> str:
    require(isinstance(value, str) and value.strip() == value and value, f"{label} must be a non-empty trimmed string")
    return value


def require_false(value: Any, label: str) -> None:
    require(value is False, f"{label} must be false")


def require_existing_path(root: Path, relative_path: str, label: str) -> Path:
    require_string(relative_path, label)
    require(not Path(relative_path).is_absolute(), f"{label} must be relative: {relative_path}")
    resolved_root = root.resolve()
    resolved_path = (resolved_root / relative_path).resolve()
    require(
        resolved_path == resolved_root or resolved_root in resolved_path.parents,
        f"{label} must stay under project root: {relative_path}",
    )
    require(resolved_path.is_file(), f"{label} does not exist: {relative_path}")
    return resolved_path


def load_json(root: Path, relative_path: str) -> Any:
    path = require_existing_path(root, relative_path, relative_path)
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def load_evidence(root: Path) -> dict[str, Any]:
    evidence = load_json(root, EVIDENCE_PATH)
    require(isinstance(evidence, dict), f"{EVIDENCE_PATH} root must be an object")
    return evidence


def load_workflow_text(root: Path) -> str:
    return require_existing_path(root, WORKFLOW_PATH, WORKFLOW_PATH).read_text(encoding="utf-8")


def path_matches_glob(path: str, pattern: str) -> bool:
    require_string(path, "path")
    require_string(pattern, "pattern")
    return fnmatch.fnmatchcase(path, pattern)


def _strip_yaml_scalar(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def _event_block(workflow_text: str, event_name: str) -> list[str]:
    lines = workflow_text.splitlines()
    marker = f"  {event_name}:"
    start = None
    for index, line in enumerate(lines):
        if line == marker:
            start = index + 1
            break
    require(start is not None, f"missing workflow event: {event_name}")

    block: list[str] = []
    for line in lines[start:]:
        if line.startswith("  ") and not line.startswith("    ") and line.strip().endswith(":"):
            break
        if line and not line.startswith(" ") and not line.startswith("#"):
            break
        block.append(line)
    return block


def workflow_paths_for_event(workflow_text: str, event_name: str) -> list[str]:
    paths: list[str] = []
    in_paths = False
    for line in _event_block(workflow_text, event_name):
        if line == "    paths:":
            in_paths = True
            continue
        if not in_paths:
            continue
        if line.startswith("      - "):
            paths.append(_strip_yaml_scalar(line.strip()[2:]))
            continue
        if line.strip() == "" or line.startswith("      #"):
            continue
        if line.startswith("    ") and not line.startswith("      "):
            break
        if line.startswith("  ") and not line.startswith("      "):
            break
    require(paths, f"{event_name} paths must be non-empty")
    return paths


def workflow_job_block(workflow_text: str, job_id: str) -> str:
    lines = workflow_text.splitlines()
    marker = f"  {job_id}:"
    start = None
    for index, line in enumerate(lines):
        if line == marker:
            start = index
            break
    require(start is not None, f"workflow missing job id: {job_id}")

    block: list[str] = []
    for line in lines[start:]:
        if block and line.startswith("  ") and not line.startswith("    ") and line.strip().endswith(":"):
            break
        if block and line and not line.startswith(" "):
            break
        block.append(line)
    return "\n".join(block)


def workflow_step_block(job_block: str, step_name: str) -> str:
    lines = job_block.splitlines()
    marker = f"      - name: {step_name}"
    start = None
    for index, line in enumerate(lines):
        if line == marker:
            start = index
            break
    require(start is not None, f"workflow missing step: {step_name}")

    block: list[str] = []
    for line in lines[start:]:
        if block and line.startswith("      - "):
            break
        block.append(line)
    return "\n".join(block)


def workflow_step_run_lines(step_block: str, step_name: str) -> list[str]:
    lines = step_block.splitlines()
    start = None
    for index, line in enumerate(lines):
        if line == "        run: |":
            start = index + 1
            break
    require(start is not None, f"workflow step must use a block run script: {step_name}")

    run_lines: list[str] = []
    for line in lines[start:]:
        if line.startswith("          "):
            run_lines.append(line[10:])
            continue
        if not line.strip():
            run_lines.append("")
            continue
        break
    require(run_lines, f"workflow step run script must be non-empty: {step_name}")
    return run_lines


def executable_shell_lines(run_lines: list[str]) -> list[str]:
    return [
        line.strip()
        for line in run_lines
        if line.strip() and not line.strip().startswith("#")
    ]


def workflow_step_command_lines(step_block: str, step_name: str) -> list[str]:
    lines = step_block.splitlines()
    for index, line in enumerate(lines):
        if line == "        run: |":
            return workflow_step_run_lines(step_block, step_name)
        if line.startswith("        run: "):
            return [_strip_yaml_scalar(line[len("        run: ") :])]
    fail(f"workflow step must define run command: {step_name}")


def validate_required_step(step: str, step_name: str) -> None:
    require(
        not any(re.match(r"^        if\s*:", line) for line in step.splitlines()),
        f"workflow step must run unconditionally: {step_name}",
    )
    require(
        not any(re.match(r"^        continue-on-error\s*:", line) for line in step.splitlines()),
        f"workflow step must not continue on error: {step_name}",
    )


def validate_unconditional_command_step(job_block: str, step_name: str, command: str) -> None:
    step = workflow_step_block(job_block, step_name)
    validate_required_step(step, step_name)
    executable = executable_shell_lines(workflow_step_command_lines(step, step_name))
    require(
        command in executable,
        f"workflow step must execute command {command}: {step_name}",
    )


def validate_diff_check_step(job_block: str) -> None:
    step = workflow_step_block(job_block, DIFF_CHECK_STEP_NAME)
    validate_required_step(step, DIFF_CHECK_STEP_NAME)
    require(
        DIFF_CHECK_DEFAULT_BRANCH_ENV in step,
        "workflow diff check must expose the default-branch merge base fallback",
    )

    run_lines = workflow_step_run_lines(step, DIFF_CHECK_STEP_NAME)
    executable = executable_shell_lines(run_lines)
    require(DIFF_CHECK_BASE_EXPR in executable, "workflow diff check must read pull_request/push base SHA")
    require(
        DIFF_CHECK_BASE_TEST in executable,
        "workflow diff check must test for missing or unavailable base SHA",
    )
    require(
        DIFF_CHECK_DEFAULT_BRANCH_FETCH in executable,
        "workflow diff check must fetch default-branch merge base fallback",
    )
    require(
        DIFF_CHECK_DEFAULT_BRANCH_MERGE_BASE in executable,
        "workflow diff check must compute default-branch merge base fallback",
    )
    require(
        'echo "Unable to resolve a base commit for pure Kotlin text diff hygiene." >&2' in executable
        and "exit 1" in executable,
        "workflow diff check must fail when no base commit can be resolved",
    )
    require(
        any(line.startswith(DIFF_CHECK_RANGE_PREFIX) for line in executable),
        "diff hygiene step must execute git diff --check with an explicit base range",
    )
    require(
        not any(line.startswith("git diff --check --") for line in executable),
        "workflow diff check must not fall back to a checkout-only diff",
    )
    for path in DIFF_CHECK_PATHS:
        require(
            any(line == path or line.startswith(f"{path} ") for line in executable),
            f"workflow diff check must include path: {path}",
        )


def validate_workflow_text(workflow_text: str) -> None:
    job_block = workflow_job_block(workflow_text, JOB_ID)
    require(LANE_NAME in job_block, f"workflow missing lane name: {LANE_NAME}")
    require("fetch-depth: 0" in job_block, "workflow checkout must fetch history for diff hygiene")
    validate_diff_check_step(job_block)
    validate_unconditional_command_step(job_block, CI_VALIDATOR_STEP_NAME, CI_VALIDATOR_COMMAND)
    validate_unconditional_command_step(job_block, BOUNDARY_VALIDATOR_STEP_NAME, BOUNDARY_VALIDATOR_COMMAND)
    validate_unconditional_command_step(job_block, CLAIM_DASHBOARD_VALIDATOR_STEP_NAME, CLAIM_DASHBOARD_VALIDATOR_COMMAND)
    require("./gradlew --no-daemon" in job_block, "workflow must invoke Gradle headlessly")
    for task in EXPECTED_TASKS:
        require(task in job_block, f"workflow missing Gradle task: {task}")

    for event_name in ("pull_request", "push"):
        paths = workflow_paths_for_event(workflow_text, event_name)
        for required in REQUIRED_PATH_FILTERS:
            require(required in paths, f"missing {event_name} path filter: {required}")

    for token in FORBIDDEN_NATIVE_FONT_ENGINES:
        require(token not in job_block, f"font CI job must not require native font engine token: {token}")


def task_to_module_path(task: str) -> str:
    parts = task.split(":")
    require(len(parts) >= 3 and parts[-1] == "test", f"font CI task must be a Gradle test task: {task}")
    return ":".join(parts[:-1])


def included_modules(settings_text: str) -> set[str]:
    return set(re.findall(r'include\("([^"]+)"\)', settings_text))


def module_diagnostics_from_settings_text(evidence: dict[str, Any], settings_text: str) -> list[dict[str, Any]]:
    policy = evidence["missingModulePolicy"]
    modules = included_modules(settings_text)
    diagnostics: list[dict[str, Any]] = []
    for row in evidence["gradleTasks"]:
        task = row["task"]
        module_path = row["modulePath"]
        if module_path not in modules:
            diagnostics.append(
                {
                    "code": policy["diagnosticCode"],
                    "classification": policy["classification"],
                    "modulePath": module_path,
                    "task": task,
                    "claimPromotionAllowed": False,
                }
            )
    return diagnostics


def _module_dir(module_path: str) -> str:
    return module_path.lstrip(":").replace(":", "/")


def validate_project_modules(root: Path, evidence: dict[str, Any]) -> None:
    settings_text = require_existing_path(root, SETTINGS_PATH, SETTINGS_PATH).read_text(encoding="utf-8")
    diagnostics = module_diagnostics_from_settings_text(evidence, settings_text)
    if diagnostics:
        fail(f"module diagnostics: {json.dumps(diagnostics, sort_keys=True)}")
    for row in evidence["gradleTasks"]:
        module_path = row["modulePath"]
        build_file = f"{_module_dir(module_path)}/build.gradle.kts"
        require_existing_path(root, build_file, f"{module_path} build file")


def validate_headless_policy(policy: Any) -> None:
    require(isinstance(policy, dict), "headlessPolicy must be an object")
    require_keys(
        policy,
        [
            "runtime",
            "nativeFontEnginesRequired",
            "kadreRequired",
            "normativeOracle",
            "forbiddenDependencies",
        ],
        "headlessPolicy",
    )
    require(policy["runtime"] == "ubuntu-latest", "headlessPolicy.runtime must be ubuntu-latest")
    require_false(policy["nativeFontEnginesRequired"], "headlessPolicy.nativeFontEnginesRequired")
    require_false(policy["kadreRequired"], "headlessPolicy.kadreRequired")
    require(policy["normativeOracle"] == "Kanvas-owned CI diagnostics only", "headlessPolicy normative oracle changed")
    require(policy["forbiddenDependencies"] == FORBIDDEN_NATIVE_FONT_ENGINES, "headlessPolicy forbidden dependencies changed")


def validate_gradle_tasks(tasks: Any) -> None:
    require(isinstance(tasks, list), "gradleTasks must be a list")
    require([row.get("task") for row in tasks if isinstance(row, dict)] == EXPECTED_TASKS, "gradleTasks must list the six M0 tasks in order")
    for index, row in enumerate(tasks):
        require(isinstance(row, dict), f"gradleTasks[{index}] must be an object")
        require_keys(row, ["modulePath", "task", "moduleExistsExpected", "claimPromotionAllowed"], f"gradleTasks[{index}]")
        require(row["modulePath"] == task_to_module_path(row["task"]), f"gradleTasks[{index}] modulePath must match task")
        require(row["moduleExistsExpected"] is True, f"gradleTasks[{index}] must expect current module existence")
        require_false(row["claimPromotionAllowed"], f"gradleTasks[{index}].claimPromotionAllowed")


def validate_workflow_evidence(workflow: Any) -> None:
    require(isinstance(workflow, dict), "workflow evidence must be an object")
    require_keys(
        workflow,
        [
            "file",
            "jobId",
            "requiredPathFilters",
            "invokesValidator",
            "invokesBoundaryValidator",
            "invokesClaimDashboardValidator",
            "invokesDiffCheck",
            "diffCheckPaths",
        ],
        "workflow",
    )
    require(workflow["file"] == WORKFLOW_PATH, "workflow.file changed")
    require(workflow["jobId"] == JOB_ID, "workflow.jobId changed")
    require(workflow["requiredPathFilters"] == REQUIRED_PATH_FILTERS, "workflow.requiredPathFilters changed")
    require(
        workflow["invokesValidator"] == "python3 scripts/validate_pure_kotlin_text_ci.py",
        "workflow.invokesValidator changed",
    )
    require(
        workflow["invokesBoundaryValidator"] == "python3 scripts/validate_pure_kotlin_text_boundary_contracts.py",
        "workflow.invokesBoundaryValidator changed",
    )
    require(
        workflow["invokesClaimDashboardValidator"] == "python3 scripts/validate_pure_kotlin_text_claim_dashboard.py",
        "workflow.invokesClaimDashboardValidator changed",
    )
    require(workflow["invokesDiffCheck"] == DIFF_CHECK_COMMAND, "workflow.invokesDiffCheck changed")
    require(workflow["diffCheckPaths"] == DIFF_CHECK_PATHS, "workflow.diffCheckPaths changed")


def validate_missing_module_policy(policy: Any) -> None:
    require(isinstance(policy, dict), "missingModulePolicy must be an object")
    require_keys(
        policy,
        [
            "diagnosticCode",
            "classification",
            "claimPromotionAllowed",
            "action",
            "blockingMilestones",
            "sampleDiagnostic",
        ],
        "missingModulePolicy",
    )
    require(policy["diagnosticCode"] == "font.ci.module-missing", "missingModulePolicy diagnostic code changed")
    require(policy["classification"] == "tracked-gap", "missingModulePolicy classification must remain tracked-gap")
    require_false(policy["claimPromotionAllowed"], "missingModulePolicy.claimPromotionAllowed")
    require(
        policy["action"] == "emit-diagnostic-and-fail-lane-until-module-or-reviewed-plan-exists",
        "missingModulePolicy action changed",
    )
    require(policy["blockingMilestones"] == [f"M{index}" for index in range(1, 14)], "blockingMilestones changed")
    sample = policy["sampleDiagnostic"]
    require(isinstance(sample, dict), "missingModulePolicy.sampleDiagnostic must be an object")
    require_keys(sample, ["code", "classification", "modulePath", "task", "claimPromotionAllowed"], "sampleDiagnostic")
    require(sample["code"] == policy["diagnosticCode"], "sampleDiagnostic code must match policy")
    require(sample["classification"] == "tracked-gap", "sampleDiagnostic classification must remain tracked-gap")
    require_false(sample["claimPromotionAllowed"], "sampleDiagnostic.claimPromotionAllowed")


def validate_trigger_samples(samples: Any) -> None:
    require(isinstance(samples, list), "triggerSamples must be a list")
    expected_paths = [
        ".upstream/specs/pure-kotlin-text/README.md",
        ".upstream/specs/pure-kotlin-text/tickets/M0-claims-ci-diagnostics/KFONT-M0-002-add-pure-kotlin-text-specs-to-ci-trigger-paths.md",
        "archives/target-closeout-2026-05-31/pure-kotlin-text/archived-migration.md",
    ]
    require([row.get("path") for row in samples if isinstance(row, dict)] == expected_paths, "triggerSamples paths changed")
    for index, row in enumerate(samples):
        require(isinstance(row, dict), f"triggerSamples[{index}] must be an object")
        require_keys(row, ["path", "matchedGlob", "triggersLane", "laneName", "reason"], f"triggerSamples[{index}]")
        path = require_string(row["path"], f"triggerSamples[{index}].path")
        require(row["laneName"] == LANE_NAME, f"triggerSamples[{index}].laneName changed")
        if row["triggersLane"]:
            glob = require_string(row["matchedGlob"], f"triggerSamples[{index}].matchedGlob")
            require(glob in REQUIRED_PATH_FILTERS, f"triggerSamples[{index}] matchedGlob is not a required filter")
            require(path_matches_glob(path, glob), f"triggerSamples[{index}] does not match its glob")
        else:
            require(row["matchedGlob"] is None, f"triggerSamples[{index}] negative sample must not name a glob")
            matches = [glob for glob in REQUIRED_PATH_FILTERS if path_matches_glob(path, glob)]
            require(not matches, f"triggerSamples[{index}] negative sample unexpectedly matches: {matches}")


def validate_non_claims(non_claims: Any) -> None:
    require(isinstance(non_claims, list), "nonClaims must be a list")
    joined = "\n".join(require_string(item, f"nonClaims[{index}]") for index, item in enumerate(non_claims))
    for token in ("rendering", "shaping", "scaler", "GPU"):
        require(token in joined, f"nonClaims must explicitly mention no {token} support")
    forbidden_positive = ["target-supported", "current-supported", "support complete", "complete support"]
    for token in forbidden_positive:
        require(token not in joined.lower(), f"nonClaims contains positive support wording: {token}")


def validate_tickets(root: Path, tickets: Any) -> None:
    require(isinstance(tickets, list), "tickets must be a list")
    require([row.get("id") for row in tickets if isinstance(row, dict)] == ["KFONT-M0-001", "KFONT-M0-002"], "tickets changed")
    for index, row in enumerate(tickets):
        require(isinstance(row, dict), f"tickets[{index}] must be an object")
        require_keys(row, ["id", "path", "classification"], f"tickets[{index}]")
        require_existing_path(root, row["path"], f"tickets[{index}].path")
        require(row["classification"] == "tracked-gap", f"tickets[{index}] must remain tracked-gap")


def validate_evidence(root: Path, evidence: dict[str, Any]) -> None:
    require_keys(evidence, TOP_LEVEL_KEYS, EVIDENCE_PATH)
    require(evidence["schemaVersion"] == 1, "schemaVersion changed")
    require(evidence["laneId"] == "KFONT-M0-001-002-ci-foundation", "laneId changed")
    require(evidence["laneName"] == LANE_NAME, "laneName changed")
    validate_tickets(root, evidence["tickets"])
    require(evidence["dashboardClassification"] == "tracked-gap", "dashboardClassification must remain tracked-gap")
    require_false(evidence["claimPromotionAllowed"], "claimPromotionAllowed")
    require(evidence["supportClaim"] == "validation-infrastructure-only", "supportClaim must remain validation-only")
    validate_headless_policy(evidence["headlessPolicy"])
    validate_gradle_tasks(evidence["gradleTasks"])
    validate_workflow_evidence(evidence["workflow"])
    validate_missing_module_policy(evidence["missingModulePolicy"])
    validate_trigger_samples(evidence["triggerSamples"])
    validate_non_claims(evidence["nonClaims"])
    require(
        evidence["validationCommands"]
        == [
            "rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_ci.py",
            "rtk python3 scripts/validate_pure_kotlin_text_ci.py",
            "rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_boundary_contracts.py",
            "rtk python3 scripts/validate_pure_kotlin_text_boundary_contracts.py",
            "rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_claim_dashboard.py",
            "rtk python3 scripts/validate_pure_kotlin_text_claim_dashboard.py",
            "rtk git diff --check",
        ],
        "validationCommands changed",
    )
    validate_project_modules(root, evidence)


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    try:
        evidence = load_evidence(root)
        workflow_text = load_workflow_text(root)
        validate_evidence(root, evidence)
        validate_workflow_text(workflow_text)
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print(f"{LANE_NAME}: validation OK")
    print("tasks: " + " ".join(EXPECTED_TASKS))
    print("module diagnostics: []")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
