# Verificación de la documentación del change `fix-provider-integration` (tasks 9.3 y 9.4)

> La documentación vive en la **raíz del workspace**, que no es un repositorio
> Git: `docs/` no está versionado. Este archivo deja constancia de lo
> actualizado, para que el cambio sea trazable desde el repositorio.

## Task 9.4 — El ADR de la numeración y la anulación

**Fichero:** `docs/adr/ADR-002-numeracion-y-anulacion-de-comprobantes.md`

El registro sigue la convención del proyecto (escenario, decisión, motivo,
alternativas descartadas y consecuencias) y contiene:

- **Contexto:** la ambigüedad de `doc-microservices.md` §3.8, que señalaba que
  la decisión de la numeración «debe formalizarse en un ADR antes de
  construir el `billing-service`», y los dos defectos que la ambigüedad
  cubría: la numeración sin reservar y la anulación sin ticket.
- **Decisión:** el hub **posee la numeración** y **ejecuta la anulación**.
- **Motivo:** la numeración es una identidad, no un dato; y repartirla entre
  dos servicios multiplica la lógica fiscal.
- **Alternativas descartadas:** cuatro, con su motivo.
- **Consecuencias:** positivas, negativas y neutrales.
- **Alcance de la decisión posterior:** qué debe cubrir la migración cuando se
  construya el `billing-service`.

**Referencia desde la documentación:** `docs/doc-microservices.md` §3.8 incorpora
la enunciación inequívoca de la responsabilidad y remite al ADR-002.

## Task 9.3 — `docs/doc-estado-implementacion.md`

Hallazgos marcados como cerrados:

| Hallazgo | Estado registrado |
|:---|:---|
| P0 5.9 — El idempotency record no tiene ruta de actualización | ✅ CERRADO (`fix-provider-integration` 1.1, 1.2, 2.1–2.5) |
| P0 5.10 — El hub nunca contacta a ningún proveedor | ✅ CERRADO (`fix-provider-integration` 4.1, 4.2) |
| P0 5.11 — El fallback del `Idempotency-Key` deja de cobrar un segundo pago | ✅ CERRADO (`fix-provider-integration` 3.1, 3.2) |
| P1 6.3 — Carrera check-then-act en el patrón idempotente | ✅ CERRADO (`fix-provider-integration` 2.1, 2.2, 2.4) |
| P1 6.4 — Un fallo transitorio de pago queda cacheado como definitivo | ✅ CERRADO (`fix-provider-integration` 2.2) |
| P1 6.10 — `attempt_number` hardcodeado a 1 | ✅ CERRADO (`fix-provider-integration` 4.3) |
| P1 6.40 — `provider_configs` es código muerto | ✅ CERRADO (`fix-provider-integration` 9.1) |
| P1 6.41 — `OBSERVADO` se reporta como `RECHAZADO` | ✅ CERRADO (`fix-provider-integration` 5.1, 5.2) |
| P1 6.42 — La anulación a SUNAT es semánticamente incorrecta | ✅ CERRADO (`fix-provider-integration` 5.3, 5.4) |
| P1 6.43 — No hay numeración propia reservada | ✅ CERRADO (`fix-provider-integration` 6.1) |
| P1 6.44 — La máquina de estados del comprobante no existe | ✅ CERRADO (`fix-provider-integration` 5.1) |
| P1 6.45 — El backoff no llega a 24 h | ✅ CERRADO (`fix-provider-integration` 6.2) |
| P1 6.46 — La DLQ es un estado irrecuperable | ✅ CERRADO (`fix-provider-integration` 6.3, 6.4) |
| P1 6.47 — El procesamiento de webhooks no es idempotente | ✅ CERRADO (`fix-provider-integration` 7.1, 7.2) |
| P1 6.48 — El webhook estándar no tiene ventana anti-replay | ✅ CERRADO (`fix-provider-integration` 7.3) |
| P1 6.49 — El token de API de APISUNAT hace doble función | ✅ CERRADO (`fix-provider-integration` 7.4) |
| P1 6.50 — La API key interna otorga `ROLE_ADMIN` | ✅ CERRADO (`fix-provider-integration` 8.1, 8.2) |
| P1 7.22 — El gateway no valida `iss` ni `aud` | ✅ CERRADO (`fix-identity-lifecycle` 7.2) |
| P1 7.24 — `application.properties` tiene precedencia sobre `application.yml` | ✅ CERRADO (`fix-provider-integration` 9.1) |
| P1 7.35 — `response_hash` no es criptográfico y nunca se valida | ✅ CERRADO (`fix-provider-integration` 1.1, 2.5) |

El apartado **1 bis** incorpora el change con su recuento, sus cierres, los
defectos adicionales encontrados durante la implementación y el estado
**abierto** restante.

## Comprobación de que la documentación no afirma lo que no es

Las marcas de cerrado se han añadido **solo** a los hallazgos que el change
cierra, cada una con el intervalo de tasks que lo cierra. Los hallazgos que
siguen abiertos (P0 5.4, y el resto de P1 y P2 enumerados en §6 y §7) se
declaran explícitamente en el apartado 1 bis.
