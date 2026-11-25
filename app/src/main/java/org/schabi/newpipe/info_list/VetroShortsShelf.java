package org.schabi.newpipe.info_list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.image.CoilHelper;

import java.util.List;

/**
 * Una fila di Shorts in mezzo alla lista della Home, come su YouTube: nella lista è una
 * "riga" sola che scorre di lato (InfoListAdapter la disegna con {@link Holder}).
 */
public final class VetroShortsShelf extends InfoItem {
    private final List<StreamInfoItem> shorts;

    /**
     * @param serviceId il servizio
     * @param shorts    gli Shorts della fila
     */
    public VetroShortsShelf(final int serviceId, @NonNull final List<StreamInfoItem> shorts) {
        // il tipo non conta: InfoListAdapter riconosce la fila prima di guardare il tipo
        super(InfoType.COMMENT, serviceId, "vetro:shorts:" + System.identityHashCode(shorts),
                "Shorts");
        this.shorts = shorts;
    }

    /** La fila disegnata: titolo "Shorts" e le anteprime verticali. */
    static final class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull final ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.vetro_shelf, parent, false));
        }

        void bind(@NonNull final VetroShortsShelf shelf) {
            ((TextView) itemView.findViewById(R.id.shelfTitle))
                    .setText(R.string.channel_tab_shorts);
            ((TextView) itemView.findViewById(R.id.shelfMore))
                    .setText(R.string.vt_shorts_from_channels);
            final LinearLayout row = itemView.findViewById(R.id.shelfItems);
            row.removeAllViews();
            final LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
            for (final StreamInfoItem item : shelf.shorts) {
                final View cell = inflater.inflate(R.layout.vetro_shelf_short, row, false);
                CoilHelper.INSTANCE.loadThumbnail(cell.findViewById(R.id.shelfThumb),
                        item.getThumbnails());
                ((TextView) cell.findViewById(R.id.shelfName)).setText(item.getName());
                ((TextView) cell.findViewById(R.id.shelfInfo)).setText(item.getViewCount() >= 0
                        ? Localization.shortViewCount(itemView.getContext(), item.getViewCount())
                        : item.getUploaderName());
                cell.setOnClickListener(v -> {
                    android.content.Context context = v.getContext();
                    while (!(context instanceof FragmentActivity)
                            && context instanceof android.content.ContextWrapper) {
                        context = ((android.content.ContextWrapper) context).getBaseContext();
                    }
                    if (context instanceof FragmentActivity) {
                        final FragmentActivity activity = (FragmentActivity) context;
                        NavigationHelper.openVideoDetailFragment(activity,
                                activity.getSupportFragmentManager(), item.getServiceId(),
                                item.getUrl(), item.getName(), null, false);
                    }
                });
                row.addView(cell);
            }
        }
    }
}
