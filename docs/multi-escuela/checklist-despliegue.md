# Checklist de despliegue multi-escuela

Procedimiento paso a paso en el servidor: [docs/operacion/procedimiento-produccion.md](../operacion/procedimiento-produccion.md).

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
- [ ] **El despliegue de V18–V27 NO se construye desde la punta de la rama** (contiene V28/V29 y código que
      los necesita). Se construye desde `34f02484` más el cambio de V27 de `eff5ae14` (solo la tabla temporal
      de la guarda). Ese V27 tiene que ser idéntico al de la rama: si se despliega el V27 de `34f02484`, en el
      despliegue siguiente Flyway se detiene por checksum distinto de V27.
      **Guarda:** V28 se niega a ejecutarse en la misma ejecución de Flyway que V27 (V27 deja una tabla
      temporal que solo existe en esa conexión). Si ocurre, V28 se detiene antes de cambiar nada con
      `Table '…v28_no_puede_aplicarse_en_el_mismo_despliegue_que_v27' doesn't exist` y la aplicación no
      arranca: `flyway repair`, desplegar la versión sin V28 y dejar V28 para el despliegue posterior. Con V27
      aplicada en un despliegue anterior (minutos o meses antes), la guarda no actúa. Solo los entornos de
      prueba que migran de cero en un arranque (tests, `docker-compose.pruebas.yml`, `docker-compose.e2e.yml`)
      la desactivan con `TAEMOI_V28_PERMITIR_MISMO_DESPLIEGUE_QUE_V27=true` (placeholder de Flyway
      `v28_permitir_mismo_despliegue_que_v27`, `false` por defecto). **En producción no se define nunca.**
      Desarrollo (`docker-compose.yml` y el perfil local) también la desactiva. Las bases de desarrollo o pruebas que ya aplicaron la versión anterior
      de V27/V28 dan error de checksum: `flyway repair` o un volumen nuevo.
      Antes del despliegue de V27, comprobar con `SHOW GRANTS` que el usuario de la aplicación tiene
      `CREATE TEMPORARY TABLES` (sin él, V27 falla antes de cambiar nada).
- [ ] Antes de V28, ejecutar `docs/multi-escuela/precheck-v28.sql` (solo lectura, solo recuentos) y guardar el
      resultado. Las siete primeras columnas, `alumno_deporte_disciplina_repetida` y `plantilla_version_repetida`
      deben ser 0. Si V28 se detiene (`Table '…v28_<tabla>_<columna>_no_coincide_con_el_catalogo' doesn't exist`),
      no ha cambiado nada: corregir las filas de esa tabla, `flyway repair` y volver a desplegar.
- [ ] **V29 (contracción de personas) va en el MISMO despliegue que V28** y que el código que ya no lee ni
      escribe `alumno.responsable_legal_nombre`/`_nif` (el responsable legal es el primer tutor marcado como
      tal). Nunca ese código sin V29 ni V29 con el código anterior. Antes, ejecutar
      `docs/multi-escuela/precheck-v29.sql` (solo lectura, solo recuentos) y guardar el resultado:
      `responsable_nombre_diferente`, `responsable_nif_diferente` y `tutores_sin_nombre_ni_nif` deben ser 0.
      Si V29 se detiene (`Table '…v29_<motivo>' doesn't exist`), no ha cambiado nada: corregir las filas,
      `flyway repair` y volver a desplegar. Si falla a medias, `flyway repair` y volver a desplegar: cada paso
      vuelve a comprobar el esquema y los datos.
- [ ] **V30 (catálogo visual) va en el despliegue 2, en la misma ejecución que V28/V29 (después de ellas)**,
      o en uno posterior, pero siempre con el código que la usa: la entidad `Tarifa` mapea `tarifa.orden` y
      Hibernate (`ddl-auto=validate`) no arranca sin esa columna, y los listados y PDF muestran `grado.nombre`
      y `color_1`/`color_2` (sin V30, Taemoi vería los nombres de V23, «Blanco-Amarillo»). Va en el
      despliegue 2, que NO se construye desde la punta de feature/multi-escuela (lleva V31 y código que la
      necesita), sino desde `despliegue/fase2-2`: develop con el arreglo de fotos (`7925bb07`) y fusionado
      `27cc7e19`, último commit anterior a V31. V31 y `1a9af487`, `af0eec8f`, `2ce13224`, `814399b8` van en
      un despliegue posterior. Las imágenes del servidor deben apuntar a `:develop` o `:sha-<sha7>` (`latest`
      solo se publica desde main). V30 no tiene guarda ni
      comprobación previa: solo actualiza datos de la organización 1 y añade una columna; no depende de V28/V29
      (usa `grado.codigo`, `sistema_grados` y `tarifa`, de V23/V24). Si falla a medias, `flyway repair` y volver
      a desplegar: cada paso vuelve a comprobar el esquema y los datos. No exige copia propia: la de V28 sirve;
      el código anterior funciona con la columna nueva (la ignora), pero mostraría los nombres nuevos.
- [ ] **Despliegue 3: V31 (sedes y vínculos, fase 3) con la aplicación parada**, en el mismo despliegue que el
      código que ya no mapea `Usuario.roles` (el código anterior también funciona sobre V31: solo añade columnas
      NULL o con valor por defecto, tablas que no lee y la FK de `usuario_rol` en cascada). Antes: copia de
      seguridad y ejecutar `docs/multi-escuela/precheck-v31.sql` (solo lectura, solo recuentos) y guardar el
      resultado. V31 no se detiene por ningún recuento, pero revisar a mano: `alumno_id_email_distinto_familia`
      (cuentas solo familia cuyo `alumno_id` no comparte email: no reciben vínculo), `alumnos_email_solo_espacios`
      (email del alumno con espacios sobrantes: hoy no visible, no recibe vínculo; corregir el email),
      `tutor_email_candidatos` (vínculos manuales posibles, R4) y `membresias_responsable_o_profesor` (debe ser 0).
      D1 («Umbrete») y D6 ya están decididas (14/10/2026). Antes del despliegue, comprobar con `SHOW GRANTS` que
      el usuario de la aplicación sigue teniendo `CREATE TEMPORARY TABLES` (V31 deja la marca de la guarda de V32).
      Si V31 falla a medias, `flyway repair` y volver a desplegar: cada paso vuelve a comprobar el esquema y los datos.
- [ ] Tras V31: ejecutar `docs/multi-escuela/postcheck-v31.sql` y guardar el resultado: `familia_alumnos_email_sin_vinculo`,
      `familia_alumnos_vinculo_sin_email`, `grupos_sin_sede` y `organizaciones_sin_sede` deben ser 0. Después,
      marcar el administrador de plataforma con SQL fuera del repositorio
      (`UPDATE usuario SET es_admin_plataforma = TRUE WHERE …`); la aplicación nunca escribe esa columna.
- [ ] Tras V31_2 (entrega 6-1, solo datos): ejecutar `docs/multi-escuela/postcheck-v31_2.sql` y guardar el resultado:
      `alumnos_sin_sede_escuelas_una_sede` debe ser 0 (en Moiskimdo, las altas posteriores a V31 quedan en
      «Umbrete»). Desde la 6-1 el alta de alumnos escribe la sede principal; desde la 6-2 también las altas desde
      una preinscripción finalizada (la sede del grupo de la solicitud; sin migración).
- [ ] Tras V31_3 (entrega 6-3, ampliación: `producto_alumno.sede_id` NULL, índice y FK, con relleno): ejecutar
      `docs/multi-escuela/postcheck-v31_3.sql` (solo lectura, solo recuentos) y guardar el resultado:
      `cargos_sin_sede_escuelas_una_sede` y `cargos_con_sede_de_otra_escuela` deben ser 0 (en Moiskimdo todos los
      cargos quedan en «Umbrete»; la segunda consulta da una fila con `sin_sede` = 0) y `cargos_total` debe coincidir
      con el recuento previo (`SELECT COUNT(*) FROM producto_alumno`). Los importes no se tocan. Desde la 6-3 cada cargo
      nuevo nace con la sede principal del alumno y nunca cambia; un alumno sin sede principal genera cargos sin sede
      (en una escuela de una sede no debería existir: lo vigilan este postcheck y `postcheck-v31_2.sql`; repetirlos
      tras el despliegue y, si alguno no da 0, asignar la sede principal y repetir el relleno de V31_3, idempotente).
      El código anterior sigue funcionando sobre el esquema
      ampliado (vuelta atrás sin migración), pero los cargos que cree quedan sin sede: si se vuelve atrás, repetir el
      relleno de V31_3 (sus dos `UPDATE`, idempotentes) al volver a la 6-3 y comprobar de nuevo el postcheck.
- [ ] Tras V31_4 (entrega 6-4, tabla nueva `tarifa_sede`, vacía): ejecutar `docs/multi-escuela/postcheck-v31_4.sql`
      (solo lectura, solo recuentos) y guardar el resultado: `tabla_tarifa_sede` = 1, `filas_tarifa_sede` = 0 y
      `flyway_31_4` = 1. Con la tabla vacía todas las escuelas cobran lo mismo que antes (Moiskimdo no cambia). El
      código anterior sigue funcionando sobre este esquema (no conoce la tabla).
- [ ] Tras V31_5 (pieza 6b-1, tablas nuevas `cobro`, `entrega_caja` y `movimiento_caja`, vacías): ejecutar
      `docs/multi-escuela/postcheck-v31_5.sql` (solo lectura, solo recuentos) y guardar el resultado: `tablas` = 3,
      las tres `filas_*` = 0, `flyway_31_5` = 1 y las informativas a 0. Sin relleno: los cargos ya pagados no tienen
      cobro. El código anterior sigue funcionando sobre este esquema (no conoce las tablas); si se vuelve atrás, los
      cobros que se hagan con él no dejan fila en `cobro`.
- [ ] **Antes de generar las mensualidades de enero de 2027**: crear la tarifa de la sede de Sierra Sur con
      `POST /api/tarifas-sede` (propietario o gestor de Sierra Sur). Los cargos ya generados NUNCA se recalculan
      (D6-32): si enero se genera antes, esos cargos quedan con el importe antiguo y hay que corregirlos a mano. Ejemplo
      (valores ficticios; `<SEDE_ID>` es el id de la sede, `<JWT>` la cookie de sesión de la cuenta de Sierra Sur):

      ```bash
      curl -X POST "https://<dominio>/api/tarifas-sede" \
        -H "Content-Type: application/json" --cookie "jwt=<JWT>" \
        -d '{"codigoTarifa":"<CODIGO_TARIFA>","sedeId":<SEDE_ID>,"importe":<IMPORTE>,"vigenteDesde":"2027-01"}'
      ```

      Si la tarifa cobra por rol familiar (tiene importe de adulto o de menor), añadir también `importeAdulto` e
      `importeMenor` (si no, 400). Comprobar después con `GET /api/tarifas-sede` y, antes de generar, con el listado
      mensual de enero (`/api/informes/listado-mensualidad-mensual?mesAno=2027-01`), que ya enseña el importe de la sede.
      Una fila de un mes futuro se puede borrar (`DELETE /api/tarifas-sede/{id}`); la del mes en curso o pasada, no.
- [ ] Antes de desplegar la entrega 6: ejecutar `docs/multi-escuela/precheck-e6.sql` (solo lectura, solo recuentos)
      y guardar el resultado. `preinscripciones_sin_turnos_resolubles` debería dar 0 filas: una fila así hace que el
      listado de preinscripciones responda 409 (comportamiento anterior, sin cambios; lo decide el propietario).
- [ ] **V32 (contracción de permisos) nunca en el mismo despliegue que V31**; antes, copia de seguridad (única
      vuelta atrás) y `postcheck-v31.sql` limpio.
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
obligatoria y se guarda hasta confirmar que la versión con V28 funciona. Lo mismo vale para V29 (mismo
despliegue y misma copia): borra `alumno.responsable_legal_nombre`/`_nif`, que el código anterior lee.

Desde V20 los roles viven en `membresia` y `usuario_rol` deja de actualizarse: volver al código
anterior exige restaurar la copia de seguridad.

Desde V27 el código escribe `tarifa_id` y sigue escribiendo `tipo_tarifa` (salvo códigos de tarifa propios
de otra escuela), y los productos que crea llevan `codigo`/`tipo`: el código anterior funciona sobre esos
datos sin cambios de esquema.

Desde V26 las cinco columnas `disciplina_id` son NOT NULL y el código anterior a V26 no las escribe.
Volver a ese código exige antes `ALTER TABLE … MODIFY disciplina_id BIGINT NULL` en `alumno_deporte`,
`grupo`, `convocatoria`, `plantilla_preinscripcion` y `preinscripcion` (en la práctica, volver atrás
después de V20 ya supone restaurar la copia de seguridad).
