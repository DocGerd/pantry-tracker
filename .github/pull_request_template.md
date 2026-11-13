## What

<one-paragraph description>

Closes #<N>

<!-- Use `Closes #N` / `Fixes #N` / `Resolves #N` when this PR fully resolves the
     issue (auto-closes it on merge). Use `Refs #N` if it's only a partial step. -->

## Checklist

- [ ] Branched off `develop` as `<type>/<tracker-id>-<slug>` and PR'd with `--base develop` (`release/*` and `hotfix/*` follow the GitFlow rules in CONTRIBUTING.md)
- [ ] Linked an issue in the body (every PR links an issue)
- [ ] Tests added/updated and `./gradlew :app:test` passes locally
- [ ] `./gradlew :app:detekt` passes locally
- [ ] No skipped hooks (no `--no-verify`), no force-push, no direct push to `develop` or `main`
- [ ] CLAUDE.md / architecture docs updated if design assumptions changed
- [ ] Multi-agent PR review run; all review threads resolved
- [ ] Waiting for review + merge by the maintainer (only humans merge to `develop` or `main`)
