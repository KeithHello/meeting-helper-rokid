# OpenClaw Gateway — 远程连接配置指南

> **场景**: 你的电脑运行 OpenClaw Gateway，Rokid 眼镜在外部网络（非同一局域网）  
> **目标**: 让眼镜通过公网安全连接到你的电脑上的 Gateway

---

## 为什么不能直接连接？

你本地电脑的 IP 是内网地址（如 `192.168.x.x`），互联网上的设备无法直接访问。需要一条「隧道」将公网流量转发到你本地的 Gateway。

---

## 方案对比

| 方案 | 难度 | 费用 | 延迟 | 稳定性 | 推荐度 |
|------|------|------|------|--------|--------|
| **Cloudflare Tunnel** | ⭐ 极简 | 免费 | 低 | 极高 | ⭐⭐⭐⭐⭐ |
| Tailscale | ⭐⭐ 简单 | 免费 | 极低 | 极高 | ⭐⭐⭐⭐ |
| frp + VPS | ⭐⭐⭐⭐ 复杂 | ~$5/月 | 低 | 高 | ⭐⭐⭐ |
| ngrok | ⭐ 极简 | 免费(有限) | 中 | 中 | ⭐⭐ |

---

## 方案一：Cloudflare Tunnel（推荐）

### 原理
```
Rokid Glasses                   Cloudflare 全球网络              你的电脑
┌──────────────┐    WSS        ┌─────────────────────┐    TCP     ┌────────────┐
│ ws://xxx.xyz │──────────────▶│ cloudflared tunnel  │──────────▶│ Gateway    │
│              │◀──────────────│ (免费, 自动 HTTPS)   │◀──────────│ :8080      │
└──────────────┘               └─────────────────────┘           └────────────┘
```

### Step 1: 注册 Cloudflare + 域名

1. 注册 https://dash.cloudflare.com/sign-up （免费）
2. 准备一个域名，将 DNS 托管到 Cloudflare（支持任何域名，`.xyz` 约 $1/年）

### Step 2: 安装 cloudflared

**Windows (PowerShell 管理员)**:
```powershell
winget install --id Cloudflare.cloudflared
```

**macOS**:
```bash
brew install cloudflared
```

**Linux**:
```bash
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o cloudflared
chmod +x cloudflared
sudo mv cloudflared /usr/local/bin/
```

### Step 3: 认证

```bash
cloudflared tunnel login
```
会自动打开浏览器 → 选择你的域名 → 授权。

### Step 4: 创建隧道

```bash
# 创建隧道
cloudflared tunnel create meeting-helper

# 记录输出的 Tunnel ID，如: abc12345-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

### Step 5: 配置隧道

创建配置文件 `~/.cloudflared/config.yml`:

```yaml
tunnel: <你的 Tunnel ID>
credentials-file: C:\Users\<用户名>\.cloudflared\<Tunnel ID>.json

ingress:
  # 将 meeting.yourdomain.com 转发到本地 8080
  - hostname: meeting.yourdomain.com
    service: ws://localhost:8080
  # 默认拒绝其他请求
  - service: http_status:404
```

> ⚠️ 注意：使用 `ws://localhost:8080` 非 `http://`，因为 Gateway 是 WebSocket 服务。

### Step 6: DNS 配置

```bash
# 创建 CNAME 记录将域名指向隧道
cloudflared tunnel route dns meeting-helper meeting.yourdomain.com
```

### Step 7: 启动隧道

```bash
# 前台运行（测试用）
cloudflared tunnel run meeting-helper

# 安装为系统服务（开机自启）
cloudflared service install
```

### Step 8: 眼镜端配置

在眼镜的 Settings 中设置 Gateway URL 为：

```
wss://meeting.yourdomain.com/ws
```

Cloudflare Tunnel 自动提供 HTTPS/WSS，无需配置证书。

---

## 方案二：Tailscale（点对点 VPN）

### 原理
```
Rokid Glasses (安装 Tailscale)         你的电脑 (安装 Tailscale)
┌──────────────────────────┐           ┌─────────────────────┐
│ Tailscale IP: 100.x.x.x  │───WireGuard──▶│ Tailscale IP: 100.x.x.x│
└──────────────────────────┘           │ Gateway :8080       │
                                       └─────────────────────┘
```

### 优缺点
- ✅ 延迟极低（直连，不经过中继）
- ✅ 免费（个人使用）
- ✅ 自动 WireGuard 加密
- ❌ 眼镜端需要安装 Tailscale APK（Rokid Glasses = Android，可以直接装）

### Step 1: 安装 Tailscale

- 电脑：https://tailscale.com/download
- Rokid 眼镜：通过 ADB 侧载 Tailscale APK，或从 https://tailscale.com/download/android 下载

### Step 2: 登录同一账号

电脑和眼镜都登录同一个 Tailscale 账号。

### Step 3: 获取电脑的 Tailscale IP

```bash
tailscale ip -4
# 输出示例: 100.64.0.5
```

### Step 4: 眼镜端配置

在眼镜 Settings 中设置 Gateway URL：

```
ws://100.64.0.5:8080/ws
```

---

## 方案三：frp + 轻量 VPS

适合有自己 VPS 的用户。在 VPS 上运行 frps（服务端），本地电脑运行 frpc（客户端）。

### frpc.ini（本地电脑）

```ini
[common]
server_addr = <你的VPS公网IP>
server_port = 7000

[meeting-helper-ws]
type = tcp
local_ip = 127.0.0.1
local_port = 8080
remote_port = 8080
```

眼镜端 URL：`ws://<VPS公网IP>:8080/ws`

---

## 启动与验证

### 1. 先启动 OpenClaw Gateway

```bash
cd ~/openclaw-gateway
export OPENAI_API_KEY=sk-xxxxx
python gateway.py
# 或 docker-compose up -d
```

### 2. 再启动隧道

```bash
# Cloudflare Tunnel
cloudflared tunnel run meeting-helper

# 或 Tailscale（已在后台运行，无需额外操作）
```

### 3. 测试连通性

在你的手机/另一台电脑上测试：

```bash
# 安装 websocat
brew install websocat     # macOS
# 或 winget install vi/websocat    # Windows

# 测试连接（Cloudflare Tunnel）
websocat wss://meeting.yourdomain.com/ws

# 测试连接（Tailscale）
websocat ws://100.64.0.5:8080/ws
```

连接成功后发送测试消息：
```json
{"type":"meeting_start","timestamp":1717027200000}
```

### 4. 在 Rokid 眼镜上配置

打开眼镜上的 Meeting Helper → Settings → 输入你的公网 URL → Save。

---

## 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| WSS 连接失败 | `service: http://` 而非 `ws://` | config.yml 改用 `service: ws://localhost:8080` |
| 隧道启动但无法访问 | 防火墙阻止 8080 | Windows: 允许 Java/Python 通过防火墙; macOS: 系统设置→网络→防火墙 |
| Tailscale 连接慢 | 使用了 DERP 中继 | 确保至少一端有公网 IP 实现直连 |
| 眼镜 WiFi 不稳定 | 会议室 WiFi 信号弱 | 使用眼镜的 4G 热点模式（如果支持） |
| 频繁断连 | 网络切换导致 WebSocket 断开 | 眼镜端已内置指数退避重连，无需额外处理 |
