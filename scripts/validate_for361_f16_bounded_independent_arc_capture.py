#!/usr/bin/env python3
"""Validate the FOR-361 bounded independent F16 arc capture for the F16 candidate."""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-361"
SCENE_ID = "f16-bounded-independent-arc-capture-for361"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-361-f16-bounded-independent-arc-capture.md"
SKIA_REFERENCE_JSON = ARTIFACT_DIR / "skia-reference-samples.json"
SKIA_REFERENCE_PNG = ARTIFACT_DIR / "skia-reference.png"
SKIA_REFERENCE_PROVENANCE = ARTIFACT_DIR / "skia-reference-provenance.json"
CURRENT_KANVAS_JSON = ARTIFACT_DIR / "current-kanvas-samples.json"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-bounded-independent-arc-capture-for-f16-candidate"
)
SOURCE_FINDINGS = [
    "global/kanvas/findings/for-360-independent-arc-scene-evidence-remains-partial",
    "global/kanvas/findings/for-359-f16-candidate-still-requires-independent-arc-scene",
]

FOR360_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/f16-independent-arc-scene-evidence-for360/"
    "f16-independent-arc-scene-evidence-for360.json"
)
FOR359_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/f16-candidate-after-for358-guard-for359/"
    "f16-candidate-after-for358-guard-for359.json"
)
FOR358_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/f16-real-additional-non-arc-row-for358/"
    "f16-real-additional-non-arc-row-for358.json"
)
FOR355_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/f16-generalized-non-scene-arc-delta-candidate-for355/"
    "f16-generalized-non-scene-arc-delta-candidate-for355.json"
)

FOR360_REQUIRED_DECISION = "F16_INDEPENDENT_ARC_SCENE_EVIDENCE_PARTIAL"
FOR359_REQUIRED_DECISION = "F16_GENERALIZED_CANDIDATE_STILL_REQUIRES_INDEPENDENT_ARC_SCENE"
FOR358_REQUIRED_DECISION = "F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE"
FOR355_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE"

CANDIDATE_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
CANDIDATE_FAMILY_ID = "nonzero_arc_delta_generalized_non_scene_guard_family"

DECISION_SUPPORTS = "F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_SUPPORTS_CANDIDATE"
DECISION_REJECTS = "F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_REJECTS_CANDIDATE"
DECISION_PARTIAL = "F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_PARTIAL"
ALLOWED_DECISIONS = [DECISION_SUPPORTS, DECISION_REJECTS, DECISION_PARTIAL]

SOURCE = PROJECT_ROOT / "tools/skia-reference/bounded_independent_arc_f16_for361_reference.cpp"
RUNNER = PROJECT_ROOT / "tools/skia-reference/build_for361_f16_bounded_independent_arc_capture.py"
CURRENT_CAPTURE_TEST = (
    PROJECT_ROOT
    / "skia-integration-tests/src/test/kotlin/org/skia/tests/For361BoundedIndependentArcF16CurrentCaptureTest.kt"
)

REFERENCE_CAPTURE_COMMAND = "rtk python3 tools/skia-reference/build_for361_f16_bounded_independent_arc_capture.py"
CURRENT_CAPTURE_COMMAND = (
    f"KANVAS_FOR361_CURRENT_CAPTURE_OUTPUT={CURRENT_KANVAS_JSON.relative_to(PROJECT_ROOT)} "
    "rtk ./gradlew --no-daemon --rerun-tasks "
    ":skia-integration-tests:test --tests org.skia.tests.For361BoundedIndependentArcF16CurrentCaptureTest"
)
VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py",
    REFERENCE_CAPTURE_COMMAND,
    CURRENT_CAPTURE_COMMAND,
    "rtk python3 scripts/validate_for360_f16_independent_arc_scene_evidence.py",
    "rtk python3 scripts/validate_for359_f16_candidate_after_for358_guard.py",
    "rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 -m py_compile scripts/validate_for361_f16_bounded_independent_arc_capture.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]

SAMPLE_METADATA = [
    {"name": "for361_background_top_left", "zone": "background", "x": 0, "y": 0, "covered": False},
    {"name": "for361_arc_right_stroke_center", "zone": "stroke-center", "x": 52, "y": 32, "covered": True},
    {"name": "for361_arc_lower_right_stroke", "zone": "stroke-edge", "x": 44, "y": 46, "covered": True},
    {"name": "for361_arc_interior_clear", "zone": "interior-clear", "x": 32, "y": 32, "covered": False},
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-361 validation failed: {message}")


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


def artifact_status(path: Path) -> dict[str, Any]:
    result: dict[str, Any] = {"path": rel(path), "exists": path.is_file()}
    if path.is_file():
        result["sha256"] = sha256(path)
    return result


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


def validate_prerequisite(data: dict[str, Any], linear: str, required_decision: str) -> dict[str, Any]:
    require(data.get("linear") == linear, f"{linear} identity changed")
    require(data.get("decision") == required_decision, f"{linear} required decision missing")
    candidate = data.get("candidate")
    require(isinstance(candidate, dict), f"{linear} candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, f"{linear} candidate changed")
    require(candidate.get("selectedForImplementation") is False, f"{linear} selected candidate")
    require(candidate.get("rendererBehaviorChanged") is False, f"{linear} changed renderer behavior")
    require(candidate.get("scoreIncreaseAuthorized") is False, f"{linear} authorized score increase")
    return candidate


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


def validate_source_guardrails() -> dict[str, Any]:
    required = {
        SOURCE: [
            "canvas->drawArc(SkRect::MakeXYWH(12, 12, 40, 40), 0, 120, false, blue)",
            "blue.setStrokeCap(SkPaint::kRound_Cap)",
            "blue.setColor(SkColorSetARGB(100, 0, 0, 255))",
            "SK_ColorTRANSPARENT",
            "SK_ColorWHITE",
            "f16-bounded-independent-arc-capture-for361",
            "independentFromFor340For341AdjacentGroups",
        ],
        RUNNER: [
            "bounded_independent_arc_f16_for361_reference.cpp",
            "libskia.a",
            "F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_SKIA_REFERENCE_CAPTURED",
            "selectedCellSubstitutionAccepted",
        ],
        CURRENT_CAPTURE_TEST: [
            "kanvas.for361.currentCapture.output",
            "For361BoundedIndependentArcF16CurrentCaptureTest",
            "SkBitmap(64, 64, rec2020, SkColorType.kRGBA_F16Norm)",
            "bitmap.eraseColor(SK_ColorWHITE)",
            "SkCanvas(bitmap).drawArc(SkRect.MakeXYWH(12f, 12f, 40f, 40f), 0f, 120f, false, paint)",
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
        reference.get("sourceType") == "isolated-skia-bounded-independent-arc-rec2020-f16-for361-round-cap",
        "Skia source type changed",
    )
    require(reference.get("dimensions") == {"width": 64, "height": 64}, "Skia dimensions changed")
    require(reference.get("colorType") == "kRGBA_F16Norm", "Skia color type changed")
    require(reference.get("colorSpace") == "Rec.2020", "Skia color space changed")
    require(reference.get("blendMode") == "kSrcOver", "Skia blend mode changed")
    require(reference.get("arcScene") is True, "Skia reference must be an arc scene")
    require(reference.get("independentFromFor340For341AdjacentGroups") is True, "Skia reference independence changed")
    require(provenance.get("linear") == LINEAR_ID, "Skia provenance linear id changed")
    require(provenance.get("sceneId") == SCENE_ID, "Skia provenance scene id changed")
    require(provenance.get("referenceJsonSha256") == sha256(SKIA_REFERENCE_JSON), "Skia reference JSON sha changed")
    require(provenance.get("referencePngSha256") == sha256(SKIA_REFERENCE_PNG), "Skia reference PNG sha changed")
    proof = provenance.get("sourceTypeProof")
    require(isinstance(proof, dict), "Skia provenance proof missing")
    require(proof.get("compiledRepoOwnedSource") is True, "Skia source compile proof missing")
    require(proof.get("executedBinary") is True, "Skia execution proof missing")
    require(proof.get("isolatedBoundedArcOutput") is True, "Skia bounded arc proof missing")
    require(proof.get("independentFromFor340For341AdjacentGroups") is True, "Skia independence proof missing")
    require(provenance.get("fullGmCrop") is False, "Skia provenance uses full GM crop")
    require(provenance.get("selectedCellSubstitutionAccepted") is False, "Skia provenance accepts selected-cell substitution")


def validate_current_capture(current: dict[str, Any]) -> None:
    require(current.get("linear") == LINEAR_ID, "current capture linear id changed")
    require(current.get("sceneId") == SCENE_ID, "current capture scene id changed")
    require(
        current.get("sourceType") == "current-kanvas-bounded-independent-arc-rec2020-f16-for361-round-cap",
        "current source type changed",
    )
    require(current.get("dimensions") == {"width": 64, "height": 64}, "current dimensions changed")
    require(current.get("colorType") == "kRGBA_F16Norm", "current color type changed")
    require(current.get("colorSpace") == "Rec.2020", "current color space changed")
    require(current.get("blendMode") == "kSrcOver", "current blend mode changed")
    require(current.get("arcScene") is True, "current capture must be an arc scene")
    require(current.get("independentFromFor340For341AdjacentGroups") is True, "current independence changed")


def for355_rule_applies(raw_reference: list[int], zone: str) -> bool:
    return zone == "stroke-center" and 0 < raw_reference[3] < 255


def over_white(raw_reference: list[int]) -> list[int]:
    r, g, b, alpha = raw_reference
    return [
        (r * alpha + 255 * (255 - alpha) + 127) // 255,
        (g * alpha + 255 * (255 - alpha) + 127) // 255,
        (b * alpha + 255 * (255 - alpha) + 127) // 255,
        255,
    ]


def candidate_rgba(raw_reference: list[int], zone: str, current_rgba: list[int]) -> list[int]:
    if for355_rule_applies(raw_reference, zone):
        return over_white(raw_reference)
    return current_rgba.copy()


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
        candidate = candidate_rgba(raw_reference, metadata["zone"], current_rgba)
        current_abs = abs_delta(current_rgba, reference_rgba)
        candidate_abs = abs_delta(candidate, reference_rgba)
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
                "candidatePolicyId": CANDIDATE_POLICY_ID,
                "candidateRgba": candidate,
                "candidatePolicyRgba": candidate,
                "for355RuleApplies": for355_rule_applies(raw_reference, metadata["zone"]),
                "currentVsReferenceSignedDelta": signed_delta(current_rgba, reference_rgba),
                "currentVsReferenceAbsDelta": current_abs,
                "currentResidual": sum(current_abs),
                "candidateVsReferenceSignedDelta": signed_delta(candidate, reference_rgba),
                "candidateVsReferenceAbsDelta": candidate_abs,
                "candidateResidual": sum(candidate_abs),
                "worsened": sum(candidate_abs) > sum(current_abs),
                "candidateWorsensCurrent": sum(candidate_abs) > sum(current_abs),
            }
        )
    return samples


def summarize(samples: list[dict[str, Any]]) -> dict[str, Any]:
    current = sum(sample["currentResidual"] for sample in samples)
    candidate = sum(sample["candidateResidual"] for sample in samples)
    return {
        "sampleCount": len(samples),
        "coveredSampleCount": sum(1 for sample in samples if sample["covered"]),
        "currentResidual": current,
        "candidateResidual": candidate,
        "residualDelta": candidate - current,
        "candidateMinusCurrentResidual": candidate - current,
        "worsenedSampleCount": sum(1 for sample in samples if sample["worsened"]),
        "for355RuleAppliedSampleCount": sum(1 for sample in samples if sample["for355RuleApplies"]),
    }


def build_artifact(
    for360: dict[str, Any],
    for359: dict[str, Any],
    for358: dict[str, Any],
    for355: dict[str, Any],
    reference: dict[str, Any],
    provenance: dict[str, Any],
    current_capture: dict[str, Any],
) -> dict[str, Any]:
    source_runner = validate_source_guardrails()
    candidate355 = validate_prerequisite(for355, "FOR-355", FOR355_REQUIRED_DECISION)
    validate_prerequisite(for358, "FOR-358", FOR358_REQUIRED_DECISION)
    validate_prerequisite(for359, "FOR-359", FOR359_REQUIRED_DECISION)
    validate_prerequisite(for360, "FOR-360", FOR360_REQUIRED_DECISION)
    for source, linear in ((for355, "FOR-355"), (for358, "FOR-358"), (for359, "FOR-359"), (for360, "FOR-360")):
        validate_implementation_guard(source, linear)
    validate_reference(reference, provenance)
    validate_current_capture(current_capture)

    samples = build_samples(reference, current_capture)
    residuals = summarize(samples)
    require(residuals["for355RuleAppliedSampleCount"] >= 1, "expected at least one FOR-355 rule-applied sample")
    decision = DECISION_REJECTS if residuals["worsenedSampleCount"] > 0 else DECISION_SUPPORTS
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": SOURCE_FINDINGS,
        "decision": decision,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-361 creates a bounded independent Rec.2020 kRGBA_F16Norm drawArc fixture outside "
            "the FOR-340/FOR-341 CircularArcsStrokeButt adjacent groups. The exact FOR-355 candidate "
            "worsens the independent stroke-center sample versus the captured Skia over-white reference, "
            "so this bounded independent arc capture rejects the candidate."
        ),
        "inputValidation": {
            "for360Artifact": rel(FOR360_ARTIFACT),
            "for360Decision": for360.get("decision"),
            "for360RequiredDecision": FOR360_REQUIRED_DECISION,
            "for359Artifact": rel(FOR359_ARTIFACT),
            "for359Decision": for359.get("decision"),
            "for359RequiredDecision": FOR359_REQUIRED_DECISION,
            "for358Artifact": rel(FOR358_ARTIFACT),
            "for358Decision": for358.get("decision"),
            "for358RequiredDecision": FOR358_REQUIRED_DECISION,
            "for355Artifact": rel(FOR355_ARTIFACT),
            "for355Decision": for355.get("decision"),
            "for355RequiredDecision": FOR355_REQUIRED_DECISION,
        },
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "family": CANDIDATE_FAMILY_ID,
            "formula": candidate355.get("formula"),
            "reusedFromFor355": True,
            "reusedExactly": True,
            "newCandidateDefined": False,
            "selectedForImplementation": False,
            "rendererBehaviorChanged": False,
            "scoreIncreaseAuthorized": False,
        },
        "row": {
            "rowId": "for361-bounded-independent-round-cap-arc-rec2020-f16",
            "family": "bounded-independent-drawArc-round-cap",
            "sceneId": SCENE_ID,
            "sourceKind": "isolated-skia-current-kanvas-candidate",
            "arcScene": True,
            "independentFromFor340For341AdjacentGroups": True,
            "excludedScene": "circular_arcs_stroke_butt_adjacent_groups",
            "selectedCellSubstitutionUsed": False,
            "fullGmCrop": False,
            "referenceCurrentCandidateComparable": True,
            "reference": {
                "status": "isolated-skia-bounded-independent-arc-rec2020-f16-reference-available",
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
            "candidateEvaluation": {
                "policyId": CANDIDATE_POLICY_ID,
                "status": "computed-policy-samples-available-not-applied",
                "for355RuleAppliedSampleCount": residuals["for355RuleAppliedSampleCount"],
            },
            "samples": samples,
            "residuals": residuals,
        },
        "scene": {
            "dimensions": {"width": 64, "height": 64},
            "colorType": "kRGBA_F16Norm",
            "colorSpace": "Rec.2020",
            "draw": {
                "op": "drawArc",
                "ovalXYWH": [12, 12, 40, 40],
                "startAngleDeg": 0,
                "sweepAngleDeg": 120,
                "useCenter": False,
                "antiAlias": False,
                "style": "kStroke_Style",
                "strokeWidth": 8,
                "strokeCap": "kRound_Cap",
                "blendMode": "kSrcOver",
                "paintArgb": [100, 0, 0, 255],
            },
            "independentFromFor340For341AdjacentGroups": True,
            "fullGmCrop": False,
            "selectedCellSubstitutionUsed": False,
            "fixtureOrCoordinateRendererBranchUsed": False,
        },
        "referenceSource": source_runner,
        "implementation": {
            "rendererBehaviorChanged": False,
            "evidenceOnly": True,
            "newColorPolicyImplemented": False,
            "candidateSelectedForImplementation": False,
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
        },
        "nonGoalsPreserved": {
            "rendererBehaviorChange": True,
            "candidateSelectionOrImplementation": True,
            "scoreIncrease": True,
            "selectedCellSubstitutionRefused": True,
            "fixtureBranchRefused": True,
            "coordinateBranchRefused": True,
            "fullGmCropRefused": True,
            "thresholdRelaxationRefused": True,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def build_partial_artifact(
    for360: dict[str, Any],
    for359: dict[str, Any],
    for358: dict[str, Any],
    for355: dict[str, Any],
    missing_paths: list[Path],
) -> dict[str, Any]:
    source_runner = validate_source_guardrails()
    candidate355 = validate_prerequisite(for355, "FOR-355", FOR355_REQUIRED_DECISION)
    validate_prerequisite(for358, "FOR-358", FOR358_REQUIRED_DECISION)
    validate_prerequisite(for359, "FOR-359", FOR359_REQUIRED_DECISION)
    validate_prerequisite(for360, "FOR-360", FOR360_REQUIRED_DECISION)
    for source, linear in ((for355, "FOR-355"), (for358, "FOR-358"), (for359, "FOR-359"), (for360, "FOR-360")):
        validate_implementation_guard(source, linear)

    missing = [rel(path) for path in missing_paths]
    reason = (
        "FOR-361 could not evaluate the bounded independent arc row because real capture outputs "
        f"are missing: {', '.join(missing)}. The validator records PARTIAL with available artifacts "
        "and does not fabricate sample RGBA values or residual totals."
    )
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": SOURCE_FINDINGS,
        "decision": DECISION_PARTIAL,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": reason,
        "inputValidation": {
            "for360Artifact": rel(FOR360_ARTIFACT),
            "for360Decision": for360.get("decision"),
            "for360RequiredDecision": FOR360_REQUIRED_DECISION,
            "for359Artifact": rel(FOR359_ARTIFACT),
            "for359Decision": for359.get("decision"),
            "for359RequiredDecision": FOR359_REQUIRED_DECISION,
            "for358Artifact": rel(FOR358_ARTIFACT),
            "for358Decision": for358.get("decision"),
            "for358RequiredDecision": FOR358_REQUIRED_DECISION,
            "for355Artifact": rel(FOR355_ARTIFACT),
            "for355Decision": for355.get("decision"),
            "for355RequiredDecision": FOR355_REQUIRED_DECISION,
        },
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "family": CANDIDATE_FAMILY_ID,
            "formula": candidate355.get("formula"),
            "reusedFromFor355": True,
            "reusedExactly": True,
            "newCandidateDefined": False,
            "selectedForImplementation": False,
            "rendererBehaviorChanged": False,
            "scoreIncreaseAuthorized": False,
        },
        "captureStatus": {
            "referenceCaptureCommand": REFERENCE_CAPTURE_COMMAND,
            "currentCaptureCommand": CURRENT_CAPTURE_COMMAND,
            "missingArtifacts": missing,
            "availableArtifacts": [
                artifact_status(SKIA_REFERENCE_JSON),
                artifact_status(SKIA_REFERENCE_PROVENANCE),
                artifact_status(SKIA_REFERENCE_PNG),
                artifact_status(CURRENT_KANVAS_JSON),
            ],
            "exactReason": reason,
        },
        "candidateEvaluation": {
            "evaluatedOnIndependentArcScene": False,
            "status": "not-evaluated-missing-real-capture-output",
            "sampleCount": 0,
            "samples": [],
            "totals": {
                "currentResidual": None,
                "candidateResidual": None,
                "residualDelta": None,
                "candidateWorsenedSampleCount": None,
                "for355RuleAppliedSampleCount": None,
            },
            "exactReason": reason,
        },
        "referenceSource": source_runner,
        "implementation": {
            "rendererBehaviorChanged": False,
            "evidenceOnly": True,
            "newColorPolicyImplemented": False,
            "candidateSelectedForImplementation": False,
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
        },
        "nonGoalsPreserved": {
            "rendererBehaviorChange": True,
            "candidateSelectionOrImplementation": True,
            "scoreIncrease": True,
            "selectedCellSubstitutionRefused": True,
            "fixtureBranchRefused": True,
            "coordinateBranchRefused": True,
            "fullGmCropRefused": True,
            "thresholdRelaxationRefused": True,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "artifact source memory changed")
    require(data.get("sourceFindings") == SOURCE_FINDINGS, "artifact source findings changed")
    require(data.get("decision") == DECISION_REJECTS, "FOR-361 decision should reject this candidate")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")
    candidate = data.get("candidate")
    require(isinstance(candidate, dict), "candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "candidate policy changed")
    require(candidate.get("reusedExactly") is True, "candidate not reused exactly")
    require(candidate.get("newCandidateDefined") is False, "new candidate defined")
    require(candidate.get("selectedForImplementation") is False, "candidate selected")

    row = data.get("row")
    require(isinstance(row, dict), "row missing")
    require(row.get("arcScene") is True, "row must be an arc scene")
    require(row.get("independentFromFor340For341AdjacentGroups") is True, "row independence missing")
    require(row.get("selectedCellSubstitutionUsed") is False, "selected-cell substitution used")
    require(row.get("fullGmCrop") is False, "full-GM crop used")
    require(row.get("referenceCurrentCandidateComparable") is True, "row must be comparable")
    samples = row.get("samples")
    require(isinstance(samples, list) and len(samples) >= 2, "expected at least two samples")
    require(any(sample.get("for355RuleApplies") is True for sample in samples), "FOR-355 rule never applied")
    require(any(sample.get("worsened") is True for sample in samples), "FOR-361 should expose a worsened sample")
    for sample in samples:
        for key in ("rawReferenceRgba", "referenceSrgbRgba", "currentKanvasSrgbRgba", "candidateRgba", "candidatePolicyRgba"):
            rgba(sample.get(key), f"{sample.get('name')} {key}")
        require(sample.get("candidatePolicyId") == CANDIDATE_POLICY_ID, f"{sample.get('name')} candidate policy changed")
        require(isinstance(sample.get("currentResidual"), int), f"{sample.get('name')} current residual missing")
        require(isinstance(sample.get("candidateResidual"), int), f"{sample.get('name')} candidate residual missing")
        require(isinstance(sample.get("worsened"), bool), f"{sample.get('name')} worsened flag missing")

    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "residuals missing")
    require(residuals.get("sampleCount") == 4, "sample count changed")
    require(residuals.get("coveredSampleCount") == 2, "covered sample count changed")
    require(residuals.get("currentResidual") == 0, "current residual changed")
    require(residuals.get("candidateResidual") == 37, "candidate residual changed")
    require(residuals.get("residualDelta") == 37, "candidate residual delta changed")
    require(residuals.get("worsenedSampleCount") == 1, "worsened sample count changed")
    require(residuals.get("for355RuleAppliedSampleCount") == 1, "FOR-355 rule-applied sample count changed")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation missing")
    for key in (
        "rendererBehaviorChanged",
        "newColorPolicyImplemented",
        "candidateSelectedForImplementation",
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
    ):
        require(implementation.get(key) is False, f"implementation guard changed: {key}")


def validate_partial_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "partial artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "partial artifact scene id changed")
    require(data.get("decision") == DECISION_PARTIAL, "partial decision missing")
    candidate = data.get("candidate")
    require(isinstance(candidate, dict), "partial candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "partial candidate policy changed")
    require(candidate.get("reusedExactly") is True, "partial candidate not reused exactly")
    capture = data.get("captureStatus")
    require(isinstance(capture, dict), "partial capture status missing")
    missing = capture.get("missingArtifacts")
    require(isinstance(missing, list) and missing, "partial missing artifact list missing")
    evaluation = data.get("candidateEvaluation")
    require(isinstance(evaluation, dict), "partial candidate evaluation missing")
    require(evaluation.get("evaluatedOnIndependentArcScene") is False, "partial evaluation should not be evaluated")
    require(evaluation.get("sampleCount") == 0, "partial samples fabricated")
    require(evaluation.get("samples") == [], "partial sample values fabricated")
    totals = evaluation.get("totals")
    require(isinstance(totals, dict), "partial totals missing")
    for key in (
        "currentResidual",
        "candidateResidual",
        "residualDelta",
        "candidateWorsenedSampleCount",
        "for355RuleAppliedSampleCount",
    ):
        require(totals.get(key) is None, f"partial {key} should be null")
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "partial implementation missing")
    require(implementation.get("rendererBehaviorChanged") is False, "partial renderer behavior changed")
    require(implementation.get("scoreChanged") is False, "partial score changed")


def build_report(data: dict[str, Any]) -> str:
    if data["decision"] == DECISION_PARTIAL:
        return build_partial_report(data)

    row = data["row"]
    sample_rows = "\n".join(
        "| {name} | {xy} | {zone} | {raw} | {ref} | {current} | {candidate} | {rule} | {current_residual} | {candidate_residual} | {worsened} |".format(
            name=sample["name"],
            xy=f"{sample['x']},{sample['y']}",
            zone=sample["zone"],
            raw=json.dumps(sample["rawReferenceRgba"]),
            ref=json.dumps(sample["referenceSrgbRgba"]),
            current=json.dumps(sample["currentKanvasSrgbRgba"]),
            candidate=json.dumps(sample["candidateRgba"]),
            rule="yes" if sample["for355RuleApplies"] else "no",
            current_residual=sample["currentResidual"],
            candidate_residual=sample["candidateResidual"],
            worsened="yes" if sample["worsened"] else "no",
        )
        for sample in row["samples"]
    )
    residuals = row["residuals"]
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-361 F16 Bounded Independent Arc Capture

Linear: `FOR-361`

Decision: `{data["decision"]}`

Candidate: `{CANDIDATE_POLICY_ID}`

FOR-361 creates a bounded independent Rec.2020 `kRGBA_F16Norm` `drawArc`
fixture outside the FOR-340/FOR-341 adjacent `CircularArcsStrokeButt` groups.
It reuses the FOR-355 candidate exactly and keeps the work evidence-only.

## Result

The current Kanvas capture matches the Skia over-white reference on this
bounded arc row. The FOR-355 candidate applies only to the `stroke-center`
sample with raw alpha `100`; there it computes `[155, 155, 255, 255]`, while
the captured Skia over-white reference is `[180, 167, 255, 255]`. This creates
a candidate residual of `37`, so the independent arc capture rejects the
candidate.

## Samples

| sample | x,y | zone | raw reference | reference | current | candidate | FOR-355 rule applies | current residual | candidate residual | worsened |
|---|---|---|---|---|---|---|---:|---:|---:|---|
{sample_rows}

## Aggregate Residuals

| metric | value |
|---|---:|
| samples | {residuals["sampleCount"]} |
| covered samples | {residuals["coveredSampleCount"]} |
| current residual | {residuals["currentResidual"]} |
| candidate residual | {residuals["candidateResidual"]} |
| residual delta | {residuals["residualDelta"]} |
| worsened samples | {residuals["worsenedSampleCount"]} |
| FOR-355 rule-applied samples | {residuals["for355RuleAppliedSampleCount"]} |

## Independence Boundary

- Bounded micro-fixture: `drawArc` oval `[12, 12, 40, 40]`, start `0`, sweep
  `120`, `kRound_Cap`.
- Selected-cell substitution used: `False`.
- Full-GM crop used: `False`.
- FOR-340/FOR-341 adjacent groups reused as proof: `False`.
- Renderer behavior changed: `False`.

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No candidate selected for implementation.
- No implementation plan authorization.
- No score increase.
- No threshold, GPU/WGSL, geometry, coverage, fallback, promotion, score, or
  Kadre change.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Skia samples: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/skia-reference-samples.json`
- Kanvas samples: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/current-kanvas-samples.json`
- Skia PNG: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/skia-reference.png`
- Validator: `scripts/validate_for361_f16_bounded_independent_arc_capture.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-361-f16-bounded-independent-arc-capture.md`

## Validation

{validation}
"""


def build_partial_report(data: dict[str, Any]) -> str:
    artifacts = "\n".join(
        "| `{path}` | `{exists}` | `{sha}` |".format(
            path=item["path"],
            exists=item["exists"],
            sha=item.get("sha256", ""),
        )
        for item in data["captureStatus"]["availableArtifacts"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-361 F16 Bounded Independent Arc Capture

Linear: `FOR-361`

Decision: `{data["decision"]}`

Candidate: `{data["candidate"]["policyId"]}`

FOR-361 attempted to create a bounded independent Rec.2020 `kRGBA_F16Norm`
`drawArc` fixture outside the FOR-340/FOR-341 adjacent
`CircularArcsStrokeButt` groups, while reusing the exact FOR-355 candidate.

## Result

{data["decisionReason"]}

No sample RGBA values or residual totals are fabricated in this partial path.

## Capture Status

| artifact | exists | sha256 |
|---|---:|---|
{artifacts}

## Candidate Evaluation

| metric | value |
|---|---:|
| evaluated on independent arc scene | `{data["candidateEvaluation"]["evaluatedOnIndependentArcScene"]}` |
| sample count | `{data["candidateEvaluation"]["sampleCount"]}` |
| current residual | `{data["candidateEvaluation"]["totals"]["currentResidual"]}` |
| candidate residual | `{data["candidateEvaluation"]["totals"]["candidateResidual"]}` |
| residual delta | `{data["candidateEvaluation"]["totals"]["residualDelta"]}` |
| worsened samples | `{data["candidateEvaluation"]["totals"]["candidateWorsenedSampleCount"]}` |

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No candidate selected for implementation.
- No score increase.
- No threshold, GPU/WGSL, geometry, coverage, fallback, promotion, score, or
  Kadre change.

## Validation

{validation}
"""


def main() -> None:
    for360 = load_json(FOR360_ARTIFACT)
    for359 = load_json(FOR359_ARTIFACT)
    for358 = load_json(FOR358_ARTIFACT)
    for355 = load_json(FOR355_ARTIFACT)
    capture_inputs = [SKIA_REFERENCE_JSON, SKIA_REFERENCE_PROVENANCE, SKIA_REFERENCE_PNG, CURRENT_KANVAS_JSON]
    missing = [path for path in capture_inputs if not path.is_file()]
    if missing:
        data = build_partial_artifact(for360, for359, for358, for355, missing)
        validate_partial_artifact(data)
    else:
        data = build_artifact(
            for360,
            for359,
            for358,
            for355,
            load_json(SKIA_REFERENCE_JSON),
            load_json(SKIA_REFERENCE_PROVENANCE),
            load_json(CURRENT_KANVAS_JSON),
        )
        validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    if data["decision"] == DECISION_PARTIAL:
        print(f"{data['decision']}: missing={','.join(data['captureStatus']['missingArtifacts'])}")
    else:
        print(
            f"{data['decision']}: current={data['row']['residuals']['currentResidual']} "
            f"candidate={data['row']['residuals']['candidateResidual']}"
        )


if __name__ == "__main__":
    main()
