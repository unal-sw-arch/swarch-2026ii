from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
import threading


class WorkerHealthServer:
    def __init__(self, is_ready):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                if self.path != "/health":
                    self.send_error(404)
                    return
                ready = is_ready()
                content = json.dumps({"status": "ok" if ready else "unavailable"}).encode()
                self.send_response(200 if ready else 503)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(content)))
                self.end_headers()
                self.wfile.write(content)

            def log_message(self, *args):
                pass

        self.server = ThreadingHTTPServer(
            ("0.0.0.0", int(os.getenv("WORKER_HEALTH_PORT", "8091"))), Handler
        )
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def start(self):
        self.thread.start()

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
