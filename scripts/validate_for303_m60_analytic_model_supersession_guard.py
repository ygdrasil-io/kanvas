#!/usr/bin/env python3
"""Generate and validate FOR-303 M60 analytic model supersession guard."""

from __future__ import annotations

import json
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-303"
PARENT_ID = "FOR-241"
SCENE_ID = "m60-bounded-nested-rrect-clip"
SCENE_DIR = PROJECT_ROOT / f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}"
SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-m60-analytic-model-supersession-guard-ticket"
SOURCE_MEMORY_PATH = (
    PROJECT_ROOT.parents[2]
    / "basic-memory/global/global/kanvas/ticket-drafts/Draft FOR next M60 analytic model supersession guard ticket.md"
)
SOURCE_MEMORY_STATUS_VERIFIED = "verified"
SOURCE_MEMORY_STATUS_UNAVAILABLE = "unavailable"
SOURCE_MEMORY_STATUS_MISMATCH = "mismatch"
SOURCE_FOR293 = SCENE_DIR / "m60-red-drawrrect-runtime-visibility-audit-for293.json"
SOURCE_FOR301 = SCENE_DIR / "m60-skaaclip-band-trace-for301.json"
SOURCE_FOR302 = SCENE_DIR / "m60-analytic-clip-model-reconciliation-for302.json"
AUDIT_NAME = "m60-analytic-model-supersession-guard-for303.json"
AUDIT = SCENE_DIR / AUDIT_NAME
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-303-m60-analytic-model-supersession-guard.md"

DECISION_APPLIED = "M60_ANALYTIC_MODEL_SUPERSESSION_GUARD_APPLIED"
DECISION_UNSAFE = "M60_ANALYTIC_MODEL_UNSAFE_CONSUMER_FOUND"
DECISION_AMBIGUOUS = "M60_ANALYTIC_MODEL_SUPERSESSION_GUARD_AMBIGUOUS"

SCAN_ROOTS = (
    PROJECT_ROOT / "scripts",
    PROJECT_ROOT / "reports/wgsl-pipeline",
)
SCAN_SUFFIXES = {".py", ".md", ".json"}
EXCLUDED_SCAN_PATHS = {AUDIT, REPORT}

MODEL_PATTERNS = {
    "red_draw_runtime_domain": re.compile(r"\bred_draw_runtime_domain\b"),
    "clip_coverage": re.compile(r"\bclip_coverage\b"),
    "difference_clip_coverage": re.compile(r"\bdifference_clip_coverage\b"),
    "for293_symbol": re.compile(r"\bfor293\b"),
    "for293_text": re.compile(r"\bFOR-293\b"),
    "for293_artifact": re.compile(r"m60-red-drawrrect-runtime-visibility-audit-for293"),
    "for293_predicate": re.compile(r"mask_alpha > 0 && clip_coverage > 0 && alpha_after_clip > 0"),
}
PROMOTION_PATTERNS = {
    "promote": re.compile(r"\bpromot(?:e|ed|ion|ing)\b", re.IGNORECASE),
    "support": re.compile(r"\bsupport(?:ed|s|ing)?\b", re.IGNORECASE),
    "pass_row": re.compile(r"\bpass(?:ed)?[- ]row\b", re.IGNORECASE),
    "threshold": re.compile(r"\bthreshold\b", re.IGNORECASE),
    "expected_unsupported": re.compile(r"\bexpected-unsupported\b"),
    "support_decision": re.compile(r"\bsupportDecision\b"),
    "keep_expected_unsupported": re.compile(r"\bKEEP_EXPECTED_UNSUPPORTED\b"),
}
M60_PATTERN = re.compile(r"\bM60\b|m60-", re.IGNORECASE)
FOR301_PATTERN = re.compile(r"\bFOR-301\b|for301\b|m60-skaaclip-band-trace-for301")
FOR302_PATTERN = re.compile(r"\bFOR-302\b|for302\b|M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT")


@dataclass(frozen=True)
class ScanResult:
    path: Path
    for_number: int | None
    marker_counts: dict[str, int]
    promotion_counts: dict[str, int]
    references_for301: bool
    references_for302: bool
    classification: str
    allowed: bool
    reason: str


def fail(message: str) -> None:
    raise SystemExit(f"FOR-303 validation failed: {message}")


def json_dump(data: dict[str, Any]) -> str:
    return json.dumps(data, indent=2, sort_keys=False) + "\n"


def load_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    if not isinstance(data, dict):
        fail(f"{path.relative_to(PROJECT_ROOT)} must contain a JSON object")
    return data


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def for_number(path: Path, text: str) -> int | None:
    candidates = [int(match.group(1)) for match in re.finditer(r"for[-_]?(\d{3})", str(path), re.IGNORECASE)]
    candidates.extend(int(match.group(1)) for match in re.finditer(r"\bFOR-(\d{3})\b", text))
    if not candidates:
        return None
    return max(candidates)


def is_for293_consumer(marker_counts: dict[str, int]) -> bool:
    return any(
        marker_counts[key] > 0
        for key in (
            "red_draw_runtime_domain",
            "clip_coverage",
            "difference_clip_coverage",
            "for293_symbol",
            "for293_text",
            "for293_artifact",
            "for293_predicate",
        )
    )


def synthetic_marker_counts(**overrides: int) -> dict[str, int]:
    counts = {key: 0 for key in MODEL_PATTERNS}
    counts.update(overrides)
    return counts


def synthetic_promotion_counts(**overrides: int) -> dict[str, int]:
    counts = {key: 0 for key in PROMOTION_PATTERNS}
    counts.update(overrides)
    return counts


def validate_synthetic_policy_cases() -> None:
    cases = [
        {
            "name": "clip_coverage promotion without supersession is forbidden",
            "path": PROJECT_ROOT / "scripts/future_m60_promotion.py",
            "number": 304,
            "text": "future M60 promotion uses clip_coverage as support evidence",
            "markers": synthetic_marker_counts(clip_coverage=1),
            "promotion": synthetic_promotion_counts(promote=1, support=1),
            "references_for301": False,
            "references_for302": False,
            "expected": "forbidden",
        },
        {
            "name": "difference_clip_coverage support without supersession is forbidden",
            "path": PROJECT_ROOT / "scripts/future_m60_support.py",
            "number": 304,
            "text": "future M60 support uses difference_clip_coverage",
            "markers": synthetic_marker_counts(difference_clip_coverage=1),
            "promotion": synthetic_promotion_counts(support=1),
            "references_for301": False,
            "references_for302": False,
            "expected": "forbidden",
        },
        {
            "name": "clip_coverage consumer without supersession is ambiguous even without promotion",
            "path": PROJECT_ROOT / "scripts/future_m60_audit.py",
            "number": 304,
            "text": "future M60 audit reads clip_coverage",
            "markers": synthetic_marker_counts(clip_coverage=1),
            "promotion": synthetic_promotion_counts(),
            "references_for301": False,
            "references_for302": False,
            "expected": "ambiguous",
        },
        {
            "name": "clip_coverage comparison with supersession is allowed",
            "path": PROJECT_ROOT / "scripts/future_m60_reconciled_audit.py",
            "number": 304,
            "text": "future M60 audit reads clip_coverage with FOR-301 and FOR-302",
            "markers": synthetic_marker_counts(clip_coverage=1),
            "promotion": synthetic_promotion_counts(),
            "references_for301": True,
            "references_for302": True,
            "expected": "comparison-reconciliation",
        },
    ]
    for case in cases:
        classification, allowed, _reason = classify(
            path=case["path"],
            number=case["number"],
            text=case["text"],
            marker_counts=case["markers"],
            promotion_counts=case["promotion"],
            references_for301=case["references_for301"],
            references_for302=case["references_for302"],
        )
        if classification != case["expected"]:
            fail(
                f"synthetic policy case `{case['name']}` expected {case['expected']}, got {classification}"
            )
        if case["expected"] in {"forbidden", "ambiguous"} and allowed is not False:
            fail(f"synthetic policy case `{case['name']}` should be disallowed")
        if case["expected"] == "comparison-reconciliation" and allowed is not True:
            fail(f"synthetic policy case `{case['name']}` should be allowed")


def classify(
    *,
    path: Path,
    number: int | None,
    text: str,
    marker_counts: dict[str, int],
    promotion_counts: dict[str, int],
    references_for301: bool,
    references_for302: bool,
) -> tuple[str, bool, str]:
    model_consumer = is_for293_consumer(marker_counts)
    promotion_or_support = M60_PATTERN.search(text) is not None and sum(promotion_counts.values()) > 0
    relative = rel(path)

    if number is not None and number <= 293:
        return (
            "historical",
            True,
            "Historical analytic model source or pre-FOR-293 foundation; preserved as evidence only.",
        )
    if number is not None and 294 <= number <= 300:
        return (
            "historical-comparison",
            True,
            "Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.",
        )
    if number == 301:
        return (
            "runtime-reconciliation",
            True,
            "FOR-301 runtime SkAAClip trace is the superseding runtime operand evidence.",
        )
    if number == 302:
        if not references_for301:
            return (
                "ambiguous",
                False,
                "FOR-302 reconciliation artifact must reference FOR-301 runtime evidence.",
            )
        return (
            "runtime-reconciliation",
            True,
            "FOR-302 reconciles the FOR-293 analytic model against the FOR-301 runtime trace.",
        )
    if number == 303:
        if references_for301 and references_for302:
            return (
                "supersession-guard",
                True,
                "FOR-303 guard references both superseding runtime/reconciliation records.",
            )
        return (
            "ambiguous",
            False,
            "FOR-303 guard material must cite both FOR-301 and FOR-302.",
        )
    if model_consumer and promotion_or_support and not (references_for301 and references_for302):
        return (
            "forbidden",
            False,
            "M60 promotion/support consumer uses the FOR-293 model without FOR-301/FOR-302 supersession.",
        )
    if model_consumer and not (references_for301 and references_for302):
        return (
            "ambiguous",
            False,
            "Post-FOR-302 FOR-293 model consumer lacks explicit supersession context.",
        )
    if model_consumer:
        return (
            "comparison-reconciliation",
            True,
            "FOR-293 model use is explicitly tied to FOR-301/FOR-302 supersession.",
        )
    if "validate_for303" in relative:
        return (
            "supersession-guard",
            True,
            "Guard script owns the scan policy.",
        )
    return (
        "historical-foundation",
        True,
        "Contains only foundational clip coverage vocabulary, not a FOR-293 visibility consumer.",
    )


def scan_file(path: Path) -> ScanResult | None:
    if path in EXCLUDED_SCAN_PATHS:
        return None
    text = path.read_text(encoding="utf-8")
    marker_counts = {name: len(pattern.findall(text)) for name, pattern in MODEL_PATTERNS.items()}
    if sum(marker_counts.values()) == 0:
        return None
    promotion_counts = {name: len(pattern.findall(text)) for name, pattern in PROMOTION_PATTERNS.items()}
    number = for_number(path, text)
    references_for301 = FOR301_PATTERN.search(text) is not None
    references_for302 = FOR302_PATTERN.search(text) is not None
    classification, allowed, reason = classify(
        path=path,
        number=number,
        text=text,
        marker_counts=marker_counts,
        promotion_counts=promotion_counts,
        references_for301=references_for301,
        references_for302=references_for302,
    )
    return ScanResult(
        path=path,
        for_number=number,
        marker_counts=marker_counts,
        promotion_counts=promotion_counts,
        references_for301=references_for301,
        references_for302=references_for302,
        classification=classification,
        allowed=allowed,
        reason=reason,
    )


def scan_consumers() -> list[ScanResult]:
    results: list[ScanResult] = []
    for root in SCAN_ROOTS:
        for path in sorted(root.rglob("*")):
            if not path.is_file() or path.suffix not in SCAN_SUFFIXES:
                continue
            result = scan_file(path)
            if result is not None:
                results.append(result)
    return results


def source_memory_status() -> str:
    if not SOURCE_MEMORY_PATH.exists():
        return SOURCE_MEMORY_STATUS_UNAVAILABLE
    if SOURCE_MEMORY in SOURCE_MEMORY_PATH.read_text(encoding="utf-8"):
        return SOURCE_MEMORY_STATUS_VERIFIED
    return SOURCE_MEMORY_STATUS_MISMATCH


def supersession_evidence(source_for293: dict[str, Any], source_for301: dict[str, Any], source_for302: dict[str, Any]) -> dict[str, Any]:
    original = source_for302["comparison"]["originalTargets"]
    target_hole = source_for302["comparison"]["candidateMinusRuntime002"]
    red_components = source_for302["comparison"]["redRuntimeComponents"]
    return {
        "for293Decision": source_for293["decision"],
        "for301Decision": source_for301["decision"],
        "for302Decision": source_for302["decision"],
        "for302ClipPolarityFixApplied": source_for302["decisions"]["M60_ANALYTIC_MODEL_CLIP_POLARITY_FIX_APPLIED"],
        "supportDecision": source_for302["supportDecision"],
        "originalTargetPixels": original["pixels"],
        "originalFor293NonZero": original["for293SurvivingClipCoverage"]["nonZeroPixels"],
        "originalRuntimePathFull": original["runtimeDifferencePathCoverage"]["fullPixels"],
        "originalRuntimeResultZero": original["runtimeDifferenceResultCoverage"]["zeroPixels"],
        "originalFor293RuntimeMismatches": original["for293SurvivingClipVsRuntimeResult"]["mismatches"],
        "originalRuntimeFormulaMatches": original["runtimeFormulaVsRuntimeResult"]["exactMatches"],
        "candidateMinusRuntimePixels": target_hole["pixels"],
        "candidateMinusRuntimeResultZero": target_hole["runtimeDifferenceResultCoverage"]["zeroPixels"],
        "redRuntimeComponentPixels": [component["pixels"] for component in red_components],
    }


def analyze() -> dict[str, Any]:
    source_for293 = load_json(SOURCE_FOR293)
    source_for301 = load_json(SOURCE_FOR301)
    source_for302 = load_json(SOURCE_FOR302)
    consumers = scan_consumers()
    unsafe = [result for result in consumers if result.classification == "forbidden"]
    ambiguous = [result for result in consumers if result.classification == "ambiguous"]
    classes = Counter(result.classification for result in consumers)
    markers = Counter()
    for result in consumers:
        markers.update(result.marker_counts)

    decision = DECISION_APPLIED
    if unsafe:
        decision = DECISION_UNSAFE
    elif ambiguous:
        decision = DECISION_AMBIGUOUS

    return {
        "linear": LINEAR_ID,
        "parent": PARENT_ID,
        "probe": "m60-analytic-model-supersession-guard",
        "sceneId": SCENE_ID,
        "backend": "audit-only-text-scan",
        "sourceMemory": SOURCE_MEMORY,
        "sourceMemoryPath": str(SOURCE_MEMORY_PATH),
        "sourceMemoryStatus": source_memory_status(),
        "sourceAudits": {
            "for293": rel(SOURCE_FOR293),
            "for301": rel(SOURCE_FOR301),
            "for302": rel(SOURCE_FOR302),
        },
        "decision": decision,
        "decisions": {
            DECISION_APPLIED: decision == DECISION_APPLIED,
            DECISION_UNSAFE: decision == DECISION_UNSAFE,
            DECISION_AMBIGUOUS: decision == DECISION_AMBIGUOUS,
        },
        "policy": {
            "historicalAllowedThrough": "FOR-300",
            "runtimeSupersessionEvidence": ["FOR-301", "FOR-302"],
            "forbiddenRule": (
                "A future M60 promotion/support consumer must not use FOR-293 red_draw_runtime_domain, "
                "clip_coverage, or difference_clip_coverage evidence unless it references both FOR-301 "
                "and FOR-302 supersession evidence."
            ),
            "guardScope": "audit-only; normal renderer, SkAAClip.op, CTM, blend, setPixel, GPU/WebGPU, thresholds, fallbacks, and M60 support status are unchanged.",
        },
        "supersessionEvidence": supersession_evidence(source_for293, source_for301, source_for302),
        "inventory": {
            "scannedRoots": [rel(root) for root in SCAN_ROOTS],
            "excludedGeneratedOutputs": [rel(path) for path in sorted(EXCLUDED_SCAN_PATHS)],
            "consumerCount": len(consumers),
            "classificationCounts": dict(sorted(classes.items())),
            "markerCounts": dict(sorted(markers.items())),
            "unsafeConsumerCount": len(unsafe),
            "ambiguousConsumerCount": len(ambiguous),
            "consumers": [
                {
                    "path": rel(result.path),
                    "forNumber": result.for_number,
                    "classification": result.classification,
                    "allowed": result.allowed,
                    "referencesFor301": result.references_for301,
                    "referencesFor302": result.references_for302,
                    "markerCounts": result.marker_counts,
                    "promotionCounts": result.promotion_counts,
                    "reason": result.reason,
                }
                for result in consumers
            ],
            "unsafeConsumers": [rel(result.path) for result in unsafe],
            "ambiguousConsumers": [rel(result.path) for result in ambiguous],
        },
        "strictPreservation": {
            "productionRenderingChanged": False,
            "normalRendererChanged": False,
            "kotlinChanged": False,
            "skAAClipOpChanged": False,
            "ctmChanged": False,
            "blendChanged": False,
            "setPixelChanged": False,
            "gpuOrWebGpuChanged": False,
            "thresholdChanged": False,
            "fallbackChanged": False,
            "m60Promoted": False,
            "historicalReportsRewritten": False,
        },
    }


def validate_audit(audit: dict[str, Any]) -> None:
    validate_synthetic_policy_cases()
    if audit.get("linear") != LINEAR_ID:
        fail("wrong Linear id")
    if audit.get("parent") != PARENT_ID:
        fail("wrong parent id")
    if audit.get("sceneId") != SCENE_ID:
        fail("wrong scene id")
    if audit.get("sourceMemory") != SOURCE_MEMORY:
        fail("mandatory source memory permalink missing")
    if audit.get("sourceMemoryStatus") == SOURCE_MEMORY_STATUS_MISMATCH:
        fail("source memory path exists but does not contain the expected permalink")
    if audit.get("decision") != DECISION_APPLIED:
        fail(f"unsafe or ambiguous FOR-293 consumer found: {audit.get('decision')}")
    decisions = audit["decisions"]
    if decisions != {
        DECISION_APPLIED: True,
        DECISION_UNSAFE: False,
        DECISION_AMBIGUOUS: False,
    }:
        fail(f"unexpected decisions: {decisions}")
    evidence = audit["supersessionEvidence"]
    if evidence["for302Decision"] != "M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT":
        fail("FOR-302 supersession decision changed")
    if evidence["for302ClipPolarityFixApplied"] is not True:
        fail("FOR-302 clip polarity correction is no longer recorded")
    if evidence["supportDecision"] != "KEEP_EXPECTED_UNSUPPORTED":
        fail("M60 support decision changed")
    if evidence["originalTargetPixels"] != 59:
        fail("original target count changed")
    if evidence["originalFor293NonZero"] != 59:
        fail("FOR-293 original-target nonzero count changed")
    if evidence["originalRuntimePathFull"] != 59:
        fail("FOR-301 runtime path full count changed")
    if evidence["originalRuntimeResultZero"] != 59:
        fail("FOR-301/FOR-302 runtime result zero count changed")
    if evidence["originalFor293RuntimeMismatches"] != 59:
        fail("FOR-293/runtime mismatch count changed")
    inventory = audit["inventory"]
    if inventory["unsafeConsumerCount"] != 0:
        fail(f"unsafe consumers present: {inventory['unsafeConsumers']}")
    if inventory["ambiguousConsumerCount"] != 0:
        fail(f"ambiguous consumers present: {inventory['ambiguousConsumers']}")
    expected_paths = {
        "scripts/validate_for293_m60_red_drawrrect_runtime_visibility_audit.py",
        "scripts/validate_for301_m60_skaaclip_band_trace.py",
        "scripts/validate_for302_m60_analytic_clip_model_reconciliation.py",
        "scripts/validate_for303_m60_analytic_model_supersession_guard.py",
        "reports/wgsl-pipeline/2026-06-03-for-293-m60-red-drawrrect-runtime-visibility-audit.md",
        "reports/wgsl-pipeline/2026-06-04-for-301-m60-skaaclip-band-trace.md",
        "reports/wgsl-pipeline/2026-06-04-for-302-m60-analytic-clip-model-reconciliation.md",
    }
    seen_paths = {consumer["path"] for consumer in inventory["consumers"]}
    missing = sorted(expected_paths - seen_paths)
    if missing:
        fail(f"expected inventory consumers missing: {missing}")
    counts = inventory["classificationCounts"]
    for classification in ("historical", "historical-comparison", "runtime-reconciliation", "supersession-guard"):
        if counts.get(classification, 0) <= 0:
            fail(f"missing classification: {classification}")
    for key, value in audit["strictPreservation"].items():
        if value is not False:
            fail(f"strict preservation `{key}` changed")


def classification_row(classification: str, count: int) -> str:
    return f"| `{classification}` | {count} |"


def consumer_rows(audit: dict[str, Any]) -> str:
    rows: list[str] = []
    for consumer in audit["inventory"]["consumers"]:
        rows.append(
            f"| `{consumer['path']}` | `{consumer['classification']}` | "
            f"{consumer['referencesFor301']} | {consumer['referencesFor302']} | `{consumer['reason']}` |"
        )
    return "\n".join(rows)


def write_report(audit: dict[str, Any]) -> None:
    counts = audit["inventory"]["classificationCounts"]
    count_rows = "\n".join(classification_row(key, counts[key]) for key in sorted(counts))
    decisions = "\n".join(f"| `{key}` | {value} |" for key, value in audit["decisions"].items())
    evidence = audit["supersessionEvidence"]
    report = f"""# FOR-303 M60 Analytic Model Supersession Guard

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Decision: `{audit["decision"]}`

## Result

FOR-303 adds an audit-only guard around the superseded FOR-293 analytic
visibility model. Historical FOR-293 through FOR-300 evidence remains
preserved, FOR-301/FOR-302 remain the superseding runtime/reconciliation
evidence, and future M60 promotion/support consumers must cite both
FOR-301 and FOR-302 before using FOR-293-derived visibility facts.

| Decision | Applied |
|---|---|
{decisions}

## Supersession Evidence

| Metric | Value |
|---|---:|
| Original target pixels | {evidence["originalTargetPixels"]} |
| FOR-293 original nonzero coverage | {evidence["originalFor293NonZero"]} |
| Runtime path full on original targets | {evidence["originalRuntimePathFull"]} |
| Runtime result zero on original targets | {evidence["originalRuntimeResultZero"]} |
| FOR-293/runtime mismatches | {evidence["originalFor293RuntimeMismatches"]} |
| Runtime formula matches | {evidence["originalRuntimeFormulaMatches"]} |
| Candidate-minus-runtime pixels | {evidence["candidateMinusRuntimePixels"]} |
| Candidate-minus-runtime result zero | {evidence["candidateMinusRuntimeResultZero"]} |

FOR-302 decision: `{evidence["for302Decision"]}`.
M60 remains `{evidence["supportDecision"]}`.

## Inventory Summary

| Classification | Consumers |
|---|---:|
{count_rows}

Unsafe consumers: `{audit["inventory"]["unsafeConsumerCount"]}`.
Ambiguous consumers: `{audit["inventory"]["ambiguousConsumerCount"]}`.

## Consumer Inventory

| Path | Classification | FOR-301 | FOR-302 | Reason |
|---|---|---:|---:|---|
{consumer_rows(audit)}

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{AUDIT_NAME}`
"""
    REPORT.write_text(report, encoding="utf-8")


def main() -> None:
    audit = analyze()
    validate_audit(audit)
    SCENE_DIR.mkdir(parents=True, exist_ok=True)
    AUDIT.write_text(json_dump(audit), encoding="utf-8")
    write_report(audit)
    print(f"FOR-303 audit written: {AUDIT.relative_to(PROJECT_ROOT)}")
    print(f"FOR-303 report written: {REPORT.relative_to(PROJECT_ROOT)}")
    print(
        "FOR-303 decision: "
        f"{audit['decision']} "
        f"consumers={audit['inventory']['consumerCount']} "
        f"unsafe={audit['inventory']['unsafeConsumerCount']} "
        f"ambiguous={audit['inventory']['ambiguousConsumerCount']} "
        f"originalMismatch={audit['supersessionEvidence']['originalFor293RuntimeMismatches']}"
    )


if __name__ == "__main__":
    main()
