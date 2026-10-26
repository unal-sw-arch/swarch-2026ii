// ignore_for_file: invalid_use_of_protected_member

part of '../live_room_page.dart';

extension _LiveRoomOrderFlows on _LiveRoomPageState {
  void _activateTable(LiveRoomTable table) {
    if (_openingTableView) return;
    _openingTableView = true;
    unawaited(_activateTableOnce(table));
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _openingTableView = false;
    });
  }

  Future<void> _activateTableOnce(LiveRoomTable table) async {
    switch (_mode) {
      case _LiveToolMode.edit:
        await _openOrderEditor(table.id);
      case _LiveToolMode.bill:
        _layout.selectTableOrGroup(table.id);
        await _openBillDialog(table.id);
      case _LiveToolMode.select:
        _layout.toggleSelection(table.id);
        await _openDeliveryDialog(table);
    }
  }

  ClientOrder? _orderForTable(int tableId) {
    final groupId = _layout.groupForTable(tableId)?.id;
    return _orders.where((order) {
      if (groupId != null) return order.tableGroupId == groupId;
      return order.tableGroupId == null && order.tableId == tableId;
    }).firstOrNull;
  }

  Future<void> _openDeliveryDialog(LiveRoomTable table) async {
    final order = _orderForTable(table.id);
    if (table.status != 'waiting' || order == null || !mounted) return;
    final group = _layout.groupForTable(table.id);
    final tableIds = group?.tableIds ?? [table.id];
    final tableLabel = _layout.tables
        .where((candidate) => tableIds.contains(candidate.id))
        .map((candidate) => candidate.identifier)
        .join(' + ');
    final editRequested = await showGeneralDialog<bool>(
      context: context,
      barrierDismissible: false,
      barrierLabel: 'Order delivery',
      barrierColor: Colors.black45,
      transitionDuration: const Duration(milliseconds: 180),
      transitionBuilder: (_, animation, _, child) => FadeTransition(
        opacity: CurvedAnimation(parent: animation, curve: Curves.easeOutCubic),
        child: ScaleTransition(
          scale: Tween(begin: .985, end: 1.0).animate(animation),
          child: child,
        ),
      ),
      pageBuilder: (_, _, _) => Padding(
        padding: const EdgeInsets.all(18),
        child: DeliveryOrderDialog(
          spanish: widget.spanish,
          tableLabel: tableLabel,
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
    if (editRequested == true && mounted) {
      await _openOrderEditor(table.id);
    }
  }

  Future<void> _openOrderEditor(int clickedTableId) async {
    if (_openingOrderEditor) return;
    _openingOrderEditor = true;
    final group = _layout.groupForTable(clickedTableId);
    final tableIds = group?.tableIds ?? [clickedTableId];
    final existingOrder = _orders.where((order) {
      if (group != null) return order.tableGroupId == group.id;
      return order.tableGroupId == null && order.tableId == clickedTableId;
    }).firstOrNull;
    final tableId =
        existingOrder?.tableId ??
        tableIds.reduce((first, second) => first < second ? first : second);
    final labels = _layout.tables
        .where((table) => tableIds.contains(table.id))
        .map((table) => table.identifier)
        .toList();
    final tableLabel = labels.join(' + ');
    try {
      final cachedMenus = _menusCache;
      final navigation = Navigator.of(context).push<bool>(
        MaterialPageRoute<bool>(
          builder: (_) {
            Widget editor(List<ClientRoomMenu> menus) => OrderEditorDialog(
              spanish: widget.spanish,
              tableLabel: tableLabel,
              menus: menus,
              existingOrder: existingOrder,
              onTransfer: existingOrder == null
                  ? null
                  : () => _chooseTransferTarget(existingOrder),
              onSubmit: (description, items) async {
                await widget.writeOrder(
                  session: widget.session,
                  roomId: widget.room.id,
                  tableId: tableId,
                  orderId: existingOrder?.id,
                  description: description,
                  items: items,
                );
              },
            );

            return cachedMenus == null
                ? _OrderEditorLoader(
                    spanish: widget.spanish,
                    loadMenus: _roomMenus,
                    builder: editor,
                  )
                : editor(cachedMenus);
          },
        ),
      );
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _openingOrderEditor = false;
      });
      final changed = await navigation;
      if (changed == true) await _reloadOrders();
    } on Object {
      if (mounted) {
        _showCenteredNotice(
          widget.spanish
              ? 'No se pudo abrir el pedido.'
              : 'Could not open order.',
        );
      }
    } finally {
      _openingOrderEditor = false;
    }
  }

  Future<bool> _chooseTransferTarget(ClientOrder order) async {
    final available = <_TransferTarget>[];
    final addedGroups = <int>{};
    for (final table in _layout.tables) {
      final group = _layout.groupForTable(table.id);
      if (group == null) {
        if (table.status == 'available' && _orderForTable(table.id) == null) {
          available.add(
            _TransferTarget(tableId: table.id, label: table.identifier),
          );
        }
        continue;
      }
      if (!addedGroups.add(group.id)) continue;
      final members = _layout.tables
          .where((candidate) => group.tableIds.contains(candidate.id))
          .toList();
      if (members.isEmpty ||
          members.any(
            (member) =>
                member.status != 'available' ||
                _orderForTable(member.id) != null,
          )) {
        continue;
      }
      members.sort((a, b) => a.id.compareTo(b.id));
      final fallbackLabel = members
          .map((member) => member.identifier)
          .join(' + ');
      available.add(
        _TransferTarget(
          tableId: members.first.id,
          label: group.identifier?.trim().isNotEmpty == true
              ? group.identifier!.trim()
              : fallbackLabel,
        ),
      );
    }
    if (available.isEmpty || !mounted) {
      _showCenteredNotice(
        widget.spanish
            ? 'No hay mesas disponibles en este salón.'
            : 'There are no available tables in this room.',
        warning: true,
      );
      return false;
    }
    final targetId = await showDialog<int>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(widget.spanish ? 'Trasladar pedido' : 'Transfer order'),
        content: SizedBox(
          width: 440,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${widget.room.name} · ${widget.spanish ? 'selecciona una mesa disponible' : 'select an available table'}',
              ),
              const SizedBox(height: 14),
              Flexible(
                child: SingleChildScrollView(
                  child: Wrap(
                    spacing: 9,
                    runSpacing: 9,
                    children: [
                      for (final target in available)
                        OutlinedButton(
                          key: ValueKey('transfer-target-${target.tableId}'),
                          onPressed: () =>
                              Navigator.pop(dialogContext, target.tableId),
                          child: Text(target.label),
                        ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(widget.spanish ? 'Cancelar' : 'Cancel'),
          ),
        ],
      ),
    );
    if (targetId == null) return false;
    try {
      await widget.transferOrder(
        session: widget.session,
        roomId: widget.room.id,
        orderId: order.id,
        tableId: targetId,
      );
      await _reload();
      return true;
    } on Object {
      if (mounted) {
        _showCenteredNotice(
          widget.spanish
              ? 'No se pudo trasladar el pedido.'
              : 'The order could not be transferred.',
        );
      }
      return false;
    }
  }

  Future<void> _openBillDialog(int clickedTableId) async {
    final group = _layout.groupForTable(clickedTableId);
    final tableIds = group?.tableIds ?? [clickedTableId];
    final order = _orders.where((value) {
      if (group != null) return value.tableGroupId == group.id;
      return value.tableGroupId == null && value.tableId == clickedTableId;
    }).firstOrNull;
    if (order == null || !mounted) {
      _showCenteredNotice(
        widget.spanish
            ? 'No hay pedido activo para facturar.'
            : 'There is no active order to bill.',
        warning: true,
      );
      return;
    }
    final hasPendingItems = order.items.any(
      (item) => item.deliveredQuantity < item.quantity,
    );
    if (order.status != 'eating' || hasPendingItems) {
      _showCenteredNotice(
        widget.spanish
            ? 'Solo puedes facturar cuando todos los productos hayan sido entregados y la mesa esté comiendo.'
            : 'You can only bill after every item is delivered and the table is eating.',
        warning: true,
      );
      return;
    }

    final labels = _layout.tables
        .where((table) => tableIds.contains(table.id))
        .map((table) => table.identifier)
        .toList();
    final tableLabel = labels.join(' + ');

    try {
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
        barrierLabel: 'Order bill',
        barrierColor: Colors.black45,
        transitionDuration: const Duration(milliseconds: 180),
        transitionBuilder: (_, animation, _, child) =>
            FadeTransition(opacity: animation, child: child),
        pageBuilder: (_, _, _) => Padding(
          padding: const EdgeInsets.all(18),
          child: BillOrderDialog(
            spanish: widget.spanish,
            tableLabel: tableLabel,
            order: order,
            productsById: productsById,
            onBill: (paymentMethod) => widget.setClosed(
              session: widget.session,
              roomId: widget.room.id,
              orderId: order.id,
              paymentMethod: paymentMethod,
            ),
            onSplit: () => _openSplitBill(order, tableLabel),
          ),
        ),
      );
      await _reload();
    } on Object {
      if (!mounted) return;
      _showCenteredNotice(
        widget.spanish
            ? 'No se pudo abrir la caja de esta mesa.'
            : 'Could not open the cashier view for this table.',
      );
    }
  }
}
