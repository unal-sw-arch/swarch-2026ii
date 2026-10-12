// ignore_for_file: invalid_use_of_protected_member

part of '../menus_page.dart';

extension _MenusCatalogOrdering on _MenusPageState {
  String _categoryScope(int menuId, int? parentCategoryId) =>
      '$menuId:${parentCategoryId ?? 'root'}';

  List<MenuCategory> _orderedCategories(
    RestaurantMenu menu,
    int? parentCategoryId,
    List<MenuCategory> fallback,
  ) =>
      _categoryOrderOverrides[_categoryScope(menu.id, parentCategoryId)] ??
      fallback;

  Widget _categoryReorderList(
    RestaurantMenu menu,
    int? parentCategoryId,
    List<MenuCategory> categories,
  ) {
    final scope = _categoryScope(menu.id, parentCategoryId);
    return ReorderableListView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      buildDefaultDragHandles: false,
      itemCount: categories.length,
      onReorderItem: _savingCategoryOrder.contains(scope)
          ? (_, _) {}
          : (oldIndex, newIndex) => _reorderCategory(
              menu,
              parentCategoryId,
              categories,
              oldIndex,
              newIndex,
            ),
      proxyDecorator: (child, _, animation) => AnimatedBuilder(
        animation: animation,
        builder: (_, _) => Material(
          color: Colors.transparent,
          elevation: 8 * animation.value,
          borderRadius: BorderRadius.circular(16),
          child: child,
        ),
      ),
      itemBuilder: (context, index) {
        final category = categories[index];
        return Material(
          key: ValueKey('drag-category-${category.id}'),
          color: Colors.transparent,
          child: Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: _CatalogCard(
              icon: parentCategoryId == null
                  ? category.isSpecial
                        ? Icons.auto_awesome_outlined
                        : Icons.category_outlined
                  : Icons.account_tree_outlined,
              title: category.name,
              badge: category.isSpecial ? (_es ? 'Especial' : 'Special') : null,
              lines: [
                '${category.products.length} ${_es ? 'productos' : 'products'}',
                '${category.subcategories.length} ${_es ? 'subcategorías' : 'subcategories'}',
              ],
              trailing: ReorderableDragStartListener(
                key: ValueKey('category-drag-handle-${category.id}'),
                index: index,
                enabled: !_savingCategoryOrder.contains(scope),
                child: Tooltip(
                  message: _es ? 'Arrastrar para ordenar' : 'Drag to reorder',
                  child: const Padding(
                    padding: EdgeInsets.all(10),
                    child: Icon(
                      Icons.drag_indicator_rounded,
                      color: Color(0xFF71859B),
                    ),
                  ),
                ),
              ),
              onTap: () => setState(() {
                _categoryId = category.id;
              }),
            ),
          ),
        );
      },
    );
  }

  Future<void> _reorderCategory(
    RestaurantMenu menu,
    int? parentCategoryId,
    List<MenuCategory> current,
    int oldIndex,
    int newIndex,
  ) async {
    if (newIndex == oldIndex) return;
    final scope = _categoryScope(menu.id, parentCategoryId);
    final previous = [...current];
    final reordered = [...previous];
    final category = reordered.removeAt(oldIndex);
    reordered.insert(newIndex, category);
    setState(() {
      _categoryOrderOverrides[scope] = reordered;
      _savingCategoryOrder.add(scope);
    });
    try {
      await widget.reorderCategories(
        token: widget.token,
        menuId: menu.id,
        parentCategoryId: parentCategoryId,
        categoryIds: reordered.map((item) => item.id).toList(),
      );
    } on Object {
      if (!mounted) return;
      setState(() => _categoryOrderOverrides[scope] = previous);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: const Color(0xFFB64A4A),
          content: Text(
            _es
                ? 'No se pudo guardar el orden de categorías.'
                : 'Could not save the category order.',
          ),
        ),
      );
    } finally {
      if (mounted) setState(() => _savingCategoryOrder.remove(scope));
    }
  }

  Future<void> _reorderProduct(
    MenuCategory category,
    int oldIndex,
    int newIndex,
  ) async {
    final previous = [
      ...(_productOrderOverrides[category.id] ?? category.products),
    ];
    if (newIndex == oldIndex) return;
    final reordered = [...previous];
    final product = reordered.removeAt(oldIndex);
    reordered.insert(newIndex, product);
    setState(() {
      _productOrderOverrides[category.id] = reordered;
      _savingProductOrder.add(category.id);
    });
    try {
      await widget.reorderProducts(
        token: widget.token,
        categoryId: category.id,
        productIds: reordered.map((item) => item.id).toList(),
      );
    } on Object {
      if (!mounted) return;
      setState(() => _productOrderOverrides[category.id] = previous);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: const Color(0xFFB64A4A),
          content: Text(
            _es ? 'No se pudo guardar el orden.' : 'Could not save the order.',
          ),
        ),
      );
    } finally {
      if (mounted) setState(() => _savingProductOrder.remove(category.id));
    }
  }
}
