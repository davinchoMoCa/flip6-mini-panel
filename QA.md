# Validation

## Device checks performed during development

On a rooted SM-F741B, Android 15 / One UI 7, with the phone closed:

- APK install/update and application launch on display 1.
- Samsung widget discovery, adding a page to the carousel and opening the applications screen from it.
- Widget playback: paused → playing → paused; volume up/down with original volume restored.
- App search, no-results state, Spotify launch, return to carousel.
- Overlay inward swipe, tap-outside dismissal, scrolling, music, controls, navigation, open-task and inner-task lists.
- Swipe-right return, pull-down search/keyboard, swipe-right keyboard dismissal.
- Long-press favorite add/remove, preserving the prior favorite set after testing.
- Ordinary vertical scrolling of the application grid.

## Public packaging

The public project enables the edge handle by default when the accessibility service is first enabled. This fixes fresh-install initialization: existing preferences are preserved. Build and Android lint are run before publication; An optional CI template is included; it is not enabled in the initial publication. Manual development checks above do not constitute automated tests or a guarantee on other firmware. Public packaging is not installed over the user's working device as part of publication.

## Suggested regression checks

### September 14, 2026 additions

The private development build containing these changes was installed on the same SM-F741B:

- Spotify playing: WindowManager reported the cover overlay as the screen-holding window. After pause and a 12-second wait, the hold was cleared; after resuming and waiting again, it returned. Global USB stay-awake was disabled for this check. This verifies flag transitions, not an overnight endurance test.
- Close apps: opened Calculator on display 1, entered **Cerrar apps**, and pressed its **Cerrar** button. Calculator disappeared from the activity task dump and the list refreshed. The next app remained available in the panel.
- The phone was partially open with a temporary CLOSED device-state override during these checks. That override is not installed or managed by this app.

Untested cases include multiple windows of the same app, closing a task that disappears concurrently, fresh installation, and playback behavior on different firmware. Closing a task is not a force-stop.

Repeat the above on any compatibility change. Also check short/diagonal swipes, two-finger input, vertical scrolling back toward the top, text selection inside search, persistence after reboot and fresh-install root/accessibility prompts. A private API or Samsung firmware change may require code updates.

Public build result: assembleDebug and lintDebug passed (0 errors, 42 warnings). Remaining warnings include private APIs, hardcoded untranslated labels, layout/RTL and dependency advisories. These are not a claim of full accessibility or portability.
