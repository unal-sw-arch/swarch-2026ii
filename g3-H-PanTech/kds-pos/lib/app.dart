import 'package:flutter/material.dart';

import 'api/gateway_client.dart';
import 'auth/login_page.dart';
import 'auth/session_controller.dart';
import 'auth/session_store.dart';
import 'dispatch/dispatch_page.dart';
import 'kitchen/queue_controller.dart';
import 'kitchen/queue_page.dart';
import 'widgets/status_banner.dart';

class KdsApp extends StatefulWidget {
  const KdsApp({
    super.key,
    required this.sessionController,
    required this.client,
    this.pollInterval = const Duration(seconds: 4),
  });

  final SessionController sessionController;
  final GatewayClient client;
  final Duration pollInterval;

  @override
  State<KdsApp> createState() => _KdsAppState();
}

class _KdsAppState extends State<KdsApp> {
  @override
  void initState() {
    super.initState();
    widget.sessionController.restore();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'KDS · Panadería Express',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.brown),
        useMaterial3: true,
      ),
      home: ListenableBuilder(
        listenable: widget.sessionController,
        builder: (context, _) {
          final controller = widget.sessionController;
          if (controller.restoring) {
            return const Scaffold(body: Center(child: CircularProgressIndicator()));
          }
          final session = controller.session;
          if (session == null) return LoginPage(controller: controller);
          return HomeShell(
            key: ValueKey(session.userId),
            session: session,
            sessionController: controller,
            client: widget.client,
            pollInterval: widget.pollInterval,
          );
        },
      ),
    );
  }
}

class HomeShell extends StatefulWidget {
  const HomeShell({
    super.key,
    required this.session,
    required this.sessionController,
    required this.client,
    required this.pollInterval,
  });

  final Session session;
  final SessionController sessionController;
  final GatewayClient client;
  final Duration pollInterval;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  late final QueueController _queue;
  int _tab = 0;

  @override
  void initState() {
    super.initState();
    _queue = QueueController(client: widget.client, pollInterval: widget.pollInterval)..start();
  }

  @override
  void dispose() {
    _queue.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final session = widget.session;
    final tabs = <({String label, IconData icon, Widget page})>[
      if (session.canSeeKitchen)
        (label: 'Cocina', icon: Icons.soup_kitchen, page: QueuePage(controller: _queue, session: session)),
      if (session.canSeeDispatch)
        (label: 'Despacho', icon: Icons.shopping_bag, page: DispatchPage(controller: _queue, session: session)),
    ];
    final index = _tab.clamp(0, tabs.length - 1);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Panadería Express · KDS'),
        actions: [
          ListenableBuilder(
            listenable: _queue,
            builder: (context, _) => Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Center(
                child: Text(
                  _queue.lastUpdated == null ? 'Conectando…' : 'Actualizado ${formatClock(_queue.lastUpdated!)}',
                  key: const Key('updated'),
                ),
              ),
            ),
          ),
          IconButton(key: const Key('refresh'), tooltip: 'Actualizar', onPressed: _queue.refresh, icon: const Icon(Icons.refresh)),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Center(child: Text('${session.email} · ${session.roles.join(', ')}')),
          ),
          IconButton(
            key: const Key('logout'),
            tooltip: 'Cerrar sesión',
            onPressed: widget.sessionController.logout,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: tabs.isEmpty ? const Center(child: Text('Su cuenta no tiene vistas disponibles en el KDS.')) : tabs[index].page,
      bottomNavigationBar: tabs.length > 1
          ? NavigationBar(
              selectedIndex: index,
              onDestinationSelected: (i) => setState(() => _tab = i),
              destinations: [for (final t in tabs) NavigationDestination(icon: Icon(t.icon), label: t.label)],
            )
          : null,
    );
  }
}
