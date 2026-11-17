# AI 交接备忘（Handover for the next AI）

> 这份文件写给**下一个接手这个仓库的 AI**，不是给最终用户的。
> 目标：让你在 5 分钟内知道「这台机器上怎么把它编译出来」以及「哪些坑已经踩过、别再踩」。
> 用户可见的版本变更请写 [CHANGELOG.md](./CHANGELOG.md)；架构细节见 [HANDOVER.md](./HANDOVER.md)；硬约束见 [AGENTS.md](./AGENTS.md)。

---

## 0. 一句话现状

- 包名/版本：`com.ai.phoneagent.aquarius` / `versionName 2.4.0` / `versionCode 25`（`app/build.gradle.kts`）。
- 编译工具链**不在默认位置**：JDK 17 与 Android SDK 都装在 `D:\AndroidDevelopment`，Gradle 缓存也在那里。
- 本机网络对部分域名（`github.com`、`repo.maven.apache.org` 的 IPv4）**不可用**，见第 2 节。

---

## 1. 本机工具链路径（记住这几个就够了）

| 用途 | 路径 |
| --- | --- |
| JDK 17（构建必须用它，机器上另有 Oracle JDK 25 不要用） | `D:\AndroidDevelopment\jdk17` |
| Android SDK | `D:\AndroidDevelopment\sdk` |
| ├ cmdline-tools | `D:\AndroidDevelopment\sdk\cmdline-tools\latest\bin\sdkmanager.bat` |
| ├ platform-tools（adb / zipalign 不在里面） | `D:\AndroidDevelopment\sdk\platform-tools` |
| ├ 平台 | `D:\AndroidDevelopment\sdk\platforms\android-37.0` |
| └ build-tools（zipalign / apksigner） | `D:\AndroidDevelopment\sdk\build-tools\37.0.0` |
| Gradle 依赖缓存 + wrapper 发行版 | `D:\AndroidDevelopment\gradle-home`（用 `GRADLE_USER_HOME` 指过去） |
| 构建日志 | `D:\AndroidDevelopment\build-*.log` |
| 镜像 init 脚本（见第 2 节） | `tools/gradle-init-mirrors.gradle` |
| 环境引导脚本（可重跑） | `tools/bootstrap_android_env.py`、`tools/bootstrap_sdk.py`、`tools/bootstrap_gradle.py` |
| 签名 keystore | `signing/aquarius-release.jks`（别名/口令见 `signing/README.txt`） |
| rikkahub 参考源码（导航/返回模式照抄自它） | `D:\AndroidDevelopment\refs\rikkahub` |
| Python（下载用，见第 2 节） | `D:\MiniPrograms\Python3117\python.exe` |
| git | `D:\MiniPrograms\Git\cmd\git.exe` |

`local.properties` 已指向 `D:\AndroidDevelopment\sdk`；`gradle.properties` 里的
`android.overridePathCheck=true` **不能删**（仓库路径含括号/中文）。

### 编译 + 签名（实测可用）

```powershell
$env:JAVA_HOME="D:\AndroidDevelopment\jdk17"
$env:GRADLE_USER_HOME="D:\AndroidDevelopment\gradle-home"
$env:JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Dfile.encoding=UTF-8"
.\gradlew.bat -I tools\gradle-init-mirrors.gradle :app:assembleRelease --console=plain
```

产物 `app\build\outputs\apk\release\app-release-unsigned.apk`，然后按 `signing/README.txt`：

```powershell
$bt = "D:\AndroidDevelopment\sdk\build-tools\37.0.0"
& "$bt\zipalign.exe" -P 16 -f 4 app\build\outputs\apk\release\app-release-unsigned.apk aligned.apk
& "$bt\apksigner.bat" sign --ks signing\aquarius-release.jks --ks-key-alias aquarius `
    --ks-pass pass:aquarius2026 --key-pass pass:aquarius2026 `
    --min-sdk-version 30 --v1-signing-enabled true --v2-signing-enabled true `
    --v3-signing-enabled true --out Aquarius-AI-2.4.0-arm64.apk aligned.apk
& "$bt\apksigner.bat" verify --verbose Aquarius-AI-2.4.0-arm64.apk
& "$bt\zipalign.exe" -c -P 16 4 Aquarius-AI-2.4.0-arm64.apk
```

---

## 2. 网络坑（踩得最惨的部分）

1. **PowerShell 的 HTTPS 全都失败**：`Invoke-WebRequest` / `curl.exe` 都会报
   `schannel: AcquireCredentialsHandle failed: SEC_E_NO_CREDENTIALS`。
   不是断网（TCP 通、git 能用），是本机 schannel 拿不到 TLS 客户端凭据。
   → **下载一律用 Python**（它自带 OpenSSL）：
   `& "D:\MiniPrograms\Python3117\python.exe" -c "import urllib.request; urllib.request.urlopen(url)"`。
2. **`github.com` 的 DNS 解析失败**（`Resolve-DnsName github.com` 直接报错）。
   Gradle wrapper 正常下载会被 `services.gradle.org → github.com` 的重定向打死：
   `UnknownHostException: github.com`。
   → 把 `gradle-9.4.1-bin.zip` 从**腾讯云镜像**预先放进
   `D:\AndroidDevelopment\gradle-home\wrapper\dists\gradle-9.4.1-bin\<hash>\`，
   并且必须校验 `gradle-wrapper.properties` 里的 `distributionSha256Sum`
   （`tools/bootstrap_gradle.py` 已经做了这件事）。
   可用的 Gradle 镜像：`https://mirrors.cloud.tencent.com/gradle/`（阿里云的 gradle 目录没有 9.4.1）。
3. **Maven Central 只返回 IPv6**，JVM 连不上；Google Maven 的 IPv4 有时也不稳。
   → `tools/gradle-init-mirrors.gradle` 用**阿里云镜像**（`maven.aliyun.com/repository/{google,public,gradle-plugin}`）
   插到所有仓库最前面，项目 `settings.gradle.kts` 不用改（Maven 仓库只能在 settings 里声明，
   模块级 `repositories {}` 会触发 `FAIL_ON_PROJECT_REPOS`）。
   同时设置 `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`。
4. `api.adoptium.net` 会返回 **403**；JDK 17 用 **Amazon Corretto** 的链接下载成功
   （`https://corretto.aws/downloads/latest/amazon-corretto-17-x64-windows-jdk.zip`）。
5. `dl.google.com`（cmdline-tools、SDK 包）IPv4 可用；SDK 包版本号注意：
   **没有 `platforms;android-37`**，新命名是 `platforms;android-37.0` / `37.1` / `37.2`。

---

## 3. 文件操作坑（会直接毁掉源码）

1. **不要用 PowerShell 的 `Get-Content` / `Set-Content` / `-replace` 改源码文件**。
   ps 5.1 默认按 ANSI/GBK 读 UTF-8，写回时中文注释会变成 `鈥滃` 这类乱码，
   而且还会吃掉换行（曾把 `DrawerSettingsScreen.kt` 整个写坏，只能凭上下文重写整文件）。
   → 改代码只用 read / write / edit 工具；查代码用 `Select-String`、`grep` 工具。
2. 工作区里 **`Remove-Item` 删除文件、`Set-Content` 新建文件会被沙箱拒绝**
   （`[sandbox: file access denied under workspace-write mode]`）。
   → 删除/新建走 edit/write 工具；必须用 shell 时按提示加一次
   `sandbox_permissions: danger-full-access` 重试同一条命令。
3. `D:\AndroidDevelopment` 在工作区外，所有写入（下载、Gradle 缓存、日志）都要
   一次性提权 `danger-full-access`，否则静默失败。
4. `UI design/*.pen` 是加密设计稿，别用文本工具读。

---

## 4. 导航 / 返回（最近一次大改，务必读懂再动）

用户要求「整体格式与返回模式照 RikkaHub」，实现如下：

- **顶部三个主标签**（历史 / 主页 / 设置）仍是 `HomeScreen` 里的 `HorizontalPager` 平移。
  每个 pager 页都包了 `clipToBounds()`：**任何页内部的位移都不能溢出到相邻页**
  （历史 bug：设置二级页里的自绘容器做视差位移时，会在主页右边留下一条上级界面残影）。
- **所有二级页都是 NavHost 的独立目的地**：`Routes.SettingsAppearance / Language / Overlay /
  Storage / Automation / ModelApi / Membership / About`（见 `navigation/Routes.kt`）。
  设置页不再自己在 pager 页里叠一层“覆盖层 + PredictiveBackHandler”。
- **转场规格照抄 RikkaHub 的 `NavDisplay`**（`navigation/AquariusNavGraph.kt` 末尾四个常量）：
  - 前进 `slideInHorizontally { it }`，旧页 `slideOutHorizontally { -it/2 } + scaleOut(0.7f) + fadeOut()`；
  - 返回/预测性返回 `slideInHorizontally { -it/2 } + scaleIn(0.7f) + fadeIn()` + 当前页 `slideOutHorizontally { it }`；
  - 没有阴影、没有圆角 —— 用户明确说过“阴影 + 直角太生硬，删掉”。
  - 参考源码：`D:\AndroidDevelopment\refs\rikkahub\app\src\main\java\me\rerere\rikkahub\RouteActivity.kt`（`NavDisplay(` 附近）。
- **不要**再往 NavHost 外面包 `PredictiveBackHandler`/“返回容器”来“手动跟手”。
  navigation-compose 2.8 起 NavHost 自己注册 `OnBackInvokedCallback` 并用 seekable 转场跟手；
  包一层会把事件吞掉，表现就是“滑到一半看不到要返回的界面”（这个 bug 我们已经修过一次）。
- 设置相关界面共用一个 **Activity 级** `SettingsViewModel`（在 `MainActivity.setContent` 里创建，
  传给 `AquariusNavGraph` 和 `HomeRoute`）。不要在目的地里再 `koinViewModel()` 造第二个实例，
  否则 API Key 输入、连接状态会分叉。
- `SettingsViewModel` 里原来的 `SettingsPage/currentPage/navigateTo/openHomePage/openModelApiPage`
  已删除（冗余的“VM 内页码导航”），页面跳转一律走 `navController.navigate(Routes.…)`。

### insets 坑

`HomeScreen` 的 pager 第 2 页已经用 `padding(top = statusBar)` 让出了一次状态栏；
所以嵌进这个页面的内容要 `consumeWindowInsets(WindowInsets.statusBars)`（见 `SettingsRoute` 的 `embedded` 分支），
否则二级页 `TopAppBar` 会再加一遍状态栏高度，表现为“第一行没有到顶”。

### 悬浮窗

- `AutomationOverlay.kt` 是原生 View，不是 Compose；触摸优先级见 `AGENTS.md`。
- 四角缩散热区（`cornerAt()`）按下时会把边框描边切成实色品牌蓝（`EdgeFlowView.setCornerHighlight(true)`），
  松手恢复原来的浅蓝描边；这套高亮与“输入校验高亮”（绿色）是两条独立状态，别互相覆盖。

---

## 5. 其它已经踩过的坑

1. **App 内语言切换后，ViewModel 里的中文文案不变**：`AppCompatDelegate` 的 per-app Locale
   只注入 Activity Context，ViewModel 拿到的 Application Context 还是系统语言。
   `SettingsViewModel.stringRes()` 里已经手工套 `createConfigurationContext`；
   新增 ViewModel 文案时要么同样处理，要么把文案留给 Compose 的 `stringResource`。
   缓存在 VM 里的状态文案切换语言后需要 `refreshLocalizedState()` 重新解析。
2. **MissingTranslation 会挂 lint**：`values/` 与 `values-en/` 的键集合必须一致，
   新增字符串两处都要加；`lintDebug` 是 `warningsAsErrors` + baseline，新增告警会直接失败。
3. **不要为了消警告去重刷 `lint-baseline.xml`**（AGENTS.md 硬约束）。
4. 截图存档自 2.4.x 起支持三种位置：应用内部 / 相册 / **用户自选文件夹（SAF 目录树）**，
   见 `AppUiPrefs.SCREENSHOT_STORAGE_*` 与 `ScreenshotArchive`；SAF 授权丢失会自动回退到应用内部。
5. `testDebugUnitTest` 在 `core:designsystem` 偶尔卡住，改用定向执行：
   `:app:testDebugUnitTest --tests "com.ai.phoneagent.di.*"`。

---

## 6. 交付清单（本仓库惯例）

按 `AGENTS.md`：APK + 源码 zip + SHA-256 + 包名 + 版本号 + 覆盖安装说明。
APK 命名沿用 `Aquarius-AI-<versionName>-arm64.apk`。
