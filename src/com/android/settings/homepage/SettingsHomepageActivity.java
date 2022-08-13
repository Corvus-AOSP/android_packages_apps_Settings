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

import static android.provider.Settings.ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY;
import static android.provider.Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY;
import static android.provider.Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.animation.LayoutTransition;
import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.os.SystemProperties;
import android.os.Build;
import android.util.FeatureFlagUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toolbar;
import android.graphics.drawable.Drawable;
import android.animation.Animator;

import androidx.viewpager.widget.ViewPager;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.window.embedding.SplitRule;

import com.android.settings.corvus.CorvusSettings;

import com.android.settings.R;
import com.android.settings.Settings;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsApplication;
import com.android.settings.accounts.AvatarViewMixin;
import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.activityembedding.ActivityEmbeddingUtils;
import com.android.settings.core.CategoryMixin;
import com.android.settings.core.FeatureFlags;
import com.android.settings.homepage.contextualcards.ContextualCardsFragment;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.Utils;
import com.android.settingslib.core.lifecycle.HideNonSystemOverlayMixin;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import com.airbnb.lottie.LottieAnimationView;

import java.net.URISyntaxException;
import java.util.Set;

import java.util.ArrayList;

/** Settings homepage activity */
public class SettingsHomepageActivity extends FragmentActivity implements
        CategoryMixin.CategoryHandler {

    private static final String TAG = "SettingsHomepageActivity";

    // Additional extra of Settings#ACTION_SETTINGS_LARGE_SCREEN_DEEP_LINK.
    // Put true value to the intent when startActivity for a deep link intent from this Activity.
    public static final String EXTRA_IS_FROM_SETTINGS_HOMEPAGE = "is_from_settings_homepage";

    // Additional extra of Settings#ACTION_SETTINGS_LARGE_SCREEN_DEEP_LINK.
    // Set & get Uri of the Intent separately to prevent failure of Intent#ParseUri.
    public static final String EXTRA_SETTINGS_LARGE_SCREEN_DEEP_LINK_INTENT_DATA =
            "settings_large_screen_deep_link_intent_data";

    static final int DEFAULT_HIGHLIGHT_MENU_KEY = R.string.menu_key_network;
    private static final long HOMEPAGE_LOADING_TIMEOUT_MS = 300;

    private TopLevelSettings mMainFragment;
    private View mHomepageView;
    private View mSuggestionView;
    private View mTwoPaneSuggestionView;
    private CategoryMixin mCategoryMixin;
    private Set<HomepageLoadedListener> mLoadedListeners;
    private boolean mIsEmbeddingActivityEnabled;
    private boolean mIsTwoPaneLastTime;

    private TabLayout mTabLayout;
    private ViewPager mViewPager;

    LinearLayout linearLayout, topContent;
    Button btnRavenDesk;
    ImageView avatarView, btnCorvusVersion, wallpaperView, statusChip;
    TextView crvsVersion, crvsMaintainer, crvsDevice, crvsBuildDate, crvsBuildType, checkGapps;
    LottieAnimationView welcomeAnimation;

    /** A listener receiving homepage loaded events. */
    public interface HomepageLoadedListener {
        /** Called when the homepage is loaded. */
        void onHomepageLoaded();
    }

    private interface FragmentBuilder<T extends Fragment>  {
        T build();
    }

    /**
     * Try to add a {@link HomepageLoadedListener}. If homepage is already loaded, the listener
     * will not be notified.
     *
     * @return Whether the listener is added.
     */
    public boolean addHomepageLoadedListener(HomepageLoadedListener listener) {
        if (mHomepageView == null) {
            return false;
        } else {
            if (!mLoadedListeners.contains(listener)) {
                mLoadedListeners.add(listener);
            }
            return true;
        }
    }

    /**
     * Shows the homepage and shows/hides the suggestion together. Only allows to be executed once
     * to avoid the flicker caused by the suggestion suddenly appearing/disappearing.
     */
    public void showHomepageWithSuggestion(boolean showSuggestion) {
        if (mHomepageView == null) {
            return;
        }
        Log.i(TAG, "showHomepageWithSuggestion: " + showSuggestion);
        final View homepageView = mHomepageView;
        mSuggestionView.setVisibility(showSuggestion ? View.VISIBLE : View.GONE);
        mTwoPaneSuggestionView.setVisibility(showSuggestion ? View.VISIBLE : View.GONE);
        mHomepageView = null;

        mLoadedListeners.forEach(listener -> listener.onHomepageLoaded());
        mLoadedListeners.clear();
        homepageView.setVisibility(View.VISIBLE);
    }

    /** Returns the main content fragment */
    public TopLevelSettings getMainFragment() {
        return mMainFragment;
    }

    @Override
    public CategoryMixin getCategoryMixin() {
        return mCategoryMixin;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_homepage_container);
        mIsEmbeddingActivityEnabled = ActivityEmbeddingUtils.isEmbeddingActivityEnabled(this);
        mIsTwoPaneLastTime = ActivityEmbeddingUtils.isTwoPaneResolution(this);

        final View appBar = findViewById(R.id.app_bar_container);
        appBar.setMinimumHeight(getSearchBoxHeight());
        initHomepageContainer();
        updateHomepageAppBar();
        updateHomepageBackground();
        mLoadedListeners = new ArraySet<>();

        initSearchBarView();

        getLifecycle().addObserver(new HideNonSystemOverlayMixin(this));
        mCategoryMixin = new CategoryMixin(this);
        getLifecycle().addObserver(mCategoryMixin);

        final String highlightMenuKey = getHighlightMenuKey();
        // Only allow features on high ram devices.
        if (!getSystemService(ActivityManager.class).isLowRamDevice()) {
            initAvatarView();
            final boolean scrollNeeded = mIsEmbeddingActivityEnabled
                    && !TextUtils.equals(getString(DEFAULT_HIGHLIGHT_MENU_KEY), highlightMenuKey);
            showSuggestionFragment(scrollNeeded);
            if (FeatureFlagUtils.isEnabled(this, FeatureFlags.CONTEXTUAL_HOME)) {
                showFragment(() -> new ContextualCardsFragment(), R.id.contextual_cards_content);
            }
        }

        // Launch the intent from deep link for large screen devices.
        launchDeepLinkIntentToRight();
    }

    @Override
    protected void onStart() {
        ((SettingsApplication) getApplication()).setHomeActivity(this);
        super.onStart();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // When it's large screen 2-pane and Settings app is in the background, receiving an Intent
        // will not recreate this activity. Update the intent for this case.
        setIntent(intent);
        // reloadHighlightMenuKey();
        if (isFinishing()) {
            return;
        }
        // Launch the intent from deep link for large screen devices.
        launchDeepLinkIntentToRight();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        final boolean isTwoPane = ActivityEmbeddingUtils.isTwoPaneResolution(this);
        if (mIsTwoPaneLastTime != isTwoPane) {
            mIsTwoPaneLastTime = isTwoPane;
            updateHomepageAppBar();
            updateHomepageBackground();
        }
    }

    private void initSearchBarView() {
        final Toolbar toolbar = findViewById(R.id.search_action_bar);
        FeatureFactory.getFactory(this).getSearchFeatureProvider()
                .initSearchToolbar(this /* activity */, toolbar, SettingsEnums.SETTINGS_HOMEPAGE);

        if (mIsEmbeddingActivityEnabled) {
            final Toolbar toolbarTwoPaneVersion = findViewById(R.id.search_action_bar_two_pane);
            FeatureFactory.getFactory(this).getSearchFeatureProvider()
                    .initSearchToolbar(this /* activity */, toolbarTwoPaneVersion,
                            SettingsEnums.SETTINGS_HOMEPAGE);
        }
    }

    private void initAvatarView() {
        final ImageView avatarView = findViewById(R.id.account_avatar);
        final ImageView avatarTwoPaneView = findViewById(R.id.account_avatar_two_pane_version);
        if (AvatarViewMixin.isAvatarSupported(this)) {
            avatarView.setVisibility(View.VISIBLE);
            getLifecycle().addObserver(new AvatarViewMixin(this, avatarView));

            if (mIsEmbeddingActivityEnabled) {
                avatarTwoPaneView.setVisibility(View.VISIBLE);
                getLifecycle().addObserver(new AvatarViewMixin(this, avatarTwoPaneView));
            }
        }

        btnCorvusVersion = findViewById(R.id.btnCorvusVersion);
        btnCorvusVersion.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showBottomSheetDialog();
                }
            });
    }

    private void showBottomSheetDialog() {
        // Init the bottom sheet views
        final BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this, R.style.CorvusBottomSheetDialogTheme);
        bottomSheetDialog.setContentView(R.layout.corvus_bottom_sheet);

        final WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        final Drawable wallpaperDrawable = wallpaperManager.getDrawable();

        wallpaperView = bottomSheetDialog.findViewById(R.id.wallpaper_view);
        wallpaperView.setImageDrawable(wallpaperDrawable);
        wallpaperView.setImageAlpha(140);

        statusChip = bottomSheetDialog.findViewById(R.id.status_chip);

        linearLayout = bottomSheetDialog.findViewById(R.id.frame_build_type);
        topContent = bottomSheetDialog.findViewById(R.id.top_content_holder);

        welcomeAnimation = bottomSheetDialog.findViewById(R.id.welcome_animation);
        playWelcomeAnim();

        crvsDevice = bottomSheetDialog.findViewById(R.id.corvus_device);
        crvsVersion = bottomSheetDialog.findViewById(R.id.corvus_version);
        crvsMaintainer = bottomSheetDialog.findViewById(R.id.corvus_maintainer);
        crvsBuildDate = bottomSheetDialog.findViewById(R.id.corvus_build_date);
        crvsBuildType = bottomSheetDialog.findViewById(R.id.corvus_build_type);
        checkGapps = bottomSheetDialog.findViewById(R.id.check_gapps);

        String buildDate = SystemProperties.get("ro.corvus.build.date");
        String fullPackageName = SystemProperties.get("ro.corvus.version");

        crvsDevice.setText(SystemProperties.get("ro.corvus.device"));
        crvsVersion.setText("Corvus_v"
                + SystemProperties.get("ro.corvus.build.version")
                + "-"
                + SystemProperties.get("ro.corvus.codename"));
        crvsMaintainer.setText(R.string.maintainer_string);
        crvsBuildDate.setText(buildDate);
        String buildType = SystemProperties.get("ro.corvus.build.type");

        // Initialise intent for Ravendesk
        btnRavenDesk = bottomSheetDialog.findViewById(R.id.btn_ravendesk);

        // Initialize Volley request to retrieve maintainers list
        RequestQueue queue = Volley.newRequestQueue(this);

        String url = "https://raw.githubusercontent.com/CorvusRom-Devices/OTA/main/maintainers.txt";
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (buildType.equals("Official") && response.contains(SystemProperties.get("ro.corvus.maintainer"))) {
                        btnRavenDesk.setVisibility(View.VISIBLE);
                        statusChip.setBackgroundResource(R.drawable.icon_official);
                        linearLayout.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.corvus_official_color)));
                        crvsBuildType.setText(buildType);
                    } else {
                        statusChip.setBackgroundResource(R.drawable.icon_unofficial);
                        linearLayout.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.corvus_unofficial_color)));
                        crvsBuildType.setText("UnOfficial");  
                    }
                },
                error -> linearLayout.setVisibility(View.GONE));
        queue.add(stringRequest);

        if (fullPackageName.contains("Gapps")) {
            checkGapps.setText("Gapps");
        } else {
            checkGapps.setText("Vanilla");
        }

        assert btnRavenDesk != null;
        btnRavenDesk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent nIntent = new Intent(Intent.ACTION_MAIN);
                nIntent.setClassName("com.corvus.ravendesk",
                        "com.corvus.ravendesk.MainActivity");
                startActivity(nIntent);
            }
        });

        bottomSheetDialog.show();
    }

    private void playWelcomeAnim() {
        welcomeAnimation.addAnimatorListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                linearLayout.setVisibility(View.INVISIBLE);
                topContent.setVisibility(View.INVISIBLE);
                welcomeAnimation.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                welcomeAnimation.setVisibility(View.GONE);
                linearLayout.setVisibility(View.VISIBLE);
                topContent.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                // Yes, we need more useless boilerplate
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
                // Another Blank Space - Taylor swift
            }
        });
    }

    private void updateHomepageBackground() {
        if (!mIsEmbeddingActivityEnabled) {
            return;
        }

        final Window window = getWindow();
        final int color = ActivityEmbeddingUtils.isTwoPaneResolution(this)
                ? Utils.getColorAttrDefaultColor(this, com.android.internal.R.attr.colorSurface)
                : Utils.getColorAttrDefaultColor(this, android.R.attr.colorBackground);

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // Update status bar color
        window.setStatusBarColor(color);
        // Update content background.
        findViewById(R.id.settings_homepage_container).setBackgroundColor(color);
    }

    private void showSuggestionFragment(boolean scrollNeeded) {
        final Class<? extends Fragment> fragmentClass = FeatureFactory.getFactory(this)
                .getSuggestionFeatureProvider(this).getContextualSuggestionFragment();
        if (fragmentClass == null) {
            return;
        }

        mSuggestionView = findViewById(R.id.suggestion_content);
        mTwoPaneSuggestionView = findViewById(R.id.two_pane_suggestion_content);
        mHomepageView = findViewById(R.id.settings_homepage_container);
        // Hide the homepage for preparing the suggestion. If scrolling is needed, the list views
        // should be initialized in the invisible homepage view to prevent a scroll flicker.
        mHomepageView.setVisibility(scrollNeeded ? View.INVISIBLE : View.GONE);
        // Schedule a timer to show the homepage and hide the suggestion on timeout.
        mHomepageView.postDelayed(() -> showHomepageWithSuggestion(false),
                HOMEPAGE_LOADING_TIMEOUT_MS);
        final FragmentBuilder<?> fragmentBuilder = () -> {
            try {
                return fragmentClass.getConstructor().newInstance();
            } catch (Exception e) {
                Log.w(TAG, "Cannot show fragment", e);
            }
            return null;
        };
        showFragment(fragmentBuilder, R.id.suggestion_content);
        if (mIsEmbeddingActivityEnabled) {
            showFragment(fragmentBuilder, R.id.two_pane_suggestion_content);
        }
    }

    private <T extends Fragment> T showFragment(FragmentBuilder<T> fragmentBuilder, int id) {
        final FragmentManager fragmentManager = getSupportFragmentManager();
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        T showFragment = (T) fragmentManager.findFragmentById(id);

        if (showFragment == null) {
            showFragment = fragmentBuilder.build();
            fragmentTransaction.add(id, showFragment);
        } else {
            fragmentTransaction.show(showFragment);
        }
        fragmentTransaction.commit();
        return showFragment;
    }

    private void launchDeepLinkIntentToRight() {
        if (!mIsEmbeddingActivityEnabled) {
            return;
        }

        final Intent intent = getIntent();
        if (intent == null || !TextUtils.equals(intent.getAction(),
                ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY)) {
            return;
        }

        if (!(this instanceof DeepLinkHomepageActivity
                || this instanceof SliceDeepLinkHomepageActivity)) {
            Log.e(TAG, "Not a deep link component");
            finish();
            return;
        }

        final String intentUriString = intent.getStringExtra(
                EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI);
        if (TextUtils.isEmpty(intentUriString)) {
            Log.e(TAG, "No EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI to deep link");
            finish();
            return;
        }

        final Intent targetIntent;
        try {
            targetIntent = Intent.parseUri(intentUriString, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Failed to parse deep link intent: " + e);
            finish();
            return;
        }

        final ComponentName targetComponentName = targetIntent.resolveActivity(getPackageManager());
        if (targetComponentName == null) {
            Log.e(TAG, "No valid target for the deep link intent: " + targetIntent);
            finish();
            return;
        }
        targetIntent.setComponent(targetComponentName);

        // To prevent launchDeepLinkIntentToRight again for configuration change.
        intent.setAction(null);

        targetIntent.setFlags(targetIntent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK);
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);

        // Sender of intent may want to send intent extra data to the destination of targetIntent.
        targetIntent.replaceExtras(intent);

        targetIntent.putExtra(EXTRA_IS_FROM_SETTINGS_HOMEPAGE, true);
        targetIntent.putExtra(SettingsActivity.EXTRA_IS_FROM_SLICE, false);

        targetIntent.setData(intent.getParcelableExtra(
                SettingsHomepageActivity.EXTRA_SETTINGS_LARGE_SCREEN_DEEP_LINK_INTENT_DATA));

        // Set 2-pane pair rule for the deep link page.
        ActivityEmbeddingRulesController.registerTwoPanePairRule(this,
                new ComponentName(getApplicationContext(), getClass()),
                targetComponentName,
                targetIntent.getAction(),
                SplitRule.FINISH_ALWAYS,
                SplitRule.FINISH_ALWAYS,
                true /* clearTop */);
        ActivityEmbeddingRulesController.registerTwoPanePairRule(this,
                new ComponentName(getApplicationContext(), Settings.class),
                targetComponentName,
                targetIntent.getAction(),
                SplitRule.FINISH_ALWAYS,
                SplitRule.FINISH_ALWAYS,
                true /* clearTop */);
        startActivity(targetIntent);
    }

    private String getHighlightMenuKey() {
        final Intent intent = getIntent();
        if (intent != null && TextUtils.equals(intent.getAction(),
                ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY)) {
            final String menuKey = intent.getStringExtra(
                    EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY);
            if (!TextUtils.isEmpty(menuKey)) {
                return menuKey;
            }
        }
        return getString(DEFAULT_HIGHLIGHT_MENU_KEY);
    }

    // private void reloadHighlightMenuKey() {
    //     mMainFragment.getArguments().putString(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY,
    //             getHighlightMenuKey());
    //     mMainFragment.reloadHighlightMenuKey();
    // }

    private void initHomepageContainer() {
        mTabLayout = findViewById(R.id.tab_layout);
        mViewPager = findViewById(R.id.viewPager);

        mTabLayout.setupWithViewPager(mViewPager);
        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager(), FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        viewPagerAdapter.addFragment(new TopLevelSettings(), "Device Settings");
        viewPagerAdapter.addFragment(new CorvusSettings(), "Corvus Settings");
        mViewPager.setAdapter(viewPagerAdapter);
    }

    private void updateHomepageAppBar() {
        if (!mIsEmbeddingActivityEnabled) {
            return;
        }
        if (ActivityEmbeddingUtils.isTwoPaneResolution(this)) {
            findViewById(R.id.homepage_app_bar_regular_phone_view).setVisibility(View.GONE);
            findViewById(R.id.homepage_app_bar_two_pane_view).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.homepage_app_bar_regular_phone_view).setVisibility(View.VISIBLE);
            findViewById(R.id.homepage_app_bar_two_pane_view).setVisibility(View.GONE);
        }
    }

    private int getSearchBoxHeight() {
        final int searchBarHeight = getResources().getDimensionPixelSize(R.dimen.search_bar_height);
        final int searchBarMargin = getResources().getDimensionPixelSize(R.dimen.search_bar_margin);
        return searchBarHeight + searchBarMargin * 2;
    }

    static class ViewPagerAdapter extends FragmentPagerAdapter {

        private final ArrayList<Fragment> fragmentArrayList = new ArrayList<>();
        private final ArrayList<String> fragmentTitle = new ArrayList<>();

        public ViewPagerAdapter(@NonNull FragmentManager fm, int behavior) {
            super(fm, behavior);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return fragmentArrayList.get(position);
        }

        @Override
        public int getCount() {
            return fragmentArrayList.size();
        }

        public void addFragment(Fragment fragment, String title){
            fragmentArrayList.add(fragment);
            fragmentTitle.add(title);
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return fragmentTitle.get(position);
        }
    }
}
