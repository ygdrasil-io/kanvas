#!/usr/bin/env python3
"""Validate the FOR-393 M60 F16 source-facing lane shader metadata audit."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-393"
DECISION = "M60_F16_SOURCE_FACING_LANE_SHADER_METADATA_AUDITED"
CLASSIFICATION = "requires-new-uniforms"
METADATA_NAME = "sourceFacingLocalBandLane"
GUARD_PROPERTY = "kanvas.webgpu.m60F16SourceFacingLaneRuntimeCandidate.enabled"
OLD_PROBE_GUARD_PROPERTY = "kanvas.webgpu.m60F16SourceColorCorrectionProbe.enabled"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-exposer-metadata-lane-safe-par-fragment-apres-for-392"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-392-refuse-lactivation-runtime-source-facing-local-band-lane-faute-de-metadata-par-fragment"
)
FOR392_DECISION = "M60_F16_SOURCE_FACING_LANE_RUNTIME_OPT_IN_EVALUATED"
FOR392_CLASSIFICATION = "runtime-hook-refused-missing-per-fragment-lane-metadata"
FOR391_DECISION = "M60_F16_SOURCE_FACING_LOCAL_BAND_LANE_METADATA_RECORDED"
FOR391_CLASSIFICATION = "source-facing-local-band-lane-stable-diagnostic-only"

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-shader-metadata-for393"
)
ARTIFACT = ARTIFACT_DIR / "m60-f16-source-facing-lane-shader-metadata-for393.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-393-m60-f16-source-facing-lane-shader-metadata.md"
FOR392_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-runtime-opt-in-for392/"
    "m60-f16-source-facing-lane-runtime-opt-in-for392.json"
)
FOR391_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-metadata-for391/"
    "m60-f16-source-facing-lane-metadata-for391.json"
)
RENDERER_SOURCE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
AA_STENCIL_COVER_SHADER = PROJECT_ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

VALIDATION_COMMANDS = [
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    "rtk python3 scripts/validate_for393_m60_f16_source_facing_lane_shader_metadata.py",
    "rtk python3 scripts/validate_for392_m60_f16_source_facing_lane_runtime_opt_in.py",
    "rtk python3 scripts/validate_for391_m60_f16_source_facing_lane_metadata.py",
    "rtk python3 scripts/validate_for390_m60_f16_full_scene_regression_discriminator.py",
    "rtk python3 scripts/validate_for389_m60_f16_source_coverage_full_scene_candidate.py",
    "rtk python3 scripts/validate_for388_m60_f16_composition_metadata_audit.py",
    "rtk python3 scripts/validate_for387_m60_f16_residual_fringe_discriminator_audit.py",
    "rtk python3 scripts/validate_for386_m60_f16_coverage_regression_discriminator_audit.py",
    "rtk python3 scripts/validate_for385_m60_f16_generalized_coverage_metadata_predicate_audit.py",
    "rtk python3 scripts/validate_for384_m60_f16_pre_correction_geometry_coverage_metadata_audit.py",
    "rtk python3 scripts/validate_for383_m60_f16_pre_probe_predicate_audit.py",
    "rtk python3 scripts/validate_for382_m60_f16_coverage_composition_membership_audit.py",
    "rtk python3 scripts/validate_for381_m60_f16_source_color_subzone_audit.py",
    "rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py",
    "rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py",
    "rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py",
    "rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py",
    "rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py",
    "rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py",
    "rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py",
    "rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py",
    "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
    "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
    "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
    "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
    "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
    "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
    "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-393 validation failed: {message}")


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


def read_source(path: Path) -> str:
    require(path.is_file(), f"missing source file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def validate_sources() -> tuple[dict[str, Any], dict[str, Any]]:
    for392 = load_json(FOR392_ARTIFACT)
    require(for392.get("decision") == FOR392_DECISION, "FOR-392 decision changed")
    require(for392.get("classification") == FOR392_CLASSIFICATION, "FOR-392 classification changed")
    require(for392.get("correctionAppliedByDefault") is False, "FOR-392 default correction changed")
    require(for392.get("defaultBehaviorChanged") is False, "FOR-392 default behavior changed")
    guard = for392.get("explicitOptInGuard")
    require(isinstance(guard, dict), "FOR-392 guard proof missing")
    require(guard.get("guardId") == GUARD_PROPERTY, "FOR-392 guard id changed")
    require(guard.get("enabledByDefault") is False, "FOR-392 guard default changed")
    require(guard.get("runtimeHookInstalled") is False, "FOR-392 runtime hook changed")
    require(guard.get("oldDrawWideProbeBlockedWhenFor392GuardRequested") is True, "FOR-392 broad probe block changed")

    for391 = load_json(FOR391_ARTIFACT)
    require(for391.get("decision") == FOR391_DECISION, "FOR-391 decision changed")
    require(for391.get("classification") == FOR391_CLASSIFICATION, "FOR-391 classification changed")
    require(for391.get("runtimeHookInstalled") is False, "FOR-391 runtime hook changed")
    metadata = for391.get("metadata")
    require(isinstance(metadata, dict), "FOR-391 metadata block missing")
    require(metadata.get("name") == METADATA_NAME, "FOR-391 metadata name changed")
    require(metadata.get("derivationFields") == ["strokeBand", "bandLocalX"], "FOR-391 derivation fields changed")
    selection = for391.get("selection")
    require(isinstance(selection, dict), "FOR-391 selection missing")
    require(selection.get("selectedPixels") == 8, "FOR-391 selected count changed")
    require(selection.get("improvedPixelsRecovered") == 8, "FOR-391 improved count changed")
    require(selection.get("regressionsIncluded") == 0, "FOR-391 regression count changed")
    ideal = selection.get("estimatedFullSceneResidualIfAppliedToThisSubset")
    require(isinstance(ideal, dict), "FOR-391 ideal residual missing")
    require(ideal.get("baseUncorrectedFullSceneResidual") == 2014, "FOR-391 base residual changed")
    require(ideal.get("simulatedAfterResidual") == 1949, "FOR-391 ideal residual changed")
    return for392, for391


def inspect_renderer_path() -> dict[str, Any]:
    source = read_source(RENDERER_SOURCE)
    shader = read_source(AA_STENCIL_COVER_SHADER)
    capture_source = read_source(CAPTURE_PRODUCER)

    for needle in (
        "private data class StencilCoverAaPolygonDraw(",
        "val edges: FloatArray",
        "val edgeCount: Int",
        "val scissor: IntArray",
        "val fillType: SkPathFillType",
        "val colorFilterPacked: FloatArray = ZERO_COLOR_FILTER_24",
        "val m60F16SourceColorCorrectionProbe: Boolean = false",
        "private fun buildStencilCoverAaDrawResources(d: StencilCoverAaPolygonDraw)",
        "val packed = FloatArray(12 + MAX_AA_EDGES * 4 + 8 + 24)",
        "System.arraycopy(d.edges, 0, packed, 12, d.edges.size)",
        "packed[colorFilterBase + 2] = if (d.m60F16SourceColorCorrectionProbe) 0f else targetColorSpaceBlendFlag()",
        "aaStencilCoverPipelineFor(d.mode, d.fillType, CoverageSide.Inside)",
        "aaStencilCoverPipelineFor(d.mode, d.fillType, CoverageSide.Outside)",
        "m60F16SourceFacingLaneRuntimeCandidateRequested()",
        "if (m60F16SourceFacingLaneRuntimeCandidateRequested()) return false",
    ):
        require(needle in source, f"renderer source missing expected proof: {needle}")

    for needle in (
        "struct Uniforms",
        "color:               vec4f",
        "viewport:            vec4f",
        "edgeCount:           u32",
        "fillType:            u32",
        "edges:               array<vec4f, 256>",
        "clipShapeBounds:     vec4f",
        "clipShapeRadiiKind:  vec4f",
        "colorFilterKindMode: vec4f",
        "fn supersampled_path_cov(pixel: vec2f) -> f32",
        "fn fs_inside(@builtin(position) frag: vec4f)",
        "fn fs_outside(@builtin(position) frag: vec4f)",
    ):
        require(needle in shader, f"AA stencil-cover shader missing expected proof: {needle}")

    for forbidden in ("sourceFacingLocalBandLane", "strokeBand", "bandLocalX", "bandXStart", "bandXEnd"):
        require(forbidden not in shader, f"shader unexpectedly exposes {forbidden}")

    for needle in (
        "activeStrokeStyleForPathAaDiagnostics = StrokeStyleDiagnostics(",
        "strokeWidth = deviceStrokeWidth",
        "cap = paint.strokeCap.coverageDiagnosticName()",
        "join = paint.strokeJoin.coverageDiagnosticName()",
        "selectCoveragePlanForDraw(",
        "strokeCaps = activeStrokeStyleForPathAaDiagnostics?.cap?.let(::listOf).orEmpty()",
        "strokeJoins = activeStrokeStyleForPathAaDiagnostics?.join?.let(::listOf).orEmpty()",
    ):
        require(needle in source, f"host diagnostic source missing expected proof: {needle}")

    for needle in (
        "bandLocalX = membership.pixel.x - band.xStart",
        "bandWidth = band.xEnd - band.xStart",
        "strokeBand = band.id",
    ):
        require(needle in capture_source, f"capture fixture metadata source missing proof: {needle}")

    return {
        "renderPoint": "StencilCoverAaPolygonDraw solid-color AA stencil-cover cover pass",
        "rendererSource": rel(RENDERER_SOURCE),
        "shaderSource": rel(AA_STENCIL_COVER_SHADER),
        "uniformPacker": "buildStencilCoverAaDrawResources",
        "shaderEntryPoints": ["fs_inside", "fs_outside"],
        "preShaderAvailableData": [
            "solid color premul/unpremul source color",
            "viewport size",
            "edgeCount",
            "fillType",
            "directed path edge segments as edges[256] vec4f(Ax, Ay, Bx, By)",
            "cover quad vertices",
            "stencil winding partition via inside/outside cover pipelines",
            "scissor rectangle",
            "clipShapeBounds",
            "clipShapeRadiiKind",
            "colorFilter payload",
            "draw-wide targetColorSpaceBlend flag",
            "host-side stroke style diagnostics: strokeWidth, cap, join",
        ],
        "shaderAvailableData": [
            "fragment position frag.xy",
            "supersampled_path_cov(frag.xy)",
            "clip_cov(frag.xy)",
            "edge list and edgeCount",
            "fillType",
            "inside/outside stencil-cover side selected by pipeline",
            "draw-wide targetColorSpaceBlend flag",
        ],
        "missingFields": [
            "strokeBand per fragment or per band",
            "bandLocalX per fragment",
            "band xStart/xEnd bounds",
            "band identity to cap/join mapping",
            "sourceFacingLocalBandLane boolean",
        ],
        "minimalExtensionPoint": {
            "owner": "StencilCoverAaPolygonDraw uniform packing",
            "packer": "buildStencilCoverAaDrawResources",
            "shader": "aa_stencil_cover.wgsl Uniforms",
            "fields": [
                "diagnostic lane metadata enable flag, disabled by default",
                "bounded band metadata table: xStart, xEnd, strokeBandId/capJoinId",
                "or equivalent segment/band metadata sufficient to derive bandLocalX from frag.xy",
            ],
        },
    }


def build_artifact() -> dict[str, Any]:
    for392, for391 = validate_sources()
    shader_path = inspect_renderer_path()
    selection = for391["selection"]
    ideal = selection["estimatedFullSceneResidualIfAppliedToThisSubset"]
    guard_enabled = for392["guardEnabledEvaluation"]

    artifact = {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": "m60-f16-source-facing-lane-shader-metadata-for393",
        "sourceArtifact": rel(FOR392_ARTIFACT),
        "secondarySourceArtifact": rel(FOR391_ARTIFACT),
        "adapter": for391["adapter"],
        "producer": "scripts/validate_for393_m60_f16_source_facing_lane_shader_metadata.py",
        "producerInput": "FOR-392 runtime refusal plus FOR-391 sourceFacingLocalBandLane metadata evidence",
        "sourceMemory": SOURCE_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "requiredFor392Decision": FOR392_DECISION,
        "requiredFor392Classification": FOR392_CLASSIFICATION,
        "requiredFor391Decision": FOR391_DECISION,
        "requiredFor391Classification": FOR391_CLASSIFICATION,
        "decision": DECISION,
        "classification": CLASSIFICATION,
        "status": CLASSIFICATION,
        "allowedStatuses": ["shader-exportable", "requires-new-uniforms", "not-derivable-currently"],
        "supportClaim": False,
        "candidateOnly": True,
        "promoted": False,
        "limitedTo": "M60 F16 bounded stroke cap/join scene only",
        "correctionAppliedByDefault": False,
        "correctionPredicateEnabledByDefault": False,
        "runtimeHookInstalled": False,
        "defaultBehaviorChanged": False,
        "renderedPixelsChanged": False,
        "wgslChanged": False,
        "fallbackChanged": False,
        "scoringChanged": False,
        "thresholdChanged": False,
        "generalizedOutsideM60F16": False,
        "oldDrawWideProbeUsed": False,
        "oldDrawWideProbeBlockedWhenFor392GuardRequested": True,
        "explicitOptInGuard": {
            "guardId": GUARD_PROPERTY,
            "enabledByDefault": False,
            "recognizedByRenderer": True,
            "preservedFromFor392": True,
            "runtimeHookInstalled": False,
            "correctionAllowedWhenEnabled": False,
            "oldDrawWideProbeGuard": OLD_PROBE_GUARD_PROPERTY,
            "oldDrawWideProbeUsed": False,
        },
        "candidatePredicate": {
            "id": "source-facing-local-band-lane",
            "metadataName": METADATA_NAME,
            "derivationFormula": "(strokeBand == round-round && bandLocalX >= 39) || (strokeBand == butt-bevel && bandLocalX <= 17)",
            "requiredDerivationFields": ["strokeBand", "bandLocalX"],
            "selectedPixels": selection["selectedPixels"],
            "improvedPixelsRecovered": selection["improvedPixelsRecovered"],
            "regressionsIncluded": selection["regressionsIncluded"],
            "idealSimulatedFullSceneImpactIfLaneRuntimeWereAvailable": {
                "baseUncorrectedFullSceneResidual": ideal["baseUncorrectedFullSceneResidual"],
                "simulatedAfterResidual": ideal["simulatedAfterResidual"],
                "deltaVsCurrent": ideal["deltaVsCurrent"],
                "gainVsCurrent": ideal["gainVsCurrent"],
            },
        },
        "shaderMetadataAudit": {
            "metadataName": METADATA_NAME,
            "status": CLASSIFICATION,
            "shaderExportableWithCurrentUniforms": False,
            "diagnosticExported": False,
            "derivedInShader": False,
            "safeToWireRuntimePredicate": False,
            "refusalReason": "source-facing-local-band-lane-requires-band-metadata-uniforms",
            "whyUnsafe": (
                "The current shader can see frag.xy, edge equations, fillType, clip and coverage, "
                "but it cannot map a fragment to the fixture stroke band or compute bandLocalX. "
                "Using the draw-wide targetColorSpaceBlend switch would reintroduce the FOR-389/FOR-392 unsafe path."
            ),
            "path": shader_path,
        },
        "guardEnabledEvaluationPreserved": {
            "runtimeCorrectionApplied": guard_enabled["runtimeCorrectionApplied"],
            "runtimeHookInstalled": guard_enabled["runtimeHookInstalled"],
            "residualWithGuardEnabled": guard_enabled["residualWithGuardEnabled"],
            "residualDeltaWithGuardEnabled": guard_enabled["residualDeltaWithGuardEnabled"],
            "reason": guard_enabled["reason"],
        },
        "nonGoalsPreserved": {
            "m60F16CorrectionEnabled": False,
            "runtimePredicateActivated": False,
            "for380ProbeRouteUsed": False,
            "scoringChanged": False,
            "thresholdChanged": False,
            "promotionChanged": False,
            "generalFallbackChanged": False,
            "aaStencilCoverColorCorrectionRewrite": False,
            "unrelatedScenesChanged": False,
        },
        "validationCommands": VALIDATION_COMMANDS,
        "classificationReason": (
            "The AA stencil-cover path has enough data for coverage, clip and color, but not enough "
            "stable per-fragment band metadata to derive sourceFacingLocalBandLane. The minimal next "
            "extension is a disabled diagnostic uniform or equivalent metadata table in "
            "StencilCoverAaPolygonDraw/buildStencilCoverAaDrawResources and aa_stencil_cover.wgsl."
        ),
    }
    return artifact


def write_outputs(artifact: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(artifact, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    REPORT.write_text(render_report(artifact), encoding="utf-8")


def render_report(artifact: dict[str, Any]) -> str:
    path = artifact["shaderMetadataAudit"]["path"]
    missing = "\n".join(f"- `{field}`" for field in path["missingFields"])
    pre_shader = "\n".join(f"- {field}" for field in path["preShaderAvailableData"])
    shader_data = "\n".join(f"- {field}" for field in path["shaderAvailableData"])
    extension_fields = "\n".join(f"- {field}" for field in path["minimalExtensionPoint"]["fields"])
    ideal = artifact["candidatePredicate"]["idealSimulatedFullSceneImpactIfLaneRuntimeWereAvailable"]
    guard = artifact["guardEnabledEvaluationPreserved"]

    return f"""# FOR-393 M60 F16 sourceFacingLocalBandLane shader metadata

Decision: `{artifact["decision"]}`

Classification: `{artifact["classification"]}`

Artifact: `{rel(ARTIFACT)}`

FOR-393 inspecte `StencilCoverAaPolygonDraw`, `buildStencilCoverAaDrawResources`
et `aa_stencil_cover.wgsl` pour savoir si la metadata
`sourceFacingLocalBandLane` peut etre derivee cote shader, par fragment, sans
changer le rendu.

## Resultat

Statut JSON: `{artifact["status"]}`.

Le chemin courant ne peut pas exporter la lane avec les uniformes existants.
Il faut ajouter des uniformes ou une table equivalente de metadata de bande
avant de raccorder le predicate au runtime.

Le garde FOR-392 est preserve :

- garde: `{artifact["explicitOptInGuard"]["guardId"]}` ;
- active par defaut: `false` ;
- hook runtime: `false` ;
- probe FOR-380 draw-wide utilise: `false` ;
- residuel avec garde demande: `{guard["residualWithGuardEnabled"]}` ;
- delta avec garde demande: `{guard["residualDeltaWithGuardEnabled"]}`.

## Donnees disponibles avant shader

{pre_shader}

## Donnees disponibles dans le shader

{shader_data}

## Donnees manquantes

{missing}

## Pourquoi le raccord runtime reste unsafe

Le predicate FOR-391 demande `strokeBand` et `bandLocalX` :

`(strokeBand == round-round && bandLocalX >= 39) || (strokeBand == butt-bevel && bandLocalX <= 17)`.

Ces champs existent dans les preuves de scene, ou `bandLocalX` vient de
`membership.pixel.x - band.xStart`, mais ils ne sont pas packs dans l'uniforme
AA stencil-cover. Le shader voit `frag.xy`, les segments de bord, la couverture
et le clip ; cela ne suffit pas a identifier de facon stable la bande de stroke
ni son origine locale. Router le garde FOR-392 vers le vieux controle
draw-wide FOR-380 serait donc unsafe et recreerait la regression full-scene
deja prouvee.

## Point d'extension minimal

Owner: `{path["minimalExtensionPoint"]["owner"]}`

Packer: `{path["minimalExtensionPoint"]["packer"]}`

Shader: `{path["minimalExtensionPoint"]["shader"]}`

Champs minimaux :

{extension_fields}

## Compteurs conserves

- pixels selectionnes par le predicate ideal: `{artifact["candidatePredicate"]["selectedPixels"]}` ;
- ameliores recuperes: `{artifact["candidatePredicate"]["improvedPixelsRecovered"]}/8` ;
- regressions incluses: `{artifact["candidatePredicate"]["regressionsIncluded"]}/8` ;
- residuel ideal si metadata runtime disponible: `{ideal["baseUncorrectedFullSceneResidual"]} -> {ideal["simulatedAfterResidual"]}`.

## Non-objectifs

Aucune correction M60 F16, activation de predicate, modification WGSL runtime,
fallback general, scoring, seuil, promotion ou scene non liee n'est effectuee.
"""


def validate_artifact(artifact: dict[str, Any]) -> None:
    require(artifact.get("decision") == DECISION, "decision changed")
    require(artifact.get("classification") == CLASSIFICATION, "classification changed")
    require(artifact.get("status") in artifact.get("allowedStatuses", []), "status is not allowed")
    require(artifact.get("status") == "requires-new-uniforms", "FOR-393 expected requires-new-uniforms")
    require(artifact.get("correctionAppliedByDefault") is False, "correction default changed")
    require(artifact.get("runtimeHookInstalled") is False, "runtime hook changed")
    require(artifact.get("defaultBehaviorChanged") is False, "default behavior changed")
    require(artifact.get("renderedPixelsChanged") is False, "rendered pixels changed")
    require(artifact.get("wgslChanged") is False, "WGSL change flag changed")
    require(artifact.get("oldDrawWideProbeUsed") is False, "old draw-wide probe used")

    guard = artifact.get("explicitOptInGuard")
    require(isinstance(guard, dict), "guard block missing")
    require(guard.get("guardId") == GUARD_PROPERTY, "guard id changed")
    require(guard.get("enabledByDefault") is False, "guard default changed")
    require(guard.get("runtimeHookInstalled") is False, "guard runtime hook changed")
    require(guard.get("oldDrawWideProbeUsed") is False, "old draw-wide probe flag changed")

    candidate = artifact.get("candidatePredicate")
    require(isinstance(candidate, dict), "candidate predicate missing")
    require(candidate.get("metadataName") == METADATA_NAME, "metadata name changed")
    require(candidate.get("requiredDerivationFields") == ["strokeBand", "bandLocalX"], "required fields changed")
    require(candidate.get("selectedPixels") == 8, "selected count changed")
    require(candidate.get("improvedPixelsRecovered") == 8, "improved count changed")
    require(candidate.get("regressionsIncluded") == 0, "regression count changed")
    ideal = candidate.get("idealSimulatedFullSceneImpactIfLaneRuntimeWereAvailable")
    require(isinstance(ideal, dict), "ideal impact missing")
    require(ideal.get("baseUncorrectedFullSceneResidual") == 2014, "base residual changed")
    require(ideal.get("simulatedAfterResidual") == 1949, "ideal residual changed")

    audit = artifact.get("shaderMetadataAudit")
    require(isinstance(audit, dict), "shader metadata audit missing")
    require(audit.get("status") == "requires-new-uniforms", "shader metadata status changed")
    require(audit.get("shaderExportableWithCurrentUniforms") is False, "shader exportability changed")
    require(audit.get("safeToWireRuntimePredicate") is False, "runtime safety changed")
    path = audit.get("path")
    require(isinstance(path, dict), "shader path block missing")
    missing = set(path.get("missingFields", []))
    for field in ("strokeBand per fragment or per band", "bandLocalX per fragment", "band xStart/xEnd bounds"):
        require(field in missing, f"missing field not recorded: {field}")
    available = set(path.get("shaderAvailableData", []))
    for field in ("fragment position frag.xy", "supersampled_path_cov(frag.xy)", "edge list and edgeCount"):
        require(field in available, f"shader available data not recorded: {field}")

    non_goals = artifact.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "non-goals missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal violated: {key}")

    require(REPORT.is_file(), f"report missing: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for needle in (
        "Statut JSON: `requires-new-uniforms`",
        "StencilCoverAaPolygonDraw",
        "buildStencilCoverAaDrawResources",
        "aa_stencil_cover.wgsl",
        "probe FOR-380 draw-wide utilise: `false`",
        "bandLocalX",
        "Point d'extension minimal",
    ):
        require(needle in report, f"report missing {needle}")


def main() -> None:
    artifact = build_artifact()
    write_outputs(artifact)
    validate_artifact(load_json(ARTIFACT))
    print(
        f"FOR-393 validation passed: status={artifact['status']} "
        f"artifact={rel(ARTIFACT)} report={rel(REPORT)}"
    )


if __name__ == "__main__":
    main()
