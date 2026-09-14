# Third-party components

This project uses Material Components for Android and AndroidX (Apache License 2.0) through Gradle dependencies. Their licenses remain applicable to redistributed binaries. The Gradle Wrapper files are from Gradle (Apache License 2.0). The MIT license applies to this project's own source, not third-party dependencies.

- Material Components: https://github.com/material-components/material-components-android/blob/master/LICENSE
- AndroidX: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt
- Gradle: https://github.com/gradle/gradle/blob/master/LICENSE

The widget deliberately uses framework android:tint because RemoteViews cannot inflate AppCompat widgets. Its narrowly scoped UseAppTint lint suppression documents that requirement.
