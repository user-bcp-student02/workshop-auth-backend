/*
 * Fragmento para fusionar en el AuthResource existente; conservar su flujo
 * de autenticación, DTO de usuario y formato de respuesta publicado.
 *
 * Inyectar:
 *
 *     @Inject
 *     TokenService tokenService;
 *
 * Al inicio del login, validar el client recibido en LoginRequest:
 *
 *     if (request == null || !TokenService.isValidClient(request.client)) {
 *         // Responder con el error genérico y el status definidos por
 *         // docs/ENDPOINTS.md; no devolver el mensaje de la excepción.
 *     }
 *
 * Después de autenticar correctamente y obtener el UserRecord:
 *
 *     TokenService.IssuedToken issued =
 *             tokenService.issue(authenticatedUser.username(), request.client);
 *
 * Sustituir LegacyTokenUtil.create(...) por issued.token() y conservar la
 * respuesta existente: token, expiresIn (issued.expiresIn()) y user.
 * `sub` procede del usuario autenticado; `client`, exclusivamente del request.
 * No registrar request.password, issued.token() ni AUTH_TOKEN_SECRET.
 */