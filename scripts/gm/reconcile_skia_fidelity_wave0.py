#!/usr/bin/env python3
"""Reconcile Skia, SVG, dashboard, score, and FP-13 evidence without rewriting inputs."""

import argparse
import hashlib
import json
import pathlib
import re
import xml.etree.ElementTree as ET


SCHEMA_VERSION = 1
KIND = "skia-fidelity-wave0-reconciliation"
GENERATED_BY = "reconcile_skia_fidelity_wave0.py"
SESSION_CLOSE_CODE = "failed.surface.prepared.session-close"
EXPECTED_UNSUPPORTED_CODES = {
    "unsupported.core_primitive.geometry.invalid",
    "unsupported.material.linear_gradient_capability_missing",
    "unsupported.geometry.path_key_nondeterministic",
    "unsupported.core_primitive.stencil_edge_fan_budget",
}
FAILURE_CODE_PATTERN = re.compile(
    r"(?<![A-Za-z0-9_.-])(?:unsupported|failed)\.[A-Za-z0-9_.-]+"
)


def _local_name(tag):
    return tag.rsplit("}", 1)[-1]


def _element_message(element):
    message = element.attrib.get("message", "").strip()
    if message:
        return message
    return " ".join(part.strip() for part in element.itertext() if part.strip())


def _failure_code(message, element, outcome):
    match = FAILURE_CODE_PATTERN.search(message or "")
    if match:
        return match.group(0)
    exception_type = element.attrib.get("type", "")
    if outcome == "skipped" and (
        "TestAbortedException" in exception_type or "TestAbortedException" in message
    ):
        return "TestAbortedException"
    return None


def _is_missing_reference(message):
    lowered = message.lower()
    return (
        "missing reference" in lowered
        or "missing-reference" in lowered
        or "reference png not found" in lowered
        or "reference image not found" in lowered
    )


def _is_size_mismatch(message):
    lowered = message.lower()
    return (
        "size mismatch" in lowered
        or "size-mismatch" in lowered
        or "buffer sizes differ" in lowered
        or "sizes differ" in lowered
    )


def _is_similarity_failure(message):
    lowered = message.lower()
    return (
        "similarity" in lowered
        and any(token in lowered for token in ("below", "threshold", "failed", "failure"))
    ) or "below-threshold" in lowered


def _classify_row(outcome, message, failure_code, failure_type):
    expected_unsupported = failure_code in EXPECTED_UNSUPPORTED_CODES
    missing_reference = _is_missing_reference(message)
    size_mismatch = _is_size_mismatch(message)
    similarity_failure = _is_similarity_failure(message)
    lifecycle_failure = bool(
        failure_code == SESSION_CLOSE_CODE
        or "session-close" in message.lower()
        or "lifecycle" in message.lower()
    )
    terminal = bool(
        lifecycle_failure
        or "terminal" in failure_type.lower()
        or "terminal" in message.lower()
        or failure_code == "unsupported.frame_memory.aggregate_budget_exceeded"
    )

    if outcome == "skipped":
        classification = "skip"
    elif expected_unsupported:
        classification = "expected-unsupported"
    elif lifecycle_failure:
        classification = "lifecycle-failure"
    elif missing_reference:
        classification = "missing-reference"
    elif size_mismatch:
        classification = "size-mismatch"
    elif similarity_failure:
        classification = "similarity-failure"
    elif terminal:
        classification = "terminal-failure"
    elif outcome in {"failure", "error"}:
        classification = "unclassified"
    else:
        classification = "pass"

    return {
        "classification": classification,
        "terminal": terminal,
        "expectedUnsupported": expected_unsupported,
        "missingReference": missing_reference,
        "sizeMismatch": size_mismatch,
        "similarityFailure": similarity_failure,
        "lifecycleFailure": lifecycle_failure,
    }


def _parse_junit(path):
    root = ET.parse(path).getroot()
    rows = []
    for testcase in root.iter():
        if _local_name(testcase.tag) != "testcase":
            continue
        failure = next(
            (child for child in testcase if _local_name(child.tag) == "failure"), None
        )
        error = next(
            (child for child in testcase if _local_name(child.tag) == "error"), None
        )
        skipped = next(
            (child for child in testcase if _local_name(child.tag) == "skipped"), None
        )
        detail = (
            failure
            if failure is not None
            else error
            if error is not None
            else skipped
        )
        if failure is not None:
            outcome = "failure"
        elif error is not None:
            outcome = "error"
        elif skipped is not None:
            outcome = "skipped"
        else:
            outcome = "passed"

        message = _element_message(detail) if detail is not None else ""
        failure_type = detail.attrib.get("type", "") if detail is not None else ""
        code = _failure_code(message, detail, outcome) if detail is not None else None
        row = {
            "name": testcase.attrib.get("name", ""),
            "class": testcase.attrib.get("classname", testcase.attrib.get("class", "")),
            "className": testcase.attrib.get("classname", testcase.attrib.get("class", "")),
            "outcome": outcome,
            "message": message,
            "failureType": failure_type,
            "failureCode": code,
        }
        row.update(_classify_row(outcome, message, code, failure_type))
        rows.append(row)

    declared = {}
    for field in ("tests", "failures", "errors", "skipped"):
        value = root.attrib.get(field)
        try:
            declared[field] = int(value) if value is not None else None
        except ValueError:
            declared[field] = None

    counts = {
        "tests": declared["tests"] if declared["tests"] is not None else len(rows),
        "failures": (
            declared["failures"]
            if declared["failures"] is not None
            else sum(row["outcome"] == "failure" for row in rows)
        ),
        "errors": (
            declared["errors"]
            if declared["errors"] is not None
            else sum(row["outcome"] == "error" for row in rows)
        ),
        "skips": (
            declared["skipped"]
            if declared["skipped"] is not None
            else sum(row["outcome"] == "skipped" for row in rows)
        ),
    }
    counts["unexpectedFailures"] = sum(
        row["outcome"] in {"failure", "error"}
        and row["classification"] in {"terminal-failure", "unclassified"}
        for row in rows
    )
    counts["unclassifiedFailures"] = sum(
        row["classification"] == "unclassified" for row in rows
    )
    counts["terminalFailures"] = sum(row["terminal"] for row in rows)
    counts["expectedUnsupported"] = sum(row["expectedUnsupported"] for row in rows)
    counts["missingReferences"] = sum(row["missingReference"] for row in rows)
    counts["sizeMismatches"] = sum(row["sizeMismatch"] for row in rows)
    counts["similarityFailures"] = sum(row["similarityFailure"] for row in rows)
    counts["lifecycleFailures"] = sum(row["lifecycleFailure"] for row in rows)
    return {**counts, "rows": rows}


def parse_skia_runner(path: pathlib.Path) -> dict:
    """Parse the current or historical Skia GM JUnit XML."""
    return _parse_junit(path)


def parse_dashboard(path: pathlib.Path) -> dict:
    """Load the committed dashboard JSON while retaining its dashboard shape."""
    value = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(value, list):
        return {"gms": value}
    if not isinstance(value, dict):
        raise ValueError("dashboard JSON must contain an object or array")
    if "gms" in value and not isinstance(value["gms"], (list, dict)):
        raise ValueError("dashboard gms must contain an array or object")
    if "gms" not in value:
        for key in ("rows", "results"):
            if isinstance(value.get(key), list):
                value = {**value, "gms": value[key]}
                break
    return value


def parse_svg_results(path: pathlib.Path) -> dict:
    """Parse SVG integration JUnit XML using the same row policy as Skia."""
    return _parse_junit(path)


def load_scores(path: pathlib.Path) -> dict[str, float]:
    """Load numeric score properties without changing the source file."""
    scores = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        separator = re.search(r"[:=]", line)
        if separator is None:
            continue
        key = line[: separator.start()].strip()
        value = line[separator.end() :].strip()
        if not key or not value:
            continue
        try:
            scores[key] = float(value)
        except ValueError as error:
            raise ValueError("invalid score on line %s: %s" % (line_number, raw_line)) from error
    return scores


def _dashboard_entries(dashboard):
    if dashboard is None:
        return []
    if isinstance(dashboard, list):
        entries = dashboard
    elif isinstance(dashboard, dict):
        entries = dashboard.get("gms", dashboard.get("rows", dashboard.get("results", [])))
        if isinstance(entries, dict):
            entries = [dict(value, name=name) if isinstance(value, dict) else {"name": name, "value": value}
                       for name, value in entries.items()]
    else:
        entries = []
    if not isinstance(entries, list):
        return []

    rows = []
    for entry in entries:
        if isinstance(entry, dict):
            row = dict(entry)
            name = row.get("name", row.get("gm", row.get("id", "")))
        else:
            name = entry
            row = {}
        name = str(name)
        row["name"] = name
        if not row.get("referenceKind"):
            row["referenceKind"] = (
                "skia-reference"
                if row.get("reference") or row.get("referencePath") or row.get("referenceImage")
                else "unknown"
            )
        rows.append(row)
    return rows


def _oracle_entries(rows):
    if rows is None:
        return []
    if isinstance(rows, dict):
        rows = rows.get("rows", [])
    if not isinstance(rows, list):
        return []
    result = []
    for entry in rows:
        if isinstance(entry, dict):
            row = dict(entry)
            row["name"] = str(row.get("name", row.get("gm", row.get("id", ""))))
        else:
            row = {"name": str(entry)}
        row["referenceKind"] = "cpu-oracle"
        result.append(row)
    return result


def _runner_entries(runner):
    if not isinstance(runner, dict):
        return []
    return [dict(row) for row in runner.get("rows", []) if isinstance(row, dict)]


def _summary(value, row_key="rows"):
    if not isinstance(value, dict):
        return {"rows": 0}
    result = {}
    for key in (
        "tests",
        "failures",
        "errors",
        "skips",
        "unexpectedFailures",
        "unclassifiedFailures",
        "terminalFailures",
        "expectedUnsupported",
        "missingReferences",
        "sizeMismatches",
        "similarityFailures",
        "lifecycleFailures",
    ):
        if key in value:
            result[key] = value[key]
    result["rows"] = len(value.get(row_key, [])) if isinstance(value.get(row_key, []), list) else 0
    return result


def _copy_input(value):
    if isinstance(value, dict):
        return dict(value)
    if isinstance(value, list):
        return {"rows": list(value)}
    if value is None:
        return {"present": False}
    return {"value": value}


def _names(rows):
    return {str(row.get("name", "")) for row in rows if isinstance(row, dict)}


def _lane_delta(left, right):
    left_names = _names(left)
    right_names = _names(right)
    return {
        "leftOnly": sorted(left_names - right_names),
        "rightOnly": sorted(right_names - left_names),
        "shared": sorted(left_names & right_names),
    }


def build_delta(inputs: dict, source_commit: str) -> dict:
    """Build a versioned, read-only reconciliation delta from parsed inputs."""
    dashboard = inputs.get("dashboard")
    runner = inputs.get("skiaRunner", inputs.get("runner"))
    svg = inputs.get("svg", inputs.get("svgResults"))
    scores = inputs.get("scores", {})
    fp13 = inputs.get("fp13")

    skia_rows = _dashboard_entries(dashboard)
    if not skia_rows:
        skia_rows = _runner_entries(runner)
        for row in skia_rows:
            row.setdefault("referenceKind", "unknown")
    if isinstance(scores, dict):
        for row in skia_rows:
            if row.get("name") in scores and "score" not in row and "similarity" not in row:
                row["score"] = scores[row["name"]]
    svg_rows = _runner_entries(svg)
    cpu_rows = _oracle_entries(inputs.get("cpuOracleRows", inputs.get("cpuOracle")))
    rows = {"skia": skia_rows, "svg": svg_rows, "cpuOracle": cpu_rows}

    fp13_input = _copy_input(fp13)
    fp13_input["acceptanceBaseline"] = False
    fp13_summary = _summary(fp13)
    runner_summary = _summary(runner)
    svg_summary = _summary(svg)
    historical = {
        **fp13_summary,
        "acceptanceBaseline": False,
        "failureDeltaFromCurrent": runner_summary.get("failures", 0)
        - fp13_summary.get("failures", 0),
        "readinessDelta": 0.0,
    }
    policy = {
        "globalThresholdWeakened": False,
        "scoresDirectlyEdited": False,
        "readinessDelta": 0.0,
    }
    return {
        "schemaVersion": SCHEMA_VERSION,
        "kind": KIND,
        "generatedBy": GENERATED_BY,
        "sourceCommit": source_commit,
        "policy": policy,
        "inputs": {
            "skiaRunner": _copy_input(runner),
            "dashboard": _copy_input(dashboard),
            "svg": _copy_input(svg),
            "scores": _copy_input(scores),
            "fp13": fp13_input,
            "fileMetadata": _copy_input(inputs.get("fileMetadata")),
        },
        "current": {
            "skia": {"runner": runner_summary, "dashboardRows": len(skia_rows)},
            "svg": svg_summary,
            "scores": {"rows": len(scores) if isinstance(scores, dict) else 0},
            "cpuOracle": {"rows": len(cpu_rows)},
        },
        "crossLaneDelta": {
            "skiaVsSvg": _lane_delta(skia_rows, svg_rows),
            "skiaVsCpuOracle": _lane_delta(skia_rows, cpu_rows),
        },
        "historicalFp13Delta": historical,
        "rows": rows,
        "nonClaims": [
            "FP-13 is historical context only and is not an acceptance baseline.",
            "Skia, SVG, and CPU-oracle rows are separate evidence lanes.",
            "This report does not weaken global thresholds or edit score inputs.",
        ],
    }


def _markdown_value(value):
    return str(value).replace("|", r"\|").replace("\n", " ")


def render_markdown(delta: dict) -> str:
    """Render the delta as a compact Markdown evidence report."""
    policy = delta.get("policy", {})
    lines = [
        "# Skia Fidelity Wave 0 Reconciliation",
        "",
        "- schemaVersion: `%s`" % _markdown_value(delta.get("schemaVersion")),
        "- kind: `%s`" % _markdown_value(delta.get("kind")),
        "- sourceCommit: `%s`" % _markdown_value(delta.get("sourceCommit")),
        "",
        "## Policy",
        "",
        "- globalThresholdWeakened: `%s`" % policy.get("globalThresholdWeakened", False),
        "- scoresDirectlyEdited: `%s`" % policy.get("scoresDirectlyEdited", False),
        "- readinessDelta: `%s`" % policy.get("readinessDelta", 0.0),
        "",
        "## Current",
        "",
        "| Lane | Tests/Rows | Failures | Errors | Skips |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    current = delta.get("current", {})
    for lane in ("skia", "svg"):
        summary = current.get(lane, {})
        if lane == "skia":
            summary = summary.get("runner", {})
        lines.append(
            "| `%s` | %s | %s | %s | %s |"
            % (
                lane,
                summary.get("tests", summary.get("rows", 0)),
                summary.get("failures", 0),
                summary.get("errors", 0),
                summary.get("skips", 0),
            )
        )
    lines.extend(
        [
            "",
            "## Rows",
            "",
            "| Lane | Name | Outcome | Classification | Reference |",
            "| --- | --- | --- | --- | --- |",
        ]
    )
    for lane in ("skia", "svg", "cpuOracle"):
        for row in delta.get("rows", {}).get(lane, []):
            lines.append(
                "| `%s` | `%s` | %s | %s | %s |"
                % (
                    lane,
                    _markdown_value(row.get("name", "")),
                    _markdown_value(row.get("outcome", "")),
                    _markdown_value(row.get("classification", "")),
                    _markdown_value(row.get("referenceKind", "")),
                )
            )
    historical = delta.get("historicalFp13Delta", {})
    lines.extend(
        [
            "",
            "## Historical FP-13 Context",
            "",
            "- acceptanceBaseline: `%s`" % historical.get("acceptanceBaseline", False),
            "- failures: `%s`" % historical.get("failures", 0),
            "- readinessDelta: `%s`" % historical.get("readinessDelta", 0.0),
            "",
            "## Non-claims",
            "",
        ]
    )
    lines.extend("- " + claim for claim in delta.get("nonClaims", []))
    return "\n".join(lines) + "\n"


def _sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _argument_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skia-runner", required=True, type=pathlib.Path, help="current SkiaGmRunner.xml")
    parser.add_argument("--dashboard-json", required=True, type=pathlib.Path, help="current Skia dashboard data/gms.json")
    parser.add_argument("--svg-xml", required=True, type=pathlib.Path, help="current SVG JUnit XML")
    parser.add_argument("--scores", required=True, type=pathlib.Path, help="current score properties")
    parser.add_argument("--fp13-runner", required=True, type=pathlib.Path, help="historical FP-13 runner XML")
    parser.add_argument("--source-commit", required=True, help="source commit SHA")
    parser.add_argument("--output-json", required=True, type=pathlib.Path, help="versioned JSON delta output")
    parser.add_argument("--output-markdown", required=True, type=pathlib.Path, help="Markdown report output")
    parser.add_argument("--check", action="store_true", help="fail on missing or unclassified current evidence")
    return parser


def _check_violations(runner, svg, missing):
    violations = ["missing input: %s" % path for path in missing]
    for lane, result in (("skia", runner), ("svg", svg)):
        if result.get("unclassifiedFailures", 0):
            violations.append(
                "%s has %s unclassified failure/error testcase(s)"
                % (lane, result["unclassifiedFailures"])
            )
        if any(row.get("failureCode") == SESSION_CLOSE_CODE for row in result.get("rows", [])):
            violations.append("%s retains %s" % (lane, SESSION_CLOSE_CODE))
    return violations


def main(argv=None):
    parser = _argument_parser()
    args = parser.parse_args(argv)
    paths = {
        "skiaRunner": args.skia_runner,
        "dashboard": args.dashboard_json,
        "svg": args.svg_xml,
        "scores": args.scores,
        "fp13": args.fp13_runner,
    }
    missing = [path for path in paths.values() if not path.is_file()]
    output_paths = {args.output_json.resolve(), args.output_markdown.resolve()}
    input_paths = {path.resolve() for path in paths.values()}
    if output_paths & input_paths:
        print("reconciliation failed: output path would overwrite an input")
        return 2
    if args.output_json.resolve() == args.output_markdown.resolve():
        print("reconciliation failed: JSON and Markdown outputs must differ")
        return 2
    if missing:
        if args.check:
            print("reconciliation check failed: missing input: %s" % ", ".join(map(str, missing)))
        else:
            print("reconciliation failed: missing input: %s" % ", ".join(map(str, missing)))
        return 2

    try:
        runner = parse_skia_runner(paths["skiaRunner"])
        dashboard = parse_dashboard(paths["dashboard"])
        svg = parse_svg_results(paths["svg"])
        scores = load_scores(paths["scores"])
        fp13 = parse_skia_runner(paths["fp13"])
        file_metadata = {
            key: {"path": str(path), "sha256": _sha256(path)}
            for key, path in paths.items()
        }
        delta = build_delta(
            {
                "skiaRunner": runner,
                "dashboard": dashboard,
                "svg": svg,
                "scores": scores,
                "fp13": fp13,
                "fileMetadata": file_metadata,
            },
            args.source_commit,
        )
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_markdown.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(delta, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        args.output_markdown.write_text(render_markdown(delta), encoding="utf-8")
    except (OSError, ValueError, ET.ParseError, json.JSONDecodeError) as error:
        print("reconciliation failed: %s" % error)
        return 2

    if args.check:
        violations = _check_violations(runner, svg, [])
        if violations:
            print("reconciliation check failed: %s" % "; ".join(violations))
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
