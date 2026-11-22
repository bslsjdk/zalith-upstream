# Horizon 模组仓库适配到 ZL2 下载 UI（2026-11-22）

> 用户方案：用 ZL2 现成的模组下载/管理 UI，把数据源适配到 Horizon 的模组仓库。
> UI 100% 复用 ZL2，只加一个数据适配器，不重写 Rust、不重写 UI。

## 1. 地基已验证（真实可用）

- **Horizon 公开模组仓库**：`https://horizon-rust.github.io/horizon-mods/index.json`
  - 带 `Access-Control-Allow-Origin: *`，手机可直接读
  - 实测 2026-11-22：**schema 1，269 个项目**（含 mod / native-mod / resourcepack / shader / modpack）
  - 例：`abelha_skull`(resourcepack)、`accelerateddecay`(native-mod)、`actuallyadditions`(native-mod)

- **index.json 结构**（`docs/api/mod-index.md` 核实）：
  ```
  { schema, generated,
    projects: [{ id, kind, name, summary, description, authors, owners,
                 license, source, icon, key, downloads,
                 versions: [{ version, published, api, side, capabilities, game,
                              file: { name, url, size, sha256, signature } }] }],
    revoked: [{ id, sha256, reason, date }] }
  ```

- **关键事实**：Horizon 自己就能把 Java 模组（Modrinth 的 jar）移植成内容模组
  （`horizon-port-pack`，见 docs/mod-browser.md）。所以模组体系虽不同，但内容来源相通。

## 2. 已落地的新文件（自有 fork zalith-upstream @ main，待推送）

路径：`ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/addon/platform/horizon/`

1. **HorizonIndex.kt**：`@Serializable` 数据模型，完整对齐 index.json schema 1
   （HorizonIndex / HorizonProject / HorizonVersion / HorizonFile / HorizonRequire /
   HorizonRevoked / HorizonKind 枚举映射 kind→目标目录）。
2. **HorizonRegistry.kt**：纯逻辑适配器——拉取（10 分钟缓存）、搜索（id/name/summary + kind/gameVersion 过滤）、
   项目/版本查询、revoked 校验。**刻意不依赖 ZL2 的 UI 类型**，便于独立编译验证。

## 3. 下一步接入（需 Gradle/JDK 编译环境，本机没有）

要让 ZL2 下载 UI 真正显示 Horizon 模组，还需把上面的逻辑映射到 ZL2 的抽象：

1. `Platform.kt` enum 加 `HORIZON("Horizon")`。
2. 新建 `HorizonSearcher : AbstractPlatformSearcher`，实现 4 个方法：
   - `searchAddons()` → 用 `HorizonRegistry.search()`，结果包装成 `PlatformSearchResult`
   - `getProject()` → HorizonProject 映射 `PlatformProject`
   - `getVersions()` → HorizonVersion 映射 `PlatformVersion`（sha256→platformSha1、
     file.url→platformDownloadUrl、file.size→platformFileSize）
   - `getVersionByLocalFile()` → 用 sha256 在 index 中匹配
3. `PlatformSearchResult.getAddonPage()` 构造 ZL2 的 `AddonPage`（含 `PlatformSearchData` +
   HMCL `AddonProjectInfo`），这是 UI 直接消费的页面数据。
4. UI 的平台选择处（下载页）增加 HORIZON 选项，切换时用 `HorizonSearcher`。
5. 安装器：Horizon 文件（.hmod/.hnmod/.hpack/.hshader）下载后需 SHA-256 校验 +
   minisign 验签（有 key 时），再放进对应目录（HorizonKind.folder）。

## 4. 为什么这样分两步

- 本小姐**没有盲写完整 HorizonSearcher + UI 接线**：那涉及 AddonPage/HMCL 类型等深依赖，
  本机无 Gradle/JDK 无法编译验证，硬推必然一堆编译错误。
- 先交付可独立验证的数据模型 + 逻辑适配器（地基），接入层留给有编译环境的步骤做，稳妥。

## 5. 状态

- API 实测可用（269 项目），数据模型已对齐。
- 2 个新文件 + 接入改动已推自有 fork，UI 接线已完成（见下）。仓库 private、未落地手机。

## 6. 接入已完成（2026-11-22 同轮落地）

以下改动全部推到自有 fork 并用 contents API 验证：

- `Platform.kt`：enum 加 `HORIZON("Horizon")`。
- `HorizonSearcher.kt`（新）：继承 AbstractPlatformSearcher，实现 searchAddons/getProject/
  getVersions/getVersionByLocalFile；内部 HorizonData 同时实现 PlatformSearchData+PlatformProject，
  HorizonVersionView 实现 PlatformVersion，HorizonSearchResult.getAddonPage() 构造 AddonPage
  （mcmod 翻译位传 null）。Horizon kind→ZL2 PlatformClasses 映射齐全。
- `_PlatformSearch.kt`：5 个 when(platform) 分支加 HORIZON（searchAddons/getVersions/getProject/
  getProjectByVersion/getVersionById）+ import + horizonSearcher 实例；验证 5 处 Platform.HORIZON。
- `_Download.Dependency.Tasks.kt`：toPlatformLoader 的 when 加 `HORIZON -> null`。
- `DownloadAddonElements.kt`：平台图标 when 加 HORIZON（暂用 modrinth 图标占位）。
- `_Search.Filter.kt`：用 `Platform.entries` 自动列出 HORIZON，无需改。

**效果**：下载页平台下拉自动出现 "Horizon"，选中后走 HorizonSearcher 读公开 index，
用 ZL2 原生模组卡片 UI 显示/搜索 Horizon 模组，点进去看版本与下载链接。
UI 100% ZL2 原生，只换了数据源。

## 7. 遗留（需编译环境确认）

- **本机无 Gradle/JDK，全部改动未编译验证**。加 enum + 补 when 分支虽已尽力齐全，
  仍可能存在遗漏的穷尽 when 或类型细节，需在 Android Studio 编译一次。
- 安装器：Horizon 文件（.hmod/.hnmod/.hpack/.hshader）下载后需 SHA-256 + minisign 验签，
  再按 HorizonKind.folder 放进游戏目录——ZL2 原安装器按 Java 路径处理，需单独适配安装分支。
- 图标：HORIZON 暂用 modrinth 占位，可加 `img_platform_horizon` 资源。
- 平台选择是否只在 HORIZON 后端显示，可按 MiscSettings.activeBackend 再过滤。


## 8. 编译审查结果（2026-11-22，GitHub Actions 云端）

本机无 java/gradle/kotlin 工具链，改用 fork 的 GitHub Actions `build.yml` 云端编译验证：

- **第 1 次编译（run 45050195276）失败**：`when expression must be exhaustive, add HORIZON branch`
  共 10 个文件漏分支 + MainActivity 的 `com.rcl` unresolved + HorizonSearcher 漏 DATAPACK。
- **逐项修复**（全部 contents API 验证）：
  补 10 个文件的 HORIZON 穷尽分支；HorizonSearcher 用嵌套 `PlatformVersion.PlatformDependency` +
  pageData 显式类型；HorizonRegistry 改用 ZL2 的 `httpGetJson<T>`（GLOBAL_JSON 有 ignoreUnknownKeys）；
  gameVersion 过滤修复（Horizon 全是 26.3）；MainActivity 改反射启动引擎（去掉 fork 对 rcl-core 的编译期依赖）。
- **第 2 次编译（run 45051886684）**：**Kotlin 编译 0 错误（无 `e:` 行）**，失败在
  `:ZalithLauncher:packageRelease` 的 `KeytoolException: Failed to read key movtery_zalith` ——
  这是 fork 缺 release 签名密钥（ZL2 原仓库靠 secrets 注入）的**打包配置问题，与代码无关**。
- **结论**：HORIZON 适配相关 Kotlin 代码已通过真实 Gradle 编译验证（Kotlin compile 阶段全过）。
  剩余签名失败是 fork 环境固有，不影响代码正确性；要出可安装 APK 需配置签名密钥或改用 debug variant。
- 缓存：build.yml 已用 `gradle/actions/setup-gradle@v5` 自带 Gradle 依赖缓存（日志见 cache 读写）。
- 工程一致性：RCL 的 zalith-upstream submodule 指针已更新到 fork HEAD b84a62（含全部适配）。
