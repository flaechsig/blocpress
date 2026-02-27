#!/bin/bash
# Aggregate module site reports into parent site

PARENT_SITE="target/site"

for module in blocpress-core blocpress-render blocpress-workbench blocpress-studio; do
    MODULE_SITE="${module}/target/site"

    if [ -d "$MODULE_SITE" ]; then
        echo "Aggregating $module reports..."

        # Create module directory in parent site
        mkdir -p "$PARENT_SITE/$module"

        # Copy entire module site to parent
        cp -r "$MODULE_SITE"/* "$PARENT_SITE/$module/" 2>/dev/null || true

        echo "✓ $module aggregated"
    fi
done

echo ""
echo "✓ Site reports aggregated successfully!"
echo "  View at: file://$PWD/$PARENT_SITE/index.html"
