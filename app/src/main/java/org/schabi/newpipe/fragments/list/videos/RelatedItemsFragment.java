package org.schabi.newpipe.fragments.list.videos;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.RelatedItemsHeaderBinding;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.info_list.dialog.InfoItemDialog;
import org.schabi.newpipe.ktx.ViewUtils;

import java.io.Serializable;
import java.util.function.Supplier;

import io.reactivex.rxjava3.core.Single;

public class RelatedItemsFragment extends BaseListInfoFragment<InfoItem, RelatedItemsInfo>
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String INFO_KEY = "related_info_key";

    private RelatedItemsInfo relatedItemsInfo;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private RelatedItemsHeaderBinding headerBinding;
    /** Canale del video aperto, per la pillola "Di ...". */
    @Nullable
    private String channelUrl;
    @Nullable
    private String channelName;
    /** La pillola scelta: 0 Tutti, 1 Di canale, 2 Video correlati, 3 Per te. */
    private int chip = 0;

    public static RelatedItemsFragment getInstance(final StreamInfo info) {
        final RelatedItemsFragment instance = new RelatedItemsFragment();
        instance.setInitialData(info);
        return instance;
    }

    public RelatedItemsFragment() {
        super(UserAction.REQUESTED_STREAM);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_related_items, container, false);
    }

    @Override
    public void onDestroyView() {
        headerBinding = null;
        super.onDestroyView();
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        if (relatedItemsInfo == null || relatedItemsInfo.getRelatedItems() == null) {
            return null;
        }

        headerBinding = RelatedItemsHeaderBinding
                .inflate(activity.getLayoutInflater(), itemsList, false);

        final SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(requireContext());
        final boolean autoplay = pref.getBoolean(getString(R.string.auto_queue_key), false);
        headerBinding.autoplaySwitch.setChecked(autoplay);
        headerBinding.autoplaySwitch.setOnCheckedChangeListener((compoundButton, b) ->
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                        .putBoolean(getString(R.string.auto_queue_key), b).apply());

        // sopra "Avanti": le pillole come su YouTube (Tutti, Di canale, Video correlati, Per te)
        final android.widget.LinearLayout header = new android.widget.LinearLayout(activity);
        header.setOrientation(android.widget.LinearLayout.VERTICAL);
        header.addView(chipsRow());
        header.addView(headerBinding.getRoot());
        return () -> header;
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<InfoItem>> loadMoreItemsLogic() {
        return Single.fromCallable(ListExtractor.InfoItemsPage::emptyPage);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<RelatedItemsInfo> loadResult(final boolean forceLoad) {
        return Single.fromCallable(() -> relatedItemsInfo);
    }

    @Override
    public void showLoading() {
        super.showLoading();
        if (headerBinding != null) {
            headerBinding.getRoot().setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void handleResult(@NonNull final RelatedItemsInfo result) {
        super.handleResult(result);

        if (headerBinding != null) {
            headerBinding.getRoot().setVisibility(View.VISIBLE);
        }
        ViewUtils.slideUp(requireView(), 120, 96, 0.06f);

    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setTitle(final String title) {
        // Nothing to do - override parent
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        // Nothing to do - override parent
    }

    // ----- pillole -----

    private View chipsRow() {
        final android.widget.HorizontalScrollView scroll =
                new android.widget.HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        final int pad = org.schabi.newpipe.util.DeviceUtils.dpToPx(16, activity);
        scroll.setPadding(pad, pad / 2, pad / 2, pad / 2);
        final android.widget.LinearLayout row = new android.widget.LinearLayout(activity);
        final String[] names = {getString(R.string.vt_chip_all),
                channelName == null ? null : getString(R.string.vt_chip_from, channelName),
                getString(R.string.vt_chip_related), getString(R.string.vt_chip_for_you)};
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) {
                continue;
            }
            final int index = i;
            final android.widget.TextView pill = new android.widget.TextView(activity, null, 0,
                    R.style.VetroPill);
            final android.widget.LinearLayout.LayoutParams params =
                    new android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            org.schabi.newpipe.util.DeviceUtils.dpToPx(34, activity));
            params.setMarginEnd(org.schabi.newpipe.util.DeviceUtils.dpToPx(8, activity));
            pill.setLayoutParams(params);
            pill.setText(names[i]);
            pill.setTag(i);
            pill.setOnClickListener(v -> {
                chip = index;
                paintChips(row);
                showChip();
            });
            row.addView(pill);
        }
        paintChips(row);
        scroll.addView(row);
        return scroll;
    }

    // la pillola scelta è bianca col testo nero, come su YouTube
    private void paintChips(@NonNull final android.widget.LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            final android.widget.TextView pill = (android.widget.TextView) row.getChildAt(i);
            final boolean on = (int) pill.getTag() == chip;
            if (on) {
                final android.graphics.drawable.GradientDrawable white =
                        new android.graphics.drawable.GradientDrawable();
                white.setColor(android.graphics.Color.WHITE);
                white.setCornerRadius(org.schabi.newpipe.util.DeviceUtils.dpToPx(17, activity));
                pill.setBackground(white);
                pill.setTextColor(android.graphics.Color.BLACK);
            } else {
                pill.setBackgroundResource(R.drawable.vetro_pill);
                pill.setTextColor(androidx.core.content.ContextCompat.getColor(activity,
                        R.color.vetro_text));
            }
        }
    }

    // la lista secondo la pillola scelta
    private void showChip() {
        if (relatedItemsInfo == null || relatedItemsInfo.getRelatedItems() == null) {
            return;
        }
        final java.util.List<InfoItem> all = relatedItemsInfo.getRelatedItems();
        final java.util.List<InfoItem> shown = new java.util.ArrayList<>();
        if (chip == 3) {
            // per te: prima i canali che ti piacciono o tra i preferiti, poi gli altri;
            // via quelli dei canali che non ti piacciono
            final java.util.Set<String> loved = new java.util.HashSet<>();
            loved.addAll(org.schabi.newpipe.util.VetroTaste.channels(activity,
                    org.schabi.newpipe.util.VetroTaste.Kind.FAVOURITE));
            loved.addAll(org.schabi.newpipe.util.VetroTaste.channels(activity,
                    org.schabi.newpipe.util.VetroTaste.Kind.LIKED));
            final java.util.Set<String> hated = org.schabi.newpipe.util.VetroTaste.channels(
                    activity, org.schabi.newpipe.util.VetroTaste.Kind.DISLIKED);
            final java.util.List<InfoItem> rest = new java.util.ArrayList<>();
            for (final InfoItem item : all) {
                final String channel = item instanceof StreamInfoItem
                        ? ((StreamInfoItem) item).getUploaderUrl() : null;
                if (channel != null && hated.contains(channel)) {
                    continue;
                }
                (channel != null && loved.contains(channel) ? shown : rest).add(item);
            }
            shown.addAll(rest);
        } else {
            for (final InfoItem item : all) {
                final boolean same = item instanceof StreamInfoItem && isSameChannel(
                        (StreamInfoItem) item);
                if (chip == 0 || (chip == 1 && same) || (chip == 2 && !same)) {
                    shown.add(item);
                }
            }
        }
        infoListAdapter.clearStreamItemList();
        infoListAdapter.addInfoItemList(shown);
        if (chip == 1 && shown.isEmpty() && channelUrl != null) {
            loadChannelVideos();
        }
    }

    private boolean isSameChannel(@NonNull final StreamInfoItem item) {
        return (channelUrl != null && channelUrl.equals(item.getUploaderUrl()))
                || (channelName != null && channelName.equals(item.getUploaderName()));
    }

    // nessun consigliato dello stesso canale: come su YouTube, i suoi ultimi video
    private void loadChannelVideos() {
        final String url = channelUrl;
        final io.reactivex.rxjava3.disposables.Disposable d = org.schabi.newpipe.util
                .ExtractorHelper.getChannelInfo(serviceId, url, false)
                .flatMap(channel -> {
                    for (final org.schabi.newpipe.extractor.linkhandler.ListLinkHandler tab
                            : channel.getTabs()) {
                        if (tab.getContentFilters().contains(
                                org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS)) {
                            return org.schabi.newpipe.util.ExtractorHelper.getChannelTab(
                                    serviceId, tab, false);
                        }
                    }
                    return Single.error(new IllegalStateException("Nessuna scheda video"));
                })
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(tab -> {
                    if (chip == 1 && infoListAdapter.getItemsList().isEmpty()) {
                        infoListAdapter.addInfoItemList(tab.getRelatedItems());
                    }
                }, e -> { });
    }

    private void setInitialData(final StreamInfo info) {
        super.setInitialData(info.getServiceId(), info.getUrl(), info.getName());
        channelUrl = info.getUploaderUrl();
        channelName = info.getUploaderName();
        if (this.relatedItemsInfo == null) {
            this.relatedItemsInfo = new RelatedItemsInfo(info);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(INFO_KEY, relatedItemsInfo);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedState) {
        super.onRestoreInstanceState(savedState);
        final Serializable serializable = savedState.getSerializable(INFO_KEY);
        if (serializable instanceof RelatedItemsInfo) {
            this.relatedItemsInfo = (RelatedItemsInfo) serializable;
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        if (headerBinding != null && getString(R.string.auto_queue_key).equals(key)) {
            headerBinding.autoplaySwitch.setChecked(sharedPreferences.getBoolean(key, false));
        }
    }

    @Override
    protected ItemViewMode getItemViewMode() {
        ItemViewMode mode = super.getItemViewMode();
        // Only list mode is supported. Either List or card will be used.
        if (mode != ItemViewMode.LIST && mode != ItemViewMode.CARD) {
            mode = ItemViewMode.LIST;
        }
        return mode;
    }

    @Override
    protected void showInfoItemDialog(final StreamInfoItem item) {
        // Try and attach the InfoItemDialog to the parent fragment of the RelatedItemsFragment
        // so that its context is not lost when the RelatedItemsFragment is reinitialized,
        // e.g. when a new stream is loaded in a parent VideoDetailFragment.
        final Fragment parentFragment = getParentFragment();
        if (parentFragment != null) {
            try {
                new InfoItemDialog.Builder(
                        parentFragment.getActivity(),
                        parentFragment.getContext(),
                        parentFragment,
                        item
                ).create().show();
            } catch (final IllegalArgumentException e) {
                InfoItemDialog.Builder.reportErrorDuringInitialization(e, item);
            }
        } else {
            super.showInfoItemDialog(item);
        }
    }

}
