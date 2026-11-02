# Development and GitHub Workflow

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | Stable, production-ready code only. No direct commits |
| `DEV` | Integration branch. Every phase is committed and pushed here |
| `feature/<phase>-<topic>` | Optional short-lived branches for large phases, merged into `DEV` |

## Per-phase loop

1. `git status` and `git pull --ff-only` on `DEV`. Never overwrite uncommitted work.
2. Implement only the current phase.
3. Run the relevant builds and tests (`./mvnw verify`, `npm run build`, `npm test`).
4. Review the diff (`git diff --stat`, then the full diff) and check that no secrets are staged.
5. Update `PROJECT_PROGRESS.md` and `README.md`.
6. Commit with the phase prefix, for example `phase-4: jwt authentication`.
7. `git push origin DEV`. A push is only reported as done after it succeeds.
8. When a milestone is stable, open a pull request from `DEV` to `main`.

Force-pushing is never done without the owner's explicit permission.

## Secret hygiene

- Real values live only in `.env` files or the hosting environment, both ignored by git.
- `.env.example` lists every variable with placeholders.
- Before each commit: `git diff --cached | grep -iE "secret|password|rzp_live"` should return nothing sensitive.
- If a secret is ever committed, rotate it immediately. Removing it from history is not enough.
