FROM ubuntu:24.04

ARG DEBIAN_FRONTEND=noninteractive

RUN set -eux; \
    sed -i 's/^Components: *main/Components: main universe/' /etc/apt/sources.list.d/ubuntu.sources; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      openjdk-21-jre-headless \
      fontconfig \
      fonts-dejavu \
      fonts-liberation \
      fonts-urw-base35 \
      fonts-noto-core \
      fonts-noto-cjk \
      fonts-noto-color-emoji \
      fonts-freefont-ttf \
      curl \
      libreoffice-core \
      libreoffice-writer \
      python3-uno; \
    rm -rf /var/lib/apt/lists/*;

# Unprivilegierter Benutzer (REQ-0050). Numerische UID, damit Kubernetes runAsNonRoot
# pruefen kann; HOME=/tmp bleibt das einzige Verzeichnis, in das der Dienst schreibt.
RUN useradd --system --uid 10001 --user-group --no-create-home --home-dir /tmp \
      --shell /usr/sbin/nologin blocpress

ENV HOME=/tmp
WORKDIR /app

# Quarkus "fast-jar" Layout
COPY target/quarkus-app/ /app/

USER 10001:10001

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=15s \
  CMD curl -sf http://localhost:8080/q/health/ready || exit 1
CMD ["java", "-jar", "/app/quarkus-run.jar"]