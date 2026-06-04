#!/usr/bin/env python3
"""Generate and validate the FOR-317 runtime cache telemetry closeout."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-317"
SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-runtime-cache-telemetry-closeout-ticket"

REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-317-runtime-cache-telemetry-closeout.md"
ARTIFACT = PROJECT_ROOT / "reports/wgsl-pipeline/runtime-cache-telemetry-closeout-for317.json"

FOR310_JSON = PROJECT_ROOT / "reports/wgsl-pipeline/m90-runtime-interactive/m90-runtime-telemetry-classification-guard-for310.json"
FOR311_JSON = PROJECT_ROOT / "reports/wgsl-pipeline/m92-kadre-runtime-rc/m92-rc-headless-kadre-dependency-policy-for311.json"
FOR312_JSON = PROJECT_ROOT / "reports/wgsl-pipeline/pm-bundle-runtime-headless-manifest-for312.json"
FOR313_JSON = PROJECT_ROOT / "reports/wgsl-pipeline/runtime-docs-kadre-headless-policy-for313.json"
FOR314_JSON = PROJECT_ROOT / "reports/wgsl-pipeline/runtime-cache-counter-source-map-for314.json"
FOR315_JSON = PROJECT_ROOT / "reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json"

FOR310_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-310-m90-runtime-telemetry-classification-guard.md"
FOR311_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-311-m92-rc-headless-kadre-dependency-policy.md"
FOR312_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-312-pm-bundle-runtime-headless-manifest.md"
FOR313_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-313-runtime-docs-kadre-headless-policy.md"
FOR314_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-314-runtime-cache-counter-source-map.md"
FOR315_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-315-headless-webgpu-cache-counters.md"
FOR316_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-316-runtime-cache-counter-evidence-bridge.md"

M85_EVIDENCE = PROJECT_ROOT / "reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json"
M90_TELEMETRY = PROJECT_ROOT / "reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json"
M92_CLASSIFICATION = PROJECT_ROOT / "reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json"

DECISION_APPLIED = "RUNTIME_CACHE_TELEMETRY_CLOSEOUT_APPLIED"
DECISION_MISSING_EVIDENCE = "RUNTIME_CACHE_TELEMETRY_CLOSEOUT_MISSING_EVIDENCE"
DECISION_UNSAFE = "RUNTIME_CACHE_TELEMETRY_CLOSEOUT_UNSAFE_CLAIM_FOUND"

SOURCE_API = "SkWebGpuDevice.cacheTelemetrySnapshot()"
FOR315_SOURCE_CLASS = "kanvas-headless-webgpu-observed"
OBSERVED_CLASSES = {"observed", "observed-runtime-cache-telemetry"}

REQUIRED_NON_CHANGES = {
    "readiness": "unchanged",
    "score": "unchanged",
    "releaseGateStatus": "unchanged",
    "rendererBehavior": "unchanged",
    "runtimeBehavior": "unchanged",
    "gradle": "unchanged",
    "shaders": "unchanged",
    "thresholds": "unchanged",
    "sceneStatus": "unchanged",
    "fallbacks": "unchanged",
    "kadreNativeBehavior": "unchanged",
    "kadreProvisioning": "unchanged",
    "supportClaims": "no-new-support-or-rendering-claim",
    "instrumentation": "no-new-runtime-instrumentation",
}

PROTECTED_M92_ROWS = {
    "shader-module-creates-selected-route": "observed-partial",
    "pipeline-creates-selected-route": "observed-partial",
    "pipeline-cache-hits-misses-selected-ledger": "derived",
    "broad-webgpu-cache-hit-callbacks": "not-observable",
    "bind-group-cache-callbacks": "not-observable",
    "native-resource-free-callbacks": "not-observable",
    "adapter-owned-memory-snapshots": "not-observable",
}

TICKET_HISTORY = {
    "FOR-310": {
        "pullRequest": 1402,
        "commits": [
            "d3263164191355189a5a6c00ccf40559a21212dc",
            "9c3ed8c192a28baccf72068c3506f75af6a49613",
        ],
    },
    "FOR-311": {
        "pullRequest": 1403,
        "commits": [
            "c2eab384cb06b8fc141d6ff1ac154d3bb6b31a68",
            "22765e247785819945a92623aeaf3a3e99864522",
        ],
    },
    "FOR-312": {
        "pullRequest": 1404,
        "commits": [
            "cde6f3a4a28569f8a3b0add11c066c12a057693a",
            "bb041e795f02507aaf79aa4dc1b446cd280fc434",
        ],
    },
    "FOR-313": {
        "pullRequest": 1405,
        "commits": [
            "9385f438c5a67e60954ed390125074eaad8a96e7",
            "7fad651b616b48aff7974011322f1c7a63db1933",
        ],
    },
    "FOR-314": {
        "pullRequest": 1406,
        "commits": [
            "1bf5ff69e464f8710022675efa2cb3e037276f00",
            "a5a5d3bcc7c0d8301d63d722dc26f4ff90f2e194",
        ],
    },
    "FOR-315": {
        "pullRequest": 1407,
        "commits": [
            "970eff34eeb60e9c36c6dcb8ecdd8804f53cb2d7",
            "a7ac5eb34b0b0c996cca43b84b0ea7188cab7896",
        ],
    },
    "FOR-316": {
        "pullRequest": 1408,
        "commits": [
            "c67433d37ed42ae66b9618ae3cd6f65b76a940dc",
            "e90157d73f1758535e2a2273dfc0e9d4eb98c37d",
        ],
    },
}


def fail(message: str, decision: str = DECISION_MISSING_EVIDENCE) -> None:
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


def assert_non_changes(source: dict[str, Any], source_path: Path, fields: list[str]) -> None:
    non_changes = require_object(source, "nonChanges", source_path)
    for field in fields:
        if non_changes.get(field) != "unchanged":
            fail(f"{rel(source_path)} nonChanges.{field} must remain unchanged", DECISION_UNSAFE)


def validate_expected_artifacts() -> dict[str, str]:
    expected = [
        FOR310_JSON,
        FOR311_JSON,
        FOR312_JSON,
        FOR313_JSON,
        FOR314_JSON,
        FOR315_JSON,
        FOR310_REPORT,
        FOR311_REPORT,
        FOR312_REPORT,
        FOR313_REPORT,
        FOR314_REPORT,
        FOR315_REPORT,
        FOR316_REPORT,
        M85_EVIDENCE,
        M90_TELEMETRY,
        M92_CLASSIFICATION,
        PROJECT_ROOT / "scripts/validate_for310_m90_runtime_telemetry_classification_guard.py",
        PROJECT_ROOT / "scripts/validate_for311_m92_rc_headless_kadre_dependency_policy.py",
        PROJECT_ROOT / "scripts/validate_for312_pm_bundle_runtime_headless_manifest.py",
        PROJECT_ROOT / "scripts/validate_for313_runtime_docs_kadre_headless_policy.py",
        PROJECT_ROOT / "scripts/validate_for314_runtime_cache_counter_source_map.py",
        PROJECT_ROOT / "scripts/validate_for315_headless_webgpu_cache_counters.py",
        PROJECT_ROOT / "scripts/validate_for316_runtime_cache_counter_evidence_bridge.py",
    ]
    present: dict[str, str] = {}
    for path in expected:
        if not path.is_file():
            fail(f"missing expected FOR-310..FOR-316 artifact: {rel(path)}")
        present[rel(path)] = "present"
    return present


def validate_ticket_decisions() -> dict[str, dict[str, Any]]:
    for310 = load_json(FOR310_JSON)
    for311 = load_json(FOR311_JSON)
    for312 = load_json(FOR312_JSON)
    for313 = load_json(FOR313_JSON)
    for314 = load_json(FOR314_JSON)
    for315 = load_json(FOR315_JSON)

    expected = {
        "FOR-310": (for310, FOR310_JSON, "linear", "M90_RUNTIME_TELEMETRY_CLASSIFICATION_GUARD_APPLIED"),
        "FOR-311": (for311, FOR311_JSON, "linear", "M92_RC_HEADLESS_KADRE_DEPENDENCY_POLICY_GUARD_APPLIED"),
        "FOR-312": (for312, FOR312_JSON, "linear", "PM_BUNDLE_RUNTIME_HEADLESS_MANIFEST_AUDIT_APPLIED"),
        "FOR-313": (for313, FOR313_JSON, "linear", "M90_RUNTIME_DOCS_KADRE_HEADLESS_POLICY_AUDIT_APPLIED"),
        "FOR-314": (for314, FOR314_JSON, "linearIssue", None),
        "FOR-315": (for315, FOR315_JSON, "linearIssue", None),
    }
    decisions: dict[str, dict[str, Any]] = {}
    for ticket, (data, source, id_field, decision) in expected.items():
        if data.get(id_field) != ticket:
            fail(f"{rel(source)} expected {id_field}={ticket!r}, got {data.get(id_field)!r}")
        if decision is not None and data.get("decision") != decision:
            fail(f"{rel(source)} expected decision={decision!r}, got {data.get('decision')!r}")
        decisions[ticket] = {
            "decision": data.get("decision", "structured-evidence-without-top-level-decision"),
            "json": rel(source),
        }

    if for315.get("status") != "pass":
        fail("FOR-315 observed artifact must remain pass")
    if for315.get("sourceClass") != FOR315_SOURCE_CLASS:
        fail("FOR-315 source class must remain kanvas-headless-webgpu-observed", DECISION_UNSAFE)
    if for315.get("sourceApi") != SOURCE_API:
        fail("FOR-315 source API must remain cacheTelemetrySnapshot()", DECISION_UNSAFE)
    if for315.get("notKadreNativeCallbacks") is not True:
        fail("FOR-315 must stay separate from Kadre native callbacks", DECISION_UNSAFE)
    if for315.get("broadKadreWgpu4kCallbacksClaimed") is not False:
        fail("FOR-315 must not claim broad Kadre/wgpu4k callbacks", DECISION_UNSAFE)
    if for315.get("releaseGate") is not False or for315.get("readinessGateChanged") is not False:
        fail("FOR-315 must not change release/readiness gates", DECISION_UNSAFE)
    assert_non_changes(
        for315,
        FOR315_JSON,
        [
            "rendererBehavior",
            "gradle",
            "shaders",
            "thresholds",
            "sceneStatus",
            "readiness",
            "releaseGateStatus",
            "fallbacks",
            "kadreNativeBehavior",
        ],
    )

    require_snippets(
        FOR316_REPORT,
        [
            "FOR-316",
            "RUNTIME_CACHE_COUNTER_EVIDENCE_BRIDGE_APPLIED",
            "FOR-315",
            "SkWebGpuDevice.cacheTelemetrySnapshot()",
            "callback cache Kadre/wgpu4k natif large",
            "Readiness: unchanged",
            "Release gate status: unchanged",
            "Score: unchanged",
        ],
    )
    decisions["FOR-316"] = {
        "decision": "RUNTIME_CACHE_COUNTER_EVIDENCE_BRIDGE_APPLIED",
        "report": rel(FOR316_REPORT),
        "structuredSource": rel(FOR314_JSON) + "#kanvasHeadlessWebGpuObservedCandidate.observedEvidenceBridge",
    }
    return decisions


def validate_runtime_boundaries() -> dict[str, Any]:
    source_map = load_json(FOR314_JSON)
    for315 = load_json(FOR315_JSON)

    assert_non_changes(
        source_map,
        FOR314_JSON,
        [
            "readiness",
            "score",
            "releaseGateStatus",
            "rendererBehavior",
            "shaders",
            "thresholds",
            "fallbacks",
            "kadreNativeBehavior",
            "gradle",
            "sceneStatus",
        ],
    )

    m85 = require_object(source_map, "m85DerivedLedger", FOR314_JSON)
    if m85.get("sourceClass") != "derived":
        fail("M85 ledger must remain derived", DECISION_UNSAFE)
    if m85.get("observedRuntimeCounters") is not False:
        fail("M85 must not become observed runtime cache telemetry", DECISION_UNSAFE)

    candidate = require_object(source_map, "kanvasHeadlessWebGpuObservedCandidate", FOR314_JSON)
    if candidate.get("sourceClass") != "kanvas-headless-webgpu-observed-candidate":
        fail("FOR-314 candidate source class changed", DECISION_UNSAFE)
    if candidate.get("snapshotFunction") != SOURCE_API:
        fail("FOR-314 snapshot function must remain cacheTelemetrySnapshot()", DECISION_UNSAFE)
    bridge = require_object(candidate, "observedEvidenceBridge", FOR314_JSON)
    expected_bridge = {
        "linearIssue": "FOR-315",
        "sourceArtifact": rel(FOR315_JSON),
        "sourceClass": FOR315_SOURCE_CLASS,
        "sourceApi": SOURCE_API,
        "backend": "WebGPU",
        "executionMode": "headless",
        "notKadreNativeCallbacks": True,
        "broadKadreWgpu4kCallbacksClaimed": False,
        "releaseGate": False,
        "readinessGateChanged": False,
    }
    for field, expected in expected_bridge.items():
        if bridge.get(field) != expected:
            fail(f"FOR-314 observedEvidenceBridge.{field} expected {expected!r}, got {bridge.get(field)!r}", DECISION_UNSAFE)
    if not isinstance(bridge.get("coldPipelineCacheMisses"), int) or bridge["coldPipelineCacheMisses"] < 1:
        fail("FOR-314 observedEvidenceBridge must preserve cold pipelineCacheMisses >= 1", DECISION_UNSAFE)
    if not isinstance(bridge.get("warmPipelineCacheHits"), int) or not isinstance(bridge.get("coldPipelineCacheHits"), int):
        fail("FOR-314 observedEvidenceBridge must preserve integer cache hit counters", DECISION_UNSAFE)
    if bridge["warmPipelineCacheHits"] <= bridge["coldPipelineCacheHits"]:
        fail("FOR-314 observedEvidenceBridge warm pipeline hits must increase", DECISION_UNSAFE)

    cold = require_object(for315, "coldSnapshot", FOR315_JSON)
    warm = require_object(for315, "warmSnapshot", FOR315_JSON)
    if cold.get("pipelineCacheMisses") != bridge["coldPipelineCacheMisses"]:
        fail("FOR-315 cold miss count no longer matches FOR-314 bridge", DECISION_UNSAFE)
    if warm.get("pipelineCacheHits") != bridge["warmPipelineCacheHits"]:
        fail("FOR-315 warm hit count no longer matches FOR-314 bridge", DECISION_UNSAFE)

    m90 = require_object(source_map, "m90ObservedPartialNativeRoute", FOR314_JSON)
    m90_classification = require_object(m90, "sourceClassification", FOR314_JSON)
    if m90_classification.get("cacheHitsMisses") != "derived":
        fail("M90 cache hits/misses must remain derived", DECISION_UNSAFE)
    if m90_classification.get("nativeRouteAllocations") != "observed-partial":
        fail("M90 native route allocations must remain observed-partial", DECISION_UNSAFE)
    live = require_object(m90, "liveRouteCounters", FOR314_JSON)
    if live.get("sourceClass") != "observed-partial":
        fail("M90 live route counters must remain observed-partial", DECISION_UNSAFE)

    telemetry = load_json(M90_TELEMETRY)
    telemetry_classification = require_object(telemetry, "sourceClassification", M90_TELEMETRY)
    if telemetry_classification.get("cacheHitsMisses") != "derived":
        fail("M90 telemetry cache hits/misses must remain derived", DECISION_UNSAFE)
    if telemetry_classification.get("nativeRouteAllocations") != "observed-partial":
        fail("M90 telemetry native route allocations must remain observed-partial", DECISION_UNSAFE)

    m92_source = require_object(source_map, "m92NotObservableKadreBlockers", FOR314_JSON)
    if m92_source.get("sourceClass") != "not-observable-kadre-blockers-with-observed-partial-creation-rows":
        fail("M92 source class changed", DECISION_UNSAFE)
    m92 = load_json(M92_CLASSIFICATION)
    rows = require_list(m92, "rows", M92_CLASSIFICATION)
    protected: dict[str, dict[str, Any]] = {}
    for row in rows:
        if not isinstance(row, dict):
            fail("M92 rows must be JSON objects", DECISION_UNSAFE)
        row_id = row.get("id")
        if row_id in PROTECTED_M92_ROWS:
            protected[str(row_id)] = row
    missing = sorted(set(PROTECTED_M92_ROWS) - set(protected))
    if missing:
        fail(f"M92 classification missing protected rows: {missing}", DECISION_UNSAFE)
    for row_id, expected_class in PROTECTED_M92_ROWS.items():
        row = protected[row_id]
        current = row.get("classification")
        if current != expected_class:
            fail(f"M92 row {row_id} must remain {expected_class}, got {current}", DECISION_UNSAFE)
        if row_id in {
            "broad-webgpu-cache-hit-callbacks",
            "bind-group-cache-callbacks",
            "native-resource-free-callbacks",
            "adapter-owned-memory-snapshots",
        } and current in OBSERVED_CLASSES:
            fail(f"M92 broad callback row {row_id} became observed", DECISION_UNSAFE)
        if row.get("releaseGate") is not False:
            fail(f"M92 row {row_id} must remain reporting-only", DECISION_UNSAFE)

    blockers = require_list(m92, "blockers", M92_CLASSIFICATION)
    blocker_ids = {
        str(item.get("id"))
        for item in blockers
        if isinstance(item, dict) and isinstance(item.get("id"), str)
    }
    for blocker in [
        "kadre-runtime.native-cache-counter-unavailable",
        "kadre-runtime.resource-lifetime-observation-partial",
    ]:
        if blocker not in blocker_ids:
            fail(f"M92 blocker {blocker} must remain present", DECISION_UNSAFE)

    return {
        "observed": [
            {
                "id": "for315.kanvas-headless-webgpu-cache-telemetry-snapshot",
                "sourceClass": FOR315_SOURCE_CLASS,
                "sourceApi": SOURCE_API,
                "sourceArtifact": rel(FOR315_JSON),
                "adapter": for315.get("adapter"),
                "coldPipelineCacheMisses": cold.get("pipelineCacheMisses"),
                "coldPipelineCacheHits": cold.get("pipelineCacheHits"),
                "warmPipelineCacheHits": warm.get("pipelineCacheHits"),
                "boundary": "Kanvas headless WebGPU only; not Kadre/wgpu4k native callbacks.",
            }
        ],
        "derived": [
            {
                "id": "m85.selected-scene-resource-ledger",
                "sourceClass": m85.get("sourceClass"),
                "sourceArtifact": m85.get("sourceArtifact"),
                "nonClaim": m85.get("nonClaim"),
            },
            {
                "id": "m90.cache-hits-misses",
                "sourceClass": m90_classification["cacheHitsMisses"],
                "sourceArtifacts": m90.get("sourceArtifacts", []),
                "boundary": "Derived from selected ledger evidence, not observed broad callbacks.",
            },
        ],
        "observed-partial": [
            {
                "id": "m90.native-route-allocations",
                "sourceClass": m90_classification["nativeRouteAllocations"],
                "sourceArtifacts": m90.get("sourceArtifacts", []),
                "boundary": "Selected route creation/churn only.",
            },
            {
                "id": "m92.selected-route-create-rows",
                "rows": {
                    row_id: protected[row_id]["classification"]
                    for row_id in [
                        "shader-module-creates-selected-route",
                        "pipeline-creates-selected-route",
                    ]
                },
                "sourceArtifact": rel(M92_CLASSIFICATION),
            },
        ],
        "not-observable": [
            {
                "id": row_id,
                "classification": protected[row_id]["classification"],
                "blocker": protected[row_id].get("blocker"),
                "releaseGate": protected[row_id].get("releaseGate"),
            }
            for row_id in [
                "broad-webgpu-cache-hit-callbacks",
                "bind-group-cache-callbacks",
                "native-resource-free-callbacks",
                "adapter-owned-memory-snapshots",
            ]
        ],
        "blockers": sorted(blocker_ids),
        "bridge": {
            "linearIssue": bridge["linearIssue"],
            "sourceArtifact": bridge["sourceArtifact"],
            "sourceClass": bridge["sourceClass"],
            "sourceApi": bridge["sourceApi"],
            "notKadreNativeCallbacks": bridge["notKadreNativeCallbacks"],
            "broadKadreWgpu4kCallbacksClaimed": bridge["broadKadreWgpu4kCallbacksClaimed"],
        },
    }


def build_evidence(decisions: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    rows = [
        (
            "FOR-310",
            "M90 telemetry classification guard",
            "Protected M90 as bounded reporting evidence: native frame timing observed, selected-route allocations observed-partial, cache hits/misses derived, broad callbacks unavailable.",
            "KEEP_M90_RUNTIME_TELEMETRY_BOUNDED_AND_REPORTING_ONLY",
            [rel(FOR310_REPORT), rel(FOR310_JSON), "scripts/validate_for310_m90_runtime_telemetry_classification_guard.py"],
        ),
        (
            "FOR-311",
            "M92 RC headless Kadre dependency policy",
            "Kept required RC validation headless and checked-in; native Kadre refresh remains optional/provisioned.",
            "KEEP_M92_RC_REQUIRED_VALIDATION_HEADLESS_AND_CHECKED_IN",
            [rel(FOR311_REPORT), rel(FOR311_JSON), "scripts/validate_for311_m92_rc_headless_kadre_dependency_policy.py"],
        ),
        (
            "FOR-312",
            "PM bundle runtime headless manifest audit",
            "Proved the PM package carries the FOR-311 headless/optional-native boundary.",
            "PM_BUNDLE_RUNTIME_HEADLESS_MANIFEST_AUDIT_APPLIED",
            [rel(FOR312_REPORT), rel(FOR312_JSON), "scripts/validate_for312_pm_bundle_runtime_headless_manifest.py"],
        ),
        (
            "FOR-313",
            "Runtime docs Kadre headless policy audit",
            "Aligned M90/MEP-NEXT docs and generated Markdown wording with required headless validation and optional/provisioned Kadre refresh.",
            "M90_RUNTIME_DOCS_KADRE_HEADLESS_POLICY_AUDIT_APPLIED",
            [rel(FOR313_REPORT), rel(FOR313_JSON), "scripts/validate_for313_runtime_docs_kadre_headless_policy.py"],
        ),
        (
            "FOR-314",
            "Runtime cache counter source map",
            "Mapped observed candidate, derived ledger, observed-partial selected route counters, and not-observable Kadre/wgpu4k blockers.",
            "runtime-cache-counter-source-map-applied",
            [rel(FOR314_REPORT), rel(FOR314_JSON), "scripts/validate_for314_runtime_cache_counter_source_map.py"],
        ),
        (
            "FOR-315",
            "Headless WebGPU cache counter evidence",
            "Published the named observed Kanvas headless WebGPU artifact from cacheTelemetrySnapshot().",
            "headless-webgpu-cache-counter-evidence-applied",
            [rel(FOR315_REPORT), rel(FOR315_JSON), "scripts/validate_for315_headless_webgpu_cache_counters.py"],
        ),
        (
            "FOR-316",
            "Runtime cache counter evidence bridge",
            "Linked the FOR-315 observed artifact into the FOR-314 source map via observedEvidenceBridge while preserving M90/M92 boundaries.",
            "RUNTIME_CACHE_COUNTER_EVIDENCE_BRIDGE_APPLIED",
            [rel(FOR316_REPORT), rel(FOR314_JSON), "scripts/validate_for316_runtime_cache_counter_evidence_bridge.py"],
        ),
    ]

    evidence: list[dict[str, Any]] = []
    for ticket, title, impact, decision, artifacts in rows:
        history = TICKET_HISTORY[ticket]
        evidence.append(
            {
                "linearIssue": ticket,
                "title": title,
                "decision": decisions.get(ticket, {}).get("decision", decision),
                "closeoutDecisionName": decision,
                "impact": impact,
                "pullRequest": history["pullRequest"],
                "commits": history["commits"],
                "artifacts": artifacts,
            }
        )
    return evidence


def build_closeout() -> dict[str, Any]:
    expected_artifacts = validate_expected_artifacts()
    decisions = validate_ticket_decisions()
    classification_states = validate_runtime_boundaries()
    evidence = build_evidence(decisions)
    evidence_ids = {item["linearIssue"] for item in evidence}
    if not {"FOR-315", "FOR-316"}.issubset(evidence_ids):
        fail("FOR-315 and FOR-316 must be referenced in closeout evidence")

    return {
        "schemaVersion": 1,
        "linearIssue": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_APPLIED,
        "status": "reporting-only-closeout",
        "date": "2026-06-04",
        "scope": "FOR-310..FOR-316 runtime cache telemetry closeout",
        "expectedArtifacts": expected_artifacts,
        "evidence": evidence,
        "classificationStates": classification_states,
        "claimBoundary": {
            "observedClaim": "Only the FOR-315 Kanvas headless WebGPU cacheTelemetrySnapshot() scenario is observed.",
            "derivedClaim": "M85 and M90 cache hit/miss ledger values remain derived selected-scene evidence.",
            "observedPartialClaim": "M90/M92 selected native route create/allocation rows remain observed-partial and reporting-only.",
            "notObservableClaim": "Broad Kadre/wgpu4k cache callbacks, bind-group callbacks, native frees, and adapter memory snapshots remain not-observable.",
            "noPromotion": "M85/M90/M92 are not promoted to observed broad cache callbacks.",
        },
        "nonChanges": REQUIRED_NON_CHANGES,
        "kadreWgpu4kUnblockConditions": [
            {
                "id": "kadre-runtime.native-cache-counter-unavailable",
                "owner": "Kadre/wgpu4k native integration",
                "condition": "Expose observed shader, pipeline, bind-group, and cache hit/miss callbacks from the native route.",
            },
            {
                "id": "kadre-runtime.resource-lifetime-observation-partial",
                "owner": "Kadre/wgpu4k native integration",
                "condition": "Expose allocation/free/resource lifetime snapshots for surface resize and scene switch transitions.",
            },
            {
                "id": "kanvas-wide-cache-callback-promotion-evidence",
                "owner": "Kanvas follow-up",
                "condition": "Add a new narrow ticket with observed native callback artifacts, source paths, headless validation policy, and unchanged renderer/gate boundaries before any broad promotion.",
            },
        ],
        "guardrailsValidatedBy": [
            "rtk python3 scripts/validate_for314_runtime_cache_counter_source_map.py",
            "rtk python3 scripts/validate_for315_headless_webgpu_cache_counters.py",
            "rtk python3 scripts/validate_for316_runtime_cache_counter_evidence_bridge.py",
        ],
        "alternateDecisions": {
            "missingEvidence": DECISION_MISSING_EVIDENCE,
            "unsafeClaim": DECISION_UNSAFE,
        },
    }


def validate_closeout_artifact(data: dict[str, Any]) -> None:
    if data.get("decision") != DECISION_APPLIED:
        fail("closeout artifact must contain the applied decision")
    evidence = require_list(data, "evidence", ARTIFACT)
    evidence_ids = {item.get("linearIssue") for item in evidence if isinstance(item, dict)}
    missing = sorted({f"FOR-{number}" for number in range(310, 317)} - evidence_ids)
    if missing:
        fail(f"closeout artifact missing FOR evidence rows: {missing}")
    if "FOR-315" not in evidence_ids or "FOR-316" not in evidence_ids:
        fail("closeout artifact must reference FOR-315 and FOR-316")

    states = require_object(data, "classificationStates", ARTIFACT)
    for field in ["observed", "derived", "observed-partial", "not-observable"]:
        if not isinstance(states.get(field), list) or not states[field]:
            fail(f"classificationStates.{field} must be a non-empty list")

    boundary = require_object(data, "claimBoundary", ARTIFACT)
    boundary_text = json.dumps(boundary, sort_keys=True)
    for snippet in ["Kanvas headless WebGPU", "not-observable", "not promoted to observed broad cache callbacks"]:
        if snippet not in boundary_text:
            fail(f"claimBoundary missing `{snippet}`")

    non_changes = require_object(data, "nonChanges", ARTIFACT)
    for field, expected in REQUIRED_NON_CHANGES.items():
        if non_changes.get(field) != expected:
            fail(f"closeout nonChanges.{field} expected {expected!r}, got {non_changes.get(field)!r}", DECISION_UNSAFE)

    forbidden = re.compile(r"\b(M85|M90|M92)\b.*observed broad cache callbacks", re.IGNORECASE)
    if forbidden.search(json.dumps(data)) and "not promoted to observed broad cache callbacks" not in json.dumps(data):
        fail("closeout contains an unsafe M85/M90/M92 observed broad callback claim", DECISION_UNSAFE)


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def pr_cell(value: Any) -> str:
    if isinstance(value, int):
        return f"[#{value}](https://github.com/ygdrasil-io/kanvas/pull/{value})"
    return "not recorded in current git log"


def commits_cell(commits: list[str]) -> str:
    return ", ".join(f"`{commit[:12]}`" for commit in commits)


def write_report(data: dict[str, Any]) -> None:
    evidence_rows = []
    for item in data["evidence"]:
        artifacts = "<br>".join(f"`{artifact}`" for artifact in item["artifacts"])
        evidence_rows.append(
            "| {linear} | {pr} | {commits} | `{decision}` | {artifacts} | {impact} |".format(
                linear=f"`{item['linearIssue']}`",
                pr=pr_cell(item["pullRequest"]),
                commits=commits_cell(item["commits"]),
                decision=item["closeoutDecisionName"],
                artifacts=artifacts,
                impact=item["impact"],
            )
        )

    states = data["classificationStates"]
    not_observable_rows = "\n".join(
        f"- `{row['id']}`: `{row['classification']}`, blocker `{row.get('blocker')}`, releaseGate `{row.get('releaseGate')}`."
        for row in states["not-observable"]
    )
    unblock_rows = "\n".join(
        f"- `{item['id']}` ({item['owner']}): {item['condition']}"
        for item in data["kadreWgpu4kUnblockConditions"]
    )
    non_changes = "\n".join(
        f"- {key}: `{value}`."
        for key, value in data["nonChanges"].items()
    )

    report = f"""# FOR-317 Runtime Cache Telemetry Closeout

Linear: `{LINEAR_ID}`

Source memory: `{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-317 closes the reporting chain for FOR-310 through FOR-316. It consolidates
the existing runtime cache telemetry artifacts without changing renderer code,
runtime behavior, Gradle gates, Kadre native execution, shader output, fallback
policy, release gate status, score, or readiness.

The claim boundary is narrow: FOR-315 provides one observed Kanvas headless
WebGPU cache counter artifact from `{SOURCE_API}`. M85 and M90 cache hit/miss
values remain derived selected-scene evidence, M90/M92 selected native create
rows remain observed-partial, and broad Kadre/wgpu4k cache callbacks remain
not-observable.

## Evidence Chain

| Issue | PR | Commits | Decision | Artifacts | Impact |
|---|---|---|---|---|---|
{chr(10).join(evidence_rows)}

## Classification Boundary

- Observed: `{states["observed"][0]["id"]}` from `{states["observed"][0]["sourceArtifact"]}`; source class `{states["observed"][0]["sourceClass"]}`; cold misses `{states["observed"][0]["coldPipelineCacheMisses"]}`; warm hits `{states["observed"][0]["warmPipelineCacheHits"]}`.
- Derived: M85 selected-scene ledger and M90 cache hit/miss counters remain derived, not observed broad callback telemetry.
- Observed-partial: M90 native route allocations and M92 selected-route shader/pipeline create rows remain selected-route creation/churn evidence.

Not observable rows:

{not_observable_rows}

## Non-Changes

{non_changes}

## Conditions Before Broad Native Cache Callback Promotion

{unblock_rows}

## Validation

- `rtk python3 scripts/validate_for317_runtime_cache_telemetry_closeout.py`
- `rtk python3 scripts/validate_for316_runtime_cache_counter_evidence_bridge.py`
- `rtk python3 scripts/validate_for315_headless_webgpu_cache_counters.py`
- `rtk python3 scripts/validate_for314_runtime_cache_counter_source_map.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/runtime-cache-telemetry-closeout-for317.json >/dev/null`
- `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check`
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_report() -> None:
    require_snippets(
        REPORT,
        [
            "FOR-317 Runtime Cache Telemetry Closeout",
            "RUNTIME_CACHE_TELEMETRY_CLOSEOUT_APPLIED",
            "FOR-315 provides one observed Kanvas headless",
            "FOR-316",
            "not-observable",
            "without changing renderer code",
            "no-new-support-or-rendering-claim",
            "Kadre/wgpu4k",
            "validate_for314_runtime_cache_counter_source_map.py",
            "validate_for315_headless_webgpu_cache_counters.py",
            "validate_for316_runtime_cache_counter_evidence_bridge.py",
        ],
    )


def main() -> None:
    data = build_closeout()
    write_artifact(data)
    validate_closeout_artifact(load_json(ARTIFACT))
    write_report(data)
    validate_report()
    print(f"{LINEAR_ID} runtime cache telemetry closeout validated")
    print(json.dumps({
        "decision": data["decision"],
        "artifact": rel(ARTIFACT),
        "report": rel(REPORT),
        "observed": data["classificationStates"]["observed"][0]["id"],
        "nonChanges": data["nonChanges"],
    }, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
