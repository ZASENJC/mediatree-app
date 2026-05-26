**English** | [简体中文](../wiki_zh-CN/Android-MD3-UI-Guide)

# Android MD3 UI Implementation Guide

This guide maps the current UI reference design to the native Android client in `mediatree-app`. The scope is Android app work only: do not package, embed, or modify backend code unless a later task explicitly asks for backend changes.

## Goals

- Use Material Design 3 / Material You as the shared visual language.
- Keep four primary bottom destinations: `Home`, `Browse`, `Favorites`, and `Settings`, with a translucent `NavigationBar`.
- Keep the `Detail` screen playback-first: player on top, metadata below.
- In the revised `Detail` layout, swap the `Cast` and `Episodes` sections.
- In `Settings`, merge account status and logout into the `Backend Connection` section, and add a separate `SMB Server` section.
- Implement the UI in the native Compose project under `frontend/android/`.

## References

- Material 3 foundations: https://m3.material.io/foundations
- Material 3 components: https://m3.material.io/components
- Jetpack Compose Material 3: https://developer.android.com/develop/ui/compose/designsystems/material3

## Code Map

| Area | File |
| --- | --- |
| Theme, colors, typography, shapes | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/theme/Theme.kt` |
| App shell and translucent bottom bar | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt` |
| Shared MD3 components | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/components/SharedComponents.kt` |
| Home | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt` |
| Browse | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/screens/BrowseScreen.kt` |
| Favorites | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/screens/FavoritesScreen.kt` |
| Settings | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt` |
| Detail | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt` |
| API adaptation | `frontend/android/app/src/main/java/com/zasenjc/mediatree/data/MediaTreeApi.kt`, `Models.kt` |

## Design Tokens

Keep tokens centralized in `Theme.kt`.

### Color

Add both light and dark schemes. The reference design is light-first, so the app should have a `MediaTreeLightScheme` and use dynamic color on Android 12+ where available.

- `primary`: muted teal for selected states, primary actions, and progress.
- `secondary`: soft lavender for secondary states and filter chips.
- `tertiary`: coral accent for favorites and highlight badges.
- `surface` / `surfaceContainer*`: page background, cards, bottom bar, settings groups.
- `outlineVariant`: dividers, input borders, weak list boundaries.

Suggested starting point:

```kotlin
private val MediaTreeLightScheme = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF74F8E5),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF8B4A45),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
)
```

### Typography

- Logo: lowercase `mediatree`, `titleLarge` or a dedicated semi-bold style.
- Screen title: `titleLarge`.
- Section title: `titleMedium`.
- Card title: `bodyLarge` / `titleSmall`, max two lines.
- Metadata and paths: `bodySmall` / `labelMedium`, using `onSurfaceVariant`.

### Shape, Spacing, Media Ratios

- Standard card: `16.dp` radius.
- Poster card: `18.dp` radius.
- Bottom nav active indicator: `24.dp` pill radius.
- Settings section card: `24.dp` radius.
- Screen horizontal padding: `16.dp`.
- Section spacing: `20.dp`.
- Episode still: `16f / 9f`.
- Poster: `2f / 3f`.
- Cast avatar: circular `48.dp`.

## Shared Components

Build shared components first, then refactor screens.

- `MediaTreeTopAppBar`: home logo on the left; search, sort/tune, and overflow on the right.
- `FrostedBottomNavigationBar`: translucent MD3 bottom navigation with selected indicator.
- `EpisodeLandscapeCard`: 16:9 card for continue watching and episode favorites.
- `PosterMediaCard`: 2:3 poster card for movies and full series.
- `AdaptiveMediaGrid`: mixed landscape/portrait layout for Favorites.
- `SettingsSectionCard`: grouped settings surface.
- `ConnectionStatusChip`: backend connection state.
- `LibrarySegmentedSelector`: library display switching.
- `SmbServerForm`: SMB input form.

For the bottom bar, use `surfaceContainerHigh.copy(alpha = 0.82f)` and `tonalElevation = 3.dp`. Real background blur should not block the first implementation; translucent tonal surfaces are enough for the first pass.

## Screen Guide

## Home

`HomeScreen.kt` already has the basic continue-watching and library structure.

1. Change the top title to left-aligned lowercase `mediatree`.
2. Use three actions: `Search`, `Sort` / `Tune`, `MoreVert`.
3. Keep `/api/recent-watched` for `Continue Watching`.
4. Use landscape `EpisodeLandscapeCard` with title, episode title/number, and progress.
5. Change `Media Library` from folder cards to a movie feed using `container.api.movies(sort = "release_date_desc", mediaRoot = session.activeLibrary)`.
6. Use vertical poster cards in an adaptive grid or staggered grid.
7. Add filter chips: `All`, `Movies`, `Series`, `New`.

## Browse

`BrowseScreen.kt` should stay file-manager-oriented.

1. Root view shows mounted source folders.
2. Folder cards show icon, name, path, movie count, and scan/release metadata.
3. Add breadcrumb, search field, and sort chip.
4. Inside a folder, preserve file browsing context while listing media.
5. Put refresh/scan in a FAB or top app bar action.

Data sources:

- Root folders: `container.api.folders(session.activeLibrary)`.
- Folder media: `container.api.movies(folder = folder, mediaRoot = session.activeLibrary)`.

## Favorites

`FavoritesScreen.kt` currently reuses `MovieList`; replace it with a mixed adaptive grid.

1. Use `EpisodeLandscapeCard` for favorited single episodes.
2. Use `PosterMediaCard` for movies and full series.
3. Initial inference:
   - `tmdbEpisode != null` or `episodeTitle != null`: episode card.
   - Otherwise: poster card.
4. If the backend later returns `media_type` / `favorite_scope`, replace inference with explicit fields.

## Settings

`SettingsScreen.kt` is a main revision point.

### Backend Connection

Merge account and logout controls into the same section:

- Backend URL `OutlinedTextField`.
- Connection status `AssistChip`.
- Account/login state row.
- Username/password fields when login is needed.
- Save button.
- Logout button inside this same card.

The current `Session` has `serverUrl`, `token`, and `activeLibrary`, but no username. Until username is available, display `Logged in` / `Not logged in`.

### SMB Server

Add a separate `SMB Server` section:

- Server address, for example `smb://192.168.1.10`.
- Share path, for example `/Media/Movies`.
- Username.
- Password.
- Enabled switch.
- Add server button.
- Existing SMB source list item.

Boundary:

- The current app API has no SMB management endpoints.
- First pass can implement UI state only, with disabled or pending submission behavior.
- Real SMB persistence requires backend endpoints such as `GET /api/smb-servers`, `POST /api/smb-servers`, and `DELETE /api/smb-servers/{id}`. That is a backend change and needs separate confirmation.

### Library Display

- Use `SegmentedButton` or `FilterChip` group for `All Libraries`, `Movies`, and `Series`.
- Real active library still uses `sessionStore.setActiveLibrary(path)`.
- Library data still comes from `/api/media-roots`.

## Detail

`DetailScreen.kt` already contains playback, subtitle selection, favorite/watched actions, and metadata.

1. Keep the player at the top.
2. Keep subtitle chips, title, episode title, date, duration, and media metadata below the player.
3. Use MD3 buttons for favorite and watched actions.
4. Swap the `Cast` and `Episodes` sections:
   - `Cast` becomes the prominent section, using avatar cards and names.
   - `Episodes` moves to the alternate/secondary position, using chips/cards such as `S01E01`, `S01E02`.
5. Use `movie.cast` first, then fallback to `movie.actress`.
6. Use `movie.crew` for staff; directors are crew entries whose `job` contains `director`.
7. Thumbnail strip can use `episodeStill`, `javdbThumbnails`, or `/api/episode-still/{id}`.

Episode list data:

- `MovieDto` has `tmdbSeason`, `tmdbEpisode`, and `episodeTitle`, but no dedicated sibling episode endpoint.
- First pass can query the parent folder with `container.api.movies(folder = parentFolder, sort = "release_date_desc")`.
- Replace this with a series/season endpoint if the backend provides one later.

## API Notes

Already available:

- `recentWatched()`
- `movies(sort = "release_date_desc")`
- `folders()`
- `favorites()`
- `detail(movieId)`
- `subtitleTracks(movieId)`
- `mediaRoots()`
- `scan(mediaRoot)`

Needs confirmation or backend support:

- SMB server create/update/delete.
- Explicit favorite scope for episode vs full series.
- Series/season episode list.
- Current username.

Without backend support, prefer progressive UI: show the form, disable submission, or explain that backend support is required. Do not fake a successful state.

## Implementation Order

1. Update `Theme.kt`.
2. Add shared MD3 components.
3. Update `MediaTreeApp.kt` bottom navigation.
4. Refactor `HomeScreen.kt`.
5. Refactor `BrowseScreen.kt`.
6. Refactor `FavoritesScreen.kt`.
7. Refactor `SettingsScreen.kt`.
8. Refactor `DetailScreen.kt`.
9. Add loading, empty, and error states.
10. Build and test.

## Acceptance Checklist

- Four primary pages share the same translucent bottom navigation.
- Home top bar shows lowercase `mediatree` and search/sort/more actions.
- Home continue watching uses landscape episode cards.
- Home media library uses vertical poster feed sorted by release date descending.
- Browse looks like a folder/file manager.
- Favorites mixes landscape and portrait cards naturally.
- Settings has account/login/logout inside `Backend Connection`.
- Settings has a separate `SMB Server` section.
- Detail can play directly, and `Cast` / `Episodes` positions are swapped.
- All screens have loading, empty, and error states.
- Chinese UI copy is preserved where applicable; code identifiers remain English.
- `cd frontend/android && ./gradlew assembleDebug` passes.

## Tests

After code implementation, run:

```bash
cd frontend/android && ./gradlew assembleDebug
```

If API models, URL utilities, or compatibility logic changes, also run:

```bash
cd frontend/android && ./gradlew testDebugUnitTest
```
