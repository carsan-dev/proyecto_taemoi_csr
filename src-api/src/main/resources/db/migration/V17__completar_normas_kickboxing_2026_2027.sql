SET @nueva_version_kickboxing = (
  SELECT COALESCE(MAX(version), 0) + 1
  FROM plantilla_preinscripcion
  WHERE deporte = 'KICKBOXING'
);

INSERT INTO plantilla_preinscripcion
  (deporte, version, activa, contenido, instrucciones, creada_en)
SELECT
  deporte,
  @nueva_version_kickboxing,
  1,
  JSON_SET(
    CAST(contenido AS JSON),
    '$.normas',
    JSON_ARRAY(
      'La temporada comienza en septiembre y finaliza en junio, ambos inclusive; las clases siguen el calendario escolar y la licencia federativa debe tramitarse a la mayor brevedad. Su importe es de 54,95 € para alumnado infantil de hasta 14 años y de 71,95 € para personas adultas desde 15 años, sujeto a cambios por la Federación.',
      'Las clases son dos horas semanales en dos días alternos de lunes a jueves.',
      'Es obligatorio disponer cuanto antes del equipo completo de protecciones: casco, bucal, guantes, calzonas de kickboxing, camiseta, coquilla y espinilleras con cubreempeine; las vendas son opcionales.',
      'El alumnado cuidará su aseo personal antes de asistir a clase, con las uñas de manos y pies cortadas y los pies limpios, y no usará relojes, anillos, pulseras, cadenas, pendientes ni colgantes.',
      'Se respetará al Maestro, a compañeros y compañeras, las instalaciones y los materiales.',
      'Las bajas temporales o definitivas se comunicarán el mes anterior para evitar el pago de la mensualidad en curso. La baja temporal exige una cuota mensual de mantenimiento de 10 € para conservar la plaza y evitar una nueva matrícula; dos semanas de ausencia al inicio sin aviso implican la baja y la pérdida de los derechos de la plaza.',
      'Las mensualidades se abonarán en efectivo en la Escuela del día 1 al 5 del mes corriente. Si esos días coinciden con festivos, se pagarán al día siguiente de su finalización. El pago fuera de plazo conlleva un recargo de 5 € por alumno o alumna.',
      'La falta de asistencia a la actividad, cualquiera que sea su duración o motivo, no exime del pago de la mensualidad correspondiente; al reincorporarse, el alumnado deberá ponerse al corriente de los pagos.',
      'Los exámenes de cinturón se celebran en junio, como primera convocatoria, y en diciembre, como segunda convocatoria. Para examinarse se requiere convocatoria del Maestro, licencia federativa en vigor, mensualidades al corriente y abono de la tasa de examen.',
      'Antes de finalizar la temporada se abrirá un plazo para reservar la plaza de septiembre por 10 €. Quien no la reserve perderá el derecho sobre la plaza, quedará sujeto a las vacantes disponibles y deberá abonar una matrícula de 20 €. Para reservar será necesario haber pagado la mensualidad de junio o, si no se asiste ese mes, la cuota de mantenimiento de la plaza.',
      'La Escuela se reserva el derecho de admisión.'
    )
  ),
  instrucciones,
  CURRENT_TIMESTAMP(6)
FROM plantilla_preinscripcion
WHERE deporte = 'KICKBOXING'
  AND activa = 1
ORDER BY version DESC
LIMIT 1;

UPDATE plantilla_preinscripcion
SET activa = (version = @nueva_version_kickboxing)
WHERE deporte = 'KICKBOXING';
