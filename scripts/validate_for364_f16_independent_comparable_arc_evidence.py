#!/usr/bin/env python3
"""Validate the FOR-364 independent comparable F16 arc evidence."""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-364"
SCENE_ID = "f16-independent-comparable-arc-evidence-for364"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-364-f16-independent-comparable-arc-evidence.md"
SKIA_REFERENCE_JSON = ARTIFACT_DIR / "skia-reference-samples.json"
SKIA_REFERENCE_PNG = ARTIFACT_DIR / "skia-reference.png"
SKIA_REFERENCE_PROVENANCE = ARTIFACT_DIR / "skia-reference-provenance.json"
CURRENT_KANVAS_JSON = ARTIFACT_DIR / "current-kanvas-samples.json"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-independent-comparable-f16-arc-evidence-after-for-363"
)
SOURCE_FINDINGS = [
    "global/kanvas/findings/for-363-constrained-f16-candidate-search-matrix-ready",
]

FOR363_SCENE_ID = "f16-constrained-candidate-search-for363"
FOR363_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR363_SCENE_ID / f"{FOR363_SCENE_ID}.json"
)
FOR362_SCENE_ID = "f16-rejected-candidate-closeout-for362"
FOR362_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR362_SCENE_ID / f"{FOR362_SCENE_ID}.json"
)
FOR361_SCENE_ID = "f16-bounded-independent-arc-capture-for361"
FOR361_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR361_SCENE_ID / f"{FOR361_SCENE_ID}.json"
)
FOR358_SCENE_ID = "f16-real-additional-non-arc-row-for358"
FOR358_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR358_SCENE_ID / f"{FOR358_SCENE_ID}.json"
)
FOR355_SCENE_ID = "f16-generalized-non-scene-arc-delta-candidate-for355"
FOR355_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR355_SCENE_ID / f"{FOR355_SCENE_ID}.json"
)
FOR345_SCENE_ID = "non-arc-rec2020-f16-reference-row-for345"
FOR345_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR345_SCENE_ID / f"{FOR345_SCENE_ID}.json"
)

FOR363_REQUIRED_DECISION = "F16_CONSTRAINED_CANDIDATE_SEARCH_MATRIX_READY"
FOR362_REQUIRED_DECISION = "F16_REJECTED_CANDIDATE_CLOSEOUT_AFTER_FOR361"
FOR361_REQUIRED_DECISION = "F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_REJECTS_CANDIDATE"
FOR358_REQUIRED_DECISION = "F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE"
FOR355_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE"
FOR345_REQUIRED_DECISION = "F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE"

CLOSED_CANDIDATE_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
CLOSED_CANDIDATE_FAMILY_ID = "nonzero_arc_delta_generalized_non_scene_guard_family"

DECISION_CAPTURED = "F16_INDEPENDENT_COMPARABLE_ARC_EVIDENCE_CAPTURED"
ALLOWED_DECISIONS = [DECISION_CAPTURED]

SOURCE = PROJECT_ROOT / "tools/skia-reference/independent_arc_f16_for364_reference.cpp"
RUNNER = PROJECT_ROOT / "tools/skia-reference/build_for364_f16_independent_comparable_arc_evidence.py"
CURRENT_CAPTURE_TEST = (
    PROJECT_ROOT
    / "skia-integration-tests/src/test/kotlin/org/skia/tests/For364IndependentComparableArcF16CurrentCaptureTest.kt"
)

REFERENCE_CAPTURE_COMMAND = "rtk python3 tools/skia-reference/build_for364_f16_independent_comparable_arc_evidence.py"
CURRENT_CAPTURE_COMMAND = (
    f"KANVAS_FOR364_CURRENT_CAPTURE_OUTPUT={CURRENT_KANVAS_JSON.relative_to(PROJECT_ROOT)} "
    "rtk ./gradlew --no-daemon --rerun-tasks "
    ":skia-integration-tests:test --tests org.skia.tests.For364IndependentComparableArcF16CurrentCaptureTest"
)
VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for364_f16_independent_comparable_arc_evidence.py",
    REFERENCE_CAPTURE_COMMAND,
    CURRENT_CAPTURE_COMMAND,
    "rtk python3 scripts/validate_for363_f16_constrained_candidate_search.py",
    "rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py",
    "rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py",
    "rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk python3 -m py_compile scripts/validate_for364_f16_independent_comparable_arc_evidence.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]

SAMPLE_METADATA = [
    {"name": "for364_background_top_left", "zone": "background", "x": 0, "y": 0, "covered": False},
    {"name": "for364_arc_diagonal_stroke_center", "zone": "stroke-center", "x": 16, "y": 16, "covered": True},
    {"name": "for364_arc_top_stroke_center", "zone": "stroke-center", "x": 32, "y": 10, "covered": True},
    {"name": "for364_arc_interior_clear", "zone": "interior-clear", "x": 32, "y": 32, "covered": False},
]

IMPLEMENTATION_FALSE_KEYS = (
    "rendererBehaviorChanged",
    "newColorPolicyImplemented",
    "candidateSelectedForImplementation",
    "candidateSelectedForEvaluation",
    "selectableCandidateDefined",
    "candidateFormulaDefined",
    "implementationPlanAuthorized",
    "colorToF16PremulChanged",
    "blendF16PremulModeChanged",
    "skBitmapGetPixelChanged",
    "skBitmapGetPixelAsSrgbChanged",
    "gpuOrWgslChanged",
    "geometryChanged",
    "coverageChanged",
    "fallbackChanged",
    "thresholdsChanged",
    "promotionChanged",
    "scoreChanged",
    "kadreChanged",
    "rendererFixtureOrCoordinateBranchAdded",
    "rendererSceneBranchAdded",
    "rendererSelectedCellOrFullGmCropBranchAdded",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-364 validation failed: {message}")


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


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def rgba(value: Any, label: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{label} must be RGBA")
    result = []
    for channel in value:
        require(isinstance(channel, int) and 0 <= channel <= 255, f"{label} channel out of range")
        result.append(channel)
    return result


def abs_delta(a: list[int], b: list[int]) -> list[int]:
    return [abs(a[i] - b[i]) for i in range(4)]


def signed_delta(a: list[int], b: list[int]) -> list[int]:
    return [a[i] - b[i] for i in range(4)]


def validate_implementation_guard(data: dict[str, Any], linear: str) -> None:
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), f"{linear} implementation block missing")
    for key in (
        "rendererBehaviorChanged",
        "gpuOrWgslChanged",
        "geometryChanged",
        "coverageChanged",
        "fallbackChanged",
        "thresholdsChanged",
        "promotionChanged",
        "scoreChanged",
        "kadreChanged",
    ):
        require(implementation.get(key) is False, f"{linear} implementation guard changed: {key}")


def validate_candidate(data: dict[str, Any], linear: str) -> dict[str, Any]:
    candidate = data.get("candidate")
    require(isinstance(candidate, dict), f"{linear} candidate block missing")
    require(candidate.get("policyId") == CLOSED_CANDIDATE_POLICY_ID, f"{linear} candidate policy changed")
    require(candidate.get("selectedForImplementation") is False, f"{linear} candidate selected for implementation")
    require(candidate.get("rendererBehaviorChanged") is False, f"{linear} changed renderer behavior")
    require(candidate.get("scoreIncreaseAuthorized") is False, f"{linear} authorized score increase")
    require(candidate.get("newCandidateDefined") is not True, f"{linear} defined a new candidate")
    return candidate


def validate_for363(for363: dict[str, Any]) -> list[dict[str, Any]]:
    require(for363.get("linear") == "FOR-363", "FOR-363 identity changed")
    require(for363.get("decision") == FOR363_REQUIRED_DECISION, "FOR-363 required decision changed")
    candidate = for363.get("closedRejectedCandidate")
    require(isinstance(candidate, dict), "FOR-363 closed rejected candidate missing")
    require(candidate.get("policyId") == CLOSED_CANDIDATE_POLICY_ID, "FOR-363 rejected candidate changed")
    require(candidate.get("reopenedByFor363") is False, "FOR-363 reopened the rejected candidate")
    require(candidate.get("selectedForEvaluation") is False, "FOR-363 selected the rejected candidate")
    require(candidate.get("selectedForImplementation") is False, "FOR-363 selected implementation")
    require(candidate.get("selectableInThisTicket") is False, "FOR-363 made candidate selectable")

    required = for363.get("requiredEvidenceForNextTicket")
    require(isinstance(required, list), "FOR-363 required evidence missing")
    required_ids = {item.get("id") for item in required if isinstance(item, dict)}
    require("independent-comparable-arc-scene" in required_ids, "FOR-363 no longer requires arc evidence")
    validate_implementation_guard(for363, "FOR-363")
    return required


def validate_for362(for362: dict[str, Any]) -> list[dict[str, Any]]:
    require(for362.get("linear") == "FOR-362", "FOR-362 identity changed")
    require(for362.get("decision") == FOR362_REQUIRED_DECISION, "FOR-362 closeout decision changed")
    candidate = validate_candidate(for362, "FOR-362")
    require(candidate.get("rejectedForSelection") is True, "FOR-362 candidate is no longer rejected")
    closeout = for362.get("selectionCloseout")
    require(isinstance(closeout, dict), "FOR-362 selection closeout missing")
    require(closeout.get("candidateSelectable") is False, "FOR-362 candidate became selectable")
    require(closeout.get("closedAsRejected") is True, "FOR-362 no longer closes the candidate as rejected")
    constraints = for362.get("nextCandidateSearchConstraints")
    require(isinstance(constraints, list), "FOR-362 next candidate constraints missing")
    constraint_ids = {item.get("id") for item in constraints if isinstance(item, dict)}
    for required_id in (
        "preserve-for345",
        "preserve-for358",
        "do-not-worsen-for361",
        "require-independent-comparable-arc-scene",
        "refuse-scene-coordinate-selected-cell-full-gm-crop-branches",
    ):
        require(required_id in constraint_ids, f"FOR-362 missing constraint: {required_id}")
    validate_implementation_guard(for362, "FOR-362")
    return constraints


def validate_for361(for361: dict[str, Any]) -> dict[str, Any]:
    require(for361.get("linear") == "FOR-361", "FOR-361 identity changed")
    require(for361.get("decision") == FOR361_REQUIRED_DECISION, "FOR-361 rejection decision changed")
    candidate = validate_candidate(for361, "FOR-361")
    require(candidate.get("reusedFromFor355") is True, "FOR-361 no longer audits the FOR-355 candidate")
    row = for361.get("row")
    require(isinstance(row, dict), "FOR-361 row missing")
    require(row.get("arcScene") is True, "FOR-361 row no longer marks arcScene")
    require(row.get("independentFromFor340For341AdjacentGroups") is True, "FOR-361 independence changed")
    require(row.get("referenceCurrentCandidateComparable") is True, "FOR-361 comparability changed")
    require(row.get("selectedCellSubstitutionUsed") is False, "FOR-361 selected-cell substitution changed")
    require(row.get("fullGmCrop") is False, "FOR-361 full-GM crop changed")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-361 residuals missing")
    require(residuals.get("currentResidual") == 0, "FOR-361 current residual changed")
    require(residuals.get("candidateResidual") == 37, "FOR-361 candidate residual changed")
    require(residuals.get("worsenedSampleCount") == 1, "FOR-361 worsened count changed")
    validate_implementation_guard(for361, "FOR-361")
    return {
        "source": "FOR-361",
        "sourceArtifact": rel(FOR361_ARTIFACT),
        "rowId": row.get("rowId"),
        "decision": for361.get("decision"),
        "currentResidual": residuals.get("currentResidual"),
        "candidateResidual": residuals.get("candidateResidual"),
        "candidateMinusCurrentResidual": residuals.get("candidateMinusCurrentResidual"),
        "worsenedSampleCount": residuals.get("worsenedSampleCount"),
        "preservedAsRejectedEvidence": True,
    }


def validate_for355(for355: dict[str, Any]) -> dict[str, Any]:
    require(for355.get("linear") == "FOR-355", "FOR-355 identity changed")
    require(for355.get("decision") == FOR355_REQUIRED_DECISION, "FOR-355 decision changed")
    candidate = validate_candidate(for355, "FOR-355")
    require(candidate.get("family") == CLOSED_CANDIDATE_FAMILY_ID, "FOR-355 family changed")
    require(candidate.get("rendererSelectable") is False, "FOR-355 became renderer-selectable")
    validate_implementation_guard(for355, "FOR-355")
    return {
        "source": "FOR-355",
        "sourceArtifact": rel(FOR355_ARTIFACT),
        "policyId": candidate.get("policyId"),
        "family": candidate.get("family"),
        "closedBy": "FOR-362",
        "reopenedByFor364": False,
        "selectableInFor364": False,
        "selectedForEvaluation": False,
        "selectedForImplementation": False,
    }


def validate_non_arc_guard(data: dict[str, Any], linear: str, required_decision: str, artifact: Path) -> dict[str, Any]:
    require(data.get("linear") == linear, f"{linear} identity changed")
    require(data.get("decision") == required_decision, f"{linear} decision changed")
    row = data.get("row")
    require(isinstance(row, dict), f"{linear} row missing")
    require(row.get("nonArc") is True, f"{linear} row is no longer non-arc")
    require(row.get("referenceCurrentCandidateComparable") is True, f"{linear} comparability changed")
    validate_implementation_guard(data, linear)
    return {
        "source": linear,
        "sourceArtifact": rel(artifact),
        "rowId": row.get("rowId"),
        "guardPreserved": True,
    }


def validate_source_guardrails() -> dict[str, Any]:
    required = {
        SOURCE: [
            "canvas->drawArc(SkRect::MakeXYWH(10, 10, 44, 44), 180, 100, false, red)",
            "red.setStrokeCap(SkPaint::kButt_Cap)",
            "red.setColor(SkColorSetARGB(128, 255, 0, 0))",
            "f16-independent-comparable-arc-evidence-for364",
            "independentFromFor361",
            "SK_ColorTRANSPARENT",
            "SK_ColorWHITE",
        ],
        RUNNER: [
            "independent_arc_f16_for364_reference.cpp",
            "F16_INDEPENDENT_COMPARABLE_ARC_EVIDENCE_SKIA_REFERENCE_CAPTURED",
            "selectedCellSubstitutionAccepted",
            "sceneCoordinateBranchAccepted",
        ],
        CURRENT_CAPTURE_TEST: [
            "For364IndependentComparableArcF16CurrentCaptureTest",
            "kanvas.for364.currentCapture.output",
            "SkBitmap(72, 72, rec2020, SkColorType.kRGBA_F16Norm)",
            "SkCanvas(bitmap).drawArc(SkRect.MakeXYWH(10f, 10f, 44f, 44f), 180f, 100f, false, paint)",
            "bitmap.getPixelAsSrgb(sample.x, sample.y)",
        ],
    }
    for path, snippets in required.items():
        require(path.is_file(), f"missing source file: {rel(path)}")
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            require(snippet in text, f"{rel(path)} missing required snippet: {snippet}")
    source_text = SOURCE.read_text(encoding="utf-8")
    for forbidden in ("CircularArcsStrokeButt", "fullGmCrop\": true", "selectedCellSubstitutionAccepted\": true"):
        require(forbidden not in source_text, f"source uses forbidden term `{forbidden}`")
    return {
        "source": {"path": rel(SOURCE), "sha256": sha256(SOURCE), "present": True},
        "runner": {"path": rel(RUNNER), "present": True, "headless": True},
        "currentCaptureTest": {"path": rel(CURRENT_CAPTURE_TEST), "present": True, "headless": True},
    }


def keyed_samples(data: dict[str, Any], key: str) -> dict[str, dict[str, Any]]:
    raw_samples = data.get("samples")
    require(isinstance(raw_samples, list), f"{key} samples missing")
    result: dict[str, dict[str, Any]] = {}
    for sample in raw_samples:
        require(isinstance(sample, dict), f"{key} sample must be an object")
        name = sample.get("name")
        require(isinstance(name, str) and name, f"{key} sample name missing")
        require(name not in result, f"duplicate sample {name}")
        rgba(sample.get(key), f"{name} {key}")
        result[name] = sample
    return result


def validate_reference(reference: dict[str, Any], provenance: dict[str, Any]) -> None:
    require(reference.get("sceneId") == SCENE_ID, "Skia reference scene id changed")
    require(
        reference.get("sourceType") == "isolated-skia-independent-arc-rec2020-f16-for364-butt-cap",
        "Skia source type changed",
    )
    require(reference.get("dimensions") == {"width": 72, "height": 72}, "Skia dimensions changed")
    require(reference.get("colorType") == "kRGBA_F16Norm", "Skia color type changed")
    require(reference.get("colorSpace") == "Rec.2020", "Skia color space changed")
    require(reference.get("blendMode") == "kSrcOver", "Skia blend mode changed")
    require(reference.get("arcScene") is True, "Skia reference must be an arc scene")
    require(reference.get("independentFromFor361") is True, "Skia reference FOR-361 independence changed")
    require(
        reference.get("independentFromFor340For341AdjacentGroups") is True,
        "Skia reference FOR-340/FOR-341 independence changed",
    )
    require(provenance.get("linear") == LINEAR_ID, "Skia provenance linear id changed")
    require(provenance.get("sceneId") == SCENE_ID, "Skia provenance scene id changed")
    require(provenance.get("referenceJsonSha256") == sha256(SKIA_REFERENCE_JSON), "Skia reference JSON sha changed")
    require(provenance.get("referencePngSha256") == sha256(SKIA_REFERENCE_PNG), "Skia reference PNG sha changed")
    proof = provenance.get("sourceTypeProof")
    require(isinstance(proof, dict), "Skia provenance proof missing")
    require(proof.get("compiledRepoOwnedSource") is True, "Skia source compile proof missing")
    require(proof.get("executedBinary") is True, "Skia execution proof missing")
    require(proof.get("isolatedIndependentArcOutput") is True, "Skia independent arc proof missing")
    require(proof.get("independentFromFor361") is True, "Skia FOR-361 independence proof missing")
    require(proof.get("independentFromFor340For341AdjacentGroups") is True, "Skia adjacent independence proof missing")
    require(provenance.get("fullGmCrop") is False, "Skia provenance uses full GM crop")
    require(provenance.get("selectedCellSubstitutionAccepted") is False, "Skia provenance accepts selected-cell substitution")
    require(provenance.get("sceneCoordinateBranchAccepted") is False, "Skia provenance accepts scene/coordinate branch")
    require(provenance.get("fixtureBranchAccepted") is False, "Skia provenance accepts fixture branch")


def validate_current_capture(current: dict[str, Any]) -> None:
    require(current.get("linear") == LINEAR_ID, "current capture linear id changed")
    require(current.get("sceneId") == SCENE_ID, "current capture scene id changed")
    require(
        current.get("sourceType") == "current-kanvas-independent-arc-rec2020-f16-for364-butt-cap",
        "current source type changed",
    )
    require(current.get("dimensions") == {"width": 72, "height": 72}, "current dimensions changed")
    require(current.get("colorType") == "kRGBA_F16Norm", "current color type changed")
    require(current.get("colorSpace") == "Rec.2020", "current color space changed")
    require(current.get("blendMode") == "kSrcOver", "current blend mode changed")
    require(current.get("arcScene") is True, "current capture must be an arc scene")
    require(current.get("independentFromFor361") is True, "current FOR-361 independence changed")
    require(
        current.get("independentFromFor340For341AdjacentGroups") is True,
        "current FOR-340/FOR-341 independence changed",
    )


def build_samples(reference: dict[str, Any], current_capture: dict[str, Any]) -> list[dict[str, Any]]:
    reference_samples = keyed_samples(reference, "referenceSrgbRgba")
    raw_reference_samples = keyed_samples(reference, "rawReferenceRgba")
    current_samples = keyed_samples(current_capture, "currentKanvasSrgbRgba")
    samples: list[dict[str, Any]] = []
    for metadata in SAMPLE_METADATA:
        name = metadata["name"]
        require(name in reference_samples, f"Skia reference missing sample {name}")
        require(name in raw_reference_samples, f"Skia raw reference missing sample {name}")
        require(name in current_samples, f"Kanvas current capture missing sample {name}")
        ref_sample = reference_samples[name]
        cur_sample = current_samples[name]
        require(ref_sample.get("x") == metadata["x"] and ref_sample.get("y") == metadata["y"], f"Skia coords changed: {name}")
        require(cur_sample.get("x") == metadata["x"] and cur_sample.get("y") == metadata["y"], f"Kanvas coords changed: {name}")
        require(ref_sample.get("zone") == metadata["zone"], f"Skia zone changed: {name}")
        require(cur_sample.get("zone") == metadata["zone"], f"Kanvas zone changed: {name}")
        raw_reference = rgba(ref_sample["rawReferenceRgba"], f"{name} raw reference")
        reference_rgba = rgba(ref_sample["referenceSrgbRgba"], f"{name} reference")
        current_rgba = rgba(cur_sample["currentKanvasSrgbRgba"], f"{name} current")
        current_abs = abs_delta(current_rgba, reference_rgba)
        samples.append(
            {
                "name": name,
                "zone": metadata["zone"],
                "x": metadata["x"],
                "y": metadata["y"],
                "covered": metadata["covered"],
                "rawReferenceRgba": raw_reference,
                "rawReferenceAlpha": raw_reference[3],
                "referenceSrgbRgba": reference_rgba,
                "currentKanvasSrgbRgba": current_rgba,
                "futureCandidateRgba": None,
                "futureCandidatePolicyId": None,
                "futureCandidateFormulaDefined": False,
                "currentVsReferenceSignedDelta": signed_delta(current_rgba, reference_rgba),
                "currentVsReferenceAbsDelta": current_abs,
                "currentResidual": sum(current_abs),
                "futureCandidateResidual": None,
                "futureCandidateWorsensCurrent": None,
            }
        )
    return samples


def summarize(samples: list[dict[str, Any]]) -> dict[str, Any]:
    current = sum(sample["currentResidual"] for sample in samples)
    return {
        "sampleCount": len(samples),
        "coveredSampleCount": sum(1 for sample in samples if sample["covered"]),
        "rawCoveredSampleCount": sum(1 for sample in samples if sample["rawReferenceAlpha"] > 0),
        "currentResidual": current,
        "futureCandidateResidual": None,
        "candidateMinusCurrentResidual": None,
        "worsenedSampleCount": None,
    }


def build_artifact(
    for363: dict[str, Any],
    for362: dict[str, Any],
    for361: dict[str, Any],
    for358: dict[str, Any],
    for355: dict[str, Any],
    for345: dict[str, Any],
    reference: dict[str, Any],
    provenance: dict[str, Any],
    current_capture: dict[str, Any],
) -> dict[str, Any]:
    source_runner = validate_source_guardrails()
    required363 = validate_for363(for363)
    constraints362 = validate_for362(for362)
    rejection361 = validate_for361(for361)
    for358_guard = validate_non_arc_guard(for358, "FOR-358", FOR358_REQUIRED_DECISION, FOR358_ARTIFACT)
    closed_candidate = validate_for355(for355)
    for345_guard = validate_non_arc_guard(for345, "FOR-345", FOR345_REQUIRED_DECISION, FOR345_ARTIFACT)
    validate_reference(reference, provenance)
    validate_current_capture(current_capture)

    samples = build_samples(reference, current_capture)
    residuals = summarize(samples)
    require(residuals["sampleCount"] == 4, "FOR-364 sample count changed")
    require(residuals["rawCoveredSampleCount"] == residuals["coveredSampleCount"], "FOR-364 covered metadata mismatch")
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": SOURCE_FINDINGS,
        "decision": DECISION_CAPTURED,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-364 captures a new independent Rec.2020 kRGBA_F16Norm drawArc row with isolated Skia "
            "reference samples and current Kanvas CPU samples. It does not define or select a future "
            "candidate; future-candidate fields remain explicit placeholders for the next evaluation ticket."
        ),
        "inputValidation": {
            "for363Artifact": rel(FOR363_ARTIFACT),
            "for363Decision": for363.get("decision"),
            "for363RequiredDecision": FOR363_REQUIRED_DECISION,
            "for362Artifact": rel(FOR362_ARTIFACT),
            "for362Decision": for362.get("decision"),
            "for362RequiredDecision": FOR362_REQUIRED_DECISION,
            "for361Artifact": rel(FOR361_ARTIFACT),
            "for361Decision": for361.get("decision"),
            "for361RequiredDecision": FOR361_REQUIRED_DECISION,
            "for358Artifact": rel(FOR358_ARTIFACT),
            "for358Decision": for358.get("decision"),
            "for358RequiredDecision": FOR358_REQUIRED_DECISION,
            "for355Artifact": rel(FOR355_ARTIFACT),
            "for355Decision": for355.get("decision"),
            "for355RequiredDecision": FOR355_REQUIRED_DECISION,
            "for345Artifact": rel(FOR345_ARTIFACT),
            "for345Decision": for345.get("decision"),
            "for345RequiredDecision": FOR345_REQUIRED_DECISION,
        },
        "for363RequiredEvidence": required363,
        "preservedConstraintsFromFor362": constraints362,
        "closedRejectedCandidate": closed_candidate,
        "preservedRejectionEvidence": rejection361,
        "nonArcGuardsPreserved": {
            "for345": for345_guard,
            "for358": for358_guard,
        },
        "row": {
            "rowId": "for364-independent-butt-cap-arc-rec2020-f16",
            "sceneId": SCENE_ID,
            "sourceKind": "isolated-skia-current-kanvas",
            "arcScene": True,
            "newIndependentArcRow": True,
            "independentFromFor361": True,
            "independentFromFor340For341AdjacentGroups": True,
            "excludedScenes": [
                "f16-bounded-independent-arc-capture-for361",
                "circular_arcs_stroke_butt",
                "circular_arcs_stroke_butt_adjacent_groups",
            ],
            "selectedCellSubstitutionUsed": False,
            "fullGmCrop": False,
            "referenceCurrentCandidateComparable": True,
            "futureCandidateComparableWhenSelected": True,
            "reference": {
                "status": "isolated-skia-independent-arc-rec2020-f16-reference-available",
                "captureCommand": REFERENCE_CAPTURE_COMMAND,
                "samplesPath": rel(SKIA_REFERENCE_JSON),
                "provenancePath": rel(SKIA_REFERENCE_PROVENANCE),
                "pngPath": rel(SKIA_REFERENCE_PNG),
                "referenceJsonSha256": sha256(SKIA_REFERENCE_JSON),
                "referencePngSha256": sha256(SKIA_REFERENCE_PNG),
            },
            "current": {
                "status": "current-kanvas-kotlin-cpu-rec2020-f16-drawArc-samples-captured",
                "captureCommand": CURRENT_CAPTURE_COMMAND,
                "samplesPath": rel(CURRENT_KANVAS_JSON),
                "captureMethod": current_capture.get("captureMethod"),
            },
            "futureCandidatePlaceholder": {
                "status": "placeholder-only-no-candidate-selected",
                "candidatePolicyId": None,
                "candidateFormulaDefined": False,
                "selectedForEvaluation": False,
                "selectedForImplementation": False,
                "samples": [],
            },
            "samples": samples,
            "residuals": residuals,
            "captureRefusals": {
                "sceneIdBranch": True,
                "coordinateBranch": True,
                "selectedCellBranch": True,
                "fixtureOnlyRendererBranch": True,
                "fullGmCropBranch": True,
                "for361ReuseAsNewRow": True,
                "for340For341AdjacentGroupReuse": True,
            },
        },
        "scene": {
            "dimensions": {"width": 72, "height": 72},
            "colorType": "kRGBA_F16Norm",
            "colorSpace": "Rec.2020",
            "draw": {
                "op": "drawArc",
                "ovalXYWH": [10, 10, 44, 44],
                "startAngleDeg": 180,
                "sweepAngleDeg": 100,
                "useCenter": False,
                "antiAlias": False,
                "style": "kStroke_Style",
                "strokeWidth": 6,
                "strokeCap": "kButt_Cap",
                "blendMode": "kSrcOver",
                "paintArgb": [128, 255, 0, 0],
            },
            "independentFromFor361": True,
            "independentFromFor340For341AdjacentGroups": True,
            "fullGmCrop": False,
            "selectedCellSubstitutionUsed": False,
            "fixtureOrCoordinateRendererBranchUsed": False,
        },
        "referenceSource": source_runner,
        "implementation": {
            "evidenceOnly": True,
            "rendererBehaviorChanged": False,
            "newColorPolicyImplemented": False,
            "candidateSelectedForImplementation": False,
            "candidateSelectedForEvaluation": False,
            "selectableCandidateDefined": False,
            "candidateFormulaDefined": False,
            "implementationPlanAuthorized": False,
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
            "rendererFixtureOrCoordinateBranchAdded": False,
            "rendererSceneBranchAdded": False,
            "rendererSelectedCellOrFullGmCropBranchAdded": False,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "artifact source memory changed")
    require(data.get("sourceFindings") == SOURCE_FINDINGS, "artifact source findings changed")
    require(data.get("decision") == DECISION_CAPTURED, "decision changed")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")
    candidate = data.get("closedRejectedCandidate")
    require(isinstance(candidate, dict), "closed rejected candidate missing")
    require(candidate.get("policyId") == CLOSED_CANDIDATE_POLICY_ID, "closed candidate policy changed")
    require(candidate.get("reopenedByFor364") is False, "FOR-364 reopened candidate")
    require(candidate.get("selectableInFor364") is False, "FOR-364 made candidate selectable")
    require(candidate.get("selectedForEvaluation") is False, "FOR-364 selected candidate for evaluation")
    require(candidate.get("selectedForImplementation") is False, "FOR-364 selected candidate")
    row = data.get("row")
    require(isinstance(row, dict), "row missing")
    require(row.get("arcScene") is True, "row must be arc")
    require(row.get("newIndependentArcRow") is True, "row must be new independent evidence")
    require(row.get("independentFromFor361") is True, "row FOR-361 independence missing")
    require(row.get("independentFromFor340For341AdjacentGroups") is True, "row adjacent independence missing")
    require(row.get("selectedCellSubstitutionUsed") is False, "selected-cell substitution used")
    require(row.get("fullGmCrop") is False, "full-GM crop used")
    require(row.get("referenceCurrentCandidateComparable") is True, "row comparability missing")
    future = row.get("futureCandidatePlaceholder")
    require(isinstance(future, dict), "future candidate placeholder missing")
    require(future.get("candidateFormulaDefined") is False, "future candidate formula defined")
    require(future.get("selectedForEvaluation") is False, "future candidate selected")
    require(future.get("selectedForImplementation") is False, "future candidate selected for implementation")
    samples = row.get("samples")
    require(isinstance(samples, list) and len(samples) == 4, "expected four samples")
    require(any(sample.get("rawReferenceAlpha", 0) > 0 for sample in samples), "no covered raw sample")
    for sample in samples:
        for key in ("rawReferenceRgba", "referenceSrgbRgba", "currentKanvasSrgbRgba"):
            rgba(sample.get(key), f"{sample.get('name')} {key}")
        require(sample.get("futureCandidateRgba") is None, f"{sample.get('name')} candidate should be placeholder")
        require(isinstance(sample.get("currentResidual"), int), f"{sample.get('name')} current residual missing")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "residuals missing")
    require(residuals.get("sampleCount") == 4, "sample count changed")
    require(residuals.get("coveredSampleCount") == 2, "covered sample count changed")
    require(isinstance(residuals.get("currentResidual"), int), "current residual missing")
    require(residuals.get("futureCandidateResidual") is None, "future candidate residual should be null")
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation missing")
    require(implementation.get("evidenceOnly") is True, "FOR-364 must be evidence-only")
    for key in IMPLEMENTATION_FALSE_KEYS:
        require(implementation.get(key) is False, f"implementation guard changed: {key}")


def build_report(data: dict[str, Any]) -> str:
    row = data["row"]
    sample_rows = "\n".join(
        "| {name} | {xy} | {zone} | {raw} | {ref} | {current} | {current_residual} |".format(
            name=sample["name"],
            xy=f"{sample['x']},{sample['y']}",
            zone=sample["zone"],
            raw=json.dumps(sample["rawReferenceRgba"]),
            ref=json.dumps(sample["referenceSrgbRgba"]),
            current=json.dumps(sample["currentKanvasSrgbRgba"]),
            current_residual=sample["currentResidual"],
        )
        for sample in row["samples"]
    )
    refusals = "\n".join(
        f"- `{name}`: `{value}`" for name, value in row["captureRefusals"].items()
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    residuals = row["residuals"]
    return f"""# FOR-364 F16 Independent Comparable Arc Evidence

Linear: `FOR-364`

Decision: `{data["decision"]}`

FOR-364 captures a new independent Rec.2020 `kRGBA_F16Norm` `drawArc` row after
FOR-363. It is evidence-only: no candidate is defined, selected, implemented, or
used to raise score.

## Result

The new row `{row["rowId"]}` has isolated Skia reference samples and current
Kanvas CPU samples. Future-candidate fields are present as explicit placeholders
for a later evaluation ticket, but remain null because FOR-364 does not select a
candidate formula.

## Preserved Gates

- FOR-363: `{data["inputValidation"]["for363Decision"]}`
- FOR-362: `{data["inputValidation"]["for362Decision"]}`
- FOR-361: `{data["inputValidation"]["for361Decision"]}`
- FOR-358: `{data["inputValidation"]["for358Decision"]}`
- FOR-355: `{data["inputValidation"]["for355Decision"]}`
- FOR-345: `{data["inputValidation"]["for345Decision"]}`

The rejected FOR-355 candidate remains closed and unavailable:
`{data["closedRejectedCandidate"]["policyId"]}`.

## Samples

| sample | x,y | zone | raw reference | reference | current | current residual |
|---|---|---|---|---|---|---:|
{sample_rows}

## Aggregate Residuals

| metric | value |
|---|---:|
| samples | {residuals["sampleCount"]} |
| covered samples | {residuals["coveredSampleCount"]} |
| raw covered samples | {residuals["rawCoveredSampleCount"]} |
| current residual | {residuals["currentResidual"]} |
| future candidate residual | `{residuals["futureCandidateResidual"]}` |

## Independence Boundary

- Oval: `[10, 10, 44, 44]`
- Start/sweep: `180` / `100`
- Stroke cap: `kButt_Cap`
- Paint ARGB: `[128, 255, 0, 0]`
- Independent from FOR-361: `True`
- Independent from FOR-340/FOR-341 adjacent groups: `True`
- Selected-cell substitution used: `False`
- Full-GM crop used: `False`

## Refused Shortcuts

{refusals}

## Non-goals Preserved

- No renderer behavior change.
- No selectable candidate, candidate formula, candidate evaluation, candidate
  implementation, score increase, or threshold change.
- No GPU/WGSL, geometry, coverage, fallback, promotion, Kadre,
  `SkBitmap.getPixel`, F16 premul, or blend behavior change.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Skia samples: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/skia-reference-samples.json`
- Kanvas samples: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/current-kanvas-samples.json`
- Skia PNG: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/skia-reference.png`
- Validator: `scripts/validate_for364_f16_independent_comparable_arc_evidence.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-364-f16-independent-comparable-arc-evidence.md`

## Validation

{validation}
"""


def main() -> None:
    data = build_artifact(
        load_json(FOR363_ARTIFACT),
        load_json(FOR362_ARTIFACT),
        load_json(FOR361_ARTIFACT),
        load_json(FOR358_ARTIFACT),
        load_json(FOR355_ARTIFACT),
        load_json(FOR345_ARTIFACT),
        load_json(SKIA_REFERENCE_JSON),
        load_json(SKIA_REFERENCE_PROVENANCE),
        load_json(CURRENT_KANVAS_JSON),
    )
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print(
        f"{DECISION_CAPTURED}: samples={data['row']['residuals']['sampleCount']} "
        f"current={data['row']['residuals']['currentResidual']} candidateSelected=false"
    )


if __name__ == "__main__":
    main()
