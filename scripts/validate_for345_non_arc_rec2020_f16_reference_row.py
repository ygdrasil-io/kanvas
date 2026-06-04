#!/usr/bin/env python3
"""Validate the FOR-345 non-arc Rec.2020 F16 reference/current/candidate row."""

from __future__ import annotations

import hashlib
import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-345"
SCENE_ID = "non-arc-rec2020-f16-reference-row-for345"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-345-non-arc-rec2020-f16-reference-row.md"
SKIA_REFERENCE_JSON = ARTIFACT_DIR / "skia-reference-samples.json"
SKIA_REFERENCE_PNG = ARTIFACT_DIR / "skia-reference.png"
SKIA_REFERENCE_PROVENANCE = ARTIFACT_DIR / "skia-reference-provenance.json"
CURRENT_KANVAS_JSON = ARTIFACT_DIR / "current-kanvas-samples.json"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-non-arc-rec2020-f16-comparable-reference-row-ticket"
)
SOURCE_FINDING = "global/kanvas/findings/for-344-broader-non-arc-f16-color-evidence-partial-finding"

FOR344_SCENE_ID = "f16-broader-non-arc-color-policy-for344"
FOR344_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR344_SCENE_ID / f"{FOR344_SCENE_ID}.json"
)
FOR344_REQUIRED_DECISION = "F16_BROADER_NON_ARC_EVIDENCE_PARTIAL_REQUIRES_MORE_REFERENCE_ROWS"

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"
SOURCE = PROJECT_ROOT / "tools/skia-reference/non_arc_rec2020_f16_reference.cpp"
RUNNER = PROJECT_ROOT / "tools/skia-reference/build_for345_non_arc_rec2020_f16_reference.py"
CURRENT_CAPTURE_TEST = (
    PROJECT_ROOT
    / "skia-integration-tests/src/test/kotlin/org/skia/tests/For345NonArcRec2020F16CurrentCaptureTest.kt"
)

DECISION_CAPTURED = "F16_NON_ARC_REC2020_REFERENCE_ROW_CAPTURED"
DECISION_PARTIAL = "F16_NON_ARC_REC2020_REFERENCE_ROW_PARTIAL_REQUIRES_SKIA_REFERENCE_SOURCE"
DECISION_REJECTS = "F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE"
DECISION_INPUT_INVALID = "F16_NON_ARC_REC2020_REFERENCE_ROW_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_CAPTURED, DECISION_PARTIAL, DECISION_REJECTS, DECISION_INPUT_INVALID]

CANDIDATE_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"

REFERENCE_CAPTURE_COMMAND = "rtk python3 tools/skia-reference/build_for345_non_arc_rec2020_f16_reference.py"
CURRENT_CAPTURE_COMMAND = (
    f"KANVAS_FOR345_CURRENT_CAPTURE_OUTPUT={CURRENT_KANVAS_JSON.relative_to(PROJECT_ROOT)} "
    "rtk ./gradlew --no-daemon --rerun-tasks "
    ":skia-integration-tests:test --tests org.skia.tests.For345NonArcRec2020F16CurrentCaptureTest"
)

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    REFERENCE_CAPTURE_COMMAND,
    CURRENT_CAPTURE_COMMAND,
    "rtk python3 scripts/validate_for344_f16_broader_non_arc_color_policy_evidence.py",
    "rtk python3 scripts/validate_for343_f16_color_policy_boundary.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    "rtk python3 -m py_compile scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
]

SAMPLE_METADATA = [
    {
        "name": "background_top_left",
        "zone": "background",
        "x": 0,
        "y": 0,
        "insideDraw": False,
        "coverageScale": 0.0,
    },
    {
        "name": "rect_center",
        "zone": "fill-center",
        "x": 16,
        "y": 16,
        "insideDraw": True,
        "coverageScale": 1.0,
    },
    {
        "name": "rect_left_inside",
        "zone": "fill-left-inside",
        "x": 8,
        "y": 16,
        "insideDraw": True,
        "coverageScale": 1.0,
    },
    {
        "name": "rect_right_inside",
        "zone": "fill-right-inside",
        "x": 23,
        "y": 16,
        "insideDraw": True,
        "coverageScale": 1.0,
    },
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-345 validation failed: {message}")


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


def validate_source_guardrails() -> None:
    required = {
        CURRENT_CAPTURE_TEST: [
            "kanvas.for345.currentCapture.output",
            "For345NonArcRec2020F16CurrentCaptureTest",
            "SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)",
            "SkBitmap(32, 32, rec2020, SkColorType.kRGBA_F16Norm)",
            "bitmap.eraseColor(SK_ColorWHITE)",
            "SkCanvas(bitmap).drawRect(SkRect.MakeXYWH(8f, 8f, 16f, 16f), paint)",
            "bitmap.getPixelAsSrgb(sample.x, sample.y)",
            '"excludedScene": "circular_arcs_stroke_butt"',
        ],
        SK_BITMAP_DEVICE: [
            "private fun colorToF16Premul(c: SkColor4f, out: FloatArray)",
            "private fun blendF16PremulMode(",
            "blendF16PremulMode(x, y, sr * cov, sg * cov, sb * cov, saCov, paint.blendMode)",
        ],
        SK_BITMAP: [
            "public fun getPixelAsSrgb",
            "getPixel] preserves the historical internal byte oracle",
            "f16PremulToSrgbUnpremul",
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


def validate_for344(for344: dict[str, Any]) -> None:
    require(for344.get("linear") == "FOR-344", "FOR-344 artifact identity changed")
    require(for344.get("decision") == FOR344_REQUIRED_DECISION, "FOR-344 required partial decision is missing")
    gap = for344.get("referenceGapClassification")
    require(isinstance(gap, dict), "FOR-344 referenceGapClassification missing")
    require(
        gap.get("stableReasonCode") == "NON_ARC_REC2020_F16_REFERENCE_CURRENT_CANDIDATE_ROWS_MISSING",
        "FOR-344 gap reason changed",
    )
    require(gap.get("requiresMoreReferenceRows") is True, "FOR-344 no longer requires reference rows")
    require(
        for344.get("boundary", {}).get("candidatePolicyId") == CANDIDATE_POLICY_ID,
        "FOR-344 candidate policy changed",
    )
    require(for344.get("boundary", {}).get("rendererBehaviorChanged") is False, "FOR-344 changed renderer behavior")


def validate_source_and_runner() -> dict[str, Any]:
    require(SOURCE.is_file(), f"missing source: {rel(SOURCE)}")
    require(RUNNER.is_file(), f"missing runner: {rel(RUNNER)}")
    source_text = SOURCE.read_text(encoding="utf-8")
    runner_text = RUNNER.read_text(encoding="utf-8")
    for needle in [
        "SkColorSpace::MakeRGB(SkNamedTransferFn::kRec2020, SkNamedGamut::kRec2020)",
        "kRGBA_F16Norm_SkColorType",
        "canvas->clear(SK_ColorWHITE)",
        "blue.setBlendMode(SkBlendMode::kSrcOver)",
        "SkColorSetARGB(100, 0, 0, 255)",
        "canvas->drawRect(SkRect::MakeXYWH(8, 8, 16, 16), blue)",
        "\\\"nonArc\\\": true",
        "\\\"excludedScene\\\": \\\"circular_arcs_stroke_butt\\\"",
    ]:
        require(needle in source_text, f"source missing `{needle}`")
    for forbidden in ["drawArc", "CircularArcsStrokeButt", "fullGmCrop\": true"]:
        require(forbidden not in source_text, f"source uses forbidden term `{forbidden}`")
    for needle in [
        "non_arc_rec2020_f16_reference.cpp",
        "libskia.a",
        "F16_NON_ARC_REC2020_REFERENCE_ROW_SKIA_REFERENCE_CAPTURED",
        "selectedCellSubstitutionAccepted",
    ]:
        require(needle in runner_text, f"runner missing `{needle}`")
    return {
        "source": {"path": rel(SOURCE), "sha256": sha256(SOURCE), "present": True},
        "runner": {"path": rel(RUNNER), "present": True, "headless": True},
        "currentCaptureTest": {"path": rel(CURRENT_CAPTURE_TEST), "present": True, "headless": True},
    }


def validate_reference_capture(reference: dict[str, Any], provenance: dict[str, Any]) -> None:
    require(reference.get("sceneId") == SCENE_ID, "Skia reference scene id changed")
    require(reference.get("sourceType") == "isolated-skia-non-arc-rec2020-f16-src-over-rect", "Skia source type changed")
    require(reference.get("dimensions") == {"width": 32, "height": 32}, "Skia reference dimensions changed")
    require(reference.get("colorType") == "kRGBA_F16Norm", "Skia reference color type changed")
    require(reference.get("colorSpace") == "Rec.2020", "Skia reference color space changed")
    require(reference.get("blendMode") == "kSrcOver", "Skia reference blend mode changed")
    require(reference.get("nonArc") is True, "Skia reference must be non-arc")
    require(reference.get("excludedScene") == "circular_arcs_stroke_butt", "Skia reference exclusion changed")
    require(provenance.get("linear") == LINEAR_ID, "Skia provenance linear id changed")
    require(provenance.get("sceneId") == SCENE_ID, "Skia provenance scene id changed")
    require(provenance.get("referenceJsonSha256") == sha256(SKIA_REFERENCE_JSON), "Skia reference JSON sha changed")
    require(provenance.get("referencePngSha256") == sha256(SKIA_REFERENCE_PNG), "Skia reference PNG sha changed")
    proof = provenance.get("sourceTypeProof")
    require(isinstance(proof, dict), "Skia provenance proof missing")
    require(proof.get("compiledRepoOwnedSource") is True, "Skia source compile proof missing")
    require(proof.get("executedBinary") is True, "Skia execution proof missing")
    require(proof.get("isolatedNonArcOutput") is True, "Skia isolated non-arc proof missing")
    require(provenance.get("fullGmCrop") is False, "Skia provenance uses full GM crop")
    require(provenance.get("selectedCellSubstitutionAccepted") is False, "Skia provenance accepts selected-cell substitution")


def validate_current_capture(current: dict[str, Any]) -> None:
    require(current.get("linear") == LINEAR_ID, "current capture linear id changed")
    require(current.get("sceneId") == SCENE_ID, "current capture scene id changed")
    require(current.get("sourceType") == "current-kanvas-non-arc-rec2020-f16-src-over-rect", "current source type changed")
    require(current.get("dimensions") == {"width": 32, "height": 32}, "current dimensions changed")
    require(current.get("colorType") == "kRGBA_F16Norm", "current color type changed")
    require(current.get("colorSpace") == "Rec.2020", "current color space changed")
    require(current.get("blendMode") == "kSrcOver", "current blend mode changed")
    require(current.get("nonArc") is True, "current capture must be non-arc")
    require(current.get("excludedScene") == "circular_arcs_stroke_butt", "current exclusion changed")


def keyed_samples(data: dict[str, Any], rgba_key: str) -> dict[str, dict[str, Any]]:
    raw_samples = data.get("samples")
    require(isinstance(raw_samples, list), f"{rgba_key} samples missing")
    result: dict[str, dict[str, Any]] = {}
    for sample in raw_samples:
        require(isinstance(sample, dict), f"{rgba_key} sample must be object")
        name = sample.get("name")
        require(isinstance(name, str) and name, f"{rgba_key} sample name missing")
        require(name not in result, f"duplicate sample {name}")
        require(isinstance(sample.get(rgba_key), list) and len(sample[rgba_key]) == 4, f"{name} {rgba_key} missing")
        result[name] = sample
    return result


def quantize_256(x: float) -> int:
    return max(0, min(255, int(math.floor(x * 256.0))))


def quantize_alpha_round(x: float) -> int:
    if math.isnan(x):
        return 0
    return max(0, min(255, int(x * 255.0 + 0.5)))


def candidate_srgb_src_over_white(sample: dict[str, Any]) -> list[int]:
    if not sample["insideDraw"]:
        return [255, 255, 255, 255]
    paint = [0, 0, 255, 100]
    alpha = quantize_alpha_round((paint[3] / 255.0) * float(sample["coverageScale"])) / 255.0
    return [
        quantize_256((paint[0] / 255.0) * alpha + (1.0 - alpha)),
        quantize_256((paint[1] / 255.0) * alpha + (1.0 - alpha)),
        quantize_256((paint[2] / 255.0) * alpha + (1.0 - alpha)),
        255,
    ]


def abs_delta(a: list[int], b: list[int]) -> list[int]:
    return [abs(a[i] - b[i]) for i in range(4)]


def signed_delta(a: list[int], b: list[int]) -> list[int]:
    return [a[i] - b[i] for i in range(4)]


def build_samples(reference: dict[str, Any], current_capture: dict[str, Any]) -> list[dict[str, Any]]:
    reference_samples = keyed_samples(reference, "referenceSrgbRgba")
    current_samples = keyed_samples(current_capture, "currentKanvasSrgbRgba")
    samples: list[dict[str, Any]] = []
    for metadata in SAMPLE_METADATA:
        name = metadata["name"]
        require(name in reference_samples, f"Skia reference missing sample {name}")
        require(name in current_samples, f"Kanvas current capture missing sample {name}")
        ref_sample = reference_samples[name]
        cur_sample = current_samples[name]
        require(ref_sample.get("x") == metadata["x"] and ref_sample.get("y") == metadata["y"], f"Skia sample coords changed: {name}")
        require(cur_sample.get("x") == metadata["x"] and cur_sample.get("y") == metadata["y"], f"Kanvas sample coords changed: {name}")
        reference_rgba = ref_sample["referenceSrgbRgba"]
        current_rgba = cur_sample["currentKanvasSrgbRgba"]
        candidate = candidate_srgb_src_over_white(metadata)
        current_abs = abs_delta(current_rgba, reference_rgba)
        candidate_abs = abs_delta(candidate, reference_rgba)
        samples.append(
            {
                "name": metadata["name"],
                "zone": metadata["zone"],
                "x": metadata["x"],
                "y": metadata["y"],
                "insideDraw": metadata["insideDraw"],
                "coverageScale": metadata["coverageScale"],
                "paintSourceRgba": [0, 0, 255, 100] if metadata["insideDraw"] else None,
                "referenceSrgbRgba": reference_rgba,
                "currentKanvasSrgbRgba": current_rgba,
                "candidatePolicyId": CANDIDATE_POLICY_ID,
                "candidatePolicyRgba": candidate,
                "currentVsReferenceSignedDelta": signed_delta(current_rgba, reference_rgba),
                "currentVsReferenceAbsDelta": current_abs,
                "currentVsReferenceSumAbsDelta": sum(current_abs),
                "candidateVsReferenceSignedDelta": signed_delta(candidate, reference_rgba),
                "candidateVsReferenceAbsDelta": candidate_abs,
                "candidateVsReferenceSumAbsDelta": sum(candidate_abs),
                "candidateWorsensCurrent": sum(candidate_abs) > sum(current_abs),
            }
        )
    return samples


def summarize(samples: list[dict[str, Any]]) -> dict[str, Any]:
    current = sum(sample["currentVsReferenceSumAbsDelta"] for sample in samples)
    candidate = sum(sample["candidateVsReferenceSumAbsDelta"] for sample in samples)
    worsened = sum(1 for sample in samples if sample["candidateWorsensCurrent"])
    improved = sum(
        1
        for sample in samples
        if sample["candidateVsReferenceSumAbsDelta"] < sample["currentVsReferenceSumAbsDelta"]
    )
    unchanged = sum(
        1
        for sample in samples
        if sample["candidateVsReferenceSumAbsDelta"] == sample["currentVsReferenceSumAbsDelta"]
    )
    covered = [sample for sample in samples if sample["insideDraw"]]
    return {
        "sampleCount": len(samples),
        "coveredSampleCount": len(covered),
        "currentResidual": current,
        "candidateResidual": candidate,
        "candidateMinusCurrentResidual": candidate - current,
        "residualReduction": current - candidate,
        "worsenedSampleCount": worsened,
        "improvedSampleCount": improved,
        "unchangedSampleCount": unchanged,
        "maxCurrentSampleResidual": max(sample["currentVsReferenceSumAbsDelta"] for sample in samples),
        "maxCandidateSampleResidual": max(sample["candidateVsReferenceSumAbsDelta"] for sample in samples),
    }


def build_artifact(
    for344: dict[str, Any],
    reference: dict[str, Any],
    provenance: dict[str, Any],
    current_capture: dict[str, Any],
) -> dict[str, Any]:
    source_runner = validate_source_and_runner()
    validate_reference_capture(reference, provenance)
    validate_current_capture(current_capture)
    samples = build_samples(reference, current_capture)
    residuals = summarize(samples)
    decision = DECISION_REJECTS if residuals["worsenedSampleCount"] > 0 else DECISION_CAPTURED
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for344Artifact": rel(FOR344_ARTIFACT),
            "for344Decision": for344.get("decision"),
            "for344RequiredDecision": FOR344_REQUIRED_DECISION,
            "for344RequiresMoreReferenceRows": for344.get("decision") == FOR344_REQUIRED_DECISION,
            "for344CandidatePolicyId": for344.get("boundary", {}).get("candidatePolicyId"),
        },
        "decision": decision,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-345 captured a genuine non-arc Rec.2020 kRGBA_F16Norm SrcOver row. "
            "The current Kanvas CPU F16 samples match the isolated Skia reference on the selected pixels, "
            "while the straight_srgb_quantized_alpha_src_over_white candidate worsens all covered rect samples."
        ),
        "row": {
            "rowId": "non-arc-rec2020-f16-src-over-rect",
            "family": "non-arc-solid-rect",
            "sceneId": SCENE_ID,
            "sourceKind": "isolated-skia-current-kanvas-candidate",
            "nonArc": True,
            "excludedScene": "circular_arcs_stroke_butt",
            "rec2020F16SrcOverOrBlendSignal": True,
            "referenceCurrentCandidateComparable": True,
            "reference": {
                "status": "isolated-skia-non-arc-rec2020-f16-src-over-reference-available",
                "captureCommand": REFERENCE_CAPTURE_COMMAND,
                "samplesPath": rel(SKIA_REFERENCE_JSON),
                "provenancePath": rel(SKIA_REFERENCE_PROVENANCE),
                "pngPath": rel(SKIA_REFERENCE_PNG),
                "referenceJsonSha256": sha256(SKIA_REFERENCE_JSON),
                "referencePngSha256": sha256(SKIA_REFERENCE_PNG),
            },
            "current": {
                "status": "current-kanvas-kotlin-cpu-rec2020-f16-src-over-samples-captured",
                "captureCommand": CURRENT_CAPTURE_COMMAND,
                "samplesPath": rel(CURRENT_KANVAS_JSON),
                "captureMethod": current_capture.get("captureMethod"),
            },
            "candidate": {
                "policyId": CANDIDATE_POLICY_ID,
                "status": "computed-policy-samples-available-not-applied",
                "formula": (
                    "source sRGB non-premul channels, "
                    "alpha=round((paintAlpha/255)*coverageScale*255)/255, "
                    "SrcOver over white, floor(channel*256)"
                ),
            },
            "samples": samples,
            "residuals": residuals,
        },
        "scene": {
            "dimensions": {"width": 32, "height": 32},
            "colorType": "kRGBA_F16Norm",
            "colorSpace": "Rec.2020",
            "backgroundRgba": [255, 255, 255, 255],
            "draw": {
                "op": "drawRect",
                "rectXYWH": [8, 8, 16, 16],
                "antiAlias": False,
                "blendMode": "kSrcOver",
                "paintArgb": [100, 0, 0, 255],
            },
            "nonArc": True,
            "fullGmCrop": False,
            "selectedCellSubstitutionUsed": False,
            "fixtureOrCoordinateRendererBranchUsed": False,
        },
        "referenceSource": source_runner,
        "referenceCapture": {
            "sourceType": reference.get("sourceType"),
            "provenance": rel(SKIA_REFERENCE_PROVENANCE),
        },
        "currentCapture": {
            "sourceType": current_capture.get("sourceType"),
            "samplesPath": rel(CURRENT_KANVAS_JSON),
        },
        "boundary": {
            "id": BOUNDARY_ID,
            "rendererBehaviorChanged": False,
            "globalF16RendererChangeAllowedNow": False,
            "candidatePolicyId": CANDIDATE_POLICY_ID,
        },
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


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "artifact source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "artifact source finding changed")
    require(data.get("decision") == DECISION_REJECTS, "FOR-345 should reject the candidate for this row")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")
    input_validation = data.get("inputValidation")
    require(isinstance(input_validation, dict), "inputValidation missing")
    require(input_validation.get("for344Decision") == FOR344_REQUIRED_DECISION, "FOR-344 gate not encoded")
    row = data.get("row")
    require(isinstance(row, dict), "row missing")
    require(row.get("nonArc") is True, "row must be non-arc")
    require(row.get("excludedScene") == "circular_arcs_stroke_butt", "row must explicitly exclude circular_arcs")
    require(row.get("rec2020F16SrcOverOrBlendSignal") is True, "row must be Rec.2020 F16 SrcOver/blend")
    require(row.get("referenceCurrentCandidateComparable") is True, "row must be comparable")
    samples = row.get("samples")
    require(isinstance(samples, list) and len(samples) == 4, "expected four aligned samples")
    for sample in samples:
        for key in ("referenceSrgbRgba", "currentKanvasSrgbRgba", "candidatePolicyRgba"):
            require(isinstance(sample.get(key), list) and len(sample[key]) == 4, f"{sample.get('name')} {key} missing")
        require(sample.get("candidatePolicyId") == CANDIDATE_POLICY_ID, f"{sample.get('name')} candidate policy changed")
        require(isinstance(sample.get("currentVsReferenceSumAbsDelta"), int), "current residual missing")
        require(isinstance(sample.get("candidateVsReferenceSumAbsDelta"), int), "candidate residual missing")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "residuals missing")
    require(residuals.get("sampleCount") == 4, "sample count changed")
    require(residuals.get("coveredSampleCount") == 3, "covered sample count changed")
    require(residuals.get("currentResidual") == 0, "current residual changed")
    require(residuals.get("candidateResidual") == 111, "candidate residual changed")
    require(residuals.get("worsenedSampleCount") == 3, "worsened sample count changed")
    require(data.get("boundary", {}).get("rendererBehaviorChanged") is False, "renderer behavior changed")
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation missing")
    for key in (
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


def fmt(value: Any) -> str:
    return "n/a" if value is None else str(value)


def build_report(data: dict[str, Any]) -> str:
    row = data["row"]
    sample_rows = "\n".join(
        "| {name} | {xy} | {zone} | {ref} | {current} | {candidate} | {current_residual} | {candidate_residual} | {worsened} |".format(
            name=sample["name"],
            xy=f"{sample['x']},{sample['y']}",
            zone=sample["zone"],
            ref=json.dumps(sample["referenceSrgbRgba"]),
            current=json.dumps(sample["currentKanvasSrgbRgba"]),
            candidate=json.dumps(sample["candidatePolicyRgba"]),
            current_residual=sample["currentVsReferenceSumAbsDelta"],
            candidate_residual=sample["candidateVsReferenceSumAbsDelta"],
            worsened="yes" if sample["candidateWorsensCurrent"] else "no",
        )
        for sample in row["samples"]
    )
    residuals = row["residuals"]
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-345 Non-Arc Rec.2020 F16 Reference Row

Linear: `FOR-345`

Decision: `{data["decision"]}`

FOR-345 creates one comparable non-arc Rec.2020 `kRGBA_F16Norm` `SrcOver`
reference/current/candidate row for `{CANDIDATE_POLICY_ID}`. It is evidence-only.

## Result

The row is a non-arc solid rect, explicitly outside `circular_arcs_stroke_butt`.
The isolated Skia reference and current Kanvas CPU F16 samples are aligned on
the same four pixels. Current Kanvas matches the reference on this row.

The candidate policy is not safe for this row: it worsens `{residuals["worsenedSampleCount"]}`
covered samples and raises residual from `{residuals["currentResidual"]}` to
`{residuals["candidateResidual"]}`.

## Samples

| sample | x,y | zone | reference | current | candidate | current residual | candidate residual | worsened |
|---|---|---|---|---|---|---:|---:|---|
{sample_rows}

## Aggregate Residuals

| metric | value |
|---|---:|
| samples | {residuals["sampleCount"]} |
| covered samples | {residuals["coveredSampleCount"]} |
| current residual | {residuals["currentResidual"]} |
| candidate residual | {residuals["candidateResidual"]} |
| candidate minus current | {residuals["candidateMinusCurrentResidual"]} |
| worsened samples | {residuals["worsenedSampleCount"]} |
| improved samples | {residuals["improvedSampleCount"]} |
| unchanged samples | {residuals["unchangedSampleCount"]} |

## Provenance

- Source memory: `{SOURCE_MEMORY}`
- Source finding: `{SOURCE_FINDING}`
- FOR-344 gate: `{data["inputValidation"]["for344Decision"]}`
- Skia reference source: `{data["referenceSource"]["source"]["path"]}`
- Skia reference capture command: `{row["reference"]["captureCommand"]}`
- Current capture method: `{row["current"]["captureMethod"]}`

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
- Validator: `scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-345-non-arc-rec2020-f16-reference-row.md`
- Skia reference source: `tools/skia-reference/non_arc_rec2020_f16_reference.cpp`
- Skia reference builder: `tools/skia-reference/build_for345_non_arc_rec2020_f16_reference.py`

## Validation

{validation}
"""


def main() -> None:
    validate_source_guardrails()
    for344 = load_json(FOR344_ARTIFACT)
    reference = load_json(SKIA_REFERENCE_JSON)
    provenance = load_json(SKIA_REFERENCE_PROVENANCE)
    current_capture = load_json(CURRENT_KANVAS_JSON)
    validate_for344(for344)
    data = build_artifact(for344, reference, provenance, current_capture)
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-345 validation passed")


if __name__ == "__main__":
    main()
