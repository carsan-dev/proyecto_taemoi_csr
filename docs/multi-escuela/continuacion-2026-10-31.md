Continuación de la fase 3. Al empezar: /router hibrido. Sigue las reglas de memoria.

Estado de partida:
- Producción y origin/develop en 2cc77f7da16c2d637850940c9832bacf50a6d602 (Flyway 31.16). E9 cerrada. Falta la prueba del propietario en producción (despliegue 159). PR #176 abierta, head 72076c504d923f02e47124fca0f2d57fe59136b5, solo texto y sin migración; worktree texto-google borrable tras el merge.
- Quedan proyecto_taemoi_csr-worktrees/e9-7 (vacía) y e9-8 (solo `.codegraph/`), bloqueadas por CodeGraph; bórralas con `cmd //c "rmdir /s /q <ruta>"`. Próxima migración libre: V31_17; V32, para la E10.

Siguientes piezas, en orden:
1. Contraer `/api/admin/users*` tras desplegar la 9-8 (397): AdminController, tests, matriz y smoke-fase1.sh. Esa ruta aún deja en WARN el valor de un JSON malformado.
2. Recursos de las familias (8c-R): quién los usa, diseño y escritura.
3. E10: V32, riesgo alto. Quitar `usuario_rol` de `UsuarioRepository.tieneDatosFueraDeLaInvitacion`; un test avisa.
4. Después de la fase 3: buzones por escuela (391), alta de escuela con plantilla de catálogo (390 y 392, cambia la D3), fase 4, sede en la preinscripción pública (393) y Sierra Sur.

Pendientes concretos:
- Decisión 399: cuenta legada sin membresía dada de alta desde otra escuela. Resolverla con el alta de escuela; falta saber cuántas hay en producción.
- `EmailServiceImpl:46` sigue registrando destinatario y `e.getMessage()`. Contraseña de más de 72 bytes: 400 en inglés. `PasswordResetServiceImpl` no bloquea la cuenta.
- Confirmar con el propietario las decisiones 379 a 384, 394 y 397; la 381 y la 382 tocan dinero. Siguen sin revisar las confirmaciones del resumen del 31/10 y las del despliegue 158.
- Actualizar fase3-sedes-permisos.md y matriz-endpoints.md con la entrada del responsable. Decisión 364: AGENTS.md sigue llamando fuente a roadmap.md. Carpeta vacía .claude/worktrees/e8b-3o.
