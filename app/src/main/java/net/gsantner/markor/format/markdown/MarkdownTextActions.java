/*#######################################################
 *
 *   Maintained by Gregor Santner, 2017-
 *   https://gsantner.net/
 *
 *   License of this file: Apache 2.0 (Commercial upon request)
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
#########################################################*/
package net.gsantner.markor.format.markdown;

import android.app.Activity;
import android.support.annotation.StringRes;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import net.gsantner.markor.R;
import net.gsantner.markor.format.general.CommonTextActions;
import net.gsantner.markor.model.Document;
import net.gsantner.markor.ui.AttachImageOrLinkDialog;
import net.gsantner.markor.ui.SearchOrCustomTextDialogCreator;
import net.gsantner.markor.ui.hleditor.TextActions;
import net.gsantner.opoc.util.Callback;
import net.gsantner.opoc.util.ContextUtils;
import net.gsantner.opoc.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class MarkdownTextActions extends TextActions {

    public static final Pattern PREFIX_ORDERED_LIST = Pattern.compile("^(\\s*)((\\d+)(\\.|\\))(\\s+))");
    public static final Pattern PREFIX_ATX_HEADING = Pattern.compile("^(\\s{0,3})(#{1,6}\\s)");
    public static final Pattern PREFIX_QUOTE = Pattern.compile("^(>\\s)");
    public static final Pattern PREFIX_CHECKED_LIST = Pattern.compile("^(\\s*)((-|\\*|\\+)\\s\\[(x|X)]\\s)");
    public static final Pattern PREFIX_UNCHECKED_LIST = Pattern.compile("^(\\s*)((-|\\*|\\+)\\s\\[\\s]\\s)");
    public static final Pattern PREFIX_UNORDERED_LIST = Pattern.compile("^(\\s*)((-|\\*|\\+)\\s)");
    public static final Pattern PREFIX_LEADING_SPACE = Pattern.compile("^(\\s*)");

    private static final Pattern[] PREFIX_PATTERNS = {
            PREFIX_ORDERED_LIST,
            PREFIX_ATX_HEADING,
            PREFIX_QUOTE,
            PREFIX_CHECKED_LIST,
            PREFIX_UNCHECKED_LIST,
            // Unordered has to be after checked list. Otherwise checklist will match as an unordered list.
            PREFIX_UNORDERED_LIST,
            PREFIX_LEADING_SPACE,
    };

    public MarkdownTextActions(Activity activity, Document document) {
        super(activity, document);
    }

    @Override
    public boolean runAction(String action, boolean modLongClick, String anotherArg) {
        int res = new ContextUtils(_context).getResId(ContextUtils.ResType.STRING, action);
        return new MarkdownTextActionsImpl(res).onClickImpl(null);
    }

    @Override
    protected ActionCallback getActionCallback(@StringRes int keyId) {
        return new MarkdownTextActionsImpl(keyId);
    }

    @Override
    protected @StringRes
    int getFormatActionsKey() {
        return R.string.pref_key__markdown__action_keys;
    }

    @Override
    public List<ActionItem> getActiveActionList() {

        final ActionItem[] TMA_ACTIONS = {
                new ActionItem(R.string.tmaid_common_checkbox_list, R.drawable.ic_check_box_black_24dp, R.string.check_list),
                new ActionItem(R.string.tmaid_common_unordered_list_char, R.drawable.ic_list_black_24dp, R.string.unordered_list),
                new ActionItem(R.string.tmaid_markdown_bold, R.drawable.ic_format_bold_black_24dp, R.string.bold),
                new ActionItem(R.string.tmaid_markdown_italic, R.drawable.ic_format_italic_black_24dp, R.string.italic),
                new ActionItem(R.string.tmaid_common_delete_lines, CommonTextActions.ACTION_DELETE_LINES_ICON, R.string.delete_lines),
                new ActionItem(R.string.tmaid_common_open_link_browser, CommonTextActions.ACTION_OPEN_LINK_BROWSER__ICON, R.string.open_link),
                new ActionItem(R.string.tmaid_common_attach_something, R.drawable.ic_attach_file_black_24dp, R.string.attach),
                new ActionItem(R.string.tmaid_common_special_key, CommonTextActions.ACTION_SPECIAL_KEY__ICON, R.string.special_key),
                new ActionItem(R.string.tmaid_common_time, R.drawable.ic_access_time_black_24dp, R.string.date_and_time),
                new ActionItem(R.string.tmaid_markdown_code_inline, R.drawable.ic_code_black_24dp, R.string.inline_code),
                new ActionItem(R.string.tmaid_common_ordered_list_number, R.drawable.ic_format_list_numbered_black_24dp, R.string.ordered_list),
                new ActionItem(R.string.tmaid_markdown_table_insert_columns, R.drawable.ic_view_module_black_24dp, R.string.table),
                new ActionItem(R.string.tmaid_markdown_quote, R.drawable.ic_format_quote_black_24dp, R.string.quote),
                new ActionItem(R.string.tmaid_markdown_h1, R.drawable.format_header_1, R.string.heading_1),
                new ActionItem(R.string.tmaid_markdown_h2, R.drawable.format_header_2, R.string.heading_2),
                new ActionItem(R.string.tmaid_markdown_h3, R.drawable.format_header_3, R.string.heading_3),
                new ActionItem(R.string.tmaid_markdown_horizontal_line, R.drawable.ic_more_horiz_black_24dp, R.string.horizontal_line),
                new ActionItem(R.string.tmaid_markdown_strikeout, R.drawable.ic_format_strikethrough_black_24dp, R.string.strikeout),
                new ActionItem(R.string.tmaid_common_accordion, R.drawable.ic_arrow_drop_down_black_24dp, R.string.accordion),
                new ActionItem(R.string.tmaid_common_indent, R.drawable.ic_format_indent_increase_black_24dp, R.string.indent),
                new ActionItem(R.string.tmaid_common_deindent, R.drawable.ic_format_indent_decrease_black_24dp, R.string.deindent),
                new ActionItem(R.string.tmaid_common_new_line_below, R.drawable.ic_baseline_keyboard_return_24, R.string.start_new_line_below),
                new ActionItem(R.string.tmaid_common_move_text_one_line_up, R.drawable.ic_baseline_arrow_upward_24, R.string.move_text_one_line_up),
                new ActionItem(R.string.tmaid_common_move_text_one_line_down, R.drawable.ic_baseline_arrow_downward_24, R.string.move_text_one_line_down),
        };

        return Arrays.asList(TMA_ACTIONS);
    }

    private class MarkdownTextActionsImpl extends ActionCallback {
        private int _action;

        MarkdownTextActionsImpl(int action) {
            _action = action;
        }

        @Override
        public void onClick(final View view) {
            onClickImpl(view);
        }

        private boolean onClickImpl(final View view) {
            if (view != null) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            switch (_action) {
                case R.string.tmaid_markdown_quote: {
                    runPrefixReplaceAction(PREFIX_QUOTE, ">$1 ", "");
                    return true;
                }
                case R.string.tmaid_markdown_h1: {
                    setHeadingAction(1);
                    return true;
                }
                case R.string.tmaid_markdown_h2: {
                    setHeadingAction(2);
                    return true;
                }
                case R.string.tmaid_markdown_h3: {
                    setHeadingAction(3);
                    return true;
                }
                case R.string.tmaid_common_unordered_list_char: {
                    final String listChar = _appSettings.getUnorderedListCharacter();
                    final String listPrefix = "$1" + listChar + " ";
                    runPrefixReplaceAction(PREFIX_UNORDERED_LIST, listPrefix, "$1");
                    return true;
                }
                case R.string.tmaid_common_checkbox_list: {
                    final String listChar = _appSettings.getUnorderedListCharacter();
                    final String uncheck = "$1" + listChar + " [ ] ";
                    final String check = "$1" + listChar + " [x] ";
                    runPrefixReplaceAction(PREFIX_UNCHECKED_LIST, uncheck, check);
                    return true;
                }
                case R.string.tmaid_common_ordered_list_number: {
                    runPrefixReplaceAction(PREFIX_ORDERED_LIST, "$11. ", "$1");
                    runRenumberOrderedListIfRequired();
                    return true;
                }
                case R.string.tmaid_markdown_bold: {
                    runInlineAction("**");
                    return true;
                }
                case R.string.tmaid_markdown_italic: {
                    runInlineAction("_");
                    return true;
                }
                case R.string.tmaid_markdown_strikeout: {
                    runInlineAction("~~");
                    return true;
                }
                case R.string.tmaid_markdown_code_inline: {
                    runInlineAction("`");
                    return true;
                }
                case R.string.tmaid_markdown_horizontal_line: {
                    runInlineAction("----\n");
                    return true;
                }
                case R.string.tmaid_markdown_table_insert_columns: {
                    SearchOrCustomTextDialogCreator.showInsertTableRowDialog(_activity, false, callbackInsertTableRow);
                    return true;
                }
                case R.string.tmaid_markdown_insert_link:
                case R.string.tmaid_markdown_insert_image: {
                    AttachImageOrLinkDialog.showInsertImageOrLinkDialog(_action == R.string.tmaid_markdown_insert_image ? 2 : 3, _document.getFormat(), _activity, _hlEditor, _document.getFile());
                    return true;
                }
                case R.string.tmaid_common_toolbar_title_clicked_edit_action: {
                    final String origText = _hlEditor.getText().toString();
                    SearchOrCustomTextDialogCreator.showMarkdownHeadlineDialog(_activity, origText, (text, lineNr) -> {
                        _hlEditor.setSelection(StringUtils.getIndexFromLineOffset(origText, lineNr, 0));
                    });
                    return true;
                }
                case R.string.tmaid_common_move_text_one_line_up:
                case R.string.tmaid_common_move_text_one_line_down: {
                    runCommonTextAction(_context.getString(_action));
                    runRenumberOrderedListIfRequired();
                    return true;
                }
                case R.string.tmaid_common_indent: {
                    runIndentLines(false);
                    runRenumberOrderedListIfRequired();
                    return true;
                }
                case R.string.tmaid_common_deindent: {
                    runIndentLines(true);
                    runRenumberOrderedListIfRequired();
                    return true;
                }
                default: {
                    return runCommonTextAction(_context.getString(_action));
                }
            }
        }

        @Override
        public boolean onLongClick(View view) {
            switch (_action) {
                case R.string.tmaid_common_open_link_browser: {
                    new CommonTextActions(_activity, _hlEditor).runAction(CommonTextActions.ACTION_SEARCH);
                    return true;
                }
                case R.string.tmaid_common_special_key: {
                    new CommonTextActions(_activity, _hlEditor).runAction(CommonTextActions.ACTION_JUMP_BOTTOM_TOP);
                    return true;
                }
                case R.string.tmaid_markdown_insert_image: {
                    int pos = _hlEditor.getSelectionStart();
                    _hlEditor.getText().insert(pos, "<img style=\"width:auto;max-height: 256px;\" src=\"\" />");
                    _hlEditor.setSelection(pos + 48);
                    return true;
                }
                case R.string.tmaid_common_time: {
                    runCommonTextAction("tmaid_common_time_insert_timestamp");
                    return true;
                }
                case R.string.tmaid_markdown_table_insert_columns: {
                    SearchOrCustomTextDialogCreator.showInsertTableRowDialog(_activity, true, callbackInsertTableRow);
                    break;
                }
                case R.string.tmaid_markdown_code_inline: {
                    _hlEditor.disableHighlighterAutoFormat();
                    final int c = _hlEditor.setSelectionExpandWholeLines();
                    _hlEditor.getText().insert(_hlEditor.getSelectionStart(), "\n```\n");
                    _hlEditor.getText().insert(_hlEditor.getSelectionEnd(), "\n```\n");
                    _hlEditor.setSelection(c + "\n```\n".length());
                    _hlEditor.enableHighlighterAutoFormat();
                    Toast.makeText(_activity, R.string.code_block, Toast.LENGTH_SHORT).show();
                    return true;
                }
                case R.string.tmaid_common_ordered_list_number: {
                    MarkdownAutoFormat.renumberOrderedList(_hlEditor.getText(), StringUtils.getSelection(_hlEditor)[0]);
                }
                case R.string.tmaid_common_deindent :
                case R.string.tmaid_common_indent : {
                    SearchOrCustomTextDialogCreator.showIndentSizeDialog(_activity, _indent, (size) -> {
                        _indent = Integer.parseInt(size);
                        _appSettings.setDocumentIndentSize(getPath(), _indent);
                    });
                    return true;
                }
            }
            return false;
        }

        private final Callback.a2<Integer, Boolean> callbackInsertTableRow = (cols, isHeaderEnabled) -> {
            StringBuilder sb = new StringBuilder();
            _hlEditor.requestFocus();
            if (!_hlEditor.isCurrentLineEmpty()) {
                sb.append("\n");
            }
            for (int i = 0; i < cols - 1; i++) {
                sb.append("  | ");
            }
            if (isHeaderEnabled) {
                sb.append("\n");
                for (int i = 0; i < cols; i++) {
                    sb.append("---");
                    if (i < cols - 1) {
                        sb.append("|");
                    }
                }
            }
            _hlEditor.moveCursorToEndOfLine(0);
            _hlEditor.insertOrReplaceTextOnCursor(sb.toString());
            _hlEditor.moveCursorToBeginOfLine(0);
            if (isHeaderEnabled) {
                _hlEditor.simulateKeyPress(KeyEvent.KEYCODE_DPAD_UP);
            }
        };
    }


    /**
     * Set/unset ATX heading level on each selected line
     * <p>
     * This routine will make the following conditional changes
     * <p>
     * Line is heading of same level as requested -> remove heading
     * Line is heading of different level that that requested -> add heading of specified level
     * Line is not heading -> add heading of specified level
     *
     * @param level ATX heading level
     */
    private void setHeadingAction(int level) {

        List<ReplacePattern> patterns = new ArrayList<>();

        String heading = StringUtils.repeatChars('#', level);

        // Replace this exact heading level with nothing
        patterns.add(new ReplacePattern("^(\\s{0,3})" + heading + " ", "$1"));

        // Replace other headings with commonmark-compatible leading space
        patterns.add(new ReplacePattern(PREFIX_ATX_HEADING, "$1" + heading + " "));

        // Replace all other prefixes with heading
        for (final Pattern pp : PREFIX_PATTERNS) {
            patterns.add(new ReplacePattern(pp, heading + "$1 "));
        }

        runRegexReplaceAction(patterns);
    }

    private void runPrefixReplaceAction(final Pattern actionPattern, final String action, final String alt) {

        List<ReplacePattern> patterns = new ArrayList<>();

        // Replace prefixes with action (or alt if prefix is specified action)
        for (final Pattern pp : PREFIX_PATTERNS) {
            patterns.add(new ReplacePattern(pp, pp == actionPattern ? alt : action));
        }

        runRegexReplaceAction(patterns);
    }

    private void runRenumberOrderedListIfRequired() {
        if (_appSettings.isMarkdownAutoUpdateList()) {
            MarkdownAutoFormat.renumberOrderedList(_hlEditor.getText(), StringUtils.getSelection(_hlEditor)[0]);
        }
    }
}
