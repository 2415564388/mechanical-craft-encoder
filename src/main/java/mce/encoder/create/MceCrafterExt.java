package mce.encoder.create;

/**
 * Mixin-injected extension on Create's MechanicalCrafterBlockEntity so our wall loader can
 * (a) suppress Create's "begin assembly on first insert" while a batch is being loaded, and
 * (b) defer a force-free craft trigger until the wall is actually spinning.
 */
public interface MceCrafterExt {
    boolean mceIsLoading();

    void mceSetLoading(boolean loading);

    boolean mceHasPendingCraft();

    void mceSetPendingCraft(boolean pending);
}
