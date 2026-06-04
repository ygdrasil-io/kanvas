#!/usr/bin/env python3
"""Audit generated PM bundle runtime headless manifest policy for FOR-312."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-312"
SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-pm-bundle-runtime-headless-manifest-audit-ticket"

PM_BUNDLE_ROOT = PROJECT_ROOT / "build/reports/wgsl-pipeline-pm-bundle"
MANIFEST = PM_BUNDLE_ROOT / "manifest.json"
README = PM_BUNDLE_ROOT / "README.md"
ARTIFACT = PROJECT_ROOT / "reports/wgsl-pipeline/pm-bundle-runtime-headless-manifest-for312.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-312-pm-bundle-runtime-headless-manifest.md"

DECISION_APPLIED = "PM_BUNDLE_RUNTIME_HEADLESS_MANIFEST_AUDIT_APPLIED"
DECISION_UNSAFE = "PM_BUNDLE_RUNTIME_HEADLESS_MANIFEST_UNSAFE_DIRECT_KADRE_CI"
DECISION_AMBIGUOUS = "PM_BUNDLE_RUNTIME_HEADLESS_MANIFEST_AMBIGUOUS"

CHECKED_IN_RUNTIME_VALIDATOR = "rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive"
DIRECT_KADRE_REFRESH = "rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive"
NATIVE_DEMO = "rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeInteractive"
NATIVE_BENCHMARK = (
    "rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeBenchmark "
    "-PkadreMepNextFrames=300 -PkadreMepNextWarmupFrames=120"
)
RC_VALIDATOR = "python3 scripts/validate_mep_rc_runtime.py ."


def fail(message: str) -> None:
    raise SystemExit(f"FOR-312 validation failed: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}; run `rtk ./gradlew --no-daemon pipelinePmBundle` first")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def read_text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing text file: {rel(path)}; run `rtk ./gradlew --no-daemon pipelinePmBundle` first")
    return path.read_text(encoding="utf-8")


def validate_m90(manifest: dict[str, Any]) -> dict[str, Any]:
    m90 = manifest.get("m90RuntimeInteractive")
    if not isinstance(m90, dict):
        fail("manifest missing m90RuntimeInteractive")
    expected = {
        "claimLevel": "bounded-kadre-runtime-interactive-evidence",
        "status": "pass",
        "demoCommand": NATIVE_DEMO,
        "benchmarkCommand": NATIVE_BENCHMARK,
        "ciEvidenceCommand": CHECKED_IN_RUNTIME_VALIDATOR,
        "optionalDirectRuntimeRefreshCommand": DIRECT_KADRE_REFRESH,
    }
    for field, value in expected.items():
        if m90.get(field) != value:
            fail(f"m90RuntimeInteractive.{field} expected {value!r}, got {m90.get(field)!r}")
    precondition = str(m90.get("optionalDirectRuntimeRefreshPrecondition", ""))
    if "external/poc-koreos" not in precondition and "org.graphiks.kadre" not in precondition:
        fail("m90 optional direct refresh must document Kadre provisioning")
    if DIRECT_KADRE_REFRESH in str(m90.get("ciEvidenceCommand", "")):
        fail("m90 ciEvidenceCommand must not use direct Kadre runtime refresh")
    non_claims = "\n".join(str(item) for item in m90.get("nonClaims", []))
    for snippet in [
        "Native demo and benchmark remain opt-in",
        "No real OS/window-manager event injection in CI.",
        "No release-grade frame.kadre-windowed FPS gate.",
        "No broad observed WebGPU cache telemetry.",
    ]:
        if snippet not in non_claims:
            fail(f"m90 nonClaims missing `{snippet}`")
    return {
        "claimLevel": m90["claimLevel"],
        "status": m90["status"],
        "demoCommand": m90["demoCommand"],
        "benchmarkCommand": m90["benchmarkCommand"],
        "ciEvidenceCommand": m90["ciEvidenceCommand"],
        "optionalDirectRuntimeRefreshCommand": m90["optionalDirectRuntimeRefreshCommand"],
        "optionalDirectRuntimeRefreshPrecondition": precondition,
        "nonClaims": m90.get("nonClaims", []),
    }


def validate_m92(manifest: dict[str, Any]) -> dict[str, Any]:
    m92 = manifest.get("m92KadreRuntimeRc")
    if not isinstance(m92, dict):
        fail("manifest missing m92KadreRuntimeRc")
    expected = {
        "claimLevel": "rc-kadre-runtime-product-like-evidence-with-observed-derived-not-observable-telemetry",
        "status": "pass",
        "singleNativeRcDemoCommand": NATIVE_DEMO,
        "headlessValidatorCommand": RC_VALIDATOR,
        "readinessAfter": 67.75,
        "readinessDelta": 0.0,
        "nativeDemoOptIn": True,
        "releaseBlockingPerformanceGate": False,
    }
    for field, value in expected.items():
        if m92.get(field) != value:
            fail(f"m92KadreRuntimeRc.{field} expected {value!r}, got {m92.get(field)!r}")
    classes = m92.get("telemetryClasses")
    expected_classes = ["observed", "observed-partial", "derived", "expected-unsupported", "not-observable"]
    if classes != expected_classes:
        fail(f"m92 telemetryClasses expected {expected_classes}, got {classes}")
    notice = str(m92.get("notice", ""))
    for snippet in [
        "one opt-in native command",
        "without claiming broad cache counters",
        "real OS event injection in CI",
        "release-grade window FPS",
        "new native execution",
    ]:
        if snippet not in notice:
            fail(f"m92 notice missing `{snippet}`")
    return {
        "claimLevel": m92["claimLevel"],
        "status": m92["status"],
        "singleNativeRcDemoCommand": m92["singleNativeRcDemoCommand"],
        "headlessValidatorCommand": m92["headlessValidatorCommand"],
        "readinessAfter": m92["readinessAfter"],
        "readinessDelta": m92["readinessDelta"],
        "nativeDemoOptIn": m92["nativeDemoOptIn"],
        "releaseBlockingPerformanceGate": m92["releaseBlockingPerformanceGate"],
        "telemetryClasses": classes,
    }


def validate_readme(readme: str) -> dict[str, str]:
    snippets = {
        "m90OptIn": "M90 runtime counters live in `manifest.json` under `m90RuntimeInteractive`; native demo and benchmark commands remain opt-in because they open local Kadre windows.",
        "m92Headless": "M92 runtime evidence lives in `manifest.json` under `m92KadreRuntimeRc`; the native RC command is opt-in, and headless validation does not open a Kadre window.",
    }
    for key, snippet in snippets.items():
        if snippet not in readme:
            fail(f"PM bundle README missing {key} wording")
    return snippets


def validate_manifest_root(manifest: dict[str, Any]) -> dict[str, Any]:
    if manifest.get("generatedBy") != "pipelinePmBundle":
        fail("manifest must be generated by pipelinePmBundle")
    if manifest.get("generationCommand") != "rtk ./gradlew --no-daemon pipelinePmBundle":
        fail("unexpected PM bundle generation command")
    return {
        "generatedBy": manifest["generatedBy"],
        "generationCommand": manifest["generationCommand"],
        "commit": manifest.get("commit"),
        "dashboardEntry": manifest.get("dashboardEntry"),
    }


def validate_audit() -> dict[str, Any]:
    manifest = load_json(MANIFEST)
    readme = read_text(README)
    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_APPLIED,
        "sourceManifest": rel(MANIFEST),
        "sourceReadme": rel(README),
        "manifest": validate_manifest_root(manifest),
        "m90RuntimeInteractive": validate_m90(manifest),
        "m92KadreRuntimeRc": validate_m92(manifest),
        "readmeWording": validate_readme(readme),
        "alternateDecisions": {"unsafe": DECISION_UNSAFE, "ambiguous": DECISION_AMBIGUOUS},
    }


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    m90 = data["m90RuntimeInteractive"]
    m92 = data["m92KadreRuntimeRc"]
    report = f"""# FOR-312 PM Bundle Runtime Headless Manifest Audit

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-312 audits the generated `pipelinePmBundle` manifest and proves the PM
package boundary carries the FOR-311 headless runtime policy.

No renderer, runtime, shader, Gradle task dependency, Kadre substitution, visual
threshold, scene status, fallback, telemetry count, readiness score, or native
demo behavior changed.

## Manifest Source

- Manifest: `{data["sourceManifest"]}`
- README: `{data["sourceReadme"]}`
- Generated by: `{data["manifest"]["generatedBy"]}`
- Generation command: `{data["manifest"]["generationCommand"]}`
- Source commit: `{data["manifest"]["commit"]}`

## M90 Runtime Manifest

- CI evidence command: `{m90["ciEvidenceCommand"]}`
- Optional direct refresh: `{m90["optionalDirectRuntimeRefreshCommand"]}`
- Optional refresh precondition: {m90["optionalDirectRuntimeRefreshPrecondition"]}
- Native demo command: `{m90["demoCommand"]}`
- Native benchmark command: `{m90["benchmarkCommand"]}`

## M92 Runtime RC Manifest

- Headless validator: `{m92["headlessValidatorCommand"]}`
- Single native RC demo command: `{m92["singleNativeRcDemoCommand"]}`
- Native demo opt-in: `{m92["nativeDemoOptIn"]}`
- Release-blocking performance gate: `{m92["releaseBlockingPerformanceGate"]}`
- Readiness after: `{m92["readinessAfter"]}`
- Readiness delta: `{m92["readinessDelta"]}`
- Telemetry classes: `{", ".join(m92["telemetryClasses"])}`

## Validation

- `rtk ./gradlew --no-daemon pipelinePmBundle`
- `rtk python3 scripts/validate_for312_pm_bundle_runtime_headless_manifest.py`
- `rtk python3 -m json.tool {rel(ARTIFACT)}`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
"""
    REPORT.write_text(report, encoding="utf-8")


def main() -> None:
    data = validate_audit()
    write_artifact(data)
    write_report(data)
    load_json(ARTIFACT)
    if DECISION_APPLIED not in read_text(REPORT):
        fail("report missing applied decision")
    print(f"{LINEAR_ID}: {DECISION_APPLIED}")
    print(f"artifact={rel(ARTIFACT)}")
    print(f"report={rel(REPORT)}")


if __name__ == "__main__":
    main()
