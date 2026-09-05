# Authenticated proxy alerts

Proxy plugin messages are not proof of a trusted sender. Grim now verifies an
HMAC-SHA256 signature before interpreting a forwarded alert as MiniMessage.
Malformed, unsigned, modified, expired and duplicate messages are ignored.

## Upgrade requirement

Upgrade every backend participating in alert sharing together. In each backend's
Grim configuration, set `alerts.proxy.secret` to the **same randomly generated
secret (at least 32 characters)**, alongside the existing `send` and `receive`
settings. Add this key manually if your translated configuration lacks it.
Keep the secret out of source control, client configuration and public logs.
Keep backend clocks synchronized (messages allow 60 seconds of clock skew).

An empty secret disables proxy alert sharing, even when send/receive are enabled.
Local alerts continue to work. Old unsigned proxy alerts are intentionally not
accepted: upgrading only some backends will interrupt cross-server alerts.
This does not replace firewalling backends or configuring proxy authentication.

Replay protection is per running receiver, holds at most 4096 accepted messages
within the validity window, and fails closed when full. Restarting a receiver
clears its replay cache. A compromised backend with the shared secret can still
sign messages; rotate the secret on all backends after suspected compromise.

## Verification

`ProxyAlertCodecTest` covers Unicode round trips, legacy unsigned messages,
tampering, wrong keys, replays, expiry, truncated/oversized frames and bounded
cache behavior. Before production rollout, test two backends through your actual
proxy, including cross-server alerts, mismatched secrets and reconnects.
