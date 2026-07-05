# Troubleshooting Guide & Build Fixes

This file documents one-time setup fixes, dependency configurations, and compilation issues encountered during development.

## Dependency & Compiler Fixes

### 1. KSP2 & Room Compatibility Mismatch
* **Symptom**: Compiling Room DAOs with `suspend` functions returning `Unit` triggers `java.lang.IllegalStateException: unexpected jvm signature V` under KSP2/Kotlin.
* **Fix**: Upgrade the Room library modules (`room-runtime`, `room-compiler`, `room-ktx`) to `2.8.4` or higher in `libs.versions.toml`.

### 2. KSP Decoupled Versioning Scheme
* **Symptom**: Finding matching KSP Gradle plugin versions for Kotlin compiler versions 2.3.0+.
* **Behavior**: Starting with KSP `2.3.0`, the versioning is decoupled from the Kotlin version. You no longer search for a matching `[Kotlin]-[KSP]` format. Use the latest independent 2.3.x release (e.g. `2.3.9` for Kotlin `2.3.20`).

### 3. Missing Material Icons in Compose BOM
* **Symptom**: `Unresolved reference 'icons'` when referencing `Icons.Default.Delete` or other material icons.
* **Fix**: Manually add the `material-icons-core` or `material-icons-extended` dependency to the Version Catalog and app-level dependencies block, as recent Compose BOM releases do not bundle them by default.
