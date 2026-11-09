import 'package:flutter_test/flutter_test.dart';
import 'package:kds_pos/auth/session_store.dart';
import 'package:kds_pos/models/queue_item.dart';

import 'support.dart';

void main() {
  group('QueueSnapshot', () {
    test('interpreta la respuesta de GET /queue en snake_case', () {
      final snap = QueueSnapshot.fromJson({
        'items': [
          orderJson(idA, status: 'en_preparacion', position: 1, eta: 3),
          orderJson(idB, position: 2, eta: 8, type: 'en_mesa', items: []),
        ],
        'capacity': {'max': 4, 'in_preparation': 1},
        'generated_at': '2026-11-09T14:00:05Z',
      });
      expect(snap.items, hasLength(2));
      final first = snap.items.first;
      expect(first.orderId, idA);
      expect(first.shortId, 'aaaaaaaa');
      expect(first.status, OrderStatus.enPreparacion);
      expect(first.etaMinutes, 3);
      expect(first.waitMinutes, 1.5);
      expect(first.startedAt, isNull);
      expect(first.items.single.name, 'Croissant');
      expect(first.isTakeaway, isTrue);
      expect(snap.items.last.isTakeaway, isFalse);
      expect(snap.items.last.items, isEmpty);
      expect(snap.capacity.max, 4);
      expect(snap.capacity.isFull, isFalse);
      expect(snap.withStatus(OrderStatus.pendiente).single.orderId, idB);
    });

    test('la capacidad llena se detecta', () {
      expect(const Capacity(max: 2, inPreparation: 2).isFull, isTrue);
      expect(const Capacity(max: 0, inPreparation: 0).isFull, isFalse);
    });
  });

  group('Session', () {
    test('lee roles, permisos e identidad de los claims del JWT', () {
      final s = Session.fromTokens(
        accessToken: fakeJwt(email: 'ana@demo.com', roles: ['cocinero', 'despachador'], permissions: ['kitchen:read']),
        refreshToken: 'r',
      );
      expect(s.userId, userId);
      expect(s.email, 'ana@demo.com');
      expect(s.roles, ['cocinero', 'despachador']);
      expect(s.permissions, ['kitchen:read']);
      expect(s.canAccessKds, isTrue);
      expect(s.canSeeKitchen, isTrue);
      expect(s.canSeeDispatch, isTrue);
      expect(s.canUpdate, isFalse);
    });

    test('solo cocinero, despachador, supervisor y admin entran al KDS', () {
      for (final role in ['cocinero', 'despachador', 'supervisor', 'admin']) {
        expect(sessionFor(roles: [role]).canAccessKds, isTrue, reason: role);
      }
      for (final role in ['cliente', 'mesero']) {
        expect(sessionFor(roles: [role]).canAccessKds, isFalse, reason: role);
      }
    });

    test('cada rol ve su vista', () {
      expect(sessionFor(roles: ['cocinero']).canSeeDispatch, isFalse);
      expect(sessionFor(roles: ['despachador']).canSeeKitchen, isFalse);
      for (final role in ['supervisor', 'admin']) {
        final s = sessionFor(roles: [role]);
        expect(s.canSeeKitchen && s.canSeeDispatch, isTrue, reason: role);
      }
    });

    test('un texto que no es un JWT lanza FormatException', () {
      expect(() => Session.fromTokens(accessToken: 'abc'), throwsFormatException);
      expect(() => Session.fromTokens(accessToken: 'a.!!!.c'), throwsFormatException);
    });

    test('el almacenamiento solo guarda los tokens', () {
      final json = sessionFor().toJson();
      expect(json.keys, unorderedEquals(['access_token', 'refresh_token', 'token_type']));
    });
  });
}
