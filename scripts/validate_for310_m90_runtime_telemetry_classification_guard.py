#!/usr/bin/env python3
"""Generate and validate FOR-310 M90 runtime telemetry classification guard."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-310"
SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-m90-runtime-telemetry-classification-guard-ticket"

M90_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/m90-runtime-interactive"
EVIDENCE = M90_DIR / "evidence.json"
TELEMETRY_LIVE = M90_DIR / "telemetry-live.json"
SCENE_SWITCHING = M90_DIR / "scene-switching.json"
PM_REPORT = M90_DIR / "pm-report.md"
SUMMARY_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-02-mep-next-runtime-interactive.md"
SPEC_PM = PROJECT_ROOT / ".upstream/specs/skia-like-realtime/05-pm-demo-and-release-candidate.md"
SPEC_PERFORMANCE = PROJECT_ROOT / ".upstream/specs/skia-like-realtime/04-performance-tiering-and-release-gates.md"

ARTIFACT = M90_DIR / "m90-runtime-telemetry-classification-guard-for310.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-310-m90-runtime-telemetry-classification-guard.md"

DECISION_APPLIED = "M90_RUNTIME_TELEMETRY_CLASSIFICATION_GUARD_APPLIED"
DECISION_UNSAFE = "M90_RUNTIME_TELEMETRY_UNSAFE_OBSERVED_CLAIM_FOUND"
DECISION_AMBIGUOUS = "M90_RUNTIME_TELEMETRY_CLASSIFICATION_AMBIGUOUS"

EXPECTED_ISSUES = ["FOR-193", "FOR-194", "FOR-195", "FOR-196"]
EXPECTED_CLASSIFICATIONS = {
    "nativeFrameTiming": "observed",
    "nativeRouteAllocations": "observed-partial",
    "cacheHitsMisses": "derived",
    "resizeInvalidation": "deterministic-derived",
}
MISSING_COUNTER_REASON = "mep-next.native-cache-counter-unavailable"
REAL_OS_EVENT_REASON = "mep-next.real-os-event-injection-not-claimed"
EXPECTED_UNSUPPORTED_REASON = "mep-next.scene-switch.expected-unsupported"
NESTED_RRECT_FALLBACK = "nested-rrect-difference-clip"

REQUIRED_ARTIFACTS = [EVIDENCE, TELEMETRY_LIVE, SCENE_SWITCHING, PM_REPORT, SUMMARY_REPORT]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-310 validation failed: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def compact(text: str) -> str:
    return " ".join(text.split())


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def read_text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing report: {rel(path)}")
    return path.read_text(encoding="utf-8")


def require(data: dict[str, Any], field: str, expected: Any, *, source: Path) -> None:
    actual = data.get(field)
    if actual != expected:
        fail(f"{rel(source)} expected {field}={expected!r}, got {actual!r}")


def require_snippets(path: Path, snippets: list[str]) -> str:
    text = read_text(path)
    compact_text = compact(text)
    lower_text = compact_text.lower()
    for snippet in snippets:
        compact_snippet = compact(snippet)
        if (
            snippet not in text
            and compact_snippet not in compact_text
            and compact_snippet.lower() not in lower_text
        ):
            fail(f"{rel(path)} missing required snippet `{snippet}`")
    return "verified"


def validate_required_artifacts() -> dict[str, str]:
    summary: dict[str, str] = {}
    for path in REQUIRED_ARTIFACTS:
        if not path.is_file():
            fail(f"missing required M90 artifact: {rel(path)}")
        summary[rel(path)] = "present"
    return summary


def validate_source_reports() -> dict[str, str]:
    return {
        rel(SUMMARY_REPORT): require_snippets(
            SUMMARY_REPORT,
            [
                "bounded interactive Kadre runtime evidence",
                "observed-partial",
                "derived ledger",
                "No release-grade `frame.kadre-windowed` FPS gate",
                "No broad observed WebGPU cache telemetry claim",
            ],
        ),
        rel(PM_REPORT): require_snippets(
            PM_REPORT,
            [
                "bounded interactive Kadre runtime evidence",
                "Reporting-only native timing sample",
                "mep-next.native-cache-counter-unavailable",
                "mep-next.real-os-event-injection-not-claimed",
            ],
        ),
        rel(SPEC_PM): require_snippets(
            SPEC_PM,
            [
                "MEP-NEXT Runtime Interactive",
                "resource/cache counters classified as",
                "real OS event injection",
            ],
        ),
        rel(SPEC_PERFORMANCE): require_snippets(
            SPEC_PERFORMANCE,
            [
                "Telemetry Classification",
                "observed-partial",
                "derived",
                "candidate-reporting-only",
            ],
        ),
    }


def validate_modes(evidence: dict[str, Any]) -> dict[str, Any]:
    modes = evidence.get("modes")
    if not isinstance(modes, dict):
        fail(f"{rel(EVIDENCE)} must contain modes")

    demo = modes.get("demo")
    benchmark = modes.get("benchmark")
    ci_evidence = modes.get("ciEvidence")
    if not isinstance(demo, dict) or not isinstance(benchmark, dict) or not isinstance(ci_evidence, dict):
        fail("M90 modes must contain demo, benchmark, and ciEvidence objects")

    expected_demo_command = "rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeInteractive"
    expected_benchmark_command = (
        "rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeBenchmark "
        "-PkadreMepNextFrames=300 -PkadreMepNextWarmupFrames=120"
    )
    expected_ci_command = "rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive"

    if demo.get("nativeWindow") is not True or demo.get("optIn") is not True:
        fail("demo mode must remain native-window opt-in")
    if demo.get("opensLongWindowInCi") is not False:
        fail("demo mode must not open a long native window in CI")
    if demo.get("command") != expected_demo_command:
        fail("demo command changed unexpectedly")

    if benchmark.get("nativeWindow") is not True or benchmark.get("optIn") is not True:
        fail("benchmark mode must remain native-window opt-in")
    if benchmark.get("gatePhase") != "candidate-reporting-only":
        fail("benchmark gatePhase must remain candidate-reporting-only")
    if benchmark.get("releaseBlocking") is not False:
        fail("benchmark must remain releaseBlocking=false")
    if benchmark.get("command") != expected_benchmark_command:
        fail("benchmark command changed unexpectedly")

    if ci_evidence.get("nativeWindow") is not False or ci_evidence.get("optIn") is not False:
        fail("CI evidence must remain headless and non-opt-in")
    if ci_evidence.get("usesKadreNativeSubmodule") is not False:
        fail("CI evidence must not require the Kadre native submodule")
    if ci_evidence.get("command") != expected_ci_command:
        fail("CI evidence command changed unexpectedly")

    return {
        "demo": {
            "nativeWindow": demo["nativeWindow"],
            "optIn": demo["optIn"],
            "opensLongWindowInCi": demo["opensLongWindowInCi"],
            "command": demo["command"],
        },
        "benchmark": {
            "nativeWindow": benchmark["nativeWindow"],
            "optIn": benchmark["optIn"],
            "gatePhase": benchmark["gatePhase"],
            "releaseBlocking": benchmark["releaseBlocking"],
            "command": benchmark["command"],
        },
        "ciEvidence": {
            "nativeWindow": ci_evidence["nativeWindow"],
            "optIn": ci_evidence["optIn"],
            "usesKadreNativeSubmodule": ci_evidence["usesKadreNativeSubmodule"],
            "command": ci_evidence["command"],
        },
    }


def validate_claim_scope(evidence: dict[str, Any]) -> dict[str, Any]:
    require(evidence, "status", "pass", source=EVIDENCE)
    require(evidence, "claimLevel", "bounded-kadre-runtime-interactive-evidence", source=EVIDENCE)
    linear_issues = evidence.get("linearIssues")
    if linear_issues != EXPECTED_ISSUES:
        fail(f"{rel(EVIDENCE)} expected linearIssues={EXPECTED_ISSUES}, got {linear_issues}")
    return {
        "status": evidence["status"],
        "claimLevel": evidence["claimLevel"],
        "linearIssues": linear_issues,
    }


def telemetry_sources(telemetry: dict[str, Any]) -> dict[str, str]:
    classification = telemetry.get("sourceClassification")
    if not isinstance(classification, dict):
        fail(f"{rel(TELEMETRY_LIVE)} must contain sourceClassification")
    for key, expected in EXPECTED_CLASSIFICATIONS.items():
        actual = classification.get(key)
        if actual != expected:
            fail(f"{rel(TELEMETRY_LIVE)} expected sourceClassification.{key}={expected}, got {actual}")
    return {key: classification[key] for key in EXPECTED_CLASSIFICATIONS}


def validate_resource_policy(telemetry: dict[str, Any]) -> dict[str, Any]:
    resources = telemetry.get("resources")
    if not isinstance(resources, dict):
        fail(f"{rel(TELEMETRY_LIVE)} must contain resources")
    require(resources, "status", "reporting-only", source=TELEMETRY_LIVE)

    observed = resources.get("observedRuntimeTelemetry")
    derived = resources.get("derivedLedger")
    live = resources.get("liveRouteCounters")
    resize = resources.get("resizeSwitchHealth")
    if not isinstance(observed, dict) or not isinstance(derived, dict) or not isinstance(live, dict):
        fail("resources must contain observedRuntimeTelemetry, derivedLedger, and liveRouteCounters")
    if not isinstance(resize, dict):
        fail("resources must contain resizeSwitchHealth")

    require(observed, "source", "mep-next.cache-observed-partial-native-route", source=TELEMETRY_LIVE)
    limitations = observed.get("limitations")
    if not isinstance(limitations, list) or not limitations:
        fail("observedRuntimeTelemetry.limitations must document partial cache evidence")
    limitations_text = " ".join(str(item) for item in limitations)
    for snippet in ["not a cache-hit implementation", "does not expose WebGPU cache hit/miss callbacks"]:
        if snippet not in limitations_text:
            fail(f"observedRuntimeTelemetry.limitations missing `{snippet}`")

    require(derived, "source", "reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json", source=TELEMETRY_LIVE)
    for field in ["pipelineCacheHits", "pipelineCacheMisses", "resourceGenerationCount", "invalidResourceReuseCount"]:
        if not isinstance(derived.get(field), int):
            fail(f"derivedLedger.{field} must remain an integer counter")

    require(live, "classification", "observed-partial", source=TELEMETRY_LIVE)
    require(live, "missingCounterReason", MISSING_COUNTER_REASON, source=TELEMETRY_LIVE)
    if live.get("pipelineCacheHits") != 0 or live.get("pipelineCacheMisses") != 180:
        fail("liveRouteCounters must preserve the observed-partial native route cache counter boundary")
    if live.get("pipelineCacheHits") == derived.get("pipelineCacheHits"):
        fail("liveRouteCounters must not mirror the derived M85 cache-hit count as observed")

    if resize.get("boundedGrowth") is not True or resize.get("invalidResourceReuseCount") != 0:
        fail("resizeSwitchHealth must preserve bounded growth with no invalid resource reuse")

    return {
        "status": resources["status"],
        "observedRuntimeTelemetry": {
            "source": observed["source"],
            "shaderModuleCreates": observed.get("shaderModuleCreates"),
            "pipelineCreates": observed.get("pipelineCreates"),
            "limitationCount": len(limitations),
        },
        "derivedLedger": {
            "source": derived["source"],
            "pipelineCacheHits": derived["pipelineCacheHits"],
            "pipelineCacheMisses": derived["pipelineCacheMisses"],
            "resourceGenerationCount": derived["resourceGenerationCount"],
            "invalidResourceReuseCount": derived["invalidResourceReuseCount"],
        },
        "liveRouteCounters": {
            "classification": live["classification"],
            "missingCounterReason": live["missingCounterReason"],
            "pipelineCacheHits": live["pipelineCacheHits"],
            "pipelineCacheMisses": live["pipelineCacheMisses"],
            "shaderModuleCreates": live.get("shaderModuleCreates"),
            "pipelineCreates": live.get("pipelineCreates"),
        },
        "resizeSwitchHealth": {
            "switchCount": resize.get("switchCount"),
            "resizeReconfigureCount": resize.get("resizeReconfigureCount"),
            "invalidResourceReuseCount": resize["invalidResourceReuseCount"],
            "boundedGrowth": resize["boundedGrowth"],
        },
    }


def validate_input_policy(evidence: dict[str, Any], telemetry: dict[str, Any]) -> dict[str, Any]:
    evidence_input = evidence.get("inputControls")
    telemetry_input = telemetry.get("input")
    if not isinstance(evidence_input, dict) or not isinstance(telemetry_input, dict):
        fail("M90 evidence and telemetry must contain input sections")
    for source, payload in [(EVIDENCE, evidence_input), (TELEMETRY_LIVE, telemetry_input)]:
        if payload.get("realOsEventInjectionClaimed") is not False:
            fail(f"{rel(source)} must not claim real OS event injection")
        if payload.get("realOsEventInjectionReason") != REAL_OS_EVENT_REASON:
            fail(f"{rel(source)} must preserve {REAL_OS_EVENT_REASON}")
    return {
        "realOsEventInjectionClaimed": False,
        "realOsEventInjectionReason": REAL_OS_EVENT_REASON,
        "pointerEventCount": telemetry_input.get("pointerEventCount"),
        "keyboardEventCount": telemetry_input.get("keyboardEventCount"),
    }


def validate_scene_switching(scene_switching: dict[str, Any], telemetry: dict[str, Any]) -> dict[str, Any]:
    require(scene_switching, "status", "pass", source=SCENE_SWITCHING)
    require(scene_switching, "sceneCount", 5, source=SCENE_SWITCHING)
    require(scene_switching, "renderableSceneCount", 4, source=SCENE_SWITCHING)
    require(scene_switching, "unsupportedSceneCount", 1, source=SCENE_SWITCHING)
    require(scene_switching, "unsupportedFallbackReason", EXPECTED_UNSUPPORTED_REASON, source=SCENE_SWITCHING)

    candidates = scene_switching.get("candidateScenes")
    if not isinstance(candidates, list):
        fail(f"{rel(SCENE_SWITCHING)} must contain candidateScenes")
    unsupported = [
        item
        for item in candidates
        if isinstance(item, dict) and item.get("status") == "expected-unsupported"
    ]
    if len(unsupported) != 1:
        fail("scene switching must preserve exactly one expected-unsupported candidate")
    row = unsupported[0]
    if row.get("id") != "m73-nested-rrect-clip-refusal-v1":
        fail("the expected-unsupported scene must remain the nested-rrect refusal")
    if row.get("renderedByKadre") is not False:
        fail("nested-rrect refusal must not be rendered by Kadre")
    fallback_reasons = row.get("fallbackReasons")
    if fallback_reasons != [NESTED_RRECT_FALLBACK]:
        fail(f"nested-rrect refusal must preserve fallbackReasons=[{NESTED_RRECT_FALLBACK}]")

    telemetry_scene = telemetry.get("scene")
    if not isinstance(telemetry_scene, dict):
        fail(f"{rel(TELEMETRY_LIVE)} must contain scene")
    if telemetry_scene.get("fallbackReason") != NESTED_RRECT_FALLBACK:
        fail(f"{rel(TELEMETRY_LIVE)} must preserve {NESTED_RRECT_FALLBACK}")
    if telemetry_scene.get("switchCount") != 4:
        fail("telemetry scene switchCount must remain 4")

    return {
        "sceneCount": scene_switching["sceneCount"],
        "renderableSceneCount": scene_switching["renderableSceneCount"],
        "unsupportedSceneCount": scene_switching["unsupportedSceneCount"],
        "unsupportedFallbackReason": scene_switching["unsupportedFallbackReason"],
        "unsupportedCandidate": {
            "id": row["id"],
            "status": row["status"],
            "renderedByKadre": row["renderedByKadre"],
            "fallbackReasons": fallback_reasons,
        },
        "telemetryScene": {
            "switchCount": telemetry_scene["switchCount"],
            "fallbackReason": telemetry_scene["fallbackReason"],
        },
    }


def validate_performance_lane(evidence: dict[str, Any], telemetry: dict[str, Any]) -> dict[str, Any]:
    require(telemetry, "lane", "frame.kadre-windowed", source=TELEMETRY_LIVE)
    modes = evidence.get("modes")
    if not isinstance(modes, dict) or not isinstance(modes.get("benchmark"), dict):
        fail("evidence benchmark mode missing")
    benchmark = modes["benchmark"]
    if benchmark.get("gatePhase") != "candidate-reporting-only" or benchmark.get("releaseBlocking") is not False:
        fail("frame.kadre-windowed benchmark must remain reporting-only and non-blocking")
    for field in ["frameCount", "droppedFrameCount", "p50Ms", "p95Ms"]:
        if field not in telemetry:
            fail(f"{rel(TELEMETRY_LIVE)} missing {field}")
    return {
        "lane": telemetry["lane"],
        "gatePhase": benchmark["gatePhase"],
        "releaseBlocking": benchmark["releaseBlocking"],
        "frameCount": telemetry["frameCount"],
        "droppedFrameCount": telemetry["droppedFrameCount"],
        "p50Ms": telemetry["p50Ms"],
        "p95Ms": telemetry["p95Ms"],
    }


def classify_telemetry_case(
    *,
    cache_hits_claim: str,
    native_route_allocations: str,
    release_blocking: bool,
    native_window_required_in_ci: bool,
    real_os_event_injection_claimed: bool,
    unsupported_scene_boundary_preserved: bool,
) -> tuple[str, bool, str]:
    if native_window_required_in_ci:
        return ("forbidden", False, "CI evidence must stay headless and must not require a native window.")
    if release_blocking:
        return ("forbidden", False, "M90 frame.kadre-windowed timing remains candidate/reporting-only.")
    if real_os_event_injection_claimed:
        return ("forbidden", False, "M90 does not claim real OS/window-manager event injection.")
    if cache_hits_claim == "observed":
        return ("forbidden", False, "Cache hits/misses are derived from M85, not fully observed on the native route.")
    if native_route_allocations == "observed" and cache_hits_claim != "derived":
        return ("ambiguous", False, "Observed allocation/churn must not imply observed cache hit/miss telemetry.")
    if not unsupported_scene_boundary_preserved:
        return ("forbidden", False, "Scene switching must preserve the expected unsupported nested-rrect fallback.")
    return ("allowed-guarded", True, "M90 claims stay bounded by observed, observed-partial, derived, and unavailable sources.")


def validate_policy_cases() -> list[dict[str, Any]]:
    cases = [
        {
            "name": "Current M90 classification is allowed",
            "cache_hits_claim": "derived",
            "native_route_allocations": "observed-partial",
            "release_blocking": False,
            "native_window_required_in_ci": False,
            "real_os_event_injection_claimed": False,
            "unsupported_scene_boundary_preserved": True,
            "expected": "allowed-guarded",
        },
        {
            "name": "Derived cache hits claimed as observed is forbidden",
            "cache_hits_claim": "observed",
            "native_route_allocations": "observed-partial",
            "release_blocking": False,
            "native_window_required_in_ci": False,
            "real_os_event_injection_claimed": False,
            "unsupported_scene_boundary_preserved": True,
            "expected": "forbidden",
        },
        {
            "name": "Native window required in CI is forbidden",
            "cache_hits_claim": "derived",
            "native_route_allocations": "observed-partial",
            "release_blocking": False,
            "native_window_required_in_ci": True,
            "real_os_event_injection_claimed": False,
            "unsupported_scene_boundary_preserved": True,
            "expected": "forbidden",
        },
        {
            "name": "M90 reporting lane as release blocking is forbidden",
            "cache_hits_claim": "derived",
            "native_route_allocations": "observed-partial",
            "release_blocking": True,
            "native_window_required_in_ci": False,
            "real_os_event_injection_claimed": False,
            "unsupported_scene_boundary_preserved": True,
            "expected": "forbidden",
        },
        {
            "name": "Real OS event injection claim is forbidden",
            "cache_hits_claim": "derived",
            "native_route_allocations": "observed-partial",
            "release_blocking": False,
            "native_window_required_in_ci": False,
            "real_os_event_injection_claimed": True,
            "unsupported_scene_boundary_preserved": True,
            "expected": "forbidden",
        },
        {
            "name": "Dropping unsupported scene boundary is forbidden",
            "cache_hits_claim": "derived",
            "native_route_allocations": "observed-partial",
            "release_blocking": False,
            "native_window_required_in_ci": False,
            "real_os_event_injection_claimed": False,
            "unsupported_scene_boundary_preserved": False,
            "expected": "forbidden",
        },
    ]
    rows: list[dict[str, Any]] = []
    for case in cases:
        decision, allowed, reason = classify_telemetry_case(
            cache_hits_claim=case["cache_hits_claim"],
            native_route_allocations=case["native_route_allocations"],
            release_blocking=case["release_blocking"],
            native_window_required_in_ci=case["native_window_required_in_ci"],
            real_os_event_injection_claimed=case["real_os_event_injection_claimed"],
            unsupported_scene_boundary_preserved=case["unsupported_scene_boundary_preserved"],
        )
        if decision != case["expected"]:
            fail(f"policy case `{case['name']}` expected {case['expected']}, got {decision}")
        rows.append({"name": case["name"], "decision": decision, "allowed": allowed, "reason": reason})
    return rows


def validate_gate() -> dict[str, Any]:
    evidence = load_json(EVIDENCE)
    telemetry = load_json(TELEMETRY_LIVE)
    scene_switching = load_json(SCENE_SWITCHING)

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_APPLIED,
        "claimDecision": "KEEP_M90_RUNTIME_TELEMETRY_BOUNDED_AND_REPORTING_ONLY",
        "requiredArtifacts": validate_required_artifacts(),
        "sourceReports": validate_source_reports(),
        "claimScope": validate_claim_scope(evidence),
        "modes": validate_modes(evidence),
        "telemetrySourceClassification": telemetry_sources(telemetry),
        "resourcePolicy": validate_resource_policy(telemetry),
        "performanceLane": validate_performance_lane(evidence, telemetry),
        "inputPolicy": validate_input_policy(evidence, telemetry),
        "sceneSwitching": validate_scene_switching(scene_switching, telemetry),
        "policyCases": validate_policy_cases(),
        "validationPolicy": {
            "requiredHeadlessValidator": "rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive",
            "optionalDirectRuntimeRefresh": {
                "command": "rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive",
                "requiredForThisGuard": False,
                "reason": "The checked-in guard validates existing M90 evidence; direct regeneration may require Kadre local source substitution or unpublished artifacts.",
            },
        },
        "alternateDecisions": {"unsafe": DECISION_UNSAFE, "ambiguous": DECISION_AMBIGUOUS},
    }


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    classification_rows = "\n".join(
        f"| `{key}` | `{value}` |" for key, value in data["telemetrySourceClassification"].items()
    )
    policy_rows = "\n".join(
        "| {name} | `{decision}` | {allowed} | {reason} |".format(**row)
        for row in data["policyCases"]
    )
    modes = data["modes"]
    resource = data["resourcePolicy"]
    performance = data["performanceLane"]
    scene = data["sceneSwitching"]
    validation_policy = data["validationPolicy"]
    report = f"""# FOR-310 M90 Runtime Telemetry Classification Guard

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-310 applies a classification guard to the M90 / MEP-NEXT Runtime Interactive
evidence. It keeps M90 useful as bounded Kadre runtime evidence while rejecting
unsafe claims that derived cache counters, observed-partial allocation counters,
or reporting-only frame timing are release-grade observed telemetry.

No renderer, shader, runtime, Kadre native behavior, cache implementation,
timing, visual threshold, scene status, fallback, or readiness score changed.

## Claim Scope

- Status: `{data["claimScope"]["status"]}`
- Claim level: `{data["claimScope"]["claimLevel"]}`
- Linear scope: `{", ".join(data["claimScope"]["linearIssues"])}`
- Claim decision: `{data["claimDecision"]}`

## Mode Boundaries

| Mode | Native window | Opt-in | Release/CI boundary |
|---|---:|---:|---|
| demo | {modes["demo"]["nativeWindow"]} | {modes["demo"]["optIn"]} | opens long window in CI: `{modes["demo"]["opensLongWindowInCi"]}` |
| benchmark | {modes["benchmark"]["nativeWindow"]} | {modes["benchmark"]["optIn"]} | `{modes["benchmark"]["gatePhase"]}`, release blocking: `{modes["benchmark"]["releaseBlocking"]}` |
| CI evidence | {modes["ciEvidence"]["nativeWindow"]} | {modes["ciEvidence"]["optIn"]} | Kadre native submodule required: `{modes["ciEvidence"]["usesKadreNativeSubmodule"]}` |

## Telemetry Classification

| Counter family | Classification |
|---|---|
{classification_rows}

## Resource Policy

- Observed native route source: `{resource["observedRuntimeTelemetry"]["source"]}`
- Observed allocation/churn limitation count: `{resource["observedRuntimeTelemetry"]["limitationCount"]}`
- Derived ledger source: `{resource["derivedLedger"]["source"]}`
- Derived pipeline cache hits/misses: `{resource["derivedLedger"]["pipelineCacheHits"]}` / `{resource["derivedLedger"]["pipelineCacheMisses"]}`
- Live route classification: `{resource["liveRouteCounters"]["classification"]}`
- Missing broad cache-counter reason: `{resource["liveRouteCounters"]["missingCounterReason"]}`
- Live route pipeline cache hits/misses: `{resource["liveRouteCounters"]["pipelineCacheHits"]}` / `{resource["liveRouteCounters"]["pipelineCacheMisses"]}`
- Resize/switch bounded growth: `{resource["resizeSwitchHealth"]["boundedGrowth"]}`, invalid reuse: `{resource["resizeSwitchHealth"]["invalidResourceReuseCount"]}`

## Performance Lane

- Lane: `{performance["lane"]}`
- Gate phase: `{performance["gatePhase"]}`
- Release blocking: `{performance["releaseBlocking"]}`
- Samples: `{performance["frameCount"]}` frames, dropped: `{performance["droppedFrameCount"]}`
- p50 / p95: `{performance["p50Ms"]} ms` / `{performance["p95Ms"]} ms`

## Input And Scene Boundaries

- Real OS event injection claimed: `{data["inputPolicy"]["realOsEventInjectionClaimed"]}`
- Real OS event reason: `{data["inputPolicy"]["realOsEventInjectionReason"]}`
- Scene candidates: `{scene["sceneCount"]}`
- Renderable scenes: `{scene["renderableSceneCount"]}`
- Unsupported scenes: `{scene["unsupportedSceneCount"]}`
- Unsupported fallback reason: `{scene["unsupportedFallbackReason"]}`
- Nested-rrect fallback: `{scene["unsupportedCandidate"]["fallbackReasons"][0]}`

## Policy Cases

| Case | Decision | Allowed | Reason |
|---|---|---:|---|
{policy_rows}

## Validation

- `rtk python3 scripts/validate_for310_m90_runtime_telemetry_classification_guard.py`
- `{validation_policy["requiredHeadlessValidator"]}`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool {rel(ARTIFACT)}`
- `rtk git diff --check origin/master...HEAD`

Optional/provisioned refresh:

- `{validation_policy["optionalDirectRuntimeRefresh"]["command"]}`
- Required for this guard: `{validation_policy["optionalDirectRuntimeRefresh"]["requiredForThisGuard"]}`
- Reason: {validation_policy["optionalDirectRuntimeRefresh"]["reason"]}
"""
    REPORT.write_text(report, encoding="utf-8")


def main() -> None:
    data = validate_gate()
    write_artifact(data)
    write_report(data)
    load_json(ARTIFACT)
    if DECISION_APPLIED not in read_text(REPORT):
        fail("report does not contain the applied decision")
    print(f"{LINEAR_ID}: {DECISION_APPLIED}")
    print(f"artifact={rel(ARTIFACT)}")
    print(f"report={rel(REPORT)}")


if __name__ == "__main__":
    main()
