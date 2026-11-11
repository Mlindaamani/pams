import { useEffect, useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ImageIcon from '@mui/icons-material/Image';
import { useBranding } from '@/branding/BrandingProvider';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import {
  removeBrandingFavicon,
  removeBrandingLogo,
  updateBranding,
  uploadBrandingFavicon,
  uploadBrandingLogo,
} from './brandingApi';

export function BrandingPage() {
  const { branding, logoUrl, refresh } = useBranding();
  const fileInput = useRef<HTMLInputElement>(null);
  const faviconInput = useRef<HTMLInputElement>(null);
  const [displayName, setDisplayName] = useState('');
  const [primaryColor, setPrimaryColor] = useState('#0B5CAD');
  const [secondaryColor, setSecondaryColor] = useState('#00695C');
  const [emailFromName, setEmailFromName] = useState('');
  const [supportEmail, setSupportEmail] = useState('');
  const [cornerRadius, setCornerRadius] = useState('10');
  const [fontFamily, setFontFamily] = useState('INTER');
  const [uiDensity, setUiDensity] = useState('COMFORTABLE');
  const [requestLabel, setRequestLabel] = useState('Requests');
  const [orderLabel, setOrderLabel] = useState('Purchase orders');
  const [vendorLabel, setVendorLabel] = useState('Vendors');
  const [budgetLabel, setBudgetLabel] = useState('Budgets');
  const [catalogLabel, setCatalogLabel] = useState('Catalog items');

  useEffect(() => {
    setDisplayName(branding.displayName);
    setPrimaryColor(branding.primaryColor);
    setSecondaryColor(branding.secondaryColor);
    setEmailFromName(branding.emailFromName ?? '');
    setSupportEmail(branding.supportEmail ?? '');
    setCornerRadius(String(branding.cornerRadius));
    setFontFamily(branding.fontFamily);
    setUiDensity(branding.uiDensity);
    setRequestLabel(branding.requestLabel);
    setOrderLabel(branding.orderLabel);
    setVendorLabel(branding.vendorLabel);
    setBudgetLabel(branding.budgetLabel);
    setCatalogLabel(branding.catalogLabel);
  }, [branding]);

  const save = useMutation({
    mutationFn: () =>
      updateBranding({
        displayName,
        primaryColor,
        secondaryColor,
        emailFromName: emailFromName || undefined,
        supportEmail: supportEmail || undefined,
        cornerRadius: Number(cornerRadius),
        fontFamily,
        uiDensity,
        requestLabel,
        orderLabel,
        vendorLabel,
        budgetLabel,
        catalogLabel,
      }),
    onSuccess: () => {
      refresh();
      toast.success('Branding updated.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const upload = useMutation({
    mutationFn: uploadBrandingLogo,
    onSuccess: () => {
      refresh();
      toast.success('Logo updated.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const removeLogo = useMutation({
    mutationFn: removeBrandingLogo,
    onSuccess: () => {
      refresh();
      toast.success('Logo removed.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const uploadFavicon = useMutation({
    mutationFn: uploadBrandingFavicon,
    onSuccess: () => {
      refresh();
      toast.success('Favicon updated.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const removeFavicon = useMutation({
    mutationFn: removeBrandingFavicon,
    onSuccess: () => {
      refresh();
      toast.success('Favicon removed.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (upload.isPending || uploadFavicon.isPending) return <Loading />;

  return (
    <>
      <PageHeader title="Branding" />
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Customize your PAMS workspace identity, colors and logo. The shared Keycloak sign-in page
        remains PAMS-branded.
      </Typography>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Workspace identity</Typography>
                <TextField
                  label="Display name"
                  value={displayName}
                  onChange={(event) => setDisplayName(event.target.value)}
                  helperText="Shown in the app header and browser title."
                  required
                />
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Box
                        sx={{
                          width: 36,
                          height: 36,
                          borderRadius: 1,
                          bgcolor: primaryColor,
                          border: '1px solid',
                          borderColor: 'divider',
                        }}
                      />
                      <TextField
                        label="Primary color"
                        value={primaryColor}
                        onChange={(event) => setPrimaryColor(event.target.value)}
                        fullWidth
                        helperText="#RRGGBB"
                        required
                      />
                    </Stack>
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Box
                        sx={{
                          width: 36,
                          height: 36,
                          borderRadius: 1,
                          bgcolor: secondaryColor,
                          border: '1px solid',
                          borderColor: 'divider',
                        }}
                      />
                      <TextField
                        label="Secondary color"
                        value={secondaryColor}
                        onChange={(event) => setSecondaryColor(event.target.value)}
                        fullWidth
                        helperText="#RRGGBB"
                        required
                      />
                    </Stack>
                  </Grid>
                </Grid>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, sm: 4 }}>
                    <TextField
                      select
                      label="Corner radius"
                      fullWidth
                      value={cornerRadius}
                      onChange={(event) => setCornerRadius(event.target.value)}
                      helperText="0–24px"
                    >
                      {['0', '4', '6', '8', '10', '12', '16', '24'].map((radius) => (
                        <MenuItem key={radius} value={radius}>
                          {radius}px
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 12, sm: 4 }}>
                    <TextField
                      select
                      label="Font family"
                      fullWidth
                      value={fontFamily}
                      onChange={(event) => setFontFamily(event.target.value)}
                    >
                      <MenuItem value="INTER">Inter</MenuItem>
                      <MenuItem value="ROBOTO">Roboto</MenuItem>
                      <MenuItem value="SYSTEM">System UI</MenuItem>
                      <MenuItem value="SERIF">Serif</MenuItem>
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 12, sm: 4 }}>
                    <TextField
                      select
                      label="Density"
                      fullWidth
                      value={uiDensity}
                      onChange={(event) => setUiDensity(event.target.value)}
                    >
                      <MenuItem value="COMFORTABLE">Comfortable</MenuItem>
                      <MenuItem value="COMPACT">Compact</MenuItem>
                    </TextField>
                  </Grid>
                </Grid>
                <Typography variant="h6" sx={{ pt: 1 }}>
                  Terminology
                </Typography>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField
                      label="Requests label"
                      fullWidth
                      value={requestLabel}
                      onChange={(event) => setRequestLabel(event.target.value)}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField
                      label="Purchase orders label"
                      fullWidth
                      value={orderLabel}
                      onChange={(event) => setOrderLabel(event.target.value)}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 4 }}>
                    <TextField
                      label="Vendors label"
                      fullWidth
                      value={vendorLabel}
                      onChange={(event) => setVendorLabel(event.target.value)}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 4 }}>
                    <TextField
                      label="Budgets label"
                      fullWidth
                      value={budgetLabel}
                      onChange={(event) => setBudgetLabel(event.target.value)}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 4 }}>
                    <TextField
                      label="Catalog label"
                      fullWidth
                      value={catalogLabel}
                      onChange={(event) => setCatalogLabel(event.target.value)}
                    />
                  </Grid>
                </Grid>
                <TextField
                  label="Email sender name"
                  value={emailFromName}
                  onChange={(event) => setEmailFromName(event.target.value)}
                  helperText="Used as the display name on notification emails."
                />
                <TextField
                  label="Support email"
                  value={supportEmail}
                  onChange={(event) => setSupportEmail(event.target.value)}
                  type="email"
                />
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="contained"
                    disabled={!displayName || save.isPending}
                    onClick={() => save.mutate()}
                  >
                    Save branding
                  </Button>
                  <Button onClick={() => refresh()}>Reset changes</Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6">Workspace logo</Typography>
              <Box
                sx={{
                  mt: 2,
                  mb: 2,
                  minHeight: 120,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  bgcolor: 'grey.50',
                  borderRadius: 2,
                  border: '1px dashed',
                  borderColor: 'divider',
                  p: 2,
                }}
              >
                {logoUrl ? (
                  <Box
                    component="img"
                    src={logoUrl}
                    alt={`${branding.displayName} logo`}
                    sx={{ maxHeight: 80, maxWidth: '100%', objectFit: 'contain' }}
                  />
                ) : (
                  <Stack alignItems="center" spacing={1}>
                    <ImageIcon color="disabled" />
                    <Typography variant="body2" color="text.secondary">
                      PAMS default mark
                    </Typography>
                  </Stack>
                )}
              </Box>
              <input
                ref={fileInput}
                type="file"
                accept="image/png,image/jpeg,image/webp,image/svg+xml"
                hidden
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) upload.mutate(file);
                  event.target.value = '';
                }}
              />
              <Stack direction="row" spacing={1}>
                <Button
                  variant="outlined"
                  disabled={upload.isPending}
                  onClick={() => fileInput.current?.click()}
                >
                  Upload logo
                </Button>
                {branding.hasLogo && (
                  <Button
                    color="error"
                    disabled={removeLogo.isPending}
                    onClick={() => removeLogo.mutate()}
                  >
                    Remove
                  </Button>
                )}
              </Stack>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                PNG, JPEG, WebP or SVG up to 2MB.
              </Typography>
              <Typography variant="subtitle2" sx={{ mt: 3 }}>
                Browser favicon
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {branding.hasFavicon ? 'Custom favicon is active.' : 'Using the PAMS default.'}
              </Typography>
              <input
                ref={faviconInput}
                type="file"
                accept="image/png,image/svg+xml,image/x-icon,image/vnd.microsoft.icon"
                hidden
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) uploadFavicon.mutate(file);
                  event.target.value = '';
                }}
              />
              <Stack direction="row" spacing={1}>
                <Button
                  variant="outlined"
                  disabled={uploadFavicon.isPending}
                  onClick={() => faviconInput.current?.click()}
                >
                  Upload favicon
                </Button>
                {branding.hasFavicon && (
                  <Button
                    color="error"
                    disabled={removeFavicon.isPending}
                    onClick={() => removeFavicon.mutate()}
                  >
                    Remove
                  </Button>
                )}
              </Stack>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                PNG, ICO or SVG up to 512KB.
              </Typography>
            </CardContent>
          </Card>
          <Alert severity="info" sx={{ mt: 2 }}>
            Branding colors are applied to buttons, links and highlights in both light and dark
            mode.
          </Alert>
        </Grid>
      </Grid>
    </>
  );
}
