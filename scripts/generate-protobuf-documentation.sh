#!/bin/bash

set -e  # Exit on error

# Get the script's directory and project root
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed or not in PATH"
    exit 1
fi

# Change to project root directory
cd "$PROJECT_ROOT"

# Proto directory
PROTO_DIR="$PROJECT_ROOT/schemas/src/main/proto"
DESCRIPTOR_FILE="$PROTO_DIR/descriptor.pb"
OUTPUT_DIR="$PROJECT_ROOT/docs"
CONFIG_FILE="$SCRIPT_DIR/sabledocs.toml"

# Create output directory
print_info "Creating output directory..."
mkdir -p "$OUTPUT_DIR"

# Check if config file exists
if [ ! -f "$CONFIG_FILE" ]; then
    print_warn "sabledocs.toml not found, using default configuration"
fi

# Generate Protobuf descriptor file
print_info "Generating Protobuf descriptor file..."
docker run --rm \
    -v "$PROTO_DIR:/protos" \
    -w /protos \
    markvincze/sabledocs \
    sh -c "protoc agent.proto -o descriptor.pb --include_source_info"

# Check if descriptor file was generated
if [ ! -f "$DESCRIPTOR_FILE" ]; then
    print_error "Descriptor file was not generated"
    exit 1
fi

print_info "Descriptor file generated successfully"

# Generate documentation using sabledocs
# Mount project root to access both proto directory and config file
print_info "Generating documentation with sabledocs using config from $CONFIG_FILE..."
docker run --rm \
    -v "$PROJECT_ROOT:/project" \
    -v "$OUTPUT_DIR:/output" \
    -w /project/schemas/src/main/proto \
    markvincze/sabledocs \
    sh -c "if [ -f /project/scripts/sabledocs.toml ]; then cp /project/scripts/sabledocs.toml ./sabledocs.toml && echo 'Using config file from /project/scripts/sabledocs.toml'; fi && sabledocs && if [ -d sabledocs_output ]; then cp -r sabledocs_output/* /output/; elif [ -d docs ]; then cp -r docs/* /output/; fi"

# Check if documentation was generated
if [ ! -f "$OUTPUT_DIR/index.html" ]; then
    print_error "Documentation file was not generated"
    exit 1
fi

print_info "Documentation generated successfully in $OUTPUT_DIR"
print_info "Open $OUTPUT_DIR/index.html in your browser to view the documentation"

