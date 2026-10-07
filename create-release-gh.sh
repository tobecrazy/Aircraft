#!/bin/bash

# Aircraft v1.4.2 GitHub Release Creator (using GitHub CLI)
# Requires: gh (GitHub CLI) - Install: brew install gh

set -e

echo "=========================================="
echo "Aircraft v1.4.2 - GitHub Release Creator"
echo "=========================================="
echo ""

# Check if gh is installed
if ! command -v gh &> /dev/null; then
    echo "✗ GitHub CLI (gh) is not installed"
    echo ""
    echo "Install it with: brew install gh"
    echo "Then run: gh auth login"
    echo ""
    echo "Or use the manual script: ./release-v1.4.2.sh"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "✗ Not authenticated with GitHub"
    echo ""
    echo "Run: gh auth login"
    exit 1
fi

echo "✓ GitHub CLI is installed and authenticated"
echo ""

# Variables
TAG="V1.4.2"
TITLE="Aircraft v1.4.2"
APK_PATH="app/build/outputs/apk/release/app-release.apk"
NOTES_FILE="RELEASE_NOTES_V1.4.2.md"

# Verify files exist
if [ ! -f "$APK_PATH" ]; then
    echo "✗ APK not found: $APK_PATH"
    exit 1
fi

if [ ! -f "$NOTES_FILE" ]; then
    echo "✗ Release notes not found: $NOTES_FILE"
    exit 1
fi

APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
echo "✓ APK found: $APK_SIZE"
echo "✓ Release notes found"
echo ""

# Push code and tag
echo "Pushing code to GitHub..."
git push origin develop 2>&1 || echo "Branch already up to date"

echo "Pushing tag to GitHub..."
git push origin "$TAG" 2>&1 || echo "Tag already exists on remote"

echo ""
echo "Creating GitHub release..."

# Create release with GitHub CLI
gh release create "$TAG" \
    --title "$TITLE" \
    --notes-file "$NOTES_FILE" \
    "$APK_PATH#app-release.apk (Android APK)" \
    --target develop

echo ""
echo "=========================================="
echo "✓ Release created successfully!"
echo "=========================================="
echo ""
echo "View release at:"
echo "https://github.com/tobecrazy/Aircraft/releases/tag/$TAG"
echo ""
