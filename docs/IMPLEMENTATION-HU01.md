# Evidencia de implementacion HU-01

Repositorio exclusivo: workshop-auth-backend. Rama: feature/HU01. Unidad de producto: [Issue #12](https://github.com/user-bcp-student02/workshop-auth-backend/issues/12). Plan: [tasks.md](../openspec/changes/hu01-signed-token-claims/tasks.md). Caracterizacion previa: [CHARACTERIZATION-HU01.md](CHARACTERIZATION-HU01.md).

## Comandos y resultados reales

| Fecha local | Comprobacion | Resultado |
| --- | --- | --- |
| 2026-10-21 17:52 | `mvn '-Dincludes=com.nimbusds:nimbus-jose-jwt' dependency:tree compile` | BUILD SUCCESS; Nimbus 10.10 resuelto, compilacion Java 21 correcta. |
| 2026-10-21 | POST https://api.osv.dev/v1/query con package Maven com.nimbusds:nimbus-jose-jwt y version 10.10 | Respuesta JSON vacia: ningun aviso devuelto para esa version en la consulta. No equivale a aprobar todas las dependencias del proyecto. |
| 2026-10-21 17:55 | `mvn '-Dtest=TokenSecretTest,AuthResourceTest' test` | BUILD SUCCESS; 16 pruebas, 0 fallos, 0 errores, 0 omitidas: 5 de secreto y 11 de caracterizacion. |
| 2026-10-21 | `mvn '-Dit.test=TokenStartupIT,AuthTransportIT' package quarkus:build failsafe:integration-test failsafe:verify` | Surefire 16 pruebas y Failsafe 12 pruebas sin fallos, errores ni omitidas. Tres procesos invalidos abortan antes de escuchar HTTP, sin divulgar la configuracion; prod con clave sintetica valida inicia. |
| 2026-10-21 | `mvn '-Dtest=TokenServiceTest,AuthResourceTest' test`, antes de implementar emision | Fase roja prevista: 3 errores UnsupportedOperationException por emision ausente; las 11 pruebas de caracterizacion pasan. No es un resultado final aceptable. |
| 2026-10-21 18:03 | Mismo comando tras implementar emision | BUILD SUCCESS; 15 pruebas, 0 fallos/errores/omitidas. Cuatro pruebas unitarias de emision y 11 de caracterizacion. Clock fijo 2026-10-21T00:00:00Z; igualdad de duracion y observacion a +2 segundos sin extender exp. Payload solo sub/client/role/iat/exp. |
| 2026-10-21 | `mvn '-Dtest=TokenServiceTest' test`, antes del verificador | Fase roja prevista: 11 pruebas, 6 fallos y 1 error por UnsupportedOperationException; las 4 pruebas de emision siguen pasando. |
| 2026-10-21 18:08 | `mvn '-Dtest=TokenServiceTest' test`, tras implementar verificacion | BUILD SUCCESS: 11 pruebas, 0 fallos/errores/omitidas; firma autorizada, alteraciones, clave distinta, none/HS384, claims ausentes o tipos invalidos, legacy y limites exp-1/exp/exp+1. |
| 2026-10-21 | `mvn '-Dtest=AuthResourceTest#requiresClientAfterSuccessfulAuthentication+preservesCredentialErrorsBeforeClientValidation' test`, antes de integrar | Fase roja prevista: 2 pruebas, 1 fallo (400 esperado frente a 200 legacy), 0 errores; la prioridad de credenciales ya pasa. |
| 2026-10-21 18:12 | `mvn '-Dtest=AuthResourceTest,TokenServiceTest,TokenSecretTest' test` | BUILD SUCCESS: 29 pruebas, 0 fallos/errores/omitidas; 13 HTTP/Resource, 11 tokens y 5 secreto. Solo se sustituyen expectativas de client opcional/formato legacy expresamente cambiadas por aprobacion. |
| 2026-10-21 18:15 | `mvn '-Dtest=AuthResourceTest#successfulLoginDoesNotLogSensitiveValues+authenticationAndJdbcFailuresUseSafeDiagnostics' test` | Antes del ajuste: 2 pruebas, 1 fallo por volcado JDBC. Tras mensajes fijos: BUILD SUCCESS, 2 pruebas sin fallos/errores/omitidas. Se comprueban logs originales, stdout y stderr de exito, rechazo, excepcion capturada y consulta fallida; no solo se redacta el informe. |
| 2026-10-21 18:17 | `mvn clean verify quarkus:build failsafe:integration-test failsafe:verify jacoco:report` | BUILD SUCCESS en 59.839 s. Surefire: 32 pruebas; Failsafe: 14 pruebas; total 46, 0 fallos, 0 errores, 0 omitidas. Se elimina target antes de compilar, sin tocar SQLite del laboratorio. |
| 2026-10-21 | `openspec validate hu01-signed-token-claims --strict --json` y `git diff --check` en workshop-auth-backend | Cambio valido, cero issues OpenSpec y diff sin errores de whitespace. Revision funcional en feature/HU01: emision/cliente/diagnosticos; consultas, esquema, seeds, CORS y campos HTTP protegidos sin cambios. |

Checklist local: 36/36 tareas completadas con la evidencia de este documento y la caracterizacion previa. No equivale a cierre del Issue, aprobacion del PR, CI externa verde ni validacion de consumidores. No se realiza merge, archive o cambio de rama en este paso.

## Trazabilidad de aceptacion

| Criterio del Issue / contrato | Evidencia |
| --- | --- |
| sub/client/iat/exp y autenticidad | TokenServiceTest y JWT validado con MACVerifier en AuthTransportIT; identidad SQLite, cliente exacto y rechazo de alteraciones, claves ajenas y algoritmos distintos. |
| expiresIn coherente y token expirado | Clock fijo, exp-iat=3600, margen de observacion 2 s y pruebas en exp-1/exp/exp+1 sin leeway. |
| Login y errores de autenticacion | AuthResourceTest: esquema publico preservado, validaciones/401/500, client obligatorio tras autenticar y prioridad existente. |
| Configuracion documentada y sin default | TokenSecretTest, TokenStartupIT y SIGNED-TOKENS.md; tres configuraciones invalidas impiden arranque antes de escuchar HTTP. |
| Sin valores sensibles en logs | Captura en memoria de logging/stdout/stderr de exito, errores de autenticacion, excepcion capturada y JDBC; diagnostico fijo sin consulta ni stack trace. |
| Decision tecnica | design.md y SIGNED-TOKENS.md: HS256 mediante Nimbus 10.10; no criptografia propia ni seguridad HTTP automatica. |
| Salud y errores del framework | AuthTransportIT sobre jar limpio en prod: 11 pruebas de transporte/salud/login; sin endpoints nuevos ni filtro Bearer. |

## Cobertura y Definition of Done

Reporte real target/site/jacoco/jacoco.csv del build limpio: AuthResource 27/27 lineas (100 %), TokenSecret 9/9 (100 %), TokenService 36/40 (90 %), LegacyDatabase 20/22 (90.91 %). Total del proyecto 96/102 (94.12 %). Las metricas de linea no son una afirmacion de cobertura de todos los caminos ni de seguridad integral.

| Criterio | Evaluacion |
| --- | --- |
| Contratos de este backend | CUMPLIDO - 46 pruebas locales, incluidos artefacto prod y cambios de cliente/token expresamente aprobados. |
| Cobertura critica >=85 % | CUMPLIDO - reporte JaCoCo citado para componentes modificados/priorizados. |
| Dependencia nueva | CUMPLIDO - Nimbus 10.10 fijado, compilacion y consulta OSV sin avisos para esa version. |
| CI, CodeQL y SCA global | PENDIENTE DE OTRO GATE - ejecucion externa sobre un commit/PR; no se publicaron commits ni se desactivaron controles. |
| Secure coding del alcance | CUMPLIDO - firma y verificacion del lado servidor, secreto sin default y logs comprobados. No implica sanear persistencia. |
| PR auditable | PENDIENTE DE OTRO GATE - revision humana y preparacion/publicacion del PR; este paso no crea ni fusiona PR. |
| Endpoints y consumidores de otros repositorios | NO APLICA AL ALCANCE - prohibicion explicita de leerlos o modificarlos; no se promete su validacion efectiva. |

## Revision de activacion y rollback

Se reviso el procedimiento de SIGNED-TOKENS.md/design.md contra el artefacto de laboratorio. TokenStartupIT acredita que configuracion invalida detiene el arranque y AuthTransportIT acredita la activacion con clave sintetica valida. Se retiro el emisor anterior y no hay referencias legacy en src/main; el build limpio evita clases residuales. El diff de LegacyDatabase solo cambia diagnosticos, no consultas, esquema ni seeds.

No se ensaya ni afirma restauracion real de un despliegue anterior: no se dispone de un predecesor firmado validado para ese ejercicio. En esa situacion el procedimiento exige detener emision y revision humana, nunca fallback automatico al token sin firma. No se copia ni restaura la SQLite del laboratorio y no se reutilizan claves expuestas.

## Riesgos residuales fuera del Issue

Persisten SQL concatenado, passwords en claro, CORS permisivo y errores/debug detallados del baseline; no se corrigen en este cambio ni se afirma aptitud para produccion. Firmar un token no sanea autenticacion/persistencia ni garantiza validacion por consumidores externos. Los avisos conocidos del harness de logging en Failsafe siguen visibles y no son fallos de pruebas.

## Evidencia de logs redactada

Evento observado en consulta fallida: `WARN com.bcp.workshop.auth.LegacyDatabase - Credential lookup failed`. No incluye consulta, password, token ni secreto. Las pruebas inspeccionan en memoria el token devuelto, las passwords sinteticas, el secreto sintetico y sus codificaciones reversibles sin escribirlos en la evidencia. El almacenamiento actual no maneja hashes; no se afirma que exista hashing. El cuerpo HTTP 500 conserva sus detalles por contrato y no se vuelca en logs.

## Limites de evidencia

Los gates externos de CI/CodeQL/SCA global no se declaran cumplidos por compilacion local. Las vulnerabilidades o deudas previas no se corrigen incidentalmente. No se registran tokens ni secretos reales en este documento; la configuracion de prueba es sintetica y explicita.