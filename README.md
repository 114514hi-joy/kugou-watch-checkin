# 酷狗概念版手表签到（Kugou Watch Checkin）

面向 WearOS 手表（如 OPPO Watch 3 Pro `378×496`）及 32 位 / 64 位 Android 设备的极简酷狗音乐概念版自动签到小工具。

- **纯原生 Java 实现**：零第三方重库、零原生 `.so` 依赖，**32 位（`armeabi-v7a`）与 64 位通用**
- **超轻体积**：Release 安装包仅约 **23 KB**，用完即走、不驻留后台
- **两种登录方式**：
  - **扫码登录**：手机酷狗 App 扫码授权，免手表打字
  - **手机号验证码登录**：支持发送短信验证码并登录
- **一键签到领 VIP**：依次调用听歌领 VIP、每日畅听 VIP、升级概念版 VIP 并查询会员到期时间

## 直接下载

可在 [releases/kugou-watch-checkin-v1.0.0.apk](releases/kugou-watch-checkin-v1.0.0.apk) 下载编译好的安装包。

## 本地构建

```powershell
gradle :app:assembleRelease
```

要求：JDK 17、Android SDK (`compileSdk 34`, `minSdk 24`)、Gradle 8.x。

## 致谢与声明

- 接口协议参考自开源项目 [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi)。
- 本项目仅用于个人学习与技术交流，请勿用于商业或违规用途。
