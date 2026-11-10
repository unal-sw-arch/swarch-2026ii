import 'package:dio/dio.dart';

/// Interceptor que inyecta el token JWT en cada petición y, ante un 401,
/// intenta renovar el token una vez y reintentar; si vuelve a fallar, avisa
/// para cerrar la sesión (#5).
class AuthInterceptor extends Interceptor {
  String? _authToken;

  /// Pide un token nuevo (p. ej. Firebase getIdToken(forceRefresh: true)).
  /// Devuelve null si no se pudo renovar.
  Future<String?> Function()? onRefreshToken;

  /// Se llama cuando el 401 persiste tras renovar: la sesión ya no es válida.
  Future<void> Function()? onSessionExpired;

  /// Dio para reintentar la petición con el token nuevo.
  Dio? _retryDio;

  void attachDio(Dio dio) => _retryDio = dio;

  void setToken(String? token) {
    _authToken = token;
  }

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    if (_authToken != null && _authToken!.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $_authToken';
    }
    options.headers['Content-Type'] = 'application/json';
    options.headers['Accept'] = 'application/json';
    super.onRequest(options, handler);
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final isUnauthorized = err.response?.statusCode == 401;
    // Evita bucles: solo reintenta si no es ya un reintento.
    final alreadyRetried = err.requestOptions.extra['__retried'] == true;

    if (isUnauthorized && !alreadyRetried && onRefreshToken != null) {
      final newToken = await onRefreshToken!.call();

      if (newToken != null && newToken.isNotEmpty) {
        setToken(newToken);
        final retryDio = _retryDio;
        if (retryDio != null) {
          try {
            final options = err.requestOptions
              ..extra['__retried'] = true
              ..headers['Authorization'] = 'Bearer $newToken';
            final response = await retryDio.fetch(options);
            return handler.resolve(response);
          } on DioException catch (retryErr) {
            await onSessionExpired?.call();
            return handler.next(retryErr);
          }
        }
      }

      // No se pudo renovar: sesión expirada.
      await onSessionExpired?.call();
    }

    super.onError(err, handler);
  }
}
