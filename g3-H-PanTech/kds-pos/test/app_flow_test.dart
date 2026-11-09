import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:kds_pos/auth/session_store.dart';

import 'support.dart';

Future<TestApp> launch(
  WidgetTester tester,
  FakeGateway fake, {
  Session? saved,
  Duration poll = const Duration(seconds: 4),
}) async {
  tester.view.physicalSize = const Size(1400, 900);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);

  final app = TestApp(fake, saved: saved);
  await tester.pumpWidget(app.widget(poll: poll));
  await settle(tester);
  return app;
}

Future<void> logIn(WidgetTester tester, {String password = 'secreta'}) async {
  await tester.enterText(find.byKey(const Key('email')), 'cocina@demo.com');
  await tester.enterText(find.byKey(const Key('password')), password);
  await tester.tap(find.byKey(const Key('login')));
  await settle(tester);
}

Finder card(String id) => find.byKey(Key('card-$id'));

void main() {
  group('Login', () {
    testWidgets('con contraseña incorrecta muestra el error y no entra', (tester) async {
      final fake = FakeGateway();
      final app = await launch(tester, fake);

      await logIn(tester, password: 'mala');

      expect(find.byKey(const Key('login-error')), findsOneWidget);
      expect(find.text('Correo o contraseña incorrectos.'), findsOneWidget);
      expect(find.byKey(const Key('login')), findsOneWidget);
      expect(app.store.session, isNull);
      expect(fake.count('GET', '/queue'), 0);
    });

    testWidgets('los campos vacíos no llaman al Gateway', (tester) async {
      final fake = FakeGateway();
      await launch(tester, fake);

      await tester.tap(find.byKey(const Key('login')));
      await settle(tester);

      expect(find.text('Escriba su correo'), findsOneWidget);
      expect(find.text('Escriba su contraseña'), findsOneWidget);
      expect(fake.requests, isEmpty);
    });

    testWidgets('una cuenta de cliente no entra al KDS y no se guarda la sesión', (tester) async {
      final fake = FakeGateway(loginToken: fakeJwt(roles: ['cliente'], permissions: ['orders:read_own']));
      final app = await launch(tester, fake);

      await logIn(tester);

      expect(find.textContaining('no tiene acceso al KDS'), findsOneWidget);
      expect(app.store.session, isNull);
      expect(fake.count('GET', '/queue'), 0);
    });

    testWidgets('un cocinero entra, se guarda la sesión y ve la cola', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA, status: 'en_preparacion')]);
      final app = await launch(tester, fake);

      await logIn(tester);

      expect(app.store.session, isNotNull);
      expect(app.store.session!.roles, ['cocinero']);
      expect(card(idA), findsOneWidget);
      expect(fake.requests.every((r) => r.url.origin == gatewayUrl), isTrue);
      await disposeApp(tester);
    });

    testWidgets('al cerrar sesión vuelve al login y borra el almacenamiento', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA)]);
      final app = await launch(tester, fake, saved: sessionFor());
      expect(card(idA), findsOneWidget);

      await tester.tap(find.byKey(const Key('logout')));
      await settle(tester);

      expect(find.byKey(const Key('login')), findsOneWidget);
      expect(app.store.session, isNull);
    });
  });

  group('Cola de cocina', () {
    testWidgets('muestra en preparación, pendientes por prioridad y capacidad', (tester) async {
      final fake = FakeGateway(items: [
        orderJson(idA, status: 'en_preparacion', position: 1, eta: 2),
        orderJson(idB, position: 2, eta: 5, prep: 3),
        orderJson(idC, position: 3, eta: 12, prep: 12, type: 'en_mesa'),
      ]);
      await launch(tester, fake, saved: sessionFor());

      expect(find.text('En preparación (1)'), findsOneWidget);
      expect(find.text('Pendientes (por prioridad) (2)'), findsOneWidget);
      expect(find.text('En preparación 1/4'), findsOneWidget);
      expect(find.text('#2'), findsOneWidget);
      expect(find.text('ETA 12 min'), findsOneWidget);
      expect(find.text('2 × Croissant'), findsNWidgets(3));
      expect(find.text('En mesa'), findsOneWidget);
      expect(tester.getTopLeft(card(idB)).dx, lessThan(tester.getTopLeft(card(idC)).dx));
      expect(find.byType(NavigationBar), findsNothing);
      await disposeApp(tester);
    });

    testWidgets('consulta GET /queue cada 4 segundos', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA)]);
      await launch(tester, fake, saved: sessionFor());
      expect(fake.count('GET', '/queue'), 1);

      await tester.pump(const Duration(seconds: 3));
      await settle(tester, steps: 1);
      expect(fake.count('GET', '/queue'), 1, reason: 'todavía no pasaron 4 s');

      await tester.pump(const Duration(seconds: 1, milliseconds: 100));
      await settle(tester);
      expect(fake.count('GET', '/queue'), 2);

      await tester.pump(const Duration(seconds: 4));
      await settle(tester);
      expect(fake.count('GET', '/queue'), 3);
      await disposeApp(tester);
    });

    testWidgets('los cambios del servidor aparecen en la siguiente consulta', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA)]);
      await launch(tester, fake, saved: sessionFor());
      expect(card(idB), findsNothing);

      fake.items.add(orderJson(idB, position: 2));
      await tester.pump(const Duration(seconds: 4));
      await settle(tester);

      expect(card(idB), findsOneWidget);
      await disposeApp(tester);
    });

    testWidgets('En preparación y Listo llaman al Gateway y actualizan la tarjeta', (tester) async {
      final fake = FakeGateway(items: [orderJson(idB)]);
      await launch(tester, fake, saved: sessionFor());

      await tester.tap(find.byKey(Key('start-$idB')));
      await settle(tester);

      final patch = fake.requests.firstWhere((r) => r.method == 'PATCH');
      expect(patch.url.toString(), '$gatewayUrl/queue/orders/$idB/status');
      expect(jsonDecode(patch.body), {'status': 'en_preparacion'});
      expect(find.text('Pedido #bbbbbbbb en preparación.'), findsOneWidget);
      expect(find.byKey(Key('start-$idB')), findsNothing);
      expect(find.byKey(Key('ready-$idB')), findsOneWidget);
      expect(find.text('En preparación (1)'), findsOneWidget);

      await tester.tap(find.byKey(Key('ready-$idB')));
      await settle(tester, steps: 20);

      expect(find.text('Pedido #bbbbbbbb listo para despachar.'), findsOneWidget);
      expect(find.text('Listos para despachar (1)'), findsOneWidget);
      expect(find.byKey(Key('ready-$idB')), findsNothing);
      expect(fake.count('PATCH', '/queue/orders/$idB/status'), 2);
      await disposeApp(tester);
    });

    testWidgets('con la cocina llena explica que el pedido sigue pendiente', (tester) async {
      final fake = FakeGateway(
        maxCapacity: 1,
        items: [orderJson(idA, status: 'en_preparacion'), orderJson(idB, position: 2)],
      );
      await launch(tester, fake, saved: sessionFor());
      expect(find.textContaining('cocina llena'), findsOneWidget);

      await tester.tap(find.byKey(Key('start-$idB')));
      await settle(tester);

      expect(find.textContaining('La cocina está llena (1 de 1 en preparación)'), findsOneWidget);
      expect(find.textContaining('sigue pendiente'), findsOneWidget);
      expect(find.byKey(Key('start-$idB')), findsOneWidget, reason: 'el pedido no se pierde ni cambia');
      await disposeApp(tester);
    });

    testWidgets('si otra persona cambió el pedido (409) avisa y actualiza la cola', (tester) async {
      final fake = FakeGateway(items: [orderJson(idB)]);
      await launch(tester, fake, saved: sessionFor());
      final before = fake.count('GET', '/queue');

      fake.nextPatchError = {
        'code': 'INVALID_STATE',
        'message': 'Transición inválida',
        'details': {'current_status': 'listo', 'requested_status': 'en_preparacion'},
      };
      await tester.tap(find.byKey(Key('start-$idB')));
      await settle(tester);

      expect(find.textContaining('cambió de estado (ahora está "listo")'), findsOneWidget);
      expect(fake.count('GET', '/queue'), greaterThan(before));
      await disposeApp(tester);
    });

    testWidgets('sin el permiso kitchen:update no hay botones de acción', (tester) async {
      final fake = FakeGateway(items: [orderJson(idB)]);
      await launch(tester, fake, saved: sessionFor(perms: ['kitchen:read']));

      expect(card(idB), findsOneWidget);
      expect(find.byKey(Key('start-$idB')), findsNothing);
      await disposeApp(tester);
    });
  });

  group('Errores de conexión y sesión', () {
    testWidgets('red caída: banner con los datos anteriores y reintento manual', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA)]);
      await launch(tester, fake, saved: sessionFor());
      expect(find.byKey(const Key('error-banner')), findsNothing);

      fake.down = true;
      await tester.tap(find.byKey(const Key('refresh')));
      await settle(tester);

      expect(find.byKey(const Key('error-banner')), findsOneWidget);
      expect(find.textContaining('Sin conexión con el Gateway'), findsOneWidget);
      expect(find.textContaining('Mostrando datos de las'), findsOneWidget);
      expect(card(idA), findsOneWidget, reason: 'se conservan los últimos datos');

      fake.down = false;
      await tester.tap(find.byKey(const Key('retry')));
      await settle(tester);

      expect(find.byKey(const Key('error-banner')), findsNothing);
      expect(card(idA), findsOneWidget);
      await disposeApp(tester);
    });

    testWidgets('sin red desde el inicio se ofrece reintentar', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA)])..down = true;
      await launch(tester, fake, saved: sessionFor());

      expect(find.byKey(const Key('error-banner')), findsOneWidget);
      expect(find.text('No se pudo cargar la cola.'), findsOneWidget);

      fake.down = false;
      await tester.tap(find.byKey(const Key('retry')));
      await settle(tester);
      expect(card(idA), findsOneWidget);
      await disposeApp(tester);
    });

    testWidgets('403 en la cola: avisa que la cuenta no tiene permiso', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA)])..queueStatus = 403;
      await launch(tester, fake, saved: sessionFor());

      expect(find.textContaining('no tiene permiso'), findsOneWidget);
      expect(card(idA), findsNothing);
      await disposeApp(tester);
    });

    testWidgets('401 que no se puede renovar vuelve al inicio de sesión', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA)]);
      final app = await launch(tester, fake, saved: sessionFor());
      expect(card(idA), findsOneWidget);

      fake.enforceToken = true;
      fake.acceptedToken = 'otro';
      fake.refreshWorks = false;
      await tester.tap(find.byKey(const Key('refresh')));
      await settle(tester);

      expect(find.byKey(const Key('login')), findsOneWidget);
      expect(find.text('Su sesión expiró. Inicie sesión de nuevo.'), findsOneWidget);
      expect(app.store.session, isNull);
    });

    testWidgets('401 con refresh_token válido renueva la sesión sin molestar', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA)]);
      final app = await launch(tester, fake, saved: sessionFor());

      fake.enforceToken = true;
      fake.acceptedToken = 'otro';
      await tester.tap(find.byKey(const Key('refresh')));
      await settle(tester);

      expect(fake.count('POST', '/auth/refresh'), 1);
      expect(find.byKey(const Key('login')), findsNothing);
      expect(card(idA), findsOneWidget);
      expect(app.store.session!.refreshToken, 'r2');
      await disposeApp(tester);
    });
  });

  group('Despacho', () {
    testWidgets('el despachador solo ve la vista de despacho, separada por tipo', (tester) async {
      final fake = FakeGateway(items: [
        orderJson(idA, status: 'listo', position: 1, type: 'para_llevar'),
        orderJson(idB, status: 'listo', position: 2, type: 'en_mesa'),
        orderJson(idC, status: 'en_preparacion', position: 3),
      ]);
      await launch(tester, fake, saved: sessionFor(roles: dispatcherRoles, perms: dispatcherPerms));

      expect(find.byType(NavigationBar), findsNothing);
      expect(find.text('Para llevar (1)'), findsOneWidget);
      expect(find.text('En mesa (1)'), findsOneWidget);
      expect(card(idA), findsOneWidget);
      expect(card(idB), findsOneWidget);
      expect(card(idC), findsNothing, reason: 'solo pedidos listos');
      expect(find.byKey(Key('start-$idC')), findsNothing);
      await disposeApp(tester);
    });

    testWidgets('Entregar llama a POST /queue/orders/{id}/deliver y quita el pedido', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA, status: 'listo'), orderJson(idB, status: 'listo', type: 'en_mesa')]);
      await launch(tester, fake, saved: sessionFor(roles: dispatcherRoles, perms: dispatcherPerms));

      await tester.tap(find.byKey(Key('deliver-$idA')));
      await settle(tester);

      final post = fake.requests.firstWhere((r) => r.method == 'POST' && r.url.path.endsWith('/deliver'));
      expect(post.url.toString(), '$gatewayUrl/queue/orders/$idA/deliver');
      expect(find.text('Pedido #aaaaaaaa entregado.'), findsOneWidget);
      expect(card(idA), findsNothing);
      expect(card(idB), findsOneWidget);
      await disposeApp(tester);
    });

    testWidgets('entregar un pedido que ya no está listo (409) muestra un mensaje claro', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA, status: 'listo')]);
      await launch(tester, fake, saved: sessionFor(roles: dispatcherRoles, perms: dispatcherPerms));

      fake.items.first['status'] = 'en_preparacion';
      await tester.tap(find.byKey(Key('deliver-$idA')));
      await settle(tester);

      expect(find.textContaining('cambió de estado'), findsOneWidget);
      await disposeApp(tester);
    });

    testWidgets('sin pedidos listos lo dice', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA)]);
      await launch(tester, fake, saved: sessionFor(roles: dispatcherRoles, perms: dispatcherPerms));
      expect(find.text('No hay pedidos listos para entregar.'), findsOneWidget);
      await disposeApp(tester);
    });

    testWidgets('el supervisor ve cocina y despacho y puede cambiar de pestaña', (tester) async {
      final fake = FakeGateway(items: [orderJson(idA, status: 'listo'), orderJson(idB, position: 2)]);
      await launch(tester, fake, saved: sessionFor(roles: supervisorRoles, perms: supervisorPerms));

      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.byKey(Key('start-$idB')), findsOneWidget);
      expect(find.byKey(Key('deliver-$idA')), findsNothing);

      await tester.tap(find.text('Despacho'));
      await settle(tester);

      expect(find.byKey(Key('deliver-$idA')), findsOneWidget);
      expect(card(idB), findsNothing);
      await disposeApp(tester);
    });
  });
}
