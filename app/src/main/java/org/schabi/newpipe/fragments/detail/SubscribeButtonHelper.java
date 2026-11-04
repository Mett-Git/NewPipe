package org.schabi.newpipe.fragments.detail;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.image.ImageStrategy;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Il tasto "Iscriviti" accanto al canale, nella pagina del video: segue le iscrizioni salvate
 * e al tocco iscrive o toglie l'iscrizione, come il tasto della pagina del canale.
 */
final class SubscribeButtonHelper {
    private static final String TAG = "SubscribeButton";
    private final CompositeDisposable disposables = new CompositeDisposable();

    /**
     * @param button il tasto (non c'è in tutti i layout)
     * @param info   il video aperto, di cui si segue il canale
     */
    void bind(@Nullable final TextView button, @NonNull final StreamInfo info) {
        disposables.clear();
        if (button == null) {
            return;
        }
        final String url = info.getUploaderUrl();
        if (url == null || url.isEmpty()) {
            button.setVisibility(View.GONE);
            return;
        }
        final SubscriptionManager manager = new SubscriptionManager(button.getContext());
        disposables.add(manager.subscriptionTable()
                .getSubscriptionFlowable(info.getServiceId(), url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(found -> show(button, manager, info,
                                found.isEmpty() ? null : found.get(0)),
                        error -> button.setVisibility(View.GONE)));
    }

    void dispose() {
        disposables.clear();
    }

    private void show(@NonNull final TextView button,
                      @NonNull final SubscriptionManager manager,
                      @NonNull final StreamInfo info,
                      @Nullable final SubscriptionEntity subscription) {
        final Context context = button.getContext();
        if (subscription == null) {
            button.setText(R.string.subscribe_button_title);
            button.setBackgroundResource(R.drawable.vetro_pill_subscribe);
            button.setTextColor(ContextCompat.getColor(context, R.color.vetro_subscribe_text));
        } else {
            button.setText(R.string.subscribed_button_title);
            button.setBackgroundResource(R.drawable.vetro_pill);
            button.setTextColor(ThemeHelper.resolveColorFromAttr(context,
                    android.R.attr.textColorPrimary));
        }
        button.setEnabled(true);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> {
            // si riabilita da solo quando il database cambia e arriva il nuovo stato
            v.setEnabled(false);
            disposables.add(Completable.fromAction(() -> {
                if (subscription == null) {
                    manager.insertSubscription(newSubscription(info));
                } else {
                    manager.deleteSubscription(subscription);
                }
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> { }, error -> {
                        Log.e(TAG, "Iscrizione dalla pagina del video non riuscita", error);
                        v.setEnabled(true);
                    }));
        });
    }

    private static SubscriptionEntity newSubscription(@NonNull final StreamInfo info) {
        final SubscriptionEntity channel = new SubscriptionEntity();
        channel.setServiceId(info.getServiceId());
        channel.setUrl(info.getUploaderUrl());
        channel.setName(info.getUploaderName());
        channel.setAvatarUrl(ImageStrategy.imageListToDbUrl(info.getUploaderAvatars()));
        if (info.getUploaderSubscriberCount() >= 0) {
            channel.setSubscriberCount(info.getUploaderSubscriberCount());
        }
        return channel;
    }
}
