# db/resync

`resync_catalogo_v24.sql` está **obsoleto desde V27**.

- **No ejecutarlo a mano nunca.** Su `UPDATE` que pone `tarifa_id = NULL` no está limitado por organización y
  borraría las tarifas con código propio de otras escuelas.
- **No modificarlo ni borrarlo.** Se conserva solo porque V24, V26 y V27 incluyen su texto idéntico y los
  tests comprueban esa identidad (`ResyncCatalogoV24Test`, `SwitchGradosMigracionIntegrationTest`,
  `TarifasProductosMigracionIntegrationTest`, `ResyncProductosDePrueba`).
- Desde V28 ninguna migración lo incluye.
