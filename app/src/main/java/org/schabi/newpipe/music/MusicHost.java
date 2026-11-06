package org.schabi.newpipe.music;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;

/** Quello che le pagine di NewPipe Music chiedono alla schermata che le contiene. */
public interface MusicHost {
    /**
     * @param songs   i brani da mettere in coda
     * @param index   quale parte per primo
     * @param shuffle true per mescolarli
     */
    void playSongs(@NonNull List<StreamInfoItem> songs, int index, boolean shuffle);

    /**
     * @param song il brano da aggiungere in fondo alla coda
     */
    void enqueue(@NonNull StreamInfoItem song);

    /**
     * @param page la pagina da aprire sopra quella attuale
     */
    void openPage(@NonNull Fragment page);

    /** Torna alla pagina di prima. */
    void goBack();

    /**
     * @return l'altezza della barra di stato
     */
    int topInset();

    /**
     * @return lo spazio da lasciare in fondo per barra e mini lettore
     */
    int bottomSpace();

    /**
     * @return l'indirizzo del brano in riproduzione, se c'è
     */
    @Nullable
    String nowPlayingUrl();

    /**
     * @return true se la musica sta andando, false se è in pausa o ferma
     */
    boolean isPlayingNow();
}
