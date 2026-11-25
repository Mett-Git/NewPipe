package org.schabi.newpipe.account;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ActivityRecaptchaBinding;
import org.schabi.newpipe.util.ThemeHelper;

/**
 * Accesso all'account YouTube: la pagina di Google dentro l'app (come in Metrolist). La
 * password la scrivi tu nella pagina di Google: l'app non la vede e non la salva. Appena
 * l'accesso è fatto si tengono i cookie della sessione e la pagina si chiude da sola.
 */
public class VetroLoginActivity extends AppCompatActivity {
    private static final String YOUTUBE = "https://www.youtube.com";
    private static final String LOGIN_URL = "https://accounts.google.com/ServiceLogin"
            + "?service=youtube&uilel=3&passive=true"
            + "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue"
            + "%26app%3Dm%26next%3Dhttps%253A%252F%252Fwww.youtube.com%252F";

    private ActivityRecaptchaBinding binding;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        ThemeHelper.setTheme(this);
        super.onCreate(savedInstanceState);
        binding = ActivityRecaptchaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.vt_account_login);
            actionBar.setSubtitle(null);
        }
        setResult(RESULT_CANCELED);

        final WebView web = binding.reCaptchaWebView;
        final WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // si entra da zero: niente sessioni vecchie rimaste nella pagina
        final CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.removeAllCookies(null);
        web.clearCache(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);
                checkSignedIn(url);
            }
        });
        web.loadUrl(LOGIN_URL);
    }

    // appena si arriva su YouTube con l'accesso fatto, si tengono i cookie e si chiude
    private void checkSignedIn(@Nullable final String url) {
        if (url == null || !url.contains("youtube.com")) {
            return;
        }
        final String cookies = CookieManager.getInstance().getCookie(YOUTUBE);
        if (VetroAccount.isSignedIn(cookies)) {
            VetroAccount.saveLogin(this, cookies);
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        binding.reCaptchaWebView.destroy();
        super.onDestroy();
    }
}
