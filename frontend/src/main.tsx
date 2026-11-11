import React from 'react';
import ReactDOM from 'react-dom/client';
import { AuthProvider } from 'react-oidc-context';
import App from './App';
import { appConfig } from './config';

const oidcConfig = {
  authority: appConfig.authority,
  client_id: appConfig.clientId,
  redirect_uri: appConfig.redirectUri,
  post_logout_redirect_uri: appConfig.postLogoutRedirectUri,
  response_type: 'code',
  scope: appConfig.scope,
  automaticSilentRenew: true,
  monitorSession: false,
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AuthProvider {...oidcConfig}>
      <App />
    </AuthProvider>
  </React.StrictMode>,
);
