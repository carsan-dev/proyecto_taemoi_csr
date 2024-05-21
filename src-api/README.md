### Bienvenido a la aplicación TaeMoi.

¡Gracias por tu interés en mi aplicación de Gestión de Alumnos! Este README te proporcionará una visión general de la aplicación, sus características principales y cómo comenzar a usarla.

---

### Descripción

La aplicación TaeMoi es una plataforma diseñada para administrar y gestionar información relacionada con alumnos en la escuela de taekwondo "MOISKIMDO TAEKWONDO" en Umbrete. Desde el seguimiento de datos personales hasta el registro de información relevante, mi aplicación ofrece una solución completa y eficiente para simplificar la gestión de alumnos.

---

### Características Principales

- **Registro de Alumnos**: Permite registrar nuevos alumnos con sus datos personales, de contacto, tarifas, grados y categorías relacionadas.
- **Consulta y Edición de Alumnos**: Facilita la búsqueda y visualización de alumnos existentes, así como la edición de sus detalles.
- **Eliminación de Alumnos**: Permite eliminar registros de alumnos cuando sea necesario.
- **Seguridad Basada en Roles**: Ofrece diferentes niveles de acceso y permisos según el rol del usuario (administrador o usuario estándar).
- **Paginación, Ordenación y Filtrado**: Permite la paginación, ordenación y filtrado de la lista de alumnos para una navegación más fácil y una mejor organización.

---

### Tecnologías Utilizadas

- **Spring Boot**: Un marco de trabajo de Java que simplifica el desarrollo de aplicaciones web.
- **Spring Security**: Proporciona autenticación y autorización para la aplicación.
- **Spring Data JPA**: Facilita el acceso y la manipulación de datos en la base de datos.
- **Hibernate**: Un framework de mapeo objeto-relacional para Java.
- **MySQL**: Un sistema de gestión de bases de datos relacional.
- **JWT (JSON Web Tokens)**: Un estándar abierto (RFC 7519) que define un formato compacto y autocontenido para la transmisión segura de información entre dos partes.

---

### Configuración del Proyecto

1. **Clonar el Repositorio**:
git clone https://github.com/Crolyx16/TaeMoi.git

2. **Configurar la Base de Datos**:
- Cree una base de datos MySQL llamada `taemoidb`.
- Actualice el archivo `application.properties` con la configuración de su base de datos:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/taemoidb
spring.datasource.username=tu_usuario
spring.datasource.password=tu_contraseña
```

---

### Ejecutar la Aplicación:

```bash
# Comando para ejecutar la aplicación Spring Boot en Linux
mvn spring-boot:run

# Comando para ejecutar la aplicación Spring Boot en Windows
./mvnw spring-boot:run
```

---

### Acceder a la Aplicación:
Usar algún gestor de peticiones REST como postman (Colección requerida adjunta al repositorio).
- Enlace a la página de descargas de Postman: https://www.postman.com/downloads/
- Enlace a la documentación que he creado de Postman para esta app: https://documenter.getpostman.com/view/32188944/2sA2rAxgd9

---

### Contribuir
¡Agradezco cualquier contribución que desees hacer para mejorar mi aplicación! Si tienes alguna idea de mejora, problema o función que te gustaría añadir, no dudes en abrir un problema o enviar una solicitud.

---

### Soporte
Si tienes alguna pregunta o necesitas ayuda con la aplicación, no dudes en ponerte en contacto conmigo en csanrom1702@g.educaand.es.

---

¡Espero que disfrutes usando TaeMoi y que te sea útil en tus actividades diarias! Si tienes alguna sugerencia o comentario, ¡me encantaría escucharlo!

Equipo de Desarrollo de TaeMoi