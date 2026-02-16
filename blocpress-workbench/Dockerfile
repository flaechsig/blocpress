FROM ubuntu:24.04

ARG DEBIAN_FRONTEND=noninteractive

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      openjdk-21-jre-headless \
      curl; \
    rm -rf /var/lib/apt/lists/*;

ENV HOME=/tmp
WORKDIR /app

# Quarkus "fast-jar" Layout
COPY target/quarkus-app/ /app/

EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=15s \
  CMD curl -sf http://localhost:8081/q/health/ready || exit 1
CMD ["java", "-jar", "/app/quarkus-run.jar"]
