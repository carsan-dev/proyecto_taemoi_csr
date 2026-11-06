-- Comprobación posterior de V32_2 (motivos de falta y festivos por sede, decisiones 455-463). Solo lectura y SOLO
-- RECUENTOS: no devuelve nombres, fechas, emails ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   flyway_32_2                  1   V32_2 aplicada con éxito
--   ultima_es_32_2               1   es la última versión aplicada
--   columna_motivo               1   asistencia_alumno.motivo_falta existe, VARCHAR(20) y admite NULL
--   tablas                       2   festivo y festivo_sede existen
--   claves_ajenas                6   las de las dos tablas, con su regla de borrado
--   faltas_con_motivo            0   V32_2 no rellena nada. Crece cuando se pasa lista con motivos.
--   presentes_con_motivo         0   SIEMPRE 0: un presente no lleva motivo.
--   motivos_desconocidos         0   SIEMPRE 0: solo INJUSTIFICADA, MEDICA, FAMILIAR u OTRA_JUSTIFICADA.
--   festivos                     0   V32_2 no crea ninguno. Crece cuando se marcan festivos.
--   festivos_sin_sede            0   SIEMPRE 0: todo festivo tiene al menos una sede.
--   sedes_de_otra_escuela        0   SIEMPRE 0: festivo_sede, su festivo y su sede son de la misma escuela.
--   festivos_con_forma_rara      0   SIEMPRE 0: DIA con desde = hasta, RANGO con desde < hasta, ANUAL con mes y día.
-- Informativa:
--   filas_de_asistencia              total de filas de asistencia; no cambia con V32_2
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '32.2' AND success = 1)
    AS flyway_32_2,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '32.2')
    AS ultima_es_32_2,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'asistencia_alumno' AND COLUMN_NAME = 'motivo_falta'
      AND COLUMN_TYPE = 'varchar(20)' AND IS_NULLABLE = 'YES')
    AS columna_motivo,
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('festivo', 'festivo_sede'))
    AS tablas,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND (
      (TABLE_NAME = 'festivo' AND DELETE_RULE = 'NO ACTION' AND REFERENCED_TABLE_NAME = 'organizacion')
      OR (TABLE_NAME = 'festivo' AND DELETE_RULE = 'SET NULL' AND REFERENCED_TABLE_NAME = 'usuario')
      OR (TABLE_NAME = 'festivo_sede' AND DELETE_RULE = 'NO ACTION' AND REFERENCED_TABLE_NAME = 'organizacion')
      OR (TABLE_NAME = 'festivo_sede' AND DELETE_RULE = 'CASCADE' AND REFERENCED_TABLE_NAME IN ('festivo', 'sede'))))
    AS claves_ajenas,
  (SELECT COUNT(*) FROM asistencia_alumno WHERE motivo_falta IS NOT NULL)
    AS faltas_con_motivo,
  (SELECT COUNT(*) FROM asistencia_alumno WHERE motivo_falta IS NOT NULL AND presente = TRUE)
    AS presentes_con_motivo,
  (SELECT COUNT(*) FROM asistencia_alumno
    WHERE motivo_falta IS NOT NULL AND motivo_falta NOT IN ('INJUSTIFICADA', 'MEDICA', 'FAMILIAR', 'OTRA_JUSTIFICADA'))
    AS motivos_desconocidos,
  (SELECT COUNT(*) FROM festivo)
    AS festivos,
  (SELECT COUNT(*) FROM festivo f WHERE NOT EXISTS (SELECT 1 FROM festivo_sede fs WHERE fs.festivo_id = f.id))
    AS festivos_sin_sede,
  (SELECT COUNT(*) FROM festivo_sede fs JOIN festivo f ON f.id = fs.festivo_id JOIN sede s ON s.id = fs.sede_id
    WHERE fs.organizacion_id <> f.organizacion_id OR fs.organizacion_id <> s.organizacion_id)
    AS sedes_de_otra_escuela,
  (SELECT COUNT(*) FROM festivo
    WHERE NOT IFNULL((
      (tipo = 'DIA' AND fecha_desde IS NOT NULL AND fecha_hasta = fecha_desde AND mes IS NULL AND dia IS NULL)
      OR (tipo = 'RANGO' AND fecha_desde IS NOT NULL AND fecha_hasta > fecha_desde AND mes IS NULL AND dia IS NULL)
      OR (tipo = 'ANUAL' AND fecha_desde IS NULL AND fecha_hasta IS NULL AND mes BETWEEN 1 AND 12
          AND dia BETWEEN 1 AND 31)), FALSE))
    AS festivos_con_forma_rara,
  (SELECT COUNT(*) FROM asistencia_alumno)
    AS filas_de_asistencia;
