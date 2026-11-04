package org.schabi.newpipe.info_list.holder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.image.CoilHelper;

import java.util.List;

/**
 * Card layout for stream.
 */
public class StreamCardInfoItemHolder extends StreamInfoItemHolder {
    private final ImageView itemUploaderAvatarView;

    public StreamCardInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_stream_card_item, parent);
        itemUploaderAvatarView = itemView.findViewById(R.id.itemUploaderAvatarView);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        super.updateFromItem(infoItem, historyRecordManager);
        // avatar del canale accanto al titolo, come in YouTube (non tutti gli elenchi lo hanno)
        final List<Image> avatars = infoItem instanceof StreamInfoItem
                ? ((StreamInfoItem) infoItem).getUploaderAvatars() : List.of();
        if (itemUploaderAvatarView == null || avatars.isEmpty()) {
            if (itemUploaderAvatarView != null) {
                itemUploaderAvatarView.setVisibility(View.GONE);
            }
            return;
        }
        itemUploaderAvatarView.setVisibility(View.VISIBLE);
        CoilHelper.INSTANCE.loadAvatar(itemUploaderAvatarView, avatars);
    }
}
