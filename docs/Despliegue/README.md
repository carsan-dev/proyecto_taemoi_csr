# Despliegue

Estos ficheros son ejemplos sanitizados de propiedades y no contienen secretos reales.

- `applicationAWS.properties.txt`
- `applicationDocker.properties.txt`

Usalos solo como referencia para comparar con `src-api/src/main/resources/application.properties`.

## Importante

- `JWT_SECRET` debe inyectarse por entorno, nunca hardcodeado.
- Las credenciales reales de base de datos, correo y OAuth2 deben vivir fuera del repositorio.
- Si despliegas sobre una base vacia, usa `APP_BOOTSTRAP_ADMIN_*` para crear el primer admin de forma segura.
