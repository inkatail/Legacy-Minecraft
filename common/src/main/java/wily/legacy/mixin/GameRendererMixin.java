package wily.legacy.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import wily.legacy.LegacyMinecraft;
import wily.legacy.LegacyMinecraftClient;
import wily.legacy.client.LegacyOptions;
import wily.legacy.client.LegacySprites;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private Minecraft minecraft;

    @Shadow private boolean hasWorldScreenshot;

    @Shadow protected abstract void takeAutoScreenshot(Path path);

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;render(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void render(ToastComponent instance, GuiGraphics graphics){
        instance.render(graphics);
        if (GLFW.glfwGetInputMode(minecraft.getWindow().getWindow(),GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_HIDDEN && !LegacyMinecraftClient.controllerHandler.isCursorDisabled) {
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            graphics.pose().pushPose();
            graphics.pose().translate(LegacyMinecraftClient.controllerHandler.getPointerX(),LegacyMinecraftClient.controllerHandler.getPointerY(), 0);
            graphics.blitSprite(LegacySprites.POINTER, -8, -8, 16, 16);
            graphics.pose().popPose();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
        }
        if (!((LegacyOptions)minecraft.options).legacyGamma().get()) return;
        float gamma = minecraft.options.gamma().get().floatValue();
        if (gamma != 0.5) {
            RenderSystem.enableBlend();
            RenderSystem.disableDepthTest();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 4400f);
            float fixedGamma;
            if (gamma> 0.5) {
                fixedGamma = (gamma - 0.5f) / 6f;
                RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE);
            }else {
                fixedGamma = 0.5f+ gamma;
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.SRC_COLOR);
            }
            RenderSystem.setShaderColor(fixedGamma, fixedGamma, fixedGamma, 1.0f);
            graphics.blit(new ResourceLocation(LegacyMinecraft.MOD_ID, "textures/gui/gamma.png"), 0, 0, 0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, (gamma > 0.5f ? gamma - 0.5f : 0.5f - gamma) / 2f);
            RenderSystem.defaultBlendFunc();
            graphics.blit(new ResourceLocation(LegacyMinecraft.MOD_ID, "textures/gui/gamma.png"), 0, 0, 0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
            graphics.pose().popPose();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }
    @Redirect(method = "tryTakeScreenshotIfNeeded",at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/renderer/GameRenderer;hasWorldScreenshot:Z"))
    private boolean canTakeWorldIcon(GameRenderer instance) {
        return hasWorldScreenshot && !LegacyMinecraftClient.retakeWorldIcon;
    }
    @Redirect(method = "tryTakeScreenshotIfNeeded",at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"))
    private void tryTakeScreenshotIfNeeded(Optional<Path> instance, Consumer<? super Path> action) {
        instance.ifPresent(path->{
                    if (!LegacyMinecraftClient.retakeWorldIcon && Files.isRegularFile(path)) {
                        this.hasWorldScreenshot = true;
                    } else {
                        this.takeAutoScreenshot(path);
                        LegacyMinecraftClient.retakeWorldIcon = false;
                    }
                }
        );
    }

}
