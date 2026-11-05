package org.schabi.newpipe.fragments.detail;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.text.HtmlCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.image.CoilHelper;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * La pagina del video tutta insieme, come su YouTube: niente schede, sotto i tasti ci sono un
 * riquadro con la descrizione e uno con i commenti e poi direttamente i video correlati. Toccando
 * un riquadro, descrizione o commenti si aprono al posto della pagina, sotto al video.
 */
final class VideoPagePanels {
    private final FragmentVideoDetailBinding binding;
    private final TabAdapter pages;
    private final String relatedTag;
    private final String descriptionTag;
    private final String commentsTag;
    private boolean open = false;
    @Nullable
    private Disposable commentsLoader;

    VideoPagePanels(@NonNull final FragmentVideoDetailBinding binding,
                    @NonNull final TabAdapter pages,
                    @NonNull final String relatedTag,
                    @NonNull final String descriptionTag,
                    @NonNull final String commentsTag) {
        this.binding = binding;
        this.pages = pages;
        this.relatedTag = relatedTag;
        this.descriptionTag = descriptionTag;
        this.commentsTag = commentsTag;
        binding.viewPager.setSwipeEnabled(false);
        binding.detailDescriptionBox.setOnClickListener(v ->
                openPanel(descriptionTag, R.string.description_tab_description));
        binding.detailCommentsBox.setOnClickListener(v ->
                openPanel(commentsTag, R.string.comments_tab_description));
        binding.detailPanelClose.setOnClickListener(v -> close());
    }

    /**
     * Riempie i riquadri col video appena caricato.
     *
     * @param info            il video
     * @param showDescription false se la descrizione è spenta nelle impostazioni
     * @param showComments    false se i commenti sono spenti o il servizio non li ha
     */
    void bind(@NonNull final StreamInfo info, final boolean showDescription,
              final boolean showComments) {
        binding.detailDescriptionBox.setVisibility(showDescription ? View.VISIBLE : View.GONE);
        final String description = plainText(info.getDescription());
        binding.detailDescriptionPreview.setText(description.isEmpty()
                ? binding.getRoot().getContext().getString(R.string.video_box_more_info)
                : description);

        binding.detailCommentsBox.setVisibility(showComments ? View.VISIBLE : View.GONE);
        binding.detailCommentsCount.setText("");
        binding.detailCommentPreview.setVisibility(View.GONE);
        dispose();
        if (showComments) {
            commentsLoader = ExtractorHelper
                    .getCommentsInfo(info.getServiceId(), info.getUrl(), false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::showComments, error -> { });
        }
    }

    private void showComments(@NonNull final CommentsInfo info) {
        if (info.isCommentsDisabled()) {
            binding.detailCommentsCount.setText(R.string.comments_are_disabled);
            return;
        }
        if (info.getCommentsCount() > 0) {
            binding.detailCommentsCount.setText(Localization.shortCount(
                    binding.getRoot().getContext(), info.getCommentsCount()));
        }
        if (info.getRelatedItems().isEmpty()) {
            return;
        }
        final CommentsInfoItem first = info.getRelatedItems().get(0);
        binding.detailCommentText.setText(plainText(first.getCommentText()));
        CoilHelper.INSTANCE.loadAvatar(binding.detailCommentAvatar, first.getUploaderAvatars());
        binding.detailCommentPreview.setVisibility(View.VISIBLE);
    }

    private void openPanel(@NonNull final String tag, @StringRes final int title) {
        final int position = pages.getItemPositionByTitle(tag);
        if (position < 0) {
            return;
        }
        open = true;
        binding.viewPager.setCurrentItem(position, false);
        binding.detailContentRootLayout.setVisibility(View.GONE);
        binding.detailPanelTitle.setText(title);
        binding.detailPanelHeader.setVisibility(View.VISIBLE);
        slideIn();
    }

    /**
     * Chiude descrizione o commenti e torna alla pagina del video.
     *
     * @return true se c'era qualcosa aperto
     */
    boolean close() {
        if (!open) {
            return false;
        }
        open = false;
        binding.detailPanelHeader.setVisibility(View.GONE);
        binding.detailContentRootLayout.setVisibility(View.VISIBLE);
        final int position = pages.getItemPositionByTitle(relatedTag);
        if (position >= 0) {
            binding.viewPager.setCurrentItem(position, false);
        }
        slideIn();
        return true;
    }

    private void slideIn() {
        binding.viewPager.setAlpha(0f);
        binding.viewPager.setTranslationY(
                DeviceUtils.dpToPx(40, binding.getRoot().getContext()));
        binding.viewPager.animate().alpha(1f).translationY(0f).setDuration(260).start();
    }

    void dispose() {
        if (commentsLoader != null) {
            commentsLoader.dispose();
            commentsLoader = null;
        }
    }

    // testo semplice su una riga sola, per le anteprime
    @NonNull
    private static String plainText(@Nullable final Description description) {
        if (description == null || description.getContent() == null) {
            return "";
        }
        final CharSequence text = description.getType() == Description.HTML
                ? HtmlCompat.fromHtml(description.getContent(),
                        HtmlCompat.FROM_HTML_MODE_COMPACT)
                : description.getContent();
        return text.toString().replaceAll("\\s+", " ").trim();
    }
}
