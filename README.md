# coco-player

Coco is a general useful music player with extra customizable features such as lyrics syncing.


## How to build

### Requirements

- Android Studio Chipmunk 2021.2.1 Patch 2 or newer.
- JDK 11.
- Android SDK Platform 33.
- Android SDK Build-Tools installed through the SDK Manager. No explicit
  `buildToolsVersion` is pinned by the project.
- Gradle 7.3.3 if building from the command line without Android Studio.

The app currently targets Android 13/API 33 and supports devices running
Android 7.0/API 24 or newer.

### Android Studio

Open the project directory in Android Studio, let Gradle sync finish, then run
the `app` configuration.

### Command Line

From the repository root:

```powershell
gradle assembleDebug
```
