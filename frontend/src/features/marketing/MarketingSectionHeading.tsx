import { Stack, Typography } from '@mui/material';

export function MarketingSectionHeading({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description: string;
}) {
  return (
    <Stack alignItems="center" textAlign="center" spacing={1.5} sx={{ maxWidth: 900, mx: 'auto' }}>
      <Typography variant="overline" color="primary" sx={{ fontWeight: 800, letterSpacing: 2 }}>
        {eyebrow}
      </Typography>
      <Typography
        variant="h3"
        sx={{
          fontSize: { xs: 32, md: 42 },
          fontWeight: 800,
          letterSpacing: '-0.035em',
          lineHeight: 1.14,
        }}
      >
        {title}
      </Typography>
      <Typography
        color="text.secondary"
        sx={{ maxWidth: 720, mx: 'auto', fontSize: { xs: 16, md: 17 }, lineHeight: 1.7 }}
      >
        {description}
      </Typography>
    </Stack>
  );
}
