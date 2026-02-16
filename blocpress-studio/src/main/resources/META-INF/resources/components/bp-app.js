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
        try {
            const url = this._workbenchUrl.replace(/\/+$/, '');
            await import(`${url}/components/bp-workbench.js`);
            this._workbenchLoaded = true;
        } catch (err) {
            this._workbenchError = `Workbench konnte nicht geladen werden: ${err.message}`;
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
                    return html`<div style="padding:32px;color:#c62828;">${this._workbenchError}</div>`;
                }
                if (!this._workbenchLoaded) {
                    return html`<div style="padding:32px;color:#666;">Workbench wird geladen...</div>`;
                }
                return html`<bp-workbench .jwt=${this._token}></bp-workbench>`;
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
