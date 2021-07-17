/*#######################################################
 *
 *   Maintained by Gregor Santner, 2017-
 *   https://gsantner.net/
 *
 *   License of this file: Apache 2.0 (Commercial upon request)
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
#########################################################*/
package net.gsantner.markor.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.HorizontalScrollView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import net.gsantner.markor.App;
import net.gsantner.markor.BuildConfig;
import net.gsantner.markor.R;
import net.gsantner.markor.format.TextConverter;
import net.gsantner.markor.format.TextFormat;
import net.gsantner.markor.format.general.CommonTextActions;
import net.gsantner.markor.format.general.DatetimeFormatDialog;
import net.gsantner.markor.model.Document;
import net.gsantner.markor.ui.AttachImageOrLinkDialog;
import net.gsantner.markor.ui.DraggableScrollbarScrollView;
import net.gsantner.markor.ui.DraggableScrollbarWebView;
import net.gsantner.markor.ui.FileInfoDialog;
import net.gsantner.markor.ui.FilesystemViewerCreator;
import net.gsantner.markor.ui.SearchOrCustomTextDialogCreator;
import net.gsantner.markor.ui.hleditor.HighlightingEditor;
import net.gsantner.markor.util.AppSettings;
import net.gsantner.markor.util.ContextUtils;
import net.gsantner.markor.util.DocumentIO;
import net.gsantner.markor.util.MarkorWebViewClient;
import net.gsantner.markor.util.ShareUtil;
import net.gsantner.opoc.activity.GsFragmentBase;
import net.gsantner.opoc.preference.FontPreferenceCompat;
import net.gsantner.opoc.ui.FilesystemViewerData;
import net.gsantner.opoc.util.ActivityUtils;
import net.gsantner.opoc.util.CoolExperimentalStuff;
import net.gsantner.opoc.util.TextViewUndoRedo;

import java.io.File;
import java.util.Arrays;

import butterknife.BindView;
import butterknife.OnTextChanged;
import other.writeily.widget.WrMarkorWidgetProvider;

@SuppressWarnings({"UnusedReturnValue", "RedundantCast"})
@SuppressLint("NonConstantResourceId")
public class DocumentEditFragment extends GsFragmentBase implements TextFormat.TextFormatApplier {
    public static final int HISTORY_DELTA = 5000;
    public static final String FRAGMENT_TAG = "DocumentEditFragment";
    private static final String SAVESTATE_DOCUMENT = "DOCUMENT";
    private static final String SAVESTATE_PREVIEW_ON = "SAVESTATE_PREVIEW_ON";

    private AppSettings _appSettings;
    private HorizontalScrollView hsView;

    // Wrap text setting and wrap text state are separated as the wrap text state may depend on
    // if the file is in the main activity (quicknote and todotxt). Documents in mainactivity
    // will _always_ open wrapped, but can be explicitly be set to unwrapped through the menu.
    // Toggling the wrap state option will set and save the new value, but the file will always
    // open wrapped in the main activity.
    private boolean wrapTextSetting;
    private boolean wrapText;
    private boolean highlightText;

    public static DocumentEditFragment newInstance(Document document) {
        DocumentEditFragment f = new DocumentEditFragment();
        Bundle args = new Bundle();
        args.putSerializable(DocumentIO.EXTRA_DOCUMENT, document);
        f.setArguments(args);
        return f;
    }

    public static DocumentEditFragment newInstance(File path, boolean pathIsFolder, final int lineNumber) {
        DocumentEditFragment f = new DocumentEditFragment();
        Bundle args = new Bundle();
        args.putSerializable(DocumentIO.EXTRA_PATH, path);
        args.putBoolean(DocumentIO.EXTRA_PATH_IS_FOLDER, pathIsFolder);
        args.putInt(DocumentIO.EXTRA_FILE_LINE_NUMBER, lineNumber);
        f.setArguments(args);
        return f;
    }


    @BindView(R.id.document__fragment__edit__highlighting_editor)
    HighlightingEditor _hlEditor;

    @BindView(R.id.document__fragment__edit__text_actions_bar)
    ViewGroup _textActionsBar;

    @BindView(R.id.document__fragment__edit__content_editor__permission_warning)
    TextView _textSdWarning;

    @BindView(R.id.document__fragment_view_webview)
    DraggableScrollbarWebView _webView;

    @BindView(R.id.document__fragment__edit__content_editor__scrolling_parent)
    DraggableScrollbarScrollView _primaryScrollView;

    private SearchView _menuSearchViewForViewMode;
    private Document _document;
    private TextFormat _textFormat;
    private ShareUtil _shareUtil;
    private TextViewUndoRedo _editTextUndoRedoHelper;
    private boolean _isPreviewVisible;
    private MarkorWebViewClient _webViewClient;
    private boolean _nextConvertToPrintMode = false;
    private boolean _firstFileLoad = true;

    public DocumentEditFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        _appSettings = new AppSettings(getContext());
        if (_appSettings.getSetWebViewFulldrawing() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw();
        }
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.document__fragment__edit;
    }

    @SuppressLint({"SetJavaScriptEnabled", "WrongConstant"})
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //applyTextFormat(TextFormat.FORMAT_PLAIN);
        _shareUtil = new ShareUtil(view.getContext());

        _webViewClient = new MarkorWebViewClient(getActivity());
        _webView.setBackgroundColor(ContextCompat.getColor(view.getContext(), _appSettings.isDarkThemeEnabled() ? R.color.dark__background : R.color.light__background));
        _webView.setWebViewClient(_webViewClient);
        WebSettings webSettings = _webView.getSettings();
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setTextZoom((int) (_appSettings.getViewFontSize() / 15.7f * 100f));
        webSettings.setAppCacheEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setGeolocationEnabled(false);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && BuildConfig.IS_TEST_BUILD) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(SAVESTATE_DOCUMENT)) {
            _document = (Document) savedInstanceState.getSerializable(SAVESTATE_DOCUMENT);
        }
        _document = loadDocument();
        loadDocumentIntoUi();
        setupAppearancePreferences(view);

        if (savedInstanceState != null && savedInstanceState.containsKey(SAVESTATE_PREVIEW_ON)) {
            _isPreviewVisible = savedInstanceState.getBoolean(SAVESTATE_PREVIEW_ON, _isPreviewVisible);
        }

        final Toolbar toolbar = getToolbar();
        if (toolbar != null) {
            toolbar.setOnLongClickListener(_longClickToTopOrBottom);
        }

        // Do not need to send contents to accessibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            _hlEditor.setImportantForAccessibility(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
        _editTextUndoRedoHelper = new TextViewUndoRedo(_hlEditor);
        _hlEditor.setLineSpacing(0, _appSettings.getEditorLineSpacing());
    }

    @Override
    public void onResume() {
        super.onResume();
        checkReloadDisk(false);

        _hlEditor.setGravity(_appSettings.isEditorStartEditingInCenter() ? Gravity.CENTER : Gravity.NO_GRAVITY);
        if (_document != null && _document.getFile() != null) {
            if (!_document.getFile().getParentFile().exists()) {
                //noinspection ResultOfMethodCallIgnored
                _document.getFile().getParentFile().mkdirs();
            }
            boolean permok = _shareUtil.canWriteFile(_document.getFile(), false);
            if (!permok && !_document.getFile().isDirectory() && _shareUtil.canWriteFile(_document.getFile(), _document.getFile().isDirectory())) {
                permok = true;
            }
            if (_shareUtil.isUnderStorageAccessFolder(_document.getFile()) && _shareUtil.getStorageAccessFrameworkTreeUri() == null) {
                _shareUtil.showMountSdDialog(getActivity());
                return;
            }
            _textSdWarning.setVisibility(permok ? View.GONE : View.VISIBLE);
        }

        /*if (_savedInstanceState != null && _savedInstanceState.containsKey("undoredopref")) {
            _hlEditor.postDelayed(() -> {
                SharedPreferences sp = getContext().getSharedPreferences("unforedopref", 0);
                _editTextUndoRedoHelper.restorePersistentState(sp, _editTextUndoRedoHelper.undoRedoPrefKeyForFile(_document.getFile()));
            }, 100);
        }*/

        if (_document != null && _document.getFile() != null && _document.getFile().getAbsolutePath().contains("mordor/1-epub-experiment.md") && getActivity() instanceof DocumentActivity) {
            _hlEditor.setText(CoolExperimentalStuff.convertEpubToText(_document.getFile(), getString(R.string.page)));
        }

        // Set initial wrap state
        initDocState();

        if (isDisplayedAtMainActivity()) {
            if (isQuickNoteOrTodoDocument()) {
                if (!_hlEditor.hasFocus()) {
                    _hlEditor.requestFocus();
                }
                _hlEditor.setSelection(_hlEditor.length());
                new ActivityUtils(getActivity()).showSoftKeyboard(_hlEditor).freeContextRef();
            }
        } else {
            restoreDocumentPositions();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.document__edit__menu, menu);
        ContextUtils cu = ContextUtils.get();
        cu.tintMenuItems(menu, true, Color.WHITE);
        cu.setSubMenuIconsVisiblity(menu, true);

        menu.findItem(R.id.action_undo).setVisible(_appSettings.isEditorHistoryEnabled());
        menu.findItem(R.id.action_redo).setVisible(_appSettings.isEditorHistoryEnabled());
        menu.findItem(R.id.action_send_debug_log).setVisible(MainActivity.IS_DEBUG_ENABLED && getActivity() instanceof DocumentActivity && !_isPreviewVisible);

        final boolean canUndo = _editTextUndoRedoHelper.getCanUndo();
        final boolean canRedo = _editTextUndoRedoHelper.getCanRedo();
        final boolean isExperimentalFeaturesEnabled = _appSettings.isExperimentalFeaturesEnabled();

        // Undo / Redo / Save (keep visible, but deactivated and tinted grey if not executable)
        Drawable drawable;
        drawable = menu.findItem(R.id.action_undo).setEnabled(canUndo).setVisible(!_isPreviewVisible).getIcon();
        drawable.mutate().setAlpha(canUndo ? 255 : 40);
        drawable = menu.findItem(R.id.action_redo).setEnabled(canRedo).setVisible(!_isPreviewVisible).getIcon();
        drawable.mutate().setAlpha(canRedo ? 255 : 40);

        // Edit / Preview switch
        menu.findItem(R.id.action_edit).setVisible(_isPreviewVisible);
        menu.findItem(R.id.submenu_attach).setVisible(false);
        menu.findItem(R.id.action_preview).setVisible(!_isPreviewVisible);
        menu.findItem(R.id.action_search).setVisible(!_isPreviewVisible);
        menu.findItem(R.id.action_search_view).setVisible(_isPreviewVisible);
        menu.findItem(R.id.submenu_format_selection).setVisible(!_isPreviewVisible);

        menu.findItem(R.id.action_share_pdf).setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);
        menu.findItem(R.id.action_share_image).setVisible(true);
        menu.findItem(R.id.action_load_epub).setVisible(isExperimentalFeaturesEnabled);

        // SearchView (View Mode)
        _menuSearchViewForViewMode = (SearchView) menu.findItem(R.id.action_search_view).getActionView();
        _menuSearchViewForViewMode.setSubmitButtonEnabled(true);
        _menuSearchViewForViewMode.setQueryHint(getString(R.string.search));
        _menuSearchViewForViewMode.setOnQueryTextFocusChangeListener((v, searchHasFocus) -> {
            if (!searchHasFocus) {
                _menuSearchViewForViewMode.setQuery("", false);
                _menuSearchViewForViewMode.setIconified(true);
            }
        });
        _menuSearchViewForViewMode.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String text) {
                _webView.findNext(true);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String text) {
                _webView.findAllAsync(text);
                return true;
            }
        });

        updateMenuToggleStates(_document.getFormat());
    }

    public void loadDocumentIntoUi() {
        int editorpos = _hlEditor.getSelectionStart();
        _hlEditor.setText(_document.getContent());
        editorpos = editorpos > _hlEditor.length() ? _hlEditor.length() - 1 : editorpos;
        _hlEditor.setSelection(Math.max(editorpos, 0));
        Activity activity = getActivity();
        if (activity instanceof DocumentActivity) {
            DocumentActivity da = ((DocumentActivity) activity);
            da.setDocumentTitle(_document.getTitle());
            da.setDocument(_document);
        }
        // At this stage the document format has been determined from extension etc
        // Here we replace it with the last saved format.
        _document.setFormat(_appSettings.getDocumentFormat(getPath(), _document.getFormat()));
        applyTextFormat(_document.getFormat());
        _textFormat.getTextActions().setDocument(_document);

        setDocumentViewVisibility(_isPreviewVisible);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item == null) {
            return true;
        }
        _shareUtil.setContext(getActivity());
        final int itemId = item.getItemId();
        switch (itemId) {
            case R.id.action_undo: {
                if (_editTextUndoRedoHelper.getCanUndo()) {
                    _hlEditor.disableHighlighterAutoFormat();
                    _editTextUndoRedoHelper.undo();
                    _hlEditor.enableHighlighterAutoFormat();
                    ((AppCompatActivity) getActivity()).supportInvalidateOptionsMenu();
                }
                return true;
            }
            case R.id.action_redo: {
                if (_editTextUndoRedoHelper.getCanRedo()) {
                    _hlEditor.disableHighlighterAutoFormat();
                    _editTextUndoRedoHelper.redo();
                    _hlEditor.enableHighlighterAutoFormat();
                    ((AppCompatActivity) getActivity()).supportInvalidateOptionsMenu();
                }
                return true;
            }
            case R.id.action_save: {
                DocumentIO.SAVE_IGNORE_EMTPY_NEXT_TIME = true;
                saveDocument();
                DocumentIO.SAVE_IGNORE_EMTPY_NEXT_TIME = false;
                return true;
            }
            case R.id.action_reload: {
                checkReloadDisk(true);
                return true;
            }
            case R.id.action_preview: {
                saveDocumentPositions();
                setDocumentViewVisibility(true);
                restoreDocumentPositions();
                return true;
            }
            case R.id.action_edit: {
                saveDocumentPositions();
                setDocumentViewVisibility(false);
                restoreDocumentPositions();
                return true;
            }
            case R.id.action_preview_edit_toggle: {
                saveDocumentPositions();
                setDocumentViewVisibility(!_isPreviewVisible);
                restoreDocumentPositions();
                return true;
            }
            case R.id.action_add_shortcut_launcher_home: {
                _shareUtil.createLauncherDesktopShortcut(_document);
                return true;
            }
            case R.id.action_share_text: {
                if (saveDocument()) {
                    _shareUtil.shareText(_document.getContent(), "text/plain");
                }
                return true;
            }
            case R.id.action_share_file: {
                if (saveDocument()) {
                    _shareUtil.shareStream(_document.getFile(), "text/plain");
                }
                return true;
            }
            case R.id.action_share_html:
            case R.id.action_share_html_source: {
                if (saveDocument()) {
                    TextConverter converter = TextFormat.getFormat(_document.getFormat(), getActivity(), _document, _hlEditor).getConverter();
                    _shareUtil.shareText(converter.convertMarkup(_document.getContent(), _hlEditor.getContext(), false, _document.getFile()),
                            "text/" + (item.getItemId() == R.id.action_share_html ? "html" : "plain"));
                }
                return true;
            }
            case R.id.action_share_calendar_event: {
                if (saveDocument()) {
                    if (!_shareUtil.createCalendarAppointment(_document.getTitle(), _document.getContent(), null)) {
                        Toast.makeText(getActivity(), R.string.no_calendar_app_is_installed, Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
            case android.R.id.home: {
                final Activity activity = getActivity();
                new ActivityUtils(getActivity()).hideSoftKeyboard().freeContextRef();
                if ((saveDocument() || (_hlEditor.length() < 10 && TextUtils.getTrimmedLength(_hlEditor.getEditableText()) == 0)) && activity != null) {
                    activity.onBackPressed();
                }
                return true;
            }
            case R.id.action_share_screenshot:
            case R.id.action_share_image:
            case R.id.action_share_pdf: {
                _appSettings.getSetWebViewFulldrawing(true);
                if (saveDocument()) {
                    _nextConvertToPrintMode = true;
                    setDocumentViewVisibility(true);
                    Toast.makeText(getActivity(), R.string.please_wait, Toast.LENGTH_LONG).show();
                    _webView.postDelayed(() -> {
                        if (item.getItemId() == R.id.action_share_pdf && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            _shareUtil.printOrCreatePdfFromWebview(_webView, _document, _document.getContent().contains("beamer\n"));
                        } else if (item.getItemId() != R.id.action_share_pdf) {
                            _shareUtil.shareImage(net.gsantner.opoc.util.ShareUtil.getBitmapFromWebView(_webView, item.getItemId() == R.id.action_share_image));
                        }
                    }, 7000);
                }

                return true;
            }
            case R.string.action_format_zimwiki:
            case R.string.action_format_keyvalue:
            case R.string.action_format_todotxt:
            case R.string.action_format_plaintext:
            case R.string.action_format_markdown: {
                if (_document != null) {
                    _document.setFormat(itemId);
                    applyTextFormat(itemId);
                    _appSettings.setDocumentFormat(getPath(), _document.getFormat());
                }
                return true;
            }
            case R.id.action_search: {
                setDocumentViewVisibility(false);
                _textFormat.getTextActions().runAction(CommonTextActions.ACTION_SEARCH);
                return true;
            }
            case R.id.action_send_debug_log: {
                final String text = AppSettings.getDebugLog() + "\n\n------------------------\n\n\n\n" + DocumentIO.getMaskedContent(_document);
                _shareUtil.draftEmail("Debug Log " + getString(R.string.app_name_real), text, "debug@localhost.lan");
                return true;
            }

            case R.id.action_attach_color: {
                new CommonTextActions(getActivity(), _hlEditor).runAction(CommonTextActions.ACTION_COLOR_PICKER);
                return true;
            }
            case R.id.action_attach_date: {
                DatetimeFormatDialog.showDatetimeFormatDialog(getActivity(), _hlEditor);
                return true;
            }
            case R.id.action_attach_audio:
            case R.id.action_attach_file:
            case R.id.action_attach_image:
            case R.id.action_attach_link: {
                int actionId = (itemId == R.id.action_attach_audio ? 4 : (itemId == R.id.action_attach_image ? 2 : 3));
                AttachImageOrLinkDialog.showInsertImageOrLinkDialog(actionId, _document.getFormat(), getActivity(), _hlEditor, _document.getFile());
                return true;
            }

            case R.id.action_load_epub: {
                FilesystemViewerCreator.showFileDialog(new FilesystemViewerData.SelectionListenerAdapter() {
                                                           @Override
                                                           public void onFsViewerSelected(String request, File file, final Integer lineNumber) {
                                                               _hlEditor.setText(CoolExperimentalStuff.convertEpubToText(file, getString(R.string.page)));
                                                           }

                                                           @Override
                                                           public void onFsViewerConfig(FilesystemViewerData.Options dopt) {
                                                               dopt.titleText = R.string.select;
                                                           }
                                                       }, getFragmentManager(), getActivity(),
                        input -> input != null && input.getAbsolutePath().toLowerCase().endsWith(".epub")
                );
                return true;
            }
            case R.id.action_speed_read: {
                CoolExperimentalStuff.showSpeedReadDialog(getActivity(), _document.getContent());
                return true;
            }
            case R.id.action_wrap_words: {
                wrapText = !wrapText;
                wrapTextSetting = wrapText;
                _appSettings.setDocumentWrapState(getPath(), wrapTextSetting);
                setHorizontalScrollMode(wrapText);
                updateMenuToggleStates(0);
                return true;
            }
            case R.id.action_enable_highlighting: {
                highlightText = !highlightText;
                _hlEditor.setHighlightingEnabled(highlightText);
                _appSettings.setDocumentHighlightState(getPath(), highlightText);
                updateMenuToggleStates(0);
                return true;
            }
            case R.id.action_info: {
                if (_document != null && _document.getFile() != null) {
                    saveDocument(); // In order to have the correct info displayed
                    FileInfoDialog.show(_document.getFile(), getFragmentManager());
                }
                return true;
            }
            case R.id.action_set_font_size: {
                SearchOrCustomTextDialogCreator.showFontSizeDialog(getActivity(), _appSettings.getDocumentFontSize(getPath()), (newSize) -> {
                    _hlEditor.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float) newSize);
                    _appSettings.setDocumentFontSize(getPath(), newSize);
                });
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private long _lastChangedThreadStart = 0;

    @OnTextChanged(value = R.id.document__fragment__edit__highlighting_editor, callback = OnTextChanged.Callback.TEXT_CHANGED)
    public void onContentEditValueChanged(CharSequence text) {
        if ((_lastChangedThreadStart + HISTORY_DELTA) < System.currentTimeMillis()) {
            _lastChangedThreadStart = System.currentTimeMillis();
            _hlEditor.postDelayed(() -> {
                _document.setContent(text.toString());
                Activity activity = getActivity();
                if (activity instanceof AppCompatActivity) {
                    ((AppCompatActivity) activity).supportInvalidateOptionsMenu();
                }
            }, HISTORY_DELTA);
        }
        Activity activity = getActivity();
        if (activity != null && activity instanceof AppCompatActivity) {
            ((AppCompatActivity) activity).supportInvalidateOptionsMenu();
        }

    }

    @SuppressWarnings({"ConstantConditions", "ResultOfMethodCallIgnored"})
    private Document loadDocument() {
        Document document = DocumentIO.loadDocument(getActivity(), getArguments(), _document);
        if (document != null) {
            document.setDoHistory(_appSettings.isEditorHistoryEnabled());
        }
        if (document.getHistory().isEmpty()) {
            document.forceAddNextChangeToHistory();
            document.addToHistory();
        }

        return document;
    }

    public void applyTextFormat(final int textFormatId) {
        _textActionsBar.removeAllViews();
        _textFormat = TextFormat.getFormat(textFormatId, getActivity(), _document, _hlEditor);
        _hlEditor.setHighlighter(_textFormat.getHighlighter());
        _hlEditor.enableHighlighterAutoFormat();
        _textFormat.getTextActions()
                .setHighlightingEditor(_hlEditor)
                .appendTextActionsToBar(_textActionsBar);

        updateMenuToggleStates(textFormatId);
    }

    private void setupAppearancePreferences(View fragmentView) {
        _hlEditor.setTextSize(TypedValue.COMPLEX_UNIT_SP, _appSettings.getDocumentFontSize(getPath()));
        _hlEditor.setTypeface(FontPreferenceCompat.typeface(getContext(), _appSettings.getFontFamily(), Typeface.NORMAL));

        _hlEditor.setBackgroundColor(_appSettings.getEditorBackgroundColor());
        _hlEditor.setTextColor(_appSettings.getEditorForegroundColor());
        fragmentView.findViewById(R.id.document__fragment__edit__text_actions_bar__scrolling_parent).setBackgroundColor(_appSettings.getEditorTextactionBarColor());
    }

    private void initDocState() {
        final String path = getPath();
        wrapTextSetting = _appSettings.getDocumentWrapState(path);
        wrapText = isDisplayedAtMainActivity() || wrapTextSetting;

        highlightText = _appSettings.getDocumentHighlightState(path, _hlEditor.getText());
        updateMenuToggleStates(0);

        setHorizontalScrollMode(wrapText);
        _hlEditor.setHighlightingEnabled(highlightText);
    }

    private void updateMenuToggleStates(final int selectedFormatActionId) {
        MenuItem mi;
        SubMenu su;
        if ((mi = _fragmentMenu.findItem(R.id.action_wrap_words)) != null) {
            mi.setChecked(wrapText);
        }
        if ((mi = _fragmentMenu.findItem(R.id.action_enable_highlighting)) != null) {
            mi.setChecked(highlightText);
        }

        if (selectedFormatActionId != 0 && (mi = _fragmentMenu.findItem(R.id.submenu_format_selection)) != null && (su = mi.getSubMenu()) != null) {
            for (int i = 0; i < su.size(); i++) {
                if ((mi = su.getItem(i)).getItemId() == selectedFormatActionId) {
                    mi.setChecked(true);
                }
            }
        }
    }

    private void setHorizontalScrollMode(final boolean wrap) {

        final Context context = getContext();
        if (context != null && _hlEditor != null) {
            _primaryScrollView.removeAllViews();
            if (hsView != null) {
                hsView.removeAllViews();
            }
            if (!wrap) {
                _hlEditor.setHorizontallyScrolling(true);
                if (hsView == null) {
                    hsView = new HorizontalScrollView(context);
                    hsView.setFillViewport(true);
                }
                hsView.addView(_hlEditor);
                _primaryScrollView.addView(hsView);
            } else {
                _hlEditor.setHorizontallyScrolling(false);
                _primaryScrollView.addView(_hlEditor);
            }
        }
    }

    @Override
    public String getFragmentTag() {
        return FRAGMENT_TAG;
    }

    @Override
    public boolean onBackPressed() {
        final boolean preview = (
                getActivity().getIntent().getBooleanExtra(DocumentActivity.EXTRA_DO_PREVIEW, false)
                        || _appSettings.getDocumentPreviewState(getPath())
                        || _document.getFile().getName().startsWith("index."));
        saveDocument();
        if (_isPreviewVisible && !preview) {
            setDocumentViewVisibility(false);
            return true;
        } else if (!_isPreviewVisible && preview) {
            setDocumentViewVisibility(true);
            return true;
        } else if (_menuSearchViewForViewMode != null && !_menuSearchViewForViewMode.isIconified()) {
            _menuSearchViewForViewMode.clearFocus();
            return true;
        }
        return false;
    }

    // Save the file
    // Only supports java.io.File. TODO: Android Content
    public boolean saveDocument() {
        boolean ret = false;
        if (isAdded() && _hlEditor != null && _hlEditor.getText() != null) {
            ret = DocumentIO.saveDocument(_document, _hlEditor.getText().toString(), _shareUtil, getContext());
            updateLauncherWidgets();

            if (_document != null && _document.getFile() != null) {
                _appSettings.setDocumentPreviewState(getPath(), _isPreviewVisible);
                saveDocumentPositions();
            }
        }
        return ret;
    }

    public void saveDocumentPositions() {
        if (_hlEditor != null && _webView != null && _document.getFile() != null) {
            if (_isPreviewVisible) {
                _appSettings.setLastViewPosition(_document.getFile(), _webView.getScrollX(), _webView.getScrollY());
            } else {
                _appSettings.setLastEditPosition(_document.getFile(), _hlEditor.getSelectionStart());
            }
        }
    }

    public void restoreDocumentPositions() {
        if (!Arrays.asList(_hlEditor, _webView, _document.getFile()).contains(null) && !isDisplayedAtMainActivity()) {
            int v;
            if ((v = _document.getInitialLineNumber()) >= 0) { // If Intent contains line number, jump to it
                _hlEditor.smoothMoveCursorToLine(v);
            } else { // otherwise take settings
                if (_isPreviewVisible) {
                    _webView.scrollAnimatedToXY(_appSettings.getLastViewPositionX(_document.getFile()), _appSettings.getLastViewPositionY(_document.getFile()));
                    new ActivityUtils(getActivity()).hideSoftKeyboard().freeContextRef();
                } else {
                    if ((v = _appSettings.isEditorStartOnBottom() || isQuickNoteOrTodoDocument() ? _hlEditor.length() : _appSettings.getLastEditPositionChar(_document.getFile())) >= 0) {
                        if (!_hlEditor.hasFocus()) {
                            _hlEditor.requestFocus();
                        }
                        _hlEditor.setSelection(v);
                        new ActivityUtils(getActivity()).showSoftKeyboard(_hlEditor).freeContextRef();
                    }
                }
            }
            _document.setInitialLineNumber(-1);
        }
    }

    private boolean isDisplayedAtMainActivity() {
        return getActivity() instanceof MainActivity;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        saveDocument();
        if ((_hlEditor.length() * _document.getHistory().size() * 1.05) < 9200 && false) {
            outState.putSerializable(SAVESTATE_DOCUMENT, _document);
        }
        if (getArguments() != null && _document.getFile() != null) {
            getArguments().putSerializable(DocumentIO.EXTRA_PATH, _document.getFile());
            getArguments().putSerializable(DocumentIO.EXTRA_PATH_IS_FOLDER, false);
        }
        outState.putBoolean(SAVESTATE_PREVIEW_ON, _isPreviewVisible);
        /*SharedPreferences sp = getContext().getSharedPreferences("unforedopref", 0);
        _editTextUndoRedoHelper.storePersistentState(sp.edit(), _editTextUndoRedoHelper.undoRedoPrefKeyForFile(_document.getFile()));
        outState.putString("undoredopref", "put");*/
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        super.onPause();
        saveDocument();
        if (_document != null && _document.getFile() != null) {
            _appSettings.addRecentDocument(_document.getFile());
        }
    }

    private void updateLauncherWidgets() {
        Context c = App.get().getApplicationContext();
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(c);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(c, WrMarkorWidgetProvider.class));
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_notes_list);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        final Toolbar toolbar = getToolbar();
        if (toolbar != null && isVisibleToUser) {
            toolbar.setOnLongClickListener(_longClickToTopOrBottom);
        }

        if (isVisibleToUser) {
            initDocState();
        }

        if (isVisibleToUser && (isDisplayedAtMainActivity() || isQuickNoteOrTodoDocument())) {
            checkReloadDisk(false);
            if (_hlEditor != null) {
                if (!_hlEditor.hasFocus()) {
                    _hlEditor.requestFocus();
                }
                _hlEditor.setSelection(_hlEditor.length());
                new ActivityUtils(getActivity()).showSoftKeyboard(_hlEditor).freeContextRef();
            }
        } else if (!isVisibleToUser && _document != null) {
            saveDocument();
            new ActivityUtils(getActivity()).hideSoftKeyboard().freeContextRef();
        }
        super.setUserVisibleHint(isVisibleToUser);
    }

    private void checkReloadDisk(boolean forceReload) {
        if (_firstFileLoad) {
            _firstFileLoad = false;
            return;
        }
        Document cmp = DocumentIO.loadDocument(getActivity(), getArguments(), null);
        if (forceReload || (_document != null && cmp != null && cmp.getContent() != null && !cmp.getContent().equals(_document.getContent()))) {
            _editTextUndoRedoHelper.clearHistory();
            _document = cmp;
            loadDocument();
            loadDocumentIntoUi();
        }
    }

    public void setDocumentViewVisibility(boolean show) {
        if (show) {
            _document.setContent(_hlEditor.getText().toString());
            _textFormat.getConverter().convertMarkupShowInWebView(_document, _webView, _nextConvertToPrintMode, _document.getFile());
        }
        _nextConvertToPrintMode = false;
        _webView.setAlpha(0);
        _webView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            _webView.animate().setDuration(150).alpha(1.0f).setListener(null);
        }
        _isPreviewVisible = show;
        ((AppCompatActivity) getActivity()).supportInvalidateOptionsMenu();
    }

    final View.OnLongClickListener _longClickToTopOrBottom = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (getUserVisibleHint()) {
                if (_isPreviewVisible) {
                    boolean top = _webView.getScrollY() > 100;
                    _webView.scrollTo(0, top ? 0 : _webView.getContentHeight());
                    if (!top) {
                        _webView.scrollBy(0, 1000);
                        _webView.scrollBy(0, 1000);
                    }
                } else {
                    new CommonTextActions(getActivity(), _hlEditor).runAction(CommonTextActions.ACTION_JUMP_BOTTOM_TOP);
                }
                return true;
            }
            return false;
        }
    };

    //
    //
    //
    //

    public Document getDocument() {
        return _document;
    }

    public String getPath() {
        return Document.getPath(_document);
    }

    public WebView getWebview() {
        return _webView;
    }

    public DocumentEditFragment setPreviewFlag(boolean preview) {
        _isPreviewVisible = preview;
        return this;
    }

    public void onToolbarTitleClicked(final Toolbar toolbar) {
        if (!_isPreviewVisible && _textFormat != null) {
            _textFormat.getTextActions().runAction(getString(R.string.tmaid_common_toolbar_title_clicked_edit_action));
        }
    }

    private boolean isQuickNoteOrTodoDocument() {
        return _document != null && (_document.getFile().getPath().equals(_appSettings.getTodoFile().getPath()) || _document.getFile().getPath().equals(_appSettings.getQuickNoteFile().getPath()));
    }
}
