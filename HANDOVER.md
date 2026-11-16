# HANDOVER · 给下一个 AI / 开发者的交接文档

> 读完这份文档你应该能在不读全部 5 万行代码的前提下继续改这个项目。
> 最后更新：2.2.0（versionCode 23），对应交付 APK `Aquarius-AI-2.2.0-mod-arm64.apk`。

---

## 0. 一句话现状

`Aquarius AI` 是 `Aries-AI`（GitHub: ZG0704666/Aries-AI，AGPL-3.0）的定制改版。当前交付版本是 arm64 单架构 Release APK，能与官方原版共存安装，UI 与交互经过 5 轮需求改造，**只在沙箱里编译验证过、没有真机跑过**。

---

## 1. 项目身份

| 项 | 值 |
| --- | --- |
| 应用名 | Aquarius AI（`app_name`） |
| 包名 / applicationId | `com.ai.phoneagent` / **`com.ai.phoneagent.aquarius`** |
| versionCode / versionName | 23 / 2.2.0 |
| minSdk / targetSdk / compileSdk | 30 / 36 / 37 |
| 语言 / UI | Kotlin 2.4.10，Jetpack Compose（主界面）+ 原生 View（悬浮窗） |
| 构建 | AGP 9.2.1，Gradle 9.4.1，KSP，Koin DI |
| 发包架构 | arm64-v8a（`abiFilters`），约 95 MB |

**为什么 applicationId 和包名不同**：包名（namespace）保持 `com.ai.phoneagent` 是为了不动几万个 import；applicationId 加 `.aquarius` 后缀实现与原版共存。

---

## 2. 版本演进（每一版做了什么）

### 阶段 A · 1.x：悬浮窗与任务可观测性（需求 1）

| 需求 | 实现位置 | 关键点 |
| --- | --- | --- |
| 悬浮窗字号更小、信息密度更高、展示 AI 返回内容 | `AutomationOverlay.kt` | 108dp 方卡 → 302×238dp 内容卡；`FeedLine(step, tag, text)` + `ArrayDeque`（上限 300 行 / 单条 6000 字）；`SpannableStringBuilder` + `RelativeSizeSpan` 做「步骤号小一号、标签着色」 |
| 「第几步」改为角落极小字 | 同上 | 先后是 `S1` 角标，2.1.0 起彻底删除（日志行自带编号） |
| 暂停的任务也要进历史、且能看详细日志 | `data/local/AutomationTask*`、`core/automation/AutomationLogBridge.kt` | 新增 Room 库 `aries_tasks.db`（`AutomationTaskEntity`：runId 主键 / goal / status / steps / log / message / startedAt / updatedAt）；日志按 `runId` 经广播精确路由；启动时 `reconcileAutomationTaskConversations()` 补齐崩溃丢失的会话 |
| 默认开启任务模式 | `MainActivity.kt` | `agentModeEnabledState = mutableStateOf(true)` |

### 阶段 B · 2.0.0：共存、悬浮窗交互、元素点击（需求 2）

| 需求 | 实现位置 | 关键点 |
| --- | --- | --- |
| 与原版共存 | `app/build.gradle.kts` | applicationId 加 `.aquarius`；FileProvider authority / Shizuku 授权 / 数据沙盒随之独立 |
| 改名 + ♒ 图标 | `strings.xml`、`res/mipmap-*`、`res/drawable/ic_launcher_*` | 图标用 PIL 脚本生成（超采样 4×，波浪线 + 渐变底） |
| 新建对话 | `ui/drawer/ConversationDrawer.kt` + `MainActivity.startNewChat()` | 抽屉新增 `drawer_new_chat`；清空 `inputTextState` 与当前会话 |
| 四角拖动缩放 + 整体拖动 | `OverlayContainer.dispatchTouchEvent/applyResize` | 四角 hotzone，最小 150×110dp；`WindowManager.LayoutParams` 直接改宽高 |
| 右下角「隐藏/显示」纯文字、可拖动 | `AutomationOverlay.setUserHidden()` + `showHiddenTag()` | 隐藏时 `removeView(主窗)`，另起一个 `TextView` 窗口显示「显示」；本身支持拖动 |
| 左下角「结束任务」 | `OverlayContainer.bottomBar` | 纯文字，`onStopTask` 回调 |
| 完整展示 AI 返回、加粗「当前动作」 | `UiAutomationAgent.describeActionForLog()`、`renderFeed()` | 去掉 `.take(logAnswerTruncateLength)` 截断 |
| 移除语音模型 | `app/build.gradle.kts` + 删 `.so` | 删除 `libsherpa-onnx-jni.so` / `libonnxruntime.so`，126MB → 95MB |
| 元素点击 | `core/utils/ActionUtils.findElementCenter()` + `ActionExecutor.executeTap` | 解析 UI 树 `bounds="[x1,y1][x2,y2]"`；优先级 resourceId > text > contentDesc > className；坐标点击降为兜底 |

### 阶段 C · 2.1.0：手感与开关（需求 3、4）

| 需求 | 实现位置 | 关键点 |
| --- | --- | --- |
| 删四角 L 形标记、热区 40dp | `EdgeFlowView.dispatchDraw`（删）、`cornerAt()`（改） | 先判断是否在底部操作栏 / 输入行内，避免抢文字点击 |
| 振动改系统键盘原生 | `core/settings/Haptics.kt` | `HapticFeedbackConstants.KEYBOARD_TAP` → `VibrationEffect.EFFECT_TICK` 兜底 |
| 全局 aries → aquarius | 121 个文件改名 + 25 个文件重命名 | 保留清单见第 8 节 |
| 用户操作时暂停、停手 1s 后重新截图 | `PhoneAgentAccessibilityService` + `UiAutomationAgent.awaitUserInteractionIdle()` | `TYPE_TOUCH_INTERACTION_START/END`；`markSelfInjectedGesture(900ms)` 区分自己注入的手势；`userInteractionIdleMs = 1000L` |
| 删麦克风权限 | `ui/onboarding/*`、`feature/settings/PermissionSetupSupport.kt` | 权限引导从 3 项降到 2 项（无障碍 + 悬浮窗） |
| 振动总开关 | `Haptics` + `AppUiPrefs.hapticsEnabled` | 统一入口短路，所有调用点自动失效 |
| 结束任务不关窗 | `AutomationViewModel.stopAgent(keepOverlay)` + `AutomationOverlay.markFinished()` | 只停任务、停转流光，窗口保留显示「已结束 · 可下发新任务」 |
| 删步骤角标 | `renderHeader()` | `stepBadge` 视图整个删除 |
| 悬浮窗开关（真关，不是隐藏） | `AutomationViewModel.setOverlayEnabled()` + `AutomationOverlay.visibleFlow` | 用 `StateFlow` 与设置页双向同步；关 = `removeView`；不影响任务 |
| 右上角「＋」内嵌输入 | `OverlayContainer.toggleInputMode()/setInputMode()` | 展开时去掉 `FLAG_NOT_FOCUSABLE` 拉键盘，并把窗口抬到上半屏防遮挡 |
| 图标波浪线平行 + `#3964FE` | PIL 脚本 | 两条同相位正弦，间距 0.196S，线宽 0.056S |
| 边框/颗粒配色 | `EdgeFlowView` | 边框 `#3964FE` @ 12% alpha；颗粒与外光晕实色 |

### 阶段 D · 2.2.0：本版（需求 5）

| 需求 | 实现位置 | 关键点 |
| --- | --- | --- |
| 删右上角「✕」 | `AutomationOverlay.kt` | 移除 `closeAction` 字段、构建块、`headerRow.addView`、字符串 `ui_v2_overlay_close_symbol` |
| 「＋」优先级高于四角热区 | `OverlayContainer.cornerAt()` + `isInsideBar(slop)` | `if (isInsideBar(newTaskAction, x, y, dp(6))) return 0` 放在所有判断之前；`newTaskAction.minWidth/minHeight = 32×28dp` |
| 热区输入不限制范围 | `AppUiPrefs.cornerRadiusPx/setCornerRadiusPx` | 删除 `MIN/MAX_CORNER_RADIUS_PX` 与 `normalizeCornerRadiusPx`；`0` 表示关闭四角拖拽 |
| 颗粒开关（连代码一起停） | `EdgeFlowView.particlesEnabled` / `setParticlesEnabled()` / `onDraw` | 关闭时 `spinner.cancel()` + `onDraw` 早退（只画边框与进度），不是「画完再隐藏」 |
| 全站 `#3F798E` 系改 `#3964FE` | `m3t.xml`、`action_tokens.xml`、`colors.xml`（app + designsystem，含 night）、`AquariusActionStyleTest` | 见第 7 节色板映射 |
| 4 项字号可调 | `AppUiPrefs` + `DrawerSettingsScreen.OverlaySettingsCard` + `OverlayContainer.applyAppearancePrefs()` | 状态行/正文/输入框共用「悬浮窗字号」；`结束任务`/`隐藏`/`＋` 各自独立；改完经 `OverlayTaskBridge.onAppearanceChanged` 立即刷新生效 |
| 推荐任务文案 | `res/values/ui_v2_strings.xml` | 「打开大众点评搜附近好吃的餐馆」→「打开超星学习通并完成题目」 |
| 图标细节收尾 | `app/src/main/res/drawable/ic_launcher_foreground.png`、`feature/settings/.../ic_launcher_foreground.png` | ① 关于页（`AboutScreen.kt`）读的是 **feature/settings 自己的一份**前景图，原先是旧版青绿波浪，2.2.0 改为与新图标同源；② 两处前景图都由解析式距离场重绘（`/tmp/gen_icon_v2.py` 的算法：逐像素求到正弦曲线的法向距离），消掉了旧版折线描边在笔画内部留下的细密白色接缝 |

---

## 3. 架构

### 3.1 模块

```
:app                  唯一 application 模块
  ├─ core/agent         Agent 数据模型（AutomationTask、AgentAction…）
  ├─ core/automation    AutomationLogBridge（runId 广播）
  ├─ core/cache         ScreenshotManager（截图 + 节流 + 复用）
  ├─ core/config        AgentConfiguration（超时、步数、idle 时长）
  ├─ core/executor      ActionExecutor（tap/swipe/input/back…）
  ├─ core/input         AppClipboardTransaction（跨 App 粘贴）
  ├─ core/overlay       OverlayTaskBridge（悬浮窗 ↔ ViewModel 的单例桥）
  ├─ core/parser        ActionParser（解析模型输出的 do(...)）
  ├─ core/platform      DeviceController（屏幕参数、旋转）
  ├─ core/security      PathGuard / InetAddressGuard / ApiConfigSignature
  ├─ core/settings      AppUiPrefs（SharedPreferences）+ Haptics  ★本改版新增
  ├─ core/templates     PromptTemplates（提示词与动作语法约束）
  ├─ core/tools         AppPackageManager、扩展工具（文件/网络/系统）
  ├─ core/utils         ActionUtils（UI 树解析/元素定位）、DisplayUtils…
  ├─ data               Room（AquariusDatabase、AutomationTaskDatabase）、DataStore、KeyStore
  ├─ di                 Koin modules（AppModule / DataModule / NetworkModule …）
  ├─ net                AquariusApiClient（OpenAI 兼容 + 官方网关）、SSE、OIDC
  ├─ ui                 Compose 界面：home / drawer / messages / inputbar / settings / onboarding / history
  ├─ vdiso              Shizuku 虚拟屏：VirtualDisplayController、VdGlFrameDispatcher、ImeFocusDeadlockController
  └─ viewmodel          AutomationViewModel（task 调度）、SettingsViewModel…
:core:common           JSON / 版本工具
:core:designsystem     Material3 主题、m3t_* 令牌、玻璃组件、AquariusActionStyle
:core:prompt           提示词仓库
:core:shizuku          Shizuku 桥
:feature:settings      设置页 Compose UI（AboutScreen 等）
:feature:updates       GitHub Release 更新检查
```

### 3.2 一次任务的完整生命周期

```
用户输入 goal
  → AutomationViewModel.startAgent()
       · 新建 conversation（Room）+ 新建 task 记录（aries_tasks.db）
       · automationOverlay.show(...)  ← 若悬浮窗开关是关的，这里也会强制显示
  → UiAutomationAgent.run()
       while (step < maxSteps && !cancelled):
           1. awaitUserInteractionIdle(service)   // 用户正在操作就等
           2. screenshot = ScreenshotManager.capture()
           3. prompt = PromptTemplates.build(task, history, uiDump)
           4. stream = api.chat(prompt)           // 流式，逐段推到悬浮窗
           5. action = ActionParser.parse(stream)
           6. ActionExecutor.execute(action)
                Tap: findElementCenter(uiDump, element_text) ?: 坐标
           7. 日志 → AutomationLogBridge.publish(runId, line) → 悬浮窗 + 主界面
  → 结束：complete() / markFinished() / 取消 → saveRunResult() 落库
```

### 3.3 悬浮窗的三层结构

```
AutomationOverlay（对象，单例由 AutomationViewModel 持有）
 ├─ window / params           WindowManager + TYPE_APPLICATION_OVERLAY
 ├─ container: OverlayContainer
 │    ├─ headerRow: [header(状态行)] [newTaskAction(＋)]
 │    ├─ divider
 │    ├─ feedScroll > feedView（Spannable 日志流）
 │    ├─ inputRow（＋展开时才 VISIBLE）
 │    └─ bottomBar: [stopAction(结束任务)] … [hideAction(隐藏/显示)]
 ├─ ring: EdgeFlowView（叠在底层，画边框 + 进度 + 颗粒）
 ├─ hiddenTagView（用户点“隐藏”后的独立小窗，显示“显示”）
 └─ _visible: StateFlow<Boolean>（设置页读这个）
```

**触摸优先级**（`dispatchTouchEvent`，改这里前务必读一遍）：

```
「＋」 > 底部操作栏 > 输入行 > 四角热区 > super（拖动/滚动）
```

### 3.4 数据存储

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| Room | `aries.db` | 会话/消息，`AquariusDatabase` |
| Room | `aries_tasks.db` | 任务记录，`AutomationTaskDatabase`，`MAX_RECORDS = 100` |
| DataStore | `app_preferences` | 模型、主题、开关；键名前缀 `aries_` |
| DataStore | `telemetry_prefs` | 安装 ID、上次心跳 |
| SharedPreferences | `aquarius_ui_prefs` | 振动/悬浮窗/热区/颗粒/字号，同步读取 |

### 3.5 主题与颜色

- 颜色令牌集中在 `core/designsystem/src/main/res/values{,-night}/m3t.xml` 与 `action_tokens.xml`，Compose 侧由 `AquariusMaterialTheme` 组装 `ColorScheme`。
- **默认强调色**（`ThemeColorStyle.DEFAULT`）走 `m3t_primary`；另有 OCEAN / FOREST / SUNSET / ROSE 四套可选主题（`action_tokens.xml` 里的 `aquarius_*_primary`），2.2.0 **没有动这四套**，只改了默认色板与品牌玻璃色。
- 2.2.0 色板映射（原值 → 新值）：

| 原 | 新 | 用途 |
| --- | --- | --- |
| `#006C84` | `#3964FE` | 浅色 primary / 动作渐变起点 |
| `#BCEBF4` | `#DDE3FF` | primaryContainer |
| `#002029` | `#001043` | onPrimaryContainer |
| `#0E8FB5` | `#3964FE` | surfaceTint / 语音胶囊 |
| `#38B6E3` | `#3964FE` | 品牌玻璃主色、按钮、开关、用户气泡、focus 描边 |
| `#0AA3C2` | `#2A4FD8` | 品牌深一档 |
| `#9FE6F2` | `#B9C7FF` | 品牌浅一档 |
| `#515A71` | `#6E56AE` | tertiary（新 primary 与 secondary 的中点） |
| `#8CDDEB`（night） | `#A8BBFF` | 深色 primary |
| `#004E60`（night） | `#1E2F8F` | 深色 primaryContainer |
| `#6FDCEF`（night） | `#8FA5FF` | 深色 surfaceTint |

改颜色时请同步改 `core/designsystem/src/test/.../AquariusActionStyleTest.kt` 的断言，否则 `:core:designsystem:test` 会红。

---

## 4. 构建与交付

### 4.1 沙箱里的实际环境（本机路径，换机器要重建）

| 项 | 路径 |
| --- | --- |
| JDK 17 | `/opt/jdk-17.0.2` |
| Android SDK 37 | `/opt/android-sdk`（build-tools 37.0.0） |
| 签名文件 | `/workspace/aries-release.jks`，alias `aries`，口令 `aries2026` |

### 4.2 命令

```bash
cd /workspace/Aries-AI
export JAVA_HOME=/opt/jdk-17.0.2
export PATH=$JAVA_HOME/bin:$PATH

./gradlew :app:assembleRelease -Dorg.gradle.java.home=/opt/jdk-17.0.2 --offline

BT=/opt/android-sdk/build-tools/37.0.0
$BT/zipalign -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk out.apk
$BT/apksigner sign --ks /workspace/aries-release.jks --ks-key-alias aries \
  --ks-pass pass:aries2026 --key-pass pass:aries2026 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true out.apk
$BT/apksigner verify out.apk && $BT/zipalign -c -P 16 4 out.apk
```

### 4.3 踩过的坑（省你几个小时）

| 现象 | 原因 / 处理 |
| --- | --- |
| `git clone` 超时、zip 不完整 | 用 `git clone --depth 1`，必要时重试；不要用 codeload zip |
| Gradle 发行包下载卡住 | 腾讯镜像 `mirrors.cloud.tencent.com/gradle/gradle-9.4.1-bin.zip`，手动放进 `~/.gradle/wrapper/dists` |
| 工具链反复下载 JDK 25 卡死 | 临时 `mv gradle/gradle-daemon-jvm.properties gradle/gradle-daemon-jvm.properties.bak`，用本机 JDK 17；**交付前记得还原** |
| `~/.gradle/init.gradle` 语法错 | 直接移除/改名 |
| 首次安装报「应用未安装」 | 多半是签名冲突（官方原版不同私钥），`adb uninstall com.ai.phoneagent` 后再装 |
| 16KB 对齐失败 | 必须 `zipalign -P 16`（`-p` 是 4KB，不够） |
| `removeView` 在触摸分发中被拒 | 用 `mainHandler.post {}` 延后一帧 |
| 悬浮窗底部文字点不到 | `cornerAt()` 先判 `isInsideBar(bottomBar, ...)` |
| 悬浮窗「＋」点不到 | 就是 2.2.0 修的那个问题：热区先吞掉了 DOWN 事件 |
| 输入法不弹 | `TYPE_APPLICATION_OVERLAY` 需要临时去掉 `FLAG_NOT_FOCUSABLE`，且要 `postDelayed(180)` 后再 `requestFocus()` |
| Compose 里 `mutableIntStateOf` 不可用 | 用 `mutableStateOf(0)` 兜底 |

---

## 5. 代码地图（要改功能先看这里）

| 想改什么 | 去哪个文件 |
| --- | --- |
| 悬浮窗外观/交互 | `app/src/main/java/com/ai/phoneagent/AutomationOverlay.kt`（约 1900 行，`OverlayContainer` 在第 800 行左右，`EdgeFlowView` 在最后） |
| 悬浮窗开关、任务启动/停止 | `viewmodel/AutomationViewModel.kt`（`startAgent` / `stopAgent(keepOverlay)` / `setOverlayEnabled` / `submitTaskFromOverlay`） |
| 悬浮窗与 ViewModel 通信 | `core/overlay/OverlayTaskBridge.kt`（`onSubmitTask` / `onStopTask` / `onAppearanceChanged`） |
| 界面偏好（振动/热区/颗粒/字号） | `core/settings/AppUiPrefs.kt` |
| 触感 | `core/settings/Haptics.kt`（唯一入口，`tap()`） |
| 设置页 | `ui/settings/DrawerSettingsScreen.kt`（含 `OverlaySettingsCard`、`NumericSettingRow`） |
| Agent 主循环 | `UiAutomationAgent.kt`（`awaitUserInteractionIdle`、`describeActionForLog`） |
| 动作执行 | `core/executor/ActionExecutor.kt` |
| 元素定位 | `core/utils/ActionUtils.kt` → `findElementCenter()` |
| 提示词 | `core/templates/PromptTemplates.kt` |
| 无障碍 / 触摸检测 | `PhoneAgentAccessibilityService.kt`（`isUserTouching` / `isUserInteracting` / `markSelfInjectedGesture`） |
| 主题色 | `core/designsystem/src/main/res/values{,-night}/m3t.xml`、`action_tokens.xml` |
| 图标 | `res/drawable/ic_launcher_foreground.png`（432）、`res/mipmap-*/ic_launcher*.png` |
| 应用名/文案 | `res/values/strings.xml`、`res/values/ui_v2_strings.xml` |

---

## 6. 功能清单（对外能力）

1. **对话**：流式回复、Markdown 渲染、代码高亮、附件（图片/文件）、会话抽屉、新建对话、长按重命名/删除。
2. **任务模式**：默认开启；开启后输入框内容作为「目标」交给 Agent，而不是普通聊天。
3. **Agent 执行**：截图 → 提示词 → 模型 → 动作解析 → 执行（Tap/Swipe/Input/Back/Home/Wait/Open App…）→ 循环；步数上限、超时、失败重试。
4. **元素级点击**：模型给 `element_text`，本地算坐标。
5. **用户接管保护**：检测到手指触摸 → 放弃当前步 → 等停手 1s → 重新截图。
6. **悬浮窗**：
   - 常驻日志流（步骤号 + 标签 + 全文）
   - 四角拖拽缩放、整体拖动
   - 「＋」新建任务（内嵌输入框）
   - 「结束任务」（保留窗口）、「隐藏/显示」（可拖动的小标签）
   - 设置里可开关窗口、调热区、调字号、开关颗粒
7. **历史**：每个任务（含暂停/失败）自动落成会话，可展开「查看操作日志（N 条）」。
8. **设置**：模型与 API（OpenAI 兼容 / 通行证 / 本地 MNN）、外观（主题/强调色/密度）、悬浮窗设置、触感开关、无障碍与权限、关于与帮助、开源许可。
9. **虚拟屏**（可选，需 Shizuku）：独立 Display + 定向输入 + 预览浮层。
10. **更新检查**：GitHub Release。

---

## 7. 交互细节备忘

- **结束任务的两条路径**：底部「结束任务」→ `OverlayTaskBridge.onStopTask` → `AutomationViewModel.handleStopFromOverlay()` → `stopAgent(keepOverlay = true)`。
- **隐藏/显示**：主窗被 `removeView`，另起 `hiddenTagView`；再次点击重加主窗并 `startSpinner()`。`_visible` 在隐藏时置 false。
- **设置页悬浮窗开关**：读 `automationOverlay.visibleFlow`；打开时若没有任务则 `showIdle(taskActive=false)`，有任务则 `showIdle(taskActive=true)`。
- **字号即时生效**：设置写入 `AppUiPrefs` → `OverlayTaskBridge.onAppearanceChanged` → `AutomationOverlay.refreshAppearance()` → `OverlayContainer.applyAppearancePrefs()`（重设 7 个 `setTextSize` + 颗粒状态 + 重绘日志）。
- **颗粒开关**：`EdgeFlowView.setParticlesEnabled(false)` 会 `cancel()` animator 并把 `phase` 归零；`onDraw` 走静态分支。`start()` 在关闭状态下直接 return，所以不会有「看不见但还在跑」的动画。

---

## 8. 不能改的东西（改了就破坏兼容）

| 项 | 位置 | 后果 |
| --- | --- | --- |
| `aries.db` / `aries_tasks.db` | `AquariusDatabase.kt:33`、`AutomationTaskDatabase.kt:34` | 老用户历史全丢 |
| DataStore 键 `use_aries_api` / `aries_api_key` / `aries_selected_model` / `aries_logged_in_user` / `aries_api_section_unlocked` | `AppPreferencesRepository.kt:120-124` | 用户模型配置被清空 |
| KeyStore 别名 `aries_ai_preference_secrets` | `AndroidKeyStoreSecretStore.kt:77` | 已加密 Key 无法解密 |
| provider 标识 `"aries"` | `MainActivity.kt:2997`、`SettingsViewModel.kt:604` | 参与 `ApiConfigSignature`，改了已存 Key 失效 |
| `api.aries.org.cn` / `sso.aries.org.cn` | `AquariusApiClient.kt:586,589` | 官方登录与 API 全废 |
| `LICENSE` / `NOTICE` 原文 | 根目录 | AGPL 违约 |

---

## 9. 待改进（按价值排序）

1. **真机验证**：虚拟屏链路、悬浮窗在各类 ROM 上的触摸事件、IME 聚焦，全都只做过代码级推理。
2. **悬浮窗与 Compose 主题联动**：目前 `OverlayContainer` 里混用了 `m3t_*` 资源色和硬编码 `#3964FE`，如果以后要支持 OCEAN 等其他强调色，应该改成从主题取色。
3. **热区上限提示**：不设范围是需求，但可以在设置里加一句「建议不超过窗口短边的一半」，减少误配。
4. **日志容量**：`feedLines` 上限 300 行 / 单条 6000 字，长任务会滚动丢历史；如需完整日志应读 `aries_tasks.db`。
5. **`overlayEnabled` 与真实状态的一致性**：`AppUiPrefs.overlayEnabled` 与 `visibleFlow` 是两套状态，目前设置页只看后者；如果要「重启后恢复上次开关」，需要启动时读前者。
6. **遥测**：`AquariusApiClient` 的心跳端点已被改成不存在的域名（静默失败），如果要做统计需重新配置并告知用户。

---

## 10. 验证过什么、没验证什么

**已验证**

- `:app:compileReleaseKotlin` 通过（JDK 17，离线）
- `:app:assembleRelease` 出包，`apksigner verify` + `zipalign -c -P 16` 通过
- 源码包从零解压后能独立编译
- APK 内 `aapt2 dump strings` 能查到新文案；图标资源已替换

**未验证（环境无 `/dev/kvm`，跑不了模拟器）**

- 悬浮窗实际触摸优先级（只能保证代码路径正确）
- 「＋」唤起输入法在各 ROM 的表现
- 颗粒开关的实际功耗收益
- 颜色改动后的整体观感（只做了色值与对比度推算）

---

## 11. 给下一个 AI 的建议

- 改悬浮窗之前先把 `AutomationOverlay.kt` 的 `dispatchTouchEvent` / `cornerAt` / `isInsideBar` 三个函数读完，触摸优先级全在这三处。
- 改颜色只动 `m3t.xml` / `action_tokens.xml` / `colors.xml`（app + core 两份 + night 两份），别在 Kotlin 里硬编码。
- 加设置项优先用 `AppUiPrefs` + `OverlaySettingsCard` + `NumericSettingRow` / `SettingsSwitchRow`，这三件套已经把「即时生效 + 无范围限制 + 恢复默认」串好了。
- 每次交付前跑一遍 `:app:assembleRelease` 并检查 `zipalign -c -P 16`，Android 15+ 设备对 16KB 页对齐是硬要求。
- 用户习惯：一次给一批需求，期望「改完直接给能装的 APK + 源码包」。交付时给 SHA-256、包名、版本号、覆盖安装说明。
