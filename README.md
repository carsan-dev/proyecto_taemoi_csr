# TaeMoi

Plataforma web integral para la escuela **Moi's Kim Do Taekwondo** de Umbrete (Sevilla). El proyecto combina un escaparate público orientado a captación con una zona privada de gestión para administradores, personal de gestión y alumnos.

## Qué ofrece actualmente

- Escaparate público con información de la escuela, disciplinas, horarios, eventos, tarifas, contacto y páginas legales.
- Gestión interna de alumnos, grupos, turnos, productos, convocatorias y tesorería.
- Portal de usuario para que cada alumno consulte su información y sus turnos.
- Autenticación con JWT, control de acceso por roles y soporte OAuth2 con Google.
- Gestión de documentos e imágenes con almacenamiento persistente.
- Generación de PDF y flujos de comunicación por correo.
- Auditoría del sistema, configuración interna y tareas programadas.
- Soporte multideporte: taekwondo, kickboxing, pilates y defensa personal femenina.

## Roles del sistema

- `ADMIN`: acceso total, incluyendo auditoría y configuración del sistema.
- `MANAGER`: operaciones de gestión sobre alumnos, grupos, turnos, eventos, productos y convocatorias.
- `USER`: acceso a su área privada para consultar información personal y horarios.

## Funcionalidades principales

### Área pública

- Página principal de escaparate.
- Secciones específicas de `taekwondo`, `kickboxing`, `pilates` y `defensa-personal-femenina`.
- Consulta de `horarios`, `eventos`, `tarifas` y `contacto`.
- Detalle individual de eventos.
- SEO y rutas legales (`política de privacidad`, `cookies`, `aviso legal`).

### Área privada de gestión

- Alta, edición, listado y eliminación de alumnos.
- Gestión de grupos y asignación de alumnos.
- Gestión de turnos y relación con alumnos.
- Gestión de eventos y productos.
- Gestión de convocatorias.
- Tesorería y seguimiento de cobros.
- Auditoría del sistema.
- Configuración interna del sistema.
- Gestión de documentos e imágenes asociadas a alumnos.

### Automatizaciones y reglas de negocio

- Seguridad basada en roles con Spring Security.
- JWT para sesión autenticada y soporte de login con Google.
- Recordatorios y jobs programados.
- Caché en memoria con Caffeine para mejorar el rendimiento.
- Generación de PDF para procesos internos.
- Reglas de progresión de grado, categorías y lógica de negocio académica/deportiva.

## Arquitectura del repositorio

```text
.
|- src-api/           API REST en Spring Boot
|- src-frontend/      Aplicación Angular
|- mysql/             Docker y scripts SQL de base de datos
|- nginx/             Configuración de Nginx para despliegue
|- static_resources/  Recursos persistentes locales (imágenes/documentos)
|- docs/              Documentación funcional, técnica y de despliegue
|- scripts/           Scripts auxiliares de despliegue y diagnóstico
`- docker-compose.yml Orquestación local/servidor
```

## Backend

Ruta principal: `src-api`

Tecnologías principales:

- Java 21
- Spring Boot 3.5.x
- Spring Security
- Spring Data JPA
- MySQL 8
- JWT (`jjwt`)
- Caffeine
- Java Mail
- OAuth2 Client
- OpenHTML to PDF / PDFBox / Batik

Estructura destacada:

- `src-api/src/main/java/com/taemoi/project/controllers`: controladores REST.
- `src-api/src/main/java/com/taemoi/project/services`: lógica de negocio.
- `src-api/src/main/java/com/taemoi/project/repositories`: acceso a datos.
- `src-api/src/main/java/com/taemoi/project/entities`: modelo de dominio.
- `src-api/src/main/java/com/taemoi/project/dtos`: contratos de entrada/salida.
- `src-api/src/main/java/com/taemoi/project/config`: seguridad y configuración.
- `src-api/src/main/resources`: perfiles, logging y recursos estáticos.

Dominios principales del backend:

- `Alumno`, `Grupo`, `Turno`, `Usuario`
- `Producto`, `ProductoAlumno`, `Tesorería`
- `Convocatoria`, `Grado`, `Categoría`, `AlumnoDeporte`
- `Evento`, `Documento`, `Auditoría`

## Frontend

Ruta principal: `src-frontend`

Tecnologías principales:

- Angular 20
- TypeScript 5.9
- Angular Material
- Bootstrap 5
- SweetAlert2
- Leaflet

Estructura destacada:

- `src-frontend/src/app/componentes/vistas`: vistas públicas y paneles principales.
- `src-frontend/src/app/componentes/endpoints`: pantallas CRUD y módulos internos.
- `src-frontend/src/app/servicios`: acceso a API y servicios de aplicación.
- `src-frontend/src/app/guards`: protección de rutas por autenticación/rol.
- `src-frontend/src/app/core`, `constants`, `utilities`, `utils`: utilidades y configuración transversal.

Rutas funcionales relevantes:

- Públicas: `/`, `/taekwondo`, `/kickboxing`, `/pilates`, `/defensa-personal-femenina`, `/horarios`, `/eventos`, `/contacto`, `/tarifas`, legales.
- Privadas: `/adminpage`, `/userpage`, `/alumnosListar`, `/gruposListar`, `/turnosCrear`, `/productosListar`, `/convocatoriasListar`, `/tesoreriaCobros`, `/auditoriaSistema`, `/configuracion-sistema`.

## Base de datos y almacenamiento

- Base de datos principal: MySQL 8.
- Scripts SQL y utilidades en `mysql/`.
- Imágenes y documentos persistentes en `static_resources/` en local o en volumen Docker en despliegue.

## Requisitos

- Java 21
- Node.js 22
- Maven 3.9+ o wrapper incluido
- MySQL 8
- Docker y Docker Compose (opcional, recomendado para despliegue integrado)

## Variables de entorno

El proyecto usa un archivo `.env` para centralizar configuración sensible y dependiente del entorno.

Variables importantes:

- Base de datos: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- Docker DB: `SPRING_DATASOURCE_URL_DOCKER`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`
- Seguridad: `JWT_SECRET`
- CORS / frontend: `CORS_ALLOWED_ORIGIN`, `APP_FRONTEND_BASE_URL`
- Correo: `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`
- OAuth2 Google: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`
- Almacenamiento: `APP_IMAGENES_PATH`, `APP_DOCUMENTOS_PATH`, `APP_IMAGENES_PATH_DOCKER`, `APP_DOCUMENTOS_PATH_DOCKER`

## Puesta en marcha local

### 1. Backend

```bash
cd src-api
mvn clean install
mvn spring-boot:run
```

Alternativa con wrapper en Windows:

```bash
cd src-api
./mvnw spring-boot:run
```

### 2. Frontend

```bash
cd src-frontend
npm install
npm start
```

### 3. Accesos por defecto de desarrollo

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:4200`

## Docker Compose

Desde la raíz del repositorio:

```bash
docker-compose up --build
```

Servicios levantados:

- MySQL: puerto `3307`
- Backend Spring Boot: puerto `8080`
- Frontend con Nginx: puerto `80`

## Comandos útiles

### Backend

```bash
cd src-api
mvn test
mvn clean install
```

### Frontend

```bash
cd src-frontend
npm test
npm run build:local
npm run build:production
```

## Documentación adicional

- Documentación general del proyecto: `docs/`
- Despliegue: `docs/Despliegue/README.md`
- Diseño: `docs/Diseño/README.md`
- Esquema de base de datos: `docs/BBDD/Esquema-relacional-BBDD-TaeMoi.pdf`
- Colección Postman del backend: `src-api/API TAEMOI.postman_collection.json`
