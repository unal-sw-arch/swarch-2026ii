// ignore_for_file: invalid_use_of_protected_member, invalid_use_of_visible_for_testing_member

part of '../room_layout_controller.dart';

extension RoomLayoutWallEditing on RoomLayoutController {
  void addWall({Offset? center}) {
    const width = 210.0;
    const height = 10.0;
    final targetCenter = center ?? const Offset(465, 345);
    final position = _freeWallPosition(
      targetCenter,
      width: width,
      height: height,
    );
    final wall = RoomWallModel(
      id: _nextTemporaryId--,
      x: position.dx,
      y: position.dy,
      width: width,
      height: height,
      rotation: 0,
    );
    walls = [...walls, wall];
    selectedWallId = wall.id;
    selectedTableIds = {};
    interaction = LayoutInteraction.normal;
    dirty = true;
    notifyListeners();
  }

  void selectWall(int id) {
    selectedWallId = id;
    selectedTableIds = {};
    interaction = LayoutInteraction.normal;
    notifyListeners();
  }

  void beginWallMove(int id) {
    selectWall(id);
    _wallMoveOrigin = walls.firstWhere((wall) => wall.id == id);
    interaction = LayoutInteraction.editingWalls;
    notifyListeners();
  }

  void moveWall(int id, Offset delta) {
    final original = _wallMoveOrigin;
    if (original == null || original.id != id) return;
    final proposed = original.copyWith(
      x: original.x + delta.dx,
      y: original.y + delta.dy,
    );
    final correction = _wallSnapCorrection(proposed, id);
    walls = [
      for (final wall in walls)
        if (wall.id == id)
          proposed.copyWith(
            x: proposed.x + correction.dx,
            y: proposed.y + correction.dy,
          )
        else
          wall,
    ];
    interaction = correction == Offset.zero
        ? LayoutInteraction.editingWalls
        : LayoutInteraction.magnetPreview;
    notifyListeners();
  }

  void beginWallResize(int id) {
    final wall = walls.firstWhere((wall) => wall.id == id);
    selectedWallId = id;
    selectedTableIds = {};
    _wallResizeOrigin = wall;
    interaction = LayoutInteraction.resizingWall;
    notifyListeners();
  }

  void resizeWallBy(Offset delta) {
    final original = _wallResizeOrigin;
    if (original == null) return;
    final width = math.max(4.0, original.width + delta.dx);
    final height = math.max(4.0, original.height + delta.dy);
    final position = _resizedPosition(
      x: original.x,
      y: original.y,
      oldWidth: original.width,
      oldHeight: original.height,
      newWidth: width,
      newHeight: height,
      rotation: original.rotation,
    );
    walls = [
      for (final wall in walls)
        if (wall.id == original.id)
          wall.copyWith(
            x: position.dx,
            y: position.dy,
            width: width,
            height: height,
          )
        else
          wall,
    ];
    notifyListeners();
  }

  void endWallResize() {
    if (_wallResizeOrigin != null) dirty = true;
    _wallResizeOrigin = null;
    interaction = LayoutInteraction.normal;
    notifyListeners();
  }

  void cancelWallResize() {
    final original = _wallResizeOrigin;
    if (original != null) {
      walls = [
        for (final wall in walls)
          if (wall.id == original.id) original else wall,
      ];
    }
    _wallResizeOrigin = null;
    interaction = LayoutInteraction.normal;
    notifyListeners();
  }

  void rotateWall(int id, double delta) {
    final wall = walls.firstWhere((wall) => wall.id == id);
    setWallRotation(id, wall.rotation + delta);
  }

  void setWallRotation(int id, double angle) {
    walls = [
      for (final wall in walls)
        if (wall.id == id)
          RoomWallModel(
            id: wall.id,
            x: wall.x,
            y: wall.y,
            width: wall.width,
            height: wall.height,
            rotation: _snapRotation(angle),
          )
        else
          wall,
    ];
    dirty = true;
    notifyListeners();
  }

  void rotateWallToward(int id, Offset pointer) {
    final wall = walls.firstWhere((wall) => wall.id == id);
    final center = Offset(wall.x + wall.width / 2, wall.y + wall.height / 2);
    final angle = _rotationTowardPointer(center, pointer);
    if (angle != null) setWallRotation(id, angle);
  }

  void finishWallEditing() {
    if (_wallMoveOrigin != null) dirty = true;
    _wallMoveOrigin = null;
    interaction = LayoutInteraction.normal;
    notifyListeners();
  }

  void cancelWallMove() {
    final original = _wallMoveOrigin;
    if (original != null) {
      walls = [
        for (final wall in walls)
          if (wall.id == original.id) original else wall,
      ];
    }
    _wallMoveOrigin = null;
    interaction = LayoutInteraction.normal;
    notifyListeners();
  }
}
