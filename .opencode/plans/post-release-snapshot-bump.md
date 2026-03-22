# Post-release snapshot bump plan

## Goal

After a Maven Central release succeeds, automatically prepare the repository for the next development cycle by bumping `pom.xml` to the next minor snapshot version.

Examples:

- `0.1.0` release -> `0.2.0-SNAPSHOT`
- `v0.1.0` tag -> normalize to `0.1.0`, then bump to `0.2.0-SNAPSHOT`

Use `-SNAPSHOT`, not `.SNAPSHOT`.

## Current context

- The release workflow is `.github/workflows/mvn-release.yml`.
- The workflow currently publishes a tagged release to Maven Central.
- The workflow already strips a leading `v` when setting the release version with:

```yaml
- name: Set version
  shell: bash
  run: mvn versions:set -DnewVersion=${GITHUB_REF_NAME#v}
```

- The project is currently on `0.2.0-SNAPSHOT` in `pom.xml`.
- The default branch in repository metadata is `master`.

## Desired behavior

Only after the Maven publish job completes successfully:

1. Check out the default branch (`master`), not the tag ref.
2. Derive the released version from the GitHub release tag.
3. Compute the next minor development version.
4. Update `pom.xml` to that next `-SNAPSHOT` version.
5. Persist the change either by:
   - opening an automated PR to `master` (recommended), or
   - pushing a commit directly to `master` if branch policy allows it.

## Recommended approach

Implement this as a second GitHub Actions job after publish, not as extra steps at the end of the publish job.

Reasoning:

- The release job runs from the tagged commit, not from the moving branch tip.
- A separate job makes success/failure boundaries clearer.
- The next-version preparation should not run if publishing fails.
- It is easier to switch between direct-push and PR-based automation.

## Proposed workflow structure

Keep the existing publish job, then add:

```yaml
prepare-next-snapshot:
  needs: publish
  if: ${{ success() }}
  runs-on: ubuntu-latest
```

Inside that job:

1. Check out `master` explicitly.
2. Set up JDK and Maven cache.
3. Parse the tag and compute the next version.
4. Run `mvn versions:set -DnewVersion="$NEXT_VERSION" -DgenerateBackupPoms=false`.
5. Commit the change.
6. Either push directly or create a PR.

## Version computation logic

Use bash and normalize the tag first:

```bash
RELEASE_VERSION="${GITHUB_REF_NAME#v}"
IFS='.' read -r MAJOR MINOR PATCH <<< "$RELEASE_VERSION"
NEXT_VERSION="$MAJOR.$((MINOR + 1)).0-SNAPSHOT"
```

Expected outputs:

- `0.1.0` -> `0.2.0-SNAPSHOT`
- `0.2.0` -> `0.3.0-SNAPSHOT`
- `1.4.3` -> `1.5.0-SNAPSHOT`

This plan intentionally bumps the minor version and resets patch to `0`.

## Preferred delivery mode

Open an automated PR instead of pushing directly to `master`.

Why this is recommended:

- safer with branch protection
- preserves review visibility
- avoids requiring direct write access to `master`
- keeps release publication separate from follow-up development changes

Suggested PR content:

- title: `chore: bump version to <next-version>`
- body: note that the PR was created automatically after release publication

## Direct push alternative

Use only if the repository intentionally allows automation to update `master`.

Requirements:

- workflow permission `contents: write`
- branch protection must allow the action to push
- git author identity must be set in the workflow step before commit

Suggested commit message:

- `chore: bump version to <next-version>`

## GitHub Actions details to remember later

- The current workflow declares `permissions: contents: read`; this is enough for publishing, but not enough for committing back.
- If using a PR flow, add the permissions needed for PR creation.
- If using direct push, update permissions to at least `contents: write`.
- The snapshot bump job should run only after `publish` succeeds.
- The bump job must check out `master` explicitly instead of relying on the tag checkout.

Example checkout direction:

```yaml
- name: Checkout default branch
  uses: actions/checkout@v6
  with:
    ref: master
    fetch-depth: 0
```

## Maven details to remember later

Use:

```bash
mvn versions:set -DnewVersion="$NEXT_VERSION" -DgenerateBackupPoms=false
```

Why:

- updates the POM cleanly
- avoids creating `pom.xml.versionsBackup`

Optional follow-up:

- run `mvn -q validate` after the version bump before committing

## Optional documentation follow-up

If desired, the same automated change can also:

- ensure `CHANGELOG.md` has an active `Unreleased` section for the next cycle
- add a short note that development has moved to the new snapshot version

This is optional and should be kept separate from the core version bump if simplicity is preferred.

## Failure and safety rules

- Do not run the snapshot bump if Maven Central publication fails.
- Do not compute the next version from an unnormalized `v` tag.
- Do not use `.SNAPSHOT`; always use `-SNAPSHOT`.
- Do not assume the release job workspace is on `master`.
- Avoid direct push if branch protection is enabled unless explicitly intended.

## Recommended implementation order when revisiting this

1. Add a second job after `publish` in `.github/workflows/mvn-release.yml`.
2. Decide PR-based flow vs direct-push flow.
3. Add the required workflow permissions.
4. Implement version parsing and next-minor snapshot calculation.
5. Update the POM with `versions:set` and disable backup POM generation.
6. Add commit/PR creation logic.
7. Test with a dry run on a temporary tag or branch.

## Decision currently recommended

When this is implemented later, prefer:

- separate post-release job
- PR creation instead of direct push
- next version format `<major>.<minor+1>.0-SNAPSHOT`

## Not being done now

This plan is intentionally being saved for later implementation only. No automation for the post-release snapshot bump is being added right now.
