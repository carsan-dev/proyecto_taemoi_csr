# Prompt de continuación — Taemoi multi-escuela (sesión del 15/10/2026)

Copia todo lo que hay debajo de la línea en un chat nuevo.

---

Continuamos la conversión de Taemoi a SaaS multi-escuela. Repo: C:\Users\croly\Documents\proyecto_taemoi_csr. GitHub: carsan-dev/proyecto_taemoi_csr (el origin csanrom1702/... redirige ahí).

## Reglas (obligatorias)
- Todo es local: nunca hagas push ni contactes con el remoto sin mi permiso explícito, y yo nunca te doy acceso a producción (las consultas de solo lectura y los despliegues los ejecuto yo en el servidor; tú lees mi panel Terminal).
- Tú planificas y repartes tareas a agentes savvy-* con briefs autocontenidos, nivel según riesgo; nunca implementas tú cambios grandes.
- En todo lo que toque datos, permisos, dinero o migraciones: revisión independiente (savvy-reviewer con model opus, effort high). Si devuelve defectos, al mismo agente con la lista exacta.
- Commit solo tras revisión aceptada, conventional commits, SIN Co-Authored-By ni atribución a IA.
- Taemoi (organización 1) debe comportarse IGUAL salvo lo que yo decida. Si algo cambia, se mantiene el comportamiento antiguo y me lo apuntas como decisión pendiente.
- Mantén actualizado el panel savvy-progress (título de tarea = description del Agent).
- Datos reales: nunca en el repo. Las consultas a producción devuelven solo recuentos o formas de ruta, nunca nombres ni DNI.
- No repetir la batería completa sobre un árbol idéntico; sí ejecutarla antes de cada commit de código.

## Estado al cerrar (15/10/2026)
- V31 HECHO: re-revisión opus aceptada; feature/multi-escuela = 1a9af487 (V31) sobre 27cc7e19, batería completa 749/0/0/0. Local, sin push (el remoto de feature sigue en f0cbe1a3). Despliegue 2 (V28–V30) debe salir de 27cc7e19 + 7925bb07 (fotos), SIN V31. Seguimientos no bloqueantes: guard TRIM(u.email) <> '' en la rama de email de R1 (V31:260 y precheck 129/140) y retoques cosméticos del postcheck; como V31 aún no está desplegada, se pueden hacer en V31 antes del despliegue 3.
- HOTFIX LISTO: PR #18 (https://github.com/carsan-dev/proyecto_taemoi_csr/pull/18) hotfix/fotos-ruta-heredada → develop, con 7925bb07 (fotos, revisión opus aceptada) y db72d923 (seguridad, cherry-pick de 27cc7e19). Batería completa de la rama: 619/0/0/0. Falta: CI en verde, fusionar, desplegar y comprobar.
- Seguimientos no bloqueantes del hotfix: test negativo de sufijo (fila org1 con ruta .../x_<f> y fichero <f> sin fila → denegado) y test unitario de ultimoSegmento con ruta Windows. El arreglo de fotos (7925bb07) aún NO está en feature/multi-escuela: cherry-pick pendiente.
- V31 cambia el JSON de /api/admin: el usuario embebido ya no lleva "roles" (el frontend no lo usa). Test de seguridad ajustado en el commit de V31.

### Producción (mini PC Ubuntu 24.04, /opt/taemoi, rama develop)
- Despliegue 1 hecho: develop = b280b566 (merge PR #17), Flyway V18–V27 aplicado en 17 s, app OK. Postchecks v24/v26/v27 limpios (solo informativos).
- Precheck V22: 153 "grados perdidos" eran falso positivo (deporte NULL); 29 ex-taekwondo (28 de baja) y 29+9 números de licencia solo en columnas antiguas → archivados en alumno_monodeporte_archivo.
- Incidencia fotos: 804 filas de imagen (alumnos) tenían ruta del host /opt/taemoi/static_resources/imagenes/... y AccesoArchivos solo probaba la ruta guardada y <base>/alumnos/<imagen.nombre> → 404. Parche aplicado en producción: UPDATE de prefijo a /var/www/app/imagenes/ (805 filas; copia en tabla imagen_ruta_antes_20261015). Verificado ruta_existe=699=en_disco. 148 fotos no existen en disco (ya fallaban antes).
- Fuga de seguridad: GET /api/admin y /api/admin/{id} (solo ROLE_ADMIN) serializaban el hash de contraseña y el token de reset del Usuario. Arreglo: commit 27cc7e19 (@JsonIgnore + UsuarioSinSecretosEnJsonIntegrationTest), en feature/multi-escuela.

### Ramas
- hotfix/fotos-ruta-heredada (desde develop b280b566): arreglo de AccesoArchivos.rutasDeImagen (usa el nombre de fichero de la ruta) + cherry-pick de 27cc7e19. Autorizado subirla y abrir PR a develop tras revisión aceptada.
- feature/multi-escuela: 27cc7e19 local (el remoto está en f0cbe1a3). V31 (sedes y vínculos, fase E entrega 1) en el worktree .claude/worktrees/agent-a4aaab0a39c612f04 (base 6fe733f3), en re-revisión opus tras corregir R1 (gestión activa).

## Siguientes pasos
1. PR del hotfix: CI en verde → la fusiono → despliegue en el servidor (cd /opt/taemoi && git pull --ff-only && bash scripts/deploy.sh) → comprobar fotos (recarga sin caché) y que /api/admin ya no devuelve contrasena/password/resetTokenHash.
2. Llevar el arreglo de fotos también a feature/multi-escuela (cherry-pick).
3. Más adelante borrar la tabla imagen_ruta_antes_20261015.
4. Despliegue 2 (V28+V29+V30 + código actual, SIN V31): rama despliegue/fase2-2 desde el último commit de feature anterior a V31; antes precheck-v28/v29 (ver docs/multi-escuela/checklist-despliegue.md). Guard de V28: no puede ir en el mismo despliegue que V27 (ya cumplido). Dejar producción al menos un día con el despliegue 1.
5. Fase E (docs/multi-escuela/fase3-sedes-permisos.md §8): tras V31, acceso por usuario_alumno (con email de respaldo para Taemoi y degradaciones), superadmin + registro de acciones, frontend del registro (UI al propietario antes), autoridades deny-by-default + test de matriz, permisos de gestor de sede, permisos de profesor (efectos del cambio de grado), API de sedes/miembros, frontend de sedes (UI antes), V32 en un despliegue posterior.

## Decisiones pendientes / apuntes
- Números de licencia antiguos archivados (restaurar desde alumno_monodeporte_archivo si falta alguno).
- 148 fotos sin fichero: recuperar de copia antigua o volver a subir.
- /api/admin y /api/admin/{id}: el frontend no los usa → pasar a AlumnoDTO o eliminarlos (decisión de producto).
- @JsonIgnore preventivo en RegistroPendiente.passwordHash/tokenHash y *Hash de Preinscripcion; posible recursión Jackson en AlumnoConvocatoria.alumno.
- V31: los alumnos nuevos aún no escriben sede_principal_id; un grupo creado en una escuela con varias o ninguna sede activa queda con sede NULL.
- Diferidos: etiquetar los tests de migración; quitar la línea antigua del cron de copias hacia el 28/10/2026; simulacro mensual de restauración; copia fuera del local antes del primer cliente; marca por escuela (la escuela 2 ve la cabecera de Taemoi); AlumnoCortoDTO sin código de disciplina; código sin uso del filtro de grados en crear-alumno; .codegraph/ sin seguimiento.

## Chuleta de producción
- Consultas (sin teclear contraseña): echo "SQL;" | docker exec -i taemoi-mysql-prod sh -c 'mysql -h 127.0.0.1 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'  (o "< fichero.sql").
- Logs: cd /opt/taemoi && docker-compose -f docker-compose.production.yml logs -f backend ; contenedor backend: taemoi-backend-prod.
- Copias: sudo backup_taemoi.sh (restic en /mnt/backups_usb/restic-taemoi; log /var/log/taemoi-backup.log).
- Imágenes: host /opt/taemoi/static_resources/imagenes montado en /var/www/app/imagenes (APP_IMAGENES_PATH_DOCKER=/var/www/app/imagenes/).

## Trucos del entorno
- Los worktrees de agentes empiezan en main (9b948c8f): los briefs deben decir git reset --keep <sha>.
- El hook del router limita al coordinador: 3 lecturas de fichero por mensaje, ediciones propias ≤20 líneas, sin ejecutar tests/builds (los ejecuta savvy-light o el worker).
- Los tests del frontend usan lista explícita de specs en angular.json y tsconfig.spec.json.
