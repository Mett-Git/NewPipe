package org.schabi.newpipe.music;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

/** La coda da sistemare: tieni la maniglia per spostare, scorri di lato per togliere. */
public final class MusicQueuePanel {
    private final PlayQueue queue;
    private final RecyclerView list;
    private final QueueAdapter adapter = new QueueAdapter();
    private final ItemTouchHelper touchHelper;
    private boolean dragging = false;
    private boolean playing = true;

    /**
     * @param body  dove mostrarlo
     * @param queue la coda del lettore
     */
    public MusicQueuePanel(@NonNull final ViewGroup body, @NonNull final PlayQueue queue) {
        this.queue = queue;
        body.removeAllViews();
        list = new RecyclerView(body.getContext());
        list.setLayoutManager(new LinearLayoutManager(body.getContext()));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, body.getResources().getDimensionPixelSize(R.dimen.vm_list_end));
        list.setAdapter(adapter);
        touchHelper = new ItemTouchHelper(new TouchCallback());
        touchHelper.attachToRecyclerView(list);
        body.addView(list);
        list.scrollToPosition(Math.max(0, queue.getIndex()));
    }

    /**
     * @param isPlaying true se la musica sta andando
     */
    public void refresh(final boolean isPlaying) {
        playing = isPlaying;
        if (!dragging && !list.isComputingLayout()) {
            adapter.notifyDataSetChanged();
        }
    }

    /** Toglie tutto tranne il brano che sta suonando. */
    public void clearOthers() {
        for (int i = queue.size() - 1; i >= 0; i--) {
            if (i != queue.getIndex()) {
                queue.remove(i);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private final class QueueAdapter extends RecyclerView.Adapter<RowHolder> {
        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            return new RowHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.music_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final RowHolder holder, final int position) {
            final PlayQueueItem item = queue.getItem(position);
            if (item == null) {
                return;
            }
            final boolean current = position == queue.getIndex();
            holder.title.setText(item.getTitle());
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                    current ? R.color.vm_accent : R.color.vm_text));
            holder.subtitle.setText(item.getUploader());
            holder.bars.setVisibility(current ? View.VISIBLE : View.GONE);
            holder.bars.setMoving(playing);
            MusicPage.loadCover(holder.cover, item.getThumbnails());
        }

        @Override
        public int getItemCount() {
            return queue.size();
        }
    }

    private final class RowHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView subtitle;
        private final ImageView cover;
        private final MusicBars bars;

        @SuppressLint("ClickableViewAccessibility")
        RowHolder(@NonNull final View view) {
            super(view);
            title = view.findViewById(R.id.musicRowTitle);
            subtitle = view.findViewById(R.id.musicRowSubtitle);
            cover = view.findViewById(R.id.musicRowCover);
            bars = view.findViewById(R.id.musicRowBars);
            final ImageView handle = view.findViewById(R.id.musicRowMore);
            handle.setImageResource(R.drawable.ic_vm_drag);
            handle.setContentDescription(view.getContext().getString(R.string.vm_move));
            handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(this);
                }
                return true;
            });
            view.setOnClickListener(v -> {
                if (getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                    queue.setIndex(getBindingAdapterPosition());
                }
            });
        }
    }

    private final class TouchCallback extends ItemTouchHelper.SimpleCallback {
        TouchCallback() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                    ItemTouchHelper.START | ItemTouchHelper.END);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public void onSelectedChanged(final RecyclerView.ViewHolder viewHolder,
                                      final int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                dragging = true;
            } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                dragging = false;
            }
        }

        @Override
        public boolean onMove(@NonNull final RecyclerView recyclerView,
                              @NonNull final RecyclerView.ViewHolder source,
                              @NonNull final RecyclerView.ViewHolder target) {
            final int from = source.getBindingAdapterPosition();
            final int to = target.getBindingAdapterPosition();
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                return false;
            }
            queue.move(from, to);
            adapter.notifyItemMoved(from, to);
            return true;
        }

        @Override
        public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                             final int direction) {
            final int position = viewHolder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                queue.remove(position);
                adapter.notifyItemRemoved(position);
            }
        }
    }
}
