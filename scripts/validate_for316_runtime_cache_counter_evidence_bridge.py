#!/usr/bin/env python3
"""Validate the FOR-316 runtime cache counter evidence bridge."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-316"
SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-runtime-cache-counter-evidence-bridge-ticket"

FOR315_ARTIFACT = PROJECT_ROOT / "reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json"
FOR314_SOURCE_MAP = PROJECT_ROOT / "reports/wgsl-pipeline/runtime-cache-counter-source-map-for314.json"
FOR314_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-314-runtime-cache-counter-source-map.md"
M90_TELEMETRY = PROJECT_ROOT / "reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json"
M92_CLASSIFICATION = PROJECT_ROOT / "reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-316-runtime-cache-counter-evidence-bridge.md"

DECISION_APPLIED = "RUNTIME_CACHE_COUNTER_EVIDENCE_BRIDGE_APPLIED"
DECISION_MISSING_SOURCE = "RUNTIME_CACHE_COUNTER_EVIDENCE_BRIDGE_MISSING_SOURCE"
DECISION_UNSAFE = "RUNTIME_CACHE_COUNTER_EVIDENCE_BRIDGE_UNSAFE_RECLASSIFICATION_FOUND"

FOR315_REL = "reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json"
SOURCE_API = "SkWebGpuDevice.cacheTelemetrySnapshot()"
SOURCE_CLASS = "kanvas-headless-webgpu-observed"
OBSERVED_CLASSES = {"observed", "observed-runtime-cache-telemetry"}

PROTECTED_M92_ROWS = {
    "pipeline-cache-hits-misses-selected-ledger": "derived",
    "broad-webgpu-cache-hit-callbacks": "not-observable",
    "bind-group-cache-callbacks": "not-observable",
    "native-resource-free-callbacks": "not-observable",
    "adapter-owned-memory-snapshots": "not-observable",
}


def fail(message: str, decision: str = DECISION_MISSING_SOURCE) -> None:
    raise SystemExit(f"{LINEAR_ID} validation failed [{decision}]: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def compact(text: str) -> str:
    return " ".join(text.split())


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


def require_object(data: dict[str, Any], field: str, source: Path) -> dict[str, Any]:
    value = data.get(field)
    if not isinstance(value, dict):
        fail(f"{rel(source)} must contain object field {field}")
    return value


def require_list(data: dict[str, Any], field: str, source: Path) -> list[Any]:
    value = data.get(field)
    if not isinstance(value, list):
        fail(f"{rel(source)} must contain list field {field}")
    return value


def require_snippets(path: Path, snippets: list[str]) -> None:
    text = read_text(path)
    flattened = compact(text)
    lower_flattened = flattened.lower()
    for snippet in snippets:
        normalized = compact(snippet)
        if snippet not in text and normalized not in flattened and normalized.lower() not in lower_flattened:
            fail(f"{rel(path)} missing required snippet `{snippet}`")


def validate_for315_artifact() -> dict[str, Any]:
    data = load_json(FOR315_ARTIFACT)
    expected = {
        "linearIssue": "FOR-315",
        "status": "pass",
        "sourceClass": SOURCE_CLASS,
        "sourceApi": SOURCE_API,
        "backend": "WebGPU",
        "executionMode": "headless",
        "notKadreNativeCallbacks": True,
        "broadKadreWgpu4kCallbacksClaimed": False,
        "releaseGate": False,
        "readinessGateChanged": False,
    }
    for field, expected_value in expected.items():
        if data.get(field) != expected_value:
            fail(f"FOR-315 expected {field}={expected_value!r}, got {data.get(field)!r}")

    cold = require_object(data, "coldSnapshot", FOR315_ARTIFACT)
    warm = require_object(data, "warmSnapshot", FOR315_ARTIFACT)
    cold_misses = cold.get("pipelineCacheMisses")
    cold_hits = cold.get("pipelineCacheHits")
    warm_hits = warm.get("pipelineCacheHits")
    for field, value in {
        "coldSnapshot.pipelineCacheMisses": cold_misses,
        "coldSnapshot.pipelineCacheHits": cold_hits,
        "warmSnapshot.pipelineCacheHits": warm_hits,
    }.items():
        if not isinstance(value, int):
            fail(f"FOR-315 {field} must be an integer")
    if cold_misses < 1:
        fail("FOR-315 cold pipelineCacheMisses must be >= 1")
    if warm_hits <= cold_hits:
        fail("FOR-315 warm pipelineCacheHits must increase over cold")

    non_changes = require_object(data, "nonChanges", FOR315_ARTIFACT)
    for field in [
        "rendererBehavior",
        "gradle",
        "shaders",
        "thresholds",
        "sceneStatus",
        "readiness",
        "releaseGateStatus",
        "fallbacks",
        "kadreNativeBehavior",
    ]:
        if non_changes.get(field) != "unchanged":
            fail(f"FOR-315 nonChanges.{field} must remain unchanged")

    return {
        "sourceArtifact": FOR315_REL,
        "sourceClass": data["sourceClass"],
        "sourceApi": data["sourceApi"],
        "adapter": data.get("adapter"),
        "coldPipelineCacheMisses": cold_misses,
        "coldPipelineCacheHits": cold_hits,
        "warmPipelineCacheHits": warm_hits,
    }


def validate_for314_source_map() -> dict[str, Any]:
    data = load_json(FOR314_SOURCE_MAP)
    if data.get("linearIssue") != "FOR-314":
        fail("FOR-314 source map linearIssue changed")

    source_artifacts = require_object(data, "sourceArtifacts", FOR314_SOURCE_MAP)
    evidence = require_object(source_artifacts, "evidence", FOR314_SOURCE_MAP)
    if evidence.get(FOR315_REL) != "verified":
        fail("FOR-314 source map does not list the FOR-315 observed artifact")

    candidate = require_object(data, "kanvasHeadlessWebGpuObservedCandidate", FOR314_SOURCE_MAP)
    if candidate.get("sourceClass") != "kanvas-headless-webgpu-observed-candidate":
        fail("FOR-314 candidate source class changed unexpectedly")
    if candidate.get("snapshotFunction") != SOURCE_API:
        fail("FOR-314 candidate snapshot function must remain cacheTelemetrySnapshot()")

    bridge = require_object(candidate, "observedEvidenceBridge", FOR314_SOURCE_MAP)
    expected_bridge = {
        "linearIssue": "FOR-315",
        "sourceArtifact": FOR315_REL,
        "sourceClass": SOURCE_CLASS,
        "sourceApi": SOURCE_API,
        "backend": "WebGPU",
        "executionMode": "headless",
        "notKadreNativeCallbacks": True,
        "broadKadreWgpu4kCallbacksClaimed": False,
        "releaseGate": False,
        "readinessGateChanged": False,
    }
    for field, expected_value in expected_bridge.items():
        if bridge.get(field) != expected_value:
            fail(f"FOR-314 observedEvidenceBridge.{field} expected {expected_value!r}, got {bridge.get(field)!r}")
    if not isinstance(bridge.get("coldPipelineCacheMisses"), int) or bridge["coldPipelineCacheMisses"] < 1:
        fail("FOR-314 observedEvidenceBridge must preserve cold pipelineCacheMisses >= 1")
    if not isinstance(bridge.get("warmPipelineCacheHits"), int) or not isinstance(bridge.get("coldPipelineCacheHits"), int):
        fail("FOR-314 observedEvidenceBridge must preserve integer pipelineCacheHits")
    if bridge["warmPipelineCacheHits"] <= bridge["coldPipelineCacheHits"]:
        fail("FOR-314 observedEvidenceBridge warm pipelineCacheHits must increase")

    bridge_requirements = " ".join(str(item) for item in require_list(candidate, "bridgeRequirements", FOR314_SOURCE_MAP))
    for snippet in [
        "FOR-315 satisfies the named observed artifact requirement",
        "Keep Kadre/wgpu4k native callbacks separate",
    ]:
        if snippet not in bridge_requirements:
            fail(f"FOR-314 bridgeRequirements missing `{snippet}`")

    non_changes = require_object(data, "nonChanges", FOR314_SOURCE_MAP)
    for field in [
        "readiness",
        "releaseGateStatus",
        "score",
        "rendererBehavior",
        "shaders",
        "thresholds",
        "fallbacks",
        "kadreNativeBehavior",
    ]:
        if non_changes.get(field) != "unchanged":
            fail(f"FOR-314 nonChanges.{field} must remain unchanged")

    return {
        "candidateSourceClass": candidate["sourceClass"],
        "observedBridgeSourceClass": bridge["sourceClass"],
        "observedBridgeSourceArtifact": bridge["sourceArtifact"],
        "snapshotFunction": candidate["snapshotFunction"],
    }


def validate_m90_m92_boundaries() -> dict[str, Any]:
    source_map = load_json(FOR314_SOURCE_MAP)
    m90 = require_object(source_map, "m90ObservedPartialNativeRoute", FOR314_SOURCE_MAP)
    classification = require_object(m90, "sourceClassification", FOR314_SOURCE_MAP)
    if classification.get("cacheHitsMisses") != "derived":
        fail("FOR-314 M90 cacheHitsMisses must remain derived", DECISION_UNSAFE)
    if classification.get("nativeRouteAllocations") != "observed-partial":
        fail("FOR-314 M90 nativeRouteAllocations must remain observed-partial", DECISION_UNSAFE)
    live = require_object(m90, "liveRouteCounters", FOR314_SOURCE_MAP)
    if live.get("sourceClass") != "observed-partial":
        fail("FOR-314 M90 liveRouteCounters must remain observed-partial", DECISION_UNSAFE)

    telemetry = load_json(M90_TELEMETRY)
    telemetry_classification = require_object(telemetry, "sourceClassification", M90_TELEMETRY)
    if telemetry_classification.get("cacheHitsMisses") != "derived":
        fail("M90 telemetry cacheHitsMisses must remain derived", DECISION_UNSAFE)
    if telemetry_classification.get("nativeRouteAllocations") != "observed-partial":
        fail("M90 telemetry nativeRouteAllocations must remain observed-partial", DECISION_UNSAFE)

    m92 = require_object(source_map, "m92NotObservableKadreBlockers", FOR314_SOURCE_MAP)
    if m92.get("sourceClass") != "not-observable-kadre-blockers-with-observed-partial-creation-rows":
        fail("FOR-314 M92 sourceClass changed", DECISION_UNSAFE)

    m92_data = load_json(M92_CLASSIFICATION)
    rows = require_list(m92_data, "rows", M92_CLASSIFICATION)
    protected: dict[str, dict[str, Any]] = {}
    for row in rows:
        if not isinstance(row, dict):
            fail("M92 rows must be objects", DECISION_UNSAFE)
        row_id = row.get("id")
        if row_id in PROTECTED_M92_ROWS:
            protected[str(row_id)] = row

    missing = sorted(set(PROTECTED_M92_ROWS) - set(protected))
    if missing:
        fail(f"M92 classification missing protected rows: {missing}", DECISION_UNSAFE)

    for row_id, expected in PROTECTED_M92_ROWS.items():
        row = protected[row_id]
        current = row.get("classification")
        if current != expected:
            fail(f"M92 row {row_id} must remain {expected}, got {current}", DECISION_UNSAFE)
        if current in OBSERVED_CLASSES:
            fail(f"M92 row {row_id} became a broad observed cache callback", DECISION_UNSAFE)
        if row.get("releaseGate") is not False:
            fail(f"M92 row {row_id} must not become release-gated", DECISION_UNSAFE)

    blockers = require_list(m92_data, "blockers", M92_CLASSIFICATION)
    blocker_ids = {
        str(item.get("id"))
        for item in blockers
        if isinstance(item, dict) and isinstance(item.get("id"), str)
    }
    if "kadre-runtime.native-cache-counter-unavailable" not in blocker_ids:
        fail("M92 must preserve the native cache counter unavailable blocker", DECISION_UNSAFE)

    return {
        "m90CacheHitsMisses": classification["cacheHitsMisses"],
        "m90NativeRouteAllocations": classification["nativeRouteAllocations"],
        "m90LiveRouteCounters": live["sourceClass"],
        "m92ProtectedRows": {row_id: protected[row_id]["classification"] for row_id in sorted(protected)},
        "m92Blockers": sorted(blocker_ids),
    }


def validate_reports() -> dict[str, str]:
    require_snippets(
        FOR314_REPORT,
        [
            FOR315_REL,
            SOURCE_CLASS,
            "not a broad Kadre/wgpu4k native cache callback claim",
            "Readiness: unchanged",
            "Score: unchanged",
            "Release gate status: unchanged",
            "Renderer behavior: unchanged",
            "Shaders: unchanged",
            "Kadre native behavior: unchanged",
        ],
    )
    return {rel(FOR314_REPORT): "verified"}


def validate_bridge() -> dict[str, Any]:
    return {
        "linearIssue": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_APPLIED,
        "for315Artifact": validate_for315_artifact(),
        "for314SourceMap": validate_for314_source_map(),
        "runtimeBoundaries": validate_m90_m92_boundaries(),
        "reports": validate_reports(),
        "nonChanges": {
            "readiness": "unchanged",
            "releaseGateStatus": "unchanged",
            "score": "unchanged",
            "rendererBehavior": "unchanged",
            "shaders": "unchanged",
            "thresholds": "unchanged",
            "fallbacks": "unchanged",
            "kadreNativeBehavior": "unchanged",
        },
        "alternateDecisions": {
            "missingSource": DECISION_MISSING_SOURCE,
            "unsafeReclassification": DECISION_UNSAFE,
        },
    }


def write_report(data: dict[str, Any]) -> None:
    artifact = data["for315Artifact"]
    source_map = data["for314SourceMap"]
    boundaries = data["runtimeBoundaries"]
    report = f"""# FOR-316 Pont de preuve runtime cache counter

Linear: `{LINEAR_ID}`

Source memory: `{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Résultat

FOR-316 relie la cartographie FOR-314 à la preuve FOR-315. La source candidate
`SkWebGpuDevice.cacheTelemetrySnapshot()` reste décrite dans FOR-314, et le
nouveau champ `observedEvidenceBridge` pointe maintenant vers l'artefact observé
`{artifact["sourceArtifact"]}`.

Ce pont établit une preuve Kanvas headless WebGPU observée pour ce scénario:
source class `{artifact["sourceClass"]}`, source API `{artifact["sourceApi"]}`,
adapter `{artifact["adapter"]}`, cold `pipelineCacheMisses={artifact["coldPipelineCacheMisses"]}`,
cold `pipelineCacheHits={artifact["coldPipelineCacheHits"]}`, warm
`pipelineCacheHits={artifact["warmPipelineCacheHits"]}`.

## Frontière de revendication

Cette preuve n'est pas un callback cache Kadre/wgpu4k natif large. Elle provient
d'une exécution headless WebGPU Kanvas et ne change ni le comportement natif
Kadre, ni les callbacks exposés par wgpu4k, ni les blockers M92.

## Cartographie FOR-314

- Source candidate FOR-314: `{source_map["candidateSourceClass"]}`.
- Pont observé FOR-315: `{source_map["observedBridgeSourceClass"]}`.
- Artefact observé nommé: `{source_map["observedBridgeSourceArtifact"]}`.
- API snapshot: `{source_map["snapshotFunction"]}`.

## M90/M92 conservés

- M90 cache hits/misses: `{boundaries["m90CacheHitsMisses"]}`.
- M90 allocations route native: `{boundaries["m90NativeRouteAllocations"]}`.
- M90 live route counters: `{boundaries["m90LiveRouteCounters"]}`.
- M92 broad cache callback rows: conservent les classifications `not-observable`
  ou `derived` protégées.
- Blocker natif conservé: `kadre-runtime.native-cache-counter-unavailable`.

## Non-changements

- Readiness: unchanged.
- Release gate status: unchanged.
- Score: unchanged.
- Renderer behavior: unchanged.
- Shaders: unchanged.
- Thresholds: unchanged.
- Fallbacks: unchanged.
- Kadre native behavior: unchanged.

## Validation

- `rtk python3 scripts/validate_for316_runtime_cache_counter_evidence_bridge.py`
- `rtk python3 scripts/validate_for315_headless_webgpu_cache_counters.py`
- `rtk python3 scripts/validate_for314_runtime_cache_counter_source_map.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/runtime-cache-counter-source-map-for314.json >/dev/null`
- `rtk python3 -m json.tool reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json >/dev/null`
- `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check`
"""
    REPORT.write_text(report, encoding="utf-8")


def main() -> None:
    data = validate_bridge()
    write_report(data)
    print(f"{LINEAR_ID} runtime cache counter evidence bridge validated")
    print(json.dumps({
        "decision": data["decision"],
        "for315Artifact": data["for315Artifact"],
        "for314SourceMap": data["for314SourceMap"],
        "m90CacheHitsMisses": data["runtimeBoundaries"]["m90CacheHitsMisses"],
        "m90NativeRouteAllocations": data["runtimeBoundaries"]["m90NativeRouteAllocations"],
    }, indent=2))


if __name__ == "__main__":
    main()
