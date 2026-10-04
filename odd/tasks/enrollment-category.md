# Enrollment Treasury category

## Objective and scope
Classify matrícula charges as MATRICULA rather than OTRO. Recognize accents, case and leading whitespace. Preserve other category rules and sport resolution. Dynamic DTO classification updates matching historical charges and CSV/PDF after deployment, without DB migration or frontend changes. No new category filter or grouped summary. User reports historical association/concept SQL updates completed manually.

## Tasks
- [x] T1 Add classifier and regression tests. Delegated worker; focused and full backend checks passed.
- [x] T2 Verify, review and deliver. Independent verifier passed; native review approved and acknowledged; functional commit pushed on develop.

## Evidence
- RED: 27 focused tests, 8 intended failures before production change.
- GREEN: 27 focused tests passed; independently rerun with 27 passing.
- Full backend: 238 tests, 233 passed, 5 opt-in MySQL concurrency skips, zero failures/errors.
- git diff --check passed, repeated by parent/verifier.
- CSV category, legacy category cases, accents/case/token boundaries and explicit sport precedence covered.
- No frontend suite or live authenticated browser check: backend-only classification change. PDF consumes the shared DTO; CSV output directly tested.
- Prefix-only scope: PREMATRICULA, MATRICULACION and Pago de matrícula are not enrollment-prefixed matches.
- Native review review-5c478865638d76cc approved and acknowledged, authority burned. Assessment failed on untracked declaration, so independent verification was performed and passed.

## Delivery
Base 52fb9e64 on develop. Source/test diff 64 additions and 2 deletions. User paused commits until Git identity change and then explicitly resumed. Author/committer verified as carsan-dev before commit.
- 33d42eb5: feat(tesoreria): añade la categoría MATRICULA — implementation and tests; pushed to origin/develop.
- This document is delivered separately as passive progress documentation.
Preserved unrelated .codegraph/, .serena/ and IDEA.md. No production data access, installs or migrations. Remote reports repository relocation and existing dependency alerts, not addressed by this feature.

## Next step
Deploy the updated backend image once built. Matching old and new charges will show MATRICULA automatically on retrieval; no additional SQL is required.
