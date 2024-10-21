/*
 * Copyright (C) 2016-2022 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crdroid.settings.fragments;

import com.android.server.AttestationService.AttestationServiceUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.android.internal.util.rising.SystemRestartUtils;

import com.android.internal.util.crdroid.KeyProviderManager;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

@SearchIndexable
public class KeyboxSpoof extends SettingsPreferenceFragment implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "KeyboxSpoof";
    private static final String SYS_KEY_ATTESTATION_SPOOF = "persist.sys.pihooks.enable.key_attestation_spoof";
    private static final String SYS_KEYBOX_USE_XML = "persist.sys.pihooks.key_attestation_use_xml";
    private static final String KEY_KEYBOX_XML_FILE_PREFERENCE = "keybox_spoof_load_xml";
    private static final String KEY_KEYBOX_SPOOF_STATUS = "keybox_spoof_status";

    private static final String KEYBOX_DATA_FILENAME = "user_keybox.xml";
    private File KEYBOX_DATA_FILE = new File(Environment.getDataSystemDirectory(), KEYBOX_DATA_FILENAME);

    private Preference mKeyAttestationSpoof;
    private Preference mKeyAttestationSpoofUseXML;
    private Preference mKeyboxFilePreference;
    private Preference mKeyboxSpoofStatus;

    private static String getSpoofMode() {
        String spoofMode;

        if (SystemProperties.getBoolean(SYS_KEYBOX_USE_XML, false) 
        && KeyProviderManager.isKeyboxAvailable()) {
            spoofMode = "XML";
        } else {
            spoofMode = "Overlay";
        }
        return spoofMode;
    }

    private static HashMap<String, String> getSpoofingMetadata() {
        HashMap<String, String> metadata = new HashMap<>();

        String areWeSpoofing = SystemProperties.getBoolean(SYS_KEY_ATTESTATION_SPOOF, false) ? "Yes" : "No";

        metadata.put("Mode", getSpoofMode());
        metadata.put("Enabled", areWeSpoofing);
        metadata.put("Keybox Loaded?", KeyProviderManager.isKeyboxAvailable() ? "Yes" : "No");

        return metadata;
    }

     public void showHashMapDialog(HashMap<String, String> hashMap, @Nullable String dialogTitle) {
        // Convert HashMap contents to a readable String format
        StringBuilder message = new StringBuilder();
        for (Map.Entry<String, String> entry : hashMap.entrySet()) {
            message.append(entry.getKey())
                   .append(": ")
                   .append(entry.getValue())
                   .append("\n");
        }

        new AlertDialog.Builder(getContext())
                .setTitle(dialogTitle)  // Title of the dialog
                .setMessage(message.toString()) // The formatted string
                .setPositiveButton(android.R.string.ok, (dialog, which) -> { dialog.dismiss();}) 
                .create()
                .show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.crdroid_settings_spoof_keybox);

        mKeyAttestationSpoof = findPreference(SYS_KEY_ATTESTATION_SPOOF);
        mKeyAttestationSpoofUseXML = findPreference(SYS_KEYBOX_USE_XML);
        mKeyboxFilePreference = findPreference(KEY_KEYBOX_XML_FILE_PREFERENCE);
        mKeyboxSpoofStatus = findPreference(KEY_KEYBOX_SPOOF_STATUS);


        mKeyboxFilePreference.setOnPreferenceClickListener(preference -> {
            openXMLFileSelector(10003);
            return true;
        });

        mKeyboxSpoofStatus.setOnPreferenceClickListener(preference -> {
            showHashMapDialog(getSpoofingMetadata(), getString(R.string.keybox_spoof_screen_title));
            return true;
        });

        mKeyAttestationSpoof.setOnPreferenceChangeListener(this);
    }

    private void openXMLFileSelector(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/xml");
        startActivityForResult(intent, requestCode);
    }

    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void writeToFileFromUri(Uri fileUri, File destinationFile, ContentResolver resolver) throws Exception {
       try {
        // Get input stream from the Uri
        InputStream inputStream = resolver.openInputStream(fileUri);

            if (inputStream != null) {
                OutputStream outputStream = new FileOutputStream(destinationFile, false); // Overwrite if exists

            // Buffer for transferring data
                byte[] buffer = new byte[1024];
                int length;

            // Write the file
                while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            // Close streams
                inputStream.close();
                outputStream.close();

                Log.d("FileWrite", "File written successfully to " + destinationFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e("FileWrite", "Error writing to " + e.getMessage(), e);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mKeyAttestationSpoof 
        && newValue instanceof Boolean 
        && (Boolean) newValue) {
            try {
                AttestationService.AttestationServiceUtils.reloadKeybox();
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
                e.printStackTrace(); 
                showToast(getContext(), getString(R.string.keybox_xml_load_toast_error_message));
            }
        }
    return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                if (requestCode == 10003) {
                    boolean exceptionThrown = false;
                    try {
                    AttestationService.AttestationServiceUtils.writeToFileFromUri(uri, KEYBOX_DATA_FILE, getContentResolver());
                    } catch (Exception e) {
                        Log.d(TAG, e.getMessage());
                        e.printStackTrace();
                        showToast(getContext(), getString(R.string.keybox_xml_load_toast_error_message));
                        exceptionThrown = true;  
                    } finally {
                        try {
                        AttestationService.AttestationServiceUtils.reloadKeybox();
                        } catch (Exception err) {
                            exceptionThrown = true;
                            Log.d(TAG, err.getMessage());
                            err.printStackTrace(); 
                            showToast(getContext(), getString(R.string.keybox_xml_load_toast_error_message));
                        }
                        if (!exceptionThrown && KeyProviderManager.isKeyboxAvailable()) {
                            showToast(getContext(), getString(R.string.keybox_xml_load_toast_message));
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_spoof_keybox) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    return keys;
                }
            };
     }