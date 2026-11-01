package cc.sighs.dreamworld.spi;

/** Current values of the mod's client and server settings. */
public interface DreamConfig {
    DreamColorMode colorMode();

    int ambientSkyLight();

    int maxTunnelsPerGraffiti();

    int dreamEntryDelayTicks();

    double graffitiSleepRange();

    double maxGraffitiSleepSpeedup();
}
