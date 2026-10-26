part of '../menus_page.dart';

class _IngredientDraft {
  const _IngredientDraft(this.name, this.description, this.categoryId);
  final String name;
  final String description;
  final int categoryId;
}

class _IngredientDialog extends StatefulWidget {
  const _IngredientDialog({required this.spanish, required this.categories});
  final bool spanish;
  final List<IngredientCategory> categories;
  @override
  State<_IngredientDialog> createState() => _IngredientDialogState();
}

class _IngredientDialogState extends State<_IngredientDialog> {
  final _name = TextEditingController();
  final _description = TextEditingController();
  late int _categoryId = widget.categories.first.id;
  @override
  void dispose() {
    _name.dispose();
    _description.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.spanish ? 'Nuevo ingrediente' : 'New ingredient'),
    content: SizedBox(
      width: 430,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _name,
            autofocus: true,
            decoration: InputDecoration(
              labelText: widget.spanish ? 'Nombre' : 'Name',
            ),
          ),
          TextField(
            controller: _description,
            decoration: InputDecoration(
              labelText: widget.spanish ? 'Descripción' : 'Description',
            ),
          ),
          DropdownButtonFormField<int>(
            initialValue: _categoryId,
            decoration: InputDecoration(
              labelText: widget.spanish ? 'Categoría' : 'Category',
            ),
            items: [
              for (final item in widget.categories)
                DropdownMenuItem(value: item.id, child: Text(item.name)),
            ],
            onChanged: (value) => setState(() => _categoryId = value!),
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: Text(widget.spanish ? 'Cancelar' : 'Cancel'),
      ),
      FilledButton(
        onPressed: () {
          if (_name.text.trim().length >= 2) {
            Navigator.pop(
              context,
              _IngredientDraft(
                _name.text.trim(),
                _description.text.trim(),
                _categoryId,
              ),
            );
          }
        },
        child: Text(widget.spanish ? 'Crear' : 'Create'),
      ),
    ],
  );
}
