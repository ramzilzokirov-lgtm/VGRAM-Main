package org.telegram.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class VgramSupportActivity extends VgramBaseActivity {
    private static final String ISSUES_URL = "https://github.com/ramzilzokirov-lgtm/VGRAM-Main/issues";
    private static final String OWNER_URL = "https://github.com/ramzilzokirov-lgtm";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setupScreen(getString(R.string.VgramSupportEyebrow), getString(R.string.VgramSupportTitle),
                getString(R.string.VgramSupportSubtitle));
        card(getString(R.string.VgramSupportContactTitle), getString(R.string.VgramSupportContactText));
        addButton(body, getString(R.string.VgramSupportWriteButton), () -> openUrl(ISSUES_URL));
        card(getString(R.string.VgramSupportOwnerTitle), getString(R.string.VgramSupportOwnerText));
        addButton(body, getString(R.string.VgramSupportOwnerButton), () -> openUrl(OWNER_URL));
        card(getString(R.string.VgramSupportSafetyTitle), getString(R.string.VgramSupportSafetyText));
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            android.widget.Toast.makeText(this, getString(R.string.VgramSupportOpenError), android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
