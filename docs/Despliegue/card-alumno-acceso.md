# Card del alumno y recuperación del acceso

La recuperación por correo permite establecer una contraseña en cuentas LOCAL y GOOGLE. Conserva proveedor, roles y asociaciones; el acceso posterior con Google conserva la contraseña local. No hay migraciones ni nuevas variables de entorno.

La card mantiene el ancho exterior, centra su contenido en un máximo de 1100 px y muestra foto vertical (220 × 280 px en escritorio; 180 × 230 px en móvil), con encuadre al 30 % superior. La ampliación admite teclado y Escape.

## Publicar y desplegar

Los cambios están en el árbol de trabajo local. Incluye los cambios de esta entrega en un commit y súbelos mediante tu flujo habitual a `develop` o `main`. Espera a que CI termine, incluido `publish-images`.

Producción utiliza imágenes GHCR; `docker-compose.production.yml` no construye el código local. CI publica etiquetas `sha-` con los siete primeros caracteres del commit. `latest` solo se actualiza desde la rama predeterminada; un push a `develop` no actualiza `latest`.

En el servidor, desde la carpeta habitual del proyecto, sustituye `abcdef0` por el SHA corto publicado:

```bash
export TAEMOI_BACKEND_IMAGE=ghcr.io/carsan-dev/taemoi-backend:sha-abcdef0
export TAEMOI_FRONTEND_IMAGE=ghcr.io/carsan-dev/taemoi-frontend:sha-abcdef0
docker compose -f docker-compose.production.yml pull backend frontend
docker compose -f docker-compose.production.yml up -d --no-deps backend frontend
docker compose -f docker-compose.production.yml ps
docker compose -f docker-compose.production.yml logs --tail=80 backend frontend
```

Mantén esas mismas etiquetas en la configuración habitual del servidor si deben persistir para futuros despliegues. Los comandos anteriores conservan base de datos, volúmenes y certificados.

## Comprobación posterior

1. Solicita recuperación desde la web con el correo de cada administrador.
2. Abre el enlace recibido y establece una contraseña nueva.
3. Comprueba inicio de sesión con la contraseña nueva y rechazo de la anterior.
4. Cierra sesión y entra mediante Google. Después comprueba de nuevo el acceso con contraseña.
5. Comprueba foto, deportes, paginación y edición/eliminación de imagen en escritorio y móvil.

No se han cambiado contraseñas directamente en la base de datos. Este cambio elimina el bloqueo de recuperación; no demuestra cuándo ni por qué cambió la contraseña histórica.

## Incidencia adicional encontrada durante validación

Validación local: backend, 212 pruebas (0 fallos, 5 omitidas); frontend, 105 correctas; compilación de producción correcta. E2E final: 20 correctas, 1 omitida y 1 fallo en `la navegación a anclas descuenta la cabecera fija` en escritorio. Esa prueba pasó al ejecutarla de forma focalizada, pero volvió a fallar en la suite completa: CI no se considera verde. Las dos pruebas de la card (escritorio/móvil), incluidos teclado, subida y eliminación PNG, pasaron. Logs completos en `tmp/access-backend.log`, `tmp/access-frontend.log`, `tmp/access-build.log` y `tmp/access-e2e-final.log`. E2E se ejecutó en puertos aislados 14200/18080 para no interferir con el backend local.

Al subir `src/assets/media/default.webp` en el backend Docker E2E, la JVM terminó con SIGSEGV en `libwebp-imageio.so`, función `UpsampleBgraLinePair_SSE2`. El log está en `tmp/access-e2e-backend-tail.log`. No se modificó esa librería; esta incidencia de procesamiento WebP queda pendiente y es independiente del encuadre CSS. La prueba de los controles de subida utiliza PNG.
