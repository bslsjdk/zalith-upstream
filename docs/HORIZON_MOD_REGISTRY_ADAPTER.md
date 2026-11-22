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
- 2 个新文件推到自有 fork（独立文件，不改现有逻辑）。
- 模组下载 UI 的完整接线待编译环境完成。仓库 private、未落地手机。