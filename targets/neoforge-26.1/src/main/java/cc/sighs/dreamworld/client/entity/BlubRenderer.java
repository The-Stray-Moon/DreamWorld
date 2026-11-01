package cc.sighs.dreamworld.client.entity;

import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.entity.BlubEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renderer of the Blub: the custom blocky model with its own texture.
 *
 * <p>26.1 renderers are split into state extraction and submission, so the resting flag and the
 * entity tick count travel through {@link BlubRenderState}.</p>
 */
public class BlubRenderer extends MobRenderer<BlubEntity, BlubRenderState, BlubModel> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(DreamWorld.MOD_ID, "textures/entity/blub.png");

    public BlubRenderer(EntityRendererProvider.Context context) {
        super(context, new BlubModel(context.bakeLayer(BlubModel.LAYER)), 0.3F);
    }

    @Override
    public BlubRenderState createRenderState() {
        return new BlubRenderState();
    }

    @Override
    public void extractRenderState(BlubEntity blub, BlubRenderState state, float partialTicks) {
        super.extractRenderState(blub, state, partialTicks);
        state.resting = blub.isDown();
        state.tickCount = blub.tickCount;
    }

    @Override
    public Identifier getTextureLocation(BlubRenderState state) {
        return TEXTURE;
    }
}
