// ignore_for_file: invalid_use_of_protected_member

part of '../hall_layout_page.dart';

extension _HallPointerGestures on _HallLayoutPageState {
  void _desktopTablePointerDown(PointerDownEvent event, int tableId) {
    if ((event.buttons & kPrimaryMouseButton) == 0) return;
    if (_activeDesktopPointer != null) return;
    _activeDesktopPointer = event.pointer;
    _activeDesktopTable = tableId;
    _desktopTableMoved = false;
    _dragDelta = Offset.zero;
    _lastGlobalPosition = event.position;
    _layout.pressTable();
  }

  void _desktopTablePointerMove(PointerMoveEvent event) {
    if (_activeDesktopPointer != event.pointer || _activeDesktopTable == null) {
      return;
    }
    final delta = event.position - _lastGlobalPosition;
    _lastGlobalPosition = event.position;
    if (!_desktopTableMoved && delta.distanceSquared > 0) {
      _desktopTableMoved = true;
      _layout.beginMove(_activeDesktopTable!);
    }
    if (_desktopTableMoved) {
      _dragDelta += _logicalDelta(delta);
      _layout.moveBy(_dragDelta);
    }
  }

  void _desktopTablePointerUp(PointerUpEvent event) {
    if (_activeDesktopPointer != event.pointer || _activeDesktopTable == null) {
      return;
    }
    if (_desktopTableMoved) {
      _layout.endMove();
    } else {
      _layout.cancelTablePress();
      _layout.toggleSelection(_activeDesktopTable!);
    }
    _clearDesktopPointer();
  }

  void _desktopTablePointerCancel(PointerCancelEvent event) {
    if (_activeDesktopPointer != event.pointer) return;
    if (_desktopTableMoved) _layout.endMove();
    _layout.cancelTablePress();
    _clearDesktopPointer();
  }

  void _clearDesktopPointer() {
    _activeDesktopPointer = null;
    _activeDesktopTable = null;
    _desktopTableMoved = false;
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

  Offset _objectDelta(Offset screenDelta, double objectRotation) {
    final logical = _logicalDelta(screenDelta);
    return Offset(
      logical.dx * math.cos(objectRotation) +
          logical.dy * math.sin(objectRotation),
      -logical.dx * math.sin(objectRotation) +
          logical.dy * math.cos(objectRotation),
    );
  }

  Offset? _layoutPosition(Offset globalPosition) {
    final renderObject = _layoutSpaceKey.currentContext?.findRenderObject();
    if (renderObject is! RenderBox || !renderObject.hasSize) return null;
    return renderObject.globalToLocal(globalPosition) -
        _HallLayoutPageState._canvasOrigin;
  }

  Offset _visibleLayoutCenter() {
    final viewport = _viewportKey.currentContext?.findRenderObject();
    if (viewport is! RenderBox || !viewport.hasSize) {
      return _layout.contentBounds.center;
    }
    final globalCenter = viewport.localToGlobal(
      viewport.size.center(Offset.zero),
    );
    return _layoutPosition(globalCenter) ?? _layout.contentBounds.center;
  }

  void _focusLayout() {
    final viewport = _viewportKey.currentContext?.findRenderObject();
    if (viewport is! RenderBox || !viewport.hasSize) return;
    final bounds = _layout.contentBounds.inflate(100);
    final availableWidth = math.max(1.0, viewport.size.width - 80);
    final availableHeight = math.max(1.0, viewport.size.height - 80);
    final scale = math
        .min(availableWidth / bounds.width, availableHeight / bounds.height)
        .clamp(.35, 1.35)
        .toDouble();
    final renderedCenter = bounds.center + _HallLayoutPageState._canvasOrigin;
    final translation = Offset(
      viewport.size.width / 2 - renderedCenter.dx * scale,
      viewport.size.height / 2 - renderedCenter.dy * scale,
    );
    if (_rotation != 0) setState(() => _rotation = 0);
    _camera.value = Matrix4.identity()
      ..translateByDouble(translation.dx, translation.dy, 0, 1)
      ..scaleByDouble(scale, scale, scale, 1);
  }
}
