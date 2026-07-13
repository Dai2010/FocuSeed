# 架构草案

## 推荐技术路线

FocuSeed 适合使用 Kotlin Multiplatform + Compose Multiplatform：

- common：番茄钟状态机、设置模型、时间锚点计算、跨平台 UI 状态。
- android：沉浸式全屏、勿扰权限、拨号器检测、通话状态、Lock Task/Kiosk 集成。
- desktop：Windows/Linux 全屏窗口、桌面环境检测、最佳努力置顶和 Kiosk 提示。

该路线让 Android 系统能力直接使用原生 Kotlin API，同时复用桌面端 UI 和核心计时逻辑。

## 模块划分

- Timer Core：纯业务模块，不依赖 UI 或平台 API。
- Session Store：保存用户设置、当前会话和时间锚点。
- Focus Guard：平台相关的全屏、勿扰、锁定和恢复逻辑。
- Dialer Bridge：Android 专用，负责检测、展示和跳转拨号器。
- UI Shell：Compose UI，展示计时、设置、权限状态和平台限制提示。

## 计时模型

番茄钟不应依赖每秒递减的内存变量作为唯一事实来源。推荐保存：

- sessionStartedAt。
- phaseStartedAt。
- workDuration。
- breakDuration。
- totalRounds。
- completedRounds。

界面刷新时通过当前时间计算剩余时间。这样即使应用被系统暂停、进入后台、打开拨号器或桌面窗口失焦，计时仍然准确。

## 强制专注模型

强制专注分三档实现：

- Soft：普通全屏、隐藏导航/窗口装饰、应用内提醒。
- Guarded：Android 勿扰权限、桌面置顶、返回应用时自动恢复全屏。
- Managed：Android 设备所有者/Lock Task、Windows Assigned Access、Linux Kiosk 会话。

UI 中必须显示当前平台实际可用档位，避免用户误以为普通应用可以绕过系统限制。

## 远端构建

本项目所有编译和打包任务都通过 GitHub Actions 执行。本地尤其是手机 Termux 环境只做轻量编辑、提交和推送。
