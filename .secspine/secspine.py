#!/usr/bin/env python3
"""secspine — Prüfwerkzeug für die Befund-Kette eines Security-Tests.

Nur Standardbibliothek. Siehe .secspine/STANDARD.md.

Befehle:
  check            Befunde formal prüfen (Pflichtfelder, Lebenszyklus, Evidence, IDs)
  render           security-review/report.md neu aus den Befunden bauen
  list             Überblick: ID, Schwere, Status, Titel
  new <ID> "Titel" Befund aus der Vorlage anlegen
  version          installierte Version zeigen und auf Updates prüfen
  pre-push         Push verweigern, wenn der Stand ein offenes SEC-Risiko enthält (ADR-0009);
                   als Git-Hook gedacht, ohne Hook prüft er HEAD
  install-hook     .git/hooks/pre-push anlegen oder ergänzen, der pre-push aufruft
"""
from __future__ import annotations

import datetime as _dt
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FINDINGS_DIR = os.path.join(ROOT, "security-review")
TEMPLATE = os.path.join(ROOT, ".secspine", "finding-template.md")
REPORT = os.path.join(ROOT, "security-review", "report.md")
STANDARD = os.path.join(ROOT, ".secspine", "STANDARD.md")
REMOTE_VERSION_URL = ("https://raw.githubusercontent.com/flaechsig/secspine/"
                      "dist/.secspine/STANDARD.md")
VERSION_RE = re.compile(r"<!--\s*secspine\s+(\S+)")

STATUSES = ["candidate", "confirmed", "dismissed", "fixed", "verified"]
SEVERITIES = ["info", "low", "medium", "high", "critical"]
SEV_ORDER = {s: i for i, s in enumerate(reversed(SEVERITIES))}  # critical=0 … info=4
ID_RE = re.compile(r"^(F|BB)-\d{2,4}$")
# ab diesem Status sind Repro/Auswirkung/Maßnahme Pflicht
NEEDS_DETAIL = {"confirmed", "fixed", "verified"}
REQUIRED_SECTIONS = ["Reproduktion", "Auswirkung", "Maßnahme"]


# ---------- Minimaler Frontmatter-Parser (flache Keys + einfache Listen) ----------

def parse(path):
    """Liest eine Befund-Datei -> (meta: dict, sections: dict[name]->text)."""
    with open(path, encoding="utf-8") as fh:
        raw = fh.read()
    meta, body = {}, raw
    if raw.startswith("---"):
        end = raw.find("\n---", 3)
        if end != -1:
            block = raw[3:end].strip("\n")
            body = raw[end + 4:]
            meta = _parse_frontmatter(block)
    return meta, _split_sections(body)


def _parse_frontmatter(block):
    meta, key = {}, None
    for line in block.splitlines():
        if not line.strip():
            continue
        m = re.match(r"^(\w+):\s*(.*)$", line)
        if m:
            key, val = m.group(1), m.group(2).strip()
            if val in ("[]", ""):
                meta[key] = [] if val == "[]" else ""
            elif val.startswith("[") and val.endswith("]"):
                inner = val[1:-1].strip()
                meta[key] = [x.strip().strip('"') for x in inner.split(",") if x.strip()]
            else:
                meta[key] = val.strip('"')
        elif line.lstrip().startswith("- ") and key is not None:
            meta.setdefault(key, [])
            if not isinstance(meta[key], list):
                meta[key] = []
            meta[key].append(line.lstrip()[2:].strip().strip('"'))
    return meta


def _split_sections(body):
    sections, name, buf = {}, None, []
    for line in body.splitlines():
        m = re.match(r"^##\s+(.*)$", line)
        if m:
            if name is not None:
                sections[name] = "\n".join(buf).strip()
            name, buf = m.group(1).strip(), []
        elif name is not None:
            buf.append(line)
    if name is not None:
        sections[name] = "\n".join(buf).strip()
    return sections


# ---------- Befunde laden ----------

def load_all():
    out = []
    if not os.path.isdir(FINDINGS_DIR):
        return out
    for fn in sorted(os.listdir(FINDINGS_DIR)):
        if fn.endswith(".md") and ID_RE.match(fn[:-3]):
            p = os.path.join(FINDINGS_DIR, fn)
            meta, sections = parse(p)
            out.append((fn, meta, sections))
    return out


def _sev_key(meta):
    # kleiner = schwerer: critical (0) sortiert vor info (4)
    return SEV_ORDER.get(meta.get("severity", "info"), 99)


# ---------- check ----------

def cmd_check():
    findings = load_all()
    problems, seen = [], {}
    if not findings:
        print("Keine Befunde in security-review/.")
        return 0
    for fn, meta, sections in findings:
        fid = meta.get("id", "")
        def bad(msg):
            problems.append(f"{fn}: {msg}")
        if not fid:
            bad("kein id im Frontmatter")
        else:
            if not ID_RE.match(fid):
                bad(f"id '{fid}' passt nicht zum Muster F-NN / BB-NN / F-NNNN")
            if fid in seen:
                bad(f"id '{fid}' doppelt (auch in {seen[fid]})")
            seen[fid] = fn
        for key in ("title", "status", "severity", "target", "source", "discovered"):
            if not meta.get(key):
                bad(f"Pflichtfeld '{key}' fehlt")
        st = meta.get("status", "")
        if st and st not in STATUSES:
            bad(f"status '{st}' unbekannt (erlaubt: {', '.join(STATUSES)})")
        sv = meta.get("severity", "")
        if sv and sv not in SEVERITIES:
            bad(f"severity '{sv}' unbekannt (erlaubt: {', '.join(SEVERITIES)})")
        if st in NEEDS_DETAIL:
            for sec in REQUIRED_SECTIONS:
                if not sections.get(sec):
                    bad(f"status '{st}' verlangt Abschnitt '## {sec}', fehlt/leer")
        for ev in meta.get("evidence", []) or []:
            if not os.path.exists(os.path.join(ROOT, ev)):
                bad(f"evidence '{ev}' existiert nicht")
    if problems:
        print(f"PRÜFUNG FEHLGESCHLAGEN ({len(problems)}):")
        for p in problems:
            print(f"  - {p}")
        return 1
    print(f"OK — {len(findings)} Befunde, keine Mängel.")
    return 0


# ---------- list ----------

def cmd_list():
    findings = sorted(load_all(), key=lambda t: (_sev_key(t[1]), t[1].get("id", "")))
    if not findings:
        print("Keine Befunde.")
        return 0
    for _, meta, _ in findings:
        print(f"  {meta.get('id',''):<8} {meta.get('severity',''):<9} "
              f"{meta.get('status',''):<10} {meta.get('title','')}")
    return 0


# ---------- render ----------

def cmd_render():
    findings = sorted(load_all(), key=lambda t: (_sev_key(t[1]), t[1].get("id", "")))
    today = _dt.date.today().isoformat()
    out = [
        "<!-- GENERIERT von .secspine/secspine.py render — nicht von Hand ändern. -->",
        "<!-- Quelle: security-review/. Ändere dort, dann neu rendern. -->",
        "",
        "# Phase 6 — Bericht",
        "",
        f"Stand: {today}. Generiert aus den Befunden in `security-review/`.",
        "",
    ]
    active = [f for f in findings if f[1].get("status") != "dismissed"]
    dismissed = [f for f in findings if f[1].get("status") == "dismissed"]

    counts = {s: 0 for s in SEVERITIES}
    for _, meta, _ in active:
        counts[meta.get("severity", "info")] = counts.get(meta.get("severity", "info"), 0) + 1
    out += ["## Überblick", "",
            "| Schwere | Anzahl |", "|---|---|"]
    for s in reversed(SEVERITIES):
        if counts.get(s):
            out.append(f"| {s} | {counts[s]} |")
    out += ["", "| ID | Schwere | Status | Titel |", "|---|---|---|---|"]
    for _, meta, _ in active:
        out.append(f"| {meta.get('id','')} | {meta.get('severity','')} | "
                   f"{meta.get('status','')} | {meta.get('title','')} |")
    out.append("")

    for _, meta, sections in active:
        out.append(f"## {meta.get('id','')} ({meta.get('severity','')}) — {meta.get('title','')}")
        tags = [f"Status: {meta.get('status','')}", f"Ziel: {meta.get('target','')}",
                f"Quelle: {meta.get('source','')}"]
        if meta.get("cwe"):
            tags.append(meta["cwe"])
        if meta.get("owasp"):
            tags.append(meta["owasp"])
        out += ["", "_" + " · ".join(tags) + "_", ""]
        for sec in ["Was"] + REQUIRED_SECTIONS:
            if sections.get(sec):
                out += [f"### {sec}", "", sections[sec], ""]
        ev = meta.get("evidence", []) or []
        if ev:
            out += ["### Nachweis", ""] + [f"- `{e}`" for e in ev] + [""]
        if meta.get("story"):
            out += [f"→ Entwicklungs-Story: {meta['story']}", ""]

    if dismissed:
        out += ["## Verworfen (dokumentiert, kein offener Befund)", ""]
        for _, meta, sections in dismissed:
            out.append(f"- **{meta.get('id','')}** {meta.get('title','')} — "
                       f"{(sections.get('Was','') or '').splitlines()[0] if sections.get('Was') else ''}")
        out.append("")

    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    with open(REPORT, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out))
    print(f"Geschrieben: {os.path.relpath(REPORT, ROOT)} ({len(active)} aktiv, "
          f"{len(dismissed)} verworfen).")
    return 0


# ---------- new ----------

def cmd_new(fid, title):
    if not ID_RE.match(fid):
        print(f"ID '{fid}' passt nicht zum Muster F-NN / BB-NN / F-NNNN.")
        return 1
    os.makedirs(FINDINGS_DIR, exist_ok=True)
    dest = os.path.join(FINDINGS_DIR, f"{fid}.md")
    if os.path.exists(dest):
        print(f"{dest} existiert schon.")
        return 1
    with open(TEMPLATE, encoding="utf-8") as fh:
        text = fh.read()
    text = text.replace("id: F-XX", f"id: {fid}")
    text = text.replace("title: Kurztitel in einer Zeile", f"title: {title}")
    text = text.replace("discovered: 2026-10-07",
                        f"discovered: {_dt.date.today().isoformat()}")
    with open(dest, "w", encoding="utf-8") as fh:
        fh.write(text)
    print(f"Angelegt: {os.path.relpath(dest, ROOT)}")
    return 0


def _installed_version():
    try:
        with open(STANDARD, encoding="utf-8") as fh:
            m = VERSION_RE.match(fh.readline())
            return m.group(1) if m else None
    except OSError:
        return None


def _as_tuple(ver):
    return tuple(int(p) if p.isdigit() else 0 for p in (ver or "").split("."))


def _remote_version(timeout=5):
    import urllib.request
    with urllib.request.urlopen(REMOTE_VERSION_URL, timeout=timeout) as resp:
        first = resp.read(200).decode("utf-8", "replace").splitlines()[0]
    m = VERSION_RE.match(first)
    return m.group(1) if m else None


def cmd_version():
    installed = _installed_version()
    print(f"secspine {installed or '(unbekannt)'} installiert.")
    try:
        remote = _remote_version()
    except Exception:
        print("Update-Prüfung nicht möglich (offline oder nicht erreichbar).")
        return 0
    if not remote:
        print("Update-Prüfung: Version am dist-Zweig nicht lesbar.")
        return 0
    if _as_tuple(remote) > _as_tuple(installed):
        print(f"Neuere Version verfügbar: {remote}. Aktualisieren mit der curl-Zeile aus dem README.")
    elif _as_tuple(remote) < _as_tuple(installed):
        print(f"Installierte Version ist neuer als der dist-Zweig ({remote}).")
    else:
        print("Aktuell.")
    return 0


# ---------- Sperre vor dem Push (ADR-0009) ----------

RISKS_DIR = "docs/11-risks"
SEC_FILE_RE = re.compile(r"(?:^|/)(SEC-\d{4})\.md$")
ZERO_SHA = re.compile(r"^0+$")
HOOK_LINE = 'python3 .secspine/secspine.py pre-push "$@" || exit 1'
HOOK_TEXT = ("#!/bin/sh\n"
             "# secspine: offene SEC-Risiken nicht pushen (ADR-0009)\n"
             + HOOK_LINE + "\n")


def _git(*args, check=True):
    res = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)
    if check and res.returncode != 0:
        raise RuntimeError(res.stderr.strip() or f"git {' '.join(args)} fehlgeschlagen")
    return res.stdout


def open_sec_risks(rev):
    """IDs der SEC-Risiken, die im Stand `rev` offen sind und kein `publish` tragen."""
    if _git("cat-file", "-t", rev, check=False).strip() not in ("commit", "tag"):
        return []
    names = _git("ls-tree", "-r", "--name-only", rev, "--", RISKS_DIR, check=False).splitlines()
    out = []
    for name in names:
        m = SEC_FILE_RE.search(name)
        if not m:
            continue
        text = _git("show", f"{rev}:{name}", check=False)
        meta = {}
        if text.startswith("---"):
            end = text.find("\n---", 3)
            if end != -1:
                meta = _parse_frontmatter(text[3:end].strip("\n"))
        if str(meta.get("status", "")).strip() == "open" and not str(meta.get("publish", "")).strip():
            out.append(m.group(1))
    return out


def cmd_pre_push(stdin):
    """Git übergibt je Ref eine Zeile: <local ref> <local sha> <remote ref> <remote sha>."""
    revs = []
    lines = [] if stdin is None else [l.split() for l in stdin.read().splitlines() if l.strip()]
    for parts in lines:
        if len(parts) >= 2 and not ZERO_SHA.match(parts[1]):  # gelöschte Refs zeigen nichts
            revs.append((parts[0], parts[1]))
    if stdin is None or not lines:
        revs = [("HEAD", "HEAD")]
    blocked = {}
    for ref, rev in revs:
        ids = open_sec_risks(rev)
        if ids:
            blocked[ref] = ids
    if not blocked:
        return 0
    print("secspine: Push verweigert — der Stand enthält offene Security-Risiken (ADR-0009):",
          file=sys.stderr)
    for ref, ids in blocked.items():
        print(f"  {ref}: {', '.join(ids)}", file=sys.stderr)
    print("Offene SEC-Risiken bleiben auf dem Security-Branch, bis sie behoben sind.\n"
          "Bewusst veröffentlichen: im SEC 'publish: <Datum>' setzen, oder einmalig\n"
          "'git push --no-verify'.", file=sys.stderr)
    return 1


def cmd_install_hook():
    hooks = _git("rev-parse", "--git-path", "hooks").strip()
    hooks = hooks if os.path.isabs(hooks) else os.path.join(ROOT, hooks)
    path = os.path.join(hooks, "pre-push")
    if _git("config", "core.hooksPath", check=False).strip():
        print("core.hooksPath ist gesetzt; der Hook gehört dorthin. Bitte von Hand eintragen:\n  "
              + HOOK_LINE)
        return 1
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
        if HOOK_LINE in text:
            print(f"Schon eingerichtet: {os.path.relpath(path, ROOT)}")
            return 0
        with open(path, "a", encoding="utf-8") as fh:
            fh.write(("" if text.endswith("\n") else "\n")
                     + "# secspine: offene SEC-Risiken nicht pushen (ADR-0009)\n" + HOOK_LINE + "\n")
        print(f"Ergänzt: {os.path.relpath(path, ROOT)}. Prüfen, dass der vorhandene Hook nicht vorher "
              "mit 'exit' endet und die Eingabe (stdin) nicht schon verbraucht.")
    else:
        os.makedirs(hooks, exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(HOOK_TEXT)
        print(f"Angelegt: {os.path.relpath(path, ROOT)}")
    os.chmod(path, os.stat(path).st_mode | 0o111)
    return 0


def main(argv):
    if not argv:
        print(__doc__)
        return 0
    cmd, rest = argv[0], argv[1:]
    if cmd == "check":
        return cmd_check()
    if cmd == "render":
        return cmd_render()
    if cmd == "list":
        return cmd_list()
    if cmd == "new":
        if len(rest) < 2:
            print('Aufruf: new <ID> "<Titel>"')
            return 1
        return cmd_new(rest[0], " ".join(rest[1:]))
    if cmd == "version":
        return cmd_version()
    if cmd == "pre-push":
        return cmd_pre_push(None if sys.stdin is None or sys.stdin.isatty() else sys.stdin)
    if cmd == "install-hook":
        return cmd_install_hook()
    print(f"Unbekannter Befehl: {cmd}\n")
    print(__doc__)
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
