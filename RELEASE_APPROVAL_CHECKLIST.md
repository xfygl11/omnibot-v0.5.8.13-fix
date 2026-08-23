# Release 发布审批清单

## 概述

在将版本标签推送到 GitHub 之前，必须完成以下检查并获得用户确认。

---

## 发布前检查清单

### 代码变更
- [ ] 所有变更已提交到本地分支
- [ ] 变更报告已生成（参见 `docs/CHANGE_REPORT_TEMPLATE.md`）
- [ ] 无未提交的临时文件（`.env`、`release.jks` 等敏感文件）

### 构建验证
- [ ] 本地构建测试通过（如有条件）
- [ ] CI 流水线通过（如有手动触发选项）

### 版本信息
- [ ] 版本号符合语义化规范（如 `v0.5.9.0`）
- [ ] 版本号已在 `app/build.gradle.kts` 中更新
- [ ] 版本号已在 `webchat/package.json` 中更新（如适用）

### 安全审查
- [ ] 确认无硬编码密钥或凭据
- [ ] 确认 `.gitignore` 包含所有敏感文件
- [ ] 确认 GitHub Secrets 已正确配置（keystore 3-part、CNB_TOKEN 等）

### 文档更新
- [ ] CHANGELOG 已更新（如项目有维护习惯）
- [ ] 发布说明已准备（GitHub Release Notes）

---

## 用户确认步骤

请阅读上方变更报告后，确认以下问题：

1. **变更是否必要？** [是 / 否]
2. **是否有风险项需要额外关注？** [无 / 有，详见备注]
3. **是否批准推送版本标签？** [批准 / 修改后再推]

---

## 发布命令（获批准后执行）

```bash
# 在 OmniBot-0.5.8.13/ 目录内执行
git add .
git commit -m "chore: bump version to v<X.Y.Z.W>"

git tag -a v<X.Y.Z.W> -m "Release v<X.Y.Z.W>"
git push origin v<X.Y.Z.W>
```

推送后：
1. GitHub Actions 自动触发 `release.yml`
2. APK 构建完成后上传到 GitHub Releases
3. CNB 同步到 CoolApp（如配置了 CNB_TOKEN）

---

## 注意事项

- **不要**在未获用户明确批准前执行 push
- **不要**在推送前清理任何未备份的本地分支
- 如遇 CI 失败，先查看日志再决定是否需要修复
