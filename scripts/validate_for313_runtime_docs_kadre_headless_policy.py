#!/usr/bin/env python3
"""Audit M90 runtime docs for the Kadre headless validation policy."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-313"
SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-runtime-docs-kadre-headless-policy-audit-ticket"

M90_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-02-mep-next-runtime-interactive.md"
M90_PM_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/m90-runtime-interactive/pm-report.md"
M90_EVIDENCE = PROJECT_ROOT / "reports/wgsl-pipeline/m90-runtime-interactive/evidence.json"
M90_CLOSEOUT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-02-mep-next-closeout.md"
M90_GENERATOR = PROJECT_ROOT / "kadre-runtime/src/main/kotlin/org/skia/kadre/runtime/MepNextRuntimeInteractive.kt"
ARTIFACT = PROJECT_ROOT / "reports/wgsl-pipeline/runtime-docs-kadre-headless-policy-for313.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-313-runtime-docs-kadre-headless-policy.md"

DECISION_APPLIED = "M90_RUNTIME_DOCS_KADRE_HEADLESS_POLICY_AUDIT_APPLIED"
DECISION_UNSAFE = "M90_RUNTIME_DOCS_DIRECT_KADRE_TASK_REQUIRED"
DECISION_AMBIGUOUS = "M90_RUNTIME_DOCS_KADRE_HEADLESS_POLICY_AMBIGUOUS"

CHECKED_IN_VALIDATOR = "rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive"
CHECKED_IN_VALIDATOR_NO_RTK = "./gradlew --no-daemon validateMepNextRuntimeInteractive"
DIRECT_KADRE_REFRESH = "rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive"
DIRECT_KADRE_REFRESH_NO_RTK = "./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive"
SUBMODULE_PRECONDITION = "external/poc-koreos"
LOCAL_ARTIFACT_PRECONDITION = "org.graphiks.kadre"


def fail(message: str) -> None:
    raise SystemExit(f"{LINEAR_ID} validation failed: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def read_text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def compact(text: str) -> str:
    return " ".join(text.split())


def require_snippet(path: Path, text: str, snippet: str) -> None:
    if snippet not in text and compact(snippet) not in compact(text):
        fail(f"{rel(path)} missing `{snippet}`")


def section_after(text: str, marker: str) -> str:
    if marker not in text:
        fail(f"missing section marker `{marker}`")
    return text.split(marker, 1)[1]


def fenced_block_after(text: str, marker: str) -> str:
    section = section_after(text, marker)
    if "```" not in section:
        fail(f"missing fenced block after `{marker}`")
    after_open = section.split("```", 1)[1]
    if "```" not in after_open:
        fail(f"unterminated fenced block after `{marker}`")
    return after_open.split("```", 1)[0]


def direct_refreshes_are_optional(path: Path, text: str) -> list[str]:
    direct_forms = [DIRECT_KADRE_REFRESH, DIRECT_KADRE_REFRESH_NO_RTK]
    direct_lines = [
        line.strip()
        for line in text.splitlines()
        if any(command in line for command in direct_forms)
    ]
    if not direct_lines:
        fail(f"{rel(path)} must still document the optional direct Kadre refresh")

    for line in direct_lines:
        lowered = line.lower()
        if "ci evidence" in lowered or "required" in lowered:
            fail(f"{rel(path)} marks direct Kadre refresh as required: {line}")
        if "|" in line and "optional" not in lowered:
            fail(f"{rel(path)} table row with direct Kadre refresh must be optional: {line}")

    text_after_first_direct = text.split(direct_lines[0], 1)[-1]
    context = "\n".join([text, text_after_first_direct])
    if SUBMODULE_PRECONDITION not in context and LOCAL_ARTIFACT_PRECONDITION not in context:
        fail(f"{rel(path)} must document Kadre provisioning for direct refresh")
    return direct_lines


def validate_m90_runtime_doc(path: Path) -> dict[str, Any]:
    text = read_text(path)
    require_snippet(path, text, "| checked-in validation | no | `" + CHECKED_IN_VALIDATOR + "`")
    require_snippet(path, text, "| optional direct refresh | no | `" + DIRECT_KADRE_REFRESH + "`")

    validation_block = fenced_block_after(text, "## Validation")
    if CHECKED_IN_VALIDATOR not in validation_block:
        fail(f"{rel(path)} validation block must use checked-in validator")
    if DIRECT_KADRE_REFRESH in validation_block:
        fail(f"{rel(path)} validation block must not require direct Kadre refresh")

    optional_section = section_after(text, "Optional/provisioned evidence refresh:")
    if DIRECT_KADRE_REFRESH not in optional_section:
        fail(f"{rel(path)} optional refresh section missing direct Kadre command")
    if SUBMODULE_PRECONDITION not in optional_section or LOCAL_ARTIFACT_PRECONDITION not in optional_section:
        fail(f"{rel(path)} optional refresh section must document both Kadre provisioning paths")

    direct_lines = direct_refreshes_are_optional(path, text)
    if "| CI evidence |" in text:
        fail(f"{rel(path)} must not retain the old `CI evidence` table row wording")
    return {
        "path": rel(path),
        "checkedInValidator": CHECKED_IN_VALIDATOR,
        "directRefreshLines": direct_lines,
        "optionalProvisioning": [SUBMODULE_PRECONDITION, LOCAL_ARTIFACT_PRECONDITION],
    }


def validate_closeout(path: Path) -> dict[str, Any]:
    text = read_text(path)
    headless_block = fenced_block_after(text, "Headless gates:")
    if CHECKED_IN_VALIDATOR_NO_RTK not in headless_block:
        fail(f"{rel(path)} headless gates must include checked-in validator")
    if DIRECT_KADRE_REFRESH_NO_RTK in headless_block or DIRECT_KADRE_REFRESH in headless_block:
        fail(f"{rel(path)} headless gates must not require direct Kadre refresh")

    optional_section = section_after(text, "Optional/provisioned Kadre runtime refresh:")
    if DIRECT_KADRE_REFRESH_NO_RTK not in optional_section:
        fail(f"{rel(path)} optional refresh section missing direct Kadre command")
    if SUBMODULE_PRECONDITION not in optional_section or LOCAL_ARTIFACT_PRECONDITION not in optional_section:
        fail(f"{rel(path)} optional refresh section must document Kadre provisioning")

    return {
        "path": rel(path),
        "headlessValidator": CHECKED_IN_VALIDATOR_NO_RTK,
        "optionalDirectRefresh": DIRECT_KADRE_REFRESH_NO_RTK,
        "optionalProvisioning": [SUBMODULE_PRECONDITION, LOCAL_ARTIFACT_PRECONDITION],
    }


def validate_evidence_json(path: Path) -> dict[str, Any]:
    evidence = load_json(path)
    modes = evidence.get("modes")
    if not isinstance(modes, dict):
        fail(f"{rel(path)} missing modes object")

    ci_evidence = modes.get("ciEvidence")
    optional_refresh = modes.get("optionalDirectRuntimeRefresh")
    if not isinstance(ci_evidence, dict) or not isinstance(optional_refresh, dict):
        fail(f"{rel(path)} must split ciEvidence and optionalDirectRuntimeRefresh")

    if ci_evidence.get("command") != CHECKED_IN_VALIDATOR:
        fail(f"{rel(path)} ciEvidence.command must use checked-in validator")
    if ci_evidence.get("nativeWindow") is not False or ci_evidence.get("optIn") is not False:
        fail(f"{rel(path)} ciEvidence must remain headless and non-opt-in")
    if ci_evidence.get("usesKadreNativeSubmodule") is not False:
        fail(f"{rel(path)} ciEvidence must not require Kadre submodule")
    if ci_evidence.get("validatesCheckedInArtifacts") is not True:
        fail(f"{rel(path)} ciEvidence must validate checked-in artifacts")

    if optional_refresh.get("command") != DIRECT_KADRE_REFRESH:
        fail(f"{rel(path)} optional direct refresh command missing")
    if optional_refresh.get("ciGate") is not False:
        fail(f"{rel(path)} optional direct refresh must not be a CI gate")
    precondition = str(optional_refresh.get("submodulePrecondition", ""))
    if SUBMODULE_PRECONDITION not in precondition and LOCAL_ARTIFACT_PRECONDITION not in precondition:
        fail(f"{rel(path)} optional direct refresh must document Kadre provisioning")

    return {
        "path": rel(path),
        "ciEvidenceCommand": ci_evidence["command"],
        "validatesCheckedInArtifacts": ci_evidence["validatesCheckedInArtifacts"],
        "optionalDirectRefresh": optional_refresh["command"],
        "optionalRefreshCiGate": optional_refresh["ciGate"],
        "optionalProvisioning": [SUBMODULE_PRECONDITION, LOCAL_ARTIFACT_PRECONDITION],
    }


def validate_generator(path: Path) -> dict[str, Any]:
    text = read_text(path)
    for snippet in [
        'put("ciEvidence", buildJsonObject {',
        'put("command", "rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive")',
        'put("validatesCheckedInArtifacts", true)',
        'put("optionalDirectRuntimeRefresh", buildJsonObject {',
        'put("ciGate", false)',
        "| checked-in validation | no | `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`",
        "| optional direct refresh | no | `rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive`",
        "Optional/provisioned evidence refresh:",
        "git submodule update --init --recursive external/poc-koreos",
        "org.graphiks.kadre:*",
    ]:
        require_snippet(path, text, snippet)
    if "| CI evidence | no | `rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive`" in text:
        fail(f"{rel(path)} generator still emits the old CI evidence row")
    if 'put("ciEvidence", buildJsonObject {' in text:
        modes_section = text.split('put("ciEvidence", buildJsonObject {', 1)[1].split('put("optionalDirectRuntimeRefresh"', 1)[0]
        if DIRECT_KADRE_REFRESH in modes_section:
            fail(f"{rel(path)} modesJson still puts direct Kadre refresh under ciEvidence")
    return {
        "path": rel(path),
        "emitsCheckedInEvidenceJson": True,
        "emitsCheckedInValidator": True,
        "emitsOptionalDirectRefresh": True,
    }


def validate_audit() -> dict[str, Any]:
    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_APPLIED,
        "documents": [
            validate_m90_runtime_doc(M90_REPORT),
            validate_m90_runtime_doc(M90_PM_REPORT),
            validate_closeout(M90_CLOSEOUT),
        ],
        "evidenceJson": validate_evidence_json(M90_EVIDENCE),
        "generator": validate_generator(M90_GENERATOR),
        "policy": {
            "requiredHeadlessValidator": CHECKED_IN_VALIDATOR,
            "directKadreRefresh": DIRECT_KADRE_REFRESH,
            "directKadreRefreshGate": "optional/provisioned-only",
            "provisioningAccepted": [SUBMODULE_PRECONDITION, LOCAL_ARTIFACT_PRECONDITION],
        },
        "unchangedClaims": {
            "rendererChanged": False,
            "shaderChanged": False,
            "gradleDependencyChanged": False,
            "sceneScoreChanged": False,
            "readinessPercentChanged": False,
            "telemetryCountChanged": False,
        },
        "alternateDecisions": {
            "unsafe": DECISION_UNSAFE,
            "ambiguous": DECISION_AMBIGUOUS,
        },
    }


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    document_rows = "\n".join(
        f"- `{doc['path']}`: required `{doc.get('checkedInValidator', doc.get('headlessValidator'))}`; optional direct refresh preserved."
        for doc in data["documents"]
    )
    report = f"""# FOR-313 Runtime Docs Kadre Headless Policy Audit

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-313 audits the M90 / MEP-NEXT runtime documents and the Markdown generator
that owns them. Required checked-in validation is now documented as
`{CHECKED_IN_VALIDATOR}`, while the direct Kadre runtime task remains only an
optional/provisioned evidence refresh.

No renderer, shader, Gradle dependency, Kadre native behavior, scene score,
fallback, readiness percentage, or telemetry count changed.

## Checked Documents

{document_rows}

## Policy

- Required headless validator: `{data["policy"]["requiredHeadlessValidator"]}`
- Optional direct Kadre refresh: `{data["policy"]["directKadreRefresh"]}`
- Optional refresh gate: `{data["policy"]["directKadreRefreshGate"]}`
- Provisioning accepted: `{", ".join(data["policy"]["provisioningAccepted"])}`
- Generator: `{data["generator"]["path"]}`
- Evidence JSON: `{data["evidenceJson"]["path"]}`

## Validation

- `rtk python3 scripts/validate_for313_runtime_docs_kadre_headless_policy.py`
- `rtk python3 -m json.tool {rel(ARTIFACT)}`
- `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
"""
    REPORT.write_text(report, encoding="utf-8")


def main() -> int:
    data = validate_audit()
    write_artifact(data)
    write_report(data)
    json.loads(ARTIFACT.read_text(encoding="utf-8"))
    if DECISION_APPLIED not in REPORT.read_text(encoding="utf-8"):
        fail("report missing applied decision")
    print(f"{LINEAR_ID}: {DECISION_APPLIED}")
    print(f"artifact={rel(ARTIFACT)}")
    print(f"report={rel(REPORT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
