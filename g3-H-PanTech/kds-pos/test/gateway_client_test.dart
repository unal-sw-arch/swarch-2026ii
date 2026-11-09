import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:kds_pos/api/gateway_client.dart';
import 'package:kds_pos/auth/session_store.dart';

import 'support.dart';

GatewayClient clientFor(FakeGateway fake, {Session? session}) =>
    GatewayClient(baseUrl: gatewayUrl, httpClient: fake.client)..session = session ?? sessionFor();

Future<void> expectStatus(int status, Matcher matcher, {Map<String, dynamic>? body}) async {
  final c = GatewayClient(
    baseUrl: gatewayUrl,
    httpClient: MockClient((_) async => http.Response(jsonEncode(body ?? {'code': 'X', 'message': 'm'}), status)),
  )..session = sessionFor();
  await expectLater(c.fetchQueue(), throwsA(matcher));
}

void main() {
  test('login envía correo y contraseña al Gateway y arma la sesión desde el JWT', () async {
    final fake = FakeGateway(
      loginToken: fakeJwt(email: 'ana@demo.com', roles: ['despachador'], permissions: ['kitchen:deliver']),
    );
    final client = GatewayClient(baseUrl: gatewayUrl, httpClient: fake.client);

    final session = await client.login('ana@demo.com', 'secreta');

    final req = fake.requests.single;
    expect(req.method, 'POST');
    expect(req.url.toString(), '$gatewayUrl/auth/login');
    expect(jsonDecode(req.body), {'email': 'ana@demo.com', 'password': 'secreta'});
    expect(session.email, 'ana@demo.com');
    expect(session.roles, ['despachador']);
    expect(session.refreshToken, 'r1');
  });

  test('login con credenciales malas dice "Correo o contraseña incorrectos"', () async {
    final client = GatewayClient(baseUrl: gatewayUrl, httpClient: FakeGateway().client);
    await expectLater(
      client.login('a@b.com', 'mala'),
      throwsA(isA<UnauthenticatedException>().having((e) => e.message, 'message', contains('incorrectos'))),
    );
  });

  test('una respuesta de login sin access_token se reporta como error del servidor', () async {
    final http.Client bad = MockClient((_) async => http.Response('{"token":"x"}', 200));
    final client = GatewayClient(baseUrl: gatewayUrl, httpClient: bad);
    await expectLater(client.login('a@b.com', 'x'), throwsA(isA<ServerException>()));
  });

  test('todas las peticiones van al Gateway con el token Bearer, nunca a Kitchen directo', () async {
    final fake = FakeGateway(items: [orderJson(idA, status: 'listo'), orderJson(idB, status: 'pendiente')]);
    final client = clientFor(fake, session: sessionFor(perms: supervisorPerms, roles: supervisorRoles));

    await client.fetchQueue();
    await client.setStatus(idB, 'en_preparacion');
    await client.deliver(idA);

    expect(fake.requests, hasLength(3));
    for (final r in fake.requests) {
      expect(r.url.origin, gatewayUrl);
      expect(r.url.port, isNot(8001));
      expect(r.headers['Authorization'], startsWith('Bearer '));
    }
    expect(fake.requests[1].method, 'PATCH');
    expect(fake.requests[1].url.path, '/queue/orders/$idB/status');
    expect(jsonDecode(fake.requests[1].body), {'status': 'en_preparacion'});
    expect(fake.requests[2].method, 'POST');
    expect(fake.requests[2].url.path, '/queue/orders/$idA/deliver');
  });

  test('sin sesión no se llama al Gateway y se pide iniciar sesión', () async {
    final fake = FakeGateway();
    final client = GatewayClient(baseUrl: gatewayUrl, httpClient: fake.client);
    await expectLater(client.fetchQueue(), throwsA(isA<UnauthenticatedException>()));
    expect(fake.requests, isEmpty);
  });

  group('errores traducidos a mensajes claros', () {
    test('403 → ForbiddenException', () => expectStatus(403, isA<ForbiddenException>()));
    test('404 → NotFoundException', () => expectStatus(404, isA<NotFoundException>()));
    test('429 → RateLimitedException', () => expectStatus(429, isA<RateLimitedException>()));
    test(
      '400 → ValidationException con el mensaje del servidor',
      () => expectStatus(400, isA<ValidationException>().having((e) => e.message, 'message', 'm')),
    );
    test(
      '503 → ServerException con estado',
      () => expectStatus(503, isA<ServerException>().having((e) => e.status, 'status', 503)),
    );

    test('500 con cuerpo que no es JSON → ServerException', () async {
      final c = GatewayClient(baseUrl: gatewayUrl, httpClient: MockClient((_) async => http.Response('boom', 500)))
        ..session = sessionFor();
      await expectLater(c.fetchQueue(), throwsA(isA<ServerException>()));
    });

    test('409 INVALID_STATE → InvalidStateException con el estado actual', () async {
      await expectStatus(
        409,
        isA<InvalidStateException>()
            .having((e) => e.current, 'current', 'listo')
            .having((e) => e.message, 'message', allOf(contains('cambió de estado'), contains('listo'))),
        body: {
          'code': 'INVALID_STATE',
          'message': 'x',
          'details': {'current_status': 'listo', 'requested_status': 'en_preparacion'},
        },
      );
    });

    test('409 KITCHEN_AT_CAPACITY → CapacityException que explica que el pedido sigue pendiente', () async {
      await expectStatus(
        409,
        isA<CapacityException>().having((e) => e.max, 'max', 4).having(
              (e) => e.message,
              'message',
              allOf(contains('cocina está llena'), contains('4 de 4'), contains('sigue pendiente')),
            ),
        body: {
          'code': 'KITCHEN_AT_CAPACITY',
          'message': 'x',
          'details': {'max': 4, 'in_preparation': 4},
        },
      );
    });

    test('red caída → NetworkException', () async {
      final fake = FakeGateway()..down = true;
      await expectLater(clientFor(fake).fetchQueue(), throwsA(isA<NetworkException>()));
    });

    test('un Gateway que no responde a tiempo → NetworkException', () async {
      final c = GatewayClient(
        baseUrl: gatewayUrl,
        httpClient: MockClient((_) => Completer<http.Response>().future),
        timeout: const Duration(milliseconds: 20),
      )..session = sessionFor();
      await expectLater(
        c.fetchQueue(),
        throwsA(isA<NetworkException>().having((e) => e.message, 'message', contains('a tiempo'))),
      );
    });
  });

  group('401 y renovación de sesión', () {
    test('con refresh_token válido renueva la sesión y repite la petición', () async {
      final fake = FakeGateway(items: [orderJson(idA)])
        ..enforceToken = true
        ..acceptedToken = 'otro-token';
      final client = clientFor(fake);
      final changes = <Session?>[];
      client.onSessionChanged = (s) async => changes.add(s);

      final snapshot = await client.fetchQueue();

      expect(snapshot.items, hasLength(1));
      expect(fake.count('POST', '/auth/refresh'), 1);
      expect(fake.count('GET', '/queue'), 2);
      expect(jsonDecode(fake.requests.firstWhere((r) => r.url.path == '/auth/refresh').body), {'refresh_token': 'r1'});
      expect(changes.single, isNotNull);
      expect(changes.single!.refreshToken, 'r2');
      expect(client.session!.accessToken, fake.acceptedToken);
    });

    test('si no se puede renovar avisa con null y lanza UnauthenticatedException', () async {
      final fake = FakeGateway()
        ..enforceToken = true
        ..acceptedToken = 'otro-token'
        ..refreshWorks = false;
      final client = clientFor(fake);
      final changes = <Session?>[];
      client.onSessionChanged = (s) async => changes.add(s);

      await expectLater(client.fetchQueue(), throwsA(isA<UnauthenticatedException>()));
      expect(changes, [null]);
      expect(client.session, isNull);
    });

    test('sin refresh_token un 401 cierra la sesión', () async {
      final fake = FakeGateway()
        ..enforceToken = true
        ..acceptedToken = 'otro-token';
      final client = clientFor(fake, session: Session.fromTokens(accessToken: fakeJwt()));
      final changes = <Session?>[];
      client.onSessionChanged = (s) async => changes.add(s);

      await expectLater(client.fetchQueue(), throwsA(isA<UnauthenticatedException>()));
      expect(changes, [null]);
      expect(fake.count('POST', '/auth/refresh'), 0);
    });

    test('si el Gateway cae al renovar no se cierra la sesión', () async {
      var calls = 0;
      final client = GatewayClient(
        baseUrl: gatewayUrl,
        httpClient: MockClient((r) async {
          calls++;
          if (r.url.path == '/queue') return http.Response('{"code":"UNAUTHENTICATED","message":"x"}', 401);
          throw http.ClientException('caído');
        }),
      )..session = sessionFor();
      final changes = <Session?>[];
      client.onSessionChanged = (s) async => changes.add(s);

      await expectLater(client.fetchQueue(), throwsA(isA<NetworkException>()));
      expect(changes, isEmpty);
      expect(client.session, isNotNull);
      expect(calls, 2);
    });
  });
}
