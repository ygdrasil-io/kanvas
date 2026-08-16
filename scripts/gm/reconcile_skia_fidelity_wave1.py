#!/usr/bin/env python3
"""Reconcile the Skia fidelity Wave 1 evidence without rewriting inputs."""

import argparse
import hashlib
import json
import pathlib
import re
import xml.etree.ElementTree as ET


SCHEMA_VERSION = 1
KIND = "skia-fidelity-wave-1"
GENERATED_BY = "reconcile_skia_fidelity_wave1.py"
EXPECTED_UNSUPPORTED_CODES = {
    "unsupported.core_primitive.geometry.invalid",
    "unsupported.material.linear_gradient_capability_missing",
    "unsupported.geometry.path_key_nondeterministic",
    "unsupported.core_primitive.stencil_edge_fan_budget",
}
FAILURE_CODE_PATTERN = re.compile(
    r"(?<![A-Za-z0-9_.-])(?:unsupported|failed|invalid|stale)\.[A-Za-z0-9_.-]+"
)
SCORE_KEYS = ("modecolorfilters",)


def _local_name(tag):
    return tag.rsplit("}", 1)[-1]


def _element_message(element):
    if element is None:
        return ""
    message = element.attrib.get("message", "").strip()
    if message:
        return message
    return " ".join(part.strip() for part in element.itertext() if part.strip())


def _failure_code(message, element, outcome, expected_codes):
    candidates = [message or ""]
    if element is not None:
        candidates.extend(element.attrib.values())
        candidates.extend(element.itertext())
    for candidate in candidates:
        for expected_code in sorted(expected_codes, key=len, reverse=True):
            if expected_code and expected_code in candidate:
                return expected_code
        match = FAILURE_CODE_PATTERN.search(candidate)
        if match:
            return match.group(0)
    exception_type = element.attrib.get("type", "") if element is not None else ""
    if "TestAbortedException" in exception_type or "TestAbortedException" in message:
        return "TestAbortedException"
    if outcome == "skipped" and "aborted" in message.lower():
        return "TestAbortedException"
    return None


def _is_missing_reference(message):
    lowered = message.lower()
    return (
        "missing reference" in lowered
        or "missing-reference" in lowered
        or "reference png not found" in lowered
        or "reference image not found" in lowered
        or "reference file not found" in lowered
        or "no reference" in lowered
    )


def _is_size_mismatch(message):
    lowered = message.lower()
    return (
        "size mismatch" in lowered
        or "size-mismatch" in lowered
        or "buffer sizes differ" in lowered
        or "sizes differ" in lowered
        or "dimension mismatch" in lowered
        or "dimensions differ" in lowered
    )


def _is_similarity_failure(message):
    lowered = message.lower()
    return (
        "similarity" in lowered
        and any(token in lowered for token in ("below", "threshold", "failed", "failure"))
    ) or "below-threshold" in lowered


def _is_lifecycle_failure(message, failure_code):
    lowered = message.lower()
    return bool(
        failure_code == "failed.surface.prepared.session-close"
        or "session-close" in lowered
        or "lifecycle" in lowered
        or "teardown" in lowered
        or "shutdown" in lowered
    )


def _is_terminal_failure(message, failure_type, failure_code):
    lowered_message = message.lower()
    lowered_type = failure_type.lower()
    return bool(
        "terminal" in lowered_type
        or "terminal" in lowered_message
        or "refusal" in lowered_message
        or "aggregate_budget_exceeded" in (failure_code or "")
    )


def _classify_row(outcome, message, failure_code, failure_type, expected_codes):
    expected_unsupported = failure_code in expected_codes
    missing_reference = _is_missing_reference(message)
    size_mismatch = _is_size_mismatch(message)
    similarity_failure = _is_similarity_failure(message)
    lifecycle_failure = _is_lifecycle_failure(message, failure_code)
    terminal_refusal = _is_terminal_failure(message, failure_type, failure_code)

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
    elif terminal_refusal:
        classification = "terminal-refusal"
    elif outcome in {"failure", "error"}:
        classification = "unclassified"
    else:
        classification = "pass"

    return {
        "classification": classification,
        "terminal": terminal_refusal,
        "terminalRefusal": terminal_refusal,
        "expectedUnsupported": expected_unsupported,
        "expectedRefusal": expected_unsupported,
        "missingReference": missing_reference,
        "sizeMismatch": size_mismatch,
        "similarityFailure": similarity_failure,
        "lifecycleFailure": lifecycle_failure,
    }


def parse_junit(path: pathlib.Path, suite: str, expected_codes: set[str]) -> dict:
    """Parse JUnit testcase rows and classify failures without changing the XML."""
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

        message = _element_message(detail)
        failure_type = detail.attrib.get("type", "") if detail is not None else ""
        failure_code = (
            _failure_code(message, detail, outcome, expected_codes)
            if detail is not None
            else None
        )
        class_name = testcase.attrib.get("classname", testcase.attrib.get("class", ""))
        row = {
            "name": testcase.attrib.get("name", ""),
            "class": class_name,
            "className": class_name,
            "suite": suite,
            "evidenceLane": suite,
            "outcome": outcome,
            "message": message,
            "failureType": failure_type,
            "failureCode": failure_code,
        }
        row.update(
            _classify_row(
                outcome,
                message,
                failure_code,
                failure_type,
                expected_codes,
            )
        )
        rows.append(row)

    declared = {}
    for field in ("tests", "failures", "errors", "skipped"):
        value = root.attrib.get(field)
        try:
            declared[field] = int(value) if value is not None else None
        except (TypeError, ValueError):
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
    counts["passed"] = sum(row["outcome"] == "passed" for row in rows)
    counts["unexpectedFailures"] = sum(
        row["outcome"] in {"failure", "error"}
        and not row["expectedUnsupported"]
        and row["classification"] != "skip"
        for row in rows
    )
    counts["unclassifiedFailures"] = sum(
        row["classification"] == "unclassified" for row in rows
    )
    counts["terminalFailures"] = sum(row["terminalRefusal"] for row in rows)
    counts["expectedUnsupported"] = sum(row["expectedUnsupported"] for row in rows)
    counts["missingReferences"] = sum(row["missingReference"] for row in rows)
    counts["sizeMismatches"] = sum(row["sizeMismatch"] for row in rows)
    counts["similarityFailures"] = sum(row["similarityFailure"] for row in rows)
    counts["lifecycleFailures"] = sum(row["lifecycleFailure"] for row in rows)
    return {**counts, "suite": suite, "rows": rows}


def parse_dashboard(path: pathlib.Path) -> dict:
    """Load dashboard JSON while accepting the common gms/rows/results shapes."""
    value = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(value, list):
        return {"gms": value}
    if not isinstance(value, dict):
        raise ValueError("dashboard JSON must contain an object or array")
    if "gms" in value and not isinstance(value["gms"], (list, dict)):
        raise ValueError("dashboard gms must contain an array or object")
    if "rows" in value and not isinstance(value["rows"], list):
        raise ValueError("dashboard rows must contain an array")
    if "results" in value and not isinstance(value["results"], list):
        raise ValueError("dashboard results must contain an array")
    if "gms" not in value:
        for key in ("rows", "results"):
            if isinstance(value.get(key), list):
                value = {**value, "gms": value[key]}
                break
    return value


def _logical_property_lines(lines):
    logical = ""
    continued = False
    for raw_line in lines:
        line = raw_line.rstrip("\r")
        if continued:
            line = line.lstrip(" \t\f")
        logical += line
        trailing_backslashes = 0
        for character in reversed(logical):
            if character != "\\":
                break
            trailing_backslashes += 1
        if trailing_backslashes % 2:
            logical = logical[:-1]
            continued = True
        else:
            yield logical
            logical = ""
            continued = False
    if continued:
        yield logical


def _split_property(line):
    line = line.lstrip(" \t\f")
    if not line or line[0] in "#!":
        return None
    escaped = False
    separator_index = None
    separator = None
    for index, character in enumerate(line):
        if escaped:
            escaped = False
            continue
        if character == "\\":
            escaped = True
            continue
        if character in "=: \t\f":
            separator_index = index
            separator = character
            break
    if separator_index is None:
        return line, ""
    key = line[:separator_index]
    value_index = separator_index
    if separator in " \t\f":
        while value_index < len(line) and line[value_index] in " \t\f":
            value_index += 1
        if value_index < len(line) and line[value_index] in "=:":
            value_index += 1
    else:
        value_index += 1
    while value_index < len(line) and line[value_index] in " \t\f":
        value_index += 1
    return key, line[value_index:]


def _unescape_property(value):
    result = []
    index = 0
    escapes = {"t": "\t", "n": "\n", "r": "\r", "f": "\f"}
    while index < len(value):
        character = value[index]
        if character != "\\" or index + 1 == len(value):
            result.append(character)
            index += 1
            continue
        escaped = value[index + 1]
        if escaped == "u" and index + 5 < len(value):
            digits = value[index + 2 : index + 6]
            if re.fullmatch(r"[0-9A-Fa-f]{4}", digits):
                result.append(chr(int(digits, 16)))
                index += 6
                continue
        result.append(escapes.get(escaped, escaped))
        index += 2
    return "".join(result)


def load_scores(path: pathlib.Path) -> dict[str, float]:
    """Load numeric score properties without changing the source file."""
    scores = {}
    for line_number, line in enumerate(
        _logical_property_lines(path.read_text(encoding="utf-8").splitlines()), 1
    ):
        property_pair = _split_property(line)
        if property_pair is None:
            continue
        key, value = property_pair
        key = _unescape_property(key).strip()
        value = _unescape_property(value).strip()
        if not key or not value:
            continue
        try:
            scores[key] = float(value)
        except ValueError as error:
            raise ValueError("invalid score on line %s: %s" % (line_number, line)) from error
    return scores


def _sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            chunk = stream.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def _sha256_path(path):
    if path.is_file():
        return _sha256_file(path)
    if path.is_dir():
        digest = hashlib.sha256()
        for child in sorted(path.rglob("*")):
            if not child.is_file():
                continue
            digest.update(str(child.relative_to(path)).encode("utf-8"))
            digest.update(b"\0")
            digest.update(child.read_bytes())
            digest.update(b"\0")
        return digest.hexdigest()
    raise FileNotFoundError(str(path))


def hash_files(paths: dict[str, pathlib.Path]) -> dict[str, dict[str, str]]:
    """Return stable path and SHA-256 metadata for files or directories."""
    result = {}
    for name, path in paths.items():
        path = pathlib.Path(path)
        result[name] = {
            "path": str(path),
            "sha256": _sha256_path(path),
        }
    return result


def _json_rows(value):
    if isinstance(value, list):
        return value
    if isinstance(value, dict):
        for key in ("rows", "gms", "results"):
            if isinstance(value.get(key), list):
                return value[key]
    return []


def _dashboard_entries(dashboard):
    entries = _json_rows(dashboard)
    if isinstance(dashboard, dict) and isinstance(dashboard.get("gms"), dict):
        entries = [
            dict(entry, name=name) if isinstance(entry, dict) else {"name": name, "value": entry}
            for name, entry in dashboard["gms"].items()
        ]
    rows = []
    for entry in entries:
        if isinstance(entry, dict):
            row = dict(entry)
            name = row.get("name", row.get("gm", row.get("id", "")))
        else:
            row = {}
            name = entry
        row["name"] = str(name)
        if not row.get("referenceKind"):
            row["referenceKind"] = (
                "skia-upstream"
                if row.get("reference")
                or row.get("referencePath")
                or row.get("referenceImage")
                else "unknown"
            )
        row["evidenceLane"] = "skia-dashboard"
        row.setdefault("routeOnly", False)
        row["classification"] = _dashboard_classification(row)
        rows.append(row)
    return rows


def _dashboard_classification(row):
    cause = str(row.get("noScoreCause") or "").strip().lower()
    if row.get("routeOnly"):
        return "route-only"
    if row.get("renderFailed"):
        return "failure"
    if row.get("noReference") or cause in {"reference-missing", "missing-reference"}:
        return "missing-reference"
    if row.get("sizeMismatch") or cause in {"size-mismatch", "dimension-mismatch"}:
        return "size-mismatch"
    if row.get("referenceUntrustable") or cause == "reference-untrustable":
        return "expected-unsupported"
    if row.get("isPassing") is False:
        return "similarity-failure"
    if row.get("isPassing") is True:
        return "pass"
    score = _numeric(row.get("score", row.get("similarity")))
    if score is not None:
        return "pass" if score >= 95.0 else "similarity-failure"
    return "no-score"


def _oracle_entries(value, lane, reference_kind):
    rows = []
    for entry in _json_rows(value):
        if isinstance(entry, dict):
            row = dict(entry)
            row["name"] = str(row.get("name", row.get("gm", row.get("id", ""))))
        else:
            row = {"name": str(entry)}
        row.setdefault("referenceKind", reference_kind)
        row["evidenceLane"] = lane
        row.setdefault("routeOnly", row["name"].lower() == "route-only")
        if "classification" not in row:
            row["classification"] = "route-only" if row["routeOnly"] else "pass"
        rows.append(row)
    return rows


def _summary(value, row_key="rows"):
    if not isinstance(value, dict):
        return {"rows": 0}
    result = {}
    for key in (
        "tests",
        "failures",
        "errors",
        "skips",
        "passed",
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
    rows = value.get(row_key, [])
    result["rows"] = len(rows) if isinstance(rows, list) else 0
    return result


def _numeric(value):
    if isinstance(value, bool) or value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value))
    except (TypeError, ValueError):
        return None


def _copy_value(value):
    if isinstance(value, dict):
        return {key: _copy_value(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_copy_value(item) for item in value]
    return value


def _evidence_entries(value):
    if isinstance(value, dict):
        entries = value.get("entries", value.get("rows", []))
    else:
        entries = value
    if not isinstance(entries, list):
        return []
    return [dict(entry) for entry in entries if isinstance(entry, dict)]


def _entry_key(row):
    return str(row.get("name", "")), str(row.get("referenceKind", ""))


def _evidence_for_row(row, entries):
    key = _entry_key(row)
    for entry in entries:
        if _entry_key(entry) == key:
            return entry
    for entry in entries:
        if str(entry.get("name", "")) == str(row.get("name", "")):
            return entry
    return None


def _dimension_pair(value):
    if not isinstance(value, dict):
        return None
    width = _numeric(value.get("width", value.get("w")))
    height = _numeric(value.get("height", value.get("h")))
    if width is None or height is None:
        return None
    return int(width), int(height)


def _entry_dimensions(entry):
    dimensions = entry.get("dimensions")
    if isinstance(dimensions, dict):
        render = _dimension_pair(dimensions.get("render", dimensions.get("generated")))
        reference = _dimension_pair(dimensions.get("reference", dimensions.get("ref")))
        if render or reference:
            return render, reference
        pair = _dimension_pair(dimensions)
        if pair:
            return pair, pair
    render = _dimension_pair(entry.get("renderDimensions", entry.get("generatedDimensions")))
    reference = _dimension_pair(entry.get("referenceDimensions"))
    if render or reference:
        return render, reference
    width = _numeric(entry.get("width", entry.get("renderWidth")))
    height = _numeric(entry.get("height", entry.get("renderHeight")))
    reference_width = _numeric(entry.get("referenceWidth", width))
    reference_height = _numeric(entry.get("referenceHeight", height))
    if width is None or height is None:
        return None, None
    return (int(width), int(height)), (
        int(reference_width),
        int(reference_height),
    )


def _valid_comparable(row, evidence):
    if row.get("referenceKind") != "skia-upstream":
        return False
    if row.get("classification") in {
        "missing-reference",
        "size-mismatch",
        "expected-unsupported",
        "no-score",
    }:
        return False
    if row.get("sizeMismatch") or row.get("noReference"):
        return False
    score = _numeric(row.get("score", row.get("similarity")))
    if score is None and row.get("isPassing") is None:
        return False
    if evidence is None:
        return False
    row_render_dimensions, row_reference_dimensions = _entry_dimensions(row)
    if (
        row_render_dimensions
        and row_reference_dimensions
        and row_render_dimensions != row_reference_dimensions
    ):
        return False
    render_dimensions, reference_dimensions = _entry_dimensions(evidence)
    if render_dimensions and reference_dimensions and render_dimensions != reference_dimensions:
        return False
    if evidence.get("comparable") is False:
        return False
    return True


def _route_only_names(lanes):
    names = set()
    for rows in lanes:
        for row in rows:
            name = str(row.get("name", ""))
            if (
                row.get("pixelImproved") is True
                or row.get("supportedAfter") is True
            ):
                continue
            if (
                row.get("routeOnly")
                or row.get("classification") == "route-only"
                or name.lower() == "route-only"
            ):
                names.add(name)
    return names


def _failed_hypotheses(evidence_value, lanes):
    supplied = []
    if isinstance(evidence_value, dict):
        value = evidence_value.get("failedHypotheses", [])
        if isinstance(value, list):
            supplied.extend(_copy_value(value))
        for entry in _evidence_entries(evidence_value):
            for key in ("failedHypothesis", "hypothesis"):
                if entry.get(key) is not None:
                    supplied.append(_copy_value(entry[key]))
    if supplied:
        return supplied[:3]

    derived = []
    seen = set()
    for rows in lanes:
        for row in rows:
            if row.get("outcome") not in {"failure", "error"}:
                continue
            value = row.get("failureCode") or row.get("message") or row.get("name")
            value = str(value)
            if value not in seen:
                seen.add(value)
                derived.append(value)
    return derived[:3]


def _metadata_for(name, hashes):
    if isinstance(hashes, dict):
        value = hashes.get(name)
        if isinstance(value, dict):
            return _copy_value(value)
    return {"path": str(name)}


def build_manifest(inputs: dict, source_commit: str, status: str) -> dict:
    """Build the Wave 1 manifest from parsed evidence and immutable provenance."""
    runner = inputs.get("skiaRunner", inputs.get("runner", {}))
    svg = inputs.get("svg", inputs.get("svgResults", {}))
    dashboard = inputs.get("dashboard", {})
    gpu = inputs.get("gpuResults", inputs.get("gpuOracle", {}))
    cpu = inputs.get("cpuResults", inputs.get("cpuOracle", {}))
    fp13 = inputs.get("fp13Runner", inputs.get("fp13", {}))
    scores_before = inputs.get("scoresBefore", inputs.get("scores_before", {}))
    scores_after = inputs.get("scoresAfter", inputs.get("scores_after", {}))
    score_file = inputs.get("scoreFile", {})
    if not isinstance(score_file, dict):
        score_file = {}

    dashboard_rows = _dashboard_entries(dashboard)
    runner_rows = [dict(row) for row in runner.get("rows", [])] if isinstance(runner, dict) else []
    skia_rows = dashboard_rows or runner_rows
    if not dashboard_rows:
        for row in skia_rows:
            row.setdefault("referenceKind", "skia-upstream")
            row.setdefault("evidenceLane", "skia-junit")

    svg_rows = [dict(row) for row in svg.get("rows", [])] if isinstance(svg, dict) else []
    for row in svg_rows:
        row.setdefault("referenceKind", "svg")
    test_oracle_rows = _oracle_entries(gpu, "testOracle", "test-oracle")
    cpu_oracle_rows = _oracle_entries(cpu, "cpuOracle", "cpu-oracle")
    evidence_value = inputs.get("evidenceIndexData", inputs.get("evidenceIndex", {}))
    evidence_rows = _evidence_entries(evidence_value)

    for row in skia_rows:
        evidence = _evidence_for_row(row, evidence_rows)
        if evidence is not None:
            row["evidenceIndexEntry"] = _copy_value(evidence)
            row["evidence"] = _copy_value(evidence)
            for key, value in evidence.items():
                row.setdefault(key, _copy_value(value))

    comparable_rows = [
        row
        for row in skia_rows
        if _valid_comparable(row, _evidence_for_row(row, evidence_rows))
    ]
    candidate_rows = [
        row
        for row in skia_rows
        if row.get("referenceKind") == "skia-upstream"
        and (
            row.get("classification") == "similarity-failure"
            or row.get("candidateUnlocked") is True
            or (
                _evidence_for_row(row, evidence_rows) or {}
            ).get("candidateUnlocked") is True
        )
    ]
    supported_rows = []
    for row in comparable_rows:
        evidence = _evidence_for_row(row, evidence_rows) or {}
        pixel_improved = (
            row.get("pixelImproved") is True
            or row.get("supportedAfter") is True
            or evidence.get("pixelImproved") is True
            or evidence.get("supportedAfter") is True
        )
        if (
            (row.get("classification") == "pass" or pixel_improved)
            and (_numeric(row.get("score", row.get("similarity"))) is not None or pixel_improved)
            and evidence.get("pixelImproved") is not False
            and evidence.get("supportedAfter") is not False
            and evidence.get("completeEvidence") is not False
        ):
            supported_rows.append(row)
    route_names = _route_only_names(
        [
            skia_rows,
            svg_rows,
            test_oracle_rows,
            cpu_oracle_rows,
            runner_rows,
            evidence_rows,
        ]
    )
    observed_comparable = len(comparable_rows)
    candidate_unlocked = len(candidate_rows)
    supported_after = len(supported_rows)
    route_only = len(route_names)

    paths = inputs.get("paths", {})
    hashes = inputs.get("hashes", inputs.get("fileMetadata", {}))
    if not isinstance(paths, dict):
        paths = {}
    if not isinstance(hashes, dict):
        hashes = {}
    dashboard_path = paths.get("dashboardJson", paths.get("dashboard"))
    dashboard_output_path = paths.get("dashboardOutput")
    dashboard_manifest = {
        "summary": _copy_value(
            dashboard.get("summary", {"rows": len(dashboard_rows)})
            if isinstance(dashboard, dict)
            else {"rows": len(dashboard_rows)}
        ),
        "gms": _copy_value(dashboard_rows),
        "paths": _copy_value(dashboard.get("paths", {}))
        if isinstance(dashboard, dict)
        else {},
        "outputDir": str(paths.get("dashboardDir", "")),
        "dataPath": str(dashboard_path or ""),
    }
    if dashboard_output_path:
        dashboard_manifest["outputSha256"] = _metadata_for(
            "dashboardOutput", hashes
        ).get("sha256")
    if dashboard_path:
        dashboard_manifest["dataSha256"] = _metadata_for(
            "dashboardJson", hashes
        ).get(
            "sha256"
        )

    before_hash = score_file.get("beforeSha256")
    after_hash = score_file.get("afterSha256")
    direct_edit = bool(score_file.get("directEditDetected", before_hash != after_hash))
    integrity = bool(score_file.get("integrityPreserved", not direct_edit))
    restored = bool(score_file.get("restored", integrity))
    runner_side_effect = bool(score_file.get("runnerSideEffectObserved", bool(runner)))
    score_manifest = {
        "beforePath": str(paths.get("scoresBefore", "")),
        "afterPath": str(paths.get("scoresAfter", "")),
        "beforeSha256": before_hash,
        "afterSha256": after_hash,
        "beforeScores": _copy_value(scores_before),
        "afterScores": _copy_value(scores_after),
        "modecolorfiltersBefore": scores_before.get("modecolorfilters")
        if isinstance(scores_before, dict)
        else None,
        "modecolorfiltersAfter": scores_after.get("modecolorfilters")
        if isinstance(scores_after, dict)
        else None,
        "directEditDetected": direct_edit,
        "integrityPreserved": integrity,
        "runnerSideEffectObserved": runner_side_effect,
        "restored": restored,
    }

    runner_summary = _summary(runner)
    runner_summary["sideEffect"] = runner_side_effect
    runner_summary["restored"] = restored
    current = {
        "runner": runner_summary,
        "dashboard": {
            "rows": len(dashboard_rows),
            "summary": _copy_value(dashboard.get("summary", {}))
            if isinstance(dashboard, dict)
            else {},
        },
        "svg": _summary(svg),
        "testOracle": {"rows": len(test_oracle_rows)},
        "cpuOracle": {"rows": len(cpu_oracle_rows)},
        "scores": {
            "rows": len(scores_after) if isinstance(scores_after, dict) else 0,
            "modecolorfilters": scores_after.get("modecolorfilters")
            if isinstance(scores_after, dict)
            else None,
        },
        "observedComparableRows": observed_comparable,
        "candidateUnlockedRows": candidate_unlocked,
    }

    provenance = {
        "files": _copy_value(
            {
                key: value
                for key, value in hashes.items()
                if isinstance(value, dict) and "path" in value
            }
        ),
        "inputs": _copy_value(hashes.get("inputs", {}))
        if isinstance(hashes.get("inputs"), dict)
        else {},
        "outputs": _copy_value(hashes.get("outputs", {}))
        if isinstance(hashes.get("outputs"), dict)
        else {},
        "evidence": _copy_value(hashes.get("evidence", {}))
        if isinstance(hashes.get("evidence"), dict)
        else {},
    }
    for field, key in (
        ("commands", "commandsJson"),
        ("environment", "environmentJson"),
        ("evidenceIndex", "evidenceIndex"),
    ):
        if key in hashes:
            provenance[field] = _copy_value(hashes[key])

    non_claims = [
        "Wave 0 population is historical context only; Wave 1 includes blocking rows and is population-shifted.",
        "Skia, SVG, test-oracle, and CPU-oracle rows remain separate evidence lanes.",
        "Route-only success is not promoted to pixel support.",
        "This report does not weaken global thresholds, assertions, reference policy, or memory budgets.",
    ]
    failed_hypotheses = _failed_hypotheses(
        evidence_value, [runner_rows, svg_rows, skia_rows]
    )
    return {
        "schemaVersion": SCHEMA_VERSION,
        "kind": KIND,
        "generatedBy": GENERATED_BY,
        "generatedAt": "not-recorded",
        "sourceCommit": source_commit,
        "status": status,
        "populationPolicy": {
            "includeBlocking": True,
            "runnerProperty": "-Dkanvas.gm.includeBlocking=true",
            "dashboardProperty": "-Pgm.includeBlocking=true",
            "wave0Population": 615,
            "wave0DirectlyComparable": False,
            "comparisonNote": "population-shifted",
        },
        "policy": {
            "globalThresholdWeakened": False,
            "assertionsWeakened": False,
            "referencesModified": False,
            "scoresDirectlyEdited": False,
            "memoryBudgetChanged": False,
            "readinessDelta": 0.0,
        },
        "dashboard": dashboard_manifest,
        "scoreFile": score_manifest,
        "current": current,
        "rows": {
            "skia": skia_rows,
            "svg": svg_rows,
            "testOracle": test_oracle_rows,
            "cpuOracle": cpu_oracle_rows,
        },
        "observedComparableRows": observed_comparable,
        "candidateUnlockedRows": candidate_unlocked,
        "supportedRowsAfter": supported_after,
        "routeOnlyRows": route_only,
        "routeOnlyRowsPromoted": False,
        "escalation": {
            "maxFailedHypotheses": 3,
            "failedHypotheses": failed_hypotheses,
        },
        "nonClaims": non_claims,
        "provenance": provenance,
        "inputs": {
            "fp13": _summary(fp13),
            "scoresBefore": _copy_value(scores_before),
            "scoresAfter": _copy_value(scores_after),
            "evidenceIndex": _copy_value(evidence_value),
        },
    }


def _markdown_value(value):
    return str(value).replace("|", r"\|").replace("\n", " ")


def render_markdown(manifest: dict) -> str:
    """Render policy, counters, classifications, non-claims, and hashes."""
    policy = manifest.get("policy", {})
    population = manifest.get("populationPolicy", {})
    current = manifest.get("current", {})
    lines = [
        "# Skia Fidelity Wave 1 Reconciliation",
        "",
        "- schemaVersion: `%s`" % _markdown_value(manifest.get("schemaVersion")),
        "- kind: `%s`" % _markdown_value(manifest.get("kind")),
        "- generatedBy: `%s`" % _markdown_value(manifest.get("generatedBy")),
        "- sourceCommit: `%s`" % _markdown_value(manifest.get("sourceCommit")),
        "- status: `%s`" % _markdown_value(manifest.get("status")),
        "",
        "## Policy",
        "",
        "- globalThresholdWeakened: `%s`" % policy.get("globalThresholdWeakened", False),
        "- assertionsWeakened: `%s`" % policy.get("assertionsWeakened", False),
        "- referencesModified: `%s`" % policy.get("referencesModified", False),
        "- scoresDirectlyEdited: `%s`" % policy.get("scoresDirectlyEdited", False),
        "- memoryBudgetChanged: `%s`" % policy.get("memoryBudgetChanged", False),
        "- readinessDelta: `%s`" % policy.get("readinessDelta", 0.0),
        "",
        "## Population Shift",
        "",
        "- includeBlocking: `%s`" % population.get("includeBlocking", False),
        "- runnerProperty: `%s`" % _markdown_value(population.get("runnerProperty", "")),
        "- dashboardProperty: `%s`"
        % _markdown_value(population.get("dashboardProperty", "")),
        "- wave0Population: `%s`" % population.get("wave0Population", 0),
        "- wave0DirectlyComparable: `%s`"
        % population.get("wave0DirectlyComparable", False),
        "- comparisonNote: `%s`"
        % _markdown_value(population.get("comparisonNote", "")),
        "",
        "## Current Counters",
        "",
        "- observedComparableRows: `%s`" % manifest.get("observedComparableRows", 0),
        "- candidateUnlockedRows: `%s`" % manifest.get("candidateUnlockedRows", 0),
        "- supportedRowsAfter: `%s`" % manifest.get("supportedRowsAfter", 0),
        "- routeOnlyRows: `%s`" % manifest.get("routeOnlyRows", 0),
        "- routeOnlyRowsPromoted: `%s`" % manifest.get("routeOnlyRowsPromoted", False),
        "",
        "| Lane | Rows | Failures | Errors | Skips |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    for lane in ("runner", "dashboard", "svg", "testOracle", "cpuOracle"):
        summary = current.get(lane, {})
        if lane == "runner":
            label = "skia-runner"
        else:
            label = lane
        lines.append(
            "| `%s` | %s | %s | %s | %s |"
            % (
                label,
                summary.get("rows", summary.get("tests", 0)),
                summary.get("failures", 0),
                summary.get("errors", 0),
                summary.get("skips", 0),
            )
        )
    lines.extend(
        [
            "",
            "## Row Classifications",
            "",
            "| Lane | Name | Outcome | Classification | Reference |",
            "| --- | --- | --- | --- | --- |",
        ]
    )
    for lane in ("skia", "svg", "testOracle", "cpuOracle"):
        for row in manifest.get("rows", {}).get(lane, []):
            lines.append(
                "| `%s` | `%s` | %s | %s | %s |"
                % (
                    _markdown_value(row.get("evidenceLane", lane)),
                    _markdown_value(row.get("name", "")),
                    _markdown_value(row.get("outcome", "")),
                    _markdown_value(row.get("classification", "")),
                    _markdown_value(row.get("referenceKind", "")),
                )
            )
    lines.extend(["", "## Non-claims", ""])
    lines.extend("- " + str(claim) for claim in manifest.get("nonClaims", []))
    lines.extend(["", "## SHA-256 Provenance", "", "| Evidence | Path | SHA-256 |", "| --- | --- | --- |"])
    provenance = manifest.get("provenance", {})
    for group in ("inputs", "outputs", "evidence", "commands", "environment", "evidenceIndex"):
        value = provenance.get(group)
        if isinstance(value, dict) and "path" in value:
            lines.append(
                "| `%s` | `%s` | `%s` |"
                % (
                    group,
                    _markdown_value(value.get("path", "")),
                    _markdown_value(value.get("sha256", "")),
                )
            )
        elif isinstance(value, dict):
            for name, metadata in sorted(value.items()):
                if not isinstance(metadata, dict):
                    continue
                lines.append(
                    "| `%s.%s` | `%s` | `%s` |"
                    % (
                        group,
                        _markdown_value(name),
                        _markdown_value(metadata.get("path", "")),
                        _markdown_value(metadata.get("sha256", "")),
                    )
                )
    return "\n".join(lines) + "\n"


def _same_file(path, other):
    path = pathlib.Path(path)
    other = pathlib.Path(other)
    if path.resolve() == other.resolve():
        return True
    try:
        return path.exists() and other.exists() and path.samefile(other)
    except OSError:
        return False


def _path_alias(output, input_path):
    output = pathlib.Path(output).resolve()
    input_path = pathlib.Path(input_path).resolve()
    if output == input_path:
        return True
    if input_path.is_dir():
        try:
            output.relative_to(input_path)
            return True
        except ValueError:
            return False
    return _same_file(output, input_path)


def _argument_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skia-runner", required=True, type=pathlib.Path)
    parser.add_argument("--dashboard-json", required=True, type=pathlib.Path)
    parser.add_argument("--dashboard-dir", required=True, type=pathlib.Path)
    parser.add_argument("--generated-renders", required=True, type=pathlib.Path)
    parser.add_argument("--svg-xml", required=True, type=pathlib.Path)
    parser.add_argument("--cpu-results", required=True, type=pathlib.Path)
    parser.add_argument("--gpu-results", required=True, type=pathlib.Path)
    parser.add_argument("--scores-before", required=True, type=pathlib.Path)
    parser.add_argument("--scores-after", required=True, type=pathlib.Path)
    parser.add_argument("--fp13-runner", required=True, type=pathlib.Path)
    parser.add_argument("--commands-json", required=True, type=pathlib.Path)
    parser.add_argument("--environment-json", required=True, type=pathlib.Path)
    parser.add_argument("--evidence-index", required=True, type=pathlib.Path)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument(
        "--status",
        default="classification",
        choices=("classification", "approved", "blocked"),
    )
    parser.add_argument("--output-json", required=True, type=pathlib.Path)
    parser.add_argument("--output-markdown", required=True, type=pathlib.Path)
    parser.add_argument("--check", action="store_true")
    return parser


def _dashboard_output(directory, data_path):
    if not directory.is_dir():
        return None
    candidates = (
        directory / "dashboard.json",
        directory / "gms.json",
        directory / "data" / "gms.json",
        directory / "index.json",
    )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    for candidate in sorted(directory.rglob("*.json")):
        if not _same_file(candidate, data_path):
            return candidate
    return None


def _resolve_evidence_path(value, index_path):
    if not isinstance(value, str) or not value:
        return None
    path = pathlib.Path(value)
    if not path.is_absolute():
        path = index_path.parent / path
    return path


def _evidence_file_paths(index_value, index_path):
    paths = {}
    for index, entry in enumerate(_evidence_entries(index_value)):
        name = str(entry.get("name", index))
        for key in ("renderPath", "referencePath", "generatedPath", "path"):
            path = _resolve_evidence_path(entry.get(key), index_path)
            if path is not None and path.is_file():
                paths["%s.%s" % (name, key)] = path
        nested = entry.get("paths")
        if isinstance(nested, dict):
            for key, value in nested.items():
                path = _resolve_evidence_path(value, index_path)
                if path is not None and path.is_file():
                    paths["%s.%s" % (name, key)] = path
    return paths


def _has_hash(value):
    return isinstance(value, str) and bool(re.fullmatch(r"[0-9a-fA-F]{64}", value))


def _entry_hashes(entry):
    hashes = []
    for key in ("renderSha256", "referenceSha256", "generatedSha256", "sha256", "hash"):
        if key in entry:
            hashes.append(entry[key])
    nested = entry.get("hashes")
    if isinstance(nested, dict):
        hashes.extend(nested.values())
    paths = entry.get("paths")
    if isinstance(paths, dict):
        for value in paths.values():
            if isinstance(value, dict):
                hashes.extend(value.get(key) for key in ("sha256", "hash"))
    for key in ("render", "generated", "reference", "ref"):
        value = entry.get(key)
        if isinstance(value, dict):
            hashes.extend(value.get(name) for name in ("sha256", "hash"))
    return [value for value in hashes if value is not None]


def _entry_paths(entry):
    paths = []
    for key in ("renderPath", "referencePath", "generatedPath", "path"):
        if isinstance(entry.get(key), str) and entry[key]:
            paths.append(entry[key])
    nested = entry.get("paths")
    if isinstance(nested, dict):
        for value in nested.values():
            if isinstance(value, str) and value:
                paths.append(value)
            elif isinstance(value, dict) and isinstance(value.get("path"), str):
                paths.append(value["path"])
    for key in ("render", "generated", "reference", "ref"):
        value = entry.get(key)
        if isinstance(value, dict) and isinstance(value.get("path"), str):
            paths.append(value["path"])
    return paths


def _entry_has_required_evidence(entry):
    if not entry.get("name") or not entry.get("referenceKind"):
        return False
    hashes = _entry_hashes(entry)
    if not hashes or not all(_has_hash(value) for value in hashes):
        return False
    paths = _entry_paths(entry)
    if not paths:
        return False
    if entry.get("referenceKind") == "skia-upstream":
        has_render_hash = any(
            key in entry for key in ("renderSha256", "generatedSha256")
        ) or any(
            isinstance(entry.get(key), dict)
            and ("sha256" in entry[key] or "hash" in entry[key])
            for key in ("render", "generated")
        )
        has_reference_hash = "referenceSha256" in entry or any(
            isinstance(entry.get(key), dict)
            and ("sha256" in entry[key] or "hash" in entry[key])
            for key in ("reference", "ref")
        )
        if isinstance(entry.get("hashes"), dict):
            has_render_hash = has_render_hash or any(
                key in entry["hashes"] for key in ("render", "generated")
            )
            has_reference_hash = has_reference_hash or any(
                key in entry["hashes"] for key in ("reference", "ref")
            )
        if not has_render_hash or not has_reference_hash:
            return False
    dimensions = entry.get("dimensions")
    if dimensions is not None:
        if isinstance(dimensions, dict):
            if _dimension_pair(dimensions):
                return True
            if _dimension_pair(dimensions.get("render")) and _dimension_pair(
                dimensions.get("reference")
            ):
                return True
        return False
    return (
        _dimension_pair(entry) is not None
        or _dimension_pair(entry.get("renderDimensions")) is not None
    )


def _check_evidence_index(value):
    violations = []
    entries = _evidence_entries(value)
    for index, entry in enumerate(entries):
        if not _entry_has_required_evidence(entry):
            violations.append(
                "evidence-index entry %s is missing required paths, SHA-256 hashes, or dimensions"
                % index
            )
    return violations


def _check_current_failures(lanes):
    violations = []
    for lane, result in lanes:
        if not isinstance(result, dict):
            continue
        rows = result.get("rows", [])
        unclassified = [
            row
            for row in rows
            if row.get("outcome") in {"failure", "error"}
            and row.get("classification") == "unclassified"
        ]
        if unclassified:
            violations.append(
                "%s has %s unclassified failure/error testcase(s)"
                % (lane, len(unclassified))
            )
        terminal = [
            row
            for row in rows
            if row.get("outcome") in {"failure", "error"}
            and row.get("terminalRefusal")
            and not row.get("expectedUnsupported")
        ]
        if terminal:
            violations.append(
                "%s has %s terminal/lifecycle failure/error testcase(s)"
                % (lane, len(terminal))
            )
    return violations


def _prepare_inputs(args, dashboard_output):
    input_paths = {
        "skiaRunner": args.skia_runner,
        "dashboardJson": args.dashboard_json,
        "dashboardDir": args.dashboard_dir,
        "generatedRenders": args.generated_renders,
        "svgXml": args.svg_xml,
        "cpuResults": args.cpu_results,
        "gpuResults": args.gpu_results,
        "scoresBefore": args.scores_before,
        "scoresAfter": args.scores_after,
        "fp13Runner": args.fp13_runner,
        "commandsJson": args.commands_json,
        "environmentJson": args.environment_json,
        "evidenceIndex": args.evidence_index,
    }
    output_paths = {
        "outputJson": args.output_json,
        "outputMarkdown": args.output_markdown,
    }
    if dashboard_output is not None:
        output_paths["dashboardOutput"] = dashboard_output
    evidence_value = json.loads(args.evidence_index.read_text(encoding="utf-8"))
    evidence_paths = _evidence_file_paths(evidence_value, args.evidence_index)
    hashes = hash_files(input_paths)
    if dashboard_output is not None:
        hashes.update(hash_files({"dashboardOutput": dashboard_output}))
    if evidence_paths:
        hashes["evidence"] = hash_files(evidence_paths)
    hashes["inputs"] = {
        key: value for key, value in hashes.items() if key in input_paths
    }
    hashes["outputs"] = {
        key: value for key, value in hashes.items() if key in {"dashboardOutput"}
    }
    try:
        dashboard = parse_dashboard(args.dashboard_json)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        raise ValueError("dashboard: %s" % error) from error
    return {
        "paths": {
            **input_paths,
            "dashboardOutput": dashboard_output,
        },
        "hashes": hashes,
        "dashboard": dashboard,
        "skiaRunner": parse_junit(
            args.skia_runner, "skia", EXPECTED_UNSUPPORTED_CODES
        ),
        "svg": parse_junit(args.svg_xml, "svg", EXPECTED_UNSUPPORTED_CODES),
        "cpuResults": json.loads(args.cpu_results.read_text(encoding="utf-8")),
        "gpuResults": json.loads(args.gpu_results.read_text(encoding="utf-8")),
        "scoresBefore": load_scores(args.scores_before),
        "scoresAfter": load_scores(args.scores_after),
        "fp13Runner": parse_junit(args.fp13_runner, "fp13", EXPECTED_UNSUPPORTED_CODES),
        "commands": json.loads(args.commands_json.read_text(encoding="utf-8")),
        "environment": json.loads(args.environment_json.read_text(encoding="utf-8")),
        "evidenceIndexData": evidence_value,
        "scoreFile": {
            "beforeSha256": hashes["scoresBefore"]["sha256"],
            "afterSha256": hashes["scoresAfter"]["sha256"],
            "directEditDetected": hashes["scoresBefore"]["sha256"]
            != hashes["scoresAfter"]["sha256"],
            "integrityPreserved": hashes["scoresBefore"]["sha256"]
            == hashes["scoresAfter"]["sha256"],
            "runnerSideEffectObserved": True,
            "restored": hashes["scoresBefore"]["sha256"]
            == hashes["scoresAfter"]["sha256"],
        },
    }


def _fatal_paths(args):
    return {
        "skiaRunner": args.skia_runner,
        "dashboardJson": args.dashboard_json,
        "dashboardDir": args.dashboard_dir,
        "generatedRenders": args.generated_renders,
        "svgXml": args.svg_xml,
        "cpuResults": args.cpu_results,
        "gpuResults": args.gpu_results,
        "scoresBefore": args.scores_before,
        "scoresAfter": args.scores_after,
        "fp13Runner": args.fp13_runner,
        "commandsJson": args.commands_json,
        "environmentJson": args.environment_json,
        "evidenceIndex": args.evidence_index,
    }


def _write_outputs(manifest, output_json, output_markdown):
    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_markdown.parent.mkdir(parents=True, exist_ok=True)
    output_json.write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    output_markdown.write_text(render_markdown(manifest), encoding="utf-8")


def main(argv=None):
    parser = _argument_parser()
    args = parser.parse_args(argv)
    input_paths = _fatal_paths(args)
    violations = []
    missing = [
        "%s: %s" % (name, path)
        for name, path in input_paths.items()
        if not path.exists()
    ]
    if args.output_json.resolve() == args.output_markdown.resolve():
        violations.append("JSON and Markdown outputs must differ")
    for output in (args.output_json, args.output_markdown):
        for name, input_path in input_paths.items():
            if _path_alias(output, input_path):
                violations.append(
                    "output path aliases input %s: %s" % (name, input_path)
                )
    if missing or violations:
        message = missing + violations
        print("reconciliation %s failed: %s" % ("check" if args.check else "", "; ".join(message)))
        return 2

    dashboard_output = _dashboard_output(args.dashboard_dir, args.dashboard_json)
    if args.check and dashboard_output is None:
        print("reconciliation check failed: dashboard output is missing")
        return 2

    try:
        inputs = _prepare_inputs(args, dashboard_output)
        manifest = build_manifest(inputs, args.source_commit, args.status)
    except (OSError, ValueError, ET.ParseError, json.JSONDecodeError) as error:
        print("reconciliation failed: %s" % error)
        return 2

    check_violations = []
    if args.check:
        check_violations.extend(
            _check_current_failures(
                [
                    ("skia", inputs["skiaRunner"]),
                    ("svg", inputs["svg"]),
                ]
            )
        )
        if manifest["scoreFile"]["directEditDetected"]:
            check_violations.append("score before/after content diverges")
        check_violations.extend(_check_evidence_index(inputs["evidenceIndexData"]))

    try:
        if check_violations:
            _write_outputs(manifest, args.output_json, args.output_markdown)
            print("reconciliation check failed: %s" % "; ".join(check_violations))
            return 1
        _write_outputs(manifest, args.output_json, args.output_markdown)
    except OSError as error:
        print("reconciliation failed: %s" % error)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
