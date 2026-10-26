import { Hono } from 'hono';
import catalogWriteRoutes from './features/catalog/routes';
import orderRoutes from './features/orders/routes';
import liveLayoutRoutes from './features/halls/routes';
import adminOperationRoutes from './features/admin/routes';
import type { CloudEnvironment } from './types';

// This router is invoked only from RestaurantCoordinator. Public mutations are
// intercepted before they can reach the read-only Worker router.
const mutationApp = new Hono<CloudEnvironment>();
mutationApp.route('/api/v1/admin', catalogWriteRoutes);
mutationApp.route('/api/v1/admin', adminOperationRoutes);
mutationApp.route('/api/v1', orderRoutes);
mutationApp.route('/api/v1', liveLayoutRoutes);
mutationApp.notFound((c) => c.json({ error: 'MUTATION_NOT_FOUND' }, 404));

export default mutationApp;
