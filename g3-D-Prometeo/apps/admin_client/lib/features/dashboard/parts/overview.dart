part of '../admin_dashboard.dart';

class _DashboardContent extends StatefulWidget {
  const _DashboardContent({
    required this.spanish,
    required this.token,
    required this.dashboardRevision,
    required this.activities,
  });

  final bool spanish;
  final String token;
  final int dashboardRevision;
  final ValueListenable<List<AdminActivity>> activities;

  @override
  State<_DashboardContent> createState() => _DashboardContentState();
}

class _DashboardContentState extends State<_DashboardContent> {
  late Future<AdminOverviewMetrics> _summary;
  int _revision = 0;

  bool get spanish => widget.spanish;
  String get token => widget.token;

  @override
  void initState() {
    super.initState();
    _summary = _loadOverview(token);
  }

  @override
  void didUpdateWidget(covariant _DashboardContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.token != token ||
        oldWidget.dashboardRevision != widget.dashboardRevision) {
      _summary = _loadOverview(token);
      _revision++;
    }
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(24, 28, 24, 40),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1180),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final wide = constraints.maxWidth >= 760;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    spanish ? 'RESUMEN' : 'OVERVIEW',
                    style: const TextStyle(
                      color: _accent,
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      letterSpacing: .8,
                    ),
                  ),
                  const SizedBox(height: 7),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Expanded(
                        child: Text(
                          spanish
                              ? 'Rendimiento de hoy'
                              : "Today's Performance",
                          style: TextStyle(
                            color: _ink,
                            fontSize: wide ? 29 : 25,
                            height: 1.1,
                            fontWeight: FontWeight.w700,
                            letterSpacing: -.8,
                          ),
                        ),
                      ),
                      if (wide)
                        Text(
                          _dateLabel(),
                          style: const TextStyle(color: _muted, fontSize: 12),
                        ),
                    ],
                  ),
                  const SizedBox(height: 26),
                  FutureBuilder<AdminOverviewMetrics>(
                    future: _summary,
                    builder: (context, snapshot) => _MetricsGrid(
                      spanish: spanish,
                      wide: wide,
                      metrics: snapshot.data,
                    ),
                  ),
                  const SizedBox(height: 22),
                  if (wide)
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: _RevenueCard(
                            spanish: spanish,
                            token: token,
                            revision: _revision,
                          ),
                        ),
                        const SizedBox(width: 16),
                        SizedBox(
                          width: 296,
                          child: Column(
                            children: [
                              _TopItemCard(spanish: spanish, metrics: _summary),
                              const SizedBox(height: 16),
                              _CategoryCard(
                                spanish: spanish,
                                metrics: _summary,
                              ),
                            ],
                          ),
                        ),
                      ],
                    )
                  else ...[
                    _RevenueCard(
                      spanish: spanish,
                      token: token,
                      revision: _revision,
                    ),
                    const SizedBox(height: 16),
                    _TopItemCard(spanish: spanish, metrics: _summary),
                    const SizedBox(height: 16),
                    _CategoryCard(spanish: spanish, metrics: _summary),
                  ],
                  const SizedBox(height: 22),
                  _RecentActivityCard(
                    spanish: spanish,
                    activities: widget.activities,
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  String _dateLabel() {
    final now = DateTime.now();
    const monthsEn = [
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec',
    ];
    const monthsEs = [
      'ene',
      'feb',
      'mar',
      'abr',
      'may',
      'jun',
      'jul',
      'ago',
      'sep',
      'oct',
      'nov',
      'dic',
    ];
    final months = spanish ? monthsEs : monthsEn;
    return '${months[now.month - 1]} ${now.day}, ${now.year}';
  }
}

class _MetricsGrid extends StatelessWidget {
  const _MetricsGrid({
    required this.spanish,
    required this.wide,
    required this.metrics,
  });
  final bool spanish;
  final bool wide;
  final AdminOverviewMetrics? metrics;

  @override
  Widget build(BuildContext context) {
    final cards = [
      _MetricData(
        spanish ? 'Ventas de hoy' : 'Sales today',
        formatPesos(metrics?.salesToday ?? 0),
        Icons.payments_outlined,
        spanish ? 'Total registrado hoy' : 'Recorded today',
      ),
      _MetricData(
        spanish ? 'Pedidos totales' : 'Total orders',
        '${metrics?.ordersToday ?? 0}',
        Icons.receipt_long_outlined,
        spanish ? 'Órdenes creadas hoy' : 'Orders created today',
      ),
      _MetricData(
        spanish ? 'Ticket promedio' : 'Average ticket',
        formatPesos(metrics?.averageTicket ?? 0),
        Icons.local_activity_outlined,
        spanish ? 'Promedio real de hoy' : 'Today’s actual average',
      ),
    ];
    if (wide) {
      return Row(
        children: [
          for (var index = 0; index < cards.length; index++) ...[
            Expanded(child: _MetricCard(data: cards[index])),
            if (index != cards.length - 1) const SizedBox(width: 12),
          ],
        ],
      );
    }
    return Column(
      children: [
        for (final card in cards) ...[
          _MetricCard(data: card),
          if (card != cards.last) const SizedBox(height: 12),
        ],
      ],
    );
  }
}

class _MetricData {
  const _MetricData(this.label, this.value, this.icon, this.detail);
  final String label;
  final String value;
  final IconData icon;
  final String detail;
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({required this.data});
  final _MetricData data;

  @override
  Widget build(BuildContext context) {
    return _Panel(
      child: SizedBox(
        height: 92,
        child: Stack(
          children: [
            Positioned(
              right: 0,
              child: Icon(data.icon, color: const Color(0xFFD8DBDF), size: 29),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  data.label,
                  style: const TextStyle(
                    color: _muted,
                    fontSize: 12,
                    letterSpacing: .4,
                  ),
                ),
                const Spacer(),
                Text(
                  data.value,
                  style: const TextStyle(
                    color: _ink,
                    fontSize: 27,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -.8,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  data.detail,
                  style: const TextStyle(
                    color: Color(0xFF626760),
                    fontSize: 10,
                    letterSpacing: .4,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
