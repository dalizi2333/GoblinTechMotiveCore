package com.dalizi2333.goblintech.api;

import net.minecraft.util.RandomSource;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

import static net.minecraft.ChatFormatting.*;

/**
 * 静态导入辅助类。
 * {@link com.gregtechceu.gtceu.api.GTValues GTValues} 的 ULV~IV 精简版。
 */
public class GoblinTechValues {

    /**
     * <p/>
     * 此值恰好等于一个普通物品的单位量。
     * 可以被 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 15, 16, 18, 20, 21, 24, ... 64 或 81
     * 等常用数字整除而不损失精度，因此用作数量单位。
     * 同时它足够小，可以与大数相乘。
     * <p/>
     * 用于确定前缀矿石中所含材料的数量。
     * 例如，粒 = M / 9，因为它包含锭的 1/9。
     */
    public static final long M = 3628800;

    /**
     * 从 "FLUID_MATERIAL_UNIT" 重命名为 "L"
     * <p/>
     * 每材料单位的流体量（质因数：3 * 3 * 2 * 2 * 2 * 2）
     */
    public static final int L = 144;
    public static final RandomSource RNG = RandomSource.createThreadSafe();

    // 各时间段的刻数快捷方式
    // 记得乘以 `level.tickRateManager().tickrate()`，否则如果 TPS 改变，此值将不准确
    public static final long SECONDS = 20;
    public static final long MINUTES = 60 * SECONDS;
    public static final long HOURS = 60 * MINUTES;
    public static final long DAYS = 24 * HOURS;
    public static final long WEEKS = 7 * DAYS;
    public static final long MONTHS = 30 * DAYS;
    public static final long YEARS = 365 * DAYS;

    /** 客户端当前时间。在服务端始终为零。 */
    public static long CLIENT_TIME = 0;

    /**
     * 电压等级数组（ULV~IV）。使用此数组替代旧的命名电压变量。
     */
    public static final long[] V = { 8, 32, 128, 512, 2048, 8192 };

    /**
     * 电压等级除以 2。
     */
    public static final int[] VH = { 4, 16, 64, 256, 1024, 4096 };

    /**
     * 考虑线损调整后的电压等级。用于配方 EU/t 以避免满安培配方。
     */
    public static final int[] VA = { 7, 30, 120, 480, 1920, 7680 };

    /** 考虑线损调整后的电压等级，除以 2。 */
    public static final int[] VHA = { 3, 15, 60, 240, 960, 3840 };

    // 电压等级索引
    public static final int ULV = 0;
    public static final int LV = 1;
    public static final int MV = 2;
    public static final int HV = 3;
    public static final int EV = 4;
    public static final int IV = 5;

    /**
     * 所有可用的电压等级（ULV~IV）。
     */
    public static final int[] ALL_TIERS = new int[] { ULV, LV, MV, HV, EV, IV };
    public static final int TIER_COUNT = ALL_TIERS.length;

    /**
     * @return 给定范围内的电压等级数组（包含两端）
     */
    public static int[] tiersBetween(int minInclusive, int maxInclusive) {
        return Arrays.stream(ALL_TIERS).dropWhile(tier -> tier < minInclusive).takeWhile(tier -> tier <= maxInclusive)
                .toArray();
    }

    // ===== Mod ID 常量 =====

    public static final String MODID_LDLIB = "ldlib";
    public static final String MODID_CONFIGURATION = "configuration";
    public static final String MODID_APPENG = "ae2";
    public static final String MODID_CREATE = "create";
    public static final String MODID_PONDER = "ponder";
    public static final String MODID_FLYWHEEL = "flywheel";
    public static final String MODID_NEOECOAE = "neoecoae";
    public static final String MODID_LDLIB2 = "ldlib2";
    public static final String MODID_SABLE = "sable";
    public static final String MODID_CAERO = "aeronautics";
    public static final String MODID_CREATEBIGCANNONS = "createbigcannons";
    public static final String MODID_DRIVEBYWIRE = "drivebywire";
    public static final String MODID_DEPLOYER = "deployer";
    public static final String MODID_VSHOSECONNECTORS = "vsfluidlink";
    public static final String MODID_CREATEMETALLURGY = "createmetallurgy";
    public static final String MODID_CREATEDIESELGENERATORS = "createdieselgenerators";
    public static final String MODID_CREATETWEAKEDCONTROLLERS = "create_tweaked_controllers";
    public static final String MODID_RPL = "ritchiesprojectilelib";
    public static final String MODID_CREATEPROPULSIONSIMULATED = "createpropulsion";
    public static final String MODID_CEE = "electroenergetics";
    public static final String MODID_PATCHOULI = "patchouli";
    public static final String MODID_TFC = "tfc";
    public static final String MODID_FIRMALIFE = "firmalife";
    public static final String MODID_TORQUELINK = "c2tfc";
    public static final String MODID_ADVANCEDXRAY = "xray";

    /**
     * 整合包运行所需的核心依赖模组列表。
     */
    public static final List<String> DEPENDENCY_MOD_IDS = List.of(
            MODID_APPENG,
            MODID_CREATE,
            MODID_CAERO,
            MODID_DEPLOYER,
            MODID_CEE,
            MODID_TFC
    );

    // ===== 显示与格式化 =====

    /**
     * 电压简称，主要用于注册。
     */
    public static final String[] VN = new String[] { "ULV", "LV", "MV", "HV", "EV", "IV" };

    public static final IntFunction<String> MAX_PLUS_FORMAT = (value) -> "" + RED + BOLD + "M" +
            GREEN + BOLD + "A" +
            BLUE + BOLD + "X" +
            YELLOW + BOLD + "+" +
            RED + BOLD + value;

    /**
     * 带格式的电压简称，用于文本显示。
     */
    public static final String[] VNF = new String[] {
            DARK_GRAY + "ULV",
            GRAY + "LV",
            AQUA + "MV",
            GOLD + "HV",
            DARK_PURPLE + "EV",
            BLUE + "IV",
    };

    /**
     * 各电压等级的颜色代码。
     */
    public static final String[] VCF = new String[] {
            DARK_GRAY.toString(),
            GRAY.toString(),
            AQUA.toString(),
            GOLD.toString(),
            DARK_PURPLE.toString(),
            BLUE.toString(),
    };

    /**
     * 用于 GUI 显示的本地化电压等级名称。
     */
    public static final String[] VLVH = new String[] {
            "Primitive",
            "Basic",
            AQUA + "Advanced",
            GOLD + "Advanced",
            DARK_PURPLE + "Advanced",
            BLUE + "Elite",
    };

    /**
     * 用于 GUI 显示的电压等级后缀。
     */
    public static final String[] VLVT = new String[] {
            "" + RESET,
            "" + RESET,
            "" + RESET,
            "II" + RESET,
            "III" + RESET,
            "" + RESET,
    };

    /**
     * 等级索引的罗马数字表示。
     */
    public static final String[] LVT = new String[] {
            "",
            "I",
            "II",
            "III",
            "IV",
            "V",
    };

    /**
     * 电压颜色值。
     */
    public static final int[] VC = new int[] { 0xC80000, 0xDCDCDC, 0xFF6400, 0xFFFF1E, 0x808080, 0xF0F0F5 };

    /**
     * 各等级主题色。
     */
    public static final int[] VCM = new int[] {
            DARK_GRAY.getColor(),
            GRAY.getColor(),
            AQUA.getColor(),
            GOLD.getColor(),
            DARK_PURPLE.getColor(),
            BLUE.getColor(),
    };

    // 蒸汽机器主题色
    public static final int VC_LP_STEAM = 0xBB8E53;
    public static final int VC_HP_STEAM = 0x79756F;

    /**
     * 电压全称。
     */
    public static final String[] VOLTAGE_NAMES = new String[] { "Ultra Low Voltage", "Low Voltage", "Medium Voltage",
            "High Voltage", "Extreme Voltage", "Insane Voltage" };
}
