import 'package:flutter_test/flutter_test.dart';
import 'package:restaurante_admin/services/admin_realtime.dart';

void main() {
  test('admin catalog ignores unrelated realtime changes', () {
    expect(
      const AdminRealtimeMessage(type: 'hall.orders.changed').changesCatalog,
      isFalse,
    );
    expect(
      const AdminRealtimeMessage(type: 'hall.layout.changed').changesCatalog,
      isFalse,
    );
    expect(
      const AdminRealtimeMessage(
        type: 'realtime.resync-required',
        staleResources: {'dashboard', 'activity'},
      ).changesCatalog,
      isFalse,
    );
    expect(
      const AdminRealtimeMessage(
        type: 'realtime.resync-required',
        staleResources: {'catalog'},
      ).changesCatalog,
      isTrue,
    );
    expect(
      const AdminRealtimeMessage(
        type: 'catalog.product.changed',
      ).changesCatalog,
      isTrue,
    );
  });

  test('admin resync invalidates only the resource whose version changed', () {
    const ordersOnly = AdminRealtimeMessage(
      type: 'realtime.resync-required',
      staleResources: {'hall:3:orders'},
    );
    expect(ordersOnly.changesActivity, isFalse);
    expect(ordersOnly.changesDashboard, isFalse);

    const catalogOnly = AdminRealtimeMessage(
      type: 'realtime.resync-required',
      staleResources: {'catalog'},
    );
    expect(catalogOnly.changesActivity, isFalse);
    expect(catalogOnly.changesDashboard, isFalse);

    const activityOnly = AdminRealtimeMessage(
      type: 'realtime.resync-required',
      staleResources: {'activity'},
    );
    expect(activityOnly.changesActivity, isTrue);
    expect(activityOnly.changesDashboard, isFalse);

    const dashboardOnly = AdminRealtimeMessage(
      type: 'realtime.resync-required',
      staleResources: {'dashboard'},
    );
    expect(dashboardOnly.changesActivity, isFalse);
    expect(dashboardOnly.changesDashboard, isTrue);

    expect(
      const AdminRealtimeMessage(type: 'activity.created').changesActivity,
      isTrue,
    );
    expect(
      const AdminRealtimeMessage(type: 'activity.created').changesDashboard,
      isFalse,
    );
    expect(
      const AdminRealtimeMessage(type: 'dashboard.changed').changesDashboard,
      isTrue,
    );
  });
}
