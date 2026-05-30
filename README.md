# 🥽 Rokid Meeting Helper

**Rokid AI Glasses 会议助手 — 实时录音、AI 摘要、智能问答**

基于 [openclaw-rokid](https://github.com/etdofreshai/openclaw-rokid) 架构，在 Rokid AI 眼镜上运行的完整会议助手应用。眼镜直连 WiFi → WebSocket → 你的 OpenClaw Gateway（GPT-5.5 + Whisper），无需手机中继。

---

## ✨ 核心功能

| 功能 | 描述 |
|------|------|
| 🎤 **实时音频流** | 4 麦克风阵列 → AudioRecord 16kHz/16bit/mono → WebSocket 实时推送 |
| 🖥️ **HUD 极简指示** | 480×640 单色绿微 LED，仅显示闪烁 REC 指示器，不干扰开会 |
| 📝 **自动摘要邮件** | 会议结束 → GPT-5.5 生成 Markdown 纪要 → SMTP 发送到 lhjjjk4@gmail.com |
| 💡 **快速问答** | 双击触控板 → 取近 30 秒转录 → GPT-5.5 搜索 → 压缩 ≤3 行要点显示 |
| 📡 **断线重连** | WebSocket 指数退避自动重连（1s→2s→4s→…→30s） |
| 🔄 **自更新** | GitHub Releases + DexClassLoader 热加载，无需侧载新 APK |

---

## 🏗️ 架构

```
眼镜 (Rokid Glasses)              隧道 (Cloudflare/Tailscale)        你的电脑
┌──────────────────────┐          ┌──────────────────────┐       ┌──────────────┐
│  Meeting Helper APK  │──WSS──▶  │  Cloudflare Tunnel   │─────▶ │  OpenClaw    │
│  • AudioRecord 推流  │          │  或 Tailscale VPN    │       │  Gateway     │
│  • HUD 录制指示器    │          └──────────────────────┘       │  :8080       │
│  • 手势触控交互      │                                          │  ├ Whisper   │
│  • 自更新加载器      │                                          │  ├ GPT-5.5   │
└──────────────────────┘                                          │  └ SMTP邮件  │
                                                                  └──────────────┘
```

**无需手机桥接** — 眼镜独立运行：
- **`glasses-app/`** — 独立 Android 应用（Kotlin/Jetpack Compose）
- **`phone-app/`** — 预留：未来 Rokid Max/AR 产品线（需手机中继 + CXR-S SDK）
- **`shared/`** — 预留：组件间共享的协议定义
- **`docs/`** — 设计文档 + OpenClaw Gateway 配置指南

---

## 📋 环境要求

| 项目 | 要求 |
|------|------|
| **开发环境** | [Android Studio](https://developer.android.com/studio) Hedgehog (2023.1.1) 或更新 |
| **JDK** | JDK 17 |
| **Rokid 眼镜** | Rokid AI Glasses（YodaOS-Sprite / Android Go） |
| **开发线缆** | Rokid 专用开发数据线（支持 ADB） |
| **WiFi** | 眼镜需连接 WiFi 网络 |
| **OpenClaw Gateway** | 运行在你的电脑上（或服务器），详见 [docs/](docs/) |
| **OpenAI API** | GPT-5.5 + Whisper API Key |

---

## 🚀 快速开始

### 1. 克隆仓库

```bash
git clone https://github.com/KeithHello/meeting-helper-rokid.git
cd meeting-helper-rokid
```

### 2. 打开项目

用 Android Studio 打开 `glasses-app/` 目录（不是根目录）。

```
File → Open → 选择 meeting-helper-rokid/glasses-app/
```

### 3. 等待 Gradle Sync

首次打开会自动下载依赖，包括：
- Jetpack Compose BOM 2024.01.00
- OkHttp 4.12.0 (WebSocket)
- kotlinx-serialization-json 1.6.2
- CameraX 1.3.1

### 4. 构建 APK

```bash
# 命令行方式
cd glasses-app
./gradlew assembleDebug

# 或 Android Studio → Build → Build Bundle(s) / APK(s) → Build APK(s)
```

APK 输出位置：`glasses-app/build/outputs/apk/debug/glasses-app-debug.apk`

### 5. 部署到 Rokid 眼镜

#### Step 5a: 连接眼镜到电脑

1. 使用 **Rokid 专用开发数据线**（非普通充电线）连接眼镜左侧镜腿触点到电脑 USB
2. 在 **Rokid AI 手机 App** 中启用 ADB 调试
3. 验证连接：
   ```bash
   adb devices
   # 应显示: <设备序列号>    device
   ```

#### Step 5b: 安装 APK

```bash
adb install glasses-app/build/outputs/apk/debug/glasses-app-debug.apk
```

如果已有旧版本，使用 `-r` 覆盖安装：
```bash
adb install -r glasses-app/build/outputs/apk/debug/glasses-app-debug.apk
```

#### Step 5c: 启动应用

在眼镜上找到 "Meeting Helper" 应用图标启动，或通过 ADB：
```bash
adb shell am start -n com.etdofresh.rokidopenclaw/.loader.LoaderActivity
```

### 6. 配置 OpenClaw Gateway

应用首次启动后，在眼镜的 Settings 界面配置 Gateway URL。

> ⚠️ **重要**: 必须先部署 OpenClaw Gateway。详见下方 [Gateway 部署](#openclaw-gateway-部署) 章节。

---

## 🔧 OpenClaw Gateway 部署

OpenClaw Gateway 是你的自建后端，负责 Whisper 语音转写、GPT-5.5 摘要生成、邮件发送。

> 📖 详细配置请参阅：
> - [docs/openclaw-gateway-setup.md](docs/openclaw-gateway-setup.md) — Gateway 完整配置指南
> - [docs/openclaw-remote-connection.md](docs/openclaw-remote-connection.md) — 远程连接方案

### 方式一：Python 直接运行

```bash
# 1. 设置环境变量
export OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
export SMTP_USER=lhjjjk4@gmail.com
export SMTP_PASS="<Gmail 应用专用密码>"

# 2. 启动 Gateway
cd docs/
python gateway.py   # 参考 openclaw-gateway-setup.md 中的完整代码
```

### 方式二：Docker Compose

```yaml
# docker-compose.yml
version: "3.8"
services:
  openclaw-gateway:
    image: openclaw/gateway:latest
    ports:
      - "8080:8080"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - OPENAI_MODEL=gpt-5.5
      - SMTP_HOST=smtp.gmail.com
      - SMTP_PORT=587
      - SMTP_USER=${SMTP_USER}
      - SMTP_PASS=${SMTP_PASS}
      - EMAIL_TO=lhjjjk4@gmail.com
```

```bash
docker-compose up -d
```

### 方式三：远程访问（眼镜和电脑不在同一网络）

如果眼镜在会议室 WiFi，电脑在家/办公室，需建立隧道：

**推荐方案 — Cloudflare Tunnel（免费）**:
```bash
# 安装
winget install --id Cloudflare.cloudflared

# 创建隧道
cloudflared tunnel login
cloudflared tunnel create meeting-helper
cloudflared tunnel route dns meeting-helper meeting.yourdomain.com

# 配置文件 ~/.cloudflared/config.yml
# tunnel: <tunnel-id>
# ingress:
#   - hostname: meeting.yourdomain.com
#     service: ws://localhost:8080

# 启动
cloudflared tunnel run meeting-helper
```

眼镜端 Settings 输入：`wss://meeting.yourdomain.com/ws`

---

## 📱 手机 App 部署（未来 Rokid Max/AR 产品线）

`phone-app/` 预留给需要手机桥接的 Rokid Max/AR 产品线。

当使用 Rokid Max 时：
1. 在 Android 手机上运行此 App
2. 通过 **CXR-S SDK** (`com.rokid.cxr:cxr-service-bridge`) 与眼镜通信
3. 手机负责 WiFi/WebSocket 连接 OpenClaw Gateway

CXR-S SDK 集成需要：
```kotlin
// settings.gradle.kts
maven { url = uri("https://maven.rokid.com/repository/maven-public/") }

// build.gradle.kts
implementation("com.rokid.cxr:cxr-service-bridge:1.0-20250519.061355-45")
```

---

## 🧪 开发调试

### 屏幕镜像

使用 scrcpy 在电脑上查看眼镜显示：
```bash
# 安装
brew install scrcpy        # macOS
winget install Genymobile.scrcpy  # Windows

# 启动镜像
scrcpy
```

### 查看日志

```bash
# 过滤 Meeting Helper 相关日志
adb logcat -s MeetingHelper:*

# 查看 WebSocket 通信
adb logcat -s MeetingHelper/Gateway:*

# 查看音频模块
adb logcat -s MeetingHelper/Audio:*
```

### 运行单元测试

```bash
cd glasses-app
./gradlew testDebugUnitTest
```

测试覆盖 6 个模块 57 个用例：
- MessageProtocol 序列化/反序列化（17 tests）
- SessionManager 状态机生命周期（28 tests）
- SessionState 数据类等值性（16 tests）
- HudTheme 颜色常量（12 tests）
- AudioEncoder Base64 编码（9 tests）
- GatewayConnection 退避算法（11 tests）

---

## 🔄 自更新系统

Loader 启动链在每次启动时自动检查 GitHub Releases 更新：

```
LoaderActivity
  → UpdateChecker (GET /repos/.../releases/latest)
  → 有新版本? → CodeDownloader (下载 .dex)
  → DynamicLoader (DexClassLoader → MainActivity)
  → 无更新? → 加载本地 DEX 或内置 MainActivity
```

发布新版本时，在 GitHub Releases 中上传 `.dex` 资源文件即可。眼镜端无需重新侧载 APK。

---

## 📁 项目结构

```
meeting-helper-rokid/
├── README.md                              ← 本文件
├── glasses-app/                           ← 眼镜端 Android 应用
│   ├── build.gradle.kts                   ← Gradle 构建配置
│   ├── src/main/
│   │   ├── AndroidManifest.xml            ← 权限/Service/Receiver 注册
│   │   ├── res/values/strings.xml         ← 字符串资源
│   │   └── java/.../rokidopenclaw/
│   │       ├── RokidOpenClawApp.kt        ← Application: 通知渠道 + OkHttp 单例
│   │       ├── MainActivity.kt            ← 主 Activity: HUD + 手势 + 权限
│   │       ├── audio/
│   │       │   ├── AudioCaptureService.kt ← AudioRecord 16kHz/16bit/mono → Flow
│   │       │   └── AudioEncoder.kt        ← PCM → Base64 编码
│   │       ├── camera/CameraCapture.kt    ← CameraX (P2, stub)
│   │       ├── loader/                    ← 🔒 自更新系统 (完整保留)
│   │       │   ├── LoaderActivity.kt
│   │       │   ├── LoaderHudScreen.kt
│   │       │   ├── UpdateChecker.kt
│   │       │   ├── CodeDownloader.kt
│   │       │   └── DynamicLoader.kt
│   │       ├── network/
│   │       │   ├── ConnectionState.kt     ← DISCONNECTED/CONNECTING/.../ERROR
│   │       │   ├── GatewayClient.kt       ← OkHttp WebSocket 客户端
│   │       │   ├── GatewayConnection.kt   ← 指数退避重连包装器
│   │       │   └── MessageProtocol.kt     ← WebSocket 消息协议 + JSON
│   │       ├── service/
│   │       │   ├── MeetingForegroundService.kt ← 前台 Service
│   │       │   └── BootReceiver.kt             ← 开机自启广播
│   │       ├── session/
│   │       │   ├── SessionManager.kt      ← 会议生命周期状态机
│   │       │   └── SessionState.kt        ← Idle/Recording/Ending/Error
│   │       ├── tts/TtsPlayback.kt         ← TTS (P2, stub)
│   │       └── ui/
│   │           ├── MainViewModel.kt       ← 中心协调器
│   │           ├── hud/
│   │           │   ├── HudMainScreen.kt   ← 根 Composable 布局
│   │           │   ├── RecordingIndicator.kt ← REC 闪烁动画
│   │           │   ├── StatusBar.kt        ← 连接/电池状态栏
│   │           │   └── QueryResultOverlay.kt ← Q&A 浮层
│   │           ├── settings/
│   │           │   └── SettingsScreen.kt   ← Gateway URL 配置
│   │           └── theme/
│   │               └── HudTheme.kt        ← 绿黑单色 Material3 主题
│   └── src/test/                          ← 单元测试 (6 文件, 57 用例)
├── phone-app/                             ← 预留：手机伴侣 App
│   └── README.md
├── shared/                                ← 预留：组件间共享协议
│   └── README.md
├── docs/                                  ← 设计文档 + Gateway 配置
│   ├── system_design.md                   ← 系统架构设计
│   ├── class-diagram.mermaid              ← 类图
│   ├── sequence-diagram.mermaid           ← 时序图
│   ├── openclaw-gateway-setup.md          ← Gateway 配置指南 (GPT-5.5)
│   └── openclaw-remote-connection.md      ← 远程连接方案
└── .gitignore
```

---

## 📚 参考资源

- **Rokid 开发者文档:** https://developer.rokid.com/
- **rokid-glass-skill:** https://github.com/eikachiu/rokid-glass-skill
- **OpenClaw:** https://openclaw.ai
- **OpenAI API:** https://platform.openai.com/docs
- **Cloudflare Tunnel:** https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/
- **Gmail SMTP 配置:** https://myaccount.google.com/apppasswords

---

## 📄 License

MIT — build on it, hack it, make it yours.
