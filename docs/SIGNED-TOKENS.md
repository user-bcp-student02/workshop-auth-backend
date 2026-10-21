# Tokens firmados HU-01

Fuente: [Issue #12](https://github.com/user-bcp-student02/workshop-auth-backend/issues/12), [specs y diseno](../openspec/changes/hu01-signed-token-claims/design.md). Este documento complementa [ENDPOINTS.md](ENDPOINTS.md), que se conserva como baseline historico.

## Configuracion de laboratorio

La aplicacion requiere `AUTH_TOKEN_SECRET`: Base64 estandar de al menos 32 bytes aleatorios. No tiene valor por defecto. Ausencia, Base64 invalido o longitud insuficiente abortan el arranque sin imprimir el valor. Esta validacion no modifica el tratamiento de errores SQLite.

Desde PowerShell, generar la clave solo en el entorno del proceso, sin imprimirla ni guardarla en Git:

```powershell
$env:AUTH_TOKEN_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Configurar `AUTH_DB_URL` hacia una SQLite exclusiva del laboratorio antes de iniciar. No usar datos reales ni publicar el laboratorio en Internet. No ejecutar con `-X` ni volcar el entorno: puede contener secretos.

## Pruebas reproducibles

Surefire y Failsafe proporcionan explicitamente una clave sintetica conocida solo a sus JVM de pruebas y una base temporal protegida. La clave sintetica no es un default de la aplicacion y nunca debe utilizarse en un despliegue.

```powershell
mvn '-Dtest=TokenSecretTest,AuthResourceTest' test
```

El recurso de aislamiento sigue rechazando archivos SQLite preexistentes o ajenos a target. No se omiten pruebas ni se cambia la base del laboratorio.

## Decision criptografica

Se usa Nimbus JOSE JWT 10.10 y HS256: JWT/JWS define el formato y HMAC la firma, no son alternativas excluyentes. No se implementan parsing o criptografia propios. No se instala un filtro Bearer ni se verifica un token en salud.

HS256 requiere proteger el secreto: quien lo conoce puede firmar. JWT firmado no cifra los claims, no evita replay y no implementa revocacion. La verificacion interna no demuestra que un consumidor externo la ejecute. No se inspeccionan ni modifican otros repositorios.

## Activacion y rollback

Preparar configuracion explicita y validar el artefacto en un entorno aislado antes de activar. No hay migracion de SQLite. Ante una regresion detener emision y restaurar un artefacto firmado previamente validado, si existe, con configuracion compatible. Si no existe, no volver automaticamente al token legacy inseguro: mantener la emision detenida y pedir una decision humana. Un cambio de clave requiere nuevo login; no reutilizar claves expuestas ni implementar doble aceptacion legacy.