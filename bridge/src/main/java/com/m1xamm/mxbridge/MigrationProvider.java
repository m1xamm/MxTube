package com.m1xamm.mxbridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class MigrationProvider extends ContentProvider {
    private static final String TAG = "MxMigration";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) {
        if (!"/data".equals(uri.getPath())) {
            return null;
        }
        Context context = getContext();
        if (context == null) {
            return null;
        }
        File archive = new File(context.getCacheDir(), "mxtube-data.zip");
        try {
            writeArchive(context.getDataDir(), archive);
            return ParcelFileDescriptor.open(archive, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException error) {
            Log.e(TAG, "cannot export data", error);
            return null;
        }
    }

    private static void writeArchive(File dataDir, File archive) throws IOException {
        Path root = dataDir.toPath();
        Path output = archive.toPath();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive));
                Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> !path.equals(root))
                    .filter(path -> !path.startsWith(root.resolve("cache")))
                    .filter(path -> !path.startsWith(root.resolve("code_cache")))
                    .filter(path -> !path.equals(output))
                    .filter(path -> path.getFileName().toString().equals("mxtube-data.zip") == false)
                    .forEach(path -> {
                        String name = root.relativize(path).toString().replace(File.separatorChar, '/');
                        try {
                            ZipEntry entry = new ZipEntry(name + (Files.isDirectory(path) ? "/" : ""));
                            zip.putNextEntry(entry);
                            if (Files.isRegularFile(path)) {
                                Files.copy(path, zip);
                            }
                            zip.closeEntry();
                        } catch (IOException error) {
                            Log.w(TAG, "cannot export " + name, error);
                        }
                    });
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "application/zip";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
