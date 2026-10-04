# Editable student product assignment date

## Scope
Edit fechaAsignacion in Gestionar Productos through existing confirm/cancel flow and PUT DTO. Preserve other fields and unchanged/null legacy dates. Calendar follows displayed local day and retains existing local time where possible; setting legacy null uses noon. Clearing is not supported. No schema migration or production access.

## Tasks
- [x] T1 Implement date editing, dirty tracking, backend setter and regressions. Delegated writer (multi-file trigger).
- [x] T2 Verify, review and deliver. Independent verifier passed; native review approved/acknowledged; functional commit published.

## Evidence
- RED: backend 1/11 failed; six new frontend regressions failed before implementation.
- GREEN: backend focused11 and component13 passed; independently rerun with same passing counts.
- Full backend240:235 passed,5 optional MySQL concurrency skips,0 failures/errors.
- Configured full frontend105 passed; development build passed. Expected simulated HTTP errors in test fixtures did not fail tests.
- Whitespace check passed; parent read date logic and reran diff check.
- Rendered-template browser test with mocked JSON transport covers edit/confirm/reload/cancel, null dates, near-midnight offset, unchanged timestamps, clearing rejection and deleted-row disabling.
- No live authenticated E2E, live MySQL persistence check or timezone/DST matrix executed.
- Native review review-6057aad97dc7eedf approved and acknowledged, authority burned. Native assessment failed on untracked declaration; independent verifier completed as conservative fallback.

## Delivery
Base ac072fc1 on develop. Source/test diff 219 additions/3 deletions. Identity carsan-dev confirmed. User previously requested Spanish functionality commits and push.
- bc3446dc: feat(productos): permite editar fecha de asignación — UI, backend and regression tests; pushed origin/develop.
This tracking document is delivered separately. Unrelated .codegraph/, .serena/, IDEA.md preserved. No installs or data mutation.

## Next step
Deploy updated backend and frontend, then open student Gestionar Productos, edit assignment date and confirm changes. Deploy/live smoke check remains user-side.
