import json
import socket
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

from ctypes import cast, POINTER
from comtypes import CLSCTX_ALL
from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume

PORT = 5050
TOKEN = "schimba-ma"  # schimba acest token pentru securitate minima


def get_volume_interface():
    devices = AudioUtilities.GetSpeakers()
    interface = devices.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
    return cast(interface, POINTER(IAudioEndpointVolume))


def get_volume_percent():
    vol = get_volume_interface()
    return round(vol.GetMasterVolumeLevelScalar() * 100)


def set_volume_percent(level):
    level = max(0, min(100, level))
    vol = get_volume_interface()
    vol.SetMasterVolumeLevelScalar(level / 100.0, None)
    return level


def set_mute(state: bool):
    vol = get_volume_interface()
    vol.SetMute(1 if state else 0, None)


class Handler(BaseHTTPRequestHandler):
    def _check_token(self, qs):
        return qs.get("token", [""])[0] == TOKEN

    def _send_json(self, data, code=200):
        body = json.dumps(data).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)

        if not self._check_token(qs):
            self._send_json({"error": "token invalid"}, 401)
            return

        if parsed.path == "/volume":
            level_param = qs.get("level")
            if level_param:
                try:
                    level = int(level_param[0])
                except ValueError:
                    self._send_json({"error": "level invalid"}, 400)
                    return
                new_level = set_volume_percent(level)
                self._send_json({"volume": new_level})
            else:
                self._send_json({"volume": get_volume_percent()})
        elif parsed.path == "/mute":
            set_mute(True)
            self._send_json({"muted": True})
        elif parsed.path == "/unmute":
            set_mute(False)
            self._send_json({"muted": False})
        elif parsed.path == "/ping":
            self._send_json({"status": "ok", "host": socket.gethostname()})
        else:
            self._send_json({"error": "not found"}, 404)

    def log_message(self, format, *args):
        pass  # nu mai afisam fiecare request in consola


def main():
    server = HTTPServer(("0.0.0.0", PORT), Handler)
    try:
        ip = socket.gethostbyname(socket.gethostname())
    except Exception:
        ip = "127.0.0.1"
    print(f"Volume Agent ruleaza pe {ip}:{PORT} (token={TOKEN})")
    print("Lasa fereastra deschisa (sau ruleaza VolumeAgent.exe in fundal).")
    server.serve_forever()


if __name__ == "__main__":
    main()
