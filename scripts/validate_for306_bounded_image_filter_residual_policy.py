#!/usr/bin/env python3
"""Generate and validate FOR-306 bounded image-filter residual policy guard."""

from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-306"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-bounded-image-filter-residual-policy-guard-ticket"
)
ARTIFACT_NAME = "bounded-image-filter-residual-policy-for306.json"
ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/bounded-image-filter-residual-policy-for306"
)
ARTIFACT = ARTIFACT_DIR / ARTIFACT_NAME
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-306-bounded-image-filter-residual-policy.md"
)

DECISION_APPLIED = "IMAGE_FILTER_RESIDUAL_POLICY_GUARD_APPLIED"
DECISION_UNSAFE = "IMAGE_FILTER_RESIDUAL_POLICY_UNSAFE_PROMOTION_FOUND"
DECISION_AMBIGUOUS = "IMAGE_FILTER_RESIDUAL_POLICY_AMBIGUOUS_CONSUMER_FOUND"

FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
REMAINING_BOUNDARY = "rgba16float-intermediate-store-to-present-byte-quantization-policy"
TARGET_ROUTE = "webgpu.image-filter.offset-crop-prepass-and-src-over"

SOURCE_AUDITS = [
    {
        "linear": "FOR-261",
        "path": "reports/wgsl-pipeline/scenes/generated/artifacts/"
        "whole-scene-rgba8-intermediate-audit-for261/"
        "whole-scene-rgba8-intermediate-audit-for261.json",
        "signal": "RGBA8Unorm intermediate candidate",
        "expectedFinding": "whole_scene_rgba8_intermediate_candidate_observed_but_precision_fixture_not_corrected",
        "expectedMissingCondition": "missing_precision_sensitive_whole_scene_rgba8_intermediate_correction_without_targetColorSpaceBlend",
    },
    {
        "linear": "FOR-262",
        "path": "reports/wgsl-pipeline/scenes/generated/artifacts/"
        "target-colorspace-blend-scope-audit-for262/"
        "target-colorspace-blend-scope-audit-for262.json",
        "signal": "targetColorSpaceBlend scope",
        "expectedFinding": "targetColorSpaceBlend_corrects_isolated_neutral_aa_but_safe_family_scope_not_proven",
        "expectedMissingCondition": "missing_family_bound_proof_for_target_colorspace_blend_and_intermediate_boundary_separation",
    },
    {
        "linear": "FOR-263",
        "path": "reports/wgsl-pipeline/scenes/generated/artifacts/"
        "target-blend-intermediate-matrix-audit-for263/"
        "target-blend-intermediate-matrix-audit-for263.json",
        "signal": "targetColorSpaceBlend x intermediateFormat matrix",
        "expectedFinding": "targetColorSpaceBlend_and_intermediateFormat_correct_different_boundaries_without_safe_shared_scope",
        "expectedMissingCondition": "missing_family_bound_proof_for_target_colorspace_blend_and_intermediate_boundary_separation",
    },
    {
        "linear": "FOR-264",
        "path": "reports/wgsl-pipeline/scenes/generated/artifacts/"
        "rgba16float-present-quantization-audit-for264/"
        "rgba16float-present-quantization-audit-for264.json",
        "signal": "RGBA16Float present byte quantization",
        "expectedFinding": "rgba16float_present_quantization_reconstructs_residual_samples_but_targetColorSpaceBlend_boundary_stays_separate",
        "expectedMissingCondition": "missing_family_bound_proof_that_rgba16float_present_byte_quantization_is_safe_without_targetColorSpaceBlend",
    },
    {
        "linear": "FOR-265",
        "path": "reports/wgsl-pipeline/scenes/generated/artifacts/"
        "rgba16float-quantization-family-scope-for265/"
        "rgba16float-quantization-family-scope-for265.json",
        "signal": "RGBA16Float quantization family scope",
        "expectedFinding": "rgba16float_present_quantization_family_scope_not_proven_targetColorSpaceBlend_boundary_stays_separate",
        "expectedMissingCondition": "missing_family_bound_proof_that_rgba16float_present_byte_quantization_is_safe_without_targetColorSpaceBlend",
    },
]

REQUIRED_LOCAL_PROOF = {
    "referenceArtifact",
    "cpuArtifact",
    "gpuArtifact",
    "diffStats",
    "routeDiagnostics",
    "fallbackStable",
    "strictLocalImprovement",
    "noGlobalThresholdChange",
    "noGlobalTargetColorSpaceBlend",
}


@dataclass(frozen=True)
class GuardCase:
    name: str
    signals: set[str]
    proof: set[str]
    global_target_blend: bool
    promotion_claim: bool
    expected_decision: str


def fail(message: str) -> None:
    raise SystemExit(f"FOR-306 validation failed: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def load_json(relative: str) -> dict[str, Any]:
    path = PROJECT_ROOT / relative
    if not path.is_file():
        fail(f"missing JSON file: {relative}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{relative} must contain a JSON object")
    return data


def require_text(owner: dict[str, Any], field: str, expected: str) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"{field} expected `{expected}`, got `{actual}`")


def audit_summary(spec: dict[str, str]) -> dict[str, Any]:
    data = load_json(spec["path"])
    require_text(data, "supportDecision", "KEEP_DIAGNOSTIC")
    require_text(data, "finding", spec["expectedFinding"])
    require_text(data, "missingCondition", spec["expectedMissingCondition"])
    require_text(data, "remainingBoundary", REMAINING_BOUNDARY)
    require_text(data, "preservedUnsupportedReason", FALLBACK_REASON)
    correction = data.get("admissibleCorrection")
    if not isinstance(correction, str) or not correction.startswith("none_applied:"):
        fail(f"{spec['linear']} admissibleCorrection must remain none_applied")
    return {
        "linear": spec["linear"],
        "source": spec["path"],
        "signal": spec["signal"],
        "supportDecision": data["supportDecision"],
        "finding": data["finding"],
        "missingCondition": data["missingCondition"],
        "remainingBoundary": data["remainingBoundary"],
        "admissibleCorrection": correction,
        "preservedUnsupportedReason": data["preservedUnsupportedReason"],
    }


def classify_guard_case(case: GuardCase) -> tuple[str, bool, str]:
    if not case.promotion_claim:
        return (
            "diagnostic-inventory",
            True,
            "Diagnostic inventory is allowed when it does not claim support or promotion.",
        )
    if case.global_target_blend:
        return (
            "forbidden",
            False,
            "Global targetColorSpaceBlend activation is not an allowed image-filter residual support proof.",
        )
    missing = sorted(REQUIRED_LOCAL_PROOF - case.proof)
    if missing:
        return (
            "forbidden",
            False,
            "Promotion lacks required local proof fields: " + ", ".join(missing),
        )
    if not case.signals:
        return (
            "ambiguous",
            False,
            "Promotion does not name the residual signal being superseded.",
        )
    return (
        "candidate-local-proof",
        True,
        "Promotion is allowed only as a candidate because complete local proof is present.",
    )


def guard_cases() -> list[dict[str, Any]]:
    cases = [
        GuardCase(
            name="FOR-261/FOR-265 diagnostic signals alone must not promote image-filter support",
            signals={"RGBA8Unorm", "RGBA16Float present quantization"},
            proof={"referenceArtifact", "gpuArtifact"},
            global_target_blend=False,
            promotion_claim=True,
            expected_decision="forbidden",
        ),
        GuardCase(
            name="Global targetColorSpaceBlend activation must not promote image-filter support",
            signals={"targetColorSpaceBlend"},
            proof=REQUIRED_LOCAL_PROOF,
            global_target_blend=True,
            promotion_claim=True,
            expected_decision="forbidden",
        ),
        GuardCase(
            name="Diagnostic inventory without support claim is allowed",
            signals={"RGBA8Unorm"},
            proof=set(),
            global_target_blend=False,
            promotion_claim=False,
            expected_decision="diagnostic-inventory",
        ),
        GuardCase(
            name="Complete local proof can become a support candidate",
            signals={"local renderer correction"},
            proof=REQUIRED_LOCAL_PROOF,
            global_target_blend=False,
            promotion_claim=True,
            expected_decision="candidate-local-proof",
        ),
    ]
    rows: list[dict[str, Any]] = []
    for case in cases:
        decision, allowed, reason = classify_guard_case(case)
        if decision != case.expected_decision:
            fail(f"guard case `{case.name}` expected {case.expected_decision}, got {decision}")
        rows.append(
            {
                "name": case.name,
                "signals": sorted(case.signals),
                "proof": sorted(case.proof),
                "globalTargetColorSpaceBlend": case.global_target_blend,
                "promotionClaim": case.promotion_claim,
                "decision": decision,
                "allowed": allowed,
                "reason": reason,
            }
        )
    return rows


def scan_current_future_consumers() -> list[dict[str, Any]]:
    roots = [PROJECT_ROOT / "scripts", PROJECT_ROOT / "reports/wgsl-pipeline"]
    suffixes = {".py", ".md", ".json"}
    future = []
    number_pattern = re.compile(r"\bFOR[-_]?(\d{3})\b|for[-_]?(\d{3})", re.IGNORECASE)
    promotion_pattern = re.compile(r"\b(promot|support|supported|pass row|readiness movement)\b", re.IGNORECASE)
    signal_pattern = re.compile(
        r"RGBA8Unorm|RGBA16Float|targetColorSpaceBlend|"
        r"rgba16float-intermediate-store-to-present-byte-quantization-policy|"
        r"image-filter\.crop-input-nonnull-prepass-required"
    )
    for root in roots:
        for path in root.rglob("*"):
            if path.suffix not in suffixes or path in {ARTIFACT, REPORT}:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            numbers = [
                int(match.group(1) or match.group(2))
                for match in number_pattern.finditer(str(path) + "\n" + text)
            ]
            if not numbers or max(numbers) <= 306:
                continue
            if signal_pattern.search(text) and promotion_pattern.search(text):
                future.append(
                    {
                        "path": rel(path),
                        "forNumber": max(numbers),
                        "classification": "future-consumer-requires-FOR-306-policy-check",
                    }
                )
    return future


def build_artifact() -> dict[str, Any]:
    source_audits = [audit_summary(spec) for spec in SOURCE_AUDITS]
    guard_rows = guard_cases()
    future_consumers = scan_current_future_consumers()
    unsafe = [row for row in guard_rows if row["decision"] == "forbidden" and row["allowed"]]
    ambiguous = [row for row in guard_rows if row["decision"] == "ambiguous"]
    if unsafe:
        decision = DECISION_UNSAFE
    elif ambiguous or future_consumers:
        decision = DECISION_AMBIGUOUS
    else:
        decision = DECISION_APPLIED
    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": decision,
        "policy": {
            "targetRoute": TARGET_ROUTE,
            "preservedFallbackReason": FALLBACK_REASON,
            "remainingBoundary": REMAINING_BOUNDARY,
            "diagnosticOnlySignals": [
                "RGBA8Unorm intermediate candidate",
                "RGBA16Float present byte quantization",
                "targetColorSpaceBlend",
            ],
            "requiredLocalProof": sorted(REQUIRED_LOCAL_PROOF),
            "forbiddenPromotionInputs": [
                "diagnostic-only RGBA8Unorm improvement without strict local parity",
                "diagnostic-only RGBA16Float present quantization family scope",
                "global targetColorSpaceBlend activation",
                "threshold weakening",
                "fallback removal without row-local reference/CPU/GPU/diff/stat evidence",
            ],
        },
        "sourceAudits": source_audits,
        "guardCases": guard_rows,
        "futureConsumers": future_consumers,
        "strictPreservation": {
            "rendererChanged": False,
            "shaderChanged": False,
            "thresholdChanged": False,
            "fallbackChanged": False,
            "sceneStatusChanged": False,
            "historicalReportsRewritten": False,
        },
    }


def write_artifact(artifact: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(artifact, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(artifact: dict[str, Any]) -> None:
    audit_rows = "\n".join(
        "| `{linear}` | {signal} | `{supportDecision}` | `{missingCondition}` |".format(**row)
        for row in artifact["sourceAudits"]
    )
    guard_rows = "\n".join(
        "| {name} | `{decision}` | `{allowed}` | {reason} |".format(**row)
        for row in artifact["guardCases"]
    )
    REPORT.write_text(
        f"""# FOR-306 Bounded Image-Filter Residual Policy

Linear: `FOR-306`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{artifact['decision']}`

## Result

FOR-306 codifies the bounded image-filter residual policy without changing the
renderer, shaders, thresholds, fallbacks, or scene statuses. The diagnostic
signals from FOR-261 to FOR-265 remain evidence, not support claims.

The preserved fallback reason remains:

```text
{FALLBACK_REASON}
```

The remaining boundary remains:

```text
{REMAINING_BOUNDARY}
```

## Source Audits

| Audit | Signal | Decision | Missing condition |
|---|---|---|---|
{audit_rows}

## Policy

The following signals are diagnostic-only for `{TARGET_ROUTE}` until a future
ticket provides complete row-local proof:

- `RGBA8Unorm` intermediate improvement;
- `RGBA16Float` present-byte quantization reconstruction;
- `targetColorSpaceBlend` correction signal.

Required local proof for any future support candidate:

- reference artifact;
- CPU artifact;
- GPU artifact;
- diff/stat artifact;
- route diagnostics;
- stable fallback policy;
- strict local improvement;
- no global threshold change;
- no global `targetColorSpaceBlend` enablement.

## Guard Cases

| Case | Decision | Allowed | Reason |
|---|---|---|---|
{guard_rows}

## Validation

- `rtk python3 scripts/validate_for306_bounded_image_filter_residual_policy.py`
- `rtk python3 scripts/validate_for265_rgba16float_quantization_family_scope.py`
- `rtk python3 scripts/validate_for264_rgba16float_present_quantization_audit.py`
- `rtk python3 scripts/validate_for263_target_blend_intermediate_matrix_audit.py`
- `rtk python3 scripts/validate_for261_whole_scene_rgba8_intermediate_audit.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
""",
        encoding="utf-8",
    )


def validate_outputs(artifact: dict[str, Any]) -> None:
    if artifact["decision"] != DECISION_APPLIED:
        fail(f"expected decision {DECISION_APPLIED}, got {artifact['decision']}")
    if len(artifact["sourceAudits"]) != 5:
        fail("expected five source audits")
    forbidden = [row for row in artifact["guardCases"] if row["decision"] == "forbidden"]
    if len(forbidden) != 2:
        fail("expected two forbidden synthetic guard cases")
    candidate = [row for row in artifact["guardCases"] if row["decision"] == "candidate-local-proof"]
    if len(candidate) != 1 or candidate[0]["allowed"] is not True:
        fail("expected one allowed complete-local-proof candidate")
    if artifact["futureConsumers"]:
        fail(f"future consumers require policy review: {artifact['futureConsumers']}")
    report_text = REPORT.read_text(encoding="utf-8")
    for needle in (DECISION_APPLIED, FALLBACK_REASON, REMAINING_BOUNDARY, "targetColorSpaceBlend"):
        if needle not in report_text:
            fail(f"report missing `{needle}`")


def main() -> None:
    artifact = build_artifact()
    write_artifact(artifact)
    write_report(artifact)
    validate_outputs(artifact)
    print(
        "FOR-306 validation passed: "
        f"{artifact['decision']} with {len(artifact['sourceAudits'])} source audits "
        f"and {len(artifact['guardCases'])} guard cases"
    )


if __name__ == "__main__":
    main()
