package org.schabi.newpipe.music;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Le azioni su un brano, uguali nel menu delle righe e in quello del lettore. */
public final class MusicActions {
    private MusicActions() {
    }

    private static void showError(final AppCompatActivity activity) {
        Toast.makeText(activity, R.string.vm_load_error, Toast.LENGTH_SHORT).show();
    }

    /**
     * @param activity    la schermata
     * @param song        il brano
     * @param disposables dove tenere il lavoro in corso
     */
    public static void addToPlaylist(@NonNull final AppCompatActivity activity,
                                     @NonNull final StreamInfoItem song,
                                     @NonNull final CompositeDisposable disposables) {
        disposables.add(PlaylistDialog.createCorrespondingDialog(activity,
                Collections.singletonList(new StreamEntity(song)),
                dialog -> dialog.show(activity.getSupportFragmentManager(), "musicPlaylist")));
    }

    /**
     * @param activity    la schermata
     * @param song        il brano
     * @param disposables dove tenere il lavoro in corso
     */
    public static void download(@NonNull final AppCompatActivity activity,
                                @NonNull final StreamInfoItem song,
                                @NonNull final CompositeDisposable disposables) {
        disposables.add(ExtractorHelper.getStreamInfo(song.getServiceId(), song.getUrl(), false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    final DownloadDialog dialog = new DownloadDialog(activity, info);
                    dialog.preferAudio();
                    dialog.show(activity.getSupportFragmentManager(), "musicDownload");
                }, error -> showError(activity)));
    }

    /**
     * @param activity la schermata
     * @param song     il brano
     */
    public static void share(@NonNull final AppCompatActivity activity,
                             @NonNull final StreamInfoItem song) {
        ShareUtils.shareText(activity, song.getName(), song.getUrl(), song.getThumbnails());
    }

    /**
     * Fa partire il brano seguito da quelli che YouTube gli mette accanto.
     *
     * @param activity    la schermata
     * @param host        chi fa partire la coda
     * @param song        il brano
     * @param disposables dove tenere il lavoro in corso
     */
    public static void radio(@NonNull final AppCompatActivity activity,
                             @NonNull final MusicHost host,
                             @NonNull final StreamInfoItem song,
                             @NonNull final CompositeDisposable disposables) {
        Toast.makeText(activity, R.string.vm_radio_starting, Toast.LENGTH_SHORT).show();
        disposables.add(ExtractorHelper.getStreamInfo(song.getServiceId(), song.getUrl(), false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    final List<StreamInfoItem> songs = new ArrayList<>();
                    songs.add(song);
                    for (final InfoItem item : info.getRelatedItems()) {
                        if (item instanceof StreamInfoItem
                                && !song.getUrl().equals(item.getUrl())) {
                            songs.add((StreamInfoItem) item);
                        }
                    }
                    host.playSongs(songs, 0, false);
                }, error -> showError(activity)));
    }
}
