"""Listen to VanGO IO module UART on COM14."""
import sys
import time

import serial

port = sys.argv[1] if len(sys.argv) > 1 else "COM14"
baud = int(sys.argv[2]) if len(sys.argv) > 2 else 115200
out_path = sys.argv[3] if len(sys.argv) > 3 else "scripts/_io_serial.log"

ser = serial.Serial(port, baud, timeout=0.2, write_timeout=1)
print(f"OPEN {port} {baud}", flush=True)
with open(out_path, "a", encoding="utf-8", errors="replace") as f:
    f.write(f"\n===== {time.strftime('%Y-%m-%d %H:%M:%S')} {port} {baud} =====\n")
    buf = b""
    t0 = time.time()
    while time.time() - t0 < 180:
        chunk = ser.read(4096)
        if chunk:
            buf += chunk
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                s = line.decode("utf-8", errors="replace").rstrip("\r")
                if s:
                    print(s, flush=True)
                    f.write(s + "\n")
                    f.flush()
ser.close()
