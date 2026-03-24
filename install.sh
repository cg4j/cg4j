#!/bin/bash
set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

METADATA_URL='https://repo1.maven.org/maven2/net/cg4j/cg4j/maven-metadata.xml'
MAVEN_BASE_URL='https://repo1.maven.org/maven2/net/cg4j/cg4j'
ARTIFACT_ID='cg4j'
ARTIFACT_SUFFIX='-jar-with-dependencies.jar'
INSTALL_BIN_DIR="$HOME/.local/bin"
INSTALL_SHARE_DIR="$HOME/.local/share/cg4j"
INSTALL_JAR="$INSTALL_SHARE_DIR/cg4j.jar"
INSTALL_VERSION_FILE="$INSTALL_SHARE_DIR/VERSION"
WRAPPER_PATH="$INSTALL_BIN_DIR/cg4j"
TAGLINE='CG4j: Call Graph Generation for Java'
LOGO_LINES=(
  '   ______________ __  _ '
  '  / ____/ ____/ // / (_)' 
  ' / /   / / __/ // /_/ / '
  '/ /___/ /_/ /__  __/ /  '
  '\____/\____/  /_/_/ /   '
  '               /___/    '
)

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

repeat_char() {
  local char="$1"
  local count="$2"
  local result

  printf -v result '%*s' "$count" ''
  printf '%s\n' "${result// /$char}"
}

center_text() {
  local text="$1"
  local width="$2"
  local text_length left_padding right_padding

  text_length=${#text}
  if [ "$text_length" -ge "$width" ]; then
    printf '%s\n' "$text"
    return
  fi

  left_padding=$(((width - text_length) / 2))
  right_padding=$((width - text_length - left_padding))
  printf '%*s%s%*s\n' "$left_padding" '' "$text" "$right_padding" ''
}

print_banner() {
  local logo_width box_inner_width box_outer_width logo_padding logo_indent border line centered_tagline

  logo_width=${#LOGO_LINES[0]}
  box_inner_width=$((${#TAGLINE} + 4))
  box_outer_width=$((box_inner_width + 2))
  logo_padding=$(((box_outer_width - logo_width) / 2))

  if [ "$logo_padding" -lt 0 ]; then
    logo_padding=0
  fi

  printf -v logo_indent '%*s' "$logo_padding" ''
  border="+$(repeat_char '-' "$box_inner_width" | tr -d '\n')+"
  centered_tagline=$(center_text "$TAGLINE" "$box_inner_width" | tr -d '\n')

  echo ""
  for line in "${LOGO_LINES[@]}"; do
    printf '%s%s\n' "$logo_indent" "$line"
  done
  printf '%s\n' "$border"
  printf '|%s|\n' "$centered_tagline"
  printf '%s\n' "$border"
  echo ""
}

cleanup() {
  if [ -n "${TMP_DIR:-}" ] && [ -d "$TMP_DIR" ]; then
    rm -rf "$TMP_DIR"
  fi
}

is_installed() {
  [ -f "$WRAPPER_PATH" ] || [ -d "$INSTALL_SHARE_DIR" ]
}

confirm_reinstall() {
  local answer

  if [ ! -r /dev/tty ]; then
    error 'Existing cg4j install found, but confirmation is required and no terminal is available.'
  fi

  while true; do
    printf 'cg4j is already installed. Uninstall it now? (y/n) ' > /dev/tty
    read -r answer < /dev/tty
    case "$answer" in
      y|Y|yes|YES|Yes)
        return 0
        ;;
      n|N|no|NO|No)
        error 'Installation aborted by user.'
        ;;
      *)
        printf 'Please answer y or n.\n' > /dev/tty
        ;;
    esac
  done
}

uninstall_existing_installation() {
  info 'Removing existing cg4j installation...'
  rm -f "$WRAPPER_PATH"
  rm -rf "$INSTALL_SHARE_DIR"
  success 'Removed existing cg4j installation'
  echo ""
}

extract_release_version() {
  sed -n 's:.*<release>\([^<][^<]*\)</release>.*:\1:p' "$1" | head -n 1
}

get_java_major_version() {
  local version="$1"
  local dot_index dash_index

  if [[ "$version" == 1.* ]]; then
    printf '%s\n' "$version" | awk -F. '{print $2}'
    return
  fi

  dot_index=${version%%.*}
  if [ "$dot_index" != "$version" ]; then
    printf '%s\n' "$dot_index"
    return
  fi

  dash_index=${version%%-*}
  if [ "$dash_index" != "$version" ]; then
    printf '%s\n' "$dash_index"
    return
  fi

  printf '%s\n' "$version"
}

detect_sha256_command() {
  if command -v sha256sum >/dev/null 2>&1; then
    SHA256_CMD='sha256sum'
  elif command -v shasum >/dev/null 2>&1; then
    SHA256_CMD='shasum -a 256'
  else
    error 'SHA-256 verification tool not found. Please install sha256sum or shasum.'
  fi
}

verify_sha256() {
  local jar_path="$1"
  local checksum_path="$2"
  local expected actual

  expected=$(tr -d '[:space:]' < "$checksum_path")
  if [ -z "$expected" ]; then
    error 'Downloaded SHA-256 checksum file is empty.'
  fi

  actual=$($SHA256_CMD "$jar_path" | awk '{print $1}')
  if [ "$actual" != "$expected" ]; then
    error "SHA-256 mismatch for downloaded cg4j artifact. Expected $expected but found $actual."
  fi
}

trap cleanup EXIT

print_banner

if is_installed; then
  warning 'Existing cg4j installation detected'
  confirm_reinstall
  uninstall_existing_installation
  success 'cg4j uninstalled successfully'
  echo 'Run the installer again later if you want to reinstall it.'
  echo ""
  exit 0
fi

info 'Installing cg4j...'
echo ""

info 'Checking Java version...'
if ! command -v java >/dev/null 2>&1; then
  error 'Java not found. Please install Java 11 or higher.'
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
JAVA_MAJOR_VERSION=$(get_java_major_version "$JAVA_VERSION")

if [ "$JAVA_MAJOR_VERSION" -lt 11 ]; then
  error "Java $JAVA_VERSION found, but Java 11 or higher is required."
fi

success "Java $JAVA_VERSION found (requires 11+)"
echo ""

info 'Checking SHA-256 verification support...'
detect_sha256_command
success "Using $SHA256_CMD for SHA-256 verification"
echo ""

TMP_DIR=$(mktemp -d)
METADATA_FILE="$TMP_DIR/maven-metadata.xml"

info 'Resolving latest cg4j release from Maven Central...'
curl -fsSL "$METADATA_URL" -o "$METADATA_FILE" || error 'Failed to download Maven metadata.'

RELEASE_VERSION=$(extract_release_version "$METADATA_FILE")
if [ -z "$RELEASE_VERSION" ]; then
  error 'Could not parse <release> from Maven metadata.'
fi

success "Resolved cg4j release $RELEASE_VERSION"
echo ""

ARTIFACT_NAME="$ARTIFACT_ID-$RELEASE_VERSION$ARTIFACT_SUFFIX"
ARTIFACT_URL="$MAVEN_BASE_URL/$RELEASE_VERSION/$ARTIFACT_NAME"
CHECKSUM_URL="$ARTIFACT_URL.sha256"
DOWNLOAD_JAR="$TMP_DIR/$ARTIFACT_NAME"
DOWNLOAD_SHA="$TMP_DIR/$ARTIFACT_NAME.sha256"

info 'Downloading cg4j release...'
curl -fsSL "$ARTIFACT_URL" -o "$DOWNLOAD_JAR" || error 'Failed to download cg4j JAR.'
curl -fsSL "$CHECKSUM_URL" -o "$DOWNLOAD_SHA" || error 'Failed to download cg4j SHA-256 checksum.'
success 'Download completed'
echo ""

info 'Verifying SHA-256 checksum...'
verify_sha256 "$DOWNLOAD_JAR" "$DOWNLOAD_SHA"
success 'Checksum verified'
echo ""

info 'Installing cg4j to ~/.local...'
mkdir -p "$INSTALL_BIN_DIR"
mkdir -p "$INSTALL_SHARE_DIR"
cp "$DOWNLOAD_JAR" "$INSTALL_JAR"
printf '%s\n' "$RELEASE_VERSION" > "$INSTALL_VERSION_FILE"

cat > "$WRAPPER_PATH" <<'EOF'
#!/bin/bash
exec java -jar "$HOME/.local/share/cg4j/cg4j.jar" "$@"
EOF

chmod +x "$WRAPPER_PATH"
success 'Installed cg4j files'
echo ""

if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
  warning '~/.local/bin is not in your PATH'
  echo ""
  echo '  Add this line to your shell profile:'
  echo '  export PATH="$HOME/.local/bin:$PATH"'
  echo ""
fi

info 'Validating installation...'
INSTALLED_VERSION=$($WRAPPER_PATH --version 2>/dev/null) || error 'Installed cg4j failed validation via --version.'
success "cg4j installed successfully: $INSTALLED_VERSION"
echo ""
echo '  Try: cg4j --help'
echo ""
