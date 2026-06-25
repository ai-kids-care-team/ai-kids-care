# Security Findings — backend (Spring Boot / Java 21) — VERIFIED

Angle: security · Component: backend · Reviewer: security-analyst · Verifier: finding-verifier (adversarial static, standard tier)
Method note: 本机无 JVM/容器 → 静态复核（每条 1 票，默认假设假阳性并尝试反驳）。仅复核 severity ∈ {high, medium}；low/info 标 skipped（未复核）。

---

```yaml
- id: SEC-01
  angle: security
  component: backend
  severity: medium
  title: Tenant CRUD mutations (camera streams, rooms, classes, event reviews) are not written to audit_logs as SUCCESS
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败。grep CameraStreamService 全文 auditWriter|.record( = 0 命中；EventReviewService 仅有
      @PreAuthorize（45/81/95/105）无任何 audit 调用 → create/update/confirm 写路径确无 SUCCESS 审计记录。
      AuditAction 枚举确无 tenant CREATE/UPDATE/DELETE。位置与描述一致。属真实取证/合规缺口（非 demo 路径）。
- id: SEC-02
  angle: security
  component: backend
  severity: medium
  title: AI service reads decrypted camera-stream credentials with no audit trail and no tenant scoping
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败。CameraStreamService.getStreamCredential:128-146 实为 repository.findById(id)（无 kindergarten
      过滤）+ AesGcmCryptoUtil.decrypt → 明文密码；该 service 全文无 auditWriter.record。StreamCredentialController
      仅 @Hidden + HTTP 层 Bearer/ROLE_AI_SERVICE，方法不叠加 @PreAuthorize。tenant-skip 本身是 OQ-3=B 既定设计
      （AI 为平台基建，不判 refuted）；但 finding 真正主张的「凭据解密读无审计 + token 无轮换 + 可枚举」是已核实的
      残留缺口，成立。位置/证据对得上。
```

```yaml
- id: SEC-03
  severity: low
  verification: { verdict: skipped, note: "low — 未复核（超出 high+medium 范围）" }
```

```yaml
- id: SEC-04
  severity: low
  verification: { verdict: skipped, note: "low — 未复核" }
```

```yaml
- id: SEC-05
  severity: low
  verification: { verdict: skipped, note: "low — 未复核" }
```

```yaml
- id: SEC-06
  severity: info
  verification: { verdict: skipped, note: "info — 未复核" }
```

```yaml
- id: SEC-07
  severity: info
  verification: { verdict: skipped, note: "info — 未复核（且提出者自承 SAFE/regression-only）" }
```

```yaml
- id: SEC-08
  severity: info
  verification: { verdict: skipped, note: "info — 未复核" }
```

---

## 复核备注
- SEC-01 / SEC-02 均为 confirmed。两条共享同一根因（审计写入未覆盖 tenant 写路径与凭据读路径），可在报告中合并叙述但分别定级 medium。
- 原文件「Explicitly checked and found OK」反误报清单（404-not-403 / 内部鉴权常时间比较 / AdminBootstrapRunner 非后门 / 登录限流哈希化）抽样合理，未在本轮范围内逐条复跑。
