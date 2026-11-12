import { cambiosPorPagina, cuerpoGuardar, nombreError } from '../../panel-nuevo/web/contenido/web-contenido-guardar';
import { HUECOS, textosQuitarDato } from './huecos-editor';
import { CANAL_EDITOR_WEB, esMensajeDelEditor, esMensajeDelPanel } from './puente-editor';
import { casoDeTexto, defTexto, defectoFijo, esTextoEditable, paginaDeTexto, pasoDeTexto } from './textos-editor';

/** W12d-bis: las claves de las otras seis páginas en el catálogo del editor (mismo máximo y obligatoriedad que `ClavesWeb`). */
describe('textos del editor de las otras seis páginas (W12d-bis)', () => {
  it('cada clave con el chip, el máximo y la obligatoriedad del boceto (y de ClavesWeb)', () => {
    expect(defTexto('sede.anterior')).toEqual(
      jasmine.objectContaining({ nombre: 'Rótulo de la parada anterior', max: 16, obligatorio: true, multi: false, parte: 'sp-cartel', ejemplo: 'Anterior' }),
    );
    expect(defTexto('tarifas.pie_cuadro')).toEqual(jasmine.objectContaining({ nombre: 'Nota de la tabla', max: 160, obligatorio: false }));
    expect(defTexto('legal.aviso.p0')).toEqual(jasmine.objectContaining({ nombre: 'Párrafo', max: 1200, obligatorio: false, multi: true }));
    expect(defTexto('pre.enviar_espera')).toEqual(jasmine.objectContaining({ nombre: 'Botón', max: 48, obligatorio: true, parte: 'pr-flujo' }));
    expect(defTexto('cabecera.menu')).toEqual(jasmine.objectContaining({ nombre: 'Botón del menú en el móvil', max: 12, obligatorio: true, parte: 'cabecera' }));
    expect(defTexto('evento.una_foto')).toEqual(jasmine.objectContaining({ nombre: 'Cuenta con una foto', max: 24, parte: 'ev-galeria' }));
    // El texto de ejemplo es el del tema (no el del boceto, si difiere).
    expect(defTexto('sede.leyenda_lleno')!.ejemplo).toBe('{x}: puedes apuntarte a la lista de espera');
    expect(defectoFijo('tarifas.examen_desde')).toBe('Según grado, desde {x}');
  });

  it('fuera, por decisión del propietario: el aria-label del botón de la sede y el texto legal que el tema no lee', () => {
    for (const clave of ['sede.preinscribirme_en', 'legal.priv.h1', 'legal.priv.p1', 'legal.aviso.p3', 'legal.priv.p2', 'legal.cookies.p0', 'legal.normas.p0']) {
      expect(esTextoEditable(clave)).withContext(clave).toBeFalse();
    }
  });

  it('cuántos textos propios de cada página tiene el catálogo (los compartidos con Inicio cuentan en Inicio)', () => {
    const claves = (pagina: string) => Object.keys(PRUEBA).filter((k) => esTextoEditable(k) && paginaDeTexto(k) === pagina);
    // 22 de `sede.*` (sin `sede.preinscribirme_en`) y los 5 de los grupos (`horarios.*`, `sedes.proxima_rotulo`).
    expect(claves('sede').length).toBe(27);
    expect(claves('tarifas').length).toBe(25);
    expect(claves('eventos').length).toBe(10);
    expect(claves('evento').length).toBe(5);
    expect(claves('legales').length).toBe(20);
    expect(claves('preinscripcion').length).toBe(59);
  });

  it('página, caso y paso de cada texto (boceto `PAG_DE_PARTE` y `CASO_DE`)', () => {
    expect(paginaDeTexto('tarifas.al_mes')).toBe('inicio');
    expect(paginaDeTexto('cabecera.menu')).toBe('inicio');
    expect(paginaDeTexto('horarios.lleno')).toBe('sede');
    expect(paginaDeTexto('legal.ficha_cif')).toBe('legales');
    expect(paginaDeTexto('portada.foto')).toBe('inicio');
    expect(casoDeTexto('tarifas.vacio_texto')).toBe('sin-tarifas');
    expect(casoDeTexto('pre.estado_espera')).toBe('fin-espera');
    expect(casoDeTexto('pre.enviar')).toBeNull();
    expect(pasoDeTexto('pre.titulo_pueblo')).toBe(1);
    expect(pasoDeTexto('pre.dia')).toBe(2);
    expect(pasoDeTexto('pre.enviar')).toBe(3);
    expect(pasoDeTexto('pre.descargar')).toBe(4);
    expect(pasoDeTexto('pre.atras')).toBe('flujo');
    expect(pasoDeTexto('sede.anterior')).toBeNull();
  });

  it('resumen: fuera de Inicio, el nombre del dato lleva delante la página', () => {
    expect(nombreError('tarifas.cada_alumno', [])).toBe('Tarifas · Cuadro de tarifas: quién paga «Cada alumno»');
    expect(nombreError('cabecera.menu', [])).toBe('Cabecera: botón del menú en el móvil «Menú»');
    expect(nombreError('horarios.lleno', [])).toBe('Una sede · Grupos y horas: aviso de grupo lleno «Lleno»');
  });

  it('cambios por página: cada texto en la suya; fotos, bloques, cabecera y pie, en Inicio', () => {
    expect(cambiosPorPagina(['sede.anterior', 'pre.atras', 'pre.enviar', 'menu.sedes', 'portada.foto'], 2)).toEqual({
      sede: 1,
      preinscripcion: 2,
      inicio: 4,
    });
  });

  it('guardar no manda los textos de otras páginas iguales al del tema', () => {
    const filas = [
      { clave: 'sede.anterior', texto: 'Anterior', imagenId: null, imagenUrl: null, orden: 0, oculto: false, permiso: null },
      { clave: 'pre.atras', texto: 'Volver', imagenId: null, imagenUrl: null, orden: 0, oculto: false, permiso: null },
    ];
    expect(cuerpoGuardar(1, filas, {}).bloques.map((b) => b.clave)).toEqual(['pre.atras']);
  });

  it('el diálogo de quitar una ficha: título y mensaje aprobados; null si el hueco no es de ese texto', () => {
    expect(textosQuitarDato('pre.vacio_llamar', ['x'], 'Llama a Herrera', 'Llama a')).toEqual({
      titulo: '¿Quitar «sede elegida» de este texto?',
      mensaje: 'En tu web, la frase saldrá sin la sede elegida. Hoy se ve así: «Llama a Herrera» y pasará a verse así: «Llama a»',
      accion: 'Quitar dato',
      cancelar: 'Cancelar',
    });
    expect(textosQuitarDato('pre.estado_espera', ['n', 'grupo', 'dias'], 'a', 'b')!.mensaje).toContain(
      'saldrá sin la posición en la lista, el grupo elegido y los días elegidos.',
    );
    expect(textosQuitarDato('tarifas.titulo', ['x'], 'a', 'b')).toBeNull();
    expect(textosQuitarDato('pre.paso_n', [], 'a', 'b')).toBeNull();
    // Cada hueco es de un texto editable.
    expect(Object.keys(HUECOS).every(esTextoEditable)).toBeTrue();
  });

  it('el puente: pedir y contestar el diálogo, con forma cerrada', () => {
    const pedir = { canal: CANAL_EDITOR_WEB, tipo: 'pedir-quitar-dato', clave: 'pre.paso_n', huecos: ['n', 'total'], antes: 'Paso 1 de 3', despues: 'Paso de 3' };
    expect(esMensajeDelEditor(pedir)).toBeTrue();
    expect(esMensajeDelEditor({ ...pedir, huecos: ['x'] })).toBeFalse();
    expect(esMensajeDelEditor({ ...pedir, clave: 'sede.preinscribirme_en', huecos: ['x'] })).toBeFalse();
    expect(esMensajeDelEditor({ ...pedir, antes: 1 })).toBeFalse();
    expect(esMensajeDelPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: true })).toBeTrue();
    expect(esMensajeDelPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: 'si' })).toBeFalse();
  });
});

/** Las claves de `TX` del boceto de las seis páginas y de Inicio (para contar las editables de cada página). */
const PRUEBA: Readonly<Record<string, true>> = Object.fromEntries(
  [
    'sede.anterior sede.siguiente sede.preinscribirme_en sede.ver_grupos sede.rotulo_horarios sede.titulo_horarios sede.cabeza_hora',
    'sede.cabeza_grupo sede.cabeza_dias sede.leyenda_quedan sede.leyenda_lleno sede.leyenda_espera sede.rotulo_llegar sede.titulo_llegar',
    'sede.ficha_lugar sede.ficha_entrada sede.ficha_telefono sede.abrir_mapa sede.rotulo_tarifa sede.rotulo_ruta sede.titulo_ruta',
    'sede.estas_aqui sede.sin_clases sedes.proxima_rotulo horarios.lleno horarios.espera horarios.boton horarios.boton_espera',
    'tarifas.cuota_igual tarifas.tambien tarifas.incluye_aparte tarifas.ver_horarios tarifas.rotulo_cuadro tarifas.titulo_cuadro',
    'tarifas.leyenda_cuadro tarifas.col_tarifa tarifas.col_quien tarifas.col_importe tarifas.cada_alumno tarifas.hermanos',
    'tarifas.pie_cuadro tarifas.rotulo_sede tarifas.titulo_sede tarifas.rotulo_aparte tarifas.titulo_aparte tarifas.licencia',
    'tarifas.examen tarifas.examen_desde tarifas.nota_aparte tarifas.vacio_titular tarifas.vacio_texto tarifas.vacio_correo',
    'tarifas.vacio_telefono tarifas.titulo tarifas.al_mes tarifas.punto1 tarifas.boton_preinscripcion',
    'eventos.pag_titulo eventos.pag_entrada eventos.proximos eventos.n_eventos eventos.anteriores eventos.sin_proximos_titular',
    'eventos.sin_proximos_texto eventos.ver_anteriores eventos.un_evento eventos.sin_anteriores eventos.enlace_ver',
    'evento.volver evento.documentos evento.galeria evento.n_fotos evento.una_foto',
    'legal.titulo legal.entrada legal.actualizada legal.en_esta_pagina legal.aviso legal.aviso.p0 legal.aviso.h1 legal.aviso.p1',
    'legal.aviso.h2 legal.aviso.p2 legal.aviso.h3 legal.aviso.p3 legal.privacidad legal.priv.p0 legal.priv.h1 legal.priv.p1',
    'legal.priv.h2 legal.priv.p2 legal.cookies legal.cookies.p0 legal.normas legal.normas.p0 legal.ficha_titular legal.ficha_cif',
    'legal.ficha_domicilio legal.ficha_correo',
    'pre.atras pre.paso_n pre.paso_pueblo pre.paso_grupo pre.paso_datos pre.titulo_pueblo pre.pista_pueblo pre.continuar',
    'pre.titulo_grupo pre.cambiar_pueblo pre.fecha_leyenda pre.fecha_pista pre.dia pre.mes pre.anio pre.ver_grupos pre.espera_fecha',
    'pre.titulo_datos pre.pista_datos pre.bloque_alumno pre.nombre pre.apellidos pre.bloque_contacto pre.telefono pre.email',
    'pre.email_pista pre.bloque_normas pre.acepto pre.fotos pre.bloque_firma pre.firma_pista pre.enviar pre.fin_plaza pre.referencia',
    'pre.estado_plaza pre.que_ahora pre.paso_correo pre.paso_correo_texto pre.paso_revisa pre.paso_revisa_texto pre.descargar',
    'pre.volver_inicio pre.resumen_titulo pre.resumen_pueblo pre.resumen_grupo pre.resumen_dias pre.sin_elegir pre.tarifa_igual',
    'pre.vacio_titulo pre.vacio_otras pre.vacio_llamar pre.vacio_llamar_fin pre.revisar_fecha pre.espera_titulo pre.espera_texto',
    'pre.enviar_espera pre.fin_espera pre.estado_espera pre.paso_correo_espera',
  ]
    .join(' ')
    .split(' ')
    .map((k) => [k, true]),
);
