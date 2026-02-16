import { LitElement, html, css } from 'lit';

const RENDER_URL_KEY = 'bp-render-url';
const DEFAULT_RENDER_URL = 'http://localhost:8080';

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
        _renderUrl: { state: true },
        _templateFile: { state: true },
        _templateBase64: { state: true },
        _jsonText: { state: true },
        _jsonValid: { state: true },
        _outputType: { state: true },
        _loading: { state: true },
        _error: { state: true }
    };

    static styles = css`
        :host {
            display: block;
            padding: 32px;
        }
        h2 {
            margin: 0 0 24px;
            font-size: 24px;
            font-weight: 600;
            color: #1e3c72;
        }

        /* Render URL bar */
        .url-bar {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-bottom: 24px;
            padding: 12px 16px;
            background: #fff;
            border: 1px solid #ddd;
            border-radius: 8px;
        }
        .url-bar label {
            font-size: 13px;
            font-weight: 600;
            color: #555;
            white-space: nowrap;
        }
        .url-bar input {
            flex: 1;
            padding: 6px 10px;
            border: 1px solid #ccc;
            border-radius: 4px;
            font-size: 13px;
            font-family: monospace;
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

        /* Template upload */
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
        .actions label {
            font-size: 13px;
            font-weight: 600;
            color: #555;
        }
        .actions select {
            padding: 6px 10px;
            border: 1px solid #ccc;
            border-radius: 4px;
            font-size: 13px;
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

        /* Error display */
        .error {
            margin-top: 16px;
            padding: 12px 16px;
            background: #fff5f5;
            border: 1px solid #e57373;
            border-radius: 8px;
            color: #c62828;
            font-size: 13px;
        }
    `;

    constructor() {
        super();
        this.jwt = '';
        this._renderUrl = localStorage.getItem(RENDER_URL_KEY) || DEFAULT_RENDER_URL;
        this._templateFile = null;
        this._templateBase64 = '';
        this._jsonText = SAMPLE_JSON;
        this._jsonValid = true;
        this._outputType = 'pdf';
        this._loading = false;
        this._error = '';
    }

    render() {
        return html`
            <h2>Workbench</h2>

            <div class="url-bar">
                <label>Render API:</label>
                <input type="text"
                    .value=${this._renderUrl}
                    @input=${this._onRenderUrlChange}
                    placeholder="${DEFAULT_RENDER_URL}">
            </div>

            <div class="columns">
                <div class="panel">
                    <h3>Template (.odt)</h3>
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
                <label>Output:</label>
                <select @change=${e => this._outputType = e.target.value}>
                    <option value="pdf" ?selected=${this._outputType === 'pdf'}>PDF</option>
                    <option value="rtf" ?selected=${this._outputType === 'rtf'}>RTF</option>
                    <option value="odt" ?selected=${this._outputType === 'odt'}>ODT</option>
                </select>
                <button
                    ?disabled=${!this._canRender()}
                    @click=${this._onRender}>
                    Render
                </button>
                ${this._loading ? html`<span class="spinner"></span>` : ''}
            </div>

            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
        `;
    }

    _onRenderUrlChange(e) {
        this._renderUrl = e.target.value;
        localStorage.setItem(RENDER_URL_KEY, this._renderUrl);
    }

    _onFileChange(e) {
        const file = e.target.files[0];
        if (!file) return;
        this._templateFile = file;
        this._error = '';
        const reader = new FileReader();
        reader.onload = () => {
            this._templateBase64 = reader.result.split(',')[1];
        };
        reader.readAsDataURL(file);
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

    _canRender() {
        return this._templateBase64 && this._jsonValid && this.jwt && !this._loading;
    }

    async _onRender() {
        this._loading = true;
        this._error = '';

        try {
            const data = JSON.parse(this._jsonText);
            const url = this._renderUrl.replace(/\/+$/, '');

            const response = await fetch(`${url}/api/render/template`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.jwt}`
                },
                body: JSON.stringify({
                    template: this._templateBase64,
                    data,
                    outputType: this._outputType
                })
            });

            if (!response.ok) {
                const text = await response.text();
                throw new Error(`${response.status} ${response.statusText}: ${text}`);
            }

            const blob = await response.blob();
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = `document.${this._outputType}`;
            a.click();
            URL.revokeObjectURL(a.href);
        } catch (err) {
            this._error = err.message;
        } finally {
            this._loading = false;
        }
    }
}

customElements.define('bp-workbench', BpWorkbench);
