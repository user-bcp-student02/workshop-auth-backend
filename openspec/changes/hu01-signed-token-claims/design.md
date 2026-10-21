# Design

## Context

Cambio: `hu01-signed-token-claims`, schema `spec-driven`, repositorio `workshop-auth-backend`, rama `feature/HU01`. Motivacion y alcance aprobados: [proposal.md](proposal.md), fuente [Issue #12](https://github.com/user-bcp-student02/workshop-auth-backend/issues/12). Contratos: [login-contract](specs/login-contract/spec.md), [signed-auth-tokens](specs/signed-auth-tokens/spec.md) y [baseline del Dia 1](../../../docs/ENDPOINTS.md).

Evidencia local relevante:

- Java 21, Quarkus 3.27.4, CDI, REST Jackson, SmallRye Health, SQLite JDBC y Commons Text en [pom.xml](../../../pom.xml). No hay dependencia JWT declarada.
- [AuthResource.java](../../../src/main/java/com/bcp/workshop/auth/AuthResource.java) valida request/username/password, consulta directamente la base, construye la respuesta con mapas y llama al emisor estatico legacy. No hay una capa AuthService en esta rama.
- [LegacyTokenUtil.java](../../../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java) concatena username, rol, expiracion y un secreto fijo antes de codificar Base64. No firma ni verifica.
- [LoginRequest.java](../../../src/main/java/com/bcp/workshop/auth/LoginRequest.java) ya contiene `client`; [application.properties](../../../src/main/resources/application.properties) ignora propiedades JSON desconocidas y no configura un secreto de token.
- [AuthResourceTest.java](../../../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) contiene solo una prueba HTTP de login exitoso. No demuestra el resto del contrato ni aislamiento de SQLite.
- El baseline menciona hashing, consultas parametrizadas, TokenService RS256 y aborto de arranque ante fallo de SQLite. El codigo disponible no contiene esos servicios: [LegacyDatabase.java](../../../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java) usa passwords en claro, SQL concatenado y captura fallos de inicializacion con `printStackTrace()`. No se presupone que esas garantias esten implementadas.

El contrato aprobado sigue siendo el objetivo protegido; las discrepancias no autorizan reconstruir la persistencia ni modificar las specs silenciosamente. Una diferencia observable que aparezca al caracterizar se documentara y revisara antes de cambiar ese comportamiento. Esta entrega solo agrega este documento; no implementa ni acredita pruebas funcionales.

## Goals / Non-Goals

**Goals:**

- Encapsular firma, verificacion, claims y tiempo sin reestructurar el flujo completo de autenticacion.
- Mantener el contrato HTTP ajeno al token y hacer comprobables los nuevos requisitos con reloj y datos sinteticos controlados.
- Eliminar el secreto embebido del token nuevo y evitar valores sensibles en configuracion versionada, logs y evidencia.
- Aplicar las decisiones de contrato aprobadas en revision, manteniendo trazabilidad con las specs y separando discrepancias previas que requieran nuevo acuerdo.

**Non-Goals:**

- No introducir filtros Bearer, endpoints protegidos, verificacion en salud, sesiones, refresh, revocacion ni un proveedor externo de identidad.
- No separar toda la aplicacion en capas, sustituir los mapas HTTP, cambiar el modelo de usuario ni migrar la base.
- No corregir SQL concatenado, almacenamiento de passwords, CORS, errores HTTP detallados o `debug` como parte incidental de HU-01.
- No modificar consumidores u otros repositorios, crear tareas ni implementar codigo en este paso.

## Contratos protegidos

La referencia exhaustiva es `login-contract`; estos limites condicionan la integracion:

- Conservar `POST /auth/login` publico, entrada/salida JSON, campos y tipos del 200, `expiresIn = 3600`, `debug` literal, identidad de SQLite y escape HTML del displayName.
- Conservar validacion request/username/password, orden y mensajes 400, comparacion exacta de credenciales, 401 documentado y 500 capturados. No registrar el contenido sensible de excepciones aunque se preserve el esquema HTTP existente.
- Conservar campos desconocidos ignorados y errores 400/405/406/415 del framework en el entorno documentado; no agregar un mapper global ni Bean Validation que cambie cuerpos u orden.
- Conservar salud publica, checks vacios y los metodos/negociacion observados. No instalar seguridad HTTP automatica que altere estos endpoints.
- El token cambia explicitamente de formato legacy a firmado y `client` pasa de opcional e ignorado a obligatorio, segun revision aprobada el 2026-10-21. Se valida tras autenticar; ausencia, null, vacio o blancos producen 400 con `{"message":"Client is required"}`. Los strings validos se conservan exactamente sin lista de permitidos ni normalizacion; no se inventa un cliente fijo.

## Componentes afectados

| Componente | Cambio futuro limitado |
| --- | --- |
| AuthResource | Sustituir la llamada de emision, consumir su duracion y delegar la validacion aprobada de cliente tras autenticar; preservar armado HTTP y ramas existentes. |
| Servicio de tokens nuevo, dentro del paquete auth | Emision y verificacion internas, claims, duracion y reloj; no contiene consultas SQLite ni construye respuestas HTTP. |
| LegacyTokenUtil | Dejar de usarlo al activar la nueva emision; retirar el codigo obsoleto al completar la verificacion, sin fallback automatico. |
| LoginRequest | Reutilizar `client` sin renombrar campos ni agregar anotaciones que anticipen su validacion a las credenciales protegidas. |
| Configuracion y pom.xml | Configuracion explicita del secreto y una dependencia JWT; no agregar una extension que active autenticacion HTTP. |
| LegacyDatabase | Solo sustituir salida de errores potencialmente sensible si las pruebas de logs lo requieren; no cambiar consultas, esquema, seeds ni semantica de autenticacion. |
| Pruebas y documentacion locales | Ampliar la suite existente, incorporar pruebas unitarias de tokens y documentar configuracion, ruptura de formato y evidencia redactada. |

## Decisions

### 1. JWT/JWS con HS256 mediante Nimbus JOSE + JWT

Se elige `com.nimbusds:nimbus-jose-jwt` para serializacion, firma y verificacion, con algoritmo permitido exclusivamente HS256. HMAC y JWT no son alternativas excluyentes: la libreria implementa un formato JWT firmado con HMAC. Se mantiene el modelo de secreto compartido pedido por `AUTH_TOKEN_SECRET`, sin implementar criptografia o parsing a mano.

La version exacta se fijara al incorporar la dependencia, compatible con Java 21 y sin vulnerabilidades High/Critical no aceptadas; no se supondra que el BOM de Quarkus la gestiona. En este paso no se instala ni modifica ninguna dependencia. Esta seleccion debe acompanarse de la revision SCA al implementar, no constituye evidencia de seguridad de una version aun no seleccionada.

Alternativas: conservar Base64 no satisface integridad; escribir un formato HMAC propio reduce dependencias pero aumenta riesgo de canonicalizacion/parsing; RS256 agrega gestion de claves sin necesidad aprobada de separar firmantes y verificadores; activar una extension completa de seguridad JWT arriesga modificar el contrato publico. No se adopta ninguna de ellas.

### 2. Servicio pequeno con reloj inyectable y una sola duracion

Un bean CDI de tokens recibe `Clock` y configuracion al construirse. La emision devuelve el token y su duracion, no un objeto HTTP. Captura una sola vez `now`, usa segundos Unix enteros, establece `iat = now`, `exp = iat + 3600` y devuelve `expiresIn = exp - iat`. El Resource no conserva otra constante independiente de vigencia.

Se emiten `sub` desde el usuario autenticado y `client` desde el valor validado de la solicitud. Se puede conservar el dato de rol legacy como claim privado `role` tomado de SQLite, sin inferir autorizaciones ni restringir valores; no se agregan requisitos de issuer, audience o roles no pedidos. No se incluye secreto, password, hash ni displayName en claims. JWT firmado no cifra su contenido.

La verificacion interna usa la libreria para verificar firma con algoritmo fijado por configuracion del servidor, nunca seleccionado libremente por el header recibido. Rechaza `none`, algoritmos distintos, estructura o claims obligatorios invalidos y firmas no autorizadas. Solo despues de verificar integridad se consideran confiables los claims; se exige sujeto/cliente validos, tiempos numericos coherentes y `now < exp`. El cliente debe ser string no vacio ni solo blancos segun `String.trim()`, tanto en emision como en verificacion; la deteccion de blancos no modifica el valor aceptado.

No se agrega un endpoint de verificacion ni un filtro. La operacion interna permite probar firma y expiracion; no garantiza que un consumidor externo la invoque. Las fallas de verificacion no se convierten en nuevos codigos HTTP. No se admite el formato legacy en ese verificador.

Alternativas: `Instant.now()` estatico y esperas reales dificultan probar el limite exacto; dos constantes de duracion pueden divergir; extraer un servicio general de autenticacion ampliaria innecesariamente el cambio. La tolerancia de 2 segundos de la spec solo aplica a medicion de coherencia: el verificador no concede leeway despues de `exp`.

### 3. Secreto obligatorio, sin valor por defecto

`AUTH_TOKEN_SECRET` se suministra por el entorno y contiene Base64 estandar de al menos 32 bytes aleatorios. Se decodifica y valida una vez al arrancar, sin mostrar su contenido ni su longitud concreta en errores. No se deriva de passwords de usuarios. Ausencia, Base64 invalido o clave insuficiente impiden habilitar la emision; el componente se inicializa al arranque para evitar descubrirlo en el primer login. No hay fallback a la constante legacy.

Las pruebas usan una clave sintetica explicita; la documentacion usa placeholders y explica la codificacion y el entorno aislado. No se versionan secretos ni archivos locales que los contengan. Este fail-fast corresponde solo a la nueva configuracion de tokens y no altera el manejo de fallos SQLite. Con configuracion valida se conserva salud; sin configuracion valida no se promete disponibilidad HTTP.

Alternativa permitida por la spec: mantener un default documentado como exclusivo de sandbox. Se descarta para eliminar activaciones accidentales con una clave conocida y evitar dos politicas de seguridad por perfil. El coste es preparar configuracion explicita tambien en desarrollo, tests y ejecuciones automatizadas.

### 4. Integracion local y logs seguros

El Resource conserva las validaciones protegidas antes de cualquier politica nueva de cliente. Tras autenticacion y cliente validos invoca el servicio y arma la misma respuesta publica. No se cambia la consulta de credenciales ni se normalizan username/password.

La revision aprobada fija el orden: validacion request/username/password, autenticacion y por ultimo validacion de `client`. Asi, credenciales incorrectas con cliente ausente conservan el 401 existente; solo credenciales validas con cliente ausente, null, vacio o blancos generan el nuevo 400. La validacion de cliente no recorta ni cambia mayusculas, no usa una lista de permitidos ni introduce anotaciones que alteren la prioridad HTTP.

No se registran request, token, cabecera Authorization, secreto, password, hash ni payload de claims. La captura de logs cubrira stdout/stderr y excepciones existentes: errores JDBC pueden incluir la consulta concatenada con credenciales. Si ocurre, se reemplaza exclusivamente la impresion de esa excepcion por un evento fijo no sensible; se conserva la captura y el retorno null existentes. La redaccion del informe no sustituye esta proteccion en origen.

Alternativas: dejar `printStackTrace()` sin inspeccion incumple la prohibicion de logs sensibles; sanear toda la API o reescribir el repositorio mezcla deudas no aprobadas. Se opta por limitar cualquier cambio de logging al necesario para los escenarios del Issue.

## Pruebas de caracterizacion antes de modificar comportamiento critico

La prueba actual no basta. Antes de integrar el servicio se ampliara la suite local usando JUnit 5, Quarkus Test y REST Assured ya disponibles. Se reutilizara AuthResourceTest para HTTP y dobles del repositorio para ramas controladas; las pruebas de token puro no necesitan arrancar Quarkus.

Toda ejecucion debe definir `AUTH_DB_URL` antes del arranque hacia un archivo SQLite temporal exclusivo de la prueba, con usuarios sinteticos. No usar `data/workshop.db`, limpiar o migrar datos del laboratorio. Los casos JDBC usan esa base; los errores 500 se fuerzan con un doble, sin atribuirlos a un payload real.

| Caracterizacion previa | Evidencia requerida |
| --- | --- |
| Login correcto, ausencia de Authorization, campos desconocidos | Status, media type y forma completa del JSON, identidad almacenada, escape HTML, rol arbitrario, expiresIn/debug; no snapshots de tokens sensibles. |
| Request/username/password invalidos | Variantes ausente, null, vacio y blancos, orden y mensajes exactos, incluso con cliente ausente. |
| Credenciales incorrectas y consulta fallida | 401 y mensaje exacto, sin token; espacios y mayusculas sin normalizacion. No atribuir rechazo de hash a un hasher inexistente. |
| Excepcion capturada | 500 con y sin mensaje, nombres de campos y clase; misma conducta antes y despues de sustituir emision. |
| Transporte y salud | Errores del framework y cuerpo vacio, salud publica con Bearer invalido y negociacion/metodo observados; contrastar en el perfil del baseline, no extrapolar desde una prueba de unidad. |
| Cliente omitido/ignorado y token legacy | Registrar el comportamiento previo sin aprobarlo como objetivo nuevo; identificar las expectativas que cambiaran solo por decisiones explicitas. No fijar el secreto legacy como valor esperado permanente. |

Si el codigo actual no reproduce un escenario protegido, el fallo se clasifica como discrepancia previa y bloquea ese cambio critico hasta revision. No se debilita la spec, no se corrige persistencia por iniciativa propia y no se presenta la caracterizacion como aprobacion de sus vulnerabilidades.

## Migration Plan

### Estrategia incremental

1. Congelar evidencia de caracterizacion con SQLite aislada y contrastarla con las decisiones contractuales aprobadas indicadas abajo. No sustituir aun la emision ni cambiar el cuerpo HTTP; revisar cualquier discrepancia previa antes de modificar ese flujo critico.
2. Incorporar configuracion, dependencia y servicio de tokens con pruebas unitarias independientes. Validar secreto ausente/invalido, dos clientes validos, claims, clave distinta, firma alterada, algoritmo no permitido y estructura incompleta.
3. Usar reloj fijo para `iat`, `exp`, `exp - 1`, `exp` y `exp + 1`, y probar la igualdad de duracion y margen de 2 segundos sin esperas ni leeway. Comprobar que los datos recuperables no contienen el secreto.
4. Integrar la nueva emision en el Resource, manteniendo el resto de ramas. Aplicar la politica de cliente aprobada y sus escenarios concretos en las specs.
5. Reejecutar caracterizacion, nuevos escenarios y captura de logs de exito/error, incluidas excepciones JDBC controladas. Completar documentacion de configuracion y ruptura de formato; retirar la emision legacy no usada sin habilitar compatibilidad automatica.
6. Antes de habilitarlo en el laboratorio, ejecutar la suite Maven y gates de dependencias existentes con configuracion sintetica, revisar evidencia y comprobar el contrato HTTP del artefacto empaquetado. Ningun resultado de esos pasos se acredita con la sola validacion OpenSpec.

No se requiere migracion de esquema, passwords ni datos. La activacion se realiza reiniciando el unico emisor con configuracion valida, no desplegando cambios en otros repositorios. Los tokens viejos no son aceptados por el nuevo verificador; los usuarios de la prueba deben volver a iniciar sesion. No se inspecciona ni se presupone compatibilidad de consumidores.

### Rollback

Conservar el artefacto y la configuracion anteriores al cambio en el entorno de laboratorio, sin copiar secretos a Git o logs. Una regresion en contratos protegidos o logs sensibles impide la activacion; si ya se activo, suspender la emision y volver al ultimo artefacto firmado conocido como valido, cuando exista, con su configuracion compatible.

La primera activacion no tendra necesariamente un predecesor firmado seguro. No volver automaticamente al token legacy: reintroduce el secreto recuperable y la ausencia de integridad. En ese caso mantener el login detenido hasta corregir o hasta una decision humana explicita de restauracion exclusiva de laboratorio. La restauracion legacy no cumple HU-01 y no constituye cierre satisfactorio del Issue.

El rollback no toca SQLite y no debe recuperar ni registrar tokens emitidos. Un cambio de clave o verificador puede invalidarlos: requerir nuevo login, no implementar una ventana de doble aceptacion ni revocacion en este cambio. Si un secreto se expuso, no reutilizarlo al restaurar.

## Risks / Trade-offs

- [Discrepancias entre baseline y rama] Mitigacion: caracterizacion previa y revision de diferencias antes de cambios criticos; no prometer hashing, fail-fast SQLite o servicios inexistentes.
- [SQL concatenado y passwords en claro siguen presentes] Mitigacion: laboratorio aislado y datos sinteticos; registrar deuda fuera de HU-01, sin afirmar seguridad integral por firmar tokens.
- [HS256 comparte capacidad de firmar con quien conoce el secreto] Mitigacion: no distribuirlo ni afirmar validacion segura en consumidores no inspeccionados. RS256 se reconsideraria en otro alcance si se requiere separacion de confianza.
- [JWT expone sus claims y permite replay hasta exp] Mitigacion: claims minimos, sin datos sensibles; revocacion y cifrado no forman parte del cambio.
- [Nueva dependencia criptografica] Mitigacion: version fija, revision SCA, solo algoritmo permitido y pruebas negativas. No actualizar Quarkus u otras dependencias incidentalmente.
- [El secreto obligatorio cambia la preparacion del arranque] Mitigacion: documentar configuracion y preparar entornos antes de activar; fallo explicito sin fallback inseguro.
- [Relojes desalineados] Mitigacion: reloj controlado en pruebas y referencia UTC del emisor; el margen de coherencia no amplia la expiracion ni promete latencia de red.
- [Ruptura de formato y cliente antes opcional] Mitigacion: probar los nuevos escenarios aprobados y comunicar la obligatoriedad de cliente y nuevo login sin modificar otros repositorios.
- [Errores detallados/debug siguen expuestos] Mitigacion: preservacion deliberada del contrato, mensajes nuevos de configuracion sin valores sensibles y prohibicion de uso fuera del laboratorio.

## Acuerdos de contrato y gate de caracterizacion

La revision humana del 2026-10-21 aprobo expresamente estas reglas, ahora reflejadas en las specs y el checklist:

- `client` obligatorio; ausencia, null, vacio o blancos segun `String.trim()` se rechazan con 400 y `{"message":"Client is required"}` solo despues de autenticar. Strings validos se conservan exactamente, sin normalizacion ni lista de permitidos.
- Se mantienen los 400 existentes y el 401 de credenciales invalidas antes de validar cliente.
- El margen de coherencia de 2 segundos esta confirmado y nunca extiende la validez del token desde `exp`.

Sigue siendo obligatorio revisar cualquier discrepancia observable revelada por la caracterizacion que no pueda resolverse sin modificar las specs o ampliar el alcance. La creacion del checklist no autoriza iniciar implementacion funcional; requiere una solicitud posterior.