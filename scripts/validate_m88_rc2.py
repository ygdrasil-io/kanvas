#!/usr/bin/env python3
import json
import sys
from pathlib import Path


def load_json(root: Path, relative_path: str):
    path = root / relative_path
    if not path.is_file():
        fail(f"missing required JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def fail(message: str):
    raise SystemExit(f"M88 RC2 validation failed: {message}")


def require(condition: bool, message: str):
    if not condition:
        fail(message)


def require_value(document, path: str, expected):
    current = document
    for part in path.split("."):
        if not isinstance(current, dict) or part not in current:
            fail(f"missing field {path}")
        current = current[part]
    require(current == expected, f"{path} expected {expected!r}, got {current!r}")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    evidence_path = "reports/wgsl-pipeline/m88-realtime-rc2/rc2-evidence.json"
    evidence = load_json(root, evidence_path)
    standalone_api_surface = load_json(root, "reports/wgsl-pipeline/m88-realtime-rc2/api-surface.json")
    standalone_gate_freeze = load_json(root, "reports/wgsl-pipeline/m88-realtime-rc2/gate-freeze.json")
    standalone_support = load_json(root, "reports/wgsl-pipeline/m88-realtime-rc2/support-refusal-matrix.json")

    require_value(evidence, "packId", "m88-realtime-renderer-rc2-v1")
    require_value(evidence, "status", "pass")
    require_value(evidence, "claimLevel", "realtime-renderer-rc2-freeze-package")
    require_value(evidence, "readinessBefore", 67.75)
    require_value(evidence, "readinessAfter", 67.75)
    require_value(evidence, "readinessDelta", 0.0)

    required_scope_ids = {"FOR-104", "FOR-174", "FOR-175", "FOR-176", "FOR-177", "FOR-178"}
    require(set(evidence.get("scopeIds", [])) == required_scope_ids, "scopeIds do not match M88 scope")

    artifacts = evidence.get("artifactPaths")
    require(isinstance(artifacts, list) and artifacts, "artifactPaths must be a non-empty list")
    for artifact in artifacts:
        require((root / artifact).is_file(), f"artifact path is missing: {artifact}")

    validation_rows = evidence.get("validationRows")
    require(isinstance(validation_rows, list) and validation_rows, "validationRows must be present")
    failed_rows = [row.get("id", "<unknown>") for row in validation_rows if row.get("status") != "pass"]
    require(not failed_rows, f"validationRows not passing: {', '.join(failed_rows)}")

    support = evidence.get("supportRefusalMatrix", {})
    require(support == standalone_support, "support-refusal-matrix.json diverges from rc2-evidence.json")
    counters = support.get("dashboardCounters", {})
    require(counters.get("totalRows", 0) > 0, "dashboardCounters.totalRows must be positive")
    require(counters.get("failRows") == 0, "dashboardCounters.failRows must stay zero")
    require(counters.get("trackedGapRows") == 0, "dashboardCounters.trackedGapRows must stay zero")
    require(counters.get("passRows") == 21, "dashboardCounters.passRows must stay frozen at 21")
    require(counters.get("expectedUnsupportedRows") == 5, "dashboardCounters.expectedUnsupportedRows must stay frozen at 5")

    api_surface = evidence.get("apiSurface", {})
    require(api_surface == standalone_api_surface, "api-surface.json diverges from rc2-evidence.json")
    shader_target = api_surface.get("shaderTarget", {})
    require(shader_target.get("implementationLanguage") == "WGSL", "M88 shader target must be WGSL")
    require("no dynamic SkSL compilation" in shader_target.get("skslScope", ""), "SkSL scope must be compatibility-only")
    require("parser-validated WGSL" in shader_target.get("runtimeEffectSupport", ""), "runtime-effect support must name parser-validated WGSL")

    categories = support.get("categories", [])
    required_categories = {"supported", "expected-unsupported", "dependency-gated", "implementation-gap", "reporting-only"}
    seen_categories = {category.get("category") for category in categories}
    require(seen_categories == required_categories, "support/refusal categories changed")
    for category in categories:
        require(category.get("pmMeaning"), f"missing pmMeaning for {category.get('category')}")
        require(category.get("nextAction"), f"missing nextAction for {category.get('category')}")

    stable_refusals = support.get("stableRefusals", [])
    sksl_refusal = next((row for row in stable_refusals if row.get("fallbackReason") == "runtime-effect.arbitrary-sksl-unsupported"), None)
    require(sksl_refusal is not None, "SkSL compatibility refusal row is missing")
    require("WGSL" in sksl_refusal.get("summary", ""), "SkSL refusal summary must point back to WGSL")
    require(sksl_refusal.get("pmImpact") == "high", "SkSL refusal PM impact must remain high")

    triage = support.get("pmTriage", [])
    triage_classifications = {row.get("classification") for row in triage}
    require(required_categories - {"supported"} <= triage_classifications, "PM triage must cover refusal/reporting categories")

    gate_freeze = evidence.get("gateFreeze", {})
    require(gate_freeze == standalone_gate_freeze, "gate-freeze.json diverges from rc2-evidence.json")
    required_gates = gate_freeze.get("requiredCorrectnessGates", [])
    require(any(gate.get("name") == "pipelinePmBundle" and gate.get("phase") == "blocking" for gate in required_gates), "pipelinePmBundle must remain a blocking correctness gate")
    kadre_generation_gate = next((gate for gate in required_gates if gate.get("name") == ":kadre-runtime:pipelineM88ReleaseCandidate2"), None)
    require(kadre_generation_gate is not None, "Kadre runtime source generation gate is missing")
    require(kadre_generation_gate.get("phase") == "source-generation-local", "Kadre runtime source generation must stay local/non-CI")

    pm_package = evidence.get("pmPackage", {})
    require(pm_package.get("generationCommand") == "rtk ./gradlew --no-daemon pipelinePmBundle", "PM bundle generation must stay headless")
    require(pm_package.get("headlessCiRequiresKadreSubmodule") is False, "Headless PM package must not require Kadre submodule")
    require("external/poc-koreos" in pm_package.get("nativeKadreDemoSetup", ""), "Native Kadre demo setup command is missing")

    non_claims = "\n".join(evidence.get("nonClaims", []))
    require("does not add broad Skia parity" in non_claims, "broad Skia parity non-claim is missing")
    require("does not promote frame.kadre-windowed" in non_claims, "native frame timing non-claim is missing")
    require("does not claim observed WebGPU runtime cache telemetry" in non_claims, "runtime cache telemetry non-claim is missing")
    require("does not claim window-surface screenshot/readback" in non_claims, "window readback non-claim is missing")
    require("WGSL remains the shader implementation target" in non_claims, "WGSL shader target non-claim is missing")

    for source in evidence.get("sourceEvidence", []):
        require((root / source).is_file(), f"source evidence is missing: {source}")

    m84 = load_json(root, "reports/wgsl-pipeline/m84-native-frame-timing/evidence.json")
    require_value(m84, "packId", "m84-native-frame-timing-candidate-v1")
    require_value(m84, "lane", "frame.kadre-windowed")
    require_value(m84, "gatePhase", "candidate-reporting-only")
    require_value(m84, "releaseBlocking", False)
    require_value(m84, "countedAsMeasuredGate", False)

    m85 = load_json(root, "reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json")
    require_value(m85, "packId", "m85-resource-lifetime-cache-hardening-v1")
    require_value(m85, "status", "pass")
    require_value(m85, "lane", "frame.kadre-windowed")
    require_value(m85, "observedRuntimeCounters", False)
    require_value(m85, "countedAsCacheReadinessGate", False)

    m86 = load_json(root, "reports/wgsl-pipeline/m86-fidelity-burndown/evidence.json")
    require_value(m86, "counters.rankedCandidates", 19)
    require_value(m86, "counters.skiaComparableSupportRows", 6)
    require_value(m86, "dashboardGateExpectation.trackedGap", 0)
    require_value(m86, "dashboardGateExpectation.unexpectedFail", 0)

    m87 = load_json(root, "reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.json")
    require_value(m87, "packId", "m87-runtime-effect-live-editing-v1")
    require_value(m87, "status", "pass")
    require_value(m87, "liveRuntimeTelemetry.pipelineKeyStableAcrossUniformEdits", True)
    require_value(m87, "liveRuntimeTelemetry.actualNativeWindowRun", False)

    print("M88 RC2 evidence validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
