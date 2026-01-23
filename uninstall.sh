#!/bin/bash

# Color codes
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
success() {
  echo -e "${GREEN}✓${NC} $1"
}

info() {
  echo -e "${BLUE}ℹ${NC} $1"
}

echo ""
info "Uninstalling cg4j..."
echo ""

# Remove wrapper script
if [ -f "$HOME/.local/bin/cg4j" ]; then
  rm "$HOME/.local/bin/cg4j"
  success "Removed ~/.local/bin/cg4j"
else
  info "~/.local/bin/cg4j not found (already removed)"
fi

# Remove JAR directory
if [ -d "$HOME/.local/share/cg4j" ]; then
  rm -rf "$HOME/.local/share/cg4j"
  success "Removed ~/.local/share/cg4j"
else
  info "~/.local/share/cg4j not found (already removed)"
fi

echo ""
success "cg4j uninstalled successfully!"
echo ""
