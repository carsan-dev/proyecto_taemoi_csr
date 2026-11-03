Continuación de la E8b (fase 3). Al empezar: /router hibrido.

Estado: producción en 7e3cf450 (8b-3m), Flyway 31.11. develop en 12b3d2b1 con #126 (hotfix del diálogo de eliminar con pagos, d58582a2) y #127 (8b-3n productos de un alumno), ambas sin migración y SIN DESPLEGAR → `desplegar` + `comprobar`. PR abierta: #128 (feature/e8b-r4, cb9250ba), retoques de la ronda 4 (decisión 301), pendiente de tu revisión visual: https://claude.ai/artifact/GQDU85pykiBM4PkeyYkoDD (copia en .claude/capturas-r4/img). CI en verde (el primer intento de frontend se colgó en el runner, DISCONNECTED sin fallos; al relanzar pasó; en la #127 pasó lo mismo). Antes de fusionarla, traer develop (tiene la directiva SearchableSelectDirective cambiada en #127).
Worktrees vivos: .claude/worktrees/e8b-3n (fusionada), fix-dialogo-eliminar (fusionada), e8b-r4 (PR #128). Quitar el enlace de node_modules antes de borrarlos.

Siguientes, en orden:
1. Revisar la comparativa y decidir #128; decidir sus dos puntos heredados: Tipo de grupo del turno solo al elegir grupo, y seleccionar alumnos que solo enseña a los que ya están si la búsqueda los incluye (¿choca con P-8?). Riesgo: la segunda línea de alumnos del grupo hace hasta 10 lecturas más por página.
2. Desplegar #126 + #127 (y #128 si la apruebas).
3. Plantillas PDF de Preinscripciones con boceto (decisión 300).
4. Arnés: el bloque `## Savvy router` de ~/.codex/AGENTS.md hace que los workers de Codex abran subagentes (collab) pese a --disable multi_agent; y el hook de Codex del router deniega comandos con `$(...)` aunque no mencionen codex.

Pendientes concretos:
- 297: consulta de cargos con cantidad > 1 (comando en el último mensaje del chat del 25/10).
- 8b-3n, avisos no bloqueantes de Opus: no hay «Deshacer» por fila (si una fila editada pasa a cobrada, solo la libera «Descartar» global); si falla la relectura tras un cobro, la fila vuelve a ofrecer Cobrar (el servidor no duplica el cobro).
- TODO(3m-5) miga de Turnos de un alumno; aspa nativa + «Borrar» en Seleccionar alumnos.
