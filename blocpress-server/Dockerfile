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
      libreoffice-core \
      libreoffice-writer; \
    rm -rf /var/lib/apt/lists/*;

ENV HOME=/tmp
WORKDIR /app

# Quarkus "fast-jar" Layout
COPY target/quarkus-app/ /app/

EXPOSE 8080
CMD ["java", "-jar", "/app/quarkus-run.jar"]