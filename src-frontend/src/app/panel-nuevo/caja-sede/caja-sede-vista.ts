import {
  CalculoEntrega,
  DIAS_AVISO_EFECTIVO,
  centimos,
  diasDesde,
  etiquetaTipoMovimiento,
  textoRelativo,
} from '../../componentes/endpoints/caja-sede/caja-calculos';
import { CobroCaja, EntregaCaja, MovimientoCaja, MovimientoDeEntrega, PendienteCobrador, ResumenCajaSede } from '../../interfaces/caja';
import { fechaApi } from '../../utils/fecha-api';
import { eur, fecha, numero } from '../formato/formato-es';

/**
 * Reglas puras de la Caja de sede del panel nuevo (8b-3j, P2 «pestañas Por recibir, Entregas y Movimientos»). Textos,
 * criterios por rol y confirmaciones copiados de `CajaSedeComponent` (6b-3, 6c-7 y 6c-9); los cálculos de dinero
 * (`calcularEntrega`, `importeConSigno`, `reglaMovimiento`) no se copian: se usan los de hoy, de `caja-calculos`. Sin
 * dependencias de Angular para probarlas sin la pantalla.
 */

export type PestanaCaja = 'recibir' | 'entregas' | 'movimientos';

/** Las tres pestañas de P2, en su orden; abre en «Por recibir». */
export const PESTANAS: PestanaCaja[] = ['recibir', 'entregas', 'movimientos'];

const ETIQUETA_PESTANA: Record<PestanaCaja, string> = {
  recibir: 'Por recibir',
  entregas: 'Entregas',
  movimientos: 'Movimientos',
};

export function etiquetaPestana(pestana: PestanaCaja): string {
  return ETIQUETA_PESTANA[pestana];
}

/**
 * El número de cada pestaña: profesores con efectivo en mano, entregas en total (todas las páginas, el `totalElements`
 * de hoy) y movimientos del periodo. Sin número mientras no ha llegado la primera lectura.
 */
export function numeroPestana(
  pestana: PestanaCaja,
  datos: { pendientes: PendienteCobrador[] | null; totalEntregas: number | null; movimientos: MovimientoCaja[] | null }
): number | null {
  switch (pestana) {
    case 'recibir':
      return datos.pendientes?.length ?? null;
    case 'entregas':
      return datos.totalEntregas;
    default:
      return datos.movimientos?.length ?? null;
  }
}

// ------------------------------------------------------------------ periodo y sede

/** La de hoy (decisión 206): la última sede elegida en este navegador, solo su id. El periodo no se recuerda. */
export const CLAVE_ULTIMA_SEDE = 'taemoi.cajaSede.ultimaSede';

const MESES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];
const MESES_EN_SELECTOR = 24;

export interface OpcionPeriodo {
  valor: string;
  etiqueta: string;
  ano: number;
  mes: number;
}

/** Los últimos meses, del actual hacia atrás («Octubre 2026»), como hoy. */
export function construirPeriodos(hoy: Date, cuantos: number = MESES_EN_SELECTOR): OpcionPeriodo[] {
  const periodos: OpcionPeriodo[] = [];
  for (let i = 0; i < cuantos; i++) {
    const f = new Date(hoy.getFullYear(), hoy.getMonth() - i, 1);
    const ano = f.getFullYear();
    const mes = f.getMonth() + 1;
    periodos.push({ valor: `${ano}-${String(mes).padStart(2, '0')}`, etiqueta: `${MESES[mes - 1]} ${ano}`, ano, mes });
  }
  return periodos;
}

// ------------------------------------------------------------------ importes

/**
 * Importe con signo explícito («+50,00 €», «−1,00 €» con el signo menos de verdad, «0,00 €»), como
 * `importeConSignoTexto` de hoy, con `eur`: el sentido no depende solo del color.
 */
export function firmado(importe: number | null | undefined): string {
  const cent = centimos(Number(importe ?? 0) || 0);
  const absoluto = eur(Math.abs(cent) / 100);
  return cent > 0 ? `+${absoluto}` : cent < 0 ? `−${absoluto}` : absoluto;
}

/** Entra (positivo) o sale (negativo) de la caja, en céntimos; nulo si es cero. */
export function tonoImporte(importe: number | null | undefined): 'entra' | 'sale' | null {
  const cent = centimos(Number(importe ?? 0) || 0);
  return cent > 0 ? 'entra' : cent < 0 ? 'sale' : null;
}

export interface CifraResumen {
  clave: 'saldo' | 'pendiente' | 'recibido' | 'diferencias';
  titulo: string;
  valor: string;
  detalle: string;
  /** Pendiente en ocre (se debe dinero a la sede); diferencias en peligro o bien según su signo, como hoy. */
  tono: 'atencion' | 'peligro' | 'bien' | null;
}

/** Las cuatro cifras del resumen con los textos de hoy; «—» mientras no hay resumen. */
export function cifrasResumen(resumen: ResumenCajaSede | null, periodo: string): CifraResumen[] {
  const cent = centimos(resumen?.diferenciasEnPeriodo ?? 0);
  return [
    { clave: 'saldo', titulo: 'Saldo de caja', valor: resumen ? eur(resumen.saldoCaja) : '—', detalle: 'Efectivo que debería haber en la sede', tono: null },
    {
      clave: 'pendiente', titulo: 'Pendiente de entregar', valor: resumen ? eur(resumen.pendienteDeEntregar) : '—',
      detalle: 'En mano de los profesores', tono: 'atencion',
    },
    { clave: 'recibido', titulo: 'Recibido este periodo', valor: resumen ? eur(resumen.recibidoEnPeriodo) : '—', detalle: periodo, tono: null },
    {
      clave: 'diferencias', titulo: 'Diferencias', valor: resumen ? firmado(resumen.diferenciasEnPeriodo) : '—',
      detalle: `Sobrantes y faltantes de ${periodo}`, tono: cent < 0 ? 'peligro' : cent > 0 ? 'bien' : null,
    },
  ];
}

// ------------------------------------------------------------------ entregas

/**
 * Diferencia que se enseña: contado − declarado, descontando la DEVOLUCION ACTIVA de la entrega (la devolución del
 * profesor, D6c-10, va en lugar del FALTANTE: entrega 25, cuenta 0 y devuelve 25, así que cuadra). La de hoy.
 */
export function diferenciaVista(entrega: EntregaCaja): number {
  const devuelto = (entrega.movimientosDeCobro ?? [])
    .filter((m) => m.tipo === 'DEVOLUCION' && m.estado === 'ACTIVO')
    .reduce((total, m) => total + m.importe, 0);
  return entrega.diferencia - devuelto;
}

/** «cuadra», «falta 1,00 €», «sobra 0,50 €», en céntimos como hoy. */
export function cuadre(diferencia: number): { estado: 'cuadra' | 'falta' | 'sobra'; texto: string } {
  const cent = Math.round(diferencia * 100);
  if (cent === 0) {
    return { estado: 'cuadra', texto: 'cuadra' };
  }
  return diferencia < 0 ? { estado: 'falta', texto: `falta ${eur(-diferencia)}` } : { estado: 'sobra', texto: `sobra ${eur(diferencia)}` };
}

/** 6c-9: los ERROR (193) y la DEVOLUCION (D6c-10) de la entrega, con el texto de hoy. */
export function textoMovimientoDeCobro(m: MovimientoDeEntrega): string {
  const signo = m.importe > 0 ? '+' : m.importe < 0 ? '−' : '';
  const importe = `${signo}${eur(Math.abs(m.importe))}`;
  switch (m.tipo) {
    case 'ERROR':
      return `Cobro deshecho ${importe}`;
    case 'DEVOLUCION':
      return `Devolución ${importe}`;
    default:
      return `${etiquetaTipoMovimiento(m.tipo)} ${importe}`;
  }
}

export type AccionAnular = 'anular' | 'pedir';

/** El propietario anula una entrega activa; la gestión pide al propietario que la anule (D6c-8). Como hoy. */
export function accionEntrega(entrega: EntregaCaja, esPropietario: boolean): AccionAnular | null {
  if (entrega.estado === 'ANULADA') {
    return null;
  }
  return esPropietario ? 'anular' : 'pedir';
}

/** Ídem con los movimientos sueltos; el automático de una entrega se anula (o se pide) anulando la entrega. */
export function accionMovimiento(movimiento: MovimientoCaja, esPropietario: boolean): AccionAnular | null {
  if (movimiento.estado === 'ANULADO' || movimiento.entregaCajaId !== null) {
    return null;
  }
  return esPropietario ? 'anular' : 'pedir';
}

/** El pie de la pestaña Entregas, con el texto de hoy. */
export function textoMostrando(mostradas: number, total: number): string {
  return `Mostrando ${numero(mostradas)} de ${numero(total)} entregas`;
}

// ------------------------------------------------------------------ por recibir

export function textoDesde(profesor: PendienteCobrador, hoy: Date = new Date()): string {
  return textoRelativo(diasDesde(profesor.cobroMasAntiguo, hoy));
}

/** Más de una semana con el dinero en mano: se avisa, como hoy. */
export function avisoEfectivo(profesor: PendienteCobrador, hoy: Date = new Date()): boolean {
  const dias = diasDesde(profesor.cobroMasAntiguo, hoy);
  return dias !== null && dias > DIAS_AVISO_EFECTIVO;
}

export function nombreAlumnoCobro(cobro: CobroCaja): string {
  return `${cobro.alumnoNombre ?? ''} ${cobro.alumnoApellidos ?? ''}`.trim() || '—';
}

/** Lo que dice el formulario bajo «Has contado» (los textos de hoy); nada si cuadra. */
export function avisoCuadre(calculo: CalculoEntrega): { tono: 'invalido' | 'falta' | 'sobra'; texto: string } | null {
  if (calculo.contado === null) {
    return { tono: 'invalido', texto: 'Escribe un importe válido, por ejemplo 45,50.' };
  }
  if (calculo.estado === 'falta') {
    return { tono: 'falta', texto: `Faltan ${eur(-calculo.diferencia)}. Se apuntará un faltante en la caja.` };
  }
  if (calculo.estado === 'sobra') {
    return { tono: 'sobra', texto: `Sobran ${eur(calculo.diferencia)}. Se apuntará un sobrante en la caja.` };
  }
  return null;
}

/** Hoy, tras un 409 al recibir (alguien cambió esos cobros): se cierra, se recarga y se explica qué hacer. */
export const TEXTO_ENTREGA_CAMBIADA =
  'Alguien ha registrado o cambiado estos cobros mientras tanto. Hemos recargado la caja: revisa lo pendiente y vuelve a recibir la entrega.';

// ------------------------------------------------------------------ confirmaciones

/** Máximo del motivo de anular y de pedir anular, como hoy. */
export const MAX_MOTIVO = 500;

export function mensajeAnularEntrega(entrega: EntregaCaja): string {
  return `Entrega de ${entrega.entregadoPorNombre ?? 'un profesor'} por ${eur(entrega.importeContado)}. Sus cobros vuelven a estar pendientes de entregar.`;
}

export function mensajeAnularMovimiento(movimiento: MovimientoCaja): string {
  return `${etiquetaTipoMovimiento(movimiento.tipo)} de ${eur(movimiento.importe)}. Deja de contar en la caja.`;
}

/** Los datos del diálogo «Pedir al propietario» de hoy; los vacíos no se pintan (como hoy). */
export function datosPedirEntrega(entrega: EntregaCaja): Array<[string, string]> {
  return [
    ['Entregó', entrega.entregadoPorNombre ?? ''],
    ['Recibió', entrega.recibidoPorNombre ?? ''],
    ['Contado', eur(entrega.importeContado)],
    ['Fecha', fecha(fechaApi(entrega.fecha))],
  ];
}

export function datosPedirMovimiento(movimiento: MovimientoCaja): Array<[string, string]> {
  return [
    ['Tipo', etiquetaTipoMovimiento(movimiento.tipo)],
    ['Importe', firmado(movimiento.importe)],
    ['Fecha', fecha(fechaApi(movimiento.fecha))],
    ['Nota', movimiento.nota ?? ''],
  ];
}

// ------------------------------------------------------------------ fechas

/** «12/10/2026 18:05» (el `dd/MM/yyyy HH:mm` de hoy). Vacío si no es una fecha. */
export function fechaHora(valor: string | null | undefined): string {
  const f = fechaApi(valor);
  if (!f) {
    return '';
  }
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${fecha(f)} ${dos(f.getHours())}:${dos(f.getMinutes())}`;
}

/** «12/10/2026». */
export function fechaDia(valor: string | null | undefined): string {
  return fecha(fechaApi(valor));
}
