/*#######################################################
 *
 *   Maintained by Gregor Santner, 2017-
 *   https://gsantner.net/
 *
 *   License of this file: Apache 2.0 (Commercial upon request)
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
#########################################################*/
package net.gsantner.markor.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.ColorRes;
import android.support.annotation.IdRes;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;
import android.support.v4.util.Pair;

import net.gsantner.markor.App;
import net.gsantner.markor.BuildConfig;
import net.gsantner.markor.R;
import net.gsantner.markor.format.TextFormat;
import net.gsantner.opoc.preference.SharedPreferencesPropertyBackend;
import net.gsantner.opoc.ui.FilesystemViewerAdapter;
import net.gsantner.opoc.ui.FilesystemViewerFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import other.de.stanetz.jpencconverter.PasswordStore;

@SuppressWarnings({"SameParameterValue", "WeakerAccess", "FieldCanBeLocal"})
public class AppSettings extends SharedPreferencesPropertyBackend {
    private final SharedPreferences _prefCache;
    private final SharedPreferences _prefHistory;
    public static Boolean _isDeviceGoodHardware = null;
    private final ContextUtils _contextUtils;

    private static final File LOCAL_TESTFOLDER_FILEPATH = new File("/storage/emulated/0/00_sync/documents/special");

    public AppSettings(Context _context) {
        super(_context);
        _prefCache = _context.getSharedPreferences("cache", Context.MODE_PRIVATE);
        _prefHistory = _context.getSharedPreferences("history", Context.MODE_PRIVATE);
        _contextUtils = new ContextUtils(_context);
        if (_isDeviceGoodHardware == null) {
            _isDeviceGoodHardware = _contextUtils.isDeviceGoodHardware();
        }
    }

    public static AppSettings get() {
        return new AppSettings(App.get());
    }


    public void setDarkThemeEnabled(boolean enabled) {
        setString(R.string.pref_key__app_theme, enabled ? "dark" : "light");
    }

    public boolean isDarkThemeEnabled() {
        switch (getString(R.string.pref_key__app_theme, "light")) {
            case "light": {
                return false;
            }
            case "dark": {
                return true;
            }
            case "auto":
            default: {
                return !isCurrentHourOfDayBetween(9, 17);
            }
        }
    }

    public int getBackgroundColor() {
        return isDarkThemeEnabled() ? rcolor(R.color.dark__background) : rcolor(R.color.light__background);
    }

    public boolean isLoadLastDirectoryAtStartup() {
        return getBool(R.string.pref_key__load_last_directory_at_startup, false);
    }

    public boolean isPreviewFirst() {
        return getBool(R.string.pref_key__is_preview_first, false);
    }

    public void setSaveDirectory(String value) {
        setString(R.string.pref_key__notebook_directory, value);
    }


    public File getNotebookDirectory() {
        return new File(getNotebookDirectoryAsStr());
    }

    public String getNotebookDirectoryAsStr() {
        String dir = getString(R.string.pref_key__notebook_directory, "");
        if (dir.isEmpty() && LOCAL_TESTFOLDER_FILEPATH.exists() && !BuildConfig.IS_TEST_BUILD) {
            dir = LOCAL_TESTFOLDER_FILEPATH.getParentFile().getParent();
            setSaveDirectory(dir);
        }
        if (dir.isEmpty()) {
            dir = new File(new File(Environment.getExternalStorageDirectory(), "/Documents")
                    , rstr(R.string.app_name).toLowerCase(Locale.ROOT))
                    .getAbsolutePath();
            setSaveDirectory(dir);
        }
        return dir;
    }

    public File getQuickNoteFile() {
        String defaultValue = new File(getNotebookDirectoryAsStr(), rstr(R.string.quicknote_default_filename)).getAbsolutePath();
        if (LOCAL_TESTFOLDER_FILEPATH.exists() && !BuildConfig.IS_TEST_BUILD) {
            defaultValue = new File(LOCAL_TESTFOLDER_FILEPATH, rstr(R.string.quicknote_default_filename)).getAbsolutePath();
        }
        return new File(getString(R.string.pref_key__quicknote_filepath, defaultValue));
    }

    public void setQuickNoteFile(final File file) {
        setString(R.string.pref_key__quicknote_filepath, file.getAbsolutePath());
    }

    public File getTodoFile() {
        String defaultValue = new File(getNotebookDirectoryAsStr(), rstr(R.string.todo_default_filename)).getAbsolutePath();
        if (LOCAL_TESTFOLDER_FILEPATH.exists() && !BuildConfig.IS_TEST_BUILD) {
            defaultValue = new File(LOCAL_TESTFOLDER_FILEPATH, rstr(R.string.todo_default_filename)).getAbsolutePath();
        }
        return new File(getString(R.string.pref_key__todo_filepath, defaultValue));
    }

    public void setTodoFile(File file) {
        setString(R.string.pref_key__todo_filepath, file.getAbsolutePath());
    }

    public String getFontFamily() {
        return getString(R.string.pref_key__font_family, rstr(R.string.default_font_family));
    }

    public int getFontSize() {
        return getInt(R.string.pref_key__editor_font_size, 15);
    }

    public int getViewFontSize() {
        int size = getInt(R.string.pref_key__view_font_size, -1);
        return size < 2 ? getFontSize() : size;
    }

    public boolean isHighlightingEnabled() {
        return getBool(R.string.pref_key__is_highlighting_activated, true);
    }

    public int getMarkdownHighlightingDelay() {
        return getInt(R.string.pref_key__markdown__hl_delay_v2, 650);
    }


    public boolean isMarkdownHighlightLineEnding() {
        return getBool(R.string.pref_key__markdown__highlight_lineending_two_or_more_space, false);
    }

    public boolean isMarkdownHighlightCodeFontMonospaceAllowed() {
        return getBool(R.string.pref_key__markdown__monospace_some_parts, false);
    }

    public boolean isMarkdownDisableCodeBlockHighlight() {
        return getBool(R.string.pref_key__markdown__disable_code_block_highlight, false);
    }

    public boolean isMarkdownAutoUpdateList() {
        return true;
        // return getBool(R.string.pref_key__markdown__auto_renumber_ordered_list, false);
    }

    public int getHighlightingDelayTodoTxt() {
        return getInt(R.string.pref_key__todotxt__hl_delay, 870);
    }

    public boolean isRenderRtl() {
        return getBool(R.string.pref_key__is_render_rtl, false);
    }

    public boolean isMarkdownMathEnabled() {
        return getBool(R.string.pref_key__markdown_render_math, false);
    }

    public boolean isMarkdownNewlineNewparagraphEnabled() {
        return getBool(R.string.pref_key__markdown_newline_newparagraph, false);
    }

    public boolean isMarkdownTableOfContentsEnabled() {
        return getBool(R.string.pref_key__markdown_show_toc, false);
    }

    public boolean isEditorStatusBarHidden() {
        return getBool(R.string.pref_key__is_editor_statusbar_hidden, false);
    }

    public boolean isSpecialFileLaunchersEnabled() {
        if (BuildConfig.IS_TEST_BUILD) {
            return false;
        }
        return getBool(R.string.pref_key__is_launcher_for_special_files_enabled, false);
    }

    public boolean isKeepScreenOn() {
        return getBool(R.string.pref_key__is_keep_screen_on, !BuildConfig.IS_TEST_BUILD);
    }

    public boolean isOverviewStatusBarHidden() {
        return getBool(R.string.pref_key__is_overview_statusbar_hidden, false);
    }

    public String getLanguage() {
        return getString(R.string.pref_key__language, "");
    }

    public void setRecreateMainRequired(boolean value) {
        setBool(R.string.pref_key__is_main_recreate_required, value);
    }

    public boolean isRecreateMainRequired() {
        boolean ret = getBool(R.string.pref_key__is_main_recreate_required, false);
        setBool(R.string.pref_key__is_main_recreate_required, false);
        return ret;
    }

    public void setSortMethod(int value) {
        setInt(R.string.pref_key__sort_method, value);
    }


    public int getSortMethod() {
        return getInt(R.string.pref_key__sort_method, FilesystemViewerFragment.SORT_BY_NAME);
    }

    public void setSortReverse(boolean value) {
        setBool(R.string.pref_key__sort_reverse, value);
    }

    public boolean isSortReverse() {
        return getBool(R.string.pref_key__sort_reverse, false);
    }

    public boolean isShowSettingsOptionInMainToolbar() {
        return true;//getBool(R.string.pref_key__show_settings_option_in_main_toolbar, true);
    }

    public boolean isHighlightingHexColorEnabled() {
        return getBool(R.string.pref_key__is_highlighting_for_hexcolor_activated, false);
    }

    public boolean isTodoAppendProConOnEndEnabled() {
        return getBool(R.string.pref_key__todotxt__append_contexts_and_projects_on_end_of_task, true);
    }

    public boolean isTodoStartTasksWithTodaysDateEnabled() {
        return getBool(R.string.pref_key__todotxt__start_new_tasks_with_todays_date, true);
    }

    public boolean isTodoAddCompletionDateEnabled() {
        return getBool(R.string.pref_key__todotxt__add_completion_date_for_todos, true);
    }

    public boolean isAppCurrentVersionFirstStart(boolean doSet) {
        int value = getInt(R.string.pref_key__app_first_start_current_version, -1);
        if (doSet) {
            setInt(R.string.pref_key__app_first_start_current_version, BuildConfig.VERSION_CODE);
        }
        return value != BuildConfig.VERSION_CODE;
    }

    public float getEditorLineSpacing() {
        return getInt(R.string.pref_key__editor_line_spacing, 100) / 100f;
    }

    public void setLastTodoUsedArchiveFilename(String value) {
        setString(R.string.pref_key__todotxt__last_used_archive_filename, value);
    }

    public String getLastTodoUsedArchiveFilename() {
        return getString(R.string.pref_key__todotxt__last_used_archive_filename, "todo.archive.txt");
    }

    public boolean isEditorStartOnBotttom() {
        return getBool(R.string.pref_key__editor_start_editing_on_bottom, true);
    }

    public boolean isEditorStartEditingInCenter() {
        return getBool(R.string.pref_key__editor_start_editing_in_center, false);
    }

    public int getEditorTextActionItemPadding() {
        return getInt(R.string.pref_key__editor_textaction_bar_item_padding, 8);
    }

    public boolean isDisableSpellingRedUnderline() {
        return getBool(R.string.pref_key__editor_disable_spelling_red_underline, true);
    }

    public void addRecentDocument(File file) {
        if (!listFileInRecents(file)) {
            return;
        }
        if (!file.equals(getTodoFile()) && !file.equals(getQuickNoteFile())) {
            ArrayList<String> recent = getRecentDocuments();
            recent.add(0, file.getAbsolutePath());
            recent.remove(getTodoFile().getAbsolutePath());
            recent.remove(getQuickNoteFile().getAbsolutePath());
            recent.remove("");
            recent.remove(null);

            setInt(file.getAbsolutePath(), getInt(file.getAbsolutePath(), 0, _prefCache) + 1, _prefCache);
            setRecentDocuments(recent);
        }
        ShortcutUtils.setShortcuts(_context);
    }

    public void toggleFavouriteFile(File file) {
        List<String> list = new ArrayList<>();
        List<File> favourites = getFavouriteFiles();
        for (File f : favourites) {
            if (f != null && (f.exists() || FilesystemViewerAdapter.isVirtualStorage(f))) {
                list.add(f.getAbsolutePath());
            }
        }
        String abs = file.getAbsolutePath();
        if (list.contains(abs)) {
            list.remove(abs);
        } else {
            list.add(abs);
        }
        setStringList(R.string.pref_key__favourite_files, list);
    }

    private static final String PREF_PREFIX_EDIT_POS_CHAR = "PREF_PREFIX_EDIT_POS_CHAR";
    private static final String PREF_PREFIX_WRAP_STATE = "PREF_PREFIX_WRAP_STATE";
    private static final String PREF_PREFIX_HIGHLIGHT_STATE = "PREF_PREFIX_HIGHLIGHT_STATE";
    private static final String PREF_PREFIX_PREVIEW_STATE = "PREF_PREFIX_PREVIEW_STATE";
    private static final String PREF_PREFIX_INDENT_SIZE = "PREF_PREFIX_INDENT_SIZE";
    private static final String PREF_PREFIX_FONT_SIZE = "PREF_PREFIX_FONT_SIZE";
    private static final String PREF_PREFIX_FILE_FORMAT = "PREF_PREFIX_FILE_FORMAT";
    private static final String PREF_PREFIX_VIEW_SCROLL_X = "PREF_PREFIX_VIEW_SCROLL_X";
    private static final String PREF_PREFIX_VIEW_SCROLL_Y = "PREF_PREFIX_VIEW_SCROLL_Y";

    public void setLastEditPosition(File file, int pos) {
        if (file == null || !file.exists()) {
            return;
        }
        if (!file.equals(getTodoFile()) && !file.equals(getQuickNoteFile())) {
            setInt(PREF_PREFIX_EDIT_POS_CHAR + file.getAbsolutePath(), pos, _prefCache);
        }
    }

    public void setLastViewPosition(File file, int scrollX, int scrollY) {
        if (file == null || !file.exists()) {
            return;
        }
        if (!file.equals(getTodoFile()) && !file.equals(getQuickNoteFile())) {
            setInt(PREF_PREFIX_VIEW_SCROLL_X + file.getAbsolutePath(), scrollX, _prefCache);
            setInt(PREF_PREFIX_VIEW_SCROLL_Y + file.getAbsolutePath(), scrollY, _prefCache);
        }
    }

    public void setDocumentWrapState(final String path, final boolean state) {
        if (fexists(path)) {
            setBool(PREF_PREFIX_WRAP_STATE + path, state);
        }
    }

    public boolean getDocumentWrapState(final String path) {
        final boolean _default = isEditorLineBreakingEnabled();
        if (!fexists(path)) {
            return _default;
        } else {
            return getBool(PREF_PREFIX_WRAP_STATE + path, _default);
        }
    }

    public void setDocumentFormat(final String path, @StringRes final int format) {
        if (fexists(path) && format != TextFormat.FORMAT_UNKNOWN) {
            setString(PREF_PREFIX_FILE_FORMAT + path, _context.getString(format));
        }
    }

    @StringRes
    public int getDocumentFormat(final String path, final int _default) {
        if (!fexists(path)) {
            return _default;
        } else {
            final String value = getString(PREF_PREFIX_FILE_FORMAT + path, null);
            final int sid = _contextUtils.getResId(net.gsantner.opoc.util.ContextUtils.ResType.STRING, value);
            // Note TextFormat.FORMAT_UNKNOWN also == 0
            return sid != 0 ? sid : _default;
        }
    }

    public void setDocumentFontSize(final String path, final int size) {
        if (fexists(path)) {
            setInt(PREF_PREFIX_FONT_SIZE + path, size);
        }
    }

    public int getDocumentFontSize(final String path) {
        final int _default = getFontSize();
        if (!fexists(path)) {
            return _default;
        } else {
            return getInt(PREF_PREFIX_FONT_SIZE + path, _default);
        }
    }

    public void setDocumentIndentSize(final String path, final int size) {
        if (fexists(path)) {
            setInt(PREF_PREFIX_INDENT_SIZE + path, size);
        }
    }

    public int getDocumentIndentSize(final String path) {
        final int _default = 4;
        if (!fexists(path)) {
            return _default;
        } else {
            return getInt(PREF_PREFIX_INDENT_SIZE + path, _default);
        }
    }

    public void setDocumentPreviewState(final String path, final boolean state) {
        setBool(PREF_PREFIX_PREVIEW_STATE + path, state);
    }

    public boolean getDocumentPreviewState(final String path) {
        // Use global setting as default
        final boolean _default = isPreviewFirst();
        if (_default || !fexists(path)) {
            return _default;
        } else {
            return getBool(PREF_PREFIX_PREVIEW_STATE + path, _default);
        }
    }

    public void setDocumentHighlightState(final String path, final boolean state) {
        setBool(PREF_PREFIX_HIGHLIGHT_STATE + path, state);
    }

    public boolean getDocumentHighlightState(final String path, final CharSequence chars) {
        final boolean lengthOk = chars != null && chars.length() < (_isDeviceGoodHardware ? 100000 : 35000);
        return getBool(PREF_PREFIX_HIGHLIGHT_STATE + path, lengthOk && isHighlightingEnabled());
    }

    public int getLastEditPositionChar(File file) {
        if (file == null || !file.exists()) {
            return -1;
        }
        if (file.equals(getTodoFile()) || file.equals(getQuickNoteFile())) {
            return -2;
        }
        return getInt(PREF_PREFIX_EDIT_POS_CHAR + file.getAbsolutePath(), -3, _prefCache);
    }

    public int getLastViewPositionX(File file) {
        if (file == null || !file.exists()) {
            return -1;
        }
        return getInt(PREF_PREFIX_VIEW_SCROLL_X + file.getAbsolutePath(), -3, _prefCache);
    }

    public int getLastViewPositionY(File file) {
        if (file == null || !file.exists()) {
            return -1;
        }
        return getInt(PREF_PREFIX_VIEW_SCROLL_Y + file.getAbsolutePath(), -3, _prefCache);
    }

    private List<String> getPopularDocumentsSorted() {
        List<String> popular = getRecentDocuments();
        Collections.sort(popular, (o1, o2) -> Integer.compare(getInt(o1, 0, _prefCache), getInt(o2, 0, _prefCache)));
        return popular;
    }

    public List<String> getPopularDocuments() {
        return getStringList(R.string.pref_key__popular_documents);
    }

    public void setPopularDocuments(List<String> v) {
        limitListTo(v, 20, true);
        setStringList(R.string.pref_key__popular_documents, v, _prefApp);
    }

    public void setRecentDocuments(List<String> v) {
        limitListTo(v, 20, true);
        setStringList(R.string.pref_key__recent_documents, v, _prefApp);
        setPopularDocuments(getPopularDocumentsSorted());
    }

    public ArrayList<String> getRecentDocuments() {
        ArrayList<String> list = getStringList(R.string.pref_key__recent_documents);
        for (int i = 0; i < list.size(); i++) {
            if (!new File(list.get(i)).isFile()) {
                list.remove(i);
                i--;
            }
        }
        return list;
    }

    public ArrayList<File> getAsFileList(List<String> list) {
        ArrayList<File> r = new ArrayList<>();
        for (String f : list) {
            r.add(new File(f));
        }
        return r;
    }

    public ArrayList<File> getFavouriteFiles() {
        ArrayList<File> list = new ArrayList<>();
        for (String fp : getStringList(R.string.pref_key__favourite_files)) {
            File f = new File(fp);
            if (f.exists() || FilesystemViewerAdapter.isVirtualStorage(f)) {
                list.add(f);
            }
        }
        return list;
    }

    public String getInjectedHeader() {
        return getString(R.string.pref_key__inject_to_head, rstr(R.string.inject_to_head_default));
    }

    public String getInjectedBody() {
        return getString(R.string.pref_key__inject_to_body, "");
    }

    public boolean isEditorHistoryEnabled() {
        return true;//getBool(R.string.pref_key__editor_history_enabled3, true);
    }

    public boolean isTodoTxtAlternativeNaming() {
        return getBool(R.string.pref_key__todotxt__alternative_naming_context_project, true);
    }

    public int getEditorBasicColorSchemeId() {
        return 0;
    }


    public int getEditorForegroundColor() {
        /*switch (getEditorBasicColorSchemeId()) {
            default:
            case 0:
                return rcolor(darkMode ? R.color.white : R.color.dark_grey);
            case 1:
                return rcolor(darkMode ? R.color.white : R.color.black);
            case 2:
                return rcolor(R.color.solarized_fg);
            case 3:
                return rcolor(R.color.solarized_fg);
        }*/


        boolean darkMode = isDarkThemeEnabled();
        int defval = rcolor(darkMode ? R.color.white : R.color.dark_grey);
        return getInt(darkMode ? R.string.pref_key__editor_basic_color_scheme__fg_dark : R.string.pref_key__editor_basic_color_scheme__fg_light, defval);
    }

    public int getEditorBackgroundColor() {
        /*switch (getEditorBasicColorSchemeId()) {
            default:
            case 0:
                return rcolor(darkMode ? R.color.dark_grey : R.color.light__background);
            case 1:
                return rcolor(darkMode ? R.color.black : R.color.white);
            case 2:
                return rcolor(darkMode ? R.color.solarized_bg_dark : R.color.solarized_bg_light);
            case 3:
                return rcolor(darkMode ? R.color.solarized_bg_dark : R.color.solarized_bg_light);
        }*/

        boolean darkMode = isDarkThemeEnabled();
        int defval = rcolor(darkMode ? R.color.dark_grey : R.color.light__background);
        return getInt(darkMode ? R.string.pref_key__editor_basic_color_scheme__bg_dark : R.string.pref_key__editor_basic_color_scheme__bg_light, defval);

    }

    public int getEditorTextactionBarColor() {
        return rcolor(isDarkThemeEnabled() ? R.color.dark__background_2 : R.color.lighter_grey);
    }

    public void setEditorBasicColor(boolean forDarkMode, @ColorRes int fgColor, @ColorRes int bgColor) {
        int resIdFg = forDarkMode ? R.string.pref_key__editor_basic_color_scheme__fg_dark : R.string.pref_key__editor_basic_color_scheme__fg_light;
        int resIdBg = forDarkMode ? R.string.pref_key__editor_basic_color_scheme__bg_dark : R.string.pref_key__editor_basic_color_scheme__bg_light;
        setInt(resIdFg, rcolor(fgColor));
        setInt(resIdBg, rcolor(bgColor));
    }

    public boolean isMultiWindowEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return getBool(R.string.pref_key__is_multi_window_enabled, true);
        } else {
            return false;
        }
    }

    public boolean isSearchQueryCaseSensitive() {
        return getBool(R.string.pref_key__is_search_query_case_sensitive, false);
    }

    public void setSearchQueryCaseSensitivity(final boolean isQuerySensitive) {
        setBool(R.string.pref_key__is_search_query_case_sensitive, isQuerySensitive);
    }

    public boolean isSearchQueryUseRegex() {
        return getBool(R.string.pref_key__is_search_query_use_regex, false);
    }

    public void setSearchQueryRegexUsing(final boolean isUseRegex) {
        setBool(R.string.pref_key__is_search_query_use_regex, isUseRegex);
    }

    public boolean isSearchInContent() {
        return getBool(R.string.pref_key__is_search_in_content, false);
    }

    public void setSearchInContent(final boolean isSearchInContent) {
        setBool(R.string.pref_key__is_search_in_content, isSearchInContent);
    }

    public boolean isOnlyFirstContentMatch() {
        return getBool(R.string.pref_key__is_only_first_content_match, false);
    }

    public void setOnlyFirstContentMatch(final boolean isOnlyFirstContentMatch) {
        setBool(R.string.pref_key__is_only_first_content_match, isOnlyFirstContentMatch);
    }

    public int getSearchMaxDepth() {
        int depth = getIntOfStringPref(R.string.pref_key__max_search_depth, Integer.MAX_VALUE);

        if (depth == 0) {
            return Integer.MAX_VALUE;
        }

        return depth;
    }

    public List<String> getFileSearchIgnorelist() {
        String pref = getString(R.string.pref_key__filesearch_ignorelist, "");
        return Arrays.asList(pref.replace("\r", "").replace("\n\n", "\n").split("\n"));
    }

    public @IdRes
    int getAppStartupTab() {
        int i = getIntOfStringPref(R.string.pref_key__app_start_tab_v2, R.id.nav_notebook);
        switch (i) {
            case 1:
                return R.id.nav_todo;
            case 2:
                return R.id.nav_quicknote;
        }
        return R.id.nav_notebook;
    }

    public boolean isSwipeToChangeMode() {
        return getBool(R.string.pref_key__swipe_to_change_mode, false);
    }

    public void setFilesystemListFolderFirst(boolean checked) {
        setBool(R.string.pref_key__filesystem_folder_first, checked);
    }


    public boolean isFilesystemListFolderFirst() {
        return getBool(R.string.pref_key__filesystem_folder_first, true);
    }

    public String getNavigationBarColor() {
        return getString(R.string.pref_key__navigationbar_color, "#000000");
    }

    public @IdRes
    Integer getAppStartupFolderMenuId() {
        switch (getString(R.string.pref_key__app_start_folder, "notebook")) {
            case "favourites":
                return R.id.action_go_to_favourite_files;
            case "internal_storage":
                return R.id.action_go_to_storage;
            case "appdata_public":
                return R.id.action_go_to_appdata_public;
            case "appdata_private":
                return R.id.action_go_to_appdata_private;
            case "popular_documents":
                return R.id.action_go_to_popular_files;
            case "recently_viewed_documents":
                return R.id.action_go_to_recent_files;
        }
        return R.id.action_go_to_home;
    }

    public File getFolderToLoadByMenuId(int itemId) {
        List<Pair<File, String>> appDataPublicDirs = _contextUtils.getAppDataPublicDirs(false, true, false);
        switch (itemId) {
            case R.id.action_go_to_home: {
                return getNotebookDirectory();
            }
            case R.id.action_go_to_popular_files: {
                return FilesystemViewerAdapter.VIRTUAL_STORAGE_POPULAR;
            }
            case R.id.action_go_to_recent_files: {
                return FilesystemViewerAdapter.VIRTUAL_STORAGE_RECENTS;
            }
            case R.id.action_go_to_favourite_files: {
                return FilesystemViewerAdapter.VIRTUAL_STORAGE_FAVOURITE;
            }
            case R.id.action_go_to_appdata_private: {
                return _contextUtils.getAppDataPrivateDir();
            }
            case R.id.action_go_to_storage: {
                return Environment.getExternalStorageDirectory();
            }
            case R.id.action_go_to_appdata_sdcard_1: {
                if (appDataPublicDirs.size() > 0) {
                    return appDataPublicDirs.get(0).first;
                }
                return Environment.getExternalStorageDirectory();
            }
            case R.id.action_go_to_appdata_sdcard_2: {
                if (appDataPublicDirs.size() > 1) {
                    return appDataPublicDirs.get(1).first;
                }
                return Environment.getExternalStorageDirectory();
            }
            case R.id.action_go_to_appdata_public: {
                appDataPublicDirs = _contextUtils.getAppDataPublicDirs(true, false, false);
                if (appDataPublicDirs.size() > 0) {
                    return appDataPublicDirs.get(0).first;
                }
                return _contextUtils.getAppDataPrivateDir();
            }
        }
        return getNotebookDirectory();
    }

    public void setShowDotFiles(boolean value) {
        setBool(R.string.pref_key__show_dot_files, value);
    }

    public boolean isShowDotFiles() {
        return getBool(R.string.pref_key__show_dot_files, false);
    }

    public int getTabWidth() {
        return getInt(R.string.pref_key__tab_width_v2, 1);
    }

    public boolean listFileInRecents(File file) {
        return getBool(file.getAbsolutePath() + "_list_in_recents", true);
    }

    public void setListFileInRecents(File file, boolean value) {
        setBool(file.getAbsolutePath() + "_list_in_recents", value);

        if (!value) {
            ArrayList<String> recent = getRecentDocuments();
            if (recent.contains(file.getAbsolutePath())) {
                recent.remove(file.getAbsolutePath());
                setRecentDocuments(recent);
            }
        }
    }

    /*public ArrayList<String> getFilesRatedWith(int rating) {
        return getFilesTaggedWith("rating_" + Integer.toString(rating));
    }

    public ArrayList<String> getFilesTaggedWith(String tag) {
        return getStringList("files_tagged_with" + tag, _prefHistory);
    }*/

    public int getRating(File file) {
        return getInt(file.getAbsolutePath() + "_rating", 0);
    }

    public void setRating(File file, int value) {
        setInt(file.getAbsolutePath() + "_rating", value);
    }

    public boolean isEditorLineBreakingEnabled() {
        return getBool(R.string.pref_key__editor_enable_line_breaking, true);
    }

    private List<String> extSettingCache;

    public synchronized boolean isExtOpenWithThisApp(String ext) {
        if (ext.equals("")) {
            ext = "None";
        }
        if (extSettingCache == null) {
            String pref = getString(R.string.pref_key__exts_to_always_open_in_this_app, "");
            extSettingCache = Arrays.asList(pref.toLowerCase().replace(",,", ",None,").replace(" ", "").split(","));
        }
        return extSettingCache.contains(ext) || extSettingCache.contains(".*");
    }

    public boolean isExperimentalFeaturesEnabled() {
        return getBool(R.string.pref_key__is_enable_experimental_features, BuildConfig.IS_TEST_BUILD);
    }

    public boolean isMarkdownBiggerHeadings() {
        return getBool(R.string.pref_key__editor_markdown_bigger_headings_2, false);
    }

    public boolean isZimWikiBiggerHeadings() {
        return getBool(R.string.pref_key__editor_zimwiki_bigger_headings, false);
    }

    public String getViewModeLinkColor() {
        return ContextUtils.colorToHexString(getInt(R.string.pref_key__view_mode_link_color, Color.parseColor("#388E3C")));
    }

    public String getUnorderedListCharacter() {
        return getString(R.string.pref_key__editor_unordered_list_character, "-");
    }

    public boolean isTodoNewTaskWithHuuidEnabled() {
        return getBool(R.string.pref_key__todotxt__start_new_tasks_with_huuid_v3, false);
    }

    public String getHuuidDeviceId() {
        String deviceid = getString("huuid_deviceid", null);
        if (deviceid == null) {
            deviceid = String.format("%08x", new Random().nextInt()).substring(0, 4);
            setString("huuid_deviceid", deviceid);
        }
        return deviceid;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public char[] getDefaultPassword() {
        return new PasswordStore(getContext()).loadKey(R.string.pref_key__default_encryption_password);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public boolean isDefaultPasswordSet() {
        final char[] key = getDefaultPassword();
        return (key != null && key.length > 0);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setDefaultPassword(String password) {
        new PasswordStore(getContext()).storeKey(password, R.string.pref_key__default_encryption_password);
    }

    public boolean getNewFileDialogLastUsedEncryption() {
        return getBool(R.string.pref_key__new_file_dialog_lastused_encryption, false);
    }

    public void setNewFileDialogLastUsedEncryption(boolean b) {
        setBool(R.string.pref_key__new_file_dialog_lastused_encryption, b);
    }

    public String getNewFileDialogLastUsedExtension() {
        return getString(R.string.pref_key__new_file_dialog_lastused_extension, ".md");
    }

    public void setNewFileDialogLastUsedExtension(String v) {
        setString(R.string.pref_key__new_file_dialog_lastused_extension, v);
    }

    public int getNewFileDialogLastUsedType() {
        return getInt(R.string.pref_key__new_file_dialog_lastused_type, 0);
    }

    public void setNewFileDialogLastUsedType(int i) {
        setInt(R.string.pref_key__new_file_dialog_lastused_type, i);
    }

    public void setFileBrowserLastBrowsedFolder(File f) {
        setString(R.string.pref_key__file_browser_last_browsed_folder, f.getAbsolutePath());
    }

    public File getFileBrowserLastBrowsedFolder() {
        return new File(getString(R.string.pref_key__file_browser_last_browsed_folder, getNotebookDirectoryAsStr()));
    }

    public boolean getSetWebViewFulldrawing(boolean... setValue) {
        final String k = "getSetWebViewFulldrawing";
        if (setValue != null && setValue.length == 1) {
            setBool(k, setValue[0]);
            return setValue[0];
        }
        return getBool(k, false);
    }

    public String getTodotxtAdditionalContextsAndProjects() {
        return getString(R.string.pref_key__todotxt__additional_projects_contexts, "+music +video @home @shop");
    }

    // Not tied to an actual settings. Just moved here for clarity.
    public int getDueDateOffset() {
        return getInt(R.string.pref_key__todotxt__due_date_offset, 3);
    }

    public boolean isOpenLinksInDefaultBrowser() {
        return getBool(R.string.pref_key__open_links_in_default_browser, false);
    }
}
