# BlurrrHooker

基于 [libxposed API](https://github.com/libxposed/api)（Modern Xposed API）的 Xposed 模块，纯 Java 实现，无 Kotlin 源码。

| 项目 | 值 |
| --- | --- |
| 模块包名 | `com.wtbruh.blurrr` |
| 目标应用 | `ai.blurrr.video` |
| libxposed API | 102.0.0（最新） |
| DexKit | org.luckypray:dexkit:2.0.6 |
| AGP / Gradle | 9.2.1 / 9.5.1 |
| compileSdk / minSdk | 37 / 26 |
| Java | 17 |

> libxposed API 102.0.0 要求 `minCompileSdk = 37`，因此工程使用 AGP 9.x + Gradle 9.x + compileSdk 37。

## 目录结构

```
.
├── app
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main
│       ├── AndroidManifest.xml
│       ├── java/com/wtbruh/blurrr
│       │   ├── ModuleMain.java          # 模块入口（XposedModule 子类）
│       │   ├── BlurrrHooker.java        # 目标应用的 hook / DexKit 逻辑
│       │   └── MainActivity.java        # 模块信息页
│       └── resources/META-INF/xposed
│           ├── module.prop              # 模块配置（minApiVersion / targetApiVersion 等）
│           ├── java_init.list           # 模块入口类
│           └── scope.list               # 默认作用域（目标应用包名）
├── gradle/libs.versions.toml            # 版本目录
└── .github/workflows/android.yml        # CI
```

## 构建

```bash
./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

推送到 `master` 分支或手动触发 `Android CI` 工作流后，可在 Actions 的 `app-debug` artifact 中下载 APK。

## 开发说明

- `ModuleMain` 中的 `TARGET_PACKAGE`、`scope.list`、`libs.versions.toml` 中的目标信息保持一致。
- `BlurrrHooker#install` 里已接入 DexKit（`System.loadLibrary("dexkit")` + `DexKitBridge.create(apkPath)`），用于确认 dex 解析链路正常。
- 已实现的 hook：
  - `ai.blurrr.video.MainActivity#onCreate(Bundle)`：原方法执行完后弹出 Toast `Module loaded`。
  - VIP 判断：DexKit 按结构定位 `public static boolean`、无参数、读取 `ai.blurrr.video.user.model.VipStatus.DEFAULT`
    的方法（当前版本为 `s3.m#j()`），hook 后恒返回 `true`；DexKit 未命中时回退到硬编码的 `s3.m#j`。
- 后续 hook 按同样方式写在 `BlurrrHooker` 中即可；`/smali/` 为反编译临时产物，已加入 `.gitignore`。
- 未开启 `autoHotReload`，如需热重载请实现 `onHotReloaded` 并同步修改 `module.prop`。
- 发布版开启了 R8（`minifyEnabled` / `shrinkResources`），ProGuard 规则见 `app/proguard-rules.pro`。

## 远程仓库

`origin` 尚未配置，推送前执行：

```bash
git remote add origin <仓库地址>
git push -u origin master
```
