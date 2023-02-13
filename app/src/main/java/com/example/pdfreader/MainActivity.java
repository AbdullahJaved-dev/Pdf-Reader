package com.example.pdfreader;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private PdfAdapter pdfAdapter;
    private List<File> pdfList;
    private RecyclerView recyclerView;

    private final static String Pdf=Environment.getExternalStorageDirectory().getPath()+"/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        runtimePermission();

    }

    private void runtimePermission() {

        Dexter.withContext(MainActivity.this).withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        displayPdf();
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        Toast.makeText(MainActivity.this, "Permission Requried!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                        permissionToken.continuePermissionRequest();
                    }
                }).check();
    }

    public ArrayList<File> findPdf() {
        ContentResolver cr = getContentResolver();
        Uri uri = MediaStore.Files.getContentUri("external");

        String[] projection = {MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME};
        String selectionMimeType = MediaStore.Files.FileColumns.MIME_TYPE + "=?";
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("pdf");
        String[] selectionArgsPdf = new String[]{mimeType};

        Cursor cursor = cr.query(uri, projection, selectionMimeType, selectionArgsPdf, null);

        assert cursor != null;
        ArrayList<File> uriList = new ArrayList<>();
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            int columnIndex = cursor.getColumnIndex(projection[0]);
            long fileId = cursor.getLong(columnIndex);
            Uri fileUri = Uri.parse(uri.toString() + "/" + fileId);
            //String displayName = cursor.getString(cursor.getColumnIndex(projection[1]));
            File file = getFileFromUri(this, fileUri);
            if (file != null) uriList.add(file);
        }
        cursor.close();
        return uriList;
    }

    private void displayPdf() {
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new GridLayoutManager(MainActivity.this
                , 3));
//        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        pdfList = new ArrayList<>();
        pdfList.addAll(findPdf());
        pdfAdapter = new PdfAdapter(this, pdfList);
        recyclerView.setAdapter(pdfAdapter);
    }

    /**
     * Get Actual path of file from content Uri
     *
     * @param context Activity context
     * @param uri     Content Uri of file
     * @return Actual file
     */
    private File getFileFromUri(Context context, Uri uri) {
        if (uri == null) return null;
        if (uri.getPath() == null) return null;

        String newUriString = uri.toString();
        newUriString = newUriString.replace(
                "content://com.android.providers.downloads.documents/",
                "content://com.android.providers.media.documents/"
        );
        newUriString = newUriString.replace(
                "/msf%3A", "/image%3A"
        );
        Uri newUri = Uri.parse(newUriString);

        String realPath = "";
        Uri databaseUri;
        String selection;
        String[] selectionArgs;
        if (newUri.getPath().contains("/document/image:")) {
            databaseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            selection = "_id=?";
            selectionArgs = new String[]{DocumentsContract.getDocumentId(newUri).split(":")[1]};
        } else {
            databaseUri = newUri;
            selection = null;
            selectionArgs = null;
        }
        try {
            String column = "_data";
            String[] projection = {column};
            Cursor cursor = context.getContentResolver().query(
                    databaseUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
            );
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(column);
                    realPath = cursor.getString(columnIndex);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.i("GetFileUri Exception:", e.getMessage() != null ? e.getMessage() : "");
        }
        String path = realPath.isEmpty() ? (
                newUri.getPath().contains("/document/raw:") ? newUri.getPath().replace(
                        "/document/raw:",
                        ""
                ) : (newUri.getPath().contains("/document/primary:") ? newUri.getPath().replace(
                        "/document/primary:",
                        "/storage/emulated/0/"
                ) : null)
        ) : realPath;

        return path == null || path.isEmpty() ? null : new File(path);
    }
}