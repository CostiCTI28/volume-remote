import json
import socket
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

from ctypes import cast, POINTER
import comtypes
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
    # HTTP/1.1 + Content-Length explicit peste tot, ca sa nu se rupa
    # conexiunea inainte ca clientul sa primeasca raspunsul complet.
    protocol_version = "HTTP/1.1"
    # raspunsurile sunt scurte; nu tinem conexiunea deschisa degeaba
    close_connection = True

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
        # Orice exceptie de aici in jos (COM/pycaw poate arunca oricand)
        # e prinsa si trimisa ca JSON, nu lasata sa taie conexiunea.
        try:
            self._handle_get()
        except Exception as e:
            traceback.print_exc()
            try:
                self._send_json({"error": str(e)}, 500)
            except Exception:
                pass  # conexiunea era deja compromisa, nimic de facut

    def _handle_get(self):
        # pycaw foloseste COM; fiecare thread care il apeleaza trebuie
        # sa aiba apartamentul COM initializat (altfel arunca eroare
        # si conexiunea moare fara raspuns -> "unexpected end of stream").
        try:
            comtypes.CoInitialize()
        except OSError:
            pass  # deja initializat pe acest thread
        except Exception:
            pass

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


class Server(ThreadingHTTPServer):
    daemon_threads = True      # firele mor odata cu procesul
    allow_reuse_address = True # repornire imediata dupa oprire


def main():
    server = Server(("0.0.0.0", PORT), Handler)
    try:
        ip = socket.gethostbyname(socket.gethostname())
    except Exception:
        ip = "127.0.0.1"
    print(f"Volume Agent ruleaza pe {ip}:{PORT} (token={TOKEN})")
    print("Lasa fereastra deschisa. Opreste cu Ctrl+C.")
    server.serve_forever()


if __name__ == "__main__":
    main()
