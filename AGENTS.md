# AGENTS.md — Project rules for AI coding assistants

Read this whole file before making any change. These rules apply to every task in this repository.
If a request conflicts with a rule here, stop and ask the developer instead of guessing.

The files given to you:
- `AGENTS.md`, `CLAUDE.md` → repository root
- `README.md` → repository root (step-by-step guide for the developer: setup, Firebase, app flow, ads)
- `firebase/` → repository root (Firebase setup files; keep up to date)
- `kit/` → shared code, copied into the app (section 4). Tested together; keep it unchanged.

In `kit/` files the package is `your.app`. When copying, replace `your.app` with the app's real
package everywhere (package lines and imports). Change nothing else in kit files.

---

## 1. Project summary

- Android app, **Kotlin**, Android Gradle Plugin 9 (built-in Kotlin: do not add the `org.jetbrains.kotlin.android` plugin).
- Movie and TV discovery app using the **TMDB API directly**.
- Networking: **Retrofit + OkHttp**. Architecture: **MVM**.
- Local storage: **Room with KSP** (not kapt).
- Firebase: Authentication (Google only), Realtime Database, Remote Config, App Check, Analytics, Crashlytics, Cloud Messaging.
- Ads: Google Mobile Ads + UMP consent (banner, native, interstitial, splash interstitial, welcome-back interstitial).
- Google Play: in-app update (IMMEDIATE), In-App Review, Remote Config force update.
- No DI framework is required. Do not add Hilt/Koin for these rules. If the project already uses one, follow it.

Tested versions (use these or newer): AGP 9.4.1, Gradle 9.6.0, KSP 2.3.6, Room 2.8.4, Retrofit 3.0.0,
OkHttp 5.5.0, Firebase BoM 34.19.0, play-services-ads 25.5.0, UMP 4.0.0, credentials 1.6.0, googleid 1.2.0,
Play app-update 2.1.0, Play review 2.0.2, lifecycle-process 2.11.0.
`googleid`: stay on **1.2.0**. 1.2.1 is built with Kotlin 2.4 and fails to compile with AGP 9.4's built-in Kotlin (2.2)
("compiled with an incompatible version of Kotlin"). Move to it only together with a Kotlin 2.3+ compiler.

## 2. Features in scope

- Splash, onboarding
- Home pages for Movies and TV (or one Home), sections as per design
- Movie details, TV details, seasons and episodes, cast, search, see-all lists
- Google sign-in screen, profile, sign-out, delete account
- Watchlist (local storage + Firebase sync)
- Banner, native and interstitial ads
- Settings, privacy policy, terms, rate us
- Force update, in-app update, in-app review, welcome-back screen (all from the kit)

## 3. Never do

- Never build watch history / "continue watching" (no history table, no `history` node).
- Never add App Open ads (the kit's welcome-back screen is the only ad on returning to the app).
- Never add anonymous sign-in or other sign-in providers.
- Never edit kit files except the package name. Never remove, rename, simplify, inline or bypass anything in `hooks/`, even if a default looks like it does nothing.
- Never call `FirebaseAuth` sign-in / sign-out / delete outside `AuthManager`.
- Never read Remote Config directly from feature code; use `RemoteValues` and `AdRules`.
- Never load or show ads outside the kit's `ads/` classes.
- Never put the TMDB key in a URL string, Retrofit `@Query`, resource file or constant. Only `TmdbConfig`.
- Never create a second `OkHttpClient()` or `Retrofit.Builder()`.
- Never `setValue()` on `Users/{uid}` or `Users/{uid}/Profile`. Use `updateChildren()`.
- Never read or write Realtime Database paths other than `Users/{uid}/Profile` and `Users/{uid}/watchlist`.
- Never hardcode anything from the Firebase project (web client id, database URL, AdMob app id).
- Never commit `google-services.json` or `local.properties`.
- Never add a "Privacy options" button for ads consent in Settings.

---

## 4. Kit files — copy unchanged

```
kit/java/hooks/        Startup, NetworkHooks, AdRules, AuthHooks          → app/src/main/java/your/app/hooks/
kit/java/auth/         AuthManager                                        → …/your/app/auth/
kit/java/config/       RemoteValues                                       → …/your/app/config/
kit/java/network/      TmdbConfig, NetworkClient, 4 interceptors          → …/your/app/network/
kit/java/network/      TmdbApi + model/Models   (STARTER: add endpoints/fields freely)
kit/java/watchlist/    WatchlistItem, WatchlistDao (+AppDatabase), WatchlistRemote, WatchlistRepository
kit/java/ads/          AdsConsent, BannerAd, NativeAdViewModel, NativeAdBinder,
                       AdNavigator, SplashInterstitial, AdLoadingOverlay, AdState,
                       WelcomeBack, WelcomeBackActivity, NativeAds (+NativeSize)
kit/java/playstore/    PlayStore, ForceUpdate (+ForceUpdateDialog), InAppUpdate, AppReview → …/your/app/playstore/
kit/java/splash/       SplashFlow (the whole splash sequence)                              → …/your/app/splash/
kit/debug/AppCheckInit.kt    → app/src/debug/java/your/app/
kit/release/AppCheckInit.kt  → app/src/release/java/your/app/
kit/res/layout/   ads_loading_overlay.xml, ads_welcome_back.xml, playstore_force_update.xml,
                  ads_native_small/medium/large.xml, ads_native_placeholder_small/medium/large.xml → app/src/main/res/layout/
kit/res/drawable/ ads_welcome_ring.xml, ads_welcome_hero.xml, ads_native_bg.xml, ads_native_badge_bg.xml,
                  ads_native_placeholder_block.xml                                        → app/src/main/res/drawable/
kit/res/values/   ads_strings.xml, ads_styles.xml, ads_ids.xml, playstore_strings.xml      → app/src/main/res/values/
kit/proguard-rules.pro       → merge into app/proguard-rules.pro
```

- If the app already has a Room database, add `WatchlistItem` as an entity and `WatchlistDao` to it instead of using the kit's `AppDatabase` (keep the DAO queries).
- `WatchlistItem` is both the Room entity and the Firebase model. Keep its field names.
- You may restyle `ads_loading_overlay.xml`, `ads_welcome_back.xml`, `playstore_force_update.xml`, the `ads_native_*` layouts,
  the drawables, `ads_styles.xml` and the
  texts; keep the file names, view ids and string names. They use theme attributes (`?attr/colorPrimary`, …) so they follow the app theme.

---

## 5. Gradle

`app/build.gradle.kts` (relevant parts):
```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.gms.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    defaultConfig {
        manifestPlaceholders["admobAppId"] = project.property("ADMOB_APP_ID") as String
        buildConfigField("String", "TMDB_BASE_URL", "\"${localProps.getProperty("TMDB_BASE_URL", "")}\"")
        buildConfigField("String", "TMDB_API_KEY", "\"${localProps.getProperty("TMDB_API_KEY", "")}\"")
        buildConfigField("String", "UMP_TEST_DEVICE_ID", "\"${localProps.getProperty("UMP_TEST_DEVICE_ID", "")}\"")
        buildConfigField("String", "ADS_TEST_DEVICE_ID", "\"${localProps.getProperty("ADS_TEST_DEVICE_ID", "")}\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { buildConfig = true; viewBinding = true }
}

dependencies {
    // … AndroidX, Retrofit, OkHttp, Gson, Glide, coroutines (+ kotlinx-coroutines-play-services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth); implementation(libs.firebase.database); implementation(libs.firebase.config)
    implementation(libs.firebase.analytics); implementation(libs.firebase.crashlytics); implementation(libs.firebase.messaging)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.credentials); implementation(libs.credentials.play); implementation(libs.googleid)
    implementation(libs.play.services.ads); implementation(libs.ump)
    implementation(libs.lifecycle.process)                                    // WelcomeBack: app foreground / background
    implementation(libs.play.app.update); implementation(libs.play.review)   // com.google.android.play:app-update / review
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
}
```

`gradle.properties`: `ADMOB_APP_ID=ca-app-pub-3940256099942544~3347511713` (Google test id during development).

`local.properties` (never committed):
```
TMDB_BASE_URL=https://api.themoviedb.org/3/
TMDB_API_KEY=<your TMDB key>
# Optional, debug builds only. Copy the ids Logcat prints for your phone:
# UMP:  addTestDeviceHashedId("…")      Ads:  setTestDeviceIds(Arrays.asList("…"))
UMP_TEST_DEVICE_ID=
ADS_TEST_DEVICE_ID=
```

Manifest: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`; the Application class; and
```xml
<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="${admobAppId}" />
<activity
    android:name=".ads.WelcomeBackActivity"
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"
    android:exported="false" />
```

---

## 6. Application class

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCheckInit.install(this)          // kit: debug / release source sets
        NetworkClient.init(this)            // stores context only; the client itself is lazy
        WatchlistRepository.init(this)      // resumes watchlist sync if already signed in
        // Welcome-back screen, then every screen it must never open over (splash, onboarding,
        // sign-in, video / trailer player, …).
        WelcomeBack.install(this, WelcomeBackActivity::class.java, SplashActivity::class.java, SignInActivity::class.java)
        // Nothing here may make a TMDB request or touch NetworkClient.okHttp / retrofit.
    }
}
```

---

## 7. Firebase setup

The developer's own Firebase project:
1. Register the app with its final package name. Enable Google sign-in. Add the debug SHA-1 (`./gradlew signingReport`). Create the Realtime Database.
2. **Only then** download `google-services.json` into `app/`. A file downloaded earlier has no web client id or database URL, and sign-in / database / the build fail. Re-download whenever sign-in, SHA-1 or the database change.
3. Publish `firebase/database.rules.json` as Realtime Database rules; `firebase/rtdb_sample.json` shows the data shape.
4. Remote Config → menu → Publish from a file → **select** `firebase/remote_config_template.json` with the file browser, then **Publish**. Don't drag and drop the file: the console rejects dropped files as "Invalid JSON file", even valid ones.
5. App Check → register the app with **Play Integrity**. Run a debug build, copy the `Firebase App Check debug token` from Logcat and add it under App Check → Manage debug tokens. Never commit the token.

Code rules:
- `.gitignore` contains `app/google-services.json` and `local.properties`.
- Google sign-in uses `getString(R.string.default_web_client_id)` (generated from `google-services.json`).
- `FirebaseDatabase.getInstance()` with no URL argument.
- When you add or change a Remote Config key, database path or rule, update the matching file in `firebase/` in the same change, and mention it in the delivery note.

---

## 8. Realtime Database — exact structure

```
Users/
  {uid}/
    Profile/
      email:          String
      name:           String
      createdAt:      Long   (ServerValue.TIMESTAMP, first sign-in only)
      lastAppOpenAt:  Long   (ServerValue.TIMESTAMP, every sign-in and every app open)
    watchlist/
      {type}_{id}/          e.g. movie_550, tv_1399
        uniqueId:   String  (same as the key)
        id:         String  (TMDB id as text)
        title:      String
        type:       String  ("movie" or "tv" only)
        posterPath: String  (e.g. "/abc.jpg")
        timestamp:  Long    (ms when added)
```

`AuthManager` writes `Profile` (with `updateChildren()`), and `AuthManager.recordAppOpen()` updates
`lastAppOpenAt`. Signed in = `AuthManager.isSignedIn()` (anonymous users count as signed out).

---

## 9. Sign-in

All sign-in, sign-out and delete-account logic is in the kit's `AuthManager`. Build the screens
around it. Get the Google credential with Credential Manager exactly like this (tested):

```kotlin
private suspend fun getGoogleCredential(activity: Activity): AuthCredential? {
    val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
        } else null
    } catch (e: GetCredentialCancellationException) { null }   // user closed the picker
    catch (e: GetCredentialException) { null }                // show an error
}
```

- Sign in: `AuthManager.signInWithGoogle(credential) { user, error -> … }`.
- Sign out: `AuthManager.signOut()`, then `CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest())`.
- Delete account: `AuthManager.deleteAccount { error -> }`. If `error == "reauth_required"`, get a fresh credential the same way and call `AuthManager.reauthenticateAndDelete(credential) { … }`. Clear credential state after a successful delete.

---

## 10. Watchlist

Use only `WatchlistRepository`:
- `observeAll()` (Flow, newest first) for the watchlist screen; `observeContains(uniqueId)` for the toggle on details.
- `toggle(item)`, `add(item)`, `remove(uniqueId)`. `uniqueId = WatchlistItem.key(type, id)`.
- Sync is automatic: merge on sign-in, live while signed in, cleared on sign-out, resumed at app start.

---

## 11. Remote Config

Parameters: `Data` (JSON, object `APP_SETTINGS`) and `Update_App` (JSON: appTitle, appDesc, appIcon, appURL).
Read only through `RemoteValues`. Ignore unknown keys.

| Key | Unit | Read as |
|---|---|---|
| `ad_status` | Boolean | `RemoteValues.adStatus` (master switch) |
| `SplashCounter` | **seconds** | `RemoteValues.splashCounter` (max wait for the splash ad) |
| `InterAdInterval` | **seconds** | `RemoteValues.interAdInterval` → ms; use `AdRules.interAdInterval()` |
| `NativeAdInterval` | **seconds** | `RemoteValues.nativeAdInterval` → ms; use `AdRules.nativeAdInterval()` |
| `URLAdInterval` | **seconds** | `RemoteValues.urlAdInterval` → ms (not used by app code) |
| `ppLink`, `tncLink` | String | `privacyPolicyUrl`, `termsUrl` |
| `min_supported_version` | Long | `minSupportedVersion` (used by `ForceUpdate`) |
| `gle_banner_id1/2/3`, `gle_native_ob`, `gle_native_home`, `gle_native_settings`, `gle_native_category`, `gle_inter_id`, `gle_splashInter_id`, `gle_welcome_inter_id` | ad unit object | `adUnitIds(key)`, `isAdUnitEnabled(key)` |

Ad unit object: `{"id": ["unit1", "unit2"], "isEnabled": true}`. Native units may add `"heightCTA": 40` (dp, the CTA
button height; missing = 40) and `"layout": "small" | "medium" | "large"` (overrides the size the code asks for; missing = the code's size). The kit keeps it within 36..`AdRules.nativeCtaMaxDp()` (default 46) — read via `RemoteValues.nativeCtaHeight(key)`, never directly.

The splash fetch is done by the kit's `SplashFlow` (interval 0, 10 s timeout, reset first when the saved fetch time is in the
future after a clock change, then `RemoteValues.reload()`). A failed or throttled fetch is fine; cached values are used.
Never fetch Remote Config yourself.

---

## 12. Splash — design only, the kit runs the sequence

Your SplashActivity designs the screen and calls the kit's `SplashFlow`. Never write the splash steps yourself and
never reorder, skip or add to them inside the splash.

```kotlin
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private val flow = SplashFlow(this)          // property: registers the permission launcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(…)                         // your design
        flow.start(
            onStatus = { status ->                // OFFLINE = show "turn on internet", LOADING = "please wait"
                …
            },
            onDone = { destination ->
                startActivity(destination ?: Intent(this, <first screen>::class.java))
                finish()
            }
        )
    }
}
```

Manifest (the splash handles rotation itself):
```xml
<activity
    android:name=".ui.splash.SplashActivity"
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"
    android:exported="true"> … MAIN / LAUNCHER intent filter … </activity>
```

What `SplashFlow` does, in this fixed order: waits for internet → Remote Config (section 11), then `Startup.run`; in parallel
consent + Mobile Ads init and the notification permission → force update (stops there) → `AuthManager.recordAppOpen()` →
interstitial preload → splash interstitial (up to `SplashCounter` s) → `onDone(destination)`. It blocks Back, runs
`Startup` once per launch even if the splash is recreated, and never waits more than 30 s for it.

- A non-null `destination` always wins: start it instead of your first screen.
- Onboarding: decide in `onDone` (e.g. first run → onboarding), but a non-null `destination` still wins.
- Logcat tag `SplashFlow` shows each step with timings.

---

## 13. Ads — kit usage

| Need | Use |
|---|---|
| Consent + Mobile Ads init | `AdsConsent.gather()` in the splash only |
| Banner | `BannerAd.load(activity, lifecycleOwner, container, RemoteValues.BANNER_HOME / BANNER_LIST / BANNER_DETAILS, label, root)` — in a Fragment pass `viewLifecycleOwner` |
| Native (kit layouts) | per placement (`KEY` = one of the four `RemoteValues.NATIVE_*` constants below): `private val xNative by lazy { NativeAds.viewModel(this, KEY) }`, then `NativeAds.attach(lifecycleOwner, xNative, slot, KEY, NativeSize.SMALL / MEDIUM / LARGE)` — `slot` is an empty `FrameLayout` (`visibility="gone"`); in a Fragment pass `viewLifecycleOwner`, in an Activity `this` |
| Native in a RecyclerView row | hold the ad from that placement's `NativeAds.viewModel(…).ad` and call `NativeAds.show(slot, ad, KEY, NativeSize.X)` in the ViewHolder |
| Native (your own layout) | collect `nativeAdViewModel.ad`; bind with `NativeAdBinder.bind(adView, ad, headline, body, cta, icon, media)` |
| Interstitial on navigation | `AdNavigator.open(activity, intent)`; for non-intent moments `AdNavigator.showThen(activity) { … }` |
| Splash interstitial | inside `SplashFlow` (section 12); never call `SplashInterstitial` yourself |
| Welcome-back screen + ad | `WelcomeBack.install(…)` in the Application (section 6); nothing else to call — see below |

Welcome back: when the app returns from the background, `WelcomeBack` opens `WelcomeBackActivity` (app icon, "Welcome back",
Start). Start shows the `gle_welcome_inter_id` interstitial (preloaded when the app goes to the background), then the user is
back on the screen they left. It never opens on a cold start, on rotation, over another full-screen ad, after the user tapped
an ad, on excluded screens, when the unit / `ad_status` / consent says no, or before the interstitial gap
(`AdRules.interAdInterval()`) has passed since the last interstitial of any kind. Showing it also restarts that gap.
- Exclude every screen it must not cover: in `install(…)`, or later with `WelcomeBack.exclude(SomeActivity::class.java)`.
- Call `WelcomeBack.skipNextResume()` right before **every** intent that leaves the app (browser, share sheet, mail, Play Store,
  YouTube, system settings). Put it in your one `openUrl` / share helper. `PlayStore`, `ForceUpdate`, `InAppUpdate` and
  `AppReview` already do it.
- Never show your own ad on returning to the app.

Native refresh rule: call `nativeAdViewModel.refreshIfNeeded(unitKey)` **only while the screen is visible** —
in `onResume()` when `!isHidden`, and in `onHiddenChanged(false)` for tabs switched with hide/show.
Never call it from a timer. The `NativeAdInterval` cap applies per screen's native ad.

Placements: `BANNER_HOME` home, `BANNER_LIST` list pages, `BANNER_DETAILS` details pages.

Natives — exactly these four placements, each with its own Remote Config unit:

| Placement | Constant | Remote Config key |
|---|---|---|
| Onboarding screen | `RemoteValues.NATIVE_OB` | `gle_native_ob` |
| Home | `RemoteValues.NATIVE_HOME` | `gle_native_home` |
| Settings | `RemoteValues.NATIVE_SETTINGS` | `gle_native_settings` |
| Category list (the screen that lists one selected genre) | `RemoteValues.NATIVE_CATEGORY` | `gle_native_category` |

Each unit has its own ids, `isEnabled`, `heightCTA` and optional `layout`. Pick each screen's default size in code; the
unit's `layout` in Remote Config can change it later. Never use one placement's constant on another screen, and don't
add native placements beyond these four. Several natives on one screen = one `NativeAds.viewModel(this, key)`,
one slot and one `attach()` per key (don't use `by viewModels()` for natives: it would share one ViewModel per screen).
Refresh each with `xNative.refreshIfNeeded(KEY)` under the visibility rule below.

Native sizes: SMALL = icon, headline, body, CTA (no media) · MEDIUM = text column + 150×120 dp media on the right, CTA ·
LARGE = header, body, full-width 170 dp media, CTA. `attach()` shows a pulsing placeholder of the same shape while the
first ad loads, the ad (and each refreshed ad) when it arrives, and hides the slot when there is no ad (also when the
unit key is missing from Remote Config). Any size can be used for any unit; the unit's `layout` in Remote Config, when
set, decides instead. The CTA height comes from `heightCTA` (section 11); never set it yourself.
Your own native layouts are also allowed: a `NativeAdView` root with headline (required), optional body, CTA, icon,
`MediaView`, and an "Ad" attribution label, bound with `NativeAdBinder`.

---

## 14. Google Play — kit usage (`playstore/`)

| Need | Use |
|---|---|
| Force update (Remote Config) | inside `SplashFlow` (section 12) |
| Play in-app update (IMMEDIATE) | `private val inAppUpdate = InAppUpdate(this)` as a **property** of the home Activity |
| In-app review | `AppReview.recordLaunch(this)` + `AppReview.maybeRequest(this)` (from the update callback) |
| Settings "Rate us" | `AppReview.openPlayStore(context)` (never the review API from a button) |

Home Activity:
```kotlin
private val inAppUpdate = InAppUpdate(this)   // property: registers its result launcher before onStart

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    …
    if (savedInstanceState == null) {
        AppReview.recordLaunch(this)
        inAppUpdate.check { AppReview.maybeRequest(this) }   // review only when there is no update
    }
}
```
- Rotation, process death, a destroyed Activity and Play errors are handled inside. Never wrap these classes or call the Play
  APIs (`AppUpdateManager`, `ReviewManager`) yourself.
- The update check runs once per app process; an interrupted update is resumed in `onResume` automatically.
- Review: asked after 4 launches or 3 days, at most every 30 days. Google decides whether the card appears and returns no
  result; never gate anything on it.
- Both only work for installs from Google Play (e.g. internal testing track); in local builds nothing appears.

---

## 15. TMDB and networking

- Key and base URL: `local.properties` → `BuildConfig` → `TmdbConfig` (kit). Read only through `TmdbConfig`.
- The kit's `NetworkClient` provides the one shared `OkHttpClient`, `Retrofit` (placeholder base URL rewritten by `TmdbInterceptor`) and `tmdbApi`. Any other client configuration: `NetworkClient.okHttp.newBuilder()…build()`.
- Interceptor order is fixed in `NetworkClient`: TMDB base URL + key → offline cache → HTTP 429 retry → `NetworkHooks.interceptors` → (network) 6 h cache TTL.
- `TmdbApi` paths are relative (`"trending/movie/week"`), `suspend` functions. Add endpoints and model fields as needed.
- List endpoints take `@Header("Cache-Control") cacheControl: String? = null`. Pull to refresh passes `"no-cache"`; normal loads pass `null`.
- Images (`https://image.tmdb.org/t/p/...`) load with the image library, not Retrofit.
- Home sections load in parallel and independently (one state per section), each with its own placeholder and retry. One failed section never blocks the page.
- Genres (Browse Categories) come from a hardcoded list, no API call.

---

## 16. Before finishing any task, check

- [ ] Kit files unchanged except the package name; nothing in `hooks/` removed or altered
- [ ] No new `OkHttpClient()` / `Retrofit.Builder()`; TMDB key only via `TmdbConfig`
- [ ] Sign-in / sign-out / delete only through `AuthManager`; watchlist only through `WatchlistRepository`
- [ ] Database writes match section 8
- [ ] Remote Config read only via `RemoteValues`; intervals treated as seconds in Remote Config
- [ ] Ads only via the kit; native refresh only while visible
- [ ] `WelcomeBack.install` with all excluded screens; `skipNextResume()` before every intent that leaves the app
- [ ] `InAppUpdate` is an Activity property; Rate us uses `AppReview.openPlayStore`; splash uses `ForceUpdate`
- [ ] Splash uses `SplashFlow` (section 12) with `configChanges` in the manifest; a non-null destination wins
- [ ] `firebase/` files updated if any key, path or rule changed
- [ ] No history, App Open ads, anonymous sign-in, extra sign-in providers or Privacy options button
- [ ] Debug and release builds succeed; `google-services.json` and `local.properties` not committed
