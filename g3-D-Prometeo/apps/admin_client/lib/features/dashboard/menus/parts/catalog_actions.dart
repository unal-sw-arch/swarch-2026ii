// ignore_for_file: invalid_use_of_protected_member

part of '../menus_page.dart';

extension _MenusCatalogActions on _MenusPageState {
  Future<void> _createMenu() async {
    final draft = await showDialog<_MenuDraft>(
      context: context,
      builder: (_) => _MenuDialog(spanish: _es, rooms: _rooms),
    );
    if (draft == null) return;
    await _write(
      () => widget.addMenu(
        token: widget.token,
        name: draft.name,
        hallAssignments: draft.assignments,
      ),
    );
  }

  Future<void> _createCategory(
    RestaurantMenu menu, {
    MenuCategory? parent,
  }) async {
    final draft = await showDialog<_CategoryDraft>(
      context: context,
      builder: (_) =>
          _CategoryDialog(spanish: _es, inheritedSpecial: parent?.isSpecial),
    );
    if (draft == null) return;
    await _write(
      () => widget.addCategory(
        token: widget.token,
        menuId: menu.id,
        name: draft.name,
        parentCategoryId: parent?.id,
        isSpecial: draft.isSpecial,
      ),
    );
  }

  Future<void> _renameCategory(MenuCategory category) async {
    final controller = TextEditingController(text: category.name);
    final name = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(_es ? 'Cambiar nombre de categoría' : 'Rename category'),
        content: TextField(
          key: const ValueKey('category-name-edit'),
          controller: controller,
          autofocus: true,
          maxLength: 60,
          decoration: InputDecoration(labelText: _es ? 'Nombre' : 'Name'),
          onSubmitted: (value) {
            if (value.trim().length >= 2) {
              Navigator.pop(dialogContext, value.trim());
            }
          },
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(_es ? 'Cancelar' : 'Cancel'),
          ),
          FilledButton(
            key: const ValueKey('save-category-name'),
            onPressed: () {
              final value = controller.text.trim();
              if (value.length >= 2) Navigator.pop(dialogContext, value);
            },
            child: Text(_es ? 'Guardar' : 'Save'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (name == null || name == category.name) return;
    await _write(
      () => widget.renameCategory(
        token: widget.token,
        categoryId: category.id,
        name: name,
      ),
    );
  }

  Future<void> _createIngredientQuick() async {
    final draft = await showDialog<_IngredientDraft>(
      context: context,
      builder: (_) => _IngredientDialog(
        spanish: _es,
        categories: _catalog.ingredientCategories,
      ),
    );
    if (draft == null) return;
    await _write(
      () => widget.addIngredient(
        token: widget.token,
        name: draft.name,
        categoryId: draft.categoryId,
        description: draft.description,
      ),
    );
  }

  Future<void> _createProduct(
    RestaurantMenu menu,
    MenuCategory category,
  ) async {
    final draft = await showDialog<_ProductDraft>(
      context: context,
      builder: (_) => _ProductDialog(
        spanish: _es,
        ingredients: _catalog.ingredients,
        secondaryRooms: _rooms
            .where((room) => menu.secondaryHallIds.contains(room.id))
            .toList(),
      ),
    );
    if (draft == null) return;
    await _write(
      () => widget.addProduct(
        token: widget.token,
        menuId: menu.id,
        name: draft.name,
        description: draft.description,
        value: draft.value,
        categoryId: category.id,
        ingredientIds: draft.ingredientIds,
        hallIds: draft.hallIds,
      ),
    );
  }

  Future<void> _editProduct(RestaurantMenu menu, CatalogProduct product) async {
    final draft = await showDialog<_ProductDraft>(
      context: context,
      builder: (_) => _ProductDialog(
        spanish: _es,
        ingredients: _catalog.ingredients,
        secondaryRooms: _rooms
            .where((room) => menu.secondaryHallIds.contains(room.id))
            .toList(),
        product: product,
        onDeactivate: () async {
          final confirmed = await showDialog<bool>(
            context: context,
            builder: (dialogContext) => AlertDialog(
              title: Text(_es ? 'Desactivar producto' : 'Deactivate product'),
              content: Text(
                _es
                    ? 'El producto dejará de aparecer en el menú, pero se conservará en el historial.'
                    : 'The product will disappear from the menu, but remain in order history.',
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(dialogContext, false),
                  child: Text(_es ? 'Cancelar' : 'Cancel'),
                ),
                FilledButton(
                  onPressed: () => Navigator.pop(dialogContext, true),
                  child: Text(_es ? 'Desactivar' : 'Deactivate'),
                ),
              ],
            ),
          );
          if (confirmed != true || !mounted) return;
          Navigator.pop(context);
          await _write(
            () => widget.deactivateProduct(
              token: widget.token,
              productId: product.id,
            ),
          );
        },
      ),
    );
    if (draft == null) return;
    await _write(
      () => widget.updateProduct(
        token: widget.token,
        productId: product.id,
        name: draft.name,
        description: draft.description,
        value: draft.value,
        ingredientIds: draft.ingredientIds,
        hallIds: draft.hallIds,
      ),
    );
  }
}
