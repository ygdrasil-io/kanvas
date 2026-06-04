#!/usr/bin/env python3
"""Generate and validate the FOR-314 runtime cache counter source map."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-314"
SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-runtime-cache-counter-source-map-ticket"

TARGET = PROJECT_ROOT / ".upstream/target/skia-like-realtime-renderer-target.md"
RUNTIME_SPEC = PROJECT_ROOT / ".upstream/specs/skia-like-realtime/02-realtime-runtime-architecture.md"
PERF_SPEC = PROJECT_ROOT / ".upstream/specs/skia-like-realtime/04-performance-tiering-and-release-gates.md"
WEBGPU_DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
WARM_CACHE_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/GeneratedSolidRectMigrationTest.kt"
M85_EVIDENCE = PROJECT_ROOT / "reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json"
M90_EVIDENCE = PROJECT_ROOT / "reports/wgsl-pipeline/m90-runtime-interactive/evidence.json"
M90_TELEMETRY = PROJECT_ROOT / "reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json"
M92_CLASSIFICATION = PROJECT_ROOT / "reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json"

ARTIFACT = PROJECT_ROOT / "reports/wgsl-pipeline/runtime-cache-counter-source-map-for314.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-314-runtime-cache-counter-source-map.md"

OBSERVED_CLASSES = {"observed", "observed-runtime-cache-telemetry"}
PARTIAL_OR_DERIVED_CLASSES = {
    "observed-partial",
    "derived",
    "deterministic-derived",
    "not-observable",
    "unavailable",
}

REQUIRED_SNAPSHOT_FIELDS = {
    "shaderModuleCacheHits": "shader module cache hit counter",
    "shaderModuleCacheMisses": "shader module cache miss counter",
    "pipelineCacheHits": "pipeline cache hit counter",
    "pipelineCacheMisses": "pipeline cache miss counter",
    "resourceCacheHits": "resource cache hit counter",
    "resourceCacheMisses": "resource cache miss counter",
    "pipelineCreations": "pipeline creation counter",
    "shaderModuleCount": "shader module entry count",
    "pipelineCacheEntryCount": "pipeline cache entry count",
    "resourceCacheEntryCount": "resource cache entry count",
}

M85_COUNTER_FIELDS = [
    "frameCount",
    "pipelineCacheMisses",
    "pipelineCacheHits",
    "shaderModuleCount",
    "pipelineCount",
    "bindGroupCount",
    "textureCount",
    "textureUploadBytes",
    "intermediateTextureBytes",
    "bindGroupChurn",
    "resourceGenerationCount",
    "invalidResourceReuseCount",
]

M90_DERIVED_FIELDS = [
    "pipelineCacheHits",
    "pipelineCacheMisses",
    "textureUploadBytes",
    "intermediateTextureBytes",
    "bindGroupChurn",
    "resourceGenerationCount",
    "invalidResourceReuseCount",
]

M90_LIVE_FIELDS = [
    "pipelineCacheHits",
    "pipelineCacheMisses",
    "shaderModuleCreates",
    "pipelineCreates",
    "bindGroupCreates",
    "textureUploads",
    "intermediateTextureBytes",
]

PROTECTED_M92_ROWS = {
    "shader-module-creates-selected-route": "observed-partial",
    "pipeline-creates-selected-route": "observed-partial",
    "pipeline-cache-hits-misses-selected-ledger": "derived",
    "resource-generation-count": "derived",
    "invalid-resource-reuse-count": "derived",
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


def require_snippets(path: Path, snippets: list[str]) -> None:
    text = read_text(path)
    flattened = compact(text)
    for snippet in snippets:
        if snippet not in text and compact(snippet) not in flattened:
            fail(f"{rel(path)} missing required snippet `{snippet}`")


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


def source_path_exists(source: Any) -> bool:
    if not isinstance(source, str) or not source:
        return False
    path_text = source.split("#", 1)[0]
    if not path_text or path_text.startswith("mep-next.") or path_text.startswith("kadre-runtime."):
        return False
    return (PROJECT_ROOT / path_text).is_file()


def require_observed_source_artifact(
    *,
    owner: str,
    classification: str,
    source: Any,
    source_artifact: Any = None,
) -> None:
    if classification not in OBSERVED_CLASSES:
        return
    if source_path_exists(source_artifact) or source_path_exists(source):
        return
    fail(
        f"{owner} reclassifies cache counters as observed without a named "
        "observed source artifact"
    )


def integer_subset(data: dict[str, Any], fields: list[str], *, owner: str) -> dict[str, int]:
    out: dict[str, int] = {}
    for field in fields:
        value = data.get(field)
        if not isinstance(value, int):
            fail(f"{owner}.{field} must be an integer counter")
        out[field] = value
    return out


def validate_docs() -> dict[str, str]:
    require_snippets(
        TARGET,
        [
            "M85 resource/cache evidence",
            "not observed WebGPU runtime cache telemetry",
            "Current readiness after the M88 realtime renderer RC2 package freeze",
        ],
    )
    require_snippets(
        RUNTIME_SPEC,
        [
            "cache hits/misses",
            "M85 makes the selected Kadre/WebGPU realtime route auditable",
            "does not claim observed WebGPU runtime cache telemetry",
        ],
    )
    require_snippets(
        PERF_SPEC,
        [
            "`observed`",
            "`observed-partial`",
            "`derived`",
            "`unavailable`",
            "must not promote `frame.kadre-windowed` to release-blocking",
        ],
    )
    return {
        rel(TARGET): "verified",
        rel(RUNTIME_SPEC): "verified",
        rel(PERF_SPEC): "verified",
    }


def validate_webgpu_snapshot() -> dict[str, Any]:
    text = read_text(WEBGPU_DEVICE)
    match = re.search(
        r"public data class GpuCacheTelemetrySnapshot\(\n(?P<body>.*?)\n    \)",
        text,
        flags=re.S,
    )
    if not match:
        fail("SkWebGpuDevice.GpuCacheTelemetrySnapshot data class not found")
    fields = re.findall(r"val\s+(\w+):\s+Int", match.group("body"))
    missing = [field for field in REQUIRED_SNAPSHOT_FIELDS if field not in fields]
    if missing:
        fail(f"GpuCacheTelemetrySnapshot missing required fields: {missing}")

    snapshot_match = re.search(
        r"public fun cacheTelemetrySnapshot\(\): GpuCacheTelemetrySnapshot = GpuCacheTelemetrySnapshot\(\n(?P<body>.*?)\n    \)",
        text,
        flags=re.S,
    )
    if not snapshot_match:
        fail("cacheTelemetrySnapshot() constructor mapping not found")
    snapshot_body = snapshot_match.group("body")
    for field in REQUIRED_SNAPSHOT_FIELDS:
        if f"{field} =" not in snapshot_body:
            fail(f"cacheTelemetrySnapshot() does not populate {field}")

    return {
        "sourceArtifact": rel(WEBGPU_DEVICE),
        "snapshotType": "SkWebGpuDevice.GpuCacheTelemetrySnapshot",
        "snapshotFunction": "SkWebGpuDevice.cacheTelemetrySnapshot()",
        "sourceClass": "kanvas-headless-webgpu-observed-candidate",
        "fields": [
            {"name": field, "meaning": REQUIRED_SNAPSHOT_FIELDS[field]}
            for field in REQUIRED_SNAPSHOT_FIELDS
        ],
        "counterFamilies": {
            "shaderModule": [
                "shaderModuleCacheHits",
                "shaderModuleCacheMisses",
                "shaderModuleCount",
            ],
            "pipeline": [
                "pipelineCacheHits",
                "pipelineCacheMisses",
                "pipelineCreations",
                "pipelineCacheEntryCount",
            ],
            "resource": [
                "resourceCacheHits",
                "resourceCacheMisses",
                "resourceCacheEntryCount",
            ],
            "creation": ["pipelineCreations"],
            "entryCount": [
                "shaderModuleCount",
                "pipelineCacheEntryCount",
                "resourceCacheEntryCount",
            ],
        },
    }


def validate_warm_cache_test() -> dict[str, Any]:
    text = read_text(WARM_CACHE_TEST)
    for snippet in [
        "generated solid color rect reuses warm pipeline cache",
        "val cold = device.cacheTelemetrySnapshot()",
        "val warm = device.cacheTelemetrySnapshot()",
        "warm.pipelineCacheHits > cold.pipelineCacheHits",
    ]:
        if snippet not in text:
            fail(f"{rel(WARM_CACHE_TEST)} missing warm-cache assertion `{snippet}`")
    return {
        "sourceArtifact": rel(WARM_CACHE_TEST),
        "testName": "generated solid color rect reuses warm pipeline cache",
        "asserts": [
            "cold pipeline cache misses are recorded",
            "warm pipeline cache hits increase after the second draw",
            "the generated solid rect path remains selected",
        ],
        "uses": "SkWebGpuDevice.cacheTelemetrySnapshot()",
    }


def classify_m85(evidence: dict[str, Any]) -> str:
    counter_source = str(evidence.get("counterSource", ""))
    observed = evidence.get("observedRuntimeCounters")
    if observed is True or counter_source.startswith("observed"):
        return "observed"
    if "derived" in counter_source:
        return "derived"
    return "unavailable"


def validate_m85() -> dict[str, Any]:
    evidence = load_json(M85_EVIDENCE)
    classification = classify_m85(evidence)
    require_observed_source_artifact(
        owner="M85",
        classification=classification,
        source=evidence.get("observedSourceArtifact"),
        source_artifact=evidence.get("observedSourceArtifact"),
    )
    if classification != "derived":
        fail(f"M85 current cache source must remain derived for FOR-314, got {classification}")
    if evidence.get("observedRuntimeCounters") is not False:
        fail("M85 observedRuntimeCounters must remain false")
    if evidence.get("countedAsCacheReadinessGate") is not False:
        fail("M85 must not count as a cache readiness gate")
    if evidence.get("counterSource") != "derived-selected-scene-resource-ledger":
        fail("M85 counterSource must remain derived-selected-scene-resource-ledger")

    per_frame = require_object(evidence, "perFrameResourceTelemetry", M85_EVIDENCE)
    counters = integer_subset(per_frame, M85_COUNTER_FIELDS, owner="M85 perFrameResourceTelemetry")
    cache_ownership = require_object(evidence, "cacheOwnership", M85_EVIDENCE)
    if cache_ownership.get("uniformValuesInPipelineKey") is not False:
        fail("M85 PipelineKey policy must continue excluding uniform values")

    non_claims = " ".join(str(item) for item in require_list(evidence, "nonClaims", M85_EVIDENCE))
    if "not observed WebGPU runtime cache telemetry" not in non_claims:
        fail("M85 nonClaims must preserve the observed runtime cache telemetry non-claim")

    return {
        "sourceClass": "derived",
        "sourceArtifact": rel(M85_EVIDENCE),
        "counterSource": evidence["counterSource"],
        "observedRuntimeCounters": evidence["observedRuntimeCounters"],
        "countedAsCacheReadinessGate": evidence["countedAsCacheReadinessGate"],
        "counters": counters,
        "pipelineKeyPolicy": cache_ownership.get("pipelineKeyPolicy"),
        "uniformValuesInPipelineKey": cache_ownership["uniformValuesInPipelineKey"],
        "nonClaim": "selected-scene deterministic ledger, not observed WebGPU runtime cache telemetry",
    }


def validate_m90_resource_block(resources: dict[str, Any], *, source: Path, owner: str) -> dict[str, Any]:
    observed = require_object(resources, "observedRuntimeTelemetry", source)
    derived = require_object(resources, "derivedLedger", source)
    live = require_object(resources, "liveRouteCounters", source)

    observed_class = "observed-partial"
    require_observed_source_artifact(
        owner=f"{owner} observedRuntimeTelemetry",
        classification=observed_class,
        source=observed.get("source"),
        source_artifact=observed.get("sourceArtifact"),
    )
    limitations = observed.get("limitations")
    if not isinstance(limitations, list) or not limitations:
        fail(f"{owner}.observedRuntimeTelemetry.limitations must document partial observation")
    limitation_text = " ".join(str(item) for item in limitations)
    for snippet in [
        "not a cache-hit implementation",
        "does not expose WebGPU cache hit/miss callbacks",
    ]:
        if snippet not in limitation_text:
            fail(f"{owner}.observedRuntimeTelemetry.limitations missing `{snippet}`")

    derived_source = derived.get("source")
    if derived_source != "reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json":
        fail(f"{owner}.derivedLedger.source must point to M85 evidence")
    derived_counters = integer_subset(derived, M90_DERIVED_FIELDS, owner=f"{owner}.derivedLedger")

    live_class = str(live.get("classification", ""))
    require_observed_source_artifact(
        owner=f"{owner} liveRouteCounters",
        classification=live_class,
        source=live.get("source"),
        source_artifact=live.get("sourceArtifact"),
    )
    if live_class != "observed-partial":
        fail(f"{owner}.liveRouteCounters.classification must remain observed-partial")
    if live.get("missingCounterReason") != "mep-next.native-cache-counter-unavailable":
        fail(f"{owner}.liveRouteCounters must preserve missing counter reason")
    live_counters = integer_subset(live, M90_LIVE_FIELDS, owner=f"{owner}.liveRouteCounters")
    if live_counters["pipelineCacheHits"] == derived_counters["pipelineCacheHits"]:
        fail(f"{owner}.liveRouteCounters must not mirror derived pipeline cache hits as observed")

    return {
        "observedRuntimeTelemetry": {
            "sourceClass": observed_class,
            "source": observed.get("source"),
            "shaderModuleCreates": observed.get("shaderModuleCreates"),
            "pipelineCreates": observed.get("pipelineCreates"),
            "limitations": limitations,
        },
        "derivedLedger": {
            "sourceClass": "derived",
            "sourceArtifact": derived_source,
            "counters": derived_counters,
        },
        "liveRouteCounters": {
            "sourceClass": live_class,
            "missingCounterReason": live["missingCounterReason"],
            "counters": live_counters,
        },
    }


def validate_m90() -> dict[str, Any]:
    evidence = load_json(M90_EVIDENCE)
    telemetry = load_json(M90_TELEMETRY)

    source_classification = require_object(telemetry, "sourceClassification", M90_TELEMETRY)
    for field, expected in {
        "nativeRouteAllocations": "observed-partial",
        "cacheHitsMisses": "derived",
    }.items():
        actual = source_classification.get(field)
        require_observed_source_artifact(
            owner=f"M90 sourceClassification.{field}",
            classification=str(actual),
            source=source_classification.get(f"{field}Source"),
            source_artifact=source_classification.get(f"{field}SourceArtifact"),
        )
        if actual != expected:
            fail(f"M90 sourceClassification.{field} must remain {expected}, got {actual}")

    evidence_resources = validate_m90_resource_block(
        require_object(evidence, "resourceCacheTelemetry", M90_EVIDENCE),
        source=M90_EVIDENCE,
        owner="M90 evidence resourceCacheTelemetry",
    )
    telemetry_resources = validate_m90_resource_block(
        require_object(telemetry, "resources", M90_TELEMETRY),
        source=M90_TELEMETRY,
        owner="M90 telemetry resources",
    )
    if evidence_resources["derivedLedger"]["counters"] != telemetry_resources["derivedLedger"]["counters"]:
        fail("M90 evidence and telemetry derived ledger counters must agree")
    if evidence_resources["liveRouteCounters"]["counters"] != telemetry_resources["liveRouteCounters"]["counters"]:
        fail("M90 evidence and telemetry live route counters must agree")

    return {
        "sourceClass": "observed-partial-native-route-with-derived-ledger",
        "sourceArtifacts": [rel(M90_EVIDENCE), rel(M90_TELEMETRY)],
        "sourceClassification": {
            "nativeRouteAllocations": source_classification["nativeRouteAllocations"],
            "cacheHitsMisses": source_classification["cacheHitsMisses"],
        },
        "observedPartial": telemetry_resources["observedRuntimeTelemetry"],
        "derivedLedger": telemetry_resources["derivedLedger"],
        "liveRouteCounters": telemetry_resources["liveRouteCounters"],
        "nonClaim": "selected native route creation/churn is not broad WebGPU cache hit/miss telemetry",
    }


def validate_m92() -> dict[str, Any]:
    classification = load_json(M92_CLASSIFICATION)
    rows = require_list(classification, "rows", M92_CLASSIFICATION)
    protected: dict[str, dict[str, Any]] = {}
    for row in rows:
        if not isinstance(row, dict):
            fail("M92 classification rows must be objects")
        row_id = row.get("id")
        if row_id not in PROTECTED_M92_ROWS:
            continue
        current_class = str(row.get("classification", ""))
        require_observed_source_artifact(
            owner=f"M92 row {row_id}",
            classification=current_class,
            source=row.get("source"),
            source_artifact=row.get("sourceArtifact"),
        )
        expected = PROTECTED_M92_ROWS[str(row_id)]
        if current_class != expected:
            fail(f"M92 row {row_id} must remain {expected}, got {current_class}")
        if row.get("releaseGate") is not False:
            fail(f"M92 row {row_id} must not be release-gated")
        protected[str(row_id)] = row

    missing = sorted(set(PROTECTED_M92_ROWS) - set(protected))
    if missing:
        fail(f"M92 classification missing protected rows: {missing}")

    blockers = require_list(classification, "blockers", M92_CLASSIFICATION)
    blocker_map = {
        str(item.get("id")): item
        for item in blockers
        if isinstance(item, dict) and isinstance(item.get("id"), str)
    }
    for blocker in [
        "kadre-runtime.native-cache-counter-unavailable",
        "kadre-runtime.resource-lifetime-observation-partial",
    ]:
        if blocker not in blocker_map:
            fail(f"M92 classification missing blocker {blocker}")

    not_observable_rows = [
        {
            "id": row_id,
            "classification": row["classification"],
            "blocker": row.get("blocker"),
            "pmMeaning": row.get("pmMeaning"),
            "releaseGate": row["releaseGate"],
        }
        for row_id, row in protected.items()
        if row.get("classification") == "not-observable"
    ]
    observed_partial_rows = [
        {
            "id": row_id,
            "classification": row["classification"],
            "sourceArtifact": row.get("source"),
            "limitation": row.get("limitation"),
            "releaseGate": row["releaseGate"],
        }
        for row_id, row in protected.items()
        if row.get("classification") == "observed-partial"
    ]

    return {
        "sourceClass": "not-observable-kadre-blockers-with-observed-partial-creation-rows",
        "sourceArtifact": rel(M92_CLASSIFICATION),
        "observedPartialRows": observed_partial_rows,
        "notObservableRows": not_observable_rows,
        "blockers": [
            {
                "id": blocker_id,
                "owner": blocker_map[blocker_id].get("owner"),
                "unblockCondition": blocker_map[blocker_id].get("unblockCondition"),
            }
            for blocker_id in sorted(blocker_map)
            if blocker_id
            in {
                "kadre-runtime.native-cache-counter-unavailable",
                "kadre-runtime.resource-lifetime-observation-partial",
            }
        ],
    }


def validate_policy_cases() -> list[dict[str, Any]]:
    cases = [
        {
            "name": "derived cache hits without observed artifact stay non-observed",
            "classification": "derived",
            "source": "reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json",
            "allowed": True,
        },
        {
            "name": "observed partial native route stays non-observed",
            "classification": "observed-partial",
            "source": "mep-next.cache-observed-partial-native-route",
            "allowed": True,
        },
        {
            "name": "observed cache claim without source artifact is rejected",
            "classification": "observed",
            "source": "mep-next.cache-observed-partial-native-route",
            "allowed": False,
        },
        {
            "name": "observed cache claim with checked-in source artifact is allowed",
            "classification": "observed",
            "source": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/GeneratedSolidRectMigrationTest.kt",
            "allowed": True,
        },
    ]
    rows: list[dict[str, Any]] = []
    for case in cases:
        allowed = case["classification"] not in OBSERVED_CLASSES or source_path_exists(case["source"])
        if allowed != case["allowed"]:
            fail(f"policy case failed: {case['name']}")
        rows.append(case | {"result": "allowed" if allowed else "rejected"})
    return rows


def build_artifact() -> dict[str, Any]:
    docs = validate_docs()
    snapshot = validate_webgpu_snapshot()
    warm_cache = validate_warm_cache_test()
    m85 = validate_m85()
    m90 = validate_m90()
    m92 = validate_m92()
    policy_cases = validate_policy_cases()

    return {
        "schemaVersion": 1,
        "linearIssue": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "generatedBy": "scripts/validate_for314_runtime_cache_counter_source_map.py",
        "status": "pass",
        "claimLevel": "runtime-cache-counter-source-map",
        "sourceArtifacts": {
            "docs": docs,
            "code": {
                rel(WEBGPU_DEVICE): "verified",
                rel(WARM_CACHE_TEST): "verified",
            },
            "evidence": {
                rel(M85_EVIDENCE): "verified",
                rel(M90_EVIDENCE): "verified",
                rel(M90_TELEMETRY): "verified",
                rel(M92_CLASSIFICATION): "verified",
            },
        },
        "kanvasHeadlessWebGpuObservedCandidate": {
            **snapshot,
            "warmPipelineReuseEvidence": warm_cache,
            "bridgeRequirements": [
                "Route cacheTelemetrySnapshot() counters into checked-in runtime evidence from a headless WebGPU execution artifact.",
                "Keep Kadre/wgpu4k native callbacks separate from Kanvas-owned headless counters.",
                "Do not count the candidate as broad Kadre native cache telemetry until the runtime evidence names an observed source artifact.",
            ],
        },
        "m85DerivedLedger": m85,
        "m90ObservedPartialNativeRoute": m90,
        "m92NotObservableKadreBlockers": m92,
        "guardPolicy": {
            "observedRequiresNamedSourceArtifact": True,
            "protectedPartialOrDerivedClasses": sorted(PARTIAL_OR_DERIVED_CLASSES),
            "policyCases": policy_cases,
        },
        "nonChanges": {
            "readiness": "unchanged",
            "score": "unchanged",
            "releaseGateStatus": "unchanged",
            "rendererBehavior": "unchanged",
            "gradle": "unchanged",
            "shaders": "unchanged",
            "thresholds": "unchanged",
            "sceneStatus": "unchanged",
            "fallbacks": "unchanged",
            "kadreNativeBehavior": "unchanged",
            "kadreProvisioning": "unchanged",
        },
        "validationRows": [
            {
                "id": "for314.gpu-cache-telemetry-snapshot-fields",
                "status": "pass",
                "assertion": "GpuCacheTelemetrySnapshot exposes shader module, pipeline, resource, creation, and entry-count counters.",
            },
            {
                "id": "for314.warm-pipeline-cache-reuse-source",
                "status": "pass",
                "assertion": "GeneratedSolidRectMigrationTest asserts warm pipeline cache reuse via cacheTelemetrySnapshot().",
            },
            {
                "id": "for314.m85-derived-ledger-boundary",
                "status": "pass",
                "assertion": "M85 remains a derived selected-scene ledger and is not counted as an observed cache readiness gate.",
            },
            {
                "id": "for314.m90-observed-partial-boundary",
                "status": "pass",
                "assertion": "M90 keeps native route allocations observed-partial and cache hits/misses derived from M85.",
            },
            {
                "id": "for314.m92-kadre-blockers",
                "status": "pass",
                "assertion": "M92 keeps broad Kadre/wgpu4k cache callback and resource lifetime blockers not observable.",
            },
            {
                "id": "for314.observed-source-artifact-guard",
                "status": "pass",
                "assertion": "Derived or observed-partial cache counters cannot be reclassified as observed without a named observed source artifact.",
            },
        ],
    }


def write_report(artifact: dict[str, Any]) -> None:
    kanvas = artifact["kanvasHeadlessWebGpuObservedCandidate"]
    m85 = artifact["m85DerivedLedger"]
    m90 = artifact["m90ObservedPartialNativeRoute"]
    m92 = artifact["m92NotObservableKadreBlockers"]
    m90_sources = ", ".join(f"`{item}`" for item in m90["sourceArtifacts"])
    lines = [
        "# FOR-314 Runtime Cache Counter Source Map",
        "",
        f"Linear issue: `{LINEAR_ID}`.",
        f"Source memory: `{SOURCE_MEMORY}`.",
        "",
        "## Summary",
        "",
        "This report maps the cache counter sources that already exist in Kanvas against the M85, M90, and M92 runtime evidence boundaries.",
        "The readiness/score/release gate/renderer/shaders/Kadre native behavior do not change.",
        "No renderer behavior, Gradle task, shader, threshold, scene status, fallback policy, readiness denominator, or Kadre provisioning changes are made.",
        "",
        "## Source Classes",
        "",
        "| Bucket | Class | Source artifact | Gate meaning |",
        "|---|---|---|---|",
        f"| Kanvas headless WebGPU | `{kanvas['sourceClass']}` | `{kanvas['sourceArtifact']}` and `{kanvas['warmPipelineReuseEvidence']['sourceArtifact']}` | Candidate source for future checked-in observed runtime evidence, not a Kadre native callback claim. |",
        f"| M85 ledger | `{m85['sourceClass']}` | `{m85['sourceArtifact']}` | Deterministic selected-scene ledger only; not observed WebGPU runtime cache telemetry. |",
        f"| M90 native route | `{m90['sourceClass']}` | {m90_sources} | Native route creation/churn is observed-partial; cache hits/misses stay derived from M85. |",
        f"| M92 Kadre blockers | `{m92['sourceClass']}` | `{m92['sourceArtifact']}` | Broad Kadre/wgpu4k callbacks and native resource lifetime snapshots remain blocked. |",
        "",
        "## Kanvas Headless Candidate",
        "",
        f"- Snapshot API: `{kanvas['snapshotType']}` via `{kanvas['snapshotFunction']}`.",
        "- Counter families: shader module, pipeline, resource, creation, and entry-count counters.",
        f"- Warm reuse source: `{kanvas['warmPipelineReuseEvidence']['testName']}` in `{kanvas['warmPipelineReuseEvidence']['sourceArtifact']}`.",
        "- Bridge requirement: future runtime evidence must route these counters through a named checked-in observed source artifact before claiming `observed` cache telemetry.",
        "",
        "## Existing Runtime Evidence",
        "",
        f"- M85 counter source: `{m85['counterSource']}`; cache readiness gate counted: `{m85['countedAsCacheReadinessGate']}`.",
        f"- M90 native route allocations: `{m90['sourceClassification']['nativeRouteAllocations']}`; M90 cache hits/misses: `{m90['sourceClassification']['cacheHitsMisses']}`.",
        f"- M92 blocker count in this map: `{len(m92['blockers'])}`.",
        "- Derived and observed-partial counters are not promoted to observed unless the source map can point to a real artifact path.",
        "",
        "## Non-Changes",
        "",
        "- Readiness: unchanged.",
        "- Score: unchanged.",
        "- Release gate status: unchanged.",
        "- Renderer behavior: unchanged.",
        "- Shaders: unchanged.",
        "- Kadre native behavior: unchanged.",
        "- Gradle, thresholds, scene status, fallbacks, and Kadre provisioning: unchanged.",
        "",
        "## Validation",
        "",
        "- `rtk python3 scripts/validate_for314_runtime_cache_counter_source_map.py`",
        "- `rtk python3 -m json.tool reports/wgsl-pipeline/runtime-cache-counter-source-map-for314.json >/dev/null`",
        "- `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`",
        "- `rtk ./gradlew pipelineSceneDashboardGate`",
        "- `rtk git diff --check origin/master...HEAD`",
        "- `rtk git diff --check`",
        "",
    ]
    REPORT.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    artifact = build_artifact()
    ARTIFACT.write_text(json.dumps(artifact, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    write_report(artifact)
    print(f"{LINEAR_ID} runtime cache counter source map validated")
    print(f"Wrote {rel(ARTIFACT)}")
    print(f"Wrote {rel(REPORT)}")


if __name__ == "__main__":
    main()
