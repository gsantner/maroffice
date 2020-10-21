package net.gsantner.markor.format.zimwiki;

import net.gsantner.markor.ui.hleditor.TextActions;

import org.junit.Test;

import java.util.List;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

public class ZimWikiReplacePatternGeneratorTest {
    private List<TextActions.ReplacePattern> replacePatterns;
    private String result;
    private final ZimWikiReplacePatternGenerator replacePatternGenerator = new ZimWikiReplacePatternGenerator();

    @Test
    public void createHeadingsWithSpecifiedLevel() {
    }
    @Test
    public void removeHeadingCharsForExactHeadingLevel() {
        setLevelFourHeadingAction();
        assertCorrectReplacement("=== Heading ===","Heading");
    }

    @Test
    public void replaceDifferentLevelHeadings() {
        setLevelFourHeadingAction();
        assertCorrectReplacement("==== Heading ====", "=== Heading ===");
    }

    @Test
    public void createEmptyHeading() {
        setLevelFourHeadingAction();
        assertCorrectReplacement("", "===  ===");
    }

    @Test
    public void addHeadingCharactersToText() {
        setLevelFourHeadingAction();
        assertCorrectReplacement("Heading", "=== Heading ===");
    }

    private void setLevelFourHeadingAction() {
        int headingLevel = 4;
        replacePatterns = replacePatternGenerator.setOrUnsetHeadingWithLevel(headingLevel);
    }

    @Test
    public void toggleFromUncheckedToCheckedBox() {
        replacePatterns = replacePatternGenerator.replaceWithNextStateCheckbox();
        String uncheckedItem = "[ ] some item";
        assertCorrectReplacement(uncheckedItem, "[*] some item");
    }

    @Test
    public void toggleCheckBoxInCorrectOrder() {
        replacePatterns = replacePatternGenerator.replaceWithNextStateCheckbox();
        String[] orderedCheckboxStates = {" ", "*", "x", ">"};
        // create checkbox
        String currentLine = "some item";
        currentLine = replaceWithFirstMatchingPattern(replacePatterns, currentLine);
        for (int i = 0; i<orderedCheckboxStates.length+1; i++) {
            assertThat(currentLine).isEqualTo("["+orderedCheckboxStates[i%orderedCheckboxStates.length]+"] some item");
            currentLine = replaceWithFirstMatchingPattern(replacePatterns, currentLine);
        }
    }

    @Test
    public void replaceNonChecklistPrefixesWithUncheckedBox() {
        replacePatterns = replacePatternGenerator.replaceWithNextStateCheckbox();
        String[] otherPrefixes = {"1.", "a.", "*"};
        for (String otherPrefix : otherPrefixes) {
            String itemWithOtherPrefix = otherPrefix + " some item";
            assertCorrectReplacement(itemWithOtherPrefix, "[ ] some item");
        }
    }

    @Test
    public void keepWhitespaceWhenAddingCheckbox() {
        replacePatterns = replacePatternGenerator.replaceWithNextStateCheckbox();
        String original = " some item";
        assertCorrectReplacement(original, " [ ] some item");
    }

    @Test
    public void changePrefixToUnorderedListOrRemoveItAlreadyPresent() {
        replacePatterns = replacePatternGenerator.replaceWithUnorderedListPrefixOrRemovePrefix();
        String[] otherPrefixes = {"1.", "2.", "a.", "[ ]", "[x]"};
        for (String otherPrefix : otherPrefixes) {
            String originalLine = otherPrefix + " some item";
            assertCorrectReplacement(originalLine, "* some item");
        }
        assertCorrectReplacement("* some item", "some item");
    }

    @Test
    public void changePrefixToOrderedListOrRemoveItAlreadyPresent() {
        replacePatterns = replacePatternGenerator.replaceWithOrderedListPrefixOrRemovePrefix();
        String[] otherPrefixes = {"[>]", "*", "[ ]"};
        for (String otherPrefix : otherPrefixes) {
            String originalLine = otherPrefix + " some item";
            assertCorrectReplacement(originalLine, "1. some item");
        }
        String[] orderedListPrefixes = {"1.", "a.", "2."};
        for (String orderedListPrefix : orderedListPrefixes) {
            String originalLine = orderedListPrefix + " some item";
            assertCorrectReplacement(originalLine, "some item");
        }
    }

    private void assertCorrectReplacement(String original, String expectedReplacement) {
        result = replaceWithFirstMatchingPattern(replacePatterns, original);
        assertThat(result).isEqualTo(expectedReplacement);
    }

    private String replaceWithFirstMatchingPattern(List<TextActions.ReplacePattern> replacePatterns, String input) {
        for(TextActions.ReplacePattern replacePattern : replacePatterns) {
            Matcher matcher = replacePattern.searchPattern.matcher(input);
            if (matcher.find()) {
                return matcher.replaceFirst(replacePattern.replacePattern);
            }
        }
        return null;
    }
}