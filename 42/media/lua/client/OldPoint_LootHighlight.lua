-- Report the loot window's selected container to OldPoint, so the overhead view can
-- outline it like vanilla tints it (mouse wheel or container buttons).
require "ISUI/ISInventoryPage"

local function report(page)
    if not OldPointBridge or not OldPointBridge.setLootContainer then return end
    if page.onCharacter or page.player ~= 0 then return end
    local object = nil
    if not page.isCollapsed and page.inventory then
        object = page:getContainerParent(page.inventory)
        if object and instanceof(object, "IsoPlayer") then object = nil end
    end
    OldPointBridge.setLootContainer(object)
end

local original = ISInventoryPage.updateContainerHighlight
function ISInventoryPage:updateContainerHighlight(...)
    local result = original(self, ...)
    report(self)
    return result
end

Events.OnPlayerDeath.Add(function(player)
    if OldPointBridge and OldPointBridge.setLootContainer and player and player:getPlayerNum() == 0 then
        OldPointBridge.setLootContainer(nil)
    end
end)
