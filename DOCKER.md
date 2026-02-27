# Blocpress Docker Compose

Starten des kompletten Blocpress-Systems mit Docker Compose.

## Voraussetzungen

- Docker 20.10+
- Docker Compose 2.0+
- Mindestens 4GB RAM für alle Container
- Ports 4200, 5432, 8080, 8081 müssen verfügbar sein

## Schnellstart

### 1. Docker Images bauen

```bash
# Alle Images bauen
docker-compose build

# Oder mit no-cache für sauberen Build
docker-compose build --no-cache
```

### 2. System starten

```bash
# Im Hintergrund starten
docker-compose up -d

# Mit Live-Logs starten (Ctrl+C zum Beenden)
docker-compose up
```

### 3. Health Check

```bash
# Status aller Services überprüfen
docker-compose ps

# Logs eines Services anschauen
docker-compose logs workbench
docker-compose logs render
docker-compose logs studio
```

## Services

### 🗄️ PostgreSQL (Port 5432)
- **Container**: blocpress-postgres
- **Database**: workbench
- **User**: workbench
- **Password**: workbench
- **Daten**: Persistent in `postgres_data` Volume

### 📤 Workbench (Port 8081)
- **Container**: blocpress-workbench
- **URL**: http://localhost:8081
- **API Base**: http://localhost:8081/api
- **Features**: Template Upload, Management, Details, Submit
- **Health**: http://localhost:8081/q/health/ready

### 📄 Render API (Port 8080)
- **Container**: blocpress-render
- **URL**: http://localhost:8080
- **API Base**: http://localhost:8080/api
- **Features**: Document Generation (ODT/PDF/RTF)
- **Health**: http://localhost:8080/q/health/ready

### 🎨 Studio (Port 4200)
- **Container**: blocpress-studio
- **URL**: http://localhost:4200
- **Features**: Template Designer Web UI
- **Health**: http://localhost:4200

## Häufige Kommandos

### System starten/stoppen

```bash
# Alle Services starten
docker-compose up -d

# Alle Services stoppen (Daten bleiben erhalten)
docker-compose stop

# Alle Services stoppen und entfernen
docker-compose down

# Neu starten
docker-compose restart workbench
```

### Logs anschauen

```bash
# Alle Logs anschauen
docker-compose logs -f

# Logs eines spezifischen Services
docker-compose logs -f workbench

# Letzte 100 Zeilen
docker-compose logs --tail=100 workbench
```

### In Container gehen

```bash
# Shell im workbench Container
docker-compose exec workbench /bin/bash

# psql im Postgres Container
docker-compose exec postgres psql -U workbench -d workbench
```

### Datenbank zurücksetzen

```bash
# Volume löschen (⚠️ Daten gehen verloren!)
docker-compose down -v

# Danach neu starten
docker-compose up -d
```

## Netzwerk

Alle Services sind in einem privaten Docker-Netzwerk `blocpress` verbunden:
- Services können sich untereinander über Service-Namen erreichen
- z.B. `postgres:5432`, `workbench:8081`, `render:8080`

## Health Checks

Jeder Service hat einen Health Check:

```bash
# Status überprüfen
docker-compose ps

# Column "STATUS" zeigt "healthy" wenn alles OK
CONTAINER ID   IMAGE                         COMMAND                  STATUS
abc123         blocpress-postgres            "docker-entrypoint..."   Up 2m (healthy)
def456         blocpress-workbench           "java -jar /app/..."     Up 2m (healthy)
ghi789         blocpress-render              "java -jar /app/..."     Up 2m (healthy)
jkl012         blocpress-studio              "npm run build && ..."   Up 2m (healthy)
```

## Umgebungsvariablen

Angepasst werden können in `docker-compose.yml`:

```yaml
# Workbench Database
QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://postgres:5432/workbench
QUARKUS_DATASOURCE_USERNAME: workbench
QUARKUS_DATASOURCE_PASSWORD: workbench

# CORS Origins
QUARKUS_HTTP_CORS_ORIGINS: "/.*/|http://localhost:4200"

# Studio API URLs
API_WORKBENCH_URL: http://localhost:8081/api
API_RENDER_URL: http://localhost:8080/api
```

## Volumes

- **postgres_data**: PostgreSQL Datenbank-Dateien
  - Pfad: `/var/lib/postgresql/data`
  - Persistent zwischen Container-Restarts

## Ports

| Service | Port | Protocol |
|---------|------|----------|
| Workbench | 8081 | HTTP |
| Render | 8080 | HTTP |
| Studio | 4200 | HTTP |
| PostgreSQL | 5432 | TCP |

## Troubleshooting

### Port bereits in Benutzung

```bash
# Port 8081 prüfen
lsof -i :8081

# Service auf anderem Port starten
# In docker-compose.yml ändern: "8082:8081"
```

### Container startet nicht

```bash
# Logs anschauen
docker-compose logs workbench

# Container neustarten
docker-compose restart workbench

# Kompletter Rebuild
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

### Datenbankverbindung fehlgeschlagen

```bash
# Postgres Health überprüfen
docker-compose ps postgres

# Wenn nicht healthy, neustarten
docker-compose restart postgres

# Logs überprüfen
docker-compose logs postgres
```

### Datenbank löschen und neu initialisieren

```bash
# Nur Datenbank löschen (Volume entfernen)
docker-compose down -v

# System neu starten (DB wird automatisch initialisiert)
docker-compose up -d
```

## Performance-Tipps

1. **Docker Desktop RAM**: Mind. 4GB in Docker Desktop Preferences
2. **Build caching**: `docker-compose build` nutzt Caching (schneller)
3. **Parallel starten**: Docker startet Services parallel (abhängig von `depends_on`)
4. **Log-Rotation**: In Production sollte Log-Rotation konfiguriert werden

## Production Deployment

Für Production sollten folgende Änderungen gemacht werden:

1. **Passwörter**: `workbench` durch sichere Passwörter ersetzen
2. **Image Tags**: `build` durch versionierte `image` Tags ersetzen
3. **Restart Policy**: `unless-stopped` kann zu `always` geändert werden
4. **Resource Limits**: CPU und Memory Limits hinzufügen
5. **Logging**: Debug-Level auf Error/Warn reduzieren
6. **Network**: Private Networks für Datenbankzugriff nutzen

Beispiel für Production:

```yaml
services:
  workbench:
    image: blocpress/workbench:1.3.0  # statt build
    restart: always
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```
