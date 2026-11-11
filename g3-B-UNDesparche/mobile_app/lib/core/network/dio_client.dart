import 'package:dio/dio.dart';
import '../config/app_config.dart';
import 'auth_interceptor.dart';

/// Cliente HTTP centralizado configurado con timeouts y soporte de interceptores
class DioClient {
  final Dio dio;
  final AuthInterceptor authInterceptor;

  DioClient({Dio? customDio, AuthInterceptor? customAuthInterceptor})
      : authInterceptor = customAuthInterceptor ?? AuthInterceptor(),
        dio = customDio ??
            Dio(
              BaseOptions(
                baseUrl: AppConfig.apiGatewayUrl,
                connectTimeout: const Duration(seconds: 10),
                receiveTimeout: const Duration(seconds: 10),
                sendTimeout: const Duration(seconds: 10),
              ),
            ) {
    authInterceptor.attachDio(dio);
    dio.interceptors.add(authInterceptor);
  }

  void updateToken(String? token) {
    authInterceptor.setToken(token);
  }
}
