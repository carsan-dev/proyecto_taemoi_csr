# Plan de Migración: Eliminar Campos Legacy de Alumno

## Resumen

Migrar los campos específicos de deporte desde la entidad `Alumno` a `AlumnoDeporte` para completar la arquitectura multi-deporte.

## Estado Actual

### Campos en Alumno (Legacy - a eliminar)
```java
// Tarifa (debería ser por deporte)
private TipoTarifa tipoTarifa;
private Double cuantiaTarifa;

// Licencia (debería ser por deporte)
private Boolean tieneLicencia;
private Integer numeroLicencia;
private Date fechaLicencia;

// Competidor (debería ser por deporte)
private Boolean competidor;
private Double peso;
private Date fechaPeso;
private String categoria;

// Grado (debería ser por deporte)
private Grado grado;
private Date fechaGrado;
private Boolean aptoParaExamen;

// Fechas (debería ser por deporte)
private Date fechaAlta;
private Date fechaAltaInicial;

// Deporte principal (legacy)
private Deporte deporte;
```

### Campos que deben permanecer en Alumno
```java
// Información personal
private String nombre;
private String apellidos;
private String direccion;
private Date fechaNacimiento;
private String nif;
private Integer numeroExpediente;

// Contacto
private String email;
private String telefono;

// General
private Boolean autorizacionWeb;
private Boolean tieneDiscapacidad;
private Imagen fotoAlumno;

// Descuento familiar (aplica a todos los deportes)
private RolFamiliar rolFamiliar;
private String grupoFamiliar;

// Estado global (cuando deja completamente la escuela)
private Boolean activo;
private Date fechaBaja; // Baja general de la escuela
```

### Campos actuales en AlumnoDeporte
```java
private Alumno alumno;
private Deporte deporte;
private Grado grado;
private Date fechaGrado;
private Boolean aptoParaExamen;
private Date fechaAlta;
private Date fechaBaja;
private Boolean activo;
// Campos de competidor
private Boolean competidor;
private Double peso;
private Date fechaPeso;
private Categoria categoria;
private Date fechaAltaCompeticion;
```

---

## Fases de Migración

### FASE 1: Preparación de AlumnoDeporte

**Objetivo:** Añadir campos faltantes a AlumnoDeporte

#### 1.1 Añadir campos de Tarifa a AlumnoDeporte
```java
// En AlumnoDeporte.java
private TipoTarifa tipoTarifa;
private Double cuantiaTarifa;
```

#### 1.2 Añadir campos de Licencia a AlumnoDeporte
```java
// En AlumnoDeporte.java
private Boolean tieneLicencia;
private Integer numeroLicencia;
private Date fechaLicencia;
```

#### 1.3 Añadir fechaAltaInicial a AlumnoDeporte
```java
// En AlumnoDeporte.java
private Date fechaAltaInicial; // Primera vez que se apuntó a este deporte
```

#### 1.4 Crear/Actualizar AlumnoDeporteDTO
- Incluir todos los nuevos campos
- Actualizar métodos de conversión

#### 1.5 Actualizar repositorio y servicios
- Añadir queries necesarias
- Actualizar métodos de servicio

---

### FASE 2: Migración de Datos

**Objetivo:** Migrar datos existentes de Alumno a AlumnoDeporte

#### 2.1 Script de migración SQL
```sql
-- Migrar tarifa del alumno al AlumnoDeporte principal
UPDATE alumno_deporte ad
INNER JOIN alumno a ON ad.alumno_id = a.id
SET
    ad.tipo_tarifa = a.tipo_tarifa,
    ad.cuantia_tarifa = a.cuantia_tarifa,
    ad.tiene_licencia = a.tiene_licencia,
    ad.numero_licencia = a.numero_licencia,
    ad.fecha_licencia = a.fecha_licencia,
    ad.fecha_alta_inicial = a.fecha_alta_inicial
WHERE ad.deporte = a.deporte;

-- Para alumnos con un solo deporte, copiar a ese deporte
-- Para alumnos con múltiples deportes, copiar al deporte principal
```

#### 2.2 Validación de datos migrados
- Script de verificación
- Comparar totales antes/después
- Verificar integridad referencial

---

### FASE 3: Actualización del Backend

**Objetivo:** Actualizar servicios para usar AlumnoDeporte

#### 3.1 Actualizar AlumnoService
- Eliminar métodos que usan campos legacy
- Redirigir a AlumnoDeporteService

#### 3.2 Actualizar AlumnoDeporteService
- Añadir métodos para gestionar tarifa por deporte
- Añadir métodos para gestionar licencia por deporte
- Actualizar cálculos de antigüedad por deporte

#### 3.3 Actualizar controladores
- AlumnoController: eliminar endpoints de campos legacy
- AlumnoDeporteController: añadir nuevos endpoints

#### 3.4 Actualizar validaciones
- `datosAlumnoValidos()`: eliminar validación de tipoTarifa
- Crear `datosAlumnoDeporteValidos()` si no existe

#### 3.5 Actualizar MensualidadUtils
- Generar mensualidades por deporte, no por alumno

---

### FASE 4: Actualización del Frontend

**Objetivo:** Actualizar componentes para usar datos por deporte

#### 4.1 Actualizar editar-alumno
- Mover gestión de tarifa a las pestañas de deporte
- Mover gestión de licencia a las pestañas de deporte
- Eliminar campos legacy del formulario de info básica

#### 4.2 Actualizar crear-alumno
- Pedir tarifa por cada deporte seleccionado
- Pedir licencia por cada deporte (si aplica)

#### 4.3 Actualizar listado-alumnos
- Mostrar información de tarifa del deporte activo/principal
- O mostrar resumen de todos los deportes

#### 4.4 Actualizar endpoints.service.ts
- Añadir métodos para actualizar tarifa por deporte
- Añadir métodos para actualizar licencia por deporte

#### 4.5 Actualizar interfaces/DTOs TypeScript
- AlumnoDTO: eliminar campos legacy
- AlumnoDeporteDTO: añadir campos nuevos

---

### FASE 5: Limpieza

**Objetivo:** Eliminar campos legacy de la base de datos

#### 5.1 Marcar campos como @Deprecated en Alumno.java
```java
@Deprecated
private TipoTarifa tipoTarifa;
// ... etc
```

#### 5.2 Script de eliminación SQL (después de validación completa)
```sql
ALTER TABLE alumno
    DROP COLUMN tipo_tarifa,
    DROP COLUMN cuantia_tarifa,
    DROP COLUMN tiene_licencia,
    DROP COLUMN numero_licencia,
    DROP COLUMN fecha_licencia,
    DROP COLUMN competidor,
    DROP COLUMN peso,
    DROP COLUMN fecha_peso,
    DROP COLUMN categoria,
    DROP COLUMN grado_id,
    DROP COLUMN fecha_grado,
    DROP COLUMN apto_para_examen,
    DROP COLUMN fecha_alta,
    DROP COLUMN fecha_alta_inicial,
    DROP COLUMN deporte;
```

#### 5.3 Eliminar campos de Alumno.java
- Eliminar atributos
- Eliminar getters/setters
- Actualizar constructores

#### 5.4 Actualizar AlumnoDTO
- Eliminar campos legacy
- Actualizar método `deAlumno()`

---

## Consideraciones Especiales

### Descuento Familiar
- `rolFamiliar` y `grupoFamiliar` permanecen en Alumno
- El descuento se aplica a TODOS los deportes del alumno
- La cuantía final por deporte = cuantía base - descuento familiar

### Productos y Mensualidades
- Cada producto está asociado a un deporte específico
- Las mensualidades deben generarse por deporte
- Revisar `ProductoAlumno` para asegurar relación con deporte

### Convocatorias
- Ya están por deporte (AlumnoConvocatoria tiene deporte)
- Verificar que no dependan de campos legacy de Alumno

### Antigüedad
- `fechaAltaInicial` en AlumnoDeporte = primera vez en ese deporte
- Antigüedad global del alumno = min(fechaAltaInicial) de todos sus deportes
- O mantener `fechaAltaInicial` en Alumno como fecha de primera inscripción

---

## Orden de Ejecución Recomendado

1. **FASE 1** - Preparar AlumnoDeporte (sin romper nada existente)
2. **FASE 2** - Migrar datos (mantener ambos lugares temporalmente)
3. **FASE 3** - Actualizar backend (usar nuevos campos, mantener compatibilidad)
4. **FASE 4** - Actualizar frontend (usar nuevos endpoints)
5. **Testing completo**
6. **FASE 5** - Limpieza (eliminar campos legacy)

---

## Estimación de Esfuerzo

| Fase | Descripción | Complejidad |
|------|-------------|-------------|
| 1 | Preparación AlumnoDeporte | Media |
| 2 | Migración de datos | Baja |
| 3 | Backend updates | Alta |
| 4 | Frontend updates | Alta |
| 5 | Limpieza | Baja |

---

## Riesgos

1. **Pérdida de datos**: Mitigar con backups antes de cada fase
2. **Inconsistencia temporal**: Durante la migración, datos en dos lugares
3. **Regresiones**: Testing exhaustivo después de cada fase
4. **Downtime**: Planificar ventana de mantenimiento para FASE 2 y 5

---

## Script de Migración Access → MySQL

Nota de estado: el script legacy `mysql/migrate_to_mysql_v8.py` fue retirado del repositorio al sanear datos sensibles. La migración histórica debe considerarse cerrada y los nuevos entornos deben partir de backup privado + migraciones controladas.

### Cambios necesarios después de la migración:

#### 1. Actualizar `write_alumnos()` (líneas 566-693)
Después de completar la migración multi-deporte, eliminar estos campos del INSERT de alumnos:
```python
# ELIMINAR estos campos del INSERT:
# - tipo_tarifa
# - cuantia_tarifa
# - tiene_licencia, numero_licencia, fecha_licencia
# - competidor, peso, fecha_peso
# - deporte, categoria_id, grado_id, fecha_grado
# - apto_para_examen, tiene_derecho_examen
# - fecha_alta, fecha_alta_inicial (mantener solo fechaBaja global)
```

#### 2. Actualizar `write_alumno_deporte()` (líneas 695-765)
Esta función ya existe y crea registros AlumnoDeporte. Después de la migración:
- Será la ÚNICA fuente de estos datos (no copia de alumno)
- Actualizar para que los datos vengan directamente de Access, no de alumno
- Añadir los nuevos campos de tarifa y licencia

```python
# Campos que debe insertar directamente desde Access:
def write_alumno_deporte_v2(f, schema: str, alumnos: List[Dict], stats: Dict):
    """
    Genera alumno_deporte directamente desde Access, sin pasar por alumno legacy.
    """
    for alumno in alumnos:
        # Leer datos de tarifa directamente de Access
        tipo_tarifa = map_tipo_tarifa(alumno.get("Tarifa"))
        cuantia_tarifa = sql_num_or_null(alumno.get("Cuota"))

        # Leer datos de licencia directamente de Access
        numero_licencia = sql_int_or_null(alumno.get("N_licencia"))
        tiene_licencia = "1" if numero_licencia != "NULL" else "0"
        fecha_licencia = sql_date_or_null(alumno.get("Fecha_licencia"))

        # ... resto de campos
```

#### 3. Nuevo flujo de migración (post-refactor)

```
Access Database
     │
     ├──► alumno (solo datos personales)
     │       - nombre, apellidos, direccion, nif, email, telefono
     │       - fechaNacimiento, autorizacionWeb, tieneDiscapacidad
     │       - rolFamiliar, grupoFamiliar (descuento familiar global)
     │       - activo, fechaBaja (estado global)
     │
     └──► alumno_deporte (datos por deporte)
             - alumno_id, deporte
             - grado_id, fecha_grado, apto_para_examen
             - tipo_tarifa, cuantia_tarifa
             - tiene_licencia, numero_licencia, fecha_licencia
             - competidor, peso, fecha_peso, categoria_id
             - fecha_alta, fecha_alta_inicial, fecha_baja, activo
```

#### 4. Tareas del script post-migración

| Tarea | Descripción |
|-------|-------------|
| Eliminar campos legacy de `write_alumnos()` | No insertar campos que ya no existen en la entidad |
| Actualizar `write_alumno_deporte()` | Leer directamente de Access, no de subquery |
| Actualizar mappings | Asegurar que `map_tipo_tarifa()` funciona para AlumnoDeporte |
| Probar migración completa | Ejecutar script y verificar datos |

---

## Notas Adicionales

- Crear rama `feature/multideporte-migration` para este trabajo
- Hacer commits pequeños y frecuentes
- Documentar cambios en CHANGELOG.md
- Actualizar CLAUDE.md después de completar la migración
- **IMPORTANTE**: No reintroducir scripts de migración con datos reales en el repositorio público
