# Tasks

Unidad de trabajo del producto: [Issue #12 - HU-01](https://github.com/user-bcp-student02/workshop-auth-backend/issues/12). Este archivo es exclusivamente el checklist de implementacion de `hu01-signed-token-claims`, no un backlog adicional ni evidencia de trabajo ejecutado.

Referencias: [proposal.md](proposal.md), [contrato HTTP](specs/login-contract/spec.md), [tokens firmados](specs/signed-auth-tokens/spec.md), [design.md](design.md) y [baseline del Dia 1](../../../docs/ENDPOINTS.md). Las reglas de cliente y margen temporal aprobadas el 2026-10-21 son las de esos artefactos.

Ejecutar solo en `workshop-auth-backend`, rama `feature/HU01`, con datos sinteticos y SQLite temporal. Completar el grupo 1 antes de introducir cambios funcionales de los grupos siguientes; ante discrepancias no aprobadas, detener el flujo afectado y pedir revision, sin ampliar alcance. Cada casilla requiere su evidencia antes de marcarse.

## 1. Caracterizacion antes del cambio funcional

- [x] 1.1 Preparar fixtures de prueba con `AUTH_DB_URL` definido antes del arranque y archivo SQLite temporal por ejecucion; verificar que las pruebas no usan ni alteran `data/workshop.db` y documentar el comando reproducible de la suite.
- [x] 1.2 Ampliar AuthResourceTest para caracterizar login 200 sin Authorization, campos/tipos completos, expiresIn 3600, debug e identidad almacenada; verificar con la prueba HTTP focalizada, sin snapshots de token.
- [x] 1.3 Caracterizar displayName escapado, roles almacenados sin enum y campos desconocidos ignorados; verificar que las pruebas HTTP conservan identidad pese a `sub` o `role` adicionales en el request.
- [x] 1.4 Agregar casos request nulo/cuerpo vacio y username/password ausentes, null, vacios y blancos; verificar status, mensajes exactos y prioridad de los 400, incluso con cliente ausente.
- [x] 1.5 Caracterizar usuario inexistente, password incorrecto, espacios/mayusculas y consulta fallida controlada; verificar el 401 documentado y ausencia de token, sin implementar hashing ni cambiar consultas.
- [x] 1.6 Forzar mediante doble del repositorio excepciones con y sin mensaje; verificar ambos cuerpos 500 documentados sin atribuirlos a un payload HTTP real.
- [x] 1.7 Caracterizar JSON malformado/tipo incompatible y metodo/negociacion no permitidos en el perfil del baseline; verificar los 400/405/406/415 y cuerpos vacios observados, registrando el entorno usado.
- [x] 1.8 Caracterizar salud publica sin Authorization y con Bearer invalido, Accept text/plain y POST sin cuerpo; verificar JSON 200 con status UP y checks vacios en las condiciones del baseline.
- [x] 1.9 Registrar el comportamiento previo de client omitido/ignorado y token legacy mediante pruebas focalizadas; verificar que se identifican como expectativas a sustituir por el cambio aprobado, sin fijar el secreto legacy como contrato permanente.
- [x] 1.10 Ejecutar la caracterizacion completa y dejar evidencia redactada por escenario; verificar cada diferencia contra baseline/specs y obtener revision de cualquier discrepancia no autorizada antes de modificar el flujo critico, sin corregir deuda de persistencia incidentalmente.

## 2. Dependencia y configuracion segura

- [x] 2.1 Incorporar una version fija de `com.nimbusds:nimbus-jose-jwt` compatible con Java 21, sin activar seguridad HTTP automatica; verificar resolucion con `mvn dependency:tree`, compilacion y revision SCA sin High/Critical no aceptadas en la nueva dependencia.
- [x] 2.2 Implementar lectura obligatoria de `AUTH_TOKEN_SECRET` como Base64 de al menos 32 bytes aleatorios, sin default; verificar con pruebas unitarias clave sintetica valida, ausencia, Base64 invalido y longitud insuficiente, sin imprimir valores sensibles.
- [x] 2.3 Inicializar y validar la configuracion de tokens al arrancar; verificar con pruebas de arranque que configuracion invalida falla sin esperar al primer login y configuracion sintetica valida permite iniciar, sin cambiar el manejo de fallos SQLite.
- [x] 2.4 Documentar configuracion local y de pruebas con placeholders, codificacion requerida y ausencia de fallback; verificar el procedimiento en laboratorio aislado y que no se versionan ni muestran secretos reales.

## 3. Emision incremental con reloj controlado

- [ ] 3.1 Agregar pruebas unitarias del contrato de emision con Clock fijo para sub, client, iat, exp, expiresIn y dos clientes validos distintos; verificar que fallan por la funcionalidad aun ausente, manteniendo verde la caracterizacion.
- [ ] 3.2 Implementar el servicio CDI de tokens con Clock inyectable, firma HS256 mediante Nimbus y resultado token/duracion, sin conexion aun al login; verificar que pasan las pruebas de emision y `exp - iat = expiresIn = 3600` usando una sola captura temporal.
- [ ] 3.3 Probar cliente con espacios externos y mayusculas y revisar datos recuperables del token; verificar preservacion exacta de client y ausencia de secreto, password, hash y displayName en el payload.
- [ ] 3.4 Probar la coherencia `abs(expiresIn - (exp - now)) <= 2` en el instante de emision con reloj controlado; verificar resultados deterministas sin esperas ni medicion de latencia de red y documentar la referencia temporal junto a esa evidencia.

## 4. Verificacion interna y expiracion

- [ ] 4.1 Agregar pruebas de token autentico, clave distinta, claims/firma alterados, `none`, algoritmos no autorizados y estructura o claims obligatorios invalidos; verificar fallos esperados antes de implementar el verificador y no crear endpoints.
- [ ] 4.2 Implementar verificacion interna con HS256 fijado por el servidor y claims confiables solo tras comprobar firma; verificar la bateria anterior y rechazo del formato legacy, manteniendo emision y verificacion independientes del Resource HTTP.
- [ ] 4.3 Agregar e implementar controles temporales con Clock fijo en `exp - 1`, `exp` y `exp + 1`; verificar aceptacion solo antes de exp, tiempos coherentes y ningun margen adicional de validez.
- [ ] 4.4 Verificar tipos y obligatoriedad de sub/client/iat/exp, incluida la misma regla de cliente no vacio ni blancos y su preservacion exacta; comprobar rechazo mediante tokens de prueba firmados con claims invalidos, no solo tokens con firma alterada.
- [ ] 4.5 Documentar el alcance de la verificacion y la decision HS256 con Nimbus frente a las alternativas; verificar que no se promete validacion en salud ni consumidores externos, cifrado, revocacion o proteccion contra replay.

## 5. Integracion del login y cliente aprobado

- [ ] 5.1 Agregar pruebas HTTP de client ausente, null, vacio y blancos con credenciales validas; verificar fallo esperado antes del cambio y resultado objetivo 400 con `{"message":"Client is required"}`, sin token.
- [ ] 5.2 Agregar pruebas de prioridad y clientes validos: credenciales invalidas con client ausente conservan 401; request/username/password invalidos conservan sus 400; strings no blancos arbitrarios se aceptan sin lista ni normalizacion. Verificar los resultados exactos de cada caso.
- [ ] 5.3 Integrar validacion de client tras autenticar y nueva emision en AuthResource, tomando expiresIn del resultado del servicio; verificar las pruebas 5.1/5.2 y que un login valido entrega claims de su usuario y cliente sin alterar el resto del JSON publico.
- [ ] 5.4 Sustituir solo las expectativas legacy afectadas en la caracterizacion y retirar la emision legacy sin fallback; verificar suite HTTP verde, rechazo legacy en el verificador y ausencia de llamadas productivas a LegacyTokenUtil.
- [ ] 5.5 Documentar el nuevo contrato en un complemento al baseline del Dia 1, con client obligatorio, orden de errores, formato firmado y nuevo login; verificar correspondencia con las pruebas y preservar docs/ENDPOINTS.md como referencia historica, sin inspeccionar consumidores.

## 6. Logs y evidencia sin valores sensibles

- [ ] 6.1 Agregar captura de logs/stdout/stderr con datos sinteticos para exito, credenciales invalidas, excepciones del Resource y errores JDBC controlados; verificar ausencia de tokens, secretos, passwords, hashes y sus representaciones reversibles en la salida original, sin volcarla a informes sensibles.
- [ ] 6.2 Corregir exclusivamente las emisiones sensibles detectadas, reemplazando stack traces JDBC por eventos fijos cuando sea necesario; verificar las pruebas de logs y mantener retorno null/401, esquema HTTP de errores, consultas y almacenamiento sin otros cambios.
- [ ] 6.3 Adjuntar evidencia redactada de logs y resultados de pruebas al documento de evidencia del Issue, junto con los comandos reproducibles; verificar que permite identificar cada caso sin exponer valores sensibles y sin ocultar filtraciones en logs originales.

## 7. Validacion integrada y revision del Issue

- [ ] 7.1 Ejecutar `mvn verify` con secreto sintetico explicito y SQLite temporal; verificar pruebas completas y reportes de cobertura disponibles, sin bypass de gates ni declaraciones de cobertura sin reporte.
- [ ] 7.2 Contrastar login/errores/salud del artefacto empaquetado en el entorno aislado contra la caracterizacion y nuevos escenarios; verificar cuerpos, status y que no se activaron filtros Bearer ni endpoints adicionales.
- [ ] 7.3 Revisar en laboratorio el procedimiento de activacion/rollback de design.md; verificar que no modifica SQLite, no reutiliza claves expuestas y detiene emision antes que volver automaticamente al token inseguro si no existe predecesor firmado seguro.
- [ ] 7.4 Consolidar la trazabilidad entre criterios del Issue #12, escenarios, pruebas y evidencia redactada; verificar decision tecnica/configuracion documentadas y que SQL parametrizado, hashing, CORS, saneamiento de debug/errores y otros repositorios no se incorporaron como trabajo secundario.
- [ ] 7.5 Ejecutar `openspec validate hu01-signed-token-claims --strict --json` y revisar diff final en feature/HU01; verificar cero errores/advertencias OpenSpec, cambios solo del alcance aprobado y checkboxes marcados unicamente con evidencia real.