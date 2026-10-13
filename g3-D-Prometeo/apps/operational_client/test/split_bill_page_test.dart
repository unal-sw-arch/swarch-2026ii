import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurante_operational/models/client_order.dart';
import 'package:restaurante_operational/features/rooms/split_bill_page.dart';

void main() {
  testWidgets('split bill fits a compact phone without overflow', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });
    await tester.pumpWidget(
      MaterialApp(
        home: SplitBillPage(
          spanish: true,
          tableLabel: '1',
          order: const ClientOrder(
            id: 1,
            tableId: 1,
            tableGroupId: null,
            status: 'eating',
            description: null,
            items: [],
          ),
          onSave: (accounts) async => throw UnimplementedError(),
          onPay: (accountId, method, tipPercent, customTip) async =>
              throw UnimplementedError(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.byKey(const ValueKey('add-split-account')), findsOneWidget);
  });

  testWidgets('split payment keeps the chosen method after saving', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1200, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    const item = ClientOrderItem(
      id: 11,
      productId: 21,
      name: 'Hamburguesa',
      productDescription: null,
      quantity: 2,
      deliveredQuantity: 2,
      deliveredUnitIndexes: [0, 1],
      status: 'delivered',
      specifications: null,
      parentOrderItemId: null,
      removedIngredientIds: [],
      ingredients: [],
      categoryName: 'Hamburguesas',
      unitValue: 1001,
    );
    const original = ClientOrder(
      id: 1,
      tableId: 1,
      tableGroupId: null,
      status: 'eating',
      description: null,
      items: [item],
      total: 2002,
    );
    List<SplitAccountWrite>? savedAccounts;
    ClientPaymentMethod? chargedMethod;
    int? chargedAccountId;

    await tester.pumpWidget(
      MaterialApp(
        home: SplitBillPage(
          spanish: true,
          tableLabel: '1',
          order: original,
          onSave: (accounts) async {
            savedAccounts = accounts;
            return ClientOrder(
              id: 1,
              tableId: 1,
              tableGroupId: null,
              status: 'eating',
              description: null,
              items: const [item],
              total: 2002,
              splitAccounts: [
                ClientSplitAccount(
                  id: 51,
                  name: accounts.first.name,
                  position: accounts.first.position,
                  status: 'draft',
                  paymentMethod: null,
                  tipPercent: 0,
                  tipValue: 0,
                  subtotal: 0,
                  totalValue: 0,
                  paidAt: null,
                  allocations: [
                    ClientSplitAllocation(
                      orderItemId: 11,
                      halfUnits: accounts.first.allocations[11]!,
                    ),
                  ],
                ),
              ],
            );
          },
          onPay: (accountId, method, tipPercent, customTip) async {
            chargedAccountId = accountId;
            chargedMethod = method;
            return original;
          },
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('split-item-11')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('payment-method-transfer')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('pay-split-account')));
    await tester.pumpAndSettle();

    expect(savedAccounts?.single.allocations[11], 4);
    expect(chargedAccountId, 51);
    expect(chargedMethod, ClientPaymentMethod.transfer);
    expect(tester.takeException(), isNull);
  });
}
