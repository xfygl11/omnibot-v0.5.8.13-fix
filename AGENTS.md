# Project Structure

This workspace contains two versions of the OmniBot project:

- `OmniBot-0.5.8.13/` — The patched v0.5.8.13 source (our fork, keystore 3-part split fix)
- `OmniBot-0.5.8.14/` — The upstream v0.5.8.14 source (for diff analysis)

Git push targets: OmniBot-0.5.8.13/ contents only (not the folder itself)

---

# Development Workflow

## 1. 变更工作流

每次代码变更前，必须先：
1. 切出新分支（命名规范：`YYMMDD-(feat|fix|chore)-xxxxx`）
2. 完成变更后，生成 **Change Report**（参见 `OmniBot-0.5.8.13/docs/CHANGE_REPORT_TEMPLATE.md`）

## 2. 版本发布流程

任何版本标签推送（`vX.Y.Z.W`）前，必须：
1. 完成 `RELEASE_APPROVAL_CHECKLIST.md` 中的全部检查项
2. 向用户展示变更报告
3. **等待用户明确批准**后才能执行 `git push origin v<X.Y.Z.W>`

发布命令（获批准后执行）：
```bash
cd /workspace/OmniBot-0.5.8.13
git add .
git commit -m "chore: bump version to v<X.Y.Z.W>"
git tag -a v<X.Y.Z.W> -m "Release v<X.Y.Z.W>"
git push origin v<X.Y.Z.W>
```

## 3. 关键配置文件

| 文件 | 说明 |
|------|------|
| `.github/workflows/release.yml` | Release APK 构建 + GitHub Releases 上传 |
| `.github/workflows/sync-to-cnb.yml` | CNB 同步到 CoolApp |
| `.github/workflows/ci.yml` | CI 验证 |
| `scripts/build-local-release.sh` | 本地构建脚本 |

## 4. 已知限制

- `APP_UPDATE_WORKER_URL` / `APP_UPDATE_WORKER_TOKEN` 为上游 Cloudflare Worker 凭据，本项目缺失，R2 上传步骤会失败但**不影响 APK 构建**
- GitHub token 每 30 天需刷新一次（使用 `git credential fill` 获取）

