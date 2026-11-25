package org.schabi.newpipe.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Il profilo personalizzato: nome, colore, foto e copertina scelti da te. Resta tutto sul
 * telefono (le due immagini sono copiate, rimpicciolite, nella cartella dell'app).
 */
final class VetroProfile {
    /** I colori fra cui scegliere. */
    static final int[] COLORS = {
            0xFFFF8A73, 0xFF5A8CFF, 0xFFAA5AFF, 0xFF4CC38A,
            0xFFF5C451, 0xFFFF6FAE, 0xFF3CC6D0, 0xFF8A8F98,
    };
    private static final String PREFS = "vetro_profile";
    private static final String KEY_NAME = "name";
    private static final String KEY_COLOR = "color";
    private static final String PHOTO_FILE = "vetro_profile_photo.jpg";
    private static final String COVER_FILE = "vetro_profile_cover.jpg";
    private static final int PHOTO_SIZE = 512;
    private static final int COVER_SIZE = 1440;

    private final Context context;

    VetroProfile(@NonNull final Context context) {
        this.context = context.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** @return il nome scelto, o "" se non l'hai mai cambiato */
    @NonNull
    String name() {
        return prefs().getString(KEY_NAME, "");
    }

    @ColorInt
    int color() {
        return prefs().getInt(KEY_COLOR, COLORS[0]);
    }

    void save(@NonNull final String name, @ColorInt final int color) {
        prefs().edit().putString(KEY_NAME, name.trim()).putInt(KEY_COLOR, color).apply();
    }

    File photo() {
        return new File(context.getFilesDir(), PHOTO_FILE);
    }

    File cover() {
        return new File(context.getFilesDir(), COVER_FILE);
    }

    /**
     * Copia un'immagine scelta dalla galleria, rimpicciolita, come foto o copertina.
     *
     * @param uri   l'immagine scelta
     * @param cover true = copertina, false = foto del profilo
     * @throws IOException se l'immagine non si legge
     */
    void saveImage(@NonNull final Uri uri, final boolean cover) throws IOException {
        final int max = cover ? COVER_SIZE : PHOTO_SIZE;
        final BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= max) {
            options.inSampleSize *= 2;
        }
        final Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Immagine non leggibile");
        }
        try (OutputStream out = new FileOutputStream(cover ? cover() : photo())) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        }
        bitmap.recycle();
    }

    void removeImages() {
        //noinspection ResultOfMethodCallIgnored
        photo().delete();
        //noinspection ResultOfMethodCallIgnored
        cover().delete();
    }

    /**
     * Mostra il profilo nella testata della scheda Profilo.
     *
     * @param root la pagina del profilo
     */
    void show(@NonNull final View root) {
        final int color = color();
        final String name = name();
        // le scritte nella lingua scelta nell'app: quella della pagina, non dell'app intera
        final Context ui = root.getContext();
        final String you = ui.getString(R.string.profile_name);

        final TextView nameView = root.findViewById(R.id.profileName);
        nameView.setText(name.isEmpty() ? you : name);

        // foto: la tua, oppure l'iniziale sul tuo colore
        final ImageView avatar = root.findViewById(R.id.profileAvatar);
        final TextView initial = root.findViewById(R.id.profileInitial);
        final GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        avatar.setBackground(circle);
        avatar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        avatar.setClipToOutline(true);
        final Bitmap photo = load(photo());
        avatar.setImageBitmap(photo);
        if (photo == null) {
            final String shown = name.isEmpty() ? you : name;
            initial.setText(shown.substring(0, 1).toUpperCase());
            initial.setVisibility(View.VISIBLE);
        } else {
            initial.setVisibility(View.GONE);
        }

        // copertina: la tua, oppure una sfumatura del tuo colore
        // l'anello attorno alla foto è dello stesso colore dello sfondo della pagina
        final GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(ThemeHelper.resolveColorFromAttr(ui, R.attr.windowBackground));
        root.findViewById(R.id.profileAvatarFrame).setBackground(ring);

        final ImageView coverView = root.findViewById(R.id.profileCover);
        final float radius = DeviceUtils.dpToPx(20, context);
        final GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{color, darker(color)});
        shape.setCornerRadius(radius);
        coverView.setBackground(shape);
        coverView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        coverView.setClipToOutline(true);
        final Bitmap cover = load(cover());
        if (cover == null) {
            coverView.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
        } else {
            coverView.setImageBitmap(cover);
        }
    }

    @Nullable
    private static Bitmap load(@NonNull final File file) {
        return file.exists() ? BitmapFactory.decodeFile(file.getPath()) : null;
    }

    @ColorInt
    private static int darker(@ColorInt final int color) {
        final float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.35f;
        return Color.HSVToColor(hsv);
    }
}
