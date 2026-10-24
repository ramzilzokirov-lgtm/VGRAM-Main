package org.telegram.ui;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;

public class VgramSupportActivity extends VgramBaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setupScreen(getString(org.telegram.messenger.R.string.VgramSupportEyebrow),
                getString(org.telegram.messenger.R.string.VgramSupportTitle),
                getString(org.telegram.messenger.R.string.VgramSupportDescription));
        card(getString(org.telegram.messenger.R.string.VgramSupportContactTitle),
                getString(org.telegram.messenger.R.string.VgramSupportContactDescription));
        addButton(body, getString(org.telegram.messenger.R.string.VgramSupportWrite),
                () -> open("https://github.com/ramzilzokirov-lgtm/VGRAM-Main/issues"));
        addButton(body, getString(org.telegram.messenger.R.string.VgramSupportRepository),
                () -> open("https://github.com/ramzilzokirov-lgtm/VGRAM-Main"));
    }
    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Не удалось открыть ссылку", android.widget.Toast.LENGTH_LONG).show();
        }
    }
}