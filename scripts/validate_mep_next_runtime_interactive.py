#!/usr/bin/env python3
import json
import sys
from pathlib import Path


def fail(message: str):
    raise SystemExit(f"MEP-NEXT runtime validation failed: {message}")


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
    require((root / relative_path).is_file(), f"missing referenced artifact: {relative_path}")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    evidence = load_json(root, "reports/wgsl-pipeline/m90-runtime-interactive/evidence.json")
    telemetry = load_json(root, "reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json")
    switching = load_json(root, "reports/wgsl-pipeline/m90-runtime-interactive/scene-switching.json")

    require(evidence.get("packId") == "mep-next-runtime-interactive-kadre-v1", "unexpected evidence packId")
    require(evidence.get("status") == "pass", "evidence status must be pass")
    require(evidence.get("claimLevel") == "bounded-kadre-runtime-interactive-evidence", "unexpected claimLevel")
    require(set(evidence.get("scopeIds", [])) == {"FOR-193", "FOR-194", "FOR-195", "FOR-196"}, "scope ids changed")

    modes = evidence.get("modes", {})
    require(modes.get("demo", {}).get("optIn") is True, "native demo must remain opt-in")
    require(modes.get("benchmark", {}).get("releaseBlocking") is False, "benchmark must not become release-blocking")
    require(modes.get("ciEvidence", {}).get("nativeWindow") is False, "CI evidence must stay headless")
    require(
        modes.get("ciEvidence", {}).get("command") == "rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive",
        "CI evidence must use the checked-in validator",
    )
    require(modes.get("ciEvidence", {}).get("usesKadreNativeSubmodule") is False, "root validation must not require Kadre submodule")
    require(modes.get("ciEvidence", {}).get("validatesCheckedInArtifacts") is True, "CI evidence must validate checked-in artifacts")
    optional_refresh = modes.get("optionalDirectRuntimeRefresh", {})
    require(
        optional_refresh.get("command")
        == "rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive",
        "optional direct runtime refresh command missing",
    )
    require(optional_refresh.get("ciGate") is False, "optional direct refresh must not be a CI gate")
    require(
        "external/poc-koreos" in optional_refresh.get("submodulePrecondition", "")
        or "org.graphiks.kadre" in optional_refresh.get("submodulePrecondition", ""),
        "optional direct refresh must document Kadre provisioning",
    )

    durable = evidence.get("durableLoop", {})
    require(durable.get("autonomousFrameClock") is True, "autonomous frame clock must be explicit")
    require(durable.get("doesNotRequirePointerWakeups") is True, "pointer wakeup non-dependency must be explicit")

    scene_switching = evidence.get("sceneSwitching", {})
    require(scene_switching.get("renderableSceneCount", 0) >= 3, "at least three renderable scenes are required")
    require(scene_switching.get("unsupportedSceneCount", 0) >= 1, "at least one unsupported scene is required")
    require(switching.get("packId") == "mep-next-scene-switching-v1", "scene-switching standalone packId changed")
    require(switching.get("renderableSceneCount", 0) >= 3, "standalone switching renderable count too low")

    input_controls = evidence.get("inputControls", {})
    telemetry_counts = input_controls.get("telemetry", {})
    require(input_controls.get("visibleStateChange") is True, "input controls must produce visible state change")
    require(telemetry_counts.get("pointerEventCount", 0) > 0, "pointer telemetry missing")
    require(telemetry_counts.get("keyboardEventCount", 0) > 0, "keyboard telemetry missing")
    require(input_controls.get("realOsEventInjectionClaimed") is False, "real OS event injection must not be claimed")

    resources = evidence.get("resourceCacheTelemetry", {})
    require(resources.get("status") == "reporting-only", "resource/cache telemetry must remain reporting-only")
    live_counters = resources.get("liveRouteCounters", {})
    require(live_counters.get("classification") == "observed-partial", "live route counters must be classified observed-partial")
    require(live_counters.get("missingCounterReason") == "mep-next.native-cache-counter-unavailable", "missing counter reason changed")
    require(telemetry.get("sourceClassification", {}).get("cacheHitsMisses") == "derived", "cache hit/miss source classification changed")

    validation_rows = evidence.get("validationRows", [])
    require(validation_rows, "validationRows missing")
    failed = [row.get("id", "<unknown>") for row in validation_rows if row.get("status") != "pass"]
    require(not failed, "validation rows failed: " + ", ".join(failed))

    non_claims = "\n".join(evidence.get("nonClaims", []))
    require("broad SkCanvas/display-list replay" in non_claims, "broad replay non-claim missing")
    require("real OS/window-manager" in non_claims, "real OS event non-claim missing")
    require("release-grade FPS" in non_claims, "release-grade FPS non-claim missing")
    require("broad WebGPU cache hits/misses" in non_claims, "broad cache telemetry non-claim missing")

    for source in evidence.get("sourceEvidence", []):
        require_file(root, source)
    for artifact in evidence.get("artifactPaths", []):
        require_file(root, artifact)

    print("MEP-NEXT runtime interactive evidence validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
