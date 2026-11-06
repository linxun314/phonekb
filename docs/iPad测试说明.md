# iPad 测试包说明（PhoneKB Pad 0.2.0 · Apple 模式）

手机当蓝牙键盘控制 **iPad** 一直没成功过（Android 平板/电脑都正常）。这一版是一个**只改协议层**的实验包，用来验证「iPad 之所以不理我们，是因为我们上报的是一个复合 HID 设备」这个假设。

## 改了什么

- **描述符**：原来是 4 个 collection 的复合设备（键盘 report id 1 / 媒体 id 2 / 鼠标 id 3 / 手柄 id 4），键盘报文只有 2 字节 `[修饰键, 键码]`，没有 LED 输出报文。现在换成**一个**标准 Boot Keyboard collection：8 字节输入报文 `[修饰键, 保留, 键码×6]`（6KRO）+ 1 字节 LED 输出报文，**不带 report id**。
- **SDP 记录**：子类从 `0x00` 改成 `0x40`（键盘），描述改成 `PhoneKB Bluetooth keyboard`。
- **发送**：键盘报文按 8 字节发（report id 0）；鼠标 / 媒体 / 手柄报文在这个包里**不发**（描述符里没有它们，发过去主机会当成未知 report id）。
- **LED 输出**：主机（iPad）下发的 LED 报文会打日志 `led 0x..`（bit1 = CapsLock），可以用来确认 iPad 真的接受了这套描述符。
- **配对入口**：这个包之前**没有**「让手机可被发现」的按钮，iPad 根本搜不到手机。现在顶栏加了「配对」，点一下开 5 分钟可发现窗口，并弹出配对步骤。

## 怎么测

1. 手机装 `PhoneKB-Pad-iPad-test.apk`（包名 `com.phonekb.pad`，v0.2.0 / 2）。
2. **先强制停止主程序 PhoneKB**（设置 → 应用 → PhoneKB → 强制停止），否则两个 App 抢同一个 HID 槽位会报「HID 注册被拒绝」。
3. iPad：设置 → 蓝牙 → 在旧的 PhoneKB / 手机名记录上点**「忽略此设备」**（描述符是配对时缓存的，不清掉永远是旧的那份）。
4. 手机打开 PhoneKB Pad → 点顶栏**「配对」** → 允许可发现。
5. iPad 蓝牙列表里点这台手机的名字 → 两端确认配对码 → 手机顶部显示「已连接」。
6. iPad 打开**备忘录**直接打字：软键盘应该收起，字要出来，退格能用，大写锁定键的灯能亮。

## 结果怎么读

- **能看到手机、能配对、打字有字** → 假设成立，接着做完整的 iPad 兼容模式（描述符 + 触控板/媒体按 iPad 的方式重做、记住 Apple 模式、帮助文案、文档）。
- **能看到手机、能配对，但打字没反应** → 描述符还不够，下一步试：把 LED 输出报文去掉、改成分离的 report id、或补 `onInterruptData`，也看 `led 0x..` 日志里 iPad 有没有回过报文。
- **连配对都失败 / 或 iPad 完全看不到手机** → 大概率不是描述符问题，先抓 `adb logcat` 里 `PhoneKB` 和 `BluetoothHidDevice` 的行再判断。

## 日志

```
C:\Users\22453\AppData\Local\Android\Sdk\platform-tools\adb.exe logcat -s PhoneKB:HidManager
```

看三行就够：`registerApp ok=true`、`conn 2 <设备名>`、以及打字时有没有 `led 0x..`。

## 和主程序的关系

`app-pad/` 是独立模块（包名 `com.phonekb.pad`，产物 `build\PhoneKB-Pad-iPad-test.apk`），主程序 `app/` 的 Android 平板 / 电脑模式完全不受影响，描述符也没动。
