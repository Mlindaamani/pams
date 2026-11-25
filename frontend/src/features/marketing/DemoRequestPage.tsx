import { useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Container,
  Grid,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import { problemMessage } from '@/api/client';
import { toast } from '@/components/Toast';
import { submitDemoRequest } from './api';
import { PublicLayout } from './PublicLayout';

const assurances = [
  'A focused demo, not a generic sales pitch',
  'Guidance on plan fit and onboarding',
  'A clear view of audit and approval controls',
];

export function DemoRequestPage() {
  const [searchParams] = useSearchParams();
  const planCode = searchParams.get('plan');
  const planLabel = planCode ? formatPlanCode(planCode) : null;
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [company, setCompany] = useState('');
  const [phone, setPhone] = useState('');
  const [message, setMessage] = useState(
    planLabel ? `I am interested in the ${planLabel} plan.` : '',
  );
  const [sent, setSent] = useState(false);

  const submit = useMutation({
    mutationFn: () =>
      submitDemoRequest({
        name,
        email,
        company,
        phone: phone || undefined,
        message: message || undefined,
      }),
    onSuccess: () => {
      setSent(true);
      toast.success('Demo request received.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  return (
    <PublicLayout>
      <Box sx={{ position: 'relative', overflow: 'hidden' }}>
        <Box
          aria-hidden
          sx={{
            position: 'absolute',
            top: -240,
            left: '50%',
            width: 1000,
            height: 700,
            transform: 'translateX(-50%)',
            background:
              'radial-gradient(circle, rgba(11,92,173,0.16) 0%, rgba(11,92,173,0.05) 40%, transparent 70%)',
            pointerEvents: 'none',
          }}
        />
        <Container maxWidth="lg" sx={{ position: 'relative', pt: { xs: 8, md: 11 }, pb: 4 }}>
          <Stack alignItems="center" textAlign="center" spacing={2}>
            <Typography
              variant="overline"
              color="primary"
              sx={{ fontWeight: 800, letterSpacing: 2 }}
            >
              Request a demo
            </Typography>
            <Typography
              component="h1"
              variant="h2"
              sx={{
                maxWidth: 860,
                fontSize: { xs: 42, sm: 52, md: 62 },
                fontWeight: 800,
                letterSpacing: '-0.045em',
                lineHeight: 1.05,
              }}
            >
              See PAMS in action
            </Typography>
            <Typography
              color="text.secondary"
              sx={{ maxWidth: 700, fontSize: { xs: 16, md: 18 }, lineHeight: 1.7 }}
            >
              Tell us how your organization buys today. We will tailor the walkthrough to your
              procurement, approval and AP workflow.
            </Typography>
            <Stack
              direction="row"
              spacing={{ xs: 1.5, sm: 2.5 }}
              flexWrap="wrap"
              useFlexGap
              justifyContent="center"
              sx={{ mt: 1 }}
            >
              {assurances.map((item) => (
                <Stack key={item} direction="row" spacing={0.75} alignItems="center">
                  <CheckCircleRoundedIcon color="success" sx={{ fontSize: 17 }} />
                  <Typography variant="body2" color="text.secondary">
                    {item}
                  </Typography>
                </Stack>
              ))}
            </Stack>
          </Stack>

          <Card
            sx={{
              maxWidth: 860,
              mx: 'auto',
              mt: { xs: 5, md: 6 },
              borderRadius: 3,
              boxShadow: '0 28px 80px rgba(7,64,121,0.14)',
            }}
          >
            <CardContent sx={{ p: { xs: 3, md: 5 } }}>
              {sent ? (
                <Stack alignItems="center" textAlign="center" spacing={2} sx={{ py: 2 }}>
                  <Box
                    sx={{
                      width: 64,
                      height: 64,
                      borderRadius: '50%',
                      bgcolor: 'rgba(46,125,50,0.10)',
                      color: 'success.main',
                      display: 'grid',
                      placeItems: 'center',
                    }}
                  >
                    <CheckCircleRoundedIcon sx={{ fontSize: 38 }} />
                  </Box>
                  <Typography variant="h4" fontWeight={800}>
                    Request received
                  </Typography>
                  <Typography color="text.secondary" sx={{ maxWidth: 540, lineHeight: 1.7 }}>
                    Thank you, {name}. Our team will follow up at {email} to schedule your PAMS
                    walkthrough.
                  </Typography>
                  <Stack
                    direction="row"
                    spacing={1.5}
                    flexWrap="wrap"
                    useFlexGap
                    justifyContent="center"
                    sx={{ mt: 1 }}
                  >
                    <Button component={RouterLink} to="/pricing" variant="outlined">
                      Back to pricing
                    </Button>
                    <Button component={RouterLink} to="/welcome" variant="contained">
                      Return home
                    </Button>
                  </Stack>
                </Stack>
              ) : (
                <Stack
                  component="form"
                  spacing={2.5}
                  onSubmit={(event) => {
                    event.preventDefault();
                    submit.mutate();
                  }}
                >
                  <Stack
                    direction={{ xs: 'column', sm: 'row' }}
                    justifyContent="space-between"
                    alignItems={{ xs: 'flex-start', sm: 'center' }}
                    spacing={1.5}
                  >
                    <Box>
                      <Typography variant="h5" fontWeight={700}>
                        Tell us about your team
                      </Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        Share a few details and we will tailor the conversation.
                      </Typography>
                    </Box>
                    {planLabel && (
                      <Chip
                        size="small"
                        variant="outlined"
                        color="primary"
                        label={`Plan: ${planLabel}`}
                      />
                    )}
                  </Stack>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <TextField
                        label="Your name"
                        value={name}
                        onChange={(event) => setName(event.target.value)}
                        autoComplete="name"
                        required
                        fullWidth
                      />
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <TextField
                        label="Work email"
                        type="email"
                        value={email}
                        onChange={(event) => setEmail(event.target.value)}
                        autoComplete="email"
                        required
                        fullWidth
                      />
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <TextField
                        label="Organization"
                        value={company}
                        onChange={(event) => setCompany(event.target.value)}
                        autoComplete="organization"
                        required
                        fullWidth
                      />
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <TextField
                        label="Phone (optional)"
                        value={phone}
                        onChange={(event) => setPhone(event.target.value)}
                        autoComplete="tel"
                        fullWidth
                      />
                    </Grid>
                  </Grid>
                  <TextField
                    label="What would you like to improve?"
                    value={message}
                    onChange={(event) => setMessage(event.target.value)}
                    multiline
                    minRows={4}
                    fullWidth
                  />
                  {submit.isError && <Alert severity="error">{problemMessage(submit.error)}</Alert>}
                  <Button
                    type="submit"
                    variant="contained"
                    size="large"
                    fullWidth
                    disabled={submit.isPending || !name || !email || !company}
                  >
                    {submit.isPending ? 'Sending…' : 'Request a demo'}
                  </Button>
                  <Typography variant="caption" color="text.secondary" textAlign="center">
                    We use this information only to respond to your request.
                  </Typography>
                </Stack>
              )}
            </CardContent>
          </Card>
        </Container>
      </Box>
    </PublicLayout>
  );
}

function formatPlanCode(code: string) {
  return code
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}
