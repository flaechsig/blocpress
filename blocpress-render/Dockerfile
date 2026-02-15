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
      libreoffice-writer; \
    rm -rf /var/lib/apt/lists/*;

ENV HOME=/tmp
WORKDIR /app

# Quarkus "fast-jar" Layout
COPY target/quarkus-app/ /app/

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=15s \
  CMD curl -sf http://localhost:8080/q/health/ready || exit 1
CMD ["java", "-jar", "/app/quarkus-run.jar"]