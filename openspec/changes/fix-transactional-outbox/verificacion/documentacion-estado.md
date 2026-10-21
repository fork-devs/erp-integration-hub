# Verificación de la actualización del reporte de estado (task 6.3)

> La documentación vive en la **raíz del workspace**, que no es un repositorio
> Git: `docs/` no está versionado. Este archivo deja constancia de lo
> actualizado, para que el cambio sea trazable desde el repositorio.

**Fichero actualizado:** `docs/doc-estado-implementacion.md`

## Hallazgos marcados como cerrados

| Hallazgo | Estado registrado |
|:---|:---|
| P0 5.3 — El Transactional Outbox no es atómico | ✅ CERRADO por `fix-transactional-outbox` (tasks 2.4, 2.5, 2.6) |
| P0 5.8 — Ninguna operación de negocio tiene demarcación transaccional | ✅ CERRADO por `fix-transactional-outbox` (tasks 2.1–2.6, 4.1–4.5, 5.1–5.4) |
| P1 6.2 — `erp-integration-hub` no tiene ni una sola transacción | ✅ CERRADO por `fix-transactional-outbox` (tasks 5.1–5.4) |
| P1 6.8 — El relay de outbox tiene head-of-line blocking y traga las excepciones | ✅ CERRADO por `fix-transactional-outbox` (tasks 3.1–3.5) |
| P1 7.21 — No hay `EventPublisherPort` de Kafka, ni `event_id` en el outbox | ⚠️ **Parcialmente cerrado**: la columna `event_id` existe y es única (tasks 1.1–1.3); el `EventPublisherPort` de Kafka sigue sin existir |
| P2 7.40 — El hub no tiene demarcación transaccional en la capa de aplicación | ✅ CERRADO por `fix-transactional-outbox` (task 5.1) |

## Apartados actualizados

- **§1 bis** — pasa a recoger el estado de los dos changes aplicados. Se
  separan los cierres de `fix-tenant-isolation` (6) de los de
  `fix-transactional-outbox` (5), y se listan los **defectos adicionales**
  encontrados durante la implementación y corregidos: el casteo de
  `java.sql.Timestamp` a `Instant` que dejaba muerta la cola de reintentos
  del hub, la lectura de la fila pendiente sin reclamar en exclusiva, y el
  payload del evento construido con `String.format`, que producía JSON
  inválido.
- **§1 (Semáforo)** — el recuento se etiqueta como *estado original* y se
  remite al apartado 1 bis como estado vigente. La familia «Atomicidad
  transaccional ausente» aparece como cerrada.
- **§8 (Cobertura de los ASR)** — QA-3 y QA-8 se actualizan: el RPO≠0 por
  pérdida de evento queda corregido, y la suite ya corre contra PostgreSQL
  real embebido.
- **§9 (Verificación de build y tests)** — se distinguen el estado vigente
  (174 tests, 0 fallos) del original (71 tests).
- **§10 (Plan de remediación)** — las acciones 7, 8 y 12 aparecen como
  cumplidas.
- **§11 (Veredicto por servicio)** — los tres servicios con demarcación
  transaccional ya no se describen como carentes de ella.

## Verificación exigida por la task

> *Verificar que el documento no afirma que la demarcación transaccional sigue
> ausente en la capa de aplicación una vez corregida.*

Las únicas apariciones que la afirman son las **descripciones históricas del
hallazgo** (§5.8, §6.2), cada una de las cuales va precedida de su marca de
cerrado, y el **veredicto por servicio**, donde la afirmación aparece
acompañada de la nota de cierre. No queda ninguna afirmación sin matiz de que
la demarcación siga ausente.

Búsqueda de control:

```
$ grep -n "Cero \`@Transactional\`" docs/doc-estado-implementacion.md
(sin resultados)

$ grep -n "ninguna demarcación transaccional" docs/doc-estado-implementacion.md
1281:  🔴 **6 P0 (3 ya cerrados):** … y **ninguna demarcación transaccional en la capa de aplicación** (§5.8) …
      ✅ **5.1, 5.2 y 5.8 están cerrados** por `fix-tenant-isolation` (5.1 y 5.2) y
      `fix-transactional-outbox` (5.8) …
```
