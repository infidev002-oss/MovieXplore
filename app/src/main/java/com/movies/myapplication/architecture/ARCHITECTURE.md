# Architecture

Single-module Android app (`:app`) in plain Kotlin with **XML layouts**, structured as **MVI (Model-View-Intent)**.
It uses the Android SDK plus Retrofit, OkHttp, Kotlin Coroutines and AndroidX Fragment (for the main screen's four
tabs). No other Jetpack libraries (no AppCompat, ViewModel or Navigation).

**Stack at a glance:** Kotlin · XML layouts + Android SDK views · MVI · Coroutines/Flow · Retrofit + OkHttp ·
org.json · SharedPreferences. Full list with versions in [section 7](#7-tech-stack).

```
┌──────────────────────────── UI layer (ui/) ─────────────────────────────────┐
│                                                                             │
│   Activity / Tab (XML layout) ──Intent──▶  Store (MviStore<State, Intent, Effect>)
│        ▲                                        │                           │
│        └──────── State (StateFlow) ◀────────────┤  reduce { copy(...) }      │
│        └──────── Effect (Channel)  ◀────────────┘  one-off: navigate, pick account
│                                                                             │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │ calls
┌──────────────────────────── Data layer (data/) ─────────────────────────────┐
│  MediaRepository ── TmdbApi (Retrofit, raw JSON) ── parsed with org.json     │
│  WatchlistRepository ── SharedPreferences "watchlist" (JSON array of Media) │
│  AccountRepository  ── SharedPreferences "account"   (name / email)         │
│  SettingsRepository ── SharedPreferences "settings"  (last watch country)   │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │ built once by
┌──────────────────────────── DI (MovieApp.kt) ───────────────────────────────┐
│  MovieApp (Application) → AppContainer: OkHttp clients, Retrofit, repositories
│  Any screen reaches it with context.appContainer                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Package layout

```
com.movies.myapplication
├── MovieApp.kt              Application + AppContainer (manual DI) + network retry
├── mvi/MviStore.kt          MVI base class
├── data/
│   ├── MediaRepository.kt   TMDB content (org.json parsing)
│   ├── WatchlistRepository.kt, AccountRepository.kt, SettingsRepository.kt
│   ├── model/               Models (Media, MediaDetails, DiscoverFilter, Mood…)
│   └── remote/TmdbApi.kt    Retrofit interface returning raw JSON bodies
└── ui/
    ├── common/              BaseActivity, ImageLoader, Navigator, PagedGrid, adapters, view helpers
    ├── main/                MainActivity (tab host) + TabFragment
    ├── home/                HomeFragment + HomeStore
    ├── watchlist/           WatchlistFragment + WatchlistStore
    ├── mood/                MoodFragment + MoodStore, MoodResultsActivity
    ├── profile/             ProfileFragment + ProfileStore
    ├── browse/              BrowseActivity + BrowseStore + FilterPanel
    ├── category/            CategoryActivity + CategoryStore
    ├── details/             DetailsActivity + DetailsStore + WatchSheet, Season*, Episode*
    └── person/              PersonActivity + PersonStore

res/
├── layout/                  activity_*, tab_*, item_*, view_*, dialog_*, panel_* XML layouts
├── drawable/                ic_* vector icons, bg_* shapes, ripples, gradients
├── anim/                    bottom sheet slide in/out
└── values/                  colors, styles (text + buttons), themes, ids, attrs, strings
```

## 1. MVI core (`mvi/MviStore.kt`)

`MviStore<S, I, E>` is the base of every screen's logic. It is plain Kotlin and owns its own coroutine scope.

| Member | Role |
|---|---|
| `state: StateFlow<S>` | One immutable data class, the single source of truth for the screen |
| `onIntent(intent: I)` | The only entry point for user actions |
| `effects: Flow<E>` | One-off events (navigation, account picker) delivered through a `Channel`, never repeated |
| `reduce { copy(...) }` | The only way a store changes its state |
| `clear()` | Cancels the store's work when its screen is destroyed |

A screen creates its store in `onCreate`, registers it with `.attach()` (from `BaseActivity`) so it is cleared in
`onDestroy`, and collects it:

```kotlin
val store = PersonStore(appContainer.mediaRepository, id).attach()
store.state.observe { state -> /* update the XML views */ }
findViewById<View>(R.id.state_retry).setOnClickListener { store.onIntent(PersonIntent.Retry) }
```

Every activity declares `configChanges` in the manifest, so rotation does not recreate it and its store simply
carries on (the job Jetpack's ViewModel would otherwise do).

### Data flow of one action

```
User taps the bookmark on Details
  → store.onIntent(DetailsIntent.ToggleSave(media))
  → DetailsStore: watchlist.toggle(media)
  → WatchlistRepository saves to SharedPreferences and updates its keys StateFlow
  → DetailsStore: reduce { copy(savedKeys = keys) }
  → DetailsActivity's state observer swaps the bookmark icon
```

## 2. Screens

| Screen | Kind | Store | Intents | Effects |
|---|---|---|---|---|
| Home | tab | `HomeStore` | `Retry`, `ToggleSave` | – |
| Watchlist | tab | `WatchlistStore` | `SelectType`, `Remove` | – |
| Mood | tab | `MoodStore` | `ToggleMood`, `ClearMoods`, `Apply` | `OpenResults` |
| Profile | tab | `ProfileStore` | login, guest, Google account, logout, delete | `PickGoogleAccount` |
| Search (Browse) | activity | `BrowseStore` | `QueryChanged`, `OpenFilters`, `CloseFilters`, `ApplyFilter`, `RegionChanged`, `LoadMore`, `Retry` | `SearchClearedForChannel` |
| Mood results | activity | 2 × `BrowseStore` (Movies / TV) | as above | – |
| Category | activity | `CategoryStore` | `LoadMore`, `Retry` | – |
| Details | activity | `DetailsStore` | `Retry`, `SelectSeason`, `ToggleSave`, `OpenWatch`, `CloseWatch`, `SelectCountry`, `RetryWatch` | – |
| Seasons | activity | `SeasonStore` | `Select`, `Retry` | – |
| Episode | activity | `EpisodeStore` | `Retry` | – |
| Person | activity | `PersonStore` | `Retry` | – |

## 3. Navigation

- `MainActivity` hosts the four tab fragments (`TabFragment`s) in one container under a floating tab bar. Switching tabs, or
  tapping the current one, scrolls it to the top. Back on another tab returns to Home.
- Every other screen is its own Activity, opened through `ui/common/Navigator.kt` with Intent extras.
- Back is handled with AndroidX's `OnBackPressedDispatcher` (`BaseActivity.interceptBack`), on every Android version.

## 4. Shared UI (`ui/common/`)

- `BaseActivity`: edge-to-edge drawing, store lifecycle, state collection, Back handling.
- `ImageLoader`: posters and photos via OkHttp, memory cache (`LruCache`), disk cache, fade-in, two retries.
- `PagedGrid` + `MediaGridAdapter`: 3-column `GridView` of posters with paging and loading/error/empty states.
- `Views.kt`: poster cards, titled rows (posters, cast, trailers), top bar, state overlay, expandable text.
- `BottomSheet.kt`: bottom sheets on a plain `Dialog` (login, Watch Availability).
- `Ui.kt`: dp conversion, system bar insets, keyboard, haptics, pills.

## 5. Data layer (`data/`)

| Class | Backed by | API |
|---|---|---|
| `MediaRepository` | `TmdbApi` (Retrofit) + org.json | trending, popular, top rated, discover, search, details, seasons, episodes, person, genres, providers, watch availability, countries |
| `WatchlistRepository` | SharedPreferences `watchlist` | `items`, `keys` (`StateFlow`), `toggle()`, `clear()` |
| `AccountRepository` | SharedPreferences `account` | `account: StateFlow<Account?>`, `signIn()`, `signOut()`; display details only |
| `SettingsRepository` | SharedPreferences `settings` | `watchCountry: StateFlow<String>` (default `US`), `setWatchCountry()` |

## 6. Dependency injection (`MovieApp.kt`)

Manual. `AppContainer` is created once in `MovieApp.onCreate()` and builds:

- a base `OkHttpClient` (10 s connect / 15 s read timeouts, 10 s HTTP/2 pings, retry-once interceptor),
- an image client (50 MB disk cache) and an API client (10 MB cache + TMDB API key interceptor) derived from it,
- one Retrofit `TmdbApi` with no converter (raw JSON bodies),
- the four repositories.

Screens reach it through `context.appContainer`.

## 7. Tech stack

What the app uses for each job. Versions come from `gradle/libs.versions.toml`.

### Language and build

| Concern | Used | Version |
|---|---|---|
| Language | Kotlin | 2.4.20 |
| Build system | Gradle (Kotlin DSL) + Android Gradle Plugin, version catalog `libs.versions.toml` | AGP 9.4.1 |
| SDK levels | `minSdk` 26 (Android 8.0), `targetSdk` / `compileSdk` 37 | – |
| Java target | Java 11 bytecode | – |
| Release optimization | R8 (shrink + optimize) | – |
| Secrets | `TMDB_API_KEY` read from `local.properties` (git-ignored) into `BuildConfig` | – |

### UI

| Concern | Used |
|---|---|
| Layouts | XML layouts in `res/layout` |
| Views | Android SDK widgets: `Activity`, `ScrollView`, `GridView`, `ListView`, `HorizontalScrollView`, `Spinner`, `Dialog`, `AlertDialog` |
| Theme | `android:Theme.Material.NoActionBar` with app colors and text styles |
| Icons | Vector drawables (`res/drawable/ic_*.xml`) |
| Images | Own `ImageLoader` (OkHttp + `LruCache` + `BitmapFactory`) |

### Architecture and state

| Concern | Used | Version |
|---|---|---|
| Pattern | MVI: own `MviStore` base class (see section 1) | – |
| Async / streams | Kotlin Coroutines + Flow (`StateFlow` for state, `Channel` for effects) | 1.10.2 |
| Dependency injection | Manual: `AppContainer` in `MovieApp.kt` | – |

### Networking

| Concern | Used | Version |
|---|---|---|
| Backend | TMDB REST API v3 (`https://api.themoviedb.org/3/`) | – |
| HTTP client | OkHttp (disk caches, timeouts, pings, retry, API-key interceptor) | 4.12.0 |
| REST client | Retrofit (`TmdbApi` interface, suspend functions returning raw bodies) | 3.0.0 |
| JSON | `org.json` (part of Android) | – |

### Local storage

| Concern | Used |
|---|---|
| Watchlist | SharedPreferences `watchlist`: the list stored as one JSON array |
| Account | SharedPreferences `account`: name, email, guest flag |
| Settings | SharedPreferences `settings`: last country picked in Watch Availability |
| API response cache | OkHttp disk cache in `cacheDir/tmdb_http` |
| Image cache | Memory (`LruCache`) + OkHttp disk cache in `cacheDir/images` |

### Authentication

| Concern | Used |
|---|---|
| Google account | Android's account picker (`AccountManager.newChooseAccountIntent`): the user picks a Google account on the phone; its email is stored |
| Guest mode | Local only, stored in SharedPreferences |

### Testing

| Concern | Used | Version |
|---|---|---|
| Unit tests | JUnit 4 | 4.13.2 |
