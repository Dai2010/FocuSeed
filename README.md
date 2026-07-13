# FocuSeed

FocuSeed 是一个面向 Linux、Windows 和 Android 的强制专注番茄钟软件。

它的目标不是做一个普通计时器，而是在用户开始番茄钟后尽可能进入“不可分心”的专注状态：全屏显示番茄钟、屏蔽通知、保留电话能力，并在通话结束后自动回到当前番茄钟会话。计时以真实时间为准，用户离开应用、进入拨号界面或接听电话时，番茄钟倒计时不会暂停。

## 目标平台

- Android 10 及以上版本。
- Windows 10/11。
- 主流 Linux 桌面环境。

## 核心功能

- 自定义工作时长、休息时长和番茄钟轮数。
- 番茄钟开始后进入全屏专注界面。
- Android 上请求勿扰模式权限，屏蔽普通通知并保留电话。
- Android 上自动检测系统可用拨号应用，提供拨号入口。
- Android 上在用户退出拨号器或通话结束后恢复番茄钟界面。
- 启动时静默检查 GitHub Release 更新，发现新版后提示下载，并支持手动切换 `ghfast` 加速。
- 使用基于时间锚点的计时模型，确保离开应用期间计时继续推进。

## 平台限制

FocuSeed 会尽量使用系统公开 API 实现强制专注，但不同平台的系统权限边界不同：

- Android 普通应用不能无条件拦截所有系统操作；强约束模式需要用户授予勿扰权限，部分设备还需要应用固定、设备所有者或企业/Kiosk 配置。
- Android 拨号应用不能依赖固定包名，项目会通过系统角色、默认拨号器和 `ACTION_DIAL` Intent 自动发现。
- Windows 可以实现全屏/置顶，但真正阻止切换应用需要系统级 Kiosk、Assigned Access 或管理员策略配合。
- Linux 尤其是 Wayland/GNOME 环境不允许普通应用强制覆盖整个桌面；全屏只能作为最佳努力，严格模式需要 Kiosk 会话或桌面环境策略配合。

详细说明见 `docs/PLATFORM_CAPABILITIES.md`。

## 许可证

This project is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for details.
