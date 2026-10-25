# 八条桌宠 · Windows 版源码

普通用户直接到 [Releases](https://github.com/staleegin/daimaobatiao-chat/releases/latest) 下载 `BatiaoPet.exe` 就行，不需要看这里。

- `Program.cs`：全部代码（.NET 8 WinForms，分层窗口按像素透明），动作、台词、菜单和 Mac 版 `../main.swift` 一致。
- `BatiaoPet.csproj`：打包成自带运行环境的单个 exe（win-x64）。
- `app.ico`：图标。

## 自己编译

1. 先准备抠好的表情帧：用 Mac 版导出到这个文件夹的 `assets/`：

   ```bash
   "八条桌宠.app/Contents/MacOS/BatiaoPet" --export "八条桌宠.app/Contents/Resources" assets
   ```

2. 装 .NET 8 SDK 后，在这个文件夹里运行（Mac、Windows 都能编译）：

   ```bash
   dotnet publish -c Release -o out
   ```

   得到 `out/BatiaoPet.exe`。
