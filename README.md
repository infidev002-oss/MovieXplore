# DevKit — developer guide

Step-by-step guide for building the movie app with this kit. Read it once from top to bottom before you start.

- **This file** explains the setup and how the pieces fit together.
- **`AGENTS.md`** holds the exact rules for your AI coding assistant (see "How to use these files" below). When this
  guide and `AGENTS.md` differ, `AGENTS.md` wins.
- **The spec document** you received describes the screens and features.

```
DevKit/
├─ AGENTS.md / CLAUDE.md      rules for you and your AI assistant
├─ README.md                  this guide
├─ kit/                       shared code: copy into your app unchanged (only the package name changes)
│  ├─ java/                   ads, auth, config, hooks, network, playstore, splash, watchlist
│  ├─ debug/ · release/       AppCheckInit for each build type
│  ├─ res/                    kit layouts, drawables, strings, styles
│  └─ proguard-rules.pro      merge into app/proguard-rules.pro
└─ firebase/                  Realtime Database rules + sample, Remote Config template
```

---

## How to use these files

DevKit is **not** your app repo. Create your own app repo and copy the files into it:

| From DevKit | Into your app repo |
|---|---|
| `AGENTS.md`, `CLAUDE.md`, `README.md` | repo root |
| `firebase/` | repo root (keep it up to date) |
| `kit/` | the app's source folders, package name replaced (section 1) |

**`CLAUDE.md` and `AGENTS.md` are for your AI assistant, not for you to follow by hand.** It reads them on its own:

| Tool | How it gets the rules |
|---|---|
| Claude Code | reads `CLAUDE.md` automatically; that file loads `AGENTS.md` (`@AGENTS.md`) |
| Codex, Cursor, GitHub Copilot, other tools that support `AGENTS.md` | read `AGENTS.md` from the repo root automatically |
| Any other tool | tell it once per session: "Read AGENTS.md in the repo root and follow it for every change", or put that line in the tool's own rules file |

- **Don't edit `AGENTS.md`.** If a rule blocks something you need, your AI is told to stop and ask. Ask us instead of
  changing the rule.
- **Firebase changes:** when you add or change a Remote Config key, a database path or a rule, update the matching file
  in `firebase/` in the same change and list it in your delivery note (your AI does this when it follows `AGENTS.md`).
- **Kit updates:** when DevKit is updated, pull it and copy `kit/` (package name replaced), `AGENTS.md` and `README.md`
  into your repo again. Never hand-edit kit files.

---

## 1. Create the project

1. New Android project, **Kotlin**, minimum SDK as in the spec. Choose the **final package name** now. Firebase, sign-in and
   AdMob are tied to it.
2. Copy `kit/` into the app, replacing `your.app` with your package name in every file:
   - `kit/java/<folder>/` → `app/src/main/java/<your/package>/<folder>/`
   - `kit/debug/AppCheckInit.kt` → `app/src/debug/java/<your/package>/`
   - `kit/release/AppCheckInit.kt` → `app/src/release/java/<your/package>/`
   - `kit/res/...` → `app/src/main/res/...`
   - merge `kit/proguard-rules.pro` into `app/proguard-rules.pro`
3. Set up Gradle as in `AGENTS.md` §5: the dependencies, `buildConfig` + `viewBinding`, release minify, and the
   `admobAppId` placeholder. Use the tested versions listed in `AGENTS.md` §1.
4. `gradle.properties`: `ADMOB_APP_ID=ca-app-pub-3940256099942544~3347511713` (Google's test app id while developing).
5. `local.properties` (never committed):
   ```
   TMDB_BASE_URL=https://api.themoviedb.org/3/
   TMDB_API_KEY=<your TMDB key>
   UMP_TEST_DEVICE_ID=          # optional, debug: id Logcat prints for your phone
   ADS_TEST_DEVICE_ID=          # optional, debug
   ```
6. `.gitignore` must contain `app/google-services.json` and `local.properties`.

---

## 2. Firebase setup (your own project)

Do these **in this order**. Download `google-services.json` only after sign-in, the SHA-1 and the database are set up: a file
downloaded earlier is missing the web client id and the database URL, and sign-in or the build fails.

1. **Create a Firebase project** → add an **Android app** with your final package name.
2. **SHA-1:** run `./gradlew signingReport` and add the debug SHA-1 (later also your release / Play signing SHA-1) under
   Project settings → your app.
3. **Authentication** → Sign-in method → enable **Google**.
4. **Realtime Database** → Create database → Rules tab → paste `firebase/database.rules.json` → Publish.
   `firebase/rtdb_sample.json` shows the data your app writes (`Users/{uid}/Profile` and `Users/{uid}/watchlist`).
5. **Now** download `google-services.json` into `app/`. Download it again whenever you change sign-in, a SHA-1 or the
   database.
6. **Remote Config** → ⋮ menu → **Publish from a file** → **select** `firebase/remote_config_template.json` with the file
   browser → **Publish**. Don't drag and drop (the console rejects dropped files as "Invalid JSON file"), and uploading
   alone only creates a draft.
7. **App Check** → your app → **Play Integrity** → register. Run a debug build once. Logcat prints
   `Firebase App Check debug token: …`. Add that token under App Check → your app → ⋮ → **Manage debug tokens**.
   Without it your debug build can be rejected once enforcement is on. Never commit the token.
8. **Analytics, Crashlytics, Cloud Messaging:** nothing to configure for development. They start from
   `google-services.json`.

When you add or change a Remote Config key, a database path or a rule, update the matching file in `firebase/` in the
same change and mention it in your delivery note.

---

## 3. Application class

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCheckInit.install(this)          // first
        NetworkClient.init(this)
        WatchlistRepository.init(this)
        // Welcome-back screen + every screen it must never open over:
        WelcomeBack.install(this, WelcomeBackActivity::class.java,
            SplashActivity::class.java, OnboardingActivity::class.java,
            SignInActivity::class.java /* , any other screens if required … */)
    }
}
```

Register it in the manifest (`android:name=".App"`), together with the AdMob `meta-data`, `WelcomeBackActivity`
(with `configChanges`=>android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"
) and the splash (see below). The exact manifest lines are in `AGENTS.md` §5 and §12.

---

## 4. App flow: splash → onboarding → home

```
Launcher icon
   ↓
SplashActivity            your design; the kit's SplashFlow runs everything
   ↓  onDone(destination)
destination != null ? ────────────────→ start destination (always wins)
   ↓ no
first run? → OnboardingActivity → Home
   ↓ no
Home (Movies / TV / Settings …)
```

### Splash: design only

```kotlin
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private val flow = SplashFlow(this)      // must be a property

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)          // your design
        flow.start(
            onStatus = { status ->
                statusText.text = if (status == SplashFlow.Status.OFFLINE) "Turn on internet" else "Please wait…"
            },
            bannerContainer = splashBannerSlot,   // optional: empty FrameLayout, visibility="gone"
            onDone = { destination ->
                val next = destination
                    ?: if (!Prefs.onboardingDone(this)) Intent(this, OnboardingActivity::class.java)
                       else Intent(this, HomeActivity::class.java)
                startActivity(next)
                finish()
            }
        )
    }
}
```

Manifest: give the splash
`android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"` and the MAIN / LAUNCHER
intent filter.

`SplashFlow` already does all of this, in a fixed order, so **don't write any of it yourself**:
- waits for internet
- fetches Remote Config
- runs the startup hook
- gathers ads consent and initialises Mobile Ads
- asks for the notification permission (Android 13+)
- shows the force-update dialog when needed
- records the app open
- preloads interstitials and shows the splash interstitial

Logcat tag `SplashFlow` shows each step.

### Onboarding

- Start it **only from the splash's `onDone`**, as above, and never when `destination` is non-null.
- Save "onboarding done" in your own SharedPreferences when the user finishes. The next launch goes straight to Home.
- The onboarding native uses `RemoteValues.NATIVE_OB` (see section 5).
- Keep it in `WelcomeBack.install(...)` (section 3), so the welcome-back screen never covers it.
- No interstitial between onboarding pages. Use `AdNavigator.open(...)` only for the final move to Home, if the spec
  asks for it.

---

## 5. Ads: what to call where

All ads go through the kit's `ads/` classes, and all ad settings come from Remote Config. Never load or show an ad any
other way, and never add App Open ads.

| What | Where | Call |
|---|---|---|
| Consent + Mobile Ads init | splash | automatic (`SplashFlow`) |
| Splash interstitial | splash | automatic (`SplashFlow`) |
| Welcome-back screen + ad | returning to the app | automatic after `WelcomeBack.install` (section 3) |
| Interstitial on a screen change | any navigation that should carry an ad | `AdNavigator.open(activity, intent)` instead of `startActivity(intent)` |
| Interstitial before another action | e.g. before closing a screen | `AdNavigator.showThen(activity) { … }` |
| Splash banner | splash | pass `bannerContainer` to `SplashFlow.start` (section 4); the kit loads it once |
| Banner | home / category list / details | `BannerAd.load(activity, lifecycleOwner, slot, RemoteValues.BANNER_HOME / BANNER_CAT / BANNER_INFO, root = slotRoot)` — details adds `inline = true` (Fragment: `viewLifecycleOwner`) |
| Native | the four placements below | `NativeAds.attach(...)` (below) |

**The four native placements** (exactly these; each has its own Remote Config unit):

| Screen | Constant | Suggested size |
|---|---|---|
| Onboarding | `RemoteValues.NATIVE_OB` | LARGE |
| Home | `RemoteValues.NATIVE_HOME` | MEDIUM |
| Settings | `RemoteValues.NATIVE_SETTINGS` | SMALL |
| Category list (a genre's list screen) | `RemoteValues.NATIVE_CATEGORY` | MEDIUM |

```kotlin
// In the screen (Fragment shown). The slot is an empty FrameLayout with visibility="gone".
private val homeNative by lazy { NativeAds.viewModel(this, RemoteValues.NATIVE_HOME) }

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    NativeAds.attach(viewLifecycleOwner, homeNative, binding.nativeSlot, RemoteValues.NATIVE_HOME, NativeSize.MEDIUM)
}
override fun onResume() {
    super.onResume()
    if (!isHidden) homeNative.refreshIfNeeded(RemoteValues.NATIVE_HOME)
}
override fun onHiddenChanged(hidden: Boolean) {          // tabs switched with hide/show
    super.onHiddenChanged(hidden)
    if (!hidden && isResumed) homeNative.refreshIfNeeded(RemoteValues.NATIVE_HOME)
}
```

- `attach()` shows a loading placeholder, then the ad, and hides the slot when there is no ad.
- Call `refreshIfNeeded` **only while the screen is visible** (one refresh per visit), never from a timer.

**Native inside a list (e.g. the category list):** put it as the first item with the kit's `NativeAdRowAdapter`:

```kotlin
private val categoryNative by lazy { NativeAds.viewModel(this, RemoteValues.NATIVE_CATEGORY) }
private lateinit var nativeRow: NativeAdRowAdapter

// onCreate
nativeRow = NativeAdRowAdapter(this, categoryNative, RemoteValues.NATIVE_CATEGORY, NativeSize.MEDIUM)
val grid = GridLayoutManager(this, 3).apply { spanSizeLookup = nativeRow.fullSpanLookup(3) }   // ad row = full width
recyclerView.layoutManager = grid
recyclerView.adapter = ConcatAdapter(nativeRow, postersAdapter)

override fun onResume() { super.onResume(); nativeRow.onScreenVisible() }
```

It shows a placeholder, then the ad, removes the row when there is no ad, and refreshes once per visit — only when the
row is on screen (scrolled away: when the user scrolls back to it). Don't put the native above the RecyclerView in a
`NestedScrollView`: that turns off recycling and breaks paging. A native in the *middle* of a list (like Home):
`xNative.refreshOnVisit(KEY, NativeAds.isRowVisible(recyclerView, rowPosition))` on each visit and
`xNative.onRowVisible(KEY)` from the row's `onViewAttachedToWindow` (see `AGENTS.md` §13).
- The size you pass is the default. Remote Config (`layout`) can change it later, and also sets the button height.
- **Native layouts: change colors only** (text, backgrounds, border, button, "Ad" badge) in the `ads_native_*` layouts and
  drawables and in `AdsNativeBadge` / `AdsNativeCta` (`values/ads_styles.xml`). Change nothing else.

**The four banner placements** (each has its own Remote Config unit):

| Screen | Constant | Type / size | Spacing (automatic) |
|---|---|---|---|
| Splash | `RemoteValues.BANNER_SPLASH` (loaded by `SplashFlow`) | anchored, large | 0 |
| Home | `RemoteValues.BANNER_HOME` | anchored, standard, **collapsible** — at the **bottom**, above the navigation | 5 dp top + bottom |
| Category list | `RemoteValues.BANNER_CAT` | anchored, standard | 5 dp top + bottom |
| Movie / TV details | `RemoteValues.BANNER_INFO` | **inline** (pass `inline = true`), max 90 dp | 10 dp top + bottom |

- The slot is an empty `FrameLayout` (`visibility="gone"`). While loading it shows a pulsing "Advertisement" placeholder
  of the banner's height; if no ad comes it hides.
- Per unit in Remote Config: `"inline"` (true / false) + `"maxHeight"`, `"size"` (`standard` / `large`, anchored only),
  `"collapsible"` (true / false) and `"collapsiblePosition"` (`top` / `bottom`, must match where that banner is on screen).
- A banner inside scrolling content (details) must be **inline**; fixed top / bottom banners are anchored. Never put an
  anchored banner inside scrolling content.
- **Spacing is automatic** (table above), outside Google's banner size. Never add margins around banners or natives in
  your layouts (natives have 10 dp on every side built in).
- **Edge-to-edge (targetSdk 35+):** keep a top banner below the status bar and a bottom banner above the navigation bar
  by applying window insets. If a system bar covers the banner, it also takes its taps (a collapsible banner can't collapse).

**Welcome-back screen: redesign `ads_welcome_back.xml`** to match your app. Keep a clickable `ads_welcome_start` (Start).
`ads_welcome_icon`, `ads_welcome_subtitle`, `ads_welcome_progress` and `ads_welcome_hero` are optional. Use one scrollable
layout that works in portrait and landscape (no `layout-land`; the screen handles rotation itself). Details: `AGENTS.md` §4.

**Leaving the app:** right before **every** intent that opens another app (browser, share sheet, mail, Play Store,
YouTube, system settings), call `WelcomeBack.skipNextResume()`. Put it inside your one `openUrl` / share helper, so
returning from there doesn't show the welcome-back screen.

---

## 6. Sign-in, watchlist, settings

- **Sign-in:** build the screen around the kit's `AuthManager`. The Credential Manager code is in `AGENTS.md` §9. Never
  call FirebaseAuth directly.
- **Watchlist:** use only `WatchlistRepository` (`observeAll`, `observeContains`, `toggle`). Sync with Firebase is
  automatic.
- **Settings:**
  - Privacy policy / terms: `RemoteValues.privacyPolicyUrl` / `termsUrl`
  - "Rate us": `AppReview.openPlayStore(context)`
  - Sign out / delete account: `AuthManager`
  - No "Privacy options" button

---

## 7. Google Play: update and review

In the home Activity:

```kotlin
private val inAppUpdate = InAppUpdate(this)      // must be a property

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    …
    if (savedInstanceState == null) {
        AppReview.recordLaunch(this)
        inAppUpdate.check { AppReview.maybeRequest(this) }
    }
}
```

Both only appear for apps installed from Google Play (internal testing track). Nothing shows in local builds.

---

## 8. TMDB

- The key and base URL come from `local.properties` → `BuildConfig` → `TmdbConfig`. Use the one `NetworkClient` /
  `tmdbApi`, and add endpoints to `TmdbApi`.
- Never put the key in code or resources, and never create another `OkHttpClient` or `Retrofit`.
- Load home sections in parallel, each with its own placeholder and retry.
- Genres come from a fixed list. Tapping one opens the **category list** screen, which carries the `NATIVE_CATEGORY` native.

---

## 9. Test before you hand over

- Debug build on a real phone with **Google's test ad ids** (the Remote Config template has them). Never click real ads.
- Turn the phone's internet off, then start the app: the splash says "turn on internet" and continues when it's back.
- Rotate the phone on the splash, onboarding, home and the welcome-back screen. Nothing may crash or restart.
- Press Home, then come back: the welcome-back screen appears (after the interstitial gap). Open a link and come back:
  no welcome-back screen.
- Sign in, add to watchlist, reinstall, sign in again: the watchlist comes back.
- Release build (`isMinifyEnabled = true`) installs and runs.
- Work through the checklist at the end of `AGENTS.md`.

## 10. Delivery

- Push your repo **without** `google-services.json` and `local.properties`.
- In the delivery note, list every change to the `firebase/` files (keys, paths, rules) and anything you couldn't do.
