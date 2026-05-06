## Project Context

**Dr. Zara** — EHR vendor simulator for internal testing at Nav.

**Stack**: Ktor, Gradle wrapper, Exposed, Jackson, OAuth 2.1, OIDC, FHIR R4, Docker Compose, Nais.

## Principles

1. All data follows **FHIR R4** spec
2. User isolation — every query filters by user/session id
3. Delete before adding; question every requirement
4. Ship working code — done = build succeeds and tests pass
5. One feature per commit (`feat|chore|fix|test|refactor|docs: description`)
6. DRY, KISS, SOLID, security-first
7. Delete unused code rather than commenting it out
8. Code in English, domain terms in Norwegian (e.g. `getSykmeldinger`, `Behandler`, `Fastlege`)
9. Prefer inlining/copying over shared Nav libraries — avoid fellesbiblioteker
10. Distroless base images for all containers

## Commits & Merging

- Commit messages are **descriptive** — explain *why*, not just *what*
- Squash when a feature branch has noisy/WIP commits
- Rebase when individual commits are meaningful and should be preserved in history
- Fat JAR built via Ktor plugin (not ShadowJar)

## Architecture

Language-specific patterns live in `.github/instructions/kotlin.instructions.md`.

## State Machine Maintenance

`docs/state_machine.md` maps every source file to its flow. **After completing a change**, check:

1. **New files?** → Add to the relevant flow's file list AND the Source File Inventory
2. **Deleted files?** → Remove from flow file lists and inventory
3. **New route/endpoint?** → Add a new flow number or sub-flow (e.g. `8g`)
4. **New state transition?** → Update the mermaid diagram

Skip this for changes that only modify existing files without adding/removing any.

## Anti-Pattern & State Coherence Check

After completing a change, review the affected flow(s) for:

- Missing authentication checks on FHIR endpoints
- Inconsistent error handling (e.g. some routes return 404, others redirect)
- Broken transitions or dead-end states
- Auth bypass patterns (routes accessible without session)
- Repository interface violations (stub vs real implementation drift)

If anti-patterns are found, **prompt the user**: "I noticed [issue]. Should I address this in a separate commit?" Never silently fix anti-patterns as part of an unrelated change.

## Testing

- New features require unit tests covering edge cases
- Test names describe behaviour (`should do X when Y`)