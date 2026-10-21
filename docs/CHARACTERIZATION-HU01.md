# Caracterizacion HU-01 / Issue #12

## Alcance y referencias

Solo `workshop-auth-backend`, rama `feature/HU01`, grupo 1 de [tasks.md](../openspec/changes/hu01-signed-token-claims/tasks.md). Baseline: [ENDPOINTS.md](ENDPOINTS.md). La guia TESTING del Dia 1 no existe en esta rama; el usuario autorizo continuar revisando directamente las pruebas existentes y este contrato.

Se reutiliza [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). [AuthTransportIT.java](../src/test/java/com/bcp/workshop/auth/AuthTransportIT.java) agrega solamente transporte y salud sobre el artefacto empaquetado, porque el baseline los observa en prod. [IsolatedDatabaseResource.java](../src/test/java/com/bcp/workshop/auth/IsolatedDatabaseResource.java) exige una ruta SQLite nueva directamente bajo target, con nombre characterization-*.db, antes de arrancar Quarkus. Rechaza la base del laboratorio y elimina la base temporal al terminar. Los datos son sinteticos, el puerto de pruebas es efimero y ambas ejecuciones escuchan solo en 127.0.0.1.

Entorno observado: Windows, Java 21.0.12.1, Maven 3.10.0, Quarkus 3.27.4. No se modifica codigo productivo, dependencias de la aplicacion ni contratos HTTP. La configuracion Maven de pruebas proporciona automaticamente AUTH_DB_URL a Surefire y Failsafe. No se comprueba todavia JWT, firma, expiracion efectiva ni la nueva obligatoriedad de client.

## Comando reproducible

Ejecutar desde la raiz de workshop-auth-backend, en PowerShell:

```powershell
mvn '-Dtest=AuthResourceTest' test
```

Caracterizacion completa, incluyendo el perfil prod, sin saltar pruebas:

```powershell
mvn '-Dtest=AuthResourceTest' '-Dit.test=AuthTransportIT' package quarkus:build org.apache.maven.plugins:maven-failsafe-plugin:3.5.4:integration-test org.apache.maven.plugins:maven-failsafe-plugin:3.5.4:verify
```

Surefire y Failsafe reciben rutas distintas bajo target, con prefijos characterization-surefire- y characterization-failsafe- y numero de fork. Los recursos eliminan sus bases al terminar y siguen rechazando archivos preexistentes: no se borra una base ajena para forzar el arranque. No hace falta exportar AUTH_DB_URL manualmente para estos comandos Maven. Esto no configura automaticamente un ejecutor JUnit externo a Maven, que debera proporcionar su propia ruta aislada.

Failsafe arranca y detiene su propio proceso prod; no usa servidores ya presentes ni necesita cambiar el puerto 8081 ocupado. Las ejecuciones historicas hasta las 17:39 definian AUTH_DB_URL manualmente con una ruta GUID en un bloque PowerShell y restauraban el entorno al finalizar; la nueva configuracion elimina esa precondicion manual para Maven.

No imprime ni conserva tokens, passwords o secretos en esta evidencia. Los reportes de ejecucion quedan en target/surefire-reports y target/failsafe-reports y pueden ser reemplazados por la siguiente ejecucion. La ejecucion es caracterizacion del grupo 1, no cumplimiento de las tareas de implementacion o gates finales de los grupos 2 a 7.

## Ejecuciones

| Fecha local | Comando | Resultado |
| --- | --- | --- |
| 2026-10-21 17:26 | Comando anterior, antes del ajuste de puerto exclusivo de tests | BUILD FAILURE: 1 prueba, 0 fallos de asercion, 1 error de arranque; puerto 8081 ocupado. No se ejercito el contrato HTTP. |
| 2026-10-21 17:27 | Comando anterior con puerto efimero en el recurso de pruebas | BUILD SUCCESS: 1 prueba, 0 fallos, 0 errores, 0 omitidas. |
| 2026-10-21 17:30 | Mismo comando, suite ampliada de login | BUILD SUCCESS: 11 pruebas, 0 fallos, 0 errores, 0 omitidas. El error SQLite provocado es parte de la caracterizacion y produce el 401 esperado. |
| 2026-10-21 17:32 | Comando completo con package, quarkus:build y Failsafe | BUILD SUCCESS: Surefire 11 pruebas en perfil test y Failsafe 9 pruebas en perfil prod; 20 en total, 0 fallos, 0 errores, 0 omitidas. |
| 2026-10-21 17:39 | Reejecucion del mismo comando completo tras revisar Issue y artefactos OpenSpec | BUILD SUCCESS: Surefire 11 pruebas en test y Failsafe 9 en prod; 20 en total, 0 fallos, 0 errores, 0 omitidas. Duracion Maven: 48.405 segundos. No se agregaron pruebas duplicadas ni se marcaron tareas adicionales. |
| Antes de la correccion de configuracion | Reporte Surefire de ejecucion sin AUTH_DB_URL | 11 pruebas contabilizadas: 1 error de arranque y 10 omitidas. InvocationTargetException envolvia IllegalStateException por variable ausente, no un fallo del contrato de login. |
| 2026-10-21 17:44 | `mvn '-Dtest=AuthResourceTest' test`, con AUTH_DB_URL externo retirado temporalmente | BUILD SUCCESS: 11 pruebas, 0 fallos, 0 errores, 0 omitidas. Surefire proporciona la base aislada automaticamente. |
| 2026-10-21 17:45 | Comando completo actual, con AUTH_DB_URL externo retirado temporalmente | BUILD SUCCESS: 11 pruebas Surefire y 9 Failsafe; 20 en total, 0 fallos, 0 errores, 0 omitidas. Duracion Maven: 38.286 segundos. |

En Failsafe se observaron avisos del harness sobre inicializacion temprana de LogManager y una clave de categoria de logging ignorada. No son fallos de asercion ni se ocultaron/desactivaron gates para obtener verde; no se modifico logging productivo para corregirlos.

## Causa y correccion de InvocationTargetException

La excepcion de reflexion era un envoltorio del arranque de Quarkus. La causa final del reporte Surefire era `IllegalStateException: Set AUTH_DB_URL to a fresh characterization database under target`, emitida por el recurso de aislamiento. Los comandos iniciales funcionaban porque exportaban la variable, pero una ejecucion Maven normal no lo hacia.

Se corrigio unicamente la configuracion de los plugins de pruebas en [pom.xml](../pom.xml), inyectando una URL SQLite aislada en el entorno de cada JVM de Surefire/Failsafe. Se conserva el guard de seguridad del recurso, no se cambia System.getenv en codigo productivo y no se omiten pruebas. Las dos ejecuciones finales quitaron la variable del entorno padre dentro de un bloque try/finally y la restauraron despues, para verificar precisamente el caso que fallaba.

## Comportamientos protegidos

| Prueba | Requisito o comportamiento protegido | Estado |
| --- | --- | --- |
| logsInWithWorkshopUser | Login 200 sin Authorization, JSON con campos publicos exactos, identidad almacenada, token string no vacio, expiresIn entero 3600 y debug legacy. No exige formato JWT. | Pasa en perfil test. |
| preservesStoredIdentityEscapesDisplayNameAndIgnoresUnknownFields | Nombre HTML escapado, rol AUDITOR sin enum nuevo e identidad de SQLite no reemplazada por sub/role/displayName enviados. | Pasa en perfil test. |
| rejectsNullAndEmptyRequests | Request JSON null o cuerpo vacio: 400 Request is null. | Pasa en perfil test. |
| validatesUsernameBeforePassword | Username ausente, null, vacio, espacios o tab: prioridad del 400 Username is required, aunque falte password/client. | Pasa en perfil test. |
| validatesPasswordAfterUsername | Username valido y password ausente, null, vacio, espacios o tab: 400 Password is required. | Pasa en perfil test. |
| rejectsInvalidCredentialsWithoutTrimmingOrChangingCase | Usuario inexistente, password incorrecto, espacios y mayusculas: mismo esquema 401, sin token ni normalizacion. | Pasa en perfil test. |
| returnsUnauthorizedWhenTheIsolatedCredentialQueryFails | Tabla temporalmente no disponible: error real de consulta conservado como 401; la tabla se restaura en finally. No demuestra salud de SQLite. | Pasa en perfil test. |
| preservesCapturedExceptionWithMessage | Resource invocado directamente con doble que lanza IllegalStateException: status 500 y entidad exacta con mensaje/clase. | Pasa; no se atribuye a una peticion HTTP real. |
| preservesCapturedExceptionWithoutMessage | Misma rama con mensaje nulo: Unknown error y clase de excepcion documentada. | Pasa; no se atribuye a una peticion HTTP real. |
| characterizesClientAsOptionalAndIgnoredBeforeTheApprovedChange | Client omitido, null, vacio, blancos u otro identificador sigue permitiendo el login actual; estas expectativas se sustituiran al implementar el cambio aprobado. | Pasa en perfil test. |
| characterizesLegacyTokenFieldsWithoutFixingTheEmbeddedSecret | Token Base64 con identidad/rol almacenados y expiracion declarada a una hora; sin snapshot ni asercion sobre el valor del secreto y sin afirmar firma o validacion efectiva. | Pasa en perfil test. |
| malformedJsonHasAnEmptyBadRequestResponse | JSON malformado: 400, cuerpo vacio y sin Content-Type de respuesta, antes del Resource. | Pasa en perfil prod. |
| incompatibleUsernameTypeHasAnEmptyBadRequestResponse | Username como objeto: mismo 400 sin JSON inventado de error. | Pasa en perfil prod. |
| getLoginIsNotAllowed | GET /auth/login: 405 con cuerpo vacio y sin Content-Type. | Pasa en perfil prod. |
| incompatibleAcceptIsNotAcceptable | POST JSON con Accept text/plain: 406 con cuerpo vacio y sin Content-Type. | Pasa en perfil prod. |
| plainTextLoginIsUnsupported | POST text/plain: 415 con cuerpo vacio y sin Content-Type. | Pasa en perfil prod. |
| healthIsPublicWithoutAuthorization | GET /q/health sin credenciales: JSON 200 con status UP y checks vacios. | Pasa en perfil prod. |
| healthDoesNotValidateAnInvalidBearerToken | Salud no comprueba Bearer y no agrega un 401/403: mismo JSON 200. | Pasa en perfil prod. |
| healthStillReturnsJsonForPlainTextAccept | Accept text/plain en salud conserva JSON 200, sin extrapolar el 406 del login. | Pasa en perfil prod. |
| healthPreservesTheObservedPostWithoutBody | POST de salud sin cuerpo conserva JSON 200, sin inventar un 405. | Pasa en perfil prod. |

## Resultado y limites

Se completaron las tareas 1.1 a 1.10 con las comprobaciones anteriores. Ninguna asercion demostro una discrepancia nueva respecto de los comportamientos protegidos; no fue necesario cambiar proposal, specs o design. No se presentan como cumplidas las garantias futuras de firma, claims JWT, obligatoriedad de cliente, configuracion de secreto o ausencia de logs sensibles. Tampoco se atribuye hashing al codigo actual: esa diferencia ya consta en design.md y no se corrigio.

La consulta al Issue #12 se realizo mediante `gh issue view 12 --repo user-bcp-student02/workshop-auth-backend --json number,title,body,state,url,comments`, porque el MCP no estaba disponible. Se revisaron el cuerpo y el comentario que identifica H-03 y confirma el token Base64 legacy. No se modifico el Issue ni se inspeccionaron consumidores.

Los resultados de perfil test y prod se distinguen expresamente. Si nuevas pruebas contradicen los artefactos aprobados, se debe detener el trabajo y solicitar revision, no cambiar el contrato para obtener verde. Permanecen pendientes los grupos funcionales 2 a 7; esta evidencia no autoriza ejecutarlos.