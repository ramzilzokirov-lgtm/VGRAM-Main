package org.telegram.ui;

import org.telegram.messenger.R;

import android.os.Bundle;

public class VgramPrivacyActivity extends VgramBaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setupScreen(getString(R.string.VgramPrivacyEyebrow), getString(R.string.VgramPrivacyTitle),
                getString(R.string.VgramPrivacySubtitle));
        card(getString(R.string.VgramPrivacyTelegramTitle), getString(R.string.VgramPrivacyTelegramText));
        card(getString(R.string.VgramPrivacyServerTitle), getString(R.string.VgramPrivacyServerText));
        card(getString(R.string.VgramPrivacyBannerTitle), getString(R.string.VgramPrivacyBannerText));
        card(getString(R.string.VgramPrivacyLocalTitle), getString(R.string.VgramPrivacyLocalText));
        card(getString(R.string.VgramPrivacyAdminTitle), getString(R.string.VgramPrivacyAdminText));
        card(getString(R.string.VgramPrivacyNetworkTitle), getString(R.string.VgramPrivacyNetworkText));
        card(getString(R.string.VgramPrivacyThirdPartyTitle), getString(R.string.VgramPrivacyThirdPartyText));
        addButton(body, getString(R.string.VgramPrivacyTelegramButton), () -> {
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(getString(R.string.PrivacyPolicyUrl))));
            } catch (Exception ignored) { }
        });
    }
}
