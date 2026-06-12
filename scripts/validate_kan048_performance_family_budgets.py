#!/usr/bin/env python3
import json
import statistics
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/performance-family-budgets"
OUTPUT_JSON = "kan-048-performance-family-budgets.json"
OUTPUT_MARKDOWN = "kan-048-performance-family-budgets.md"

SPEC_PERFORMANCE = ".upstream/specs/skia-like-realtime/04-performance-tiering-and-release-gates.md"
SPEC_BENCHMARK = ".upstream/specs/wgsl-pipeline/12-benchmark-harness-and-performance-gates.md"
TARGET_RENDERER = ".upstream/target/skia-like-realtime-renderer-target.md"
KAN020_POLICY = "reports/wgsl-pipeline/performance/kan-020-slice-performance-minimum.json"
M67_FAMILY_BUDGETS = "reports/wgsl-pipeline/performance/m67-performance-tiering/m67-family-budgets.json"
KAN047_CODEC = "reports/wgsl-pipeline/codec-provenance-matrix/kan-047-codec-provenance-matrix.json"
KAN052_IMAGE_FILTER = "reports/wgsl-pipeline/image-filter-visual-delta/kan-052-image-filter-visual-delta.json"
KAN053_TEXT_GLYPH = "reports/wgsl-pipeline/text-glyph-visual-delta/kan-053-text-glyph-visual-delta.json"

BITMAP_COLOR_RAW_PAYLOADS = [
    "reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/cpu-performance.json",
    "reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/gpu-performance.json",
    "reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/cpu-performance.json",
    "reports/wgsl-pipeline/scenes/artifacts/linear-gradient-rect/gpu-performance.json",
    "reports/wgsl-pipeline/scenes/artifacts/src-over-stack/cpu-performance.json",
    "reports/wgsl-pipeline/scenes/artifacts/src-over-stack/gpu-performance.json",
]

STABLE_UNAVAILABLE_REASONS = {
    "performance.image-filter.intermediate-benchmark-unavailable",
    "performance.text-glyph.production-sampling-route-unavailable",
    "gpu.adapter-missing",
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-048 performance family budgets validation failed: {message}")


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


def numeric(value: Any, field: str) -> float:
    require(isinstance(value, (int, float)), f"{field} must be numeric")
    return float(value)


def performance_payload(root: Path, relative_path: str) -> dict[str, Any]:
    payload = load_json(root, relative_path)
    require(isinstance(payload, dict), f"{relative_path} must contain an object")
    payload["artifactPath"] = relative_path
    return payload


def measured_payload_summary(payload: dict[str, Any]) -> dict[str, Any]:
    scene_id = payload.get("sceneId")
    status = payload.get("status")
    timing = payload.get("timing") if isinstance(payload.get("timing"), dict) else {}
    environment = payload.get("environment") if isinstance(payload.get("environment"), dict) else {}
    counters = payload.get("counters") if isinstance(payload.get("counters"), dict) else {}
    baseline = payload.get("baseline") if isinstance(payload.get("baseline"), dict) else {}
    gate = payload.get("gate") if isinstance(payload.get("gate"), dict) else {}
    lane = str(payload.get("lane", "unknown"))
    adapter = payload.get("adapter") or environment.get("adapter")

    return {
        "sceneId": scene_id,
        "lane": lane,
        "status": status,
        "artifactPath": payload.get("artifactPath"),
        "command": payload.get("command"),
        "sampleCount": payload.get("sampleCount"),
        "timing": {
            "medianMs": timing.get("medianMs"),
            "p95Ms": timing.get("p95Ms"),
        },
        "environment": {
            "host": environment.get("host"),
            "os": environment.get("os"),
            "jdk": environment.get("jdk"),
            "backend": environment.get("backend"),
            "adapter": adapter,
        },
        "counters": counters,
        "baseline": {
            "name": baseline.get("name"),
            "commit": baseline.get("commit"),
            "owner": baseline.get("owner"),
        },
        "regression": payload.get("regression", {}),
        "sourceGate": {
            "mode": gate.get("mode"),
            "owner": gate.get("owner"),
            "reason": gate.get("reason"),
            "quarantinePolicy": gate.get("quarantinePolicy"),
        },
        "rawMetrics": payload.get("rawMetrics"),
    }


def environment_summary(payloads: list[dict[str, Any]]) -> dict[str, Any]:
    hosts = sorted({str(payload["environment"].get("host")) for payload in payloads if payload["environment"].get("host")})
    jdks = sorted({str(payload["environment"].get("jdk")) for payload in payloads if payload["environment"].get("jdk")})
    backends = sorted({str(payload["environment"].get("backend")) for payload in payloads if payload["environment"].get("backend")})
    adapters = sorted({str(payload["environment"].get("adapter")) for payload in payloads if payload["environment"].get("adapter")})
    return {
        "host": hosts[0] if len(hosts) == 1 else hosts,
        "jdk": jdks[0] if len(jdks) == 1 else jdks,
        "backends": backends,
        "adapters": adapters,
    }


def source_audit(root: Path) -> dict[str, Any]:
    require_text(
        root,
        SPEC_PERFORMANCE,
        [
            "Estimated or missing payloads are not measured evidence.",
            "No metric may skip directly from estimated to release-blocking.",
            "Required Metadata",
        ],
    )
    require_text(
        root,
        SPEC_BENCHMARK,
        [
            "Only `measured` values can participate in regression gates.",
            "If adapter identity is missing, the row must use",
            "No benchmark gate may be required in CI unless it has",
        ],
    )
    require_text(
        root,
        TARGET_RENDERER,
        [
            "release-grade performance budgets across feature families",
            "Performance and cache readiness",
        ],
    )
    kan020 = load_json(root, KAN020_POLICY)
    require(kan020.get("releaseBlockingChange") is False, "KAN-020 must remain non-release-blocking")
    require(kan020.get("status") == "pass", "KAN-020 policy must remain pass")
    m67 = load_json(root, M67_FAMILY_BUDGETS)
    require(m67.get("counters", {}).get("families", 0) >= 7, "M67 family budget inventory changed")
    require(all(row.get("releaseBlocking") is False for row in m67.get("families", []) if isinstance(row, dict)), "M67 family rows must remain non-release-blocking")
    return {
        "performanceSpec": SPEC_PERFORMANCE,
        "benchmarkSpec": SPEC_BENCHMARK,
        "targetRenderer": TARGET_RENDERER,
        "kan020Policy": KAN020_POLICY,
        "m67FamilyBudgets": M67_FAMILY_BUDGETS,
    }


def bitmap_color_family(root: Path) -> dict[str, Any]:
    raw_payloads = [measured_payload_summary(performance_payload(root, path)) for path in BITMAP_COLOR_RAW_PAYLOADS]
    medians = [numeric(payload["timing"].get("medianMs"), f"{payload['artifactPath']}.timing.medianMs") for payload in raw_payloads]
    p95s = [numeric(payload["timing"].get("p95Ms"), f"{payload['artifactPath']}.timing.p95Ms") for payload in raw_payloads]
    return {
        "familyId": "bitmap-color",
        "family": "bitmap/color",
        "status": "measured",
        "measured": True,
        "payloadClass": "selected-raw-benchmark-payloads",
        "measurementType": "CPU scalar and WebGPU cache/timing dashboard benchmarks",
        "lane": "family.bitmap-color",
        "sourceFamilies": ["bitmap sampling", "gradient/color", "blend/color-filter"],
        "rawPayloads": raw_payloads,
        "timing": {
            "p50Ms": round(statistics.median(medians), 6),
            "p95Ms": round(max(p95s), 6),
            "maxMedianMs": round(max(medians), 6),
            "maxP95Ms": round(max(p95s), 6),
        },
        "environment": environment_summary(raw_payloads),
        "gate": {
            "phase": "reporting-only",
            "releaseBlocking": False,
            "reason": "KAN-048 aggregates existing local M43/M59 payloads; no CI-owned baseline, variance policy, owner matrix, and negative fixture are promoted here.",
        },
        "releaseBlocking": False,
        "countedAsMeasuredGate": False,
        "quarantineRationale": [
            "local measured payloads are PM trend evidence, not release gates",
            "family-level baseline/variance/owner/negative fixture is not complete",
            "adapter-specific GPU rows stay reporting-only and cannot be compared without eligibility policy",
        ],
    }


def image_filter_family(root: Path) -> dict[str, Any]:
    evidence = load_json(root, KAN052_IMAGE_FILTER)
    blocker = evidence.get("blocker") if isinstance(evidence.get("blocker"), dict) else {}
    require(evidence.get("blocked") is True, "KAN-052 must remain blocked root-cause evidence")
    return {
        "familyId": "image-filter",
        "family": "filters",
        "status": "unavailable",
        "measured": False,
        "payloadClass": "unavailable-with-root-cause",
        "measurementType": "image-filter intermediate texture benchmark",
        "lane": "family.image-filter",
        "reason": "performance.image-filter.intermediate-benchmark-unavailable",
        "source": KAN052_IMAGE_FILTER,
        "sourceBlocker": blocker.get("rootCause", "unknown"),
        "rawPayloads": [],
        "gate": {
            "phase": "reporting-only",
            "releaseBlocking": False,
            "reason": "No isolated filter-family timing payload exists after the KAN-052 root-cause blocker.",
        },
        "releaseBlocking": False,
        "countedAsMeasuredGate": False,
        "quarantineRationale": [
            blocker.get("missingCondition", "missing stable image-filter performance payload"),
            "root-cause blocker must be solved before filter timing can be promoted",
        ],
    }


def text_glyph_family(root: Path) -> dict[str, Any]:
    evidence = load_json(root, KAN053_TEXT_GLYPH)
    blocker = evidence.get("blocker") if isinstance(evidence.get("blocker"), dict) else {}
    require(evidence.get("blocked") is True, "KAN-053 must remain blocked root-cause evidence")
    return {
        "familyId": "text-glyph",
        "family": "text",
        "status": "unavailable",
        "measured": False,
        "payloadClass": "unavailable-with-root-cause",
        "measurementType": "glyph atlas upload benchmark",
        "lane": "family.text-glyph",
        "reason": "performance.text-glyph.production-sampling-route-unavailable",
        "source": KAN053_TEXT_GLYPH,
        "sourceBlocker": blocker.get("rootCause", "unknown"),
        "rawPayloads": [],
        "gate": {
            "phase": "reporting-only",
            "releaseBlocking": False,
            "reason": "No production glyph atlas sampling route timing exists after the KAN-053 root-cause blocker.",
        },
        "releaseBlocking": False,
        "countedAsMeasuredGate": False,
        "quarantineRationale": [
            blocker.get("reasonCode", "requires-production-text-route"),
            "outline-path text evidence is not a glyph atlas sampling performance payload",
        ],
    }


def build_family_rows(root: Path) -> list[dict[str, Any]]:
    return [
        image_filter_family(root),
        text_glyph_family(root),
        bitmap_color_family(root),
    ]


def claim_guard(evidence: dict[str, Any]) -> dict[str, list[str]]:
    rows = [row for row in evidence.get("familyRows", []) if isinstance(row, dict)]
    guard: dict[str, list[str]] = {
        "missingFamilies": [],
        "unavailableRowsMissingReason": [],
        "estimatedPayloadsCountedAsMeasured": [],
        "releaseBlockingRows": [],
        "measuredRowsMissingMetadata": [],
        "measuredRowsMissingRawPayloads": [],
        "nonReportingOnlyGates": [],
    }
    required = {"image-filter", "text-glyph", "bitmap-color"}
    present = {str(row.get("familyId")) for row in rows}
    guard["missingFamilies"] = sorted(required - present)
    for row in rows:
        family_id = str(row.get("familyId"))
        if row.get("releaseBlocking") is True or row.get("countedAsMeasuredGate") is True:
            guard["releaseBlockingRows"].append(family_id)
        gate = row.get("gate") if isinstance(row.get("gate"), dict) else {}
        if gate.get("phase") != "reporting-only" or gate.get("releaseBlocking") is True:
            guard["nonReportingOnlyGates"].append(family_id)
        if row.get("status") == "unavailable":
            reason = row.get("reason")
            if not isinstance(reason, str) or reason not in STABLE_UNAVAILABLE_REASONS:
                guard["unavailableRowsMissingReason"].append(family_id)
        if row.get("status") == "measured":
            payloads = row.get("rawPayloads") if isinstance(row.get("rawPayloads"), list) else []
            if not payloads:
                guard["measuredRowsMissingRawPayloads"].append(family_id)
            for payload in payloads:
                if not isinstance(payload, dict):
                    guard["measuredRowsMissingMetadata"].append(family_id)
                    continue
                if payload.get("status") == "estimated":
                    guard["estimatedPayloadsCountedAsMeasured"].append(f"{family_id}:{payload.get('artifactPath')}")
                required_payload_fields = [
                    payload.get("command"),
                    payload.get("sampleCount"),
                    payload.get("timing", {}).get("medianMs") if isinstance(payload.get("timing"), dict) else None,
                    payload.get("timing", {}).get("p95Ms") if isinstance(payload.get("timing"), dict) else None,
                    payload.get("environment", {}).get("host") if isinstance(payload.get("environment"), dict) else None,
                    payload.get("environment", {}).get("jdk") if isinstance(payload.get("environment"), dict) else None,
                    payload.get("environment", {}).get("backend") if isinstance(payload.get("environment"), dict) else None,
                    payload.get("baseline", {}).get("name") if isinstance(payload.get("baseline"), dict) else None,
                    payload.get("baseline", {}).get("commit") if isinstance(payload.get("baseline"), dict) else None,
                ]
                if any(value in (None, "", {}) for value in required_payload_fields):
                    guard["measuredRowsMissingMetadata"].append(f"{family_id}:{payload.get('artifactPath')}")
                if payload.get("lane") == "GPU" and not payload.get("environment", {}).get("adapter"):
                    guard["measuredRowsMissingMetadata"].append(f"{family_id}:{payload.get('artifactPath')}:adapter")
    return guard


def summarize(rows: list[dict[str, Any]], guard: dict[str, list[str]]) -> dict[str, int]:
    return {
        "familyRows": len(rows),
        "measuredFamilies": sum(1 for row in rows if row.get("status") == "measured"),
        "unavailableFamilies": sum(1 for row in rows if row.get("status") == "unavailable"),
        "estimatedPayloadsCountedAsMeasured": len(guard["estimatedPayloadsCountedAsMeasured"]),
        "releaseBlockingRows": len(guard["releaseBlockingRows"]),
        "unavailableRowsMissingReason": len(guard["unavailableRowsMissingReason"]),
        "measuredRowsMissingMetadata": len(guard["measuredRowsMissingMetadata"]),
    }


def build_evidence(root: Path) -> dict[str, Any]:
    source_audit_payload = source_audit(root)
    codec = load_json(root, KAN047_CODEC)
    require(codec.get("status") == "pass", "KAN-047 codec provenance matrix must remain pass")
    rows = build_family_rows(root)
    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-048",
        "packId": "kan-048-performance-family-budgets",
        "status": "pass",
        "closureDecision": "performance-family-budgets-reporting",
        "claimLevel": "pm-performance-family-budget-evidence",
        "releaseBlockingChange": False,
        "slowBenchmarkCiRequired": False,
        "readinessDelta": 0,
        "familyRows": rows,
        "gatePolicy": {
            "phase": "reporting-only",
            "releaseBlocking": False,
            "promotionRequires": [
                "CI-owned baseline",
                "host/JDK/backend/adapter eligibility",
                "variance policy",
                "negative fixture",
                "baseline owner",
            ],
        },
        "sourceAudit": source_audit_payload,
        "nonClaims": [
            "KAN-048 does not add a release-blocking performance threshold.",
            "KAN-048 does not run slow benchmarks as required CI gates.",
            "KAN-048 does not count estimated, missing, derived, or unavailable payloads as measured.",
            "KAN-048 does not claim GPU timing when adapter identity is missing.",
            "KAN-048 does not change renderer, shader, cache runtime, or correctness thresholds.",
        ],
        "requiredValidation": [
            "validateKan048PerformanceFamilyBudgets",
            "pipelineConformance",
            "pipelinePmBundle",
        ],
    }
    guard = claim_guard(evidence)
    evidence["claimGuard"] = guard
    evidence["summary"] = summarize(rows, guard)
    validate_evidence(evidence, root)
    return evidence


def validate_evidence(evidence: dict[str, Any], root: Path) -> None:
    require(evidence.get("ticket") == "KAN-048", "ticket id changed")
    require(evidence.get("releaseBlockingChange") is False, "releaseBlockingChange must remain false")
    require(evidence.get("slowBenchmarkCiRequired") is False, "slow benchmark CI requirement is not allowed")
    rows = evidence.get("familyRows")
    require(isinstance(rows, list) and rows, "familyRows missing")
    guard = claim_guard(evidence)
    if guard["missingFamilies"]:
        fail(f"missing families: {guard['missingFamilies']}")
    if guard["unavailableRowsMissingReason"]:
        fail(f"unavailable row missing reason: {guard['unavailableRowsMissingReason']}")
    if guard["estimatedPayloadsCountedAsMeasured"]:
        fail(f"estimated payload counted as measured: {guard['estimatedPayloadsCountedAsMeasured']}")
    if guard["releaseBlockingRows"]:
        fail(f"release-blocking row: {guard['releaseBlockingRows']}")
    if guard["measuredRowsMissingMetadata"]:
        fail(f"measured row missing metadata: {guard['measuredRowsMissingMetadata']}")
    if guard["measuredRowsMissingRawPayloads"]:
        fail(f"measured row missing raw payloads: {guard['measuredRowsMissingRawPayloads']}")
    if guard["nonReportingOnlyGates"]:
        fail(f"non-reporting-only gate: {guard['nonReportingOnlyGates']}")
    for row in rows:
        if not isinstance(row, dict):
            fail("family row must be an object")
        for payload in row.get("rawPayloads", []):
            artifact = payload.get("artifactPath")
            require(isinstance(artifact, str) and (root / artifact).is_file(), f"missing raw payload artifact: {artifact}")
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
    rows = evidence["familyRows"]
    summary = evidence["summary"]
    family_table = markdown_table(
        ["Family", "Status", "Measured", "Lane", "p50 / p95", "Reason / gate"],
        [
            [
                row["family"],
                row["status"],
                row["measured"],
                row["lane"],
                f'{row.get("timing", {}).get("p50Ms", "unavailable")} / {row.get("timing", {}).get("p95Ms", "unavailable")}',
                row.get("reason") or row["gate"]["reason"],
            ]
            for row in rows
        ],
    )
    return f"""# KAN-048 Performance Family Budgets

Status: `{evidence["status"]}`

KAN-048 reports family-level performance payloads without creating a
release-blocking gate or requiring slow benchmarks in CI.

## Summary

| Metric | Value |
| --- | ---: |
| Family rows | `{summary["familyRows"]}` |
| Measured families | `{summary["measuredFamilies"]}` |
| Unavailable families | `{summary["unavailableFamilies"]}` |
| Estimated payloads counted as measured | `{summary["estimatedPayloadsCountedAsMeasured"]}` |
| Release-blocking rows | `{summary["releaseBlockingRows"]}` |

## Family Budgets

{family_table}

## Gate Policy

- Family gates remain `reporting-only`.
- Promotion requires CI-owned baseline, host/JDK/backend/adapter eligibility,
  variance policy, negative fixture, and baseline owner.
- Unavailable rows keep stable reasons instead of being estimated.

## Quarantine Rationale

{chr(10).join(f"- `{row['familyId']}`: " + "; ".join(row.get("quarantineRationale", [])) for row in rows)}

## Non-Claims

{chr(10).join(f"- {item}" for item in evidence["nonClaims"])}
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(json.dumps(evidence, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
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
        f"KAN-048 performance family budgets PASS: "
        f"{evidence['summary']['familyRows']} family rows, "
        f"{evidence['summary']['measuredFamilies']} measured, "
        f"{evidence['summary']['unavailableFamilies']} unavailable."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
