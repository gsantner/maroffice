package net.gsantner.markor.ui.fsearch;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.Build;
import android.support.design.widget.Snackbar;
import android.support.v4.util.Pair;
import android.view.View;
import android.widget.Toast;

import net.gsantner.markor.R;
import net.gsantner.markor.format.TextFormat;
import net.gsantner.opoc.util.Callback;
import net.gsantner.opoc.util.FileUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import other.de.stanetz.jpencconverter.JavaPasswordbasedCryption;

@SuppressWarnings("WeakerAccess")

public class SearchEngine {
    public static boolean isSearchExecuting = false;
    public static AtomicReference<WeakReference<Activity>> activity = new AtomicReference<>();
    public final static List<String> defaultIgnoredDirs = new ArrayList<>(Arrays.asList("^\\.git$", "^\\.tmp$", ".*[Tt]humb.*"));

    public static final int maxPreviewLength = 100;
    public static final int maxQueryHistoryCount = 20;
    public static final LinkedList<String> queryHistory = new LinkedList<>();

    public static void addToHistory(String query) {
        queryHistory.remove(query);

        if (queryHistory.size() == maxQueryHistoryCount) {
            queryHistory.removeLast();
        }
        queryHistory.addFirst(query);
    }

    public static class SearchOptions {
        public File rootSearchDir;
        public String query;

        public boolean isRegexQuery;
        public boolean isCaseSensitiveQuery;
        public boolean isSearchInContent;
        public boolean isOnlyFirstContentMatch;

        public int maxSearchDepth;
        public List<String> ignoredDirectories;
        public boolean isShowMatchPreview = true;
        public boolean isShowResultOnCancel = true;
        public char[] password = new char[0];
    }

    public static class FitFile {
        public final String path;
        public final boolean isDirectory;
        public final List<Pair<String, Integer>> matchesWithLineNumberAndLineText;

        public FitFile(final String a_path, final boolean a_isDirectory, List<Pair<String, Integer>> lineNumbers) {
            path = a_path;
            isDirectory = a_isDirectory;
            matchesWithLineNumberAndLineText = Collections.unmodifiableList(lineNumbers != null ? lineNumbers : new ArrayList<>());
        }
    }

    public static SearchEngine.QueueSearchFilesTask queueFileSearch(Activity activity, SearchOptions config, Callback.a1<List<FitFile>> callback) {
        SearchEngine.activity.set(new WeakReference<>(activity));
        SearchEngine.isSearchExecuting = true;
        SearchEngine.addToHistory(config.query);
        SearchEngine.QueueSearchFilesTask task = new SearchEngine.QueueSearchFilesTask(config, callback);
        task.execute();

        return task;
    }

    public static class QueueSearchFilesTask extends AsyncTask<Void, Integer, List<FitFile>> {
        private final SearchOptions _config;
        private final Callback.a1<List<FitFile>> _callback;
        private final Pattern _regex;

        private Snackbar _snackBar;
        private Integer _countCheckedFiles = 0;
        private Integer _currentQueueLength = 1;
        private boolean _isCanceled = false;
        private Integer _currentSearchDepth = 0;
        private final List<FitFile> _result = new ArrayList<>();
        private final List<Pattern> _ignoredRegexDirs = new ArrayList<>();
        private final List<String> _ignoredExactDirs = new ArrayList<>();

        public QueueSearchFilesTask(final SearchOptions config, final Callback.a1<List<FitFile>> callback) {
            _config = config;
            _callback = callback;

            _config.query = _config.isCaseSensitiveQuery ? _config.query : _config.query.toLowerCase();
            splitRegexExactFiles(config.ignoredDirectories, _ignoredExactDirs, _ignoredRegexDirs);
            splitRegexExactFiles(SearchEngine.defaultIgnoredDirs, _ignoredExactDirs, _ignoredRegexDirs);

            Pattern pattern = null;
            if (_config.isRegexQuery) {
                try {
                    _config.query = _config.query.replaceAll("(?<![.])[*]", ".*");
                    pattern = Pattern.compile(_config.query);
                } catch (Exception ex) {
                    Activity a;
                    if (SearchEngine.activity.get() != null && (a = SearchEngine.activity.get().get()) != null) {
                        String errorMessage = a.getString(R.string.regex_can_not_be_compiled) + ": " + _config.query;
                        Toast.makeText(a, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
            }
            _regex = pattern;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (_config.isRegexQuery && _regex == null) {
                cancel(true);
                return;
            }
            bindSnackBar(_config.query);
        }

        @SuppressLint("ShowToast")
        public void bindSnackBar(String text) {
            if (!SearchEngine.isSearchExecuting) {
                return;
            }

            try {
                View view = SearchEngine.activity.get().get().findViewById(android.R.id.content);
                _snackBar = Snackbar.make(view, text, Snackbar.LENGTH_INDEFINITE)
                        .addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                if (SearchEngine.isSearchExecuting) {
                                    bindSnackBar(text);
                                }
                            }
                        });
                _snackBar.setAction(android.R.string.cancel, (v) -> {
                    _snackBar.dismiss();
                    preCancel();
                });
                _snackBar.show();
            } catch (Exception ignored) {
            }
        }

        @Override
        protected List<FitFile> doInBackground(Void... voidp) {
            Queue<File> mainQueue = new LinkedList<>();
            mainQueue.add(_config.rootSearchDir);

            while (!mainQueue.isEmpty() && !isCancelled() && !_isCanceled) {
                File currentDirectory = mainQueue.remove();

                if (!currentDirectory.canRead() || currentDirectory.isFile()) {
                    continue;
                }

                _currentSearchDepth = getDirectoryDepth(_config.rootSearchDir, currentDirectory);
                if (_currentSearchDepth > _config.maxSearchDepth) {
                    break;
                }
                _currentQueueLength = mainQueue.size() + 1;
                publishProgress(_currentQueueLength, _currentSearchDepth, _result.size(), _countCheckedFiles);

                mainQueue.addAll(currentDirectoryHandler(currentDirectory));
            }

            if (_isCanceled && _result.size() == 0) {
                cancel(true);
            }

            return _result;
        }

        private Queue<File> currentDirectoryHandler(File currentDir) {
            Queue<File> subQueue = new LinkedList<>();

            try {
                if (!currentDir.canRead() || currentDir.isFile()) {
                    return subQueue;
                }

                File[] subDirsOrFiles = currentDir.listFiles();
                for (final File f : (subDirsOrFiles != null ? subDirsOrFiles : new File[0])) {
                    if (isCancelled() || _isCanceled) {
                        break;
                    }
                    _countCheckedFiles++;

                    if (!f.canRead()) {
                        continue;
                    } else if (f.isDirectory()) { // Handling for directory
                        if (isIgnored(f) || isFileContainSymbolicLinks(f, currentDir)) {
                            continue;
                        }
                        subQueue.add(f);
                    } else { // Handling for file
                        if (isIgnored(f)) {
                            continue;
                        }

                        if (_config.isSearchInContent) {
                            if (!TextFormat.isTextFile(f.getName())) {
                                continue;
                            }
                            List<Pair<String, Integer>> contentMatches = getContentMatches(f, _config.isOnlyFirstContentMatch);

                            if (contentMatches.isEmpty()) {
                                continue;
                            }

                            String path = f.getCanonicalPath().replace(_config.rootSearchDir.getCanonicalPath() + "/", "");
                            _result.add(new FitFile(path, false, contentMatches));
                        }
                    }

                    if (!_config.isSearchInContent) {
                        getFileIfNameMatches(f);
                    }

                    publishProgress(_currentQueueLength + subQueue.size(), _currentSearchDepth, _result.size(), _countCheckedFiles);
                }
            } catch (Exception ignored) {
            }

            return subQueue;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (_snackBar != null) {
                _snackBar.setText("f:" + values[2] + " qu:" + values[0] + "|" + values[1] + " c:" + values[3] + "\n" + _config.query);
            }
        }

        @Override
        protected void onPostExecute(List<FitFile> ret) {
            super.onPostExecute(ret);
            SearchEngine.isSearchExecuting = false;
            if (_snackBar != null) {
                _snackBar.dismiss();
            }
            if (_callback != null) {
                try {
                    _callback.callback(ret);
                } catch (Exception ignored) {
                }
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            SearchEngine.isSearchExecuting = false;
        }

        public void splitRegexExactFiles(List<String> list, List<String> exactList, List<Pattern> regexList) {
            for (String pattern : (list != null ? list : new ArrayList<String>())) {
                if (pattern.isEmpty()) {
                    continue;
                }
                if (!_config.isCaseSensitiveQuery) {
                    pattern = pattern.toLowerCase();
                }

                if (pattern.startsWith("\"")) {
                    pattern = pattern.replace("\"", "");
                    if (pattern.isEmpty()) {
                        continue;
                    }
                    exactList.add(pattern);
                } else {
                    pattern = pattern.replaceAll("(?<![.])[*]", ".*");
                    try {
                        regexList.add(Pattern.compile(pattern));
                    } catch (Exception ex) {

                        Activity a;
                        if (SearchEngine.activity.get() != null && (a = SearchEngine.activity.get().get()) != null) {
                            String errorMessage = a.getString(R.string.regex_can_not_be_compiled) + ": " + pattern;
                            Toast.makeText(a, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }
        }

        private boolean isFileContainSymbolicLinks(File file, File expectedParentDir) {
            try {
                File realParentDir = file.getCanonicalFile().getParentFile();
                if (realParentDir != null && expectedParentDir.getCanonicalPath().equals(realParentDir.getCanonicalPath())) {
                    return false;
                }
            } catch (Exception ignored) {
            }

            return true;
        }

        private void getFileIfNameMatches(File file) {
            try {
                String fileName = _config.isCaseSensitiveQuery ? file.getName() : file.getName().toLowerCase();
                boolean isMatch = _config.isRegexQuery ? _regex.matcher(fileName).matches() : fileName.contains(_config.query);

                if (isMatch) {
                    String path = file.getCanonicalPath().replace(_config.rootSearchDir.getCanonicalPath() + "/", "");
                    _result.add(new FitFile(path, file.isDirectory(), null));
                }
            } catch (Exception ignored) {
            }
        }

        private int getDirectoryDepth(File parentDir, File childDir) {
            try {
                String parentPath = parentDir.getCanonicalPath();
                String childPath = childDir.getCanonicalPath();
                if (!childPath.startsWith(parentPath)) {
                    return -1;
                }

                String res = childPath.replace(parentPath, "");
                return res.split("/").length;
            } catch (Exception ignored) {
            }

            return -1;
        }

        private void preCancel() {
            if (_config.isShowResultOnCancel) {
                _isCanceled = true;
                return;
            }

            cancel(true);
        }

        // Match line and return preview string. Preview will be null if no match found
        private String matchLine(final String line) {
            final String preparedLine = _config.isCaseSensitiveQuery ? line : line.toLowerCase();

            int start = -1, end = -1;
            if (_config.isRegexQuery) {
                final Matcher match = _regex.matcher(preparedLine);
                if (match.find()) {
                    start = match.start();
                    end = match.end();
                }
            } else {
                start = preparedLine.indexOf(_config.query);
                if (start >= 0) {
                    end = start + _config.query.length();
                }
            }

            // Preview is based on original line
            if (start >= 0 && end <= line.length()) {
                if (!_config.isShowMatchPreview) {
                    return "";
                }
                if (line.length() < maxPreviewLength) {
                    return line;
                } else {
                    int offset = (maxPreviewLength - (end - start)) / 2;
                    int subStart = Math.max(start - offset, 0);
                    int subEnd = Math.min(end + offset, line.length());
                    return String.format("… %s …", line.substring(subStart, subEnd));
                }
            }
            return null;
        }

        private List<Pair<String, Integer>> getContentMatches(final File file, final boolean isFirstMatchOnly) {
            List<Pair<String, Integer>> ret = new ArrayList<>();

            if (!file.canRead() || file.isDirectory()) {
                return ret;
            }

            try (final BufferedReader br = new BufferedReader(new InputStreamReader(getInputStream(file)))) {
                int lineNumber = 0;
                for (String line; (line = br.readLine()) != null; ) {
                    if (isCancelled() || _isCanceled) {
                        break;
                    }
                    line = matchLine(line);
                    if (line != null) {
                        ret.add(new Pair<>(line, lineNumber));
                        if (isFirstMatchOnly) {
                            break;
                        }
                    }
                    lineNumber++;
                }
            } catch (Exception ignored) {
            }

            return ret;
        }

        private boolean isIgnored(File directory) {
            final String dirName = _config.isCaseSensitiveQuery ? directory.getName() : directory.getName().toLowerCase();
            for (final String pattern : _ignoredExactDirs) {
                if (dirName.equals(pattern)) {
                    return true;
                }
            }

            for (final Pattern pattern : _ignoredRegexDirs) {
                if (pattern.matcher(dirName).matches()) {
                    return true;
                }
            }
            return false;
        }

        private InputStream getInputStream(File file) throws FileNotFoundException {
            if (isEncryptedFile(file)) {
                final byte[] encryptedContext = FileUtils.readCloseStreamWithSize(new FileInputStream(file), (int) file.length());
                return new ByteArrayInputStream(JavaPasswordbasedCryption.getDecryptedText(encryptedContext, _config.password.clone()).getBytes(StandardCharsets.UTF_8));
            } else {
                return new FileInputStream(file);
            }
        }
    }


    private static boolean isEncryptedFile(File file) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && file.getName().endsWith(JavaPasswordbasedCryption.DEFAULT_ENCRYPTION_EXTENSION);
    }
}
