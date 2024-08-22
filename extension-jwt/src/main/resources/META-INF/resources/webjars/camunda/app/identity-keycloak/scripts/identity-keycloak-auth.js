import "./keycloak.js";

let camundaIdentityKeycloak = undefined;

const portalName = document.querySelector('base').attributes['app-root'].value;

async function fetchKeycloakOptions(optionsUrl) {
    const response = await fetch(optionsUrl);
    const options = await response.json();
    return options;
}

await fetchKeycloakOptions(portalName+"/app/keycloak/keycloak-options.json")
    .then(options => {
        if (options) {
            camundaIdentityKeycloak = new Keycloak(options);
            camundaIdentityKeycloak.onTokenExpired = () => camundaIdentityKeycloak.updateToken(options.minValidity);
            camundaIdentityKeycloak.onAuthRefreshError = () => camundaIdentityKeycloak.login(options)
                .catch(() => console.error('Login failed'));
            return camundaIdentityKeycloak.init({
                onLoad: 'login-required',
                checkLoginIframe: false,
                promiseType: 'native'
            });
        } else {
            return Promise.resolve;
        }
    }).then(() => {
            if (camundaIdentityKeycloak) {
                (function () {
                    const constantMock = window.fetch;
                    window.fetch = function () {
                        if (arguments[0].startsWith("/") || arguments[0].startsWith(window.location.origin)) {
                            if (arguments[0].endsWith('/api/admin/auth/user/default/logout')) {
                                camundaIdentityKeycloak.logout();
                            }
                            var args = Object.assign({}, arguments[1]);
                            if (!args.headers) {
                                args.headers = new Headers();
                            }
                            if (args.headers instanceof Headers) {
                                args.headers.append('Authorization', 'Bearer ' + camundaIdentityKeycloak.token);
                            } else {
                                args.headers['Authorization'] = 'Bearer ' + camundaIdentityKeycloak.token;
                            }
                            return constantMock.apply(this, [arguments[0], args]);
                        } else {
                            return constantMock.apply(this, arguments);
                        }
                    }
                })();

                (function (open) {
                    XMLHttpRequest.prototype.open = function () {
                        open.apply(this, arguments);
                        if (arguments[1].startsWith("/") || arguments[1].startsWith(window.location.origin)) {
                            if (arguments[1].endsWith('/admin/auth/user/default/logout')) {
                                camundaIdentityKeycloak.logout();
                            }
                            this.withCredentials = true;
                            this.setRequestHeader('Authorization', 'Bearer ' + camundaIdentityKeycloak.token);
                        }
                    };
                })(XMLHttpRequest.prototype.open);
            }
        }
    )
