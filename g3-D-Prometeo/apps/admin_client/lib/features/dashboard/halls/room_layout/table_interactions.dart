// ignore_for_file: invalid_use_of_protected_member, invalid_use_of_visible_for_testing_member

part of '../room_layout_controller.dart';

extension RoomLayoutTableInteractions on RoomLayoutController {
  void toggleSelection(int id) {
    selectedWallId = null;
    if (selectedTableIds.contains(id)) {
      selectedTableIds = {...selectedTableIds}..remove(id);
    } else {
      selectedTableIds = {...selectedTableIds, id};
    }
    interaction = LayoutInteraction.normal;
    notifyListeners();
  }

  void selectTable(int id) {
    selectedTableIds = {id};
    selectedWallId = null;
    interaction = LayoutInteraction.normal;
    invalidPlacement = false;
    notifyListeners();
  }

  void clearSelection() {
    selectedTableIds = {};
    selectedWallId = null;
    interaction = LayoutInteraction.normal;
    invalidPlacement = false;
    notifyListeners();
  }

  void beginMove(int id) {
    selectedWallId = null;
    final group = groupForTable(id);
    selectedTableIds = group == null ? {id} : group.tableIds.toSet();
    _interactionOrigin = {
      for (final table in tables)
        if (selectedTableIds.contains(table.id)) table.id: table,
    };
    interaction = LayoutInteraction.tablePressed;
    invalidPlacement = false;
    notifyListeners();
  }

  void pressTable() {
    interaction = LayoutInteraction.tablePressed;
    notifyListeners();
  }

  void cancelTablePress() {
    if (interaction == LayoutInteraction.tablePressed) {
      interaction = LayoutInteraction.normal;
      notifyListeners();
    }
  }

  void moveBy(Offset delta) {
    if (_interactionOrigin.isEmpty) return;
    var adjusted = delta;
    final movingIds = _interactionOrigin.keys.toSet();
    final proposed = _interactionOrigin.values
        .map(
          (table) =>
              table.copyWith(x: table.x + delta.dx, y: table.y + delta.dy),
        )
        .toList();
    final correction = _snapCorrection(proposed, movingIds);
    adjusted += correction;
    final moved = _interactionOrigin.values
        .map(
          (table) => table.copyWith(
            x: table.x + adjusted.dx,
            y: table.y + adjusted.dy,
          ),
        )
        .toList();
    _replaceMoving(moved);
    invalidPlacement = _hasCollision(moved, movingIds);
    interaction = correction == Offset.zero
        ? LayoutInteraction.draggingTable
        : LayoutInteraction.magnetPreview;
    notifyListeners();
  }

  void endMove() {
    if (invalidPlacement) {
      _replaceMoving(_interactionOrigin.values.toList());
    } else {
      dirty = true;
    }
    _interactionOrigin = {};
    invalidPlacement = false;
    interaction = LayoutInteraction.normal;
    notifyListeners();
  }

  void beginResize(int id) {
    final table = tables.firstWhere((table) => table.id == id);
    selectedTableIds = {id};
    selectedWallId = null;
    _interactionOrigin = {id: table};
    interaction = LayoutInteraction.resizingTable;
    invalidPlacement = false;
    notifyListeners();
  }

  void resizeBy(Offset delta) {
    if (_interactionOrigin.length != 1) return;
    final original = _interactionOrigin.values.single;
    final width = math.max(
      RoomLayoutController.minimumTableSize,
      original.width + delta.dx,
    );
    final height = math.max(
      RoomLayoutController.minimumTableSize,
      original.height + delta.dy,
    );
    final position = _resizedPosition(
      x: original.x,
      y: original.y,
      oldWidth: original.width,
      oldHeight: original.height,
      newWidth: width,
      newHeight: height,
      rotation: original.rotation,
    );
    final resized = original.copyWith(
      x: position.dx,
      y: position.dy,
      width: width,
      height: height,
    );
    _replaceMoving([resized]);
    invalidPlacement = _hasCollision([resized], {resized.id});
    notifyListeners();
  }

  void endResize() => endMove();

  void rotateTable(int id, double delta) {
    final table = tables.firstWhere((table) => table.id == id);
    setTableRotation(id, table.rotation + delta);
  }

  void setTableRotation(int id, double angle) {
    final table = tables.firstWhere((table) => table.id == id);
    final rotated = table.copyWith(rotation: _snapRotation(angle));
    if (_hasCollision([rotated], {id})) {
      invalidPlacement = true;
      notifyListeners();
      return;
    }
    invalidPlacement = false;
    _replaceMoving([rotated]);
    dirty = true;
    notifyListeners();
  }

  void rotateTableToward(int id, Offset pointer) {
    final table = tables.firstWhere((table) => table.id == id);
    final center = Offset(
      table.x + table.width / 2,
      table.y + table.height / 2,
    );
    final angle = _rotationTowardPointer(center, pointer);
    if (angle != null) setTableRotation(id, angle);
  }

  void beginObjectRotation() {
    interaction = LayoutInteraction.rotatingObject;
    invalidPlacement = false;
    notifyListeners();
  }

  void finishObjectRotation() {
    interaction = LayoutInteraction.normal;
    invalidPlacement = false;
    notifyListeners();
  }
}
