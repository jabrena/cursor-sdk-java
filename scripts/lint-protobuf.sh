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

# Lint Protobuf contract
print_info "Linting Protobuf contract..."
docker run --rm \
    --volume "$PROJECT_ROOT:/workspace" \
    --workdir /workspace \
    yoheimuta/protolint lint schemas/src/main/proto/agent.proto

print_info "Protobuf linting completed successfully"

