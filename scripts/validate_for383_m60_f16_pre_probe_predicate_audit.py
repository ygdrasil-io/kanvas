#!/usr/bin/env python3
"""Validate the FOR-383 M60 F16 pre-probe predicate audit evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-383"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-pre-probe-predicate-audit-for383/"
    "m60-f16-pre-probe-predicate-audit-for383.json"
)
FOR382_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-coverage-composition-membership-audit-for382/"
    "m60-f16-coverage-composition-membership-audit-for382.json"
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-383-m60-f16-pre-probe-predicate-audit.md"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

DECISION = "M60_F16_PRE_PROBE_PREDICATE_AUDIT_RECORDED"
CLASSIFICATION = "pre-probe-predicate-too-broad"
FOR382_DECISION = "M60_F16_COVERAGE_COMPOSITION_MEMBERSHIP_AUDIT_RECORDED"
FOR382_CLASSIFICATION = (
    "local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof"
)
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-predicate-moteur-independant-du-probe-apres-for-382"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-382-separe-les-8-pixels-ameliores-m60-f16-mais-confirme-quil-manque-un-predicate-moteur-independant-du-probe"
)
EXPECTED_FULL_SCENE = {
    "uncorrectedSimilarity": 95.91,
    "correctedSimilarity": 87.06,
    "uncorrectedMismatchPixels": 1004,
    "correctedMismatchPixels": 3181,
    "uncorrectedGreaterThanEightPixels": 10,
    "correctedGreaterThanEightPixels": 3164,
}
EXPECTED_TRUTH_SETS = {
    "allAuditedPixels": (24576, 2014, 231162, 229148),
    "improved": (8, 734, 669, -65),
    "regressed": (3171, 1274, 230487, 229213),
    "unchanged": (21397, 6, 6, 0),
    "for379Critical": (10, 856, 816, -40),
}
EXPECTED_CATEGORIES = {
    "source-locale-plausible": 8,
    "coverage-composition-plausible": 3024,
    "mixed": 147,
    "insufficient": 21397,
}
EXPECTED_CANDIDATES = {
    "partial-coverage-and-source-alpha": (451, 8, 443, 0.0177, 1.0, 1146, 21556, "too-broad"),
    "partial-alpha-current-residual-high": (10, 8, 2, 0.8, 1.0, 856, 816, "too-broad"),
    "partial-alpha-current-error-shape": (9, 8, 1, 0.8889, 1.0, 790, 734, "too-broad"),
    "partial-alpha-round-or-butt-fringe": (9, 8, 1, 0.8889, 1.0, 790, 734, "too-broad"),
    "partial-alpha-current-low": (441, 0, 441, 0.0, 0.0, 290, 20740, "proof-missing"),
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-383 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def channel_dict(value: Any, label: str) -> dict[str, int]:
    require(isinstance(value, dict), f"{label} must be an object")
    out: dict[str, int] = {}
    for channel in ("r", "g", "b", "a"):
        raw = value.get(channel)
        require(isinstance(raw, int), f"{label}.{channel} must be int")
        out[channel] = raw
    return out


def validate_set(name: str, value: Any, expected: tuple[int, int, int, int]) -> None:
    require(isinstance(value, dict), f"{name} must be object")
    count, before, after, delta = expected
    require(value.get("count") == count, f"{name} count changed")
    require(value.get("beforeResidual") == before, f"{name} before residual changed")
    require(value.get("afterResidual") == after, f"{name} after residual changed")
    require(value.get("deltaVsCurrent") == delta, f"{name} delta changed")
    require(value.get("gainVsCurrent") == -delta, f"{name} gain changed")
    before_channels = channel_dict(value.get("beforeErrorByChannel"), f"{name}.beforeErrorByChannel")
    after_channels = channel_dict(value.get("afterErrorByChannel"), f"{name}.afterErrorByChannel")
    require(sum(before_channels.values()) == before, f"{name} before channel total mismatch")
    require(sum(after_channels.values()) == after, f"{name} after channel total mismatch")


def validate_source() -> None:
    require(CAPTURE_PRODUCER.is_file(), "capture producer missing")
    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    for needle in (
        "writeM60F16PreProbePredicateAudit(",
        "m60F16PreProbePredicateAuditJson(",
        "preProbePredicateCandidates()",
        '"decision": "M60_F16_PRE_PROBE_PREDICATE_AUDIT_RECORDED"',
        SOURCE_MEMORY,
        SOURCE_FINDING,
        '"candidateSelectionUsesProbeOutcome": false',
        '"candidateSelectionUsesProbeResidual": false',
        '"candidateSelectionUsesDeltaVsCurrent": false',
        '"candidateSelectionUsesFor379MembershipAsPrimary": false',
        '"correctionAppliedByDefault": false',
        '"probeEnabledByDefault": false',
        '"correctionPredicateEnabled": false',
    ):
        require(needle in source, f"capture producer missing {needle}")


def validate_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor382Decision") == FOR382_DECISION, "FOR-382 decision requirement changed")
    require(data.get("requiredFor382Classification") == FOR382_CLASSIFICATION, "FOR-382 class requirement changed")
    require(data.get("auditDoesNotProduceCorrection") is True, "audit must not produce correction")
    require(data.get("auditDoesNotApplyRendererChange") is True, "audit must not apply renderer change")
    require(data.get("correctionKept") is False, "correction must remain refused")
    require(data.get("correctionAppliedByDefault") is False, "correction must remain disabled")

    rules = data.get("predicateSearchRules")
    require(isinstance(rules, dict), "predicate search rules missing")
    for key in (
        "candidateSelectionUsesProbeOutcome",
        "candidateSelectionUsesProbeResidual",
        "candidateSelectionUsesDeltaVsCurrent",
        "candidateSelectionUsesFor379MembershipAsPrimary",
    ):
        require(rules.get(key) is False, f"{key} must be false")
    require(rules.get("probeOutcomeUsedOnlyAsEvaluationTruth") is True, "probe outcome evaluation guard missing")
    require(rules.get("for382SourceLocalCategoryUsedOnlyAsEvaluationTruth") is True, "FOR-382 truth guard missing")

    signals = data.get("preProbeSignals")
    require(isinstance(signals, dict), "preProbeSignals missing")
    require(signals.get("rendererRuntimePredicateReady") is False, "runtime predicate must remain blocked")
    require(signals.get("referenceRequiredForResidualSignals") is True, "reference dependency must be explicit")

    guard = data.get("fullSceneGuard")
    require(isinstance(guard, dict), "fullSceneGuard missing")
    for key, expected in EXPECTED_FULL_SCENE.items():
        require(guard.get(key) == expected, f"fullSceneGuard.{key} changed")
    require(guard.get("refusalsChanged") is False, "refusal stability changed")

    truth = data.get("truthSetsForEvaluationOnly")
    require(isinstance(truth, dict), "truth sets missing")
    for name, expected in EXPECTED_TRUTH_SETS.items():
        validate_set(name, truth.get(name), expected)

    categories = data.get("for382CategoriesForEvaluationOnly")
    require(isinstance(categories, dict), "FOR-382 categories missing")
    for name, expected_count in EXPECTED_CATEGORIES.items():
        validate_set(name, categories.get(name), (expected_count, categories[name]["beforeResidual"], categories[name]["afterResidual"], categories[name]["deltaVsCurrent"]))

    candidates_raw = data.get("candidatePredicates")
    require(isinstance(candidates_raw, list), "candidatePredicates must be list")
    candidates = {candidate.get("id"): candidate for candidate in candidates_raw if isinstance(candidate, dict)}
    require(set(candidates) == set(EXPECTED_CANDIDATES), "candidate ids changed")
    for candidate_id, expected in EXPECTED_CANDIDATES.items():
        selected, recovered, regressed, precision, recall, before, after, candidate_class = expected
        candidate = candidates[candidate_id]
        require(candidate.get("usesOutcomeOracle") is False, f"{candidate_id} uses outcome oracle")
        require(candidate.get("usesProbeResidualForSelection") is False, f"{candidate_id} uses probe residual")
        require(candidate.get("usesDeltaVsCurrentForSelection") is False, f"{candidate_id} uses delta outcome")
        require(candidate.get("usesFor379MembershipAsPrimary") is False, f"{candidate_id} uses FOR-379 membership")
        require(candidate.get("selectedPixels") == selected, f"{candidate_id} selected count changed")
        require(candidate.get("sourceLocalRecovered") == recovered, f"{candidate_id} recovered count changed")
        require(candidate.get("regressedPixelsIncluded") == regressed, f"{candidate_id} regressed count changed")
        require(abs(candidate.get("precision") - precision) < 0.00005, f"{candidate_id} precision changed")
        require(abs(candidate.get("recall") - recall) < 0.00005, f"{candidate_id} recall changed")
        require(candidate.get("candidateClass") == candidate_class, f"{candidate_id} class changed")
        validate_set(f"{candidate_id}.diagnosticSelectionResidual", candidate.get("diagnosticSelectionResidual"), (selected, before, after, after - before))

    best = data.get("bestCandidate")
    require(isinstance(best, dict), "bestCandidate missing")
    require(best.get("id") == "partial-alpha-current-error-shape", "best candidate changed")
    require(best.get("selectedPixels") == 9, "best candidate selected count changed")
    require(best.get("sourceLocalRecovered") == 8, "best candidate recovered count changed")
    require(best.get("regressedPixelsIncluded") == 1, "best candidate regressed count changed")
    require(best.get("runtimePredicateReady") is False, "best candidate must not be runtime-ready")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "non-goals missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal {key} must remain false")
    return data


def validate_for382() -> None:
    data = load_json(FOR382_ARTIFACT)
    require(data.get("decision") == FOR382_DECISION, "FOR-382 decision changed")
    require(data.get("classification") == FOR382_CLASSIFICATION, "FOR-382 classification changed")
    require(data.get("correctionAppliedByDefault") is False, "FOR-382 correction must remain disabled")


def validate_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for needle in (
        DECISION,
        CLASSIFICATION,
        "partial-alpha-current-error-shape",
        "9 | 8 | 1 | 0.8889 | 1.0000 | 790 -> 734",
        "diagnostic-only",
        "aucune correction renderer",
        "preuve de geometrie/couverture",
    ):
        require(needle in report, f"report missing {needle}")
    require(data["bestCandidate"]["id"] in report, "report does not mention best candidate")


def main() -> None:
    validate_source()
    validate_for382()
    data = validate_artifact()
    validate_report(data)
    print("FOR-383 M60 F16 pre-probe predicate audit validation passed")


if __name__ == "__main__":
    main()
