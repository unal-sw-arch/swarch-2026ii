// ignore_for_file: invalid_use_of_protected_member

part of '../hall_layout_page.dart';

extension _HallLayoutCanvas on _HallLayoutPageState {
  Widget _canvas() => ClipRect(
    key: _viewportKey,
    child: MouseRegion(
      onHover: (event) {
        final position = _layoutPosition(event.position);
        if (position != null) _lastPointerPosition = position;
      },
      child: InteractiveViewer(
        key: const ValueKey('room-layout-canvas'),
        transformationController: _camera,
        alignment: Alignment.topLeft,
        minScale: .35,
        maxScale: 3.5,
        boundaryMargin: const EdgeInsets.all(10000),
        panEnabled:
            _layout.interaction == LayoutInteraction.normal || !_editing,
        scaleEnabled:
            _layout.interaction == LayoutInteraction.normal || !_editing,
        constrained: false,
        child: Transform.rotate(
          angle: _rotation,
          child: SizedBox(
            key: _layoutSpaceKey,
            width: _HallLayoutPageState._canvasExtent,
            height: _HallLayoutPageState._canvasExtent,
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                Positioned.fill(
                  child: GestureDetector(
                    key: const ValueKey('layout-background'),
                    behavior: HitTestBehavior.opaque,
                    onTap: _editing ? _layout.clearSelection : null,
                    onSecondaryTapDown: _editing
                        ? _showBackgroundContextMenu
                        : null,
                  ),
                ),
                for (final wall in _layout.walls) _wall(wall),
                for (final table in _layout.tables) _table(table),
              ],
            ),
          ),
        ),
      ),
    ),
  );

  Widget _wall(RoomWallModel wall) => Positioned(
    left: _HallLayoutPageState._canvasOrigin.dx + wall.x - 40,
    top: _HallLayoutPageState._canvasOrigin.dy + wall.y - 40,
    width: wall.width + 80,
    height: wall.height + 80,
    child: Transform.rotate(
      angle: wall.rotation,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Positioned(
            left: 40,
            top: 40,
            width: wall.width,
            height: wall.height,
            child: GestureDetector(
              onTap: _editing ? () => _layout.selectWall(wall.id) : null,
              onSecondaryTapDown: _editing
                  ? (details) => _showObjectContextMenu(
                      details.globalPosition,
                      wallId: wall.id,
                    )
                  : null,
              onPanStart: _editing
                  ? (details) {
                      _dragDelta = Offset.zero;
                      _lastGlobalPosition = details.globalPosition;
                      _layout.beginWallMove(wall.id);
                    }
                  : null,
              onPanUpdate: _editing
                  ? (details) {
                      final delta =
                          details.globalPosition - _lastGlobalPosition;
                      _lastGlobalPosition = details.globalPosition;
                      _dragDelta += _logicalDelta(delta);
                      _layout.moveWall(wall.id, _dragDelta);
                    }
                  : null,
              onPanEnd: _editing ? (_) => _layout.finishWallEditing() : null,
              onPanCancel: _editing ? _layout.cancelWallMove : null,
              child: MouseRegion(
                cursor: _editing ? SystemMouseCursors.move : MouseCursor.defer,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: _layout.selectedWallId == wall.id
                        ? const Color(0xFF5A7898)
                        : const Color(0xFF74818D),
                    border: _layout.selectedWallId == wall.id
                        ? Border.all(color: const Color(0xFF4C9EF8), width: 2)
                        : null,
                  ),
                ),
              ),
            ),
          ),
          if (_editing && _layout.selectedWallId == wall.id) ...[
            Positioned(
              left: 40 + wall.width - 7,
              top: 40 + wall.height - 7,
              child: GestureDetector(
                onPanStart: (details) {
                  _resizeDelta = Offset.zero;
                  _lastGlobalPosition = details.globalPosition;
                  _layout.beginWallResize(wall.id);
                },
                onPanUpdate: (details) {
                  final delta = details.globalPosition - _lastGlobalPosition;
                  _lastGlobalPosition = details.globalPosition;
                  _resizeDelta += _objectDelta(delta, wall.rotation);
                  _layout.resizeWallBy(_resizeDelta);
                },
                onPanEnd: (_) => _layout.endWallResize(),
                onPanCancel: _layout.cancelWallResize,
                child: const MouseRegion(
                  cursor: SystemMouseCursors.resizeDownRight,
                  child: _ResizeHandle(),
                ),
              ),
            ),
            Positioned(
              left: 40 + wall.width / 2 - 10,
              top: 2,
              child: _RotationHandle(
                key: ValueKey('rotate-wall-${wall.id}'),
                onStart: (position) {
                  _layout.beginObjectRotation();
                },
                onUpdate: (position) {
                  final pointer = _layoutPosition(position);
                  if (pointer != null) {
                    _layout.rotateWallToward(wall.id, pointer);
                  }
                },
                onEnd: _layout.finishObjectRotation,
              ),
            ),
          ],
        ],
      ),
    ),
  );

  Widget _table(RoomTableModel table) {
    final selected = _layout.selectedTableIds.contains(table.id);
    final invalid = selected && _layout.invalidPlacement;
    final magnet =
        selected && _layout.interaction == LayoutInteraction.magnetPreview;
    return Positioned(
      left: _HallLayoutPageState._canvasOrigin.dx + table.x - 40,
      top: _HallLayoutPageState._canvasOrigin.dy + table.y - 40,
      width: table.width + 80,
      height: table.height + 80,
      child: Transform.rotate(
        angle: table.rotation,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Positioned(
              left: 40,
              top: 40,
              width: table.width,
              height: table.height,
              child: Listener(
                key: ValueKey('room-table-${table.id}'),
                onPointerDown: _editingTables && _desktop
                    ? (event) => _desktopTablePointerDown(event, table.id)
                    : null,
                onPointerMove: _editingTables && _desktop
                    ? _desktopTablePointerMove
                    : null,
                onPointerUp: _editingTables && _desktop
                    ? _desktopTablePointerUp
                    : null,
                onPointerCancel: _editingTables && _desktop
                    ? _desktopTablePointerCancel
                    : null,
                child: GestureDetector(
                  onSecondaryTapDown: _editingTables
                      ? (details) => _showObjectContextMenu(
                          details.globalPosition,
                          tableId: table.id,
                        )
                      : null,
                  onTap: _editingTables && !_desktop
                      ? () => _layout.toggleSelection(table.id)
                      : null,
                  onLongPressStart: _editingTables && !_desktop
                      ? (details) {
                          _dragDelta = Offset.zero;
                          _lastGlobalPosition = details.globalPosition;
                          _layout.beginMove(table.id);
                        }
                      : null,
                  onLongPressMoveUpdate: _editingTables && !_desktop
                      ? (details) {
                          final delta =
                              details.globalPosition - _lastGlobalPosition;
                          _lastGlobalPosition = details.globalPosition;
                          _dragDelta += _logicalDelta(delta);
                          _layout.moveBy(_dragDelta);
                        }
                      : null,
                  onLongPressEnd: _editingTables && !_desktop
                      ? (_) => _layout.endMove()
                      : null,
                  child: MouseRegion(
                    cursor: _editingTables
                        ? SystemMouseCursors.move
                        : MouseCursor.defer,
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 110),
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
                        boxShadow: [
                          BoxShadow(
                            color: magnet
                                ? const Color(0x554C9EF8)
                                : const Color(0x10000000),
                            blurRadius: magnet ? 16 : 7,
                          ),
                        ],
                      ),
                      child: Center(
                        child: Transform.rotate(
                          angle: -table.rotation - _rotation,
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              if (_layout.groupForTable(table.id) != null) ...[
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
                                    fontWeight: FontWeight.w600,
                                    color: selected
                                        ? const Color(0xFF4C9EF8)
                                        : const Color(0xFF6D7075),
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
            if (_editingTables &&
                selected &&
                _layout.selectedTableIds.length == 1) ...[
              Positioned(
                left: 40 + table.width - 7,
                top: 40 + table.height - 7,
                child: GestureDetector(
                  key: const ValueKey('resize-table-handle'),
                  onPanStart: (details) {
                    _resizeDelta = Offset.zero;
                    _lastGlobalPosition = details.globalPosition;
                    _layout.beginResize(table.id);
                  },
                  onPanUpdate: (details) {
                    final delta = details.globalPosition - _lastGlobalPosition;
                    _lastGlobalPosition = details.globalPosition;
                    _resizeDelta += _objectDelta(delta, table.rotation);
                    _layout.resizeBy(_resizeDelta);
                  },
                  onPanEnd: (_) => _layout.endResize(),
                  child: const MouseRegion(
                    cursor: SystemMouseCursors.resizeDownRight,
                    child: _ResizeHandle(),
                  ),
                ),
              ),
              Positioned(
                left: 40 + table.width / 2 - 10,
                top: 2,
                child: _RotationHandle(
                  key: ValueKey('rotate-table-${table.id}'),
                  onStart: (position) {
                    _layout.beginObjectRotation();
                  },
                  onUpdate: (position) {
                    final pointer = _layoutPosition(position);
                    if (pointer != null) {
                      _layout.rotateTableToward(table.id, pointer);
                    }
                  },
                  onEnd: _layout.finishObjectRotation,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
