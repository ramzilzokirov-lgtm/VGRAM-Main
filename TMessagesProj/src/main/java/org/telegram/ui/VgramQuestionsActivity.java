package org.telegram.ui;

import android.os.Bundle;

public class VgramQuestionsActivity extends VgramBaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setupScreen(getString(R.string.VgramQuestionsEyebrow), getString(R.string.TelegramFAQ),
                getString(R.string.VgramQuestionsSubtitle));
        expandableCard(getString(R.string.VgramFaqLoginTitle), getString(R.string.VgramFaqLoginText));
        expandableCard(getString(R.string.VgramFaqBadgesTitle), getString(R.string.VgramFaqBadgesText));
        expandableCard(getString(R.string.VgramFaqUpdatesTitle), getString(R.string.VgramFaqUpdatesText));
        expandableCard(getString(R.string.VgramFaqPluginsTitle), getString(R.string.VgramFaqPluginsText));
        expandableCard(getString(R.string.VgramFaqPremiumTitle), getString(R.string.VgramFaqPremiumText));
        expandableCard(getString(R.string.VgramFaqThemesTitle), getString(R.string.VgramFaqThemesText));
        expandableCard(getString(R.string.VgramFaqSupportTitle), getString(R.string.VgramFaqSupportText));
    }
}
