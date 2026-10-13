part of '../hall_layout_page.dart';

enum _EditorMenuAction { copy, paste }

class _CopiedLayoutObject {
  const _CopiedLayoutObject.table(this.table) : wall = null;
  const _CopiedLayoutObject.wall(this.wall) : table = null;

  final RoomTableModel? table;
  final RoomWallModel? wall;
}

class _ToolButton extends StatelessWidget {
  const _ToolButton({
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    this.color,
  });
  final IconData icon;
  final String tooltip;
  final VoidCallback? onPressed;
  final Color? color;

  @override
  Widget build(BuildContext context) => IconButton(
    onPressed: onPressed,
    tooltip: tooltip,
    color: color,
    disabledColor: const Color(0xFFCDCFD0),
    icon: Icon(icon),
  );
}

class _ResizeHandle extends StatelessWidget {
  const _ResizeHandle();

  @override
  Widget build(BuildContext context) => const DecoratedBox(
    decoration: BoxDecoration(
      color: Colors.white,
      shape: BoxShape.circle,
      border: Border.fromBorderSide(
        BorderSide(color: Color(0xFF4C9EF8), width: 2),
      ),
    ),
    child: SizedBox.square(dimension: 14),
  );
}

class _RotationHandle extends StatelessWidget {
  const _RotationHandle({
    super.key,
    required this.onStart,
    required this.onUpdate,
    required this.onEnd,
  });

  final ValueChanged<Offset> onStart;
  final ValueChanged<Offset> onUpdate;
  final VoidCallback onEnd;

  @override
  Widget build(BuildContext context) => Listener(
    behavior: HitTestBehavior.opaque,
    onPointerDown: (event) => onStart(event.position),
    onPointerMove: (event) => onUpdate(event.position),
    onPointerUp: (_) => onEnd(),
    onPointerCancel: (_) => onEnd(),
    child: const MouseRegion(
      cursor: SystemMouseCursors.grab,
      child: SizedBox(
        width: 20,
        height: 38,
        child: Stack(
          alignment: Alignment.topCenter,
          children: [
            Positioned(
              top: 17,
              child: SizedBox(
                width: 2,
                height: 20,
                child: ColoredBox(color: Color(0xFF4C9EF8)),
              ),
            ),
            DecoratedBox(
              decoration: BoxDecoration(
                color: Colors.white,
                shape: BoxShape.circle,
                border: Border.fromBorderSide(
                  BorderSide(color: Color(0xFF4C9EF8), width: 2),
                ),
              ),
              child: SizedBox.square(
                dimension: 20,
                child: Icon(
                  Icons.rotate_right,
                  size: 13,
                  color: Color(0xFF4C9EF8),
                ),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _LiveBadge extends StatelessWidget {
  const _LiveBadge({required this.spanish});
  final bool spanish;

  @override
  Widget build(BuildContext context) => Material(
    key: const ValueKey('admin-live-view-badge'),
    color: const Color(0xEFFFFFFF),
    borderRadius: BorderRadius.circular(12),
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            Icons.visibility_outlined,
            size: 18,
            color: Color(0xFF71859B),
          ),
          const SizedBox(width: 8),
          Text(spanish ? 'Vista en vivo' : 'Live View'),
        ],
      ),
    ),
  );
}
