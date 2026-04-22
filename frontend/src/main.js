import { jsx as _jsx } from "react/jsx-runtime";
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactKeycloakProvider } from '@react-keycloak/web';
import Keycloak from 'keycloak-js';
import App from './App';
import './index.css';
const keycloak = window._keycloak ?? new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081',
    realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'tradetracker',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'tradetracker-frontend',
});
window._keycloak = keycloak;
if (!window._keycloak_) {
    window._keycloak_ = keycloak;
}
// ── React Query client ────────────────────────────────────────────────────────
const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 30000,
            retry: 1,
            refetchOnWindowFocus: false,
        },
    },
});
ReactDOM.createRoot(document.getElementById('root')).render(_jsx(ReactKeycloakProvider, { authClient: keycloak, initOptions: { onLoad: 'login-required', pkceMethod: 'S256' }, LoadingComponent: _jsx("div", { className: "flex h-screen items-center justify-center text-gray-500", children: "Authenticating\u2026" }), children: _jsx(QueryClientProvider, { client: queryClient, children: _jsx(App, {}) }) }));
