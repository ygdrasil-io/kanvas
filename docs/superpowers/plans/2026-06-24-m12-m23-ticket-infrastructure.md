# M12-M23 Ticket Infrastructure Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or inline execution.

**Goal:** Create all ticket infrastructure (12 milestone READMEs + 58 ticket files) for GPU renderer milestones M12 through M23, following the exact format of existing M0-M11 tickets.

**Architecture:** Generate all files via a Python script that embeds ticket metadata from the design doc, using the existing ticket template format with unique French PM notes, Graphite references, and domain-specific content per ticket.

**Tech Stack:** Python 3 for file generation, markdown files for output.

---

### Task 1: Create the Python generation script

**Files:**
- Create: `scripts/gen_m12_m23_tickets.py`

- [ ] **Step 1: Write the generation script**

- [ ] **Step 2: Run the script to generate all files**

```bash
python3 scripts/gen_m12_m23_tickets.py
```

- [ ] **Step 3: Update STATUS.md with M12-M23 rows**

- [ ] **Step 4: Validate file count**

```bash
find .upstream/specs/gpu-renderer/tickets/M1[2-9]* .upstream/specs/gpu-renderer/tickets/M2[0-3]* -name "KGPU-*.md" 2>/dev/null | wc -l
# Expected: 58
```

- [ ] **Step 5: Run basic validation**

```bash
rtk git diff --check
```
