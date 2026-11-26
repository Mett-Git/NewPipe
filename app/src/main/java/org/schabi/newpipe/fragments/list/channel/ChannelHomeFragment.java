package org.schabi.newpipe.fragments.list.channel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.ContentAvailability;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.VetroRound;
import org.schabi.newpipe.util.image.CoilHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * La scheda "Home" del canale, ordinata come su YouTube: in cima il video in evidenza, sotto
 * le sezioni che scorrono di lato (Nuove uscite, I più visti, Shorts, Live, Solo abbonati,
 * Playlist, Podcast). Le sezioni vuote non compaiono; "Vedi tutto" apre la scheda giusta.
 */
public class ChannelHomeFragment extends BaseFragment {
    private static final int SHELF_SIZE = 12;
    /** Quanti video di "Nuove uscite" si vedono prima della freccia giù. */
    private static final int ROWS_SHOWN = 4;
    /** Quanti video grandi al massimo fra una sezione e l'altra. */
    private static final int BETWEEN = 5;

    @Nullable
    private ChannelInfo channel;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public ChannelHomeFragment() {
    }

    /**
     * @param channel il canale (con l'elenco delle sue schede)
     */
    public ChannelHomeFragment(@NonNull final ChannelInfo channel) {
        this.channel = channel;
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel_home, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        if (channel == null) {
            rootView.findViewById(R.id.channelHomeLoading).setVisibility(View.GONE);
            return;
        }
        load(rootView, channel);
    }

    /** Quello che serve per disegnare la Home del canale. */
    private static final class Sections {
        @Nullable
        StreamInfoItem featured;
        final Map<String, List<InfoItem>> tabs = new HashMap<>();
    }

    private void load(@NonNull final View root, @NonNull final ChannelInfo info) {
        final int serviceId = info.getServiceId();
        final List<Single<Map.Entry<String, List<InfoItem>>>> loads = new ArrayList<>();
        for (final ListLinkHandler handler : info.getTabs()) {
            final String tab = handler.getContentFilters().isEmpty()
                    ? "" : handler.getContentFilters().get(0);
            if (!tab.equals(ChannelTabs.VIDEOS) && !tab.equals(ChannelTabs.SHORTS)
                    && !tab.equals(ChannelTabs.LIVESTREAMS) && !tab.equals(ChannelTabs.PLAYLISTS)
                    && !tab.equals(ChannelTabs.PODCASTS)) {
                continue;
            }
            loads.add(ExtractorHelper.getChannelTab(serviceId, handler, false)
                    .map(tabInfo -> Map.entry(tab, (List<InfoItem>) itemsOf(tabInfo)))
                    .onErrorReturnItem(Map.entry(tab, Collections.emptyList()))
                    .subscribeOn(Schedulers.io()));
        }
        final Single<List<StreamInfoItem>> featured = Single
                .fromCallable(() -> featuredVideo(info))
                .onErrorReturnItem(Collections.emptyList())
                .subscribeOn(Schedulers.io());

        final Single<Sections> all = (loads.isEmpty()
                ? Single.just(new Object[0])
                : Single.zip(loads, results -> results))
                .zipWith(featured, (results, top) -> {
                    final Sections sections = new Sections();
                    for (final Object r : results) {
                        @SuppressWarnings("unchecked") final Map.Entry<String, List<InfoItem>>
                                entry = (Map.Entry<String, List<InfoItem>>) r;
                        sections.tabs.put(entry.getKey(), entry.getValue());
                    }
                    sections.featured = top.isEmpty() ? null : top.get(0);
                    return sections;
                });
        disposables.add(all.observeOn(AndroidSchedulers.mainThread())
                .subscribe(sections -> show(root, sections), e -> show(root, new Sections())));
    }

    private static List<InfoItem> itemsOf(@NonNull final ChannelTabInfo info) {
        return info.getRelatedItems() == null
                ? Collections.emptyList() : new ArrayList<>(info.getRelatedItems());
    }

    // il video "in evidenza" che il canale mette in cima alla sua pagina su YouTube
    private static List<StreamInfoItem> featuredVideo(@NonNull final ChannelInfo info)
            throws Exception {
        if (!info.getUrl().contains("youtube.com") || !info.getId().startsWith("UC")) {
            return Collections.emptyList();
        }
        final JSONObject client = new JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", YoutubeParsingHelper.getClientVersion())
                .put("hl", Locale.getDefault().getLanguage());
        final JSONObject request = new JSONObject()
                .put("context", new JSONObject().put("client", client))
                .put("browseId", info.getId());
        final Map<String, List<String>> headers =
                new HashMap<>(YoutubeParsingHelper.getYouTubeHeaders());
        headers.put("Content-Type", Collections.singletonList("application/json"));
        final String body = DownloaderImpl.getInstance().post(
                "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false", headers,
                request.toString().getBytes(StandardCharsets.UTF_8)).responseBody();
        final JSONObject player = findFirst(new JSONObject(body), "channelVideoPlayerRenderer");
        if (player == null || player.optString("videoId").isEmpty()) {
            return Collections.emptyList();
        }
        final String id = player.optString("videoId");
        final StreamInfoItem item = new StreamInfoItem(info.getServiceId(),
                "https://www.youtube.com/watch?v=" + id, text(player.optJSONObject("title")),
                StreamType.VIDEO_STREAM);
        item.setUploaderName(info.getName());
        item.setUploaderUrl(info.getUrl());
        item.setThumbnails(List.of(new org.schabi.newpipe.extractor.Image(
                "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg", 360, 480,
                org.schabi.newpipe.extractor.Image.ResolutionLevel.MEDIUM)));
        item.setTextualUploadDate(text(player.optJSONObject("publishedTimeText")));
        final String views = text(player.optJSONObject("viewCountText"));
        item.setShortDescription(views);
        return List.of(item);
    }

    // ----- disegno -----

    private void show(@NonNull final View root, @NonNull final Sections sections) {
        if (!isAdded()) {
            return;
        }
        root.findViewById(R.id.channelHomeLoading).setVisibility(View.GONE);
        final LinearLayout content = root.findViewById(R.id.channelHomeContent);
        content.removeAllViews();

        final List<StreamInfoItem> videos = streams(sections.tabs.get(ChannelTabs.VIDEOS));
        final List<StreamInfoItem> latest = new ArrayList<>();
        final List<StreamInfoItem> members = new ArrayList<>();
        for (final StreamInfoItem v : videos) {
            (v.getContentAvailability() == ContentAvailability.MEMBERSHIP ? members : latest)
                    .add(v);
        }
        final List<StreamInfoItem> pool = new ArrayList<>(latest);
        pool.sort((a, b) -> Long.compare(b.getViewCount(), a.getViewCount()));

        // in cima due video in evidenza, uno sotto l'altro: quello scelto dal canale (se c'è)
        // e i più visti. Altri video grandi vanno fra una sezione e l'altra (scelta di Matt)
        final List<StreamInfoItem> tops = new ArrayList<>();
        if (sections.featured != null) {
            tops.add(sections.featured);
        }
        while (tops.size() < 2 && !pool.isEmpty()) {
            tops.add(pool.remove(0));
        }
        final List<StreamInfoItem> between = new ArrayList<>();
        while (between.size() < BETWEEN && pool.size() > SHELF_SIZE / 2) {
            between.add(pool.remove(0));
        }
        // i video grandi non si ripetono nelle sezioni
        final java.util.Set<String> big = new java.util.HashSet<>();
        for (final StreamInfoItem v : tops) {
            big.add(v.getUrl());
        }
        for (final StreamInfoItem v : between) {
            big.add(v.getUrl());
        }
        final List<StreamInfoItem> newest = new ArrayList<>();
        for (final StreamInfoItem v : latest) {
            if (!big.contains(v.getUrl())) {
                newest.add(v);
            }
        }

        for (int i = 0; i < tops.size(); i++) {
            content.addView(featuredView(content, tops.get(i),
                    i == 0 ? R.string.vt_channel_featured : 0));
        }
        final java.util.Iterator<StreamInfoItem> next = between.iterator();
        final Runnable bigVideo = () -> {
            if (next.hasNext()) {
                content.addView(featuredView(content, next.next(), 0));
            }
        };

        if (addRows(content, getString(R.string.vt_channel_latest), ChannelTabs.VIDEOS,
                newest)) {
            bigVideo.run();
        }
        if (addShelf(content, getString(R.string.vt_channel_popular),
                getString(R.string.vt_channel_popular_note), null, pool, false)) {
            bigVideo.run();
        }
        if (addShelf(content, getString(R.string.channel_tab_shorts), null, ChannelTabs.SHORTS,
                streams(sections.tabs.get(ChannelTabs.SHORTS)), true)) {
            bigVideo.run();
        }
        if (addShelf(content, getString(R.string.channel_tab_livestreams), null,
                ChannelTabs.LIVESTREAMS, streams(sections.tabs.get(ChannelTabs.LIVESTREAMS)),
                false)) {
            bigVideo.run();
        }
        if (addShelf(content, getString(R.string.vt_channel_members), null, null, members,
                false)) {
            bigVideo.run();
        }
        if (addPlaylistShelf(content, getString(R.string.channel_tab_playlists),
                ChannelTabs.PLAYLISTS, sections.tabs.get(ChannelTabs.PLAYLISTS))) {
            bigVideo.run();
        }
        addPlaylistShelf(content, getString(R.string.channel_tab_podcasts), ChannelTabs.PODCASTS,
                sections.tabs.get(ChannelTabs.PODCASTS));

        root.findViewById(R.id.channelHomeEmpty)
                .setVisibility(content.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private static List<StreamInfoItem> streams(@Nullable final List<InfoItem> items) {
        final List<StreamInfoItem> out = new ArrayList<>();
        if (items != null) {
            for (final InfoItem item : items) {
                if (item instanceof StreamInfoItem) {
                    out.add((StreamInfoItem) item);
                }
            }
        }
        return out;
    }

    private View featuredView(@NonNull final ViewGroup parent, @NonNull final StreamInfoItem item,
                              final int title) {
        final View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.vetro_shelf_featured, parent, false);
        bindVideo(view, item);
        // come su YouTube: a tutta larghezza (angoli dritti), 16:9, foto del canale a sinistra
        final View thumb = view.findViewById(R.id.shelfThumb);
        thumb.setClipToOutline(false);
        thumb.getLayoutParams().height =
                getResources().getDisplayMetrics().widthPixels * 9 / 16;
        view.findViewById(R.id.shelfBadge).setVisibility(View.GONE);
        final ImageView avatar = view.findViewById(R.id.shelfAvatar);
        if (channel != null && !channel.getAvatars().isEmpty()) {
            avatar.setVisibility(View.VISIBLE);
            avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(final View v, final android.graphics.Outline outline) {
                    outline.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
            avatar.setClipToOutline(true);
            CoilHelper.INSTANCE.loadAvatar(avatar, channel.getAvatars());
        }
        return view;
    }

    // una sezione con titolo, "Vedi tutto" (se c'è la scheda) e la fila di video
    private boolean addShelf(@NonNull final LinearLayout content, @NonNull final String title,
                             @Nullable final String note, @Nullable final String tab,
                             @NonNull final List<StreamInfoItem> items, final boolean shorts) {
        if (items.isEmpty()) {
            return false;
        }
        final LinearLayout row = shelf(content, title, note, tab);
        for (final StreamInfoItem item : items.subList(0, Math.min(SHELF_SIZE, items.size()))) {
            final View cell = LayoutInflater.from(requireContext()).inflate(
                    shorts ? R.layout.vetro_shelf_short : R.layout.vetro_shelf_video, row, false);
            bindVideo(cell, item);
            row.addView(cell);
        }
        return true;
    }

    private boolean addRows(@NonNull final LinearLayout content, @NonNull final String title,
                            @NonNull final String tab, @NonNull final List<StreamInfoItem> items) {
        if (items.isEmpty()) {
            return false;
        }
        final LinearLayout list = shelf(content, title, null, tab);
        // la fila che scorre di lato qui non serve: i video vanno uno sotto l'altro
        final ViewGroup scroll = (ViewGroup) list.getParent();
        final ViewGroup shelfView = (ViewGroup) scroll.getParent();
        shelfView.removeView(scroll);
        final LinearLayout rows = new LinearLayout(requireContext());
        rows.setOrientation(LinearLayout.VERTICAL);
        shelfView.addView(rows);
        final int max = Math.min(SHELF_SIZE, items.size());
        for (int i = 0; i < max; i++) {
            final View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.vetro_shelf_row, rows, false);
            bindVideo(row, items.get(i));
            VetroRound.apply(row.findViewById(R.id.shelfThumb), 8);
            row.setVisibility(i < ROWS_SHOWN ? View.VISIBLE : View.GONE);
            rows.addView(row);
        }
        if (max > ROWS_SHOWN) {
            final ImageView expand = new ImageView(requireContext());
            expand.setImageResource(R.drawable.ic_expand_more);
            expand.setImageTintList(android.content.res.ColorStateList.valueOf(
                    ((TextView) shelfView.findViewById(R.id.shelfTitle)).getCurrentTextColor()));
            expand.setBackgroundResource(R.drawable.vetro_pill);
            final int pad = org.schabi.newpipe.util.DeviceUtils.dpToPx(6, requireContext());
            expand.setPadding(pad, pad, pad, pad);
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    org.schabi.newpipe.util.DeviceUtils.dpToPx(44, requireContext()),
                    org.schabi.newpipe.util.DeviceUtils.dpToPx(32, requireContext()));
            params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            params.topMargin = pad;
            expand.setLayoutParams(params);
            expand.setOnClickListener(v -> {
                for (int i = 0; i < rows.getChildCount(); i++) {
                    rows.getChildAt(i).setVisibility(View.VISIBLE);
                }
                expand.setVisibility(View.GONE);
            });
            rows.addView(expand);
        }
        return true;
    }

    private boolean addPlaylistShelf(@NonNull final LinearLayout content,
                                     @NonNull final String title, @NonNull final String tab,
                                     @Nullable final List<InfoItem> items) {
        final List<PlaylistInfoItem> playlists = new ArrayList<>();
        if (items != null) {
            for (final InfoItem item : items) {
                if (item instanceof PlaylistInfoItem) {
                    playlists.add((PlaylistInfoItem) item);
                }
            }
        }
        if (playlists.isEmpty()) {
            return false;
        }
        final LinearLayout row = shelf(content, title, null, tab);
        for (final PlaylistInfoItem p : playlists.subList(0, Math.min(SHELF_SIZE,
                playlists.size()))) {
            final View cell = LayoutInflater.from(requireContext())
                    .inflate(R.layout.vetro_shelf_video, row, false);
            VetroRound.apply(cell.findViewById(R.id.shelfThumb), 12);
            CoilHelper.INSTANCE.loadThumbnail(cell.findViewById(R.id.shelfThumb),
                    p.getThumbnails());
            ((TextView) cell.findViewById(R.id.shelfName)).setText(p.getName());
            final TextView count = cell.findViewById(R.id.shelfDuration);
            if (p.getStreamCount() >= 0) {
                count.setText(Localization.localizeStreamCountMini(requireContext(),
                        p.getStreamCount()));
                count.setVisibility(View.VISIBLE);
            }
            ((TextView) cell.findViewById(R.id.shelfInfo)).setText(p.getUploaderName());
            cell.setOnClickListener(v -> NavigationHelper.openPlaylistFragment(getFM(),
                    p.getServiceId(), p.getUrl(), p.getName()));
            row.addView(cell);
        }
        return true;
    }

    private LinearLayout shelf(@NonNull final LinearLayout content, @NonNull final String title,
                               @Nullable final String note, @Nullable final String tab) {
        final View shelf = LayoutInflater.from(requireContext())
                .inflate(R.layout.vetro_shelf, content, false);
        final TextView titleView = shelf.findViewById(R.id.shelfTitle);
        final TextView more = shelf.findViewById(R.id.shelfMore);
        if (tab != null) {
            titleView.setText(title + "  ›");
            titleView.setOnClickListener(v -> openTab(tab));
            more.setVisibility(View.GONE);
        } else if (note != null) {
            titleView.setText(title);
            more.setText(note);
        } else {
            titleView.setText(title);
            more.setVisibility(View.GONE);
        }
        content.addView(shelf);
        return shelf.findViewById(R.id.shelfItems);
    }

    private void bindVideo(@NonNull final View cell, @NonNull final StreamInfoItem item) {
        VetroRound.apply(cell.findViewById(R.id.shelfThumb), 12);
        CoilHelper.INSTANCE.loadThumbnail(cell.findViewById(R.id.shelfThumb),
                item.getThumbnails());
        ((TextView) cell.findViewById(R.id.shelfName)).setText(item.getName());
        final TextView duration = cell.findViewById(R.id.shelfDuration);
        if (duration != null) {
            if (item.getStreamType() == StreamType.LIVE_STREAM) {
                duration.setText(R.string.duration_live);
                duration.setVisibility(View.VISIBLE);
            } else if (item.getDuration() > 0) {
                duration.setText(Localization.getDurationString(item.getDuration()));
                duration.setVisibility(View.VISIBLE);
            }
        }
        final TextView badge = cell.findViewById(R.id.shelfBadge);
        if (badge != null && item.getContentAvailability() == ContentAvailability.MEMBERSHIP) {
            badge.setText(R.string.vt_channel_members_badge);
            badge.setVisibility(View.VISIBLE);
        }
        final List<String> info = new ArrayList<>();
        if (item.getViewCount() >= 0) {
            info.add(Localization.shortViewCount(requireContext(), item.getViewCount()));
        } else if (item.getShortDescription() != null && !item.getShortDescription().isEmpty()) {
            info.add(item.getShortDescription());
        }
        final String date = item.getUploadDate() != null
                ? Localization.relativeTime(item.getUploadDate().offsetDateTime())
                : item.getTextualUploadDate();
        if (date != null && !date.isEmpty()) {
            info.add(date);
        }
        ((TextView) cell.findViewById(R.id.shelfInfo)).setText(String.join(" · ", info));
        cell.setOnClickListener(v -> NavigationHelper.openVideoDetailFragment(requireContext(),
                getFM(), item.getServiceId(), item.getUrl(), item.getName(), null, false));
    }

    // "Vedi tutto": la scheda del canale con quel contenuto
    private void openTab(@NonNull final String tab) {
        final Fragment parent = getParentFragment();
        if (parent instanceof ChannelFragment) {
            ((ChannelFragment) parent).selectChannelTab(tab);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    // ----- lettura della risposta di YouTube -----

    @Nullable
    private static JSONObject findFirst(@Nullable final Object node, @NonNull final String key) {
        if (node instanceof JSONObject) {
            final JSONObject object = (JSONObject) node;
            final JSONObject hit = object.optJSONObject(key);
            if (hit != null) {
                return hit;
            }
            final JSONArray names = object.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                final JSONObject found = findFirst(object.opt(names.optString(i)), key);
                if (found != null) {
                    return found;
                }
            }
        } else if (node instanceof JSONArray) {
            final JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                final JSONObject found = findFirst(array.opt(i), key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @NonNull
    private static String text(@Nullable final JSONObject object) {
        if (object == null) {
            return "";
        }
        if (object.has("simpleText")) {
            return object.optString("simpleText");
        }
        final JSONArray runs = object.optJSONArray("runs");
        final StringBuilder out = new StringBuilder();
        for (int i = 0; runs != null && i < runs.length(); i++) {
            final JSONObject run = runs.optJSONObject(i);
            if (run != null) {
                out.append(run.optString("text"));
            }
        }
        return out.toString();
    }
}
