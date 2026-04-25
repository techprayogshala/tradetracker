import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useKeycloak } from '@react-keycloak/web';
import { useEffect, useState } from 'react';
import api from './lib/apiClient';
import AppLayout from './components/layout/AppLayout';
import DashboardPage from './pages/DashboardPage';
import PortfolioPage from './pages/PortfolioPage';
import TradesPage from './pages/TradesPage';
import TaxPage from './pages/TaxPage';
import SettingsPage from './pages/SettingsPage';
export default function App() {
    const { keycloak, initialized } = useKeycloak();
    const [authReady, setAuthReady] = useState(false);
    // Set global auth flag and local state only after token is available
    useEffect(() => {
        if (initialized && keycloak.authenticated) {
            window._authReady = true;
            setTimeout(() => setAuthReady(true), 100);
        }
    }, [initialized, keycloak.authenticated]);
    // Provision user on first login
    useEffect(() => {
        if (!authReady)
            return;
        const kc = window._keycloak;
        console.log('provision called, kc.token:', kc?.token ? 'EXISTS' : 'MISSING', 'authenticated:', kc?.authenticated);
        if (!kc?.token) {
            console.log('NO TOKEN - not calling provision');
            return;
        }
        api.post('/v1/portfolios/provision').catch(() => { });
    }, [authReady]);
    // Wait for BOTH initialized AND authenticated before rendering app
    if (!authReady) {
        return (_jsx("div", { className: "flex h-screen items-center justify-center bg-gray-50", children: _jsx("div", { className: "text-gray-400 text-sm", children: "Authenticating..." }) }));
    }
    return (_jsx(BrowserRouter, { children: _jsx(Routes, { children: _jsxs(Route, { element: _jsx(AppLayout, {}), children: [_jsx(Route, { index: true, element: _jsx(Navigate, { to: "/dashboard", replace: true }) }), _jsx(Route, { path: "/dashboard", element: _jsx(DashboardPage, {}) }), _jsx(Route, { path: "/portfolio/:portfolioId", element: _jsx(PortfolioPage, {}) }), _jsx(Route, { path: "/portfolio/:portfolioId/trades", element: _jsx(TradesPage, {}) }), _jsx(Route, { path: "/portfolio/:portfolioId/tax", element: _jsx(TaxPage, {}) }), _jsx(Route, { path: "/settings", element: _jsx(SettingsPage, {}) })] }) }) }));
}
