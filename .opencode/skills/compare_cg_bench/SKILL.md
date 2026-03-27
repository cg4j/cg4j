---
name: compare_cg_bench
description: Compare local master and current feature branch call graph overlap and produce PR-ready benchmark results
license: AGPL-3.0
compatibility: opencode
metadata:
  audience: developers
  workflow: benchmarking
  category: analysis
---

## What I do

- Compare the current feature branch against local `master` exactly as it exists in the local git repository
- Create a temporary local-`master` git worktree under `/tmp/` so both revisions can be built side by side
- Build each revision with `mvn clean package -Dmaven.test.skip=true`
- Generate WALA and ASM call graph CSVs for the repo's built-in benchmark fixtures: `slf4j` and `okhttp + deps`
- Run `benchmark/compare_cg.py --diff` for each revision's WALA/ASM pair
- Convert the overlap results into a markdown `## Results` section with four tables and two comparison bullets
- Ask the user whether to paste the results into the PR they are currently working on or save them to a user-specified local markdown file

## When to use me

Use this skill when:

- The user wants a repeatable local benchmark comparison between the current feature branch and local `master`
- The user wants a ready-to-paste markdown results section describing WALA/ASM edge overlap on the standard repository fixtures
- The user wants to rerun the same comparison workflow for future feature-branch testing without re-deriving the commands

## Prerequisites

- The current repository must be a git repo with a local `master` branch
- The current branch must be a feature branch, not `master` or `main`
- Java, Maven, and Python 3 must be installed
- Python dependencies for `benchmark/compare_cg.py` must already be available
  - If they are not, install them with:
    ```bash
    pip install -r benchmark/requirements.txt
    ```
- The benchmark fixtures must exist:
  - `src/test/resources/test-jars/slf4j-api-2.0.17.jar`
  - `src/test/resources/test-jars/okhttp-jvm-5.3.2.jar`
  - `src/test/resources/test-jars/deps/`
- The working tree should be safe to build in place

## Workflow

1. Validate the git state.
   - Run:
     ```bash
     git branch --show-current
     git rev-parse --verify master
     ```
   - If the current branch is `master` or `main`, stop and tell the user this skill must start from a feature branch.

2. Create a temporary `master` worktree under `/tmp/`.
   - Use a deterministic temp path such as:
     ```bash
     /tmp/cg4j-master-bench-<timestamp>
     ```
   - Create it with:
     ```bash
     git worktree add /tmp/cg4j-master-bench-<timestamp> master
     ```
   - Use the current workspace for the feature branch build.
   - When finished, clean up the temporary worktree:
     ```bash
     git worktree remove /tmp/cg4j-master-bench-<timestamp>
     ```

3. Build both revisions.
   - In the feature branch workspace, run:
     ```bash
     mvn clean package -Dmaven.test.skip=true
     ```
   - In the `/tmp/` local-`master` worktree, run:
     ```bash
     mvn clean package -Dmaven.test.skip=true
     ```
   - Locate each assembled fat jar in `target/` using the current project version from `pom.xml`.
   - Use the `cg4j-<version>-jar-with-dependencies.jar` artifact from each revision.

4. Prepare a temporary output directory under `/tmp/`.
   - Example:
     ```bash
     /tmp/compare-cg-bench-<timestamp>
     ```
   - Keep generated CSVs grouped by revision and fixture so the results are easy to inspect.

5. Generate call graph CSVs for each revision.
   - Always use `--include-rt=true`.
   - Run both engines for both fixtures.

   Feature branch commands:
   ```bash
   java -jar <feature-jar> -j src/test/resources/test-jars/slf4j-api-2.0.17.jar -o /tmp/compare-cg-bench-<timestamp>/feature-slf4j-wala.csv --engine=wala --include-rt=true
   java -jar <feature-jar> -j src/test/resources/test-jars/slf4j-api-2.0.17.jar -o /tmp/compare-cg-bench-<timestamp>/feature-slf4j-asm.csv --engine=asm --include-rt=true
   java -jar <feature-jar> -j src/test/resources/test-jars/okhttp-jvm-5.3.2.jar -d src/test/resources/test-jars/deps -o /tmp/compare-cg-bench-<timestamp>/feature-okhttp-wala.csv --engine=wala --include-rt=true
   java -jar <feature-jar> -j src/test/resources/test-jars/okhttp-jvm-5.3.2.jar -d src/test/resources/test-jars/deps -o /tmp/compare-cg-bench-<timestamp>/feature-okhttp-asm.csv --engine=asm --include-rt=true
   ```

   Local `master` worktree commands:
   ```bash
   java -jar <master-jar> -j src/test/resources/test-jars/slf4j-api-2.0.17.jar -o /tmp/compare-cg-bench-<timestamp>/master-slf4j-wala.csv --engine=wala --include-rt=true
   java -jar <master-jar> -j src/test/resources/test-jars/slf4j-api-2.0.17.jar -o /tmp/compare-cg-bench-<timestamp>/master-slf4j-asm.csv --engine=asm --include-rt=true
   java -jar <master-jar> -j src/test/resources/test-jars/okhttp-jvm-5.3.2.jar -d src/test/resources/test-jars/deps -o /tmp/compare-cg-bench-<timestamp>/master-okhttp-wala.csv --engine=wala --include-rt=true
   java -jar <master-jar> -j src/test/resources/test-jars/okhttp-jvm-5.3.2.jar -d src/test/resources/test-jars/deps -o /tmp/compare-cg-bench-<timestamp>/master-okhttp-asm.csv --engine=asm --include-rt=true
   ```

   Notes:
   - Run the feature branch commands from the current workspace root.
   - Run the local `master` commands from the `/tmp/` worktree root so its relative fixture paths resolve correctly.

6. Compare WALA and ASM for each revision.
   - Run these four commands:
     ```bash
     python benchmark/compare_cg.py --diff /tmp/compare-cg-bench-<timestamp>/master-slf4j-wala.csv /tmp/compare-cg-bench-<timestamp>/master-slf4j-asm.csv
     python benchmark/compare_cg.py --diff /tmp/compare-cg-bench-<timestamp>/feature-slf4j-wala.csv /tmp/compare-cg-bench-<timestamp>/feature-slf4j-asm.csv
     python benchmark/compare_cg.py --diff /tmp/compare-cg-bench-<timestamp>/master-okhttp-wala.csv /tmp/compare-cg-bench-<timestamp>/master-okhttp-asm.csv
     python benchmark/compare_cg.py --diff /tmp/compare-cg-bench-<timestamp>/feature-okhttp-wala.csv /tmp/compare-cg-bench-<timestamp>/feature-okhttp-asm.csv
     ```
   - Parse each `Edges Set Comparison` table into these semantic categories:
     - `Only in WALA`
     - `Intersection (Both)`
     - `Only in ASM`
   - Do not keep raw CSV filenames in the final markdown tables.

7. Produce the final markdown results section.
   - Create a markdown block with exactly this structure:
     ```md
     ## Results

     ### Master / SLF4J

     | Category | Count | Percentage |
     | --- | ---: | ---: |
     | Only in WALA | ... | ... |
     | Intersection (Both) | ... | ... |
     | Only in ASM | ... | ... |

     ### Feature Branch / SLF4J

     | Category | Count | Percentage |
     | --- | ---: | ---: |
     | Only in WALA | ... | ... |
     | Intersection (Both) | ... | ... |
     | Only in ASM | ... | ... |

     ### Master / OkHttp + deps

     | Category | Count | Percentage |
     | --- | ---: | ---: |
     | Only in WALA | ... | ... |
     | Intersection (Both) | ... | ... |
     | Only in ASM | ... | ... |

     ### Feature Branch / OkHttp + deps

     | Category | Count | Percentage |
     | --- | ---: | ---: |
     | Only in WALA | ... | ... |
     | Intersection (Both) | ... | ... |
     | Only in ASM | ... | ... |

     ### Branch Comparison Notes

     - On `slf4j`, the feature branch changes WALA/ASM overlap from `X%` to `Y%`, changes WALA-only from `A%` to `B%`, and changes ASM-only from `C%` to `D%`.
     - On `okhttp` with dependencies, the feature branch changes WALA/ASM overlap from `X%` to `Y%`, changes WALA-only from `A%` to `B%`, and changes ASM-only from `C%` to `D%`.
     ```
   - Preserve the order of sections exactly as shown.
   - Keep fixture labels exactly as shown: `SLF4J` and `OkHttp + deps`.
   - Format counts with thousands separators when available.
   - Use the percentages from `compare_cg.py --diff` directly.
   - Write comparison bullets as concise narrative takeaways, not just raw number restatements.

8. Hand off the markdown to the user.
   - After generating the results, ask exactly one targeted question:
     - whether the user wants to post the results in the PR they are currently working on, or
     - save them to a user-specified local `.md` path, usually under `/tmp/`
   - Do not assume a save path.
   - Do not auto-post to GitHub.
   - If the user chooses a local file, write the markdown to the path they specify.

## Output Shape Guidance

- The goal is a benchmark summary that compares how much WALA and ASM agree within each revision, then states how that agreement changes from local `master` to the feature branch.
- The four tables are not branch-vs-branch diffs; each table is an engine-overlap summary inside one revision for one fixture.
- The final two bullets are the branch-vs-branch interpretation layer and should highlight whether the feature branch improves or worsens overlap.
- Favor language like `increases overlap`, `reduces WALA-only edges`, or `shrinks ASM-only share` when the data supports it.
- If one metric improves while another regresses, say so plainly in the same sentence.

## Error Handling

- If not on a feature branch, stop with: `Error: compare_cg_bench must start from a feature branch, not master/main.`
- If local `master` does not exist, stop with: `Error: local master branch not found.`
- If the temporary `/tmp/` worktree cannot be created, stop and surface the git error.
- If Maven build fails in either revision, stop and report which revision failed.
- If the fat jar cannot be found in either `target/` directory, stop and report the missing path.
- If any benchmark fixture is missing, stop and report the missing file or directory.
- If any `java -jar` benchmark command fails, stop and report the exact command context that failed.
- If `benchmark/compare_cg.py --diff` fails, stop and report the failing CSV pair.
- Always attempt to remove the temporary `/tmp/` master worktree before returning, even when a benchmark step fails.

## Safety Rules

- Compare only against local `master`; do not run `git fetch`, `git pull`, or any remote-sync command.
- Use the current feature branch workspace as-is.
- Create the comparison worktree under `/tmp/`, not inside the repository.
- Do not overwrite user files outside the explicit output path they choose for saved markdown.
