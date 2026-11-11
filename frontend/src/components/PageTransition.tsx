import { keyframes } from '@emotion/react';
import { Box } from '@mui/material';
import { type PropsWithChildren } from 'react';
import { useLocation } from 'react-router-dom';

const enter = keyframes`
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
`;

export function PageTransition({ children }: PropsWithChildren) {
  const { pathname } = useLocation();
  return (
    <Box key={pathname} sx={{ animation: `${enter} 280ms ease-out` }}>
      {children}
    </Box>
  );
}
