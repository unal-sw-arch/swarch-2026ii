// ignore_for_file: invalid_use_of_protected_member, invalid_use_of_visible_for_testing_member

part of '../room_layout_controller.dart';

extension RoomLayoutTableManagement on RoomLayoutController {
  void createTable(String identifier, {Offset? center}) {
    interaction = LayoutInteraction.creatingTable;
    const width = 130.0;
    const height = 82.0;
    final targetCenter = center ?? const Offset(165, 131);
    final position = _freeTablePosition(
      targetCenter,
      width: width,
      height: height,
    );
    final table = RoomTableModel(
      id: _nextTemporaryId--,
      identifier: identifier.trim(),
      x: position.dx,
      y: position.dy,
      width: width,
      height: height,
    );
    tables = [...tables, table];
    selectedTableIds = {table.id};
    selectedWallId = null;
    interaction = LayoutInteraction.normal;
    dirty = true;
    notifyListeners();
  }

  void deleteSelected() {
    final deleted = selectedTableIds;
    tables = tables.where((table) => !deleted.contains(table.id)).toList();
    groups = groups
        .map(
          (group) => RoomTableGroupModel(
            id: group.id,
            identifier: group.identifier,
            tableIds: group.tableIds
                .where((id) => !deleted.contains(id))
                .toList(),
          ),
        )
        .where((group) => group.tableIds.length >= 2)
        .toList();
    selectedTableIds = {};
    if (selectedWallId != null) {
      walls = walls.where((wall) => wall.id != selectedWallId).toList();
      selectedWallId = null;
    }
    dirty = true;
    notifyListeners();
  }

  void groupSelected({String? identifier}) {
    if (selectedTableIds.length < 2) return;
    selectedWallId = null;
    final selected = tables
        .where((table) => selectedTableIds.contains(table.id))
        .toList();
    groups = groups
        .where(
          (group) =>
              group.tableIds.every((id) => !selectedTableIds.contains(id)),
        )
        .toList();
    final requested = identifier?.trim() ?? '';
    groups = [
      ...groups,
      RoomTableGroupModel(
        id: _nextTemporaryId--,
        identifier: requested.isNotEmpty
            ? requested
            : selected.map((table) => table.identifier).join(' + '),
        tableIds: selectedTableIds.toList(),
      ),
    ];
    dirty = true;
    notifyListeners();
  }

  void ungroupSelected() {
    groups = groups
        .where(
          (group) =>
              group.tableIds.every((id) => !selectedTableIds.contains(id)),
        )
        .toList();
    dirty = true;
    notifyListeners();
  }

  RoomTableGroupModel? groupForTable(int tableId) {
    for (final group in groups) {
      if (group.tableIds.contains(tableId)) return group;
    }
    return null;
  }
}
