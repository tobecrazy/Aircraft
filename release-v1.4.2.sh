#!/bin/bash

# Aircraft v1.4.2 Release Script
# This script will push the code and tag to GitHub

set -e

echo "=========================================="
echo "Aircraft v1.4.2 Release Script"
echo "=========================================="
echo ""

# Check current branch
CURRENT_BRANCH=$(git branch --show-current)
echo "✓ Current branch: $CURRENT_BRANCH"

# Show commit info
echo ""
echo "Latest commit:"
git log -1 --oneline
echo ""

# Check if tag exists
if git rev-parse V1.4.2 >/dev/null 2>&1; then
    echo "✓ Tag V1.4.2 exists locally"
else
    echo "✗ Tag V1.4.2 not found"
    exit 1
fi

# Check if APK exists
APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo "✓ Release APK found: $APK_SIZE"
else
    echo "✗ Release APK not found at $APK_PATH"
    exit 1
fi

echo ""
echo "=========================================="
echo "Ready to push to GitHub"
echo "=========================================="
echo ""
echo "This will:"
echo "  1. Push develop branch to origin"
echo "  2. Push tag V1.4.2 to origin"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

echo ""
echo "Pushing develop branch..."
git push origin develop

echo ""
echo "Pushing tag V1.4.2..."
git push origin V1.4.2

echo ""
echo "=========================================="
echo "✓ Successfully pushed to GitHub!"
echo "=========================================="
echo ""
echo "Next steps:"
echo ""
echo "1. Go to: https://github.com/tobecrazy/Aircraft/releases/new?tag=V1.4.2"
echo ""
echo "2. Fill in the release form:"
echo "   - Title: Aircraft v1.4.2"
echo "   - Description: Copy content from RELEASE_NOTES_V1.4.2.md"
echo "   - Upload: $APK_PATH"
echo ""
echo "3. Click 'Publish release'"
echo ""
echo "Release notes file: $(pwd)/RELEASE_NOTES_V1.4.2.md"
echo "APK location: $(pwd)/$APK_PATH"
echo ""
