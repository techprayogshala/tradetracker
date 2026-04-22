import axios from 'axios';
const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
    headers: { 'Content-Type': 'application/json' },
});
apiClient.interceptors.request.use(async (config) => {
    const kc = window._keycloak_;
    if (!kc?.token) {
        return config;
    }
    if (kc.isTokenExpired(30)) {
        try {
            await kc.updateToken(30);
        }
        catch {
        }
    }
    if (kc.token) {
        config.headers.Authorization = `Bearer ${kc.token}`;
    }
    return config;
});
export function initApiAuth(_keycloak) {
}
export default apiClient;
