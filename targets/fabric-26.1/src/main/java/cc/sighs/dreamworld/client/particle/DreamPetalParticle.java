package cc.sighs.dreamworld.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * A cherry petal that keeps the velocity it was spawned with.
 *
 * <p>The vanilla {@code CHERRY_LEAVES} provider drops the speed arguments on the floor - its particle
 * only ever drifts on a soft wind - so it cannot be used for a burst. This one copies the vanilla look
 * (same sprite sheet, size, spin and gravity) and adds the initial push, which is what lets the wand's
 * blast throw petals outwards.</p>
 *
 * <p>26.1 replaced {@code TextureSheetParticle} with {@link SingleQuadParticle}; the quad's layer is
 * chosen from the stitched sprite so opaque and translucent petal sheets both work.</p>
 */
public class DreamPetalParticle extends SingleQuadParticle {
    /** Lifetime of one petal, in ticks. */
    private static final int LIFETIME = 160;
    private static final float GRAVITY = 0.0008F;
    /** Fraction of the velocity kept per tick. */
    private static final float DRAG = 0.88F;
    /** Sideways drift that keeps the petal fluttering instead of flying like a bullet. */
    private static final float FLUTTER = 0.02F;

    private final float flutterAngle;
    private final float spinAcceleration;
    private final SingleQuadParticle.Layer layer;
    private float rotSpeed;

    protected DreamPetalParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.layer = SingleQuadParticle.Layer.bySprite(sprite);
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.lifetime = LIFETIME + this.random.nextInt(40);
        this.gravity = GRAVITY;
        this.friction = DRAG;
        this.rotSpeed = (float) Math.toRadians(this.random.nextBoolean() ? -30.0D : 30.0D);
        this.spinAcceleration = (float) Math.toRadians(this.random.nextBoolean() ? -5.0D : 5.0D);
        this.flutterAngle = this.random.nextFloat() * 360.0F;
        float size = this.random.nextBoolean() ? 0.05F : 0.075F;
        this.quadSize = size * 1.4F;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return this.layer;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        // Slow sideways flutter on top of the remaining outward momentum.
        double angle = Math.toRadians(this.flutterAngle + this.age * 6.0D);
        this.xd += Math.cos(angle) * FLUTTER * 0.05D;
        this.zd += Math.sin(angle) * FLUTTER * 0.05D;
        this.yd -= this.gravity;
        this.rotSpeed += this.spinAcceleration / 20.0F;
        this.oRoll = this.roll;
        this.roll += this.rotSpeed / 20.0F;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= this.friction;
        this.yd *= this.friction;
        this.zd *= this.friction;
    }

    /** Provider that hands the spawn velocity through to the particle. */
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            return new DreamPetalParticle(level, x, y, z, xSpeed, ySpeed, zSpeed,
                    sprites.get(random.nextInt(12), 12));
        }
    }
}
