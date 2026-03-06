#!/bin/bash
read -s -p "Docker Hub Password: " DHPASS
echo

TOKEN=$(curl -s -X POST "https://hub.docker.com/v2/users/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"flaechsig\",\"password\":\"$DHPASS\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

if [ -z "$TOKEN" ]; then
  echo "Login fehlgeschlagen."
  exit 1
fi

README=$(python3 -c "import json; print(json.dumps(open('docs/dockerhub.md').read()))")
SHORT_DESC="Java REST API: render LibreOffice ODT templates to PDF/RTF. Self-hosted, open source."

curl -s -X PATCH "https://hub.docker.com/v2/repositories/flaechsig/blocpress-render/" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"description\": \"$SHORT_DESC\", \"full_description\": $README}" \
  && echo "✓ Docker Hub README und Beschreibung aktualisiert"
