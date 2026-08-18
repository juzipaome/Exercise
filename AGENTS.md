# 练迹项目说明

## 定位

练迹是仅 Android、离线优先的个人健身计划与训练记录 App；运行时不拉取动作数据。

## 构建与验证

```powershell
$env:JAVA_HOME='D:\Software\JetBrains\Android Studio\jbr'
$env:ANDROID_HOME='D:\Software\Android\Sdk'
.\gradlew.bat testDebugUnitTest assembleDebug
.\tools\validate_exercise_dataset.ps1
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 技术栈

- Kotlin、单 Activity、Jetpack Compose
- MIUIX 0.9.4-rc01、Room、DataStore、Coroutines/Flow、Coil GIF
- 仅支持 Android 15 及以上：minSdk 35、targetSdk 36、compileSdk 37、Java 21

## 目录与约定

- `app/src/main/kotlin/com/juzi/lianji/ui/`：页面与通用 UI。
- `app/src/main/kotlin/com/juzi/lianji/data/`：Room、Repository、导入和备份。
- `app/src/main/assets/exercise_dataset/`：固定的 1,324 条离线动作元数据及本地可选媒体。
- `references/miuix/`：跟踪官方 `main` 的本地 MIUIX 参考源码，不参与 App 构建或主仓库提交。
- `tools/`：数据完整性检查；UI 验收截图放 `captures/`，不要堆在项目根目录。
- 整个项目 UI 必须统一使用当前 Maven 已发布的 MIUIX 版本，不得新增 Material UI 组件、自绘已有 MIUIX 图标、自建或仿制 MIUIX 已提供的控件；需要核对最新实现时优先查看 `references/miuix/` 及其中的 `example/`，实际调用必须确认已包含在 App 当前依赖版本中。
- 页面进出和返回必须直接使用 MIUIX 0.9.4-rc01 `miuix-nav` 的 `NavDisplay`，不得只复制其曲线自行实现；主 Tab Pager 遵循 0.9.4-rc01 示例的 `MainPagerState` 行为。
- 确认框遵循示例 App 使用 MIUIX `WindowDialog`，大型选择器经 `FloatingBottomSheet` 入口使用 MIUIX `WindowBottomSheet`；设置页主题、休息时间等紧凑单选项使用 MIUIX `OverlayDropdownPreference`。不得自行使用 Compose `Dialog` 仿制弹层；弹层的进入/收回和点击遮罩收回行为必须由 MIUIX 提供，底部面板的拖动行为也必须由 MIUIX 提供。
- 弹层内部的菜单、单选和多选同样直接使用当前 MIUIX 示例中的 `OverlayIconDropdownMenu`、`RadioButtonPreference` 和 `CheckboxPreference`，不得用基础布局重新拼装 MIUIX 已提供的选择控件。
- 所有可勾选行点击文字区域与点击圆圈必须具有一致的触觉反馈。
- UI 不直接访问 DAO；训练历史使用快照，不能随计划模板修改。
- 动作媒体仅按个人授权使用，公开发布媒体版本前必须重新核实授权。

## 当前状态

四个主 Tab、计划、日程、训练组计时/休息、历史、自定义动作、主题和 JSON 备份已接通；计划编辑和训练中可查看动作详情，已完成记录会自动汇总力量与有氧 PB，并集中显示在动作详情页。
当前单元测试和 Debug 构建通过；Compose 仪器化测试尚未建立，添加首个测试时再引入对应依赖。
