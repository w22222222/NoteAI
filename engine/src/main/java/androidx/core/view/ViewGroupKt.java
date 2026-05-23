package androidx.core.view;

import android.view.View;
import android.view.ViewGroup;

import java.util.Iterator;
import java.util.NoSuchElementException;

import kotlin.sequences.Sequence;

public final class ViewGroupKt {
    private ViewGroupKt() {
    }

    public static Sequence<View> getChildren(ViewGroup viewGroup) {
        return () -> new Iterator<View>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < viewGroup.getChildCount();
            }

            @Override
            public View next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return viewGroup.getChildAt(index++);
            }
        };
    }
}
