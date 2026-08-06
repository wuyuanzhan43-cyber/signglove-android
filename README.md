# 手语手套 · Android 手机版

手机直连手套蓝牙（**JDY-18 BLE 4.2**，FFE0/FFE1），手势→DeepSeek 组句→语音播报；
生命体征异常时**持续响铃+震动+全屏告警（手动才停）**，并把**求助消息+手机 GPS 定位**推送给家人微信。

**当前版本 v1.5**（多候选语义选择版）· 完整变更记录见 [CHANGELOG.md](CHANGELOG.md)

> 仅 Android：原生 Kotlin 编写，暂无 iOS 版。JDY-18 为 BLE 4.2，iPhone 理论上可连，但需另行开发 iOS App。

## 功能
- 蓝牙 BLE 连接 JDY-18（FFE0 服务 / FFE1 notify），断线 3 秒自动重连，接收 `GESTURE:<name>` 手势
- 手势→词映射→停顿组句（手机直调 DeepSeek，可在设置页填 key）
- **多候选语义选择**（可开关）：停顿后 DeepSeek 一次生成多个不同语义的候选句，屏幕点选或继续打**数字手势 1~N** 选定，选中才播报
- 中文 TTS 语音播报
- 生命体征卡片（现为模拟数据，真实 MAX30102/GSR 接入后替换）+ 异常自动检测
- **SOS**：异常→8 秒可取消倒计时→持续响铃/震动/全屏红屏（手动停）+ GPS 定位 + 微信推送（Server酱/企业微信）
- 设置页：Server酱 SendKey / 企业微信 Webhook / DeepSeek Key / 称呼 / 停顿秒数（存手机本地）

## 怎么编译出 APK（本机无需 Android 环境）

用 **GitHub Actions 云编译**：
1. 新建一个 GitHub 仓库，把本目录（`jdy_gesture_android/`）作为仓库根推上去。
2. 推送后 `.github/workflows/android.yml` 自动运行，编译 debug APK。
3. 打开仓库 **Actions** → 最新一次运行 → **Artifacts** → 下载 `SignGlove-debug-apk` → 解压得 `app-debug.apk`。
4. 传到 Android 手机安装（需在系统里允许「安装未知来源应用」）。

> 也可用 Android Studio 直接打开本目录编译运行。

## 手机上使用
1. 安装后首次打开，授予**蓝牙、定位、通知**权限。
2. 手机系统设置 → 蓝牙 → 与 **JDY-18 配对**（PIN **000000**）。
3. 回 App → 下拉选中 JDY-18 → 「🔌 连接」。
4. 「⚙️ 设置」填入 Server酱 SendKey 或企业微信 Webhook，保存。
5. 测试报警：「⚠️ 模拟生命体征异常」→ 8 秒倒计时 → 手机持续响铃+震动+全屏告警 → 点「停止」才停 → 家人微信收到含定位链接的求助。

## 国产 ROM 注意
后台持续响铃/定位可能被省电策略限制，请在系统里给本 App 开：**自启动、后台运行、通知、锁屏显示、定位"始终允许"**白名单。

## 技术
原生 Kotlin · minSdk 26(Android 8) · BLE GATT(JDY-18) · OkHttp · FusedLocation · 前台服务持续告警 · 定位用高德 uri 链接（免 key，微信可点开）。
