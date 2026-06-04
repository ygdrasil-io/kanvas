#!/usr/bin/env python3
"""Validate FOR-315 headless WebGPU cache counter evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-315"
SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-headless-web-gpu-cache-counter-evidence-ticket"

ARTIFACT = PROJECT_ROOT / "reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-315-headless-webgpu-cache-counters.md"
TEST_SOURCE = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/GeneratedSolidRectMigrationTest.kt"
FOR314_SOURCE_MAP = PROJECT_ROOT / "reports/wgsl-pipeline/runtime-cache-counter-source-map-for314.json"
M90_TELEMETRY = PROJECT_ROOT / "reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json"
M92_CLASSIFICATION = PROJECT_ROOT / "reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json"

SNAPSHOT_FIELDS = [
    "shaderModuleCacheHits",
    "shaderModuleCacheMisses",
    "pipelineCacheHits",
    "pipelineCacheMisses",
    "resourceCacheHits",
    "resourceCacheMisses",
    "pipelineCreations",
    "shaderModuleCount",
    "pipelineCacheEntryCount",
    "resourceCacheEntryCount",
]

PROTECTED_M92_ROWS = {
    "pipeline-cache-hits-misses-selected-ledger": "derived",
    "broad-webgpu-cache-hit-callbacks": "not-observable",
    "bind-group-cache-callbacks": "not-observable",
    "native-resource-free-callbacks": "not-observable",
    "adapter-owned-memory-snapshots": "not-observable",
}


def fail(message: str) -> None:
    raise SystemExit(f"{LINEAR_ID} validation failed: {message}")


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


def require_int_snapshot(data: dict[str, Any], field: str) -> dict[str, int]:
    snapshot = require_object(data, field, ARTIFACT)
    out: dict[str, int] = {}
    for name in SNAPSHOT_FIELDS:
        value = snapshot.get(name)
        if not isinstance(value, int):
            fail(f"{field}.{name} must be an integer")
        out[name] = value
    return out


def validate_test_source() -> dict[str, str]:
    require_snippets(
        TEST_SOURCE,
        [
            "generated solid color rect reuses warm pipeline cache",
            "val cold = device.cacheTelemetrySnapshot()",
            "val warm = device.cacheTelemetrySnapshot()",
            "WRITE_EVIDENCE_PROPERTY",
            "writeCacheCounterEvidence(cold, warm, diagnostics, adapter)",
        ],
    )
    return {rel(TEST_SOURCE): "verified"}


def validate_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    expected_scalars = {
        "schemaVersion": 1,
        "linearIssue": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "status": "pass",
        "claimLevel": "headless-webgpu-cache-counter-evidence",
        "sourceClass": "kanvas-headless-webgpu-observed",
        "sourceApi": "SkWebGpuDevice.cacheTelemetrySnapshot()",
        "backend": "WebGPU",
        "executionMode": "headless",
        "notKadreNativeCallbacks": True,
        "broadKadreWgpu4kCallbacksClaimed": False,
        "releaseGate": False,
        "readinessGateChanged": False,
    }
    for field, expected in expected_scalars.items():
        if data.get(field) != expected:
            fail(f"{rel(ARTIFACT)} expected {field}={expected!r}, got {data.get(field)!r}")

    if not isinstance(data.get("adapter"), str) or not data["adapter"]:
        fail("artifact must name the adapter used by the headless WebGPU run")

    generated_path = require_object(data, "generatedPath", ARTIFACT)
    if generated_path.get("selectedPath") != "generated":
        fail("generated path was not selected")
    if generated_path.get("generatedDefaultAvailable") is not True:
        fail("generated path must remain available by default")

    cold = require_int_snapshot(data, "coldSnapshot")
    warm = require_int_snapshot(data, "warmSnapshot")
    if cold["pipelineCacheMisses"] < 1:
        fail(f"cold pipeline cache misses must be >= 1, got {cold['pipelineCacheMisses']}")
    if warm["pipelineCacheHits"] <= cold["pipelineCacheHits"]:
        fail(
            "warm pipeline cache hits must increase "
            f"(cold={cold['pipelineCacheHits']} warm={warm['pipelineCacheHits']})"
        )

    invariants = require_object(data, "invariants", ARTIFACT)
    required_invariants = {
        "coldPipelineCacheMissesAtLeastOne": True,
        "warmPipelineCacheHitsIncreased": True,
        "generatedPathSelected": True,
    }
    for field, expected in required_invariants.items():
        if invariants.get(field) is not expected:
            fail(f"invariant {field} must be {expected}")

    non_changes = require_object(data, "nonChanges", ARTIFACT)
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
            fail(f"nonChanges.{field} must remain unchanged")

    return {
        "sourceClass": data["sourceClass"],
        "sourceApi": data["sourceApi"],
        "adapter": data["adapter"],
        "coldPipelineCacheMisses": cold["pipelineCacheMisses"],
        "coldPipelineCacheHits": cold["pipelineCacheHits"],
        "warmPipelineCacheHits": warm["pipelineCacheHits"],
    }


def validate_report() -> dict[str, str]:
    require_snippets(
        REPORT,
        [
            "FOR-315 Headless WebGPU Cache Counter Evidence",
            "`SkWebGpuDevice.cacheTelemetrySnapshot()`",
            "`kanvas-headless-webgpu-observed`",
            "not a broad Kadre/wgpu4k native callback claim",
            "Readiness, release gates, renderer behavior",
        ],
    )
    return {rel(REPORT): "verified"}


def validate_for314_source_map() -> dict[str, Any]:
    data = load_json(FOR314_SOURCE_MAP)
    candidate = require_object(data, "kanvasHeadlessWebGpuObservedCandidate", FOR314_SOURCE_MAP)
    if candidate.get("sourceClass") != "kanvas-headless-webgpu-observed-candidate":
        fail("FOR-314 headless candidate source class changed unexpectedly")
    if candidate.get("snapshotFunction") != "SkWebGpuDevice.cacheTelemetrySnapshot()":
        fail("FOR-314 source map no longer points at cacheTelemetrySnapshot()")
    bridge = require_list(candidate, "bridgeRequirements", FOR314_SOURCE_MAP)
    bridge_text = " ".join(str(item) for item in bridge)
    for snippet in [
        "checked-in runtime evidence from a headless WebGPU execution artifact",
        "Keep Kadre/wgpu4k native callbacks separate",
    ]:
        if snippet not in bridge_text:
            fail(f"FOR-314 bridge requirements missing `{snippet}`")

    m90 = require_object(data, "m90ObservedPartialNativeRoute", FOR314_SOURCE_MAP)
    classification = require_object(m90, "sourceClassification", FOR314_SOURCE_MAP)
    if classification.get("cacheHitsMisses") != "derived":
        fail("FOR-314 must keep M90 cache hits/misses derived")
    if classification.get("nativeRouteAllocations") != "observed-partial":
        fail("FOR-314 must keep M90 native route allocations observed-partial")

    m92 = require_object(data, "m92NotObservableKadreBlockers", FOR314_SOURCE_MAP)
    if m92.get("sourceClass") != "not-observable-kadre-blockers-with-observed-partial-creation-rows":
        fail("FOR-314 M92 blocker source class changed")

    return {
        "sourceClass": candidate["sourceClass"],
        "snapshotFunction": candidate["snapshotFunction"],
        "m90CacheHitsMisses": classification["cacheHitsMisses"],
        "m90NativeRouteAllocations": classification["nativeRouteAllocations"],
        "m92SourceClass": m92["sourceClass"],
    }


def validate_m90_telemetry() -> dict[str, Any]:
    data = load_json(M90_TELEMETRY)
    source_classification = require_object(data, "sourceClassification", M90_TELEMETRY)
    if source_classification.get("cacheHitsMisses") != "derived":
        fail("M90 telemetry cacheHitsMisses must remain derived")
    if source_classification.get("nativeRouteAllocations") != "observed-partial":
        fail("M90 telemetry nativeRouteAllocations must remain observed-partial")

    resources = require_object(data, "resources", M90_TELEMETRY)
    observed = require_object(resources, "observedRuntimeTelemetry", M90_TELEMETRY)
    limitations = require_list(observed, "limitations", M90_TELEMETRY)
    limitation_text = " ".join(str(item) for item in limitations)
    if "does not expose WebGPU cache hit/miss callbacks" not in limitation_text:
        fail("M90 observedRuntimeTelemetry must preserve cache callback limitation")

    live = require_object(resources, "liveRouteCounters", M90_TELEMETRY)
    if live.get("classification") != "observed-partial":
        fail("M90 live route counters must remain observed-partial")
    if live.get("missingCounterReason") != "mep-next.native-cache-counter-unavailable":
        fail("M90 missing counter reason changed")

    return {
        "cacheHitsMisses": source_classification["cacheHitsMisses"],
        "nativeRouteAllocations": source_classification["nativeRouteAllocations"],
        "liveRouteClassification": live["classification"],
        "missingCounterReason": live["missingCounterReason"],
    }


def validate_m92_classification() -> dict[str, Any]:
    data = load_json(M92_CLASSIFICATION)
    rows = require_list(data, "rows", M92_CLASSIFICATION)
    protected: dict[str, dict[str, Any]] = {}
    for row in rows:
        if not isinstance(row, dict):
            fail("M92 rows must be objects")
        row_id = row.get("id")
        if row_id in PROTECTED_M92_ROWS:
            protected[str(row_id)] = row

    missing = sorted(set(PROTECTED_M92_ROWS) - set(protected))
    if missing:
        fail(f"M92 classification missing protected rows: {missing}")

    for row_id, expected in PROTECTED_M92_ROWS.items():
        row = protected[row_id]
        if row.get("classification") != expected:
            fail(f"M92 row {row_id} must remain {expected}, got {row.get('classification')}")
        if row.get("releaseGate") is not False:
            fail(f"M92 row {row_id} must not become release-gated")

    blockers = require_list(data, "blockers", M92_CLASSIFICATION)
    blocker_ids = {
        str(item.get("id"))
        for item in blockers
        if isinstance(item, dict) and isinstance(item.get("id"), str)
    }
    if "kadre-runtime.native-cache-counter-unavailable" not in blocker_ids:
        fail("M92 must preserve kadre-runtime.native-cache-counter-unavailable blocker")

    return {
        "protectedRows": {
            row_id: {
                "classification": protected[row_id]["classification"],
                "releaseGate": protected[row_id]["releaseGate"],
            }
            for row_id in sorted(protected)
        },
        "blockers": sorted(blocker_ids),
    }


def build_validation_summary() -> dict[str, Any]:
    return {
        "linearIssue": LINEAR_ID,
        "status": "pass",
        "testSource": validate_test_source(),
        "artifact": validate_artifact(),
        "report": validate_report(),
        "for314SourceMap": validate_for314_source_map(),
        "m90Telemetry": validate_m90_telemetry(),
        "m92Classification": validate_m92_classification(),
    }


def main() -> None:
    summary = build_validation_summary()
    print(f"{LINEAR_ID} headless WebGPU cache counter evidence validated")
    print(json.dumps(summary["artifact"], indent=2))


if __name__ == "__main__":
    main()
