# Enrollment sport assignment

## Objective
One matrícula per sport. Require explicit student sport for each new matrícula, persist alumnoDeporte and include sport in concept. Preserve OTRO, non-enrollment products and historical rows. No schema migration or production access.

## Authorization and delivery
User explicitly authorized implementation, Spanish functionality-separated commits on develop, then push. Preserve unrelated .codegraph/, .serena/, IDEA.md. Delivery strategy ask-on-risk; actual source/test diff 352 lines across 8 files. Planned units: API validation/concepts with backend tests; UI selection/queue/API wrapper with component tests and test config; progress documentation.

## Tasks
- [x] T1 Implement sport-linked matrícula assignment and regressions. Delegated worker (multi-file trigger). Verified focused checks and native review; functionality commits recorded below.
- [x] T2 Independent verification and authorized delivery. Delegated verifier due unassessable native assessment. Full checks passed, functionality commits pushed and remote SHA confirmed.

## Acceptance
Explicit valid sport per matrícula; separate assignments for two sports; generic matrícula rejection; descriptive concept; OTRO unchanged; other products unchanged; historical records untouched.

## Evidence
Backend observed RED: 3 failing behavior regressions. Final focused GREEN: 16 backend tests and 7 ChromeHeadless component tests. Development build and git diff --check passed. Frontend initial spec exclusion/fixture failures resolved. Rendered-template check verifies selector, disabled assignment, pending sport label and no premature API call; no live authenticated browser check performed.
Native review review-a5a137b329235e54 approved and acknowledged (authority burned). Nonblocking informational finding R3-multiline-concept at ProductoAlumnoServiceImpl.java:91, deferred. Native assess could not classify due untracked declaration; independent verifier confirmed focused 16/7 passes, development build, full backend 218 tests (213 passed, 5 opt-in MySQL skipped), configured frontend 105 passed and diff-check. PDFBox generated incidental font cache; source status unchanged. No live authenticated UI test. Existing sport filter matches association OR concept, so contradictory sport-labelled product concepts may match both sports; no repository query change made. Use generic matrícula concept and explicit selector; exclusive SQL filter behavior not tested by the new TODOS test.

## Commits
Branch develop, initial HEAD 62c667bc.
- ef2a8915: fix(matriculas): vincula cada cargo a un deporte (API validation/concepts + backend tests).
- 0e011080: feat(matriculas): permite seleccionar el deporte (UI, API wrapper, frontend tests/config).
Both pushed successfully to origin/develop; remote confirmed 0e0110801afb9a0bd089fb0725bcb0712ed3624c. This progress document is delivered in a separate documentation commit.
Push reported repository relocation to carsan-dev/proyecto_taemoi_csr and 5 default-branch dependency alerts (2 high, 3 moderate); remote configuration and dependencies were not changed.

## Next step
Deliver this final progress document. Optional follow-ups: live authenticated workflow check, historical charge reconciliation, concept consistency and exclusive sport filtering; all outside this completed change.
