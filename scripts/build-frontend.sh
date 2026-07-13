#!/bin/sh
# Builds the frontend and copies the resulting dist into the backend's static resources folder.

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend"
STATIC_DIR="$REPO_ROOT/src/main/resources/static"

echo "Building frontend..."
cd "$FRONTEND_DIR"
yarn build

echo "Copying dist to $STATIC_DIR..."
rm -rf "$STATIC_DIR"
cp -r "$FRONTEND_DIR/dist" "$STATIC_DIR"

echo "Done."
