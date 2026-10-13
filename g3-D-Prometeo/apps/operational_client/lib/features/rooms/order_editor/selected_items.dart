// ignore_for_file: invalid_use_of_protected_member

part of '../order_editor_dialog.dart';

extension _OrderEditorSelectedItems on _OrderEditorDialogState {
  Future<void> _transferOrder() async {
    if (widget.onTransfer == null) return;
    if (await widget.onTransfer!() && mounted) Navigator.pop(context);
  }

  Widget _selectedProducts() {
    final selected = _orderedSelectedEntries();
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        InkWell(
          key: const ValueKey('toggle-selected-products'),
          onTap: selected.isEmpty
              ? null
              : () => setState(() => _selectionExpanded = true),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 10, 14, 8),
            child: Row(
              children: [
                const Icon(
                  Icons.shopping_basket_outlined,
                  size: 20,
                  color: Color(0xFF71859B),
                ),
                const SizedBox(width: 9),
                Expanded(
                  child: Text(
                    selected.isEmpty
                        ? (_es
                              ? 'Aún no hay productos seleccionados.'
                              : 'No products selected yet.')
                        : '${_es ? 'Pedido actual' : 'Current order'} · '
                              '${selected.fold<int>(0, (total, entry) => total + entry.value.quantity)}',
                    style: const TextStyle(
                      color: Color(0xFF565D64),
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                if (selected.isNotEmpty)
                  const Icon(Icons.open_in_full_rounded, size: 18),
              ],
            ),
          ),
        ),
        GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: selected.isEmpty
              ? null
              : () => setState(() => _selectionExpanded = true),
          child: SizedBox(
            height: 55,
            child: selected.isEmpty
                ? const SizedBox.shrink()
                : ListView.separated(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.fromLTRB(18, 4, 18, 10),
                    itemCount: selected.length,
                    separatorBuilder: (_, _) => const SizedBox(width: 8),
                    itemBuilder: (_, index) {
                      final entry = selected[index];
                      return Chip(
                        key: ValueKey('selected-order-line-${entry.key}'),
                        avatar: CircleAvatar(
                          child: Text('${entry.value.quantity}'),
                        ),
                        label: Text(entry.value.product.name),
                      );
                    },
                  ),
          ),
        ),
      ],
    );
  }

  Widget _expandedSelectedProducts() {
    final selected = _orderedSelectedEntries();
    final total = selected.fold<int>(
      0,
      (sum, entry) => sum + entry.value.product.value * entry.value.quantity,
    );
    return Material(
      key: const ValueKey('expanded-order-summary'),
      color: const Color(0xFFFAF9F6),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 14, 10, 12),
            child: Row(
              children: [
                const Icon(
                  Icons.shopping_basket_outlined,
                  color: Color(0xFF71859B),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _es ? 'Pedido actual' : 'Current order',
                        style: const TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      Text(
                        '${selected.fold<int>(0, (sum, entry) => sum + entry.value.quantity)} ${_es ? 'productos' : 'items'}',
                        style: const TextStyle(color: Color(0xFF73777C)),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  key: const ValueKey('close-expanded-order-summary'),
                  tooltip: _es ? 'Volver al menú' : 'Back to menu',
                  onPressed: () => setState(() => _selectionExpanded = false),
                  icon: const Icon(Icons.close_rounded),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: selected.isEmpty
                ? Center(
                    child: Text(
                      _es ? 'El pedido está vacío.' : 'The order is empty.',
                      style: const TextStyle(color: Color(0xFF73777C)),
                    ),
                  )
                : ListView.separated(
                    padding: const EdgeInsets.all(18),
                    itemCount: selected.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 9),
                    itemBuilder: (_, index) =>
                        _selectedProductGroup(selected[index]),
                  ),
          ),
          Container(
            padding: const EdgeInsets.fromLTRB(20, 13, 20, 16),
            decoration: const BoxDecoration(
              color: Colors.white,
              border: Border(top: BorderSide(color: Color(0xFFE0E3E6))),
            ),
            child: Row(
              children: [
                Text(
                  _es ? 'Total provisional' : 'Draft total',
                  style: const TextStyle(
                    color: Color(0xFF676D73),
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const Spacer(),
                Text(
                  formatPesos(total),
                  style: const TextStyle(
                    fontSize: 19,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _selectedProductGroup(MapEntry<String, _DraftLine> entry) {
    final line = entry.value;
    final expanded = line.quantity > 1 && _expandedLineKeys.contains(entry.key);
    return Column(
      children: [
        _selectedProductRow(entry),
        AnimatedSize(
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOutCubic,
          child: !expanded
              ? const SizedBox.shrink()
              : Padding(
                  padding: const EdgeInsets.fromLTRB(18, 7, 4, 2),
                  child: Column(
                    children: [
                      for (var unit = 0; unit < line.quantity; unit++)
                        _individualUnitRow(entry.key, line, unit),
                    ],
                  ),
                ),
        ),
      ],
    );
  }

  Widget _individualUnitRow(String key, _DraftLine line, int unit) {
    final delivered = unit < line.deliveredQuantity;
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Material(
        color: delivered ? const Color(0xFFE8F3E5) : Colors.white,
        borderRadius: BorderRadius.circular(10),
        child: ListTile(
          key: ValueKey('order-line-$key-unit-$unit'),
          dense: true,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
            side: const BorderSide(color: Color(0xFFE0E3E6)),
          ),
          leading: Icon(
            delivered ? Icons.check_circle_outline : Icons.edit_outlined,
            color: delivered
                ? const Color(0xFF5B9B66)
                : const Color(0xFF71859B),
          ),
          title: Text(
            '${line.product.name} · ${_es ? 'unidad' : 'unit'} ${unit + 1}',
          ),
          subtitle: delivered
              ? Text(_es ? 'Producto entregado' : 'Delivered item')
              : Text(_es ? 'Editar solo esta unidad' : 'Edit only this unit'),
          trailing: delivered ? null : const Icon(Icons.chevron_right_rounded),
          onTap: delivered ? null : () => _editSingleUnit(key, unit),
        ),
      ),
    );
  }

  Widget _selectedProductRow(MapEntry<String, _DraftLine> entry) {
    final line = entry.value;
    final special = line.parentLineKey != null;
    final categoryName = _categoryPathForProduct(line.product.id);
    final removed = line.product.ingredients
        .where((item) => line.removedIngredientIds.contains(item.id))
        .map((item) => item.name)
        .join(', ');
    return Padding(
      padding: EdgeInsets.only(left: special ? 24 : 0),
      child: Material(
        color: special ? const Color(0xFFF2F5F8) : Colors.white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: const BorderSide(color: Color(0xFFE0E3E6)),
        ),
        child: InkWell(
          key: ValueKey('expanded-order-line-${entry.key}'),
          onTap: () {
            if (line.quantity > 1) {
              setState(() {
                if (!_expandedLineKeys.add(entry.key)) {
                  _expandedLineKeys.remove(entry.key);
                }
              });
            } else {
              _showProductDetails(line.product, draftKey: entry.key);
            }
          },
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 12, 8, 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  line.product.name,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    CircleAvatar(
                      radius: 17,
                      backgroundColor: const Color(0xFFE9EFF5),
                      child: Text('${line.quantity}'),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        categoryName ?? '',
                        style: const TextStyle(
                          color: Color(0xFF71859B),
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    Text(
                      formatPesos(line.product.value * line.quantity),
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    if (line.quantity > 1)
                      AnimatedRotation(
                        turns: _expandedLineKeys.contains(entry.key) ? .5 : 0,
                        duration: const Duration(milliseconds: 180),
                        child: const Icon(Icons.keyboard_arrow_down_rounded),
                      ),
                    if (line.quantity == 1)
                      IconButton(
                        tooltip: _es ? 'Editar producto' : 'Edit product',
                        onPressed: () => _showProductDetails(
                          line.product,
                          draftKey: entry.key,
                        ),
                        icon: const Icon(Icons.edit_outlined),
                      ),
                    if (_canRemove(entry.key))
                      IconButton(
                        tooltip: _es ? 'Quitar una unidad' : 'Remove one unit',
                        onPressed: () => _removeOne(entry.key),
                        icon: const Icon(Icons.remove_circle_outline),
                      ),
                  ],
                ),
                if (line.specifications.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(line.specifications),
                  ),
                if (removed.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 3),
                    child: Text(
                      '${_es ? 'Sin' : 'No'} $removed',
                      style: const TextStyle(color: Color(0xFFAA5A56)),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _editSingleUnit(String key, int unit) async {
    final source = _lines[key];
    if (source == null ||
        source.quantity <= 1 ||
        unit < source.deliveredQuantity) {
      return;
    }
    final undoLength = _undo.length;
    final original = source.copy();
    _remember();
    final newKey = 'custom:${_nextLine++}';
    final individual = source.copy()
      ..quantity = 1
      ..deliveredQuantity = 0;
    setState(() {
      source.quantity--;
      _lines[newKey] = individual;
      if (source.quantity <= 1) _expandedLineKeys.remove(key);
    });
    await _showProductDetails(individual.product, draftKey: newKey);
    if (!mounted) return;
    final edited = _lines[newKey];
    final hasAttachedSpecials = _lines.values.any(
      (candidate) => candidate.parentLineKey == newKey,
    );
    final unchanged =
        edited != null &&
        edited.product.id == original.product.id &&
        edited.quantity == 1 &&
        edited.specifications == original.specifications &&
        edited.removedIngredientIds.length ==
            original.removedIngredientIds.length &&
        edited.removedIngredientIds.containsAll(
          original.removedIngredientIds,
        ) &&
        !hasAttachedSpecials;
    if (unchanged) {
      setState(() {
        source.quantity++;
        _lines.remove(newKey);
        while (_undo.length > undoLength) {
          _undo.removeLast();
        }
      });
    }
  }

  String? _categoryPathForProduct(int productId) {
    final category = _categories
        .where(
          (candidate) =>
              candidate.products.any((product) => product.id == productId),
        )
        .firstOrNull;
    if (category == null) return null;
    final names = <String>[];
    final visited = <int>{};
    ClientMenuCategory? current = category;
    while (current != null && visited.add(current.id)) {
      names.add(current.name);
      final parentId = current.parentCategoryId;
      current = parentId == null
          ? null
          : _categories.where((item) => item.id == parentId).firstOrNull;
    }
    return names.reversed.join(' › ');
  }

  String? _searchCategoryLabel(int productId) {
    final category = _categories
        .where(
          (candidate) =>
              candidate.products.any((product) => product.id == productId),
        )
        .firstOrNull;
    if (category == null) return null;
    return _categoryPathForProduct(productId);
  }
}
