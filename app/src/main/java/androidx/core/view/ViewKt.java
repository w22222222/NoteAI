package androidx.core.view;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;

import kotlin.sequences.Sequence;

public final class ViewKt {
    private ViewKt() {
    }

    public static Sequence<ViewParent> getAncestors(View view) {
        return () -> new Iterator<ViewParent>() {
            private ViewParent next = view.getParent();

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public ViewParent next() {
                if (next == null) {
                    throw new NoSuchElementException();
                }
                ViewParent current = next;
                next = current.getParent();
                return current;
            }
        };
    }

    public static Sequence<View> getAllViews(View view) {
        return () -> new Iterator<View>() {
            private final ArrayDeque<View> pending = new ArrayDeque<>();

            {
                pending.add(view);
            }

            @Override
            public boolean hasNext() {
                return !pending.isEmpty();
            }

            @Override
            public View next() {
                if (pending.isEmpty()) {
                    throw new NoSuchElementException();
                }
                View current = pending.removeFirst();
                if (current instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) current;
                    for (int i = group.getChildCount() - 1; i >= 0; i--) {
                        pending.addFirst(group.getChildAt(i));
                    }
                }
                return current;
            }
        };
    }
}
