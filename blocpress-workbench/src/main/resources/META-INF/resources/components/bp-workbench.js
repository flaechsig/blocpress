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
        apiBaseUrl: { type: String, attribute: 'api-base-url' },
        _templates: { state: true },
        _searchText: { state: true },
        _showSuggestions: { state: true },
        _selectedTemplate: { state: true },
        _uploadMode: { state: true },
        _uploadName: { state: true },
        _uploadFile: { state: true },
        _jsonText: { state: true },
        _jsonValid: { state: true },
        _pdfUrl: { state: true },
        _rendering: { state: true },
        _uploading: { state: true },
        _error: { state: true },
        _success: { state: true }
    };

    static styles = css`
        :host {
            display: block;
            padding: 32px;
        }

        /* Row 1: Template bar */
        .template-bar {
            display: flex;
            align-items: flex-start;
            gap: 8px;
            margin-bottom: 24px;
        }
        .template-bar .autocomplete {
            flex: 1;
        }

        /* Autocomplete */
        .autocomplete {
            position: relative;
        }
        .autocomplete input {
            width: 100%;
            padding: 8px 10px;
            border: 1px solid #ccc;
            border-radius: 4px;
            font-size: 13px;
            box-sizing: border-box;
        }
        .autocomplete input:focus {
            border-color: #1e3c72;
            outline: none;
        }
        .suggestions {
            position: absolute;
            top: 100%;
            left: 0;
            right: 0;
            max-height: 200px;
            overflow-y: auto;
            background: #fff;
            border: 1px solid #ccc;
            border-top: none;
            border-radius: 0 0 4px 4px;
            box-shadow: 0 4px 8px rgba(0,0,0,0.1);
            z-index: 10;
        }
        .suggestion {
            padding: 8px 10px;
            font-size: 13px;
            cursor: pointer;
        }
        .suggestion:hover {
            background: #eef;
        }
        .suggestion .hint {
            font-size: 11px;
            color: #888;
            margin-left: 8px;
        }
        .selected-template {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 8px 10px;
            background: #eef;
            border: 1px solid #1e3c72;
            border-radius: 4px;
            font-size: 13px;
            color: #1e3c72;
            font-weight: 500;
        }
        .selected-template .clear-btn {
            margin-left: auto;
            background: none;
            border: none;
            color: #888;
            cursor: pointer;
            font-size: 16px;
            padding: 0 4px;
            line-height: 1;
        }
        .selected-template .clear-btn:hover {
            color: #c62828;
        }

        /* Upload button (small icon) */
        .icon-btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 34px;
            height: 34px;
            border: 1px solid #ccc;
            border-radius: 4px;
            background: #f9f9f9;
            cursor: pointer;
            color: #555;
            font-size: 16px;
            flex-shrink: 0;
            transition: background 0.15s;
        }
        .icon-btn:hover {
            background: #eee;
            border-color: #1e3c72;
            color: #1e3c72;
        }

        /* Upload form (dropdown under bar) */
        .upload-form {
            margin-bottom: 24px;
            padding: 14px;
            background: #fafafa;
            border: 1px solid #e0e0e0;
            border-radius: 6px;
        }
        .upload-form .fields {
            display: flex;
            align-items: flex-end;
            gap: 12px;
            flex-wrap: wrap;
        }
        .upload-form .field {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }
        .upload-form .field-name { flex: 1; }
        .upload-form label {
            font-size: 12px;
            font-weight: 600;
            color: #555;
        }
        .upload-form input[type="text"] {
            padding: 6px 8px;
            border: 1px solid #ccc;
            border-radius: 4px;
            font-size: 13px;
            box-sizing: border-box;
        }
        .upload-form input[type="file"] {
            font-size: 12px;
        }
        .btn-save {
            padding: 6px 16px;
            border: none;
            border-radius: 4px;
            background: #1e3c72;
            color: #fff;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
            white-space: nowrap;
        }
        .btn-save:hover:not(:disabled) {
            background: #2a5298;
        }
        .btn-save:disabled {
            background: #aaa;
            cursor: not-allowed;
        }
        .btn-cancel {
            padding: 6px 16px;
            border: 1px solid #ccc;
            border-radius: 4px;
            background: #fff;
            color: #555;
            font-size: 12px;
            cursor: pointer;
            white-space: nowrap;
        }
        .btn-cancel:hover {
            background: #f5f5f5;
        }

        /* Row 2: Preview + Editor */
        .workspace {
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
            display: flex;
            flex-direction: column;
        }
        .panel-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 12px;
        }
        .panel-header h3 {
            margin: 0;
            font-size: 15px;
            font-weight: 600;
            color: #333;
        }

        /* PDF preview */
        .pdf-frame {
            flex: 1;
            min-height: 600px;
            border: 1px solid #e0e0e0;
            border-radius: 4px;
            background: #f5f5f5;
        }
        .pdf-placeholder {
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 600px;
            color: #999;
            font-size: 13px;
            border: 1px dashed #ddd;
            border-radius: 4px;
        }

        /* Refresh button */
        .refresh-btn {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 5px 12px;
            border: 1px solid #ccc;
            border-radius: 4px;
            background: #fff;
            color: #555;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
        }
        .refresh-btn:hover:not(:disabled) {
            background: #f5f5f5;
            border-color: #1e3c72;
            color: #1e3c72;
        }
        .refresh-btn:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }

        /* JSON editor */
        textarea {
            flex: 1;
            min-height: 560px;
            width: 100%;
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

        /* Spinner */
        .spinner {
            display: inline-block;
            width: 14px;
            height: 14px;
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
        this._templates = [];
        this._searchText = '';
        this._showSuggestions = false;
        this._selectedTemplate = null;
        this._uploadMode = false;
        this._uploadName = '';
        this._uploadFile = null;
        this._jsonText = SAMPLE_JSON;
        this._jsonValid = true;
        this._pdfUrl = null;
        this._rendering = false;
        this._uploading = false;
        this._error = '';
        this._success = '';
    }

    connectedCallback() {
        super.connectedCallback();
        this._loadTemplates();
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        if (this._pdfUrl) URL.revokeObjectURL(this._pdfUrl);
    }

    render() {
        return html`
            <!-- Row 1: Template selection -->
            <div class="template-bar">
                ${this._selectedTemplate
                    ? this._renderSelectedTemplate()
                    : this._renderAutocomplete()}
                <button class="icon-btn"
                    title="Neues Template hochladen"
                    @click=${this._toggleUploadMode}>&#8679;</button>
            </div>

            ${this._uploadMode ? this._renderUploadForm() : ''}

            <!-- Row 2: Preview + JSON (only when template selected) -->
            ${this._selectedTemplate ? html`
                <div class="workspace">
                    <div class="panel">
                        <div class="panel-header">
                            <h3>Vorschau</h3>
                            ${this._rendering ? html`<span class="spinner"></span>` : ''}
                        </div>
                        ${this._pdfUrl
                            ? html`<iframe class="pdf-frame" src=${this._pdfUrl}></iframe>`
                            : html`<div class="pdf-placeholder">
                                ${this._rendering ? 'Wird gerendert...' : 'Noch keine Vorschau'}
                              </div>`}
                    </div>

                    <div class="panel">
                        <div class="panel-header">
                            <h3>JSON Data</h3>
                            <button class="refresh-btn"
                                ?disabled=${!this._jsonValid || this._rendering}
                                @click=${this._renderPdf}>
                                &#8635; Aktualisieren
                            </button>
                        </div>
                        <textarea
                            class=${this._jsonValid ? '' : 'invalid'}
                            .value=${this._jsonText}
                            @input=${this._onJsonInput}
                        ></textarea>
                        ${this._jsonValid ? '' : html`<div class="json-error">Invalid JSON</div>`}
                    </div>
                </div>
            ` : ''}

            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
            ${this._success ? html`<div class="success">${this._success}</div>` : ''}
        `;
    }

    _renderAutocomplete() {
        const filtered = this._templates.filter(t =>
            t.name.toLowerCase().includes(this._searchText.toLowerCase())
        );
        return html`
            <div class="autocomplete">
                <input type="text"
                    placeholder="Template"
                    .value=${this._searchText}
                    @input=${this._onSearchInput}
                    @focus=${() => this._showSuggestions = true}
                    @blur=${() => setTimeout(() => this._showSuggestions = false, 200)}>
                ${this._showSuggestions && this._searchText && filtered.length > 0
                    ? html`
                        <div class="suggestions">
                            ${filtered.map(t => html`
                                <div class="suggestion" @mousedown=${() => this._selectTemplate(t)}>
                                    ${t.name}
                                    <span class="hint">${t.id.substring(0, 8)}</span>
                                </div>
                            `)}
                        </div>`
                    : ''}
            </div>
        `;
    }

    _renderSelectedTemplate() {
        return html`
            <div class="autocomplete">
                <div class="selected-template">
                    ${this._selectedTemplate.name}
                    <button class="clear-btn" title="Auswahl aufheben"
                        @click=${this._clearSelection}>&times;</button>
                </div>
            </div>
        `;
    }

    _renderUploadForm() {
        return html`
            <div class="upload-form">
                <div class="fields">
                    <div class="field field-name">
                        <label>Name</label>
                        <input type="text"
                            placeholder="Eindeutiger Template-Name"
                            .value=${this._uploadName}
                            @input=${e => this._uploadName = e.target.value}>
                    </div>
                    <div class="field">
                        <label>Datei (.odt)</label>
                        <input type="file" accept=".odt" @change=${this._onUploadFileChange}>
                    </div>
                    <button class="btn-save"
                        ?disabled=${!this._canSave()}
                        @click=${this._onSave}>
                        Speichern
                    </button>
                    <button class="btn-cancel" @click=${this._toggleUploadMode}>Abbrechen</button>
                    ${this._uploading ? html`<span class="spinner"></span>` : ''}
                </div>
            </div>
        `;
    }

    // --- API helpers ---

    _getApiBase() {
        if (this.apiBaseUrl) return this.apiBaseUrl.replace(/\/+$/, '');
        const src = new URL(import.meta.url);
        return src.origin;
    }

    _getRenderUrl() {
        return (localStorage.getItem(RENDER_URL_KEY) || DEFAULT_RENDER_URL).replace(/\/+$/, '');
    }

    // --- Template list ---

    async _loadTemplates() {
        try {
            const response = await fetch(`${this._getApiBase()}/api/templates`);
            if (response.ok) {
                this._templates = await response.json();
            }
        } catch {
            // ignore
        }
    }

    // --- Autocomplete ---

    _onSearchInput(e) {
        this._searchText = e.target.value;
        this._showSuggestions = true;
    }

    _selectTemplate(template) {
        this._selectedTemplate = template;
        this._searchText = '';
        this._showSuggestions = false;
        this._error = '';
        this._success = '';
        this._renderPdf();
    }

    _clearSelection() {
        this._selectedTemplate = null;
        if (this._pdfUrl) {
            URL.revokeObjectURL(this._pdfUrl);
            this._pdfUrl = null;
        }
    }

    // --- Upload ---

    _toggleUploadMode() {
        this._uploadMode = !this._uploadMode;
        this._uploadName = '';
        this._uploadFile = null;
        this._error = '';
        this._success = '';
    }

    _onUploadFileChange(e) {
        const file = e.target.files[0];
        if (file) this._uploadFile = file;
    }

    _canSave() {
        return this._uploadName.trim() && this._uploadFile && !this._uploading;
    }

    async _onSave() {
        this._uploading = true;
        this._error = '';
        this._success = '';

        try {
            const formData = new FormData();
            formData.append('name', this._uploadName.trim());
            formData.append('file', this._uploadFile);

            const response = await fetch(`${this._getApiBase()}/api/templates`, {
                method: 'POST',
                body: formData
            });

            if (response.status === 409) {
                throw new Error(`Template '${this._uploadName}' existiert bereits.`);
            }
            if (!response.ok) {
                const text = await response.text();
                throw new Error(`${response.status}: ${text}`);
            }

            const result = await response.json();
            this._success = `Template '${result.name}' gespeichert`;
            this._uploadName = '';
            this._uploadFile = null;
            this._uploadMode = false;

            await this._loadTemplates();
            const created = this._templates.find(t => t.id === result.id);
            if (created) this._selectTemplate(created);
        } catch (err) {
            this._error = err.message;
        } finally {
            this._uploading = false;
        }
    }

    // --- JSON ---

    _onJsonInput(e) {
        this._jsonText = e.target.value;
        try {
            JSON.parse(this._jsonText);
            this._jsonValid = true;
        } catch {
            this._jsonValid = false;
        }
    }

    // --- PDF rendering ---

    async _renderPdf() {
        if (!this._selectedTemplate || !this._jsonValid) return;

        this._rendering = true;
        this._error = '';

        try {
            // 1. Fetch template binary from workbench API
            const templateResponse = await fetch(
                `${this._getApiBase()}/api/templates/${this._selectedTemplate.id}`
            );
            if (!templateResponse.ok) throw new Error('Template konnte nicht geladen werden.');

            const blob = await templateResponse.blob();
            const base64 = await this._blobToBase64(blob);

            // 2. Send to render API
            const data = JSON.parse(this._jsonText);
            const renderResponse = await fetch(`${this._getRenderUrl()}/api/render/template`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    ...(this.jwt ? { 'Authorization': `Bearer ${this.jwt}` } : {})
                },
                body: JSON.stringify({
                    template: base64,
                    data,
                    outputType: 'pdf'
                })
            });

            if (!renderResponse.ok) {
                const text = await renderResponse.text();
                throw new Error(`Render fehlgeschlagen: ${renderResponse.status} ${text}`);
            }

            // 3. Display PDF
            const pdfBlob = await renderResponse.blob();
            if (this._pdfUrl) URL.revokeObjectURL(this._pdfUrl);
            this._pdfUrl = URL.createObjectURL(pdfBlob);
        } catch (err) {
            this._error = err.message;
        } finally {
            this._rendering = false;
        }
    }

    _blobToBase64(blob) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result.split(',')[1]);
            reader.onerror = reject;
            reader.readAsDataURL(blob);
        });
    }
}

customElements.define('bp-workbench', BpWorkbench);
