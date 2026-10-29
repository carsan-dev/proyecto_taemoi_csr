// Lo que daba la pantalla anterior (retirada en 8b-Z), grabado con ella antes de borrarla. Generado: no editar a mano.
import { ValoresDeHoy } from '../../testing/oraculo-hoy';

export const HOY: ValoresDeHoy = {
  "ProductoFormularioComponent (8b-3g, crear y editar producto del panel nuevo) crear: el MISMO POST /productos de hoy, campo a campo (precio es-ES «1.010,50» = hoy «1010.50»)": [
    {"metodo":"POST","url":"http://localhost:8080/api/productos","credenciales":true,"cuerpo":"{\"concepto\":\"Viaje al campeonato\",\"precio\":1010.5,\"vendibleProfesor\":false}"},
    {"metodo":"POST","url":"http://localhost:8080/api/productos","credenciales":true,"cuerpo":"{\"concepto\":\"Camiseta del club\",\"precio\":12.5,\"vendibleProfesor\":true}"},
    {"metodo":"POST","url":"http://localhost:8080/api/productos","credenciales":true,"cuerpo":"{\"concepto\":\"Cuota\",\"precio\":25,\"vendibleProfesor\":false}"},
    {"metodo":"POST","url":"http://localhost:8080/api/productos","credenciales":true,"cuerpo":"{\"concepto\":\"Seguro\",\"precio\":1.5,\"vendibleProfesor\":true}"},
    {"metodo":"POST","url":"http://localhost:8080/api/productos","credenciales":true,"cuerpo":"{\"concepto\":\"Gratis\",\"precio\":0,\"vendibleProfesor\":false}"},
  ],
  "ProductoFormularioComponent (8b-3g, crear y editar producto del panel nuevo) editar: el MISMO PUT /productos/{id} de hoy, campo a campo, sin tocar nada y cambiando cada campo": [
    {"metodo":"PUT","url":"http://localhost:8080/api/productos/7","credenciales":true,"cuerpo":"{\"concepto\":\"Viaje al campeonato\",\"precio\":1010.5,\"vendibleProfesor\":true}"},
    {"metodo":"PUT","url":"http://localhost:8080/api/productos/7","credenciales":true,"cuerpo":"{\"concepto\":\"Viaje al nacional\",\"precio\":1010.5,\"vendibleProfesor\":true}"},
    {"metodo":"PUT","url":"http://localhost:8080/api/productos/7","credenciales":true,"cuerpo":"{\"concepto\":\"Viaje al campeonato\",\"precio\":999.99,\"vendibleProfesor\":true}"},
    {"metodo":"PUT","url":"http://localhost:8080/api/productos/7","credenciales":true,"cuerpo":"{\"concepto\":\"Viaje al campeonato\",\"precio\":1234567.89,\"vendibleProfesor\":true}"},
    {"metodo":"PUT","url":"http://localhost:8080/api/productos/7","credenciales":true,"cuerpo":"{\"concepto\":\"Viaje al campeonato\",\"precio\":1010.5,\"vendibleProfesor\":false}"},
    {"metodo":"PUT","url":"http://localhost:8080/api/productos/7","credenciales":true,"cuerpo":"{\"concepto\":\"Viaje\",\"precio\":0,\"vendibleProfesor\":false}"},
  ],
};
