# Checklist de despliegue multi-escuela

## Antes
- [ ] Copia de seguridad de la base de datos y de las carpetas de documentos e imágenes.
- [ ] Contar usuarios sin rol (no podrán iniciar sesión tras V20); decidir su rol si hay alguno:
      `SELECT COUNT(*) FROM usuario u WHERE NOT EXISTS (SELECT 1 FROM usuario_rol r WHERE r.usuario_id = u.id);`
- [ ] En el `.env` del servidor, `APP_IMAGENES_PATH_DOCKER` debe ser `/var/www/app/imagenes/` (no `…/alumnos/`).
- [ ] Ventana de mantenimiento con la aplicación parada durante las migraciones V18–V20.
- [ ] Cargar fuera del repositorio los datos legales de la organización 1 (`nombre_legal`, `cif`, `firmante_*`).
- [ ] V23 (catálogo) nunca se despliega sin el cambio de código de los enums a las tablas (fase 2, tarea 5).
- [ ] Antes de V22, ejecutar `docs/multi-escuela/precheck-v22.sql` (solo lectura, solo recuentos) y guardar el resultado.

## Después
- [ ] Entrar como administrador, gestor y familia; cada uno ve lo mismo que antes.
- [ ] Dar de alta un alumno; su expediente es el siguiente al más alto.
- [ ] Se ven las fotos de eventos en la web pública y las fotos y documentos de alumnos en el panel.

## Vuelta atrás
Desde V20 los roles viven en `membresia` y `usuario_rol` deja de actualizarse: volver al código
anterior exige restaurar la copia de seguridad.
