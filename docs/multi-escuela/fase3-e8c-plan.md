# E8c · Fotos de eventos: plan

Aprobado por el propietario el 30/10/2026 (decisión 342). Orden de la entrega: fotos de eventos, E9 y después recursos de las familias.

- Boceto aprobado: [fase3-e8c-boceto-fotos.html](fase3-e8c-boceto-fotos.html). La maqueta manda sobre este texto.
- Inventario con citas: [fase3-e8c-inventario.md](fase3-e8c-inventario.md).

## Qué se construye

- **Panel, Editar y Crear evento:** bloque «Fotos del evento» entre Imagen y Documentos. Etiqueta «opcional», contador «N / 40», aviso fijo «Sube solo fotos de personas con autorización web.» y rejilla de miniaturas. La imagen actual del evento (el cartel) no cambia.
- **Web pública, página del evento:** bloque «Fotos del evento» debajo de los detalles, solo si hay fotos: mosaico, «Ver las N fotos» y visor a pantalla completa con «3 de 24», anterior y siguiente.
- **Cartelera:** etiqueta «N fotos» en la tarjeta, solo si tiene fotos.
- Sin fotos, Moiskimdo no ve ningún cambio.

## Ajustes al contrastar con el plan e8b §16

- **Arrastrar no es la única vía.** Cada miniatura lleva acciones para mover antes o después y eliminar, con objetivos de 44 px en móvil, teclado y `LiveAnnouncer`, como el orden del listado de eventos.
- **Rejilla fluida.** La columna del formulario es más estrecha que el boceto porque la vista previa sigue al lado: mínimo 96 px por miniatura en escritorio y tres columnas a 375 px.
- **Barra inferior.** Sigue siendo «Cancelar» y «Guardar cambios». El contador «N cambios sin guardar» cuenta solo los cambios de fotos y no aparece si no hay ninguno.
- **Borrar una foto ya guardada:** al momento y con confirmación, como los documentos. Subir y reordenar van con «Guardar cambios».

## Piezas

### F1 · API (`savvy-careful`: permisos de ficheros, API pública y migración)

- Migración aditiva `V31_16`: tabla de fotos del evento con organización, evento, imagen y orden. Comprueba que `V31_16` siga libre.
- Ficheros en `org-<id>/fotos-eventos/`, una carpeta NUEVA. No pueden ir bajo `org-<id>/eventos/`, que es público por ruta. `RutasArchivos.esImagenPublica` no cambia: la lista blanca ya deja privada esa carpeta.
- Servir por endpoint con la regla de `GET /api/eventos/{id}/imagen`: evento oculto, 404 para anónimos y familias, y caché `private`. Reutiliza su `?w=`, su ETag y su semáforo de redimensionado.
- Subida de una foto por petición, con `ROLE_ADMIN` o `ROLE_MANAGER`, las mismas validaciones (HEIC rechazado) y tope de 40 fotos por evento que aguante dos subidas a la vez.
- Lista, orden y borrado. Al borrar la foto o el evento se borran también los ficheros.
- `numeroFotos` en la lista pública sin consultas N+1. Actualiza los tests dorados de JSON.
- Miniatura de 640 px al subir, en la misma carpeta privada, con fila propia en `imagen` y `miniatura_id` en `evento_foto`: `?w=` hasta 640 la sirve, porque WebP no se reduce al servir. Solo en la galería, la orientación EXIF se aplica antes de reducir.
- Límites: 60 megapíxeles por foto (400, sin decodificar) y dos procesados a la vez (503 si no hay hueco en 30 s). Una foto visible se cachea 1 h, y `GET` y `HEAD` de la imagen no se auditan.
- Pruebas: oculto y visible para cada rol, aislamiento entre escuelas, que `/imagenes/org-<id>/fotos-eventos/...` no sirva nada sin sesión, tope concurrente y limpieza de ficheros.

### F2 · Panel (`savvy-medium`)

Bloque del boceto en `panel-nuevo/eventos/evento-formulario.component`. Reutiliza `prepararFicheroParaSubida`, `SUBIDA_IMAGEN`, el patrón de lote de `enviarDocumentos` y `cdk/drag-drop`. Selección múltiple. Al guardar, sube en paralelo limitado; las fallidas quedan marcadas con «Reintentar» y las demás no se pierden.

### F3 · Web pública (`savvy-medium`)

Bloque, visor y etiqueta en `componentes/vistas/eventos/`, con el estilo actual de esa zona y sin los tokens del panel (decisión 240). Carga diferida, teclado, Esc y gestos en el visor.

## Despliegue

Con migración `V31_16`: `desplegar_migrar`. El orden es F1, y después F2 y F3. Sin interruptor (decisión pendiente 341), cada pieza se ve al desplegar: no pasa nada con F1 sola, y F2 y F3 no muestran nada a las familias hasta que se suban fotos. Antes del merge, prueba en la app real.

## Fuera de esta pieza

- La q16 sigue abierta para el cartel y para los documentos de eventos, públicos por ruta.
- La web pública solo resuelve la organización 1 (decisión 10): la galería de otra escuela espera a la fase 4.
- Los textos propios de Moiskimdo en la web de eventos no cambian.
