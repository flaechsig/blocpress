"""
Helfer fuer blocpress-render (ADR-0021): haelt eine LibreOffice-Instanz warm und konvertiert
Dokumente auf Anfrage. render spricht mit ihm ueber stdin/stdout:

  Helfer -> render: READY                      Instanz bereit
  render -> Helfer: CONVERT <format> <laenge>  danach <laenge> Bytes ODT
  Helfer -> render: PROGRESS                   Dokument geladen; danach Fortschritt des Exports (je Seite)
  Helfer -> render: OK <laenge>                danach <laenge> Bytes Ergebnis
  Helfer -> render: FAIL <meldung>             LibreOffice konnte das Dokument nicht konvertieren

Die Instanz laeuft mit eigenem Profil an einer lokalen Pipe, nie ueber TCP.

Fortschritt wird nur beim Export abgefragt: Beim Laden ruft LibreOffice den StatusIndicator fuer
ein Rohdokument zehntausendfach synchron ueber die Pipe auf (251 Seiten: ~50 000 Aufrufe, ~25 %
der Zeit, Messung 2026-10-10). Die Ladephase deckt das Zeitlimit ohne Fortschritt in render ab.
"""
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time

import uno
import unohelper
from com.sun.star.beans import PropertyValue
from com.sun.star.task import XStatusIndicator

FILTERS = {"pdf": ("writer_pdf_Export", "pdf"), "rtf": ("Rich Text Format", "rtf"), "odt": ("writer8", "odt")}

out = sys.stdout.buffer
inp = sys.stdin.buffer
lock = threading.Lock()


def send(line, payload=None):
    with lock:
        out.write(line.encode("utf-8") + b"\n")
        if payload is not None:
            out.write(payload)
        out.flush()


def prop(name, value):
    p = PropertyValue()
    p.Name = name
    p.Value = value
    return p


class Progress(unohelper.Base, XStatusIndicator):
    def start(self, text, rng): send("PROGRESS")
    def end(self): send("PROGRESS")
    def setText(self, text): pass
    def setValue(self, value): send("PROGRESS")
    def reset(self): pass


def read_exactly(n):
    data = bytearray()
    while len(data) < n:
        chunk = inp.read(n - len(data))
        if not chunk:
            raise EOFError("stdin closed")
        data.extend(chunk)
    return bytes(data)


def connect(pipe):
    ctx = uno.getComponentContext()
    resolver = ctx.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", ctx)
    deadline = time.time() + 60
    while True:
        try:
            remote = resolver.resolve(f"uno:pipe,name={pipe};urp;StarOffice.ComponentContext")
            return remote.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", remote)
        except Exception:
            if time.time() > deadline:
                raise
            time.sleep(0.1)


def convert(desktop, work, fmt, data):
    filter_name, ext = FILTERS[fmt]
    src = os.path.join(work, "in.odt")
    dst = os.path.join(work, "out." + ext)
    with open(src, "wb") as f:
        f.write(data)
    doc = desktop.loadComponentFromURL(uno.systemPathToFileUrl(src), "_blank", 0,
                                       (prop("Hidden", True), prop("ReadOnly", True)))
    if doc is None:
        raise RuntimeError("LibreOffice could not load the document")
    send("PROGRESS")
    try:
        doc.storeToURL(uno.systemPathToFileUrl(dst),
                       (prop("FilterName", filter_name), prop("StatusIndicator", Progress())))
    finally:
        doc.close(True)
    with open(dst, "rb") as f:
        result = f.read()
    os.remove(src)
    os.remove(dst)
    return result


def main():
    work = tempfile.mkdtemp(prefix="bp-lo-")
    pipe = f"blocpress{os.getpid()}"
    soffice = subprocess.Popen(
        ["soffice", "-env:UserInstallation=" + uno.systemPathToFileUrl(os.path.join(work, "profile")),
         "--headless", "--invisible", "--nologo", "--nodefault", "--norestore", "--nolockcheck",
         f"--accept=pipe,name={pipe};urp;"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    desktop = None
    try:
        desktop = connect(pipe)
        send("READY")
        while True:
            line = inp.readline()
            if not line:
                break
            parts = line.decode("utf-8").split()
            if len(parts) != 3 or parts[0] != "CONVERT" or parts[1] not in FILTERS:
                send("FAIL malformed request")
                continue
            data = read_exactly(int(parts[2]))
            try:
                result = convert(desktop, work, parts[1], data)
                send(f"OK {len(result)}", result)
            except Exception as e:
                send("FAIL " + " ".join(str(e).split()))
    finally:
        try:
            if desktop is not None:
                desktop.terminate()
        except Exception:
            pass
        try:
            soffice.wait(timeout=10)
        except Exception:
            soffice.kill()
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
