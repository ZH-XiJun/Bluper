<div align="center">
<img src="readme-assets/app-icon.png" width=174 alt="App icon"/>

  APP ICON TODO
  
## Bluper ![GitHub Release](https://img.shields.io/github/v/release/ZH-XiJun/com.wtbruh.blurrr?include_prereleases)

*bl**UPER** - s**UPER** **BLU**rr**R***

**帮助用户稍微体验一下Blurrr的SVIP功能。记得24小时内删除哦**

[Say English](README-EN.md)

</div>

## ~~嫌犯口供~~开发说明

由于本人爱用AE做视频，但是呢平时太忙了拿不到电脑，遂决定使用一些手机软件制作视频

接触到Blurrr后，发现有SVIP，接着又发现了[逆向教程](https://bbs.binmt.cc/thread-159589-1-1.html)，于是用AI神力出这个Xposed模块

## 使用说明

- 点小图标下载：![GitHub Release](https://img.shields.io/github/v/release/ZH-XiJun/com.wtbruh.blurrr?include_prereleases)

- 或者下CI版，需要登录GitHub： [![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/zh-xijun/com.wtbruh.blurrr/android.yml)](https://github.com/zh-xijun/com.wtbruh.blurrr/actions/workflows/android.yml)

- 使用最新的[LSPosed](https://github.com/lsposed/lsposed)/[Vector](https://github.com/jingmatrix/vector)框架激活它。（[NPatch](https://github.com/HSSkyBoy/NPatch)/[LSPatch](https://github.com/lsposed/lspatch)兼容性未知）

- 强制停止并重启Blurrr

- 观察到Module loaded的toast弹窗后，就可以开始试用咯

## 构建

*兄弟，跟正常App一样该咋构建咋构建就好了*

```bash
./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

同时有[CI工作流](httos://github.com/zh-xijun/com.wtbruh.blurrr/actions)，push一下就能触发编译流程了
