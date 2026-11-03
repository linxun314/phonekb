# PhoneKB

**手机当键盘用 —— 把 Android 手机变成蓝牙键盘 / 触控板 / 媒体遥控器 / 游戏手柄，平板和电脑零安装、免 root。**

纯蓝牙 HID：被控端不装任何 App，手机端一个 APK 就是全部。

[![Release](https://img.shields.io/github/v/release/linxun314/phonekb?label=release&color=4C8DFF)](https://github.com/linxun314/phonekb/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/linxun314/phonekb/total?color=3DDC84)](https://github.com/linxun314/phonekb/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/linxun314/phonekb/build.yml?branch=main&label=build)](https://github.com/linxun314/phonekb/actions)
[![License](https://img.shields.io/github/license/linxun314/phonekb?color=FFB020)](LICENSE)
[![Android](https://img.shields.io/badge/Android-9%2B%20%C2%B7%20API%2028-3DDC84?logo=android&logoColor=white)](#兼容性)

> Turn your Android phone into a Bluetooth HID keyboard / trackpad / media remote / gamepad — the host device needs **zero installation and no root**.

实测组合：**Redmi K40（Android 13 / HyperOS）→ iQOO Pad2（Android 14）**。平板只做一次蓝牙配对，之后打开手机 App 就能用。

**[⬇️ 下载最新版](https://github.com/linxun314/phonekb/releases/latest)** · [快速开始](#快速开始) · [功能](#功能) · [截图](#截图) · [常见问题](#常见问题) · [文档](#文档)

---

## 当前版本 v0.36.1

这一版新增 / 打磨的内容（README 只介绍当前版本，旧版本说明不保留）：

- **手柄页「触控板」浮层的 ↑ ↓ 修好了**：这两颗箭头之前只画了没做触摸判定，点在上一律变成「移动指针」，所以按了没反应；现在上半上滚、下半下滚，按住连发，按下的半边会高亮
- **移除 WiFi 伴侣**：伴侣模式需要每次扫码配对、平板还要常开无障碍服务，用起来比蓝牙通道麻烦得多；现在整个 App 回到单一通道 —— **纯蓝牙 HID，被控端零安装、免配置**
- 顶栏不再有「伴侣」入口，底栏版本号同步更新；因为去掉了整套 WiFi 通道、二维码、语音识别和无障碍注入代码，**安装包从 713 KB 降到 481 KB**
- 中文输入改回最稳的方案：用平板自己的输入法（蓝牙键盘发的是键码，任何输入法的拼音都能用）
- 上一版（v0.35.0）的伴侣语音输入随之移除；再上一版（v0.34.0）的媒体页重做、手柄 LT/L1/RT 修复等全部保留
- 版本：手机端 **0.36.1**（versionCode 58）

## 30 秒了解

| 你想要的 | PhoneKB 怎么做 |
|---|---|
| 用手机给平板打字、按快捷键 | 手机注册成**标准蓝牙键盘**，平板把它当普通蓝牙键盘用，**平板不装 App** |
| 用手机当鼠标 | 触控板 + 物理左/中/右键，支持点击、拖动、双指滚动、右键菜单，速度可调（含加速度） |
| 躺床上刷视频、拖进度、切歌、调音量、调亮度、截屏 | 媒体页：上一段 / 播放暂停 / 下一段、−10s / ＋10s（可按住连发）、音量 ± / 静音 / 亮度 −/＋ / 截屏 / 多任务 / 返回主页菜单 |
| 手机当手柄玩模拟器 | 手柄页：浮键 / Xbox 式皮肤 / 自定义三种布局，移动盘（十字键 / 圆盘 / 模拟摇杆）+ ABXY + L1/R1/LT/RT + SELECT/START + 双模拟摇杆，大小可分别无级调节 |

| 键盘 | 触控板 |
|---|---|
| ![键盘](docs/screenshots/01-keyboard-landscape-computer.png) | ![触控板](docs/screenshots/03-touchpad-landscape.png) |

## 功能

- **蓝牙 HID，一装一配**：手机端一个 APK，被控端只需蓝牙配对，不装软件、不 root
- **键盘**：手机布局 / 电脑布局两套，含符号页、Ctrl+C/V/X/A、Alt+Tab、方向键
- **触控板**：单指移动 · 点击左键 · 双指滚动 · 双指点击右键 · 三指中键；底部左/中/右键可长按拖动；右侧滚动列；一键弹出浮层数字键盘 / 浮层键盘 / 浮层触控板
- **指针灵敏度**：0.4×–2.5× 无极滑块 + 慢/标准/快预设 + 加速度开关 + 滚轮 1–6 行，四处触控面统一生效
- **媒体键**：5 键媒体条（**⏪ 快退 / ⏮ / ⏯ / ⏭ / ⏩ 快进**，长按 ⏪、⏩ 每 100 ms 连发）+ 4 药丸（**亮度 −/＋**、**−10s / ＋10s**，±10s 可切换发「上一段/下一段」或 ←/→）+ 音量 / 静音 / CC、返回 / 主页 / 菜单 / 电源
- **触控延迟优化**：一帧历史采样全累加、合并窗口 2 ms、极速模式（移动中不重绘不震动）、点击方式「抬手 / 按下即点」、长按拖拽 250/350/450 ms 可调；「更多设置」里可读指针延迟 p50/p95（实测 age p50=8 ms / p95=10 ms，App 侧发送成本 p50=2 ms）
- **游戏手柄**
  - 三种布局：**浮键**（左下移动盘、右下 X/Y/A/B、两侧肩键、顶部 SELECT/START，中间留空不挡画面）/ **皮肤**（整块 Xbox 式机身 + 白色圆环 + 双摇杆）/ **自定义**（任何按键都能拖，横竖屏各存一套）
  - 移动盘三种形态：**十字键** / **圆盘**（按住滑动选 8 方向、扇区高亮、松手回中）/ **摇杆**（真模拟摇杆，摇杆头跟手，输出 X/Y 轴；可开「摇杆同时发方向键」兼容只认方向键的老模拟器）
  - 大小：**移动盘** 与 **按键（ABXY / 肩键 / 菜单键）** 各一个 **60%–200%** 无级滑块，滑块下方显示「本屏实际 N%」（屏幕放不下时如实钳制），立即生效并记忆
  - **大键模式**：隐藏肩键 / 摇杆 / 菜单键，只留方向盘与 ABXY —— 横屏左右贴边、竖屏上下竖排，按键可放到接近满屏
  - 右摇杆轴向可选 **RX/RY** 或 **Z/RZ**（选 Z/RZ 需在平板上忘记设备后重新配对一次）
  - 输入：ABXY + L1/R1/LT/RT + SELECT/START + **L/R 模拟摇杆**（四轴），支持多指同时按
  - 触控板：中间空白可当触控板（关 / 中间面板 / 整片空白），手势与触控板页一致，可选「只移动」不点击；另有可拖动位置的触控板浮层（带 返回/主页/后台），左半屏按键照常可用
  - 排错：设置里有「方向键方向 · 修正一格」开关，旧配对导致方向整体偏一格时可临时纠正；震动反馈可关
- **双手模式**：左侧触控区 + 右侧按键板，一键「互换」左右/上下位置并记忆
- **单手模式**：拇指遥控台 / 拇指键盘，左右手可切换
- **横竖屏自适应**：横屏紧凑工具栏 + 沉浸全屏；顶部「旋转」键强制横/竖屏（长按恢复跟随系统）
- **自适应界面**：以 393dp 为基准按屏幕宽度与系统字体缩放，顶栏放不下时收进「更多」；帮助 →「界面大小」可选 自动 / 小 / 标准 / 大
- **比例可调**：键盘页（触控区 / 键盘）、触控板页与媒体页（功能区 / 留空区）都能拖动调节、双击复位并记忆
- **稳定连接**：前台服务保活、HID 断连自动重新注册、内置配对引导与排错帮助

## 一条通道：蓝牙 HID

```
        ┌────────────────────────────┐
        │     平板 / 电脑 / TV        │
        │    （被控端，零安装、免 root）│
        └──────────▲─────────────────┘
             蓝牙 HID 键鼠 / 媒体 / 手柄
        ┌──────────┴─────────────────┐
        │        Android 手机         │
        │          PhoneKB            │
        └────────────────────────────┘
```

手机把自己注册成**标准蓝牙键盘 + 鼠标 + 媒体控制器 + 手柄**，被控端系统直接认成外设，所以：

- **不需要** 在被控端装 App、开调试、给权限，也不需要 root
- **不需要** 连同一个 WiFi，纯蓝牙即可
- 中文输入用被控端自己的输入法（外接键盘的身份，任何输入法都正常）

## 截图

| 键盘（电脑布局） | 键盘（手机布局） | 数字键盘浮层 |
|---|---|---|
| ![电脑布局](docs/screenshots/01-keyboard-landscape-computer.png) | ![手机布局](docs/screenshots/02-keyboard-landscape-phone.png) | ![数字键盘](docs/screenshots/05-numpad-overlay.png) |

| 媒体 | 手柄（皮肤布局） | 双手模式 |
|---|---|---|
| ![媒体](docs/screenshots/06-media-landscape.png) | ![手柄](docs/screenshots/07-gamepad-landscape.png) | ![双手](docs/screenshots/08-twohand-landscape.png) |

| 手柄（竖屏皮肤） | 手柄设置 | 数字键盘 / 触控板浮层 |
|---|---|---|
| ![手柄竖屏](docs/screenshots/07j-gamepad-skin-portrait.png) | ![手柄设置](docs/screenshots/07e-gamepad-settings.png) | ![手柄触控板](docs/screenshots/08-gamepad-tp-overlay.png) |

| 单手遥控台 | 单手拇指键盘 | 自适应 · 窄屏「更多」 |
|---|---|---|
| ![单手遥控台](docs/screenshots/10-onehand-deck.png) | ![拇指键盘](docs/screenshots/11-onehand-thumb-keyboard.png) | ![更多菜单](docs/screenshots/19-adaptive-overflow-portrait.png) |

| 竖屏键盘 | 指针灵敏度入口 | 指针灵敏度设置 |
|---|---|---|
| ![竖屏键盘](docs/screenshots/12-keyboard-portrait-phone.png) | ![灵敏度胶囊](docs/screenshots/22-sensitivity-chip.png) | ![灵敏度弹窗](docs/screenshots/23-sensitivity-dialog.png) |

| 竖屏触控板 | 界面大小 | 手柄（浮键 · 竖屏） |
|---|---|---|
| ![竖屏触控板](docs/screenshots/14-touchpad-portrait.png) | ![界面大小](docs/screenshots/21-ui-size-dialog.png) | ![手柄浮键竖屏](docs/screenshots/07b-gamepad-portrait.png) |

更多截图见 [docs/screenshots/](docs/screenshots/)。

## 下载与安装

从 [Releases](https://github.com/linxun314/phonekb/releases/latest) 下载：

| 文件 | 装在哪 | 说明 |
|---|---|---|
| `PhoneKB-0.36.1.apk` | **手机** | 全部键鼠 / 媒体 / 手柄功能，一个 APK 就是全部 |

安装提示：Android 13+ 首次安装需允许「此来源的应用」；首次运行请允许通知权限（前台服务保活，否则后台会被系统回收导致蓝牙断开）。

## 快速开始

1. **手机装 PhoneKB 并打开**，顶部状态显示「等待配对 / 已连接」
2. **点右上角「配对」**，按提示让手机蓝牙处于可被发现状态
3. **在被控设备上配对**：打开平板/电脑的蓝牙设置 → 搜索手机蓝牙名称 → 配对；回到 App 看到「**已连接 · 设备名**」即可使用

```mermaid
graph LR
  A[手机打开 PhoneKB] --> B[点「配对」]
  B --> C[平板蓝牙搜索手机名]
  C --> D[配对成功]
  D --> E[顶部显示「已连接」]
  E --> F[键盘 / 触控板 / 媒体 / 手柄]
```

**连上了但按键没反应？** 在手机蓝牙设置里删掉该设备的配对，再从 App 的配对引导重新走一次（HID 需要正确的配对流程）。换了新版本如果手柄摇杆 / LT / RT 没反应，同样需要「忘记设备 → 重新配对」一次（HID 报告描述符更新过）。更多排错见 [docs/使用说明.md](docs/使用说明.md)。

**应用被系统杀掉后平板不自动重连？** 杀掉 App 等于注销了 HID 外设，平板只会在已配对列表里保留记录、不会自动把「输入设备」连回来。回到 App 后到平板蓝牙里点一下这台手机即可（必要时「取消配对」再重新配对一次）。

## 手势与快捷键速查

| 触控板 | 操作 |
|---|---|
| 移动光标 | 单指滑动（速度可在左上角「灵敏度」里调） |
| 左键 | 单指点击 |
| 右键 | 双指点击 |
| 中键 | 三指点击 |
| 滚动 | 双指上下滑动，或按住右侧 ↑/↓ |
| 拖拽 | 按住底部「左键」再用触控区移动 |

| 键盘 | 说明 |
|---|---|
| 切换布局 | 顶部「切换布局」：手机布局 / 电脑布局 |
| 符号 | 手机布局左下角「符号」页，`ABC` 返回 |
| 快捷键 | Ctrl+C / Ctrl+V / Ctrl+X / Ctrl+A / Alt+Tab 专用键 |
| 浮层键盘 / 数字键盘 | 触控板页左侧「键盘」「数字」按钮随时呼出 |

| 手柄 | 说明 |
|---|---|
| 换布局 / 大小 / 类型 | 左上「移动盘」→ 手柄设置，或右上齿轮 |
| 拖动按键位置 | 设置里「编辑布局」，或长按空白约 3 秒；顶部「重置 / 完成」 |
| 中间当触控板 | 顶部「触控」小键切 关 / 中间 / 全屏；黑底浮层可拖动位置 |

## 兼容性

| 角色 | 要求 |
|---|---|
| 手机（控制端） | Android 9（API 28）及以上；系统保留 **Bluetooth HID Device profile**（少数厂商 ROM 会屏蔽，App 会检测并提示） |
| 被控端 | 支持蓝牙键盘/鼠标的设备：Android 平板/手机、Windows、macOS、Linux；iPadOS 13+ 理论支持（未实测） |
| 界面适配 | 以 393dp 为基准自动缩放，约 320–480dp 宽度、系统字体 0.85–1.3 倍下均可用 |

## 为什么不用投屏 / 远程桌面？

| 方案 | 延迟 | 被控端安装 | 能打字/用快捷键 | 适合场景 |
|---|---|---|---|---|
| **PhoneKB（蓝牙 HID）** | 极低（HID 事件） | **不需要** | 系统级键盘，任何 App 都能输入 | 打字、快捷操作、演示、躺床操作 |
| scrcpy / 无线投屏 | 中（视频编码） | 需要开 USB/无线调试 | 部分 | 需要看屏幕、精细操作 |
| 远程桌面类 App | 较高 | 两端都装 | 部分 | 完整桌面操控 |

PhoneKB 不传输画面，只把手机当外设，所以延迟低、被控端干净；需要看屏幕时仍可配合投屏使用。

## 隐私与权限

- 手机端权限：只有 **蓝牙**（HID 必需）和 **通知**（前台服务保活）；不需要网络、存储、通讯录、位置、麦克风、相机
- 没有服务器、没有账号，不采集也不上传任何数据，所有配置只保存在本机 SharedPreferences

## 构建

### Gradle（Android Studio 用户）

```bash
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。需要 JDK 17+、Android SDK Platform 36。

### 命令行（不装 Android Studio，Windows）

```powershell
powershell -ExecutionPolicy Bypass -File tools/build.ps1 -Clean -VersionName 0.36.1 -VersionCode 58
```

脚本直接调用 `aapt2 → javac → d8 → zipalign → apksigner`，产物在 `build/`。设备没有 adb 时，可用 `node tools/serve-apk.js` 起一个局域网下载页（端口 8000）。

### GitHub Actions

仓库自带 CI（[build.yml](.github/workflows/build.yml)），push / PR 自动构建 debug APK 并上传 Artifacts。发布流程见 [docs/发布指南.md](docs/发布指南.md)。

## 项目结构

```
app/src/main/java/com/phonekb/          手机端
  MainActivity.java      页面框架、顶栏、浮层、设置记忆
  HidManager.java        BluetoothHidDevice 封装：注册、连接、报文发送
  HidConstants.java      HID Report Descriptor 与键值常量
  KeyboardView.java      键盘页（两套布局 + 符号页）
  TouchpadView.java      触控板页（手势 / 滚动 / 按键）
  PadController.java     触控板手势判定
  ControlPadView.java    浮层数字键盘 / 键盘 / 触控板（含灵敏度入口）
  MediaView.java         媒体页
  GamepadView.java       手柄页（三布局 / 圆盘 / 摇杆 / 编辑 / 触控板）
  TwoHandView.java       双手模式
  OneHandDeckView.java   单手模式
  PointerSettings.java   指针灵敏度 / 滚轮 / 加速度设置
  SensitivityDialog.java 指针灵敏度弹窗
  KeepAliveService.java  前台保活服务
  ChromeBar.java / Ui.java / Icons.java  顶栏折叠、自适应尺寸、自绘图标
tools/build.ps1          无 IDE 构建脚本
tools/serve-apk.js       局域网 APK 下载页
docs/                    文档与截图
```

## 技术实现

- 手机端用 `BluetoothHidDevice`（API 28+）注册复合 HID 设备，一个 Report Descriptor 内 4 个 Report ID：
  - 键盘：8 字节，6KRO + 修饰键
  - 鼠标：按键 + 相对位移 + 滚轮
  - Consumer Control：16 位 usage（媒体、音量、亮度、浏览器/主页/返回等）
  - Gamepad：hat + 16 个按键 + 4 个轴（X / Y / Rx / Ry，-127..127，摇杆用）
- 全部 UI 为自绘 `View`（Canvas），蓝牙功能**无第三方依赖**（连 jar 都没有）；自适应缩放、顶栏折叠也由自绘逻辑完成
- 手势统一走 `PadController`；指针位移 = 手指数 × 1.7（基准）× 灵敏度，开启加速度时快扫最多再 ×1.9
- 手柄低延迟：HID 报告在专用线程发送、鼠标/摇杆移动 4ms 合并上报（≈250Hz）
- 前台服务保活，避免 Activity 被回收导致 HID 静默注销；注册失败自动重试

## 已知限制

- **中文输入**：蓝牙 HID 只发键码，汉字由被控端自己的输入法完成（手机端不做语音/上传，这也是 v0.36.0 移除 WiFi 伴侣后的取舍）
- **光标外观**：光标由被控端系统绘制，蓝牙鼠标只能发相对位移，无法改形状/大小；可在平板「辅助功能 / 鼠标」里调指针大小
- **亮度键**：多数 Android 平板/电视盒子响应 Consumer 亮度 usage，iQOO / vivo 等部分平板会完全忽略（属于被控端行为，App 无法绕过）
- **截屏 / 多任务**：走 PrintScreen / Alt+Tab，最终由系统决定，个别 App 可能拦截
- 少数手机 ROM 禁用 HID Device profile，只能当主机用
- 同一时间只能有一个 App 注册 HID 外设，装了其它 HID 类 App 需先停用
- 旧配对缓存会保留旧的 HID 描述符：新增摇杆 / LT / RT 后需「忘记设备 → 重新配对」一次

## 常见问题

**Q：平板需要 root 吗？手机需要 root 吗？**
A：都不需要。被控端甚至不需要装 App。

**Q：为什么平板打不出中文？**
A：蓝牙键盘只能发送键码（和外接键盘一样），平板不会把手机上的拼音变成汉字。改用平板自己的输入法即可：拼音、语音、手写都能用，键盘照常打字。

**Q：手机和平板需要连同一个 WiFi 吗？**
A：不需要。PhoneKB 只走蓝牙 HID。

**Q：手柄摇杆 / LT / RT 没反应？**
A：这几项是后加的，HID 描述符变了，需要在平板蓝牙设置里「忘记设备」，再从 App 重新配对一次。

**Q：手柄方向键整体偏一格 / 按左出下？**
A：旧配对缓存的旧描述符导致。重新配对可根治；不想重配可在 手柄设置 →「方向键方向」改成「修正一格」。

**Q：能控制 iPhone / iPad 吗？**
A：iPadOS 13+ 支持蓝牙键盘鼠标，理论上可配对使用（本仓库主要在 Android 平板实测，Apple 设备未实测）。

**Q：能反向，用平板控制手机吗？**
A：不能。PhoneKB 的方向固定：手机是外设，被控端是主机。

**Q：一次能连几台设备？**
A：HID 同一时间只保持一条连接；换设备时在系统蓝牙里切换即可，配对关系会保留。

**Q：为什么不用 scrcpy 那种方案？**
A：那是「投屏 + 注入」，需要被控端开调试；PhoneKB 追求被控端零安装、系统级键盘、低延迟。两者可以互补。

## Roadmap

- [x] 媒体页重做（截屏 / 多任务 / ±10s 长按连发）
- [x] 手柄三布局 + 十字键 / 圆盘 / 模拟摇杆
- [ ] HID 绝对触控（数字化仪）模式：触控板 1:1 映射平板屏幕，点哪打哪
- [ ] USB 通道（ADB 注入）
- [ ] Apple 设备实测（iPadOS）与兼容性说明

## 参与贡献

Issues / PR 都欢迎。提交前请先看 [docs/需求与进展.md](docs/需求与进展.md)（当前能力与待办）与 [docs/使用说明.md](docs/使用说明.md)。

## 文档

| 文档 | 内容 |
|---|---|
| [docs/使用说明.md](docs/使用说明.md) | 完整安装、配对、各页面操作、各平台注意事项、排错、卸载 |
| [docs/调试参数.md](docs/调试参数.md) | `adb am start` 调试参数（切页、模拟窄屏、浮层、灵敏度等） |
| [docs/需求与进展.md](docs/需求与进展.md) | 当前版本的能力清单、限制、待办 |
| [docs/验证记录.md](docs/验证记录.md) | 真机验证记录（Redmi K40 + iQOO Pad2） |
| [docs/发布指南.md](docs/发布指南.md) | 建仓库、构建、打 tag、发布 Release |
| [CHANGELOG.md](CHANGELOG.md) | 当前版本更新说明 |

## 致谢

实现前调研并参考了这些开源项目的行为与思路（**本仓库代码为独立实现，未复制任何 GPL/AGPL 代码**）：

- [jqssun/android-bt-remote](https://github.com/jqssun/android-bt-remote)（GPLv3，界面/交互参考）
- [arnav-kr/Bluke](https://github.com/arnav-kr/Bluke)（AGPLv3）
- [raghavk92/Kontroller](https://github.com/raghavk92/Kontroller)
- [darusc/Mousedroid](https://github.com/darusc/Mousedroid)

## License

[Apache-2.0](LICENSE) © linxun314

---

如果这个项目对你有用，欢迎点一个 ⭐ Star，让更多人看到。
