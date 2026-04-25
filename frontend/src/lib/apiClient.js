import axios from 'axios';
let tokenRefreshPromise = null;
const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
    headers: { 'Content-Type': 'application/json' },
});
apiClient.defaults.paramsSerializer = (params) => {
    return new URLSearchParams(params).toString();
};
apiClient.interceptors.request.use(async (config) => {
    const kc = window._keycloak;
    if (!kc?.token) {
        return config;
    }
    if (kc.isTokenExpired(30)) {
        if (!tokenRefreshPromise) {
            tokenRefreshPromise = kc.updateToken(30).finally(() => {
                tokenRefreshPromise = null;
            });
        }
        await tokenRefreshPromise;
    }
    if (kc.token) {
        config.headers.Authorization = `Bearer ${kc.token}`;
    }
    return config;
});
export function initApiAuth(_keycloak) {
}
export default apiClient;
