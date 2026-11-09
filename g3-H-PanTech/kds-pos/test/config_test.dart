import 'package:flutter_test/flutter_test.dart';
import 'package:kds_pos/config.dart';

void main() {
  test('sin configuración usa el puerto publicado del Gateway y 4 s de polling', () {
    final c = AppConfig.resolve();
    expect(c.gatewayUrl, AppConfig.defaultGatewayUrl);
    expect(c.pollInterval, const Duration(seconds: 4));
  });

  test('--dart-define tiene prioridad sobre el archivo', () {
    final c = AppConfig.resolve(
      define: 'http://gateway:8080/',
      file: {'gateway_url': 'http://otro:9999', 'poll_seconds': 3},
    );
    expect(c.gatewayUrl, 'http://gateway:8080');
    expect(c.pollInterval, const Duration(seconds: 3));
  });

  test('sin define se lee el archivo', () {
    final c = AppConfig.resolve(define: '', file: {'gateway_url': 'https://gw.panaderia.local'});
    expect(c.gatewayUrl, 'https://gw.panaderia.local');
  });

  test('POLL_SECONDS del define manda y se acota entre 2 y 30 s', () {
    expect(AppConfig.resolve(definePollSeconds: 5).pollInterval, const Duration(seconds: 5));
    expect(AppConfig.resolve(definePollSeconds: 1).pollInterval, const Duration(seconds: 2));
    expect(AppConfig.resolve(definePollSeconds: 600).pollInterval, const Duration(seconds: 30));
  });

  test('una URL que no es http(s) se rechaza', () {
    expect(() => AppConfig.resolve(define: 'ftp://gw'), throwsFormatException);
    expect(() => AppConfig.resolve(define: 'localhost:8080'), throwsFormatException);
    expect(() => AppConfig.resolve(define: 'http://'), throwsFormatException);
  });
}
