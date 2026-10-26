// ignore_for_file: invalid_use_of_protected_member, invalid_use_of_visible_for_testing_member

part of '../room_layout_controller.dart';

extension RoomLayoutClipboardAndQueries on RoomLayoutController {
  void pasteTable(RoomTableModel source, Offset center) {
    final identifier = _copiedIdentifier(source.identifier);
    final position = _freeTablePosition(
      center,
      width: source.width,
      height: source.height,
      rotation: source.rotation,
    );
    final table = source.copyWith(
      id: _nextTemporaryId--,
      identifier: identifier,
      x: position.dx,
      y: position.dy,
    );
    tables = [...tables, table];
    selectedTableIds = {table.id};
    selectedWallId = null;
    interaction = LayoutInteraction.normal;
    dirty = true;
    notifyListeners();
  }

  void pasteWall(RoomWallModel source, Offset center) {
    final wall = RoomWallModel(
      id: _nextTemporaryId--,
      x: center.dx - source.width / 2,
      y: center.dy - source.height / 2,
      width: source.width,
      height: source.height,
      rotation: source.rotation,
    );
    walls = [...walls, wall];
    selectedWallId = wall.id;
    selectedTableIds = {};
    interaction = LayoutInteraction.normal;
    dirty = true;
    notifyListeners();
  }

  RoomTableModel? get selectedTable => selectedTableIds.length == 1
      ? tables.firstWhere((table) => table.id == selectedTableIds.single)
      : null;

  RoomWallModel? get selectedWall => selectedWallId == null
      ? null
      : walls.firstWhere((wall) => wall.id == selectedWallId);

  Rect get contentBounds {
    Rect? result;
    for (final table in tables) {
      result = result?.expandToInclude(_bounds(table)) ?? _bounds(table);
    }
    for (final wall in walls) {
      result = result?.expandToInclude(_wallBounds(wall)) ?? _wallBounds(wall);
    }
    return result ?? const Rect.fromLTWH(-200, -150, 400, 300);
  }
}
