/*
 * Copyright (C) 2018 The Android Open Source Project
 *               2022 CorvusOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.homepage;

import static com.android.settings.search.actionbar.SearchMenuController.NEED_SEARCH_ICON_IN_ACTION_BAR;
import static com.android.settingslib.search.SearchIndexable.MOBILE;

import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.view.View;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.provider.Settings;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.activityembedding.ActivityEmbeddingUtils;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.support.SupportPreferenceController;
import com.android.settings.widget.HomepagePreference;
import com.android.settingslib.core.instrumentation.Instrumentable;
import com.android.settingslib.drawer.Tile;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable(forTarget = MOBILE)
public class TopLevelSettings extends DashboardFragment implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final String TAG = "TopLevelSettings";
    private static final String SAVED_HIGHLIGHT_MIXIN = "highlight_mixin";
    private static final String PREF_KEY_SUPPORT = "top_level_support";

    private boolean mIsEmbeddingActivityEnabled;
    private TopLevelHighlightMixin mHighlightMixin;
    private boolean mFirstStarted = true;

    public TopLevelSettings() {
        final Bundle args = new Bundle();
        // Disable the search icon because this page uses a full search view in actionbar.
        args.putBoolean(NEED_SEARCH_ICON_IN_ACTION_BAR, false);
        setArguments(args);
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.top_level_settings;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final RecyclerView recyclerView = getView().findViewById(R.id.recycler_view);
        recyclerView.setVerticalScrollBarEnabled(false);
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DASHBOARD_SUMMARY;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        HighlightableMenu.fromXml(context, getPreferenceScreenResId());
        use(SupportPreferenceController.class).setActivity(getActivity());
    }

    @Override
    public int getHelpResource() {
        // Disable the help icon because this page uses a full search view in actionbar.
        return 0;
    }

    @Override
    public Fragment getCallbackFragment() {
        return this;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        // Register SplitPairRule for SubSettings.
        ActivityEmbeddingRulesController.registerSubSettingsPairRule(getContext(),
                true /* clearTop */);

        setHighlightPreferenceKey(preference.getKey());
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        new SubSettingLauncher(getActivity())
                .setDestination(pref.getFragment())
                .setArguments(pref.getExtras())
                .setSourceMetricsCategory(caller instanceof Instrumentable
                        ? ((Instrumentable) caller).getMetricsCategory()
                        : Instrumentable.METRICS_CATEGORY_UNKNOWN)
                .setTitleRes(-1)
                .launch();
        return true;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mIsEmbeddingActivityEnabled =
                ActivityEmbeddingUtils.isEmbeddingActivityEnabled(getContext());
        if (!mIsEmbeddingActivityEnabled) {
            return;
        }

        if (icicle != null) {
            mHighlightMixin = icicle.getParcelable(SAVED_HIGHLIGHT_MIXIN);
        }
        if (mHighlightMixin == null) {
            mHighlightMixin = new TopLevelHighlightMixin();
        }
    }

    @Override
    public void onStart() {
        if (mFirstStarted) {
            mFirstStarted = false;
        } else if (mIsEmbeddingActivityEnabled && isOnlyOneActivityInTask()
                && !ActivityEmbeddingUtils.isTwoPaneResolution(getActivity())) {
            // Set default highlight menu key for 1-pane homepage since it will show the placeholder
            // page once changing back to 2-pane.
            Log.i(TAG, "Set default menu key");
            setHighlightMenuKey(getString(SettingsHomepageActivity.DEFAULT_HIGHLIGHT_MENU_KEY),
                    /* scrollNeeded= */ false);
        }
        super.onStart();
    }

    private boolean isOnlyOneActivityInTask() {
        final ActivityManager.RunningTaskInfo taskInfo = getSystemService(ActivityManager.class)
                .getRunningTasks(1).get(0);
        return taskInfo.numActivities == 1;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mHighlightMixin != null) {
            outState.putParcelable(SAVED_HIGHLIGHT_MIXIN, mHighlightMixin);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        final PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }
        setPreferenceLayout();
    }

    @Override
    public void onResume() {
        super.onResume();
        setIconStyles();
    }

    private void setPreferenceLayout() {
        final PreferenceScreen screen = getPreferenceScreen();
        final int count = screen.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            final Preference preference = screen.getPreference(i);

        String key = preference.getKey();

            if (key != null) {
                if (key.equals("top_level_about_device")){
                    preference.setLayoutResource(R.layout.about_phone_layout);
                }
                if (key.equals("top_level_network")){
                    preference.setLayoutResource(R.layout.top_preference_layout);
                }
                if (key.equals("top_level_connected_devices")){
                    preference.setLayoutResource(R.layout.bottom_preference_layout);
                }
                if (key.equals("top_level_apps")){
                    preference.setLayoutResource(R.layout.top_preference_layout);
                }
                if (key.equals("top_level_storage")){
                    preference.setLayoutResource(R.layout.middle_preference_layout);
                }
                if (key.equals("top_level_notifications")){
                    preference.setLayoutResource(R.layout.middle_preference_layout);
                }
                if (key.equals("top_level_battery")){
                    preference.setLayoutResource(R.layout.middle_preference_layout);
                }
                if (key.equals("top_level_location")){
                    preference.setLayoutResource(R.layout.bottom_preference_layout);
                }
                if (key.equals("top_level_sound")){
                    preference.setLayoutResource(R.layout.top_preference_layout);
                }
                if (key.equals("top_level_display")){
                    preference.setLayoutResource(R.layout.middle_preference_layout);
                }
                if (key.equals("top_level_wallpaper")){
                    preference.setLayoutResource(R.layout.middle_preference_layout);
                }
                if (key.equals("top_level_accessibility")){
                    preference.setLayoutResource(R.layout.bottom_preference_layout);
                }
                if (key.equals("top_level_security")){
                    preference.setLayoutResource(R.layout.top_preference_layout);
                }
                if (key.equals("top_level_privacy")){
                    preference.setLayoutResource(R.layout.middle_preference_layout);
                }
                if (key.equals("top_level_emergency")){
                    preference.setLayoutResource(R.layout.middle_preference_layout);
                }
                if (key.equals("top_level_accounts")){
                    preference.setLayoutResource(R.layout.middle_preference_layout);
                }
                if (key.equals("top_level_system")){
                    preference.setLayoutResource(R.layout.bottom_preference_layout);
                }
                if (key.equals("top_level_google")){
                    preference.setLayoutResource(R.layout.top_preference_layout_extra);
                    preference.setOrder(992);
                }
                if (key.equals("top_level_wellbeing")){
                    preference.setLayoutResource(R.layout.bottom_preference_layout_extra);
                    preference.setOrder(994);
                }
            }
	    }
    }

    private void setIconStyles() {
        final PreferenceScreen screen = getPreferenceScreen();
        final int count = screen.getPreferenceCount();
        final int[] colorfulTint = getContext().getResources().getIntArray(R.array.dashboard_icon_colors);

        int defValue = 0xff808080;

        int[] colors = new int[] {
                android.R.attr.textColorPrimaryInverse,
                android.R.attr.colorAccent,
                android.R.attr.colorBackgroundFloating
        };

        TypedArray ta = getContext().getTheme().obtainStyledAttributes(colors);
        final int normalColor = ta.getColor(0, defValue);
        final int accentColor = ta.getColor(1, defValue);
        final int background = ta.getColor(2, defValue);
        ta.recycle();

        int mDashboardIconStyle = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.DASHBOARD_ICON_STYLES, 0);

        for (int i = 0; i < count; i++) {
            final Preference preference = screen.getPreference(i);
            Drawable icon = preference.getIcon();
            if (icon != null) {
                if (icon instanceof LayerDrawable) {
                    LayerDrawable lIcon = (LayerDrawable) icon;
                    if(lIcon.getNumberOfLayers() == 2) {
                        Drawable bg = lIcon.getDrawable(0);
                        Drawable fg = lIcon.getDrawable(1);
                        bg.setTintList(null);
                        fg.setTintList(null);
                        fg.setTint(0);
                        bg.setTint(0);
                        switch(mDashboardIconStyle) {
                            case 0:
                                fg.setTint(accentColor);
                                break;
                            case 1:
                                fg.setTint(accentColor);
                                bg.setTint(background);
                                break;
                            case 2:
                                fg.setTint(normalColor);
                                bg.setTint(accentColor);
                                break;
                            case 3:
                                bg.setTint(accentColor);
                                bg.setAlpha(50);
                                fg.setTint(accentColor);
                                break;
                            case 4:
                                fg.setTint(colorfulTint[i]);
                                break;
                            case 5:
                                fg.setTint(colorfulTint[i]);
                                bg.setTint(colorfulTint[i]);
                                bg.setAlpha(50);
                                break;
                            case 6:
                                fg.setTint(colorfulTint[i]);
                                bg.setTint(background);
                                break;
                            case 7:
                                fg.setTint(normalColor);
                                bg.setTint(colorfulTint[i]);
                                break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        highlightPreferenceIfNeeded();
    }

    @Override
    public void highlightPreferenceIfNeeded() {
        if (mHighlightMixin != null) {
            mHighlightMixin.highlightPreferenceIfNeeded(getActivity());
        }
    }

    /** Returns a {@link TopLevelHighlightMixin} that performs highlighting */
    public TopLevelHighlightMixin getHighlightMixin() {
        return mHighlightMixin;
    }

    /** Highlight a preference with specified preference key */
    public void setHighlightPreferenceKey(String prefKey) {
        // Skip Tips & support since it's full screen
        if (mHighlightMixin != null && !TextUtils.equals(prefKey, PREF_KEY_SUPPORT)) {
            mHighlightMixin.setHighlightPreferenceKey(prefKey);
        }
    }

    /** Show/hide the highlight on the menu entry for the search page presence */
    public void setMenuHighlightShowed(boolean show) {
        if (mHighlightMixin != null) {
            mHighlightMixin.setMenuHighlightShowed(show);
        }
    }

    /** Highlight and scroll to a preference with specified menu key */
    public void setHighlightMenuKey(String menuKey, boolean scrollNeeded) {
        if (mHighlightMixin != null) {
            mHighlightMixin.setHighlightMenuKey(menuKey, scrollNeeded);
        }
    }

    @Override
    protected boolean shouldForceRoundedIcon() {
        return getContext().getResources()
                .getBoolean(R.bool.config_force_rounded_icon_TopLevelSettings);
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        if (!mIsEmbeddingActivityEnabled || !(getActivity() instanceof SettingsHomepageActivity)) {
            return super.onCreateAdapter(preferenceScreen);
        }
        return mHighlightMixin.onCreateAdapter(this, preferenceScreen);
    }

    @Override
    protected Preference createPreference(Tile tile) {
        return new HomepagePreference(getPrefContext());
    }

    void reloadHighlightMenuKey() {
        if (mHighlightMixin != null) {
            mHighlightMixin.reloadHighlightMenuKey(getArguments());
        }
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.top_level_settings) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    // Never searchable, all entries in this page are already indexed elsewhere.
                    return false;
                }
            };
}
