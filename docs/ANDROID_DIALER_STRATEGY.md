# Android 拨号器检测策略

国内 Android 手机的电话/拨号器包名差异很大，FocuSeed 不应把某个品牌的包名写死为唯一方案。正确策略是优先读取系统角色和默认拨号器，其次查询所有支持拨号 Intent 的应用，最后让用户选择。

## 检测优先级

1. 使用 `RoleManager.ROLE_DIALER` 获取当前默认拨号应用。
2. 使用 `TelecomManager.defaultDialerPackage` 作为兼容补充。
3. 使用 `PackageManager.queryIntentActivities(Intent.ACTION_DIAL, tel:)` 枚举可处理拨号的应用。
4. 如果托管/Kiosk 模式启用，把默认拨号器和必要电话组件加入 allowlist。
5. 如果检测到多个候选，在应用内展示“系统默认”和“其他可用拨号器”。

## Android 11+ 包可见性

Android 11 之后查询其他应用需要在 Manifest 中声明可见性。FocuSeed 应声明拨号 Intent 查询，而不是使用宽泛的 `QUERY_ALL_PACKAGES`。

建议 Manifest 查询项：

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.DIAL" />
        <data android:scheme="tel" />
    </intent>
</queries>
```

## 跳转拨号器

拨号入口应默认使用 `ACTION_DIAL`，让系统拨号器接管输入和呼叫流程：

```kotlin
val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
startActivity(intent)
```

如果用户选择了某个候选拨号器，可以在确认该包仍可处理 Intent 后设置 `intent.setPackage(packageName)`。如果启动失败，应回退到系统 chooser。

## 通话后恢复

- 记录进入拨号器前的番茄钟会话 ID、阶段和时间锚点。
- 在应用 `onResume` 时重新计算当前阶段，而不是恢复旧倒计时数值。
- 监听通话状态时，只把状态用于恢复 UI，不暂停番茄钟计时。
- 通话结束或拨号器退出后，重新进入沉浸式全屏，并重新应用勿扰策略。

## 厂商差异处理

- 华为、小米、OPPO、vivo、荣耀、三星等厂商可能使用不同电话组件。
- 项目只把厂商包名作为诊断日志或兼容提示，不作为主检测逻辑。
- 自动检测结果应在设置页可见，方便用户反馈某个 ROM 的兼容问题。
