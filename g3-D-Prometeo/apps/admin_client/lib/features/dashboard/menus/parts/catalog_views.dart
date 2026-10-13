// ignore_for_file: invalid_use_of_protected_member

part of '../menus_page.dart';

extension _MenusCatalogViews on _MenusPageState {
  Widget _overview() => ListView(
    padding: const EdgeInsets.fromLTRB(28, 28, 28, 40),
    children: [
      _pageHeader(
        title: _es ? 'Menús' : 'Menus',
        subtitle: _es
            ? 'Configura el menú principal y las selecciones secundarias de cada salón.'
            : 'Configure each room main menu and its secondary selections.',
        actions: [
          FilledButton.icon(
            key: const ValueKey('add-menu'),
            onPressed: _createMenu,
            icon: const Icon(Icons.add),
            label: Text(_es ? 'Añadir menú' : 'Add menu'),
          ),
        ],
      ),
      const SizedBox(height: 24),
      if (_catalog.menus.isEmpty)
        _empty(_es ? 'Aún no hay menús' : 'No menus yet')
      else
        LayoutBuilder(
          builder: (context, constraints) {
            final columns = constraints.maxWidth >= 950
                ? 3
                : constraints.maxWidth >= 600
                ? 2
                : 1;
            return GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: _catalog.menus.length,
              gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: columns,
                mainAxisSpacing: 16,
                crossAxisSpacing: 16,
                mainAxisExtent: 190,
              ),
              itemBuilder: (context, index) {
                final menu = _catalog.menus[index];
                return _CatalogCard(
                  key: ValueKey('menu-card-${menu.id}'),
                  icon: Icons.menu_book_outlined,
                  title: menu.name,
                  lines: [
                    '${menu.categories.length} ${_es ? 'categorías' : 'categories'} · ${menu.productCount} ${_es ? 'productos' : 'products'}',
                    '${menu.primaryHallIds.length} ${_es ? 'principales' : 'primary'} · ${menu.secondaryHallIds.length} ${_es ? 'secundarios' : 'secondary'}',
                  ],
                  onTap: () => setState(() => _menuId = menu.id),
                );
              },
            );
          },
        ),
    ],
  );

  Widget _menuDetail(RestaurantMenu menu) {
    final categories = _orderedCategories(menu, null, menu.categories);
    return ListView(
      padding: const EdgeInsets.fromLTRB(28, 24, 28, 40),
      children: [
        _pageHeader(
          back: () => setState(_resetNavigation),
          title: menu.name,
          subtitle: _assignmentText(menu),
          actions: [
            OutlinedButton.icon(
              key: const ValueKey('add-category'),
              onPressed: () => _createCategory(menu),
              icon: const Icon(Icons.category_outlined),
              label: Text(_es ? 'Añadir categoría' : 'Add category'),
            ),
            FilledButton.icon(
              key: const ValueKey('add-ingredient'),
              onPressed: _catalog.ingredientCategories.isEmpty
                  ? null
                  : _createIngredientQuick,
              icon: const Icon(Icons.grass_outlined),
              label: Text(_es ? 'Añadir ingrediente' : 'Add ingredient'),
            ),
          ],
        ),
        const SizedBox(height: 24),
        if (categories.isEmpty)
          _empty(
            _es ? 'Crea la primera categoría' : 'Create the first category',
          )
        else
          _categoryReorderList(menu, null, categories),
      ],
    );
  }

  Widget _categoryDetail(MenuCategory category) {
    final menu = _menu!;
    final products = _productOrderOverrides[category.id] ?? category.products;
    final subcategories = _orderedCategories(
      menu,
      category.id,
      category.subcategories,
    );
    return ListView(
      padding: const EdgeInsets.fromLTRB(28, 24, 28, 40),
      children: [
        _pageHeader(
          back: () => setState(() {
            _categoryId = category.parentCategoryId;
          }),
          title: category.name,
          subtitle: category.isSpecial
              ? (_es
                    ? 'Categoría especial: combos, adiciones u otros productos asociados.'
                    : 'Special category: combos, additions, or associated products.')
              : (category.parentCategoryId != null
                    ? (_es ? 'Subcategoría' : 'Subcategory')
                    : menu.name),
          actions: [
            OutlinedButton.icon(
              key: const ValueKey('rename-category'),
              onPressed: () => _renameCategory(category),
              icon: const Icon(Icons.edit_outlined),
              label: Text(_es ? 'Cambiar nombre' : 'Rename'),
            ),
            OutlinedButton.icon(
              key: const ValueKey('add-subcategory'),
              onPressed: () => _createCategory(menu, parent: category),
              icon: const Icon(Icons.account_tree_outlined),
              label: Text(_es ? 'Añadir subcategoría' : 'Add subcategory'),
            ),
            FilledButton.icon(
              key: const ValueKey('add-product'),
              onPressed: () => _createProduct(menu, category),
              icon: const Icon(Icons.add),
              label: Text(_es ? 'Añadir producto' : 'Add product'),
            ),
          ],
        ),
        const SizedBox(height: 24),
        if (subcategories.isNotEmpty) ...[
          Text(
            _es ? 'Subcategorías' : 'Subcategories',
            style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 12),
          _categoryReorderList(menu, category.id, subcategories),
          const SizedBox(height: 12),
        ],
        Text(
          _es ? 'Productos' : 'Products',
          style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 12),
        if (category.products.isEmpty)
          _empty(_es ? 'No hay productos.' : 'No products.')
        else
          DecoratedBox(
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: const Color(0xFFE5E7E9)),
            ),
            child: ReorderableListView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              buildDefaultDragHandles: false,
              itemCount: products.length,
              onReorderItem: _savingProductOrder.contains(category.id)
                  ? (_, _) {}
                  : (oldIndex, newIndex) =>
                        _reorderProduct(category, oldIndex, newIndex),
              proxyDecorator: (child, _, animation) => AnimatedBuilder(
                animation: animation,
                builder: (_, _) => Material(
                  color: Colors.white,
                  elevation: 8 * animation.value,
                  borderRadius: BorderRadius.circular(12),
                  child: child,
                ),
              ),
              itemBuilder: (context, index) {
                final product = products[index];
                return Material(
                  key: ValueKey('menu-product-${product.id}'),
                  color: Colors.transparent,
                  child: ListTile(
                    onTap: () => _editProduct(menu, product),
                    leading: const CircleAvatar(
                      backgroundColor: Color(0xFFE9EEF3),
                      child: Icon(
                        Icons.restaurant_menu,
                        color: Color(0xFF71859B),
                      ),
                    ),
                    title: Text(product.name),
                    subtitle: Text(product.description ?? ''),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(formatPesos(product.value)),
                        const SizedBox(width: 10),
                        ReorderableDragStartListener(
                          key: ValueKey('drag-product-${product.id}'),
                          index: index,
                          enabled: !_savingProductOrder.contains(category.id),
                          child: Tooltip(
                            message: _es
                                ? 'Arrastrar para ordenar'
                                : 'Drag to reorder',
                            child: const Padding(
                              padding: EdgeInsets.all(10),
                              child: Icon(
                                Icons.drag_indicator_rounded,
                                color: Color(0xFF71859B),
                              ),
                            ),
                          ),
                        ),
                        const Icon(Icons.edit_outlined, size: 18),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
      ],
    );
  }
}
