package cc.sighs.dreamworld.spi;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A registry entry owned by the shared code and filled in by the target's registrar.
 *
 * <p>The shared tree names every object it needs, but only a loader knows how to register it. The
 * target binds the registered object (or a supplier of it) during mod construction; until then
 * {@link #get()} must not be called.</p>
 */
public final class DreamHolder<T> implements Supplier<T> {
    private final String path;
    private volatile Supplier<? extends T> supplier;

    public DreamHolder(String path) {
        this.path = path;
    }

    /** @return the registry path of this entry */
    public String path() {
        return path;
    }

    public void bind(Supplier<? extends T> supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    public void bind(T value) {
        T bound = Objects.requireNonNull(value, "value");
        this.supplier = () -> bound;
    }

    @Override
    public T get() {
        Supplier<? extends T> current = supplier;
        if (current == null) {
            throw new IllegalStateException("DreamWorld registry entry '" + path + "' is not bound yet");
        }
        return current.get();
    }
}
