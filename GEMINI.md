# WidgetCalendar Project

## Project Overview

**WidgetCalendar** is a lightweight Android project dedicated to providing minimalist, high-performance home screen widgets for calendar events. It operates entirely offline, fetching data directly from the device's local `CalendarContract` provider without external APIs.

The project features two distinct widgets:
1.  **Simple Calendar Widget:** A scrollable timeline of upcoming events grouped by day (e.g., "Today", "Tomorrow"). It features a sleek transparent design, rounded corners, and a subtle progress bar for ongoing events.
2.  **Next Event Countdown:** A focused widget that displays a real-time countdown (using system `Chronometer`) to the very next upcoming event.

The main application serves only as a configuration hub for granting permissions and filtering which calendars (e.g., Work, Personal) are visible in the widgets.

## Architecture & Technology

*   **Language:** Kotlin
*   **Build System:** Gradle (Kotlin DSL)
*   **UI Framework:** Android `RemoteViews` (XML layouts) for Home Screen Widgets.
*   **Architecture:**
    *   **`MainActivity`:** Handles runtime permissions and `SharedPreferences` for calendar filtering.
    *   **`CalendarWidget` (`AppWidgetProvider`):** Manages the timeline widget lifecycle.
    *   **`NextEventWidget` (`AppWidgetProvider`):** Manages the countdown widget.
    *   **`WidgetService` (`RemoteViewsService`):** Acts as the data adapter for the timeline list, querying the `ContentResolver`.

## Building and Running

This project uses standard Android build commands.

### Prerequisites
*   Android SDK (API 34 recommended)
*   Java JDK 1.8 or higher (Java 17 recommended for modern Gradle)

### Commands

**Build Debug APK:**
```bash
./gradlew assembleDebug
```
*Output:* `app/build/outputs/apk/debug/app-debug.apk`

**Build Release APK (Signed):**
```bash
./gradlew assembleRelease
```
*Output:* `app/build/outputs/apk/release/app-release.apk`
*Note:* The project includes a `release.keystore` configured in `build.gradle.kts`.

**Install via ADB:**
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Key Files

*   **`app/src/main/java/com/example/simplecalendarwidget/`**
    *   `CalendarWidget.kt`: Provider for the main list widget. Handles updates and template intents.
    *   `NextEventWidget.kt`: Provider for the countdown widget. Uses `Chronometer` for battery-efficient ticking.
    *   `WidgetService.kt`: The `RemoteViewsFactory` implementation. Contains the logic for querying `CalendarContract`, grouping events by day, and formatting the list items.
    *   `MainActivity.kt`: The user-facing app for permission requests and calendar selection checkboxes.
*   **`app/src/main/res/layout/`**
    *   `widget_layout.xml`: Layout for the timeline widget (Header + ListView).
    *   `widget_item.xml`: Layout for individual event items (Time + Title + Progress Bar).
    *   `widget_header.xml`: Layout for date headers in the list.
    *   `widget_next_event.xml`: Layout for the countdown widget.
*   **`ANDROID_WIDGET_MASTERY.md`**: A comprehensive guide on advanced widget development, including future-proofing for Android 16, memory limits, and Jetpack Glance migration strategies.

## Development Conventions

*   **RemoteViews Limitations:** Do not use custom views or unsupported layouts (like `ConstraintLayout` inside lists) as they may crash on some launchers. Stick to `LinearLayout`, `FrameLayout`, and `RelativeLayout`.
*   **Theme:** The design relies on transparency and white text (`@color/white` and transparent backgrounds) to fit any wallpaper.
*   **Updates:**
    *   Widgets update automatically every 30 minutes (standard Android limit).
    *   Instant updates are triggered when changing settings in `MainActivity` via `AppWidgetManager.notifyAppWidgetViewDataChanged`.
*   **Data Fetching:** All calendar queries are performed on background threads or within the `WidgetService` to avoid ANRs (Application Not Responding).
*   **Android 16+ Readiness:** Refer to `ANDROID_WIDGET_MASTERY.md` for handling strict memory limits (< 1MB payloads) and the deprecation of `RemoteViewsService` in favor of `RemoteCollectionItems`.
