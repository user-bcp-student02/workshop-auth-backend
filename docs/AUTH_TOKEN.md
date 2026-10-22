# Token de autenticación — #12 / H-03

La emisión usa JWT compacto con firma HMAC-SHA256 (`HS256`), implementada
con las primitivas criptográficas del JDK. Se conserva HMAC y no se agrega
una dependencia JWT. El token incluye `sub` (ID del usuario autenticado),
`client` (valor validado de la solicitud), `iat` y `exp`.

`expiresIn` es `exp - iat`, expresado en segundos. Al comparar contra el
reloj de observación, se admite una diferencia máxima de un segundo por la
precisión en segundos de los claims NumericDate.

`client` debe tener entre 1 y 64 caracteres, empezar con un carácter
alfanumérico y contener únicamente caracteres alfanuméricos, punto,
guion bajo o guion.

Configurar `AUTH_TOKEN_SECRET` con al menos 32 bytes aleatorios y
`AUTH_TOKEN_TTL_SECONDS` en el entorno de ejecución. No registrar el token,
el secreto ni credenciales. El perfil `test` usa un valor aislado de
laboratorio.

La emisión antigua queda deshabilitada. Antes del despliegue, comprobar
compatibilidad con tokens ya emitidos y validar que la versión de rollback
pueda consumir los tokens nuevos. No se requiere migración de datos.