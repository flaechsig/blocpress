import { LitElement, html, css } from 'lit';

export class BpNav extends LitElement {
    static properties = {
        route: { type: String }
    };

    static styles = css`
        :host {
            display: block;
        }
        nav {
            display: flex;
            flex-direction: column;
            gap: 2px;
            padding: 8px;
        }
        a {
            display: block;
            padding: 10px 16px;
            border-radius: 6px;
            text-decoration: none;
            color: #e0e0e0;
            font-size: 14px;
            cursor: pointer;
            transition: background 0.15s;
        }
        a:hover:not(.disabled) { background: rgba(255, 255, 255, 0.1); }
        a.active {
            background: rgba(255, 255, 255, 0.15);
            color: #fff;
            font-weight: 600;
        }
        a.disabled {
            color: rgba(255, 255, 255, 0.35);
            cursor: default;
        }
    `;

    constructor() {
        super();
        this.route = 'workbench';
    }

    render() {
        return html`
            <nav>
                <a class=${this.route === 'workbench' ? 'active' : ''}
                   @click=${() => this._navigate('workbench')}>Workbench</a>
                <a class="disabled">Proof</a>
                <a class="disabled">Admin</a>
            </nav>
        `;
    }

    _navigate(route) {
        this.dispatchEvent(new CustomEvent('navigate', {
            detail: { route }, bubbles: true, composed: true
        }));
    }
}

customElements.define('bp-nav', BpNav);
