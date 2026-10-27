package dev.focus.pomodoro;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** One generic adapter for every list in the app. */
abstract class Rows<T> extends RecyclerView.Adapter<Rows.H> {
    List<T> data = new ArrayList<>();

    abstract View make(Context c);

    abstract void bind(View v, T item);

    static final class H extends RecyclerView.ViewHolder {
        H(View v) { super(v); }
    }

    @Override
    public H onCreateViewHolder(ViewGroup p, int type) {
        View v = make(p.getContext());
        if (v.getLayoutParams() == null) v.setLayoutParams(new RecyclerView.LayoutParams(UI.M, UI.W));
        return new H(v);
    }

    @Override
    public void onBindViewHolder(H h, int i) { bind(h.itemView, data.get(i)); }

    @Override
    public int getItemCount() { return data.size(); }

    void set(List<T> l) {
        data = l;
        notifyDataSetChanged();
    }
}
