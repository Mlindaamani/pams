import { Navigate } from 'react-router-dom';
import { useAbility } from '@/auth/AbilityProvider';
import { DashboardPage } from './DashboardPage';

export function HomePage() {
  const ability = useAbility();
  if (ability.can('read', 'Platform') && !ability.can('read', 'PurchaseRequest')) {
    return <Navigate to="/platform/dashboard" replace />;
  }
  return <DashboardPage />;
}
