#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


POLICY_PATH = "reports/wgsl-pipeline/performance/kan-020-slice-performance-minimum.json"
REPORT_PATH = "reports/wgsl-pipeline/2026-06-10-kan-020-performance-proof-minimum.md"
SPEC_PATH = ".upstream/specs/skia-like-realtime/04-performance-tiering-and-release-gates.md"


def fail(message: str):
    raise SystemExit(f"KAN-020 performance proof minimum validation failed: {message}")


def require(condition: bool, message: str):
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def require_file(root: Path, relative_path: str):
    require((root / relative_path).is_file(), f"missing referenced artifact: {relative_path}")


def require_contains(root: Path, relative_path: str, snippets: list[str]):
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    for snippet in snippets:
        require(snippet in text, f"{relative_path} missing snippet: {snippet}")


def require_source(policy: dict[str, Any], source_id: str) -> dict[str, Any]:
    for row in policy.get("sourceEvidence", []):
        if isinstance(row, dict) and row.get("id") == source_id:
            return row
    fail(f"missing sourceEvidence row {source_id}")


def require_class(policy: dict[str, Any], class_id: str, may_count: bool):
    classes = policy.get("minimumContract", {}).get("allowedEvidenceClasses", [])
    for row in classes:
        if isinstance(row, dict) and row.get("class") == class_id:
            require(row.get("mayCountAsMeasuredGate") is may_count, f"{class_id} measured-gate flag changed")
            requirements = row.get("requires")
            require(isinstance(requirements, list) and requirements, f"{class_id} requirements missing")
            return row
    fail(f"missing allowed evidence class {class_id}")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    policy = load_json(root, POLICY_PATH)
    require(policy.get("schemaVersion") == 1, "policy schemaVersion changed")
    require(policy.get("packId") == "kan-020-slice-performance-proof-minimum-v1", "policy packId changed")
    require(policy.get("ticket") == "KAN-020", "policy ticket changed")
    require(policy.get("status") == "pass", "policy status must remain pass")
    require(policy.get("claimLevel") == "performance-proof-policy", "policy claimLevel changed")
    require(policy.get("releaseBlockingChange") is False, "KAN-020 must not create a release-blocking change")
    require(policy.get("readinessDelta") == 0, "KAN-020 must not move readiness")

    contract = policy.get("minimumContract")
    require(isinstance(contract, dict), "minimumContract missing")
    required_fields = set(contract.get("requiredFields", []))
    for field in (
        "sliceId",
        "sliceType",
        "performanceClass",
        "sourceEvidence",
        "releaseBlocking",
        "countedAsMeasuredGate",
        "rationale",
        "nonClaims",
    ):
        require(field in required_fields, f"minimumContract required field missing: {field}")

    require_class(policy, "release-blocking-measured-gate", True)
    require_class(policy, "measured-candidate", False)
    require_class(policy, "reporting-only", False)
    require_class(policy, "derived-ledger-reporting", False)
    require_class(policy, "non-gating-refusal", False)

    forbidden = "\n".join(contract.get("forbidden", []))
    for snippet in (
        "counting estimated metrics as measured",
        "counting missing metrics as measured",
        "counting derived selected-scene cache ledgers as observed WebGPU runtime cache telemetry",
        "adding a new release-blocking threshold from KAN-020",
    ):
        require(snippet in forbidden, f"forbidden promotion missing: {snippet}")

    slice_types = {row.get("sliceType"): row for row in policy.get("sliceTypeMinimums", []) if isinstance(row, dict)}
    for slice_type in (
        "bounded-rendering-support",
        "expected-unsupported-refusal",
        "runtime-frame",
        "resource-cache",
        "release-readiness",
    ):
        require(slice_type in slice_types, f"missing slice type minimum: {slice_type}")

    m59 = load_json(root, "reports/wgsl-pipeline/performance/m59-performance-release-gate.json")
    m59_policy = m59.get("policy", {})
    require(m59_policy.get("releaseBlocking") is True, "M59 selected gate must remain release-blocking")
    require(m59_policy.get("missingOrEstimatedStatus") == "release-blocking-failure-for-selected-rows", "M59 missing/estimated policy changed")
    require(m59_policy.get("minimumSampleCount") == 30, "M59 minimum sample count changed")
    require(len(m59.get("selectedRows", [])) == 7, "M59 selected row count changed")
    m59_source = require_source(policy, "m59-selected-release-gates")
    require(m59_source.get("performanceClass") == "release-blocking-measured-gate", "M59 policy class changed")
    require(m59_source.get("releaseBlocking") is True, "M59 source must stay releaseBlocking=true")
    require(m59_source.get("countedAsMeasuredGate") is True, "M59 source must count as measured gate")
    require(m59_source.get("selectedRows") == len(m59.get("selectedRows", [])), "M59 selected row count mismatch")

    m67_frame = load_json(root, "reports/wgsl-pipeline/performance/m67-performance-tiering/m67-frame-gate-candidate.json")
    m67_counters = m67_frame.get("counters", {})
    require(m67_frame.get("releaseBlocking") is False, "M67 frame gate must stay non-release-blocking")
    require(m67_frame.get("policy", {}).get("gatePhase") == "candidate", "M67 gate phase changed")
    require(m67_counters.get("measuredRows") == 3, "M67 measured row count changed")
    require(m67_counters.get("passRows") == 1, "M67 pass row count changed")
    require(m67_counters.get("warnRows") == 2, "M67 warn row count changed")
    require(m67_counters.get("failRows") == 0, "M67 fail row count changed")
    for row in m67_frame.get("rows", []):
        require(row.get("payloadStatus") == "measured", "M67 candidate row must keep measured payload")
        require(row.get("releaseBlocking") is False, "M67 candidate rows must stay non-release-blocking")
        require(row.get("sampleCount", 0) >= 120, "M67 candidate row sample count below policy")
    m67_source = require_source(policy, "m67-headless-frame-candidate")
    require(m67_source.get("performanceClass") == "measured-candidate", "M67 source class changed")
    require(m67_source.get("countedAsMeasuredGate") is False, "M67 must not count as release measured gate")

    m67_family = load_json(root, "reports/wgsl-pipeline/performance/m67-performance-tiering/m67-family-budgets.json")
    m67_family_counters = m67_family.get("counters", {})
    require(m67_family_counters.get("families") == 7, "M67 family count changed")
    require(m67_family_counters.get("measuredFamilies") == 1, "M67 measured family count changed")
    require(m67_family_counters.get("reportingOnlyFamilies") == 6, "M67 reporting-only family count changed")
    reporting_only = [row for row in m67_family.get("families", []) if isinstance(row, dict) and row.get("status") == "reporting-only"]
    require(len(reporting_only) == 6, "M67 reporting-only rows changed")
    require(all(row.get("releaseBlocking") is False and row.get("measured") is False for row in reporting_only), "M67 reporting-only rows must stay non-measured and non-blocking")
    m67_family_source = require_source(policy, "m67-family-reporting-only")
    require(m67_family_source.get("performanceClass") == "reporting-only", "M67 family source class changed")
    require(m67_family_source.get("reportingOnlyFamilies") == 6, "M67 family policy row count mismatch")

    m67_negative = load_json(root, "reports/wgsl-pipeline/performance/m67-performance-tiering-negative/m67-negative-fixture.json")
    require(m67_negative.get("expectedStatus") == "quarantine", "M67 negative fixture status changed")
    require(m67_negative.get("deterministic") is True, "M67 negative fixture must stay deterministic")
    require(m67_negative.get("opaqueFailure") is False, "M67 negative fixture must not become opaque")

    m84 = load_json(root, "reports/wgsl-pipeline/m84-native-frame-timing/evidence.json")
    measured_payload = m84.get("measuredPayload", {})
    require(m84.get("lane") == "frame.kadre-windowed", "M84 lane changed")
    require(m84.get("releaseBlocking") is False, "M84 must stay non-release-blocking")
    require(m84.get("countedAsMeasuredGate") is False, "M84 must not count as measured gate")
    require(m84.get("gatePhase") == "candidate-reporting-only", "M84 gate phase changed")
    require(measured_payload.get("status") == "measured", "M84 payload must remain measured")
    require(measured_payload.get("measuredSampleCount") == 120, "M84 measured sample count changed")
    require(measured_payload.get("nativeTimingClaim") == "present-call-duration-only", "M84 native timing claim changed")
    m84_source = require_source(policy, "m84-native-frame-candidate-reporting")
    require(m84_source.get("performanceClass") == "measured-candidate", "M84 source class changed")
    require(m84_source.get("countedAsMeasuredGate") is False, "M84 policy must not count as measured gate")

    m84_negative = load_json(root, "reports/wgsl-pipeline/m84-native-frame-timing/negative-fixture.json")
    require(m84_negative.get("status") == "expected-fail", "M84 negative fixture status changed")
    require(m84_negative.get("mutatesBaseline") is False, "M84 negative fixture must not mutate baselines")

    m85 = load_json(root, "reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json")
    require(m85.get("counterSource") == "derived-selected-scene-resource-ledger", "M85 counter source changed")
    require(m85.get("observedRuntimeCounters") is False, "M85 must not become observed runtime counters")
    require(m85.get("countedAsCacheReadinessGate") is False, "M85 must not count as cache readiness gate")
    require(m85.get("readinessDelta") == 0, "M85 readiness delta changed")
    telemetry = m85.get("perFrameResourceTelemetry", {})
    require(telemetry.get("frameCount") == 180, "M85 frame count changed")
    require(telemetry.get("pipelineCacheHits") == 179, "M85 cache hit count changed")
    require(telemetry.get("pipelineCacheMisses") == 1, "M85 cache miss count changed")
    require(telemetry.get("invalidResourceReuseCount") == 0, "M85 invalid resource reuse changed")
    m85_source = require_source(policy, "m85-resource-cache-derived-ledger")
    require(m85_source.get("performanceClass") == "derived-ledger-reporting", "M85 source class changed")
    require(m85_source.get("observedRuntimeCounters") is False, "M85 source observed flag changed")
    require(m85_source.get("countedAsCacheReadinessGate") is False, "M85 source gate flag changed")

    for row in policy.get("validationRows", []):
        require(row.get("status") == "pass", f"validation row failed: {row.get('id')}")
    for artifact in policy.get("artifactPaths", []):
        require_file(root, artifact)

    non_claims = "\n".join(policy.get("nonClaims", []))
    for snippet in (
        "does not create a new release-blocking performance threshold",
        "does not promote frame.kadre-windowed to release-grade FPS",
        "does not count derived M85 cache ledgers as observed runtime cache telemetry",
        "does not count estimated or missing metrics as measured evidence",
    ):
        require(snippet in non_claims, f"policy non-claim missing: {snippet}")

    require_contains(root, REPORT_PATH, [
        "KAN-020 defines the minimum performance proof",
        "Estimated, missing, derived, or diagnostic-only metrics must not be counted",
        "`reports/wgsl-pipeline/performance/kan-020-slice-performance-minimum.json`",
        "Do not add a new release-blocking threshold from KAN-020.",
        "Performance class: measured-candidate",
    ])
    require_contains(root, SPEC_PATH, [
        "Estimated or missing payloads are not measured evidence.",
        "No metric may skip directly from estimated to release-blocking.",
        "M85 does not make arbitrary scene caches release-ready.",
    ])

    print("KAN-020 performance proof minimum validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
