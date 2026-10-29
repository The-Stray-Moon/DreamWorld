package cc.sighs.dreamworld.client.entity;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RabbitRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Rabbit;

/**
 * Renderer of the dream rabbit: the vanilla rabbit model with the blue dream texture.
 */
public class DreamRabbitRenderer extends RabbitRenderer {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DreamWorldNeoForge121.MOD_ID,
                    "textures/entity/dream_rabbit.png");

    public DreamRabbitRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Rabbit rabbit) {
        return TEXTURE;
    }
}
