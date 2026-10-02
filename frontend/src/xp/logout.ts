export const LOGOUT_PATH = "/oauth2/logout";

export function logout() {
  window.location.assign(LOGOUT_PATH);
}
