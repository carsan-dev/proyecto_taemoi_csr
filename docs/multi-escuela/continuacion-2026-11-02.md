Continuación de la fase 3. Al empezar: /router hibrido. Sigue las reglas de memoria.

Estado de partida:
- develop en 6b6dfcf6 con #184 (arrastre táctil), #185 (hoja de asistencia marcada) y #186 (foto en Mis grupos), sin migración. Pregunta al propietario qué SHA hay en producción: la última confirmada es c170566297a26b1ebead2e37aeb907fab1910e6e (Flyway 31.17).
- PR abierta: #187, modo privado, SHA 87da0cbf630ba4e63032a719d53bda75b55994b1. Lleva V31_18. Comprueba su CI: la combinación con #186 solo la valida ella. Despliegue: «Con migración V31_18 → `desplegar_migrar` + postcheck `docs/multi-escuela/postcheck-importes-tapados.sql`», seguido de `comprobar`. Próxima migración libre: V31_19; V32 reservada para la E10.
- Worktree vivo: proyecto_taemoi_csr-worktrees/modo-privado, con enlace node_modules. Bórralo tras fusionar la #187, quitando antes el enlace. Sigue bloqueada salud-panel. Postman sigue en `git stash`: no lo muestres ni lo subas.

Primero:
1. Tras desplegar, que el propietario pruebe en su móvil: arrastre de material, activar el modo privado en Mi perfil y Mi caja como profesor.
2. Cerrar 8c-R (paso 8c-R3): copia con `Materiales_Examen`, un cambio visto con cuenta de familia y un vídeo de unos 100 MB. Al cerrar, marca 8c-R3 y la entrega e8c.

Siguientes piezas, en orden:
1. 422: el JSON de /crear guarda fotoAlumno sin validar. Hay una tarjeta de tarea propuesta en la sesión anterior; si caducó, sale del roadmap.
2. Limpieza de la cabecera (379), sábado y domingo solo si hay turnos (380) y elegir vista al entrar con Google (384).
3. E10: V32, con migración y riesgo alto. Quitar usuario_rol de UsuarioRepository.tieneDatosFueraDeLaInvitacion; un test avisa.
4. Después: buzones (391), alta de escuela (390 y 392), fase 4, sede en la preinscripción (393) y Sierra Sur, con los huecos de la 394 antes del alta.

Pendientes nuevos: modo privado en otro dispositivo ya abierto no se aplica hasta recargar, aunque la ayuda dice «en todos tus dispositivos»; la ficha del profesor sigue con iniciales; un cliente que corta una foto a medias deja fila 500 en auditoría; fotos heredadas con tipo no image/* caen a iniciales; la ruta de familias sirve SVG heredado y caché `public`.
Siguen los pendientes de continuacion-2026-11-01.md y commitear los continuacion-*.md.
