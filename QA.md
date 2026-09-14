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

Repeat the above on any compatibility change. Also check short/diagonal swipes, two-finger input, vertical scrolling back toward the top, text selection inside search, persistence after reboot and fresh-install root/accessibility prompts. A private API or Samsung firmware change may require code updates.

Public build result: assembleDebug and lintDebug passed (0 errors, 42 warnings). Remaining warnings include private APIs, hardcoded untranslated labels, layout/RTL and dependency advisories. These are not a claim of full accessibility or portability.
