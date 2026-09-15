package fr.xephi.authme.api.v3;
import org.bukkit.entity.Player;
/** Test double only; never part of the release. */
public final class AuthMeApi {
    public static boolean authenticated, broken;
    private static final AuthMeApi INSTANCE = new AuthMeApi();
    public static AuthMeApi getInstance() { if (broken) throw new IllegalStateException("Unavailable test API"); return INSTANCE; }
    public boolean isAuthenticated(Player player) { return authenticated; }
}
