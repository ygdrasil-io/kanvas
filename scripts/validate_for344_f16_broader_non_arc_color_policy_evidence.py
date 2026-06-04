#!/usr/bin/env python3
"""Validate the FOR-344 broader non-arc F16 color-policy evidence artifact."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-344"
SCENE_ID = "f16-broader-non-arc-color-policy-for344"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-344-f16-broader-non-arc-color-policy-evidence.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-broader-non-arc-f16-color-policy-evidence-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-343-f16-color-policy-boundary-ready-for-broader-evidence-finding"
)

FOR343_SCENE_ID = "f16-color-policy-boundary-for343"
FOR343_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR343_SCENE_ID / f"{FOR343_SCENE_ID}.json"
)
FOR343_REQUIRED_DECISION = "F16_COLOR_POLICY_BOUNDARY_READY_FOR_BROADER_EVIDENCE"

FOR342_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation-for342"
FOR342_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR342_SCENE_ID / f"{FOR342_SCENE_ID}.json"
)
FOR342_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_SCOPED_IMPLEMENTATION_PARTIAL_REQUIRES_SAFER_ROUTE"
)

FOR337_SCENE_ID = "circular-arcs-stroke-butt-f16-color-policy-cross-scene-evidence-for337"
FOR337_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR337_SCENE_ID / f"{FOR337_SCENE_ID}.json"
)
FOR337_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_CROSS_SCENE_EVIDENCE_MIXED_REQUIRES_MORE_DATA"
)

M60_AA_DIAGNOSTIC = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/aa-residual-diagnostic.json"
)
M60_ROUTE_GPU = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-gpu.json"
M60_STATS = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/stats.json"
M60_NEUTRAL_AA_STATS = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts/m60-target-colorspace-neutral-aa/stats.json"
)
M60_CANDIDATE_INVENTORY = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts/m60-target-color-candidate-inventory.json"

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

DECISION_READY = "F16_BROADER_NON_ARC_EVIDENCE_READY_FOR_POLICY_DECISION"
DECISION_PARTIAL = "F16_BROADER_NON_ARC_EVIDENCE_PARTIAL_REQUIRES_MORE_REFERENCE_ROWS"
DECISION_REJECTS = "F16_BROADER_NON_ARC_EVIDENCE_REJECTS_GLOBAL_MIGRATION"
DECISION_INPUT_INVALID = "F16_BROADER_NON_ARC_EVIDENCE_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_READY, DECISION_PARTIAL, DECISION_REJECTS, DECISION_INPUT_INVALID]

CANDIDATE_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"

REQUIRED_UNSAFE_ROUTE_CODES = [
    "F16_POLICY_UNSAFE_SELECTED_CELL_SUBSTITUTION",
    "F16_POLICY_UNSAFE_FIXTURE_BRANCH",
    "F16_POLICY_UNSAFE_COORDINATE_BRANCH",
    "F16_POLICY_UNSAFE_GLOBAL_HOOK_MUTATION",
    "F16_POLICY_UNSAFE_FULL_GM_CROP",
    "F16_POLICY_UNSAFE_THRESHOLD_RELAXATION",
]

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for344_f16_broader_non_arc_color_policy_evidence.py",
    "rtk python3 scripts/validate_for343_f16_color_policy_boundary.py",
    "rtk python3 scripts/validate_for337_circular_arcs_stroke_butt_f16_color_policy_cross_scene_evidence.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-344 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


def validate_source_guardrails() -> None:
    required = {
        SK_BITMAP_DEVICE: [
            "private fun colorToF16Premul(c: SkColor4f, out: FloatArray)",
            "private fun blendF16PremulMode(",
            "blendF16PremulMode(x, y, sr * cov, sg * cov, sb * cov, saCov, paint.blendMode)",
        ],
        SK_BITMAP: [
            "public fun getPixelAsSrgb",
            "getPixel] preserves the historical internal byte oracle",
        ],
        SK_PNG_ENCODER: [
            "SkBitmap.getPixelAsSrgb",
            "src.getPixelAsSrgb(x, y)",
        ],
    }
    for path, snippets in required.items():
        if not path.is_file():
            fail(f"missing source file: {rel(path)}")
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            require(snippet in text, f"{rel(path)} missing required snippet: {snippet}")


def validate_for343(for343: dict[str, Any]) -> None:
    require(for343.get("linear") == "FOR-343", "FOR-343 artifact identity changed")
    require(for343.get("decision") == FOR343_REQUIRED_DECISION, "FOR-343 boundary-ready decision is missing")
    require(for343.get("sourceFindings") == [
        "global/kanvas/findings/for-342-circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation-partial-safer-route-finding"
    ], "FOR-343 source finding changed")

    boundary = for343.get("boundary")
    require(isinstance(boundary, dict), "FOR-343 boundary block missing")
    require(boundary.get("id") == BOUNDARY_ID, "FOR-343 boundary id changed")
    require(boundary.get("rendererBehaviorChanged") is False, "FOR-343 changed renderer behavior")
    require(boundary.get("globalF16RendererChangeAllowedNow") is False, "FOR-343 allows global F16 change")
    require(boundary.get("broaderEvidenceCollectionAllowed") is True, "FOR-343 does not allow broader evidence")

    diagnostics = for343.get("dangerousRouteDiagnostics")
    require(isinstance(diagnostics, list), "FOR-343 dangerousRouteDiagnostics missing")
    diag_status = {item.get("code"): item.get("status") for item in diagnostics if isinstance(item, dict)}
    for code in (
        "F16_POLICY_UNSAFE_FIXTURE_BRANCH",
        "F16_POLICY_UNSAFE_COORDINATE_BRANCH",
        "F16_POLICY_UNSAFE_SELECTED_CELL_SUBSTITUTION",
        "F16_POLICY_UNSAFE_FULL_GM_CROP",
        "F16_POLICY_UNSAFE_GLOBAL_HOOK_MUTATION_WITHOUT_BOUNDARY",
    ):
        require(diag_status.get(code) == "rejected", f"FOR-343 unsafe route not rejected: {code}")

    non_goals = for343.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "FOR-343 nonGoalsPreserved missing")
    for key in (
        "colorToF16Premul",
        "blendF16PremulMode",
        "skBitmapGetPixelInternalOracle",
        "skBitmapGetPixelAsSrgbExportBoundary",
        "geometry",
        "coveragePolicy",
        "gpu",
        "wgsl",
        "thresholds",
        "fallbacks",
        "kadre",
        "promotion",
        "score",
    ):
        require(non_goals.get(key) is True, f"FOR-343 non-goal guard changed: {key}")


def validate_inputs(for342: dict[str, Any], for337: dict[str, Any]) -> None:
    require(for342.get("linear") == "FOR-342", "FOR-342 artifact identity changed")
    require(for342.get("decision") == FOR342_REQUIRED_DECISION, "FOR-342 safer-route decision changed")
    require(for342.get("implementation", {}).get("rendererBehaviorChanged") is False, "FOR-342 changed renderer")
    require(for342.get("implementation", {}).get("implementationApplied") is False, "FOR-342 applied renderer behavior")
    require(for342.get("policy", {}).get("sourceCandidatePolicyId") == CANDIDATE_POLICY_ID, "FOR-342 policy changed")
    totals = for342.get("residualTotals")
    require(isinstance(totals, dict), "FOR-342 residualTotals missing")
    require(totals.get("oldCurrentOverWhiteResidual") == 375, "FOR-342 current residual changed")
    require(totals.get("actualNewOverWhiteResidual") == 375, "FOR-342 actual-new residual changed")
    require(totals.get("candidateNewOverWhiteResidual") == 0, "FOR-342 candidate residual changed")

    require(for337.get("linear") == "FOR-337", "FOR-337 artifact identity changed")
    require(for337.get("decision") == FOR337_REQUIRED_DECISION, "FOR-337 mixed evidence decision changed")
    summary = for337.get("summary")
    require(isinstance(summary, dict), "FOR-337 summary missing")
    require(summary.get("requiresMoreData") is True, "FOR-337 no longer requires more data")
    require(summary.get("supportsImplementation") is False, "FOR-337 now supports implementation unexpectedly")


def sum_sample_residual(samples: list[dict[str, Any]], current_key: str, reference_key: str) -> int | None:
    total = 0
    counted = 0
    for sample in samples:
        current = sample.get(current_key)
        reference = sample.get(reference_key)
        if isinstance(current, list) and isinstance(reference, list) and len(current) == 4 and len(reference) == 4:
            total += sum(abs(int(current[i]) - int(reference[i])) for i in range(4))
            counted += 1
    if counted == 0:
        return None
    return total


def high_delta_residual(samples: list[dict[str, Any]]) -> int:
    total = 0
    for sample in samples:
        ref = sample.get("referenceRgba")
        gpu = sample.get("gpuRgba")
        if isinstance(ref, list) and isinstance(gpu, list) and len(ref) == 4 and len(gpu) == 4:
            total += sum(abs(int(gpu[i]) - int(ref[i])) for i in range(4))
    return total


def matrix_row(
    *,
    row_id: str,
    family: str,
    source_kind: str,
    scene_id: str,
    non_arc: bool,
    rec2020_f16_src_over_or_blend_signal: bool,
    comparable: bool,
    reference_status: str,
    current_status: str,
    candidate_status: str,
    sample_count: int,
    current_residual: int | None,
    candidate_residual: int | None,
    worsened_samples: int | None,
    source_artifacts: list[str],
    reason: str,
    notes: list[str],
    sample_preview: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    candidate_minus_current = None
    residual_reduction = None
    if current_residual is not None and candidate_residual is not None:
        candidate_minus_current = candidate_residual - current_residual
        residual_reduction = current_residual - candidate_residual
    return {
        "rowId": row_id,
        "family": family,
        "sceneId": scene_id,
        "sourceKind": source_kind,
        "nonArc": non_arc,
        "rec2020F16SrcOverOrBlendSignal": rec2020_f16_src_over_or_blend_signal,
        "referenceCurrentCandidateComparable": comparable,
        "reference": {"status": reference_status},
        "current": {"status": current_status},
        "candidate": {"policyId": CANDIDATE_POLICY_ID, "status": candidate_status},
        "sampleCount": sample_count,
        "currentResidual": current_residual,
        "candidateResidual": candidate_residual,
        "candidateMinusCurrentResidual": candidate_minus_current,
        "residualReduction": residual_reduction,
        "worsenedSamples": worsened_samples,
        "sourceArtifacts": source_artifacts,
        "classificationReason": reason,
        "notes": notes,
        "samplePreview": sample_preview or [],
    }


def build_arc_prerequisite_row(for342: dict[str, Any]) -> dict[str, Any]:
    totals = for342["residualTotals"]
    return matrix_row(
        row_id="arc-prerequisite-for342-adjacent-cells",
        family="circular-arcs-stroke-butt",
        scene_id=FOR342_SCENE_ID,
        source_kind="arc-adjacent-old-current-actual-new-candidate",
        non_arc=False,
        rec2020_f16_src_over_or_blend_signal=True,
        comparable=True,
        reference_status="isolated-skia-over-white-reference-available",
        current_status="old-current-and-actual-new-renderer-match-unchanged",
        candidate_status="computed-policy-samples-available-not-applied",
        sample_count=int(totals["sampleCount"]),
        current_residual=int(totals["actualNewOverWhiteResidual"]),
        candidate_residual=int(totals["candidateNewOverWhiteResidual"]),
        worsened_samples=0,
        source_artifacts=[rel(FOR342_ARTIFACT)],
        reason=(
            "FOR-342 keeps the known arc-adjacent prerequisite visible: the candidate matches "
            "the isolated Skia-over-white samples, but renderer behavior was intentionally not changed."
        ),
        notes=[
            "This is not the non-arc proof required for a global color-policy migration.",
            "The row prevents FOR-344 from losing the source candidate and safer-route context.",
        ],
    )


def build_non_arc_m60_row(m60_aa: dict[str, Any], m60_route: dict[str, Any], m60_stats: dict[str, Any]) -> dict[str, Any]:
    raw_samples = m60_aa.get("highDeltaSamples")
    samples = raw_samples if isinstance(raw_samples, list) else []
    preview = [
        {
            "x": sample.get("x"),
            "y": sample.get("y"),
            "referenceRgba": sample.get("referenceRgba"),
            "currentRgba": sample.get("gpuRgba"),
            "candidatePolicyRgba": None,
            "reason": "no straight-sRGB F16 candidate sample exists for this non-arc diagnostic",
        }
        for sample in samples[:3]
        if isinstance(sample, dict)
    ]
    stats_summary = m60_stats.get("residualSummary", {}) if isinstance(m60_stats.get("residualSummary"), dict) else {}
    return matrix_row(
        row_id="non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend",
        family="bounded-stroke-cap-join",
        scene_id=str(m60_route.get("sceneId")),
        source_kind="non-arc-target-colorspace-blend-diagnostic",
        non_arc=True,
        rec2020_f16_src_over_or_blend_signal=bool(m60_aa.get("targetColorSpaceBlend")),
        comparable=False,
        reference_status="reference-rgba-high-delta-samples-available",
        current_status="current-gpu-rgba-high-delta-samples-available",
        candidate_status="missing-for-f16-policy-candidate",
        sample_count=len(samples),
        current_residual=high_delta_residual(samples),
        candidate_residual=None,
        worsened_samples=None,
        source_artifacts=[rel(M60_AA_DIAGNOSTIC), rel(M60_ROUTE_GPU), rel(M60_STATS)],
        reason=(
            "This is the available non-arc color/blend signal, but it is a targetColorSpaceBlend "
            "diagnostic and not a Rec.2020 F16 reference/current/candidate row for "
            "straight_srgb_quantized_alpha_src_over_white."
        ),
        notes=[
            f"routeStatus={m60_route.get('status')}",
            f"resolvedRootCause={m60_route.get('resolvedRootCause')}",
            f"remainingRootCause={m60_route.get('remainingRootCause')}",
            f"residualClassification={stats_summary.get('classification')}",
        ],
        sample_preview=preview,
    )


def build_neutral_aa_row(neutral: dict[str, Any]) -> dict[str, Any]:
    current = neutral.get("postPresentRed")
    reference = neutral.get("referenceRed")
    candidate = neutral.get("targetBlendRed")
    current_residual = abs(int(current) - int(reference)) if isinstance(current, int) and isinstance(reference, int) else None
    candidate_residual = abs(int(candidate) - int(reference)) if isinstance(candidate, int) and isinstance(reference, int) else None
    return matrix_row(
        row_id="non-arc-m60-target-colorspace-neutral-aa-substitute-refused",
        family="target-colorspace-neutral-aa",
        scene_id=str(neutral.get("sceneId")),
        source_kind="isolated-target-colorspace-blend-diagnostic-fixture",
        non_arc=True,
        rec2020_f16_src_over_or_blend_signal=True,
        comparable=False,
        reference_status="single-channel-cpu-reference-sample-available",
        current_status="post-present-red-sample-available",
        candidate_status="targetColorSpaceBlend-red-sample-available-not-f16-policy-candidate",
        sample_count=1,
        current_residual=current_residual,
        candidate_residual=candidate_residual,
        worsened_samples=0 if candidate_residual is not None and current_residual is not None and candidate_residual <= current_residual else None,
        source_artifacts=[rel(M60_NEUTRAL_AA_STATS)],
        reason=(
            "The neutral AA fixture has a reference/current/targetBlend triple, but the candidate is "
            "the WebGPU targetColorSpaceBlend pilot, not the FOR-341/FOR-342 F16 CPU policy candidate. "
            "Using it as the non-arc F16 proof would be a fixture substitution."
        ),
        notes=[
            f"sourceRoute={neutral.get('sourceRoute')}",
            f"targetRoute={neutral.get('targetRoute')}",
            str(neutral.get("result")),
        ],
        sample_preview=[
            {
                "channel": "red",
                "reference": reference,
                "current": current,
                "targetColorSpaceBlendCandidate": candidate,
                "f16PolicyCandidate": None,
            }
        ],
    )


def build_inventory_row(inventory: dict[str, Any]) -> dict[str, Any]:
    candidates = inventory.get("candidates")
    candidate_count = len(candidates) if isinstance(candidates, list) else 0
    non_candidates = inventory.get("nonCandidates")
    non_candidate_count = len(non_candidates) if isinstance(non_candidates, list) else 0
    return matrix_row(
        row_id="non-arc-target-color-candidate-inventory-reference-gap",
        family="target-color-candidate-inventory",
        scene_id=str(inventory.get("id")),
        source_kind="inventory-classification",
        non_arc=True,
        rec2020_f16_src_over_or_blend_signal=candidate_count > 0,
        comparable=False,
        reference_status="inventory-references-existing-scene-artifacts-only",
        current_status="inventory-current-route-stats-only",
        candidate_status="missing-straight-srgb-f16-policy-candidate",
        sample_count=candidate_count + non_candidate_count,
        current_residual=None,
        candidate_residual=None,
        worsened_samples=None,
        source_artifacts=[rel(M60_CANDIDATE_INVENTORY)],
        reason=(
            "The inventory is useful cross-scene evidence for target-color candidates, but it does "
            "not contain per-sample Rec.2020 F16 reference/current/candidate rows."
        ),
        notes=[
            f"candidateRows={candidate_count}",
            f"nonCandidateRows={non_candidate_count}",
            f"generatedDate={inventory.get('generatedDate')}",
        ],
    )


def dangerous_routes() -> list[dict[str, str]]:
    return [
        {
            "code": "F16_POLICY_UNSAFE_SELECTED_CELL_SUBSTITUTION",
            "route": "reuse selected-cell or arc-adjacent samples as non-arc proof",
            "status": "rejected",
            "reason": "FOR-344 requires genuine non-arc evidence, not extrapolation from circular_arcs_stroke_butt.",
        },
        {
            "code": "F16_POLICY_UNSAFE_FIXTURE_BRANCH",
            "route": "fixture-specific renderer branch",
            "status": "rejected",
            "reason": "A fixture branch would encode evidence gaps into renderer behavior.",
        },
        {
            "code": "F16_POLICY_UNSAFE_COORDINATE_BRANCH",
            "route": "coordinate-specific renderer branch",
            "status": "rejected",
            "reason": "Coordinate patches would substitute chosen cells for color-policy semantics.",
        },
        {
            "code": "F16_POLICY_UNSAFE_GLOBAL_HOOK_MUTATION",
            "route": "mutate colorToF16Premul, blendF16PremulMode, SkBitmap.getPixel, or SkBitmap.getPixelAsSrgb",
            "status": "rejected",
            "reason": "Global hooks require comparable non-arc reference/current/candidate rows first.",
        },
        {
            "code": "F16_POLICY_UNSAFE_FULL_GM_CROP",
            "route": "use a full-GM crop as implementation reference",
            "status": "rejected",
            "reason": "Full-GM crop evidence is not an isolated reference for this policy migration.",
        },
        {
            "code": "F16_POLICY_UNSAFE_THRESHOLD_RELAXATION",
            "route": "relax similarity or residual thresholds",
            "status": "rejected",
            "reason": "FOR-344 is evidence-only and cannot create policy safety by weakening gates.",
        },
    ]


def build_artifact(
    for343: dict[str, Any],
    for342: dict[str, Any],
    for337: dict[str, Any],
    m60_aa: dict[str, Any],
    m60_route: dict[str, Any],
    m60_stats: dict[str, Any],
    neutral: dict[str, Any],
    inventory: dict[str, Any],
) -> dict[str, Any]:
    rows = [
        build_arc_prerequisite_row(for342),
        build_non_arc_m60_row(m60_aa, m60_route, m60_stats),
        build_neutral_aa_row(neutral),
        build_inventory_row(inventory),
    ]
    comparable = [row for row in rows if row["referenceCurrentCandidateComparable"]]
    non_arc_rows = [row for row in rows if row["nonArc"]]
    comparable_non_arc = [row for row in non_arc_rows if row["referenceCurrentCandidateComparable"]]
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for343Artifact": rel(FOR343_ARTIFACT),
            "for343Decision": for343.get("decision"),
            "for343RequiredDecision": FOR343_REQUIRED_DECISION,
            "for343BoundaryId": for343.get("boundary", {}).get("id"),
            "for343BoundaryReadyForBroaderEvidence": for343.get("decision") == FOR343_REQUIRED_DECISION,
            "for342Artifact": rel(FOR342_ARTIFACT),
            "for342Decision": for342.get("decision"),
            "for337Artifact": rel(FOR337_ARTIFACT),
            "for337Decision": for337.get("decision"),
        },
        "decision": DECISION_PARTIAL,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-343 is ready for broader evidence, but the repository still lacks a genuine non-arc "
            "Rec.2020 F16 reference/current/candidate row for the straight_srgb_quantized_alpha_src_over_white "
            "policy. The available non-arc targetColorSpaceBlend artifacts are useful gap evidence and "
            "substitution guards, not policy proof."
        ),
        "boundary": {
            "id": BOUNDARY_ID,
            "status": "evidence-collection-partial-not-implemented",
            "globalF16RendererChangeAllowedNow": False,
            "rendererBehaviorChanged": False,
            "candidatePolicyId": CANDIDATE_POLICY_ID,
            "requiredReferenceKind": "non-arc Rec.2020 kRGBA_F16Norm SrcOver/blend isolated reference",
        },
        "matrix": {
            "description": "Reference/current/candidate matrix for FOR-344 broader non-arc F16 policy evidence.",
            "requiredComparableNonArcRowsBeforeGlobalMigration": 1,
            "requiredCrossSceneRowsBeforeGlobalMigration": 2,
            "rowCount": len(rows),
            "comparableRowCount": len(comparable),
            "nonArcRowCount": len(non_arc_rows),
            "comparableNonArcRowCount": len(comparable_non_arc),
            "rows": rows,
        },
        "referenceGapClassification": {
            "status": "partial",
            "stableReasonCode": "NON_ARC_REC2020_F16_REFERENCE_CURRENT_CANDIDATE_ROWS_MISSING",
            "trueComparableNonArcReferencePresent": False,
            "falseProofRejected": True,
            "requiresMoreReferenceRows": True,
            "minimumMissingRow": (
                "A non-arc Rec.2020 kRGBA_F16Norm SrcOver/blend scene with isolated reference, "
                "current renderer samples, straight_srgb_quantized_alpha_src_over_white candidate samples, "
                "per-sample residuals, aggregate residuals, and worsened-sample counts."
            ),
        },
        "dangerousRouteDiagnostics": dangerous_routes(),
        "implementation": {
            "rendererBehaviorChanged": False,
            "evidenceOnly": True,
            "colorToF16PremulChanged": False,
            "blendF16PremulModeChanged": False,
            "skBitmapGetPixelChanged": False,
            "skBitmapGetPixelAsSrgbChanged": False,
            "gpuOrWgslChanged": False,
            "geometryChanged": False,
            "coverageChanged": False,
            "fallbackChanged": False,
            "thresholdsChanged": False,
            "promotionChanged": False,
            "scoreChanged": False,
            "kadreChanged": False,
        },
        "nonGoalsPreserved": {
            "globalF16PolicyChange": True,
            "selectedCellSubstitutionRefused": True,
            "fixtureBranchRefused": True,
            "coordinateBranchRefused": True,
            "fullGmCropRefused": True,
            "thresholdRelaxationRefused": True,
            "ganeshGraphitePortNotAdded": True,
            "skslCompilerVmWorkNotAdded": True,
            "historicalArtifactsRewritten": False,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def fmt(value: Any) -> str:
    return "n/a" if value is None else str(value)


def build_report(data: dict[str, Any]) -> str:
    rows = "\n".join(
        "| {row} | {scene} | {non_arc} | {signal} | {ref} | {current} | {candidate} | {current_residual} | {candidate_residual} | {comparable} |".format(
            row=row["rowId"],
            scene=row["sceneId"],
            non_arc="yes" if row["nonArc"] else "no",
            signal="yes" if row["rec2020F16SrcOverOrBlendSignal"] else "no",
            ref=row["reference"]["status"],
            current=row["current"]["status"],
            candidate=row["candidate"]["status"],
            current_residual=fmt(row["currentResidual"]),
            candidate_residual=fmt(row["candidateResidual"]),
            comparable="yes" if row["referenceCurrentCandidateComparable"] else "no",
        )
        for row in data["matrix"]["rows"]
    )
    diagnostics = "\n".join(
        f"| `{item['code']}` | {item['route']} | {item['status']} | {item['reason']} |"
        for item in data["dangerousRouteDiagnostics"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-344 F16 Broader Non-Arc Color Policy Evidence

Linear: `FOR-344`

Decision: `{data["decision"]}`

FOR-344 collects broader non-arc F16 color-policy evidence before any global
mutation of `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
`SkBitmap.getPixelAsSrgb`. The ticket is evidence/architecture only.

## Result

FOR-343 is present with `{data["inputValidation"]["for343Decision"]}` and the
boundary `{data["boundary"]["id"]}` is ready for broader evidence collection.

The result remains partial. The repository has non-arc target-color/blend
diagnostics, but not a genuine non-arc Rec.2020 F16
reference/current/candidate row for `{data["boundary"]["candidatePolicyId"]}`.
Those diagnostics are kept as gap evidence and substitution guards, not as a
global migration proof.

## Matrix

| row | scene | non-arc | F16/Rec.2020 blend signal | reference | current | candidate | current residual | candidate residual | comparable |
|---|---|---|---|---|---|---|---:|---:|---|
{rows}

Comparable non-arc rows:
`{data["matrix"]["comparableNonArcRowCount"]}` /
`{data["matrix"]["requiredComparableNonArcRowsBeforeGlobalMigration"]}`.

## Reference Gap

Stable reason:
`{data["referenceGapClassification"]["stableReasonCode"]}`.

Missing row:
{data["referenceGapClassification"]["minimumMissingRow"]}

## Dangerous Routes

| diagnostic | route | status | reason |
|---|---|---|---|
{diagnostics}

## Non-goals Preserved

- No renderer behavior change.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for344_f16_broader_non_arc_color_policy_evidence.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-344-f16-broader-non-arc-color-policy-evidence.md`

## Validation

{validation}
"""


def validate_row(row: dict[str, Any]) -> None:
    for key in (
        "rowId",
        "family",
        "sceneId",
        "nonArc",
        "rec2020F16SrcOverOrBlendSignal",
        "referenceCurrentCandidateComparable",
        "reference",
        "current",
        "candidate",
        "sampleCount",
        "currentResidual",
        "candidateResidual",
        "sourceArtifacts",
        "classificationReason",
    ):
        require(key in row, f"{row.get('rowId', '<unknown>')} missing {key}")
    require(isinstance(row["sampleCount"], int) and row["sampleCount"] >= 0, "invalid sampleCount")
    require(isinstance(row["sourceArtifacts"], list) and row["sourceArtifacts"], "sourceArtifacts missing")
    current = row["currentResidual"]
    candidate = row["candidateResidual"]
    if current is not None and candidate is not None:
        require(
            row["candidateMinusCurrentResidual"] == candidate - current,
            f"{row['rowId']} residual delta mismatch",
        )
        require(row["residualReduction"] == current - candidate, f"{row['rowId']} residual reduction mismatch")
    if row["referenceCurrentCandidateComparable"]:
        require(candidate is not None, f"{row['rowId']} comparable row missing candidate residual")


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "source finding changed")
    require(data.get("decision") == DECISION_PARTIAL, "unexpected FOR-344 decision")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")

    input_validation = data.get("inputValidation")
    require(isinstance(input_validation, dict), "inputValidation missing")
    require(input_validation.get("for343Decision") == FOR343_REQUIRED_DECISION, "FOR-343 decision not imported")
    require(input_validation.get("for343BoundaryReadyForBroaderEvidence") is True, "FOR-343 broader gate not enforced")
    require(input_validation.get("for343BoundaryId") == BOUNDARY_ID, "FOR-343 boundary id not imported")

    boundary = data.get("boundary")
    require(isinstance(boundary, dict), "boundary missing")
    require(boundary.get("globalF16RendererChangeAllowedNow") is False, "global F16 change allowed")
    require(boundary.get("rendererBehaviorChanged") is False, "renderer behavior changed")
    require(boundary.get("candidatePolicyId") == CANDIDATE_POLICY_ID, "candidate policy changed")

    matrix = data.get("matrix")
    require(isinstance(matrix, dict), "matrix missing")
    rows = matrix.get("rows")
    require(isinstance(rows, list) and len(rows) >= 4, "matrix rows missing")
    row_ids = [row.get("rowId") for row in rows if isinstance(row, dict)]
    require(row_ids == [
        "arc-prerequisite-for342-adjacent-cells",
        "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend",
        "non-arc-m60-target-colorspace-neutral-aa-substitute-refused",
        "non-arc-target-color-candidate-inventory-reference-gap",
    ], "matrix row order changed")
    for row in rows:
        validate_row(row)

    non_arc_rows = [row for row in rows if row["nonArc"]]
    comparable_non_arc = [row for row in non_arc_rows if row["referenceCurrentCandidateComparable"]]
    require(len(non_arc_rows) >= 3, "non-arc rows missing")
    require(len(comparable_non_arc) == 0, "FOR-344 should remain partial until a true comparable non-arc row exists")
    require(
        any(row["rec2020F16SrcOverOrBlendSignal"] for row in non_arc_rows),
        "non-arc Rec.2020/F16 blend signal missing",
    )

    gap = data.get("referenceGapClassification")
    require(isinstance(gap, dict), "referenceGapClassification missing")
    require(gap.get("status") == "partial", "reference gap status changed")
    require(gap.get("trueComparableNonArcReferencePresent") is False, "false comparable reference claim")
    require(gap.get("falseProofRejected") is True, "false proof guard missing")
    require(gap.get("requiresMoreReferenceRows") is True, "more-reference-row requirement missing")

    diagnostics = data.get("dangerousRouteDiagnostics")
    require(isinstance(diagnostics, list), "dangerous route diagnostics missing")
    require([item.get("code") for item in diagnostics] == REQUIRED_UNSAFE_ROUTE_CODES, "unsafe route codes changed")
    require(all(item.get("status") == "rejected" for item in diagnostics), "unsafe route not rejected")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation block missing")
    for key, expected in {
        "rendererBehaviorChanged": False,
        "evidenceOnly": True,
        "colorToF16PremulChanged": False,
        "blendF16PremulModeChanged": False,
        "skBitmapGetPixelChanged": False,
        "skBitmapGetPixelAsSrgbChanged": False,
        "gpuOrWgslChanged": False,
        "geometryChanged": False,
        "coverageChanged": False,
        "fallbackChanged": False,
        "thresholdsChanged": False,
        "promotionChanged": False,
        "scoreChanged": False,
        "kadreChanged": False,
    }.items():
        require(implementation.get(key) is expected, f"implementation guard changed: {key}")


def main() -> None:
    validate_source_guardrails()
    for343 = load_json(FOR343_ARTIFACT)
    for342 = load_json(FOR342_ARTIFACT)
    for337 = load_json(FOR337_ARTIFACT)
    m60_aa = load_json(M60_AA_DIAGNOSTIC)
    m60_route = load_json(M60_ROUTE_GPU)
    m60_stats = load_json(M60_STATS)
    neutral = load_json(M60_NEUTRAL_AA_STATS)
    inventory = load_json(M60_CANDIDATE_INVENTORY)

    validate_for343(for343)
    validate_inputs(for342, for337)
    data = build_artifact(for343, for342, for337, m60_aa, m60_route, m60_stats, neutral, inventory)
    validate_artifact(data)
    artifact_text = json.dumps(data, indent=2, ensure_ascii=True) + "\n"
    report_text = build_report(data)
    write_if_changed(ARTIFACT, artifact_text)
    write_if_changed(REPORT, report_text)
    validate_artifact(load_json(ARTIFACT))
    if REPORT.read_text(encoding="utf-8") != report_text:
        fail(f"{rel(REPORT)} does not match generated report")
    print("FOR-344 validation passed")


if __name__ == "__main__":
    main()
