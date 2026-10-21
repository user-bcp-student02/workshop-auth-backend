# Proposal

## Why

El [Issue #12: HU-01 - Token firmado con expiracion y claims minimos](https://github.com/user-bcp-student02/workshop-auth-backend/issues/12), consultado mediante `github-official` el 2026-10-21 y sin comentarios, compromete identidad y cliente verificables, expiracion coherente y proteccion de secretos. El contrato del Dia 1 describe un token Base64 sin firma que contiene un secreto: no garantiza integridad y expone material sensible.

## What Changes

- **BREAKING**: sustituir el comportamiento del token legacy por un token firmado con `sub`, `client`, `iat` y `exp`; no prometer compatibilidad con la decodificacion del formato anterior.
- Obtener `sub` de la identidad autenticada y `client` de la solicitud validada, sin inventar un cliente ni aceptar identidad desde campos adicionales.
- **BREAKING**, aprobado en revision el 2026-10-21: `client` pasa de opcional a obligatorio. Tras autenticar, ausencia, null, vacio o solo blancos devuelven 400 con `{"message":"Client is required"}`; los strings validos se conservan exactamente, sin normalizacion ni lista de permitidos. Los 400/401 existentes mantienen prioridad.
- Preservar la duracion declarada de 3600 segundos y exigir coherencia con la expiracion. Se confirma tolerancia de 2 segundos, medida al emitir con el mismo reloj, no tras el transporte HTTP ni como extension de validez.
- Rechazar tokens alterados o expirados en la comprobacion de validez, sin agregar un endpoint de verificacion ni atribuir esa comprobacion a `/q/health`.
- Documentar la configuracion de `AUTH_TOKEN_SECRET`; cualquier secreto por defecto debe quedar explicitamente limitado al laboratorio. No exponer secretos en el token ni registrar tokens, secretos o valores sensibles.
- Exigir evidencia de login valido, credenciales invalidas, claims, expiracion, configuracion y logs redactados. La decision tecnica sobre HMAC y libreria JWT se documenta en design.md, como exige el Issue.

## Capabilities

### New Capabilities

- `login-contract`: compatibilidad HTTP del login y de la salud documentados el Dia 1, salvo los cambios del token y de `client` expresamente identificados.
- `signed-auth-tokens`: integridad, claims minimos, vigencia, coherencia temporal y confidencialidad de tokens y configuracion.

### Modified Capabilities

Ninguna: el inventario `openspec/specs/` no contiene capacidades previas. Las nuevas specs formalizan tanto contratos existentes como requisitos del Issue; no significan que se creen nuevos endpoints.

## Impact

El alcance se limita a `workshop-auth-backend`, su `POST /auth/login`, emision/comprobacion de tokens, configuracion, pruebas y documentacion de evidencia. La planificacion incluye propuesta, specs, diseno y checklist; el Issue sigue siendo la unidad de trabajo del producto. Crear esos artefactos no autoriza iniciar implementacion funcional.

### Baseline y comportamiento protegido

Fuente contractual: [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md). Se preservan metodo/ruta, JSON, validacion y comparacion exacta de credenciales, orden de errores existentes, 401 generico documentado, estructura de exito (`token`, `expiresIn`, `user`, `debug`), identidad y escape del nombre visible, errores 500 y errores del framework. `GET /q/health` sigue siendo publico y no verifica tokens.

El Issue describe HMAC y `AUTH_TOKEN_SECRET` como existentes, pero el login inspeccionado usa `LegacyTokenUtil` con Base64 y secreto fijo. Se toma el Issue como objetivo aprobado y el contrato del Dia 1 como baseline, no su contexto como prueba de implementacion. La firma y los claims cambian explicitamente el token; `client`, antes opcional e ignorado, pasa a tener relevancia funcional.

### Fuera de alcance

Nuevos endpoints, refresh/revocacion, autorizacion por roles, cambios de almacenamiento o hashing de passwords, saneamiento de errores/debug, CORS, rate limiting, checks de salud adicionales, migracion de consumidores y cualquier otro repositorio. Las decisiones tecnicas pertenecen a design.md, no a esta propuesta.

### Riesgos y acuerdos de revision

- La revision humana del 2026-10-21 concreta la validacion de `client` descrita arriba y sus escenarios, incluido 401 para credenciales invalidas aunque falte `client`. No se inventan clientes por defecto ni se cambia el baseline historico del Dia 1.
- Se confirma el margen de coherencia de 2 segundos con reloj controlado; el token es invalido desde `exp` y no se garantiza un plazo de transporte de red.
- El diseno documenta HS256 con Nimbus y secreto obligatorio sin default, sin ampliar el alcance del Issue.
- Se conservan defectos del baseline no incluidos en el Issue, como `debug` y detalles de errores 500. No implica aprobarlos para produccion.
- La aceptacion funcional requerira pruebas y evidencia de logs con datos sinteticos. La validacion OpenSpec solo comprueba los artefactos; cualquier discrepancia previa de comportamiento requiere revision antes de tocar ese flujo critico. La implementacion necesita una solicitud posterior.