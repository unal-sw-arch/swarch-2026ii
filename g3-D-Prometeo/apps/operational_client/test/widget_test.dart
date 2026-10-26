import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurante_operational/features/auth/login_page.dart';
import 'package:restaurante_operational/main.dart';
import 'package:restaurante_operational/features/splash/splash_screen.dart';
import 'package:restaurante_operational/models/client_user.dart';
import 'package:restaurante_operational/models/client_room.dart';
import 'package:restaurante_operational/models/client_order.dart';
import 'package:restaurante_operational/services/client_realtime.dart';
import 'package:restaurante_operational/features/rooms/rooms_page.dart';
import 'package:restaurante_operational/features/rooms/live_room_page.dart';
import 'package:restaurante_operational/features/rooms/order_editor_dialog.dart';
import 'package:restaurante_operational/features/rooms/delivery_order_dialog.dart';
import 'package:restaurante_operational/features/rooms/bill_order_dialog.dart';
import 'package:restaurante_operational/features/rooms/daily_orders_dialog.dart';
import 'package:restaurante_operational/utils/money.dart';

Future<void> completeChecksImmediately() async {}
Future<bool> reconnectFails() async => false;
Future<bool> reconnectNeverFinishes() => Completer<bool>().future;
Future<ClientSession?> restoreNoClientSession() async => null;

void main() {
  test('formats stored pesos directly with spaces', () {
    expect(formatPesos(1800), r'$1 800');
    expect(formatPesos(123400), r'$123 400');
  });
  testWidgets('splash waits until initial checks finish', (tester) async {
    final checks = Completer<void>();

    await tester.pumpWidget(
      MaterialApp(
        home: SplashScreen(
          strings: AppStrings.fromLocale(const Locale('en')),
          initialChecks: () => checks.future,
          restoreSession: restoreNoClientSession,
        ),
      ),
    );

    expect(find.byKey(const ValueKey('splash-logo')), findsOneWidget);
    expect(find.byType(LoginPage), findsNothing);
    await tester.pump(const Duration(seconds: 2));
    expect(find.byType(LoginPage), findsNothing);

    checks.complete();
    await tester.pump();
    await tester.pumpAndSettle();
    expect(find.byType(LoginPage), findsOneWidget);
  });

  testWidgets('login form animates and toggles password visibility', (
    tester,
  ) async {
    await tester.pumpWidget(
      const RestaurantApp(
        initialChecks: completeChecksImmediately,
        restoreSession: restoreNoClientSession,
      ),
    );
    await tester.pump(const Duration(milliseconds: 1500));
    await tester.pumpAndSettle();

    expect(find.text('Welcome!'), findsOneWidget);
    expect(find.text('Username'), findsOneWidget);
    expect(find.text('Password'), findsOneWidget);
    expect(find.text('Sign In'), findsOneWidget);

    await tester.enterText(find.byType(TextField).first, 'restaurant.user');
    await tester.pumpAndSettle();
    expect(find.text('restaurant.user'), findsOneWidget);

    final passwordField = find.byType(TextField).last;
    expect(tester.widget<TextField>(passwordField).obscureText, isTrue);

    await tester.tap(find.bySemanticsLabel('Show password'));
    await tester.pumpAndSettle();
    expect(tester.widget<TextField>(passwordField).obscureText, isFalse);
  });

  testWidgets('successful login keeps its fields while parent transitions', (
    tester,
  ) async {
    ClientSession? authenticatedSession;
    await tester.pumpWidget(
      MaterialApp(
        home: LoginPage(
          strings: AppStrings.fromLocale(const Locale('en')),
          authenticate: (username, password) async => ClientSession(
            token: 'employee-token',
            expiresAt: DateTime.utc(2026, 8, 31),
            user: const ClientUser(
              id: 7,
              fullName: 'Daniel Tellez',
              username: 'daniel',
              role: 'cashier',
            ),
          ),
          onAuthenticated: (session) => authenticatedSession = session,
        ),
      ),
    );

    await tester.enterText(find.byType(TextField).first, 'daniel');
    await tester.enterText(find.byType(TextField).last, 'a-secure-password');
    await tester.tap(find.text('Sign In'));
    await tester.pump();

    expect(authenticatedSession?.user.role, 'cashier');
    final fields = tester
        .widgetList<TextField>(find.byType(TextField))
        .toList();
    expect(fields.first.controller?.text, 'daniel');
    expect(fields.last.controller?.text, 'a-secure-password');
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('a reachable cloud service opens login', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SplashScreen(
          strings: AppStrings.fromLocale(const Locale('en')),
          initialChecks: completeChecksImmediately,
          restoreSession: restoreNoClientSession,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(LoginPage), findsOneWidget);
  });

  testWidgets('a valid stored session opens rooms and can log out', (
    tester,
  ) async {
    final session = ClientSession(
      token: 'stored-token',
      expiresAt: DateTime.now().add(const Duration(hours: 12)),
      user: const ClientUser(
        id: 7,
        fullName: 'Carlos Ruiz',
        username: 'carlos',
        role: 'waiter',
      ),
    );
    var logoutCalls = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: SplashScreen(
          strings: AppStrings.fromLocale(const Locale('en')),
          initialChecks: completeChecksImmediately,
          restoreSession: () async => session,
          logout: (_) async => logoutCalls++,
          roomsBuilder: (_, _, onLogout) => Scaffold(
            body: TextButton(
              key: const ValueKey('restored-session-logout'),
              onPressed: onLogout,
              child: const Text('Restored rooms'),
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));

    expect(find.text('Restored rooms'), findsOneWidget);
    expect(find.byType(LoginPage), findsNothing);
    await tester.tap(find.byKey(const ValueKey('restored-session-logout')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));
    expect(logoutCalls, 1);
    expect(find.byType(LoginPage), findsOneWidget);
  });

  testWidgets('an unavailable cloud service exposes retry', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SplashScreen(
          strings: AppStrings.fromLocale(const Locale('en')),
          initialChecks: () async => throw Exception('offline'),
          restoreSession: restoreNoClientSession,
        ),
      ),
    );

    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('cloud-unavailable')), findsOneWidget);
    expect(find.byKey(const ValueKey('retry-cloud-check')), findsOneWidget);
  });

  testWidgets('uses Spanish for a Spanish device locale', (tester) async {
    tester.binding.platformDispatcher.localeTestValue = const Locale('es');
    addTearDown(tester.binding.platformDispatcher.clearLocaleTestValue);

    await tester.pumpWidget(
      const RestaurantApp(
        initialChecks: completeChecksImmediately,
        restoreSession: restoreNoClientSession,
      ),
    );
    await tester.pump(const Duration(milliseconds: 1500));
    await tester.pumpAndSettle();

    expect(find.text('¡Bienvenido!'), findsOneWidget);
    expect(find.text('Usuario'), findsOneWidget);
    expect(find.text('Contraseña'), findsOneWidget);
    expect(find.text('Ingresar'), findsOneWidget);
    expect(find.bySemanticsLabel('Mostrar contraseña'), findsOneWidget);
  });

  testWidgets('falls back to English for unsupported locales', (tester) async {
    tester.binding.platformDispatcher.localeTestValue = const Locale('fr');
    addTearDown(tester.binding.platformDispatcher.clearLocaleTestValue);

    await tester.pumpWidget(
      const RestaurantApp(
        initialChecks: completeChecksImmediately,
        restoreSession: restoreNoClientSession,
      ),
    );
    await tester.pump(const Duration(milliseconds: 1500));
    await tester.pumpAndSettle();

    expect(find.text('Welcome!'), findsOneWidget);
    expect(find.text('Sign In'), findsOneWidget);
  });

  testWidgets('employee sees only assigned rooms and can open live view', (
    tester,
  ) async {
    final session = ClientSession(
      token: 'employee-token',
      expiresAt: DateTime.utc(2026, 8, 31),
      user: const ClientUser(
        id: 7,
        fullName: 'Carlos Ruiz',
        username: 'carlos',
        role: 'waiter',
      ),
    );
    final realtime = ClientRealtimeService(session);
    await tester.pumpWidget(
      MaterialApp(
        home: RoomsPage(
          session: session,
          spanish: true,
          realtime: realtime,
          loadRooms: (_) async => const [
            ClientRoomSummary(id: 3, name: 'Terraza', tableCount: 8),
          ],
          liveRoomBuilder: (_, room, realtimeService) =>
              Scaffold(body: Text('Live: ${room.name}')),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Carlos Ruiz'), findsOneWidget);
    expect(find.text('Terraza'), findsOneWidget);
    expect(find.text('8 mesas'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('assigned-room-3')));
    await tester.pumpAndSettle();
    expect(find.text('Live: Terraza'), findsOneWidget);

    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    await realtime.dispose();
  });

  testWidgets('live room exposes view controls and floating work modes', (
    tester,
  ) async {
    var menuLoads = 0;
    final session = ClientSession(
      token: 'employee-token',
      expiresAt: DateTime.utc(2026, 8, 31),
      user: const ClientUser(
        id: 7,
        fullName: 'Carlos Ruiz',
        username: 'carlos',
        role: 'waiter',
      ),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: LiveRoomPage(
          session: session,
          room: const ClientRoomSummary(id: 3, name: 'Terraza', tableCount: 1),
          spanish: true,
          loadOrders: ({required session, required roomId}) async => const [
            ClientOrder(
              id: 8,
              tableId: 13,
              tableGroupId: null,
              status: 'waiting',
              description: null,
              items: [],
              total: 23500,
            ),
          ],
          loadMenus: ({required session, required roomId}) async {
            menuLoads++;
            return const [];
          },
          loadLayout: ({required session, required roomId}) async =>
              const LiveRoomLayout(
                roomId: 3,
                roomName: 'Terraza',
                groups: [],
                walls: [],
                tables: [
                  LiveRoomTable(
                    id: 13,
                    identifier: '13',
                    x: -1300,
                    y: -700,
                    width: 100,
                    height: 70,
                    rotation: 0,
                    status: 'waiting',
                  ),
                ],
              ),
          saveLayout:
              ({required session, required roomId, required layout}) async =>
                  layout,
        ),
      ),
    );
    await tester.pumpAndSettle();

    // The order catalog is prefetched once when the room opens so tapping a
    // table never has to wait for this request.
    expect(menuLoads, 1);

    expect(find.byKey(const ValueKey('live-mode-edit')), findsOneWidget);
    expect(find.byKey(const ValueKey('live-mode-bill')), findsOneWidget);
    expect(find.byKey(const ValueKey('live-mode-select')), findsOneWidget);
    expect(find.byKey(const ValueKey('external-orders')), findsOneWidget);
    expect(find.byKey(const ValueKey('live-table-13')), findsOneWidget);
    expect(find.byKey(const ValueKey('active-room-total')), findsOneWidget);
    expect(find.text(r'$23 500'), findsOneWidget);
    final toolbarCenter = tester.getCenter(
      find.byKey(const ValueKey('live-bottom-tools')),
    );
    expect(toolbarCenter.dx, closeTo(400, 1));

    await tester.tap(find.byKey(const ValueKey('live-mode-edit')));
    await tester.pump();
    expect(menuLoads, 1);
    expect(
      tester
          .widget<IconButton>(find.byKey(const ValueKey('live-mode-edit')))
          .isSelected,
      isTrue,
    );
    expect(
      tester
          .widget<IconButton>(find.byKey(const ValueKey('live-mode-select')))
          .isSelected,
      isFalse,
    );
    await tester.tap(find.byKey(const ValueKey('live-table-13')));
    await tester.pumpAndSettle();
    expect(menuLoads, 1);
    expect(find.byType(OrderEditorDialog), findsOneWidget);
    expect(find.text('Mesa esperando'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('close-order-editor')));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('live-mode-select')));
    await tester.tap(find.byKey(const ValueKey('live-table-13')));
    await tester.pumpAndSettle();
    expect(find.text('Entrega · Mesa 13'), findsOneWidget);
    expect(find.text('Este pedido no tiene productos.'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('close-order-delivery')));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('rotate-live-room')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('fit-live-room')));
    await tester.pump();
    expect(tester.takeException(), isNull);
  });

  testWidgets('a rapid double tap opens only one order editor', (tester) async {
    final menus = Completer<List<ClientRoomMenu>>();
    final session = ClientSession(
      token: 'employee-token',
      expiresAt: DateTime.utc(2026, 8, 31),
      user: const ClientUser(
        id: 7,
        fullName: 'Carlos Ruiz',
        username: 'carlos',
        role: 'waiter',
      ),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: LiveRoomPage(
          session: session,
          room: const ClientRoomSummary(id: 3, name: 'Terraza', tableCount: 1),
          spanish: true,
          loadOrders: ({required session, required roomId}) async => const [],
          loadMenus: ({required session, required roomId}) => menus.future,
          loadLayout: ({required session, required roomId}) async =>
              const LiveRoomLayout(
                roomId: 3,
                roomName: 'Terraza',
                groups: [],
                walls: [],
                tables: [
                  LiveRoomTable(
                    id: 13,
                    identifier: '13',
                    x: 0,
                    y: 0,
                    width: 100,
                    height: 70,
                    rotation: 0,
                    status: 'available',
                  ),
                ],
              ),
          saveLayout:
              ({required session, required roomId, required layout}) async =>
                  layout,
        ),
      ),
    );
    await tester.pump();
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('live-mode-edit')));
    await tester.pump();
    final table = find.byKey(const ValueKey('live-table-13'));
    await tester.tap(table);
    await tester.tap(table, warnIfMissed: false);
    await tester.pump();

    menus.complete(const []);
    await tester.pumpAndSettle();

    expect(find.byType(OrderEditorDialog, skipOffstage: false), findsOneWidget);
  });

  testWidgets('billing cycles payment methods and groups identical products', (
    tester,
  ) async {
    ClientPaymentMethod? billedWith;
    const product = ClientOrderItem(
      id: 1,
      productId: 20,
      name: 'Hamburguesa',
      categoryName: 'Hamburguesas',
      productDescription: null,
      quantity: 2,
      deliveredQuantity: 2,
      deliveredUnitIndexes: [0, 1],
      status: 'delivered',
      specifications: null,
      parentOrderItemId: null,
      removedIngredientIds: [],
      ingredients: [],
      unitValue: 14000,
    );
    await tester.pumpWidget(
      MaterialApp(
        home: BillOrderDialog(
          spanish: true,
          tableLabel: '4',
          order: const ClientOrder(
            id: 9,
            tableId: 4,
            tableGroupId: null,
            status: 'eating',
            description: null,
            items: [
              product,
              ClientOrderItem(
                id: 2,
                productId: 20,
                name: 'Hamburguesa',
                categoryName: 'Hamburguesas',
                productDescription: null,
                quantity: 1,
                deliveredQuantity: 1,
                deliveredUnitIndexes: [0],
                status: 'delivered',
                specifications: null,
                parentOrderItemId: null,
                removedIngredientIds: [],
                ingredients: [],
                unitValue: 14000,
              ),
            ],
          ),
          productsById: const {},
          onBill: (method) async => billedWith = method,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Hamburguesa'), findsOneWidget);
    expect(find.text(r'3 × $14 000'), findsOneWidget);
    expect(find.text(r'$42 000'), findsNWidgets(2));

    await tester.tap(find.byKey(const ValueKey('payment-method-transfer')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('payment-method-card')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('payment-method-cash')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('submit-order-bill')));
    await tester.pump();
    expect(billedWith, ClientPaymentMethod.cash);
  });

  testWidgets('daily history only displays billed orders', (tester) async {
    ClientOrder order(
      int id,
      String status,
      String label,
      int total, {
      ClientPaymentMethod? paymentMethod,
    }) => ClientOrder(
      id: id,
      tableId: id,
      tableGroupId: null,
      status: status,
      description: null,
      items: const [],
      tableLabel: label,
      total: total,
      paymentMethod: paymentMethod,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: DailyOrdersDialog(
          spanish: true,
          orders: [
            order(1, 'waiting', 'Pendiente', 12000),
            order(2, 'eating', 'Comiendo', 18000),
            order(
              3,
              'closed',
              'Pagada',
              25000,
              paymentMethod: ClientPaymentMethod.cash,
            ),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Mesa Pagada'), findsOneWidget);
    expect(find.text('Mesa Pendiente'), findsNothing);
    expect(find.text('Mesa Comiendo'), findsNothing);
    expect(find.text(r'$25 000'), findsNWidgets(2));
    expect(find.text(r'$55 000'), findsNothing);
    expect(find.text('Efectivo'), findsNWidgets(2));

    await tester.tap(find.byKey(const ValueKey('history-payment-transfer')));
    await tester.pumpAndSettle();
    expect(find.text('Mesa Pagada'), findsNothing);
  });

  testWidgets('delivery view preserves item details and linked additions', (
    tester,
  ) async {
    var deliveredNormal = 0;
    var deliveredSpecial = 0;

    ClientOrder order() => ClientOrder(
      id: 8,
      tableId: 13,
      tableGroupId: null,
      status: deliveredNormal == 1 && deliveredSpecial == 1
          ? 'eating'
          : 'waiting',
      description: 'Pedido de la ventana',
      items: [
        ClientOrderItem(
          id: 101,
          productId: 20,
          name: 'Hamburguesa',
          categoryName: 'Hamburguesas',
          productDescription: 'Carne y pan artesanal',
          quantity: 1,
          deliveredQuantity: deliveredNormal,
          deliveredUnitIndexes: deliveredNormal == 1 ? const [0] : const [],
          status: deliveredNormal == 1 ? 'delivered' : 'ordered',
          specifications: 'Término medio',
          parentOrderItemId: null,
          removedIngredientIds: const [7],
          ingredients: const [ClientProductIngredient(id: 7, name: 'Cebolla')],
        ),
        ClientOrderItem(
          id: 102,
          productId: 21,
          name: 'Tocineta',
          categoryName: 'Adiciones',
          productDescription: 'Porción crujiente',
          quantity: 1,
          deliveredQuantity: deliveredSpecial,
          deliveredUnitIndexes: deliveredSpecial == 1 ? const [0] : const [],
          status: deliveredSpecial == 1 ? 'delivered' : 'ordered',
          specifications: 'Muy tostada',
          parentOrderItemId: 101,
          removedIngredientIds: const [],
          ingredients: const [],
        ),
      ],
    );

    await tester.pumpWidget(
      MaterialApp(
        home: DeliveryOrderDialog(
          spanish: true,
          tableLabel: '13',
          initialOrder: order(),
          onDeliver: (itemId, unitIndex) async {
            expect(unitIndex, 0);
            if (itemId == 101) deliveredNormal++;
            if (itemId == 102) deliveredSpecial++;
            return order();
          },
          onUndoDelivery: (itemId, unitIndex) async => order(),
          onEditOrder: () {},
        ),
      ),
    );

    expect(find.text('Hamburguesa'), findsOneWidget);
    expect(find.text('Tocineta'), findsOneWidget);
    expect(find.text('Hamburguesas'), findsOneWidget);
    expect(find.text('Adiciones'), findsOneWidget);
    expect(find.text('Adiciones asociadas'), findsOneWidget);
    expect(find.textContaining('Carne y pan artesanal'), findsOneWidget);
    expect(find.textContaining('Término medio'), findsOneWidget);
    expect(find.text('Sin Cebolla'), findsOneWidget);
    expect(find.textContaining('Porción crujiente'), findsOneWidget);
    expect(find.textContaining('Muy tostada'), findsOneWidget);

    await tester.tap(
      find.byKey(const ValueKey('deliver-order-item-101-unit-0')),
    );
    await tester.pumpAndSettle();
    expect(deliveredNormal, 1);
    expect(find.textContaining('ahora está comiendo'), findsNothing);

    await tester.tap(
      find.byKey(const ValueKey('deliver-order-item-102-unit-0')),
    );
    await tester.pumpAndSettle();
    expect(deliveredSpecial, 1);
    expect(find.textContaining('ahora está comiendo'), findsOneWidget);
  });

  testWidgets('order editor navigates categories and submits quantities', (
    tester,
  ) async {
    List<OrderItemWrite>? submitted;
    await tester.binding.setSurfaceSize(const Size(1200, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: OrderEditorDialog(
          spanish: true,
          tableLabel: '13',
          existingOrder: null,
          menus: const [
            ClientRoomMenu(
              id: 1,
              name: 'Principal',
              isPrimary: true,
              categories: [
                ClientMenuCategory(
                  id: 10,
                  name: 'Platos',
                  parentCategoryId: null,
                  isSpecial: false,
                  products: [
                    ClientMenuProduct(
                      id: 20,
                      name: 'Hamburguesa',
                      description: 'Carne y pan',
                      value: 1800,
                      ingredients: [],
                    ),
                  ],
                ),
                ClientMenuCategory(
                  id: 11,
                  name: 'Adiciones',
                  parentCategoryId: null,
                  isSpecial: true,
                  products: [
                    ClientMenuProduct(
                      id: 21,
                      name: 'Tocineta',
                      description: 'Porción adicional',
                      value: 300,
                      ingredients: [],
                    ),
                  ],
                ),
                ClientMenuCategory(
                  id: 12,
                  name: 'Hamburguesas',
                  parentCategoryId: 10,
                  isSpecial: false,
                  products: [],
                ),
                ClientMenuCategory(
                  id: 13,
                  name: 'Toppings',
                  parentCategoryId: 11,
                  isSpecial: true,
                  products: [
                    ClientMenuProduct(
                      id: 22,
                      name: 'Queso extra',
                      description: null,
                      value: 500,
                      ingredients: [],
                    ),
                  ],
                ),
              ],
            ),
          ],
          onSubmit: (description, items) async => submitted = items,
        ),
      ),
    );

    await tester.enterText(
      find.byKey(const ValueKey('order-product-search')),
      'hamb',
    );
    await tester.pump();
    expect(find.byKey(const ValueKey('order-product-20')), findsOneWidget);
    expect(find.text('Platos'), findsOneWidget);
    expect(find.textContaining('Principal ›'), findsNothing);
    expect(find.textContaining('#10'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('order-product-20')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('special-category-11')), findsOneWidget);
    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();
    await tester.binding.handlePopRoute();
    await tester.pump();
    expect(
      tester
          .widget<TextField>(find.byKey(const ValueKey('order-product-search')))
          .controller!
          .text,
      isEmpty,
    );
    expect(find.byKey(const ValueKey('close-order-editor')), findsOneWidget);

    expect(find.byKey(const ValueKey('order-category-11')), findsOneWidget);
    expect(
      tester.getTopLeft(find.byKey(const ValueKey('order-category-10'))).dy,
      tester.getTopLeft(find.byKey(const ValueKey('order-category-11'))).dy,
    );
    await tester.tap(find.byKey(const ValueKey('order-category-10')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('order-category-12')));
    await tester.pump();
    await tester.binding.handlePopRoute();
    await tester.pump();
    expect(find.byKey(const ValueKey('order-category-12')), findsOneWidget);
    await tester.binding.handlePopRoute();
    await tester.pump();
    expect(find.byKey(const ValueKey('order-category-11')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('order-category-10')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('order-product-20')));
    await tester.pumpAndSettle();
    expect(find.text('Hamburguesa'), findsWidgets);
    final specialSearch = find.byKey(const ValueKey('special-product-search'));
    expect(specialSearch, findsOneWidget);
    await tester.enterText(specialSearch, 'queso');
    await tester.pumpAndSettle();
    expect(find.text('Queso extra'), findsOneWidget);
    expect(find.byKey(const ValueKey('special-category-11')), findsNothing);
    await tester.enterText(specialSearch, '');
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('special-category-11')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('special-subcategory-13')));
    await tester.pumpAndSettle();
    expect(find.text('Queso extra'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('increase-special-product-22')));
    await tester.pump();
    await tester.tap(find.text('Guardar cambios'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('increase-product-20')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('toggle-selected-products')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('expanded-order-line-custom:1')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('expanded-order-summary')),
      findsOneWidget,
    );
    await tester.tap(
      find.byKey(const ValueKey('close-expanded-order-summary')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('submit-order')));
    await tester.pumpAndSettle();

    expect(submitted, hasLength(3));
    expect(submitted![0].productId, 20);
    expect(submitted![0].quantity, 1);
    expect(submitted![1].productId, 22);
    expect(submitted![1].quantity, 1);
    expect(submitted![1].parentIndex, 0);
    expect(submitted![2].productId, 20);
    expect(submitted![2].quantity, 1);
    expect(submitted![2].specifications, isEmpty);
    expect(submitted![2].removedIngredientIds, isEmpty);
  });

  testWidgets(
    'catalog search keeps bottom controls down and transfer stays in menu view',
    (tester) async {
      var transferCalls = 0;
      const product = ClientMenuProduct(
        id: 20,
        name: 'Hamburguesa',
        description: null,
        value: 18000,
        ingredients: [],
      );
      await tester.pumpWidget(
        MaterialApp(
          home: OrderEditorDialog(
            spanish: true,
            tableLabel: '4',
            menus: const [
              ClientRoomMenu(
                id: 1,
                name: 'Principal',
                isPrimary: true,
                categories: [
                  ClientMenuCategory(
                    id: 10,
                    name: 'Hamburguesas',
                    parentCategoryId: null,
                    isSpecial: false,
                    products: [product],
                  ),
                ],
              ),
            ],
            existingOrder: const ClientOrder(
              id: 7,
              tableId: 4,
              tableGroupId: null,
              status: 'waiting',
              description: null,
              items: [
                ClientOrderItem(
                  id: 70,
                  productId: 20,
                  name: 'Hamburguesa',
                  productDescription: null,
                  quantity: 1,
                  deliveredQuantity: 0,
                  deliveredUnitIndexes: [],
                  status: 'ordered',
                  specifications: null,
                  parentOrderItemId: null,
                  removedIngredientIds: [],
                  ingredients: [],
                ),
              ],
            ),
            onTransfer: () async {
              transferCalls++;
              return false;
            },
            onSubmit: (_, _) async {},
          ),
        ),
      );

      expect(
        tester.widget<Scaffold>(find.byType(Scaffold)).resizeToAvoidBottomInset,
        isTrue,
      );
      expect(find.byKey(const ValueKey('transfer-order')), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('transfer-order')));
      await tester.pump();
      expect(transferCalls, 1);

      await tester.tap(find.byKey(const ValueKey('order-product-search')));
      await tester.pump();
      expect(
        tester.widget<Scaffold>(find.byType(Scaffold)).resizeToAvoidBottomInset,
        isFalse,
      );

      tester.testTextInput.hide();
      FocusManager.instance.primaryFocus?.unfocus();
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('toggle-selected-products')));
      await tester.pumpAndSettle();
      final expanded = find.byKey(const ValueKey('expanded-order-summary'));
      expect(expanded, findsOneWidget);
      expect(
        find.descendant(
          of: expanded,
          matching: find.byKey(const ValueKey('transfer-order')),
        ),
        findsNothing,
      );
    },
  );

  testWidgets('order search combines unordered product and category terms', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: OrderEditorDialog(
          spanish: true,
          tableLabel: '2',
          menus: const [
            ClientRoomMenu(
              id: 1,
              name: 'Principal',
              isPrimary: true,
              categories: [
                ClientMenuCategory(
                  id: 10,
                  name: 'Perros',
                  parentCategoryId: null,
                  isSpecial: false,
                  products: [],
                ),
                ClientMenuCategory(
                  id: 11,
                  name: 'Especiales',
                  parentCategoryId: 10,
                  isSpecial: false,
                  products: [],
                ),
                ClientMenuCategory(
                  id: 12,
                  name: 'Gigante',
                  parentCategoryId: 11,
                  isSpecial: false,
                  products: [
                    ClientMenuProduct(
                      id: 80,
                      name: 'Carne',
                      description: null,
                      value: 20500,
                      ingredients: [],
                    ),
                  ],
                ),
                ClientMenuCategory(
                  id: 13,
                  name: 'Hamburguesas',
                  parentCategoryId: null,
                  isSpecial: false,
                  products: [
                    ClientMenuProduct(
                      id: 81,
                      name: 'Carne',
                      description: null,
                      value: 18000,
                      ingredients: [],
                    ),
                  ],
                ),
              ],
            ),
          ],
          existingOrder: null,
          onSubmit: (_, _) async {},
        ),
      ),
    );

    await tester.enterText(
      find.byKey(const ValueKey('order-product-search')),
      'carne gigante',
    );
    await tester.pump();
    expect(find.byKey(const ValueKey('order-product-80')), findsOneWidget);
    expect(find.byKey(const ValueKey('order-product-81')), findsNothing);
    expect(find.text('Perros › Especiales › Gigante'), findsOneWidget);
    expect(find.textContaining('#'), findsNothing);

    await tester.enterText(
      find.byKey(const ValueKey('order-product-search')),
      'gigante carne',
    );
    await tester.pump();
    expect(find.byKey(const ValueKey('order-product-80')), findsOneWidget);
    expect(find.byKey(const ValueKey('order-product-81')), findsNothing);
  });

  testWidgets('order editor removes only undelivered units', (tester) async {
    List<OrderItemWrite>? submitted;
    const product = ClientMenuProduct(
      id: 20,
      name: 'Hamburguesa',
      description: null,
      value: 18000,
      ingredients: [],
    );
    await tester.pumpWidget(
      MaterialApp(
        home: OrderEditorDialog(
          spanish: true,
          tableLabel: '4',
          menus: const [
            ClientRoomMenu(
              id: 1,
              name: 'Principal',
              isPrimary: true,
              categories: [
                ClientMenuCategory(
                  id: 10,
                  name: 'Platos',
                  parentCategoryId: null,
                  isSpecial: false,
                  products: [product],
                ),
              ],
            ),
          ],
          existingOrder: const ClientOrder(
            id: 7,
            tableId: 4,
            tableGroupId: null,
            status: 'waiting',
            description: null,
            items: [
              ClientOrderItem(
                id: 70,
                productId: 20,
                name: 'Hamburguesa',
                productDescription: null,
                quantity: 2,
                deliveredQuantity: 1,
                deliveredUnitIndexes: [0],
                status: 'ordered',
                specifications: null,
                parentOrderItemId: null,
                removedIngredientIds: [],
                ingredients: [],
              ),
            ],
          ),
          onSubmit: (_, items) async => submitted = items,
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('toggle-selected-products')));
    await tester.pumpAndSettle();
    final line = find.byKey(const ValueKey('expanded-order-line-existing:70'));
    final remove = find.descendant(
      of: line,
      matching: find.byIcon(Icons.remove_circle_outline),
    );
    expect(remove, findsOneWidget);
    await tester.tap(remove);
    await tester.pump();
    expect(
      find.descendant(
        of: line,
        matching: find.byIcon(Icons.remove_circle_outline),
      ),
      findsNothing,
    );
    await tester.tap(
      find.byKey(const ValueKey('close-expanded-order-summary')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('submit-order')));
    await tester.pumpAndSettle();
    expect(submitted, hasLength(1));
    expect(submitted!.single.quantity, 1);
  });

  testWidgets('a grouped product can be customized one unit at a time', (
    tester,
  ) async {
    List<OrderItemWrite>? submitted;
    const product = ClientMenuProduct(
      id: 30,
      name: 'Hamburguesa muy larga',
      description: null,
      value: 18000,
      ingredients: [],
    );
    await tester.pumpWidget(
      MaterialApp(
        home: OrderEditorDialog(
          spanish: true,
          tableLabel: '5',
          menus: const [
            ClientRoomMenu(
              id: 1,
              name: 'Principal',
              isPrimary: true,
              categories: [
                ClientMenuCategory(
                  id: 10,
                  name: 'Comidas',
                  parentCategoryId: null,
                  isSpecial: false,
                  products: [product],
                ),
              ],
            ),
          ],
          existingOrder: null,
          onSubmit: (_, items) async => submitted = items,
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('order-category-10')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('increase-product-30')));
    await tester.tap(find.byKey(const ValueKey('increase-product-30')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('toggle-selected-products')));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('expanded-order-line-default:30')),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('order-line-default:30-unit-0')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('order-line-default:30-unit-1')),
      findsOneWidget,
    );
    await tester.tap(
      find.byKey(const ValueKey('order-line-default:30-unit-0')),
    );
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).last, 'Sin cebolla');
    await tester.tap(find.text('Guardar cambios'));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('close-expanded-order-summary')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('submit-order')));
    await tester.pumpAndSettle();

    expect(submitted, hasLength(2));
    expect(submitted!.map((item) => item.quantity), everyElement(1));
    expect(
      submitted!.map((item) => item.specifications),
      containsAll(<String>['', 'Sin cebolla']),
    );
  });

  testWidgets('an unchanged grouped unit stays grouped', (tester) async {
    List<OrderItemWrite>? submitted;
    const product = ClientMenuProduct(
      id: 31,
      name: 'Perro americano',
      description: null,
      value: 20500,
      ingredients: [],
    );
    await tester.pumpWidget(
      MaterialApp(
        home: OrderEditorDialog(
          spanish: true,
          tableLabel: '6',
          menus: const [
            ClientRoomMenu(
              id: 1,
              name: 'Principal',
              isPrimary: true,
              categories: [
                ClientMenuCategory(
                  id: 10,
                  name: 'Perros',
                  parentCategoryId: null,
                  isSpecial: false,
                  products: [product],
                ),
              ],
            ),
          ],
          existingOrder: null,
          onSubmit: (_, items) async => submitted = items,
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('order-category-10')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('increase-product-31')));
    await tester.tap(find.byKey(const ValueKey('increase-product-31')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('toggle-selected-products')));
    await tester.pumpAndSettle();
    await tester.ensureVisible(
      find.byKey(const ValueKey('expanded-order-line-default:31')),
    );
    await tester.tap(
      find.byKey(const ValueKey('expanded-order-line-default:31')),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('order-line-default:31-unit-0')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('close-expanded-order-summary')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('submit-order')));
    await tester.pumpAndSettle();

    expect(submitted, hasLength(1));
    expect(submitted!.single.quantity, 2);
  });

  testWidgets('an entirely pending order can be submitted empty', (
    tester,
  ) async {
    List<OrderItemWrite>? submitted;
    const product = ClientMenuProduct(
      id: 20,
      name: 'Hamburguesa',
      description: null,
      value: 18000,
      ingredients: [],
    );
    await tester.pumpWidget(
      MaterialApp(
        home: OrderEditorDialog(
          spanish: true,
          tableLabel: '4',
          menus: const [
            ClientRoomMenu(
              id: 1,
              name: 'Principal',
              isPrimary: true,
              categories: [
                ClientMenuCategory(
                  id: 10,
                  name: 'Platos',
                  parentCategoryId: null,
                  isSpecial: false,
                  products: [product],
                ),
              ],
            ),
          ],
          existingOrder: const ClientOrder(
            id: 8,
            tableId: 4,
            tableGroupId: null,
            status: 'waiting',
            description: null,
            items: [
              ClientOrderItem(
                id: 80,
                productId: 20,
                name: 'Hamburguesa',
                productDescription: null,
                quantity: 1,
                deliveredQuantity: 0,
                deliveredUnitIndexes: [],
                status: 'ordered',
                specifications: null,
                parentOrderItemId: null,
                removedIngredientIds: [],
                ingredients: [],
              ),
            ],
          ),
          onSubmit: (_, items) async => submitted = items,
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('toggle-selected-products')));
    await tester.pumpAndSettle();
    final line = find.byKey(const ValueKey('expanded-order-line-existing:80'));
    await tester.tap(
      find.descendant(
        of: line,
        matching: find.byIcon(Icons.remove_circle_outline),
      ),
    );
    await tester.pump();
    expect(find.text('Eliminar pedido'), findsOneWidget);
    await tester.tap(
      find.byKey(const ValueKey('close-expanded-order-summary')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('submit-order')));
    await tester.pumpAndSettle();
    expect(submitted, isEmpty);
  });
}
