# App Cloner (Shortcut Edition)

An Android app that lists every installed app and, with one tap,
pins a home-screen shortcut that launches it directly.

**Scope note:** this creates duplicate *shortcuts*, not a sandboxed
second instance of an app (true "dual-app" virtualization is far more
complex and is what dedicated cloning tools like Parallel Space do —
it's out of scope here).

## What's included

- `app/` — the Android app source (Kotlin, single Activity)
- `.github/workflows/android-build.yml` — builds a debug APK on every
  push to `main` and uploads it as a downloadable artifact

## How to get the APK

1. Create a new **public or private GitHub repo**.
2. Push everything in this folder to it:
   ```bash
   cd AppCloner
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
3. Go to the repo's **Actions** tab — the "Android Build" workflow
   runs automatically.
4. When it finishes (green check), open the workflow run and scroll
   to **Artifacts** — download `app-debug-apk`. GitHub packages
   artifacts as a `.zip`; unzip it to get `app-debug.apk`.
5. Transfer the APK to your phone and install it (you'll need to
   allow "install unknown apps" for whichever app you use to open it).

You can also trigger a build manually anytime from the Actions tab
via "Run workflow" (workflow_dispatch).

## Permissions note

Pinning shortcuts uses Android's `ShortcutManager.requestPinShortcut`
API (Android 8+), which always shows the user a system confirmation
dialog — the app cannot add a shortcut silently.

## Local build (optional)

If you have Android Studio installed, you can just open this folder
directly and hit Run — no need to wait for CI.
