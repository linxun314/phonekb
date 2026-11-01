# PhoneKB

**把 Android 手机变成蓝牙键盘 / 触控板 / 媒体遥控器 / 游戏手柄 —— 平板、电脑端零安装。**

PhoneKB 是一个纯 Android 端实现的开源小工具：手机作为 **Bluetooth HID 外设**（键盘 + 鼠标 + 多媒体键 + 手柄）工作，
被控设备（Android 平板 / Windows / macOS / Linux / iPadOS）只需要当成普通蓝牙键鼠配对，**不需要在被控端装任何 App，也不需要 root**。

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
- **稳定连接**：前台服务保活，HID 断连自动重新注册，内置配对与排错帮助

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

更多截图见 [docs/screenshots/](docs/screenshots/)。

## 兼容性

- **手机端**：Android 9（API 28）及以上，且系统保留 **Bluetooth HID Device profile**。
  少数厂商 ROM 会屏蔽该 profile（只能当主机，不能当外设）；App 内会检测注册状态并给出提示。
- **被控端**：任何支持蓝牙键盘/鼠标的设备——Android 平板/手机、Windows、macOS、Linux、iPadOS（后两者未实测）。
- 不需要 root，不需要网络，不需要被控端安装软件。

## 安装

1. 在 [Releases](../../releases) 下载最新的 `PhoneKB-x.y.z.apk`（如暂未发布，可用下面「构建」自行编译）；
2. 手机安装（Android 13+ 需允许此来源安装应用）；
3. 首次打开时允许通知权限（前台服务保活需要），蓝牙权限在配对时授权。

## 快速开始

1. 打开 PhoneKB，顶部应显示「等待配对 / 已连接」；
2. 点右上角 **配对**，按提示让手机蓝牙处于可被发现状态；
3. 在平板（或电脑）的蓝牙设置里搜索手机蓝牙名称并配对；
4. 连接成功后 App 顶部显示 **已连接 · 设备名**，即可当作键盘/鼠标使用。

**连上了但没反应？** 先把手机蓝牙设置里这个设备的配对删掉，从 App 的配对引导重新连一次（HID 注册需要正确的配对流程）。
更多排错见 [docs/使用说明.md](docs/使用说明.md)。

## 构建

### 方式一：Gradle（Android Studio 用户）

```bash
./gradlew assembleDebug     # Windows: gradlew.bat assembleDebug
```

要求 JDK 17+、Android SDK Platform 36、Build-Tools 36.x；首次构建会自动下载 Gradle 与 AGP。
用 Android Studio 直接 `File → Open` 本目录即可运行。

### 方式二：不装 Android Studio 的命令行构建（Windows）

```powershell
powershell -ExecutionPolicy Bypass -File tools/build.ps1 -Clean -VersionName 0.12.1 -VersionCode 17
```

脚本直接调用 `aapt2 → javac → d8 → zipalign → apksigner`，产物为 `build/PhoneKB.apk`。
需要 JDK 17+ 和 Android SDK（build-tools / platforms），通过 `ANDROID_HOME` 或默认路径查找。

### 方式三：GitHub Actions（自动构建）

仓库自带 `.github/workflows/build.yml`，push / Pull Request 时自动用 Gradle 构建 debug APK 并上传为构建产物（Artifacts），也可在 Actions 页手动触发。

## 项目结构

```
app/src/main/java/com/phonekb/
  MainActivity.java     页面框架、顶栏、浮层、设置记忆
  HidManager.java       BluetoothHidDevice 封装：注册、连接、各类报文发送
  HidConstants.java     HID Report Descriptor 与键值常量
  KeyboardView.java     键盘页（两套布局 + 符号页）
  TouchpadView.java     触控板页（手势 / 滚动 / 按键）
  PadController.java    触控板手势判定
  ControlPadView.java   浮层数字键盘 / 键盘 / 触控板
  MediaView.java        媒体页
  GamepadView.java      手柄页
  TwoHandView.java      双手模式
  OneHandDeckView.java  单手模式
  KeepAliveService.java 前台保活服务
  Icons.java / Ui.java  自绘图标与通用绘制工具
app/src/main/res/drawable/ic_launcher.xml
tools/build.ps1         无 IDE 构建脚本
docs/                   文档与截图
```

## 技术说明

- 基于 `BluetoothHidDevice`（API 28+）注册为复合 HID 设备，一个 Report Descriptor 内包含 4 个 Report ID：
  - 键盘：8 字节，6KRO + 修饰键
  - 鼠标：按键 + 相对位移 + 滚轮
  - Consumer Control：16 位 usage（媒体、音量、浏览器/主页/返回等）
  - Gamepad：hat + 16 个按键
- 全部 UI 为自绘 `View`（Canvas 绘制），无第三方依赖、无 AndroidX；
- 前台服务保活，防止 Activity 被系统回收导致 HID 静默注销；注册失败自动重试；
- 无网络权限，所有数据仅保存在本机 SharedPreferences。

## 已知限制

- **中文输入**：HID 只发送键码，汉字需要被控端自己的拼音输入法；「手机打中文、平板直接出汉字」需要后续的 WiFi 伴侣 App（见 Roadmap）。
- **光标外观**：光标由被控端系统绘制，蓝牙鼠标只能发送相对位移，无法改变光标形状/大小；可在平板「辅助功能 / 鼠标设置」里调节指针大小。
- 部分手机 ROM 禁用了 HID Device profile，只能把该手机当主机用。
- 同一时间只能有一个 App 注册 HID 外设，若装了其它 HID 类 App 需先停用。

## Roadmap

- WiFi 伴侣 App（被控端装一次，需无障碍权限）：中文直接上屏、剪贴板双向同步、手机语音输入、自定义光标、WiFi 通道与蓝牙自动切换
- HID 绝对触控（数字签）模式：触控板 1:1 映射到平板屏幕，点哪打哪
- 指针速度 / 滚轮速度 / 反向滚动等设置
- 手柄摇杆、扳机键

## 致谢

实现前调研并参考了这些开源项目的行为与思路（**本仓库代码为独立实现，未复制任何 GPL/AGPL 代码**）：

- [jqssun/android-bt-remote](https://github.com/jqssun/android-bt-remote)（GPLv3，界面/交互参考）
- [arnav-kr/Bluke](https://github.com/arnav-kr/Bluke)（AGPLv3）
- [raghavk92/Kontroller](https://github.com/raghavk92/Kontroller)
- [darusc/Mousedroid](https://github.com/darusc/Mousedroid)

## License

[Apache-2.0](LICENSE)
