import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurante_operational/models/client_order.dart';
import 'package:restaurante_operational/features/rooms/daily_orders_dialog.dart';

void main() {
  testWidgets(
    'history shows each paid subaccount with its own method and items',
    (tester) async {
      final now = DateTime.now().toUtc().toIso8601String();
      final order = ClientOrder(
        id: 1,
        tableId: 1,
        tableGroupId: null,
        status: 'closed',
        description: null,
        tableLabel: 'T-01',
        total: 20000,
        items: const [
          ClientOrderItem(
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
            unitValue: 10000,
          ),
        ],
        splitAccounts: [
          ClientSplitAccount(
            id: 51,
            name: 'Cliente 1',
            position: 0,
            status: 'paid',
            paymentMethod: ClientPaymentMethod.cash,
            tipPercent: 0,
            tipValue: 0,
            subtotal: 10000,
            totalValue: 10000,
            paidAt: now,
            allocations: const [
              ClientSplitAllocation(orderItemId: 11, halfUnits: 2),
            ],
          ),
          ClientSplitAccount(
            id: 52,
            name: 'Cliente 2',
            position: 1,
            status: 'paid',
            paymentMethod: ClientPaymentMethod.card,
            tipPercent: 0,
            tipValue: 0,
            subtotal: 10000,
            totalValue: 10000,
            paidAt: now,
            allocations: const [
              ClientSplitAllocation(orderItemId: 11, halfUnits: 2),
            ],
          ),
        ],
      );
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: DailyOrdersDialog(spanish: true, orders: [order]),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Cliente 1'), findsOneWidget);
      expect(find.text('Cliente 2'), findsOneWidget);
      expect(find.text('Efectivo'), findsNWidgets(2));
      expect(find.text('Tarjeta'), findsNWidgets(2));
      expect(find.textContaining('20 000'), findsOneWidget);
      await tester.tap(find.text('Cliente 1'));
      await tester.pumpAndSettle();
      expect(find.text('1× Hamburguesa'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
