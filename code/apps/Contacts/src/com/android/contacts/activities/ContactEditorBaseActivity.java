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
 * limitations under the License
 */

package com.android.contacts.activities;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.editor.ContactEditorBaseFragment;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.editor.EditorIntents;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.util.DialogManager;

import android.app.ActionBar;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
/**
 * SPRD:
 * @{
 */
import android.widget.Toast;
/**
 * @}
 */

/**
 * Base Activity for contact editors.
 */
abstract public class ContactEditorBaseActivity extends ContactsActivity
        implements DialogManager.DialogShowingViewActivity {
    protected static final String TAG = "ContactEditorActivity";

    /**
     * Intent action to edit a contact with all available field inputs displayed.
     *
     * Only used to open the "fully expanded" editor -- {@link ContactEditorActivity}.
     */
    public static final String ACTION_EDIT = "com.android.contacts.action.FULL_EDIT";

    /**
     * Intent action to insert a new contact with all available field inputs displayed.
     *
     * Only used to open the "fully expanded" editor -- {@link ContactEditorActivity}.
     */
    public static final String ACTION_INSERT = "com.android.contacts.action.FULL_INSERT";

    public static final String ACTION_JOIN_COMPLETED = "joinCompleted";
    public static final String ACTION_SAVE_COMPLETED = "saveCompleted";

    /**
     * Contract for contact editors Fragments that are managed by this Activity.
     */
    public interface ContactEditor {

        /**
         * Modes that specify what the AsyncTask has to perform after saving
         */
        public interface SaveMode {
            /**
             * Close the editor after saving
             */
            public static final int CLOSE = 0;

            /**
             * Reload the data so that the user can continue editing
             */
            public static final int RELOAD = 1;

            /**
             * Split the contact after saving
             */
            public static final int SPLIT = 2;

            /**
             * Join another contact after saving
             */
            public static final int JOIN = 3;

            /**
             * Navigate to the compact editor view after saving.
             */
            public static final int COMPACT = 4;
        }

        /**
         * The status of the contact editor.
         */
        public interface Status {
            /**
             * The loader is fetching data
             */
            public static final int LOADING = 0;

            /**
             * Not currently busy. We are waiting for the user to enter data
             */
            public static final int EDITING = 1;

            /**
             * The data is currently being saved. This is used to prevent more
             * auto-saves (they shouldn't overlap)
             */
            public static final int SAVING = 2;

            /**
             * Prevents any more saves. This is used if in the following cases:
             * - After Save/Close
             * - After Revert
             * - After the user has accepted an edit suggestion
             * - After the user chooses to expand the compact editor
             */
            public static final int CLOSING = 3;

            /**
             * Prevents saving while running a child activity.
             */
            public static final int SUB_ACTIVITY = 4;
        }

        /**
         * Sets the hosting Activity that will receive callbacks from the contact editor.
         */
        void setListener(ContactEditorBaseFragment.Listener listener);

        /**
         * Initialize the contact editor.
         */
        void load(String action, Uri lookupUri, Bundle intentExtras);

        /**
         * Applies extras from the hosting Activity to the first writable raw contact.
         */
        void setIntentExtras(Bundle extras);

        /**
         * Saves or creates the contact based on the mode, and if successful
         * finishes the activity.
         *
         * @param backPressed whether the save was initiated as a result of a back button press
         *         or because the framework stopped the editor Activity
         */
        boolean save(int saveMode, boolean backPressed);

        /**
         * If there are no unsaved changes, just close the editor, otherwise the user is prompted
         * before discarding unsaved changes.
         */
        boolean revert();

        /**
         * Invoked after the contact is saved.
         */
        void onSaveCompleted(boolean hadChanges, int saveMode, boolean saveSucceeded,
                Uri contactLookupUri, Bundle updatedPhotos, boolean backPressed, long photoId,
                long nameId);
        /**
         * Invoked after the contact is joined.
         */
        /**
         * SPRD:
         */
        void onSaveCompleted(boolean hadChanges, int saveMode, boolean saveSucceeded,
                Uri contactLookupUri, Bundle updatedPhotos, boolean backPressed, long photoId,
                long nameId, int errorToast);
        /**
         * @}
         */
        void onJoinCompleted(Uri uri);
    }

    /**
     * Boolean intent key that specifies that this activity should finish itself
     * (instead of launching a new view intent) after the editor changes have been
     * saved.
     */
    public static final String INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED =
            "finishActivityOnSaveCompleted";

    protected ContactEditor mFragment;
    private boolean mFinishActivityOnSaveCompleted;

    private DialogManager mDialogManager = new DialogManager(this);
    /**
     * SPRD bug538816 During editing contacts, add photo for it, change language, back to
     * editor view, click join in menu, save and back key lose efficacy
     * SPRD bug542650 During editing contacts, add photo for it, change font size, back to
     * editor view, click join in menu, save and back key lost efficacy
     * SPRD bug545538 During editing contacts, take photo for it, cut to horizontal screen
     * before clicking save button, contact editor finish and data has not been saved
     * @{
     */
    private Handler mListenerHandler;
    private static final int MESSAGE_LOCALE_CHANGED = 0;
    private static final int MESSAGE_CONFIGURATION_CHANGED = 1;
    private float mFontScale;
    /**
     * @}
     */

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        // Determine whether or not this activity should be finished after the user is done
        // editing the contact or if this activity should launch another activity to view the
        // contact's details.
        mFinishActivityOnSaveCompleted = intent.getBooleanExtra(
                INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, false);

        // The only situation where action could be ACTION_JOIN_COMPLETED is if the
        // user joined the contact with another and closed the activity before
        // the save operation was completed.  The activity should remain closed then.
        if (ACTION_JOIN_COMPLETED.equals(action)) {
            finish();
            return;
        }

        if (ACTION_SAVE_COMPLETED.equals(action)) {
            finish();
            return;
        }

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            if (Intent.ACTION_EDIT.equals(action) || ACTION_EDIT.equals(action)) {
                actionBar.setTitle(getResources().getString(
                        R.string.contact_editor_title_existing_contact));
            } else {
                actionBar.setTitle(getResources().getString(
                        R.string.contact_editor_title_new_contact));
            }
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        /**
         * SPRD bug538816 During editing contacts, add photo for it, change language, back to
         * editor view, click join in menu, save and back key lose efficacy
         * SPRD bug542650 During editing contacts, add photo for it, change font size, back to
         * editor view, click join in menu, save and back key lost efficacy
         * SPRD bug545538 During editing contacts, take photo for it, cut to horizontal screen
         * before clicking save button, contact editor finish and data has not been saved
         * @{
         */
        IntentFilter filter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(mBroadcastReceiver, filter);
        mFontScale = getResources().getConfiguration().fontScale;
        mListenerHandler = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case MESSAGE_LOCALE_CHANGED:
                    finish();
                case MESSAGE_CONFIGURATION_CHANGED:
                    Configuration config = getResources().getConfiguration();
                    if (config.fontScale != mFontScale) {
                        finish();
                    }
                default:
                    break;
                }
            }
        };
        /**
         * @}
         */
    }

    /**
     * SPRD bug538816 During editing contacts, add photo for it, change language, back to
     * editor view, click join in menu, save and back key lose efficacy
     * SPRD bug542650 During editing contacts, add photo for it, change font size, back to
     * editor view, click join in menu, save and back key lost efficacy
     * @{
     */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED)) {
                mListenerHandler.sendEmptyMessage(MESSAGE_LOCALE_CHANGED);
            } else if (intent.getAction().equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                mListenerHandler.sendEmptyMessage(MESSAGE_CONFIGURATION_CHANGED);
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
    }
    /**
     * @}
     */

    @Override
    protected void onPause() {
        super.onPause();
        final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        final View currentFocus = getCurrentFocus();
        if (imm != null && currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (mFragment == null) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_EDIT.equals(action) || ACTION_EDIT.equals(action)) {
            mFragment.setIntentExtras(intent.getExtras());
            /**
             * SPRD: bug425303, add Usim group name length and capacity exception process.
             *
             * @{
             */
            int errorToastForGroupCreate =
                    intent.getIntExtra(ContactSaveService.EXTRA_ERROR_TOAST, 0);
            if (errorToastForGroupCreate != 0) {
                Log.d(TAG, "errorToastForGroupCreate toast.show ");
                Toast.makeText(this, errorToastForGroupCreate, Toast.LENGTH_SHORT).show();
            }
            /**
             * @}
             */
        } else if (ACTION_SAVE_COMPLETED.equals(action)) {
            /**
             * SPRD:Bug 426619 Contacts crashed when create a new SIM contact when SIM card has full.
            mFragment.onSaveCompleted(true,
                    intent.getIntExtra(ContactEditorFragment.SAVE_MODE_EXTRA_KEY,
                            ContactEditor.SaveMode.CLOSE),
                    intent.getBooleanExtra(ContactSaveService.EXTRA_SAVE_SUCCEEDED, false),
                    intent.getData(),
                    (Bundle) intent.getParcelableExtra(ContactSaveService.EXTRA_UPDATED_PHOTOS),
                    intent.getBooleanExtra(ContactEditorFragment.INTENT_EXTRA_SAVE_BACK_PRESSED,
                            false),
                    intent.getLongExtra(ContactEditorFragment.INTENT_EXTRA_PHOTO_ID, -1),
                    intent.getLongExtra(ContactEditorFragment.INTENT_EXTRA_NAME_ID, -1));
             * @{
             */
            // add for Bug545540 if there is no data in contacts, delete the contacts by
            // ContactDeletionInteraction
            boolean shouldDelete = intent.getBooleanExtra(
                    ContactSaveService.EXTRA_SHOULD_DELETE, false);
            Log.d(TAG, "ContactEditorBaseActivity : onNewIntent : shouldDelete = "
                    + shouldDelete);
            if (shouldDelete) {
                setResult(ContactDeletionInteraction.RESULT_CODE_DELETED);
                finish();
                return;
            }
            mFragment.onSaveCompleted(true,
                    intent.getIntExtra(ContactEditorFragment.SAVE_MODE_EXTRA_KEY,
                            ContactEditor.SaveMode.CLOSE),
                    intent.getBooleanExtra(ContactSaveService.EXTRA_SAVE_SUCCEEDED, false),
                    intent.getData(),
                    (Bundle) intent.getParcelableExtra(ContactSaveService.EXTRA_UPDATED_PHOTOS),
                    intent.getBooleanExtra(ContactEditorFragment.INTENT_EXTRA_SAVE_BACK_PRESSED,
                            false),
                    intent.getLongExtra(ContactEditorFragment.INTENT_EXTRA_PHOTO_ID, -1),
                    intent.getLongExtra(ContactEditorFragment.INTENT_EXTRA_NAME_ID, -1),
                    intent.getIntExtra(ContactSaveService.EXTRA_ERROR_TOAST, 0));
            /**
             * @}
             */
        } else if (ACTION_JOIN_COMPLETED.equals(action)) {
            mFragment.onJoinCompleted(intent.getData());
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (DialogManager.isManagedId(id)) return mDialogManager.onCreateDialog(id, args);

        // Nobody knows about the Dialog
        Log.w(TAG, "Unknown dialog requested, id: " + id + ", args: " + args);
        return null;
    }

    protected final ContactEditorBaseFragment.Listener  mFragmentListener =
            new ContactEditorBaseFragment.Listener() {

        @Override
        public void onDeleteRequested(Uri contactUri) {
            ContactDeletionInteraction.start(ContactEditorBaseActivity.this, contactUri, true);
        }

        @Override
        public void onReverted() {
            finish();
        }

        @Override
        public void onSaveFinished(Intent resultIntent) {
            final boolean backPressed = resultIntent == null ? false : resultIntent.getBooleanExtra(
                    ContactEditorBaseFragment.INTENT_EXTRA_SAVE_BACK_PRESSED, false);
            if (mFinishActivityOnSaveCompleted) {
                setResult(resultIntent == null ? RESULT_CANCELED : RESULT_OK, resultIntent);
            } else if (resultIntent != null) {
                if (backPressed) {
                    ImplicitIntentsUtil.startActivityInApp(ContactEditorBaseActivity.this,
                            resultIntent);
                }
            }
            finish();
        }

        @Override
        public void onContactSplit(Uri newLookupUri) {
            finish();
        }

        @Override
        public void onContactNotFound() {
            finish();
        }

        @Override
        public void onEditOtherContactRequested(
                Uri contactLookupUri, ArrayList<ContentValues> values) {
            final Intent intent = EditorIntents.createEditOtherContactIntent(
                    contactLookupUri, values);
            ImplicitIntentsUtil.startActivityInApp(ContactEditorBaseActivity.this, intent);
            finish();
        }

        @Override
        public void onCustomCreateContactActivityRequested(AccountWithDataSet account,
                Bundle intentExtras) {
            final AccountTypeManager accountTypes =
                    AccountTypeManager.getInstance(ContactEditorBaseActivity.this);
            final AccountType accountType = accountTypes.getAccountType(
                    account.type, account.dataSet);

            Intent intent = new Intent();
            intent.setClassName(accountType.syncAdapterPackageName,
                    accountType.getCreateContactActivityClassName());
            intent.setAction(Intent.ACTION_INSERT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            if (intentExtras != null) {
                intent.putExtras(intentExtras);
            }
            intent.putExtra(RawContacts.ACCOUNT_NAME, account.name);
            intent.putExtra(RawContacts.ACCOUNT_TYPE, account.type);
            intent.putExtra(RawContacts.DATA_SET, account.dataSet);
            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            startActivity(intent);
            finish();
        }

        @Override
        public void onCustomEditContactActivityRequested(AccountWithDataSet account,
                Uri rawContactUri, Bundle intentExtras, boolean redirect) {
            final AccountTypeManager accountTypes =
                    AccountTypeManager.getInstance(ContactEditorBaseActivity.this);
            final AccountType accountType = accountTypes.getAccountType(
                    account.type, account.dataSet);

            Intent intent = new Intent();
            intent.setClassName(accountType.syncAdapterPackageName,
                    accountType.getEditContactActivityClassName());
            intent.setAction(Intent.ACTION_EDIT);
            intent.setData(rawContactUri);
            if (intentExtras != null) {
                intent.putExtras(intentExtras);
            }

            if (redirect) {
                intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                startActivity(intent);
                finish();
            } else {
                startActivity(intent);
            }
        }
    };

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }
}
