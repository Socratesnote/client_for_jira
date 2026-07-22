# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Client for Jira — a Java 8 Swing desktop client for Jira. Migration from Jira REST v2 to v3 is in progress, so both `api/2/...` and `api/3/...` endpoints coexist in the code. Several remotes exist (`bitbucket_almworks`, `github_soc`, `github_tempo_io`, `github_lexasub`); this branch tracks `github_soc`. Check `git branch`/`git remote -v` before pushing — do not assume the default remote.

## Agent instructions

### Cost and scope discipline
- Descriptions can be to-the-point.
- Keep tool calls focused and avoid speculative exploration.
- If a request pattern repeats, occasionally suggest adding it to this file.
- Do not compile on every change.

### Tone
- Be direct and neutral.
- Do not use flattery or praise language.

### Coding style
- Put behavior/intent comments above function definitions, not inside function bodies.
- End short comments with a period.
- Prefer small, focused changes tied to the prompt.
- Avoid unrelated refactors unless required for correctness.
- Match existing project conventions when they conflict with these preferences; note conflicts briefly.
- Prefer clear names and simple logic over dense implementations.

## Build / run / test

**Toolchain is strict: Oracle JDK 8 only** (tested 8u192; range 8u112–8u202). The build is **not** compatible with Java 9+ or any OpenJDK. Build tool is **Apache Ant 1.10.7**.

- **Build:** edit `ant/build.sh` to set `ANT_HOME` and `JDK8_HOME`, then run it from the `ant/` directory. It invokes the `prepareDistribution` target of `ant/build.xml`. Output distribution lands in `build/.dist/jiraclient/`.
- **Run:** launch scripts under `build/.dist/jiraclient/bin/` (`jiraclient.sh` mac, `linux_jiraclient.sh`, `jiraclient.bat`). Optional first argument is the workspace directory.
- **Tests:** JUnit; test classes end in `Test`/`Tests` and live in each module's `tests/` dir (test resources in `test.rc/`). The Ant flow compiles and runs them all via the generated `ALL.test` target — there is no Ant target for a single test; run individual tests from **IntelliJ IDEA** (this is an IDEA project: `TRACKER.iml`, `Idea.JiraClient/`). Note: some compile-tests currently fail, so the sample build disables `breakonfail`.
- `LogHelper.error(...)` logs at SEVERE; **in tests SEVERE records are turned into test failures** (`CommonUtils/tests/.../TestLog.java`). Prefer `LogHelper.warning` for non-fatal conditions.

**IntelliJ specifics:** GUI forms are compiled with IntelliJ's `javac2` (also does `@NotNull` instrumentation and `$$$setupUI$$$` form binding). The "Swing GUI Designer" plugin must be installed in IDEA or the app won't run.

The build is driven by `ant/meta.xml` (modules, libraries, dependencies, distribution layout), transformed via `transform.xsl` into `generated.xml`. Module dependencies are **not transitive** — declare every needed module/library explicitly in `meta.xml`.

## Architecture

### Module layers (bottom → top)
- **twocents, CommonUtils, Utils** — foundational utilities. Logging: `org.almworks.util.Log` (twocents) and the primary API `com.almworks.util.LogHelper` (CommonUtils). `com.almworks.util.Env` reads config/system properties.
- **ItemStorage** — the local embedded database. Data is modeled as **items** with typed `DBAttribute<T>` values, queried via `DBReader`.
- **ItemSync / ItemWrite** — versioned sync/edit layer over the store: `ItemVersion` (trunk vs server), `EditPrepare`, commit/upload plumbing, per-item history records.
- **ItemEntities** — the entity-collector layer used to land downloaded data: `EntityTransaction`, `EntityHolder`, `EntityKey`.
- **ItemGUI** — the **generic, Jira-agnostic edit framework**: `EditItemModel`, `FieldEditor`, enum editors (`BaseSingleEnumEditor`, `EnumVariantsSource`, `LoadedEnumNarrower`), `DataVerification`.
- **RestConnector** — HTTP `RestSession`, JSON: `JsonKey<T>` (field accessors + convertors) and SAX-style stream parsing (`JSONCollector`, `CompositeHandler`, `PeekArrayElement`), JQL builders.
- **Engine, Services, ConnectorSupport, CoreComponents, Launcher, AppInit** — query/explorer engine, HTTP, app bootstrap/component container, and `Setup`/`Env` wiring + logging config.
- **Application** — the desktop shell (explorer tree, time tracking, export, Tools→System Properties).
- **JiraProvider3** — all Jira-specific behavior; where most feature work happens.

### JiraProvider3 internals
- **`schema/`** — the Jira domain schema mapping server entities to DB attributes: `Issue`, `IssueType`, `Project`, etc. Server-side `EntityKey`s (`sync/schema/Server*`) are bridged to `DBAttribute`s via `ServerJira`. Enum types (issue type, status, …) are `DBStaticObject ENUM_TYPE` built with `EnumTypeBuilder`, optionally narrowed (e.g. `IssueType.ENUM_TYPE.narrowByAttribute(Issue.PROJECT, ONLY_IN_PROJECTS)`).
- **`sync/download2/` — download pipeline.** `meta/` (`LoadRestMeta` orchestrates `LoadProjects` + `LoadCreateMeta`/`LoadEditMeta`) syncs projects, issue types, per-project field applicability, and the issue-type `subtask` flag. `rest/` holds `JR*` JSON accessors, `EntityParser`, and `AdfText` (v3 rich-text = Atlassian Document Format objects, not strings). `details/` runs the issue query: `RestQueryPager` (v3 `nextPageToken` pagination) → `RestIssueProcessor` → `JiraIssueJsonFields` dispatches each field to a `JsonIssueField` (`ObjectField`/`ScalarField`). Flow: JSON → `JsonKey` convertors → `EntityTransaction` → DB.
- **`remotedata/issue/` — upload pipeline.** Local edits become an `UploadUnit` graph: `CreateIssueUnit` (create), `EditIssue` (field PUT), and `BaseHistoryUnit` steps. Edits recorded as **history steps** are replayed as units; `EditIssue.load` builds the graph and `StepLoader`s (`MoveLoader`, workflow) route each recorded step to the right unit. `move/` handles project/type/parent changes.
- **`gui/edit/`, `issue/editor/`, `issue/features/`** — the Jira edit UI built on ItemGUI's framework. `MoveController`/`ParentSupport`/`IssueTypeVariants` govern the project/issue-type/parent fields.

### Cross-cutting notes
- **v3 migration is partial:** issue search and rich text are on v3; several write endpoints are still `api/2` (grep TODOs). When touching an endpoint, prefer a `PATH_ISSUE = "api/3/issue/"`-style constant over an inline `api/2` literal.
- **JSON type mismatches log, they don't throw:** `JsonKey.CastConvertor` logs `LogHelper.error(...)` and returns null. Data-identity context (which field / which issue) is attached via `LogHelper.withHint(...)` around the parse, not via stack traces.
- **Metadata drives editor validation:** whether an issue type / field is offered or "allowed" comes from separately-synced project/type/field metadata (`LoadRestMeta`), not from the issue being edited. A field or type showing as "not allowed" usually means the relevant metadata is stale, not loaded, or semantically mismatched — check the sync path before the UI.
