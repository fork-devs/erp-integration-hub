-- =============================================================================
-- ERP INTEGRATION HUB - V6: ELIMINAR LA CONFIGURACION DE PROVEEDOR
-- =============================================================================
-- La tabla provider_configs estaba muerta: su adapter no lo usaba nadie, y
-- la configuracion real de cada proveedor (URL base, credencial, tiempo de
-- espera) vive en la configuracion de la aplicacion, no en la base de datos.
-- La tabla daba la sensacion de que la configuracion era editable en
-- caliente, cuando no lo era: nadie la leia.
--
-- Se elimina la tabla y su semilla. Los proveedores reales son los que
-- existen hoy: SUNAT (comprobantes), Stripe (pagos), Chequea.pe (RUC) y
-- SUNARP (placas).
--
-- Ver docs/doc-estado-implementacion.md, hallazgo P1 7.35.
-- =============================================================================

DROP TABLE IF EXISTS provider_configs;
