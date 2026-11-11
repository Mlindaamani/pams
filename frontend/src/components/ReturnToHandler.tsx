import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';

const RETURN_KEY = 'pams.returnTo';

export function rememberReturnPath(path: string) {
  if (path && path !== '/login') {
    sessionStorage.setItem(RETURN_KEY, path);
  }
}

export function ReturnToHandler() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (auth.isLoading || !auth.isAuthenticated) return;
    const saved = sessionStorage.getItem(RETURN_KEY);
    if (!saved) return;
    sessionStorage.removeItem(RETURN_KEY);
    const current = `${location.pathname}${location.search}`;
    if (saved !== current) {
      navigate(saved, { replace: true });
    }
  }, [auth.isAuthenticated, auth.isLoading, location.pathname, location.search, navigate]);

  return null;
}
