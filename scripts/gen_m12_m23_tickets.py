#!/usr/bin/env python3
"""Generate M12-M23 milestone README.md and ticket files."""

import os, subprocess, textwrap

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TICKETS_DIR = os.path.join(ROOT, ".upstream", "specs", "gpu-renderer", "tickets")

