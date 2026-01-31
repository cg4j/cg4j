#!/bin/bash
set -e  # Exit on any error

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
success() {
  echo -e "${GREEN}✓${NC} $1"
}

error() {
  echo -e "${RED}✗${NC} $1" >&2
  exit 1
}

warning() {
  echo -e "${YELLOW}⚠${NC} $1"
}

info() {
  echo -e "${BLUE}ℹ${NC} $1"
}

# Start installation
echo ""
info "Installing cg4j..."
echo ""

# Check Java version
info "Checking Java version..."
if ! command -v java &> /dev/null; then
  error "Java not found. Please install Java 11 or higher."
fi

# Extract Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
JAVA_MAJOR_VERSION=$(echo "$JAVA_VERSION" | awk -F. '{print $1}')

# Handle Java version format (1.8.x becomes 8, 11.x becomes 11, etc.)
if [ "$JAVA_MAJOR_VERSION" = "1" ]; then
  JAVA_MAJOR_VERSION=$(echo "$JAVA_VERSION" | awk -F. '{print $2}')
fi

if [ "$JAVA_MAJOR_VERSION" -lt 11 ]; then
  error "Java $JAVA_VERSION found, but Java 11 or higher is required."
fi

success "Java $JAVA_VERSION found (requires 11+)"
echo ""

# Check Maven
info "Checking Maven..."
if ! command -v mvn &> /dev/null; then
  error "Maven not found. Please install Maven 3.6 or higher."
fi

MVN_VERSION=$(mvn -version | head -1 | awk '{print $3}')
success "Maven $MVN_VERSION found"
echo ""

# Build the project
info "Building cg4j (skipping tests)..."
echo ""
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
  error "Build failed. Please check the Maven output above."
fi

echo ""
success "Build completed successfully"
echo ""

# Create installation directories
info "Installing to ~/.local/bin..."
mkdir -p "$HOME/.local/bin"
mkdir -p "$HOME/.local/share/cg4j"

# Copy JAR file and rename
JAR_FILE="target/cg4j-0.1.0-SNAPSHOT-jar-with-dependencies.jar"
if [ ! -f "$JAR_FILE" ]; then
  error "JAR file not found: $JAR_FILE"
fi

cp "$JAR_FILE" "$HOME/.local/share/cg4j/cg4j.jar"
success "Copied cg4j.jar to ~/.local/share/cg4j/"

# Create wrapper script
cat > "$HOME/.local/bin/cg4j" << 'EOF'
#!/bin/bash
exec java -jar "$HOME/.local/share/cg4j/cg4j.jar" "$@"
EOF

chmod +x "$HOME/.local/bin/cg4j"
success "Created wrapper script at ~/.local/bin/cg4j"
echo ""

# Check if ~/.local/bin is in PATH
if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
  warning "~/.local/bin is not in your PATH"
  echo ""
  echo "  Add this line to your ~/.bashrc or ~/.zshrc:"
  echo "  export PATH=\"\$HOME/.local/bin:\$PATH\""
  echo ""
  echo "  Then restart your terminal or run: source ~/.bashrc"
  echo ""
fi

# Extract version from the built JAR (includes git commit hash)
PROJECT_VERSION=$(java -jar "$HOME/.local/share/cg4j/cg4j.jar" --version 2>/dev/null || echo "0.1.0-SNAPSHOT")

success "cg4j $PROJECT_VERSION installed successfully!"
echo ""
echo "  Try: cg4j --help"
echo ""
