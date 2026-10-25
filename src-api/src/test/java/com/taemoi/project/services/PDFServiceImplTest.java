package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.controllers.PDFController;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.legado.Deporte;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.services.impl.PDFServiceImpl;

class PDFServiceImplTest {
    private final AlumnoDeporteRepository repository = mock(AlumnoDeporteRepository.class);
    private final ProductoAlumnoRepository cargos = mock(ProductoAlumnoRepository.class);
    private final PDFServiceImpl service = new PDFServiceImpl();

    PDFServiceImplTest() {
        ReflectionTestUtils.setField(service, "alumnoDeporteRepository", repository);
        ReflectionTestUtils.setField(service, "productoAlumnoRepository", cargos);
        ReflectionTestUtils.setField(service, "marcaEscuela", MarcaEscuelaPruebas.soloMoiskimdo());
    }

    @Test
    void dpfContieneSoloNombresOrdenadosYRespetaEstadoDeAlumna() throws Exception {
        AlumnoDeporte z = membership("Zoe", "Perez", true);
        AlumnoDeporte a = membership("Ana & Eva", "<Lopez>", true);
        // Otro deporte de la misma alumna no debe excluir su inscripción en DPF.
        AlumnoDeporte otherSport = new AlumnoDeporte();
        otherSport.setAlumno(a.getAlumno());
        otherSport.setDisciplina(com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.TAEKWONDO.name()));
        a.getAlumno().setDeportes(List.of(otherSport, a));
        when(repository.findActivosByDeporteWithAlumno(Deporte.DEFENSA_PERSONAL_FEMENINA.name()))
                .thenReturn(List.of(z, membership("Inactiva", "Prueba", false), a, membership(null, null, true)));
        String active = text(service.generarInformeAlumnasDpf(true));
        assertTrue(active.contains("Ana & Eva"));
        assertTrue(active.contains("<Lopez>"));
        assertTrue(active.indexOf("Ana & Eva") < active.indexOf("Zoe"));
        assertFalse(active.contains("Inactiva"));
        assertFalse(active.contains("null"));
        for (String excluded : List.of("private@example.test", "600123456", "987654", "Licencia", "Expediente", "Grado")) {
            assertFalse(active.contains(excluded), excluded);
        }
        assertTrue(text(service.generarInformeAlumnasDpf(false)).contains("Inactiva"));
        verify(repository, times(2)).findActivosByDeporteWithAlumno(Deporte.DEFENSA_PERSONAL_FEMENINA.name());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void listadoDpfVacioProducePdfLegible() throws Exception {
        when(repository.findActivosByDeporteWithAlumno(Deporte.DEFENSA_PERSONAL_FEMENINA.name())).thenReturn(List.of());
        assertTrue(text(service.generarInformeAlumnasDpf(true)).contains("No hay alumnas"));
    }

    @Test
    void controladorTransmiteFiltroActivoYDevuelvePdfEnLinea() {
        PDFService pdfService = mock(PDFService.class);
        PDFController controller = new PDFController();
        ReflectionTestUtils.setField(controller, "pdfService", pdfService);
        byte[] bytes = { 1, 2, 3 };
        when(pdfService.generarInformeAlumnasDpf(false)).thenReturn(bytes);
        var response = controller.generarInformeAlumnasDpf(false);
        assertArrayEquals(bytes, response.getBody());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals("inline", response.getHeaders().getContentDisposition().getType());
    }

    /**
     * Decisión 297: en el informe de mensualidades la columna por fila es «Importe» (precio × cantidad), para que las
     * filas sumen el total; un cargo de 12,50 con cantidad 3 muestra 37,50 y no el precio unitario.
     */
    @Test
    void informeDeMensualidades_columnaImporte_precioPorCantidad() throws Exception {
        Alumno alumno = new Alumno();
        alumno.setId(297L);
        alumno.setNombre("Lucia");
        alumno.setApellidos("Prueba");
        alumno.setActivo(true);
        alumno.setNumeroExpediente(29701);
        ProductoAlumno cargo = new ProductoAlumno();
        cargo.setAlumno(alumno);
        cargo.setConcepto("MENSUALIDAD ENERO 2026 - TAEKWONDO");
        cargo.setPrecio(12.5);
        cargo.setCantidad(3);
        cargo.setPagado(false);
        cargo.setFechaAsignacion(new java.util.Date());
        when(cargos.findAllMensualidadesYTarifasCompetidorWithAlumno()).thenReturn(List.of(cargo));

        String texto = text(service.generarInformeMensualidades(false));

        // La cabecera de la tabla sale en mayúsculas (CSS).
        assertTrue(texto.toUpperCase().contains("IMPORTE"), texto);
        assertFalse(texto.toUpperCase().contains("PRECIO"), texto);
        assertFalse(texto.contains("12.50") || texto.contains("12,50"), "sin el precio unitario: " + texto);
        // La fila: fecha de asignación, importe y estado (el concepto ocupa sus propias líneas).
        String fila = texto.lines().filter(l -> l.contains(" PENDIENTE")).findFirst().orElseThrow(() -> new AssertionError(texto));
        assertTrue(fila.contains("37.50 €") || fila.contains("37,50 €"), "la fila muestra el importe: " + fila);
    }

    private AlumnoDeporte membership(String name, String surnames, boolean active) {
        Alumno alumno = new Alumno();
        alumno.setNombre(name);
        alumno.setApellidos(surnames);
        alumno.setActivo(active);
        alumno.setEmail("private@example.test");
        alumno.setTelefono("600123456");
        alumno.setNumeroExpediente(987654);
        AlumnoDeporte membership = new AlumnoDeporte();
        membership.setAlumno(alumno);
        membership.setDisciplina(com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.DEFENSA_PERSONAL_FEMENINA.name()));
        membership.setActivo(true);
        return membership;
    }

    private String text(byte[] bytes) throws Exception {
        try (PDDocument document = PDDocument.load(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
