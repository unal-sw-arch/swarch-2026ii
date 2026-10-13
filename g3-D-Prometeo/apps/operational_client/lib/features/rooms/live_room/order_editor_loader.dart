part of '../live_room_page.dart';

class _OrderEditorLoader extends StatefulWidget {
  const _OrderEditorLoader({
    required this.spanish,
    required this.loadMenus,
    required this.builder,
  });

  final bool spanish;
  final Future<List<ClientRoomMenu>> Function() loadMenus;
  final Widget Function(List<ClientRoomMenu> menus) builder;

  @override
  State<_OrderEditorLoader> createState() => _OrderEditorLoaderState();
}

class _OrderEditorLoaderState extends State<_OrderEditorLoader> {
  late Future<List<ClientRoomMenu>> _menus = widget.loadMenus();

  @override
  Widget build(BuildContext context) => FutureBuilder<List<ClientRoomMenu>>(
    future: _menus,
    builder: (context, snapshot) {
      if (snapshot.hasData) return widget.builder(snapshot.data!);
      if (!snapshot.hasError) {
        return const Scaffold(
          backgroundColor: Color(0xFFFAF9F6),
          body: Center(child: CircularProgressIndicator()),
        );
      }
      return Scaffold(
        backgroundColor: const Color(0xFFFAF9F6),
        body: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(
                    Icons.cloud_off_outlined,
                    size: 42,
                    color: Color(0xFF71859B),
                  ),
                  const SizedBox(height: 14),
                  Text(
                    widget.spanish
                        ? 'No se pudo cargar el menú.'
                        : 'The menu could not be loaded.',
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 18),
                  Wrap(
                    spacing: 10,
                    children: [
                      OutlinedButton(
                        onPressed: () => Navigator.pop(context),
                        child: Text(widget.spanish ? 'Cerrar' : 'Close'),
                      ),
                      FilledButton.icon(
                        onPressed: () =>
                            setState(() => _menus = widget.loadMenus()),
                        icon: const Icon(Icons.refresh_rounded),
                        label: Text(widget.spanish ? 'Reintentar' : 'Retry'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      );
    },
  );
}
