# WA StatusSaver

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="100" alt="App Icon"/>
</p>

<p align="center">
  <strong>Save WhatsApp & WhatsApp Business statuses directly to your gallery.</strong><br/>
  Built for Android 11–14 with Jetpack Compose and clean MVVM architecture.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?logo=android" />
  <img src="https://img.shields.io/badge/Min%20SDK-API%2030-brightgreen" />
  <img src="https://img.shields.io/badge/Language-Kotlin-blue?logo=kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-purple" />
  <img src="https://img.shields.io/badge/Architecture-MVVM-orange" />
</p>

---

## Features

-  **View image statuses** — browse all viewed WhatsApp image statuses in a clean grid
-  **View video statuses** — watch video statuses with full playback controls
-  **Save to gallery** — save any status to your phone's gallery with one tap
-  **Dual app support** — works with both WhatsApp and WhatsApp Business simultaneously
-  **Privacy first** — no internet permission, no data collection, everything stays on your device
-  **Instant reload** — pull-to-refresh loads the latest statuses immediately
-  **Dark theme** — WhatsApp-style dark UI out of the box

---

## Screenshots

> _Coming soon_

---

## How It Works

Android 11+ (API 30+) uses **Scoped Storage**, which means apps cannot freely browse the filesystem. WhatsApp also hides the `.Statuses` folder from normal access.

WA StatusSaver uses the **Storage Access Framework (SAF)** — the only compliant way to access this folder on modern Android. You grant access once by manually navigating to the `.Statuses` folder in the system file picker. The app then uses `takePersistableUriPermission` to remember that grant permanently, even after a reboot.

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM |
| Async | Kotlin Coroutines + StateFlow |
| Image loading | Coil |
| Video playback | ExoPlayer (Media3) |
| Storage access | SAF — DocumentFile + OpenDocumentTree |
| File saving | MediaStore API |
| Persistence | SharedPreferences |

---

## Project Structure

```
app/src/main/kotlin/com/malawianlad/wastatussaver/
│
├── MainActivity.kt                  # UI entry point — all Compose screens
│
└── ui/
    ├── StatusFile.kt                # Data model (uri, name, type)
    ├── StatusViewModel.kt           # Business logic, scanning, saving
    ├── StatusViewModelFactory.kt    # ViewModel factory (injects Application)
    │
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 11+
- A physical Android device running Android 11–14 (recommended for testing — WhatsApp does not run on emulators)

### Build & Run

1. Clone the repo
   ```bash
   git clone https://github.com/cleverhauli/StatusSaver.git
   ```

2. Open in Android Studio

3. Sync Gradle — **File → Sync Project with Gradle Files**

4. Connect your Android phone via USB and enable **USB Debugging**
   > Settings → About Phone → tap Build Number 7 times → Developer Options → USB Debugging ✅

5. Press **Run ▶** and select your device

### First Launch — Grant Folder Access

>  This step is required. The app cannot access statuses without it.

1. Open **WhatsApp → Status tab** and view at least one status from a contact
   _(This forces WhatsApp to create the `.Statuses` folder — it won't exist otherwise)_

2. Launch the app — tap **"Pick WhatsApp .Statuses folder"**

3. In the system file picker, navigate to:
   ```
   Internal Storage → Android → media → com.whatsapp → WhatsApp → Media → .Statuses
   ```
   > If `.Statuses` is not visible, tap the ⋮ menu → **Show hidden files**

4. Tap **"Use this folder"** → **Allow**

5. Statuses will now appear in the grid. You only need to do this once.

For **WhatsApp Business**, tap the second button and navigate to:
```
Internal Storage → Android → media → com.whatsapp.w4b → WhatsApp Business → Media → .Statuses
```

---

## Where Are Saved Files Stored?

| Type | Location |
|---|---|
| Images | `Pictures/WA StatusSaver/` |
| Videos | `Movies/WA StatusSaver/` |

Files appear in your Gallery app immediately after saving.

---

## Permissions

| Permission | Why it's needed |
|---|---|
| None declared | The app uses SAF — no `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE` needed |
| `queries` (package visibility) | Detects whether WhatsApp / WhatsApp Business is installed |

---

## Common Issues

| Problem | Fix |
|---|---|
| App keeps returning to Grant screen | You must navigate **into** the `.Statuses` folder itself before tapping "Use this folder" — not the parent `Media` folder |
| No statuses showing after granting | Open WhatsApp → Status tab and view at least one status first |
| `.Statuses` folder not visible in picker | Tap ⋮ menu in the file picker → Show hidden files |
| Saved files not appearing in Gallery | Wait a few seconds for the Media Scanner to index them, then refresh your Gallery app |

---

## Contributing

Pull requests are welcome. For major changes please open an issue first to discuss what you'd like to change.

1. Fork the repo
2. Create your branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

---

## License

```
MIT License

Copyright (c) 2026 cleverhauli

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<p align="center">Made with ❤️ in Malawi</p>
