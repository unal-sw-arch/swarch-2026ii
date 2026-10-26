part of '../menus_page.dart';

class _CategoryDraft {
  const _CategoryDraft(this.name, this.isSpecial);
  final String name;
  final bool isSpecial;
}

class _CategoryDialog extends StatefulWidget {
  const _CategoryDialog({required this.spanish, this.inheritedSpecial});
  final bool spanish;
  final bool? inheritedSpecial;
  @override
  State<_CategoryDialog> createState() => _CategoryDialogState();
}

class _CategoryDialogState extends State<_CategoryDialog> {
  final _name = TextEditingController();
  bool _special = false;
  bool get _subcategory => widget.inheritedSpecial != null;
  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(
      _subcategory
          ? (widget.spanish ? 'Nueva subcategoría' : 'New subcategory')
          : (widget.spanish ? 'Nueva categoría' : 'New category'),
    ),
    content: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        TextField(
          controller: _name,
          autofocus: true,
          decoration: InputDecoration(
            labelText: widget.spanish ? 'Nombre' : 'Name',
          ),
        ),
        if (!_subcategory)
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            value: _special,
            title: Text(
              widget.spanish ? 'Categoría especial' : 'Special category',
            ),
            onChanged: (value) => setState(() => _special = value),
          )
        else
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Text(
              widget.inheritedSpecial == true
                  ? (widget.spanish
                        ? 'Esta subcategoría también será especial.'
                        : 'This subcategory will also be special.')
                  : (widget.spanish
                        ? 'Esta subcategoría será normal.'
                        : 'This subcategory will be regular.'),
            ),
          ),
      ],
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
              _CategoryDraft(
                _name.text.trim(),
                widget.inheritedSpecial ?? _special,
              ),
            );
          }
        },
        child: Text(widget.spanish ? 'Crear' : 'Create'),
      ),
    ],
  );
}
