#!/usr/bin/env python3
import json
import sys
from pathlib import Path


EXPECTED_ISSUES = {
    "FOR-204",
    "FOR-205",
    "FOR-206",
    "FOR-207",
    "FOR-208",
    "FOR-209",
    "FOR-210",
    "FOR-211",
    "FOR-212",
    "FOR-213",
    "FOR-217",
    "FOR-219",
    "FOR-220",
}


def fail(message: str):
    raise SystemExit(f"RC Kadre runtime validation failed: {message}")


def require(condition: bool, message: str):
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str):
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def require_file(root: Path, relative_path: str):
    require((root / relative_path).is_file(), f"missing referenced file: {relative_path}")


def load_text(root: Path, relative_path: str):
    path = root / relative_path
    require(path.is_file(), f"missing text file: {relative_path}")
    return path.read_text(encoding="utf-8")


def rows_by_id(classification: dict) -> dict:
    rows = classification.get("rows", [])
    require(isinstance(rows, list) and rows, "classification rows missing")
    return {row.get("id"): row for row in rows}


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    evidence = load_json(root, "reports/wgsl-pipeline/m92-kadre-runtime-rc/evidence.json")
    classification = load_json(root, "reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json")
    m90 = load_json(root, "reports/wgsl-pipeline/m90-runtime-interactive/evidence.json")
    switching = load_json(root, "reports/wgsl-pipeline/m90-runtime-interactive/scene-switching.json")
    telemetry = load_json(root, "reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json")

    require(evidence.get("packId") == "m92-kadre-runtime-rc-closeout-v1", "unexpected evidence packId")
    require(evidence.get("status") == "pass", "evidence status must be pass")
    require(set(evidence.get("scopeIds", [])) == EXPECTED_ISSUES, "scope ids changed")
    require(
        evidence.get("claimLevel")
        == "rc-kadre-runtime-product-like-evidence-with-observed-derived-not-observable-telemetry",
        "unexpected claimLevel",
    )

    native = evidence.get("commands", {}).get("singleNativeRcDemo", {})
    require(native.get("optIn") is True, "native RC demo must be opt-in")
    require(native.get("nativeWindow") is True, "native RC demo must be marked as windowed")
    require(native.get("ciGate") is False, "native RC demo must not be a CI gate")
    require("external/poc-koreos" in native.get("submodulePrecondition", ""), "Kadre submodule precondition missing")

    headless = evidence.get("commands", {}).get("headlessEvidence", {})
    require(
        headless.get("command") == "rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive",
        "headless evidence must use the checked-in MEP-NEXT validator",
    )
    require(headless.get("nativeWindow") is False, "headless evidence must not open native windows")
    require(headless.get("usesKadreNativeSubmodule") is False, "headless evidence must not require Kadre submodule")
    require(headless.get("ciGate") is True, "headless evidence validator must remain a CI gate")
    require(
        headless.get("validatesCheckedInArtifacts") is True,
        "headless evidence must validate checked-in artifacts",
    )
    require(
        ":kadre-runtime:pipelineMepNextRuntimeInteractive" not in headless.get("command", ""),
        "direct Kadre runtime task must not be listed as mandatory headless evidence",
    )

    optional_refresh = evidence.get("commands", {}).get("optionalDirectRuntimeRefresh", {})
    require(
        optional_refresh.get("command") == "rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive",
        "optional direct runtime refresh command missing",
    )
    require(optional_refresh.get("ciGate") is False, "direct Kadre runtime refresh must not be a CI gate")
    require(
        "external/poc-koreos" in optional_refresh.get("submodulePrecondition", "")
        or "org.graphiks.kadre" in optional_refresh.get("submodulePrecondition", ""),
        "direct Kadre runtime refresh must document Kadre source/local artifact precondition",
    )

    product = evidence.get("productRuntimeEvidence", {})
    require(product.get("sceneSwitching", {}).get("renderableSceneCount", 0) >= 3, "scene switching needs three renderable scenes")
    require(product.get("sceneSwitching", {}).get("unsupportedSceneCount", 0) >= 1, "scene switching needs an unsupported scene")
    require(product.get("inputControls", {}).get("realOsEventInjectionClaimed") is False, "real OS injection must not be claimed")
    require(product.get("modeSplit", {}).get("headlessCiSeparate") is True, "headless CI split missing")

    telemetry_summary = evidence.get("telemetrySummary", {})
    require(telemetry_summary.get("readinessDelta") == "0.00%", "readiness delta must remain zero")
    require(telemetry_summary.get("releaseBlockingPerformanceGate") is False, "performance gate must remain non-release-blocking")

    row_map = rows_by_id(classification)
    required_classifications = {
        "native-frame-timing": "observed",
        "scene-switch-events": "observed",
        "shader-module-creates-selected-route": "observed-partial",
        "pipeline-cache-hits-misses-selected-ledger": "derived",
        "broad-webgpu-cache-hit-callbacks": "not-observable",
        "native-resource-free-callbacks": "not-observable",
        "nested-rrect-clip-scene": "expected-unsupported",
        "real-os-event-injection": "expected-unsupported",
    }
    for row_id, expected in required_classifications.items():
        require(row_id in row_map, f"missing classification row: {row_id}")
        require(row_map[row_id].get("classification") == expected, f"{row_id} classification changed")
        require(row_map[row_id].get("releaseGate") is False, f"{row_id} must not be release-gated")

    blockers = {blocker.get("id") for blocker in classification.get("blockers", [])}
    require("kadre-runtime.native-cache-counter-unavailable" in blockers, "native cache blocker missing")
    require("kadre-runtime.resource-lifetime-observation-partial" in blockers, "resource lifetime blocker missing")
    require(
        classification.get("boundedGrowthPolicy", {}).get("nativeLogPolicy")
        == "No append-only native telemetry log is introduced in this slice.",
        "bounded growth policy changed",
    )

    require(m90.get("modes", {}).get("ciEvidence", {}).get("nativeWindow") is False, "M90 CI evidence must remain headless")
    require(switching.get("renderableSceneCount", 0) >= 3, "M90 switching source lost renderable scenes")
    require(
        telemetry.get("sourceClassification", {}).get("cacheHitsMisses") == "derived",
        "M90 cache hit/miss source must remain derived",
    )

    for path in evidence.get("sourceEvidence", []):
        require_file(root, path)
    for path in evidence.get("artifacts", []):
        require_file(root, path)

    closeout = load_text(root, "reports/wgsl-pipeline/2026-06-02-rc-kadre-runtime-closeout.md")
    pm_script = load_text(root, "reports/wgsl-pipeline/2026-06-02-rc-pm-demo-script.md")

    closeout_flat = " ".join(closeout.split())
    pm_script_flat = " ".join(pm_script.split())

    require("Readiness delta: `0.00%`" in closeout, "closeout must keep readiness delta at 0.00%")
    require("WGSL remains the shader implementation target" in closeout, "closeout must keep WGSL target wording")
    require("not-observable" in closeout, "closeout must document not-observable telemetry")
    require("not treated as solved by derived M85 ledgers" in closeout_flat, "closeout must not treat blockers as solved")
    require("Single opt-in native RC command" in closeout, "closeout must document the opt-in native command")
    require("not wired into CI" in closeout_flat, "closeout must keep native command out of CI")
    require(
        "rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive" in closeout,
        "closeout must document checked-in MEP-NEXT runtime validation",
    )
    require(
        ":kadre-runtime:pipelineMepNextRuntimeInteractive"
        not in closeout.split("Optional/provisioned evidence refresh:")[0],
        "direct Kadre runtime task must not appear before optional/provisioned refresh in closeout",
    )

    require("Do not claim release-grade `frame.kadre-windowed` FPS" in pm_script_flat, "PM script must forbid release-grade FPS claims")
    require("Do not claim broad observed WebGPU cache hit/miss telemetry" in pm_script_flat, "PM script must forbid broad cache claims")
    require("Do not claim dynamic SkSL compilation" in pm_script_flat, "PM script must forbid dynamic SkSL claims")
    require("WGSL remains the shader target" in pm_script_flat, "PM script must keep WGSL target wording")
    require("Native-Unavailable Fallback" in pm_script, "PM script must include native-unavailable fallback")
    require(":kadre-runtime:pipelineMepNextRuntimeInteractive" not in pm_script.split("Optional Kadre-provisioned evidence refresh:")[0], "Kadre runtime task must not be required before optional provisioning")
    native_unavailable_fallback = pm_script.split("## Native-Unavailable Fallback", 1)[1].split(
        "Use the optional Kadre-provisioned refresh only",
        1,
    )[0]
    require(
        ":kadre-runtime:pipelineMepNextRuntimeInteractive" not in native_unavailable_fallback,
        "Kadre runtime task must not be required in native-unavailable fallback",
    )

    non_claims = "\n".join(evidence.get("nonClaims", []))
    require("No new native window execution" in non_claims, "native execution non-claim missing")
    require("WGSL remains the shader implementation target" in non_claims, "WGSL target non-claim missing")
    require("No broad observed WebGPU cache" in non_claims, "cache telemetry non-claim missing")

    print("RC Kadre runtime evidence validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
