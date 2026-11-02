import { HttpErrorResponse } from '@angular/common/http';

import { CATALOGO_TAEMOI } from '../../features/catalogo/testing/catalogo.fixtures';
import { MOTIVO_LECTURA } from '../../utils/subida-archivos.util';
import { VideoDetalle } from './material-examen.models';
import {
  coloresBloque,
  fraseGrados,
  fusionarOrden,
  mismoOrden,
  motivoDocumentoSustituto,
  motivoFalloSubida,
  motivoVideo,
  peso,
  porcentaje,
  resumenDisciplina,
  textoGrados,
  textoProgreso,
  textoVideos,
  tipoFichero,
} from './material-examen-vista';

const MB = 1024 * 1024;

function fichero(nombre: string, bytes: number, tipo = ''): File {
  const archivo = new File(['x'], nombre, { type: tipo });
  Object.defineProperty(archivo, 'size', { value: bytes });
  return archivo;
}

function video(nombreFichero: string, orden = 1): VideoDetalle {
  return { fichero: nombreFichero, titulo: nombreFichero, orden, bytes: 10, fecha: '2026-09-12T10:00:00Z', tambienEnSuperiores: false };
}

describe('material-examen-vista', () => {
  describe('pesos', () => {
    it('como en el boceto: KB, MB enteros, MB con decimal por debajo de 10 y GB con decimal', () => {
      expect(peso(248 * 1024)).toBe('248 KB');
      expect(peso(1.2 * MB)).toBe('1,2 MB');
      expect(peso(466 * MB)).toBe('466 MB');
      expect(peso(4.1 * 1024 * MB)).toBe('4,1 GB');
      expect(peso(0)).toBe('0 MB');
    });

    it('sin decimal cuando el decimal es cero', () => {
      expect(peso(2 * MB)).toBe('2 MB');
      expect(peso(1024 * MB)).toBe('1 GB');
    });

    it('resumen de la disciplina', () => {
      expect(resumenDisciplina({ codigo: 'T', nombre: 'T', bloques: new Array(11).fill(null), totales: { temarios: 11, videos: 40, bytes: 4.1 * 1024 * MB } }))
        .toBe('11 bloques · 40 vídeos · 4,1 GB');
      expect(resumenDisciplina({ codigo: 'T', nombre: 'T', bloques: new Array(1).fill(null), totales: { temarios: 0, videos: 1, bytes: 58 * MB } }))
        .toBe('1 bloque · 1 vídeo · 58 MB');
    });

    it('«N vídeos» o «Sin vídeos»', () => {
      expect(textoVideos(0)).toBe('Sin vídeos');
      expect(textoVideos(1)).toBe('1 vídeo');
      expect(textoVideos(7)).toBe('7 vídeos');
    });
  });

  describe('progreso', () => {
    it('porcentaje acotado y sin dividir por cero', () => {
      expect(porcentaje(88 * MB, 142 * MB)).toBe(62);
      expect(porcentaje(5, 0)).toBe(0);
      expect(porcentaje(10, 5)).toBe(100);
    });

    it('frase del boceto en escritorio y la corta en móvil', () => {
      expect(textoProgreso(88 * MB, 142 * MB, false)).toBe('62 % · 88 de 142 MB · No cierres esta pantalla hasta que termine.');
      expect(textoProgreso(88 * MB, 142 * MB, true)).toBe('62 % · 88 de 142 MB. No cierres esta pantalla.');
    });
  });

  describe('grados', () => {
    const blanco = { codigo: 'BLANCO', nombre: 'BLANCO' };
    const blancoAmarillo = { codigo: 'BLANCO_AMARILLO', nombre: 'BLANCO-AMARILLO' };

    it('primero y último, capitalizados', () => {
      expect(textoGrados([blanco, blancoAmarillo])).toBe('Alumnos de Blanco a Blanco-amarillo');
    });

    it('un solo grado', () => {
      expect(textoGrados([{ codigo: 'ROJO', nombre: 'ROJO' }])).toBe('Alumnos de Rojo');
    });

    it('conserva DAN y PUM en mayúsculas', () => {
      expect(textoGrados([{ codigo: 'NEGRO_1_DAN', nombre: 'NEGRO 1º DAN' }])).toBe('Alumnos de Negro 1º DAN');
      expect(textoGrados([{ codigo: 'ROJO_NEGRO_1_PUM', nombre: 'ROJO-NEGRO 1º PUM' }])).toBe('Alumnos de Rojo-negro 1º PUM');
    });

    describe('bloques de negros: de DAN a DAN', () => {
      const pum = [
        { codigo: 'ROJO_NEGRO_1_PUM', nombre: 'ROJO-NEGRO 1º PUM' },
        { codigo: 'NEGRO_1_DAN', nombre: 'NEGRO 1º DAN' },
      ];
      const ids = ['b07_negro_1_a_negro_2', 'b08_negro_2_a_negro_3', 'b09_negro_3_a_negro_4', 'b10_negro_4_a_negro_5'];
      for (const [i, id] of ids.entries()) {
        it(`${id}, con y sin catálogo`, () => {
          const esperado = `Alumnos de Negro ${i + 1}º DAN a Negro ${i + 2}º DAN`;
          expect(textoGrados(pum, id)).toBe(esperado);
          const catalogo = (c: string) => ({ nombre: c.replace(/^NEGRO_(\d)_DAN$/, 'NEGRO $1º DAN') });
          expect(textoGrados(pum, id, catalogo)).toBe(esperado);
        });
      }

      it('b11 sigue en Negro 5º DAN y un bloque de color no cambia', () => {
        expect(textoGrados([{ codigo: 'NEGRO_5_DAN', nombre: 'NEGRO 5º DAN' }], 'b11_negro_5_a_maximo')).toBe('Alumnos de Negro 5º DAN');
        expect(textoGrados([blanco, blancoAmarillo], 'b01_inicio_a_amarillo')).toBe('Alumnos de Blanco a Blanco-amarillo');
      });
    });

    describe('bloque de rojo a negro: del rojo a Negro 1º DAN', () => {
      const rojo = [{ codigo: 'ROJO', nombre: 'ROJO' }];
      const negro1 = { codigo: 'NEGRO_1_DAN', nombre: 'NEGRO 1º DAN' };

      it('con catálogo de taekwondo', () => {
        const buscar = (c: string) => (c === 'ROJO' ? { nombre: 'ROJO' } : c === 'NEGRO_1_DAN' ? { nombre: 'NEGRO 1º DAN' } : undefined);
        expect(textoGrados([rojo[0], negro1], 'b06_rojo_a_negro_1', buscar)).toBe('Alumnos de Rojo a Negro 1º DAN');
      });

      it('con catálogo de kickboxing, el rojo es marrón', () => {
        const buscar = (c: string) => (c === 'ROJO' ? { nombre: 'MARRÓN' } : c === 'NEGRO_1_DAN' ? { nombre: 'NEGRO 1º DAN' } : undefined);
        expect(textoGrados([{ codigo: 'MARRON', nombre: 'MARRÓN' }, negro1], 'b06_rojo_a_negro_1', buscar)).toBe('Alumnos de Marrón a Negro 1º DAN');
      });

      it('sin catálogo, toma el primer grado del bloque', () => {
        expect(textoGrados([{ codigo: 'ROJO', nombre: 'ROJO' }], 'b06_rojo_a_negro_1')).toBe('Alumnos de Rojo a Negro 1º DAN');
      });
    });

    it('vacío sin grados', () => {
      expect(textoGrados([])).toBe('');
      expect(fraseGrados([])).toBe('');
    });

    it('frase del bloque, en minúscula dentro de la oración', () => {
      expect(fraseGrados([blanco, blancoAmarillo])).toBe('Lo ven las familias de alumnos de blanco a blanco-amarillo.');
    });

    it('cintos: el primer color del primer grado y el último del último', () => {
      const catalogo: Record<string, { color1: string; color2: string | null }> = {
        BLANCO: { color1: '#FFFFFF', color2: null },
        BLANCO_AMARILLO: { color1: '#FFFFFF', color2: '#FFFF00' },
      };
      expect(coloresBloque([blanco, blancoAmarillo], (c) => catalogo[c])).toEqual(['#FFFFFF', '#FFFF00']);
      expect(coloresBloque([blanco], (c) => catalogo[c])).toEqual(['#FFFFFF', '#FFFFFF']);
    });

    it('cintos: sin grado en el catálogo, gris y no revienta', () => {
      const [uno, dos] = coloresBloque([{ codigo: 'X', nombre: 'X' }], () => undefined);
      expect(uno).toBeTruthy();
      expect(dos).toBe(uno);
    });
  });

  describe('cintos de la transición del bloque', () => {
    const buscar = (disciplina: string) => (codigo: string) => {
      const id = CATALOGO_TAEMOI.disciplinas.find((d) => d.codigo === disciplina)?.sistemaGradosId;
      return CATALOGO_TAEMOI.sistemasGrados.find((s) => s.id === id)?.grados.find((g) => g.codigo === codigo);
    };
    const rojo = [{ codigo: 'ROJO', nombre: 'ROJO' }];

    for (const disciplina of ['TAEKWONDO', 'KICKBOXING']) {
      it(disciplina + ': el primer bloque va de blanco a amarillo', () => {
        expect(coloresBloque([{ codigo: 'BLANCO', nombre: 'BLANCO' }], buscar(disciplina), 'b01_inicio_a_amarillo', disciplina))
          .toEqual(['#FFFFFF', '#FFFF00']);
      });

      it(disciplina + ': usa el destino del bloque, no el último grado al que llega', () => {
        expect(coloresBloque(rojo, buscar(disciplina), 'b06_rojo_a_negro_1', disciplina))
          .toEqual([disciplina === 'KICKBOXING' ? '#8B4513' : '#FF0000', '#000000']);
      });

      for (let dan = 1; dan <= 4; dan++) {
        it(disciplina + ': los dos cintos son negros del ' + dan + 'º al ' + (dan + 1) + 'º DAN', () => {
          const grados = [
            ...(dan <= 3 ? [{ codigo: 'ROJO_NEGRO_' + dan + '_PUM', nombre: 'ROJO-NEGRO ' + dan + 'º PUM' }] : []),
            { codigo: 'NEGRO_' + dan + '_DAN', nombre: 'NEGRO ' + dan + 'º DAN' },
          ];
          const id = 'b' + String(dan + 6).padStart(2, '0') + '_negro_' + dan + '_a_negro_' + (dan + 1);
          expect(coloresBloque(grados, buscar(disciplina), id, disciplina)).toEqual(['#000000', '#000000']);
        });
      }

      it(disciplina + ': negro desde el 5º DAN y en las futuras transiciones numeradas', () => {
        expect(coloresBloque([], buscar(disciplina), 'b11_negro_5_a_maximo', disciplina)).toEqual(['#000000', '#000000']);
        expect(coloresBloque([], buscar(disciplina), 'b12_negro_6_a_negro_7', disciplina)).toEqual(['#000000', '#000000']);
      });

      const intermedios = [
        ['b02_amarillo_a_naranja', '#FFFF00', '#FFA500'],
        ['b03_naranja_a_verde', '#FFA500', '#008000'],
        ['b04_verde_a_azul', '#008000', '#0000FF'],
        ['b05_azul_a_rojo', '#0000FF', disciplina === 'KICKBOXING' ? '#8B4513' : '#FF0000'],
      ];
      for (const [id, origen, destino] of intermedios) {
        it(disciplina + ': conserva la transición intermedia ' + id, () => {
          expect(coloresBloque([], buscar(disciplina), id, disciplina)).toEqual([origen, destino]);
        });
      }
    }

    it('Kickboxing: el código ROJO usa el nombre y el color marrón del catálogo', () => {
      expect(buscar('KICKBOXING')('ROJO')?.nombre).toBe('MARRÓN');
      expect(buscar('KICKBOXING')('ROJO')?.color1).toBe('#8B4513');
      expect(rojo).toEqual([{ codigo: 'ROJO', nombre: 'ROJO' }]);
    });

    it('no reinterpreta otras disciplinas ni identificadores de bloque desconocidos', () => {
      expect(coloresBloque(rojo, buscar('TAEKWONDO'), 'custom', 'TAEKWONDO')).toEqual(['#FF0000', '#FF0000']);
      expect(coloresBloque(rojo, buscar('TAEKWONDO'), 'b06_rojo_a_negro_1', 'PILATES')).toEqual(['#FF0000', '#FF0000']);
    });
  });

  describe('tipoFichero', () => {
    it('la extensión en mayúsculas', () => {
      expect(tipoFichero('01_taeguk.mp4')).toBe('MP4');
      expect(tipoFichero('sin-extension')).toBe('');
    });
  });

  describe('motivoVideo (antes de enviar: la API corta sin leer el cuerpo)', () => {
    it('vale un vídeo de extensión admitida, en mayúsculas o minúsculas', () => {
      expect(motivoVideo(fichero('a.mp4', 10))).toBeNull();
      expect(motivoVideo(fichero('a.MOV', 10))).toBeNull();
      expect(motivoVideo(fichero('a.webm', 500 * MB))).toBeNull();
    });

    it('rechaza otra extensión con el texto de la API', () => {
      expect(motivoVideo(fichero('a.avi', 10))).toBe('Solo se admiten vídeos mp4, webm, m4v o mov.');
      expect(motivoVideo(fichero('sin-extension', 10))).toBe('Solo se admiten vídeos mp4, webm, m4v o mov.');
    });

    it('rechaza lo que pasa de 500 MB con el texto del boceto', () => {
      expect(motivoVideo(fichero('a.mov', 612 * MB))).toBe('Pesa 612 MB. El máximo es 500 MB por vídeo.');
    });

    it('rechaza un fichero vacío', () => {
      expect(motivoVideo(fichero('a.mp4', 0))).toBe('El fichero está vacío.');
    });

    it('al sustituir, la cabecera debe servir para el nombre guardado: webm con webm y el resto entre sí', () => {
      expect(motivoVideo(fichero('a.mov', 10), 'viejo.mp4')).toBeNull();
      expect(motivoVideo(fichero('a.webm', 10), 'viejo.mp4')).toBe('Este vídeo solo se puede sustituir por otro MP4, M4V o MOV.');
      expect(motivoVideo(fichero('a.mp4', 10), 'viejo.webm')).toBe('Este vídeo solo se puede sustituir por otro WEBM.');
    });
  });

  describe('motivoDocumentoSustituto', () => {
    it('la extensión del guardado o una equivalente', () => {
      expect(motivoDocumentoSustituto('normas.pdf', 'nuevo.pdf')).toBeNull();
      expect(motivoDocumentoSustituto('foto.jpg', 'nueva.jpeg')).toBeNull();
      expect(motivoDocumentoSustituto('normas.pdf', 'nuevo.docx')).toBe('Este documento solo se puede sustituir por otro PDF.');
    });
  });

  describe('motivoFalloSubida', () => {
    const corte = new HttpErrorResponse({ status: 0 });

    it('corte de conexión a mitad: frase del boceto y reintentable', () => {
      expect(motivoFalloSubida(corte, 88 * MB)).toEqual({ motivo: 'Se cortó la conexión a los 88 MB. Vuelve a subirlo.', reintentable: true });
    });

    it('sin un byte enviado, puede ser Android que no pudo leer el fichero: hay que volver a elegirlo', () => {
      expect(motivoFalloSubida(corte, 0)).toEqual({ motivo: MOTIVO_LECTURA, reintentable: true });
    });

    it('el mensaje de la API; un 4xx no se reintenta', () => {
      const e = new HttpErrorResponse({ status: 415, error: { mensaje: 'El fichero no es un vídeo válido.' } });
      expect(motivoFalloSubida(e, 10)).toEqual({ motivo: 'El fichero no es un vídeo válido.', reintentable: false });
    });

    it('401 con su mensaje y reintentable', () => {
      const e = new HttpErrorResponse({ status: 401, error: { mensaje: 'Sesión caducada.' } });
      expect(motivoFalloSubida(e, 10)).toEqual({ motivo: 'Sesión caducada.', reintentable: true });
    });

    it('5xx sin cuerpo útil: texto genérico y reintentable', () => {
      const e = new HttpErrorResponse({ status: 502, error: '<html>' });
      expect(motivoFalloSubida(e, 10)).toEqual({ motivo: 'No se pudo subir el vídeo.', reintentable: true });
    });
  });

  describe('orden', () => {
    it('mismoOrden compara por fichero', () => {
      expect(mismoOrden([video('a'), video('b')], [video('a'), video('b')])).toBeTrue();
      expect(mismoOrden([video('b'), video('a')], [video('a'), video('b')])).toBeFalse();
    });

    it('fusionarOrden conserva el orden local, quita lo borrado y añade lo nuevo al final con los datos del servidor', () => {
      const locales = [video('c'), video('a'), video('b')];
      const nuevoA = { ...video('a'), titulo: 'Nuevo título' };
      const servidor = [nuevoA, video('c'), video('d')];
      expect(fusionarOrden(locales, servidor).map((v) => v.fichero)).toEqual(['c', 'a', 'd']);
      expect(fusionarOrden(locales, servidor)[1].titulo).toBe('Nuevo título');
    });
  });
});
