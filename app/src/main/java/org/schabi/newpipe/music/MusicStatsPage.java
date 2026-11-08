package org.schabi.newpipe.music;

import android.view.View;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.List;

/** Il riepilogo: minuti di questo mese, brani e artisti più ascoltati. */
public class MusicStatsPage extends MusicPage {
    private static final int TOP_SONGS = 10;
    private static final int TOP_ARTISTS = 5;

    @Override
    protected void build() {
        final View header = inflater().inflate(R.layout.music_list_header, content, false);
        header.findViewById(R.id.musicListBack).setOnClickListener(v -> host().goBack());
        header.findViewById(R.id.musicListCover).setVisibility(View.GONE);
        header.findViewById(R.id.musicListButtons).setVisibility(View.GONE);
        ((TextView) header.findViewById(R.id.musicListTitle)).setText(R.string.vm_stats);
        content.addView(header);

        final View minutes = inflater().inflate(R.layout.music_stats_minutes, content, false);
        ((TextView) minutes.findViewById(R.id.musicStatsMinutes))
                .setText(String.valueOf(MusicStore.minutesThisMonth(requireContext())));
        content.addView(minutes);

        final List<StreamInfoItem> songs = MusicStore.topSongs(requireContext(), TOP_SONGS);
        if (songs.isEmpty()) {
            addMessage(content, R.string.vm_stats_empty, null);
            return;
        }
        addSection(content, getString(R.string.vm_top_songs));
        addSongRows(content, songs);
        addSection(content, getString(R.string.vm_top_artists));
        for (final MusicStore.TopArtist artist
                : MusicStore.topArtists(requireContext(), TOP_ARTISTS)) {
            content.addView(linkRow(content, artist.getName(),
                    getResources().getQuantityString(R.plurals.vm_plays_count,
                            artist.getPlays(), artist.getPlays()),
                    artist.getSong().getThumbnails(),
                    v -> host().openPage(MusicArtistPage.create(artist.getName(),
                            ImageStrategy.INSTANCE.imageListToDbUrl(
                                    artist.getSong().getThumbnails())))));
        }
    }
}
