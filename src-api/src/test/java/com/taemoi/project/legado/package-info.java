/**
 * ORÁCULO CONGELADO (solo tests). Copia literal de los enums y clases de configuración que el código de
 * producción usaba hasta V27 ({@code Deporte}, {@code TipoGrado}, {@code TipoTarifa}, {@code TipoCategoria},
 * {@code TarifaConfig}, {@code GradeProgressionConfig}, {@code ExamEligibilityConfig},
 * {@code ExamMaterialBlockConfig}, {@code GradoUtils}, {@code FechaUtils}, {@code DeporteEdadUtils}).
 *
 * <p>V28 (contracción) los quita de producción: el catálogo (disciplina, grado, tarifa, categoria) es la
 * única fuente. Se conservan aquí, sin cambios salvo el paquete, para que los tests de caracterización y
 * de paridad sigan comparando el catálogo de Taemoi (organización 1) con el comportamiento anterior, con
 * las mismas aserciones. No se usan desde {@code src/main}.
 */
package com.taemoi.project.legado;
