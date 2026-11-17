#!/bin/sh
# Runs Kanasta Admin inside the container and shows it in the browser (noVNC)
Xvfb :0 -screen 0 1280x800x24 -wr &
sleep 1
x11vnc -display :0 -forever -shared -nopw -quiet -rfbport 5900 &
websockify --web /usr/share/novnc 6080 localhost:5900 &
while true; do
  python3 main.py
  sleep 1
done
