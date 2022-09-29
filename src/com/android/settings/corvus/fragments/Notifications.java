/*
 * Copyright (C) 2020-22 CorvusROM
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

package com.android.settings.corvus.fragments;

import android.content.ContentResolver;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.graphics.Color;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.search.SearchIndexable;

import com.corvus.support.preferences.SystemSettingSwitchPreference;
import com.corvus.support.preferences.SystemSettingMasterSwitchPreference;

import com.android.internal.util.corvus.CorvusUtils;

@SearchIndexable
public class Notifications extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_EDGE_LIGHTING = "pulse_ambient_light";
    private static final String NEW_RETICKER = "new_reticker";
    private static final String NEW_RETICKER_ANIMATION = "new_reticker_animation";

    private SystemSettingMasterSwitchPreference mEdgeLighting;
    private SystemSettingSwitchPreference mNewReticker, mNewRetickerAnimation;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ContentResolver resolver = getActivity().getContentResolver();

        addPreferencesFromResource(R.xml.notifications);

        final PreferenceScreen screen = getPreferenceScreen();

        mEdgeLighting = (SystemSettingMasterSwitchPreference)
                findPreference(KEY_EDGE_LIGHTING);
        boolean enabled = Settings.System.getIntForUser(resolver,
                KEY_EDGE_LIGHTING, 0, UserHandle.USER_CURRENT) == 1;
        mEdgeLighting.setChecked(enabled);
        mEdgeLighting.setOnPreferenceChangeListener(this);

        mNewReticker = (SystemSettingSwitchPreference) findPreference(NEW_RETICKER);
        mNewReticker.setOnPreferenceChangeListener(this);
        mNewRetickerAnimation = (SystemSettingSwitchPreference) findPreference(NEW_RETICKER_ANIMATION);
        mNewRetickerAnimation.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mEdgeLighting) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver, KEY_EDGE_LIGHTING,
                    value ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mNewReticker || preference == mNewRetickerAnimation) {
            CorvusUtils.showSystemUiRestartDialog(getActivity());
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CORVUS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.notifications);
}
