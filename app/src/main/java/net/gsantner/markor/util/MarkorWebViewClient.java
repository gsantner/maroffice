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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.content.ContextCompat;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import net.gsantner.markor.R;
import net.gsantner.markor.activity.DocumentActivity;
import net.gsantner.markor.format.TextFormat;

import java.io.File;
import java.net.URLDecoder;

public class MarkorWebViewClient extends WebViewClient {

    private final Activity _activity;

    public MarkorWebViewClient(Activity activity) {
        _activity = activity;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        try {
            if (url.equals("about:blank")) {
                view.reload();
                return true;
            }
            if (url.startsWith("file:///android_asset/")) {
                return false;
            } else if (url.startsWith("file://")) {
                ShareUtil su = new ShareUtil(view.getContext());
                File file = new File(URLDecoder.decode(url.replace("file://", "")));
                String mimetype;
                for (String str : new String[]{file.getAbsolutePath(), file.getAbsolutePath().replaceFirst("[#].*$", ""), file.getAbsolutePath() + ".md", file.getAbsolutePath() + ".txt"}) {
                    File f = new File(str);
                    if (f.exists() && TextFormat.isTextFile(f)) {
                        file = f;
                        break;
                    }
                }
                if (TextFormat.isTextFile(file)) {
                    Intent newPreview = new Intent(_activity, DocumentActivity.class);
                    newPreview.putExtra(DocumentIO.EXTRA_PATH, file);
                    newPreview.putExtra(DocumentActivity.EXTRA_DO_PREVIEW, true);
                    _activity.startActivity(newPreview);
                } else if ((mimetype = ContextUtils.getMimeType(url)) != null) {
                    su.viewFileInOtherApp(file, mimetype);
                } else {
                    su.viewFileInOtherApp(file, null);
                }
            } else {
                ContextUtils cu = new ContextUtils(_activity.getApplicationContext());
                ShareUtil su = new ShareUtil(view.getContext());
                try {
                    // Use a CustomTabsIntent.Builder to configure CustomTabsIntent.
                    // Once ready, call CustomTabsIntent.Builder.build() to create a CustomTabsIntent
                    // and launch the desired Url with CustomTabsIntent.launchUrl()
                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                    builder.setToolbarColor(ContextCompat.getColor(_activity, R.color.primary));
                    builder.setSecondaryToolbarColor(ContextCompat.getColor(_activity, R.color.primary_dark));
                    builder.addDefaultShareMenuItem();
                    CustomTabsIntent customTabsIntent = builder.build();
                    su.enableChromeCustomTabsForOtherBrowsers(customTabsIntent.intent);
                    customTabsIntent.launchUrl(_activity, Uri.parse(url));
                } catch (Exception e) {
                    cu.openWebpageInExternalBrowser(url);
                }
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
    }

}
