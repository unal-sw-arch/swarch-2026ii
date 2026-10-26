part of '../admin_dashboard.dart';

class _RecentActivityCard extends StatefulWidget {
  const _RecentActivityCard({required this.spanish, required this.activities});

  final bool spanish;
  final ValueListenable<List<AdminActivity>> activities;

  @override
  State<_RecentActivityCard> createState() => _RecentActivityCardState();
}

class _RecentActivityCardState extends State<_RecentActivityCard> {
  final _cardKey = GlobalKey();
  bool _isExpanded = false;

  Future<void> _expand() async {
    final renderBox = _cardKey.currentContext?.findRenderObject() as RenderBox?;
    if (renderBox == null || !renderBox.hasSize) return;

    final sourceRect = renderBox.localToGlobal(Offset.zero) & renderBox.size;
    final route = PageRouteBuilder<void>(
      opaque: false,
      barrierDismissible: true,
      barrierColor: const Color(0xA6000000),
      barrierLabel: widget.spanish ? 'Actividad reciente' : 'Recent activity',
      transitionDuration: const Duration(milliseconds: 460),
      reverseTransitionDuration: const Duration(milliseconds: 380),
      pageBuilder: (context, animation, secondaryAnimation) =>
          _ExpandedActivitySurface(
            spanish: widget.spanish,
            activities: widget.activities,
          ),
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        return _ActivityExpansionTransition(
          animation: animation,
          sourceRect: sourceRect,
          child: child,
        );
      },
    );
    final navigation = Navigator.of(context).push<void>(route);
    // Navigator.push instala primero la copia animada sobre la tarjeta. Ocultar
    // el origen después evita el frame vacío que aparecía antes de la ruta.
    setState(() => _isExpanded = true);
    final routeAnimation = route.animation;

    void restoreCard(AnimationStatus status) {
      // Este callback ocurre durante el tick del último frame de la ruta. La
      // tarjeta original se reconstruye en ese mismo frame, debajo de la copia
      // animada, evitando un frame vacío entre ambas.
      if (status == AnimationStatus.dismissed && mounted && _isExpanded) {
        setState(() => _isExpanded = false);
      }
    }

    routeAnimation?.addStatusListener(restoreCard);
    await navigation;
    await route.completed;
    routeAnimation?.removeStatusListener(restoreCard);
    if (mounted && _isExpanded) setState(() => _isExpanded = false);
  }

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      key: _cardKey,
      child: IgnorePointer(
        ignoring: _isExpanded,
        child: Opacity(
          opacity: _isExpanded ? 0 : 1,
          child: _Panel(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        widget.spanish
                            ? 'Actividad reciente'
                            : 'Recent activity',
                        style: const TextStyle(
                          color: _ink,
                          fontSize: 17,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    IconButton(
                      key: const ValueKey('expand-recent-activity'),
                      tooltip: widget.spanish ? 'Expandir' : 'Expand',
                      onPressed: _expand,
                      icon: const Icon(Icons.open_in_full_rounded, size: 19),
                      color: _accent,
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                ValueListenableBuilder<List<AdminActivity>>(
                  valueListenable: widget.activities,
                  builder: (context, activities, _) => _ActivityTable(
                    spanish: widget.spanish,
                    compact: true,
                    activities: activities,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ActivityExpansionTransition extends StatelessWidget {
  const _ActivityExpansionTransition({
    required this.animation,
    required this.sourceRect,
    required this.child,
  });

  final Animation<double> animation;
  final Rect sourceRect;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final available = Size(constraints.maxWidth, constraints.maxHeight);
        final horizontalMargin = available.width < 600 ? 14.0 : 28.0;
        final verticalMargin = available.height < 700 ? 14.0 : 28.0;
        final targetWidth = (available.width - horizontalMargin * 2).clamp(
          0.0,
          920.0,
        );
        final targetHeight = (available.height - verticalMargin * 2).clamp(
          0.0,
          680.0,
        );
        final targetRect = Rect.fromLTWH(
          (available.width - targetWidth) / 2,
          (available.height - targetHeight) / 2,
          targetWidth,
          targetHeight,
        );

        return AnimatedBuilder(
          animation: animation,
          child: child,
          builder: (context, child) {
            final curve = animation.status == AnimationStatus.reverse
                ? Curves.easeInOutCubic
                : Curves.easeInOutCubicEmphasized;
            final progress = curve.transform(animation.value);
            final rect = Rect.lerp(sourceRect, targetRect, progress)!;
            return Stack(
              children: [
                Positioned.fromRect(
                  rect: rect,
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(13 + (5 * progress)),
                    child: child,
                  ),
                ),
              ],
            );
          },
        );
      },
    );
  }
}

class _ExpandedActivitySurface extends StatelessWidget {
  const _ExpandedActivitySurface({
    required this.spanish,
    required this.activities,
  });

  final bool spanish;
  final ValueListenable<List<AdminActivity>> activities;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: _surface,
      elevation: 24,
      shadowColor: Colors.black54,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    spanish ? 'Actividad reciente' : 'Recent activity',
                    style: const TextStyle(
                      color: _ink,
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                IconButton(
                  key: const ValueKey('close-recent-activity'),
                  tooltip: spanish ? 'Cerrar' : 'Close',
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close_rounded),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Expanded(
              child: ValueListenableBuilder<List<AdminActivity>>(
                valueListenable: activities,
                builder: (context, values, _) => SingleChildScrollView(
                  child: _ActivityTable(
                    spanish: spanish,
                    compact: false,
                    activities: values,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ActivityTable extends StatelessWidget {
  const _ActivityTable({
    required this.spanish,
    required this.compact,
    required this.activities,
  });

  final bool spanish;
  final bool compact;
  final List<AdminActivity> activities;

  @override
  Widget build(BuildContext context) {
    final rows = compact ? activities.take(2) : activities;

    return Container(
      decoration: BoxDecoration(
        border: Border.all(color: const Color(0xFFE4E6E8)),
        borderRadius: BorderRadius.circular(10),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          _ActivityRow(
            author: spanish ? 'Autor' : 'Author',
            type: spanish ? 'Tipo' : 'Type',
            modification: spanish ? 'Modificación' : 'Modification',
            header: true,
          ),
          if (activities.isEmpty)
            _ActivityRow(
              author: '—',
              type: '—',
              modification: spanish
                  ? 'Aún no hay actividad registrada.'
                  : 'No activity has been recorded yet.',
            ),
          for (final row in rows)
            _ActivityRow(
              author: row.author,
              type: row.type,
              modification: row.modification,
            ),
        ],
      ),
    );
  }
}

class _ActivityRow extends StatelessWidget {
  const _ActivityRow({
    required this.author,
    required this.type,
    required this.modification,
    this.header = false,
  });

  final String author;
  final String type;
  final String modification;
  final bool header;

  @override
  Widget build(BuildContext context) {
    final style = TextStyle(
      color: header ? _muted : _ink,
      fontSize: header ? 11 : 12,
      fontWeight: header ? FontWeight.w700 : FontWeight.w400,
      letterSpacing: header ? .35 : 0,
    );
    return Container(
      color: header ? const Color(0xFFF5F6F6) : _surface,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
      child: Row(
        children: [
          Expanded(flex: 2, child: Text(author, style: style)),
          const SizedBox(width: 12),
          Expanded(flex: 2, child: Text(type, style: style)),
          const SizedBox(width: 12),
          Expanded(flex: 5, child: Text(modification, style: style)),
        ],
      ),
    );
  }
}
