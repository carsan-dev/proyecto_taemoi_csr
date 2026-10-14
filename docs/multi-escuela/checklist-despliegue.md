# Checklist de despliegue multi-escuela

## Antes
- [ ] Copia de seguridad de la base de datos y de las carpetas de documentos e imágenes.
- [x] Contar usuarios sin rol (no podrán iniciar sesión tras V20); decidir su rol si hay alguno.
      Resultado del 13/10/2026: 0. Repetir justo antes de desplegar:
      `SELECT COUNT(*) FROM usuario u WHERE NOT EXISTS (SELECT 1 FROM usuario_rol r WHERE r.usuario_id = u.id);`
- [ ] En el `.env` del servidor, `APP_IMAGENES_PATH_DOCKER` debe ser `/var/www/app/imagenes/` (no `…/alumnos/`).
- [ ] Generar y guardar fuera del repositorio la clave `app.cifrado.clave` (base64, 256 bits) para los
      datos de salud y añadirla al `.env`. Sin copia de esa clave, los datos de salud no se pueden leer.
- [ ] Ventana de mantenimiento con la aplicación parada durante las migraciones V18–V20.
- [ ] Cargar fuera del repositorio los datos legales de la organización 1 (`nombre_legal`, `cif`, `firmante_*`).
- [ ] V23 (catálogo) nunca se despliega sin el cambio de código de los enums a las tablas (fase 2, tarea 5).
- [ ] Antes de V22, ejecutar `docs/multi-escuela/precheck-v22.sql` (solo lectura, solo recuentos) y guardar el resultado.
- [ ] Antes de V26, ejecutar `docs/multi-escuela/postcheck-v26.sql` (solo lectura, solo recuentos): la primera consulta debe dar 0 en las cinco tablas o V26 se detiene.
      Si V26 falla, corregir la fila indicada en el error (`v26_<tabla>_tiene_filas_sin_disciplina`),
      ejecutar `flyway repair` y volver a desplegar: V26 se detiene antes de cambiar ninguna columna.

- [ ] Antes de V27, ejecutar `docs/multi-escuela/postcheck-v24.sql` y `docs/multi-escuela/postcheck-v27.sql`
      (solo lectura, solo recuentos) y guardar el resultado. V27 no se detiene por ningún recuento; para
      Taemoi el código conserva el comportamiento anterior (20 € de competidor, variantes sin clasificar,
      conceptos literales, tesorería por concepto) y los recuentos solo dicen a qué afectaría cambiarlo.
- [ ] **V28 (contracción del catálogo) va en un despliegue POSTERIOR al de V26/V27.** Solo se despliega
      cuando la versión con V26/V27 ya ha funcionado en producción y sus comprobaciones posteriores están
      limpias: `postcheck-v26.sql` (primera consulta a 0 en las cinco tablas) y `postcheck-v27.sql`
      (`inscripciones_tarifa_distinta` = 0). Nunca en el mismo despliegue que V26/V27: ampliar y contraer en
      un solo paso deja sin vuelta atrás si el cambio de llamadores falla.
- [ ] Antes de V28, ejecutar `docs/multi-escuela/precheck-v28.sql` (solo lectura, solo recuentos) y guardar el
      resultado. Las siete primeras columnas, `alumno_deporte_disciplina_repetida` y `plantilla_version_repetida`
      deben ser 0. Si V28 se detiene (`Table '…v28_<tabla>_<columna>_no_coincide_con_el_catalogo' doesn't exist`),
      no ha cambiado nada: corregir las filas de esa tabla, `flyway repair` y volver a desplegar.
- [ ] Desde V27, NUNCA ejecutar a mano `db/resync/resync_catalogo_v24.sql`: su UPDATE que pone
      `tarifa_id = NULL` donde `tipo_tarifa` es NULL no se limita a una organización y borraría las tarifas
      con código propio de otras escuelas (que no escriben `tipo_tarifa`). Ninguna migración posterior a V27
      lo incluye (un test lo comprueba).

## Después
- [ ] Entrar como administrador, gestor y familia; cada uno ve lo mismo que antes.
- [ ] Dar de alta un alumno; su expediente es el siguiente al más alto.
- [ ] Se ven las fotos de eventos en la web pública y las fotos y documentos de alumnos en el panel.

## Vuelta atrás
**Después de V28 la única vuelta atrás es restaurar la copia de seguridad** tomada justo antes de V28: V28
borra columnas (`deporte`, `tipo_grado`, `tipo_tarifa`, `tipo_categoria`) que el código anterior lee y escribe,
y no hay forma de reconstruirlas desde la aplicación. Por eso la copia de seguridad previa a V28 es
obligatoria y se guarda hasta confirmar que la versión con V28 funciona.

Desde V20 los roles viven en `membresia` y `usuario_rol` deja de actualizarse: volver al código
anterior exige restaurar la copia de seguridad.

Desde V27 el código escribe `tarifa_id` y sigue escribiendo `tipo_tarifa` (salvo códigos de tarifa propios
de otra escuela), y los productos que crea llevan `codigo`/`tipo`: el código anterior funciona sobre esos
datos sin cambios de esquema.

Desde V26 las cinco columnas `disciplina_id` son NOT NULL y el código anterior a V26 no las escribe.
Volver a ese código exige antes `ALTER TABLE … MODIFY disciplina_id BIGINT NULL` en `alumno_deporte`,
`grupo`, `convocatoria`, `plantilla_preinscripcion` y `preinscripcion` (en la práctica, volver atrás
después de V20 ya supone restaurar la copia de seguridad).
