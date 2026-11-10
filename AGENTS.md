# AGENTS.md — PhoneKB 开发原则（所有协作者 / AI 必读）

> 这份文件是**硬规则**。任何 AI 代理、任何新会话，动手之前先读这一页。

## 一、执行闸门（最高优先级）

**用户没说「执行」，就只出方案，不动代码。**

1. 需求进来时，第一件事是**复述理解 + 出方案**：说清「你要的是什么、我打算怎么做、会改哪些地方、有什么代价、怎么验收」。
2. 在用户明确回复「执行」（或同义：做吧 / 开始 / 按你说的做 / 就这个）之前：
   - **不写代码、不改文件、不编译、不装机、不截图、不提交、不推送**
   - 允许做的只有：读代码、查文档、查资料、画效果图（用户要求时）、出方案
3. 用户说「执行」**只覆盖那一个已确认的方案**。方案里新增的想法、顺带要修的 bug、我自己的「优化」都不算，必须再问一次。
4. 一条消息里如果同时给了「方案」和「动作」（例如「你先出方案，同时把这条规则写进原则」），**只做他明确下达的动作**，其余仍然等「执行」。
5. 用户中途改需求，等于方案作废，回到第 1 步重新确认。

## 二、交付与验证

1. 每次改完都要自证：`& .\tools\build.ps1 -Clean` 编译 → `adb -s e97880b3 install -r build\PhoneKB.apk` 装机 → 截图/日志验证。用户可能同时拔线并行测试，装机前先 `adb devices` 确认在线。
2. 版本号 4 处一起改：`app/src/main/AndroidManifest.xml:3-4`、`app/build.gradle:13-14`、`tools/build.ps1:6-7`（含 :15 注释）、`MainActivity.java` footer（约 :293）。
3. 文档同步：`CHANGELOG.md`、`README.md`、`docs/使用说明.md`、`docs/调试参数.md`、`docs/需求与进展.md`、`docs/验证记录.md`、`docs/发布指南.md`。
4. **推送 / 打 tag / 发 Release 必须用户单独说**，不说话就只在本地提交。

## 三、Git

- 允许本地 `git add -A && git commit`（每次功能完成一条）。
- `git push` / `git tag` / GitHub Release **等用户明确指令**。
- 仓库：https://github.com/linxun314/phonekb

## 四、界面与交互原则

详见 [docs/设计原则.md](docs/设计原则.md)：常用键在拇指弧内、命中区不相交、有效触控 ≥ 48dp、按下先发数据再管视觉、每个开关都能关掉、横竖屏分别验收。

## 五、技术红线（踩过的坑，别再踩）

1. Bluetooth HID 描述符**在配对时被主机缓存**，改描述符必须让主机「忘记设备」重新配对。
2. `Ui.rounded/roundedStroke` 的 radius 参数是 **dp**，传 `Ui.dpi(x)` 会放大 2.75 倍。
3. 调试 extra 里的弹窗回调必须用 `new Handler(Looper.getMainLooper()).postDelayed(...)`，不能用 `contentFrame.postDelayed`（`--ei orient` 重建会吃掉回调）。
4. `--ei orient 1` = 竖屏、`2` = 横屏（文档曾写反，已纠正）。
5. 滚动 / 布局数学一律在 **dp 空间**算，只在绘制基元里乘密度；混着算会静默缩小、裁切。
6. 手机侧 `adb shell input tap/keyevent` 在 MIUI/HyperOS 上被拒绝，UI 验证用截图 + 调试 extra。
7. 勿截用户屏幕之外的私人内容；脚本里只用 ASCII 以防 PowerShell 中文乱码。
