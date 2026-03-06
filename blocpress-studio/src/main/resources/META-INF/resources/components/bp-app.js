import { LitElement, html, css } from 'lit';
import { getCurrentRoute, navigateTo } from './bp-router.js';
import './bp-nav.js';
import './bp-token-input.js';

const WORKBENCH_URL_KEY = 'bp-workbench-url';
const DEFAULT_WORKBENCH_URL = 'http://localhost:8081';

export class BpApp extends LitElement {
    static properties = {
        _token: { state: true },
        _route: { state: true },
        _workbenchLoaded: { state: true },
        _workbenchError: { state: true },
        _workbenchUrl: { state: true }
    };

    static styles = css`
        :host {
            display: grid;
            grid-template-rows: 56px 1fr;
            grid-template-columns: 220px 1fr;
            height: 100vh;
        }
        header {
            grid-column: 1 / -1;
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0 20px;
            background: linear-gradient(135deg, #1e3c72, #2a5298);
            color: #fff;
        }
        .logo {
            height: 36px;
        }
        aside {
            background: #1a1a2e;
            overflow-y: auto;
        }
        main {
            background: #f5f5f5;
            overflow-y: auto;
        }
        .load-error {
            padding: 32px;
            color: #c62828;
        }
        .load-error p {
            margin: 0 0 12px;
            font-family: monospace;
            font-size: 13px;
        }
        .load-error button {
            padding: 8px 20px;
            background: #1e3c72;
            color: #fff;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
        }
        .load-error button:hover {
            background: #2a5298;
        }
    `;

    constructor() {
        super();
        this._token = localStorage.getItem('bp-jwt') || '';
        this._route = getCurrentRoute();
        this._workbenchLoaded = false;
        this._workbenchError = '';
        this._workbenchUrl = localStorage.getItem(WORKBENCH_URL_KEY) || DEFAULT_WORKBENCH_URL;
        this._onHashChange = () => { this._route = getCurrentRoute(); };
    }

    connectedCallback() {
        super.connectedCallback();
        window.addEventListener('hashchange', this._onHashChange);
        this._loadWorkbench();
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        window.removeEventListener('hashchange', this._onHashChange);
    }

    async _loadWorkbench() {
        this._workbenchError = '';
        this._workbenchLoaded = false;
        // Retry a few times: handles the brief window after a Quarkus hot-reload
        // where the proxy returns an error module. Each attempt uses a fresh ?v=
        // URL so the browser never serves a cached failed module.
        for (let attempt = 1; attempt <= 3; attempt++) {
            try {
                await import(`/proxy/bp-workbench.js?v=${Date.now()}`);
                this._workbenchLoaded = true;
                return;
            } catch (err) {
                if (attempt < 3) {
                    await new Promise(r => setTimeout(r, 2000));
                } else {
                    this._workbenchError = err.message;
                }
            }
        }
    }

    render() {
        return html`
            <header>
                <img class="logo" src="/logo.svg" alt="blocpress - studio">
                <bp-token-input @token-changed=${this._onTokenChanged}></bp-token-input>
            </header>
            <aside>
                <bp-nav .route=${this._route} @navigate=${this._onNavigate}></bp-nav>
            </aside>
            <main>
                ${this._renderContent()}
            </main>
        `;
    }

    _renderContent() {
        switch (this._route) {
            case 'workbench':
                if (this._workbenchError) {
                    return html`
                        <div class="load-error">
                            <p>Workbench konnte nicht geladen werden:</p>
                            <p>${this._workbenchError}</p>
                            <button @click=${() => this._loadWorkbench()}>
                                Neu verbinden
                            </button>
                        </div>`;
                }
                if (!this._workbenchLoaded) {
                    return html`<div style="padding:32px;color:#666;">Workbench wird geladen...</div>`;
                }
                return html`<bp-workbench
                    .jwt=${this._token}
                    api-base-url=${this._workbenchUrl}></bp-workbench>`;
            default:
                return html`<div style="padding:32px;color:#666;">Coming soon</div>`;
        }
    }

    _onTokenChanged(e) {
        this._token = e.detail.token;
    }

    _onNavigate(e) {
        navigateTo(e.detail.route);
    }
}

customElements.define('bp-app', BpApp);
