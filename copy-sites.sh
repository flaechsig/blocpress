#!/bin/bash
# Copy module site directories to parent site
# Run this after: mvn clean verify site

PARENT_SITE="target/site"

if [ ! -d "$PARENT_SITE" ]; then
    echo "Error: Parent site directory not found at $PARENT_SITE"
    exit 1
fi

echo "Copying module sites to parent site..."

# Copy each module's site
for module in blocpress-core blocpress-render blocpress-workbench blocpress-studio; do
    SOURCE="${module}/target/site"
    DEST="${PARENT_SITE}/${module}"

    if [ -d "$SOURCE" ]; then
        echo "Copying $SOURCE to $DEST"
        cp -r "$SOURCE" "$DEST"
    else
        echo "Warning: $SOURCE not found"
    fi
done

echo "Done! Module sites copied to parent site."
