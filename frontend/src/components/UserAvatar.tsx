import { useEffect, useState } from 'react';
import { Avatar, type SxProps, type Theme } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { getMe, myAvatar } from '@/features/admin/usersApi';

interface Props {
  name?: string;
  size?: number;
  sx?: SxProps<Theme>;
}

export function UserAvatar({ name, size = 32, sx }: Props) {
  const [src, setSrc] = useState<string | undefined>();
  const me = useQuery({
    queryKey: ['users', 'me'],
    queryFn: getMe,
    retry: false,
    retryOnMount: false,
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  });
  const avatar = useQuery({
    queryKey: ['users', 'me', 'avatar'],
    queryFn: myAvatar,
    enabled: Boolean(me.data?.hasAvatar),
    retry: false,
    retryOnMount: false,
  });

  useEffect(() => {
    if (!avatar.data) {
      setSrc(undefined);
      return;
    }
    const url = URL.createObjectURL(avatar.data);
    setSrc(url);
    return () => URL.revokeObjectURL(url);
  }, [avatar.data]);

  return (
    <Avatar src={src} sx={{ width: size, height: size, ...sx }}>
      {String(name ?? me.data?.displayName ?? 'U')
        .slice(0, 1)
        .toUpperCase()}
    </Avatar>
  );
}
