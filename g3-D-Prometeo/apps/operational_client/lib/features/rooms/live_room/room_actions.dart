// ignore_for_file: invalid_use_of_protected_member

part of '../live_room_page.dart';

extension _LiveRoomActions on _LiveRoomPageState {
  Widget _historyButton() => Material(
    elevation: 5,
    color: Colors.white,
    borderRadius: BorderRadius.circular(24),
    child: IconButton(
      key: const ValueKey('daily-orders'),
      tooltip: widget.spanish ? 'Órdenes del día' : 'Today’s orders',
      onPressed: _openDailyOrders,
      icon: const Icon(Icons.history_rounded),
    ),
  );

  Widget _roomActiveTotal() {
    final total = _orders
        .where((order) => order.status == 'waiting' || order.status == 'eating')
        .fold<int>(0, (sum, order) => sum + order.total);
    return Material(
      key: const ValueKey('active-room-total'),
      elevation: 5,
      color: Colors.white,
      borderRadius: BorderRadius.circular(18),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 11),
        child: Text(
          formatPesos(total),
          style: const TextStyle(
            color: Color(0xFF303840),
            fontSize: 16,
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }

  Widget _externalOrdersButton() {
    final count = _orders.where((order) => order.isExternal).length;
    return Badge(
      isLabelVisible: count > 0,
      label: Text('$count'),
      child: Material(
        elevation: 5,
        color: count > 0 ? const Color(0xFFFFF1B8) : Colors.white,
        borderRadius: BorderRadius.circular(24),
        child: IconButton(
          key: const ValueKey('external-orders'),
          tooltip: widget.spanish ? 'Pedidos externos' : 'External orders',
          onPressed: _openExternalOrders,
          icon: const Icon(Icons.takeout_dining_outlined),
        ),
      ),
    );
  }

  Future<void> _openExternalOrders() async {
    final active = _orders.where((order) => order.isExternal).toList();
    final selection = await Navigator.of(context).push<Object>(
      PageRouteBuilder<Object>(
        transitionDuration: const Duration(milliseconds: 180),
        reverseTransitionDuration: const Duration(milliseconds: 180),
        transitionsBuilder: (_, animation, _, child) =>
            FadeTransition(opacity: animation, child: child),
        pageBuilder: (routeContext, _, _) => Scaffold(
          backgroundColor: const Color(0xFFFAF9F6),
          body: SafeArea(
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(10, 10, 14, 10),
                  child: Row(
                    children: [
                      IconButton(
                        key: const ValueKey('close-external-orders'),
                        onPressed: () => Navigator.pop(routeContext),
                        icon: const Icon(Icons.arrow_back_rounded),
                      ),
                      const Icon(Icons.takeout_dining_outlined),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          widget.spanish
                              ? 'Pedidos externos'
                              : 'External orders',
                          style: const TextStyle(
                            fontSize: 21,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                      FilledButton.icon(
                        key: const ValueKey('create-external-order'),
                        onPressed: () => Navigator.pop(
                          routeContext,
                          const _CreateExternalOrderIntent(),
                        ),
                        icon: const Icon(Icons.add),
                        label: Text(widget.spanish ? 'Nuevo' : 'New'),
                      ),
                    ],
                  ),
                ),
                const Divider(height: 1),
                Expanded(
                  child: active.isEmpty
                      ? Center(
                          child: Text(
                            widget.spanish
                                ? 'No hay pedidos externos activos.'
                                : 'There are no active external orders.',
                            style: const TextStyle(color: Color(0xFF73777C)),
                          ),
                        )
                      : ListView.separated(
                          padding: const EdgeInsets.all(16),
                          itemCount: active.length,
                          separatorBuilder: (_, _) => const SizedBox(height: 9),
                          itemBuilder: (_, index) {
                            final order = active[index];
                            return ListTile(
                              key: ValueKey('external-order-${order.id}'),
                              tileColor: order.status == 'waiting'
                                  ? const Color(0xFFFFF1B8)
                                  : const Color(0xFFDDF1D9),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(13),
                              ),
                              leading: Icon(
                                order.status == 'waiting'
                                    ? Icons.schedule_rounded
                                    : Icons.restaurant_rounded,
                              ),
                              title: Text(
                                order.externalName!,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              subtitle: Text(
                                order.status == 'waiting'
                                    ? (widget.spanish
                                          ? 'Esperando productos'
                                          : 'Waiting for items')
                                    : (widget.spanish ? 'Comiendo' : 'Eating'),
                              ),
                              trailing: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  IconButton(
                                    key: ValueKey(
                                      'edit-external-order-${order.id}',
                                    ),
                                    tooltip: widget.spanish
                                        ? 'Añadir o modificar productos'
                                        : 'Add or edit items',
                                    onPressed: () => Navigator.pop(
                                      routeContext,
                                      _EditExternalOrderIntent(order),
                                    ),
                                    icon: const Icon(Icons.edit_outlined),
                                  ),
                                  const Icon(Icons.chevron_right),
                                ],
                              ),
                              onTap: () => Navigator.pop(routeContext, order),
                            );
                          },
                        ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    if (!mounted || selection == null) return;
    if (selection is _CreateExternalOrderIntent) {
      await _createExternalOrder();
    } else if (selection is _EditExternalOrderIntent) {
      await _editExternalOrder(selection.order);
    } else if (selection is ClientOrder) {
      await _openExternalOrder(selection);
    }
  }

  Future<void> _createExternalOrder() async {
    final controller = TextEditingController();
    final name = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(
          widget.spanish ? 'Nuevo pedido externo' : 'New external order',
        ),
        content: TextField(
          key: const ValueKey('external-order-name'),
          controller: controller,
          autofocus: true,
          maxLength: 80,
          decoration: InputDecoration(
            labelText: widget.spanish ? 'Nombre del pedido' : 'Order name',
          ),
          onSubmitted: (value) {
            if (value.trim().isNotEmpty) {
              Navigator.pop(dialogContext, value.trim());
            }
          },
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(widget.spanish ? 'Cancelar' : 'Cancel'),
          ),
          FilledButton(
            onPressed: () {
              if (controller.text.trim().isNotEmpty) {
                Navigator.pop(dialogContext, controller.text.trim());
              }
            },
            child: Text(widget.spanish ? 'Continuar' : 'Continue'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (name == null || !mounted) return;
    final menus = await _roomMenus();
    if (!mounted) return;
    await Navigator.push<void>(
      context,
      MaterialPageRoute(
        builder: (_) => OrderEditorDialog(
          spanish: widget.spanish,
          tableLabel: name,
          menus: menus,
          existingOrder: null,
          onSubmit: (description, items) => widget.writeExternalOrder(
            session: widget.session,
            roomId: widget.room.id,
            externalName: name,
            description: description,
            items: items,
          ),
        ),
      ),
    );
    await _reload();
  }

  Future<void> _openExternalOrder(ClientOrder order) async {
    if (order.status == 'waiting') {
      final editRequested = await showGeneralDialog<bool>(
        context: context,
        barrierDismissible: false,
        barrierLabel: 'External order delivery',
        barrierColor: Colors.black45,
        transitionDuration: const Duration(milliseconds: 180),
        transitionBuilder: (_, animation, _, child) =>
            FadeTransition(opacity: animation, child: child),
        pageBuilder: (_, _, _) => Padding(
          padding: const EdgeInsets.all(18),
          child: DeliveryOrderDialog(
            spanish: widget.spanish,
            tableLabel: order.externalName!,
            initialOrder: order,
            onDeliver: (itemId, unitIndex) => widget.deliverItem(
              session: widget.session,
              roomId: widget.room.id,
              orderId: order.id,
              itemId: itemId,
              unitIndex: unitIndex,
            ),
            onDeliverAll: () => widget.deliverAllItems(
              session: widget.session,
              roomId: widget.room.id,
              orderId: order.id,
            ),
            onUndoDelivery: (itemId, unitIndex) => widget.undoDeliveredItem(
              session: widget.session,
              roomId: widget.room.id,
              orderId: order.id,
              itemId: itemId,
              unitIndex: unitIndex,
            ),
            onEditOrder: () => Navigator.pop(context, true),
          ),
        ),
      );
      await _reload();
      if (editRequested == true && mounted) await _editExternalOrder(order);
      return;
    }
    await _billExternalOrder(order);
  }

  Future<void> _editExternalOrder(ClientOrder order) async {
    final menus = await _roomMenus();
    if (!mounted) return;
    await Navigator.push<void>(
      context,
      MaterialPageRoute(
        builder: (_) => OrderEditorDialog(
          spanish: widget.spanish,
          tableLabel: order.externalName!,
          menus: menus,
          existingOrder: order,
          onSubmit: (description, items) => widget.writeOrder(
            session: widget.session,
            roomId: widget.room.id,
            tableId: 0,
            orderId: order.id,
            description: description,
            items: items,
          ),
        ),
      ),
    );
    await _reload();
  }

  Future<void> _billExternalOrder(ClientOrder order) async {
    if (order.items.any((item) => item.deliveredQuantity < item.quantity)) {
      return;
    }
    final menus = await _roomMenus();
    if (!mounted) return;
    final productsById = {
      for (final menu in menus)
        for (final category in menu.categories)
          for (final product in category.products) product.id: product,
    };
    await showGeneralDialog<void>(
      context: context,
      barrierDismissible: false,
      barrierLabel: 'External order bill',
      barrierColor: Colors.black45,
      transitionDuration: const Duration(milliseconds: 180),
      transitionBuilder: (_, animation, _, child) =>
          FadeTransition(opacity: animation, child: child),
      pageBuilder: (_, _, _) => Padding(
        padding: const EdgeInsets.all(18),
        child: BillOrderDialog(
          spanish: widget.spanish,
          tableLabel: order.externalName!,
          order: order,
          productsById: productsById,
          onBill: (paymentMethod) => widget.setClosed(
            session: widget.session,
            roomId: widget.room.id,
            orderId: order.id,
            paymentMethod: paymentMethod,
          ),
          onSplit: () => _openSplitBill(order, order.externalName!),
        ),
      ),
    );
    await _reload();
  }

  Future<bool> _openSplitBill(ClientOrder order, String tableLabel) async {
    await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => SplitBillPage(
          spanish: widget.spanish,
          tableLabel: tableLabel,
          order: order,
          onSave: (accounts) => widget.saveSplit(
            session: widget.session,
            roomId: widget.room.id,
            orderId: order.id,
            accounts: accounts,
          ),
          onPay: (accountId, paymentMethod, tipPercent, customTip) =>
              widget.paySplit(
                session: widget.session,
                roomId: widget.room.id,
                orderId: order.id,
                accountId: accountId,
                paymentMethod: paymentMethod,
                tipPercent: tipPercent,
                customTip: customTip,
              ),
        ),
      ),
    );
    if (mounted) await _reload();
    // Close the underlying bill dialog even after a partial payment: it still
    // holds the pre-payment order and must not offer a stale full-charge action.
    return true;
  }

  Future<void> _openDailyOrders() async {
    try {
      final orders = await widget.loadTodayOrders(
        session: widget.session,
        roomId: widget.room.id,
      );
      if (!mounted) return;
      await showGeneralDialog<void>(
        context: context,
        barrierDismissible: true,
        barrierLabel: 'Daily orders',
        barrierColor: Colors.black45,
        transitionDuration: const Duration(milliseconds: 180),
        transitionBuilder: (_, animation, _, child) =>
            FadeTransition(opacity: animation, child: child),
        pageBuilder: (_, _, _) => Padding(
          padding: const EdgeInsets.all(18),
          child: DailyOrdersDialog(
            spanish: widget.spanish,
            orders: orders,
            loadOrders: ({required from, required to, paymentMethod}) =>
                widget.loadTodayOrders(
                  session: widget.session,
                  roomId: widget.room.id,
                  from: from,
                  to: to,
                  paymentMethod: paymentMethod,
                ),
          ),
        ),
      );
    } on Object {
      if (!mounted) return;
      _showCenteredNotice(
        widget.spanish
            ? 'No se pudo cargar el historial del día.'
            : 'Could not load today’s history.',
      );
    }
  }
}
