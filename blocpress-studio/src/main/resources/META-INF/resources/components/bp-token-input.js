import { LitElement, html, css } from 'lit';

const STORAGE_KEY = 'bp-jwt';

export class BpTokenInput extends LitElement {
    static properties = {
        _token: { state: true },
        _draft: { state: true }
    };

    static styles = css`
        :host {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        textarea {
            width: 260px;
            height: 32px;
            font-size: 12px;
            font-family: monospace;
            resize: none;
            border: 1px solid rgba(255, 255, 255, 0.3);
            border-radius: 4px;
            padding: 6px;
            background: rgba(255, 255, 255, 0.1);
            color: #fff;
        }
        textarea::placeholder { color: rgba(255, 255, 255, 0.5); }
        button {
            padding: 6px 12px;
            border: 1px solid rgba(255, 255, 255, 0.3);
            border-radius: 4px;
            background: rgba(255, 255, 255, 0.1);
            color: #fff;
            cursor: pointer;
            font-size: 12px;
            white-space: nowrap;
        }
        button:hover { background: rgba(255, 255, 255, 0.2); }
        .token-preview {
            font-family: monospace;
            font-size: 12px;
            color: rgba(255, 255, 255, 0.8);
        }
    `;

    constructor() {
        super();
        this._token = localStorage.getItem(STORAGE_KEY) || '';
        this._draft = '';
    }

    render() {
        if (this._token) {
            return html`
                <span class="token-preview">${this._token.substring(0, 20)}...</span>
                <button @click=${this._clear}>Clear</button>
            `;
        }
        return html`
            <textarea
                placeholder="Paste JWT token..."
                .value=${this._draft}
                @input=${e => this._draft = e.target.value}
            ></textarea>
            <button @click=${this._setToken}>Set Token</button>
        `;
    }

    _setToken() {
        const value = this._draft.trim();
        if (!value) return;
        localStorage.setItem(STORAGE_KEY, value);
        this._token = value;
        this._draft = '';
        this.dispatchEvent(new CustomEvent('token-changed', {
            detail: { token: value }, bubbles: true, composed: true
        }));
    }

    _clear() {
        localStorage.removeItem(STORAGE_KEY);
        this._token = '';
        this.dispatchEvent(new CustomEvent('token-changed', {
            detail: { token: '' }, bubbles: true, composed: true
        }));
    }
}

customElements.define('bp-token-input', BpTokenInput);
