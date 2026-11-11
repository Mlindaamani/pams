import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  AppBar,
  Badge,
  Box,
  Breadcrumbs,
  Button,
  Chip,
  Collapse,
  Divider,
  Drawer,
  IconButton,
  Link,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import NotificationsIcon from '@mui/icons-material/Notifications';
import DashboardIcon from '@mui/icons-material/Dashboard';
import DescriptionIcon from '@mui/icons-material/Description';
import ApprovalIcon from '@mui/icons-material/Approval';
import StorefrontIcon from '@mui/icons-material/Storefront';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import PaymentsIcon from '@mui/icons-material/Payments';
import RequestQuoteIcon from '@mui/icons-material/RequestQuote';
import BarChartIcon from '@mui/icons-material/BarChart';
import BusinessIcon from '@mui/icons-material/Business';
import HistoryIcon from '@mui/icons-material/History';
import PeopleIcon from '@mui/icons-material/People';
import Inventory2Icon from '@mui/icons-material/Inventory2';
import CorporateFareIcon from '@mui/icons-material/CorporateFare';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import SettingsIcon from '@mui/icons-material/Settings';
import SpaceDashboardIcon from '@mui/icons-material/SpaceDashboard';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import WorkspacePremiumIcon from '@mui/icons-material/WorkspacePremium';
import ContactMailIcon from '@mui/icons-material/ContactMail';
import PaletteIcon from '@mui/icons-material/Palette';
import WebhookIcon from '@mui/icons-material/Webhook';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import SecurityIcon from '@mui/icons-material/Security';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import ExpandMore from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import SearchIcon from '@mui/icons-material/Search';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import HomeIcon from '@mui/icons-material/Home';
import { useAbility } from '@/auth/AbilityProvider';
import { useBranding } from '@/branding/BrandingProvider';
import type { Action, Subject } from '@/auth/ability';
import { PageTransition } from '@/components/PageTransition';
import { ReturnToHandler } from '@/components/ReturnToHandler';
import { UserMenu } from '@/components/UserMenu';
import { useThemeMode } from '@/components/ThemeModeProvider';
import { CommandPalette } from '@/components/CommandPalette';
import { unreadCount } from '@/features/notifications/api';

interface NavItem {
  label: string;
  to: string;
  action: Action;
  subject: Subject;
  icon: ReactNode;
}

interface NavGroup {
  id: string;
  label: string;
  icon: ReactNode;
  items: NavItem[];
}

const standaloneItems: NavItem[] = [
  { label: 'Dashboard', to: '/', action: 'read', subject: 'Notification', icon: <DashboardIcon /> },
  {
    label: 'Dashboard',
    to: '/platform/dashboard',
    action: 'read',
    subject: 'Platform',
    icon: <SpaceDashboardIcon />,
  },
  {
    label: 'Tenants',
    to: '/platform/tenants',
    action: 'read',
    subject: 'Tenant',
    icon: <BusinessIcon />,
  },
  {
    label: 'Revenue',
    to: '/platform/revenue',
    action: 'read',
    subject: 'Tenant',
    icon: <CreditCardIcon />,
  },
  {
    label: 'Demo requests',
    to: '/platform/demo-requests',
    action: 'read',
    subject: 'Tenant',
    icon: <ContactMailIcon />,
  },
  {
    label: 'Subscription plans',
    to: '/platform/plans',
    action: 'read',
    subject: 'Tenant',
    icon: <WorkspacePremiumIcon />,
  },
];

const navGroups: NavGroup[] = [
  {
    id: 'procurement',
    label: 'Procurement',
    icon: <ShoppingCartIcon />,
    items: [
      {
        label: 'My requests',
        to: '/requests',
        action: 'read',
        subject: 'PurchaseRequest',
        icon: <DescriptionIcon />,
      },
      {
        label: 'Approvals',
        to: '/approvals',
        action: 'read',
        subject: 'Approval',
        icon: <ApprovalIcon />,
      },
      {
        label: 'Delegations',
        to: '/delegations',
        action: 'read',
        subject: 'Delegation',
        icon: <SwapHorizIcon />,
      },
      {
        label: 'Vendors',
        to: '/vendors',
        action: 'read',
        subject: 'Vendor',
        icon: <StorefrontIcon />,
      },
      {
        label: 'Purchase orders',
        to: '/purchase-orders',
        action: 'read',
        subject: 'PurchaseOrder',
        icon: <ReceiptLongIcon />,
      },
    ],
  },
  {
    id: 'finance',
    label: 'Finance',
    icon: <AccountBalanceWalletIcon />,
    items: [
      {
        label: 'Budgets',
        to: '/budgets',
        action: 'read',
        subject: 'Budget',
        icon: <AccountBalanceWalletIcon />,
      },
      {
        label: 'Invoices',
        to: '/invoices',
        action: 'read',
        subject: 'Invoice',
        icon: <RequestQuoteIcon />,
      },
      {
        label: 'Payments',
        to: '/payments',
        action: 'read',
        subject: 'Payment',
        icon: <PaymentsIcon />,
      },
      {
        label: 'Reports',
        to: '/reports',
        action: 'read',
        subject: 'Report',
        icon: <BarChartIcon />,
      },
    ],
  },
  {
    id: 'administration',
    label: 'Administration',
    icon: <AdminPanelSettingsIcon />,
    items: [
      { label: 'Users', to: '/admin/users', action: 'read', subject: 'User', icon: <PeopleIcon /> },
      {
        label: 'Departments',
        to: '/admin/departments',
        action: 'read',
        subject: 'Department',
        icon: <BusinessIcon />,
      },
      {
        label: 'Catalog',
        to: '/admin/catalog',
        action: 'read',
        subject: 'Catalog',
        icon: <Inventory2Icon />,
      },
      {
        label: 'Organization',
        to: '/admin/organization',
        action: 'read',
        subject: 'Organization',
        icon: <CorporateFareIcon />,
      },
      {
        label: 'Branding',
        to: '/admin/branding',
        action: 'read',
        subject: 'Branding',
        icon: <PaletteIcon />,
      },
      {
        label: 'Roles & permissions',
        to: '/admin/access',
        action: 'read',
        subject: 'Config',
        icon: <SecurityIcon />,
      },
      {
        label: 'Billing & subscription',
        to: '/admin/billing',
        action: 'read',
        subject: 'Subscription',
        icon: <CreditCardIcon />,
      },
      {
        label: 'Workflow',
        to: '/admin/workflow',
        action: 'read',
        subject: 'Workflow',
        icon: <AccountTreeIcon />,
      },
      {
        label: 'Webhooks',
        to: '/admin/webhooks',
        action: 'read',
        subject: 'Webhook',
        icon: <WebhookIcon />,
      },
    ],
  },
  {
    id: 'system',
    label: 'System',
    icon: <SettingsIcon />,
    items: [
      {
        label: 'System status',
        to: '/admin/system',
        action: 'read',
        subject: 'System',
        icon: <MonitorHeartIcon />,
      },
      { label: 'Audit log', to: '/audit', action: 'read', subject: 'Audit', icon: <HistoryIcon /> },
    ],
  },
];

const EXPANDED_KEY = 'pams.nav.expanded';

function isActivePath(item: NavItem, pathname: string) {
  return item.to === '/'
    ? pathname === '/'
    : pathname === item.to || pathname.startsWith(`${item.to}/`);
}

export function AppLayout() {
  const ability = useAbility();
  const { branding, logoUrl } = useBranding();
  const { mode, toggle } = useThemeMode();
  const location = useLocation();
  const navigate = useNavigate();
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up('md'));
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(() => {
    const saved = localStorage.getItem(EXPANDED_KEY);
    if (saved) {
      try {
        return new Set<string>(JSON.parse(saved) as string[]);
      } catch {
        // ignore malformed storage
      }
    }
    return new Set(['procurement']);
  });

  const unread = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: unreadCount,
    refetchInterval: 60_000,
  });

  const terminology = useMemo(
    () => ({
      '/requests': branding.requestLabel,
      '/vendors': branding.vendorLabel,
      '/purchase-orders': branding.orderLabel,
      '/budgets': branding.budgetLabel,
      '/admin/catalog': branding.catalogLabel,
    }),
    [
      branding.requestLabel,
      branding.vendorLabel,
      branding.orderLabel,
      branding.budgetLabel,
      branding.catalogLabel,
    ],
  );
  const visibleGroups = useMemo(
    () =>
      navGroups
        .map((group) => ({
          ...group,
          items: group.items
            .filter((item) => ability.can(item.action, item.subject))
            .map((item) => ({
              ...item,
              label: terminology[item.to as keyof typeof terminology] ?? item.label,
            })),
        }))
        .filter((group) => group.items.length > 0),
    [ability, terminology],
  );
  const visibleStandalone = standaloneItems.filter((item) =>
    ability.can(item.action, item.subject),
  );
  const allItems = [...visibleStandalone, ...visibleGroups.flatMap((group) => group.items)];

  const activeItem = allItems.find((item) => isActivePath(item, location.pathname));
  const activeGroup = visibleGroups.find((group) =>
    group.items.some((item) => isActivePath(item, location.pathname)),
  );

  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setPaletteOpen(true);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  useEffect(() => {
    if (!activeGroup) return;
    setExpanded((current) => {
      if (current.has(activeGroup.id)) return current;
      const next = new Set(current).add(activeGroup.id);
      localStorage.setItem(EXPANDED_KEY, JSON.stringify([...next]));
      return next;
    });
  }, [activeGroup]);

  const toggleGroup = (id: string) => {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      localStorage.setItem(EXPANDED_KEY, JSON.stringify([...next]));
      return next;
    });
  };

  const activeButtonSx = {
    mx: 1,
    mb: 0.5,
    borderRadius: 2,
    minHeight: 38,
    py: 0.5,
    '&.Mui-selected': {
      bgcolor: 'transparent',
      color: 'primary.main',
      fontWeight: 600,
      '& .MuiListItemIcon-root': { color: 'primary.main' },
      '&:hover': { bgcolor: 'transparent' },
    },
  } as const;

  const renderItem = (item: NavItem, nested: boolean) => {
    const active = isActivePath(item, location.pathname);
    return (
      <ListItemButton
        key={item.to}
        component={RouterLink}
        to={item.to}
        selected={active}
        onClick={() => setDrawerOpen(false)}
        sx={{ ...activeButtonSx, pl: nested ? 7 : 2 }}
      >
        <ListItemIcon sx={{ color: active ? 'primary.main' : 'text.secondary' }}>
          {item.icon}
        </ListItemIcon>
        <ListItemText primary={item.label} primaryTypographyProps={{ noWrap: true }} />
      </ListItemButton>
    );
  };

  const drawer = (
    <Box
      sx={{
        width: 280,
        height: '100%',
        overflowY: 'auto',
        scrollbarWidth: 'thin',
        scrollbarColor: 'rgba(107, 114, 128, 0.45) transparent',
        '&::-webkit-scrollbar': { width: 6 },
        '&::-webkit-scrollbar-track': { backgroundColor: 'transparent', marginY: 1 },
        '&::-webkit-scrollbar-thumb': {
          backgroundColor: 'divider',
          borderRadius: 6,
          border: '1px solid transparent',
          backgroundClip: 'content-box',
        },
        '&::-webkit-scrollbar-thumb:hover': { backgroundColor: 'text.disabled' },
        '& .MuiListItemIcon-root': { minWidth: 34 },
        '& .MuiListItemIcon-root svg': { fontSize: 20 },
        '& .MuiListItemText-root': { my: 0 },
        '& .MuiListItemButton-root': { minHeight: 38, py: 0.5 },
      }}
      role="navigation"
      aria-label="Main navigation"
    >
      <Divider />
      <List sx={{ pt: 0.5 }}>{visibleStandalone.map((item) => renderItem(item, false))}</List>
      {visibleGroups.map((group) => {
        const open = expanded.has(group.id);
        const groupActive = group.items.some((item) => isActivePath(item, location.pathname));
        return (
          <Box key={group.id} sx={{ mb: 0.5 }}>
            <ListItemButton
              onClick={() => toggleGroup(group.id)}
              aria-expanded={open}
              sx={{
                mx: 1,
                mb: 0.5,
                borderRadius: 2,
                minHeight: 38,
                py: 0.5,
                color: groupActive ? 'primary.main' : 'inherit',
              }}
            >
              <ListItemIcon sx={{ color: groupActive ? 'primary.main' : 'text.secondary' }}>
                {group.icon}
              </ListItemIcon>
              <ListItemText
                primary={group.label}
                primaryTypographyProps={{ fontWeight: 600, noWrap: true }}
              />
              {open ? (
                <ExpandMore fontSize="small" data-testid="chevron-down" />
              ) : (
                <ChevronRightIcon fontSize="small" data-testid="chevron-right" />
              )}
            </ListItemButton>
            <Collapse
              in={open}
              timeout={reducedMotion ? 0 : 260}
              easing="cubic-bezier(0.4, 0, 0.2, 1)"
            >
              <List disablePadding>{group.items.map((item) => renderItem(item, true))}</List>
            </Collapse>
          </Box>
        );
      })}
      <Box sx={{ height: 8 }} />
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <ReturnToHandler />
      <AppBar position="fixed" sx={{ zIndex: (mui) => mui.zIndex.drawer + 1 }}>
        <Toolbar>
          {!desktop && (
            <IconButton
              color="inherit"
              aria-label="Open navigation"
              onClick={() => setDrawerOpen(true)}
            >
              <MenuIcon />
            </IconButton>
          )}
          <Stack
            direction="row"
            spacing={1.5}
            alignItems="center"
            sx={{ mr: 2, flexGrow: 1, minWidth: 0 }}
          >
            {logoUrl ? (
              <Box
                sx={{
                  bgcolor: '#FFFFFF',
                  borderRadius: 1,
                  px: 1,
                  py: 0.5,
                  height: 34,
                  display: 'flex',
                  alignItems: 'center',
                  flexShrink: 0,
                }}
              >
                <Box
                  component="img"
                  src={logoUrl}
                  alt={`${branding.displayName} — PAMS`}
                  sx={{ maxHeight: 26, maxWidth: 190, objectFit: 'contain' }}
                />
              </Box>
            ) : (
              <Box
                component="img"
                src="/logo/pams-logo-reverse.svg"
                alt="PAMS — Procurement & Approval Management System"
                sx={{ height: 34, objectFit: 'contain', objectPosition: 'left', flexShrink: 0 }}
              />
            )}
            {branding.displayName !== 'PAMS' && (
              <Typography
                variant="subtitle1"
                noWrap
                sx={{
                  color: '#FFFFFF',
                  fontWeight: 700,
                  maxWidth: { xs: 150, sm: 280, md: 360 },
                }}
              >
                {branding.displayName}
              </Typography>
            )}
          </Stack>
          <Tooltip title="Search (Ctrl/Cmd+K)">
            <Button
              color="inherit"
              onClick={() => setPaletteOpen(true)}
              startIcon={<SearchIcon />}
              sx={{
                display: { xs: 'none', sm: 'flex' },
                border: 1,
                borderColor: 'rgba(255,255,255,0.35)',
                borderRadius: 2,
                mr: 1,
                color: 'inherit',
                textTransform: 'none',
              }}
            >
              Search
              <Chip
                label="⌘K"
                size="small"
                sx={{ ml: 1, height: 20, bgcolor: 'rgba(255,255,255,0.18)', color: 'inherit' }}
              />
            </Button>
          </Tooltip>
          <Tooltip title={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
            <IconButton color="inherit" aria-label="Toggle color mode" onClick={toggle}>
              {mode === 'dark' ? <LightModeIcon /> : <DarkModeIcon />}
            </IconButton>
          </Tooltip>
          <IconButton
            color="inherit"
            aria-label="Notifications"
            component={RouterLink}
            to="/notifications"
          >
            <Badge badgeContent={unread.data?.unread ?? 0} color="secondary">
              <NotificationsIcon />
            </Badge>
          </IconButton>
          <UserMenu />
        </Toolbar>
      </AppBar>

      {desktop ? (
        <Drawer
          variant="permanent"
          sx={{
            width: 280,
            flexShrink: 0,
            '& .MuiDrawer-paper': {
              width: 280,
              top: { xs: 56, md: 64 },
              height: { xs: 'calc(100% - 56px)', md: 'calc(100% - 64px)' },
              borderRight: 1,
              borderColor: 'divider',
            },
          }}
        >
          {drawer}
        </Drawer>
      ) : (
        <Drawer
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          slotProps={{
            paper: {
              sx: {
                width: 280,
                top: { xs: 56, md: 64 },
                height: { xs: 'calc(100% - 56px)', md: 'calc(100% - 64px)' },
              },
            },
          }}
        >
          {drawer}
        </Drawer>
      )}

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          minWidth: 0,
          pt: { xs: '64px', md: '72px' },
          px: { xs: 2, md: 4, lg: 5 },
          pb: { xs: 3, md: 5 },
        }}
      >
        <Box
          sx={{
            position: 'sticky',
            top: { xs: '56px', md: '64px' },
            zIndex: (mui) => mui.zIndex.appBar - 1,
            mx: { xs: -2, md: -4, lg: -5 },
            mt: { xs: -1, md: -1 },
            px: { xs: 2, md: 4, lg: 5 },
            py: 1,
            mb: 2,
            bgcolor: 'background.default',
            borderBottom: 1,
            borderColor: 'divider',
          }}
        >
          <Box sx={{ width: '100%', maxWidth: 1500, mx: 0 }}>
            <Breadcrumbs aria-label="Breadcrumb">
              <Link
                component={RouterLink}
                to="/"
                underline="none"
                color="inherit"
                sx={{ display: 'flex', alignItems: 'center' }}
              >
                <HomeIcon fontSize="small" />
              </Link>
              {activeGroup && <Typography color="text.secondary">{activeGroup.label}</Typography>}
              <Typography color="text.primary">{activeItem?.label ?? 'Details'}</Typography>
            </Breadcrumbs>
          </Box>
        </Box>
        <Box sx={{ width: '100%', maxWidth: 1500, mx: 0 }}>
          <PageTransition>
            <Outlet />
          </PageTransition>
        </Box>
      </Box>

      <CommandPalette
        open={paletteOpen}
        onClose={() => setPaletteOpen(false)}
        items={allItems.map((item) => ({ label: item.label, to: item.to }))}
        onSelect={(to) => {
          setPaletteOpen(false);
          navigate(to);
        }}
      />
    </Box>
  );
}

export function DashboardLink() {
  const navigate = useNavigate();
  return (
    <Button onClick={() => navigate('/requests/new')} variant="contained">
      New request
    </Button>
  );
}
