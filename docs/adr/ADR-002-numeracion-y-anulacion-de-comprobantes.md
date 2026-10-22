# ADR-002 — Quién posee la numeración de comprobantes y quién ejecuta la anulación

- **Estado:** Aceptado
- **Fecha:** 2026-10-21
- **Change:** `fix-provider-integration`
- **Ámbito:** `erp-integration-hub` y, en el futuro, `billing-service`
- **Copia canónica:** `erp-integration-hub/docs/adr/ADR-002-numeracion-y-anulacion-de-comprobantes.md` (este repositorio; los demás guardan un espejo)

## Contexto

`doc-microservices.md` §3.8 asigna la emisión de comprobantes al
`billing-service`, con el hub como canal. El código, sin embargo, concentra
TODO el ciclo de vida fiscal en el hub: la emisión, la consulta de estado, la
anulación, la numeración y el estado de la cola de reintentos.

De ese reparto sederivan dos decisiones que el código no tenía explícitas:

1. **Quién posee la numeración** (taller, serie, número). Es la identidad
   fiscal del comprobante. Mientras nadie la reservase, dos emisiones
   simultaneas de la misma serie y número llegaban las dos a SUNAT, y la
   autoridad fiscal veía un duplicado. La comprobación se hace ahora ANTES
   de contactar al proveedor, mediante una reserva de la numeración.

2. **Quién ejecuta la anulación**. Es la operación que retira un comprobante
   ya emitido, y la que exige el **ticket de comunicación** del documento.
   El código la ejecutaba el hub, pero sin obtener ese ticket: la solicitud
   se rechazaba, y el motivo del rechazo no decía nada del problema real.

Además, el propio `doc-microservices.md` señalaba la ambigüedad: *«debe
formalizarse en un ADR antes de construir el `billing-service` (que es el que
lo consume según `doc-microservices.md:73`)»*.

## Decisión

**La numeración de comprobantes la posee el hub de integraciones**, y se
reserva antes de emitir. **La anulación también la ejecuta el hub**, obteniendo
el ticket de comunicación del documento y reenviándolo en la solicitud.

Mientras el `billing-service` no exista, el hub es el único que tiene el
ciclo de vida fiscal completo, y partirlo en dos servicios sin el contrato
de numeración duplicaría la lógica fiscal en dos sitios.

Cuando se construya el `billing-service`, este ADR debe ser sustituido por
otro que lo anule, y la numeración debe migrar a quien posea el estado
fiscal.

## Motivo

La numeración es una **identidad**, no un dato. Si dos servicios la
reservaran por su cuenta, ambos podrían emitir con la misma serie y número, y
la duplicación se detectaría en SUNAT, que es el peor sitio posible para
detectarla. Reservarla en un único sitio, antes de contactar al proveedor,
convierte la duplicación en un error local y explícito.

La anulación tiene la misma naturaleza: necesita el documento emitido, y el
documento emitido es el que tiene el hub. Requerir el ticket de comunicación
es parte del protocolo de la autoridad fiscal, no una decisión de diseño.

La alternativa —el `billing-service` desde el principio— exige construir dos
servicios, un contrato entre ellos y una migración de un ciclo de vida que
hoy vive entero en un sitio. El coste no se corresponde con el beneficio.

## Alternativas descartadas

- **El `billing-service` posee la numeración desde el principio.** Se
  descartó porque el `billing-service` todavía no existe, y porque
  construirlo ahora sin el resto de los microservicios del taller produciría
  un servicio aislado, con su propio despliegue y su propia base de datos,
  sin más función que la de un par de pantallas. La numeración es la parte
  del ciclo fiscal que más se parece a un problema de concurrencia, y la
  resuelve mejor un único propietario.
- **El hub pide el número al `billing-service` en cada emisión.** Se
  descartó porque convierte cada emisión en dos llamadas sincrónicas, con la
  ventana de carrera entre la reserva y la emisión. La reserva se hace junto
  a la emisión, en el mismo sitio.
- **Numerar en el cliente (la aplicación de taller).** Se descartó porque el
  cliente no puede garantizar la unicidad: dos sucursales del mismo taller
  numeran a la vez, y el numero se duplica. La numeración es un servicio del
  servidor.
- **Dejar la numeración como está, sin reserva.** Se descartó porque es el
  defecto que este change corrige: el duplicado fiscal llega a SUNAT, y desde
  ahí no hay vuelta atrás.

## Consecuencias

**Positivas:**

- La unicidad de (taller, serie, número) está garantizada por la clave
  primaria de la tabla de numeración, y la carrera entre dos emisiones
  simultáneas la resuelve el almacén de datos.
- La anulación incluye el ticket de comunicación, de modo que la autoridad
  fiscal puede aceptarla.
- La responsabilidad queda enunciada de forma inequívoca, que es lo que
  `doc-microservices.md` pedía.

**Negativas:**

- El hub acumula responsabilidades fiscales, y con ellas la dependencia de
  la autoridad fiscal. La tenía, pero ahora está enunciada: es una
  deuda conocida y no una sorpresa.
- La numeración requiere una tabla propia, que hay que mantener y cuyo
  crecimiento hay que vigilar.
- Si el `billing-service` se construye, este ADR hay que anularlo, y la
  numeración hay que migrar. La reserva existente facilitate esa migración:
  la tabla es el punto de partida obvious.

**Neutrales:**

- La anulación rechazada de forma transitoria se reprograma, y la rechazada
  de forma definitiva se devuelve al cliente. Lo que se reprograma no es la
  anulación: es la operación de baja, que el hub reintenta.

## Alcance de la decisión posterior

Cuando se construya el `billing-service`, este registro deberá ser
sustituido por otro que lo anule, y la decisión de `doc-microservices.md`
§3.8 —que asigna la emisión al `billing-service`— deberá ser revisada. La
migración de la numeración tendrá que cubrir, como mínimo:

1. Quién reserva la numeración, y con qué clave de unicidad.
2. Qué pasa con las reservas en curso en el momento del corte.
3. Cómo se comunica el ciclo de anulación entre los dos servicios.
