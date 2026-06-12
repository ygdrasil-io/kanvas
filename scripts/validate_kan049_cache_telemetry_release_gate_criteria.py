#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/cache-telemetry-release-gate"
OUTPUT_JSON = "kan-049-cache-telemetry-release-gate.json"
OUTPUT_MARKDOWN = "kan-049-cache-telemetry-release-gate.md"
OUTPUT_NEGATIVE_FIXTURE = "kan-049-cache-telemetry-negative-fixture.json"
OUTPUT_GATE_FREEZE_DELTA = "kan-049-cache-telemetry-gate-freeze-delta.json"

SPEC_RUNTIME = ".upstream/specs/skia-like-realtime/02-realtime-runtime-architecture.md"
SPEC_PERFORMANCE = ".upstream/specs/skia-like-realtime/04-performance-tiering-and-release-gates.md"
TARGET_RENDERER = ".upstream/target/skia-like-realtime-renderer-target.md"
TARGET_WGSL = ".upstream/target/high-performance-wgsl-pipeline-target.md"
KAN020_POLICY = "reports/wgsl-pipeline/performance/kan-020-slice-performance-minimum.json"
KAN021_SELECTED = "reports/wgsl-pipeline/m85-resource-lifetime-cache/kan-021-selected-telemetry.json"
KAN048_BUDGETS = "reports/wgsl-pipeline/performance-family-budgets/kan-048-performance-family-budgets.json"
M84_NEGATIVE = "reports/wgsl-pipeline/m84-native-frame-timing/negative-fixture.json"
M85_EVIDENCE = "reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json"
M88_GATE_FREEZE = "reports/wgsl-pipeline/m88-realtime-rc2/gate-freeze.json"
M90_TELEMETRY = "reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json"
M92_CLASSIFICATION = "reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json"
FOR314_SOURCE_MAP = "reports/wgsl-pipeline/runtime-cache-counter-source-map-for314.json"
FOR315_HEADLESS = "reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json"
FOR317_CLOSEOUT = "reports/wgsl-pipeline/runtime-cache-telemetry-closeout-for317.json"

ALLOWED_SOURCE_CLASSES = {"observed", "observed-partial", "derived", "unavailable"}
STABLE_UNAVAILABLE_REASONS = {
    "kadre-runtime.native-cache-counter-unavailable",
    "kadre-runtime.resource-lifetime-observation-partial",
    "kan049.command-pass-counter-unavailable",
}

OBSERVED_HEADLESS_COUNTERS = [
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

M85_DERIVED_COUNTERS = [
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
]

M90_PARTIAL_COUNTERS = [
    "pipelineCacheHits",
    "pipelineCacheMisses",
    "shaderModuleCreates",
    "pipelineCreates",
    "bindGroupCreates",
    "textureUploads",
    "intermediateTextureBytes",
]


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-049 cache telemetry release-gate validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def require_text(root: Path, relative_path: str, snippets: list[str]) -> None:
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    flattened = " ".join(text.split())
    for snippet in snippets:
        require(
            snippet in text or " ".join(snippet.split()) in flattened,
            f"{relative_path} missing required snippet: {snippet}",
        )


def require_object(data: dict[str, Any], field: str, source: str) -> dict[str, Any]:
    value = data.get(field)
    require(isinstance(value, dict), f"{source}.{field} must be an object")
    return value


def require_list(data: dict[str, Any], field: str, source: str) -> list[Any]:
    value = data.get(field)
    require(isinstance(value, list), f"{source}.{field} must be a list")
    return value


def row_id_slug(source_id: str) -> str:
    explicit = {
        "broad-webgpu-cache-hit-callbacks": "broadWebGpuCacheHitCallbacks",
        "bind-group-cache-callbacks": "bindGroupCacheCallbacks",
        "native-resource-free-callbacks": "nativeResourceFreeCallbacks",
        "adapter-owned-memory-snapshots": "adapterOwnedMemorySnapshots",
    }
    if source_id in explicit:
        return explicit[source_id]
    parts = source_id.split("-")
    if not parts:
        return source_id
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


def source_audit(root: Path) -> dict[str, Any]:
    require_text(
        root,
        SPEC_RUNTIME,
        [
            "Resource telemetry per frame:",
            "M85 makes the selected Kadre/WebGPU realtime route auditable",
            "does not claim observed WebGPU runtime cache telemetry",
            "live/resource telemetry classifies counters as `observed`, `observed-partial`, `derived`, or `unavailable`",
        ],
    )
    require_text(
        root,
        SPEC_PERFORMANCE,
        [
            "`observed`",
            "`observed-partial`",
            "`derived`",
            "`unavailable`",
            "must not count derived cache hits/misses as observed WebGPU cache telemetry",
            "Negative Fixtures",
        ],
    )
    require_text(
        root,
        TARGET_RENDERER,
        [
            "it is not observed WebGPU runtime cache telemetry and is not counted as a cache readiness gate",
            "`PipelineKey` axes must represent layout, shader code, resources, or",
        ],
    )
    require_text(
        root,
        TARGET_WGSL,
        [
            "Only the first three categories belong in `PipelineKey`.",
            "GPU caches must be bounded unless their key space is proven finite and small.",
            "minimum pipeline cache hit rate per benchmark scene",
        ],
    )

    kan020 = load_json(root, KAN020_POLICY)
    kan021 = load_json(root, KAN021_SELECTED)
    kan048 = load_json(root, KAN048_BUDGETS)
    for314 = load_json(root, FOR314_SOURCE_MAP)
    for317 = load_json(root, FOR317_CLOSEOUT)
    require(kan020.get("status") == "pass", "KAN-020 policy must remain pass")
    require(kan021.get("status") == "pass", "KAN-021 selected telemetry must remain pass")
    require(kan021.get("countedAsCacheReadinessGate") is False, "KAN-021 cache readiness gate changed")
    require(kan021.get("selectedEvidence", {}).get("observedRuntimeCounters") is False, "KAN-021 selected evidence must remain derived")
    require(kan048.get("status") == "pass", "KAN-048 budgets must remain pass")
    require(kan048.get("releaseBlockingChange") is False, "KAN-048 must not add release-blocking budgets")
    require(for314.get("status") == "pass", "FOR-314 source map must remain pass")
    require(for317.get("status") == "reporting-only-closeout", "FOR-317 closeout must remain reporting-only")
    return {
        "runtimeSpec": SPEC_RUNTIME,
        "performanceSpec": SPEC_PERFORMANCE,
        "targetRenderer": TARGET_RENDERER,
        "targetWgsl": TARGET_WGSL,
        "kan020Policy": KAN020_POLICY,
        "kan021SelectedTelemetry": KAN021_SELECTED,
        "kan048PerformanceFamilyBudgets": KAN048_BUDGETS,
        "for314SourceMap": FOR314_SOURCE_MAP,
        "for317Closeout": FOR317_CLOSEOUT,
    }


def observed_headless_rows(root: Path) -> list[dict[str, Any]]:
    evidence = load_json(root, FOR315_HEADLESS)
    require(evidence.get("sourceClass") == "kanvas-headless-webgpu-observed", "FOR-315 source class changed")
    require(evidence.get("sourceApi") == "SkWebGpuDevice.cacheTelemetrySnapshot()", "FOR-315 source API changed")
    require(evidence.get("notKadreNativeCallbacks") is True, "FOR-315 must stay separate from Kadre callbacks")
    require(evidence.get("broadKadreWgpu4kCallbacksClaimed") is False, "FOR-315 must not claim broad Kadre callbacks")
    require(evidence.get("releaseGate") is False, "FOR-315 must remain non-gating")
    warm = require_object(evidence, "warmSnapshot", FOR315_HEADLESS)
    cold = require_object(evidence, "coldSnapshot", FOR315_HEADLESS)
    rows: list[dict[str, Any]] = []
    for counter in OBSERVED_HEADLESS_COUNTERS:
        require(counter in warm, f"FOR-315 warmSnapshot missing counter: {counter}")
        rows.append(
            {
                "id": f"headless-webgpu.{counter}",
                "counterName": counter,
                "sourceClass": "observed",
                "provenance": {
                    "sourceArtifact": FOR315_HEADLESS,
                    "sourceApi": evidence.get("sourceApi"),
                    "sourceTest": evidence.get("sourceArtifact"),
                    "backend": evidence.get("backend"),
                    "executionMode": evidence.get("executionMode"),
                    "adapter": evidence.get("adapter"),
                },
                "value": warm.get(counter),
                "coldValue": cold.get(counter),
                "lane": "frame.headless-webgpu",
                "scope": "one generated solid-rect warm-cache snapshot",
                "countsAsObservedWebGpuCacheTelemetry": True,
                "countedAsCacheReadinessGate": False,
                "countedAsMeasuredGate": False,
                "releaseBlocking": False,
                "gateTreatment": "reporting-only; eligible for a future candidate only through cacheTelemetry.observed-counter.candidate criteria",
                "quarantineRationale": [
                    "single checked-in headless WebGPU snapshot is not a CI-owned baseline",
                    "variance, host/JDK/backend/adapter eligibility, owner, and negative fixture are not accepted as a release gate here",
                ],
            }
        )
    return rows


def observed_partial_native_rows(root: Path) -> list[dict[str, Any]]:
    evidence = load_json(root, M90_TELEMETRY)
    resources = require_object(evidence, "resources", M90_TELEMETRY)
    live = require_object(resources, "liveRouteCounters", "M90 resources")
    require(live.get("classification") == "observed-partial", "M90 live route classification changed")
    require(live.get("missingCounterReason") == "mep-next.native-cache-counter-unavailable", "M90 missing counter reason changed")
    rows: list[dict[str, Any]] = []
    for counter in M90_PARTIAL_COUNTERS:
        require(counter in live, f"M90 liveRouteCounters missing counter: {counter}")
        rows.append(
            {
                "id": f"native-route.{counter}",
                "counterName": counter,
                "sourceClass": "observed-partial",
                "provenance": {
                    "sourceArtifact": M90_TELEMETRY,
                    "source": "mep-next.cache-observed-partial-native-route",
                    "lane": evidence.get("lane"),
                },
                "value": live.get(counter),
                "lane": evidence.get("lane"),
                "scope": "selected native route creation/churn only",
                "missingCounterFamilies": [
                    "shaderModule",
                    "pipeline",
                    "bindGroup",
                    "resource-cache-hit-miss",
                ],
                "stableMissingCounterReason": "mep-next.native-cache-counter-unavailable",
                "countsAsObservedWebGpuCacheTelemetry": False,
                "countedAsCacheReadinessGate": False,
                "countedAsMeasuredGate": False,
                "releaseBlocking": False,
                "gateTreatment": "reporting-only; not eligible until full observed cache hit/miss counters exist",
            }
        )
    return rows


def m85_derived_rows(root: Path) -> list[dict[str, Any]]:
    m85 = load_json(root, M85_EVIDENCE)
    require(m85.get("observedRuntimeCounters") is False, "M85 observedRuntimeCounters must remain false")
    require(m85.get("countedAsCacheReadinessGate") is False, "M85 countedAsCacheReadinessGate must remain false")
    require(m85.get("counterSource") == "derived-selected-scene-resource-ledger", "M85 counter source changed")
    telemetry = require_object(m85, "perFrameResourceTelemetry", M85_EVIDENCE)
    rows: list[dict[str, Any]] = []
    for counter in M85_DERIVED_COUNTERS:
        require(counter in telemetry, f"M85 telemetry missing counter: {counter}")
        rows.append(
            {
                "id": f"m85-derived.{counter}",
                "counterName": counter,
                "sourceClass": "derived",
                "provenance": {
                    "sourceArtifact": M85_EVIDENCE,
                    "counterSource": m85.get("counterSource"),
                    "sceneContractId": m85.get("sceneContractId"),
                    "lane": m85.get("lane"),
                },
                "value": telemetry.get(counter),
                "lane": m85.get("lane"),
                "scope": "deterministic selected-scene resource/cache ledger",
                "countsAsObservedWebGpuCacheTelemetry": False,
                "countedAsCacheReadinessGate": False,
                "countedAsMeasuredGate": False,
                "releaseBlocking": False,
                "gateTreatment": "PM diagnostics only; cannot count as observed cache readiness",
            }
        )
    return rows


def unavailable_native_rows(root: Path) -> list[dict[str, Any]]:
    m92 = load_json(root, M92_CLASSIFICATION)
    rows = require_list(m92, "rows", M92_CLASSIFICATION)
    blockers = {
        blocker.get("id"): blocker
        for blocker in require_list(m92, "blockers", M92_CLASSIFICATION)
        if isinstance(blocker, dict)
    }
    unavailable_rows: list[dict[str, Any]] = []
    for source_row in rows:
        if not isinstance(source_row, dict) or source_row.get("classification") != "not-observable":
            continue
        blocker_id = source_row.get("blocker")
        require(isinstance(blocker_id, str) and blocker_id in STABLE_UNAVAILABLE_REASONS, f"M92 row missing stable blocker: {source_row.get('id')}")
        blocker = blockers.get(blocker_id, {})
        source_id = str(source_row.get("id"))
        unavailable_rows.append(
            {
                "id": f"native-callbacks.{row_id_slug(source_id)}",
                "counterName": source_id,
                "sourceClass": "unavailable",
                "provenance": {
                    "sourceArtifact": M92_CLASSIFICATION,
                    "sourceRowId": source_id,
                    "blockerOwner": blocker.get("owner"),
                    "unblockCondition": blocker.get("unblockCondition"),
                },
                "lane": "frame.kadre-windowed",
                "scope": "broad native route cache/resource callbacks",
                "stableMissingCounterReason": blocker_id,
                "countsAsObservedWebGpuCacheTelemetry": False,
                "countedAsCacheReadinessGate": False,
                "countedAsMeasuredGate": False,
                "releaseBlocking": False,
                "gateTreatment": "blocked until the missing counter is observable from a named source artifact",
            }
        )

    for counter in ("commandEncoderCount", "renderPassCount"):
        unavailable_rows.append(
            {
                "id": f"native-route.{counter}",
                "counterName": counter,
                "sourceClass": "unavailable",
                "provenance": {
                    "sourceArtifact": SPEC_RUNTIME,
                    "sourceRequirement": "Resource telemetry per frame: command encoder/pass count.",
                    "blockerOwner": "Kanvas WebGPU instrumentation",
                    "unblockCondition": "Serialize command encoder and render-pass counts from the runtime frame telemetry artifact.",
                },
                "lane": "frame.headless-webgpu-or-frame.kadre-windowed",
                "scope": "runtime frame telemetry schema",
                "stableMissingCounterReason": "kan049.command-pass-counter-unavailable",
                "countsAsObservedWebGpuCacheTelemetry": False,
                "countedAsCacheReadinessGate": False,
                "countedAsMeasuredGate": False,
                "releaseBlocking": False,
                "gateTreatment": "unavailable; must not be synthesized from code comments or render-pass diagnostics",
            }
        )
    return unavailable_rows


def build_counter_rows(root: Path) -> list[dict[str, Any]]:
    return (
        observed_headless_rows(root)
        + observed_partial_native_rows(root)
        + m85_derived_rows(root)
        + unavailable_native_rows(root)
    )


def build_negative_fixture() -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "id": "kan049-cache-telemetry-promotion-negative-fixture",
        "status": "expected-fail",
        "mutatesBaseline": False,
        "cases": [
            {
                "id": "m85-derived-ledger-promoted-as-observed",
                "sourceClass": "derived",
                "attemptedPromotion": "observed",
                "expectedFailureReason": "kan049.derived-ledger-not-observed-webgpu-cache-telemetry",
            },
            {
                "id": "m90-observed-partial-native-route-promoted",
                "sourceClass": "observed-partial",
                "attemptedPromotion": "release-blocking",
                "expectedFailureReason": "kan049.observed-partial-missing-full-cache-counter-family",
            },
            {
                "id": "native-callback-unavailable-promoted",
                "sourceClass": "unavailable",
                "attemptedPromotion": "candidate",
                "expectedFailureReason": "kan049.unavailable-counter-missing-observed-source",
            },
            {
                "id": "headless-observed-snapshot-release-blocking-without-policy",
                "sourceClass": "observed",
                "attemptedPromotion": "release-blocking",
                "expectedFailureReason": "kan049.observed-counter-missing-release-gate-policy",
            },
        ],
        "assertion": "Counters that are derived, observed-partial, unavailable, or observed without an accepted gate policy fail promotion without rewriting baselines.",
    }


def build_gate_freeze_delta(root: Path) -> dict[str, Any]:
    freeze = load_json(root, M88_GATE_FREEZE)
    require(freeze.get("status") == "pass", "M88 gate freeze must remain pass")
    performance_gates = require_list(freeze, "performanceGates", M88_GATE_FREEZE)
    names = [str(gate.get("name")) for gate in performance_gates if isinstance(gate, dict)]
    m85_gate = next((gate for gate in performance_gates if isinstance(gate, dict) and gate.get("name") == "m85 resource/cache ledger"), None)
    require(isinstance(m85_gate, dict), "M88 gate freeze missing M85 resource/cache ledger")
    require(m85_gate.get("phase") == "reporting-only", "M85 gate phase must remain reporting-only")
    return {
        "schemaVersion": 1,
        "id": "kan049-cache-telemetry-gate-freeze-delta",
        "status": "pass",
        "sourceArtifact": M88_GATE_FREEZE,
        "baselinePerformanceGates": names,
        "addedValidationOnlyGates": ["validateKan049CacheTelemetryReleaseGateCriteria"],
        "changedReleaseBlockingGates": [],
        "promotedCacheTelemetryGates": [],
        "phaseDeltas": [
            {
                "gate": "m85 resource/cache ledger",
                "before": "reporting-only",
                "after": "reporting-only",
                "reason": "M85 remains a derived selected-scene ledger, not observed WebGPU runtime cache telemetry.",
            },
            {
                "gate": "headless WebGPU cacheTelemetrySnapshot",
                "before": "not-listed-as-release-gate",
                "after": "reporting-only-classification-only",
                "reason": "FOR-315 is observed evidence but lacks accepted baseline, variance, owner, and release-gate negative fixture policy.",
            },
        ],
        "nonClaims": [
            "KAN-049 does not add a release-blocking cache telemetry gate.",
            "KAN-049 does not promote frame.kadre-windowed timing to release-grade FPS.",
            "KAN-049 does not count M85 derived ledgers as observed WebGPU cache telemetry.",
        ],
    }


def build_gate_criteria() -> dict[str, Any]:
    return {
        "candidate": {
            "name": "cacheTelemetry.observed-counter.candidate",
            "allowedSourceClass": "observed",
            "requires": [
                "named observed source artifact from headless/native runtime execution",
                "host/JDK/backend/adapter metadata",
                "CI-owned baseline and eligible adapter policy",
                "variance policy with quarantine owner",
                "negative fixture proving the failure path",
                "explicit exclusion of derived M85 ledgers and observed-partial native churn",
            ],
            "forbidden": [
                "derived selected-scene ledgers counted as observed telemetry",
                "observed-partial native route creation/churn counted as cache hit/miss telemetry",
                "unavailable counters filled by estimates",
            ],
        },
        "releaseBlocking": {
            "name": "cacheTelemetry.observed-counter.release-blocking",
            "allowedSourceClass": "observed",
            "requires": [
                "candidate gate accepted by release owner",
                "threshold, baseline commit, owner, and rollback policy",
                "negative fixture in CI that fails without mutating checked-in baselines",
                "quarantine/rebaseline policy for host, JDK, backend, and adapter mismatch",
                "metadata violation fails the selected gate",
            ],
            "forbidden": [
                "skipping reporting-only or candidate phases",
                "promoting frame.kadre-windowed without owned hardware and accepted variance",
                "moving readiness from cache telemetry with derived or unavailable counters",
            ],
        },
    }


def build_gate_candidates() -> list[dict[str, Any]]:
    return [
        {
            "id": "headless-webgpu-cacheTelemetrySnapshot",
            "sourceClass": "observed",
            "phase": "reporting-only",
            "candidateCriteriaName": "cacheTelemetry.observed-counter.candidate",
            "releaseBlockingCriteriaName": "cacheTelemetry.observed-counter.release-blocking",
            "negativeFixture": f"{DEFAULT_OUTPUT_DIR}/{OUTPUT_NEGATIVE_FIXTURE}",
            "quarantineRationale": [
                "FOR-315 is a checked-in single-scenario snapshot, not a CI-owned baseline.",
                "Adapter eligibility, variance, threshold, owner, and rollback policy are not accepted here.",
            ],
            "releaseBlocking": False,
        }
    ]


def claim_guard(evidence: dict[str, Any]) -> dict[str, list[str]]:
    rows = [row for row in evidence.get("counterRows", []) if isinstance(row, dict)]
    guard: dict[str, list[str]] = {
        "invalidSourceClasses": [],
        "missingSourceClasses": [],
        "rowsMissingProvenance": [],
        "observedRowsMissingArtifact": [],
        "unavailableRowsMissingReason": [],
        "derivedRowsCountedObserved": [],
        "m85DerivedLedgerCountedObserved": [],
        "observedPartialRowsPromoted": [],
        "releaseBlockingRows": [],
        "cacheReadinessGateRows": [],
        "candidateRowsMissingFixtureOrQuarantine": [],
        "gateFreezeReleaseBlockingDelta": [],
        "missingRequiredRows": [],
    }
    present_classes = {str(row.get("sourceClass")) for row in rows}
    guard["missingSourceClasses"] = sorted(ALLOWED_SOURCE_CLASSES - present_classes)
    required_ids = {
        "headless-webgpu.pipelineCacheHits",
        "m85-derived.pipelineCacheHits",
        "native-route.pipelineCreates",
        "native-callbacks.broadWebGpuCacheHitCallbacks",
        "native-route.commandEncoderCount",
        "native-route.renderPassCount",
    }
    present_ids = {str(row.get("id")) for row in rows}
    guard["missingRequiredRows"] = sorted(required_ids - present_ids)
    for row in rows:
        row_id = str(row.get("id"))
        source_class = str(row.get("sourceClass"))
        if source_class not in ALLOWED_SOURCE_CLASSES:
            guard["invalidSourceClasses"].append(row_id)
        provenance = row.get("provenance")
        if not isinstance(provenance, dict) or not provenance:
            guard["rowsMissingProvenance"].append(row_id)
        if source_class == "observed":
            artifact = provenance.get("sourceArtifact") if isinstance(provenance, dict) else None
            if not isinstance(artifact, str) or not artifact:
                guard["observedRowsMissingArtifact"].append(row_id)
        if source_class == "unavailable":
            reason = row.get("stableMissingCounterReason")
            if not isinstance(reason, str) or reason not in STABLE_UNAVAILABLE_REASONS:
                guard["unavailableRowsMissingReason"].append(row_id)
        if source_class == "derived" and row.get("countsAsObservedWebGpuCacheTelemetry") is True:
            guard["derivedRowsCountedObserved"].append(row_id)
        if row_id.startswith("m85-derived.") and (
            source_class != "derived" or row.get("countsAsObservedWebGpuCacheTelemetry") is True
        ):
            guard["m85DerivedLedgerCountedObserved"].append(row_id)
        if source_class == "observed-partial" and (
            row.get("releaseBlocking") is True or row.get("countedAsCacheReadinessGate") is True
        ):
            guard["observedPartialRowsPromoted"].append(row_id)
        if row.get("releaseBlocking") is True:
            guard["releaseBlockingRows"].append(row_id)
        if row.get("countedAsCacheReadinessGate") is True:
            guard["cacheReadinessGateRows"].append(row_id)

    for candidate in evidence.get("gateCandidates", []):
        if not isinstance(candidate, dict):
            guard["candidateRowsMissingFixtureOrQuarantine"].append("<non-object>")
            continue
        if not candidate.get("negativeFixture") or not candidate.get("quarantineRationale"):
            guard["candidateRowsMissingFixtureOrQuarantine"].append(str(candidate.get("id")))

    freeze = evidence.get("gateFreezeDelta") if isinstance(evidence.get("gateFreezeDelta"), dict) else {}
    changed = freeze.get("changedReleaseBlockingGates") if isinstance(freeze, dict) else None
    if changed:
        guard["gateFreezeReleaseBlockingDelta"].extend(str(item) for item in changed)
    promoted = freeze.get("promotedCacheTelemetryGates") if isinstance(freeze, dict) else None
    if promoted:
        guard["gateFreezeReleaseBlockingDelta"].extend(str(item) for item in promoted)
    return guard


def summarize(rows: list[dict[str, Any]], guard: dict[str, list[str]]) -> dict[str, int]:
    return {
        "counterRows": len(rows),
        "observedRows": sum(1 for row in rows if row.get("sourceClass") == "observed"),
        "observedPartialRows": sum(1 for row in rows if row.get("sourceClass") == "observed-partial"),
        "derivedRows": sum(1 for row in rows if row.get("sourceClass") == "derived"),
        "unavailableRows": sum(1 for row in rows if row.get("sourceClass") == "unavailable"),
        "releaseBlockingRows": len(guard["releaseBlockingRows"]),
        "m85DerivedLedgerCountedObserved": len(guard["m85DerivedLedgerCountedObserved"]),
        "unavailableRowsMissingReason": len(guard["unavailableRowsMissingReason"]),
    }


def build_evidence(root: Path) -> dict[str, Any]:
    source_audit_payload = source_audit(root)
    rows = build_counter_rows(root)
    negative_fixture = build_negative_fixture()
    gate_freeze_delta = build_gate_freeze_delta(root)
    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-049",
        "packId": "kan-049-cache-telemetry-release-gate-criteria",
        "status": "pass",
        "claimLevel": "cache-telemetry-release-gate-criteria",
        "releaseBlockingChange": False,
        "readinessDelta": 0,
        "counterRows": rows,
        "gateCriteria": build_gate_criteria(),
        "gateCandidates": build_gate_candidates(),
        "negativeFixture": negative_fixture,
        "gateFreezeDelta": gate_freeze_delta,
        "sourceAudit": source_audit_payload,
        "nonClaims": [
            "KAN-049 does not add a release-blocking cache telemetry gate.",
            "KAN-049 does not claim arbitrary scene cache readiness.",
            "KAN-049 does not claim device-lost recovery.",
            "KAN-049 does not promote frame.kadre-windowed FPS to release-grade.",
            "KAN-049 does not count M85 derived ledgers as observed WebGPU cache telemetry.",
            "KAN-049 does not count observed-partial native route creation/churn as broad cache hit/miss telemetry.",
        ],
        "requiredValidation": [
            "validateKan049CacheTelemetryReleaseGateCriteria",
            "pipelineConformance",
            "pipelinePmBundle",
        ],
    }
    guard = claim_guard(evidence)
    evidence["claimGuard"] = guard
    evidence["summary"] = summarize(rows, guard)
    validate_evidence(evidence, root)
    return evidence


def validate_provenance_paths(evidence: dict[str, Any], root: Path) -> None:
    for row in evidence.get("counterRows", []):
        if not isinstance(row, dict):
            fail("counter row must be an object")
        provenance = row.get("provenance")
        if not isinstance(provenance, dict):
            fail(f"counter row missing provenance: {row.get('id')}")
        artifact = provenance.get("sourceArtifact")
        if isinstance(artifact, str):
            require((root / artifact).is_file(), f"missing source artifact for {row.get('id')}: {artifact}")
        for artifact in provenance.get("sourceArtifacts", []):
            require(isinstance(artifact, str) and (root / artifact).is_file(), f"missing source artifact for {row.get('id')}: {artifact}")


def validate_evidence(evidence: dict[str, Any], root: Path) -> None:
    require(evidence.get("ticket") == "KAN-049", "ticket id changed")
    require(evidence.get("releaseBlockingChange") is False, "releaseBlockingChange must remain false")
    require(evidence.get("readinessDelta") == 0, "readinessDelta must remain zero")
    rows = evidence.get("counterRows")
    require(isinstance(rows, list) and rows, "counterRows missing")
    guard = claim_guard(evidence)
    if guard["missingRequiredRows"]:
        fail(f"missing required counter row: {guard['missingRequiredRows']}")
    if guard["invalidSourceClasses"]:
        fail(f"invalid source class: {guard['invalidSourceClasses']}")
    if guard["missingSourceClasses"]:
        fail(f"missing source classes: {guard['missingSourceClasses']}")
    if guard["rowsMissingProvenance"]:
        fail(f"counter missing provenance: {guard['rowsMissingProvenance']}")
    if guard["observedRowsMissingArtifact"]:
        fail(f"observed counter missing source artifact: {guard['observedRowsMissingArtifact']}")
    if guard["unavailableRowsMissingReason"]:
        fail(f"unavailable counter missing stable reason: {guard['unavailableRowsMissingReason']}")
    if guard["m85DerivedLedgerCountedObserved"]:
        fail(f"M85 derived ledger counted as observed: {guard['m85DerivedLedgerCountedObserved']}")
    if guard["derivedRowsCountedObserved"]:
        fail(f"derived counter counted as observed: {guard['derivedRowsCountedObserved']}")
    if guard["observedPartialRowsPromoted"]:
        fail(f"observed-partial counter promoted: {guard['observedPartialRowsPromoted']}")
    if guard["releaseBlockingRows"]:
        fail(f"release-blocking counter: {guard['releaseBlockingRows']}")
    if guard["cacheReadinessGateRows"]:
        fail(f"cache readiness gate counter: {guard['cacheReadinessGateRows']}")
    if guard["candidateRowsMissingFixtureOrQuarantine"]:
        fail(f"gate candidate missing negative fixture or quarantine rationale: {guard['candidateRowsMissingFixtureOrQuarantine']}")
    if guard["gateFreezeReleaseBlockingDelta"]:
        fail(f"gate freeze delta promoted cache telemetry: {guard['gateFreezeReleaseBlockingDelta']}")

    gate_criteria = evidence.get("gateCriteria")
    require(isinstance(gate_criteria, dict), "gateCriteria missing")
    for key in ("candidate", "releaseBlocking"):
        criteria = gate_criteria.get(key)
        require(isinstance(criteria, dict), f"gateCriteria.{key} missing")
        require(isinstance(criteria.get("name"), str) and criteria.get("name"), f"gateCriteria.{key}.name missing")
        require(criteria.get("allowedSourceClass") == "observed", f"gateCriteria.{key} must only allow observed source class")
    candidate_text = " ".join(str(item) for item in gate_criteria["candidate"].get("requires", []))
    require("negative fixture" in candidate_text, "candidate criteria must name negative fixture")

    fixture = evidence.get("negativeFixture")
    require(isinstance(fixture, dict), "negativeFixture missing")
    require(fixture.get("status") == "expected-fail", "negative fixture must be expected-fail")
    require(fixture.get("mutatesBaseline") is False, "negative fixture must not mutate baselines")
    fixture_cases = fixture.get("cases")
    require(isinstance(fixture_cases, list) and len(fixture_cases) >= 4, "negative fixture cases missing")
    for case in fixture_cases:
        require(isinstance(case, dict), "negative fixture case must be object")
        require(case.get("expectedFailureReason"), f"negative fixture case missing reason: {case.get('id')}")

    freeze = evidence.get("gateFreezeDelta")
    require(isinstance(freeze, dict), "gateFreezeDelta missing")
    require(freeze.get("changedReleaseBlockingGates") == [], "gate freeze changed release-blocking gates")
    require(freeze.get("promotedCacheTelemetryGates") == [], "gate freeze promoted cache telemetry gates")
    require("m85 resource/cache ledger" in freeze.get("baselinePerformanceGates", []), "gate freeze missing M85 baseline gate")

    validate_provenance_paths(evidence, root)
    expected = evidence.get("summary")
    if isinstance(expected, dict):
        summary = summarize([row for row in rows if isinstance(row, dict)], guard)
        for key, value in summary.items():
            require(expected.get(key) == value, f"summary mismatch for {key}: expected {value}, found {expected.get(key)}")


def markdown_table(headers: list[str], rows: list[list[Any]]) -> str:
    out = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
    ]
    for row in rows:
        out.append("| " + " | ".join(str(cell) for cell in row) + " |")
    return "\n".join(out)


def render_markdown(evidence: dict[str, Any]) -> str:
    summary = evidence["summary"]
    rows = evidence["counterRows"]
    table = markdown_table(
        ["Counter", "Class", "Lane", "Gate treatment", "Source"],
        [
            [
                row["id"],
                f'`{row["sourceClass"]}`',
                row.get("lane", ""),
                row.get("gateTreatment", ""),
                row.get("provenance", {}).get("sourceArtifact", ""),
            ]
            for row in rows
        ],
    )
    criteria = evidence["gateCriteria"]
    return f"""# KAN-049 Cache Telemetry Release-Gate Criteria

Status: `{evidence["status"]}`

KAN-049 classifies cache/resource telemetry counters and freezes release-gate
promotion rules without adding a release-blocking cache telemetry gate.

## Summary

| Metric | Value |
| --- | ---: |
| Counter rows | `{summary["counterRows"]}` |
| Observed rows | `{summary["observedRows"]}` |
| Observed-partial rows | `{summary["observedPartialRows"]}` |
| Derived rows | `{summary["derivedRows"]}` |
| Unavailable rows | `{summary["unavailableRows"]}` |
| Release-blocking rows | `{summary["releaseBlockingRows"]}` |
| M85 derived rows counted observed | `{summary["m85DerivedLedgerCountedObserved"]}` |

## Counter Classification

{table}

## Gate Criteria

- Candidate name: `{criteria["candidate"]["name"]}`.
- Release-blocking name: `{criteria["releaseBlocking"]["name"]}`.
- Candidate promotion only allows `observed` counters with named source
  artifacts, metadata, variance policy, owner, quarantine rationale, and a
  negative fixture.
- Derived M85 ledgers, observed-partial native churn, and unavailable counters
  remain non-gating.

## Negative Fixture

`{DEFAULT_OUTPUT_DIR}/{OUTPUT_NEGATIVE_FIXTURE}` records expected-fail
promotion attempts for derived, observed-partial, unavailable, and unowned
observed cache telemetry.

## Gate-Freeze Delta

`{DEFAULT_OUTPUT_DIR}/{OUTPUT_GATE_FREEZE_DELTA}` records no changed
release-blocking gates and no promoted cache telemetry gates.

## Non-Claims

{chr(10).join(f"- {item}" for item in evidence["nonClaims"])}
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(json.dumps(evidence, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    (output_dir / OUTPUT_NEGATIVE_FIXTURE).write_text(
        json.dumps(evidence["negativeFixture"], indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    (output_dir / OUTPUT_GATE_FREEZE_DELTA).write_text(
        json.dumps(evidence["gateFreezeDelta"], indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    return evidence


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path.cwd().resolve()
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    try:
        evidence = write_outputs(root, output_dir)
    except ValidationError as exc:
        print(exc, file=sys.stderr)
        return 1
    print(
        f"KAN-049 cache telemetry release-gate criteria PASS: "
        f"{evidence['summary']['counterRows']} counter rows "
        f"({evidence['summary']['observedRows']} observed, "
        f"{evidence['summary']['observedPartialRows']} observed-partial, "
        f"{evidence['summary']['derivedRows']} derived, "
        f"{evidence['summary']['unavailableRows']} unavailable)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
