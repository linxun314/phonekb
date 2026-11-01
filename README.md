# PhoneKB

**把 Android 手机变成蓝牙键盘 / 触控板 / 媒体遥控器 / 游戏手柄 —— 平板、电脑端零安装。**
外加一个可选的 **WiFi 伴侣**：解决蓝牙 HID 打不出中文、剪贴板不同步的问题。

PhoneKB 是一个纯 Android 端实现的开源小工具：手机作为 **Bluetooth HID 外设**（键盘 + 鼠标 + 多媒体键 + 手柄）工作，
被控设备（Android 平板 / Windows / macOS / Linux / iPadOS）只需要当成普通蓝牙键鼠配对，**不需要在被控端装任何 App，也不需要 root**。

如果偶尔需要「手机打中文、平板直接出字」，再在平板装一次可选的 **PhoneKB 伴侣**（需无障碍权限），
通过 WiFi 把文字写进平板当前输入框，并做剪贴板双向同步。蓝牙 HID 与 WiFi 按需同时使用，互不影响。

> 实测组合：Redmi K40（Android 13 / HyperOS）→ iQOO Pad2（Android 14）。平板端零安装，配对即用。

## 功能

- **蓝牙 HID，一装一配**：手机端一个 APK，被控端只需蓝牙配对
- **键盘**：手机布局 / 电脑布局两套，含符号页、Ctrl+C/V/X/A、Alt+Tab 等快捷键
- **触控板**：单指移动 · 点按左键 · 双指滚动 · 双指点击右键 · 三指中键；物理左/中/右键可拖动（拖拽选中等场景）；可一键弹出**浮层数字键盘 / 浮层键盘**
- **媒体键**：上一首 / 播放暂停 / 下一首、音量 ±、静音、CC、返回 / 主页 / 菜单 / 电源
- **游戏手柄**：十字键 + X/Y/A/B + L1/R1 + SELECT/START
- **双手模式**：触控区 + 按键板左右分工，可一键「互换」对调
- **单手模式**：拇指遥控台 / 拇指键盘，左右手可切换
- **横竖屏自适应**：横屏紧凑工具栏 + 沉浸全屏；顶部「旋转」键可强制横/竖屏（长按恢复跟随系统）
- **比例可调**：键盘页（触控区/键盘）、触控板页与媒体页（功能区/留空区）均可拖动调节并记忆
- **WiFi 伴侣（可选）**：平板装一次 PhoneKB 伴侣 → 中文直接写入平板输入框、剪贴板双向同步（平板复制的文字自动回到手机）；手机与平板**扫码配对**，也支持手动 IP / 配对码
- **稳定连接**：前台服务保活，HID 断连自动重新注册，内置配对与排错帮助

## 两种通道怎么选

| 场景 | 用哪条通道 |
|---|---|
| 打字、快捷键、移动/点击光标、媒体键、手柄 | **蓝牙 HID**（平板零安装） |
| 输入中文、把长文本发到平板、剪贴板互传 | **WiFi 伴侣**（平板装一次伴侣 App） |

## 截图

| 键盘（电脑布局） | 触控板 | 媒体 |
|---|---|---|
| ![键盘](docs/screenshots/01-keyboard-landscape-computer.png) | ![触控板](docs/screenshots/03-touchpad-landscape.png) | ![媒体](docs/screenshots/06-media-landscape.png) |

| 手柄 | 双手模式 | 单手模式 |
|---|---|---|
| ![手柄](docs/screenshots/07-gamepad-landscape.png) | ![双手](docs/screenshots/08-twohand-landscape.png) | ![单手](docs/screenshots/10-onehand-deck.png) |

| 键盘（手机布局） | 数字键盘浮层 | 竖屏 · 触控板 |
|---|---|---|
| ![手机布局](docs/screenshots/02-keyboard-landscape-phone.png) | ![数字键盘](docs/screenshots/05-numpad-overlay.png) | ![竖屏触控板](docs/screenshots/14-touchpad-portrait.png) |

| WiFi 伴侣（手机端） |
|---|
| ![伴侣](docs/screenshots/17-wifi-companion-phone.png) |

更多截图见 [docs/screenshots/](docs/screenshots/)。

## 兼容性

- **手机端**：Android 9（API 28）及以上，且系统保留 **Bluetooth HID Device profile**。
  少数厂商 ROM 会屏蔽该 profile（只能当主机，不能当外设）；App 内会检测注册状态并给出提示。
- **被控端**：任何支持蓝牙键盘/鼠标的设备——Android 平板/手机、Windows、macOS、Linux、iPadOS（后两者未实测）。
  蓝牙 HID 不需要 root，不需要被控端安装软件。
- **WiFi 伴侣（可选）**：被控端为 Android 9（API 28）及以上平板，安装伴侣 APK 并开启无障碍服务；手机与平板在同一 WiFi（也可以用手机热点）。

## 安装

1. 在 [Releases](../../releases) 下载最新的 `PhoneKB-x.y.z.apk`（如暂未发布，可用下面「构建」自行编译）；
2. 手机安装（Android 13+ 需允许此来源安装应用）；
3. 首次打开时允许通知权限（前台服务保活需要），蓝牙权限在配对时授权。
4. 需要中文输入时，再把 `PhoneKB-Companion-x.y.z.apk` 装到平板上（平板首次需要允许安装应用、开启无障碍服务）。

## 快速开始（蓝牙 HID）

1. 打开 PhoneKB，顶部应显示「等待配对 / 已连接」；
2. 点右上角 **配对**，按提示让手机蓝牙处于可被发现状态；
3. 在平板（或电脑）的蓝牙设置里搜索手机蓝牙名称并配对；
4. 连接成功后 App 顶部显示 **已连接 · 设备名**，即可当作键盘/鼠标使用。

**连上了但没反应？** 先把手机蓝牙设置里这个设备的配对删掉，从 App 的配对引导重新连一次（HID 注册需要正确的配对流程）。
更多排错见 [docs/使用说明.md](docs/使用说明.md)。

## WiFi 伴侣（可选，解决中文输入）

1. 平板安装并打开 **PhoneKB 伴侣**，页面会显示一个配对二维码；
2. 手机打开 PhoneKB → 右上角 **伴侣** → 点 **扫码连接平板**，用相机拍下平板二维码（自动填好 IP / 端口 / 配对码）；
3. 平板会自动连回手机，手机伴侣页显示「平板已连接」；
4. 在「发送中文到平板」框里输入中文 → **发送到平板**，文字会写进平板当前输入框（或点 **发送剪贴板** 发手机剪贴板内容）；
5. 在平板上复制文字，会自动同步到手机剪贴板，手机直接粘贴即可。

> 无障碍服务是 Android 的安全限制：平板重启/被清理后需要回「设置 → 无障碍」重新打开一次。
> 建议给伴侣 App 开「电池不限制」「允许自启动」，可减少被系统清理的概率。
> 具体步骤见 [docs/使用说明.md](docs/使用说明.md)。

## 构建

### 方式一：Gradle（Android Studio 用户）

```bash
./gradlew assembleDebug     # Windows: gradlew.bat assembleDebug
```

一次构建两个模块，产物：

- 手机端：`app/build/outputs/apk/debug/app-debug.apk`
- 平板伴侣：`companion/build/outputs/apk/debug/app-debug.apk`

要求 JDK 17+、Android SDK Platform 36、Build-Tools 36.x；首次构建会自动下载 Gradle 与 AGP。
用 Android Studio 直接 `File → Open` 本目录即可运行。

### 方式二：不装 Android Studio 的命令行构建（Windows）

```powershell
# 手机端
powershell -ExecutionPolicy Bypass -File tools/build.ps1 -Clean -Module app -VersionName 0.15.0 -VersionCode 20
# 平板伴侣
powershell -ExecutionPolicy Bypass -File tools/build.ps1 -Clean -Module companion -VersionName 0.3.0 -VersionCode 3
```

脚本直接调用 `aapt2 → javac → d8 → zipalign → apksigner`，产物为 `build/PhoneKB.apk` 与 `build/PhoneKB-Companion.apk`。
需要 JDK 17+ 和 Android SDK（build-tools / platforms），通过 `ANDROID_HOME` 或默认路径查找。

平板通常没有 adb：在电脑上执行 `node tools/serve-apk.js` 起一个局域网下载页（端口 8000），平板浏览器打开 `http://<电脑IP>:8000` 即可下载安装伴侣 APK。

### 方式三：GitHub Actions（自动构建）

仓库自带 `.github/workflows/build.yml`，push / Pull Request 时自动用 Gradle 构建两个 debug APK 并上传为构建产物（Artifacts），也可在 Actions 页手动触发。

发布到 GitHub 的详细步骤见 [docs/发布指南.md](docs/发布指南.md)（建仓库、推送、打 tag、上传 APK）。

## 项目结构

```
app/src/main/java/com/phonekb/          手机端
  MainActivity.java      页面框架、顶栏、浮层、设置记忆
  HidManager.java        BluetoothHidDevice 封装：注册、连接、各类报文发送
  HidConstants.java      HID Report Descriptor 与键值常量
  KeyboardView.java      键盘页（两套布局 + 符号页）
  TouchpadView.java      触控板页（手势 / 滚动 / 按键）
  PadController.java     触控板手势判定
  ControlPadView.java    浮层数字键盘 / 键盘 / 触控板
  MediaView.java         媒体页
  GamepadView.java       手柄页
  TwoHandView.java       双手模式
  OneHandDeckView.java   单手模式
  CompanionActivity.java WiFi 伴侣页（扫码配对 / 中文上屏 / 剪贴板 / 日志）
  CompanionBridge.java   WiFi 通道服务端（TCP，一行一个 JSON）
  QrCode.java            二维码生成
  KeepAliveService.java  前台保活服务
  Icons.java / Ui.java   自绘图标与通用绘制工具
companion/src/main/java/com/phonekb/companion/   平板伴侣
  MainActivity.java      配对二维码 / 连接状态 / 收发日志
  LinkService.java       通道客户端 + 配对监听 + 保活
  InjectorService.java   无障碍服务：把文字写入当前输入框
  QrCode.java            二维码生成
  res/xml/accessibility_service.xml
tools/build.ps1         无 IDE 构建脚本
tools/serve-apk.js      局域网 APK 下载页（平板安装伴侣用）
docs/                    文档与截图
```

## 技术说明

- 手机端基于 `BluetoothHidDevice`（API 28+）注册为复合 HID 设备，一个 Report Descriptor 内包含 4 个 Report ID：
  - 键盘：8 字节，6KRO + 修饰键
  - 鼠标：按键 + 相对位移 + 滚轮
  - Consumer Control：16 位 usage（媒体、音量、浏览器/主页/返回等）
  - Gamepad：hat + 16 个按键
- 全部 UI 为自绘 `View`（Canvas 绘制），蓝牙功能无第三方依赖；
- 前台服务保活，防止 Activity 被系统回收导致 HID 静默注销；注册失败自动重试；
- WiFi 伴侣：手机端是 TCP 服务端（默认端口 **47653**），平板上是客户端；协议为「一行一个 JSON」；
  配对时平板监听 **47654** 并展示二维码，手机扫码后把 `{ip,port,token}` 交给平板，平板再主动连回手机；
- 平板端用 `AccessibilityService` 把文字插入当前焦点输入框，并读取剪贴板做双向同步；手机端 APK 只使用系统相机 App 扫码，不申请相机权限；
- 手机端仅申请蓝牙、网络（局域网 WiFi 伴侣用）、通知（前台保活）权限；不采集、不上传任何数据，配置只保存在本机 SharedPreferences。

## 已知限制

- **中文输入**：蓝牙 HID 只发送键码，汉字需要被控端自己的拼音输入法；「手机打中文、平板直接出汉字」请用 WiFi 伴侣。
- **WiFi 伴侣依赖无障碍**：Android 不允许第三方 App 自动开启无障碍，平板重启/被清理后需要手动重新打开一次；需要同一局域网（手机热点也可以）。
- **伴侣只解决文字/剪贴板**：键鼠操作仍走蓝牙 HID（平板零安装），两条通道互不影响。
- **光标外观**：光标由被控端系统绘制，蓝牙鼠标只能发送相对位移，无法改变光标形状/大小；可在平板「辅助功能 / 鼠标设置」里调节指针大小。
- 部分手机 ROM 禁用了 HID Device profile，只能把该手机当主机用。
- 同一时间只能有一个 App 注册 HID 外设，若装了其它 HID 类 App 需先停用。

## Roadmap

- WiFi 伴侣：手机语音输入直接上屏、自定义光标浮窗（大小/颜色）、WiFi 与蓝牙自动切换
- HID 绝对触控（数字签）模式：触控板 1:1 映射到平板屏幕，点哪打哪
- 指针速度 / 滚轮速度 / 反向滚动等设置
- 手柄摇杆、扳机键
- USB 通道（ADB 注入）

## 致谢

实现前调研并参考了这些开源项目的行为与思路（**本仓库代码为独立实现，未复制任何 GPL/AGPL 代码**）：

- [jqssun/android-bt-remote](https://github.com/jqssun/android-bt-remote)（GPLv3，界面/交互参考）
- [arnav-kr/Bluke](https://github.com/arnav-kr/Bluke)（AGPLv3）
- [raghavk92/Kontroller](https://github.com/raghavk92/Kontroller)
- [darusc/Mousedroid](https://github.com/darusc/Mousedroid)

## License

[Apache-2.0](LICENSE)
