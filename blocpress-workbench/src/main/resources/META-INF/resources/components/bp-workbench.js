import { LitElement, html, css } from 'https://esm.sh/lit@3.2.1';

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
        _success: { state: true },
        _detailsView: { state: true },
        _templateDetails: { state: true },
        _submitting: { state: true },
        // Phase 3: TestData Management
        _activeTab: { state: true },
        _testDataSets: { state: true },
        _selectedTestData: { state: true },
        _generatedForm: { state: true },
        _testDataMode: { state: true },
        _testDataName: { state: true },
        _testDataFormData: { state: true },
        _testDataErrors: { state: true },
        _savingExpectedPdf: { state: true },
        _comparingPdf: { state: true },
        _regressionResult: { state: true },
        _testDataModeSaving: { state: true }
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

        /* Status badge */
        .status-badge {
            padding: 2px 8px;
            border-radius: 3px;
            color: #fff;
            font-size: 11px;
            font-weight: 600;
            white-space: nowrap;
        }

        /* Details panel */
        .details-panel {
            background: #fff;
            border: 1px solid #ddd;
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 24px;
        }
        .details-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 16px;
            padding-bottom: 12px;
            border-bottom: 1px solid #e0e0e0;
        }
        .details-header h3 {
            margin: 0;
            font-size: 16px;
            font-weight: 600;
            color: #333;
        }
        .details-body {
            display: flex;
            flex-direction: column;
            gap: 16px;
        }
        .validation-section {
            padding: 12px;
            background: #f9f9f9;
            border-radius: 4px;
        }
        .validation-section h4 {
            margin: 0 0 8px 0;
            font-size: 14px;
            color: #333;
            font-weight: 600;
        }
        .validation-section ul {
            margin: 0;
            padding-left: 20px;
            font-size: 13px;
        }
        .validation-message {
            padding: 8px;
            margin-bottom: 4px;
            border-radius: 4px;
            font-size: 13px;
        }
        .validation-message.error {
            background: #fff5f5;
            border-left: 3px solid #c62828;
            color: #c62828;
        }
        .validation-message.warning {
            background: #fffbf0;
            border-left: 3px solid #f57c00;
            color: #f57c00;
        }
        .condition {
            margin-bottom: 8px;
            padding: 8px;
            background: #fff;
            border-radius: 4px;
            font-size: 13px;
        }
        .condition.invalid {
            border-left: 3px solid #c62828;
        }
        .condition code {
            font-family: monospace;
            font-size: 12px;
            display: block;
            margin-bottom: 4px;
        }
        .condition .error-msg {
            font-size: 11px;
            color: #c62828;
        }
        .btn-submit {
            padding: 8px 16px;
            border: none;
            border-radius: 4px;
            background: #1e3c72;
            color: #fff;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
            align-self: flex-start;
        }
        .btn-submit:hover:not(:disabled) {
            background: #2a5298;
        }
        .btn-submit:disabled {
            background: #aaa;
            cursor: not-allowed;
        }

        /* Phase 3: Tabs */
        .tabs {
            display: flex;
            gap: 4px;
            border-bottom: 1px solid #ddd;
            margin-bottom: 16px;
        }
        .tab-btn {
            padding: 10px 16px;
            background: #f5f5f5;
            border: 1px solid #ddd;
            border-bottom: none;
            cursor: pointer;
            font-size: 13px;
            border-radius: 4px 4px 0 0;
            transition: all 0.2s;
        }
        .tab-btn.active {
            background: #fff;
            color: #1e3c72;
            border-color: #1e3c72;
            font-weight: 600;
        }
        .tab-btn:hover:not(.active) {
            background: #fff;
        }

        /* Test Data List */
        .testdata-list {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }
        .testdata-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 12px;
            background: #f9f9f9;
            border: 1px solid #e0e0e0;
            border-radius: 4px;
        }
        .testdata-item-info {
            flex: 1;
        }
        .testdata-item-name {
            font-weight: 600;
            color: #333;
            margin-bottom: 4px;
        }
        .testdata-item-meta {
            font-size: 12px;
            color: #888;
        }
        .testdata-item-actions {
            display: flex;
            gap: 6px;
        }
        .testdata-btn {
            padding: 6px 12px;
            border: 1px solid #ccc;
            border-radius: 4px;
            background: #fff;
            color: #555;
            font-size: 12px;
            cursor: pointer;
            white-space: nowrap;
        }
        .testdata-btn:hover {
            background: #f5f5f5;
        }
        .testdata-btn.primary {
            background: #1e3c72;
            color: #fff;
            border-color: #1e3c72;
        }
        .testdata-btn.primary:hover {
            background: #2a5298;
        }
        .testdata-btn.danger {
            background: #fff5f5;
            color: #c62828;
            border-color: #c62828;
        }
        .testdata-btn.danger:hover {
            background: #ffecec;
        }

        /* Test Data Form */
        .testdata-form {
            display: flex;
            flex-direction: column;
            gap: 12px;
        }
        .form-group {
            display: flex;
            flex-direction: column;
            gap: 6px;
        }
        .form-group label {
            font-size: 13px;
            font-weight: 600;
            color: #333;
        }
        .form-group input {
            padding: 8px;
            border: 1px solid #ccc;
            border-radius: 4px;
            font-size: 13px;
        }
        .form-group input[type="checkbox"] {
            width: 18px;
            height: 18px;
            cursor: pointer;
        }
        .form-actions {
            display: flex;
            gap: 8px;
            margin-top: 12px;
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
        this._detailsView = false;
        this._templateDetails = null;
        this._submitting = false;
        // Phase 3: TestData Management
        this._activeTab = 'upload'; // 'upload', 'testdata', 'preview'
        this._testDataSets = [];
        this._selectedTestData = null;
        this._generatedForm = null;
        this._testDataMode = 'list'; // 'list' or 'create'/'edit'
        this._testDataName = '';
        this._testDataFormData = {};
        this._testDataErrors = {};
        this._savingExpectedPdf = false;
        this._comparingPdf = false;
        this._regressionResult = null;
        this._testDataModeSaving = false;
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

            ${this._detailsView ? this._renderDetailsView() : ''}

            <!-- Tab Navigation (only when template selected) -->
            ${this._selectedTemplate && !this._detailsView ? html`
                <div class="tabs">
                    <button class="tab-btn ${this._activeTab === 'upload' ? 'active' : ''}"
                        @click=${() => this._switchTab('upload')}>
                        Upload
                    </button>
                    <button class="tab-btn ${this._activeTab === 'testdata' ? 'active' : ''}"
                        @click=${() => this._switchTab('testdata')}>
                        Testdaten
                    </button>
                    <button class="tab-btn ${this._activeTab === 'preview' ? 'active' : ''}"
                        @click=${() => this._switchTab('preview')}>
                        Vorschau
                    </button>
                </div>
            ` : ''}

            <!-- Upload Tab Content -->
            ${this._selectedTemplate && !this._detailsView && this._activeTab === 'upload' ? html`
                <div class="panel" style="margin-bottom: 24px;">
                    <div class="panel-header">
                        <h3>JSON Data</h3>
                        <button class="refresh-btn"
                            ?disabled=${!this._jsonValid || this._rendering}
                            @click=${this._renderPdf}>
                            &#8635; Vorschau generieren
                        </button>
                    </div>
                    <textarea
                        class=${this._jsonValid ? '' : 'invalid'}
                        .value=${this._jsonText}
                        @input=${this._onJsonInput}
                    ></textarea>
                    ${this._jsonValid ? '' : html`<div class="json-error">Ungültiges JSON</div>`}
                </div>
            ` : ''}

            <!-- TestData Tab Content -->
            ${this._selectedTemplate && !this._detailsView && this._activeTab === 'testdata' ? this._renderTestDataTab() : ''}

            <!-- Preview Tab Content -->
            ${this._selectedTemplate && !this._detailsView && this._activeTab === 'preview' ? html`
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
                        ${this._jsonValid ? '' : html`<div class="json-error">Ungültiges JSON</div>`}
                    </div>
                </div>
            ` : ''}

            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
            ${this._success ? html`<div class="success">${this._success}</div>` : ''}
        `;
    }

    _renderTestDataTab() {
        if (this._testDataMode === 'create') {
            return this._renderTestDataForm();
        }
        return this._renderTestDataList();
    }

    _renderTestDataList() {
        return html`
            <div class="panel" style="margin-bottom: 24px;">
                <div class="panel-header">
                    <h3>Test Datensätze</h3>
                    <button class="btn-submit" @click=${() => {
                        this._testDataMode = 'create';
                        this._testDataName = '';
                        this._testDataFormData = {};
                    }}>+ Neu</button>
                </div>

                ${this._testDataSets.length === 0
                    ? html`<p style="color: #888; text-align: center; padding: 20px;">Keine Testdaten vorhanden</p>`
                    : html`
                        <div class="testdata-list">
                            ${this._testDataSets.map(td => html`
                                <div class="testdata-item">
                                    <div class="testdata-item-info">
                                        <div class="testdata-item-name">${td.name}</div>
                                        <div class="testdata-item-meta">
                                            ${td.hasExpectedPdf ? '✓ Expected PDF vorhanden' : 'Kein Expected PDF'}
                                            | Erstellt: ${new Date(td.createdAt).toLocaleDateString('de-DE')}
                                        </div>
                                    </div>
                                    <div class="testdata-item-actions">
                                        ${td.hasExpectedPdf ? html`
                                            <button class="testdata-btn"
                                                @click=${() => this._renderWithTestData(td)}>
                                                Vorschau
                                            </button>
                                        ` : ''}
                                        <button class="testdata-btn primary"
                                            ?disabled=${this._savingExpectedPdf}
                                            @click=${() => this._prepareForPdfSave(td)}>
                                            PDF speichern
                                        </button>
                                        <button class="testdata-btn danger"
                                            @click=${() => this._deleteTestDataSet(td.id)}>
                                            Löschen
                                        </button>
                                    </div>
                                </div>
                            `)}
                        </div>
                    `}
            </div>
        `;
    }

    _renderTestDataForm() {
        if (!this._generatedForm) {
            this._generateFormFromTemplate();
        }

        return html`
            <div class="panel" style="margin-bottom: 24px;">
                <div class="panel-header">
                    <h3>Neuer Testdatensatz</h3>
                    <button class="icon-btn" @click=${() => this._testDataMode = 'list'}>&times;</button>
                </div>

                <div class="testdata-form">
                    <div class="form-group">
                        <label>Name</label>
                        <input type="text"
                            placeholder="z.B. Standardfall"
                            .value=${this._testDataName}
                            @input=${e => this._testDataName = e.target.value}>
                    </div>

                    ${this._generatedForm?.map(field => html`
                        <div class="form-group">
                            <label>${field.name}</label>
                            ${field.type === 'Boolean'
                                ? html`<input type="checkbox"
                                    name="${field.name}"
                                    @change=${this._onTestDataInput.bind(this)}>`
                                : field.type === 'Number'
                                ? html`<input type="number"
                                    name="${field.name}"
                                    placeholder="${field.name}"
                                    @input=${this._onTestDataInput.bind(this)}>`
                                : html`<input type="text"
                                    name="${field.name}"
                                    placeholder="${field.name}"
                                    @input=${this._onTestDataInput.bind(this)}>`
                            }
                        </div>
                    `) || html`<p style="color: #888;">Keine Felder definiert</p>`}

                    <div class="form-actions">
                        <button class="btn-submit"
                            ?disabled=${this._testDataModeSaving || !this._testDataName.trim()}
                            @click=${this._createTestDataSet}>
                            ${this._testDataModeSaving ? 'Speichert...' : 'Speichern'}
                        </button>
                        <button class="btn-cancel" @click=${() => this._testDataMode = 'list'}>
                            Abbrechen
                        </button>
                    </div>
                </div>
            </div>
        `;
    }

    _prepareForPdfSave(testData) {
        this._selectedTestData = testData;
        this._jsonText = JSON.stringify(testData.testData, null, 2);
        this._jsonValid = true;
        this._activeTab = 'upload';
        this._renderPdf();
    }

    async _renderWithTestData(testData) {
        this._jsonText = JSON.stringify(testData.testData, null, 2);
        this._jsonValid = true;
        this._activeTab = 'preview';
        await this._renderPdf();
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
        const statusColors = {
            'DRAFT': '#888',
            'SUBMITTED': '#1976d2',
            'APPROVED': '#388e3c',
            'REJECTED': '#d32f2f'
        };

        return html`
            <div class="autocomplete">
                <div class="selected-template">
                    ${this._selectedTemplate.name}
                    <span class="status-badge"
                        style="background: ${statusColors[this._selectedTemplate.status] || '#888'}">
                        ${this._selectedTemplate.status}
                    </span>
                    <button class="icon-btn" title="Details anzeigen"
                        @click=${this._showDetails}>ⓘ</button>
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

    _renderDetailsView() {
        if (!this._templateDetails) return '';

        const vr = this._templateDetails.validationResult;
        const canSubmit = this._templateDetails.status === 'DRAFT' && vr?.isValid;

        return html`
            <div class="details-panel">
                <div class="details-header">
                    <h3>Template Details: ${this._templateDetails.name}</h3>
                    <button class="icon-btn" title="Schließen"
                        @click=${this._closeDetails}>&times;</button>
                </div>

                <div class="details-body">
                    <div class="validation-section">
                        <strong>Status:</strong> ${this._templateDetails.status}<br>
                        <strong>Erstellt:</strong> ${new Date(this._templateDetails.createdAt).toLocaleString('de-DE')}<br>
                        <strong>Validierung:</strong>
                        ${vr?.isValid ? html`<span style="color: #388e3c;">✓ Gültig</span>` : html`<span style="color: #c62828;">✗ Ungültig</span>`}
                    </div>

                    ${vr?.errors?.length > 0 ? html`
                        <div class="validation-section">
                            <h4>Fehler</h4>
                            ${vr.errors.map(e => html`
                                <div class="validation-message error">
                                    <strong>${e.code}:</strong> ${e.message}
                                </div>
                            `)}
                        </div>
                    ` : ''}

                    ${vr?.warnings?.length > 0 ? html`
                        <div class="validation-section">
                            <h4>Warnungen</h4>
                            ${vr.warnings.map(w => html`
                                <div class="validation-message warning">
                                    <strong>${w.code}:</strong> ${w.message}
                                </div>
                            `)}
                        </div>
                    ` : ''}

                    ${vr?.userFields?.length > 0 ? html`
                        <div class="validation-section">
                            <h4>Datenfelder (${vr.userFields.length})</h4>
                            <ul>
                                ${vr.userFields.map(f => html`<li>${f.name} (${f.type})</li>`)}
                            </ul>
                        </div>
                    ` : ''}

                    ${vr?.repetitionGroups?.length > 0 ? html`
                        <div class="validation-section">
                            <h4>Wiederholungsgruppen (${vr.repetitionGroups.length})</h4>
                            <ul>
                                ${vr.repetitionGroups.map(rg => html`
                                    <li>${rg.name} → <code>${rg.arrayPath}</code> (${rg.type})</li>
                                `)}
                            </ul>
                        </div>
                    ` : ''}

                    ${vr?.conditions?.length > 0 ? html`
                        <div class="validation-section">
                            <h4>Bedingungen (${vr.conditions.length})</h4>
                            ${vr.conditions.map(c => html`
                                <div class="condition ${c.syntaxValid ? '' : 'invalid'}">
                                    <code>${c.expression}</code>
                                    ${!c.syntaxValid ? html`<div class="error-msg">${c.errorMessage}</div>` : ''}
                                </div>
                            `)}
                        </div>
                    ` : ''}

                    ${canSubmit ? html`
                        <button class="btn-submit"
                            ?disabled=${this._submitting}
                            @click=${this._submitTemplate}>
                            ${this._submitting ? 'Wird eingereicht...' : 'Zur Freigabe einreichen'}
                        </button>
                    ` : ''}
                </div>
            </div>
        `;
    }

    // --- API helpers ---

    _getApiBase() {
        if (this.apiBaseUrl) return this.apiBaseUrl.replace(/\/+$/, '');
        try {
            const src = new URL(import.meta.url);
            return src.origin;
        } catch {
            // Fallback when import.meta.url is not available (cross-origin dynamic import)
            return window.location.origin;
        }
    }

    _getRenderUrl() {
        return (localStorage.getItem(RENDER_URL_KEY) || DEFAULT_RENDER_URL).replace(/\/+$/, '');
    }

    // --- Template list ---

    async _loadTemplates() {
        try {
            const response = await fetch(`${this._getApiBase()}/api/workbench/templates`);
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
        this._activeTab = 'upload';
        this._loadTestDataSets();
        this._generateFormFromTemplate();
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

            const response = await fetch(`${this._getApiBase()}/api/workbench/templates`, {
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
                `${this._getApiBase()}/api/workbench/templates/${this._selectedTemplate.id}`
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

            // 3. Display PDF and store blob for later use
            const pdfBlob = await renderResponse.blob();
            this._pdfBlob = pdfBlob; // Store for later use (e.g., saving as expected PDF)
            if (this._pdfUrl) URL.revokeObjectURL(this._pdfUrl);
            this._pdfUrl = URL.createObjectURL(pdfBlob);

            // 4. If we're in save mode, save the expected PDF
            if (this._selectedTestData) {
                await this._finalizePdfSave();
            }
        } catch (err) {
            this._error = err.message;
        } finally {
            this._rendering = false;
        }
    }

    async _finalizePdfSave() {
        if (!this._pdfBlob || !this._selectedTestData || !this._selectedTemplate) return;

        this._savingExpectedPdf = true;
        try {
            const saveResponse = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${this._selectedTemplate.id}/testdata/${this._selectedTestData.id}/save-expected`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/octet-stream' },
                    body: this._pdfBlob
                }
            );

            if (!saveResponse.ok) throw new Error('Fehler beim Speichern');

            this._success = 'Expected PDF gespeichert';
            this._selectedTestData = null;
            await this._loadTestDataSets();
        } catch (err) {
            this._error = err.message;
        } finally {
            this._savingExpectedPdf = false;
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

    // --- Details View ---

    async _showDetails() {
        if (!this._selectedTemplate) return;

        try {
            const response = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${this._selectedTemplate.id}/details`
            );
            if (!response.ok) throw new Error('Details konnten nicht geladen werden');

            this._templateDetails = await response.json();
            this._detailsView = true;
        } catch (err) {
            this._error = err.message;
        }
    }

    _closeDetails() {
        this._detailsView = false;
        this._templateDetails = null;
    }

    async _submitTemplate() {
        if (!this._templateDetails) return;

        this._submitting = true;
        this._error = '';

        try {
            const response = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${this._templateDetails.id}/submit`,
                { method: 'POST' }
            );

            if (!response.ok) {
                const text = await response.text();
                throw new Error(text);
            }

            this._success = 'Template erfolgreich eingereicht';
            this._closeDetails();
            await this._loadTemplates();

            // Re-select template to update status
            const updated = this._templates.find(t => t.id === this._templateDetails.id);
            if (updated) {
                this._selectedTemplate = updated;
            }
        } catch (err) {
            this._error = err.message;
        } finally {
            this._submitting = false;
        }
    }

    // --- Phase 3: TestData Management ---

    async _loadTestDataSets() {
        if (!this._selectedTemplate) return;

        try {
            const response = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${this._selectedTemplate.id}/testdata`
            );
            if (response.ok) {
                this._testDataSets = await response.json();
            }
        } catch {
            // ignore
        }
    }

    _generateFormFromTemplate() {
        if (!this._selectedTemplate?.validationResult?.userFields) {
            this._generatedForm = null;
            return;
        }

        const fields = this._selectedTemplate.validationResult.userFields;
        const formElements = fields.map(field => {
            const { name, type } = field;
            let inputHtml = '';

            switch (type) {
                case 'String':
                    inputHtml = `<input type="text" name="${name}" placeholder="${name}">`;
                    break;
                case 'Number':
                    inputHtml = `<input type="number" name="${name}" placeholder="${name}">`;
                    break;
                case 'Boolean':
                    inputHtml = `<input type="checkbox" name="${name}">`;
                    break;
                default:
                    inputHtml = `<input type="text" name="${name}" placeholder="${name}">`;
            }

            return {
                name,
                type,
                inputHtml
            };
        });

        this._generatedForm = formElements;
    }

    _onTestDataInput(e) {
        const name = e.target.name;
        const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;

        // Build nested object from dot-notation field names
        const parts = name.split('.');
        let obj = this._testDataFormData;
        for (let i = 0; i < parts.length - 1; i++) {
            if (!obj[parts[i]]) obj[parts[i]] = {};
            obj = obj[parts[i]];
        }
        obj[parts[parts.length - 1]] = value;

        this._testDataFormData = { ...this._testDataFormData };
    }

    async _createTestDataSet() {
        if (!this._selectedTemplate || !this._testDataName.trim()) {
            this._error = 'Name erforderlich';
            return;
        }

        this._testDataModeSaving = true;
        this._error = '';

        try {
            const response = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${this._selectedTemplate.id}/testdata`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        name: this._testDataName,
                        testData: this._testDataFormData
                    })
                }
            );

            if (!response.ok) throw new Error('Fehler beim Erstellen');

            this._success = `TestDataSet '${this._testDataName}' erstellt`;
            this._testDataName = '';
            this._testDataFormData = {};
            this._testDataMode = 'list';
            await this._loadTestDataSets();
        } catch (err) {
            this._error = err.message;
        } finally {
            this._testDataModeSaving = false;
        }
    }

    async _deleteTestDataSet(testDataId) {
        if (!this._selectedTemplate) return;

        try {
            const response = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${this._selectedTemplate.id}/testdata/${testDataId}`,
                { method: 'DELETE' }
            );

            if (!response.ok) throw new Error('Fehler beim Löschen');

            this._success = 'TestDataSet gelöscht';
            await this._loadTestDataSets();
        } catch (err) {
            this._error = err.message;
        }
    }

    async _saveExpectedPdf(testDataId) {
        if (!this._pdfUrl || !this._selectedTemplate) {
            this._error = 'Bitte PDF generieren zuerst';
            return;
        }

        this._savingExpectedPdf = true;
        this._error = '';

        try {
            const response = await fetch(this._pdfUrl);
            const pdfBlob = await response.blob();

            const saveResponse = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${this._selectedTemplate.id}/testdata/${testDataId}/save-expected`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/octet-stream' },
                    body: pdfBlob
                }
            );

            if (!saveResponse.ok) throw new Error('Fehler beim Speichern');

            this._success = 'Expected PDF gespeichert';
            await this._loadTestDataSets();
        } catch (err) {
            this._error = err.message;
        } finally {
            this._savingExpectedPdf = false;
        }
    }

    _switchTab(tab) {
        this._activeTab = tab;
        if (tab === 'testdata' && this._selectedTemplate) {
            this._loadTestDataSets();
            this._generateFormFromTemplate();
        }
    }
}

customElements.define('bp-workbench', BpWorkbench);
