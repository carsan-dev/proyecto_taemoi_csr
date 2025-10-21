# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TaeMoi is a comprehensive platform for managing a Taekwondo school ("Moi's Kim Do Taekwondo" in Umbrete, Sevilla). It combines a public-facing web storefront with an internal management system for students (alumnos), groups (grupos), schedules (turnos), events (eventos), products (productos), and exam calls (convocatorias).

The application supports three user roles:
- **ADMIN**: Full system access
- **MANAGER**: Management operations for students, groups, schedules, events, and products
- **USER**: Students who can view their own groups and schedules

## Technology Stack

- **Backend**: Spring Boot 3.4.5 with Java 21 LTS
- **Frontend**: Angular 20 (standalone components) with Node.js 22
- **Database**: MySQL 8.0
- **Authentication**: JWT-based authentication with Spring Security (JJWT 0.12.6)
- **Caching**: Caffeine (in-memory cache)
- **Deployment**: Docker Compose with three services (database, backend, frontend)

## Development Commands

### Backend (Spring Boot API)

```bash
# Navigate to backend directory
cd src-api

# Build the project
mvn clean install

# Run the application (requires MySQL running and .env configured)
mvn spring-boot:run

# Run tests
mvn test

# Run a specific test class
mvn test -Dtest=AlumnoServiceImplTest

# Run a specific test method
mvn test -Dtest=AlumnoServiceImplTest#testMethodName
```

The backend runs on port 8080 by default.

### Frontend (Angular)

```bash
# Navigate to frontend directory
cd src-frontend

# Install dependencies
npm install

# Start development server (local environment)
npm start

# Build for local environment
npm run build:local

# Build for production
npm run build:production

# Run tests
npm test
```

The frontend development server runs on port 4200 by default.

### Docker Deployment

```bash
# Start all services (from project root)
docker-compose up -d

# Stop all services
docker-compose down

# Rebuild and start
docker-compose up --build

# View logs
docker-compose logs -f [service-name]
```

Services:
- MySQL: Port 3307 (external) -> 3306 (internal)
- Spring Boot API: Port 8080
- Angular/Nginx: Port 80

## Architecture

### Backend Structure

The Spring Boot API follows a layered architecture:

- **Controladores** (`controladores/`): REST controllers exposing API endpoints under `/api/*`
  - Key controllers: `AlumnoController`, `GrupoController`, `TurnoController`, `EventoController`, `ProductoController`, `ConvocatoriaController`, `AuthenticationController`

- **Servicios** (`servicios/` and `servicios/impl/`): Business logic layer
  - Interface-implementation pattern (e.g., `AlumnoService` implemented by `AlumnoServiceImpl`)
  - Key services: `AuthenticationService`, `JwtService`, `EmailService`
  - **Caching**: Services use `@Cacheable` and `@CacheEvict` annotations for performance

- **Repositorios** (`repositorios/`): JPA repositories for data access
  - Extend `JpaRepository` for CRUD operations
  - **Performance**: Custom queries with `JOIN FETCH` to avoid N+1 problems
  - Methods like `findByIdWithAlumnos()`, `findAllWithAlumnos()`, `findByIdWithRelaciones()` load relationships eagerly

- **Entidades** (`entidades/`): JPA entities representing database tables
  - Main entities: `Alumno`, `Grupo`, `Turno`, `Evento`, `Usuario`, `Producto`, `Convocatoria`, `Documento`
  - Uses Lombok annotations for boilerplate reduction
  - **Indexes**: Entities have database indexes on frequently queried columns (nombre, email, nif, etc.)

- **DTOs** (`dtos/`, `dtos/request/`, `dtos/response/`): Data transfer objects
  - Request DTOs: `LoginRequest`, `RegistroRequest`, `EmailRequest`
  - Response DTOs: Custom DTOs for different views (e.g., `AlumnoConGruposDTO`, `GrupoConAlumnosDTO`)

- **Configuración** (`configuracion/`): Configuration classes
  - `SecurityConfiguration`: Spring Security setup with role-based access control and security headers
  - `JwtAuthenticationFilter`: JWT token validation filter
  - `WebConfig`: CORS configuration (uses environment variable)
  - `CacheConfig`: Caffeine cache configuration with 10-minute expiration
  - `InicializadorDatos`: Data initialization on startup

- **Errores** (`errores/`): Custom exceptions organized by domain
  - `GlobalExceptionHandler`: Centralized exception handling

### Security Implementation

- JWT-based authentication with tokens containing user roles
- **JWT Secret**: Stored in environment variable `JWT_SECRET` (not hardcoded)
- `JwtAuthenticationFilter` intercepts requests and validates tokens
- **Secure Cookies**: HTTP-Only, Secure, SameSite=Strict for JWT storage
- **Security Headers**: CSP, XSS Protection, HSTS, Frame Options configured in `SecurityConfiguration`
- Role-based authorization configured in `SecurityConfiguration.java`:
  - Public endpoints: `/api/auth/**`, `/api/eventos` (GET), `/api/turnos/dto` (GET), `/api/mail/**`
  - Admin-only: `/api/admin/**`
  - Manager+Admin: Most CRUD operations on alumnos, grupos, turnos, eventos, productos
  - User access: Can view their own grupos and turnos
- Session management: Stateless (SessionCreationPolicy.STATELESS)
- **File Upload Security**: Validates file types (only images: JPEG, PNG, GIF, WebP), size limits (50MB)

### Performance Optimizations

- **Caching Strategy**:
  - In-memory caching with Caffeine
  - Cache names: `alumnos`, `grupos`, `turnos`, `eventos`, `productos`, `usuarios`, `grados`, `categorias`
  - 10-minute write expiration, 5-minute access expiration
  - Max 1000 entries per cache
  - `@Cacheable` on read operations, `@CacheEvict` on updates/deletes

- **Database Optimizations**:
  - Indexes on: `Alumno.nombre`, `Alumno.nif`, `Alumno.email`, `Alumno.numeroExpediente`, `Alumno.aptoParaExamen`, `Usuario.email`, `Grupo.nombre`
  - HikariCP connection pool configured (max 10, min idle 5)
  - Custom JOIN FETCH queries to avoid N+1 query problems

- **N+1 Query Prevention**:
  - Use repository methods with `WithAlumnos`, `WithTurnos`, `WithRelaciones` suffix
  - These methods use `LEFT JOIN FETCH` to eagerly load relationships
  - Example: `grupoRepository.findAllWithAlumnos()` instead of `findAll()`

### Frontend Structure

Angular 17 application using standalone components:

- **Componentes** (`src/app/componentes/`):
  - `vistas/`: Public and authenticated views
    - Public: `escaparate-principal`, `eventos`, `contacto`, `eltaekwondo`, `horarios`, `kickboxing`, `pilates`
    - Admin: `vista-principal-admin` (admin dashboard)
    - User: `vista-principal-user` (student dashboard)
  - `endpoints/`: CRUD components for entities (alumnos, grupos, turnos, eventos, productos)
  - `generales/`: Shared components

- **Servicios** (`src/app/servicios/`):
  - `authentication/`: Authentication service for login/logout and JWT management
  - `endpoints/`: Service for API communication
  - `contacto/`: Mail service for contact form
  - `generales/`: Utility services (e.g., sidebar service)

- **Guards** (`src/app/guards/`):
  - `roleGuard`: Protects routes requiring authentication/authorization

- **Routing** (`app.routes.ts`):
  - Public routes: `/inicio`, `/eventos`, `/contacto`, `/horarios`, etc.
  - Protected admin routes: `/adminpage`, `/alumnosListar`, `/gruposListar`, `/turnosListar`, `/eventosListar`, `/productosListar` (all use `roleGuard`)
  - User routes: `/userpage`, `/userpage/turnos`

### Environment Configuration

The application uses environment-specific configurations:

- **Backend**: Spring profiles (default, docker)
  - Environment variables required (via `.env` file):
    - Database: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
    - Mail: `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`
    - Security: `JWT_SECRET`, `CORS_ALLOWED_ORIGIN`
  - Docker profile uses `SPRING_DATASOURCE_URL_DOCKER`

- **Frontend**: Angular environment files
  - `environment.local.ts`: Local development (use `npm run build:local`)
  - `environment.prod.ts`: Production (use `npm run build:production`)

### Key Domain Concepts

- **Alumno**: Students enrolled in the school with personal data, grade (grado), category (categoria), groups, and schedules
- **Grupo**: Groups/classes that students belong to (by sport: Taekwondo, Kickboxing, Pilates)
- **Turno**: Time slots/schedules associated with groups and students
- **Evento**: Events displayed on the public website
- **Convocatoria**: Exam calls for students (separated by sport/deporte)
- **AlumnoConvocatoria**: Join entity linking students to exam calls with associated products
- **Producto**: Products that can be assigned to students (mensualidad, derechos de examen, licencias)
- **ProductoAlumno**: Product assignments to students with payment tracking
- **Usuario**: User accounts with roles (ADMIN, MANAGER, USER) linked to Alumno entities
- **Grado**: Belt ranks in martial arts (TipoGrado enum: BLANCO, AMARILLO, NARANJA, VERDE, AZUL, ROJO, NEGRO_1_DAN, etc.)
- **Categoria**: Categories for competitive students based on age
- **Deporte**: Sports enumeration (TAEKWONDO, KICKBOXING, PILATES, DEFENSA_PERSONAL_FEMENINA)
- **Documento**: Documents attached to students (stored in file system)

### Business Logic Specifics

- **Automatic Grade Eligibility**: `esAptoParaExamen()` calculates if a student is eligible for exam based on current grade and time since last exam
- **Category Assignment**: Students marked as competitors are automatically assigned categories based on age
- **Mensualidad (Monthly Fee) Management**: `MensualidadUtils` handles monthly fee naming and assignment
- **Grade Progression Maps**: Different progression paths for minors vs adults in Taekwondo, separate path for Kickboxing
- **Tarifa (Fee) System**: Different fee types (INFANTIL, ADULTO, ADULTO_GRUPO, FAMILIAR, PILATES, etc.) with automatic cuantía assignment

### Database

- MySQL 8.0 database
- JPA/Hibernate with `ddl-auto=update` (auto-updates schema)
- Schema diagram available at: `docs/BBDD/Esquema-relacional-BBDD-TaeMoi.pdf`
- **Connection Pooling**: HikariCP configured with:
  - Maximum pool size: 10
  - Minimum idle: 5
  - Connection timeout: 30 seconds
  - Idle timeout: 10 minutes
  - Max lifetime: 30 minutes

### File Management

- **Images**: Stored in platform-specific directories
  - Linux: `/var/www/app/imagenes/`
  - Windows: `%USERPROFILE%/static_resources/imagenes/`
  - Max file size: 50MB
  - Allowed types: JPEG, PNG, GIF, WebP
  - Images served at `/imagenes/**` endpoint with 1-hour cache

- **Documents**: Similar structure for student documents
  - Linux: `/var/www/app/documentos/`
  - Windows: `%USERPROFILE%/static_resources/documentos/`
  - Served at `/documentos/**` endpoint

### Testing

- Backend tests in `src-api/src/test/java/com/taemoi/project/`
  - Controller tests: Test REST endpoints with MockMvc
  - Service tests: Test business logic with mocked dependencies
  - Entity tests: Test entity validation and behavior

- Frontend uses Jasmine/Karma for testing

## Important Implementation Notes

- **Caching**: When modifying entities, ensure proper cache eviction using `@CacheEvict`
- **N+1 Queries**: Always use `findByIdWithRelaciones()` or similar methods when loading entities with relationships
- **Security**: Never bypass security for convenience. All admin/manager endpoints require proper role checks
- **File Validation**: `ImagenServiceImpl` has comprehensive validation - don't bypass it
- **JWT Secret**: Must be set in `.env` file, never commit the actual secret
- **CORS**: Configured via environment variable `CORS_ALLOWED_ORIGIN` for flexibility across environments
- **Database Indexes**: Present on frequently queried fields - don't remove them
- **Lombok**: Entities use Lombok annotations - ensure annotation processor is enabled in IDE
- **Email**: Gmail SMTP requires app-specific password in environment variables
- **Login Attempts**: `LoginAttemptService` tracks failed login attempts (5 max, 15-minute lockout)
- **Lazy Initialization**: Enabled in `application.properties` - first requests may be slower
- **Transaction Management**: Use `@Transactional` for operations that modify multiple entities
- **Fecha Utilities**: `FechaUtils` provides age calculation and date manipulation utilities
- **Mensualidad Naming**: Use `MensualidadUtils.formatearNombreMensualidad()` for consistent naming

## Security Considerations

- HTTPS should be enabled in production (currently configured for development with `setSecure(false)`)
- Change `spring.jpa.show-sql` to `false` in production
- Consider moving from `ddl-auto=update` to `validate` with migration tools (Flyway/Liquibase) for production
- Review and update JWT token expiration time (currently 10 hours) based on security requirements
- Database credentials should be rotated regularly
- Consider implementing refresh token mechanism for better security
