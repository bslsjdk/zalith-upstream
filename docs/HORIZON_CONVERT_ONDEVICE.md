# 原版 Java 26.3 → Horizon 26.3 转换：手机端落地（2026-11-23）

## 真相（基于真代码调查，不再猜测）
之前转换功能"不存在"的三层根因：
1. fork（真正编译进 APK 的 ZalithLauncher 模块）里**没有 libhorizon_android.so** —— Rust 引擎没编进 APK；
2. RCL 主仓库的转换代码（HorizonResourcePreparer / PrepareBridge / JvmRunner）**全在没被编译的模块**，且 runPrepare/captureOutput/fillMissingFromNetwork 全是 TODO 占位；
3. 主界面 LauncherScreen.kt **没有**任何转换/后端切换 UI；Menu 里 activeBackend 设置项存在但无入口消费。

上游事实：horizon-prepare 是桌面 Rust 工具，跑 Java exporter 需要 JVM；上游 release 只有 Linux/macOS/Windows launcher，无 Android APK，也无现成 resources 包可下载。

## 突破口（决定性）
读 game.rs 源码确认：引擎判定"资源就绪"的唯一标准就是 **`<resources>/horizon-resources.json` 文件存在**（测试 game.rs:22302 写 "{}" 即视为就绪）。
resources 主体（assets/minecraft/textures、models、lang、data/*）全是版权资源，本质在玩家自己的 26.3.jar 里 —— 用 java.util.zip 纯解压即可，Android 原生支持（API 1+），**不需要 JVM / prepare 二进制 / 联网**。

## 已落地（4 文件，全推到 fork 真正编译的目录，远端回读全 OK）
1. `game/horizon/HorizonResourceConverter.kt`：纯 Kotlin 转换器。找 26.3.jar → 解压 assets/data/pack.mcmeta（带 zip-slip 防护）→ 复制 launcher assets → 写 horizon-resources.json + pack.mcmeta。带 check()/进度回调。
2. `ui/screens/content/HorizonConvertScreen.kt`：ZL2 原生风格转换界面，输入源/目标路径 + 版本，后台 Dispatchers.IO 转换 + 进度条。
3. `ui/activities/HorizonConvertActivity.kt`：独立入口 Activity（ComponentActivity + setContent），零侵入现有 UI。
4. `AndroidManifest.xml`：注册 HorizonConvertActivity（LAUNCHER intent-filter），桌面可直接打开"Horizon 转换"。

## 验证
- 编译触发：fork build.yml workflow_dispatch → run 45118982354（head 1107e775），等待结果。

## 仍在的局限（如实）
- 引擎 so 仍未编进 APK：转换出的 resources 目录已就绪，但"用 Horizon 后端启动游戏"仍缺引擎本身。转换功能已完成，引擎集成是另一件事。
- worldgen 二进制表仍需 Java exporter 精确生成；本方案用 vanilla 默认世界生成兜底（引擎读不到自定义表时用内置）。非原版逐字节等价，但可启动。
- 仓库 private，未落地手机（用户自下载）。
