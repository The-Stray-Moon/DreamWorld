package cc.sighs.dreamworld.client.entity;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import cc.sighs.dreamworld.entity.BlubEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer of the Blub: the custom blocky model with its own texture.
 */
public class BlubRenderer extends MobRenderer<BlubEntity, BlubModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID,
                    "textures/entity/blub.png");

    public BlubRenderer(EntityRendererProvider.Context context) {
        super(context, new BlubModel(context.bakeLayer(BlubModel.LAYER)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(BlubEntity rabbit) {
        return TEXTURE;
    }
}
