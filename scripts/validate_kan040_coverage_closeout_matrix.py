#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/coverage-closeout-matrix"
OUTPUT_JSON = "kan-040-coverage-closeout-matrix.json"
OUTPUT_MARKDOWN = "kan-040-coverage-closeout-matrix.md"

KAN004_EVIDENCE_PATH = "reports/wgsl-pipeline/scenes/artifacts/kan-004-clips-aa/kan-004-clips-aa.json"
KAN035_EVIDENCE_PATH = "reports/wgsl-pipeline/hairlines-root-cause/kan-035-hairlines-root-cause.json"
KAN036_EVIDENCE_PATH = "reports/wgsl-pipeline/butt-stroke-non-hairline/kan-036-butt-stroke-non-hairline.json"
KAN037_EVIDENCE_PATH = "reports/wgsl-pipeline/caps-joins-micro-matrix/kan-037-caps-joins-micro-matrix.json"
KAN038_EVIDENCE_PATH = "reports/wgsl-pipeline/dashes-bounded-v1/kan-038-dashes-bounded-v1.json"
KAN039_EVIDENCE_PATH = "reports/wgsl-pipeline/nested-clip-stack-v1/kan-039-nested-clip-stack-v1.json"
SPEC_FALLBACKS_PATH = ".upstream/specs/geometry-coverage/05-fallback-diagnostics.md"
SPEC_VALIDATION_PATH = ".upstream/specs/geometry-coverage/06-validation-and-perf.md"
SPEC_RENDERING_FEATURE_PATH = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"

SUPPORTABLE_BOUNDED = "supportable-bounded"
VISIBLE_NON_SUPPORTABLE = "visible-non-supportable"
EXPECTED_UNSUPPORTED = "expected-unsupported"
DEPENDENCY_GATED = "dependency-gated"

STABLE_REASON_CODES = {
    "coverage.hairline.row-specific-artifacts-required",
    "coverage.stroke-cap-join-visual-parity-below-threshold",
    "coverage.dashing.row-specific-artifacts-required",
    "coverage.edge-count-exceeded",
    "coverage.dash-budget-exceeded",
    "coverage.nested-clip-visual-parity-below-threshold",
    "coverage.arbitrary-aa-clip-unsupported",
    "geometry.clip-stack-unsupported",
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-040 coverage closeout matrix validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> dict[str, Any]:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")
    require(isinstance(data, dict), f"{relative_path} must contain a JSON object")
    return data


def require_object(data: dict[str, Any], field: str, source: str) -> dict[str, Any]:
    value = data.get(field)
    require(isinstance(value, dict), f"{source}.{field} must be an object")
    return value


def require_list(data: dict[str, Any], field: str, source: str) -> list[Any]:
    value = data.get(field)
    require(isinstance(value, list), f"{source}.{field} must be a list")
    return value


def require_file(root: Path, relative_path: str) -> None:
    require((root / relative_path).is_file(), f"missing required file: {relative_path}")


def require_contains(root: Path, relative_path: str, snippets: list[str]) -> None:
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    flattened = " ".join(text.split())
    for snippet in snippets:
        require(
            snippet in text or " ".join(snippet.split()) in flattened,
            f"{relative_path} missing snippet: {snippet}",
        )


def bool_file(root: Path, relative_path: str | None) -> bool:
    return bool(relative_path) and (root / relative_path).is_file()


def artifact_paths(*packs: dict[str, Any]) -> list[str]:
    paths: set[str] = {
        KAN004_EVIDENCE_PATH,
        KAN035_EVIDENCE_PATH,
        KAN036_EVIDENCE_PATH,
        KAN037_EVIDENCE_PATH,
        KAN038_EVIDENCE_PATH,
        KAN039_EVIDENCE_PATH,
        SPEC_FALLBACKS_PATH,
        SPEC_VALIDATION_PATH,
        SPEC_RENDERING_FEATURE_PATH,
    }
    for pack in packs:
        for path in pack.get("artifactPaths", []):
            if isinstance(path, str):
                paths.add(path)
    return sorted(paths)


def support_proofs(root: Path, paths: dict[str, str]) -> dict[str, bool]:
    return {
        "reference": bool_file(root, paths.get("reference")),
        "cpu": bool_file(root, paths.get("cpuImage")),
        "gpu": bool_file(root, paths.get("gpuImage")),
        "diff": bool_file(root, paths.get("cpuDiff")) and bool_file(root, paths.get("gpuDiff")),
        "stats": bool_file(root, paths.get("stats")),
        "route": bool_file(root, paths.get("cpuRoute")) and bool_file(root, paths.get("gpuRoute")),
    }


def missing_false_artifacts(availability: dict[str, Any]) -> list[str]:
    missing: list[str] = []
    for key, value in availability.items():
        if isinstance(value, dict) and value.get("available") is False:
            missing.append(key)
    return sorted(missing)


def stable_reason(reason: str) -> bool:
    return reason in STABLE_REASON_CODES and reason == reason.lower() and "." in reason


def matrix_row(
    *,
    ticket: str,
    row_id: str,
    family: str,
    classification: str,
    row_status: str,
    support_claim: bool,
    fallback_reason: str,
    route: str,
    proofs: dict[str, bool],
    missing_proofs: list[str],
    evidence_pack: str,
    summary: str,
    details: dict[str, Any],
) -> dict[str, Any]:
    return {
        "ticket": ticket,
        "id": row_id,
        "family": family,
        "classification": classification,
        "rowStatus": row_status,
        "supportClaim": support_claim,
        "fallbackReason": fallback_reason,
        "reasonCodeStable": True if support_claim else stable_reason(fallback_reason),
        "route": route,
        "proofs": proofs,
        "missingProofs": missing_proofs,
        "evidencePack": evidence_pack,
        "summary": summary,
        "details": details,
    }


def validate_source_packs(
    kan004: dict[str, Any],
    kan035: dict[str, Any],
    kan036: dict[str, Any],
    kan037: dict[str, Any],
    kan038: dict[str, Any],
    kan039: dict[str, Any],
) -> None:
    expected_tickets = {
        "KAN-004": kan004,
        "KAN-035": kan035,
        "KAN-036": kan036,
        "KAN-037": kan037,
        "KAN-038": kan038,
        "KAN-039": kan039,
    }
    for ticket, pack in expected_tickets.items():
        require(pack.get("ticket") == ticket, f"{ticket} pack ticket changed")
        require(pack.get("status") == "pass", f"{ticket} pack status changed")

    require(kan004.get("supportClaim") == "m57-aaclip-bounded-grid-only", "KAN-004 support claim changed")
    require(kan004.get("broadClipStackSupportClaim") is False, "KAN-004 broad clip claim changed")
    require(kan004.get("thresholdsWeakened") is False, "KAN-004 threshold policy changed")
    require(kan004.get("sharedCoverageChanged") is False, "KAN-004 shared coverage policy changed")

    for ticket, pack in [
        ("KAN-035", kan035),
        ("KAN-036", kan036),
        ("KAN-037", kan037),
        ("KAN-038", kan038),
        ("KAN-039", kan039),
    ]:
        require(pack.get("supportClaim") is False, f"{ticket} unexpectedly claims support")
        require(pack.get("rendererChanged") is False, f"{ticket} renderer policy changed")
        require(pack.get("sharedShadersChanged") is False, f"{ticket} shader policy changed")
        require(pack.get("thresholdsWeakened") is False, f"{ticket} threshold policy changed")
        require(pack.get("edgeBudgetChanged") is False, f"{ticket} edge-budget policy changed")


def build_rows(
    root: Path,
    kan004: dict[str, Any],
    kan035: dict[str, Any],
    kan036: dict[str, Any],
    kan037: dict[str, Any],
    kan038: dict[str, Any],
    kan039: dict[str, Any],
) -> list[dict[str, Any]]:
    support = require_object(kan004, "supportScene", KAN004_EVIDENCE_PATH)
    hairline_row = require_object(kan035, "dashboardPolicyRow", KAN035_EVIDENCE_PATH)
    hairline_refusal = require_object(kan035, "observedRefusal", KAN035_EVIDENCE_PATH)
    hairline_availability = require_object(kan035, "rowLocalArtifactAvailability", KAN035_EVIDENCE_PATH)
    butt_row = require_object(kan036, "selectedRow", KAN036_EVIDENCE_PATH)
    butt_refusal = require_object(kan036, "webGpuRefusal", KAN036_EVIDENCE_PATH)
    butt_availability = require_object(kan036, "artifactAvailability", KAN036_EVIDENCE_PATH)
    caps_scene = require_object(kan037, "scene", KAN037_EVIDENCE_PATH)
    caps_candidate = require_object(kan037, "candidate", KAN037_EVIDENCE_PATH)
    caps_refusal = require_object(kan037, "webGpuRefusal", KAN037_EVIDENCE_PATH)
    caps_availability = require_object(kan037, "artifactAvailability", KAN037_EVIDENCE_PATH)
    dash_candidate = require_object(kan038, "candidate", KAN038_EVIDENCE_PATH)
    dash_availability = require_object(kan038, "artifactAvailability", KAN038_EVIDENCE_PATH)
    nested_candidate = require_object(kan039, "candidate", KAN039_EVIDENCE_PATH)
    nested_baseline = require_object(kan039, "m57SupportBaseline", KAN039_EVIDENCE_PATH)
    nested_availability = require_object(kan039, "artifactAvailability", KAN039_EVIDENCE_PATH)

    require(support.get("sceneId") == "m57-aaclip-bounded-grid", "KAN-004 support scene changed")
    require(support.get("status") == "pass", "m57 status changed")
    require(support.get("gpuFallbackReason") == "none", "m57 fallback changed")
    require(nested_baseline.get("sceneId") == support.get("sceneId"), "KAN-039 m57 baseline scene changed")
    require(nested_baseline.get("fallbackReason") == "none", "KAN-039 m57 fallback changed")

    require(hairline_row.get("sceneId") == "skia-gm-hairlines", "Hairlines row changed")
    require(hairline_row.get("status") == "expected-unsupported", "Hairlines row status changed")
    require(hairline_refusal.get("fallbackReason") == "coverage.stroke-cap-join-visual-parity-below-threshold", "Hairlines refusal changed")
    require(butt_refusal.get("fallbackReason") == "coverage.stroke-cap-join-visual-parity-below-threshold", "Butt stroke fallback changed")
    require(caps_candidate.get("fallbackReason") == "coverage.stroke-cap-join-visual-parity-below-threshold", "Caps/joins fallback changed")
    require(dash_candidate.get("fallbackReason") == "coverage.dashing.row-specific-artifacts-required", "Dashing fallback changed")
    require(nested_candidate.get("fallbackReason") == "coverage.nested-clip-visual-parity-below-threshold", "Nested clip fallback changed")

    return [
        matrix_row(
            ticket="KAN-004",
            row_id=support["sceneId"],
            family="AA clips",
            classification=SUPPORTABLE_BOUNDED,
            row_status=support["status"],
            support_claim=True,
            fallback_reason=support["gpuFallbackReason"],
            route=support["gpuRouteName"],
            proofs=support_proofs(
                root,
                {
                    "reference": support["reference"],
                    "cpuImage": support["cpuImage"],
                    "gpuImage": support["gpuImage"],
                    "cpuDiff": support["cpuDiff"],
                    "gpuDiff": support["gpuDiff"],
                    "stats": support["stats"],
                    "cpuRoute": support["cpuRoute"],
                    "gpuRoute": support["gpuRoute"],
                },
            ),
            missing_proofs=[],
            evidence_pack=KAN004_EVIDENCE_PATH,
            summary="Bounded AA clip row remains supported with reference/CPU/GPU/diff/stat/route and fallbackReason=none.",
            details={
                "edgeCount": support["edgeCount"],
                "edgeBudget": support["edgeBudget"],
                "clipOp": support["clipOp"],
                "clipShape": support["clipShape"],
                "gpuSimilarity": support["gpuSimilarity"],
            },
        ),
        matrix_row(
            ticket="KAN-035",
            row_id=hairline_row["sceneId"],
            family="HairlinesGM",
            classification=VISIBLE_NON_SUPPORTABLE,
            row_status=hairline_row["status"],
            support_claim=False,
            fallback_reason=hairline_row["fallbackReason"],
            route=hairline_refusal["route"],
            proofs={
                "reference": bool_file(root, hairline_availability["reference"]["path"]),
                "cpu": bool(hairline_availability["cpuStats"]["available"]),
                "gpu": False,
                "diff": False,
                "stats": bool(hairline_availability["cpuStats"]["available"]),
                "route": bool(hairline_availability["gpuStableRefusal"]["available"]),
            },
            missing_proofs=missing_false_artifacts(hairline_availability),
            evidence_pack=KAN035_EVIDENCE_PATH,
            summary="HairlinesGM remains visible but non-supportable; current abort is cap/join parity and row-local WebGPU image/diff are absent.",
            details={
                "primaryRootCause": kan035["rootCause"]["primaryBucket"],
                "observedFallback": hairline_refusal["fallbackReason"],
                "coverageEdgeCount": hairline_refusal["coverageEdgeCount"],
                "edgeBudget": hairline_refusal["edgeBudget"],
            },
        ),
        matrix_row(
            ticket="KAN-036",
            row_id=butt_row["fixtureId"],
            family="Butt stroke non-hairline",
            classification=EXPECTED_UNSUPPORTED,
            row_status=butt_refusal["status"],
            support_claim=False,
            fallback_reason=butt_refusal["fallbackReason"],
            route=butt_refusal["selectedRoute"],
            proofs={
                "reference": bool_file(root, butt_availability["skiaReference"]["path"]),
                "cpu": bool_file(root, butt_availability["cpuOracle"]["path"]),
                "gpu": False,
                "diff": bool_file(root, butt_availability["cpuOracle"]["path"])
                and bool_file(root, butt_availability["cpuVsSkiaDiff"]["path"]),
                "stats": True,
                "route": True,
            },
            missing_proofs=missing_false_artifacts(butt_availability),
            evidence_pack=KAN036_EVIDENCE_PATH,
            summary="Selected butt-cap non-hairline row stays expected-unsupported until WebGPU image/diff and CPU-vs-Skia support-ready evidence exist.",
            details={
                "strokeWidth": butt_refusal["strokeWidth"],
                "pathVerbCount": butt_refusal["pathVerbCount"],
                "coverageEdgeCount": butt_refusal["coverageEdgeCount"],
                "edgeBudget": butt_refusal["edgeBudget"],
            },
        ),
        matrix_row(
            ticket="KAN-037",
            row_id=caps_scene["sceneId"],
            family="Caps/joins micro-matrix",
            classification=EXPECTED_UNSUPPORTED,
            row_status=caps_refusal["status"],
            support_claim=False,
            fallback_reason=caps_candidate["fallbackReason"],
            route=caps_refusal["selectedRoute"],
            proofs={
                "reference": bool_file(root, caps_availability["skiaReference"]["path"]),
                "cpu": bool_file(root, caps_availability["cpuOracle"]["path"]),
                "gpu": bool_file(root, caps_availability["webGpuDiagnosticImage"]["path"]),
                "diff": bool_file(root, caps_availability["cpuDiff"]["path"])
                and bool_file(root, caps_availability["webGpuDiagnosticDiff"]["path"]),
                "stats": True,
                "route": bool_file(root, caps_availability["webGpuProductionRoute"]["path"]),
            },
            missing_proofs=missing_false_artifacts(caps_availability),
            evidence_pack=KAN037_EVIDENCE_PATH,
            summary="Round-round candidate and butt/square sentinels remain expected-unsupported with closed-contour CPU join evidence still missing.",
            details={
                "candidate": caps_candidate["id"],
                "capJoinMatrix": caps_scene["capJoinMatrix"],
                "coverageEdgeCount": caps_refusal["coverageEdgeCount"],
                "edgeBudget": caps_refusal["edgeBudget"],
                "blockingCondition": caps_candidate["blockingCondition"],
            },
        ),
        matrix_row(
            ticket="KAN-038",
            row_id=dash_candidate["id"],
            family="Dashes",
            classification=DEPENDENCY_GATED,
            row_status=dash_candidate["status"],
            support_claim=False,
            fallback_reason=dash_candidate["fallbackReason"],
            route="policy-row",
            proofs={
                "reference": False,
                "cpu": False,
                "gpu": False,
                "diff": False,
                "stats": False,
                "route": False,
            },
            missing_proofs=missing_false_artifacts(dash_availability),
            evidence_pack=KAN038_EVIDENCE_PATH,
            summary="Bounded dash candidate is identified but gated by missing row-specific reference/CPU/GPU/diff/stat/route and post-dash verb/edge diagnostics.",
            details={
                "dashIntervalCount": dash_candidate["dashIntervalCount"],
                "dashIntervalBudget": dash_candidate["dashIntervalBudget"],
                "phase": dash_candidate["phase"],
                "strokeWidth": dash_candidate["strokeWidth"],
                "supportReady": dash_candidate["supportReady"],
                "postDashVerbCount": dash_candidate["postDashVerbCount"],
                "postDashEdgeCount": dash_candidate["postDashEdgeCount"],
            },
        ),
        matrix_row(
            ticket="KAN-039",
            row_id=nested_candidate["sceneId"],
            family="Nested clips",
            classification=EXPECTED_UNSUPPORTED,
            row_status=nested_candidate["status"],
            support_claim=False,
            fallback_reason=nested_candidate["fallbackReason"],
            route=nested_candidate["gpuRoute"],
            proofs={
                "reference": bool_file(root, nested_availability["skiaReference"]["path"]),
                "cpu": bool_file(root, nested_availability["cpuOracle"]["path"]),
                "gpu": bool_file(root, nested_availability["webGpuDiagnosticImage"]["path"]),
                "diff": bool_file(root, nested_availability["cpuDiff"]["path"])
                and bool_file(root, nested_availability["webGpuDiagnosticDiff"]["path"]),
                "stats": True,
                "route": bool_file(root, nested_availability["webGpuRoute"]["path"]),
            },
            missing_proofs=missing_false_artifacts(nested_availability),
            evidence_pack=KAN039_EVIDENCE_PATH,
            summary="Nested rect/rrect clip row stays expected-unsupported below visual parity floor and lacks selector-owned fallbackReason=none route.",
            details={
                "clipDepth": nested_candidate["clipDepth"],
                "clipDepthBudget": nested_candidate["clipDepthBudget"],
                "edgeCount": nested_candidate["edgeCount"],
                "edgeBudget": nested_candidate["edgeBudget"],
                "clipOp": nested_candidate["clipOp"],
                "clipShape": nested_candidate["clipShape"],
                "gpuSimilarity": nested_candidate["gpuSimilarity"],
                "supportThreshold": nested_candidate["supportThreshold"],
            },
        ),
    ]


def claim_guard(rows: list[dict[str, Any]], source_packs: list[dict[str, Any]]) -> dict[str, Any]:
    categories = {SUPPORTABLE_BOUNDED, VISIBLE_NON_SUPPORTABLE, EXPECTED_UNSUPPORTED, DEPENDENCY_GATED}
    support_rows_missing_proofs = [
        row["id"]
        for row in rows
        if row["supportClaim"] and not all(row["proofs"].values())
    ]
    unsupported_rows_missing_fallback = [
        row["id"]
        for row in rows
        if not row["supportClaim"] and (not row["fallbackReason"] or row["fallbackReason"] == "none")
    ]
    unsupported_rows_unstable_reason = [
        row["id"]
        for row in rows
        if not row["supportClaim"] and not row["reasonCodeStable"]
    ]
    hidden_promotion_rows = [
        row["id"]
        for row in rows
        if row["rowStatus"] == "pass" and not row["supportClaim"]
    ]
    budget_or_threshold_changes: list[str] = []
    for pack in source_packs:
        ticket = str(pack.get("ticket"))
        if pack.get("thresholdsWeakened") is True:
            budget_or_threshold_changes.append(f"{ticket}:thresholdsWeakened")
        if pack.get("edgeBudgetChanged") is True:
            budget_or_threshold_changes.append(f"{ticket}:edgeBudgetChanged")
        if pack.get("sharedCoverageChanged") is True:
            budget_or_threshold_changes.append(f"{ticket}:sharedCoverageChanged")
        if pack.get("rendererChanged") is True:
            budget_or_threshold_changes.append(f"{ticket}:rendererChanged")
        if pack.get("sharedShadersChanged") is True:
            budget_or_threshold_changes.append(f"{ticket}:sharedShadersChanged")

    return {
        "supportRowsMissingProofs": support_rows_missing_proofs,
        "unsupportedRowsMissingFallback": unsupported_rows_missing_fallback,
        "unsupportedRowsUnstableReason": unsupported_rows_unstable_reason,
        "hiddenPromotionRows": hidden_promotion_rows,
        "budgetOrThresholdChanges": budget_or_threshold_changes,
        "pmBundleCategoriesVisible": {row["classification"] for row in rows} == categories,
    }


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    kan004 = load_json(root, KAN004_EVIDENCE_PATH)
    kan035 = load_json(root, KAN035_EVIDENCE_PATH)
    kan036 = load_json(root, KAN036_EVIDENCE_PATH)
    kan037 = load_json(root, KAN037_EVIDENCE_PATH)
    kan038 = load_json(root, KAN038_EVIDENCE_PATH)
    kan039 = load_json(root, KAN039_EVIDENCE_PATH)

    validate_source_packs(kan004, kan035, kan036, kan037, kan038, kan039)
    require_contains(root, SPEC_FALLBACKS_PATH, [
        "Fallback action must be explicit.",
        "Must not silently replace arbitrary clip with integer scissor.",
        "Every `Unsupported` plan has a code and action.",
    ])
    require_contains(root, SPEC_VALIDATION_PATH, [
        "CPU oracle evidence exists",
        "WebGPU evidence exists or a stable unsupported diagnostic is asserted",
        "fallback behavior is explicit",
    ])
    require_contains(root, SPEC_RENDERING_FEATURE_PATH, [
        "Clip stack depth",
        "Coverage edge count",
        "no broad Path AA support claim from one bounded subset",
    ])

    rows = build_rows(root, kan004, kan035, kan036, kan037, kan038, kan039)
    guard = claim_guard(rows, [kan004, kan035, kan036, kan037, kan038, kan039])
    require(not guard["supportRowsMissingProofs"], f"support rows missing proofs: {guard['supportRowsMissingProofs']}")
    require(not guard["unsupportedRowsMissingFallback"], f"unsupported rows missing fallback: {guard['unsupportedRowsMissingFallback']}")
    require(not guard["unsupportedRowsUnstableReason"], f"unsupported rows unstable reason: {guard['unsupportedRowsUnstableReason']}")
    require(not guard["hiddenPromotionRows"], f"hidden promotion rows: {guard['hiddenPromotionRows']}")
    require(not guard["budgetOrThresholdChanges"], f"budget/threshold changes found: {guard['budgetOrThresholdChanges']}")
    require(guard["pmBundleCategoriesVisible"] is True, "PM bundle categories are not all represented")

    artifacts = artifact_paths(kan004, kan035, kan036, kan037, kan038, kan039)
    missing_artifacts = [path for path in artifacts if not (root / path).is_file()]
    require(not missing_artifacts, f"missing committed artifacts: {missing_artifacts}")

    categories = {
        SUPPORTABLE_BOUNDED: sum(1 for row in rows if row["classification"] == SUPPORTABLE_BOUNDED),
        VISIBLE_NON_SUPPORTABLE: sum(1 for row in rows if row["classification"] == VISIBLE_NON_SUPPORTABLE),
        EXPECTED_UNSUPPORTED: sum(1 for row in rows if row["classification"] == EXPECTED_UNSUPPORTED),
        DEPENDENCY_GATED: sum(1 for row in rows if row["classification"] == DEPENDENCY_GATED),
    }
    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-040",
        "packId": "kan-040-coverage-stroke-clip-closeout-matrix",
        "status": "pass",
        "closureDecision": "coverage-stroke-clip-closeout-matrix",
        "claimLevel": "pm-coverage-stroke-clip-aggregation-no-new-renderer-support",
        "supportClaim": "m57-aaclip-bounded-grid-only",
        "rendererChanged": False,
        "sharedShadersChanged": False,
        "thresholdsWeakened": False,
        "edgeBudgetChanged": False,
        "readinessDelta": 0,
        "summary": {
            "totalRows": len(rows),
            "supportableBounded": categories[SUPPORTABLE_BOUNDED],
            "visibleNonSupportable": categories[VISIBLE_NON_SUPPORTABLE],
            "expectedUnsupported": categories[EXPECTED_UNSUPPORTED],
            "dependencyGated": categories[DEPENDENCY_GATED],
            "supportClaims": sum(1 for row in rows if row["supportClaim"]),
            "unsupportedRows": sum(1 for row in rows if not row["supportClaim"]),
        },
        "categoryDefinitions": {
            SUPPORTABLE_BOUNDED: "A bounded row with reference, CPU/GPU, diff/stat, route, and fallbackReason=none.",
            VISIBLE_NON_SUPPORTABLE: "A visible row with stable refusal or policy evidence that is not supportable under current proof rules.",
            EXPECTED_UNSUPPORTED: "A selected row with stable unsupported diagnostic and explicit support blockers.",
            DEPENDENCY_GATED: "A row whose missing artifacts or dependency facts block any support claim.",
        },
        "matrixRows": rows,
        "claimGuard": guard,
        "requiredValidation": [
            "validateKan035HairlinesRootCause",
            "validateKan036ButtStrokeNonHairline",
            "validateKan037CapsJoinsMicroMatrix",
            "validateKan038DashesBoundedV1",
            "validateKan039NestedClipStackV1",
            "pipelineSceneDashboardGate",
            "pipelinePmBundle",
        ],
        "nonClaims": [
            "KAN-040 does not add renderer, shader, selector, PipelineKey, threshold, edge-budget, dash-budget, or clip-depth changes.",
            "KAN-040 does not claim broad Path AA, hairline, stroke, cap/join, dash, AA clip, or clip-stack support.",
            "KAN-040 does not promote visible non-supportable, expected-unsupported, or dependency-gated rows to support.",
            "KAN-040 does not replace AA clip evidence with integer scissor substitution.",
            "KAN-040 does not port Ganesh or Graphite and does not add SkSL compiler behavior.",
        ],
        "validationRows": [
            {
                "id": "support-claims-have-complete-proofs",
                "status": "pass",
                "evidence": "The only support row is m57-aaclip-bounded-grid and all support proof booleans are true.",
            },
            {
                "id": "unsupported-rows-have-stable-reasons",
                "status": "pass",
                "evidence": "All five non-support rows carry stable non-none fallback reason codes.",
            },
            {
                "id": "pm-categories-visible",
                "status": "pass",
                "evidence": "Matrix includes supportable-bounded, visible-non-supportable, expected-unsupported, and dependency-gated categories.",
            },
            {
                "id": "source-policy-preserved",
                "status": "pass",
                "evidence": "No source pack reports renderer, shader, threshold, edge-budget, shared-coverage, or hidden promotion changes.",
            },
            {
                "id": "artifact-audit-complete",
                "status": "pass",
                "evidence": f"{len(artifacts)} committed source artifacts are present.",
            },
        ],
        "artifactAudit": {
            "checkedCommittedArtifacts": len(artifacts),
            "missingCommittedArtifacts": len(missing_artifacts),
            "missing": missing_artifacts,
        },
        "artifactPaths": artifacts,
    }
    return evidence


def render_markdown(evidence: dict[str, Any]) -> str:
    summary = evidence["summary"]
    matrix = "\n".join(
        "| {ticket} | `{id}` | `{classification}` | `{rowStatus}` | `{fallbackReason}` | `{support}` | {missing} |".format(
            ticket=row["ticket"],
            id=row["id"],
            classification=row["classification"],
            rowStatus=row["rowStatus"],
            fallbackReason=row["fallbackReason"],
            support=row["supportClaim"],
            missing=", ".join(f"`{item}`" for item in row["missingProofs"]) or "none",
        )
        for row in evidence["matrixRows"]
    )
    validations = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['evidence']} |"
        for row in evidence["validationRows"]
    )
    non_claims = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    required = "\n".join(f"- `{item}`" for item in evidence["requiredValidation"])
    return f"""# KAN-040 Coverage Stroke Clip Closeout Matrix

KAN-040 aggregates the coverage/strokes/clips wave without adding renderer
support. The matrix keeps the only support claim bounded to
`m57-aaclip-bounded-grid`; every other row remains visible as a stable refusal
or dependency-gated row.

## Summary

| Metric | Count |
|---|---:|
| Total rows | {summary['totalRows']} |
| supportable-bounded | {summary['supportableBounded']} |
| visible-non-supportable | {summary['visibleNonSupportable']} |
| expected-unsupported | {summary['expectedUnsupported']} |
| dependency-gated | {summary['dependencyGated']} |
| support claims | {summary['supportClaims']} |
| unsupported rows | {summary['unsupportedRows']} |

## Matrix

| Ticket | Row | Classification | Status | Fallback | Support claim | Missing proofs |
|---|---|---|---|---|---:|---|
{matrix}

## Claim Guard

| Guard | Value |
|---|---|
| supportRowsMissingProofs | `{evidence['claimGuard']['supportRowsMissingProofs']}` |
| unsupportedRowsMissingFallback | `{evidence['claimGuard']['unsupportedRowsMissingFallback']}` |
| unsupportedRowsUnstableReason | `{evidence['claimGuard']['unsupportedRowsUnstableReason']}` |
| hiddenPromotionRows | `{evidence['claimGuard']['hiddenPromotionRows']}` |
| budgetOrThresholdChanges | `{evidence['claimGuard']['budgetOrThresholdChanges']}` |
| pmBundleCategoriesVisible | `{evidence['claimGuard']['pmBundleCategoriesVisible']}` |

## Required Validation

{required}

## Validation

| Check | Status | Evidence |
|---|---|---|
{validations}

## Non-Claims

{non_claims}
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(
        json.dumps(evidence, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    return evidence


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    output_dir = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else root / DEFAULT_OUTPUT_DIR
    evidence = write_outputs(root, output_dir)
    summary = evidence["summary"]
    print(
        "KAN-040 validation passed: "
        f"{summary['totalRows']} rows, {summary['supportableBounded']} bounded support, "
        f"{summary['visibleNonSupportable']} visible non-supportable, "
        f"{summary['expectedUnsupported']} expected-unsupported, "
        f"{summary['dependencyGated']} dependency-gated."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
