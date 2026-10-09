FROM ubuntu:24.04

ARG DEBIAN_FRONTEND=noninteractive

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      openjdk-21-jre-headless \
      curl; \
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

EXPOSE 8082
HEALTHCHECK --interval=10s --timeout=3s --start-period=15s \
  CMD curl -sf http://localhost:8082/q/health/ready || exit 1
CMD ["java", "-jar", "/app/quarkus-run.jar"]
