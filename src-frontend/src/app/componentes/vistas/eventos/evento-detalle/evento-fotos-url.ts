/** Devuelve la URL de una foto con el ancho pedido (`?w=`), sustituyendo el que ya tuviera. */
export function urlFotoConAncho(url: string, ancho: number): string {
  const valor = String(Math.max(1, Math.floor(ancho)));
  if (/([?&])w=[^&]*/.test(url)) {
    return url.replace(/([?&])w=[^&]*/, `$1w=${valor}`);
  }
  return `${url}${url.includes('?') ? '&' : '?'}w=${valor}`;
}

/** «1 foto» / «24 fotos». */
export function textoNumeroFotos(total: number): string {
  return total === 1 ? '1 foto' : `${total} fotos`;
}
