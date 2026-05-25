# Android Developer Assistant Tool - Comprehensive Development Prompt

## Project Overview

Develop a comprehensive Android developer assistance tool integrated as a feature module within the app's Developer Settings. This tool provides a suite of utilities for Android developers including decompilation, UI inspection, color picking, activity monitoring, and developer options management.

## Core Architecture Requirements

### 1. Integration Framework
- **Entry Point**: Integrate all features through Developer Settings (no separate app development)
- **Navigation Structure**: Implement a tabbed or grid-based navigation system for feature organization
- **Module Architecture**: Design as a modular system where each feature can be independently enabled/disabled
- **Resource Efficiency**: Ensure minimal memory footprint and battery consumption
- **Accessibility**: Full integration with Android's accessibility services where required

---

## Feature Module Specifications

### 2. APK Decompilation & Code Viewing

**Functionality Requirements:**
- Decompile third-party applications to extract and display:
  - Java source code (with syntax highlighting)
  - Resource files (layouts, drawables, values)
  - Manifest information
  - DEX files and bytecode
  - Asset files
- **Code Display Features:**
  - Syntax highlighting with multiple color themes
  - Line numbers and code folding
  - Search and filter capabilities
  - Code structure tree view
  - Jump to definition functionality
- **Sharing Integration:**
  - Share decompiled files to WeChat, Telegram, Email
  - Export selected code snippets
  - Generate shareable links or QR codes
- **Performance Optimization:**
  - Lazy loading for large files
  - Caching mechanism for frequently accessed APKs
  - Background decompilation process
  - Progress indication for long operations

**Technical Stack Recommendations:**
- Use established decompilation tools: CFR, Procyon, or Jadx for Java conversion
- Implement ApkTool for resource extraction
- Consider embedding or integrating existing open-source decompilation libraries
- Handle obfuscated code gracefully

---

### 3. Layout & UI Inspector

**Functionality Requirements:**
- Real-time view hierarchy inspection:
  - Display layout tree structure
  - Show view component composition
  - Identify parent-child relationships
  - Show multiple views at the same screen position with selection capability
- **View Property Display:**
  - View ID (resource ID)
  - Dimensions (width, height)
  - Position coordinates (screen-relative and parent-relative)
  - Padding and margin values
  - Background colors and drawables
  - Text content and styling information
  - Visibility states
  - Custom attributes
- **Layout Export:**
  - Export view hierarchy as XML layout files
  - Export as JSON format for programmatic use
  - Generate layout documentation
  - Export as visual diagrams/screenshots
- **Interactive Features:**
  - Tap-to-select view on screen
  - Highlight selected view
  - Real-time property update
  - Multi-touch support for multiple view selection
  - Zoom and pan capabilities
- **Visualization:**
  - Display view boundaries with color coding
  - Show padding and margin as visual guides
  - Overlay coordinates and dimensions on screen

**Technical Implementation:**
- Utilize AccessibilityService for view tree traversal
- Implement custom overlay system for visual indicators
- Use WindowManager for floating inspection UI
- Cache view hierarchy with refresh intervals

---

### 4. Color Picker Tool

**Functionality Requirements:**
- Screen-based color sampling:
  - Sample any color from any visible app interface
  - Real-time color preview during sampling
  - Display precise screen coordinates
  - Support for multiple sampling points
- **Color Information Display:**
  - RGB values with 0-255 range
  - Hex color code (#RRGGBB, #AARRGGBB)
  - ARGB format (including alpha channel)
  - CMYK values for print design
  - HSL/HSV color space representation
  - Color name suggestions (if available)
- **Functionality Features:**
  - Copy color value to clipboard (multiple formats)
  - Share color information
  - Color history tracking
  - Color palette generation
  - Similar colors suggestion algorithm
  - Accessibility mode for color-blind users
- **UI/UX:**
  - Magnified view of color area being sampled
  - Adjustable sampling area size
  - Real-time color preview display
  - Quick action buttons for common formats

**Technical Considerations:**
- Use PixelCopy API (API 24+) for color sampling
- Implement fallback methods for lower API levels
- Handle overlay permissions and window manager tokens
- Implement color space conversion algorithms

---

### 5. Open Source Project Discovery

**Functionality Requirements:**
- Daily curated Android open source projects:
  - Automated project collection and curation
  - Daily update mechanism
  - Source from popular repositories (GitHub, GitLab, etc.)
  - Project categorization by use case/library type
- **Project Information Display:**
  - Project name and description
  - GitHub stars and fork counts
  - Last updated timestamp
  - Key features and tech stack
  - Direct links to repository
  - Installation instructions
- **User Interaction:**
  - Open repository directly
  - Share project links
  - Save favorite projects locally
  - Filter/search projects
  - View trending projects
- **Data Management:**
  - Local caching of project data
  - Sync with remote source
  - Offline access capability

**Backend Integration:**
- Design API endpoint for daily project data
- Implement smart caching strategy
- Use RSS feeds or GitHub API for data collection
- Scheduled background sync

---

### 6. Activity Monitor & History

**Functionality Requirements:**
- Current Activity Information Display:
  - Stack-top Activity class name
  - Package name
  - Activity opening timestamp
  - Activity history with chronological order
  - Duration of stay in each Activity
- **Features:**
  - Real-time Activity tracking
  - Floating window mode display
  - Expandable/collapsible window
  - Search and filter by package/activity name
  - Activity stack visualization
- **Data Presentation:**
  - List view with activity details
  - Stack visualization diagram
  - Timeline view of activity navigation
  - Statistics (most visited activities, average duration)
- **Export Functionality:**
  - Export activity history as CSV/JSON
  - Generate activity flow diagrams
  - Share activity logs

**Technical Requirements:**
- Use AccessibilityService for Activity tracking
- Implement WindowManager for floating UI
- Handle Activity lifecycle properly
- Thread-safe data storage

---

### 7. Manifest Information Viewer

**Functionality Requirements:**
- View AndroidManifest.xml from any installed application:
  - Parse and display manifest structure
  - Show permissions list with descriptions
  - Display activities, services, broadcast receivers, content providers
  - Show intent filters and exported components
  - Display meta-data information
  - Show application attributes
- **Search & Filter:**
  - Search for specific permissions/components
  - Filter by component type
  - Filter by export status
- **Export Options:**
  - Save as plain text (.txt)
  - Save as formatted HTML with styling
  - Save as XML (original format)
  - Save to SD card or selected location
- **Presentation:**
  - Tree view hierarchy
  - Formatted text display
  - Color-coded component types
  - Expandable sections for detailed info

**Implementation:**
- Extract APK and parse manifest using XML parser
- Cache parsed manifests
- Implement efficient search algorithm
- Generate HTML with proper formatting

---

### 8. Installed Applications Browser

**Functionality Requirements:**
- Three application categories:
  - All installed applications
  - Recently used applications
  - Recently installed applications
- **Display Mode:**
  - Grid view layout (customizable grid columns)
  - Application icons with labels
  - Sort options (alphabetical, size, install date, last used)
- **Application Information Provided:**
  - Package name
  - App name and icon
  - Version number (version code and name)
  - Launch Activity (main launcher component)
  - UID
  - Installation location (APK path)
  - Native library directory (SO files location)
  - Application data directory path
  - First installation timestamp
  - Last updated timestamp
  - Hardening/encryption info (if available)
  - Component information (activities, services, etc.)
  - Signature information
  - Target SDK version
- **Interactive Features:**
  - Tap to view detailed information
  - Long press to open context menu
  - Share application information
  - Quick launch application
  - Open app settings
  - View app details in system settings

---

### 9. APK & SO File Extraction

**Functionality Requirements:**
- Extract APK files:
  - Direct APK extraction from installed applications
  - Save to configurable location
  - Batch extraction support
- **Extract SO (Native Library) Files:**
  - Extract individual .so files
  - Extract all libraries from architecture type
  - Support for multiple architectures (armeabi-v7a, arm64-v8a, x86, x86_64)
- **File Management:**
  - Show extraction progress
  - Verify extracted files
  - Display file size and hash
- **Sharing:**
  - Share via messaging apps
  - Upload to cloud storage
  - Generate download link
  - Export to external storage
- **Organization:**
  - Create organized directory structure
  - Timestamp-based naming
  - Custom naming options

---

### 10. Developer Options Quick Toggle

**Functionality Requirements:**
- One-click control for Developer Options:
  - Show Layout Bounds
  - Show GPU Overdraw
  - Show Layout Updates
  - Force GPU Rendering
  - Show GPU View Updates
  - Show GPU Rendering Mode
  - Show Pointer Location
  - Strict Mode
  - Don't Keep Activities
  - Lock Screen Don't Turn Off
  - Running Services
  - System UI Tuner
- **Operation Method:**
  - Automation-based (no manual Developer Settings navigation)
  - Single click to toggle each option
  - Batch operations (enable/disable multiple at once)
  - Quick status indicator showing current state
- **UI/UX:**
  - Toggle switches for each option
  - Grouped by category
  - Visual feedback for state changes
  - Toast notifications for confirmations
- **Accessibility Integration:**
  - Use AccessibilityService for automated toggling
  - Implement settings simulation through AccessibilityService
  - Handle system version compatibility

**Important Note:**
- This tool is designed to accelerate existing developer option workflows
- It complements, not replaces, system Developer Settings
- Suitable only for users seeking automation of repetitive tasks

---

### 11. System Information Display

**Functionality Requirements:**
- **Version Information:**
  - Android OS version
  - API level
  - Security patch level
  - Bootloader version
  - Build number
  - Build fingerprint
- **Hardware Information:**
  - Device manufacturer
  - Device model
  - Device name
  - CPU model and architecture
  - RAM size
  - Storage size (internal/external)
  - Battery info
  - Screen resolution and DPI
- **Screen Information:**
  - Physical screen dimensions
  - Screen density (DPI)
  - Aspect ratio
  - Refresh rate
  - Safe area information
- **CPU Information:**
  - Processor name
  - Number of cores
  - Core frequency
  - Architecture (ARM64, x86, etc.)
  - CPU usage statistics
- **Virtual Machine Information:**
  - Runtime type (ART vs Dalvik for older versions)
  - Heap size
  - GC configuration
  - Instruction set
- **Network Information:**
  - Device IP address (WiFi and mobile)
  - MAC address
  - Network type (WiFi, mobile, etc.)
  - Connectivity status
  - DNS information
  - Signal strength
- **Device ID Information:**
  - IMEI (if available)
  - Android ID
  - MAC addresses
  - Serial number
  - Device UUID
- **Presentation:**
  - Structured information display
  - Copy-to-clipboard for each field
  - Export as JSON/CSV
  - Share capabilities

---

### 12. Quick Settings Launcher

**Functionality Requirements:**
- Direct navigation to common system settings:
  - System Settings (main)
  - Language Settings
  - System UI Tuner
  - Developer Options
  - My Applications
  - Display Settings
  - Sound and Vibration
  - Battery and Device Care
- **Implementation:**
  - Intent-based navigation to system apps
  - Fallback for unavailable settings
  - Error handling for unsupported devices
  - Customizable shortcut list

---

### 13. Android Job Opportunities (Internal Referral)

**Functionality Requirements:**
- Display current job listings:
  - Positions from major domestic internet companies (tier-1 and tier-2)
  - Job description and requirements
  - Salary range (if available)
  - Application link
  - Company information
- **Features:**
  - Category filtering by company/position
  - Search functionality
  - Share job listings
  - Open application page
  - Save favorite listings
- **Data Management:**
  - Update mechanism for job postings
  - Local caching
  - Last update timestamp

**Backend Integration:**
- Job data API endpoint
- Regular update schedule
- Curation of quality listings

---

## Shortcut & Widget System

### 14. Desktop Shortcut Creation

**Functionality Requirements:**
- Long-press icon on any tool to create desktop shortcut:
  - Generate launcher shortcut
  - Custom icon and label
  - Direct launch to specific tool
  - Support for multiple shortcuts of same tool
- **Implementation:**
  - Use ShortcutManager API (API 25+)
  - Fallback to legacy shortcut creation
  - Permission handling
  - Icon generation

### 15. Home Screen Widget Support

**Functionality Requirements:**
- Add widgets to home screen:
  - Quick access widgets for main tools
  - Resizable widgets (1x1, 2x2, etc.)
  - Widget configuration options
  - Real-time data updates
- **Widget Types:**
  - Activity Monitor widget
  - System Info widget
  - Color Picker quick access
  - Developer Options quick toggles
  - Open Source Projects widget

### 16. Android 8.0+ App Shortcuts

**Functionality Requirements:**
- Long-press app icon to access shortcuts:
  - Recently used tools
  - Frequently accessed features
  - Pinned shortcuts
  - Dynamic shortcut generation
- **Implementation:**
  - ShortcutManager API
  - Track usage statistics
  - Update shortcuts dynamically

### 17. Quick Settings Tile (Android 8.0+)

**Functionality Requirements:**
- Add tools to system notification shade:
  - Quick tile for main features
  - One-click access without opening app
  - Tile status updates
  - Custom icons and labels
- **Implementation:**
  - TileService API
  - Broadcast receiver for status updates
  - Permission handling

---

## Permission Requirements

### 18. Permission Strategy

**Accessibility Service (Primary):**
- **Purpose:**
  - Quick Activity class name retrieval
  - Developer options automated toggling
  - View hierarchy inspection
  - Accessibility compliance
- **Implementation:**
  - Manual user authorization required
  - Privacy assurance documentation
  - Minimal data collection
  - User consent flow
- **Data Handling:**
  - No information collection
  - Local processing only
  - User control and transparency

**Other Required Permissions:**
```
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.GET_TASKS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.CHANGE_CONFIGURATION" />
```

---

## Technical Architecture

### 19. Overall System Design

**Module Organization:**
```
├── Core
│   ├── AccessibilityService Integration
│   ├── WindowManager Service
│   └── Permission Manager
├── Features
│   ├── Decompiler Module
│   ├── LayoutInspector Module
│   ├── ColorPicker Module
│   ├── ProjectBrowser Module
│   ├── ActivityMonitor Module
│   ├── ManifestViewer Module
│   ├── AppBrowser Module
│   ├── FileExtractor Module
│   ├── DeveloperToggle Module
│   ├── SystemInfo Module
│   ├── QuickSettings Module
│   └── JobListings Module
├── UI Layer
│   ├── DeveloperSettings Entry
│   ├── Navigation System
│   ├── Fragment-based UI
│   └── Overlay Components
├── Data Layer
│   ├── Local Database
│   ├── Cache Management
│   ├── SharedPreferences
│   └── File System Access
└── Utilities
    ├── Color
