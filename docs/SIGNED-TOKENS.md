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
mvn test
```

El recurso de aislamiento sigue rechazando archivos SQLite preexistentes o ajenos a target. No se omiten pruebas ni se cambia la base del laboratorio.

Build limpio, integracion prod y cobertura:

```powershell
mvn clean verify quarkus:build failsafe:integration-test failsafe:verify jacoco:report
```

Usar build limpio al retirar el emisor legacy evita distribuir clases obsoletas de compilaciones anteriores. Los reportes locales estan bajo target/surefire-reports, target/failsafe-reports y target/site/jacoco.

## Decision criptografica

Se usa Nimbus JOSE JWT 10.10 y HS256: JWT/JWS define el formato y HMAC la firma, no son alternativas excluyentes. No se implementan parsing o criptografia propios. No se instala un filtro Bearer ni se verifica un token en salud.

HS256 requiere proteger el secreto: quien lo conoce puede firmar. JWT firmado no cifra los claims, no evita replay y no implementa revocacion. La verificacion interna no demuestra que un consumidor externo la ejecute. No se inspeccionan ni modifican otros repositorios.

## Contrato de login actualizado

POST /auth/login sigue siendo publico y JSON. Se conserva la validacion request/username/password y luego la autenticacion exacta. Solo tras credenciales validas se exige client string no vacio ni blancos segun String.trim(): ausencia, null, vacio o blancos devuelve 400 con `{"message":"Client is required"}`, sin token. Credenciales incorrectas conservan el 401 del baseline incluso si client es invalido.

Client no tiene lista de permitidos ni normalizacion: espacios externos y mayusculas se conservan exactamente en su claim. La identidad y el rol siguen procediendo de SQLite, no de campos extra del request.

El 200 conserva token string, expiresIn entero 3600, user y debug literal authenticated-with-legacy-flow. Token ahora es JWT/JWS HS256 y contiene sub, client, iat, exp y el dato de rol almacenado. iat/exp son segundos Unix: exp-iat=3600. El margen de coherencia al observar emision es 2 segundos, nunca una ampliacion de vigencia. El verificador rechaza desde now=exp y no acepta tokens legacy.

El formato rompe la decodificacion legacy y exige nuevo login. No se agregan refresh, sesiones, endpoints protegidos ni verificacion en /q/health. Los errores y campos publicos ajenos a esos cambios se mantienen segun el baseline.

## Activacion y rollback

Preparar configuracion explicita y validar el artefacto en un entorno aislado antes de activar. No hay migracion de SQLite. Ante una regresion detener emision y restaurar un artefacto firmado previamente validado, si existe, con configuracion compatible. Si no existe, no volver automaticamente al token legacy inseguro: mantener la emision detenida y pedir una decision humana. Un cambio de clave requiere nuevo login; no reutilizar claves expuestas ni implementar doble aceptacion legacy.