# 练迹

一个仅 Android、纯本地离线的健身计划与训练记录 App，使用 Kotlin、Jetpack Compose、Room 与 MIUIX。

当前已接通计划创建、日程安排、力量训练组与休息计时、有氧时长/距离记录、历史日历、离线动作库、自定义动作、动作中文显示名修改、主题设置及 JSON 备份恢复。计划编辑和训练中均可查看动作详情；已完成记录会自动汇总力量与有氧 PB，并集中显示在动作详情页。训练历史保存独立快照，不会随计划模板或动作显示名修改。

## 构建

本机配置使用 Android Studio 自带 JBR、Android API 37 编译平台，运行目标保持 Android API 36：

```powershell
$env:JAVA_HOME='D:\Software\JetBrains\Android Studio\jbr'
$env:ANDROID_HOME='D:\Software\Android\Sdk'
.\gradlew.bat testDebugUnitTest assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

验证内置动作数量、ID 和署名：

```powershell
.\tools\validate_exercise_dataset.ps1
```

如果你在本地拥有已获授权的动作媒体，也可以额外验证图片和 GIF：

```powershell
.\tools\validate_exercise_dataset.ps1 -RequireMedia
```

项目协作边界和目录约定见 `AGENTS.md`。

## 目录

- `app/`：Android 应用、Room 数据层、Compose UI 与离线动作元数据。
- `app/src/main/assets/exercise_dataset/images/` 和 `videos/`：本地可选媒体目录，已被 `.gitignore` 排除，不随公开源码发布。
- `tools/`：数据校验与资源生成工具。

## 数据与许可

- 动作元数据及说明来自 [hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset)，MIT License。
- 180×180 图片与 GIF：© Gym visual — https://gymvisual.com/ 。由于授权范围限制，媒体文件不包含在本仓库中；如需本地使用，请自行取得授权并放入上述目录。
- UI 引用了 [MIUIX](https://github.com/compose-miuix-ui/miuix)。
