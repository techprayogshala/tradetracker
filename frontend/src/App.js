import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useKeycloak } from '@react-keycloak/web';
import { useEffect } from 'react';
import api from './lib/apiClient';
import AppLayout from './components/layout/AppLayout';
import DashboardPage from './pages/DashboardPage';
import PortfolioPage from './pages/PortfolioPage';
import TradesPage from './pages/TradesPage';
import TaxPage from './pages/TaxPage';
import SettingsPage from './pages/SettingsPage';
export default function App() {
    const { keycloak, initialized } = useKeycloak();
    useEffect(() => {
        if (!initialized || !keycloak.authenticated)
            return;
        const provision = async () => {
            try {
                await api.post('/v1/portfolios/provision');
            }
            catch { }
        };
        const timer = setTimeout(provision, 100);
        return () => clearTimeout(timer);
    }, [initialized, keycloak.authenticated]);
    if (!initialized) {
        return (_jsx("div", { className: "flex h-screen items-center justify-center bg-gray-50", children: _jsx("div", { className: "text-gray-400 text-sm", children: "Loading\u2026" }) }));
    }
    return (_jsx(BrowserRouter, { children: _jsx(Routes, { children: _jsxs(Route, { element: _jsx(AppLayout, {}), children: [_jsx(Route, { index: true, element: _jsx(Navigate, { to: "/dashboard", replace: true }) }), _jsx(Route, { path: "/dashboard", element: _jsx(DashboardPage, {}) }), _jsx(Route, { path: "/portfolio/:portfolioId", element: _jsx(PortfolioPage, {}) }), _jsx(Route, { path: "/portfolio/:portfolioId/trades", element: _jsx(TradesPage, {}) }), _jsx(Route, { path: "/portfolio/:portfolioId/tax", element: _jsx(TaxPage, {}) }), _jsx(Route, { path: "/settings", element: _jsx(SettingsPage, {}) })] }) }) }));
}
