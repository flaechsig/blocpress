# =============================================================================
# blocpress-studio — All-in-one image for quickstart / local development
#
# Bundles:
#   - blocpress-studio    (Studio landing + router, port 8080)
#   - blocpress-render    (Render API, port 8081)
#   - blocpress-workbench (Workbench UI, internal port 8082)
#   - PostgreSQL 18       (internal, not exposed)
#   - Elasticsearch 8.11  (internal, not exposed)
#
# Build from project root:
#   mvn clean package -DskipTests
#   docker build -f docker/studio/Dockerfile -t flaechsig/blocpress-studio-quickstart:latest .
#
# Run:
#   docker run -d -p 8080:8080 -p 8081:8081 -v blocpress-data:/data --name blocpress flaechsig/blocpress-studio-quickstart:latest
#
# Workbench UI:  http://localhost:8080
# Render API:    http://localhost:8081
# Data (PostgreSQL, Elasticsearch): volume /data
# Dev JWT token: docs/samples/quickstart/token.txt
# =============================================================================

FROM ubuntu:24.04

ARG DEBIAN_FRONTEND=noninteractive

# ---------------------------------------------------------------------------
# System packages
# ---------------------------------------------------------------------------
RUN set -eux; \
    sed -i 's/^Components: *main/Components: main universe/' /etc/apt/sources.list.d/ubuntu.sources; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      # Runtime
      ca-certificates \
      openjdk-21-jre-headless \
      # LibreOffice (for render → PDF/RTF export)
      fontconfig \
      fonts-dejavu \
      fonts-liberation \
      fonts-urw-base35 \
      fonts-noto-core \
      fonts-noto-color-emoji \
      fonts-freefont-ttf \
      curl \
      libreoffice-core \
      libreoffice-writer \
      # PDF diff (for workbench regression tests)
      poppler-utils \
      imagemagick \
      # Database: PostgreSQL 18 from the PGDG repository (installed below)
      postgresql-common \
      # Process manager
      supervisor; \
    /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y; \
    apt-get install -y --no-install-recommends postgresql-18; \
    rm -rf /var/lib/apt/lists/*; \
    # Allow ImageMagick to read/write PDF files
    sed -i 's/rights="none" pattern="PDF"/rights="read|write" pattern="PDF"/' /etc/ImageMagick-6/policy.xml

# ---------------------------------------------------------------------------
# Elasticsearch 8.11 (full-text search, UC-19 / TI-7)
# Runs as dedicated system user — ES 8.x refuses to start as root
# ---------------------------------------------------------------------------
RUN set -eux; \
    curl -fsSL https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-8.11.0-linux-x86_64.tar.gz \
        -o /tmp/es.tar.gz; \
    tar -xzf /tmp/es.tar.gz -C /opt; \
    mv /opt/elasticsearch-8.11.0 /opt/elasticsearch; \
    rm /tmp/es.tar.gz; \
    useradd -r -s /bin/false -d /opt/elasticsearch elasticsearch; \
    mkdir -p /opt/elasticsearch/data /opt/elasticsearch/logs; \
    chown -R elasticsearch:elasticsearch /opt/elasticsearch

# ES configuration: single-node, no security (dev quickstart)
RUN printf 'cluster.name: blocpress\n\
node.name: quickstart\n\
discovery.type: single-node\n\
xpack.security.enabled: false\n\
network.host: 127.0.0.1\n\
path.data: /data/elasticsearch\n\
path.logs: /opt/elasticsearch/logs\n' \
    > /opt/elasticsearch/config/elasticsearch.yml

# ---------------------------------------------------------------------------
# PostgreSQL: trust-based local auth (dev only — no passwords needed)
# ---------------------------------------------------------------------------
RUN echo "host all all 127.0.0.1/32 trust" >> /etc/postgresql/18/main/pg_hba.conf && \
    echo "local all all trust"              >> /etc/postgresql/18/main/pg_hba.conf && \
    sed -i "s/#listen_addresses = 'localhost'/listen_addresses = 'localhost'/" \
        /etc/postgresql/18/main/postgresql.conf && \
    sed -i "s|^data_directory = .*|data_directory = '/data/postgresql'|" \
        /etc/postgresql/18/main/postgresql.conf && \
    grep -q "^data_directory = '/data/postgresql'" /etc/postgresql/18/main/postgresql.conf

# ---------------------------------------------------------------------------
# Data volume: /data/postgresql and /data/elasticsearch, created by entrypoint.sh
# when empty. Without a volume the data is lost with the container.
# ---------------------------------------------------------------------------
VOLUME /data

# ---------------------------------------------------------------------------
# Application JARs (built with mvn clean package -DskipTests)
# ---------------------------------------------------------------------------
COPY blocpress-studio/target/quarkus-app/    /app/studio/
COPY blocpress-render/target/quarkus-app/    /app/render/
COPY blocpress-workbench/target/quarkus-app/ /app/workbench/

# ---------------------------------------------------------------------------
# Database initialisation SQL
# ---------------------------------------------------------------------------
COPY docker/studio/init-studio.sql /docker-entrypoint-initdb.d/init-studio.sql

# ---------------------------------------------------------------------------
# supervisord config + startup script
# ---------------------------------------------------------------------------
COPY docker/studio/supervisord.conf /etc/supervisor/conf.d/blocpress.conf
COPY --chmod=755 docker/studio/entrypoint.sh /entrypoint.sh

# ---------------------------------------------------------------------------
# Built-in dev JWT (matches docs/samples/quickstart/token.txt)
# Override via docker run -e MP_JWT_VERIFY_PUBLICKEY=... -e MP_JWT_VERIFY_ISSUER=...
# ---------------------------------------------------------------------------
ENV MP_JWT_VERIFY_PUBLICKEY="MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsKU92I7dzo/eEOyQtS3KLZ3D8KEpzvClAWjzzzXck2WQV+QTFH6+meHOetww5ZWCfRT8B1LU4A9buMxY8OuXQCnEP5vYCeFmYz7JCXHEceprpQbBm7CRrwUzJNJxW9cCl+bG/JrGYUV9rgW6zWSYFFel49DGQH0BUovDoeqD70bWRt9sP76mRl97ELk7wABPSare9L3UiWhDnQVn+mabOG9wNFRui/8awRmvOoV1kY3M1gbz34sn3Kx7K0kEip1LBbt5xeVnv3N7WDiP52OtSByqqX180jLnaRobTGu2Zt6gZGa1LHbydWOm+CH6TuBFL4NLB/YvUypYl/Xty8DQfQIDAQAB"
ENV MP_JWT_VERIFY_ISSUER="https://blocpress.dev"
ENV HOME=/tmp

EXPOSE 8080 8081

# Healthy only when studio, render and workbench are ready; the readiness of render and
# workbench includes the database, that of workbench also Elasticsearch.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s \
  CMD curl -sf http://localhost:8080/q/health/ready \
   && curl -sf http://localhost:8081/q/health/ready \
   && curl -sf http://localhost:8082/q/health/ready || exit 1

ENTRYPOINT ["/entrypoint.sh"]
