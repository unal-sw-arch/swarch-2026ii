FROM debian:bookworm-slim
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 python3-pyqt5 python3-requests \
    xvfb x11vnc novnc websockify fonts-dejavu-core tzdata \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY main.py start.sh ./
COPY app ./app
COPY assets ./assets
ENV KANASTA_API=http://api-gateway:8000 \
    KANASTA_FULLSCREEN=1 \
    DISPLAY=:0 \
    QT_X11_NO_MITSHM=1
EXPOSE 6080
CMD ["sh", "start.sh"]
