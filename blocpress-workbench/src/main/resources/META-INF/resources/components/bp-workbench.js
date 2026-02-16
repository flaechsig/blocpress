import { LitElement, html, css } from 'lit';

const SAMPLE_JSON = JSON.stringify({
    firstname: 'John',
    lastname: 'Doe',
    street: '123 Main St',
    postcode: '12345',
    city: 'Anytown'
}, null, 2);

export class BpWorkbench extends LitElement {
    static properties = {
        jwt: { type: String },
        apiBaseUrl: { type: String, attribute: 'api-base-url' },
        _templateName: { state: true },
        _templateFile: { state: true },
        _jsonText: { state: true },
        _jsonValid: { state: true },
        _loading: { state: true },
        _error: { state: true },
        _success: { state: true }
    };

    static styles = css`
        :host {
            display: block;
            padding: 32px;
        }

        /* Two-column layout */
        .columns {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 24px;
            margin-bottom: 24px;
        }
        .panel {
            background: #fff;
            border: 1px solid #ddd;
            border-radius: 8px;
            padding: 20px;
        }
        .panel h3 {
            margin: 0 0 12px;
            font-size: 15px;
            font-weight: 600;
            color: #333;
        }

        /* Form inputs */
        input[type="text"] {
            width: 100%;
            padding: 8px 10px;
            border: 1px solid #ccc;
            border-radius: 4px;
            font-size: 13px;
            box-sizing: border-box;
            margin-bottom: 12px;
        }
        input[type="file"] {
            font-size: 13px;
        }
        .filename {
            margin-top: 8px;
            font-size: 13px;
            color: #2e7d32;
            font-weight: 500;
        }

        /* JSON editor */
        textarea {
            width: 100%;
            height: 200px;
            font-family: monospace;
            font-size: 13px;
            border: 1px solid #ccc;
            border-radius: 4px;
            padding: 10px;
            resize: vertical;
            box-sizing: border-box;
        }
        textarea.invalid {
            border-color: #c62828;
            background: #fff5f5;
        }
        .json-error {
            margin-top: 6px;
            font-size: 12px;
            color: #c62828;
        }

        /* Action bar */
        .actions {
            display: flex;
            align-items: center;
            gap: 16px;
            padding: 16px 20px;
            background: #fff;
            border: 1px solid #ddd;
            border-radius: 8px;
        }
        .actions button {
            padding: 8px 24px;
            border: none;
            border-radius: 4px;
            background: #1e3c72;
            color: #fff;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
        }
        .actions button:hover:not(:disabled) {
            background: #2a5298;
        }
        .actions button:disabled {
            background: #aaa;
            cursor: not-allowed;
        }
        .spinner {
            display: inline-block;
            width: 18px;
            height: 18px;
            border: 2px solid #ccc;
            border-top-color: #1e3c72;
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
        }
        @keyframes spin {
            to { transform: rotate(360deg); }
        }

        /* Feedback */
        .error {
            margin-top: 16px;
            padding: 12px 16px;
            background: #fff5f5;
            border: 1px solid #e57373;
            border-radius: 8px;
            color: #c62828;
            font-size: 13px;
        }
        .success {
            margin-top: 16px;
            padding: 12px 16px;
            background: #f1f8e9;
            border: 1px solid #aed581;
            border-radius: 8px;
            color: #33691e;
            font-size: 13px;
        }
    `;

    constructor() {
        super();
        this.jwt = '';
        this.apiBaseUrl = '';
        this._templateName = '';
        this._templateFile = null;
        this._jsonText = SAMPLE_JSON;
        this._jsonValid = true;
        this._loading = false;
        this._error = '';
        this._success = '';
    }

    render() {
        return html`
            <div class="columns">
                <div class="panel">
                    <h3>Template (.odt)</h3>
                    <input type="text"
                        placeholder="Template-Name (eindeutig)"
                        .value=${this._templateName}
                        @input=${e => this._templateName = e.target.value}>
                    <input type="file" accept=".odt" @change=${this._onFileChange}>
                    ${this._templateFile
                        ? html`<div class="filename">${this._templateFile.name}</div>`
                        : ''}
                </div>

                <div class="panel">
                    <h3>JSON Data</h3>
                    <textarea
                        class=${this._jsonValid ? '' : 'invalid'}
                        .value=${this._jsonText}
                        @input=${this._onJsonInput}
                    ></textarea>
                    ${this._jsonValid ? '' : html`<div class="json-error">Invalid JSON</div>`}
                </div>
            </div>

            <div class="actions">
                <button
                    ?disabled=${!this._canUpload()}
                    @click=${this._onUpload}>
                    Upload
                </button>
                ${this._loading ? html`<span class="spinner"></span>` : ''}
            </div>

            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
            ${this._success ? html`<div class="success">${this._success}</div>` : ''}
        `;
    }

    _getApiBase() {
        if (this.apiBaseUrl) return this.apiBaseUrl.replace(/\/+$/, '');
        // Default: same origin as the component was loaded from
        const src = new URL(import.meta.url);
        return src.origin;
    }

    _onFileChange(e) {
        const file = e.target.files[0];
        if (!file) return;
        this._templateFile = file;
        this._error = '';
        this._success = '';
    }

    _onJsonInput(e) {
        this._jsonText = e.target.value;
        try {
            JSON.parse(this._jsonText);
            this._jsonValid = true;
        } catch {
            this._jsonValid = false;
        }
    }

    _canUpload() {
        return this._templateName.trim() && this._templateFile && !this._loading;
    }

    async _onUpload() {
        this._loading = true;
        this._error = '';
        this._success = '';

        try {
            const formData = new FormData();
            formData.append('name', this._templateName.trim());
            formData.append('file', this._templateFile);

            const response = await fetch(`${this._getApiBase()}/api/templates`, {
                method: 'POST',
                body: formData
            });

            if (response.status === 409) {
                throw new Error(`Template '${this._templateName}' existiert bereits.`);
            }
            if (!response.ok) {
                const text = await response.text();
                throw new Error(`${response.status}: ${text}`);
            }

            const result = await response.json();
            this._success = `Template gespeichert (ID: ${result.id})`;
            this._templateName = '';
            this._templateFile = null;
        } catch (err) {
            this._error = err.message;
        } finally {
            this._loading = false;
        }
    }
}

customElements.define('bp-workbench', BpWorkbench);
