# OpenClaw Gateway — 会议助手配置指南

> 配置 OpenClaw Gateway 以支持 Rokid Meeting Helper 的 OpenAI 后端（GPT-5.5 + Whisper）

---

## 1. 架构总览

```
Rokid Glasses (WiFi)                    OpenClaw Gateway                      OpenAI API
┌──────────────────┐    WebSocket      ┌──────────────────────┐    HTTP       ┌─────────────┐
│  audio_frame ────┼──────────────────→│  Whisper STT         │──────────────→│  Whisper API │
│  meeting_start   │                   │  (实时流式转写)       │               └─────────────┘
│  meeting_end ────┼──────────────────→│                      │               ┌─────────────┐
│  quick_query ────┼──────────────────→│  GPT-5.5             │──────────────→│  GPT-5.5 API │
│                  │                   │  (摘要/搜索/Q&A)      │               └─────────────┘
│                  │                   │                      │               ┌─────────────┐
│                  │                   │  create_document     │──────────────→│  SMTP/Gmail  │
│                  │   ←───────────────│  ──→ lhjjjk4@...     │               └─────────────┘
│  summary_sent ←──┼──────────────────│                      │
│  query_result ←──┼──────────────────│                      │
│  error         ←──┼──────────────────│                      │
└──────────────────┘                   └──────────────────────┘
```

---

## 2. WebSocket 协议定义

### 2.1 连接参数

```
URL: ws://<gateway-host>:8080/ws
Query Params: ?session_id=<uuid>&device_id=<rokid_serial>
```

### 2.2 入站消息（Glasses → Gateway）

#### `audio_frame` — PCM 音频帧

```json
{
  "type": "audio_frame",
  "data": "<Base64 encoded PCM 16kHz/16bit/mono>",
  "timestamp": 1717027200123
}
```
- 频率：每 100ms 一帧
- 每帧大小：3200 bytes PCM → ~4268 bytes Base64
- 处理：追加到 Whisper 实时转写缓冲区

#### `meeting_start` — 会议开始

```json
{
  "type": "meeting_start",
  "timestamp": 1717027200000
}
```
- 处理：初始化转录缓冲区、会议元数据

#### `meeting_end` — 会议结束

```json
{
  "type": "meeting_end",
  "timestamp": 1717027260000,
  "durationSeconds": 600
}
```
- 处理：触发摘要生成 → 邮件发送 → 返回 summary_sent

#### `quick_query` — 快速问答

```json
{
  "type": "quick_query",
  "timestamp": 1717027230000
}
```
- 处理：取最近 30s 转录 → GPT-4o 搜索/压缩 → 返回 query_result

### 2.3 出站消息（Gateway → Glasses）

#### `transcription_delta` — 增量转写（仅日志，不显示）

```json
{
  "type": "transcription_delta",
  "text": "那么关于 Q3 的营收数字...",
  "isFinal": false
}
```

#### `query_result` — Q&A 压缩要点

```json
{
  "type": "query_result",
  "items": [
    "Q3 营收同比增长 12%",
    "净利润 ¥450M",
    "竞品市占率下降 3%"
  ]
}
```
- 最多 3 条，每条 ≤ 40 字符

#### `summary_sent` — 摘要发送确认

```json
{
  "type": "summary_sent",
  "documentId": "doc_abc123"
}
```

#### `error` — 错误消息

```json
{
  "type": "error",
  "code": "STT_001",
  "message": "Whisper API 暂时不可用，正在重试..."
}
```

---

## 3. Gateway 核心配置

### 3.1 环境变量

```bash
# .env 或环境变量配置

# ── OpenAI ──────────────────────────────────
OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
OPENAI_MODEL=gpt-5.5                          # 摘要 & Q&A 模型
OPENAI_WHISPER_MODEL=whisper-1               # 语音转写模型

# ── Gateway ─────────────────────────────────
GATEWAY_HOST=0.0.0.0
GATEWAY_PORT=8080
GATEWAY_WS_PATH=/ws

# ── 邮件 ────────────────────────────────────
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=lhjjjk4@gmail.com
SMTP_PASS=<应用专用密码>
EMAIL_TO=lhjjjk4@gmail.com

# ── 搜索（可选）─────────────────────────────
# 使用 GPT-4o 内置的 web_search tool 或配置 Bing/Google API
SEARCH_PROVIDER=openai                      # openai | bing | google
BING_API_KEY=xxxxxxxxxxxx                   # 仅当 SEARCH_PROVIDER=bing
```

### 3.2 Gateway 伪代码（核心逻辑）

```python
# gateway.py — OpenClaw Gateway 会议助手核心逻辑

import asyncio
import json
import base64
import time
from collections import deque
from openai import AsyncOpenAI

client = AsyncOpenAI(api_key=os.environ["OPENAI_API_KEY"])

class MeetingSession:
    def __init__(self, session_id: str):
        self.session_id = session_id
        self.transcription_buffer: deque[dict] = deque(maxlen=600)  # ~10min
        self.full_transcript: list[str] = []
        self.is_active = False
        self.start_time = None
        self.audio_chunks: list[bytes] = []
        self.last_stt_time = 0

    def get_recent_transcript(self, seconds: int = 30) -> str:
        """获取最近 N 秒的转写文本"""
        cutoff = time.time() - seconds
        recent = [
            t["text"] for t in self.transcription_buffer
            if t["timestamp"] > cutoff
        ]
        return " ".join(recent)

    def append_transcription(self, text: str, is_final: bool):
        entry = {"text": text, "is_final": is_final, "timestamp": time.time()}
        self.transcription_buffer.append(entry)
        if is_final:
            self.full_transcript.append(text)


# ── 活跃会话管理 ──
sessions: dict[str, MeetingSession] = {}


# ── WebSocket 消息路由 ──

async def handle_message(ws, raw: str):
    msg = json.loads(raw)
    msg_type = msg.get("type")
    
    # 从连接上下文获取 session
    session = get_session_from_ws(ws)

    if msg_type == "audio_frame":
        await handle_audio_frame(session, msg)
    elif msg_type == "meeting_start":
        await handle_meeting_start(session, msg)
    elif msg_type == "meeting_end":
        await handle_meeting_end(session, ws, msg)
    elif msg_type == "quick_query":
        await handle_quick_query(session, ws, msg)


# ── 消息处理器 ──

async def handle_audio_frame(session: MeetingSession, msg: dict):
    """处理音频帧：解码 → 发送到 Whisper → 追加到转录缓存"""
    try:
        pcm_bytes = base64.b64decode(msg["data"])
        session.audio_chunks.append(pcm_bytes)

        # 每 2 秒调用一次 Whisper（攒够 20 帧）
        if len(session.audio_chunks) >= 20:
            await transcribe_chunks(session)
    except Exception as e:
        log.error(f"Audio frame error: {e}")


async def transcribe_chunks(session: MeetingSession):
    """调用 Whisper API 转写攒够的音频 chunk"""
    if not session.audio_chunks:
        return

    combined = b"".join(session.audio_chunks)
    session.audio_chunks.clear()

    # Whisper API 需要文件格式；将 PCM 封装为 WAV
    wav_bytes = pcm_to_wav(combined, sample_rate=16000, channels=1, bits=16)

    try:
        transcript = await client.audio.transcriptions.create(
            model="whisper-1",
            file=("audio.wav", wav_bytes, "audio/wav"),
            language="zh",                           # 中文为主，Whisper 可自动检测
            response_format="verbose_json",
            timestamp_granularities=["segment"]
        )

        text = transcript.text.strip()
        if text:
            session.append_transcription(text, is_final=True)
            # 发送增量转写给眼镜端（仅日志用途，HUD 不显示）
            await ws_send_json(session.ws, {
                "type": "transcription_delta",
                "text": text,
                "isFinal": True
            })

    except Exception as e:
        log.error(f"Whisper transcription failed: {e}")
        await ws_send_json(session.ws, {
            "type": "error",
            "code": "STT_001",
            "message": f"语音转写暂时失败: {str(e)[:100]}"
        })


async def handle_meeting_start(session: MeetingSession, msg: dict):
    """开始会议：标记活跃，初始化缓冲区"""
    session.is_active = True
    session.start_time = msg.get("timestamp", time.time() * 1000)
    session.transcription_buffer.clear()
    session.full_transcript.clear()
    session.audio_chunks.clear()
    log.info(f"Meeting started: {session.session_id}")


async def handle_meeting_end(session: MeetingSession, ws, msg: dict):
    """结束会议：flush 剩余音频 → 生成摘要 → 发送邮件"""
    session.is_active = False

    # 1. Flush 剩余音频
    if session.audio_chunks:
        await transcribe_chunks(session)

    # 2. 构建完整转录文本
    full_text = "\n".join(session.full_transcript)
    duration_minutes = msg.get("durationSeconds", 0) // 60

    if not full_text.strip():
        await ws_send_json(ws, {
            "type": "error",
            "code": "SUMMARY_001",
            "message": "没有可用的转录文本，无法生成摘要"
        })
        return

    # 3. 调用 GPT-5.5 生成结构化摘要
    summary_md = await generate_summary(full_text, duration_minutes)

    # 4. 发送邮件
    success, doc_id = await send_summary_email(
        to="lhjjjk4@gmail.com",
        subject=f"会议纪要 - {time.strftime('%Y-%m-%d %H:%M')}",
        body=summary_md
    )

    # 5. 返回确认
    if success:
        await ws_send_json(ws, {
            "type": "summary_sent",
            "documentId": doc_id
        })
    else:
        await ws_send_json(ws, {
            "type": "error",
            "code": "EMAIL_001",
            "message": "摘要已生成但邮件发送失败，请检查邮箱配置"
        })


async def handle_quick_query(session: MeetingSession, ws, msg: dict):
    """快速问答：取最近 30s 转录 → GPT-5.5 搜索 → 压缩要点"""
    if not session.is_active:
        return

    # 1. 获取最近 30s 的转录
    query_context = session.get_recent_transcript(seconds=30)
    if not query_context.strip():
        await ws_send_json(ws, {
            "type": "query_result",
            "items": ["（最近 30 秒内没有检测到有效对话）"]
        })
        return

    # 2. 调用 GPT-4o — 搜索 + 压缩
    try:
        response = await client.chat.completions.create(
            model="gpt-5.5",
            messages=[
                {
                    "role": "system",
                    "content": (
                        "你是会议数据助理。用户正在会议中，刚刚讨论了以下话题。"
                        "请基于这段对话内容，联网搜索相关数据，"
                        "然后用极简格式返回 ≤3 条核心发现。"
                        "每条 ≤40 个中文字符，格式为纯要点，不带编号。"
                        "如果话题中没有需要查证的数据，可以基于常识给出简短反馈。"
                    )
                },
                {
                    "role": "user",
                    "content": f"会议中讨论的内容：\n{query_context}"
                }
            ],
            tools=[{
                "type": "function",
                "function": {
                    "name": "web_search",
                    "description": "搜索互联网获取最新数据",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "query": {"type": "string", "description": "搜索关键词"}
                        },
                        "required": ["query"]
                    }
                }
            }],
            tool_choice="auto",
            max_tokens=200,
            temperature=0.3
        )

        # 解析响应为要点列表
        raw = response.choices[0].message.content.strip()
        items = [line.strip().lstrip("-•·").strip()
                 for line in raw.split("\n")
                 if line.strip()][:3]

        if not items:
            items = [raw[:40]]

        await ws_send_json(ws, {
            "type": "query_result",
            "items": items
        })

    except Exception as e:
        log.error(f"Quick query failed: {e}")
        await ws_send_json(ws, {
            "type": "error",
            "code": "QUERY_001",
            "message": f"搜索暂时不可用: {str(e)[:100]}"
        })


# ── 摘要生成 ──

SUMMARY_PROMPT = """你是资深会议记录助手。请根据会议转录文本生成结构化 Markdown 摘要。

## 格式要求
严格按以下 Markdown 模板输出：

# 📋 会议纪要

**日期**: {date}
**时长**: {duration} 分钟

## 一、讨论要点
- （每条 1-2 句，列出 3-8 个关键讨论话题）

## 二、决策事项
- （已达成一致的决定，如有）

## 三、待办事项
| 事项 | 负责人 | 截止日期 |
|------|--------|----------|
| xxx  | （从上下文推断，无法推断则留空）| （从上下文推断）|

## 四、关键数据
- （涉及的具体数字、百分比、金额等）

## 五、下次会议建议
- （如有提及）

---

*由 Rokid Meeting Helper 自动生成*"""


async def generate_summary(transcript: str, duration_min: int) -> str:
    """调用 GPT-4o 生成结构化会议摘要"""
    from datetime import datetime

    prompt = SUMMARY_PROMPT.format(
        date=datetime.now().strftime("%Y年%m月%d日 %H:%M"),
        duration=duration_min
    )

    try:
        response = await client.chat.completions.create(
            model="gpt-5.5",
            messages=[
                {"role": "system", "content": prompt},
                {"role": "user", "content": f"会议转录文本：\n\n{transcript}"}
            ],
            max_tokens=2000,
            temperature=0.3
        )
        return response.choices[0].message.content

    except Exception as e:
        log.error(f"Summary generation failed: {e}")
        return f"# 会议纪要\n\n*摘要生成失败: {e}*\n\n## 原始转录\n\n{transcript[:500]}..."


# ── 邮件发送 ──

async def send_summary_email(to: str, subject: str, body: str) -> tuple[bool, str]:
    """通过 SMTP 发送摘要邮件"""
    import smtplib
    from email.mime.text import MIMEText
    from email.mime.multipart import MIMEMultipart
    import uuid

    doc_id = f"doc_{uuid.uuid4().hex[:12]}"

    try:
        msg = MIMEMultipart("alternative")
        msg["Subject"] = subject
        msg["From"] = os.environ["SMTP_USER"]
        msg["To"] = to

        msg.attach(MIMEText(body, "markdown", "utf-8"))

        with smtplib.SMTP(os.environ["SMTP_HOST"], int(os.environ["SMTP_PORT"])) as server:
            server.starttls()
            server.login(os.environ["SMTP_USER"], os.environ["SMTP_PASS"])
            server.sendmail(os.environ["SMTP_USER"], [to], msg.as_string())

        log.info(f"Summary email sent to {to} — {doc_id}")
        return True, doc_id

    except Exception as e:
        log.error(f"Email send failed: {e}")
        return False, doc_id


# ── 工具函数 ──

def pcm_to_wav(pcm_data: bytes, sample_rate=16000, channels=1, bits=16) -> bytes:
    """PCM 裸流 → WAV 格式（Whisper API 需要）"""
    import struct
    import io

    byte_rate = sample_rate * channels * bits // 8
    block_align = channels * bits // 8

    buffer = io.BytesIO()
    buffer.write(b"RIFF")
    buffer.write(struct.pack("<I", 36 + len(pcm_data)))
    buffer.write(b"WAVE")
    buffer.write(b"fmt ")
    buffer.write(struct.pack("<I", 16))           # chunk size
    buffer.write(struct.pack("<H", 1))            # PCM
    buffer.write(struct.pack("<H", channels))
    buffer.write(struct.pack("<I", sample_rate))
    buffer.write(struct.pack("<I", byte_rate))
    buffer.write(struct.pack("<H", block_align))
    buffer.write(struct.pack("<H", bits))
    buffer.write(b"data")
    buffer.write(struct.pack("<I", len(pcm_data)))
    buffer.write(pcm_data)

    return buffer.getvalue()
```

---

## 4. Docker Compose 部署（推荐）

```yaml
# docker-compose.yml

version: "3.8"

services:
  openclaw-gateway:
    image: openclaw/gateway:latest            # 或你的 Gateway 镜像
    container_name: openclaw-gateway
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - OPENAI_MODEL=gpt-5.5
      - GATEWAY_HOST=0.0.0.0
      - GATEWAY_PORT=8080
      - SMTP_HOST=smtp.gmail.com
      - SMTP_PORT=587
      - SMTP_USER=${SMTP_USER}
      - SMTP_PASS=${SMTP_PASS}
      - EMAIL_TO=lhjjjk4@gmail.com
    volumes:
      - ./sessions:/data/sessions          # 会话持久化
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

```bash
# 启动
export OPENAI_API_KEY=sk-xxxxx
export SMTP_USER=lhjjjk4@gmail.com
export SMTP_PASS=<应用专用密码>
docker-compose up -d
```

---

## 5. 关键参数调优

### 5.1 Whisper 转写

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| 模型 | `whisper-1` | OpenAI 托管，响应快 |
| 语言 | `zh` 或不指定 | 中文为主；不指定则自动检测 |
| 批量帧数 | 20 帧 (2s) | 攒够 2s 音频再调 API，减少调用次数 |
| 响应格式 | `verbose_json` | 获取时间戳用于 debug |
| 重试策略 | 3 次，指数退避 | 避免 API 限流 |

### 5.2 GPT-4o 摘要

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| 模型 | `gpt-5.5` | 性价比最优 |
| max_tokens | 2000 | 足够生成完整会议纪要 |
| temperature | 0.3 | 低温度保证输出稳定 |
| 输入截断 | 前 32000 tokens | 约 1 小时会议内容 |

### 5.3 GPT-4o 快速问答

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| 模型 | `gpt-5.5` | 搜索 + 压缩 |
| max_tokens | 200 | 仅供 3 行 HUD 显示 |
| temperature | 0.3 | 确保数据准确 |
| Web Search | `tool_choice: "auto"` | 自动判断是否需要搜索 |

---

## 6. 邮件配置（Gmail）

在 Gmail 中使用应用专用密码：

1. 前往 https://myaccount.google.com/security
2. 开启「两步验证」
3. 生成「应用专用密码」→ 选择「邮件」→ 选择「其他」
4. 将生成的 16 位密码填入 `SMTP_PASS`

---

## 7. 自签名证书（wss:// 可选）

如果眼镜和 Gateway 在同一局域网，`ws://` 即可。如需 `wss://`：

```bash
# 生成自签名证书（局域网用）
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout gateway.key \
  -out gateway.crt \
  -subj "/CN=192.168.1.100"
```

然后在 Gateway 配置中加载证书。眼镜端需将 Gateway URL 改为 `wss://192.168.1.100:8080/ws`。

---

## 8. 快速验证清单

部署完成后，用 `websocat` 或浏览器控制台验证：

```bash
# 1. 安装 websocat（一次性）
cargo install websocat   # 或 brew install websocat

# 2. 测试连接
websocat ws://localhost:8080/ws

# 3. 发送 meeting_start
> {"type":"meeting_start","timestamp":1717027200000}

# 4. 发送音频帧
> {"type":"audio_frame","data":"AAAA...","timestamp":1717027200100}

# 5. 发送 meeting_end
> {"type":"meeting_end","timestamp":1717027260000,"durationSeconds":60}

# 预期响应：
# ← {"type":"summary_sent","documentId":"doc_xxx"}
```

---

## 9. 故障排查

| 现象 | 可能原因 | 解决 |
|------|---------|------|
| Gateway 连接不上 | 防火墙/端口未开放 | 检查 `iptables` / 安全组，确认端口 8080 开放 |
| Whisper 返回空 | 音频帧太小/静音 | 增加攒帧数（20→30），检查 PCM 格式 |
| 摘要邮件未收到 | Gmail SMTP 拒绝 | 确认使用了应用专用密码而非普通密码 |
| quick_query 无结果 | 30s 内无有效对话 | 说话后等待 3-5s 再双击 |
| WebSocket 频繁断连 | 网络不稳定 | 眼镜端已内置指数退避重连，无需额外配置 |
