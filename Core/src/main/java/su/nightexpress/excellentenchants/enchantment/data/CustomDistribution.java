package su.nightexpress.excellentenchants.enchantment.data;

import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentenchants.api.enchantment.distribution.DistributionOptions;
import su.nightexpress.excellentenchants.api.enchantment.EnchantmentData;
import su.nightexpress.excellentenchants.api.DistributionWay;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.StringUtil;
import su.nightexpress.nightcore.util.random.Rnd;
import su.nightexpress.nightcore.util.wrapper.UniInt;

import java.util.*;

public class CustomDistribution implements DistributionOptions {

    private final EnchantmentData enchantmentData;

    private final Map<DistributionWay, Double> weightMap;
    private final Map<DistributionWay, UniInt> levelRangeMap;
    private final Set<LootTables> lootTables;

    public CustomDistribution(@NotNull EnchantmentData enchantmentData) {
        this.enchantmentData = enchantmentData;

        this.weightMap = new HashMap<>();
        this.levelRangeMap = new HashMap<>();
        this.lootTables = new HashSet<>();
    }

    public void load(@NotNull FileConfig config) {
        for (DistributionWay distributionWay : DistributionWay.values()) {
            String pathName = distributionWay.getPathName();

            double obtainChance = ConfigValue.create("Distribution." + pathName + ".Weight", 50D,
                "Determines how often this enchantment will appear in " + pathName + ".",
                "Greater value = Greater chance.").read(config);

            this.weightMap.put(distributionWay, obtainChance);

            int levelMin = ConfigValue.create("Distribution." + pathName + ".Level.Min", -1,
                "Minimal level when obtained via " + pathName,
                "Can not be less than enchantment min. level. Set -1 to use enchantment min. level.").read(config);

            int levelMax = ConfigValue.create("Distribution." + pathName + ".Level.Max", -1,
                "Maximal level when obtained via " + pathName,
                "Can not be greater than enchantment max. level. Set -1 to use enchantment max. level.").read(config);

            this.levelRangeMap.put(distributionWay, UniInt.of(levelMin, levelMax));
        }

        boolean isWhitelist = ConfigValue.create("Distribution." + DistributionWay.LOOT_GENERATION.getPathName() + ".LootTables.Whitelist",
            false,
            "When 'true', uses only loot tables listed below.",
            "When 'false', uses ALL loot tables except ones listed below.",
            "[Default is false]"
        ).read(config);

        Set<LootTables> tables = ConfigValue.forSet("Distribution." + DistributionWay.LOOT_GENERATION.getPathName() + ".LootTables.List",
            id -> StringUtil.getEnum(id, LootTables.class).orElse(null),
            (cfg, path, set) -> cfg.set(path, set.stream().map(Enum::name).toList()),
            Set.of(
                LootTables.DESERT_PYRAMID
            ),
            "Depends on Whitelist mode, this enchantment will appear on items generated by certain Loot Tables only.",
            "=".repeat(15) +  " EXAMPLES " + "=".repeat(15),
            "==> Set 'Whitelist' on 'true' and add '" + LootTables.DESERT_PYRAMID.name() + "' to the list to make this enchantment appear on items generated inside Desert Pyramid chests only.",
            "==> Set 'Whitelist' on 'false' and add '" + LootTables.DESERT_PYRAMID.name() + "' to the list to make this enchantment appear on items generated inside any structure's chests except Deset Pyramids.",
            "",
            "[*] Keep in mind, that Loot Generation is only applicable to items in chests. Listing here loot tables of mobs, fishing and other sources is useless.",
            "Available loot table names: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/loot/LootTables.html"
        ).read(config);

        if (isWhitelist) {
            this.lootTables.addAll(tables);
        }
        else {
            this.lootTables.addAll(Arrays.asList(LootTables.values()));
            this.lootTables.removeAll(tables);
        }
    }

    @NotNull
    public Map<DistributionWay, Double> getWeightMap() {
        return weightMap;
    }

    @NotNull
    public Map<DistributionWay, UniInt> getLevelRangeMap() {
        return levelRangeMap;
    }

    @NotNull
    public Set<LootTables> getLootTables() {
        return lootTables;
    }

    public boolean isDistributable(@NotNull DistributionWay distributionWay) {
        if (distributionWay == DistributionWay.ENCHANTING && (this.enchantmentData.isTreasure() || this.enchantmentData.isCurse())) return false;

        return this.getWeight(distributionWay) > 0D;
    }

    public boolean isGoodLootTable(@NotNull LootTable table) {
        return this.getLootTables().stream().anyMatch(tables -> tables.getLootTable() == table);
    }

    public double getWeight(@NotNull DistributionWay distributionWay) {
        return this.getWeightMap().getOrDefault(distributionWay, 0D);
    }

    public int getMinLevel(@NotNull DistributionWay distributionWay) {
        return this.getLevelRangeMap().getOrDefault(distributionWay, UniInt.of(-1, -1)).getMinValue();
    }

    public int getMaxLevel(@NotNull DistributionWay distributionWay) {
        return this.getLevelRangeMap().getOrDefault(distributionWay, UniInt.of(-1, -1)).getMaxValue();
    }

    public int generateLevel(@NotNull DistributionWay distributionWay) {
        int levelCapMin = this.getMinLevel(distributionWay);
        int levelCapMax = this.getMaxLevel(distributionWay);

        if (levelCapMin < 1) levelCapMin = 1;
        if (levelCapMax <= 0 || levelCapMax > this.enchantmentData.getMaxLevel()) levelCapMax = this.enchantmentData.getMaxLevel();

        return Rnd.get(levelCapMin, levelCapMax);
    }

    public int getLevelByEnchantCost(int xpLevel) {
        int get = 0;

        for (int level = this.enchantmentData.getMaxLevel(); level > 0; level--) {
            if (xpLevel >= this.enchantmentData.getMinCost(level) && xpLevel <= this.enchantmentData.getMaxCost(level)) {
                get = level;
                break;
            }
        }

        return get != 0 ? this.fineLevel(get, DistributionWay.ENCHANTING) : 0;
    }

    public int fineLevel(int level, @NotNull DistributionWay distributionWay) {
        int levelCapMin = this.getMinLevel(distributionWay);
        int levelCapMax = this.getMaxLevel(distributionWay);

        if (levelCapMin > 0 && level < levelCapMin) level = levelCapMin;
        if (levelCapMax > 0 && level > levelCapMax) level = levelCapMax;

        return level;
    }
}
