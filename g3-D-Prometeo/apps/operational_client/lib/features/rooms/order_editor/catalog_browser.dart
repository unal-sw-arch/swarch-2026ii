// ignore_for_file: invalid_use_of_protected_member

part of '../order_editor_dialog.dart';

extension _OrderEditorCatalogBrowser on _OrderEditorDialogState {
  List<_ProductSearchResult> _searchResults() {
    final query = _normalizeSearchText(_searchQuery.trim());
    if (query.isEmpty) return const [];
    final tokens = query
        .split(RegExp(r'\s+'))
        .where((token) => token.isNotEmpty)
        .toSet()
        .toList();
    final results = <_ProductSearchResult>[];
    for (final product in _products) {
      final category = _searchCategoryLabel(product.id) ?? '';
      final productName = _normalizeSearchText(product.name);
      final categoryName = _normalizeSearchText(category);
      final scores = [
        for (final token in tokens)
          _tokenMatchScore(productName, categoryName, token),
      ];
      final score = scores.any((value) => value == null)
          ? null
          : scores.whereType<int>().fold<int>(0, (sum, value) => sum + value);
      if (score != null) {
        results.add(
          _ProductSearchResult(
            product: product,
            categoryLabel: category,
            score: score,
          ),
        );
      }
    }
    results.sort((left, right) {
      final score = left.score.compareTo(right.score);
      if (score != 0) return score;
      return left.product.name.toLowerCase().compareTo(
        right.product.name.toLowerCase(),
      );
    });
    return results;
  }

  String _normalizeSearchText(String value) => value
      .toLowerCase()
      .replaceAll(RegExp('[áàäâ]'), 'a')
      .replaceAll(RegExp('[éèëê]'), 'e')
      .replaceAll(RegExp('[íìïî]'), 'i')
      .replaceAll(RegExp('[óòöô]'), 'o')
      .replaceAll(RegExp('[úùüû]'), 'u')
      .replaceAll('ñ', 'n');

  int? _tokenMatchScore(String product, String category, String token) {
    final productWords = product.split(RegExp(r'\s+'));
    final categoryWords = category.split(RegExp(r'[^a-z0-9]+'));
    if (product == token) return 0;
    if (productWords.contains(token)) return 5;
    if (product.startsWith(token)) return 10 + product.length - token.length;
    final productIndex = product.indexOf(token);
    if (productIndex >= 0) return 30 + productIndex;
    if (category == token) return 35;
    if (categoryWords.contains(token)) return 40;
    if (category.startsWith(token)) return 50 + category.length - token.length;
    final categoryIndex = category.indexOf(token);
    if (categoryIndex >= 0) return 70 + categoryIndex;
    final combined = '$product $category';
    var cursor = 0;
    var gaps = 0;
    for (final codeUnit in token.codeUnits) {
      final index = combined.indexOf(String.fromCharCode(codeUnit), cursor);
      if (index < 0) return null;
      gaps += index - cursor;
      cursor = index + 1;
    }
    return 110 + gaps;
  }

  Widget _catalog() {
    final searchResults = _searchResults();
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 14, 20, 4),
          child: TextField(
            key: const ValueKey('order-product-search'),
            controller: _searchController,
            focusNode: _searchFocus,
            textInputAction: TextInputAction.search,
            onChanged: (value) => setState(() => _searchQuery = value),
            decoration: InputDecoration(
              hintText: _es ? 'Buscar productos' : 'Search products',
              prefixIcon: const Icon(Icons.search_rounded),
              suffixIcon: _searchQuery.isEmpty
                  ? null
                  : IconButton(
                      key: const ValueKey('clear-order-product-search'),
                      onPressed: () {
                        _searchController.clear();
                        setState(() => _searchQuery = '');
                      },
                      icon: const Icon(Icons.close_rounded),
                    ),
              filled: true,
              fillColor: Colors.white,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(15),
                borderSide: const BorderSide(color: Color(0xFFDDE2E7)),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(15),
                borderSide: const BorderSide(color: Color(0xFFDDE2E7)),
              ),
            ),
          ),
        ),
        Expanded(
          child: _searchQuery.trim().isNotEmpty
              ? _searchCatalog(searchResults)
              : _categoryCatalog(),
        ),
      ],
    );
  }

  Widget _searchCatalog(List<_ProductSearchResult> results) {
    if (results.isEmpty) {
      return Center(
        child: Text(
          _es ? 'No se encontraron productos.' : 'No products found.',
          style: const TextStyle(color: Color(0xFF73777C)),
        ),
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 850) {
          return ListView.builder(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
            itemCount: results.length,
            itemBuilder: (_, index) {
              final result = results[index];
              return _productRow(
                result.product,
                categoryLabel: result.categoryLabel,
              );
            },
          );
        }
        return GridView.builder(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
          itemCount: results.length,
          gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
            maxCrossAxisExtent: 260,
            mainAxisExtent: 220,
            mainAxisSpacing: 10,
            crossAxisSpacing: 10,
          ),
          itemBuilder: (_, index) {
            final result = results[index];
            return _productRow(
              result.product,
              categoryLabel: result.categoryLabel,
              desktopTile: true,
            );
          },
        );
      },
    );
  }

  Widget _categoryCatalog() {
    final current = _categories
        .where((item) => item.id == _categoryId)
        .firstOrNull;
    if (current == null) {
      final roots = _categories
          .where((item) => item.parentCategoryId == null)
          .toList();
      return LayoutBuilder(
        builder: (context, constraints) {
          final columns = constraints.maxWidth >= 1000
              ? 5
              : constraints.maxWidth >= 760
              ? 4
              : constraints.maxWidth >= 520
              ? 3
              : 2;
          return GridView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
            itemCount: roots.length,
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: columns,
              mainAxisSpacing: 12,
              crossAxisSpacing: 12,
              childAspectRatio: 1,
            ),
            itemBuilder: (_, index) {
              final category = roots[index];
              return _CategoryButton(
                key: ValueKey('order-category-${category.id}'),
                category: category,
                square: true,
                onTap: () => setState(() => _categoryId = category.id),
              );
            },
          );
        },
      );
    }
    final children = _categories
        .where((item) => item.parentCategoryId == current.id)
        .toList();
    final products = current.products;
    return LayoutBuilder(
      builder: (context, constraints) {
        final desktop = constraints.maxWidth >= 850;
        final columns = (constraints.maxWidth / 250).floor().clamp(2, 8);
        final tileWidth =
            (constraints.maxWidth - 40 - (columns - 1) * 10) / columns;
        return ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Row(
              children: [
                IconButton(
                  onPressed: () =>
                      setState(() => _categoryId = current.parentCategoryId),
                  icon: const Icon(Icons.arrow_back),
                ),
                Expanded(
                  child: Text(
                    current.name,
                    style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
            if (children.isNotEmpty) ...[
              const SizedBox(height: 8),
              if (desktop)
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: [
                    for (final category in children)
                      SizedBox(
                        width: tileWidth,
                        child: _CategoryButton(
                          key: ValueKey('order-category-${category.id}'),
                          category: category,
                          onTap: () =>
                              setState(() => _categoryId = category.id),
                        ),
                      ),
                  ],
                )
              else
                for (var index = 0; index < children.length; index++) ...[
                  _CategoryButton(
                    key: ValueKey('order-category-${children[index].id}'),
                    category: children[index],
                    onTap: () =>
                        setState(() => _categoryId = children[index].id),
                  ),
                  if (index < children.length - 1) const SizedBox(height: 10),
                ],
              const SizedBox(height: 16),
            ],
            if (desktop)
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  for (final product in products)
                    SizedBox(
                      width: tileWidth,
                      height: 220,
                      child: _productRow(
                        product,
                        categoryLabel: _categoryPathForProduct(product.id),
                        desktopTile: true,
                      ),
                    ),
                ],
              )
            else
              for (final product in products)
                _productRow(
                  product,
                  categoryLabel: _categoryPathForProduct(product.id),
                ),
          ],
        );
      },
    );
  }

  Widget _productRow(
    ClientMenuProduct product, {
    required String? categoryLabel,
    bool desktopTile = false,
  }) {
    final quantity = _lines[_defaultKey(product.id)]?.quantity ?? 0;
    return Card(
      key: ValueKey('order-product-${product.id}'),
      child: InkWell(
        onTap: () => _showProductDetails(product),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                product.name,
                textAlign: TextAlign.center,
                maxLines: desktopTile ? 2 : null,
                overflow: desktopTile ? TextOverflow.ellipsis : null,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                formatPesos(product.value),
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: Color(0xFF71859B),
                  fontWeight: FontWeight.w700,
                ),
              ),
              if (categoryLabel?.isNotEmpty == true) ...[
                const SizedBox(height: 3),
                Text(
                  categoryLabel!,
                  textAlign: TextAlign.center,
                  maxLines: desktopTile ? 2 : null,
                  overflow: desktopTile ? TextOverflow.ellipsis : null,
                  style: const TextStyle(
                    color: Color(0xFF71859B),
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
              if ((product.description ?? '').isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(
                    product.description!,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: Color(0xFF777B80)),
                  ),
                ),
              if (desktopTile) const Spacer() else const SizedBox(height: 7),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  IconButton(
                    key: ValueKey('decrease-product-${product.id}'),
                    onPressed: quantity == 0
                        ? null
                        : () => _change(product, -1),
                    icon: const Icon(Icons.remove_circle_outline),
                  ),
                  SizedBox(
                    width: 32,
                    child: Text('$quantity', textAlign: TextAlign.center),
                  ),
                  IconButton(
                    key: ValueKey('increase-product-${product.id}'),
                    onPressed: () => _change(product, 1),
                    icon: const Icon(Icons.add_circle_outline),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
