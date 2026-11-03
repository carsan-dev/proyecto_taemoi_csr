Continuación de la fase 3. Al empezar: /router hibrido. Sigue las reglas de memoria.
Estado de partida:

* Producción y develop en aa22de88 (Flyway 31.20), con #188 (cinturones del material y V31_19), #189 (foto del alumno segura, 422), #190 (kickboxing sin PUM y marrón bordado, V31_20, decisión 424), #191 (bloques de negros de DAN a DAN) y #192 (rojo a negro hasta Negro 1º DAN). Sin PR abiertas. Próxima migración libre: V31_21; V32 reservada para la E10.
* Worktrees vivos: ninguno de esta sesión. Queda vacía la carpeta .claude/worktrees/cinturones-material (bloqueada por un proceso; bórrala si se deja). Sigue bloqueada salud-panel. Postman sigue en `git stash`: no lo muestres ni lo subas.
* Postchecks de producción: siempre con `--default-character-set=utf8mb4` (literales con «Ó» y «º»).

Primero, preguntar al propietario (una cosa cada vez):

1. Asignación manual de un producto a un alumno de kickboxing (ProductoAlumnoServiceImpl:328): el concepto sale del producto común y dice «ROJO». ¿Debe decir «MARRÓN» como los cargos automáticos?

Siguientes piezas, en orden:

1. Huecos de imágenes vistos en la 422: ImagenServiceImpl.eliminarImagenDeSistema borra la ruta absoluta heredada tal cual (borrado arbitrario con una fila forzada) y la foto redirige a cualquier host http(s) guardado en imagen.url. Riesgo alto: careful.
2. Limpieza de la cabecera (379), sábado y domingo solo si hay turnos (380) y elegir vista al entrar con Google (384).
3. E10: V32, con migración y riesgo alto. Quitar usuario_rol de UsuarioRepository.tieneDatosFueraDeLaInvitacion; un test avisa.
4. Después: buzones (391), alta de escuela (390 y 392), fase 4, sede en la preinscripción (393) y Sierra Sur, con los huecos de la 394 antes del alta.

Pendientes: test inestable CambioTarifasProductosIntegrationTest.alumnoConLicencia (expediente aleatorio duplicado: que busque uno libre); MaterialExamenTomcatIntegrationTest falló una vez (fila de auditoría ausente); modo privado en otro dispositivo abierto no se aplica hasta recargar; la ficha del profesor sigue con iniciales; un cliente que corta una foto a medias deja fila 500 en auditoría; fotos heredadas con tipo no image/* caen a iniciales; la ruta de familias sirve SVG heredado y caché `public`. Siguen los pendientes de continuacion-2026-11-01.md y commitear los continuacion-*.md.
