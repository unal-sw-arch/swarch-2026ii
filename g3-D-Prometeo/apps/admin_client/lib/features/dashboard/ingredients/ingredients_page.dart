import 'package:flutter/material.dart';

import '../../../services/admin_api.dart';
import '../menus/catalog_models.dart';

typedef LoadIngredientCatalog = Future<CatalogSnapshot> Function(String token);
typedef AddIngredientCategory =
    Future<IngredientCategory> Function({
      required String token,
      required String name,
    });
typedef AddCategorizedIngredient =
    Future<CatalogIngredient> Function({
      required String token,
      required String name,
      required int categoryId,
      String? description,
    });

class IngredientsPage extends StatefulWidget {
  const IngredientsPage({
    super.key,
    required this.spanish,
    required this.token,
    this.loadCatalog = getCatalog,
    this.addCategory = createIngredientCategory,
    this.addIngredient = createIngredient,
  });
  final bool spanish;
  final String token;
  final LoadIngredientCatalog loadCatalog;
  final AddIngredientCategory addCategory;
  final AddCategorizedIngredient addIngredient;
  @override
  State<IngredientsPage> createState() => _IngredientsPageState();
}

class _IngredientsPageState extends State<IngredientsPage> {
  CatalogSnapshot _catalog = const CatalogSnapshot(menus: [], ingredients: []);
  int? _categoryId;
  bool _loading = true;
  String? _error;
  bool get _es => widget.spanish;
  IngredientCategory? get _category => _catalog.ingredientCategories
      .where((item) => item.id == _categoryId)
      .firstOrNull;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _catalog = await widget.loadCatalog(widget.token);
    } on Object {
      _error = _es
          ? 'No se pudieron cargar los ingredientes.'
          : 'Ingredients could not be loaded.';
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return Center(
        child: OutlinedButton.icon(
          onPressed: _load,
          icon: const Icon(Icons.refresh),
          label: Text(_error!),
        ),
      );
    }
    final category = _category;
    return ListView(
      padding: const EdgeInsets.fromLTRB(28, 28, 28, 40),
      children: [
        Wrap(
          alignment: WrapAlignment.spaceBetween,
          crossAxisAlignment: WrapCrossAlignment.center,
          spacing: 16,
          runSpacing: 12,
          children: [
            SizedBox(
              width: 500,
              child: Row(
                children: [
                  if (category != null)
                    IconButton(
                      key: const ValueKey('back-to-ingredient-categories'),
                      onPressed: () => setState(() => _categoryId = null),
                      icon: const Icon(Icons.arrow_back),
                    ),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          category?.name ??
                              (_es ? 'Ingredientes' : 'Ingredients'),
                          style: const TextStyle(
                            fontSize: 28,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          category == null
                              ? (_es
                                    ? 'Organiza los ingredientes en categorías reutilizables.'
                                    : 'Organize ingredients into reusable categories.')
                              : (_es
                                    ? 'Ingredientes disponibles en esta categoría.'
                                    : 'Ingredients available in this category.'),
                          style: const TextStyle(color: Color(0xFF72767B)),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            if (category == null)
              FilledButton.icon(
                key: const ValueKey('add-ingredient-category'),
                onPressed: _createCategory,
                icon: const Icon(Icons.add),
                label: Text(_es ? 'Añadir categoría' : 'Add category'),
              )
            else
              FilledButton.icon(
                key: const ValueKey('add-ingredient-in-category'),
                onPressed: () => _createIngredient(category),
                icon: const Icon(Icons.add),
                label: Text(_es ? 'Añadir ingrediente' : 'Add ingredient'),
              ),
          ],
        ),
        const SizedBox(height: 24),
        if (category == null) _categories() else _ingredients(category),
      ],
    );
  }

  Widget _categories() {
    if (_catalog.ingredientCategories.isEmpty) {
      return _empty(
        _es
            ? 'Crea la primera categoría de ingredientes.'
            : 'Create the first ingredient category.',
      );
    }
    return Column(
      children: [
        for (final category in _catalog.ingredientCategories) ...[
          _IngredientCard(
            key: ValueKey('ingredient-category-${category.id}'),
            title: category.name,
            subtitle:
                '${category.ingredients.length} ${_es ? 'ingredientes' : 'ingredients'}',
            onTap: () => setState(() => _categoryId = category.id),
          ),
          const SizedBox(height: 12),
        ],
      ],
    );
  }

  Widget _ingredients(IngredientCategory category) {
    if (category.ingredients.isEmpty) {
      return _empty(
        _es
            ? 'Esta categoría aún no tiene ingredientes.'
            : 'This category has no ingredients yet.',
      );
    }
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE5E7E9)),
      ),
      child: Column(
        children: [
          for (final ingredient in category.ingredients)
            ListTile(
              leading: const CircleAvatar(
                backgroundColor: Color(0xFFEAF0E8),
                child: Icon(Icons.grass_outlined, color: Color(0xFF688064)),
              ),
              title: Text(ingredient.name),
              subtitle: Text(ingredient.description ?? ''),
            ),
        ],
      ),
    );
  }

  Widget _empty(String text) => Container(
    alignment: Alignment.center,
    padding: const EdgeInsets.all(42),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
    ),
    child: Text(text, style: const TextStyle(color: Color(0xFF72767B))),
  );

  Future<void> _createCategory() async {
    final controller = TextEditingController();
    final name = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          _es ? 'Nueva categoría de ingredientes' : 'New ingredient category',
        ),
        content: TextField(
          key: const ValueKey('new-ingredient-category-name'),
          controller: controller,
          autofocus: true,
          decoration: InputDecoration(labelText: _es ? 'Nombre' : 'Name'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(_es ? 'Cancelar' : 'Cancel'),
          ),
          FilledButton(
            key: const ValueKey('submit-ingredient-category'),
            onPressed: () {
              if (controller.text.trim().length >= 2) {
                Navigator.pop(context, controller.text.trim());
              }
            },
            child: Text(_es ? 'Crear' : 'Create'),
          ),
        ],
      ),
    );
    Future<void>.delayed(const Duration(milliseconds: 400), controller.dispose);
    if (name == null) return;
    await _write(() => widget.addCategory(token: widget.token, name: name));
  }

  Future<void> _createIngredient(IngredientCategory category) async {
    final name = TextEditingController();
    final description = TextEditingController();
    final draft = await showDialog<(String, String)>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_es ? 'Nuevo ingrediente' : 'New ingredient'),
        content: SizedBox(
          width: 430,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                key: const ValueKey('new-categorized-ingredient-name'),
                controller: name,
                autofocus: true,
                decoration: InputDecoration(labelText: _es ? 'Nombre' : 'Name'),
              ),
              TextField(
                controller: description,
                decoration: InputDecoration(
                  labelText: _es ? 'Descripción' : 'Description',
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(_es ? 'Cancelar' : 'Cancel'),
          ),
          FilledButton(
            key: const ValueKey('submit-categorized-ingredient'),
            onPressed: () {
              if (name.text.trim().length >= 2) {
                Navigator.pop(context, (
                  name.text.trim(),
                  description.text.trim(),
                ));
              }
            },
            child: Text(_es ? 'Crear' : 'Create'),
          ),
        ],
      ),
    );
    Future<void>.delayed(const Duration(milliseconds: 400), () {
      name.dispose();
      description.dispose();
    });
    if (draft == null) return;
    await _write(
      () => widget.addIngredient(
        token: widget.token,
        name: draft.$1,
        description: draft.$2,
        categoryId: category.id,
      ),
    );
  }

  Future<void> _write(Future<Object> Function() operation) async {
    try {
      await operation();
      if (mounted) await _load();
    } on Object {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: const Color(0xFFB64A4A),
            content: Text(_es ? 'No se pudo guardar.' : 'Could not save.'),
          ),
        );
      }
    }
  }
}

class _IngredientCard extends StatelessWidget {
  const _IngredientCard({
    super.key,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(15),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(15),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            const CircleAvatar(
              backgroundColor: Color(0xFFEAF0E8),
              child: Icon(Icons.grass_outlined, color: Color(0xFF688064)),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  Text(
                    subtitle,
                    style: const TextStyle(color: Color(0xFF72767B)),
                  ),
                ],
              ),
            ),
            const Icon(Icons.chevron_right),
          ],
        ),
      ),
    ),
  );
}
