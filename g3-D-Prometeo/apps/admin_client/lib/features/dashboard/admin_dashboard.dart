import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../auth/login_page.dart';
import 'employees/employees_page.dart';
import 'halls/halls_page.dart';
import 'history/order_history_page.dart';
import 'menus/menus_page.dart';
import 'ingredients/ingredients_page.dart';
import '../../services/admin_api.dart';
import '../../services/admin_realtime.dart';
import '../../utils/money.dart';

part 'parts/navigation.dart';
part 'parts/overview.dart';
part 'parts/revenue_chart.dart';
part 'parts/summary_cards.dart';
part 'parts/recent_activity.dart';
part 'parts/panel.dart';

const _ink = Color(0xFF222326);
const _muted = Color(0xFF7A7D82);
const _accent = Color(0xFF8798AC);
const _surface = Colors.white;

const _emptyOverview = AdminOverviewMetrics(
  salesToday: 0,
  ordersToday: 0,
  averageTicket: 0,
  points: [],
  topProduct: null,
  categories: [],
);

Future<AdminOverviewMetrics> _loadOverview(
  String token, {
  String period = 'day',
  int range = 7,
}) => token.isEmpty
    ? Future.value(_emptyOverview)
    : getAdminOverview(token, period: period, range: range);

enum _AdminSection { overview, menus, ingredients, halls, employees, history }

typedef LoadAdminActivities =
    Future<List<AdminActivity>> Function(String token);

class AdminDashboard extends StatefulWidget {
  const AdminDashboard({
    super.key,
    required this.strings,
    required this.onLogout,
    this.adminName = 'Admin User',
    this.sessionToken = '',
    this.loadActivities = getAdminActivities,
  });

  final AppStrings strings;
  final VoidCallback onLogout;
  final String adminName;
  final String sessionToken;
  final LoadAdminActivities loadActivities;

  @override
  State<AdminDashboard> createState() => _AdminDashboardState();
}

class _AdminDashboardState extends State<AdminDashboard> {
  _AdminSection _section = _AdminSection.overview;
  late final AdminRealtimeService _realtime;
  StreamSubscription<AdminRealtimeMessage>? _realtimeSubscription;
  int _sectionRevision = 0;
  int _dashboardRevision = 0;
  int _activityPatchRevision = 0;
  final _activities = ValueNotifier<List<AdminActivity>>([]);
  Future<void>? _activityReload;

  bool get _es => widget.strings.isSpanish;

  @override
  void initState() {
    super.initState();
    _realtime = AdminRealtimeService(widget.sessionToken);
    _realtimeSubscription = _realtime.messages.listen((message) {
      if (!mounted) return;
      if (message.type == 'realtime.resync-required') {
        if (message.changesActivity) unawaited(_reloadActivities());
      } else if (message.type == 'activity.created') {
        final raw = message.payload['activity'];
        if (raw is Map) {
          _activityPatchRevision++;
          _mergeActivities([
            AdminActivity.fromJson(Map<String, dynamic>.from(raw)),
          ]);
        }
      }
      final resync = message.type == 'realtime.resync-required';
      final catalogSection =
          _section == _AdminSection.menus ||
          _section == _AdminSection.ingredients;
      if (message.changesDashboard) {
        setState(() => _dashboardRevision++);
      }
      if (_section == _AdminSection.overview ||
          isLocalAdminMutation(message.mutationId)) {
        return;
      }
      if (catalogSection && !message.changesCatalog) {
        return;
      }
      if ((resync &&
              (!catalogSection ||
                  message.staleResources.contains('catalog'))) ||
          message.type.startsWith('catalog.') ||
          message.type == 'hall.layout.changed' ||
          message.type == 'hall.catalog.changed' ||
          message.type == 'hall.orders.changed' ||
          (message.type == 'dashboard.changed' &&
              _section == _AdminSection.halls) ||
          message.type.startsWith('halls.') ||
          message.type.startsWith('employee.')) {
        setState(() => _sectionRevision++);
      }
    });
    if (widget.sessionToken.isNotEmpty) unawaited(_reloadActivities());
    unawaited(_realtime.start());
  }

  Future<void> _reloadActivities() {
    final pending = _activityReload;
    if (pending != null) return pending;
    late final Future<void> request;
    request = _fetchActivities().whenComplete(() {
      if (identical(_activityReload, request)) _activityReload = null;
    });
    _activityReload = request;
    return request;
  }

  Future<void> _fetchActivities() async {
    final patchRevision = _activityPatchRevision;
    try {
      final loaded = await widget.loadActivities(widget.sessionToken);
      if (!mounted) return;
      if (patchRevision == _activityPatchRevision) {
        _replaceActivities(loaded);
      } else {
        _mergeActivities(loaded);
      }
    } on Object {
      // A later activity patch or a versioned reconnect can repair the list.
    }
  }

  void _replaceActivities(Iterable<AdminActivity> incoming) {
    final activities = incoming.toList()
      ..sort((left, right) => right.id.compareTo(left.id));
    _activities.value = activities.take(100).toList(growable: false);
  }

  void _mergeActivities(Iterable<AdminActivity> incoming) {
    final byId = <int, AdminActivity>{
      for (final activity in _activities.value) activity.id: activity,
      for (final activity in incoming) activity.id: activity,
    };
    final merged = byId.values.toList()
      ..sort((left, right) => right.id.compareTo(left.id));
    _activities.value = merged.take(100).toList(growable: false);
  }

  @override
  void dispose() {
    _realtimeSubscription?.cancel();
    unawaited(_realtime.dispose());
    _activities.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final desktop = constraints.maxWidth >= 840;
        if (desktop) {
          return Scaffold(
            body: Row(
              children: [
                SizedBox(
                  width: 244,
                  child: _NavigationPanel(
                    adminName: widget.adminName,
                    spanish: _es,
                    onLogout: widget.onLogout,
                    selected: _section,
                    onSelected: (value) => setState(() => _section = value),
                  ),
                ),
                const VerticalDivider(width: 1, color: Color(0xFFE3E4E4)),
                Expanded(child: _content()),
              ],
            ),
          );
        }

        return Scaffold(
          appBar: AppBar(
            backgroundColor: _surface,
            surfaceTintColor: Colors.transparent,
            titleSpacing: 4,
            title: _AdminIdentity(name: widget.adminName, compact: true),
          ),
          drawer: Drawer(
            child: SafeArea(
              child: _NavigationPanel(
                adminName: widget.adminName,
                spanish: _es,
                onLogout: widget.onLogout,
                hideIdentity: true,
                selected: _section,
                closeOnSelect: true,
                onSelected: (value) => setState(() => _section = value),
              ),
            ),
          ),
          body: _content(),
        );
      },
    );
  }

  Widget _content() => KeyedSubtree(
    key: ValueKey('${_section.name}-$_sectionRevision'),
    child: switch (_section) {
      _AdminSection.overview => _DashboardContent(
        spanish: _es,
        token: widget.sessionToken,
        dashboardRevision: _dashboardRevision,
        activities: _activities,
      ),
      _AdminSection.employees => EmployeesPage(
        spanish: _es,
        token: widget.sessionToken,
      ),
      _AdminSection.halls => HallsPage(
        spanish: _es,
        token: widget.sessionToken,
      ),
      _AdminSection.menus => MenusPage(
        spanish: _es,
        token: widget.sessionToken,
      ),
      _AdminSection.ingredients => IngredientsPage(
        spanish: _es,
        token: widget.sessionToken,
      ),
      _AdminSection.history => OrderHistoryPage(
        spanish: _es,
        token: widget.sessionToken,
      ),
    },
  );
}
