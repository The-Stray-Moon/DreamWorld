package cc.sighs.dreamworld.spi;

/**
 * How the dream layer colours terrain and water.
 *
 * <p>{@link #MODE_1} keeps terrain on the pink wheel and water on the blue-green one;
 * {@link #MODE_2} swaps the two wheels. {@link #RANDOM} picks one of the two whenever a client
 * enters the dream.</p>
 */
public enum DreamColorMode {
    RANDOM,
    MODE_1,
    MODE_2;

    /** {@return whether terrain and water swap hue wheels for this mode} */
    public boolean swaps(boolean randomChoice) {
        return this == MODE_2 || (this == RANDOM && randomChoice);
    }
}
