/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.settings.deviceinfo;

import android.content.Intent;
import android.os.Bundle;
import android.os.storage.DiskInfo;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.settings.InstrumentedFragment;
import com.android.settings.R;

public class PrivateVolumeFormat extends InstrumentedFragment {
    private VolumeInfo mVolume;
    private DiskInfo mDisk;
    /* SPRD: 538966 add StorageListener @{ */
    private StorageManager mStorageManager;
    private static final String TAG = "PrivateVolumeFormat";
    /* @} */


    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.DEVICEINFO_STORAGE;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        /* SPRD: 538966 add StorageListener @{ */
        mStorageManager = getActivity().getSystemService(StorageManager.class);
        mStorageManager.registerListener(mStorageListener);
        /* @} */
        final StorageManager storage = getActivity().getSystemService(StorageManager.class);
        final String volumeId = getArguments().getString(VolumeInfo.EXTRA_VOLUME_ID);
        mVolume = storage.findVolumeById(volumeId);
        /* SPRD: 538966 add StorageListener @{ */
        if (mVolume != null) {
            mDisk = storage.findDiskById(mVolume.getDiskId());
        }
        /* @} */
        final View view = inflater.inflate(R.layout.storage_internal_format, container, false);
        final TextView body = (TextView) view.findViewById(R.id.body);
        final Button confirm = (Button) view.findViewById(R.id.confirm);
        /* SPRD: 538966 add StorageListener @{ */
        if (mDisk != null) {
            body.setText(TextUtils.expandTemplate(getText(R.string.storage_internal_format_details),
                    mDisk.getDescription()));
        }
        /* @} */
        confirm.setOnClickListener(mConfirmListener);

        return view;
    }

    private final OnClickListener mConfirmListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Intent intent = new Intent(getActivity(), StorageWizardFormatProgress.class);
            intent.putExtra(DiskInfo.EXTRA_DISK_ID, mDisk.getId());
            intent.putExtra(StorageWizardFormatConfirm.EXTRA_FORMAT_PRIVATE, false);
            intent.putExtra(StorageWizardFormatConfirm.EXTRA_FORGET_UUID, mVolume.getFsUuid());
            startActivity(intent);
            getActivity().finish();
        }
    };

    /* SPRD: 538966 add StorageListener @{ */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mStorageManager != null && mStorageListener != null) {
            try {
                mStorageManager.unregisterListener(mStorageListener);
            } catch (Exception e) {
                Log.i(TAG,"unregisterListener... exception");
            }
        }
    }

    StorageEventListener mStorageListener = new StorageEventListener() {
        @Override
        public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
            if (VolumeInfo.STATE_EJECTING == newState || VolumeInfo.STATE_REMOVED == newState
                    || VolumeInfo.STATE_UNMOUNTED == newState || VolumeInfo.STATE_BAD_REMOVAL == newState) {
                if (getActivity() != null) {
                    Toast.makeText(getActivity(), R.string.sdcard_unmounted, Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
            }
        }
    };
    /* @} */

}
