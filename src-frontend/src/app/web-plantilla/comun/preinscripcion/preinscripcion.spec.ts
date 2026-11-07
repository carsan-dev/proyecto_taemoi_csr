import { PLAZAS, grupoDePrueba, sedeDePrueba, turnoDePrueba } from '../../datos/datos-de-prueba';
import { borrarBorrador, guardarBorrador, leerBorrador, leerValor, guardarValor } from './almacen';
import {
  DATOS_VACIOS,
  DatosPreinscripcion,
  diasDeParametro,
  diasElegidos,
  edadTemporada,
  encajaEdad,
  errorFecha,
  esMenor,
  inicioTemporada,
  normasDeContenido,
  plazasLibres,
  salidaDeParametros,
  salidasParaEdad,
  sedeDeParametro,
  sinPlaza,
  solicitudDe,
  turnosElegidos,
  validarDatos,
} from './preinscripcion';

/** Sábado 7 de noviembre de 2026, 12:00. */
const AHORA = new Date(2026, 10, 7, 12, 0);

function adulto(cambios: Partial<DatosPreinscripcion> = {}): DatosPreinscripcion {
  return {
    ...DATOS_VACIOS,
    nombre: 'Lucía',
    apellidos: 'García Ruiz',
    dia: '14',
    mes: '3',
    anio: '1990',
    dni: '12345678Z',
    direccion: 'C/ Ejemplo, 1, Estepa',
    discapacidad: 'no',
    telefono: '612 345 678',
    email: 'lucia@ejemplo.es',
    normas: true,
    firmante: 'Lucía García Ruiz',
    ...cambios,
  };
}

const menor = (cambios: Partial<DatosPreinscripcion> = {}) =>
  adulto({ anio: '2017', dni: '', tutorNombre: 'Ana Ruiz Pérez', tutorDni: 'X1234567L', ...cambios });

const validar = (d: DatosPreinscripcion, firmado = true, normasListas = true) =>
  validarDatos(d, { ahora: AHORA, firmado, normasListas });
const ids = (d: DatosPreinscripcion, firmado = true, normasListas = true) => validar(d, firmado, normasListas).map((e) => e.id);

describe('preinscripción de la web por plantilla · lógica común', () => {
  describe('fecha de nacimiento y edad', () => {
    it('mensajes del boceto: vacía, sin parte, con letras, año corto, mes y día imposibles, futura, antigua', () => {
      const f = (dia: string, mes: string, anio: string) => errorFecha({ dia, mes, anio }, AHORA);
      expect(f('', '', '')?.msg).toBe('Escribe la fecha de nacimiento del alumno.');
      expect(f('', '3', '2016')).toEqual({ id: 'dia', campo: 'fecha', msg: 'Falta el día de la fecha de nacimiento.' });
      expect(f('14', '', '2016')?.id).toBe('mes');
      expect(f('14', '3', '')?.id).toBe('anio');
      expect(f('1a', '3', '2016')?.msg).toBe('Escribe la fecha solo con números, por ejemplo 14 3 2016.');
      expect(f('14', '3', '16')?.msg).toBe('Escribe el año con cuatro cifras, por ejemplo 2016.');
      expect(f('14', '13', '2016')?.msg).toBe('El mes tiene que ir del 1 al 12.');
      expect(f('31', '2', '2017')?.msg).toBe('Esa fecha no existe: febrero de 2017 tiene 28 días.');
      expect(f('8', '11', '2026')?.msg).toBe('La fecha de nacimiento tiene que ser anterior a hoy.');
      expect(f('1', '1', '1899')?.msg).toBe('Revisa el año: 1899 no parece un año de nacimiento.');
      expect(f('29', '2', '2016')).toBeNull();
    });

    it('edad de cohorte: año de inicio de la temporada menos año de nacimiento', () => {
      expect(inicioTemporada('2026-2027')).toBe(2026);
      expect(inicioTemporada(null)).toBeNull();
      expect(inicioTemporada('2026')).toBeNull();
      expect(edadTemporada({ dia: '31', mes: '12', anio: '2016' }, 2026, AHORA)).toBe(10);
      expect(edadTemporada({ dia: '31', mes: '12', anio: '2016' }, null, AHORA)).toBeNull();
      expect(edadTemporada({ dia: '', mes: '', anio: '' }, 2026, AHORA)).toBeNull();
    });

    it('menor de edad a día de hoy (cumpleaños aún no llegado)', () => {
      expect(esMenor({ dia: '8', mes: '11', anio: '2008' }, AHORA)).toBeTrue();
      expect(esMenor({ dia: '7', mes: '11', anio: '2008' }, AHORA)).toBeFalse();
      expect(esMenor({ dia: '', mes: '', anio: '' }, AHORA)).toBeFalse();
    });

    it('grupo de su edad: sin mínimo 0, sin máximo 99', () => {
      expect(encajaEdad({ rangoEdadMin: 4, rangoEdadMax: 6 }, 6)).toBeTrue();
      expect(encajaEdad({ rangoEdadMin: 4, rangoEdadMax: 6 }, 7)).toBeFalse();
      expect(encajaEdad({ rangoEdadMin: 12, rangoEdadMax: null }, 60)).toBeTrue();
      expect(encajaEdad({ rangoEdadMin: null, rangoEdadMax: null }, 0)).toBeTrue();
      expect(encajaEdad({ rangoEdadMin: 4, rangoEdadMax: 6 }, null)).toBeFalse();
    });
  });

  describe('validación del paso de datos', () => {
    it('un adulto completo, con firma y normas, no tiene errores', () => {
      expect(validar(adulto())).toEqual([]);
    });

    it('todo vacío: los errores en el orden del formulario, con la firma al final', () => {
      expect(ids({ ...DATOS_VACIOS }, false)).toEqual([
        'nombre',
        'apellidos',
        'dia',
        'dni',
        'direccion',
        'discapacidad',
        'telefono',
        'email',
        'normas',
        'firmante',
        'firma',
      ]);
    });

    it('DNI/NIE: ocho cifras y letra, o X/Y/Z, siete cifras y letra; sin espacios ni guiones', () => {
      for (const valido of ['12345678Z', '12345678z', 'X1234567L', 'Y1234567X', 'Z7654321A']) {
        expect(ids(adulto({ dni: valido }))).withContext(valido).toEqual([]);
      }
      for (const malo of ['1234567Z', '123456789', 'A1234567L', '12345678-Z', '12 345 678Z', 'X12345678']) {
        expect(validar(adulto({ dni: malo }))).withContext(malo).toEqual([
          { id: 'dni', campo: 'dni', msg: 'El DNI/NIE es obligatorio y debe tener un formato válido.' },
        ]);
      }
      expect(validar(adulto({ dni: '' }))[0].msg).toBe('El DNI/NIE es obligatorio y debe tener un formato válido.');
    });

    it('menor: DNI opcional (si lo pone, válido) y responsable legal con nombre y DNI/NIE válidos', () => {
      expect(validar(menor())).toEqual([]);
      expect(validar(menor({ dni: '1234' }))[0]).toEqual({ id: 'dni', campo: 'dni', msg: 'Si lo indicas, debe tener un formato válido.' });
      expect(ids(menor({ tutorNombre: '', tutorDni: '' }))).toEqual(['tutorNombre', 'tutorDni']);
      expect(validar(menor({ tutorDni: '1234' }))[0].msg).toBe('Introduce un DNI/NIE válido del responsable legal.');
      expect(ids(adulto({ tutorNombre: '', tutorDni: '' }))).toEqual([]);
    });

    it('teléfono: nueve cifras empezando por 6, 7, 8 o 9, con +34 o 0034 opcional; el secundario, vacío o válido', () => {
      for (const valido of ['612345678', '612 345 678', '+34612345678', '+34 612 345 678', '0034 912345678', '812345678']) {
        expect(ids(adulto({ telefono: valido }))).withContext(valido).toEqual([]);
      }
      for (const malo of ['61234', '512345678', '+33612345678', '6123456789', '612-345-678']) {
        expect(ids(adulto({ telefono: malo }))).withContext(malo).toEqual(['telefono']);
      }
      expect(validar(adulto({ telefono2: '61234' }))[0].msg).toBe('Introduce un teléfono secundario de nueve dígitos, o déjalo vacío.');
      expect(ids(adulto({ telefono2: '+34 699 000 000' }))).toEqual([]);
    });

    it('email, observaciones de más de 1.000 caracteres y discapacidad sin contestar', () => {
      expect(ids(adulto({ email: 'ana@ejemplo' }))).toEqual(['email']);
      expect(ids(adulto({ observaciones: 'x'.repeat(1001) }))).toEqual(['observaciones']);
      expect(ids(adulto({ observaciones: 'x'.repeat(1000) }))).toEqual([]);
      expect(ids(adulto({ discapacidad: '' }))).toEqual(['discapacidad']);
      expect(ids(adulto({ discapacidad: 'si' }))).toEqual([]);
    });

    it('normas obligatorias; sin normas cargadas no se puede enviar', () => {
      expect(validar(adulto({ normas: false }))).toEqual([
        { id: 'normas', campo: 'normas', msg: 'Marca que aceptas las normas de la actividad.' },
      ]);
      expect(validar(adulto(), true, false)).toEqual([
        { id: 'normas', campo: 'normas', msg: 'No puedes enviar la solicitud hasta que se carguen las normas.' },
      ]);
    });

    it('firma dibujada obligatoria (decisión 493) y nombre de quien firma', () => {
      expect(validar(adulto(), false)).toEqual([{ id: 'firma', campo: 'firma', msg: 'Firma en el recuadro.' }]);
      expect(ids(adulto({ firmante: '  ' }))).toEqual(['firmante']);
    });
  });

  describe('sede, grupo y días', () => {
    const peques = grupoDePrueba('Peques', [
      turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.quedan(2)),
      turnoDePrueba('Miércoles', '17:00', '17:45', PLAZAS.lleno),
      turnoDePrueba('Viernes', '18:00', '19:00', PLAZAS.espera),
    ]);
    const infantil = grupoDePrueba('Infantil', [turnoDePrueba('Martes', '18:00', '19:00')], { rangoEdadMin: 7, rangoEdadMax: 11 });
    const sede = sedeDePrueba(2, 'Estepa', { grupos: [peques, infantil] });
    const sinSlug = sedeDePrueba(9, 'Herrera', { slug: null });

    it('la sede del parámetro, por slug o por id', () => {
      expect(sedeDeParametro([sede, sinSlug], 'estepa')).toBe(sede);
      expect(sedeDeParametro([sede, sinSlug], '9')).toBe(sinSlug);
      expect(sedeDeParametro([sede, sinSlug], '2')).toBe(sede);
      expect(sedeDeParametro([sede, sinSlug], 'mollina')).toBeNull();
      expect(sedeDeParametro([sede, sinSlug], null)).toBeNull();
    });

    it('las salidas de su edad, por hora', () => {
      expect(salidasParaEdad(sede, 5).map((s) => `${s.grupo.nombre} ${s.inicio}`)).toEqual(['Peques 17:00', 'Peques 18:00']);
      expect(salidasParaEdad(sede, 8).map((s) => s.grupo.nombre)).toEqual(['Infantil']);
      expect(salidasParaEdad(sede, 40)).toEqual([]);
      expect(salidasParaEdad(sede, null)).toEqual([]);
    });

    it('la salida de ?grupo=&dias=: la que tiene esos días; sin días, la primera del grupo', () => {
      expect(salidaDeParametros(sede, String(peques.id), [])?.inicio).toBe('17:00');
      expect(salidaDeParametros(sede, String(peques.id), [5])?.inicio).toBe('18:00');
      expect(salidaDeParametros(sede, String(peques.id), [2])?.inicio).toBe('17:00');
      expect(salidaDeParametros(sede, '999', [])).toBeNull();
      expect(salidaDeParametros(sede, 'abc', [])).toBeNull();
    });

    it('días: del parámetro, solo los de la salida; sin ninguno, todos; un turno por día', () => {
      const salida = salidaDeParametros(sede, String(peques.id), [])!;
      expect(diasDeParametro('3, 1,9,x,1')).toEqual([3, 1]);
      expect(diasElegidos(salida, [3])).toEqual([3]);
      expect(diasElegidos(salida, [5])).toEqual([1, 3]);
      expect(diasElegidos(salida, [3, 1])).toEqual([1, 3]);
      expect(turnosElegidos(salida, [1, 3]).map((t) => t.id)).toEqual([peques.turnos[0].id, peques.turnos[1].id]);
    });

    it('lista de espera solo si ningún turno elegido tiene plaza; las libres, la menor de los que tienen', () => {
      const [lunes, miercoles, viernes] = peques.turnos;
      expect(sinPlaza([lunes, miercoles])).toBeFalse();
      expect(sinPlaza([miercoles])).toBeTrue();
      expect(sinPlaza([viernes])).toBeTrue();
      expect(sinPlaza([])).toBeFalse();
      expect(plazasLibres([lunes, miercoles])).toBe(2);
      expect(plazasLibres([miercoles])).toBeNull();
    });
  });

  describe('solicitud', () => {
    it('la forma de Moiskimdo: campos del formulario, fecha ISO, turnos, firma y discapacidad booleana', () => {
      const s = solicitudDe(adulto({ fotos: true, telefono2: ' 699000000 ', observaciones: ' Mejor por la tarde ' }), {
        deporte: 'TAEKWONDO',
        turnoIds: [11, 12],
        firmaBase64: 'data:image/png;base64,AAA',
        ahora: AHORA,
      });
      expect(s).toEqual({
        deporte: 'TAEKWONDO',
        turnoIds: [11, 12],
        nombre: 'Lucía',
        apellidos: 'García Ruiz',
        dni: '12345678Z',
        fechaNacimiento: '1990-03-14',
        direccion: 'C/ Ejemplo, 1, Estepa',
        telefono: '612 345 678',
        telefono2: '699000000',
        email: 'lucia@ejemplo.es',
        observaciones: 'Mejor por la tarde',
        tutorNombre: '',
        tutorDni: '',
        tieneDiscapacidad: false,
        consentimientoFotografico: true,
        aceptacionNormas: true,
        firmanteNombre: 'Lucía García Ruiz',
        firmaBase64: 'data:image/png;base64,AAA',
      });
    });

    it('un menor manda su responsable legal; un adulto, vacío aunque quede escrito en el borrador', () => {
      const base = { deporte: 'TAEKWONDO', turnoIds: [1], firmaBase64: 'x', ahora: AHORA };
      const m = solicitudDe(menor({ discapacidad: 'si' }), base);
      expect([m.tutorNombre, m.tutorDni, m.dni, m.tieneDiscapacidad]).toEqual(['Ana Ruiz Pérez', 'X1234567L', '', true]);
      const a = solicitudDe(adulto({ tutorNombre: 'Ana', tutorDni: 'X1234567L' }), base);
      expect([a.tutorNombre, a.tutorDni]).toEqual(['', '']);
    });

    it('normas de la plantilla: objeto o JSON en texto, sin vacías; importes aparte', () => {
      expect(normasDeContenido({ normas: ['Uno', ' ', 'Dos', 3], importes: 'Mensualidad: 25 €' })).toEqual({
        normas: ['Uno', 'Dos'],
        importes: 'Mensualidad: 25 €',
      });
      expect(normasDeContenido('{"normas":["A"]}')).toEqual({ normas: ['A'], importes: null });
      expect(normasDeContenido('no es json')).toEqual({ normas: [], importes: null });
      expect(normasDeContenido(null)).toEqual({ normas: [], importes: null });
    });
  });

  describe('almacenamiento', () => {
    const CLAVE = 'prueba:preinscripcion';
    afterEach(() => {
      sessionStorage.removeItem(CLAVE);
      localStorage.removeItem(CLAVE);
    });

    it('borrador en sessionStorage: se guarda, se lee con los tipos de cada campo y se borra', () => {
      guardarBorrador(sessionStorage, CLAVE, adulto({ fotos: true }));
      expect(leerBorrador(sessionStorage, CLAVE)).toEqual(adulto({ fotos: true }));
      borrarBorrador(sessionStorage, CLAVE);
      expect(leerBorrador(sessionStorage, CLAVE)).toEqual({ ...DATOS_VACIOS });
    });

    it('un borrador roto o con tipos ajenos se lee vacío en esos campos', () => {
      sessionStorage.setItem(CLAVE, '{roto');
      expect(leerBorrador(sessionStorage, CLAVE)).toEqual({ ...DATOS_VACIOS });
      sessionStorage.setItem(CLAVE, JSON.stringify({ nombre: 3, normas: 'sí', discapacidad: 'quizá', email: 'a@b.es', firma: 'x' }));
      expect(leerBorrador(sessionStorage, CLAVE)).toEqual({ ...DATOS_VACIOS, email: 'a@b.es' });
    });

    it('sin almacenamiento no falla', () => {
      const roto = { getItem: () => { throw new Error('x'); }, setItem: () => { throw new Error('x'); }, removeItem: () => { throw new Error('x'); } } as unknown as Storage;
      expect(leerBorrador(roto, CLAVE)).toEqual({ ...DATOS_VACIOS });
      expect(() => guardarBorrador(roto, CLAVE, adulto())).not.toThrow();
      expect(() => borrarBorrador(roto, CLAVE)).not.toThrow();
      expect(leerValor(roto, CLAVE)).toBeNull();
      expect(() => guardarValor(roto, CLAVE, 'x')).not.toThrow();
      expect(leerBorrador(null, CLAVE)).toEqual({ ...DATOS_VACIOS });
    });

    it('última sede en localStorage', () => {
      guardarValor(localStorage, CLAVE, 'estepa');
      expect(leerValor(localStorage, CLAVE)).toBe('estepa');
    });
  });
});
