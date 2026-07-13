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

`.github/workflows/remote-builds.yml` 是远端构建入口。当前 workflow 面向后续 Kotlin/Compose Multiplatform 项目骨架设计：

- Android debug APK。
- Linux 桌面包。
- Windows 桌面包。

在 Gradle 项目骨架加入之前，workflow 可以手动触发但不会在本地执行任何编译。
