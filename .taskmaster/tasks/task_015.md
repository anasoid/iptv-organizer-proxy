# Task ID: 15

**Title:** React Admin Panel - Filter Management & Preview

**Status:** pending

**Dependencies:** 14

**Priority:** medium

**Description:** Build filter management UI with YAML editor, syntax highlighting, validation, and filter preview functionality

**Details:**

1. Install YAML editor:
   - `npm install @monaco-editor/react` (Monaco editor)
   - Or `npm install react-codemirror2 codemirror` (CodeMirror)
2. Create filters API service: `src/services/filtersApi.ts`
   - getFilters(), createFilter(data), updateFilter(id, data), deleteFilter(id)
   - previewFilter(id, sourceId)
3. Create filters pages:
   - src/pages/Filters/FiltersList.tsx
     - DataGrid: ID, Name, Description, Created At, Actions
     - Add Filter button
     - Actions: Edit, Delete, Clone, Preview, Export
   - src/pages/Filters/FilterForm.tsx
     - Fields: Name, Description
     - YAML Editor with syntax highlighting
     - Two sections clearly separated: rules (include/exclude), favoris
     - Template buttons (preset filters):
       - "Block Adult Content"
       - "Sports Only"
       - "HD Channels Only"
       - "Kids Channels"
     - Validate YAML button (client-side validation)
     - Preview button → shows preview modal
     - Import YAML file button
     - Export YAML file button
   - src/pages/Filters/FilterPreview.tsx
     - Modal/drawer showing preview results
     - Source selector dropdown (required)
     - Tabs: Live Streams, VOD, Series
     - For each tab:
       - Show favoris virtual categories (100000+) at top
       - Show regular categories below
       - Expand category to see filtered streams
       - Highlight which rules matched each stream
     - Total counts: X streams out of Y after filtering
4. YAML editor features:
   - Syntax highlighting for YAML
   - Auto-completion for rule types (include, exclude)
   - Line numbers
   - Error highlighting for invalid YAML
   - Expandable/collapsible sections
5. Template filters:
   - Hardcode common filter YAML templates in frontend
   - User can select template and customize
6. Import/Export:
   - Import: file upload, parse YAML, populate editor
   - Export: download current YAML as .yml file
7. Clone filter:
   - Duplicate existing filter with "(Copy)" suffix
   - Open in edit mode
8. Validation:
   - Client-side: parse YAML, check structure (rules and favoris sections)
   - Server-side: validate YAML syntax and structure
   - Show validation errors in UI

**Test Strategy:**

1. Test filters list displays all filters
2. Test add filter form creates new filter
3. Test YAML editor has syntax highlighting
4. Test YAML validation catches syntax errors
5. Test template buttons populate editor correctly
6. Test preview shows filtered streams correctly
7. Test preview displays favoris categories first (100000+)
8. Test preview shows which rules matched
9. Test import YAML file loads into editor
10. Test export YAML downloads correct file
11. Test clone filter duplicates existing filter
12. Test delete filter removes filter
13. Manual testing: create complex filter and preview results
