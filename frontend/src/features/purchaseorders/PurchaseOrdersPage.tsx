import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Stack } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { Link } from '@mui/material';
import { Can } from '@/auth/AbilityProvider';
import { useBranding } from '@/branding/BrandingProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { money, dateTime, downloadBlob } from '@/lib/format';
import { listVendors } from '@/features/vendors/api';
import {
  cancelPurchaseOrder,
  issuePurchaseOrder,
  listPurchaseOrders,
  purchaseOrderPdf,
} from './api';

export function PurchaseOrdersPage() {
  const { branding } = useBranding();
  const queryClient = useQueryClient();
  const orders = useQuery({
    queryKey: ['purchase-orders'],
    queryFn: () => listPurchaseOrders({ size: 100 }),
  });
  const vendors = useQuery({ queryKey: ['vendors'], queryFn: () => listVendors({ size: 100 }) });
  const [cancelling, setCancelling] = useState<{
    id: string;
    version: number;
    poNumber?: string;
  } | null>(null);

  const issue = useMutation({
    mutationFn: (order: { id: string; version: number }) =>
      issuePurchaseOrder(order.id, order.version),
    onSuccess: () => {
      toast.success('Purchase order issued.');
      void queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
    },
  });
  const cancel = useMutation({
    mutationFn: (order: { id: string; version: number }) =>
      cancelPurchaseOrder(order.id, order.version),
    onSuccess: () => {
      toast.success('Purchase order cancelled.');
      void queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
    },
  });

  if (orders.isLoading) return <Loading />;
  const rows = orders.data?.content ?? [];
  const vendorName = (id?: string) =>
    vendors.data?.content.find((vendor) => vendor.id === id)?.name ?? id;

  return (
    <>
      <PageHeader title={branding.orderLabel} />
      <ConfirmDialog
        open={Boolean(cancelling)}
        title="Cancel purchase order"
        message={`Cancel ${cancelling?.poNumber ?? 'this purchase order'}? Committed budget will be released.`}
        confirmLabel="Cancel purchase order"
        color="warning"
        busy={cancel.isPending}
        onCancel={() => setCancelling(null)}
        onConfirm={() => {
          if (cancelling)
            cancel.mutate(
              { id: cancelling.id, version: cancelling.version },
              { onSuccess: () => setCancelling(null) },
            );
        }}
      />
      {rows.length === 0 ? (
        <EmptyState message="No purchase orders yet." />
      ) : (
        <DataTable
          ariaLabel="Purchase orders"
          rows={rows}
          getRowId={(order) => order.id!}
          columns={[
            {
              key: 'number',
              label: 'Number',
              render: (order) => (
                <Link
                  component={RouterLink}
                  to={`/purchase-orders/${order.id}`}
                  color="primary.main"
                  underline="hover"
                  sx={{ fontWeight: 600 }}
                >
                  {order.poNumber}
                </Link>
              ),
            },
            {
              key: 'vendor',
              label: 'Vendor',
              render: (order) => vendorName(order.vendorId),
              sortValue: (order) => vendorName(order.vendorId) ?? '',
            },
            {
              key: 'status',
              label: 'Status',
              render: (order) => order.status,
              sortValue: (order) => order.status ?? '',
            },
            {
              key: 'total',
              label: 'Total',
              render: (order) => money(order.total),
              sortValue: (order) => Number(order.total?.amount ?? 0),
            },
            {
              key: 'issued',
              label: 'Issued',
              render: (order) => dateTime(order.issuedAt),
              sortValue: (order) => order.issuedAt ?? '',
            },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              render: (order) => (
                <Stack direction="row" spacing={1} justifyContent="flex-end">
                  <Button
                    size="small"
                    onClick={async () => {
                      const blob = await purchaseOrderPdf(order.id!);
                      downloadBlob(blob, `${order.poNumber}.pdf`);
                    }}
                  >
                    PDF
                  </Button>
                  <Can I="issue" a="PurchaseOrder">
                    {['DRAFT', 'APPROVED'].includes(order.status ?? '') && (
                      <Button
                        size="small"
                        variant="contained"
                        onClick={() => issue.mutate({ id: order.id!, version: order.version ?? 0 })}
                      >
                        Issue
                      </Button>
                    )}
                  </Can>
                  <Can I="cancel" a="PurchaseOrder">
                    {['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'ISSUED'].includes(
                      order.status ?? '',
                    ) && (
                      <Button
                        size="small"
                        color="error"
                        onClick={() =>
                          setCancelling({
                            id: order.id!,
                            version: order.version ?? 0,
                            poNumber: order.poNumber,
                          })
                        }
                      >
                        Cancel
                      </Button>
                    )}
                  </Can>
                </Stack>
              ),
            },
          ]}
        />
      )}
    </>
  );
}
