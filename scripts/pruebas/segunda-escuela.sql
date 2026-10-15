-- Segunda escuela SINTÉTICA (organización 2) para probar el aislamiento multi-escuela.
-- Requiere la base migrada hasta V29 (V22 quitó de alumno las columnas monodeporte; V28 quitó los
-- enums deporte/tipo_tarifa/tipo_grado: la disciplina y la tarifa se identifican solo por
-- disciplina_id y tarifa_id; V29 quitó alumno.responsable_legal_*). organizacion 2 queda con
-- comportamiento_legado_taemoi = FALSE (valor por defecto).
-- Repetible: borra antes lo que crea. Contraseña de los usuarios: la de README.md.
-- La organización 2 tiene su propio catálogo: disciplinas TAEKWONDO, KICKBOXING y PILATES (sin
-- sistema de grados: sus alumnos no tienen grado, alumno_deporte.grado_id = NULL) y las tarifas
-- INFANTIL, ADULTO, KICKBOXING y PILATES. categoria sigue sin filas propias.
SET NAMES utf8mb4;

SET @hash = '$2a$10$cGP1XzmNaGM3aN1zBb2Xgu9kxqubleJSV4iUxyS/3HEiz5CeHig.a';

-- ---------------------------------------------------------------- limpieza (repetible)
DELETE FROM alumno_convocatoria WHERE organizacion_id = 2;
DELETE FROM convocatoria WHERE organizacion_id = 2;
DELETE pt FROM preinscripcion_turno pt JOIN preinscripcion p ON p.id = pt.preinscripcion_id WHERE p.organizacion_id = 2;
DELETE FROM preinscripcion WHERE organizacion_id = 2;
DELETE FROM plantilla_preinscripcion WHERE organizacion_id = 2;
DELETE FROM alumno_reto_diario_log WHERE organizacion_id = 2;
DELETE FROM registro_pendiente WHERE organizacion_id = 2;
DELETE FROM documento WHERE organizacion_id = 2;
DELETE FROM auditoria_evento WHERE organizacion_id = 2;
DELETE FROM evento WHERE organizacion_id = 2;
DELETE pa FROM producto_alumno pa WHERE pa.organizacion_id = 2;
DELETE at FROM alumno_turno at JOIN alumno a ON a.id = at.alumno_id WHERE a.organizacion_id = 2;
DELETE ag FROM alumno_grupo ag JOIN alumno a ON a.id = ag.alumno_id WHERE a.organizacion_id = 2;
DELETE FROM alumno_monodeporte_archivo WHERE organizacion_id = 2;
DELETE FROM alumno_salud WHERE organizacion_id = 2;
DELETE FROM consentimiento WHERE organizacion_id = 2;
DELETE FROM tutor WHERE organizacion_id = 2;
DELETE FROM alumno_deporte WHERE organizacion_id = 2;
DELETE FROM membresia WHERE organizacion_id = 2
   OR usuario_id IN (SELECT id FROM (SELECT id FROM usuario WHERE email IN
      ('propietario2@example.test', 'admin2@example.test', 'multiescuela@example.test')) u);
UPDATE usuario SET ultima_organizacion_id = NULL WHERE ultima_organizacion_id = 2;
DELETE FROM usuario WHERE email IN ('propietario2@example.test', 'admin2@example.test', 'multiescuela@example.test');
DELETE FROM alumno WHERE organizacion_id = 2;
DELETE FROM turno WHERE organizacion_id = 2;
DELETE FROM grupo WHERE organizacion_id = 2;
UPDATE disciplina SET producto_licencia_id = NULL, producto_competidor_id = NULL WHERE organizacion_id = 2;
DELETE FROM tarifa WHERE organizacion_id = 2;
DELETE FROM producto WHERE organizacion_id = 2;
DELETE FROM categoria WHERE organizacion_id = 2;
DELETE FROM grado WHERE organizacion_id = 2;
DELETE FROM disciplina WHERE organizacion_id = 2;
DELETE FROM sistema_grados WHERE organizacion_id = 2;
UPDATE alumno SET foto_alumno_id = NULL WHERE foto_alumno_id IN (SELECT id FROM (SELECT id FROM imagen WHERE organizacion_id = 2) i);
DELETE FROM imagen WHERE organizacion_id = 2;
DELETE FROM contador WHERE organizacion_id = 2;
DELETE FROM configuracion_organizacion WHERE organizacion_id = 2;
DELETE FROM organizacion WHERE id = 2;

START TRANSACTION;

-- ---------------------------------------------------------------- organización
INSERT INTO organizacion (id, slug, nombre, localidad, provincia, email_contacto, zona_horaria, activa)
VALUES (2, 'sierra-sur-sintetica', 'Escuela Sierra Sur (sintetica)', 'Localidad Sintetica', 'Sevilla',
        'contacto@sierra-sur.example.test', 'Europe/Madrid', TRUE);
INSERT INTO configuracion_organizacion (organizacion_id, clave, valor) VALUES (2, 'limite_turno', '24');

-- ---------------------------------------------------------------- catálogo propio de la org 2
INSERT INTO disciplina (id, organizacion_id, codigo, nombre, sistema_grados_id, permite_competicion,
  requiere_licencia, edad_adulto, orden, activa) VALUES
  (101, 2, 'TAEKWONDO', 'Taekwondo', NULL, 1, 1, 14, 1, 1),
  (102, 2, 'KICKBOXING', 'Kickboxing', NULL, 1, 1, 15, 2, 1),
  (103, 2, 'PILATES', 'Pilates', NULL, 0, 0, 14, 3, 1);
INSERT INTO tarifa (id, organizacion_id, disciplina_id, codigo, nombre, importe, activa) VALUES
  (101, 2, 101, 'INFANTIL', 'Infantil', 32.00, 1),
  (102, 2, 101, 'ADULTO', 'Adulto', 32.00, 1),
  (103, 2, 102, 'KICKBOXING', 'Kickboxing', 30.00, 1),
  (104, 2, 103, 'PILATES', 'Pilates', 30.00, 1);

-- ---------------------------------------------------------------- grupo, turno y productos
INSERT INTO grupo (id, nombre, tipo, rango_edad_min, rango_edad_max, organizacion_id, disciplina_id)
VALUES (101, 'Sierra Sur Taekwondo Lunes y Miercoles', 'Taekwondo', NULL, NULL, 2, 101);
INSERT INTO turno (id, dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id) VALUES
  (201, 'Lunes', '18:00', '19:00', 'Sierra Sur Taekwondo Lunes', 101, 2),
  (202, 'Miércoles', '18:00', '19:00', 'Sierra Sur Taekwondo Miércoles', 101, 2);
INSERT INTO producto (id, concepto, precio, organizacion_id, codigo, tipo, disciplina_id, grado_id) VALUES
  (101, 'MENSUALIDAD', 32.0, 2, 'MENSUALIDAD', 'MENSUALIDAD', NULL, NULL),
  (102, 'LICENCIA FEDERATIVA', 0.0, 2, 'LICENCIA_FEDERATIVA', 'LICENCIA', NULL, NULL),
  (103, 'RESERVA DE PLAZA', 15.0, 2, 'RESERVA_PLAZA', 'RESERVA_PLAZA', NULL, NULL);

-- ---------------------------------------------------------------- alumnos (expedientes 1..10, propios de la org 2)
INSERT INTO alumno (id, activo, apellidos, autorizacion_web, direccion, email, fecha_alta, fecha_alta_inicial,
  fecha_baja, fecha_nacimiento, nif, nombre, numero_expediente, telefono, tiene_derecho_examen,
  tiene_discapacidad, organizacion_id) VALUES
(1001, 1, 'Sur Alcala', 1, 'Calle Sintetica 1', 'sur.familia1@example.test', '2026-09-07', '2026-09-07', NULL, '2015-03-11', '45000001H', 'Ines', 1, '611000001', 0, 0, 2),
(1002, 1, 'Sur Alcala', 1, 'Calle Sintetica 1', 'sur.familia1@example.test', '2026-09-07', '2026-09-07', NULL, '2017-06-02', NULL, 'Teo', 2, '611000001', 0, 0, 2),
(1003, 1, 'Sur Benitez', 1, 'Calle Sintetica 2', 'sur.alumno3@example.test', '2026-09-08', '2026-09-08', NULL, '1990-01-20', '45000003C', 'Marta', 3, '611000003', 0, 0, 2),
(1004, 1, 'Sur Cordero', 1, 'Calle Sintetica 3', 'sur.alumno4@example.test', '2026-09-08', '2026-09-08', NULL, '1985-07-14', '45000004K', 'Raul', 4, '611000004', 0, 0, 2),
(1005, 1, 'Sur Duran', 1, 'Calle Sintetica 4', 'sur.alumno5@example.test', '2026-09-09', '2026-09-09', NULL, '2014-11-30', '45000005E', 'Alba', 5, '611000005', 0, 0, 2),
(1006, 1, 'Sur Esteban', 1, 'Calle Sintetica 5', 'sur.alumno6@example.test', '2026-09-09', '2026-09-09', NULL, '2016-02-08', '45000006T', 'Leo', 6, '611000006', 0, 0, 2),
(1007, 1, 'Sur Fajardo', 1, 'Calle Sintetica 6', 'sur.alumno7@example.test', '2026-09-10', '2026-09-10', NULL, '1995-05-05', '45000007R', 'Ivan', 7, '611000007', 0, 0, 2),
(1008, 1, 'Sur Galindo', 1, 'Calle Sintetica 7', NULL, '2026-09-10', '2026-09-10', NULL, '2012-09-19', '45000008W', 'Celia', 8, '611000008', 0, 0, 2),
(1009, 1, 'Sur Herrador', 1, 'Calle Sintetica 8', 'sur.alumno9@example.test', '2026-09-11', '2026-09-11', NULL, '2018-12-01', NULL, 'Gael', 9, '611000009', 0, 0, 2),
(1010, 0, 'Sur Iglesias', 1, 'Calle Sintetica 9', 'sur.alumno10@example.test', '2026-09-11', '2026-09-11', '2026-10-01', '1979-04-22', '45000010G', 'Eva', 10, '611000010', 0, 0, 2);

INSERT INTO alumno_deporte (id, activo, principal, apto_para_examen, fecha_alta, fecha_alta_inicial,
  fecha_baja, cuantia_tarifa, rol_familiar, competidor, tiene_licencia, alumno_id, grado_id,
  organizacion_id, disciplina_id, tarifa_id) VALUES
(1001, 1, 1, 0, '2026-09-07', '2026-09-07', NULL, 32.0, 'NINGUNO', 0, 0, 1001, NULL, 2, 101, 101),
(1002, 1, 1, 0, '2026-09-07', '2026-09-07', NULL, 32.0, 'NINGUNO', 0, 0, 1002, NULL, 2, 101, 101),
(1003, 1, 1, 0, '2026-09-08', '2026-09-08', NULL, 32.0, 'NINGUNO', 0, 0, 1003, NULL, 2, 101, 102),
(1004, 1, 1, 0, '2026-09-08', '2026-09-08', NULL, 32.0, 'NINGUNO', 0, 0, 1004, NULL, 2, 101, 102),
(1005, 1, 1, 0, '2026-09-09', '2026-09-09', NULL, 32.0, 'NINGUNO', 0, 0, 1005, NULL, 2, 101, 101),
(1006, 1, 1, 0, '2026-09-09', '2026-09-09', NULL, 32.0, 'NINGUNO', 0, 0, 1006, NULL, 2, 101, 101),
(1007, 1, 1, 0, '2026-09-10', '2026-09-10', NULL, 30.0, 'NINGUNO', 0, 0, 1007, NULL, 2, 102, 103),
(1008, 1, 1, 0, '2026-09-10', '2026-09-10', NULL, 32.0, 'NINGUNO', 0, 0, 1008, NULL, 2, 101, 102),
(1009, 1, 1, 0, '2026-09-11', '2026-09-11', NULL, 32.0, 'NINGUNO', 0, 0, 1009, NULL, 2, 101, 101),
(1010, 0, 1, 0, '2026-09-11', '2026-09-11', '2026-10-01', 32.0, 'NINGUNO', 0, 0, 1010, NULL, 2, 101, 102);

INSERT INTO alumno_grupo (grupo_id, alumno_id)
SELECT 101, ad.alumno_id FROM alumno_deporte ad
WHERE ad.organizacion_id = 2 AND ad.activo = 1 AND ad.disciplina_id = 101;
INSERT INTO alumno_turno (alumno_id, turno_id)
SELECT ad.alumno_id, t.id FROM alumno_deporte ad JOIN turno t ON t.grupo_id = 101
WHERE ad.organizacion_id = 2 AND ad.activo = 1 AND ad.disciplina_id = 101;

INSERT INTO producto_alumno (cantidad, concepto, fecha_asignacion, fecha_pago, notas, pagado, precio, alumno_id,
  producto_id, alumno_deporte_id, organizacion_id)
SELECT 1, CONCAT('MENSUALIDAD OCTUBRE 2026 - ', d.codigo), '2026-10-01 10:00:00',
       IF(MOD(ad.alumno_id, 3) = 0, NULL, '2026-10-03 18:00:00'), NULL, MOD(ad.alumno_id, 3) <> 0,
       ad.cuantia_tarifa, ad.alumno_id, 101, ad.id, 2
FROM alumno_deporte ad JOIN disciplina d ON d.id = ad.disciplina_id
WHERE ad.organizacion_id = 2 AND ad.activo = 1;
INSERT INTO producto_alumno (cantidad, concepto, fecha_asignacion, fecha_pago, notas, pagado, precio, alumno_id,
  producto_id, alumno_deporte_id, organizacion_id)
VALUES (1, 'RESERVA DE PLAZA', '2026-09-07 10:00:00', '2026-09-07 10:05:00', NULL, 1, 15.0, 1001, 103, 1001, 2);

-- ---------------------------------------------------------------- usuarios y membresías
INSERT INTO usuario (apellidos, contrasena, email, nombre, alumno_id, auth_provider, recordatorio_racha_email_habilitado)
VALUES ('Sierra Sur', @hash, 'propietario2@example.test', 'Propietaria', NULL, 'LOCAL', 0),
       ('Sierra Sur', @hash, 'admin2@example.test', 'Administrador', NULL, 'LOCAL', 0);
INSERT INTO membresia (usuario_id, organizacion_id, rol)
SELECT id, 2, 'PROPIETARIO' FROM usuario WHERE email = 'propietario2@example.test';
INSERT INTO membresia (usuario_id, organizacion_id, rol)
SELECT id, 2, 'ADMINISTRADOR' FROM usuario WHERE email = 'admin2@example.test';

-- Usuario en las dos escuelas: ADMINISTRADOR en la 2 y FAMILIA en la 1. Se le asigna el correo
-- del primer alumno activo de la org 1 cuyo correo no tiene usuario (por id, determinista).
SET @alumno_multi = (
  SELECT a.id FROM alumno a
  WHERE a.organizacion_id = 1 AND a.activo = 1 AND a.email IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM usuario u WHERE u.email = a.email)
    AND NOT EXISTS (SELECT 1 FROM usuario u WHERE u.alumno_id = a.id)
    AND (SELECT COUNT(*) FROM alumno b WHERE b.email = a.email) = 1
  ORDER BY a.id LIMIT 1);
UPDATE alumno SET email = 'multiescuela@example.test' WHERE id = @alumno_multi;
INSERT INTO usuario (apellidos, contrasena, email, nombre, alumno_id, auth_provider, recordatorio_racha_email_habilitado)
SELECT LEFT(a.apellidos, 50), @hash, 'multiescuela@example.test', LEFT(a.nombre, 50), a.id, 'LOCAL', 0
FROM alumno a WHERE a.id = @alumno_multi;
INSERT INTO membresia (usuario_id, organizacion_id, rol)
SELECT id, 1, 'FAMILIA' FROM usuario WHERE email = 'multiescuela@example.test'
UNION ALL
SELECT id, 2, 'ADMINISTRADOR' FROM usuario WHERE email = 'multiescuela@example.test';

COMMIT;
