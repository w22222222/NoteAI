package com.noteai.noteai.image;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class LocalImageInsertManager implements ImageInsertManager {
    private final Context context;

    public LocalImageInsertManager(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void importImage(Uri sourceUri, Callback callback) {
        if (sourceUri == null) {
            callback.onError("未选择图片");
            return;
        }

        try {
            File imageDir = new File(context.getFilesDir(), "images");
            if (!imageDir.exists() && !imageDir.mkdirs()) {
                callback.onError("创建图片目录失败");
                return;
            }

            String ext = guessExtension(sourceUri);
            String fileName = "img_" + System.currentTimeMillis() + "." + ext;
            File targetFile = new File(imageDir, fileName);

            ContentResolver resolver = context.getContentResolver();
            try (InputStream in = resolver.openInputStream(sourceUri);
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                if (in == null) {
                    callback.onError("无法读取图片");
                    return;
                }
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (FileInputStream boundsInput = new FileInputStream(targetFile)) {
                BitmapFactory.decodeStream(boundsInput, null, options);
            }

            int width = Math.max(options.outWidth, 0);
            int height = Math.max(options.outHeight, 0);
            String localPath = "images/" + fileName;
            String markdownText = "![图片](" + localPath + "){width=" + width + " height=" + height + "}";

            callback.onSuccess(new InsertedImage(localPath, markdownText, "图片", width, height));
        } catch (Exception e) {
            callback.onError("插入图片失败: " + e.getMessage());
        }
    }

    private String guessExtension(Uri uri) {
        String ext = null;
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType != null) {
            ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        }
        if (ext == null || ext.trim().isEmpty()) {
            ext = "jpg";
        }
        return ext;
    }
}
