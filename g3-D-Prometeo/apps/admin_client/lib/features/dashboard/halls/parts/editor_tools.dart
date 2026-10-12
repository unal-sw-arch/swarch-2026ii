// ignore_for_file: invalid_use_of_protected_member

part of '../hall_layout_page.dart';

extension _HallEditorTools on _HallLayoutPageState {
  Widget _editTools(bool compact) => Material(
    color: Colors.white,
    elevation: 5,
    borderRadius: BorderRadius.circular(18),
    child: compact
        ? Row(mainAxisSize: MainAxisSize.min, children: _toolButtons())
        : Column(mainAxisSize: MainAxisSize.min, children: _toolButtons()),
  );

  List<Widget> _toolButtons() => [
    _ToolButton(
      icon: Icons.table_restaurant_outlined,
      tooltip: _es ? 'Crear mesa' : 'Create table',
      onPressed: _createTable,
    ),
    _ToolButton(
      icon: Icons.architecture_outlined,
      tooltip: _es ? 'Agregar pared' : 'Add wall',
      onPressed: _createWall,
    ),
    _ToolButton(
      icon: Icons.link,
      tooltip: _es ? 'Agrupar mesas' : 'Group tables',
      onPressed: _layout.selectedTableIds.length >= 2 ? _groupSelected : null,
    ),
    _ToolButton(
      icon: Icons.link_off,
      tooltip: _es ? 'Separar grupo' : 'Ungroup',
      onPressed:
          _layout.selectedTableIds.any(
            (id) => _layout.groupForTable(id) != null,
          )
          ? _layout.ungroupSelected
          : null,
    ),
    const SizedBox(width: 1, height: 1),
    _ToolButton(
      icon: Icons.delete_outline,
      color: const Color(0xFFD73333),
      tooltip: _es ? 'Eliminar selección' : 'Delete selection',
      onPressed:
          _layout.selectedTableIds.isEmpty && _layout.selectedWallId == null
          ? null
          : _confirmDelete,
    ),
  ];

  Future<void> _groupSelected() async {
    final controller = TextEditingController();
    final name = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(_es ? 'Nombre de la mesa' : 'Table name'),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLength: 50,
          decoration: InputDecoration(
            labelText: _es ? 'Nombre opcional' : 'Optional name',
            helperText: _es
                ? 'Vacío: se combinarán los nombres de las mesas.'
                : 'Empty: the table names will be combined.',
          ),
          onSubmitted: (_) => Navigator.pop(dialogContext, controller.text),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(_es ? 'Cancelar' : 'Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, controller.text),
            child: Text(_es ? 'Agrupar' : 'Group'),
          ),
        ],
      ),
    );
    // showDialog devuelve el resultado al comenzar la animación de salida.
    // El TextField todavía usa su controller hasta que la ruta desaparece.
    Future<void>.delayed(const Duration(milliseconds: 400), controller.dispose);
    if (name != null && mounted) _layout.groupSelected(identifier: name);
  }

  Widget _cameraTools() => Material(
    color: Colors.white,
    elevation: 3,
    borderRadius: BorderRadius.circular(22),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          onPressed: () => _zoom(.82),
          icon: const Icon(Icons.remove),
          tooltip: _es ? 'Alejar' : 'Zoom out',
        ),
        IconButton(
          onPressed: () => _zoom(1.22),
          icon: const Icon(Icons.add),
          tooltip: _es ? 'Acercar' : 'Zoom in',
        ),
        IconButton(
          onPressed: () => setState(() => _rotation -= math.pi / 2),
          icon: const Icon(Icons.rotate_left),
          tooltip: _es ? 'Rotar' : 'Rotate',
        ),
        IconButton(
          onPressed: () {
            _focusLayout();
          },
          icon: const Icon(Icons.center_focus_strong),
          tooltip: _es ? 'Centrar' : 'Reset camera',
        ),
      ],
    ),
  );

  void _zoom(double factor) {
    final matrix = _camera.value.clone();
    matrix.scaleByDouble(factor, factor, factor, 1);
    _camera.value = matrix;
  }

  Future<void> _createTable() async {
    final controller = TextEditingController();
    final identifier = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_es ? 'Nueva mesa' : 'New table'),
        content: TextField(
          key: const ValueKey('new-table-identifier'),
          controller: controller,
          autofocus: true,
          maxLength: 50,
          decoration: InputDecoration(
            labelText: _es ? 'Identificador visible' : 'Visible identifier',
          ),
          onSubmitted: (value) {
            if (value.trim().isNotEmpty) Navigator.pop(context, value.trim());
          },
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(_es ? 'Cancelar' : 'Cancel'),
          ),
          FilledButton(
            onPressed: () {
              if (controller.text.trim().isNotEmpty) {
                Navigator.pop(context, controller.text.trim());
              }
            },
            child: Text(_es ? 'Crear' : 'Create'),
          ),
        ],
      ),
    );
    // showDialog completa al iniciar la animación de salida; el TextField aún
    // puede vivir algunos frames y no debe perder su controller antes.
    Future<void>.delayed(const Duration(milliseconds: 400), controller.dispose);
    if (identifier == null || !mounted) return;
    if (_layout.tables.any((table) => table.identifier == identifier)) {
      _message(
        _es
            ? 'Ese identificador ya existe.'
            : 'That identifier already exists.',
        error: true,
      );
      return;
    }
    _layout.createTable(identifier, center: _visibleLayoutCenter());
  }

  void _createWall() {
    _layout.addWall(center: _visibleLayoutCenter());
  }

  void _copySelection() {
    if (!_editing) return;
    final table = _layout.selectedTable;
    final wall = _layout.selectedWall;
    if (table == null && wall == null) return;
    _copiedObject = table != null
        ? _CopiedLayoutObject.table(table)
        : _CopiedLayoutObject.wall(wall!);
    _message(_es ? 'Objeto copiado.' : 'Object copied.');
  }

  void _pasteCopied(Offset position) {
    if (!_editing) return;
    final copied = _copiedObject;
    if (copied == null) return;
    if (copied.table != null) {
      _layout.pasteTable(copied.table!, position);
    } else {
      _layout.pasteWall(copied.wall!, position);
    }
  }

  Future<void> _showObjectContextMenu(
    Offset globalPosition, {
    int? tableId,
    int? wallId,
  }) async {
    if (tableId != null) {
      _layout.selectTable(tableId);
    } else if (wallId != null) {
      _layout.selectWall(wallId);
    }
    final action = await _showEditorMenu(globalPosition, const [
      _EditorMenuAction.copy,
    ]);
    if (!mounted) return;
    if (action == _EditorMenuAction.copy) _copySelection();
  }

  Future<void> _showBackgroundContextMenu(TapDownDetails details) async {
    _layout.clearSelection();
    final position = _layoutPosition(details.globalPosition);
    if (position != null) _lastPointerPosition = position;
    final action = await _showEditorMenu(details.globalPosition, const [
      _EditorMenuAction.paste,
    ]);
    if (!mounted || action != _EditorMenuAction.paste) return;
    _pasteCopied(position ?? _visibleLayoutCenter());
  }

  Future<_EditorMenuAction?> _showEditorMenu(
    Offset globalPosition,
    List<_EditorMenuAction> actions,
  ) {
    final overlay = Overlay.of(context).context.findRenderObject() as RenderBox;
    final local = overlay.globalToLocal(globalPosition);
    return showMenu<_EditorMenuAction>(
      context: context,
      position: RelativeRect.fromLTRB(
        local.dx,
        local.dy,
        overlay.size.width - local.dx,
        overlay.size.height - local.dy,
      ),
      items: [
        for (final action in actions)
          PopupMenuItem<_EditorMenuAction>(
            value: action,
            enabled: action != _EditorMenuAction.paste || _copiedObject != null,
            child: Row(
              children: [
                Icon(
                  action == _EditorMenuAction.copy
                      ? Icons.copy_outlined
                      : Icons.content_paste_outlined,
                  size: 18,
                ),
                const SizedBox(width: 10),
                Text(
                  action == _EditorMenuAction.copy
                      ? (_es ? 'Copiar' : 'Copy')
                      : (_es ? 'Pegar' : 'Paste'),
                ),
              ],
            ),
          ),
      ],
    );
  }

  Future<void> _confirmDelete() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_es ? 'Eliminar selección' : 'Delete selection'),
        content: Text(
          _es
              ? 'Esta acción eliminará los elementos seleccionados al guardar los cambios.'
              : 'This will delete the selected items when changes are saved.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(_es ? 'Cancelar' : 'Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFFD33A3A),
            ),
            onPressed: () => Navigator.pop(context, true),
            child: Text(_es ? 'Eliminar' : 'Delete'),
          ),
        ],
      ),
    );
    if (confirmed == true) _layout.deleteSelected();
  }
}
