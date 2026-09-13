package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;

public class ImportActivity extends Activity {
    private static final int IMPORT_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, IMPORT_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == IMPORT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                final Uri treeUri = resultData.getData();
                if (treeUri != null) {
                    final DocumentFile treeDocument = DocumentFile.fromTreeUri(this, treeUri);
                    if (treeDocument != null) {
                        copyFiles(treeDocument);
                        return;
                    }
                }
            }

            finish();
        } else {
            super.onActivityResult(requestCode, resultCode, resultData);
        }
    }

    private void copyFiles(DocumentFile treeDocument) {
        ProgressDialog dialog = createProgressDialog();
        dialog.show();

        new Thread(() -> {
            ContentResolver contentResolver = getContentResolver();
            boolean success=false;
            try (GameSession lock=GameSession.tryAcquire(getFilesDir())) {
                if(lock!=null) {
                    File directory=new SettingsRepository(this).gameDirectory();
                    if(directory.isDirectory()||directory.mkdirs())
                        success=FileUtils.copyRecursively(contentResolver,treeDocument,directory);
                }
            } catch(java.io.IOException ignored) {}
            final boolean copied=success;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                dialog.dismiss();
                if (copied) {
                    startLauncherActivity();
                    finish();
                } else {
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Import failed")
                            .setMessage("Could not copy all game files. Check the selected folder and available storage space, then try again.")
                            .setPositiveButton("Back", (prompt, which) -> finish()).show();
                }
            });
        }).start();
    }

    private void startLauncherActivity() {
        Intent intent = GameProfiles.intent(this, LauncherActivity.class);
        startActivity(intent);
    }

    private ProgressDialog createProgressDialog() {
        ProgressDialog progressDialog = new ProgressDialog(this,
            android.R.style.Theme_Material_Light_Dialog);
        progressDialog.setTitle(R.string.loading_dialog_title);
        progressDialog.setMessage(getString(R.string.loading_dialog_message));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(false);

        return progressDialog;
    }
}
