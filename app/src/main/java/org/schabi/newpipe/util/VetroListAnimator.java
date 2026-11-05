package org.schabi.newpipe.util;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;

/**
 * Stile Vetro: gli elementi delle liste entrano salendo e sfumando, la prima volta che si vedono.
 * I primi uno dopo l'altro, quelli che arrivano scorrendo subito e più in fretta. Tornando
 * indietro nella lista non si anima nulla.
 */
public final class VetroListAnimator implements RecyclerView.OnChildAttachStateChangeListener {
    private static final int FIRST_SCREEN_ITEMS = 8;

    private final RecyclerView list;
    private final float shift;
    private int lastAnimated = -1;

    private VetroListAnimator(@NonNull final RecyclerView list) {
        this.list = list;
        shift = DeviceUtils.dpToPx(18, list.getContext());
    }

    /**
     * Cerca le liste dentro una vista e le fa animare.
     *
     * @param view la vista di una pagina
     */
    public static void attachToLists(@NonNull final View view) {
        if (view instanceof RecyclerView) {
            if (view.getTag(R.id.vetro_list_animator) == null) {
                final VetroListAnimator animator = new VetroListAnimator((RecyclerView) view);
                view.setTag(R.id.vetro_list_animator, animator);
                ((RecyclerView) view).addOnChildAttachStateChangeListener(animator);
            }
        } else if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                attachToLists(group.getChildAt(i));
            }
        }
    }

    @Override
    public void onChildViewAttachedToWindow(@NonNull final View view) {
        final int position = list.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        if (position == 0 && list.getChildCount() == 1) {
            lastAnimated = -1; // la lista è stata ricaricata da capo
        }
        if (position <= lastAnimated) {
            return;
        }
        lastAnimated = position;

        final boolean firstScreen = position < FIRST_SCREEN_ITEMS;
        view.setAlpha(0f);
        view.setTranslationY(shift);
        final AnimatorSet animation = new AnimatorSet();
        animation.playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, shift, 0f));
        animation.setInterpolator(new DecelerateInterpolator(1.6f));
        animation.setDuration(firstScreen ? 280 : 180);
        animation.setStartDelay(firstScreen ? position * 45L : 0);
        view.setTag(R.id.vetro_item_animation, animation);
        animation.start();
    }

    @Override
    public void onChildViewDetachedFromWindow(@NonNull final View view) {
        // gli elementi si riusano: niente deve restare trasparente o spostato
        final Object animation = view.getTag(R.id.vetro_item_animation);
        if (animation instanceof Animator) {
            ((Animator) animation).cancel();
            view.setTag(R.id.vetro_item_animation, null);
        }
        view.setAlpha(1f);
        view.setTranslationY(0f);
    }
}
