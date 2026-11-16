# AGENTS.md — 给 AI 协作者的速查

Aquarius AI 是 [Aries-AI](https://github.com/ZG0704666/Aries-AI) 的定制改版（AGPL-3.0-only）。
本文件只放"AI 动手前必须知道"的硬约束；完整背景见 [README.md](./README.md)，逐版改动与架构细节见 [HANDOVER.md](./HANDOVER.md)。

## 事实来源

- 工具链以 `app/build.gradle.kts`、`gradle/libs.versions.toml`、`settings.gradle.kts` 为准：JDK 17、Gradle 9.4.1、AGP 9.2.1、Kotlin 2.4.10、KSP、compileSdk 37 / targetSdk 36 / minSdk 30。
- 包名（namespace）是 `com.ai.phoneagent`，**applicationId 是 `com.ai.phoneagent.aquarius`**（用于与原版共存）。新增 Activity/Service 时不要写死 applicationId。
- 仓库已**不含** `Aries-site/`（文档站）与 `telemetry-worker/`（统计后端），也没有 CMake / `app/src/main/cpp` / Git submodule。

## 布局

| 路径 | 内容 |
| --- | --- |
| `app/` | 唯一的 `com.android.application` 模块。入口：`AquariusAgentApp`（启动 Koin）、`MainActivity`、`PhoneAgentAccessibilityService`、`VirtualDisplayController`、`UiAutomationAgent` |
| `app/.../core/` | App 内的 agent 循环、动作解析/执行、工具、截图缓存、安全守卫（不是根目录的 `core/` 模块） |
| `app/.../core/settings/` | `AppUiPrefs`（SharedPreferences 即时偏好）、`Haptics`（唯一触感入口） |
| `app/.../core/overlay/` | `OverlayTaskBridge`：悬浮窗 ↔ ViewModel 的单例回调桥 |
| `app/.../vdiso/` | Shizuku 虚拟屏：`ShizukuVirtualDisplayEngine`、`VdGlFrameDispatcher`、`ImeFocusDeadlockController` |
| `app/.../{data,di,net,ui,viewmodel}` | Room/DataStore、Koin、API/SSE、Compose 界面、ViewModel |
| `app/.../AutomationOverlay.kt` | 悬浮窗（原生 View，非 Compose），约 1900 行 |
| `core/{common,designsystem,prompt,shizuku}` | 工具、M3 主题 + `m3t_*` 令牌、提示词、Shizuku 桥 |
| `feature/{settings,updates}` | 设置 UI、GitHub 更新检查 |

## 命令

```bash
./gradlew :app:assembleRelease        # 出包
./gradlew :app:testDebugUnitTest      # JVM/Robolectric 单测
./gradlew :core:designsystem:test     # 主题色断言在这里
./gradlew lintDebug                   # 所有模块 warningsAsErrors=true
```

- `testDebugUnitTest` 在 `core:designsystem` 可能卡住，必要时改为定向执行：`:app:testDebugUnitTest --tests "com.ai.phoneagent.di.*"`。
- 每个 Android 模块都有 `lint-baseline.xml` 且 `warningsAsErrors=true`：新增警告会让 `lintDebug` 失败。**不要**为了消警告去重刷 baseline。
- `local.properties` 必须有 `sdk.dir`；`gradle.properties` 的 `android.overridePathCheck=true` 不能删（路径可能含非 ASCII）。

## 硬约束（改了会破坏兼容）

| 不要动 | 位置 |
| --- | --- |
| `aries.db` / `aries_tasks.db` | `AquariusDatabase.kt`、`AutomationTaskDatabase.kt` |
| DataStore 键 `use_aries_api` / `aries_api_key` / `aries_selected_model` / `aries_logged_in_user` / `aries_api_section_unlocked` | `AppPreferencesRepository.kt` |
| KeyStore 别名 `aries_ai_preference_secrets` | `AndroidKeyStoreSecretStore.kt` |
| provider 标识 `"aries"` | `MainActivity.kt`、`SettingsViewModel.kt`（参与 `ApiConfigSignature`） |
| `api.aries.org.cn` / `sso.aries.org.cn` | `AquariusApiClient.kt` |
| `LICENSE` / `NOTICE` 原文 | 根目录（AGPL 要求） |

## 约定

- Maven 仓库只在 `settings.gradle.kts` 声明；模块级 `repositories {}` 会触发 `FAIL_ON_PROJECT_REPOS`。
- 复用 `core/designsystem/src/main/res/values{,-night}/m3t.xml` 与 `action_tokens.xml` 的令牌，别在 Kotlin 里硬编码 hex/dp/sp。默认强调色是品牌蓝 `#3964FE`；另有 OCEAN/FOREST/SUNSET/ROSE 四套可选主题（未随改版调整）。
- 用户可见文案放字符串资源，保持中文。
- 新的 Koin 单例/ViewModel 放 `app/.../di/`，`app/src/test/.../di/` 下的契约测试是安全网。
- 设置项统一用 `AppUiPrefs` + `DrawerSettingsScreen` 里的 `SettingsSwitchRow` / `NumericSettingRow`（已内置「即时生效 + 不设范围 + 恢复默认」）。
- 悬浮窗触摸优先级在 `OverlayContainer.dispatchTouchEvent` → `cornerAt()` → `isInsideBar()`：**「＋」> 底部操作栏 > 输入行 > 四角热区 > super**。动热区前先读这三处。
- `UI design/*.pen` 是加密设计稿，不要用文本工具读。
- 忽略 `._*` AppleDouble 文件与 `temp/`、`source/`、`key/` 等本地目录。

## 交付

构建 → `zipalign -P 16 -f 4` → `apksigner sign --v1 --v2 --v3` → `apksigner verify` + `zipalign -c -P 16`。
交付时给出：APK、源码 zip、SHA-256、包名、版本号、覆盖安装说明。签名与完整命令见 [HANDOVER.md](./HANDOVER.md) 第 4 节。
