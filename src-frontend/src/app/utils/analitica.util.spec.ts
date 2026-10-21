import { urlSinToken } from './analitica.util';

/** La URL que ve Google Analytics nunca lleva el `token` de los enlaces de contraseña o invitación. */
describe('urlSinToken', () => {
  const base = 'https://moiskimdo.es';

  it('quita el token y deja la URL sin «?» cuando era el único parámetro', () => {
    expect(urlSinToken(`${base}/reset-password?token=SECRETO1`)).toBe(`${base}/reset-password`);
  });

  it('quita el token y conserva los demás parámetros en su orden', () => {
    expect(urlSinToken(`${base}/crear-contrasena?utm_source=correo&token=SECRETO1&a=b`)).toBe(
      `${base}/crear-contrasena?utm_source=correo&a=b`
    );
  });

  it('quita también un token repetido o vacío', () => {
    expect(urlSinToken(`${base}/reset-password?token=A&x=1&token=B`)).toBe(`${base}/reset-password?x=1`);
    expect(urlSinToken(`${base}/reset-password?token=`)).toBe(`${base}/reset-password`);
  });

  it('sin token deja la URL igual', () => {
    expect(urlSinToken(`${base}/horarios?dia=lunes`)).toBe(`${base}/horarios?dia=lunes`);
    expect(urlSinToken(`${base}/`)).toBe(`${base}/`);
  });

  it('conserva el fragmento', () => {
    expect(urlSinToken(`${base}/reset-password?token=SECRETO1#formulario`)).toBe(`${base}/reset-password#formulario`);
    expect(urlSinToken(`${base}/horarios?dia=lunes&token=X#tabla`)).toBe(`${base}/horarios?dia=lunes#tabla`);
  });

  it('no confunde otros parámetros que contienen «token» en el nombre', () => {
    expect(urlSinToken(`${base}/x?mitoken=1&token=S`)).toBe(`${base}/x?mitoken=1`);
  });

  it('si no puede leer la URL la devuelve sin parámetros ni fragmento', () => {
    expect(urlSinToken('/reset-password?token=SECRETO1#a')).toBe('/reset-password');
    expect(urlSinToken('')).toBe('');
  });
});
