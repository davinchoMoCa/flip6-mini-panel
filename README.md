# Flip6 Mini Panel

A native Android app that turns a rooted Galaxy Z Flip6 cover screen into a small music player and app launcher. Built to make a phone with a broken inner touchscreen useful again.

**Experimental, device-specific software.** Tested on one SM-F741B running Android 15 / One UI 7, firmware F741BXXS2BYGB, with Magisk 30.7. Minimum SDK 31 is a build setting, not a claim of compatibility with other devices or Android versions.

## Features

- A Samsung cover-screen widget with clock, playback controls, volume, Spotify shortcut and app launcher.
- A dedicated applications screen with search and favorites.
- Swipe right to return to the cover carousel; with the keyboard open, dismiss it first.
- Pull down at the top of the apps list to focus search and show the keyboard.
- Long-press an app to toggle favorites; favorites appear first when the list is rebuilt.
- An accessibility overlay with an edge handle, music controls, brightness, navigation and task switching.
- Material 3 dark styling in the app; a matching RemoteViews layout for the widget.

The widget and full app are separate surfaces. The full app does not duplicate the widget's music player. This is not a ROM, bootloader unlocker, root installer or a replacement for Samsung System UI.

## Install

1. Use a compatible, already-rooted phone. Download the APK from [Releases](https://github.com/davinchoMoCa/flip6-mini-panel/releases).
2. Install it and open Mini Panel. ADB alternative: `adb install -r Mini-Panel-v0.5.0.apk`.
3. Grant Mini Panel superuser access in your root manager when you use a root-powered action. Initial setup may require the inner screen or an existing authorized ADB connection.
4. For the edge handle, enable **Mini Panel** under Android Settings → Accessibility → Installed apps. Android may require allowing restricted settings for a sideloaded app. Only enable the service if you trust the APK/source.
5. In Samsung's cover-screen widget editor, select **Mini Panel** and add its preview to the carousel. On the tested phone this was accessible through Settings → Cover screen → Widgets.
6. Close the phone. Navigate to the new widget. **Aplicaciones** and **Abrir escritorio** both open the applications screen.

To return with a gesture, start within the app area rather than on the floating edge handle or inside the editable search field. Vertical scrolling remains available. The back destination is the last active home/carousel page; the app does not force a particular widget index.

The edge handle opens on tap or inward swipe. Tap outside the panel to close it. Long-press the handle to move it to the other edge. To disable the overlay, turn off its accessibility service.

## Limitations

- Cover display ID `1`, inner display ID `0`, closed state `0`, Samsung brightness settings and private task-manager APIs are device-specific assumptions.
- Root-powered navigation/control actions require the phone to be closed. Some apps or subsequent dialogs can still insist on the inner screen.
- Gestures are local to Mini Panel, not global gestures across other apps.
- The widget uses Android RemoteViews. It is not an embedded activity and does not show live album art or song metadata.
- Playback commands target Android's selected media session; they are not Spotify-exclusive.
- Spotify's own cover widget, permission-dialog routing and firmware/root setup are outside this repository.
- A self-built APK normally has a different signing certificate from the downloadable APK. Android will reject an in-place update across certificates. Uninstalling first removes app settings; use a consistent private signing key for your own builds.
- No full TalkBack, large-font, reboot-persistence or multi-device test matrix has been completed.

## Build

Use Android Studio or a JDK supported by Gradle 9.5 and AGP 9.3.1. The local build was verified with JDK 25. Install Android SDK Platform 36 and set `ANDROID_HOME`, or add your SDK path to an untracked `local.properties`.

```sh
./gradlew assembleDebug
```

Windows: `gradlew.bat assembleDebug`. The APK is under `build/outputs/apk/debug/`. Debug builds use your local Android debug key by default.

Optional distribution signing uses the environment variables `MINIPANEL_KEYSTORE_PATH`, `MINIPANEL_KEYSTORE_PASSWORD`, `MINIPANEL_KEY_ALIAS` and `MINIPANEL_KEY_PASSWORD`. Never commit a signing key or credentials. Without them, `assembleRelease` produces an unsigned release APK.

## Architecture and permissions

### Latest source changes (after v0.5.0)

- **Close apps:** open the edge menu → **Cerrar apps**. Close individual standard app tasks on the cover or inner display, then refresh the list. This removes the task like dismissing it from Recents; it does not force-stop the package or guarantee that background playback stops. Mini Panel and System UI are excluded. Tasks that cannot be handled individually are rejected.
- **Spotify screen-on:** while the accessibility overlay is enabled, Mini Panel checks Spotify's active media session through the root bridge every 10 seconds and keeps the cover overlay's screen on during playback. Pausing or a failed check releases this flag (an expanded menu retains its existing screen-on behavior). It does not deliberately wake a screen switched off by the user. No global screen-timeout setting is changed. This relies on the Android media-session dump format and needs verification on other firmware.

These changes are in the source branch; the existing v0.5.0 release APK predates them. Build the current source to include them.

| Component | Responsibility |
| --- | --- |
| `MainActivity` | App grid, search, favorites, local gestures |
| `PanelService` | Accessibility overlay attached to the cover display |
| `CoverWidget` | Samsung-compatible widget and fixed PendingIntent actions |
| `MaterialUi` | Shared color, type and component helpers |
| `RootClient` / `RootBridge` | Allowlisted actions via `su` and `app_process` |

A scoped `<queries>` declaration enumerates apps with launcher activities. No broad package-query or network-state permission is requested. The accessibility service does not request window-content retrieval or gesture injection. Root itself is powerful: review the source before granting it. There is no INTERNET permission, analytics SDK, account login or cloud backend in this project.

The widget receiver is not exported; its click PendingIntents are explicit and immutable. The root client accepts a fixed action grammar, not arbitrary commands. The app does not automatically accept Android permission dialogs. The widget retains Samsung's privacy-widget behavior.

## Contributing

Issues and pull requests are welcome. Include device model, Android/One UI versions, root manager version, reproduction steps and redacted logs. Please do not post serial numbers, accounts, tokens or full private phone dumps. Device compatibility reports and improvements to accessibility, gesture handling and automated tests are particularly useful.

See [QA.md](QA.md) for what has actually been tested. MIT license for this project's code. Android, Samsung, Spotify and other names belong to their respective owners; this project is independent and not endorsed by them.

## Español

Mini Panel convierte la pantalla externa de un Galaxy Z Flip6 con root en un pequeño reproductor y lanzador de aplicaciones. Incluye widget de música, catálogo con búsqueda, favoritas y un menú lateral.

Instala la APK de Releases, concede root a Mini Panel, habilita su servicio de accesibilidad si quieres la orilla flotante y agrega **Mini Panel** desde el editor de widgets de Samsung. La configuración inicial puede requerir acceso a la interna o ADB autorizado.

- Desliza a la derecha dentro del catálogo para volver al carrusel.
- Desliza hacia abajo al principio de la lista para buscar.
- Mantén pulsada una app para cambiar su estado de favorita.
- En el menú lateral, **Cerrar apps** permite cerrar ventanas individualmente, como desde Recientes.
- Mientras Spotify reproduce, el panel mantiene encendida la externa. Al pausar libera ese bloqueo en unos 10 segundos; después se aplica el tiempo de apagado habitual. Requiere la orilla de accesibilidad habilitada y root. Estas dos funciones están en el código posterior a la APK v0.5.0.

Probado únicamente en SM-F741B con Android 15 / One UI 7. No instala root ni repara la pantalla interna. Algunas aplicaciones y permisos todavía pueden requerir abrir el teléfono.

## CI template

A build/lint workflow is provided in docs/github-actions-build.example.yml. To enable it, move it into .github/workflows/build.yml using credentials with workflow permission. It is not enabled in this initial publication because the publishing credential lacks that scope.
