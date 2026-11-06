package org.schabi.newpipe.util;

import androidx.annotation.Nullable;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

/**
 * YouTube ora manda la foto di chi commenta in {@code author.avatarThumbnailUrl}, mentre
 * l'estrattore di NewPipe la cerca ancora in {@code avatar.image.sources} e non la trova.
 * Qui si completa la risposta nel punto dove lui la cerca. Se un giorno YouTube o l'estrattore
 * cambiano, le risposte che hanno già la foto o non hanno quel campo restano come sono.
 */
public final class YoutubeCommentAvatars {
    private static final int SIZE = 88;

    private YoutubeCommentAvatars() {
    }

    /**
     * @param url  l'indirizzo chiesto
     * @param body la risposta
     * @return la risposta, completata con le foto se è una pagina di commenti di YouTube
     */
    @Nullable
    public static String fix(final String url, @Nullable final String body) {
        if (body == null || !url.contains("youtubei/v1/next")
                || !body.contains("commentEntityPayload")
                || !body.contains("avatarThumbnailUrl")) {
            return body;
        }
        try {
            final JsonObject root = JsonParser.object().from(body);
            final JsonArray mutations = root.getObject("frameworkUpdates")
                    .getObject("entityBatchUpdate").getArray("mutations");
            for (final Object mutation : mutations) {
                if (mutation instanceof JsonObject) {
                    addAvatar(((JsonObject) mutation).getObject("payload")
                            .getObject("commentEntityPayload"));
                }
            }
            return JsonWriter.string(root);
        } catch (final JsonParserException e) {
            return body;
        }
    }

    private static void addAvatar(final JsonObject comment) {
        final String avatarUrl = comment.getObject("author").getString("avatarThumbnailUrl");
        if (avatarUrl == null || comment.has("avatar")) {
            return;
        }
        final JsonObject source = new JsonObject();
        source.put("url", avatarUrl);
        source.put("width", SIZE);
        source.put("height", SIZE);
        final JsonArray sources = new JsonArray();
        sources.add(source);
        final JsonObject image = new JsonObject();
        image.put("sources", sources);
        final JsonObject avatar = new JsonObject();
        avatar.put("image", image);
        comment.put("avatar", avatar);
    }
}
