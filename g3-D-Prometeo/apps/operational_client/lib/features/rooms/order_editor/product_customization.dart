// ignore_for_file: invalid_use_of_protected_member

part of '../order_editor_dialog.dart';

extension _OrderEditorProductCustomization on _OrderEditorDialogState {
  void _change(ClientMenuProduct product, int delta) {
    _remember();
    setState(() {
      final key = _defaultKey(product.id);
      final line = _lines.putIfAbsent(key, () => _DraftLine(product: product));
      line.quantity = (line.quantity + delta).clamp(0, 99);
      if (line.quantity == 0) _lines.remove(key);
      _mergeIdenticalLines();
    });
  }

  bool _canRemove(String key) {
    final line = _lines[key];
    if (line == null || line.quantity <= line.deliveredQuantity) return false;
    if (line.parentLineKey != null) return true;
    return !_lines.values.any(
      (candidate) =>
          candidate.parentLineKey == key && candidate.deliveredQuantity > 0,
    );
  }

  void _removeOne(String key) {
    if (!_canRemove(key)) return;
    _remember();
    setState(() {
      final line = _lines[key]!;
      line.quantity--;
      if (line.quantity == 0) {
        _lines.remove(key);
        _lines.removeWhere((_, candidate) => candidate.parentLineKey == key);
      }
    });
  }

  Future<void> _showProductDetails(
    ClientMenuProduct product, {
    String? draftKey,
  }) async {
    final editingExisting = draftKey != null && _lines.containsKey(draftKey);
    final line = editingExisting
        ? _lines[draftKey]!
        : _DraftLine(product: product, quantity: 1);
    final notes = TextEditingController(text: line.specifications);
    final specialSearchController = TextEditingController();
    var specialSearchQuery = '';
    final removed = {...line.removedIngredientIds};
    final specialCategories = _isSpecialProduct(product.id)
        ? const <ClientMenuCategory>[]
        : [
            for (final menu in widget.menus)
              if (menu.categories.any(
                (category) => category.products.any(
                  (candidate) => candidate.id == product.id,
                ),
              ))
                ...menu.categories.where((category) => category.isSpecial),
          ];
    final specialQuantities = {
      for (final category in specialCategories)
        for (final special in category.products)
          special.id: editingExisting
              ? _lines.values
                        .where(
                          (line) =>
                              line.parentLineKey == draftKey &&
                              line.product.id == special.id,
                        )
                        .firstOrNull
                        ?.quantity ??
                    0
              : 0,
    };
    final specialRoots = specialCategories
        .where((category) => category.parentCategoryId == null)
        .toList();
    Iterable<ClientMenuCategory> categoryBranch(ClientMenuCategory root) sync* {
      yield root;
      for (final child in specialCategories.where(
        (candidate) => candidate.parentCategoryId == root.id,
      )) {
        yield* categoryBranch(child);
      }
    }

    int categoryQuantity(ClientMenuCategory category) =>
        categoryBranch(category).fold(
          0,
          (total, candidate) =>
              total +
              candidate.products.fold(
                0,
                (subtotal, product) =>
                    subtotal + (specialQuantities[product.id] ?? 0),
              ),
        );
    List<({ClientMenuCategory category, ClientMenuProduct product})>
    specialSearchResults() {
      final query = _normalizeSearchText(specialSearchQuery.trim());
      if (query.isEmpty) return const [];
      final tokens = query.split(RegExp(r'\s+'));
      final results =
          <({ClientMenuCategory category, ClientMenuProduct product})>[];
      for (final category in specialCategories) {
        for (final special in category.products) {
          final searchable = _normalizeSearchText(
            '${special.name} ${_categoryPathForProduct(special.id) ?? category.name}',
          );
          if (tokens.every(searchable.contains)) {
            results.add((category: category, product: special));
          }
        }
      }
      results.sort(
        (left, right) => left.product.name.toLowerCase().compareTo(
          right.product.name.toLowerCase(),
        ),
      );
      return results;
    }

    int? selectedSpecialCategoryId;
    final customizationScrollController = ScrollController();
    final specialSearchAnchor = GlobalKey();
    final specialPanelKeys = <int, GlobalKey>{};
    var customizationOpen = true;
    void revealSpecialSearch() {
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        // Espera a que el teclado reduzca el viewport antes de calcular la
        // posición final del buscador.
        await Future<void>.delayed(const Duration(milliseconds: 280));
        if (!customizationOpen || !customizationScrollController.hasClients) {
          return;
        }
        final targetContext = specialSearchAnchor.currentContext;
        if (targetContext == null || !targetContext.mounted) return;
        await Scrollable.ensureVisible(
          targetContext,
          duration: const Duration(milliseconds: 260),
          curve: Curves.easeOutCubic,
          alignment: 0,
          alignmentPolicy: ScrollPositionAlignmentPolicy.explicit,
        );
      });
    }

    void revealSpecialProducts(int categoryId) {
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        await Future<void>.delayed(const Duration(milliseconds: 230));
        if (!customizationOpen || !customizationScrollController.hasClients) {
          return;
        }
        final targetContext = specialPanelKeys[categoryId]?.currentContext;
        if (targetContext == null || !targetContext.mounted) return;
        await Scrollable.ensureVisible(
          targetContext,
          duration: const Duration(milliseconds: 280),
          curve: Curves.easeOutCubic,
          alignment: 0,
        );
      });
    }

    await showGeneralDialog<void>(
      context: context,
      barrierDismissible: true,
      barrierLabel: _es ? 'Editar producto' : 'Edit product',
      barrierColor: Colors.black54,
      transitionDuration: const Duration(milliseconds: 180),
      transitionBuilder: (_, animation, _, child) => FadeTransition(
        opacity: CurvedAnimation(parent: animation, curve: Curves.easeOutCubic),
        child: ScaleTransition(
          scale: Tween(begin: .98, end: 1.0).animate(animation),
          child: child,
        ),
      ),
      pageBuilder: (context, _, _) => StatefulBuilder(
        builder: (context, modalSetState) => AlertDialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(22),
          ),
          titlePadding: const EdgeInsets.fromLTRB(24, 22, 12, 8),
          title: Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: const Color(0xFFE9EFF5),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(
                  Icons.restaurant_menu,
                  color: Color(0xFF71859B),
                ),
              ),
              const SizedBox(width: 13),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      product.name,
                      style: const TextStyle(
                        fontSize: 21,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    Text(
                      formatPesos(product.value),
                      style: const TextStyle(
                        color: Color(0xFF71859B),
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
              ),
              IconButton(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.close),
              ),
            ],
          ),
          content: SizedBox(
            width: 620,
            child: SingleChildScrollView(
              key: const ValueKey('product-customization-scroll'),
              controller: customizationScrollController,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  if ((product.description ?? '').isNotEmpty) ...[
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: const Color(0xFFF4F5F6),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(
                        product.description!,
                        style: const TextStyle(
                          color: Color(0xFF5F646A),
                          height: 1.35,
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],
                  TextField(
                    controller: notes,
                    maxLines: 3,
                    decoration: InputDecoration(
                      labelText: _es
                          ? 'Descripción o indicaciones'
                          : 'Notes or instructions',
                    ),
                  ),
                  const SizedBox(height: 16),
                  _SectionTitle(
                    icon: Icons.eco_outlined,
                    label: _es ? 'Ingredientes' : 'Ingredients',
                  ),
                  if (product.ingredients.isEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(
                        _es
                            ? 'Este producto no tiene ingredientes registrados.'
                            : 'This product has no registered ingredients.',
                        style: const TextStyle(color: Color(0xFF7A7D82)),
                      ),
                    ),
                  for (final ingredient in product.ingredients)
                    Container(
                      margin: const EdgeInsets.only(top: 6),
                      decoration: BoxDecoration(
                        border: Border.all(color: const Color(0xFFE2E5E8)),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: CheckboxListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 10,
                        ),
                        dense: true,
                        title: Text(ingredient.name),
                        value: !removed.contains(ingredient.id),
                        onChanged: (included) => modalSetState(() {
                          included == true
                              ? removed.remove(ingredient.id)
                              : removed.add(ingredient.id);
                        }),
                      ),
                    ),
                  if (specialCategories.isNotEmpty) ...[
                    const SizedBox(height: 20),
                    _SectionTitle(
                      icon: Icons.auto_awesome_outlined,
                      label: _es
                          ? 'Personaliza con categorías especiales'
                          : 'Customize with special categories',
                    ),
                    const SizedBox(height: 10),
                    KeyedSubtree(
                      key: specialSearchAnchor,
                      child: TextField(
                        key: const ValueKey('special-product-search'),
                        controller: specialSearchController,
                        onTap: revealSpecialSearch,
                        onChanged: (value) => modalSetState(() {
                          specialSearchQuery = value;
                        }),
                        decoration: InputDecoration(
                          hintText: _es
                              ? 'Buscar adiciones y productos especiales'
                              : 'Search additions and special products',
                          prefixIcon: const Icon(Icons.search_rounded),
                          suffixIcon: specialSearchQuery.isEmpty
                              ? null
                              : IconButton(
                                  onPressed: () {
                                    specialSearchController.clear();
                                    modalSetState(
                                      () => specialSearchQuery = '',
                                    );
                                  },
                                  icon: const Icon(Icons.close_rounded),
                                ),
                          filled: true,
                          fillColor: const Color(0xFFF4F6F8),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(13),
                            borderSide: BorderSide.none,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                    if (specialSearchQuery.trim().isNotEmpty)
                      _SpecialSearchResults(
                        results: specialSearchResults(),
                        quantities: specialQuantities,
                        spanish: _es,
                        onChanged: (special, delta) => modalSetState(() {
                          specialQuantities[special.id] =
                              ((specialQuantities[special.id] ?? 0) + delta)
                                  .clamp(0, 99);
                        }),
                      )
                    else ...[
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          for (final category in specialRoots)
                            _SpecialCategoryButton(
                              key: ValueKey('special-category-${category.id}'),
                              category: category,
                              selected:
                                  selectedSpecialCategoryId == category.id ||
                                  specialCategories.any(
                                    (candidate) =>
                                        candidate.id ==
                                            selectedSpecialCategoryId &&
                                        candidate.parentCategoryId ==
                                            category.id,
                                  ),
                              quantity: categoryQuantity(category),
                              onTap: () {
                                final willOpen =
                                    selectedSpecialCategoryId != category.id;
                                modalSetState(() {
                                  selectedSpecialCategoryId = willOpen
                                      ? category.id
                                      : null;
                                });
                                if (willOpen) {
                                  revealSpecialProducts(category.id);
                                }
                              },
                            ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      AnimatedSwitcher(
                        duration: const Duration(milliseconds: 220),
                        switchInCurve: Curves.easeOutCubic,
                        switchOutCurve: Curves.easeInCubic,
                        transitionBuilder: (child, animation) => FadeTransition(
                          opacity: animation,
                          child: SizeTransition(
                            sizeFactor: animation,
                            alignment: Alignment.topCenter,
                            child: child,
                          ),
                        ),
                        child: selectedSpecialCategoryId == null
                            ? const SizedBox.shrink()
                            : _SpecialProductsPanel(
                                key: specialPanelKeys.putIfAbsent(
                                  selectedSpecialCategoryId!,
                                  GlobalKey.new,
                                ),
                                category: specialCategories.firstWhere(
                                  (item) =>
                                      item.id == selectedSpecialCategoryId,
                                ),
                                categories: specialCategories,
                                spanish: _es,
                                quantities: specialQuantities,
                                onCategorySelected: (categoryId) {
                                  modalSetState(() {
                                    selectedSpecialCategoryId = categoryId;
                                  });
                                  revealSpecialProducts(categoryId);
                                },
                                onChanged: (special, delta) => modalSetState(
                                  () {
                                    specialQuantities[special.id] =
                                        ((specialQuantities[special.id] ?? 0) +
                                                delta)
                                            .clamp(0, 99);
                                  },
                                ),
                              ),
                      ),
                    ],
                  ],
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(_es ? 'Cancelar' : 'Cancel'),
            ),
            FilledButton(
              onPressed: () {
                _remember();
                setState(() {
                  final targetKey = editingExisting
                      ? draftKey
                      : 'custom:${_nextLine++}';
                  line.specifications = notes.text.trim();
                  line.removedIngredientIds = removed;
                  if (line.quantity == 0) line.quantity = 1;
                  _lines[targetKey] = line;
                  final specialIds = {
                    for (final category in specialCategories)
                      for (final special in category.products) special.id,
                  };
                  _lines.removeWhere(
                    (_, candidate) =>
                        candidate.parentLineKey == targetKey &&
                        specialIds.contains(candidate.product.id),
                  );
                  for (final category in specialCategories) {
                    for (final special in category.products) {
                      final specialKey = 'special:$targetKey:${special.id}';
                      final quantity = specialQuantities[special.id] ?? 0;
                      if (quantity == 0) {
                        _lines.remove(specialKey);
                      } else {
                        final specialLine = _lines.putIfAbsent(
                          specialKey,
                          () => _DraftLine(
                            product: special,
                            parentLineKey: targetKey,
                          ),
                        );
                        specialLine.quantity = quantity;
                      }
                    }
                  }
                  _mergeIdenticalLines();
                });
                Navigator.pop(context);
              },
              child: Text(_es ? 'Guardar cambios' : 'Save changes'),
            ),
          ],
        ),
      ),
    );
    customizationOpen = false;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      customizationScrollController.dispose();
      notes.dispose();
      specialSearchController.dispose();
    });
  }
}
