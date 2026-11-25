import { Link as RouterLink } from 'react-router-dom';
import { Box, Button, Stack, Typography } from '@mui/material';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';

export function MarketingCtaPanel({
  title,
  description,
  actionLabel,
  actionTo,
}: {
  title: string;
  description: string;
  actionLabel: string;
  actionTo: string;
}) {
  return (
    <Box
      sx={{
        position: 'relative',
        overflow: 'hidden',
        borderRadius: 4,
        px: { xs: 3, md: 8 },
        py: { xs: 5, md: 7 },
        textAlign: 'center',
        color: '#FFFFFF',
        background: 'linear-gradient(135deg, #0B5CAD 0%, #074079 100%)',
      }}
    >
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          inset: 0,
          opacity: 0.12,
          backgroundImage:
            'linear-gradient(rgba(255,255,255,0.35) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.35) 1px, transparent 1px)',
          backgroundSize: '48px 48px',
        }}
      />
      <Stack alignItems="center" spacing={2} sx={{ position: 'relative' }}>
        <Typography
          variant="h3"
          sx={{
            color: '#FFFFFF',
            fontSize: { xs: 32, md: 44 },
            fontWeight: 800,
            letterSpacing: '-0.03em',
          }}
        >
          {title}
        </Typography>
        <Typography sx={{ maxWidth: 620, color: 'rgba(255,255,255,0.78)', lineHeight: 1.7 }}>
          {description}
        </Typography>
        <Button
          component={RouterLink}
          to={actionTo}
          variant="contained"
          color="secondary"
          size="large"
          endIcon={<ArrowForwardIcon />}
          sx={{ mt: 1 }}
        >
          {actionLabel}
        </Button>
      </Stack>
    </Box>
  );
}
