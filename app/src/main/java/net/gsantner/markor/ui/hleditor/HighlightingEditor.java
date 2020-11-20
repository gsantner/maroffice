/*#######################################################
 *
 *   Maintained by Gregor Santner, 2017-
 *   https://gsantner.net/
 *
 *   License of this file: Apache 2.0 (Commercial upon request)
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
#########################################################*/
package net.gsantner.markor.ui.hleditor;

import android.content.Context;
import android.os.Handler;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import net.gsantner.markor.activity.MainActivity;
import net.gsantner.markor.model.Document;
import net.gsantner.markor.util.AppSettings;
import net.gsantner.markor.util.ContextUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;


@SuppressWarnings("UnusedReturnValue")
public class HighlightingEditor extends AppCompatEditText {
    public boolean isCurrentLineEmpty() {
        final int posOrig = getSelectionStart();
        final int posLineBegin = moveCursorToBeginOfLine(0);
        final int posEndBegin = moveCursorToEndOfLine(0);
        setSelection(posOrig);
        return posLineBegin == posEndBegin;
    }

    interface OnTextChangedListener {
        void onTextChanged(String text);
    }

    private final boolean _isDeviceGoodHardware;
    private boolean _modified = true;
    private boolean _hlEnabled = false;
    private boolean _accessibilityEnabled = true;
    private final boolean _isSpellingRedUnderline;
    private Highlighter _hl;
    private final Set<TextWatcher> _appliedModifiers = new HashSet<>(); /* Tracks currently applied modifiers */

    public final static String PLACE_CURSOR_HERE_TOKEN = "%%PLACE_CURSOR_HERE%%";
    private final Handler _updateHandler = new Handler();
    private final Runnable _updateRunnable;

    public HighlightingEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        AppSettings as = new AppSettings(context);
        if (as.isHighlightingEnabled()) {
            setHighlighter(Highlighter.getDefaultHighlighter(this, new Document(new File("/tmp"))));
            enableHighlighterAutoFormat();
            setHighlightingEnabled(as.isHighlightingEnabled());
        }

        _isDeviceGoodHardware = new ContextUtils(context).isDeviceGoodHardware();
        _isSpellingRedUnderline = !as.isDisableSpellingRedUnderline();
        _updateRunnable = () -> { highlightWithoutChange(); };

        addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable e) {
                cancelUpdate();
                if (!_modified) {
                    return;
                }
                if (MainActivity.IS_DEBUG_ENABLED) {
                    AppSettings.appendDebugLog("Changed text (afterTextChanged)");
                }
                if (_hl != null) {
                    int delay = (int) _hl.getHighlightingFactorBasedOnFilesize() * (_hl.isFirstHighlighting() ? 300 : _hl.getHighlightingDelay(getContext()));
                    if (MainActivity.IS_DEBUG_ENABLED) {
                        AppSettings.appendDebugLog("Highlighting run: delay " + delay + "ms, cfactor " + _hl.getHighlightingFactorBasedOnFilesize());
                    }
                    _updateHandler.postDelayed(_updateRunnable, delay);
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (MainActivity.IS_DEBUG_ENABLED) {
                    AppSettings.appendDebugLog("Changed text (onTextChanged)");
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (MainActivity.IS_DEBUG_ENABLED) {
                    AppSettings.appendDebugLog("Changed text (beforeTextChanged)");
                }
            }
        });
    }

    public void setHighlighter(Highlighter newHighlighter) {
        disableHighlighterAutoFormat();
        _hl = newHighlighter;
        reloadHighlighter();

        // Alpha in animation
        setAlpha(0.3f);
        animate().alpha(1.0f)
                .setDuration(500)
                .setListener(null);
    }

    public Highlighter getHighlighter() {
        return _hl;
    }

    public void enableHighlighterAutoFormat() {
        setFilters(new InputFilter[]{_hl.getAutoFormatter()});

        TextWatcher modifier = (_hl != null) ? _hl.getTextModifier() : null;
        if (modifier != null && !_appliedModifiers.contains(modifier)) {
            addTextChangedListener(modifier);
            _appliedModifiers.add(modifier);
        }
    }

    public void disableHighlighterAutoFormat() {
        setFilters(new InputFilter[]{});

        TextWatcher modifier = (_hl != null) ? _hl.getTextModifier() : null;
        if (modifier != null) {
            removeTextChangedListener(modifier);
            _appliedModifiers.remove(modifier);
        }
    }

    private void cancelUpdate() {
        _updateHandler.removeCallbacks(_updateRunnable);
    }

    public void reloadHighlighter() {
        enableHighlighterAutoFormat();
        highlightWithoutChange();
    }

    final int MAX_ACCESSIBILITY_TEXT = 500;

    // This allows us to disable sending of accessibility events when highlighting is being
    // performed. Sending of many accessibility events can cause performance issues and crashes.
    // Blocking accessibility during _highlighting_ should have no impact on users.
    // Additionaly the accessibility messages can send a very large amount of text on each
    @Override
    public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
        if (_accessibilityEnabled) {
            final CharSequence text = event.getBeforeText();
            if (text != null && text.length() > (2 * MAX_ACCESSIBILITY_TEXT)) {
                final int from = event.getFromIndex();
                final int newStart = Math.max(from - MAX_ACCESSIBILITY_TEXT, 0);
                final int neededAfter = Math.max(Math.max(MAX_ACCESSIBILITY_TEXT, event.getAddedCount()), event.getRemovedCount());
                final int newEnd = Math.min(from + neededAfter, text.length());
                event.setBeforeText(text.subSequence(newStart, newEnd));
                event.setFromIndex(from - newStart);
            }
            super.sendAccessibilityEventUnchecked(event);
        }
    }

    @Override
    public void sendAccessibilityEvent(int eventType) {
        if (_accessibilityEnabled) {
            super.sendAccessibilityEvent(eventType);
        }
    }

    private void highlightWithoutChange() {
        final Editable editable = getText();
        final boolean isFileShortEnough = editable.length() <= (_isDeviceGoodHardware ? 100000 : 35000);
        if (isFileShortEnough && _hlEnabled) {
            _modified = false;
            try {
                if (MainActivity.IS_DEBUG_ENABLED) {
                    AppSettings.appendDebugLog("Start highlighting");
                }
                _accessibilityEnabled = false;
                _hl.run(editable);
            } catch (Exception e) {
                // In no case ever let highlighting crash the editor
                e.printStackTrace();
            } catch (Error e) {
                e.printStackTrace();
            } finally {
                _accessibilityEnabled = true;
            }
            if (MainActivity.IS_DEBUG_ENABLED) {
                AppSettings.appendDebugLog(_hl._profiler.resetDebugText());
                AppSettings.appendDebugLog("Finished highlighting");
            }
            _modified = true;
        }
    }

    public void simulateKeyPress(int keyEvent_KEYCODE_SOMETHING) {
        dispatchKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyEvent_KEYCODE_SOMETHING, 0));
        dispatchKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_UP, keyEvent_KEYCODE_SOMETHING, 0));
    }

    public void insertOrReplaceTextOnCursor(String newText) {
        newText = (newText == null ? "" : newText);
        int newCursorPos = newText.indexOf(PLACE_CURSOR_HERE_TOKEN);
        newText = newText.replace(PLACE_CURSOR_HERE_TOKEN, "");
        int start = Math.max(getSelectionStart(), 0);
        int end = Math.max(getSelectionEnd(), 0);
        getText().replace(Math.min(start, end), Math.max(start, end), newText, 0, newText.length());
        if (newCursorPos >= 0) {
            setSelection(start + newCursorPos);
        }
    }

    public int getShiftWidth(String text) {
        if (text.contains("sw=2") || text.contains("shiftwidth=2")) {
            return 2;
        } else if (text.contains("sw=8") || text.contains("shiftwidth=8")) {
            return 8;
        } else {
            return 4;
        }
    }

    public int moveCursorToEndOfLine(int offset) {
        simulateKeyPress(KeyEvent.KEYCODE_MOVE_END);
        setSelection(getSelectionStart() + offset);
        return getSelectionStart();
    }

    public int moveCursorToBeginOfLine(int offset) {
        simulateKeyPress(KeyEvent.KEYCODE_MOVE_HOME);
        setSelection(getSelectionStart() + offset);
        return getSelectionStart();
    }

    // Set selection to fill whole lines
    // Returns original selectionStart
    public int setSelectionExpandWholeLines() {
        final int orig_s = getSelectionStart();
        final int orig_e = getSelectionEnd();

        setSelection(orig_s);
        simulateKeyPress(KeyEvent.KEYCODE_MOVE_HOME);
        final int new_s = getSelectionStart();

        setSelection(orig_e);
        simulateKeyPress(KeyEvent.KEYCODE_MOVE_END);
        final int new_e = getSelectionStart();

        setSelection(new_s, new_e);
        return orig_s;
    }

    //
    // Simple getter / setter
    //

    public void setHighlightingEnabled(final boolean enable) {
        if (_hlEnabled && !enable) {
            _hlEnabled = false;
            Highlighter.clearSpans(getText());
        } else if (!_hlEnabled && enable && _hl != null) {
            _hlEnabled = true;
            highlightWithoutChange();
        }
    }


    public boolean indexesValid(int... indexes) {
        int len = length();
        for (int index : indexes) {
            if (index < 0 || index > len) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isSuggestionsEnabled() {
        return _isSpellingRedUnderline && super.isSuggestionsEnabled();
    }

    @Override
    public void setSelection(int index) {
        if (indexesValid(index)) {
            super.setSelection(index);
        }
    }

    @Override
    public void setSelection(int start, int stop) {
        if (indexesValid(start, stop)) {
            super.setSelection(start, stop);
        } else if (indexesValid(start, stop - 1)) {
            super.setSelection(start, stop - 1);
        } else if (indexesValid(start + 1, stop)) {
            super.setSelection(start + 1, stop);
        }
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (MainActivity.IS_DEBUG_ENABLED) {
            AppSettings.appendDebugLog("Selection changed: " + selStart + "->" + selEnd);
        }
    }
}
