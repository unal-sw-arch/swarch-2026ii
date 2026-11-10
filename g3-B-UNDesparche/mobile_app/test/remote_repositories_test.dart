import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:dio/dio.dart';
import 'package:mobile_app/core/network/auth_interceptor.dart';
import 'package:mobile_app/core/network/dio_client.dart';
import 'package:mobile_app/core/network/api_endpoints.dart';
import 'package:mobile_app/core/services/notification_service.dart';

void main() {
  group('Pruebas de Red y Preparacion de API Gateway (Fase 5)', () {
    test('AuthInterceptor inyecta el token Bearer en los encabezados HTTP', () {
      final interceptor = AuthInterceptor();
      interceptor.setToken('jwt_test_token_123');

      final options = RequestOptions(path: '/events/');
      final handler = RequestInterceptorHandler();

      interceptor.onRequest(options, handler);

      expect(options.headers['Authorization'], equals('Bearer jwt_test_token_123'));
      expect(options.headers['Content-Type'], equals('application/json'));
      expect(options.headers['Accept'], equals('application/json'));
    });

    test('DioClient inicializa con la URL base del API Gateway', () {
      final client = DioClient();
      expect(client.dio.options.baseUrl, equals(ApiEndpoints.baseUrl));
      expect(client.dio.options.connectTimeout, equals(const Duration(seconds: 10)));
    });

    test('NotificationService emite eventos en su stream', () async {
      final service = NotificationService();

      unawaited(expectLater(
        service.onNotification,
        emits(contains('Alerta de prueba')),
      ));

      service.notifyEventChange(
        eventTitle: 'Alerta de prueba',
        changeDescription: 'Evento reprogramado',
      );
    });
  });
}
