/*
 * Copyright (C) 2010 Thialfihar <thi@thialfihar.org>
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

package org.thialfihar.android.apg;

import java.io.File;

import org.bouncycastle2.bcpg.HashAlgorithmTags;
import org.bouncycastle2.openpgp.PGPEncryptedData;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

public class BaseActivity extends Activity
                          implements Runnable, ProgressDialogUpdater,
                          AskForSecretKeyPassPhrase.PassPhraseCallbackInterface {

    private ProgressDialog mProgressDialog = null;
    private Thread mRunningThread = null;

    private long mSecretKeyId = 0;
    private String mDeleteFile = null;
    protected static SharedPreferences mPreferences = null;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            handlerCallback(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mPreferences == null) {
            mPreferences = getPreferences(MODE_PRIVATE);
        }
        Apg.initialize(this);
    }

    @Override
    protected Dialog onCreateDialog(int id) {

        // in case it is a progress dialog
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(false);
        switch (id) {
            case Id.dialog.encrypting: {
                mProgressDialog.setMessage(this.getString(R.string.progress_initializing));
                return mProgressDialog;
            }

            case Id.dialog.decrypting: {
                mProgressDialog.setMessage(this.getString(R.string.progress_initializing));
                return mProgressDialog;
            }

            case Id.dialog.saving: {
                mProgressDialog.setMessage(this.getString(R.string.progress_saving));
                return mProgressDialog;
            }

            case Id.dialog.importing: {
                mProgressDialog.setMessage(this.getString(R.string.progress_importing));
                return mProgressDialog;
            }

            case Id.dialog.exporting: {
                mProgressDialog.setMessage(this.getString(R.string.progress_exporting));
                return mProgressDialog;
            }

            default: {
                break;
            }
        }
        mProgressDialog = null;

        switch (id) {
            case Id.dialog.pass_phrase: {
                return AskForSecretKeyPassPhrase.createDialog(this, getSecretKeyId(), this);
            }

            case Id.dialog.pass_phrases_do_not_match: {
                AlertDialog.Builder alert = new AlertDialog.Builder(this);

                alert.setIcon(android.R.drawable.ic_dialog_alert);
                alert.setTitle(R.string.error);
                alert.setMessage(R.string.passPhrasesDoNotMatch);

                alert.setPositiveButton(android.R.string.ok,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                removeDialog(Id.dialog.pass_phrases_do_not_match);
                                            }
                                        });
                alert.setCancelable(false);

                return alert.create();
            }

            case Id.dialog.no_pass_phrase: {
                AlertDialog.Builder alert = new AlertDialog.Builder(this);

                alert.setIcon(android.R.drawable.ic_dialog_alert);
                alert.setTitle(R.string.error);
                alert.setMessage(R.string.passPhraseMustNotBeEmpty);

                alert.setPositiveButton(android.R.string.ok,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                removeDialog(Id.dialog.no_pass_phrase);
                                            }
                                        });
                alert.setCancelable(false);

                return alert.create();
            }

            case Id.dialog.delete_file: {
                AlertDialog.Builder alert = new AlertDialog.Builder(this);

                alert.setIcon(android.R.drawable.ic_dialog_alert);
                alert.setTitle(R.string.warning);
                alert.setMessage(this.getString(R.string.fileDeleteConfirmation, getDeleteFile()));

                alert.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                removeDialog(Id.dialog.delete_file);
                                File file = new File(getDeleteFile());
                                String msg = "";
                                if (file.delete()) {
                                    msg = BaseActivity.this.getString(
                                            R.string.fileDeleteSuccessful);
                                } else {
                                    msg = BaseActivity.this.getString(
                                            R.string.errorMessage,
                                            BaseActivity.this.getString(
                                                    R.string.error_fileDeleteFailed, file));
                                }
                                Toast.makeText(BaseActivity.this,
                                               msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                alert.setNegativeButton(android.R.string.cancel,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                removeDialog(Id.dialog.delete_file);
                                            }
                                        });
                alert.setCancelable(true);

                return alert.create();
            }

            default: {
                break;
            }
        }

        return super.onCreateDialog(id);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Id.request.secret_keys: {
                if (resultCode == RESULT_OK) {
                    Bundle bundle = data.getExtras();
                    setSecretKeyId(bundle.getLong("selectedKeyId"));
                } else {
                    setSecretKeyId(Id.key.none);
                }
                break;
            }

            default: {
                break;
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public void setProgress(int resourceId, int progress, int max) {
        setProgress(getString(resourceId), progress, max);
    }

    public void setProgress(int progress, int max) {
        Message msg = new Message();
        Bundle data = new Bundle();
        data.putInt("type", Id.message.progress_update);
        data.putInt("progress", progress);
        data.putInt("max", max);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    public void setProgress(String message, int progress, int max) {
        Message msg = new Message();
        Bundle data = new Bundle();
        data.putInt("type", Id.message.progress_update);
        data.putString("message", message);
        data.putInt("progress", progress);
        data.putInt("max", max);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    public void handlerCallback(Message msg) {
        Bundle data = msg.getData();
        if (data == null) {
            return;
        }

        int type = data.getInt("type");
        switch (type) {
            case Id.message.progress_update: {
                String message = data.getString("message");
                if (mProgressDialog != null) {
                    if (message != null) {
                        mProgressDialog.setMessage(message);
                    }
                    mProgressDialog.setMax(data.getInt("max"));
                    mProgressDialog.setProgress(data.getInt("progress"));
                }
                break;
            }

            case Id.message.import_done: // intentionall no break
            case Id.message.export_done: // intentionall no break
            case Id.message.done: {
                mProgressDialog = null;
                doneCallback(msg);
                break;
            }
        }
    }

    public void doneCallback(Message msg) {

    }

    public void passPhraseCallback(long keyId, String passPhrase) {
        Apg.setCachedPassPhrase(keyId, passPhrase);
    }

    public void sendMessage(Message msg) {
        mHandler.sendMessage(msg);
    }

    public void startThread() {
        mRunningThread = new Thread(this);
        mRunningThread.start();
    }

    public void run() {

    }

    public void setSecretKeyId(long id) {
        mSecretKeyId = id;
    }

    public long getSecretKeyId() {
        return mSecretKeyId;
    }

    public int getDefaultEncryptionAlgorithm() {
        return mPreferences.getInt(Constants.pref.default_encryption_algorithm,
                                   PGPEncryptedData.AES_256);
    }

    public void setDefaultEncryptionAlgorithm(int value) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putInt(Constants.pref.default_encryption_algorithm, value);
        editor.commit();
    }

    public int getDefaultHashAlgorithm() {
        return mPreferences.getInt(Constants.pref.default_hash_algorithm,
                                   HashAlgorithmTags.SHA256);
    }

    public void setDefaultHashAlgorithm(int value) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putInt(Constants.pref.default_hash_algorithm, value);
        editor.commit();
    }

    public boolean getDefaultAsciiArmour() {
        return mPreferences.getBoolean(Constants.pref.default_ascii_armour, false);
    }

    public void setDefaultAsciiArmour(boolean value) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(Constants.pref.default_ascii_armour, value);
        editor.commit();
    }

    public boolean hasSeenChangeLog() {
        return mPreferences.getBoolean(Constants.pref.has_seen_change_log, false);
    }

    public void setHasSeenChangeLog(boolean value) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(Constants.pref.has_seen_change_log, value);
        editor.commit();
    }

    protected void setDeleteFile(String deleteFile) {
        mDeleteFile = deleteFile;
    }

    protected String getDeleteFile() {
        return mDeleteFile;
    }
}
