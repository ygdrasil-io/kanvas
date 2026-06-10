#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


REPORT_PATH = "reports/wgsl-pipeline/2026-06-10-kan-021-cache-resource-telemetry.md"
EVIDENCE_PATH = "reports/wgsl-pipeline/m85-resource-lifetime-cache/kan-021-selected-telemetry.json"
SPEC_RUNTIME_PATH = ".upstream/specs/skia-like-realtime/02-realtime-runtime-architecture.md"
SPEC_PERF_PATH = ".upstream/specs/skia-like-realtime/04-performance-tiering-and-release-gates.md"


def fail(message: str):
    raise SystemExit(f"KAN-021 cache/resource telemetry validation failed: {message}")


def require(condition: bool, message: str):
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")
    require(isinstance(data, dict), f"{relative_path} must be a JSON object")
    return data


def require_file(root: Path, relative_path: str):
    require((root / relative_path).is_file(), f"missing referenced artifact: {relative_path}")


def require_contains(root: Path, relative_path: str, snippets: list[str]):
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    flattened = " ".join(text.split())
    for snippet in snippets:
        require(
            snippet in text or " ".join(snippet.split()) in flattened,
            f"{relative_path} missing snippet: {snippet}",
        )


def require_object(data: dict[str, Any], field: str, source: str) -> dict[str, Any]:
    value = data.get(field)
    require(isinstance(value, dict), f"{source}.{field} must be an object")
    return value


def require_list(data: dict[str, Any], field: str, source: str) -> list[Any]:
    value = data.get(field)
    require(isinstance(value, list), f"{source}.{field} must be a list")
    return value


def row_by_id(rows: list[Any], row_id: str, source: str) -> dict[str, Any]:
    for row in rows:
        if isinstance(row, dict) and row.get("id") == row_id:
            return row
    fail(f"{source} missing row id: {row_id}")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    evidence = load_json(root, EVIDENCE_PATH)
    require(evidence.get("schemaVersion") == 1, "schemaVersion changed")
    require(evidence.get("ticket") == "KAN-021", "ticket id changed")
    require(evidence.get("packId") == "kan-021-cache-resource-telemetry-selected-v1", "packId changed")
    require(evidence.get("status") == "pass", "status must remain pass")
    require(evidence.get("claimLevel") == "selected-cache-resource-telemetry-reporting", "claimLevel changed")
    require(evidence.get("performanceClass") == "derived-ledger-reporting", "performanceClass must use KAN-020 resource-cache default")
    require(evidence.get("releaseBlocking") is False, "KAN-021 must not become release-blocking")
    require(evidence.get("countedAsMeasuredGate") is False, "KAN-021 must not count as a measured gate")
    require(evidence.get("countedAsCacheReadinessGate") is False, "KAN-021 must not count as a cache readiness gate")
    require(evidence.get("readinessDelta") == 0, "KAN-021 must not move readiness")
    require(evidence.get("selectedSceneContractId") == "m83-display-list-pm-scene-v1", "selected scene contract changed")
    require(evidence.get("lane") == "frame.kadre-windowed", "lane changed")

    selected = require_object(evidence, "selectedEvidence", EVIDENCE_PATH)
    require(selected.get("primaryArtifact") == "reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json", "primary selected artifact changed")
    require(selected.get("counterSource") == "derived-selected-scene-resource-ledger", "selected counter source changed")
    require(selected.get("observedRuntimeCounters") is False, "M85 selected evidence must stay derived")
    require(selected.get("observedRuntimeCacheTelemetryClaimed") is False, "must not claim observed runtime cache telemetry")

    m85 = load_json(root, "reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json")
    require(m85.get("packId") == "m85-resource-lifetime-cache-hardening-v1", "M85 packId changed")
    require(m85.get("sceneContractId") == evidence.get("selectedSceneContractId"), "M85 selected scene mismatch")
    require(m85.get("lane") == evidence.get("lane"), "M85 lane mismatch")
    require(m85.get("observedRuntimeCounters") is False, "M85 must remain non-observed")
    require(m85.get("countedAsCacheReadinessGate") is False, "M85 must remain non-gating")
    require(m85.get("counterSource") == "derived-selected-scene-resource-ledger", "M85 counter source changed")
    telemetry = require_object(m85, "perFrameResourceTelemetry", "M85")
    selected_counters = require_object(selected, "counters", "selectedEvidence")
    for field in (
        "frameCount",
        "pipelineCacheHits",
        "pipelineCacheMisses",
        "shaderModuleCount",
        "pipelineCount",
        "bindGroupCount",
        "textureCount",
        "textureUploadBytes",
        "intermediateTextureBytes",
        "bindGroupChurn",
        "resourceGenerationCount",
        "invalidResourceReuseCount",
    ):
        require(selected_counters.get(field) == telemetry.get(field), f"selected counter mismatch: {field}")
    require(selected_counters.get("frameCount") == 180, "selected frame count changed")
    require(selected_counters.get("pipelineCacheHits") == 179, "selected cache hits changed")
    require(selected_counters.get("pipelineCacheMisses") == 1, "selected cache misses changed")
    require(selected_counters.get("invalidResourceReuseCount") == 0, "selected invalid reuse must stay zero")

    cache_keys = require_object(evidence, "cacheKeyPolicy", EVIDENCE_PATH)
    m85_ownership = require_object(m85, "cacheOwnership", "M85")
    require(cache_keys.get("pipelineKeyPolicy") == "layout-code-resource-pipeline-state-only", "pipeline key policy changed")
    require(cache_keys.get("uniformValuesInPipelineKey") is False, "uniform values must not become pipeline key axes")
    require(cache_keys.get("boundedKeySpaceCount") == m85_ownership.get("boundedKeySpaceCount"), "bounded keyspace count mismatch")
    require(cache_keys.get("boundedKeySpaceCount") == 6, "bounded keyspace count changed")

    invalidation = require_object(evidence, "invalidationEvidence", EVIDENCE_PATH)
    m85_invalidation = require_object(m85, "resizeInvalidation", "M85")
    require(invalidation.get("source") == "reports/wgsl-pipeline/m82-kadre-input-resize-runtime-loop/evidence.json", "invalidation source changed")
    require(invalidation.get("reconfigureCount") == m85_invalidation.get("reconfigureCount"), "reconfigure count mismatch")
    require(invalidation.get("reconfigureFailureCount") == 0, "reconfigure failures must stay zero")
    require(invalidation.get("generationsStrictlyAdvance") is True, "generations must strictly advance")
    require(invalidation.get("generationSequenceMonotonic") is True, "generation sequence must stay monotonic")
    require(invalidation.get("invalidatesWebGpuResources") is True, "resize must invalidate WebGPU resources")
    require(invalidation.get("invalidResourceReuseCount") == 0, "invalid reuse must stay zero")
    require(invalidation.get("failureReasonIfObserved") == "m85.invalid-resource-generation-reuse", "invalid reuse reason changed")

    source_map = require_object(evidence, "sourceClassification", EVIDENCE_PATH)
    require(source_map.get("m85Ledger") == "derived", "M85 source class changed")
    require(source_map.get("m90NativeRouteAllocations") == "observed-partial", "M90 native route class changed")
    require(source_map.get("m90CacheHitsMisses") == "derived", "M90 cache hit/miss class changed")
    require(source_map.get("for315HeadlessWebGpuSnapshot") == "observed", "FOR-315 observed bridge class changed")
    require(source_map.get("broadKadreWgpu4kCacheCallbacks") == "not-observable", "broad Kadre callback class changed")

    m90_live = load_json(root, "reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json")
    source_classification = require_object(m90_live, "sourceClassification", "M90 telemetry-live")
    require(source_classification.get("nativeRouteAllocations") == "observed-partial", "M90 nativeRouteAllocations changed")
    require(source_classification.get("cacheHitsMisses") == "derived", "M90 cacheHitsMisses changed")
    live_resources = require_object(m90_live, "resources", "M90 telemetry-live")
    live_route = require_object(live_resources, "liveRouteCounters", "M90 resources")
    require(live_route.get("classification") == "observed-partial", "M90 live route counters class changed")
    require(live_route.get("missingCounterReason") == "mep-next.native-cache-counter-unavailable", "M90 missing counter reason changed")

    for315 = load_json(root, "reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json")
    require(for315.get("sourceClass") == "kanvas-headless-webgpu-observed", "FOR-315 source class changed")
    require(for315.get("sourceApi") == "SkWebGpuDevice.cacheTelemetrySnapshot()", "FOR-315 source API changed")
    require(for315.get("notKadreNativeCallbacks") is True, "FOR-315 must stay separate from Kadre native callbacks")
    require(for315.get("broadKadreWgpu4kCallbacksClaimed") is False, "FOR-315 must not claim broad Kadre callbacks")
    require(for315.get("releaseGate") is False, "FOR-315 must remain non-gating")

    m92 = load_json(root, "reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json")
    m92_rows = require_list(m92, "rows", "M92 telemetry classification")
    require(row_by_id(m92_rows, "shader-module-creates-selected-route", "M92").get("classification") == "observed-partial", "M92 shader create class changed")
    require(row_by_id(m92_rows, "pipeline-creates-selected-route", "M92").get("classification") == "observed-partial", "M92 pipeline create class changed")
    require(row_by_id(m92_rows, "pipeline-cache-hits-misses-selected-ledger", "M92").get("classification") == "derived", "M92 cache hit/miss class changed")
    require(row_by_id(m92_rows, "resource-generation-count", "M92").get("classification") == "derived", "M92 resource generation class changed")
    require(row_by_id(m92_rows, "broad-webgpu-cache-hit-callbacks", "M92").get("classification") == "not-observable", "M92 broad cache callback class changed")
    require(row_by_id(m92_rows, "native-resource-free-callbacks", "M92").get("classification") == "not-observable", "M92 resource free callback class changed")

    device_loss = require_object(evidence, "deviceLossDiagnostics", EVIDENCE_PATH)
    require(device_loss.get("status") == "expected-unsupported", "device-loss status changed")
    require(device_loss.get("reason") == "m85.device-loss-recreate-observation-unsupported", "device-loss reason changed")
    require(device_loss.get("recreateClaimed") is False, "device-loss recreate must not be claimed")

    validations = require_list(evidence, "validationRows", EVIDENCE_PATH)
    require(len(validations) >= 6, "validationRows missing rows")
    for row in validations:
        require(isinstance(row, dict), "validation row must be object")
        require(row.get("status") == "pass", f"validation row failed: {row.get('id')}")

    non_claims = "\n".join(evidence.get("nonClaims", []))
    for snippet in (
        "does not claim broad cache behavior",
        "does not claim observed WebGPU runtime cache telemetry",
        "does not claim real device-lost recovery",
        "does not count as a release-blocking or cache-readiness gate",
        "does not promote observed-partial native allocation churn to observed cache hits",
    ):
        require(snippet in non_claims, f"missing non-claim: {snippet}")

    for artifact in evidence.get("artifactPaths", []):
        require_file(root, artifact)

    require_contains(root, REPORT_PATH, [
        "KAN-021 selects `m83-display-list-pm-scene-v1`",
        "Performance class: `derived-ledger-reporting`",
        "M85 ledger: `derived`",
        "M90 native route allocations: `observed-partial`",
        "FOR-315 headless WebGPU snapshot: `observed`",
        "No broad cache, device-loss recovery, cache eviction, or cache-readiness gate claim is added.",
        "`reports/wgsl-pipeline/m85-resource-lifetime-cache/kan-021-selected-telemetry.json`",
    ])
    require_contains(root, SPEC_RUNTIME_PATH, [
        "M85 makes the selected Kadre/WebGPU realtime route auditable",
        "does not claim observed WebGPU runtime cache telemetry",
        "Pipeline cache keys must stay based on layout, code, resource, and pipeline state.",
    ])
    require_contains(root, SPEC_PERF_PATH, [
        "`observed`",
        "`observed-partial`",
        "`derived`",
        "`unavailable`",
        "cannot count as observed cache readiness",
    ])

    print("KAN-021 cache/resource telemetry validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
