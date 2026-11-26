# Hearth & Hunger 0.1.5
Minecraft 1.21.1, NeoForge 21.1.200+, Java 21. Independent of Iron Forge.
Build using Gradle 8.10.2: gradle clean build. Install the resulting jar on both client and server.
Farmer's Delight is optional; other food mods are classified by item ID with overrides in config/hearthhunger.properties. Example: food.farmersdelight\:beef_stew=FULL,MEAT,4.
Features: 32-minute daylight cycle, activity-based hunger, countdown and activity HUD, meal bonuses and digestion, vanilla food balancing and stack limits. Back up worlds before alpha testing. 

## 0.1.1
- Short fixed-width HUD labels, right-aligned timer, and 1.2 second activity hold; higher activity takes effect immediately.
- Extended natural regeneration at 3.5–8.5 hunger bars; one half-heart every 6–21 seconds before meal bonuses. Vanilla handles regeneration at 9–10 bars. No passive survival healing at 3 bars or lower; potions and other healing remain intact. Respects naturalRegeneration and leaves Peaceful healing vanilla.
- New configurable keys: activityHoldSeconds, extendedNaturalRegen, passiveRegenScale, passiveHealExhaustion.

## 0.1.2
- Sprint activity hunger multiplier reduced from 5.0 to 3.5. Vanilla sprint exhaustion remains unchanged. Existing configs using the old 5.0 default migrate once; other custom values remain intact.

## 0.1.3
- Replaces vanilla natural survival healing at every hunger level, independent of saturation. Half-heart every 2 seconds at full hunger, 3 seconds at 9.5 bars, 4 seconds at 9 bars, and previous slower rates below. No natural healing at 3 bars or below. Meal bonuses accelerate healing frequency. External healing, starvation and saturation's hunger-buffer role remain unchanged. Natural regeneration gamerule and Peaceful are respected. Other mods replacing FoodData may conflict.

## 0.1.4
- Natural healing now costs no hunger or saturation; removes the old passiveHealExhaustion setting. Full hunger heals half a heart per second. 7–10 bars heal rapidly, below 7 slows, at 4 bars and below healing is much slower, and only zero hunger disables natural healing. Hunger activity costs and meal perks remain intact; low-hunger meal buff cutoff is unchanged. The naturalRegeneration gamerule and Peaceful behavior remain respected. Potions and external healing remain unchanged.

## 0.1.5
- Every 5.5 hearts actually restored by custom natural regeneration costs one visible hunger bar (2 food points), deducted directly rather than through exhaustion or saturation. Partial healing is saved in player data and carries across healing sessions. Potions and other external healing do not count. Existing regeneration speeds and meal bonuses remain unchanged.
