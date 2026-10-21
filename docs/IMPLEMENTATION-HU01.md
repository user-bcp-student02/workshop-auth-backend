# Evidencia de implementacion HU-01

Repositorio exclusivo: workshop-auth-backend. Rama: feature/HU01. Unidad de producto: [Issue #12](https://github.com/user-bcp-student02/workshop-auth-backend/issues/12). Plan: [tasks.md](../openspec/changes/hu01-signed-token-claims/tasks.md). Caracterizacion previa: [CHARACTERIZATION-HU01.md](CHARACTERIZATION-HU01.md).

## Comandos y resultados reales

| Fecha local | Comprobacion | Resultado |
| --- | --- | --- |
| 2026-10-21 17:52 | `mvn '-Dincludes=com.nimbusds:nimbus-jose-jwt' dependency:tree compile` | BUILD SUCCESS; Nimbus 10.10 resuelto, compilacion Java 21 correcta. |
| 2026-10-21 | POST https://api.osv.dev/v1/query con package Maven com.nimbusds:nimbus-jose-jwt y version 10.10 | Respuesta JSON vacia: ningun aviso devuelto para esa version en la consulta. No equivale a aprobar todas las dependencias del proyecto. |
| 2026-10-21 17:55 | `mvn '-Dtest=TokenSecretTest,AuthResourceTest' test` | BUILD SUCCESS; 16 pruebas, 0 fallos, 0 errores, 0 omitidas: 5 de secreto y 11 de caracterizacion. |
| 2026-10-21 | `mvn '-Dit.test=TokenStartupIT,AuthTransportIT' package quarkus:build failsafe:integration-test failsafe:verify` | Surefire 16 pruebas y Failsafe 12 pruebas sin fallos, errores ni omitidas. Tres procesos invalidos abortan antes de escuchar HTTP, sin divulgar la configuracion; prod con clave sintetica valida inicia. |

## Limites de evidencia

Los gates externos de CI/CodeQL/SCA global no se declaran cumplidos por compilacion local. Las vulnerabilidades o deudas previas no se corrigen incidentalmente. No se registran tokens ni secretos reales en este documento; la configuracion de prueba es sintetica y explicita.