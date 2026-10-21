# Proposal

## Why

El [Issue #12: HU-01 - Token firmado con expiracion y claims minimos](https://github.com/user-bcp-student02/workshop-auth-backend/issues/12), consultado mediante `github-official` el 2026-10-21 y sin comentarios, compromete identidad y cliente verificables, expiracion coherente y proteccion de secretos. El contrato del Dia 1 describe un token Base64 sin firma que contiene un secreto: no garantiza integridad y expone material sensible.

## What Changes

- **BREAKING**: sustituir el comportamiento del token legacy por un token firmado con `sub`, `client`, `iat` y `exp`; no prometer compatibilidad con la decodificacion del formato anterior.
- Obtener `sub` de la identidad autenticada y `client` de la solicitud validada, sin inventar un cliente ni aceptar identidad desde campos adicionales.
- Preservar la duracion declarada de 3600 segundos y exigir coherencia con la expiracion. Se propone tolerancia de 2 segundos, medida al emitir con el mismo reloj, no tras el transporte HTTP.
- Rechazar tokens alterados o expirados en la comprobacion de validez, sin agregar un endpoint de verificacion ni atribuir esa comprobacion a `/q/health`.
- Documentar la configuracion de `AUTH_TOKEN_SECRET`; cualquier secreto por defecto debe quedar explicitamente limitado al laboratorio. No exponer secretos en el token ni registrar tokens, secretos o valores sensibles.
- Exigir evidencia de login valido, credenciales invalidas, claims, expiracion, configuracion y logs redactados. La decision HMAC frente a una libreria JWT queda pendiente de una etapa posterior, como exige el Issue.

## Capabilities

### New Capabilities

- `login-contract`: compatibilidad HTTP del login y de la salud documentados el Dia 1, salvo los cambios del token y de `client` expresamente identificados.
- `signed-auth-tokens`: integridad, claims minimos, vigencia, coherencia temporal y confidencialidad de tokens y configuracion.

### Modified Capabilities

Ninguna: el inventario `openspec/specs/` no contiene capacidades previas. Las nuevas specs formalizan tanto contratos existentes como requisitos del Issue; no significan que se creen nuevos endpoints.

## Impact

El alcance se limita a `workshop-auth-backend`, su `POST /auth/login`, emision/comprobacion de tokens, configuracion, pruebas y documentacion de evidencia. Esta entrega contiene solo propuesta y specs, sin cambios funcionales, dependencias, diseno tecnico ni tareas.

### Baseline y comportamiento protegido

Fuente contractual: [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md). Se preservan metodo/ruta, JSON, validacion y comparacion exacta de credenciales, orden de errores existentes, 401 generico documentado, estructura de exito (`token`, `expiresIn`, `user`, `debug`), identidad y escape del nombre visible, errores 500 y errores del framework. `GET /q/health` sigue siendo publico y no verifica tokens.

El Issue describe HMAC y `AUTH_TOKEN_SECRET` como existentes, pero el login inspeccionado usa `LegacyTokenUtil` con Base64 y secreto fijo. Se toma el Issue como objetivo aprobado y el contrato del Dia 1 como baseline, no su contexto como prueba de implementacion. La firma y los claims cambian explicitamente el token; `client`, antes opcional e ignorado, pasa a tener relevancia funcional.

### Fuera de alcance

Nuevos endpoints, refresh/revocacion, autorizacion por roles, cambios de almacenamiento o hashing de passwords, saneamiento de errores/debug, CORS, rate limiting, checks de salud adicionales, migracion de consumidores y cualquier otro repositorio. No se eligen algoritmo, formato JWT, libreria, clases ni estrategia de implementacion.

### Riesgos, supuestos y decisiones pendientes

- El Issue exige validar `client` pero no define obligatoriedad, valores admitidos, normalizacion ni respuesta de error. No se inventa esa politica: las specs cubren solicitudes con `client` valido y exigen validacion, pero la omision (antes permitida) y los casos invalidos requieren acuerdo antes de implementar. No queda aprobado un cambio silencioso de opcional a obligatorio.
- La tolerancia propuesta de 2 segundos y la comprobacion de expiracion con reloj controlado requieren revision; no se garantiza un plazo de transporte de red.
- La politica concreta del secreto de laboratorio y la decision HMAC frente a libreria JWT se documentaran posteriormente, sin ampliar el alcance del Issue.
- Se conservan defectos del baseline no incluidos en el Issue, como `debug` y detalles de errores 500. No implica aprobarlos para produccion.
- La aceptacion funcional requerira pruebas y evidencia de logs con datos sinteticos. La validacion OpenSpec solo comprueba los artefactos; esta propuesta no esta lista para implementacion mientras sigan pendientes las decisiones de contrato anteriores.