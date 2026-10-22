# Verificación de la actualización de la documentación de patrones (task 6.4)

> La documentación vive en la **raíz del workspace**, que no es un repositorio
> Git: `docs/` no está versionado. Este archivo deja constancia de lo
> actualizado, para que el cambio sea trazable desde el repositorio.

## `docs/doc-design-patterns.md` §6 — Patrón Outbox

**Añadido:**

1. **La publicación es una fase separada de la transacción de negocio.** El
   diagrama de secuencia muestra que el relay reclama los mensajes pendientes
   (`FOR UPDATE SKIP LOCKED`), que esa transacción se cierra, y que la
   publicación ocurre después. Se explica por qué: publicar dentro de la
   transacción que confirmó el cambio de negocio es exactamente lo que el
   patrón evita, porque el bus podría recibir un evento que la base de datos
   no llegó a confirmar.

2. **El transporte sigue siendo el `EventPublisherPort`, y hoy ese puerto solo
   escribe un log.** Se dice sin rodeos que la entrega al bus **no es real**:
   no hay Kafka en el despliegue actual, y la sustitución del puerto por un
   transporte efectivo pertenece al change de publicación de eventos de
   dominio. El patrón está **construido pero vacío**: la bandeja, el relay, la
   atomicidad y la cola de muertos son reales; el bus, no.

3. **El estado del mensaje es explícito** (`PENDING`, `PUBLISHED`,
   `DEAD_LETTER`), con contador de intentos, fecha del próximo reintento y
   último error. El `event_id` viaja con el mensaje y **no se regenera en cada
   reintento**, que es lo que hace posible la deduplicación por el
   consumidor.

4. **Un mensaje fallido no detiene el lote**, el fallo queda registrado con su
   error y su número de intento, la espera es creciente, y al agotar los
   intentos el mensaje pasa a una cola de muertos que **se conserva** y desde
   la que se puede reactivar.

5. **La demarcación de la escritura del evento es del caso de uso, no del
   adaptador**, y el relay es la excepción controlada: la lectura con
   reclamación de la fila necesita una transacción escribible, y la abre el
   relay, en la capa de aplicación.

## `docs/doc-microservices.md` §4 — Estrategia de comunicación y consistencia

**Añadido** a la viñeta de comunicación asíncrona: la publicación es una fase
separada de la transacción de negocio, con la misma explicación del riesgo
que se evita, y la constatación de que el transporte sigue siendo el
`EventPublisherPort` y de que, mientras no se sustituya por Kafka, la entrega
al bus no es real.

## Verificación exigida por la task

> *Verificar que la descripción del patrón en la documentación no afirma que la
> entrega al bus es real mientras el transporte siga siendo un registro en el
> log.*

```
$ grep -n "publicación es una fase separada" docs/doc-microservices.md docs/doc-design-patterns.md
docs/doc-microservices.md:131:  … La **publicación es una fase separada de la transacción de negocio** …
docs/doc-design-patterns.md:100: **La publicación es una fase separada de la transacción de negocio.** …
```

Ninguna de las dos afirmaciones presenta la entrega al bus como real: las dos
indican expresamente que el transporte sigue siendo el `EventPublisherPort` y
que el bus todavía no existe.
