import 'dart:async';

import 'package:flutter/gestures.dart' show kSecondaryMouseButton;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurante_admin/services/admin_api.dart';
import 'package:restaurante_admin/features/dashboard/admin_dashboard.dart';
import 'package:restaurante_admin/features/auth/login_page.dart';
import 'package:restaurante_admin/main.dart';
import 'package:restaurante_admin/features/auth/registration_page.dart';
import 'package:restaurante_admin/features/splash/splash_screen.dart';
import 'package:restaurante_admin/features/dashboard/halls/hall_layout_page.dart';
import 'package:restaurante_admin/features/dashboard/halls/halls_page.dart';
import 'package:restaurante_admin/features/dashboard/halls/room_layout_models.dart';
import 'package:restaurante_admin/features/dashboard/menus/catalog_models.dart';
import 'package:restaurante_admin/features/dashboard/menus/menus_page.dart';
import 'package:restaurante_admin/features/dashboard/ingredients/ingredients_page.dart';
import 'package:restaurante_admin/features/dashboard/history/order_history_page.dart';
import 'package:restaurante_admin/utils/money.dart';

Future<void> completeChecksImmediately() async {}
Future<void> failChecksImmediately() async => throw Exception('not found');
Future<AdminSession?> restoreNoSession() async => null;

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
          restoreSession: restoreNoSession,
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
        restoreSession: restoreNoSession,
      ),
    );
    await tester.pump(const Duration(milliseconds: 1500));
    await tester.pumpAndSettle();

    expect(find.text('Welcome Admin'), findsOneWidget);
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

  testWidgets('uses Spanish for a Spanish device locale', (tester) async {
    tester.binding.platformDispatcher.localeTestValue = const Locale('es');
    addTearDown(tester.binding.platformDispatcher.clearLocaleTestValue);

    await tester.pumpWidget(
      const RestaurantApp(
        initialChecks: completeChecksImmediately,
        restoreSession: restoreNoSession,
      ),
    );
    await tester.pump(const Duration(milliseconds: 1500));
    await tester.pumpAndSettle();

    expect(find.text('¡Bienvenido, administrador!'), findsOneWidget);
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
        restoreSession: restoreNoSession,
      ),
    );
    await tester.pump(const Duration(milliseconds: 1500));
    await tester.pumpAndSettle();

    expect(find.text('Welcome Admin'), findsOneWidget);
    expect(find.text('Sign In'), findsOneWidget);
  });

  testWidgets('shows unavailable state when the cloud API is unreachable', (
    tester,
  ) async {
    await tester.pumpWidget(
      const RestaurantApp(
        initialChecks: failChecksImmediately,
        restoreSession: restoreNoSession,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('404'), findsOneWidget);
    expect(find.text('Service unavailable'), findsOneWidget);
    expect(find.byKey(const ValueKey('retry-cloud-check')), findsOneWidget);
    expect(find.byType(LoginPage), findsNothing);
  });

  testWidgets('crossfades between login and registration', (tester) async {
    await tester.pumpWidget(
      const RestaurantApp(
        initialChecks: completeChecksImmediately,
        restoreSession: restoreNoSession,
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('open-register')));
    await tester.pump(const Duration(milliseconds: 210));
    expect(find.byType(LoginPage), findsOneWidget);
    expect(find.byType(RegistrationPage), findsOneWidget);

    await tester.pumpAndSettle();
    expect(find.byType(LoginPage), findsNothing);
    expect(find.text('Admin Registration'), findsOneWidget);

    await tester.ensureVisible(find.byKey(const ValueKey('return-to-login')));
    await tester.tap(find.byKey(const ValueKey('return-to-login')));
    await tester.pumpAndSettle();
    expect(find.byType(LoginPage), findsOneWidget);
  });

  testWidgets('registration submits its fields and returns to login', (
    tester,
  ) async {
    var submitted = false;
    var returnedToLogin = false;
    await tester.pumpWidget(
      MaterialApp(
        home: RegistrationPage(
          strings: AppStrings.fromLocale(const Locale('en')),
          onSignIn: () => returnedToLogin = true,
          onRegisterAdmin:
              ({
                required fullName,
                required username,
                required password,
              }) async {
                expect(fullName, 'Jane Doe');
                expect(username, 'jane.doe');
                expect(password, 'a-secure-password');
                submitted = true;
                return const RegisteredAdmin(
                  id: 1,
                  fullName: 'Jane Doe',
                  username: 'jane.doe',
                  role: 'admin',
                );
              },
        ),
      ),
    );

    final fields = find.byType(TextField);
    await tester.enterText(fields.at(0), 'Jane Doe');
    await tester.enterText(fields.at(1), 'jane.doe');
    await tester.enterText(fields.at(2), 'a-secure-password');
    await tester.tap(find.byKey(const ValueKey('create-account')));
    await tester.pumpAndSettle();

    expect(submitted, isTrue);
    expect(returnedToLogin, isTrue);
    expect(find.text('Account created successfully.'), findsOneWidget);
  });

  testWidgets('dashboard adapts to desktop and mobile sizes', (tester) async {
    final strings = AppStrings.fromLocale(const Locale('en'));

    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: AdminDashboard(strings: strings, onLogout: () {}),
      ),
    );
    expect(find.text('Admin User'), findsWidgets);
    expect(find.text("Today's Performance"), findsOneWidget);
    expect(find.text('Manage menus'), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.binding.setSurfaceSize(const Size(390, 844));
    await tester.pumpAndSettle();
    expect(find.text('Admin User'), findsWidgets);
    expect(find.text("Today's Performance"), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('layout editor saves its independent state and gates edit mode', (
    tester,
  ) async {
    const emptyLayout = RoomLayoutModel(
      roomId: 1,
      roomName: 'Main Hall',
      tables: [],
      walls: [],
      groups: [],
    );
    RoomLayoutModel? submitted;
    Future<RoomLayoutModel> load({
      required String token,
      required int roomId,
    }) async {
      expect(token, 'admin-token');
      return emptyLayout;
    }

    Future<RoomLayoutModel> save({
      required String token,
      required int roomId,
      required RoomLayoutModel layout,
    }) async {
      submitted = layout;
      return layout;
    }

    await tester.binding.setSurfaceSize(const Size(1200, 850));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HallLayoutPage(
            spanish: false,
            token: 'admin-token',
            loadLayout: load,
            saveLayout: save,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Edit Layout'), findsOneWidget);
    expect(find.byKey(const ValueKey('room-layout-canvas')), findsOneWidget);
    expect(find.byKey(const ValueKey('save-layout')), findsOneWidget);

    await tester.tap(find.byTooltip('Create table'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('new-table-identifier')),
      'T-01',
    );
    await tester.tap(find.text('Create').last);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('rotate-table--1')), findsOneWidget);
    expect(find.byTooltip('Move walls'), findsNothing);

    await tester.tapAt(const Offset(900, 600));
    await tester.pump();
    expect(find.byKey(const ValueKey('rotate-table--1')), findsNothing);

    final createdTable = find.byKey(const ValueKey('room-table--1'));
    await tester.tap(createdTable);
    await tester.pump();
    expect(find.byKey(const ValueKey('rotate-table--1')), findsOneWidget);
    final rotationGesture = await tester.startGesture(
      tester.getCenter(find.byKey(const ValueKey('rotate-table--1'))),
    );
    await rotationGesture.moveBy(const Offset(18, 0));
    await tester.pump();
    await rotationGesture.moveBy(const Offset(24, 0));
    await rotationGesture.up();
    await tester.pump();
    await tester.drag(createdTable, const Offset(50, 20));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('save-layout')));
    await tester.pumpAndSettle();
    expect(submitted?.tables, hasLength(1));
    expect(submitted?.walls, isEmpty);
    expect(submitted!.tables.single.x, greaterThan(-65));
    expect(submitted!.tables.single.rotation.abs(), greaterThan(0));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HallLayoutPage(
            spanish: false,
            token: 'employee-token',
            isAdmin: false,
            loadLayout: ({required token, required roomId}) async =>
                emptyLayout,
            saveLayout: save,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Edit Layout'), findsNothing);
    expect(find.byKey(const ValueKey('save-layout')), findsNothing);
    expect(find.byKey(const ValueKey('admin-live-view-badge')), findsOneWidget);
  });

  testWidgets('layout objects support keyboard and contextual copy paste', (
    tester,
  ) async {
    const layout = RoomLayoutModel(
      roomId: 1,
      roomName: 'Main Hall',
      walls: [],
      groups: [],
      tables: [
        RoomTableModel(
          id: 1,
          identifier: '1',
          x: 0,
          y: 0,
          width: 100,
          height: 70,
        ),
      ],
    );
    await tester.binding.setSurfaceSize(const Size(1200, 850));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HallLayoutPage(
            spanish: false,
            token: 'admin-token',
            loadLayout: ({required token, required roomId}) async => layout,
            saveLayout:
                ({required token, required roomId, required layout}) async =>
                    layout,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('room-table-1')));
    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.keyC);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.keyC);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pumpAndSettle();
    expect(find.text('1 copy'), findsOneWidget);

    await tester.tap(
      find.byKey(const ValueKey('room-table--1')),
      buttons: kSecondaryMouseButton,
    );
    await tester.pumpAndSettle();
    expect(find.text('Copy'), findsOneWidget);
    await tester.tap(find.text('Copy'));
    await tester.pumpAndSettle();

    await tester.tapAt(const Offset(80, 700), buttons: kSecondaryMouseButton);
    await tester.pumpAndSettle();
    expect(find.text('Paste'), findsOneWidget);
    await tester.tap(find.text('Paste'));
    await tester.pumpAndSettle();
    expect(find.text('1 copy copy'), findsOneWidget);
  });

  testWidgets('grouping tables keeps the name dialog alive while it closes', (
    tester,
  ) async {
    const layout = RoomLayoutModel(
      roomId: 1,
      roomName: 'Main Hall',
      walls: [],
      groups: [],
      tables: [
        RoomTableModel(
          id: 1,
          identifier: '1',
          x: 0,
          y: 0,
          width: 100,
          height: 70,
        ),
        RoomTableModel(
          id: 2,
          identifier: '2',
          x: 180,
          y: 0,
          width: 100,
          height: 70,
        ),
      ],
    );
    await tester.binding.setSurfaceSize(const Size(1200, 850));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HallLayoutPage(
            spanish: false,
            token: 'admin-token',
            loadLayout: ({required token, required roomId}) async => layout,
            saveLayout:
                ({required token, required roomId, required layout}) async =>
                    layout,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('room-table-1')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('room-table-2')));
    await tester.pump();
    await tester.tap(find.byTooltip('Group tables'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Window table');
    await tester.tap(find.text('Group'));
    await tester.pumpAndSettle();

    expect(find.text('Window table'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('rooms overview shows statistics before opening an editor', (
    tester,
  ) async {
    RoomMenuConfiguration? savedConfiguration;
    const room = RoomSummary(
      id: 8,
      name: 'Terraza',
      tableCount: 12,
      orderCount: 31,
      totalSales: 82050,
      averageSale: 2647,
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HallsPage(
            spanish: false,
            token: 'admin-token',
            loadRooms: (_) async => const [room],
            createNewRoom: ({required token, required name}) async => room,
            loadMenus: (_) async => const CatalogSnapshot(
              ingredients: [],
              menus: [
                RestaurantMenu(
                  id: 4,
                  name: 'Dinner',
                  hallAssignments: [
                    MenuHallAssignment(hallId: 8, isPrimary: true),
                  ],
                  categories: [],
                ),
                RestaurantMenu(
                  id: 5,
                  name: 'Extras',
                  hallAssignments: [
                    MenuHallAssignment(hallId: 8, isPrimary: false),
                  ],
                  categories: [
                    MenuCategory(
                      id: 50,
                      menuId: 5,
                      name: 'Sides',
                      products: [
                        CatalogProduct(
                          id: 51,
                          name: 'Fries',
                          value: 8500,
                          menuId: 5,
                          categoryId: 50,
                          ingredientIds: [],
                          hallIds: [8],
                        ),
                      ],
                    ),
                  ],
                ),
              ],
            ),
            saveMenus:
                ({
                  required token,
                  required roomId,
                  required configuration,
                }) async {
                  expect(roomId, 8);
                  savedConfiguration = configuration;
                },
            editorBuilder: (selected, onBack) => Center(
              key: const ValueKey('fake-room-editor'),
              child: Text(selected.name),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Terraza'), findsOneWidget);
    expect(find.text('12'), findsOneWidget);
    expect(find.text('31'), findsOneWidget);
    expect(find.text(r'$82 050'), findsOneWidget);
    expect(find.text(r'$2 647'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('room-menus-8')));
    await tester.pumpAndSettle();
    expect(find.text('Terraza menus'), findsOneWidget);
    expect(find.text('Dinner'), findsOneWidget);
    expect(find.text('Extras'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('save-room-menus')));
    await tester.pumpAndSettle();
    expect(savedConfiguration!.primaryMenuId, 4);
    expect(savedConfiguration!.secondaryMenus, hasLength(1));
    expect(savedConfiguration!.secondaryMenus.single.menuId, 5);
    expect(savedConfiguration!.secondaryMenus.single.productIds, [51]);
    await tester.tap(find.byKey(const ValueKey('room-card-8')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('fake-room-editor')), findsOneWidget);
  });

  testWidgets('menu catalog requires a category before creating products', (
    tester,
  ) async {
    var catalog = const CatalogSnapshot(
      ingredients: [],
      menus: [
        RestaurantMenu(
          id: 4,
          name: 'Dinner',
          hallAssignments: [MenuHallAssignment(hallId: 1, isPrimary: true)],
          categories: [],
        ),
      ],
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: MenusPage(
            spanish: false,
            token: 'admin-token',
            loadCatalog: (_) async => catalog,
            loadRooms: (_) async => const [
              RoomSummary(
                id: 1,
                name: 'Main Hall',
                tableCount: 8,
                orderCount: 0,
                totalSales: 0,
                averageSale: 0,
              ),
            ],
            addCategory:
                ({
                  required token,
                  required menuId,
                  required name,
                  parentCategoryId,
                  isSpecial = false,
                }) async {
                  final category = MenuCategory(
                    id: 9,
                    menuId: menuId,
                    name: name,
                    products: const [],
                  );
                  catalog = CatalogSnapshot(
                    ingredients: catalog.ingredients,
                    menus: [
                      RestaurantMenu(
                        id: 4,
                        name: 'Dinner',
                        hallAssignments: const [
                          MenuHallAssignment(hallId: 1, isPrimary: true),
                        ],
                        categories: [category],
                      ),
                    ],
                  );
                  return category;
                },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('add-menu')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('menu-card-4')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('add-ingredient')), findsOneWidget);
    expect(find.byKey(const ValueKey('add-product')), findsNothing);

    await tester.tap(find.byKey(const ValueKey('add-category')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Starters');
    await tester.tap(find.text('Create'));
    await tester.pumpAndSettle();
    expect(find.text('Starters'), findsOneWidget);
    await tester.tap(find.text('Starters'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('add-product')), findsOneWidget);
    expect(find.byKey(const ValueKey('add-subcategory')), findsOneWidget);
  });

  testWidgets('menu products reorder optimistically by dragging', (
    tester,
  ) async {
    var catalogLoads = 0;
    List<int>? savedOrder;
    List<int>? savedCategoryOrder;
    const products = [
      CatalogProduct(
        id: 1,
        name: 'First',
        value: 10000,
        menuId: 4,
        categoryId: 9,
        ingredientIds: [],
        hallIds: [],
      ),
      CatalogProduct(
        id: 2,
        name: 'Second',
        value: 9000,
        menuId: 4,
        categoryId: 9,
        ingredientIds: [],
        hallIds: [],
      ),
    ];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: MenusPage(
            spanish: false,
            token: 'admin-token',
            loadCatalog: (_) async {
              catalogLoads++;
              return const CatalogSnapshot(
                ingredients: [],
                menus: [
                  RestaurantMenu(
                    id: 4,
                    name: 'Dinner',
                    hallAssignments: [],
                    categories: [
                      MenuCategory(
                        id: 9,
                        menuId: 4,
                        name: 'Mains',
                        products: products,
                      ),
                      MenuCategory(
                        id: 10,
                        menuId: 4,
                        name: 'Drinks',
                        products: [],
                      ),
                    ],
                  ),
                ],
              );
            },
            loadRooms: (_) async => const [],
            reorderProducts:
                ({
                  required token,
                  required categoryId,
                  required productIds,
                }) async {
                  savedOrder = productIds;
                },
            reorderCategories:
                ({
                  required token,
                  required menuId,
                  required parentCategoryId,
                  required categoryIds,
                }) async {
                  savedCategoryOrder = categoryIds;
                },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('menu-card-4')));
    await tester.pumpAndSettle();
    final categoryGesture = await tester.startGesture(
      tester.getCenter(find.byKey(const ValueKey('category-drag-handle-9'))),
    );
    await tester.pump();
    await categoryGesture.moveTo(
      tester.getCenter(find.byKey(const ValueKey('drag-category-10'))) +
          const Offset(0, 160),
    );
    await tester.pump(const Duration(milliseconds: 300));
    await categoryGesture.up();
    await tester.pumpAndSettle();
    expect(savedCategoryOrder, [10, 9]);
    expect(catalogLoads, 1);
    await tester.tap(find.text('Mains'));
    await tester.pumpAndSettle();

    final gesture = await tester.startGesture(
      tester.getCenter(find.byKey(const ValueKey('drag-product-1'))),
    );
    await tester.pump();
    await gesture.moveTo(
      tester.getCenter(find.byKey(const ValueKey('menu-product-2'))) +
          const Offset(0, 80),
    );
    await tester.pump(const Duration(milliseconds: 300));
    await gesture.up();
    await tester.pumpAndSettle();

    expect(savedOrder, [2, 1]);
    expect(catalogLoads, 1);
    expect(find.text('First'), findsOneWidget);
    expect(find.text('Second'), findsOneWidget);
  });

  testWidgets('ingredients are browsed and created inside categories', (
    tester,
  ) async {
    const category = IngredientCategory(
      id: 3,
      name: 'Vegetables',
      ingredients: [CatalogIngredient(id: 8, name: 'Tomato', categoryId: 3)],
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: IngredientsPage(
            spanish: false,
            token: 'admin-token',
            loadCatalog: (_) async => const CatalogSnapshot(
              menus: [],
              ingredients: [
                CatalogIngredient(id: 8, name: 'Tomato', categoryId: 3),
              ],
              ingredientCategories: [category],
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('add-ingredient-category')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('add-ingredient-in-category')),
      findsNothing,
    );
    await tester.tap(find.byKey(const ValueKey('ingredient-category-3')));
    await tester.pumpAndSettle();
    expect(find.text('Tomato'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('add-ingredient-in-category')),
      findsOneWidget,
    );
  });

  testWidgets('recent activity expands and closes from the barrier', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: AdminDashboard(
          strings: AppStrings.fromLocale(const Locale('en')),
          onLogout: () {},
        ),
      ),
    );

    final expand = find.byKey(const ValueKey('expand-recent-activity'));
    await tester.ensureVisible(expand);
    await tester.tap(expand);
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('close-recent-activity')), findsOneWidget);
    expect(find.text('Author'), findsNWidgets(2));
    expect(find.text('Type'), findsNWidgets(2));
    expect(find.text('Modification'), findsNWidgets(2));

    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('close-recent-activity')), findsNothing);
    expect(
      find.byKey(const ValueKey('expand-recent-activity')),
      findsOneWidget,
    );
  });

  testWidgets('revenue chart changes its time period', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1200, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: AdminDashboard(
          strings: AppStrings.fromLocale(const Locale('en')),
          onLogout: () {},
        ),
      ),
    );

    expect(find.text('Revenue: 7-day trend'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('revenue-period-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Month').last);
    await tester.pumpAndSettle();

    expect(find.text('Revenue: 6-month trend'), findsOneWidget);
    final currentMonth = const [
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec',
    ][DateTime.now().month - 1];
    expect(find.textContaining(currentMonth), findsWidgets);

    await tester.tap(find.byKey(const ValueKey('revenue-range-selector')));
    await tester.pumpAndSettle();
    final slider = find.byKey(const ValueKey('revenue-range-slider'));
    await tester.drag(slider, const Offset(500, 0));
    await tester.tap(find.byKey(const ValueKey('apply-revenue-range')));
    await tester.pumpAndSettle();

    expect(find.text('Revenue: 24-month trend'), findsOneWidget);
    expect(find.text('24 months'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('revenue-period-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Minute').last);
    await tester.pumpAndSettle();

    expect(find.text('Revenue: last 30 minutes'), findsOneWidget);
    expect(find.text('30 minutes'), findsOneWidget);
  });

  testWidgets('employee form fades in and closes from its barrier', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: AdminDashboard(
          strings: AppStrings.fromLocale(const Locale('en')),
          onLogout: () {},
        ),
      ),
    );
    await tester.tap(find.text('Employees'));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('add-employee')));
    await tester.pump(const Duration(milliseconds: 130));
    expect(find.byKey(const ValueKey('employee-name')), findsOneWidget);
    expect(find.byKey(const ValueKey('employee-password')), findsOneWidget);
    await tester.tapAt(const Offset(1180, 780));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('employee-name')), findsNothing);
  });

  testWidgets('order history cleanup selects statuses and confirms deletion', (
    tester,
  ) async {
    Set<String>? deletedStatuses;
    var summaryLoads = 0;
    const room = RoomSummary(
      id: 4,
      name: 'Terraza',
      tableCount: 8,
      orderCount: 9,
      totalSales: 100000,
      averageSale: 20000,
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: OrderHistoryPage(
            spanish: true,
            token: 'admin-token',
            loadRooms: (_) async => const [room],
            loadSummary: ({required token, required roomId}) async {
              summaryLoads++;
              return summaryLoads == 1
                  ? const AdminOrderHistorySummary(
                      waiting: 2,
                      eating: 1,
                      closed: 6,
                    )
                  : const AdminOrderHistorySummary(
                      waiting: 0,
                      eating: 1,
                      closed: 6,
                    );
            },
            loadEntries: ({required token, required roomId}) async => const [],
            deleteOrders:
                ({required token, required roomId, required statuses}) async {
                  deletedStatuses = Set.of(statuses);
                  return 2;
                },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('history-status-waiting')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('delete-order-history')));
    await tester.pumpAndSettle();
    expect(find.textContaining('2 órdenes de Terraza'), findsOneWidget);
    await tester.tap(
      find.byKey(const ValueKey('confirm-delete-order-history')),
    );
    await tester.pumpAndSettle();

    expect(deletedStatuses, {'waiting'});
    expect(summaryLoads, 2);
    expect(find.text('Se eliminaron 2 órdenes.'), findsOneWidget);
  });
}
