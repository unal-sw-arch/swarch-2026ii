part of '../admin_dashboard.dart';

enum _RevenuePeriod { minute, hour, day, month, year }

class _RevenueCard extends StatefulWidget {
  const _RevenueCard({
    required this.spanish,
    required this.token,
    required this.revision,
  });
  final bool spanish;
  final String token;
  final int revision;

  @override
  State<_RevenueCard> createState() => _RevenueCardState();
}

class _RevenueCardState extends State<_RevenueCard> {
  _RevenuePeriod _period = _RevenuePeriod.day;
  int _minutes = 30;
  int _days = 7;
  int _months = 6;
  int _years = 5;
  AdminOverviewMetrics? _metrics;
  int? _hoveredIndex;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(covariant _RevenueCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.revision != widget.revision ||
        oldWidget.token != widget.token) {
      _load();
    }
  }

  bool get spanish => widget.spanish;

  int get _range => switch (_period) {
    _RevenuePeriod.minute => _minutes,
    _RevenuePeriod.hour => 0,
    _RevenuePeriod.day => _days,
    _RevenuePeriod.month => _months,
    _RevenuePeriod.year => _years,
  };

  ({String title, List<String> labels, List<double> values})
  get _data => switch (_period) {
    _RevenuePeriod.minute => (
      title: spanish
          ? 'Ingresos: últimos $_minutes minutos'
          : 'Revenue: last $_minutes minutes',
      labels: _metrics?.points.map((point) => point.label).toList() ?? const [],
      values: _metrics?.points.map((point) => point.value).toList() ?? const [],
    ),
    _RevenuePeriod.hour => (
      title: spanish ? 'Ingresos: hoy por hora' : 'Revenue: today by hour',
      labels: _metrics?.points.map((point) => point.label).toList() ?? const [],
      values: _metrics?.points.map((point) => point.value).toList() ?? const [],
    ),
    _RevenuePeriod.day => (
      title: spanish
          ? 'Ingresos: últimos $_days días'
          : 'Revenue: $_days-day trend',
      labels: _metrics?.points.map((point) => point.label).toList() ?? const [],
      values: _metrics?.points.map((point) => point.value).toList() ?? const [],
    ),
    _RevenuePeriod.month => (
      title: spanish
          ? 'Ingresos: últimos $_months meses'
          : 'Revenue: $_months-month trend',
      labels: _metrics?.points.map((point) => point.label).toList() ?? const [],
      values: _metrics?.points.map((point) => point.value).toList() ?? const [],
    ),
    _RevenuePeriod.year => (
      title: spanish
          ? 'Ingresos: últimos $_years años'
          : 'Revenue: $_years-year trend',
      labels: _metrics?.points.map((point) => point.label).toList() ?? const [],
      values: _metrics?.points.map((point) => point.value).toList() ?? const [],
    ),
  };

  Future<void> _load() async {
    try {
      final metrics = await _loadOverview(
        widget.token,
        period: _period.name,
        range: _period == _RevenuePeriod.hour ? 24 : _range,
      );
      if (mounted) setState(() => _metrics = metrics);
    } on Object {
      if (mounted) setState(() => _metrics = null);
    }
  }

  List<int> _visibleIndices(int count) {
    if (count == 0) return const [];
    if (count == 1) return const [0];
    final visible = count.clamp(2, 7);
    return List.generate(
      visible,
      (index) => (index * (count - 1) / (visible - 1)).round(),
    );
  }

  Future<void> _changeRange() async {
    if (_period == _RevenuePeriod.hour) return;
    final (minimum, maximum) = switch (_period) {
      _RevenuePeriod.minute => (2, 30),
      _RevenuePeriod.day => (2, 31),
      _RevenuePeriod.month => (2, 24),
      _RevenuePeriod.year => (2, 10),
      _RevenuePeriod.hour => (0, 0),
    };
    var selected = _range;
    final result = await showDialog<int>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(spanish ? 'Rango del gráfico' : 'Chart range'),
          content: SizedBox(
            width: 360,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  _rangeLabel(selected),
                  key: const ValueKey('revenue-range-value'),
                  style: const TextStyle(
                    color: _ink,
                    fontSize: 24,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                Slider(
                  key: const ValueKey('revenue-range-slider'),
                  min: minimum.toDouble(),
                  max: maximum.toDouble(),
                  divisions: maximum - minimum,
                  value: selected.toDouble(),
                  label: '$selected',
                  onChanged: (value) =>
                      setDialogState(() => selected = value.round()),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(spanish ? 'Cancelar' : 'Cancel'),
            ),
            FilledButton(
              key: const ValueKey('apply-revenue-range'),
              onPressed: () => Navigator.pop(context, selected),
              child: Text(spanish ? 'Aplicar' : 'Apply'),
            ),
          ],
        ),
      ),
    );
    if (result == null || !mounted) return;
    setState(() {
      switch (_period) {
        case _RevenuePeriod.minute:
          _minutes = result;
          break;
        case _RevenuePeriod.day:
          _days = result;
          break;
        case _RevenuePeriod.month:
          _months = result;
          break;
        case _RevenuePeriod.year:
          _years = result;
          break;
        case _RevenuePeriod.hour:
          break;
      }
    });
    _load();
  }

  String _rangeLabel(int value) => switch (_period) {
    _RevenuePeriod.minute => spanish ? '$value minutos' : '$value minutes',
    _RevenuePeriod.day => spanish ? '$value días' : '$value days',
    _RevenuePeriod.month => spanish ? '$value meses' : '$value months',
    _RevenuePeriod.year => spanish ? '$value años' : '$value years',
    _RevenuePeriod.hour => '',
  };

  @override
  Widget build(BuildContext context) {
    final data = _data;
    return _Panel(
      child: SizedBox(
        height: 390,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    data.title,
                    style: const TextStyle(
                      color: _ink,
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Wrap(
                  spacing: 7,
                  children: [
                    if (_period != _RevenuePeriod.hour)
                      _RevenueControlButton(
                        key: const ValueKey('revenue-range-selector'),
                        label: _rangeLabel(_range),
                        icon: Icons.tune_rounded,
                        onTap: _changeRange,
                      ),
                    _RevenuePeriodSelector(
                      period: _period,
                      spanish: spanish,
                      onChanged: (value) {
                        setState(() {
                          _period = value;
                          _metrics = null;
                          _hoveredIndex = null;
                        });
                        _load();
                      },
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),
            Expanded(
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 260),
                switchInCurve: Curves.easeOutCubic,
                switchOutCurve: Curves.easeOutCubic,
                child: TweenAnimationBuilder<double>(
                  key: ValueKey('${_period.name}-$_range'),
                  tween: Tween(begin: 0, end: 1),
                  duration: const Duration(milliseconds: 420),
                  curve: Curves.easeOutCubic,
                  builder: (context, progress, child) => LayoutBuilder(
                    builder: (context, constraints) => MouseRegion(
                      onExit: (_) => setState(() => _hoveredIndex = null),
                      onHover: (event) {
                        if (data.values.isEmpty) return;
                        final width = (constraints.maxWidth - 54).clamp(
                          1.0,
                          double.infinity,
                        );
                        final x = (event.localPosition.dx - 52).clamp(
                          0.0,
                          width,
                        );
                        final index = data.values.length == 1
                            ? 0
                            : (x / width * (data.values.length - 1)).round();
                        if (index != _hoveredIndex) {
                          setState(() => _hoveredIndex = index);
                        }
                      },
                      child: Stack(
                        children: [
                          Positioned.fill(
                            child: CustomPaint(
                              painter: _RevenuePainter(
                                values: data.values,
                                progress: progress,
                                hoveredIndex: _hoveredIndex,
                              ),
                            ),
                          ),
                          if (_hoveredIndex case final index?)
                            Positioned(
                              left: 60,
                              top: 2,
                              child: DecoratedBox(
                                decoration: BoxDecoration(
                                  color: _ink,
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 9,
                                    vertical: 6,
                                  ),
                                  child: Text(
                                    '${data.labels[index]} · ${formatPesos(data.values[index])}',
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 11,
                                    ),
                                  ),
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
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: _visibleIndices(data.labels.length)
                  .map(
                    (index) => Text(
                      data.labels[index],
                      style: TextStyle(color: _muted, fontSize: 10),
                    ),
                  )
                  .toList(),
            ),
          ],
        ),
      ),
    );
  }
}

class _RevenueControlButton extends StatelessWidget {
  const _RevenueControlButton({
    super.key,
    required this.label,
    required this.icon,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: const Color(0xFFF0F3F6),
      borderRadius: BorderRadius.circular(8),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 7),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, color: _accent, size: 15),
              const SizedBox(width: 5),
              Text(
                label,
                style: const TextStyle(
                  color: _accent,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RevenuePeriodSelector extends StatelessWidget {
  const _RevenuePeriodSelector({
    required this.period,
    required this.spanish,
    required this.onChanged,
  });

  final _RevenuePeriod period;
  final bool spanish;
  final ValueChanged<_RevenuePeriod> onChanged;

  @override
  Widget build(BuildContext context) {
    final labels = spanish
        ? const ['Minuto', 'Hora', 'Día', 'Mes', 'Año']
        : const ['Minute', 'Hour', 'Day', 'Month', 'Year'];
    return PopupMenuButton<_RevenuePeriod>(
      key: const ValueKey('revenue-period-selector'),
      initialValue: period,
      tooltip: spanish ? 'Cambiar período' : 'Change period',
      onSelected: onChanged,
      itemBuilder: (context) => [
        for (var index = 0; index < _RevenuePeriod.values.length; index++)
          PopupMenuItem(
            value: _RevenuePeriod.values[index],
            child: Text(labels[index]),
          ),
      ],
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: const Color(0xFFF0F3F6),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                labels[period.index],
                style: const TextStyle(
                  color: _accent,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(width: 5),
              const Icon(Icons.expand_more_rounded, color: _accent, size: 16),
            ],
          ),
        ),
      ),
    );
  }
}

class _RevenuePainter extends CustomPainter {
  const _RevenuePainter({
    required this.values,
    required this.progress,
    required this.hoveredIndex,
  });

  final List<double> values;
  final double progress;
  final int? hoveredIndex;

  @override
  void paint(Canvas canvas, Size size) {
    if (values.isEmpty) return;
    const left = 52.0;
    final graphWidth = size.width - left;
    final maximum = values.fold<double>(
      0,
      (current, value) => value > current ? value : current,
    );
    final scaleMaximum = maximum <= 0 ? 1.0 : maximum;
    final grid = Paint()
      ..color = const Color(0xFFE5E7E8)
      ..strokeWidth = 1;
    for (var i = 1; i <= 3; i++) {
      final y = size.height * i / 4;
      canvas.drawLine(Offset(left, y), Offset(size.width, y), grid);
    }
    for (final fraction in const [0.0, .5, 1.0]) {
      final label = TextPainter(
        text: TextSpan(
          text: formatPesos(scaleMaximum * fraction),
          style: const TextStyle(color: _muted, fontSize: 9),
        ),
        textDirection: TextDirection.ltr,
      )..layout(maxWidth: left - 4);
      label.paint(
        canvas,
        Offset(0, size.height * (1 - fraction) - label.height / 2),
      );
    }
    final path = Path();
    for (var i = 0; i < values.length; i++) {
      final point = Offset(
        left +
            (values.length == 1
                ? graphWidth / 2
                : graphWidth * i / (values.length - 1)),
        size.height * (1 - (values[i] / scaleMaximum) * progress),
      );
      i == 0
          ? path.moveTo(point.dx, point.dy)
          : path.lineTo(point.dx, point.dy);
    }
    canvas.drawPath(
      path,
      Paint()
        ..color = _accent
        ..strokeWidth = 3
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
    if (hoveredIndex != null) {
      final index = hoveredIndex!.clamp(0, values.length - 1);
      final point = Offset(
        left +
            (values.length == 1
                ? graphWidth / 2
                : graphWidth * index / (values.length - 1)),
        size.height * (1 - (values[index] / scaleMaximum) * progress),
      );
      canvas.drawCircle(point, 5, Paint()..color = Colors.white);
      canvas.drawCircle(
        point,
        5,
        Paint()
          ..color = _accent
          ..strokeWidth = 2
          ..style = PaintingStyle.stroke,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _RevenuePainter oldDelegate) =>
      oldDelegate.progress != progress ||
      oldDelegate.values != values ||
      oldDelegate.hoveredIndex != hoveredIndex;
}
