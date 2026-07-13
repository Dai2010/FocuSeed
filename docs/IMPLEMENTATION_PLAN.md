# 实施计划

## 第 1 阶段：核心模型

- 建立 Kotlin Multiplatform + Compose Multiplatform 项目骨架。
- 实现 Timer Core 状态机和真实时间锚点计时。
- 实现用户设置：工作时长、休息时长、轮数。
- 在 GitHub Actions 中产出 Android、Linux、Windows 构建产物。

## 第 2 阶段：Android 专注能力

- 实现沉浸式全屏和退出后恢复。
- 实现勿扰权限引导和电话例外策略。
- 实现拨号器检测、候选列表和拨号跳转。
- 实现通话/拨号器返回后的状态恢复。

## 第 3 阶段：桌面专注能力

- 实现 Windows 无边框全屏和置顶。
- 实现 Linux 全屏请求和桌面环境检测。
- 为 GNOME/Wayland 等受限环境显示明确提示。
- 补充 Linux Kiosk 会话部署文档。

## 第 4 阶段：严格模式

- Android：支持 Lock Task/设备所有者配置文档。
- Windows：支持 Assigned Access 部署文档。
- Linux：支持专用 Kiosk 会话部署文档。
- 在 UI 中展示当前设备可用的严格程度。
