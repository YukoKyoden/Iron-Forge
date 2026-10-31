# Kingdoms & Claims 0.4.26.2

Forge 1.20.1 / Forge 47.4.28 / Java 17. Existing 0.4.26 worlds use the same saved-data format.

Shadowed menu/HUD text now uses an opaque white face with its dark shadow retained, including blueprint cost quantities. Status faces remain bright red/green. Non-shadowed inventory labels retain their dark inventory text. Warehouses show item display names instead of registry IDs.

House blueprints now cost only wood, iron and a small amount of gold. Cost scales with supported block count, independently of decorative block types: wood = ceiling(blocks / 4), iron = ceiling(blocks / 32), gold = ceiling(blocks / 256). A 218-block cottage costs 55 wood, 7 iron and 1 gold ingot. Houses are identified by their Houses folder, housing structure analysis or house/cottage/apartment/home name. The server sends the pricing category in the preview so its displayed three-resource cost matches the server transaction, including manually organized folders. All three resources are deducted atomically from the kingdom stockpile. Manually built structures do not incur another charge when registered.

Other blueprint categories retain their existing material rules. This release does not change warfare mechanics; the war redesign is a separate next task.

Automated checks cover monotonic house pricing, resource limits, architect input, stockpile transactions, housing geometry and actual SavedData round trips. Minecraft input/visual testing remains separate.
