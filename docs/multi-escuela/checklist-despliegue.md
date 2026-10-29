# Checklist de despliegue multi-escuela

Procedimiento paso a paso en el servidor: [docs/operacion/procedimiento-produccion.md](../operacion/procedimiento-produccion.md).

## CI tras fusionar (decisión 215)
- En el push a `develop`/`main`, el job `ya-probado` salta `backend-tests`, `frontend-tests` y `e2e` si el commit es
  un merge de dos padres y un run de PR en verde de su misma cabeza (los tres jobs en `success`) probó el MISMO árbol
  (artefacto `arbol-<hash>` que sube `huella-pr` en cada PR). Entonces `publish-images` arranca directamente.
- Cualquier otro caso (push directo, merge squash o rebase, base que avanzó tras el run de la PR, run anterior a la
  huella, error de la API) prueba todo. El resumen del job `ya-probado` dice por qué y qué run reutiliza.
- Forzar todo: Actions → CI → «Run workflow» en `develop`/`main` con `forzar_tests` marcado (por defecto); publica imágenes.

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
- [ ] Tras V31_6 (pieza 6b-2c, columnas `cancelado_en`, `cancelado_por_usuario_id` y `motivo_cancelacion` en
      `producto_alumno`, todas NULL): ejecutar `docs/multi-escuela/postcheck-v31_6.sql` (solo lectura, solo recuentos)
      y guardar el resultado: `columnas_cancelacion` = 3, `columnas_nulables` = 3, `idx_cancelado_por` = 1,
      `fk_cancelado_por` = 1, `fk_cancelado_por_set_null` = 1, `flyway_31_6` = 1, `cargos_cancelados` = 0,
      `columna_vendido_por` = 1, `idx_vendido_por` = 1, `fk_vendido_por` = 1, `fk_vendido_por_set_null` = 1 y las
      informativas a 0; `ventas_del_profesor` (ventas del profesor desde la 6b-2 que marcó el relleno de
      `vendido_por_usuario_id` desde la auditoría, decisión 172) igual a `ventas_auditadas` y
      `ventas_auditadas_sin_marcar` = 0. Las columnas son INSTANT y las FK se añaden sin reconstruir la tabla. El código anterior
      sigue funcionando sobre este esquema (no conoce las columnas); si se vuelve atrás con cargos ya cancelados, ese
      código los contaría otra vez como pendientes (reactivarlos o anotarlos antes).
- [ ] Tras V31_8 (pieza 6c-2, tabla nueva `solicitud`, vacía, y columnas `cobro_id`, `producto_alumno_id`, `metodo` y
      `cobro_devuelto_activo_id` al final de `movimiento_caja`; despliegue **con migración**, atajo
      `desplegar_migrar`): ejecutar `docs/multi-escuela/postcheck-v31_8.sql` (solo lectura, solo recuentos) y guardar
      el resultado: `tabla_solicitud` = 1, `filas_solicitud` = 0, `columnas_virtuales_solicitud` = 4,
      `uk_solicitud` = 4, `fk_solicitud` = 13 (`fk_solicitud_set_null` = 8, `fk_solicitud_sin_accion` = 5),
      `columnas_movimiento_caja` = 4, `metodo_no_nulo_efectivo` = 1, `uk_cobro_devuelto_activo` = 1,
      `fk_movimiento_caja` = 8 (`fk_movimiento_caja_set_null` = 4, `fk_movimiento_caja_sin_accion` = 4),
      `flyway_31_8` = 1, `movimientos_no_efectivo` = 0,
      `sedes_con_saldo_distinto_solo_efectivo` = 0 (el saldo de caja de cada sede no cambia) y las informativas a 0.
      Las columnas son INSTANT y las FK se añaden sin reconstruir la tabla. Sin pantallas: Moiskimdo no ve nada nuevo
      hasta la 6c-7. El código anterior sigue funcionando sobre este esquema (no conoce la tabla; sus movimientos nuevos
      quedan EFECTIVO por el DEFAULT); si se vuelve atrás, las solicitudes ya pedidas se quedan sin pantalla.
- [ ] Resto de la entrega 6c (6c-3 a 6c-9) **sin migración nueva**: V31_8 ya está en producción; atajo `desplegar`.
      Los encargos (6c-5 y 6c-8) no salen sin la 6c-6 (ya incluida). Moiskimdo solo nota lo decidido: el aviso
      completo de la 186 («El examen está pagado: haz la devolución o revierte el cobro antes»), la bandeja con su
      contador y, si algún día hay devoluciones enlazadas, la marca «Devuelto» en tesorería y ficha (hoy no tiene
      ninguna: el resumen de tesorería no enseña la línea de lo devuelto y sus totales no cambian).
- [ ] Prueba manual de la 6c-9 tras desplegar, en Sierra Sur y con datos ficticios o un alumno de prueba (nunca
      datos reales en capturas ni notas):
      1. el profesor encarga un producto desde Mi caja («Pedidos»); en la bandeja de gestión («Encargos en curso»)
         sale con su línea de estados y «Sin cobrar», y el contador de la cabecera sube;
      2. «Marcar encargado» y «Marcar recibido» lo avanzan; el profesor lo ve en Mi caja y lo marca entregado;
      3. otro encargo cobrado por el profesor y rechazado desde la bandeja con motivo: sale «Devolución pendiente: la
         registra el profesor» (sin botón); el profesor la registra desde Mi caja y su cobro sale de «Llevas encima»;
      4. otro encargo cobrado, **entregado a la sede** y retirado: en la bandeja, «Registrar devolución» (confirma
         alumno, concepto e importe); tras registrarla, la tesorería marca la fila «Devuelto» y el resumen dice
         «De ello, … EUR devueltos»; la ficha del alumno enseña «devuelto» en lugar de la casilla de pagado; la caja de
         sede baja ese importe;
      5. en la caja de sede, la entrega de la devolución del profesor (paso 3) dice «cuadra» con «Devolución −…» y,
         si se aceptó un «deshacer cobro entregado» (tipo 2), su entrega enseña «Cobro deshecho +…»;
      6. el contador vuelve a bajar tras cada acción, sin recargar la página.
- [ ] Avisar a la gestión de Moiskimdo (cambio deliberado de la 6b-2c): borrar un cargo a mano pasa a ser solo del
      propietario y solo si nunca se cobró; la gestión y el responsable lo **cancelan** (con motivo). El botón
      «Borrar» de la ficha da 403 al gestor hasta que el frontend pinte «Cancelar».
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
- [ ] E7 (profesor con sus grupos; asignación de profesores; marca por escuela en PDF y correos): sin migración →
      atajo `desplegar`. Prueba manual: asignar un profesor ficticio a un grupo en la escuela de pruebas, entrar como él
      (Mis grupos, ficha, salud, pase y cobro en Mi caja), y subir un logo como superadmin a una escuela que no sea
      Moiskimdo y generar un PDF.
- [ ] E8 (miembros, sedes, permisos de la sesión e invitaciones por correo): **con migración V31_9** (pieza E8-4,
      decisión 223: `usuario.contrasena` pasa a admitir NULL; ninguna fila cambia) → atajo `desplegar_migrar`.
      Comprobar después (solo lectura): Flyway en `31.9`
      (`SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1`), la columna
      con `IS_NULLABLE = 'YES'` y el mismo `COLUMN_TYPE`, `CHARACTER_SET_NAME` y `COLLATION_NAME` que antes, y
      `SELECT COUNT(*) FROM usuario WHERE contrasena IS NULL` = solo las invitaciones pendientes (0 justo tras migrar).
      El código anterior funciona sobre este esquema; volver a NOT NULL exige antes dar contraseña a las invitaciones
      pendientes o borrarlas. El enlace de los correos usa `app.frontend.base-url` (el mismo que restablecer
      contraseña; pendiente conocido del dominio único). Prueba manual en la escuela de pruebas con un email ficticio
      propio: invitar desde «Profesores», que llegue el correo con la marca de esa escuela, crear la contraseña con el
      enlace (entra en esa escuela) y que el enlace ya no sirva una segunda vez.
- [ ] E8b, pieza 8b-1 (base del panel nuevo; decisión 255): sin migración → atajo `desplegar`. Cambia el CSS global
      de todos (fuera el tema de Angular Material) sin cambiar nada visible. Comprobar en el móvil portada, login,
      área de familias y panel (Alumnos, Tesorería, Mi caja) como antes, y que
      `curl -sI https://moiskimdo.es/panel-nuevo.css` responde 200 con `Cache-Control: no-cache` y las cabeceras de
      seguridad (CSP, HSTS, X-Frame-Options, nosniff, Referrer-Policy). Igual para
      `curl -sI https://moiskimdo.es/gestion-compartida.css`.
- [ ] E8b, pieza 8b-I (interruptor del diseño nuevo por usuario; decisiones 255, 256 y 258): con migración V31_10
      (`diseno_panel_usuario`, tabla nueva y vacía; solo amplía) → atajo `desplegar_migrar`. Con el interruptor apagado
      (por defecto) nadie nota nada; solo el propietario ve «Diseño nuevo» en su menú de cuenta del panel, con la
      pantalla de asignación («Para todo el equipo», oculto hasta R5). Después, solo lectura: Flyway en 31.10
      (`SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1`) y
      `SELECT COUNT(*) FROM diseno_panel_usuario` = 0. Comprobar en el móvil que el propietario (también profesor en
      Umbrete) sigue viendo «Mi caja» y los mismos menús, y que un gestor no ve «Diseño nuevo».
- [ ] E8b, pieza 8b-T (color y logo de la escuela en el panel; decisiones 255 y 257): sin migración (las columnas
      `color_primario` y `logo_imagen_id` son de V18) → atajo `desplegar`. Después, el superadmin entra en «Logo y
      color» y pone `#1B2B2E` a Moiskimdo (decisión 257; la vista previa dice texto blanco, 14,66:1); queda en el
      registro de acciones de Moiskimdo como `PLATAFORMA MARCA_COLOR organizacion=#1 quitado=NO`, sin el valor.
      Comprobar en el móvil, con el interruptor apagado, que nada cambia para el propietario ni el gestor (cabecera,
      menús, logo de siempre y «Mi caja»): con él apagado nadie lee la marca.
- [ ] E8b, pieza 8b-N (navegación del panel nuevo; decisiones 255, 256 y 259): sin migración → atajo `desplegar`.
      Con el interruptor apagado, todo como antes (cabecera, pie y rutas). Después, el propietario, con su interruptor
      encendido, recorre en el móvil las 6 entradas (Inicio, Alumnos, Grupos, Tesorería, Solicitudes y Escuela) desde
      «Más» y la barra inferior (Inicio, Alumnos, Tesorería), comprueba que sigue viendo «Mi caja», «Mis grupos»,
      «Registro de acciones», «Configuración» y «Diseño nuevo», que el contador de Solicitudes coincide con el de hoy y
      que la portada y el área de familias siguen con la cabecera y el pie de hoy. Al terminar, «Volver al diseño
      anterior» desde su cuenta lo deja como antes.
- [ ] E8b, pieza 8b-2 (Inicio I1; decisiones 242, 255, 267 y 268): sin migración → atajo `desplegar`. Con el
      interruptor apagado, el inicio de hoy sin cambios y la tesorería cobra igual (mismo diálogo «Confirmar cobro»).
      Después, el propietario, con su interruptor encendido, abre Inicio y compara «Pendiente de cobro · vencido» con
      la tesorería: los desgloses por tipo, mes y sede suman el total, y las mensualidades del mes en curso solo
      cuentan desde el día 6. Comprueba la ocupación de la semana y «Hoy» frente a los grupos reales; no cobra nada.
- [ ] E8b, pieza 8b-3a (Alumnos C; decisiones 251, 253 y 267): sin migración → atajo `desplegar`. Con el
      interruptor apagado, el listado de alumnos de hoy sin cambios (las herramientas del pie, «Generar informe», «Dar
      de baja» y «Dar de alta» hacen lo mismo). Después, el propietario, con su interruptor encendido, abre Alumnos:
      el contador da el total de la escuela (no el de la página) y cambia con la búsqueda, la disciplina, «Inactivos» y
      «Solo aptos»; el pendiente de cada alumno coincide con lo vencido de la tesorería; y prueba «Cobrar» solo sobre un
      cargo ficticio de la escuela de pruebas, nunca de Moiskimdo.
- [ ] E8b, pieza 8b-3b (ficha del alumno F1 «Carnet»; decisión 254): sin migración → atajo `desplegar`. Con el
      interruptor apagado, la ficha de hoy sin cambios (sus diálogos de foto, agregar deporte, actualizar grado,
      convocatorias y WhatsApp salen ahora de piezas compartidas y se ven igual). Después, el propietario, con su
      interruptor encendido, abre la ficha de un alumno real y recorre las cuatro pestañas (Deportes, Pagos, Datos y
      contacto, Documentos y notas) sin guardar cambios: comprueba que el pendiente del carnet coincide con el del
      listado, que el cinturón blanco se distingue, que «1 de N» conserva la pestaña y que «← Alumnos» vuelve al
      listado con su búsqueda, filtros y página; y prueba «Cobrar» solo sobre un cargo ficticio de la escuela de
      pruebas, nunca de Moiskimdo.
- [ ] E8b, pieza 8b-D1 (diálogos, avisos y carga del panel nuevo; decisiones 269, 270 y 271): sin migración → atajo
      `desplegar`. Con el interruptor apagado, todo igual: el listado de alumnos de hoy con sus tres herramientas del
      pie, la tesorería con su «Revertir» y las fichas y familias con SweetAlert2. Después, el propietario, con su
      interruptor encendido, en la escuela de pruebas: prueba un cobro solo sobre un cargo ficticio (diálogo «Cobrar»
      con el método en pastillas y aviso «Cobro registrado»), abre «Dar de baja» de un alumno y lo cancela con Esc (no
      cambia nada), y abre desde «Informes» las tres herramientas (gestión de mensualidades y licencias, listado de
      asistencia y listado de mensualidades) en el panel lateral, con «Ver PDF» en los dos listados. Nunca sobre
      Moiskimdo.
- [ ] E8b, pieza 8b-C (color de la escuela en el panel nuevo, variante B; decisión 273): sin migración → atajo
      `desplegar`. Con el interruptor apagado, todo igual (también «Diseño nuevo»). Después, el propietario, con su
      interruptor encendido, revisa Inicio, Alumnos y la ficha: pestaña activa, filtros y «Filas | Tarjetas» elegidos,
      secundarios, enlaces y la página actual en el acento de Moiskimdo (`#01707F` sobre `#D5F5FC`, borde `#4595A3`);
      la cabecera, el botón principal, los estados y la ocupación, como antes.
- [ ] Retirada del interruptor del diseño nuevo (decisión del propietario del 29/10: código y tabla juntos):
      **con migración V31_13** (contracción en versión decimal: borra la tabla `diseno_panel_usuario` y la clave
      `panel.diseno_por_defecto` de `configuracion_organizacion` en todas las escuelas) → atajo `desplegar_migrar`.
      Después, solo lectura: Flyway en 31.13
      (`SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1`), la tabla
      no existe (`SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME =
      'diseno_panel_usuario'` = 0) y `SELECT COUNT(*) FROM configuracion_organizacion WHERE clave =
      'panel.diseno_por_defecto'` = 0. Vuelta atrás: restaurar la copia de seguridad de `desplegar_migrar`; la imagen
      anterior sola no arranca (`ddl-auto=validate` exige la tabla). Comprobar en el móvil que el propietario y un
      gestor entran en el panel con sus menús de siempre (también «Mi caja») y que ya no aparece «Diseño nuevo».

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
