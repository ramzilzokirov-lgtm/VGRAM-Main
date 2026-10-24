package org.telegram.ui;

import android.os.Bundle;

public class VgramFeaturesActivity extends VgramBaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setupScreen(getString(R.string.VgramFeaturesEyebrow), getString(R.string.VgramFeaturesTitle),
                getString(R.string.VgramFeaturesSubtitle));
        card(getString(R.string.VgramFeatureThemesTitle), getString(R.string.VgramFeatureThemesText));
        card(getString(R.string.VgramFeatureBadgesTitle), getString(R.string.VgramFeatureBadgesText));
        card(getString(R.string.VgramFeaturePremiumTitle), getString(R.string.VgramFeaturePremiumText));
        card(getString(R.string.VgramFeatureBannersTitle), getString(R.string.VgramFeatureBannersText));
        card(getString(R.string.VgramFeaturePluginsTitle), getString(R.string.VgramFeaturePluginsText));
        card(getString(R.string.VgramFeatureMessagingTitle), getString(R.string.VgramFeatureMessagingText));
        card(getString(R.string.VgramFeatureUpdatesTitle), getString(R.string.VgramFeatureUpdatesText));
        card(getString(R.string.VgramFeatureLimitsTitle), getString(R.string.VgramFeatureLimitsText));
    }
}
