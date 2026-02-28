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
        // Phase 4: Dashboard & Status Management
        _dashboardView: { state: true },
        _statusFilter: { state: true },
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
        _testDataModeSaving: { state: true },
        _selectedTestDataForPreview: { state: true },
        _expandedTestDataId: { state: true },
        _editingTestDataId: { state: true },
        _editingTestDataFormData: { state: true },
        _savingTestData: { state: true }
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
            cursor: pointer;
            transition: all 0.2s;
        }
        .testdata-item.selected {
            background: #e3f2fd;
            border-color: #1e3c72;
            box-shadow: 0 0 0 2px rgba(30, 60, 114, 0.1);
        }
        .testdata-item:hover {
            background: #f5f5f5;
        }
        .testdata-item.selected:hover {
            background: #e3f2fd;
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

        /* Tree structure styles */
        .tree-node {
            margin-bottom: 12px;
        }
        .tree-node-header {
            font-weight: 600;
            margin: 12px 0 8px 0;
            color: #333;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .tree-array-header {
            display: flex;
            align-items: center;
            gap: 8px;
            font-weight: 600;
            margin: 12px 0 8px 0;
            color: #333;
        }
        .btn-add-item {
            padding: 4px 10px;
            font-size: 12px;
            background: #4CAF50;
            color: white;
            border: none;
            border-radius: 3px;
            cursor: pointer;
            white-space: nowrap;
        }
        .btn-add-item:hover {
            background: #45a049;
        }
        .array-item {
            border-left: 2px solid #ddd;
            padding-left: 12px;
            margin: 8px 0;
            padding: 8px;
            background: #fafafa;
            border-radius: 3px;
        }
        .array-item-header {
            display: flex;
            align-items: center;
            gap: 8px;
            font-weight: 500;
            color: #555;
            margin-bottom: 8px;
        }
        .btn-remove-item {
            padding: 2px 8px;
            font-size: 12px;
            background: #f44336;
            color: white;
            border: none;
            border-radius: 3px;
            cursor: pointer;
            white-space: nowrap;
        }
        .btn-remove-item:hover {
            background: #da190b;
        }
        .tree-field-group {
            margin-bottom: 12px;
        }

        /* Test Data Item Container */
        .testdata-item-container {
            border: 1px solid #e0e0e0;
            border-radius: 4px;
            margin-bottom: 8px;
            background: #fff;
            overflow: hidden;
        }
        .testdata-item-container.expanded {
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }

        /* Test Data Item */
        .testdata-item {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 12px;
            background: #f9f9f9;
            cursor: pointer;
            transition: all 0.2s;
            border-bottom: 1px solid transparent;
        }
        .testdata-item:hover {
            background: #f5f5f5;
        }
        .testdata-item.selected {
            background: #e3f2fd;
            border-color: #1e3c72;
        }
        .testdata-item.expanded {
            border-bottom: 1px solid #e0e0e0;
        }

        /* Expand/Collapse Button */
        .expand-btn {
            background: none;
            border: none;
            color: #555;
            cursor: pointer;
            font-size: 12px;
            padding: 4px 8px;
            min-width: 20px;
            text-align: center;
        }
        .expand-btn:hover {
            color: #1e3c72;
        }

        /* Info Section */
        .testdata-item-info {
            flex: 1;
            cursor: pointer;
        }
        .testdata-item-info:hover {
            text-decoration: underline;
        }

        /* Edit Panel */
        .testdata-edit-panel {
            padding: 16px;
            background: #f5f5f5;
            border-top: 1px solid #e0e0e0;
        }
        .testdata-view {
            padding: 12px 0;
        }
        .testdata-view-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 12px;
            padding-bottom: 8px;
            border-bottom: 1px solid #e0e0e0;
        }
        .testdata-view-header h4 {
            margin: 0;
            font-size: 14px;
            color: #333;
        }
        .testdata-tree-view {
            max-height: 400px;
            overflow-y: auto;
            font-size: 13px;
            color: #555;
            font-family: 'Courier New', monospace;
        }
        .testdata-edit {
            padding: 8px 0;
        }
        .testdata-edit-header {
            margin-bottom: 12px;
            padding-bottom: 8px;
            border-bottom: 1px solid #e0e0e0;
        }
        .testdata-edit-header h4 {
            margin: 0;
            font-size: 14px;
            color: #333;
        }

        /* Dashboard View */
        .dashboard {
            padding: 0;
        }

        .dashboard-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 24px;
            padding-bottom: 16px;
            border-bottom: 1px solid #ddd;
        }

        .dashboard-header h2 {
            margin: 0;
            font-size: 24px;
            font-weight: 600;
            color: #333;
        }

        .btn-primary {
            padding: 10px 16px;
            border: none;
            border-radius: 4px;
            background: #1e3c72;
            color: white;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            white-space: nowrap;
        }

        .btn-primary:hover {
            background: #2a5298;
        }

        /* Status Filter */
        .status-filter {
            margin-bottom: 24px;
        }

        .filter-buttons {
            display: flex;
            gap: 12px;
            flex-wrap: wrap;
        }

        .filter-btn {
            padding: 10px 16px;
            border: 2px solid #ddd;
            background: white;
            border-radius: 8px;
            cursor: pointer;
            font-size: 14px;
            font-weight: 500;
            transition: all 0.2s;
        }

        .filter-btn:hover {
            background: #f5f5f5;
            border-color: #1e3c72;
        }

        .filter-btn.active {
            background: #1e3c72;
            color: white;
            border-color: #1e3c72;
        }

        .filter-btn .badge {
            background: rgba(0,0,0,0.1);
            padding: 2px 8px;
            border-radius: 12px;
            margin-left: 8px;
            font-size: 12px;
        }

        .filter-btn.active .badge {
            background: rgba(255,255,255,0.2);
        }

        /* Template List */
        .template-list {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
            gap: 16px;
        }

        .template-card {
            border: 1px solid #ddd;
            border-radius: 8px;
            padding: 16px;
            background: white;
            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
            transition: box-shadow 0.2s;
            display: flex;
            flex-direction: column;
        }

        .template-card:hover {
            box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        }

        .card-header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 12px;
            gap: 8px;
        }

        .card-header h3 {
            margin: 0;
            font-size: 16px;
            font-weight: 600;
            flex: 1;
            word-break: break-word;
        }

        .status-badge-card {
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 11px;
            font-weight: 600;
            color: white;
            text-transform: uppercase;
            white-space: nowrap;
            flex-shrink: 0;
        }

        .card-meta {
            display: flex;
            gap: 16px;
            font-size: 13px;
            color: #666;
            margin-bottom: 16px;
            flex-wrap: wrap;
        }

        .card-actions {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
            margin-top: auto;
        }

        .action-btn {
            padding: 6px 12px;
            border: 1px solid #ddd;
            border-radius: 4px;
            background: white;
            cursor: pointer;
            font-size: 13px;
            transition: all 0.2s;
            white-space: nowrap;
        }

        .action-btn:hover {
            border-color: #1e3c72;
            color: #1e3c72;
        }

        .action-btn.primary {
            background: #1e3c72;
            color: white;
            border-color: #1e3c72;
        }

        .action-btn.primary:hover {
            background: #2a5298;
        }

        .action-btn.success {
            background: #388e3c;
            color: white;
            border-color: #388e3c;
        }

        .action-btn.success:hover {
            background: #45a049;
        }

        .action-btn.danger {
            background: #d32f2f;
            color: white;
            border-color: #d32f2f;
        }

        .action-btn.danger:hover {
            background: #e53935;
        }

        .action-btn.secondary {
            background: #f5f5f5;
            border-color: #ddd;
        }

        .action-btn.secondary:hover {
            background: #eee;
        }

        /* Empty State */
        .empty-state {
            text-align: center;
            padding: 48px;
            color: #999;
        }

        /* Workspace Header with Back Button */
        .workspace-header {
            display: flex;
            align-items: center;
            gap: 16px;
            margin-bottom: 24px;
            padding-bottom: 16px;
            border-bottom: 1px solid #ddd;
        }

        .btn-back {
            padding: 8px 12px;
            border: 1px solid #ddd;
            border-radius: 4px;
            background: white;
            color: #555;
            font-size: 13px;
            cursor: pointer;
            white-space: nowrap;
        }

        .btn-back:hover {
            background: #f5f5f5;
            border-color: #1e3c72;
            color: #1e3c72;
        }

        .workspace-header h2 {
            margin: 0;
            font-size: 18px;
            font-weight: 600;
            color: #333;
            flex: 1;
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
        // Phase 4: Dashboard & Status Management
        this._dashboardView = true; // Start with dashboard
        this._statusFilter = 'ALL'; // Filter: ALL, DRAFT, SUBMITTED, APPROVED, REJECTED
        // Phase 3: TestData Management
        this._activeTab = 'testdata'; // Only 'testdata' tab now
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
        this._selectedTestDataForPreview = null;
        this._expandedTestDataId = null;
        this._editingTestDataId = null;
        this._editingTestDataFormData = {};
        this._savingTestData = false;
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
        if (this._dashboardView) {
            return this._renderDashboard();
        }

        return html`
            <div class="workspace-header">
                <button class="btn-back" @click=${() => this._returnToDashboard()}>
                    ← Zurück zum Dashboard
                </button>
                <h2>${this._selectedTemplate?.name || 'Template Workspace'}</h2>
            </div>

            ${this._uploadMode ? this._renderUploadForm() : ''}

            ${this._detailsView ? this._renderDetailsView() : ''}

            <!-- Tab Navigation (only when template selected) -->
            ${this._selectedTemplate && !this._detailsView ? html`
                <div class="tabs">
                    <button class="tab-btn ${this._activeTab === 'testdata' ? 'active' : ''}"
                        @click=${() => this._switchTab('testdata')}>
                        Testdaten
                    </button>
                </div>
            ` : ''}

            <!-- TestData Tab Content (now includes preview) -->
            ${this._selectedTemplate && !this._detailsView && this._activeTab === 'testdata' ? this._renderTestDataTab() : ''}

            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
            ${this._success ? html`<div class="success">${this._success}</div>` : ''}
        `;
    }

    _renderDashboard() {
        const filteredTemplates = this._filterTemplates();

        return html`
            <div class="dashboard">
                <div class="dashboard-header">
                    <h2>Template Verwaltung</h2>
                    <button class="btn-primary" @click=${this._openUploadDialog.bind(this)}>
                        + Neues Template hochladen
                    </button>
                </div>

                <div class="status-filter">
                    ${this._renderFilterButtons()}
                </div>

                <div class="template-list">
                    ${filteredTemplates.length === 0 ? html`
                        <div class="empty-state">
                            <p>Keine Templates mit Status "${this._statusFilter === 'ALL' ? 'beliebig' : this._statusFilter}"</p>
                        </div>
                    ` : filteredTemplates.map(t => this._renderTemplateCard(t))}
                </div>

                ${this._uploadMode ? this._renderUploadForm() : ''}

                ${this._error ? html`<div class="error">${this._error}</div>` : ''}
                ${this._success ? html`<div class="success">${this._success}</div>` : ''}
            </div>
        `;
    }

    _renderFilterButtons() {
        const filters = [
            { key: 'ALL', label: '📋 Alle', count: this._templates.length },
            { key: 'DRAFT', label: '📝 Entwurf', count: this._countByStatus('DRAFT') },
            { key: 'SUBMITTED', label: '🧪 Test', count: this._countByStatus('SUBMITTED') },
            { key: 'APPROVED', label: '✅ Produktiv', count: this._countByStatus('APPROVED') },
            { key: 'REJECTED', label: '❌ Abgelehnt', count: this._countByStatus('REJECTED') }
        ];

        return html`
            <div class="filter-buttons">
                ${filters.map(f => html`
                    <button
                        class="filter-btn ${this._statusFilter === f.key ? 'active' : ''}"
                        @click=${() => { this._statusFilter = f.key; }}>
                        ${f.label} <span class="badge">${f.count}</span>
                    </button>
                `)}
            </div>
        `;
    }

    _filterTemplates() {
        if (this._statusFilter === 'ALL') return this._templates;
        return this._templates.filter(t => t.status === this._statusFilter);
    }

    _countByStatus(status) {
        return this._templates.filter(t => t.status === status).length;
    }

    _getStatusColor(status) {
        const colors = {
            'DRAFT': '#888',
            'SUBMITTED': '#1976d2',
            'APPROVED': '#388e3c',
            'REJECTED': '#d32f2f'
        };
        return colors[status] || '#888';
    }

    _getStatusIcon(status) {
        const icons = {
            'DRAFT': '📝',
            'SUBMITTED': '🧪',
            'APPROVED': '✅',
            'REJECTED': '❌'
        };
        return icons[status] || '•';
    }

    _renderTemplateCard(template) {
        const statusColor = this._getStatusColor(template.status);
        const actions = this._getTemplateActions(template);

        return html`
            <div class="template-card">
                <div class="card-header">
                    <h3 style="color: ${statusColor};">
                        ${this._getStatusIcon(template.status)} ${template.name}
                    </h3>
                    <span class="status-badge-card" style="background: ${statusColor};">
                        ${template.status}
                    </span>
                </div>

                <div class="card-meta">
                    <span>Erstellt: ${new Date(template.createdAt).toLocaleDateString('de-DE')}</span>
                    <span>${template.isValid ? '✓ Valid' : '✗ Invalid'}</span>
                </div>

                <div class="card-actions">
                    ${actions.map(action => html`
                        <button
                            class="action-btn ${action.style}"
                            @click=${action.handler}>
                            ${action.label}
                        </button>
                    `)}
                </div>
            </div>
        `;
    }

    _getTemplateActions(template) {
        const actions = [
            { label: 'Öffnen', style: 'primary', handler: () => this._openTemplate(template) }
        ];

        switch (template.status) {
            case 'DRAFT':
                actions.push(
                    { label: 'Aktualisieren', style: 'secondary', handler: () => this._updateTemplate(template) },
                    { label: '→ Test', style: 'success', handler: () => this._changeStatus(template.id, 'SUBMITTED') },
                    { label: 'Löschen', style: 'danger', handler: () => this._deleteTemplate(template.id) }
                );
                break;

            case 'SUBMITTED':
                actions.push(
                    { label: '← Zurück', style: 'secondary', handler: () => this._changeStatus(template.id, 'DRAFT') },
                    { label: '✓ Genehmigen', style: 'success', handler: () => this._changeStatus(template.id, 'APPROVED') },
                    { label: '✗ Ablehnen', style: 'danger', handler: () => this._changeStatus(template.id, 'REJECTED') }
                );
                break;

            case 'APPROVED':
                actions.push(
                    { label: '← Zurück zu Test', style: 'secondary', handler: () => this._changeStatus(template.id, 'SUBMITTED') },
                    { label: 'Als Kopie', style: 'primary', handler: () => this._duplicateTemplate(template) }
                );
                break;

            case 'REJECTED':
                actions.push(
                    { label: '← Zurück', style: 'secondary', handler: () => this._changeStatus(template.id, 'DRAFT') },
                    { label: 'Löschen', style: 'danger', handler: () => this._deleteTemplate(template.id) }
                );
                break;
        }

        return actions;
    }

    _openUploadDialog() {
        this._uploadMode = true;
        this._uploadName = '';
        this._uploadFile = null;
        this._error = '';
        this._success = '';
    }

    _openTemplate(template) {
        this._dashboardView = false;
        this._selectTemplate(template);
    }

    _returnToDashboard() {
        this._dashboardView = true;
        this._selectedTemplate = null;
        this._searchText = '';
        this._showSuggestions = false;
        this._error = '';
        this._success = '';
        this._activeTab = 'testdata';
        if (this._pdfUrl) {
            URL.revokeObjectURL(this._pdfUrl);
            this._pdfUrl = null;
        }
    }

    async _changeStatus(templateId, newStatus) {
        try {
            const response = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${templateId}/status`,
                {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ newStatus })
                }
            );

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Fehler: ${response.status} - ${errorText}`);
            }

            this._success = `Template Status geändert zu ${newStatus}`;
            await this._loadTemplates();
        } catch (err) {
            this._error = err.message;
        }
    }

    async _duplicateTemplate(template) {
        const newName = prompt('Name für Kopie:', `${template.name} (Kopie)`);
        if (!newName || !newName.trim()) return;

        try {
            const response = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${template.id}/duplicate`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name: newName.trim() })
                }
            );

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Fehler: ${response.status} - ${errorText}`);
            }

            const created = await response.json();
            this._success = `Template "${created.name}" erstellt als DRAFT`;
            await this._loadTemplates();
        } catch (err) {
            this._error = err.message;
        }
    }

    async _updateTemplate(template) {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.odt';

        input.onchange = async (e) => {
            const file = e.target.files[0];
            if (!file) return;

            const formData = new FormData();
            formData.append('file', file);

            try {
                const response = await fetch(
                    `${this._getApiBase()}/api/workbench/templates/${template.id}/content`,
                    {
                        method: 'PUT',
                        body: formData
                    }
                );

                if (!response.ok) {
                    const errorText = await response.text();
                    throw new Error(`Fehler: ${response.status} - ${errorText}`);
                }

                const updated = await response.json();
                this._success = `Template "${updated.name}" aktualisiert`;
                await this._loadTemplates();
            } catch (err) {
                this._error = err.message;
            }
        };

        input.click();
    }

    async _deleteTemplate(templateId) {
        if (!confirm('Wirklich löschen?')) return;

        try {
            const response = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${templateId}`,
                { method: 'DELETE' }
            );

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Fehler: ${response.status} - ${errorText}`);
            }

            this._success = 'Template gelöscht';
            await this._loadTemplates();
        } catch (err) {
            this._error = err.message;
        }
    }

    _renderTestDataTab() {
        if (this._testDataMode === 'create') {
            return this._renderTestDataForm();
        }
        return this._renderTestDataTabContent();
    }

    _renderTestDataTabContent() {
        // If creating new test data, show form
        if (this._testDataMode === 'create') {
            return this._renderTestDataForm();
        }

        return html`
            <div class="workspace">
                <!-- Left panel: Test data list -->
                <div class="panel">
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
                                    <div class="testdata-item-container">
                                        <!-- Item Header -->
                                        <div class="testdata-item ${this._selectedTestDataForPreview?.id === td.id ? 'selected' : ''} ${this._expandedTestDataId === td.id ? 'expanded' : ''}">
                                            <!-- Expand/Collapse Button -->
                                            <button class="expand-btn" @click=${() => this._toggleExpandTestData(td.id)}>
                                                ${this._expandedTestDataId === td.id ? '▼' : '▶'}
                                            </button>

                                            <!-- Info Section (clickable for preview) -->
                                            <div class="testdata-item-info" @click=${() => this._selectTestDataForPreview(td)}>
                                                <div class="testdata-item-name">${td.name}</div>
                                                <div class="testdata-item-meta">
                                                    ${td.hasExpectedPdf ? '✓ Expected PDF' : '○ Kein Expected PDF'}
                                                    | ${new Date(td.createdAt).toLocaleDateString('de-DE')}
                                                </div>
                                            </div>

                                            <!-- Action Buttons -->
                                            <div class="testdata-item-actions">
                                                <button class="testdata-btn"
                                                    @click=${() => this._duplicateTestData(td)}>
                                                    Kopieren
                                                </button>
                                                <button class="testdata-btn danger"
                                                    @click=${() => this._deleteTestDataSet(td.id)}>
                                                    Löschen
                                                </button>
                                            </div>
                                        </div>

                                        <!-- Expanded Content: Edit Form -->
                                        ${this._expandedTestDataId === td.id ? html`
                                            <div class="testdata-edit-panel">
                                                ${this._editingTestDataId === td.id
                                                    ? this._renderTestDataEditForm(td)
                                                    : this._renderTestDataReadView(td)
                                                }
                                            </div>
                                        ` : ''}
                                    </div>
                                `)}
                            </div>
                        `}
                </div>

                <!-- Right panel: PDF preview -->
                <div class="panel">
                    <div class="panel-header">
                        <h3>Vorschau</h3>
                        ${this._rendering ? html`<span class="spinner"></span>` : ''}
                    </div>
                    ${this._pdfUrl
                        ? html`<iframe class="pdf-frame" src=${this._pdfUrl}></iframe>`
                        : html`<div class="pdf-placeholder">
                            ${this._rendering ? 'Wird gerendert...' : 'Testfall auswählen zur Vorschau'}
                          </div>`}
                </div>
            </div>
        `;
    }

    _renderTestDataReadView(testData) {
        return html`
            <div class="testdata-view">
                <div class="testdata-view-header">
                    <h4>Testdaten: ${testData.name}</h4>
                    <div style="display: flex; gap: 8px;">
                        <button class="btn-submit" style="padding: 6px 12px; font-size: 12px;"
                            @click=${() => {
                                this._editingTestDataId = testData.id;
                                this._editingTestDataFormData = JSON.parse(JSON.stringify(testData.testData || {}));
                            }}>
                            ✎ Bearbeiten
                        </button>
                        <button class="btn-submit" style="padding: 6px 12px; font-size: 12px;"
                            @click=${() => this._duplicateTestData(testData)}>
                            ↗ Als Testfall hinterlegen
                        </button>
                    </div>
                </div>
                <div class="testdata-tree-view">
                    ${this._renderTestDataAsTree(testData.testData || {}, '', 0)}
                </div>
            </div>
        `;
    }

    _renderTestDataEditForm(testData) {
        return html`
            <div class="testdata-edit">
                <div class="testdata-edit-header">
                    <h4>Testdaten Bearbeitung: ${testData.name}</h4>
                </div>
                <div class="testdata-form">
                    <!-- Render editable tree structure -->
                    ${Object.entries(this._selectedTemplate?.validationResult?.schema?.properties || {}).map(([fieldName, fieldSchema]) =>
                        this._renderSchemaProperty(fieldName, fieldSchema, fieldName, 0)
                    )}
                </div>
                <div class="form-actions" style="margin-top: 16px;">
                    <button class="btn-submit"
                        ?disabled=${this._savingTestData}
                        @click=${() => this._saveTestDataEdits(testData.id)}>
                        ${this._savingTestData ? 'Speichert...' : '💾 Speichern'}
                    </button>
                    <button class="btn-cancel"
                        @click=${() => this._editingTestDataId = null}>
                        Abbrechen
                    </button>
                    <button class="testdata-btn primary"
                        ?disabled=${this._savingExpectedPdf}
                        @click=${() => this._saveExpectedPdfForTestData(testData)}>
                        PDF speichern
                    </button>
                </div>
            </div>
        `;
    }

    _renderTestDataAsTree(obj, path, depth = 0) {
        if (!obj || typeof obj !== 'object') {
            return html`<div style="margin-left: ${depth * 20}px; padding: 4px; color: #666;">
                ${obj === null ? '(leer)' : String(obj)}
            </div>`;
        }

        if (Array.isArray(obj)) {
            return html`
                <div style="margin-left: ${depth * 20}px;">
                    <div style="font-weight: 600; color: #555; margin: 8px 0;">📁 Array (${obj.length} Items)</div>
                    ${obj.map((item, idx) => html`
                        <div style="margin-left: 12px; padding: 4px; background: #f5f5f5; margin-bottom: 4px; border-radius: 3px;">
                            <div style="font-weight: 500; color: #555;">🔲 Item ${idx + 1}</div>
                            ${this._renderTestDataAsTree(item, `${path}[${idx}]`, depth + 2)}
                        </div>
                    `)}
                </div>
            `;
        }

        // Sort entries alphabetically by key
        const sortedEntries = Object.entries(obj).sort((a, b) => a[0].localeCompare(b[0]));

        return html`
            <div style="margin-left: ${depth * 20}px;">
                ${sortedEntries.map(([key, value]) => html`
                    <div style="margin: 4px 0; padding: 4px; background: #fafafa; border-radius: 3px;">
                        <div style="font-weight: 500; color: #333;">${key}:</div>
                        <div style="margin-left: 12px;">
                            ${typeof value === 'object' && value !== null
                                ? this._renderTestDataAsTree(value, `${path}.${key}`, depth + 1)
                                : html`<span style="color: #666; font-family: monospace;">${value === null ? '(leer)' : String(value)}</span>`
                            }
                        </div>
                    </div>
                `)}
            </div>
        `;
    }

    _renderTestDataForm() {
        if (!this._selectedTemplate?.validationResult?.schema) {
            return html`<div class="panel" style="margin-bottom: 24px;">
                <p style="color: #888;">Kein Schema definiert</p>
            </div>`;
        }

        const schema = this._selectedTemplate.validationResult.schema;

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

                    <!-- Render tree structure from schema -->
                    ${Object.entries(schema.properties || {}).map(([fieldName, fieldSchema]) =>
                        this._renderSchemaProperty(fieldName, fieldSchema, fieldName, 0)
                    )}

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

    async _selectTestDataForPreview(testData) {
        this._selectedTestDataForPreview = testData;
        this._jsonText = JSON.stringify(testData.testData, null, 2);
        this._jsonValid = true;
        await this._renderPdf();
    }

    async _saveExpectedPdfForTestData(testData) {
        this._selectedTestData = testData;
        this._jsonText = JSON.stringify(testData.testData, null, 2);
        this._jsonValid = true;
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

    async _selectTemplate(template) {
        this._selectedTemplate = template;
        this._searchText = '';
        this._showSuggestions = false;
        this._error = '';
        this._success = '';
        this._activeTab = 'testdata';

        // Load full template details (with validationResult) for form generation
        try {
            const response = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${template.id}/details`
            );
            if (response.ok) {
                const details = await response.json();
                // Merge details with summary to get validationResult
                this._selectedTemplate = {
                    ...this._selectedTemplate,
                    validationResult: details.validationResult
                };

                // Generate sample JSON from schema
                if (details.validationResult?.schema?.properties) {
                    // DEBUG: Log schema to see if arrays are correct
                    console.log('Template schema:', JSON.stringify(details.validationResult.schema, null, 2));
                    this._jsonText = this._generateSampleJsonFromSchema(details.validationResult.schema);
                    console.log('Generated sample JSON:', this._jsonText);
                    this._jsonValid = true;
                }
            }
        } catch (err) {
            console.warn('Fehler beim Laden der Template-Details:', err);
            // Continue even if details fail to load
        }

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
            this._uploadName = '';
            this._uploadFile = null;
            this._uploadMode = false;

            await this._loadTemplates();
            const created = this._templates.find(t => t.id === result.id);
            if (created) {
                // Wait for template to be fully loaded (with schema)
                await this._selectTemplate(created);

                // Auto-create "default" test data set with generated sample JSON
                await this._createDefaultTestDataAfterUpload();

                this._success = `Template '${result.name}' gespeichert und Testfall "default" erstellt`;
            }
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
            this._selectedTestDataForPreview = null;
            this._pdfUrl = null;
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
        // Form is now generated on-the-fly in _renderTestDataForm using schema
        // This method is kept for backward compatibility but does nothing
    }

    /**
     * Map JSON-Schema type to HTML input type.
     */
    _getInputType(schemaType) {
        switch (schemaType) {
            case 'number':
            case 'integer':
                return 'number';
            case 'boolean':
                return 'checkbox';
            case 'string':
            default:
                return 'text';
        }
    }

    /**
     * Generate sample JSON data from JSON-Schema structure.
     * Creates example values for each field based on its type.
     */
    _generateSampleJsonFromSchema(schema) {
        if (!schema || !schema.properties) {
            return '{}';
        }

        const generateValue = (fieldSchema, fieldName) => {
            if (!fieldSchema) return null;

            // Check if there's a default value from the ODT
            if ('default' in fieldSchema) {
                return fieldSchema.default;
            }

            if (fieldSchema.type === 'object') {
                // Nested object
                if (fieldSchema.properties) {
                    const obj = {};
                    for (const [key, propSchema] of Object.entries(fieldSchema.properties)) {
                        const value = generateValue(propSchema, key);
                        if (value !== null) obj[key] = value;
                    }
                    return obj;
                }
                return {};
            } else if (fieldSchema.type === 'array') {
                // Array - return single example item
                if (fieldSchema.items) {
                    const item = generateValue(fieldSchema.items, 'item');
                    return item !== null ? [item] : [];
                }
                return [];
            } else if (fieldSchema.type === 'number' || fieldSchema.type === 'integer') {
                return 0;
            } else if (fieldSchema.type === 'boolean') {
                return false;
            } else {
                // Default string with field name as example (only if no default value)
                return `${fieldName}_example`;
            }
        };

        const result = {};
        for (const [fieldName, fieldSchema] of Object.entries(schema.properties)) {
            const value = generateValue(fieldSchema, fieldName);
            if (value !== null) result[fieldName] = value;
        }

        return JSON.stringify(result, null, 2);
    }

    /**
     * Get nested value from object using dot-notation path.
     * Example: _getNestedValue({a: {b: 5}}, 'a.b') returns 5
     */
    _getNestedValue(obj, path) {
        if (!obj || !path) return null;
        const parts = path.split('.');
        let value = obj;
        for (const part of parts) {
            if (value && typeof value === 'object') {
                value = value[part];
            } else {
                return null;
            }
        }
        return value;
    }

    /**
     * Set nested value in object using dot-notation path.
     * Creates intermediate objects as needed.
     */
    _setNestedValue(obj, path, value) {
        const parts = path.split('.');
        let current = obj;
        for (let i = 0; i < parts.length - 1; i++) {
            if (!current[parts[i]]) {
                current[parts[i]] = {};
            }
            current = current[parts[i]];
        }
        current[parts[parts.length - 1]] = value;
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
        if (this._selectedTemplate) {
            this._loadTestDataSets();
            this._generateFormFromTemplate();
        }
    }

    // --- Tree Structure Rendering ---

    /**
     * Recursively render schema property as tree node (object, array, or leaf field).
     * @param fieldName - Display name of field
     * @param fieldSchema - JSON Schema for this field
     * @param path - Dot-notation path to field in data object
     * @param depth - Nesting level for indentation
     */
    _renderSchemaProperty(fieldName, fieldSchema, path, depth = 0) {
        if (!fieldSchema) return '';

        if (fieldSchema.type === 'object') {
            return this._renderObjectField(fieldName, fieldSchema, path, depth);
        } else if (fieldSchema.type === 'array') {
            return this._renderArrayField(fieldName, fieldSchema, path, depth);
        } else {
            return this._renderLeafField(fieldName, fieldSchema, path, depth);
        }
    }

    /**
     * Render a nested object with its child properties.
     */
    _renderObjectField(fieldName, fieldSchema, path, depth) {
        const indent = depth * 20;
        return html`
            <div class="tree-node" style="margin-left: ${indent}px;">
                <div class="tree-node-header">📁 ${fieldName}</div>
                ${Object.entries(fieldSchema.properties || {}).map(([childName, childSchema]) =>
                    this._renderSchemaProperty(childName, childSchema, `${path}.${childName}`, depth + 1)
                )}
            </div>
        `;
    }

    /**
     * Render an array field with items and add/remove buttons.
     */
    _renderArrayField(fieldName, fieldSchema, path, depth) {
        const indent = depth * 20;
        const targetData = this._editingTestDataId ? this._editingTestDataFormData : this._testDataFormData;
        const currentArray = this._getNestedValue(targetData, path) || [];

        return html`
            <div class="tree-node" style="margin-left: ${indent}px;">
                <div class="tree-array-header">
                    📁 ${fieldName}
                    <button class="btn-add-item" @click=${() => this._addArrayItem(path, fieldSchema.items)}>
                        + Hinzufügen
                    </button>
                </div>

                ${Array.isArray(currentArray) ? currentArray.map((item, index) => html`
                    <div class="array-item" style="margin-left: ${(depth + 1) * 20}px;">
                        <div class="array-item-header">
                            🔲 Item ${index + 1}
                            <button class="btn-remove-item" @click=${() => this._removeArrayItem(path, index)}>
                                ✕
                            </button>
                        </div>

                        ${Object.entries(fieldSchema.items?.properties || {}).map(([childName, childSchema]) =>
                            this._renderSchemaProperty(childName, childSchema, `${path}[${index}].${childName}`, depth + 2)
                        )}
                    </div>
                `) : ''}
            </div>
        `;
    }

    /**
     * Render a leaf field (text, number, checkbox, etc.).
     */
    _renderLeafField(fieldName, fieldSchema, path, depth) {
        const indent = depth * 20;
        const targetData = this._editingTestDataId ? this._editingTestDataFormData : this._testDataFormData;
        const currentValue = this._getNestedValue(targetData, path);
        const inputType = this._getInputType(fieldSchema.type);
        const isChecked = inputType === 'checkbox' && currentValue === true;

        return html`
            <div class="tree-field-group" style="margin-left: ${indent}px;">
                <label style="display: block; font-size: 13px; margin-bottom: 6px;">
                    ${fieldName}
                    ${fieldSchema.type === 'boolean' ? '' : ''}
                </label>
                ${inputType === 'checkbox'
                    ? html`<input type="checkbox"
                        name="${path}"
                        ?checked=${isChecked}
                        @change=${this._onTestDataInput.bind(this)}>`
                    : inputType === 'number'
                    ? html`<input type="number"
                        name="${path}"
                        .value=${currentValue !== undefined ? currentValue : ''}
                        placeholder="${fieldName}"
                        @input=${this._onTestDataInput.bind(this)}
                        style="width: 100%; padding: 6px; border: 1px solid #ccc; border-radius: 3px;">`
                    : html`<input type="text"
                        name="${path}"
                        .value=${currentValue !== undefined ? currentValue : ''}
                        placeholder="${fieldName}"
                        @input=${this._onTestDataInput.bind(this)}
                        style="width: 100%; padding: 6px; border: 1px solid #ccc; border-radius: 3px;">`
                }
            </div>
        `;
    }

    /**
     * Add a new item to an array.
     * Works with both _testDataFormData and _editingTestDataFormData
     */
    _addArrayItem(arrayPath, itemSchema) {
        const targetData = this._editingTestDataId ? this._editingTestDataFormData : this._testDataFormData;
        const currentArray = this._getNestedValue(targetData, arrayPath) || [];
        const newItem = this._createEmptyItem(itemSchema);

        this._setNestedValue(targetData, arrayPath, [...currentArray, newItem]);

        if (this._editingTestDataId) {
            this._editingTestDataFormData = { ...this._editingTestDataFormData };
        } else {
            this._testDataFormData = { ...this._testDataFormData };
        }
    }

    /**
     * Remove an item from an array by index.
     * Works with both _testDataFormData and _editingTestDataFormData
     */
    _removeArrayItem(arrayPath, index) {
        const targetData = this._editingTestDataId ? this._editingTestDataFormData : this._testDataFormData;
        const currentArray = this._getNestedValue(targetData, arrayPath) || [];
        const updated = currentArray.filter((_, i) => i !== index);

        this._setNestedValue(targetData, arrayPath, updated);

        if (this._editingTestDataId) {
            this._editingTestDataFormData = { ...this._editingTestDataFormData };
        } else {
            this._testDataFormData = { ...this._testDataFormData };
        }
    }

    /**
     * Create an empty item based on schema for array initialization.
     */
    _createEmptyItem(itemSchema) {
        if (!itemSchema) return {};

        if (itemSchema.type === 'object') {
            const obj = {};
            for (const [key, schema] of Object.entries(itemSchema.properties || {})) {
                if (schema.default !== undefined) {
                    obj[key] = schema.default;
                } else if (schema.type === 'number' || schema.type === 'integer') {
                    obj[key] = 0;
                } else if (schema.type === 'boolean') {
                    obj[key] = false;
                } else if (schema.type === 'array') {
                    obj[key] = [];
                } else if (schema.type === 'object') {
                    obj[key] = {};
                } else {
                    obj[key] = '';
                }
            }
            return obj;
        }
        return '';
    }

    /**
     * Enhanced _onTestDataInput to handle both dot-notation and array index paths.
     * Examples: 'customer.firstname' or 'positions[0].name'
     * Works with both _testDataFormData and _editingTestDataFormData
     */
    _onTestDataInput(e) {
        const name = e.target.name;
        const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;

        // Parse path that may contain array indices: "positions[0].name" -> ["positions", 0, "name"]
        const pathParts = [];
        const regex = /(\w+)|\[(\d+)\]/g;
        let match;
        while ((match = regex.exec(name)) !== null) {
            if (match[1]) {
                pathParts.push(match[1]);
            } else if (match[2]) {
                pathParts.push(parseInt(match[2]));
            }
        }

        // Determine which data object to update (editing or creating)
        const targetData = this._editingTestDataId ? this._editingTestDataFormData : this._testDataFormData;

        // Navigate to the target object and set value
        let obj = targetData;
        for (let i = 0; i < pathParts.length - 1; i++) {
            const part = pathParts[i];
            const nextPart = pathParts[i + 1];

            if (typeof nextPart === 'number') {
                // Next part is array index - ensure current part is array
                if (!Array.isArray(obj[part])) {
                    obj[part] = [];
                }
                obj = obj[part];
            } else {
                // Regular object property
                if (typeof obj[part] !== 'object' || obj[part] === null) {
                    obj[part] = {};
                }
                obj = obj[part];
            }
        }

        const lastPart = pathParts[pathParts.length - 1];
        obj[lastPart] = value;

        // Update the corresponding reactive property
        if (this._editingTestDataId) {
            this._editingTestDataFormData = { ...this._editingTestDataFormData };
        } else {
            this._testDataFormData = { ...this._testDataFormData };
        }
    }

    /**
     * Create "default" test data set with the generated sample JSON after upload.
     */
    async _createDefaultTestDataAfterUpload() {
        if (!this._selectedTemplate) {
            console.warn('Cannot create default test data: template not set');
            return;
        }

        if (!this._jsonValid) {
            console.warn('Cannot create default test data: JSON not valid');
            return;
        }

        if (!this._jsonText) {
            console.warn('Cannot create default test data: no JSON text available');
            return;
        }

        try {
            // Parse the generated sample JSON
            let testData;
            try {
                testData = JSON.parse(this._jsonText);
            } catch (parseErr) {
                console.error('Invalid JSON in _jsonText:', parseErr);
                this._error = `JSON Parse Error: ${parseErr.message}`;
                return;
            }

            console.log('Creating default test data with:', testData);

            const response = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${this._selectedTemplate.id}/testdata`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        name: 'default',
                        testData: testData
                    })
                }
            );

            if (!response.ok) {
                const errorText = await response.text();
                console.error(`Failed to create default test data: ${response.status} - ${errorText}`);
                this._error = `Fehler beim Erstellen des default Testfalls: ${response.status}`;
                return;
            }

            // Reload test data sets
            await this._loadTestDataSets();
            console.log('✓ Default test data created successfully');
        } catch (err) {
            console.error('Could not create default test data:', err);
            this._error = `Fehler: ${err.message}`;
        }
    }


    /**
     * Generate default test data object from schema.
     * Reuses _generateSampleJsonFromSchema but returns object instead of string.
     */
    _generateDefaultTestData(schema) {
        const jsonString = this._generateSampleJsonFromSchema(schema);
        try {
            return JSON.parse(jsonString);
        } catch {
            return {};
        }
    }

    /**
     * Duplicate an existing test data set.
     */
    async _duplicateTestData(testData) {
        // Prompt for new name
        const newName = prompt('Name für Duplikat:', `Kopie von ${testData.name}`);
        if (!newName || !newName.trim()) return;

        try {
            // Fetch full test data
            const response = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${this._selectedTemplate.id}/testdata/${testData.id}`
            );
            if (!response.ok) throw new Error('Fehler beim Laden der Testdaten');
            const fullTestData = await response.json();

            // Create new test data with same data but different name
            const createResponse = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${this._selectedTemplate.id}/testdata`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        name: newName.trim(),
                        testData: fullTestData.testData
                    })
                }
            );

            if (!createResponse.ok) throw new Error('Fehler beim Duplizieren');

            this._success = `TestDataSet '${newName}' erstellt`;
            await this._loadTestDataSets();
        } catch (err) {
            this._error = err.message;
        }
    }

    /**
     * Toggle expand/collapse of a test data item.
     */
    _toggleExpandTestData(testDataId) {
        if (this._expandedTestDataId === testDataId) {
            this._expandedTestDataId = null;
            this._editingTestDataId = null;
        } else {
            this._expandedTestDataId = testDataId;
            this._editingTestDataId = null;
        }
    }

    /**
     * Save edited test data.
     */
    async _saveTestDataEdits(testDataId) {
        if (!this._selectedTemplate) return;

        this._savingTestData = true;
        this._error = '';

        try {
            const response = await fetch(
                `${this._getApiBase()}/api/workbench/templates/${this._selectedTemplate.id}/testdata/${testDataId}`,
                {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        name: this._testDataSets.find(td => td.id === testDataId)?.name || 'Unnamed',
                        testData: this._editingTestDataFormData
                    })
                }
            );

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Fehler beim Speichern: ${response.status} - ${errorText || 'Unbekannter Fehler'}`);
            }

            this._success = 'Testdaten gespeichert';
            this._editingTestDataId = null;
            await this._loadTestDataSets();

            // Auto-refresh preview with updated test data
            const updated = this._testDataSets.find(td => td.id === testDataId);
            if (updated) {
                await this._selectTestDataForPreview(updated);
            }
        } catch (err) {
            this._error = err.message;
            console.error('Save error:', err);
        } finally {
            this._savingTestData = false;
        }
    }
}

customElements.define('bp-workbench', BpWorkbench);
