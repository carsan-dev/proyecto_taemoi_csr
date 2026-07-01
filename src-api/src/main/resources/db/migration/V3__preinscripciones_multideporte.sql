CREATE TABLE plantilla_preinscripcion (
  id BIGINT NOT NULL AUTO_INCREMENT,
  deporte VARCHAR(50) NOT NULL,
  version INT NOT NULL,
  activa BIT NOT NULL DEFAULT 1,
  contenido LONGTEXT NOT NULL,
  instrucciones LONGTEXT NOT NULL,
  creada_en DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plantilla_deporte_version (deporte, version),
  KEY idx_plantilla_activa (deporte, activa)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE preinscripcion (
  id BIGINT NOT NULL AUTO_INCREMENT,
  referencia VARCHAR(32) NOT NULL,
  temporada VARCHAR(9) NOT NULL,
  deporte VARCHAR(50) NOT NULL,
  estado VARCHAR(20) NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  apellidos VARCHAR(160) NOT NULL,
  dni VARCHAR(16) NOT NULL,
  fecha_nacimiento DATE NOT NULL,
  direccion VARCHAR(255) NOT NULL,
  telefono VARCHAR(20) NOT NULL,
  email VARCHAR(180) NOT NULL,
  tutor_nombre VARCHAR(180) NULL,
  tutor_dni VARCHAR(16) NULL,
  consentimiento_fotografico BIT NOT NULL DEFAULT 0,
  aceptacion_normas BIT NOT NULL,
  firmante_nombre VARCHAR(180) NOT NULL,
  firma MEDIUMBLOB NOT NULL,
  turno_id BIGINT NOT NULL,
  turno_snapshot LONGTEXT NOT NULL,
  plantilla_id BIGINT NOT NULL,
  plantilla_snapshot LONGTEXT NOT NULL,
  pdf_firmado MEDIUMBLOB NOT NULL,
  token_documento_hash VARCHAR(64) NOT NULL,
  email_enviado BIT NOT NULL DEFAULT 0,
  email_intentos INT NOT NULL DEFAULT 0,
  email_ultimo_error VARCHAR(500) NULL,
  creada_en DATETIME(6) NOT NULL,
  actualizada_en DATETIME(6) NOT NULL,
  finalizada_en DATETIME(6) NULL,
  cancelada_en DATETIME(6) NULL,
  alumno_id BIGINT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_preinscripcion_referencia (referencia),
  UNIQUE KEY uk_preinscripcion_dni_deporte_temporada (dni, deporte, temporada),
  KEY idx_preinscripcion_filtros (temporada, deporte, estado),
  CONSTRAINT fk_pre_turno FOREIGN KEY (turno_id) REFERENCES turno(id),
  CONSTRAINT fk_pre_plantilla FOREIGN KEY (plantilla_id) REFERENCES plantilla_preinscripcion(id),
  CONSTRAINT fk_pre_alumno FOREIGN KEY (alumno_id) REFERENCES alumno(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO plantilla_preinscripcion
  (deporte, version, activa, contenido, instrucciones, creada_en)
VALUES
('TAEKWONDO',1,1,'{"cabecera":"Escuela Moi Kim Do","contacto":"Umbrete (Sevilla)","titulo":"Solicitud de preinscripción de Taekwondo","consentimiento":"Autorizo de forma opcional el uso de imágenes en actividades del club.","normas":["La temporada comienza en septiembre y finaliza en junio, ambos inclusive; las clases siguen el calendario escolar y la licencia federativa debe tramitarse a la mayor brevedad.","Las clases infantiles, precadete, cadete, junior y senior son dos horas semanales, en dos días alternos de lunes a jueves.","La Escuela podrá cambiar el turno del alumno según sus necesidades deportivas.","El equipo de competición lo elige el Maestro. Sus integrantes cumplirán entrenamientos y eventos adicionales y deberán estar federados, disponer de protecciones y chándal y abonar la cuota extra vigente.","En los entrenamientos se usará el uniforme oficial de Taekwondo de la Escuela, con cinturón y escudo.","El alumnado cuidará su aseo personal y no usará relojes, anillos, pulseras, cadenas, pendientes ni colgantes.","Se respetará al Maestro, compañeros, instalaciones y materiales.","Las bajas se comunicarán el mes anterior. La baja temporal exige la cuota de mantenimiento vigente para conservar plaza; dos semanas de ausencia inicial sin aviso implican baja.","Las mensualidades se abonarán en efectivo en la Escuela del día 1 al 5 del mes corriente; el pago fuera de plazo puede llevar el recargo vigente.","La falta de asistencia no exime del pago de la mensualidad.","La licencia federativa será obligatoria tras el primer grado, con importes según edad, fecha y condiciones de la Federación.","Los cambios de cinturón se realizan mediante examen en junio o diciembre, o por evaluación continua, con convocatoria, licencia, pagos y tasas correspondientes.","La reserva para la siguiente temporada requiere estar al corriente o mantener plaza y abonar la reserva en plazo; en otro caso se pierde el derecho de plaza y procede matrícula.","La Escuela se reserva el derecho de admisión."],"importes":"El pago se formaliza presencialmente al inicio de la temporada."}','Conserva tu referencia y acude al club para formalizar la inscripción y el pago.',CURRENT_TIMESTAMP(6)),
('KICKBOXING',1,1,'{"cabecera":"Escuela Moi Kim Do","contacto":"Umbrete (Sevilla)","titulo":"Solicitud de preinscripción de Kickboxing","consentimiento":"Autorizo de forma opcional el uso de imágenes en actividades del club.","normas":["La temporada comienza en septiembre y finaliza en junio, ambos inclusive; se sigue el calendario escolar y debe tramitarse la licencia federativa a la mayor brevedad.","Las clases son dos horas semanales en dos días alternos de lunes a jueves.","Es obligatorio disponer cuanto antes del equipo completo de protecciones: casco, bucal, guantes, calzonas, camiseta, coquilla y espinilleras con cubreempeine; las vendas son opcionales.","El alumnado cuidará su aseo personal y no usará relojes, anillos, pulseras, cadenas, pendientes ni colgantes.","Se respetará al Maestro, compañeros, instalaciones y materiales.","Las bajas se comunicarán el mes anterior. La baja temporal exige la cuota mensual de mantenimiento vigente; dos semanas de ausencia inicial sin aviso implican baja.","Las mensualidades se abonarán en efectivo en la Escuela del día 1 al 5 del mes corriente; el pago fuera de plazo puede llevar el recargo vigente.","La falta de asistencia no exime del pago de la mensualidad.","Los exámenes de cinturón se celebran en junio y diciembre y requieren convocatoria del Maestro, licencia en vigor, pagos al corriente y tasa de examen.","La reserva para septiembre requiere estar al corriente de junio o abonar mantenimiento y reserva en plazo; en otro caso se pierde el derecho de plaza y procede matrícula.","La Escuela se reserva el derecho de admisión."],"importes":"El pago se formaliza presencialmente al inicio de la temporada."}','Conserva tu referencia y acude al club para formalizar la inscripción y el pago.',CURRENT_TIMESTAMP(6)),
('PILATES',1,1,'{"cabecera":"Escuela Moi Kim Do","contacto":"Umbrete (Sevilla)","titulo":"Solicitud de preinscripción de Pilates","consentimiento":"Autorizo de forma opcional el uso de imágenes en actividades del club.","normas":["Comunicar lesiones o limitaciones antes de la actividad.","Asistir con ropa cómoda y respetar el horario."],"importes":"El pago se formaliza presencialmente al inicio de la temporada."}','Conserva tu referencia y acude al club para formalizar la inscripción y el pago.',CURRENT_TIMESTAMP(6)),
('DEFENSA_PERSONAL_FEMENINA',1,1,'{"cabecera":"Escuela Moi Kim Do","contacto":"Umbrete (Sevilla)","titulo":"Solicitud de preinscripción de Defensa Personal Femenina","consentimiento":"Autorizo de forma opcional el uso de imágenes en actividades del club.","normas":["Comunicar lesiones o limitaciones antes de la actividad.","Respetar la confidencialidad y el espacio de las participantes."],"importes":"El pago se formaliza presencialmente al inicio de la temporada."}','Conserva tu referencia y acude al club para formalizar la inscripción y el pago.',CURRENT_TIMESTAMP(6));
