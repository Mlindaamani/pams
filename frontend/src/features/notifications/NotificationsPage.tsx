import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Checkbox,
  FormControlLabel,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from '@mui/material';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { dateTime } from '@/lib/format';
import { toast } from '@/components/Toast';
import { listNotifications, markAllRead, markNotificationRead } from './api';

export function NotificationsPage() {
  const queryClient = useQueryClient();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const notifications = useQuery({
    queryKey: ['notifications', { unreadOnly }],
    queryFn: () => listNotifications({ unread: unreadOnly || undefined, size: 100 }),
  });
  const markAll = useMutation({
    mutationFn: markAllRead,
    onSuccess: (count) => {
      toast.success(`${count} notification(s) marked read.`);
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
  const markRead = useMutation({
    mutationFn: (id: string) => markNotificationRead(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  if (notifications.isLoading) return <Loading />;
  const rows = notifications.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Notifications"
        actions={
          <Button variant="outlined" onClick={() => markAll.mutate()} disabled={markAll.isPending}>
            Mark all read
          </Button>
        }
      />
      <FormControlLabel
        control={
          <Checkbox
            checked={unreadOnly}
            onChange={(event) => setUnreadOnly(event.target.checked)}
          />
        }
        label="Unread only"
      />
      {rows.length === 0 ? (
        <EmptyState message="No notifications." />
      ) : (
        <List>
          {rows.map((notification) => (
            <ListItem
              key={notification.id}
              divider
              secondaryAction={
                !notification.readAt && (
                  <Button size="small" onClick={() => markRead.mutate(notification.id!)}>
                    Mark read
                  </Button>
                )
              }
            >
              <ListItemText
                primary={notification.title}
                secondary={
                  <Stack>
                    <Typography variant="body2">{notification.body}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {dateTime(notification.createdAt)}
                    </Typography>
                  </Stack>
                }
              />
            </ListItem>
          ))}
        </List>
      )}
    </>
  );
}
