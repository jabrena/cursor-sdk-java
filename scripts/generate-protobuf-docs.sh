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

# Create docs directory
print_info "Creating docs directory..."
mkdir -p docs

# Generate Protobuf Documentation
print_info "Generating Protobuf documentation..."
docker run --rm \
    -v "$PROJECT_ROOT/docs:/out" \
    -v "$PROJECT_ROOT/schemas/src/main/proto:/protos" \
    pseudomuto/protoc-gen-doc \
    --doc_opt=html,index.html \
    agent.proto

# Check if documentation was generated
if [ ! -f "$PROJECT_ROOT/docs/index.html" ]; then
    print_error "Documentation file was not generated"
    exit 1
fi

print_info "Documentation generated successfully"

