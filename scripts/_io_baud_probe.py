"""Probe COM14 for any UART bytes at several bauds (hex dump)."""
import sys
import time

import serial

port = sys.argv[1] if len(sys.argv) > 1 else "COM14"
bauds = [115200, 9600, 57600, 230400, 38400]
for baud in bauds:
    try:
        ser = serial.Serial(port, baud, timeout=0.2)
        ser.dtr = False
        ser.rts = False
        time.sleep(0.05)
        ser.reset_input_buffer()
        t0 = time.time()
        buf = b""
        while time.time() - t0 < 4.0:
            buf += ser.read(4096)
        ser.close()
        print(f"{baud}: {len(buf)} bytes {buf[:80]!r}")
    except Exception as e:
        print(f"{baud}: ERROR {e}")
