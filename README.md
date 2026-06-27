# MyTube Offline — Java Android port of the 2006-style YouTube clone

A self-contained, offline Android app that mirrors the Core-5 feature set of the
single-file `index.php` YouTube clone. Compiles on-device in **AIDE Pro** *or*
in the cloud via **GitHub Actions** — your pick.

---

## What this app does

| PHP page / action              | Android equivalent                                |
|--------------------------------|---------------------------------------------------|
| `index.php` (home feed)        | `MainActivity` — RecyclerView of every uploaded video |
| `?page=signup` + `action=signup` | `SignupActivity` + `DbHelper.insertUser`         |
| `?page=login`  + `action=login`  | `LoginActivity`  + `DbHelper.findUserByUsername` + `PasswordUtil.verify` |
| `?action=logout`               | `SessionManager.logout()`                          |
| `?page=upload` + `action=upload_video` | `UploadActivity` (gallery picker) + `ThumbUtil` + `DbHelper.insertVideo` |
| `?page=watch` (play, increment views) | `WatchActivity` (VideoView + custom seek) + `DbHelper.incrementViews` |
| `?page=account` (my videos, delete)   | `AccountActivity` + `DbHelper.deleteVideo`       |
| `generate_video_id()` (11-char id)    | `IdGenerator.nextVideoId()`                      |
| `password_hash()` / `password_verify()` | `PasswordUtil.hash()` / `verify()` (PBKDF2)    |
| `uploads/` folder              | `getExternalFilesDir()/uploads/` (app-private, no permission) |

---

## Scope of v1 (Core-5)

The following PHP features are **intentionally deferred** to a later version
(they were not selected in the scope question):

- Comments (`post_comment` AJAX, `comments` table)
- Likes / dislikes (`like_video` AJAX, `likes` table)
- Channel pages (`?channel=username`)
- Contact form (`contact_messages` table)
- About / Copyright static pages

The DB schema already reserves the `users` table shape so adding comments/likes
later is a non-breaking migration.

---

## Project layout

```
MyTubeOffline/
├── .github/
│   └── workflows/
│       ├── build.yml             (debug APK on push/PR/manual)
│       └── release.yml           (signed release APK on tag push)
├── .gitignore                    (excludes build/, .gradle/, local.properties, keystores)
├── build.gradle               (project-level)
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/
│   └── gradle-wrapper.properties   (Gradle 5.4.1)
└── app/
    ├── build.gradle           (AGP 3.5.3, min 24, target 28, compile 28, signed-release config)
    ├── mytube.keystore        (committed RSA 2048 keystore — see ⚠️ note above)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/mytube/offline/
        │   ├── data/
        │   │   ├── DbHelper.java         (SQLite: users + videos tables)
        │   │   ├── SessionManager.java   (SharedPreferences login state)
        │   │   └── IdGenerator.java      (11-char video id)
        │   ├── util/
        │   │   ├── FileUtil.java         (content URI -> app storage)
        │   │   ├── ThumbUtil.java        (MediaMetadataRetriever -> jpg)
        │   │   └── PasswordUtil.java     (PBKDF2 hash + verify)
        │   ├── adapter/
        │   │   └── VideoAdapter.java     (RecyclerView adapter)
        │   └── ui/
        │       ├── MainActivity.java     (home feed)
        │       ├── LoginActivity.java
        │       ├── SignupActivity.java
        │       ├── UploadActivity.java
        │       ├── WatchActivity.java    (VideoView + custom controls)
        │       └── AccountActivity.java  (my videos + delete)
        └── res/
            ├── layout/   (7 activity layouts + item_video.xml)
            ├── values/   (strings.xml, colors.xml, themes.xml)
            ├── drawable/ (box_bg, btn_classic, input_classic, header_bg, ic_launcher)
            ├── menu/     (main.xml)
            └── xml/      (file_paths.xml for FileProvider)
```

---

## How to build (recommended path: GitHub Actions)

This is the **easiest path** — push the code to a GitHub repo and the APK
gets built for you in CI, downloadable from the Actions tab. No AIDE Pro,
no PC setup, no SDK install.

### One-time setup

1. Create a new empty GitHub repo (private or public — doesn't matter).
2. Unzip `MyTubeOffline.zip` somewhere. **Copy the contents of the
   `MyTubeOffline/` folder** (not the folder itself) into the repo root.
   Your repo should look like:

   ```
   .github/
   app/
   gradle/
   .gitignore
   build.gradle
   gradle.properties
   settings.gradle
   README.md
   ```

3. Commit & push:

   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```

4. Open your repo on GitHub → click the **Actions** tab.
5. You'll see a workflow named **Build Debug APK** start running.
   It takes ~3-5 minutes on first run (downloading dependencies),
   ~1-2 minutes on subsequent runs (everything cached).
6. When it finishes, click the run → scroll down to **Artifacts** →
   download `mytube-offline-debug-apk`. Unzip it → you have
   `app-debug.apk` ready to install.

### Manual trigger (no push needed)

Want to rebuild without committing anything?

- Actions tab → **Build Debug APK** → **Run workflow** button (top-right).

### Releases (signed APK + GitHub Release page)

This project ships with a **keystore committed in the repo** at
`app/mytube.keystore`, with credentials hardcoded in `app/build.gradle`.
That's intentional — private repo, offline app, no distribution concerns.
You don't need to set up any GitHub Secrets.

When you're ready to publish a version:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The **Release APK** workflow will:
1. Check out the code (including the keystore)
2. Build `assembleRelease` — Gradle picks up the signing config from `build.gradle` automatically
3. Verify the APK is actually signed (sanity check)
4. Rename it to `MyTubeOffline-v1.0.0.apk`
5. Create a GitHub Release at `github.com/<you>/<repo>/releases` with the APK attached

Anyone with access to the repo can download & install the APK. No further
setup required.

#### ⚠️ If you ever fork this into a PUBLIC repo

**Do NOT push the existing keystore.** Generate a new one locally:

```bash
keytool -genkeypair -v \
  -keystore app/mytube.keystore \
  -alias mytube \
  -storetype PKCS12 \
  -keyalg RSA -keysize 2048 -validity 9125 \
  -storepass <your-new-password> \
  -keypass   <your-new-password> \
  -dname "CN=MyTube Offline, OU=App, O=Self, L=Local, ST=Local, C=IN"
```

Then update the credentials in `app/build.gradle` and switch to GitHub
Secrets (see git history of `.github/workflows/release.yml` for the
secret-based version).

### Why GitHub Actions beats AIDE Pro on-device builds

| | AIDE Pro | GitHub Actions |
|---|---|---|
| Phone battery / heat | Drains both | Zero |
| Build speed | 5-15 min for first build | ~3 min first, ~1 min cached |
| Reproducible | Device-dependent | Always identical (ubuntu-latest) |
| APK output | On phone only | Downloadable from any browser |
| Versioning | Manual | Tied to git tags |
| Multiple APKs (debug + release) | Painful | One workflow each |
| Error visibility | Tiny phone screen | Full web console with log highlights |

You can still use AIDE Pro to **edit** code on your phone — push commits
and let CI do the building. Best of both worlds.

---

## How to import into AIDE Pro

1. **Unzip** `MyTubeOffline.zip` anywhere on your device's internal storage.
   A good spot is `/storage/emulated/0/AIDEProjects/MyTubeOffline/`.

2. Open **AIDE Pro** → tap the **folder icon** in the top bar → navigate to
   the unzipped `MyTubeOffline/` folder → tap **Open**.

3. AIDE Pro should auto-detect the Gradle project. If it asks "Open as Gradle
   project?" — tap **Yes**.

4. Wait for the initial Gradle sync. The first sync downloads:
   - Gradle 5.4.1 (the `gradle-wrapper.properties` pins this version)
   - AGP 3.5.3 + AndroidX artifacts from Google's Maven mirror

   If you're offline, AIDE Pro's bundled Gradle distribution will be used
   instead and the AndroidX artifacts must already be in the local Gradle
   cache (`~/.gradle/caches/`). If they're not, connect once to seed the
   cache, then build offline afterwards.

5. Tap **Build → Run** (or the play icon) once sync finishes.

---

## Common AIDE Pro gotchas

### 1. "Gradle version not supported"
If AIDE Pro complains about Gradle 5.4.1, your bundled Gradle is older.
Edit `gradle/wrapper/gradle-wrapper.properties` and change `distributionUrl`
to whichever Gradle version your AIDE Pro ships (often 5.2.1 or 6.5). Don't
go above 6.5 — AGP 3.5.3 doesn't support newer Gradle.

### 2. "Could not resolve androidx.appcompat:appcompat:1.1.0"
AIDE Pro's offline cache doesn't have it. Two fixes:
- Connect to the internet once, hit Build, let Gradle download, then build offline forever.
- Or bump to `1.2.0` / `1.3.1` if your AIDE Pro's cache already has those.

### 3. R class not generated / "cannot resolve symbol R"
Usually a layout XML error. Open each `res/layout/*.xml` in AIDE Pro and
check the bottom tab for error markers. The most common cause is using
`@color/` or `@drawable/` references that don't exist — every color and
drawable used in this project is defined in `res/values/colors.xml` and
`res/drawable/`.

### 4. VideoView shows black screen, no playback
Tested only with `.mp4` files. Android's VideoView uses MediaPlayer under
the hood, and that doesn't decode `.webm` or `.ogg` on every device. The
picker accepts all three extensions (parity with PHP) but `.mp4` is the
only format guaranteed to play.

### 5. "Cannot play this video" toast on Watch
The picked file was probably truncated during copy. Re-pick it; if the
issue persists, try a smaller video or one stored in primary storage
(not a USB OTG drive or cloud-mounted provider).

### 6. Thumbnails appear as a generic play icon
`MediaMetadataRetriever.getFrameAtTime()` failed. Common cause: the
video uses a codec the platform can't decode frame-by-frame. The video
will still play fine — only the thumbnail is affected.

---

## Storage layout on the device

After running the app once and uploading a video, you'll find files at:

```
/data/data/com.mytube.offline/databases/mytube.db   ← SQLite DB
/storage/emulated/0/Android/data/com.mytube.offline/files/uploads/
    ├── <uuid>.mp4            ← uploaded video
    └── thumb_<8chars>.jpg    ← auto-extracted thumbnail
```

These are wiped when the app is uninstalled — same semantics as the PHP
`uploads/` directory sitting inside the project folder.

---

## Security notes

- Passwords are hashed with **PBKDF2WithHmacSHA1, 12000 iterations, 16-byte
  salt, 256-bit key**. This is *not* bcrypt (PHP's default), but it is a
  standard NIST-recommended password hash and is built into Android's
  javax.crypto — no third-party dependencies.
- The hash is stored in the SQLite DB in the form
  `pbkdf2:<iterations>:<saltHex>:<hashHex>` so the iteration count is
  upgradeable without DB migration.
- Verification uses a constant-time comparison to prevent timing attacks.

---

## License

This port mirrors the original `index.php` by DK [DRUVX13 - GITHUB].
Use and modify freely under the same terms as the original.
