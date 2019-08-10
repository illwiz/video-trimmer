package dev.illwiz.videotrimmer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.snackbar.Snackbar;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import dev.illwiz.videotrimmer.trimmer.TelegramActivity;
import dev.illwiz.videotrimmer.utils.Prop;
import dev.illwiz.videotrimmer.utils.Utils;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_PERMISSION_READ_WRITE_STORAGE = 1;
    private static final int REQ_SELECT_VIDEO = 2;
    private static final int REQ_TRIM_VIDEO = 3;
    @BindView(R.id.selectVideoBtn)
    Button selectVideoBtn;

    private String[] VIDEO_TRIMMERS = new String[]{
        "Telegram"
    };
    private Uri videoFile;
    private AlertDialog selectTrimmerDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        initialize();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQ_PERMISSION_READ_WRITE_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    selectVideo();
                    showMessage("Storage access granted");
                } else {
                    showMessage("Require to access storage!");
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_SELECT_VIDEO) {
                videoFile = data.getData();
                selectVideoBtn.setText(videoFile.toString());
                showSelectTrimmerDialog(true);
            } else if(requestCode == REQ_TRIM_VIDEO) {
                showMessage("Trim video success");
            }
        }
    }

    @OnClick(R.id.selectVideoBtn)
    public void selectVideoBtn() {
        if(!Utils.permissionGranted(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE
                    ,Manifest.permission.WRITE_EXTERNAL_STORAGE},REQ_PERMISSION_READ_WRITE_STORAGE);
            return;
        }
        if(selectVideoBtn.getText().toString().equals("Select Video")) {
            selectVideo();
        } else {
            showSelectTrimmerDialog(true);
        }
    }

    private void initialize() {

    }

    private void selectVideo() {
        Intent intent = new Intent();
        intent.setType("video/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,"Select Video"),REQ_SELECT_VIDEO);
    }

    private String getPath(Uri uri) {
        String[] projection = { MediaStore.Video.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            // HERE YOU WILL GET A NULLPOINTER IF CURSOR IS NULL
            // THIS CAN BE, IF YOU USED OI FILE MANAGER FOR PICKING THE MEDIA
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } else
            return null;
    }

    private void showSelectTrimmerDialog(boolean show) {
        if(show) {
            if(selectTrimmerDialog==null) {
                selectTrimmerDialog = new AlertDialog.Builder(this)
                    .setSingleChoiceItems(VIDEO_TRIMMERS,0,(dialogInterface, index) -> {
                        Class trimmerActivityClass = null;
                        if(index==0) { // Telegram
                            trimmerActivityClass = TelegramActivity.class;
                        }
                        Intent intent = new Intent(this,trimmerActivityClass);
                        intent.putExtra(Prop.MAIN_OBJ,videoFile);
                        startActivityForResult(intent,REQ_TRIM_VIDEO);
                        dialogInterface.dismiss();
                    })
                    .create();
            }
            selectTrimmerDialog.show();
        } else if(selectTrimmerDialog!=null) {
            selectTrimmerDialog.dismiss();
        }
    }

    private void showMessage(String message) {
        Snackbar.make(findViewById(android.R.id.content),message,Snackbar.LENGTH_LONG).show();
    }
}
