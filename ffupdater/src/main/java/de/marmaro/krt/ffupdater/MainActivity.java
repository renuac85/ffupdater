package de.marmaro.krt.ffupdater;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.dmstocking.optional.java.util.Optional;

import java.util.HashMap;
import java.util.Map;

import de.marmaro.krt.ffupdater.api.ApiResponses;
import de.marmaro.krt.ffupdater.background.LatestReleaseService;
import de.marmaro.krt.ffupdater.background.RepeatedNotifierExecuting;
import de.marmaro.krt.ffupdater.gui.DownloadOnClick;
import de.marmaro.krt.ffupdater.version.Version;
import de.marmaro.krt.ffupdater.version.VersionExtractor;

//import android.content.IntentFilter;
//import android.view.View.OnClickListener;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    public static final String OPENED_BY_NOTIFICATION = "OpenedByNotification";

    private Map<UpdateChannel, Version> localFirefox = new HashMap<>();;
    private Map<UpdateChannel, Version> availableVersions = new HashMap<>();;
    private DownloadUrlGenerator downloadUrl;

    protected Map<UpdateChannel, TextView> installedTextViews;
    protected Map<UpdateChannel, TextView> availableTextViews;
    protected Map<UpdateChannel, Button> checkAvailableButtons;
    protected Map<UpdateChannel, Button> downloadButtons;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build();
        StrictMode.setThreadPolicy(policy);

        installedTextViews = new HashMap<>();
        installedTextViews.put(UpdateChannel.RELEASE, (TextView) findViewById(R.id.installed_version));
        installedTextViews.put(UpdateChannel.BETA, (TextView) findViewById(R.id.installed_beta_version));
        installedTextViews.put(UpdateChannel.NIGHTLY, (TextView) findViewById(R.id.installed_nightly_version));
        installedTextViews.put(UpdateChannel.FOCUS, (TextView) findViewById(R.id.installed_focus_version));
        installedTextViews.put(UpdateChannel.KLAR, (TextView) findViewById(R.id.installed_klar_version));

        availableTextViews = new HashMap<>();
        availableTextViews.put(UpdateChannel.RELEASE, (TextView) findViewById(R.id.available_version));
        availableTextViews.put(UpdateChannel.BETA, (TextView) findViewById(R.id.available_beta_version));
        availableTextViews.put(UpdateChannel.NIGHTLY, (TextView) findViewById(R.id.available_nightly_version));
        availableTextViews.put(UpdateChannel.FOCUS, (TextView) findViewById(R.id.available_focus_version));
        availableTextViews.put(UpdateChannel.KLAR, (TextView) findViewById(R.id.available_klar_version));

        checkAvailableButtons = new HashMap<>();
        checkAvailableButtons.put(UpdateChannel.RELEASE, (Button) findViewById(R.id.check_available_stable_button));
        checkAvailableButtons.put(UpdateChannel.BETA, (Button) findViewById(R.id.check_available_beta_button));
        checkAvailableButtons.put(UpdateChannel.NIGHTLY, (Button) findViewById(R.id.check_available_nightly_button));
        checkAvailableButtons.put(UpdateChannel.FOCUS, (Button) findViewById(R.id.check_available_focus_button));
        checkAvailableButtons.put(UpdateChannel.KLAR, (Button) findViewById(R.id.check_available_klar_button));

        downloadButtons = new HashMap<>();
        downloadButtons.put(UpdateChannel.RELEASE, (Button) findViewById(R.id.download_stable_button));
        downloadButtons.put(UpdateChannel.BETA, (Button) findViewById(R.id.download_beta_button));
        downloadButtons.put(UpdateChannel.NIGHTLY, (Button) findViewById(R.id.download_nightly_button));
        downloadButtons.put(UpdateChannel.FOCUS, (Button) findViewById(R.id.download_focus_button));
        downloadButtons.put(UpdateChannel.KLAR, (Button) findViewById(R.id.download_klar_button));

        // starts the repeated update check
        RepeatedNotifierExecuting.register(this);

        downloadUrl = DownloadUrlGenerator.create();
        Log.i(TAG, "Firefox Release URL: " + downloadUrl.getUrl(UpdateChannel.RELEASE));
        Log.i(TAG, "Firefox Beta URL: " + downloadUrl.getUrl(UpdateChannel.BETA));

        // register onClickListener for checking the latest version numbers
        for (Map.Entry<UpdateChannel, Button> entry : checkAvailableButtons.entrySet()) {
            Button button = entry.getValue();
            button.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    loadLatestFirefoxVersions();
                }
            });
        }

        // register onClickListener for all download buttons
        for (Map.Entry<UpdateChannel, Button> entry : downloadButtons.entrySet()) {
            Button button = entry.getValue();
            button.setOnClickListener(new DownloadOnClick(downloadUrl, entry.getKey(), this));
        }
    }

    /**
     * Listen to the broadcast from {@link LatestReleaseService} and use the transmitted {@link Version} object.
     */
    private BroadcastReceiver latestReleaseServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Object raw = intent.getSerializableExtra(LatestReleaseService.EXTRA_RESPONSE_VERSION);
            Optional<ApiResponses> apiResponses = Optional.ofNullable((ApiResponses) raw);

            if (apiResponses.isPresent()) {
                downloadUrl.update(apiResponses.get());
                availableVersions = new VersionExtractor(apiResponses.get()).getVersionStrings();
                Log.d(TAG, "Found highest available version: " + availableVersions);
                updateDownloadButtonVisibilityDependingOnUrlAvailability();
                updateInstalledVersionTextViews();
                updateAvailableVersionTextViews();
            } else {
                displayErrorConnectionFailure();
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(latestReleaseServiceReceiver, new IntentFilter(LatestReleaseService.RESPONSE_ACTION));

        // load latest firefox version, when intent has got the "open by notification" flag
        Bundle bundle = getIntent().getExtras();
        if (null != bundle) {
            if (bundle.getBoolean(OPENED_BY_NOTIFICATION, false)) {
                bundle.putBoolean(OPENED_BY_NOTIFICATION, false);
                getIntent().replaceExtras(bundle);
                loadLatestFirefoxVersions();
            }
        }

        // check for the version of the current installed firefox
        localFirefox = InstalledFirefoxAppService.create(getPackageManager()).getLocalVersions();
        Log.i(TAG, localFirefox.toString());

        updateDownloadButtonVisibilityDependingOnUrlAvailability();
        updateInstalledVersionTextViews();
        updateAvailableVersionTextViews();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(latestReleaseServiceReceiver);
    }

    private void displayErrorConnectionFailure() {
        Log.d(TAG, "Could not determine highest available version.");
        (new AlertDialog.Builder(this))
                .setMessage(getString(R.string.check_available_error_message))
                .setPositiveButton(getString(R.string.ok), null)
                .show();
        changeCheckButtonVisibilityTo(true);
        updateAvailableVersionTextViews();
    }

    /**
     * Set the availableVersionTextView to "(checking…)" and start the LatestReleaseService service
     */
    private void loadLatestFirefoxVersions() {
        changeCheckButtonVisibilityTo(false);

        for (Map.Entry<UpdateChannel, TextView> entry : availableTextViews.entrySet()) {
            entry.getValue().setText(getString(R.string.checking));
        }

        Intent checkVersions = new Intent(this, LatestReleaseService.class);
        startService(checkVersions);
    }

    /**
     * Show or hide the download buttons when its download URL is available
     */
    private void updateDownloadButtonVisibilityDependingOnUrlAvailability() {
        for (Map.Entry<UpdateChannel, Button> entry : downloadButtons.entrySet()) {
            Button button = entry.getValue();
            button.setVisibility(downloadUrl.isUrlAvailable(entry.getKey()) ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Set the visibility of all check buttons.
     *
     * @param visible
     */
    private void changeCheckButtonVisibilityTo(boolean visible) {
        for (Map.Entry<UpdateChannel, Button> entry : checkAvailableButtons.entrySet()) {
            entry.getValue().setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Show the current version numbers of the current installed firefox apps
     */
    private void updateInstalledVersionTextViews() {
        for (UpdateChannel updateChannel : UpdateChannel.values()) {
            String installedText = getString(R.string.not_installed_text_format, getString(R.string.none), getString(R.string.ff_not_installed));

            Optional<Version> version = Optional.ofNullable(localFirefox.get(updateChannel));
            if (version.isPresent()) {
                installedText = getString(R.string.installed_version_text_format, version.get().getName(), version.get().getCode());
            }

            installedTextViews.get(updateChannel).setText(installedText);
        }
    }

    /**
     * Show the latest available versions of firefox, firefox beta, firefox nightly...
     */
    private void updateAvailableVersionTextViews() {
        for (Map.Entry<UpdateChannel, Version> versionStrings : availableVersions.entrySet()) {
            availableTextViews.get(versionStrings.getKey()).setText(versionStrings.getValue().getName());
        }
    }
}
