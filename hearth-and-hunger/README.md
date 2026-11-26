# Hearth & Hunger 0.1.2
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
