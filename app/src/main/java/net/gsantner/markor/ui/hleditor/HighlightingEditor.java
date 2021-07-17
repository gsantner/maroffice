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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import net.gsantner.markor.activity.MainActivity;
import net.gsantner.markor.model.Document;
import net.gsantner.markor.util.AppSettings;
import net.gsantner.opoc.util.StringUtils;

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

        _isSpellingRedUnderline = !as.isDisableSpellingRedUnderline();
        _updateRunnable = () -> {
            highlightWithoutChange();
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setFallbackLineSpacing(false);
        }

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
        enableHighlighterAutoFormat();
        highlightWithoutChange();

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

        final TextWatcher modifier = (_hl != null) ? _hl.getTextModifier() : null;
        if (modifier != null && !_appliedModifiers.contains(modifier)) {
            addTextChangedListener(modifier);
            _appliedModifiers.add(modifier);
        }
    }

    public void disableHighlighterAutoFormat() {
        setFilters(new InputFilter[]{});

        final TextWatcher modifier = (_hl != null) ? _hl.getTextModifier() : null;
        if (modifier != null) {
            removeTextChangedListener(modifier);
            _appliedModifiers.remove(modifier);
        }
    }

    private void cancelUpdate() {
        _updateHandler.removeCallbacks(_updateRunnable);
    }

    // Accessibility code is blocked during rapid update events
    // such as highlighting and some actions.
    // This prevents errors and potential crashes.
    @Override
    public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
        if (_accessibilityEnabled && length() < 10000) {
            super.sendAccessibilityEventUnchecked(event);
        }
    }

    // Hleditor will report that it is not autofillable under certain circumstances
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int getAutofillType() {
        if (_accessibilityEnabled && length() < 10000) {
            return super.getAutofillType();
        } else {
            return View.AUTOFILL_TYPE_NONE;
        }
    }

    private void highlightWithoutChange() {
        if (_hlEnabled) {
            _modified = false;
            try {
                if (MainActivity.IS_DEBUG_ENABLED) {
                    AppSettings.appendDebugLog("Start highlighting");
                }
                setAccessibilityEnabled(false);
                _hl.run(getText());
            } catch (Exception e) {
                // In no case ever let highlighting crash the editor
                e.printStackTrace();
            } catch (Error e) {
                e.printStackTrace();
            } finally {
                setAccessibilityEnabled(true);
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
        final int[] sel = StringUtils.getSelection(this);
        final CharSequence text = getText();
        setSelection(
                StringUtils.getLineStart(text, sel[0]),
                StringUtils.getLineEnd(text, sel[1])
        );
        return sel[0];
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

    public void smoothMoveCursor(final int startIndex, final int endIndex, int... arg0Delay__arg1Duration) {
        final int delay = Math.max(1, arg0Delay__arg1Duration != null && arg0Delay__arg1Duration.length > 0 ? arg0Delay__arg1Duration[0] : 500);
        final int duration = Math.max(1, arg0Delay__arg1Duration != null && arg0Delay__arg1Duration.length > 1 ? arg0Delay__arg1Duration[1] : 400);

        postDelayed(() -> {
            if (!hasFocus()) {
                requestFocus();
            }

            ObjectAnimator anim = ObjectAnimator.ofInt(this, "selection", startIndex, endIndex);
            anim.setDuration(duration);
            anim.start();
        }, delay);
    }

    public void smoothMoveCursorToLine(final int lineNumber, int... arg0Delay__arg1Duration) {
        final int delay = Math.max(1, arg0Delay__arg1Duration != null && arg0Delay__arg1Duration.length > 0 ? arg0Delay__arg1Duration[0] : 500);
        final int duration = Math.max(1, arg0Delay__arg1Duration != null && arg0Delay__arg1Duration.length > 1 ? arg0Delay__arg1Duration[1] : 400);

        this.postDelayed(() -> {
            String text = getText().toString();
            int index = StringUtils.getIndexFromLineOffset(text, lineNumber, 0);
            if (index < 0) {
                return;
            }

            smoothMoveCursor(0, index, 1, duration);
        }, delay);
    }

    public void setAccessibilityEnabled(final boolean enabled) {
        _accessibilityEnabled = enabled;
    }

    public boolean getAccessibilityEnabled() {
        return _accessibilityEnabled;
    }

    public boolean isVisible() {
        if (!isShown()) {
            return false;
        }
        final Rect actualPosition = new Rect();
        getGlobalVisibleRect(actualPosition);
        final Rect screen = new Rect(0, 0, Resources.getSystem().getDisplayMetrics().widthPixels, Resources.getSystem().getDisplayMetrics().heightPixels);
        return actualPosition.intersect(screen);
    }
}
