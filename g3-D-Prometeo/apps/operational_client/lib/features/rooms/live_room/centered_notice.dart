part of '../live_room_page.dart';

class _CenteredNotice extends StatefulWidget {
  const _CenteredNotice({required this.message, required this.warning});

  final String message;
  final bool warning;

  @override
  State<_CenteredNotice> createState() => _CenteredNoticeState();
}

class _CenteredNoticeState extends State<_CenteredNotice> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer(const Duration(milliseconds: 1500), _close);
  }

  void _close() {
    if (mounted && Navigator.of(context).canPop()) {
      Navigator.of(context).pop();
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => GestureDetector(
    behavior: HitTestBehavior.opaque,
    onTap: _close,
    child: Material(
      color: Colors.transparent,
      child: Center(
        child: IgnorePointer(
          child: Container(
            constraints: const BoxConstraints(maxWidth: 420),
            margin: const EdgeInsets.symmetric(horizontal: 28),
            padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 18),
            decoration: BoxDecoration(
              color: const Color(0xFFFDFDFC),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: widget.warning
                    ? const Color(0xFFE5C077)
                    : const Color(0xFFE1A09D),
              ),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x29000000),
                  blurRadius: 24,
                  offset: Offset(0, 8),
                ),
              ],
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  widget.warning
                      ? Icons.info_outline_rounded
                      : Icons.error_outline_rounded,
                  color: widget.warning
                      ? const Color(0xFFB68232)
                      : const Color(0xFFB64A4A),
                ),
                const SizedBox(width: 12),
                Flexible(
                  child: Text(
                    widget.message,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      height: 1.3,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
