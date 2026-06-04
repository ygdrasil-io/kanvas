#!/usr/bin/env python3
"""Validate the FOR-343 explicit F16 color-policy boundary artifact."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-343"
SCENE_ID = "f16-color-policy-boundary-for343"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-343-f16-color-policy-boundary.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-explicit-f16-color-policy-boundary-and-broader-evidence-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-342-circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation-partial-safer-route-finding"
)

FOR342_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation-for342"
FOR342_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR342_SCENE_ID
    / f"{FOR342_SCENE_ID}.json"
)
FOR342_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_SCOPED_IMPLEMENTATION_PARTIAL_REQUIRES_SAFER_ROUTE"
)

DECISION_READY = "F16_COLOR_POLICY_BOUNDARY_READY_FOR_BROADER_EVIDENCE"
DECISION_PARTIAL = "F16_COLOR_POLICY_BOUNDARY_PARTIAL_REQUIRES_ARCHITECTURE_DECISION"
DECISION_INPUT_INVALID = "F16_COLOR_POLICY_BOUNDARY_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_READY, DECISION_PARTIAL, DECISION_INPUT_INVALID]

REQUIRED_POLICY_AXES = [
    "sourceColorSpace",
    "alphaQuantization",
    "compositingBasis",
    "exportReadbackBoundary",
    "referenceBasis",
]

REQUIRED_UNSAFE_ROUTE_CODES = [
    "F16_POLICY_UNSAFE_FIXTURE_BRANCH",
    "F16_POLICY_UNSAFE_COORDINATE_BRANCH",
    "F16_POLICY_UNSAFE_SELECTED_CELL_SUBSTITUTION",
    "F16_POLICY_UNSAFE_FULL_GM_CROP",
    "F16_POLICY_UNSAFE_GLOBAL_HOOK_MUTATION_WITHOUT_BOUNDARY",
]

FOR342_POLICY_ID = (
    "adjacent_circular_arcs_stroke_butt_f16_straight_srgb_quantized_alpha_src_over_white"
)

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for343_f16_color_policy_boundary.py",
    "rtk python3 scripts/validate_for342_circular_arcs_stroke_butt_adjacent_f16_color_policy_scoped_implementation.py",
    "rtk python3 scripts/validate_for341_circular_arcs_stroke_butt_adjacent_f16_color_policy_decision.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-343 validation failed: {message}")


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


def validate_for342(for342: dict[str, Any]) -> None:
    require(for342.get("linear") == "FOR-342", "FOR-342 artifact identity changed")
    require(for342.get("decision") == FOR342_REQUIRED_DECISION, "FOR-342 safer-route decision is missing")
    require(for342.get("sourceFindings") == [
        "global/kanvas/findings/for-341-circular-arcs-stroke-butt-adjacent-f16-color-policy-ready-for-scoped-implementation-finding"
    ], "FOR-342 source finding changed")

    input_validation = for342.get("inputValidation")
    require(isinstance(input_validation, dict), "FOR-342 inputValidation missing")
    require(
        input_validation.get("rawTransparentPngBasisAcceptedForImplementation") is False,
        "FOR-342 accepted raw transparent PNG basis",
    )

    implementation = for342.get("implementation")
    require(isinstance(implementation, dict), "FOR-342 implementation block missing")
    require(implementation.get("rendererBehaviorChanged") is False, "FOR-342 changed renderer behavior")
    require(implementation.get("implementationApplied") is False, "FOR-342 applied renderer behavior")
    require(implementation.get("partialDecisionStable") is True, "FOR-342 partial decision is not stable")

    policy = for342.get("policy")
    require(isinstance(policy, dict), "FOR-342 policy block missing")
    require(policy.get("authorizedPolicyId") == FOR342_POLICY_ID, "FOR-342 authorized policy changed")
    require(policy.get("rawTransparentPngBasisAcceptedForImplementation") is False, "FOR-342 accepts raw PNG")

    totals = for342.get("residualTotals")
    require(isinstance(totals, dict), "FOR-342 residualTotals missing")
    require(totals.get("oldCurrentOverWhiteResidual") == 375, "FOR-342 old residual changed")
    require(totals.get("actualNewOverWhiteResidual") == 375, "FOR-342 actual-new residual changed")
    require(totals.get("candidateNewOverWhiteResidual") == 0, "FOR-342 candidate residual changed")
    require(totals.get("rawTransparentPngResidualRejected") == 7065, "FOR-342 raw residual changed")


def build_artifact(for342: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for342Artifact": rel(FOR342_ARTIFACT),
            "for342Decision": for342.get("decision"),
            "for342RequiredDecision": FOR342_REQUIRED_DECISION,
            "for342SaferRouteRequired": for342.get("decision") == FOR342_REQUIRED_DECISION,
            "for342AuthorizedPolicyId": for342["policy"]["authorizedPolicyId"],
            "rawTransparentPngBasisAcceptedForImplementation": False,
            "historicalArtifactsFOR329ToFOR342Rewritten": False,
        },
        "decision": DECISION_READY,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-343 defines an explicit F16 color-policy boundary and broader evidence gates "
            "before any global F16 renderer behavior change. It carries FOR-342's safer-route "
            "decision forward, rejects fixture/coordinate branches and unbounded hook mutation, "
            "and leaves all renderer behavior unchanged."
        ),
        "boundary": {
            "id": "cpu-raster-f16-color-policy-boundary",
            "status": "defined-for-evidence-not-implemented",
            "scope": "CPU raster F16 color policy evidence boundary before a future global migration",
            "rendererBehaviorChanged": False,
            "globalF16RendererChangeAllowedNow": False,
            "broaderEvidenceCollectionAllowed": True,
            "internalOracle": "SkBitmap.getPixel",
            "exportBoundary": "SkBitmap.getPixelAsSrgb",
            "rawTransparentPngBasisAcceptedForImplementation": False,
            "policyAxes": [
                {
                    "name": "sourceColorSpace",
                    "candidateValueFromFOR342": "straight sRGB source channels",
                    "currentHookOrEvidence": "colorToF16Premul / paint color transform evidence",
                    "futureAttachmentPoint": "typed PipelineIR color-space policy block before F16 premul conversion",
                    "migrationGate": "requires reference/current/candidate samples across arc and non-arc F16 blend scenes",
                },
                {
                    "name": "alphaQuantization",
                    "candidateValueFromFOR342": "rounded covered alpha",
                    "currentHookOrEvidence": "coverage-applied source alpha before blendF16PremulMode",
                    "futureAttachmentPoint": "coverage-to-color boundary with an explicit alpha quantization policy",
                    "migrationGate": "requires worsened-sample accounting and edge/center sample tables",
                },
                {
                    "name": "compositingBasis",
                    "candidateValueFromFOR342": "SrcOver composited over white for comparable evidence",
                    "currentHookOrEvidence": "blendF16PremulMode",
                    "futureAttachmentPoint": "BlendPlan or pipeline blend/store policy selected by typed color domain",
                    "migrationGate": "requires non-arc Rec.2020 F16 SrcOver blend proof before global hook change",
                },
                {
                    "name": "exportReadbackBoundary",
                    "candidateValueFromFOR342": "encoded sRGB export remains explicit and unchanged",
                    "currentHookOrEvidence": "SkBitmap.getPixelAsSrgb",
                    "futureAttachmentPoint": "readback/export policy boundary after internal SkBitmap.getPixel oracle",
                    "migrationGate": "requires separate export migration if getPixelAsSrgb semantics change",
                },
                {
                    "name": "referenceBasis",
                    "candidateValueFromFOR342": "isolated Skia over-white reference, not raw transparent PNG",
                    "currentHookOrEvidence": "FOR-340/FOR-342 isolated reference and residual tables",
                    "futureAttachmentPoint": "evidence artifact schema with reference/current/candidate triples",
                    "migrationGate": "requires cross-scene reference/current/candidate evidence and raw PNG rejection",
                },
            ],
            "futureAttachmentPoints": [
                {
                    "id": "pipeline-ir-color-policy-block",
                    "owner": "future typed PipelineIR color-space/value semantics",
                    "purpose": "make source color space, alpha domain, precision, and reference basis auditable before CPU/GPU specialization",
                    "notThisTicket": True,
                },
                {
                    "id": "cpu-raster-f16-premul-conversion-policy",
                    "owner": "future CPU raster F16 conversion boundary",
                    "currentGlobalHook": "colorToF16Premul",
                    "purpose": "route F16 source-color policy through a named boundary before touching conversion semantics",
                    "notThisTicket": True,
                },
                {
                    "id": "cpu-raster-f16-blend-policy",
                    "owner": "future CPU raster blend/store policy",
                    "currentGlobalHook": "blendF16PremulMode",
                    "purpose": "select compositing basis without fixture or coordinate branches",
                    "notThisTicket": True,
                },
                {
                    "id": "encoded-export-readback-policy",
                    "owner": "future export/readback migration if approved separately",
                    "currentGlobalHook": "SkBitmap.getPixelAsSrgb",
                    "purpose": "separate internal oracle behavior from encoded export semantics",
                    "notThisTicket": True,
                },
            ],
        },
        "broaderEvidenceRequiredBeforeGlobalF16Change": [
            {
                "id": "for342-adjacent-arc-prerequisite",
                "kind": "arc-adjacent-f16-policy-prerequisite",
                "status": "present-partial-safer-route",
                "requiresReferenceCurrentCandidate": True,
                "sourceArtifacts": [rel(FOR342_ARTIFACT)],
                "summary": {
                    "sampleCount": for342["residualTotals"]["sampleCount"],
                    "strokeSampleCount": for342["residualTotals"]["strokeSampleCount"],
                    "oldCurrentOverWhiteResidual": for342["residualTotals"]["oldCurrentOverWhiteResidual"],
                    "actualNewOverWhiteResidual": for342["residualTotals"]["actualNewOverWhiteResidual"],
                    "candidateNewOverWhiteResidual": for342["residualTotals"]["candidateNewOverWhiteResidual"],
                },
                "blocksGlobalChangeUntil": "combined with broader non-arc and cross-scene evidence",
            },
            {
                "id": "non-arc-rec2020-f16-src-over-blend-reference-current-candidate",
                "kind": "non-arc-f16-blend",
                "status": "missing-required",
                "requiresReferenceCurrentCandidate": True,
                "minimumRequirement": (
                    "A non-arc Rec.2020 kRGBA_F16Norm SrcOver/blend scene with isolated reference, "
                    "current renderer samples, candidate policy samples, residuals, and worsened-sample counts."
                ),
                "mustNotUseAsSubstitute": [
                    "FOR-337 M60 targetColorSpaceBlend diagnostic without candidate values",
                    "sRGB-only F16 blend tests",
                    "arc selected-cell residuals",
                ],
            },
            {
                "id": "cross-scene-reference-current-candidate-matrix",
                "kind": "cross-scene-reference-current-candidate",
                "status": "missing-required",
                "requiresReferenceCurrentCandidate": True,
                "minimumRequirement": (
                    "At least one arc-adjacent group and one non-arc F16 blend group must report "
                    "reference/current/candidate triples under the same named policy axes before "
                    "colorToF16Premul, blendF16PremulMode, or export behavior can move globally."
                ),
                "mustInclude": [
                    "isolated reference or explicit expected reference basis",
                    "current SkBitmap.getPixelAsSrgb export samples",
                    "candidate policy samples",
                    "per-sample residuals and aggregate residual totals",
                    "worsened-sample count",
                ],
            },
        ],
        "dangerousRouteDiagnostics": [
            {
                "code": "F16_POLICY_UNSAFE_FIXTURE_BRANCH",
                "route": "fixture-specific renderer branch",
                "status": "rejected",
                "reason": "would encode policy by scene identity instead of a typed color-policy boundary",
            },
            {
                "code": "F16_POLICY_UNSAFE_COORDINATE_BRANCH",
                "route": "coordinate-specific renderer branch",
                "status": "rejected",
                "reason": "would patch selected pixels/cells rather than define global policy semantics",
            },
            {
                "code": "F16_POLICY_UNSAFE_SELECTED_CELL_SUBSTITUTION",
                "route": "selected-cell or FOR-327 substitution",
                "status": "rejected",
                "reason": "FOR-329 through FOR-342 require exact-cell traceability and reject extrapolation",
            },
            {
                "code": "F16_POLICY_UNSAFE_FULL_GM_CROP",
                "route": "full-GM crop reference",
                "status": "rejected",
                "reason": "crop evidence is not an isolated reference for implementation",
            },
            {
                "code": "F16_POLICY_UNSAFE_GLOBAL_HOOK_MUTATION_WITHOUT_BOUNDARY",
                "route": "mutating colorToF16Premul, blendF16PremulMode, or SkBitmap.getPixelAsSrgb before boundary approval",
                "status": "rejected",
                "reason": "these hooks are global and require broader evidence plus an explicit migration ticket",
            },
        ],
        "historicalTraceability": [
            {
                "linear": "FOR-329",
                "role": "historical selected-cell CPU raster audit remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-330",
                "role": "historical selected-cell evidence remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-331",
                "role": "historical selected-cell sample selection remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-332",
                "role": "historical CPU color pipeline trace remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-333",
                "role": "historical Kotlin CPU runtime trace remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-334",
                "role": "historical F16 diagnostic context remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-335",
                "role": "selected-cell F16 blend policy prerequisite remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-336",
                "role": "renderer color-policy cross-scene gate remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-337",
                "role": "mixed cross-scene evidence finding remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-338",
                "role": "comparable-sample target artifact remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-339",
                "role": "adjacent F16 runtime trace remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-340",
                "role": "adjacent isolated Skia reference remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-341",
                "role": "adjacent candidate policy decision remains traceable",
                "rewritten": False,
            },
            {
                "linear": "FOR-342",
                "role": "scoped implementation refusal and safer-route decision imported",
                "rewritten": False,
                "decision": for342.get("decision"),
            },
        ],
        "implementation": {
            "rendererBehaviorChanged": False,
            "evidenceOnly": True,
            "colorToF16PremulChanged": False,
            "blendF16PremulModeChanged": False,
            "skBitmapGetPixelChanged": False,
            "skBitmapGetPixelAsSrgbChanged": False,
            "fixtureBranchAdded": False,
            "coordinateBranchAdded": False,
            "fallbackChanged": False,
        },
        "nonGoalsPreserved": {
            "colorToF16Premul": True,
            "blendF16PremulMode": True,
            "skBitmapGetPixelInternalOracle": True,
            "skBitmapGetPixelAsSrgbExportBoundary": True,
            "geometry": True,
            "coveragePolicy": True,
            "gpu": True,
            "wgsl": True,
            "thresholds": True,
            "fallbacks": True,
            "kadre": True,
            "promotion": True,
            "score": True,
            "historicalArtifactsFOR329ToFOR342Rewritten": False,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "source finding changed")
    require(data.get("decision") == DECISION_READY, "expected boundary-ready decision")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")

    input_validation = data.get("inputValidation")
    require(isinstance(input_validation, dict), "inputValidation missing")
    require(input_validation.get("for342Decision") == FOR342_REQUIRED_DECISION, "FOR-342 decision not imported")
    require(input_validation.get("for342SaferRouteRequired") is True, "FOR-342 safer-route gate not enforced")
    require(input_validation.get("rawTransparentPngBasisAcceptedForImplementation") is False, "raw PNG basis accepted")
    require(input_validation.get("historicalArtifactsFOR329ToFOR342Rewritten") is False, "history rewrite flag changed")

    boundary = data.get("boundary")
    require(isinstance(boundary, dict), "boundary block missing")
    require(boundary.get("status") == "defined-for-evidence-not-implemented", "boundary status changed")
    require(boundary.get("rendererBehaviorChanged") is False, "boundary changed renderer behavior")
    require(boundary.get("globalF16RendererChangeAllowedNow") is False, "global F16 change allowed too early")
    require(boundary.get("broaderEvidenceCollectionAllowed") is True, "broader evidence collection not allowed")
    require(boundary.get("internalOracle") == "SkBitmap.getPixel", "internal oracle changed")
    require(boundary.get("exportBoundary") == "SkBitmap.getPixelAsSrgb", "export boundary changed")
    require(boundary.get("rawTransparentPngBasisAcceptedForImplementation") is False, "raw PNG basis accepted")

    axes = boundary.get("policyAxes")
    require(isinstance(axes, list), "policyAxes missing")
    axis_names = [axis.get("name") for axis in axes if isinstance(axis, dict)]
    require(axis_names == REQUIRED_POLICY_AXES, "policy axis order or names changed")
    for axis in axes:
        require(axis.get("futureAttachmentPoint"), f"{axis.get('name')} future attachment missing")
        require(axis.get("migrationGate"), f"{axis.get('name')} migration gate missing")

    attachment_points = boundary.get("futureAttachmentPoints")
    require(isinstance(attachment_points, list) and len(attachment_points) >= 4, "future attachment points missing")
    for point in attachment_points:
        require(point.get("notThisTicket") is True, f"{point.get('id')} should be future-only")

    broader = data.get("broaderEvidenceRequiredBeforeGlobalF16Change")
    require(isinstance(broader, list), "broader evidence requirements missing")
    evidence_ids = {item.get("id"): item for item in broader if isinstance(item, dict)}
    require(
        "non-arc-rec2020-f16-src-over-blend-reference-current-candidate" in evidence_ids,
        "non-arc F16 blend evidence requirement missing",
    )
    require(
        "cross-scene-reference-current-candidate-matrix" in evidence_ids,
        "cross-scene reference/current/candidate evidence requirement missing",
    )
    require(
        evidence_ids["non-arc-rec2020-f16-src-over-blend-reference-current-candidate"].get("status")
        == "missing-required",
        "non-arc evidence must remain required",
    )
    require(
        evidence_ids["cross-scene-reference-current-candidate-matrix"].get("requiresReferenceCurrentCandidate")
        is True,
        "cross-scene evidence must require reference/current/candidate triples",
    )

    diagnostics = data.get("dangerousRouteDiagnostics")
    require(isinstance(diagnostics, list), "dangerous route diagnostics missing")
    require([diag.get("code") for diag in diagnostics] == REQUIRED_UNSAFE_ROUTE_CODES, "unsafe route codes changed")
    require(all(diag.get("status") == "rejected" for diag in diagnostics), "unsafe route not rejected")

    history = data.get("historicalTraceability")
    require(isinstance(history, list) and len(history) == 14, "FOR-329 through FOR-342 traceability missing")
    require([item.get("linear") for item in history] == [f"FOR-{number}" for number in range(329, 343)], "history order changed")
    require(all(item.get("rewritten") is False for item in history), "historical artifact rewrite detected")
    require(history[-1].get("decision") == FOR342_REQUIRED_DECISION, "FOR-342 history decision changed")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation block missing")
    require(implementation.get("rendererBehaviorChanged") is False, "renderer behavior changed")
    require(implementation.get("evidenceOnly") is True, "FOR-343 must be evidence-only")
    for key in (
        "colorToF16PremulChanged",
        "blendF16PremulModeChanged",
        "skBitmapGetPixelChanged",
        "skBitmapGetPixelAsSrgbChanged",
        "fixtureBranchAdded",
        "coordinateBranchAdded",
        "fallbackChanged",
    ):
        require(implementation.get(key) is False, f"{key} must remain false")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        if key == "historicalArtifactsFOR329ToFOR342Rewritten":
            require(value is False, f"{key} changed")
        else:
            require(value is True, f"{key} not preserved")


def axes_table(data: dict[str, Any]) -> str:
    rows = [
        "| axis | candidate signal | future attachment | migration gate |",
        "|---|---|---|---|",
    ]
    for axis in data["boundary"]["policyAxes"]:
        rows.append(
            "| {name} | {candidate} | {attachment} | {gate} |".format(
                name=axis["name"],
                candidate=axis["candidateValueFromFOR342"],
                attachment=axis["futureAttachmentPoint"],
                gate=axis["migrationGate"],
            )
        )
    return "\n".join(rows)


def evidence_table(data: dict[str, Any]) -> str:
    rows = [
        "| requirement | kind | status | reference/current/candidate |",
        "|---|---|---|---|",
    ]
    for item in data["broaderEvidenceRequiredBeforeGlobalF16Change"]:
        rows.append(
            "| {id} | {kind} | {status} | {triples} |".format(
                id=item["id"],
                kind=item["kind"],
                status=item["status"],
                triples="yes" if item["requiresReferenceCurrentCandidate"] else "no",
            )
        )
    return "\n".join(rows)


def diagnostics_table(data: dict[str, Any]) -> str:
    rows = [
        "| diagnostic | route | status |",
        "|---|---|---|",
    ]
    for item in data["dangerousRouteDiagnostics"]:
        rows.append(f"| `{item['code']}` | {item['route']} | {item['status']} |")
    return "\n".join(rows)


def build_report(data: dict[str, Any]) -> str:
    return f"""# FOR-343 F16 Color Policy Boundary

Linear: `FOR-343`

Decision: `{data["decision"]}`

FOR-343 defines the explicit F16 color-policy boundary requested by FOR-342.
It is architecture/evidence only: no renderer behavior is changed, and no
global F16 hook is migrated in this ticket.

## Boundary

The boundary is `{data["boundary"]["id"]}`. It keeps `SkBitmap.getPixel` as the
internal oracle and `SkBitmap.getPixelAsSrgb` as the encoded export/readback
boundary. Raw transparent PNG Skia output remains rejected as an implementation
basis.

{axes_table(data)}

## Future Attachment Points

{chr(10).join(f"- `{point['id']}`: {point['purpose']}" for point in data["boundary"]["futureAttachmentPoints"])}

All attachment points are future-only. Any implementation must arrive through a
separate migration ticket with broader evidence, not through this artifact.

## Required Broader Evidence

{evidence_table(data)}

The non-arc F16 blend requirement and the cross-scene
reference/current/candidate requirement are intentionally still marked missing.
They are gates before any global change to `colorToF16Premul`,
`blendF16PremulMode`, or export semantics.

## Dangerous Routes

{diagnostics_table(data)}

## Imported FOR-342 Evidence

- FOR-342 decision: `{data["inputValidation"]["for342Decision"]}`
- Old/current over-white residual: `{data["broaderEvidenceRequiredBeforeGlobalF16Change"][0]["summary"]["oldCurrentOverWhiteResidual"]}`
- Actual-new renderer residual: `{data["broaderEvidenceRequiredBeforeGlobalF16Change"][0]["summary"]["actualNewOverWhiteResidual"]}`
- Candidate-new residual: `{data["broaderEvidenceRequiredBeforeGlobalF16Change"][0]["summary"]["candidateNewOverWhiteResidual"]}`
- Raw transparent PNG basis accepted: `false`

## Non-goals Preserved

- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No fixture branch, coordinate branch, selected-cell substitution, full-GM crop
  basis, or global hook mutation without boundary.
- No geometry, coverage, GPU, WGSL, threshold, fallback, Kadre, promotion, or
  score change.
- Historical artifacts FOR-329 through FOR-342 remain traceable and are not
  rewritten.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for343_f16_color_policy_boundary.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-343-f16-color-policy-boundary.md`

## Validation

{chr(10).join(f"- `{command}`" for command in VALIDATION_COMMANDS)}
"""


def main() -> None:
    for342 = load_json(FOR342_ARTIFACT)
    validate_for342(for342)
    data = build_artifact(for342)
    validate_artifact(data)
    artifact_text = json.dumps(data, indent=2) + "\n"
    report_text = build_report(data)
    write_if_changed(ARTIFACT, artifact_text)
    write_if_changed(REPORT, report_text)
    validate_artifact(load_json(ARTIFACT))
    if REPORT.read_text(encoding="utf-8") != report_text:
        fail(f"{rel(REPORT)} does not match generated report")


if __name__ == "__main__":
    main()
