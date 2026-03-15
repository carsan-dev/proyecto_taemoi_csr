# Materiales_Examen

Estructura base para materiales de examen servidos por API protegida.

## Convenciones
- Directorios en minusculas ASCII y sin espacios.
- Bloques con formato `bNN_slug`.
- En bloques de progresion, usar `origen_a_destino` (ej. `b03_naranja_a_verde`).
- PDF del temario en `temario/temario.pdf`.
- Documentacion adicional en `documentacion/`.
- Orden recomendado para documentacion: prefijo numerico (`01_`, `02-`, `03 `).
- Videos en `videos/` con prefijo de orden: `01_`, `02_`, `03_`.
- `index.json` opcional en la carpeta del bloque para titulo y orden.

## Ejemplo
- `taekwondo/b01_inicio_a_amarillo/temario/temario.pdf`
- `taekwondo/b01_inicio_a_amarillo/documentacion/01_reglamento.pdf`
- `taekwondo/b01_inicio_a_amarillo/documentacion/02_glosario.docx`
- `taekwondo/b01_inicio_a_amarillo/videos/01_presentacion.mp4`
- `kickboxing/b01_inicio_a_amarillo/temario/temario.pdf`
