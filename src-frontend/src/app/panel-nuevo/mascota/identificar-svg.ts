/**
 * Hace locales a una instancia los IDs de un SVG inline: `id`, `url(#…)`, `href`, `aria-*` y los selectores `#id` del
 * `<style>` (solo los IDs que el SVG declara: los colores `#e3130a` no se tocan).
 */
export function identificarSvg(svg: string, prefijo: string): string {
  const ids = Array.from(svg.matchAll(/\bid="([^"]+)"/g), (m) => m[1]);
  const conEstilos = svg.replace(/(<style\b[^>]*>)([\s\S]*?)(<\/style>)/g, (_, abre: string, css: string, cierra: string) => {
    let reescrito = css;
    for (const id of ids) {
      const literal = id.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      reescrito = reescrito.replace(new RegExp(`#${literal}(?![\\w-])`, 'g'), `#${prefijo}${id}`);
    }
    return abre + reescrito + cierra;
  });
  return conEstilos
    .replace(/\bid="([^"]+)"/g, (_, id: string) => `id="${prefijo}${id}"`)
    .replace(/url\(#([^)]+)\)/g, (_, id: string) => `url(#${prefijo}${id})`)
    .replace(/\b(href|xlink:href)="#([^"]+)"/g, (_, atributo: string, id: string) => `${atributo}="#${prefijo}${id}"`)
    .replace(/\b(aria-labelledby|aria-describedby)="([^"]+)"/g, (_, atributo: string, lista: string) =>
      `${atributo}="${lista.split(/\s+/).map((id) => prefijo + id).join(' ')}"`);
}
