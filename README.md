# Number by Color — Design Plan & Architecture

**Number by Color** is an offline-first, ad-free Android coloring-by-number and educational unlock app built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Room**. It runs cleanly on phones, tablets, and Amazon Fire tablets with zero accounts, ads, or required internet access, while supporting optional parent-managed GitHub catalog sync and portable JSON profile transfers across devices.

---

## 1. Overview & Core Loop

- **Kid-Friendly Coloring Gameplay**: Kids pick a picture from built-in, synced, or custom coloring pages and tap or drag to paint numbered grid cells (`1..N`). Completed pictures celebrate with an animation and unlock as stickers.
- **Educational Unlock Progression**:
  - The standard **Dot** brush is always free.
  - Larger brushes (**Cross**, **Blob**, and **Fill Bucket**) unlock per picture by answering short, level-appropriate learning questions.
  - The **Fill Bucket** allows filling up to 3 colors before needing a quick 4-question refill.
- **Sticker Book**: Every completed picture becomes a collectible sticker that kids can place, drag, resize, mirror, and layer across customizable paper backgrounds.
- **Parent-Controlled Customization**: Parents can create custom coloring pages (from photos, pixel art, GIFs, or blank canvases), edit individual blocks and animation frames, author custom education levels and questions, publish shared content to GitHub, and back up or transfer kid profiles via JSON.

---

## 2. Features & User Experience

### 2.1 Gallery, Play Screen & Sticker Book
- **Gallery (`HomeScreen`)**: Displays built-in, synced, and custom coloring pages with completion badges, in-progress previews, and custom page edit/delete/export controls.
- **Play Canvas (`PlayScreen`)**: Interactive pinch-to-zoom and pan canvas with number overlays, palette selector, active brush unlock progress, and replay/color-again actions.
- **Sticker Book (`StickerBookScreen`)**: Multi-page digital sticker album (`page_1..N`) with selectable paper tints and touch manipulation (move, scale, mirror, bring to front, clear page).

### 2.2 New Page Import & Block-Level Pixel / Frame Editor
Accessible from both **Create Page** (`Edit Blocks`) and **Custom Page cards** (`Edit` icon button):

- **Automated Conversion (`ImageToGridConverter` & `GifFrameDecoder`)**:
  - Converts photos, pixel art, or animated GIFs into numbered grids with tunable **Grid Detail**, **Max Colors**, and **Trim Background** controls, or starts from a **Blank Page**.
  - Detects pixel-art block size and sub-pixel grid phase via 1D edge projections and trims edge-connected backgrounds before cropping.
- **Grid & Shape Editing Tools (`BlockEditorScreen`, `BlockEditorEngine`, `BlockEditorCanvas`)**:
  - **Draw**, **Eraser**, **Eyedropper**, **Contiguous Flood Fill**, **Global Replace** (all matching blocks across the active frame or all frames), **Shape Tools** (filled/outline rectangles, circles, lines), **Pinch-to-Zoom & Pan** (with single-finger Pan toggle), **Undo/Redo** (bounded immutable snapshot stack), and **Number / Gridline Toggles**.
- **Selective Color Activation & Palette Management**:
  - Toggle visibility per swatch plus **Uncolor All**, **Show All**, and **Solo** controls so editors can inspect outlines against an uncolored backdrop and spot gaps.
  - Reorder palette numbers `1..N` via **Drag-and-Drop** or **Tap-to-Swap** (remapping cell characters across all frames simultaneously).
  - **Add Color**, **Edit Shade**, **Merge Color** (collapse one color into another and compact numbering cleanly), and **Delete Unused Color**.
- **Multi-Frame Animation Timeline**:
  - Frame strip to switch frames, add frames at the end, insert frames in between, delete frames, adjust frame delay (`frameMs`), and preview animation playback.
  - Editing a custom page with existing partial coloring progress resets stale progress cleanly so color counts stay accurate.

### 2.3 Hierarchical Settings & Parent Admin Hub
Designed with `BoxWithConstraints` responsive layouts (`Compact` phones and `Expanded` tablets with `720.dp` max width) and full `Next` / `Done` IME keyboard navigation across all forms:

- **Settings Root**:
  - **Profile Settings**: Kid name, emoji avatar, Educational Unlock Level selector (built-in or custom levels), Sound Effects toggle, **Share & Backup Profile**, and **Manage Kids**.
  - **Admin** *(PIN-protected)*:
    - **Server Setup**: GitHub repo/catalog URL, Wi-Fi-only auto-sync toggle, GitHub Fine-Grained PAT for publishing, target publish folder, and manual **Sync Now**.
    - **Education Levels**: Create, edit, and publish targeted custom education levels (`CustomEducationLevel`) pairing practice topics with targeted custom questions.
    - **Custom Questions**: Author custom multiple-choice/sentence-clue questions (`CustomQuestion`) tagged to built-in modes or custom education levels.
    - **Custom Pages**: Inspect pending vs. synced custom pages and publish individually or in bulk to GitHub.
- **Direct Profile Entry for New Kids**: Tapping **New kid** / **+ Add kid** opens directly into **Profile Settings** for the newly created kid—skipping General Settings and Admin menus—and returns straight to the gallery on Save or Back.

### 2.4 Multi-Profile Support & Cross-Device JSON Transfer
Built to work across standard Android devices and **Amazon Fire tablets / Kids profiles** without requiring Google Play Services, Firestore logins, or per-device GitHub keys:

- **Profile Disambiguation (`#ID` Badges)**: Every profile has a unique internal ID surfaced as a short 6-character badge (e.g., `#ABC987`) in Profile Settings, exported filenames, import dialogs, and Profile Switcher cards whenever multiple kids share the same name.
- **Profile Export & Share (`ProfileExportEngine`)**:
  - **Share Profile** (Android Share Sheet for email, Quick Share, Drive, etc.) and **Save JSON** (`<name>_<code>.profile.json`).
  - Bundles profile settings, all completed & in-progress coloring grids, Sticker Book pages & placed stickers, and any referenced local custom templates, custom levels, and custom questions.
- **Conflict-Aware Import & Overwrite Warnings (`ProfileImportConfirmationDialog`)**:
  - Accessible from both **Profile Settings** and the **Profile Switcher** dialog.
  - **Exact Profile ID Match**: Displays a clear warning that importing will permanently overwrite all coloring progress, sticker book pages, and settings for that profile, with options to **Overwrite Profile** or **Keep Both (Import as Separate Profile)**.
  - **Same Name, Different ID**: Lists local profiles sharing that name alongside their `#ID` badge and completion/sticker counts so parents can choose whether to **Keep Both** or **Overwrite** a specific profile.
  - **Clone Isolation**: Importing as a separate new profile generates fresh profile and sticker primary keys so existing profiles' stickers and progress remain untouched.

---

## 3. Technical Architecture & Data Strategy

```text
┌───────────────────┐     ┌────────────────────────┐     ┌────────────────────────┐
│   HomeScreen /    │     │   CreatePageScreen /   │     │   ProfileSettings &    │
│   PlayScreen      │     │   BlockEditorScreen    │     │   Admin Sub-Screens    │
└─────────┬─────────┘     └───────────┬────────────┘     └───────────┬────────────┘
          │                           │                              │
          └───────────────┬───────────┴──────────────────────────────┘
                          ▼
               ┌──────────────────────┐
               │  ColorPopViewModel   │  (StateFlows, Undo/Redo, Sync & Transfer)
               └──────────┬───────────┘
                          │
        ┌─────────────────┼──────────────────┬───────────────────────┐
        ▼                 ▼                  ▼                       ▼
┌──────────────┐  ┌───────────────┐  ┌───────────────────┐  ┌──────────────────┐
│ Room DB (v7) │  │ BlockEditor & │  │ Export Engines    │  │ RemoteCatalog    │
│ Profiles,    │  │ Grid/Challenge│  │ Page / Question / │  │ SyncEngine       │
│ Progress,    │  │ Engines       │  │ Level / Profile   │  │ (GitHub Sync &   │
│ Stickers,    │  │               │  │ (.json / PNG)     │  │  PAT Publish)    │
│ Custom/Synced│  │               │  │                   │  │                  │
└──────────────┘  └───────────────┘  └───────────────────┘  └──────────────────┘
```

### Persistence (`ColorPopDatabase`, Room v7)
- **Per-Profile Data**: `profiles`, `picture_progress`, `sticker_pages`, `sticker_placements`.
- **Content Tables**: Separate `custom_*` and `synced_*` tables for templates (`custom_templates`, `synced_templates`), questions (`custom_questions`, `synced_questions`), and education levels (`custom_levels`, `synced_levels`), merged non-destructively at runtime with automatic version-aware promotion when local items are published to GitHub.
- **Preferences**: Active profile selection, hashed Parent Admin PIN, sync URL/interval/Wi-Fi flags, and optional GitHub PAT stored locally in `SharedPreferences`.

---

## 4. APK Build & Sharing Workflow (GitHub Actions)

The repository includes `.github/workflows/build-apk.yml` so family devices and tablets can download a ready-to-install APK directly from GitHub:

- **Build Pipeline**: Runs on `ubuntu-latest` with JDK 21 and Gradle `9.3.1` (`gradle :app:assembleDebug`), copies `app/build/outputs/apk/debug/app-debug.apk` to `numberbycolor.apk`, uploads the `numberbycolor-apk` artifact, and commits `numberbycolor.apk` back to the branch (`[skip ci]`).
- **Consistent Signing Key Across Updates**:
  - Uses the `DEBUG_KEYSTORE_BASE64` GitHub Actions **Secret** when configured (or caches a `keytool`-generated keystore across builds and uploads a temporary `debug-keystore-base64` artifact when no secret is set yet).
  - Keeping the same signing key ensures future updates of `numberbycolor.apk` install cleanly over the existing app on tablets without uninstalling or losing local profiles/progress.

---

## 5. Summary of Design Revisions & Rollout Updates

- **Block Editor & Multi-Frame Refinement**: Added full-screen zoomable Block Editor with Draw, Erase, Eyedropper, Flood Fill, Global Replace, Shape tools, color visibility/Solo toggles, Drag/Tap-to-Swap palette reordering, Color Merge, and multi-frame timeline controls.
- **Custom Education Levels & Targeted Practice**: Added Room v7 support, `CustomEducationLevel` management, `LevelExportEngine` JSON round-trip/GitHub publish/sync, and targeted practice topic & custom-question filtering in `ChallengeGenerator`.
- **Hierarchical Settings & Form Polish**: Reorganized Settings into a tablet-adaptive drill-down hierarchy (`Root -> Profile | Admin -> Server / Levels / Questions / Pages`), added `Next` / `Done` IME keyboard progression across dialogs and forms, and updated **New kid / + Add kid** to open directly into **Profile Settings**.
- **Portable Profile JSON Export & Import**: Added `ProfileExportEngine`, `ProfileTransferDialogs`, `Share Profile` / `Save JSON` / `Import Profile` controls, `#ID` badges for duplicate-name disambiguation, sticker primary-key isolation when cloning profiles, and overwrite warning confirmation dialogs.
- **Automated APK Distribution**: Added `.github/workflows/build-apk.yml` to build, sign consistently, track `numberbycolor.apk` in the repository, and support sideloading across family Android and Fire tablets.
