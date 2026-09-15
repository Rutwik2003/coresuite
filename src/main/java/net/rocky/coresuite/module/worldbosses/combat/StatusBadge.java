package net.rocky.coresuite.module.worldbosses.combat;

public enum StatusBadge {
    DEFLECTED("&8[&c&l⚔ DEFLECTED&8]"),
    SEARING_SIPHON("&8[&6&l♨ SEARING SIPHON&8]"),
    CALDERA_MORTAR("&8[&4&l☄ CALDERA MORTAR&8]"),
    ASHEN_SLAM("&8[&c&l💥 ASHEN SLAM&8]"),
    OVERGROWTH_CLEAVE("&8[&a&l🪓 OVERGROWTH CLEAVE&8]"),
    BRIAR_VOLLEY("&8[&2&l🏹 BRIAR VOLLEY&8]"),
    ROOTED("&8[&2&l🌿 ROOTED&8]"),
    NEUROTOXIN_LEAP("&8[&5&l☣ NEUROTOXIN LEAP&8]"),
    WEB_SPIT("&8[&5&l🕸 VENOMOUS WEB SPIT&8]"),
    WEB_SNARE("&8[&5&l🕸 WEB SNARE&8]"),
    QUENCHED("&8[&b&l💧 QUENCHED&8]"),
    STUNNED("&8[&e&l✨ STUNNED&8]"),
    EVADED("&8[&5&l✦ EVADED&8]"),
    VORTEX_ROOTS("&8[&2&l✦ VORTEX ROOTS&8]");

    private final String prefix;

    StatusBadge(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public String format(String message) {
        return prefix + " " + message;
    }
}
