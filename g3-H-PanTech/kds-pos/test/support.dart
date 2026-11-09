import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:kds_pos/api/gateway_client.dart';
import 'package:kds_pos/app.dart';
import 'package:kds_pos/auth/session_controller.dart';
import 'package:kds_pos/auth/session_store.dart';

const gatewayUrl = 'http://gateway.test:8080';
const userId = '8f14e45f-ceea-467f-a0e6-1f3d3b6a1c11';

/// JWT sin firma válida (el KDS solo lee los claims; el Gateway verifica la firma).
String fakeJwt({
  String sub = userId,
  String email = 'cocina@demo.com',
  List<String> roles = const ['cocinero'],
  List<String> permissions = const ['kitchen:read', 'kitchen:update'],
  String nonce = '',
}) {
  String part(Map<String, dynamic> m) => base64Url.encode(utf8.encode(jsonEncode(m))).replaceAll('=', '');
  return '${part({'alg': 'RS256', 'typ': 'JWT'})}.'
      '${part({'sub': sub, 'email': email, 'roles': roles, 'permissions': permissions, 'level': 50, 'n': nonce})}.firma';
}

const cookRoles = ['cocinero'];
const cookPerms = ['kitchen:read', 'kitchen:update'];
const dispatcherRoles = ['despachador'];
const dispatcherPerms = ['kitchen:deliver'];
const supervisorRoles = ['supervisor'];
const supervisorPerms = ['kitchen:read', 'kitchen:update', 'kitchen:deliver'];

Map<String, dynamic> orderJson(
  String id, {
  String status = 'pendiente',
  int prep = 5,
  int position = 1,
  int eta = 5,
  String type = 'para_llevar',
  List<Map<String, dynamic>>? items,
}) =>
    {
      'order_id': id,
      'customer_id': '33333333-3333-4333-8333-333333333333',
      'order_type': type,
      'status': status,
      'prep_minutes': prep,
      'priority_score': prep.toDouble(),
      'wait_minutes': 1.5,
      'position': position,
      'eta_minutes': eta,
      'enqueued_at': '2026-11-09T14:00:00Z',
      'started_at': null,
      'ready_at': null,
      'items': items ??
          [
            {'product_id': '44444444-4444-4444-8444-444444444444', 'name': 'Croissant', 'quantity': 2, 'prep_minutes': prep},
          ],
    };

const idA = 'aaaaaaaa-1111-4111-8111-111111111111';
const idB = 'bbbbbbbb-2222-4222-8222-222222222222';
const idC = 'cccccccc-3333-4333-8333-333333333333';

/// Gateway falso que imita a Kitchen detrás del Gateway y registra cada petición.
class FakeGateway {
  FakeGateway({List<Map<String, dynamic>>? items, this.maxCapacity = 4, String? loginToken})
      : items = items ?? [],
        loginToken = loginToken ?? fakeJwt();

  final List<Map<String, dynamic>> items;
  int maxCapacity;
  String loginToken;
  bool down = false;
  int? queueStatus;
  String acceptedToken = '';
  bool enforceToken = false;
  bool refreshWorks = true;
  String refreshedToken = '';
  Map<String, dynamic>? nextPatchError;
  int? nextPatchStatusCode;
  final List<http.Request> requests = [];

  late final http.Client client = MockClient(_handle);

  int count(String method, String path) => requests.where((r) => r.method == method && r.url.path == path).length;

  http.Response _json(int status, Object body, {Map<String, String>? headers}) => http.Response(
        jsonEncode(body),
        status,
        headers: {'content-type': 'application/json; charset=utf-8', ...?headers},
      );

  http.Response _error(int status, String code, String message, [Map<String, dynamic> details = const {}]) =>
      _json(status, {'code': code, 'message': message, 'request_id': 'req-1', 'details': details});

  Future<http.Response> _handle(http.Request r) async {
    requests.add(r);
    if (down) throw http.ClientException('connection refused');
    final path = r.url.path;

    if (r.method == 'POST' && path == '/auth/login') {
      final body = jsonDecode(r.body) as Map<String, dynamic>;
      if (body['password'] == 'mala') return _error(401, 'UNAUTHENTICATED', 'Credenciales inválidas');
      return _json(200, {'access_token': loginToken, 'refresh_token': 'r1', 'token_type': 'Bearer', 'expires_in': 900});
    }
    if (r.method == 'POST' && path == '/auth/refresh') {
      if (!refreshWorks) return _error(401, 'UNAUTHENTICATED', 'Refresh inválido');
      acceptedToken = refreshedToken.isEmpty ? fakeJwt(nonce: 'refrescado') : refreshedToken;
      return _json(200, {'access_token': acceptedToken, 'refresh_token': 'r2', 'token_type': 'Bearer', 'expires_in': 900});
    }

    if (enforceToken && r.headers['Authorization'] != 'Bearer $acceptedToken') {
      return _error(401, 'UNAUTHENTICATED', 'Token inválido o vencido');
    }
    if (r.headers['Authorization'] == null) return _error(401, 'UNAUTHENTICATED', 'Falta el token de acceso');

    if (r.method == 'GET' && path == '/queue') {
      if (queueStatus != null) return _error(queueStatus!, queueStatus == 403 ? 'FORBIDDEN' : 'INTERNAL_ERROR', 'Error');
      final inPrep = items.where((i) => i['status'] == 'en_preparacion').length;
      return _json(200, {
        'items': items,
        'capacity': {'max': maxCapacity, 'in_preparation': inPrep},
        'generated_at': '2026-11-09T14:00:05Z',
      });
    }

    final match = RegExp(r'^/queue/orders/([^/]+)/(status|deliver)$').firstMatch(path);
    if (match != null) {
      final id = match.group(1)!;
      final order = items.where((i) => i['order_id'] == id).firstOrNull;
      if (order == null) return _error(404, 'NOT_FOUND', 'La comanda no existe');

      if (match.group(2) == 'status' && r.method == 'PATCH') {
        if (nextPatchError != null) {
          final status = nextPatchStatusCode ?? 409;
          final e = nextPatchError!;
          nextPatchError = null;
          nextPatchStatusCode = null;
          return _error(status, e['code'] as String, e['message'] as String, (e['details'] as Map<String, dynamic>?) ?? {});
        }
        final to = (jsonDecode(r.body) as Map<String, dynamic>)['status'] as String;
        final current = order['status'] as String;
        if (to == 'en_preparacion' && current == 'pendiente') {
          final inPrep = items.where((i) => i['status'] == 'en_preparacion').length;
          if (inPrep >= maxCapacity) {
            return _error(409, 'KITCHEN_AT_CAPACITY', 'cocina llena', {'max': maxCapacity, 'in_preparation': inPrep});
          }
          order['status'] = 'en_preparacion';
          return _json(200, order);
        }
        if (to == 'listo' && current == 'en_preparacion') {
          order['status'] = 'listo';
          return _json(200, order);
        }
        return _error(409, 'INVALID_STATE', 'Transición inválida', {'current_status': current, 'requested_status': to});
      }

      if (match.group(2) == 'deliver' && r.method == 'POST') {
        if (order['status'] != 'listo') {
          return _error(409, 'INVALID_STATE', 'Transición inválida',
              {'current_status': order['status'], 'requested_status': 'entregado'});
        }
        items.remove(order);
        return _json(200, {...order, 'status': 'entregado'});
      }
    }
    return _error(404, 'NOT_FOUND', 'Ruta no encontrada');
  }
}

/// Controladores conectados a un [FakeGateway] y a un almacenamiento en memoria.
class TestApp {
  TestApp(this.gateway, {Session? saved})
      : store = MemorySessionStore(saved),
        client = GatewayClient(baseUrl: gatewayUrl, httpClient: gateway.client) {
    session = SessionController(client: client, store: store);
  }

  final FakeGateway gateway;
  final MemorySessionStore store;
  final GatewayClient client;
  late final SessionController session;

  KdsApp widget({Duration poll = const Duration(seconds: 4)}) =>
      KdsApp(sessionController: session, client: client, pollInterval: poll);
}

Session sessionFor({List<String> roles = cookRoles, List<String> perms = cookPerms}) =>
    Session.fromTokens(accessToken: fakeJwt(roles: roles, permissions: perms), refreshToken: 'r1');

/// Avanza el reloj en pasos cortos para que se resuelvan las futuras del cliente HTTP falso.
Future<void> settle(WidgetTester tester, {int steps = 6}) async {
  for (var i = 0; i < steps; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

/// Desmonta la app para cancelar los timers del polling.
Future<void> disposeApp(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
}
