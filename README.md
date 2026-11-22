# SocialLiteFree 🛡️

A 100% free, open-source alternative to parental control and kid-safe social media browser apps (like SocialLite).

Built using **Kotlin**, **Jetpack Compose (Material 3)**, and **Android WebKit WebView** with custom JavaScript injection engines for content filtering.

---

## ✨ Features

### 1. ▶️ YouTube Controls
- **Ad Stripping**: Filters video ads, sponsored cards, and auto-skips unskippable ads
- **Hide Shorts**: Completely removes the Shorts feed and bottom navigation tab
- **Background Audio**: Allows videos/music to continue playing when the screen is off or app is in background
- **Search-Only Mode**: Hides recommended feeds to prevent doom-scrolling
- **Disable Feed Autoplay**: Saves mobile data and reduces attention capture
- **Hide Comments & Recommendations**: Clean and distraction-free viewing

### 2. 📸 Instagram Controls
- **Ad & Sponsored Filtering**: Intercepts GraphQL and Timeline connections to strip sponsored posts
- **Hide Suggested Content**: Removes netego units, explore prompts, and recommended accounts
- **Reels Time Budget**: Configurable daily limit (e.g., 30 mins) with auto-redirect when exceeded
- **Block Post Creation**: Disables posting stories and feeds
- **Hide Comments**: Removes comment sections from posts and reels

### 3. 🎵 TikTok Controls
- **Block Explore & Discover**: Restricts viral algorithmic rabbit holes
- **Block Live Streams**: Restricts live broadcasting pages
- **Block TikTok Shop**: Hides commercial product recommendations
- **Block Upload / Camera**: Disables content creation
- **Hide Comments**: Clean video experience

### 4. 🐦 X (Twitter) Controls
- **Force 'Following' Tab**: Bypasses the algorithmic "For You" timeline
- **Hide Promoted Tweets**: Strips sponsored ads and promoted threads
- **Hide App Install Prompts**: Removes bottom sheet popups

### 5. 📱 Universal Parental Controls
- **Grayscale Mode**: Reduces dopamine response by removing bright colors
- **Kid-Friendly Interface**: Simple platform selector and comprehensive controls menu
- **No Subscriptions / No Paywalls**: Free forever with local DataStore preference storage

---

## 🛠️ Project Structure

```
SocialLiteFree/
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/js/
│   │   │   ├── common_controls.js      # Universal grayscale & pop-up blocker
│   │   │   ├── youtube_controls.js     # Adblock, Shorts remover, and player controls
│   │   │   ├── instagram_controls.js   # Feed filter & Reels budget timer
│   │   │   ├── tiktok_controls.js      # Explore, Live & Shop blocker
│   │   │   └── twitter_controls.js     # Promoted tweet & For-You blocker
│   │   └── java/com/socialitefree/app/
│   │       ├── MainActivity.kt         # Compose navigation entrypoint
│   │       ├── data/
│   │       │   ├── PlatformConfig.kt   # Platform definitions & config JSON
│   │       │   └── PreferencesManager.kt # DataStore settings management
│   │       ├── service/
│   │       │   └── BackgroundPlayService.kt # Foreground media audio playback
│   │       ├── ui/
│   │       │   ├── screens/
│   │       │   │   ├── HomeScreen.kt   # Grid of social media platforms
│   │       │   │   ├── SettingsScreen.kt # Toggles for all parental controls
│   │       │   │   └── BrowserScreen.kt # WebView container with JS bridge
│   │       │   └── theme/              # Material 3 colors & theme
│   │       └── webview/
│   │           ├── SocialWebViewClient.kt # Injects JS & blocks app schemes
│   │           └── SocialWebChromeClient.kt # Fullscreen & progress handling
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🚀 How to Build & Install

### Option A: Using Android Studio (Recommended)
1. Open **Android Studio**.
2. Select **Open** and choose the `SocialLiteFree` folder:  
   `C:\Users\BIT PATNA\Desktop\rea\SocialLiteFree`
3. Wait for Gradle sync to complete.
4. Connect an Android phone (with USB Debugging enabled) or start an Emulator.
5. Click the green **Run (▶)** button to install and launch on your device!

### Option B: Build APK via Command Line
Run in PowerShell / Terminal:
```bash
cd "c:\Users\BIT PATNA\Desktop\rea\SocialLiteFree"
./gradlew assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`
