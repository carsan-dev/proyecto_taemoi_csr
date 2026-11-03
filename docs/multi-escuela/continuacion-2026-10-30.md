Continuación de la fase 3. Al empezar: /router hibrido. Sigue las reglas de memoria. Tope del día: pregúntamelo.

Estado: producción en 944eee32 (Flyway 31.16). develop = 944eee3291466c05cab0f34156fd0ed15ceb6f07; el checkout local sigue en bec1ded0. Fotos de eventos (decisión 342) desplegadas con las #164 y #165. Postcheck y nginx correctos en producción. Plan en docs/multi-escuela/fase3-e8c-plan.md. Orden: E9 y después recursos de las familias. Sierra Sur no ha dado fecha: el 16/11 es solo una estimación.

Foto real de móvil probada por el propietario en producción. Copia local taemoi_fotos ya borrada. En .claude/launch.json quedan fotos-api y fotos-front, de modelo. Antes de actualizar develop local, quita del checkout las copias sin seguimiento de fase3-e8c-plan.md, fase3-e8c-inventario.md y fase3-e8c-boceto-fotos.html: coinciden con las de la rama, salvo finales de línea en el plan, y bloquean el ff.

Worktrees vivos y ya fusionados, borrables: .claude/worktrees/e8b-3o (rama feat/fotos-eventos-api) y .claude/worktrees/e8c-f2 (rama feat/fotos-eventos-web). Ambos con node_modules enlazado: e8c-f2 por unión, quítala con rmdir antes de git worktree remove. Ramas locales ya fusionadas y borrables: feat/fotos-eventos-panel, perf/caja-pendientes, fix/destello-cabecera, fix/estilos-gestion-web, fix/retoques-lote, feat/por-cobrar-grupo, feat/perfil-cuenta, feat/pasar-lista, refactor/retirar-listados-completos. En .claude/launch.json queda la entrada pasar-lista-api, que apunta a un .properties de un scratchpad antiguo: sirve de modelo, pero hay que recrearlo. Al copiar la base de pruebas, hay que dar permiso al usuario taemoi sobre la copia, y parar el preview antes de un mvnw verify (el jar en uso rompe repackage).

Siguientes piezas, en este orden:
1. E9: sedes y miembros.
2. Recursos de las familias: falta decidir quién los usa y diseñarlos.

Decisión pendiente:
- 341: sin interruptor del panel, cómo se despliega una pieza nueva sin que se vea antes de tiempo. AGENTS.md y la decisión 255 siguen diciendo que cada pieza se despliega apagada; el interruptor se retiró en 0a10bf31.

Pendientes de pasar lista, por decidir:
- Gestión pasa lista, pero no ve la constancia (no hay boceto).
- La API de gestión no tiene límite de días, pero la hoja «Elegir clase» solo ofrece 14.
- El responsable de sede tiene API, pero no ruta en el panel (igual que Grupos).
- Guardar con nadie marcado registra a todos como falta, sin confirmación.
- En Grupos de gestión, con «Pasar lista», la papelera baja a una segunda línea.
- Residuos aceptados: la respuesta del PUT cuenta una fila residual de anonimizado; la redacción de auditoría es por clave.
- Sin prueba en la app real de que gestión corrija más allá de 14 días (la API sí está probada).

Pendientes menores:
- Decisión 163: GET /api/alumnos?deporte=X sin page ni size no fija orden con gestión (AlumnoServiceImpl.java:375); el frontend siempre pagina.
- ListadosCompletosRetiradosIntegrationTest no cubre la sesión SOLO_PLATAFORMA.
- Presupuesto del bundle: la referencia 1.499.939 está desfasada; el 30/10 midió 1.344.354 bytes con node_modules enlazado, sin npm ci. Falta la cifra oficial y actualizar AGENTS.md.
- Los logs de nginx de producción no tienen volumen: se pierden al desplegar. Para comprobar uso de rutas, usar auditoria_evento.
- Karma: 21 specs siguen fuera de angular.json y de tsconfig.spec.json.
- Karma: Chrome se colgó en la CI de la #165 (run 41127356283, test 2477) sin causa establecida; no se reprodujo en local y hay un precedente sin esta rama (run 40033219948). Si repite, registra semilla y nombres. browserDisconnectTolerance no rescata por un defecto de Karma 6.4.4.
- Mi perfil: el contador cuenta unidades UTF-16 y la pantalla corta en 20 emojis, aunque la API admite 40.
- Mi perfil: el aviso de guardado tapa un momento «Guardar cambios» en escritorio si se vuelve a editar enseguida.
- RecordatorioRachaServiceImpl:123-124 guarda todas las columnas de la cuenta: un PUT de perfil en esa ventana se pierde.
- Sin prueba automática de que la imagen anterior arranca sobre el esquema nuevo (V31_14 y V31_15).
- e2e: expansión del PDF en test.skip (permitirExpandir=false).
- RegistroAccionesPanel: los tipos DISENO_* son solo históricos.
- Parpadeo raro: un aviso de sesión repetido cuando se muestra lo de hoy pasa encendido a null (skeleton.component.ts, usuarioLogueadoCambio).
- estilosPanelGuard puede colgarse si la hoja no dispara ni load ni error.
- Vista previa del profesor sin revisar a ojo (quedó de la 8b-P).
- docs/multi-escuela/roadmap.md sigue siendo el export del 24/10, y dice por error que el material de examen está en el frontend.
- valores-actuales.md §5 está desactualizado (ExamMaterialBlockConfig ya solo vive en tests).
- mvnw verify tardó 11:19 el 30/10, frente a ~6,5 min; sin causa confirmada.
- nginx de producción avisa de que worker_connections (2048) supera el límite de ficheros abiertos (1024).
- Dependabot avisa de 33 vulnerabilidades en la rama por defecto (3 críticas, 12 altas).
