# 构建说明

## 原则

本仓库不要求在手机本地编译。所有编译、打包和较重检查都应通过 GitHub Actions 远端运行。

## 本地允许做的事

- 编辑源码和文档。
- 使用 `git status` 查看轻量状态。
- 提交和推送。
- 查看远端 workflow 结果。

## 本地避免做的事

- 不运行 Android、Linux 或 Windows 编译。
- 不运行大型测试套件。
- 不安装大型 SDK 或桌面构建依赖。

## 远端构建入口

`.github/workflows/remote-builds.yml` 是远端构建和发布入口，产物版本号由 workflow 中的 `APP_VERSION` 控制：

- Android debug APK：`FocuSeed-v<version>-android-debug.apk`。
- Linux DEB：`FocuSeed-v<version>-linux.deb`。
- Linux DPKG 命名包：`FocuSeed-v<version>-linux.dpkg`。
- Linux RPM：`FocuSeed-v<version>-linux.rpm`。
- Arch Linux：`focuseed-<version>-1-x86_64.pkg.tar.zst`。
- Windows EXE：`FocuSeed-v<version>-windows.exe`。
- Windows MSI：`FocuSeed-v<version>-windows.msi`。

推送 `v*` tag 时，workflow 会把这些产物发布到 GitHub Release，并优先使用 `docs/releases/<tag>.md` 作为发布介绍。
