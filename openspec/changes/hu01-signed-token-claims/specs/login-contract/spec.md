# Spec Delta

## Purpose

Preservar los comportamientos HTTP del Dia 1 no cambiados por el Issue #12, distinguiendo las nuevas obligaciones del token y la politica de cliente aun pendiente.

Baseline: [docs/ENDPOINTS.md](../../../../../docs/ENDPOINTS.md).
Fuente del cambio: [Issue #12](https://github.com/user-bcp-student02/workshop-auth-backend/issues/12).
Los escenarios de login con credenciales incluyen un `client` valido, por ejemplo `angular-workshop`; no resuelven la omision de `client`. El formato legacy del token queda sustituido por la capacidad `signed-auth-tokens`.

## ADDED Requirements

### Requirement: Login exitoso conserva el contrato publico
El servicio SHALL conservar `POST /auth/login` sin autenticacion previa, entrada/salida JSON y respuesta 200 con `token` string no vacio, `expiresIn` entero 3600, `user` y `debug` igual a `authenticated-with-legacy-flow`. La identidad SHALL provenir del almacenamiento; no se devolveran password, hash, id ni el campo `client` fuera del token.

#### Scenario: Credenciales validas sin Authorization
- **WHEN** se envia JSON con credenciales validas y `client` valido, `Content-Type: application/json`, `Accept: application/json` y sin `Authorization`
- **THEN** se obtiene 200 con los campos y tipos documentados y un token conforme a `signed-auth-tokens`
- **AND** `user.username` y `user.role` coinciden con los almacenados, sin restringir los roles a un enum nuevo

#### Scenario: Nombre visible escapado
- **WHEN** el usuario autenticado tiene displayName `<Student & Trainer>`
- **THEN** `user.displayName` es `&lt;Student &amp; Trainer&gt;`

#### Scenario: Campos desconocidos no controlan identidad
- **WHEN** una solicitud valida agrega campos desconocidos como `role` o `sub`
- **THEN** se ignoran esos campos y la identidad de respuesta y el sujeto del token corresponden al usuario autenticado

### Requirement: Validacion de credenciales conserva orden y mensajes
El servicio SHALL conservar el orden request, username, password y sus errores 400 del Dia 1. Ausencia, null, cadena vacia o solo blancos segun `String.trim()` invalidan las credenciales. La nueva validacion de `client` SHALL NOT ocultar estos errores preexistentes.

#### Scenario: Request nulo o vacio
- **WHEN** se envia JSON `null` o cuerpo vacio en las condiciones observadas en el baseline
- **THEN** se obtiene 400 con `{"message":"Request is null"}`

#### Scenario: Username invalido se informa primero
- **WHEN** username esta ausente, es null, vacio o solo blancos, incluso si falta password
- **THEN** se obtiene 400 con `{"message":"Username is required"}`

#### Scenario: Password invalido con username valido
- **WHEN** username supera su validacion y password esta ausente, es null, vacio o solo blancos
- **THEN** se obtiene 400 con `{"message":"Password is required"}`

### Requirement: Autenticacion exacta conserva el rechazo documentado
El servicio SHALL comparar las credenciales exactas, sin recortarlas ni normalizarlas, conservando la distincion de mayusculas/minusculas del baseline. SHALL devolver 401 con `message` igual a `Invalid user <username enviado> or password` para usuario inexistente, password incorrecto o hash rechazado, sin emitir token.

#### Scenario: Credenciales invalidas
- **WHEN** se envia username `student` y password incorrecto con `client` valido
- **THEN** se obtiene 401 con `{"message":"Invalid user student or password"}` y sin token
- **AND** usuario inexistente y hash rechazado siguen el mismo esquema y no revelan la causa especifica

#### Scenario: No recortar ni cambiar mayusculas
- **WHEN** solo existe el usuario `student` con una password exacta y se envia ` student `, `STUDENT` o una password alterada agregando espacios
- **THEN** las credenciales no se convierten en las almacenadas y se obtiene el 401 documentado

#### Scenario: Fallo de consulta conserva el baseline
- **WHEN** la consulta de credenciales falla y no entrega un usuario, como documenta el Dia 1
- **THEN** se conserva el 401 documentado, sin convertirlo en evidencia de salud de la base

### Requirement: Errores ajenos al cambio conservan su contrato
El servicio SHALL preservar los errores 500 capturados y las respuestas observadas del framework en el entorno del baseline. El Issue #12 no autoriza sanear estos cuerpos ni homogeneizar todos los errores como JSON.

#### Scenario: Excepcion capturada con mensaje
- **WHEN** ocurre una `java.lang.IllegalStateException` con mensaje `test failure` en el flujo capturado del login
- **THEN** se obtiene 500 con `{"message":"test failure","exception":"java.lang.IllegalStateException"}`

#### Scenario: Excepcion capturada sin mensaje
- **WHEN** la misma excepcion carece de mensaje
- **THEN** se obtiene 500 con `{"message":"Unknown error","exception":"java.lang.IllegalStateException"}`

#### Scenario: Errores previos al metodo
- **WHEN** se envia JSON malformado o username como objeto en el entorno documentado
- **THEN** se obtiene 400 con cuerpo vacio y sin JSON de error inventado

#### Scenario: Metodo o negociacion incompatibles
- **WHEN** se consulta `GET /auth/login`, se pide `Accept: text/plain` en un POST JSON, o se envia `Content-Type: text/plain`
- **THEN** se obtienen respectivamente 405, 406 o 415, con cuerpo vacio en el entorno del baseline

### Requirement: Salud sigue publica y no verifica tokens
El servicio SHALL preservar `GET /q/health` sin autenticacion y sin agregar checks de autenticacion o SQLite. Con las dependencias y condiciones del Dia 1 SHALL conservar el 200 con `{"status":"UP","checks":[]}`. No se define un nuevo 401, 403, 405 o 503 para salud por este cambio.

#### Scenario: Salud sin credenciales o con Bearer invalido
- **WHEN** se consulta `GET /q/health` sin `Authorization` o con `Authorization: Bearer test-only-not-a-token`
- **THEN** se obtiene 200 con `{"status":"UP","checks":[]}` sin validar ese token

#### Scenario: Negociacion y metodo observados no se extrapolan del login
- **WHEN** se consulta salud con `Accept: text/plain` o mediante POST sin cuerpo en el entorno documentado
- **THEN** se conserva la respuesta JSON 200 observada, sin atribuirle el 406 o 405 del login