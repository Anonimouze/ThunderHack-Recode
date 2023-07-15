package thunder.hack.injection;

import net.minecraft.client.gui.DrawContext;
import thunder.hack.Thunderhack;
import thunder.hack.events.impl.Render2DEvent;
import thunder.hack.events.impl.RenderBlurEvent;
import thunder.hack.gui.hud.impl.Crosshair;
import thunder.hack.gui.hud.impl.Hotbar;
import thunder.hack.modules.client.ClickGui;
import thunder.hack.utility.render.MSAAFramebuffer;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class MixinIngameHud {
    @Inject(at = @At(value = "HEAD"), method = "render")
    public void render(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (ClickGui.getInstance().msaa.getValue()) {
            MSAAFramebuffer.use(() -> Thunderhack.EVENT_BUS.post(new Render2DEvent(context.getMatrices(), context)));
        } else {
            Thunderhack.EVENT_BUS.post(new Render2DEvent(context.getMatrices(), context));
        }
        Thunderhack.EVENT_BUS.post(new RenderBlurEvent(tickDelta,context.getMatrices()));
    }

    @Inject(at = @At(value = "HEAD"), method = "renderHotbar",cancellable = true)
    public void renderHotbarCustom(float tickDelta, DrawContext context, CallbackInfo ci) {
        if(Thunderhack.moduleManager.get(Hotbar.class).isEnabled()){
            ci.cancel();
            Hotbar.renderCustomHotbar(tickDelta,context);
        }
    }

    @Inject(method = "renderExperienceBar", at = @At(value = "HEAD"),cancellable = true)
    public void renderXpBarCustom(DrawContext context, int x, CallbackInfo ci) {
        if(Thunderhack.moduleManager.get(Hotbar.class).isEnabled()){
            ci.cancel();
            Hotbar.renderXpBar(x,context.getMatrices());
        }
    }

    @Inject(method = "renderCrosshair",at = @At(value = "HEAD"),cancellable = true)
    public void renderCrosshair(DrawContext context, CallbackInfo ci) {
        if(Thunderhack.moduleManager.get(Crosshair.class).isEnabled()){
            ci.cancel();
        }
    }
}