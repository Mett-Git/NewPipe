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

    /** Le pillole in cima, come su YouTube Music: -1 = tutto. */
    private static final int CHIP_PLAYLISTS = 0;
    private static final int CHIP_SONGS = 1;
    private static final int CHIP_ALBUMS = 2;
    private static final int CHIP_ARTISTS = 3;
    private int chip = -1;

    @Override
    protected void build() {
        content.addView(pageTitle(getString(R.string.vm_library)));
        // pillole: Playlist, Brani, Album, Artisti, Download (toccandone una si filtra,
        // toccandola di nuovo si torna a tutto)
        final HorizontalScrollView chips = new HorizontalScrollView(requireContext());
        chips.setHorizontalScrollBarEnabled(false);
        chips.setClipToPadding(false);
        chips.setPadding(dp(12), 0, dp(12), dp(4));
        final LinearLayout strip = new LinearLayout(requireContext());
        final int[] names = {R.string.vm_chip_playlists, R.string.vm_chip_songs,
                R.string.vm_chip_albums, R.string.vm_chip_artists};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            final TextView pill = (TextView) inflater().inflate(R.layout.music_chip, strip, false);
            pill.setText(names[i]);
            pill.setSelected(chip == i);
            pill.setOnClickListener(v -> {
                chip = chip == index ? -1 : index;
                disposables.clear();
                content.removeAllViews();
                build();
            });
            strip.addView(pill);
        }
        final TextView downloads = (TextView) inflater().inflate(R.layout.music_chip, strip,
                false);
        downloads.setText(R.string.downloads);
        downloads.setOnClickListener(v ->
                org.schabi.newpipe.util.NavigationHelper.openDownloads(requireActivity()));
        strip.addView(downloads);
        chips.addView(strip);
        content.addView(chips);

        addSection(content, getString(R.string.vm_recent_activity));
        final LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        content.addView(box);

        if (chip == -1 || chip == CHIP_SONGS) {
            final String likedTitle = getString(R.string.vm_liked);
            final String recentTitle = getString(R.string.vm_recent);
            final List<StreamInfoItem> liked = MusicStore.liked(requireContext());
            final List<StreamInfoItem> recent = MusicStore.recent(requireContext());
            box.addView(linkRow(box, likedTitle, songsCount(liked.size()),
                    liked.isEmpty() ? new java.util.ArrayList<>() : liked.get(0).getThumbnails(),
                    v -> host().openPage(MusicListPage.liked(likedTitle))));
            box.addView(linkRow(box, recentTitle, songsCount(recent.size()),
                    recent.isEmpty() ? new java.util.ArrayList<>()
                            : recent.get(0).getThumbnails(),
                    v -> host().openPage(MusicListPage.recent(recentTitle))));
        }
        if (chip == -1 || chip == CHIP_ARTISTS) {
            for (final MusicStore.FollowedArtist artist : MusicStore.followed(requireContext())) {
                final View row = linkRow(box, artist.getName(), getString(R.string.vm_artist),
                        ImageStrategy.INSTANCE.dbUrlToImageList(artist.getCover()),
                        v -> host().openPage(
                                MusicArtistPage.create(artist.getName(), artist.getCover())));
                makeRound(row);
                box.addView(row);
            }
        }
        final AppDatabase database = NewPipeDatabase.getInstance(requireContext());
        if (chip == -1 || chip == CHIP_PLAYLISTS) {
            disposables.add(new LocalPlaylistManager(database).getPlaylists().firstOrError()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(playlists -> {
                        for (final PlaylistMetadataEntry playlist : playlists) {
                            box.addView(linkRow(box, playlist.getOrderingName(),
                                    getString(R.string.vm_playlist_count,
                                            songsCount((int) playlist.getStreamCount())),
                                    ImageStrategy.INSTANCE.dbUrlToImageList(
                                            playlist.getThumbnailUrl()),
                                    v -> host().openPage(MusicListPage.localPlaylist(
                                            playlist.getOrderingName(), playlist.getUid()))));
                        }
                    }, error -> addMessage(box, R.string.vm_load_error, null)));
        }
        if (chip == -1 || chip == CHIP_ALBUMS || chip == CHIP_PLAYLISTS) {
            // album e playlist salvati da YouTube
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

        // in fondo: il riepilogo e la sessione privata
        if (chip == -1) {
            final LinearLayout first = gridRow();
            final int minutes = MusicStore.minutesThisMonth(requireContext());
            first.addView(shortcut(first, getString(R.string.vm_stats),
                    getResources().getQuantityString(R.plurals.vm_minutes_count, minutes,
                            minutes),
                    R.drawable.ic_vm_equalizer, R.color.vm_text,
                    v -> host().openPage(new MusicStatsPage())));
            first.addView(privateTile(first));
        }
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

    private String songsCount(final int count) {
        return getResources().getQuantityString(R.plurals.vm_songs_count, count, count);
    }
}
