# Token de login — #12 / H-03

La emisión usa JWT con `alg=HS256`; se conserva HMAC y no se añade una
dependencia de JWT. El token contiene `sub` (usuario autenticado), `client`
(valor validado del request), `iat` y `exp`. La verificación rechaza firma
inválida, claims ausentes o mal tipados y tokens expirados.

`expiresIn` expresa `exp - iat` en segundos. `iat` y `exp` tienen precisión de
segundos; la diferencia con `exp - now` al observar la respuesta puede ser de
hasta 1 segundo por redondeo, sin extender la expiración del token.

En ejecución, configurar `AUTH_TOKEN_SECRET` con al menos 32 bytes aleatorios.
La aplicación no define un secreto de producción por defecto y debe fallar al
iniciar si la configuración falta o es demasiado corta. El valor de `%test`
en `application.properties` es exclusivo de pruebas aisladas.

El endpoint debe validar `client` y, tras autenticar al usuario, llamar a
`TokenService.issue(authenticatedUser.username(), request.client)`. Mantener
el DTO de respuesta existente. No registrar contraseñas, tokens ni secretos.
El formato heredado de `LegacyTokenUtil` está deshabilitado.