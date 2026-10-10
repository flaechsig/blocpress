"""
Attrappe des LibreOffice-Helfers fuer Tests (gleiches Protokoll wie bp-convert.py).
Das Verhalten steuert der Inhalt des Dokuments:
  HANG  - keine Antwort, kein Fortschritt
  SPIN  - meldet endlos Fortschritt, wird nie fertig
  CRASH - beendet sich mitten in der Konvertierung
  sonst - meldet einmal Fortschritt und gibt den Inhalt zurueck
Mit dem Argument "silent" meldet sie nie READY.
"""
import sys
import time

out = sys.stdout.buffer
inp = sys.stdin.buffer


def send(line, payload=None):
    out.write(line.encode() + b"\n")
    if payload is not None:
        out.write(payload)
    out.flush()


if len(sys.argv) > 1 and sys.argv[1] == "silent":
    time.sleep(3600)
send("READY")
while True:
    line = inp.readline()
    if not line:
        break
    _, fmt, n = line.decode().split()
    data = inp.read(int(n))
    if data == b"HANG":
        time.sleep(3600)
    elif data == b"SPIN":
        while True:
            send("PROGRESS")
            time.sleep(0.05)
    elif data == b"CRASH":
        sys.exit(1)
    else:
        send("PROGRESS")
        send(f"OK {len(data)}", data)
