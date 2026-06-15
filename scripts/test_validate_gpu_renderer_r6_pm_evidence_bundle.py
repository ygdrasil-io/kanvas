#!/usr/bin/env python3
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_gpu_renderer_r6_pm_evidence_bundle as validator


ROOT_ARTIFACT_NAMES = [
    "gpu-renderer-first-route-pm-evidence-01-command.txt",
    "gpu-renderer-first-route-pm-evidence-02-analysis.txt",
    "gpu-renderer-first-route-pm-evidence-03-route.txt",
    "gpu-renderer-first-route-pm-evidence-04-material.txt",
    "gpu-renderer-first-route-pm-evidence-05-wgsl.txt",
    "gpu-renderer-first-route-pm-evidence-06-payload.txt",
    "gpu-renderer-first-route-pm-evidence-07-pipeline-key.txt",
    "gpu-renderer-first-route-pm-evidence-08-resource-decision.txt",
    "gpu-renderer-first-route-pm-evidence-09-submission.txt",
    "gpu-renderer-first-route-pm-evidence-10-readback.txt",
    "gpu-renderer-first-route-pm-evidence-11-telemetry.txt",
    "gpu-renderer-first-route-pm-evidence-12-pipeline-cache.txt",
    "gpu-renderer-first-route-pm-evidence-13-negative-cpu-fallback.txt",
    "gpu-renderer-first-route-pm-evidence-14-unsupported-route-refusals.txt",
]


def exported_text(lines: list[str]) -> str:
    return "\n".join(lines) + "\n"


def sha256_text(text: str) -> str:
    return "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest()


def write_json(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_fixture_bundle(root: Path) -> Path:
    output_dir = root / "gpu-renderer/build/reports/gpu-renderer-r6-first-route-pm-evidence"
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest_lines = [
        "validation.report.name=gpu-renderer-first-route-pm-evidence",
        "validation.report.status=Incomplete",
        "validation.gate.name=first-route-promotion",
        "validation.gate.passed=false",
        "validation.gate.missingEvidence=route,resource-decision,submission,readback,pipeline-cache",
        "validation.bundle.scope=pm-evidence-only",
        f"validation.report.artifacts={len(ROOT_ARTIFACT_NAMES)}",
        "validation.report.diagnostics=6",
        "validation.gate.diagnostics=7",
    ]
    for index, artifact_name in enumerate(ROOT_ARTIFACT_NAMES, start=1):
        text = f"{artifact_name}: root refusal-first fixture evidence\n"
        (output_dir / artifact_name).write_text(text, encoding="utf-8")
        ordinal = f"{index:02d}"
        manifest_lines.extend(
            [
                f"artifact.{ordinal}.name={artifact_name}",
                "artifact.{ordinal}.lines=1".format(ordinal=ordinal),
                f"artifact.{ordinal}.sha256={sha256_text(text)}",
            ]
        )
    manifest_lines.extend(
        [
            "diagnostic.01=first-route PM evidence incomplete: route, resource-decision, submission, readback, pipeline-cache",
            "diagnostic.02=route requires GPURouteDecision.Native but found GPURouteDecision.Refused",
            "diagnostic.03=resource-decision requires GPUResourceMaterializationDecision.Materialized but found GPUResourceMaterializationDecision.Refused",
            "diagnostic.04=submission requires GPUCommandSubmission.Submitted but found GPUCommandSubmission.Refused",
            "diagnostic.05=readback requires GPUReadbackResult.Completed",
            "diagnostic.06=pipeline-cache requires GPUCacheTelemetry.pipeline",
            "gateDiagnostic.01=validation report status is Incomplete",
            "gateDiagnostic.02=first-route PM evidence incomplete: route, resource-decision, submission, readback, pipeline-cache",
            "gateDiagnostic.03=route requires GPURouteDecision.Native but found GPURouteDecision.Refused",
            "gateDiagnostic.04=resource-decision requires GPUResourceMaterializationDecision.Materialized but found GPUResourceMaterializationDecision.Refused",
            "gateDiagnostic.05=submission requires GPUCommandSubmission.Submitted but found GPUCommandSubmission.Refused",
            "gateDiagnostic.06=readback requires GPUReadbackResult.Completed",
            "gateDiagnostic.07=pipeline-cache requires GPUCacheTelemetry.pipeline",
        ]
    )
    (output_dir / validator.MANIFEST_ARTIFACT).write_text(exported_text(manifest_lines), encoding="utf-8")
    return output_dir


def update_manifest_artifact_digest(output_dir: Path, artifact_name: str, text: str) -> None:
    manifest_path = output_dir / validator.MANIFEST_ARTIFACT
    manifest_lines = manifest_path.read_text(encoding="utf-8").splitlines()
    artifact_index = ROOT_ARTIFACT_NAMES.index(artifact_name) + 1
    ordinal = f"{artifact_index:02d}"
    updated_lines = []
    for line in manifest_lines:
        if line.startswith(f"artifact.{ordinal}.lines="):
            updated_lines.append(f"artifact.{ordinal}.lines={validator.line_count_for_exported_text(text)}")
        elif line.startswith(f"artifact.{ordinal}.sha256="):
            updated_lines.append(f"artifact.{ordinal}.sha256={sha256_text(text)}")
        else:
            updated_lines.append(line)
    manifest_path.write_text(exported_text(updated_lines), encoding="utf-8")


class GpuRendererR6PmEvidenceValidatorTest(unittest.TestCase):
    def test_validate_output_accepts_refusal_first_bundle_with_matching_release_sidecar(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))
            _, _, entry = validator.validate_output(output_dir)
            write_json(output_dir / validator.OUTPUT_MANIFEST_ENTRY, entry)

            _, rows, validated_entry = validator.validate_output(output_dir)

        self.assertEqual(14, len(rows))
        self.assertEqual("ActivationCandidate", validated_entry["status"])
        self.assertEqual("Incomplete", validated_entry["validationReportStatus"])
        self.assertFalse(validated_entry["promotionGatePassed"])
        self.assertFalse(validated_entry["productRouteActivated"])

    def test_validate_output_builds_m1_activation_candidate_entry_without_product_activation(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))

            _, _, entry = validator.validate_output(output_dir)

        self.assertEqual("ActivationCandidate", entry["status"])
        self.assertEqual("activation-candidate", entry["packagingState"])
        self.assertEqual("Incomplete", entry["validationReportStatus"])
        self.assertEqual(
            "reports/gpu-renderer/2026-06-14-m1-promotion-policy-decision.md",
            entry["activationDecisionRef"],
        )
        self.assertEqual("opt-in-adapter-backed-r6-executed-diagnostic", entry["adapterEvidenceProvenance"])
        self.assertEqual("required-before-product-activation", entry["adapterEvidenceRequirement"])
        self.assertFalse(entry["productRouteActivated"])
        self.assertFalse(entry["releaseBlocking"])
        self.assertEqual(0.0, entry["readinessDelta"])

    def test_validate_output_rejects_unmanifested_root_bundle_files(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))
            (output_dir / "unmanifested-review-note.txt").write_text("safe-looking PM note\n", encoding="utf-8")

            with self.assertRaisesRegex(validator.ValidationError, "unexpected root PM evidence files"):
                validator.validate_output(output_dir)

    def test_validate_output_rejects_duplicate_root_manifest_keys(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))
            manifest_path = output_dir / validator.MANIFEST_ARTIFACT
            manifest_text = manifest_path.read_text(encoding="utf-8")
            manifest_path.write_text(
                manifest_text.replace(
                    "validation.gate.passed=false\n",
                    "validation.gate.passed=true\nvalidation.gate.passed=false\n",
                    1,
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "duplicate manifest key: validation.gate.passed"):
                validator.validate_output(output_dir)

    def test_validate_output_rejects_duplicate_manifest_entry_sidecar_keys(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))
            _, _, entry = validator.validate_output(output_dir)
            sidecar_text = json.dumps(entry, indent=2, sort_keys=True)
            self.assertIn('  "productRouteActivated": false,', sidecar_text)
            sidecar_text = sidecar_text.replace(
                '  "productRouteActivated": false,',
                '  "productRouteActivated": true,\n  "productRouteActivated": false,',
                1,
            )
            (output_dir / validator.OUTPUT_MANIFEST_ENTRY).write_text(sidecar_text + "\n", encoding="utf-8")

            with self.assertRaisesRegex(validator.ValidationError, "duplicate JSON key: productRouteActivated"):
                validator.validate_output(output_dir)

    def test_inject_pm_bundle_rejects_duplicate_target_manifest_keys_before_copy(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            temp_root = Path(temp)
            output_dir = write_fixture_bundle(temp_root)
            _, _, entry = validator.validate_output(output_dir)
            bundle_dir = temp_root / "pm-bundle"
            bundle_dir.mkdir()
            manifest_path = bundle_dir / "manifest.json"
            manifest_path.write_text(
                "{\n"
                '  "generatedBy": "pipelinePmBundle",\n'
                '  "gpuRendererR6FirstRoutePmEvidence": {"promotionGatePassed": true},\n'
                '  "gpuRendererR6FirstRoutePmEvidence": {"promotionGatePassed": false}\n'
                "}\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                validator.ValidationError,
                "duplicate JSON key: gpuRendererR6FirstRoutePmEvidence",
            ):
                validator.inject_pm_bundle(output_dir, bundle_dir, entry)

            self.assertFalse((bundle_dir / validator.RELEASE_DIR).exists())

    def test_validate_output_rejects_root_manifest_diagnostic_drift(self) -> None:
        unsafe_replacements = [
            (
                "validation.report.diagnostics=6",
                "validation.report.diagnostics=0",
                "root PM report diagnostic count changed",
            ),
            (
                "validation.gate.diagnostics=7",
                "validation.gate.diagnostics=0",
                "root PM gate diagnostic count changed",
            ),
            (
                "diagnostic.03=resource-decision requires GPUResourceMaterializationDecision.Materialized but found GPUResourceMaterializationDecision.Refused",
                "diagnostic.03=resource-decision skipped for local review",
                "root PM report diagnostic.03 changed",
            ),
            (
                "gateDiagnostic.06=readback requires GPUReadbackResult.Completed",
                "gateDiagnostic.06=readback evidence skipped locally",
                "root PM gate diagnostic.06 changed",
            ),
        ]

        for old, new, message in unsafe_replacements:
            with self.subTest(message=message):
                with tempfile.TemporaryDirectory() as temp:
                    output_dir = write_fixture_bundle(Path(temp))
                    manifest_path = output_dir / validator.MANIFEST_ARTIFACT
                    manifest_text = manifest_path.read_text(encoding="utf-8")
                    self.assertIn(old, manifest_text)
                    manifest_path.write_text(manifest_text.replace(old, new, 1), encoding="utf-8")

                    with self.assertRaisesRegex(validator.ValidationError, message):
                        validator.validate_output(output_dir)

    def test_validate_output_rejects_missing_evidence_order_or_duplicates(self) -> None:
        unsafe_values = [
            "pipeline-cache,readback,submission,resource-decision,route",
            "route,resource-decision,submission,readback,pipeline-cache,route",
            "route,resource-decision,submission,readback",
        ]

        for value in unsafe_values:
            with self.subTest(value=value):
                with tempfile.TemporaryDirectory() as temp:
                    output_dir = write_fixture_bundle(Path(temp))
                    manifest_path = output_dir / validator.MANIFEST_ARTIFACT
                    manifest_text = manifest_path.read_text(encoding="utf-8")
                    manifest_path.write_text(
                        manifest_text.replace(
                            "validation.gate.missingEvidence=route,resource-decision,submission,readback,pipeline-cache",
                            f"validation.gate.missingEvidence={value}",
                            1,
                        ),
                        encoding="utf-8",
                    )

                    with self.assertRaisesRegex(validator.ValidationError, "default missing evidence sequence changed"):
                        validator.validate_output(output_dir)

    def test_validate_output_rejects_unexpected_root_manifest_fields(self) -> None:
        unsafe_extra_fields = [
            "releaseStatus=ready-for-first-route",
            "artifact.15.name=extra-root-pm-claim.txt",
            "gateDiagnostic.08=promotion gate passed locally",
        ]

        for extra_field in unsafe_extra_fields:
            with self.subTest(extra_field=extra_field):
                with tempfile.TemporaryDirectory() as temp:
                    output_dir = write_fixture_bundle(Path(temp))
                    manifest_path = output_dir / validator.MANIFEST_ARTIFACT
                    manifest_text = manifest_path.read_text(encoding="utf-8")
                    manifest_path.write_text(manifest_text + extra_field + "\n", encoding="utf-8")

                    with self.assertRaisesRegex(validator.ValidationError, "root PM manifest has unexpected fields"):
                        validator.validate_output(output_dir)

    def test_validate_output_rejects_truncated_manifest_even_when_self_consistent(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))
            removed = output_dir / "gpu-renderer-first-route-pm-evidence-14-unsupported-route-refusals.txt"
            removed.unlink()
            manifest_path = output_dir / validator.MANIFEST_ARTIFACT
            kept_lines = [
                line
                for line in manifest_path.read_text(encoding="utf-8").splitlines()
                if not line.startswith("artifact.14.")
            ]
            kept_lines = [
                "validation.report.artifacts=13" if line == "validation.report.artifacts=14" else line
                for line in kept_lines
            ]
            manifest_path.write_text(exported_text(kept_lines), encoding="utf-8")

            with self.assertRaisesRegex(validator.ValidationError, "root PM manifest missing fields"):
                validator.validate_output(output_dir)

    def test_validate_output_rejects_drifted_release_sidecar_entry(self) -> None:
        unsafe_sidecars = [
            {"readinessDelta": False},
            {"supportClaim": True},
            {"activationNote": "ready for release"},
        ]

        for overrides in unsafe_sidecars:
            with self.subTest(overrides=overrides):
                with tempfile.TemporaryDirectory() as temp:
                    output_dir = write_fixture_bundle(Path(temp))
                    _, _, entry = validator.validate_output(output_dir)
                    entry.update(overrides)
                    write_json(output_dir / validator.OUTPUT_MANIFEST_ENTRY, entry)

                    with self.assertRaisesRegex(validator.ValidationError, "manifest entry sidecar"):
                        validator.validate_output(output_dir)

    def test_validate_output_rejects_root_manifest_support_claim(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))
            manifest_path = output_dir / validator.MANIFEST_ARTIFACT
            manifest_text = manifest_path.read_text(encoding="utf-8")
            manifest_path.write_text(manifest_text + "supportClaim=true\n", encoding="utf-8")

            with self.assertRaisesRegex(validator.ValidationError, "supportClaim"):
                validator.validate_output(output_dir)

    def test_validate_output_rejects_spaced_json_or_adapter_requirement_claims(self) -> None:
        artifact_name = "gpu-renderer-first-route-pm-evidence-03-route.txt"
        cases = [
            "productRouteActivated = true",
            '"releaseBlocking": true',
            "nativeKadreCiRequired = true",
            "webGpuAdapterRequired: true",
            "support_claim=true",
            "supported: true",
        ]

        for claim in cases:
            with self.subTest(claim=claim):
                with tempfile.TemporaryDirectory() as temp:
                    output_dir = write_fixture_bundle(Path(temp))
                    contaminated_text = exported_text(
                        [
                            f"{artifact_name}: root refusal-first fixture evidence",
                            claim,
                        ]
                    )
                    (output_dir / artifact_name).write_text(contaminated_text, encoding="utf-8")
                    update_manifest_artifact_digest(output_dir, artifact_name, contaminated_text)

                    with self.assertRaisesRegex(validator.ValidationError, "forbidden product claim"):
                        validator.validate_output(output_dir)

    def test_validate_output_rejects_root_artifact_content_contamination(self) -> None:
        contamination_markers = [
            "gpuRendererShadow v=1",
            "kanvas.gpu.renderer.shadow.fillRect",
            "routing:GPURouteDecision.Native:",
            "resources:GPUResourceMaterializationDecision.Materialized:",
            "GPUCommandSubmission.Submitted",
            "GPUReadbackResult.Completed",
            "readbackBytes",
            "rawPixels",
            "supportClaim",
        ]
        artifact_name = "gpu-renderer-first-route-pm-evidence-09-submission.txt"

        for marker in contamination_markers:
            with self.subTest(marker=marker):
                with tempfile.TemporaryDirectory() as temp:
                    output_dir = write_fixture_bundle(Path(temp))
                    contaminated_text = exported_text(
                        [
                            f"{artifact_name}: root refusal-first fixture evidence",
                            f"contaminatedReviewLine={marker}",
                        ]
                    )
                    (output_dir / artifact_name).write_text(contaminated_text, encoding="utf-8")
                    update_manifest_artifact_digest(output_dir, artifact_name, contaminated_text)

                    with self.assertRaisesRegex(validator.ValidationError, marker):
                        validator.validate_output(output_dir)

    def test_validate_output_rejects_root_pipeline_cache_positive_evidence(self) -> None:
        artifact_name = "gpu-renderer-first-route-pm-evidence-12-pipeline-cache.txt"
        contaminated_text = exported_text(
            [
                "telemetry:GPUCacheTelemetry.pipeline:event:pipeline:Miss:"
                "key=render-pipeline:first-fill-rect:subject=webgpu:first-route-submit",
            ]
        )

        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))
            (output_dir / artifact_name).write_text(contaminated_text, encoding="utf-8")
            update_manifest_artifact_digest(output_dir, artifact_name, contaminated_text)

            with self.assertRaisesRegex(validator.ValidationError, "GPUCacheTelemetry.pipeline"):
                validator.validate_output(output_dir)


if __name__ == "__main__":
    unittest.main()
