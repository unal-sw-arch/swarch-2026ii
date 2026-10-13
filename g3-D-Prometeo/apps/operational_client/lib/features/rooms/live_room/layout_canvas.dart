// ignore_for_file: invalid_use_of_protected_member

part of '../live_room_page.dart';

extension _LiveRoomCanvas on _LiveRoomPageState {
  Widget _header() => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
    child: Row(
      children: [
        IconButton(
          key: const ValueKey('back-to-assigned-rooms'),
          onPressed: widget.onBack ?? () => Navigator.of(context).pop(),
          icon: const Icon(Icons.arrow_back),
        ),
        const SizedBox(width: 4),
        Expanded(
          child: Text(
            widget.room.name,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w700),
          ),
        ),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 6),
          decoration: BoxDecoration(
            color: const Color(0xFFE8F0E8),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (_saving) ...[
                const SizedBox.square(
                  dimension: 12,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
                const SizedBox(width: 7),
              ] else ...[
                const Icon(Icons.circle, size: 8, color: Color(0xFF5B9B66)),
                const SizedBox(width: 7),
              ],
              Text(
                _saving
                    ? (widget.spanish ? 'Guardando' : 'Saving')
                    : 'Live View',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(width: 8),
        if (widget.onEditLayout != null) ...[
          OutlinedButton.icon(
            key: const ValueKey('edit-layout-from-live-view'),
            onPressed: _saving ? null : widget.onEditLayout,
            icon: const Icon(Icons.edit_outlined, size: 18),
            label: Text(widget.spanish ? 'Editar layout' : 'Edit layout'),
          ),
          const SizedBox(width: 8),
        ],
        IconButton(
          onPressed: _saving ? null : _reload,
          tooltip: widget.spanish ? 'Actualizar' : 'Refresh',
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
  );

  Widget _errorView() => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(_error!, style: const TextStyle(color: Color(0xFFC94E4E))),
        const SizedBox(height: 12),
        OutlinedButton.icon(
          onPressed: _load,
          icon: const Icon(Icons.refresh),
          label: Text(widget.spanish ? 'Reintentar' : 'Retry'),
        ),
      ],
    ),
  );

  Widget _canvas() => ClipRect(
    key: _viewportKey,
    child: InteractiveViewer(
      key: const ValueKey('live-room-canvas'),
      transformationController: _camera,
      alignment: Alignment.topLeft,
      minScale: .35,
      maxScale: 3.5,
      boundaryMargin: const EdgeInsets.all(10000),
      constrained: false,
      panEnabled: _layout.interaction == LiveLayoutInteraction.normal,
      scaleEnabled: _layout.interaction == LiveLayoutInteraction.normal,
      child: Transform.rotate(
        angle: _rotation,
        alignment: Alignment.topLeft,
        origin: _LiveRoomPageState._canvasOrigin + _layout.contentBounds.center,
        child: SizedBox(
          width: _LiveRoomPageState._canvasExtent,
          height: _LiveRoomPageState._canvasExtent,
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              Positioned.fill(
                child: GestureDetector(
                  key: const ValueKey('live-layout-background'),
                  behavior: HitTestBehavior.opaque,
                  onTap: _canInteract ? _layout.clearSelection : null,
                ),
              ),
              for (final wall in _layout.walls) _wall(wall),
              for (final table in _layout.tables) _table(table),
            ],
          ),
        ),
      ),
    ),
  );

  Widget _wall(LiveRoomWall wall) => Positioned(
    left: _LiveRoomPageState._canvasOrigin.dx + wall.x,
    top: _LiveRoomPageState._canvasOrigin.dy + wall.y,
    width: wall.width,
    height: wall.height,
    child: Transform.rotate(
      angle: wall.rotation,
      child: const ColoredBox(color: Color(0xFF74818D)),
    ),
  );

  Widget _table(LiveRoomTable table) {
    final selected = _layout.selectedTableIds.contains(table.id);
    final invalid = selected && _layout.invalidPlacement;
    final grouped = _layout.groupForTable(table.id) != null;
    return Positioned(
      left: _LiveRoomPageState._canvasOrigin.dx + table.x,
      top: _LiveRoomPageState._canvasOrigin.dy + table.y,
      width: table.width,
      height: table.height,
      child: Transform.rotate(
        angle: table.rotation,
        child: Listener(
          key: ValueKey('live-table-${table.id}'),
          onPointerDown: _canInteract && _desktop
              ? (event) => _pointerDown(event, table.id)
              : null,
          onPointerMove: _canInteract && _desktop ? _pointerMove : null,
          onPointerUp: _canInteract && _desktop ? _pointerUp : null,
          onPointerCancel: _canInteract && _desktop ? _pointerCancel : null,
          child: GestureDetector(
            onTap: _canInteract && !_desktop
                ? () => _activateTable(table)
                : null,
            onLongPressStart: _canInteract && !_desktop
                ? (details) {
                    HapticFeedback.mediumImpact();
                    _dragDelta = Offset.zero;
                    _lastGlobalPosition = details.globalPosition;
                    _layout.beginMove(table.id);
                  }
                : null,
            onLongPressMoveUpdate: _canInteract && !_desktop
                ? (details) {
                    final delta = details.globalPosition - _lastGlobalPosition;
                    _lastGlobalPosition = details.globalPosition;
                    _dragDelta += _logicalDelta(delta);
                    _layout.moveBy(_dragDelta);
                  }
                : null,
            onLongPressEnd: _canInteract && !_desktop
                ? (_) {
                    if (_layout.endMove()) _persist();
                  }
                : null,
            child: MouseRegion(
              cursor: _canInteract
                  ? SystemMouseCursors.move
                  : MouseCursor.defer,
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 100),
                decoration: BoxDecoration(
                  color: invalid
                      ? const Color(0xFFFFE4E2)
                      : table.status == 'waiting'
                      ? const Color(0xFFFFF1B8)
                      : table.status == 'eating'
                      ? const Color(0xFFDDF1D9)
                      : const Color(0xFFF7F6F4),
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(
                    color: invalid
                        ? const Color(0xFFE14B43)
                        : selected
                        ? const Color(0xFF4C9EF8)
                        : const Color(0xFFD4D8DC),
                    width: selected ? 2 : 1,
                  ),
                  boxShadow: const [
                    BoxShadow(color: Color(0x10000000), blurRadius: 7),
                  ],
                ),
                child: Center(
                  child: Transform.rotate(
                    angle: -table.rotation - _rotation,
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (grouped) ...[
                          const Icon(
                            Icons.link,
                            size: 14,
                            color: Color(0xFF6D8DAC),
                          ),
                          const SizedBox(width: 4),
                        ],
                        Flexible(
                          child: Text(
                            table.identifier,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: selected
                                  ? const Color(0xFF4C9EF8)
                                  : const Color(0xFF6D7075),
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _pointerDown(PointerDownEvent event, int tableId) {
    if (_activePointer != null) return;
    _activePointer = event.pointer;
    _activeTable = tableId;
    _pointerMoved = false;
    _dragDelta = Offset.zero;
    _lastGlobalPosition = event.position;
    _layout.pressTable();
  }

  void _pointerMove(PointerMoveEvent event) {
    if (_activePointer != event.pointer || _activeTable == null) return;
    final delta = event.position - _lastGlobalPosition;
    _lastGlobalPosition = event.position;
    if (!_pointerMoved && delta.distanceSquared > 0) {
      _pointerMoved = true;
      _layout.beginMove(_activeTable!);
    }
    if (_pointerMoved) {
      _dragDelta += _logicalDelta(delta);
      _layout.moveBy(_dragDelta);
    }
  }

  void _pointerUp(PointerUpEvent event) {
    if (_activePointer != event.pointer || _activeTable == null) return;
    if (_pointerMoved) {
      if (_layout.endMove()) _persist();
    } else {
      _layout.cancelTablePress();
      final table = _layout.tables.firstWhere(
        (item) => item.id == _activeTable,
      );
      _activateTable(table);
    }
    _clearPointer();
  }

  void _pointerCancel(PointerCancelEvent event) {
    if (_activePointer != event.pointer) return;
    if (_pointerMoved) _layout.cancelMove();
    _layout.cancelTablePress();
    _clearPointer();
  }

  void _clearPointer() {
    _activePointer = null;
    _activeTable = null;
    _pointerMoved = false;
  }

  Offset _logicalDelta(Offset screenDelta) {
    final scale = _camera.value.getMaxScaleOnAxis();
    final dx = screenDelta.dx / scale;
    final dy = screenDelta.dy / scale;
    return Offset(
      dx * math.cos(_rotation) + dy * math.sin(_rotation),
      -dx * math.sin(_rotation) + dy * math.cos(_rotation),
    );
  }

  void _focusLayout() {
    final viewport = _viewportKey.currentContext?.findRenderObject();
    if (viewport is! RenderBox || !viewport.hasSize) return;
    final bounds = _layout.contentBounds.inflate(80);
    final availableWidth = math.max(1.0, viewport.size.width - 48);
    final availableHeight = math.max(1.0, viewport.size.height - 48);
    final scale = math
        .min(availableWidth / bounds.width, availableHeight / bounds.height)
        .clamp(.35, 1.35)
        .toDouble();
    final renderedCenter = bounds.center + _LiveRoomPageState._canvasOrigin;
    final translation = Offset(
      viewport.size.width / 2 - renderedCenter.dx * scale,
      viewport.size.height / 2 - renderedCenter.dy * scale,
    );
    if (_rotation != 0) setState(() => _rotation = 0);
    _camera.value = Matrix4.identity()
      ..translateByDouble(translation.dx, translation.dy, 0, 1)
      ..scaleByDouble(scale, scale, scale, 1);
  }

  void _rotateView() => setState(() => _rotation -= math.pi / 2);

  Widget _tools() => Material(
    key: const ValueKey('live-bottom-tools'),
    color: Colors.white,
    elevation: 4,
    borderRadius: BorderRadius.circular(20),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        _modeButton(
          key: const ValueKey('live-mode-edit'),
          mode: _LiveToolMode.edit,
          tooltip: widget.spanish ? 'Editar pedido' : 'Edit order',
          icon: Icons.edit_outlined,
        ),
        _modeButton(
          key: const ValueKey('live-mode-bill'),
          mode: _LiveToolMode.bill,
          tooltip: widget.spanish ? 'Facturar' : 'Bill',
          icon: Icons.receipt_long_outlined,
        ),
        _modeButton(
          key: const ValueKey('live-mode-select'),
          mode: _LiveToolMode.select,
          tooltip: widget.spanish ? 'Seleccionar' : 'Select',
          icon: Icons.touch_app_outlined,
        ),
        IconButton(
          key: const ValueKey('toggle-live-table-link'),
          onPressed:
              _canInteract &&
                  _layout.canToggleGroup &&
                  !(_layout.canUngroup && _selectedGroupHasActiveOrder)
              ? _toggleSelectedGroup
              : null,
          tooltip: _layout.canUngroup
              ? (widget.spanish ? 'Desenlazar selección' : 'Unlink selection')
              : (widget.spanish ? 'Enlazar selección' : 'Link selection'),
          icon: Icon(_layout.canUngroup ? Icons.link_off : Icons.link),
        ),
        IconButton(
          key: const ValueKey('rotate-live-room'),
          onPressed: _rotateView,
          tooltip: widget.spanish ? 'Rotar vista' : 'Rotate view',
          icon: const Icon(Icons.rotate_left),
        ),
        IconButton(
          key: const ValueKey('fit-live-room'),
          onPressed: _focusLayout,
          tooltip: widget.spanish ? 'Ver salón completo' : 'Fit to screen',
          icon: const Icon(Icons.center_focus_strong),
        ),
      ],
    ),
  );

  bool get _selectedGroupHasActiveOrder => _layout.selectedTableIds.any(
    (tableId) =>
        _layout.groupForTable(tableId) != null &&
        _orderForTable(tableId) != null,
  );

  Future<void> _toggleSelectedGroup() async {
    if (_layout.canUngroup) {
      if (_selectedGroupHasActiveOrder) {
        _showCenteredNotice(
          widget.spanish
              ? 'No puedes desenlazar mesas con un pedido activo.'
              : 'Tables with an active order cannot be unlinked.',
          warning: true,
        );
        return;
      }
      _layout.toggleSelectedGroup();
      await _persist();
      return;
    }
    final controller = TextEditingController();
    final name = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(widget.spanish ? 'Nombre de la mesa' : 'Table name'),
        content: TextField(
          key: const ValueKey('logical-table-name'),
          controller: controller,
          autofocus: true,
          maxLength: 50,
          decoration: InputDecoration(
            labelText: widget.spanish ? 'Nombre opcional' : 'Optional name',
            helperText: widget.spanish
                ? 'Si lo dejas vacío se usarán los nombres de las mesas.'
                : 'Leave empty to use the table names.',
          ),
          onSubmitted: (_) => Navigator.pop(dialogContext, controller.text),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(widget.spanish ? 'Cancelar' : 'Cancel'),
          ),
          FilledButton(
            key: const ValueKey('confirm-logical-table-name'),
            onPressed: () => Navigator.pop(dialogContext, controller.text),
            child: Text(widget.spanish ? 'Enlazar' : 'Link'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (name == null || !mounted) return;
    _layout.toggleSelectedGroup(identifier: name);
    await _persist();
  }

  Widget _modeButton({
    required Key key,
    required _LiveToolMode mode,
    required String tooltip,
    required IconData icon,
  }) => IconButton(
    key: key,
    isSelected: _mode == mode,
    style: IconButton.styleFrom(
      backgroundColor: _mode == mode ? const Color(0xFFDCE8F5) : null,
    ),
    onPressed: () {
      setState(() => _mode = mode);
      if (mode == _LiveToolMode.edit) _layout.clearSelection();
    },
    tooltip: tooltip,
    icon: Icon(icon),
  );
}
