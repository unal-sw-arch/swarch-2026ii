#!/usr/bin/env python3
"""Cliente WebSocket mínimo (stdlib) para probar el chat a mano, sin instalar websocat.

    python3 ws_client.py "ws://localhost:8080/api/chat/v1/ws?ticket=<ticket>"

Escribe un frame JSON por línea, p. ej.:
    {"type":"message.send","id":"f-1","payload":{"conversation_id":1,"client_message_id":"c-1","body":"Hola"}}
    {"type":"sync","id":"s-1","payload":{"conversations":[{"conversation_id":1,"last_seq":0}]}}
    {"type":"ping","id":"p-1","payload":{}}
Los frames que llegan del servidor se imprimen tal cual. Ctrl-D para salir.
"""
import base64
import os
import socket
import struct
import sys
import threading
import urllib.parse


def connect(url, origin):
    u = urllib.parse.urlparse(url)
    port = u.port or 80
    sock = socket.create_connection((u.hostname, port))
    key = base64.b64encode(os.urandom(16)).decode()
    path = u.path + ("?" + u.query if u.query else "")
    request = (f"GET {path} HTTP/1.1\r\nHost: {u.hostname}:{port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
               f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n"
               + (f"Origin: {origin}\r\n" if origin else "") + "\r\n")
    sock.sendall(request.encode())
    response = b""
    while b"\r\n\r\n" not in response:
        chunk = sock.recv(1024)
        if not chunk:
            raise SystemExit("El servidor cerró durante el handshake")
        response += chunk
    status = response.split(b"\r\n", 1)[0].decode()
    if " 101 " not in status:
        raise SystemExit(f"Handshake rechazado: {status}")
    return sock


def send_text(sock, text):
    payload = text.encode()
    header = bytearray([0x81])
    mask = os.urandom(4)
    n = len(payload)
    if n < 126:
        header.append(0x80 | n)
    elif n < 65536:
        header += bytes([0x80 | 126]) + struct.pack(">H", n)
    else:
        header += bytes([0x80 | 127]) + struct.pack(">Q", n)
    sock.sendall(bytes(header) + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(payload)))


def recv_exact(sock, n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise ConnectionError
        data += chunk
    return data


def reader(sock):
    try:
        while True:
            b1, b2 = recv_exact(sock, 2)
            opcode, n = b1 & 0x0F, b2 & 0x7F
            if n == 126:
                n = struct.unpack(">H", recv_exact(sock, 2))[0]
            elif n == 127:
                n = struct.unpack(">Q", recv_exact(sock, 8))[0]
            payload = recv_exact(sock, n)
            if opcode == 0x1:
                print("<", payload.decode(), flush=True)
            elif opcode == 0x8:
                code = struct.unpack(">H", payload[:2])[0] if len(payload) >= 2 else None
                print(f"< [cerrado por el servidor: {code} {payload[2:].decode(errors='replace')}]", flush=True)
                os._exit(0)
            elif opcode == 0x9:  # ping de protocolo → pong
                sock.sendall(bytes([0x8A, 0x80 | n]) + b"\x00\x00\x00\x00" + payload)  # máscara 0: payload intacto
    except ConnectionError:
        print("< [conexión cerrada]", flush=True)
        os._exit(0)


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    sock = connect(sys.argv[1], os.environ.get("WS_ORIGIN"))
    print("conectado; escribe frames JSON (uno por línea)", flush=True)
    threading.Thread(target=reader, args=(sock,), daemon=True).start()
    for line in sys.stdin:
        if line.strip():
            send_text(sock, line.strip())


if __name__ == "__main__":
    main()
