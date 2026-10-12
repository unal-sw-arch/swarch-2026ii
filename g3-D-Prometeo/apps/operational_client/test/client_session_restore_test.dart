import 'package:flutter_test/flutter_test.dart';
import 'package:restaurante_operational/models/client_user.dart';
import 'package:restaurante_operational/services/client_auth_api.dart';

const _user = ClientUser(
  id: 7,
  fullName: 'Carlos Ruiz',
  username: 'carlos',
  role: 'waiter',
);

void main() {
  test('restores a valid stored employee session', () async {
    final expiresAt = DateTime.now().toUtc().add(const Duration(hours: 12));
    final restored = await restoreStoredClientSession(
      readToken: () async => 'stored-token',
      clearToken: () async => fail('A valid token must not be cleared'),
      verify: (token) async =>
          ClientSession(token: token, expiresAt: expiresAt, user: _user),
    );

    expect(restored?.token, 'stored-token');
    expect(restored?.expiresAt, expiresAt);
    expect(restored?.user.fullName, 'Carlos Ruiz');
  });

  test('clears a token explicitly rejected by the server', () async {
    var clearCalls = 0;
    final restored = await restoreStoredClientSession(
      readToken: () async => 'expired-token',
      clearToken: () async => clearCalls++,
      verify: (_) async => null,
    );

    expect(restored, isNull);
    expect(clearCalls, 1);
  });

  test('preserves a token when verification fails temporarily', () async {
    var clearCalls = 0;
    final restored = await restoreStoredClientSession(
      readToken: () async => 'still-valid-token',
      clearToken: () async => clearCalls++,
      verify: (_) async =>
          throw const ClientLoginException('CONNECTION_FAILED'),
    );

    expect(restored, isNull);
    expect(clearCalls, 0);
  });
}
