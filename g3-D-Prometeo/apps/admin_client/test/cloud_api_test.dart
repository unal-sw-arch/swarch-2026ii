import 'package:flutter_test/flutter_test.dart';
import 'package:restaurante_admin/services/cloud_api.dart';

void main() {
  test('uses the configured Cloudflare API origin', () {
    final origin = getCloudApiBaseUri();
    expect(origin.hasScheme, isTrue);
    expect(origin.host, isNotEmpty);
    expect(origin.path, '/');
  });
}
