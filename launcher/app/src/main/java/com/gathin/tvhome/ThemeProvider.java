package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Lets tools/theme.sh write the theme files into {@link Config#dir} on firmware where the adb shell is
 * locked out of Android/data (TCL G07, Android 11). Only the files {@link ThemeFiles} allows, and only for
 * callers holding WRITE_SECURE_SETTINGS, which the adb shell has and ordinary apps cannot get.
 *
 *   content write --uri content://com.gathin.tvhome.theme/config.json < /data/local/tmp/config.json
 *   content write --uri content://com.gathin.tvhome.theme/wallpapers/dusk.jpg < /data/local/tmp/dusk.jpg
 *   content delete --uri content://com.gathin.tvhome.theme/config.json
 *   content delete --uri content://com.gathin.tvhome.theme/wallpapers      (every image in the collection)
 */
public class ThemeProvider extends ContentProvider {
    private static final String TAG = "TvHome.Theme";
    /** config.json is capped by Config's reader; theme_revision is a short token. */
    private static final long MAX_CONFIG_BYTES = 64 * 1024;
    private static final long MAX_IMAGE_BYTES = 16L * 1024 * 1024;

    private File themeDir() throws FileNotFoundException {
        File dir = Config.dir(getContext());
        if (dir == null) throw new FileNotFoundException("theme storage unavailable");
        try {
            return dir.getCanonicalFile();
        } catch (IOException e) {
            throw new FileNotFoundException("theme storage: " + e.getMessage());
        }
    }

    private File file(Uri uri, boolean createParent) throws FileNotFoundException {
        String path = ThemeFiles.path(uri.getPathSegments());
        if (path == null) throw new FileNotFoundException(String.valueOf(uri));
        File dir = themeDir();
        File target = new File(dir, path);
        File parent = target.getParentFile();
        if (parent == null) throw new FileNotFoundException(String.valueOf(uri));
        if (!parent.isDirectory() && (!createParent || !parent.mkdirs())) {
            throw new FileNotFoundException(String.valueOf(uri));
        }
        if (hasSymlink(dir, path)) throw new FileNotFoundException(String.valueOf(uri));
        File canonicalParent;
        try {
            canonicalParent = parent.getCanonicalFile();
        } catch (IOException e) {
            throw new FileNotFoundException(String.valueOf(uri));
        }
        if (!under(dir, canonicalParent)) throw new FileNotFoundException(String.valueOf(uri));
        return new File(canonicalParent, target.getName());
    }

    /** True if any existing component of the path below dir is a symlink, dangling ones included. */
    private static boolean hasSymlink(File dir, String path) {
        File current = dir;
        for (String part : path.split("/")) {
            current = new File(current, part);
            try {
                if (Files.isSymbolicLink(current.toPath())) return true;
            } catch (Exception e) {
                return true;
            }
            if (!current.exists()) return false;
        }
        return false;
    }

    private static boolean under(File dir, File candidate) {
        String base = dir.getPath();
        String path = candidate.getPath();
        return path.equals(base) || path.startsWith(base + File.separator);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        boolean write = mode.contains("w");
        File target = file(uri, write);
        if (!write) {
            return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        return openAtomic(target);
    }

    /**
     * The caller writes into a pipe; a worker writes a sibling temp file, checks it and renames it over the
     * destination. A truncated push therefore leaves the old theme in place, and the ContentProvider contract
     * stays the same for tools/theme.sh.
     */
    private ParcelFileDescriptor openAtomic(File target) throws FileNotFoundException {
        File parent = target.getParentFile();
        File temp = new File(parent, "." + target.getName() + ".tmp");
        if ((temp.exists() || Files.isSymbolicLink(temp.toPath())) && !temp.delete()) {
            throw new FileNotFoundException("cannot replace " + temp);
        }
        ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createReliablePipe();
        } catch (IOException e) {
            throw new FileNotFoundException("pipe: " + e.getMessage());
        }
        Thread writer = new Thread(() -> writeAtomically(pipe[0], temp, target), "theme-write");
        writer.setDaemon(true);
        writer.start();
        return pipe[1];
    }

    private void writeAtomically(ParcelFileDescriptor source, File temp, File target) {
        boolean done = false;
        try (ParcelFileDescriptor.AutoCloseInputStream in =
                     new ParcelFileDescriptor.AutoCloseInputStream(source)) {
            boolean config = Config.FILE_NAME.equals(target.getName())
                    || "theme_revision".equals(target.getName());
            long max = config ? MAX_CONFIG_BYTES : MAX_IMAGE_BYTES;
            long total = 0;
            boolean tooLarge = false;
            byte[] buf = new byte[16 * 1024];
            try (FileOutputStream out = new FileOutputStream(temp)) {
                int r;
                // Drain past the cap so the caller does not see a broken pipe and fall back to a plain cp.
                while ((r = in.read(buf)) > 0) {
                    total += r;
                    if (total > max) {
                        tooLarge = true;
                        continue;
                    }
                    out.write(buf, 0, r);
                }
                if (total == 0) throw new IOException("empty file");
                if (tooLarge) throw new IOException("larger than " + max + " bytes");
                out.flush();
                out.getFD().sync();
            }
            if (Config.FILE_NAME.equals(target.getName()) && !validJson(temp)) {
                throw new IOException("not a JSON object");
            }
            if (ThemeFiles.collectionImage(target.getName()) && !validImage(temp)) {
                throw new IOException("not an image");
            }
            if (Files.isSymbolicLink(target.toPath())) {
                throw new IOException("symlink destination");
            }
            if (!temp.renameTo(target)) throw new IOException("rename failed");
            done = true;
        } catch (Exception e) {
            Log.w(TAG, "could not write " + target + ": " + e.getMessage());
        } finally {
            if (!done) temp.delete();
        }
    }

    private static boolean validJson(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            new JSONObject(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validImage(File file) {
        byte[] head = new byte[12];
        try (InputStream in = new FileInputStream(file)) {
            int n = 0, r;
            while (n < head.length && (r = in.read(head, n, head.length - n)) > 0) n += r;
            if (n < head.length) return false;
        } catch (IOException e) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".png")) {
            return (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
        }
        if (name.endsWith(".webp")) {
            return head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
        }
        return (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        try {
            String collection = ThemeFiles.collection(uri.getPathSegments());
            if (collection == null) return file(uri, false).delete() ? 1 : 0;
            File dir = themeDir();
            if (hasSymlink(dir, collection)) return 0;
            File folder = new File(dir, collection);
            File canonical;
            try {
                canonical = folder.getCanonicalFile();
            } catch (IOException e) {
                return 0;
            }
            if (!under(dir, canonical)) return 0;
            File[] images = canonical.listFiles((d, name) -> ThemeFiles.collectionImage(name));
            int n = 0;
            if (images != null) for (File f : images) if (f.delete()) n++;
            return n;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
