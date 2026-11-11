import type { EventoDetalleWeb, EventosWeb, GrupoWeb, Plazas, SitioWeb } from '../datos/modelos';
import type { CasoVista } from './puente-editor';

/**
 * «Ver el caso» (W12c-bis, d528; `CASOS` del boceto `boceto-panel-contenido-web-comarcal.html`): cada caso poco frecuente
 * se simula cambiando en memoria los datos que ya tiene la vista previa de gestión, para que el tema pinte los textos
 * que solo salen en él. Vive en el chunk de la capa de edición; nunca llama a la API ni cambia lo guardado.
 */
export interface SimuladorCaso {
  sitio(sitio: SitioWeb): SitioWeb;
  eventos(eventos: EventosWeb): EventosWeb;
  evento(evento: EventoDetalleWeb): EventoDetalleWeb;
}

const igual = <T>(x: T) => x;

/** Todos los grupos (y sus turnos) con el mismo estado de plazas: «Grupo lleno» o «Lista de espera». */
function conPlazas(sitio: SitioWeb, estado: Plazas['estado']): SitioWeb {
  const plazas: Plazas = { estado, quedan: null };
  const grupo = (g: GrupoWeb): GrupoWeb => ({
    ...g,
    plazas: g.plazas ? plazas : null,
    turnos: g.turnos.map((t) => ({ ...t, plazas })),
  });
  return { ...sitio, sedes: sitio.sedes.map((s) => ({ ...s, grupos: s.grupos.map(grupo) })) };
}

function simulador(parcial: Partial<SimuladorCaso>): SimuladorCaso {
  return { sitio: igual, eventos: igual, evento: igual, ...parcial };
}

export function simuladorDe(caso: CasoVista): SimuladorCaso {
  switch (caso) {
    case 'lleno':
      return simulador({ sitio: (s) => conPlazas(s, 'LLENO') });
    case 'espera':
      return simulador({ sitio: (s) => conPlazas(s, 'LISTA_ESPERA') });
    case 'sin-tarifas':
      // «Aún no hay mensualidad en Tesorería»: sin tarifas publicadas.
      return simulador({ sitio: (s) => ({ ...s, tarifas: [], tarifasSede: [] }) });
    case 'sin-proximos':
      return simulador({ eventos: (e) => ({ ...e, proximos: [] }) });
    case 'sin-eventos':
      return simulador({ eventos: () => ({ proximos: [], anteriores: [] }) });
    case 'un-evento':
      return simulador({ eventos: (e) => ({ ...e, proximos: e.proximos.slice(0, 1) }) });
    case 'sin-anteriores':
      return simulador({ eventos: (e) => ({ ...e, anteriores: [] }) });
    case 'una-foto':
      return simulador({ evento: (e) => ({ ...e, fotos: e.fotos.slice(0, 1) }) });
  }
}

/** La parte de la página donde se ve cada caso (`PARTE_CASO` del boceto), para enseñarla al elegirlo. */
export const PARTE_DEL_CASO: Readonly<Record<CasoVista, readonly string[]>> = {
  lleno: ['horarios', 'sp-horarios'],
  espera: ['horarios', 'sp-horarios'],
  'sin-tarifas': ['tp-banda'],
  'sin-proximos': ['ep-proximos'],
  'sin-eventos': ['ep-proximos'],
  'un-evento': ['ep-proximos'],
  'sin-anteriores': ['ep-anteriores'],
  'una-foto': ['ev-galeria'],
};
