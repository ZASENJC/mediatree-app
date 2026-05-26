[English](../wiki/Android-MD3-UI-Guide) | **简体中文**

# Android MD3 UI 开发指引

本文档用于把当前 UI 参考图落地到 `mediatree-app` 原生 Android 客户端。实现范围只包含 Android app：不把后端打进 APK，不修改 `backend/`，除非后续明确要求后端配合。

## 目标

- 使用 Material Design 3 / Material You 作为统一设计语言。
- 底栏保持四个一级页面：`首页`、`浏览`、`收藏`、`设置`，采用半透明 `NavigationBar`。
- `影片页` 以播放器为首屏核心，上方播放，下方信息；`演员` 与 `剧集` 信息区按修订版参考图换位。
- `设置页` 将账户状态、登录相关入口、退出登录并入 `后端连接` 区块，并新增 `SMB 服务器` 设置区块。
- 所有 UI 改造优先落在 `frontend/android/` 原生 Compose 工程内。

## 参考资料

- Material 3 foundations: https://m3.material.io/foundations
- Material 3 components: https://m3.material.io/components
- Jetpack Compose Material 3: https://developer.android.com/develop/ui/compose/designsystems/material3

## 代码落点

| 范围 | 文件 |
| --- | --- |
| 主题、颜色、字体、形状 | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/theme/Theme.kt` |
| 根导航、半透明底栏 | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt` |
| 共用 MD3 组件 | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/components/SharedComponents.kt` |
| 首页 | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt` |
| 浏览 | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/screens/BrowseScreen.kt` |
| 收藏 | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/screens/FavoritesScreen.kt` |
| 设置 | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt` |
| 影片页 | `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt` |
| API 适配 | `frontend/android/app/src/main/java/com/zasenjc/mediatree/data/MediaTreeApi.kt`、`Models.kt` |

## 设计 Token

优先把视觉系统收敛到 `Theme.kt`，避免每个 screen 自己硬编码颜色和圆角。

### 颜色

建议补齐 light / dark 两套 `ColorScheme`。参考图以浅色为主，因此默认应支持浅色 `MediaTreeLightScheme`，Android 12+ 可启用 dynamic color。

- `primary`：静音青绿色，用于选中态、主按钮、进度条。
- `secondary`：柔和淡紫，用于次级状态、筛选 chip。
- `tertiary`：珊瑚色，用于收藏、重点徽标。
- `surface` / `surfaceContainer*`：用于页面背景、卡片、底栏和设置分组。
- `outlineVariant`：用于分割线、输入框边界、列表弱边界。

实现建议：

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

### 字体

- App logo：`titleLarge` 或独立 `TextStyle`，小写 `mediatree`，字重 `SemiBold`。
- Screen title：`titleLarge`。
- Section title：`titleMedium`。
- 卡片标题：`bodyLarge` / `titleSmall`，最多两行。
- 元信息、路径、说明：`bodySmall` / `labelMedium`，颜色使用 `onSurfaceVariant`。

### 形状与间距

- 普通卡片：`16.dp` 圆角。
- 海报卡：`18.dp` 圆角，图片裁切同圆角。
- 底栏 active indicator：`24.dp` 圆角胶囊。
- 设置分组卡：`24.dp` 圆角。
- 屏幕边距：`16.dp`。
- 区块间距：`20.dp`。
- 卡片内边距：`12.dp` 或 `16.dp`。

### 图片比例

- 剧集横版封面：`16f / 9f`。
- 竖版海报：`2f / 3f`。
- 演员头像：`48.dp` 圆形。
- 影片页播放器：竖屏 `16f / 9f`，横屏全屏。

## 共用组件

建议先抽取组件，再改页面，避免每个 screen 重复实现。

### `MediaTreeTopAppBar`

用途：统一顶部栏。

- 首页使用左侧 `mediatree` wordmark。
- 首页右侧依次放 `Search`、`Sort` / `Tune`、`MoreVert`。
- 其他页面使用标题居中或左对齐，按内容密度选择。

### `FrostedBottomNavigationBar`

用途：替换当前 `MediaTreeApp.kt` 中裸 `NavigationBar`。

实现要点：

- `containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)`。
- 使用 `tonalElevation = 3.dp`。
- 外层增加水平边距和底部安全区，形状可用 `RoundedCornerShape(28.dp)`。
- 继续在 `detail` route 隐藏底栏。
- `NavigationBarItem` 保持四项，不新增一级入口。

注意：Compose 原生背景模糊不应成为首期阻塞项；先做半透明和 tonal surface，后续如需真实 blur 再评估实现成本。

### 媒体卡片

建议拆成：

- `EpisodeLandscapeCard`：继续观看、单集收藏，`16:9`，底部渐变 scrim，标题、集数、进度条。
- `PosterMediaCard`：媒体库、整剧收藏，`2:3`，标题、年份 chip、收藏状态。
- `AdaptiveMediaGrid`：收藏页混排横竖卡，按 `GridCells.Adaptive` 或 staggered grid 实现。

### 设置组件

建议拆成：

- `SettingsSectionCard(title, icon, content)`。
- `ConnectionStatusChip(status)`。
- `LibrarySegmentedSelector`。
- `SmbServerForm`。

设置页所有输入使用 MD3 `OutlinedTextField`，操作按钮优先使用 `FilledTonalButton` / `Button`，危险操作使用 `TextButton` 或 `OutlinedButton` 并避免视觉抢主按钮。

## 页面实现指引

## 首页

当前 `HomeScreen.kt` 已有继续观看、媒体库、搜索和排序基础。改造时做以下调整：

1. 顶栏标题改为左侧 `mediatree`，不要使用居中的 `MediaTree`。
2. 右侧按钮改为：`Search`、`Sort` / `Tune`、`MoreVert`。
3. `继续观看` 继续使用 `/api/recent-watched`，卡片改为横版 `EpisodeLandscapeCard`。
4. `媒体库` 不再只显示 folder card；应调用 `container.api.movies(sort = "release_date_desc", mediaRoot = session.activeLibrary)` 获取影片流。
5. 使用瀑布流或自适应网格展示竖版海报。若当前 Compose 版本没有稳定 staggered grid，可先使用 `LazyVerticalGrid(GridCells.Adaptive(150.dp))`，后续替换为 `LazyVerticalStaggeredGrid`。
6. 顶部添加筛选 chip：`全部`、`电影`、`剧集`、`新上架`。如果后端暂不支持类型过滤，首期只改 UI 状态，不发送额外参数。

验收标准：

- 首屏可同时看到 `继续观看` 和 `媒体库` 起始内容。
- 横版卡片显示标题、集数或 `episodeTitle`、观看进度。
- 海报卡按发行日期从新到旧请求数据。

## 浏览

`BrowseScreen.kt` 保持文件管理定位，不做海报墙。

1. 根层展示挂载媒体库里的源文件夹。
2. 卡片内容包含 folder icon、名称、路径、影片数量、最近扫描/最新发行日期等元信息。
3. 顶部加入 breadcrumb、搜索输入框和排序 chip。
4. 进入具体文件夹后，可继续使用当前 movie list，但视觉上要保留“文件夹浏览”的上下文。
5. 刷新/扫描操作可放到 `FloatingActionButton` 或 top app bar action。

数据来源：

- 根层：`container.api.folders(session.activeLibrary)`。
- 文件夹内影片：`container.api.movies(folder = folder, mediaRoot = session.activeLibrary)`。

## 收藏

`FavoritesScreen.kt` 当前复用了 `MovieList`。新设计需要自适应混排。

1. 新建 `FavoriteAdaptiveGrid`，不要直接复用普通列表。
2. 单集收藏使用横版 `EpisodeLandscapeCard`。
3. 整部剧 / 电影收藏使用竖版 `PosterMediaCard`。
4. 判断方式首期可使用现有字段：
   - `tmdbEpisode != null` 或 `episodeTitle != null`：按单集展示。
   - 其他：按竖版海报展示。
5. 如果后端未来返回明确 `media_type` / `favorite_scope`，再替换推断逻辑。

验收标准：

- 横版和竖版卡片可以自然堆叠。
- 卡片有收藏态视觉标记。
- 空收藏状态使用 MD3 empty state，不使用大段说明文字。

## 设置

`SettingsScreen.kt` 是修订版重点。

### 后端连接

将账户和退出登录并入 `后端连接` section/card：

- `服务器地址`：`OutlinedTextField`。
- `连接状态`：`AssistChip`，如 `已连接`、`需要登录`、`离线`。
- `账号`：已登录时显示用户名或 token 状态；未登录时显示账号/密码输入。
- `保存`：`Button` 或 `FilledTonalButton`。
- `退出登录`：放在同一个卡片内，使用 `TextButton` 或 `OutlinedButton`。

当前 `Session` 只有 `serverUrl`、`token`、`activeLibrary`，没有 username。若要展示用户名，需要后端提供或客户端保存登录账号；首期可以显示 `已登录` / `未登录`。

### SMB 服务器

新增独立 `SMB 服务器` section/card：

- `服务器地址`，如 `smb://192.168.1.10`。
- `共享路径`，如 `/Media/Movies`。
- `用户名`。
- `密码`。
- `启用` switch。
- `添加服务器` 主按钮。
- 已添加服务器列表 item。

重要边界：

- 当前 app API 中没有 SMB 管理端点。
- 首期可先做 UI 表单和本地输入状态，不承诺真实保存。
- 若要真实添加 SMB source，需要后端新增 API，例如 `GET /api/smb-servers`、`POST /api/smb-servers`、`DELETE /api/smb-servers/{id}`。这属于后端变更，需要单独确认。

### 媒体库显示

- 使用 `SegmentedButton` 或一组 `FilterChip` 切换 `全部媒体库`、`电影库`、`剧集库`。
- 当前真实 active library 仍通过 `sessionStore.setActiveLibrary(path)` 保存。
- 媒体库列表继续来自 `/api/media-roots`。

## 影片页

`DetailScreen.kt` 当前已具备播放器、字幕、收藏、已看、元信息。改造重点是信息结构和位置。

1. 顶部播放器保持第一信息层级。
2. 播放器下方保留字幕 chips、标题、episode title、年份、时长、编码等 metadata chips。
3. 操作按钮使用 MD3：`收藏` 用 tonal/favorite accent，`已看` 用 filled tonal。
4. `演员` 与 `剧集` 两个 section 按修订版换位：
   - `演员` 放到原先剧集选择的主要位置，展示头像、姓名、角色名。
   - `剧集` 放到原先演员信息的旁侧/次级位置，展示 `S01E01`、`S01E02` 等 chips/cards。
5. `演员` 数据优先使用 `movie.cast`，回退到 `movie.actress`。
6. `staff` 使用 `movie.crew`，导演优先筛选 `job` 包含 `director` 的人员。
7. 缩略图条优先使用 `episodeStill`、`javdbThumbnails` 或 `/api/episode-still/{id}`。

剧集列表数据说明：

- 当前 `MovieDto` 有 `tmdbSeason`、`tmdbEpisode`、`episodeTitle`，但没有专门的“同剧集列表” endpoint。
- 首期可以使用当前影片所在 folder 请求 `container.api.movies(folder = parentFolder, sort = "release_date_desc")` 作为剧集列表候选。
- 如果后端未来提供 series/season endpoint，再替换数据来源。

## API 与数据适配

已有可直接使用的 API：

- `recentWatched()`：继续观看。
- `movies(sort = "release_date_desc")`：媒体库发行日期排序。
- `folders()`：浏览页源文件夹。
- `favorites()`：收藏页。
- `detail(movieId)`：影片页基础信息。
- `subtitleTracks(movieId)`：字幕 selector。
- `mediaRoots()`：设置页媒体库。
- `scan(mediaRoot)`：扫描当前媒体库。

需要确认或后续补齐的能力：

- SMB 服务器新增、保存、删除。
- 收藏范围：单集收藏与整部剧收藏的明确字段。
- 同剧集/同 season 的 episode list。
- 当前登录账号名称。

没有后端支持时，app 侧应采用渐进策略：先显示 UI、禁用提交或提示“需要后端支持”，不要伪造成功状态。

## 实施顺序

1. `Theme.kt`：补齐 light scheme、surface container、typography、shapes。
2. `SharedComponents.kt`：抽取 top app bar、底栏、媒体卡、设置 section。
3. `MediaTreeApp.kt`：替换半透明底栏并确认 detail route 隐藏。
4. `HomeScreen.kt`：改首页结构和媒体库数据源。
5. `BrowseScreen.kt`：强化文件夹管理样式。
6. `FavoritesScreen.kt`：改为自适应混排。
7. `SettingsScreen.kt`：合并后端连接账户区，新增 SMB section。
8. `DetailScreen.kt`：调整播放器下方信息结构，交换 `演员` / `剧集` 位置。
9. 小屏、横屏、空状态、加载态、错误态统一补齐。
10. 运行构建和必要测试。

## 验收清单

- 四个主页面底栏一致，半透明且选中态清晰。
- 首页顶部显示小写 `mediatree`，右侧包含搜索、排列、更多图标。
- 首页 `继续观看` 为横版集封面，`媒体库` 为竖版海报流。
- 浏览页看起来像文件管理器，而不是媒体海报墙。
- 收藏页横竖卡混排自然，能区分单集和整部剧/电影。
- 设置页账号、登录状态、退出登录位于 `后端连接` 内。
- 设置页存在独立 `SMB 服务器` 设置栏。
- 影片页播放器可直接播放，`演员` 和 `剧集` section 位置已交换。
- 所有页面有 loading、empty、error 状态。
- 文案使用中文，代码标识符保持英文。
- `cd frontend/android && ./gradlew assembleDebug` 通过。

## 测试建议

文档落地到代码后，至少运行：

```bash
cd frontend/android && ./gradlew assembleDebug
```

如果修改了 `UrlUtils.kt`、`Models.kt` 或 API 兼容逻辑，再运行：

```bash
cd frontend/android && ./gradlew testDebugUnitTest
```
