package net.rocky.coresuite.common.integration;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the reflected RegionQuery contract, including BUILD's mandatory non-null actor. */
public class BossProtectionGateTest {
    public enum Association { NON_MEMBER, OWNER }
    public interface Subject { Association getAssociation(List<Object> regions); }
    public enum Flag { BUILD, MOB_SPAWNING, MOB_DAMAGE }
    public static final class Query {
        boolean denied;
        Subject actor;
        public boolean testState(String location, Subject subject, Flag... flags) {
            if (subject == null) throw new IllegalArgumentException("BUILD requires a non-null associable");
            actor = subject;
            assertEquals(Association.NON_MEMBER, subject.getAssociation(List.of()));
            assertEquals(Association.NON_MEMBER, subject.getAssociation(List.of(new Object())));
            assertEquals(1, flags.length, "WorldGuard flags must be checked independently");
            return !denied;
        }
    }
    @Test void allowsWildernessWithoutPassingNullBuildActor() throws Exception {
        Query query = new Query();
        assertThrows(IllegalArgumentException.class, () -> query.testState("wilderness", null, Flag.BUILD));
        assertTrue(BossProtectionGate.testWorldGuardState(query, "wilderness", String.class, Subject.class, Flag.values()));
        assertNotNull(query.actor);
    }
    @Test void explicitProtectionDenialIsStillRespected() throws Exception {
        Query query = new Query(); query.denied = true;
        assertFalse(BossProtectionGate.testWorldGuardState(query, "protected", String.class, Subject.class, Flag.values()));
    }
    @Test void unwrapsProviderExceptionForUsefulDiagnostics() {
        var cause = new IllegalArgumentException("BUILD requires a non-null associable");
        assertSame(cause, BossProtectionGate.invocationCause(new InvocationTargetException(new InvocationTargetException(cause))));
        assertSame(cause, BossProtectionGate.invocationCause(cause));
    }

    public static final class CombiningQuery {
        public boolean testState(String location, Subject subject, Flag... flags) {
            return java.util.Arrays.stream(flags).anyMatch(flag -> flag != Flag.BUILD);
        }
    }
    @Test void allowedMobSpawningCannotMaskImplicitBuildDenial() throws Exception {
        CombiningQuery query = new CombiningQuery();
        assertTrue(query.testState("protected", regions -> Association.NON_MEMBER, Flag.values()));
        assertFalse(BossProtectionGate.testWorldGuardState(query, "protected", String.class, Subject.class, Flag.values()));
    }
}
