# 八条桌宠 · 安卓版源码

普通用户直接下载 [BatiaoPet.apk](https://github.com/staleegin/daimaobatiao-chat/raw/main/desktop-pet/BatiaoPet.apk) 安装就行，不需要看这里。

- `src/`：全部代码（Java）。`PetService` 是悬浮窗和前台服务，`PetView` 画八条、处理点击和拖动，`MainActivity` 是设置页。动作、台词和 Mac / Windows 版一致。
- `res/`：图标。
- `build.sh`：不用 Gradle，直接用 SDK 自带工具打包。

## 自己编译

1. 准备抠好的表情帧：用 Mac 版导出到这个文件夹的 `assets/`（和 Windows 版相同）：

   ```bash
   "八条桌宠.app/Contents/MacOS/BatiaoPet" --export "八条桌宠.app/Contents/Resources" assets
   ```

2. 装好 JDK 17 和 Android SDK（`platforms;android-34`、`build-tools;34.0.0`），用你自己的签名文件运行：

   ```bash
   KEYSTORE=你的.jks KS_PASS=密码 ./build.sh
   ```

   得到 `build/BatiaoPet.apk`。用别的签名打出来的包不能覆盖安装官方版，要先卸载。
