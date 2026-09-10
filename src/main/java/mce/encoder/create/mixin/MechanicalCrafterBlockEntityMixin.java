package mce.encoder.create.mixin;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import mce.encoder.create.MceCrafterExt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds two small behaviours to Create's mechanical crafter used by our wall loader:
 * <ul>
 *   <li>while {@code mceLoading}, every {@code checkCompletedRecipe} is suppressed, so inserting a
 *       whole batch one crafter at a time never makes Create start assembling mid-load;</li>
 *   <li>after a load armed {@code mcePendingCraft}, once the wall is actually spinning we trigger a
 *       force-free {@code checkCompletedRecipe(false)} exactly once (force-free tolerates the empty
 *       crafters that surround a small recipe on a large wall).</li>
 * </ul>
 */
@Mixin(MechanicalCrafterBlockEntity.class)
public abstract class MechanicalCrafterBlockEntityMixin implements MceCrafterExt {

    @Unique
    private boolean mceLoading;

    @Unique
    private boolean mcePendingCraft;

    @Override
    @Unique
    public boolean mceIsLoading() {
        return mceLoading;
    }

    @Override
    @Unique
    public void mceSetLoading(boolean loading) {
        mceLoading = loading;
    }

    @Override
    @Unique
    public boolean mceHasPendingCraft() {
        return mcePendingCraft;
    }

    @Override
    @Unique
    public void mceSetPendingCraft(boolean pending) {
        mcePendingCraft = pending;
    }

    @Inject(method = "checkCompletedRecipe", at = @At("HEAD"), cancellable = true)
    private void mceSuppressWhileLoading(boolean force, CallbackInfo ci) {
        if (mceLoading) ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mceTriggerPendingCraft(CallbackInfo ci) {
        if (!mcePendingCraft || mceLoading) return;
        MechanicalCrafterBlockEntity self = (MechanicalCrafterBlockEntity) (Object) this;
        if (self.getSpeed() != 0) {
            mcePendingCraft = false;
            self.checkCompletedRecipe(false);
        }
    }
}
