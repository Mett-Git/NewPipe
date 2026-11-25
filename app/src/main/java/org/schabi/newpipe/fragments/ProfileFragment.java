package org.schabi.newpipe.fragments;

import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.image.CoilHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * La scheda Profilo, come "Tu" su YouTube: i video visti di recente in una fila e sotto le tue
 * cose. NewPipe non ha account: tutto quello che c'è qui sta solo sul telefono.
 */
public class ProfileFragment extends BaseFragment {
    private static final int MAX_HISTORY = 15;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private LinearLayout historyCells;
    private View historySection;
    private TextView subtitle;
    private View header;
    /** Il profilo scelto da te: nome, colore, foto, copertina. */
    private VetroProfile profile;
    /** true = la prossima immagine scelta è la copertina, false = la foto. */
    private boolean pickingCover;
    private ActivityResultLauncher<String> pickImage;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profile = new VetroProfile(requireContext());
        pickImage = registerForActivityResult(new ActivityResultContracts.GetContent(),
                this::onImagePicked);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        historyCells = rootView.findViewById(R.id.profileHistory);
        historySection = rootView.findViewById(R.id.profileHistorySection);
        subtitle = rootView.findViewById(R.id.profileSubtitle);
        header = rootView;
        profile.show(rootView);
        rootView.findViewById(R.id.profileEdit).setOnClickListener(v -> openEditor());
        rootView.findViewById(R.id.profileAvatarFrame).setOnClickListener(v -> openEditor());
        final FragmentManager manager = requireActivity().getSupportFragmentManager();
        rootView.findViewById(R.id.profileHistoryAll).setOnClickListener(
                v -> NavigationHelper.openStatisticFragment(manager));

        final LinearLayout rows = rootView.findViewById(R.id.profileRows);
        addRow(rows, R.drawable.ic_tv, R.string.tab_subscriptions,
                () -> NavigationHelper.openSubscriptionFragment(manager));
        addRow(rows, R.drawable.ic_bookmark, R.string.tab_bookmarks,
                () -> NavigationHelper.openBookmarksFragment(manager));
        addRow(rows, R.drawable.ic_file_download, R.string.downloads,
                () -> NavigationHelper.openDownloads(requireActivity()));
        addRow(rows, R.drawable.ic_settings, R.string.settings,
                () -> NavigationHelper.openSettings(requireContext()));
    }

    private void addRow(final LinearLayout parent, @DrawableRes final int icon,
                        @StringRes final int title, final Runnable action) {
        final View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.vetro_menu_row, parent, false);
        ((ImageView) row.findViewById(R.id.vetroRowIcon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.vetroRowTitle)).setText(title);
        row.setOnClickListener(v -> action.run());
        parent.addView(row);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (historyCells == null) {
            return;
        }
        disposables.clear();
        disposables.add(new HistoryRecordManager(requireContext()).getStreamStatistics()
                .firstOrError()
                .map(entries -> {
                    final List<StreamStatisticsEntry> sorted = new ArrayList<>(entries);
                    sorted.sort(Comparator.comparing(
                            StreamStatisticsEntry::getLatestAccessDate).reversed());
                    return sorted.subList(0, Math.min(sorted.size(), MAX_HISTORY));
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showHistory, error -> historySection.setVisibility(View.GONE)));
        disposables.add(new SubscriptionManager(requireContext()).subscriptions().firstOrError()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> subtitle.setText(getResources().getQuantityString(
                        R.plurals.profile_subscriptions, list.size(), list.size())),
                        error -> { }));
    }

    @Override
    public void onPause() {
        super.onPause();
        disposables.clear();
    }

    private void showHistory(final List<StreamStatisticsEntry> entries) {
        historySection.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        historyCells.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(historyCells.getContext());
        for (final StreamStatisticsEntry entry : entries) {
            final StreamEntity stream = entry.getStreamEntity();
            final View cell = inflater.inflate(R.layout.vetro_profile_video, historyCells, false);
            final ImageView thumbnail = cell.findViewById(R.id.vetroRowIcon);
            CoilHelper.INSTANCE.loadThumbnail(thumbnail, stream.getThumbnailUrl());
            ((TextView) cell.findViewById(R.id.vetroRowTitle)).setText(stream.getTitle());
            ((TextView) cell.findViewById(R.id.vetroRowSubtitle)).setText(stream.getUploader());
            cell.setOnClickListener(v -> NavigationHelper.openVideoDetailFragment(
                    requireContext(), requireActivity().getSupportFragmentManager(),
                    stream.getServiceId(), stream.getUrl(), stream.getTitle(), null, false));
            historyCells.addView(cell);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        historyCells = null;
        header = null;
    }

    // la finestra "Modifica profilo": nome, colore, foto e copertina
    private void openEditor() {
        final View form = LayoutInflater.from(requireContext())
                .inflate(R.layout.vetro_profile_edit, null, false);
        final EditText name = form.findViewById(R.id.profileEditName);
        name.setText(profile.name());
        final int[] chosen = {profile.color()};
        final LinearLayout colors = form.findViewById(R.id.profileEditColors);
        final int size = DeviceUtils.dpToPx(30, requireContext());
        final int gap = DeviceUtils.dpToPx(8, requireContext());
        for (final int color : VetroProfile.COLORS) {
            final View swatch = new View(requireContext());
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginEnd(gap);
            swatch.setLayoutParams(params);
            swatch.setTag(color);
            swatch.setOnClickListener(v -> {
                chosen[0] = color;
                paintSwatches(colors, color);
            });
            colors.addView(swatch);
        }
        paintSwatches(colors, chosen[0]);

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.vt_profile_edit)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    profile.save(name.getText().toString(), chosen[0]);
                    refreshHeader();
                })
                .create();
        // foto e copertina: si tiene quello che hai già scritto, poi si apre la galleria
        form.findViewById(R.id.profileEditPhoto).setOnClickListener(v -> {
            profile.save(name.getText().toString(), chosen[0]);
            dialog.dismiss();
            pickingCover = false;
            pickImage.launch("image/*");
        });
        form.findViewById(R.id.profileEditCover).setOnClickListener(v -> {
            profile.save(name.getText().toString(), chosen[0]);
            dialog.dismiss();
            pickingCover = true;
            pickImage.launch("image/*");
        });
        form.findViewById(R.id.profileEditReset).setOnClickListener(v -> {
            profile.save(name.getText().toString(), chosen[0]);
            profile.removeImages();
            dialog.dismiss();
            refreshHeader();
        });
        dialog.show();
    }

    // il colore scelto ha un anello bianco attorno
    private void paintSwatches(final LinearLayout colors, final int selected) {
        for (int i = 0; i < colors.getChildCount(); i++) {
            final View swatch = colors.getChildAt(i);
            final int color = (int) swatch.getTag();
            final GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(color);
            if (color == selected) {
                circle.setStroke(DeviceUtils.dpToPx(3, requireContext()), 0xFFFFFFFF);
            }
            swatch.setBackground(circle);
        }
    }

    private void onImagePicked(@Nullable final Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            profile.saveImage(uri, pickingCover);
        } catch (final Exception e) {
            Toast.makeText(requireContext(), R.string.general_error, Toast.LENGTH_SHORT).show();
        }
        refreshHeader();
    }

    private void refreshHeader() {
        if (header != null) {
            profile.show(header);
        }
    }
}
