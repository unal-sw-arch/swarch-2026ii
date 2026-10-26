import React from 'react';
import { InventoryAdminPanel } from './components/InventoryAdminPanel';

export function EquipmentAdminPage(): React.JSX.Element {
  return <InventoryAdminPanel isSystemAdmin={false} />;
}
