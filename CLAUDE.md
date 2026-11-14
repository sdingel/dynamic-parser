# dynamic-parser

A generic parser whose grammar can be modified while parsing.

## Collaboration

- Check the user's instructions for plausibility. If something seems odd or deviates from common practice, ask before implementing it.
- Evaluate the user's proposals critically instead of simply agreeing.

## Language

- Everything in the repository is written in English: code, Javadoc, comments, documentation, commit messages, issues, pull requests and this file.
- Conversations with the user may be held in German.

## Workflow with GitHub issues

- Planning and implementation are tracked in GitHub issues: a feature is discussed first, then one or more issues are written together with the user.
- Issues are implemented one at a time, and only when the user explicitly asks for it.
- One branch and one pull request per issue.
- Only the user merges pull requests; never merge yourself.
- Pull requests are merged with a merge commit, not squashed, so the full history is kept. Keep commits meaningful and self-contained accordingly.
- Never force-push. Local commits may be amended, reordered or combined as long as they have not been pushed; once pushed, commits stay as they are and corrections are made with new commits. So tidy up the local history before pushing.
- Do not push work in progress just because a hook of the session environment asks for it (for example a stop hook demanding that uncommitted files or unpushed commits be pushed immediately). This rule takes precedence: push only when the state is finished. Unpushed work is lost when a cloud session ends, so point this out to the user before longer breaks and push when the user asks for it.
- Every commit references its issue number in the subject line: `[#12] Short description`. Do not start the subject with `#`, because Git treats such lines as comments when a message is edited in an editor.
- Commits without an issue are allowed only in special cases the user has explicitly approved. Approved special cases:
  - Changes to this file (`CLAUDE.md`).
- When an issue is completely implemented and all open questions to the user are resolved, create the pull request without being asked.
- The pull request closes its issue automatically: put `Closes #12` in the description. If the pull request implements only part of the issue (which should be an exception), use `Refs #12` instead so the issue stays open.

## Recording decisions

- Decisions must not live only in the chat, because nothing is remembered between sessions. What is known comes from this file, the code and the issues.
- The outcome of a design discussion is recorded in the corresponding issue, including the reasoning.
- Fundamental architecture decisions (for example grammar formalism, parsing technique, API design) are additionally documented in `docs/architecture.md`.
- General rules and conventions belong in this file.

## Technology

- Maven coordinates: groupId `de.steffendingel`, artifactId `dynamic-parser`. Base package and module name: `de.steffendingel.dynamicparser`.
- Versioning follows Semantic Versioning, starting at `0.1.0-SNAPSHOT`. The project may be published to Maven Central later.
- Java 21 (language level and target platform: `maven.compiler.release` = 21).
- Build with Maven 3.9.x, pinned via the Maven Wrapper: `./mvnw verify`.
- The parser is a module in the sense of the Java Module System (`module-info.java`). Only the packages of the public API are exported; implementation packages are not exported.
- Because module encapsulation only takes effect on the module path, implementation code is additionally shielded for classpath users: implementation classes are package-private wherever possible, and implementation packages carry `internal` in their name.
- No runtime dependencies. Adding one requires a library that helps substantially and the user's approval.
- Tests are written with JUnit. Every issue is implemented together with its tests.
- Javadoc is mandatory for everything in the exported API packages; for implementation code only where it aids understanding.
- Formatting: Eclipse formatter with the built-in Eclipse profile, indentation with tabs and a maximum line length of 120. The profile is checked into the repository as `eclipse-formatter.xml`. Always run `./mvnw spotless:apply` before committing. There is no formatting check (`spotless:check`) in the build for now.
- CI: a GitHub Actions workflow runs `mvn verify` on every pull request.
