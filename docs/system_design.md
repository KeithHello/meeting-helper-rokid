# Rokid Meeting Helper — 系统架构设计文档

> **版本**: 1.1  
> **日期**: 2026-05-30  
> **目标**: Rokid AI Glasses (YodaOS-Sprite, Android Go, 480×640 单色绿 micro-LED)

---

## 1. 架构总览

```
┌──────────────────────────────────────────────────────────────────────┐
│                        LoaderActivity (入口, 保持不变)                 │
│  ┌─────────────────┐   ┌─────────────────┐   ┌──────────────────┐   │
│  │  UpdateChecker   │   │  CodeDownloader │   │  DynamicLoader   │   │
│  └─────────────────┘   └─────────────────┘   └──────────────────┘   │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ startActivity
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                         MainActivity                                  │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      MainViewModel                             │   │
│  │  ┌──────────────┐  ┌───────────────┐  ┌───────────────────┐  │   │
│  │  │ SessionMgr   │  │GatewayClient  │  │ AudioCaptureSvc  │  │   │
│  │  │ (会话生命周期) │  │ (WebSocket)   │  │ (AudioRecord)     │  │   │
│  │  └──────┬───────┘  └───────┬───────┘  └────────┬──────────┘  │   │
│  │         │                  │                    │             │   │
│  │         ▼                  ▼                    ▼             │   │
│  │  ┌──────────────────────────────────────────────────────────┐ │   │
│  │  │              MeetingForegroundService                     │ │   │
│  │  │              (前台服务, 保持进程存活)                       │ │   │
│  │  └──────────────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                     Compose UI Layer                           │   │
│  │  ┌────────────┐  ┌───────────┐  ┌────────┐  ┌─────────────┐ │   │
│  │  │HudMainScreen│  │StatusBar  │  │RecDot  │  │QueryOverlay │ │   │
│  │  └────────────┘  └───────────┘  └────────┘  └─────────────┘ │   │
│  └──────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
                               │ WebSocket (WiFi / Cloudflare Tunnel)
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      OpenClaw Gateway (自建服务器)                     │
│  Whisper STT → GPT-5.5 → 摘要生成 → SMTP 邮件                         │
└──────────────────────────────────────────────────────────────────────┘
```

## 2. 包结构

```
com.etdofresh.rokidopenclaw/
├── RokidOpenClawApp.kt          # Application: 通知渠道 + OkHttp单例
├── MainActivity.kt              # Activity: HUD + 手势 + 权限 + Service启动
├── audio/
│   ├── AudioCaptureService.kt   # AudioRecord 16kHz/16bit/mono → Flow<ByteArray>
│   └── AudioEncoder.kt          # PCM → Base64 编码
├── camera/
│   └── CameraCapture.kt         # CameraX (stub, P2)
├── loader/                      # 自更新系统 (完整保留)
│   ├── LoaderActivity.kt
│   ├── LoaderHudScreen.kt
│   ├── UpdateChecker.kt
│   ├── CodeDownloader.kt
│   └── DynamicLoader.kt
├── network/
│   ├── ConnectionState.kt       # DISCONNECTED/CONNECTING/CONNECTED/RECONNECTING/ERROR
│   ├── GatewayClient.kt         # OkHttp WebSocket 客户端
│   ├── GatewayConnection.kt     # 指数退避重连包装器
│   └── MessageProtocol.kt       # WebSocket 消息协议 + JSON序列化
├── service/
│   ├── MeetingForegroundService.kt  # 前台 Service (START_STICKY)
│   └── BootReceiver.kt              # 开机自启广播
├── session/
│   ├── SessionManager.kt        # 会议生命周期状态机
│   └── SessionState.kt          # Idle/Recording/Ending/Error
├── tts/
│   └── TtsPlayback.kt           # TTS (stub, P2)
└── ui/
    ├── MainViewModel.kt         # 中心协调器 (AndroidViewModel)
    ├── hud/
    │   ├── HudMainScreen.kt     # 根 Composable 布局
    │   ├── RecordingIndicator.kt # REC 闪烁动画
    │   ├── StatusBar.kt          # 连接/电池状态栏
    │   └── QueryResultOverlay.kt # Q&A 浮层 (3s 消失)
    ├── settings/
    │   └── SettingsScreen.kt     # Gateway URL 配置
    └── theme/
        └── HudTheme.kt          # 绿黑单色 Material3 主题
```

## 3. WebSocket 消息协议

| 方向 | 消息类型 | 用途 | 频率 |
|------|---------|------|------|
| 眼镜→Gateway | `audio_frame` | PCM Base64 音频帧 | 每 100ms |
| 眼镜→Gateway | `meeting_start` | 会议开始 | 一次 |
| 眼镜→Gateway | `meeting_end` | 会议结束 + 请求摘要 | 一次 |
| 眼镜→Gateway | `quick_query` | 近 30s 转录查询 | 按需 |
| Gateway→眼镜 | `transcription_delta` | Whisper 增量转写 (仅日志) | 2s/次 |
| Gateway→眼镜 | `query_result` | GPT-5.5 搜索压缩要点 (≤3行) | 按需 |
| Gateway→眼镜 | `summary_sent` | 摘要邮件已发送确认 | 一次 |
| Gateway→眼镜 | `error` | 错误反馈 | 按需 |

## 4. 关键技术决策

| 决策 | 理由 |
|------|------|
| 包名保持 `com.etdofresh.rokidopenclaw` | DynamicLoader 硬编码，零修改复用 |
| MVVM + StateFlow | Jetpack 标准，Compose 友好，单向数据流 |
| AudioRecord → Flow<ByteArray> | 解耦采集与推送，便于测试和替换 |
| OkHttp WebSocket | 成熟稳定，自动 ping/pong，支持 WSS |
| 指数退避重连 1s→30s | 标准恢复策略，避免网络风暴 |
| 转写文本不显示 HUD | P0 需求：眼镜只显示 REC 指示器 |
| Gateway 端维护 30s 滑动窗口 | 减轻眼镜内存压力 |

## 5. 状态管理

### HudUiState (单一数据源)
```
HudUiState
├── sessionState: SessionState (Idle | Recording | Ending | Error)
├── connectionState: ConnectionState
├── isRecording: Boolean
├── wifiEnabled: Boolean
├── batteryLevel: Int (0-100)
├── queryResult: QueryResultMessage?
├── summarySent: Boolean
└── errorMessage: String?
```

### SessionManager 状态机
```
Idle ──startMeeting()──▶ Recording ──endMeeting()──▶ Ending ──reset()──▶ Idle
  ▲                         │                         │
  │                         │ error()                 │ error()
  │                         ▼                         ▼
  └─────────reset()────── Error ◀──────────────────────┘
```

## 6. 部署架构

```
Rokid Glasses (WiFi/4G)          Cloudflare Tunnel         自建服务器
┌─────────────────────┐          ┌──────────────┐       ┌──────────────┐
│  Meeting Helper APK │──WSS──▶  │ tunnel       │─────▶ │  OpenClaw    │
│  ws://tunnel.xyz/ws │          │ (cloudflared)│       │  Gateway:8080│
└─────────────────────┘          └──────────────┘       │  ├ Whisper STT│
                                                        │  ├ GPT-5.5    │
                                                        │  └ SMTP邮件  │
                                                        └──────────────┘
```
