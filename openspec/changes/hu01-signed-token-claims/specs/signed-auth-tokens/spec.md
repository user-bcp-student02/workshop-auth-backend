# Spec Delta

## Purpose

Definir las garantias observables de integridad, identidad, cliente, vigencia y confidencialidad de los tokens comprometidas por el Issue #12, sin prescribir formato, algoritmo o libreria.

Fuente: [Issue #12](https://github.com/user-bcp-student02/workshop-auth-backend/issues/12).
Baseline: [docs/ENDPOINTS.md](../../../../../docs/ENDPOINTS.md).
La firma y los claims sustituyen explicitamente el token legacy sin firma. La comprobacion de tokens descrita aqui es una condicion verificable del token, no un nuevo endpoint ni una garantia sobre consumidores no inspeccionados. La politica de `client` esta aprobada y concretada en `login-contract`.

## ADDED Requirements

### Requirement: Token firmado protege su integridad
El sistema SHALL emitir tokens firmados para el login exitoso y SHALL distinguir un token autentico de uno alterado o firmado con material no autorizado. Una cadena Base64 con datos y secreto concatenados SHALL NOT satisfacer este requisito. El material secreto SHALL NOT formar parte de los datos recuperables del token.

#### Scenario: Token autentico
- **WHEN** se obtiene un token mediante login valido y se comprueba su firma con el material de verificacion autorizado antes de expirar
- **THEN** la firma se reconoce como valida y los claims son los emitidos para esa autenticacion

#### Scenario: Claims o firma alterados
- **WHEN** se modifica el sujeto, cliente, expiracion o firma de un token emitido sin una nueva firma autorizada
- **THEN** el token no supera la comprobacion de autenticidad

#### Scenario: Material de verificacion no autorizado
- **WHEN** se comprueba el token usando material de verificacion que no corresponde al emisor
- **THEN** la firma no se considera valida

#### Scenario: No incluir el secreto en el token
- **WHEN** se inspeccionan los datos recuperables de un token emitido con un secreto sintetico conocido
- **THEN** no contienen ese secreto ni una codificacion reversible del mismo como dato o claim

### Requirement: Claims minimos identifican sujeto y cliente autenticados
Todo token emitido SHALL contener `sub`, `client`, `iat` y `exp`. `sub` SHALL coincidir con el username autenticado y `client` con el valor validado de la solicitud, sin sustituirlo por una constante. `iat` y `exp` SHALL ser instantes numericos en segundos Unix, con `exp` posterior a `iat`. No se exige ni elimina un claim de rol por este Issue.

#### Scenario: Claims de login valido
- **WHEN** se autentica un usuario con username almacenado `student` y `client` valido `angular-workshop`
- **THEN** el token contiene `sub` igual a `student`, `client` igual a `angular-workshop` y los instantes `iat` y `exp`
- **AND** un `sub` adicional enviado en el request no reemplaza la identidad autenticada

#### Scenario: Cliente procede de cada solicitud
- **WHEN** el mismo usuario inicia sesion con dos identificadores distintos que superan la politica de validacion de cliente acordada
- **THEN** cada token refleja el identificador de su solicitud y no un cliente fijo

### Requirement: Cliente se valida antes de emitir
El sistema SHALL validar `client` tras autenticar y antes de emitir, conforme a `login-contract`. SHALL rechazar ausencia, null, vacio o solo blancos segun `String.trim()`, sin emitir token. SHALL aceptar los demas strings sin normalizacion ni lista de permitidos y reflejarlos exactamente en el claim, preservando la prioridad de errores de credenciales.

#### Scenario: Cliente valido se refleja sin sustitucion
- **WHEN** las credenciales y el cliente enviado superan sus validaciones
- **THEN** se emite un token cuyo claim `client` corresponde al identificador validado de esa solicitud

#### Scenario: Cliente rechazado no obtiene token
- **WHEN** una solicitud con credenciales validas omite `client` o lo envia null, vacio o solo blancos
- **THEN** se obtiene 400 con `{"message":"Client is required"}` y sin token

#### Scenario: Cliente no se normaliza ni sustituye
- **WHEN** se autentica con `client` igual a ` Angular-Workshop `
- **THEN** el token firmado contiene exactamente ` Angular-Workshop ` como cliente, sin recortes, cambios de mayusculas ni sustitucion por una constante

### Requirement: Expiracion y duracion declarada son coherentes
El sistema SHALL preservar `expiresIn = 3600` segundos y emitir `exp - iat = 3600`. Al instante de emision observado `now`, con el mismo reloj de referencia, SHALL cumplirse `abs(expiresIn - (exp - now)) <= 2` segundos. Esta tolerancia aprobada cubre granularidad/observacion, no tiempo de red ni ampliacion de la validez tras `exp`.

#### Scenario: Coherencia con reloj controlado
- **WHEN** se emite un token con reloj de referencia fijado en el segundo Unix `t`
- **THEN** `iat` es `t`, `exp` es `t + 3600` y la respuesta declara `expiresIn` entero igual a 3600
- **AND** la diferencia absoluta entre `expiresIn` y `exp - now` al observar la emision no supera 2 segundos

#### Scenario: Evidencia explicita del margen
- **WHEN** se revisa la evidencia de coherencia temporal con datos sinteticos
- **THEN** se documentan el reloj de referencia, el instante de emision y la tolerancia de 2 segundos
- **AND** no se interpreta una medicion tardia del cliente HTTP como una nueva duracion del token

### Requirement: Token expirado no es valido
La comprobacion de validez SHALL rechazar un token cuando el instante de referencia sea mayor o igual a `exp`, aunque su firma sea correcta. Antes de `exp`, un token autentico con claims validos SHALL superar la comprobacion temporal. No se requiere un endpoint protegido nuevo ni se promete validacion en otros repositorios.

#### Scenario: Antes del limite
- **WHEN** un token autentico con claims validos se comprueba en `exp - 1` segundo
- **THEN** supera las comprobaciones de firma y expiracion

#### Scenario: Limite exacto y token vencido
- **WHEN** el mismo token se comprueba en `exp` o en `exp + 1` segundo
- **THEN** se rechaza por expiracion, sin extender el plazo por la tolerancia de coherencia de `expiresIn`

### Requirement: Configuracion de secreto delimita el laboratorio
La documentacion SHALL explicar `AUTH_TOKEN_SECRET`, su uso y como configurarlo sin publicar un valor real. Si se conserva un secreto por defecto, SHALL identificarse explicitamente como exclusivo de laboratorio/sandbox; SHALL NOT presentarse como configuracion segura fuera de ese entorno. Esto no habilita el despliegue del laboratorio en Internet.

#### Scenario: Configuracion reproducible sin secretos reales
- **WHEN** se revisa la documentacion de configuracion
- **THEN** contiene instrucciones para `AUTH_TOKEN_SECRET` con placeholders o datos sinteticos y explica las restricciones del entorno

#### Scenario: Tratamiento del valor por defecto
- **WHEN** se revisa la configuracion y la documentacion de la solucion elegida
- **THEN** no existe un secreto por defecto presentado como apto fuera del sandbox
- **AND** cualquier valor por defecto conservado queda documentado como exclusivo del laboratorio

### Requirement: Logs y evidencia no divulgan valores sensibles
El sistema SHALL NOT registrar tokens completos, secretos, passwords, hashes ni representaciones reversibles de esos valores. La evidencia SHALL usar datos sinteticos y logs redactados sin conservar esos valores sensibles, tanto en exito como en errores de autenticacion o excepciones.

#### Scenario: Logs de login exitoso
- **WHEN** se realiza un login exitoso con credenciales y secreto sinteticos identificables y se capturan sus logs
- **THEN** los logs no contienen el token devuelto, el secreto, la password ni el hash, tampoco codificados de forma reversible

#### Scenario: Logs de errores
- **WHEN** se ejercitan credenciales invalidas y una excepcion del login con datos sinteticos
- **THEN** los logs capturados tampoco contienen tokens, secretos, passwords ni hashes

#### Scenario: Evidencia auditable y redactada
- **WHEN** se revisa la evidencia de pruebas y logs del Issue #12
- **THEN** se pueden identificar los casos de login valido, credenciales invalidas, claims y expiracion sin mostrar valores sensibles
- **AND** redactar el informe no se usa para ocultar que la aplicacion registro esos valores: los logs originales de prueba tambien deben satisfacer la prohibicion

### Requirement: Decision tecnica posterior queda documentada
La evidencia de cierre del Issue SHALL incluir la decision sobre conservar HMAC o adoptar una libreria JWT y su justificacion, ademas de pruebas de claims, expiracion, errores de autenticacion y configuracion. Esta spec SHALL NOT elegir ninguna alternativa ni exigir un formato JWT.

#### Scenario: Revision de evidencia de cierre
- **WHEN** se evalua la implementacion futura contra el Issue #12
- **THEN** existen resultados verificables de los escenarios funcionales y una decision tecnica documentada sobre las alternativas del Issue
- **AND** la presente propuesta no se presenta como esa decision ni como evidencia de pruebas ejecutadas