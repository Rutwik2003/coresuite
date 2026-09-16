package net.rocky.coresuite.module.worldbosses.combat;

/** A boss can only move forward through these states; terminal states cannot pay twice. */
public enum EncounterState {
    PREPARING, FIGHTING, DEFEATED, ABANDONED;
    public boolean isTerminal() { return this == DEFEATED || this == ABANDONED; }
}
