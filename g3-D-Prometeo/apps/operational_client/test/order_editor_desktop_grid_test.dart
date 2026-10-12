import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurante_operational/models/client_order.dart';
import 'package:restaurante_operational/features/rooms/order_editor_dialog.dart';

void main() {
  testWidgets('wide order catalog uses product tiles across the row', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1300, 850);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });
    final products = List.generate(
      6,
      (index) => ClientMenuProduct(
        id: index + 1,
        name: 'Producto ${index + 1}',
        description: 'Descripción breve',
        value: 15000,
        ingredients: const [],
      ),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: OrderEditorDialog(
          spanish: true,
          tableLabel: 'T-01',
          menus: [
            ClientRoomMenu(
              id: 1,
              name: 'Principal',
              isPrimary: true,
              categories: [
                ClientMenuCategory(
                  id: 1,
                  name: 'Hamburguesas',
                  parentCategoryId: null,
                  isSpecial: false,
                  products: products,
                ),
              ],
            ),
          ],
          existingOrder: null,
          onSubmit: (description, items) async {},
        ),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('order-category-1')));
    await tester.pumpAndSettle();

    final first = tester.getTopLeft(
      find.byKey(const ValueKey('order-product-1')),
    );
    final second = tester.getTopLeft(
      find.byKey(const ValueKey('order-product-2')),
    );
    expect(first.dy, second.dy);
    expect(second.dx, greaterThan(first.dx));
    expect(tester.takeException(), isNull);
  });
}
