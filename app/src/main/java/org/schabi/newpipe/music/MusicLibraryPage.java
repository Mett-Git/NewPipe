package org.schabi.newpipe.music;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

/** La raccolta: i brani che piacciono, gli ascoltati e le playlist già salvate in NewPipe. */
public class MusicLibraryPage extends MusicPage {
    // tornando qui da un album o da una playlist, quello che è cambiato deve vedersi subito
    @Override
    public void onHiddenChanged(final boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) {
            disposables.clear();
            content.removeAllViews();
            build();
        }
    }

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
        content.addView(iconRow(getString(R.string.vm_stats), getResources().getQuantityString(
                R.plurals.vm_minutes_count, MusicStore.minutesThisMonth(requireContext()),
                MusicStore.minutesThisMonth(requireContext())), R.drawable.ic_vm_equalizer,
                v -> host().openPage(new MusicStatsPage())));
        addPrivateSwitch();
        addFollowedArtists();
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

    private void addPrivateSwitch() {
        final SwitchCompat toggle = (SwitchCompat) inflater()
                .inflate(R.layout.music_switch_row, content, false);
        toggle.setChecked(MusicStore.isPrivate(requireContext()));
        toggle.setOnCheckedChangeListener((button, checked) ->
                MusicStore.setPrivate(requireContext(), checked));
        content.addView(toggle);
    }

    private void addFollowedArtists() {
        final List<MusicStore.FollowedArtist> artists = MusicStore.followed(requireContext());
        if (artists.isEmpty()) {
            return;
        }
        addSection(content, getString(R.string.vm_chip_artists));
        for (final MusicStore.FollowedArtist artist : artists) {
            content.addView(linkRow(content, artist.getName(), getString(R.string.vm_artist),
                    ImageStrategy.INSTANCE.dbUrlToImageList(artist.getCover()),
                    v -> host().openPage(
                            MusicArtistPage.create(artist.getName(), artist.getCover()))));
        }
    }

    // al posto della copertina, un'icona
    private View iconRow(final String title, final String subtitle, @DrawableRes final int icon,
                         final View.OnClickListener click) {
        // niente caricamento di copertina: l'immagine segnaposto arriverebbe dopo e coprirebbe
        // l'icona
        final View row = inflater().inflate(R.layout.music_row, content, false);
        ((TextView) row.findViewById(R.id.musicRowTitle)).setText(title);
        ((TextView) row.findViewById(R.id.musicRowSubtitle)).setText(subtitle);
        row.findViewById(R.id.musicRowMore).setVisibility(View.GONE);
        row.setOnClickListener(click);
        final ImageView cover = row.findViewById(R.id.musicRowCover);
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
