#!/usr/bin/env python3
"""Generate and validate FOR-311 M92 RC headless Kadre dependency policy."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-311"
SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-m92-rc-headless-kadre-dependency-policy-guard-ticket"

M92_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/m92-kadre-runtime-rc"
EVIDENCE = M92_DIR / "evidence.json"
CLASSIFICATION = M92_DIR / "telemetry-classification.json"
CLOSEOUT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-02-rc-kadre-runtime-closeout.md"
PM_SCRIPT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-02-rc-pm-demo-script.md"
VALIDATOR = PROJECT_ROOT / "scripts/validate_mep_rc_runtime.py"
PM_BUNDLE_GENERATOR = PROJECT_ROOT / "build.gradle.kts"
ARTIFACT = M92_DIR / "m92-rc-headless-kadre-dependency-policy-for311.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-311-m92-rc-headless-kadre-dependency-policy.md"

DECISION_APPLIED = "M92_RC_HEADLESS_KADRE_DEPENDENCY_POLICY_GUARD_APPLIED"
DECISION_UNSAFE = "M92_RC_HEADLESS_KADRE_DIRECT_TASK_REQUIRED"
DECISION_AMBIGUOUS = "M92_RC_HEADLESS_KADRE_POLICY_AMBIGUOUS"

DIRECT_KADRE_TASK = "rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive"
CHECKED_IN_RUNTIME_VALIDATOR = "rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive"
RC_VALIDATOR = "python3 scripts/validate_mep_rc_runtime.py ."
PM_BUNDLE = "rtk ./gradlew --no-daemon pipelinePmBundle"
NATIVE_DEMO = "rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeInteractive"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-311 validation failed: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def read_text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing text file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return " ".join(text.split())


def require_snippet(path: Path, snippet: str) -> None:
    text = read_text(path)
    if snippet not in text and compact(snippet) not in compact(text):
        fail(f"{rel(path)} missing `{snippet}`")


def required_section(text: str, start: str, end: str | None = None) -> str:
    if start not in text:
        fail(f"missing section marker `{start}`")
    section = text.split(start, 1)[1]
    if end is not None:
        if end not in section:
            fail(f"missing section end marker `{end}` after `{start}`")
        section = section.split(end, 1)[0]
    return section


def validate_m92_evidence(evidence: dict[str, Any]) -> dict[str, Any]:
    if evidence.get("status") != "pass":
        fail("M92 evidence must remain pass")
    if evidence.get("claimLevel") != "rc-kadre-runtime-product-like-evidence-with-observed-derived-not-observable-telemetry":
        fail("M92 claimLevel changed")

    commands = evidence.get("commands")
    if not isinstance(commands, dict):
        fail("M92 evidence must contain commands")
    native = commands.get("singleNativeRcDemo")
    headless = commands.get("headlessEvidence")
    optional = commands.get("optionalDirectRuntimeRefresh")
    validator = commands.get("validator")
    bundle = commands.get("pmBundle")
    if not all(isinstance(item, dict) for item in [native, headless, optional, validator, bundle]):
        fail("M92 commands must include native, headless, optional refresh, validator, and PM bundle entries")

    if native.get("command") != NATIVE_DEMO or native.get("nativeWindow") is not True or native.get("ciGate") is not False:
        fail("single native RC demo command boundary changed")
    if headless.get("command") != CHECKED_IN_RUNTIME_VALIDATOR:
        fail("headless evidence must use validateMepNextRuntimeInteractive")
    if headless.get("nativeWindow") is not False or headless.get("ciGate") is not True:
        fail("headless evidence must remain a native-window-free CI gate")
    if headless.get("usesKadreNativeSubmodule") is not False:
        fail("headless evidence must not require Kadre native submodule")
    if headless.get("validatesCheckedInArtifacts") is not True:
        fail("headless evidence must validate checked-in artifacts")
    if DIRECT_KADRE_TASK in str(headless.get("command")):
        fail("direct Kadre task must not be the headless evidence command")

    if optional.get("command") != DIRECT_KADRE_TASK:
        fail("optional direct runtime refresh command missing")
    if optional.get("ciGate") is not False:
        fail("optional direct runtime refresh must not be a CI gate")
    precondition = str(optional.get("submodulePrecondition", ""))
    if "external/poc-koreos" not in precondition and "org.graphiks.kadre" not in precondition:
        fail("optional direct runtime refresh must document Kadre provisioning")

    if validator.get("command") != RC_VALIDATOR or validator.get("ciGate") is not True:
        fail("RC validator command changed")
    if bundle.get("command") != PM_BUNDLE or bundle.get("ciGate") is not True:
        fail("PM bundle command changed")

    telemetry_summary = evidence.get("telemetrySummary")
    if not isinstance(telemetry_summary, dict):
        fail("M92 evidence must contain telemetrySummary")
    expected_counts = {
        "observedCount": 5,
        "derivedCount": 4,
        "notObservableCount": 4,
        "expectedUnsupportedCount": 2,
    }
    for field, expected in expected_counts.items():
        if telemetry_summary.get(field) != expected:
            fail(f"telemetrySummary.{field} expected {expected}, got {telemetry_summary.get(field)}")
    if telemetry_summary.get("releaseBlockingPerformanceGate") is not False:
        fail("M92 must not claim a release-blocking performance gate")
    if telemetry_summary.get("readinessDelta") != "0.00%":
        fail("M92 readiness delta must remain zero")

    return {
        "claimLevel": evidence["claimLevel"],
        "headlessEvidence": {
            "command": headless["command"],
            "ciGate": headless["ciGate"],
            "nativeWindow": headless["nativeWindow"],
            "usesKadreNativeSubmodule": headless["usesKadreNativeSubmodule"],
            "validatesCheckedInArtifacts": headless["validatesCheckedInArtifacts"],
        },
        "optionalDirectRuntimeRefresh": {
            "command": optional["command"],
            "ciGate": optional["ciGate"],
            "precondition": optional["submodulePrecondition"],
        },
        "singleNativeRcDemo": {
            "command": native["command"],
            "ciGate": native["ciGate"],
            "nativeWindow": native["nativeWindow"],
        },
        "telemetrySummary": {field: telemetry_summary[field] for field in expected_counts}
        | {
            "releaseBlockingPerformanceGate": telemetry_summary["releaseBlockingPerformanceGate"],
            "readinessDelta": telemetry_summary["readinessDelta"],
        },
    }


def validate_classification(classification: dict[str, Any]) -> dict[str, Any]:
    rows = classification.get("rows")
    if not isinstance(rows, list):
        fail("classification rows missing")
    counts: dict[str, int] = {}
    release_gated: list[str] = []
    for row in rows:
        if not isinstance(row, dict):
            fail("classification rows must be objects")
        kind = row.get("classification")
        if not isinstance(kind, str):
            fail("classification row missing class")
        counts[kind] = counts.get(kind, 0) + 1
        if row.get("releaseGate") is not False:
            release_gated.append(str(row.get("id")))
    expected = {
        "observed": 5,
        "observed-partial": 2,
        "derived": 4,
        "not-observable": 4,
        "expected-unsupported": 2,
    }
    if counts != expected:
        fail(f"classification counts changed: expected {expected}, got {counts}")
    if release_gated:
        fail(f"classification rows must not be release gated: {release_gated}")
    blockers = {item.get("id") for item in classification.get("blockers", []) if isinstance(item, dict)}
    for blocker in [
        "kadre-runtime.native-cache-counter-unavailable",
        "kadre-runtime.resource-lifetime-observation-partial",
    ]:
        if blocker not in blockers:
            fail(f"missing blocker {blocker}")
    return {"counts": counts, "blockers": sorted(blockers), "releaseGatedRows": release_gated}


def validate_reports() -> dict[str, str]:
    closeout = read_text(CLOSEOUT)
    pm_script = read_text(PM_SCRIPT)
    validator = read_text(VALIDATOR)
    pm_bundle_generator = read_text(PM_BUNDLE_GENERATOR)

    for snippet in [CHECKED_IN_RUNTIME_VALIDATOR, RC_VALIDATOR, "Optional/provisioned evidence refresh"]:
        require_snippet(CLOSEOUT, snippet)
    closeout_before_optional = closeout.split("Optional/provisioned evidence refresh:", 1)[0]
    if DIRECT_KADRE_TASK in closeout_before_optional:
        fail("direct Kadre task appears before optional/provisioned refresh in closeout")

    fallback = required_section(
        pm_script,
        "## Native-Unavailable Fallback",
        "Use the optional Kadre-provisioned refresh only",
    )
    if DIRECT_KADRE_TASK in fallback:
        fail("direct Kadre task appears in native-unavailable fallback")
    for snippet in [CHECKED_IN_RUNTIME_VALIDATOR, RC_VALIDATOR, "Optional Kadre-provisioned evidence refresh"]:
        require_snippet(PM_SCRIPT, snippet)

    for snippet in [
        "headless evidence must use the checked-in MEP-NEXT validator",
        "direct Kadre runtime refresh must not be a CI gate",
        "direct Kadre runtime task must not appear before optional/provisioned refresh in closeout",
        "Kadre runtime task must not be required in native-unavailable fallback",
    ]:
        if snippet not in validator:
            fail(f"{rel(VALIDATOR)} missing validator rule `{snippet}`")

    m90_manifest = required_section(
        pm_bundle_generator,
        '"m90RuntimeInteractive" to linkedMapOf<String, Any>(',
        '"runtimeCapabilities" to linkedMapOf(',
    )
    if f'"ciEvidenceCommand" to "{CHECKED_IN_RUNTIME_VALIDATOR}"' not in m90_manifest:
        fail("PM bundle M90 manifest must publish validateMepNextRuntimeInteractive as ciEvidenceCommand")
    if f'"optionalDirectRuntimeRefreshCommand" to "{DIRECT_KADRE_TASK}"' not in m90_manifest:
        fail("PM bundle M90 manifest must publish direct Kadre task only as optional refresh")
    ci_line = next((line for line in m90_manifest.splitlines() if '"ciEvidenceCommand"' in line), "")
    if ":kadre-runtime:pipelineMepNextRuntimeInteractive" in ci_line:
        fail("PM bundle M90 ciEvidenceCommand must not use the direct Kadre runtime task")

    return {
        rel(CLOSEOUT): "verified",
        rel(PM_SCRIPT): "verified",
        rel(VALIDATOR): "verified",
        rel(PM_BUNDLE_GENERATOR): "verified",
    }


def classify_policy_case(
    *,
    headless_command: str,
    direct_task_ci_gate: bool,
    direct_task_before_optional: bool,
    native_demo_ci_gate: bool,
) -> tuple[str, bool, str]:
    if native_demo_ci_gate:
        return ("forbidden", False, "Native window demo must remain opt-in and outside CI.")
    if direct_task_ci_gate:
        return ("forbidden", False, "Direct Kadre runtime refresh may resolve unpublished Kadre artifacts and must not be a CI gate.")
    if direct_task_before_optional:
        return ("forbidden", False, "Direct Kadre runtime task must appear only in optional/provisioned sections.")
    if headless_command != CHECKED_IN_RUNTIME_VALIDATOR:
        return ("ambiguous", False, "Headless evidence must name the checked-in MEP-NEXT runtime validator.")
    return ("allowed-guarded", True, "M92 required validation stays headless and checked-in; Kadre refresh remains optional/provisioned.")


def validate_policy_cases() -> list[dict[str, Any]]:
    cases = [
        {
            "name": "Current M92 required validation is allowed",
            "headless_command": CHECKED_IN_RUNTIME_VALIDATOR,
            "direct_task_ci_gate": False,
            "direct_task_before_optional": False,
            "native_demo_ci_gate": False,
            "expected": "allowed-guarded",
        },
        {
            "name": "Direct Kadre task as CI gate is forbidden",
            "headless_command": CHECKED_IN_RUNTIME_VALIDATOR,
            "direct_task_ci_gate": True,
            "direct_task_before_optional": False,
            "native_demo_ci_gate": False,
            "expected": "forbidden",
        },
        {
            "name": "Direct Kadre task before optional section is forbidden",
            "headless_command": CHECKED_IN_RUNTIME_VALIDATOR,
            "direct_task_ci_gate": False,
            "direct_task_before_optional": True,
            "native_demo_ci_gate": False,
            "expected": "forbidden",
        },
        {
            "name": "Native demo as CI gate is forbidden",
            "headless_command": CHECKED_IN_RUNTIME_VALIDATOR,
            "direct_task_ci_gate": False,
            "direct_task_before_optional": False,
            "native_demo_ci_gate": True,
            "expected": "forbidden",
        },
        {
            "name": "Unknown headless command is ambiguous",
            "headless_command": "python3 some-other-validator.py",
            "direct_task_ci_gate": False,
            "direct_task_before_optional": False,
            "native_demo_ci_gate": False,
            "expected": "ambiguous",
        },
    ]
    rows: list[dict[str, Any]] = []
    for case in cases:
        decision, allowed, reason = classify_policy_case(
            headless_command=case["headless_command"],
            direct_task_ci_gate=case["direct_task_ci_gate"],
            direct_task_before_optional=case["direct_task_before_optional"],
            native_demo_ci_gate=case["native_demo_ci_gate"],
        )
        if decision != case["expected"]:
            fail(f"policy case `{case['name']}` expected {case['expected']}, got {decision}")
        rows.append({"name": case["name"], "decision": decision, "allowed": allowed, "reason": reason})
    return rows


def validate_gate() -> dict[str, Any]:
    evidence = load_json(EVIDENCE)
    classification = load_json(CLASSIFICATION)
    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_APPLIED,
        "dependencyDecision": "KEEP_M92_RC_REQUIRED_VALIDATION_HEADLESS_AND_CHECKED_IN",
        "m92Evidence": validate_m92_evidence(evidence),
        "classification": validate_classification(classification),
        "reportChecks": validate_reports(),
        "policyCases": validate_policy_cases(),
        "alternateDecisions": {"unsafe": DECISION_UNSAFE, "ambiguous": DECISION_AMBIGUOUS},
    }


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    evidence = data["m92Evidence"]
    classification = data["classification"]
    policy_rows = "\n".join(
        "| {name} | `{decision}` | {allowed} | {reason} |".format(**row)
        for row in data["policyCases"]
    )
    counts_rows = "\n".join(
        f"| `{key}` | `{value}` |" for key, value in classification["counts"].items()
    )
    report = f"""# FOR-311 M92 RC Headless Kadre Dependency Policy

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-311 keeps M92 RC validation headless by making checked-in artifact
validators the required CI path and keeping direct Kadre runtime regeneration as
an optional/provisioned refresh.

No renderer, shader, runtime, Kadre source integration, Gradle substitution,
visual threshold, scene status, fallback, telemetry count, readiness score, or
native demo behavior changed.

## Required Headless Path

- Runtime evidence validator: `{evidence["headlessEvidence"]["command"]}`
- RC validator: `{RC_VALIDATOR}`
- PM bundle: `{PM_BUNDLE}`
- Headless uses Kadre native submodule: `{evidence["headlessEvidence"]["usesKadreNativeSubmodule"]}`
- Validates checked-in artifacts: `{evidence["headlessEvidence"]["validatesCheckedInArtifacts"]}`

## Optional Kadre Refresh

- Command: `{evidence["optionalDirectRuntimeRefresh"]["command"]}`
- CI gate: `{evidence["optionalDirectRuntimeRefresh"]["ciGate"]}`
- Precondition: {evidence["optionalDirectRuntimeRefresh"]["precondition"]}

## Native Demo Boundary

- Command: `{evidence["singleNativeRcDemo"]["command"]}`
- Native window: `{evidence["singleNativeRcDemo"]["nativeWindow"]}`
- CI gate: `{evidence["singleNativeRcDemo"]["ciGate"]}`

## Classification Preserved

| Classification | Count |
|---|---:|
{counts_rows}

Blockers preserved:

- `kadre-runtime.native-cache-counter-unavailable`
- `kadre-runtime.resource-lifetime-observation-partial`

Readiness delta: `{evidence["telemetrySummary"]["readinessDelta"]}`

Release-blocking performance gate: `{evidence["telemetrySummary"]["releaseBlockingPerformanceGate"]}`

## Policy Cases

| Case | Decision | Allowed | Reason |
|---|---|---:|---|
{policy_rows}

## Validation

- `rtk python3 scripts/validate_for311_m92_rc_headless_kadre_dependency_policy.py`
- `rtk python3 scripts/validate_mep_rc_runtime.py .`
- `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool {rel(ARTIFACT)}`
- `rtk python3 -m json.tool {rel(EVIDENCE)}`
- `rtk git diff --check origin/master...HEAD`
"""
    REPORT.write_text(report, encoding="utf-8")


def main() -> None:
    data = validate_gate()
    write_artifact(data)
    write_report(data)
    load_json(ARTIFACT)
    if DECISION_APPLIED not in read_text(REPORT):
        fail("report does not contain applied decision")
    print(f"{LINEAR_ID}: {DECISION_APPLIED}")
    print(f"artifact={rel(ARTIFACT)}")
    print(f"report={rel(REPORT)}")


if __name__ == "__main__":
    main()
