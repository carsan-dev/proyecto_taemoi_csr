# TaeMoi API

Backend REST de TaeMoi, la plataforma de gestión y escaparate web para la escuela **Moi's Kim Do Taekwondo**. Este módulo concentra la lógica de negocio, la seguridad, el acceso a datos, la gestión documental y varios procesos internos del sistema.

## Qué cubre esta API

- Autenticación con JWT y control de acceso por roles.
- Soporte de login social con Google mediante OAuth2.
- Gestión de alumnos, grupos, turnos, eventos, productos y convocatorias.
- Tesorería y seguimiento de cobros.
- Gestión de documentos e imágenes de alumnos.
- Auditoría del sistema.
- Generación de PDF para procesos internos.
- Envío de correos y recuperación de contraseña.
- Reglas de negocio de grados, categorías, tarifas y multideporte.

## Tecnologías utilizadas

- Java 21
- Spring Boot 3.5.x
- Spring Web
- Spring Security
- Spring Data JPA
- MySQL 8
- JWT (`jjwt`)
- OAuth2 Client
- Caffeine
- Java Mail
- OpenHTML to PDF / PDFBox / Batik
- Lombok

## Estructura principal

```text
src-api/
|- src/main/java/com/taemoi/project/
|  |- config/         Configuración general, seguridad, caché y web
|  |- controllers/    Endpoints REST
|  |- dtos/           Objetos de entrada y salida
|  |- entities/       Modelo de dominio y entidades JPA
|  |- exceptions/     Manejo centralizado de errores
|  |- jobs/           Tareas programadas
|  |- repositories/   Acceso a datos
|  |- services/       Lógica de negocio
|  `- utils/          Utilidades auxiliares
|- src/main/resources/
|  |- application.properties
|  |- application-local.properties
|  |- application-docker.properties
|  `- application-production.properties
`- pom.xml
```

## Endpoints y módulos principales

Controladores destacados en `src/main/java/com/taemoi/project/controllers`:

- `AuthenticationController`: login, registro, sesión y flujos de autenticación.
- `AlumnoController`: operaciones sobre alumnos.
- `GrupoController`: gestión de grupos.
- `TurnoController`: gestión de turnos y horarios.
- `EventoController`: publicación y mantenimiento de eventos.
- `ProductoController` y `ProductoAlumnoController`: catálogo y asignación de productos.
- `ConvocatoriaController`: convocatorias y procesos relacionados.
- `TesoreriaController`: cobros y visión económica.
- `AuditoriaController`: consulta de actividad del sistema.
- `PDFController`: generación de documentos PDF.
- `EmailController`: comunicación por correo.
- `AdminController`, `MigracionController`, `GradoController`: operaciones administrativas y auxiliares.

## Dominios principales

Entidades destacadas en `src/main/java/com/taemoi/project/entities`:

- `Alumno`
- `Grupo`
- `Turno`
- `Usuario`
- `Producto` y `ProductoAlumno`
- `Convocatoria` y `AlumnoConvocatoria`
- `Grado` y `Categoría`
- `Evento`
- `Documento` e `Imagen`
- `AlumnoDeporte`
- `AuditoriaEvento`

## Seguridad

- Autenticación basada en JWT.
- Autorización por roles `ADMIN`, `MANAGER` y `USER`.
- Integración con OAuth2 para Google.
- Configuración de seguridad centralizada en `src/main/java/com/taemoi/project/config`.
- Recuperación de contraseña y validaciones de acceso.

## Rendimiento y procesos internos

- Caché en memoria con Caffeine.
- Tareas programadas para mantenimiento y recordatorios.
- Gestión de ficheros con rutas configurables por entorno.
- Configuración por perfiles: local, docker y producción.

## Requisitos

- Java 21
- Maven 3.9+ o wrapper incluido
- MySQL 8
- Archivo `.env` con las variables necesarias

## Variables de entorno relevantes

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATASOURCE_URL_DOCKER`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGIN`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `GOOGLE_REDIRECT_URI`
- `APP_IMAGENES_PATH`
- `APP_DOCUMENTOS_PATH`
- `APP_IMAGENES_PATH_DOCKER`
- `APP_DOCUMENTOS_PATH_DOCKER`

## Ejecución en local

```bash
cd src-api
mvn clean install
mvn spring-boot:run
```

En Windows también puedes usar el wrapper incluido:

```bash
cd src-api
./mvnw spring-boot:run
```

La API arranca por defecto en `http://localhost:8080`.

## Tests

```bash
cd src-api
mvn test
```

Ejecutar una clase concreta:

```bash
mvn test -Dtest=AlumnoServiceImplTest
```

Ejecutar un método concreto:

```bash
mvn test -Dtest=AlumnoServiceImplTest#testMethodName
```

## Colección Postman

Puedes usar la colección incluida en `src-api/API TAEMOI.postman_collection.json` para explorar y probar los endpoints de la API.

## Relación con el resto del proyecto

- Frontend Angular: `../src-frontend`
- Orquestación Docker: `../docker-compose.yml`
- Scripts SQL y base de datos: `../mysql`
- Documentación adicional: `../docs`
