import 'package:flutter/material.dart';

import '../../models/client_order.dart';
import '../../services/client_rooms_api.dart';
import '../../utils/money.dart';

part 'order_editor/selected_items.dart';
part 'order_editor/catalog_browser.dart';
part 'order_editor/product_customization.dart';
part 'order_editor/draft_submission.dart';
part 'order_editor/draft_models.dart';
part 'order_editor/category_widgets.dart';
part 'order_editor/special_product_widgets.dart';

class OrderEditorDialog extends StatefulWidget {
  const OrderEditorDialog({
    super.key,
    required this.spanish,
    required this.tableLabel,
    required this.menus,
    required this.existingOrder,
    required this.onSubmit,
    this.onTransfer,
  });
  final bool spanish;
  final String tableLabel;
  final List<ClientRoomMenu> menus;
  final ClientOrder? existingOrder;
  final Future<void> Function(String description, List<OrderItemWrite> items)
  onSubmit;
  final Future<bool> Function()? onTransfer;
  @override
  State<OrderEditorDialog> createState() => _OrderEditorDialogState();
}

class _OrderEditorDialogState extends State<OrderEditorDialog> {
  final Map<String, _DraftLine> _lines = {};
  final List<Map<String, _DraftLine>> _undo = [];
  final Set<String> _expandedLineKeys = {};
  int _nextLine = 1;
  final _orderDescription = TextEditingController();
  final _searchController = TextEditingController();
  final _searchFocus = FocusNode();
  int? _categoryId;
  String _searchQuery = '';
  bool _selectionExpanded = false;
  bool _saving = false;
  String? _submitError;
  bool get _es => widget.spanish;
  List<ClientMenuCategory> get _categories => [
    for (final menu in widget.menus) ...menu.categories,
  ];
  List<ClientMenuProduct> get _products => [
    for (final category in _categories) ...category.products,
  ];

  @override
  void initState() {
    super.initState();
    _searchFocus.addListener(_onSearchFocusChanged);
    _orderDescription.text = widget.existingOrder?.description ?? '';
    final existing = widget.existingOrder;
    if (existing != null) {
      for (final item in existing.items) {
        final product = _products
            .where((value) => value.id == item.productId)
            .firstOrNull;
        if (product == null) continue;
        final parentKey = item.parentOrderItemId == null
            ? null
            : 'existing:${item.parentOrderItemId}';
        _lines['existing:${item.id}'] = _DraftLine(
          product: product,
          quantity: item.quantity,
          deliveredQuantity: item.deliveredQuantity,
          specifications: item.specifications ?? '',
          removedIngredientIds: {...item.removedIngredientIds},
          parentLineKey: parentKey,
        );
      }
      _mergeIdenticalLines();
    }
  }

  @override
  void dispose() {
    _searchFocus
      ..removeListener(_onSearchFocusChanged)
      ..dispose();
    _orderDescription.dispose();
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => PopScope<void>(
    canPop:
        _searchQuery.isEmpty &&
        !_selectionExpanded &&
        _categoryId == null &&
        !_saving,
    onPopInvokedWithResult: (didPop, _) {
      if (!didPop && !_saving) _goBack();
    },
    child: Scaffold(
      // While catalog search owns the keyboard, keep the draft summary and
      // footer at the physical bottom (behind the keyboard) instead of moving
      // them into the reduced viewport and taking space from search results.
      resizeToAvoidBottomInset: !_searchFocus.hasFocus,
      backgroundColor: const Color(0xFFFAF9F6),
      body: SafeArea(
        child: Stack(
          children: [
            Column(
              children: [
                _header(),
                const Divider(height: 1),
                Expanded(child: _catalog()),
                const Divider(height: 1),
                _selectedProducts(),
                const Divider(height: 1),
                _footer(),
              ],
            ),
            Positioned.fill(
              child: IgnorePointer(
                ignoring: !_selectionExpanded,
                child: AnimatedOpacity(
                  duration: const Duration(milliseconds: 180),
                  curve: Curves.easeOutCubic,
                  opacity: _selectionExpanded ? 1 : 0,
                  child: _expandedSelectedProducts(),
                ),
              ),
            ),
          ],
        ),
      ),
    ),
  );

  void _onSearchFocusChanged() {
    if (mounted) setState(() {});
  }

  void _goBack() {
    if (_searchQuery.isNotEmpty) {
      _searchController.clear();
      setState(() => _searchQuery = '');
      return;
    }
    if (_selectionExpanded) {
      setState(() => _selectionExpanded = false);
      return;
    }
    final current = _categories
        .where((category) => category.id == _categoryId)
        .firstOrNull;
    if (current != null) {
      setState(() => _categoryId = current.parentCategoryId);
      return;
    }
    Navigator.maybePop(context);
  }

  Widget _header() => Padding(
    padding: const EdgeInsets.fromLTRB(22, 16, 12, 14),
    child: Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                _es
                    ? 'Pedido · Mesa ${widget.tableLabel}'
                    : 'Order · Table ${widget.tableLabel}',
                style: const TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w700,
                ),
              ),
              Text(
                widget.existingOrder == null
                    ? (_es ? 'Nuevo pedido' : 'New order')
                    : (_es
                          ? 'Modificar pedido enviado'
                          : 'Modify submitted order'),
                style: const TextStyle(color: Color(0xFF73777C)),
              ),
            ],
          ),
        ),
        IconButton(
          key: const ValueKey('undo-order-edit'),
          tooltip: _es ? 'Deshacer' : 'Undo',
          onPressed: _saving || _undo.isEmpty ? null : _undoLast,
          icon: const Icon(Icons.undo_rounded),
        ),
        if (widget.onTransfer != null)
          IconButton(
            key: const ValueKey('transfer-order'),
            tooltip: _es ? 'Trasladar pedido' : 'Transfer order',
            onPressed: _saving ? null : _transferOrder,
            icon: const Icon(Icons.swap_horiz_rounded),
          ),
        IconButton(
          key: const ValueKey('close-order-editor'),
          tooltip: _es ? 'Cerrar pedido' : 'Close order',
          onPressed: _saving ? null : () => Navigator.pop(context),
          icon: const Icon(Icons.close_rounded),
        ),
      ],
    ),
  );
}
