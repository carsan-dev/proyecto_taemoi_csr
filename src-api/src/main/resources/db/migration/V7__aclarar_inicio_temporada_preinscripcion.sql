UPDATE plantilla_preinscripcion
SET instrucciones = 'Conserva tu referencia y acude al club, a partir del inicio de la temporada en septiembre, para formalizar la inscripción y el pago.'
WHERE activa = 1
  AND instrucciones = 'Conserva tu referencia y acude al club para formalizar la inscripción y el pago.';
