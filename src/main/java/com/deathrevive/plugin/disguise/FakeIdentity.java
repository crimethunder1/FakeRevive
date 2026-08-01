package com.deathrevive.plugin.disguise;

/**
 * A fake identity assigned to a player after being revived — an unclaimed Minecraft
 * username paired with a skin borrowed from a different, already-taken account,
 * so the displayed name never belongs to the same account as the displayed skin.
 *
 * @param name           the fake Minecraft username shown to other players
 * @param skinValue      the Base64-encoded texture value from the skin donor's profile
 * @param skinSignature  the Mojang-signed signature for the texture; required for the
 *                       client to accept and render the skin
 */
public record FakeIdentity(String name, String skinValue, String skinSignature) {
}
