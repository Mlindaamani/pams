import { createBrowserRouter, Navigate, useParams } from 'react-router-dom';
import { AppLayout } from '@/components/AppLayout';
import { LoginPage } from '@/auth/LoginPage';
import { RequireAbility } from '@/auth/RequireAbility';
import { RequireAuth } from '@/auth/RequireAuth';
import { HomePage } from '@/features/dashboard/HomePage';
import { RequestListPage } from '@/features/requests/RequestListPage';
import { RequestFormPage } from '@/features/requests/RequestFormPage';
import { RequestDetailPage } from '@/features/requests/RequestDetailPage';
import { ApprovalInboxPage } from '@/features/approvals/InboxPage';
import { VendorsPage } from '@/features/vendors/VendorsPage';
import { BudgetsPage } from '@/features/budgets/BudgetsPage';
import { PurchaseOrdersPage } from '@/features/purchaseorders/PurchaseOrdersPage';
import { PurchaseOrderDetailPage } from '@/features/purchaseorders/PurchaseOrderDetailPage';
import { InvoicesPage } from '@/features/invoices/InvoicesPage';
import { InvoiceDetailPage } from '@/features/invoices/InvoiceDetailPage';
import { ReportsPage } from '@/features/reports/ReportsPage';
import { PoliciesPage } from '@/features/admin/PoliciesPage';
import { DepartmentsPage } from '@/features/admin/DepartmentsPage';
import { UsersPage } from '@/features/admin/UsersPage';
import { CatalogPage } from '@/features/admin/CatalogPage';
import { OrganizationPage } from '@/features/admin/OrganizationPage';
import { BrandingPage } from '@/features/admin/BrandingPage';
import { WebhooksPage } from '@/features/admin/WebhooksPage';
import { DelegationsPage } from '@/features/approvals/DelegationsPage';
import { ProfilePage } from '@/features/profile/ProfilePage';
import { SystemPage } from '@/features/system/SystemPage';
import { RolesPermissionsPage } from '@/features/admin/RolesPermissionsPage';
import { WorkflowConfigPage } from '@/features/admin/WorkflowConfigPage';
import { PlatformDashboardPage } from '@/features/platform/PlatformDashboardPage';
import { PlatformFeatureFormPage } from '@/features/platform/PlatformFeatureFormPage';
import { PlatformPlanFormPage } from '@/features/platform/PlatformPlanFormPage';
import { PlatformPlansPage } from '@/features/platform/PlatformPlansPage';
import { PlatformSubscriptionDetailPage } from '@/features/platform/PlatformSubscriptionDetailPage';
import { PlatformSubscriptionsPage } from '@/features/platform/PlatformSubscriptionsPage';
import { TenantDetailPage } from '@/features/platform/TenantDetailPage';
import { TenantFormPage } from '@/features/platform/TenantFormPage';
import { TenantInvitePage } from '@/features/platform/TenantInvitePage';
import { BillingPage } from '@/features/subscription/BillingPage';
import { PlanChangePreviewPage } from '@/features/subscription/PlanChangePreviewPage';
import { TenantsPage } from '@/features/platform/TenantsPage';
import { NotFoundPage } from '@/features/system/NotFoundPage';
import { ForbiddenPage } from '@/features/system/ForbiddenPage';
import { AuditPage } from '@/features/audit/AuditPage';
import { PaymentsPage } from '@/features/payments/PaymentsPage';
import { PaymentFormPage } from '@/features/payments/PaymentFormPage';
import { PaymentAgingPage } from '@/features/payments/PaymentAgingPage';
import { NotificationsPage } from '@/features/notifications/NotificationsPage';
import type { Action, Subject } from '@/auth/ability';

function LegacySubscriptionRedirect() {
  const { id } = useParams();
  return <Navigate to={`/platform/revenue/${id ?? ''}`} replace />;
}

function guard(action: Action, subject: Subject, element: JSX.Element) {
  return (
    <RequireAbility action={action} subject={subject}>
      {element}
    </RequireAbility>
  );
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <HomePage /> },
      { path: 'requests', element: guard('read', 'PurchaseRequest', <RequestListPage />) },
      { path: 'requests/new', element: guard('create', 'PurchaseRequest', <RequestFormPage />) },
      { path: 'requests/:id', element: guard('read', 'PurchaseRequest', <RequestDetailPage />) },
      {
        path: 'requests/:id/edit',
        element: guard('update', 'PurchaseRequest', <RequestFormPage />),
      },
      { path: 'approvals', element: guard('read', 'Approval', <ApprovalInboxPage />) },
      { path: 'vendors', element: guard('read', 'Vendor', <VendorsPage />) },
      { path: 'budgets', element: guard('read', 'Budget', <BudgetsPage />) },
      { path: 'purchase-orders', element: guard('read', 'PurchaseOrder', <PurchaseOrdersPage />) },
      {
        path: 'purchase-orders/:id',
        element: guard('read', 'PurchaseOrder', <PurchaseOrderDetailPage />),
      },
      { path: 'invoices', element: guard('read', 'Invoice', <InvoicesPage />) },
      { path: 'invoices/:id', element: guard('read', 'Invoice', <InvoiceDetailPage />) },
      { path: 'reports', element: guard('read', 'Report', <ReportsPage />) },
      { path: 'payments', element: guard('read', 'Payment', <PaymentsPage />) },
      { path: 'payments/new', element: guard('manage', 'Payment', <PaymentFormPage />) },
      { path: 'payments/aging', element: guard('read', 'Payment', <PaymentAgingPage />) },
      { path: 'platform/dashboard', element: guard('read', 'Platform', <PlatformDashboardPage />) },
      {
        path: 'platform/performance',
        element: <Navigate to="/platform/tenants?tab=performance" replace />,
      },
      {
        path: 'platform/revenue',
        element: guard('read', 'Tenant', <PlatformSubscriptionsPage />),
      },
      {
        path: 'platform/subscriptions',
        element: <Navigate to="/platform/revenue" replace />,
      },
      {
        path: 'platform/plans',
        element: guard('read', 'Tenant', <PlatformPlansPage />),
      },
      {
        path: 'platform/plans/new',
        element: guard('manage', 'Tenant', <PlatformPlanFormPage />),
      },
      {
        path: 'platform/plans/:code/edit',
        element: guard('manage', 'Tenant', <PlatformPlanFormPage />),
      },
      {
        path: 'platform/features/new',
        element: guard('manage', 'Tenant', <PlatformFeatureFormPage />),
      },
      {
        path: 'platform/features/:code/edit',
        element: guard('manage', 'Tenant', <PlatformFeatureFormPage />),
      },
      {
        path: 'platform/revenue/:id',
        element: guard('read', 'Tenant', <PlatformSubscriptionDetailPage />),
      },
      {
        path: 'platform/subscriptions/:id',
        element: <LegacySubscriptionRedirect />,
      },
      { path: 'platform/tenants', element: guard('read', 'Tenant', <TenantsPage />) },
      {
        path: 'platform/tenants/new',
        element: guard('manage', 'Tenant', <TenantFormPage />),
      },
      { path: 'platform/tenants/:id', element: guard('read', 'Tenant', <TenantDetailPage />) },
      {
        path: 'platform/tenants/:id/edit',
        element: guard('manage', 'Tenant', <TenantFormPage />),
      },
      {
        path: 'platform/tenants/:id/invite',
        element: guard('manage', 'Tenant', <TenantInvitePage />),
      },
      { path: 'delegations', element: guard('read', 'Delegation', <DelegationsPage />) },
      { path: 'profile', element: <ProfilePage /> },
      { path: 'admin/users', element: guard('read', 'User', <UsersPage />) },
      { path: 'admin/departments', element: guard('read', 'Department', <DepartmentsPage />) },
      { path: 'admin/catalog', element: guard('read', 'Catalog', <CatalogPage />) },
      { path: 'admin/organization', element: guard('read', 'Organization', <OrganizationPage />) },
      { path: 'admin/branding', element: guard('read', 'Branding', <BrandingPage />) },
      { path: 'admin/system', element: guard('read', 'System', <SystemPage />) },
      { path: 'admin/access', element: guard('read', 'Config', <RolesPermissionsPage />) },
      { path: 'admin/billing', element: guard('read', 'Subscription', <BillingPage />) },
      {
        path: 'admin/billing/change-plan',
        element: guard('manage', 'Subscription', <PlanChangePreviewPage />),
      },
      { path: 'admin/workflow', element: guard('read', 'Workflow', <WorkflowConfigPage />) },
      { path: 'admin/policies', element: guard('read', 'Policy', <PoliciesPage />) },
      { path: 'admin/webhooks', element: guard('read', 'Webhook', <WebhooksPage />) },
      { path: 'audit', element: guard('read', 'Audit', <AuditPage />) },
      { path: 'notifications', element: <NotificationsPage /> },
      { path: '403', element: <ForbiddenPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
