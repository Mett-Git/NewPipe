package org.schabi.newpipe.music;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

/** La raccolta: i brani che piacciono, gli ascoltati e le playlist già salvate in NewPipe. */
public class MusicLibraryPage extends MusicPage {
    @Override
    protected void build() {
        content.addView(pageTitle(getString(R.string.vm_library)));
        final String likedTitle = getString(R.string.vm_liked);
        final String recentTitle = getString(R.string.vm_recent);
        final List<StreamInfoItem> liked = MusicStore.liked(requireContext());
        final List<StreamInfoItem> recent = MusicStore.recent(requireContext());
        content.addView(iconRow(likedTitle, songsCount(liked.size()), R.drawable.ic_favorite,
                v -> host().openPage(MusicListPage.liked(likedTitle))));
        content.addView(iconRow(recentTitle, songsCount(recent.size()), R.drawable.ic_history,
                v -> host().openPage(MusicListPage.recent(recentTitle))));
        addSection(content, getString(R.string.vm_your_playlists));
        final LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        content.addView(box);
        final AppDatabase database = NewPipeDatabase.getInstance(requireContext());
        disposables.add(new LocalPlaylistManager(database).getPlaylists().firstOrError()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlists -> {
                    for (final PlaylistMetadataEntry playlist : playlists) {
                        box.addView(linkRow(box, playlist.getOrderingName(),
                                songsCount((int) playlist.getStreamCount()),
                                ImageStrategy.INSTANCE.dbUrlToImageList(
                                        playlist.getThumbnailUrl()),
                                v -> host().openPage(MusicListPage.localPlaylist(
                                        playlist.getOrderingName(), playlist.getUid()))));
                    }
                }, error -> addMessage(box, R.string.vm_load_error, null)));
        disposables.add(new RemotePlaylistManager(database).getPlaylists().firstOrError()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlists -> {
                    for (final PlaylistRemoteEntity playlist : playlists) {
                        box.addView(linkRow(box, playlist.getOrderingName(),
                                playlist.getUploader(),
                                ImageStrategy.INSTANCE.dbUrlToImageList(
                                        playlist.getThumbnailUrl()),
                                v -> host().openPage(MusicListPage.playlist(
                                        playlist.getOrderingName(), playlist.getUrl()))));
                    }
                }, error -> addMessage(box, R.string.vm_load_error, null)));
    }

    // al posto della copertina, un'icona
    private View iconRow(final String title, final String subtitle, @DrawableRes final int icon,
                         final View.OnClickListener click) {
        final View row = linkRow(content, title, subtitle, List.of(), click);
        final ImageView cover = row.findViewById(R.id.musicRowCover);
        CoilHelper.INSTANCE.loadThumbnail(cover, (String) null);
        cover.setScaleType(ImageView.ScaleType.CENTER);
        cover.setImageResource(icon);
        cover.setImageTintList(ContextCompat.getColorStateList(
                requireContext(), R.color.vm_accent));
        return row;
    }

    private String songsCount(final int count) {
        return getResources().getQuantityString(R.plurals.vm_songs_count, count, count);
    }
}
