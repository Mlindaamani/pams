import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  CardContent,
  Container,
  Grid,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { problemMessage } from '@/api/client';
import { toast } from '@/components/Toast';
import { submitDemoRequest } from './api';
import { PublicLayout } from './PublicLayout';

export function DemoRequestPage() {
  const [searchParams] = useSearchParams();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [company, setCompany] = useState('');
  const [phone, setPhone] = useState('');
  const [message, setMessage] = useState(
    searchParams.get('plan') ? `I am interested in the ${searchParams.get('plan')} plan.` : '',
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
      <Container maxWidth="md" sx={{ py: { xs: 6, md: 10 } }}>
        <Grid container spacing={4}>
          <Grid size={{ xs: 12, md: 5 }}>
            <Typography variant="h3" sx={{ mb: 2 }}>
              See PAMS in action
            </Typography>
            <Typography color="text.secondary" sx={{ mb: 3 }}>
              Tell us how your organization buys today. We will tailor the walkthrough to your
              procurement, approval and AP workflow.
            </Typography>
            <Stack spacing={1.5}>
              {[
                'A focused demo, not a generic sales pitch',
                'Guidance on plan fit and onboarding',
                'A clear view of audit and approval controls',
              ].map((item) => (
                <Typography key={item} variant="body2">
                  • {item}
                </Typography>
              ))}
            </Stack>
          </Grid>
          <Grid size={{ xs: 12, md: 7 }}>
            <Card>
              <CardContent sx={{ p: 3 }}>
                {sent ? (
                  <Alert severity="success">
                    Thank you. Your demo request has been received and our team will follow up.
                  </Alert>
                ) : (
                  <Stack
                    component="form"
                    spacing={2}
                    onSubmit={(event) => {
                      event.preventDefault();
                      submit.mutate();
                    }}
                  >
                    <Typography variant="h6">Request a demo</Typography>
                    <TextField
                      label="Your name"
                      value={name}
                      onChange={(event) => setName(event.target.value)}
                      required
                      fullWidth
                    />
                    <TextField
                      label="Work email"
                      type="email"
                      value={email}
                      onChange={(event) => setEmail(event.target.value)}
                      required
                      fullWidth
                    />
                    <TextField
                      label="Organization"
                      value={company}
                      onChange={(event) => setCompany(event.target.value)}
                      required
                      fullWidth
                    />
                    <TextField
                      label="Phone (optional)"
                      value={phone}
                      onChange={(event) => setPhone(event.target.value)}
                      fullWidth
                    />
                    <TextField
                      label="What would you like to improve?"
                      value={message}
                      onChange={(event) => setMessage(event.target.value)}
                      multiline
                      minRows={3}
                      fullWidth
                    />
                    <Button
                      type="submit"
                      variant="contained"
                      size="large"
                      disabled={submit.isPending || !name || !email || !company}
                    >
                      {submit.isPending ? 'Sending…' : 'Send request'}
                    </Button>
                    <Typography variant="caption" color="text.secondary">
                      We use this information only to respond to your request.
                    </Typography>
                  </Stack>
                )}
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Container>
    </PublicLayout>
  );
}
