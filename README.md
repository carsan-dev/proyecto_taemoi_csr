# TaeMoi: Plataforma Integral para Escuelas de Taekwondo

¡Bienvenido a TaeMoi!

TaeMoi es una solución innovadora diseñada específicamente para la escuela de taekwondo **Moi's Kim Do Taekwondo** en Umbrete (Sevilla), con el objetivo de mejorar la gestión interna y la presencia en línea del negocio. Mi plataforma ofrece una combinación única de un escaparate web personalizado y una completa plataforma de gestión de alumnos, grupos, turnos, eventos y procesos internos.

---

## Título del Proyecto

### Curso Escolar 2023-2024

#### Autor: Carlos Sánchez Román: [(Enlace a mi cuenta de gitHub)](https://github.com/csanrom1702)

#### Tutor: Antonio Gabriel González Casado [(Enlace a su cuenta de gitHub)](https://github.com/prof-antonio-gabriel)

#### Fecha de Inicio: 06-03-2024

#### Fecha de Finalización: 16-06-2024

---

## Qué es TaeMoi?

La aplicación TaeMoi es una plataforma diseñada para administrar y gestionar información relacionada con alumnos en la escuela de taekwondo **"MOI'S KIM DO TAEKWONDO"** en Umbrete. Desde el seguimiento de datos personales hasta el registro de información relevante, ofrece una solución completa y eficiente para simplificar la gestión de alumnos.

Además de su funcionalidad principal de gestión interna, TaeMoi también cuenta con un escaparate web accesible al público estándar. Este escaparate ofrece información relevante, como eventos, horarios de clases, y otras noticias relacionadas con la escuela de taekwondo. Así, cualquier persona interesada en la escuela puede acceder a esta información de forma fácil y rápida.

Por otro lado, la plataforma incluye una parte de inicio de sesión reservada para los usuarios gestores del negocio. Desde esta área segura, los administradores pueden acceder a todas las funcionalidades avanzadas de la aplicación, como la gestión de alumnos, entre otros aspectos fundamentales para la administración eficiente del negocio.

Y a su vez, los alumnos dados de alta podrán iniciar sesión en la aplicación para consultar sus grupos y horarios.

---

## Definir el objetivo de la aplicación

En TaeMoi, nuestro objetivo es proporcionar una solución integral y especializada para la gestión eficiente de la escuela de taekwondo, así como para promover la visibilidad y accesibilidad de la misma en línea. Algunos aspectos clave que definirán el propósito y la misión de mi aplicación web son los siguientes:

- **Funcionalidades Centrales:** TaeMoi ofrecerá una serie de características fundamentales, como el acceso a información pública sobre la escuela, la visualización de eventos y horarios, y la disponibilidad de datos de contacto para potenciales interesados. Esto garantizará que tanto los usuarios anónimos como los clientes potenciales puedan acceder fácilmente a la información relevante sobre esta escuela de taekwondo.
- **Gestión Eficiente:** La aplicación se centrará en proporcionar herramientas robustas para la gestión interna de la escuela, permitiendo a los gestores y administradores realizar operaciones CRUD sobre los alumnos, grupos, turnos y eventos, así como filtrar, ordenar y paginar la información según sea necesario. Esto facilitará la administración de la escuela y mejorará la experiencia para el personal del negocio.
- **Satisfacción del Cliente:** TaeMoi se esforzará por resolver el problema concreto de la falta de soluciones especializadas para la gestión de escuelas de taekwondo en el mercado actual. Al ofrecer una plataforma que aborde específicamente las necesidades de este sector, nuestra aplicación tiene como objetivo cubrir una importante brecha en el mercado y satisfacer las necesidades de Moi's Kim Do Taekwondo.
- **Optimización de Procesos:** Al simplificar y automatizar tareas administrativas, TaeMoi permitirá a la escuela de taekwondo optimizar sus procesos internos, ahorrar tiempo y recursos, y mejorar la eficiencia general de la gestión del negocio. Esto se traducirá en una experiencia más fluida y satisfactoria tanto para el personal como para los clientes.

---

## Estructura del Proyecto

El repositorio del proyecto contiene un API Restful desarrollado en SpringBoot para la lógica de la aplicación y proveer los datos para utilizar de la base de datos, el frontend desarrollado en Angular17 para las vistas de la aplicación y consumir los servicios del API rest y una carpeta docs con toda la documentación relacionada al proyecto. Así se vería la estructura:

- [docs](./docs)
  - BBDD
    - [Esquema-relacional-BBDD-TaeMoi.pdf](./docs/BBDD/Esquema-relacional-BBDD-TaeMoi.pdf)
  - Despliegue
    - [README.md](./docs/Despliegue/README.md)
    - [applicationAWS.properties.txt](./docs/Despliegue/applicationAWS.properties.txt)
    - [applicationDocker.properties.txt](./docs/Despliegue/applicationDocker.properties.txt)
  - Diseño
    - [README.md](./docs/Diseño/README.md)
    - [Prototipo TaeMoi.jpg](./docs/Diseño/Prototipo-TaeMoi.jpg)
  - [2023-2024-IES-ALIXAR-DAW2-Carlos-Sánchez-Román-Taemoi.pdf](./docs/2023-2024-IES-ALIXAR-DAW2-Carlos-Sánchez-Román-Taemoi.pdf)
- [src-api](./src-api/)
- [src-frontend](./src-frontend/)
- [mysql](./mysql/)
- [env.txt](./env.txt)
- [docker-compose.yml](./docker-compose.yml)
- [README.md](./README.md)
