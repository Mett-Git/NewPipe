package org.schabi.newpipe.music;

import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
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
        // quattro riquadri di vetro, due per riga
        final LinearLayout first = gridRow();
        first.addView(shortcut(first, likedTitle, songsCount(liked.size()),
                R.drawable.ic_favorite, R.color.vm_accent,
                v -> host().openPage(MusicListPage.liked(likedTitle))));
        first.addView(shortcut(first, recentTitle, songsCount(recent.size()),
                R.drawable.ic_history, R.color.vm_text,
                v -> host().openPage(MusicListPage.recent(recentTitle))));
        final LinearLayout second = gridRow();
        final int minutes = MusicStore.minutesThisMonth(requireContext());
        second.addView(shortcut(second, getString(R.string.vm_stats),
                getResources().getQuantityString(R.plurals.vm_minutes_count, minutes, minutes),
                R.drawable.ic_vm_equalizer, R.color.vm_text,
                v -> host().openPage(new MusicStatsPage())));
        second.addView(privateTile(second));
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

    private LinearLayout gridRow() {
        final LinearLayout row = new LinearLayout(requireContext());
        row.setPadding(dp(8), 0, dp(8), 0);
        content.addView(row);
        return row;
    }

    private View shortcut(final LinearLayout parent, final String title, final String subtitle,
                          @DrawableRes final int icon, @ColorRes final int tint,
                          final View.OnClickListener click) {
        final View tile = inflater().inflate(R.layout.music_shortcut, parent, false);
        ((TextView) tile.findViewById(R.id.musicShortcutTitle)).setText(title);
        ((TextView) tile.findViewById(R.id.musicShortcutSubtitle)).setText(subtitle);
        final ImageView image = tile.findViewById(R.id.musicShortcutIcon);
        image.setImageResource(icon);
        image.setImageTintList(ContextCompat.getColorStateList(requireContext(), tint));
        tile.setOnClickListener(click);
        return tile;
    }

    // la sessione privata si accende e si spegne toccando il riquadro
    private View privateTile(final LinearLayout parent) {
        final View tile = shortcut(parent, getString(R.string.vm_private_short), "",
                R.drawable.ic_vm_lock, R.color.vm_text, null);
        final SwitchCompat toggle = tile.findViewById(R.id.musicShortcutSwitch);
        toggle.setVisibility(View.VISIBLE);
        tile.setContentDescription(getString(R.string.vm_private));
        showPrivate(tile, MusicStore.isPrivate(requireContext()));
        tile.setOnClickListener(v -> {
            final boolean on = !MusicStore.isPrivate(requireContext());
            MusicStore.setPrivate(requireContext(), on);
            showPrivate(tile, on);
        });
        return tile;
    }

    private void showPrivate(final View tile, final boolean on) {
        ((SwitchCompat) tile.findViewById(R.id.musicShortcutSwitch)).setChecked(on);
        ((TextView) tile.findViewById(R.id.musicShortcutSubtitle))
                .setText(on ? R.string.vm_private_on : R.string.vm_private_off);
        ((ImageView) tile.findViewById(R.id.musicShortcutIcon))
                .setImageTintList(ContextCompat.getColorStateList(requireContext(),
                        on ? R.color.vm_accent : R.color.vm_text));
    }

    // gli artisti seguiti in fila, con la foto tonda
    private void addFollowedArtists() {
        final List<MusicStore.FollowedArtist> artists = MusicStore.followed(requireContext());
        if (artists.isEmpty()) {
            return;
        }
        addSection(content, getString(R.string.vm_chip_artists));
        final LinearLayout strip = new LinearLayout(requireContext());
        for (final MusicStore.FollowedArtist artist : artists) {
            final View card = inflater().inflate(R.layout.music_artist_card, strip, false);
            ((TextView) card.findViewById(R.id.musicCardTitle)).setText(artist.getName());
            loadCover(card.findViewById(R.id.musicCardCover),
                    ImageStrategy.INSTANCE.dbUrlToImageList(artist.getCover()));
            card.setOnClickListener(v -> host().openPage(
                    MusicArtistPage.create(artist.getName(), artist.getCover())));
            strip.addView(card);
        }
        final HorizontalScrollView scroll = new HorizontalScrollView(requireContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(12), 0, dp(12), 0);
        scroll.addView(strip);
        content.addView(scroll);
    }

    private String songsCount(final int count) {
        return getResources().getQuantityString(R.plurals.vm_songs_count, count, count);
    }
}
